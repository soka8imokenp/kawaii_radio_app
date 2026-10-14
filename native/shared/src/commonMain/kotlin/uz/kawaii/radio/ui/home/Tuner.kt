package uz.kawaii.radio.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.motionSpring
import kotlin.math.abs
import kotlin.math.roundToInt

private const val STEP = 34f // dp of drag per station

/** Horizontal thumb-wheel tuner with detents, like a hardware jog wheel. */
@Composable
fun Tuner(modifier: Modifier, onStep: (Int) -> Unit, preview: (Int) -> String) {
    val sound = LocalSound.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val step = STEP * density
    val x = remember { Animatable(0f) }
    var pending by remember { mutableIntStateOf(0) }

    Box(modifier) {
        Box(Modifier.fillMaxWidth().tray(RoundedCornerShape(22.dp)).padding(5.dp)) {
            val wheel = RoundedCornerShape(16.dp)
            Canvas(
                Modifier.fillMaxWidth().height(46.dp)
                    .dropShadow(wheel, Shadow(10.dp, K.shadeDeep, spread = (-5).dp, offset = DpOffset(0.dp, 6.dp)))
                    .dropShadow(wheel, Shadow(0.dp, K.shadeSoft, offset = DpOffset(0.dp, 2.dp)))
                    .dropShadow(wheel, Shadow(0.dp, K.shadeSoft, spread = 1.dp))
                    .clip(wheel)
                    .innerShadow(wheel, Shadow(0.dp, K.spec, offset = DpOffset(0.dp, 1.dp)))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val x0 = x.value
                            var moved = false
                            var lastDetent = (x0 / step).roundToInt()
                            var total = 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                total += c.positionChange().x
                                if (abs(total) > 4 * density) moved = true
                                if (moved) {
                                    c.consume()
                                    scope.launch { x.snapTo(x0 + total) }
                                    val det = ((x0 + total) / step).roundToInt()
                                    if (det != lastDetent) {
                                        lastDetent = det
                                        sound.tick()
                                        pending = -det
                                    }
                                }
                            }
                            if (!moved) {
                                // tap: left half = previous, right half = next
                                val left = down.position.x < size.width / 2
                                sound.press()
                                onStep(if (left) -1 else 1)
                                val d = if (left) step else -step
                                scope.launch {
                                    // kick the wheel one detent; a full detent looks identical to 0, so snap back after
                                    x.animateTo(d, tween(140, easing = LinearOutSlowInEasing))
                                    delay(140)
                                    x.snapTo(0f)
                                }
                            } else {
                                val det = ((x0 + total) / step).roundToInt()
                                pending = 0
                                scope.launch {
                                    x.animateTo(det * step, motionSpring(500f, 30f))
                                    x.snapTo(0f)
                                }
                                if (det != 0) onStep(-det)
                            }
                        }
                    },
            ) {
                val period = STEP.dp.toPx()
                val tickW = 2.dp.toPx()
                val tickX = 16.dp.toPx()
                val rowSmall = 9.dp.toPx()
                val rowTall = 20.dp.toPx()
                // cylinder body
                drawRect(Brush.verticalGradient(listOf(K.tunerA, K.tunerB)))
                // three rows of detent ticks that scroll with the drag
                val shift = ((x.value % period) + period) % period
                var px = shift - period
                while (px < size.width + period) {
                    val tx = px + tickX
                    drawRect(K.tickSoft, Offset(tx, 0f), Size(tickW, rowSmall))
                    drawRect(K.tick, Offset(tx, (size.height - rowTall) / 2), Size(tickW, rowTall))
                    drawRect(K.tickSoft, Offset(tx, size.height - rowSmall), Size(tickW, rowSmall))
                    px += period
                }
                // row separators
                val line = 1.dp.toPx()
                drawRect(K.tickSoft, Offset(0f, rowSmall), Size(size.width, line))
                drawRect(K.tickSoft, Offset(0f, size.height - rowSmall - line), Size(size.width, line))
                // glossy cylinder shading: dark edges, bright centre
                drawRect(
                    Brush.horizontalGradient(
                        0f to K.tunerShade, 0.3f to Color.Transparent, 0.5f to K.tunerShine, 0.7f to Color.Transparent, 1f to K.tunerShade,
                    ),
                )
                // red needle with a soft glow
                val nw = 5.dp.toPx()
                val nh = 20.dp.toPx()
                val tl = Offset((size.width - nw) / 2, (size.height - nh) / 2)
                drawRoundRect(K.tickRed.copy(alpha = 0.25f), tl - Offset(3.dp.toPx(), 3.dp.toPx()), Size(nw + 6.dp.toPx(), nh + 6.dp.toPx()), CornerRadius(6.dp.toPx()))
                drawRoundRect(K.tickRed, tl, Size(nw, nh), CornerRadius(nw / 2))
                drawRoundRect(Color(1f, 1f, 1f, 0.55f), tl + Offset(nw * 0.25f, 0.5f), Size(nw * 0.5f, 1.dp.toPx()), CornerRadius(1f))
            }
        }
        AnimatedVisibility(
            pending != 0,
            Modifier.align(Alignment.TopCenter).offset(y = (-36).dp).zIndex(30f).wrapContentWidth(unbounded = true),
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut(),
        ) {
            val p = pending
            BasicText(
                (if (p > 0) "►► " else "◄◄ ") + if (p != 0) preview(p) else "",
                Modifier.background(K.lcdInk, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                style = T.pixel(14.sp, Color(0xFFC9D3A4), lh = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
