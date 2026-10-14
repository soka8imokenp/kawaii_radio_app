package uz.kawaii.radio.kr01.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Loudness envelope for the LCD waveform. Instead of decoding the whole song (slow, lots of RAM),
 * it seeks to each bar and decodes ~100 ms there: a few hundred mp3 frames in total. For cloud
 * songs that also means only a fraction of the file is read. Results are cached on disk.
 */
class Waveform(private val context: Context) {
    private val lock = Mutex() // one song at a time keeps weak phones responsive
    private val dir = File(context.filesDir, "peaks").apply { mkdirs() }

    suspend fun read(trackId: String, uri: String, bars: Int): FloatArray? = withContext(Dispatchers.IO) {
        val file = File(dir, "${trackId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}-${uri.hashCode().toUInt()}-$bars.bin")
        load(file, bars)?.let { return@withContext it }
        lock.withLock {
            load(file, bars) ?: runCatching { measure(uri, bars) }.getOrNull()?.also { save(file, it) }
        }
    }

    private fun load(f: File, bars: Int): FloatArray? = runCatching {
        DataInputStream(f.inputStream().buffered()).use { s -> FloatArray(bars) { s.readFloat() } }
    }.getOrNull()

    private fun save(f: File, v: FloatArray) = runCatching {
        DataOutputStream(f.outputStream().buffered()).use { s -> v.forEach(s::writeFloat) }
    }

    private fun measure(uri: String, bars: Int): FloatArray {
        val ex = MediaExtractor()
        val reader = MediaData.Reader(MediaData.factory(context), Uri.parse(uri))
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(reader)
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val want = rate / 10 * channels // ~100 ms of samples per bar
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val out = FloatArray(bars)
            for (i in 0 until bars) {
                ex.seekTo(durationUs * (2L * i + 1) / (2L * bars), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()
                var sum = 0.0
                var sampled = 0
                var decoded = 0
                var inputDone = false
                var guard = 0
                while (decoded < want && guard++ < 64) {
                    if (!inputDone) {
                        val ii = codec.dequeueInputBuffer(5_000)
                        if (ii >= 0) {
                            val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 5_000)
                    if (oi >= 0) {
                        val buf = codec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                        // every 8th sample is plenty for an envelope
                        val n = buf.remaining()
                        var k = 0
                        while (k < n) {
                            val s = buf.get(k) / 32768.0
                            sum += s * s
                            sampled++
                            k += 8
                        }
                        decoded += n
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                out[i] = if (sampled > 0) sqrt(sum / sampled).toFloat() else 0f
            }
            return out
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
            runCatching { reader.close() }
        }
    }
}
