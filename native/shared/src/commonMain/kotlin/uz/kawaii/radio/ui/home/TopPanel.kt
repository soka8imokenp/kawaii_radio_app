package uz.kawaii.radio.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.kawaii.radio.Lang
import uz.kawaii.radio.Mode
import uz.kawaii.radio.data.LyricLine
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.data.hasKanjiLrc
import uz.kawaii.radio.data.lineAt
import uz.kawaii.radio.data.parseKanjiLrc
import uz.kawaii.radio.data.parseLrc
import uz.kawaii.radio.data.AutoLyrics
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.LocalUi
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Logo
import uz.kawaii.radio.ui.kit.darkWell
import uz.kawaii.radio.ui.kit.dotScreen
import uz.kawaii.radio.ui.kit.pressClick
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lyricGlow
import uz.kawaii.radio.ui.theme.motionSpring
import kotlin.math.abs

private val TABS = listOf(Tab.JPOP to "J-Pop", Tab.OST to "Anime OST", Tab.SAVED to "Saqlangan")
private const val NO_LYRICS = "Bu qo'shiq uchun matn hali yo'q"

/** Lyrics of a song: 3 variants — Uzbek translation (UZ), Romaji (ROM), and Japanese Kanji/Kana (JP). */
class SongLyrics(
    val uzbek: String?,
    val romaji: String?,
    val kanji: String?,
    val auto: Boolean,
    val note: String,
) {
    val hasUz: Boolean get() = !uzbek.isNullOrBlank()
    val hasRom: Boolean get() = !romaji.isNullOrBlank() && parseLrc(romaji).isNotEmpty()
    val hasJp: Boolean get() = !kanji.isNullOrBlank() || hasKanjiLrc(romaji)

    val availableLangs: List<Lang> get() = buildList {
        if (hasUz) add(Lang.UZ)
        if (hasRom) add(Lang.ROM)
        if (hasJp) add(Lang.JP)
    }

    fun effectiveLang(lang: Lang): Lang = when {
        lang == Lang.UZ && hasUz -> Lang.UZ
        lang == Lang.JP && hasJp -> Lang.JP
        lang == Lang.ROM && hasRom -> Lang.ROM
        hasRom -> Lang.ROM
        hasUz -> Lang.UZ
        hasJp -> Lang.JP
        else -> Lang.ROM
    }

    fun nextLang(lang: Lang): Lang {
        val avail = availableLangs
        if (avail.size <= 1) return effectiveLang(lang)
        val cur = effectiveLang(lang)
        val idx = avail.indexOf(cur)
        return avail[(idx + 1) % avail.size]
    }

    fun linesFor(lang: Lang): List<LyricLine> = when (effectiveLang(lang)) {
        Lang.UZ -> if (!uzbek.isNullOrBlank()) parseLrc(uzbek) else emptyList()
        Lang.ROM -> if (!romaji.isNullOrBlank()) parseLrc(romaji) else emptyList()
        Lang.JP -> when {
            !kanji.isNullOrBlank() -> parseKanjiLrc(kanji)
            !romaji.isNullOrBlank() -> parseKanjiLrc(romaji)
            else -> emptyList()
        }
    }
}

@Composable
fun rememberSongLyrics(t: Track): SongLyrics {
    val store = LocalStore.current
    val own = t.lyrics
    if (own != null && (!own.uzbek.isBlank() || !own.romaji.isNullOrBlank() || !own.kanji.isNullOrBlank())) {
        return remember(t.id, own) { SongLyrics(own.uzbek, own.romaji, own.kanji, false, NO_LYRICS) }
    }
    val found by produceState<AutoLyrics?>(null, t.id) { value = store.autoLyrics(t) ?: AutoLyrics(at = -1) }
    val f = found
    return remember(t.id, f) {
        when {
            f == null -> SongLyrics(null, null, null, true, "Matn qidirilmoqda…")
            f.synced != null -> SongLyrics(null, f.synced, null, true, NO_LYRICS)
            f.instrumental -> SongLyrics(null, null, null, true, "♪ So'zsiz musiqa")
            else -> SongLyrics(null, null, null, true, NO_LYRICS)
        }
    }
}
private val PanelShape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)

@Composable
fun TopPanel(
    track: Track,
    tracks: List<Track>,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onPremium: () -> Unit,
    drawer: Drawer,
    clock: State<Double>,
    onToggle: () -> Unit,
    onDragEnd: (velocity: Float) -> Unit,
    coverScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val store = LocalStore.current
    val engine by store.engine.collectAsState()
    val saved by store.saved.collectAsState()
    val pass by store.pass.collectAsState()
    val rawList = remember(tab, tracks, saved) { tabList(tab, tracks, saved) }
    val list = remember(rawList, track, tab) {
        if (tab == Tab.SAVED || rawList.isEmpty() || rawList.any { it.t.id == track.id }) rawList
        else listOf(Entry(track, -1)) + rawList
    }

    Box(
        modifier.fillMaxWidth()
            .dropShadow(PanelShape, Shadow(26.dp, Color(0f, 0f, 0f, 0.38f), offset = DpOffset(0.dp, 12.dp)))
            .background(Brush.verticalGradient(0f to K.p.panelTop, 0.6f to K.p.panelMid, 1f to K.p.panelBottom), PanelShape)
            .innerShadow(PanelShape, Shadow(0.dp, Color(1f, 1f, 1f, 0.06f), offset = DpOffset(0.dp, (-1).dp)))
            .clip(PanelShape),
    ) {
        AmbientGlow(track.coverArt, engine.playing)
        Box(Modifier.matchParentSize().dotScreen(0.05f))

        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Header(engine.playing)
            Spacer(Modifier.height(8.dp))
            Segmented(tab, onTab)
            Spacer(Modifier.height(10.dp))
            if (list.isNotEmpty()) Covers(list, track.id, engine.playing, coverScale) else EmptySaved(pass, onPremium, coverScale)
            Spacer(Modifier.height(4.dp))
            NowCard(track, engine.playing, drawer, clock, onOpen = onToggle)
            Grip(drawer, onToggle, onDragEnd)
        }
    }
}

/**
 * Ambient light from the current cover, alive: three soft colour clouds taken from the cover
 * drift and breathe behind the cover flow. They slow almost to a halt while paused.
 */
@Composable
private fun BoxScope.AmbientGlow(cover: String, playing: Boolean) {
    val platform = LocalStore.current.platform
    val palette by produceState(emptyList<Color>(), cover) { value = platform.glowBitmap(cover)?.let(::coverColors) ?: emptyList() }
    val c0 by animateColorAsState(palette.getOrElse(0) { Color.Transparent }, tween(900))
    val c1 by animateColorAsState(palette.getOrElse(1) { Color.Transparent }, tween(900))
    val c2 by animateColorAsState(palette.getOrElse(2) { Color.Transparent }, tween(900))
    val alpha by animateFloatAsState(if (playing) 0.38f else 0.22f, tween(800))
    // a clock that runs at full speed while playing and creeps while paused
    val speed by animateFloatAsState(if (playing) 1f else 0.12f, tween(1200))
    val time = remember { mutableFloatStateOf(0f) }
    val active = uz.kawaii.radio.ui.LocalPageActive.current
    // the clouds drift only while music plays on a visible page; otherwise they hold still (no redraws)
    LaunchedEffect(playing, active) {
        if (!playing || !active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            time.floatValue += (now - last) / 1e9f * speed
            last = now
        }
    }
    Canvas(
        Modifier.matchParentSize().graphicsLayer { this.alpha = alpha },
    ) {
        val t = time.floatValue
        val cx = size.width / 2
        val cy = 220.dp.toPx()
        val r = 150.dp.toPx()
        val drift = 70.dp.toPx()
        val blobs = listOf(
            Triple(c0, Offset(cx + sin(t * 0.31f) * drift, cy + cos(t * 0.23f) * drift * 0.6f), 1f + 0.12f * sin(t * 0.47f)),
            Triple(c1, Offset(cx + cos(t * 0.19f + 2f) * drift * 1.2f, cy + sin(t * 0.27f + 1f) * drift * 0.8f), 0.9f + 0.14f * sin(t * 0.39f + 1f)),
            Triple(c2, Offset(cx + sin(t * 0.17f + 4f) * drift, cy + cos(t * 0.33f + 3f) * drift), 0.8f + 0.12f * cos(t * 0.43f)),
        )
        for ((c, at, k) in blobs) {
            if (c.alpha == 0f) continue
            val rad = r * k
            drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = at, radius = rad), rad, at)
        }
    }
}

/** Three colours of a (small, blurred) cover: the averages of its top-left, top-right and bottom, a bit more vivid. */
private fun coverColors(b: ImageBitmap): List<Color> {
    val px = b.toPixelMap()
    fun avg(x0: Int, y0: Int, x1: Int, y1: Int): Color {
        var r = 0f; var g = 0f; var bl = 0f; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { val c = px[x, y]; r += c.red; g += c.green; bl += c.blue; n++ }
        if (n == 0) return Color.Transparent
        r /= n; g /= n; bl /= n
        // push away from grey so the light reads as colour, not haze
        val m = (r + g + bl) / 3f
        fun v(x: Float) = (m + (x - m) * 1.5f).coerceIn(0f, 1f)
        return Color(v(r), v(g), v(bl))
    }
    val w = px.width
    val h = px.height
    return listOf(avg(0, 0, w / 2, h / 2), avg(w / 2, 0, w, h / 2), avg(0, h / 2, w, h))
}

@Composable
private fun Header(playing: Boolean) {
    val ui = LocalUi.current
    val sound = LocalSound.current
    val store = LocalStore.current
    val mode by store.mode.collectAsState()
    val live = mode == Mode.LIVE
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(Color(0xFFF0F0F0), Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText("Kawaii Radio", style = T.inter(16.sp, FontWeight.Bold, Color.White, lh = 16.sp, ls = 0.01.em))
            Spacer(Modifier.height(3.dp))
            BasicText("KR-01 · J-POP FM", style = T.pixel(11.sp, K.dimText, ls = 0.12.em, lh = 11.sp))
        }
        // the whole catalogue as a list
        Row(
            Modifier.padding(end = 8.dp).darkWell(RoundedCornerShape(9.dp))
                .tap(onClick = { sound.press(); ui.library = true })
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Px(Glyph.LIST, 12.dp, Color(0xFFB8BBC3))
            BasicText("RO'YXAT", style = T.pixel(12.sp, Color(0xFFB8BBC3), lh = 12.sp))
        }
        // LIVE switch: on = the shared broadcast for everyone, off = any song (Kawaii Pass)
        Row(
            Modifier.darkWell(RoundedCornerShape(9.dp))
                .tap(onClick = { sound.press(); store.setMode(if (live) Mode.PICK else Mode.LIVE) })
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // lit = on the broadcast, dark = off (pick your own songs); the number = people on LIVE right now
            val listeners by store.chat.live.collectAsState()
            OnAirDot(live, 7)
            BasicText(if (live && listeners > 0) "LIVE · $listeners" else "LIVE", style = T.pixel(12.sp, if (live) Color(0xFFFF8A95) else K.dimText2, lh = 12.sp))
        }
    }
}

@Composable
private fun OnAirDot(on: Boolean, size: Int) {
    var m = Modifier.size(size.dp)
    if (on) m = m.dropShadow(CircleShape, Shadow(8.dp, K.onAir))
    Box(m.background(if (on) K.onAir else K.offDot, CircleShape))
}

@Composable
private fun Segmented(tab: Tab, onTab: (Tab) -> Unit) {
    val sound = LocalSound.current
    val k = TABS.indexOfFirst { it.first == tab }
    val pos by animateFloatAsState(k.toFloat(), motionSpring(520f, 36f))
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .darkWell(RoundedCornerShape(15.dp), spec = 0.06f)
            .padding(3.dp),
    ) {
        val w = maxWidth / 3
        Box(Modifier.matchParentSize()) {
          Box(
            Modifier.offset { IntOffset((w.toPx() * pos).toInt(), 0) }
                .width(w).fillMaxHeight()
                .dropShadow(RoundedCornerShape(12.dp), Shadow(8.dp, Color(0f, 0f, 0f, 0.5f), offset = DpOffset(0.dp, 3.dp)))
                .background(Brush.verticalGradient(listOf(Color(0xFF4A4C53), Color(0xFF393B41))), RoundedCornerShape(12.dp))
                .innerShadow(RoundedCornerShape(12.dp), Shadow(0.dp, Color(1f, 1f, 1f, 0.14f), offset = DpOffset(0.dp, 1.dp))),
          )
        }
        Row(Modifier.fillMaxWidth()) {
            for ((key, label) in TABS) {
                val color by animateColorAsState(if (key == tab) Color.White else K.tabOff, tween(150))
                Box(
                    Modifier.weight(1f).padding(vertical = 7.dp)
                        .tap(onClick = { if (key != tab) { sound.press(); onTab(key) } }),
                    contentAlignment = Alignment.Center,
                ) { BasicText(label, style = T.pixel(14.sp, color, lh = 20.sp)) }
            }
        }
    }
}

/** Cover-flow: active cover big and glossy, neighbours tilt away. */
@Composable
private fun Covers(list: List<Entry>, currentId: String, playing: Boolean, k: Float) {
    val store = LocalStore.current
    val sound = LocalSound.current
    val active = list.indexOfFirst { it.t.id == currentId }
    val centre = if (active < 0) 0 else active
    val density = LocalDensity.current
    var drag by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(260.dp * k)
            .pointerInput(list, centre) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { c, dx -> drag += dx; c.consume() },
                    onDragEnd = {
                        if (abs(drag) >= 40.dp.toPx()) {
                            val next = centre + if (drag < 0) 1 else -1
                            if (next in list.indices) { sound.press(); store.play(list[next].t.id, list.map { it.t.id }) }
                        }
                    },
                )
            },
    ) {
        list.forEachIndexed { n, e ->
            val d = n - centre
            val ad = abs(d)
            if (ad > 2) return@forEachIndexed
            key(e.t.id) {
                val isActive = e.t.id == currentId
                val spec = motionSpring<Float>(260f, 28f)
                val x by animateFloatAsState(d * 184f * k, spec)
                val scale by animateFloatAsState(if (ad == 0) 1f else 0.68f - (ad - 1) * 0.1f, spec)
                val rot by animateFloatAsState(if (d == 0) 0f else if (d < 0) 42f else -42f, spec)
                val alpha by animateFloatAsState(if (ad == 0) 1f else if (ad == 1) 0.6f else 0.22f, spec)
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = 4.dp * k).size(236.dp * k)
                        .zIndex(10f - ad)
                        .graphicsLayer {
                            translationX = x * density.density
                            scaleX = scale; scaleY = scale
                            rotationY = rot
                            this.alpha = alpha
                            cameraDistance = 700f * density.density / 72f
                        }
                        .tap(onClick = { sound.press(); store.play(e.t.id, list.map { it.t.id }) }),
                ) {
                    CoverTile(e.t.coverArt, isActive)
                    if (isActive) {
                        Box(
                            Modifier.offset(10.dp, 10.dp).size(24.dp)
                                .dropShadow(CircleShape, Shadow(6.dp, Color(0f, 0f, 0f, 0.35f), offset = DpOffset(0.dp, 2.dp)))
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (playing) EqIcon() else CheckIcon() }
                    }
                }
            }
        }
    }
}

/** Bezel + inner hairline, like the reference tiles. */
@Composable
private fun CoverTile(cover: String, active: Boolean) {
    val bezel = RoundedCornerShape(24.dp)
    val inner = RoundedCornerShape(21.dp)
    Box(
        Modifier.fillMaxSize()
            .dropShadow(
                bezel,
                if (active) Shadow(30.dp, Color(0f, 0f, 0f, 0.55f), offset = DpOffset(0.dp, 16.dp))
                else Shadow(16.dp, Color(0f, 0f, 0f, 0.45f), offset = DpOffset(0.dp, 8.dp)),
            )
            .background(
                Brush.verticalGradient(if (active) listOf(Color.White, Color(0xFFCFD2DA)) else listOf(Color(0xFF3A3C42), Color(0xFF1C1D21))),
                bezel,
            )
            .padding(3.dp)
            .clip(inner),
    ) {
        AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.matchParentSize()
                .drawWithContent {
                    // linear-gradient(150deg …): direction (sin 150°, -cos 150°) in screen space
                    val vx = 0.5f; val vy = 0.866f
                    val len = size.width * vx + size.height * vy
                    val c = Offset(size.width / 2, size.height / 2)
                    drawRect(
                        Brush.linearGradient(
                            0f to Color(1f, 1f, 1f, 0.32f), 0.32f to Color(1f, 1f, 1f, 0.06f),
                            0.5f to Color.Transparent, 1f to Color(0f, 0f, 0f, 0.28f),
                            start = c - Offset(vx, vy) * (len / 2), end = c + Offset(vx, vy) * (len / 2),
                        ),
                    )
                }
                .innerShadow(inner, Shadow(0.dp, Color(0f, 0f, 0f, 0.35f), spread = 1.dp)),
        )
    }
}

/** Tiny equaliser on the active cover (transform-only animation). */
@Composable
private fun EqIcon() {
    val t = rememberInfiniteTime()
    Row(Modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (k in 0..2) {
            val period = 0.8f + k * 0.17f
            Box(
                Modifier.width(3.dp).fillMaxHeight12()
                    .graphicsLayer {
                        val p = (t.value / period) % 1f
                        // ease-in-out between 0.3 and 1 and back
                        val tri = if (p < 0.5f) p * 2 else (1 - p) * 2
                        val e = tri * tri * (3 - 2 * tri)
                        scaleY = 0.3f + 0.7f * e
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .background(Color(0xFF1D1E22), RoundedCornerShape(1.dp)),
            )
        }
    }
}

private fun Modifier.fillMaxHeight12() = this.height(12.dp)

/** Seconds since this composable appeared, ticking every frame. */
@Composable
fun rememberInfiniteTime(): State<Float> {
    val active = uz.kawaii.radio.ui.LocalPageActive.current
    return produceState(0f, active) {
        if (!active) return@produceState
        val start = androidx.compose.runtime.withFrameNanos { it } - (value * 1e9f).toLong()
        while (true) androidx.compose.runtime.withFrameNanos { value = (it - start) / 1e9f }
    }
}

@Composable
private fun CheckIcon() {
    Canvas(Modifier.size(12.dp)) {
        scale(size.width / 16f, pivot = Offset.Zero) {
            val p = Path().apply { moveTo(3f, 8.5f); lineTo(6f, 11.5f); lineTo(13f, 4.5f) }
            drawPath(p, Color(0xFF222222), style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun EmptySaved(premium: Boolean, onPremium: () -> Unit, k: Float) {
    Column(
        Modifier.fillMaxWidth().height(260.dp * k),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(48.dp).darkWell(RoundedCornerShape(16.dp), color = Color(0xFF1A1B1E), spec = 0f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                scale(size.width / 16f, pivot = Offset.Zero) {
                    drawPath(Path().apply { moveTo(3f, 1.5f); lineTo(13f, 1.5f); lineTo(13f, 14.5f); lineTo(8f, 11.3f); lineTo(3f, 14.5f); close() }, Color(0xFFFFD36B))
                }
            }
        }
        BasicText(if (premium) "Hali saqlangan qo'shiq yo'q" else "Kawaii Pass imkoniyati", style = T.pixel(14.sp, Color.White, weight = FontWeight.SemiBold))
        BasicText(
            if (premium) "«Saqlash» tugmasi bilan qo'shiqlarni ilova ichida saqlang." else "Sevimli qo'shiqlarni ilova ichida saqlab, internetsiz tinglang.",
            Modifier.widthIn(max = 250.dp),
            style = T.pixel(12.sp, K.tabOff, lh = 16.sp).copy(textAlign = TextAlign.Center),
        )
        if (!premium) {
            Box(
                Modifier.padding(top = 4.dp)
                    .dropShadow(CircleShape, Shadow(12.dp, Color(245, 185, 59, (0.35f * 255).toInt()), offset = DpOffset(0.dp, 4.dp)))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFE08A), K.gold)), CircleShape)
                    .innerShadow(CircleShape, Shadow(0.dp, Color(1f, 1f, 1f, 0.6f), offset = DpOffset(0.dp, 1.dp)))
                    .pressClick(onClick = onPremium)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) { BasicText("Kawaii Pass", style = T.pixel(13.sp, Color(0xFF3A2A05), weight = FontWeight.SemiBold)) }
        }
    }
}

/** Song card: lyrics when the song has them, otherwise a note that there are none. Grows when the panel is pulled down. */
@Composable
private fun NowCard(t: Track, playing: Boolean, drawer: Drawer, clock: State<Double>, onOpen: () -> Unit) {
    val store = LocalStore.current
    val sound = LocalSound.current
    val lang by store.lang.collectAsState()
    val song = rememberSongLyrics(t)
    val shownLang = song.effectiveLang(lang)
    val lines = remember(song, shownLang) { song.linesFor(shownLang) }
    val canSwitch = song.availableLangs.size > 1
    Column(
        Modifier.fillMaxWidth().zIndex(2f)
            .darkWell(RoundedCornerShape(16.dp), color = K.card)
            .tap(enabled = !drawer.open, onClick = onOpen)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnAirDot(playing, 6)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t.title, Modifier.weight(1f, fill = false), style = T.inter(14.sp, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(t.artist, Modifier.weight(1f, fill = false), style = T.inter(12.sp, color = K.dimText), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (lines.isNotEmpty()) {
                BasicText(
                    when {
                        song.auto -> "ORIGINAL"
                        shownLang == Lang.UZ -> if (canSwitch) "O'ZB ⇄" else "O'ZB"
                        shownLang == Lang.ROM -> if (canSwitch) "ROM ⇄" else "ROM"
                        else -> if (canSwitch) "日本 ⇄" else "日本"
                    },
                    Modifier.tap(enabled = canSwitch, onClick = { sound.press(); store.setLang(song.nextLang(lang)) })
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    style = T.dot(11.sp, K.dimText2),
                )
            } else {
                BasicText(if (playing) "EFIRDA" else "TAYYOR", style = T.dot(11.sp, K.dimText2))
            }
        }
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth()
                .layout { m, c ->
                    // height follows the drawer every frame without recomposing
                    val h = (70.dp.toPx() + drawer.ext.value).toInt()
                    val p = m.measure(Constraints.fixed(c.maxWidth, h))
                    layout(p.width, h) { p.place(0, 0) }
                },
        ) {
            AnimatedContent(
                drawer.open,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            ) { open ->
                if (open) FullLyrics(lines, clock, song.note) else Box(Modifier.fillMaxWidth().height(70.dp)) { LyricLines(lines, clock, shownLang, song.note) }
            }
        }
    }
}

/** Two-line karaoke readout of the collapsed panel. */
@Composable
private fun LyricLines(lines: List<LyricLine>, clock: State<Double>, lang: Lang, note: String) {
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { BasicText(note, style = T.dot(14.sp, K.lyricDim)) }
        return
    }
    val li by remember(lines) { derivedStateOf { lineAt(lines, clock.value) } }
    val glow = lyricGlow()
    // the animation keeps the texts themselves: an outgoing line must not index into the next song's lyrics
    val cur = lines.getOrNull(li)?.text?.ifEmpty { "♪ ♪ ♪" } ?: "♪ · · ·"
    val next = lines.getOrNull(li + 1)?.text ?: ""
    AnimatedContent(
        Triple(lang, li, cur to next),
        transitionSpec = {
            (fadeIn(tween(200, 200)) + slideInVertically(tween(200, 200)) { it / 7 }) togetherWith
                (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 7 })
        },
    ) { (_, _, texts) ->
        Column(Modifier.fillMaxWidth()) {
            BasicText(texts.first, style = T.dot(17.sp, K.lyric, lh = 24.sp).copy(shadow = glow), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(texts.second, style = T.dot(13.sp, K.lyricDim, lh = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Whole song in the pulled-down panel; the current line stays centred and lit. */
@Composable
private fun FullLyrics(lines: List<LyricLine>, clock: State<Double>, note: String) {
    val store = LocalStore.current
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(note, style = T.dot(15.sp, K.lyricDim).copy(textAlign = TextAlign.Center))
        }
        return
    }
    val li by remember(lines) { derivedStateOf { lineAt(lines, clock.value) } }
    val list = rememberLazyListState()
    var viewport by remember { mutableStateOf(0) }
    // keep the sung line in the middle: the top padding is half the window, so only half a line remains
    LaunchedEffect(li, viewport) {
        if (li < 0 || viewport == 0) return@LaunchedEffect
        val h = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == li }?.size
            ?: list.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
        list.animateScrollToItem(li, h / 2)
    }
    val glow = lyricGlow()
    LazyColumn(
        Modifier.fillMaxSize()
            .onPlaced { viewport = it.size.height }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
        state = list,
        // room above the first and below the last line, so they can sit in the middle too
        contentPadding = PaddingValues(vertical = with(LocalDensity.current) { (viewport / 2).toDp() }),
    ) {
        itemsIndexed(lines) { k, l ->
            val on = k == li
            val color by animateColorAsState(if (on) K.lyric else K.lyricDim, tween(300))
            BasicText(
                l.text.ifEmpty { "♪" },
                Modifier.fillMaxWidth()
                    .tap(onClick = { store.seek(l.t) })
                    .padding(vertical = 5.dp),
                style = T.dot(17.sp, color, lh = 24.sp).let { if (on) it.copy(shadow = glow) else it },
            )
        }
    }
}

/** Grip: drag down to open the lyrics, up to close; tap toggles. */
@Composable
private fun Grip(drawer: Drawer, onToggle: () -> Unit, onDragEnd: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    val w by animateDpAsState(if (drawer.open) 28.dp else 40.dp)
    val c by animateColorAsState(if (drawer.open) Color(0xFF6A6D75) else K.offDot)
    Box(
        Modifier.fillMaxWidth().height(22.dp)
            .draggable(
                rememberDraggableState { d -> scope.launch { drawer.ext.snapTo((drawer.ext.value + d).coerceIn(0f, drawer.max)) } },
                Orientation.Vertical,
                onDragStopped = { v -> onDragEnd(v) },
            )
            .tap(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(w).height(5.dp)
                .background(c, CircleShape)
                .innerShadow(CircleShape, Shadow(1.dp, Color(0f, 0f, 0f, 0.5f), offset = DpOffset(0.dp, 1.dp))),
        )
    }
}
