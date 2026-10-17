package uz.kawaii.radio.data

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val category: String = "J-Pop",
    val coverArt: String = "",
    val audioSrc: String,
    val duration: Double = 0.0,
    val bpm: Int = 120,
    val energy: Int = 70,
    val lyrics: Lyrics? = null,
    val info: TrackInfo? = null,
) {
    /** Which radio station plays it; the same rule as stationOf() in cloud/src/index.ts. */
    val station: Station get() = stationOf(category, lyrics != null)
}

/** The three radio stations, each with its own broadcast (cloud: GET /radio?station=<key>). */
enum class Station(val key: String, val label: String, val freq: String) {
    /** Japanese songs with vocals: J-Pop, J-Rock, Vocaloid, anime openings and endings. */
    JPOP("jpop", "J-Pop", "J-POP FM"),
    /** Soundtracks, mostly without words: orchestra, piano, covers of anime and Ghibli themes. */
    OST("ost", "OST", "OST FM"),
    /** Music from Riot Games: League of Legends, Valorant, Arcane… */
    RIOT("riot", "Riot Music", "RIOT FM"),
}

private val RIOT_WORDS = listOf("riot", "league", "valorant", "arcane", "lol", "tft", "wild rift")
private val INSTRUMENTAL_WORDS = listOf("instrumental", "orchestra", "piano", "soundtrack")

/**
 * Riot words in the category → Riot Music; "Instrumental" (or an "OST" with no lyrics) → OST;
 * everything else, anime openings with vocals included, → J-Pop.
 */
fun stationOf(category: String, hasLyrics: Boolean): Station {
    val c = category.lowercase()
    val words = c.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    return when {
        RIOT_WORDS.any { w -> if (' ' in w) w in c else w in words } -> Station.RIOT
        INSTRUMENTAL_WORDS.any { it in c } -> Station.OST
        "ost" in words && !hasLyrics -> Station.OST
        else -> Station.JPOP
    }
}

@Serializable
data class Lyrics(val uzbek: String = "", val romaji: String? = null, val kanji: String? = null)

@Serializable
data class TrackInfo(val description: String, val details: List<Detail> = emptyList())

@Serializable
data class Detail(val title: String, val text: String)

@Serializable
data class CloudPlaylist(val tracks: List<Track> = emptyList())
