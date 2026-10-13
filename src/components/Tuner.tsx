import { animate, motion, useMotionValue, useTransform } from 'motion/react';
import { useRef, useState } from 'react';
import { player } from '../audio/player';
import { press, tick } from '../lib/feedback';

const STEP = 34; // px of drag per station

/** Horizontal thumb-wheel tuner with detents, like a hardware jog wheel. */
export function Tuner({ onStep, preview }: { onStep: (d: number) => void; preview: (d: number) => string }) {
  const x = useMotionValue(0);
  const bgX = useTransform(x, (v) => `${v}px 0, ${v}px 50%, ${v}px 100%, 0 0, 0 0`);
  const start = useRef<{ px: number; x0: number; moved: boolean } | null>(null);
  const lastDetent = useRef(0);
  const [pending, setPending] = useState(0);

  const onDown = (e: React.PointerEvent) => {
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    start.current = { px: e.clientX, x0: x.get(), moved: false };
    lastDetent.current = Math.round(x.get() / STEP);
  };
  const onMove = (e: React.PointerEvent) => {
    if (!start.current) return;
    const dx = e.clientX - start.current.px;
    if (Math.abs(dx) > 4) start.current.moved = true;
    x.set(start.current.x0 + dx);
    const det = Math.round(x.get() / STEP);
    if (det !== lastDetent.current) {
      lastDetent.current = det;
      tick();
      setPending(-det);
      player.setPreview(-det);
    }
  };
  const onUp = (e: React.PointerEvent) => {
    const st = start.current;
    start.current = null;
    if (!st) return;
    if (!st.moved) {
      // tap: left half = previous, right half = next
      const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
      press();
      onStep(e.clientX < r.left + r.width / 2 ? -1 : 1);
      kick(e.clientX < r.left + r.width / 2 ? STEP : -STEP);
      return;
    }
    const det = Math.round(x.get() / STEP);
    animate(x, det * STEP, { type: 'spring', stiffness: 500, damping: 30 }).then(() => x.set(0));
    setPending(0);
    player.setPreview(0);
    if (det !== 0) onStep(-det);
  };
  const kick = (d: number) => {
    animate(x, [0, d, d], { duration: 0.28, ease: 'easeOut' }).then(() => x.set(0));
  };

  return (
    <div className="relative flex-1">
      <div className="tray rounded-[22px] p-[5px]">
        <div
          className="relative h-[46px] touch-none overflow-hidden rounded-[16px]"
          style={{ boxShadow: '0 0 0 1px var(--shade-soft), 0 2px 0 var(--shade-soft), 0 6px 10px -5px var(--shade-deep), inset 0 1px 0 var(--spec)' }}
          onPointerDown={onDown}
          onPointerMove={onMove}
          onPointerUp={onUp}
          onPointerCancel={onUp}
        >
          {/* cylinder body + three rows of detent ticks that scroll with the drag */}
          <motion.div
            className="absolute inset-0"
            style={{
              backgroundImage: [
                // top row: short ticks
                'repeating-linear-gradient(90deg, transparent 0 16px, var(--tick-soft) 16px 18px, transparent 18px 34px)',
                // middle row: tall ticks
                'repeating-linear-gradient(90deg, transparent 0 16px, var(--tick) 16px 18px, transparent 18px 34px)',
                // bottom row: short ticks
                'repeating-linear-gradient(90deg, transparent 0 16px, var(--tick-soft) 16px 18px, transparent 18px 34px)',
                'var(--grain)',
                'linear-gradient(180deg, var(--tuner-a), var(--tuner-b))',
              ].join(','),
              backgroundPosition: bgX,
              backgroundSize: '34px 9px, 34px 20px, 34px 9px, auto, 100% 100%',
              backgroundRepeat: 'repeat-x, repeat-x, repeat-x, repeat, no-repeat',
            }}
          />
          {/* row separators */}
          <div className="pointer-events-none absolute inset-x-0 top-[9px] h-px bg-[var(--tick-soft)]" />
          <div className="pointer-events-none absolute inset-x-0 bottom-[9px] h-px bg-[var(--tick-soft)]" />
          {/* glossy cylinder shading: dark edges, bright centre */}
          <div className="pointer-events-none absolute inset-0" style={{ background: 'linear-gradient(90deg, var(--tuner-shade), transparent 30%, var(--tuner-shine) 50%, transparent 70%, var(--tuner-shade))' }} />
          {/* red needle */}
          <div className="pointer-events-none absolute left-1/2 top-1/2 h-[20px] w-[5px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--tick-red)] shadow-[0_0_6px_rgba(239,68,68,0.55),inset_0_1px_0_rgba(255,255,255,0.55)]" />
        </div>
      </div>
      {pending !== 0 ? (
        <motion.div initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}
          className="pointer-events-none absolute -top-9 left-1/2 z-30 -translate-x-1/2 whitespace-nowrap rounded-lg bg-[#262e1e] px-2.5 py-1 font-pixel text-[14px] text-[#c9d3a4]">
          {pending > 0 ? '►► ' : '◄◄ '}{preview(pending)}
        </motion.div>
      ) : null}
    </div>
  );
}
