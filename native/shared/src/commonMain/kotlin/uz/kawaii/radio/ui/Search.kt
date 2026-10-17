package uz.kawaii.radio.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.raised
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kawaii.radio.data.mainArtist
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lcdShadow

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** Search over the whole catalog, typed on a little LCD; artists and recent songs when empty. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Search(active: Boolean) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val tracks by store.tracks.collectAsState()
    val engine by store.engine.collectAsState()
    val recent by store.recent.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    LaunchedEffect(active) { if (!active) focus.clearFocus() }

    val q = norm(query)
    val results = remember(q, tracks) {
        if (q.isEmpty()) emptyList()
        else tracks.filter { norm(it.title).contains(q) || norm(it.artist).contains(q) || norm(it.category).contains(q) }
    }
    val artists = remember(tracks) {
        tracks.groupingBy { mainArtist(it.artist) }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val recentTracks = recent.mapNotNull { byId[it] }.take(8)

    Column(Modifier.fillMaxSize()) {
        PageHeader("Qidiruv", "${tracks.size} ta qo'shiq efirda")

        // the search field is a strip of LCD glass with a blinking pixel cursor
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(5.dp)) {
            Row(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(13.dp)).border(2.dp, K.lcdBorder, RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Px(Glyph.SEARCH, 16.dp, K.lcdInk)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) BasicText("QO'SHIQ YOKI IJROCHI", style = T.pixel(16.sp, K.lcdInk.copy(alpha = 0.4f), lh = 18.sp))
                    BasicTextField(
                        query, { query = it },
                        Modifier.fillMaxWidth(),
                        enabled = active,
                        singleLine = true,
                        textStyle = T.pixel(16.sp, K.lcdInk, lh = 18.sp).copy(shadow = lcdShadow()),
                        cursorBrush = SolidColor(K.lcdInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
                if (query.isNotEmpty()) BasicText("✕", Modifier.tap(onClick = { query = "" }).padding(4.dp), style = T.pixel(15.sp, K.lcdInk))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (q.isEmpty()) {
                item(key = "artists") { ArtistStrip() }
                if (recentTracks.isNotEmpty()) {
                    item(key = "recent-label") { SectionLabel("Yaqinda tinglangan") }
                    items(recentTracks, key = { "r-" + it.id }) { t ->
                        val ids = recentTracks.map { it.id }
                        SongRow(
                            t, t.id == engine.id, engine.playing,
                            onClick = { if (t.id == engine.id) store.toggle() else store.play(t.id, ids) },
                            onLongClick = { ui.song = t.id },
                        )
                    }
                }
            } else {
                item(key = "count") { SectionLabel(if (results.isEmpty()) "Hech narsa topilmadi" else "${results.size} ta natija") }
                items(results, key = { it.id }) { t ->
                    val ids = results.map { it.id }
                    SongRow(
                        t, t.id == engine.id, engine.playing, sub = t.category.uppercase(),
                        onClick = { focus.clearFocus(); if (t.id == engine.id) store.toggle() else store.play(t.id, ids) },
                        onLongClick = { ui.song = t.id },
                    )
                }
            }
        }
    }
}

/** Spotify-like row of artist cards with "Hammasini ko'rish"; a tap opens the artist page. */
@Composable
private fun ArtistStrip() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val sound = LocalSound.current
    val index by store.artistIndex.collectAsState()
    if (index.order.isEmpty()) return
    val top = remember(index) { index.order.take(12) }
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("IJROCHILAR", Modifier.weight(1f), style = T.micro(12.sp, K.inkSoft))
            BasicText(
                "Hammasini ko'rish ›",
                Modifier.raised(RoundedCornerShape(10.dp)).tap(onClick = { sound.press(); ui.artists = true }).padding(horizontal = 10.dp, vertical = 5.dp),
                style = T.pixel(12.sp, K.ink),
            )
        }
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.padding(top = 12.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(top.size, key = { top[it].id }) { i ->
                val a = top[i]
                Column(
                    Modifier.width(84.dp).tap(onClick = { sound.press(); ui.artist = a.id }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    uz.kawaii.radio.ui.sheets.ArtistPhoto(a, 72.dp)
                    BasicText(
                        a.name, Modifier.padding(top = 6.dp),
                        style = T.pixel(12.sp, K.ink, weight = FontWeight.SemiBold).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
