package uz.kawaii.radio.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import uz.kawaii.radio.res.Res
import uz.kawaii.radio.res.logo

/** Pixel-art glyph from rows of '#': crisp hardware silkscreen icons. */
@Composable
fun Px(rows: List<String>, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val w = rows.maxOf { it.length }
    val cells = remember(rows) { rows.flatMapIndexed { y, r -> r.mapIndexedNotNull { x, c -> if (c == '#') x to y else null } } }
    Canvas(modifier.size(size, size * rows.size / w)) {
        val k = this.size.width / w
        // a hair of overlap so neighbouring cells never show seams
        for ((x, y) in cells) drawRect(color, Offset(x * k, y * k), Size(k + 0.5f, k + 0.5f))
    }
}

/** Multi-colour pixel sprite: every character of [rows] is a colour from [palette]; '.' is empty. */
@Composable
fun PxArt(rows: List<String>, palette: Map<Char, Color>, size: Dp, modifier: Modifier = Modifier) {
    val w = rows.maxOf { it.length }
    val cells = remember(rows) { rows.flatMapIndexed { y, r -> r.mapIndexedNotNull { x, c -> if (c != '.') Triple(x, y, c) else null } } }
    Canvas(modifier.size(size, size * rows.size / w)) {
        val k = this.size.width / w
        for ((x, y, c) in cells) drawRect(palette[c] ?: Color.Magenta, Offset(x * k, y * k), Size(k + 0.5f, k + 0.5f))
    }
}

/**
 * The chat reactions as little pixel sprites in the radio's own style. They still travel as the
 * emoji the server knows (❤️ 🔥 😂 😭), so older app versions keep working.
 */
object Reaction {
    private val ink = Color(0xFF3B2B33)
    private class Sprite(val rows: List<String>, val palette: Map<Char, Color>)
    private val sprites = mapOf(
        "❤️" to Sprite(
            listOf(".ooo...ooo.", "owppo.opppo", "owpppopppPo", "opppppppPPo", ".oppppppPo.", "..oppppPo..", "...oppPo...", "....oPo....", ".....o....."),
            mapOf('o' to ink, 'p' to Color(0xFFFF7A9C), 'P' to Color(0xFFE0476E), 'w' to Color.White),
        ),
        "🔥" to Sprite(
            listOf("....o......", "...oro.....", "...orro.o..", "..orrrooro.", ".orrrrrrro.", ".orryrrrro.", "orryyyrrrro", "orryyyyrrro", "orryywyyrro", ".orryyyyro.", "..ooooooo.."),
            mapOf('o' to ink, 'r' to Color(0xFFFF6A3D), 'y' to Color(0xFFFFD54A), 'w' to Color(0xFFFFF6D0)),
        ),
        "😂" to Sprite(
            listOf("...ooooo...", ".ooyyyyyoo.", ".oyyyyyyyo.", "oyyoyyyoyyo", "oyoyoyoyoyo", "oyyyyyyyyyo", "ocyommmoyco", "oyyommmoyyo", ".oyyoooyyo.", "..ooyyyoo..", "....ooo...."),
            mapOf('o' to ink, 'y' to Color(0xFFFFD86B), 'm' to Color(0xFFB8323F), 'c' to Color(0xFFFF9AB5)),
        ),
        "😭" to Sprite(
            listOf("...ooooo...", ".ooyyyyyoo.", ".oyyyyyyyo.", "oyooyyyooyo", "oyyoyyyoyyo", "oyybyyybyyo", "oyybyyybyyo", "oyyyoooyyyo", ".oyoyyyoyo.", "..ooyyyoo..", "....ooo...."),
            mapOf('o' to ink, 'y' to Color(0xFFFFD86B), 'b' to Color(0xFF6EC6FF)),
        ),
    )

    /** The sprite for [emoji], or the emoji itself as text when there is none. */
    @Composable
    fun Icon(emoji: String, size: Dp, modifier: Modifier = Modifier) {
        val s = sprites[emoji]
        if (s != null) PxArt(s.rows, s.palette, size, modifier)
        else androidx.compose.foundation.text.BasicText(emoji, modifier)
    }
}

object Glyph {
    /** Sticker pack key: a round kawaii face. */
    val SMILE = listOf(
        "...######...", ".##......##.", ".#........#.", "#..##..##..#", "#..##..##..#", "#..........#",
        "#.#......#.#", "#..#....#..#", ".#..####..#.", ".##......##.", "...######...",
    )
    val INFO = listOf(
        ".##########.", "#..........#", "#....##....#", "#..........#", "#...###....#", "#....##....#",
        "#....##....#", "#....##....#", "#...####...#", "#..........#", ".##########.",
    )
    val SAVE = listOf(
        "##########..", "#..#####.##.", "#..#####..##", "#..##.##...#", "#..#####...#", "#..........#",
        "#.########.#", "#.#......#.#", "#.########.#", "#.#......#.#", "############",
    )
    val HEART_FULL = listOf(
        ".###...###.", "###########", "###########", "###########", ".#########.", "..#######..",
        "...#####...", "....###....", ".....#.....",
    )
    val HEART = listOf(
        ".###...###.", "#...#.#...#", "#....#....#", "#.........#", ".#.......#.", "..#.....#..",
        "...#...#...", "....#.#....", ".....#.....",
    )
    val RADIO = listOf(
        ".........##.", ".......##...", ".....##.....", "############", "#..........#", "#.####..##.#",
        "#.####.#..##", "#.####.#..##", "#.####..##.#", "#..........#", "############",
    )
    val STAR = listOf(
        ".....##.....", ".....##.....", "....####....", "############", ".##########.", "..########..",
        "...######...", "..###..###..", ".##......##.",
    )
    val DOWNLOAD = listOf(
        "....##....", "....##....", "....##....", "....##....", "..######..", "...####...", "....##....",
        "..........", "##########", "##########",
    )
    val CHECK = listOf(
        ".........##", "........##.", ".......##..", "##....##...", ".##..##....", "..####.....", "...##......",
    )
    val LIST = listOf(
        "##.#########", "............", "##.#########", "............", "##.#########", "............", "##.#########",
    )
    val SEARCH = listOf(
        "..#####.....", ".##...##....", "##.....##...", "#.......#...", "#.......#...", "#.......#...",
        "##.....##...", ".##...###...", "..#####.##..", ".........##.", "..........##", "...........#",
    )
    val PLAY = listOf(
        "##......", "####....", "######..", "########", "########", "######..", "####....", "##......",
    )
    val NEXT = listOf(
        "#....#.#", "##...#.#", "###..#.#", "####.#.#", "####.#.#", "###..#.#", "##...#.#", "#....#.#",
    )
    val SHUFFLE = listOf(
        "##.......###", "..#.....#..#", "...#...#....", "....#.#.....", ".....#......", "....#.#.....",
        "...#...#....", "..#.....#..#", "##.......###",
    )
    val CLOCK = listOf(
        "...######...", "..#......#..", ".#...#....#.", "#....#.....#", "#....#.....#", "#....####..#",
        "#..........#", "#..........#", ".#........#.", "..#......#..", "...######...",
    )
    val QUEUE = listOf(
        "##########..", "............", "##########..", "............", "######...#..", ".........##.",
        "######..####", ".........##.", ".........#..",
    )
    val MOON = listOf(
        "....####...", "..####.....", ".####......", ".###.......", "####.......", "####.......",
        "####.......", ".####......", ".######..##", "..########.", "....####...",
    )
    val CHAT = listOf(
        ".##########.", "############", "##........##", "##.##.##.###", "##........##", "############",
        ".##########.", "..###.......", "..##........", "..#.........",
    )
    val USER = listOf(
        "....####....", "...######...", "...######...", "...######...", "....####....", "............",
        "..########..", ".##########.", "############", "############",
    )
    val REPLY = listOf(
        "....##......", "...###......", "..####......", ".##########.", "###########.", ".####....##.",
        "..###....##.", "...##....##.", ".........##.", ".......####.",
    )
    val COPY = listOf(
        ".########...", ".#......#...", ".#..########", ".#..#......#", ".#..#......#", ".#..#......#",
        ".####......#", "....#......#", "....########",
    )
    val TRASH = listOf(
        "...######...", ".##########.", "............", ".##########.", ".##.##.##.#.", ".##.##.##.#.",
        ".##.##.##.#.", ".##.##.##.#.", "..########..",
    )
    val BAN = listOf(
        "..######..", ".##....##.", "##.##...##", "##..##..##", "##...##.##", ".##....##.", "..######..",
    )
}

@Composable
fun HeartIcon(filled: Boolean, color: Color) = Px(if (filled) Glyph.HEART_FULL else Glyph.HEART, 22.dp, if (filled) uz.kawaii.radio.ui.theme.K.heart else color)

/** Outlined play triangle (16-unit viewbox). */
@Composable
fun PlayIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        scale(size.width / 16f, pivot = Offset.Zero) {
            val p = Path().apply { moveTo(4.5f, 2.8f); lineTo(4.5f, 13.2f); lineTo(12.9f, 8f); close() }
            drawPath(p, color, style = Stroke(1.3f, join = StrokeJoin.Round))
        }
    }
}

/** Two outlined bars. */
@Composable
fun PauseIcon(color: Color) {
    Canvas(Modifier.size(26.dp)) {
        scale(size.width / 16f, pivot = Offset.Zero) {
            val st = Stroke(1.25f)
            drawRoundRect(color, Offset(3.4f, 2.4f), Size(3.2f, 11.2f), CornerRadius(0.9f), style = st)
            drawRoundRect(color, Offset(9.4f, 2.4f), Size(3.2f, 11.2f), CornerRadius(0.9f), style = st)
        }
    }
}

/** The Kawaii mascot logo, tinted. */
@Composable
fun Logo(color: Color, modifier: Modifier = Modifier) {
    Image(painterResource(Res.drawable.logo), null, modifier, colorFilter = ColorFilter.tint(color))
}
