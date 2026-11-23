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

/**
 * A personal event, also the built-in presets: the stored shape of shared/design/events.md (same
 * JSON as the web app's types.ts). Fields are named after the lunar calendar because events were
 * lunar-only at first. [lunarDay] / [lunarMonth] are the day and month of the monthly / yearly rule
 * in [calendar] (for none / daily / weekly: the start day in [calendar]); [isLeapMonth] only for
 * lunar yearly events. Every field after [createdAt] has the spec's default, which the encoder
 * leaves out (encodeDefaults = false): events saved before 2026-11 decode as lunar, yearly, all
 * day, and a lunar yearly event without the new fields is saved exactly as before.
 */
@Serializable
data class LunarEvent(
    val id: String,
    val title: String,
    val lunarDay: Int,
    val lunarMonth: Int,
    val isLeapMonth: Boolean,
    val createdAt: Long,
    val calendar: String = LUNAR,
    val repeat: String = YEARLY,
    /** First day, "YYYY-MM-DD" (solar). Required for none, daily, weekly; a lower bound otherwise. */
    val start: String? = null,
    /** Weekly: 0 = Monday … 6 = Sunday; empty = the weekday of [start]. */
    val weekdays: List<Int> = emptyList(),
    /** Last day an occurrence may start (inclusive). */
    val until: String? = null,
    /** At most this many occurrences from [start] (removed ones count too). */
    val count: Int? = null,
    /** Occurrence start days removed ("chỉ lần này"). */
    val exceptions: List<String> = emptyList(),
    val allDay: Boolean = true,
    val startTime: String = "09:00",
    val endTime: String = "10:00",
    /** An occurrence covers its start day and the days - 1 days after it. */
    val days: Int = 1,
    /** Minutes before the start time, or before 08:00 of the start day when all day. */
    val reminders: List<Int> = emptyList(),
    val notes: String? = null,
    val location: String? = null,
    /** Theme colour token: gold, brand, lunar, good, clash. */
    val color: String = GOLD,
) {
    val isSolar: Boolean get() = calendar == SOLAR
    val isMonthly: Boolean get() = repeat == MONTHLY
    /** Monthly or yearly on lunar dates (the rule follows the moon). */
    val isLunarRule: Boolean get() = calendar == LUNAR && (repeat == MONTHLY || repeat == YEARLY)
    val isRepeating: Boolean get() = repeat != NONE
    val span: Int get() = maxOf(1, days)

    companion object {
        const val LUNAR = "lunar"
        const val SOLAR = "solar"
        const val NONE = "none"
        const val DAILY = "daily"
        const val WEEKLY = "weekly"
        const val MONTHLY = "monthly"
        const val YEARLY = "yearly"
        const val GOLD = "gold"
        val COLORS = listOf("gold", "brand", "lunar", "good", "clash")
    }
}

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

/** What an upcoming event is; holidays include mồng 1 and rằm. PERSONAL: one of the user's own events. */
enum class EventKind { HOLIDAY, OBSERVANCE, OBSERVANCE_LUNAR, FESTIVAL, PERSONAL }

data class UpcomingEvent(
    val solarDate: SolarDate,
    val title: String,
    val lunarDay: Int?,
    val lunarMonth: Int?,
    val lunar: Boolean,
    val daysUntil: Int,
    val kind: EventKind = EventKind.HOLIDAY,
    /** PERSONAL: the event's id, colour token and time label (null when all day). */
    val eventId: String? = null,
    val color: String? = null,
    val time: String? = null,
)

object Upcoming {
    /**
     * The events on one solar day: a solar holiday, then a named lunar holiday or, failing that,
     * mồng 1 / rằm (not of tháng Giêng, which has its own holidays).
     */
    private fun on(d: SolarDate, daysUntil: Int): List<UpcomingEvent> {
        val h = CalendarData.holidays
        val out = ArrayList<UpcomingEvent>(2)
        h.solarHoliday(d)?.let { out += UpcomingEvent(d, it, null, null, false, daysUntil) }
        val l = LunarCalendar.solarToLunar(d)
        val named = h.lunar["${l.day}-${l.month}"]
        val observances = h.observancesOf(d, l)
        // A named lunar day (Tết Hạ nguyên on 15/10) stands for that month's mồng 1 / rằm.
        val lunarNamed = observances.any { it.second == EventKind.OBSERVANCE_LUNAR }
        when {
            named != null -> out += UpcomingEvent(d, named, l.day, l.month, true, daysUntil)
            lunarNamed -> Unit
            l.day == 1 && l.month != 1 -> out += UpcomingEvent(d, "Mồng 1 tháng ${l.month}", l.day, l.month, true, daysUntil)
            l.day == 15 && l.month != 1 -> out += UpcomingEvent(d, "Ngày rằm tháng ${l.month}", l.day, l.month, true, daysUntil)
        }
        for ((title, kind) in observances) {
            out += if (kind == EventKind.OBSERVANCE) UpcomingEvent(d, title, null, null, false, daysUntil, kind)
            else UpcomingEvent(d, title, l.day, l.month, true, daysUntil, kind)
        }
        return out
    }

    /** Up to [maxCount] solar/lunar holidays, mồng 1 and rằm within [daysToScan] days from [today]. */
    fun events(today: SolarDate, maxCount: Int = 12, daysToScan: Int = 180): List<UpcomingEvent> {
        val out = ArrayList<UpcomingEvent>()
        var d = today
        for (i in 0 until daysToScan) {
            if (out.size >= maxCount || !d.isInRange()) break
            out += on(d, i)
            d = d.plusDays(1)
        }
        return out.take(maxCount)
    }

    /** First day of the solar month [offset] months after [today]'s. */
    fun monthStart(today: SolarDate, offset: Int): SolarDate = SolarDate(today.year, today.month, 1).plusMonths(offset.toLong())

    /**
     * The events of the whole solar month [offset] months after [today]'s (0 = this month, its
     * past days included), daysUntil counted from [today] (negative for past days). Empty past
     * the lunar tables (LunarCalendar.LAST_DAY). The user's own [personal] events come first on their day.
     */
    fun month(today: SolarDate, offset: Int, personal: List<LunarEvent> = emptyList()): List<UpcomingEvent> {
        val first = monthStart(today, offset)
        val todayJd = LunarCalendar.jdFromDate(today.day, today.month, today.year)
        val out = ArrayList<UpcomingEvent>()
        var d = first
        while (d.month == first.month && d.isInRange()) {
            val daysUntil = LunarCalendar.jdFromDate(d.day, d.month, d.year) - todayJd
            out += PersonalEvents.upcoming(personal, d, daysUntil)
            out += on(d, daysUntil)
            d = d.plusDays(1)
        }
        return out
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
