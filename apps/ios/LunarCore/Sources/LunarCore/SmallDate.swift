import Foundation

/// The small date under a day in a month grid (shared/design/month-grid.md): the day, with its
/// month on the first of a month and on the selected day ("16/10"); `leap` for a leap lunar month,
/// which the grids draw as a small raised "n" after the month.
public struct SmallDate: Hashable, Sendable {
    public let day: Int
    public let month: Int?
    public let leap: Bool

    public init(_ day: Int, _ month: Int? = nil, leap: Bool = false) {
        self.day = day
        self.month = month
        self.leap = leap
    }

    /// Plain text, the leap mark as a plain "n": for estimating widths and for VoiceOver.
    public var text: String {
        guard let month else { return "\(day)" }
        return "\(day)/\(month)\(leap ? "n" : "")"
    }

    /// A lunar day: with its month on mồng 1 and when [selected].
    public static func lunar(_ l: LunarDate, selected: Bool) -> SmallDate {
        l.day == 1 || selected ? SmallDate(l.day, l.month, leap: l.leap) : SmallDate(l.day)
    }

    /// A solar day (the small date of a grid picking lunar days): with its month on the 1st and when [selected].
    public static func solar(_ d: SolarDate, selected: Bool) -> SmallDate {
        d.day == 1 || selected ? SmallDate(d.day, d.month) : SmallDate(d.day)
    }
}
