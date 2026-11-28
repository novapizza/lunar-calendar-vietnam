import SwiftUI
import LunarCore

// Placeholders of the Sự kiện and Tiện ích tabs. Each reads LunarCore already; steps 6 to 8 of the
// iOS plan replace them with the real screens (Hôm nay: TodayScreen.swift, Lịch: CalendarScreen.swift).

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
