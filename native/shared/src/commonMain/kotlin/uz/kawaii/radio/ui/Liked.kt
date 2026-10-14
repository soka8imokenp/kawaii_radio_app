package uz.kawaii.radio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.HeartIcon
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.pressClick
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.motionSpring

/** Liked songs: each row is a key cap; tap plays it, heart removes it. */
@Composable
fun Liked(onOpenRadio: () -> Unit) {
    val store = LocalStore.current
    val tracks by store.tracks.collectAsState()
    val liked by store.liked.collectAsState()
    val engine by store.engine.collectAsState()
    val list = tracks.filter { it.id in liked }

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText("Sevimlilar", style = T.inter(22.sp, FontWeight.Bold, K.ink, lh = 22.sp))
                Spacer(Modifier.height(6.dp))
                BasicText("Yoqtirgan qo'shiqlaringiz", style = T.pixel(14.sp, K.inkSoft))
            }
            Plate {
                Led(list.isNotEmpty(), K.heart)
                BasicText(list.size.toString().padStart(2, '0'), style = T.inter(14.sp, FontWeight.ExtraBold, K.ink, ls = 0.1.sp))
            }
        }
        GrooveH()

        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).raised(CircleShape), contentAlignment = Alignment.Center) { HeartIcon(false, K.ctlInk) }
                Spacer(Modifier.height(16.dp))
                BasicText("Hali sevimli qo'shiq yo'q", style = T.inter(16.sp, FontWeight.Bold, K.ink))
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "Radioda yurakcha tugmasini bosing — qo'shiq shu yerda paydo bo'ladi.",
                    Modifier.widthIn(max = 240.dp),
                    style = T.inter(13.sp, color = K.inkSoft).copy(textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.raised(RoundedCornerShape(14.dp)).pressClick(onClick = onOpenRadio).padding(horizontal = 20.dp, vertical = 10.dp),
                ) { BasicText("RADIOGA O'TISH", style = T.inter(13.sp, FontWeight.Bold, K.ctlInk)) }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { t ->
                    val current = t.id == engine.id
                    Row(
                        Modifier.animateItem().fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f).raised(RoundedCornerShape(12.dp), current)
                                .pressClick(onClick = { store.select(t.id) }).padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                t.coverArt, null,
                                Modifier.size(48.dp).dropShadow(RoundedCornerShape(9.dp), Shadow(0.dp, K.shade, spread = 2.dp)).clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f)) {
                                BasicText(t.title, style = T.inter(15.sp, FontWeight.Bold, K.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(t.artist, style = T.pixel(13.sp, K.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Led(current && engine.playing)
                        }
                        Box(
                            Modifier.width(48.dp).height(60.dp).raised(RoundedCornerShape(12.dp))
                                .pressClick(onClick = { store.toggleLike(t.id) }),
                            contentAlignment = Alignment.Center,
                        ) { HeartIcon(true, K.ctlInk) }
                    }
                }
            }
        }
    }
}

/** Flat silkscreened label plate. */
@Composable
fun Plate(content: @Composable () -> Unit) {
    Row(
        Modifier.tray(RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
