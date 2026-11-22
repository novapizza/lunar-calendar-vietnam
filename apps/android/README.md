# Android app

Kotlin + Jetpack Compose. Application id `io.github.novapizza.licham` (`.debug` for debug builds); change it before the first Play upload if another one is wanted, it is permanent after that.

## Modules

| Module | What |
|---|---|
| `core` | Pure Kotlin (JVM): lunar calendar from Hồ Ngọc Đức's year tables, can chi, trực, day rating, giờ hoàng đạo, activities, holidays, mệnh, colour tokens. Reads `shared/data/*.json` and `shared/design/tokens.json` as Java resources straight from `shared/` (no copies). |
| `app` | Compose UI, same features as the web app: Hôm nay (day arrows/swipe, can chi, rating, zodiac clash, activities, giờ hoàng đạo, notes), Lịch (month grid with lunar days and dots, month/year picker, selected-day panel), Sự kiện (upcoming holidays), Tiện ích (date converter, auspicious-date finder, lunar events + .ics export, birth date → mệnh colour, theme). Bottom tab bar on phones, left rail and two columns from 600 dp. Data in SharedPreferences, same shapes as the web's localStorage. |

Look and feel follow the web app: Be Vietnam Pro, plus Playfair Display for the Today tear-off sheet (`app/src/main/res/font`, licences in `licenses/`), Tailwind line heights and spacing, the web's card/chip/button/field styles and shadows (`ui/Components.kt`). Checked against the web at 427 dp wide: Hôm nay, Lịch and Sự kiện text positions within 1 dp.

Widgets (Glance) will use `core` too.

## Commands (from `apps/android`)

```
.\gradlew.bat :core:test            # unit tests against shared/test-vectors
.\gradlew.bat :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: Gradle 9.5.1, AGP 9.2.0, Kotlin 2.3.21, Compose BOM 2026.05.01, compileSdk/targetSdk 36, minSdk 26, JDK 21 from Android Studio (`JAVA_HOME`).

## Correctness

`core` must reproduce `shared/test-vectors/` exactly: 10,659 solar dates (every 7th day 1900–2099, each Tết and leap-month start) and 599 mệnh cases. `TestVectorsTest` also converts every vector back from lunar to solar.

## Not done yet

Widgets, release signing, Play listing, real-device testing, unfolded-foldable (two panes split at the fold) layout.
