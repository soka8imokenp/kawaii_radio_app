package uz.kawaii.radio

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the shared UI needs from the operating system. Android implements it in :androidApp;
 * an iOS app only has to provide its own implementation (AVPlayer, NSUserDefaults…).
 */
interface Platform {
    val kv: KeyValueStore
    val audio: AudioEngine
    val sound: ClickSound
    val updater: UpdateInstaller

    /** Name shown in Profil, e.g. 1.0.14 (or "dev"). */
    val appVersion: String

    /** Whole response body as text; throws on network errors or non-2xx answers. */
    suspend fun httpGetText(url: String, headers: Map<String, String> = emptyMap()): String

    /** Loudness of the track in [bars] buckets (0..1), or null when it can't be read. Cached by the platform. */
    suspend fun waveform(trackId: String, uri: String, bars: Int): FloatArray?

    /** A tiny, already-blurred copy of the cover for the ambient glow behind the cover flow. */
    suspend fun glowBitmap(uri: String): ImageBitmap?

    /** The cover as a 1-bit LCD bitmap (ordered dither), [size]×[size] pixels, ink on transparent. */
    suspend fun pixelCover(uri: String, size: Int): ImageBitmap?

    /** System bar icons: dark ones over light surfaces, light ones over dark surfaces. */
    fun setSystemBarIcons(statusDark: Boolean, navDark: Boolean)

    /** Lets the user pick a photo for the avatar; returns its local uri, or null when cancelled. */
    suspend fun pickAvatarPhoto(): String?
    /** The stored avatar photo, if any. */
    fun avatarPhoto(): String?

    /** Keeps the song on the device so it plays offline; returns false when it could not be stored. */
    suspend fun saveOffline(trackId: String, uri: String): Boolean
    fun removeOffline(trackId: String)
    /** Local copy of a saved song, if there is one. */
    fun offlineUri(trackId: String): String?

    fun openUrl(url: String)
}

interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/** Short synthesized mechanical clicks. */
interface ClickSound {
    /** Light detent of the tuner wheel. */
    fun tick()
    /** Heavier clack of a physical key. */
    fun press()
}

data class QueueItem(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
)

data class EngineState(
    /** Id of the current song, null before anything is loaded. */
    val id: String? = null,
    /** Ids in the play queue, in order. */
    val queue: List<String> = emptyList(),
    val playing: Boolean = false,
    val durationMs: Long = 0,
)

/** The background player (Media3 on Android). Indices are positions in its play queue. */
interface AudioEngine {
    val state: StateFlow<EngineState>

    /** Live playback position; cheap enough to read every frame. */
    fun positionMs(): Long

    fun setQueue(items: List<QueueItem>, startIndex: Int)
    /** Applies a new catalog without interrupting the song that is playing. */
    fun syncQueue(items: List<QueueItem>)
    fun select(index: Int, play: Boolean)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun next()
    fun previous()
    fun setShuffle(on: Boolean)
    /** Puts a song right after the current one. */
    fun insertNext(item: QueueItem)
    /** 0..1, used by the sleep timer to fade out. */
    fun setVolume(v: Float)
}

sealed interface InstallResult {
    data object Started : InstallResult
    data object NeedsPermission : InstallResult
}

interface UpdateInstaller {
    /** Installed build number (Android versionCode). */
    val installedBuild: Int
    /** Downloads the APK (resuming from a finished earlier download), reporting bytes; returns its path. */
    suspend fun download(url: String, build: Int, size: Long, onProgress: (done: Long, total: Long) -> Unit): String
    fun canInstall(): Boolean
    fun openInstallSettings()
    fun install(path: String): InstallResult
    /** Removes leftovers of earlier updates. */
    fun cleanup()
}
