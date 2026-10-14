const cache = new Map<string, string>();

/**
 * Renders a cover into a 1-bit LCD bitmap (Bayer 4x4 ordered dither),
 * painted with the LCD ink colour on transparent background.
 */
export async function ditherCover(src: string, w = 96, h = 96, ink: [number, number, number] = [38, 46, 30]): Promise<string> {
  const key = `${src}|${w}x${h}`;
  const hit = cache.get(key);
  if (hit) return hit;
  const img = new Image();
  img.crossOrigin = 'anonymous'; // cloud covers are cross-origin; getImageData needs CORS
  img.src = src;
  await img.decode();
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const g = c.getContext('2d', { willReadFrequently: true })!;
  const s = Math.max(w / img.width, h / img.height);
  g.drawImage(img, (w - img.width * s) / 2, (h - img.height * s) / 2, img.width * s, img.height * s);
  const data = g.getImageData(0, 0, w, h);
  const px = data.data;
  const bayer = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      let l = (0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2]) / 255;
      l = Math.min(1, Math.max(0, (l - 0.5) * 1.35 + 0.5)); // contrast boost
      const on = l < (bayer[(y % 4) * 4 + (x % 4)] + 0.5) / 16;
      px[i] = ink[0];
      px[i + 1] = ink[1];
      px[i + 2] = ink[2];
      px[i + 3] = on ? 235 : 0;
    }
  }
  g.putImageData(data, 0, 0);
  const url = c.toDataURL();
  cache.set(key, url);
  return url;
}
