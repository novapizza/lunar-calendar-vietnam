package io.github.novapizza.licham.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * shared/test-vectors/events.json: personal event dates both apps must reproduce
 * (shared/design/events.md; the web app's personalEvents.ts reproduces them).
 */
class EventVectorsTest {
    private val dir = File(System.getProperty("testVectors") ?: "../../../shared/test-vectors")
    private val root: JsonObject = Json.parseToJsonElement(File(dir, "events.json").readText()).jsonObject
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val minute = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun event(e: JsonElement) = json.decodeFromJsonElement(LunarEvent.serializer(), e)
    private fun day(e: JsonElement) = SolarDate.parse(e.jsonPrimitive.content)!!
    private fun days(e: JsonElement?) = e!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun starts() {
        val range = root["range"]!!.jsonObject
        val from = day(range["from"]!!)
        val to = day(range["to"]!!)
        val cases = root["cases"]!!.jsonArray.map { it.jsonObject }
        assertTrue("expected the full case list", cases.size >= 12)
        val bad = cases.mapNotNull { c ->
            val expected = days(c["starts"])
            val actual = PersonalEvents.occurrenceStarts(event(c["event"]!!), from, to).map { it.toString() }
            if (expected == actual) null else "${c["name"]!!.jsonPrimitive.content}:\n  expected $expected\n  actual   $actual"
        }
        assertEquals("${bad.size} of ${cases.size} cases differ:\n${bad.joinToString("\n")}", 0, bad.size)
    }

    @Test
    fun covering() {
        val c = root["covering"]!!.jsonObject
        val d = day(c["day"]!!)
        val e = event(c["event"]!!)
        assertEquals(days(c["starts"]), PersonalEvents.occurrencesCovering(e, d, d).map { it.toString() })
        assertEquals(days(c["starts"]), PersonalEvents.eventsOnDay(listOf(e), d).map { it.start.toString() })
    }

    @Test
    fun reminders() {
        val r = root["reminders"]!!.jsonObject
        val events = r["events"]!!.jsonArray.map(::event)
        val from = LocalDateTime.parse(r["from"]!!.jsonPrimitive.content, minute)
        val to = LocalDateTime.parse(r["to"]!!.jsonPrimitive.content, minute)
        val expected = r["fires"]!!.jsonArray.map { f ->
            val a = f.jsonArray
            listOf(a[0].jsonPrimitive.content, a[1].jsonPrimitive.content, a[2].jsonPrimitive.int.toString(), a[3].jsonPrimitive.content)
        }
        val actual = PersonalEvents.remindersBetween(events, from, to).map {
            listOf(it.event.id, it.start.toString(), it.minutes.toString(), it.at.format(minute))
        }
        assertEquals(expected, actual)
    }
}
