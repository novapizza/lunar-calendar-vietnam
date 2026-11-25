// Copies the shared JSON the iOS package reads at run time into its resources:
// shared/data/*.json and shared/design/tokens.json → apps/ios/LunarCore/Sources/LunarCore/Resources/.
// SwiftPM only bundles files inside the package, so the copies are committed; `--check` exits 1
// when one differs from its source (shared/ stays the single source of truth).
import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const out = join(root, 'apps', 'ios', 'LunarCore', 'Sources', 'LunarCore', 'Resources');
const check = process.argv.includes('--check');

const sources = [
  ...readdirSync(join(root, 'shared', 'data')).filter(f => f.endsWith('.json')).map(f => join(root, 'shared', 'data', f)),
  join(root, 'shared', 'design', 'tokens.json'),
];

let stale = 0;
if (!check) mkdirSync(out, { recursive: true });
for (const src of sources) {
  const name = src.split(/[\\/]/).pop();
  const dst = join(out, name);
  const text = readFileSync(src);
  const same = existsSync(dst) && readFileSync(dst).equals(text);
  if (check) {
    if (!same) { console.error(`out of date: apps/ios/LunarCore/Sources/LunarCore/Resources/${name}`); stale++; }
  } else if (!same) {
    writeFileSync(dst, text);
    console.log(`wrote apps/ios/LunarCore/Sources/LunarCore/Resources/${name}`);
  }
}
if (check && stale) process.exit(1);
