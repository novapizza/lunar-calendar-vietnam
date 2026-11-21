# Lịch Âm Việt Nam

A Vietnamese lunar calendar single-page application built with React 19, TypeScript, and Tailwind CSS 4. View lunar/solar dates side-by-side, find auspicious dates for important events, manage personal notes, and export recurring lunar anniversaries to Google Calendar.

---

## Features

### Calendar
- **Monthly calendar grid** with solar and lunar dates displayed together
- **Week numbers** (ISO standard)
- Navigate by month or jump directly via month/year selectors
- **"Today" shortcut** button

### Day Detail (Sidebar)
- Large solar and lunar date display
- **Can Chi** for year, month, and day (Heavenly Stems & Earthly Branches)
- **Trực** (Thập Nhị Trực / 12 Directors) with good/neutral/bad rating
- **Giờ Hoàng Đạo** — six auspicious hours for the day
- Recommended activities: Cưới hỏi, Chuyển nhà, Khai trương
- Vietnamese lunar and solar **holiday badges**
- **Live clock** with current traditional hour (Giờ)

### Zodiac Clash (Xung Tuổi)
- Select your zodiac (tuổi) — persisted to localStorage across sessions
- Sidebar warning when the selected date clashes with your zodiac (Lục Xung rule)
- **Frown icon on calendar cells** marking every clashing day at a glance

### Auspicious Date Finder (Chọn Ngày Tốt)
- Search upcoming good dates for **Cưới Hỏi**, **Khởi Công**, or **Khai Trương**
- Results ranked by activity-specific Trực match (5-star = perfect fit)
- Click a result to jump directly to that date on the calendar

### Quick Date Picker
- Navigate by clicking any solar or lunar field
- Mini calendar with good/bad day indicators
- Lunar ↔ solar two-way conversion

### Personal Notes
- Add free-text notes to any calendar day
- Notes persist in **localStorage** — no account needed
- Sticky-note icon appears on calendar cells that have notes

### Lunar Event Export (Google Calendar Sync)
- Save recurring **lunar anniversaries** (giỗ, sinh nhật âm lịch, etc.) with title, lunar day/month, and optional leap-month flag
- Select **1–30 years** to generate using a slider — the app resolves the correct solar date individually for each year (since lunar dates shift annually)
- Downloads a standard **RFC 5545 `.ics` file** importable into Google Calendar, Apple Calendar, Outlook, and any calendar app
- Skipped years (e.g. leap month absent in a given year) are listed with a clear explanation
- Occurrences are resolved per **lunar year** and kept when their real solar date is inside the chosen range, so events in lunar months 11–12 (which fall in Jan/Feb of the next solar year) are never missed or duplicated
- Every conversion is verified by converting back to lunar; day 30 in a 29-day month (tháng thiếu) moves to day 29 and the event description says so

### Design
- Modern, calm UI built on semantic design tokens (`shared/design/tokens.json`, generated into `apps/web/src/theme.generated.css`) — warm neutral surfaces, brand blue, traditional red/gold for lunar markers
- **Be Vietnam Pro** typeface (designed for Vietnamese diacritics)
- **Automatic dark mode** following the device setting
- Popups are bottom sheets on phones and centred dialogs on desktop (`apps/web/src/components/Sheet.tsx`)
- The Botpress chat widget loads on tablet/desktop only — never on phones

### Mobile & Installable App (PWA)
- Built for phones first: bottom tab bar, safe-area support (notch / home indicator), no horizontal scrolling down to 320px wide
- Swipe left/right to change month (Lịch) or day (Hôm nay)
- **Installable**: one-tap install on Android/Chrome/Edge; on iPhone a banner shows the Share → Add to Home Screen steps
- **Works offline** — all lunar calculations run on the device; the service worker caches the app and its font
- When a new version is deployed, a small "Đã có phiên bản mới" toast lets the user update (no surprise reloads while typing)

---

## Tech Stack

| Layer | Library |
|---|---|
| Framework | React 19 |
| Language | TypeScript 5.8 |
| Build tool | Vite 6 |
| Styling | Tailwind CSS 4 |
| Lunar library | `@dqcai/vn-lunar` |
| Date utilities | `date-fns` |
| Icons | `lucide-react` |
| Class merging | `clsx` + `tailwind-merge` |
| PWA | `vite-plugin-pwa` (Workbox) |

---

## Repository layout

Three apps and the data they share, in one repository (npm workspaces for the JavaScript parts).

```
apps/
  web/        React + Vite PWA (this README's features). Deployed by Cloudflare Workers Builds.
  ios/        Swift / SwiftUI app + widgets (planned, see apps/ios/README.md)
  android/    Kotlin / Jetpack Compose app + widgets (planned, see apps/android/README.md)
shared/       Language-neutral source of truth for all three apps
  data/         calendar.json (can, chi, giờ hoàng đạo, trực, activities), holidays.json,
                menh.json (nạp âm, elements), presets.json
  design/       tokens.json (colours: light/dark + one palette per mệnh), icon.svg (logo master)
  test-vectors/ lunar-days.json, menh.json: expected results every app must reproduce
  scripts/      gen-tokens.mjs, gen-test-vectors.mjs
wrangler.jsonc  Cloudflare deploy config (serves apps/web/dist)
```

**Rule:** data and colours are edited in `shared/` only. Generated files (`apps/web/src/theme.generated.css`, `apps/web/public/icon.svg`, `shared/test-vectors/*`) are rebuilt with `npm run gen`; `npm run check` fails when one is out of date.

Web source (`apps/web/src/`): `services/lunarService.ts` (lunar info, verified lunar → solar, auspicious dates), `lib/menh.ts` (mệnh from birth date), `services/icsService.ts` (.ics export), `components/views/*` (Hôm nay, Lịch, Sự kiện, Tiện ích), `components/modals/*`, `hooks/*`.

---

## Getting Started

Requires Node.js 24 (`.node-version`; Cloudflare reads it too). All commands run from the repository root.

```bash
npm install            # installs every workspace
npm run dev            # web app at http://localhost:3000
npm run lint           # type check the web app
npm run build          # production build -> apps/web/dist (sw.js, manifest.webmanifest)
npm run preview        # serve the production build locally
npm run gen            # regenerate theme CSS, logo copy and test vectors from shared/
npm run check          # fail if a generated file is out of date
```

`apps/web/public/_headers` keeps `sw.js` uncached on Cloudflare so updates reach users. App icons for the web are generated from `apps/web/public/icon.svg` (a copy of `shared/design/icon.svg`): `npm run generate-pwa-assets -w @lunar/web`.

---

## Exporting to Google Calendar

1. Click **"Xuất sự kiện âm lịch"** in the sidebar.
2. Add one or more lunar events (title + lunar day/month).
3. Use the slider to choose how many years to generate (default: 5).
4. Click **"Tải file .ics"** — a file named `lich-am-su-kien-YYYY-YYYY.ics` downloads automatically.
5. In Google Calendar: **Settings → Import & export → Import** → select the file.

Each year gets its own calendar entry on the correct solar date, since a fixed lunar date falls on a different solar date every year.

---

## License

MIT
