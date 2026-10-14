import { motion } from 'motion/react';
import { useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { SplashScreen } from '@capacitor/splash-screen';

const SEGMENTS = 14;
const EASE = [0.76, 0, 0.24, 1] as const;

/**
 * Launch screen. The first frame is identical to the native Android splash (white logo on black),
 * so the native one is hidden right away and the animation continues from it: the logo lifts, the
 * LCD-style "self-test" fills, then the black shutter rolls up into the dark top panel.
 */
export function Splash({ onDone }: { onDone: () => void }) {
  const done = useRef(onDone);
  done.current = onDone;
  useEffect(() => {
    if (Capacitor.isNativePlatform()) SplashScreen.hide().catch(() => {});
    const t = setTimeout(() => done.current(), 2100);
    return () => clearTimeout(t);
  }, []);

  return (
    <motion.div
      className="fixed inset-0 z-[60] flex flex-col items-center justify-center bg-black"
      style={{ clipPath: 'inset(0% 0% 0% 0%)' }}
      exit={{ clipPath: 'inset(0% 0% 100% 0%)' }}
      transition={{ duration: 0.6, ease: EASE }}
    >
      <motion.div
        className="relative flex flex-col items-center"
        exit={{ y: -30, opacity: 0, scale: 0.92 }}
        transition={{ duration: 0.35, ease: 'easeIn' }}
      >
        {/* same size / position as the native splash icon (144dp tall, centred) */}
        <motion.img
          src="/logo.svg" alt="" draggable={false}
          className="h-[144px] w-auto select-none"
          style={{ filter: 'invert(1)' }}
          initial={{ y: 0, scale: 1 }}
          animate={{ y: -18, scale: 0.86 }}
          transition={{ delay: 0.15, type: 'spring', stiffness: 160, damping: 18 }}
        />

        {/* absolutely placed so the logo alone sits dead-centre, exactly like the native splash */}
        <motion.div className="absolute left-1/2 top-full flex w-[260px] -translate-x-1/2 flex-col items-center"
          initial={{ y: 0 }} animate={{ y: -8 }} transition={{ delay: 0.15, type: 'spring', stiffness: 160, damping: 18 }}>
          <motion.div
            className="font-pixel text-[22px] leading-none tracking-[0.18em] text-white"
            initial={{ opacity: 0, y: 8, filter: 'blur(4px)' }}
            animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
            transition={{ delay: 0.35, duration: 0.45, ease: 'easeOut' }}
          >
            KAWAII RADIO
          </motion.div>

          {/* self-test: emerald LED segments, like the LCD on the device */}
          <motion.div className="mt-4 flex gap-[4px]" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.55 }}>
            {new Array(SEGMENTS).fill(0).map((_, i) => (
              <motion.span
                key={i}
                className="h-[6px] w-[10px] rounded-[1.5px]"
                initial={{ backgroundColor: 'rgba(255,255,255,0.12)', boxShadow: '0 0 0 rgba(34,197,94,0)' }}
                animate={{ backgroundColor: '#22c55e', boxShadow: '0 0 8px rgba(34,197,94,0.75)' }}
                transition={{ delay: 0.7 + i * 0.055, duration: 0.12 }}
              />
            ))}
          </motion.div>

          <motion.div
            className="micro mt-3" style={{ fontSize: 9, color: 'rgba(255,255,255,0.4)' }}
            initial={{ opacity: 0 }} animate={{ opacity: [0, 1, 0.4, 1] }}
            transition={{ delay: 1.55, duration: 0.4 }}
          >
            KR-01 · ON AIR
          </motion.div>
        </motion.div>
      </motion.div>
    </motion.div>
  );
}
