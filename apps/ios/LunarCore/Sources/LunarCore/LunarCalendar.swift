import Foundation

/// Start of one lunar month: its number, leap flag and Julian day number of day 1.
public struct LunarMonthStart: Hashable, Sendable {
    public let month: Int
    public let leap: Bool
    public let jd: Int
}

/// Vietnamese lunar calendar from Hồ Ngọc Đức's precomputed year tables
/// (shared/data/lunar-years.json, UTC+7), the same tables the web and Android apps use.
/// Range: solar 1900-01-31 (Tết 1900) to 2099-12-31.
public enum LunarCalendar {
    public static let firstDay = SolarDate(1900, 1, 31)
    public static let lastDay = SolarDate(2099, 12, 31)

    /// Julian day number of a solar date.
    public static func jdFromDate(_ dd: Int, _ mm: Int, _ yy: Int) -> Int {
        let a = (14 - mm) / 12
        let y = yy + 4800 - a
        let m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if jd < 2_299_161 { jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083 }
        return jd
    }

    /// Solar date of a Julian day number.
    public static func jdToDate(_ jd: Int) -> SolarDate {
        let b: Int
        let c: Int
        if jd > 2_299_160 {
            let a = jd + 32044
            b = (4 * a + 3) / 146_097
            c = a - b * 146_097 / 4
        } else {
            b = 0
            c = jd + 32082
        }
        let d = (4 * c + 3) / 1461
        let e = c - 1461 * d / 4
        let m = (5 * e + 2) / 153
        let day = e - (153 * m + 2) / 5 + 1
        let month = m + 3 - 12 * (m / 10)
        let year = b * 100 + d - 4800 + m / 10
        return SolarDate(year, month, day)
    }

    /// Decoded years, behind a lock (the app and the widget may ask from several threads).
    private final class Cache: @unchecked Sendable {
        let lock = NSLock()
        var months: [Int: [LunarMonthStart]] = [:]
    }
    private static let cache = Cache()

    /// The 12 or 13 months of lunar year [year], in order; nil outside the tables
    /// (decoding rules in lunar-years.json).
    public static func monthsOf(_ year: Int) -> [LunarMonthStart]? {
        cache.lock.lock()
        defer { cache.lock.unlock() }
        if let cached = cache.months[year] { return cached }
        let table = CalendarData.lunarYears
        let i = year - table.firstYear
        guard i >= 0, i < table.years.count else { return nil }
        let v = table.years[i]
        let tetOffset = v >> 17
        let leapLength = 29 + ((v >> 16) & 1)
        let leapMonth = v & 0xF
        var bits = v >> 4
        var lengths = [Int](repeating: 29, count: 12)
        for k in stride(from: 11, through: 0, by: -1) {
            lengths[k] = 29 + (bits & 1)
            bits >>= 1
        }
        var jd = jdFromDate(1, 1, year) + tetOffset
        var out: [LunarMonthStart] = []
        out.reserveCapacity(13)
        for m in 1...12 {
            out.append(LunarMonthStart(month: m, leap: false, jd: jd))
            jd += lengths[m - 1]
            if m == leapMonth {
                out.append(LunarMonthStart(month: m, leap: true, jd: jd))
                jd += leapLength
            }
        }
        cache.months[year] = out
        return out
    }

    /// Solar → lunar. Traps for dates outside `firstDay`...`lastDay` (as the Kotlin port throws):
    /// callers check `SolarDate.isInRange` first.
    public static func solarToLunar(_ yy: Int, _ mm: Int, _ dd: Int) -> LunarDate {
        let date = SolarDate(yy, mm, dd)
        precondition(date.isInRange, "\(date) is outside \(firstDay)...\(lastDay)")
        let jd = jdFromDate(dd, mm, yy)
        var lunarYear = yy
        var months = monthsOf(yy)!
        if jd < months[0].jd {
            lunarYear = yy - 1
            months = monthsOf(lunarYear)!
        }
        let start = months.last { $0.jd <= jd }!
        return LunarDate(day: jd - start.jd + 1, month: start.month, year: lunarYear, leap: start.leap)
    }

    public static func solarToLunar(_ date: SolarDate) -> LunarDate {
        solarToLunar(date.year, date.month, date.day)
    }

    /// Lunar → solar, or nil when that (leap) month does not exist that year.
    /// Like the web app's library, day 30 of a 29-day month rolls into the next month;
    /// callers that need an exact date convert the result back (`Conversion`).
    public static func lunarToSolar(_ lunarDay: Int, _ lunarMonth: Int, _ lunarYear: Int, _ leap: Bool) -> SolarDate? {
        guard let start = monthsOf(lunarYear)?.first(where: { $0.month == lunarMonth && $0.leap == leap }) else { return nil }
        return jdToDate(start.jd + lunarDay - 1)
    }
}
