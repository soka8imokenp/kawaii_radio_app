package uz.kawaii.radio.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import uz.kawaii.radio.data.Artist
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.LocalUi
import uz.kawaii.radio.ui.SectionLabel
import uz.kawaii.radio.ui.SongRow
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T

/** Round artist picture; falls back to the cover of one of their songs, then to their initial. */
@Composable
fun ArtistPhoto(a: Artist, size: Dp, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    val tracks by store.tracks.collectAsState()
    val fallback = remember(a, tracks) { tracks.firstOrNull { a.sings(it.artist) }?.coverArt }
    // small avatars load the small picture: decoding big images for a grid is what made it stutter
    val url = (if (size <= 100.dp) a.thumb ?: a.picture else a.picture ?: a.thumb) ?: fallback
    val m = modifier.size(size).clip(CircleShape).border(2.dp, K.lcdBorder.copy(alpha = 0.35f), CircleShape)
    if (url != null) {
        AsyncImage(url, a.name, m.background(K.base2), contentScale = ContentScale.Crop)
    } else {
        Box(m.background(K.base2), contentAlignment = Alignment.Center) {
            BasicText(a.name.take(1).uppercase(), style = T.display(size.value.times(0.4f).sp, K.inkSoft))
        }
    }
}

/** "Hammasini ko'rish": every artist as an avatar with a name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllArtistsSheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val sound = LocalSound.current
    val artists by store.artists.collectAsState()
    val tracks by store.tracks.collectAsState()
    // the most-played artists in our catalogue first
    val sorted = remember(artists, tracks) { artists.sortedByDescending { a -> tracks.count { a.sings(it.artist) } } }
    Sheet(open, onClose, "Ijrochilar", scroll = false) {
        // lazy grid: only the avatars on screen are built and loaded, so it scrolls without stutter
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(sorted, key = { it.id }) { a ->
                Column(
                    Modifier.fillMaxWidth().tap(onClick = { sound.press(); ui.artist = a.id }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtistPhoto(a, 76.dp)
                    BasicText(
                        a.name, Modifier.padding(top = 6.dp),
                        style = T.pixel(13.sp, K.ink, weight = FontWeight.SemiBold).copy(textAlign = TextAlign.Center),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One artist: picture, who they are, why they are famous, their best-known songs and ours to play. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistSheet(id: String?, onClose: () -> Unit) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val artists by store.artists.collectAsState()
    val engine by store.engine.collectAsState()
    val a = remember(id, artists) { artists.firstOrNull { it.id == id } }
    Sheet(id != null && a != null, onClose, "Ijrochi") {
        if (a == null) return@Sheet
        // two or three of ours are enough: the page is about the artist, not a playlist
        val songs = remember(a) { store.songsOf(a).take(3) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ArtistPhoto(a, 132.dp)
            BasicText(a.name, Modifier.padding(top = 12.dp), style = T.display(26.sp, K.ink, weight = FontWeight.SemiBold))
            if (a.tagline.isNotBlank()) {
                BasicText(a.tagline, Modifier.padding(top = 6.dp), style = T.pixel(14.sp, K.inkSoft).copy(textAlign = TextAlign.Center))
            }
            val meta = listOf(a.genre, a.origin, a.since).filter { it.isNotBlank() }
            if (meta.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in meta) {
                        BasicText(m, Modifier.raised(RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp), style = T.pixel(12.sp, K.ink))
                    }
                }
            }
        }

        if (a.bio.isNotEmpty()) {
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in a.bio) BasicText(p, style = T.pixel(15.sp, K.ink, lh = 22.sp))
            }
        }

        if (a.known.isNotEmpty()) {
            SectionLabel("Eng mashhur qo'shiqlari")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in a.known) {
                    Row(Modifier.raised(RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("♪ $k", style = T.pixel(13.sp, K.ink))
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            SectionLabel("Kawaii Radio'da tinglang")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ids = songs.map { it.id }
                for (t in songs) {
                    SongRow(
                        t, t.id == engine.id, engine.playing,
                        onClick = { if (t.id == engine.id) store.toggle() else store.play(t.id, ids) },
                        onLongClick = { ui.song = t.id },
                    )
                }
            }
        }

        a.pictureSource?.let {
            BasicText("Rasm: $it", Modifier.fillMaxWidth().padding(top = 16.dp), style = T.pixel(11.sp, K.inkSoft).copy(textAlign = TextAlign.Center))
        }
    }
}
