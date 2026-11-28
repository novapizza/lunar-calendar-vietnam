import SwiftUI
import UniformTypeIdentifiers
import LunarCore

// The Tiện ích sheets, from Android's ui/Sheets.kt (the web's modals): Đổi ngày âm – dương, Tìm ngày
// tốt, Xuất lịch, Thông tin ứng dụng, Chọn ngày sinh.

private let firstYear = 1900
private let lastYear = 2099
private func yearPage(_ year: Int) -> Int { firstYear + (year - firstYear) / 12 * 12 }

/// A lunar month of a lunar year: number, leap flag, solar first day, length.
private struct LunarMonthInfo: Hashable {
    let month: Int
    let leap: Bool
    let first: SolarDate
    let days: Int
}

/// The 12 or 13 months of lunar year [year] in order, leaving out those outside the tables.
private func lunarMonthsOf(_ year: Int) -> [LunarMonthInfo] {
    guard let months = LunarCalendar.monthsOf(year) else { return [] }
    let nextJd = LunarCalendar.monthsOf(year + 1)?.first?.jd
    return months.enumerated().compactMap { i, m in
        guard let end = i + 1 < months.count ? months[i + 1].jd : nextJd else { return nil }
        let first = LunarCalendar.jdToDate(m.jd)
        return first.isInRange ? LunarMonthInfo(month: m.month, leap: m.leap, first: first, days: end - m.jd) : nil
    }
}

private let threeColumns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)
private let sevenColumns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

/// "T2 … CN" over a day grid, CN in the lunar red.
private struct WeekdayRow: View {
    @Environment(\.colors) private var c

    var body: some View {
        LazyVGrid(columns: sevenColumns, spacing: 4) {
            ForEach(CalendarData.calendar.weekDays, id: \.self) { w in
                Text(w).font(AppFont.sans(11, .semibold)).foregroundStyle(w == "CN" ? c.lunar : c.fg3)
            }
        }
        .padding(.bottom, 4)
        .accessibilityHidden(true)
    }
}

/// One day of a picker grid: the big number, the small date of the other calendar under it.
private struct DayGridCell: View {
    let date: SolarDate
    let big: Int
    let small: SmallDate?
    /// The small date in the lunar red (a lunar 1st or 15th, a solar 1st).
    let mark: Bool
    let active: Bool
    let isToday: Bool
    let enabled: Bool
    let idPrefix: String
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text("\(big)").font(AppFont.sans(15, .semibold))
                    .foregroundStyle(active ? c.onBrand : date.isSunday ? c.lunar : c.fg)
                if let small {
                    SmallDateText(date: small, size: 10, weight: !active && mark ? .semibold : .regular,
                                  color: active ? c.onBrand.opacity(0.8) : mark ? c.lunar : c.fg3)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(RoundedRectangle(cornerRadius: 12).fill(active ? c.brand : .clear))
            .overlay { if !active && isToday { RoundedRectangle(cornerRadius: 12).strokeBorder(c.brand, lineWidth: 2) } }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
        .accessibilityIdentifier("\(idPrefix)-\(date.iso)")
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}

/// Blank cells before the 1st, so it falls under its weekday.
private func leading(_ first: SolarDate?) -> [SolarDate?] { Array(repeating: nil, count: first?.weekdayIndex ?? 0) }

// MARK: - Đổi ngày âm – dương

/// Android's QuickDateSheet: the solar and lunar dates side by side; the one being picked has the
/// brand ring, the year → month → day grids below.
struct QuickDateSheet: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var selected: SolarDate
    @State private var lunarCal = false
    @State private var mode = 2 // 0 years, 1 months, 2 days
    @State private var viewYear: Int
    /// Solar: month 0–11 of viewYear; lunar: index in lunarMonthsOf(viewYear).
    @State private var viewMonth: Int
    @State private var pageStart: Int

    init(start: SolarDate) {
        _selected = State(initialValue: start)
        _viewYear = State(initialValue: start.year)
        _viewMonth = State(initialValue: start.month - 1)
        _pageStart = State(initialValue: yearPage(start.year))
    }

    private func lastIndex(_ y: Int) -> Int { lunarCal ? lunarMonthsOf(y).count - 1 : 11 }

    private func showIn(lunar lunarMode: Bool) {
        let l = LunarCalendar.solarToLunar(selected)
        lunarCal = lunarMode
        mode = 2
        viewYear = lunarMode ? l.year : selected.year
        viewMonth = lunarMode
            ? max(0, lunarMonthsOf(l.year).firstIndex { $0.month == l.month && $0.leap == l.leap } ?? 0)
            : selected.month - 1
    }

    private func step(_ dir: Int) {
        switch mode {
        case 0: pageStart += dir * 12
        case 1: viewYear += dir
        default:
            if dir < 0 && viewMonth == 0 {
                viewYear -= 1
                viewMonth = lastIndex(viewYear)
            } else if dir > 0 && viewMonth == lastIndex(viewYear) {
                viewYear += 1
                viewMonth = 0
            } else {
                viewMonth += dir
            }
        }
    }

    var body: some View {
        let t = today()
        let todayLunar = LunarCalendar.solarToLunar(t)
        let lunar = LunarCalendar.solarToLunar(selected)
        let lunarMonths = lunarCal ? lunarMonthsOf(viewYear) : []
        let shown = lunarCal && !lunarMonths.isEmpty ? lunarMonths[min(viewMonth, lunarMonths.count - 1)] : nil
        let pageEnd = min(pageStart + 11, lastYear)
        let title = switch mode {
        case 0: "\(String(pageStart)) – \(String(pageEnd))"
        case 1: String(viewYear)
        default: !lunarCal ? "Tháng \(viewMonth + 1), \(String(viewYear))"
            : shown.map { "Tháng \($0.month)\($0.leap ? " nhuận" : ""), \(String(viewYear))" } ?? String(viewYear)
        }
        let canPrev = switch mode {
        case 0: pageStart > firstYear
        case 1: viewYear > firstYear
        default: viewYear > firstYear || viewMonth > 0
        }
        let canNext = switch mode {
        case 0: pageStart + 12 <= lastYear
        case 1: viewYear < lastYear
        default: viewYear < lastYear || viewMonth < lastIndex(viewYear)
        }
        let unit = switch mode { case 0: "12 năm"; case 1: "Năm"; default: "Tháng" }
        let calName = lunarCal ? "âm" : "dương"
        SheetFrame(title: "Đổi ngày âm – dương", icon: "calendar",
                   footer: AnyView(PrimaryButton(text: "Xem ngày \(selected.day)/\(selected.month)/\(String(selected.year))", tall: true) {
                       state.selectTab(.calendar)
                       state.selectDate(selected)
                       state.closeSheet()
                   }),
                   onClose: { state.closeSheet() }) {
            HStack(spacing: 10) {
                DateCard(active: !lunarCal, icon: "sun.max", label: "Dương lịch",
                         value: "\(selected.day)/\(selected.month)/\(String(selected.year))", detail: selected.weekdayName) { showIn(lunar: false) }
                DateCard(active: lunarCal, icon: "moon.stars", label: "Âm lịch",
                         value: "\(lunar.day)/\(lunar.month)/\(String(lunar.year))",
                         detail: (lunar.leap ? "Tháng nhuận · " : "") + DayInfo.of(selected).canChiYear) { showIn(lunar: true) }
            }
            NavHeading(title: title, canPrev: canPrev, canNext: canNext, prevLabel: "\(unit) trước", nextLabel: "\(unit) sau",
                       onPrev: { step(-1) }, onNext: { step(1) },
                       onTitle: mode == 2 ? { mode = 1 } : mode == 1 ? { pageStart = yearPage(viewYear); mode = 0 } : nil)
                .padding(.top, 16)
            Text(mode == 0 ? "Chọn năm \(calName) lịch" : mode == 1 ? "Chọn tháng \(calName) lịch"
                 : lunarCal ? "Chọn ngày âm · số nhỏ là ngày dương" : "Chọn ngày dương · số nhỏ là ngày âm")
                .font(AppFont.sans(12)).foregroundStyle(c.fg2)
                .frame(maxWidth: .infinity).padding(.top, 4).padding(.bottom, 12)
            switch mode {
            case 0:
                let selYear = lunarCal ? lunar.year : selected.year
                let curYear = lunarCal ? todayLunar.year : t.year
                LazyVGrid(columns: threeColumns, spacing: 10) {
                    ForEach(pageStart..<pageStart + 12, id: \.self) { y in
                        GridCell(text: String(y), active: y == selYear, current: y == curYear) { viewYear = y; mode = 1 }
                            .opacity(y > lastYear ? 0 : 1).disabled(y > lastYear)
                    }
                }
            case 1:
                LazyVGrid(columns: threeColumns, spacing: 10) {
                    if !lunarCal {
                        ForEach(0..<12, id: \.self) { m in
                            GridCell(text: "Tháng \(m + 1)", active: viewYear == selected.year && m + 1 == selected.month,
                                     current: viewYear == t.year && m + 1 == t.month) { viewMonth = m; mode = 2 }
                        }
                    } else {
                        ForEach(Array(lunarMonths.enumerated()), id: \.offset) { i, m in
                            GridCell(text: "Tháng \(m.month)" + (m.leap ? "\nnhuận" : ""),
                                     active: viewYear == lunar.year && m.month == lunar.month && m.leap == lunar.leap,
                                     current: viewYear == todayLunar.year && m.month == todayLunar.month && m.leap == todayLunar.leap) {
                                viewMonth = i
                                mode = 2
                            }
                        }
                    }
                }
            default:
                // One cell per day of the month on show: the big number in the calendar being picked,
                // the small one in the other ("1/m" on the first of a month).
                let days: [SolarDate] = !lunarCal
                    ? (1...daysInMonth(viewYear, viewMonth + 1)).map { SolarDate(viewYear, viewMonth + 1, $0) }
                    : shown.map { m in (0..<m.days).map { m.first.plusDays($0) } } ?? []
                WeekdayRow()
                LazyVGrid(columns: sevenColumns, spacing: 4) {
                    ForEach(Array((leading(days.first) + days).enumerated()), id: \.offset) { _, d in
                        if let d {
                            let info = DayInfo.orNil(d)
                            let l = info?.lunar
                            let small: SmallDate? = l.map { !lunarCal ? SmallDate.lunar($0, selected: d == selected) : SmallDate.solar(d, selected: d == selected) }
                            DayGridCell(date: d, big: lunarCal ? (l?.day ?? d.day) : d.day, small: small,
                                        mark: l.map { lunarCal ? d.day == 1 : $0.day == 1 || $0.day == 15 } ?? false,
                                        active: d == selected, isToday: d == t, enabled: info != nil, idPrefix: "q") { selected = d }
                        } else {
                            Color.clear.frame(height: 48)
                        }
                    }
                }
            }
        }
        .presentationDetents([.large])
    }
}

/// One of Đổi ngày's two dates: label, the date in that calendar, a detail line.
private struct DateCard: View {
    let active: Bool
    let icon: String
    let label: String
    let value: String
    let detail: String
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                SectionLabel(text: label, icon: icon)
                Text(value).font(AppFont.sans(20, .heavy)).foregroundStyle(c.fg).lineLimit(1).minimumScaleFactor(0.8).padding(.top, 4)
                Text(detail).font(AppFont.sans(12)).foregroundStyle(c.fg2).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 16).fill(active ? c.brandSoft : c.surface2))
            .overlay { if active { RoundedRectangle(cornerRadius: 16).strokeBorder(c.brand, lineWidth: 2) } }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label) \(value), \(detail)")
        .accessibilityAddTraits(active ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Tìm ngày tốt

/// Android's AuspiciousSheet: three kinds of work; the good days in the next two months.
struct AuspiciousSheet: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var active: String?
    @State private var results: [AuspiciousDate] = []
    private let icons = ["WEDDING": "heart", "GROUNDBREAKING": "hammer", "OPENING": "storefront"]

    var body: some View {
        SheetFrame(title: "Tìm ngày tốt", icon: "magnifyingglass", subtitle: "Gợi ý trong 2 tháng tới", onClose: { state.closeSheet() }) {
            HStack(spacing: 8) {
                ForEach(Auspicious.activities, id: \.key) { a in
                    let on = active == a.key
                    Button {
                        active = a.key
                        results = Auspicious.find(a.key, from: today())
                    } label: {
                        VStack(spacing: 8) {
                            Image(systemName: icons[a.key] ?? "star").font(.system(size: 18, weight: .medium))
                            Text(a.label).font(AppFont.sans(14, .semibold)).lineLimit(1).minimumScaleFactor(0.8)
                        }
                        .foregroundStyle(on ? c.onBrand : c.fg)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 8).padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 16).fill(on ? c.brand : c.surface2))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            Group {
                if !results.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: "\(results.count) ngày phù hợp")
                        ForEach(results, id: \.date) { item in row(item) }
                    }
                } else {
                    Text(active != nil ? "Không tìm thấy ngày phù hợp trong khoảng thời gian này." : "Chọn loại việc ở trên để xem các ngày tốt.")
                        .font(AppFont.sans(14)).foregroundStyle(c.fg2).multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity).padding(.horizontal, 24).padding(.vertical, 40)
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(c.line, style: StrokeStyle(lineWidth: 1, dash: [6, 4])))
                }
            }
            .padding(.top, 20)
            Text("Dựa trên hệ thống Trực truyền thống. Chỉ mang tính chất tham khảo.")
                .font(AppFont.sans(11)).foregroundStyle(c.fg3).multilineTextAlignment(.center)
                .frame(maxWidth: .infinity).padding(.top, 20)
        }
        .presentationDetents([.large])
    }

    private func row(_ item: AuspiciousDate) -> some View {
        Button {
            state.selectTab(.calendar)
            state.selectDate(item.date)
            state.closeSheet()
        } label: {
            HStack(spacing: 12) {
                VStack(spacing: 2) {
                    Text("\(item.date.day)").font(AppFont.sans(18, .heavy)).foregroundStyle(c.brand)
                    Text("Th.\(item.date.month)").font(AppFont.sans(10, .semibold)).foregroundStyle(c.fg2)
                }
                .frame(width: 48, height: 48)
                .background(RoundedRectangle(cornerRadius: 12).fill(c.surface))
                VStack(alignment: .leading, spacing: 0) {
                    Text(item.date.longLabel).font(AppFont.sans(14, .bold)).foregroundStyle(c.fg)
                    Text("Âm \(item.lunarDate) · Trực \(item.truc)").font(AppFont.sans(12)).foregroundStyle(c.fg2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if item.score == 10 {
                    HStack(spacing: 4) {
                        Image(systemName: "sparkles").font(.system(size: 10, weight: .bold))
                        Text("Rất hợp").font(AppFont.sans(11, .bold))
                    }
                    .foregroundStyle(c.good)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(Capsule().fill(c.goodSoft))
                }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.fg3)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16).fill(c.surface2))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Xuất lịch

/// The .ics type (com.apple.ical.ics), or the generic calendar event type.
private let icsType = UTType(filenameExtension: "ics") ?? .calendarEvent

/// The exported calendar, for the system's "save to Files" sheet.
private struct IcsDocument: FileDocument {
    static var readableContentTypes: [UTType] { [icsType] }
    let text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

/// Android's LunarEventsSheet: built-in event groups to tick, the user's events, the number of years;
/// "Tải file .ics" saves the file where the user picks (Android's CreateDocument).
struct LunarEventsSheet: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var yearCount = 5
    @State private var result: IcsResult?
    @State private var pending: IcsResult?
    @State private var exporting = false
    @State private var failed = false
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 2)

    var body: some View {
        let startYear = today().year
        let all = state.exportEvents
        // VEVENTs written: one per rule, or one per occurrence (lunar repeats give 12–13 a year when monthly).
        let total = Ics.count(all, startYear, yearCount)
        SheetFrame(title: "Xuất lịch", icon: "square.and.arrow.down", subtitle: "Tạo file .ics cho Google Calendar, Apple Calendar, Outlook",
                   footer: AnyView(PrimaryButton(text: all.isEmpty ? "Chọn sự kiện để xuất" : "Tải file .ics · \(total) mục",
                                                 icon: "arrow.down.doc", enabled: !all.isEmpty, tall: true) {
                       pending = Ics.build(all, startYear, yearCount)
                       exporting = true
                   }),
                   onClose: { state.closeSheet() }) {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel(text: "Ngày lễ có sẵn")
                    LazyVGrid(columns: columns, spacing: 8) {
                        ForEach(CalendarData.presets.presets, id: \.id) { p in preset(p) }
                    }
                }
                PersonalEventsList(emptyText: "Thêm giỗ, sinh nhật… để xuất cùng lúc.")
                if !all.isEmpty { range(all: all, startYear: startYear) }
            }
        }
        .presentationDetents([.large])
        .fileExporter(isPresented: $exporting, document: pending.map { IcsDocument(text: $0.text) }, contentType: icsType,
                      defaultFilename: Ics.fileName(startYear, yearCount)) { outcome in
            switch outcome {
            case .success: result = pending
            case .failure: failed = true
            }
            pending = nil
        }
        .alert("Không ghi được file", isPresented: $failed) { Button("OK", role: .cancel) {} }
    }

    private func preset(_ p: PresetJson) -> some View {
        let on = state.enabledPresets.contains(p.id)
        return Button {
            state.togglePreset(p.id)
            result = nil
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(p.emoji).font(.system(size: 24))
                Text(p.label).font(AppFont.sans(14, .bold)).foregroundStyle(on ? c.brand : c.fg)
                Text(p.description).font(AppFont.sans(11)).foregroundStyle(c.fg2).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16).fill(on ? c.brandSoft : c.surface2))
            .overlay { if on { RoundedRectangle(cornerRadius: 16).strokeBorder(c.brand, lineWidth: 2) } }
            .overlay(alignment: .topTrailing) {
                ZStack {
                    Circle().fill(on ? c.brand : .clear)
                    if on { Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(c.onBrand) }
                    else { Circle().strokeBorder(c.line, lineWidth: 2) }
                }
                .frame(width: 20, height: 20).padding(10)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(p.label), \(p.description)")
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
    }

    @ViewBuilder private func range(all: [LunarEvent], startYear: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                SectionLabel(text: "Khoảng thời gian")
                Spacer()
                Text("\(String(startYear)) – \(String(startYear + yearCount - 1))").font(AppFont.sans(14, .bold)).foregroundStyle(c.fg)
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    Slider(value: Binding(get: { Double(yearCount) }, set: { yearCount = Int($0.rounded()); result = nil }), in: 1...30, step: 1)
                        .tint(c.brand)
                        .accessibilityLabel("Số năm")
                        .accessibilityValue("\(yearCount) năm")
                    Text("\(yearCount) năm").font(AppFont.sans(14, .bold)).foregroundStyle(c.brand).frame(width: 64, alignment: .trailing)
                }
                Text("\(all.count) sự kiện × \(yearCount) năm. Ngày âm lịch mỗi năm rơi vào ngày dương lịch khác.")
                    .font(AppFont.sans(12)).foregroundStyle(c.fg2)
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 16).fill(c.surface2))
            if let r = result { done(r) }
        }
    }

    private func done(_ r: IcsResult) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle").font(.system(size: 15, weight: .semibold))
                Text("Đã tạo \(r.totalEvents) sự kiện").font(AppFont.sans(14, .semibold))
            }
            .foregroundStyle(c.good)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 16).fill(c.goodSoft))
            if r.skippedCount > 0 {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle").font(.system(size: 12, weight: .semibold)).foregroundStyle(c.gold)
                        Text("Bỏ qua \(r.skippedCount) mục").font(AppFont.sans(12, .bold)).foregroundStyle(c.fg)
                    }
                    .padding(.bottom, 2)
                    ForEach(Array(r.skipped.enumerated()), id: \.offset) { _, s in
                        Text("• \(Text(s.title).font(AppFont.sans(12, .semibold)).foregroundStyle(c.fg)) năm âm \(String(s.lunarYear))\(s.reason == "leap_month_not_found" ? " — năm này không có tháng nhuận đó" : " — lỗi chuyển đổi ngày")")
                            .font(AppFont.sans(12)).foregroundStyle(c.fg2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(c.goldSoft))
            }
            Text("Google Calendar → Cài đặt → Nhập và xuất → chọn file .ics vừa tải")
                .font(AppFont.sans(12)).foregroundStyle(c.fg2).multilineTextAlignment(.center).frame(maxWidth: .infinity)
        }
        .padding(.top, 4)
    }
}

// MARK: - Thông tin ứng dụng

struct AboutSheet: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        SheetFrame(title: "Thông tin ứng dụng", icon: "info.circle",
                   footer: AnyView(PrimaryButton(text: "Đã hiểu", tall: true) { state.closeSheet() }),
                   onClose: { state.closeSheet() }) {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel(text: "Phương pháp tính toán")
                    VStack(alignment: .leading, spacing: 10) {
                        para("Âm lịch:", "theo bảng năm âm lịch tính sẵn của Hồ Ngọc Đức (giờ Việt Nam), xác định pha Mặt Trăng và 24 tiết khí theo đúng lịch truyền thống Việt Nam.")
                        para("Ngày tốt / xấu:", "đánh giá dựa trên Thập Nhị Trực (12 Trực).")
                        para("Xung tuổi:", "theo quy tắc Lục Xung của Địa Chi (Tý–Ngọ, Sửu–Mùi, Dần–Thân, Mão–Dậu, Thìn–Tuất, Tỵ–Hợi).")
                    }
                }
                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel(text: "Công nghệ")
                    VStack(alignment: .leading, spacing: 0) {
                        let items = [
                            ("Dữ liệu dùng chung", "Bảng năm âm, ngày lễ, nạp âm, quy tắc ngày tốt: cùng nguồn với bản web và Android."),
                            ("Swift + SwiftUI", "Xây dựng giao diện iOS."),
                            ("Be Vietnam Pro, Playfair Display", "Phông chữ (SIL Open Font License)."),
                        ]
                        ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                            if i > 0 { Hairline() }
                            VStack(alignment: .leading, spacing: 0) {
                                Text(item.0).font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
                                Text(item.1).font(AppFont.sans(12)).foregroundStyle(c.fg2)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                        }
                    }
                    .background(RoundedRectangle(cornerRadius: 16).fill(c.surface2))
                }
                Text("Thông tin ngày tốt/xấu chỉ mang tính chất tham khảo dựa trên tài liệu cổ học phương Đông. Hãy cân nhắc kỹ trước các quyết định quan trọng.")
                    .font(AppFont.sans(12)).foregroundStyle(c.fg).lineSpacing(4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 16).fill(c.goldSoft))
            }
        }
        .presentationDetents([.large])
    }

    private func para(_ bold: String, _ rest: String) -> some View {
        Text("\(Text(bold + " ").font(AppFont.sans(14, .bold)).foregroundStyle(c.fg))\(rest)")
            .font(AppFont.sans(14)).foregroundStyle(c.fg2).lineSpacing(5)
    }
}

// MARK: - Chọn ngày sinh

/// Android's BirthDateSheet: year → month → day (solar), up to today; "Xoá ngày sinh".
struct BirthDateSheet: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var mode: Int // 0 years, 1 months, 2 days
    @State private var viewYear: Int
    @State private var viewMonth: Int
    @State private var pageStart: Int

    init(value: SolarDate?) {
        _mode = State(initialValue: value != nil ? 2 : 0)
        _viewYear = State(initialValue: value?.year ?? 1990)
        _viewMonth = State(initialValue: value?.month ?? 1)
        _pageStart = State(initialValue: yearPage(value?.year ?? 1990))
    }

    private func monthAllowed(_ y: Int, _ m: Int, _ t: SolarDate) -> Bool {
        SolarDate(y, m, daysInMonth(y, m)) >= LunarCalendar.firstDay && SolarDate(y, m, 1) <= t
    }

    var body: some View {
        let value = state.birthDate
        let t = today()
        let maxYear = t.year
        let pageEnd = min(pageStart + 11, maxYear)
        let prev = SolarDate(viewYear, viewMonth, 1).plusMonths(-1)
        let next = SolarDate(viewYear, viewMonth, 1).plusMonths(1)
        let heading: NavHeading = switch mode {
        case 0: NavHeading(title: "\(String(pageStart)) – \(String(pageEnd))", canPrev: pageStart > firstYear, canNext: pageStart + 12 <= maxYear,
                           prevLabel: "12 năm trước", nextLabel: "12 năm sau", onPrev: { pageStart -= 12 }, onNext: { pageStart += 12 }, onTitle: nil)
        case 1: NavHeading(title: String(viewYear), canPrev: viewYear > firstYear, canNext: viewYear < maxYear,
                           prevLabel: "Năm trước", nextLabel: "Năm sau", onPrev: { viewYear -= 1 }, onNext: { viewYear += 1 },
                           onTitle: { pageStart = yearPage(viewYear); mode = 0 })
        default: NavHeading(title: "Tháng \(viewMonth), \(String(viewYear))", canPrev: monthAllowed(prev.year, prev.month, t),
                            canNext: monthAllowed(next.year, next.month, t), prevLabel: "Tháng trước", nextLabel: "Tháng sau",
                            onPrev: { viewYear = prev.year; viewMonth = prev.month }, onNext: { viewYear = next.year; viewMonth = next.month },
                            onTitle: { mode = 1 })
        }
        let footer: AnyView? = value == nil ? nil : AnyView(
            Button {
                state.updateBirthDate(nil)
                state.closeSheet()
            } label: {
                Text("Xoá ngày sinh").font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg2)
                    .frame(maxWidth: .infinity).padding(.vertical, 10).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        )
        SheetFrame(title: "Chọn ngày sinh", heading: AnyView(heading), footer: footer, onClose: { state.closeSheet() }) {
            Text(mode == 0 ? "Chọn năm sinh (dương lịch)" : mode == 1 ? "Chọn tháng sinh" : "Chọn ngày sinh · số nhỏ là ngày âm")
                .font(AppFont.sans(12)).foregroundStyle(c.fg2).padding(.bottom, 12)
            switch mode {
            case 0:
                LazyVGrid(columns: threeColumns, spacing: 10) {
                    ForEach(pageStart..<pageStart + 12, id: \.self) { y in
                        GridCell(text: String(y), active: y == value?.year, current: false) { viewYear = y; mode = 1 }
                            .opacity(y > maxYear ? 0 : 1).disabled(y > maxYear)
                    }
                }
            case 1:
                LazyVGrid(columns: threeColumns, spacing: 10) {
                    ForEach(1...12, id: \.self) { m in
                        let allowed = monthAllowed(viewYear, m, t)
                        GridCell(text: "Tháng \(m)", active: viewYear == value?.year && m == value?.month, current: false) { viewMonth = m; mode = 2 }
                            .opacity(allowed ? 1 : 0.3).disabled(!allowed)
                    }
                }
            default:
                let days = (1...daysInMonth(viewYear, viewMonth)).map { SolarDate(viewYear, viewMonth, $0) }
                WeekdayRow()
                LazyVGrid(columns: sevenColumns, spacing: 4) {
                    ForEach(Array((leading(days.first) + days).enumerated()), id: \.offset) { _, d in
                        if let d {
                            let info = DayInfo.orNil(d)
                            DayGridCell(date: d, big: d.day, small: info.map { SmallDate.lunar($0.lunar, selected: d == value) },
                                        mark: info?.lunar.day == 1, active: d == value, isToday: false,
                                        enabled: info != nil && d <= t, idPrefix: "b") {
                                state.updateBirthDate(d)
                                state.closeSheet()
                            }
                        } else {
                            Color.clear.frame(height: 48)
                        }
                    }
                }
            }
        }
        .presentationDetents([.large])
    }
}
