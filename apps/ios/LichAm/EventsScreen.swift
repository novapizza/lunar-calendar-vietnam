import SwiftUI
import LunarCore

private func countdown(_ days: Int) -> String {
    switch days {
    case 0: "Hôm nay"
    case 1: "Ngày mai"
    case -1: "Hôm qua"
    case ..<0: "\(-days) ngày trước"
    default: "\(days) ngày nữa"
    }
}

/// Months loaded at first: this month and the next three.
private let minMonths = 4

/// Sự kiện (Android's EventsScreen, the web's views/EventsView.tsx): the next event highlighted, then
/// the following ones grouped by solar month, without end: one more month loads whenever the end of
/// the list comes into view, up to the end of the lunar tables (2099). The user's own events come
/// first on their day; a personal event opens its detail, a built-in day its reminder form.
struct EventsScreen: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var count = minMonths

    var body: some View {
        let t = today()
        let personal = state.lunarEvents
        let events = (0..<count).flatMap { Upcoming.month(t, $0, personal: personal) }
        let ended = Upcoming.monthStart(t, count) > LunarCalendar.lastDay
        // This month's past days are listed too (dimmed); the highlight is the next event from today.
        let nextIndex = events.firstIndex { $0.daysUntil >= 0 }
        let groups = Self.groups(events.enumerated().filter { $0.offset != nextIndex }.map(\.element))
        GeometryReader { geo in
            let wide = geo.size.width >= 720
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    title(wide: wide, personalCount: personal.count)
                    Group {
                        if let nextIndex { NextEvent(e: events[nextIndex], open: open) } else { EmptyEvents() }
                    }
                    .padding(.top, 16)
                    ForEach(groups, id: \.month) { g in
                        MonthGroup(month: g.month, items: g.items, open: open, reminds: reminds).padding(.top, 24)
                    }
                    if ended {
                        Text("Lịch tính đến hết năm 2099.").font(AppFont.sans(12)).foregroundStyle(c.fg3)
                            .frame(maxWidth: .infinity).padding(.top, 16)
                    } else {
                        // The end of the list in view: load one more month (a new id each time, so
                        // onAppear runs again while the end stays in view).
                        Color.clear.frame(height: 1).id(count).onAppear { count += 1 }
                    }
                }
                .frame(maxWidth: wide ? 720 : .infinity)
                .frame(maxWidth: .infinity)
                .padding(EdgeInsets(top: wide ? 32 : 16, leading: 16, bottom: 40, trailing: 16))
            }
        }
    }

    /// Events grouped by "Tháng 11, 2026", in order.
    private static func groups(_ events: [UpcomingEvent]) -> [(month: String, items: [UpcomingEvent])] {
        var out: [(month: String, items: [UpcomingEvent])] = []
        for e in events {
            let key = "Tháng \(e.solarDate.month), \(String(e.solarDate.year))"
            if out.last?.month == key { out[out.count - 1].items.append(e) } else { out.append((key, [e])) }
        }
        return out
    }

    private func title(wide: Bool, personalCount: Int) -> some View {
        HStack(alignment: .bottom, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                SectionLabel(text: "Sắp diễn ra")
                Text("Ngày lễ & sự kiện").font(AppFont.sans(wide ? 30 : 24, .heavy)).tracking(wide ? -0.3 : -0.24)
                    .foregroundStyle(c.fg).lineLimit(1).accessibilityAddTraits(.isHeader)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            SoftButton(text: personalCount == 0 ? "Thêm sự kiện" : "Của bạn (\(personalCount))",
                       icon: personalCount == 0 ? "plus" : "note.text") { state.pushSheet(.personalEvents) }
        }
    }

    private func open(_ e: UpcomingEvent) {
        if let id = e.eventId { state.openDetail(id, e.solarDate) } else if e.ref != nil { state.openBuiltinReminder(e) }
    }

    /// A bell on any event with a reminder (user, 2026-11-24).
    private func reminds(_ e: UpcomingEvent) -> Bool {
        if let id = e.eventId { return !(state.eventById(id)?.reminders.isEmpty ?? true) }
        return BuiltinReminders.reminds(state.builtinReminders, e.ref, e.solarDate)
    }
}

private struct EmptyEvents: View {
    @Environment(\.colors) private var c

    var body: some View {
        Card(padding: 40) {
            VStack(spacing: 12) {
                Image(systemName: "calendar.badge.exclamationmark").font(.system(size: 36)).foregroundStyle(c.fg3)
                Text("Không có sự kiện sắp tới").font(AppFont.sans(16, .semibold)).foregroundStyle(c.fg2)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

/// The next event on the hero gradient: title, its day (and lunar day, time), days to go.
private struct NextEvent: View {
    let e: UpcomingEvent
    let open: (UpcomingEvent) -> Void
    @Environment(\.colors) private var c

    var body: some View {
        let tappable = e.eventId != nil || e.ref != nil
        let line = e.solarDate.longLabel
            + (e.lunar ? " · \(e.lunarDay ?? 0)/\(e.lunarMonth ?? 0) Âm" : "")
            + (e.time.map { " · \($0)" } ?? "")
        Button { open(e) } label: {
            VStack(alignment: .leading, spacing: 8) {
                Text("SỰ KIỆN TIẾP THEO").font(AppFont.sans(11, .semibold)).tracking(1.3).foregroundStyle(.white.opacity(0.65))
                HStack(alignment: .bottom, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(e.title).font(AppFont.sans(20, .heavy)).tracking(-0.2).foregroundStyle(.white)
                            .multilineTextAlignment(.leading)
                        Text(line).font(AppFont.sans(14)).foregroundStyle(.white.opacity(0.8))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    VStack(alignment: .trailing, spacing: 4) {
                        Text(e.daysUntil == 0 ? "🎉" : "\(e.daysUntil)").font(AppFont.sans(36, .heavy)).foregroundStyle(c.heroAccent)
                            .frame(height: 36)
                        Text(e.daysUntil == 0 ? "Hôm nay" : "ngày nữa").font(AppFont.sans(12, .semibold)).foregroundStyle(.white.opacity(0.75))
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                LinearGradient(colors: [c.heroFrom, c.heroVia, c.heroTo], startPoint: .topLeading, endPoint: .bottomTrailing)
                    .overlay(alignment: .topTrailing) {
                        Circle().fill(c.heroAccent.opacity(0.08)).frame(width: 176, height: 176).offset(x: 48, y: -48)
                    }
            }
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .webShadow(c.shadowTint, float: true)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!tappable)
        .accessibilityIdentifier("next-event")
    }
}

/// One solar month: a card of rows (date tile, title, lunar date or "Dương lịch", kind, bell, countdown).
private struct MonthGroup: View {
    let month: String
    let items: [UpcomingEvent]
    let open: (UpcomingEvent) -> Void
    let reminds: (UpcomingEvent) -> Bool
    @Environment(\.colors) private var c

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionLabel(text: month).padding(.leading, 4).padding(.bottom, 8)
            Card(padding: 0) {
                ForEach(Array(items.enumerated()), id: \.offset) { i, e in
                    if i > 0 { Hairline() }
                    Button { open(e) } label: { row(e) }
                        .buttonStyle(.plain)
                        .disabled(e.eventId == nil && e.ref == nil)
                }
            }
        }
    }

    private func row(_ e: UpcomingEvent) -> some View {
        let personal = e.kind == .personal
        let fg = personal ? c.event(e.color) : e.lunar ? c.lunar : c.brand
        let bg = personal ? c.eventSoft(e.color) : e.lunar ? c.lunarSoft : c.brandSoft
        let kind: String? = switch e.kind {
        case .observance, .observanceLunar: "Kỷ niệm"
        case .festival: "Lễ hội"
        case .personal: "Cá nhân"
        case .holiday: nil
        }
        let soon = (0...7).contains(e.daysUntil)
        return HStack(spacing: 12) {
            VStack(spacing: 2) {
                Text("\(e.solarDate.day)").font(AppFont.sans(18, .heavy)).foregroundStyle(fg).frame(height: 18)
                Text(PersonalEvents.weekdayShort[e.solarDate.weekdayIndex]).font(AppFont.sans(10, .semibold)).foregroundStyle(fg)
            }
            .frame(width: 48, height: 48)
            .background(RoundedRectangle(cornerRadius: 16).fill(bg))
            VStack(alignment: .leading, spacing: 2) {
                Text(e.title).font(AppFont.sans(14, .bold)).foregroundStyle(c.fg).multilineTextAlignment(.leading)
                HStack(spacing: 4) {
                    Image(systemName: e.lunar ? "moon.stars.fill" : "sun.max.fill").font(.system(size: 10)).foregroundStyle(c.gold)
                    Text((e.lunar ? "\(e.lunarDay ?? 0)/\(e.lunarMonth ?? 0) âm lịch" : "Dương lịch") + (e.time.map { " · \($0)" } ?? ""))
                        .font(AppFont.sans(12)).foregroundStyle(c.fg2)
                    if let kind { Text("· \(kind)").font(AppFont.sans(12)).foregroundStyle(c.fg3) }
                    if reminds(e) {
                        Image(systemName: "bell.fill").font(.system(size: 10)).foregroundStyle(c.gold).accessibilityLabel("Có nhắc")
                    }
                }
                .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Badge(text: countdown(e.daysUntil), fg: soon ? c.gold : e.daysUntil < 0 ? c.fg3 : c.fg2, bg: soon ? c.goldSoft : c.surface2)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .opacity(e.daysUntil < 0 ? 0.55 : 1)
        .contentShape(Rectangle())
    }
}
