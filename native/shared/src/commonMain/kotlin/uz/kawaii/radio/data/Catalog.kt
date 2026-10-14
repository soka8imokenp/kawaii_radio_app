package uz.kawaii.radio.data

import kotlinx.serialization.json.Json
import uz.kawaii.radio.KeyValueStore

/** Cloudflare Worker that serves playlist.json and the uploaded songs (see cloud/). */
const val CLOUD_URL = "https://kawaii-radio.kawaii-radio-cloud.workers.dev"

private const val CLOUD_CACHE_KEY = "kr01-cloud"

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

/** Paths in playlist.json are relative to the Worker ("/files/audio/…"). */
private fun absolutize(t: Track): Track {
    fun abs(p: String) = if (p.startsWith("http:") || p.startsWith("https:")) p else CLOUD_URL + (if (p.startsWith("/")) "" else "/") + p
    return t.copy(audioSrc = abs(t.audioSrc), coverArt = if (t.coverArt.isBlank()) "" else abs(t.coverArt))
}

fun parseCloud(text: String): List<Track> =
    runCatching { json.decodeFromString<CloudPlaylist>(text).tracks.map(::absolutize).distinctBy { it.id } }.getOrDefault(emptyList())

/** Last playlist we saw, so cloud songs show instantly (and offline) on the next launch. */
fun cachedCloud(kv: KeyValueStore): List<Track> = kv.get(CLOUD_CACHE_KEY)?.let(::parseCloud) ?: emptyList()

fun rememberCloud(kv: KeyValueStore, text: String) = kv.set(CLOUD_CACHE_KEY, text)
