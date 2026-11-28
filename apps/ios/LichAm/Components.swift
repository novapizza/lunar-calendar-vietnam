import SwiftUI
import UIKit
import LunarCore

// The web's building blocks, as Android's ui/Components.kt: card, section label, icon tile, row,
// badge, buttons. Sizes are the web's Tailwind classes.

let weekdayNames = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"]

extension SolarDate {
    var weekdayName: String { weekdayNames[weekdayIndex] }
    /// "Thứ Bảy, 21/11/2026"
    var longLabel: String { "\(weekdayName), \(day)/\(month)/\(year)" }
}

/// Emoji of a chi (shared/data/calendar.json), as on the web and Android.
func chiEmoji(_ chi: String) -> String? { CalendarData.calendar.chiEmoji[chi] }

extension LichAmColors {
    /// The event's colour token (shared/design/events.md: gold, brand, lunar, good, clash).
    func event(_ token: String?) -> Color {
        switch token { case "brand": brand; case "lunar": lunar; case "good": good; case "clash": clash; default: gold }
    }

    func eventSoft(_ token: String?) -> Color {
        switch token { case "brand": brandSoft; case "lunar": lunarSoft; case "good": goodSoft; case "clash": clashSoft; default: goldSoft }
    }
}

extension Color {
    /// [self] moved [t] of the way to [other] (Compose's lerp), in sRGB.
    func mix(_ other: Color, _ t: Double) -> Color {
        var (r1, g1, b1, a1, r2, g2, b2, a2): (CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0, 0, 0, 0, 0)
        UIColor(self).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        UIColor(other).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let f = CGFloat(t)
        return Color(.sRGB, red: r1 + (r2 - r1) * f, green: g1 + (g2 - g1) * f, blue: b1 + (b2 - b1) * f, opacity: a1 + (a2 - a1) * f)
    }
}

extension View {
    /// --shadow-card (0 1px 2px tint/5%, 0 4px 16px -4px tint/8%); [float] = --shadow-float.
    func webShadow(_ tint: Color, float: Bool = false) -> some View {
        Group {
            if float {
                shadow(color: tint.opacity(0.22), radius: 16, x: 0, y: 8)
            } else {
                shadow(color: tint.opacity(0.05), radius: 1, x: 0, y: 1)
                    .shadow(color: tint.opacity(0.08), radius: 6, x: 0, y: 4)
            }
        }
    }
}

/// `.card`: surface, radius 20, shadow-card.
struct Card<Content: View>: View {
    var padding: CGFloat = 16
    var color: Color? = nil
    @ViewBuilder let content: Content
    @Environment(\.colors) private var c

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 20).fill(color ?? c.surface))
            .webShadow(c.shadowTint)
    }
}

/// `.section-label`: 11, semibold, uppercase, tracking 0.08em, fg-3.
struct SectionLabel: View {
    let text: String
    var icon: String? = nil
    @Environment(\.colors) private var c

    var body: some View {
        HStack(spacing: 6) {
            if let icon { Image(systemName: icon).font(.system(size: 12, weight: .medium)) }
            Text(text.uppercased()).font(AppFont.sans(11, .semibold)).tracking(11 * 0.08)
        }
        .foregroundStyle(c.fg3)
    }
}

/// Rounded square tile with an icon (w-10 h-10 rounded-2xl by default).
struct IconTile: View {
    let icon: String
    let bg: Color
    let fg: Color
    var size: CGFloat = 40
    var iconSize: CGFloat = 17
    var radius: CGFloat = 16

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: iconSize, weight: .medium))
            .foregroundStyle(fg)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: radius).fill(bg))
    }
}

/// The web's `Row`: px-4 py-3, tile, title text-sm semibold, detail text-xs.
struct InfoRow: View {
    let icon: String
    let bg: Color
    let fg: Color
    let title: String
    var detail: String? = nil
    @Environment(\.colors) private var c

    var body: some View {
        HStack(spacing: 12) {
            IconTile(icon: icon, bg: bg, fg: fg)
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
                if let detail { Text(detail).font(AppFont.sans(12)).foregroundStyle(c.fg2).lineLimit(1) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

struct Hairline: View {
    @Environment(\.colors) private var c
    var body: some View { Rectangle().fill(c.line).frame(height: 1) }
}

/// Small pill badge: text-[11px] bold, px-2.5 py-1.
struct Badge: View {
    let text: String
    let fg: Color
    let bg: Color

    var body: some View {
        Text(text).font(AppFont.sans(11, .bold)).foregroundStyle(fg).lineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(Capsule().fill(bg))
    }
}

/// `.icon-btn`: 40 × 40 round, icon in fg-2.
struct IconBtn: View {
    let icon: String
    let label: String
    var tint: Color? = nil
    var iconSize: CGFloat = 18
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: iconSize, weight: .medium))
                .foregroundStyle(tint ?? c.fg2)
                .frame(width: 40, height: 40)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// `.chip`: rounded-full px-3.5 py-2, text-sm semibold, surface, shadow-card; icon 15 in brand.
struct Chip: View {
    let text: String
    var icon: String? = nil
    var textColor: Color? = nil
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Image(systemName: icon).font(.system(size: 13, weight: .semibold)).foregroundStyle(c.brand) }
                Text(text).font(AppFont.sans(14, .semibold)).foregroundStyle(textColor ?? c.fg).lineLimit(1)
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(Capsule().fill(c.surface))
            .webShadow(c.shadowTint)
        }
        .buttonStyle(.plain)
    }
}

/// `.btn-soft` small: px-3 py-1.5 text-xs semibold, brand on brand-soft.
struct SoftButton: View {
    let text: String
    var icon: String? = nil
    let action: () -> Void
    @Environment(\.colors) private var c

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Image(systemName: icon).font(.system(size: 13, weight: .semibold)) }
                Text(text).font(AppFont.sans(12, .semibold))
            }
            .foregroundStyle(c.brand)
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 12).fill(c.brandSoft))
        }
        .buttonStyle(.plain)
    }
}
