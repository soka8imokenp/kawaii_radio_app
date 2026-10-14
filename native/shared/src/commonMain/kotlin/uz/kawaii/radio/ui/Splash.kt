package uz.kawaii.radio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kawaii.radio.ui.kit.Logo
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.motionSpring

private const val SEGMENTS = 14
private val Shutter = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)

/**
 * Launch screen. Its first frame is identical to the native Android splash (white logo on black),
 * so the animation continues from it: the logo lifts, the LCD-style "self-test" fills, then the
 * black shutter rolls up into the dark top panel.
 */
@Composable
fun Splash(onBooted: () -> Unit, onGone: () -> Unit) {
    val lift = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val leds = remember { Animatable(0f) }
    val segs = remember { List(SEGMENTS) { Animatable(0f) } }
    val tag = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val shutter = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { delay(150); lift.animateTo(1f, motionSpring(160f, 18f)) }
        launch { delay(350); title.animateTo(1f, tween(450, easing = EaseOut)) }
        launch { delay(550); leds.animateTo(1f, tween(300)) }
        segs.forEachIndexed { i, a -> launch { delay(700L + i * 55L); a.animateTo(1f, tween(120)) } }
        launch {
            delay(1550)
            tag.animateTo(1f, keyframes { durationMillis = 400; 0f at 0; 1f at 133; 0.4f at 266; 1f at 400 })
        }
        delay(2100)
        onBooted()
        launch { leave.animateTo(1f, tween(350, easing = EaseIn)) }
        shutter.animateTo(1f, tween(600, easing = Shutter))
        onGone()
    }

    Box(
        Modifier.fillMaxSize()
            // swallow touches until the app is revealed
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            // clip-path: inset(0 0 x% 0): the black sheet rolls up from the bottom
            .drawShutter { shutter.value },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                alpha = 1f - leave.value
                translationY = -30f * density * leave.value
                val s = 1f - 0.08f * leave.value
                scaleX = s; scaleY = s
            },
            contentAlignment = Alignment.Center,
        ) {
            // same size / position as the native splash icon (144dp tall, centred)
            Logo(
                Color.White,
                Modifier.height(144.dp).width(121.dp).graphicsLayer {
                    translationY = -18f * density * lift.value
                    val s = 1f - 0.14f * lift.value
                    scaleX = s; scaleY = s
                },
            )
            // placed under the logo without moving it off-centre
            Column(
                Modifier.wrapContentSize(unbounded = true).offset(y = 72.dp).layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) { p.place(-p.width / 2, (-8f * density * lift.value).toInt()) }
                }.width(260.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    "KAWAII RADIO",
                    Modifier.graphicsLayer { alpha = title.value; translationY = (1 - title.value) * 8 * density },
                    style = T.pixel(22.sp, Color.White, ls = 0.18.em, lh = 22.sp),
                )
                Row(
                    Modifier.padding(top = 16.dp).graphicsLayer { alpha = leds.value },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    segs.forEach { a ->
                        val c = lerp(Color(1f, 1f, 1f, 0.12f), K.ledGreen, a.value)
                        Box(
                            Modifier.size(10.dp, 6.dp)
                                .dropShadow(RoundedCornerShape(1.5.dp), Shadow(8.dp, Color(34, 197, 94, (0.75f * 255 * a.value).toInt())))
                                .background(c, RoundedCornerShape(1.5.dp)),
                        )
                    }
                }
                BasicText(
                    "KR-01 · ON AIR",
                    Modifier.padding(top = 12.dp).graphicsLayer { alpha = tag.value },
                    style = T.micro(9.sp, Color(1f, 1f, 1f, 0.4f)),
                )
            }
        }
    }
}

/** Black background that shrinks upwards as [progress] goes 0 → 1; content is clipped with it. */
private fun Modifier.drawShutter(progress: () -> Float) = this
    .graphicsLayer {
        val p = progress()
        clip = true
        shape = GenericShape { size, _ -> addRect(Rect(0f, 0f, size.width, size.height * (1f - p))) }
    }
    .background(Color.Black)
