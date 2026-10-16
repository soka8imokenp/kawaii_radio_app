/**
 * Kawaii Radio cloud: one Cloudflare Worker in front of one R2 bucket.
 *
 *   GET    /playlist.json           public  – tracks the app shows (merged after the built-in ones)
 *   GET    /files/<key>             public  – audio / covers, with Range support for seeking
 *   GET    /radio                   public  – the live broadcast: which song plays when (same for everyone)
 *   GET    /chat                    public  – WebSocket of the global listeners' chat (see chat.ts)
 *   PUT    /api/stickers/<file>     token   – upload a sticker file (or pack.json) to stickers/
 *   PUT    /api/data/<name>.json    token   – app data (artists.json…), served at /files/data/<name>.json
 *   POST   /api/tracks              token   – add a track (multipart or JSON with *_url fields)
 *   PATCH  /api/tracks/<id>         token   – change fields of a track (lyrics, info, category…)
 *   PUT    /api/tracks/<id>/cover   token   – replace the cover (multipart `cover` or JSON `cover_url`)
 *   DELETE /api/tracks/<id>         token   – remove a track and its files
 *
 * Writes need `Authorization: Bearer <UPLOAD_TOKEN>` (a Worker secret).
 */

import { ChatRoom } from './chat';
export { ChatRoom };

export interface Env {
  BUCKET: R2Bucket;
  UPLOAD_TOKEN: string;
  /** Typed once in the app (Profil) to become a chat admin. */
  ADMIN_CODE: string;
  /** Firebase service account JSON (secret) for chat push notifications; pushes are off without it. */
  FCM_SERVICE_ACCOUNT?: string;
  CHAT: DurableObjectNamespace<ChatRoom>;
}

interface Track {
  id: string;
  title: string;
  artist: string;
  category: string;
  coverArt: string;
  audioSrc: string;
  duration: number;
  bpm: number;
  energy: number;
  lyrics: { uzbek: string; romaji?: string; kanji?: string } | null;
  info?: { description: string; details: { title: string; text: string }[] };
  addedAt?: string;
}

interface Playlist { version: number; updatedAt: string; tracks: Track[] }

const PLAYLIST_KEY = 'playlist.json';
const DEFAULT_COVER = '/files/covers/default.webp';
const MAX_AUDIO = 50 * 1024 * 1024;
const MAX_COVER = 8 * 1024 * 1024;

const CORS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, HEAD, POST, PATCH, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type, Range',
  'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges, ETag',
};

class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

const json = (data: unknown, status = 200, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS, ...extra },
  });

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
    try {
      return await route(req, env);
    } catch (e) {
      if (e instanceof HttpError) return json({ ok: false, error: e.message }, e.status);
      return json({ ok: false, error: `internal error: ${(e as Error).message}` }, 500);
    }
  },
};

async function route(req: Request, env: Env): Promise<Response> {
  const url = new URL(req.url);
  const path = decodeURIComponent(url.pathname);
  const m = req.method;

  if (path === '/' && m === 'GET') {
    return json({ ok: true, service: 'kawaii-radio-cloud', playlist: '/playlist.json', docs: 'see cloud/AGENT.md in the app repo' });
  }
  if (path === '/chat' || path === '/chat/push' || path === '/chat/avatar') {
    return env.CHAT.get(env.CHAT.idFromName('global')).fetch(req);
  }
  if (path === '/radio' && m === 'GET') {
    return json(await radio(env), 200, { 'Cache-Control': 'no-store' });
  }
  if ((path === '/playlist.json' || path === '/api/tracks') && (m === 'GET' || m === 'HEAD')) {
    const pl = await readPlaylist(env);
    return json(pl.data, 200, { 'Cache-Control': 'no-cache' });
  }
  if (path.startsWith('/files/') && (m === 'GET' || m === 'HEAD')) {
    return serveFile(req, env, path.slice('/files/'.length));
  }

  if (path.startsWith('/api/')) {
    auth(req, env);
    const seg = path.split('/').filter(Boolean); // ['api','tracks',id?,'cover'?]
    // app data files (e.g. artists.json): stored in data/, read by the app from /files/data/<name>.json
    if (seg[1] === 'data' && seg[2] && m === 'PUT') {
      const name = seg[2];
      if (!/^[\w-]+\.json$/.test(name)) throw new HttpError(400, 'data file must be <name>.json');
      const body = await req.text();
      try { JSON.parse(body); } catch { throw new HttpError(400, 'not valid JSON'); }
      if (body.length > 2 * 1024 * 1024) throw new HttpError(413, 'data file over 2 MB');
      await env.BUCKET.put(`data/${name}`, body, { httpMetadata: { contentType: 'application/json; charset=utf-8' } });
      return json({ ok: true, url: `/files/data/${name}` });
    }
    if (seg[1] === 'chat' && seg[2] === 'status' && m === 'GET') {
      return env.CHAT.get(env.CHAT.idFromName('global')).fetch(new Request(new URL('/chat/status', req.url)));
    }
    if (seg[1] === 'stickers' && seg[2] && m === 'PUT') {
      const name = seg.slice(2).join('/');
      if (!/^[\w.-]+$/.test(name)) throw new HttpError(400, 'bad sticker file name');
      const body = await req.arrayBuffer();
      if (!body.byteLength || body.byteLength > 2 * 1024 * 1024) throw new HttpError(413, 'sticker must be 1 B – 2 MB');
      await env.BUCKET.put(`stickers/${name}`, body, { httpMetadata: { contentType: req.headers.get('Content-Type') ?? 'application/octet-stream' } });
      return json({ ok: true, url: `/files/stickers/${name}` });
    }
    if (seg[1] === 'radio' && seg[2] === 'reset' && m === 'POST') {
      await env.BUCKET.delete(RADIO_KEY);
      return json({ ok: true, radio: await radio(env) });
    }
    if (seg[1] !== 'tracks') throw new HttpError(404, 'unknown endpoint');
    const id = seg[2];
    if (!id && m === 'POST') return addTrack(req, env);
    if (id && seg[3] === 'cover' && (m === 'PUT' || m === 'POST')) return replaceCover(req, env, id);
    if (id && seg[3] === 'audio' && (m === 'PUT' || m === 'POST')) return replaceAudio(req, env, id);
    if (id && !seg[3] && m === 'PATCH') return patchTrack(req, env, id);
    if (id && !seg[3] && m === 'DELETE') return deleteTrack(env, id);
    if (id && !seg[3] && m === 'GET') {
      const t = (await readPlaylist(env)).data.tracks.find((x) => x.id === id);
      if (!t) throw new HttpError(404, `track ${id} not found`);
      return json({ ok: true, track: t });
    }
  }
  throw new HttpError(404, 'not found');
}

function auth(req: Request, env: Env) {
  const h = req.headers.get('Authorization') ?? '';
  if (!env.UPLOAD_TOKEN || h !== `Bearer ${env.UPLOAD_TOKEN}`) throw new HttpError(401, 'bad or missing token');
}

/* ---------------- files ---------------- */

async function serveFile(req: Request, env: Env, key: string): Promise<Response> {
  if (!key || key === PLAYLIST_KEY) throw new HttpError(404, 'not found');
  const head = req.method === 'HEAD';
  const obj = head
    ? await env.BUCKET.head(key)
    : await env.BUCKET.get(key, { range: req.headers, onlyIf: req.headers });
  if (!obj) throw new HttpError(404, 'file not found');

  const headers = new Headers(CORS);
  obj.writeHttpMetadata(headers);
  headers.set('ETag', obj.httpEtag);
  headers.set('Accept-Ranges', 'bytes');
  headers.set('Cache-Control', key.endsWith('.json') ? 'no-cache' : 'public, max-age=31536000, immutable');

  if (!('body' in obj)) {
    // HEAD, or a conditional request that matched (If-None-Match)
    headers.set('Content-Length', String(obj.size));
    return new Response(null, { status: head ? 200 : 304, headers });
  }
  const body = (obj as R2ObjectBody).body;
  const r = obj.range as { offset?: number; length?: number; suffix?: number } | undefined;
  if (r && req.headers.has('Range')) {
    const offset = r.suffix !== undefined ? obj.size - r.suffix : (r.offset ?? 0);
    const length = r.suffix !== undefined ? r.suffix : (r.length ?? obj.size - offset);
    headers.set('Content-Range', `bytes ${offset}-${offset + length - 1}/${obj.size}`);
    headers.set('Content-Length', String(length));
    return new Response(body, { status: 206, headers });
  }
  headers.set('Content-Length', String(obj.size));
  return new Response(body, { status: 200, headers });
}

/* ---------------- playlist storage ---------------- */

async function readPlaylist(env: Env): Promise<{ data: Playlist; etag: string | null }> {
  const obj = await env.BUCKET.get(PLAYLIST_KEY);
  if (!obj) return { data: { version: 0, updatedAt: new Date(0).toISOString(), tracks: [] }, etag: null };
  return { data: (await obj.json()) as Playlist, etag: obj.etag };
}

/** Read-modify-write with an etag check, retried, so two uploads at once don't overwrite each other. */
async function updatePlaylist(env: Env, fn: (pl: Playlist) => void): Promise<Playlist> {
  for (let attempt = 0; attempt < 5; attempt++) {
    const { data, etag } = await readPlaylist(env);
    fn(data);
    data.version += 1;
    data.updatedAt = new Date().toISOString();
    const body = JSON.stringify(data, null, 2);
    const opts: R2PutOptions = { httpMetadata: { contentType: 'application/json; charset=utf-8' } };
    if (etag) opts.onlyIf = { etagMatches: etag };
    const res = await env.BUCKET.put(PLAYLIST_KEY, body, opts);
    if (res) return data;
    await new Promise((r) => setTimeout(r, 100 + Math.random() * 200));
  }
  throw new HttpError(409, 'playlist is busy, try again');
}

/* ---------------- track helpers ---------------- */

function slug(s: string) {
  return s
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48);
}

const AUDIO_TYPES: Record<string, string> = {
  'audio/mpeg': 'mp3', 'audio/mp3': 'mp3', 'audio/mp4': 'm4a', 'audio/x-m4a': 'm4a', 'audio/aac': 'aac',
  'audio/ogg': 'ogg', 'audio/opus': 'opus', 'audio/flac': 'flac', 'audio/x-flac': 'flac', 'audio/wav': 'wav', 'audio/x-wav': 'wav',
};
const IMAGE_TYPES: Record<string, string> = {
  'image/jpeg': 'jpg', 'image/jpg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'image/gif': 'gif',
};
const EXT_TYPE: Record<string, string> = {
  mp3: 'audio/mpeg', m4a: 'audio/mp4', aac: 'audio/aac', ogg: 'audio/ogg', opus: 'audio/ogg', flac: 'audio/flac', wav: 'audio/wav',
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp', gif: 'image/gif',
};

interface Blobish { data: ArrayBuffer | ReadableStream; type: string; ext: string; size?: number }

function pickType(kind: 'audio' | 'image', contentType: string, name: string): { type: string; ext: string } {
  const table = kind === 'audio' ? AUDIO_TYPES : IMAGE_TYPES;
  const ct = contentType.split(';')[0].trim().toLowerCase();
  if (table[ct]) return { type: ct === 'audio/mp3' ? 'audio/mpeg' : ct, ext: table[ct] };
  const ext = (name.split('?')[0].split('.').pop() ?? '').toLowerCase();
  if (EXT_TYPE[ext] && EXT_TYPE[ext].startsWith(kind)) return { type: EXT_TYPE[ext], ext: ext === 'jpeg' ? 'jpg' : ext };
  // Telegram and many CDNs answer with application/octet-stream and no extension: assume mp3 / jpeg
  if (ct === '' || ct === 'application/octet-stream') return kind === 'audio' ? { type: 'audio/mpeg', ext: 'mp3' } : { type: 'image/jpeg', ext: 'jpg' };
  throw new HttpError(415, `unsupported ${kind} type "${ct || ext || 'unknown'}"`);
}

async function fromFile(f: File, kind: 'audio' | 'image'): Promise<Blobish> {
  const { type, ext } = pickType(kind, f.type, f.name);
  return { data: await f.arrayBuffer(), type, ext, size: f.size };
}

async function fromUrl(u: string, kind: 'audio' | 'image'): Promise<Blobish> {
  let res: Response;
  try { res = await fetch(u); } catch (e) { throw new HttpError(400, `cannot download ${kind}_url: ${(e as Error).message}`); }
  if (!res.ok || !res.body) throw new HttpError(400, `cannot download ${kind}_url: HTTP ${res.status}`);
  const { type, ext } = pickType(kind, res.headers.get('Content-Type') ?? '', new URL(u).pathname);
  const buf = await res.arrayBuffer();
  return { data: buf, type, ext, size: buf.byteLength };
}

function checkSize(b: Blobish, max: number, what: string) {
  const size = b.size ?? (b.data instanceof ArrayBuffer ? b.data.byteLength : 0);
  if (size > max) throw new HttpError(413, `${what} is too big (${Math.round(size / 1048576)} MB, max ${max / 1048576} MB)`);
  if (size === 0) throw new HttpError(400, `${what} is empty`);
}

type Meta = Partial<Omit<Track, 'audioSrc' | 'coverArt' | 'addedAt'>>;

function cleanMeta(raw: Record<string, unknown>): Meta {
  const out: Meta = {};
  const str = (k: string) => (typeof raw[k] === 'string' ? (raw[k] as string).trim() : undefined);
  const num = (k: string) => (raw[k] === undefined || raw[k] === null || raw[k] === '' ? undefined : Number(raw[k]));
  if (str('id')) out.id = slug(str('id')!);
  if (str('title') !== undefined) out.title = str('title');
  if (str('artist') !== undefined) out.artist = str('artist');
  if (str('category') !== undefined) out.category = str('category');
  for (const k of ['duration', 'bpm', 'energy'] as const) {
    const v = num(k);
    if (v !== undefined) {
      if (!Number.isFinite(v) || v < 0) throw new HttpError(400, `${k} must be a positive number`);
      out[k] = k === 'energy' ? Math.min(100, Math.round(v)) : Math.round(v);
    }
  }
  if ('lyrics' in raw) {
    const l = raw.lyrics as { uzbek?: unknown; romaji?: unknown; kanji?: unknown } | null;
    if (l === null) out.lyrics = null;
    else if (typeof l === 'object' && typeof l.uzbek === 'string') {
      out.lyrics = { uzbek: l.uzbek };
      if (typeof l.romaji === 'string' && l.romaji.trim()) out.lyrics.romaji = l.romaji;
      if (typeof l.kanji === 'string' && l.kanji.trim()) out.lyrics.kanji = l.kanji;
    } else throw new HttpError(400, 'lyrics must be null or { "uzbek": "...", "romaji"?: "...", "kanji"?: "..." }');
  }
  if ('info' in raw) {
    const i = raw.info as { description?: unknown; details?: unknown } | null;
    if (i === null) out.info = undefined;
    else if (typeof i === 'object' && typeof i.description === 'string') {
      const details = Array.isArray(i.details)
        ? (i.details as { title?: unknown; text?: unknown }[])
            .filter((d) => typeof d?.title === 'string' && typeof d?.text === 'string')
            .map((d) => ({ title: d.title as string, text: d.text as string }))
        : [];
      out.info = { description: i.description, details };
    } else throw new HttpError(400, 'info must be null or { "description": "...", "details": [{ "title", "text" }] }');
  }
  return out;
}

async function readInput(req: Request): Promise<{ meta: Record<string, unknown>; audio?: Blobish; cover?: Blobish }> {
  const ct = req.headers.get('Content-Type') ?? '';
  if (ct.includes('multipart/form-data')) {
    const form = await req.formData();
    let meta: Record<string, unknown> = {};
    const m = form.get('meta');
    if (typeof m === 'string' && m.trim()) {
      try { meta = JSON.parse(m); } catch { throw new HttpError(400, 'field "meta" is not valid JSON'); }
    }
    // plain form fields are accepted too (title=…&artist=…)
    for (const [k, v] of form.entries()) if (typeof v === 'string' && k !== 'meta' && !(k in meta)) meta[k] = v;
    const a = form.get('audio');
    const c = form.get('cover');
    return {
      meta,
      audio: a instanceof File ? await fromFile(a, 'audio') : typeof meta.audio_url === 'string' ? await fromUrl(meta.audio_url, 'audio') : undefined,
      cover: c instanceof File && c.size > 0 ? await fromFile(c, 'image') : typeof meta.cover_url === 'string' && meta.cover_url ? await fromUrl(meta.cover_url, 'image') : undefined,
    };
  }
  if (ct.includes('application/json')) {
    let meta: Record<string, unknown>;
    try { meta = await req.json(); } catch { throw new HttpError(400, 'body is not valid JSON'); }
    return {
      meta,
      audio: typeof meta.audio_url === 'string' && meta.audio_url ? await fromUrl(meta.audio_url, 'audio') : undefined,
      cover: typeof meta.cover_url === 'string' && meta.cover_url ? await fromUrl(meta.cover_url, 'image') : undefined,
    };
  }
  throw new HttpError(415, 'send multipart/form-data or application/json');
}

/* ---------------- endpoints ---------------- */

async function addTrack(req: Request, env: Env): Promise<Response> {
  const input = await readInput(req);
  const meta = cleanMeta(input.meta);
  if (!input.audio) throw new HttpError(400, 'audio is required (multipart file "audio" or "audio_url")');
  if (!meta.title) throw new HttpError(400, 'title is required');
  if (!meta.artist) throw new HttpError(400, 'artist is required');
  checkSize(input.audio, MAX_AUDIO, 'audio');
  if (input.cover) checkSize(input.cover, MAX_COVER, 'cover');

  const current = (await readPlaylist(env)).data;
  let id = meta.id || slug(`${meta.artist}-${meta.title}`) || `track-${Date.now()}`;
  if (current.tracks.some((t) => t.id === id)) {
    if (meta.id) throw new HttpError(409, `track id "${id}" already exists (use PATCH to change it, or DELETE first)`);
    id = `${id}-${Date.now().toString(36)}`;
  }
  const stamp = Date.now().toString(36);
  const audioKey = `audio/${id}-${stamp}.${input.audio.ext}`;
  await env.BUCKET.put(audioKey, input.audio.data, { httpMetadata: { contentType: input.audio.type } });
  let coverKey: string | null = null;
  if (input.cover) {
    coverKey = `covers/${id}-${stamp}.${input.cover.ext}`;
    await env.BUCKET.put(coverKey, input.cover.data, { httpMetadata: { contentType: input.cover.type } });
  }

  const track: Track = {
    id,
    title: meta.title,
    artist: meta.artist,
    category: meta.category || 'J-Pop',
    coverArt: coverKey ? `/files/${coverKey}` : DEFAULT_COVER,
    audioSrc: `/files/${audioKey}`,
    duration: meta.duration ?? 0,
    bpm: meta.bpm ?? 120,
    energy: meta.energy ?? 70,
    lyrics: meta.lyrics ?? null,
    ...(meta.info ? { info: meta.info } : {}),
    addedAt: new Date().toISOString(),
  };
  const pl = await updatePlaylist(env, (p) => {
    if (p.tracks.some((t) => t.id === id)) throw new HttpError(409, `track id "${id}" already exists`);
    p.tracks.push(track);
  });
  return json({ ok: true, track, total: pl.tracks.length, version: pl.version }, 201);
}

async function patchTrack(req: Request, env: Env, id: string): Promise<Response> {
  let raw: Record<string, unknown>;
  try { raw = await req.json(); } catch { throw new HttpError(400, 'body must be JSON'); }
  const meta = cleanMeta(raw);
  delete meta.id;
  let updated: Track | undefined;
  await updatePlaylist(env, (p) => {
    const t = p.tracks.find((x) => x.id === id);
    if (!t) throw new HttpError(404, `track ${id} not found`);
    if ('info' in raw && raw.info === null) delete t.info;
    Object.assign(t, meta);
    if (meta.title === '' || meta.artist === '') throw new HttpError(400, 'title / artist cannot be empty');
    updated = t;
  });
  return json({ ok: true, track: updated });
}

async function replaceCover(req: Request, env: Env, id: string): Promise<Response> {
  const input = await readInput(req);
  if (!input.cover) throw new HttpError(400, 'cover is required (multipart file "cover" or "cover_url")');
  checkSize(input.cover, MAX_COVER, 'cover');
  const exists = (await readPlaylist(env)).data.tracks.some((t) => t.id === id);
  if (!exists) throw new HttpError(404, `track ${id} not found`);
  const key = `covers/${id}-${Date.now().toString(36)}.${input.cover.ext}`;
  await env.BUCKET.put(key, input.cover.data, { httpMetadata: { contentType: input.cover.type } });
  let old = '';
  let updated: Track | undefined;
  await updatePlaylist(env, (p) => {
    const t = p.tracks.find((x) => x.id === id);
    if (!t) throw new HttpError(404, `track ${id} not found`);
    old = t.coverArt;
    t.coverArt = `/files/${key}`;
    updated = t;
  });
  if (old.startsWith('/files/covers/') && old !== DEFAULT_COVER) await env.BUCKET.delete(old.slice('/files/'.length));
  return json({ ok: true, track: updated });
}

async function replaceAudio(req: Request, env: Env, id: string): Promise<Response> {
  const input = await readInput(req);
  if (!input.audio) throw new HttpError(400, 'audio is required (multipart file "audio" or "audio_url")');
  checkSize(input.audio, MAX_AUDIO, 'audio');
  const meta = cleanMeta(input.meta);
  const exists = (await readPlaylist(env)).data.tracks.some((t) => t.id === id);
  if (!exists) throw new HttpError(404, `track ${id} not found`);
  const key = `audio/${id}-${Date.now().toString(36)}.${input.audio.ext}`;
  await env.BUCKET.put(key, input.audio.data, { httpMetadata: { contentType: input.audio.type } });
  let old = '';
  let updated: Track | undefined;
  await updatePlaylist(env, (p) => {
    const t = p.tracks.find((x) => x.id === id);
    if (!t) throw new HttpError(404, `track ${id} not found`);
    old = t.audioSrc;
    t.audioSrc = `/files/${key}`;
    if (meta.duration !== undefined) t.duration = meta.duration;
    updated = t;
  });
  if (old.startsWith('/files/audio/')) await env.BUCKET.delete(old.slice('/files/'.length));
  return json({ ok: true, track: updated });
}

async function deleteTrack(env: Env, id: string): Promise<Response> {
  let removed: Track | undefined;
  const pl = await updatePlaylist(env, (p) => {
    const i = p.tracks.findIndex((x) => x.id === id);
    if (i < 0) throw new HttpError(404, `track ${id} not found`);
    removed = p.tracks.splice(i, 1)[0];
  });
  const keys = [removed!.audioSrc, removed!.coverArt]
    .filter((s) => s.startsWith('/files/') && s !== DEFAULT_COVER)
    .map((s) => s.slice('/files/'.length));
  if (keys.length) await env.BUCKET.delete(keys);
  return json({ ok: true, removed: removed!.id, total: pl.tracks.length });
}

/* ---------------- live radio ----------------
 * The broadcast is a chain of cycles. A cycle plays every song once, shuffled, back to back; its
 * timetable is fixed when it is created and stored in R2, so every listener computes the same song
 * and the same second from the clock. Songs added later join the next cycle, so nothing that is
 * already scheduled moves.
 */

const RADIO_KEY = 'radio.json';
/** At most this many slots are handed to the app at once. */
const RADIO_AHEAD = 80;

interface Slot { id: string; start: number; duration: number } // epoch ms, ms
interface Cycle { start: number; end: number; slots: Slot[] }
interface RadioState { cycles: Cycle[]; version?: number }
/** The next 3 hours are never changed: phones already know them. */
const FROZEN_MS = 3 * 3600_000;

/** Small seeded PRNG: the same seed gives the same shuffle on every request. */
function mulberry32(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function makeCycle(tracks: Track[], start: number, avoidFirst?: string, seed = start): Cycle | null {
  const pool = tracks.filter((t) => t.duration > 0);
  if (!pool.length) return null;
  const rnd = mulberry32(seed % 2147483647);
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  // never the same song twice in a row across the cycle boundary
  if (pool.length > 1 && pool[0].id === avoidFirst) pool.push(pool.shift()!);
  let at = start;
  const slots = pool.map((t) => {
    const s = { id: t.id, start: at, duration: Math.round(t.duration * 1000) };
    at += s.duration;
    return s;
  });
  return { start, end: at, slots };
}

async function radio(env: Env) {
  const now = Date.now();
  for (let attempt = 0; attempt < 5; attempt++) {
    const obj = await env.BUCKET.get(RADIO_KEY);
    const state: RadioState = obj ? ((await obj.json()) as RadioState) : { cycles: [] };
    let cycles = state.cycles.filter((c) => c.end > now - 60_000);
    let changed = cycles.length !== state.cycles.length;
    const playlist = (await readPlaylist(env)).data;

    // songs were added or removed: re-plan everything after the frozen window. Songs of the
    // current cycle that have not been scheduled inside the window (new ones included) are
    // shuffled again into its rest, so new songs come on air within a few hours.
    if (cycles.length && state.version !== playlist.version) {
      const freeze = now + FROZEN_MS;
      const k = cycles.findIndex((c) => c.end > freeze);
      if (k >= 0) {
        const c = cycles[k];
        const kept = c.slots.filter((s) => s.start < freeze);
        const used = new Set(kept.map((s) => s.id));
        const tailStart = kept.length ? kept[kept.length - 1].start + kept[kept.length - 1].duration : c.start;
        const tail = makeCycle(playlist.tracks.filter((t) => !used.has(t.id)), tailStart, kept[kept.length - 1]?.id, tailStart + playlist.version);
        cycles = [...cycles.slice(0, k), { start: c.start, end: tail?.end ?? tailStart, slots: [...kept, ...(tail?.slots ?? [])] }];
      }
      changed = true;
    }

    // nobody listened for a long time: the broadcast simply starts again from now
    if (!cycles.length || cycles[0].start > now) {
      const c = makeCycle(playlist.tracks, now - (now % 1000));
      if (!c) return { serverTime: now, slots: [] };
      cycles = [c];
      changed = true;
    }
    // keep more than the frozen window scheduled ahead, so the app never runs off the end;
    // a new cycle (with every song, new ones included) starts as soon as the previous one ends
    while (cycles[cycles.length - 1].end - now < FROZEN_MS + 3600_000) {
      const last = cycles[cycles.length - 1];
      const c = makeCycle(playlist.tracks, last.end, last.slots[last.slots.length - 1]?.id);
      if (!c) break;
      cycles.push(c);
      changed = true;
    }

    if (changed) {
      const opts: R2PutOptions = { httpMetadata: { contentType: 'application/json' } };
      if (obj) opts.onlyIf = { etagMatches: obj.etag };
      const ok = await env.BUCKET.put(RADIO_KEY, JSON.stringify({ cycles, version: playlist.version }), opts);
      if (!ok) { await new Promise((r) => setTimeout(r, 80 + Math.random() * 150)); continue; } // another request won, use its timetable
    }

    // only the frozen window: what phones keep is exactly what can no longer change
    const slots = cycles.flatMap((c) => c.slots).filter((s) => s.start + s.duration > now && s.start < now + FROZEN_MS).slice(0, RADIO_AHEAD);
    return { serverTime: Date.now(), slots };
  }
  throw new HttpError(409, 'radio is busy, try again');
}
