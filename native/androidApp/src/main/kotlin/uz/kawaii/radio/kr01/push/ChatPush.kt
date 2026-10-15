package uz.kawaii.radio.kr01.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import uz.kawaii.radio.kr01.KawaiiApp
import uz.kawaii.radio.kr01.MainActivity
import uz.kawaii.radio.kr01.R

/** Receives chat pushes (data-only FCM messages sent by cloud/src/chat.ts) and new FCM tokens. */
class KawaiiMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as KawaiiApp).store.chat.onPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] != "chat") return
        val chat = (application as KawaiiApp).store.chat
        // switched off in Profil, or the listener is reading the chat right now
        if (!chat.pushOn.value || chat.isReading) return
        val name = d["name"].orEmpty().ifEmpty { "Mehmon" }
        // called by "@name" or answered: say so, like a messenger does
        val text = if (d["mention"] == "1") "@ sizni eslatdi: ${d["text"].orEmpty()}" else d["text"].orEmpty()
        ChatNotifications.show(this, name, text, d["ts"]?.toLongOrNull() ?: System.currentTimeMillis())
    }
}

/** One conversation-style notification for the chat, with the latest messages stacked like a messenger. */
object ChatNotifications {
    private const val CHANNEL = "chat"
    private const val ID = 4242
    const val EXTRA_OPEN = "open"
    const val OPEN_CHAT = "chat"

    private data class Line(val name: String, val text: String, val ts: Long)
    private val lines = ArrayDeque<Line>()

    fun show(context: Context, name: String, text: String, ts: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val recent = synchronized(lines) {
            lines.addLast(Line(name, text, ts))
            while (lines.size > 8) lines.removeFirst()
            lines.toList()
        }
        ensureChannel(context)
        val me = Person.Builder().setName("Siz").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Kawaii Radio Chat")
            .setGroupConversation(true)
        for (l in recent) style.addMessage(l.text, l.ts, Person.Builder().setName(l.name).build())

        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_CHAT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(context, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification) // the white Kawaii bunny
            .setColor(0xFF22C55E.toInt())
            .setStyle(style)
            .setContentTitle(name)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }

    /** Opening the chat reads everything: the notification goes away. */
    fun clear(context: Context) {
        synchronized(lines) { lines.clear() }
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Chat", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tinglovchilar chatidagi yangi xabarlar"
            },
        )
    }
}
