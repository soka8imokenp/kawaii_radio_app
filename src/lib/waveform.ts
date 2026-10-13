const cache = new Map<string, Promise<number[]>>();

/** Pseudo waveform used until the real one is decoded (stable per track). */
export function placeholderPeaks(seed: string, n: number): number[] {
  let h = 0;
  for (const c of seed) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const out: number[] = [];
  for (let i = 0; i < n; i++) {
    h = (h * 1103515245 + 12345) >>> 0;
    const r = (h % 1000) / 1000;
    const env = 0.35 + 0.65 * Math.abs(Math.sin(i / 9 + seed.length));
    out.push(Math.max(0.08, r * env));
  }
  return out;
}

/** Real peaks of the audio file (RMS per bucket, normalised 0..1). Decoded once and cached. */
export function trackPeaks(src: string, n: number): Promise<number[]> {
  const key = `${src}|${n}`;
  let p = cache.get(key);
  if (!p) {
    p = (async () => {
      const buf = await (await fetch(src)).arrayBuffer();
      const ctx = new OfflineAudioContext(1, 1, 22050);
      const audio = await ctx.decodeAudioData(buf);
      const data = audio.getChannelData(0);
      const size = Math.floor(data.length / n);
      const out: number[] = [];
      let max = 0;
      for (let i = 0; i < n; i++) {
        let sum = 0;
        for (let k = i * size; k < (i + 1) * size; k += 8) sum += data[k] * data[k];
        const v = Math.sqrt(sum / (size / 8));
        out.push(v);
        max = Math.max(max, v);
      }
      return out.map((v) => Math.max(0.06, Math.pow(v / (max || 1), 1.3)));
    })();
    cache.set(key, p);
  }
  return p;
}
