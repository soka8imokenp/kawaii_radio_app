package uz.kawaii.radio.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kawaii.radio.AudioEngine
import uz.kawaii.radio.RadioStore
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.UiState
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.HeartIcon
import uz.kawaii.radio.ui.kit.Key
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.PauseIcon
import uz.kawaii.radio.ui.kit.PlayIcon
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.Screw
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.kit.lcd
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.motionSpring

enum class Tab { JPOP, OST, SAVED }

data class Entry(val t: Track, val i: Int)

fun tabList(tab: Tab, tracks: List<Track>, saved: List<String>): List<Entry> =
    tracks.mapIndexedNotNull { i, t ->
        val keep = when (tab) {
            Tab.SAVED -> t.id in saved
            Tab.OST -> t.isOst
            Tab.JPOP -> !t.isOst
        }
        if (keep) Entry(t, i) else null
    }

/**
 * Playback position in seconds: follows every frame while a song plays (smooth waveform scroll),
 * a few times a second otherwise (seeks while paused).
 */
@Composable
fun rememberClock(audio: AudioEngine, playing: Boolean): State<Double> {
    val clock = remember { mutableDoubleStateOf(audio.positionMs() / 1000.0) }
    val active = uz.kawaii.radio.ui.LocalPageActive.current
    LaunchedEffect(playing, active) {
        while (true) {
            // every frame only while it is playing on screen; hidden page: once a second is plenty
            if (playing && active) withFrameNanos { } else delay(if (active) 200 else 1_000)
            clock.doubleValue = audio.positionMs() / 1000.0
        }
    }
    return clock
}

/** Pull-down lyrics drawer: how far the dark panel is pulled (px) and whether it is open. */
class Drawer(val ext: Animatable<Float, *>, open: Boolean) {
    var open by mutableStateOf(open)
    var max = 0f
}

/** Main radio screen: dark selector panel on top, hardware controls below. */
@Composable
fun Home(ui: UiState) {
    val store = LocalStore.current
    val tracks by store.tracks.collectAsState()
    val engine by store.engine.collectAsState()
    val saved by store.saved.collectAsState()
    val liked by store.liked.collectAsState()
    val t = store.current(tracks, engine.id) ?: return NoSignal()
    val clock = rememberClock(store.platform.audio, engine.playing)

    var tab by remember { mutableStateOf(if (t.isOst) Tab.OST else Tab.JPOP) }
    LaunchedEffect(t.id) {
        if (store.live && tab != Tab.SAVED) {
            tab = if (t.isOst) Tab.OST else Tab.JPOP
        }
    }
    val isLiked = t.id in liked
    val isSaved = t.id in saved
    // the tuner walks the current band; an empty Saved band falls back to J-Pop/OST
    val band = tabList(tab, tracks, saved).ifEmpty { tabList(if (t.isOst) Tab.OST else Tab.JPOP, tracks, saved) }
    val pos = band.indexOfFirst { it.i == tracks.indexOf(t) }.coerceAtLeast(0)
    val at = { d: Int -> band[(((pos + d) % band.size) + band.size) % band.size] }
    val downloads by store.downloads.collectAsState()
    val saving = downloads[t.id]
    val pass by store.pass.collectAsState()
    val onSave = { if (pass) store.toggleSave(t.id) else ui.premium = true }

    val scope = rememberCoroutineScope()
    val drawer = remember { Drawer(Animatable(0f), false) }
    val settle = { to: Boolean ->
        drawer.open = to
        scope.launch { drawer.ext.animateTo(if (to) drawer.max else 0f, motionSpring(320f, 36f)) }
        Unit
    }
    DisposableEffect(drawer.open) {
        ui.closeDrawer = if (drawer.open) ({ settle(false) }) else null
        onDispose { }
    }

    val changeTab = { next: Tab ->
        tab = next
        val list = tabList(next, tracks, saved)
        // live broadcast: tabs only browse, the song on air keeps playing
        if (!store.live && list.isNotEmpty() && list.none { it.t.id == t.id }) store.play(list[0].t.id, list.map { it.t.id })
    }

    val density = LocalDensity.current.density
    // the controls slide out of the bottom while the lyrics drawer is open: keep them inside the screen
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val rootH = constraints.maxHeight.toFloat()
        // short screens: the cover flow gives up height so the hardware controls below always fit
        // (panel without covers ≈ 242dp, controls ≈ 236dp)
        val coverScale = ((maxHeight.value - 242f - 236f) / 260f).coerceIn(0.62f, 1f)
        Column(Modifier.fillMaxSize()) {
            TopPanel(
                track = t,
                tracks = tracks,
                tab = tab,
                onTab = changeTab,
                onPremium = { ui.premium = true },
                drawer = drawer,
                clock = clock,
                coverScale = coverScale,
                onToggle = { settle(!drawer.open) },
                onDragEnd = { v ->
                    settle(if (v > 300 * density) true else if (v < -300 * density) false else drawer.ext.value > drawer.max / 2)
                },
                modifier = Modifier.zIndex(1f).layout { m, c ->
                    // the panel grows over the controls without pushing them (they slide away by themselves)
                    val p = m.measure(c.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                    val ext = drawer.ext.value.toInt()
                    val base = p.height - ext
                    drawer.max = (rootH - base - 6 * density).coerceAtLeast(0f)
                    layout(p.width, base) { p.place(0, 0) }
                },
            )

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { translationY = drawer.ext.value }
                    .then(if (drawer.open) Modifier.pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
                    } else Modifier),
            ) {
                // recessed seam groove of the housing
                GrooveH(Modifier.align(Alignment.TopCenter))
                Column(
                    Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Screw(Modifier.align(Alignment.CenterStart).offset(x = (-12).dp))
                        Screw(Modifier.align(Alignment.CenterEnd).offset(x = 12.dp))
                        Box(Modifier.fillMaxWidth().tray(RoundedCornerShape(22.dp)).padding(5.dp)) {
                            WaveLcd(t, engine.playing, engine.durationMs, clock)
                        }
                    }

                    // transport: embossed round keys around the wheel scrubber
                    Box(Modifier.padding(top = 8.dp)) {
                        GrooveH(Modifier.align(Alignment.Center).bleed(16.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Screw(Modifier.offset(x = (-6).dp))
                            Key(CircleShape, Modifier.size(52.dp), onPress = { store.toggle() }) {
                                AnimatedContent(
                                    engine.playing,
                                    transitionSpec = {
                                        (fadeIn(tween(120)) + scaleIn(tween(120), 0.5f)) togetherWith (fadeOut(tween(120)) + scaleOut(tween(120), 0.5f))
                                    },
                                ) { playing -> if (playing) PauseIcon(K.ink) else PlayIcon(K.ink) }
                            }
                            Screw()
                            Tuner(
                                modifier = Modifier.weight(1f),
                                onStep = { d -> store.play(at(d).t.id, band.map { it.t.id }) },
                                preview = { d -> at(d).t.title },
                            )
                            Screw()
                            Key(CircleShape, Modifier.size(52.dp), active = isLiked, onPress = { store.toggleLike(t.id) }) {
                                LikePop(isLiked)
                            }
                            Screw(Modifier.offset(x = 6.dp))
                        }
                    }

                    // segmented function keys docked in one tray
                    Row(
                        Modifier.padding(top = 8.dp).fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Key(RoundedCornerShape(15.dp), Modifier.weight(1f).height(40.dp), onPress = { ui.info = t.id }) {
                            Px(Glyph.INFO, 19.dp, K.ctlInk)
                            BasicText("TAVSIF", style = T.micro())
                        }
                        Key(RoundedCornerShape(15.dp), Modifier.weight(1f).height(40.dp), active = isSaved, onPress = onSave) {
                            Px(Glyph.SAVE, 19.dp, K.ctlInk)
                            BasicText(
                                when {
                                    saving != null -> "SAQLANMOQDA ${(saving * 100).toInt()}%"
                                    isSaved -> "SAQLANGAN"
                                    else -> "SAQLASH"
                                },
                                style = T.micro(),
                            )
                            if (isSaved) Led(true)
                        }
                    }
                }
            }
        }
    }
}

/** Heart pops in with a bouncy spring when liked. */
@Composable
private fun LikePop(liked: Boolean) {
    val s = remember { Animatable(1f) }
    var first by remember { mutableIntStateOf(0) }
    LaunchedEffect(liked) {
        if (first++ > 0 && liked) {
            s.snapTo(0.4f)
            s.animateTo(1f, motionSpring(600f, 12f))
        }
    }
    Box(Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value }) { HeartIcon(liked, K.ctlInk) }
}

/** Lets a child stick out [x] on both sides of its parent's padding (like CSS -inset-x). */
fun Modifier.bleed(x: androidx.compose.ui.unit.Dp) = layout { m, c ->
    val extra = x.roundToPx() * 2
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

/** Very first launch without internet: nothing cached yet, so there is nothing to tune in to. */
@Composable
private fun NoSignal() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().tray(RoundedCornerShape(22.dp)).padding(5.dp)) {
            Column(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(16.dp))
                    .border(2.dp, K.lcdBorder, RoundedCornerShape(16.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText("SIGNAL YO'Q", style = T.pixel(22.sp, K.lcdInk, lh = 22.sp))
                BasicText(
                    "Qo'shiqlar internetdan yuklanadi. Internetga ulaning: radio o'zi ishga tushadi.",
                    Modifier.padding(top = 10.dp),
                    style = T.pixel(13.sp, K.lcdInk.copy(alpha = 0.7f), lh = 17.sp).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
            }
        }
    }
}
