package uz.kawaii.radio.kr01

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import uz.kawaii.radio.data.skipBuild
import uz.kawaii.radio.kr01.push.ChatNotifications
import uz.kawaii.radio.ui.Page
import uz.kawaii.radio.ui.KawaiiApp as KawaiiUi

class MainActivity : ComponentActivity() {
    private val platform get() = (application as KawaiiApp).platform
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { platform.onPhotoPicked(it) }

    fun pickPhoto() = photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    /** Android 13+: chat (and player) notifications need the user's yes; asked once. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val kv = (application as KawaiiApp).platform.kv
        if (kv.get("asked-notifications") == "1") return
        kv.set("asked-notifications", "1")
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Tapping a chat notification opens the chat page. */
    private fun handleOpen(intent: Intent?) {
        if (intent?.getStringExtra(ChatNotifications.EXTRA_OPEN) == ChatNotifications.OPEN_CHAT) {
            (application as KawaiiApp).ui.page = Page.Chat
            intent.removeExtra(ChatNotifications.EXTRA_OPEN)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private var storageWait: CompletableDeferred<Boolean>? = null
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { storageWait?.complete(it) }

    /** Android 9 and older only: writing to the Music folder needs the storage permission. */
    suspend fun ensureStoragePermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true
        val wait = CompletableDeferred<Boolean>().also { storageWait = it }
        runOnUiThread { storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
        return wait.await()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // the system splash hands over at once: the Compose splash starts from the same frame
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // light icons over the dark top strip, dark icons over the chalk navigation area
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val app = application as KawaiiApp
        platform.activity = this
        onBackPressedDispatcher.addCallback(this) {
            // nothing left to close: keep playing in the background, like a music app should
            if (!app.ui.back { skipBuild(app.store.platform, it.build) }) moveTaskToBack(true)
        }
        setContent { KawaiiUi(app.store, app.ui) }
        handleOpen(intent)
        askNotificationsOnce()
    }

    override fun onPause() {
        (application as KawaiiApp).store.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (platform.activity === this) platform.activity = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        (application as KawaiiApp).store.onResume()
    }
}
