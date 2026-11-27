import SwiftUI
import LunarCore

@main
struct LichAmApp: App {
    @State private var state = AppState()

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
