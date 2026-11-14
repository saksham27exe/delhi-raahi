package app.delhiraahi.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Text-to-speech that can be awaited, so we never listen while we are still talking.
 * Uses Google's engine when installed (many tablets default to an engine without Indian voices) and the
 * best-quality Indian English / Hindi voice it offers.
 */
class Speaker(ctx: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val engine: String? = GOOGLE_TTS.takeIf { pkg ->
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }
    private val tts = TextToSpeech(ctx.applicationContext, { ready.complete(it == TextToSpeech.SUCCESS) }, engine)
    private var voiceLocale: Locale? = null

    init {
        tts.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { pending.remove(id)?.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { pending.remove(id)?.complete(Unit) }
            override fun onError(id: String, errorCode: Int) { pending.remove(id)?.complete(Unit) }
            override fun onStop(id: String, interrupted: Boolean) { pending.remove(id)?.complete(Unit) }
        })
    }

    suspend fun say(text: String, locale: Locale) {
        if (!ready.await()) return
        if (voiceLocale != locale) { pickVoice(locale); voiceLocale = locale }
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            withTimeoutOrNull(text.length * 160L + 6_000) { done.await() }
        } finally {
            if (!done.isCompleted) tts.stop()
            pending.remove(id)
        }
    }

    private fun pickVoice(locale: Locale) {
        val all = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == locale.language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        val pool = all.filter { it.locale.country == locale.country }.ifEmpty { all }
        val best = pool.sortedWith(
            compareByDescending<Voice> { it.quality }.thenByDescending { it.isNetworkConnectionRequired }.thenBy { it.latency },
        ).firstOrNull()
        if (best != null) tts.voice = best
        else {
            val r = tts.setLanguage(locale)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.ENGLISH)
        }
        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)
    }

    /** Cut the current sentence short (the traveller tapped to talk). */
    fun stop() { tts.stop() }
    fun shutdown() { tts.stop(); tts.shutdown() }
}

private const val GOOGLE_TTS = "com.google.android.tts"

sealed interface Heard {
    data class Text(val options: List<String>) : Heard
    data object Nothing : Heard
    data object NoPermission : Heard
    data class Failed(val code: Int) : Heard
}

/** One-shot speech recognition (Android's recogniser) as a suspend call. */
class Listener(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** A short beep tells the listener the microphone is now open. */
    fun beep() { runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) } }

    suspend fun listen(languageTag: String, onPartial: (String) -> Unit): Heard = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val r = rec ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { rec = it }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                    if (cont.isActive) cont.resume(if (l.isEmpty()) Heard.Nothing else Heard.Text(l))
                }
                override fun onError(error: Int) {
                    if (!cont.isActive) return
                    cont.resume(when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Heard.Nothing
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Heard.NoPermission
                        else -> Heard.Failed(error)
                    })
                }
                override fun onPartialResults(b: Bundle?) {
                    b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Give people time to finish a sentence such as "from Kashmere Gate to ... AIIMS".
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            r.startListening(intent)
            cont.invokeOnCancellation { main.post { runCatching { r.cancel() } } }
        }
    }

    /** Stop waiting for more speech and return what was heard so far. */
    fun finish() { main.post { runCatching { rec?.stopListening() } } }

    fun destroy() {
        main.post { runCatching { rec?.destroy() }; rec = null }
        runCatching { tone?.release() }
    }
}
