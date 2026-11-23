package io.github.novapizza.licham.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** One occurrence of a personal event, expanded for the calendar export. */
data class Occurrence(
    val solar: SolarDate,
    /** Stable per occurrence: the event id plus the year (and month) it belongs to. */
    val uid: String,
    /** Lunar events: the lunar month, leap flag and lunar year the occurrence stands for. */
    val lunarMonth: Int? = null,
    val lunarLeap: Boolean = false,
    val lunarYear: Int? = null,
    /** The day was moved to the month's last day (lunar 30 → 29, solar 31 → 30, 29/2 → 28/2). */
    val adjustedTo: Int? = null,
)

/** An event covering a day: the event and the start day of its occurrence. */
data class DayEvent(val event: LunarEvent, val start: SolarDate)

/** A reminder of one occurrence: fires [minutes] before the reminder base, at [at] (local time). */
data class ReminderFire(val event: LunarEvent, val start: SolarDate, val minutes: Int, val at: LocalDateTime)

/**
 * The user's own events: the rules of shared/design/events.md, ported from the web app's
 * personalEvents.ts (shared/test-vectors/events.json holds dates both apps must reproduce).
 */
object PersonalEvents {
    /**
     * One note of the old "Ghi chú" card (removed 2026-11-23) as a one-off all-day event on its
     * day: title = first line (≤ 60 characters), notes = the whole text when longer. Same as the
     * web app's noteToEvent (shared/design/events.md, "Old notes").
     */
    fun noteToEvent(n: UserNote): LunarEvent {
        val text = n.content.trim()
        val first = text.lineSequence().first().trim()
        val title = when {
            first.isEmpty() -> "Ghi chú"
            first.length > 60 -> first.take(59).trimEnd() + "…"
            else -> first
        }
        val d = SolarDate.parse(n.date)
        return LunarEvent(
            n.id, title, d?.day ?: 1, d?.month ?: 1, false, n.createdAt,
            calendar = LunarEvent.SOLAR, repeat = LunarEvent.NONE, start = n.date,
            notes = text.takeIf { it != title },
        )
    }

    private val FIRST: LocalDate = LunarCalendar.FIRST_DAY.toLocalDate()
    private val LAST: LocalDate = LunarCalendar.LAST_DAY.toLocalDate()

    // ---- fields with their defaults ----------------------------------------

    fun startOf(e: LunarEvent): SolarDate? = e.start?.let(SolarDate::parse)
    fun untilOf(e: LunarEvent): SolarDate? = e.until?.let(SolarDate::parse)

    /** Weekly: the chosen weekdays (0 = Monday), else the weekday of the start day. */
    fun weekdaysOf(e: LunarEvent): List<Int> = e.weekdays.ifEmpty { listOfNotNull(startOf(e)?.weekdayIndex) }

    // ---- occurrences ---------------------------------------------------------

    private fun max(a: LocalDate, b: LocalDate) = if (a > b) a else b
    private fun min(a: LocalDate, b: LocalDate) = if (a < b) a else b

    /** A lunar month of lunar year [year]: first solar day and length (29 or 30). */
    private data class Month(val month: Int, val leap: Boolean, val first: SolarDate, val days: Int)

    private fun monthsWithLength(year: Int): List<Month> {
        val starts = runCatching { LunarCalendar.monthsOf(year) }.getOrNull() ?: return emptyList()
        val nextJd = runCatching { LunarCalendar.monthsOf(year + 1).first().jd }.getOrNull()
        return starts.mapIndexedNotNull { i, m ->
            val end = starts.getOrNull(i + 1)?.jd ?: nextJd ?: return@mapIndexedNotNull null
            Month(m.month, m.leap, LunarCalendar.jdToDate(m.jd), end - m.jd)
        }
    }

    /** The rule's candidate start days in [lo, hi], in order (start, until, count, exceptions not applied). */
    private fun candidates(e: LunarEvent, from: LocalDate, to: LocalDate): Sequence<LocalDate> {
        val lo = max(from, FIRST)
        val hi = min(to, LAST)
        if (lo > hi) return emptySequence()
        fun inRange(d: LocalDate) = d in lo..hi
        return when (e.repeat) {
            LunarEvent.NONE -> sequenceOf(startOf(e)?.toLocalDate()).filterNotNull().filter(::inRange)
            LunarEvent.DAILY, LunarEvent.WEEKLY -> {
                val wd = weekdaysOf(e).toSet()
                generateSequence(lo) { it.plusDays(1) }.takeWhile { it <= hi }
                    .filter { e.repeat == LunarEvent.DAILY || (it.dayOfWeek.value - 1) in wd }
            }
            else -> if (e.isSolar) {
                (lo.year..hi.year).asSequence().flatMap { y ->
                    (if (e.isMonthly) (1..12).toList() else listOf(e.lunarMonth)).asSequence()
                        .map { m -> LocalDate.of(y, m, minOf(e.lunarDay, daysInMonth(y, m))) }
                }.filter(::inRange)
            } else {
                // Lunar: lunar year lo.year - 1 may still have months in solar year lo.year.
                ((lo.year - 1)..hi.year).asSequence().flatMap { ly ->
                    if (e.isMonthly) {
                        monthsWithLength(ly).asSequence().map { m -> m.first.toLocalDate().plusDays((minOf(e.lunarDay, m.days) - 1).toLong()) }
                    } else {
                        when (val r = Conversion.lunarToSolar(e.lunarDay, e.lunarMonth, ly, e.isLeapMonth)) {
                            is LunarToSolar.Ok -> sequenceOf(r.solar.toLocalDate())
                            is LunarToSolar.Fail -> emptySequence()
                        }
                    }
                }.filter(::inRange)
            }
        }
    }

    private fun startsBetween(e: LunarEvent, from: LocalDate, to: LocalDate): List<LocalDate> {
        val start = startOf(e)?.toLocalDate()
        val until = untilOf(e)?.toLocalDate()
        var lo = from
        var hi = to
        if (start != null) lo = max(lo, start)
        if (until != null) hi = min(hi, until)
        if (lo > hi) return emptyList()
        val count = e.count
        val list = if (start != null && count != null && count > 0) {
            // The first `count` candidates from the start (a sequence: stops once they are found).
            candidates(e, start, until ?: LAST).take(count).filter { it in lo..hi }.toList()
        } else {
            candidates(e, lo, hi).toList()
        }
        if (e.exceptions.isEmpty()) return list
        val skip = e.exceptions.toSet()
        return list.filter { it.toString() !in skip }
    }

    /**
     * Start days of the occurrences of [e] that start in [from] … [to], in order: the candidates
     * bounded by start and until, the first `count` from start, without exceptions.
     */
    fun occurrenceStarts(e: LunarEvent, from: SolarDate, to: SolarDate): List<SolarDate> =
        startsBetween(e, from.toLocalDate(), to.toLocalDate()).map { it.toSolar() }

    /** Start days of the occurrences covering some day of [from] … [to] (an occurrence covers `days` days). */
    fun occurrencesCovering(e: LunarEvent, from: SolarDate, to: SolarDate): List<SolarDate> =
        startsBetween(e, from.toLocalDate().minusDays((e.span - 1).toLong()), to.toLocalDate()).map { it.toSolar() }

    /** The events covering [day]: all-day ones first (list order), then timed ones by start time. */
    fun eventsOnDay(events: List<LunarEvent>, day: SolarDate): List<DayEvent> {
        if (events.isEmpty() || !day.isInRange()) return emptyList()
        val out = ArrayList<DayEvent>()
        for (e in events) for (s in occurrencesCovering(e, day, day)) out += DayEvent(e, s)
        return out.sortedBy { if (it.event.allDay) "" else it.event.startTime }
    }

    /** The events covering [date] (eventsOnDay without the start days). */
    fun on(events: List<LunarEvent>, date: SolarDate): List<LunarEvent> = eventsOnDay(events, date).map { it.event }

    /** Whether some occurrence of [e] covers [date]. */
    fun occursOn(e: LunarEvent, date: SolarDate): Boolean = occurrencesCovering(e, date, date).isNotEmpty()

    /** The next occurrence of [e] starting on or after [day], or null (scans up to 2099). */
    fun nextOccurrence(e: LunarEvent, day: SolarDate): SolarDate? {
        var y = day.year
        while (y <= LunarCalendar.LAST_DAY.year) {
            val from = maxOf(day, SolarDate(y, 1, 1))
            occurrenceStarts(e, from, SolarDate(y + 4, 12, 31)).firstOrNull()?.let { return it }
            y += 5
        }
        return null
    }

    /** The events starting on [d] as Sự kiện list entries (first on their day). */
    fun upcoming(events: List<LunarEvent>, d: SolarDate, daysUntil: Int): List<UpcomingEvent> {
        val own = eventsOnDay(events, d).filter { it.start == d }
        if (own.isEmpty()) return emptyList()
        val l = LunarCalendar.solarToLunar(d)
        return own.map { (e, _) ->
            val time = if (e.allDay) null else timeLabel(e)
            if (e.isLunarRule) UpcomingEvent(d, e.title, l.day, l.month, true, daysUntil, EventKind.PERSONAL, e.id, e.color, time)
            else UpcomingEvent(d, e.title, null, null, false, daysUntil, EventKind.PERSONAL, e.id, e.color, time)
        }
    }

    // ---- words -----------------------------------------------------------------

    val WEEKDAY_SHORT = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    private fun fmt(d: SolarDate) = "${d.day}/${d.month}/${d.year}"

    /** "Cả ngày", "09:00 – 10:00". */
    fun timeLabel(e: LunarEvent): String = if (e.allDay) "Cả ngày" else "${e.startTime} – ${e.endTime}"

    /** The repeat in words: "Hằng năm vào 10/3 âm · đến 31/12/2030", "Hằng tuần vào T2, T4 · 10 lần", "Không lặp · 25/11/2026". */
    fun describe(e: LunarEvent): String {
        val lunar = e.calendar == LunarEvent.LUNAR
        var rule = when (e.repeat) {
            LunarEvent.NONE -> return startOf(e)?.let { "Không lặp · ${fmt(it)}" } ?: "Không lặp"
            LunarEvent.DAILY -> "Hằng ngày"
            LunarEvent.WEEKLY -> "Hằng tuần vào ${weekdaysOf(e).sorted().joinToString(", ") { WEEKDAY_SHORT[it] }}"
            LunarEvent.MONTHLY -> "Hằng tháng vào ngày ${e.lunarDay}${if (lunar) " âm" else ""}"
            else -> "Hằng năm vào ${e.lunarDay}/${e.lunarMonth}${if (lunar) (if (e.isLeapMonth) " nhuận âm" else " âm") else ""}"
        }
        val until = untilOf(e)
        if (until != null) rule += " · đến ${fmt(until)}"
        else if (e.count != null && e.start != null) rule += " · ${e.count} lần"
        return rule
    }

    /** Reminder options: minutes and label; all-day ones are counted back from 08:00 of the day. */
    val REMINDERS_TIMED = listOf(
        0 to "Đúng giờ", 5 to "5 phút trước", 10 to "10 phút trước", 15 to "15 phút trước", 30 to "30 phút trước",
        60 to "1 giờ trước", 120 to "2 giờ trước", 1440 to "1 ngày trước", 2880 to "2 ngày trước", 10080 to "1 tuần trước",
    )
    val REMINDERS_ALL_DAY = listOf(
        0 to "Vào ngày, lúc 8:00", 1440 to "1 ngày trước, lúc 8:00", 2880 to "2 ngày trước, lúc 8:00", 10080 to "1 tuần trước, lúc 8:00",
    )

    fun reminderOptions(allDay: Boolean) = if (allDay) REMINDERS_ALL_DAY else REMINDERS_TIMED

    fun reminderLabel(allDay: Boolean, minutes: Int): String =
        reminderOptions(allDay).firstOrNull { it.first == minutes }?.second ?: "$minutes phút trước"

    // ---- reminders -------------------------------------------------------------

    private fun parseTime(s: String): LocalTime = runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.of(9, 0))

    /** The moment [minutes] before the reminder base of the occurrence starting on [start]. */
    fun fireTime(e: LunarEvent, start: SolarDate, minutes: Int): LocalDateTime {
        val base = if (e.allDay) LocalTime.of(8, 0) else parseTime(e.startTime)
        return start.toLocalDate().atTime(base).minusMinutes(minutes.toLong())
    }

    /** Reminders of [events] firing in [from, to), in time order. */
    fun remindersBetween(events: List<LunarEvent>, from: LocalDateTime, to: LocalDateTime): List<ReminderFire> {
        val out = ArrayList<ReminderFire>()
        for (e in events) {
            if (e.reminders.isEmpty()) continue
            val longest = e.reminders.max()
            // Occurrences whose reminders can fall in the window: starts up to `longest` minutes after it.
            val lo = from.toLocalDate().minusDays(1)
            val hi = to.plusMinutes(longest.toLong()).toLocalDate().plusDays(1)
            for (s in startsBetween(e, lo, hi)) {
                val start = s.toSolar()
                for (m in e.reminders) {
                    val at = fireTime(e, start, m)
                    if (at >= from && at < to) out += ReminderFire(e, start, m, at)
                }
            }
        }
        return out.sortedBy { it.at }
    }

    /**
     * The notification text of an occurrence: "Hôm nay · 09:00 – 10:00", "Ngày mai · Cả ngày",
     * "Thứ Năm 26/11 · Cả ngày", plus " · 10/10 âm" for lunar repeats.
     */
    fun reminderText(e: LunarEvent, start: SolarDate, today: SolarDate): String {
        val diff = ChronoUnit.DAYS.between(today.toLocalDate(), start.toLocalDate())
        val day = when (diff) {
            0L -> "Hôm nay"
            1L -> "Ngày mai"
            else -> "${WEEKDAY_NAMES[start.weekdayIndex]} ${start.day}/${start.month}"
        }
        val lunar = if (e.isLunarRule && start.isInRange()) LunarCalendar.solarToLunar(start).let { " · ${it.day}/${it.month} âm" } else ""
        return "$day · ${timeLabel(e)}$lunar"
    }

    val WEEKDAY_NAMES = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")

    // ---- export ----------------------------------------------------------------

    /** Whether the export writes [e] as one VEVENT with an RRULE (solar rules RFC 5545 can say exactly). */
    fun exportsAsRule(e: LunarEvent): Boolean = when (e.repeat) {
        LunarEvent.NONE, LunarEvent.DAILY, LunarEvent.WEEKLY -> e.start != null
        else -> when {
            !e.isSolar -> false
            e.isMonthly -> e.lunarDay <= 28
            else -> !(e.lunarMonth == 2 && e.lunarDay == 29)
        }
    }

    /** The first candidate start day on or after [from] (exceptions not applied: COUNT and EXDATE count them). */
    fun firstCandidate(e: LunarEvent, from: SolarDate): SolarDate? {
        val lo = startOf(e)?.toLocalDate() ?: from.toLocalDate()
        return candidates(e, lo, untilOf(e)?.toLocalDate() ?: LAST).firstOrNull()?.toSolar()
    }

    /**
     * Every occurrence of [e] whose start falls in [startYear] … [startYear] + [yearCount] - 1, with
     * the lunar month and year it stands for; lunar yearly leap events also return the lunar years
     * of the range that lack the leap month (skipped).
     */
    fun occurrences(e: LunarEvent, startYear: Int, yearCount: Int): Pair<List<Occurrence>, List<SkippedYear>> {
        val endYear = startYear + yearCount - 1
        val starts = occurrenceStarts(e, SolarDate(startYear, 1, 1), SolarDate(endYear, 12, 31))
        val list = starts.map { s ->
            if (e.isLunarRule) {
                val l = LunarCalendar.solarToLunar(s)
                val uid = if (e.isMonthly) "${e.id}-${l.year}-${l.month}${if (l.leap) "n" else ""}" else "${e.id}-${l.year}"
                Occurrence(s, uid, l.month, l.leap, l.year, adjustedTo = l.day.takeIf { it != e.lunarDay })
            } else when (e.repeat) {
                LunarEvent.MONTHLY -> Occurrence(s, "${e.id}-${s.year}-${s.month}", adjustedTo = s.day.takeIf { it != e.lunarDay })
                LunarEvent.YEARLY -> Occurrence(s, "${e.id}-${s.year}", adjustedTo = s.day.takeIf { it != e.lunarDay })
                else -> Occurrence(s, "${e.id}-$s")
            }
        }
        val skipped = ArrayList<SkippedYear>()
        if (e.calendar == LunarEvent.LUNAR && e.repeat == LunarEvent.YEARLY && e.isLeapMonth) {
            for (ly in startYear..endYear) {
                val r = Conversion.lunarToSolar(e.lunarDay, e.lunarMonth, ly, true)
                if (r is LunarToSolar.Fail) skipped += SkippedYear(e.title, ly, r.reason)
            }
        }
        return list to skipped
    }
}
