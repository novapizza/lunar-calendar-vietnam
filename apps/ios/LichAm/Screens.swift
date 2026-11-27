import SwiftUI
import LunarCore

// Placeholders of the four tabs (step 3 of the iOS plan: the app's frame). Each reads LunarCore
// already, so the frame shows the data is wired; steps 4 to 8 replace them with the real screens.

private let weekdayNames = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"]

private struct ScreenTitle: View {
    let title: String
    let subtitle: String
    @Environment(\.colors) private var c

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(AppFont.sans(22, .heavy))
            Text(subtitle).font(AppFont.sans(13)).foregroundStyle(c.fg2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }
}

struct TodayScreen: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let info = state.selectedInfo
        VStack(spacing: 16) {
            ScreenTitle(title: "Hôm nay", subtitle: "\(weekdayNames[info.solar.weekdayIndex]), \(info.solar.day)/\(info.solar.month)/\(info.solar.year)")
            VStack(spacing: 8) {
                Text("\(info.solar.day)")
                    .font(AppFont.serif(96, .bold))
                    .foregroundStyle(info.solar.isSunday || info.holiday != nil ? c.lunar : c.fg)
                Text("Ngày \(info.lunar.day) tháng \(info.lunar.month)\(info.lunar.leap ? " nhuận" : "") năm \(info.canChiYear)")
                    .font(AppFont.serifItalic(18))
                    .foregroundStyle(c.fg2)
                Text("Ngày \(info.canChiDay) · Tháng \(info.canChiMonth) · Trực \(info.truc)")
                    .font(AppFont.sans(14, .medium))
                if let h = info.holiday ?? info.observance { Text(h).font(AppFont.sans(14, .semibold)).foregroundStyle(c.lunar) }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 20).fill(c.surface))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(c.line))
            .padding(.horizontal, 16)
            Spacer()
        }
    }
}

struct CalendarScreen: View {
    @Environment(AppState.self) private var state

    var body: some View {
        let m = state.shownMonth
        VStack {
            ScreenTitle(title: "Tháng \(m.month) năm \(m.year)", subtitle: "\(monthGrid(m.year, m.month).count) ô lưới tháng (bước 5)")
            Spacer()
        }
    }
}

struct EventsScreen: View {
    @Environment(AppState.self) private var state

    var body: some View {
        VStack {
            ScreenTitle(title: "Sự kiện", subtitle: "\(state.lunarEvents.count) sự kiện của bạn (bước 6)")
            Spacer()
        }
    }
}

struct ToolsScreen: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ScreenTitle(title: "Tiện ích", subtitle: "Đổi ngày, tìm ngày tốt, xuất .ics (bước 8)")
            Segmented(title: "Màu chủ đạo", options: [(AccentPref.banMenh, "Bản mệnh"), (.tuongSinh, "Tương sinh"), (.macDinh, "Mặc định")],
                      selected: state.accentPref) { state.updateAccent($0) }
            Segmented(title: "Giao diện", options: [(ThemePref.system, "Theo máy"), (.light, "Sáng"), (.dark, "Tối")],
                      selected: state.themePref) { state.updateTheme($0) }
            Spacer()
        }
    }
}

/// A row of choices on surface-2 (Android's "Giao diện" picker).
private struct Segmented<T: Hashable>: View {
    let title: String
    let options: [(T, String)]
    let selected: T
    let pick: (T) -> Void
    @Environment(\.colors) private var c

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(AppFont.sans(14, .semibold))
            HStack(spacing: 4) {
                ForEach(options, id: \.0) { value, label in
                    let active = value == selected
                    Button { pick(value) } label: {
                        Text(label)
                            .font(AppFont.sans(13, active ? .bold : .medium))
                            .foregroundStyle(active ? c.brand : c.fg2)
                            .frame(maxWidth: .infinity, minHeight: 36)
                            .background(RoundedRectangle(cornerRadius: 9).fill(active ? c.surface : .clear))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(4)
            .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
        }
        .padding(.horizontal, 16)
    }
}
