@file:OptIn(kotlin.time.ExperimentalTime::class)

package uz.kawaii.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import uz.kawaii.radio.data.AutoLyrics
import uz.kawaii.radio.data.CLOUD_URL
import uz.kawaii.radio.data.lookupLyrics
import uz.kawaii.radio.data.monthKey
import uz.kawaii.radio.data.CloudPlaylist
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.data.cachedCloud
import uz.kawaii.radio.data.json
import uz.kawaii.radio.data.parseCloud
import uz.kawaii.radio.data.rememberCloud
import kotlin.time.Clock

enum class Lang { UZ, JP }
enum class ThemeMode { AUTO, LIGHT, DARK }

/** Listening history of one song. */
@Serializable
data class PlayStat(val plays: Int = 0, val seconds: Long = 0, val last: Long = 0)

@Serializable
data class Profile(
    val name: String = "",
    /** "p:<n>" = one of the pixel avatars, "photo" = picture from the gallery (stored by the platform). */
    val avatar: String = "p:0",
    val favArtist: String? = null,
)

@Serializable
private data class Saved(
    val id: String? = null,
    /** liked song id → when it was liked (epoch ms), oldest first */
    val likes: Map<String, Long> = emptyMap(),
    val saved: List<String> = emptyList(),
    val lang: String = "uz",
    val theme: String = "auto",
    val crossfade: Int = 4,
    val queue: List<String> = emptyList(),
    val stats: Map<String, PlayStat> = emptyMap(),
    val recent: List<String> = emptyList(),
    val profile: Profile = Profile(),
    /** "2026-10" → song id → seconds listened that month */
    val months: Map<String, Map<String, Long>> = emptyMap(),
)

private const val STATE_KEY = "kr01-state"
private const val PREMIUM_KEY = "kr01-premium"
/** A song counts as "played" after this much listening. */
private const val PLAY_SECONDS = 30

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * App state shared by every screen: the catalog (cloud songs), likes, listening history, profile,
 * songs kept offline, lyric language, theme. Playback itself lives in [Platform.audio].
 */
class RadioStore(val platform: Platform, private val scope: CoroutineScope) {
    private val kv = platform.kv
    private val audio = platform.audio
    private val saved0 = kv.get(STATE_KEY)?.let { runCatching { json.decodeFromString<Saved>(it) }.getOrNull() } ?: Saved()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _likes = MutableStateFlow(saved0.likes)
    /** Liked song id → when it was liked. */
    val likes: StateFlow<Map<String, Long>> = _likes.asStateFlow()
    private val _liked = MutableStateFlow(saved0.likes.keys.toList())
    /** Liked song ids, oldest first. */
    val liked: StateFlow<List<String>> = _liked.asStateFlow()

    /** Songs kept inside the app (Kawaii Pass feature). */
    private val _saved = MutableStateFlow(saved0.saved)
    val saved: StateFlow<List<String>> = _saved.asStateFlow()

    private val _lang = MutableStateFlow(if (saved0.lang == "jp") Lang.JP else Lang.UZ)
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.entries.firstOrNull { it.name.equals(saved0.theme, true) } ?: ThemeMode.AUTO)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _crossfade = MutableStateFlow(saved0.crossfade)
    /** Seconds of crossfade between songs, 0 = off. */
    val crossfade: StateFlow<Int> = _crossfade.asStateFlow()

    fun setCrossfade(seconds: Int) {
        _crossfade.value = seconds
        audio.setCrossfade(seconds)
        persist()
    }

    private val _stats = MutableStateFlow(saved0.stats)
    val stats: StateFlow<Map<String, PlayStat>> = _stats.asStateFlow()

    private val _months = MutableStateFlow(saved0.months)
    /** Listening per month, for "Kawaii Wrapped". */
    val months: StateFlow<Map<String, Map<String, Long>>> = _months.asStateFlow()

    private val _recent = MutableStateFlow(saved0.recent)
    /** Recently played song ids, newest first. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _profile = MutableStateFlow(saved0.profile)
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    /** When the sleep timer stops the music (epoch ms), null when off. */
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()

    /** Kawaii Pass subscription (temporary flag until the kawaii.uz backend exists). */
    val premium: Boolean = kv.get(PREMIUM_KEY) == "1"

    /** Short "static" moment on the LCD while switching stations. */
    private val _tuning = MutableStateFlow(false)
    val tuning: StateFlow<Boolean> = _tuning.asStateFlow()

    val engine: StateFlow<EngineState> = audio.state

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var tuneJob: Job? = null
    private var sleepJob: Job? = null

    /** Fires whenever the app comes back to the foreground. */
    private val _resumed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resumed: SharedFlow<Unit> = _resumed.asSharedFlow()

    fun onResume() {
        _resumed.tryEmit(Unit)
        refreshCatalog()
    }

    suspend fun start() {
        // every song lives in the cloud; the last playlist seen is kept so the app opens instantly (and offline)
        val all = cachedCloud(kv)
        _tracks.value = all
        audio.setCrossfade(_crossfade.value)
        restoreQueue(all, saved0.queue, saved0.id)
        _ready.value = true
        refreshCatalog()
        scope.launch {
            audio.state.map { it.id }.distinctUntilChanged().drop(1).collect {
                tuneJob?.cancel()
                _tuning.value = true
                tuneJob = scope.launch { delay(900); _tuning.value = false }
                persist()
            }
        }
        scope.launch { trackListening() }
    }

    private fun restoreQueue(all: List<Track>, queue: List<String>, id: String?) {
        if (all.isEmpty()) return
        val byId = all.associateBy { it.id }
        val q = queue.mapNotNull { byId[it] }.ifEmpty { all }
        audio.setQueue(q.map(::queueItem), q.indexOfFirst { it.id == id }.coerceAtLeast(0))
    }

    private fun queueItem(t: Track) = QueueItem(
        id = t.id,
        uri = platform.offlineUri(t.id) ?: t.audioSrc,
        title = t.title,
        artist = t.artist,
        artworkUri = t.coverArt,
    )

    /** Pulls the cloud playlist; called on launch and whenever the app comes back to the foreground. */
    fun refreshCatalog() {
        if (!_ready.value) return
        scope.launch {
            val text = runCatching { platform.httpGetText("$CLOUD_URL/playlist.json") }.getOrNull() ?: return@launch
            // a broken answer must not wipe the songs we already have
            if (runCatching { json.decodeFromString<CloudPlaylist>(text) }.isFailure) return@launch
            rememberCloud(kv, text)
            val all = parseCloud(text)
            if (all == _tracks.value) return@launch
            if (_tracks.value.isEmpty()) {
                // first launch: nothing was cached yet, so this is the initial queue
                restoreQueue(all, emptyList(), null)
            } else {
                // keep the current queue, minus songs that were removed, with fresh addresses
                val byId = all.associateBy { it.id }
                val q = engine.value.queue.mapNotNull { byId[it] }.ifEmpty { all }
                audio.syncQueue(q.map(::queueItem))
            }
            _tracks.value = all
        }
    }

    fun track(id: String?): Track? = _tracks.value.firstOrNull { it.id == id }

    fun current(list: List<Track> = _tracks.value, id: String? = engine.value.id): Track? =
        list.firstOrNull { it.id == id } ?: list.firstOrNull()

    /**
     * Plays [id] with [context] as the queue (the tab, the liked list, search results…), like a
     * streaming app does. When that queue is already loaded it simply jumps, without a gap.
     */
    fun play(id: String, context: List<String> = engine.value.queue, shuffle: Boolean = false) {
        val ctx = context.ifEmpty { _tracks.value.map { it.id } }
        val q = engine.value.queue
        audio.setShuffle(shuffle)
        if (q == ctx) {
            val i = q.indexOf(id)
            if (i >= 0) { audio.select(i, true); return }
        }
        val byId = _tracks.value.associateBy { it.id }
        val items = ctx.mapNotNull { byId[it] }
        val start = items.indexOfFirst { it.id == id }.coerceAtLeast(0)
        audio.setQueue(items.map(::queueItem), start)
        audio.play()
        persist()
    }

    /** "Barchasini tinglash" / "Aralashtirish": the whole list from the start, or shuffled. */
    fun playAll(ids: List<String>, shuffle: Boolean) {
        if (ids.isEmpty()) return
        play(if (shuffle) ids.random() else ids.first(), ids, shuffle)
    }

    /** Puts the song right after the current one. */
    fun playNext(id: String) {
        val t = track(id) ?: return
        audio.insertNext(queueItem(t))
    }

    fun toggle() = if (engine.value.playing) audio.pause() else audio.play()
    fun next() = audio.next()
    fun seek(seconds: Double) = audio.seekTo((seconds.coerceAtLeast(0.0) * 1000).toLong())

    fun toggleLike(id: String) {
        val m = LinkedHashMap(_likes.value)
        if (m.remove(id) == null) m[id] = nowMs()
        _likes.value = m
        _liked.value = m.keys.toList()
        persist()
    }

    fun setLang(l: Lang) {
        _lang.value = l
        persist()
    }

    fun setTheme(t: ThemeMode) {
        _theme.value = t
        persist()
    }

    fun updateProfile(f: (Profile) -> Profile) {
        _profile.value = f(_profile.value)
        persist()
    }

    private val lyricJobs = HashMap<String, Deferred<AutoLyrics?>>()

    /** Automatic lyrics, looked up once per song and shared by every screen; a failed lookup is retried later. */
    suspend fun autoLyrics(t: Track): AutoLyrics? {
        val job = lyricJobs.getOrPut(t.id) { scope.async { lookupLyrics(platform, t) } }
        val r = job.await()
        if (r == null) lyricJobs.remove(t.id)
        return r
    }

    /** Keeps the song inside the app so it plays offline. */
    fun toggleSave(id: String) {
        val t = track(id) ?: return
        if (id in _saved.value) {
            _saved.value = _saved.value - id
            platform.removeOffline(id)
        } else {
            _saved.value = _saved.value + id
            if (t.audioSrc.startsWith("http")) scope.launch { platform.saveOffline(id, t.audioSrc) }
        }
        persist()
    }

    /** Sleep timer: the music fades out and stops after [minutes]; null turns it off. */
    fun setSleep(minutes: Int?) {
        sleepJob?.cancel()
        audio.setVolume(1f)
        if (minutes == null) { _sleepAt.value = null; return }
        val at = nowMs() + minutes * 60_000L
        _sleepAt.value = at
        sleepJob = scope.launch {
            while (nowMs() < at - 10_000) delay(1_000)
            // gentle 10 s fade, then stop
            for (i in 10 downTo 1) {
                audio.setVolume(i / 10f)
                delay(1_000)
            }
            audio.pause()
            delay(300)
            audio.setVolume(1f)
            _sleepAt.value = null
        }
    }

    /** Counts listening time per song, plays (after 30 s) and the recently played list. */
    private suspend fun trackListening() {
        var sessionId: String? = null
        var sessionSeconds = 0
        var sinceSave = 0
        while (scope.isActive) {
            delay(1_000)
            val e = engine.value
            if (!e.playing || e.id == null) continue
            if (e.id != sessionId) {
                sessionId = e.id
                sessionSeconds = 0
                _recent.value = (listOf(e.id) + _recent.value.filter { it != e.id }).take(30)
            }
            sessionSeconds++
            val old = _stats.value[e.id] ?: PlayStat()
            val counted = if (sessionSeconds == PLAY_SECONDS) 1 else 0
            _stats.value = _stats.value + (e.id to old.copy(plays = old.plays + counted, seconds = old.seconds + 1, last = nowMs()))
            val mk = monthKey(nowMs())
            val month = _months.value[mk].orEmpty()
            _months.value = _months.value + (mk to (month + (e.id to (month[e.id] ?: 0L) + 1)))
            if (++sinceSave >= 15 || counted == 1) { sinceSave = 0; persist() }
        }
    }

    private fun persist() {
        val s = Saved(
            id = engine.value.id,
            likes = _likes.value,
            saved = _saved.value,
            lang = if (_lang.value == Lang.JP) "jp" else "uz",
            theme = _theme.value.name.lowercase(),
            crossfade = _crossfade.value,
            queue = engine.value.queue,
            stats = _stats.value,
            recent = _recent.value,
            profile = _profile.value,
            months = _months.value,
        )
        kv.set(STATE_KEY, json.encodeToString(Saved.serializer(), s))
    }
}
