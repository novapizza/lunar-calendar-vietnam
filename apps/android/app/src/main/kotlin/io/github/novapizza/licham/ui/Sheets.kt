package io.github.novapizza.licham.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.SheetKind
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.core.Auspicious
import io.github.novapizza.licham.core.AuspiciousDate
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.Conversion
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.Ics
import io.github.novapizza.licham.core.IcsResult
import io.github.novapizza.licham.core.LunarCalendar
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.core.LunarToSolar
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.daysInMonth
import io.github.novapizza.licham.core.isInRange
import io.github.novapizza.licham.core.isSunday
import io.github.novapizza.licham.core.monthGrid
import io.github.novapizza.licham.core.orNull
import io.github.novapizza.licham.core.plusDays
import io.github.novapizza.licham.core.plusMonths
import io.github.novapizza.licham.core.weekdayIndex

private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2099

/** Opens the sheet [AppState.sheet] names, if any. */
@Composable
fun AppSheets(state: AppState) {
    val close = { state.closeSheet() }
    when (state.sheet) {
        SheetKind.MONTH_PICKER -> MonthYearPickerSheet(state, close)
        SheetKind.QUICK_DATE -> QuickDateSheet(state, close)
        SheetKind.AUSPICIOUS -> AuspiciousSheet(state, close)
        SheetKind.LUNAR_EVENTS -> LunarEventsSheet(state, close)
        SheetKind.PERSONAL_EVENTS -> PersonalEventsSheet(state, close)
        SheetKind.EVENT_EDITOR -> EventEditorSheet(state, close)
        SheetKind.EVENT_DETAIL -> EventDetailSheet(state, close)
        SheetKind.ABOUT -> AboutSheet(close)
        SheetKind.BIRTH_DATE -> BirthDateSheet(state, close)
        null -> Unit
    }
}

/** Dashed rounded-2xl border (the web's `border border-dashed border-line`). */
internal fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

/**
 * The web's Sheet: grab handle (40×6, surface-3), header px-5 pt-2 pb-3 (icon tile, title
 * text-lg bold or a custom heading, subtitle text-xs, close icon-btn on surface-2), body
 * px-5 pb-5, footer px-5 pt-3 with a top border.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetFrame(
    title: String,
    onClose: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    heading: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    /**
     * Keep the height the sheet opened with: content that grows later (a date picker opening in
     * the event form) scrolls inside instead of stretching the sheet to full screen (user,
     * 2026-11-23). Sheets whose steps differ in height (year → month → day) leave it off.
     */
    keepHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalLichAmColors.current
    val density = LocalDensity.current
    var openedHeight by remember { mutableStateOf<Dp?>(null) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.surface,
        contentColor = c.fg,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).width(40.dp).height(6.dp).clip(Pill).background(c.surface3))
        },
    ) {
        Column(
            Modifier.navigationBarsPadding().then(
                if (!keepHeight) Modifier
                else Modifier
                    .then(openedHeight?.let { Modifier.heightIn(max = it) } ?: Modifier)
                    .onSizeChanged { if (openedHeight == null && it.height > 0) openedHeight = with(density) { it.height.toDp() } },
            ),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconTile(icon, c.brandSoft, c.brand, iconSize = 20.dp)
                    HSpace(12.dp)
                }
                Column(Modifier.weight(1f)) {
                    if (heading != null) heading()
                    else Text(title, color = c.fg, fontSize = 18.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.Bold, letterSpacing = HeadingTracking)
                    if (subtitle != null) Text(subtitle, color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
                }
                HSpace(12.dp)
                IconBtn(Icons.Filled.Close, "Đóng", onClose, bg = c.surface2, iconSize = 18.dp)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp), content = content)
            if (footer != null) {
                Divider()
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) { footer() }
            }
        }
    }
}

/** Month/year grid cell: h-14 rounded-2xl text-[15px] semibold; active brand + shadow, current ring. */
@Composable
private fun GridCell(text: String, active: Boolean, current: Boolean, enabled: Boolean = true, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLichAmColors.current
    Box(
        modifier
            .height(56.dp)
            .then(if (active) Modifier.webShadow(R16) else Modifier)
            .clip(R16)
            .background(if (active) c.brand else c.surface2)
            .then(if (!active && current) Modifier.border(2.dp, c.brand, R16) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (active) c.onBrand else if (current) c.brand else c.fg, fontSize = 15.sp, lineHeight = if ('\n' in text) 18.sp else 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun <T> Grid(items: List<T>, cols: Int, gap: Dp, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** "‹ title ▾ ›" heading of the month/year and birth date pickers. */
@Composable
private fun NavHeading(title: String, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onTitle: (() -> Unit)?, prevLabel: String, nextLabel: String) {
    val c = LocalLichAmColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.alpha(if (canPrev) 1f else 0.3f)) { IconBtn(Icons.Filled.ChevronLeft, prevLabel, { if (canPrev) onPrev() }) }
        Row(
            Modifier.weight(1f).padding(horizontal = 4.dp).clip(R12).then(if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = c.fg, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = HeadingTracking, maxLines = 1)
            if (onTitle != null) Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = c.fg3)
        }
        Box(Modifier.alpha(if (canNext) 1f else 0.3f)) { IconBtn(Icons.Filled.ChevronRight, nextLabel, { if (canNext) onNext() }) }
    }
}

private fun pageStartOf(year: Int) = MIN_YEAR + (year - MIN_YEAR) / 12 * 12

// ---------------------------------------------------------------------------
// Month / year picker (Lịch header)
// ---------------------------------------------------------------------------

@Composable
private fun MonthYearPickerSheet(state: AppState, onClose: () -> Unit) {
    val value = state.shownMonth
    val today = AppState.today()
    var yearMode by remember { mutableStateOf(false) }
    var viewYear by remember { mutableIntStateOf(value.year) }
    var pageStart by remember { mutableIntStateOf(pageStartOf(value.year)) }
    val pageEnd = minOf(pageStart + 11, MAX_YEAR)
    SheetFrame(
        title = if (yearMode) "Chọn năm" else "Chọn tháng", onClose = onClose,
        heading = {
            if (!yearMode) NavHeading("$viewYear", viewYear > MIN_YEAR, viewYear < MAX_YEAR, { viewYear-- }, { viewYear++ }, { pageStart = pageStartOf(viewYear); yearMode = true }, "Năm trước", "Năm sau")
            else NavHeading("$pageStart – $pageEnd", pageStart > MIN_YEAR, pageStart + 12 <= MAX_YEAR, { pageStart -= 12 }, { pageStart += 12 }, { yearMode = false }, "12 năm trước", "12 năm sau")
        },
    ) {
        if (!yearMode) {
            Grid((1..12).toList(), 3, 10.dp) { m, mod ->
                GridCell("Tháng $m", viewYear == value.year && m == value.month, viewYear == today.year && m == today.month, modifier = mod) {
                    state.showMonth(viewYear, m); onClose()
                }
            }
        } else {
            Grid((pageStart..pageStart + 11).toList(), 3, 10.dp) { y, mod ->
                GridCell("$y", y == viewYear, y == today.year, enabled = y <= MAX_YEAR, modifier = mod.alpha(if (y > MAX_YEAR) 0f else 1f)) { viewYear = y; yearMode = false }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Đổi ngày âm – dương (QuickDatePickerModal)
// ---------------------------------------------------------------------------

@Composable
private fun QuickDateSheet(state: AppState, onClose: () -> Unit) {
    val c = LocalLichAmColors.current
    val today = AppState.today()
    val todayLunar = LunarCalendar.solarToLunar(today)
    var selected by remember { mutableStateOf(state.selectedDate) }
    val lunar = LunarCalendar.solarToLunar(selected)
    // The two dates side by side; the one being picked has the brand ring, the app's
    // year → month → day grids below (as in "Chọn tháng" and "Chọn ngày sinh").
    var lunarCal by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(2) } // 0 year, 1 month, 2 day
    var viewYear by remember { mutableIntStateOf(selected.year) }
    // Solar: month 0–11 of viewYear; lunar: index in lunarMonthsOf(viewYear).
    var viewMonth by remember { mutableIntStateOf(selected.month - 1) }
    var pageStart by remember { mutableIntStateOf(pageStartOf(selected.year)) }
    val lunarMonths = remember(lunarCal, viewYear) { if (lunarCal) lunarMonthsOf(viewYear) else emptyList() }
    fun lastIndex(y: Int) = if (lunarCal) lunarMonthsOf(y).size - 1 else 11
    fun showIn(lunarMode: Boolean) {
        lunarCal = lunarMode
        mode = 2
        viewYear = if (lunarMode) lunar.year else selected.year
        viewMonth = if (lunarMode) lunarMonthsOf(lunar.year).indexOfFirst { it.month == lunar.month && it.leap == lunar.leap }.coerceAtLeast(0) else selected.month - 1
    }
    val shown = if (lunarCal) lunarMonths.getOrNull(minOf(viewMonth, lunarMonths.size - 1)) else null
    val pageEnd = minOf(pageStart + 11, MAX_YEAR)
    val title = when (mode) {
        0 -> "$pageStart – $pageEnd"
        1 -> "$viewYear"
        else -> if (!lunarCal) "Tháng ${viewMonth + 1}, $viewYear" else shown?.let { "Tháng ${it.month}${if (it.leap) " nhuận" else ""}, $viewYear" } ?: "$viewYear"
    }
    val canPrev = when (mode) { 0 -> pageStart > MIN_YEAR; 1 -> viewYear > MIN_YEAR; else -> viewYear > MIN_YEAR || viewMonth > 0 }
    val canNext = when (mode) { 0 -> pageStart + 12 <= MAX_YEAR; 1 -> viewYear < MAX_YEAR; else -> viewYear < MAX_YEAR || viewMonth < lastIndex(viewYear) }
    fun step(dir: Int) {
        when (mode) {
            0 -> pageStart += dir * 12
            1 -> viewYear += dir
            else -> when {
                dir < 0 && viewMonth == 0 -> { viewYear -= 1; viewMonth = lastIndex(viewYear) }
                dir > 0 && viewMonth == lastIndex(viewYear) -> { viewYear += 1; viewMonth = 0 }
                else -> viewMonth += dir
            }
        }
    }
    val unit = when (mode) { 0 -> "12 năm"; 1 -> "Năm"; else -> "Tháng" }
    val calName = if (lunarCal) "âm" else "dương"
    SheetFrame(
        "Đổi ngày âm – dương", onClose, icon = Icons.Outlined.CalendarMonth,
        footer = {
            PrimaryButton("Xem ngày ${selected.day}/${selected.month}/${selected.year}", {
                state.selectDate(selected); state.tab = Tab.CALENDAR; onClose()
            }, Modifier.fillMaxWidth(), tall = true)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateCard(!lunarCal, Icons.Outlined.WbSunny, "Dương lịch", "${selected.day}/${selected.month}/${selected.year}", selected.weekdayName(), Modifier.weight(1f)) { showIn(false) }
            DateCard(
                lunarCal, Icons.Filled.NightsStay, "Âm lịch", "${lunar.day}/${lunar.month}/${lunar.year}",
                (if (lunar.leap) "Tháng nhuận · " else "") + DayInfo.of(selected).canChiYear, Modifier.weight(1f),
            ) { showIn(true) }
        }
        VSpace(16.dp)
        NavHeading(
            title, canPrev, canNext, { step(-1) }, { step(1) },
            when (mode) { 2 -> ({ mode = 1 }); 1 -> ({ pageStart = pageStartOf(viewYear); mode = 0 }); else -> null },
            "$unit trước", "$unit sau",
        )
        Text(
            when (mode) { 0 -> "Chọn năm $calName lịch"; 1 -> "Chọn tháng $calName lịch"; else -> if (lunarCal) "Chọn ngày âm · số nhỏ là ngày dương" else "Chọn ngày dương · số nhỏ là ngày âm" },
            color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        )
        when (mode) {
            0 -> {
                val selYear = if (lunarCal) lunar.year else selected.year
                val curYear = if (lunarCal) todayLunar.year else today.year
                Grid((pageStart..pageStart + 11).toList(), 3, 10.dp) { y, mod ->
                    GridCell("$y", y == selYear, y == curYear, enabled = y <= MAX_YEAR, modifier = mod.alpha(if (y > MAX_YEAR) 0f else 1f)) { viewYear = y; mode = 1 }
                }
            }
            1 -> if (!lunarCal) {
                Grid((0..11).toList(), 3, 10.dp) { m, mod ->
                    GridCell("Tháng ${m + 1}", viewYear == selected.year && m + 1 == selected.month, viewYear == today.year && m + 1 == today.month, modifier = mod) { viewMonth = m; mode = 2 }
                }
            } else {
                Grid(lunarMonths.indices.toList(), 3, 10.dp) { i, mod ->
                    val m = lunarMonths[i]
                    GridCell(
                        "Tháng ${m.month}" + if (m.leap) "\nnhuận" else "",
                        viewYear == lunar.year && m.month == lunar.month && m.leap == lunar.leap,
                        viewYear == todayLunar.year && m.month == todayLunar.month && m.leap == todayLunar.leap,
                        modifier = mod,
                    ) { viewMonth = i; mode = 2 }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalendarData.calendar.weekDays.forEach { w ->
                        Text(w, Modifier.weight(1f), color = if (w == "CN") c.lunar else c.fg3, fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
                // One cell per day of the month on show: the big number in the calendar being
                // picked, the small one in the other ("1/m" on the first of a month).
                val days: List<SolarDate> = if (!lunarCal) (1..daysInMonth(viewYear, viewMonth + 1)).map { SolarDate(viewYear, viewMonth + 1, it) }
                else shown?.let { m -> (0 until m.days).map { m.first.plusDays(it.toLong()) } } ?: emptyList()
                val lead = days.firstOrNull()?.weekdayIndex ?: 0
                Grid(List<SolarDate?>(lead) { null } + days, 7, 4.dp) { d, mod ->
                    if (d == null) Spacer(mod.height(48.dp)) else {
                        val info = DayInfo.orNull(d)
                        val active = d == selected
                        Column(
                            mod.height(48.dp).clip(R12).background(if (active) c.brand else Color.Transparent)
                                .then(if (!active && d == today) Modifier.border(2.dp, c.brand, R12) else Modifier)
                                .clickable(enabled = info != null) { selected = d }.alpha(if (info != null) 1f else 0.3f),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            val l = info?.lunar
                            val big = if (lunarCal && l != null) l.day else d.day
                            val small = when {
                                l == null -> ""
                                !lunarCal -> if (l.day == 1) "1/${l.month}" else "${l.day}"
                                else -> if (d.day == 1) "1/${d.month}" else "${d.day}"
                            }
                            val mark = l != null && if (lunarCal) d.day == 1 else l.day == 1 || l.day == 15
                            val dayFs = cappedSp(15f)
                            val smallFs = cappedSp(10f)
                            Text("$big", color = if (active) c.onBrand else if (d.isSunday) c.lunar else c.fg, fontSize = dayFs, lineHeight = dayFs, fontWeight = FontWeight.SemiBold, modifier = Modifier.leadingNone(dayFs))
                            Text(
                                small, color = if (active) c.onBrand.copy(alpha = 0.8f) else if (mark) c.lunar else c.fg3,
                                fontSize = smallFs, lineHeight = smallFs, fontWeight = if (!active && mark) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 4.dp).leadingNone(smallFs),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One of Đổi ngày's two dates: label, the date in that calendar, a detail line. */
@Composable
private fun DateCard(active: Boolean, icon: ImageVector, label: String, value: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLichAmColors.current
    Column(
        modifier.clip(R16).background(if (active) c.brandSoft else c.surface2)
            .then(if (active) Modifier.border(2.dp, c.brand, R16) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        SectionLabel(label, icon = icon)
        Text(value, color = c.fg, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(detail, color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
    }
}

/** A lunar month of a lunar year: number, leap flag, solar first day, length. */
private data class LunarMonthInfo(val month: Int, val leap: Boolean, val first: SolarDate, val days: Int)

/** The 12 or 13 months of lunar year [year] in order, leaving out those outside the tables. */
private fun lunarMonthsOf(year: Int): List<LunarMonthInfo> {
    val months = runCatching { LunarCalendar.monthsOf(year) }.getOrNull() ?: return emptyList()
    val nextJd = runCatching { LunarCalendar.monthsOf(year + 1).first().jd }.getOrNull()
    return months.mapIndexedNotNull { i, m ->
        val end = months.getOrNull(i + 1)?.jd ?: nextJd ?: return@mapIndexedNotNull null
        val first = LunarCalendar.jdToDate(m.jd)
        if (first.isInRange()) LunarMonthInfo(m.month, m.leap, first, end - m.jd) else null
    }
}

// ---------------------------------------------------------------------------
// Tìm ngày tốt (AuspiciousDateModal)
// ---------------------------------------------------------------------------

@Composable
private fun AuspiciousSheet(state: AppState, onClose: () -> Unit) {
    val c = LocalLichAmColors.current
    var active by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<AuspiciousDate>>(emptyList()) }
    val icons = mapOf("WEDDING" to Icons.Outlined.FavoriteBorder, "GROUNDBREAKING" to Icons.Outlined.Handyman, "OPENING" to Icons.Outlined.Storefront)
    SheetFrame("Tìm ngày tốt", onClose, icon = Icons.Outlined.Search, subtitle = "Gợi ý trong 2 tháng tới") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Auspicious.ACTIVITIES.forEach { a ->
                val on = active == a.key
                Column(
                    Modifier.weight(1f).clip(R16).background(if (on) c.brand else c.surface2)
                        .clickable { active = a.key; results = Auspicious.find(a.key, AppState.today()) }.padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(icons.getValue(a.key), null, Modifier.size(20.dp), tint = if (on) c.onBrand else c.fg)
                    Text(a.label, color = if (on) c.onBrand else c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        VSpace(20.dp)
        if (results.isNotEmpty()) {
            SectionLabel("${results.size} ngày phù hợp")
            VSpace(8.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(R16).background(c.surface2)
                            .clickable { state.selectDate(item.date); state.tab = Tab.CALENDAR; onClose() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.size(48.dp).clip(R12).background(c.surface), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val dayFs = cappedSp(18f)
                            Text("${item.date.day}", color = c.brand, fontSize = dayFs, fontWeight = FontWeight.ExtraBold, lineHeight = dayFs, modifier = Modifier.leadingNone(dayFs))
                            Text("Th.${item.date.month}", color = c.fg2, fontSize = cappedSp(10f), lineHeight = cappedSp(14f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                        }
                        HSpace(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(item.date.longLabel(), color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Âm ${item.lunarDate} · Trực ${item.truc}", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        if (item.score == 10) {
                            Row(Modifier.clip(Pill).background(c.goodSoft).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(11.dp), tint = c.good); HSpace(4.dp)
                                Text("Rất hợp", color = c.good, fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.Bold)
                            }
                            HSpace(12.dp)
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), tint = c.fg3)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().dashedBorder(c.line).padding(horizontal = 24.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (active != null) "Không tìm thấy ngày phù hợp trong khoảng thời gian này." else "Chọn loại việc ở trên để xem các ngày tốt.",
                    color = c.fg2, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )
            }
        }
        VSpace(20.dp)
        Text("Dựa trên hệ thống Trực truyền thống. Chỉ mang tính chất tham khảo.", color = c.fg3, fontSize = 11.sp, lineHeight = 16.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------
// Xuất lịch (LunarEventModal)
// ---------------------------------------------------------------------------

@Composable
private fun LunarEventsSheet(state: AppState, onClose: () -> Unit) {
    val c = LocalLichAmColors.current
    val context = LocalContext.current
    var yearCount by remember { mutableIntStateOf(5) }
    var result by remember { mutableStateOf<IcsResult?>(null) }
    var pending by remember { mutableStateOf<IcsResult?>(null) }
    val startYear = AppState.today().year
    val all = state.exportEvents
    // VEVENTs written: one per rule, or one per occurrence (lunar repeats give 12-13 a year when monthly).
    val total = remember(all, startYear, yearCount) { Ics.count(all, startYear, yearCount) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val r = pending ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(r.text.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { result = r }
                .onFailure { Toast.makeText(context, "Không ghi được file", Toast.LENGTH_SHORT).show() }
        }
        pending = null
    }
    SheetFrame(
        "Xuất lịch", onClose, icon = Icons.Outlined.EventNote, subtitle = "Tạo file .ics cho Google Calendar, Apple Calendar, Outlook",
        footer = {
            PrimaryButton(
                if (all.isEmpty()) "Chọn sự kiện để xuất" else "Tải file .ics · $total mục",
                {
                    pending = Ics.build(all, startYear, yearCount)
                    createFile.launch(Ics.fileName(startYear, yearCount))
                },
                Modifier.fillMaxWidth(), enabled = all.isNotEmpty(), icon = Icons.Outlined.FileDownload, tall = true,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                SectionLabel("Ngày lễ có sẵn")
                VSpace(8.dp)
                Grid(CalendarData.presets.presets, 2, 8.dp) { p, mod ->
                    val on = p.id in state.enabledPresets
                    Box(
                        mod.clip(R16).background(if (on) c.brandSoft else c.surface2)
                            .then(if (on) Modifier.border(2.dp, c.brand, R16) else Modifier)
                            .clickable { state.togglePreset(p.id); result = null }.padding(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.emoji, fontSize = 24.sp, lineHeight = 24.sp, modifier = Modifier.leadingNone(24.sp))
                            Text(p.label, color = if (on) c.brand else c.fg, fontSize = 14.sp, lineHeight = 17.5.sp, fontWeight = FontWeight.Bold)
                            Text(p.description, color = c.fg2, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = (-2).dp).size(20.dp).clip(Pill).background(if (on) c.brand else Color.Transparent)
                                .then(if (!on) Modifier.border(2.dp, c.line, Pill) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = c.onBrand) }
                    }
                }
            }
            PersonalEventsList(state, "Thêm giỗ, sinh nhật… để xuất cùng lúc.")
            if (all.isNotEmpty()) {
                Column {
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Khoảng thời gian", Modifier.weight(1f))
                        Text("$startYear – ${startYear + yearCount - 1}", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.fillMaxWidth().clip(R16).background(c.surface2).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = yearCount.toFloat(), onValueChange = { yearCount = it.toInt(); result = null }, valueRange = 1f..30f, steps = 28,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = c.brand, activeTrackColor = c.brand, inactiveTrackColor = c.line,
                                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                                ),
                            )
                            HSpace(12.dp)
                            Text("$yearCount năm", color = c.brand, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                        }
                        Text("${all.size} sự kiện × $yearCount năm. Ngày âm lịch mỗi năm rơi vào ngày dương lịch khác.", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    result?.let { r ->
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth().clip(R16).background(c.goodSoft).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(17.dp), tint = c.good); HSpace(8.dp)
                                Text("Đã tạo ${r.totalEvents} sự kiện", color = c.good, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (r.skippedCount > 0) {
                                Column(Modifier.fillMaxWidth().clip(R16).background(c.goldSoft).padding(16.dp)) {
                                    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(14.dp), tint = c.gold); HSpace(8.dp)
                                        Text("Bỏ qua ${r.skippedCount} mục", color = c.fg, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                    r.skipped.forEach { s ->
                                        Text(
                                            buildAnnotatedString {
                                                append("• ")
                                                pushStyle(SpanStyle(color = c.fg, fontWeight = FontWeight.SemiBold)); append(s.title); pop()
                                                append(" năm âm ${s.lunarYear}" + if (s.reason == "leap_month_not_found") " — năm này không có tháng nhuận đó" else " — lỗi chuyển đổi ngày")
                                            },
                                            color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp,
                                        )
                                    }
                                }
                            }
                            Text("Google Calendar → Cài đặt → Nhập và xuất → chọn file .ics vừa tải", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** Two-way switch (Âm lịch / Dương lịch, Hằng năm / Hằng tháng), the web's Segmented. */
@Composable
internal fun Segmented(value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    val c = LocalLichAmColors.current
    Row(Modifier.fillMaxWidth().clip(R12).background(c.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (v, text) ->
            val on = v == value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) c.brand else Color.Transparent)
                    .clickable { onChange(v) }.padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, color = if (on) c.onBrand else c.fg2, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Thông tin ứng dụng (AboutModal)
// ---------------------------------------------------------------------------

@Composable
private fun AboutSheet(onClose: () -> Unit) {
    val c = LocalLichAmColors.current
    SheetFrame("Thông tin ứng dụng", onClose, icon = Icons.Outlined.Info, footer = { PrimaryButton("Đã hiểu", onClose, Modifier.fillMaxWidth(), tall = true) }) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                SectionLabel("Phương pháp tính toán")
                VSpace(8.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutPara("Âm lịch:", "theo bảng năm âm lịch tính sẵn của Hồ Ngọc Đức (giờ Việt Nam), xác định pha Mặt Trăng và 24 tiết khí theo đúng lịch truyền thống Việt Nam.")
                    AboutPara("Ngày tốt / xấu:", "đánh giá dựa trên Thập Nhị Trực (12 Trực).")
                    AboutPara("Xung tuổi:", "theo quy tắc Lục Xung của Địa Chi (Tý–Ngọ, Sửu–Mùi, Dần–Thân, Mão–Dậu, Thìn–Tuất, Tỵ–Hợi).")
                }
            }
            Column {
                SectionLabel("Công nghệ")
                VSpace(8.dp)
                Column(Modifier.fillMaxWidth().clip(R16).background(c.surface2)) {
                    listOf(
                        "Dữ liệu dùng chung" to "Bảng năm âm, ngày lễ, nạp âm, quy tắc ngày tốt: cùng nguồn với bản web.",
                        "Kotlin + Jetpack Compose" to "Xây dựng giao diện Android.",
                        "Be Vietnam Pro" to "Phông chữ (SIL Open Font License).",
                    ).forEachIndexed { i, (n, d) ->
                        if (i > 0) Divider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(n, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(d, color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            Text(
                "Thông tin ngày tốt/xấu chỉ mang tính chất tham khảo dựa trên tài liệu cổ học phương Đông. Hãy cân nhắc kỹ trước các quyết định quan trọng.",
                color = c.fg, fontSize = 12.sp, lineHeight = 19.5.sp, modifier = Modifier.fillMaxWidth().clip(R16).background(c.goldSoft).padding(16.dp),
            )
        }
    }
}

@Composable
private fun AboutPara(bold: String, rest: String) {
    val c = LocalLichAmColors.current
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(color = c.fg, fontWeight = FontWeight.Bold)); append("$bold "); pop()
            append(rest)
        },
        color = c.fg2, fontSize = 14.sp, lineHeight = 22.75.sp,
    )
}

// ---------------------------------------------------------------------------
// Ngày sinh: year → month → day (BirthDatePickerModal)
// ---------------------------------------------------------------------------

@Composable
private fun BirthDateSheet(state: AppState, onClose: () -> Unit) {
    val value = state.birthDate
    val today = AppState.today()
    var mode by remember { mutableIntStateOf(if (value != null) 2 else 0) } // 0 year, 1 month, 2 day
    var viewYear by remember { mutableIntStateOf(value?.year ?: 1990) }
    var viewMonth by remember { mutableIntStateOf(value?.month ?: 1) }
    var pageStart by remember { mutableIntStateOf(pageStartOf(value?.year ?: 1990)) }
    val maxYear = today.year
    val pageEnd = minOf(pageStart + 11, maxYear)
    fun monthAllowed(y: Int, m: Int) = SolarDate(y, m, daysInMonth(y, m)) >= LunarCalendar.FIRST_DAY && SolarDate(y, m, 1) <= today
    val prevMonth = SolarDate(viewYear, viewMonth, 1).plusMonths(-1)
    val nextMonth = SolarDate(viewYear, viewMonth, 1).plusMonths(1)
    val c = LocalLichAmColors.current
    SheetFrame(
        "Chọn ngày sinh", onClose,
        heading = {
            when (mode) {
                0 -> NavHeading("$pageStart – $pageEnd", pageStart > MIN_YEAR, pageStart + 12 <= maxYear, { pageStart -= 12 }, { pageStart += 12 }, null, "12 năm trước", "12 năm sau")
                1 -> NavHeading("$viewYear", viewYear > MIN_YEAR, viewYear < maxYear, { viewYear-- }, { viewYear++ }, { pageStart = pageStartOf(viewYear); mode = 0 }, "Năm trước", "Năm sau")
                else -> NavHeading(
                    "Tháng $viewMonth, $viewYear", monthAllowed(prevMonth.year, prevMonth.month), monthAllowed(nextMonth.year, nextMonth.month),
                    { viewYear = prevMonth.year; viewMonth = prevMonth.month }, { viewYear = nextMonth.year; viewMonth = nextMonth.month }, { mode = 1 }, "Tháng trước", "Tháng sau",
                )
            }
        },
        footer = if (value != null) ({
            Text(
                "Xoá ngày sinh", color = c.fg2, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(R12).clickable { state.updateBirthDate(null); onClose() }.padding(vertical = 10.dp),
            )
        }) else null,
    ) {
        Text(
            when (mode) { 0 -> "Chọn năm sinh (dương lịch)"; 1 -> "Chọn tháng sinh"; else -> "Chọn ngày sinh · số nhỏ là ngày âm" },
            color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
        when (mode) {
            0 -> Grid((pageStart..pageStart + 11).toList(), 3, 10.dp) { y, mod ->
                GridCell("$y", y == value?.year, false, enabled = y <= maxYear, modifier = mod.alpha(if (y > maxYear) 0f else 1f)) { viewYear = y; mode = 1 }
            }
            1 -> Grid((1..12).toList(), 3, 10.dp) { m, mod ->
                GridCell("Tháng $m", viewYear == value?.year && m == value.month, false, enabled = monthAllowed(viewYear, m), modifier = mod) { viewMonth = m; mode = 2 }
            }
            else -> {
                // Only the month's days, after blank leading cells (as on the web).
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalendarData.calendar.weekDays.forEach { w ->
                        Text(w, Modifier.weight(1f), color = if (w == "CN") c.lunar else c.fg3, fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
                val lead = SolarDate(viewYear, viewMonth, 1).weekdayIndex
                val cells: List<SolarDate?> = List(lead) { null } + (1..daysInMonth(viewYear, viewMonth)).map { SolarDate(viewYear, viewMonth, it) }
                Grid(cells, 7, 4.dp) { d, mod ->
                    if (d == null) Spacer(mod.height(48.dp)) else {
                        val info = DayInfo.orNull(d)
                        val active = d == value
                        val enabled = info != null && d <= today
                        Column(
                            mod.height(48.dp).clip(R12).background(if (active) c.brand else Color.Transparent)
                                .clickable(enabled = enabled) { state.updateBirthDate(d); onClose() }.alpha(if (enabled) 1f else 0.3f),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            val dayFs = cappedSp(15f)
                            Text("${d.day}", color = if (active) c.onBrand else c.fg, fontSize = dayFs, lineHeight = dayFs, fontWeight = FontWeight.SemiBold, modifier = Modifier.leadingNone(dayFs))
                            if (info != null) {
                                val l = info.lunar
                                Text(
                                    if (l.day == 1) "${l.day}/${l.month}" else "${l.day}",
                                    color = if (active) c.onBrand.copy(alpha = 0.8f) else if (l.day == 1) c.lunar else c.fg3,
                                    fontSize = cappedSp(10f), lineHeight = cappedSp(10f), fontWeight = if (!active && l.day == 1) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(top = 4.dp).leadingNone(cappedSp(10f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
