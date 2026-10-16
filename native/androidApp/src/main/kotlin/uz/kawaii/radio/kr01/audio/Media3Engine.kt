package uz.kawaii.radio.kr01.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.kawaii.radio.AudioEngine
import uz.kawaii.radio.EngineState
import uz.kawaii.radio.QueueItem

/**
 * Talks to [PlaybackService] through a MediaController. Commands issued before the connection is
 * up are queued and replayed, so the UI never has to wait for it.
 */
class Media3Engine(context: Context) : AudioEngine {
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
            })
            pending.forEach { it(c) }
            pending.clear()
            publish()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun run(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    private var queueIds: List<String> = emptyList()

    private fun publish() {
        val c = controller ?: return
        val ids = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
        if (ids != queueIds) queueIds = ids
        val buffering = c.playbackState == Player.STATE_BUFFERING
        val playing = c.playWhenReady && (c.playbackState == Player.STATE_READY || buffering)
        _state.value = EngineState(
            id = c.currentMediaItem?.mediaId,
            queue = queueIds,
            playing = playing,
            buffering = buffering,
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
        )
    }

    override fun positionMs(): Long = controller?.currentPosition ?: 0

    private fun item(q: QueueItem): MediaItem = q.toMediaItem()

    override fun setQueue(items: List<QueueItem>, startIndex: Int) = run { c ->
        c.setMediaItems(items.map(::item), startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0)
        c.prepare()
        // show the restored song (and queue) right away, before the player reports back
        queueIds = items.map { it.id }
        _state.value = _state.value.copy(id = items.getOrNull(startIndex)?.id, queue = queueIds)
    }

    /**
     * Brings the player's queue in line with [items] using moves/inserts/removals only, so the song
     * that is playing is never interrupted.
     */
    override fun syncQueue(items: List<QueueItem>) = run { c ->
        val want = items.map { it.id }
        val wanted = want.toHashSet()
        val currentId = c.currentMediaItem?.mediaId
        // 1. drop songs that are gone (except the one playing)
        for (i in c.mediaItemCount - 1 downTo 0) {
            val id = c.getMediaItemAt(i).mediaId
            if (id !in wanted && id != currentId) c.removeMediaItem(i)
        }
        // 2. walk the wanted order: move existing items into place, insert new ones
        for ((i, q) in items.withIndex()) {
            val at = (i until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == q.id }
            when {
                at == i -> Unit
                at != null -> c.moveMediaItem(at, i)
                else -> c.addMediaItem(i, item(q))
            }
        }
        publish()
    }

    override fun select(index: Int, play: Boolean) = run { c ->
        if (index !in 0 until c.mediaItemCount) return@run
        c.seekTo(index, 0)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (play) c.play()
    }

    override fun play() = run { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    override fun pause() = run { it.pause() }
    override fun seekTo(ms: Long) = run { it.seekTo(ms) }

    override fun next() = run { c ->
        c.seekToNextMediaItem()
        c.play()
    }

    override fun previous() = run { c ->
        c.seekToPrevious()
        c.play()
    }

    override fun setShuffle(on: Boolean) = run { it.shuffleModeEnabled = on }

    override fun insertNext(item: QueueItem) = run { c ->
        val q = item
        // move it if it is already queued, so the song is never listed twice
        val existing = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == q.id }
        val target = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        when {
            existing == c.currentMediaItemIndex -> Unit
            existing != null -> c.moveMediaItem(existing, if (existing < target) target - 1 else target)
            else -> c.addMediaItem(target, this.item(q))
        }
        publish()
    }

    // the service applies it together with the crossfade curves
    override fun refreshItem(item: QueueItem) = run { c ->
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == item.id && i != c.currentMediaItemIndex) c.replaceMediaItem(i, item.toMediaItem())
        }
    }

    override fun setVolume(v: Float) { PlaybackTuning.master = v.coerceIn(0f, 1f) }

    override fun setCrossfade(seconds: Int) { PlaybackTuning.crossfadeMs = seconds.coerceAtLeast(0) * 1000L }
}

/** The player-side form of a queue entry (shared with the service's playback resumption). */
fun QueueItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle("Kawaii Radio")
            .apply { if (artworkUri.isNotEmpty()) setArtworkUri(Uri.parse(artworkUri)) }
            .build(),
    )
    .build()
