import SwiftUI
import WidgetKit
import LunarCore

// The widgets (Android's widget/): Tờ lịch hôm nay and Lịch tháng, each small, medium and large,
// redrawn at midnight and whenever the app saves (WidgetCenter.reloadAllTimelines in AppState).
// They read the app's data from the App Group's defaults, with Android's keys.

/// What the widgets need from the app's store.
struct WidgetSettings {
    let personal: [LunarEvent]
    let userChi: String?
    let palette: MenhElement
    /// "SYSTEM", "LIGHT" or "DARK" (the app's Giao diện).
    let theme: String

    static func read() -> WidgetSettings {
        let d = UserDefaults(suiteName: "group.com.coding4pizza.lunar") ?? .standard
        let personal = d.string(forKey: "lunarEvents").flatMap { $0.data(using: .utf8) }
            .flatMap { try? JSONDecoder().decode([LunarEvent].self, from: $0) } ?? []
        let menh = d.string(forKey: "userBirthDate").flatMap(SolarDate.parse).flatMap(MenhCalc.ofBirthDate)
        let accent = d.string(forKey: "accent").flatMap(AccentPref.init(rawValue:)) ?? .banMenh
        return WidgetSettings(personal: personal, userChi: menh?.chi, palette: MenhCalc.paletteFor(menh, accent),
                              theme: d.string(forKey: "theme") ?? "SYSTEM")
    }
}

struct DayEntry: TimelineEntry {
    let date: Date
    let day: SolarDate
    let settings: WidgetSettings
}

private func solarDay(_ date: Date) -> SolarDate {
    let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
    return SolarDate(c.year!, c.month!, c.day!)
}

struct DayProvider: TimelineProvider {
    private func entry(_ date: Date) -> DayEntry { DayEntry(date: date, day: solarDay(date), settings: .read()) }

    func placeholder(in context: Context) -> DayEntry { entry(Date()) }

    func getSnapshot(in context: Context, completion: @escaping (DayEntry) -> Void) { completion(entry(Date())) }

    /// Today now, tomorrow from midnight; asked again after midnight.
    func getTimeline(in context: Context, completion: @escaping (Timeline<DayEntry>) -> Void) {
        let now = Date()
        let midnight = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: now))!
        completion(Timeline(entries: [entry(now), entry(midnight)], policy: .after(midnight)))
    }
}

/// The app's colours: its palette, light or dark as its Giao diện says (Theo máy = the system's).
private struct Themed<Content: View>: View {
    let settings: WidgetSettings
    @ViewBuilder let content: () -> Content
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let dark = settings.theme == "DARK" || (settings.theme != "LIGHT" && scheme == .dark)
        content().environment(\.colors, LichAmColors(settings.palette, dark: dark))
    }
}

private struct SheetEntryView: View {
    let entry: DayEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colors) private var c

    var body: some View {
        let name = DayInfo.orNil(entry.day).flatMap { sheetName($0, entry.settings.personal) }
        SheetWidgetView(day: entry.day, name: name, family: family)
            .widgetURL(widgetLink("today"))
            .containerBackground(for: .widget) { SheetPaper() }
    }
}

private struct MonthEntryView: View {
    let entry: DayEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colors) private var c

    var body: some View {
        MonthWidgetView(today: entry.day, family: family, userChi: entry.settings.userChi, personal: entry.settings.personal)
            // Small has no room for a link per day: it opens Lịch on today.
            .widgetURL(widgetLink("calendar", entry.day))
            .containerBackground(for: .widget) { c.surface }
    }
}

struct TodaySheetWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TodaySheet", provider: DayProvider()) { e in
            Themed(settings: e.settings) { SheetEntryView(entry: e) }
        }
        .configurationDisplayName("Tờ lịch hôm nay")
        .description("Ngày dương, ngày âm và tên ngày của hôm nay, như tờ lịch trong app.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

struct MonthWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "Month", provider: DayProvider()) { e in
            Themed(settings: e.settings) { MonthEntryView(entry: e) }
        }
        .configurationDisplayName("Lịch tháng")
        .description("Tháng này với ngày âm, ngày tốt xấu và sự kiện; chạm một ngày để mở ngày đó.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

@main
struct LichAmWidgets: WidgetBundle {
    var body: some Widget {
        TodaySheetWidget()
        MonthWidget()
    }
}
