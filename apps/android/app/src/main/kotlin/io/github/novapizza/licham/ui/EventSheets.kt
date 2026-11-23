package io.github.novapizza.licham.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.EventEditTarget
import io.github.novapizza.licham.core.Conversion
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.LunarCalendar
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.LunarToSolar
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.daysInMonth
import io.github.novapizza.licham.core.isInRange
import io.github.novapizza.licham.core.plusDays
import io.github.novapizza.licham.core.plusMonths
import io.github.novapizza.licham.core.weekdayIndex
import java.util.UUID

// ---------------------------------------------------------------------------
// Colours and words shared by the event screens
// ---------------------------------------------------------------------------

/** The event's colour token (shared/design/events.md: gold, brand, lunar, good, clash) in the theme. */
fun LichAmColors.eventColor(token: String?): Color = when (token) {
    "brand" -> brand
    "lunar" -> lunar
    "good" -> good
    "clash" -> clash
    else -> gold
}

fun LichAmColors.eventSoft(token: String?): Color = when (token) {
    "brand" -> brandSoft
    "lunar" -> lunarSoft
    "good" -> goodSoft
    "clash" -> clashSoft
    else -> goldSoft
}

/** "17/10 âm" (leap months: "17/6 nhuận âm"). */
private fun lunarLabel(d: SolarDate): String {
    if (!d.isInRange()) return ""
    val l = LunarCalendar.solarToLunar(d)
    return "${l.day}/${l.month}${if (l.leap) " nhuận" else ""} âm"
}

/** "Cả ngày", "09:00 – 10:00", plus " · đến Thứ Năm 26/11" when the occurrence covers several days. */
private fun timeWithEnd(e: LunarEvent, start: SolarDate): String {
    val base = PersonalEvents.timeLabel(e)
    if (e.span <= 1) return base
    val last = start.plusDays((e.span - 1).toLong())
    return "$base · đến ${last.weekdayName()} ${last.day}/${last.month}"
}

private val REPEATS = listOf(
    LunarEvent.NONE to "Không lặp", LunarEvent.DAILY to "Hằng ngày", LunarEvent.WEEKLY to "Hằng tuần",
    LunarEvent.MONTHLY to "Hằng tháng", LunarEvent.YEARLY to "Hằng năm",
)

@Composable
private fun ColorDot(token: String?, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(Pill).background(LocalLichAmColors.current.eventColor(token)))
}

// ---------------------------------------------------------------------------
// Day lists: the Calendar's selected-day panel and the Today page
// ---------------------------------------------------------------------------

/** "Sự kiện" card: the day's occurrences (tap: detail) and "+ Thêm" (the editor on that day). */
@Composable
fun DayEventsCard(state: AppState, day: SolarDate, compact: Boolean = false) {
    val c = LocalLichAmColors.current
    val list = remember(state.lunarEvents, day) { state.personalOn(day) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Sự kiện", Modifier.weight(1f), icon = Icons.Outlined.EventNote)
            SoftButton("Thêm", { state.openEditor(null, day) }, icon = Icons.Filled.Add)
        }
        VSpace(if (compact) 8.dp else 12.dp)
        if (list.isEmpty()) {
            Text("Chưa có sự kiện trong ngày này.", color = c.fg3, fontSize = 13.sp, lineHeight = 18.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { (e, start) ->
                    Row(
                        Modifier.fillMaxWidth().clip(R12).background(c.eventSoft(e.color)).clickable { state.openDetail(e.id, start) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(e.color)
                        HSpace(10.dp)
                        Text(e.title, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        HSpace(8.dp)
                        Text(PersonalEvents.timeLabel(e), color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sự kiện của bạn: all events (title, rule in words) → detail; "+ Thêm" → editor
// ---------------------------------------------------------------------------

@Composable
internal fun PersonalEventsSheet(state: AppState, onClose: () -> Unit) {
    SheetFrame("Sự kiện của bạn", onClose, icon = Icons.Outlined.EventNote, subtitle = "Giỗ, sinh nhật, lịch hẹn… theo ngày âm hoặc dương") {
        PersonalEventsList(state, "Thêm giỗ, sinh nhật, lịch hẹn… Sự kiện hiện trong lịch, danh sách sự kiện và có thể nhắc trước.")
    }
}

/** The user's events, each opening its detail on its next occurrence; "Thêm" opens the editor. */
@Composable
internal fun PersonalEventsList(state: AppState, emptyText: String) {
    val c = LocalLichAmColors.current
    val today = AppState.today()
    Column {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Sự kiện riêng (${state.lunarEvents.size})", Modifier.weight(1f))
            SoftButton("Thêm", { state.openEditor(null, state.selectedDate) }, icon = Icons.Filled.Add)
        }
        if (state.lunarEvents.isEmpty()) {
            Box(Modifier.fillMaxWidth().dashedBorder(c.line).padding(horizontal = 16.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(emptyText, color = c.fg2, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.clip(R16).background(c.surface2)) {
                state.lunarEvents.forEachIndexed { i, e ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val day = PersonalEvents.nextOccurrence(e, today) ?: PersonalEvents.startOf(e) ?: today
                            state.openDetail(e.id, day)
                        }.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(e.color, 10)
                        HSpace(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(e.title, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                PersonalEvents.describe(e) + if (e.allDay) "" else " · ${e.startTime}",
                                color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = c.fg3)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Event detail
// ---------------------------------------------------------------------------

@Composable
private fun DetailLine(icon: ImageVector, text: String, strong: Boolean = false) {
    val c = LocalLichAmColors.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(16.dp), tint = c.fg3)
        HSpace(12.dp)
        Text(text, color = if (strong) c.fg else c.fg2, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** One occurrence: day, time, repeat, reminders, place, notes; Sửa / Xoá (this one or all for repeats). */
@Composable
internal fun EventDetailSheet(state: AppState, onClose: () -> Unit) {
    val c = LocalLichAmColors.current
    val ref = state.detailRef
    val e = ref?.let { state.eventById(it.id) }
    if (ref == null || e == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    // "edit" / "delete": which button asked "Chỉ lần này / Tất cả các lần".
    var ask by remember(ref) { mutableStateOf<String?>(null) }
    val start = ref.start
    SheetFrame(
        e.title, onClose,
        heading = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(5.dp).height(32.dp).clip(Pill).background(c.eventColor(e.color)))
                HSpace(12.dp)
                Text(e.title, color = c.fg, fontSize = 18.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.Bold, letterSpacing = HeadingTracking)
            }
        },
        footer = {
            if (ask == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Xoá", {
                        if (e.isRepeating) ask = "delete" else { state.deleteEvent(e.id); onClose() }
                    }, Modifier.weight(1f))
                    PrimaryButton("Sửa", {
                        if (e.isRepeating) ask = "edit" else state.openEditor(e.id, start)
                    }, Modifier.weight(1f), tall = true)
                }
            } else {
                val edit = ask == "edit"
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (edit) "Sửa sự kiện lặp lại" else "Xoá sự kiện lặp lại",
                        color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Chỉ lần này", {
                            if (edit) { ask = null; state.openEditor(e.id, start, onlyThis = true) }
                            else { state.deleteOccurrence(e.id, start); onClose() }
                        }, Modifier.weight(1f))
                        PrimaryButton("Tất cả các lần", {
                            if (edit) { ask = null; state.openEditor(e.id, start) }
                            else { state.deleteEvent(e.id); onClose() }
                        }, Modifier.weight(1f), tall = true)
                    }
                    Text(
                        "Huỷ", color = c.fg2, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(R12).clickable { ask = null }.padding(vertical = 8.dp),
                    )
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailLine(Icons.Outlined.CalendarMonth, "${start.longLabel()} · ${lunarLabel(start)}", strong = true)
            DetailLine(Icons.Outlined.Schedule, timeWithEnd(e, start))
            if (e.isRepeating) DetailLine(Icons.Outlined.Repeat, PersonalEvents.describe(e))
            if (e.reminders.isNotEmpty()) {
                DetailLine(Icons.Outlined.NotificationsNone, e.reminders.sorted().joinToString(", ") { PersonalEvents.reminderLabel(e.allDay, it) })
            }
            e.location?.takeIf { it.isNotBlank() }?.let { DetailLine(Icons.Outlined.Place, it) }
            e.notes?.takeIf { it.isNotBlank() }?.let { DetailLine(Icons.Outlined.Description, it) }
        }
    }
}

// ---------------------------------------------------------------------------
// Event editor
// ---------------------------------------------------------------------------

private fun hm(s: String): Pair<Int, Int> = runCatching { s.substring(0, 2).toInt() to s.substring(3, 5).toInt() }.getOrDefault(9 to 0)
private fun hmText(h: Int, m: Int) = "%02d:%02d".format(h, m)
private fun hasLeap(year: Int, month: Int) = runCatching { LunarCalendar.monthsOf(year).any { it.leap && it.month == month } }.getOrDefault(false)

/** The editor's fields; [original] is the event edited (null: a new one). */
private class EventForm(target: EventEditTarget, val original: LunarEvent?) {
    val onlyThis = target.onlyThis
    var title by mutableStateOf(original?.title ?: "")
    var allDay by mutableStateOf(original?.allDay ?: true)
    var date by mutableStateOf(if (original != null && !target.onlyThis) PersonalEvents.startOf(original) ?: target.day else target.day)
    var lunarEntry by mutableStateOf(original != null && !target.onlyThis && original.isLunarRule)
    var lDay by mutableIntStateOf(0)
    var lMonth by mutableIntStateOf(1)
    var lYear by mutableIntStateOf(2026)
    var lLeap by mutableStateOf(false)
    var startH by mutableIntStateOf(hm(original?.startTime ?: "09:00").first)
    var startM by mutableIntStateOf(hm(original?.startTime ?: "09:00").second)
    var endH by mutableIntStateOf(hm(original?.endTime ?: "10:00").first)
    var endM by mutableIntStateOf(hm(original?.endTime ?: "10:00").second)
    var days by mutableIntStateOf(original?.span ?: 1)
    var repeat by mutableStateOf(if (target.onlyThis) LunarEvent.NONE else original?.repeat ?: LunarEvent.NONE)
    var weekdays by mutableStateOf(original?.let { PersonalEvents.weekdaysOf(it).toSet() }?.ifEmpty { null } ?: setOf(target.day.weekdayIndex))
    var calendar by mutableStateOf(original?.calendar ?: LunarEvent.LUNAR)
    var leapOnly by mutableStateOf(original?.isLeapMonth ?: true)
    /** 0 never, 1 until a day, 2 after a number of times. */
    var endMode by mutableIntStateOf(if (target.onlyThis) 0 else if (original?.until != null) 1 else if (original?.count != null) 2 else 0)
    var until by mutableStateOf(original?.let { PersonalEvents.untilOf(it) } ?: target.day.plusMonths(1))
    var count by mutableIntStateOf(original?.count ?: 10)
    var reminders by mutableStateOf(original?.reminders?.distinct()?.sorted() ?: emptyList())
    var location by mutableStateOf(original?.location ?: "")
    var notes by mutableStateOf(original?.notes ?: "")
    var color by mutableStateOf(original?.color ?: LunarEvent.GOLD)

    init {
        val l = LunarCalendar.solarToLunar(date)
        lYear = l.year; lMonth = l.month; lLeap = l.leap
        // A lunar rule keeps its day (30 stays 30 in a 29-day month).
        lDay = if (original != null && original.isLunarRule && !target.onlyThis) original.lunarDay else l.day
        if (original == null || original.repeat != LunarEvent.YEARLY || original.calendar != LunarEvent.LUNAR) leapOnly = true
    }

    /** The start day in the solar calendar; null when the lunar date entered does not exist. */
    val start: SolarDate?
        get() = if (!lunarEntry) date else when (val r = Conversion.lunarToSolar(lDay, lMonth, lYear, lLeap)) {
            is LunarToSolar.Ok -> r.solar
            is LunarToSolar.Fail -> null
        }

    fun enterLunar(on: Boolean) {
        if (on == lunarEntry) return
        if (on) {
            val l = LunarCalendar.solarToLunar(date)
            lDay = l.day; lMonth = l.month; lYear = l.year; lLeap = l.leap
        } else {
            start?.let { date = it }
        }
        lunarEntry = on
    }

    /** The lunar date of the rule: the one entered, else the start day's. */
    fun ruleLunar(s: SolarDate): Triple<Int, Int, Boolean> =
        if (lunarEntry) Triple(lDay, lMonth, lLeap) else LunarCalendar.solarToLunar(s).let { Triple(it.day, it.month, it.leap) }

    val repeating get() = repeat != LunarEvent.NONE
    val rule get() = repeat == LunarEvent.MONTHLY || repeat == LunarEvent.YEARLY

    val error: String?
        get() {
            val s = start ?: return "Ngày âm này không có trong lịch."
            if (!s.isInRange()) return "Lịch tính từ 31/1/1900 đến 31/12/2099."
            if (!allDay && days == 1 && endH * 60 + endM <= startH * 60 + startM) return "Giờ kết thúc phải sau giờ bắt đầu."
            if (repeat == LunarEvent.WEEKLY && weekdays.isEmpty()) return "Chọn ít nhất một ngày trong tuần."
            if (repeating && endMode == 1 && until < s) return "Ngày kết thúc phải từ ngày bắt đầu trở đi."
            return null
        }

    fun build(): LunarEvent? {
        val s = start ?: return null
        if (error != null || title.isBlank()) return null
        val lunarRule = rule && calendar == LunarEvent.LUNAR
        val (ld, lm, leap) = if (lunarRule) ruleLunar(s).let { (d, m, lp) -> Triple(d, m, repeat == LunarEvent.YEARLY && lp && leapOnly) } else Triple(s.day, s.month, false)
        // An old event without a start keeps none: a start would hide its earlier occurrences.
        val keepNoStart = rule && original != null && !onlyThis && original.start == null && endMode != 2
        val base = if (original != null && !onlyThis) original else LunarEvent(UUID.randomUUID().toString(), "", 1, 1, false, System.currentTimeMillis())
        return base.copy(
            title = title.trim(),
            lunarDay = ld, lunarMonth = lm, isLeapMonth = leap,
            calendar = if (lunarRule) LunarEvent.LUNAR else LunarEvent.SOLAR,
            repeat = repeat,
            start = if (keepNoStart) null else s.toString(),
            weekdays = if (repeat == LunarEvent.WEEKLY) weekdays.sorted() else emptyList(),
            until = if (repeating && endMode == 1) until.toString() else null,
            count = if (repeating && endMode == 2) count else null,
            exceptions = if (original != null && !onlyThis && repeating) original.exceptions else emptyList(),
            allDay = allDay,
            startTime = if (allDay) "09:00" else hmText(startH, startM),
            endTime = if (allDay) "10:00" else hmText(endH, endM),
            days = days,
            reminders = reminders.distinct().sorted(),
            notes = notes.trim().ifEmpty { null },
            location = location.trim().ifEmpty { null },
            color = color,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = LocalLichAmColors.current.fg2, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Hint(text: String, color: Color? = null) {
    Text(text, color = color ?: LocalLichAmColors.current.fg2, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun TimeSelects(label: String, h: Int, m: Int, onChange: (Int, Int) -> Unit) {
    val c = LocalLichAmColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.width(72.dp))
        SelectField(h, (0..23).toList(), { "%02d giờ".format(it) }, { onChange(it, m) }, Modifier.weight(1f))
        SelectField(m, (0..55 step 5).toList(), { "%02d phút".format(it) }, { onChange(h, it) }, Modifier.weight(1f))
    }
}

@Composable
private fun CheckLine(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    val c = LocalLichAmColors.current
    Row(Modifier.clip(R12).clickable { onChange(!checked) }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = c.brand, checkmarkColor = c.onBrand, uncheckedColor = c.fg3))
        Text(text, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** "Sự kiện mới" / "Sửa sự kiện" (shared/design/events.md, "Screens"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventEditorSheet(state: AppState, onClose: () -> Unit) {
    val c = LocalLichAmColors.current
    val context = LocalContext.current
    val target = state.editTarget
    if (target == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val original = target.id?.let { state.eventById(it) }
    val f = remember(target) { EventForm(target, original) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun addReminder(m: Int) {
        f.reminders = (f.reminders + m).distinct().sorted()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val start = f.start
    val error = f.error
    SheetFrame(
        when {
            original == null -> "Sự kiện mới"
            target.onlyThis -> "Sửa lần này"
            else -> "Sửa sự kiện"
        },
        onClose, icon = Icons.Outlined.EventNote,
        subtitle = if (target.onlyThis) "Chỉ ngày ${target.day.day}/${target.day.month}/${target.day.year}, các lần khác giữ nguyên" else null,
        footer = {
            PrimaryButton("Lưu", {
                val e = f.build() ?: return@PrimaryButton
                if (target.onlyThis && original != null) state.saveOccurrence(original.id, target.day, e) else state.saveEvent(e)
                onClose()
            }, Modifier.fillMaxWidth(), enabled = f.title.isNotBlank() && error == null, tall = true)
        },
        keepHeight = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WebTextField(f.title, { f.title = it }, "Tên sự kiện, vd: Giỗ ông nội", singleLine = true)

            // Cả ngày
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cả ngày", color = c.fg, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(
                    f.allDay, {
                        f.allDay = it
                        // Reminder options differ: keep only those the new kind offers.
                        val allowed = PersonalEvents.reminderOptions(it).map { o -> o.first }.toSet()
                        f.reminders = f.reminders.filter { m -> m in allowed }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onBrand, checkedTrackColor = c.brand,
                        uncheckedThumbColor = c.fg3, uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.line,
                    ),
                )
            }

            // Ngày bắt đầu: one line that opens a month grid with the lunar days. An edited lunar
            // rule keeps its own day (30 stays 30) until another day is picked.
            DatePickerField(start ?: f.date, { f.date = it; f.lunarEntry = false }, "Ngày bắt đầu")

            if (!f.allDay) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeSelects("Bắt đầu", f.startH, f.startM) { h, m ->
                        // Moving the start keeps the length when the end would come before it.
                        val len = (f.endH * 60 + f.endM) - (f.startH * 60 + f.startM)
                        f.startH = h; f.startM = m
                        if (f.days == 1 && f.endH * 60 + f.endM <= h * 60 + m) {
                            val end = minOf(h * 60 + m + maxOf(len, 60), 23 * 60 + 55)
                            f.endH = end / 60; f.endM = end % 60
                        }
                    }
                    TimeSelects("Kết thúc", f.endH, f.endM) { h, m -> f.endH = h; f.endM = m }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Kéo dài")
                    SelectField(f.days, (1..30).toList(), { "$it ngày" }, { f.days = it })
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Lặp lại")
                    SelectField(f.repeat, REPEATS.map { it.first }, { r -> REPEATS.first { it.first == r }.second }, { f.repeat = it })
                }
            }
            if (f.days > 1 && start != null) {
                val last = start.plusDays((f.days - 1).toLong())
                Hint("Đến ${last.longLabel()}" + if (f.allDay) "" else ", ${hmText(f.endH, f.endM)}")
            }

            if (f.repeat == LunarEvent.WEEKLY) {
                Column {
                    FieldLabel("Vào các ngày")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PersonalEvents.WEEKDAY_SHORT.forEachIndexed { i, w ->
                            val on = i in f.weekdays
                            Box(
                                Modifier.weight(1f).height(40.dp).clip(R12).background(if (on) c.brand else c.surface2)
                                    .clickable { f.weekdays = if (on) f.weekdays - i else f.weekdays + i },
                                contentAlignment = Alignment.Center,
                            ) { Text(w, color = if (on) c.onBrand else c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }

            if (f.rule && start != null) {
                Column {
                    Segmented(f.calendar, listOf(LunarEvent.LUNAR to "Âm lịch", LunarEvent.SOLAR to "Dương lịch")) { f.calendar = it }
                    val yearly = f.repeat == LunarEvent.YEARLY
                    if (f.calendar == LunarEvent.LUNAR) {
                        val (d, m, leap) = f.ruleLunar(start)
                        Hint(
                            (if (yearly) "Ngày $d/$m${if (leap && f.leapOnly) " nhuận" else ""} âm hằng năm" else "Ngày $d âm hằng tháng") +
                                if (d == 30) ". Tháng thiếu (29 ngày): rơi vào ngày 29." else if (!yearly) ", cả tháng nhuận." else ".",
                            c.fg,
                        )
                        if (yearly && leap) CheckLine(f.leapOnly, "Chỉ tháng nhuận") { f.leapOnly = it }
                    } else {
                        val d = start.day
                        Hint(
                            if (yearly) "Ngày $d/${start.month} hằng năm" + (if (start.month == 2 && d == 29) ". Năm không nhuận: rơi vào 28/2." else ".")
                            else "Ngày $d hằng tháng" + (if (d > 28) ". Tháng không có ngày này: rơi vào ngày cuối tháng." else "."),
                            c.fg,
                        )
                    }
                }
            }

            if (f.repeating) {
                Column {
                    FieldLabel("Kết thúc")
                    SelectField(f.endMode, listOf(0, 1, 2), { listOf("Không bao giờ", "Đến ngày", "Sau … lần")[it] }, { f.endMode = it })
                    if (f.endMode == 1) {
                        VSpace(8.dp)
                        DatePickerField(f.until, { f.until = it }, "Đến ngày", min = start)
                    }
                    if (f.endMode == 2) {
                        VSpace(8.dp)
                        SelectField(f.count, (1..100).toList(), { "Sau $it lần" }, { f.count = it })
                    }
                }
            }

            // Nhắc: chips with ×, "+ Thêm nhắc".
            Column {
                FieldLabel("Nhắc")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.reminders.forEach { m ->
                        Row(
                            Modifier.clip(Pill).background(c.brandSoft).padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(PersonalEvents.reminderLabel(f.allDay, m), color = c.brand, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                            IconBtn(Icons.Filled.Close, "Bỏ nhắc", { f.reminders = f.reminders - m }, tint = c.brand, size = 28.dp, iconSize = 14.dp)
                        }
                    }
                    val options = PersonalEvents.reminderOptions(f.allDay).filter { it.first !in f.reminders }
                    if (options.isNotEmpty()) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                Modifier.clip(Pill).background(c.surface2).clickable { open = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Add, null, Modifier.size(14.dp), tint = c.fg2)
                                HSpace(4.dp)
                                Text("Thêm nhắc", color = c.fg, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp).background(c.surface)) {
                                options.forEach { (m, label) ->
                                    DropdownMenuItem(text = { Text(label, color = c.fg) }, onClick = { open = false; addReminder(m) })
                                }
                            }
                        }
                    }
                }
            }

            Column {
                FieldLabel("Địa điểm")
                WebTextField(f.location, { f.location = it }, "Thêm địa điểm", singleLine = true)
            }
            Column {
                FieldLabel("Ghi chú")
                WebTextField(f.notes, { f.notes = it }, "Thêm ghi chú", minLines = 2)
            }

            Column {
                FieldLabel("Màu")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LunarEvent.COLORS.forEachIndexed { i, token ->
                        val on = f.color == token
                        Box(
                            Modifier.size(36.dp).clip(Pill)
                                .then(if (on) Modifier.border(2.dp, c.fg, Pill) else Modifier)
                                .padding(4.dp).clip(Pill).background(c.eventColor(token))
                                .clickable(onClickLabel = "Màu ${i + 1}") { f.color = token },
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Color.White) }
                    }
                }
            }

            if (error != null && f.title.isNotBlank()) Text(error, color = c.bad, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
        }
    }
}
