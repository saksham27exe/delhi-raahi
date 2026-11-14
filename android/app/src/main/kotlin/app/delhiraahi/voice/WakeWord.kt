package app.delhiraahi.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.Executors

/**
 * Which words count as "Hey Raahi". Pure Kotlin so it can be unit-tested.
 *
 * The offline model has no word "raahi", so it is spelt as sounds it does know ("rah hee", "ra hi"…).
 * The grammar also holds ~150 everyday words as decoys: ordinary speech ("rain here", "hey, how are
 * you") then matches those decoys instead of the wake phrase, which keeps false wake-ups rare.
 */
object WakeMatch {
    private val RA = setOf("ra", "rah")
    private val HEE = setOf("hee", "hi", "he")

    private val WAKE = listOf("hey ra hee", "hey rah hee", "hey ra hi", "hey rah he", "hey ra he")
    private val DECOYS = ("the a an and is are was to of in on at it i you we he she they me my your our this that " +
        "what where when how why who yes no ok okay hi hello hey here hear there rain right ready really rather rah " +
        "ravi rahul ray rate raw rice go going come coming take get want need can will would should do did done have " +
        "has had be been bus metro stop gate delhi today tomorrow now time please thank thanks sir madam one two three " +
        "four five good bad very much more about from for with by not but so if then just also like know think see " +
        "look say").split(" ")

    /** The Vosk grammar (a JSON list of phrases). */
    val grammar: String = (WAKE + DECOYS + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

    /** True when the heard words contain the wake phrase: "(hey) ra/rah hee/hi/he", or "hey rah i". */
    fun heard(text: String): Boolean {
        val w = text.lowercase().split(' ').filter { it.isNotBlank() && it != "[unk]" }
        for (i in 0 until w.size - 1) {
            if (w[i] in RA && w[i + 1] in HEE) return true
            if (i > 0 && w[i - 1] == "hey" && w[i] in RA && w[i + 1] == "i") return true
        }
        return false
    }

    /** The full phrase with "hey": safe enough to act on before the sentence ends (faster wake-up). */
    fun heardFull(text: String): Boolean {
        val w = text.lowercase().split(' ').filter { it.isNotBlank() && it != "[unk]" }
        return (0 until w.size - 2).any { w[it] == "hey" && w[it + 1] in RA && w[it + 2] in HEE }
    }
}

/** True when a screen reader (TalkBack) is on: then the wake word is on unless the person turned it off. */
fun screenReaderOn(ctx: Context): Boolean =
    (ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

fun hasMicPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Listens for "Hey Raahi" on the phone itself (Vosk, open source, no internet, nothing recorded or sent).
 * Runs only while the app is on screen and the assistant panel is closed; [onWake] then opens the panel.
 */
class WakeWord(context: Context, private val onWake: () -> Unit) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var model: Model? = null
    private var service: SpeechService? = null
    private var rec: Recognizer? = null
    private var wanted = false
    private var loading = false

    /** Start listening (main thread). Does nothing without the microphone permission. */
    fun start() {
        wanted = true
        if (service != null || loading || !hasMicPermission(ctx)) return
        val m = model
        if (m != null) { begin(m); return }
        loading = true
        worker.execute {
            val loaded = runCatching { loadModel() }.onFailure { Log.w(TAG, "wake model not loaded", it) }.getOrNull()
            main.post {
                loading = false
                if (loaded != null) { model = loaded; if (wanted) begin(loaded) }
            }
        }
    }

    /** Stop listening and free the microphone at once (main thread). */
    fun stop() {
        wanted = false
        service?.let { runCatching { it.stop(); it.shutdown() } }
        service = null
        rec?.let { runCatching { it.close() } }
        rec = null
    }

    fun release() {
        stop()
        worker.execute { model?.close(); model = null }
        worker.shutdown()
    }

    private fun begin(m: Model) {
        if (service != null) return
        try {
            val r = Recognizer(m, RATE, WakeMatch.grammar)
            rec = r
            service = SpeechService(r, RATE).also { it.startListening(listener) }
        } catch (e: Exception) {   // mic busy (a call, another app): try again shortly
            Log.w(TAG, "wake listen failed", e)
            stop(); wanted = true
            main.postDelayed({ if (wanted && service == null) start() }, 3000)
        }
    }

    private fun woke() {
        if (!wanted) return
        stop()
        onWake()
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (WakeMatch.heardFull(field(hypothesis, "partial"))) woke()
        }
        override fun onResult(hypothesis: String?) {
            if (WakeMatch.heard(field(hypothesis, "text"))) woke()
        }
        override fun onFinalResult(hypothesis: String?) {
            if (WakeMatch.heard(field(hypothesis, "text"))) woke()
        }
        override fun onError(exception: Exception?) {
            Log.w(TAG, "wake error", exception)
            stop(); wanted = true
            main.postDelayed({ if (wanted && service == null) start() }, 3000)
        }
        override fun onTimeout() {}
    }

    private fun field(json: String?, key: String) = runCatching { JSONObject(json ?: "{}").optString(key) }.getOrDefault("")

    /** Copies the model out of the APK once (Vosk needs real files), then opens it. */
    private fun loadModel(): Model {
        val dir = File(ctx.filesDir, "wake/$MODEL")
        val done = File(dir, ".ready")
        if (!done.exists()) {
            dir.deleteRecursively()
            copyAssets("$ASSETS/$MODEL", dir)
            done.writeText("ok")
        }
        return Model(dir.absolutePath)
    }

    private fun copyAssets(path: String, to: File) {
        val kids = ctx.assets.list(path).orEmpty()
        if (kids.isEmpty()) {
            to.parentFile?.mkdirs()
            ctx.assets.open(path).use { i -> to.outputStream().use { i.copyTo(it) } }
        } else {
            to.mkdirs()
            kids.forEach { copyAssets("$path/$it", File(to, it)) }
        }
    }

    companion object {
        private const val TAG = "WakeWord"
        private const val RATE = 16000f
        private const val ASSETS = "wake"
        /** Must match the folder the Gradle task fetchWakeModel puts in the assets. */
        const val MODEL = "vosk-model-small-en-in-0.4"
    }
}
