import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'uz.kawaii.radio.kr01',
  appName: 'Kawaii Radio',
  webDir: 'dist',
  backgroundColor: '#e8e9ee',
  plugins: {
    SplashScreen: { launchShowDuration: 600, backgroundColor: '#c9cbd3', showSpinner: false },
  },
};

export default config;
