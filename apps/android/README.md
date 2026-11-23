# Android app

Kotlin + Jetpack Compose. Application id `com.coding4pizza.lunar` (`.debug` for debug builds), from the app's site lunar.coding4pizza.com; permanent after the first Play upload. The code's package stays `io.github.novapizza.licham`.

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

## Release builds (Google Play)

Play App Signing keeps the app signing key; the build signs with an upload key kept outside the repo.

1. Create the upload key once (it asks for the passwords; keep the .jks and the passwords safe, losing them means asking Google to reset the upload key):
   ```
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\keys\lunar-upload.jks" -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Write `apps/android/keystore.properties` (ignored by git), with the passwords either in it or, better, in a file next to the .jks that `passwordsFile` names, so nothing secret sits in the repository folder:
   ```
   storeFile=C:/Users/<you>/keys/lunar-upload.jks
   keyAlias=upload
   passwordsFile=C:/Users/<you>/keys/lunar-upload-passwords.properties
   ```
   and in that file `storePassword=...` and `keyPassword=...` (the same for a PKCS12 keystore, keytool's default: `keytool -storepasswd` changes both).
3. `.\gradlew.bat :app:bundleRelease` gives `app/build/outputs/bundle/release/app-release.aab`, the file to upload. Raise `versionCode` in `app/build.gradle.kts` for every upload.

In CI the same key comes from environment variables (`ANDROID_UPLOAD_STORE_FILE`, `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`), and `ANDROID_VERSION_CODE` / `ANDROID_VERSION_NAME` set the version.

### Publishing from GitHub Actions

`.github/workflows/android-release.yml` runs on a tag `android-vX.Y.Z`:
1. Unit tests.
2. Then, in the environment `play-release` (limited to `android-v*` tags), the release notes check and `bundleRelease` signed with the upload key: versionName X.Y.Z, versionCode X·10000 + Y·100 + Z.
3. A check that the bundle is not debug-signed (and matches `ANDROID_UPLOAD_CERT_SHA256` when set).
4. The AAB and the R8 mapping kept as an artifact.
5. The upload to Play (Workload Identity, or a service account key): release "X.Y.Z (code)", track `internal`, status `draft` by default; Run workflow on a tag picks another track or status.

**Release notes** ("What's new" on Play) come from `apps/android/whatsnew/whatsnew-vi-VN` (500 characters at most; a longer or empty file stops the run). Update it in the commit you tag: the run warns when it has not changed since the previous `android-v*` tag.

The secrets and variables it needs are listed at the top of the workflow. Play's API only accepts an app that already has one release uploaded by hand in Play Console. 0.1.5 (105) was the first release uploaded this way (23 Nov 2026).

```
git tag -a android-v0.1.6 -m "Android 0.1.6"
git push origin android-v0.1.6
```

Pushing the tag starts a run with status `draft`. For another status, cancel that run before its release job starts and use Run workflow on the tag.

Without `keystore.properties` the release build is signed with the debug key: it installs for testing (`:app:assembleRelease`), Play refuses it. Release builds run R8; `proguard-rules.pro` keeps what reflection needs (WorkManager's Room database, crashed at start without it).

The launcher icon follows the app's main colour (Màu chủ đạo): five `activity-alias` entries in the manifest, one per palette (`mipmap-anydpi-v26/ic_launcher*.xml`), exactly one enabled; `LauncherIcon` switches them when the app leaves the screen. Checked on the emulator's Pixel Launcher: an icon pinned to the home screen stays and changes colour. Other launchers not checked. The Play listing keeps one icon.

The privacy policy Play asks for is `apps/web/public/privacy.html`, served at https://lunar.coding4pizza.com/privacy and linked from Tiện ích.

## Not done yet

Real-device testing, unfolded-foldable (two panes split at the fold) layout.
