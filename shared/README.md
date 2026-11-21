# shared

Source of truth for the web, iOS and Android apps. Plain JSON and SVG so every language can read it.

| Folder | What | Who reads it |
|---|---|---|
| `data/calendar.json` | Can, chi, chi emoji, weekday labels, giờ hoàng đạo table, Thập Nhị Trực (names, month offsets, good/bad), activity rules | all apps |
| `data/holidays.json` | Lunar and solar holidays, keyed `"day-month"` | all apps |
| `data/menh.json` | Nạp âm of the 60-year cycle, the five elements and tương sinh, earliest birth date | all apps |
| `data/presets.json` | Built-in lunar event groups (Tết, Giỗ Tổ, mồng 1, rằm) | all apps |
| `design/tokens.json` | Colours: base light/dark and one palette per element (light/dark) | web via `theme.generated.css`; iOS/Android via their own generators |
| `design/icon.svg` | Logo master | web (copied to `apps/web/public/icon.svg`), app icons |
| `test-vectors/lunar-days.json` | 10,659 solar dates (every 7th day 1900–2099, every Tết, every leap-month start) → lunar date, can chi, trực, rating, giờ hoàng đạo | unit tests of every app |
| `test-vectors/menh.json` | Lunar year → can chi, nạp âm, element (1900–2099); birth dates on both sides of each Tết → lunar year | unit tests of every app |

## Commands (from the repository root)

- `npm run gen`: rebuild `apps/web/src/theme.generated.css`, `apps/web/public/icon.svg` and `test-vectors/*`.
- `npm run check`: exit 1 when one of them is out of date.

## Reference implementation

The lunar conversion is Hồ Ngọc Đức's algorithm for UTC+7, as implemented by `@dqcai/vn-lunar` (used by the web app and by `scripts/gen-test-vectors.mjs`). The Swift and Kotlin ports are correct when they reproduce `test-vectors/` exactly. When this layout was introduced (2026-11-21), the web app reproduced both files with 0 mismatches.
