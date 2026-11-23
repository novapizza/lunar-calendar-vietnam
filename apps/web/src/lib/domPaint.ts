/**
 * Paints an element's subtree onto a 2D canvas from what the browser has laid out: every box's
 * place and size (getBoundingClientRect) and computed style. It covers what TearOffSheet's sheet
 * uses, not CSS in general: background colours, linear and radial gradients, rounded corners,
 * overflow clips, borders, outer drop shadows and inset ring shadows, rotated boxes, text (font,
 * letter spacing, upper case, gradient "foil" print with its drop shadow) and inline SVG icons.
 * The page curl (curlGL) uses the result as the turning sheet's texture.
 */

/**
 * Canvas 2D has no font-variant-numeric: Playfair Display set in `lining-nums` (the sheet's day
 * numbers) came out in its default old-style figures, so the numbers changed shape the moment a
 * finger curled the sheet (2026-11-23). Such text is drawn with "Sheet Digits" first: Playfair's
 * lining figures as plain digits (public/fonts/sheet-digits.woff, scripts/sheet-digits.py).
 */
const DIGITS = 'Sheet Digits';
let digits: Promise<boolean> | null = null;
let digitsLoaded = false;

/** Loads the fonts the painter adds to the page's own; true once they can be used. */
export function paintFontsReady(): Promise<boolean> {
  if (!digits) {
    const face = new FontFace(DIGITS, `url(${import.meta.env.BASE_URL}fonts/sheet-digits.woff)`, { weight: '400 900' });
    document.fonts.add(face);
    digits = face.load().then(() => (digitsLoaded = true), () => false);
  }
  return digits;
}

/** Whether paintFontsReady has finished loading (a texture painted before it should be repainted). */
export const paintFontsLoaded = () => digitsLoaded;

/** A CSS colour as the canvas accepts it: rgb()/rgba() as is; color(srgb …), oklab(), oklch() converted. */
export function cssColor(c: string): string {
  const s = c.trim();
  let m = /^color\(srgb\s+([\d.e+-]+)\s+([\d.e+-]+)\s+([\d.e+-]+)(?:\s*\/\s*([\d.e+-]+%?))?\)$/i.exec(s);
  if (m) return rgba(+m[1] * 255, +m[2] * 255, +m[3] * 255, alpha(m[4]));
  m = /^oklab\(\s*([\d.e+-]+%?)\s+([\d.e+-]+)\s+([\d.e+-]+)(?:\s*\/\s*([\d.e+-]+%?))?\)$/i.exec(s);
  if (m) return fromOklab(pct(m[1]), +m[2], +m[3], alpha(m[4]));
  m = /^oklch\(\s*([\d.e+-]+%?)\s+([\d.e+-]+)\s+([\d.e+-]+)(?:deg)?(?:\s*\/\s*([\d.e+-]+%?))?\)$/i.exec(s);
  if (m) {
    const h = (+m[3] * Math.PI) / 180;
    return fromOklab(pct(m[1]), +m[2] * Math.cos(h), +m[2] * Math.sin(h), alpha(m[4]));
  }
  return s;
}

const pct = (v: string) => (v.endsWith('%') ? parseFloat(v) / 100 : parseFloat(v));
const alpha = (v: string | undefined) => (v === undefined ? 1 : pct(v));
const rgba = (r: number, g: number, b: number, a: number) =>
  `rgba(${Math.round(Math.min(255, Math.max(0, r)))}, ${Math.round(Math.min(255, Math.max(0, g)))}, ${Math.round(Math.min(255, Math.max(0, b)))}, ${a})`;

function fromOklab(L: number, a: number, b: number, al: number): string {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  const lin = [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
  const g = lin.map(x => (x <= 0.0031308 ? 12.92 * x : 1.055 * x ** (1 / 2.4) - 0.055) * 255);
  return rgba(g[0], g[1], g[2], al);
}

/** Splits at commas outside parentheses. */
function splitTop(s: string): string[] {
  const out: string[] = [];
  let depth = 0, start = 0;
  for (let i = 0; i < s.length; i++) {
    if (s[i] === '(') depth++;
    else if (s[i] === ')') depth--;
    else if (s[i] === ',' && depth === 0) { out.push(s.slice(start, i).trim()); start = i + 1; }
  }
  out.push(s.slice(start).trim());
  return out;
}

/** A colour stop "colour [pos]" → colour and position (0..1, or null when not given). */
function parseStop(stop: string, length: number): { color: string; at: number | null } {
  const m = /^(.*\))\s*([\d.-]+(?:%|px))?$/.exec(stop) ?? /^(\S+)\s*([\d.-]+(?:%|px))?$/.exec(stop);
  const color = cssColor(m ? m[1] : stop);
  const pos = m?.[2];
  const at = pos === undefined ? null : pos.endsWith('%') ? parseFloat(pos) / 100 : parseFloat(pos) / length;
  return { color, at };
}

function addStops(g: CanvasGradient, stops: string[], length: number) {
  const parsed = stops.map(s => parseStop(s, length));
  // Missing positions: first 0, last 1, the rest spread evenly between known ones.
  if (parsed[0].at === null) parsed[0].at = 0;
  if (parsed[parsed.length - 1].at === null) parsed[parsed.length - 1].at = 1;
  for (let i = 1; i < parsed.length; i++) {
    if (parsed[i].at !== null) continue;
    let j = i;
    while (parsed[j].at === null) j++;
    const a = parsed[i - 1].at!, b = parsed[j].at!;
    for (let k = i; k < j; k++) parsed[k].at = a + ((b - a) * (k - i + 1)) / (j - i + 1);
  }
  let last = 0;
  for (const p of parsed) {
    last = Math.max(last, Math.min(1, Math.max(0, p.at!)));
    try { g.addColorStop(last, p.color); } catch { /* unparsable colour: skip the stop */ }
  }
}

/** A CSS linear-gradient() over the box (x, y, w, h). */
function linearGradient(ctx: CanvasRenderingContext2D, args: string[], x: number, y: number, w: number, h: number): CanvasGradient {
  let angle = 180; // "to bottom"
  let stops = args;
  const first = args[0];
  if (/deg$/.test(first)) { angle = parseFloat(first); stops = args.slice(1); }
  else if (/^to /.test(first)) {
    const t = first.slice(3);
    const right = t.includes('right'), left = t.includes('left'), top = t.includes('top'), bottom = t.includes('bottom');
    if ((right || left) && (top || bottom)) {
      // Corner: the gradient line is perpendicular to the diagonal through the other two corners.
      const a = (Math.atan2(h, w) * 180) / Math.PI;
      angle = right && bottom ? 180 - a : right && top ? a : left && bottom ? 180 + a : 360 - a;
    } else angle = right ? 90 : left ? 270 : top ? 0 : 180;
    stops = args.slice(1);
  }
  const rad = (angle * Math.PI) / 180;
  const dx = Math.sin(rad), dy = -Math.cos(rad);
  const half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2;
  const cx = x + w / 2, cy = y + h / 2;
  const g = ctx.createLinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half);
  addStops(g, stops, half * 2);
  return g;
}

/** Fills the current path with a CSS radial-gradient() (ellipse, farthest-corner, "at x% y%"). */
function fillRadial(ctx: CanvasRenderingContext2D, args: string[], x: number, y: number, w: number, h: number) {
  let px = 0.5, py = 0.5;
  let stops = args;
  const m = /at\s+([\d.]+)%\s+([\d.]+)%/.exec(args[0]);
  if (m || /^(circle|ellipse|closest|farthest)/.test(args[0])) {
    if (m) { px = +m[1] / 100; py = +m[2] / 100; }
    stops = args.slice(1);
  }
  const cx = x + w * px, cy = y + h * py;
  // farthest-corner ellipse: farthest-side proportions, scaled to pass through the farthest corner.
  let rx = Math.max(cx - x, x + w - cx), ry = Math.max(cy - y, y + h - cy);
  const fx = Math.max(cx - x, x + w - cx), fy = Math.max(cy - y, y + h - cy);
  const k = Math.hypot(fx / rx, fy / ry);
  rx *= k; ry *= k;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(1, ry / rx);
  const g = ctx.createRadialGradient(0, 0, 0, 0, 0, rx);
  addStops(g, stops, rx);
  ctx.fillStyle = g;
  ctx.fillRect(-rx * 2, (-ry * 2 * rx) / ry, rx * 4, (ry * 4 * rx) / ry);
  ctx.restore();
}

function gradients(bg: string): { kind: string; args: string[] }[] {
  if (!bg || bg === 'none') return [];
  return splitTop(bg).map(layer => {
    const m = /^(linear|radial)-gradient\((.*)\)$/s.exec(layer.trim());
    return m ? { kind: m[1], args: splitTop(m[2]) } : { kind: 'other', args: [] };
  });
}

function radii(cs: CSSStyleDeclaration, w: number, h: number): [number, number, number, number] {
  const r = (v: string) => (v.endsWith('%') ? (parseFloat(v) / 100) * Math.min(w, h) : parseFloat(v) || 0);
  const max = Math.min(w, h) / 2;
  return [cs.borderTopLeftRadius, cs.borderTopRightRadius, cs.borderBottomRightRadius, cs.borderBottomLeftRadius]
    .map(v => Math.min(max, r(v))) as [number, number, number, number];
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, [tl, tr, br, bl]: number[]) {
  ctx.beginPath();
  ctx.moveTo(x + tl, y);
  ctx.lineTo(x + w - tr, y); ctx.arcTo(x + w, y, x + w, y + tr, tr);
  ctx.lineTo(x + w, y + h - br); ctx.arcTo(x + w, y + h, x + w - br, y + h, br);
  ctx.lineTo(x + bl, y + h); ctx.arcTo(x, y + h, x, y + h - bl, bl);
  ctx.lineTo(x, y + tl); ctx.arcTo(x, y, x + tl, y, tl);
  ctx.closePath();
}

/** Shadows "[inset] x y blur spread colour" (colour first or last). */
function shadows(v: string): { inset: boolean; x: number; y: number; blur: number; spread: number; color: string }[] {
  if (!v || v === 'none') return [];
  return splitTop(v).map(s => {
    const inset = /\binset\b/.test(s);
    const rest = s.replace(/\binset\b/, '').trim();
    const colorM = /(rgba?\([^)]*\)|color\([^)]*\)|oklab\([^)]*\)|oklch\([^)]*\)|#[0-9a-f]+|transparent)/i.exec(rest);
    const color = colorM ? cssColor(colorM[1]) : 'rgba(0,0,0,0.5)';
    const nums = rest.replace(colorM?.[1] ?? '', '').trim().split(/\s+/).map(parseFloat).filter(n => !Number.isNaN(n));
    return { inset, x: nums[0] ?? 0, y: nums[1] ?? 0, blur: nums[2] ?? 0, spread: nums[3] ?? 0, color };
  });
}

/**
 * The box's 2D turn and scale (translation dropped: the box is drawn about its laid-out centre),
 * from the individual `rotate` and `scale` properties and `transform`, in CSS order; null when
 * there is none.
 */
function boxMatrix(cs: CSSStyleDeclaration): DOMMatrix | null {
  const st = cs as CSSStyleDeclaration & { rotate?: string; scale?: string };
  const rotate = st.rotate && st.rotate !== 'none' ? st.rotate : null;
  const scale = st.scale && st.scale !== 'none' ? st.scale : null;
  const transform = cs.transform && cs.transform !== 'none' ? cs.transform : null;
  if (!rotate && !scale && !transform) return null;
  let m = new DOMMatrix();
  if (rotate) {
    // "45deg", or "z 45deg" / "0 0 1 45deg": only turns in the page's plane matter here.
    const angle = /(-?[\d.]+)(deg|rad|turn|grad)\s*$/.exec(rotate);
    if (angle) {
      const v = parseFloat(angle[1]);
      const deg = angle[2] === 'rad' ? (v * 180) / Math.PI : angle[2] === 'turn' ? v * 360 : angle[2] === 'grad' ? v * 0.9 : v;
      m = m.rotate(deg);
    }
  }
  if (scale) {
    const [sx, sy = sx] = scale.split(/\s+/).map(v => (v.endsWith('%') ? parseFloat(v) / 100 : parseFloat(v)));
    m = m.scale(sx, sy);
  }
  if (transform) m = m.multiply(new DOMMatrix(transform));
  return m;
}

/** Draws one line of text whose ink box is `r` (from a Range). */
function drawText(ctx: CanvasRenderingContext2D, text: string, r: { left: number; top: number; height: number }, cs: CSSStyleDeclaration, fill: string | CanvasGradient, dropShadow: string | null) {
  const lining = digitsLoaded && /lining-nums/.test(cs.fontVariantNumeric) && /playfair/i.test(cs.fontFamily);
  ctx.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${lining ? `"${DIGITS}", ` : ''}${cs.fontFamily}`;
  const spacing = parseFloat(cs.letterSpacing) || 0;
  const c = ctx as CanvasRenderingContext2D & { letterSpacing?: string; fontVariantNumeric?: string };
  const native = 'letterSpacing' in c;
  if (native) c.letterSpacing = `${spacing}px`;
  const m = ctx.measureText(text);
  const ascent = m.fontBoundingBoxAscent ?? parseFloat(cs.fontSize) * 0.9;
  const descent = m.fontBoundingBoxDescent ?? parseFloat(cs.fontSize) * 0.25;
  const base = r.top + (r.height - (ascent + descent)) / 2 + ascent;
  const draw = (dx: number, dy: number) => {
    if (native || !spacing) ctx.fillText(text, r.left + dx, base + dy);
    else {
      let x = r.left + dx;
      for (const ch of text) { ctx.fillText(ch, x, base + dy); x += ctx.measureText(ch).width + spacing; }
    }
  };
  ctx.textBaseline = 'alphabetic';
  ctx.textAlign = 'left';
  if (dropShadow) { ctx.fillStyle = dropShadow; draw(0, 2); }
  ctx.fillStyle = fill;
  draw(0, 0);
  if (native) c.letterSpacing = '0px';
}

/** The lines of a text node: [text, rect] per visual line (characters grouped by their top). */
function lines(node: Text): { text: string; rect: DOMRect }[] {
  const range = document.createRange();
  range.selectNodeContents(node);
  const rects = range.getClientRects();
  if (rects.length <= 1) return rects.length ? [{ text: node.data, rect: rects[0] }] : [];
  const out: { text: string; rect: DOMRect }[] = [];
  let cur = '', top = NaN, first: DOMRect | null = null;
  for (let i = 0; i < node.data.length; i++) {
    range.setStart(node, i); range.setEnd(node, i + 1);
    const r = range.getBoundingClientRect();
    if (r.width === 0 && node.data[i] === ' ' && cur === '') continue;
    if (!Number.isNaN(top) && Math.abs(r.top - top) > r.height / 2) {
      out.push({ text: cur.replace(/\s+$/, ''), rect: first! });
      cur = ''; first = null;
    }
    if (!first) { first = r; top = r.top; }
    cur += node.data[i];
  }
  if (cur.trim()) out.push({ text: cur.replace(/\s+$/, ''), rect: first! });
  return out;
}

function paintSvg(ctx: CanvasRenderingContext2D, svg: SVGSVGElement, x: number, y: number, w: number, h: number, color: string) {
  const vb = svg.viewBox?.baseVal;
  const vw = vb && vb.width ? vb.width : 24, vh = vb && vb.height ? vb.height : 24;
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(w / vw, h / vh);
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.lineWidth = parseFloat(svg.getAttribute('stroke-width') ?? '2');
  const fill = svg.getAttribute('fill');
  svg.querySelectorAll('path, polyline, line, circle, rect, polygon').forEach(el => {
    let p: Path2D | null = null;
    if (el instanceof SVGPathElement) p = new Path2D(el.getAttribute('d') ?? '');
    else if (el instanceof SVGPolylineElement || el instanceof SVGPolygonElement) {
      const pts = (el.getAttribute('points') ?? '').trim().split(/[\s,]+/).map(Number);
      p = new Path2D();
      for (let i = 0; i + 1 < pts.length; i += 2) (i ? p.lineTo : p.moveTo).call(p, pts[i], pts[i + 1]);
      if (el instanceof SVGPolygonElement) p.closePath();
    } else if (el instanceof SVGLineElement) {
      p = new Path2D();
      p.moveTo(+el.getAttribute('x1')!, +el.getAttribute('y1')!); p.lineTo(+el.getAttribute('x2')!, +el.getAttribute('y2')!);
    } else if (el instanceof SVGCircleElement) {
      p = new Path2D(); p.arc(+el.getAttribute('cx')!, +el.getAttribute('cy')!, +el.getAttribute('r')!, 0, Math.PI * 2);
    } else if (el instanceof SVGRectElement) {
      p = new Path2D(); p.rect(+el.getAttribute('x')!, +el.getAttribute('y')!, +el.getAttribute('width')!, +el.getAttribute('height')!);
    }
    if (!p) return;
    if (fill && fill !== 'none') ctx.fill(p);
    if (svg.getAttribute('stroke') !== 'none') ctx.stroke(p);
  });
  ctx.restore();
}

/**
 * Paints `root` and its subtree onto `ctx` (already scaled to CSS px), placed relative to
 * `origin` (a rect in client coordinates). `root`'s own opacity is ignored, so a hidden copy
 * (opacity 0) can be painted.
 */
export function paintElement(ctx: CanvasRenderingContext2D, root: Element, origin: { left: number; top: number }) {
  const walk = (el: Element, opacity: number, isRoot: boolean) => {
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || (cs.visibility === 'hidden' && !isRoot)) return;
    const op = isRoot ? opacity : opacity * parseFloat(cs.opacity || '1');
    if (op < 0.004) return;
    const br = el.getBoundingClientRect();
    if (br.width === 0 && br.height === 0 && el.childNodes.length === 0) return;

    // Rotated boxes (the ornament's diamond): draw the untransformed box, turned about its centre.
    // Tailwind v4 turns boxes with the `rotate` / `scale` properties, not `transform`.
    const html = el as HTMLElement;
    const matrix = boxMatrix(cs);
    const transformed = matrix !== null;
    const w = transformed ? html.offsetWidth : br.width;
    const h = transformed ? html.offsetHeight : br.height;
    const x = transformed ? br.left + br.width / 2 - origin.left - w / 2 : br.left - origin.left;
    const y = transformed ? br.top + br.height / 2 - origin.top - h / 2 : br.top - origin.top;

    ctx.save();
    ctx.globalAlpha = op;
    if (transformed) {
      const m = matrix;
      ctx.translate(x + w / 2, y + h / 2);
      ctx.transform(m.a, m.b, m.c, m.d, 0, 0);
      ctx.translate(-(x + w / 2), -(y + h / 2));
    }

    if (el instanceof SVGSVGElement) {
      paintSvg(ctx, el, x, y, w, h, cssColor(cs.color));
      ctx.restore();
      return;
    }

    const r = radii(cs, w, h);
    const shadowList = shadows(cs.boxShadow);
    const bgColor = cssColor(cs.backgroundColor);
    const layers = gradients(cs.backgroundImage);
    const clipText = cs.backgroundClip === 'text' || (cs as CSSStyleDeclaration & { webkitBackgroundClip?: string }).webkitBackgroundClip === 'text';

    // Outer drop shadow (the rings), drawn with the box's fill.
    const outer = shadowList.find(s => !s.inset);
    if (!clipText && (bgColor !== 'rgba(0, 0, 0, 0)' || layers.length)) {
      roundRect(ctx, x, y, w, h, r);
      if (outer) {
        ctx.save();
        ctx.shadowColor = outer.color; ctx.shadowOffsetX = outer.x; ctx.shadowOffsetY = outer.y; ctx.shadowBlur = outer.blur;
        ctx.fillStyle = bgColor !== 'rgba(0, 0, 0, 0)' ? bgColor : 'rgba(0,0,0,1)';
        ctx.fill();
        ctx.restore();
      }
      if (bgColor !== 'rgba(0, 0, 0, 0)') { ctx.fillStyle = bgColor; ctx.fill(); }
      for (let i = layers.length - 1; i >= 0; i--) {
        const layer = layers[i];
        if (layer.kind === 'linear') { ctx.fillStyle = linearGradient(ctx, layer.args, x, y, w, h); roundRect(ctx, x, y, w, h, r); ctx.fill(); }
        else if (layer.kind === 'radial') { ctx.save(); roundRect(ctx, x, y, w, h, r); ctx.clip(); fillRadial(ctx, layer.args, x, y, w, h); ctx.restore(); }
      }
    }
    // Inset ring shadows (the moon): bands just inside the padding edge.
    for (const s of shadowList.filter(s => s.inset && s.blur === 0 && s.x === 0 && s.y === 0 && s.spread > 0).reverse()) {
      ctx.save();
      roundRect(ctx, x, y, w, h, r); ctx.clip();
      ctx.lineWidth = s.spread * 2; ctx.strokeStyle = s.color;
      roundRect(ctx, x, y, w, h, r); ctx.stroke();
      ctx.restore();
    }
    // Borders (all sides alike here).
    const bw = parseFloat(cs.borderTopWidth) || 0;
    if (bw > 0 && cs.borderTopStyle !== 'none') {
      ctx.lineWidth = bw; ctx.strokeStyle = cssColor(cs.borderTopColor);
      roundRect(ctx, x + bw / 2, y + bw / 2, w - bw, h - bw, r.map(v => Math.max(0, v - bw / 2)));
      ctx.stroke();
    }

    // Clip children to rounded overflow boxes (the paper's light and moons).
    if (cs.overflow === 'hidden' || cs.overflowX === 'hidden' || cs.overflowX === 'clip') { roundRect(ctx, x, y, w, h, r); ctx.clip(); }

    // Own text.
    const upper = cs.textTransform === 'uppercase';
    const fill = clipText && layers[0]?.kind === 'linear' ? linearGradient(ctx, layers[0].args, x, y, w, h) : cssColor(cs.color);
    const drop = clipText && /drop-shadow/.test(cs.filter) ? 'rgba(0, 0, 0, 0.22)' : null;
    for (const node of Array.from(el.childNodes)) {
      if (node.nodeType === Node.TEXT_NODE && node.textContent && node.textContent.trim()) {
        ctx.globalAlpha = op;
        for (const line of lines(node as Text)) {
          const t = upper ? line.text.toLocaleUpperCase('vi') : line.text;
          drawText(ctx, t, { left: line.rect.left - origin.left, top: line.rect.top - origin.top, height: line.rect.height }, cs, fill, drop);
        }
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        ctx.restore();
        ctx.save();
        if (cs.overflow === 'hidden' || cs.overflowX === 'hidden' || cs.overflowX === 'clip') { roundRect(ctx, x, y, w, h, r); ctx.clip(); }
        walk(node as Element, op, false);
      }
    }
    ctx.restore();
  };
  walk(root, 1, true);
}
