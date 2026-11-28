# iOS app (in progress)

Swift + SwiftUI, with WidgetKit widgets, the same features as the Android app (decided with the
user 2026-11-25). Built on a Mac with Xcode; not published yet.

| Folder | What | Status |
|---|---|---|
| `LunarCore/` | Swift package: lunar calendar from Hồ Ngọc Đức's tables, can chi, trực, rating, giờ hoàng đạo, holidays, mệnh, personal events (occurrences, reminders, .ics), ported from `apps/android/core`. Pure Swift + Foundation. | 2026-11-27: `swift test` 29/29 pass on macOS (Swift 6.3.1, Intel); builds for iOS Simulator 26.5 with Xcode 26.6 |
| `project.yml`, `LichAm/`, `LichAmWidget/` | XcodeGen project; the app's frame: theme from `tokens.json` (light/dark, 5 palettes), Be Vietnam Pro and Playfair Display (bundled from `apps/android/app/src/main/res/font`), icon, bottom tab bar, storage in the App Group with Android's keys, deep links `com.coding4pizza.lunar://open?tab=…&d=…` | 2026-11-27: runs on the iPhone 17 Pro simulator (iOS 26.5) |
| `LichAm/TodayScreen.swift`, `TearOffSheet.swift`, `Components.swift` | Hôm nay: the tear-off sheet (lacquer, gold foil, rings, moon, can chi; turned with UIPageViewController's page curl, arrows, back to today, theme button), assessment and tuổi, the day's events, việc nên làm, giờ hoàng đạo | 2026-11-27: UI tests pass |
| `LichAm/CalendarScreen.swift` | Lịch: header (month, its lunar months, month/year picker sheet, arrows, "Hôm nay" chip), month grid (solar day, lunar day as shared/design/month-grid.md with the raised "n" of a leap month, dots for good/bad day, event, zodiac clash; swipe sideways to turn the month), the selected day's panel (can chi, rating and Trực, holiday, observances, Mồng 1/rằm, clash) and the day's cards. iPad (≥ 720 pt): grid left, panel right. Chips Tìm ngày tốt, Đổi ngày, Xuất lịch open Tiện ích's sheets. Not yet: the month folding to two weeks under a sticky header as the page scrolls | 2026-11-28: UI tests pass |
| `LichAm/EventsScreen.swift`, `EventSheets.swift`, `Forms.swift` | Sự kiện: the next event highlighted, then the following ones by solar month, loading one more month as the end comes into view (to 2099); "Của bạn (n)" lists your events. Sheets (one system sheet with Android's stack): an event's detail (edit or delete this time or all times), the editor (all day, start date picker by year/month/day, time, length, repeat with weekdays, lunar or solar rule, leap month only, end, reminders, place, note, colour), a built-in day's reminder | 2026-11-28: UI tests pass |
| `LichAm/Reminders.swift` | Reminders as local notifications. iOS has no alarm that wakes the app (Android's way), and Apple's documentation says it keeps an app's 64 soonest local notifications: so the next 64 reminders (`PersonalEvents.nextReminders`) are registered at once, each with a calendar trigger at its minute in local time, and replaced when events change, when the app becomes active and on a clock or time zone change. Text as shared/design/events.md ("Hôm nay · Cả ngày"…, the place on a second line); a banner also while the app is open; tapping one opens that day in Lịch. Permission is asked when the editor opens with reminders, when one is added and on Lưu; when notifications are off, "Bật thông báo để nhận nhắc" offers Settings. Limit: an app left closed past its 64th reminder shows no more until it is opened again | 2026-11-28: UI tests pass; the simulator held 64 for a daily event, next at 08:00 local (app log) |
| `LichAmUITests/` | XCUITest on a simulator. Hôm nay: arrows, swipe to turn, vertical drag scrolls, cards on screen, theme cycles and is kept after a restart, link opens the Calendar. Lịch: arrows, flick and slow drag turn the month, tap selects a day (and a day of the next month shows that month), the picker (month, 12-year page), a leap month day, "Hôm nay", vertical drag on the grid scrolls. Sự kiện: add on another day, yearly lunar event with detail, edit all, delete all, a built-in day's reminder on and off, events kept after a restart. Nhắc: a reminder's banner (the app's `-ui-reminder-soon` shows the first one 3 s after it is registered) and its tap opening the day, a daily event's reminders. Tiện ích: Đổi ngày (lunar 15/11/2026 = 23/12/2026, then opened in Lịch), Tìm ngày tốt, Xuất lịch (saved through the system's sheet), birth date (tuổi, mệnh, cleared), Thông tin, Giao diện. Widgets: SpringBoard's gallery lists both widgets in three sizes | 24/24 pass 2026-11-28 |
| `LichAm/ToolsScreen.swift`, `ToolsSheets.swift` | Tiện ích (Android's ToolsScreen and ui/Sheets.kt): Đổi ngày âm – dương (the two dates side by side, year → month → day grids in either calendar, "Xem ngày" opens it), Tìm ngày tốt (cưới hỏi, khởi công, khai trương in the next two months), Xuất lịch (built-in groups, your events, 1–30 years; the .ics is saved where the user picks, with the system's file sheet), Ngày sinh (year → month → day; tuổi and mệnh), Màu chủ đạo, Giao diện, Thông tin ứng dụng, the privacy policy (browser). iPad (≥ 720 pt): two columns | 2026-11-28: UI tests pass; the saved file held 15 VEVENTs for Tết over 5 years |
| `LichAmWidget/` | Widgets (Android's widget/), each small, medium and large, drawn with the app's colours (palette of the mệnh or the chosen accent; light or dark as Giao diện says) and fonts (`Theme.swift` and the font files are built into the extension too). Tờ lịch hôm nay: the sheet's lacquer and gold foil; small = day, weekday, lunar date; medium = the day left, the lunar date in its ring and the day's name right (holiday, else your event, else a commemorative day or festival, else mồng 1 / rằm); large = the app's sheet with the can chi. Lịch tháng: the Calendar's grid (lunar days, good / bad / clash / event dots, today ringed, Sundays and holidays red, other months faded), today's names in the header; large shows every week, medium and small three weeks from today's; in medium and large a tap on a day opens it in Lịch. Redrawn at midnight and whenever the app saves. Debug builds draw them in the app with `-ui-widgets sheet` or `-ui-widgets month` (screenshots; `-ui-open …&d=` sets the day) | 2026-11-28: SpringBoard's widget gallery shows both in three sizes, drawn by the extension (UI test). Not tested on the home screen: the iOS 26.5 simulator's SpringBoard crashes in `-[SBHRippleSimulation clear]` when a widget is dropped in, so a tap on a day was not tried |
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
