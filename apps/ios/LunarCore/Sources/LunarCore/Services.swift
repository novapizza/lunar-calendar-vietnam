import Foundation

// MARK: - Day helpers shared by the screens

public enum Zodiac {
    /// Lục Xung: the day's chi is opposite the user's (Tý–Ngọ, Sửu–Mùi, …).
    public static func clashes(_ userChi: String?, _ day: DayInfo) -> Bool {
        guard let userChi, let u = CalendarData.calendar.chi.firstIndex(of: userChi) else { return false }
        let d = day.dayChiIndex
        return d >= 0 && abs(u - d) == 6
    }
}

/// "Mồng 1" / "Ngày rằm" for lunar day 1/15, except 1/1 and 15/1 (named holidays).
public func lunarDayLabel(_ lunar: LunarDate) -> String? {
    if lunar.day == 1 && lunar.month != 1 { return "Mồng 1" }
    if lunar.day == 15 && lunar.month != 1 { return "Ngày rằm" }
    return nil
}

/// "Tháng 9 – 10 âm lịch · Bính Ngọ" for a solar month.
public func lunarMonthSubtitle(_ year: Int, _ month: Int) -> String {
    let firstOfMonth = SolarDate(year, month, 1)
    guard let first = DayInfo.orNil(firstOfMonth.isInRange ? firstOfMonth : LunarCalendar.firstDay),
          let last = DayInfo.orNil(SolarDate(year, month, daysInMonth(year, month))) else { return "" }
    let a = first.lunar.month
    let b = last.lunar.month
    let months = a == b ? "Tháng \(a)" : "Tháng \(a) – \(b)"
    return "\(months) âm lịch · \(last.canChiYear)"
}

// MARK: - Verified lunar → solar (as the web app's lunarToSolar)

public enum LunarToSolar: Hashable, Sendable {
    /// The solar day, and whether day 30 fell back to 29.
    case ok(SolarDate, Bool)
    case fail(String)
}

public enum Conversion {
    private static func exact(_ day: Int, _ month: Int, _ year: Int, _ leap: Bool) -> SolarDate? {
        guard let s = LunarCalendar.lunarToSolar(day, month, year, leap), s.isInRange else { return nil }
        return LunarCalendar.solarToLunar(s) == LunarDate(day: day, month: month, year: year, leap: leap) ? s : nil
    }

    /// Converts and checks by converting back; day 30 of a 29-day month falls back to day 29.
    public static func lunarToSolar(_ day: Int, _ month: Int, _ year: Int, _ leap: Bool) -> LunarToSolar {
        if let s = exact(day, month, year, leap) { return .ok(s, false) }
        if day == 30, let s = exact(29, month, year, leap) { return .ok(s, true) }
        if leap && exact(1, month, year, true) == nil { return .fail("leap_month_not_found") }
        return .fail("conversion_error")
    }
}

// MARK: - Upcoming holidays and lunar days (Sự kiện tab)

public enum Upcoming {
    /// The events on one solar day: a solar holiday, then a named lunar holiday or, failing that,
    /// mồng 1 / rằm (not of tháng Giêng, which has its own holidays), then the observances.
    private static func on(_ d: SolarDate, _ daysUntil: Int) -> [UpcomingEvent] {
        let h = CalendarData.holidays
        var out: [UpcomingEvent] = []
        if let s = h.solarHoliday(d) {
            out.append(UpcomingEvent(solarDate: d, title: s, lunarDay: nil, lunarMonth: nil, lunar: false, daysUntil: daysUntil, ref: s))
        }
        let l = LunarCalendar.solarToLunar(d)
        let named = h.lunar["\(l.day)-\(l.month)"]
        let observances = h.observancesOf(d, l)
        // A named lunar day (Tết Hạ nguyên on 15/10) stands for that month's mồng 1 / rằm.
        let lunarNamed = observances.contains { $0.kind == .observanceLunar }
        if let named {
            out.append(UpcomingEvent(solarDate: d, title: named, lunarDay: l.day, lunarMonth: l.month, lunar: true, daysUntil: daysUntil, ref: named))
        } else if lunarNamed {
            // nothing: the observance below names the day
        } else if l.day == 1 && l.month != 1 {
            out.append(UpcomingEvent(solarDate: d, title: "Mồng 1 tháng \(l.month)", lunarDay: l.day, lunarMonth: l.month, lunar: true,
                                     daysUntil: daysUntil, ref: BuiltinReminders.mong1))
        } else if l.day == 15 && l.month != 1 {
            out.append(UpcomingEvent(solarDate: d, title: "Ngày rằm tháng \(l.month)", lunarDay: l.day, lunarMonth: l.month, lunar: true,
                                     daysUntil: daysUntil, ref: BuiltinReminders.ram))
        }
        for o in observances {
            if o.kind == .observance {
                out.append(UpcomingEvent(solarDate: d, title: o.title, lunarDay: nil, lunarMonth: nil, lunar: false, daysUntil: daysUntil,
                                         kind: o.kind, ref: o.title))
            } else {
                out.append(UpcomingEvent(solarDate: d, title: o.title, lunarDay: l.day, lunarMonth: l.month, lunar: true, daysUntil: daysUntil,
                                         kind: o.kind, ref: o.title))
            }
        }
        return out
    }

    /// Up to [maxCount] solar/lunar holidays, mồng 1 and rằm within [daysToScan] days from [today].
    public static func events(_ today: SolarDate, maxCount: Int = 12, daysToScan: Int = 180) -> [UpcomingEvent] {
        var out: [UpcomingEvent] = []
        var d = today
        for i in 0..<daysToScan {
            if out.count >= maxCount || !d.isInRange { break }
            out += on(d, i)
            d = d.plusDays(1)
        }
        return Array(out.prefix(maxCount))
    }

    /// First day of the solar month [offset] months after [today]'s.
    public static func monthStart(_ today: SolarDate, _ offset: Int) -> SolarDate {
        SolarDate(today.year, today.month, 1).plusMonths(offset)
    }

    /// The events of the whole solar month [offset] months after [today]'s (0 = this month, its
    /// past days included), daysUntil counted from [today] (negative for past days). Empty past
    /// the lunar tables. The user's own [personal] events come first on their day.
    public static func month(_ today: SolarDate, _ offset: Int, personal: [LunarEvent] = []) -> [UpcomingEvent] {
        let first = monthStart(today, offset)
        let todayJd = today.jd
        var out: [UpcomingEvent] = []
        var d = first
        while d.month == first.month && d.isInRange {
            let daysUntil = d.jd - todayJd
            out += PersonalEvents.upcoming(personal, d, daysUntil)
            out += on(d, daysUntil)
            d = d.plusDays(1)
        }
        return out
    }
}

// MARK: - Auspicious-date finder (Tìm ngày tốt)

public struct Activity: Hashable, Sendable {
    public let key: String
    public let label: String
    public let field: String
}

public struct AuspiciousDate: Hashable, Sendable {
    public let date: SolarDate
    public let lunarDate: String
    public let score: Int
    public let truc: String
}

public enum Auspicious {
    /// The finder's three choices, as in the web app (Khởi công uses the "moving" rule).
    public static let activities = [
        Activity(key: "WEDDING", label: "Cưới Hỏi", field: "wedding"),
        Activity(key: "GROUNDBREAKING", label: "Khởi Công", field: "moving"),
        Activity(key: "OPENING", label: "Khai Trương", field: "opening"),
    ]

    /// Up to 10 days from [from] within monthsToScan × 31 days whose activities include the choice.
    public static func find(_ activityKey: String, from: SolarDate, monthsToScan: Int = 2) -> [AuspiciousDate] {
        let field = activities.first { $0.key == activityKey }?.field
        var out: [AuspiciousDate] = []
        var d = from
        for _ in 0..<(monthsToScan * 31) {
            if !d.isInRange { break }
            let info = DayFacts.info(d)
            let include = field.map { info.activities.contains($0) } ?? (info.rating == .good)
            if include {
                out.append(AuspiciousDate(date: d, lunarDate: "\(info.lunar.day)/\(info.lunar.month)", score: field != nil ? 10 : 7, truc: info.truc))
            }
            if out.count >= 10 { break }
            d = d.plusDays(1)
        }
        return out
    }
}
