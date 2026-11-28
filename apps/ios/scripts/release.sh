#!/usr/bin/env bash
# Archives the iOS app and uploads it to App Store Connect, where it shows in TestFlight and can be
# sent for review. On a Mac with Xcode and XcodeGen, from anywhere in the repository:
#   TEAM_ID=ABCDE12345 apps/ios/scripts/release.sh
#
# TEAM_ID   the Apple Developer team (developer.apple.com → Account → Membership details); not
#           needed once DEVELOPMENT_TEAM is set in project.yml.
# BUILD     the build number; by default the commit count of HEAD, which rises with every commit
#           (App Store Connect takes each build number once per version). The version itself is
#           MARKETING_VERSION in project.yml.
# Signing and upload use the Apple ID signed in to Xcode (Xcode → Settings → Accounts), or an App
# Store Connect API key (for CI): ASC_KEY_PATH=…/AuthKey_XXXX.p8 ASC_KEY_ID=XXXX ASC_ISSUER_ID=<uuid>.
# MODE      upload (default) | export: the signed .ipa in build/release/, not uploaded |
#           check: a Release archive without signing (no team, no account), to see that it builds
#           and what goes into it.
set -euo pipefail
cd "$(dirname "$0")/.."
MODE=${MODE:-upload}
OUT=build/release
BUILD=${BUILD:-$(git rev-list --count HEAD)}
XCODEGEN=$(command -v xcodegen || echo /usr/local/bin/xcodegen)
ARCHIVE="$OUT/LichAm.xcarchive"
APP="$ARCHIVE/Products/Applications/LichAm.app"

rm -rf "$OUT" && mkdir -p "$OUT"
"$XCODEGEN" generate -q

# macOS's bash 3.2 treats an empty array as unset under set -u, hence the ${AUTH[@]+…} below.
AUTH=()
if [ -n "${ASC_KEY_PATH:-}" ]; then
  AUTH=(-authenticationKeyPath "$ASC_KEY_PATH" -authenticationKeyID "${ASC_KEY_ID:?}" -authenticationKeyIssuerID "${ASC_ISSUER_ID:?}")
fi
TEAM=()
[ -n "${TEAM_ID:-}" ] && TEAM=(DEVELOPMENT_TEAM="$TEAM_ID")

archive() {
  xcodebuild archive -project LichAm.xcodeproj -scheme LichAm -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$ARCHIVE" CURRENT_PROJECT_VERSION="$BUILD" \
    "$@" -quiet
}

summary() {
  echo "version $(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' "$APP/Info.plist")" \
       "build $(/usr/libexec/PlistBuddy -c 'Print CFBundleVersion' "$APP/Info.plist")"
  echo "privacy manifests:"
  find "$APP" -name PrivacyInfo.xcprivacy | sed "s|^$APP|  LichAm.app|"
}

if [ "$MODE" = check ]; then
  archive CODE_SIGNING_ALLOWED=NO
  summary
  exit 0
fi

archive -allowProvisioningUpdates ${TEAM[@]+"${TEAM[@]}"} ${AUTH[@]+"${AUTH[@]}"}
summary

DESTINATION=upload
[ "$MODE" = export ] && DESTINATION=export
cat > "$OUT/ExportOptions.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>$DESTINATION</string>
  <key>signingStyle</key><string>automatic</string>
  ${TEAM_ID:+<key>teamID</key><string>$TEAM_ID</string>}
  <key>manageAppVersionAndBuildNumber</key><false/>
  <key>uploadSymbols</key><true/>
</dict>
</plist>
EOF
xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportPath "$OUT" \
  -exportOptionsPlist "$OUT/ExportOptions.plist" -allowProvisioningUpdates ${AUTH[@]+"${AUTH[@]}"}

if [ "$DESTINATION" = upload ]; then
  echo "Uploaded build $BUILD. It shows in App Store Connect → TestFlight once Apple has processed it."
else
  echo "Exported: $(ls "$OUT"/*.ipa)"
fi
