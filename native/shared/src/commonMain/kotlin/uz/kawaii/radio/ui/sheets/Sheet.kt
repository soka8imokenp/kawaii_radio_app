package uz.kawaii.radio.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.housing
import uz.kawaii.radio.ui.kit.pressClick
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.emboss
import uz.kawaii.radio.ui.theme.motionSpring

/**
 * Bottom sheet in the housing material: scrim, spring slide-up, drag down to close.
 * [dismissable] = false while something must not be interrupted (an update downloading).
 */
@Composable
fun BaseSheet(
    open: Boolean,
    onClose: () -> Unit,
    scrim: Color = Color(40, 42, 55, (0.35f * 255).toInt()),
    radius: Dp = 34.dp,
    dismissable: Boolean = true,
    stiffness: Float = 380f,
    damping: Float = 36f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = open
    if (!visible.currentState && !visible.targetState && visible.isIdle) return

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(scrim).tap(onClick = { if (dismissable) onClose() }))
        }
        AnimatedVisibility(
            visible,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(motionSpring(stiffness, damping)) { it },
            exit = slideOutVertically(motionSpring(stiffness, damping)) { it },
        ) {
            val drag = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current.density
            LaunchedEffect(open) { if (open) drag.snapTo(0f) }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                val shape = RoundedCornerShape(topStart = radius, topEnd = radius)
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth()
                        .heightIn(max = maxHeight * 0.86f)
                        .graphicsLayer { translationY = drag.value }
                        .draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d * if (drag.value + d < 0) 0f else 0.6f).coerceAtLeast(0f)) } },
                            Orientation.Vertical,
                            enabled = dismissable,
                            onDragStopped = { v ->
                                if (drag.value > 110 * density || v > 600 * density) onClose()
                                else drag.animateTo(0f, motionSpring(stiffness, damping))
                            },
                        )
                        .dropShadow(shape, Shadow(40.dp, Color(40, 42, 55, (0.25f * 255).toInt()), offset = DpOffset(0.dp, (-20).dp)))
                        .housing(shape)
                        .innerShadow(shape, Shadow(0.dp, Color.White, offset = DpOffset(0.dp, 1.dp)))
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                    content = content,
                )
            }
        }
    }
}

/** Standard sheet: handle, embossed title, "Yopish" key, scrolling body. */
@Composable
fun Sheet(open: Boolean, onClose: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    BaseSheet(open, onClose) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(56.dp, 6.dp).tray(CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title.uppercase(), Modifier.weight(1f), style = T.inter(13.sp, FontWeight.Bold, K.ink, ls = 1.8.sp).copy(shadow = emboss()))
            Box(Modifier.raised(CircleShape).pressClick(onClick = onClose).padding(horizontal = 12.dp, vertical = 4.dp)) {
                BasicText("YOPISH", style = T.inter(12.sp, FontWeight.Bold, K.ctlInk, ls = 0.6.sp).copy(shadow = emboss()))
            }
        }
        GrooveH(Modifier.padding(horizontal = 20.dp))
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
            content()
        }
    }
}
