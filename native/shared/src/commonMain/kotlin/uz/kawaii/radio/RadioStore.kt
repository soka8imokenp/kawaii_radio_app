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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import uz.kawaii.radio.chat.ChatStore
import uz.kawaii.radio.chat.PublicCard
import kotlinx.coroutines.flow.combine
import uz.kawaii.radio.data.mainArtist
import uz.kawaii.radio.data.Artist
import uz.kawaii.radio.data.cachedArtists
import uz.kawaii.radio.data.parseArtists
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import uz.kawaii.radio.data.AutoLyrics
import uz.kawaii.radio.data.CLOUD_URL
import uz.kawaii.radio.data.lookupLyrics
import uz.kawaii.radio.data.monthKey
import uz.kawaii.radio.data.CloudPlaylist
import uz.kawaii.radio.data.Station
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.data.cachedCloud
import uz.kawaii.radio.data.json
import uz.kawaii.radio.data.parseCloud
import uz.kawaii.radio.data.rememberCloud
import kotlin.time.Clock

enum class Lang { UZ, ROM, JP }
/** LIVE: the shared broadcast, the same song and second for everyone. PICK: any song, any order (Kawaii Pass). */
enum class Mode { LIVE, PICK }

/** One song of the broadcast timetable (epoch ms, ms), as served by the cloud at /radio. */
@Serializable
data class Slot(val id: String, val start: Long, val duration: Long)

@Serializable
private data class RadioSchedule(val serverTime: Long, val slots: List<Slot> = emptyList(), val station: String? = null)
enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * Listening history of one song. [seconds] = listened by choice (O'Z TANLOVIM), [radio] = heard on
 * the broadcast. Rankings ("most listened", top artists) use only the songs picked by choice, so the
 * broadcast doesn't decide anybody's taste; total listening time counts both.
 */
@Serializable
data class PlayStat(val plays: Int = 0, val seconds: Long = 0, val last: Long = 0, val radio: Long = 0) {
    val total: Long get() = seconds + radio
}

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
    val mode: String = "live",
    val station: String = "jpop",
)

private const val STATE_KEY = "kr01-state"
private const val PREMIUM_KEY = "kr01-premium"
/** Beta: Kawaii Pass is on for everybody until the real subscription exists. Set to false to lock it again. */
const val BETA = true
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

    private val _artists = MutableStateFlow(cachedArtists(kv))
    /** Artist pages (Qidiruv), from the cloud's data/artists.json. */
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    fun artist(id: String?): Artist? = _artists.value.firstOrNull { it.id == id }

    /** Artist order, song counts and fallback covers, rebuilt off the main thread when artists or songs change. */
    val artistIndex: StateFlow<uz.kawaii.radio.data.ArtistIndex> by lazy {
        combine(_artists, _tracks) { a, t -> uz.kawaii.radio.data.buildArtistIndex(a, t) }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, uz.kawaii.radio.data.ArtistIndex())
    }

    /** Songs of [a] in our catalogue, the ones listened to most first. */
    fun songsOf(a: Artist): List<Track> =
        _tracks.value.filter { a.sings(it.artist) }.sortedByDescending { _stats.value[it.id]?.seconds ?: 0L }

    private val _likes = MutableStateFlow(saved0.likes)
    /** Liked song id → when it was liked. */
    val likes: StateFlow<Map<String, Long>> = _likes.asStateFlow()
    private val _liked = MutableStateFlow(saved0.likes.keys.toList())
    /** Liked song ids, oldest first. */
    val liked: StateFlow<List<String>> = _liked.asStateFlow()

    /** Songs kept inside the app (Kawaii Pass feature). */
    private val _saved = MutableStateFlow(saved0.saved)
    val saved: StateFlow<List<String>> = _saved.asStateFlow()

    private val _lang = MutableStateFlow(
        when (saved0.lang) {
            "jp" -> Lang.JP
            "rom" -> Lang.ROM
            else -> Lang.UZ
        }
    )
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.entries.firstOrNull { it.name.equals(saved0.theme, true) } ?: ThemeMode.AUTO)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _crossfade = MutableStateFlow(saved0.crossfade)
    /** Seconds of crossfade between songs, 0 = off. */
    val crossfade: StateFlow<Int> = _crossfade.asStateFlow()

    fun setCrossfade(seconds: Int) {
        _crossfade.value = seconds
        if (!live) audio.setCrossfade(seconds) // the broadcast changes songs on its own timetable
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

    private val _pass = MutableStateFlow(BETA || kv.get(PREMIUM_KEY) == "1")
    /** Kawaii Pass subscription (temporary local flag until the kawaii.uz backend exists). */
    val pass: StateFlow<Boolean> = _pass.asStateFlow()
    val premium: Boolean get() = _pass.value

    private val _mode = MutableStateFlow(if (saved0.mode == "pick" && _pass.value) Mode.PICK else Mode.LIVE)
    /** Listening mode; without Kawaii Pass it is always the live broadcast. */
    val mode: StateFlow<Mode> = _mode.asStateFlow()
    val live: Boolean get() = _mode.value == Mode.LIVE

    private val _station = MutableStateFlow(Station.entries.firstOrNull { it.key == saved0.station } ?: Station.JPOP)
    /** The station on the dial: in LIVE its broadcast plays, otherwise its songs are the list on screen. */
    val station: StateFlow<Station> = _station.asStateFlow()

    /**
     * Turns the dial to another station. LIVE tunes in to that station's broadcast (playing on if
     * music was playing); otherwise the songs of the station become the queue, unless the song
     * that plays already belongs to it.
     */
    fun setStation(s: Station) {
        if (s == _station.value) return
        _station.value = s
        persist()
        if (live) {
            scope.launch { tuneIn(play = engine.value.playing) }
        } else {
            val list = _tracks.value.filter { it.station == s }
            val cur = track(engine.value.id)
            if (list.isNotEmpty() && cur?.station != s) play(list[0].id, list.map { it.id })
        }
    }

    private val _passNeeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires when the listener tried something that needs Kawaii Pass (picking a song in the live broadcast…). */
    val passNeeded: SharedFlow<Unit> = _passNeeded.asSharedFlow()

    private val _leaveLiveAsk = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires when a Kawaii Pass holder tried to pick, skip or seek while on the broadcast. */
    val leaveLiveAsk: SharedFlow<Unit> = _leaveLiveAsk.asSharedFlow()
    private var pendingAction: (() -> Unit)? = null

    /**
     * Live: blocks the action. Without Kawaii Pass it asks to subscribe; with it, it asks whether to
     * leave the broadcast and keeps [retry] to run once the listener agrees. Returns true when blocked.
     */
    private fun blockedInLive(retry: () -> Unit): Boolean {
        if (!live) return false
        if (premium) {
            pendingAction = retry
            _leaveLiveAsk.tryEmit(Unit)
        } else {
            _passNeeded.tryEmit(Unit)
        }
        return true
    }

    /** Answer to "leave the broadcast?": on yes, LIVE goes off and the blocked action runs. */
    fun answerLeaveLive(leave: Boolean) {
        val action = pendingAction
        pendingAction = null
        if (!leave) return
        setMode(Mode.PICK)
        action?.invoke()
    }

    fun setMode(m: Mode) {
        if (m == Mode.PICK && !premium) { _passNeeded.tryEmit(Unit); return }
        if (m == _mode.value) return
        _mode.value = m
        persist()
        if (m == Mode.LIVE) {
            audio.setCrossfade(0) // the timetable decides where songs change
            scope.launch { tuneIn(play = engine.value.playing) }
        } else {
            // keep the song that is on, now with the whole catalogue as the queue
            audio.setCrossfade(_crossfade.value)
            val id = engine.value.id
            val pos = audio.positionMs()
            val wasPlaying = engine.value.playing
            val all = _tracks.value
            if (all.isEmpty()) return
            audio.setQueue(all.map(::queueItem), all.indexOfFirst { it.id == id }.coerceAtLeast(0))
            audio.seekTo(pos)
            if (wasPlaying) audio.play()
        }
    }

    /* ---------- live broadcast ---------- */

    /** Timetables of the stations, as far as this phone knows them. */
    private val schedules = HashMap<Station, List<Slot>>()
    private val schedule: List<Slot> get() = schedules[_station.value].orEmpty()
    /** Cloud without stations (an older Worker): one timetable for every song, used for every station. */
    private var oneSchedule = false
    /** Server clock minus phone clock, so every phone agrees on "now". */
    private var skew = 0L
    private fun liveNow() = nowMs() + skew

    private suspend fun fetchSchedule(st: Station): Boolean {
        val t0 = nowMs()
        val text = runCatching { platform.httpGetText("$CLOUD_URL/radio?station=${st.key}") }.getOrNull() ?: return false
        val t1 = nowMs()
        val r = runCatching { json.decodeFromString<RadioSchedule>(text) }.getOrNull() ?: return false
        skew = r.serverTime - (t0 + t1) / 2
        oneSchedule = r.station == null
        if (oneSchedule) Station.entries.forEach { schedules[it] = r.slots } else schedules[st] = r.slots
        return r.slots.isNotEmpty()
    }

    /** The slot on air at [at] among songs this phone knows, or null. */
    private fun slotAt(at: Long): Slot? {
        val known = _tracks.value.associateBy { it.id }
        return schedule.firstOrNull { it.start <= at && at < it.start + it.duration && it.id in known }
    }

    /**
     * Joins the broadcast where it is right now: the song on air, from the right second, with the
     * following songs of the timetable queued behind it.
     */
    private suspend fun tuneIn(play: Boolean) {
        val st = _station.value
        if (schedule.isEmpty() || schedule.last().start - liveNow() < 30 * 60_000) fetchSchedule(st)
        if (st != _station.value) return // the dial moved on while we were asking
        val now = liveNow()
        val byId = _tracks.value.associateBy { it.id }
        val ahead = schedule.filter { it.start + it.duration > now && it.id in byId }
        // nothing on this station yet (e.g. Riot Music before its first songs): silence, not the old station
        if (ahead.isEmpty()) { if (track(engine.value.id)?.station != st) audio.pause(); return }
        // the same song can't sit twice in the engine's queue: stop at the first repeat
        val seen = HashSet<String>()
        val slots = ahead.takeWhile { seen.add(it.id) }
        audio.setCrossfade(0)
        audio.setQueue(slots.map { queueItem(byId.getValue(it.id)) }, 0)
        audio.seekTo((now - slots[0].start).coerceAtLeast(0))
        if (play) audio.play()
    }

    /** Keeps a playing phone on the broadcast: corrects drift and skipped songs without interrupting buffering. */
    private suspend fun keepOnAir() {
        var lastSyncMs = 0L
        while (scope.isActive) {
            delay(5_000)
            val e = engine.value
            if (!live || !e.playing || e.buffering || schedule.isEmpty()) continue
            val now = liveNow()
            val slot = slotAt(now) ?: continue
            if (e.id == slot.id) {
                val expected = now - slot.start
                if (now - lastSyncMs > 30_000 && kotlin.math.abs(audio.positionMs() - expected) > 12_000) {
                    lastSyncMs = now
                    audio.seekTo(expected)
                }
            } else {
                // right at a song change the two may differ for a moment; only act on a real mismatch
                val near = slotAt(now - 8_000)?.id == e.id || slotAt(now + 8_000)?.id == e.id
                if (!near && now - lastSyncMs > 15_000) {
                    lastSyncMs = now
                    tuneIn(play = true)
                }
            }
        }
    }

    private val _trending = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    /** "Hozir ko'p tinglanmoqda": songs most people listened to this week (song id → listeners). */
    val trending: StateFlow<List<Pair<String, Int>>> = _trending.asStateFlow()
    private var trendingAt = 0L

    /** Fetches [trending] again, at most every 5 minutes. */
    fun refreshTrending() {
        if (nowMs() - trendingAt < 5 * 60_000) return
        trendingAt = nowMs()
        scope.launch { chat.trending()?.let { _trending.value = it } ?: run { trendingAt = 0L } }
    }

    /** Temporary: "Obuna bo'lish" simply turns Kawaii Pass on, on this phone. */
    fun activatePass() {
        kv.set(PREMIUM_KEY, "1")
        _pass.value = true
    }

    /** Short "static" moment on the LCD while switching stations. */
    private val _tuning = MutableStateFlow(false)
    val tuning: StateFlow<Boolean> = _tuning.asStateFlow()

    val engine: StateFlow<EngineState> = audio.state

    /** The global listeners' chat; connects the first time the Chat page is opened. */
    val chat: ChatStore by lazy {
        // the public profile other listeners see: favourite artist, likes, listening, top artists, song on now
        val card = combine(_profile, _liked, _stats, audio.state, _tracks) { pr, liked, stats, e, tracks ->
            val byId = tracks.associateBy { it.id }
            val top = stats.entries
                .mapNotNull { (id, st) -> byId[id]?.let { mainArtist(it.artist) to st.seconds } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                .entries.sortedByDescending { it.value }.take(3).map { it.key }
            PublicCard(
                fav = pr.favArtist, likes = liked.size,
                plays = stats.values.sumOf { it.plays }, minutes = (stats.values.sumOf { it.total } / 60).toInt(),
                top = top, now = if (e.playing) e.id else null,
            )
        }.stateIn(scope, SharingStarted.Eagerly, PublicCard())
        ChatStore(platform, scope, profile, mode, audio.state.map { it.playing }.stateIn(scope, SharingStarted.Eagerly, false), card)
    }

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var tuneJob: Job? = null
    private var sleepJob: Job? = null

    /** Fires whenever the app comes back to the foreground. */
    private val _resumed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resumed: SharedFlow<Unit> = _resumed.asSharedFlow()

    /** App left the screen: the chat server may send notifications again. */
    fun onPause() = chat.onPause()

    fun onResume() {
        _resumed.tryEmit(Unit)
        refreshCatalog()
        chat.onResume()
        // back from the background while paused: show what is on air now
        if (live && !engine.value.playing) scope.launch { tuneIn(play = false) }
    }

    suspend fun start() {
        // every song lives in the cloud; the last playlist seen is kept so the app opens instantly (and offline)
        val all = cachedCloud(kv)
        _tracks.value = all
        audio.setCrossfade(if (live) 0 else _crossfade.value)
        restoreQueue(all, saved0.queue, saved0.id)
        _ready.value = true
        refreshCatalog()
        if (live) scope.launch { tuneIn(play = false) }
        scope.launch { keepOnAir() }
        // the chat connects at launch: unread badge, listener counter and typing need it
        chat.start()
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
            val text = runCatching { platform.httpGetText("$CLOUD_URL/files/data/artists.json") }.getOrNull() ?: return@launch
            parseArtists(kv, text)?.let { _artists.value = it }
        }
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
                if (live) { _tracks.value = all; tuneIn(play = false); return@launch }
            } else if (live) {
                // the broadcast queue only holds known songs; new ones join on the next tune-in
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
        if (blockedInLive { play(id, context, shuffle) }) return
        // a context without the song (e.g. the broadcast queue just left) falls back to the whole catalogue
        val ctx = context.ifEmpty { _tracks.value.map { it.id } }.let { c -> if (id in c) c else _tracks.value.map { it.id } }
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
        if (blockedInLive { playAll(ids, shuffle) }) return
        if (ids.isEmpty()) return
        play(if (shuffle) ids.random() else ids.first(), ids, shuffle)
    }

    /** Puts the song right after the current one. */
    fun playNext(id: String) {
        if (blockedInLive { playNext(id) }) return
        val t = track(id) ?: return
        audio.insertNext(queueItem(t))
    }

    /** Live: pausing leaves the broadcast running; Play joins it again where it is now. */
    fun toggle() {
        when {
            engine.value.playing -> audio.pause()
            live -> scope.launch { tuneIn(play = true) }
            else -> audio.play()
        }
    }
    fun next() { if (!blockedInLive { next() }) audio.next() }
    fun seek(seconds: Double) { if (!blockedInLive { seek(seconds) }) audio.seekTo((seconds.coerceAtLeast(0.0) * 1000).toLong()) }

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

    private val _downloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** Songs being saved for offline listening → progress 0..1. */
    val downloads: StateFlow<Map<String, Float>> = _downloads.asStateFlow()

    /** Keeps the song inside the app so it plays without internet; tapping again removes the copy. */
    fun toggleSave(id: String) {
        if (id in _downloads.value) return
        if (id in _saved.value) {
            _saved.value = _saved.value - id
            platform.removeOffline(id)
            track(id)?.let { audio.refreshItem(queueItem(it)) }
            persist()
        } else {
            scope.launch { save(id) }
        }
    }

    /** "Hammasini saqlash": every song of [ids] that is not saved yet, one after another. */
    fun saveAll(ids: List<String>) {
        val todo = ids.filter { it !in _saved.value && it !in _downloads.value }
        if (todo.isEmpty()) return
        _downloads.value = _downloads.value + todo.associateWith { 0f }
        scope.launch { for (id in todo) save(id) }
    }

    private suspend fun save(id: String) {
        val t = track(id) ?: return
        _downloads.value = _downloads.value + (id to 0f)
        val ok = platform.saveOffline(id, t.audioSrc) { p -> _downloads.value = _downloads.value + (id to p) }
        _downloads.value = _downloads.value - id
        if (ok) {
            _saved.value = (_saved.value + id).distinct()
            // from now on the queue plays the local copy
            audio.refreshItem(queueItem(t))
            persist()
        }
    }

    private val _exporting = MutableStateFlow<Set<String>>(emptySet())
    val exporting: StateFlow<Set<String>> = _exporting.asStateFlow()

    /** Puts the mp3 into the phone's Music folder; returns where it went, or null. */
    suspend fun exportToPhone(id: String): String? {
        val t = track(id) ?: return null
        _exporting.value = _exporting.value + id
        return try {
            platform.exportToPhone(id, t.audioSrc, "${t.artist} - ${t.title}")
        } finally {
            _exporting.value = _exporting.value - id
        }
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
            val onAir = live
            _stats.value = _stats.value + (e.id to old.copy(
                plays = old.plays + counted, last = nowMs(),
                seconds = old.seconds + if (onAir) 0 else 1, radio = old.radio + if (onAir) 1 else 0,
            ))
            // the month's top list (Wrapped) is about songs listened to by choice
            if (!onAir) {
                val mk = monthKey(nowMs())
                val month = _months.value[mk].orEmpty()
                _months.value = _months.value + (mk to (month + (e.id to (month[e.id] ?: 0L) + 1)))
            }
            if (counted == 1) chat.reportPlay(e.id)
            if (++sinceSave >= 15 || counted == 1) { sinceSave = 0; persist() }
        }
    }

    private fun persist() {
        val s = Saved(
            id = engine.value.id,
            likes = _likes.value,
            saved = _saved.value,
            lang = _lang.value.name.lowercase(),
            theme = _theme.value.name.lowercase(),
            crossfade = _crossfade.value,
            queue = engine.value.queue,
            stats = _stats.value,
            recent = _recent.value,
            profile = _profile.value,
            months = _months.value,
            mode = if (_mode.value == Mode.PICK) "pick" else "live",
            station = _station.value.key,
        )
        kv.set(STATE_KEY, json.encodeToString(Saved.serializer(), s))
    }
}
