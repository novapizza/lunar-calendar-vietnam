package io.github.novapizza.licham.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.ThemePref
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.DayFacts
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.lunarDayLabel
import io.github.novapizza.licham.core.plusDays
import io.github.novapizza.licham.core.weekOfYear
import io.github.novapizza.licham.core.weekdayIndex
import java.time.LocalDateTime
import kotlin.math.hypot

/** Lunar month names as printed on tear-off calendars ("Tháng Giêng" … "Tháng Chạp"); month 11 is "Mười Một", not the old "Một" (read as month 1, 2026-11-22). */
internal val LUNAR_MONTH_NAMES = listOf("Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười Một", "Chạp")

private val SheetShape = RoundedCornerShape(20.dp)
private val PadShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
private val BindingShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** A page turn in progress: from which day, which way. */
private class Turn(val from: SolarDate, val forward: Boolean, val id: Int)

/**
 * The day on show (a plain field) and the turn in progress (state). Written during composition,
 * so the turn starts in the same frame as the new day: starting it from an effect showed the new
 * day for a frame first (the web had that bug, measured 2026-11-22).
 */
private class TurnTracker(var shown: SolarDate) {
    val turn = mutableStateOf<Turn?>(null)
}

/**
 * Fitting the sheet to the pane's height (LocalPaneHeight, two-pane layouts only). Only the
 * number's line changes the sheet's height, so after each layout rest = sheet height - the
 * number's box, and the number may be at most (pane height - rest) / 1.05. A wide, short pane
 * (Galaxy Z Fold 8 unfolded, at 420 dpi) put the sheet's bottom under the tab bar otherwise.
 */
private class SheetFit {
    /** The number's box (1.05 x its size) in the last composition. */
    var box = 0.dp
    /** Everything but the number's box, from the last layout. */
    var rest by mutableStateOf<Dp?>(null)
}

/**
 * The Today page's date as one sheet of a Vietnamese tear-off wall calendar (lịch bóc),
 * components/TearOffSheet.tsx: a lacquer sheet hung on two brass rings, framed by a double gold
 * hairline, the solar day in large gold-foil serif (Sundays and holidays, red on a paper
 * calendar, get a gold seal on the weekday), and the lunar date in a gold "moon" next to the
 * can chi. Two sheets peek out below.
 *
 * A finger on the bottom edge curls the paper and tears it off (the next day), and the arrows turn the
 * page from the bottom-right corner (SheetPeel, Android 10+ with animations on); an earlier day
 * brings a sheet back. Older systems swipe and turn with PageTurn.
 */
@Composable
fun TearOffSheet(state: AppState, info: DayInfo, now: LocalDateTime) {
    val c = LocalLichAmColors.current
    val d = state.selectedDate
    val accent = c.heroAccent
    val tracker = remember { TurnTracker(d) }
    if (tracker.shown != d) {
        tracker.turn.value = Turn(tracker.shown, d > tracker.shown, (tracker.turn.value?.id ?: 0) + 1)
        tracker.shown = d
    }
    val turn = tracker.turn.value
    // The finger-driven curl draws with Canvas.drawVertices, hardware-accelerated from Android 10.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val peel = remember {
        val scale = { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && scale() > 0f) PeelController(scope, context.resources.displayMetrics.density, scale) else null
    }
    if (peel != null) {
        peel.accent = accent
        peel.onTorn = {
            // Torn off by hand: the curl has already shown the turn, so no PageTurn.
            val next = tracker.shown.plusDays(1)
            tracker.shown = next
            tracker.turn.value = null
            state.selectDate(next)
        }
    }
    // The arrows always step from the selected day, also while a turn shows an older sheet.
    val prev = { state.selectDate(d.plusDays(-1)) }
    val next = { state.selectDate(d.plusDays(1)) }
    fun infoOf(day: SolarDate) = if (day == d) info else DayInfo.of(day)
    val paneHeight = LocalPaneHeight.current
    val fit = remember { SheetFit() }
    val density = LocalDensity.current

    // One sheet, nothing under it: the two sheets that peeked out below were removed (user, 2026-11-23).
    Box(Modifier.fillMaxWidth().onSizeChanged { fit.rest = with(density) { it.height.toDp() } - fit.box }) {
        // No clip here: the turning sheet leaves the sheet's box. Binding and paper round their own corners.
        // A sideways swipe steps a day; with the curl, only above its bottom zone (the curl takes its
        // touches first: it consumes them before the swipe's slop).
        BoxWithConstraints(Modifier.fillMaxWidth().webShadow(SheetShape, float = true).swipe(onNext = next, onPrev = prev)) {
            val sheetWidth = maxWidth
            // Solar day: 44% of the sheet's width (the web's 44cqw), 80–184, and in a pane no
            // taller than the whole sheet can stand (SheetFit).
            val byWidth = (sheetWidth * 0.44f).coerceIn(80.dp, 184.dp)
            val rest = fit.rest
            val numberDp = if (paneHeight != null && rest != null) minOf(byWidth, ((paneHeight - rest) / 1.05f).coerceAtLeast(80.dp)) else byWidth
            fit.box = numberDp * 1.05f
            Column {
                Binding(state, d)
                Box(if (peel != null) Modifier.pointerInput(peel) { peel.gestures(this) } else Modifier) {
                    val layer = rememberGraphicsLayer()
                    val liveLayer = rememberGraphicsLayer()
                    if (turn != null) {
                        // The turning sheet, recorded into a layer and not drawn: the curl draws it.
                        // Composed first, so the live sheet above it takes the touches.
                        val turning = if (turn.forward) turn.from else d
                        key(turn.id) {
                            SheetPage(
                                turning, infoOf(turning), now, sheetWidth, numberDp, {}, {},
                                Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() } },
                                personal = state.lunarEvents,
                            )
                        }
                    }
                    // The sheet underneath: the new day going forward, the old one going back, the
                    // next day while a finger holds the sheet.
                    val under = when {
                        turn != null && !turn.forward -> turn.from
                        peel?.peeking == true -> d.plusDays(1)
                        else -> d
                    }
                    SheetPage(
                        under, infoOf(under), now, sheetWidth, numberDp, prev, next,
                        if (peel == null) Modifier else Modifier.drawWithContent {
                            liveLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(liveLayer)
                        },
                        personal = state.lunarEvents,
                    )
                    // Two brass rings, over the sheet (30dp below the binding's top = 18dp above the paper).
                    Ring(Modifier.offset(x = sheetWidth * 0.26f, y = (-18).dp))
                    Ring(Modifier.offset(x = sheetWidth * 0.74f - 9.dp, y = (-18).dp))
                    if (peel != null) {
                        // Snapshot the current day's sheet while idle, so a touch curls at once.
                        val liveKey = listOf(d, now.hour, now.minute, c, sheetWidth, numberDp)
                        val idle = turn == null && !peel.peeking
                        LaunchedEffect(liveKey, idle) {
                            if (idle) {
                                withFrameNanos { }
                                withFrameNanos { }
                                peel.snapshotLive(liveLayer, liveKey)
                            }
                        }
                        if (turn != null) {
                            LaunchedEffect(turn.id) {
                                peel.playTurn(layer, turn.forward) { if (tracker.turn.value?.id == turn.id) tracker.turn.value = null }
                            }
                        }
                        val ringXs = sheetWidth * 0.26f to sheetWidth * 0.74f - 9.dp
                        val flatTurn = turn?.forward == true
                        Spacer(
                            Modifier.matchParentSize().drawBehind {
                                val rings: DrawScope.() -> Unit = {
                                    drawRing(Offset(ringXs.first.toPx(), -18.dp.toPx()))
                                    drawRing(Offset(ringXs.second.toPx(), -18.dp.toPx()))
                                }
                                // A forward turn shows the old sheet flat until its curl starts (a
                                // frame or two for the snapshot): never the new day alone.
                                if (flatTurn && peel.curl == null) { drawLayer(layer); rings() }
                                else peel.draw(this, rings)
                            },
                        )
                    } else if (turn != null) {
                        key(turn.id) {
                            PageTurn(layer, turn.forward, sheetWidth, Modifier.matchParentSize()) {
                                if (tracker.turn.value?.id == turn.id) tracker.turn.value = null
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The turning sheet, a corner peel: the recorded sheet drawn PIECES times, each piece cut
 * parallel to the fold (the flat part, thin bands round the curl, the folded part), turned about
 * the fold line by PageCurl.frame and projected in perspective, so the paper bends round a
 * cylinder as the bottom-right corner lifts. A piece turned past 90° shows the cream back of the
 * paper. The rings are drawn between the flat piece and the lifted ones. Forward runs progress
 * 0 -> 1 (the sheet peels off past the top-left corner), backward 1 -> 0.
 */
@Composable
private fun PageTurn(layer: GraphicsLayer, forward: Boolean, sheetWidth: Dp, modifier: Modifier, onDone: () -> Unit) {
    val c = LocalLichAmColors.current
    val accent = c.heroAccent
    val progress = remember { Animatable(if (forward) 0f else 1f) }
    LaunchedEffect(Unit) {
        // Linear time; the ease (slow start, fast end) is in PageCurl.frame. Follows the
        // system's animation scale (off = no turn).
        progress.animateTo(if (forward) 1f else 0f, tween(PageCurl.TURN_MS, easing = LinearEasing))
        onDone()
    }
    val backTop = lerp(Color(0xFFFBF7EF), accent, 0.18f)
    val backBottom = lerp(Color(0xFFEFE6D6), accent, 0.32f)
    Spacer(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            val f = PageCurl.frame(progress.value, w, h, 0.5.dp.toPx())
            val corner = 20.dp.toPx()
            val page = android.graphics.Path().apply {
                addRoundRect(0f, 0f, w, h, floatArrayOf(0f, 0f, 0f, 0f, corner, corner, corner, corner), android.graphics.Path.Direction.CW)
            }
            val dd = PageCurl.d
            // Shadow the lifted paper casts on the sheet underneath, sliding with the fold.
            clipPath(page.asComposePath()) {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Black.copy(alpha = f.shadowAlpha), 1f to Color.Transparent,
                        start = Offset(dd[0] * f.shadowFrom, dd[1] * f.shadowFrom), end = Offset(dd[0] * f.shadowTo, dd[1] * f.shadowTo),
                    ),
                )
            }
            val dist = PageCurl.PERSPECTIVE_DP.dp.toPx()
            val cx = w / 2
            val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
            val dst = FloatArray(8)
            val p3 = FloatArray(3)
            val matrix = android.graphics.Matrix()
            val backBrush = Brush.linearGradient(listOf(backTop, backBottom), start = Offset(0f, 0f), end = Offset(w * 0.36f, h))
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                val saved = if (f.opacity < 1f) nc.saveLayerAlpha(null, (f.opacity * 255).toInt()) else nc.save()
                f.pieces.forEachIndexed { k, piece ->
                    if (piece.poly.size >= 6) {
                        // Project the page corners of this piece's plane; the homography maps the whole plane.
                        for (i in 0 until 4) {
                            piece.map(src[2 * i], src[2 * i + 1], p3)
                            val s = dist / (dist - p3[2])
                            dst[2 * i] = cx + (p3[0] - cx) * s
                            dst[2 * i + 1] = p3[1] * s
                        }
                        matrix.setPolyToPoly(src, 0, dst, 0, 4)
                        val outline = android.graphics.Path().apply {
                            moveTo(piece.poly[0], piece.poly[1])
                            for (i in 1 until piece.poly.size / 2) lineTo(piece.poly[2 * i], piece.poly[2 * i + 1])
                            close()
                            op(page, android.graphics.Path.Op.INTERSECT)
                        }
                        nc.save()
                        nc.concat(matrix)
                        nc.clipPath(outline)
                        if (piece.back) drawRect(backBrush) else drawLayer(layer)
                        // The shade runs smoothly across the piece, matching its neighbours at the edges.
                        if (piece.shadeStart > 0f || piece.shadeEnd > 0f) drawRect(
                            Brush.linearGradient(
                                0f to Color.Black.copy(alpha = piece.shadeStart), 1f to Color.Black.copy(alpha = piece.shadeEnd),
                                start = Offset(dd[0] * piece.sStart, dd[1] * piece.sStart), end = Offset(dd[0] * piece.sEnd, dd[1] * piece.sEnd),
                            ),
                        )
                        nc.restore()
                    }
                    // The rings stay over the paper still on the pad, under the curl and the flap.
                    if (k == 0) {
                        drawRing(Offset(sheetWidth.toPx() * 0.26f, -18.dp.toPx()))
                        drawRing(Offset(sheetWidth.toPx() * 0.74f - 9.dp.toPx(), -18.dp.toPx()))
                    }
                }
                nc.restoreToCount(saved)
            }
        },
    )
}

private val RingBrushStops = arrayOf(0f to Color(0xFF7A5512), 0.45f to Color(0xFFE9CF8A), 0.55f to Color(0xFFFFF3CF), 1f to Color(0xFFA77D26))

/** A brass ring drawn on a canvas (as Ring, for PageTurn). */
private fun DrawScope.drawRing(topLeft: Offset) {
    val ring = Size(9.dp.toPx(), 30.dp.toPx())
    val r = CornerRadius(ring.width / 2)
    drawRoundRect(Color.Black.copy(alpha = 0.3f), topLeft + Offset(0f, 2.dp.toPx()), ring, r)
    drawRoundRect(Brush.horizontalGradient(*RingBrushStops, startX = topLeft.x, endX = topLeft.x + ring.width), topLeft, ring, r)
}

/** The darker lacquer strip edged in gold: theme button, week, back to today. */
@Composable
private fun Binding(state: AppState, d: SolarDate) {
    val c = LocalLichAmColors.current
    val white = Color.White.copy(alpha = 0.85f)
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(BindingShape)
            .background(Brush.linearGradient(listOf(c.heroFrom, c.heroVia, c.heroTo)))
            .background(Color.Black.copy(alpha = 0.25f)),
    ) {
        Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, label) = when (state.themePref) {
                ThemePref.SYSTEM -> Icons.Outlined.Brightness6 to "Giao diện: theo hệ thống"
                ThemePref.LIGHT -> Icons.Outlined.LightMode to "Giao diện: sáng"
                ThemePref.DARK -> Icons.Outlined.DarkMode to "Giao diện: tối"
            }
            IconBtn(icon, label, state::cycleTheme, tint = white, iconSize = 18.dp)
            // The year on the binding, in larger print; the week moved to the sheet's top line.
            Text(
                "${d.year}", color = c.heroAccent, fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.3.em, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            if (d == AppState.today()) Spacer(Modifier.size(40.dp))
            else IconBtn(Icons.AutoMirrored.Filled.Undo, "Về hôm nay", { state.selectDate(AppState.today()) }, tint = white, iconSize = 18.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(c.heroAccent.copy(alpha = 0.7f)))
    }
}

@Composable
private fun Ring(modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.size(9.dp, 30.dp)
            .shadow(3.dp, shape, ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(Brush.horizontalGradient(*RingBrushStops)),
    )
}

/** Gold-foil print: a light-to-deep gradient of the palette's hero accent, with a 2dp drop. */
@Composable
private fun foil(accent: Color): TextStyle {
    val drop = with(LocalDensity.current) { 2.dp.toPx() }
    // Merged into the theme's text style (no font padding, centred line height), lining figures.
    return LocalTextStyle.current.merge(TextStyle(
        fontFeatureSettings = "lnum",
        brush = Brush.verticalGradient(
            0f to lerp(Color.White, accent, 0.4f),
            0.48f to accent,
            1f to lerp(Color(0xFF7A4A08), accent, 0.68f),
        ),
        shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, drop), 0f),
    ))
}

/** dp → sp that ignores the font size setting: sizes taken from the sheet's width. */
@Composable
private fun Dp.fixedSp(): TextUnit = with(LocalDensity.current) { this@fixedSp.toSp() }

/** One sheet of paper: lacquer with its light and moons, the frame and the dates. */
@Composable
private fun SheetPage(d: SolarDate, info: DayInfo, now: LocalDateTime, sheetWidth: Dp, numberDp: Dp, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier, personal: List<LunarEvent> = emptyList()) {
    val c = LocalLichAmColors.current
    val accent = c.heroAccent
    val red = d.weekdayIndex == 6 || info.holiday != null
    val gold = foil(accent)

    Column(
        modifier.fillMaxWidth().clip(PadShape)
            .background(Brush.linearGradient(listOf(c.heroFrom, c.heroVia, c.heroTo)))
            .drawBehind {
                // Soft light at 50% 25% (white 10% -> clear at 60%) and the old hero card's two
                // moons (-right-16 -top-2 w-56; -left-10 bottom-24 w-32), as the web's paper.
                val center = Offset(size.width * 0.5f, size.height * 0.25f)
                val reach = hypot(size.width * 0.5f, size.height * 0.75f)
                drawRect(Brush.radialGradient(0f to Color.White.copy(alpha = 0.10f), 0.6f to Color.Transparent, center = center, radius = reach))
                val big = 112.dp.toPx()
                drawCircle(accent.copy(alpha = 0.06f), big, Offset(size.width + 64.dp.toPx() - big, -8.dp.toPx() + big))
                val small = 64.dp.toPx()
                drawCircle(accent.copy(alpha = 0.05f), small, Offset(-40.dp.toPx() + small, size.height - 96.dp.toPx() - small))
            }
            .padding(12.dp),
    ) {
        // Double gold hairline frame, as on printed calendar sheets.
        val outer = RoundedCornerShape(14.dp)
        val inner = RoundedCornerShape(11.dp)
        Column(Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.45f), outer).padding(3.dp).border(1.dp, accent.copy(alpha = 0.25f), inner).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)) {
            Text(
                "THÁNG ${d.month} · TUẦN ${d.weekOfYear}", color = accent, fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.34.em, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            // Solar day, sized by TearOffSheet.
            val numberSize = numberDp.fixedSp()
            val numberBox = numberDp * 1.05f
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Filled.ChevronLeft, "Ngày trước", onPrev, tint = Color.White.copy(alpha = 0.6f), iconSize = 22.dp)
                Text(
                    "${d.day}", style = gold, fontFamily = PlayfairDisplay, fontWeight = FontWeight.Bold, fontSize = numberSize,
                    lineHeight = numberSize * 1.05f, letterSpacing = (-0.02).em, textAlign = TextAlign.Center,
                    // Android never lays a line out shorter than the font's own height (Playfair: 1.33em),
                    // so the 1.05em line box of the web is a fixed box the glyphs may overflow.
                    modifier = Modifier.weight(1f).height(numberBox).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
                )
                IconBtn(Icons.Filled.ChevronRight, "Ngày sau", onNext, tint = Color.White.copy(alpha = 0.6f), iconSize = 22.dp)
            }

            // Sundays and holidays (red on a paper calendar) get a gold seal on the weekday.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(32.dp).height(1.dp).background(accent.copy(alpha = 0.4f)))
                Text(
                    d.weekdayName().uppercase(), color = if (red) c.heroAccentInk else Color.White, fontSize = 13.sp, lineHeight = 19.5.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.em,
                    // Same padding every day, the seal only adds the fill (padded on Sundays alone, the sheet grew 8dp).
                    modifier = (if (red) Modifier.clip(Pill).background(accent) else Modifier).padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Box(Modifier.width(32.dp).height(1.dp).background(accent.copy(alpha = 0.4f)))
            }

            Text(
                // A day without a holiday is named after the user's own event first.
                info.holiday ?: PersonalEvents.on(personal, d).firstOrNull()?.title ?: info.observance ?: lunarDayLabel(info.lunar) ?: "", color = accent, fontFamily = PlayfairDisplay, fontStyle = FontStyle.Italic,
                fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 24.dp),
            )

            // Ornament: hairlines and a small gold diamond.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.55f)))))
                Box(Modifier.size(6.dp).rotate(45f).background(accent.copy(alpha = 0.8f)))
                Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent))))
            }

            // Lunar date in a gold moon, a gold rule, the can chi beside it: always two columns (as
            // the web and the widget). CanChiRows puts each label above its value, smaller, when
            // the column is too narrow for label and value on one line.
            val canChi = listOf("NĂM" to info.canChiYear, "THÁNG" to info.canChiMonth, "NGÀY" to info.canChiDay)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Moon(info, gold)
                Spacer(Modifier.width(if (sheetWidth >= 352.dp) 20.dp else 16.dp))
                Box(Modifier.width(1.dp).height(80.dp).background(accent.copy(alpha = 0.35f)))
                Spacer(Modifier.width(if (sheetWidth >= 352.dp) 20.dp else 16.dp))
                BoxWithConstraints(Modifier.weight(1f)) { CanChiRows(canChi, maxWidth) }
            }

            val hourName = CalendarData.calendar.chi[DayFacts.chiOfHour(now.hour)]
            Text(
                "GIỜ ${hourName.uppercase()} · %02d:%02d".format(now.hour, now.minute), color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.em, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
        }
    }
}

/** The lunar day in a 104dp gold ring, "Âm lịch" above and "tháng Mười" below. */
@Composable
private fun Moon(info: DayInfo, gold: TextStyle) {
    val c = LocalLichAmColors.current
    val accent = c.heroAccent
    Column(
        Modifier.size(104.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.12f))
            .drawBehind {
                // inset 0 0 0 4px black/8%, then a 1px ring of the accent at 28% just inside it.
                val b = 1.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.08f), size.minDimension / 2 - b - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
                drawCircle(accent.copy(alpha = 0.28f), size.minDimension / 2 - b - 4.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        // A leap month says so on the top line, so the bottom line stays inside the ring.
        Text(if (info.lunar.leap) "NHUẬN" else "ÂM LỊCH", color = accent.copy(alpha = 0.85f), fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.28.em)
        val fs = 41.6.sp
        Text(
            "${info.lunar.day}", style = gold, fontFamily = PlayfairDisplay, fontWeight = FontWeight.Bold, fontSize = fs, lineHeight = fs,
            modifier = Modifier.leadingNone(fs),
        )
        // "Mười Một" smaller (10.5 instead of 12), as on the web: at 12 it touched the ring.
        val name = LUNAR_MONTH_NAMES[info.lunar.month - 1]
        Text(
            "tháng $name", color = Color.White.copy(alpha = 0.7f), maxLines = 1, softWrap = false,
            fontFamily = PlayfairDisplay, fontStyle = FontStyle.Italic, fontSize = if (name.length > 5) 10.5.sp else 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun CanChiLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em, maxLines = 1, softWrap = false, modifier = modifier)
}

@Composable
private fun CanChiValue(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontFamily = PlayfairDisplay, fontWeight = FontWeight.SemiBold, fontSize = 16.8.sp, lineHeight = 25.2.sp, maxLines = 1, softWrap = false, modifier = modifier)
}

/**
 * Three rows: label (as wide as the widest label, at least 44dp, the web's w-11) and value on one
 * baseline; when that does not fit [room], each label (7.5sp) above its value.
 */
@Composable
private fun CanChiRows(rows: List<Pair<String, String>>, room: Dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em, fontFamily = BeVietnamPro)
    val valueStyle = TextStyle(fontSize = 16.8.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlayfairDisplay)
    val widest = rows.maxOf { measurer.measure(it.first, labelStyle).size.width }
    val labelWidth = maxOf(with(density) { widest.toDp() } + 2.dp, 44.dp)
    // The longest of the 60 can chi ("Nhâm Thân"), not today's: the layout does not change from day to day (as the web).
    val valueWidth = remember(measurer, density) {
        val cal = CalendarData.calendar
        with(density) { (0 until 60).maxOf { measurer.measure("${cal.can[it % 10]} ${cal.chi[it % 12]}", valueStyle).size.width }.toDp() }
    }
    if (labelWidth + 12.dp + valueWidth > room) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { (label, value) ->
                Column {
                    Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 7.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em, maxLines = 1, softWrap = false)
                    CanChiValue(value)
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CanChiLabel(label, Modifier.width(labelWidth).alignByBaseline())
                CanChiValue(value, Modifier.alignByBaseline())
            }
        }
    }
}
