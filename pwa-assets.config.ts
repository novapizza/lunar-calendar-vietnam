import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config';

// Generates PWA icons (favicon, apple-touch-icon, 192/512, maskable) from public/icon.svg.
// Run: npm run generate-pwa-assets
export default defineConfig({
  headLinkOptions: { preset: '2023' },
  preset: {
    ...minimal2023Preset,
    maskable: { ...minimal2023Preset.maskable, resizeOptions: { background: '#124F8B' } },
    apple: { ...minimal2023Preset.apple, resizeOptions: { background: '#124F8B' } },
  },
  images: ['public/icon.svg'],
});
