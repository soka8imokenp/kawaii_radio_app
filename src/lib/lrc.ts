export interface LyricLine { t: number; text: string }

/** Parses `[mm:ss.xx] text` lines. Lines without a timestamp are ignored. */
export function parseLrc(src: string): LyricLine[] {
  const out: LyricLine[] = [];
  for (const raw of src.split('\n')) {
    const m = raw.match(/^\[(\d+):(\d+(?:\.\d+)?)\]\s*(.*)$/);
    if (m) out.push({ t: Number(m[1]) * 60 + Number(m[2]), text: m[3].trim() });
  }
  return out.sort((a, b) => a.t - b.t);
}

export function lineAt(lines: LyricLine[], time: number) {
  let i = -1;
  for (let k = 0; k < lines.length; k++) if (lines[k].t <= time) i = k;
  return i;
}
