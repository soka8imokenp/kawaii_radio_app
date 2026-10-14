package uz.kawaii.radio.ui.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import uz.kawaii.radio.ui.GoldButton
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.kit.HeartIcon
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.pressClick
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.emboss
import uz.kawaii.radio.ui.theme.lcdShadow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoSheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val tracks by store.tracks.collectAsState()
    val engine by store.engine.collectAsState()
    val liked by store.liked.collectAsState()
    val t = store.current(tracks, engine.id)
    Sheet(open, onClose, "Qo'shiq haqida") {
        if (t == null) return@Sheet
        val isLiked = t.id in liked
        Row {
            Box(Modifier.tray(RoundedCornerShape(22.dp)).padding(5.dp)) {
                AsyncImage(t.coverArt, null, Modifier.size(112.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.padding(top = 4.dp)) {
                BasicText(t.title, style = T.inter(22.sp, FontWeight.Bold, K.ink, lh = 26.sp))
                BasicText(t.artist, style = T.inter(14.sp, color = K.inkSoft))
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (x in listOf(t.category, "${t.bpm} BPM", "Energiya ${t.energy}%")) {
                        BasicText(
                            x.uppercase(),
                            Modifier.tray(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                            style = T.inter(11.sp, FontWeight.Bold, K.ink, ls = 0.55.sp).copy(shadow = emboss()),
                        )
                    }
                }
            }
        }
        t.info?.let { info ->
            Box(
                Modifier.padding(top = 20.dp).fillMaxWidth().lcd(RoundedCornerShape(18.dp))
                    .border(3.dp, Color(0xFF3B4232), RoundedCornerShape(18.dp)).padding(16.dp),
            ) {
                BasicText(info.description, style = T.lcd(21.sp, lh = 24.sp).copy(shadow = lcdShadow()))
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (d in info.details) {
                    Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(16.dp)) {
                        BasicText(d.title.uppercase(), style = T.inter(12.sp, FontWeight.Bold, K.accent, ls = 1.44.sp))
                        BasicText(d.text, Modifier.padding(top = 4.dp), style = T.inter(14.sp, color = K.ink, lh = 19.sp))
                    }
                }
            }
        }
        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth().raised(RoundedCornerShape(18.dp), isLiked)
                .pressClick(onClick = { store.toggleLike(t.id) }).padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeartIcon(isLiked, K.ctlInk)
            BasicText(
                if (isLiked) "SEVIMLILARDA" else "SEVIMLILARGA QO'SHISH",
                style = T.inter(14.sp, FontWeight.Bold, K.ctlInk, ls = 1.7.sp).copy(shadow = emboss()),
            )
        }
    }
}

/** Kawaii Pass upsell shown when a free user taps "Saqlash". */
@Composable
fun PremiumSheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val perks = listOf(
        "Ilova ichida saqlash" to "Qo'shiqlar «Saqlangan» bo'limida doim qo'l ostingizda",
        "Internetsiz tinglash" to "Saqlangan qo'shiqlar oflayn ham ijro etiladi",
        "Reklamasiz efir" to "Hech narsa musiqangizni bo'lmaydi",
    )
    Sheet(open, onClose, "Kawaii Pass") {
        Column(
            Modifier.fillMaxWidth().lcd(RoundedCornerShape(18.dp)).border(3.dp, Color(0xFF3B4232), RoundedCornerShape(18.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText("KAWAII PASS", style = T.pixel(22.sp, K.lcdInk, lh = 22.sp).copy(shadow = lcdShadow()))
            BasicText(
                "Saqlash funksiyasi obunachilar uchun", Modifier.padding(top = 8.dp),
                style = T.pixel(13.sp, K.lcdInk.copy(alpha = 0.7f)).copy(textAlign = TextAlign.Center),
            )
        }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            perks.forEachIndexed { i, (title, text) ->
                val a = remember { Animatable(0f) }
                LaunchedEffect(open) { if (open) { a.snapTo(0f); delay(50L + i * 60L); a.animateTo(1f, tween(250)) } }
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 10 * density }
                        .tray(RoundedCornerShape(18.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Led(true, K.gold, modifier = Modifier.padding(top = 4.dp))
                    Column {
                        BasicText(title, style = T.inter(14.sp, FontWeight.Bold, K.ink))
                        BasicText(text, Modifier.padding(top = 2.dp), style = T.inter(13.sp, color = K.inkSoft, lh = 17.sp))
                    }
                }
            }
        }
        GoldButton("OBUNA BO'LISH", Modifier.padding(top = 20.dp)) { store.platform.openUrl("https://kawaii.uz/kawaii-pass") }
        Spacer(Modifier.height(0.dp))
    }
}
