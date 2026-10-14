import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useState } from 'react';
import { InfoSheet, PremiumSheet } from './components/Sheets';
import { NavBar, type Page } from './components/NavBar';
import { Home } from './pages/Home';
import { Liked } from './pages/Liked';
import { Profile } from './pages/Profile';
import { UpdateSheet } from './components/UpdateSheet';
import { Splash } from './components/Splash';
import { checkForUpdate, isSkipped, skipBuild, type UpdateInfo } from './lib/updater';

const ORDER: Page[] = ['home', 'liked', 'profile'];

export default function App() {
  const [booted, setBooted] = useState(false);
  const [page, setPage] = useState<Page>('home');
  const [dir, setDir] = useState(1);
  const [info, setInfo] = useState(false);
  const [premiumOpen, setPremiumOpen] = useState(false);
  const [update, setUpdate] = useState<UpdateInfo | null>(null);

  // on every launch: ask GitHub Releases whether a newer build exists ("Keyinroq" hides that build until the next one)
  useEffect(() => {
    if (!booted) return;
    let alive = true;
    checkForUpdate().then((u) => alive && u && !isSkipped(u.build) && setUpdate(u)).catch(() => {});
    return () => { alive = false; };
  }, [booted]);

  const go = (p: Page) => {
    setDir(ORDER.indexOf(p) > ORDER.indexOf(page) ? 1 : -1);
    setPage(p);
  };

  return (
    <div className="flex h-full justify-center bg-[var(--page)]">
      <AnimatePresence>{!booted ? <Splash onDone={() => setBooted(true)} /> : null}</AnimatePresence>
      <motion.main
        className="housing relative flex h-[100dvh] w-full max-w-[480px] flex-col overflow-hidden"
        initial={{ opacity: 0, scale: 0.96 }} animate={booted ? { opacity: 1, scale: 1 } : {}} transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
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
              {page === 'profile' ? <Profile onPremium={() => setPremiumOpen(true)} onUpdate={setUpdate} /> : null}
            </motion.div>
          </AnimatePresence>
        </div>
        <NavBar page={page} onPage={go} />
      </motion.main>

      <InfoSheet open={info} onClose={() => setInfo(false)} />
      <PremiumSheet open={premiumOpen} onClose={() => setPremiumOpen(false)} />
      <UpdateSheet info={update} onClose={() => { if (update) skipBuild(update.build); setUpdate(null); }} />
    </div>
  );
}
