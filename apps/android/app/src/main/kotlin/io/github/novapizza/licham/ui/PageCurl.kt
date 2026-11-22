package io.github.novapizza.licham.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Geometry of the tear-off sheet's page turn, a corner peel: the bottom-right corner curls up
 * (paper wrapping round a cylinder, the back of the sheet showing), the fold runs diagonally
 * across the sheet towards the top-left, and the folded sheet leaves past that corner.
 *
 * A port of apps/web/src/lib/pageCurl.ts (same constants, same steps): keep the two in step.
 * Coordinates: x right, y down, z towards the viewer, px, origin at the page's top-left.
 */
object PageCurl {
    const val CURL_BANDS = 24
    /** Pieces drawn: the flat piece, the curl bands, the folded piece. */
    const val PIECES = CURL_BANDS + 2
    const val TURN_MS = 800
    /** Perspective distance in dp (the web's CSS `perspective: 2400px`); vanishing point at the page's top centre. */
    const val PERSPECTIVE_DP = 2400f
    /** The peel runs 25° off vertical, towards the bottom-right corner. */
    const val TILT_DEG = 25f
    private const val TILT = TILT_DEG * PI.toFloat() / 180f
    /** How far the flap folds over, degrees (180 = flat on the sheet): real paper springs back. */
    private const val MAX_BEND = 155f
    /** Length of the curl (the part wrapped round the cylinder), as a fraction of the page diagonal. */
    private const val CURL = 0.22f

    /** Across the fold, towards the peeled corner. */
    val d = floatArrayOf(sin(TILT), cos(TILT))
    /** Along the fold (the turning axis); axis × d = +z. */
    val axis = floatArrayOf(cos(TILT), -sin(TILT))

    class Piece(
        /** Outline on the flat page, x0,y0,x1,y1,…; empty = not drawn. */
        val poly: FloatArray,
        val originX: Float, val originY: Float,
        val shiftX: Float, val shiftY: Float, val shiftZ: Float,
        /** Turn about the fold direction, degrees: 0 flat, 90 on edge, 180 folded flat over. */
        val angle: Float,
        /** Darkening of the visible side, 0..1. */
        val shade: Float,
    ) {
        val back: Boolean get() = cos(angle * PI.toFloat() / 180f) < 0f

        /** Where page point (x, y) of this piece ends up in 3D. */
        fun map(x: Float, y: Float, out: FloatArray) {
            val rx = x - originX; val ry = y - originY
            val al = rx * axis[0] + ry * axis[1]
            val sd = rx * d[0] + ry * d[1]
            val rad = angle * PI.toFloat() / 180f
            out[0] = originX + axis[0] * al + d[0] * sd * cos(rad) + shiftX
            out[1] = originY + axis[1] * al + d[1] * sd * cos(rad) + shiftY
            out[2] = sd * sin(rad) + shiftZ
        }
    }

    class Frame(
        val pieces: List<Piece>,
        val opacity: Float,
        /** Shadow on the sheet underneath: black at [shadowAlpha] on the fold line ([shadowFrom], px along d), clear by [shadowTo]. */
        val shadowFrom: Float, val shadowTo: Float, val shadowAlpha: Float,
    )

    private fun easeInOut(t: Float) = 0.5f - 0.5f * cos(PI.toFloat() * t.coerceIn(0f, 1f))

    /** Clips a polygon (x,y pairs) to the half-plane dot(p, n) >= c (Sutherland–Hodgman). */
    private fun clip(poly: FloatArray, nx: Float, ny: Float, c: Float): FloatArray {
        val out = ArrayList<Float>()
        val n = poly.size / 2
        for (i in 0 until n) {
            val px = poly[2 * i]; val py = poly[2 * i + 1]
            val qx = poly[(2 * i + 2) % poly.size]; val qy = poly[(2 * i + 3) % poly.size]
            val dp = px * nx + py * ny - c; val dq = qx * nx + qy * ny - c
            if (dp >= 0) { out += px; out += py }
            if ((dp >= 0) != (dq >= 0)) {
                val t = dp / (dp - dq)
                out += px + (qx - px) * t; out += py + (qy - py) * t
            }
        }
        return out.toFloatArray()
    }

    /**
     * The page at progress p (0 = flat, 1 = gone) for a page of [width] × [height] px. Slow at
     * first, then faster: the fold travels from inside the bottom-right corner to past the
     * top-left one on an ease-in curve, the curl closes to MAX_BEND by 45 %, and the sheet fades
     * over the last 12 %. [overlap] px is added on each side of a piece so no seam shows.
     */
    fun frame(progress: Float, width: Float, height: Float, overlap: Float): Frame {
        val p = progress.coerceIn(0f, 1f)
        val sMax = width * d[0] + height * d[1]

        val travel = 0.3f * p + 0.7f * p.pow(2.2f)
        val fold = 0.8f - 1.02f * travel
        val b = min(1f, p / 0.45f)
        val bend = MAX_BEND * (1 - (1 - b) * (1 - b))
        val opacity = if (p > 0.88f) 1 - (p - 0.88f) / 0.12f else 1f

        fun angleAt(s: Float) = bend * ((s / sMax - fold) / CURL).coerceIn(0f, 1f)
        val sFold = fold * sMax
        val sCurlEnd = (fold + CURL) * sMax

        // Once the fold has passed the top-left corner the sheet keeps turning about the fold
        // line: walk the paper between the fold and the corner first.
        var S = 0f; var Z = 0f
        if (sFold < 0) {
            S = sFold
            val step = CURL * sMax / CURL_BANDS
            var s = sFold
            while (s < 0) {
                val len = min(step, -s)
                val rad = angleAt(s + len / 2) * PI.toFloat() / 180f
                S += len * cos(rad); Z += len * sin(rad)
                s += step
            }
        }

        val a = sFold.coerceIn(0f, sMax); val c = sCurlEnd.coerceIn(0f, sMax)
        val edges = FloatArray(PIECES + 1)
        edges[0] = 0f; edges[1] = a
        for (i in 1..CURL_BANDS) edges[1 + i] = a + (c - a) * i / CURL_BANDS
        edges[PIECES] = sMax

        val rect = floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
        val pieces = ArrayList<Piece>(PIECES)
        for (k in 0 until PIECES) {
            val s0 = edges[k]; val s1 = edges[k + 1]
            val len = s1 - s0
            val angle = when (k) { 0 -> 0f; PIECES - 1 -> angleAt(sMax); else -> angleAt((s0 + s1) / 2) }
            val rad = angle * PI.toFloat() / 180f
            val poly = if (len > 0.01f) clip(clip(rect, d[0], d[1], s0 - overlap), -d[0], -d[1], -(s1 + overlap)) else FloatArray(0)
            val cs = cos(rad)
            pieces += Piece(
                poly, d[0] * s0, d[1] * s0, d[0] * (S - s0), d[1] * (S - s0), Z, angle,
                if (k == 0) 0f else min(0.6f, (1 - abs(cs)) * 0.55f),
            )
            S += len * cs; Z += len * sin(rad)
        }
        return Frame(pieces, opacity, sFold, sFold + 0.4f * sMax, 0.4f * min(1f, bend / 90f) * opacity)
    }

}
