package io.github.novapizza.licham.core

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One occurrence skipped during export (a leap month that does not exist that year, …). */
data class SkippedYear(val title: String, val lunarYear: Int, val reason: String)

data class IcsResult(val text: String, val totalEvents: Int, val skipped: List<SkippedYear>) {
    val skippedCount: Int get() = skipped.size
}

/**
 * RFC 5545 export of personal events (shared/design/events.md, "Export"): one VEVENT with an RRULE
 * for none, daily, weekly, solar monthly with day ≤ 28 and solar yearly except 29/2
 * (PersonalEvents.exportsAsRule); one VEVENT per occurrence whose start falls inside [startYear] …
 * [startYear] + [yearCount] - 1 for the rest (lunar repeats, solar day > 28), as the web app's
 * icsService. Timed events use floating local times; reminders become VALARM DISPLAY.
 */
object Ics {
    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** Folds a line at 75 UTF-8 bytes; continuation lines start with one space. */
    fun fold(line: String): String {
        val parts = ArrayList<String>()
        var remaining = line
        var max = 75
        while (remaining.toByteArray(Charsets.UTF_8).size > max) {
            var bytes = 0
            var split = 0
            var i = 0
            while (i < remaining.length) {
                val cp = remaining.codePointAt(i)
                val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
                if (bytes + len > max) break
                bytes += len
                i += Character.charCount(cp)
                split = i
            }
            parts += remaining.substring(0, split)
            remaining = remaining.substring(split)
            max = 74
        }
        parts += remaining
        return parts.joinToString("\r\n ")
    }

    private fun ymd(d: SolarDate) = "%04d%02d%02d".format(d.year, d.month, d.day)
    private fun local(t: LocalDateTime) = "%04d%02d%02dT%02d%02d00".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
    private fun time(s: String): LocalTime = runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.of(9, 0))

    fun utcStamp(now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): String =
        now.withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

    fun build(events: List<LunarEvent>, startYear: Int, yearCount: Int, dtstamp: String = utcStamp()): IcsResult {
        val vevents = ArrayList<String>()
        val skipped = ArrayList<SkippedYear>()
        for (event in events) {
            if (PersonalEvents.exportsAsRule(event)) {
                ruleEvent(event, startYear, dtstamp)?.let { vevents += it }
                continue
            }
            val (list, skip) = PersonalEvents.occurrences(event, startYear, yearCount)
            skipped += skip
            list.forEach { vevents += vevent(event, it, dtstamp) }
        }
        val header = listOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Lịch Âm Việt Nam//VI", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
            fold("X-WR-CALNAME:Sự kiện âm lịch Việt Nam"),
        ).joinToString("\r\n")
        val text = "$header\r\n${vevents.joinToString("\r\n")}\r\nEND:VCALENDAR"
        return IcsResult(text, vevents.size, skipped)
    }

    /** How many VEVENTs [build] writes (the export button's count). */
    fun count(events: List<LunarEvent>, startYear: Int, yearCount: Int): Int = events.sumOf { e ->
        if (PersonalEvents.exportsAsRule(e)) (if (PersonalEvents.firstCandidate(e, SolarDate(startYear, 1, 1)) != null) 1 else 0)
        else PersonalEvents.occurrences(e, startYear, yearCount).first.size
    }

    /** DTSTART / DTEND of the occurrence starting on [start]: dates when all day, else floating local times. */
    private fun span(e: LunarEvent, start: SolarDate): List<String> {
        val last = start.plusDays((e.span - 1).toLong())
        if (e.allDay) return listOf("DTSTART;VALUE=DATE:${ymd(start)}", "DTEND;VALUE=DATE:${ymd(last.plusDays(1))}")
        val from = start.toLocalDate().atTime(time(e.startTime))
        var to = last.toLocalDate().atTime(time(e.endTime))
        if (to <= from) to = from.plusHours(1)
        return listOf("DTSTART:${local(from)}", "DTEND:${local(to)}")
    }

    /** "-PT15M", "PT8H", "-PT16H", "-P1D": an RFC 5545 duration of [minutes] (negative = before). */
    fun duration(minutes: Int): String {
        val sign = if (minutes < 0) "-" else ""
        var m = kotlin.math.abs(minutes)
        if (m == 0) return "PT0M"
        if (m % 1440 == 0) return "${sign}P${m / 1440}D"
        val h = m / 60
        m %= 60
        return sign + "PT" + (if (h > 0) "${h}H" else "") + (if (m > 0) "${m}M" else "")
    }

    /** VALARM DISPLAY per reminder, relative to DTSTART (all day: DTSTART is midnight, the base 08:00). */
    private fun alarms(e: LunarEvent): List<String> = e.reminders.distinct().sorted().flatMap { m ->
        listOf(
            "BEGIN:VALARM", "ACTION:DISPLAY", fold("DESCRIPTION:${escape(e.title)}"),
            "TRIGGER:${duration(if (e.allDay) 8 * 60 - m else -m)}", "END:VALARM",
        )
    }

    private fun extras(e: LunarEvent): List<String> = listOfNotNull(e.location?.takeIf { it.isNotBlank() }?.let { fold("LOCATION:${escape(it.trim())}") })

    private val BYDAY = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    /** One VEVENT with an RRULE (PersonalEvents.exportsAsRule); null when the rule has no occurrence. */
    private fun ruleEvent(e: LunarEvent, startYear: Int, dtstamp: String): String? {
        // The first candidate: COUNT counts from it and EXDATE removes the exceptions after.
        val first = PersonalEvents.firstCandidate(e, SolarDate(startYear, 1, 1)) ?: return null
        val rule = when (e.repeat) {
            LunarEvent.NONE -> null
            LunarEvent.DAILY -> "FREQ=DAILY"
            LunarEvent.WEEKLY -> "FREQ=WEEKLY;BYDAY=" + PersonalEvents.weekdaysOf(e).sorted().joinToString(",") { BYDAY[it] }
            LunarEvent.MONTHLY -> "FREQ=MONTHLY;BYMONTHDAY=${e.lunarDay}"
            else -> "FREQ=YEARLY;BYMONTH=${e.lunarMonth};BYMONTHDAY=${e.lunarDay}"
        }?.let { r ->
            val until = PersonalEvents.untilOf(e)
            when {
                // UNTIL takes the type of DTSTART: a date, or the end of that day in floating local time.
                until != null -> r + ";UNTIL=" + if (e.allDay) ymd(until) else "${ymd(until)}T235959"
                e.count != null && e.start != null -> "$r;COUNT=${e.count}"
                else -> r
            }
        }
        val exdates = if (rule == null) emptyList() else e.exceptions.mapNotNull(SolarDate::parse).sorted().map { d ->
            if (e.allDay) "EXDATE;VALUE=DATE:${ymd(d)}" else "EXDATE:${local(d.toLocalDate().atTime(time(e.startTime)))}"
        }
        val description = (e.notes?.takeIf { it.isNotBlank() }?.trim()?.let { "$it\n" } ?: "") + "Tạo bởi Lịch Âm Việt Nam"
        return (
            listOf("BEGIN:VEVENT", fold("UID:${e.id}@lunar-vn"), "DTSTAMP:$dtstamp") + span(e, first) +
                listOfNotNull(rule?.let { "RRULE:$it" }) + exdates +
                listOf(fold("SUMMARY:${escape(e.title)}"), fold("DESCRIPTION:${escape(description)}")) + extras(e) + alarms(e) +
                "END:VEVENT"
            ).joinToString("\r\n")
    }

    /** One occurrence of an expanded event (lunar repeats, solar day > 28): the export of the old app, plus times and reminders. */
    private fun vevent(e: LunarEvent, o: Occurrence, dtstamp: String): String {
        val summary: String
        val about: String
        var note = ""
        if (o.lunarMonth != null) {
            val leapTag = if (o.lunarLeap) " Nhuận" else ""
            summary = "${e.title} (Âm ${e.lunarDay}/${o.lunarMonth}$leapTag)"
            about = if (e.isMonthly) "Sự kiện âm lịch ngày ${e.lunarDay} hằng tháng, tháng ${o.lunarMonth}$leapTag (năm âm ${o.lunarYear})"
            else "Sự kiện âm lịch ngày ${e.lunarDay} tháng ${e.lunarMonth}$leapTag (năm âm ${o.lunarYear})"
            if (o.adjustedTo != null) note = "\nTháng ${o.lunarMonth} năm nay chỉ có 29 ngày nên sự kiện được đặt vào ngày 29."
        } else {
            summary = e.title
            about = if (e.isMonthly) "Sự kiện dương lịch ngày ${e.lunarDay} hằng tháng"
            else "Sự kiện dương lịch ngày ${e.lunarDay} tháng ${e.lunarMonth} hằng năm"
            if (o.adjustedTo != null) note = "\nTháng ${o.solar.month} chỉ có ${o.adjustedTo} ngày nên sự kiện được đặt vào ngày ${o.adjustedTo}."
        }
        val notes = e.notes?.takeIf { it.isNotBlank() }?.trim()?.let { "\n$it" } ?: ""
        val description = escape("$about$note$notes\nTạo bởi Lịch Âm Việt Nam")
        return (
            listOf("BEGIN:VEVENT", fold("UID:${o.uid}@lunar-vn"), "DTSTAMP:$dtstamp") + span(e, o.solar) +
                listOf(fold("SUMMARY:${escape(summary)}"), fold("DESCRIPTION:$description")) + extras(e) + alarms(e) +
                "END:VEVENT"
            ).joinToString("\r\n")
    }

    /** File name the web app uses. */
    fun fileName(startYear: Int, yearCount: Int) = "lich-am-su-kien-$startYear-${startYear + yearCount - 1}.ics"
}
