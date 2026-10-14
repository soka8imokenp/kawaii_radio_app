package uz.kawaii.radio.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import uz.kawaii.radio.Platform

/** Releases are published by .github/workflows/build-apk.yml as tag v1.0.<build> with kawaii-radio.apk attached. */
const val REPO = "soka8imokenp/kawaii_radio_app"
private const val APK_ASSET = "kawaii-radio.apk"
private const val SKIP_KEY = "kr01-skip-build"

data class UpdateInfo(
    val version: String,   // e.g. 1.0.14
    val build: Int,        // Android versionCode (= workflow run number)
    val current: String,   // installed version name
    val notes: List<String>,
    val apkUrl: String,
    val size: Long,
)

/**
 * Asks GitHub for the latest release. Null when the installed build is already the newest;
 * throws when the check itself failed (offline, rate limit…).
 */
suspend fun checkForUpdate(p: Platform): UpdateInfo? {
    val text = p.httpGetText(
        "https://api.github.com/repos/$REPO/releases/latest",
        mapOf("Accept" to "application/vnd.github+json"),
    )
    val rel = json.parseToJsonElement(text).jsonObject
    val tag = rel["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val build = tag.substringAfterLast('.').toIntOrNull() ?: 0
    val asset = (rel["assets"] as? JsonArray)?.map { it.jsonObject }
        ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == APK_ASSET }
    if (build == 0 || asset == null || build <= p.updater.installedBuild) {
        p.updater.cleanup() // old APKs
        return null
    }
    val notes = (rel["body"]?.jsonPrimitive?.contentOrNull ?: "")
        .lines()
        .map { it.trimStart('-', '*', '•', ' ', '\t').trim() }
        .filter { it.isNotEmpty() && !it.startsWith("co-authored-by:", ignoreCase = true) }
        .take(6)
    return UpdateInfo(
        version = tag.removePrefix("v"),
        build = build,
        current = p.appVersion,
        notes = notes.ifEmpty { listOf("Yaxshilanishlar va tuzatishlar") },
        apkUrl = asset.str("browser_download_url"),
        size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0,
    )
}

private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()

/** "Keyinroq": don't pop this build up on every launch again (it stays available in Profil). */
fun skipBuild(p: Platform, build: Int) = p.kv.set(SKIP_KEY, build.toString())
fun isSkipped(p: Platform, build: Int) = p.kv.get(SKIP_KEY)?.toIntOrNull() == build
