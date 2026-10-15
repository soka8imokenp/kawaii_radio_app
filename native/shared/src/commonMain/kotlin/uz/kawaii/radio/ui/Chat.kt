package uz.kawaii.radio.ui

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
    LaunchedEffect(active) { if (active) chat.start() }

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
    var menuFor by remember { mutableStateOf<ChatMsg?>(null) }
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
        PageHeader("Chat", if (connected) "$online onlayn · $live LIVE'da" else "Ulanmoqda…")

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(reversed, key = { m -> if (m.pending) "p-${m.nonce}" else "m-${m.id}" }) { m ->
                    Bubble(m, mine = m.uid == me.uid, onLong = { menuFor = m }, onReplyTap = {})
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
                        draft, { if (it.length <= 1000) draft = it },
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

    // long press on a bubble: reply, delete (own or as admin), block (admin)
    val m = menuFor
    Sheet(m != null, { menuFor = null }, "Xabar") {
        if (m != null) {
            val mine = m.uid == me.uid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelKey("Javob berish", Modifier.fillMaxWidth(), height = 48.dp) { replyTo = m; menuFor = null }
                if (mine || me.admin) PixelKey("O'chirish", Modifier.fillMaxWidth(), height = 48.dp) { chat.delete(m); menuFor = null }
                if (me.admin && !mine) PixelKey("Bloklash: ${m.name}", Modifier.fillMaxWidth(), height = 48.dp) { chat.ban(m.uid, true); menuFor = null }
            }
        }
    }
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
private fun Bubble(m: ChatMsg, mine: Boolean, onLong: () -> Unit, onReplyTap: () -> Unit) {
    val store = LocalStore.current
    Row(
        Modifier.fillMaxWidth().alpha(if (m.pending) 0.6f else 1f),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            AvatarView(m.avatar, null, 30.dp)
            Spacer(Modifier.width(6.dp))
        }
        val shape = RoundedCornerShape(16.dp)
        val bare = m.kind == "sticker"
        Column(
            Modifier.widthIn(max = 270.dp)
                .then(if (bare) Modifier else if (mine) Modifier.lcd(shape).border(2.dp, K.lcdBorder, shape) else Modifier.raised(shape))
                .pointerInput(m.id, m.nonce) { detectTapGestures(onLongPress = { onLong() }) }
                .padding(horizontal = if (bare) 0.dp else 12.dp, vertical = if (bare) 0.dp else 8.dp),
        ) {
            val ink = if (mine) K.lcdInk else K.ink
            if (!mine) BasicText(m.name, style = T.pixel(12.sp, K.inkSoft, weight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            m.reply?.let { r ->
                Column(Modifier.padding(top = 4.dp, bottom = 4.dp).tap(onClick = onReplyTap)) {
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
