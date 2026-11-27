import SwiftUI
import UIKit
import LunarCore

/// Lunar month names as printed on tear-off calendars ("Tháng Giêng" … "Tháng Chạp").
let lunarMonthNames = ["Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười Một", "Chạp"]

private let ringStops: [Gradient.Stop] = [
    .init(color: Color(hex: "#7A5512"), location: 0), .init(color: Color(hex: "#E9CF8A"), location: 0.45),
    .init(color: Color(hex: "#FFF3CF"), location: 0.55), .init(color: Color(hex: "#A77D26"), location: 1),
]

/// The Today page's date as one sheet of a Vietnamese tear-off wall calendar (lịch bóc), as
/// Android's TearOffSheet and the web's components/TearOffSheet.tsx: a lacquer sheet hung on two
/// brass rings, framed by a double gold hairline, the solar day in large gold-foil serif (Sundays
/// and holidays get a gold seal on the weekday), the lunar date in a gold "moon" beside the can chi.
///
/// The paper turns with iOS's own page curl (UIPageViewController): drag it, or tap the arrows.
/// Android draws its curl itself (SheetPeel); the plan chose the system curl on iOS.
struct TearOffSheet: View {
    let now: Date
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let d = state.selectedDate
        GeometryReader { geo in
            let width = geo.size.width
            VStack(spacing: 0) {
                SheetBinding(day: d)
                // A copy of the paper sizes the page curl (every day has the same height); the
                // curl draws the real sheets over it.
                SheetPage(day: d, now: now, width: width, onPrev: {}, onNext: {})
                    .hidden()
                    .overlay {
                        PageCurl(day: d, now: now, width: width, colors: c, personal: state.lunarEvents,
                                 select: { state.selectDate($0) })
                    }
            }
            .overlay(alignment: .topLeading) {
                // Two brass rings over the binding and the paper (18 pt into the paper).
                Ring().offset(x: width * 0.26, y: 30)
                Ring().offset(x: width * 0.74 - 9, y: 30)
            }
            .background(GeometryReader { inner in Color.clear.preference(key: SheetHeight.self, value: inner.size.height) })
        }
        .frame(height: sheetHeight)
        .onPreferenceChange(SheetHeight.self) { sheetHeight = $0 }
        .webShadow(c.shadowTint, float: true)
    }

    @State private var sheetHeight: CGFloat = 520
}

private struct SheetHeight: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

/// The darker lacquer strip edged in gold: theme button, year, back to today.
private struct SheetBinding: View {
    let day: SolarDate
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let white = Color.white.opacity(0.85)
        let (icon, label) = switch state.themePref {
        case .system: ("circle.lefthalf.filled", "Giao diện: theo hệ thống")
        case .light: ("sun.max", "Giao diện: sáng")
        case .dark: ("moon", "Giao diện: tối")
        }
        HStack(spacing: 0) {
            IconBtn(icon: icon, label: label, tint: white) { state.cycleTheme() }
            Text(verbatim: "\(day.year)")
                .font(AppFont.sans(15, .bold)).tracking(15 * 0.3)
                .foregroundStyle(c.heroAccent)
                .frame(maxWidth: .infinity)
            if day == today() {
                Color.clear.frame(width: 40, height: 40)
            } else {
                IconBtn(icon: "arrow.uturn.backward", label: "Về hôm nay", tint: white) { state.selectDate(today()) }
            }
        }
        .padding(.horizontal, 6)
        .frame(height: 48)
        .background {
            ZStack {
                LinearGradient(colors: [c.heroFrom, c.heroVia, c.heroTo], startPoint: .topLeading, endPoint: .bottomTrailing)
                Color.black.opacity(0.25)
            }
        }
        .overlay(alignment: .bottom) { Rectangle().fill(c.heroAccent.opacity(0.7)).frame(height: 1) }
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 20, topTrailingRadius: 20))
    }
}

private struct Ring: View {
    var body: some View {
        Capsule()
            .fill(LinearGradient(stops: ringStops, startPoint: .leading, endPoint: .trailing))
            .frame(width: 9, height: 30)
            .shadow(color: .black.opacity(0.3), radius: 1.5, x: 0, y: 2)
            .allowsHitTesting(false)
    }
}

/// One sheet of paper: lacquer with its light and moons, the frame and the dates.
struct SheetPage: View {
    let day: SolarDate
    let now: Date
    let width: CGFloat
    var personal: [LunarEvent] = []
    let onPrev: () -> Void
    let onNext: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        let info = DayInfo.of(day)
        let accent = c.heroAccent
        let red = day.isSunday || info.holiday != nil
        // Solar day: 44% of the sheet's width (the web's 44cqw), 80–184.
        let number = min(max(width * 0.44, 80), 184)
        let canChi = [("NĂM", info.canChiYear), ("THÁNG", info.canChiMonth), ("NGÀY", info.canChiDay)]
        let hour = Calendar.current.component(.hour, from: now)
        let minute = Calendar.current.component(.minute, from: now)
        let gap: CGFloat = width >= 352 ? 20 : 16

        VStack(spacing: 0) {
            Text("THÁNG \(day.month) · TUẦN \(day.weekOfYear)")
                .font(AppFont.sans(11, .semibold)).tracking(11 * 0.34)
                .foregroundStyle(accent)
                .frame(maxWidth: .infinity)

            HStack(spacing: 0) {
                IconBtn(icon: "chevron.left", label: "Ngày trước", tint: .white.opacity(0.6), iconSize: 20, action: onPrev)
                Text(verbatim: "\(day.day)")
                    .font(AppFont.serifFigures(number))
                    .tracking(-0.02 * number)
                    .foil(accent)
                    .frame(maxWidth: .infinity)
                    .frame(height: number * 1.05)
                    .accessibilityIdentifier("sheet-day")
                    .accessibilityLabel(day.longLabel)
                IconBtn(icon: "chevron.right", label: "Ngày sau", tint: .white.opacity(0.6), iconSize: 20, action: onNext)
            }

            // Sundays and holidays (red on a paper calendar) get a gold seal on the weekday.
            HStack(spacing: 12) {
                Rectangle().fill(accent.opacity(0.4)).frame(width: 32, height: 1)
                Text(day.weekdayName.uppercased())
                    .font(AppFont.sans(13, .semibold)).tracking(13 * 0.4)
                    .foregroundStyle(red ? c.heroAccentInk : .white)
                    .padding(.horizontal, 12).padding(.vertical, 4)
                    .background(Capsule().fill(red ? accent : .clear))
                Rectangle().fill(accent.opacity(0.4)).frame(width: 32, height: 1)
            }

            // A day without a holiday is named after the user's own event first. One line, so
            // every sheet has the same height (the page curl's pages share one size).
            Text(info.holiday ?? PersonalEvents.on(personal, day).first?.title ?? info.observance ?? lunarDayLabel(info.lunar) ?? " ")
                .font(AppFont.serifItalic(16))
                .foregroundStyle(accent)
                .lineLimit(1).minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, minHeight: 24)
                .padding(.top, 8)

            // Ornament: hairlines and a small gold diamond.
            HStack(spacing: 12) {
                LinearGradient(colors: [.clear, accent.opacity(0.55)], startPoint: .leading, endPoint: .trailing).frame(height: 1)
                Rectangle().fill(accent.opacity(0.8)).frame(width: 6, height: 6).rotationEffect(.degrees(45))
                LinearGradient(colors: [accent.opacity(0.55), .clear], startPoint: .leading, endPoint: .trailing).frame(height: 1)
            }
            .padding(.top, 12)

            // Lunar date in a gold moon, a gold rule, the can chi beside it.
            HStack(spacing: 0) {
                Moon(info: info)
                Spacer().frame(width: gap)
                Rectangle().fill(accent.opacity(0.35)).frame(width: 1, height: 80)
                Spacer().frame(width: gap)
                CanChiRows(rows: canChi).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.top, 16)

            Text(String(format: "GIỜ %@ · %02d:%02d", CalendarData.calendar.chi[DayFacts.chiOfHour(hour)].uppercased(), hour, minute))
                .font(AppFont.sans(10, .semibold)).tracking(10 * 0.3)
                .foregroundStyle(.white.opacity(0.55))
                .frame(maxWidth: .infinity)
                .padding(.top, 20)
        }
        .padding(EdgeInsets(top: 24, leading: 16, bottom: 16, trailing: 16))
        // Double gold hairline frame, as on printed calendar sheets.
        .overlay(RoundedRectangle(cornerRadius: 11).stroke(accent.opacity(0.25), lineWidth: 1))
        .padding(3)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(accent.opacity(0.45), lineWidth: 1))
        .padding(12)
        .background { Paper(accent: accent) }
        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 20, bottomTrailingRadius: 20))
    }
}

/// The lacquer: hero gradient, a soft light at 50% 25% and two faint moons (the web's paper).
private struct Paper: View {
    let accent: Color
    @Environment(\.colors) private var c

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            ZStack(alignment: .topLeading) {
                LinearGradient(colors: [c.heroFrom, c.heroVia, c.heroTo], startPoint: .topLeading, endPoint: .bottomTrailing)
                RadialGradient(stops: [.init(color: .white.opacity(0.10), location: 0), .init(color: .clear, location: 0.6)],
                               center: UnitPoint(x: 0.5, y: 0.25), startRadius: 0, endRadius: hypot(w * 0.5, h * 0.75))
                Circle().fill(accent.opacity(0.06)).frame(width: 224, height: 224).offset(x: w + 64 - 224, y: -8)
                Circle().fill(accent.opacity(0.05)).frame(width: 128, height: 128).offset(x: -40, y: h - 96 - 128)
            }
        }
    }
}

/// The lunar day in a 104 pt gold ring, "Âm lịch" above and "tháng Mười" below.
private struct Moon: View {
    let info: DayInfo
    @Environment(\.colors) private var c

    var body: some View {
        let accent = c.heroAccent
        let name = lunarMonthNames[info.lunar.month - 1]
        VStack(spacing: 0) {
            // A leap month says so on the top line, so the bottom line stays inside the ring.
            Text(info.lunar.leap ? "NHUẬN" : "ÂM LỊCH")
                .font(.custom("BeVietnamPro-SemiBold", fixedSize: 9)).tracking(9 * 0.28)
                .foregroundStyle(accent.opacity(0.85))
            Text(verbatim: "\(info.lunar.day)")
                .font(AppFont.serifFigures(41.6))
                .foil(accent)
                .frame(height: 42)
            Text("tháng \(name)")
                .font(.custom("PlayfairDisplay-Italic", fixedSize: name.count > 5 ? 10.5 : 12))
                .foregroundStyle(.white.opacity(0.7))
                .lineLimit(1)
                .padding(.top, 2)
        }
        .frame(width: 104, height: 104)
        .background(Circle().fill(.black.opacity(0.12)))
        .overlay(Circle().inset(by: 3).stroke(.black.opacity(0.08), lineWidth: 4))
        .overlay(Circle().inset(by: 5.5).stroke(accent.opacity(0.28), lineWidth: 1))
        .overlay(Circle().stroke(accent.opacity(0.55), lineWidth: 1))
    }
}

/// Three rows: label (at least 44 pt, the web's w-11) and value on one baseline; when that does
/// not fit, each label smaller above its value.
private struct CanChiRows: View {
    let rows: [(String, String)]

    var body: some View {
        ViewThatFits(in: .horizontal) {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(rows, id: \.0) { label, v in
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Text(label).font(AppFont.sans(9, .semibold)).tracking(9 * 0.24)
                            .foregroundStyle(.white.opacity(0.55)).lineLimit(1)
                            .frame(minWidth: 44, alignment: .leading)
                        // The longest of the 60 can chi sizes the column, so the layout does not
                        // change from day to day (as the web).
                        Text("Nhâm Thân").font(AppFont.serif(16.8, .semibold)).lineLimit(1).hidden()
                            .overlay(alignment: .leading) { value(v) }
                    }
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                ForEach(rows, id: \.0) { label, v in
                    VStack(alignment: .leading, spacing: 0) {
                        Text(label).font(AppFont.sans(7.5, .semibold)).tracking(7.5 * 0.24)
                            .foregroundStyle(.white.opacity(0.55)).lineLimit(1)
                        value(v)
                    }
                }
            }
        }
    }

    private func value(_ text: String) -> some View {
        Text(text).font(AppFont.serif(16.8, .semibold)).foregroundStyle(.white).lineLimit(1).fixedSize()
    }
}

extension View {
    /// Gold-foil print: a light-to-deep gradient of the palette's hero accent, with a 2 pt drop.
    func foil(_ accent: Color) -> some View {
        foregroundStyle(LinearGradient(stops: [
            .init(color: Color.white.mix(accent, 0.4), location: 0),
            .init(color: accent, location: 0.48),
            .init(color: Color(hex: "#7A4A08").mix(accent, 0.68), location: 1),
        ], startPoint: .top, endPoint: .bottom))
        .shadow(color: .black.opacity(0.22), radius: 0, x: 0, y: 2)
    }
}

// MARK: - Page curl

/// Stands in front of a pan's delegate: turns down a drag that is more vertical than sideways
/// and passes every other question to the original delegate.
final class SidewaysOnly: NSObject, UIGestureRecognizerDelegate {
    private weak var original: UIGestureRecognizerDelegate?

    init(_ original: UIGestureRecognizerDelegate?) { self.original = original }

    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        if let pan = g as? UIPanGestureRecognizer {
            // The distance moved so far decides; the speed when it is still zero.
            let t = pan.translation(in: pan.view), v = pan.velocity(in: pan.view)
            let upright = t == .zero ? abs(v.y) > abs(v.x) : abs(t.y) > abs(t.x)
            if upright { return false }
        }
        return original?.gestureRecognizerShouldBegin?(g) ?? true
    }

    override func responds(to selector: Selector!) -> Bool {
        super.responds(to: selector) || (original?.responds(to: selector) ?? false)
    }

    override func forwardingTarget(for selector: Selector!) -> Any? { original }
}

/// A page of the curl: one day's sheet in a hosting controller.
private final class PageHost: UIHostingController<AnyView> {
    let day: SolarDate
    init(day: SolarDate, view: AnyView) {
        self.day = day
        super.init(rootView: view)
        self.view.backgroundColor = .clear
    }

    @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
}

/// UIPageViewController with .pageCurl: dragging the sheet curls it to the next or previous day;
/// a day chosen elsewhere (arrows, back to today) turns the page too.
private struct PageCurl: UIViewControllerRepresentable {
    let day: SolarDate
    let now: Date
    let width: CGFloat
    let colors: LichAmColors
    let personal: [LunarEvent]
    let select: (SolarDate) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIPageViewController {
        let pvc = UIPageViewController(transitionStyle: .pageCurl, navigationOrientation: .horizontal)
        pvc.isDoubleSided = false
        pvc.view.backgroundColor = .clear
        pvc.dataSource = context.coordinator
        pvc.delegate = context.coordinator
        // Only dragging turns the page: the edge taps would fight the arrows on the sheet.
        pvc.gestureRecognizers.filter { $0 is UITapGestureRecognizer }.forEach { $0.isEnabled = false }
        // Only sideways drags curl the page; a drag that starts upright scrolls the Today page. The
        // curl's pan took vertical drags too (UI test 2026-11-27: the page did not move), so its
        // delegate (the page view controller) gets a front that turns those down.
        for pan in pvc.gestureRecognizers.compactMap({ $0 as? UIPanGestureRecognizer }) {
            let front = SidewaysOnly(pan.delegate)
            context.coordinator.fronts.append(front)
            pan.delegate = front
        }
        pvc.setViewControllers([context.coordinator.page(day)], direction: .forward, animated: false)
        context.coordinator.shown = day
        return pvc
    }

    func updateUIViewController(_ pvc: UIPageViewController, context: Context) {
        let co = context.coordinator
        co.parent = self
        if co.shown != day {
            let forward = day > co.shown
            co.shown = day
            let animate = !UIAccessibility.isReduceMotionEnabled && abs(day.jd - (co.visible(pvc)?.jd ?? day.jd)) <= 31
            pvc.setViewControllers([co.page(day)], direction: forward ? .forward : .reverse, animated: animate)
        } else if let host = pvc.viewControllers?.first as? PageHost {
            // Clock, theme or events changed: redraw the sheet on show.
            host.rootView = co.content(host.day)
        }
    }

    final class Coordinator: NSObject, UIPageViewControllerDataSource, UIPageViewControllerDelegate {
        /// The delegate fronts of the curl's pans (a gesture recognizer holds its delegate weakly).
        var fronts: [SidewaysOnly] = []

        var parent: PageCurl
        var shown: SolarDate

        init(_ parent: PageCurl) {
            self.parent = parent
            shown = parent.day
        }

        func visible(_ pvc: UIPageViewController) -> SolarDate? { (pvc.viewControllers?.first as? PageHost)?.day }

        func content(_ d: SolarDate) -> AnyView {
            let p = parent
            return AnyView(
                SheetPage(day: d, now: p.now, width: p.width, personal: p.personal,
                          onPrev: { p.select(d.plusDays(-1)) }, onNext: { p.select(d.plusDays(1)) })
                    .environment(\.colors, p.colors)
                    .frame(maxHeight: .infinity, alignment: .top)
            )
        }

        func page(_ d: SolarDate) -> PageHost { PageHost(day: d, view: content(d)) }

        func pageViewController(_ pvc: UIPageViewController, viewControllerBefore vc: UIViewController) -> UIViewController? {
            guard let d = (vc as? PageHost)?.day.plusDays(-1), d.isInRange else { return nil }
            return page(d)
        }

        func pageViewController(_ pvc: UIPageViewController, viewControllerAfter vc: UIViewController) -> UIViewController? {
            guard let d = (vc as? PageHost)?.day.plusDays(1), d.isInRange else { return nil }
            return page(d)
        }

        func pageViewController(_ pvc: UIPageViewController, didFinishAnimating finished: Bool,
                                previousViewControllers: [UIViewController], transitionCompleted completed: Bool) {
            guard completed, let d = visible(pvc) else { return }
            shown = d
            parent.select(d)
        }
    }
}
