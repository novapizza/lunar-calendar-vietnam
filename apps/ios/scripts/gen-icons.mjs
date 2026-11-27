// Draws the iOS app icon and the tab bar's logo mark from shared/design/icon.svg.
//   node apps/ios/scripts/gen-icons.mjs
// Uses sharp from the repository's node_modules (installed for the web app's PWA icons).
//
// Outputs (committed, so the Mac needs no Node):
//   LichAm/Assets.xcassets/AppIcon.appiconset/icon-1024.png   the icon, 1024 × 1024, opaque
//   LichAm/Assets.xcassets/LogoMark.imageset/logo-mark.png    the crescent and stars without the
//                                                             background, drawn on the palette's
//                                                             logo colour by the app (like Android)

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const ios = join(dirname(fileURLToPath(import.meta.url)), '..');
const svg = readFileSync(join(ios, '../../shared/design/icon.svg'), 'utf8');
const assets = join(ios, 'LichAm/Assets.xcassets');

const write = async (rel, input, size, opaque = false) => {
  const file = join(assets, rel);
  mkdirSync(dirname(file), { recursive: true });
  let img = sharp(Buffer.from(input), { density: 72 * size / 512 }).resize(size, size);
  // App icons must have no alpha channel (App Store rule).
  if (opaque) img = img.removeAlpha();
  writeFileSync(file, await img.png().toBuffer());
  console.log(`wrote ${rel}`);
};

await write('AppIcon.appiconset/icon-1024.png', svg, 1024, true);
// The mark alone: the background rectangle is the first <rect> after </defs>.
const mark = svg.replace(/(<\/defs>\s*)<rect[^>]*\/>/, '$1');
if (mark === svg) throw new Error('icon.svg changed: background <rect> not found');
await write('LogoMark.imageset/logo-mark.png', mark, 384);
