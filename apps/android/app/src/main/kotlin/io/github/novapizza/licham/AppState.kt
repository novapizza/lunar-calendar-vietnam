package io.github.novapizza.licham

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.novapizza.licham.core.AccentPref
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.Element
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.Menh
import io.github.novapizza.licham.core.MenhCalc
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.UserNote
import io.github.novapizza.licham.core.daysInMonth
import io.github.novapizza.licham.core.isInRange
import io.github.novapizza.licham.core.orNull
import io.github.novapizza.licham.core.presetEvents
import io.github.novapizza.licham.core.toSolar
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

enum class Tab { TODAY, CALENDAR, EVENTS, TOOLS }
enum class ThemePref { SYSTEM, LIGHT, DARK }

/** Popups ("sheets"), as in the web app. */
enum class SheetKind { MONTH_PICKER, QUICK_DATE, AUSPICIOUS, LUNAR_EVENTS, ABOUT, BIRTH_DATE }

/**
 * Everything the screens read and change. Persisted in SharedPreferences with the
 * same shapes as the web app's localStorage (notes, lunar events, presets, birth date,
 * accent and theme preferences).
 */
class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("licham", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> read(key: String, decode: (String) -> T, default: T): T =
        prefs.getString(key, null)?.let { runCatching { decode(it) }.getOrNull() } ?: default

    var tab by mutableStateOf(Tab.TODAY)
    var sheet by mutableStateOf<SheetKind?>(null)

    var selectedDate by mutableStateOf(today())
        private set
    /** First day of the month shown in the month calendar. */
    var shownMonth by mutableStateOf(today().let { SolarDate(it.year, it.month, 1) })
        private set

    var notes by mutableStateOf(read("userNotes", { json.decodeFromString(ListSerializer(UserNote.serializer()), it) }, emptyList()))
        private set
    var lunarEvents by mutableStateOf(read("lunarEvents", { json.decodeFromString(ListSerializer(LunarEvent.serializer()), it) }, emptyList()))
        private set
    var enabledPresets by mutableStateOf(read("lunarPresets", { json.decodeFromString(SetSerializer(String.serializer()), it) }, emptySet()))
        private set
    var birthDate by mutableStateOf(prefs.getString("userBirthDate", null)?.let { SolarDate.parse(it) })
        private set
    var accentPref by mutableStateOf(read("accent", { AccentPref.valueOf(it) }, AccentPref.BAN_MENH))
        private set
    var themePref by mutableStateOf(read("theme", { ThemePref.valueOf(it) }, ThemePref.SYSTEM))
        private set

    val menh: Menh? get() = birthDate?.let { MenhCalc.ofBirthDate(it) }
    val userChi: String? get() = menh?.chi
    val palette: Element get() = MenhCalc.paletteFor(menh, accentPref)
    val selectedInfo: DayInfo get() = DayInfo.of(selectedDate)
    val exportEvents: List<LunarEvent> get() = presetEvents(enabledPresets) + lunarEvents

    fun notesOn(date: SolarDate): List<UserNote> = notes.filter { it.date == date.toString() }

    // ---- navigation -------------------------------------------------------

    fun selectTab(t: Tab) {
        tab = t
        // Switching to "Hôm nay" always resets to today (web behaviour).
        if (t == Tab.TODAY) selectDate(today())
    }

    /** Selects a day and shows its month. Out-of-range days are ignored. */
    fun selectDate(d: SolarDate) {
        if (!d.isInRange()) return
        selectedDate = d
        shownMonth = SolarDate(d.year, d.month, 1)
    }

    /** Shows a month and moves the selected day into it: same day number, or the 1st. */
    fun showMonth(year: Int, month: Int) {
        val day = selectedDate.day.takeIf { it <= daysInMonth(year, month) } ?: 1
        val next = SolarDate(year, month, day)
        if (!next.isInRange()) return
        selectedDate = next
        shownMonth = SolarDate(year, month, 1)
    }

    // ---- notes ------------------------------------------------------------

    fun addNote(date: SolarDate, content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        notes = notes + UserNote(UUID.randomUUID().toString(), date.toString(), text, System.currentTimeMillis())
        save("userNotes", json.encodeToString(ListSerializer(UserNote.serializer()), notes))
    }

    fun deleteNote(id: String) {
        notes = notes.filterNot { it.id == id }
        save("userNotes", json.encodeToString(ListSerializer(UserNote.serializer()), notes))
    }

    // ---- lunar events -----------------------------------------------------

    fun saveLunarEvent(id: String?, title: String, day: Int, month: Int, leap: Boolean) {
        lunarEvents = if (id == null) {
            lunarEvents + LunarEvent(UUID.randomUUID().toString(), title, day, month, leap, System.currentTimeMillis())
        } else {
            lunarEvents.map { if (it.id == id) it.copy(title = title, lunarDay = day, lunarMonth = month, isLeapMonth = leap) else it }
        }
        save("lunarEvents", json.encodeToString(ListSerializer(LunarEvent.serializer()), lunarEvents))
    }

    fun deleteLunarEvent(id: String) {
        lunarEvents = lunarEvents.filterNot { it.id == id }
        save("lunarEvents", json.encodeToString(ListSerializer(LunarEvent.serializer()), lunarEvents))
    }

    fun togglePreset(id: String) {
        enabledPresets = if (id in enabledPresets) enabledPresets - id else enabledPresets + id
        save("lunarPresets", json.encodeToString(SetSerializer(String.serializer()), enabledPresets))
    }

    // ---- settings ---------------------------------------------------------

    fun updateBirthDate(d: SolarDate?) {
        birthDate = d
        save("userBirthDate", d?.toString())
    }

    fun updateAccent(p: AccentPref) {
        accentPref = p
        save("accent", p.name)
    }

    fun updateTheme(p: ThemePref) {
        themePref = p
        save("theme", p.name)
    }

    fun cycleTheme() = updateTheme(ThemePref.entries[(themePref.ordinal + 1) % ThemePref.entries.size])

    private fun save(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    companion object {
        fun today(): SolarDate = LocalDate.now().toSolar()
        /** Names from shared/data so labels stay identical across apps. */
        val chiEmoji: Map<String, String> get() = CalendarData.calendar.chiEmoji
    }
}
