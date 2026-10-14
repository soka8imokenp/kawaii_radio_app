import { motion } from 'motion/react';
import { press } from '../lib/feedback';
import { Icon } from './parts';

export type Page = 'home' | 'liked' | 'profile';

const ITEMS: [Page, string][] = [['home', 'Asosiy'], ['liked', 'Sevimlilar'], ['profile', 'Profil']];

/** Bottom navigation as a row of key caps in one moulded rim. */
export function NavBar({ page, onPage }: { page: Page; onPage: (p: Page) => void }) {
  return (
    <nav className="relative z-30 px-4 pb-[max(8px,env(safe-area-inset-bottom))] pt-1">
      <div className="tray grid grid-cols-3 gap-[7px] rounded-[20px] p-[4px]">
        {ITEMS.map(([key, label]) => {
          const active = page === key;
          const icon = key === 'home' ? Icon.radio : key === 'liked' ? Icon.heart(false) : Icon.user;
          return (
            <motion.button
              key={key}
              onClick={() => { if (!active) { press(); onPage(key); } }}
              animate={{ y: active ? 1 : 0 }}
              transition={{ type: 'spring', stiffness: 800, damping: 30 }}
              className={`cap ${active ? 'is-down' : ''} relative flex h-[40px] flex-col items-center justify-center gap-[2px] rounded-[15px]`}
              aria-label={label}
            >
              <span className={active ? 'opacity-100' : 'opacity-75'}>{icon}</span>
              <span className="micro" style={{ fontSize: 10, letterSpacing: "0.06em" }}>{label}</span>
            </motion.button>
          );
        })}
      </div>
    </nav>
  );
}
