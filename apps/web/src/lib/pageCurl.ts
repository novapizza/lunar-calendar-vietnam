/**
 * Geometry of the tear-off sheet's page turn, a corner peel: the bottom-right corner curls up
 * (paper wrapping round a cylinder, the back of the sheet showing), the fold runs diagonally
 * across the sheet towards the top-left, and the folded sheet leaves past that corner. The sheet
 * itself never slides sideways (a drift to the left read as the sheet sliding off, 2026-11-22).
 *
 * The sheet is cut parallel to the fold into: one flat piece before the fold, CURL_BANDS thin
 * bands for the part wrapped round the cylinder (so the bend looks round, not faceted), and one
 * flat piece folded over beyond it. Each is a flat plane turned about the fold direction.
 * Coordinates: x right, y down, z towards the viewer, px, origin at the page's top-left.
 *
 * The Android app has the same function (ui/PageCurl.kt): keep the two in step.
 */

export const CURL_BANDS = 24;
/** Pieces drawn: the flat piece, the curl bands, the folded piece. */
export const PIECES = CURL_BANDS + 2;
export const TURN_MS = 800;
/** CSS `perspective`, px; vanishing point at the page's top centre. */
export const PERSPECTIVE = 2400;
/** The peel runs 25° off vertical, towards the bottom-right corner. */
export const TILT_DEG = 25;
const TILT = (TILT_DEG * Math.PI) / 180;
/** How far the flap folds over, degrees (180 = flat on the sheet). */
const MAX_BEND = 155;
/** Length of the curl (the part wrapped round the cylinder), as a fraction of the page diagonal. */
const CURL = 0.22;

export type Pt = [number, number];

export interface Piece {
  /** The piece's outline on the flat page (a polygon, page coordinates); empty = not drawn. */
  poly: Pt[];
  /** A point on the piece's starting edge: the pivot of its turn. */
  origin: Pt;
  /** Translation after the turn, px. */
  shift: [number, number, number];
  /** Turn about the fold direction, degrees: 0 flat, 90 on edge, 180 folded flat over. */
  angle: number;
  /** True when the back of the paper faces the viewer. */
  back: boolean;
  /** Even darkening of the visible side, 0..1 (0 on the flat piece, rising smoothly round the curl). */
  shade: number;
}

export interface CurlFrame {
  pieces: Piece[];
  /** Unit vector of the fold line (the turning axis), in the page plane. */
  axis: Pt;
  /** Opacity of the turning page. */
  opacity: number;
  /**
   * Shadow the lifted paper casts on the sheet underneath: black at `alpha` on the fold line
   * (`from`, distance along the peel direction from the page's top-left corner, px), fading
   * out by `to`. One smooth gradient that slides with the fold (two separate shadows, one on
   * the flat paper and one even over the sheet underneath, met in a hard edge, 2026-11-22).
   */
  shadow: { from: number; to: number; alpha: number };
}

/** Clips a polygon to the half-plane dot(p, n) >= c (Sutherland–Hodgman). */
function clip(poly: Pt[], n: Pt, c: number): Pt[] {
  const out: Pt[] = [];
  for (let i = 0; i < poly.length; i++) {
    const p = poly[i], q = poly[(i + 1) % poly.length];
    const dp = p[0] * n[0] + p[1] * n[1] - c, dq = q[0] * n[0] + q[1] * n[1] - c;
    if (dp >= 0) out.push(p);
    if ((dp >= 0) !== (dq >= 0)) {
      const t = dp / (dp - dq);
      out.push([p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t]);
    }
  }
  return out;
}

/**
 * The page at progress p (0 = flat, 1 = gone) for a page of `width` × `height`.
 * Slow at first, then faster, like peeling a sheet off and pulling it away: the fold travels
 * from inside the bottom-right corner to past the top-left one on an ease-in curve (with some
 * speed from the start, so the corner moves at once), the curl closes to MAX_BEND by 45 %, and the
 * sheet fades over the last 12 %.
 */
export function curlFrame(progress: number, width: number, height: number): CurlFrame {
  const p = Math.min(1, Math.max(0, progress)); // Math.pow of a negative number is NaN
  const d: Pt = [Math.sin(TILT), Math.cos(TILT)]; // across the fold, towards the peeled corner
  const axis: Pt = [Math.cos(TILT), -Math.sin(TILT)]; // along the fold; axis × d = +z
  const sMax = width * d[0] + height * d[1];

  const travel = 0.3 * p + 0.7 * Math.pow(p, 2.2);
  const fold = 0.8 - 1.02 * travel;
  const b = Math.min(1, p / 0.45);
  // The flap never folds flat onto the sheet: real paper springs back, so it stops at
  // MAX_BEND and stands a little out from the page (180° looked pressed flat, 2026-11-22).
  const bend = MAX_BEND * (1 - (1 - b) * (1 - b));
  const opacity = p > 0.88 ? 1 - (p - 0.88) / 0.12 : 1;

  const angleAt = (s: number) => bend * Math.min(1, Math.max(0, (s / sMax - fold) / CURL));
  const sFold = fold * sMax;
  const sCurlEnd = (fold + CURL) * sMax;

  // Where the current piece's starting edge has moved to (along d, along z). Once the fold has
  // passed the top-left corner (sFold < 0) the sheet keeps turning about the fold line, not
  // about the corner: the paper between the fold and the corner is walked through first, so
  // the folded sheet keeps sliding away.
  let S = 0, Z = 0;
  if (sFold < 0) {
    S = sFold;
    const step = (CURL * sMax) / CURL_BANDS;
    for (let s = sFold; s < 0; s += step) {
      const len = Math.min(step, -s);
      const rad = (angleAt(s + len / 2) * Math.PI) / 180;
      S += len * Math.cos(rad);
      Z += len * Math.sin(rad);
    }
  }

  // Piece boundaries along d: flat | CURL_BANDS curl bands | folded.
  const clamp = (s: number) => Math.min(sMax, Math.max(0, s));
  const a = clamp(sFold), c = clamp(sCurlEnd);
  const edges = [0, a];
  for (let i = 1; i <= CURL_BANDS; i++) edges.push(a + ((c - a) * i) / CURL_BANDS);
  edges.push(sMax);

  const rect: Pt[] = [[0, 0], [width, 0], [width, height], [0, height]];
  const pieces: Piece[] = [];
  for (let k = 0; k < PIECES; k++) {
    const s0 = edges[k], s1 = edges[k + 1];
    const len = s1 - s0;
    const angle = k === 0 ? 0 : k === PIECES - 1 ? angleAt(sMax) : angleAt((s0 + s1) / 2);
    const rad = (angle * Math.PI) / 180;
    // 0.5px overlap on each side so neighbouring pieces leave no seam.
    const poly = len > 0.01 ? clip(clip(rect, d, s0 - 0.5), [-d[0], -d[1]], -(s1 + 0.5)) : [];
    const cos = Math.cos(rad);
    pieces.push({
      poly,
      origin: [d[0] * s0, d[1] * s0],
      shift: [d[0] * (S - s0), d[1] * (S - s0), Z],
      angle,
      back: cos < 0,
      // Lit from the front: paper on edge is darkest.
      shade: k === 0 ? 0 : Math.min(0.6, (1 - Math.abs(cos)) * 0.55),
    });
    S += len * cos;
    Z += len * Math.sin(rad);
  }
  const shadow = { from: sFold, to: sFold + 0.4 * sMax, alpha: 0.4 * Math.min(1, bend / 90) * opacity };
  return { pieces, axis, opacity, shadow };
}
