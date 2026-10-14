package uz.kawaii.radio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.kawaii.radio.Lang
import uz.kawaii.radio.data.checkForUpdate
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Led
import uz.kawaii.radio.ui.kit.Logo
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
    val liked by store.liked.collectAsState()
    val saved by store.saved.collectAsState()
    val lang by store.lang.collectAsState()
    var check by remember { mutableStateOf(Check.Idle) }
    val runCheck = {
        if (check != Check.Checking) {
            sound.press()
            check = Check.Checking
            scope.launch {
                check = try {
                    val u = checkForUpdate(store.platform)
                    if (u != null) { ui.update = u; Check.Idle } else Check.Latest
                } catch (_: Throwable) {
                    Check.Error
                }
            }
        }
    }
    val ls = lcdShadow()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).raised(CircleShape), contentAlignment = Alignment.Center) {
                Logo(Color(0xFF6F7177).copy(alpha = 0.9f), Modifier.size(40.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                BasicText("Mehmon", style = T.inter(22.sp, FontWeight.Bold, K.ink, lh = 22.sp))
                Spacer(Modifier.height(6.dp))
                BasicText("Kawaii Radio tinglovchisi", style = T.pixel(14.sp, K.inkSoft))
            }
        }

        // Kawaii Pass status on a small LCD
        Box(Modifier.padding(top = 20.dp).fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(5.dp)) {
            Column(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(14.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("KAWAII PASS", style = T.pixel(15.sp, K.lcdInk, lh = 15.sp).copy(shadow = ls))
                    BasicText(
                        if (store.premium) "FAOL ●" else "YO'Q ○",
                        style = if (store.premium) T.pixel(15.sp, K.lcdInk, lh = 15.sp).copy(shadow = ls) else T.pixel(15.sp, K.lcdGhost, lh = 15.sp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(liked.size.toString().padStart(2, '0'), "SEVIMLI", Modifier.weight(1f))
                    Stat(saved.size.toString().padStart(2, '0'), "SAQLANGAN", Modifier.weight(1f))
                    Stat(if (lang == Lang.JP) "JP" else "UZ", "MATN", Modifier.weight(1f))
                }
            }
        }
        if (!store.premium) GoldButton("KAWAII PASS OLISH", Modifier.padding(top = 12.dp), 16, 14) { ui.premium = true }

        Section("SOZLAMALAR")
        Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp)) {
            SettingRow("Qo'shiq matni tili", "Matnli qo'shiqlar uchun") {
                Switch2(lang == Lang.JP, "O'ZB", "日本") { jp -> store.setLang(if (jp) Lang.JP else Lang.UZ) }
            }
        }

        Section("AKKAUNT")
        Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp)) {
            SettingRow("Kawaii.uz akkaunti", "kawaii.uz saytidagi akkauntingizga kiring") {
                SmallKey(onClick = { store.platform.openUrl("https://kawaii.uz/login") }) {
                    BasicText("KIRISH", style = T.inter(12.sp, FontWeight.Bold, K.ctlInk))
                }
            }
            GrooveH()
            SettingRow("Ilova haqida", "Kawaii Radio KR-01") {
                BasicText("v${store.platform.appVersion}", style = T.pixel(15.sp, K.ink))
            }
            GrooveH()
            SettingRow("Yangilanishlar", check.text) {
                SmallKey(onClick = runCheck, sound = false) {
                    Led(check != Check.Idle, if (check == Check.Error) K.ledRed else K.ledGreen, if (check == Check.Checking) 1.6f else 1f)
                    BasicText(if (check == Check.Checking) "KUTING" else "TEKSHIRISH", style = T.inter(12.sp, FontWeight.Bold, K.ctlInk))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        BasicText(value, style = T.pixel(24.sp, K.lcdInk, lh = 24.sp).copy(shadow = lcdShadow()))
        Spacer(Modifier.height(4.dp))
        BasicText(label, style = T.pixel(13.sp, K.lcdInk.copy(alpha = 0.6f), lh = 13.sp))
    }
}

@Composable
private fun Section(title: String) {
    BasicText(title, Modifier.padding(top = 20.dp, bottom = 8.dp), style = T.inter(12.sp, FontWeight.Bold, K.inkSoft, ls = 1.44.sp))
}

@Composable
private fun SettingRow(title: String, sub: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(title, style = T.inter(15.sp, FontWeight.Bold, K.ink))
            if (sub != null) BasicText(sub, Modifier.padding(top = 2.dp), style = T.inter(12.5.sp, color = K.inkSoft))
        }
        trailing()
    }
}

@Composable
private fun SmallKey(onClick: () -> Unit, sound: Boolean = true, content: @Composable () -> Unit) {
    val m = Modifier.raised(RoundedCornerShape(12.dp))
    Row(
        (if (sound) m.pressClick(onClick = onClick) else m.tap(onClick = onClick)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Two-position hardware switch. */
@Composable
private fun Switch2(right: Boolean, left: String, rightLabel: String, onChange: (Boolean) -> Unit) {
    val sound = LocalSound.current
    val pos by animateFloatAsState(if (right) 1f else 0f, motionSpring(600f, 34f))
    BoxWithConstraints(Modifier.width(132.dp).tray(RoundedCornerShape(14.dp)).padding(4.dp)) {
        val half = maxWidth / 2
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset { IntOffset((half.toPx() * pos).toInt(), 0) }.width(half).fillMaxHeight().raised(RoundedCornerShape(10.dp)))
        }
        Row {
            for ((v, label) in listOf(false to left, true to rightLabel)) {
                Box(
                    Modifier.weight(1f).height(34.dp).tap(onClick = { if (v != right) { sound.press(); onChange(v) } }),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = T.inter(12.sp, FontWeight.Bold, if (v == right) K.ctlInk else K.inkSoft.copy(alpha = 0.7f)))
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
    ) { BasicText(text, style = T.inter(13.sp, FontWeight.Bold, Color(0xFF3A2A05), ls = 1.sp)) }
}
