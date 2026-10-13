import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle } from '@capacitor/haptics';

const native = Capacitor.isNativePlatform();

/** Light detent used by the tuner wheel. */
export function tick() {
  if (native) Haptics.selectionChanged().catch(() => {});
  else navigator.vibrate?.(4);
  click(2400, 0.012, 0.05);
}

/** Heavier "clack" for physical buttons. */
export function press() {
  if (native) Haptics.impact({ style: ImpactStyle.Light }).catch(() => {});
  else navigator.vibrate?.(10);
  click(900, 0.03, 0.09);
}

let ctx: AudioContext | null = null;
function ac() {
  if (!ctx) ctx = new AudioContext();
  if (ctx.state === 'suspended') ctx.resume();
  return ctx;
}

/** Short synthesized mechanical click: filtered noise burst + body thump. */
function click(freq: number, len: number, gain: number) {
  try {
    const c = ac();
    const t = c.currentTime;
    const buf = c.createBuffer(1, Math.ceil(c.sampleRate * len), c.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / d.length, 3);
    const src = c.createBufferSource();
    src.buffer = buf;
    const bp = c.createBiquadFilter();
    bp.type = 'bandpass';
    bp.frequency.value = freq;
    bp.Q.value = 1.2;
    const g = c.createGain();
    g.gain.value = gain;
    src.connect(bp).connect(g).connect(c.destination);
    src.start(t);
  } catch {
    /* audio unavailable */
  }
}
