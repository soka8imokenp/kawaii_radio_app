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
    /** Normalised names of this artist (computed once, not for every song). */
    val keys: Set<String> by lazy { (aliases + name).map(::artistKey).toSet() }

    /** Does a song credit (e.g. "Kenshi Yonezu, Hikaru Utada") name this artist? */
    fun sings(credit: String): Boolean = creditKeys(credit).any { it in keys }
}

private val CREDIT_SPLIT = Regex(""",| feat\.? | ft\.? | & | x |/""", RegexOption.IGNORE_CASE)

fun artistKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** The artists named in a song credit, normalised. */
fun creditKeys(credit: String): List<String> = credit.split(CREDIT_SPLIT).map(::artistKey).filter { it.isNotEmpty() }

/** Precomputed once per catalogue change: artists ordered by how many of our songs they sing, with a cover each. */
data class ArtistIndex(
    val order: List<Artist> = emptyList(),
    val songs: Map<String, Int> = emptyMap(),
    val cover: Map<String, String> = emptyMap(),
)

fun buildArtistIndex(artists: List<Artist>, tracks: List<Track>): ArtistIndex {
    // one pass over the songs: credit name → songs, instead of artists × songs regex checks
    val byKey = HashMap<String, MutableList<Track>>()
    for (t in tracks) for (k in creditKeys(t.artist)) byKey.getOrPut(k) { mutableListOf() }.add(t)
    val songs = HashMap<String, Int>()
    val cover = HashMap<String, String>()
    for (a in artists) {
        val mine = a.keys.flatMap { byKey[it].orEmpty() }.distinctBy { it.id }
        songs[a.id] = mine.size
        mine.firstOrNull()?.coverArt?.let { cover[a.id] = it }
    }
    return ArtistIndex(artists.sortedByDescending { songs[it.id] ?: 0 }, songs, cover)
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
