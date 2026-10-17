import { DurableObject } from 'cloudflare:workers';
import type { Env } from './index';

/**
 * The global listeners' chat: one Durable Object holding every live WebSocket and the message
 * history (its own SQLite). Hibernation API: idle sockets cost nothing while nobody writes.
 *
 * Client → server (JSON):
 *   hello    { uid, key, name, avatar, live }   first message; `key` proves the uid belongs to this phone
 *   send     { kind: 'text'|'sticker'|'track', body, reply?, nonce? }
 *   history  { before }                          older messages, newest first
 *   delete   { id }                              own message, or any message for admins
 *   ban      { uid, on }                         admins only
 *   admin    { code }                            turns this user into an admin (a personal admin key, or the ADMIN_CODE secret)
 *   unadmin  {}                                  gives the admin rights back
 * Admins only:
 *   ban      { uid, on, purge? }                 block / unblock; purge also removes all their messages
 *   mute     { uid, minutes }                    no writing for a while (0 = speak again)
 *   purge    { uid }                             removes every message of a listener
 *   clear    {}                                  empties the whole chat
 *   room     { slow?, locked? }                  slow mode (seconds between messages) / only admins may write
 *   pin      { id | null }                       pins a message on top of the chat (null unpins)
 *   users    { filter? }                         listener list for the admin panel ('online' | 'banned' | 'muted' | all)
 *   stats    {}                                  numbers for the admin panel
 *   log      { mine? }                           the admin journal (newest first)
 * Owner (the main admin) only:
 *   setadmin { uid, on }                         gives / takes away admin rights
 * Admins can't touch each other (block, mute, wipe); only the owner can, and nobody touches the owner.
 *   profile  { name, avatar, card }              card = public profile (favourite artist, stats, song on now)
 *   whois    { uid }                             asks for somebody's profile page
 *   react    { id, emoji }                       toggles a reaction (❤️ 🔥 😂 😭), one per person per message
 *   typing   {}                                  the listener is typing (sent every few seconds while typing)
 *   presence { live, viewing }                   listening to LIVE / looking at the chat right now (no push then)
 * HTTP POST /chat/push { uid, key, token, on }   the phone's FCM token for chat notifications
 * HTTP POST /chat/play { uid, key, id }          a song was listened to (≥ 30 s); feeds "Hozir ko'p tinglanmoqda"
 * HTTP GET  /chat/trending                       { tracks: [{ id, n }] }: most listened songs of the last 7 days (n = listeners)
 * Server → client:
 *   welcome  { you, history, online, live, room } history is newest last
 *   room     { slow, locked, pin }               chat settings changed (pin = pinned message or null)
 *   cleared  {}                                  the whole chat was emptied
 *   purged   { uid }                             every message of this listener was removed
 *   admin    { ok, text? }                       answer to an admin login
 *   users    { users }                           answer to `users`
 *   stats    { … }                               answer to `stats`
 *   staff    { staff }                           uid → 1 (admin) / 2 (owner), sent on join and whenever it changes
 *   log      { entries }                         answer to `log`
 *   msg      { m, nonce? }
 *   deleted  { id }
 *   history  { msgs }                            newest last
 *   presence { online, live }
 *   you      { uid, admin, owner, banned, mutedUntil }
 *   user     { uid, name, avatar, admin, joined, seen, online, live, messages, card }
 *   reactions { id, reactions }                  reactions = { emoji: [uid…] }
 *   typing   { uid, name }
 *   error    { text }
 */

interface Attachment { uid?: string; admin?: boolean; owner?: boolean; banned?: boolean; live?: boolean; viewing?: boolean; name?: string; avatar?: string; sent?: number[]; typingAt?: number; mutedUntil?: number }

/**
 * Personal admin keys, stored as SHA-256 only (the keys themselves are never in the code): one per
 * admin, so a key can be replaced without touching the other. Typed in the app: Profil → Admin.
 */
/** Admin key labels with the owner role: they can give and take admin rights. */
const OWNER_LABELS = new Set(['soka']);

const ADMIN_KEYS: Record<string, string> = {
  '6c01da4f33f9935d8c31e555597b950e0fc0068b16bb8d164638abec2d01e4b9': 'soka',
  'cc8ce06ba7233a632af4023008b7c555391378898a6114bb764601ff09c0e843': 'dost',
};

interface Room { slow: number; locked: boolean; pin: Msg | null }

const EMOJI = ['❤️', '🔥', '😂', '😭'];

interface Msg {
  id: number;
  uid: string;
  name: string;
  avatar: string;
  kind: string;
  body: string;
  reply?: { id: number; name: string; body: string; kind: string } | null;
  ts: number;
  reactions?: Record<string, string[]>;
}

const HISTORY = 60;
const MAX_TEXT = 1000;
/** Listeners (not admins) write at most this much in one message: a thought, not an article. */
const USER_TEXT = 300;
const RATE_COUNT = 8;      // at most 8 messages
const RATE_WINDOW = 20_000; // per 20 seconds

async function sha256(s: string) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Chat formatting marks (**bold**, __italic__, ++underline++, ~~strike~~, `code`, ||spoiler||) taken out, e.g. for a push. */
const stripFormat = (s: string) => s.replace(/(\*\*|__|\+\+|~~|\|\||`)(.+?)\1/g, (_, mark, inner) => (mark === '||' ? '▒▒▒' : inner));

const clean = (s: unknown, max: number) => (typeof s === 'string' ? s.trim().slice(0, max) : '');

/** The public profile a phone shares (kept small); null when none was sent. */
function cardOf(c: unknown): string | null {
  if (!c || typeof c !== 'object') return null;
  const o = c as Record<string, unknown>;
  const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) && v >= 0 ? Math.round(v) : 0);
  const card = {
    fav: clean(o.fav, 60) || null,
    likes: num(o.likes),
    plays: num(o.plays),
    minutes: num(o.minutes),
    top: Array.isArray(o.top) ? o.top.slice(0, 3).map((x) => clean(x, 60)).filter(Boolean) : [],
    now: clean(o.now, 80) || null,
  };
  return JSON.stringify(card);
}

export class ChatRoom extends DurableObject<Env> {
  private sql: SqlStorage;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, name TEXT, avatar TEXT,
        kind TEXT, body TEXT, reply TEXT, ts INTEGER, deleted INTEGER DEFAULT 0);
      CREATE TABLE IF NOT EXISTS users (
        uid TEXT PRIMARY KEY, keyhash TEXT, name TEXT, avatar TEXT,
        admin INTEGER DEFAULT 0, banned INTEGER DEFAULT 0, seen INTEGER);
      CREATE TABLE IF NOT EXISTS push (uid TEXT PRIMARY KEY, token TEXT, enabled INTEGER DEFAULT 1);
      CREATE TABLE IF NOT EXISTS reactions (mid INTEGER, uid TEXT, emoji TEXT, PRIMARY KEY (mid, uid));
      CREATE TABLE IF NOT EXISTS plays (day INTEGER, tid TEXT, uid TEXT, PRIMARY KEY (day, tid, uid));
      CREATE TABLE IF NOT EXISTS adminlog (
        id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, actor TEXT, actorName TEXT,
        action TEXT, target TEXT, targetName TEXT, detail TEXT);
    `);
    // columns added later: ignore "duplicate column" on rooms that already have them
    for (const col of ['card TEXT', 'created INTEGER', 'muted INTEGER DEFAULT 0']) {
      try { this.sql.exec(`ALTER TABLE users ADD COLUMN ${col}`); } catch { /* already there */ }
    }
  }

  async fetch(req: Request): Promise<Response> {
    if (req.method === 'POST' && new URL(req.url).pathname.endsWith('/push')) return this.registerPush(req);
    if (req.method === 'POST' && new URL(req.url).pathname.endsWith('/avatar')) return this.uploadAvatar(req);
    if (req.method === 'POST' && new URL(req.url).pathname.endsWith('/play')) return this.recordPlay(req);
    if (req.method === 'GET' && new URL(req.url).pathname.endsWith('/trending')) return this.trending();
    if (req.method === 'GET' && new URL(req.url).pathname.endsWith('/status')) {
      // diagnostics (the Worker only lets this through with the upload token)
      const tokens = this.sql.exec('SELECT COUNT(*) AS n FROM push').one().n;
      const enabled = this.sql.exec('SELECT COUNT(*) AS n FROM push WHERE enabled = 1').one().n;
      const users = this.sql.exec('SELECT COUNT(*) AS n FROM users').one().n;
      return Response.json({
        users, pushTokens: tokens, pushEnabled: enabled, hasFirebaseKey: !!this.env.FCM_SERVICE_ACCOUNT,
        online: this.ctx.getWebSockets().length, lastPush: (await this.ctx.storage.get('lastPush')) ?? null,
      });
    }
    if (req.headers.get('Upgrade') !== 'websocket') return new Response('expected websocket', { status: 426 });
    const pair = new WebSocketPair();
    this.ctx.acceptWebSocket(pair[1]);
    pair[1].serializeAttachment({} satisfies Attachment);
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  /* ---------- helpers ---------- */

  private att(ws: WebSocket): Attachment { return (ws.deserializeAttachment() as Attachment) ?? {}; }
  private setAtt(ws: WebSocket, a: Attachment) { ws.serializeAttachment(a); }
  private send(ws: WebSocket, data: unknown) { try { ws.send(JSON.stringify(data)); } catch { /* closed */ } }
  private broadcast(data: unknown) { const s = JSON.stringify(data); for (const ws of this.ctx.getWebSockets()) { try { ws.send(s); } catch { /* closed */ } } }

  private async room(): Promise<Room> {
    return ((await this.ctx.storage.get('room')) as Room | undefined) ?? { slow: 0, locked: false, pin: null };
  }

  private async setRoom(change: Partial<Room>) {
    const r = { ...(await this.room()), ...change };
    await this.ctx.storage.put('room', r);
    this.broadcast({ type: 'room', ...r });
  }

  /** Tells every open socket of [uid] about its new rights (admin / banned / muted). */
  private updateUser(uid: string, change: Partial<Attachment>) {
    for (const other of this.ctx.getWebSockets()) {
      const oa = this.att(other);
      if (oa.uid !== uid) continue;
      const na = { ...oa, ...change };
      this.setAtt(other, na);
      this.send(other, { type: 'you', uid, admin: !!na.admin, owner: !!na.owner, banned: !!na.banned, mutedUntil: na.mutedUntil ?? 0 });
    }
  }

  /** 0 = listener, 1 = admin, 2 = owner. */
  private role(uid: string): number {
    return Number(this.sql.exec('SELECT admin FROM users WHERE uid = ?', uid).toArray()[0]?.admin ?? 0);
  }

  /** Admins and the owner, uid → role. */
  private staff(): Record<string, number> {
    const out: Record<string, number> = {};
    for (const r of this.sql.exec('SELECT uid, admin FROM users WHERE admin > 0').toArray()) out[r.uid as string] = Number(r.admin);
    return out;
  }

  /**
   * May [a] block / mute / wipe [uid]? Never themselves; other admins only when [a] is the owner;
   * the owner never. Says why not to [ws].
   */
  private canTouch(ws: WebSocket, a: Attachment, uid: string): boolean {
    if (!uid) return false;
    if (uid === a.uid) { this.send(ws, { type: 'error', text: "O'zingizga qo'llab bo'lmaydi" }); return false; }
    const r = this.role(uid);
    if (r === 2 || (r === 1 && !a.owner)) { this.send(ws, { type: 'error', text: "Adminga qo'llab bo'lmaydi" }); return false; }
    return true;
  }

  /** One line in the admin journal. */
  private log(a: Attachment, action: string, target?: string, detail?: string) {
    const targetName = target ? String(this.sql.exec('SELECT name FROM users WHERE uid = ?', target).toArray()[0]?.name ?? '') : null;
    this.sql.exec(
      'INSERT INTO adminlog (ts, actor, actorName, action, target, targetName, detail) VALUES (?, ?, ?, ?, ?, ?, ?)',
      Date.now(), a.uid ?? '', a.name ?? '', action, target ?? null, targetName, detail ?? null,
    );
    if (Math.random() < 0.05) this.sql.exec('DELETE FROM adminlog WHERE id < (SELECT MAX(id) - 2000 FROM adminlog)');
  }

  private presence() {
    const people = new Map<string, boolean>();
    for (const ws of this.ctx.getWebSockets()) {
      const a = this.att(ws);
      if (a.uid) people.set(a.uid, (people.get(a.uid) ?? false) || !!a.live);
    }
    return { type: 'presence', online: people.size, live: [...people.values()].filter(Boolean).length };
  }

  private row(r: Record<string, SqlStorageValue>): Msg {
    return {
      id: r.id as number, uid: r.uid as string, name: r.name as string, avatar: r.avatar as string,
      kind: r.kind as string, body: r.body as string, reply: r.reply ? JSON.parse(r.reply as string) : null, ts: r.ts as number,
    };
  }

  private recent(before?: number): Msg[] {
    const rows = before
      ? this.sql.exec('SELECT * FROM messages WHERE deleted = 0 AND id < ? ORDER BY id DESC LIMIT ?', before, HISTORY).toArray()
      : this.sql.exec('SELECT * FROM messages WHERE deleted = 0 ORDER BY id DESC LIMIT ?', HISTORY).toArray();
    const msgs = rows.map((r) => this.row(r)).reverse();
    if (msgs.length) {
      const lo = msgs[0].id;
      const hi = msgs[msgs.length - 1].id;
      const byId = new Map(msgs.map((m) => [m.id, m]));
      for (const r of this.sql.exec('SELECT mid, uid, emoji FROM reactions WHERE mid BETWEEN ? AND ?', lo, hi).toArray()) {
        const m = byId.get(r.mid as number);
        if (!m) continue;
        m.reactions ??= {};
        (m.reactions[r.emoji as string] ??= []).push(r.uid as string);
      }
    }
    return msgs;
  }

  private reactionsOf(mid: number): Record<string, string[]> {
    const out: Record<string, string[]> = {};
    for (const r of this.sql.exec('SELECT uid, emoji FROM reactions WHERE mid = ?', mid).toArray()) (out[r.emoji as string] ??= []).push(r.uid as string);
    return out;
  }

  /** Who is called by "@name" in the text (names are matched whole, case-insensitive), plus the author replied to. */
  private mentioned(body: string, replyTo: number | null, self: string): Set<string> {
    const out = new Set<string>();
    const text = body.toLowerCase();
    if (text.includes('@')) {
      for (const u of this.sql.exec('SELECT uid, name FROM users').toArray()) {
        const n = String(u.name ?? '').toLowerCase();
        if (n.length < 2) continue;
        const at = text.indexOf('@' + n);
        const end = text.charAt(at + 1 + n.length);
        if (at >= 0 && (end === '' || !/[\p{L}\p{N}]/u.test(end))) out.add(u.uid as string);
      }
    }
    if (replyTo != null) {
      const r = this.sql.exec('SELECT uid FROM messages WHERE id = ?', replyTo).toArray()[0];
      if (r) out.add(r.uid as string);
    }
    out.delete(self);
    return out;
  }

  /* ---------- socket events ---------- */

  async webSocketMessage(ws: WebSocket, raw: string | ArrayBuffer) {
    let m: Record<string, unknown>;
    try { m = JSON.parse(typeof raw === 'string' ? raw : new TextDecoder().decode(raw)); } catch { return; }
    const a = this.att(ws);

    if (m.type === 'hello') {
      const uid = clean(m.uid, 64);
      const key = clean(m.key, 128);
      if (uid.length < 8 || key.length < 16) return this.send(ws, { type: 'error', text: 'bad hello' });
      const hash = await sha256(key);
      const name = clean(m.name, 32) || 'Mehmon';
      const avatar = clean(m.avatar, 80) || 'p:0';
      const user = this.sql.exec('SELECT * FROM users WHERE uid = ?', uid).toArray()[0];
      if (user && user.keyhash !== hash) return this.send(ws, { type: 'error', text: 'bu ID boshqa telefonga tegishli' });
      const card = cardOf(m.card);
      if (!user) this.sql.exec('INSERT INTO users (uid, keyhash, name, avatar, seen, card, created) VALUES (?, ?, ?, ?, ?, ?, ?)', uid, hash, name, avatar, Date.now(), card, Date.now());
      else this.sql.exec('UPDATE users SET name = ?, avatar = ?, seen = ?, card = COALESCE(?, card), created = COALESCE(created, ?) WHERE uid = ?', name, avatar, Date.now(), card, Date.now(), uid);
      const admin = Number(user?.admin ?? 0) > 0;
      const owner = Number(user?.admin ?? 0) === 2;
      const banned = !!user?.banned;
      const mutedUntil = Number(user?.muted ?? 0) > Date.now() ? Number(user?.muted) : 0;
      this.setAtt(ws, { uid, admin, owner, banned, mutedUntil, live: !!m.live, name, avatar, sent: [] });
      const p = this.presence();
      this.send(ws, {
        type: 'welcome', you: { uid, admin, owner, banned, mutedUntil }, history: this.recent(), online: p.online, live: p.live,
        room: await this.room(), staff: this.staff(),
      });
      this.broadcast(this.presence());
      return;
    }
    if (!a.uid) return this.send(ws, { type: 'error', text: 'hello first' });

    switch (m.type) {
      case 'send': {
        if (a.banned) return this.send(ws, { type: 'error', text: "Siz chatda bloklangansiz" });
        const kind = m.kind === 'sticker' || m.kind === 'track' ? (m.kind as string) : 'text';
        const body = clean(m.body, kind === 'text' ? MAX_TEXT : 200);
        if (!body) return;
        const now = Date.now();
        if (!a.admin && kind === 'text' && typeof m.body === 'string' && m.body.trim().length > USER_TEXT) {
          return this.send(ws, { type: 'error', text: `Xabar juda uzun: ${USER_TEXT} belgigacha` });
        }
        if (!a.admin) {
          if ((a.mutedUntil ?? 0) > now) return this.send(ws, { type: 'error', text: `Siz ${Math.ceil(((a.mutedUntil ?? 0) - now) / 60_000)} daqiqa yoza olmaysiz` });
          const room = await this.room();
          if (room.locked) return this.send(ws, { type: 'error', text: 'Chat vaqtincha yopiq' });
          const last = (a.sent ?? []).at(-1) ?? 0;
          if (room.slow > 0 && now - last < room.slow * 1000) return this.send(ws, { type: 'error', text: `Sekin rejim: ${Math.ceil((room.slow * 1000 - (now - last)) / 1000)} soniya kuting` });
        }
        const sent = (a.sent ?? []).filter((t) => now - t < Math.max(RATE_WINDOW, 120_000));
        if (sent.filter((t) => now - t < RATE_WINDOW).length >= RATE_COUNT) return this.send(ws, { type: 'error', text: 'Juda tez! Biroz kuting' });
        sent.push(now);
        this.setAtt(ws, { ...a, sent });

        let reply: Msg['reply'] = null;
        if (typeof m.reply === 'number') {
          const r = this.sql.exec('SELECT id, name, body, kind FROM messages WHERE id = ? AND deleted = 0', m.reply).toArray()[0];
          if (r) reply = { id: r.id as number, name: r.name as string, body: String(r.body).slice(0, 120), kind: r.kind as string };
        }
        const id = this.sql.exec(
          'INSERT INTO messages (uid, name, avatar, kind, body, reply, ts) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id',
          a.uid, a.name ?? 'Mehmon', a.avatar ?? 'p:0', kind, body, reply ? JSON.stringify(reply) : null, now,
        ).one().id as number;
        const msg: Msg = { id, uid: a.uid, name: a.name ?? 'Mehmon', avatar: a.avatar ?? 'p:0', kind, body, reply, ts: now };
        // the sender gets its nonce back to replace the pending bubble
        for (const other of this.ctx.getWebSockets()) this.send(other, { type: 'msg', m: msg, nonce: other === ws ? m.nonce : undefined });
        const called = kind === 'text' ? this.mentioned(body, reply?.id ?? null, a.uid) : this.mentioned('', reply?.id ?? null, a.uid);
        this.ctx.waitUntil(this.notify(msg, called).catch(() => {}));
        return;
      }
      case 'react': {
        const id = typeof m.id === 'number' ? m.id : -1;
        // an unknown emoji is ignored; no emoji at all removes the listener's reaction
        if (m.emoji != null && !(typeof m.emoji === 'string' && EMOJI.includes(m.emoji))) return;
        const emoji = typeof m.emoji === 'string' ? m.emoji : null;
        if (id < 0 || a.banned) return;
        if (!this.sql.exec('SELECT 1 FROM messages WHERE id = ? AND deleted = 0', id).toArray().length) return;
        const had = this.sql.exec('SELECT emoji FROM reactions WHERE mid = ? AND uid = ?', id, a.uid).toArray()[0];
        if (!emoji || had?.emoji === emoji) this.sql.exec('DELETE FROM reactions WHERE mid = ? AND uid = ?', id, a.uid);
        else this.sql.exec('INSERT INTO reactions (mid, uid, emoji) VALUES (?, ?, ?) ON CONFLICT(mid, uid) DO UPDATE SET emoji = excluded.emoji', id, a.uid, emoji);
        return this.broadcast({ type: 'reactions', id, reactions: this.reactionsOf(id) });
      }
      case 'typing': {
        const now = Date.now();
        if (a.banned || (a.typingAt && now - a.typingAt < 2_000)) return;
        this.setAtt(ws, { ...a, typingAt: now });
        for (const other of this.ctx.getWebSockets()) if (other !== ws) this.send(other, { type: 'typing', uid: a.uid, name: a.name });
        return;
      }
      case 'history': {
        const before = typeof m.before === 'number' ? m.before : undefined;
        return this.send(ws, { type: 'history', msgs: this.recent(before) });
      }
      case 'delete': {
        const r = this.sql.exec('SELECT uid FROM messages WHERE id = ?', m.id).toArray()[0];
        if (!r || (r.uid !== a.uid && !a.admin)) return;
        this.sql.exec('UPDATE messages SET deleted = 1 WHERE id = ?', m.id);
        if (r.uid !== a.uid) this.log(a, 'delete', r.uid as string);
        if ((await this.room()).pin?.id === m.id) await this.setRoom({ pin: null });
        return this.broadcast({ type: 'deleted', id: m.id });
      }
      case 'ban': {
        if (!a.admin) return;
        const uid = clean(m.uid, 64);
        const on = !!m.on;
        if (!this.canTouch(ws, a, uid)) return;
        this.sql.exec('UPDATE users SET banned = ? WHERE uid = ?', on ? 1 : 0, uid);
        this.updateUser(uid, { banned: on });
        if (on && m.purge) this.purge(uid);
        this.log(a, on ? 'ban' : 'unban', uid, on && m.purge ? 'xabarlari bilan' : undefined);
        return this.send(ws, { type: 'error', text: on ? 'Foydalanuvchi bloklandi' : 'Blokdan chiqarildi' });
      }
      case 'mute': {
        if (!a.admin) return;
        const uid = clean(m.uid, 64);
        if (!this.canTouch(ws, a, uid)) return;
        const minutes = typeof m.minutes === 'number' ? Math.max(0, Math.min(m.minutes, 7 * 24 * 60)) : 0;
        const until = minutes > 0 ? Date.now() + minutes * 60_000 : 0;
        this.sql.exec('UPDATE users SET muted = ? WHERE uid = ?', until, uid);
        this.updateUser(uid, { mutedUntil: until });
        this.log(a, until ? 'mute' : 'unmute', uid, until ? `${minutes} daq` : undefined);
        return this.send(ws, { type: 'error', text: until ? `${minutes} daqiqaga ovozsiz qilindi` : 'Yana yoza oladi' });
      }
      case 'purge': {
        if (!a.admin) return;
        const uid = clean(m.uid, 64);
        if (!this.canTouch(ws, a, uid)) return;
        this.purge(uid);
        this.log(a, 'purge', uid);
        return this.send(ws, { type: 'error', text: "Xabarlari o'chirildi" });
      }
      case 'clear': {
        if (!a.admin) return;
        this.sql.exec('UPDATE messages SET deleted = 1 WHERE deleted = 0');
        this.sql.exec('DELETE FROM reactions');
        await this.setRoom({ pin: null });
        this.log(a, 'clear');
        return this.broadcast({ type: 'cleared' });
      }
      case 'room': {
        if (!a.admin) return;
        const change: Partial<Room> = {};
        if (typeof m.slow === 'number') change.slow = Math.max(0, Math.min(Math.round(m.slow), 600));
        if (typeof m.locked === 'boolean') change.locked = m.locked;
        if (change.slow !== undefined) this.log(a, 'slow', undefined, change.slow ? `${change.slow} s` : "o'chdi");
        if (change.locked !== undefined) this.log(a, change.locked ? 'lock' : 'unlock');
        return this.setRoom(change);
      }
      case 'pin': {
        if (!a.admin) return;
        if (m.id == null) { this.log(a, 'unpin'); return this.setRoom({ pin: null }); }
        const r = this.sql.exec('SELECT * FROM messages WHERE id = ? AND deleted = 0', m.id).toArray()[0];
        if (!r) return;
        this.log(a, 'pin', r.uid as string, String(r.body).slice(0, 60));
        return this.setRoom({ pin: this.row(r) });
      }
      case 'users': {
        if (!a.admin) return;
        const online = new Map<string, boolean>();
        for (const other of this.ctx.getWebSockets()) {
          const oa = this.att(other);
          if (oa.uid) online.set(oa.uid, (online.get(oa.uid) ?? false) || !!oa.live);
        }
        const now = Date.now();
        const rows = this.sql.exec(`
          SELECT u.uid, u.name, u.avatar, u.admin, u.banned, u.muted, u.seen, u.created,
                 (SELECT COUNT(*) FROM messages WHERE uid = u.uid AND deleted = 0) AS messages
          FROM users u ORDER BY u.seen DESC LIMIT 300`).toArray();
        let users = rows.map((u) => ({
          uid: u.uid, name: u.name, avatar: u.avatar, admin: Number(u.admin ?? 0) > 0, owner: Number(u.admin ?? 0) === 2, banned: !!u.banned,
          mutedUntil: Number(u.muted ?? 0) > now ? Number(u.muted) : 0, seen: u.seen ?? 0, joined: u.created ?? u.seen ?? 0,
          messages: u.messages, online: online.has(u.uid as string), live: online.get(u.uid as string) ?? false,
        }));
        if (m.filter === 'online') users = users.filter((u) => u.online);
        else if (m.filter === 'banned') users = users.filter((u) => u.banned);
        else if (m.filter === 'muted') users = users.filter((u) => u.mutedUntil > 0);
        return this.send(ws, { type: 'users', users });
      }
      case 'stats': {
        if (!a.admin) return;
        const one = (q: string, ...b: SqlStorageValue[]) => Number(this.sql.exec(q, ...b).one().n);
        const day = Date.now() - 24 * 3600_000;
        const p = this.presence();
        return this.send(ws, {
          type: 'stats', online: p.online, live: p.live,
          users: one('SELECT COUNT(*) AS n FROM users'),
          banned: one('SELECT COUNT(*) AS n FROM users WHERE banned = 1'),
          muted: one('SELECT COUNT(*) AS n FROM users WHERE muted > ?', Date.now()),
          messagesToday: one('SELECT COUNT(*) AS n FROM messages WHERE ts > ? AND deleted = 0', day),
          messages: one('SELECT COUNT(*) AS n FROM messages WHERE deleted = 0'),
          activeToday: one('SELECT COUNT(*) AS n FROM users WHERE seen > ?', day),
        });
      }
      case 'admin': {
        const code = clean(m.code, 128);
        const label = code ? ADMIN_KEYS[await sha256(code)] : undefined;
        const ok = !!label || (!!code && !!this.env.ADMIN_CODE && code === this.env.ADMIN_CODE);
        if (!ok) return this.send(ws, { type: 'admin', ok: false, text: "Kalit noto'g'ri" });
        const owner = !!label && OWNER_LABELS.has(label);
        this.sql.exec('UPDATE users SET admin = ? WHERE uid = ?', owner ? 2 : 1, a.uid);
        this.updateUser(a.uid, { admin: true, owner });
        this.log({ ...a, admin: true }, 'login', undefined, owner ? 'bosh admin' : undefined);
        this.broadcast({ type: 'staff', staff: this.staff() });
        return this.send(ws, { type: 'admin', ok: true });
      }
      case 'unadmin': {
        if (a.owner) return this.send(ws, { type: 'error', text: "Bosh admin chiqa olmaydi" });
        this.sql.exec('UPDATE users SET admin = 0 WHERE uid = ?', a.uid);
        this.updateUser(a.uid, { admin: false, owner: false });
        this.log(a, 'logout');
        return this.broadcast({ type: 'staff', staff: this.staff() });
      }
      case 'setadmin': {
        if (!a.owner) return this.send(ws, { type: 'error', text: 'Faqat bosh admin' });
        const uid = clean(m.uid, 64);
        if (!uid || uid === a.uid || this.role(uid) === 2) return;
        if (!this.sql.exec('SELECT 1 FROM users WHERE uid = ?', uid).toArray().length) return;
        const on = !!m.on;
        this.sql.exec('UPDATE users SET admin = ? WHERE uid = ?', on ? 1 : 0, uid);
        this.updateUser(uid, { admin: on, owner: false });
        this.log(a, on ? 'grant' : 'revoke', uid);
        this.broadcast({ type: 'staff', staff: this.staff() });
        return this.send(ws, { type: 'error', text: on ? 'Admin qilindi' : 'Adminlikdan olindi' });
      }
      case 'log': {
        if (!a.admin) return;
        const rows = m.mine
          ? this.sql.exec('SELECT * FROM adminlog WHERE actor = ? ORDER BY id DESC LIMIT 150', a.uid).toArray()
          : this.sql.exec('SELECT * FROM adminlog ORDER BY id DESC LIMIT 150').toArray();
        return this.send(ws, { type: 'log', entries: rows });
      }
      case 'profile': {
        const name = clean(m.name, 32) || a.name || 'Mehmon';
        const avatar = clean(m.avatar, 80) || a.avatar || 'p:0';
        this.sql.exec('UPDATE users SET name = ?, avatar = ?, card = COALESCE(?, card), seen = ? WHERE uid = ?', name, avatar, cardOf(m.card), Date.now(), a.uid);
        return this.setAtt(ws, { ...a, name, avatar });
      }
      case 'whois': {
        const uid = clean(m.uid, 64);
        const u = this.sql.exec('SELECT * FROM users WHERE uid = ?', uid).toArray()[0];
        if (!u) return this.send(ws, { type: 'error', text: 'Foydalanuvchi topilmadi' });
        const count = this.sql.exec('SELECT COUNT(*) AS n FROM messages WHERE uid = ? AND deleted = 0', uid).one().n as number;
        let online = false;
        let live = false;
        for (const other of this.ctx.getWebSockets()) {
          const oa = this.att(other);
          if (oa.uid === uid) { online = true; live = live || !!oa.live; }
        }
        return this.send(ws, {
          type: 'user', uid, name: u.name, avatar: u.avatar, admin: Number(u.admin ?? 0) > 0, owner: Number(u.admin ?? 0) === 2, banned: !!u.banned,
          mutedUntil: Number(u.muted ?? 0) > Date.now() ? Number(u.muted) : 0,
          joined: u.created ?? u.seen, seen: u.seen, online, live, messages: count,
          card: u.card ? JSON.parse(u.card as string) : null,
        });
      }
      case 'presence': {
        this.setAtt(ws, { ...a, live: !!m.live, viewing: !!m.viewing });
        return this.broadcast(this.presence());
      }
    }
  }

  /** Removes every message of [uid] for everybody. */
  private purge(uid: string) {
    if (!uid) return;
    this.sql.exec('UPDATE messages SET deleted = 1 WHERE uid = ? AND deleted = 0', uid);
    this.broadcast({ type: 'purged', uid });
  }

  /* ---------- what people listen to: one row per listener, song and day ---------- */

  private async recordPlay(req: Request): Promise<Response> {
    const b = (await req.json().catch(() => ({}))) as Record<string, unknown>;
    const uid = clean(b.uid, 64);
    const key = clean(b.key, 128);
    const tid = clean(b.id, 80);
    const user = this.sql.exec('SELECT keyhash FROM users WHERE uid = ?', uid).toArray()[0];
    if (!tid || !user || user.keyhash !== (await sha256(key))) return Response.json({ ok: false }, { status: 403 });
    const day = Math.floor(Date.now() / 86_400_000);
    this.sql.exec('INSERT OR IGNORE INTO plays (day, tid, uid) VALUES (?, ?, ?)', day, tid, uid);
    // keep a month at most
    if (Math.random() < 0.02) this.sql.exec('DELETE FROM plays WHERE day < ?', day - 30);
    return Response.json({ ok: true });
  }

  private trending(): Response {
    const since = Math.floor(Date.now() / 86_400_000) - 7;
    const tracks = this.sql.exec(
      'SELECT tid AS id, COUNT(DISTINCT uid) AS n FROM plays WHERE day >= ? GROUP BY tid ORDER BY n DESC, MAX(day) DESC LIMIT 20', since,
    ).toArray();
    return Response.json({ tracks }, { headers: { 'Cache-Control': 'max-age=300', 'Access-Control-Allow-Origin': '*' } });
  }

  /* ---------- avatars: a listener's own photo, shown to everybody ---------- */

  private async uploadAvatar(req: Request): Promise<Response> {
    const uid = clean(req.headers.get('X-Chat-Uid'), 64);
    const key = clean(req.headers.get('X-Chat-Key'), 128);
    const user = this.sql.exec('SELECT keyhash FROM users WHERE uid = ?', uid).toArray()[0];
    if (!user || user.keyhash !== (await sha256(key))) return Response.json({ ok: false, error: 'wrong key' }, { status: 403 });
    const body = await req.arrayBuffer();
    if (!body.byteLength || body.byteLength > 300 * 1024) return Response.json({ ok: false, error: 'photo must be under 300 KB' }, { status: 413 });
    await this.env.BUCKET.put(`avatars/${uid}.jpg`, body, { httpMetadata: { contentType: 'image/jpeg' } });
    // the version in the value makes every phone fetch the new photo instead of a cached one
    const avatar = `u:${uid}:${Date.now().toString(36)}`;
    this.sql.exec('UPDATE users SET avatar = ? WHERE uid = ?', avatar, uid);
    return Response.json({ ok: true, avatar });
  }

  /* ---------- push notifications (Firebase Cloud Messaging) ---------- */

  private async registerPush(req: Request): Promise<Response> {
    const b = (await req.json().catch(() => ({}))) as Record<string, unknown>;
    const uid = clean(b.uid, 64);
    const key = clean(b.key, 128);
    const token = clean(b.token, 4096);
    if (uid.length < 8 || key.length < 16 || !token) return Response.json({ ok: false, error: 'bad request' }, { status: 400 });
    const hash = await sha256(key);
    const user = this.sql.exec('SELECT keyhash FROM users WHERE uid = ?', uid).toArray()[0];
    if (user && user.keyhash !== hash) return Response.json({ ok: false, error: 'wrong key' }, { status: 403 });
    if (!user) this.sql.exec('INSERT INTO users (uid, keyhash, name, avatar, seen) VALUES (?, ?, ?, ?, ?)', uid, hash, 'Mehmon', 'p:0', Date.now());
    // one phone, one token: a token that moved to another account is taken over
    this.sql.exec('DELETE FROM push WHERE token = ? AND uid != ?', token, uid);
    this.sql.exec('INSERT INTO push (uid, token, enabled) VALUES (?, ?, ?) ON CONFLICT(uid) DO UPDATE SET token = excluded.token, enabled = excluded.enabled', uid, token, b.on === false ? 0 : 1);
    return Response.json({ ok: true });
  }

  private fcmToken: { value: string; until: number } | null = null;

  /** OAuth access token for FCM from the service account (a signed JWT exchanged at Google). */
  private async accessToken(sa: { client_email: string; private_key: string }): Promise<string> {
    if (this.fcmToken && this.fcmToken.until > Date.now() + 60_000) return this.fcmToken.value;
    const b64 = (o: unknown) => btoa(typeof o === 'string' ? o : JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
    const now = Math.floor(Date.now() / 1000);
    const unsigned = `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64({
      iss: sa.client_email, scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600,
    })}`;
    const pem = sa.private_key.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
    const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    const key = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
    const sig = new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned)));
    const jwt = `${unsigned}.${b64(String.fromCharCode(...sig))}`;
    const r = (await (await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
    })).json()) as { access_token?: string; expires_in?: number };
    if (!r.access_token) throw new Error('FCM auth failed');
    this.fcmToken = { value: r.access_token, until: Date.now() + (r.expires_in ?? 3600) * 1000 };
    return r.access_token;
  }

  /** Sends the new message to every phone that wants chat notifications, except its author and people looking at the chat. */
  private async notify(msg: Msg, called: Set<string> = new Set()) {
    if (!this.env.FCM_SERVICE_ACCOUNT) { await this.ctx.storage.put('lastPush', { at: Date.now(), error: 'FCM_SERVICE_ACCOUNT secret is missing' }); return; }
    const sa = JSON.parse(this.env.FCM_SERVICE_ACCOUNT) as { client_email: string; private_key: string; project_id: string };
    const watching = new Set(this.ctx.getWebSockets().map((w) => this.att(w)).filter((x) => x.viewing && x.uid).map((x) => x.uid!));
    const targets = this.sql.exec(
      'SELECT p.uid, p.token FROM push p JOIN users u ON u.uid = p.uid WHERE p.enabled = 1 AND u.banned = 0 AND p.uid != ?', msg.uid,
    ).toArray().filter((r) => !watching.has(r.uid as string));
    if (!targets.length) return;
    let access: string;
    try { access = await this.accessToken(sa); } catch (e) {
      await this.ctx.storage.put('lastPush', { at: Date.now(), error: `auth: ${(e as Error).message}` });
      return;
    }
    const results: string[] = [];
    const text = msg.kind === 'sticker' ? 'Stiker' : msg.kind === 'track' ? "♪ Qo'shiq ulashdi" : stripFormat(msg.body);
    // data-only: the app draws the notification itself (chat style, grouped, our logo)
    const data = { type: 'chat', mid: String(msg.id), name: msg.name, avatar: msg.avatar, kind: msg.kind, text: text.slice(0, 300), ts: String(msg.ts) };
    await Promise.all(targets.map(async (t) => {
      const mention = called.has(t.uid as string) ? '1' : '0';
      const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${access}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: { token: t.token, data: { ...data, mention }, android: { priority: 'HIGH', ttl: '86400s' } } }),
      });
      results.push(res.ok ? 'ok' : `${res.status} ${(await res.text()).slice(0, 200)}`);
      // phone uninstalled the app or the token expired: forget it
      if (res.status === 404 || res.status === 400) this.sql.exec('DELETE FROM push WHERE uid = ? AND token = ?', t.uid, t.token);
    }));
    await this.ctx.storage.put('lastPush', { at: Date.now(), sent: targets.length, results });
  }

  async webSocketClose(ws: WebSocket) {
    try { ws.close(); } catch { /* already closed */ }
    this.broadcast(this.presence());
  }

  async webSocketError(ws: WebSocket) {
    await this.webSocketClose(ws);
  }
}
