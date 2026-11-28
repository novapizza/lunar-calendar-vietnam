import Foundation

/// The five elements; the raw value matches shared/data/menh.json and the palettes in tokens.json.
public enum MenhElement: String, CaseIterable, Sendable {
    case kim, thuy, moc, hoa, tho

    public var info: ElementJson { CalendarData.menh.elements[rawValue]! }
    /// The element that generates this one (tương sinh).
    public var generatedBy: MenhElement { MenhElement(rawValue: info.generatedBy)! }
}

/// Mệnh of a lunar year, e.g. 1990 → Canh Ngọ, Lộ Bàng Thổ, Thổ.
public struct Menh: Hashable, Sendable {
    public let lunarYear: Int
    public let canChi: String
    public let chi: String
    public let napAm: String
    public let element: MenhElement
}

/// Which colour the app uses: own element, generating element, or the default red.
public enum AccentPref: String, Sendable {
    case banMenh = "BAN_MENH", tuongSinh = "TUONG_SINH", macDinh = "MAC_DINH"
}

public enum MenhCalc {
    public static func ofLunarYear(_ lunarYear: Int) -> Menh {
        let data = CalendarData.menh
        let cal = CalendarData.calendar
        let i = floorMod(lunarYear - data.cycleStartYear, 60)
        let chi = cal.chi[i % 12]
        let nap = data.napAm[i >> 1]
        return Menh(lunarYear: lunarYear, canChi: "\(cal.can[i % 10]) \(chi)", chi: chi, napAm: nap.name,
                    element: MenhElement(rawValue: nap.element)!)
    }

    /// Mệnh from a solar birth date; someone born before Tết belongs to the previous lunar year.
    public static func ofBirthDate(_ date: SolarDate) -> Menh? {
        guard let min = SolarDate.parse(CalendarData.menh.birthDateMin), date >= min, date.isInRange else { return nil }
        return ofLunarYear(LunarCalendar.solarToLunar(date).year)
    }

    /// Palette for a preference; without a mệnh the app stays red (Hỏa palette = default).
    public static func paletteFor(_ menh: Menh?, _ pref: AccentPref) -> MenhElement {
        guard let menh, pref != .macDinh else { return .hoa }
        return pref == .tuongSinh ? menh.element.generatedBy : menh.element
    }
}
