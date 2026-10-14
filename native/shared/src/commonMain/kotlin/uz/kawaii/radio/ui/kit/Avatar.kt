package uz.kawaii.radio.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Pixel avatars: a glyph on a coloured "screen". Index 0 is the Kawaii bunny. */
object Avatars {
    val CAT = listOf(
        "#..........#", "##........##", "###......###", "############", "############", "##.##..##.##",
        "##.##..##.##", "############", "#####..#####", "############", ".##########.", "..########..",
    )
    val GHOST = listOf(
        "....####....", "..########..", ".##########.", ".##..##..##.", "###..##..###", "############",
        "############", "############", "############", "############", "##.##..##.##", "#...#..#...#",
    )
    val STAR = listOf(
        ".....##.....", ".....##.....", "....####....", "....####....", "############", ".##########.",
        "..########..", "...######...", "..########..", "..###..###..", ".###....###.", ".##......##.",
    )
    val PHONES = listOf(
        "...######...", "..##....##..", ".##......##.", "##........##", "#..........#", "#..........#",
        "###......###", "####....####", "####....####", "####....####", "####....####", ".##......##.",
    )
    val ONIGIRI = listOf(
        ".....##.....", "....####....", "...######...", "..########..", "..########..", ".##########.",
        ".##########.", "############", "####....####", "####....####", "####....####", ".##########.",
    )
    val NOTE = listOf(
        "....#######.", "....#######.", "....#.....#.", "....#.....#.", "....#.....#.", "....#.....#.",
        "....#....###", "..###...####", ".####...####", ".####....##.", "..##........", "............",
    )

    /** (background, glyph) — glyph null means the Kawaii logo. */
    val all: List<Pair<Color, List<String>?>> = listOf(
        Color(0xFFCFD8AA) to null,
        Color(0xFFFFB8CB) to CAT,
        Color(0xFFB9DDFF) to GHOST,
        Color(0xFFFFE08A) to STAR,
        Color(0xFFCDBBFF) to PHONES,
        Color(0xFFB5EFD3) to ONIGIRI,
        Color(0xFFFFCFA6) to NOTE,
        Color(0xFFFF9AA8) to Glyph.HEART_FULL,
    )
}

private val AvatarInk = Color(0xFF262E1E)

/** Round avatar: the gallery photo, or a pixel glyph on its colour. */
@Composable
fun AvatarView(avatar: String, photo: String?, size: Dp, modifier: Modifier = Modifier) {
    val inner = Modifier.size(size).clip(CircleShape)
    if (avatar == "photo" && photo != null) {
        AsyncImage(photo, null, modifier.then(inner), contentScale = ContentScale.Crop)
        return
    }
    val n = avatar.removePrefix("p:").toIntOrNull()?.coerceIn(0, Avatars.all.lastIndex) ?: 0
    val (bg, glyph) = Avatars.all[n]
    Box(
        modifier.then(inner).background(bg).innerShadow(CircleShape, Shadow(4.dp, Color(0f, 0f, 0f, 0.25f), offset = DpOffset(0.dp, 2.dp))),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph == null) Logo(AvatarInk, Modifier.size(size * 0.56f))
        else Px(glyph, size * 0.5f, AvatarInk, Modifier.padding(top = size * 0.02f))
    }
}
