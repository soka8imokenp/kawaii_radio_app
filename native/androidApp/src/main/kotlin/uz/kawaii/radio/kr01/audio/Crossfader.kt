package uz.kawaii.radio.kr01.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Settings the app changes while the service runs (same process). */
object PlaybackTuning {
    /** 0 = off. */
    @Volatile var crossfadeMs: Long = 4_000
    /** Overall volume factor, used by the sleep timer's fade-out. */
    @Volatile var master: Float = 1f
}

/**
 * Real crossfade on top of a single session player: shortly before a song ends, a second
 * "tail" player is lined up on the same song, takes over its last seconds and fades out
 * while the main player already starts the next song and fades in.
 */
@OptIn(UnstableApi::class)
class Crossfader(context: Context, private val main: ExoPlayer) {
    private val tail = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(MediaData.factory(context)))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
        .build()
    private val handler = Handler(Looper.getMainLooper())

    private enum class Stage { IDLE, ARMED, FADING }
    private var stage = Stage.IDLE
    private var armedAt = 0L      // position (ms) in the outgoing song where the tail takes over
    private var armedIndex = -1
    private var fadeStart = 0L    // uptime ms
    private var fadeLen = 0L

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, if (stage == Stage.ARMED) 20 else 100)
        }
    }

    init {
        main.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && stage == Stage.FADING && main.playbackState != Player.STATE_BUFFERING) tail.pause()
                if (isPlaying && stage == Stage.FADING) tail.play()
            }
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                // a manual skip while lined up cancels the crossfade
                if (stage == Stage.ARMED && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) reset()
            }
        })
        handler.post(tick)
    }

    private fun reset() {
        stage = Stage.IDLE
        tail.stop()
        tail.clearMediaItems()
    }

    private fun step() {
        val master = PlaybackTuning.master
        val fade = PlaybackTuning.crossfadeMs
        when (stage) {
            Stage.IDLE -> {
                main.volume = master
                if (fade <= 0 || !main.isPlaying || main.repeatMode == Player.REPEAT_MODE_ONE) return
                val dur = main.duration
                if (dur == C.TIME_UNSET || dur < fade * 3) return
                val pos = main.currentPosition
                // line the tail up ~2 s before the fade, paused exactly where the fade will begin
                if (dur - pos <= fade + 2_000 && main.mediaItemCount > 1) {
                    val item = main.currentMediaItem ?: return
                    armedAt = (dur - fade).coerceAtLeast(pos + 300)
                    armedIndex = main.currentMediaItemIndex
                    tail.setMediaItem(item, armedAt)
                    tail.volume = 0f
                    tail.prepare()
                    stage = Stage.ARMED
                }
            }
            Stage.ARMED -> {
                if (main.currentMediaItemIndex != armedIndex) { reset(); return }
                main.volume = master
                if (main.currentPosition >= armedAt && tail.playbackState == Player.STATE_READY) {
                    // hand over: the tail continues the old song, the main player moves on silently
                    tail.volume = master
                    tail.play()
                    main.volume = 0f
                    main.seekToNextMediaItem()
                    fadeStart = android.os.SystemClock.uptimeMillis()
                    fadeLen = PlaybackTuning.crossfadeMs.coerceAtLeast(500)
                    stage = Stage.FADING
                }
            }
            Stage.FADING -> {
                val p = ((android.os.SystemClock.uptimeMillis() - fadeStart).toFloat() / fadeLen).coerceIn(0f, 1f)
                // equal-power curves keep the loudness steady through the blend
                main.volume = master * sin(p * PI / 2).toFloat()
                tail.volume = master * cos(p * PI / 2).toFloat()
                if (p >= 1f) { main.volume = master; reset() }
            }
        }
    }

    fun release() {
        handler.removeCallbacks(tick)
        tail.release()
    }
}
