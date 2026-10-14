import { useSyncExternalStore } from 'react';
import { Capacitor } from '@capacitor/core';
import { MediaSession } from '@jofr/capacitor-media-session';
import { playlist } from '../data/playlist';
import { cachedCloudTracks, fetchCloudTracks } from '../data/cloud';
import type { Track } from '../data/types';

export interface PlayerState {
  index: number;
  playing: boolean;
  time: number;
  duration: number;
  volume: number;
  shuffle: boolean;
  loop: boolean;
  liked: string[];
  saved: string[];   // songs kept inside the app (Kawaii Pass feature)
  premium: boolean;  // Kawaii Pass subscription
  lang: 'uz' | 'jp';
  tuning: boolean; // short "static" moment while switching stations
  preview: number; // stations the tuner wheel is currently dialled away from
  catalog: number; // bumps whenever cloud tracks are merged into the playlist
}

/* ---------- cloud tracks: appended after the built-in ones ---------- */
const BUILTIN = playlist.length;
function mergeCloud(tracks: Track[]) {
  const builtinIds = new Set(playlist.slice(0, BUILTIN).map((t) => t.id));
  playlist.splice(BUILTIN, playlist.length - BUILTIN, ...tracks.filter((t) => !builtinIds.has(t.id)));
}
mergeCloud(cachedCloudTracks());

const LS = 'kr01';
const saved = (() => {
  try { return JSON.parse(localStorage.getItem(LS) || '{}'); } catch { return {}; }
})();

const savedIndex = (() => {
  const byId = typeof saved.id === 'string' ? playlist.findIndex((t) => t.id === saved.id) : -1;
  if (byId >= 0) return byId;
  return typeof saved.index === 'number' ? Math.max(0, Math.min(saved.index, playlist.length - 1)) : 0;
})();

let state: PlayerState = {
  index: savedIndex,
  playing: false,
  time: 0,
  duration: playlist[savedIndex].duration,
  volume: typeof saved.volume === 'number' ? saved.volume : 0.8,
  shuffle: !!saved.shuffle,
  loop: !!saved.loop,
  liked: Array.isArray(saved.liked) ? saved.liked : [],
  saved: Array.isArray(saved.saved) ? saved.saved : [],
  premium: (() => { try { return localStorage.getItem('kr01-premium') === '1'; } catch { return false; } })(),
  lang: saved.lang === 'jp' ? 'jp' : 'uz',
  tuning: false,
  preview: 0,
  catalog: 0,
};

const listeners = new Set<() => void>();
/** Playback position changes several times a second: only the LCD and lyrics listen to it. */
const timeListeners = new Set<() => void>();
function set(patch: Partial<PlayerState>) {
  state = { ...state, ...patch };
  const timeOnly = Object.keys(patch).every((k) => k === 'time');
  if (!timeOnly) listeners.forEach((l) => l());
  timeListeners.forEach((l) => l());
  if ('index' in patch || 'volume' in patch || 'shuffle' in patch || 'loop' in patch || 'liked' in patch || 'saved' in patch || 'lang' in patch) {
    try { localStorage.setItem(LS, JSON.stringify({ index: state.index, id: playlist[state.index]?.id, volume: state.volume, shuffle: state.shuffle, loop: state.loop, liked: state.liked, saved: state.saved, lang: state.lang })); } catch { /* private mode */ }
  }
}

const audio = new Audio();
audio.preload = 'auto';
audio.crossOrigin = 'anonymous';
audio.src = playlist[state.index].audioSrc;
audio.volume = state.volume;

let ctx: AudioContext | null = null;
let analyser: AnalyserNode | null = null;
const freq = new Uint8Array(64);

function ensureGraph() {
  if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; }
  try {
    ctx = new AudioContext();
    const src = ctx.createMediaElementSource(audio);
    analyser = ctx.createAnalyser();
    analyser.fftSize = 128;
    analyser.smoothingTimeConstant = 0.78;
    src.connect(analyser).connect(ctx.destination);
  } catch {
    ctx = null;
  }
}

/** 0..1 energy per band; falls back to a gentle synthetic pulse when analysis is unavailable. */
export function spectrum(bands: number): number[] {
  const out = new Array(bands).fill(0);
  if (analyser && state.playing) {
    analyser.getByteFrequencyData(freq);
    const usable = 44; // skip the empty top end
    for (let b = 0; b < bands; b++) {
      const i0 = Math.floor(Math.pow(b / bands, 1.6) * usable);
      const i1 = Math.max(i0 + 1, Math.floor(Math.pow((b + 1) / bands, 1.6) * usable));
      let m = 0;
      for (let i = i0; i < i1; i++) m = Math.max(m, freq[i]);
      out[b] = m / 255;
    }
  } else if (state.playing) {
    const t = performance.now() / 1000;
    const beat = Math.pow(1 - ((t * playlist[state.index].bpm) / 60) % 1, 3);
    for (let b = 0; b < bands; b++) out[b] = (0.25 + 0.5 * beat) * (0.6 + 0.4 * Math.sin(t * 3 + b * 0.7)) * (1 - b / bands * 0.6);
  }
  return out;
}

let tuneTimer: ReturnType<typeof setTimeout> | undefined;
function load(index: number, autoplay: boolean) {
  const i = ((index % playlist.length) + playlist.length) % playlist.length;
  const t = playlist[i];
  set({ index: i, time: 0, duration: t.duration, tuning: true });
  clearTimeout(tuneTimer);
  tuneTimer = setTimeout(() => set({ tuning: false }), 900);
  audio.src = t.audioSrc;
  updateSession(t);
  if (autoplay) play();
}

export const player = {
  play,
  pause,
  toggle: () => (state.playing ? pause() : play()),
  select: (i: number) => load(i, true),
  next: () => load(state.shuffle ? randomOther() : state.index + 1, state.playing || true),
  prev: () => (audio.currentTime > 4 ? seek(0) : load(state.index - 1, state.playing || true)),
  step: (d: number) => load(state.index + d, state.playing),
  seek,
  setVolume: (v: number) => { audio.volume = v; set({ volume: v }); },
  toggleShuffle: () => set({ shuffle: !state.shuffle }),
  toggleLoop: () => set({ loop: !state.loop }),
  setPreview: (n: number) => set({ preview: n }),
  setLang: (lang: 'uz' | 'jp') => set({ lang }),
  /** Keeps the song inside the app: audio goes to Cache Storage so it plays offline. */
  toggleSave: async (id: string) => {
    const t = playlist.find((x) => x.id === id);
    if (!t) return;
    if (state.saved.includes(id)) {
      set({ saved: state.saved.filter((x) => x !== id) });
      try { (await caches.open('kr01-saved')).delete(t.audioSrc); } catch { /* no cache api */ }
      return;
    }
    set({ saved: [...state.saved, id] });
    try { await (await caches.open('kr01-saved')).add(t.audioSrc); } catch { /* offline copy optional */ }
  },
  toggleLike: (id: string) => set({ liked: state.liked.includes(id) ? state.liked.filter((x) => x !== id) : [...state.liked, id] }),
};

function randomOther() {
  if (playlist.length < 2) return state.index;
  let r = state.index;
  while (r === state.index) r = Math.floor(Math.random() * playlist.length);
  return r;
}

function play() {
  ensureGraph();
  audio.play().then(() => set({ playing: true })).catch(() => set({ playing: false }));
  set({ playing: true });
}
function pause() {
  audio.pause();
  set({ playing: false });
}
function seek(t: number) {
  audio.currentTime = Math.max(0, Math.min(t, audio.duration || t));
  set({ time: audio.currentTime });
}

audio.addEventListener('timeupdate', () => set({ time: audio.currentTime }));
audio.addEventListener('loadedmetadata', () => {
  const t = playlist[state.index];
  if (!t.duration && Number.isFinite(audio.duration)) t.duration = Math.round(audio.duration); // cloud tracks may come without one
  set({ duration: audio.duration || t.duration });
});
audio.addEventListener('ended', () => (state.loop ? (seek(0), play()) : player.next()));
audio.addEventListener('pause', () => state.playing && !audio.ended && set({ playing: false }));
audio.addEventListener('play', () => !state.playing && set({ playing: true }));

/* ---------- lock screen / notification controls ---------- */
const native = Capacitor.isNativePlatform();

/**
 * The native plugin downloads http(s) artwork itself with HttpURLConnection, but https://localhost only
 * exists inside the WebView: the request fails and Capacitor turns that into an app crash.
 * So on Android the cover is handed over as a small base64 JPEG instead.
 */
function coverDataUrl(src: string): Promise<string | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous'; // cloud covers come from the Worker; without this the canvas is tainted
    img.onload = () => {
      try {
        const s = Math.min(512, img.naturalWidth, img.naturalHeight) || 512;
        const c = document.createElement('canvas');
        c.width = c.height = s;
        const k = Math.max(s / img.naturalWidth, s / img.naturalHeight);
        const w = img.naturalWidth * k, h = img.naturalHeight * k;
        c.getContext('2d')!.drawImage(img, (s - w) / 2, (s - h) / 2, w, h);
        resolve(c.toDataURL('image/jpeg', 0.85));
      } catch { resolve(null); }
    };
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

let sessionSeq = 0;
async function updateSession(t: Track) {
  const meta = { title: t.title, artist: t.artist, album: 'Kawaii Radio' };
  if (native) {
    const seq = ++sessionSeq;
    const cover = await coverDataUrl(t.coverArt);
    if (seq !== sessionSeq) return; // a newer track was selected meanwhile
    const artwork = cover ? [{ src: cover, sizes: '512x512', type: 'image/jpeg' }] : [];
    MediaSession.setMetadata({ ...meta, artwork }).catch(() => {});
  } else if ('mediaSession' in navigator) {
    const artwork = [{ src: new URL(t.coverArt, location.href).href, sizes: '512x512', type: 'image/webp' }];
    navigator.mediaSession.metadata = new MediaMetadata({ ...meta, artwork });
  }
}
const actions: [MediaSessionAction, () => void][] = [
  ['play', play], ['pause', pause], ['nexttrack', () => player.next()], ['previoustrack', () => player.prev()],
];
for (const [a, fn] of actions) {
  if (native) MediaSession.setActionHandler({ action: a }, fn).catch(() => {});
  else if ('mediaSession' in navigator) { try { navigator.mediaSession.setActionHandler(a, fn); } catch { /* unsupported */ } }
}
// only report real changes (timeupdate fires several times a second), and don't start the
// native foreground service / notification before the user has played anything
let lastPs: 'playing' | 'paused' | null = null;
listeners.add(() => {
  const ps = state.playing ? 'playing' : 'paused';
  if (ps === lastPs || (lastPs === null && ps === 'paused')) return;
  lastPs = ps;
  if (native) MediaSession.setPlaybackState({ playbackState: ps }).catch(() => {});
  else if ('mediaSession' in navigator) navigator.mediaSession.playbackState = ps;
});
updateSession(playlist[state.index]);

/** Pulls the cloud playlist on launch and whenever the app comes back to the foreground. */
async function refreshCatalog() {
  const tracks = await fetchCloudTracks();
  if (!tracks) return;
  const currentId = playlist[state.index]?.id;
  mergeCloud(tracks);
  const i = playlist.findIndex((t) => t.id === currentId);
  set({ catalog: state.catalog + 1, index: i >= 0 ? i : Math.min(state.index, playlist.length - 1) });
}
refreshCatalog();
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') refreshCatalog(); });

/** Player state without the playback clock: re-renders on track / play / like… changes only. */
export function usePlayer(): PlayerState {
  return useSyncExternalStore(subscribe, getSnapshot);
}
/** Same state, but also re-renders while the song plays (for the clock, waveform and lyrics). */
export function usePlayerTime(): PlayerState {
  return useSyncExternalStore(subscribeTime, () => state);
}
// usePlayer's snapshot only moves on non-time changes, so time ticks never re-render its users
let snapshot = state;
listeners.add(() => { snapshot = state; });
const getSnapshot = () => snapshot;
function subscribe(l: () => void) { listeners.add(l); return () => { listeners.delete(l); }; }
function subscribeTime(l: () => void) { timeListeners.add(l); return () => { timeListeners.delete(l); }; }
export const getState = () => state;
export { playlist };
