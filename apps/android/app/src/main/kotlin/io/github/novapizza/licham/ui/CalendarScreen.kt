package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.SheetKind
import io.github.novapizza.licham.core.SmallDate
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.DayFacts
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.EventKind
import io.github.novapizza.licham.core.Rating
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.Zodiac
import io.github.novapizza.licham.core.isSunday
import io.github.novapizza.licham.core.lunarDayLabel
import io.github.novapizza.licham.core.lunarMonthSubtitle
import io.github.novapizza.licham.core.monthGrid
import io.github.novapizza.licham.core.orNull
import io.github.novapizza.licham.core.plusMonths
import io.github.novapizza.licham.core.weekOfYear
import java.time.LocalDateTime

/**
 * [personal]: the first of the user's own events that day (its name in large cells); [dot]: the
 * colour token of the dot beside the solar day, the first event's colour, gold for a note alone (null: none).
 */
private data class Cell(val date: SolarDate, val info: DayInfo?, val dot: String?, val clash: Boolean, val personal: String? = null, val personalColor: String? = null)

/** Widens a child by [by] on both sides (the web's negative margin, -mx-2). */
fun Modifier.bleed(by: Dp) = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

/**
 * Lịch (views/CalendarView.tsx): header, month grid (solar day, lunar day, dots for good/bad
 * day, note, zodiac clash), then the selected day's panel. Wide: grid left, panel right.
 */
@Composable
fun CalendarScreen(state: AppState, now: LocalDateTime, wide: Boolean) {
    val m = state.shownMonth
    val userChi = state.userChi
    val personal = state.lunarEvents
    val cells = remember(m, userChi, personal) {
        monthGrid(m.year, m.month).map { d ->
            val info = DayInfo.orNull(d)
            val own = if (info != null) PersonalEvents.on(personal, d) else emptyList()
            val first = own.firstOrNull()
            val dot = first?.color
            Cell(d, info, dot, info != null && Zodiac.clashes(userChi, info), first?.title, first?.color)
        }
    }
    if (!wide && LocalHinge.current == null) {
        // One column: the header stays at the top and the month folds up under it to two week
        // rows as the page scrolls (WeekFold), as the web's useStickyWeeks.
        val scroll = rememberScrollState()
        val fold = remember { WeekFold() }
        val selectedIndex = cells.indexOfFirst { it.date == state.selectedDate }
        fold.rows = cells.size / 7
        fold.first = fold.pairFor(if (selectedIndex < 0) -1 else selectedIndex / 7, cells.size / 7)
        Page(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp), gap = 12.dp, scroll = scroll) {
            val c = LocalLichAmColors.current
            CalendarHeader(
                state, wide,
                Modifier
                    .onPlaced { fold.headerTop = it.positionInParent().y; fold.headerH = it.size.height.toFloat() }
                    .zIndex(2f)
                    // Scrolls up by the chip row's height, then stays (the title row is held: WeekFold).
                    .graphicsLayer { translationY = scroll.value - fold.chipsHidden(scroll.value.toFloat()) }
                    // Opaque from the screen's top edge (the page's 12dp padding) across the gutters.
                    .drawBehind {
                        val side = 16.dp.toPx()
                        val top = 12.dp.toPx()
                        drawRect(c.bg, Offset(-side, -top), Size(size.width + 2 * side, size.height + top))
                    },
                fold to scroll,
            )
            MonthGrid(state, cells, userChi != null, wide, fold to scroll)
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { SelectedDayPanel(state, now) }
        }
    } else {
        TwoPanes(
            1.25f, 1f, top = if (wide) 24.dp else 12.dp, bottom = if (wide) 24.dp else 40.dp, gap = 12.dp,
            left = {
                CalendarHeader(state, wide)
                MonthGrid(state, cells, userChi != null, wide)
            },
            right = { SelectedDayPanel(state, now) },
        )
    }
}

@Composable
private fun CalendarHeader(state: AppState, wide: Boolean, modifier: Modifier = Modifier, folding: Pair<WeekFold, ScrollState>? = null) {
    val c = LocalLichAmColors.current
    val m = state.shownMonth
    val today = AppState.today()
    val showingThisMonth = m.year == today.year && m.month == today.month
    // text-[clamp(1.25rem,6.4vw,1.5rem)], text-3xl with the rail.
    val vw = LocalConfiguration.current.screenWidthDp
    val titleSize = if (wide) 30f else (vw * 0.064f).coerceIn(20f, 24f)
    val fold = folding?.first
    val s = { folding?.second?.value?.toFloat() ?: 0f }
    if (fold != null) with(LocalDensity.current) { fold.keepGap = 12.dp.toPx() }
    Column(modifier) {
        Row(
            if (fold == null) Modifier
            else Modifier.zIndex(1f)
                .graphicsLayer { translationY = fold.chipsHidden(s()) }
                // Opaque above the title row and across the gutters: the chips slide away under it.
                .drawBehind {
                    val side = 16.dp.toPx()
                    drawRect(c.bg, Offset(-side, -20 * size.height), Size(size.width + 2 * side, 21 * size.height))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f).clip(R12)
                    .semantics(mergeDescendants = true) { contentDescription = "Chọn tháng, năm" }
                    .clickable { state.sheet = SheetKind.MONTH_PICKER }.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tháng ${m.month} ", color = c.fg, fontSize = titleSize.sp, lineHeight = (titleSize * 1.33f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = HeadingTracking, maxLines = 1)
                    Text("${m.year}", color = c.fg3, fontSize = titleSize.sp, lineHeight = (titleSize * 1.33f).sp, fontWeight = FontWeight.Bold, letterSpacing = HeadingTracking, maxLines = 1)
                    Icon(Icons.Filled.ExpandMore, null, Modifier.size(20.dp).padding(top = 2.dp), tint = c.fg3)
                }
                Text(lunarMonthSubtitle(m.year, m.month), color = c.fg2, fontSize = if (wide) 14.sp else 12.sp, lineHeight = if (wide) 20.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (wide && !showingThisMonth) {
                Text("Hôm nay", color = c.brand, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(R12).background(c.brandSoft).clickable { state.selectDate(today) }.padding(horizontal = 12.dp, vertical = 8.dp))
                HSpace(8.dp)
            }
            IconBtn(Icons.Filled.ChevronLeft, "Tháng trước", { m.plusMonths(-1).let { state.showMonth(it.year, it.month) } })
            IconBtn(Icons.Filled.ChevronRight, "Tháng sau", { m.plusMonths(1).let { state.showMonth(it.year, it.month) } })
        }
        // Chip row (mt-3), scrolling sideways; shadows need a little room.
        Row(
            (
                if (fold == null) Modifier
                else Modifier.onSizeChanged { fold.chipsH = it.height.toFloat() }
                    .graphicsLayer { alpha = fold.chipsFold.let { h -> if (h > 0f) 1 - fold.chipsHidden(s()) / h else 1f } }
                ).bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!wide && !showingThisMonth) Chip("Hôm nay", Icons.AutoMirrored.Filled.Undo, { state.selectDate(today) }, textColor = c.brand)
            Chip("Tìm ngày tốt", Icons.Outlined.Search, { state.sheet = SheetKind.AUSPICIOUS })
            Chip("Đổi ngày", Icons.Outlined.CalendarMonth, { state.sheet = SheetKind.QUICK_DATE })
            Chip("Xuất lịch", Icons.Outlined.FileDownload, { state.sheet = SheetKind.LUNAR_EVENTS })
        }
    }
}

@Composable
private fun MonthGrid(state: AppState, cells: List<Cell>, showClash: Boolean, wide: Boolean, folding: Pair<WeekFold, ScrollState>? = null) {
    val c = LocalLichAmColors.current
    val m = state.shownMonth
    val today = AppState.today()
    val gap = if (wide) 4.dp else 2.dp
    val pad = if (wide) 12.dp else 4.dp
    val fold = folding?.first
    val scroll = folding?.second
    if (fold != null) with(LocalDensity.current) { fold.pad = pad.toPx(); fold.gap = gap.toPx() }
    val s = { scroll?.value?.toFloat() ?: 0f }
    // Phones: p-1 (4px padding), as wide as the cards below (the 8dp bleed made it wider than
    // them, user 2026-11-23); with the rail p-3.
    val tint = c.shadowTint
    Card(
        padding = pad,
        modifier = (
            if (fold == null) Modifier
            else Modifier
                .onPlaced { fold.cardY = it.positionInParent().y }
                .zIndex(1f)
                // Stays right under the header once it gets there, and gets shorter: the card's
                // shape (corners and shadow as webShadow's) loses `folded` at the bottom and
                // clips the content to it. The page's colour behind the whole card, the page's
                // 16dp gutters beside it and 16dp under it keeps the usual gap, and neither the
                // cards scrolling up under it nor their shadows show outside its rounded corners.
                .graphicsLayer { translationY = fold.cardShift(s()) }
                .drawBehind {
                    val bottom = size.height - fold.folded(s())
                    val side = 16.dp.toPx()
                    drawRect(c.bg, Offset(-side, 0f), Size(size.width + 2 * side, bottom + 16.dp.toPx()))
                }
                .graphicsLayer {
                    shape = ShorterCardShape(fold.folded(s()), 20.dp.toPx())
                    clip = true
                    shadowElevation = 3.dp.toPx()
                    ambientShadowColor = tint.copy(alpha = 0.06f)
                    spotShadowColor = tint.copy(alpha = 0.14f)
                }
            ).swipe(
            onNext = { m.plusMonths(1).let { state.showMonth(it.year, it.month) } },
            onPrev = { m.plusMonths(-1).let { state.showMonth(it.year, it.month) } },
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().then(
                if (fold == null) Modifier
                else Modifier.zIndex(1f)
                    // Covers everything above the labels too (the card's clip bounds it): the rows
                    // moving up under them never show above them.
                    .drawBehind { drawRect(c.surface, Offset(0f, -20 * size.height), Size(size.width, 21 * size.height)) },
            ),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            CalendarData.calendar.weekDays.forEach { w ->
                Text(w, Modifier.weight(1f).padding(vertical = 8.dp), color = if (w == "CN") c.lunar else c.fg3, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(top = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            cells.chunked(7).forEachIndexed { row, week ->
                Row(
                    Modifier.fillMaxWidth().then(
                        if (fold == null) Modifier
                        else Modifier
                            .then(if (row == 0) Modifier.onSizeChanged { fold.rowH = it.height.toFloat() } else Modifier)
                            .graphicsLayer {
                                val t = fold.progress(s())
                                translationY = fold.rowShift(t)
                                alpha = if (row == fold.first || row == fold.first + 1) 1f else 1 - t
                            },
                    ),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    week.forEach { cell ->
                        DayCell(cell, Modifier.weight(1f), selected = cell.date == state.selectedDate, today = cell.date == today, inMonth = cell.date.month == m.month) { state.selectDate(cell.date) }
                    }
                }
            }
        }
        // Legend: pt-3 pb-1, gap-x-4, 11px. While the card gets shorter it fades with the rows.
        Row(
            Modifier.fillMaxWidth().then(
                if (fold == null) Modifier
                else Modifier.onSizeChanged { fold.legendH = it.height.toFloat() }.graphicsLayer { alpha = 1 - fold.progress(s()) },
            ).padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            Legend(c.good, "Ngày tốt")
            Legend(c.bad.copy(alpha = 0.7f), "Ngày xấu")
            Legend(c.gold, "Sự kiện")
            if (showClash) Legend(c.clash, "Xung tuổi")
        }
    }
}

/**
 * The month card getting shorter under the sticky header as the Calendar page scrolls (user,
 * 2026-11-23), as the web's hooks/useStickyWeeks.ts: the same card, corners, labels and legend,
 * with only two week rows left, one of them the selected day's week (the next one with it, the
 * one before when it is the last; kept while the selection moves between the two).
 *
 * The header's chip row goes first: the header scrolls up by its height while its title row is
 * held in place and covers it as it fades. Then the card stays right under the header and, over
 * the next K px of scrolling (K = all rows but two, and the legend), by `folded` = 0..K, loses
 * that much at the bottom: the rows move up by t·first·step (t = folded/K) so rows [first,
 * first + 1] end under the labels, and the other rows and the legend fade. Px, in the page
 * column's coordinates.
 */
/** The card without its bottom [cut] px, all four corners rounded by [radius] px. */
private class ShorterCardShape(private val cut: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, (size.height - cut).coerceAtLeast(0f), CornerRadius(radius)))
}

@Stable
private class WeekFold {
    var headerTop by mutableFloatStateOf(0f)
    var headerH by mutableFloatStateOf(0f)
    var cardY by mutableFloatStateOf(0f)
    var rowH by mutableFloatStateOf(0f)
    var legendH by mutableFloatStateOf(0f)
    /** The header's chip row (with its 12dp top padding): it hides first. */
    var chipsH by mutableFloatStateOf(0f)
    /** The chip row's top padding stays under the title row (the web header's pb-3). */
    var keepGap = 0f
    /** Px of scrolling over which the chip row slides away. */
    val chipsFold: Float get() = maxOf(0f, chipsH - keepGap)
    var pad = 0f
    var gap = 0f
    var rows by mutableIntStateOf(6)
    var first by mutableIntStateOf(0)
    private var pair = 0

    fun pairFor(selectedRow: Int, rows: Int): Int {
        var f = pair
        if (selectedRow >= 0 && selectedRow != f && selectedRow != f + 1) f = selectedRow
        f = f.coerceIn(0, maxOf(0, rows - 2))
        pair = f
        return f
    }

    private val step: Float get() = rowH + gap
    /** All rows but two, and the legend. */
    private val foldBy: Float get() = maxOf(0f, (rows - 2) * step + legendH)
    /** How much of the chip row has slid away under the title row at scroll s. */
    fun chipsHidden(s: Float): Float = s.coerceIn(0f, chipsFold)
    /** Scroll at which the card's top meets the header's bottom (the header without its chip row). */
    private val start: Float get() = cardY - (headerTop + headerH - chipsFold)
    fun folded(s: Float): Float = (s - start).coerceIn(0f, foldBy)
    fun progress(s: Float): Float = if (foldBy > 0f) folded(s) / foldBy else 0f
    fun rowShift(t: Float): Float = -t * first * step
    fun cardShift(s: Float): Float = maxOf(0f, s - start)
}

@Composable
private fun Legend(color: Color, label: String) {
    val c = LocalLichAmColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(Pill).background(color))
        HSpace(6.dp)
        Text(label, color = c.fg2, fontSize = 11.sp, lineHeight = 16.5.sp)
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(5.dp).clip(Pill).background(color))

/**
 * Square day cell: today = brand-soft fill, selected = brand outline (ring-2 inset);
 * solar 17px semibold (20/24px in wider cells), lunar 11px; dots 5px, 2px after the number.
 */
@Composable
private fun DayCell(cell: Cell, modifier: Modifier, selected: Boolean, today: Boolean, inMonth: Boolean, onClick: () -> Unit) {
    val c = LocalLichAmColors.current
    val info = cell.info
    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .clip(R12)
            .background(if (today) c.brandSoft else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, c.brand, R12) else Modifier)
            .clickable(enabled = info != null, onClickLabel = "${cell.date.day} tháng ${cell.date.month}", onClick = onClick)
            .alpha(if (inMonth) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        val w = maxWidth
        val solarSize = when { w >= 88.dp -> 24f; w >= 64.dp -> 20f; else -> 17f }
        val lunarSize = when { w >= 88.dp -> 14f; w >= 64.dp -> 12f; else -> 11f }
        // The cell is a fixed square: text follows the font setting only up to 1.3x.
        val solarFs = cappedSp(solarSize)
        val lunarFs = cappedSp(lunarSize)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box {
                Text(
                    "${cell.date.day}",
                    color = when { today -> c.brand; cell.date.isSunday -> c.lunar; else -> c.fg },
                    fontSize = solarFs, lineHeight = solarFs,
                    fontWeight = if (today || selected) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier.leadingNone(solarFs),
                )
                if (cell.dot != null) Box(Modifier.align(Alignment.CenterEnd).offset(x = 7.dp)) { Dot(c.eventColor(cell.dot)) }
            }
            if (info != null) {
                val firstDay = info.lunar.day == 1
                val special = firstDay || info.lunar.day == 15 || info.holiday != null
                val small = SmallDate.lunar(info.lunar, selected)
                val hasDots = info.rating != Rating.NEUTRAL || cell.clash
                val lunarText = @Composable {
                    SmallDateText(
                        small,
                        color = if (special) c.lunar else c.fg3,
                        fontSize = lunarFs,
                        fontWeight = if (special) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.leadingNone(lunarFs),
                    )
                }
                val dots = @Composable { modifier: Modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        if (info.rating == Rating.GOOD) Dot(c.good)
                        if (info.rating == Rating.BAD) Dot(c.bad.copy(alpha = 0.7f))
                        if (cell.clash) Dot(c.clash)
                    }
                }
                if (small.month != null && hasDots) {
                    // "17/6ⁿ" is too wide to hang the dots off a centred number: they ran into the
                    // cell's edge (emulator, 2026-11-24). Centre text and dots together, as the web does.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        lunarText()
                        dots(Modifier)
                    }
                } else {
                    Box {
                        lunarText()
                        if (hasDots) dots(Modifier.align(Alignment.CenterEnd).offset(x = 7.dp))
                    }
                }
                val cellName = info.holiday ?: cell.personal
                if (w >= 88.dp && cellName != null) {
                    Text(cellName, color = if (info.holiday != null) c.lunar else c.eventColor(cell.personalColor), fontSize = cappedSp(10f), lineHeight = cappedSp(12f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = w - 12.dp))
                }
            }
        }
    }
}

private data class RatingStyle(val icon: ImageVector, val label: String, val bg: Color, val fg: Color)

/** Tag: rounded-full good-soft px-2.5 py-1 text-xs semibold, icon 12. */
@Composable
private fun SelectedDayPanel(state: AppState, now: LocalDateTime) {
    val c = LocalLichAmColors.current
    val d = state.selectedDate
    val info = state.selectedInfo
    val (icon, label, bg, fg) = when (info.rating) {
        Rating.GOOD -> RatingStyle(Icons.Outlined.ThumbUp, "Ngày tốt", c.goodSoft, c.good)
        Rating.BAD -> RatingStyle(Icons.Outlined.ThumbDown, "Ngày xấu", c.badSoft, c.bad)
        Rating.NEUTRAL -> RatingStyle(Icons.Filled.Remove, "Ngày bình thường", c.surface2, c.fg2)
    }
    val clash = Zodiac.clashes(state.userChi, info)
    // Everything the Today page shows for a day, so the panel is the same day in short: its
    // commemorative days and festivals, and "Mồng 1" / "Ngày rằm" when the day has no name.
    val own = state.personalOn(d)
    val dayLabel = if (info.holiday != null || info.observances.isNotEmpty() || own.isNotEmpty()) null else lunarDayLabel(info.lunar)
    Card {
        SectionLabel("Ngày đã chọn · Tuần ${d.weekOfYear}")
        Column(Modifier.padding(top = 4.dp)) {
            Text("${d.weekdayName()}, ${d.day}/${d.month}", color = c.fg, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
            Row {
                Text("Âm lịch ", color = c.fg2, fontSize = 14.sp, lineHeight = 20.sp)
                Text("${info.lunar.day}/${info.lunar.month}", color = c.lunar, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                Text(" · tháng ${LUNAR_MONTH_NAMES[info.lunar.month - 1]}${if (info.lunar.leap) " nhuận" else ""}", color = c.fg2, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Năm" to info.canChiYear, "Tháng" to info.canChiMonth, "Ngày" to info.canChiDay).forEach { (l, v) ->
                Column(Modifier.weight(1f).clip(R12).background(c.surface2).padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l.uppercase(), color = c.fg3, fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.em)
                    Text(v, color = c.fg, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        // The day's rating with its Trực (as Today's "Ngày tốt / Trực …"), then its names.
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayNote(icon, label, "Trực ${info.truc}", fg, bg)
            info.holiday?.let { DayNote(Icons.Filled.Flag, it, null, c.lunar, c.lunarSoft) }
            info.observances.forEach { (title, kind) ->
                val festival = kind == EventKind.FESTIVAL
                DayNote(if (festival) Icons.Outlined.AccountBalance else Icons.Outlined.AutoAwesome, title, if (festival) "Lễ hội" else "Kỷ niệm", c.gold, c.goldSoft)
            }
            dayLabel?.let { DayNote(Icons.Outlined.DarkMode, it, null, c.lunar, c.surface2) }
            if (clash) DayNote(Icons.Outlined.SentimentDissatisfied, "Xung tuổi ${state.userChi}", "nên cân nhắc việc quan trọng", c.clash, c.clashSoft)
        }
    }
    DayEventsCard(state, d, compact = true)
    Activities(info)
    GioHoangDaoCard(info, if (d == AppState.today()) DayFacts.chiOfHour(now.hour) else null)
}

/** One line of the day panel: the rating and Trực, a holiday, commemorative day, festival, "Mồng 1" or a zodiac clash. */
@Composable
private fun DayNote(icon: ImageVector, title: String, detail: String?, fg: Color, bg: Color) {
    Row(Modifier.fillMaxWidth().clip(R12).background(bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = fg)
        HSpace(8.dp)
        Text(
            buildAnnotatedString {
                append(title)
                if (detail != null) withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = fg.copy(alpha = 0.75f))) { append(" · $detail") }
            },
            color = fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}
