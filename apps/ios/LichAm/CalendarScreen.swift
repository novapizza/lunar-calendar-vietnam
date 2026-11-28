import SwiftUI
import UIKit
import LunarCore

/// One day of the month grid. [dot]: the colour token of the dot beside the solar day (the first of
/// the user's own events that day, nil: none); [personal]: that event's name (large cells only).
private struct Cell: Identifiable {
    let date: SolarDate
    let info: DayInfo?
    let dot: String?
    let clash: Bool
    let personal: String?
    var id: Int { date.jd }
}

/// Lịch (Android's CalendarScreen, the web's views/CalendarView.tsx): header with the month and
/// its lunar months, the month grid (solar day, lunar day, dots for a good or bad day, an event, a
/// zodiac clash), then the selected day's panel. Wide (iPad): grid left, panel right. One column:
/// the header stays at the top and the month folds up under it to two week rows as the page scrolls
/// (WeekFold, as Android and the web's useStickyWeeks).
struct CalendarScreen: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var picking = false
    @State private var fold = WeekFold()
    @Environment(\.wideLayout) private var wide

    var body: some View {
        TimelineView(.everyMinute) { context in
            let now = context.date
            GeometryReader { geo in
                ScrollView {
                    if wide {
                        HStack(alignment: .top, spacing: 24) {
                            VStack(spacing: 12) {
                                CalendarHeader(wide: true, pick: { picking = true })
                                MonthGrid(wide: true)
                            }
                            .frame(width: (geo.size.width - 72) * 1.25 / 2.25)
                            VStack(spacing: 12) { SelectedDayPanel(now: now) }
                        }
                        .padding(24)
                    } else {
                        VStack(spacing: 12) {
                            CalendarHeader(wide: false, pick: { picking = true }, fold: fold).zIndex(2)
                            MonthGrid(wide: false, fold: fold).zIndex(1)
                            VStack(spacing: 12) { SelectedDayPanel(now: now) }.padding(.top, 4)
                        }
                        .padding(EdgeInsets(top: 12, leading: 16, bottom: 40, trailing: 16))
                        // How far the page has scrolled (its top above the scroll view's).
                        .onGeometryChange(for: CGFloat.self) { max(0, -$0.frame(in: .scrollView).minY) } action: { v in
                            if fold.s != v { fold.s = v }
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $picking) {
            MonthYearPicker(shown: state.shownMonth)
                .environment(state)
                .environment(\.colors, c)
        }
    }
}

// MARK: - Header

private struct CalendarHeader: View {
    let wide: Bool
    let pick: () -> Void
    var fold: WeekFold? = nil
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let m = state.shownMonth
        let t = today()
        let showingThisMonth = m.year == t.year && m.month == t.month
        // clamp(1.25rem, 6.4vw, 1.5rem); text-3xl beside the panel.
        let titleSize: CGFloat = wide ? 30 : min(max(UIScreen.main.bounds.width * 0.064, 20), 24)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Button(action: pick) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(spacing: 0) {
                            Text("Tháng \(m.month) ").font(AppFont.sans(titleSize, .heavy)).tracking(-0.01 * titleSize).foregroundStyle(c.fg)
                            Text("\(String(m.year))").font(AppFont.sans(titleSize, .bold)).tracking(-0.01 * titleSize).foregroundStyle(c.fg3)
                            Image(systemName: "chevron.down").font(.system(size: 14, weight: .semibold)).foregroundStyle(c.fg3)
                                .padding(.leading, 4).padding(.top, 2)
                        }
                        .lineLimit(1)
                        Text(lunarMonthSubtitle(m.year, m.month))
                            .font(AppFont.sans(wide ? 14 : 12)).foregroundStyle(c.fg2).lineLimit(1)
                    }
                    .padding(.horizontal, 4).padding(.vertical, 2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Chọn tháng, năm")
                .accessibilityValue("Tháng \(m.month) năm \(String(m.year))")
                .accessibilityAddTraits(.isButton)
                if wide && !showingThisMonth {
                    SoftButton(text: "Hôm nay") { state.selectDate(t) }.padding(.trailing, 8)
                }
                IconBtn(icon: "chevron.left", label: "Tháng trước") { state.showMonth(m.plusMonths(-1).year, m.plusMonths(-1).month) }
                IconBtn(icon: "chevron.right", label: "Tháng sau") { state.showMonth(m.plusMonths(1).year, m.plusMonths(1).month) }
            }
            .folding(fold) { f, title in FoldTitle(fold: f, content: title) }
            .zIndex(1)
            // Chip row (mt-3), scrolling sideways; the shadows need a little room.
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if !wide && !showingThisMonth {
                        Chip(text: "Hôm nay", icon: "arrow.uturn.backward", textColor: c.brand) { state.selectDate(t) }
                    }
                    Chip(text: "Tìm ngày tốt", icon: "magnifyingglass") { state.pushSheet(.auspicious) }
                    Chip(text: "Đổi ngày", icon: "calendar") { state.pushSheet(.quickDate) }
                    Chip(text: "Xuất lịch", icon: "square.and.arrow.down") { state.pushSheet(.lunarEvents) }
                }
                .padding(.horizontal, 16).padding(.vertical, 4)
            }
            .padding(.horizontal, -16)
            .padding(.top, 8)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { h in if let fold, fold.chipsH != h { fold.chipsH = h } }
            .folding(fold) { f, chips in FoldChips(fold: f, content: chips) }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { h in if let fold, fold.headerH != h { fold.headerH = h } }
        .folding(fold) { f, header in FoldHeader(fold: f, content: header) }
    }
}

// MARK: - Month grid

private struct MonthGrid: View {
    let wide: Bool
    var fold: WeekFold? = nil
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var lastSwipe = Date.distantPast

    var body: some View {
        let m = state.shownMonth
        let userChi = state.userChi
        let t = today()
        let cells = monthGrid(m.year, m.month).map { d in
            let info = DayInfo.orNil(d)
            let first = info == nil ? nil : PersonalEvents.on(state.lunarEvents, d).first
            return Cell(date: d, info: info, dot: first?.color, clash: info.map { Zodiac.clashes(userChi, $0) } ?? false, personal: first?.title)
        }
        let rows = stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<$0 + 7]) }
        let gap: CGFloat = wide ? 4 : 2
        // The two weeks left when the month is folded: the selected day's and the next (WeekFold).
        let selectedRow = cells.firstIndex { $0.date == state.selectedDate }.map { $0 / 7 } ?? -1
        let first = fold?.pairFor(selectedRow, rows.count) ?? 0
        let grid = VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: gap) {
                ForEach(CalendarData.calendar.weekDays, id: \.self) { w in
                    Text(w).font(AppFont.sans(12, .semibold)).foregroundStyle(w == "CN" ? c.lunar : c.fg3)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                }
            }
            // Folding, the labels cover the rows moving up under them (the card's clip bounds it).
            .background(alignment: .bottom) { if fold != nil { Rectangle().fill(c.surface).padding(.top, -400) } }
            .zIndex(1)
            VStack(spacing: gap) {
                ForEach(Array(rows.enumerated()), id: \.element.first!.id) { r, week in
                    HStack(spacing: gap) {
                        ForEach(week) { cell in
                            DayCell(cell: cell, selected: cell.date == state.selectedDate, isToday: cell.date == t,
                                    inMonth: cell.date.month == m.month) {
                                // A quick flick also ends as a tap on the cell it started on (log,
                                // 2026-11-28): that tap would take the grid back to the old month.
                                if Date().timeIntervalSince(lastSwipe) > 0.4 { state.selectDate(cell.date) }
                            }
                        }
                    }
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { h in
                        if r == 0, let fold, fold.rowH != h { fold.rowH = h }
                    }
                    .folding(fold) { f, row in FoldRow(fold: f, row: r, first: first, rows: rows.count, content: row) }
                }
            }
            .padding(.top, gap)
            HStack(spacing: 16) {
                Legend(color: c.good, label: "Ngày tốt")
                Legend(color: c.bad.opacity(0.7), label: "Ngày xấu")
                Legend(color: c.gold, label: "Sự kiện")
                if userChi != nil { Legend(color: c.clash, label: "Xung tuổi") }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 12).padding(.bottom, 4)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { h in if let fold, fold.legendH != h { fold.legendH = h } }
            .folding(fold) { f, legend in FoldFade(fold: f, rows: rows.count, content: legend) }
        }
        Group {
            if let fold {
                FoldCard(fold: fold, rows: rows.count, padding: 4, content: grid)
            } else {
                Card(padding: wide ? 12 : 4) { grid }
            }
        }
        // A sideways swipe turns the month (left = next), as Android's Modifier.swipe; vertical drags
        // still scroll the page.
        .sidewaysSwipe { step in
            lastSwipe = Date()
            // The month at the time of the swipe, read from the state.
            let to = state.shownMonth.plusMonths(step)
            state.showMonth(to.year, to.month)
        }
    }
}

// MARK: - WeekFold

/// The month card getting shorter under the sticky header as the page scrolls (Android's WeekFold, the
/// web's hooks/useStickyWeeks.ts): the same card, corners, labels and legend, with two week rows left,
/// the selected day's week and the next one (the one before when it is the last; kept while the
/// selection moves between the two).
///
/// The chip row goes first: the header scrolls up by its height while the title row is held and
/// covers it as it fades. Then the card stays under the header and, over the next K pt of scrolling
/// (K = all rows but two, and the legend), loses `folded` = 0…K at the bottom: the rows move up by
/// t·first·step (t = folded / K) so rows first and first + 1 end under the labels, and the other rows
/// and the legend fade. Points, in the page's coordinates (the page's top padding 12, spacing 12).
@Observable @MainActor
final class WeekFold {
    /// How far the page has scrolled.
    var s: CGFloat = 0
    var headerH: CGFloat = 0
    /// The chip row with its 12 pt above (8 padding + 4 inside).
    var chipsH: CGFloat = 0
    var rowH: CGFloat = 0
    var legendH: CGFloat = 0
    @ObservationIgnored private var pair = 0

    private let keepGap: CGFloat = 12
    private let gap: CGFloat = 2

    /// The first of the two weeks kept: moves only when the selected week is not one of them.
    func pairFor(_ selectedRow: Int, _ rows: Int) -> Int {
        var f = pair
        if selectedRow >= 0 && selectedRow != f && selectedRow != f + 1 { f = selectedRow }
        f = min(max(f, 0), max(0, rows - 2))
        pair = f
        return f
    }

    /// Points of scrolling over which the chip row slides away (its top 12 stay under the title).
    var chipsFold: CGFloat { max(0, chipsH - keepGap) }
    var chipsHidden: CGFloat { min(max(s, 0), chipsFold) }
    /// Scroll at which the card's top meets the header's bottom (the header without its chip row):
    /// the card's top is 12 + headerH + 12, the header's bottom then 12 + headerH − chipsFold.
    private var start: CGFloat { 12 + chipsFold }
    private var step: CGFloat { rowH + gap }
    private func foldBy(_ rows: Int) -> CGFloat { max(0, CGFloat(rows - 2) * step + legendH) }
    func folded(_ rows: Int) -> CGFloat { min(max(s - start, 0), foldBy(rows)) }
    func progress(_ rows: Int) -> CGFloat { foldBy(rows) > 0 ? folded(rows) / foldBy(rows) : 0 }
    var cardShift: CGFloat { max(0, s - start) }
    func rowShift(_ first: Int, _ rows: Int) -> CGFloat { -progress(rows) * CGFloat(first) * step }
}

extension View {
    /// [wrap] applied when the page folds (one column), the view as it is otherwise.
    @ViewBuilder func folding<T: View>(_ fold: WeekFold?, _ wrap: (WeekFold, Self) -> T) -> some View {
        if let fold { wrap(fold, self) } else { self }
    }
}

// Each piece that moves with the scroll reads it in its own small view, so a scroll redraws these
// and not the 42 day cells.

/// The header: held at the top of the screen once the chip row has slid away; opaque from above
/// the screen's top across the gutters.
private struct FoldHeader<Content: View>: View {
    let fold: WeekFold
    let content: Content
    @Environment(\.colors) private var c

    var body: some View {
        content
            .background(alignment: .bottom) { Rectangle().fill(c.bg).padding(.horizontal, -16).padding(.top, -400) }
            .offset(y: fold.s - fold.chipsHidden)
    }
}

/// The title row: held while the header scrolls by the chip row's height; opaque above it, so the
/// chips slide away under it.
private struct FoldTitle<Content: View>: View {
    let fold: WeekFold
    let content: Content
    @Environment(\.colors) private var c

    var body: some View {
        content
            .background(alignment: .bottom) { Rectangle().fill(c.bg).padding(.horizontal, -16).padding(.top, -400) }
            .offset(y: fold.chipsHidden)
    }
}

/// The chip row fades as it slides away.
private struct FoldChips<Content: View>: View {
    let fold: WeekFold
    let content: Content

    var body: some View {
        content.opacity(fold.chipsFold > 0 ? 1 - fold.chipsHidden / fold.chipsFold : 1)
    }
}

/// The card: stays under the header and gets shorter, its corners and shadow with it; the page's
/// colour behind it, beside it and 16 pt under it, so the cards scrolling up under it never show.
private struct FoldCard<Content: View>: View {
    let fold: WeekFold
    let rows: Int
    let padding: CGFloat
    let content: Content
    @Environment(\.colors) private var c

    var body: some View {
        let cut = fold.folded(rows)
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .clipShape(ShorterCard(cut: cut))
            .background { ShorterCard(cut: cut).fill(c.surface).webShadow(c.shadowTint) }
            .background(alignment: .top) { Rectangle().fill(c.bg).padding(.horizontal, -16).padding(.bottom, cut - 16) }
            .offset(y: fold.cardShift)
    }
}

/// A week row: moves up with the fold; the rows other than the two kept fade and stop taking taps
/// (a clip does not stop them).
private struct FoldRow<Content: View>: View {
    let fold: WeekFold
    let row: Int
    let first: Int
    let rows: Int
    let content: Content

    var body: some View {
        let t = fold.progress(rows)
        let kept = row == first || row == first + 1
        content
            .offset(y: fold.rowShift(first, rows))
            .opacity(kept ? 1 : 1 - t)
            .allowsHitTesting(kept || t == 0)
    }
}

/// The legend fades with the rows.
private struct FoldFade<Content: View>: View {
    let fold: WeekFold
    let rows: Int
    let content: Content

    var body: some View { content.opacity(1 - fold.progress(rows)) }
}

/// The card without its bottom [cut] points, all four corners rounded (Card's 20).
private struct ShorterCard: Shape {
    var cut: CGFloat
    var animatableData: CGFloat {
        get { cut }
        set { cut = newValue }
    }

    func path(in rect: CGRect) -> Path {
        Path(roundedRect: CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: max(0, rect.height - cut)), cornerRadius: 20)
    }
}

extension View {
    /// Calls [onSwipe] with +1 for a swipe to the left (next), -1 to the right. A flick counts by where
    /// it is heading: XCUITest's swipeLeft moved about 7 pt at about 1000 pt/s (log, 2026-11-28), short
    /// of a DragGesture's minimum distance. From iOS 18 a UIKit pan (its velocity, and recognizing
    /// together with the page's scroll view); iOS 17 a DragGesture's predicted end.
    @ViewBuilder func sidewaysSwipe(_ onSwipe: @escaping (Int) -> Void) -> some View {
        if #available(iOS 18.0, *) {
            gesture(SidewaysPan(onSwipe: onSwipe))
        } else {
            simultaneousGesture(DragGesture(minimumDistance: 10).onEnded { v in
                if let step = swipeStep(dx: v.predictedEndTranslation.width, dy: v.predictedEndTranslation.height) { onSwipe(step) }
            })
        }
    }
}

/// +1 / -1 for a mostly sideways move of at least 30 pt (Android: 60 px, about 23 pt), nil otherwise.
private func swipeStep(dx: CGFloat, dy: CGFloat) -> Int? {
    guard abs(dx) > abs(dy), abs(dx) >= 30 else { return nil }
    return dx < 0 ? 1 : -1
}

@available(iOS 18.0, *)
private struct SidewaysPan: UIGestureRecognizerRepresentable {
    let onSwipe: (Int) -> Void

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        /// Only a mostly sideways drag begins: upright ones are the page's scroll alone.
        func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
            guard let pan = g as? UIPanGestureRecognizer else { return true }
            let t = pan.translation(in: pan.view)
            let v = pan.velocity(in: pan.view)
            let ok = t == .zero ? abs(v.x) > abs(v.y) : abs(t.x) > abs(t.y)
            return ok
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    }

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator { Coordinator() }

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let pan = UIPanGestureRecognizer()
        pan.delegate = context.coordinator
        return pan
    }

    func handleUIGestureRecognizerAction(_ pan: UIPanGestureRecognizer, context: Context) {
        guard pan.state == .ended else { return }
        // Where the touch is heading: the move so far plus 0.1 s at its speed.
        let t = pan.translation(in: pan.view)
        let v = pan.velocity(in: pan.view)
        if let step = swipeStep(dx: t.x + v.x * 0.1, dy: t.y + v.y * 0.1) { onSwipe(step) }
    }
}

private struct Legend: View {
    let color: Color
    let label: String
    @Environment(\.colors) private var c

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 6, height: 6)
            Text(label).font(AppFont.sans(11)).foregroundStyle(c.fg2).lineLimit(1)
        }
    }
}

private struct Dot: View {
    let color: Color
    var body: some View { Circle().fill(color).frame(width: 5, height: 5) }
}

/// Square day cell: today on brand-soft, selected with a 2 pt brand ring; solar 17 semibold (20/24 in
/// wider cells), lunar 11 (12/14); dots 5 pt, 2 pt after the number; days of other months faded.
private struct DayCell: View {
    let cell: Cell
    let selected: Bool
    let isToday: Bool
    let inMonth: Bool
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            GeometryReader { g in
                content(width: g.size.width).frame(width: g.size.width, height: g.size.height)
            }
            .aspectRatio(1, contentMode: .fit)
            .background(RoundedRectangle(cornerRadius: 12).fill(isToday ? c.brandSoft : .clear))
            .overlay { if selected { RoundedRectangle(cornerRadius: 12).strokeBorder(c.brand, lineWidth: 2) } }
            .opacity(inMonth ? 1 : 0.35)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(cell.info == nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityIdentifier("day-\(cell.date.iso)")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private var accessibilityText: String {
        var s = "\(cell.date.day) tháng \(cell.date.month)"
        if let l = cell.info?.lunar { s += ", âm lịch \(l.day)/\(l.month)\(l.leap ? " nhuận" : "")" }
        return s
    }

    @ViewBuilder private func content(width w: CGFloat) -> some View {
        let solarSize: CGFloat = w >= 88 ? 24 : w >= 64 ? 20 : 17
        let lunarSize: CGFloat = w >= 88 ? 14 : w >= 64 ? 12 : 11
        VStack(spacing: 2) {
            Text("\(cell.date.day)")
                .font(AppFont.sans(solarSize, isToday || selected ? .bold : .semibold))
                .foregroundStyle(isToday ? c.brand : cell.date.isSunday ? c.lunar : c.fg)
                .frame(height: solarSize)
                .overlay(alignment: .trailing) {
                    if let dot = cell.dot { Dot(color: c.event(dot)).offset(x: 7) }
                }
            if let info = cell.info {
                let special = info.lunar.day == 1 || info.lunar.day == 15 || info.holiday != nil
                let small = SmallDate.lunar(info.lunar, selected: selected)
                let hasDots = info.rating != .neutral || cell.clash
                let lunarText = SmallDateText(date: small, size: lunarSize, weight: special ? .bold : .medium,
                                              color: special ? c.lunar : c.fg3)
                    .frame(height: lunarSize)
                let dots = VStack(spacing: 1) {
                    if info.rating == .good { Dot(color: c.good) }
                    if info.rating == .bad { Dot(color: c.bad.opacity(0.7)) }
                    if cell.clash { Dot(color: c.clash) }
                }
                if small.month != nil && hasDots {
                    // "17/6ⁿ" is too wide to hang the dots off a centred number: text and dots
                    // centred together (as Android and the web).
                    HStack(spacing: 2) { lunarText; dots }
                } else {
                    lunarText.overlay(alignment: .trailing) { if hasDots { dots.offset(x: 7) } }
                }
                if w >= 88, let name = info.holiday ?? cell.personal {
                    Text(name).font(AppFont.sans(10, .semibold))
                        .foregroundStyle(info.holiday != nil ? c.lunar : c.event(cell.dot))
                        .lineLimit(1).frame(maxWidth: w - 12)
                }
            }
        }
    }
}

/// A month grid's small date (shared/design/month-grid.md): "16" or "16/10", and after a leap month a
/// small raised "n" (0.7 of the size).
struct SmallDateText: View {
    let date: SmallDate
    let size: CGFloat
    let weight: Font.Weight
    let color: Color

    var body: some View {
        let main = date.month.map { "\(date.day)/\($0)" } ?? "\(date.day)"
        let leap = date.month != nil && date.leap
        Group {
            if leap {
                Text("\(Text(main))\(Text("n").font(AppFont.sans(size * 0.7, weight)).baselineOffset(size * 0.45))")
            } else {
                Text(main)
            }
        }
        .font(AppFont.sans(size, weight))
        .foregroundStyle(color)
        .lineLimit(1)
        .fixedSize()
    }
}

// MARK: - Selected day

private struct SelectedDayPanel: View {
    let now: Date
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let d = state.selectedDate
        let info = state.selectedInfo
        let (icon, label, bg, fg): (String, String, Color, Color) = switch info.rating {
        case .good: ("hand.thumbsup", "Ngày tốt", c.goodSoft, c.good)
        case .bad: ("hand.thumbsdown", "Ngày xấu", c.badSoft, c.bad)
        case .neutral: ("minus", "Ngày bình thường", c.surface2, c.fg2)
        }
        // Everything Hôm nay shows for a day, in short: its holidays, commemorative days and
        // festivals, and "Mồng 1" / "Ngày rằm" when the day has no name.
        let named = info.holiday != nil || !info.observances.isEmpty || !state.personalOn(d).isEmpty
        let dayLabel = named ? nil : lunarDayLabel(info.lunar)
        let l = info.lunar
        Card {
            SectionLabel(text: "Ngày đã chọn · Tuần \(d.weekOfYear)")
            VStack(alignment: .leading, spacing: 0) {
                Text("\(d.weekdayName), \(d.day)/\(d.month)").font(AppFont.sans(20, .heavy)).foregroundStyle(c.fg)
                    .accessibilityIdentifier("selected-day")
                Text("\(Text("Âm lịch ").foregroundStyle(c.fg2))\(Text("\(l.day)/\(l.month)").font(AppFont.sans(14, .bold)).foregroundStyle(c.lunar))\(Text(" · tháng \(lunarMonthNames[l.month - 1])\(l.leap ? " nhuận" : "")").foregroundStyle(c.fg2))")
                    .font(AppFont.sans(14))
            }
            .padding(.top, 4)
            HStack(spacing: 8) {
                ForEach([("Năm", info.canChiYear), ("Tháng", info.canChiMonth), ("Ngày", info.canChiDay)], id: \.0) { name, value in
                    VStack(spacing: 2) {
                        Text(name.uppercased()).font(AppFont.sans(10, .semibold)).tracking(0.5).foregroundStyle(c.fg3)
                        Text(value).font(AppFont.sans(12, .bold)).foregroundStyle(c.fg).lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 4).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
                }
            }
            .padding(.top, 12)
            // The rating with its Trực (as Hôm nay's), then the day's names.
            VStack(spacing: 8) {
                DayNote(icon: icon, title: label, detail: "Trực \(info.truc)", fg: fg, bg: bg)
                if let h = info.holiday { DayNote(icon: "flag.fill", title: h, detail: nil, fg: c.lunar, bg: c.lunarSoft) }
                ForEach(info.observances, id: \.self) { o in
                    let festival = o.kind == .festival
                    DayNote(icon: festival ? "building.columns" : "sparkles", title: o.title,
                            detail: festival ? "Lễ hội" : "Kỷ niệm", fg: c.gold, bg: c.goldSoft)
                }
                if let dayLabel { DayNote(icon: "moon", title: dayLabel, detail: nil, fg: c.lunar, bg: c.surface2) }
                if Zodiac.clashes(state.userChi, info), let chi = state.userChi {
                    DayNote(icon: "exclamationmark.circle", title: "Xung tuổi \(chi)", detail: "nên cân nhắc việc quan trọng", fg: c.clash, bg: c.clashSoft)
                }
            }
            .padding(.top, 12)
        }
        DayEventsCard(day: d, compact: true)
        Activities(info: info)
        GioHoangDaoCard(info: info, hourChi: d == today(now) ? DayFacts.chiOfHour(Calendar.current.component(.hour, from: now)) : nil)
    }
}

/// One line of the day panel: the rating and Trực, a holiday, commemorative day, festival, "Mồng 1"
/// or a zodiac clash.
private struct DayNote: View {
    let icon: String
    let title: String
    let detail: String?
    let fg: Color
    let bg: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon).font(.system(size: 13, weight: .medium)).foregroundStyle(fg)
            Group {
                if let detail {
                    Text("\(Text(title))\(Text(" · \(detail)").font(AppFont.sans(14, .medium)).foregroundStyle(fg.opacity(0.75)))")
                } else {
                    Text(title)
                }
            }
            .font(AppFont.sans(14, .semibold))
            .foregroundStyle(fg)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 12).fill(bg))
    }
}

// MARK: - Month / year picker

private let minYear = 1900
private let maxYear = 2099
private func pageStartOf(_ year: Int) -> Int { minYear + (year - minYear) / 12 * 12 }

/// "Chọn tháng" (Android's MonthYearPickerSheet): the 12 months of a year; tapping the year shows 12
/// years at a time.
private struct MonthYearPicker: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @Environment(\.dismiss) private var dismiss
    @State private var yearMode = false
    @State private var viewYear: Int
    @State private var pageStart: Int
    /// The sheet is as tall as its content (Android's bottom sheet), not half the screen.
    @State private var height: CGFloat = 400

    init(shown: SolarDate) {
        _viewYear = State(initialValue: shown.year)
        _pageStart = State(initialValue: pageStartOf(shown.year))
    }

    var body: some View {
        let shown = state.shownMonth
        let t = today()
        let pageEnd = min(pageStart + 11, maxYear)
        let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)
        VStack(spacing: 16) {
            HStack(spacing: 8) {
                if yearMode {
                    NavHeading(title: "\(String(pageStart)) – \(String(pageEnd))", canPrev: pageStart > minYear, canNext: pageStart + 12 <= maxYear,
                               prevLabel: "12 năm trước", nextLabel: "12 năm sau",
                               onPrev: { pageStart -= 12 }, onNext: { pageStart += 12 }, onTitle: { yearMode = false })
                } else {
                    NavHeading(title: String(viewYear), canPrev: viewYear > minYear, canNext: viewYear < maxYear,
                               prevLabel: "Năm trước", nextLabel: "Năm sau",
                               onPrev: { viewYear -= 1 }, onNext: { viewYear += 1 },
                               onTitle: { pageStart = pageStartOf(viewYear); yearMode = true })
                }
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 15, weight: .semibold)).foregroundStyle(c.fg2)
                        .frame(width: 40, height: 40).background(Circle().fill(c.surface2))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Đóng")
            }
            LazyVGrid(columns: columns, spacing: 10) {
                if yearMode {
                    ForEach(pageStart..<pageStart + 12, id: \.self) { y in
                        GridCell(text: String(y), active: y == viewYear, current: y == t.year) { viewYear = y; yearMode = false }
                            .opacity(y > maxYear ? 0 : 1)
                            .disabled(y > maxYear)
                    }
                } else {
                    ForEach(1...12, id: \.self) { m in
                        GridCell(text: "Tháng \(m)", active: viewYear == shown.year && m == shown.month,
                                 current: viewYear == t.year && m == t.month) {
                            state.showMonth(viewYear, m)
                            dismiss()
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 24)
        .foregroundStyle(c.fg)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.height(height)])
        .presentationDragIndicator(.visible)
        .presentationBackground(c.surface)
        .presentationCornerRadius(28)
    }
}

/// "‹ title ▾ ›" heading of the month/year, Đổi ngày and birth date pickers; no ▾ when the title
/// does nothing ([onTitle] nil).
struct NavHeading: View {
    let title: String
    let canPrev: Bool
    let canNext: Bool
    let prevLabel: String
    let nextLabel: String
    let onPrev: () -> Void
    let onNext: () -> Void
    let onTitle: (() -> Void)?
    @Environment(\.colors) private var c

    var body: some View {
        HStack(spacing: 4) {
            IconBtn(icon: "chevron.left", label: prevLabel) { if canPrev { onPrev() } }.opacity(canPrev ? 1 : 0.3)
                .accessibilityIdentifier("nav-prev")
            Button { onTitle?() } label: {
                HStack(spacing: 4) {
                    Text(title).font(AppFont.sans(18, .heavy)).tracking(-0.18).lineLimit(1).foregroundStyle(c.fg)
                    if onTitle != nil {
                        Image(systemName: "chevron.down").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.fg3)
                    }
                }
                .frame(maxWidth: .infinity).padding(.vertical, 6).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(onTitle == nil)
            IconBtn(icon: "chevron.right", label: nextLabel) { if canNext { onNext() } }.opacity(canNext ? 1 : 0.3)
                .accessibilityIdentifier("nav-next")
        }
    }
}

/// A month or year of a picker grid: brand when chosen, a brand ring on the current one.
struct GridCell: View {
    let text: String
    let active: Bool
    let current: Bool
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            Text(text).font(AppFont.sans(15, .semibold)).multilineTextAlignment(.center)
                .foregroundStyle(active ? c.onBrand : current ? c.brand : c.fg)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(RoundedRectangle(cornerRadius: 16).fill(active ? c.brand : c.surface2))
                .overlay { if !active && current { RoundedRectangle(cornerRadius: 16).strokeBorder(c.brand, lineWidth: 2) } }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}
