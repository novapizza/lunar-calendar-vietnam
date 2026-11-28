import SwiftUI
import LunarCore

/// The privacy policy, on the web app's site (apps/web/public/privacy.html).
let privacyURL = URL(string: "https://lunar.coding4pizza.com/privacy")!

/// Tiện ích (Android's ToolsScreen): the tools (converter, finder, export), then the settings (birth
/// date, colour, theme), the app's information and the privacy policy. iPad (wide layout): two columns.
struct ToolsScreen: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @Environment(\.wideLayout) private var wideLayout

    var body: some View {
        GeometryReader { geo in
            let wide = wideLayout
            ScrollView {
                Group {
                    if wide {
                        HStack(alignment: .top, spacing: 24) {
                            VStack(alignment: .leading, spacing: 16) { tools(wide: true) }.frame(maxWidth: .infinity)
                            VStack(alignment: .leading, spacing: 16) { settings }.frame(maxWidth: .infinity).padding(.top, 8)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 16) {
                            tools(wide: false)
                            settings
                        }
                    }
                }
                .padding(EdgeInsets(top: wide ? 32 : 16, leading: 16, bottom: 40, trailing: 16))
            }
        }
    }

    @ViewBuilder private func tools(wide: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            SectionLabel(text: "Công cụ")
            Text("Tiện ích").font(AppFont.sans(wide ? 30 : 24, .heavy)).tracking(wide ? -0.3 : -0.24).foregroundStyle(c.fg)
                .accessibilityAddTraits(.isHeader)
        }
        Card(padding: 0) {
            ToolRow(icon: "arrow.left.arrow.right", title: "Đổi ngày âm – dương", detail: "Tra ngày dương ra ngày âm và ngược lại") { state.pushSheet(.quickDate) }
            Hairline()
            ToolRow(icon: "magnifyingglass", title: "Tìm ngày tốt", detail: "Cưới hỏi, khởi công, khai trương") { state.pushSheet(.auspicious) }
            Hairline()
            ToolRow(icon: "square.and.arrow.down", title: "Xuất sự kiện âm lịch", detail: "Giỗ, sinh nhật âm lịch ra file .ics cho Google Calendar") { state.pushSheet(.lunarEvents) }
        }
    }

    @ViewBuilder private var settings: some View {
        SectionLabel(text: "Cài đặt").padding(.leading, 4).padding(.top, 8)
        Card(padding: 0) {
            BirthDateRow()
            Hairline()
            AccentRow()
            Hairline()
            ThemeRow()
        }
        Card(padding: 0) {
            ToolRow(icon: "info.circle", title: "Thông tin ứng dụng", detail: "Cách tính âm lịch, ngày tốt xấu, xung tuổi") { state.pushSheet(.about) }
            Hairline()
            // The App Store asks for the privacy policy; it opens in the browser.
            Link(destination: privacyURL) {
                ToolRowLabel(icon: "hand.raised", title: "Chính sách quyền riêng tư", detail: "Ứng dụng không thu thập dữ liệu của bạn")
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Chính sách quyền riêng tư")
            .accessibilityHint("Ứng dụng không thu thập dữ liệu của bạn")
        }
    }
}

private struct ToolRow: View {
    let icon: String
    let title: String
    let detail: String
    let action: () -> Void

    var body: some View {
        Button(action: action) { ToolRowLabel(icon: icon, title: title, detail: detail) }
            .buttonStyle(.plain)
            .accessibilityLabel(title)
            .accessibilityHint(detail)
    }
}

private struct ToolRowLabel: View {
    let icon: String
    let title: String
    let detail: String
    @Environment(\.colors) private var c

    var body: some View {
        HStack(spacing: 12) {
            IconTile(icon: icon, bg: c.brandSoft, fg: c.brand)
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
                Text(detail).font(AppFont.sans(12)).foregroundStyle(c.fg2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.fg3)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/// Ngày sinh: the date (or "Chọn ngày"), opening the picker; the tuổi and mệnh under it.
private struct BirthDateRow: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let b = state.birthDate
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Ngày sinh").font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
                    Text("Dương lịch. Tính mệnh, tuổi xung").font(AppFont.sans(12)).foregroundStyle(c.fg2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button { state.pushSheet(.birthDate) } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar").font(.system(size: 14)).foregroundStyle(c.fg3)
                        Text(b.map { String(format: "%02d/%02d/%04d", $0.day, $0.month, $0.year) } ?? "Chọn ngày")
                            .font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(b != nil ? "Đổi ngày sinh" : "Chọn ngày sinh")
                .accessibilityValue(b.map { "\($0.day)/\($0.month)/\(String($0.year))" } ?? "")
            }
            if let m = state.menh {
                Text("Tuổi \(m.canChi) (\(String(m.lunarYear)) âm lịch) · mệnh \(m.element.info.name) – \(m.napAm)")
                    .font(AppFont.sans(12)).foregroundStyle(c.fg2)
                    .accessibilityIdentifier("menh")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

/// Màu chủ đạo: the mệnh's palette, the one that generates it, the default; a choice repeating an
/// earlier palette is left out (as on the web).
private struct AccentRow: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    private func options(_ menh: Menh?) -> [(AccentPref, String, MenhElement)] {
        var seen = Set<MenhElement>()
        return [(AccentPref.banMenh, "Bản mệnh"), (.tuongSinh, "Tương sinh"), (.macDinh, "Mặc định")]
            .map { ($0.0, $0.1, MenhCalc.paletteFor(menh, $0.0)) }
            .filter { (menh != nil || $0.0 == .macDinh) && seen.insert($0.2).inserted }
    }

    var body: some View {
        let menh = state.menh
        VStack(alignment: .leading, spacing: 0) {
            Text("Màu chủ đạo").font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
            if menh == nil { Text("Nhập ngày sinh để dùng màu hợp mệnh").font(AppFont.sans(12)).foregroundStyle(c.fg2) }
            HStack(spacing: 4) {
                ForEach(options(menh), id: \.0.rawValue) { pref, label, element in
                    let active = element == state.palette
                    let swatch = LichAmColors(element, dark: false).brand
                    Button { state.updateAccent(pref) } label: {
                        VStack(spacing: 4) {
                            Circle().fill(swatch).frame(width: 24, height: 24)
                                .padding(active ? 4 : 0)
                                .overlay { if active { Circle().strokeBorder(swatch, lineWidth: 2) } }
                                .frame(height: 32)
                            Text(label).font(AppFont.sans(12, .semibold)).foregroundStyle(active ? c.fg : c.fg2)
                            Text(element.info.color).font(AppFont.sans(11)).foregroundStyle(c.fg2)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 4).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 8).fill(active ? c.surface : c.surface2))
                        .webShadow(active ? c.shadowTint : .clear)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(label), \(element.info.color)")
                    .accessibilityAddTraits(active ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(4)
            .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
            .padding(.top, 8)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

/// Giao diện: Theo máy / Sáng / Tối.
private struct ThemeRow: View {
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Giao diện").font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
            HStack(spacing: 4) {
                ForEach([(ThemePref.system, "Theo máy", "desktopcomputer"), (.light, "Sáng", "sun.max.fill"), (.dark, "Tối", "moon.fill")], id: \.0) { pref, label, icon in
                    let active = state.themePref == pref
                    Button { state.updateTheme(pref) } label: {
                        VStack(spacing: 4) {
                            Image(systemName: icon).font(.system(size: 14, weight: .medium))
                            Text(label).font(AppFont.sans(12, .semibold))
                        }
                        .foregroundStyle(active ? c.brand : c.fg2)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 4).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 8).fill(active ? c.surface : c.surface2))
                        .webShadow(active ? c.shadowTint : .clear)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(label)
                    .accessibilityAddTraits(active ? [.isButton, .isSelected] : .isButton)
                    .accessibilityIdentifier("theme-\(pref.rawValue.lowercased())")
                }
            }
            .padding(4)
            .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}
