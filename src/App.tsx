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

  const go = (p: Page) => setPage(p);

  return (
    <div className="flex h-full justify-center bg-[var(--page)]">
      <AnimatePresence>{!booted ? <Splash onDone={() => setBooted(true)} /> : null}</AnimatePresence>
      <motion.main
        className="housing relative flex h-[100dvh] w-full max-w-[480px] flex-col overflow-hidden"
        initial={{ opacity: 0, scale: 0.96 }} animate={booted ? { opacity: 1, scale: 1 } : {}} transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="relative flex min-h-0 flex-1 flex-col">
          {/* all pages stay mounted: switching only fades/slides, nothing is rebuilt */}
          {ORDER.map((p) => {
            const active = p === page;
            const side = ORDER.indexOf(p) < ORDER.indexOf(page) ? -1 : 1;
            return (
              <motion.div
                key={p}
                className="absolute inset-0 flex flex-col"
                initial={false}
                animate={active
                  ? { opacity: 1, x: 0, visibility: 'visible' }
                  : { opacity: 0, x: side * 24, transitionEnd: { visibility: 'hidden' } }}
                transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
                style={{ pointerEvents: active ? 'auto' : 'none', willChange: 'transform, opacity' }}
                aria-hidden={!active}
              >
                {p === 'home' ? <Home onInfo={() => setInfo(true)} onPremium={() => setPremiumOpen(true)} /> : null}
                {p === 'liked' ? <Liked onOpenRadio={() => go('home')} /> : null}
                {p === 'profile' ? <Profile onPremium={() => setPremiumOpen(true)} onUpdate={setUpdate} /> : null}
              </motion.div>
            );
          })}
        </div>
        <NavBar page={page} onPage={go} />
      </motion.main>

      <InfoSheet open={info} onClose={() => setInfo(false)} />
      <PremiumSheet open={premiumOpen} onClose={() => setPremiumOpen(false)} />
      <UpdateSheet info={update} onClose={() => { if (update) skipBuild(update.build); setUpdate(null); }} />
    </div>
  );
}
