#!/usr/bin/env node
/**
 * Copies a Telegram sticker pack into the Kawaii Radio cloud (stickers/ in R2) for the in-app chat.
 *
 *   node cloud/stickers.mjs Kawaiiuz --info   # only show what the pack contains
 *   node cloud/stickers.mjs Kawaiiuz          # download, convert, upload, write stickers/pack.json
 *
 * Static stickers (webp) go as they are, animated ones (.tgs, Lottie) too; video stickers (.webm)
 * become animated webp with ffmpeg. Needs TELEGRAM_BOT_TOKEN (any bot of yours) and the upload
 * token in cloud/.secrets.
 */
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const secrets = Object.fromEntries(
  readFileSync(join(here, '.secrets'), 'utf8').split('\n').filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]),
);
const BOT = secrets.TELEGRAM_BOT_TOKEN;
const BASE = secrets.KAWAII_BASE_URL;
const AUTH = { Authorization: `Bearer ${secrets.KAWAII_UPLOAD_TOKEN}` };
if (!BOT) { console.error('Add TELEGRAM_BOT_TOKEN=... to cloud/.secrets first'); process.exit(1); }

const name = process.argv[2];
const infoOnly = process.argv.includes('--info');
if (!name) { console.error('usage: node cloud/stickers.mjs <pack name> [--info]'); process.exit(1); }

const tg = async (method, params) => {
  const r = await (await fetch(`https://api.telegram.org/bot${BOT}/${method}?${new URLSearchParams(params)}`)).json();
  if (!r.ok) throw new Error(`${method}: ${r.description}`);
  return r.result;
};

const set = await tg('getStickerSet', { name });
const kind = (s) => (s.is_video ? 'video' : s.is_animated ? 'animated' : 'static');
const counts = set.stickers.reduce((m, s) => ({ ...m, [kind(s)]: (m[kind(s)] ?? 0) + 1 }), {});
console.log(`"${set.title}" (${set.name}): ${set.stickers.length} stickers`, counts);
if (infoOnly) process.exit(0);

const tmp = mkdtempSync(join(tmpdir(), 'kr-stickers-'));
const pack = { name: set.name, title: set.title, stickers: [] };
try {
  for (const [i, s] of set.stickers.entries()) {
    const f = await tg('getFile', { file_id: s.file_id });
    const data = Buffer.from(await (await fetch(`https://api.telegram.org/file/bot${BOT}/${f.file_path}`)).arrayBuffer());
    let out = data;
    let ext = kind(s) === 'animated' ? 'tgs' : 'webp';
    let type = kind(s) === 'animated' ? 'application/x-tgsticker' : 'image/webp';
    if (kind(s) === 'video') {
      // webm (VP9 with alpha) → looping animated webp, 512 px, keeps transparency
      const src = join(tmp, `${i}.webm`);
      const dst = join(tmp, `${i}.webp`);
      writeFileSync(src, data);
      execFileSync('ffmpeg', ['-v', 'error', '-y', '-c:v', 'libvpx-vp9', '-i', src, '-vf', 'scale=512:-1:flags=lanczos,fps=24',
        '-c:v', 'libwebp_anim', '-lossless', '0', '-q:v', '70', '-loop', '0', '-an', dst]);
      out = readFileSync(dst);
    }
    const file = `${s.file_unique_id}.${ext}`;
    const r = await (await fetch(`${BASE}/api/stickers/${file}`, { method: 'PUT', headers: { ...AUTH, 'Content-Type': type }, body: out })).json();
    if (!r.ok) throw new Error(`${file}: ${r.error}`);
    pack.stickers.push({ id: s.file_unique_id, file: `/files/stickers/${file}`, type: ext === 'tgs' ? 'lottie' : 'image', emoji: s.emoji ?? '' });
    console.log(`✓ ${i + 1}/${set.stickers.length} ${s.emoji ?? ''} ${kind(s)} → ${file} (${Math.round(out.length / 1024)} KB)`);
  }
  const r = await (await fetch(`${BASE}/api/stickers/pack.json`, {
    method: 'PUT', headers: { ...AUTH, 'Content-Type': 'application/json' }, body: JSON.stringify(pack),
  })).json();
  if (!r.ok) throw new Error(`pack.json: ${r.error}`);
  console.log(`\nDone: ${pack.stickers.length} stickers, manifest at ${BASE}/files/stickers/pack.json`);
} finally {
  rmSync(tmp, { recursive: true, force: true });
}
