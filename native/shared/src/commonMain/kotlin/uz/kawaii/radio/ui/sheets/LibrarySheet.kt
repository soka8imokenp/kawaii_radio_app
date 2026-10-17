package uz.kawaii.radio.ui.sheets

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kawaii.radio.data.Station
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.data.formatDuration
import uz.kawaii.radio.data.mainArtist
import uz.kawaii.radio.ui.Chip
import uz.kawaii.radio.ui.Cover
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.LocalUi
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.pressClick
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.emboss

private enum class Shelf(val label: String) { ALL("Hammasi"), JPOP("J-Pop"), OST("OST"), RIOT("Riot Music"), SAVED("Saqlangan") }
private enum class Order(val label: String) { AZ("A–Z"), ARTIST("Ijrochi"), NEW("Yangi") }

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** Section letter for the A–Z list: Latin letters as they are, everything else (kana, kanji, digits) under "#". */
private fun letter(s: String): String = s.trim().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"

/**
 * The whole catalogue as a list: shelves, three orders with sticky section headers, a quick
 * filter. Tap plays (the visible list becomes the queue), long-press opens the song card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val sound = LocalSound.current
    val tracks by store.tracks.collectAsState()
    val saved by store.saved.collectAsState()
    val engine by store.engine.collectAsState()
    var shelf by remember { mutableStateOf(Shelf.ALL) }
    var order by remember { mutableStateOf(Order.AZ) }
    var query by remember { mutableStateOf("") }
    val q = norm(query)

    val list = remember(tracks, saved, shelf, order, q) {
        tracks.filter {
            when (shelf) {
                Shelf.ALL -> true
                Shelf.JPOP -> it.station == Station.JPOP
                Shelf.OST -> it.station == Station.OST
                Shelf.RIOT -> it.station == Station.RIOT
                Shelf.SAVED -> it.id in saved
            } && (q.isEmpty() || norm(it.title).contains(q) || norm(it.artist).contains(q))
        }.let { l ->
            when (order) {
                Order.AZ -> l.sortedWith(compareBy({ letter(it.title) == "#" }, { it.title.lowercase() }))
                Order.ARTIST -> l.sortedWith(compareBy({ mainArtist(it.artist).lowercase() }, { it.title.lowercase() }))
                Order.NEW -> l.reversed() // the cloud appends, so the end is the newest
            }
        }
    }
    val sections: List<Pair<String, List<Track>>> = remember(list, order) {
        when (order) {
            Order.AZ -> list.groupBy { letter(it.title) }.toList()
            Order.ARTIST -> list.groupBy { mainArtist(it.artist) }.toList()
            Order.NEW -> listOf("" to list)
        }
    }
    val ids = list.map { it.id }
    val listState = rememberLazyListState()
    LaunchedEffect(shelf, order, q) { listState.scrollToItem(0) }

    BaseSheet(open, onClose) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(56.dp, 6.dp).tray(CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("RO'YXAT", style = T.pixel(16.sp, K.ink, ls = 1.6.sp, weight = FontWeight.SemiBold).copy(shadow = emboss()))
                BasicText("${list.size} ta qo'shiq", style = T.pixel(12.sp, K.inkSoft))
            }
            Box(Modifier.raised(CircleShape).pressClick(onClick = onClose).padding(horizontal = 12.dp, vertical = 4.dp)) {
                BasicText("YOPISH", style = T.micro(11.sp).copy(shadow = emboss()))
            }
        }

        // quick filter on a strip of LCD
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().tray(RoundedCornerShape(16.dp)).padding(4.dp)) {
            Row(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(12.dp)).border(2.dp, K.lcdBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Px(Glyph.SEARCH, 14.dp, K.lcdInk)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) BasicText("FILTR", style = T.pixel(15.sp, K.lcdInk.copy(alpha = 0.4f), lh = 17.sp))
                    BasicTextField(
                        query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = T.pixel(15.sp, K.lcdInk, lh = 17.sp), cursorBrush = SolidColor(K.lcdInk),
                    )
                }
            }
        }
        Row(
            Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (s in Shelf.entries) Chip(s.label, shelf == s) { shelf = s }
        }
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in Order.entries) Chip(o.label, order == o) { order = o }
        }
        GrooveH(Modifier.padding(horizontal = 20.dp))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            if (list.isEmpty()) item { BasicText("Hech narsa topilmadi", Modifier.padding(top = 24.dp), style = T.pixel(14.sp, K.inkSoft)) }
            for ((head, songs) in sections) {
                if (head.isNotEmpty()) stickyHeader(key = "h-$head") {
                    // covers the list's side padding too, so no song peeks out beside the letter
                    Column(Modifier.bleedX(20.dp)) {
                        BasicText(
                            head.uppercase(),
                            Modifier.fillMaxWidth().background(K.base2).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                            style = T.micro(11.sp, K.inkSoft, ls = 0.1.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Box(Modifier.fillMaxWidth().height(10.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(K.base2, K.base2.copy(alpha = 0f)))))
                    }
                }
                items(songs, key = { it.id }) { t ->
                    val current = t.id == engine.id
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .raised(RoundedCornerShape(12.dp), current)
                            .pointerInput(t.id, ids) {
                                detectTapGestures(
                                    onPress = { sound.press() },
                                    onTap = { if (current) store.toggle() else { store.play(t.id, ids); onClose() } },
                                    onLongPress = { ui.song = t.id },
                                )
                            }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Cover(t.coverArt, 38.dp, 7.dp)
                        Column(Modifier.weight(1f)) {
                            BasicText(t.title, style = T.pixel(14.sp, K.ink, weight = FontWeight.SemiBold, lh = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(t.artist, style = T.pixel(12.sp, K.inkSoft, lh = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (t.id in saved) Px(Glyph.CHECK, 11.dp, K.ledGreen)
                        if (current) Led(engine.playing)
                        if (t.duration > 0) BasicText(formatDuration(t.duration), Modifier.width(36.dp), style = T.lcd(17.sp, K.inkSoft))
                    }
                }
            }
        }
    }
}


/** Stretches a child [x] past both sides of the parent's horizontal padding. */
private fun Modifier.bleedX(x: androidx.compose.ui.unit.Dp) = layout { m, c ->
    val extra = x.roundToPx() * 2
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}
