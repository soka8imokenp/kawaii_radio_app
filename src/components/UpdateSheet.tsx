import { AnimatePresence, motion } from 'motion/react';
import { useState } from 'react';
import { press } from '../lib/feedback';
import { downloadUpdate, installUpdate, type UpdateInfo } from '../lib/updater';
import { Key, Led } from './parts';

type Phase = 'ready' | 'downloading' | 'done' | 'error';
const BLOCKS = 22;
const mb = (b: number) => (b / 1_048_576).toFixed(1);

/** Cassette with two reels: they spin while the update streams in, tape moves from left to right reel. */
function Cassette({ spinning, progress }: { spinning: boolean; progress: number }) {
  const reel = (side: 'l' | 'r') => {
    const tape = side === 'l' ? 1 - progress : progress; // tape amount on this reel
    return (
      <div className="relative flex h-[46px] w-[46px] items-center justify-center">
        <div className="absolute rounded-full bg-[#2f3a24]" style={{ width: 22 + tape * 22, height: 22 + tape * 22, opacity: 0.85 }} />
        <motion.svg width="26" height="26" viewBox="-13 -13 26 26" className="relative"
          animate={spinning ? { rotate: 360 } : { rotate: 0 }}
          transition={spinning ? { duration: side === 'l' ? 1.1 : 0.8, repeat: Infinity, ease: 'linear' } : { duration: 0.3 }}>
          <circle r="11" fill="#d7e0b6" stroke="#262e1e" strokeWidth="1.6" />
          {[0, 60, 120, 180, 240, 300].map((a) => (
            <rect key={a} x="-1.4" y="-10" width="2.8" height="4.2" rx="0.6" fill="#262e1e" transform={`rotate(${a})`} />
          ))}
          <circle r="3.2" fill="#262e1e" />
        </motion.svg>
      </div>
    );
  };
  return (
    <div className="relative mx-auto flex h-[64px] w-[170px] items-center justify-between rounded-[10px] border-2 border-[#262e1e] px-3">
      {reel('l')}
      <div className="absolute inset-x-[52px] top-1/2 h-[2px] -translate-y-1/2 bg-[#262e1e] opacity-60" />
      {reel('r')}
      <div className="absolute -bottom-[2px] left-1/2 h-[8px] w-[54px] -translate-x-1/2 rounded-t-[4px] border-2 border-b-0 border-[#262e1e]" />
    </div>
  );
}

export function UpdateSheet({ info, onClose }: { info: UpdateInfo | null; onClose: () => void }) {
  const [phase, setPhase] = useState<Phase>('ready');
  const [done, setDone] = useState(0);
  const [total, setTotal] = useState(0);
  const [uri, setUri] = useState('');
  const progress = total ? Math.min(1, done / total) : 0;

  const start = async () => {
    if (!info) return;
    press();
    setPhase('downloading');
    setDone(0);
    setTotal(info.size);
    try {
      const file = await downloadUpdate(info, (d, t) => { setDone(d); setTotal(t); });
      setUri(file);
      setPhase('done');
      await installUpdate(file).catch(() => setPhase('done'));
    } catch {
      setPhase('error');
    }
  };

  return (
    <AnimatePresence>
      {info ? (
        <>
          <motion.div className="fixed inset-0 z-[70] bg-[rgba(30,32,40,0.45)] backdrop-blur-[3px]" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => phase !== 'downloading' && onClose()} />
          <motion.div
            className="housing fixed inset-x-0 bottom-0 z-[80] mx-auto max-w-[480px] rounded-t-[30px] px-5 pb-[max(18px,env(safe-area-inset-bottom))] pt-3 shadow-[0_-18px_40px_rgba(30,32,40,0.3)]"
            initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: 'spring', stiffness: 360, damping: 34 }}
          >
            <div className="flex justify-center"><div className="tray h-[5px] w-12 rounded-full" /></div>

            <div className="mt-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Led on color={phase === 'error' ? 'var(--led-red)' : 'var(--led-green)'} pulse={phase === 'downloading' ? 1.6 : 1} />
                <span className="micro">{phase === 'error' ? 'Xatolik' : phase === 'done' ? 'Tayyor' : 'Yangilanish'}</span>
              </div>
              <span className="micro text-[10px] text-[var(--ink-soft)]">KR-01 · OTA</span>
            </div>
            <div className="mt-2 text-[22px] font-extrabold leading-tight text-[var(--ink)]">
              {phase === 'done' ? "O'rnatishga tayyor!" : phase === 'error' ? 'Yuklab bo\'lmadi' : 'Yangi versiya chiqdi!'}
            </div>

            {/* LCD with version readout, cassette and pixel progress */}
            <div className="bezel mt-3 rounded-[22px] p-[6px]">
              <div className="lcd relative overflow-hidden rounded-[16px] border-2 border-[#3a4131] px-4 pb-3 pt-3">
                <div className="flex items-center justify-between font-pixel text-[14px] leading-none">
                  <span className="lcd-text">HOZIR v{info.current}</span>
                  <span className="lcd-text">→ v{info.version}</span>
                </div>
                <div className="mt-3"><Cassette spinning={phase === 'downloading'} progress={progress} /></div>
                <div className="mt-3 flex gap-[3px]">
                  {new Array(BLOCKS).fill(0).map((_, i) => {
                    const lit = i < Math.round(progress * BLOCKS);
                    return (
                      <motion.div key={i} className="h-[12px] flex-1 rounded-[1px]"
                        animate={{ backgroundColor: lit ? 'rgba(38,46,30,0.88)' : 'rgba(38,46,30,0.1)', scaleY: lit && i === Math.round(progress * BLOCKS) - 1 ? [1, 1.35, 1] : 1 }}
                        transition={{ duration: 0.2 }} />
                    );
                  })}
                </div>
                <div className="mt-2 flex items-end justify-between font-pixel leading-none">
                  <span className="text-[26px] lcd-text">{Math.round(progress * 100)}%</span>
                  <span className="text-[13px] lcd-text">{total ? `${mb(done)} / ${mb(total)} MB` : `${mb(info.size)} MB`}</span>
                </div>
              </div>
            </div>

            {/* what's new */}
            <div className="tray mt-3 rounded-[18px] px-4 py-3">
              <div className="micro text-[10px] text-[var(--ink-soft)]">Nimalar yangi</div>
              <ul className="mt-2 flex flex-col gap-1.5">
                {info.notes.map((n, i) => (
                  <motion.li key={i} className="flex items-start gap-2 text-[13.5px] leading-snug text-[var(--ink)]"
                    initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.15 + i * 0.06 }}>
                    <span className="mt-[6px] h-[5px] w-[5px] shrink-0 rounded-[1px] bg-[var(--ink)]" />{n}
                  </motion.li>
                ))}
              </ul>
            </div>

            {/* physical keys */}
            <div className="tray mt-3 grid grid-cols-[1fr_1.4fr] gap-[7px] rounded-[20px] p-[5px]">
              <Key cap className="h-[48px] rounded-[15px]" onPress={() => phase !== 'downloading' && onClose()}>
                <span className="micro">{phase === 'downloading' ? 'Kuting…' : 'Keyinroq'}</span>
              </Key>
              <AnimatePresence mode="wait" initial={false}>
                {phase === 'done' ? (
                  <motion.div key="install" initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ opacity: 0 }}>
                    <Key cap className="h-[48px] w-full gap-2 rounded-[15px]" onPress={() => installUpdate(uri)}>
                      <Led on /><span className="micro">O'rnatish</span>
                    </Key>
                  </motion.div>
                ) : (
                  <motion.div key="dl" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <Key cap active={phase === 'downloading'} className="h-[48px] w-full gap-2 rounded-[15px]" onPress={() => phase !== 'downloading' && start()}>
                      <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" shapeRendering="crispEdges"><path d="M7 1h2v8h3l-4 4-4-4h3zM2 13h12v2H2z" /></svg>
                      <span className="micro">{phase === 'downloading' ? 'Yuklanmoqda' : phase === 'error' ? 'Qayta urinish' : 'Yuklab olish'}</span>
                    </Key>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
            {phase === 'done' ? <div className="mt-2 text-center text-[11.5px] text-[var(--ink-soft)]">Android o'rnatish oynasida «O'rnatish» tugmasini bosing</div> : null}
          </motion.div>
        </>
      ) : null}
    </AnimatePresence>
  );
}
