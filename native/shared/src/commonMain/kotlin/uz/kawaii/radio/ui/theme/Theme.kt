package uz.kawaii.radio.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import uz.kawaii.radio.res.Res
import uz.kawaii.radio.res.dotgothic16
import uz.kawaii.radio.res.inter_400
import uz.kawaii.radio.res.inter_600
import uz.kawaii.radio.res.inter_700
import uz.kawaii.radio.res.inter_800
import uz.kawaii.radio.res.pixelify_500
import uz.kawaii.radio.res.pixelify_600
import uz.kawaii.radio.res.vt323
import kotlin.math.sqrt

/*
 * Design system: tactile retro-modern pocket synth (Teenage Engineering × Braun).
 * Chalk bases, soft raised panels, graphite ink, emerald LED, vermilion tick.
 * Controls are embossed (raised) or debossed (pressed), never flat.
 */
/** Every colour of the hardware, in one material. Two finishes: chalk (light) and graphite (dark). */
@Immutable
class Palette(
    val dark: Boolean,
    // chalk & putty
    val page: Color, val base: Color, val base2: Color, val raised: Color, val tray: Color,
    val ink: Color, val inkSoft: Color, val ctlInk: Color,
    val spec: Color, val shade: Color, val shadeSoft: Color, val shadeDeep: Color,
    val groove: Color, val screw: Color, val mould: Color, val emboss: Color,
    // LCD
    val lcdA: Color, val lcdB: Color, val lcdInk: Color, val lcdGhost: Color, val lcdBorder: Color,
    // tuner wheel
    val tunerA: Color, val tunerB: Color, val tick: Color, val tickSoft: Color, val tunerShade: Color, val tunerShine: Color,
    // dark top panel
    val panelTop: Color, val panelMid: Color, val panelBottom: Color,
)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, (a * 255).toInt())

val Chalk = Palette(
    dark = false,
    page = Color(0xFFDFE1E6), base = Color(0xFFEBECEF), base2 = Color(0xFFEFF1F5), raised = Color(0xFFFAFAFC), tray = Color(0xFFE3E5EA),
    ink = Color(0xFF374151), inkSoft = Color(0xFF6B7280), ctlInk = Color(0xFF4B5563),
    spec = Color.White, shade = Color(0xFFB8BDCB), shadeSoft = rgba(184, 189, 203, 0.55f), shadeDeep = rgba(140, 146, 163, 0.5f),
    groove = Color(0xFFD5D8DF), screw = Color(0xFFC3C7D1), mould = Color(1f, 1f, 1f, 0.35f), emboss = Color(1f, 1f, 1f, 0.9f),
    lcdA = Color(0xFFCFD8AA), lcdB = Color(0xFFAAB68A), lcdInk = Color(0xFF262E1E), lcdGhost = rgba(38, 46, 30, 0.1f), lcdBorder = Color(0xFF3A4131),
    tunerA = Color(0xFFFDFDFE), tunerB = Color(0xFFECEEF2), tick = rgba(55, 65, 81, 0.45f), tickSoft = rgba(55, 65, 81, 0.14f),
    tunerShade = rgba(184, 189, 203, 0.85f), tunerShine = Color(1f, 1f, 1f, 0.9f),
    panelTop = Color(0xFF2C2E33), panelMid = Color(0xFF232428), panelBottom = Color(0xFF1F2023),
)

/** Night finish: graphite plastic, same keys and trays, a softly backlit LCD. */
val Graphite = Palette(
    dark = true,
    page = Color(0xFF0E0F11), base = Color(0xFF1A1C20), base2 = Color(0xFF1F2125), raised = Color(0xFF2B2E34), tray = Color(0xFF15171A),
    ink = Color(0xFFE6E8EC), inkSoft = Color(0xFF9198A3), ctlInk = Color(0xFFD3D7DE),
    spec = Color(1f, 1f, 1f, 0.08f), shade = Color(0xFF040506), shadeSoft = rgba(0, 0, 0, 0.55f), shadeDeep = rgba(0, 0, 0, 0.7f),
    groove = Color(0xFF0C0D0F), screw = Color(0xFF3B3F46), mould = Color(1f, 1f, 1f, 0.035f), emboss = Color(0f, 0f, 0f, 0.6f),
    lcdA = Color(0xFFB9C68E), lcdB = Color(0xFF8B9866), lcdInk = Color(0xFF1E2517), lcdGhost = rgba(30, 37, 23, 0.12f), lcdBorder = Color(0xFF2B3124),
    tunerA = Color(0xFF363940), tunerB = Color(0xFF26282D), tick = rgba(230, 232, 236, 0.42f), tickSoft = rgba(230, 232, 236, 0.11f),
    tunerShade = rgba(0, 0, 0, 0.65f), tunerShine = Color(1f, 1f, 1f, 0.12f),
    panelTop = Color(0xFF1C1D21), panelMid = Color(0xFF16171A), panelBottom = Color(0xFF121315),
)

/**
 * Current colours. Backed by snapshot state, so switching the finish recomposes and redraws
 * everything that reads it, including plain Modifier factories.
 */
object K {
    var p: Palette by mutableStateOf(Chalk)

    val dark get() = p.dark
    val page get() = p.page
    val base get() = p.base
    val base2 get() = p.base2
    val raised get() = p.raised
    val tray get() = p.tray
    val ink get() = p.ink
    val inkSoft get() = p.inkSoft
    val ctlInk get() = p.ctlInk
    val spec get() = p.spec
    val shade get() = p.shade
    val shadeSoft get() = p.shadeSoft
    val shadeDeep get() = p.shadeDeep
    val groove get() = p.groove
    val screw get() = p.screw
    val mould get() = p.mould

    val lcdA get() = p.lcdA
    val lcdB get() = p.lcdB
    val lcdInk get() = p.lcdInk
    val lcdGhost get() = p.lcdGhost
    val lcdBorder get() = p.lcdBorder

    val tunerA get() = p.tunerA
    val tunerB get() = p.tunerB
    val tick get() = p.tick
    val tickSoft get() = p.tickSoft
    val tunerShade get() = p.tunerShade
    val tunerShine get() = p.tunerShine

    // hardware accents (same in both finishes)
    val ledGreen = Color(0xFF22C55E)
    val ledRed = Color(0xFFEF4444)
    val tickRed = Color(0xFFEF4444)
    val accent get() = if (p.dark) Color(0xFFB08AE6) else Color(0xFF8A60C2)
    val heart = Color(0xFFFF4258)
    val gold = Color(0xFFF5B93B)

    // dark top panel
    val bar get() = p.panelTop
    val well = Color(0xFF141518)
    val card = Color(0xFF1B1C1F)
    val lyric = Color(0xFFD4E2A6)
    val lyricDim = Color(0xFF5D6150)
    val dimText = Color(0xFF7D818A)
    val dimText2 = Color(0xFF6F727B)
    val tabOff = Color(0xFF868992)
    val onAir = Color(0xFFFF4D5E)
    val offDot = Color(0xFF4A4C52)
}

@Immutable
class Fonts(val inter: FontFamily, val pixel: FontFamily, val lcd: FontFamily, val dot: FontFamily)

val LocalFonts = staticCompositionLocalOf<Fonts> { error("fonts not provided") }

@Composable
fun rememberFonts(): Fonts = Fonts(
    inter = FontFamily(
        Font(Res.font.inter_400, FontWeight.Normal),
        Font(Res.font.inter_600, FontWeight.SemiBold),
        Font(Res.font.inter_700, FontWeight.Bold),
        Font(Res.font.inter_800, FontWeight.ExtraBold),
    ),
    pixel = FontFamily(Font(Res.font.pixelify_500, FontWeight.Medium), Font(Res.font.pixelify_600, FontWeight.SemiBold)),
    lcd = FontFamily(Font(Res.font.vt323)),
    dot = FontFamily(Font(Res.font.dotgothic16)),
)

/** Lines sit exactly in their box like the CSS original (no extra font padding). */
private val Tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

object T {
    @Composable fun inter(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = K.ink, lh: TextUnit = TextUnit.Unspecified, ls: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = LocalFonts.current.inter, fontSize = size, fontWeight = weight, color = color, lineHeight = lh, letterSpacing = ls, lineHeightStyle = Tight)

    /** font-pixel: Pixelify Sans 500, +0.03em */
    @Composable fun pixel(size: TextUnit, color: Color = K.ink, ls: TextUnit = 0.03.em, weight: FontWeight = FontWeight.Medium, lh: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = LocalFonts.current.pixel, fontSize = size, fontWeight = weight, color = color, letterSpacing = ls, lineHeight = lh, lineHeightStyle = Tight)

    /** .micro: compact uppercase hardware label */
    @Composable fun micro(size: TextUnit = 12.sp, color: Color = K.ctlInk, ls: TextUnit = 0.08.em) =
        TextStyle(fontFamily = LocalFonts.current.pixel, fontSize = size, fontWeight = FontWeight.SemiBold, color = color, letterSpacing = ls, lineHeight = size, lineHeightStyle = Tight)

    @Composable fun lcd(size: TextUnit, color: Color = K.lcdInk, lh: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = LocalFonts.current.lcd, fontSize = size, color = color, letterSpacing = 0.04.em, lineHeight = lh, lineHeightStyle = Tight)

    @Composable fun dot(size: TextUnit, color: Color, lh: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = LocalFonts.current.dot, fontSize = size, color = color, letterSpacing = 0.05.em, lineHeight = lh, lineHeightStyle = Tight)
}

/** .emboss: 1px white highlight under the text, as if stamped into the plastic. */
@Composable fun emboss(): Shadow = with(LocalDensity.current) { Shadow(K.p.emboss, Offset(0f, (if (K.dark) -1 else 1).dp.toPx()), 0f) }

/** .lcd-text: soft drop shadow of LCD segments. */
@Composable fun lcdShadow(): Shadow = with(LocalDensity.current) {
    val d = 1.5.dp.toPx()
    Shadow(Color(38, 46, 30, (0.18f * 255).toInt()), Offset(d, d), 0f)
}

/** Lit lyric line glow. */
@Composable fun lyricGlow(): Shadow = with(LocalDensity.current) { Shadow(Color(196, 224, 120, (0.35f * 255).toInt()), Offset.Zero, 10.dp.toPx()) }

/** Motion's spring(stiffness, damping) expressed as a Compose spring (mass 1). */
fun <T> motionSpring(stiffness: Float, damping: Float, visibilityThreshold: T? = null): SpringSpec<T> =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = visibilityThreshold)
