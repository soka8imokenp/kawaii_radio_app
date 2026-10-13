import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { FileOpener } from '@capacitor-community/file-opener';

/** Releases are published by .github/workflows/build-apk.yml as tag v1.0.<build> with kawaii-radio.apk attached. */
export const REPO = 'soka8imokenp/kawaii_radio_app';
const APK_ASSET = 'kawaii-radio.apk';

export interface UpdateInfo {
  version: string;   // e.g. 1.0.14
  build: number;     // Android versionCode (= workflow run number)
  current: string;   // installed version name
  notes: string[];   // release notes, one change per line
  apkUrl: string;
  size: number;      // bytes
}

export const appVersion = (): string => (import.meta.env.VITE_APP_VERSION as string | undefined) ?? 'dev';

/** Web preview: open the app with ?update-demo to see the panel with a simulated download. */
export const isDemo = () => typeof location !== 'undefined' && new URLSearchParams(location.search).has('update-demo');

export async function checkForUpdate(): Promise<UpdateInfo | null> {
  if (isDemo()) {
    return {
      version: '1.0.99', build: 99, current: appVersion(), size: 41_800_000, apkUrl: '',
      notes: ["Yangi qo'shiqlar qo'shildi", 'Qo\'shiq matni tezroq ochiladi', 'Kichik xatolar tuzatildi'],
    };
  }
  if (!Capacitor.isNativePlatform()) return null;
  try {
    const info = await App.getInfo();
    const installed = Number(info.build) || 0;
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers: { Accept: 'application/vnd.github+json' } });
    if (!res.ok) return null;
    const rel = await res.json();
    const build = Number(String(rel.tag_name ?? '').split('.').pop());
    const asset = (rel.assets ?? []).find((a: { name: string }) => a.name === APK_ASSET);
    if (!build || !asset || build <= installed) return null;
    const notes = String(rel.body ?? '')
      .split('\n')
      .map((l: string) => l.replace(/^[-*•\s]+/, '').trim())
      .filter(Boolean)
      .slice(0, 6);
    return {
      version: String(rel.tag_name).replace(/^v/, ''),
      build,
      current: info.version,
      notes: notes.length ? notes : ['Yaxshilanishlar va tuzatishlar'],
      apkUrl: asset.browser_download_url,
      size: asset.size ?? 0,
    };
  } catch {
    return null;
  }
}

/** Downloads the APK into the app cache, reporting progress; resolves with a file URI for the installer. */
export async function downloadUpdate(u: UpdateInfo, onProgress: (done: number, total: number) => void): Promise<string> {
  if (isDemo()) {
    // simulated download for the web preview
    const total = u.size;
    for (let done = 0; done < total; done += total / 60) {
      onProgress(done, total);
      await new Promise((r) => setTimeout(r, 70));
    }
    onProgress(total, total);
    return 'demo://kawaii-radio.apk';
  }
  const path = `updates/kawaii-radio-${u.build}.apk`;
  const handle = await Filesystem.addListener('progress', (p) => onProgress(p.bytes, p.contentLength || u.size));
  try {
    await Filesystem.downloadFile({ url: u.apkUrl, path, directory: Directory.Cache, progress: true, recursive: true });
    const { uri } = await Filesystem.getUri({ path, directory: Directory.Cache });
    return uri;
  } finally {
    handle.remove();
  }
}

/** Hands the APK to Android's package installer (the user confirms the update there). */
export async function installUpdate(uri: string) {
  if (uri.startsWith('demo://')) return;
  await FileOpener.open({ filePath: uri, contentType: 'application/vnd.android.package-archive', openWithDefault: true });
}
