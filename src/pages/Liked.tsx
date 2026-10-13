import { AnimatePresence, motion } from 'motion/react';
import { player, playlist, usePlayer } from '../audio/player';
import { press } from '../lib/feedback';
import { Icon, Led, Plate } from '../components/parts';

/** Liked songs: each row is a key cap; tap plays it, heart removes it. */
export function Liked({ onOpenRadio }: { onOpenRadio: () => void }) {
  const s = usePlayer();
  const list = playlist.map((t, i) => ({ t, i })).filter(({ t }) => s.liked.includes(t.id));
  return (
    <div className="flex min-h-0 flex-1 flex-col px-4 pt-[max(16px,env(safe-area-inset-top))]">
      <header className="flex items-center justify-between pb-3">
        <div>
          <div className="text-[22px] font-bold leading-none text-[var(--ink)]">Sevimlilar</div>
          <div className="mt-1.5 font-pixel text-[14px] text-[var(--ink-soft)]">Yoqtirgan qo'shiqlaringiz</div>
        </div>
        <Plate><Led on={list.length > 0} color="#ff4258" /> {String(list.length).padStart(2, '0')}</Plate>
      </header>
      <div className="groove-h -mx-4" />

      <div className="no-scrollbar -mx-4 flex-1 overflow-y-auto px-4 pb-4 pt-4">
        {list.length === 0 ? (
          <div className="flex flex-col items-center pt-16 text-center">
            <div className="raised flex h-20 w-20 items-center justify-center rounded-full">{Icon.heart(false)}</div>
            <div className="mt-4 text-[16px] font-bold text-[var(--ink)]">Hali sevimli qo'shiq yo'q</div>
            <div className="mt-1 max-w-[240px] text-[13px] text-[var(--ink-soft)]">Radioda yurakcha tugmasini bosing — qo'shiq shu yerda paydo bo'ladi.</div>
            <button onClick={() => { press(); onOpenRadio(); }} className="raised mt-5 rounded-[14px] px-5 py-2.5 text-[13px] font-bold uppercase">Radioga o'tish</button>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <AnimatePresence initial={false}>
              {list.map(({ t, i }, k) => {
                const current = i === s.index;
                return (
                  <motion.div
                    key={t.id}
                    layout
                    initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0, transition: { delay: k * 0.04 } }} exit={{ opacity: 0, x: -40 }}
                    className="rim flex items-center gap-2 rounded-[18px] p-[5px]"
                  >
                    <button onClick={() => { press(); player.select(i); }} className={`cap ${current ? 'is-down' : ''} flex min-w-0 flex-1 items-center gap-3 rounded-[12px] p-2 text-left`}>
                      <img src={t.coverArt} alt="" className="h-12 w-12 shrink-0 rounded-[9px] object-cover shadow-[0_0_0_2px_var(--ring)]" />
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-[15px] font-bold text-[var(--ink)]">{t.title}</div>
                        <div className="truncate font-pixel text-[13px] text-[var(--ink-soft)]">{t.artist}</div>
                      </div>
                      <Led on={current && s.playing} />
                    </button>
                    <button onClick={() => { press(); player.toggleLike(t.id); }} aria-label="Olib tashlash" className="cap flex h-[60px] w-12 items-center justify-center rounded-[12px]">
                      {Icon.heart(true)}
                    </button>
                  </motion.div>
                );
              })}
            </AnimatePresence>
          </div>
        )}
      </div>
    </div>
  );
}
