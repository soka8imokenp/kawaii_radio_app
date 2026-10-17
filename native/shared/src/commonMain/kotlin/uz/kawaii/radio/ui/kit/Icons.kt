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

object Glyph {
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
