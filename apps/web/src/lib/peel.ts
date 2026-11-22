/**
 * Where the tear-off sheet folds, from a finger. Sheet-local CSS px, origin at the paper's
 * top-left, y down.
 *
 * A touch near an edge grabs the paper at that edge (at a corner when near two): the grab point
 * G on the edge and the edge's outward normal e. The finger's travel Δ moves G with it. The fold
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
 * The grab for a touch at (x, y) on a w × h sheet: the nearest edge within `zone` px (a corner
 * when two edges are), or null. `anywhere` takes the nearest edge whatever the distance (mouse).
 */
export function grabAt(x: number, y: number, w: number, h: number, zone: number, anywhere = false): Grab | null {
  const d = { left: x, right: w - x, top: y, bottom: h - y };
  const near = (Object.keys(d) as (keyof typeof d)[]).filter(k => d[k] <= zone);
  const corner = near.length >= 2 && (near.includes('left') || near.includes('right')) && (near.includes('top') || near.includes('bottom'));
  if (corner) {
    const sx = near.includes('left') ? -1 : 1, sy = near.includes('top') ? -1 : 1;
    return { gx: sx < 0 ? 0 : w, gy: sy < 0 ? 0 : h, ex: sx * Math.SQRT1_2, ey: sy * Math.SQRT1_2 };
  }
  let edge = near[0];
  if (!edge) {
    if (!anywhere) return null;
    edge = (Object.keys(d) as (keyof typeof d)[]).reduce((a, b) => (d[a] <= d[b] ? a : b));
  }
  if (edge === 'left') return { gx: 0, gy: y, ex: -1, ey: 0 };
  if (edge === 'right') return { gx: w, gy: y, ex: 1, ey: 0 };
  if (edge === 'top') return { gx: x, gy: 0, ex: 0, ey: -1 };
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
