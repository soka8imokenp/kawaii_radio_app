import { AnimatePresence, motion } from 'motion/react';
import { useState } from 'react';
import { player, playlist, usePlayer } from '../audio/player';
import { Icon, Key, Led, Screw } from '../components/parts';
import { TopPanel, tabList, isOst, type Tab } from '../components/TopPanel';
import { Tuner } from '../components/Tuner';
import { WaveLCD } from '../components/WaveLCD';

/** Main radio screen: dark selector panel on top, hardware controls below. */
export function Home({ onInfo, onPremium }: { onInfo: () => void; onPremium: () => void }) {
  const s = usePlayer();
  const t = playlist[s.index];
  const [tab, setTab] = useState<Tab>(isOst(t.category) ? 'OST' : 'JPOP');
  const liked = s.liked.includes(t.id);
  const isSaved = s.saved.includes(t.id);
  // tuner walks the current band; an empty Saved band falls back to J-Pop/OST
  const band = tabList(tab, s.saved).length ? tabList(tab, s.saved) : tabList(isOst(t.category) ? 'OST' : 'JPOP');
  const pos = Math.max(0, band.findIndex(({ i }) => i === s.index));
  const at = (d: number) => band[(((pos + d) % band.length) + band.length) % band.length];
  const onSave = () => (s.premium ? player.toggleSave(t.id) : onPremium());

  const changeTab = (next: Tab) => {
    setTab(next);
    const nextList = tabList(next, s.saved);
    if (nextList.length && !nextList.some(({ i }) => i === s.index)) player.select(nextList[0].i);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <TopPanel tab={tab} onTab={changeTab} onPremium={onPremium} />

      <section className="relative flex flex-1 flex-col justify-center px-4 pb-1 pt-1.5">
        {/* recessed seam grooves of the housing */}
        <div className="groove-h pointer-events-none absolute inset-x-0 top-0" />
        <div className="relative z-10 flex flex-col">
          <div className="relative">
            <div className="absolute -left-3 top-1/2 -translate-y-1/2"><Screw /></div>
            <div className="absolute -right-3 top-1/2 -translate-y-1/2"><Screw /></div>
            <div className="bezel rounded-[22px] p-[5px]"><WaveLCD /></div>
          </div>

          {/* transport: embossed round keys around the wheel scrubber */}
          <div className="relative mt-2 flex items-center gap-2">
            <div className="groove-h pointer-events-none absolute -inset-x-4 top-1/2 -z-10" />
            <Screw className="-ml-1.5" />
            <Key round className="h-[52px] w-[52px] shrink-0" onPress={() => player.toggle()} ariaLabel={s.playing ? 'Pauza' : 'Ijro'}>
              <AnimatePresence mode="wait" initial={false}>
                <motion.span key={s.playing ? 'pa' : 'pl'} initial={{ scale: 0.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.5, opacity: 0 }} transition={{ duration: 0.12 }}>
                  {s.playing ? Icon.pause : Icon.play}
                </motion.span>
              </AnimatePresence>
            </Key>
            <Screw />
            <Tuner onStep={(d) => player.select(at(d).i)} preview={(d) => at(d).t.title} />
            <Screw />
            <Key round active={liked} className="h-[52px] w-[52px] shrink-0" onPress={() => player.toggleLike(t.id)} ariaLabel="Yoqtirish">
              <motion.span key={String(liked)} initial={{ scale: liked ? 0.4 : 1 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 600, damping: 12 }}>
                {Icon.heart(liked)}
              </motion.span>
            </Key>
            <Screw className="-mr-1.5" />
          </div>

          {/* segmented function keys docked in one tray */}
          <div className="tray mt-2 grid grid-cols-2 gap-[7px] rounded-[20px] p-[4px]">
            <Key cap className="h-[40px] gap-2 rounded-[15px]" onPress={onInfo}>
              {Icon.info}<span className="micro">Tavsif</span>
            </Key>
            <Key cap active={isSaved} className="h-[40px] gap-2 rounded-[15px]" onPress={onSave}>
              {Icon.save}<span className="micro">{isSaved ? 'Saqlangan' : 'Saqlash'}</span>
              {isSaved ? <Led on /> : null}
            </Key>
          </div>
        </div>
      </section>
    </div>
  );
}
