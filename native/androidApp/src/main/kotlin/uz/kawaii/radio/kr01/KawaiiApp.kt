package uz.kawaii.radio.kr01

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import uz.kawaii.radio.RadioStore
import uz.kawaii.radio.ui.UiState

/** Holds the app state for the whole process, so reopening the window is instant. */
class KawaiiApp : Application(), SingletonImageLoader.Factory {
    val scope = MainScope()
    lateinit var store: RadioStore
        private set
    val ui = UiState()
    lateinit var platform: AndroidPlatform
        private set

    override fun onCreate() {
        super.onCreate()
        platform = AndroidPlatform(this)
        store = RadioStore(platform, scope)
        scope.launch { store.start() }
        // chat notifications: hand the phone's FCM token to the chat server (refreshed tokens arrive in KawaiiMessagingService)
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { store.chat.onPushToken(it) }
        }
    }

    /** Same loader as before, plus animated webp for chat stickers (Android 9+; older phones show the first frame). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory()) }
            .build()
}
