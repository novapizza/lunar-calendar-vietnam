# iOS app (in progress)

Swift + SwiftUI, with WidgetKit widgets, the same features as the Android app (decided with the
user 2026-11-25). Built on a Mac with Xcode; not published yet.

| Folder | What | Status |
|---|---|---|
| `LunarCore/` | Swift package: lunar calendar from Hồ Ngọc Đức's tables, can chi, trực, rating, giờ hoàng đạo, holidays, mệnh, personal events (occurrences, reminders, .ics), ported from `apps/android/core`. Pure Swift + Foundation. | written 2026-11-25, not yet built |
| app, widgets | XcodeGen project, SwiftUI screens, WidgetKit | not started |

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
