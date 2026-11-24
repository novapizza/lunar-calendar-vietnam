package io.github.novapizza.licham.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SmallDateTest {
    @Test
    fun lunarDayShowsItsMonthOnTheFirstAndWhenSelected() {
        val l = LunarDate(16, 10, 2026, false)
        assertEquals("16", SmallDate.lunar(l, selected = false).text)
        assertEquals("16/10", SmallDate.lunar(l, selected = true).text)
        assertEquals("1/10", SmallDate.lunar(LunarDate(1, 10, 2026, false), selected = false).text)
    }

    @Test
    fun leapMonthIsMarkedOnlyWithTheMonth() {
        // 2025 has a leap 6th lunar month: 2025-08-08 is 15/6 leap (shared/test-vectors/events.json).
        val l = LunarCalendar.solarToLunar(SolarDate(2025, 8, 8))
        assertEquals(LunarDate(15, 6, 2025, true), l)
        assertEquals(SmallDate(15), SmallDate.lunar(l, selected = false))
        assertEquals(SmallDate(15, 6, true), SmallDate.lunar(l, selected = true))
        assertEquals("15/6n", SmallDate.lunar(l, selected = true).text)
    }

    @Test
    fun solarDayShowsItsMonthOnTheFirstAndWhenSelected() {
        assertEquals("24", SmallDate.solar(SolarDate(2026, 11, 24), selected = false).text)
        assertEquals("24/11", SmallDate.solar(SolarDate(2026, 11, 24), selected = true).text)
        assertEquals("1/12", SmallDate.solar(SolarDate(2026, 12, 1), selected = false).text)
    }
}
