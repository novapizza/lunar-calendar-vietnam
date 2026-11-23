package io.github.novapizza.licham.ui

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The tear-off sheet's page curl (CurlPainter) driven by a finger or by a day change, as the
 * web's components/SheetPeel.tsx. A finger near the bottom curls the bottom edge at once (a
 * bottom corner when near a side; the sheet turns from its bottom only); the paper follows the finger; let go after a flick or past three quarters of the
 * sheet and it tears off and flies on in the flick's direction (the next day), else it settles
 * back. The middle of the sheet is left to the page (scrolling), and a tap on an edge still
 * reaches the button under it. The arrows (and day changes elsewhere) play a turn from the
 * bottom-right corner.
 *
 * The curl draws bitmaps: the current day's sheet is snapshotted from its graphics layer while
 * idle, so a touch curls at once; a turn snapshots the turning sheet's layer.
 */
@Stable
internal class PeelController(
    private val scope: CoroutineScope,
    private val density: Float,
    /** The system's animation scale, read when each run starts: it can change while the app runs. */
    private val animScale: () -> Float,
) {
    private val painter = CurlPainter()
    private val pad = 2f

    /** The frame on show; null = the overlay draws nothing. */
    var curl by mutableStateOf<CurlState?>(null)
        private set

    /** A finger holds the sheet: the next day shows underneath. */
    var peeking by mutableStateOf(false)
        private set

    /** Tear-off finished: go to the next day without a turn. */
    var onTorn: () -> Unit = {}

    /** Back of the paper: cream warmed by the palette's gold (as the old PageTurn and the web). */
    var accent: Color = Color(0xFFE9CF8A)

    private var liveKey: Any? = null
    private var live: Bitmap? = null
    private var backKey: Any? = null
    private var back: Bitmap? = null
    private var page = Path()
    private var w = 0f
    private var h = 0f
    private var r = 0f
    private var turnPending = false
    private var onTurnDone: (() -> Unit)? = null
    private var job: Job? = null

    private class Sample(val t: Long, val x: Float, val y: Float)

    private sealed class Mode {
        data object Idle : Mode()
        class Drag(val grab: Peel.Grab, var dx: Float = 0f, var dy: Float = 0f, var lift: Float = 0f, val samples: ArrayDeque<Sample> = ArrayDeque()) : Mode()
        class Back(val grab: Peel.Grab, val dx: Float, val dy: Float, val lift: Float, var t: Float = 0f) : Mode()
        class Tear(val grab: Peel.Grab, var dx: Float, var dy: Float, val ux: Float, val uy: Float, var speed: Float, var fade: Float = 0f) : Mode()
        class Turn(val forward: Boolean, var t: Float = 0f) : Mode()
    }

    private var mode: Mode = Mode.Idle

    /** A turn is waiting for its snapshot or running: a forward one still shows the old sheet flat until then. */
    val turning: Boolean get() = turnPending || mode is Mode.Turn

    // ---- bitmaps -------------------------------------------------------------

    private suspend fun snapshot(layer: GraphicsLayer): Bitmap? {
        if (layer.size.width <= 0 || layer.size.height <= 0) return null
        val img = layer.toImageBitmap().asAndroidBitmap()
        // A hardware bitmap cannot be drawn on a software canvas: copy it first.
        val soft = if (img.config == Bitmap.Config.HARDWARE) img.copy(Bitmap.Config.ARGB_8888, false) ?: return null else img
        val p = pad.toInt()
        val out = Bitmap.createBitmap(soft.width + 2 * p, soft.height + 2 * p, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(soft, pad, pad, null)
        // Kept on the GPU: drawn every frame, never uploaded again.
        return out.copy(Bitmap.Config.HARDWARE, false) ?: out
    }

    /** Snapshots the current day's sheet (the live layer) for the next touch, when [key] (what it shows) has changed. */
    suspend fun snapshotLive(layer: GraphicsLayer, key: Any) {
        if (key == liveKey && live != null) return
        if (mode != Mode.Idle || turnPending) return
        val bmp = snapshot(layer) ?: return
        if (mode != Mode.Idle || turnPending) return
        live = bmp
        liveKey = key
    }

    /** Sizes everything for [sheet] and hands it to the painter. */
    private fun prepare(sheet: Bitmap) {
        w = sheet.width - 2 * pad
        h = sheet.height - 2 * pad
        r = Peel.radiusFor(w, h, density)
        val key = Triple(sheet.width, sheet.height, accent)
        if (key != backKey || back == null) {
            val corner = 20 * density
            page = Path().apply { addRoundRect(0f, 0f, w, h, floatArrayOf(0f, 0f, 0f, 0f, corner, corner, corner, corner), Path.Direction.CW) }
            val b = Bitmap.createBitmap(sheet.width, sheet.height, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            c.translate(pad, pad)
            val top = androidx.compose.ui.graphics.lerp(Color(0xFFFBF7EF), accent, 0.18f).toArgb()
            val bottom = androidx.compose.ui.graphics.lerp(Color(0xFFEFE6D6), accent, 0.32f).toArgb()
            c.drawPath(page, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w * 0.36f, h, top, bottom, Shader.TileMode.CLAMP) })
            back = b.copy(Bitmap.Config.HARDWARE, false) ?: b
            backKey = key
        }
        painter.set(sheet, back!!, w, h, pad, page)
    }

    // ---- drawing -------------------------------------------------------------

    /** Draws the frame on show; [rings] draws the brass rings (over the flat paper, under the curl). */
    fun draw(scope: DrawScope, rings: DrawScope.() -> Unit) {
        val s = curl ?: return
        scope.drawIntoCanvas { c -> painter.draw(c.nativeCanvas, s, 2400 * density) { scope.rings() } }
    }

    // ---- animation -----------------------------------------------------------

    private fun run() {
        if (job?.isActive == true) return
        // The system's animation scale stretches every time (developer options); 0 = jump to the end.
        val scale = animScale()
        job = scope.launch {
            var last = withFrameNanos { it }
            while (mode != Mode.Idle) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e6f).coerceIn(0f, 50f)
                step(if (scale > 0f) dt / scale else 1000f)
                last = now
            }
        }
    }

    private fun stateOf(f: Peel.Fold, opacity: Float) = CurlState(f.fx, f.fy, f.nx, f.ny, r, opacity)

    private fun step(dt: Float) {
        when (val m = mode) {
            Mode.Idle -> Unit
            is Mode.Drag -> {
                m.lift = min(1f, m.lift + dt / 110f) // ~110 ms to curl a fresh touch
                curl = stateOf(Peel.foldFor(m.grab, m.dx, m.dy, r, Peel.easeOut(m.lift)), 1f)
            }
            is Mode.Back -> {
                m.t += dt
                val t = min(1f, m.t / 240f)
                val k = Peel.easeOut(t)
                curl = stateOf(Peel.foldFor(m.grab, m.dx * (1 - k), m.dy * (1 - k), r, m.lift * (1 - k)), 1f)
                if (t >= 1f) finish()
            }
            is Mode.Tear -> {
                m.speed *= 1.06f.pow(dt / 16f) // accelerate away
                m.dx += m.ux * m.speed * dt
                m.dy += m.uy * m.speed * dt
                val f = Peel.foldFor(m.grab, m.dx, m.dy, r, 1f)
                // The whole sheet has turned once G has travelled twice the span plus half the cylinder.
                if (f.reach > 2 * Peel.span(m.grab, f.nx, f.ny, w, h) + PI.toFloat() * r) m.fade = min(1f, m.fade + 0.12f * dt / 16f)
                curl = stateOf(f, 1 - m.fade)
                if (m.fade >= 1f) finish()
            }
            is Mode.Turn -> {
                m.t += dt
                val p0 = min(1f, m.t / TURN_MS)
                val p = if (m.forward) p0 else 1 - p0
                val grab = Peel.Grab(w, h, Peel.SQRT1_2, Peel.SQRT1_2)
                val ux = -sin(TILT); val uy = -cos(TILT)
                // Far enough that the sheet has turned completely (2·span + πr) by 86 %, when it fades.
                val total = 1.35f * (2 * Peel.span(grab, -ux, -uy, w, h) + PI.toFloat() * r)
                val travel = (0.3f * p + 0.7f * p.pow(2.2f)) * total
                val opacity = if (p > 0.86f) max(0f, 1 - (p - 0.86f) / 0.14f) else 1f
                curl = stateOf(Peel.foldFor(grab, ux * travel, uy * travel, r, min(1f, p / 0.08f)), opacity)
                if (p0 >= 1f) finish()
            }
        }
    }

    /** Ends the run; the overlay and the day underneath change in the same frame. */
    private fun finish() {
        val m = mode
        mode = Mode.Idle
        curl = null
        when (m) {
            is Mode.Tear -> { peeking = false; onTorn() }
            is Mode.Back, is Mode.Drag -> peeking = false
            is Mode.Turn -> onTurnDone?.invoke().also { onTurnDone = null }
            Mode.Idle -> Unit
        }
    }

    /**
     * A turn from the arrows or a day change elsewhere. Forward peels [layer] (the sheet that was
     * on show) off; back brings it (the new day) down. [done] runs when it has finished.
     */
    suspend fun playTurn(layer: GraphicsLayer, forward: Boolean, done: () -> Unit) {
        if (mode is Mode.Drag || mode is Mode.Back || mode is Mode.Tear) { done(); return }
        turnPending = true
        try {
            withFrameNanos { } // the turning sheet has been drawn into its layer
            val bmp = snapshot(layer)
            if (bmp == null) { done(); return }
            onTurnDone?.invoke()
            prepare(bmp)
            mode = Mode.Turn(forward)
            onTurnDone = done
            step(0f)
            run()
        } finally {
            turnPending = false
        }
    }

    // ---- gestures ------------------------------------------------------------

    private fun begin(x: Float, width: Float, height: Float): Boolean {
        if (turning || mode is Mode.Tear) return false
        val sheet = live ?: return false
        prepare(sheet)
        val zone = max(44 * density, min(width, height) * 0.2f)
        val grab = Peel.grabBottom(x, width, height, zone)
        mode = Mode.Drag(grab)
        peeking = true
        step(0f)
        run()
        return true
    }

    private fun move(dx: Float, dy: Float, t: Long) {
        val m = mode as? Mode.Drag ?: return
        m.dx = dx; m.dy = dy
        // The finger's recent path (last 120 ms): the flick's speed and direction at release.
        m.samples.addLast(Sample(t, dx, dy))
        while (m.samples.size > 2 && t - m.samples.first().t > 120) m.samples.removeFirst()
        curl = stateOf(Peel.foldFor(m.grab, m.dx, m.dy, r, Peel.easeOut(m.lift)), 1f)
    }

    private fun release(dx: Float, dy: Float, t: Long, held: Long) {
        val m = mode as? Mode.Drag ?: return
        m.dx = dx; m.dy = dy
        val f = Peel.foldFor(m.grab, dx, dy, r, 1f)
        // Velocity over the last 120 ms (px/ms); a finger that stopped before letting go has none.
        val a = m.samples.firstOrNull { t - it.t <= 120 } ?: Sample(t, dx, dy)
        val dtMs = max(16f, (t - a.t).toFloat())
        val vx = (dx - a.x) / dtMs; val vy = (dy - a.y) / dtMs
        val moved = hypot(dx, dy)
        // Pulled away from the grab (along -n) fast enough, or past three quarters of the sheet: tear it off.
        val along = -(vx * f.nx + vy * f.ny)
        val reachShare = f.reach / max(1f, Peel.span(m.grab, f.nx, f.ny, w, h))
        if (moved > 12 * density && (along > 0.45f * density || reachShare > 0.75f)) {
            val len = hypot(vx, vy)
            val ux = if (len > 0.3f * density) vx / len else -f.nx
            val uy = if (len > 0.3f * density) vy / len else -f.ny
            mode = Mode.Tear(m.grab, dx, dy, ux, uy, max(0.9f * density, len))
        } else if (moved < 8 * density && held < 350) {
            // A tap that had started to curl the edge: drop the curl at once; the button under it gets the tap.
            cancelNow()
        } else {
            mode = Mode.Back(m.grab, dx, dy, m.lift)
        }
    }

    private fun cancelNow() {
        job?.cancel()
        mode = Mode.Idle
        curl = null
        peeking = false
    }

    private fun settle() {
        val m = mode as? Mode.Drag ?: return
        mode = Mode.Back(m.grab, m.dx, m.dy, m.lift)
    }

    /**
     * Pointer input for the paper (its box, the overlay's coordinates): a touch in the bottom
     * 30 % curls the bottom edge (higher up, TearOffSheet's sideways swipe steps a day); moves are consumed once past
     * half the touch slop, so the page does not scroll and the button under the finger does not
     * click. A touch in the middle is left alone.
     *
     * A touch in the bottom zone stays the sheet's even when it cannot curl yet (the last sheet still
     * flying off, a turn running, the next sheet not painted): the page used to scroll under a quick
     * second flick (user, 2026-11-23). It starts curling from wherever the finger is once it can.
     */
    suspend fun gestures(scope: PointerInputScope) = with(scope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val width = size.width.toFloat(); val height = size.height.toFloat()
            var p0 = down.position
            if (p0.y < 0.7f * height) return@awaitEachGesture
            var started = begin(p0.x, width, height)
            var t0 = down.uptimeMillis
            if (started) (mode as? Mode.Drag)?.samples?.addLast(Sample(t0, 0f, 0f))
            val slop = viewConfiguration.touchSlop / 2
            var dragging = false
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    if (ch == null) { settle(); break }
                    if (!dragging && ch.isConsumed) { settle(); break }
                    if (!dragging && (ch.position - down.position).getDistance() > slop) dragging = true
                    if (dragging) ch.consume()
                    if (!started && ch.pressed && begin(ch.position.x, width, height)) {
                        started = true; p0 = ch.position; t0 = ch.uptimeMillis
                        (mode as? Mode.Drag)?.samples?.addLast(Sample(t0, 0f, 0f))
                    }
                    if (!started) { if (!ch.pressed) break else continue }
                    val d: Offset = ch.position - p0
                    if (!ch.pressed) { release(d.x, d.y, ch.uptimeMillis, ch.uptimeMillis - t0); break }
                    move(d.x, d.y, ch.uptimeMillis)
                }
            } finally {
                settle()
            }
        }
    }

    companion object {
        const val TURN_MS = 650f
        private const val TILT = (25 * PI / 180).toFloat()
    }
}
