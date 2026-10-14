import { Capacitor, registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Directory, Filesystem } from '@capacitor/filesystem';

/** Releases are published by .github/workflows/build-apk.yml as tag v1.0.<build> with kawaii-radio.apk attached. */
export const REPO = 'soka8imokenp/kawaii_radio_app';
const APK_ASSET = 'kawaii-radio.apk';
const UPDATES_DIR = 'updates';
const SKIP_KEY = 'kr01-skip-build';

export interface UpdateInfo {
  version: string;   // e.g. 1.0.14
  build: number;     // Android versionCode (= workflow run number)
  current: string;   // installed version name
  notes: string[];   // release notes, one change per line
  apkUrl: string;
  size: number;      // bytes
}

/** Native side: android/app/src/main/java/uz/kawaii/radio/kr01/ApkInstallerPlugin.java */
interface ApkInstallerPlugin {
  canInstall(): Promise<{ granted: boolean }>;
  openSettings(): Promise<void>;
  install(o: { path: string }): Promise<void>;
}
const ApkInstaller = registerPlugin<ApkInstallerPlugin>('ApkInstaller');

export const appVersion = (): string => (import.meta.env.VITE_APP_VERSION as string | undefined) ?? 'dev';

/** Web preview: open the app with ?update-demo to see the panel with a simulated download. */
export const isDemo = () => typeof location !== 'undefined' && new URLSearchParams(location.search).has('update-demo');

/**
 * Asks GitHub for the latest release. Resolves null when the installed build is already the newest,
 * throws when the check itself failed (offline, rate limit…).
 */
export async function checkForUpdate(): Promise<UpdateInfo | null> {
  if (isDemo()) {
    return {
      version: '1.0.99', build: 99, current: appVersion(), size: 41_800_000, apkUrl: '',
      notes: ["Yangi qo'shiqlar qo'shildi", 'Qo\'shiq matni tezroq ochiladi', 'Kichik xatolar tuzatildi'],
    };
  }
  if (!Capacitor.isNativePlatform()) return null;
  const info = await App.getInfo();
  const installed = Number(info.build) || 0;
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers: { Accept: 'application/vnd.github+json' }, cache: 'no-store' });
  if (!res.ok) throw new Error(`GitHub HTTP ${res.status}`);
  const rel = await res.json();
  const build = Number(String(rel.tag_name ?? '').split('.').pop());
  const asset = (rel.assets ?? []).find((a: { name: string }) => a.name === APK_ASSET);
  if (!build || !asset || build <= installed) {
    Filesystem.rmdir({ path: UPDATES_DIR, directory: Directory.Cache, recursive: true }).catch(() => {}); // old APKs
    return null;
  }
  const notes = String(rel.body ?? '')
    .split('\n')
    .map((l: string) => l.replace(/^[-*•\s]+/, '').trim())
    .filter((l: string) => l && !/^co-authored-by:/i.test(l))
    .slice(0, 6);
  return {
    version: String(rel.tag_name).replace(/^v/, ''),
    build,
    current: info.version,
    notes: notes.length ? notes : ['Yaxshilanishlar va tuzatishlar'],
    apkUrl: asset.browser_download_url,
    size: asset.size ?? 0,
  };
}

/** "Keyinroq": don't pop this build up on every launch again (it stays available in Profil). */
export function skipBuild(build: number) {
  try { localStorage.setItem(SKIP_KEY, String(build)); } catch { /* private mode */ }
}
export function isSkipped(build: number) {
  try { return Number(localStorage.getItem(SKIP_KEY)) === build; } catch { return false; }
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
  const path = `${UPDATES_DIR}/kawaii-radio-${u.build}.apk`;
  // already downloaded earlier (e.g. the user went to settings for the install permission)
  try {
    const st = await Filesystem.stat({ path, directory: Directory.Cache });
    if (u.size && st.size === u.size) {
      onProgress(u.size, u.size);
      return st.uri;
    }
  } catch { /* not there yet */ }

  const handle = await Filesystem.addListener('progress', (p) => onProgress(p.bytes, p.contentLength || u.size));
  try {
    await Filesystem.downloadFile({ url: u.apkUrl, path, directory: Directory.Cache, progress: true, recursive: true });
  } finally {
    handle.remove();
  }
  const st = await Filesystem.stat({ path, directory: Directory.Cache });
  if (u.size && st.size !== u.size) {
    await Filesystem.deleteFile({ path, directory: Directory.Cache }).catch(() => {});
    throw new Error(`Fayl to'liq yuklanmadi (${st.size} / ${u.size})`);
  }
  return st.uri;
}

export const canInstall = async () => (isDemo() || !Capacitor.isNativePlatform() ? true : (await ApkInstaller.canInstall()).granted);
export const openInstallSettings = () => ApkInstaller.openSettings();

/** Hands the APK to Android's package installer (the user confirms the update there). */
export async function installUpdate(uri: string): Promise<'started' | 'permission'> {
  if (uri.startsWith('demo://')) return 'started';
  try {
    await ApkInstaller.install({ path: uri });
    return 'started';
  } catch (e) {
    if ((e as { code?: string }).code === 'NO_PERMISSION') return 'permission';
    throw e;
  }
}
