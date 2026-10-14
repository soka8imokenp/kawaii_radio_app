package uz.kawaii.radio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kawaii.radio.Lang
import uz.kawaii.radio.ThemeMode
import uz.kawaii.radio.data.checkForUpdate
import uz.kawaii.radio.data.formatListening
import uz.kawaii.radio.data.mainArtist
import uz.kawaii.radio.nowMs
import uz.kawaii.radio.ui.kit.AvatarView
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.pressClick
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lcdShadow
import uz.kawaii.radio.ui.theme.motionSpring

private enum class Check(val text: String) {
    Idle("Yangi versiya bormi, tekshiring"),
    Checking("Tekshirilmoqda…"),
    Latest("Sizda eng so'nggi versiya"),
    Error("Tekshirib bo'lmadi, internetni tekshiring"),
}

@Composable
fun Profile(ui: UiState) {
    val store = LocalStore.current
    val sound = LocalSound.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val tracks by store.tracks.collectAsState()
    val liked by store.liked.collectAsState()
    val saved by store.saved.collectAsState()
    val lang by store.lang.collectAsState()
    val theme by store.theme.collectAsState()
    val stats by store.stats.collectAsState()
    val recent by store.recent.collectAsState()
    val profile by store.profile.collectAsState()
    val sleepAt by store.sleepAt.collectAsState()
    var check by remember { mutableStateOf(Check.Idle) }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val ls = lcdShadow()

    val listened = stats.values.sumOf { it.seconds }
    val plays = stats.values.sumOf { it.plays }
    val top = stats.entries.filter { byId[it.key] != null && it.value.seconds > 0 }.sortedByDescending { it.value.seconds }.take(5)
    val topArtist = stats.entries.mapNotNull { e -> byId[e.key]?.let { mainArtist(it.artist) to e.value.seconds } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.maxByOrNull { it.value }?.key

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
        // ── who: avatar (tap to change) + editable name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).raised(CircleShape).tap(onClick = { sound.press(); ui.avatarPicker = true }).padding(6.dp)) {
                AvatarView(profile.avatar, store.platform.avatarPhoto(), 72.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(24.dp).raised(CircleShape),
                    contentAlignment = Alignment.Center,
                ) { BasicText("✎", style = T.pixel(12.sp, K.ctlInk)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                var name by remember(profile.name) { mutableStateOf(profile.name) }
                Box {
                    if (name.isEmpty()) BasicText("MEHMON", style = T.pixel(24.sp, K.ink.copy(alpha = 0.35f), weight = FontWeight.SemiBold, lh = 26.sp))
                    BasicTextField(
                        name, { if (it.length <= 18) name = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = T.pixel(24.sp, K.ink, weight = FontWeight.SemiBold, lh = 26.sp),
                        cursorBrush = SolidColor(K.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); store.updateProfile { it.copy(name = name.trim()) } }),
                    )
                }
                LaunchedEffect(name) { delay(600); if (name.trim() != profile.name) store.updateProfile { it.copy(name = name.trim()) } }
                Spacer(Modifier.height(4.dp))
                BasicText("Ismni bosib o'zgartiring", style = T.pixel(12.sp, K.inkSoft))
            }
        }

        // ── favourite artist on its own little LCD
        SectionLabel("Sevimli ijrochi")
        Box(Modifier.fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(5.dp)) {
            Row(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(14.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(14.dp))
                    .tap(onClick = { sound.press(); ui.artistPicker = true })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        (profile.favArtist ?: "TANLANMAGAN").uppercase(),
                        style = T.pixel(20.sp, if (profile.favArtist != null) K.lcdInk else K.lcdGhost, lh = 22.sp).copy(shadow = ls),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val n = profile.favArtist?.let { a -> tracks.count { mainArtist(it.artist).equals(a, true) || it.artist.contains(a, true) } } ?: 0
                    BasicText(
                        if (profile.favArtist == null) "BOSING VA TANLANG" else if (n > 0) "EFIRDA $n TA QO'SHIQ" else "HOZIRCHA EFIRDA YO'Q",
                        Modifier.padding(top = 4.dp), style = T.pixel(12.sp, K.lcdInk.copy(alpha = 0.6f)),
                    )
                }
                val fav = profile.favArtist
                val songs = fav?.let { a -> tracks.filter { mainArtist(it.artist).equals(a, true) || it.artist.contains(a, true) }.map { it.id } }.orEmpty()
                if (songs.isNotEmpty()) {
                    Box(
                        Modifier.size(44.dp).raised(CircleShape).pressClick(onClick = { store.playAll(songs, shuffle = true) }),
                        contentAlignment = Alignment.Center,
                    ) { Px(Glyph.PLAY, 13.dp, K.ctlInk, Modifier.offset(x = 2.dp)) }
                }
            }
        }

        // ── listening stats
        SectionLabel("Statistika")
        Box(Modifier.fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(5.dp)) {
            Column(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(14.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("KAWAII PASS", style = T.pixel(14.sp, K.lcdInk, lh = 14.sp).copy(shadow = ls))
                    BasicText(if (store.premium) "FAOL ●" else "YO'Q ○", style = T.pixel(14.sp, if (store.premium) K.lcdInk else K.lcdGhost, lh = 14.sp))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat((listened / 60).toString(), "DAQIQA", Modifier.weight(1f))
                    Stat(plays.toString().padStart(2, '0'), "TINGLASH", Modifier.weight(1f))
                    Stat(liked.size.toString().padStart(2, '0'), "SEVIMLI", Modifier.weight(1f))
                    Stat(saved.size.toString().padStart(2, '0'), "SAQLANGAN", Modifier.weight(1f))
                }
                if (topArtist != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(K.lcdInk.copy(alpha = 0.2f)))
                    Spacer(Modifier.height(8.dp))
                    BasicText("KO'P TINGLAGANINGIZ: ${topArtist.uppercase()}", style = T.pixel(12.sp, K.lcdInk, lh = 14.sp).copy(shadow = ls), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!store.premium) GoldButton("KAWAII PASS OLISH", Modifier.padding(top = 12.dp), 16, 14) { ui.premium = true }

        if (top.isNotEmpty()) {
            SectionLabel("Eng ko'p tinglangan")
            Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                top.forEachIndexed { i, (id, st) ->
                    val t = byId[id] ?: return@forEachIndexed
                    if (i > 0) GrooveH()
                    Row(
                        Modifier.fillMaxWidth().tap(onClick = { sound.press(); ui.song = id }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BasicText("${i + 1}", Modifier.width(18.dp), style = T.pixel(18.sp, K.inkSoft, lh = 18.sp))
                        Cover(t.coverArt, 38.dp, 7.dp)
                        Column(Modifier.weight(1f)) {
                            BasicText(t.title, style = T.pixel(14.sp, K.ink, weight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(t.artist, style = T.pixel(12.sp, K.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        BasicText(formatListening(st.seconds).uppercase(), style = T.micro(10.sp, K.inkSoft))
                    }
                }
            }
        }

        val recentTracks = recent.mapNotNull { byId[it] }.take(12)
        if (recentTracks.isNotEmpty()) {
            SectionLabel("Yaqinda tinglangan")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (t in recentTracks) {
                    Column(Modifier.width(84.dp).tap(onClick = { sound.press(); store.play(t.id, recentTracks.map { it.id }) })) {
                        Box(Modifier.tray(RoundedCornerShape(14.dp)).padding(4.dp)) { Cover(t.coverArt, 76.dp, 10.dp) }
                        BasicText(t.title, Modifier.padding(top = 6.dp), style = T.pixel(12.sp, K.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(mainArtist(t.artist), style = T.pixel(11.sp, K.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // ── settings
        SectionLabel("Sozlamalar")
        Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp)) {
            SettingRow("Mavzu", "Yorug', tungi yoki telefonga qarab") {
                Switch3(theme.ordinal, listOf("AVTO", "KUN", "TUN")) { store.setTheme(ThemeMode.entries[it]) }
            }
            GrooveH()
            SettingRow("Qo'shiq matni", "Matnli qo'shiqlar uchun til") {
                Switch3(if (lang == Lang.JP) 1 else 0, listOf("O'ZB", "日本")) { store.setLang(if (it == 1) Lang.JP else Lang.UZ) }
            }
            GrooveH()
            // ticks while the timer runs so the countdown stays current
            val now by produceState(nowMs(), sleepAt) { while (sleepAt != null) { delay(15_000); value = nowMs() } }
            val left = sleepAt?.let { ((it - now) / 60_000).coerceAtLeast(0) + 1 }
            SettingRow("Uyqu taymeri", if (left != null) "Musiqa $left daqiqadan keyin to'xtaydi" else "Musiqa o'zi sekin to'xtaydi") {
                Led(sleepAt != null, K.gold)
            }
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(15, 30, 60)) Chip("$m daq", false) { store.setSleep(m) }
                if (sleepAt != null) Chip("O'chirish", true) { store.setSleep(null) }
            }
        }

        SectionLabel("Akkaunt")
        Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp)) {
            SettingRow("Kawaii.uz akkaunti", "kawaii.uz saytidagi akkauntingizga kiring") {
                SmallKey(onClick = { store.platform.openUrl("https://kawaii.uz/login") }) { BasicText("KIRISH", style = T.micro(11.sp)) }
            }
            GrooveH()
            SettingRow("Ilova haqida", "Kawaii Radio KR-01") {
                BasicText("v${store.platform.appVersion}", style = T.pixel(15.sp, K.ink))
            }
            GrooveH()
            SettingRow("Yangilanishlar", check.text) {
                SmallKey(onClick = {
                    if (check != Check.Checking) {
                        sound.press()
                        check = Check.Checking
                        scope.launch {
                            check = try {
                                val u = checkForUpdate(store.platform)
                                if (u != null) { ui.update = u; Check.Idle } else Check.Latest
                            } catch (_: Throwable) { Check.Error }
                        }
                    }
                }, sound = false) {
                    Led(check != Check.Idle, if (check == Check.Error) K.ledRed else K.ledGreen, if (check == Check.Checking) 1.6f else 1f)
                    BasicText(if (check == Check.Checking) "KUTING" else "TEKSHIRISH", style = T.micro(11.sp))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        BasicText(value, style = T.pixel(22.sp, K.lcdInk, lh = 22.sp).copy(shadow = lcdShadow()), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        BasicText(label, style = T.pixel(10.sp, K.lcdInk.copy(alpha = 0.6f), lh = 11.sp), maxLines = 1)
    }
}

@Composable
private fun SettingRow(title: String, sub: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(title, style = T.pixel(15.sp, K.ink, weight = FontWeight.SemiBold))
            if (sub != null) BasicText(sub, Modifier.padding(top = 2.dp), style = T.pixel(12.sp, K.inkSoft, lh = 15.sp))
        }
        trailing()
    }
}

@Composable
private fun SmallKey(onClick: () -> Unit, sound: Boolean = true, content: @Composable () -> Unit) {
    val m = Modifier.raised(RoundedCornerShape(12.dp))
    Row(
        (if (sound) m.pressClick(onClick = onClick) else m.tap(onClick = onClick)).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Multi-position hardware slide switch. */
@Composable
private fun Switch3(selected: Int, labels: List<String>, onChange: (Int) -> Unit) {
    val sound = LocalSound.current
    val pos by animateFloatAsState(selected.toFloat(), motionSpring(600f, 34f))
    BoxWithConstraints(Modifier.width((labels.size * 52).dp).tray(RoundedCornerShape(14.dp)).padding(4.dp)) {
        val cell = maxWidth / labels.size
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset { IntOffset((cell.toPx() * pos).toInt(), 0) }.width(cell).fillMaxHeight().raised(RoundedCornerShape(10.dp)))
        }
        Row {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(32.dp).tap(onClick = { if (i != selected) { sound.press(); onChange(i) } }),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = T.micro(11.sp, if (i == selected) K.ctlInk else K.inkSoft.copy(alpha = 0.7f), ls = 0.04.em))
                }
            }
        }
    }
}

/** Gold Kawaii Pass button. */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, radius: Int = 18, vertical: Int = 16, onClick: () -> Unit) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier.fillMaxWidth()
            .dropShadow(shape, Shadow(14.dp, Color(245, 185, 59, 77), offset = DpOffset(0.dp, 6.dp)))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFE08A), K.gold)), shape)
            .innerShadow(shape, Shadow(0.dp, Color(160, 100, 10, 64), offset = DpOffset(0.dp, (-2).dp)))
            .innerShadow(shape, Shadow(0.dp, Color(1f, 1f, 1f, 0.7f), offset = DpOffset(0.dp, 1.dp)))
            .pressClick(onClick = onClick)
            .padding(vertical = vertical.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = T.pixel(15.sp, Color(0xFF3A2A05), weight = FontWeight.SemiBold, ls = 0.08.em)) }
}
