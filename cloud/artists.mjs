#!/usr/bin/env node
/**
 * Builds data/artists.json for the app's artist pages (Qidiruv → Ijrochilar):
 * every artist credited in the catalogue, its official picture from Deezer's public API (linked,
 * not copied) and the Uzbek texts from cloud/artists-bio.json.
 *
 *   node cloud/artists.mjs --dry   # show what was found, upload nothing
 *   node cloud/artists.mjs         # build and upload
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const secrets = Object.fromEntries(
  readFileSync(join(here, '.secrets'), 'utf8').split('\n').filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]),
);
const BASE = secrets.KAWAII_BASE_URL;
const dry = process.argv.includes('--dry');
const bios = JSON.parse(readFileSync(join(here, 'artists-bio.json'), 'utf8'));

const norm = (s) => s.normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');
const slug = (s) => s.normalize('NFKD').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '');
const split = (credit) => credit.split(/,| feat\.? | ft\.? | & | x |\//i).map((s) => s.trim()).filter(Boolean);

async function get(url) {
  for (let i = 1; ; i++) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(30_000) });
      return await r.json();
    } catch (e) {
      if (i >= 4) throw e;
      await new Promise((r) => setTimeout(r, 1500 * i));
    }
  }
}

/* ---------- artists of the catalogue ---------- */
const tracks = (await get(`${BASE}/playlist.json`)).tracks;
const byKey = new Map(); // norm(name) → { name, songs: Set }
for (const t of tracks) {
  for (const n of split(t.artist)) {
    const k = norm(n);
    if (!k) continue;
    const e = byKey.get(k) ?? { name: n, aliases: new Set(), songs: new Set() };
    if (n !== e.name) e.aliases.add(n);
    e.songs.add(norm(t.title));
    byKey.set(k, e);
  }
}

/* ---------- Deezer: the right artist and its picture ---------- */
async function deezer(name, songs) {
  const found = (await get(`https://api.deezer.com/search/artist?q=${encodeURIComponent(name)}&limit=10`)).data ?? [];
  const same = found.filter((a) => norm(a.name) === norm(name));
  if (!same.length) return null;
  // several artists share the name: pick the one whose songs are ours
  let best = null;
  for (const a of same.slice(0, 4)) {
    const top = (await get(`https://api.deezer.com/artist/${a.id}/top?limit=50`)).data ?? [];
    const overlap = top.filter((t) => [...songs].some((s) => s && (norm(t.title).includes(s) || s.includes(norm(t.title))))).length;
    const score = overlap * 1_000_000 + (a.nb_fan ?? 0);
    if (!best || score > best.score) best = { a, score, overlap };
  }
  return described(best.a, best.overlap);
}

/** Pictures (500 px for the page, 250 px for lists) and the artist's real top songs on Deezer. */
async function described(a, overlap) {
  // Deezer's placeholder for artists without a picture has an empty hash in the url
  const ok = (u) => (u && !u.includes('/artist//') ? u : null);
  const top = ((await get(`https://api.deezer.com/artist/${a.id}/top?limit=25`)).data ?? [])
    .map((t) => t.title_short || t.title)
    // versions of the same song (live, remix, instrumental, TV size…) and covers are not "popular songs"
    .filter((t) => !/\b(live|remix|instrumental|inst\.|tv size|acoustic|ver\.|version|cover|karaoke)\b/i.test(t));
  const known = [...new Map(top.map((t) => [norm(t), t])).values()].slice(0, 6);
  return { id: a.id, fans: a.nb_fan, overlap, picture: ok(a.picture_big), thumb: ok(a.picture_medium), known };
}

const artists = [];
for (const [, e] of byKey) {
  const id = slug(e.name);
  const bio = bios[id] ?? {};
  // artists-bio.json can pin the right Deezer page ("deezer": id) or turn the picture off ("deezer": null)
  const d = 'deezer' in bio
    ? (bio.deezer == null ? null : await get(`https://api.deezer.com/artist/${bio.deezer}`).then((a) => described(a, 'pinned')).catch(() => null))
    : await deezer(e.name, e.songs).catch(() => null);
  artists.push({
    id,
    name: bio.name ?? e.name,
    aliases: [...new Set([...e.aliases, ...(bio.aliases ?? [])])],
    picture: d?.picture ?? null,
    thumb: d?.thumb ?? null,
    pictureSource: d?.picture ? 'Deezer' : null,
    genre: bio.genre ?? '',
    origin: bio.origin ?? '',
    since: bio.since ?? '',
    tagline: bio.tagline ?? '',
    bio: bio.bio ?? [],
    // real popularity first (Deezer's top songs); my hand-written list only when Deezer knows too few
    known: (d?.known?.length ?? 0) >= 5 ? d.known : (bio.known ?? []),
  });
  console.log(`${d?.picture ? '🖼' : '··'} ${bio.bio ? '📝' : '··'} ${e.name}${d ? `  (deezer ${d.id}, ${d.fans} fans, ${d.overlap} songs match)` : '  (not on Deezer)'}`);
}

const withBio = artists.filter((a) => a.bio.length).length;
const withPic = artists.filter((a) => a.picture).length;
console.log(`\n${artists.length} artists, ${withPic} with picture, ${withBio} with a story`);
if (dry) process.exit(0);

const res = await fetch(`${BASE}/api/data/artists.json`, {
  method: 'PUT',
  headers: { Authorization: `Bearer ${secrets.KAWAII_UPLOAD_TOKEN}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ artists }),
});
console.log(await res.text());
