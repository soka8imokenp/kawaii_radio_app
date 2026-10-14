package uz.kawaii.radio.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uz.kawaii.radio.ClickSound
import uz.kawaii.radio.ui.theme.motionSpring

val LocalSound = staticCompositionLocalOf<ClickSound> {
    object : ClickSound { override fun tick() {}; override fun press() {} }
}

/** Tap handling without ripples: [onDown] fires the moment the finger lands, [onClick] on release. */
fun Modifier.tap(enabled: Boolean = true, onDown: ((Boolean) -> Unit)? = null, onClick: () -> Unit): Modifier =
    if (!enabled) this else composed {
        val click by rememberUpdatedState(onClick)
        val down by rememberUpdatedState(onDown)
        pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    down?.invoke(true)
                    tryAwaitRelease()
                    down?.invoke(false)
                },
                onTap = { click() },
            )
        }
    }

/** Plain button: clicks on touch, acts on release. */
fun Modifier.pressClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val sound = LocalSound.current
    tap(enabled, onDown = { if (it) sound.press() }, onClick = onClick)
}

/**
 * Physical key: sinks on press, springs back, clicks. [active] keeps it latched down.
 */
@Composable
fun Key(
    shape: Shape,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    gap: Dp = 8.dp,
    onPress: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val sound = LocalSound.current
    var down by remember { mutableStateOf(false) }
    val sunk = down || active
    val y by animateFloatAsState(if (sunk) 1.5f else 0f, motionSpring(900f, 30f))
    val s by animateFloatAsState(if (down) 0.975f else 1f, motionSpring(900f, 30f))
    val density = LocalDensity.current.density
    Row(
        modifier
            .graphicsLayer { translationY = y * density; scaleX = s; scaleY = s }
            .raised(shape, sunk)
            .tap(onDown = { d -> down = d; if (d) sound.press() }, onClick = onPress),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
