import { AnimatePresence, motion } from 'motion/react';
import { useMemo, useState, type ReactNode } from 'react';
import { player, playlist, usePlayer } from '../audio/player';
import { press } from '../lib/feedback';
import { Icon, Led } from './parts';

function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.div className="fixed inset-0 z-40 bg-[rgba(40,42,55,0.35)] backdrop-blur-[2px]" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.div
            className="housing fixed inset-x-0 bottom-0 z-50 mx-auto flex max-h-[86dvh] max-w-[480px] flex-col rounded-t-[34px] border-t border-[var(--sheet-border)] pb-[env(safe-area-inset-bottom)] shadow-[0_-20px_40px_rgba(40,42,55,0.25)]"
            initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }}
            transition={{ type: 'spring', stiffness: 380, damping: 36 }}
            drag="y" dragConstraints={{ top: 0, bottom: 0 }} dragElastic={{ top: 0, bottom: 0.6 }}
            onDragEnd={(_, i) => (i.offset.y > 110 || i.velocity.y > 600) && onClose()}
          >
            <div className="flex justify-center pt-3"><div className="well h-[6px] w-14 rounded-full" /></div>
            <div className="flex items-center justify-between px-6 pb-3 pt-3">
              <div className="text-[13px] font-bold uppercase tracking-[0.14em] emboss">{title}</div>
              <button onClick={() => { press(); onClose(); }} className="raised rounded-full px-3 py-1 text-[12px] font-bold uppercase tracking-wider emboss">Yopish</button>
            </div>
            <div className="groove-h mx-5" />
            <div className="no-scrollbar flex-1 overflow-y-auto px-5 pb-6 pt-4">{children}</div>
          </motion.div>
        </>
      ) : null}
    </AnimatePresence>
  );
}

const isOst = (c: string) => c.toLowerCase().includes('ost');

export function LibrarySheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const s = usePlayer();
  const [tab, setTab] = useState<'JPOP' | 'OST' | 'LIKED'>('JPOP');
  const list = useMemo(() => playlist.map((t, i) => ({ t, i })).filter(({ t }) =>
    tab === 'LIKED' ? s.liked.includes(t.id) : tab === 'OST' ? isOst(t.category) : !isOst(t.category)), [tab, s.liked, s.catalog]);
  return (
    <Sheet open={open} onClose={onClose} title="Kassetalar · Tapes">
      {/* physical 3-way switch */}
      <div className="well relative mb-5 grid grid-cols-3 rounded-[16px] p-1">
        <motion.div layout className="raised absolute bottom-1 top-1 rounded-[12px]" style={{ width: 'calc((100% - 8px) / 3)', left: `calc(4px + ${['JPOP', 'OST', 'LIKED'].indexOf(tab)} * (100% - 8px) / 3)` }} transition={{ type: 'spring', stiffness: 500, damping: 34 }} />
        {(['JPOP', 'OST', 'LIKED'] as const).map((k) => (
          <button key={k} onClick={() => { press(); setTab(k); }} className="relative z-10 py-2.5 text-[12px] font-bold uppercase tracking-[0.12em] emboss">
            {k === 'JPOP' ? 'J-Pop' : k === 'OST' ? 'Anime OST' : '♥ Sevimli'}
          </button>
        ))}
      </div>
      <div className="flex flex-col gap-3">
        {list.length === 0 ? <div className="py-10 text-center text-[14px] text-[var(--ink-soft)]">Hali sevimli qo'shiqlar yo'q</div> : null}
        {list.map(({ t, i }, k) => {
          const active = i === s.index;
          return (
            <motion.button
              key={t.id}
              initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: k * 0.035 }}
              onClick={() => { press(); player.select(i); onClose(); }}
              className={`raised ${active ? 'is-down' : ''} flex items-center gap-3 rounded-[20px] p-2.5 text-left`}
            >
              <div className="well relative h-14 w-14 shrink-0 overflow-hidden rounded-[12px] p-[3px]">
                <img src={t.coverArt} alt="" className="h-full w-full rounded-[9px] object-cover" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-[16px] font-bold">{t.title}</div>
                <div className="truncate text-[13px] text-[var(--ink-soft)]">{t.artist} · {t.category}</div>
              </div>
              <div className="flex flex-col items-end gap-1.5 pr-1">
                <Led on={active && s.playing} />
                <span className="font-lcd text-[18px] leading-none text-[var(--ink-soft)]">{t.duration ? `${Math.floor(t.duration / 60)}:${String(t.duration % 60).padStart(2, '0')}` : '–:––'}</span>
              </div>
            </motion.button>
          );
        })}
      </div>
    </Sheet>
  );
}

export function InfoSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const s = usePlayer();
  const t = playlist[s.index];
  return (
    <Sheet open={open} onClose={onClose} title="Qo'shiq haqida">
      <div className="flex gap-4">
        <div className="bezel shrink-0 rounded-[22px] p-[5px]"><img src={t.coverArt} alt="" className="h-28 w-28 rounded-[17px] object-cover" /></div>
        <div className="min-w-0 pt-1">
          <div className="text-[22px] font-bold leading-tight">{t.title}</div>
          <div className="text-[14px] text-[var(--ink-soft)]">{t.artist}</div>
          <div className="mt-3 flex flex-wrap gap-2">
            {[t.category, `${t.bpm} BPM`, `Energiya ${t.energy}%`].map((x) => <span key={x} className="well rounded-lg px-2 py-1 text-[11px] font-bold uppercase tracking-wider emboss">{x}</span>)}
          </div>
        </div>
      </div>
      {t.info ? (
        <div className="lcd mt-5 rounded-[18px] border-[3px] border-[#3b4232] p-4">
          <p className="font-lcd text-[21px] leading-[1.15] lcd-text">{t.info.description}</p>
        </div>
      ) : null}
      <div className="mt-4 flex flex-col gap-3">
        {t.info?.details.map((d) => (
          <div key={d.title} className="well rounded-[18px] p-4">
            <div className="text-[12px] font-bold uppercase tracking-[0.12em] text-[var(--accent)]">{d.title}</div>
            <div className="mt-1 text-[14px] leading-snug">{d.text}</div>
          </div>
        ))}
      </div>
      <button onClick={() => { press(); player.toggleLike(t.id); }} className={`raised ${s.liked.includes(t.id) ? 'is-down' : ''} mt-5 flex w-full items-center justify-center gap-2 rounded-[18px] py-4 text-[14px] font-bold uppercase tracking-[0.12em] emboss`}>
        {Icon.heart(s.liked.includes(t.id))} {s.liked.includes(t.id) ? 'Sevimlilarda' : "Sevimlilarga qo'shish"}
      </button>
    </Sheet>
  );
}

/** Kawaii Pass upsell shown when a free user taps "Saqlash". */
export function PremiumSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const perks: [string, string][] = [
    ["Ilova ichida saqlash", "Qo'shiqlar «Saqlangan» bo'limida doim qo'l ostingizda"],
    ['Internetsiz tinglash', "Saqlangan qo'shiqlar oflayn ham ijro etiladi"],
    ['Reklamasiz efir', "Hech narsa musiqangizni bo'lmaydi"],
  ];
  return (
    <Sheet open={open} onClose={onClose} title="Kawaii Pass">
      <div className="lcd rounded-[18px] border-[3px] border-[#3b4232] p-4 text-center">
        <div className="font-pixel text-[22px] leading-none lcd-text">KAWAII PASS</div>
        <div className="mt-2 font-pixel text-[13px] leading-snug opacity-70">Saqlash funksiyasi obunachilar uchun</div>
      </div>
      <div className="mt-4 flex flex-col gap-3">
        {perks.map(([title, text], i) => (
          <motion.div key={title} className="well flex items-start gap-3 rounded-[18px] p-4" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 + i * 0.06 }}>
            <Led on color="#f5b93b" />
            <div>
              <div className="text-[14px] font-bold">{title}</div>
              <div className="mt-0.5 text-[13px] leading-snug text-[var(--ink-soft)]">{text}</div>
            </div>
          </motion.div>
        ))}
      </div>
      <button
        onClick={() => { press(); window.open('https://kawaii.uz/kawaii-pass', '_blank'); }}
        className="mt-5 w-full rounded-[18px] bg-gradient-to-b from-[#ffe08a] to-[#f5b93b] py-4 text-[14px] font-bold uppercase tracking-[0.1em] text-[#3a2a05] shadow-[0_8px_18px_rgba(245,185,59,0.35),inset_0_1px_0_rgba(255,255,255,0.7),inset_0_-2px_0_rgba(160,100,10,0.25)]"
      >
        Obuna bo'lish
      </button>
    </Sheet>
  );
}
