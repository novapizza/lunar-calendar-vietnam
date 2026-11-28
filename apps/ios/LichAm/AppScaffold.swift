import SwiftUI

private struct NavItem {
    let tab: Tab
    let label: String
    let icon: String
    let iconOn: String
}

private let nav = [
    NavItem(tab: .today, label: "Hôm nay", icon: "calendar", iconOn: "calendar"),
    NavItem(tab: .calendar, label: "Lịch", icon: "square.grid.2x2", iconOn: "square.grid.2x2.fill"),
    NavItem(tab: .events, label: "Sự kiện", icon: "sparkles", iconOn: "sparkles"),
]

/// The bottom tab bar of Android's AppScaffold: three labelled tabs plus the logo, which opens
/// Tiện ích. (The tablet rail comes with the iPad layout.)
struct AppScaffold: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch state.tab {
                case .today: TodayScreen()
                case .calendar: CalendarScreen()
                case .events: EventsScreen()
                case .tools: ToolsScreen()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            // Scrolled content stops under the status bar (as Android's statusBarsPadding), not behind it.
            .clipped()
            BottomBar()
        }
        .background(c.bg.ignoresSafeArea())
        .foregroundStyle(c.fg)
        // The event sheets (list, detail, editor) in one system sheet; swiping it down closes them all.
        .sheet(isPresented: Binding(get: { state.sheet != nil }, set: { if !$0 { state.closeAllSheets() } })) {
            AppSheets()
                .environment(state)
                .environment(\.colors, c)
                .presentationDragIndicator(.visible)
                .presentationBackground(c.surface)
                .presentationCornerRadius(28)
        }
    }
}

private struct BottomBar: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(c.line).frame(height: 1)
            HStack(spacing: 0) {
                ForEach(nav, id: \.tab) { item in
                    TabButton(item: item, active: state.tab == item.tab) { state.selectTab(item.tab) }
                        .frame(maxWidth: .infinity)
                        .accessibilityIdentifier("tab-\(item.tab.rawValue.lowercased())")
                }
                Button { state.selectTab(.tools) } label: {
                    Logo(active: state.tab == .tools, size: 36)
                        .frame(maxWidth: .infinity, minHeight: 60)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Tiện ích và cài đặt")
            }
        }
        .background(c.surface.ignoresSafeArea(edges: .bottom))
    }
}

private struct TabButton: View {
    let item: NavItem
    let active: Bool
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: active ? item.iconOn : item.icon)
                    .font(.system(size: 18, weight: active ? .semibold : .regular))
                    .foregroundStyle(active ? c.brand : c.fg2)
                    .frame(width: 56, height: 32)
                    .background(Capsule().fill(active ? c.brandSoft : .clear))
                Text(item.label)
                    .font(AppFont.sans(11, active ? .bold : .medium))
                    .foregroundStyle(active ? c.fg : c.fg2)
            }
            .padding(.top, 8)
            .padding(.bottom, 6)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// The logo on the palette's lacquer (rounded 12); active: a 2 pt brand ring around it.
struct Logo: View {
    let active: Bool
    let size: CGFloat
    @Environment(\.colors) private var c

    var body: some View {
        Image("LogoMark")
            .resizable()
            .frame(width: size, height: size)
            .background(c.logo)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(4)
            .overlay {
                if active { RoundedRectangle(cornerRadius: 16).stroke(c.brand, lineWidth: 2) }
            }
    }
}
