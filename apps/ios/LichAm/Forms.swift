import SwiftUI
import LunarCore

// The sheet frame, buttons and fields of Android's ui/Sheets.kt, Components.kt and DatePickerField.kt.

/// The web's Sheet: header (icon tile, title text-lg bold or a custom heading, subtitle, close
/// button on surface-2), the body scrolling under it, an optional footer with a top border.
struct SheetFrame<Content: View>: View {
    let title: String
    var icon: String? = nil
    var subtitle: String? = nil
    var heading: AnyView? = nil
    var footer: AnyView? = nil
    let onClose: () -> Void
    @ViewBuilder let content: Content
    @Environment(\.colors) private var c
    // Heights of the header, the body and the footer: the sheet fits them, as Android's
    // ModalBottomSheet wraps its content, up to the full height (the body then scrolls).
    @State private var headerHeight: CGFloat = 0
    @State private var bodyHeight: CGFloat = 0
    @State private var footerHeight: CGFloat = 0

    private var fitted: CGFloat { headerHeight + bodyHeight + footerHeight }

    var body: some View {
        frame
            .presentationDetents(fitted > 0 ? [.height(fitted)] : [.large])
            .modifier(FittedFormSheet(height: fitted))
    }

    private var frame: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                if let heading {
                    heading.frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    if let icon { IconTile(icon: icon, bg: c.brandSoft, fg: c.brand, size: 40, iconSize: 17, radius: 14) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(AppFont.sans(18, .bold)).foregroundStyle(c.fg).accessibilityAddTraits(.isHeader)
                        if let subtitle { Text(subtitle).font(AppFont.sans(12)).foregroundStyle(c.fg2) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.system(size: 15, weight: .semibold)).foregroundStyle(c.fg2)
                        .frame(width: 40, height: 40).background(Circle().fill(c.surface2))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Đóng")
            }
            .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 12)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { headerHeight = $0 }
            ScrollView {
                content
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20).padding(.bottom, 20)
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { bodyHeight = $0 }
            }
            .scrollDismissesKeyboard(.interactively)
            .scrollBounceBehavior(.basedOnSize)
            if let footer {
                VStack(spacing: 0) {
                    Hairline()
                    footer.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 12)
                }
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { footerHeight = $0 }
            }
        }
        .background(c.surface)
        .foregroundStyle(c.fg)
    }
}

/// iPad (regular width): the sheet is a form sheet, which ignores detents; from iOS 18 it takes the
/// fitted height instead of the fixed form height (the editor's date picker was cut off).
private struct FittedFormSheet: ViewModifier {
    let height: CGFloat

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), UIDevice.current.userInterfaceIdiom == .pad, height > 0 {
            content
                .frame(idealHeight: height)
                .presentationSizing(.form.fitted(horizontal: false, vertical: true))
        } else {
            content
        }
    }
}

/// `.btn` rounded-xl, px-4 py-2.5 (tall: py-3), text-sm semibold; disabled at 40 %.
private struct Btn: View {
    let text: String
    var icon: String? = nil
    let bg: Color
    let fg: Color
    var enabled = true
    var tall = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Image(systemName: icon).font(.system(size: 14, weight: .semibold)) }
                Text(text).font(AppFont.sans(14, .semibold)).lineLimit(1)
            }
            .foregroundStyle(enabled ? fg : fg.opacity(0.6))
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16).padding(.vertical, tall ? 12 : 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(enabled ? bg : bg.opacity(0.4)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct PrimaryButton: View {
    let text: String
    var icon: String? = nil
    var enabled = true
    var tall = false
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View { Btn(text: text, icon: icon, bg: c.brand, fg: c.onBrand, enabled: enabled, tall: tall, action: action) }
}

struct GhostButton: View {
    let text: String
    var tall = false
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View { Btn(text: text, bg: c.surface2, fg: c.fg, tall: tall, action: action) }
}

struct FieldLabel: View {
    let text: String
    @Environment(\.colors) private var c

    var body: some View {
        Text(text).font(AppFont.sans(12, .semibold)).foregroundStyle(c.fg2).padding(.bottom, 6)
    }
}

struct Hint: View {
    let text: String
    var color: Color? = nil
    @Environment(\.colors) private var c

    var body: some View {
        Text(text).font(AppFont.sans(12)).foregroundStyle(color ?? c.fg2).padding(.top, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// `.field` as a select: rounded-xl, surface-2, px-3.5 py-2.5, the value 16 semibold; the choices in
/// the system's menu.
struct SelectField<T: Hashable>: View {
    let value: T
    let options: [T]
    let label: (T) -> String
    let onSelect: (T) -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Menu {
            Picker(selection: Binding(get: { value }, set: { onSelect($0) })) {
                ForEach(options, id: \.self) { Text(label($0)).tag($0) }
            } label: { EmptyView() }
        } label: {
            HStack {
                Text(label(value)).font(AppFont.sans(16, .semibold)).foregroundStyle(c.fg).lineLimit(1)
                Spacer(minLength: 4)
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(c.fg3)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// `.field`: surface-2, rounded-xl, 16 pt text (one line, or [minLines] growing).
struct FormTextField: View {
    let placeholder: String
    @Binding var text: String
    var minLines = 1
    @Environment(\.colors) private var c

    var body: some View {
        Group {
            if minLines > 1 {
                TextField("", text: $text, prompt: Text(placeholder).foregroundStyle(c.fg3), axis: .vertical)
                    .lineLimit(minLines...8)
            } else {
                TextField("", text: $text, prompt: Text(placeholder).foregroundStyle(c.fg3))
            }
        }
        .font(AppFont.sans(16))
        .foregroundStyle(c.fg)
        .tint(c.brand)
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
        .accessibilityLabel(placeholder)
    }
}

/// Two or three choices side by side on surface-2 (Android's Segmented).
struct SegmentedChoice<T: Hashable>: View {
    let options: [(T, String)]
    let selected: T
    let pick: (T) -> Void
    @Environment(\.colors) private var c

    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.0) { value, label in
                let active = value == selected
                Button { pick(value) } label: {
                    Text(label)
                        .font(AppFont.sans(13, active ? .bold : .medium))
                        .foregroundStyle(active ? c.brand : c.fg2)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .background(RoundedRectangle(cornerRadius: 9).fill(active ? c.surface : .clear))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(active ? .isSelected : [])
            }
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
    }
}

// MARK: - Date field

private let pickMinYear = 1900
private let pickMaxYear = 2099
private let pickPage = 12

/// A date as one line ("Thứ Hai, 23/11/2026 · 15/10 âm"); tapping it opens a month grid under it
/// with the lunar day under each day (Android's DatePickerField); the title zooms out to months and
/// years, "Hôm nay" jumps back. Picking a day closes it.
struct DatePickerField: View {
    let value: SolarDate
    let label: String
    var min: SolarDate? = nil
    let onPick: (SolarDate) -> Void
    @Environment(\.colors) private var c
    @State private var open = false
    @State private var mode = 2 // 0 years, 1 months, 2 days
    @State private var viewYear = 2026
    @State private var viewMonth = 1
    @State private var pageStart = 2016

    private func allowed(_ d: SolarDate) -> Bool { d.isInRange && (min.map { d >= $0 } ?? true) }
    private func pick(_ d: SolarDate) { onPick(d); open = false }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            FieldLabel(text: label)
            Button {
                if !open { viewYear = value.year; viewMonth = value.month; mode = 2 }
                open.toggle()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "calendar").font(.system(size: 16, weight: .medium)).foregroundStyle(c.brand)
                    Text(lineText).font(AppFont.sans(15, .semibold)).foregroundStyle(c.fg).lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.fg3)
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(label): \(lineText)")
            if open { picker.padding(.top, 8) }
        }
    }

    private var lineText: String {
        guard value.isInRange else { return value.longLabel }
        let l = LunarCalendar.solarToLunar(value)
        return "\(value.longLabel) · \(l.day)/\(l.month)\(l.leap ? " nhuận" : "") âm"
    }

    private var picker: some View {
        let t = today()
        let title = switch mode {
        case 0: "\(String(pageStart)) – \(String(pageStart + pickPage - 1))"
        case 1: String(viewYear)
        default: "Tháng \(viewMonth), \(String(viewYear))"
        }
        return VStack(spacing: 4) {
            HStack(spacing: 0) {
                IconBtn(icon: "chevron.left", label: "Trước") { step(-1) }
                Button {
                    if mode == 2 { mode = 1 } else if mode == 1 { pageStart = viewYear - viewYear % pickPage; mode = 0 }
                } label: {
                    HStack(spacing: 4) {
                        Text(title).font(AppFont.sans(15, .bold)).foregroundStyle(c.fg)
                        if mode != 0 { Image(systemName: "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(c.fg3) }
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 8).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(mode == 0)
                IconBtn(icon: "chevron.right", label: "Sau") { step(1) }
            }
            switch mode {
            case 0:
                ChoiceGrid(items: Array(pageStart..<pageStart + pickPage), label: { String($0) }, selected: { $0 == value.year },
                           enabled: { (pickMinYear...pickMaxYear).contains($0) }) { viewYear = $0; mode = 1 }
            case 1:
                ChoiceGrid(items: Array(1...12), label: { "Th \($0)" }, selected: { viewYear == value.year && $0 == value.month },
                           enabled: { _ in true }) { viewMonth = $0; mode = 2 }
            default:
                dayGrid(today: t)
            }
            HStack {
                Text(mode == 2 ? "Số nhỏ là ngày âm" : mode == 1 ? "Chọn tháng" : "Chọn năm")
                    .font(AppFont.sans(11)).foregroundStyle(c.fg3)
                Spacer()
                Button { pick(t) } label: {
                    Text("Hôm nay").font(AppFont.sans(13, .semibold)).foregroundStyle(c.brand)
                        .padding(.horizontal, 8).padding(.vertical, 6)
                }
                .buttonStyle(.plain)
                .disabled(!allowed(t))
                .opacity(allowed(t) ? 1 : 0.3)
            }
            .padding(.leading, 4)
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 16).fill(c.surface2))
    }

    private func step(_ dir: Int) {
        switch mode {
        case 0: pageStart = Swift.max(pickMinYear - pickMinYear % pickPage, Swift.min(pickMaxYear - pickMaxYear % pickPage, pageStart + dir * pickPage))
        case 1: viewYear = Swift.max(pickMinYear, Swift.min(pickMaxYear, viewYear + dir))
        default:
            let m = SolarDate(viewYear, viewMonth, 1).plusMonths(dir)
            if (pickMinYear...pickMaxYear).contains(m.year) { viewYear = m.year; viewMonth = m.month }
        }
    }

    private func dayGrid(today t: SolarDate) -> some View {
        let first = SolarDate(viewYear, viewMonth, 1)
        let cells: [SolarDate?] = Array(repeating: nil, count: first.weekdayIndex)
            + (1...daysInMonth(viewYear, viewMonth)).map { SolarDate(viewYear, viewMonth, $0) }
        let rows = stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<Swift.min($0 + 7, cells.count)]) }
        return VStack(spacing: 2) {
            HStack(spacing: 0) {
                ForEach(CalendarData.calendar.weekDaysShort, id: \.self) { w in
                    Text(w).font(AppFont.sans(11, .semibold)).foregroundStyle(w == "CN" ? c.lunar : c.fg3).frame(maxWidth: .infinity)
                }
            }
            .padding(.vertical, 4)
            ForEach(rows.indices, id: \.self) { r in
                HStack(spacing: 2) {
                    ForEach(0..<7, id: \.self) { i in
                        if i < rows[r].count, let d = rows[r][i] {
                            PickDay(d: d, selected: d == value, isToday: d == t, enabled: allowed(d)) { pick(d) }
                        } else {
                            Color.clear.frame(maxWidth: .infinity, minHeight: 46, maxHeight: 46)
                        }
                    }
                }
            }
        }
    }
}

private struct PickDay: View {
    let d: SolarDate
    let selected: Bool
    let isToday: Bool
    let enabled: Bool
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        let l = LunarCalendar.solarToLunar(d)
        let special = l.day == 1 || l.day == 15
        Button(action: action) {
            VStack(spacing: 1) {
                Text("\(d.day)").font(AppFont.sans(15, .semibold)).foregroundStyle(selected ? c.onBrand : isToday ? c.brand : c.fg)
                SmallDateText(date: SmallDate.lunar(l, selected: selected), size: 10, weight: special ? .semibold : .regular,
                              color: selected ? c.onBrand.opacity(0.8) : special ? c.lunar : c.fg3)
            }
            .frame(maxWidth: .infinity, minHeight: 46, maxHeight: 46)
            .background(RoundedRectangle(cornerRadius: 12).fill(selected ? c.brand : isToday ? c.brandSoft : .clear))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Ngày \(d.day) tháng \(d.month) năm \(String(d.year)), âm lịch \(l.day) tháng \(l.month)")
        .accessibilityIdentifier("pick-\(d.iso)")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// Four columns of choices (years or months).
private struct ChoiceGrid<T: Hashable>: View {
    let items: [T]
    let label: (T) -> String
    let selected: (T) -> Bool
    let enabled: (T) -> Bool
    let onPick: (T) -> Void
    @Environment(\.colors) private var c

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 4), spacing: 6) {
            ForEach(items, id: \.self) { item in
                let on = selected(item)
                let ok = enabled(item)
                Button { onPick(item) } label: {
                    Text(label(item)).font(AppFont.sans(14, .semibold)).foregroundStyle(on ? c.onBrand : c.fg)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(RoundedRectangle(cornerRadius: 12).fill(on ? c.brand : c.surface))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!ok)
                .opacity(ok ? 1 : 0)
            }
        }
        .padding(.vertical, 4)
    }
}
