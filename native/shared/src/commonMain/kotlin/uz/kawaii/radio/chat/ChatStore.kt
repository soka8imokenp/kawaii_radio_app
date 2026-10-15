package uz.kawaii.radio.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import uz.kawaii.radio.ChatSocket
import uz.kawaii.radio.Mode
import uz.kawaii.radio.Platform
import uz.kawaii.radio.Profile
import uz.kawaii.radio.SocketListener
import uz.kawaii.radio.data.CLOUD_URL
import uz.kawaii.radio.data.json
import kotlin.random.Random

@Serializable
data class ChatReply(val id: Long, val name: String, val body: String, val kind: String = "text")

@Serializable
data class ChatMsg(
    val id: Long,
    val uid: String,
    val name: String,
    val avatar: String = "p:0",
    /** "text", "sticker" (body = sticker id) or "track" (body = song id). */
    val kind: String = "text",
    val body: String,
    val reply: ChatReply? = null,
    val ts: Long,
    /** Sent by this phone, not yet confirmed by the server. */
    val pending: Boolean = false,
    val nonce: String? = null,
)

@Serializable
data class Sticker(val id: String, val file: String, val type: String = "image", val emoji: String = "") {
    val url: String get() = if (file.startsWith("http")) file else CLOUD_URL + file
}

@Serializable
private data class StickerPack(val title: String = "", val stickers: List<Sticker> = emptyList())

@Serializable
private data class Identity(val uid: String, val key: String)

/** What this phone shows about its listener on their public profile page. */
@Serializable
data class PublicCard(
    val fav: String? = null,
    val likes: Int = 0,
    val plays: Int = 0,
    val minutes: Int = 0,
    val top: List<String> = emptyList(),
    /** Song playing right now, or null. */
    val now: String? = null,
)

/** Somebody's profile page, as the chat server describes it. */
@Serializable
data class UserCard(
    val uid: String,
    val name: String = "Mehmon",
    val avatar: String = "p:0",
    val admin: Boolean = false,
    val banned: Boolean = false,
    val joined: Long = 0,
    val seen: Long = 0,
    val online: Boolean = false,
    val live: Boolean = false,
    val messages: Int = 0,
    val card: PublicCard? = null,
)

data class Me(val uid: String, val admin: Boolean = false, val banned: Boolean = false)

private const val ID_KEY = "chat-id"
private const val PUSH_KEY = "chat-push"
private const val PUSH_TOKEN_KEY = "chat-push-token"
private const val HISTORY_PAGE = 60

/**
 * The global listeners' chat (cloud/src/chat.ts): one WebSocket, kept open while the app runs and
 * reopened with a growing delay after it drops. Each phone has its own id + secret key, so nobody
 * can write under somebody else's name.
 */
class ChatStore(
    private val p: Platform,
    private val scope: CoroutineScope,
    private val profile: StateFlow<Profile>,
    private val mode: StateFlow<Mode>,
    private val playing: StateFlow<Boolean>,
    private val card: StateFlow<PublicCard>,
) {
    private val identity: Identity = p.kv.get(ID_KEY)?.let { runCatching { json.decodeFromString<Identity>(it) }.getOrNull() }
        ?: Identity(hex(12), hex(24)).also { p.kv.set(ID_KEY, json.encodeToString(Identity.serializer(), it)) }

    private val _messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    /** Oldest first. */
    val messages: StateFlow<List<ChatMsg>> = _messages.asStateFlow()

    private val _me = MutableStateFlow(Me(identity.uid))
    val me: StateFlow<Me> = _me.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _online = MutableStateFlow(0)
    val online: StateFlow<Int> = _online.asStateFlow()
    private val _live = MutableStateFlow(0)
    /** People listening to the LIVE broadcast right now. */
    val live: StateFlow<Int> = _live.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val stickers: StateFlow<List<Sticker>> = _stickers.asStateFlow()

    private val _user = MutableStateFlow<UserCard?>(null)
    /** Profile page currently open (answer to [whois]). */
    val user: StateFlow<UserCard?> = _user.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short messages from the server ("too fast", "blocked"…). */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var socket: ChatSocket? = null
    private var started = false
    private var retry = 0
    private var reconnectJob: Job? = null
    private var loadingOlder = false

    /** Called when the chat page is first opened; safe to call again. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        connect()
        scope.launch { loadStickers() }
        // tell the room when the name / avatar or the LIVE status changes
        scope.launch {
            // name / avatar / public card: only real changes, at most every couple of seconds
            combine(profile, card) { pr, c -> pr to c }.distinctUntilChanged().debounce(2_000).collect { (pr, c) ->
                send(buildJsonObject {
                    put("type", "profile"); put("name", displayName(pr)); put("avatar", shareableAvatar(pr))
                    put("card", json.encodeToJsonElement(PublicCard.serializer(), c))
                })
            }
        }
        scope.launch {
            combine(mode, playing) { m, pl -> m == Mode.LIVE && pl }.distinctUntilChanged().collect { sendViewing() }
        }
    }

    /** App back in the foreground: reconnect at once if the socket dropped meanwhile. */
    fun onResume() {
        foreground = true
        sendViewing()
        if (started && !_connected.value) { reconnectJob?.cancel(); retry = 0; connect() }
    }

    fun onPause() {
        foreground = false
        sendViewing()
    }

    /* ---------- push notifications ---------- */

    private var foreground = true
    private var pageVisible = false

    /** The chat page is on screen: the server sends no push to a phone that is reading the chat. */
    fun setPageVisible(visible: Boolean) {
        pageVisible = visible
        sendViewing()
    }

    /** Read by the push service: drop the notification when the chat is already in front of the listener. */
    val isReading: Boolean get() = foreground && pageVisible

    private fun sendViewing() {
        send(buildJsonObject { put("type", "presence"); put("live", mode.value == Mode.LIVE && playing.value); put("viewing", isReading) })
    }

    private val _pushOn = MutableStateFlow(p.kv.get(PUSH_KEY) != "0")
    /** Chat notifications wanted (Profil switch). */
    val pushOn: StateFlow<Boolean> = _pushOn.asStateFlow()

    fun setPushOn(on: Boolean) {
        _pushOn.value = on
        p.kv.set(PUSH_KEY, if (on) "1" else "0")
        scope.launch { registerPush() }
    }

    /** New FCM token from the platform: remember it and tell the chat server. */
    fun onPushToken(token: String) {
        p.kv.set(PUSH_TOKEN_KEY, token)
        scope.launch { registerPush() }
    }

    private suspend fun registerPush() {
        val token = p.kv.get(PUSH_TOKEN_KEY) ?: return
        val body = buildJsonObject {
            put("uid", identity.uid); put("key", identity.key); put("token", token); put("on", _pushOn.value)
        }.toString()
        runCatching { p.httpPostJson("$CLOUD_URL/chat/push", body) }
    }

    private fun connect() {
        socket?.close()
        val url = CLOUD_URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/chat"
        socket = p.openSocket(url, object : SocketListener {
            override fun onOpen() {
                retry = 0
                val pr = profile.value
                send(buildJsonObject {
                    put("type", "hello"); put("uid", identity.uid); put("key", identity.key)
                    put("name", displayName(pr)); put("avatar", shareableAvatar(pr))
                    put("live", mode.value == Mode.LIVE && playing.value)
                    put("card", json.encodeToJsonElement(PublicCard.serializer(), card.value))
                }, force = true)
            }
            override fun onMessage(text: String) = handle(text)
            override fun onClosed(error: String?) {
                _connected.value = false
                // messages that never left stay visible as failed until the next try
                reconnectJob?.cancel()
                reconnectJob = scope.launch {
                    delay(minOf(30_000L, 1_000L shl minOf(retry++, 5)))
                    connect()
                }
            }
        })
    }

    private fun send(o: JsonObject, force: Boolean = false): Boolean {
        if (!force && !_connected.value) return false
        return socket?.send(o.toString()) == true
    }

    private fun handle(text: String) {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        fun msgs(key: String) = o[key]?.jsonArray?.mapNotNull { runCatching { json.decodeFromJsonElement(ChatMsg.serializer(), it) }.getOrNull() }.orEmpty()
        when (o.str("type")) {
            "welcome" -> {
                _connected.value = true
                o["you"]?.jsonObject?.let(::applyMe)
                val history = msgs("history")
                // keep messages still waiting to be sent after a reconnect
                _messages.update { old -> history + old.filter { it.pending } }
                _hasMore.value = history.size >= HISTORY_PAGE
                presence(o)
                sendViewing()
                resendPending()
            }
            "msg" -> {
                val m = o["m"]?.let { runCatching { json.decodeFromJsonElement(ChatMsg.serializer(), it) }.getOrNull() } ?: return
                val nonce = o.str("nonce")
                _messages.update { old ->
                    val rest = if (nonce != null) old.filterNot { it.nonce == nonce } else old
                    if (rest.any { it.id == m.id && !it.pending }) rest else rest + m
                }
            }
            "deleted" -> {
                val id = o["id"]?.jsonPrimitive?.longOrNull ?: return
                _messages.update { old -> old.filterNot { it.id == id && !it.pending } }
            }
            "history" -> {
                val older = msgs("msgs")
                loadingOlder = false
                _hasMore.value = older.size >= HISTORY_PAGE
                _messages.update { old -> older.filter { o2 -> old.none { it.id == o2.id } } + old }
            }
            "presence" -> presence(o)
            "you" -> applyMe(o)
            "user" -> _user.value = runCatching { json.decodeFromJsonElement(UserCard.serializer(), o) }.getOrNull()
            "error" -> o.str("text")?.let { _errors.tryEmit(it) }
        }
    }

    private fun applyMe(o: JsonObject) {
        _me.value = Me(
            uid = o.str("uid") ?: identity.uid,
            admin = o["admin"]?.jsonPrimitive?.booleanOrNull == true,
            banned = o["banned"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    private fun presence(o: JsonObject) {
        o["online"]?.jsonPrimitive?.intOrNull?.let { _online.value = it }
        o["live"]?.jsonPrimitive?.intOrNull?.let { _live.value = it }
    }

    /* ---------- actions ---------- */

    /** Sends a text, a sticker (body = sticker id) or a song card (body = song id). */
    fun send(kind: String, body: String, replyTo: ChatMsg? = null) {
        val text = body.trim()
        if (text.isEmpty()) return
        // hidden admin login: "/admin <code>" is never posted
        if (kind == "text" && text.startsWith("/admin ")) {
            send(buildJsonObject { put("type", "admin"); put("code", text.removePrefix("/admin ").trim()) })
            return
        }
        val nonce = hex(8)
        val pr = profile.value
        val pending = ChatMsg(
            id = -1, uid = identity.uid, name = displayName(pr), avatar = shareableAvatar(pr), kind = kind, body = text,
            reply = replyTo?.let { ChatReply(it.id, it.name, it.body.take(120), it.kind) },
            ts = uz.kawaii.radio.nowMs(), pending = true, nonce = nonce,
        )
        _messages.update { it + pending }
        sendPending(pending)
    }

    private fun sendPending(m: ChatMsg) {
        send(buildJsonObject {
            put("type", "send"); put("kind", m.kind); put("body", m.body); put("nonce", m.nonce)
            m.reply?.let { put("reply", it.id) }
        })
    }

    private fun resendPending() = _messages.value.filter { it.pending }.forEach(::sendPending)

    fun loadOlder() {
        if (loadingOlder || !_hasMore.value) return
        val first = _messages.value.firstOrNull { !it.pending } ?: return
        loadingOlder = true
        if (!send(buildJsonObject { put("type", "history"); put("before", first.id) })) loadingOlder = false
    }

    fun delete(m: ChatMsg) {
        if (m.pending) { _messages.update { old -> old.filterNot { it.nonce == m.nonce } }; return }
        send(buildJsonObject { put("type", "delete"); put("id", m.id) })
    }

    fun ban(uid: String, on: Boolean) {
        send(buildJsonObject { put("type", "ban"); put("uid", uid); put("on", on) })
        if (on) _errors.tryEmit("Foydalanuvchi bloklandi")
    }

    /** Opens somebody's profile page; the answer arrives in [user]. */
    fun whois(uid: String) {
        _user.value = UserCard(uid) // shows the sheet at once, filled in when the answer comes
        send(buildJsonObject { put("type", "whois"); put("uid", uid) })
    }

    fun closeUser() { _user.value = null }

    fun sticker(id: String): Sticker? = _stickers.value.firstOrNull { it.id == id }

    private suspend fun loadStickers() {
        val text = runCatching { p.httpGetText("$CLOUD_URL/files/stickers/pack.json") }.getOrNull() ?: return
        runCatching { json.decodeFromString<StickerPack>(text) }.getOrNull()?.let { _stickers.value = it.stickers }
    }

    private fun displayName(pr: Profile) = pr.name.trim().ifEmpty { "Mehmon" }

    /** Gallery photos stay on the phone; others see the pixel avatar. */
    private fun shareableAvatar(pr: Profile) = if (pr.avatar.startsWith("p:")) pr.avatar else "p:0"
}

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

private fun hex(bytes: Int): String = Random.nextBytes(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
