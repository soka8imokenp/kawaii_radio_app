import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'uz.kawaii.radio.kr01',
  appName: 'Kawaii Radio',
  webDir: 'dist',
  backgroundColor: '#000000',
  plugins: {
    // the web Splash hides it as soon as it is on screen; the duration is only a safety net
    SplashScreen: { launchShowDuration: 4000, launchAutoHide: true, launchFadeOutDuration: 200, backgroundColor: '#000000', showSpinner: false },
  },
};

export default config;
