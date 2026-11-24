package io.github.novapizza.licham.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BuiltinRemindersTest {
    private val today = SolarDate(2026, 11, 24)

    @Test
    fun builtInDaysCarryTheirRef() {
        val dec = Upcoming.month(today, 1).associate { it.title to it.ref }
        assertEquals(BuiltinReminders.MONG_1, dec["Mồng 1 tháng 11"])
        assertEquals(BuiltinReminders.RAM, dec["Ngày rằm tháng 11"])
        val jan = Upcoming.month(today, 2).associate { it.title to it.ref }
        assertEquals("Tết Dương Lịch", jan["Tết Dương Lịch"])
    }

    @Test
    fun repeatsOffered() {
        assertEquals(listOf(LunarEvent.MONTHLY, LunarEvent.YEARLY, LunarEvent.NONE), BuiltinReminders.repeats(BuiltinReminders.MONG_1))
        assertEquals(listOf(LunarEvent.YEARLY, LunarEvent.NONE), BuiltinReminders.repeats("Tết Dương Lịch"))
    }

    /** The web's form saves this for "Mồng 1 tháng 11" (9/12/2026) as it opens; same fires (checked in the browser 2026-11-24). */
    @Test
    fun monthlyMong1FiresAtEightOnEveryFirst() {
        val r = LunarEvent(
            "r", BuiltinReminders.MONG_1, 1, 11, false, 0, calendar = LunarEvent.LUNAR, repeat = LunarEvent.MONTHLY,
            start = "2026-12-09", reminders = listOf(0), ref = BuiltinReminders.MONG_1,
        )
        val fires = PersonalEvents.remindersBetween(listOf(r), LocalDateTime.of(2026, 12, 1, 0, 0), LocalDateTime.of(2027, 3, 1, 0, 0))
        assertEquals(
            listOf(LocalDateTime.of(2026, 12, 9, 8, 0), LocalDateTime.of(2027, 1, 8, 8, 0), LocalDateTime.of(2027, 2, 6, 8, 0)),
            fires.map { it.at },
        )
        assertTrue(BuiltinReminders.reminds(listOf(r), BuiltinReminders.MONG_1, SolarDate(2027, 1, 8)))
        assertFalse(BuiltinReminders.reminds(listOf(r), BuiltinReminders.MONG_1, SolarDate(2026, 11, 9)))
        assertFalse(BuiltinReminders.reminds(listOf(r), BuiltinReminders.RAM, SolarDate(2026, 12, 23)))
    }
}
