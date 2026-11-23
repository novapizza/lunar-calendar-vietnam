import { useCallback, useEffect, useLayoutEffect, useRef, type PointerEvent as ReactPointerEvent, type RefObject } from 'react';
import { paintElement, cssColor, paintFontsLoaded, paintFontsReady } from '../lib/domPaint';
import { CurlRenderer } from '../lib/curlGL';
import { easeOut, foldFor, grabBottom, radiusFor, span, type Grab } from '../lib/peel';

interface Props {
  /** The live paper (TearOffSheet's sheet wrapper, `relative`), with the rings in it. */
  paperRef: RefObject<HTMLDivElement | null>;
  /** A hidden copy of the sheet showing the current day (its first child is the sheet). */
  sourceRef: RefObject<HTMLDivElement | null>;
  /** Changes when what the source sheet shows changes (day, clock): the texture is repainted. */
  faceKey: string;
  /** A turn from the arrows or a day change elsewhere: forward peels the old sheet off, back brings the new one down. */
  turn: { forward: boolean; id: number } | null;
  onTurnDone: () => void;
  /** A finger grabbed the sheet: show the next day underneath. */
  onPeekStart: () => void;
  /** The finger let go: torn off (go to the next day) or put back. */
  onPeekEnd: (torn: boolean) => void;
}

/** Overlay margins around the sheet, as fractions of its size: room for the flap to leave the sheet. */
const MARGIN_X = 0.6;
const MARGIN_Y = 0.45;
const TURN_MS = 650;
const TILT = (25 * Math.PI) / 180;

type Mode =
  | { kind: 'idle' }
  | { kind: 'drag'; grab: Grab; x0: number; y0: number; dx: number; dy: number; lift: number; t0: number; samples: { t: number; x: number; y: number }[] }
  | { kind: 'tear'; grab: Grab; dx: number; dy: number; ux: number; uy: number; speed: number; fade: number }
  | { kind: 'back'; grab: Grab; dx: number; dy: number; lift: number; t0: number; from: { dx: number; dy: number; lift: number } }
  | { kind: 'turn'; forward: boolean; t0: number };

/**
 * The tear-off sheet's page curl, in WebGL (lib/curlGL), over the live sheet. A finger near the
 * bottom curls the bottom edge at once (a bottom corner when near a side); the paper follows the
 * finger; let go after a flick or past half the sheet and it tears off and flies on in the
 * flick's direction, else it settles back. The sheet turns from its bottom only (user,
 * 2026-11-23): the rest is left to the page (scrolling) and its buttons, and a tap on the bottom
 * zone still reaches a button under it. The arrows (and day changes elsewhere) play a
 * turn from the bottom-right corner. Without WebGL, or with reduced motion, days just change.
 */
export function SheetPeel({ paperRef, sourceRef, faceKey, turn, onTurnDone, onPeekStart, onPeekEnd }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const renderer = useRef<CurlRenderer | null | undefined>(undefined);
  const painted = useRef<string | null>(null);
  const mode = useRef<Mode>({ kind: 'idle' });
  const raf = useRef(0);
  const geo = useRef({ w: 1, h: 1, mx: 0, my: 0, r: 20 });
  const cb = useRef({ onTurnDone, onPeekStart, onPeekEnd });
  cb.current = { onTurnDone, onPeekStart, onPeekEnd };

  const reduced = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const getRenderer = useCallback(() => {
    if (renderer.current === undefined && canvasRef.current) renderer.current = CurlRenderer.create(canvasRef.current);
    return renderer.current ?? null;
  }, []);

  /**
   * Measures the sheet, sizes the overlay, paints the source sheet and the rings into textures:
   * 'auto' when what they show has changed, 'force' always, 'keep' never (a forward turn uses the
   * texture of the sheet that was on show; the source copy already shows the new day).
   */
  const prepare = useCallback((paint: 'auto' | 'force' | 'keep' = 'auto'): boolean => {
    const gl = getRenderer();
    const paper = paperRef.current, source = sourceRef.current?.firstElementChild as HTMLElement | null;
    const canvas = canvasRef.current;
    if (!gl || !paper || !source || !canvas) return false;
    const pr = paper.getBoundingClientRect();
    const w = pr.width, h = source.getBoundingClientRect().height;
    const mx = Math.round(w * MARGIN_X), my = Math.round(h * MARGIN_Y);
    geo.current = { w, h, mx, my, r: radiusFor(w, h) };
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.style.left = `${-mx}px`;
    canvas.style.top = `${-my}px`;
    canvas.style.width = `${w + 2 * mx}px`;
    canvas.style.height = `${h + 2 * my}px`;
    gl.resize(w + 2 * mx, h + 2 * my, dpr);
    gl.sheet = { x: mx, y: my, w, h };
    if (paint === 'keep' && painted.current) return true;
    if (paint === 'auto' && painted.current === `${faceKey}|${w}x${h}|${paintFontsLoaded()}`) return true;

    const sr = source.getBoundingClientRect();
    const sheet = document.createElement('canvas');
    sheet.width = Math.round(sr.width * dpr);
    sheet.height = Math.round(sr.height * dpr);
    const ctx = sheet.getContext('2d')!;
    ctx.scale(dpr, dpr);
    paintElement(ctx, source, sr);
    gl.setSheet(sheet);

    // The rings hang over the paper's top: drawn between the flat paper and the curl.
    const rings = Array.from(paper.querySelectorAll<HTMLElement>(':scope > .tear-ring'));
    if (rings.length) {
      const rs = rings.map(r => r.getBoundingClientRect());
      const pad = 6;
      const left = Math.min(...rs.map(r => r.left)) - pad, top = Math.min(...rs.map(r => r.top)) - pad;
      const right = Math.max(...rs.map(r => r.right)) + pad, bottom = Math.max(...rs.map(r => r.bottom)) + pad;
      const rc = document.createElement('canvas');
      rc.width = Math.round((right - left) * dpr);
      rc.height = Math.round((bottom - top) * dpr);
      const rctx = rc.getContext('2d')!;
      rctx.scale(dpr, dpr);
      rings.forEach(r => paintElement(rctx, r, { left, top }));
      gl.setRings(rc);
      gl.rings = { x: left - pr.left + mx, y: top - pr.top + my, w: right - left, h: bottom - top };
    }

    // The back of the paper: cream warmed by the palette's gold (#fbf7ef + 18 % at the top left, #efe6d6 + 32 % at the bottom right).
    const accent = getComputedStyle(document.documentElement).getPropertyValue('--hero-accent').trim();
    const rgb = hexRgb(accent) ?? [0.95, 0.83, 0.55];
    const mix = (base: [number, number, number], k: number) => base.map((b, i) => b * (1 - k) + rgb[i] * k) as [number, number, number];
    gl.backTop = mix([0xfb / 255, 0xf7 / 255, 0xef / 255], 0.18);
    gl.backBottom = mix([0xef / 255, 0xe6 / 255, 0xd6 / 255], 0.32);
    painted.current = `${faceKey}|${w}x${h}|${paintFontsLoaded()}`;
    return true;
  }, [faceKey, getRenderer, paperRef, sourceRef]);

  const prepareRef = useRef(prepare);
  prepareRef.current = prepare;

  // Repaint the current sheet's texture while idle, so a touch curls at once.
  useEffect(() => {
    if (reduced) return;
    const ric = window.requestIdleCallback ?? ((f: () => void) => window.setTimeout(f, 120));
    const cancel = window.cancelIdleCallback ?? window.clearTimeout;
    let id: number | undefined, live = true;
    // The painter's own fonts first (lining digits), then paint.
    void paintFontsReady().then(() => {
      if (live) id = ric(() => { if (mode.current.kind === 'idle') prepare(); }, { timeout: 1200 });
    });
    return () => { live = false; if (id !== undefined) cancel(id); };
  }, [faceKey, prepare, reduced]);

  const show = (on: boolean) => { if (canvasRef.current) canvasRef.current.style.display = on ? 'block' : 'none'; };

  /** One frame of whatever is running. */
  const frame = useCallback((now: number) => {
    const gl = renderer.current;
    const m = mode.current;
    const { w, h, mx, my, r } = geo.current;
    if (!gl || m.kind === 'idle') return;
    let fold: { fx: number; fy: number; nx: number; ny: number }, opacity = 1, next = true;

    if (m.kind === 'drag') {
      m.lift = Math.min(1, m.lift + 1 / 7); // ~110ms to curl a fresh touch
      fold = foldFor(m.grab, m.dx, m.dy, r, easeOut(m.lift));
    } else if (m.kind === 'back') {
      const t = Math.min(1, (now - m.t0) / 240), k = easeOut(t);
      m.dx = m.from.dx * (1 - k); m.dy = m.from.dy * (1 - k); m.lift = m.from.lift * (1 - k);
      fold = foldFor(m.grab, m.dx, m.dy, r, m.lift);
      if (t >= 1) next = false;
    } else if (m.kind === 'tear') {
      m.speed *= 1.06; // accelerate away
      m.dx += m.ux * m.speed * 16; m.dy += m.uy * m.speed * 16;
      const f = foldFor(m.grab, m.dx, m.dy, r, 1);
      fold = f;
      // The whole sheet has turned once G has travelled twice the span plus half the cylinder.
      if (f.reach > 2 * span(m.grab, f.nx, f.ny, w, h) + Math.PI * r) m.fade = Math.min(1, m.fade + 0.12);
      opacity = 1 - m.fade;
      if (m.fade >= 1) next = false;
    } else {
      // Development only: window.__tearFreeze = 0..1 holds the turn at that point (screenshots).
      const freeze = import.meta.env.DEV ? (window as unknown as { __tearFreeze?: number }).__tearFreeze : undefined;
      const p0 = typeof freeze === 'number' ? Math.min(0.999, freeze) : Math.min(1, (now - m.t0) / TURN_MS);
      const p = m.forward ? p0 : 1 - p0;
      const grab: Grab = { gx: w, gy: h, ex: Math.SQRT1_2, ey: Math.SQRT1_2 };
      const ux = -Math.sin(TILT), uy = -Math.cos(TILT);
      // Far enough that the sheet has turned completely (2·span + πr) by 86 %, when it fades.
      const total = 1.35 * (2 * span(grab, -ux, -uy, w, h) + Math.PI * r);
      const travel = (0.3 * p + 0.7 * p ** 2.2) * total;
      fold = foldFor(grab, ux * travel, uy * travel, r, Math.min(1, p / 0.08));
      opacity = p > 0.86 ? Math.max(0, 1 - (p - 0.86) / 0.14) : 1;
      if (p0 >= 1) next = false;
    }

    gl.draw({ fx: fold.fx + mx, fy: fold.fy + my, nx: fold.nx, ny: fold.ny, r, opacity });
    if (next) raf.current = requestAnimationFrame(frame);
    else finish();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const finish = () => {
    const m = mode.current;
    mode.current = { kind: 'idle' };
    // Keep the last frame until React has swapped the sheets underneath, then hide.
    requestAnimationFrame(() => requestAnimationFrame(() => { if (mode.current.kind === 'idle') { show(false); renderer.current?.clear(); } }));
    // The day may have changed meanwhile: repaint the current sheet when the browser is idle.
    const ric = window.requestIdleCallback ?? ((f: () => void) => window.setTimeout(f, 200));
    ric(() => { if (mode.current.kind === 'idle') prepareRef.current(); }, { timeout: 1500 });
    if (m.kind === 'tear') cb.current.onPeekEnd(true);
    else if (m.kind === 'back') cb.current.onPeekEnd(false);
    else if (m.kind === 'turn') cb.current.onTurnDone();
  };

  const start = (m: Mode) => {
    cancelAnimationFrame(raf.current);
    mode.current = m;
    show(true);
    raf.current = requestAnimationFrame(frame);
  };

  // Turns from the arrows / day changes: forward uses the texture of the sheet that was on show
  // (painted before the day changed); back paints the new day (the source copy shows it now).
  useLayoutEffect(() => {
    if (!turn) return;
    if (reduced || !getRenderer()) { onTurnDone(); return; }
    if (mode.current.kind === 'drag' || mode.current.kind === 'tear' || mode.current.kind === 'back') { onTurnDone(); return; }
    if (!prepare(turn.forward ? 'keep' : 'force')) { onTurnDone(); return; }
    start({ kind: 'turn', forward: turn.forward, t0: performance.now() });
    return () => cancelAnimationFrame(raf.current);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [turn]);

  useEffect(() => () => cancelAnimationFrame(raf.current), []);

  // ---- gestures ----------------------------------------------------------

  const local = (e: { clientX: number; clientY: number }) => {
    const pr = paperRef.current!.getBoundingClientRect();
    return { x: e.clientX - pr.left, y: e.clientY - pr.top };
  };

  const pending = useRef<{ id: number; x: number; y: number; t: number; type: string; zone: boolean } | null>(null);

  const begin = (e: ReactPointerEvent, x: number): boolean => {
    if (mode.current.kind === 'turn' || mode.current.kind === 'tear') return false;
    if (!prepare()) return false;
    const { w, h } = geo.current;
    const zone = Math.max(44, Math.min(w, h) * 0.2);
    const grab = grabBottom(x, w, h, zone);
    cb.current.onPeekStart();
    start({ kind: 'drag', grab, x0: e.clientX, y0: e.clientY, dx: 0, dy: 0, lift: 0, t0: performance.now(), samples: [{ t: performance.now(), x: e.clientX, y: e.clientY }] });
    return true;
  };

  const onDown = (e: ReactPointerEvent<HTMLDivElement>, zone: boolean) => {
    if (reduced || !getRenderer() || e.button > 0) return;
    const { x } = local(e);
    pending.current = { id: e.pointerId, x: e.clientX, y: e.clientY, t: performance.now(), type: e.pointerType, zone };
    // On an edge, a touch curls the paper at once; the mouse waits for a drag (clicks stay clicks).
    if (zone && e.pointerType !== 'mouse') begin(e, x);
    try { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId); } catch { /* pointer already gone */ }
  };

  const onMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const p = pending.current;
    if (!p || p.id !== e.pointerId) return;
    const m = mode.current;
    if (m.kind !== 'drag') {
      if (Math.hypot(e.clientX - p.x, e.clientY - p.y) < 6) return;
      const { x } = local({ clientX: p.x, clientY: p.y });
      if (!begin(e, x)) return;
      const d = mode.current as Extract<Mode, { kind: 'drag' }>;
      d.x0 = p.x; d.y0 = p.y;
    }
    const d = mode.current as Extract<Mode, { kind: 'drag' }>;
    d.dx = e.clientX - d.x0;
    d.dy = e.clientY - d.y0;
    // The finger's recent path (last 120 ms): the flick's speed and direction at release.
    const t = performance.now();
    d.samples.push({ t, x: e.clientX, y: e.clientY });
    while (d.samples.length > 2 && t - d.samples[0].t > 120) d.samples.shift();
  };

  const onUp = (e: ReactPointerEvent<HTMLDivElement>, cancelled = false) => {
    const p = pending.current;
    if (!p || p.id !== e.pointerId) return;
    pending.current = null;
    const m = mode.current;
    const moved = Math.hypot(e.clientX - p.x, e.clientY - p.y);
    if (m.kind !== 'drag') {
      // A tap: hand it to the button under the edge zone (the arrows).
      if (!cancelled && p.zone && moved < 8) tapThrough(e.clientX, e.clientY);
      return;
    }
    const { w, h, r } = geo.current;
    const f = foldFor(m.grab, m.dx, m.dy, r, 1);
    // Velocity over the last 120 ms; a finger that stopped before letting go has none.
    const now = performance.now();
    const s = m.samples.filter(q => now - q.t <= 120);
    const a = s[0] ?? { t: now, x: e.clientX, y: e.clientY }, b = { t: now, x: e.clientX, y: e.clientY };
    const dt = Math.max(16, b.t - a.t);
    const vx = (b.x - a.x) / dt, vy = (b.y - a.y) / dt;
    // Pulled away from the grab (along -n) fast enough, or past half the sheet: tear it off.
    const along = -(vx * f.nx + vy * f.ny);
    // Half the sheet has turned when G has travelled about one span (the fold then crosses the middle).
    const reachShare = f.reach / Math.max(1, span(m.grab, f.nx, f.ny, w, h));
    const tear = !cancelled && moved > 12 && (along > 0.45 || reachShare > 0.75);
    if (tear) {
      const speed = Math.max(0.9, Math.hypot(vx, vy));
      const len = Math.hypot(vx, vy);
      const ux = len > 0.3 ? vx / len : -f.nx, uy = len > 0.3 ? vy / len : -f.ny;
      mode.current = { kind: 'tear', grab: m.grab, dx: m.dx, dy: m.dy, ux, uy, speed, fade: 0 };
    } else if (!cancelled && p.zone && moved < 8 && performance.now() - p.t < 350) {
      // A tap that had started to curl the edge: drop the curl at once and press the button
      // under it (its own turn then plays).
      cancelAnimationFrame(raf.current);
      mode.current = { kind: 'idle' };
      show(false);
      renderer.current?.clear();
      cb.current.onPeekEnd(false);
      tapThrough(e.clientX, e.clientY);
    } else {
      mode.current = { kind: 'back', grab: m.grab, dx: m.dx, dy: m.dy, lift: m.lift, t0: performance.now(), from: { dx: m.dx, dy: m.dy, lift: m.lift } };
    }
  };

  const tapThrough = (x: number, y: number) => {
    const target = document.elementsFromPoint(x, y).find(el => !(el as HTMLElement).dataset?.peelZone && el.closest('button'));
    (target?.closest('button') as HTMLButtonElement | null)?.click();
  };

  const zoneProps = (cls: string) => ({
    'data-peel-zone': '1',
    className: `absolute z-20 ${cls}`,
    style: { touchAction: 'none' as const },
    onPointerDown: (e: ReactPointerEvent<HTMLDivElement>) => onDown(e, true),
    onPointerMove: onMove,
    onPointerUp: (e: ReactPointerEvent<HTMLDivElement>) => onUp(e),
    onPointerCancel: (e: ReactPointerEvent<HTMLDivElement>) => onUp(e, true),
  });

  return (
    <>
      <canvas ref={canvasRef} className="pointer-events-none absolute z-10" style={{ display: 'none' }} aria-hidden="true" />
      {!reduced && (
        <>
          {/* The bottom zone (30 %): touch here curls the bottom edge; the rest stays the page's
              (scrolling, and TearOffSheet's sideways swipe). */}
          <div {...zoneProps('inset-x-0 bottom-0 h-[30%]')} aria-hidden="true" />
        </>
      )}
    </>
  );
}

function hexRgb(v: string): [number, number, number] | null {
  const c = cssColor(v);
  const m = /^#?([0-9a-f]{6})$/i.exec(c);
  if (m) {
    const n = parseInt(m[1], 16);
    return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
  }
  const r = /rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)/.exec(c);
  return r ? [+r[1] / 255, +r[2] / 255, +r[3] / 255] : null;
}
