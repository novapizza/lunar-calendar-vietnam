import SwiftUI
import LunarCore

@main
struct LichAmApp: App {
    @State private var state: AppState

    init() {
        // UI tests start from a fresh store (LichAmUITests passes -ui-reset).
        if CommandLine.arguments.contains("-ui-reset") { Store.reset() }
        let state = AppState()
        // Screenshots and tests may start on a link: -ui-open com.coding4pizza.lunar://open?tab=calendar&d=…
        // (simctl openurl asks "Open in Lịch Âm?" first).
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "-ui-open"), i + 1 < args.count, let url = URL(string: args[i + 1]) { state.open(url) }
        _state = State(initialValue: state)
    }

    var body: some Scene {
        WindowGroup {
            Root()
                .environment(state)
                // Widgets and notifications open com.coding4pizza.lunar://open?tab=…&d=YYYY-MM-DD.
                .onOpenURL { state.open($0) }
        }
    }
}

/// Theme from the user's choice (Hệ thống / Sáng / Tối) and the palette of their mệnh.
private struct Root: View {
    @Environment(AppState.self) private var state
    @Environment(\.colorScheme) private var systemScheme

    private var scheme: ColorScheme? {
        switch state.themePref {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    var body: some View {
        let dark = (scheme ?? systemScheme) == .dark
        AppScaffold()
            .environment(\.colors, LichAmColors(state.palette, dark: dark))
            .preferredColorScheme(scheme)
            .dynamicTypeSize(...maxDynamicType)
            .font(AppFont.sans(16))
    }
}
