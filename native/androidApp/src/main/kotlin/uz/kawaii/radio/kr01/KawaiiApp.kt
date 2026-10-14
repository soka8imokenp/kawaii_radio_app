package uz.kawaii.radio.kr01

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import uz.kawaii.radio.RadioStore
import uz.kawaii.radio.ui.UiState

/** Holds the app state for the whole process, so reopening the window is instant. */
class KawaiiApp : Application() {
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
    }
}
