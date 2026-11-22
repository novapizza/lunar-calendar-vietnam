package io.github.novapizza.licham.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import io.github.novapizza.licham.R
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.isSunday
import io.github.novapizza.licham.core.lunarDayLabel
import io.github.novapizza.licham.core.weekOfYear
import io.github.novapizza.licham.ui.LUNAR_MONTH_NAMES
import io.github.novapizza.licham.ui.LichAmColors
import io.github.novapizza.licham.ui.weekdayName
import kotlin.math.hypot

/**
 * The Today page's tear-off sheet (ui/TearOffSheet.kt) drawn on a bitmap for the widget, which
 * cannot draw it in Glance: Playfair Display, gold foil, letter spacing, hairline gradients, the
 * brass rings. Laid out in the app's own dp (at least 343dp wide) with its two-column lunar row:
 * the gold ring on the left, the can chi on the right. The sheet fills the widget's box: a taller
 * box spreads the gaps between the parts, a wider one widens the sheet.
 */
internal class SheetPainter(context: Context, private val c: LichAmColors) {
    private val playfair = ResourcesCompat.getFont(context, R.font.playfair_display) ?: Typeface.SERIF
    private val playfairItalic = ResourcesCompat.getFont(context, R.font.playfair_display_italic) ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    private val sansSemi = ResourcesCompat.getFont(context, R.font.be_vietnam_pro_semibold) ?: Typeface.DEFAULT_BOLD
    private val sansBold = ResourcesCompat.getFont(context, R.font.be_vietnam_pro_bold) ?: Typeface.DEFAULT_BOLD

    private val accent = c.heroAccent

    fun paint(d: SolarDate, info: DayInfo, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val aspect = heightPx.toFloat() / widthPx
        var w = MIN_W
        var layout = lunarRow(info, w)
        var extra = 0f
        val natural = BINDING + paperHeight(layout.height) + PAD
        if (w * aspect >= natural) {
            extra = w * aspect - natural
        } else {
            w = natural / aspect
            layout = lunarRow(info, w)
            // A wider sheet can fit the inline labels, and the row is never taller then.
            extra = (w * aspect - (BINDING + paperHeight(layout.height) + PAD)).coerceAtLeast(0f)
        }
        val s = widthPx / w
        cv.scale(s, s)
        drawSheet(cv, d, info, w, layout, extra)
        return bmp
    }

    private fun argb(color: Color, alpha: Float = 1f) = color.copy(alpha = color.alpha * alpha).toArgb()

    private fun drawSheet(cv: Canvas, d: SolarDate, info: DayInfo, W: Float, row: LunarRow, extra: Float) {
        val paperTop = BINDING
        val paperH = paperHeight(row.height) + extra
        val paperBottom = BINDING + paperH
        // The extra height of a taller box, shared by four gaps: above the number, above the
        // ornament, above the lunar row and below it.
        val gap = extra / 4f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }

        // The pad: two sheets under this one (inset 16 / 8, peeking 12 / 6 below).
        fun padSheet(inset: Float, drop: Float, color: Int, edge: Int) {
            val r = RectF(inset, paperTop + 20f, W - inset, paperBottom + drop)
            val p = bottomRounded(r, 20f)
            fill.shader = null; fill.color = color; cv.drawPath(p, fill)
            stroke.color = edge; cv.drawPath(p, stroke)
        }
        // A soft drop shadow under the pad.
        fill.shader = null; fill.color = argb(Color.Black, 0.18f)
        cv.drawPath(bottomRounded(RectF(10f, paperTop + 20f, W - 10f, paperBottom + 14f), 20f), fill)
        padSheet(16f, 12f, argb(c.heroTo, 0.7f), argb(accent, 0.15f))
        padSheet(8f, 6f, argb(c.heroVia), argb(accent, 0.2f))

        // Paper: lacquer gradient, soft light at 50% 25%, two faint moons.
        val paper = RectF(0f, paperTop, W, paperBottom)
        val paperPath = bottomRounded(paper, 20f)
        fill.color = OPAQUE
        fill.shader = LinearGradient(0f, paperTop, W, paperBottom, intArrayOf(argb(c.heroFrom), argb(c.heroVia), argb(c.heroTo)), null, Shader.TileMode.CLAMP)
        cv.drawPath(paperPath, fill)
        cv.save()
        cv.clipPath(paperPath)
        val cx = W / 2f
        val cy = paperTop + paperH * 0.25f
        val reach = hypot(W * 0.5f, paperH * 0.75f)
        fill.color = OPAQUE
        fill.shader = RadialGradient(cx, cy, reach, intArrayOf(argb(Color.White, 0.10f), argb(Color.White, 0f)), floatArrayOf(0f, 0.6f), Shader.TileMode.CLAMP)
        cv.drawRect(paper, fill)
        fill.shader = null
        fill.color = argb(accent, 0.06f); cv.drawCircle(W + 64f - 112f, paperTop - 8f + 112f, 112f, fill)
        fill.color = argb(accent, 0.05f); cv.drawCircle(-40f + 64f, paperBottom - 96f - 64f, 64f, fill)
        cv.restore()

        // Binding: darker lacquer strip, the year, a gold edge.
        val binding = RectF(0f, 0f, W, BINDING)
        val bindingPath = topRounded(binding, 20f)
        fill.color = OPAQUE
        fill.shader = LinearGradient(0f, 0f, W, BINDING, intArrayOf(argb(c.heroFrom), argb(c.heroVia), argb(c.heroTo)), null, Shader.TileMode.CLAMP)
        cv.drawPath(bindingPath, fill)
        fill.shader = null; fill.color = argb(Color.Black, 0.25f); cv.drawPath(bindingPath, fill)
        fill.color = argb(accent, 0.7f); cv.drawRect(0f, BINDING - 1f, W, BINDING, fill)
        text(cv, "${d.year}", cx, 0f, BINDING, 15f, sansBold, argb(accent), spacing = 0.3f)

        // Two brass rings, 18dp above the paper.
        ring(cv, W * 0.26f, paperTop - 18f)
        ring(cv, W * 0.74f - 9f, paperTop - 18f)

        // Double gold hairline frame.
        stroke.shader = null
        stroke.color = argb(accent, 0.45f)
        cv.drawRoundRect(RectF(12.5f, paperTop + 12.5f, W - 12.5f, paperBottom - 12.5f), 14f, 14f, stroke)
        stroke.color = argb(accent, 0.25f)
        cv.drawRoundRect(RectF(16.5f, paperTop + 16.5f, W - 16.5f, paperBottom - 16.5f), 11f, 11f, stroke)

        // Contents, top to bottom with the app's line heights.
        val left = SIDE
        val right = W - SIDE
        var y = paperTop + 40f
        text(cv, "THÁNG ${d.month} · TUẦN ${d.weekOfYear}", cx, y, 16.5f, 11f, sansSemi, argb(accent), spacing = 0.34f)
        y += 16.5f + gap

        val number = NUMBER
        foilText(cv, "${d.day}", cx, y, number * 1.05f, number, spacing = -0.02f)
        y += number * 1.05f

        // Weekday between two hairlines; Sundays and holidays get the gold seal.
        val red = d.isSunday || info.holiday != null
        val wd = d.weekdayName().uppercase()
        val wdPaint = textPaint(13f, sansSemi, 0.4f)
        val wdWidth = wdPaint.measureText(wd) - 13f * 0.4f
        val pillW = wdWidth + 24f
        val rowH = 27.5f
        if (red) {
            fill.shader = null; fill.color = argb(accent)
            cv.drawRoundRect(RectF(cx - pillW / 2, y, cx + pillW / 2, y + rowH), rowH / 2, rowH / 2, fill)
        }
        text(cv, wd, cx, y, rowH, 13f, sansSemi, if (red) argb(c.heroAccentInk) else argb(Color.White), spacing = 0.4f)
        fill.color = argb(accent, 0.4f)
        cv.drawRect(cx - pillW / 2 - 12f - 32f, y + rowH / 2 - 0.5f, cx - pillW / 2 - 12f, y + rowH / 2 + 0.5f, fill)
        cv.drawRect(cx + pillW / 2 + 12f, y + rowH / 2 - 0.5f, cx + pillW / 2 + 12f + 32f, y + rowH / 2 + 0.5f, fill)
        y += rowH

        // The day's name in italic gold.
        y += 8f
        val name = info.holiday ?: info.observance ?: lunarDayLabel(info.lunar)
        if (name != null) text(cv, name, cx, y, 24f, 16f, playfairItalic, argb(accent), maxWidth = right - left, wght = 400)
        y += 24f

        // Ornament: fading hairlines and a small gold diamond.
        y += 12f + gap
        val oy = y + 3f
        fill.color = OPAQUE
        fill.shader = LinearGradient(left, 0f, cx - 15f, 0f, argb(accent, 0f), argb(accent, 0.55f), Shader.TileMode.CLAMP)
        cv.drawRect(left, oy - 0.5f, cx - 15f, oy + 0.5f, fill)
        fill.color = OPAQUE
        fill.shader = LinearGradient(cx + 15f, 0f, right, 0f, argb(accent, 0.55f), argb(accent, 0f), Shader.TileMode.CLAMP)
        cv.drawRect(cx + 15f, oy - 0.5f, right, oy + 0.5f, fill)
        fill.shader = null; fill.color = argb(accent, 0.8f)
        cv.drawPath(Path().apply { moveTo(cx, oy - 4.2f); lineTo(cx + 4.2f, oy); lineTo(cx, oy + 4.2f); lineTo(cx - 4.2f, oy); close() }, fill)
        y += 6f

        // The lunar row: the gold ring, a gold rule, the can chi (TearOffSheet's wide layout).
        y += 16f + gap
        moon(cv, info, left + 52f, y + row.height / 2f)
        fill.shader = null; fill.color = argb(accent, 0.35f)
        val ruleX = left + 104f + 20f
        cv.drawRect(ruleX, y + row.height / 2f - 40f, ruleX + 1f, y + row.height / 2f + 40f, fill)
        canChi(cv, info, ruleX + 21f, right, y, row)
    }

    /** How the can chi fit beside the ring: labels inline (as the app) or, when too narrow, above. */
    private class LunarRow(val inline: Boolean, val labelWidth: Float, val height: Float)

    private fun canChiRows(info: DayInfo) = listOf("NĂM" to info.canChiYear, "THÁNG" to info.canChiMonth, "NGÀY" to info.canChiDay)

    private fun lunarRow(info: DayInfo, w: Float): LunarRow {
        val room = (w - 2 * SIDE) - (104f + 20f + 1f + 20f)
        val labelPaint = textPaint(9f, sansSemi, 0.24f)
        val valuePaint = textPaint(16.8f, playfair, 0f, 600)
        val rows = canChiRows(info)
        // As TearOffSheet's CanChiRows: the widest label + 2dp, at least 44dp, then 12dp, then the value.
        val labelWidth = maxOf(rows.maxOf { labelPaint.measureText(it.first) } + 2f, 44f)
        val valueWidth = rows.maxOf { valuePaint.measureText(it.second) }
        return if (labelWidth + 12f + valueWidth <= room) LunarRow(true, labelWidth, 104f)
        else LunarRow(false, 0f, maxOf(104f, 3 * (STACK_LABEL + 25.2f) + 2 * 4f))
    }

    private fun paperHeight(rowHeight: Float) =
        40f + 16.5f + NUMBER * 1.05f + 27.5f + 8f + 24f + 12f + 6f + 16f + rowHeight + 32f

    private fun canChi(cv: Canvas, info: DayInfo, x: Float, right: Float, top: Float, row: LunarRow) {
        val rows = canChiRows(info)
        val labelColor = argb(Color.White, 0.55f)
        val valueColor = argb(Color.White)
        if (row.inline) {
            // Three rows 25.2 high, 6 apart, label and value on one baseline, centred on the ring.
            var y = top + (row.height - (3 * 25.2f + 2 * 6f)) / 2f
            rows.forEach { (label, value) ->
                val base = textLeft(cv, value, x + row.labelWidth + 12f, y, 25.2f, 16.8f, playfair, valueColor, maxWidth = right - x - row.labelWidth - 12f, wght = 600)
                textLeft(cv, label, x, y, 25.2f, 9f, sansSemi, labelColor, spacing = 0.24f, baseline = base)
                y += 25.2f + 6f
            }
        } else {
            // Too narrow: each label smaller, above its value.
            var y = top + (row.height - (3 * (STACK_LABEL + 25.2f) + 2 * 4f)) / 2f
            rows.forEach { (label, value) ->
                textLeft(cv, label, x, y, STACK_LABEL, 7.5f, sansSemi, labelColor, spacing = 0.24f)
                textLeft(cv, value, x, y + STACK_LABEL, 25.2f, 16.8f, playfair, valueColor, maxWidth = right - x, wght = 600)
                y += STACK_LABEL + 25.2f + 4f
            }
        }
    }

    private fun moon(cv: Canvas, info: DayInfo, cx: Float, cy: Float) {
        val r = 52f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = argb(Color.Black, 0.12f); cv.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f; p.color = argb(Color.Black, 0.08f); cv.drawCircle(cx, cy, r - 1f - 2f, p)
        p.strokeWidth = 1f; p.color = argb(accent, 0.28f); cv.drawCircle(cx, cy, r - 1f - 4.5f, p)
        p.color = argb(accent, 0.55f); cv.drawCircle(cx, cy, r - 0.5f, p)
        val top = cy - (13.5f + 41.6f + 2f + 16f) / 2f
        text(cv, if (info.lunar.leap) "NHUẬN" else "ÂM LỊCH", cx, top, 13.5f, 9f, sansSemi, argb(accent, 0.85f), spacing = 0.28f)
        foilText(cv, "${info.lunar.day}", cx, top + 13.5f, 41.6f, 41.6f)
        val month = LUNAR_MONTH_NAMES[info.lunar.month - 1]
        text(cv, "tháng $month", cx, top + 13.5f + 41.6f + 2f, 16f, if (month.length > 5) 10.5f else 12f, playfairItalic, argb(Color.White, 0.7f), wght = 400)
    }

    /** A brass ring: 9×30, horizontal brass gradient, a 2dp shadow. */
    private fun ring(cv: Canvas, x: Float, y: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = argb(Color.Black, 0.3f)
        cv.drawRoundRect(RectF(x, y + 2f, x + 9f, y + 32f), 4.5f, 4.5f, p)
        p.color = OPAQUE
        p.shader = LinearGradient(
            x, 0f, x + 9f, 0f,
            intArrayOf(0xFF7A5512.toInt(), 0xFFE9CF8A.toInt(), 0xFFFFF3CF.toInt(), 0xFFA77D26.toInt()), floatArrayOf(0f, 0.45f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        cv.drawRoundRect(RectF(x, y, x + 9f, y + 30f), 4.5f, 4.5f, p)
    }

    /**
     * [wght] sets the weight axis of the variable Playfair files, as ui/Theme.kt PlayfairDisplay does
     * (without it Android draws the default 400 instance); serif text also gets lining figures
     * ("lnum", as the app's foil()), else Playfair's old-style 4 hangs below the line.
     */
    private fun textPaint(size: Float, face: Typeface, spacing: Float, wght: Int = 0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face
        textSize = size
        letterSpacing = spacing
        textAlign = Paint.Align.CENTER
        if (wght > 0) {
            fontVariationSettings = "'wght' $wght"
            fontFeatureSettings = "lnum"
        }
    }

    /** Draws one line centred on [cx] in the line box [top]..[top]+[lineH]; shrinks to [maxWidth]. */
    private fun text(cv: Canvas, s: String, cx: Float, top: Float, lineH: Float, size: Float, face: Typeface, color: Int, spacing: Float = 0f, maxWidth: Float = Float.MAX_VALUE, wght: Int = 0) {
        val p = textPaint(size, face, spacing, wght)
        val wide = p.measureText(s)
        if (wide > maxWidth) p.textSize = size * maxWidth / wide
        p.color = color
        val fm = p.fontMetrics
        // Letter spacing trails the last glyph: shift by half of it to centre the ink (the web's pl-[…em]).
        cv.drawText(s, cx + p.textSize * spacing / 2f, top + lineH / 2f - (fm.ascent + fm.descent) / 2f, p)
    }

    /**
     * Draws one line from [x] in the line box [top]..[top]+[lineH] (or on [baseline]), shrunk to
     * [maxWidth]; returns its baseline.
     */
    private fun textLeft(cv: Canvas, s: String, x: Float, top: Float, lineH: Float, size: Float, face: Typeface, color: Int, spacing: Float = 0f, maxWidth: Float = Float.MAX_VALUE, wght: Int = 0, baseline: Float? = null): Float {
        val p = textPaint(size, face, spacing, wght).apply { textAlign = Paint.Align.LEFT }
        val wide = p.measureText(s)
        if (wide > maxWidth) p.textSize = size * maxWidth / wide
        p.color = color
        val fm = p.fontMetrics
        val base = baseline ?: (top + lineH / 2f - (fm.ascent + fm.descent) / 2f)
        cv.drawText(s, x, base, p)
        return base
    }

    /** Gold-foil print (TearOffSheet foil()): light-to-deep accent gradient, a 2dp drop beneath. */
    private fun foilText(cv: Canvas, s: String, cx: Float, top: Float, lineH: Float, size: Float, spacing: Float = 0f) {
        val p = textPaint(size, playfair, spacing, wght = 700)
        val fm = p.fontMetrics
        val base = top + lineH / 2f - (fm.ascent + fm.descent) / 2f
        val x = cx + size * spacing / 2f
        p.color = argb(Color.Black, 0.22f)
        cv.drawText(s, x, base + 2f, p)
        // Over the line's box (ascent to descent, no font padding), as Compose spreads the app's foil brush.
        p.color = OPAQUE
        p.shader = LinearGradient(
            0f, base + fm.ascent, 0f, base + fm.descent,
            intArrayOf(lerp(Color.White, accent, 0.4f).toArgb(), accent.toArgb(), lerp(Color(0xFF7A4A08), accent, 0.68f).toArgb()),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP,
        )
        cv.drawText(s, x, base, p)
    }

    private fun bottomRounded(r: RectF, radius: Float) = Path().apply {
        addRoundRect(r, floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius), Path.Direction.CW)
    }

    private fun topRounded(r: RectF, radius: Float) = Path().apply {
        addRoundRect(r, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
    }

    companion object {
        /** A paint's alpha also scales its shader: gradients are drawn with an opaque colour. */
        private const val OPAQUE = 0xFFFFFFFF.toInt()

        /** The narrowest sheet drawn (the web's phone width); a wider box widens it. */
        const val MIN_W = 343f
        const val BINDING = 48f
        /** Pad sheets peeking below the paper. */
        const val PAD = 12f
        /** Padding and frame from the paper's side to the contents (12 + 4 + 1 + 16). */
        const val SIDE = 33f
        /** The solar day: 44% of the 343dp sheet, as TearOffSheet sizes it. */
        const val NUMBER = 151f
        /** A stacked can chi label's line (7.5sp type). */
        const val STACK_LABEL = 11f
    }
}
