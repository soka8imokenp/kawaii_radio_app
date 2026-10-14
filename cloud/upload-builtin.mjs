#!/usr/bin/env node
/**
 * One-off: moves the 10 songs that used to be bundled inside the APK to the cloud, with their
 * lyrics, "Tavsif" texts, category, BPM and covers (taken from native/shared/.../builtin.json).
 * Songs already in the cloud (same artist + title) are skipped, so re-running it is safe.
 *
 *   node cloud/upload-builtin.mjs [--dry]
 *
 * Needs cloud/.secrets.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const res = join(here, '..', 'native', 'shared', 'src', 'commonMain', 'composeResources');
const dry = process.argv.includes('--dry');

const secrets = Object.fromEntries(
  readFileSync(join(here, '.secrets'), 'utf8').split('\n').filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1).trim()]),
);
const BASE = secrets.KAWAII_BASE_URL;
const AUTH = { Authorization: `Bearer ${secrets.KAWAII_UPLOAD_TOKEN}` };

const norm = (s) => s.normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');
const tracks = JSON.parse(readFileSync(join(res, 'files', 'builtin.json'), 'utf8'));
const cloud = (await (await fetch(`${BASE}/playlist.json`, { cache: 'no-store' })).json()).tracks;
const have = new Set(cloud.map((t) => norm(`${t.artist}-${t.title}`)));

for (const t of tracks) {
  const name = `${t.artist} — ${t.title}`;
  if (have.has(norm(`${t.artist}-${t.title}`))) { console.log(`skip (already in cloud): ${name}`); continue; }
  const meta = {
    id: t.id, title: t.title, artist: t.artist, category: t.category,
    duration: t.duration, bpm: t.bpm, energy: t.energy, lyrics: t.lyrics ?? null, ...(t.info ? { info: t.info } : {}),
  };
  if (dry) { console.log(`would upload: ${name} [${t.category}]${t.lyrics ? ' +lyrics' : ''}`); continue; }
  const form = new FormData();
  form.append('audio', new Blob([readFileSync(join(res, t.audioSrc))], { type: 'audio/mpeg' }), t.audioSrc.split('/').pop());
  form.append('cover', new Blob([readFileSync(join(res, t.coverArt))], { type: 'image/webp' }), t.coverArt.split('/').pop());
  form.append('meta', JSON.stringify(meta));
  const r = await fetch(`${BASE}/api/tracks`, { method: 'POST', headers: AUTH, body: form });
  const body = await r.json().catch(() => ({}));
  if (!r.ok || !body.ok) { console.error(`FAILED ${name}: ${r.status} ${body.error ?? ''}`); process.exitCode = 1; continue; }
  console.log(`uploaded: ${name} → ${body.track.id} (total ${body.total})`);
}
