package uz.kawaii.radio.ui.sheets

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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uz.kawaii.radio.chat.AdminUser
import uz.kawaii.radio.chat.LogEntry
import uz.kawaii.radio.chat.Me
import uz.kawaii.radio.data.formatWhen
import uz.kawaii.radio.nowMs
import uz.kawaii.radio.ui.Chip
import uz.kawaii.radio.ui.LocalStore
import uz.kawaii.radio.ui.SectionLabel
import uz.kawaii.radio.ui.SmallKey
import uz.kawaii.radio.ui.Switch3
import uz.kawaii.radio.ui.kit.AvatarView
import uz.kawaii.radio.ui.kit.GrooveH
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lcdShadow

private enum class Who(val label: String, val filter: String) {
    ONLINE("Onlayn", "online"), ALL("Hammasi", ""), MUTED("Ovozsiz", "muted"), BANNED("Bloklangan", "banned"),
}

private val SLOW = listOf(0, 10, 30, 60)

/**
 * The admins' control desk: room numbers on an LCD, chat switches (slow mode, closed chat,
 * pinned message, wipe), and every listener with block / mute / wipe keys.
 */
@Composable
fun AdminSheet(open: Boolean, onClose: () -> Unit) {
    val store = LocalStore.current
    val chat = store.chat
    val sound = LocalSound.current
    val me by chat.me.collectAsState()
    val stats by chat.stats.collectAsState()
    val room by chat.room.collectAsState()
    val users by chat.users.collectAsState()
    var who by remember { mutableStateOf(Who.ONLINE) }
    var query by remember { mutableStateOf("") }
    var wipeArmed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var mine by remember { mutableStateOf(false) }
    val log by chat.log.collectAsState()
    LaunchedEffect(open, tab, mine) { if (open && tab == 1) chat.loadLog(mine) }

    // fresh numbers while the panel is open
    LaunchedEffect(open, who) {
        if (!open) return@LaunchedEffect
        chat.loadUsers(who.filter)
        while (true) { chat.loadStats(); delay(15_000) }
    }
    LaunchedEffect(wipeArmed) { if (wipeArmed) { delay(4_000); wipeArmed = false } }
    // rights taken away while the panel is open
    LaunchedEffect(me.admin) { if (open && !me.admin) onClose() }

    Sheet(open, onClose, if (me.owner) "Bosh admin" else "Admin panel") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Switch3(tab, listOf("BOSHQARUV", "JURNAL")) { tab = it }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 1) {
            Journal(log, mine) { mine = it }
            return@Sheet
        }
        // ── room on an LCD
        Box(Modifier.fillMaxWidth().tray(RoundedCornerShape(20.dp)).padding(5.dp)) {
            Column(
                Modifier.fillMaxWidth().lcd(RoundedCornerShape(14.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("KAWAII CHAT", style = T.pixel(14.sp, K.lcdInk, lh = 14.sp).copy(shadow = lcdShadow()))
                    BasicText(
                        when {
                            room.locked -> "YOPIQ ■"
                            room.slow > 0 -> "SEKIN ${room.slow}S"
                            else -> "OCHIQ ●"
                        },
                        style = T.pixel(14.sp, K.lcdInk, lh = 14.sp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                val s = stats
                Row(Modifier.fillMaxWidth()) {
                    LcdNum(Modifier.weight(1f), s?.online, "ONLAYN")
                    LcdNum(Modifier.weight(1f), s?.live, "RADIODA")
                    LcdNum(Modifier.weight(1f), s?.activeToday, "BUGUN")
                    LcdNum(Modifier.weight(1f), s?.users, "JAMI")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    LcdNum(Modifier.weight(1f), s?.messagesToday, "XABAR/KUN")
                    LcdNum(Modifier.weight(1f), s?.muted, "OVOZSIZ")
                    LcdNum(Modifier.weight(1f), s?.banned, "BLOKDA")
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── chat switches
        SectionLabel("Chat")
        Column(Modifier.fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp)) {
            AdminRow("Sekin rejim", if (room.slow == 0) "Har kim xohlagancha yozadi" else "Har ${room.slow} soniyada bitta xabar") {
                Switch3(SLOW.indexOf(room.slow).coerceAtLeast(0), listOf("YO'Q", "10S", "30S", "60S")) { chat.setSlow(SLOW[it]) }
            }
            GrooveH()
            AdminRow("Chatni yopish", if (room.locked) "Faqat adminlar yoza oladi" else "Hamma yoza oladi") {
                Switch3(if (room.locked) 1 else 0, listOf("OCHIQ", "YOPIQ")) { chat.setLocked(it == 1) }
            }
            GrooveH()
            val pin = room.pin
            AdminRow("Qadalgan xabar", pin?.let { "${it.name}: ${if (it.kind == "text") it.body else "stiker / qo'shiq"}" } ?: "Xabarni bosing → «Qadash»") {
                if (pin != null) SmallKey(onClick = { chat.pin(null) }) { BasicText("OLIB TASHLASH", style = T.micro(10.sp)) }
            }
            GrooveH()
            AdminRow("Chatni tozalash", if (wipeArmed) "Yana bir marta bosing: hamma xabarlar o'chadi!" else "Barcha xabarlar hamma uchun o'chiriladi") {
                SmallKey(onClick = { if (wipeArmed) { wipeArmed = false; chat.clearChat() } else wipeArmed = true }) {
                    BasicText(if (wipeArmed) "TASDIQLASH" else "TOZALASH", style = T.micro(11.sp, K.onAir))
                }
            }
        }

        // ── people
        SectionLabel("Tinglovchilar")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (w in Who.entries) Chip(w.label, who == w) { if (who != w) { sound.press(); who = w; expanded = null } }
        }
        Box(
            Modifier.padding(top = 10.dp).fillMaxWidth().lcd(RoundedCornerShape(12.dp)).border(2.dp, K.lcdBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (query.isEmpty()) BasicText("ISM BO'YICHA QIDIRISH", style = T.pixel(14.sp, K.lcdInk.copy(alpha = 0.4f), lh = 17.sp))
            BasicTextField(
                query, { query = it.take(32) }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = T.pixel(14.sp, K.lcdInk, lh = 17.sp), cursorBrush = SolidColor(K.lcdInk),
            )
        }
        val list = users
        val shown = list?.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        Column(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                shown == null -> Note("Yuklanmoqda…")
                shown.isEmpty() -> Note(if (who == Who.ONLINE) "Hozir hech kim onlayn emas" else "Hech kim topilmadi")
                else -> for (u in shown) UserRow(u, me, expanded == u.uid) { expanded = if (expanded == u.uid) null else u.uid }
            }
        }
    }
}

@Composable
private fun UserRow(u: AdminUser, me: Me, open: Boolean, onToggle: () -> Unit) {
    val chat = LocalStore.current.chat
    val sound = LocalSound.current
    val self = u.uid == me.uid
    // admins can't touch each other; only the owner can, and nobody touches the owner
    val touch = me.canTouch(u.uid, if (u.owner) 2 else if (u.admin) 1 else 0)
    Column(Modifier.fillMaxWidth().raised(RoundedCornerShape(16.dp), open)) {
        Row(
            Modifier.fillMaxWidth().tap(onClick = { sound.press(); onToggle() }).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AvatarView(u.avatar, null, 38.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        u.name + if (self) " (siz)" else "", Modifier.weight(1f, fill = false),
                        style = T.pixel(14.sp, K.ink, weight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (u.owner) Tag("BOSH ADMIN", K.gold) else if (u.admin) Tag("ADMIN", K.gold)
                    if (u.banned) Tag("BLOK", K.onAir)
                    if (u.mutedUntil > nowMs()) Tag("OVOZSIZ", K.inkSoft)
                }
                BasicText(
                    when {
                        u.live -> "● radioda · ${u.messages} xabar"
                        u.online -> "● onlayn · ${u.messages} xabar"
                        else -> "${formatWhen(u.seen)} · ${u.messages} xabar"
                    },
                    style = T.pixel(11.sp, if (u.online) K.ledGreen else K.inkSoft), maxLines = 1,
                )
            }
            BasicText(if (open) "▴" else "▾", style = T.pixel(14.sp, K.inkSoft))
        }
        if (!open) return@Column
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (me.owner && !self && !u.owner) {
                Act(if (u.admin) "ADMINLIKDAN OLISH" else "ADMIN QILISH", Modifier.fillMaxWidth(), K.gold) { chat.setAdmin(u.uid, !u.admin) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Act("PROFIL", Modifier.weight(1f)) { chat.whois(u.uid) }
                if (touch) {
                    if (u.banned) Act("BLOKDAN CHIQARISH", Modifier.weight(2f)) { chat.ban(u.uid, false) }
                    else Act("BLOKLASH", Modifier.weight(1f), K.onAir) { chat.ban(u.uid, true) }
                }
            }
            if (!touch && !self) Note(if (u.owner) "Bosh adminga tegib bo'lmaydi" else "Adminlar bir-birini bloklay olmaydi")
            if (touch) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (u.mutedUntil > nowMs()) Act("OVOZNI YOQISH", Modifier.weight(1f)) { chat.mute(u.uid, 0) }
                    else {
                        Act("OVOZSIZ 10D", Modifier.weight(1f)) { chat.mute(u.uid, 10) }
                        Act("1 SOAT", Modifier.weight(1f)) { chat.mute(u.uid, 60) }
                        Act("1 KUN", Modifier.weight(1f)) { chat.mute(u.uid, 24 * 60) }
                    }
                }
                if (u.messages > 0 || !u.banned) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (u.messages > 0) Act("XABARLARINI O'CHIRISH", Modifier.weight(1f), K.onAir) { chat.purge(u.uid) }
                    if (!u.banned) Act("BLOK + TOZALASH", Modifier.weight(1f), K.onAir) { chat.ban(u.uid, true, purge = true) }
                }
            }
        }
    }
}

@Composable
private fun Act(label: String, modifier: Modifier, color: Color = K.ctlInk, onClick: () -> Unit) {
    val sound = LocalSound.current
    Box(
        modifier.height(38.dp).tray(RoundedCornerShape(12.dp)).tap(onClick = { sound.press(); onClick() }),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = T.micro(10.sp, color), maxLines = 1) }
}

@Composable
private fun Tag(text: String, color: Color) {
    BasicText(
        text, Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
        style = T.pixel(9.sp, color, lh = 10.sp),
    )
}

@Composable
private fun LcdNum(modifier: Modifier, value: Int?, label: String) {
    Column(modifier) {
        BasicText(value?.toString() ?: "--", style = T.pixel(20.sp, K.lcdInk, lh = 20.sp).copy(shadow = lcdShadow()), maxLines = 1)
        Spacer(Modifier.height(3.dp))
        BasicText(label, style = T.pixel(9.sp, K.lcdInk.copy(alpha = 0.6f), lh = 10.sp), maxLines = 1)
    }
}

@Composable
private fun AdminRow(title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            BasicText(title, style = T.pixel(14.sp, K.ink, weight = FontWeight.SemiBold))
            BasicText(sub, Modifier.padding(top = 2.dp), style = T.pixel(11.sp, K.inkSoft, lh = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun Note(text: String) {
    BasicText(text, Modifier.fillMaxWidth().padding(vertical = 16.dp), style = T.pixel(13.sp, K.inkSoft).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
}

/** The admin journal: who did what to whom, newest first; all admins or only my own actions. */
@Composable
private fun Journal(log: List<LogEntry>?, mine: Boolean, onMine: (Boolean) -> Unit) {
    val sound = LocalSound.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Hammasi", !mine) { if (mine) { sound.press(); onMine(false) } }
        Chip("Mening", mine) { if (!mine) { sound.press(); onMine(true) } }
    }
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        when {
            log == null -> Note("Yuklanmoqda…")
            log.isEmpty() -> Note("Jurnal hali bo'sh")
            else -> log.forEachIndexed { i, e ->
                if (i > 0) GrooveH()
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(actionMark(e.action), style = T.pixel(14.sp, actionColor(e.action), lh = 16.sp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            buildString {
                                append(e.actorName.ifBlank { "Admin" })
                                if (!e.targetName.isNullOrBlank()) append(" → ").append(e.targetName)
                            },
                            style = T.pixel(13.sp, K.ink, weight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        BasicText(
                            actionText(e.action) + (e.detail?.let { " · $it" } ?: ""),
                            style = T.pixel(12.sp, K.inkSoft, lh = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicText(formatWhen(e.ts), style = T.pixel(10.sp, K.inkSoft, lh = 12.sp))
                }
            }
        }
    }
}

private fun actionText(a: String) = when (a) {
    "ban" -> "bloklandi"
    "unban" -> "blokdan chiqarildi"
    "mute" -> "ovozsiz qilindi"
    "unmute" -> "yana yoza oladi"
    "purge" -> "barcha xabarlari o'chirildi"
    "delete" -> "xabari o'chirildi"
    "clear" -> "chat tozalandi"
    "slow" -> "sekin rejim"
    "lock" -> "chat yopildi"
    "unlock" -> "chat ochildi"
    "pin" -> "xabar qadaldi"
    "unpin" -> "qadash olib tashlandi"
    "grant" -> "admin qilindi"
    "revoke" -> "adminlikdan olindi"
    "login" -> "admin bo'lib kirdi"
    "logout" -> "adminlikdan chiqdi"
    else -> a
}

private fun actionMark(a: String) = when (a) {
    "ban", "purge", "delete", "clear" -> "✕"
    "mute", "lock", "slow" -> "◐"
    "grant", "login", "pin" -> "★"
    else -> "●"
}

private fun actionColor(a: String) = when (a) {
    "ban", "purge", "delete", "clear" -> K.onAir
    "grant", "login", "pin" -> K.gold
    "unban", "unmute", "unlock" -> K.ledGreen
    else -> K.inkSoft
}
