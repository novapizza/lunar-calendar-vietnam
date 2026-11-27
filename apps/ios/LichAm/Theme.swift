import SwiftUI
import LunarCore

/// The app's colours, read from shared/design/tokens.json (the same values as the web and Android):
/// base light/dark colours, overridden by the palette of the chosen element (Android's LichAmColors).
struct LichAmColors {
    let bg, surface, surface2, surface3, line, fg, fg2, fg3: Color
    let brand, brandStrong, brandSoft, onBrand: Color
    let lunar, lunarSoft, gold, goldSoft, good, goodSoft, bad, badSoft, clash, clashSoft: Color
    let heroFrom, heroVia, heroTo, heroAccent, heroAccentInk: Color
    /// Logo background: the light palette's value, kept in dark mode (as on the web).
    let logo: Color
    /// --shadow-tint ("r g b"), the colour of card shadows.
    let shadowTint: Color

    init(_ palette: MenhElement, dark: Bool) {
        let t = CalendarData.tokens
        let pal = t.palettes[palette.rawValue] ?? t.palettes["hoa"]!
        var v = (dark ? t.base.dark : t.base.light).merging(dark ? pal.dark : pal.light) { _, p in p }
        v["logo"] = pal.light["logo"] ?? t.base.light["logo"]
        func c(_ name: String) -> Color {
            guard let hex = v[name] else { fatalError("tokens.json has no colour '\(name)'") }
            return Color(hex: hex)
        }
        bg = c("bg"); surface = c("surface"); surface2 = c("surface-2"); surface3 = c("surface-3")
        line = c("line"); fg = c("fg"); fg2 = c("fg-2"); fg3 = c("fg-3")
        brand = c("brand"); brandStrong = c("brand-strong"); brandSoft = c("brand-soft"); onBrand = c("on-brand")
        lunar = c("lunar"); lunarSoft = c("lunar-soft"); gold = c("gold"); goldSoft = c("gold-soft")
        good = c("good"); goodSoft = c("good-soft"); bad = c("bad"); badSoft = c("bad-soft")
        clash = c("clash"); clashSoft = c("clash-soft")
        heroFrom = c("hero-from"); heroVia = c("hero-via"); heroTo = c("hero-to")
        heroAccent = c("hero-accent"); heroAccentInk = c("hero-accent-ink")
        logo = c("logo")
        let rgb = (v["shadow-tint"] ?? "0 0 0").split(separator: " ").compactMap { Double($0) }
        shadowTint = rgb.count == 3 ? Color(red: rgb[0] / 255, green: rgb[1] / 255, blue: rgb[2] / 255) : .black
    }
}

extension Color {
    /// "#rrggbb" → Color (sRGB).
    init(hex: String) {
        let v = UInt32(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        self.init(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
}

extension EnvironmentValues {
    @Entry var colors = LichAmColors(.hoa, dark: false)
}

/// The web's typefaces, bundled from apps/android/app/src/main/res/font (project.yml):
/// Be Vietnam Pro for text, Playfair Display for the tear-off sheet.
enum AppFont {
    /// Be Vietnam Pro at [size] points, growing with Dynamic Type like body text.
    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name = switch weight {
        case .medium: "BeVietnamPro-Medium"
        case .semibold: "BeVietnamPro-SemiBold"
        case .bold: "BeVietnamPro-Bold"
        case .heavy, .black: "BeVietnamPro-ExtraBold"
        default: "BeVietnamPro-Regular"
        }
        return .custom(name, size: size, relativeTo: .body)
    }

    /// Playfair Display (a variable font, 400–900): the weight picks the instance.
    static func serif(_ size: CGFloat, _ weight: Font.Weight = .semibold) -> Font {
        .custom("PlayfairDisplay-Regular", size: size, relativeTo: .body).weight(weight)
    }

    static func serifItalic(_ size: CGFloat) -> Font {
        .custom("PlayfairDisplay-Italic", size: size, relativeTo: .body)
    }
}

/// Largest Dynamic Type size the app honours (Android caps the font scale at 1.3).
let maxDynamicType = DynamicTypeSize.xxLarge
