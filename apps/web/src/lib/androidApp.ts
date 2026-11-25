/**
 * The Android app (com.coding4pizza.lunar). On Android the web offers it instead of installing
 * the PWA (user, 2026-11-25): one link that opens the app when it is installed, else its page on
 * Google Play.
 */

export const ANDROID_PACKAGE = 'com.coding4pizza.lunar';
export const PLAY_URL = `https://play.google.com/store/apps/details?id=${ANDROID_PACKAGE}`;

/**
 * An Android intent URL (Chrome, Samsung Internet, Firefox): it opens the app's main screen
 * through its `com.coding4pizza.lunar://open` filter (AndroidManifest.xml); with the app missing,
 * or a version older than that filter (0.1.12 and before), the browser goes to the fallback,
 * Google Play. The scheme is the package name (reverse domain, RFC 8252) so no other app uses it,
 * and the URL names the package too, so only this app can answer it.
 */
export const OPEN_APP_URL =
  `intent://open#Intent;scheme=${ANDROID_PACKAGE};package=${ANDROID_PACKAGE};` +
  `S.browser_fallback_url=${encodeURIComponent(PLAY_URL)};end`;

export function isAndroid(): boolean {
  return /Android/i.test(navigator.userAgent);
}
