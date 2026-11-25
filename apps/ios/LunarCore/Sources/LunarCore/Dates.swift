import Foundation

/// A lunar date. `month` is 1...12; `leap` marks a leap month (tháng nhuận).
public struct LunarDate: Hashable, Sendable {
    public let day: Int
    public let month: Int
    public let year: Int
    public let leap: Bool

    public init(day: Int, month: Int, year: Int, leap: Bool) {
        self.day = day
        self.month = month
        self.year = year
        self.leap = leap
    }
}

/// A solar (Gregorian) date. `month` is 1...12. Dates are counted in Julian day numbers, so no
/// time zone or `Calendar` is involved (the lunar tables are already in UTC+7).
public struct SolarDate: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(_ year: Int, _ month: Int, _ day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// The solar date of a Julian day number.
    public init(jd: Int) {
        self = LunarCalendar.jdToDate(jd)
    }

    /// Julian day number.
    public var jd: Int { LunarCalendar.jdFromDate(day, month, year) }

    public static func < (a: SolarDate, b: SolarDate) -> Bool {
        (a.year, a.month, a.day) < (b.year, b.month, b.day)
    }

    /// "YYYY-MM-DD", the stored form.
    public var description: String { String(format: "%04d-%02d-%02d", year, month, day) }
    public var iso: String { description }

    /// Parses "YYYY-MM-DD"; nil when the text has another shape.
    public static func parse(_ iso: String) -> SolarDate? {
        let p = iso.split(separator: "-", omittingEmptySubsequences: false)
        guard p.count == 3, p[0].count == 4, p[1].count == 2, p[2].count == 2,
              p.allSatisfy({ $0.allSatisfy(\.isASCII) && $0.allSatisfy(\.isNumber) }),
              let y = Int(p[0]), let m = Int(p[1]), let d = Int(p[2]) else { return nil }
        return SolarDate(y, m, d)
    }

    public func plusDays(_ n: Int) -> SolarDate { SolarDate(jd: jd + n) }

    /// The same day [n] months later, clamped to the month's length (as java.time's plusMonths).
    public func plusMonths(_ n: Int) -> SolarDate {
        let total = year * 12 + (month - 1) + n
        let y = Int(floor(Double(total) / 12))
        let m = total - y * 12 + 1
        return SolarDate(y, m, min(day, daysInMonth(y, m)))
    }

    /// 0 = Monday … 6 = Sunday (the calendar grid starts on Monday).
    public var weekdayIndex: Int { jd % 7 }
    public var isSunday: Bool { weekdayIndex == 6 }

    /// Week number as the web app shows it: weeks start Monday, week 1 contains 1 January.
    public var weekOfYear: Int {
        let monday = jd - weekdayIndex
        func firstMonday(_ y: Int) -> Int { let j = SolarDate(y, 1, 1); return j.jd - j.weekdayIndex }
        if monday >= firstMonday(year + 1) { return 1 }
        return (monday - firstMonday(year)) / 7 + 1
    }

    /// Inside the lunar tables (1900-01-31 … 2099-12-31).
    public var isInRange: Bool { self >= LunarCalendar.firstDay && self <= LunarCalendar.lastDay }
}

public func isLeapYear(_ y: Int) -> Bool { (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 }

public func daysInMonth(_ year: Int, _ month: Int) -> Int {
    switch month {
    case 2: return isLeapYear(year) ? 29 : 28
    case 4, 6, 9, 11: return 30
    default: return 31
    }
}

/// Days of the month grid: Monday of the first week … Sunday of the last week.
public func monthGrid(_ year: Int, _ month: Int) -> [SolarDate] {
    let first = SolarDate(year, month, 1)
    let start = first.jd - first.weekdayIndex
    let last = SolarDate(year, month, daysInMonth(year, month))
    let end = last.jd + (6 - last.weekdayIndex)
    return (start...end).map { SolarDate(jd: $0) }
}

/// A local date and time to the minute, without a time zone (reminders, .ics floating times).
public struct LocalDateTime: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let date: SolarDate
    public let hour: Int
    public let minute: Int

    public init(_ date: SolarDate, _ hour: Int, _ minute: Int) {
        self.date = date
        self.hour = hour
        self.minute = minute
    }

    public init(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int) {
        self.init(SolarDate(year, month, day), hour, minute)
    }

    /// Minutes since the start of Julian day 0.
    public var totalMinutes: Int { date.jd * 1440 + hour * 60 + minute }

    public init(totalMinutes t: Int) {
        let day = Int(floor(Double(t) / 1440))
        let m = t - day * 1440
        self.init(SolarDate(jd: day), m / 60, m % 60)
    }

    public func plusMinutes(_ n: Int) -> LocalDateTime { LocalDateTime(totalMinutes: totalMinutes + n) }

    public static func < (a: LocalDateTime, b: LocalDateTime) -> Bool { a.totalMinutes < b.totalMinutes }

    /// "YYYY-MM-DD HH:mm".
    public var description: String { "\(date) " + String(format: "%02d:%02d", hour, minute) }

    /// The moment in [timeZone] (the device's by default), for scheduling notifications.
    public func toDate(in timeZone: TimeZone = .current) -> Date? {
        var c = DateComponents()
        c.year = date.year; c.month = date.month; c.day = date.day; c.hour = hour; c.minute = minute
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        return cal.date(from: c)
    }
}

/// "HH:mm" (seconds ignored); nil when the text has another shape.
func parseTime(_ s: String) -> (hour: Int, minute: Int)? {
    let p = s.split(separator: ":")
    guard p.count == 2 || p.count == 3, let h = Int(p[0]), let m = Int(p[1]),
          (0...23).contains(h), (0...59).contains(m) else { return nil }
    return (h, m)
}

func floorMod(_ a: Int, _ n: Int) -> Int { ((a % n) + n) % n }
