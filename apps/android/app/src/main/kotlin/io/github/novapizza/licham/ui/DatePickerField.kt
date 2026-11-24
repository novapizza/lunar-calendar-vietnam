package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.core.SmallDate
import io.github.novapizza.licham.core.LunarCalendar
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.daysInMonth
import io.github.novapizza.licham.core.isInRange
import io.github.novapizza.licham.core.weekdayIndex

private const val MIN_YEAR = 1901
private const val MAX_YEAR = 2099
private const val PAGE = 12

private enum class PickMode { DAY, MONTH, YEAR }

/**
 * A date as one line ("Thứ Hai, 23/11/2026 · 15/10 âm"); tapping it opens a month grid under it
 * with the lunar day under each day (as the web's DatePickerField), the title zooms out to months
 * and years, "Hôm nay" jumps back. Picking a day closes it.
 */
@Composable
fun DatePickerField(value: SolarDate, onPick: (SolarDate) -> Unit, label: String, min: SolarDate? = null) {
    val c = LocalLichAmColors.current
    var open by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(PickMode.DAY) }
    var viewYear by remember { mutableIntStateOf(value.year) }
    var viewMonth by remember { mutableIntStateOf(value.month) }
    var pageStart by remember { mutableIntStateOf(value.year - value.year % PAGE) }
    val today = AppState.today()
    fun pick(d: SolarDate) { onPick(d); open = false }
    fun allowed(d: SolarDate) = d.isInRange() && (min == null || d >= min)

    Column {
        Text(label, color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().clip(R12).background(c.surface2).clickable {
                if (!open) { viewYear = value.year; viewMonth = value.month; mode = PickMode.DAY }
                open = !open
            }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = c.brand)
            Spacer(Modifier.width(10.dp))
            val l = if (value.isInRange()) LunarCalendar.solarToLunar(value) else null
            Text(
                value.longLabel() + (l?.let { " · ${it.day}/${it.month}${if (it.leap) " nhuận" else ""} âm" } ?: ""),
                color = c.fg, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(20.dp), tint = c.fg3)
        }
        if (!open) return@Column

        Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(R16).background(c.surface2).padding(8.dp)) {
            // Header: ‹ title ›; the title zooms out (days → months → years).
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Filled.ChevronLeft, "Trước", {
                    when (mode) {
                        PickMode.YEAR -> pageStart = maxOf(MIN_YEAR - MIN_YEAR % PAGE, pageStart - PAGE)
                        PickMode.MONTH -> viewYear = maxOf(MIN_YEAR, viewYear - 1)
                        PickMode.DAY -> if (viewMonth == 1) { if (viewYear > MIN_YEAR) { viewYear--; viewMonth = 12 } } else viewMonth--
                    }
                }, iconSize = 20.dp)
                val title = when (mode) {
                    PickMode.YEAR -> "$pageStart – ${pageStart + PAGE - 1}"
                    PickMode.MONTH -> "$viewYear"
                    PickMode.DAY -> "Tháng $viewMonth, $viewYear"
                }
                Row(
                    Modifier.weight(1f).clip(R12).clickable(enabled = mode != PickMode.YEAR) {
                        if (mode == PickMode.DAY) mode = PickMode.MONTH else { pageStart = viewYear - viewYear % PAGE; mode = PickMode.YEAR }
                    }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, color = c.fg, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                    if (mode != PickMode.YEAR) Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = c.fg3)
                }
                IconBtn(Icons.Filled.ChevronRight, "Sau", {
                    when (mode) {
                        PickMode.YEAR -> pageStart = minOf(MAX_YEAR - MAX_YEAR % PAGE, pageStart + PAGE)
                        PickMode.MONTH -> viewYear = minOf(MAX_YEAR, viewYear + 1)
                        PickMode.DAY -> if (viewMonth == 12) { if (viewYear < MAX_YEAR) { viewYear++; viewMonth = 1 } } else viewMonth++
                    }
                }, iconSize = 20.dp)
            }

            when (mode) {
                PickMode.YEAR -> ChoiceGrid((0 until PAGE).map { pageStart + it }, { "$it" }, { it == value.year }, { it in MIN_YEAR..MAX_YEAR }) {
                    viewYear = it; mode = PickMode.MONTH
                }
                PickMode.MONTH -> ChoiceGrid((1..12).toList(), { "Th $it" }, { viewYear == value.year && it == value.month }, { true }) {
                    viewMonth = it; mode = PickMode.DAY
                }
                PickMode.DAY -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        WEEKDAY_SHORT.forEach { w ->
                            Text(w, color = if (w == "CN") c.lunar else c.fg3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    val first = SolarDate(viewYear, viewMonth, 1)
                    val cells: List<SolarDate?> = List(first.weekdayIndex) { null } + (1..daysInMonth(viewYear, viewMonth)).map { SolarDate(viewYear, viewMonth, it) }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 0 until 7) {
                                val d = week.getOrNull(i)
                                Box(Modifier.weight(1f).height(46.dp)) {
                                    if (d != null) DayCellPick(d, d == value, d == today, allowed(d)) { pick(d) }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (mode) { PickMode.DAY -> "Số nhỏ là ngày âm"; PickMode.MONTH -> "Chọn tháng"; PickMode.YEAR -> "Chọn năm" },
                    color = c.fg3, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f),
                )
                Text(
                    "Hôm nay", color = c.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alpha(if (allowed(today)) 1f else 0.3f).clip(R12)
                        .clickable(enabled = allowed(today)) { pick(today) }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DayCellPick(d: SolarDate, selected: Boolean, isToday: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalLichAmColors.current
    val l = LunarCalendar.solarToLunar(d)
    val bg = when { selected -> c.brand; isToday -> c.brandSoft; else -> Color.Transparent }
    Column(
        Modifier.fillMaxWidth().height(46.dp).clip(R12).background(bg).alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Ngày ${d.day} tháng ${d.month} năm ${d.year}, âm lịch ${l.day} tháng ${l.month}" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("${d.day}", color = if (selected) c.onBrand else if (isToday) c.brand else c.fg, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        SmallDateText(
            SmallDate.lunar(l, selected),
            color = when { selected -> c.onBrand.copy(alpha = 0.8f); l.day == 1 || l.day == 15 -> c.lunar; else -> c.fg3 },
            fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (l.day == 1 || l.day == 15) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Four columns of choices (years or months). */
@Composable
private fun <T> ChoiceGrid(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, enabled: (T) -> Boolean, onPick: (T) -> Unit) {
    val c = LocalLichAmColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { it ->
                    val on = selected(it)
                    val ok = enabled(it)
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(R12).background(if (on) c.brand else c.surface)
                            .alpha(if (ok) 1f else 0f).clickable(enabled = ok) { onPick(it) },
                        contentAlignment = Alignment.Center,
                    ) { Text(label(it), color = if (on) c.onBrand else c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
