import { motion } from 'motion/react';
import { useEffect, useRef, useState } from 'react';
import { player, playlist, usePlayerTime } from '../audio/player';
import { placeholderPeaks, trackPeaks } from '../lib/waveform';
import { press } from '../lib/feedback';

const BARS = 220; // bars across the whole track
const GAP = 4.2; // px per bar on screen

const fmt = (s: number) => {
  const v = Math.max(0, Math.floor(s || 0));
  return `${Math.floor(v / 60)}:${String(v % 60).padStart(2, '0')}`;
};

function Noise() {
  const cv = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    let raf = 0;
    const loop = () => {
      const c = cv.current;
      if (c) {
        const g = c.getContext('2d')!;
        const img = g.createImageData(c.width, c.height);
        for (let i = 0; i < img.data.length; i += 4) {
          img.data[i] = 38; img.data[i + 1] = 46; img.data[i + 2] = 30; img.data[i + 3] = Math.random() < 0.3 ? 120 : 0;
        }
        g.putImageData(img, 0, 0);
      }
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);
  return <canvas ref={cv} width={120} height={24} className="pointer-events-none absolute inset-0 h-full w-full [image-rendering:pixelated]" />;
}

export function WaveLCD() {
  const s = usePlayerTime();
  const t = playlist[s.index];
  const [peaks, setPeaks] = useState(() => placeholderPeaks(t.id, BARS));
  useEffect(() => {
    let alive = true;
    setPeaks(placeholderPeaks(t.id, BARS));
    trackPeaks(t.audioSrc, BARS).then((p) => alive && setPeaks(p)).catch(() => {});
    return () => { alive = false; };
  }, [t.id, t.audioSrc]);

  const progress = s.duration ? s.time / s.duration : 0;
  const offset = progress * BARS * GAP;
  const drag = useRef<{ x: number; t: number } | null>(null);

  return (
    <div className="lcd relative overflow-hidden rounded-[16px] border-2 border-[#3a4131] px-3.5 pb-1.5 pt-1.5">
      <motion.div className="pointer-events-none absolute inset-0" animate={{ opacity: s.playing ? 1 : 0.35 }} transition={{ duration: 0.6 }}
        style={{ background: 'radial-gradient(90% 80% at 35% 25%, rgba(240,255,200,0.4), transparent 70%)' }} />

      <div className="relative flex items-center justify-between gap-3 font-pixel text-[15px] leading-none">
        <span className="flex min-w-0 items-center gap-2 lcd-text">
          <svg width="15" height="15" viewBox="0 0 16 16" className="shrink-0" fill="currentColor"><path d="M2 2h12v2H8v8H2V8h4V4H2zM10 6h4v8h-4z" opacity="0.9" /></svg>
          <span className="truncate uppercase">{t.title}</span>
        </span>
        <span className="relative shrink-0">
          <span className="text-[var(--lcd-ghost)]">88:88 SEC</span>
          <span className="absolute right-0 top-0 lcd-text">{fmt(s.time)}<span className="text-[var(--lcd-ghost)]"> SEC</span></span>
        </span>
      </div>

      {/* waveform: scrolls under a fixed centre playhead; drag to scrub */}
      <div
        className="relative mt-1 h-[30px] touch-none overflow-hidden"
        onPointerDown={(e) => { (e.target as HTMLElement).setPointerCapture(e.pointerId); drag.current = { x: e.clientX, t: s.time }; }}
        onPointerMove={(e) => {
          if (!drag.current || !e.buttons) return;
          const dt = (-(e.clientX - drag.current.x) / (BARS * GAP)) * s.duration;
          player.seek(drag.current.t + dt);
        }}
        onPointerUp={() => (drag.current = null)}
      >
        <div className="absolute inset-y-0 left-1/2" style={{ transform: `translateX(${-offset}px)` }}>
          {peaks.map((p, i) => {
            const played = i * GAP < offset;
            return (
              <span key={i} className="absolute top-1/2 w-[2px] -translate-y-1/2 rounded-[1px]"
                style={{ left: i * GAP, height: `${Math.max(8, p * 100)}%`, background: played ? 'rgba(38,46,30,0.9)' : 'rgba(38,46,30,0.38)' }} />
            );
          })}
        </div>
        <div className="absolute inset-x-0 top-1/2 h-px bg-[rgba(38,46,30,0.15)]" />
        <div className="absolute left-1/2 top-[2px] bottom-[2px] w-[3px] -translate-x-1/2 rounded-sm bg-[var(--lcd-ink)]" />
        {s.tuning ? <Noise /> : null}
      </div>

      <div className="relative mt-1.5 h-px bg-[rgba(38,46,30,0.2)]" />
      <div className="relative mt-1.5 flex items-center justify-between font-pixel text-[15px] leading-none">
        {/* lyric language: always on the panel, lit only when the song has that transcript */}
        <span className="flex gap-3">
          {(['uz', 'jp'] as const).map((l) => {
            const available = l === 'uz' ? !!t.lyrics : !!t.lyrics?.romaji;
            const on = available && s.lang === l;
            return (
              <button key={l} disabled={!available} onClick={() => { press(); player.setLang(l); }}
                className={`-my-2 py-2 uppercase ${on ? 'lcd-text' : available ? 'opacity-45' : 'text-[var(--lcd-ghost)]'}`}>
                {l === 'uz' ? 'UZ' : 'JP'} {on ? '●' : '○'}
              </button>
            );
          })}
        </span>
        <span className="text-[var(--lcd-ghost)] max-[360px]:hidden">{t.bpm} BPM</span>
        <span className={s.playing ? 'lcd-text' : 'text-[var(--lcd-ghost)]'}>{s.tuning ? 'TUNING ●' : 'ON AIR ●'}</span>
      </div>
    </div>
  );
}
