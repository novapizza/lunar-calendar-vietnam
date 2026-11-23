/**
 * Where the tear-off sheet folds, from a finger. Sheet-local CSS px, origin at the paper's
 * top-left, y down.
 *
 * A touch near the bottom grabs the paper at the bottom edge (at a corner when near a side): the
 * grab point G on the edge and the edge's outward normal e. The finger's travel Δ moves G with it. The fold
 * is where paper turned over a cylinder of radius r puts G under the finger: with v = -Δ and the
 * fold normal n (towards the peeling side), G sits d0 = (|v·n| + πr)/2 past the fold. n starts as
 * e (a fresh touch lifts the edge at once) and turns to v as the finger travels.
 */

export interface Grab {
  /** Grab point on the paper's edge and the edge's outward unit normal. */
  gx: number; gy: number; ex: number; ey: number;
}

export interface Fold { fx: number; fy: number; nx: number; ny: number }

/**
 * The grab for a touch at (x, y) on a w × h sheet, which turns from its bottom only (user,
 * 2026-11-23): a bottom corner when the touch is within `zone` px of a side, else the bottom
 * edge under the finger.
 */
export function grabBottom(x: number, w: number, h: number, zone: number): Grab {
  if (x <= zone) return { gx: 0, gy: h, ex: -Math.SQRT1_2, ey: Math.SQRT1_2 };
  if (w - x <= zone) return { gx: w, gy: h, ex: Math.SQRT1_2, ey: Math.SQRT1_2 };
  return { gx: x, gy: h, ex: 0, ey: 1 };
}

/** The fold for a grab, the finger's travel (dx, dy), radius r and lift 0..1 (how far a fresh touch has curled the edge). */
export function foldFor(g: Grab, dx: number, dy: number, r: number, lift: number): Fold & { reach: number } {
  const vx = -dx, vy = -dy;
  const bx = vx + g.ex * Math.PI * r, by = vy + g.ey * Math.PI * r;
  const len = Math.hypot(bx, by) || 1;
  const nx = bx / len, ny = by / len;
  const reach = Math.max(0, vx * nx + vy * ny);
  const d0 = (reach + Math.PI * r * lift) / 2;
  return { fx: g.gx - nx * d0, fy: g.gy - ny * d0, nx, ny, reach };
}

/** How far the paper reaches from the grab across the sheet along -n (the whole sheet has turned when the fold has passed it). */
export function span(g: Grab, nx: number, ny: number, w: number, h: number): number {
  return Math.max(...[[0, 0], [w, 0], [0, h], [w, h]].map(([x, y]) => (g.gx - x) * nx + (g.gy - y) * ny));
}

/** Cylinder radius for a sheet: a little under a tenth of its short side. */
export const radiusFor = (w: number, h: number) => Math.min(42, Math.max(16, Math.min(w, h) * 0.085));

export const easeOut = (t: number) => 1 - (1 - t) ** 3;
