# iOS app (in progress)

Swift + SwiftUI, with WidgetKit widgets, the same features as the Android app (decided with the
user 2026-11-25). Built on a Mac with Xcode; not published yet.

| Folder | What | Status |
|---|---|---|
| `LunarCore/` | Swift package: lunar calendar from Hồ Ngọc Đức's tables, can chi, trực, rating, giờ hoàng đạo, holidays, mệnh, personal events (occurrences, reminders, .ics), ported from `apps/android/core`. Pure Swift + Foundation. | 2026-11-27: `swift test` 29/29 pass on macOS (Swift 6.3.1, Intel); builds for iOS Simulator 26.5 with Xcode 26.6 |
| `project.yml`, `LichAm/`, `LichAmWidget/` | XcodeGen project; the app's frame: theme from `tokens.json` (light/dark, 5 palettes), Be Vietnam Pro and Playfair Display (bundled from `apps/android/app/src/main/res/font`), icon, bottom tab bar, storage in the App Group with Android's keys, deep links `com.coding4pizza.lunar://open?tab=…&d=…`; one small widget | 2026-11-27: runs on the iPhone 17 Pro simulator (iOS 26.5) |
| `LichAm/TodayScreen.swift`, `TearOffSheet.swift`, `Components.swift` | Hôm nay: the tear-off sheet (lacquer, gold foil, rings, moon, can chi; turned with UIPageViewController's page curl, arrows, back to today, theme button), assessment and tuổi, the day's events, việc nên làm, giờ hoàng đạo | 2026-11-27: UI tests pass |
| `LichAm/CalendarScreen.swift` | Lịch: header (month, its lunar months, month/year picker sheet, arrows, "Hôm nay" chip), month grid (solar day, lunar day as shared/design/month-grid.md with the raised "n" of a leap month, dots for good/bad day, event, zodiac clash; swipe sideways to turn the month), the selected day's panel (can chi, rating and Trực, holiday, observances, Mồng 1/rằm, clash) and the day's cards. iPad (≥ 720 pt): grid left, panel right. Not yet: the month folding to two weeks under a sticky header as the page scrolls; the chips Tìm ngày tốt, Đổi ngày, Xuất lịch (shown disabled until Tiện ích) | 2026-11-28: UI tests pass |
| `LichAmUITests/` | XCUITest on a simulator. Hôm nay: arrows, swipe to turn, vertical drag scrolls, cards on screen, theme cycles and is kept after a restart, link opens the Calendar. Lịch: arrows, flick and slow drag turn the month, tap selects a day (and a day of the next month shows that month), the picker (month, 12-year page), a leap month day, "Hôm nay", vertical drag on the grid scrolls | 12/12 pass 2026-11-28 |
| Sự kiện, Tiện ích | placeholders | next steps |
| `scripts/gen-icons.mjs` | draws the app icon and the tab bar's logo mark from `shared/design/icon.svg` (sharp, on any machine with the repo's `node_modules`); the PNGs are committed | |

## Build and run (on the Mac)

```
brew install xcodegen          # once
cd apps/ios
xcodegen generate              # after changing project.yml or adding files
open LichAm.xcodeproj          # or: xcodebuild -project LichAm.xcodeproj -scheme LichAm -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

The Xcode project, Info.plists and entitlements are generated from `project.yml` and not committed.

UI tests (taps and swipes; screenshots kept in the result bundle):

```
xcodebuild test -project LichAm.xcodeproj -scheme LichAm \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -resultBundlePath /tmp/ui.xcresult
xcrun xcresulttool export attachments --path /tmp/ui.xcresult --output-path /tmp/ui-shots
```

Check a screen on screen, not with `exists` alone: XCUITest finds views that are scrolled away.
Tests that need a fixed day launch with `-ui-open com.coding4pizza.lunar://open?tab=calendar&d=YYYY-MM-DD`
(the app opens that link at start; `simctl openurl` asks "Open in Lịch Âm?" first).

## Shared data

`shared/` stays the single source of truth. SwiftPM only bundles files inside the package, so
`npm run gen` (from the repository root) copies `shared/data/*.json` and `shared/design/tokens.json`
into `LunarCore/Sources/LunarCore/Resources/` (`shared/scripts/gen-ios-data.mjs`), and
`npm run check` fails when a copy differs from its source. Edit the files in `shared/`, never the copies.

## Tests (on the Mac)

```
cd apps/ios/LunarCore
swift test
```

The tests read `shared/test-vectors/` straight from the repository and must pass with 0
mismatches, like the web app and Android's core: 10,659 solar dates (and each converted back),
200 lunar years and the birth dates around each Tết for mệnh, the personal event cases of
`events.json`, plus the cases of Android's `ServicesTest`, `BuiltinRemindersTest` and `SmallDateTest`.

## Differences from the Kotlin core

- Dates are Julian day numbers (`SolarDate.jd`), not `java.time`: no time zone, no `Calendar`.
  `LocalDateTime` is a local date and minute for reminders and .ics; `toDate(in:)` turns it into a
  `Date` for notifications.
- The JSON field `repeat` is `repeatKind` in Swift (`repeat` is a keyword); the stored JSON is
  unchanged, and encoding leaves default values out as the other apps do.
