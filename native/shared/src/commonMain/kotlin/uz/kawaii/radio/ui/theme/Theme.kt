package uz.kawaii.radio.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
object K {
    // chalk & putty
    val page = Color(0xFFDFE1E6)
    val base = Color(0xFFEBECEF)
    val base2 = Color(0xFFEFF1F5)
    val raised = Color(0xFFFAFAFC)
    val tray = Color(0xFFE3E5EA)
    val ink = Color(0xFF374151)
    val inkSoft = Color(0xFF6B7280)
    val ctlInk = Color(0xFF4B5563)
    val spec = Color.White
    val shade = Color(0xFFB8BDCB)
    val shadeSoft = Color(184, 189, 203, (0.55f * 255).toInt())
    val shadeDeep = Color(140, 146, 163, (0.5f * 255).toInt())
    val groove = Color(0xFFD5D8DF)
    val hl = Color(1f, 1f, 1f, 0.95f)
    val screw = Color(0xFFC3C7D1)

    // hardware accents
    val ledGreen = Color(0xFF22C55E)
    val ledRed = Color(0xFFEF4444)
    val tickRed = Color(0xFFEF4444)
    val accent = Color(0xFF8A60C2)
    val heart = Color(0xFFFF4258)
    val gold = Color(0xFFF5B93B)

    // LCD
    val lcdA = Color(0xFFCFD8AA)
    val lcdB = Color(0xFFAAB68A)
    val lcdInk = Color(0xFF262E1E)
    val lcdGhost = Color(38, 46, 30, (0.1f * 255).toInt())
    val lcdBorder = Color(0xFF3A4131)

    // tuner wheel
    val tunerA = Color(0xFFFDFDFE)
    val tunerB = Color(0xFFECEEF2)
    val tick = Color(55, 65, 81, (0.45f * 255).toInt())
    val tickSoft = Color(55, 65, 81, (0.14f * 255).toInt())
    val tunerShade = Color(184, 189, 203, (0.85f * 255).toInt())
    val tunerShine = Color(1f, 1f, 1f, 0.9f)

    // dark top panel
    val bar = Color(0xFF2C2E33)
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
@Composable fun emboss(): Shadow = with(LocalDensity.current) { Shadow(Color(1f, 1f, 1f, 0.9f), Offset(0f, 1.dp.toPx()), 0f) }

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
