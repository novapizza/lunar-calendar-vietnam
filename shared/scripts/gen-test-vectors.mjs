// Generates the test vectors every app must reproduce exactly.
//   node shared/scripts/gen-test-vectors.mjs           write shared/test-vectors/*.json
//   node shared/scripts/gen-test-vectors.mjs --check   exit 1 if a file is out of date
//
// Reference: the lunar library the web app uses (@dqcai/vn-lunar: Hồ Ngọc Đức's
// precomputed year tables, UTC+7, the same values as shared/data/lunar-years.json)
// plus the rules in shared/data. The web app computes the same
// values (checked in the browser when this was written); the iOS and Android
// ports are correct when their unit tests reproduce these files.

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LunarCalendar, getSolarDate } from '@dqcai/vn-lunar';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const read = rel => JSON.parse(readFileSync(join(root, rel), 'utf8'));
const calendar = read('shared/data/calendar.json');
const menhData = read('shared/data/menh.json');
const check = process.argv.includes('--check');

// @dqcai/vn-lunar spells 丙 "Bình"; the apps use "Bính".
const fixCan = s => s.replace(/^Bình /, 'Bính ');
const iso = (y, m, d) => `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

// Month can chi by Ho Ngoc Duc's rule; @dqcai/vn-lunar's own monthCanChi is one stem off.
const monthCanChi = (m, y) => `${calendar.can[(y * 12 + m + 3) % 10]} ${calendar.chi[(m + 1) % 12]}`;

function dayRow(y, m, d) {
  const l = LunarCalendar.fromSolar(d, m, y);
  const ld = l.lunarDate;
  const dayCanChi = fixCan(l.dayCanChi);
  const dayChi = calendar.chi.indexOf(dayCanChi.split(' ')[1]);
  const monthChiStart = calendar.truc.monthChiStart[Math.abs(ld.month) - 1];
  const truc = calendar.truc.names[(dayChi - monthChiStart + 12) % 12];
  const rating = calendar.truc.good.includes(truc) ? 'good' : calendar.truc.bad.includes(truc) ? 'bad' : 'neutral';
  return [
    iso(y, m, d), ld.day, Math.abs(ld.month), ld.year, ld.leap ? 1 : 0,
    dayCanChi, monthCanChi(Math.abs(ld.month), ld.year), fixCan(l.yearCanChi),
    truc, rating, calendar.gioHoangDao.byDayChi[dayChi].join(' '),
  ];
}

// Every 7th day 1900-02-01 .. 2099-12-31, plus every Tết and the first day of every leap month.
const dates = new Map();
for (const t = new Date(Date.UTC(1900, 1, 1)); t.getUTCFullYear() < 2100; t.setUTCDate(t.getUTCDate() + 7)) {
  dates.set(iso(t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate()), [t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate()]);
}
const tet = [];
for (let y = 1900; y <= 2099; y++) {
  const s = getSolarDate(1, 1, y, false);
  if (y > 1900 || iso(s.year, s.month, s.day) >= menhData.birthDateMin) {
    dates.set(iso(s.year, s.month, s.day), [s.year, s.month, s.day]);
    tet.push(iso(s.year, s.month, s.day));
  }
  for (let m = 1; m <= 12; m++) {
    const leap = getSolarDate(1, m, y, true);
    if (!leap?.year) continue;
    const back = LunarCalendar.fromSolar(leap.day, leap.month, leap.year).lunarDate;
    if (back.leap && Math.abs(back.month) === m && back.year === y) dates.set(iso(leap.year, leap.month, leap.day), [leap.year, leap.month, leap.day]);
  }
}
const days = [...dates.values()].sort((a, b) => (a[0] - b[0]) || (a[1] - b[1]) || (a[2] - b[2])).map(([y, m, d]) => dayRow(y, m, d));

function menhOfLunarYear(y) {
  const i = (((y - menhData.cycleStartYear) % 60) + 60) % 60;
  const n = menhData.napAm[i >> 1];
  return [y, `${calendar.can[i % 10]} ${calendar.chi[i % 12]}`, n.name, n.element];
}
const menhYears = [];
for (let y = 1900; y <= 2099; y++) menhYears.push(menhOfLunarYear(y));

// Birth dates on both sides of each Tết: the day before belongs to the previous lunar year.
const birthDates = tet.flatMap(t => {
  const d = new Date(`${t}T00:00:00Z`);
  const before = new Date(d.getTime() - 86400000);
  const b = iso(before.getUTCFullYear(), before.getUTCMonth() + 1, before.getUTCDate());
  return [b, t].filter(x => x >= menhData.birthDateMin).map(x => {
    const [y, m, dd] = x.split('-').map(Number);
    return [x, LunarCalendar.fromSolar(dd, m, y).lunarDate.year];
  });
});

const files = {
  'shared/test-vectors/lunar-days.json': {
    $comment: 'Solar date -> lunar date and day facts. Every 7th day 1900-02-01..2099-12-31 plus every Tết and leap-month start. Hours = chi indexes of the six giờ hoàng đạo.',
    columns: ['solar', 'lunarDay', 'lunarMonth', 'lunarYear', 'leap', 'canChiDay', 'canChiMonth', 'canChiYear', 'truc', 'rating', 'gioHoangDao'],
    rows: days,
  },
  'shared/test-vectors/menh.json': {
    $comment: 'Lunar year -> can chi, nạp âm, element; solar birth date -> lunar year (dates on both sides of each Tết).',
    years: { columns: ['lunarYear', 'canChi', 'napAm', 'element'], rows: menhYears },
    birthDates: { columns: ['solar', 'lunarYear'], rows: birthDates },
  },
};

mkdirSync(join(root, 'shared/test-vectors'), { recursive: true });
let stale = 0;
for (const [rel, obj] of Object.entries(files)) {
  // One row per line keeps the files diffable.
  const text = JSON.stringify(obj, null, 2).replace(/\[\n\s+([^\[\]{}]*?)\n\s+\]/g, (_, inner) => `[${inner.replace(/,\n\s+/g, ', ')}]`) + '\n';
  const file = join(root, rel);
  const current = existsSync(file) ? readFileSync(file, 'utf8') : null;
  if (current === text) continue;
  if (check) { console.error(`out of date: ${rel} (run npm run gen)`); stale++; }
  else { writeFileSync(file, text); console.log(`wrote ${rel}: ${(text.length / 1024).toFixed(0)} KiB`); }
}
if (stale) process.exit(1);
if (check) console.log('test vectors: up to date');
console.log(`days ${days.length}, tết ${tet.length}, menh years ${menhYears.length}, birth dates ${birthDates.length}`);
