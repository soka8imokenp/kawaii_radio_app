package uz.kawaii.radio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.kawaii.radio.chat.ChatMsg
import uz.kawaii.radio.ui.kit.AvatarView
import uz.kawaii.radio.ui.kit.Glyph
import uz.kawaii.radio.ui.kit.LocalSound
import uz.kawaii.radio.ui.kit.Px
import uz.kawaii.radio.ui.kit.lcd
import uz.kawaii.radio.ui.kit.raised
import uz.kawaii.radio.ui.kit.tap
import uz.kawaii.radio.ui.kit.tray
import uz.kawaii.radio.ui.sheets.Sheet
import uz.kawaii.radio.ui.theme.K
import uz.kawaii.radio.ui.theme.T
import uz.kawaii.radio.ui.theme.lcdShadow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
private fun clock(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/** The global listeners' chat: bubbles on the plastic, the composer is a strip of LCD glass. */
@Composable
fun Chat(active: Boolean) {
    val store = LocalStore.current
    val chat = store.chat
    val sound = LocalSound.current
    LaunchedEffect(active) {
        if (active) { chat.start(); store.platform.clearChatNotifications() }
        chat.setPageVisible(active)
    }

    val messages by chat.messages.collectAsState()
    val me by chat.me.collectAsState()
    val connected by chat.connected.collectAsState()
    val online by chat.online.collectAsState()
    val live by chat.live.collectAsState()
    val hasMore by chat.hasMore.collectAsState()
    val stickers by chat.stickers.collectAsState()
    val engine by store.engine.collectAsState()

    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMsg?>(null) }
    var picker by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { chat.errors.collect { toast = it } }
    LaunchedEffect(toast) { if (toast != null) { delay(2_500); toast = null } }

    // newest at the bottom; the list is reversed so it opens at the latest message
    val list = rememberLazyListState()
    val reversed = remember(messages) { messages.asReversed() }
    LaunchedEffect(messages.lastOrNull()?.let { it.id to it.nonce }) {
        if (list.firstVisibleItemIndex <= 2) list.animateScrollToItem(0)
    }
    // reached the oldest loaded message: fetch the page before it
    LaunchedEffect(list, hasMore) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to list.layoutInfo.totalItemsCount }.collect { (last, total) ->
            if (hasMore && total > 0 && last >= total - 3) chat.loadOlder()
        }
    }

    val send = { kind: String, body: String ->
        sound.press()
        chat.send(kind, body, replyTo)
        replyTo = null
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val typing by chat.typing.collectAsState()
        val sub = when {
            !connected -> "Ulanmoqda…"
            typing.size == 1 -> "${typing[0]} yozmoqda…"
            typing.size > 1 -> "${typing.size} kishi yozmoqda…"
            else -> "$online onlayn · $live LIVE'da"
        }
        PageHeader("Chat", sub) {
            // the mini player moves up here on this page: cover, song and artist in the corner
            AnimatedVisibility(
                active,
                enter = fadeIn(tween(260, delayMillis = 120)) + slideInHorizontally(tween(320, delayMillis = 120)) { it / 2 },
                exit = fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 2 },
            ) { NowPlayingChip() }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(reversed, key = { m -> if (m.pending) "p-${m.nonce}" else "m-${m.id}" }) { m ->
                    Bubble(
                        m, mine = m.uid == me.uid, admin = me.admin,
                        onReply = { replyTo = m },
                        onDelete = { chat.delete(m) },
                        onBan = { chat.ban(m.uid, true) },
                        onReact = { e -> chat.react(m, e) },
                        myUid = me.uid,
                        mentionsMe = chat.mentionsMe(m),
                    )
                }
                if (messages.isEmpty() && connected) {
                    item(key = "empty") {
                        BasicText(
                            "Hali hech kim yozmadi. Birinchi bo'ling!", Modifier.fillMaxWidth().padding(top = 40.dp),
                            style = T.pixel(14.sp, K.inkSoft).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        )
                    }
                }
            }
            toast?.let {
                BasicText(
                    it, Modifier.align(Alignment.TopCenter).padding(top = 8.dp).raised(RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    style = T.pixel(13.sp, K.ink),
                )
            }
        }

        // reply preview
        // "@" suggestions: names seen in the chat that start with what was typed after "@"
        val word = draft.substringAfterLast(' ').substringAfterLast('\n')
        if (word.startsWith("@")) {
            val q = word.drop(1).lowercase()
            val names = remember(messages, q) { chat.mentionNames().filter { it.lowercase().startsWith(q) }.take(6) }
            if (names.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (n in names) Chip("@$n", false) { draft = draft.dropLast(word.length) + "@$n " }
                }
            }
        }

        replyTo?.let { r ->
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().tray(RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText("↩ ${r.name}", style = T.pixel(12.sp, K.inkSoft))
                    BasicText(preview(r), style = T.pixel(13.sp, K.ink), maxLines = 1)
                }
                BasicText("✕", Modifier.tap(onClick = { replyTo = null }).padding(6.dp), style = T.pixel(15.sp, K.ink))
            }
        }

        if (me.banned) {
            BasicText(
                "Siz chatda bloklangansiz", Modifier.fillMaxWidth().padding(16.dp),
                style = T.pixel(14.sp, K.onAir).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        } else {
            // composer: share the song on air, LCD text field, stickers, send
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth().tray(RoundedCornerShape(18.dp)).padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RoundKey(Glyph.RADIO, enabled = engine.id != null) { engine.id?.let { send("track", it) } }
                Box(
                    Modifier.weight(1f).lcd(RoundedCornerShape(13.dp)).border(2.dp, K.lcdBorder, RoundedCornerShape(13.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (draft.isEmpty()) BasicText("XABAR YOZING…", style = T.pixel(15.sp, K.lcdInk.copy(alpha = 0.4f), lh = 18.sp))
                    BasicTextField(
                        draft, { if (it.length <= 1000) { draft = it; if (it.isNotBlank()) chat.typing() } },
                        Modifier.fillMaxWidth(),
                        maxLines = 4,
                        textStyle = T.pixel(15.sp, K.lcdInk, lh = 18.sp).copy(shadow = lcdShadow()),
                        cursorBrush = SolidColor(K.lcdInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) { send("text", draft); draft = "" } }),
                    )
                }
                if (stickers.isNotEmpty()) RoundKey(Glyph.STAR, active = picker) { picker = !picker }
                RoundKey(Glyph.NEXT, enabled = draft.isNotBlank()) { send("text", draft); draft = "" }
            }
            if (picker) {
                LazyVerticalGrid(
                    GridCells.Fixed(4),
                    Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 12.dp).tray(RoundedCornerShape(18.dp)),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(stickers, key = { it.id }) { s ->
                        Box(Modifier.size(72.dp).tap(onClick = { send("sticker", s.id); picker = false }), contentAlignment = Alignment.Center) {
                            StickerView(s.id, 68)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    UserSheet()
}

private fun preview(m: ChatMsg) = when (m.kind) {
    "sticker" -> "Stiker"
    "track" -> "Qo'shiq"
    else -> m.body
}

@Composable
private fun RoundKey(glyph: List<String>, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).alpha(if (enabled) 1f else 0.4f).raised(RoundedCornerShape(13.dp), active).tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Px(glyph, 15.dp, K.ctlInk) }
}

@Composable
private fun Bubble(
    m: ChatMsg, mine: Boolean, admin: Boolean,
    onReply: () -> Unit, onDelete: () -> Unit, onBan: () -> Unit,
    onReact: (String) -> Unit, myUid: String, mentionsMe: Boolean,
) {
    val store = LocalStore.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().alpha(if (m.pending) 0.6f else 1f),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            AvatarView(m.avatar, null, 30.dp, Modifier.tap(onClick = { store.chat.whois(m.uid) }))
            Spacer(Modifier.width(6.dp))
        }
        val shape = RoundedCornerShape(16.dp)
        val bare = m.kind == "sticker"
        Column(
            Modifier.widthIn(max = 270.dp)
                .then(if (bare) Modifier else if (mine) Modifier.lcd(shape).border(2.dp, K.lcdBorder, shape) else Modifier.raised(shape))
                .then(if (mentionsMe && !bare) Modifier.border(2.dp, K.gold, shape) else Modifier)
                .pointerInput(m.id, m.nonce) { detectTapGestures(onLongPress = { if (!m.pending || mine) menu = true }) }
                .padding(horizontal = if (bare) 0.dp else 12.dp, vertical = if (bare) 0.dp else 8.dp),
        ) {
            val ink = if (mine) K.lcdInk else K.ink
            if (!mine) BasicText(
                m.name, Modifier.tap(onClick = { store.chat.whois(m.uid) }),
                style = T.pixel(12.sp, K.inkSoft, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            )
            m.reply?.let { r ->
                Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    BasicText("↩ ${r.name}", style = T.pixel(11.sp, ink.copy(alpha = 0.6f)))
                    BasicText(if (r.kind == "text") r.body else if (r.kind == "sticker") "Stiker" else "Qo'shiq", style = T.pixel(12.sp, ink.copy(alpha = 0.75f)), maxLines = 1)
                }
            }
            when (m.kind) {
                "sticker" -> StickerView(m.body, 140)
                "track" -> {
                    val t = store.track(m.body)
                    Row(
                        Modifier.padding(top = 2.dp).tap(onClick = { if (t != null) store.play(t.id) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (t != null) Cover(t.coverArt, 44.dp)
                        Column {
                            BasicText("♪ ${t?.title ?: "Qo'shiq"}", style = T.pixel(14.sp, ink, weight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
                            BasicText(t?.artist ?: "", style = T.pixel(12.sp, ink.copy(alpha = 0.7f)), maxLines = 1)
                        }
                    }
                }
                else -> BasicText(m.body, style = T.pixel(15.sp, ink, lh = 20.sp).let { if (mine) it.copy(shadow = lcdShadow()) else it })
            }
            BasicText(
                if (m.pending) "…" else clock(m.ts),
                Modifier.align(Alignment.End).padding(top = 2.dp),
                style = T.pixel(10.sp, ink.copy(alpha = 0.5f)),
            )
            if (m.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (e in uz.kawaii.radio.chat.REACTIONS) {
                        val who = m.reactions[e].orEmpty()
                        if (who.isEmpty()) continue
                        val minePick = myUid in who
                        BasicText(
                            "$e ${who.size}",
                            Modifier.raised(RoundedCornerShape(10.dp), minePick).tap(onClick = { onReact(e) }).padding(horizontal = 7.dp, vertical = 3.dp),
                            style = T.pixel(12.sp, if (minePick) K.ink else K.inkSoft),
                        )
                    }
                }
            }
            if (menu) MessageMenu(m, mine, admin, onDismiss = { menu = false }, onReply = onReply, onDelete = onDelete, onBan = onBan, onReact = onReact)
        }
    }
}

/** A sticker of the pack (static or animated webp); the emoji stands in until the pack is loaded. */
@Composable
private fun StickerView(id: String, size: Int) {
    val chat = LocalStore.current.chat
    val stickers by chat.stickers.collectAsState()
    val s = remember(id, stickers) { stickers.firstOrNull { it.id == id } }
    if (s == null) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { BasicText("🖼", style = T.pixel((size / 3).sp, K.inkSoft)) }
        return
    }
    AsyncImage(s.url, s.emoji, Modifier.size(size.dp), contentScale = ContentScale.Fit)
}

/** The song on air as a compact chip for the chat header; tapping it opens the radio. */
@Composable
private fun NowPlayingChip() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val tracks by store.tracks.collectAsState()
    val engine by store.engine.collectAsState()
    val t = store.current(tracks, engine.id) ?: return
    Row(
        Modifier.padding(start = 10.dp).widthIn(max = 190.dp).raised(RoundedCornerShape(13.dp))
            .tap(onClick = { ui.page = Page.Home }).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Cover(t.coverArt, 34.dp, 7.dp)
        Column(Modifier.padding(end = 4.dp)) {
            BasicText(
                t.title, Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_200, repeatDelayMillis = 1_500),
                style = T.pixel(13.sp, K.ink, weight = androidx.compose.ui.text.font.FontWeight.SemiBold, lh = 15.sp), maxLines = 1,
            )
            BasicText(
                (if (engine.playing) "♪ " else "") + t.artist, Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_200, repeatDelayMillis = 1_500),
                style = T.pixel(11.sp, K.inkSoft, lh = 13.sp), maxLines = 1,
            )
        }
    }
}

/** Telegram-style menu next to the message: below it, or above when there is no room. */
private class NextToBubble(private val mine: Boolean, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (if (mine) anchorBounds.right - popupContentSize.width else anchorBounds.left).coerceIn(0, maxX)
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MessageMenu(
    m: ChatMsg, mine: Boolean, admin: Boolean,
    onDismiss: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit, onBan: () -> Unit, onReact: (String) -> Unit,
) {
    val sound = LocalSound.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    Popup(popupPositionProvider = NextToBubble(mine, gap), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(196.dp).raised(RoundedCornerShape(14.dp)).padding(vertical = 4.dp)) {
            @Composable
            fun item(label: String, color: androidx.compose.ui.graphics.Color = K.ink, action: () -> Unit) {
                Box(
                    Modifier.fillMaxWidth().height(42.dp).tap(onClick = { sound.press(); onDismiss(); action() }).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { BasicText(label, style = T.pixel(14.sp, color)) }
            }
            // quick reactions on top, like Telegram
            if (!m.pending) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (e in uz.kawaii.radio.chat.REACTIONS) {
                        Box(
                            Modifier.size(40.dp).tap(onClick = { sound.press(); onDismiss(); onReact(e) }),
                            contentAlignment = Alignment.Center,
                        ) { BasicText(e, style = T.pixel(22.sp, K.ink)) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 10.dp).raised(RoundedCornerShape(1.dp)))
            }
            if (!m.pending) item("↩  Javob berish", action = onReply)
            if (m.kind == "text") item("⧉  Nusxa olish") { clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.body)) }
            if (mine || admin) item("✕  O'chirish", K.onAir, onDelete)
            if (admin && !mine) item("⊘  Bloklash", K.onAir, onBan)
        }
    }
}

/** Somebody's profile page from the chat: avatar, badges, an LCD of their numbers, favourites and the song on now. */
@Composable
private fun UserSheet() {
    val store = LocalStore.current
    val chat = store.chat
    val sound = LocalSound.current
    val u by chat.user.collectAsState()
    val me by chat.me.collectAsState()
    val shown = u
    Sheet(shown != null, { chat.closeUser() }, "Profil") {
        if (shown == null) return@Sheet
        val c = shown.card
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AvatarView(shown.avatar, null, 84.dp)
            BasicText(
                shown.name, Modifier.padding(top = 10.dp),
                style = T.pixel(22.sp, K.ink, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (shown.admin) Badge("ADMIN", K.gold)
                when {
                    shown.live -> Badge("● LIVE", K.onAir)
                    shown.online -> Badge("● ONLAYN", K.ledGreen)
                    shown.seen > 0 -> BasicText("oxirgi marta: ${uz.kawaii.radio.data.formatWhen(shown.seen)}", style = T.pixel(12.sp, K.inkSoft))
                }
            }
        }
        // numbers on a little LCD, like the Kawaii Pass panel
        Row(
            Modifier.padding(top = 16.dp).fillMaxWidth().lcd(RoundedCornerShape(16.dp)).border(3.dp, K.lcdBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            LcdStat(Modifier.weight(1f), (c?.likes ?: 0).toString(), "SEVIMLI")
            LcdStat(Modifier.weight(1f), ((c?.minutes ?: 0) / 60).toString(), "SOAT")
            LcdStat(Modifier.weight(1f), shown.messages.toString(), "XABAR")
        }
        if (c?.fav != null || !c?.top.isNullOrEmpty()) {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().tray(RoundedCornerShape(16.dp)).padding(14.dp)) {
                c?.fav?.let {
                    BasicText("SEVIMLI IJROCHI", style = T.micro(11.sp, K.inkSoft))
                    BasicText(it, Modifier.padding(top = 4.dp, bottom = 8.dp), style = T.pixel(16.sp, K.ink, weight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                }
                if (!c?.top.isNullOrEmpty()) {
                    BasicText("KO'P TINGLAGANLARI", style = T.micro(11.sp, K.inkSoft))
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        c!!.top.forEachIndexed { i, a -> Chip("${i + 1}. $a", false) { } }
                    }
                }
            }
        }
        val now = c?.now?.let { store.track(it) }
        if (now != null) {
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().raised(RoundedCornerShape(16.dp))
                    .tap(onClick = { sound.press(); chat.closeUser(); store.play(now.id) }).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Cover(now.coverArt, 48.dp)
                Column(Modifier.weight(1f)) {
                    BasicText("HOZIR TINGLAYAPTI", style = T.micro(10.sp, K.inkSoft))
                    BasicText(now.title, style = T.pixel(15.sp, K.ink, weight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
                    BasicText(now.artist, style = T.pixel(12.sp, K.inkSoft), maxLines = 1)
                }
                Px(Glyph.PLAY, 12.dp, K.ctlInk)
            }
        }
        if (shown.joined > 0) {
            BasicText(
                "Chatda: ${uz.kawaii.radio.data.formatWhen(shown.joined)} dan beri", Modifier.fillMaxWidth().padding(top = 12.dp),
                style = T.pixel(12.sp, K.inkSoft).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
        if (me.admin && shown.uid != me.uid && !shown.banned) {
            PixelKey("Bloklash", Modifier.padding(top = 12.dp).fillMaxWidth(), height = 46.dp) { chat.ban(shown.uid, true); chat.closeUser() }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    BasicText(
        text, Modifier.raised(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        style = T.pixel(11.sp, color, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    )
}

@Composable
private fun LcdStat(modifier: Modifier, value: String, label: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(value, style = T.pixel(24.sp, K.lcdInk, lh = 24.sp).copy(shadow = lcdShadow()))
        BasicText(label, Modifier.padding(top = 4.dp), style = T.pixel(11.sp, K.lcdInk.copy(alpha = 0.6f)))
    }
}
