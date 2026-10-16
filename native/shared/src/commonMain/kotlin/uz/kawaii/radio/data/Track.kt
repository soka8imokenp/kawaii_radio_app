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
    /** Anime OST tab: the category mentions "OST"; everything else is J-Pop. */
    val isOst: Boolean get() = category.contains("ost", ignoreCase = true)
}

@Serializable
data class Lyrics(val uzbek: String = "", val romaji: String? = null, val kanji: String? = null)

@Serializable
data class TrackInfo(val description: String, val details: List<Detail> = emptyList())

@Serializable
data class Detail(val title: String, val text: String)

@Serializable
data class CloudPlaylist(val tracks: List<Track> = emptyList())
