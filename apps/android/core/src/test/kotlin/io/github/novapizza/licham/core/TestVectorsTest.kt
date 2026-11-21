package io.github.novapizza.licham.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Kotlin port must reproduce shared/test-vectors exactly: they come from the
 * library the web app uses, and the web app reproduces them with 0 mismatches.
 */
class TestVectorsTest {
    private val dir = File(System.getProperty("testVectors") ?: "../../../shared/test-vectors")
    private fun read(name: String): JsonObject = Json.parseToJsonElement(File(dir, name).readText()).jsonObject
    private fun JsonArray.str(i: Int) = this[i].jsonPrimitive.content

    @Test
    fun lunarDays() {
        val rows = read("lunar-days.json")["rows"]!!.jsonArray
        assertTrue("expected the full vector set", rows.size > 10_000)
        val mismatches = mutableListOf<String>()
        for (r in rows.map { it.jsonArray }) {
            val date = SolarDate.parse(r.str(0))!!
            val info = DayInfo.of(date)
            val expected = listOf(
                r.str(0), r[1].jsonPrimitive.int, r[2].jsonPrimitive.int, r[3].jsonPrimitive.int, r[4].jsonPrimitive.int,
                r.str(5), r.str(6), r.str(7), r.str(8), r.str(9), r.str(10),
            )
            val actual = listOf(
                date.toString(), info.lunar.day, info.lunar.month, info.lunar.year, if (info.lunar.leap) 1 else 0,
                info.canChiDay, info.canChiMonth, info.canChiYear, info.truc, info.rating.name.lowercase(),
                info.gioHoangDao.joinToString(" ") { it.chiIndex.toString() },
            )
            if (expected != actual) mismatches += "expected $expected\n   actual $actual"
        }
        assertEquals("${mismatches.size} of ${rows.size} rows differ, first:\n${mismatches.take(5).joinToString("\n")}", 0, mismatches.size)
    }

    @Test
    fun lunarToSolarRoundTrip() {
        // Every vector row converted back must land on the same solar date.
        val rows = read("lunar-days.json")["rows"]!!.jsonArray.map { it.jsonArray }
        val bad = rows.filter { r ->
            val back = LunarCalendar.lunarToSolar(r[1].jsonPrimitive.int, r[2].jsonPrimitive.int, r[3].jsonPrimitive.int, r[4].jsonPrimitive.int == 1)
            back?.toString() != r.str(0)
        }
        assertEquals("first: ${bad.take(3)}", 0, bad.size)
    }

    @Test
    fun menhYears() {
        val years = read("menh.json")["years"]!!.jsonObject["rows"]!!.jsonArray.map { it.jsonArray }
        val bad = years.filter { r ->
            val m = MenhCalc.ofLunarYear(r[0].jsonPrimitive.int)
            listOf(m.canChi, m.napAm, m.element.key) != listOf(r.str(1), r.str(2), r.str(3))
        }
        assertEquals(200, years.size)
        assertEquals("first: ${bad.take(3)}", 0, bad.size)
    }

    @Test
    fun menhBirthDates() {
        val rows = read("menh.json")["birthDates"]!!.jsonObject["rows"]!!.jsonArray.map { it.jsonArray }
        val bad = rows.filter { r -> MenhCalc.ofBirthDate(SolarDate.parse(r.str(0))!!)?.lunarYear != r[1].jsonPrimitive.int }
        assertTrue(rows.size > 390)
        assertEquals("first: ${bad.take(3)}", 0, bad.size)
    }

    @Test
    fun knownDates() {
        // Spot checks that read like the app: Tết 2026 and the day before it.
        assertEquals(LunarDate(1, 1, 2026, false), LunarCalendar.solarToLunar(2026, 2, 17))
        assertEquals("Kỷ Tỵ", MenhCalc.ofBirthDate(SolarDate(1990, 1, 26))!!.canChi)
        assertEquals(Element.THO, MenhCalc.ofBirthDate(SolarDate(1990, 1, 27))!!.element)
        assertEquals(Element.HOA, MenhCalc.paletteFor(MenhCalc.ofLunarYear(1990), AccentPref.TUONG_SINH))
    }
}
