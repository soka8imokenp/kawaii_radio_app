package uz.kawaii.radio.kr01

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import androidx.core.view.WindowCompat
import kotlinx.coroutines.withContext
import uz.kawaii.radio.KeyValueStore
import uz.kawaii.radio.Platform
import uz.kawaii.radio.kr01.audio.Clicks
import uz.kawaii.radio.kr01.audio.Media3Engine
import uz.kawaii.radio.kr01.audio.Waveform
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AndroidPlatform(private val context: Context) : Platform {
    private val prefs = context.getSharedPreferences("kr01", Context.MODE_PRIVATE)

    override val kv = object : KeyValueStore {
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun set(key: String, value: String?) = prefs.edit { if (value == null) remove(key) else putString(key, value) }
    }
    override val audio = Media3Engine(context)
    override val sound = Clicks()
    override val updater = Updater(context)
    override val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "dev"

    private val waveforms = Waveform(context)
    private val offlineDir = File(context.filesDir, "offline").apply { mkdirs() }

    override suspend fun httpGetText(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            useCaches = false
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    override suspend fun waveform(trackId: String, uri: String, bars: Int): FloatArray? = waveforms.read(trackId, uri, bars)

    override suspend fun glowBitmap(uri: String): ImageBitmap? = withContext(Dispatchers.Default) {
        if (uri.isEmpty()) return@withContext null
        val req = ImageRequest.Builder(context).data(uri).size(48).allowHardware(false).build()
        val res = SingletonImageLoader.get(context).execute(req) as? SuccessResult ?: return@withContext null
        blur(res.image.toBitmap().scale(24, 24)).asImageBitmap()
    }

    /** Three box-blur passes ≈ a gaussian; on a 24×24 bitmap this takes microseconds. */
    private fun blur(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        var px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        repeat(3) {
            px = boxPass(px, w, h, 3, horizontal = true)
            px = boxPass(px, w, h, 3, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(src: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean): IntArray {
        val out = IntArray(src.size)
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = if (horizontal) src[line * w + j] else src[j * w + line]
                    a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++
                }
                val idx = if (horizontal) line * w + i else i * w + line
                out[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
    }

    override suspend fun pixelCover(uri: String, size: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        if (uri.isEmpty()) return@withContext null
        val req = ImageRequest.Builder(context).data(uri).size(size * 2).allowHardware(false).build()
        val res = SingletonImageLoader.get(context).execute(req) as? SuccessResult ?: return@withContext null
        val src = res.image.toBitmap()
        // centre-crop to a square, then shrink to the LCD grid
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side).scale(size, size)
        val px = IntArray(size * size).also { square.getPixels(it, 0, size, 0, 0, size, size) }
        // Bayer 4×4 ordered dither with a contrast boost: "on" pixels become opaque white (tinted when drawn)
        val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        for (y in 0 until size) for (x in 0 until size) {
            val p = px[y * size + x]
            var l = (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) / 255f
            l = ((l - 0.5f) * 1.35f + 0.5f).coerceIn(0f, 1f)
            val on = l < (bayer[(y % 4) * 4 + (x % 4)] + 0.5f) / 16f
            px[y * size + x] = if (on) -0x1 else 0
        }
        Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Set by MainActivity while it is alive: status bar styling and the photo picker need a window. */
    var activity: MainActivity? = null
    private var picking: CompletableDeferred<Uri?>? = null

    override fun setSystemBarIcons(statusDark: Boolean, navDark: Boolean) {
        val a = activity ?: return
        a.runOnUiThread {
            WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
                isAppearanceLightStatusBars = statusDark
                isAppearanceLightNavigationBars = navDark
            }
        }
    }

    private val avatarFile = File(context.filesDir, "avatar.jpg")

    override fun avatarPhoto(): String? = avatarFile.takeIf { it.length() > 0 }?.let { Uri.fromFile(it).toString() + "?v=" + it.lastModified() }

    override suspend fun pickAvatarPhoto(): String? {
        val a = activity ?: return null
        val wait = CompletableDeferred<Uri?>().also { picking = it }
        a.pickPhoto()
        val uri = wait.await() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(uri).size(384).allowHardware(false).build()
                val res = SingletonImageLoader.get(context).execute(req) as? SuccessResult ?: return@runCatching null
                val b = res.image.toBitmap()
                val side = minOf(b.width, b.height)
                val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side).scale(256, 256)
                avatarFile.outputStream().use { sq.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                avatarPhoto()
            }.getOrNull()
        }
    }

    fun onPhotoPicked(uri: Uri?) {
        picking?.complete(uri)
        picking = null
    }

    private fun offlineFile(id: String) = File(offlineDir, id.filter { it.isLetterOrDigit() || it == '-' || it == '_' } + ".audio")

    override suspend fun saveOffline(trackId: String, uri: String): Boolean = withContext(Dispatchers.IO) {
        val file = offlineFile(trackId)
        if (file.length() > 0) return@withContext true
        val part = File(file.path + ".part")
        runCatching {
            val c = (URL(uri).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
            try {
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                c.inputStream.use { i -> part.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
            } finally {
                c.disconnect()
            }
            part.renameTo(file)
        }.getOrDefault(false).also { if (!it) part.delete() }
    }

    override fun removeOffline(trackId: String) {
        offlineFile(trackId).delete()
    }

    override fun offlineUri(trackId: String): String? = offlineFile(trackId).takeIf { it.length() > 0 }?.let { Uri.fromFile(it).toString() }

    override fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
