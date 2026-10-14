package uz.kawaii.radio.kr01

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.kawaii.radio.InstallResult
import uz.kawaii.radio.UpdateInstaller
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads release APKs into cache/updates and hands them to Android's package installer. */
class Updater(private val context: Context) : UpdateInstaller {
    private val dir = File(context.cacheDir, "updates")

    override val installedBuild: Int = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    }.getOrDefault(0)

    override suspend fun download(url: String, build: Int, size: Long, onProgress: (Long, Long) -> Unit): String =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val file = File(dir, "kawaii-radio-$build.apk")
            // already downloaded earlier (e.g. the user went to settings for the install permission)
            if (size > 0 && file.length() == size) {
                onProgress(size, size)
                return@withContext file.absolutePath
            }
            val part = File(dir, file.name + ".part")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: size
                var done = 0L
                var reported = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - reported >= 128 * 1024) {
                                reported = done
                                withContext(Dispatchers.Main) { onProgress(done, total) }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) { onProgress(done, total) }
                if (size > 0 && part.length() != size) {
                    part.delete()
                    error("Fayl to'liq yuklanmadi (${part.length()} / $size)")
                }
                file.delete()
                part.renameTo(file)
                file.absolutePath
            } finally {
                conn.disconnect()
            }
        }

    /** Android 8+ only lets an app start the installer after the user allowed "Install unknown apps" for it. */
    override fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    override fun openInstallSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun install(path: String): InstallResult {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) error("Yangilanish fayli topilmadi")
        if (!canInstall()) return InstallResult.NeedsPermission
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallResult.Started
    }

    override fun cleanup() {
        dir.deleteRecursively()
    }
}
