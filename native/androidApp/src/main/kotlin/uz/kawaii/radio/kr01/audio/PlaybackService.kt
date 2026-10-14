package uz.kawaii.radio.kr01.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Bundle
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uz.kawaii.radio.kr01.KawaiiApp
import uz.kawaii.radio.kr01.widget.WidgetState
import uz.kawaii.radio.QueueItem
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import uz.kawaii.radio.kr01.MainActivity
import uz.kawaii.radio.kr01.R

/**
 * Background playback: ExoPlayer inside a media session. Android shows the lock-screen /
 * notification controls, handles headphones and Bluetooth buttons, and keeps it alive in the background.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var crossfader: Crossfader? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaData.factory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // the radio never runs dry: after the last song it starts over
        player.repeatMode = Player.REPEAT_MODE_ALL
        crossfader = Crossfader(this, player)
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = updateWidget(p)
        })

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).setCallback(LikeCallback()).build()
        watchLikes()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /* ---------- home screen widget ---------- */

    private var widgetCoverUri: String? = null

    private fun updateWidget(p: Player) {
        val md = p.mediaMetadata
        WidgetState.update(this, md.title?.toString(), md.artist?.toString(), p.playWhenReady && p.playbackState != Player.STATE_ENDED)
        val art = md.artworkUri?.toString() ?: return
        if (art == widgetCoverUri) return
        widgetCoverUri = art
        scope.launch {
            val req = ImageRequest.Builder(this@PlaybackService).data(art).size(160).allowHardware(false).build()
            val bmp = (SingletonImageLoader.get(this@PlaybackService).execute(req) as? SuccessResult)?.image?.toBitmap() ?: return@launch
            WidgetState.update(this@PlaybackService, null, null, p.playWhenReady, WidgetState.rounded(bmp, 160, 28f))
        }
    }

    /* ---------- heart button in the notification / on the lock screen ---------- */

    private val scope = MainScope()
    private val store get() = (application as KawaiiApp).store

    private fun likeButton(liked: Boolean) = CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
        .setDisplayName(if (liked) "Sevimlilardan olib tashlash" else "Sevimlilarga qo'shish")
        .setSessionCommand(LIKE)
        .build()

    /** Keeps the heart in step with likes made anywhere (app, notification, another screen). */
    private fun watchLikes() {
        scope.launch {
            combine(store.liked, store.engine.map { it.id }.distinctUntilChanged()) { liked, id -> id != null && id in liked }
                .distinctUntilChanged()
                .collect { liked -> session?.setMediaButtonPreferences(listOf(likeButton(liked))) }
        }
    }

    private inner class LikeCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(LIKE).build())
                .build()

        /** Play pressed on the widget / headphones while nothing is loaded: continue the saved queue. */
        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val s = store
            val ids = s.engine.value.queue.ifEmpty { s.tracks.value.map { it.id } }
            val items = ids.mapNotNull { id -> s.track(id) }.map {
                QueueItem(it.id, s.platform.offlineUri(it.id) ?: it.audioSrc, it.title, it.artist, it.coverArt).toMediaItem()
            }
            val start = ids.indexOf(s.engine.value.id).coerceAtLeast(0)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, start.coerceAtMost((items.size - 1).coerceAtLeast(0)), 0))
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand == LIKE) store.engine.value.id?.let(store::toggleLike)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        crossfader?.release()
        crossfader = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

private val LIKE = SessionCommand("uz.kawaii.radio.LIKE", Bundle.EMPTY)
