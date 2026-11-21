/**
 * Orientation policy.
 * - Phones (shorter screen side under 600 CSS px, including a foldable's cover screen):
 *   portrait only.
 * - Unfolded foldables, tablets and computers: free rotation.
 *
 * The web manifest says `orientation: "any"` so larger devices can rotate; phones are
 * locked here where the browser allows it (installed PWA / fullscreen; the call is
 * rejected in an ordinary browser tab). `RotateOverlay` covers the cases where the lock
 * is not available.
 */

const PHONE_MAX_SHORT_SIDE = 600;

type LockableOrientation = ScreenOrientation & {
  lock?: (orientation: 'portrait') => Promise<void>;
};

function isPhoneScreen(): boolean {
  return Math.min(window.screen.width, window.screen.height) < PHONE_MAX_SHORT_SIDE;
}

function applyOrientationPolicy(): void {
  const orientation = window.screen.orientation as LockableOrientation | undefined;
  if (!orientation) return;
  try {
    if (isPhoneScreen()) {
      orientation.lock?.('portrait').catch(() => { /* not allowed in a browser tab */ });
    } else {
      // e.g. a foldable that was locked while folded and has just been opened
      orientation.unlock?.();
    }
  } catch {
    /* Screen Orientation API missing or blocked */
  }
}

applyOrientationPolicy();
// Folding / unfolding changes which screen is in use.
window.addEventListener('resize', applyOrientationPolicy);
