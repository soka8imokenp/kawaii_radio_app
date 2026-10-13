import { Capacitor } from '@capacitor/core';
import { StatusBar, Style } from '@capacitor/status-bar';

/** Single light theme. The top panel is dark, so the status bar uses light content over it. */
const BAR = '#2c2e33';

document.documentElement.dataset.theme = 'light';
try { localStorage.removeItem('kr01-theme'); } catch { /* private mode */ }
document.querySelector('meta[name="theme-color"]')?.setAttribute('content', BAR);
if (Capacitor.isNativePlatform()) {
  StatusBar.setStyle({ style: Style.Dark }).catch(() => {});
  StatusBar.setBackgroundColor({ color: BAR }).catch(() => {});
}
