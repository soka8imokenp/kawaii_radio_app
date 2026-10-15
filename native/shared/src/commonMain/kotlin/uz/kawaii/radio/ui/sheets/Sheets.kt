package uz.kawaii.radio.ui.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kawaii.radio.data.POPULAR_ARTISTS
import uz.kawaii.radio.data.Track
import uz.kawaii.radio.data.formatDuration
import uz.kawaii.radio.data.formatListening
import uz.kawaii.radio.data.formatWhen
import uz.kawaii.radio.data.mainArtist
import uz.kawaii.radio.ui.Chip
import uz.kawaii.radio.ui.GoldButton
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.LocalUi
import uz.kawaii.radio.ui.PixelKey
import uz.kawaii.radio.ui.SectionLabel
import uz.kawaii.radio.ui.kit.Avatars
import uz.kawaii.radio.ui.kit.AvatarView
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.HeartIcon
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.emboss
import uz.kawaii.radio.ui.theme.lcdShadow

/** Cover + title + artist + chips, shared by the song card and "Tavsif". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongHead(t: Track) {
    Row {
        Box(Modifier.tray(RoundedCornerShape(22.dp)).padding(5.dp)) {
            AsyncImage(t.coverArt, null, Modifier.size(112.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.padding(top = 4.dp)) {
            BasicText(t.title, style = T.pixel(22.sp, K.ink, weight = FontWeight.SemiBold, lh = 26.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(t.artist, Modifier.padding(top = 2.dp), style = T.pixel(14.sp, K.inkSoft, lh = 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (x in listOf(t.category, "${t.bpm} BPM", "Energiya ${t.energy}%")) {
                    BasicText(
                        x.uppercase(),
                        Modifier.tray(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = T.micro(10.sp, K.ink).copy(shadow = emboss()),
                    )
                }
            }
        }
    }
}

/** LCD readout line: label on the left, value on the right. */
@Composable
private fun LcdLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = T.pixel(13.sp, K.lcdInk.copy(alpha = 0.6f), lh = 15.sp))
        BasicText(value.uppercase(), style = T.pixel(14.sp, K.lcdInk, lh = 16.sp).copy(shadow = lcdShadow()))
    }
}

/**
 * Song card: when it was liked, how often and how long it was played, and the actions
 * (play, play next, like, description, all songs of the artist).
 */
@Composable
fun SongSheet(id: String?, onClose: () -> Unit) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val tracks by store.tracks.collectAsState()
    val likes by store.likes.collectAsState()
    val stats by store.stats.collectAsState()
    val engine by store.engine.collectAsState()
    var last by remember { mutableStateOf<String?>(null) }
    if (id != null) last = id
    val t = tracks.firstOrNull { it.id == last }
    var queued by remember(id) { mutableStateOf(false) }
    var exported by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Sheet(id != null, onClose, "Qo'shiq") {
        if (t == null) return@Sheet
        val liked = t.id in likes
        val st = stats[t.id]
        SongHead(t)

        Box(Modifier.padding(top = 18.dp).fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(5.dp)) {
            Column(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(14.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                LcdLine("SEVIMLIGA QO'SHILGAN", likes[t.id]?.let(::formatWhen) ?: "—")
                LcdLine("TINGLANGAN", if ((st?.plays ?: 0) > 0) "${st!!.plays} marta" else "hali yo'q")
                LcdLine("JAMI VAQT", formatListening(st?.seconds ?: 0))
                LcdLine("OXIRGI MARTA", st?.last?.takeIf { it > 0 }?.let(::formatWhen) ?: "—")
                LcdLine("DAVOMIYLIGI", if (t.duration > 0) formatDuration(t.duration) else "—")
            }
        }

        val current = t.id == engine.id
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelKey(if (current && engine.playing) "Pauza" else "Ijro etish", Modifier.weight(1f), glyph = Glyph.PLAY, glyphSize = 11.dp, height = 46.dp) {
                    if (current) store.toggle() else store.play(t.id)
                }
                PixelKey(if (queued) "Navbatda ✓" else "Keyingisi", Modifier.weight(1f), glyph = Glyph.QUEUE, glyphSize = 14.dp, height = 46.dp, active = queued) {
                    if (!queued && !current) { store.playNext(t.id); queued = true }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelKey(if (liked) "Sevimlida" else "Sevimli", Modifier.weight(1f), glyph = if (liked) Glyph.HEART_FULL else Glyph.HEART, glyphSize = 16.dp, height = 46.dp, active = liked) { store.toggleLike(t.id) }
                PixelKey("Tavsif", Modifier.weight(1f), glyph = Glyph.INFO, glyphSize = 15.dp, height = 46.dp) { ui.info = t.id }
            }
            val saved by store.saved.collectAsState()
            val downloads by store.downloads.collectAsState()
            val exporting by store.exporting.collectAsState()
            val p = downloads[t.id]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelKey(
                    when { p != null -> "Saqlanmoqda ${(p * 100).toInt()}%"; t.id in saved -> "Ilovada ✓"; else -> "Saqlash" },
                    Modifier.weight(1f), glyph = if (t.id in saved) Glyph.CHECK else Glyph.DOWNLOAD, glyphSize = 13.dp, height = 46.dp,
                    active = t.id in saved || p != null,
                ) { if (store.premium) store.toggleSave(t.id) else ui.premium = true }
                PixelKey(
                    if (t.id in exporting) "Yuklanmoqda…" else "Telefonga", Modifier.weight(1f),
                    glyph = Glyph.DOWNLOAD, glyphSize = 13.dp, height = 46.dp, active = t.id in exporting,
                ) {
                    if (!store.premium) ui.premium = true
                    else if (t.id !in exporting) scope.launch {
                        exported = store.exportToPhone(t.id)?.let { "Saqlandi: $it" } ?: "Saqlab bo'lmadi, internetni tekshiring"
                    }
                }
            }
            exported?.let { BasicText(it, Modifier.fillMaxWidth(), style = T.pixel(12.sp, K.inkSoft, lh = 15.sp)) }
            val artist = mainArtist(t.artist)
            val songs = tracks.filter { mainArtist(it.artist).equals(artist, true) }.map { it.id }
            if (songs.size > 1) {
                PixelKey("$artist · ${songs.size} ta qo'shiq", Modifier.fillMaxWidth(), glyph = Glyph.SHUFFLE, glyphSize = 15.dp, height = 46.dp) {
                    store.playAll(songs, shuffle = true)
                    onClose()
                }
            }
        }
    }
}

/** "Tavsif": description of the song and its details. */
@Composable
fun InfoSheet(id: String?, onClose: () -> Unit) {
    val store = LocalStore.current
    val tracks by store.tracks.collectAsState()
    val liked by store.liked.collectAsState()
    var last by remember { mutableStateOf<String?>(null) }
    if (id != null) last = id
    val t = tracks.firstOrNull { it.id == last }
    Sheet(id != null, onClose, "Qo'shiq haqida") {
        if (t == null) return@Sheet
        val isLiked = t.id in liked
        SongHead(t)
        val info = t.info
        if (info == null) {
            BasicText(
                "Bu qo'shiq uchun tavsif hali yo'q",
                Modifier.padding(top = 24.dp).fillMaxWidth(),
                style = T.pixel(14.sp, K.inkSoft).copy(textAlign = TextAlign.Center),
            )
        } else {
            Box(
                Modifier.padding(top = 20.dp).fillMaxWidth().lcd(RoundedCornerShape(18.dp))
                    .border(3.dp, K.lcdBorder, RoundedCornerShape(18.dp)).padding(16.dp),
            ) {
                BasicText(info.description, style = T.lcd(21.sp, lh = 24.sp).copy(shadow = lcdShadow()))
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (d in info.details) {
                    Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(16.dp)) {
                        BasicText(d.title.uppercase(), style = T.micro(11.sp, K.accent, ls = 0.1.sp))
                        BasicText(d.text, Modifier.padding(top = 6.dp), style = T.pixel(14.sp, K.ink, lh = 19.sp))
                    }
                }
            }
        }
        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth().raised(RoundedCornerShape(18.dp), isLiked)
                .tap(onClick = { store.toggleLike(t.id) }).padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeartIcon(isLiked, K.ctlInk)
            BasicText(if (isLiked) "SEVIMLILARDA" else "SEVIMLILARGA QO'SHISH", style = T.micro(12.sp).copy(shadow = emboss()))
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
            Modifier.fillMaxWidth().lcd(RoundedCornerShape(18.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(18.dp)).padding(16.dp),
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
                        BasicText(title, style = T.pixel(15.sp, K.ink, weight = FontWeight.SemiBold))
                        BasicText(text, Modifier.padding(top = 2.dp), style = T.pixel(13.sp, K.inkSoft, lh = 17.sp))
                    }
                }
            }
        }
        // temporary: there is no payment yet, so subscribing switches Kawaii Pass on right away
        GoldButton("OBUNA BO'LISH", Modifier.padding(top = 20.dp)) { store.activatePass(); onClose() }
    }
}

/** Avatar picker: eight pixel avatars or a photo from the gallery. */
@Composable
fun AvatarSheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val sound = LocalSound.current
    val scope = rememberCoroutineScope()
    val profile by store.profile.collectAsState()
    var photo by remember { mutableStateOf(store.platform.avatarPhoto()) }
    Sheet(open, onClose, "Avatar") {
        BasicText("Pikselli avatarlardan birini tanlang yoki galereyadan rasm qo'ying.", style = T.pixel(13.sp, K.inkSoft, lh = 17.sp))
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatars.all.indices.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in row) {
                        val key = "p:$i"
                        Box(
                            Modifier.size(72.dp).raised(CircleShape, profile.avatar == key)
                                .tap(onClick = { sound.press(); store.updateProfile { it.copy(avatar = key) } })
                                .padding(6.dp),
                        ) { AvatarView(key, null, 60.dp) }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                Box(
                    Modifier.size(56.dp).raised(CircleShape, profile.avatar == "photo")
                        .tap(onClick = { sound.press(); store.updateProfile { it.copy(avatar = "photo") } }).padding(4.dp),
                ) { AvatarView("photo", photo, 48.dp) }
            }
            PixelKey("Galereyadan", Modifier.weight(1f), height = 46.dp) {
                scope.launch {
                    val p = store.platform.pickAvatarPhoto() ?: return@launch
                    photo = p
                    store.updateProfile { it.copy(avatar = "photo") }
                }
            }
        }
    }
}

/** Favourite artist: artists from the radio first (with song counts), then popular names, or type one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistSheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val tracks by store.tracks.collectAsState()
    val profile by store.profile.collectAsState()
    var custom by remember { mutableStateOf("") }
    val onAir = remember(tracks) {
        tracks.groupingBy { mainArtist(it.artist) }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val inRadio = onAir.map { it.first.lowercase() }.toSet()
    val pick = { name: String -> store.updateProfile { it.copy(favArtist = name) }; onClose() }

    Sheet(open, onClose, "Sevimli ijrochi") {
        // type your own on an LCD strip
        Box(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(5.dp)) {
            Row(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(13.dp)).border(2.dp, K.lcdBorder, RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (custom.isEmpty()) BasicText("O'ZINGIZ YOZING", style = T.pixel(16.sp, K.lcdInk.copy(alpha = 0.4f), lh = 18.sp))
                    BasicTextField(
                        custom, { if (it.length <= 32) custom = it }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = T.pixel(16.sp, K.lcdInk, lh = 18.sp), cursorBrush = SolidColor(K.lcdInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) pick(custom.trim()) }),
                    )
                }
                if (custom.isNotBlank()) BasicText("OK", Modifier.tap(onClick = { pick(custom.trim()) }).padding(4.dp), style = T.pixel(15.sp, K.lcdInk))
            }
        }
        SectionLabel("Radioda bor")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, n) in onAir.take(30)) Chip("$name · $n", profile.favArtist.equals(name, true)) { pick(name) }
        }
        SectionLabel("Mashhur J-pop")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (name in POPULAR_ARTISTS.filter { it.lowercase() !in inRadio }) Chip(name, profile.favArtist.equals(name, true)) { pick(name) }
        }
        if (profile.favArtist != null) {
            Spacer(Modifier.height(16.dp))
            PixelKey("Tozalash", Modifier.fillMaxWidth()) { store.updateProfile { it.copy(favArtist = null) }; onClose() }
        }
    }
}

/** Kawaii Pass holder tried to pick, skip or seek while on the broadcast: leave it? */
@Composable
fun LeaveLiveSheet(open: Boolean, onAnswer: (leave: Boolean) -> Unit) {
    Sheet(open, { onAnswer(false) }, "LIVE efir") {
        Column(
            Modifier.fillMaxWidth().lcd(RoundedCornerShape(18.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(18.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText("● LIVE", style = T.pixel(22.sp, K.lcdInk, lh = 22.sp).copy(shadow = lcdShadow()))
            BasicText(
                "Siz jonli efirdasiz. Efirdan chiqasizmi?", Modifier.padding(top = 8.dp),
                style = T.pixel(14.sp, K.lcdInk.copy(alpha = 0.8f)).copy(textAlign = TextAlign.Center),
            )
            BasicText(
                "Chiqsangiz, qo'shiqlarni o'zingiz tanlaysiz. Efirga LIVE tugmasi bilan qaytasiz.", Modifier.padding(top = 6.dp),
                style = T.pixel(12.sp, K.lcdInk.copy(alpha = 0.6f), lh = 16.sp).copy(textAlign = TextAlign.Center),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelKey("Efirda qolish", Modifier.weight(1f), height = 48.dp) { onAnswer(false) }
            PixelKey("Chiqish", Modifier.weight(1f), height = 48.dp) { onAnswer(true) }
        }
    }
}
