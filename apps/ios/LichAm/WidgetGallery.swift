#if DEBUG
import SwiftUI
import WidgetKit
import LunarCore

/// The widgets drawn in the app, at an iPhone's widget sizes, for screenshots (the home screen cannot be
/// driven over SSH): launch with `-ui-widgets sheet` or `-ui-widgets month`; `-ui-open …&d=YYYY-MM-DD`
/// sets the day taken as today. Debug builds only.
struct WidgetGallery: View {
    let kind: String
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    // The iPhone 17 Pro's widget sizes in points (phỏng đoán from Apple's size table for 402 pt wide).
    private let sizes: [(WidgetFamily, CGSize)] = [
        (.systemSmall, CGSize(width: 170, height: 170)),
        (.systemMedium, CGSize(width: 364, height: 170)),
        (.systemLarge, CGSize(width: 364, height: 382)),
    ]

    var body: some View {
        let day = state.selectedDate
        VStack(spacing: 12) {
            ForEach(sizes, id: \.0) { family, size in
                widget(family, day)
                    .frame(width: size.width, height: size.height)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .frame(maxWidth: .infinity, alignment: family == .systemSmall ? .leading : .center)
            }
        }
        .padding(.horizontal, 19)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(LinearGradient(colors: [Color(hex: "#5B6E8C"), Color(hex: "#2E3A4F")], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
    }

    @ViewBuilder
    private func widget(_ family: WidgetFamily, _ day: SolarDate) -> some View {
        if kind == "month" {
            MonthWidgetView(today: day, family: family, userChi: state.userChi, personal: state.lunarEvents)
                .background(c.surface)
        } else {
            let name = DayInfo.orNil(day).flatMap { sheetName($0, state.lunarEvents) }
            SheetWidgetView(day: day, name: name, family: family).background(SheetPaper())
        }
    }
}
#endif
