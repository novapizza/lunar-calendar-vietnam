package io.github.novapizza.licham.core

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One occurrence skipped during export (a leap month that does not exist that year, …). */
data class SkippedYear(val title: String, val lunarYear: Int, val reason: String)

data class IcsResult(val text: String, val totalEvents: Int, val skipped: List<SkippedYear>) {
    val skippedCount: Int get() = skipped.size
}

/**
 * RFC 5545 export of lunar events, same output as the web app's icsService:
 * one all-day VEVENT per occurrence, resolved per lunar year and kept when its
 * real solar date falls inside [startYear] … [startYear] + [yearCount] - 1.
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

    fun utcStamp(now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): String =
        now.withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

    fun build(events: List<LunarEvent>, startYear: Int, yearCount: Int, dtstamp: String = utcStamp()): IcsResult {
        val endYear = startYear + yearCount - 1
        val vevents = ArrayList<String>()
        val skipped = ArrayList<SkippedYear>()
        for (event in events) {
            for (lunarYear in (startYear - 1)..endYear) {
                when (val r = Conversion.lunarToSolar(event.lunarDay, event.lunarMonth, lunarYear, event.isLeapMonth)) {
                    is LunarToSolar.Fail -> if (lunarYear >= startYear) skipped += SkippedYear(event.title, lunarYear, r.reason)
                    is LunarToSolar.Ok -> {
                        if (r.solar.year < startYear || r.solar.year > endYear) continue
                        vevents += vevent(event, lunarYear, r.solar, r.adjustedDay, dtstamp)
                    }
                }
            }
        }
        val header = listOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Lịch Âm Việt Nam//VI", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
            fold("X-WR-CALNAME:Sự kiện âm lịch Việt Nam"),
        ).joinToString("\r\n")
        val text = "$header\r\n${vevents.joinToString("\r\n")}\r\nEND:VCALENDAR"
        return IcsResult(text, vevents.size, skipped)
    }

    private fun vevent(e: LunarEvent, lunarYear: Int, solar: SolarDate, adjusted: Boolean, dtstamp: String): String {
        val leapTag = if (e.isLeapMonth) " Nhuận" else ""
        val summary = escape("${e.title} (Âm ${e.lunarDay}/${e.lunarMonth}$leapTag)")
        val note = if (adjusted) "\nTháng ${e.lunarMonth} năm nay chỉ có 29 ngày nên sự kiện được đặt vào ngày 29." else ""
        val description = escape("Sự kiện âm lịch ngày ${e.lunarDay} tháng ${e.lunarMonth}$leapTag (năm âm $lunarYear)$note\nTạo bởi Lịch Âm Việt Nam")
        return listOf(
            "BEGIN:VEVENT",
            fold("UID:${e.id}-$lunarYear@lunar-vn"),
            "DTSTAMP:$dtstamp",
            "DTSTART;VALUE=DATE:${ymd(solar)}",
            "DTEND;VALUE=DATE:${ymd(solar.plusDays(1))}",
            fold("SUMMARY:$summary"),
            fold("DESCRIPTION:$description"),
            "END:VEVENT",
        ).joinToString("\r\n")
    }

    /** File name the web app uses. */
    fun fileName(startYear: Int, yearCount: Int) = "lich-am-su-kien-$startYear-${startYear + yearCount - 1}.ics"
}
