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
    val close = { state.sheet = null }
    when (state.sheet) {
        SheetKind.MONTH_PICKER -> MonthYearPickerSheet(state, close)
        SheetKind.QUICK_DATE -> QuickDateSheet(state, close)
        SheetKind.AUSPICIOUS -> AuspiciousSheet(state, close)
        SheetKind.LUNAR_EVENTS -> LunarEventsSheet(state, close)
        SheetKind.ABOUT -> AboutSheet(close)
        SheetKind.BIRTH_DATE -> BirthDateSheet(state, close)
        null -> Unit
    }
}

/** Dashed rounded-2xl border (the web's `border border-dashed border-line`). */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

/**
 * The web's Sheet: grab handle (40×6, surface-3), header px-5 pt-2 pb-3 (icon tile, title
 * text-lg bold or a custom heading, subtitle text-xs, close icon-btn on surface-2), body
 * px-5 pb-5, footer px-5 pt-3 with a top border.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(
    title: String,
    onClose: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    heading: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalLichAmColors.current
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
        Column(Modifier.navigationBarsPadding()) {
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
        Text(text, color = if (active) c.onBrand else if (current) c.brand else c.fg, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun <T> Grid(items: List<T>, cols: Int, gap: Dp, cell: @Composable (T, Modifier) -> Unit) {
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
    var selected by remember { mutableStateOf(state.selectedDate) }
    var view by remember { mutableStateOf(SolarDate(selected.year, selected.month, 1)) }
    val lunar = LunarCalendar.solarToLunar(selected)
    val today = AppState.today()
    fun pick(d: SolarDate) { if (d.isInRange()) { selected = d; view = SolarDate(d.year, d.month, 1) } }
    // Like JS Date.setDate/setMonth: an overflowing day rolls into the next month.
    fun solarChange(day: Int = selected.day, month: Int = selected.month, year: Int = selected.year) = pick(SolarDate(year, month, 1).plusDays((day - 1).toLong()))
    fun lunarChange(day: Int = lunar.day, month: Int = lunar.month, year: Int = lunar.year) {
        var r = Conversion.lunarToSolar(day, month, year, lunar.leap)
        if (r !is LunarToSolar.Ok && lunar.leap) r = Conversion.lunarToSolar(day, month, year, false)
        if (r is LunarToSolar.Ok) pick(r.solar)
    }
    val years = (MIN_YEAR..MAX_YEAR).toList()
    SheetFrame(
        "Đổi ngày âm – dương", onClose, icon = Icons.Outlined.CalendarMonth, subtitle = "Chọn một ngày để xem lịch",
        footer = {
            PrimaryButton("Xem ngày ${selected.day}/${selected.month}/${selected.year}", {
                state.selectDate(selected); state.tab = Tab.CALENDAR; onClose()
            }, Modifier.fillMaxWidth(), tall = true)
        },
    ) {
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tháng ${view.month}, ${view.year}", color = c.fg, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = HeadingTracking, modifier = Modifier.weight(1f))
            IconBtn(Icons.Filled.ChevronLeft, "Tháng trước", { view = view.plusMonths(-1) }, iconSize = 18.dp)
            IconBtn(Icons.Filled.ChevronRight, "Tháng sau", { view = view.plusMonths(1) }, iconSize = 18.dp)
        }
        Row(Modifier.fillMaxWidth()) {
            CalendarData.calendar.weekDays.forEach { w ->
                Text(w, Modifier.weight(1f).padding(bottom = 4.dp), color = if (w == "CN") c.lunar else c.fg3, fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            monthGrid(view.year, view.month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val info = DayInfo.orNull(d)
                        val sel = d == selected
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.width(40.dp).height(44.dp).clip(R12)
                                    .background(if (sel) c.brand else Color.Transparent)
                                    .then(if (!sel && d == today) Modifier.border(2.dp, c.brand, R12) else Modifier)
                                    .clickable(enabled = info != null) { pick(d) }
                                    .alpha(if (d.month != view.month) 0.3f else 1f),
                                // Same spacing as the month grid's day cells: no line leading, 2dp between the numbers.
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                            ) {
                                // Fixed 40×44 cell: capped like the month grid.
                                val solarFs = cappedSp(14f)
                                val lunarFs = cappedSp(9f)
                                Text("${d.day}", color = if (sel) c.onBrand else if (d.isSunday) c.lunar else c.fg, fontSize = solarFs, lineHeight = solarFs, fontWeight = FontWeight.SemiBold, modifier = Modifier.leadingNone(solarFs))
                                if (info != null) {
                                    val l = info.lunar
                                    Text(
                                        if (l.day == 1) "1/${l.month}" else "${l.day}",
                                        color = if (sel) c.onBrand.copy(alpha = 0.8f) else if (l.day == 1 || l.day == 15) c.lunar else c.fg3,
                                        fontSize = lunarFs, lineHeight = lunarFs, fontWeight = if (!sel && (l.day == 1 || l.day == 15)) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.leadingNone(lunarFs),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        VSpace(16.dp)
        SectionLabel("Dương lịch", icon = Icons.Outlined.WbSunny)
        VSpace(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectField(selected.day, (1..31).toList(), { "$it" }, { solarChange(day = it) }, Modifier.weight(1f))
            SelectField(selected.month, (1..12).toList(), { "Th $it" }, { solarChange(month = it, day = minOf(selected.day, daysInMonth(selected.year, it))) }, Modifier.weight(1.2f))
            SelectField(selected.year, years, { "$it" }, { solarChange(year = it, day = minOf(selected.day, daysInMonth(it, selected.month))) }, Modifier.weight(1.4f))
        }
        VSpace(12.dp)
        SectionLabel("Âm lịch" + if (lunar.leap) " (tháng nhuận)" else "", icon = Icons.Filled.NightsStay)
        VSpace(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectField(lunar.day, (1..30).toList(), { "$it" }, { lunarChange(day = it) }, Modifier.weight(1f))
            SelectField(lunar.month, (1..12).toList(), { "Th $it" }, { lunarChange(month = it) }, Modifier.weight(1.2f))
            SelectField(lunar.year, years, { "$it" }, { lunarChange(year = it) }, Modifier.weight(1.4f))
        }
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
// Xuất lịch âm (LunarEventModal)
// ---------------------------------------------------------------------------

@Composable
private fun LunarEventsSheet(state: AppState, onClose: () -> Unit) {
    val c = LocalLichAmColors.current
    val context = LocalContext.current
    var showForm by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(1) }
    var month by remember { mutableIntStateOf(1) }
    var leap by remember { mutableStateOf(false) }
    var yearCount by remember { mutableIntStateOf(5) }
    var result by remember { mutableStateOf<IcsResult?>(null) }
    var pending by remember { mutableStateOf<IcsResult?>(null) }
    val startYear = AppState.today().year
    val all = state.exportEvents
    val total = yearCount * all.size

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val r = pending ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(r.text.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { result = r }
                .onFailure { Toast.makeText(context, "Không ghi được file", Toast.LENGTH_SHORT).show() }
        }
        pending = null
    }
    fun resetForm() { editingId = null; title = ""; day = 1; month = 1; leap = false; showForm = false }

    SheetFrame(
        "Xuất lịch âm", onClose, icon = Icons.Outlined.EventNote, subtitle = "Tạo file .ics cho Google Calendar, Apple Calendar, Outlook",
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
            Column {
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Sự kiện riêng (${state.lunarEvents.size})", Modifier.weight(1f))
                    if (!showForm) SoftButton("Thêm", { resetForm(); showForm = true }, icon = Icons.Filled.Add)
                }
                if (state.lunarEvents.isEmpty() && !showForm) {
                    Box(Modifier.fillMaxWidth().dashedBorder(c.line).padding(horizontal = 16.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Thêm giỗ, sinh nhật âm lịch… để xuất cùng lúc.", color = c.fg2, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                    }
                }
                if (state.lunarEvents.isNotEmpty()) {
                    Column(Modifier.clip(R16).background(c.surface2)) {
                        state.lunarEvents.forEachIndexed { i, e ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().background(if (editingId == e.id) c.brandSoft else Color.Transparent).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                    Text(e.title, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Âm ${e.lunarDay}/${e.lunarMonth}${if (e.isLeapMonth) " · nhuận" else ""}", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                                IconBtn(Icons.Outlined.Edit, "Chỉnh sửa", {
                                    editingId = e.id; title = e.title; day = e.lunarDay; month = e.lunarMonth; leap = e.isLeapMonth; showForm = true; result = null
                                }, iconSize = 16.dp)
                                IconBtn(Icons.Outlined.Delete, "Xoá", { state.deleteLunarEvent(e.id); if (editingId == e.id) resetForm(); result = null }, iconSize = 16.dp)
                            }
                        }
                    }
                }
                if (showForm) {
                    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(R16).background(c.surface2).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (editingId != null) "Sửa sự kiện" else "Sự kiện mới", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                        WebTextField(title, { title = it }, "Tên sự kiện, vd: Giỗ ông nội", singleLine = true, bg = c.surface)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectField(day, (1..30).toList(), { "Ngày $it" }, { day = it }, Modifier.weight(1f), bg = c.surface)
                            SelectField(month, (1..12).toList(), { "Tháng $it" }, { month = it }, Modifier.weight(1f), bg = c.surface)
                        }
                        Row(Modifier.clickable { leap = !leap }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(leap, { leap = it }, colors = CheckboxDefaults.colors(checkedColor = c.brand, checkmarkColor = c.onBrand, uncheckedColor = c.fg3))
                            Text(
                                buildAnnotatedString {
                                    append("Tháng nhuận ")
                                    pushStyle(SpanStyle(color = c.fg2, fontSize = 12.sp)); append("(chỉ chọn nếu đúng là tháng nhuận)"); pop()
                                },
                                color = c.fg, fontSize = 14.sp, lineHeight = 20.sp,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            GhostButton("Huỷ", { resetForm() }, bg = c.surface)
                            PrimaryButton("Lưu", {
                                if (title.isNotBlank()) { state.saveLunarEvent(editingId, title.trim(), day, month, leap); resetForm(); result = null }
                            }, enabled = title.isNotBlank(), modifier = Modifier.width(88.dp))
                        }
                    }
                }
            }
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
                        Text("${all.size} sự kiện × $yearCount năm. Mỗi năm có ngày dương lịch riêng.", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
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
