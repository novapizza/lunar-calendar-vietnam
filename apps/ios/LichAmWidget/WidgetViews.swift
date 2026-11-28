import SwiftUI
import UIKit
import WidgetKit
import LunarCore

// The widgets' views (Android's widget/TodaySheetWidget.kt and MonthWidget.kt). Built into the widget
// extension, and into the app too, where the debug gallery (-ui-widgets) draws them for screenshots.

private let monthNames = ["Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười Một", "Chạp"]
private let weekdays = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"]

/// A link that opens the app on [tab] (and for Lịch, on [day]).
func widgetLink(_ tab: String, _ day: SolarDate? = nil) -> URL {
    URL(string: "com.coding4pizza.lunar://open?tab=\(tab)" + (day.map { "&d=\($0.iso)" } ?? ""))!
}

/// The sheet's name line: holiday, else the user's own event, else a commemorative day / festival,
/// else "Mồng 1" / "Ngày rằm" (Android's nameOf).
func sheetName(_ info: DayInfo, _ personal: [LunarEvent]) -> String? {
    info.holiday ?? PersonalEvents.on(personal, info.solar).first?.title ?? info.observance ?? lunarDayLabel(info.lunar)
}

private func blend(_ a: Color, _ b: Color, _ t: Double) -> Color {
    var (r1, g1, b1, a1, r2, g2, b2, a2): (CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0, 0, 0, 0, 0)
    UIColor(a).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
    UIColor(b).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
    let f = CGFloat(t)
    return Color(.sRGB, red: r1 + (r2 - r1) * f, green: g1 + (g2 - g1) * f, blue: b1 + (b2 - b1) * f, opacity: 1)
}

private extension View {
    /// The sheet's gold foil (TearOffSheet's foil): light to deep accent, a small drop.
    func goldFoil(_ accent: Color) -> some View {
        foregroundStyle(LinearGradient(stops: [
            .init(color: blend(.white, accent, 0.4), location: 0),
            .init(color: accent, location: 0.48),
            .init(color: blend(Color(hex: "#7A4A08"), accent, 0.68), location: 1),
        ], startPoint: .top, endPoint: .bottom))
        .shadow(color: .black.opacity(0.22), radius: 0, x: 0, y: 1.5)
    }
}

// MARK: - Tờ lịch hôm nay

/// The tear-off sheet's lacquer: hero gradient, a soft light, a faint moon.
struct SheetPaper: View {
    @Environment(\.colors) private var c

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .topLeading) {
                LinearGradient(colors: [c.heroFrom, c.heroVia, c.heroTo], startPoint: .topLeading, endPoint: .bottomTrailing)
                RadialGradient(stops: [.init(color: .white.opacity(0.10), location: 0), .init(color: .clear, location: 0.6)],
                               center: UnitPoint(x: 0.5, y: 0.25), startRadius: 0, endRadius: max(g.size.width, g.size.height) * 0.8)
                Circle().fill(c.heroAccent.opacity(0.06)).frame(width: g.size.width * 0.7).offset(x: g.size.width * 0.55, y: -g.size.width * 0.2)
            }
        }
    }
}

/// Tờ lịch hôm nay: small = the day, its weekday and lunar date; medium = the solar day left, the
/// lunar date in its ring right (Android's landscape layout); large = the app's sheet (number,
/// weekday, name, the ring and the can chi).
struct SheetWidgetView: View {
    let day: SolarDate
    let name: String?
    let family: WidgetFamily
    @Environment(\.colors) private var c

    var body: some View {
        let info = DayInfo.of(day)
        Group {
            switch family {
            case .systemMedium: medium(info)
            case .systemLarge, .systemExtraLarge: large(info)
            default: small(info)
            }
        }
        // The double gold hairline frame of printed calendar sheets.
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(c.heroAccent.opacity(0.25), lineWidth: 1).padding(9))
        .overlay(RoundedRectangle(cornerRadius: 15).stroke(c.heroAccent.opacity(0.45), lineWidth: 1).padding(6))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(weekdays[day.weekdayIndex]), \(day.day)/\(day.month)/\(String(day.year)), âm lịch \(info.lunar.day)/\(info.lunar.month)" + (name.map { ", \($0)" } ?? ""))
    }

    private func monthLine(_ size: CGFloat) -> some View {
        Text("THÁNG \(day.month) · TUẦN \(day.weekOfYear)")
            .font(.custom("BeVietnamPro-SemiBold", fixedSize: size)).tracking(size * 0.3)
            .foregroundStyle(c.heroAccent).lineLimit(1).minimumScaleFactor(0.7)
    }

    private func number(_ size: CGFloat) -> some View {
        Text(verbatim: "\(day.day)").font(AppFont.serifFigures(size)).tracking(-0.02 * size)
            .goldFoil(c.heroAccent).lineLimit(1).frame(height: size * 1.02)
    }

    /// Sundays and holidays (red on a paper calendar) get the gold seal.
    private func weekday(_ info: DayInfo, _ size: CGFloat) -> some View {
        let red = day.isSunday || info.holiday != nil
        return Text(weekdays[day.weekdayIndex].uppercased())
            .font(.custom("BeVietnamPro-SemiBold", fixedSize: size)).tracking(size * 0.3)
            .foregroundStyle(red ? c.heroAccentInk : .white).lineLimit(1)
            .padding(.horizontal, size * 0.9).padding(.vertical, size * 0.25)
            .background(Capsule().fill(red ? c.heroAccent : .clear))
    }

    private func nameLine(_ size: CGFloat, lines: Int = 1) -> some View {
        Text(name ?? " ").font(.custom("PlayfairDisplay-Italic", fixedSize: size))
            .foregroundStyle(c.heroAccent).multilineTextAlignment(.center).lineLimit(lines).minimumScaleFactor(0.7)
    }

    private func small(_ info: DayInfo) -> some View {
        let leap = info.lunar.leap ? " nhuận" : ""
        return VStack(spacing: 2) {
            monthLine(8)
            number(64)
            weekday(info, 9)
            Text("\(info.lunar.day) tháng \(monthNames[info.lunar.month - 1])\(leap)")
                .font(.custom("PlayfairDisplay-Italic", fixedSize: 12)).foregroundStyle(.white.opacity(0.75))
                .lineLimit(1).minimumScaleFactor(0.7).padding(.top, 3)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func medium(_ info: DayInfo) -> some View {
        HStack(spacing: 8) {
            VStack(spacing: 4) {
                monthLine(9)
                number(76)
                weekday(info, 10)
            }
            .frame(maxWidth: .infinity)
            VStack(spacing: 6) {
                LunarRing(info: info, size: 92)
                if name != nil { nameLine(12) }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func large(_ info: DayInfo) -> some View {
        VStack(spacing: 0) {
            monthLine(10)
            number(128).padding(.top, 6)
            weekday(info, 12).padding(.top, 4)
            nameLine(15).padding(.top, 8)
            HStack(spacing: 10) {
                LinearGradient(colors: [.clear, c.heroAccent.opacity(0.55)], startPoint: .leading, endPoint: .trailing).frame(height: 1)
                Rectangle().fill(c.heroAccent.opacity(0.8)).frame(width: 5, height: 5).rotationEffect(.degrees(45))
                LinearGradient(colors: [c.heroAccent.opacity(0.55), .clear], startPoint: .leading, endPoint: .trailing).frame(height: 1)
            }
            .padding(.top, 10)
            HStack(spacing: 16) {
                LunarRing(info: info, size: 96)
                Rectangle().fill(c.heroAccent.opacity(0.35)).frame(width: 1, height: 72)
                VStack(alignment: .leading, spacing: 5) {
                    ForEach([("NĂM", info.canChiYear), ("THÁNG", info.canChiMonth), ("NGÀY", info.canChiDay)], id: \.0) { label, value in
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(label).font(.custom("BeVietnamPro-SemiBold", fixedSize: 8)).tracking(2)
                                .foregroundStyle(.white.opacity(0.55)).frame(width: 40, alignment: .leading)
                            Text(value).font(AppFont.serif(15, .semibold)).foregroundStyle(.white).lineLimit(1).fixedSize()
                        }
                    }
                }
            }
            .padding(.top, 14)
        }
        .padding(.horizontal, 20).padding(.vertical, 22)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// The lunar day in a gold ring: "ÂM LỊCH" (or "NHUẬN"), the day, "tháng …".
private struct LunarRing: View {
    let info: DayInfo
    let size: CGFloat
    @Environment(\.colors) private var c

    var body: some View {
        let name = monthNames[info.lunar.month - 1]
        VStack(spacing: 0) {
            Text(info.lunar.leap ? "NHUẬN" : "ÂM LỊCH")
                .font(.custom("BeVietnamPro-SemiBold", fixedSize: size * 0.085)).tracking(size * 0.024)
                .foregroundStyle(c.heroAccent.opacity(0.85))
            Text(verbatim: "\(info.lunar.day)").font(AppFont.serifFigures(size * 0.4)).goldFoil(c.heroAccent).frame(height: size * 0.41)
            Text("tháng \(name)").font(.custom("PlayfairDisplay-Italic", fixedSize: size * (name.count > 5 ? 0.1 : 0.115)))
                .foregroundStyle(.white.opacity(0.7)).lineLimit(1).padding(.top, 1)
        }
        .frame(width: size, height: size)
        .background(Circle().fill(.black.opacity(0.12)))
        .overlay(Circle().inset(by: size * 0.05).stroke(c.heroAccent.opacity(0.28), lineWidth: 1))
        .overlay(Circle().stroke(c.heroAccent.opacity(0.55), lineWidth: 1))
    }
}

// MARK: - Lịch tháng

/// Lịch tháng: this month as the Calendar's grid (solar day, small lunar day, today ringed, Sundays and
/// holidays red, dots for a good / bad day, a clash, an event); the header names today's events.
/// Large: every week; medium and small: three weeks from today's (moved back near the month's end so
/// the rows stay full; small has room for no more). Medium and large open Lịch on the day tapped.
struct MonthWidgetView: View {
    let today: SolarDate
    let family: WidgetFamily
    let userChi: String?
    let personal: [LunarEvent]
    @Environment(\.colors) private var c

    var body: some View {
        let month = monthGrid(today.year, today.month)
        let allRows = month.count / 7
        let small = family == .systemSmall
        GeometryReader { g in
            let pad: CGFloat = small ? 10 : 14
            let titleSize: CGFloat = small ? 12 : 15
            let weekSize: CGFloat = small ? 8 : 10
            let headerH = titleSize * 1.5
            let weekH = weekSize * 1.7
            let cellW = (g.size.width - 2 * pad) / 7
            let gridH = g.size.height - 2 * pad - headerH - weekH
            // Rows and type as Android's MonthWidget: small cells get type up to 28 % bigger; a cell
            // must hold 1.71 of the solar size (the two lines' ink is about 1.55 of it); the whole
            // month when it nearly fits, else the weeks that fit from today's (at least 3). Small
            // shows the whole month, as Android's 2×2 (3 weeks there left the rows far apart and
            // today's ring a tall rectangle, user 2026-11-28).
            let boost = min(max(1.28 - (cellW - 22) / 14 * 0.28, 1), 1.28)
            let fitH: CGFloat = 1.71
            let fit = gridH / (cellW * 0.46 * boost / min(0.55 * boost, 1 / fitH))
            let rows = small || fit >= CGFloat(allRows) - 0.15 ? allRows
                : fit >= 1.7 ? min(max(Int(fit), min(3, allRows)), allRows) : min(max(Int(fit.rounded()), 1), allRows)
            let first = min(max(0, (month.firstIndex(of: today) ?? 0) / 7), allRows - rows)
            let grid = Array(month[first * 7 ..< (first + rows) * 7])
            let cellH = gridH / CGFloat(rows)
            let solar = min(cellW * 0.46 * boost, cellH * 0.55 * boost, cellH / fitH)
            VStack(spacing: 0) {
                header(titleSize, small: small).frame(height: headerH)
                HStack(spacing: 0) {
                    ForEach(CalendarData.calendar.weekDays, id: \.self) { w in
                        Text(w).font(.custom("BeVietnamPro-Medium", fixedSize: weekSize))
                            .foregroundStyle(w == "CN" ? c.lunar : c.fg3).frame(maxWidth: .infinity)
                    }
                }
                .frame(height: weekH)
                ForEach(0..<rows, id: \.self) { r in
                    HStack(spacing: 0) {
                        ForEach(grid[r * 7 ..< r * 7 + 7], id: \.self) { d in
                            let cell = DayCellView(day: d, today: today, userChi: userChi, personal: personal, solarSize: solar, cellWidth: cellW, cellHeight: cellH)
                                .frame(width: cellW, height: cellH)
                            if small { cell } else { Link(destination: widgetLink("calendar", d)) { cell } }
                        }
                    }
                }
            }
            .padding(pad)
        }
    }

    private func header(_ size: CGFloat, small: Bool) -> some View {
        let events = todayEvents()
        return HStack(spacing: 6) {
            Text("Tháng \(today.month)").font(.custom("BeVietnamPro-Bold", fixedSize: size)).foregroundStyle(c.fg)
            if !small || events == nil {
                Text(String(today.year)).font(.custom("BeVietnamPro-Bold", fixedSize: size)).foregroundStyle(c.fg3)
                    .layoutPriority(events == nil ? 1 : -1)
            }
            Spacer(minLength: 0)
            if let events {
                Text(events.text).font(.custom("BeVietnamPro-Bold", fixedSize: size * 0.72))
                    .foregroundStyle(events.festive ? c.gold : c.lunar).lineLimit(1).truncationMode(.tail)
            }
        }
        .lineLimit(1)
    }

    /// Today's names in one line: a holiday, the user's own events, commemorative days and festivals,
    /// else mồng 1 / rằm; gold when the first is the user's own or a commemorative day / festival.
    private func todayEvents() -> (text: String, festive: Bool)? {
        guard let info = DayInfo.orNil(today) else { return nil }
        let own = PersonalEvents.on(personal, today).map(\.title)
        let fallback = info.holiday == nil && own.isEmpty && info.observances.isEmpty ? lunarDayLabel(info.lunar) : nil
        let names = [info.holiday].compactMap { $0 } + own + info.observances.map(\.title) + [fallback].compactMap { $0 }
        guard !names.isEmpty else { return nil }
        return (names.joined(separator: " · "), info.holiday == nil && (!own.isEmpty || !info.observances.isEmpty))
    }
}

private struct DayCellView: View {
    let day: SolarDate
    let today: SolarDate
    let userChi: String?
    let personal: [LunarEvent]
    let solarSize: CGFloat
    let cellWidth: CGFloat
    let cellHeight: CGFloat
    @Environment(\.colors) private var c

    var body: some View {
        let info = DayInfo.orNil(day)
        let isToday = day == today
        // Today's ring (1 pt padding, 1.5 pt stroke) leaves less height than the other cells have:
        // its two lines (ink about 1.55 of the solar size) shrink to fit inside it, as on Android.
        let solarSize = isToday ? self.solarSize * min(1, max(0.6, (cellHeight - 7) / (self.solarSize * 1.55))) : self.solarSize
        let inMonth = day.month == today.month
        let red = day.isSunday || info?.holiday != nil
        let fade = inMonth ? 1.0 : 0.35
        let dot = max(2.5, min(5, solarSize * 0.3))
        let eventColor = PersonalEvents.on(personal, day).first.map { c.widgetEvent($0.color) }
        var dayDots: [Color] = []
        if info?.rating == .good { dayDots.append(c.good) }
        if info?.rating == .bad { dayDots.append(c.bad.opacity(0.7)) }
        if let info, Zodiac.clashes(userChi, info) { dayDots.append(c.clash) }
        // The lines' font boxes overlap: the gap between the digits is then about 0.44 of the solar
        // digits' height, as on Android (measured on both widgets 2026-11-28: Android 23 px under 52 px
        // digits; iOS with spacing 0.12 left 33 px under 43 px).
        return VStack(spacing: -solarSize * 0.11) {
            withDots(eventColor.map { [$0] } ?? [], dot) {
                Text(verbatim: "\(day.day)")
                    .font(.custom(isToday ? "BeVietnamPro-Bold" : "BeVietnamPro-Medium", fixedSize: solarSize))
                    .foregroundStyle(isToday ? c.brand : red ? c.lunar : c.fg)
            }
            if let l = info?.lunar {
                let mark = l.day == 1 || l.day == 15
                let s = SmallDate.lunar(l, selected: isToday)
                // "20/10" with its dots (and the gap that keeps it centred) may be wider than the cell:
                // smaller type then, so the dots stay inside today's ring (as Android's DayCell).
                // Be Vietnam Pro's digits are 0.66 of the size wide, "/" 0.5: about 0.63 a letter.
                // Centred, the dots need their width on both sides; a narrow cell (small widget,
                // about 21 pt) drops the left one and the date sits a little left, as Android's
                // WithDots. Today's ring takes 2.5 pt a side and keeps a little air inside.
                let text = s.month.map { "\(s.day)/\($0)" } ?? "\(s.day)"
                let natural = solarSize * 0.62
                let room = cellWidth - (isToday ? 8 : 2)
                let textW = CGFloat(text.count) * 0.63
                let side = dayDots.isEmpty ? 0 : dot * 1.4
                let centred = natural * textW + 2 * side <= room
                let size = max(min(natural, (room - (centred ? 2 : 1) * side) / textW), natural * 0.6)
                withDots(dayDots, dot, centred: centred) {
                    HStack(alignment: .top, spacing: 0) {
                        Text(verbatim: text)
                        if s.month != nil && s.leap { Text("n").font(.custom(mark ? "BeVietnamPro-Bold" : "BeVietnamPro-Regular", fixedSize: size * 0.7)) }
                    }
                    .font(.custom(mark ? "BeVietnamPro-Bold" : "BeVietnamPro-Regular", fixedSize: size))
                    .foregroundStyle(mark ? c.lunar : c.fg3)
                    .lineLimit(1).minimumScaleFactor(0.6)
                }
            }
        }
        .opacity(fade)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            if isToday {
                RoundedRectangle(cornerRadius: 7).fill(c.brandSoft)
                    .overlay(RoundedRectangle(cornerRadius: 7).strokeBorder(c.brand, lineWidth: 1.5))
                    .padding(1)
            }
        }
    }

    /// [text] with [dots] stacked just after it; the text stays centred unless [centred] is false
    /// (no room for the balancing gap before it).
    private func withDots<T: View>(_ dots: [Color], _ size: CGFloat, centred: Bool = true, @ViewBuilder _ text: () -> T) -> some View {
        HStack(spacing: size * 0.4) {
            if !dots.isEmpty && centred { Color.clear.frame(width: size, height: 1) }
            text()
            if !dots.isEmpty {
                VStack(spacing: 1) { ForEach(Array(dots.enumerated()), id: \.offset) { _, col in Circle().fill(col).frame(width: size, height: size) } }
            }
        }
    }
}

private extension LichAmColors {
    /// The event's colour token (shared/design/events.md: gold, brand, lunar, good, clash).
    func widgetEvent(_ token: String?) -> Color {
        switch token { case "brand": brand; case "lunar": lunar; case "good": good; case "clash": clash; default: gold }
    }
}
