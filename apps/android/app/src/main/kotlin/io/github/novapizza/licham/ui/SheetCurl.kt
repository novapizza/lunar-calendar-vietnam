package io.github.novapizza.licham.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the tear-off sheet folds, from a finger: the web's lib/peel.ts. Px, origin at the
 * paper's top-left, y down.
 *
 * A touch near the bottom grabs the paper at the bottom edge (at a corner when near a side): the
 * grab point G on the edge and the edge's outward normal e. The finger's travel Δ moves G with it. The fold
 * is where paper turned over a cylinder of radius r puts G under the finger: with v = -Δ and the
 * fold normal n (towards the peeling side), G sits d0 = (v·n + πr·lift)/2 past the fold. n starts
 * as e (a fresh touch lifts the edge at once) and turns to v as the finger travels.
 */
internal object Peel {
    const val SQRT1_2 = 0.70710677f

    class Grab(val gx: Float, val gy: Float, val ex: Float, val ey: Float)

    class Fold(val fx: Float, val fy: Float, val nx: Float, val ny: Float, val reach: Float)

    /**
     * The grab for a touch at x on a w × h sheet, which turns from its bottom only (user,
     * 2026-11-23): a bottom corner when the touch is within [zone] px of a side, else the bottom
     * edge under the finger.
     */
    fun grabBottom(x: Float, w: Float, h: Float, zone: Float): Grab = when {
        x <= zone -> Grab(0f, h, -SQRT1_2, SQRT1_2)
        w - x <= zone -> Grab(w, h, SQRT1_2, SQRT1_2)
        else -> Grab(x, h, 0f, 1f)
    }

    /** The fold for a grab, the finger's travel (dx, dy), radius r and lift 0..1 (how far a fresh touch has curled the edge). */
    fun foldFor(g: Grab, dx: Float, dy: Float, r: Float, lift: Float): Fold {
        val vx = -dx; val vy = -dy
        val bx = vx + g.ex * PI.toFloat() * r; val by = vy + g.ey * PI.toFloat() * r
        val len = hypot(bx, by).takeIf { it > 0f } ?: 1f
        val nx = bx / len; val ny = by / len
        val reach = max(0f, vx * nx + vy * ny)
        val d0 = (reach + PI.toFloat() * r * lift) / 2
        return Fold(g.gx - nx * d0, g.gy - ny * d0, nx, ny, reach)
    }

    /** How far the paper reaches from the grab across the sheet along -n (the whole sheet has turned when the fold has passed it). */
    fun span(g: Grab, nx: Float, ny: Float, w: Float, h: Float): Float =
        maxOf((g.gx) * nx + (g.gy) * ny, (g.gx - w) * nx + g.gy * ny, g.gx * nx + (g.gy - h) * ny, (g.gx - w) * nx + (g.gy - h) * ny)

    /** Cylinder radius: a little under a tenth of the short side, 16–42dp. */
    fun radiusFor(w: Float, h: Float, density: Float): Float = min(42f * density, max(16f * density, min(w, h) * 0.085f))

    fun easeOut(t: Float): Float { val u = 1 - t.coerceIn(0f, 1f); return 1 - u * u * u }
}

/** One frame of the curl: a point on the fold line, the unit normal towards the peeling side, the radius, px; the turning sheet's opacity. */
internal class CurlState(val fx: Float, val fy: Float, val nx: Float, val ny: Float, val r: Float, val opacity: Float)

/**
 * Draws the turning sheet bent round a cylinder whose axis is the fold line, as the web's WebGL
 * curl (lib/curlGL.ts): a fine mesh over the sheet drawn with Canvas.drawVertices (hardware from
 * API 29), the cream back of the paper where it has turned over, lit per vertex.
 *
 * A sheet point p at d = (p - F)·n past the fold is flat for d ≤ 0, wraps round the cylinder for
 * 0 < d < πr and lies turned over, 2r above the sheet, beyond. Seen in a slight perspective from
 * above the sheet's top centre. Draw order: the shadow the lifted paper casts on the sheet
 * underneath, the flat part (a clipped bitmap, exact along the fold), the rings, the curled part
 * facing the viewer, then the back (it is the higher one wherever the two overlap).
 */
internal class CurlPainter {
    private var w = 0f
    private var h = 0f
    /** Transparent border round the bitmaps: the curled outline comes from filtered alpha, not a jagged triangle edge. */
    private var pad = 0f
    private var cols = 0
    private var rows = 0
    private var texs = FloatArray(0)
    private var verts = FloatArray(0)
    private var dist = FloatArray(0)
    private var colFront = IntArray(0)
    private var colBack = IntArray(0)
    private var idxFront = ShortArray(0)
    private var idxBack = ShortArray(0)
    private var sheet: Bitmap? = null
    private val sheetPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val flatPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val shade = Path()
    private var page = Path()

    /**
     * The sheet to turn: [sheet] and [back] are (w + 2·pad) × (h + 2·pad) px with the paper at
     * (pad, pad); [page] is the paper's outline (rounded bottom corners).
     */
    fun set(sheet: Bitmap, back: Bitmap, w: Float, h: Float, pad: Float, page: Path) {
        this.sheet = sheet
        this.page = page
        sheetPaint.shader = BitmapShader(sheet, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        // The flat part is the same bitmap filling a path: paper coordinates, so shifted by the pad.
        flatPaint.shader = BitmapShader(sheet, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-pad, -pad) })
        }
        backPaint.shader = BitmapShader(back, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (w == this.w && h == this.h && pad == this.pad) return
        this.w = w; this.h = h; this.pad = pad
        // About 16px cells on a phone at 3x: smooth light round a 16–42dp cylinder.
        cols = 64
        rows = max(8, (cols * h / w).toInt())
        val n = (cols + 1) * (rows + 1)
        texs = FloatArray(2 * n); verts = FloatArray(2 * n); dist = FloatArray(n)
        colFront = IntArray(n); colBack = IntArray(n)
        idxFront = ShortArray(6 * cols * rows); idxBack = ShortArray(6 * cols * rows)
        var k = 0
        for (j in 0..rows) for (i in 0..cols) {
            texs[2 * k] = (w + 2 * pad) * i / cols
            texs[2 * k + 1] = (h + 2 * pad) * j / rows
            k++
        }
    }

    /** Draws a frame on [canvas] (paper coordinates); [rings] draws the brass rings over the flat paper. */
    fun draw(canvas: Canvas, s: CurlState, perspective: Float, rings: (Canvas) -> Unit) {
        if (sheet == null) return
        if (s.opacity <= 0f) return
        val saved = if (s.opacity < 1f) canvas.saveLayerAlpha(null, (s.opacity * 255).toInt()) else canvas.save()
        val r = s.r
        // Shadow on the sheet underneath, past the fold: darkest at the fold, gone 7 radii on (smoothstep).
        val stops = 9
        val colors = IntArray(stops) { i -> val t = i / (stops - 1f); alpha(0.38f * (1 - t * t * (3 - 2 * t))) }
        val positions = FloatArray(stops) { it / (stops - 1f) }
        shadowPaint.shader = LinearGradient(s.fx, s.fy, s.fx + s.nx * 7 * r, s.fy + s.ny * 7 * r, colors, positions, Shader.TileMode.CLAMP)
        // Filled paths, not clips: a clip path per frame cost the GPU more (emulator, 2026-11-23).
        halfPlane(s, -pad, -pad, w + pad, h + pad, flat = false)
        shade.set(clip)
        shade.op(page, Path.Op.INTERSECT)
        canvas.drawPath(shade, shadowPaint)

        // The flat part: the sheet cut along the fold.
        halfPlane(s, -pad, -pad, w + pad, h + pad, flat = true)
        canvas.drawPath(clip, flatPaint)

        rings(canvas)

        // The curl.
        val pi = PI.toFloat()
        val vx = w / 2
        val lx = -0.18f; val ly = -0.30f; val lz = 1f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        val Lx = lx / ll; val Ly = ly / ll; val Lz = lz / ll
        var k = 0
        for (j in 0..rows) for (i in 0..cols) {
            val x = texs[2 * k] - pad
            val y = texs[2 * k + 1] - pad
            val d = (x - s.fx) * s.nx + (y - s.fy) * s.ny
            var px = x; var py = y; var pz = 0f
            var mx = 0f; var my = 0f; var mz = 1f
            if (d > 0f) {
                val bx = x - s.nx * d; val by = y - s.ny * d
                if (d < pi * r) {
                    val th = d / r
                    val sn = sin(th); val cs = cos(th)
                    px = bx + s.nx * r * sn; py = by + s.ny * r * sn; pz = r * (1 - cs)
                    mx = -s.nx * sn; my = -s.ny * sn; mz = cs
                } else {
                    px = bx - s.nx * (d - pi * r); py = by - s.ny * (d - pi * r); pz = 2 * r
                    mx = 0f; my = 0f; mz = -1f
                }
            }
            val sc = perspective / (perspective - pz)
            verts[2 * k] = vx + (px - vx) * sc
            verts[2 * k + 1] = py * sc
            dist[k] = d
            val lit = (mx * Lx + my * Ly + mz * Lz) / Lz
            colFront[k] = gray(0.42f + 0.58f * lit.coerceIn(0f, 1f))
            colBack[k] = gray(0.42f + 0.58f * (-lit).coerceIn(0f, 1f))
            k++
        }
        var nf = 0; var nb = 0
        fun tri(a: Int, b: Int, c: Int) {
            if (dist[a] <= 0f && dist[b] <= 0f && dist[c] <= 0f) return
            // Grid triangles wind clockwise on screen (y down) when flat: still so = the front faces us.
            val area = (verts[2 * b] - verts[2 * a]) * (verts[2 * c + 1] - verts[2 * a + 1]) -
                (verts[2 * b + 1] - verts[2 * a + 1]) * (verts[2 * c] - verts[2 * a])
            if (area >= 0f) {
                idxFront[nf++] = a.toShort(); idxFront[nf++] = b.toShort(); idxFront[nf++] = c.toShort()
            } else {
                idxBack[nb++] = a.toShort(); idxBack[nb++] = b.toShort(); idxBack[nb++] = c.toShort()
            }
        }
        for (j in 0 until rows) for (i in 0 until cols) {
            val a = j * (cols + 1) + i
            tri(a, a + 1, a + cols + 2)
            tri(a, a + cols + 2, a + cols + 1)
        }
        if (nf > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, texs, 0, colFront, 0, idxFront, 0, nf, sheetPaint)
        if (nb > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, texs, 0, colBack, 0, idxBack, 0, nb, backPaint)
        canvas.restoreToCount(saved)
    }

    /** [clip] = the rectangle cut to the flat side of the fold (d ≤ 0), or with [flat] false the other side. */
    private fun halfPlane(s: CurlState, l: Float, t: Float, rr: Float, b: Float, flat: Boolean) {
        val sign = if (flat) 1f else -1f
        val pts = floatArrayOf(l, t, rr, t, rr, b, l, b)
        clip.rewind()
        var first = true
        for (i in 0 until 4) {
            val px = pts[2 * i]; val py = pts[2 * i + 1]
            val qx = pts[(2 * i + 2) % 8]; val qy = pts[(2 * i + 3) % 8]
            val dp = sign * ((px - s.fx) * s.nx + (py - s.fy) * s.ny)
            val dq = sign * ((qx - s.fx) * s.nx + (qy - s.fy) * s.ny)
            if (dp <= 0f) { if (first) clip.moveTo(px, py) else clip.lineTo(px, py); first = false }
            if ((dp <= 0f) != (dq <= 0f) && abs(dp - dq) > 1e-6f) {
                val u = dp / (dp - dq)
                val ix = px + (qx - px) * u; val iy = py + (qy - py) * u
                if (first) clip.moveTo(ix, iy) else clip.lineTo(ix, iy)
                first = false
            }
        }
        clip.close()
    }

    private fun gray(v: Float): Int { val c = (v.coerceIn(0f, 1f) * 255).toInt(); return (0xFF shl 24) or (c shl 16) or (c shl 8) or c }

    private fun alpha(a: Float): Int = ((a.coerceIn(0f, 1f) * 255).toInt()) shl 24
}
