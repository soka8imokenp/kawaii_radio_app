package uz.kawaii.radio.kr01

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import uz.kawaii.radio.data.skipBuild
import uz.kawaii.radio.ui.KawaiiApp as KawaiiUi

class MainActivity : ComponentActivity() {
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
        onBackPressedDispatcher.addCallback(this) {
            // nothing left to close: keep playing in the background, like a music app should
            if (!app.ui.back { skipBuild(app.store.platform, it.build) }) moveTaskToBack(true)
        }
        setContent { KawaiiUi(app.store, app.ui) }
    }

    override fun onResume() {
        super.onResume()
        (application as KawaiiApp).store.onResume()
    }
}
