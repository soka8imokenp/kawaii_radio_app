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

/** Lowercase letters and digits only, without "(feat. …)", "[…]" and " - movie ver." style tails. */
private fun norm(s: String): String =
    s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex(""" - .*$"""), " ")
        .filter { it.isLetterOrDigit() }

private fun artists(s: String): List<String> =
    s.split(Regex(""",|&| feat\.? | ft\.? | x |/""", RegexOption.IGNORE_CASE)).map(::norm).filter { it.isNotEmpty() }

private fun sameArtist(a: String, b: String): Boolean {
    val x = artists(a)
    return artists(b).any { it in x }
}

private fun sameTitle(a: String, b: String): Boolean {
    val x = norm(a)
    val y = norm(b)
    if (x.isEmpty() || y.isEmpty()) return false
    return x == y || (minOf(x.length, y.length) >= 4 && (x.contains(y) || y.contains(x)))
}

private val JAPANESE = Regex("[\u3040-\u30ff\u4e00-\u9fff]")
private val TIMESTAMP = Regex("""\[[0-9:.]+]""")
private val WORD = Regex("[a-z']+")
private val ENGLISH = setOf(
    "the", "you", "i", "and", "to", "me", "my", "a", "is", "of", "it", "in", "that", "your", "be",
    "so", "just", "can", "with", "what", "this", "all", "for", "on", "are", "we", "don't", "i'm", "it's",
)

/**
 * 0 = the original in Japanese script, 1 = romaji or other Latin text, 2 = looks like an English
 * translation. English only wins when nothing else exists (songs that really are in English).
 */
private fun languageTier(r: JsonObject): Int {
    val text = (r.str("syncedLyrics") ?: "").replace(TIMESTAMP, " ")
    if (JAPANESE.findAll(text).take(20).count() >= 20) return 0
    val words = WORD.findAll(text.lowercase()).map { it.value }.toList()
    val english = if (words.isEmpty()) 0.0 else words.count { it in ENGLISH }.toDouble() / words.size
    return if (english < 0.25) 1 else 2
}

private fun lengthGap(r: JsonObject, t: Track): Double {
    val d = r["duration"]?.jsonPrimitive?.doubleOrNull ?: return 99.0
    return if (t.duration > 0) kotlin.math.abs(d - t.duration) else 0.0
}

/**
 * LRCLIB's search is fuzzy and happily returns other people's songs, so a hit only counts when it
 * is really this song: the same artist, and the same title or the same length to two seconds
 * (Japanese songs are often listed under their kanji title).
 */
private fun isThisSong(r: JsonObject, t: Track): Boolean {
    if (!sameArtist(r.str("artistName") ?: return false, t.artist)) return false
    val d = r["duration"]?.jsonPrimitive?.doubleOrNull
    val gap = if (t.duration > 0 && d != null) kotlin.math.abs(d - t.duration) else null
    return if (sameTitle(r.str("trackName") ?: "", t.title)) gap == null || gap <= 8.0 else gap != null && gap <= 2.0
}

suspend fun lookupLyrics(p: Platform, t: Track): AutoLyrics? {
    // v3: earlier versions could keep lyrics of another song or a translation; those answers are dropped
    val key = "lrc3:${t.id}"
    p.kv.get(key)?.let { cached ->
        val a = runCatching { json.decodeFromString<AutoLyrics>(cached) }.getOrNull()
        if (a != null && (a.synced != null || a.instrumental || nowMs() - a.at < RETRY_MS)) return a
    }
    val artist = mainArtist(t.artist)
    val found = runCatching {
        // the exact match (title + artist + length) and the looser search together: anyone can upload
        // to LRCLIB, so the same song often has several versions, translations among them
        val exact = if (t.duration > 0) runCatching {
            json.parseToJsonElement(
                p.httpGetText("$API/get?track_name=${enc(t.title)}&artist_name=${enc(artist)}&duration=${t.duration.toInt()}", HEADERS),
            ).jsonObject
        }.getOrNull() else null
        if (exact != null && exact.bool("instrumental") && isThisSong(exact, t)) return@runCatching exact
        val search = (json.parseToJsonElement(p.httpGetText("$API/search?track_name=${enc(t.title)}&artist_name=${enc(artist)}", HEADERS)) as? JsonArray)
            ?.map { it.jsonObject }.orEmpty()
        (listOfNotNull(exact) + search)
            .filter { it.str("syncedLyrics") != null && isThisSong(it, t) }
            .minWithOrNull(compareBy<JsonObject>({ languageTier(it) }, { lengthGap(it, t) }))
    }.getOrElse { return null } // offline: try again next time
    val result = AutoLyrics(found?.str("syncedLyrics"), found?.bool("instrumental") == true, nowMs())
    p.kv.set(key, json.encodeToString(AutoLyrics.serializer(), result))
    return result
}
