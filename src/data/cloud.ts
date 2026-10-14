import type { Track } from './types';

/**
 * Tracks added through the cloud (Cloudflare Worker + R2, see cloud/). The Worker URL comes from
 * VITE_CLOUD_URL in .env; without it the app simply plays the built-in playlist.
 */
export const CLOUD_URL = ((import.meta.env.VITE_CLOUD_URL as string | undefined) ?? '').replace(/\/+$/, '');

const CACHE_KEY = 'kr01-cloud';

/** Paths in playlist.json are relative to the Worker ("/files/audio/…"). */
function absolutize(t: Track): Track {
  const abs = (p: string) => (/^https?:/.test(p) ? p : `${CLOUD_URL}${p.startsWith('/') ? '' : '/'}${p}`);
  return { ...t, audioSrc: abs(t.audioSrc), coverArt: abs(t.coverArt), duration: Number(t.duration) || 0 };
}

function valid(t: unknown): t is Track {
  const x = t as Track;
  return !!x && typeof x.id === 'string' && typeof x.title === 'string' && typeof x.artist === 'string' && typeof x.audioSrc === 'string';
}

function parse(data: unknown): Track[] {
  const list = (data as { tracks?: unknown[] })?.tracks;
  return Array.isArray(list) ? list.filter(valid).map((t) => absolutize({ category: 'J-Pop', bpm: 120, energy: 70, lyrics: null, coverArt: '', ...(t as Partial<Track>) } as Track)) : [];
}

/** Last playlist we saw, so cloud tracks show instantly (and offline) on the next launch. */
export function cachedCloudTracks(): Track[] {
  if (!CLOUD_URL) return [];
  try { return parse(JSON.parse(localStorage.getItem(CACHE_KEY) || 'null')); } catch { return []; }
}

export async function fetchCloudTracks(): Promise<Track[] | null> {
  if (!CLOUD_URL) return null;
  try {
    const res = await fetch(`${CLOUD_URL}/playlist.json`, { cache: 'no-store' });
    if (!res.ok) return null;
    const data = await res.json();
    try { localStorage.setItem(CACHE_KEY, JSON.stringify(data)); } catch { /* storage full */ }
    return parse(data);
  } catch {
    return null; // offline: keep what we have
  }
}
