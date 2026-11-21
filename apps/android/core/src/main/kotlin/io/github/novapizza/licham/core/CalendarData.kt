package io.github.novapizza.licham.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// Models of the JSON files in shared/data (packaged as Java resources by core/build.gradle.kts).

@Serializable
data class CalendarJson(
    val can: List<String>,
    val chi: List<String>,
    val chiEmoji: Map<String, String>,
    val weekDays: List<String>,
    val weekDaysShort: List<String>,
    val gioHoangDao: GioHoangDaoJson,
    val truc: TrucJson,
    /** Activity rules by key; also holds a "$comment" string, so it is read as raw JSON. */
    @SerialName("activities") private val activitiesRaw: JsonObject,
) {
    val activities: Map<String, ActivityJson> by lazy {
        activitiesRaw.filterKeys { !it.startsWith("$") }
            .mapValues { (_, v) -> Json.decodeFromJsonElement(ActivityJson.serializer(), v) }
    }
}

@Serializable
data class GioHoangDaoJson(val byDayChi: List<List<Int>>)

@Serializable
data class TrucJson(
    val names: List<String>,
    val monthChiStart: List<Int>,
    val good: List<String>,
    val bad: List<String>,
)

@Serializable
data class ActivityJson(val label: String, val truc: List<String>)

@Serializable
data class HolidaysJson(val lunar: Map<String, String>, val solar: Map<String, String>)

@Serializable
data class ElementJson(val name: String, val color: String, val generatedBy: String)

@Serializable
data class NapAmJson(val name: String, val element: String)

@Serializable
data class MenhJson(
    val cycleStartYear: Int,
    val elements: Map<String, ElementJson>,
    val napAm: List<NapAmJson>,
    val birthDateMin: String,
)

@Serializable
data class PresetEventJson(val id: String, val title: String, val lunarDay: Int, val lunarMonth: Int)

@Serializable
data class PresetJson(
    val id: String,
    val label: String,
    val description: String,
    val emoji: String,
    val events: List<PresetEventJson>,
)

@Serializable
data class PresetsJson(val presets: List<PresetJson>)

@Serializable
data class LunarYearsJson(val firstYear: Int, val years: List<Int>)

@Serializable
data class PaletteJson(val light: Map<String, String>, val dark: Map<String, String>)

@Serializable
data class BaseColorsJson(val light: Map<String, String>, val dark: Map<String, String>)

@Serializable
data class TokensJson(
    val base: BaseColorsJson,
    val palettes: Map<String, PaletteJson>,
    @SerialName("descriptions") val descriptions: Map<String, String> = emptyMap(),
)

/** The shared data, loaded once from the classpath. */
object CalendarData {
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> load(name: String): T {
        val stream = CalendarData::class.java.classLoader.getResourceAsStream(name)
            ?: error("$name is not on the classpath (core/build.gradle.kts packages shared/data and shared/design)")
        return json.decodeFromString(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    val calendar: CalendarJson by lazy { load("calendar.json") }
    val holidays: HolidaysJson by lazy { load("holidays.json") }
    val menh: MenhJson by lazy { load("menh.json") }
    val presets: PresetsJson by lazy { load("presets.json") }
    val lunarYears: LunarYearsJson by lazy { load("lunar-years.json") }
    val tokens: TokensJson by lazy { load("tokens.json") }
}
