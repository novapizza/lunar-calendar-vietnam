import Foundation

// Models of the JSON files in shared/data (copied into Resources/ by shared/scripts/gen-ios-data.mjs).

/// Keys read from a JSON object whose keys are data ("10-3", "wedding", …).
struct AnyKey: CodingKey {
    var stringValue: String
    var intValue: Int? { nil }
    init(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { nil }
}

public struct CalendarJson: Decodable, Sendable {
    public let can: [String]
    public let chi: [String]
    public let chiEmoji: [String: String]
    public let weekDays: [String]
    public let weekDaysShort: [String]
    public let gioHoangDao: GioHoangDaoJson
    public let truc: TrucJson
    /// Activity rules by key; the JSON object also holds a "$comment" string, skipped.
    public let activities: [String: ActivityJson]

    enum CodingKeys: String, CodingKey { case can, chi, chiEmoji, weekDays, weekDaysShort, gioHoangDao, truc, activities }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        can = try c.decode([String].self, forKey: .can)
        chi = try c.decode([String].self, forKey: .chi)
        chiEmoji = try c.decode([String: String].self, forKey: .chiEmoji)
        weekDays = try c.decode([String].self, forKey: .weekDays)
        weekDaysShort = try c.decode([String].self, forKey: .weekDaysShort)
        gioHoangDao = try c.decode(GioHoangDaoJson.self, forKey: .gioHoangDao)
        truc = try c.decode(TrucJson.self, forKey: .truc)
        let a = try c.nestedContainer(keyedBy: AnyKey.self, forKey: .activities)
        var out: [String: ActivityJson] = [:]
        for k in a.allKeys where !k.stringValue.hasPrefix("$") {
            out[k.stringValue] = try a.decode(ActivityJson.self, forKey: k)
        }
        activities = out
    }
}

public struct GioHoangDaoJson: Decodable, Sendable {
    public let byDayChi: [[Int]]
}

public struct TrucJson: Decodable, Sendable {
    public let names: [String]
    public let monthChiStart: [Int]
    public let good: [String]
    public let bad: [String]
}

public struct ActivityJson: Decodable, Sendable {
    public let label: String
    public let truc: [String]
}

public struct DayNamesJson: Decodable, Sendable {
    public var solar: [String: String] = [:]
    public var lunar: [String: String] = [:]

    public init() {}

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        solar = try c.decodeIfPresent([String: String].self, forKey: AnyKey(stringValue: "solar")) ?? [:]
        lunar = try c.decodeIfPresent([String: String].self, forKey: AnyKey(stringValue: "lunar")) ?? [:]
    }
}

public struct HolidaysJson: Decodable, Sendable {
    public let lunar: [String: String]
    public let solar: [String: String]
    /// First year a solar holiday counts (24/11 from 2026, 27/7 from 2027).
    public let solarSince: [String: Int]
    /// Commemorative and lesser traditional days: printed, not red.
    public let observances: DayNamesJson
    /// Temple festivals (main day): printed, not red.
    public let festivals: DayNamesJson

    enum CodingKeys: String, CodingKey { case lunar, solar, solarSince, observances, festivals }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        lunar = try c.decode([String: String].self, forKey: .lunar)
        solar = try c.decode([String: String].self, forKey: .solar)
        solarSince = try c.decodeIfPresent([String: Int].self, forKey: .solarSince) ?? [:]
        observances = try c.decodeIfPresent(DayNamesJson.self, forKey: .observances) ?? DayNamesJson()
        festivals = try c.decodeIfPresent(DayNamesJson.self, forKey: .festivals) ?? DayNamesJson()
    }

    /// The solar holiday on [date], honouring `solarSince`.
    public func solarHoliday(_ date: SolarDate) -> String? {
        let key = "\(date.day)-\(date.month)"
        return date.year >= (solarSince[key] ?? 0) ? solar[key] : nil
    }

    /// The commemorative days and festivals of a day, observances first. A leap month has none of
    /// the lunar ones; a solar observance gives way to the same day's holiday (27/7 from 2027).
    public func observancesOf(_ date: SolarDate, _ lunar: LunarDate) -> [DayName] {
        var out: [DayName] = []
        let solarKey = "\(date.day)-\(date.month)"
        if let o = observances.solar[solarKey], solarHoliday(date) == nil { out.append(DayName(title: o, kind: .observance)) }
        if !lunar.leap {
            let lunarKey = "\(lunar.day)-\(lunar.month)"
            if let o = observances.lunar[lunarKey] { out.append(DayName(title: o, kind: .observanceLunar)) }
            if let f = festivals.lunar[lunarKey] { out.append(DayName(title: f, kind: .festival)) }
        }
        return out
    }
}

/// A named day of the holiday data: its title and what kind of day it is.
public struct DayName: Hashable, Sendable {
    public let title: String
    public let kind: EventKind
}

public struct ElementJson: Decodable, Sendable {
    public let name: String
    public let color: String
    public let generatedBy: String
}

public struct NapAmJson: Decodable, Sendable {
    public let name: String
    public let element: String
}

public struct MenhJson: Decodable, Sendable {
    public let cycleStartYear: Int
    public let elements: [String: ElementJson]
    public let napAm: [NapAmJson]
    public let birthDateMin: String
}

public struct PresetEventJson: Decodable, Sendable {
    public let id: String
    public let title: String
    public let lunarDay: Int
    public let lunarMonth: Int
}

public struct PresetJson: Decodable, Sendable {
    public let id: String
    public let label: String
    public let description: String
    public let emoji: String
    public let events: [PresetEventJson]
}

public struct PresetsJson: Decodable, Sendable {
    public let presets: [PresetJson]
}

public struct LunarYearsJson: Decodable, Sendable {
    public let firstYear: Int
    public let years: [Int]
}

/// Colours of one theme: token name ("brand", "brand-soft", …) → "#rrggbb", light and dark.
public struct PaletteJson: Decodable, Sendable {
    public let light: [String: String]
    public let dark: [String: String]
}

public struct TokensJson: Decodable, Sendable {
    public let base: PaletteJson
    public let palettes: [String: PaletteJson]
    public let descriptions: [String: String]

    enum CodingKeys: String, CodingKey { case base, palettes, descriptions }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        base = try c.decode(PaletteJson.self, forKey: .base)
        palettes = try c.decode([String: PaletteJson].self, forKey: .palettes)
        descriptions = try c.decodeIfPresent([String: String].self, forKey: .descriptions) ?? [:]
    }
}

/// The shared data, loaded once from the package's resources.
public enum CalendarData {
    static func load<T: Decodable>(_ name: String) -> T {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json") else {
            fatalError("\(name).json is missing from LunarCore's resources (run `npm run gen`)")
        }
        do {
            return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
        } catch {
            fatalError("\(name).json: \(error)")
        }
    }

    public static let calendar: CalendarJson = load("calendar")
    public static let holidays: HolidaysJson = load("holidays")
    public static let menh: MenhJson = load("menh")
    public static let presets: PresetsJson = load("presets")
    public static let lunarYears: LunarYearsJson = load("lunar-years")
    public static let tokens: TokensJson = load("tokens")
}
