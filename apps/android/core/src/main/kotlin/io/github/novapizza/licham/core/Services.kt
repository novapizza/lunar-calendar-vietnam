package io.github.novapizza.licham.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

// ---------------------------------------------------------------------------
// Solar date helpers (java.time; minSdk 26 has it)
// ---------------------------------------------------------------------------

fun SolarDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)
fun LocalDate.toSolar(): SolarDate = SolarDate(year, monthValue, dayOfMonth)
fun SolarDate.plusDays(n: Long): SolarDate = toLocalDate().plusDays(n).toSolar()
fun SolarDate.plusMonths(n: Long): SolarDate = toLocalDate().plusMonths(n).toSolar()

/** 0 = Monday … 6 = Sunday (the calendar grid starts on Monday). */
val SolarDate.weekdayIndex: Int get() = toLocalDate().dayOfWeek.value - 1
val SolarDate.isSunday: Boolean get() = toLocalDate().dayOfWeek == DayOfWeek.SUNDAY
fun daysInMonth(year: Int, month: Int): Int = LocalDate.of(year, month, 1).lengthOfMonth()

/** Week number as the web app shows it (weeks start Monday; week 1 contains 1 January). */
val SolarDate.weekOfYear: Int get() = toLocalDate().get(WeekFields.of(DayOfWeek.MONDAY, 1).weekOfWeekBasedYear())

fun SolarDate.isInRange(): Boolean = this in LunarCalendar.FIRST_DAY..LunarCalendar.LAST_DAY

/** Day facts, or null outside the supported range (1900-01-31 … 2099-12-31). */
fun DayInfo.Companion.orNull(date: SolarDate): DayInfo? = if (date.isInRange()) DayFacts.info(date) else null

/** Days of the month grid: Monday of the first week … Sunday of the last week. */
fun monthGrid(year: Int, month: Int): List<SolarDate> {
    val first = SolarDate(year, month, 1)
    val start = first.plusDays(-first.weekdayIndex.toLong())
    val last = SolarDate(year, month, daysInMonth(year, month))
    val end = last.plusDays((6 - last.weekdayIndex).toLong())
    val out = ArrayList<SolarDate>(42)
    var d = start
    while (d <= end) { out += d; d = d.plusDays(1) }
    return out
}

// ---------------------------------------------------------------------------
// User data (stored on the device; same shapes as the web app's localStorage)
// ---------------------------------------------------------------------------

@Serializable
data class UserNote(val id: String, val date: String, val content: String, val createdAt: Long)

@Serializable
data class LunarEvent(
    val id: String,
    val title: String,
    val lunarDay: Int,
    val lunarMonth: Int,
    val isLeapMonth: Boolean,
    val createdAt: Long,
)

/** Built-in event groups of shared/data/presets.json as LunarEvents. */
fun presetEvents(enabled: Set<String>): List<LunarEvent> =
    CalendarData.presets.presets.filter { it.id in enabled }.flatMap { p ->
        p.events.map { LunarEvent(it.id, it.title, it.lunarDay, it.lunarMonth, false, 0) }
    }

// ---------------------------------------------------------------------------
// Day helpers shared by the screens
// ---------------------------------------------------------------------------

object Zodiac {
    /** Lục Xung: the day's chi is opposite the user's (Tý–Ngọ, Sửu–Mùi, …). */
    fun clashes(userChi: String?, day: DayInfo): Boolean {
        if (userChi == null) return false
        val u = CalendarData.calendar.chi.indexOf(userChi)
        val d = day.dayChiIndex
        return u >= 0 && d >= 0 && kotlin.math.abs(u - d) == 6
    }
}

/** "Mồng 1" / "Ngày rằm" for lunar day 1/15, except 1/1 and 15/1 (named holidays). */
fun lunarDayLabel(lunar: LunarDate): String? = when {
    lunar.day == 1 && lunar.month != 1 -> "Mồng 1"
    lunar.day == 15 && lunar.month != 1 -> "Ngày rằm"
    else -> null
}

/** "Tháng 9 – 10 âm lịch · Bính Ngọ" for a solar month. */
fun lunarMonthSubtitle(year: Int, month: Int): String {
    val first = DayInfo.orNull(SolarDate(year, month, 1).let { if (it.isInRange()) it else LunarCalendar.FIRST_DAY })
    val last = DayInfo.orNull(SolarDate(year, month, daysInMonth(year, month)))
    if (first == null || last == null) return ""
    val a = first.lunar.month
    val b = last.lunar.month
    val months = if (a == b) "Tháng $a" else "Tháng $a – $b"
    return "$months âm lịch · ${last.canChiYear}"
}

// ---------------------------------------------------------------------------
// Verified lunar → solar (as the web app's lunarToSolar)
// ---------------------------------------------------------------------------

sealed interface LunarToSolar {
    data class Ok(val solar: SolarDate, val adjustedDay: Boolean) : LunarToSolar
    data class Fail(val reason: String) : LunarToSolar
}

object Conversion {
    private fun exact(day: Int, month: Int, year: Int, leap: Boolean): SolarDate? {
        val s = runCatching { LunarCalendar.lunarToSolar(day, month, year, leap) }.getOrNull() ?: return null
        if (!s.isInRange()) return null
        val back = LunarCalendar.solarToLunar(s)
        return if (back == LunarDate(day, month, year, leap)) s else null
    }

    /** Converts and checks by converting back; day 30 of a 29-day month falls back to day 29. */
    fun lunarToSolar(day: Int, month: Int, year: Int, leap: Boolean): LunarToSolar {
        exact(day, month, year, leap)?.let { return LunarToSolar.Ok(it, false) }
        if (day == 30) exact(29, month, year, leap)?.let { return LunarToSolar.Ok(it, true) }
        if (leap && exact(1, month, year, true) == null) return LunarToSolar.Fail("leap_month_not_found")
        return LunarToSolar.Fail("conversion_error")
    }
}

// ---------------------------------------------------------------------------
// Upcoming holidays and lunar days (Sự kiện tab)
// ---------------------------------------------------------------------------

data class UpcomingEvent(
    val solarDate: SolarDate,
    val title: String,
    val lunarDay: Int?,
    val lunarMonth: Int?,
    val lunar: Boolean,
    val daysUntil: Int,
)

object Upcoming {
    /** Up to [maxCount] solar/lunar holidays, mồng 1 and rằm within [daysToScan] days from [today]. */
    fun events(today: SolarDate, maxCount: Int = 12, daysToScan: Int = 180): List<UpcomingEvent> {
        val out = ArrayList<UpcomingEvent>()
        val h = CalendarData.holidays
        var d = today
        for (i in 0 until daysToScan) {
            if (out.size >= maxCount || !d.isInRange()) break
            h.solar["${d.day}-${d.month}"]?.let { out += UpcomingEvent(d, it, null, null, false, i) }
            if (out.size >= maxCount) break
            val l = LunarCalendar.solarToLunar(d)
            val named = h.lunar["${l.day}-${l.month}"]
            when {
                named != null -> out += UpcomingEvent(d, named, l.day, l.month, true, i)
                l.day == 1 && l.month != 1 -> out += UpcomingEvent(d, "Mồng 1 tháng ${l.month}", l.day, l.month, true, i)
                l.day == 15 && l.month != 1 -> out += UpcomingEvent(d, "Ngày rằm tháng ${l.month}", l.day, l.month, true, i)
            }
            d = d.plusDays(1)
        }
        return out.take(maxCount)
    }
}

// ---------------------------------------------------------------------------
// Auspicious-date finder (Tìm ngày tốt)
// ---------------------------------------------------------------------------

data class Activity(val key: String, val label: String, val field: String)

data class AuspiciousDate(val date: SolarDate, val lunarDate: String, val score: Int, val truc: String)

object Auspicious {
    /** The finder's three choices, as in the web app (Khởi công uses the "moving" rule). */
    val ACTIVITIES = listOf(
        Activity("WEDDING", "Cưới Hỏi", "wedding"),
        Activity("GROUNDBREAKING", "Khởi Công", "moving"),
        Activity("OPENING", "Khai Trương", "opening"),
    )

    /** Up to 10 days from [from] within monthsToScan × 31 days whose activities include the choice. */
    fun find(activityKey: String, from: SolarDate, monthsToScan: Int = 2): List<AuspiciousDate> {
        val field = ACTIVITIES.firstOrNull { it.key == activityKey }?.field
        val out = ArrayList<AuspiciousDate>()
        var d = from
        for (i in 0 until monthsToScan * 31) {
            if (!d.isInRange()) break
            val info = DayFacts.info(d)
            val include = if (field != null) field in info.activities else info.rating == Rating.GOOD
            if (include) out += AuspiciousDate(d, "${info.lunar.day}/${info.lunar.month}", if (field != null) 10 else 7, info.truc)
            if (out.size >= 10) break
            d = d.plusDays(1)
        }
        return out
    }
}
