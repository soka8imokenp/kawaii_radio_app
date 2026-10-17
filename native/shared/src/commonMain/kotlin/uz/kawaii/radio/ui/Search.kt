package uz.kawaii.radio.ui

import uz.kawaii.radio.nowMs
import uz.kawaii.radio.PlayStat
import uz.kawaii.radio.data.Track
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

/**
 * Search over the whole catalog, typed on a little LCD. With nothing typed it is the discovery
 * page: artists (with their stories, free for everybody), then for Kawaii Pass holders songs picked
 * for the listener, what people listen to most this week, new songs, the whole catalogue, and at
 * the end what was listened to recently.
 */
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
    val pass by store.pass.collectAsState()
    val stats by store.stats.collectAsState()
    val liked by store.liked.collectAsState()
    val profile by store.profile.collectAsState()
    val trending by store.trending.collectAsState()
    LaunchedEffect(active) { if (active) store.refreshTrending() }
    val forYou = remember(tracks, stats, liked, profile.favArtist) { recommend(tracks, stats, liked, profile.favArtist) }
    val hot = remember(trending, byId) { trending.mapNotNull { (id, n) -> byId[id]?.let { it to n } }.take(10) }
    val fresh = remember(tracks) { tracks.takeLast(12).reversed() }

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
                if (!pass) {
                    item(key = "locked") {
                        LockedPanel(
                            "Siz uchun", "Tavsiyalar, hozir ko'p tinglanayotgan qo'shiqlar, yangi qo'shiqlar va tinglash tarixi obunachilar uchun.",
                            Modifier.padding(top = 6.dp),
                        )
                    }
                    return@LazyColumn
                }
                if (forYou.isNotEmpty()) {
                    item(key = "foryou-label") { SectionLabel("Siz uchun") }
                    item(key = "foryou") { CoverStrip(forYou) }
                }
                if (hot.isNotEmpty()) {
                    item(key = "hot-label") { SectionLabel("Hozir ko'p tinglanmoqda") }
                    items(hot, key = { "h-" + it.first.id }) { (t, n) ->
                        val ids = hot.map { it.first.id }
                        SongRow(
                            t, t.id == engine.id, engine.playing, sub = "${hot.indexOfFirst { it.first.id == t.id } + 1}-O'RIN · $n KISHI TINGLADI",
                            onClick = { if (t.id == engine.id) store.toggle() else store.play(t.id, ids) },
                            onLongClick = { ui.song = t.id },
                        )
                    }
                }
                if (fresh.isNotEmpty()) {
                    item(key = "fresh-label") { SectionLabel("Yangi qo'shilganlar") }
                    item(key = "fresh") { CoverStrip(fresh) }
                }
                item(key = "all") {
                    PixelKey("Barcha qo'shiqlar · ${tracks.size}", Modifier.padding(top = 6.dp).fillMaxWidth(), glyph = Glyph.LIST, height = 46.dp) { ui.library = true }
                }
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

/** Covers in a row (tap plays, the row becomes the queue). */
@Composable
private fun CoverStrip(list: List<Track>) {
    val store = LocalStore.current
    val sound = LocalSound.current
    val ids = list.map { it.id }
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(list, key = { it.id }) { t ->
            Column(Modifier.width(96.dp).tap(onClick = { sound.press(); store.play(t.id, ids) })) {
                Box(Modifier.tray(RoundedCornerShape(14.dp)).padding(4.dp)) { Cover(t.coverArt, 88.dp, 10.dp) }
                BasicText(t.title, Modifier.padding(top = 6.dp), style = T.pixel(12.sp, K.ink), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                BasicText(mainArtist(t.artist), style = T.pixel(11.sp, K.inkSoft), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Songs for this listener: artists they like (liked songs, songs they chose to listen to, their
 * favourite artist) weigh most, songs they already know are left out, and a daily seed mixes the
 * rest so the row changes from day to day.
 */
private fun recommend(tracks: List<Track>, stats: Map<String, PlayStat>, liked: List<String>, fav: String?): List<Track> {
    val byId = tracks.associateBy { it.id }
    val taste = HashMap<String, Double>()
    fun add(artist: String, w: Double) { taste[artist.lowercase()] = (taste[artist.lowercase()] ?: 0.0) + w }
    liked.forEach { id -> byId[id]?.let { add(mainArtist(it.artist), 3.0) } }
    stats.forEach { (id, st) -> byId[id]?.let { add(mainArtist(it.artist), st.seconds / 120.0) } }
    fav?.let { add(it, 5.0) }
    val known = liked.toSet() + stats.filter { it.value.total > 90 }.keys
    val rnd = kotlin.random.Random(nowMs() / 86_400_000)
    return tracks.filter { it.id !in known }
        .map { it to (taste[mainArtist(it.artist).lowercase()] ?: 0.0).coerceAtMost(12.0) + rnd.nextDouble() * 2.5 }
        .sortedByDescending { it.second }
        .take(12)
        .map { it.first }
}
