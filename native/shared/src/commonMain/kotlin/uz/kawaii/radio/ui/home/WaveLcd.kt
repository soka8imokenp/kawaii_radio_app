package uz.kawaii.radio.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kawaii.radio.Lang
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lcdShadow
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val BARS = 220 // bars across the whole track
private const val GAP = 4.2f // dp per bar on screen

private fun fmt(s: Double): String {
    val v = max(0, s.toInt())
    return "${v / 60}:${(v % 60).toString().padStart(2, '0')}"
}

/** Pseudo waveform used until the real one is read (stable per track). */
private fun placeholderPeaks(seed: String, n: Int): FloatArray {
    var h = 0L
    for (c in seed) h = (h * 31 + c.code) and 0xFFFFFFFFL
    return FloatArray(n) { i ->
        h = (h * 1103515245 + 12345) and 0xFFFFFFFFL
        val r = (h % 1000) / 1000f
        val env = 0.35f + 0.65f * kotlin.math.abs(sin(i / 9f + seed.length))
        max(0.08f, r * env)
    }
}

private val inkPlayed = Color(38, 46, 30, (0.9f * 255).toInt())
private val inkAhead = Color(38, 46, 30, (0.38f * 255).toInt())
private val inkLine = Color(38, 46, 30, (0.15f * 255).toInt())
private val inkRule = Color(38, 46, 30, (0.2f * 255).toInt())

@Composable
fun WaveLcd(t: Track, playing: Boolean, durationMs: Long, clock: State<Double>) {
    val store = LocalStore.current
    val sound = LocalSound.current
    val lang by store.lang.collectAsState()
    val tuning by store.tuning.collectAsState()
    var peaks by remember(t.id) { mutableStateOf(placeholderPeaks(t.id, BARS)) }
    LaunchedEffect(t.id) {
        val uri = store.platform.offlineUri(t.id) ?: t.audioSrc
        store.platform.waveform(t.id, uri, BARS)?.let { real ->
            val top = real.maxOrNull()?.takeIf { it > 0f } ?: 1f
            peaks = FloatArray(real.size) { max(0.06f, (real[it] / top).pow(1.3f)) }
        }
    }
    val duration = if (durationMs > 0) durationMs / 1000.0 else t.duration
    val glow by animateFloatAsState(if (playing) 1f else 0.35f, tween(600))
    val shape = RoundedCornerShape(16.dp)
    val ls = lcdShadow()

    Box(
        Modifier.fillMaxWidth()
            .lcd(shape)
            .border(2.dp, K.lcdBorder, shape)
            .clip(shape),
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = glow }.drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to Color(240, 255, 200, (0.4f * 255).toInt()), 0.7f to Color.Transparent,
                    center = Offset(size.width * 0.35f, size.height * 0.25f), radius = size.width * 0.9f,
                ),
            )
        })
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(15.dp)) {
                    scale(size.width / 16f, pivot = Offset.Zero) {
                        drawPath(
                            Path().apply {
                                moveTo(2f, 2f); lineTo(14f, 2f); lineTo(14f, 4f); lineTo(8f, 4f); lineTo(8f, 12f); lineTo(2f, 12f)
                                lineTo(2f, 8f); lineTo(6f, 8f); lineTo(6f, 4f); lineTo(2f, 4f); close()
                                moveTo(10f, 6f); lineTo(14f, 6f); lineTo(14f, 14f); lineTo(10f, 14f); close()
                            },
                            K.lcdInk, alpha = 0.9f,
                        )
                    }
                }
                BasicText(
                    t.title.uppercase(), Modifier.weight(1f).padding(start = 8.dp, end = 12.dp),
                    style = T.pixel(15.sp, K.lcdInk, lh = 15.sp).copy(shadow = ls), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Box {
                    BasicText("88:88 SEC", style = T.pixel(15.sp, K.lcdGhost, lh = 15.sp))
                    val time by remember { derivedStateOf { fmt(clock.value) } }
                    Row(Modifier.align(Alignment.CenterEnd)) {
                        BasicText(time, style = T.pixel(15.sp, K.lcdInk, lh = 15.sp).copy(shadow = ls))
                        BasicText(" SEC", style = T.pixel(15.sp, K.lcdGhost, lh = 15.sp))
                    }
                }
            }

            // waveform: scrolls under a fixed centre playhead; drag to scrub
            Waveform(peaks, clock, duration, tuning) { store.seek(it) }

            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).drawBehind { drawRect(inkRule) })
            BoxWithConstraints(Modifier.padding(top = 6.dp).fillMaxWidth()) {
                val narrow = maxWidth < 330.dp
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // lyric language: always on the panel, lit only when the song has that transcript
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (l in Lang.entries) {
                            val available = if (l == Lang.UZ) t.lyrics != null else t.lyrics?.romaji != null
                            val on = available && lang == l
                            val label = (if (l == Lang.UZ) "UZ" else "JP") + if (on) " ●" else " ○"
                            val style = when {
                                on -> T.pixel(15.sp, K.lcdInk, lh = 15.sp).copy(shadow = ls)
                                available -> T.pixel(15.sp, K.lcdInk.copy(alpha = 0.45f), lh = 15.sp)
                                else -> T.pixel(15.sp, K.lcdGhost, lh = 15.sp)
                            }
                            BasicText(
                                label,
                                Modifier.tap(enabled = available, onClick = { sound.press(); store.setLang(l) }).padding(vertical = 2.dp),
                                style = style,
                            )
                        }
                    }
                    if (!narrow) BasicText("${t.bpm} BPM", style = T.pixel(15.sp, K.lcdGhost, lh = 15.sp))
                    BasicText(
                        if (tuning) "TUNING ●" else "ON AIR ●",
                        style = if (playing) T.pixel(15.sp, K.lcdInk, lh = 15.sp).copy(shadow = ls) else T.pixel(15.sp, K.lcdGhost, lh = 15.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Waveform(peaks: FloatArray, clock: State<Double>, duration: Double, tuning: Boolean, onSeek: (Double) -> Unit) {
    // while scrubbing the wave follows the finger at once; the player is only asked to seek a few times a second
    var scrub by remember { mutableStateOf<Double?>(null) }
    Box(
        Modifier.padding(top = 4.dp).fillMaxWidth().height(30.dp)
            .pointerInput(duration) {
                var startX = 0f
                var startT = 0.0
                var lastSeek = TimeSource.Monotonic.markNow()
                detectHorizontalDragGestures(
                    onDragStart = { o -> startX = o.x; startT = clock.value; lastSeek = TimeSource.Monotonic.markNow() },
                    onHorizontalDrag = { c, _ ->
                        c.consume()
                        val dx = c.position.x - startX
                        val to = (startT - dx / (BARS * GAP * density) * duration).coerceIn(0.0, duration)
                        scrub = to
                        if (lastSeek.elapsedNow() > 120.milliseconds) { onSeek(to); lastSeek = TimeSource.Monotonic.markNow() }
                    },
                    onDragEnd = { scrub?.let(onSeek); scrub = null },
                    onDragCancel = { scrub = null },
                )
            },
    ) {
        // its own layer: redrawn every frame without touching the rest of the LCD
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val gap = GAP.dp.toPx()
            val barW = 2.dp.toPx()
            val progress = if (duration > 0) ((scrub ?: clock.value) / duration).coerceIn(0.0, 1.0).toFloat() else 0f
            val offset = progress * BARS * gap
            val cx = size.width / 2
            val first = max(0, ((offset - cx) / gap).toInt() - 1)
            val last = minOf(peaks.size - 1, ((offset + cx) / gap).toInt() + 1)
            val r = CornerRadius(1.dp.toPx())
            for (i in first..last) {
                val x = cx - offset + i * gap
                val h = size.height * max(0.08f, peaks[i])
                drawRoundRect(if (i * gap < offset) inkPlayed else inkAhead, Offset(x, (size.height - h) / 2), Size(barW, h), r)
            }
            val line = 1.dp.toPx()
            drawRect(inkLine, Offset(0f, size.height / 2 - line / 2), Size(size.width, line))
            val ph = 3.dp.toPx()
            drawRoundRect(K.lcdInk, Offset(cx - ph / 2, 2.dp.toPx()), Size(ph, size.height - 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        if (tuning) Noise()
    }
}

/** Static between stations: a 120×24 grid of random LCD pixels, regenerated every frame. */
@Composable
private fun Noise() {
    val t = rememberInfiniteTime()
    Canvas(Modifier.fillMaxSize()) {
        t.value // redraw every frame
        val cw = size.width / 120f
        val ch = size.height / 24f
        val c = Color(38, 46, 30, 120)
        for (y in 0 until 24) for (x in 0 until 120) {
            if (Random.nextFloat() < 0.3f) drawRect(c, Offset(x * cw, y * ch), Size(cw, ch))
        }
    }
}
