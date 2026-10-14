package uz.kawaii.radio.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.kawaii.radio.data.mainArtist
import uz.kawaii.radio.data.monthKey
import uz.kawaii.radio.data.monthTitle
import uz.kawaii.radio.nowMs
import uz.kawaii.radio.ui.Chip
import uz.kawaii.radio.ui.Cover
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.PixelKey
import uz.kawaii.radio.ui.kit.AvatarView
import uz.kawaii.radio.ui.kit.Logo
import uz.kawaii.radio.ui.kit.dotScreen
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lcdShadow

/**
 * "Kawaii Wrapped": a month of listening on one card — minutes, top artist, top songs — that can
 * be shared as a picture.
 */
@Composable
fun WrappedSheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val months by store.months.collectAsState()
    val tracks by store.tracks.collectAsState()
    val profile by store.profile.collectAsState()
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val keys = (months.keys + monthKey(nowMs())).distinct().sortedDescending().take(6)
    var month by remember { mutableStateOf(monthKey(nowMs())) }
    val layer = rememberGraphicsLayer()

    Sheet(open, onClose, "Kawaii Wrapped") {
        val data = months[month].orEmpty()
        val seconds = data.values.sum()
        val songs = data.entries.filter { byId[it.key] != null }.sortedByDescending { it.value }
        val artists = songs.groupBy({ mainArtist(byId[it.key]!!.artist) }, { it.value }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }
        val topSong = songs.firstOrNull()?.let { byId[it.key] }
        val name = profile.name.ifBlank { "Mehmon" }

        if (keys.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in keys) Chip(monthTitle(k), k == month) { month = k }
            }
        }

        // the card itself: recorded into a layer so it can be shared as a picture
        Column(
            Modifier.fillMaxWidth()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .background(Brush.verticalGradient(listOf(Color(0xFF2C2E33), Color(0xFF1A1B1E))), RoundedCornerShape(26.dp))
                .dotScreen(0.05f)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(Color(0xFFF0F0F0), Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BasicText("KAWAII WRAPPED", style = T.pixel(16.sp, Color.White, ls = 0.1.em, lh = 16.sp))
                    BasicText(monthTitle(month), Modifier.padding(top = 3.dp), style = T.pixel(11.sp, K.dimText, ls = 0.12.em, lh = 11.sp))
                }
                AvatarView(profile.avatar, store.platform.avatarPhoto(), 34.dp)
            }

            // minutes on the LCD
            Column(
                Modifier.padding(top = 16.dp).fillMaxWidth().lcd(RoundedCornerShape(16.dp))
                    .border(2.dp, K.lcdBorder, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                BasicText("${name.uppercase()} TINGLADI", style = T.pixel(12.sp, K.lcdInk.copy(alpha = 0.65f), lh = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText("${seconds / 60}", style = T.pixel(46.sp, K.lcdInk, lh = 48.sp).copy(shadow = lcdShadow()))
                    BasicText(" DAQIQA", Modifier.padding(bottom = 8.dp), style = T.pixel(16.sp, K.lcdInk, lh = 16.sp))
                }
                BasicText("${songs.size} TA QO'SHIQ · ${artists.size} TA IJROCHI", style = T.pixel(12.sp, K.lcdInk, lh = 13.sp))
            }

            if (topSong == null) {
                BasicText(
                    "Bu oyda hali hech narsa tinglanmadi. Radioni yoqing — xulosa shu yerda paydo bo'ladi.",
                    Modifier.padding(top = 16.dp), style = T.pixel(13.sp, K.dimText, lh = 17.sp),
                )
            } else {
                BasicText("ENG SEVIMLI IJROCHI", Modifier.padding(top = 16.dp), style = T.micro(10.sp, K.dimText))
                BasicText(
                    artists.first().key.uppercase(), Modifier.padding(top = 4.dp),
                    style = T.pixel(24.sp, Color(0xFFD4E2A6), weight = FontWeight.SemiBold, lh = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )

                BasicText("№1 QO'SHIQ", Modifier.padding(top = 14.dp), style = T.micro(10.sp, K.dimText))
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(topSong.coverArt, 56.dp, 10.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        BasicText(topSong.title, style = T.pixel(16.sp, Color.White, weight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(topSong.artist, style = T.pixel(12.sp, K.dimText), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText("${(songs.first().value / 60)} DAQIQA", Modifier.padding(top = 2.dp), style = T.micro(10.sp, Color(0xFFFF8A95)))
                    }
                }

                if (songs.size > 1) {
                    BasicText("TOP 5", Modifier.padding(top = 14.dp), style = T.micro(10.sp, K.dimText))
                    Column(Modifier.padding(top = 4.dp)) {
                        songs.take(5).forEachIndexed { i, (id, s) ->
                            val t = byId[id] ?: return@forEachIndexed
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText("${i + 1}", Modifier.width(18.dp), style = T.pixel(13.sp, Color(0xFFD4E2A6)))
                                BasicText(t.title, Modifier.weight(1f), style = T.pixel(13.sp, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText("${s / 60}'", style = T.pixel(12.sp, K.dimText))
                            }
                        }
                    }
                }
            }

            Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(Color(1f, 1f, 1f, 0.08f)))
            BasicText("KAWAII RADIO · KR-01 · J-POP FM", Modifier.padding(top = 10.dp), style = T.pixel(10.sp, K.dimText, ls = 0.12.em))
        }

        PixelKey("Ulashish", Modifier.padding(top = 16.dp).fillMaxWidth(), height = 46.dp) {
            scope.launch {
                val img = layer.toImageBitmap()
                store.platform.shareImage(img, "Mening ${monthTitle(month).lowercase()} oyim Kawaii Radio'da: ${seconds / 60} daqiqa J-Pop ♪")
            }
        }
    }
}
