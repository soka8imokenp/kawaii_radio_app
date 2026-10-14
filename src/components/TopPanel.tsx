import { AnimatePresence, motion, useTransform, type MotionValue, type PanInfo } from 'motion/react';
import { useEffect, useMemo, useRef } from 'react';
import { player, playlist, usePlayer, usePlayerTime } from '../audio/player';
import { press } from '../lib/feedback';
import { lineAt, parseLrc } from '../lib/lrc';

export type Tab = 'JPOP' | 'OST' | 'SAVED';
export const isOst = (c: string) => c.toLowerCase().includes('ost');
export const tabList = (tab: Tab, saved: string[] = []) =>
  playlist.map((t, i) => ({ t, i })).filter(({ t }) => (tab === 'SAVED' ? saved.includes(t.id) : tab === 'OST' ? isOst(t.category) : !isOst(t.category)));

const TABS: [Tab, string][] = [['JPOP', 'J-Pop'], ['OST', 'Anime OST'], ['SAVED', 'Saqlangan']];

function Segmented({ tab, onTab }: { tab: Tab; onTab: (t: Tab) => void }) {
  const k = TABS.findIndex(([t]) => t === tab);
  return (
    <div className="relative grid grid-cols-3 rounded-[15px] bg-[#141518] p-[3px] shadow-[inset_0_1px_3px_rgba(0,0,0,0.7),0_1px_0_rgba(255,255,255,0.06)]">
      <motion.div
        className="absolute bottom-[3px] top-[3px] rounded-[12px]"
        style={{ width: 'calc((100% - 6px) / 3)', background: 'linear-gradient(180deg, #4a4c53, #393b41)', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.14), 0 3px 8px rgba(0,0,0,0.5)' }}
        animate={{ left: `calc(3px + ${k} * (100% - 6px) / 3)` }}
        transition={{ type: 'spring', stiffness: 520, damping: 36 }}
      />
      {TABS.map(([key, label]) => (
        <button key={key} onClick={() => { if (key !== tab) { press(); onTab(key); } }}
          className={`relative z-10 flex items-center justify-center py-[7px] font-pixel text-[14px] transition-colors ${tab === key ? 'text-white' : 'text-[#868992]'}`}>
          {label}
        </button>
      ))}
    </div>
  );
}

/** Cover-flow: active cover big and glossy with a floor reflection, neighbours tilt away. */
function Covers({ list }: { list: { t: (typeof playlist)[number]; i: number }[] }) {
  const s = usePlayer();
  const active = list.findIndex(({ i }) => i === s.index);
  const centre = active < 0 ? 0 : active;
  return (
    <motion.div
      className="relative h-[260px] touch-pan-y"
      onPanEnd={(_, info) => {
        if (Math.abs(info.offset.x) < 40) return;
        const next = centre + (info.offset.x < 0 ? 1 : -1);
        if (next >= 0 && next < list.length) { press(); player.select(list[next].i); }
      }}
    >
      {list.map(({ t, i }, k) => {
        const d = k - centre;
        const ad = Math.abs(d);
        if (ad > 2) return null;
        const isActive = i === s.index;
        return (
          <motion.button
            key={t.id}
            onClick={() => { press(); player.select(i); }}
            className="absolute left-1/2 top-[4px] h-[236px] w-[236px]"
            initial={false}
            animate={{ x: `calc(-50% + ${d * 184}px)`, scale: ad === 0 ? 1 : 0.68 - (ad - 1) * 0.1, rotateY: d === 0 ? 0 : d < 0 ? 42 : -42, opacity: ad === 0 ? 1 : ad === 1 ? 0.6 : 0.22, zIndex: 10 - ad }}
            transition={{ type: 'spring', stiffness: 260, damping: 28 }}
            style={{ transformPerspective: 700, WebkitBoxReflect: 'below 6px linear-gradient(transparent 62%, rgba(255,255,255,0.16))' } as React.CSSProperties}
          >
            {/* bezel + inner hairline, like the reference tiles */}
            <div className="h-full w-full rounded-[24px] p-[3px]"
              style={{ background: isActive ? 'linear-gradient(180deg, #ffffff, #cfd2da)' : 'linear-gradient(180deg, #3a3c42, #1c1d21)', boxShadow: isActive ? '0 16px 30px rgba(0,0,0,0.55)' : '0 8px 16px rgba(0,0,0,0.45)' }}>
              <div className="relative h-full w-full overflow-hidden rounded-[21px] shadow-[inset_0_0_0_1px_rgba(0,0,0,0.35)]">
                <img src={t.coverArt} alt="" className="h-full w-full object-cover" draggable={false} />
                <div className="pointer-events-none absolute inset-0" style={{ background: 'linear-gradient(150deg, rgba(255,255,255,0.32) 0%, rgba(255,255,255,0.06) 32%, rgba(255,255,255,0) 50%, rgba(0,0,0,0.28) 100%)' }} />
              </div>
            </div>
            {isActive ? (
              <span className="absolute left-2.5 top-2.5 flex h-6 w-6 items-center justify-center rounded-full bg-white shadow-[0_2px_6px_rgba(0,0,0,0.35)]">
                {s.playing ? <EqIcon /> : <svg width="12" height="12" viewBox="0 0 16 16"><path d="M3 8.5l3 3 7-7" stroke="#222" strokeWidth="2.4" fill="none" strokeLinecap="round" strokeLinejoin="round" /></svg>}
              </span>
            ) : null}
          </motion.button>
        );
      })}
    </motion.div>
  );
}

function EqIcon() {
  return (
    <span className="flex h-3 items-end gap-[2px]">
      {[0, 1, 2].map((k) => (
        <span key={k} className="eq-bar h-full w-[3px] rounded-sm bg-[#1d1e22]" style={{ animationDuration: `${0.8 + k * 0.17}s` }} />
      ))}
    </span>
  );
}

const NO_LYRICS = "Bu qo'shiq uchun matn hali yo'q";

function useLyrics() {
  const s = usePlayer();
  const t = playlist[s.index];
  const src = t.lyrics ? (s.lang === 'jp' && t.lyrics.romaji ? t.lyrics.romaji : t.lyrics.uzbek) : '';
  return useMemo(() => (src ? parseLrc(src) : []), [src]);
}

/** Two-line karaoke readout of the collapsed panel. */
function LyricLines() {
  const s = usePlayerTime();
  const lines = useLyrics();
  const li = lineAt(lines, s.time);
  const cur = li >= 0 ? lines[li].text || '♪ ♪ ♪' : '♪ · · ·';
  if (!lines.length) {
    return <div className="flex h-full items-center font-dot text-[14px] text-[#5d6150]">{NO_LYRICS}</div>;
  }
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div key={`${s.lang}-${li}`} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.2 }}>
        <div className="line-clamp-2 text-[17px] leading-[24px] text-[#d4e2a6] [text-shadow:0_0_10px_rgba(196,224,120,0.35)]">{cur}</div>
        <div className="truncate text-[13px] leading-[22px] text-[#5d6150]">{lines[li + 1]?.text ?? ''}</div>
      </motion.div>
    </AnimatePresence>
  );
}

/** Whole song in the pulled-down panel; the current line stays centred and lit. */
function FullLyrics() {
  const s = usePlayerTime();
  const lines = useLyrics();
  const li = lineAt(lines, s.time);
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = box.current;
    const line = el?.querySelector<HTMLElement>(`[data-l="${li}"]`);
    if (el && line) el.scrollTo({ top: line.offsetTop - el.clientHeight / 2 + line.offsetHeight / 2, behavior: 'smooth' });
  }, [li]);
  if (!lines.length) {
    return <div className="flex h-full items-center justify-center text-center font-dot text-[15px] text-[#5d6150]">{NO_LYRICS}</div>;
  }
  return (
    <div ref={box} className="no-scrollbar h-full overflow-y-auto py-[40%] [mask-image:linear-gradient(transparent,#000_18%,#000_82%,transparent)]">
      {lines.map((l, k) => (
        <button key={k} data-l={k} onClick={() => player.seek(l.t)}
          className={`block w-full py-[5px] text-left text-[17px] leading-[24px] transition-[color,opacity] duration-300 ${k === li ? 'text-[#d4e2a6] [text-shadow:0_0_10px_rgba(196,224,120,0.35)]' : 'text-[#5d6150]'}`}>
          {l.text || '♪'}
        </button>
      ))}
    </div>
  );
}

/** Song card: lyrics when the song has them, otherwise a note that there are none. Grows when the panel is pulled down. */
function NowCard({ ext, open, onOpen }: { ext: MotionValue<number>; open: boolean; onOpen: () => void }) {
  const s = usePlayer();
  const t = playlist[s.index];
  const lines = useLyrics();
  const height = useTransform(ext, (v) => 70 + v);
  const canJp = !!t.lyrics?.romaji;
  return (
    <div onClick={() => !open && onOpen()}
      className="relative z-20 overflow-hidden rounded-[16px] bg-[#1b1c1f] px-4 pb-2.5 pt-2.5 shadow-[inset_0_1px_3px_rgba(0,0,0,0.6),0_1px_0_rgba(255,255,255,0.05)]">
      <div className="flex items-center gap-2">
        <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${s.playing ? 'bg-[#ff4d5e] shadow-[0_0_8px_#ff4d5e]' : 'bg-[#4a4c52]'}`} />
        <span className="min-w-0 truncate text-[14px] font-bold text-white">{t.title}</span>
        <span className="min-w-0 shrink truncate text-[12px] text-[#7d818a]">{t.artist}</span>
        {lines.length ? (
          <button disabled={!canJp} onClick={(e) => { e.stopPropagation(); press(); player.setLang(s.lang === 'jp' ? 'uz' : 'jp'); }}
            className="-my-2 ml-auto shrink-0 py-2 pl-2 font-dot text-[11px] text-[#6f727b]">
            {s.lang === 'jp' && canJp ? '日本' : "O'ZB"}{canJp ? ' ⇄' : ''}
          </button>
        ) : (
          <span className="ml-auto shrink-0 font-dot text-[11px] text-[#6f727b]">{s.playing ? 'EFIRDA' : 'TAYYOR'}</span>
        )}
      </div>
      <motion.div className="relative mt-1.5 overflow-hidden font-dot" style={{ height }}>
        <AnimatePresence initial={false} mode="wait">
          {open ? (
            <motion.div key="full" className="absolute inset-0" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
              <FullLyrics />
            </motion.div>
          ) : (
            <motion.div key="lines" className="absolute inset-x-0 top-0 h-[70px]" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
              <LyricLines />
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  );
}

function EmptySaved({ premium, onPremium }: { premium: boolean; onPremium: () => void }) {
  return (
    <div className="flex h-[260px] flex-col items-center justify-center gap-1.5 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-[16px] bg-[#1a1b1e] shadow-[inset_0_1px_3px_rgba(0,0,0,0.7)]">
        <svg width="20" height="20" viewBox="0 0 16 16" fill="#ffd36b"><path d="M3 1.5h10v13l-5-3.2-5 3.2z" /></svg>
      </div>
      <div className="text-[14px] font-semibold text-white">{premium ? "Hali saqlangan qo'shiq yo'q" : 'Kawaii Pass imkoniyati'}</div>
      <div className="max-w-[250px] text-[12px] leading-snug text-[#868992]">
        {premium ? "«Saqlash» tugmasi bilan qo'shiqlarni ilova ichida saqlang." : "Sevimli qo'shiqlarni ilova ichida saqlab, internetsiz tinglang."}
      </div>
      {!premium ? <button onClick={() => { press(); onPremium(); }} className="mt-1 rounded-full bg-gradient-to-b from-[#ffe08a] to-[#f5b93b] px-4 py-1.5 text-[12px] font-bold text-[#3a2a05] shadow-[0_4px_12px_rgba(245,185,59,0.35),inset_0_1px_0_rgba(255,255,255,0.6)]">Kawaii Pass</button> : null}
    </div>
  );
}

export interface Drawer {
  ext: MotionValue<number>;  // how far the panel is pulled down (px)
  open: boolean;
  onPan: (info: PanInfo) => void;
  onPanStart: () => void;
  onPanEnd: (info: PanInfo) => void;
  toggle: () => void;
}

export function TopPanel({ tab, onTab, onPremium, drawer, panelRef }: {
  tab: Tab; onTab: (t: Tab) => void; onPremium: () => void; drawer: Drawer; panelRef: React.Ref<HTMLElement>;
}) {
  const s = usePlayer();
  const t = playlist[s.index];
  const list = useMemo(() => tabList(tab, s.saved), [tab, s.saved, s.catalog]);
  // the panel grows over the controls without pushing them (they slide away by themselves)
  const pull = useTransform(drawer.ext, (v) => -v);
  return (
    <motion.section ref={panelRef} className="relative z-20 shrink-0 overflow-hidden rounded-b-[30px] px-4 pb-0 pt-[max(12px,env(safe-area-inset-top))]"
      style={{ marginBottom: pull, background: 'linear-gradient(180deg, #2c2e33 0%, #232428 60%, #1f2023 100%)', boxShadow: '0 12px 26px rgba(0,0,0,0.38), inset 0 -1px 0 rgba(255,255,255,0.06)' }}>
      {/* ambient light from the current cover */}
      <AnimatePresence>
        {/* drawn small and scaled up: the same glow for a fraction of the blur cost */}
        <motion.img key={t.coverArt} src={t.coverArt} alt="" aria-hidden
          className="pointer-events-none absolute left-1/2 top-[220px] h-[70px] w-[70px] rounded-full object-cover"
          style={{ x: '-50%', y: '-50%', scale: 4, filter: 'blur(12px) saturate(1.6)' }}
          initial={{ opacity: 0 }} animate={{ opacity: s.playing ? 0.55 : 0.32 }} exit={{ opacity: 0 }} transition={{ duration: 0.8 }} />
      </AnimatePresence>
      <div className="pointer-events-none absolute inset-0 opacity-[0.05]" style={{ backgroundImage: 'radial-gradient(#fff 0.6px, transparent 0.8px)', backgroundSize: '4px 4px' }} />

      <div className="relative">
        <div className="flex items-center justify-between pt-0.5">
          <div className="flex items-center gap-2.5">
            <img src="/logo.svg" alt="Kawaii" className="h-[26px] w-[26px] [filter:brightness(0)_invert(0.94)]" />
            <div className="leading-none">
              <div className="text-[16px] font-bold tracking-[0.01em] text-white">Kawaii Radio</div>
              <div className="mt-[3px] font-pixel text-[11px] tracking-[0.12em] text-[#7d818a]">KR-01 · J-POP FM</div>
            </div>
          </div>
          <div className="flex items-center gap-2 rounded-[9px] bg-[#141518] px-2.5 py-[5px] shadow-[inset_0_1px_3px_rgba(0,0,0,0.7),0_1px_0_rgba(255,255,255,0.05)]">
            <span className={`h-[7px] w-[7px] rounded-full ${s.playing ? 'bg-[#ff4d5e] shadow-[0_0_8px_#ff4d5e]' : 'bg-[#4a4c52]'}`} />
            <span className={`font-pixel text-[12px] ${s.playing ? 'text-[#ff8a95]' : 'text-[#6f727b]'}`}>ON AIR</span>
          </div>
        </div>
        <div className="mt-2"><Segmented tab={tab} onTab={onTab} /></div>
        <div className="mt-2.5">{list.length ? <Covers list={list} /> : <EmptySaved premium={s.premium} onPremium={onPremium} />}</div>
        <div className="mt-1"><NowCard ext={drawer.ext} open={drawer.open} onOpen={drawer.toggle} /></div>
        {/* grip: drag down to open the lyrics, up to close; tap toggles */}
        <motion.div className="flex h-[22px] cursor-grab items-center justify-center" style={{ touchAction: 'none' }}
          onPanStart={drawer.onPanStart} onPan={(_, i) => drawer.onPan(i)} onPanEnd={(_, i) => drawer.onPanEnd(i)} onTap={drawer.toggle}>
          <motion.div className="h-[5px] w-10 rounded-full bg-[#4a4c52] shadow-[inset_0_1px_1px_rgba(0,0,0,0.5)]"
            animate={{ width: drawer.open ? 28 : 40, backgroundColor: drawer.open ? '#6a6d75' : '#4a4c52' }} />
        </motion.div>
      </div>
    </motion.section>
  );
}
