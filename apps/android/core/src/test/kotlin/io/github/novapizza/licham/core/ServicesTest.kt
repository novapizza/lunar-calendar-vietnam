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

    @Test
    fun auspiciousFindsMatchingDays() {
        val days = Auspicious.find("WEDDING", SolarDate(2026, 11, 21))
        assertTrue(days.isNotEmpty())
        days.forEach { assertTrue("wedding" in DayInfo.of(it.date).activities) }
    }
}
