/**
 * Geometry of the tear-off sheet's page turn, a corner peel: the bottom-right corner curls up
 * (paper wrapping round a cylinder, the back of the sheet showing), the fold runs diagonally
 * across the sheet towards the top-left, and the folded sheet leaves past that corner. The sheet
 * itself never slides sideways (a drift to the left read as the sheet sliding off, 2026-11-22).
 *
 * The sheet is cut parallel to the fold into STRIPS fixed strips of equal width; each frame
 * turns every strip about the fold direction by its own angle, so together they bend round a
 * cylinder. Fixed strips let the browser animate transforms only: an earlier version cut 26
 * page-sized pieces anew every frame, which on a phone blanked the sheet for a frame at the start
 * and end of a turn (screen recording, 2026-11-22).
 *
 * Coordinates: the page's x right, y down, z towards the viewer, px, origin at its top-left. The
 * strips live in the "fold frame": u along the fold, s across it (towards the peeled corner),
 * i.e. the page rotated by -TILT_DEG.
 *
 * The Android app (ui/PageCurl.kt) draws the same curve with the same constants: keep in step.
 */

export const STRIPS = 48;
export const TURN_MS = 800;
/** CSS `perspective`, px; vanishing point at the page's top centre. */
export const PERSPECTIVE = 2400;
/** The peel runs 25° off vertical, towards the bottom-right corner. */
export const TILT_DEG = 25;
const TILT = (TILT_DEG * Math.PI) / 180;
/** How far the flap folds over, degrees (180 = flat on the sheet): real paper springs back. */
const MAX_BEND = 155;
/** Length of the curl (the part wrapped round the cylinder), as a fraction of the page diagonal. */
const CURL = 0.22;

const COS = Math.cos(TILT);
const SIN = Math.sin(TILT);

/** The page's bounding box in the fold frame (u along the fold, s across it). */
export function foldFrame(width: number, height: number) {
  return { uMin: -height * SIN, uMax: width * COS, sMax: width * SIN + height * COS };
}

export interface StripPose {
  /** Where the strip's starting edge has moved to, across the fold (s) and towards the viewer (z), px. */
  s: number;
  z: number;
  /** Turn about the fold, degrees: 0 flat, 90 on edge, 180 folded flat over. */
  angle: number;
  /** True when the back of the paper faces the viewer. */
  back: boolean;
  /** Darkening of the visible side, 0..1. */
  shade: number;
}

export interface CurlFrame {
  strips: StripPose[];
  /** Opacity of the turning page. */
  opacity: number;
  /**
   * Shadow the lifted paper casts on the sheet underneath: black at `alpha` on the fold line
   * (`from`, px across the fold from the page's top-left corner), fading out by `to`.
   */
  shadow: { from: number; to: number; alpha: number };
}

/**
 * The page at progress p (0 = flat, 1 = gone) for a page of `width` × `height`. Slow at first,
 * then faster, like peeling a sheet off and pulling it away: the fold travels from inside the
 * bottom-right corner to past the top-left one on an ease-in curve (with some speed from the
 * start, so the corner moves at once), the curl closes to MAX_BEND by 45 %, and the sheet fades
 * over the last 12 %.
 */
export function curlFrame(progress: number, width: number, height: number): CurlFrame {
  const p = Math.min(1, Math.max(0, progress)); // Math.pow of a negative number is NaN
  const { sMax } = foldFrame(width, height);

  const travel = 0.3 * p + 0.7 * Math.pow(p, 2.2);
  const fold = 0.8 - 1.02 * travel;
  const b = Math.min(1, p / 0.45);
  const bend = MAX_BEND * (1 - (1 - b) * (1 - b));
  const opacity = p > 0.88 ? 1 - (p - 0.88) / 0.12 : 1;

  const angleAt = (s: number) => bend * Math.min(1, Math.max(0, (s / sMax - fold) / CURL));
  const sFold = fold * sMax;
  const h = sMax / STRIPS;

  // Where the current strip's starting edge has moved to. Once the fold has passed the top-left
  // corner (sFold < 0) the sheet keeps turning about the fold line, not about the corner: the
  // paper between the fold and the corner is walked through first, so the folded sheet keeps
  // sliding away.
  let S = 0, Z = 0;
  if (sFold < 0) {
    S = sFold;
    for (let s = sFold; s < 0; s += h / 2) {
      const len = Math.min(h / 2, -s);
      const rad = (angleAt(s + len / 2) * Math.PI) / 180;
      S += len * Math.cos(rad);
      Z += len * Math.sin(rad);
    }
  }

  const strips: StripPose[] = [];
  for (let k = 0; k < STRIPS; k++) {
    const angle = angleAt((k + 0.5) * h);
    const rad = (angle * Math.PI) / 180;
    const cos = Math.cos(rad);
    // Lit from the front: paper on edge is darkest.
    strips.push({ s: S, z: Z, angle, back: cos < 0, shade: angle === 0 ? 0 : Math.min(0.6, (1 - Math.abs(cos)) * 0.55) });
    S += h * cos;
    Z += h * Math.sin(rad);
  }
  return { strips, opacity, shadow: { from: sFold, to: sFold + 0.4 * sMax, alpha: 0.4 * Math.min(1, bend / 90) * opacity } };
}
