package app.delhiraahi.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A soft alert sound made in code (no audio file): short, rounded bell notes that rise.
 * Quiet enough not to turn heads in a crowded bus, distinct enough to notice.
 * Plays on the notification stream, so it follows the phone's notification volume and silent mode.
 */
object Chime {
    enum class Kind(val notes: DoubleArray) {
        /** Bus arriving, or "your stop is next": two notes. */
        SOFT(doubleArrayOf(784.0, 1046.5)),
        /** You've arrived: three notes. */
        ARRIVE(doubleArrayOf(659.3, 784.0, 1046.5)),
    }

    private const val RATE = 22_050
    private const val GAP_S = 0.16        // next note starts this long after the previous one
    private const val NOTE_S = 0.9        // each note rings out this long
    private const val LEVEL = 0.32        // well below full scale: soft

    /** 16-bit mono samples for [kind]; never clips. */
    fun samples(kind: Kind, rate: Int = RATE): ShortArray {
        val total = ((GAP_S * (kind.notes.size - 1) + NOTE_S) * rate).toInt()
        val mix = DoubleArray(total)
        kind.notes.forEachIndexed { k, f ->
            val start = (k * GAP_S * rate).toInt()
            val len = (NOTE_S * rate).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= total) break
                val tt = i.toDouble() / rate
                val attack = (tt / 0.008).coerceAtMost(1.0)                 // no click at the start
                val env = attack * exp(-tt / 0.22)                           // bell-like fade
                // A pure tone with a faint overtone: rounder than a beep.
                mix[idx] += env * (sin(2 * PI * f * tt) + 0.18 * sin(2 * PI * 2 * f * tt))
            }
        }
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        val gain = LEVEL / peak
        return ShortArray(total) { i ->
            // Fade the very end to silence as well.
            val tail = ((total - i).toDouble() / (0.02 * rate)).coerceAtMost(1.0)
            (mix[i] * gain * tail * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun play(kind: Kind) {
        runCatching {
            val pcm = samples(kind)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            val ms = pcm.size * 1000L / RATE + 200
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { track.stop(); track.release() } }, ms)
        }
    }
}
