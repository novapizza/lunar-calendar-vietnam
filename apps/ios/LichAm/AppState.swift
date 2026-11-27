import Foundation
import Observation
import LunarCore

enum Tab: String, CaseIterable { case today = "TODAY", calendar = "CALENDAR", events = "EVENTS", tools = "TOOLS" }
enum ThemePref: String, CaseIterable { case system = "SYSTEM", light = "LIGHT", dark = "DARK" }

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

    // MARK: personal events

    /// Adds [e] or replaces the event with its id.
    func saveEvent(_ e: LunarEvent) {
        if let i = lunarEvents.firstIndex(where: { $0.id == e.id }) { lunarEvents[i] = e } else { lunarEvents.append(e) }
        eventsChanged()
    }

    /// "Chỉ lần này" on an edit: the occurrence on [day] leaves the series and [copy] takes its place.
    func saveOccurrence(seriesId: String, day: SolarDate, copy: LunarEvent) {
        lunarEvents = lunarEvents.map { $0.id == seriesId ? withException($0, day) : $0 } + [copy]
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
    }

    func removeBuiltinReminder(_ ref: String) {
        builtinReminders.removeAll { $0.ref == ref }
        save("builtinReminders", Store.encode(builtinReminders))
    }

    private func eventsChanged() {
        save("lunarEvents", Store.encode(lunarEvents))
        // Reminders are scheduled from step 7 of the iOS plan.
    }

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

    private func save(_ key: String, _ value: String?) {
        if let value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) }
    }
}
