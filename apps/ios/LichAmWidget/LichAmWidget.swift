import SwiftUI
import WidgetKit
import LunarCore

// The widget extension's frame (step 3 of the iOS plan): one small widget with today's lunar
// date, redrawn at midnight. Step 9 replaces it with Tờ lịch hôm nay and Lịch tháng.

struct DayEntry: TimelineEntry {
    let date: Date
    let info: DayInfo
}

private func entry(_ date: Date) -> DayEntry {
    let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
    return DayEntry(date: date, info: DayInfo.of(SolarDate(c.year!, c.month!, c.day!)))
}

struct DayProvider: TimelineProvider {
    func placeholder(in context: Context) -> DayEntry { entry(Date()) }

    func getSnapshot(in context: Context, completion: @escaping (DayEntry) -> Void) { completion(entry(Date())) }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DayEntry>) -> Void) {
        let now = Date()
        let midnight = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: now))!
        completion(Timeline(entries: [entry(now), entry(midnight)], policy: .after(midnight)))
    }
}

struct LunarDayView: View {
    let entry: DayEntry

    var body: some View {
        let i = entry.info
        VStack(spacing: 2) {
            Text("\(i.solar.day)/\(i.solar.month)").font(.system(size: 15, weight: .semibold))
            Text("\(i.lunar.day)").font(.system(size: 44, weight: .bold, design: .serif))
            Text("tháng \(i.lunar.month)\(i.lunar.leap ? " nhuận" : "") âm lịch").font(.system(size: 12))
        }
        .widgetURL(URL(string: "com.coding4pizza.lunar://open?tab=today"))
        .containerBackground(for: .widget) { Color(.systemBackground) }
    }
}

struct LunarDayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LunarDay", provider: DayProvider()) { LunarDayView(entry: $0) }
            .configurationDisplayName("Ngày âm hôm nay")
            .description("Ngày âm lịch của hôm nay.")
            .supportedFamilies([.systemSmall])
    }
}

@main
struct LichAmWidgets: WidgetBundle {
    var body: some Widget { LunarDayWidget() }
}
