import Foundation

/// Rating of a day from its trực.
public enum Rating: String, Sendable {
    case good, bad, neutral
}

/// One giờ hoàng đạo: chi index, chi name and hours ("23–1h").
public struct Hour: Hashable, Sendable {
    public let chiIndex: Int
    public let chi: String
    public let startHour: Int
    public let endHour: Int
    public var label: String { "\(startHour)–\(endHour)h" }
}

/// Everything the app shows about one day; same rules as the web app's getLunarInfo.
public struct DayInfo: Sendable {
    public let solar: SolarDate
    public let lunar: LunarDate
    public let canChiDay: String
    public let canChiMonth: String
    public let canChiYear: String
    public let truc: String
    public let rating: Rating
    public let gioHoangDao: [Hour]
    /// Activity keys of shared/data/calendar.json (wedding, moving, opening) that suit the day.
    public let activities: Set<String>
    public let holiday: String?
    /// The first commemorative day or festival: printed on the sheet when there is no holiday, not red.
    public let observance: String?
    /// All of the day's commemorative days and festivals, observances first (the Calendar's day panel).
    public let observances: [DayName]

    public var dayChiIndex: Int {
        let chi = canChiDay.split(separator: " ", maxSplits: 1).last.map(String.init) ?? ""
        return CalendarData.calendar.chi.firstIndex(of: chi) ?? -1
    }

    public static func of(_ date: SolarDate) -> DayInfo { DayFacts.info(date) }

    /// Day facts, or nil outside the supported range (1900-01-31 … 2099-12-31).
    public static func orNil(_ date: SolarDate) -> DayInfo? { date.isInRange ? DayFacts.info(date) : nil }
}

public enum DayFacts {
    private static var cal: CalendarJson { CalendarData.calendar }

    /// Can chi of a day from its Julian day number.
    public static func canChiDay(_ date: SolarDate) -> String {
        let jd = date.jd
        return "\(cal.can[(jd + 9) % 10]) \(cal.chi[(jd + 1) % 12])"
    }

    /// Can chi of a lunar month (a leap month shares its month's can chi).
    public static func canChiMonth(_ lunar: LunarDate) -> String {
        "\(cal.can[(lunar.year * 12 + lunar.month + 3) % 10]) \(cal.chi[(lunar.month + 1) % 12])"
    }

    /// Can chi of a lunar year.
    public static func canChiYear(_ lunarYear: Int) -> String {
        "\(cal.can[floorMod(lunarYear + 6, 10)]) \(cal.chi[floorMod(lunarYear + 8, 12)])"
    }

    /// Chi index of the traditional hour at a clock hour (Tý = 23h–1h).
    public static func chiOfHour(_ hour: Int) -> Int { ((hour + 1) / 2) % 12 }

    public static func info(_ date: SolarDate) -> DayInfo {
        let lunar = LunarCalendar.solarToLunar(date)
        let jd = date.jd
        let dayCanChi = "\(cal.can[(jd + 9) % 10]) \(cal.chi[(jd + 1) % 12])"
        let dayChi = (jd + 1) % 12
        let truc = cal.truc.names[(dayChi - cal.truc.monthChiStart[lunar.month - 1] + 12) % 12]
        let rating: Rating = cal.truc.good.contains(truc) ? .good : cal.truc.bad.contains(truc) ? .bad : .neutral
        let hours = cal.gioHoangDao.byDayChi[dayChi].map { i in
            Hour(chiIndex: i, chi: cal.chi[i], startHour: (i * 2 - 1 + 24) % 24, endHour: (i * 2 + 1) % 24)
        }
        let activities: Set<String> = rating == .good
            ? Set(cal.activities.filter { $0.value.truc.contains(truc) }.keys)
            : []
        let h = CalendarData.holidays
        let holiday = h.lunar["\(lunar.day)-\(lunar.month)"] ?? h.solarHoliday(date)
        let observances = h.observancesOf(date, lunar)
        return DayInfo(
            solar: date,
            lunar: lunar,
            canChiDay: dayCanChi,
            canChiMonth: canChiMonth(lunar),
            canChiYear: canChiYear(lunar.year),
            truc: truc,
            rating: rating,
            gioHoangDao: hours,
            activities: activities,
            holiday: holiday,
            observance: observances.first?.title,
            observances: observances
        )
    }
}
