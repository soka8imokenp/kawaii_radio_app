package uz.kawaii.radio.kr01

import android.graphics.Color
import android.os.Bundle
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
import uz.kawaii.radio.ui.KawaiiApp as KawaiiUi

class MainActivity : ComponentActivity() {
    private val platform get() = (application as KawaiiApp).platform
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { platform.onPhotoPicked(it) }

    fun pickPhoto() = photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

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
