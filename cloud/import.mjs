#!/usr/bin/env node
/**
 * Bulk upload of a folder of audio files to the Kawaii Radio cloud.
 * Title, artist, duration and cover come from the files' own tags; tracks already in the cloud or
 * built into the app (src/data/playlist.ts) are skipped, so re-running it is safe.
 *
 *   node cloud/import.mjs <folder> [--dry] [--ost list.txt] [--skip "Artist - Title" ...]
 *
 *   --dry        only print what would be uploaded
 *   --ost FILE   file names / titles (one per line) that go to the "Anime OST" tab
 *   --skip TEXT  extra "Artist - Title" (or file name) to leave out, repeatable
 *
 * Needs cloud/.secrets (written by setup.sh).
 */
import { parseFile } from 'music-metadata';
import { readFileSync, readdirSync } from 'node:fs';
import { basename, dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const AUDIO = new Set(['.mp3', '.m4a', '.aac', '.ogg', '.opus', '.flac', '.wav']);
const MIME = { '.mp3': 'audio/mpeg', '.m4a': 'audio/mp4', '.aac': 'audio/aac', '.ogg': 'audio/ogg', '.opus': 'audio/ogg', '.flac': 'audio/flac', '.wav': 'audio/wav' };

/* ---------- args ---------- */
const args = process.argv.slice(2);
const folder = args.find((a, i) => !a.startsWith('--') && !['--ost', '--skip'].includes(args[i - 1]));
if (!folder) { console.error('usage: node cloud/import.mjs <folder> [--dry] [--ost list.txt] [--skip "Artist - Title"]'); process.exit(1); }
const dry = args.includes('--dry');
const listArg = (flag) => args.flatMap((a, i) => (args[i - 1] === flag ? [a] : []));
const norm = (s) => s.normalize('NFKC').toLowerCase().replace(/\(feat[^)]*\)|feat\..*$/g, '').replace(/[^\p{L}\p{N}]+/gu, '');
const ostList = listArg('--ost').flatMap((f) => readFileSync(f, 'utf8').split('\n')).map((l) => l.trim()).filter(Boolean).map(norm);
const skipList = listArg('--skip').map(norm);

/* ---------- secrets ---------- */
const secrets = Object.fromEntries(
  readFileSync(join(here, '.secrets'), 'utf8').split('\n').filter((l) => l.includes('=') && !l.startsWith('#')).map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1).trim()]),
);
const BASE = secrets.KAWAII_BASE_URL;
const AUTH = { Authorization: `Bearer ${secrets.KAWAII_UPLOAD_TOKEN}` };

/* ---------- what is already there ---------- */
const cloud = (await (await fetch(`${BASE}/playlist.json`)).json()).tracks;
const builtinSrc = readFileSync(join(here, '../src/data/playlist.ts'), 'utf8');
const builtin = [...builtinSrc.matchAll(/title: '([^']*)', artist: '([^']*)'/g)].map((m) => ({ title: m[1], artist: m[2] }));
const have = new Set([...cloud, ...builtin].map((t) => norm(t.title) + '|' + norm(t.artist.split(/,| feat\.? | & /i)[0])));

/* ---------- go ---------- */
const files = readdirSync(folder).filter((f) => AUDIO.has(extname(f).toLowerCase())).sort();
let added = 0, skipped = 0, failed = 0;
for (const f of files) {
  const path = join(folder, f);
  const stem = basename(f, extname(f));
  let meta;
  try { meta = await parseFile(path); } catch (e) { console.log(`✗ ${f}: cannot read tags (${e.message})`); failed++; continue; }
  const c = meta.common;
  const dash = stem.indexOf(' - ');
  const title = (c.title || (dash > 0 ? stem.slice(dash + 3) : stem)).trim();
  const artist = (c.artists?.join(', ') || c.artist || (dash > 0 ? stem.slice(0, dash) : 'Unknown')).trim();
  const key = norm(title) + '|' + norm(artist.split(/,| feat\.? | & /i)[0]);

  if (have.has(key) || skipList.includes(norm(`${artist} - ${title}`)) || skipList.includes(norm(stem))) {
    console.log(`· ${artist} — ${title}: already there, skipped`);
    skipped++;
    continue;
  }
  const ost = ostList.includes(norm(stem)) || ostList.includes(norm(title));
  const category = ost ? 'Anime OST' : 'J-Pop';
  const duration = Math.round(meta.format.duration ?? 0);
  if (dry) { console.log(`+ ${artist} — ${title} [${category}, ${duration}s${c.picture?.length ? ', cover' : ''}]`); added++; continue; }

  const form = new FormData();
  form.set('audio', new Blob([readFileSync(path)], { type: MIME[extname(f).toLowerCase()] }), f);
  const pic = c.picture?.[0];
  if (pic) form.set('cover', new Blob([pic.data], { type: pic.format }), `cover.${pic.format.split('/')[1] || 'jpg'}`);
  form.set('meta', JSON.stringify({ title, artist, category, duration }));
  try {
    const res = await (await fetch(`${BASE}/api/tracks`, { method: 'POST', headers: AUTH, body: form })).json();
    if (!res.ok) throw new Error(res.error);
    have.add(key);
    added++;
    console.log(`✓ ${artist} — ${title} [${category}] (${res.total})`);
  } catch (e) {
    failed++;
    console.log(`✗ ${artist} — ${title}: ${e.message}`);
  }
}
console.log(`\n${dry ? 'would add' : 'added'} ${added}, skipped ${skipped}, failed ${failed}`);
