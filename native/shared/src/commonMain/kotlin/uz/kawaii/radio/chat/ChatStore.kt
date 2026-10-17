package uz.kawaii.radio.chat

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
    /** emoji → ids of the people who reacted with it */
    val reactions: Map<String, List<String>> = emptyMap(),
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
    val owner: Boolean = false,
    val banned: Boolean = false,
    val joined: Long = 0,
    val seen: Long = 0,
    val online: Boolean = false,
    val live: Boolean = false,
    val messages: Int = 0,
    val card: PublicCard? = null,
    /** Can't write until then (epoch ms), 0 = free to talk. */
    val mutedUntil: Long = 0,
)

/** A listener in the admin panel's list. */
@Serializable
data class AdminUser(
    val uid: String,
    val name: String = "Mehmon",
    val avatar: String = "p:0",
    val admin: Boolean = false,
    val owner: Boolean = false,
    val banned: Boolean = false,
    val mutedUntil: Long = 0,
    val seen: Long = 0,
    val joined: Long = 0,
    val messages: Int = 0,
    val online: Boolean = false,
    val live: Boolean = false,
)

/** One line of the admin journal: who did what to whom. */
@Serializable
data class LogEntry(
    val id: Long = 0,
    val ts: Long = 0,
    val actor: String = "",
    val actorName: String = "",
    /** ban, unban, mute, unmute, purge, delete, clear, slow, lock, unlock, pin, unpin, grant, revoke, login, logout */
    val action: String = "",
    val target: String? = null,
    val targetName: String? = null,
    val detail: String? = null,
)

/** Numbers on the admin panel's LCD. */
@Serializable
data class RoomStats(
    val online: Int = 0,
    val live: Int = 0,
    val users: Int = 0,
    val banned: Int = 0,
    val muted: Int = 0,
    val messagesToday: Int = 0,
    val messages: Int = 0,
    val activeToday: Int = 0,
)

/** Chat settings every listener follows: slow mode, closed chat, the pinned message. */
data class RoomState(val slow: Int = 0, val locked: Boolean = false, val pin: ChatMsg? = null)

/** State of the profile photo's upload to the chat. */
enum class AvatarSync { Idle, Uploading, Done, Failed }

/** Admin key check: nothing asked yet, waiting for the server, or the key was wrong. */
sealed interface AdminLogin {
    data object Idle : AdminLogin
    data object Checking : AdminLogin
    data class Failed(val text: String) : AdminLogin
}

data class Me(val uid: String, val admin: Boolean = false, val banned: Boolean = false, val mutedUntil: Long = 0, val owner: Boolean = false) {
    /** May this listener block / mute / wipe somebody with [role] (0 listener, 1 admin, 2 owner)? */
    fun canTouch(uid: String, role: Int): Boolean = admin && uid != this.uid && (role == 0 || (role == 1 && owner))
}

private const val ID_KEY = "chat-id"
private const val READ_KEY = "chat-read"
private const val AVATAR_KEY = "chat-avatar"
private const val AVATAR_SRC_KEY = "chat-avatar-src"
/** The reactions offered on a long press, like Telegram's quick row. */
val REACTIONS = listOf("❤️", "🔥", "😂", "😭")
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

    private val _room = MutableStateFlow(RoomState())
    val room: StateFlow<RoomState> = _room.asStateFlow()

    private val _adminLogin = MutableStateFlow<AdminLogin>(AdminLogin.Idle)
    val adminLogin: StateFlow<AdminLogin> = _adminLogin.asStateFlow()

    private val _users = MutableStateFlow<List<AdminUser>?>(null)
    /** Admin panel: the listeners of the last [loadUsers], null while loading. */
    val users: StateFlow<List<AdminUser>?> = _users.asStateFlow()

    private val _stats = MutableStateFlow<RoomStats?>(null)
    val stats: StateFlow<RoomStats?> = _stats.asStateFlow()

    private val _staff = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Admins (1) and the owner (2) by uid: admins can't block each other, so the keys hide for them. */
    val staff: StateFlow<Map<String, Int>> = _staff.asStateFlow()
    fun role(uid: String): Int = _staff.value[uid] ?: 0

    private val _log = MutableStateFlow<List<LogEntry>?>(null)
    /** Admin journal of the last [loadLog], newest first; null while loading. */
    val log: StateFlow<List<LogEntry>?> = _log.asStateFlow()

    private val _avatarSync = MutableStateFlow(AvatarSync.Idle)
    /** The gallery photo on its way to the chat server (shown in the avatar picker). */
    val avatarSync: StateFlow<AvatarSync> = _avatarSync.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short messages from the server ("too fast", "blocked"…). */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var socket: ChatSocket? = null
    private var started = false

    /* ---------- unread, typing, mentions ---------- */

    private var lastRead: Long = p.kv.get(READ_KEY)?.toLongOrNull() ?: 0L
    private val _unread = MutableStateFlow(0)
    /** Messages from other people that arrived since the chat was last looked at (badge on the Chat key). */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private fun recountUnread() {
        if (isReading) markRead()
        else _unread.value = _messages.value.count { !it.pending && it.id > lastRead && it.uid != identity.uid }
    }

    private fun markRead() {
        val last = _messages.value.lastOrNull { !it.pending }?.id ?: return
        if (last > lastRead) { lastRead = last; p.kv.set(READ_KEY, last.toString()) }
        _unread.value = 0
    }

    /** Names seen in the chat, for "@" suggestions (newest first, without this listener). */
    fun mentionNames(): List<String> =
        _messages.value.asReversed().filter { it.uid != identity.uid }.map { it.name }.distinct().take(20)

    /** True when [m] calls this listener by "@name" or replies to them. */
    fun mentionsMe(m: ChatMsg): Boolean {
        if (m.uid == identity.uid) return false
        if (m.reply != null && _messages.value.any { it.id == m.reply.id && it.uid == identity.uid }) return true
        val me = displayName(profile.value).lowercase()
        return me.length >= 2 && m.kind == "text" && m.body.lowercase().contains("@$me")
    }

    /** Toggles [emoji] on [m] (the same emoji again takes it back). */
    fun react(m: ChatMsg, emoji: String) {
        if (m.pending) return
        send(buildJsonObject { put("type", "react"); put("id", m.id); put("emoji", emoji) })
    }
    private var retry = 0
    private var reconnectJob: Job? = null
    private var loadingOlder = false

    /** Called when the chat page is first opened; safe to call again. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch { _messages.collect { recountUnread() } }
        connect()
        scope.launch { loadStickers() }
        // tell the room when the name / avatar or the LIVE status changes
        scope.launch {
            // name / avatar / public card: only real changes, at most every couple of seconds
            combine(profile, card) { pr, c -> pr to c }.distinctUntilChanged().debounce(2_000).collect { (pr, c) ->
                syncAvatar(pr)
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
        // a new gallery photo keeps avatar = "photo": check the file itself when coming back
        if (started) scope.launch {
            val before = p.kv.get(AVATAR_KEY)
            syncAvatar(profile.value)
            if (p.kv.get(AVATAR_KEY) != before) send(buildJsonObject { put("type", "profile"); put("name", displayName(profile.value)); put("avatar", shareableAvatar(profile.value)) })
        }
        sendViewing()
        if (isReading) markRead()
        if (started && !_connected.value && !connecting) { reconnectJob?.cancel(); retry = 0; connect() }
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
        if (isReading) markRead()
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

    /** Bumped for every new socket: events of an older, replaced socket are ignored (no reconnect storms). */
    private var generation = 0
    private var connecting = false

    private fun connect() {
        val gen = ++generation
        connecting = true
        socket?.close()
        val url = CLOUD_URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/chat"
        socket = p.openSocket(url, object : SocketListener {
            override fun onOpen() {
                if (gen != generation) return
                retry = 0
                val pr = profile.value
                send(buildJsonObject {
                    put("type", "hello"); put("uid", identity.uid); put("key", identity.key)
                    put("name", displayName(pr)); put("avatar", shareableAvatar(pr))
                    put("live", mode.value == Mode.LIVE && playing.value)
                    put("card", json.encodeToJsonElement(PublicCard.serializer(), card.value))
                }, force = true)
            }
            override fun onMessage(text: String) { if (gen == generation) handle(text) }
            override fun onClosed(error: String?) {
                if (gen != generation) return
                connecting = false
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
                connecting = false
                _connected.value = true
                o["you"]?.jsonObject?.let(::applyMe)
                val history = msgs("history")
                // first time in the chat: what was written before counts as read
                if (lastRead == 0L) history.lastOrNull()?.let { lastRead = it.id; p.kv.set(READ_KEY, it.id.toString()) }
                // keep messages still waiting to be sent after a reconnect
                _messages.update { old -> history + old.filter { it.pending } }
                _hasMore.value = history.size >= HISTORY_PAGE
                presence(o)
                o["room"]?.jsonObject?.let(::applyRoom)
                o["staff"]?.let(::applyStaff)
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
            "reactions" -> {
                val id = o["id"]?.jsonPrimitive?.longOrNull ?: return
                val r = o["reactions"]?.let { runCatching { json.decodeFromJsonElement(ReactionsSerializer, it) }.getOrNull() } ?: emptyMap()
                _messages.update { old -> old.map { if (it.id == id && !it.pending) it.copy(reactions = r) else it } }
            }
            "user" -> _user.value = runCatching { json.decodeFromJsonElement(UserCard.serializer(), o) }.getOrNull()
            "error" -> o.str("text")?.let { _errors.tryEmit(it) }
            "room" -> applyRoom(o)
            "staff" -> o["staff"]?.let(::applyStaff)
            "log" -> _log.value = o["entries"]?.jsonArray?.mapNotNull { runCatching { json.decodeFromJsonElement(LogEntry.serializer(), it) }.getOrNull() }.orEmpty()
            "cleared" -> _messages.update { old -> old.filter { it.pending } }
            "purged" -> {
                val uid = o.str("uid") ?: return
                _messages.update { old -> old.filterNot { it.uid == uid && !it.pending } }
                _users.value?.let { l -> _users.value = l.map { if (it.uid == uid) it.copy(messages = 0) else it } }
            }
            "admin" -> _adminLogin.value =
                if (o["ok"]?.jsonPrimitive?.booleanOrNull == true) AdminLogin.Idle else AdminLogin.Failed(o.str("text") ?: "Kalit noto'g'ri")
            "users" -> _users.value = o["users"]?.jsonArray?.mapNotNull { runCatching { json.decodeFromJsonElement(AdminUser.serializer(), it) }.getOrNull() }.orEmpty()
            "stats" -> _stats.value = runCatching { json.decodeFromJsonElement(RoomStats.serializer(), o) }.getOrNull()
        }
    }

    private fun applyMe(o: JsonObject) {
        _me.value = Me(
            uid = o.str("uid") ?: identity.uid,
            admin = o["admin"]?.jsonPrimitive?.booleanOrNull == true,
            banned = o["banned"]?.jsonPrimitive?.booleanOrNull == true,
            mutedUntil = o["mutedUntil"]?.jsonPrimitive?.longOrNull ?: 0,
            owner = o["owner"]?.jsonPrimitive?.booleanOrNull == true,
        )
        // an admin action changed somebody's rights: refresh the open admin list
        if (_me.value.admin && _users.value != null) loadUsers(lastFilter)
    }

    private fun applyStaff(e: kotlinx.serialization.json.JsonElement) {
        _staff.value = runCatching { e.jsonObject.mapValues { it.value.jsonPrimitive.intOrNull ?: 0 } }.getOrDefault(emptyMap())
    }

    private fun applyRoom(o: JsonObject) {
        _room.value = RoomState(
            slow = o["slow"]?.jsonPrimitive?.intOrNull ?: 0,
            locked = o["locked"]?.jsonPrimitive?.booleanOrNull == true,
            pin = o["pin"]?.takeIf { it is JsonObject }?.let { runCatching { json.decodeFromJsonElement(ChatMsg.serializer(), it) }.getOrNull() },
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

    fun ban(uid: String, on: Boolean, purge: Boolean = false) {
        send(buildJsonObject { put("type", "ban"); put("uid", uid); put("on", on); put("purge", purge) })
        refreshAdmin()
    }

    /* ---------- admin ---------- */

    /** Checks a personal admin key; [me] turns admin on success, [adminLogin] says why not otherwise. */
    fun adminLogin(key: String) {
        val k = key.trim()
        if (k.isEmpty()) return
        if (!_connected.value) { _adminLogin.value = AdminLogin.Failed("Chatga ulanib bo'lmadi, internetni tekshiring"); return }
        _adminLogin.value = AdminLogin.Checking
        send(buildJsonObject { put("type", "admin"); put("code", k) })
    }

    fun adminLogout() = send(buildJsonObject { put("type", "unadmin") }).let { }

    /** [minutes] = 0 lets the listener write again. */
    fun mute(uid: String, minutes: Int) {
        send(buildJsonObject { put("type", "mute"); put("uid", uid); put("minutes", minutes) })
        refreshAdmin()
    }

    fun purge(uid: String) = send(buildJsonObject { put("type", "purge"); put("uid", uid) }).let { refreshAdmin() }
    fun clearChat() = send(buildJsonObject { put("type", "clear") }).let { refreshAdmin() }
    fun setSlow(seconds: Int) = send(buildJsonObject { put("type", "room"); put("slow", seconds) }).let { }
    fun setLocked(on: Boolean) = send(buildJsonObject { put("type", "room"); put("locked", on) }).let { }
    fun pin(m: ChatMsg?) = send(buildJsonObject { put("type", "pin"); if (m != null) put("id", m.id) else put("id", JsonNull) }).let { }

    private var lastFilter = ""
    /** Admin panel list: "online", "banned", "muted" or "" for everybody. */
    fun loadUsers(filter: String = lastFilter) {
        lastFilter = filter
        send(buildJsonObject { put("type", "users"); put("filter", filter) })
    }
    fun loadStats() = send(buildJsonObject { put("type", "stats") }).let { }

    /** Owner only: gives or takes away admin rights. */
    fun setAdmin(uid: String, on: Boolean) {
        send(buildJsonObject { put("type", "setadmin"); put("uid", uid); put("on", on) })
        refreshAdmin()
    }

    /** Admin journal: only this admin's actions when [mine]. */
    fun loadLog(mine: Boolean) {
        _log.value = null
        send(buildJsonObject { put("type", "log"); put("mine", mine) })
    }
    private fun refreshAdmin() {
        if (!_me.value.admin) return
        scope.launch { delay(400); loadStats(); if (_users.value != null) loadUsers(lastFilter) }
    }

    /* ---------- what is listened to ---------- */

    /** A song was listened to for 30 s: counted (once per listener and day) for "Hozir ko'p tinglanmoqda". */
    fun reportPlay(trackId: String) {
        scope.launch {
            runCatching {
                p.httpPostJson("$CLOUD_URL/chat/play", buildJsonObject { put("uid", identity.uid); put("key", identity.key); put("id", trackId) }.toString())
            }
        }
    }

    /** Most listened songs of the last week (song id → listeners), or null when the cloud can't be reached. */
    suspend fun trending(): List<Pair<String, Int>>? = runCatching {
        json.parseToJsonElement(p.httpGetText("$CLOUD_URL/chat/trending")).jsonObject["tracks"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val id = o.str("id") ?: return@mapNotNull null
            id to (o["n"]?.jsonPrimitive?.intOrNull ?: 0)
        }
    }.getOrNull()

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

    /** Pixel avatars go as they are; a gallery photo goes as its uploaded copy ("u:<uid>:<version>"). */
    private fun shareableAvatar(pr: Profile) =
        if (pr.avatar.startsWith("p:")) pr.avatar else p.kv.get(AVATAR_KEY) ?: "p:0"

    /**
     * A photo was just picked (or a pixel avatar chosen): upload it now, not after the usual
     * debounce, and tell the room, so the picker can show that it worked.
     */
    fun avatarChanged(pr: Profile) {
        if (pr.avatar != "photo") { _avatarSync.value = AvatarSync.Idle; return }
        scope.launch {
            syncAvatar(pr)
            if (_avatarSync.value == AvatarSync.Done) send(buildJsonObject { put("type", "profile"); put("name", displayName(pr)); put("avatar", shareableAvatar(pr)) })
        }
    }

    /** Uploads the profile photo when it is new, so other listeners see it in the chat. */
    private suspend fun syncAvatar(pr: Profile) {
        if (pr.avatar != "photo") return
        val src = p.avatarPhoto() ?: return
        if (src == p.kv.get(AVATAR_SRC_KEY) && p.kv.get(AVATAR_KEY) != null) { _avatarSync.value = AvatarSync.Done; return }
        _avatarSync.value = AvatarSync.Uploading
        // the chat identity must exist on the server before it accepts a photo
        if (!_connected.value) withTimeoutOrNull(8_000) { connected.first { it } }
        val bytes = p.avatarJpeg() ?: run { _avatarSync.value = AvatarSync.Failed; return }
        val text = runCatching {
            p.httpPostBytes("$CLOUD_URL/chat/avatar", bytes, mapOf("X-Chat-Uid" to identity.uid, "X-Chat-Key" to identity.key))
        }.getOrNull()
        val avatar = text?.let { runCatching { json.parseToJsonElement(it).jsonObject.str("avatar") }.getOrNull() }
        if (avatar == null) { _avatarSync.value = AvatarSync.Failed; return }
        p.kv.set(AVATAR_KEY, avatar)
        p.kv.set(AVATAR_SRC_KEY, src)
        _avatarSync.value = AvatarSync.Done
    }
}

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

private val ReactionsSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

private fun hex(bytes: Int): String = Random.nextBytes(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
