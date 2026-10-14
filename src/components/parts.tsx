import { motion } from 'motion/react';
import { useState, type ReactNode, type CSSProperties } from 'react';
import { press } from '../lib/feedback';

/**
 * Physical key: sinks on press, springs back, clicks.
 * `cap` = key cap seated inside a shared .rim frame (no rim of its own).
 */
export function Key({ children, onPress, active, className = '', style, round, cap, ariaLabel }: {
  children: ReactNode; onPress: () => void; active?: boolean; className?: string; style?: CSSProperties; round?: boolean; cap?: boolean; ariaLabel?: string;
}) {
  const [down, setDown] = useState(false);
  const sunk = down || active;
  return (
    <motion.button
      aria-label={ariaLabel}
      onPointerDown={() => { setDown(true); press(); }}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      onClick={onPress}
      animate={{ y: sunk ? 1.5 : 0, scale: down ? 0.975 : 1 }}
      transition={{ type: 'spring', stiffness: 900, damping: 30 }}
      className={`${cap ? 'cap' : 'raised'} ${sunk ? 'is-down' : ''} ${round ? 'rounded-full' : cap ? 'rounded-[12px]' : 'rounded-[18px]'} flex items-center justify-center text-[var(--ink)] outline-none ${className}`}
      style={style}
    >
      {children}
    </motion.button>
  );
}

/**
 * Keyboard keycap: the top surface sits up-left over its skirt (light from top-left).
 * Pressing pushes the top down into the skirt, exactly like a real key.
 */
export function Keycap({ children, onPress, active, className = '', ariaLabel }: {
  children: ReactNode; onPress: () => void; active?: boolean; className?: string; ariaLabel?: string;
}) {
  const [down, setDown] = useState(false);
  const sunk = down || active;
  return (
    <button
      aria-label={ariaLabel}
      onPointerDown={() => { setDown(true); press(); }}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      onClick={onPress}
      className={`keycap relative block h-full w-full outline-none ${className}`}
    >
      <motion.span
        className="keycap-top absolute flex items-center justify-center gap-2.5 rounded-[10px] text-[var(--ctl-ink)]"
        initial={false}
        animate={sunk ? { top: 6, left: 6, right: 5, bottom: 4 } : { top: 3, left: 3, right: 9, bottom: 9 }}
        transition={{ type: 'spring', stiffness: 900, damping: 32 }}
      >
        {children}
      </motion.span>
    </button>
  );
}

/**
 * Tape-deck piano key: a thick slab hinged at the back. Pressing tilts it down,
 * hiding most of its front face; `lit` makes the colour strip glow.
 */
export function DeckKey({ children, sub, stripe, lit, onPress, ariaLabel }: {
  children: ReactNode; sub: string; stripe: string; lit?: boolean; onPress: () => void; ariaLabel?: string;
}) {
  const [down, setDown] = useState(false);
  const sunk = down || lit;
  return (
    <button
      aria-label={ariaLabel}
      onPointerDown={() => { setDown(true); press(); }}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      onClick={onPress}
      className="relative h-[64px] w-full outline-none [perspective:420px]"
    >
      <motion.span
        className="absolute inset-x-0 top-0 block"
        style={{ transformOrigin: '50% 0%', transformStyle: 'preserve-3d' }}
        initial={false}
        animate={{ rotateX: down ? -16 : lit ? -7 : 0, y: down ? 2 : 0 }}
        transition={{ type: 'spring', stiffness: 700, damping: 24 }}
      >
        {/* top face */}
        <span className="deck-top relative flex h-[50px] items-center justify-center gap-2.5 rounded-t-[10px] text-[var(--ctl-ink)]">
          <span className="absolute inset-x-3 top-[5px] h-[3px] rounded-full transition-[box-shadow,opacity] duration-300"
            style={{ background: stripe, opacity: lit ? 1 : 0.55, boxShadow: lit ? `0 0 10px ${stripe}, 0 0 2px ${stripe}` : 'none' }} />
          {children}
          <span className="absolute bottom-[4px] right-3 font-pixel text-[10px] leading-none tracking-[0.1em] opacity-55">{sub}</span>
        </span>
        {/* front face: the thickness of the key */}
        <motion.span className="deck-front block rounded-b-[8px]" initial={false} animate={{ height: sunk ? 4 : 11 }} transition={{ type: 'spring', stiffness: 700, damping: 24 }} />
      </motion.span>
    </button>
  );
}

export function Led({ on, color = 'var(--led-green)', pulse }: { on: boolean; color?: string; pulse?: number }) {
  return (
    <span
      className={`led inline-block shrink-0 ${on ? '' : 'off'}`}
      style={on ? ({ '--c': color, '--on': pulse ?? 1, transition: 'box-shadow 120ms' } as CSSProperties) : undefined}
    />
  );
}

/** Flat silkscreened label plate, as on the reference hardware. */
export function Plate({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`label-plate flex items-center gap-2 rounded-[8px] px-2.5 py-[3px] text-[14px] uppercase text-[var(--ink)] ${className}`}>
      {children}
    </div>
  );
}

export function Screw({ className = '' }: { className?: string }) {
  return <span className={`dots ${className}`} />;
}

/** Renders a pixel-art glyph from rows of '#' — crisp hardware silkscreen icons. */
function Px({ rows, size = 20, color = 'currentColor' }: { rows: string[]; size?: number; color?: string }) {
  const w = Math.max(...rows.map((r) => r.length));
  return (
    <svg width={size} height={(size * rows.length) / w} viewBox={`0 0 ${w} ${rows.length}`} shapeRendering="crispEdges" fill={color}>
      {rows.flatMap((r, y) => [...r].map((c, x) => (c === '#' ? <rect key={`${x}-${y}`} x={x} y={y} width={1} height={1} /> : null)))}
    </svg>
  );
}

const INFO = [
  '.##########.',
  '#..........#',
  '#....##....#',
  '#..........#',
  '#...###....#',
  '#....##....#',
  '#....##....#',
  '#....##....#',
  '#...####...#',
  '#..........#',
  '.##########.',
];
const SAVE = [
  '##########..',
  '#..#####.##.',
  '#..#####..##',
  '#..##.##...#',
  '#..#####...#',
  '#..........#',
  '#.########.#',
  '#.#......#.#',
  '#.########.#',
  '#.#......#.#',
  '############',
];
const HEART_FULL = [
  '.###...###.',
  '###########',
  '###########',
  '###########',
  '.#########.',
  '..#######..',
  '...#####...',
  '....###....',
  '.....#.....',
];
const HEART = [
  '.###...###.',
  '#...#.#...#',
  '#....#....#',
  '#.........#',
  '.#.......#.',
  '..#.....#..',
  '...#...#...',
  '....#.#....',
  '.....#.....',
];
const NOTE = [
  '..########',
  '..########',
  '..##....##',
  '..##....##',
  '..##....##',
  '####..####',
  '####..####',
];

const RADIO = [
  '.........##.',
  '.......##...',
  '.....##.....',
  '############',
  '#..........#',
  '#.####..##.#',
  '#.####.#..##',
  '#.####.#..##',
  '#.####..##.#',
  '#..........#',
  '############',
];
const USER = [
  '....####....',
  '...######...',
  '...######...',
  '...######...',
  '....####....',
  '............',
  '..########..',
  '.##########.',
  '############',
  '############',
];
const SUN = [
  '.....#.....',
  '.#...#...#.',
  '..#.....#..',
  '....###....',
  '...#####...',
  '##.#####.##',
  '...#####...',
  '....###....',
  '..#.....#..',
  '.#...#...#.',
  '.....#.....',
];
const MOON = [
  '....####...',
  '..####.....',
  '.####......',
  '.###.......',
  '####.......',
  '####.......',
  '####.......',
  '.####......',
  '.######..##',
  '..########.',
  '....####...',
];

export const Icon = {
  radio: <Px rows={RADIO} size={20} />,
  user: <Px rows={USER} size={18} />,
  sun: <Px rows={SUN} size={16} />,
  moon: <Px rows={MOON} size={16} />,
  play: <svg width="24" height="24" viewBox="0 0 16 16"><path d="M4.5 2.8v10.4l8.4-5.2z" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" /></svg>,
  pause: <svg width="26" height="26" viewBox="0 0 16 16"><rect x="3.4" y="2.4" width="3.2" height="11.2" rx="0.9" fill="none" stroke="currentColor" strokeWidth="1.25" /><rect x="9.4" y="2.4" width="3.2" height="11.2" rx="0.9" fill="none" stroke="currentColor" strokeWidth="1.25" /></svg>,
  heart: (filled: boolean) => <Px rows={filled ? HEART_FULL : HEART} size={22} color={filled ? '#ff4258' : 'currentColor'} />,
  info: <Px rows={INFO} size={19} />,
  save: <Px rows={SAVE} size={19} />,
  note: <Px rows={NOTE} size={15} />,
};
