package uz.kawaii.radio.data

import kotlinx.serialization.Serializable
import uz.kawaii.radio.KeyValueStore

/**
 * An artist page (Qidiruv → Ijrochilar). Written in Uzbek and kept in the cloud (data/artists.json,
 * built by cloud/artists.mjs), so texts can be improved without a new app version.
 */
@Serializable
data class Artist(
    val id: String,
    val name: String,
    /** Other spellings used in song credits ("ZUTOMAYO", "Zutomayo"…). */
    val aliases: List<String> = emptyList(),
    /** Official artist picture (~500 px), shown from its source (see [pictureSource]). */
    val picture: String? = null,
    /** The same picture small (~250 px) for avatars in lists. */
    val thumb: String? = null,
    val pictureSource: String? = null,
    /** e.g. "J-Pop · Vocaloid" */
    val genre: String = "",
    /** e.g. "Tokio, Yaponiya" */
    val origin: String = "",
    /** e.g. "2017-yildan" */
    val since: String = "",
    /** One line under the name: who this is in a few words. */
    val tagline: String = "",
    /** The story, paragraph by paragraph. */
    val bio: List<String> = emptyList(),
    /** Their best-known songs (not necessarily in our catalogue). */
    val known: List<String> = emptyList(),
) {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Does a song credit (e.g. "Kenshi Yonezu, Hikaru Utada") name this artist? */
    fun sings(credit: String): Boolean {
        val names = (aliases + name).map(::norm).toSet()
        return credit.split(Regex(""",| feat\.? | ft\.? | & | x |/""", RegexOption.IGNORE_CASE)).any { norm(it) in names }
    }
}

@Serializable
private data class ArtistsFile(val artists: List<Artist> = emptyList())

private const val ARTISTS_KEY = "kr01-artists"

fun cachedArtists(kv: KeyValueStore): List<Artist> =
    kv.get(ARTISTS_KEY)?.let { runCatching { json.decodeFromString<ArtistsFile>(it).artists }.getOrNull() }.orEmpty()

/** Parses the cloud file and remembers it for the next (offline) launch; null when it is broken. */
fun parseArtists(kv: KeyValueStore, text: String): List<Artist>? {
    val list = runCatching { json.decodeFromString<ArtistsFile>(text).artists }.getOrNull() ?: return null
    kv.set(ARTISTS_KEY, text)
    return list
}
