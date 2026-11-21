import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config';

// Generates PWA icons (favicon, apple-touch-icon, 192/512, maskable) from public/icon.svg,
// a copy of shared/design/icon.svg made by `npm run gen` (edit the shared one).
// Run: npm run generate-pwa-assets
export default defineConfig({
  headLinkOptions: { preset: '2023' },
  preset: {
    ...minimal2023Preset,
    maskable: { ...minimal2023Preset.maskable, resizeOptions: { background: '#8A2F1C' } },
    apple: { ...minimal2023Preset.apple, resizeOptions: { background: '#8A2F1C' } },
  },
  images: ['public/icon.svg'],
});
