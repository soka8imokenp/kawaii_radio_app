package uz.kawaii.radio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.Key
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.PauseIcon
import uz.kawaii.radio.ui.kit.PlayIcon
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.pressClick
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T

/** Page title in the pixel face, with a sub-line and an optional plate on the right. */
@Composable
fun PageHeader(title: String, sub: String, trailing: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(title.uppercase(), style = T.pixel(24.sp, K.ink, ls = 0.06.em, weight = FontWeight.SemiBold, lh = 24.sp))
                Spacer(Modifier.height(6.dp))
                BasicText(sub, style = T.pixel(13.sp, K.inkSoft, lh = 15.sp))
            }
            trailing?.invoke()
        }
        GrooveH()
    }
}

/** Small uppercase label above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier.padding(top = 20.dp, bottom = 8.dp), style = T.micro(11.sp, K.inkSoft, ls = 0.12.em))
}

/** Flat silkscreened plate (counter / status). */
@Composable
fun Plate(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.tray(RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Raised key with a pixel glyph and/or a micro label. */
@Composable
fun PixelKey(
    label: String?,
    modifier: Modifier = Modifier,
    glyph: List<String>? = null,
    glyphSize: Dp = 14.dp,
    active: Boolean = false,
    height: Dp = 40.dp,
    onClick: () -> Unit,
) {
    Key(RoundedCornerShape(14.dp), modifier.height(height), active = active, onPress = onClick) {
        if (glyph != null) Px(glyph, glyphSize, K.ctlInk)
        if (label != null) BasicText(label.uppercase(), style = T.micro(11.sp))
    }
}

/** Pill chip, latched down when selected. */
@Composable
fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sound = LocalSound.current
    Box(
        modifier.raised(RoundedCornerShape(12.dp), selected)
            .tap(onClick = { sound.press(); onClick() })
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        BasicText(label, style = T.pixel(13.sp, if (selected) K.ink else K.inkSoft, lh = 15.sp))
    }
}

/** Square cover with a hairline bezel. */
@Composable
fun Cover(url: String, size: Dp, radius: Dp = 9.dp, modifier: Modifier = Modifier) {
    AsyncImage(
        url, null,
        modifier.size(size)
            .dropShadow(RoundedCornerShape(radius), Shadow(0.dp, K.shade, spread = 2.dp))
            .clip(RoundedCornerShape(radius)),
        contentScale = ContentScale.Crop,
    )
}

/**
 * A song as a key cap seated in a tray: tap plays it, long-press opens the song card,
 * the key on the right saves it for offline listening.
 */
@Composable
fun SongRow(
    t: Track,
    current: Boolean,
    playing: Boolean,
    sub: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val sound = LocalSound.current
    Row(
        Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).raised(RoundedCornerShape(13.dp), current)
                .pointerInput(t.id) {
                    detectTapGestures(
                        onPress = { sound.press() },
                        onTap = { onClick() },
                        onLongPress = onLongClick?.let { f -> { f() } },
                    )
                }
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(t.coverArt, 46.dp)
            Column(Modifier.weight(1f)) {
                BasicText(t.title, style = T.pixel(15.sp, K.ink, weight = FontWeight.SemiBold, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(t.artist, style = T.pixel(13.sp, K.inkSoft, lh = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) BasicText(sub, Modifier.padding(top = 2.dp), style = T.micro(10.sp, K.inkSoft.copy(alpha = 0.8f)))
            }
            if (current) Led(playing)
        }
        SaveKey(t.id, Modifier.width(44.dp).height(62.dp))
        trailing?.invoke()
    }
}

/**
 * Mini player above the navigation on every page but the radio: cover, title, a thin LCD
 * progress line, play/pause and next. Tapping it opens the radio.
 */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val store = LocalStore.current
    val tracks by store.tracks.collectAsState()
    val engine by store.engine.collectAsState()
    val t = store.current(tracks, engine.id) ?: return
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(engine.playing, engine.durationMs, t.id) {
        while (true) {
            if (engine.playing) withFrameNanos { } else delay(250)
            val d = if (engine.durationMs > 0) engine.durationMs.toFloat() else (t.duration * 1000).toFloat()
            progress.floatValue = if (d > 0) (store.platform.audio.positionMs() / d).coerceIn(0f, 1f) else 0f
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp)) {
        Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(5.dp)) {
            Row(
                Modifier.fillMaxWidth().raised(RoundedCornerShape(13.dp)).tap(onClick = onOpen).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Cover(t.coverArt, 40.dp, 8.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(t.title, style = T.pixel(14.sp, K.ink, weight = FontWeight.SemiBold, lh = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(t.artist, style = T.pixel(12.sp, K.inkSoft, lh = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    // progress on a strip of LCD glass
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        val r = CornerRadius(2.dp.toPx())
                        drawRoundRect(K.lcdB, cornerRadius = r)
                        drawRoundRect(K.lcdInk.copy(alpha = 0.85f), size = Size(size.width * progress.floatValue, size.height), cornerRadius = r)
                    }
                }
                Key(CircleShape, Modifier.size(40.dp), onPress = { store.toggle() }) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        if (engine.playing) PauseIcon(K.ctlInk) else PlayIcon(K.ctlInk)
                    }
                }
                Key(CircleShape, Modifier.size(40.dp), onPress = { store.next() }) { Px(Glyph.NEXT, 13.dp, K.ctlInk) }
            }
        }
    }
}

/**
 * Offline key: a down arrow, the percentage while it downloads, a lit check once the song is on
 * the phone (tap again to remove the copy).
 */
@Composable
fun SaveKey(id: String, modifier: Modifier) {
    val store = LocalStore.current
    val saved by store.saved.collectAsState()
    val downloads by store.downloads.collectAsState()
    val ui = LocalUi.current
    val pass by store.pass.collectAsState()
    val p = downloads[id]
    val isSaved = id in saved
    Key(RoundedCornerShape(13.dp), modifier, active = isSaved || p != null, onPress = { if (pass) store.toggleSave(id) else ui.premium = true }) {
        when {
            p != null -> BasicText("${(p * 100).toInt()}%", style = T.micro(10.sp, K.ctlInk, ls = 0.em))
            isSaved -> Px(Glyph.CHECK, 14.dp, K.ledGreen)
            else -> Px(Glyph.DOWNLOAD, 13.dp, K.ctlInk)
        }
    }
}

/** Little "●" plate with a number, used in page headers. */
@Composable
fun CountPlate(n: Int, color: Color) {
    Plate {
        Led(n > 0, color)
        BasicText(n.toString().padStart(2, '0'), style = T.pixel(15.sp, K.ink, lh = 15.sp))
    }
}
