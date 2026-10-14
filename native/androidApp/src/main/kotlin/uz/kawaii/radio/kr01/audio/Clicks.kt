package uz.kawaii.radio.kr01.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import uz.kawaii.radio.ClickSound
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Short synthesized mechanical clicks: a filtered noise burst, pre-rendered once into static
 * AudioTracks so a tap costs nothing but "play". No vibration (haptics on every tap felt heavy).
 */
class Clicks : ClickSound {
    private val rate = 44_100
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "clicks").apply { priority = Thread.MAX_PRIORITY } }
    private val tick by lazy { Voice(render(2400.0, 0.012, 0.05)) }
    private val press by lazy { Voice(render(900.0, 0.03, 0.09)) }

    override fun tick() = worker.execute { runCatching { tick.play() } }
    override fun press() = worker.execute { runCatching { press.play() } }

    /** Noise with a cubic decay through a band-pass (RBJ biquad, Q 1.2). */
    private fun render(freq: Double, len: Double, gain: Double): ShortArray {
        val n = (rate * len).toInt()
        val w = 2 * PI * freq / rate
        val alpha = sin(w) / (2 * 1.2)
        val a0 = 1 + alpha
        val b0 = alpha / a0
        val b2 = -alpha / a0
        val a1 = -2 * cos(w) / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        return ShortArray(n) { i ->
            val x = (Random.nextDouble() * 2 - 1) * (1 - i.toDouble() / n).pow(3)
            val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            (y * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** A few static tracks per sound, used round-robin so fast tuner spins can overlap. */
    private inner class Voice(pcm: ShortArray) {
        private val tracks = List(3) { build(pcm) }
        private var next = 0

        fun play() {
            val t = tracks[next]
            next = (next + 1) % tracks.size
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }

        private fun build(pcm: ShortArray): AudioTrack {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val b = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
            if (Build.VERSION.SDK_INT >= 26) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            return b.build().apply { write(pcm, 0, pcm.size) }
        }
    }
}
