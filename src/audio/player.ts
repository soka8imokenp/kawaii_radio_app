import { useSyncExternalStore } from 'react';
import { Capacitor } from '@capacitor/core';
import { MediaSession } from '@jofr/capacitor-media-session';
import { playlist } from '../data/playlist';
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
}

const LS = 'kr01';
const saved = (() => {
  try { return JSON.parse(localStorage.getItem(LS) || '{}'); } catch { return {}; }
})();

let state: PlayerState = {
  index: typeof saved.index === 'number' ? Math.min(saved.index, playlist.length - 1) : 0,
  playing: false,
  time: 0,
  duration: playlist[0].duration,
  volume: typeof saved.volume === 'number' ? saved.volume : 0.8,
  shuffle: !!saved.shuffle,
  loop: !!saved.loop,
  liked: Array.isArray(saved.liked) ? saved.liked : [],
  saved: Array.isArray(saved.saved) ? saved.saved : [],
  premium: (() => { try { return localStorage.getItem('kr01-premium') === '1'; } catch { return false; } })(),
  lang: saved.lang === 'jp' ? 'jp' : 'uz',
  tuning: false,
  preview: 0,
};

const listeners = new Set<() => void>();
function set(patch: Partial<PlayerState>) {
  state = { ...state, ...patch };
  listeners.forEach((l) => l());
  if ('index' in patch || 'volume' in patch || 'shuffle' in patch || 'loop' in patch || 'liked' in patch || 'saved' in patch || 'lang' in patch) {
    try { localStorage.setItem(LS, JSON.stringify({ index: state.index, volume: state.volume, shuffle: state.shuffle, loop: state.loop, liked: state.liked, saved: state.saved, lang: state.lang })); } catch { /* private mode */ }
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
audio.addEventListener('loadedmetadata', () => set({ duration: audio.duration || playlist[state.index].duration }));
audio.addEventListener('ended', () => (state.loop ? (seek(0), play()) : player.next()));
audio.addEventListener('pause', () => state.playing && !audio.ended && set({ playing: false }));
audio.addEventListener('play', () => !state.playing && set({ playing: true }));

/* ---------- lock screen / notification controls ---------- */
const native = Capacitor.isNativePlatform();
function updateSession(t: Track) {
  const artwork = [{ src: new URL(t.coverArt, location.href).href, sizes: '512x512', type: 'image/webp' }];
  const meta = { title: t.title, artist: t.artist, album: 'Kawaii Radio', artwork };
  if (native) MediaSession.setMetadata(meta).catch(() => {});
  else if ('mediaSession' in navigator) navigator.mediaSession.metadata = new MediaMetadata(meta);
}
const actions: [MediaSessionAction, () => void][] = [
  ['play', play], ['pause', pause], ['nexttrack', () => player.next()], ['previoustrack', () => player.prev()],
];
for (const [a, fn] of actions) {
  if (native) MediaSession.setActionHandler({ action: a }, fn).catch(() => {});
  else if ('mediaSession' in navigator) { try { navigator.mediaSession.setActionHandler(a, fn); } catch { /* unsupported */ } }
}
listeners.add(() => {
  const ps = state.playing ? 'playing' : 'paused';
  if (native) MediaSession.setPlaybackState({ playbackState: ps }).catch(() => {});
  else if ('mediaSession' in navigator) navigator.mediaSession.playbackState = ps;
});
updateSession(playlist[state.index]);

export function usePlayer(): PlayerState {
  return useSyncExternalStore((l) => { listeners.add(l); return () => listeners.delete(l); }, () => state);
}
export const getState = () => state;
export { playlist };
