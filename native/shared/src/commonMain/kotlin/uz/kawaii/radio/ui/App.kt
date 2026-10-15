package uz.kawaii.radio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import uz.kawaii.radio.RadioStore
import uz.kawaii.radio.ThemeMode
import uz.kawaii.radio.data.UpdateInfo
import uz.kawaii.radio.data.checkForUpdate
import uz.kawaii.radio.data.skipBuild
import uz.kawaii.radio.ui.home.Home
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.housing
import uz.kawaii.radio.ui.sheets.ArtistSheet
import uz.kawaii.radio.ui.sheets.AvatarSheet
import uz.kawaii.radio.ui.sheets.InfoSheet
import uz.kawaii.radio.ui.sheets.LibrarySheet
import uz.kawaii.radio.ui.sheets.LeaveLiveSheet
import uz.kawaii.radio.ui.sheets.PremiumSheet
import uz.kawaii.radio.ui.sheets.SongSheet
import uz.kawaii.radio.ui.sheets.UpdateSheet
import uz.kawaii.radio.ui.sheets.WrappedSheet
import uz.kawaii.radio.ui.theme.Chalk
import uz.kawaii.radio.ui.theme.Graphite
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.LocalFonts
import uz.kawaii.radio.ui.theme.rememberFonts

enum class Page { Home, Search, Chat, Liked, Profile }

val LocalStore = staticCompositionLocalOf<RadioStore> { error("store not provided") }
val LocalUi = staticCompositionLocalOf<UiState> { error("ui not provided") }

/** Screen-level state the system back button can unwind. */
class UiState {
    var page by mutableStateOf(Page.Home)
    /** Song whose "Tavsif" sheet is open. */
    var info by mutableStateOf<String?>(null)
    /** Song whose card (dates, plays, actions) is open. */
    var song by mutableStateOf<String?>(null)
    var premium by mutableStateOf(false)
    /** "You are on the live broadcast, leave it?" (Kawaii Pass holders only). */
    var leaveLive by mutableStateOf(false)
    var avatarPicker by mutableStateOf(false)
    var artistPicker by mutableStateOf(false)
    var wrapped by mutableStateOf(false)
    var library by mutableStateOf(false)
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateBusy by mutableStateOf(false)
    /** Set by Home while its lyrics drawer is open; closes it. */
    var closeDrawer: (() -> Unit)? = null

    /** True when something was closed, false when the app should go to the background. */
    fun back(onSkipUpdate: (UpdateInfo) -> Unit): Boolean {
        // mandatory update: back can't close it (the app is useless until it is installed)
        if (update != null) return true
        if (info != null) { info = null; return true }
        if (song != null) { song = null; return true }
        if (premium) { premium = false; return true }
        if (leaveLive) { leaveLive = false; return true }
        if (avatarPicker) { avatarPicker = false; return true }
        if (artistPicker) { artistPicker = false; return true }
        if (wrapped) { wrapped = false; return true }
        if (library) { library = false; return true }
        closeDrawer?.let { it(); return true }
        if (page != Page.Home) { page = Page.Home; return true }
        return false
    }
}

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun KawaiiApp(store: RadioStore, ui: UiState) {
    // chalk or graphite, before anything reads a colour
    val mode by store.theme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.AUTO && systemDark)
    K.p = if (dark) Graphite else Chalk

    CompositionLocalProvider(
        LocalFonts provides rememberFonts(),
        LocalSound provides store.platform.sound,
        LocalStore provides store,
        LocalUi provides ui,
    ) {
        var booted by remember { mutableStateOf(false) }
        var splashGone by remember { mutableStateOf(false) }

        // something that needs Kawaii Pass was tried (e.g. picking a song in the live broadcast)
        LaunchedEffect(Unit) { store.passNeeded.collect { ui.premium = true } }
        LaunchedEffect(Unit) { store.leaveLiveAsk.collect { ui.leaveLive = true } }

        // on every launch: ask GitHub Releases whether a newer build exists
        LaunchedEffect(booted) {
            if (!booted) return@LaunchedEffect
            val u = runCatching { checkForUpdate(store.platform) }.getOrNull()
            // every new release is mandatory: "Keyinroq" no longer exists
            if (u != null) ui.update = u
        }
        // and again whenever the app comes back from the background, so nobody stays on an old version
        LaunchedEffect(booted) {
            if (!booted) return@LaunchedEffect
            store.resumed.collect {
                if (ui.update == null) runCatching { checkForUpdate(store.platform) }.getOrNull()?.let { ui.update = it }
            }
        }

        // the radio has a dark panel under the status bar; the other pages are plastic all the way up
        val onHome = ui.page == Page.Home
        val strip by animateColorAsState(if (onHome) K.bar else K.base2, tween(220))
        SideEffect { store.platform.setSystemBarIcons(statusDark = splashGone && !onHome && !dark, navDark = !dark) }

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
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(strip))
                Box(Modifier.weight(1f).fillMaxWidth()) { Pages(ui) }
                // while typing (chat, search) the keyboard takes the bottom: the player and the nav step aside,
                // so the text field sits right on the keyboard
                val typing = imeVisible()
                // on the chat page the player sits in the header instead (Chat.kt: NowPlayingChip)
                AnimatedVisibility(!onHome && ui.page != Page.Chat && !typing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    MiniPlayer(onOpen = { ui.page = Page.Home })
                }
                if (!typing) NavBar(ui.page) { ui.page = it }
            }

            LibrarySheet(open = ui.library, onClose = { ui.library = false })
            SongSheet(id = ui.song, onClose = { ui.song = null })
            InfoSheet(id = ui.info, onClose = { ui.info = null })
            PremiumSheet(open = ui.premium, onClose = { ui.premium = false })
            LeaveLiveSheet(open = ui.leaveLive, onAnswer = { leave -> ui.leaveLive = false; store.answerLeaveLive(leave) })
            AvatarSheet(open = ui.avatarPicker, onClose = { ui.avatarPicker = false })
            ArtistSheet(open = ui.artistPicker, onClose = { ui.artistPicker = false })
            WrappedSheet(open = ui.wrapped, onClose = { ui.wrapped = false })
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
                Page.Search -> Search(active)
                Page.Chat -> Chat(active)
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun imeVisible(): Boolean = WindowInsets.isImeVisible
