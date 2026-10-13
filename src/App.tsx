import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useState } from 'react';
import { InfoSheet, PremiumSheet } from './components/Sheets';
import { NavBar, type Page } from './components/NavBar';
import { Home } from './pages/Home';
import { Liked } from './pages/Liked';
import { Profile } from './pages/Profile';
import { UpdateSheet } from './components/UpdateSheet';
import { checkForUpdate, type UpdateInfo } from './lib/updater';

/** Boot sequence shown once on launch: LCD self-test, then the brand. */
function Boot({ onDone }: { onDone: () => void }) {
  useEffect(() => { const t = setTimeout(onDone, 1700); return () => clearTimeout(t); }, [onDone]);
  return (
    <motion.div className="fixed inset-0 z-[60] flex items-center justify-center bg-[var(--page)]" exit={{ opacity: 0 }} transition={{ duration: 0.4 }}>
      <motion.div initial={{ scale: 0.92, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: 'spring', stiffness: 200, damping: 20 }}
        className="lcd flex h-32 w-72 flex-col items-center justify-center rounded-[22px] border-[3px] border-[#3a4131]">
        <motion.div className="font-pixel text-[26px] leading-none lcd-text" animate={{ opacity: [0, 1, 0.2, 1] }} transition={{ duration: 0.8, times: [0, 0.3, 0.5, 1] }}>KAWAII RADIO</motion.div>
        <div className="mt-3 flex gap-1">
          {new Array(12).fill(0).map((_, i) => (
            <motion.div key={i} className="h-3 w-3 bg-[var(--lcd-ink)]" initial={{ opacity: 0.1 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 + i * 0.07 }} />
          ))}
        </div>
      </motion.div>
    </motion.div>
  );
}

const ORDER: Page[] = ['home', 'liked', 'profile'];

export default function App() {
  const [booted, setBooted] = useState(false);
  const [page, setPage] = useState<Page>('home');
  const [dir, setDir] = useState(1);
  const [info, setInfo] = useState(false);
  const [premiumOpen, setPremiumOpen] = useState(false);
  const [update, setUpdate] = useState<UpdateInfo | null>(null);

  // on every launch: ask GitHub Releases whether a newer build exists
  useEffect(() => {
    if (!booted) return;
    let alive = true;
    checkForUpdate().then((u) => alive && u && setUpdate(u));
    return () => { alive = false; };
  }, [booted]);

  const go = (p: Page) => {
    setDir(ORDER.indexOf(p) > ORDER.indexOf(page) ? 1 : -1);
    setPage(p);
  };

  return (
    <div className="flex h-full justify-center bg-[var(--page)]">
      <AnimatePresence>{!booted ? <Boot onDone={() => setBooted(true)} /> : null}</AnimatePresence>
      <motion.main
        className="housing relative flex h-[100dvh] w-full max-w-[480px] flex-col overflow-hidden"
        initial={{ opacity: 0 }} animate={booted ? { opacity: 1 } : {}} transition={{ duration: 0.4 }}
      >
        <div className="relative flex min-h-0 flex-1 flex-col">
          <AnimatePresence mode="popLayout" initial={false} custom={dir}>
            <motion.div
              key={page}
              custom={dir}
              className="absolute inset-0 flex flex-col"
              variants={{
                enter: (d: number) => ({ x: d * 60, opacity: 0 }),
                center: { x: 0, opacity: 1 },
                exit: (d: number) => ({ x: d * -60, opacity: 0 }),
              }}
              initial="enter" animate="center" exit="exit"
              transition={{ type: 'spring', stiffness: 420, damping: 38 }}
            >
              {page === 'home' ? <Home onInfo={() => setInfo(true)} onPremium={() => setPremiumOpen(true)} /> : null}
              {page === 'liked' ? <Liked onOpenRadio={() => go('home')} /> : null}
              {page === 'profile' ? <Profile onPremium={() => setPremiumOpen(true)} /> : null}
            </motion.div>
          </AnimatePresence>
        </div>
        <NavBar page={page} onPage={go} />
      </motion.main>

      <InfoSheet open={info} onClose={() => setInfo(false)} />
      <PremiumSheet open={premiumOpen} onClose={() => setPremiumOpen(false)} />
      <UpdateSheet info={update} onClose={() => setUpdate(null)} />
    </div>
  );
}
