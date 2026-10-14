package uz.kawaii.radio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kawaii.radio.data.formatDuration
import uz.kawaii.radio.data.formatListening
import uz.kawaii.radio.data.formatShortDate
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.HeartIcon
import uz.kawaii.radio.ui.kit.Key
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T

private enum class Sort(val label: String) { NEW("Yangi"), AZ("A–Z"), TOP("Ko'p tinglangan") }

/**
 * Liked songs: play all / shuffle, three sort orders, and every row opens the song card
 * (when it was liked, how often it was played…). The round key plays it right away.
 */
@Composable
fun Liked(onOpenRadio: () -> Unit) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val tracks by store.tracks.collectAsState()
    val likes by store.likes.collectAsState()
    val stats by store.stats.collectAsState()
    val engine by store.engine.collectAsState()
    val saved by store.saved.collectAsState()
    val downloads by store.downloads.collectAsState()
    var sort by remember { mutableStateOf(Sort.NEW) }

    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val list = likes.keys.mapNotNull { byId[it] }.let { l ->
        when (sort) {
            Sort.NEW -> l.sortedByDescending { likes[it.id] ?: 0 }
            Sort.AZ -> l.sortedBy { it.title.lowercase() }
            Sort.TOP -> l.sortedByDescending { stats[it.id]?.seconds ?: 0 }
        }
    }
    val ids = list.map { it.id }
    val total = list.sumOf { it.duration }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Sevimlilar",
            if (list.isEmpty()) "Yoqtirgan qo'shiqlaringiz" else "${list.size} ta qo'shiq · ${formatListening(total.toLong())} · bosib turing: karta",
        ) { CountPlate(list.size, K.heart) }

        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).raised(CircleShape), contentAlignment = Alignment.Center) { HeartIcon(false, K.ctlInk) }
                Spacer(Modifier.height(16.dp))
                BasicText("HALI SEVIMLI QO'SHIQ YO'Q", style = T.pixel(16.sp, K.ink))
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "Radioda yurakcha tugmasini bosing — qo'shiq shu yerda paydo bo'ladi.",
                    Modifier.widthIn(max = 250.dp),
                    style = T.pixel(13.sp, K.inkSoft, lh = 17.sp).copy(textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(20.dp))
                PixelKey("Radioga o'tish", Modifier.widthIn(min = 180.dp), glyph = Glyph.RADIO, glyphSize = 16.dp, onClick = onOpenRadio)
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelKey("Tinglash", Modifier.weight(1f), glyph = Glyph.PLAY, glyphSize = 11.dp) { store.playAll(ids, shuffle = false) }
                        PixelKey("Aralash", Modifier.weight(1f), glyph = Glyph.SHUFFLE, glyphSize = 15.dp) { store.playAll(ids, shuffle = true) }
                    }
                    // everything liked onto the phone, for listening without internet
                    val left = ids.count { it !in saved }
                    val busy = ids.count { it in downloads }
                    PixelKey(
                        when {
                            busy > 0 -> "Saqlanmoqda · ${ids.size - left}/${ids.size}"
                            left == 0 -> "Hammasi telefonda ✓"
                            else -> "Hammasini saqlash · $left ta"
                        },
                        Modifier.fillMaxWidth(), glyph = if (left == 0) Glyph.CHECK else Glyph.DOWNLOAD, glyphSize = 13.dp,
                        active = left == 0 || busy > 0,
                    ) { if (store.premium) store.saveAll(ids) else ui.premium = true }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in Sort.entries) Chip(s.label, sort == s) { sort = s }
                    }
                }
            }
            items(list, key = { it.id }) { t ->
                val current = t.id == engine.id
                val st = stats[t.id]
                val sub = buildString {
                    append("♥ ").append(formatShortDate(likes[t.id] ?: 0))
                    if ((st?.plays ?: 0) > 0) append(" · ${st!!.plays} marta")
                    if (t.duration > 0) append(" · ").append(formatDuration(t.duration))
                }
                Box(Modifier.animateItem()) {
                    SongRow(
                        t, current, engine.playing, sub.uppercase(),
                        onClick = { if (current) store.toggle() else store.play(t.id, ids) },
                        onLongClick = { ui.song = t.id },
                        trailing = {
                            Key(RoundedCornerShape(13.dp), Modifier.size(44.dp, 62.dp), active = true, onPress = { store.toggleLike(t.id) }) {
                                HeartIcon(true, K.ctlInk)
                            }
                        },
                    )
                }
            }
        }
    }
}
