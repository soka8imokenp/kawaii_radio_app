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
 *   admin    { code }                            turns this user into an admin (code = ADMIN_CODE secret)
 *   profile  { name, avatar }
 *   presence { live }                            listening to the LIVE broadcast or not
 * Server → client:
 *   welcome  { you, history, online, live }      history is newest last
 *   msg      { m, nonce? }
 *   deleted  { id }
 *   history  { msgs }                            newest last
 *   presence { online, live }
 *   you      { uid, admin, banned }
 *   error    { text }
 */

interface Attachment { uid?: string; admin?: boolean; banned?: boolean; live?: boolean; name?: string; avatar?: string; sent?: number[] }

interface Msg {
  id: number;
  uid: string;
  name: string;
  avatar: string;
  kind: string;
  body: string;
  reply?: { id: number; name: string; body: string; kind: string } | null;
  ts: number;
}

const HISTORY = 60;
const MAX_TEXT = 1000;
const RATE_COUNT = 8;      // at most 8 messages
const RATE_WINDOW = 20_000; // per 20 seconds

async function sha256(s: string) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

const clean = (s: unknown, max: number) => (typeof s === 'string' ? s.trim().slice(0, max) : '');

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
    `);
  }

  async fetch(req: Request): Promise<Response> {
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
    return rows.map((r) => this.row(r)).reverse();
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
      const avatar = clean(m.avatar, 16) || 'p:0';
      const user = this.sql.exec('SELECT * FROM users WHERE uid = ?', uid).toArray()[0];
      if (user && user.keyhash !== hash) return this.send(ws, { type: 'error', text: 'bu ID boshqa telefonga tegishli' });
      if (!user) this.sql.exec('INSERT INTO users (uid, keyhash, name, avatar, seen) VALUES (?, ?, ?, ?, ?)', uid, hash, name, avatar, Date.now());
      else this.sql.exec('UPDATE users SET name = ?, avatar = ?, seen = ? WHERE uid = ?', name, avatar, Date.now(), uid);
      const admin = !!user?.admin;
      const banned = !!user?.banned;
      this.setAtt(ws, { uid, admin, banned, live: !!m.live, name, avatar, sent: [] });
      const p = this.presence();
      this.send(ws, { type: 'welcome', you: { uid, admin, banned }, history: this.recent(), online: p.online, live: p.live });
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
        const sent = (a.sent ?? []).filter((t) => now - t < RATE_WINDOW);
        if (sent.length >= RATE_COUNT) return this.send(ws, { type: 'error', text: 'Juda tez! Biroz kuting' });
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
        return this.broadcast({ type: 'deleted', id: m.id });
      }
      case 'ban': {
        if (!a.admin) return;
        const uid = clean(m.uid, 64);
        const on = !!m.on;
        this.sql.exec('UPDATE users SET banned = ? WHERE uid = ?', on ? 1 : 0, uid);
        for (const other of this.ctx.getWebSockets()) {
          const oa = this.att(other);
          if (oa.uid === uid) { this.setAtt(other, { ...oa, banned: on }); this.send(other, { type: 'you', uid, admin: !!oa.admin, banned: on }); }
        }
        return;
      }
      case 'admin': {
        if (!this.env.ADMIN_CODE || clean(m.code, 128) !== this.env.ADMIN_CODE) return this.send(ws, { type: 'error', text: "Kod noto'g'ri" });
        this.sql.exec('UPDATE users SET admin = 1 WHERE uid = ?', a.uid);
        this.setAtt(ws, { ...a, admin: true });
        return this.send(ws, { type: 'you', uid: a.uid, admin: true, banned: !!a.banned });
      }
      case 'profile': {
        const name = clean(m.name, 32) || a.name || 'Mehmon';
        const avatar = clean(m.avatar, 16) || a.avatar || 'p:0';
        this.sql.exec('UPDATE users SET name = ?, avatar = ? WHERE uid = ?', name, avatar, a.uid);
        return this.setAtt(ws, { ...a, name, avatar });
      }
      case 'presence': {
        this.setAtt(ws, { ...a, live: !!m.live });
        return this.broadcast(this.presence());
      }
    }
  }

  async webSocketClose(ws: WebSocket) {
    try { ws.close(); } catch { /* already closed */ }
    this.broadcast(this.presence());
  }

  async webSocketError(ws: WebSocket) {
    await this.webSocketClose(ws);
  }
}
