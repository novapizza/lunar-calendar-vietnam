package io.github.novapizza.licham

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.novapizza.licham.core.AccentPref
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.DayEvent
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.Element
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.PersonalEvents
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
enum class SheetKind { MONTH_PICKER, QUICK_DATE, AUSPICIOUS, LUNAR_EVENTS, PERSONAL_EVENTS, EVENT_EDITOR, EVENT_DETAIL, ABOUT, BIRTH_DATE }

/** What the event editor opens on: an event ([id], null = a new one) and the day it was opened from; [onlyThis] = that occurrence alone. */
data class EventEditTarget(val id: String?, val day: SolarDate, val onlyThis: Boolean = false)

/** The occurrence the event detail shows: the event and the occurrence's start day. */
data class EventRef(val id: String, val start: SolarDate)

/**
 * Everything the screens read and change. Persisted in SharedPreferences with the
 * same shapes as the web app's localStorage (notes, lunar events, presets, birth date,
 * accent and theme preferences).
 */
class AppState(context: Context) {
    private val app = context.applicationContext
    private val prefs = context.getSharedPreferences("licham", Context.MODE_PRIVATE)
    private val json = JSON

    private fun <T> read(key: String, decode: (String) -> T, default: T): T =
        prefs.getString(key, null)?.let { runCatching { decode(it) }.getOrNull() } ?: default

    var tab by mutableStateOf(Tab.TODAY)
    var sheet by mutableStateOf<SheetKind?>(null)
    /** Sheets under the one shown (the events list under an event's detail under its editor): closing one shows the one below. */
    private var sheetStack = emptyList<SheetKind>()
    var editTarget by mutableStateOf<EventEditTarget?>(null)
        private set
    var detailRef by mutableStateOf<EventRef?>(null)
        private set

    var selectedDate by mutableStateOf(today())
        private set
    /** First day of the month shown in the month calendar. */
    var shownMonth by mutableStateOf(today().let { SolarDate(it.year, it.month, 1) })
        private set

    var lunarEvents by mutableStateOf(readEvents(prefs))
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

    /** The user's own events covering [date]: all-day ones first, then by start time. */
    fun personalOn(date: SolarDate): List<DayEvent> = PersonalEvents.eventsOnDay(lunarEvents, date)
    fun eventById(id: String): LunarEvent? = lunarEvents.firstOrNull { it.id == id }

    // ---- sheets -------------------------------------------------------------

    /** Shows [kind] over the sheet open now, which comes back when [kind] closes. */
    fun pushSheet(kind: SheetKind) {
        sheet?.let { if (it != kind) sheetStack = sheetStack + it }
        sheet = kind
    }

    /** Closes the sheet shown: the one under it comes back. */
    fun closeSheet() {
        sheet = sheetStack.lastOrNull()
        sheetStack = sheetStack.dropLast(1)
    }

    fun closeAllSheets() {
        sheetStack = emptyList()
        sheet = null
    }

    /** The event editor: a new event on [day] ([id] null), the series [id], or its occurrence on [day] alone. */
    fun openEditor(id: String?, day: SolarDate, onlyThis: Boolean = false) {
        editTarget = EventEditTarget(id, day, onlyThis)
        pushSheet(SheetKind.EVENT_EDITOR)
    }

    fun openDetail(id: String, start: SolarDate) {
        detailRef = EventRef(id, start)
        pushSheet(SheetKind.EVENT_DETAIL)
    }

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

    // ---- personal events -------------------------------------------------

    /** Adds [e] or replaces the event with its id. */
    fun saveEvent(e: LunarEvent) {
        lunarEvents = if (lunarEvents.any { it.id == e.id }) lunarEvents.map { if (it.id == e.id) e else it } else lunarEvents + e
        // The detail open under the editor follows the edited series to an occurrence it still has.
        detailRef?.takeIf { it.id == e.id }?.let { ref ->
            val day = PersonalEvents.occurrencesCovering(e, ref.start, ref.start).firstOrNull()
                ?: PersonalEvents.nextOccurrence(e, ref.start) ?: PersonalEvents.startOf(e) ?: ref.start
            detailRef = EventRef(e.id, day)
        }
        eventsChanged()
    }

    /** "Chỉ lần này" on an edit: the occurrence on [day] leaves the series [seriesId] and [copy] (repeat none) takes its place. */
    fun saveOccurrence(seriesId: String, day: SolarDate, copy: LunarEvent) {
        lunarEvents = lunarEvents.map { if (it.id == seriesId) withException(it, day) else it } + copy
        detailRef = EventRef(copy.id, PersonalEvents.startOf(copy) ?: day)
        eventsChanged()
    }

    fun deleteEvent(id: String) {
        lunarEvents = lunarEvents.filterNot { it.id == id }
        eventsChanged()
    }

    /** "Chỉ lần này" on a delete: [day] joins the series' exceptions. */
    fun deleteOccurrence(id: String, day: SolarDate) {
        lunarEvents = lunarEvents.map { if (it.id == id) withException(it, day) else it }
        eventsChanged()
    }

    private fun withException(e: LunarEvent, day: SolarDate) =
        e.copy(exceptions = (e.exceptions + day.toString()).distinct().sorted())

    /** Saves the list and sets the next reminder alarm. */
    private fun eventsChanged() {
        save("lunarEvents", json.encodeToString(ListSerializer(LunarEvent.serializer()), lunarEvents))
        Reminders.reschedule(app)
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
        /** The web may write null for an absent value: it reads as the default (coerceInputValues). */
        val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun prefs(context: Context) = context.getSharedPreferences("licham", Context.MODE_PRIVATE)

        private fun readEvents(prefs: android.content.SharedPreferences): List<LunarEvent> {
            val events = prefs.getString("lunarEvents", null)?.let { runCatching { JSON.decodeFromString(ListSerializer(LunarEvent.serializer()), it) }.getOrNull() } ?: emptyList()
            // The old day notes ("Ghi chú" card, removed 2026-11-23) move into events once. The
            // events are written (commit) before the old key goes, so a failure keeps the notes.
            val oldRaw = prefs.getString("userNotes", null) ?: return events
            val notes = runCatching { JSON.decodeFromString(ListSerializer(UserNote.serializer()), oldRaw) }.getOrNull() ?: return events
            val have = events.map { it.id }.toSet()
            val all = events + notes.filter { it.id !in have && it.content.isNotBlank() && SolarDate.parse(it.date) != null }.map(PersonalEvents::noteToEvent)
            val saved = prefs.edit().putString("lunarEvents", JSON.encodeToString(ListSerializer(LunarEvent.serializer()), all)).commit()
            if (saved) prefs.edit().remove("userNotes").commit()
            return all
        }

        /** The stored personal events, for the reminder receiver and the widgets. */
        fun readEvents(context: Context): List<LunarEvent> = readEvents(prefs(context))

        fun today(): SolarDate = LocalDate.now().toSolar()
        /** Names from shared/data so labels stay identical across apps. */
        val chiEmoji: Map<String, String> get() = CalendarData.calendar.chiEmoji
    }
}
