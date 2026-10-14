package uz.kawaii.radio.kr01.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.KeyEvent
import android.widget.RemoteViews
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import uz.kawaii.radio.kr01.MainActivity
import uz.kawaii.radio.kr01.R
import uz.kawaii.radio.kr01.audio.PlaybackService

/** Home screen widget: cover, a little LCD with the song, play/pause and next. */
class KawaiiWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetState.push(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = WidgetState.push(context)
}

/** What the widget shows; the playback service keeps it current. */
object WidgetState {
    @Volatile private var title = "Kawaii Radio"
    @Volatile private var artist = "KR-01 · J-POP FM"
    @Volatile private var playing = false
    @Volatile private var cover: Bitmap? = null

    fun update(context: Context, title: String?, artist: String?, playing: Boolean, cover: Bitmap? = this.cover) {
        if (title != null) this.title = title
        if (artist != null) this.artist = artist
        this.playing = playing
        this.cover = cover
        push(context)
    }

    fun push(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, KawaiiWidget::class.java))
        if (ids.isEmpty()) return
        for (id in ids) {
            // the LCD text is drawn for the widget's real width, so it is never shrunk
            val widthDp = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            mgr.updateAppWidget(id, views(context, (widthDp - CHROME_DP).coerceIn(80, 420)))
        }
    }

    /** Everything around the LCD in the layout: paddings, cover, gaps and the two keys. */
    private const val CHROME_DP = 8 + 52 + 10 + 20 + 8 + 46 + 6 + 46 + 8

    private fun views(context: Context, lcdDp: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_player)
        v.setImageViewBitmap(R.id.w_text, lcdText(context, title, artist, lcdDp))
        v.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        cover?.let { v.setImageViewBitmap(R.id.w_cover, it) }
        v.setOnClickPendingIntent(
            R.id.w_root,
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        v.setOnClickPendingIntent(R.id.w_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 1))
        v.setOnClickPendingIntent(R.id.w_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, 2))
        return v
    }

    /** A media key sent straight to the playback service (it starts it if needed). */
    private fun mediaButton(context: Context, key: Int, code: Int): PendingIntent {
        val i = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, key))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, code, i, flags)
        else PendingIntent.getService(context, code, i, flags)
    }

    /** The two LCD lines rendered with the pixel font, ellipsized to fit. */
    private fun lcdText(context: Context, title: String, artist: String, widthDp: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).toInt()
        val h = (44 * d).toInt()
        val font = ResourcesCompat.getFont(context, R.font.pixelify)
        val ink = ContextCompat.getColor(context, R.color.w_lcd_ink)
        val dim = ContextCompat.getColor(context, R.color.w_lcd_dim)
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 14 * d * context.resources.configuration.fontScale.coerceAtMost(1.2f); color = ink }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 11 * d; color = dim }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val t1 = TextUtils.ellipsize(title.uppercase(), big, w.toFloat(), TextUtils.TruncateAt.END).toString()
        val t2 = TextUtils.ellipsize(artist, small, w.toFloat(), TextUtils.TruncateAt.END).toString()
        c.drawText(t1, 0f, h * 0.46f, big)
        c.drawText(t2, 0f, h * 0.84f, small)
        return out
    }

    /** Rounded square cover for the widget (RemoteViews can't clip by themselves). */
    fun rounded(src: Bitmap, size: Int, radius: Float): Bitmap {
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createScaledBitmap(Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side), size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, p)
        return out
    }
}
