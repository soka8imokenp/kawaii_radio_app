package uz.kawaii.radio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import uz.kawaii.radio.RadioStore
import uz.kawaii.radio.data.UpdateInfo
import uz.kawaii.radio.data.checkForUpdate
import uz.kawaii.radio.data.isSkipped
import uz.kawaii.radio.data.skipBuild
import uz.kawaii.radio.ui.home.Home
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.housing
import uz.kawaii.radio.ui.sheets.InfoSheet
import uz.kawaii.radio.ui.sheets.PremiumSheet
import uz.kawaii.radio.ui.sheets.UpdateSheet
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.LocalFonts
import uz.kawaii.radio.ui.theme.rememberFonts

enum class Page { Home, Liked, Profile }

val LocalStore = staticCompositionLocalOf<RadioStore> { error("store not provided") }

/** Screen-level state the system back button can unwind. */
class UiState {
    var page by mutableStateOf(Page.Home)
    var info by mutableStateOf(false)
    var premium by mutableStateOf(false)
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateBusy by mutableStateOf(false)
    /** Set by Home while its lyrics drawer is open; closes it. */
    var closeDrawer: (() -> Unit)? = null

    /** True when something was closed, false when the app should go to the background. */
    fun back(onSkipUpdate: (UpdateInfo) -> Unit): Boolean {
        update?.let { if (!updateBusy) { onSkipUpdate(it); update = null }; return true }
        if (info) { info = false; return true }
        if (premium) { premium = false; return true }
        closeDrawer?.let { it(); return true }
        if (page != Page.Home) { page = Page.Home; return true }
        return false
    }
}

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun KawaiiApp(store: RadioStore, ui: UiState) {
    CompositionLocalProvider(
        LocalFonts provides rememberFonts(),
        LocalSound provides store.platform.sound,
        LocalStore provides store,
    ) {
        var booted by remember { mutableStateOf(false) }
        var splashGone by remember { mutableStateOf(false) }

        // on every launch: ask GitHub Releases whether a newer build exists ("Keyinroq" hides that build until the next one)
        LaunchedEffect(booted) {
            if (!booted) return@LaunchedEffect
            val u = runCatching { checkForUpdate(store.platform) }.getOrNull()
            if (u != null && !isSkipped(store.platform, u.build)) ui.update = u
        }

        Box(Modifier.fillMaxSize().background(K.page), contentAlignment = Alignment.TopCenter) {
            val enter = remember { Animatable(0f) }
            LaunchedEffect(booted) { if (booted) enter.animateTo(1f, tween(700, easing = EaseOutQuint)) }
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxSize()
                    .graphicsLayer {
                        alpha = enter.value
                        val s = 0.96f + 0.04f * enter.value
                        scaleX = s; scaleY = s
                    }
                    .housing(),
            ) {
                // the dark status bar strip of the original (light icons over #2c2e33)
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(K.bar))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Pages(ui)
                }
                NavBar(ui.page) { ui.page = it }
            }

            InfoSheet(open = ui.info, onClose = { ui.info = false })
            PremiumSheet(open = ui.premium, onClose = { ui.premium = false })
            UpdateSheet(
                info = ui.update,
                onBusy = { ui.updateBusy = it },
                onClose = { ui.update?.let { skipBuild(store.platform, it.build) }; ui.update = null },
            )

            if (!splashGone) Splash(onBooted = { booted = true }, onGone = { splashGone = true })
        }
    }
}

/** All pages stay composed: switching only fades/slides, nothing is rebuilt. */
@Composable
private fun Pages(ui: UiState) {
    val order = Page.entries
    for (p in order) {
        val active = p == ui.page
        val side = if (order.indexOf(p) < order.indexOf(ui.page)) -1f else 1f
        val a by animateFloatAsState(if (active) 1f else 0f, tween(220, easing = EaseOutQuint))
        val shift = LocalDensity.current.density * 24f
        Box(
            Modifier.fillMaxSize()
                .zIndex(if (active) 1f else 0f)
                .graphicsLayer {
                    alpha = a
                    translationX = (1f - a) * side * shift
                }
                .then(if (active) Modifier else Modifier.blockInput()),
        ) {
            when (p) {
                Page.Home -> Home(ui)
                Page.Liked -> Liked(onOpenRadio = { ui.page = Page.Home })
                Page.Profile -> Profile(ui)
            }
        }
    }
}

/** Swallows touches meant for a page that is fading out. */
private fun Modifier.blockInput() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
