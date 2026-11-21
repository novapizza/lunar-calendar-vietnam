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
        // Next lunar 1st after 13/10 is 1/11 (Mồng 1 tháng 11).
        assertTrue(ev.first().title, ev.first().title.startsWith("Ngày rằm") || ev.first().title.startsWith("Mồng 1"))
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
