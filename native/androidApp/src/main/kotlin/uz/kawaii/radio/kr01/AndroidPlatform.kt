package uz.kawaii.radio.kr01

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

    override suspend fun saveOffline(trackId: String, uri: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val file = offlineFile(trackId)
        if (file.length() > 0) return@withContext true
        val part = File(file.path + ".part")
        runCatching {
            download(uri, part, onProgress)
            part.renameTo(file)
        }.getOrDefault(false).also { if (!it) part.delete() }
    }

    /** Streams [uri] into [to], reporting 0..1. */
    private fun download(uri: String, to: File, onProgress: (Float) -> Unit) {
        val c = (URL(uri).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            var last = 0L
            c.inputStream.use { i ->
                to.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - last > 128 * 1024) { last = done; onProgress(done.toFloat() / total) }
                    }
                }
            }
            onProgress(1f)
        } finally {
            c.disconnect()
        }
    }

    override suspend fun exportToPhone(trackId: String, uri: String, fileName: String): String? = withContext(Dispatchers.IO) {
        val bad = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        val name = fileName.map { if (it in bad) '_' else it }.joinToString("").take(120) + ".mp3"
        runCatching {
            // reuse the offline copy when there is one, otherwise fetch it once
            val tmp = File(context.cacheDir, "export.tmp")
            val src = offlineFile(trackId).takeIf { it.length() > 0 } ?: tmp.also { download(uri, it) {} }
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Kawaii Radio")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val r = context.contentResolver
                val item = r.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: error("no media store")
                r.openOutputStream(item)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
                r.update(item, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } else {
                // Android 9 and older: the Music folder needs the storage permission
                if (activity?.ensureStoragePermission() != true) error("no permission")
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Kawaii Radio").apply { mkdirs() }
                val out = File(dir, name)
                src.copyTo(out, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(out.path), arrayOf("audio/mpeg"), null)
            }
            tmp.delete()
            "Music/Kawaii Radio/$name"
        }.getOrNull()
    }

    override fun removeOffline(trackId: String) {
        offlineFile(trackId).delete()
    }

    override fun offlineUri(trackId: String): String? = offlineFile(trackId).takeIf { it.length() > 0 }?.let { Uri.fromFile(it).toString() }

    override fun shareImage(image: ImageBitmap, text: String) {
        val a = activity ?: return
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "kawaii-wrapped.png")
            file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            a.startActivity(Intent.createChooser(send, "Ulashish"))
        }
    }

    override fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
