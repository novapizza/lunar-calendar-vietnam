#!/usr/bin/env bash
# App Store screenshots from the simulators. On a Mac with Xcode and XcodeGen:
#   apps/ios/scripts/appstore-screenshots.sh
#
# Builds the app for the simulator once (Debug: the widget gallery, -ui-widgets, is debug only),
# then on the two sizes App Store Connect asks for, an iPhone 6.9″ (iPhone 17 Pro Max, 1320 × 2868)
# and, because the app runs on iPad, an iPad 13″ (iPad Pro 13-inch (M5), 2064 × 2752), opens each
# scene below with a fresh store and saves build/appstore-screenshots/<iphone|ipad>/NN-name.png.
# The status bar shows 9:41 with a full battery while it runs.
# IPHONE= / IPAD= name other simulators; DAY= the day shown (default Mồng 1 Tết Đinh Mùi).
set -euo pipefail
cd "$(dirname "$0")/.."
IPHONE=${IPHONE:-iPhone 17 Pro Max}
IPAD=${IPAD:-iPad Pro 13-inch (M5)}
DAY=${DAY:-2027-02-06}
OUT=build/appstore-screenshots
DD=build/screenshots-dd
APP_ID=com.coding4pizza.lunar
XCODEGEN=$(command -v xcodegen || echo /usr/local/bin/xcodegen)

# name | appearance | tab | extra launch arguments
SCENES="01-today|light|today|
02-calendar|light|calendar|
03-events|light|events|
04-tools|light|tools|
05-today-dark|dark|today|
06-widget-sheet|light|today|-ui-widgets sheet
07-widget-month|light|today|-ui-widgets month"

"$XCODEGEN" generate -q
xcodebuild build -project LichAm.xcodeproj -scheme LichAm -configuration Debug \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath "$DD" -quiet
APP="$DD/Build/Products/Debug-iphonesimulator/LichAm.app"

udid() {
  xcrun simctl list devices available | grep -F "    $1 (" | head -1 | grep -oE '[0-9A-F-]{36}' \
    || { echo "no simulator named \"$1\" (xcrun simctl list devices available)" >&2; exit 1; }
}

capture() {  # <device> <file>: once the app has drawn and settled
  # The launch screen is one colour and compresses to ~120 KB at iPad size (seen: an iPad shot
  # taken 5 s after launch was still the launch screen). Shoot every 2 s until a shot is bigger
  # than that and the same size as the one before, for at most ~40 s.
  local f=$2 prev=0 size i
  sleep 3
  for i in $(seq 1 20); do
    xcrun simctl io "$1" screenshot "$f" >/dev/null 2>&1
    size=$(stat -f %z "$f")
    if [ "$size" -gt 250000 ] && [ "$size" -eq "$prev" ]; then return 0; fi
    prev=$size
    sleep 2
  done
  echo "  warning: $f may not have settled (last size $size bytes)" >&2
}

shoot() {  # <simulator name> <folder>
  local dev dir name look tab extra
  dev=$(udid "$1"); dir="$OUT/$2"
  rm -rf "$dir" && mkdir -p "$dir"
  if xcrun simctl boot "$dev" 2>/dev/null; then
    xcrun simctl bootstatus "$dev" -b >/dev/null
    # A fresh boot shows system banners for a while (seen: "Ready for Apple Intelligence"
    # over a shot ~30 s after boot); let them come and go first.
    sleep "${WARMUP:-60}"
  fi
  xcrun simctl status_bar "$dev" override --time 9:41 --batteryState charged --batteryLevel 100 \
    --cellularMode active --cellularBars 4 --wifiBars 3
  xcrun simctl install "$dev" "$APP"
  while IFS='|' read -r name look tab extra; do
    xcrun simctl ui "$dev" appearance "$look"
    xcrun simctl terminate "$dev" "$APP_ID" 2>/dev/null || true
    # $extra is split on purpose: "-ui-widgets sheet" is two arguments.
    xcrun simctl launch "$dev" "$APP_ID" -ui-reset -ui-open "$APP_ID://open?tab=$tab&d=$DAY" $extra >/dev/null
    capture "$dev" "$dir/$name.png"
    echo "$2/$name.png"
  done <<< "$SCENES"
  xcrun simctl terminate "$dev" "$APP_ID" 2>/dev/null || true
  xcrun simctl ui "$dev" appearance light
  xcrun simctl status_bar "$dev" clear
}

shoot "$IPHONE" iphone
shoot "$IPAD" ipad
echo "in apps/ios/$OUT"
