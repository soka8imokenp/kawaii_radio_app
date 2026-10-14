package uz.kawaii.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import uz.kawaii.radio.data.CLOUD_URL
import uz.kawaii.radio.data.CloudPlaylist
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.data.cachedCloud
import uz.kawaii.radio.data.json
import uz.kawaii.radio.data.parseCloud
import uz.kawaii.radio.data.rememberCloud

enum class Lang { UZ, JP }

@Serializable
private data class Saved(
    val id: String? = null,
    val liked: List<String> = emptyList(),
    val saved: List<String> = emptyList(),
    val lang: String = "uz",
)

private const val STATE_KEY = "kr01-state"
private const val PREMIUM_KEY = "kr01-premium"

/**
 * App state shared by every screen: the catalog (built-in + cloud songs), likes, songs kept
 * offline, lyric language. Playback itself lives in [Platform.audio]; its queue mirrors [tracks].
 */
class RadioStore(val platform: Platform, private val scope: CoroutineScope) {
    private val kv = platform.kv
    private val audio = platform.audio
    private val saved0 = kv.get(STATE_KEY)?.let { runCatching { json.decodeFromString<Saved>(it) }.getOrNull() } ?: Saved()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _liked = MutableStateFlow(saved0.liked)
    val liked: StateFlow<List<String>> = _liked.asStateFlow()

    /** Songs kept inside the app (Kawaii Pass feature). */
    private val _saved = MutableStateFlow(saved0.saved)
    val saved: StateFlow<List<String>> = _saved.asStateFlow()

    private val _lang = MutableStateFlow(if (saved0.lang == "jp") Lang.JP else Lang.UZ)
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    /** Kawaii Pass subscription (temporary flag until the kawaii.uz backend exists). */
    val premium: Boolean = kv.get(PREMIUM_KEY) == "1"

    /** Short "static" moment on the LCD while switching stations. */
    private val _tuning = MutableStateFlow(false)
    val tuning: StateFlow<Boolean> = _tuning.asStateFlow()

    val engine: StateFlow<EngineState> = audio.state

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var tuneJob: Job? = null

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
        val start = all.indexOfFirst { it.id == saved0.id }.coerceAtLeast(0)
        if (all.isNotEmpty()) audio.setQueue(all.map(::queueItem), start)
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
            // first launch: nothing was cached yet, so this is the initial queue
            if (_tracks.value.isEmpty()) audio.setQueue(all.map(::queueItem), 0) else audio.syncQueue(all.map(::queueItem))
            _tracks.value = all
        }
    }

    fun current(list: List<Track> = _tracks.value, id: String? = engine.value.id): Track? =
        list.firstOrNull { it.id == id } ?: list.firstOrNull()

    fun select(id: String, play: Boolean = true) {
        val i = _tracks.value.indexOfFirst { it.id == id }
        if (i >= 0) audio.select(i, play)
    }

    fun toggle() = if (engine.value.playing) audio.pause() else audio.play()
    fun seek(seconds: Double) = audio.seekTo((seconds.coerceAtLeast(0.0) * 1000).toLong())

    fun toggleLike(id: String) {
        _liked.value = if (id in _liked.value) _liked.value - id else _liked.value + id
        persist()
    }

    fun setLang(l: Lang) {
        _lang.value = l
        persist()
    }

    /** Keeps the song inside the app so it plays offline. */
    fun toggleSave(id: String) {
        val t = _tracks.value.firstOrNull { it.id == id } ?: return
        if (id in _saved.value) {
            _saved.value = _saved.value - id
            platform.removeOffline(id)
        } else {
            _saved.value = _saved.value + id
            // built-in songs are already inside the app
            if (t.audioSrc.startsWith("http")) scope.launch { platform.saveOffline(id, t.audioSrc) }
        }
        persist()
    }

    private fun persist() {
        val s = Saved(engine.value.id, _liked.value, _saved.value, if (_lang.value == Lang.JP) "jp" else "uz")
        kv.set(STATE_KEY, json.encodeToString(Saved.serializer(), s))
    }
}
