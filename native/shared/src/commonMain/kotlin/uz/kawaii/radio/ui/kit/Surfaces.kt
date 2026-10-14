package uz.kawaii.radio.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import uz.kawaii.radio.ui.theme.K

/*
 * The CSS surfaces of the web version (.raised, .tray, .lcd …) as modifiers. Shadows are listed
 * bottom-most first. Compose caches every shadow as a bitmap per size, so they cost one blit per frame.
 */

private fun sh(radius: Dp, color: Color, y: Dp = 0.dp, spread: Dp = 0.dp) =
    Shadow(radius = radius, color = color, spread = spread, offset = DpOffset(0.dp, y))

/** Embossed control (keys, round buttons); [down] = debossed / pressed. */
fun Modifier.raised(shape: Shape, down: Boolean = false): Modifier =
    if (!down) {
        this
            .dropShadow(shape, sh(12.dp, K.shadeDeep, y = 6.dp, spread = (-5).dp))
            .dropShadow(shape, sh(0.dp, K.shadeSoft, y = 2.dp))
            .dropShadow(shape, sh(0.dp, K.shadeSoft, spread = 1.dp))
            .background(Brush.verticalGradient(listOf(K.raised, K.base2)), shape)
            .innerShadow(shape, sh(0.dp, K.spec, y = 1.dp))
    } else {
        this
            .dropShadow(shape, sh(0.dp, K.spec, y = 1.dp))
            .dropShadow(shape, sh(0.dp, K.shadeSoft, spread = 1.dp))
            .background(Brush.verticalGradient(listOf(K.tray, K.base2)), shape)
            .innerShadow(shape, sh(0.dp, K.spec, y = (-1).dp))
            .innerShadow(shape, sh(4.dp, K.shade, y = 2.dp))
    }

/** Recessed modular tray: controls dock into it. */
fun Modifier.tray(shape: Shape): Modifier = this
    .dropShadow(shape, sh(0.dp, K.spec, y = 1.dp))
    .background(K.tray, shape)
    .innerShadow(shape, sh(0.dp, K.spec, y = (-1).dp))
    .innerShadow(shape, sh(5.dp, K.shade, y = 2.dp))

/** Dark recessed well of the top panel. */
fun Modifier.darkWell(shape: Shape, color: Color = K.well, spec: Float = 0.05f): Modifier = this
    .dropShadow(shape, sh(0.dp, Color(1f, 1f, 1f, spec), y = 1.dp))
    .background(color, shape)
    .innerShadow(shape, sh(3.dp, Color(0f, 0f, 0f, 0.7f), y = 1.dp))

private val lcdGrid = Color(38, 46, 30, (0.05f * 255).toInt())

/** Greenish segment LCD: radial glass gradient, 6px pixel grid, deep inner shadow. */
fun Modifier.lcd(shape: Shape): Modifier = this
    .drawWithCache {
        val step = 6.dp.toPx()
        val line = 1.dp.toPx()
        // radial-gradient(120% 90% at 30% 20%): an ellipse, drawn as a circle on a squashed canvas
        val rx = size.width * 1.2f
        val ry = size.height * 0.9f
        val c = Offset(size.width * 0.3f, size.height * 0.2f)
        val glass = Brush.radialGradient(listOf(K.lcdA, K.lcdB), center = c, radius = rx)
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            clipPath(Path().apply { addOutline(outline) }) {
                scale(1f, ry / rx, pivot = c) { drawRect(glass, topLeft = Offset(-rx, -rx * 2), size = Size(rx * 4, rx * 4)) }
                var y = 0f
                while (y < size.height) { drawRect(lcdGrid, Offset(0f, y), Size(size.width, line)); y += step }
                var x = 0f
                while (x < size.width) { drawRect(lcdGrid, Offset(x, 0f), Size(line, size.height)); x += step }
            }
        }
    }
    .innerShadow(shape, sh(18.dp, Color(30, 40, 20, (0.3f * 255).toInt()), y = 8.dp))
    .innerShadow(shape, sh(0.dp, Color(0f, 0f, 0f, 0.2f), y = 2.dp))

/** Chalk housing with a barely-there moulding texture. */
fun Modifier.housing(shape: Shape? = null): Modifier = (if (shape != null) this.clip(shape) else this).drawWithCache {
    val cell = 12.dp.toPx().coerceAtLeast(2f)
    val n = kotlin.math.ceil(cell).toInt()
    val tile = ImageBitmap(n, n)
    Canvas(tile).drawCircle(Offset(n * 0.3f, n * 0.4f), 1.dp.toPx(), Paint().apply { color = Color(1f, 1f, 1f, 0.35f) })
    val dots = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    val base = Brush.verticalGradient(listOf(K.base2, K.base))
    onDrawBehind {
        drawRect(base)
        drawRect(dots)
    }
}

/** Faint dot screen over the dark panel. */
fun Modifier.dotScreen(alpha: Float): Modifier = this.drawWithCache {
    val n = kotlin.math.ceil(4.dp.toPx()).toInt().coerceAtLeast(2)
    val tile = ImageBitmap(n, n)
    Canvas(tile).drawCircle(Offset(n / 2f, n / 2f), 0.7.dp.toPx(), Paint().apply { color = Color.White })
    val dots = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(dots, alpha = alpha) }
}

/** Recessed seam groove (horizontal). */
@Composable
fun GrooveH(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).drawBehind {
        val px = 1.dp.toPx()
        drawRect(K.groove, size = Size(size.width, px))
        drawRect(K.spec, topLeft = Offset(0f, px), size = Size(size.width, px))
    })
}

/** Tiny alignment screw dots ":". */
@Composable
fun Screw(modifier: Modifier = Modifier) {
    Column(modifier) {
        ScrewDot()
        Spacer(Modifier.height(6.dp))
        ScrewDot()
    }
}

@Composable
private fun ScrewDot() {
    Box(
        Modifier.size(3.dp)
            .dropShadow(CircleShape, sh(0.dp, K.spec, y = 1.dp))
            .background(K.screw, CircleShape)
            .innerShadow(CircleShape, sh(1.dp, Color(0f, 0f, 0f, 0.15f), y = 1.dp))
    )
}

/** LED with a radial highlight; [pulse] scales the glow. */
@Composable
fun Led(on: Boolean, color: Color = K.ledGreen, pulse: Float = 1f, modifier: Modifier = Modifier) {
    val c = if (on) color else Color(0xFF9CA3AF)
    var m = modifier.size(9.dp)
    if (on) m = m.dropShadow(CircleShape, Shadow(radius = 9.dp * pulse, color = c))
    m = m.dropShadow(CircleShape, Shadow(radius = 0.dp, color = K.shadeSoft, spread = 1.5.dp))
    Box(
        m.drawBehind {
            val dark = Color(c.red * 0.65f, c.green * 0.65f, c.blue * 0.65f, 1f)
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White, 0.14f to Color.White, 0.42f to c, 1f to dark,
                    center = Offset(size.width * 0.35f, size.height * 0.3f), radius = size.width * 0.8f,
                ),
            )
        }.then(if (on) Modifier else Modifier.innerShadow(CircleShape, sh(2.dp, Color(0f, 0f, 0f, 0.3f), y = 1.dp)))
    )
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
