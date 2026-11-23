package io.github.novapizza.licham.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesTest {
    @Test
    fun monthGridStartsMondayEndsSunday() {
        val g = monthGrid(2026, 11)
        assertEquals(SolarDate(2026, 10, 26), g.first())
        assertEquals(SolarDate(2026, 12, 6), g.last())
        assertEquals(0, g.size % 7)
    }

    @Test
    fun subtitleAndWeek() {
        assertEquals("Tháng 9 – 10 âm lịch · Bính Ngọ", lunarMonthSubtitle(2026, 11))
        assertEquals(47, SolarDate(2026, 11, 21).weekOfYear)
    }

    @Test
    fun upcomingStartsWithNamedOrLunarDays() {
        val ev = Upcoming.events(SolarDate(2026, 11, 21))
        assertEquals(12, ev.size)
        // 21/11/2026 is 13/10 âm; the next lunar day of note is rằm tháng 10 (23/11), which has
        // its own name, Tết Hạ nguyên, in place of "Ngày rằm tháng 10".
        assertEquals("Tết Hạ nguyên", ev.first().title)
        assertEquals(SolarDate(2026, 11, 23), ev.first().solarDate)
    }

    @Test
    fun holidaysObservancesAndFestivals() {
        // 27/7 is a day off only from 2027; before that it is a commemorative day.
        val jul2026 = DayInfo.of(SolarDate(2026, 7, 27))
        assertEquals(null, jul2026.holiday)
        assertEquals("Ngày Thương binh – Liệt sĩ", jul2026.observance)
        val jul2027 = DayInfo.of(SolarDate(2027, 7, 27))
        assertEquals("Ngày Thương binh – Liệt sĩ", jul2027.holiday)
        assertEquals(null, jul2027.observance)
        // 24/11 is a day off from 2026 (Nghị quyết 28/2026/QH16).
        assertEquals("Ngày Văn hóa Việt Nam", DayInfo.of(SolarDate(2026, 11, 24)).holiday)
        assertEquals(null, DayInfo.of(SolarDate(2025, 11, 24)).holiday)
        assertEquals("Ngày sinh Chủ tịch Hồ Chí Minh", DayInfo.of(SolarDate(2026, 5, 19)).observance)
        // 18/11/2026 is 10/10 âm: the Ông Hoàng Mười festival, listed as a festival.
        val nov18 = DayInfo.of(SolarDate(2026, 11, 18))
        assertEquals(10, nov18.lunar.day); assertEquals(10, nov18.lunar.month)
        assertEquals("Lễ hội đền Ông Hoàng Mười", nov18.observance)
        val ev = Upcoming.month(SolarDate(2026, 11, 1), 0).filter { it.solarDate == SolarDate(2026, 11, 18) }
        assertTrue(ev.toString(), ev.any { it.kind == EventKind.FESTIVAL && it.title == "Lễ hội đền Ông Hoàng Mười" })
    }

    @Test
    fun conversionFallsBackToDay29() {
        // Every lunar month has 29 or 30 days; day 30 must always resolve (exactly or adjusted).
        for (y in 2020..2030) for (m in 1..12) {
            assertTrue("$m/$y", Conversion.lunarToSolar(30, m, y, false) is LunarToSolar.Ok)
        }
        assertEquals(LunarToSolar.Fail("leap_month_not_found"), Conversion.lunarToSolar(1, 1, 2026, true))
    }

    @Test
    fun icsFoldsAndCounts() {
        val e = LunarEvent("e1", "Giỗ ông nội, có dấu phẩy; và chấm phẩy", 10, 3, false, 0)
        val r = Ics.build(listOf(e), 2026, 3, dtstamp = "20261121T000000Z")
        assertEquals(3, r.totalEvents)
        assertTrue(r.text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(r.text.contains("SUMMARY:Giỗ ông nội\\, có dấu phẩy\\; và chấm phẩy (Âm 10/3)"))
        r.text.split("\r\n").forEach { assertTrue(it, it.toByteArray(Charsets.UTF_8).size <= 75) }
    }

    private fun ev(day: Int, month: Int = 1, calendar: String = LunarEvent.LUNAR, repeat: String = LunarEvent.YEARLY, leap: Boolean = false) =
        LunarEvent("x", "t", day, month, leap, 0, calendar, repeat)

    /** Days of 2024-2028 an event falls on, scanned one by one. */
    private fun scan(e: LunarEvent): List<SolarDate> {
        val out = ArrayList<SolarDate>()
        var d = SolarDate(2024, 1, 1)
        while (d.year <= 2028) { if (PersonalEvents.occursOn(e, d)) out += d; d = d.plusDays(1) }
        return out
    }

    @Test
    fun personalEventsMatchTheirOccurrences() {
        // Same cases and counts as the web app's personalEvents.ts (measured in the browser 2026-11-23).
        data class Case(val e: LunarEvent, val count: Int, val adjusted: Int, val first: SolarDate)
        val cases = listOf(
            Case(ev(10, 3), 5, 0, SolarDate(2024, 4, 18)),
            Case(ev(30, repeat = LunarEvent.MONTHLY), 62, 30, SolarDate(2024, 1, 10)),
            Case(ev(15, repeat = LunarEvent.MONTHLY), 62, 0, SolarDate(2024, 1, 25)),
            Case(ev(15, 6, leap = true), 1, 0, SolarDate(2025, 8, 8)),
            Case(ev(29, 2, LunarEvent.SOLAR), 5, 3, SolarDate(2024, 2, 29)),
            Case(ev(31, calendar = LunarEvent.SOLAR, repeat = LunarEvent.MONTHLY), 60, 25, SolarDate(2024, 1, 31)),
        )
        for (c in cases) {
            val days = scan(c.e)
            val (list, _) = PersonalEvents.occurrences(c.e, 2024, 5)
            assertEquals(c.e.toString(), days, list.map { it.solar })
            assertEquals(c.e.toString(), c.count, days.size)
            assertEquals(c.e.toString(), c.adjusted, list.count { it.adjustedTo != null })
            assertEquals(c.e.toString(), c.first, days.first())
        }
        assertEquals(listOf(2024, 2026, 2027, 2028), PersonalEvents.occurrences(ev(15, 6, leap = true), 2024, 5).second.map { it.lunarYear })
    }

    @Test
    fun personalEventsKeepTheOldStoredShape() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(LunarEvent.serializer(), """{"id":"a","title":"Giỗ","lunarDay":10,"lunarMonth":3,"isLeapMonth":false,"createdAt":1}""")
        assertEquals(LunarEvent.LUNAR, old.calendar)
        assertEquals(LunarEvent.YEARLY, old.repeat)
        // Lunar yearly events are saved without the new fields, as before (and as the web app).
        assertEquals(false, json.encodeToString(LunarEvent.serializer(), old).contains("calendar"))
        assertEquals("""{"id":"a","title":"Giỗ","lunarDay":10,"lunarMonth":3,"isLeapMonth":false,"createdAt":1}""", json.encodeToString(LunarEvent.serializer(), old))
        assertEquals(true, old.allDay)
        assertEquals(emptyList<Int>(), old.reminders)
        val solar = old.copy(calendar = LunarEvent.SOLAR, repeat = LunarEvent.MONTHLY)
        assertEquals(solar, json.decodeFromString(LunarEvent.serializer(), json.encodeToString(LunarEvent.serializer(), solar)))
        val full = solar.copy(repeat = LunarEvent.WEEKLY, start = "2026-11-23", weekdays = listOf(0, 3), count = 4, exceptions = listOf("2026-11-26"),
            allDay = false, startTime = "07:30", days = 2, reminders = listOf(0, 30), notes = "n", location = "l", color = "clash")
        assertEquals(full, json.decodeFromString(LunarEvent.serializer(), json.encodeToString(LunarEvent.serializer(), full)))
        // The web may write nulls for absent values: they read as the defaults (AppState's Json coerces).
        val lenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val fromWeb = lenient.decodeFromString(LunarEvent.serializer(), """{"id":"a","title":"t","lunarDay":1,"lunarMonth":1,"isLeapMonth":false,"createdAt":1,"allDay":null,"start":null,"reminders":null,"color":"brand","extra":1}""")
        assertEquals(true, fromWeb.allDay)
        assertEquals("brand", fromWeb.color)
    }

    @Test
    fun personalEventsInListAndExport() {
        val today = SolarDate(2026, 11, 23)
        val mine = listOf(LunarEvent("m", "Sinh nhật mẹ", 25, 11, false, 0, LunarEvent.SOLAR))
        val nov = Upcoming.month(today, 0, mine).filter { it.kind == EventKind.PERSONAL }
        assertEquals(listOf(SolarDate(2026, 11, 25) to 2), nov.map { it.solarDate to it.daysUntil })
        // Solar yearly: one VEVENT with an RRULE; lunar monthly: one per occurrence (24 in 2 years).
        val r = Ics.build(mine + ev(10, repeat = LunarEvent.MONTHLY), 2026, 2, dtstamp = "20261121T000000Z")
        assertEquals(1 + 24, r.totalEvents)
        assertEquals(r.totalEvents, Ics.count(mine + ev(10, repeat = LunarEvent.MONTHLY), 2026, 2))
        assertTrue(r.text, r.text.contains("UID:m@lunar-vn\r\nDTSTAMP:20261121T000000Z\r\nDTSTART;VALUE=DATE:20261125\r\nDTEND;VALUE=DATE:20261126\r\nRRULE:FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=25\r\n"))
        assertTrue(r.text.contains("SUMMARY:Sinh nhật mẹ\r\n"))
        assertTrue(r.text.contains("SUMMARY:t (Âm 10/12)"))
    }

    @Test
    fun icsOldLunarYearlyUnchanged() {
        // The export of an old stored event, byte for byte as before the new fields (2026-11-23).
        val e = LunarEvent("g", "Giỗ", 10, 3, false, 0)
        val r = Ics.build(listOf(e), 2026, 1, dtstamp = "20261121T000000Z")
        val expected = listOf(
            "BEGIN:VEVENT", "UID:g-2026@lunar-vn", "DTSTAMP:20261121T000000Z", "DTSTART;VALUE=DATE:20260426", "DTEND;VALUE=DATE:20260427",
            "SUMMARY:Giỗ (Âm 10/3)", "DESCRIPTION:Sự kiện âm lịch ngày 10 tháng 3 (năm âm 2026)\\nTạo\r\n  bởi Lịch Âm Việt Nam", "END:VEVENT",
        ).joinToString("\r\n")
        assertTrue(r.text, r.text.contains(expected))
        assertEquals(1, r.totalEvents)
    }

    @Test
    fun icsRulesTimesAndAlarms() {
        val weekly = LunarEvent(
            "w", "Họp", 23, 11, false, 0, LunarEvent.SOLAR, LunarEvent.WEEKLY, start = "2026-11-23", weekdays = listOf(3, 0),
            until = "2026-12-31", exceptions = listOf("2026-12-03"), allDay = false, startTime = "09:30", endTime = "10:15",
            reminders = listOf(15, 1440), notes = "Phòng 2", location = "Hà Nội",
        )
        val allDay = LunarEvent("a", "Đi chơi", 24, 12, false, 0, LunarEvent.SOLAR, LunarEvent.DAILY, start = "2026-12-24", count = 3, days = 2, reminders = listOf(0, 1440))
        val lunar = LunarEvent("l", "Rằm", 15, 1, false, 0, LunarEvent.LUNAR, LunarEvent.MONTHLY, allDay = false, startTime = "18:00", endTime = "19:00")
        val text = Ics.build(listOf(weekly, allDay, lunar), 2026, 1, dtstamp = "20261121T000000Z").text
        listOf(
            "DTSTART:20261123T093000\r\nDTEND:20261123T101500\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261231T235959\r\nEXDATE:20261203T093000",
            "DESCRIPTION:Phòng 2\\nTạo bởi Lịch Âm Việt Nam\r\nLOCATION:Hà Nội\r\nBEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:Họp\r\nTRIGGER:-PT15M\r\nEND:VALARM",
            "TRIGGER:-P1D",
            "DTSTART;VALUE=DATE:20261224\r\nDTEND;VALUE=DATE:20261226\r\nRRULE:FREQ=DAILY;COUNT=3",
            "TRIGGER:PT8H\r\n", "TRIGGER:-PT16H\r\n",
            "UID:l-2026-1@lunar-vn\r\nDTSTAMP:20261121T000000Z\r\nDTSTART:20260303T180000\r\nDTEND:20260303T190000",
        ).forEach { assertTrue(it, text.contains(it)) }
    }

    @Test
    fun personalEventsWordsAndReminders() {
        val e = LunarEvent("w", "Họp", 23, 11, false, 0, LunarEvent.SOLAR, LunarEvent.WEEKLY, start = "2026-11-23", weekdays = listOf(3, 0), count = 10)
        assertEquals("Hằng tuần vào T2, T5 · 10 lần", PersonalEvents.describe(e))
        assertEquals("Hằng năm vào 10/3 âm", PersonalEvents.describe(LunarEvent("g", "Giỗ", 10, 3, false, 0)))
        assertEquals("Hằng tháng vào ngày 1 âm · đến 31/12/2030", PersonalEvents.describe(LunarEvent("g", "Giỗ", 1, 3, false, 0, repeat = LunarEvent.MONTHLY, until = "2030-12-31")))
        assertEquals("Không lặp · 24/12/2026", PersonalEvents.describe(LunarEvent("n", "t", 24, 12, false, 0, LunarEvent.SOLAR, LunarEvent.NONE, start = "2026-12-24")))
        val today = SolarDate(2026, 11, 25)
        // 25/11/2026 is 17/10 âm (23/11 is rằm tháng 10).
        assertEquals("Hôm nay · Cả ngày · 17/10 âm", PersonalEvents.reminderText(LunarEvent("g", "Giỗ", 17, 10, false, 0), today, today))
        assertEquals("Ngày mai · 09:00 – 10:00", PersonalEvents.reminderText(e.copy(allDay = false), SolarDate(2026, 11, 26), today))
        assertEquals("Thứ Sáu 27/11 · Cả ngày", PersonalEvents.reminderText(e, SolarDate(2026, 11, 27), today))
        // Day lists: all-day first, then timed by start time.
        val a = LunarEvent("a", "A", 25, 11, false, 0, LunarEvent.SOLAR, LunarEvent.NONE, start = "2026-11-25", allDay = false, startTime = "14:00")
        val b = LunarEvent("b", "B", 25, 11, false, 0, LunarEvent.SOLAR, LunarEvent.NONE, start = "2026-11-25", allDay = false, startTime = "08:00")
        val c = LunarEvent("c", "C", 24, 11, false, 0, LunarEvent.SOLAR, LunarEvent.NONE, start = "2026-11-24", days = 2)
        assertEquals(listOf("C", "B", "A"), PersonalEvents.on(listOf(a, b, c), today).map { it.title })
    }

    @Test
    fun auspiciousFindsMatchingDays() {
        val days = Auspicious.find("WEDDING", SolarDate(2026, 11, 21))
        assertTrue(days.isNotEmpty())
        days.forEach { assertTrue("wedding" in DayInfo.of(it.date).activities) }
    }

    @Test
    fun oldNotesBecomeOneOffEvents() {
        // Same results as the web app's noteToEvent (checked in the browser 2026-11-23).
        val short = PersonalEvents.noteToEvent(UserNote("n1", "2026-11-24", "Mua hoa quả cúng rằm", 1))
        assertEquals("Mua hoa quả cúng rằm", short.title)
        assertEquals(null, short.notes)
        assertEquals(LunarEvent.NONE, short.repeat)
        assertEquals("2026-11-24", short.start)
        assertEquals(listOf(SolarDate(2026, 11, 24)), PersonalEvents.occurrenceStarts(short, SolarDate(2026, 1, 1), SolarDate(2027, 12, 31)))
        val long = PersonalEvents.noteToEvent(UserNote("n2", "2026-11-27", "Gọi điện cho bác Hai\nhỏi chuyện đám cưới tháng sau, nhớ mang quà", 2))
        assertEquals("Gọi điện cho bác Hai", long.title)
        assertEquals("Gọi điện cho bác Hai\nhỏi chuyện đám cưới tháng sau, nhớ mang quà", long.notes)
        val cut = PersonalEvents.noteToEvent(UserNote("n3", "2026-11-28", "x".repeat(80), 3))
        assertEquals("x".repeat(59) + "…", cut.title)
    }
}
