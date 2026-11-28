import Foundation
import Observation
import WidgetKit
import LunarCore

enum Tab: String, CaseIterable { case today = "TODAY", calendar = "CALENDAR", events = "EVENTS", tools = "TOOLS" }
enum ThemePref: String, CaseIterable { case system = "SYSTEM", light = "LIGHT", dark = "DARK" }

/// Popups ("sheets"), as Android's SheetKind (the month picker is the Calendar's own sheet).
enum SheetKind: String, Identifiable {
    case quickDate, auspicious, lunarEvents, personalEvents, eventEditor, eventDetail, about, birthDate
    var id: String { rawValue }
}

/// What the event editor opens on: an event ([id], nil = a new one) and the day it was opened from;
/// [onlyThis] = that occurrence alone; [builtin] = the reminder of a built-in day (its own form).
struct EventEditTarget: Hashable {
    let id: String?
    let day: SolarDate
    var onlyThis = false
    var builtin: BuiltinDay? = nil
}

/// A built-in day (holiday, observance, mồng 1, rằm) whose reminder the editor sets.
struct BuiltinDay: Hashable {
    let ref: String
    let title: String
    let calendar: String
}

/// The occurrence the event detail shows: the event and the occurrence's start day.
struct EventRef: Hashable {
    let id: String
    let start: SolarDate
}

/// Where the app keeps its data: the App Group's defaults, so the widgets read the same events.
enum Store {
    static let appGroup = "group.com.coding4pizza.lunar"
    static var defaults: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    /// Personal events: the same keys and JSON as Android's SharedPreferences and the web's
    /// localStorage (shared/design/events.md), so the stored shape stays one.
    static func readEvents(_ d: UserDefaults = defaults) -> [LunarEvent] {
        let events = decode([LunarEvent].self, d.string(forKey: "lunarEvents")) ?? []
        // The old day notes ("Ghi chú", removed 2026-11-23) move into events once, as on Android.
        guard let notes = decode([UserNote].self, d.string(forKey: "userNotes")) else { return events }
        let have = Set(events.map(\.id))
        let all = events + notes
            .filter { !have.contains($0.id) && !$0.content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && SolarDate.parse($0.date) != nil }
            .map(PersonalEvents.noteToEvent)
        if let json = encode(all) {
            d.set(json, forKey: "lunarEvents")
            d.removeObject(forKey: "userNotes")
        }
        return all
    }

    /// Reminders set on built-in days: never listed, only reminded.
    static func readBuiltin(_ d: UserDefaults = defaults) -> [LunarEvent] {
        (decode([LunarEvent].self, d.string(forKey: "builtinReminders")) ?? []).filter { $0.ref != nil }
    }

    static let keys = ["lunarEvents", "builtinReminders", "lunarPresets", "userBirthDate", "accent", "theme", "userNotes"]

    /// Removes everything the app stored (UI tests only).
    static func reset(_ d: UserDefaults = defaults) { keys.forEach(d.removeObject(forKey:)) }

    static func decode<T: Decodable>(_ type: T.Type, _ json: String?) -> T? {
        guard let data = json?.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    static func encode<T: Encodable>(_ value: T) -> String? {
        let e = JSONEncoder()
        e.outputFormatting = [.withoutEscapingSlashes]
        return (try? e.encode(value)).flatMap { String(data: $0, encoding: .utf8) }
    }
}

/// Today in the device's time zone.
func today(_ now: Date = Date()) -> SolarDate {
    let c = Calendar.current.dateComponents([.year, .month, .day], from: now)
    return SolarDate(c.year!, c.month!, c.day!)
}

/// Everything the screens read and change (Android's AppState), persisted in the App Group's
/// defaults with the same keys and values as Android (lunarEvents, builtinReminders, lunarPresets,
/// userBirthDate, accent, theme).
@Observable @MainActor
final class AppState {
    @ObservationIgnored private let defaults: UserDefaults

    var tab: Tab = .today
    /// The sheet shown; the ones under it come back when it closes (Android's sheet stack).
    private(set) var sheet: SheetKind?
    @ObservationIgnored private var sheetStack: [SheetKind] = []
    private(set) var editTarget: EventEditTarget?
    private(set) var detailRef: EventRef?
    private(set) var selectedDate: SolarDate
    /// First day of the month shown in the month calendar.
    private(set) var shownMonth: SolarDate

    private(set) var lunarEvents: [LunarEvent]
    private(set) var builtinReminders: [LunarEvent]
    private(set) var enabledPresets: Set<String>
    private(set) var birthDate: SolarDate?
    private(set) var accentPref: AccentPref
    private(set) var themePref: ThemePref

    init(defaults: UserDefaults = Store.defaults) {
        self.defaults = defaults
        let t = today()
        selectedDate = t
        shownMonth = SolarDate(t.year, t.month, 1)
        lunarEvents = Store.readEvents(defaults)
        builtinReminders = Store.readBuiltin(defaults)
        enabledPresets = Set(Store.decode([String].self, defaults.string(forKey: "lunarPresets")) ?? [])
        birthDate = defaults.string(forKey: "userBirthDate").flatMap(SolarDate.parse)
        accentPref = defaults.string(forKey: "accent").flatMap(AccentPref.init(rawValue:)) ?? .banMenh
        themePref = defaults.string(forKey: "theme").flatMap(ThemePref.init(rawValue:)) ?? .system
    }

    var menh: Menh? { birthDate.flatMap(MenhCalc.ofBirthDate) }
    var userChi: String? { menh?.chi }
    var palette: MenhElement { MenhCalc.paletteFor(menh, accentPref) }
    var selectedInfo: DayInfo { DayInfo.of(selectedDate) }
    var exportEvents: [LunarEvent] { presetEvents(enabledPresets) + lunarEvents }

    /// The user's own events covering [date]: all-day ones first, then by start time.
    func personalOn(_ date: SolarDate) -> [DayEvent] { PersonalEvents.eventsOnDay(lunarEvents, date) }
    func eventById(_ id: String) -> LunarEvent? { lunarEvents.first { $0.id == id } }

    // MARK: navigation

    func selectTab(_ t: Tab) {
        tab = t
        // Switching to "Hôm nay" always resets to today (web behaviour).
        if t == .today { selectDate(today()) }
    }

    /// Selects a day and shows its month. Out-of-range days are ignored.
    func selectDate(_ d: SolarDate) {
        guard d.isInRange else { return }
        selectedDate = d
        shownMonth = SolarDate(d.year, d.month, 1)
    }

    /// Shows a month and moves the selected day into it: same day number, or the 1st.
    func showMonth(_ year: Int, _ month: Int) {
        let day = selectedDate.day <= daysInMonth(year, month) ? selectedDate.day : 1
        let next = SolarDate(year, month, day)
        guard next.isInRange else { return }
        selectedDate = next
        shownMonth = SolarDate(year, month, 1)
    }

    /// A link com.coding4pizza.lunar://open?tab=calendar&d=YYYY-MM-DD (widgets, notifications).
    func open(_ url: URL) {
        guard url.scheme == "com.coding4pizza.lunar", url.host == "open" else { return }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let tab = items.first { $0.name == "tab" }?.value.flatMap { Tab(rawValue: $0.uppercased()) } ?? .today
        selectTab(tab)
        if let d = items.first(where: { $0.name == "d" })?.value.flatMap(SolarDate.parse) { selectDate(d) }
    }

    // MARK: sheets

    /// Shows [kind] over the sheet open now, which comes back when [kind] closes.
    func pushSheet(_ kind: SheetKind) {
        if let s = sheet, s != kind { sheetStack.append(s) }
        sheet = kind
    }

    /// Closes the sheet shown: the one under it comes back.
    func closeSheet() { sheet = sheetStack.popLast() }

    func closeAllSheets() {
        sheetStack = []
        sheet = nil
    }

    /// The event editor: a new event on [day] ([id] nil), the series [id], or its occurrence on [day] alone.
    func openEditor(_ id: String?, _ day: SolarDate, onlyThis: Bool = false) {
        editTarget = EventEditTarget(id: id, day: day, onlyThis: onlyThis)
        pushSheet(.eventEditor)
    }

    /// The reminder form of the built-in day [e] (Sự kiện tab), on that occurrence.
    func openBuiltinReminder(_ e: UpcomingEvent) {
        guard let ref = e.ref else { return }
        editTarget = EventEditTarget(id: BuiltinReminders.of(builtinReminders, ref)?.id, day: e.solarDate,
                                     builtin: BuiltinDay(ref: ref, title: e.title, calendar: e.lunar ? LunarEvent.lunar : LunarEvent.solar))
        pushSheet(.eventEditor)
    }

    func openDetail(_ id: String, _ start: SolarDate) {
        detailRef = EventRef(id: id, start: start)
        pushSheet(.eventDetail)
    }

    // MARK: personal events

    /// Adds [e] or replaces the event with its id.
    func saveEvent(_ e: LunarEvent) {
        if let i = lunarEvents.firstIndex(where: { $0.id == e.id }) { lunarEvents[i] = e } else { lunarEvents.append(e) }
        // The detail open under the editor follows the edited series to an occurrence it still has.
        if let ref = detailRef, ref.id == e.id {
            let day = PersonalEvents.occurrencesCovering(e, ref.start, ref.start).first
                ?? PersonalEvents.nextOccurrence(e, ref.start) ?? PersonalEvents.startOf(e) ?? ref.start
            detailRef = EventRef(id: e.id, start: day)
        }
        eventsChanged()
    }

    /// "Chỉ lần này" on an edit: the occurrence on [day] leaves the series and [copy] takes its place.
    func saveOccurrence(seriesId: String, day: SolarDate, copy: LunarEvent) {
        lunarEvents = lunarEvents.map { $0.id == seriesId ? withException($0, day) : $0 } + [copy]
        detailRef = EventRef(id: copy.id, start: PersonalEvents.startOf(copy) ?? day)
        eventsChanged()
    }

    func deleteEvent(_ id: String) {
        lunarEvents.removeAll { $0.id == id }
        eventsChanged()
    }

    /// "Chỉ lần này" on a delete: [day] joins the series' exceptions.
    func deleteOccurrence(_ id: String, _ day: SolarDate) {
        lunarEvents = lunarEvents.map { $0.id == id ? withException($0, day) : $0 }
        eventsChanged()
    }

    private func withException(_ e: LunarEvent, _ day: SolarDate) -> LunarEvent {
        var e = e
        e.exceptions = Array(Set(e.exceptions + [day.iso])).sorted()
        return e
    }

    /// Saves the reminder of [r]'s ref, replacing the one kept for it.
    func saveBuiltinReminder(_ r: LunarEvent) {
        builtinReminders = builtinReminders.filter { $0.ref != r.ref } + [r]
        save("builtinReminders", Store.encode(builtinReminders))
        scheduleReminders()
    }

    func removeBuiltinReminder(_ ref: String) {
        builtinReminders.removeAll { $0.ref == ref }
        save("builtinReminders", Store.encode(builtinReminders))
        scheduleReminders()
    }

    private func eventsChanged() {
        save("lunarEvents", Store.encode(lunarEvents))
        scheduleReminders()
    }

    /// Registers the next reminders of everything that reminds: the personal events and the built-in
    /// days' reminders (Android's readRemindable).
    func scheduleReminders() { Reminders.schedule(lunarEvents + builtinReminders) }

    func togglePreset(_ id: String) {
        if enabledPresets.contains(id) { enabledPresets.remove(id) } else { enabledPresets.insert(id) }
        save("lunarPresets", Store.encode(enabledPresets.sorted()))
    }

    // MARK: settings

    func updateBirthDate(_ d: SolarDate?) {
        birthDate = d
        save("userBirthDate", d?.iso)
    }

    func updateAccent(_ p: AccentPref) {
        accentPref = p
        save("accent", p.rawValue)
    }

    func updateTheme(_ p: ThemePref) {
        themePref = p
        save("theme", p.rawValue)
    }

    /// The binding's theme button: Theo máy → Sáng → Tối → Theo máy.
    func cycleTheme() {
        let all = ThemePref.allCases
        updateTheme(all[(all.firstIndex(of: themePref)! + 1) % all.count])
    }

    /// Stores [value] under [key] and redraws the widgets, which read the events, birth date, accent
    /// and theme from the same defaults.
    private func save(_ key: String, _ value: String?) {
        if let value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) }
        WidgetCenter.shared.reloadAllTimelines()
    }
}
