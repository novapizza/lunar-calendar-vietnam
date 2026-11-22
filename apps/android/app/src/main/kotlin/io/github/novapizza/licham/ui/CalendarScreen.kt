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
import androidx.compose.foundation.rememberScrollState
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

private data class Cell(val date: SolarDate, val info: DayInfo?, val hasNote: Boolean, val clash: Boolean)

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
    val noteDates = remember(state.notes) { state.notes.map { it.date }.toSet() }
    val userChi = state.userChi
    val cells = remember(m, noteDates, userChi) {
        monthGrid(m.year, m.month).map { d ->
            val info = DayInfo.orNull(d)
            Cell(d, info, d.toString() in noteDates, info != null && Zodiac.clashes(userChi, info))
        }
    }
    if (!wide && LocalHinge.current == null) {
        Page(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp), gap = 12.dp) {
            CalendarHeader(state, wide)
            MonthGrid(state, cells, userChi != null, wide)
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
private fun CalendarHeader(state: AppState, wide: Boolean) {
    val c = LocalLichAmColors.current
    val m = state.shownMonth
    val today = AppState.today()
    val showingThisMonth = m.year == today.year && m.month == today.month
    // text-[clamp(1.25rem,6.4vw,1.5rem)], text-3xl with the rail.
    val vw = LocalConfiguration.current.screenWidthDp
    val titleSize = if (wide) 30f else (vw * 0.064f).coerceIn(20f, 24f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Modifier.bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp).padding(top = 8.dp),
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
private fun MonthGrid(state: AppState, cells: List<Cell>, showClash: Boolean, wide: Boolean) {
    val c = LocalLichAmColors.current
    val m = state.shownMonth
    val today = AppState.today()
    val gap = if (wide) 4.dp else 2.dp
    // Phones: -mx-2 p-1 (8px gutter, 4px padding); with the rail p-3.
    Card(
        padding = if (wide) 12.dp else 4.dp,
        modifier = (if (wide) Modifier else Modifier.bleed(8.dp)).swipe(
            onNext = { m.plusMonths(1).let { state.showMonth(it.year, it.month) } },
            onPrev = { m.plusMonths(-1).let { state.showMonth(it.year, it.month) } },
        ),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CalendarData.calendar.weekDays.forEach { w ->
                Text(w, Modifier.weight(1f).padding(vertical = 8.dp), color = if (w == "CN") c.lunar else c.fg3, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(top = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    week.forEach { cell ->
                        DayCell(cell, Modifier.weight(1f), selected = cell.date == state.selectedDate, today = cell.date == today, inMonth = cell.date.month == m.month) { state.selectDate(cell.date) }
                    }
                }
            }
        }
        // Legend: pt-3 pb-1, gap-x-4, 11px.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            Legend(c.good, "Ngày tốt")
            Legend(c.bad.copy(alpha = 0.7f), "Ngày xấu")
            Legend(c.gold, "Ghi chú")
            if (showClash) Legend(c.clash, "Xung tuổi")
        }
    }
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
                if (cell.hasNote) Box(Modifier.align(Alignment.CenterEnd).offset(x = 7.dp)) { Dot(c.gold) }
            }
            if (info != null) {
                val firstDay = info.lunar.day == 1
                val special = firstDay || info.lunar.day == 15 || info.holiday != null
                Box {
                    Text(
                        if (firstDay) "${info.lunar.day}/${info.lunar.month}" else "${info.lunar.day}",
                        color = if (special) c.lunar else c.fg3,
                        fontSize = lunarFs, lineHeight = lunarFs,
                        fontWeight = if (special) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.leadingNone(lunarFs),
                    )
                    if (info.rating != Rating.NEUTRAL || cell.clash) {
                        Column(
                            Modifier.align(Alignment.CenterEnd).offset(x = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            if (info.rating == Rating.GOOD) Dot(c.good)
                            if (info.rating == Rating.BAD) Dot(c.bad.copy(alpha = 0.7f))
                            if (cell.clash) Dot(c.clash)
                        }
                    }
                }
                if (w >= 88.dp && info.holiday != null) {
                    Text(info.holiday!!, color = c.lunar, fontSize = cappedSp(10f), lineHeight = cappedSp(12f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = w - 12.dp))
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
    val dayLabel = if (info.holiday != null || info.observances.isNotEmpty()) null else lunarDayLabel(info.lunar)
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
    Activities(info)
    GioHoangDaoCard(info, if (d == AppState.today()) DayFacts.chiOfHour(now.hour) else null)
    NotesCard(state.notesOn(d), { state.addNote(d, it) }, state::deleteNote, compact = true)
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
