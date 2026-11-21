# shared

Source of truth for the web, iOS and Android apps. Plain JSON and SVG so every language can read it.

| Folder | What | Who reads it |
|---|---|---|
| `data/calendar.json` | Can, chi, chi emoji, weekday labels, giờ hoàng đạo table, Thập Nhị Trực (names, month offsets, good/bad), activity rules | all apps |
| `data/holidays.json` | Lunar and solar holidays, keyed `"day-month"` | all apps |
| `data/menh.json` | Nạp âm of the 60-year cycle, the five elements and tương sinh, earliest birth date | all apps |
| `data/lunar-years.json` | Hồ Ngọc Đức's lunar year table 1900–2099 (Tết offset, month lengths, leap month), the data behind every solar ↔ lunar conversion | Android, iOS (the web reads the same table inside @dqcai/vn-lunar) |
| `data/presets.json` | Built-in lunar event groups (Tết, Giỗ Tổ, mồng 1, rằm) | all apps |
| `design/tokens.json` | Colours: base light/dark and one palette per element (light/dark) | web via `theme.generated.css`; iOS/Android via their own generators |
| `design/icon.svg` | Logo master | web (copied to `apps/web/public/icon.svg`), app icons |
| `test-vectors/lunar-days.json` | 10,659 solar dates (every 7th day 1900–2099, every Tết, every leap-month start) → lunar date, can chi, trực, rating, giờ hoàng đạo | unit tests of every app |
| `test-vectors/menh.json` | Lunar year → can chi, nạp âm, element (1900–2099); birth dates on both sides of each Tết → lunar year | unit tests of every app |

## Commands (from the repository root)

- `npm run gen`: rebuild `apps/web/src/theme.generated.css`, `apps/web/public/icon.svg` and `test-vectors/*`.
- `npm run check`: exit 1 when one of them is out of date.

## Reference implementation

Solar ↔ lunar conversion uses Hồ Ngọc Đức's precomputed year tables (UTC+7): `@dqcai/vn-lunar` for the web and `scripts/gen-test-vectors.mjs`, `data/lunar-years.json` (the same values) for the native apps. Can chi follow `calendar.json` `canChiRules`; the month rule is Hồ Ngọc Đức's, because `@dqcai/vn-lunar`'s own month can chi is one stem off (month 10 of 2026 is Kỷ Hợi, it says Mậu Hợi).

On 2026-11-21 the web app and the Android `core` module both reproduced `test-vectors/` with 0 mismatches.
