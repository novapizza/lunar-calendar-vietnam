/**
 * WebGL page curl for the tear-off sheet: a fine mesh over the sheet bent round a cylinder whose
 * axis is the fold line, lit per pixel, the cream back of the paper where it has turned over.
 * Any fold direction, so the curl can follow a finger from any edge. Replaces the 48 CSS strips,
 * whose flat per-strip shading and seams read as creases (2026-11-22).
 *
 * Geometry, in the overlay's CSS px (x right, y down, z towards the viewer): a sheet point p at
 * distance d = (p - F)·n past the fold line (through F, unit normal n pointing to the peeling
 * side) is flat for d ≤ 0, wraps round the cylinder of radius r for 0 < d < πr, and lies turned
 * over, 2r above the sheet, beyond that. Seen in a slight perspective from above.
 *
 * Draw order: the shadow the lifted paper casts on the sheet underneath, the flat part, the brass
 * rings (they hang over the paper), then the curled part (with depth, it can overlap itself).
 */

export interface CurlState {
  /** A point on the fold line and the unit normal towards the peeling side, overlay px. */
  fx: number; fy: number; nx: number; ny: number;
  /** Cylinder radius, px. */
  r: number;
  /** Whole turning sheet's opacity. */
  opacity: number;
}

const VS = `
attribute vec2 aUV;
uniform vec4 uSheet;      // x, y, w, h (px)
uniform vec2 uRes;        // overlay size (px)
uniform vec2 uF;
uniform vec2 uN;
uniform float uR;
uniform float uPersp;
uniform vec2 uVanish;
uniform float uZScale;
varying vec2 vUV;
varying float vD;
varying vec3 vNormal;
void main() {
  vec2 p = uSheet.xy + aUV * uSheet.zw;
  float d = dot(p - uF, uN);
  vec3 pos = vec3(p, 0.0);
  vec3 nrm = vec3(0.0, 0.0, 1.0);
  if (d > 0.0) {
    float PI = 3.14159265;
    vec2 base = p - uN * d;
    if (d < PI * uR) {
      float th = d / uR;
      pos = vec3(base + uN * (uR * sin(th)), uR * (1.0 - cos(th)));
      nrm = vec3(-uN * sin(th), cos(th));
    } else {
      pos = vec3(base - uN * (d - PI * uR), 2.0 * uR);
      nrm = vec3(0.0, 0.0, -1.0);
    }
  }
  float s = uPersp / (uPersp - pos.z);
  vec2 q = uVanish + (pos.xy - uVanish) * s;
  vec2 clip = q / uRes * 2.0 - 1.0;
  gl_Position = vec4(clip.x, -clip.y, -pos.z * uZScale, 1.0);
  vUV = aUV;
  vD = d;
  vNormal = nrm;
}`;

const FS = `
precision mediump float;
uniform sampler2D uTex;
uniform vec3 uBackTop;
uniform vec3 uBackBottom;
uniform float uOpacity;
uniform float uPass;      // 0 flat part, 1 curled part
uniform float uFrontSign; // +1 when the screen's front faces are the paper's front
varying vec2 vUV;
varying float vD;
varying vec3 vNormal;
void main() {
  if (uPass < 0.5 && vD > 0.0) discard;
  if (uPass > 0.5 && vD <= 0.0) discard;
  vec4 tex = texture2D(uTex, vUV);
  vec3 nrm = normalize(vNormal);
  bool front = (gl_FrontFacing ? 1.0 : -1.0) * uFrontSign > 0.0;
  vec3 col;
  if (front) {
    col = tex.a > 0.0 ? tex.rgb / tex.a : vec3(0.0);
  } else {
    // Cream back, darker towards the bottom right (a 160deg gradient, colours from SheetPeel).
    float t = clamp(dot(vUV - vec2(0.0), vec2(0.34, 0.94)), 0.0, 1.0);
    col = mix(uBackTop, uBackBottom, t);
    nrm = -nrm;
  }
  // Light from the front, a little from the top left; the flat sheet keeps its own colours.
  vec3 L = normalize(vec3(-0.18, -0.30, 1.0));
  float lit = max(dot(nrm, L), 0.0) / L.z;
  float shade = mix(0.42, 1.0, clamp(lit, 0.0, 1.0));
  float gloss = pow(max(lit - 0.92, 0.0) * 10.0, 2.0) * 0.12;
  col = col * shade + gloss;
  float a = tex.a * uOpacity;
  gl_FragColor = vec4(col * a, a);
}`;

/** The shadow on the sheet underneath, past the fold: darkest at the fold, gone a few radii on. */
const FS_SHADOW = `
precision mediump float;
uniform float uOpacity;
uniform float uR;
varying float vD;
varying vec2 vUV;
uniform sampler2D uTex;
void main() {
  if (vD <= 0.0) discard;
  float a = 0.38 * (1.0 - smoothstep(0.0, uR * 7.0, vD)) * uOpacity * texture2D(uTex, vUV).a;
  gl_FragColor = vec4(0.0, 0.0, 0.0, a);
}`;

const VS_FLAT = `
attribute vec2 aUV;
uniform vec4 uRect;
uniform vec2 uRes;
varying vec2 vUV;
void main() {
  vec2 q = uRect.xy + aUV * uRect.zw;
  vec2 clip = q / uRes * 2.0 - 1.0;
  gl_Position = vec4(clip.x, -clip.y, 0.0, 1.0);
  vUV = aUV;
}`;
const FS_FLAT = `
precision mediump float;
uniform sampler2D uTex;
uniform float uOpacity;
varying vec2 vUV;
void main() { gl_FragColor = texture2D(uTex, vUV) * uOpacity; }`;

/** The shadow pass reuses the curl's vertex shader with the sheet left flat (r = 0 path not taken: d is all it needs). */
const VS_SHADOW = `
attribute vec2 aUV;
uniform vec4 uSheet;
uniform vec2 uRes;
uniform vec2 uF;
uniform vec2 uN;
varying float vD;
varying vec2 vUV;
void main() {
  vec2 p = uSheet.xy + aUV * uSheet.zw;
  vD = dot(p - uF, uN);
  vUV = aUV;
  vec2 clip = p / uRes * 2.0 - 1.0;
  gl_Position = vec4(clip.x, -clip.y, 0.0, 1.0);
}`;

function compile(gl: WebGLRenderingContext, vs: string, fs: string): WebGLProgram {
  const sh = (type: number, src: string) => {
    const s = gl.createShader(type)!;
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s) ?? 'shader');
    return s;
  };
  const p = gl.createProgram()!;
  gl.attachShader(p, sh(gl.VERTEX_SHADER, vs));
  gl.attachShader(p, sh(gl.FRAGMENT_SHADER, fs));
  gl.linkProgram(p);
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p) ?? 'link');
  return p;
}

const COLS = 64;
const ROWS = 96;

export class CurlRenderer {
  private gl: WebGLRenderingContext;
  private curl: WebGLProgram;
  private shadow: WebGLProgram;
  private flat: WebGLProgram;
  private mesh: WebGLBuffer;
  private quad: WebGLBuffer;
  private index: WebGLBuffer;
  private count: number;
  private sheetTex: WebGLTexture;
  private ringsTex: WebGLTexture;
  private dpr = 1;
  private w = 0;
  private h = 0;
  /** Sheet rect in the overlay (CSS px) and the rings' rect. */
  sheet = { x: 0, y: 0, w: 1, h: 1 };
  rings: { x: number; y: number; w: number; h: number } | null = null;
  backTop: [number, number, number] = [0.98, 0.96, 0.92];
  backBottom: [number, number, number] = [0.92, 0.87, 0.78];

  /** Null when WebGL is unavailable. */
  static create(canvas: HTMLCanvasElement): CurlRenderer | null {
    const gl = canvas.getContext('webgl', { premultipliedAlpha: true, alpha: true, antialias: true, depth: true });
    if (!gl) return null;
    try { return new CurlRenderer(gl); } catch { return null; }
  }

  private constructor(gl: WebGLRenderingContext) {
    this.gl = gl;
    this.curl = compile(gl, VS, FS);
    this.shadow = compile(gl, VS_SHADOW, FS_SHADOW);
    this.flat = compile(gl, VS_FLAT, FS_FLAT);
    const uv: number[] = [];
    for (let j = 0; j <= ROWS; j++) for (let i = 0; i <= COLS; i++) uv.push(i / COLS, j / ROWS);
    const idx: number[] = [];
    for (let j = 0; j < ROWS; j++) for (let i = 0; i < COLS; i++) {
      const a = j * (COLS + 1) + i, b = a + 1, c = a + COLS + 1, d = c + 1;
      idx.push(a, c, b, b, c, d);
    }
    this.count = idx.length;
    this.mesh = gl.createBuffer()!;
    gl.bindBuffer(gl.ARRAY_BUFFER, this.mesh);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(uv), gl.STATIC_DRAW);
    this.index = gl.createBuffer()!;
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.index);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(idx), gl.STATIC_DRAW);
    this.quad = gl.createBuffer()!;
    gl.bindBuffer(gl.ARRAY_BUFFER, this.quad);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([0, 0, 1, 0, 0, 1, 1, 1]), gl.STATIC_DRAW);
    this.sheetTex = this.texture();
    this.ringsTex = this.texture();
  }

  private texture(): WebGLTexture {
    const gl = this.gl;
    const t = gl.createTexture()!;
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return t;
  }

  private upload(t: WebGLTexture, src: HTMLCanvasElement) {
    const gl = this.gl;
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
  }

  setSheet(src: HTMLCanvasElement) { this.upload(this.sheetTex, src); }
  setRings(src: HTMLCanvasElement) { this.upload(this.ringsTex, src); }

  /** Overlay size in CSS px and the device pixel ratio used for the canvas. */
  resize(w: number, h: number, dpr: number) {
    this.w = w; this.h = h; this.dpr = dpr;
    const c = this.gl.canvas as HTMLCanvasElement;
    const pw = Math.round(w * dpr), ph = Math.round(h * dpr);
    if (c.width !== pw || c.height !== ph) { c.width = pw; c.height = ph; }
  }

  clear() {
    const gl = this.gl;
    gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  }

  draw(st: CurlState) {
    const gl = this.gl;
    this.clear();
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    const { x, y, w, h } = this.sheet;

    const bindMesh = (prog: WebGLProgram) => {
      gl.useProgram(prog);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.mesh);
      const loc = gl.getAttribLocation(prog, 'aUV');
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.index);
    };
    const u = (prog: WebGLProgram, name: string) => gl.getUniformLocation(prog, name);

    // 1. Shadow on the sheet underneath.
    gl.disable(gl.DEPTH_TEST);
    bindMesh(this.shadow);
    gl.uniform4f(u(this.shadow, 'uSheet'), x, y, w, h);
    gl.uniform2f(u(this.shadow, 'uRes'), this.w, this.h);
    gl.uniform2f(u(this.shadow, 'uF'), st.fx, st.fy);
    gl.uniform2f(u(this.shadow, 'uN'), st.nx, st.ny);
    gl.uniform1f(u(this.shadow, 'uR'), st.r);
    gl.uniform1f(u(this.shadow, 'uOpacity'), st.opacity);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.sheetTex);
    gl.uniform1i(u(this.shadow, 'uTex'), 0);
    gl.drawElements(gl.TRIANGLES, this.count, gl.UNSIGNED_SHORT, 0);

    // 2. Flat part, 3. rings, 4. curled part.
    const curlPass = (pass: number) => {
      bindMesh(this.curl);
      gl.uniform4f(u(this.curl, 'uSheet'), x, y, w, h);
      gl.uniform2f(u(this.curl, 'uRes'), this.w, this.h);
      gl.uniform2f(u(this.curl, 'uF'), st.fx, st.fy);
      gl.uniform2f(u(this.curl, 'uN'), st.nx, st.ny);
      gl.uniform1f(u(this.curl, 'uR'), Math.max(st.r, 0.5));
      gl.uniform1f(u(this.curl, 'uPersp'), 2400);
      gl.uniform2f(u(this.curl, 'uVanish'), x + w / 2, y);
      gl.uniform1f(u(this.curl, 'uZScale'), 1 / (4 * Math.max(st.r, 1) + 10));
      gl.uniform3f(u(this.curl, 'uBackTop'), ...this.backTop);
      gl.uniform3f(u(this.curl, 'uBackBottom'), ...this.backBottom);
      gl.uniform1f(u(this.curl, 'uOpacity'), st.opacity);
      gl.uniform1f(u(this.curl, 'uPass'), pass);
      gl.uniform1f(u(this.curl, 'uFrontSign'), 1);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, this.sheetTex);
      gl.uniform1i(u(this.curl, 'uTex'), 0);
      gl.drawElements(gl.TRIANGLES, this.count, gl.UNSIGNED_SHORT, 0);
    };
    curlPass(0);
    if (this.rings) {
      gl.useProgram(this.flat);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quad);
      const loc = gl.getAttribLocation(this.flat, 'aUV');
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
      gl.uniform4f(u(this.flat, 'uRect'), this.rings.x, this.rings.y, this.rings.w, this.rings.h);
      gl.uniform2f(u(this.flat, 'uRes'), this.w, this.h);
      gl.uniform1f(u(this.flat, 'uOpacity'), 1);
      gl.bindTexture(gl.TEXTURE_2D, this.ringsTex);
      gl.uniform1i(u(this.flat, 'uTex'), 0);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
    gl.enable(gl.DEPTH_TEST);
    gl.depthFunc(gl.LEQUAL);
    gl.clear(gl.DEPTH_BUFFER_BIT);
    curlPass(1);
    gl.disable(gl.DEPTH_TEST);
  }

  get pixelRatio() { return this.dpr; }
}
