package uz.kawaii.radio.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uz.kawaii.radio.Platform
import uz.kawaii.radio.nowMs

/**
 * Synced lyrics for songs that came without any, from LRCLIB (lrclib.net, an open lyrics database).
 * Looked up once per song and remembered on the phone; a miss is retried after a week.
 */
@Serializable
data class AutoLyrics(val synced: String? = null, val instrumental: Boolean = false, val at: Long = 0)

private const val API = "https://lrclib.net/api"
private const val RETRY_MS = 7L * 24 * 3600 * 1000
private val HEADERS = mapOf("User-Agent" to "KawaiiRadio/1.0 (https://github.com/soka8imokenp/kawaii_radio_app)")

private fun enc(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code) append(c.toChar())
        else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
    }
}

private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun JsonObject.bool(k: String) = this[k]?.jsonPrimitive?.booleanOrNull == true

suspend fun lookupLyrics(p: Platform, t: Track): AutoLyrics? {
    val key = "lrc:${t.id}"
    p.kv.get(key)?.let { cached ->
        val a = runCatching { json.decodeFromString<AutoLyrics>(cached) }.getOrNull()
        if (a != null && (a.synced != null || a.instrumental || nowMs() - a.at < RETRY_MS)) return a
    }
    val artist = mainArtist(t.artist)
    val found = runCatching {
        // exact match first (title + artist + length), then a looser search
        val exact = if (t.duration > 0) runCatching {
            json.parseToJsonElement(
                p.httpGetText("$API/get?track_name=${enc(t.title)}&artist_name=${enc(artist)}&duration=${t.duration.toInt()}", HEADERS),
            ).jsonObject
        }.getOrNull() else null
        exact?.takeIf { it.str("syncedLyrics") != null || it.bool("instrumental") }
            ?: (json.parseToJsonElement(p.httpGetText("$API/search?track_name=${enc(t.title)}&artist_name=${enc(artist)}", HEADERS)) as? JsonArray)
                ?.map { it.jsonObject }
                ?.filter { it.str("syncedLyrics") != null }
                ?.minByOrNull { r -> if (t.duration > 0) kotlin.math.abs((r["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) - t.duration) else 0.0 }
    }.getOrElse { return null } // offline: try again next time
    val result = AutoLyrics(found?.str("syncedLyrics"), found?.bool("instrumental") == true, nowMs())
    p.kv.set(key, json.encodeToString(AutoLyrics.serializer(), result))
    return result
}
