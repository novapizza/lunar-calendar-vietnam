# Android app (planned)

Kotlin + Jetpack Compose, with Glance widgets. Not started: the Gradle project comes with the first Android work.

Reads from `shared/`:
- `shared/data/*.json`: package as assets (or generate Kotlin constants) instead of re-typing names, holidays, nạp âm.
- `shared/design/tokens.json`: generate a Compose `ColorScheme` per element (light/dark).
- `shared/design/icon.svg`: app icon source (adaptive icon). Per-element launcher icons are possible through activity-alias.
- `shared/test-vectors/*.json`: JUnit cases; the Kotlin lunar conversion must reproduce them exactly.
