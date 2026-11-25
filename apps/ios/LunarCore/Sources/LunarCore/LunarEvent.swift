import Foundation

/// A personal event, also the built-in presets: the stored shape of shared/design/events.md (same
/// JSON as the web app's types.ts and Android's LunarEvent). Fields are named after the lunar
/// calendar because events were lunar-only at first. `lunarDay` / `lunarMonth` are the day and
/// month of the monthly / yearly rule in `calendar` (for none / daily / weekly: the start day in
/// `calendar`); `isLeapMonth` only for lunar yearly events. Every field after `createdAt` has the
/// spec's default: an absent or null value reads as the default, and encoding leaves defaults
/// out, so a lunar yearly event is written with the six old fields only, as the other apps do.
public struct LunarEvent: Codable, Hashable, Sendable, Identifiable {
    public static let lunar = "lunar"
    public static let solar = "solar"
    public static let none = "none"
    public static let daily = "daily"
    public static let weekly = "weekly"
    public static let monthly = "monthly"
    public static let yearly = "yearly"
    public static let gold = "gold"
    public static let colors = ["gold", "brand", "lunar", "good", "clash"]

    public var id: String
    public var title: String
    public var lunarDay: Int
    public var lunarMonth: Int
    public var isLeapMonth: Bool
    /// Milliseconds since 1970.
    public var createdAt: Int
    public var calendar: String
    /// The JSON field `repeat` (a Swift keyword): none, daily, weekly, monthly, yearly.
    public var repeatKind: String
    /// First day, "YYYY-MM-DD" (solar). Required for none, daily, weekly; a lower bound otherwise.
    public var start: String?
    /// Weekly: 0 = Monday … 6 = Sunday; empty = the weekday of `start`.
    public var weekdays: [Int]
    /// Last day an occurrence may start (inclusive).
    public var until: String?
    /// At most this many occurrences from `start` (removed ones count too).
    public var count: Int?
    /// Occurrence start days removed ("chỉ lần này").
    public var exceptions: [String]
    public var allDay: Bool
    public var startTime: String
    public var endTime: String
    /// An occurrence covers its start day and the days - 1 days after it.
    public var days: Int
    /// Minutes before the start time, or before 08:00 of the start day when all day.
    public var reminders: [Int]
    public var notes: String?
    public var location: String?
    /// Theme colour token: gold, brand, lunar, good, clash.
    public var color: String
    /// Only on a reminder for a built-in day (key `builtinReminders`, not `lunarEvents`): that
    /// day's ref (`UpcomingEvent.ref`). Such an event is never listed; it only fires reminders.
    public var ref: String?

    public init(
        id: String, title: String, lunarDay: Int, lunarMonth: Int, isLeapMonth: Bool, createdAt: Int,
        calendar: String = LunarEvent.lunar, repeatKind: String = LunarEvent.yearly, start: String? = nil,
        weekdays: [Int] = [], until: String? = nil, count: Int? = nil, exceptions: [String] = [],
        allDay: Bool = true, startTime: String = "09:00", endTime: String = "10:00", days: Int = 1,
        reminders: [Int] = [], notes: String? = nil, location: String? = nil, color: String = LunarEvent.gold,
        ref: String? = nil
    ) {
        self.id = id
        self.title = title
        self.lunarDay = lunarDay
        self.lunarMonth = lunarMonth
        self.isLeapMonth = isLeapMonth
        self.createdAt = createdAt
        self.calendar = calendar
        self.repeatKind = repeatKind
        self.start = start
        self.weekdays = weekdays
        self.until = until
        self.count = count
        self.exceptions = exceptions
        self.allDay = allDay
        self.startTime = startTime
        self.endTime = endTime
        self.days = days
        self.reminders = reminders
        self.notes = notes
        self.location = location
        self.color = color
        self.ref = ref
    }

    public var isSolar: Bool { calendar == LunarEvent.solar }
    public var isMonthly: Bool { repeatKind == LunarEvent.monthly }
    /// Monthly or yearly on lunar dates (the rule follows the moon).
    public var isLunarRule: Bool {
        calendar == LunarEvent.lunar && (repeatKind == LunarEvent.monthly || repeatKind == LunarEvent.yearly)
    }
    public var isRepeating: Bool { repeatKind != LunarEvent.none }
    public var span: Int { max(1, days) }

    enum CodingKeys: String, CodingKey {
        case id, title, lunarDay, lunarMonth, isLeapMonth, createdAt, calendar
        case repeatKind = "repeat"
        case start, weekdays, until, count, exceptions, allDay, startTime, endTime, days, reminders, notes, location, color, ref
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        lunarDay = try c.decode(Int.self, forKey: .lunarDay)
        lunarMonth = try c.decode(Int.self, forKey: .lunarMonth)
        isLeapMonth = try c.decodeIfPresent(Bool.self, forKey: .isLeapMonth) ?? false
        createdAt = try c.decodeIfPresent(Int.self, forKey: .createdAt) ?? 0
        calendar = try c.decodeIfPresent(String.self, forKey: .calendar) ?? LunarEvent.lunar
        repeatKind = try c.decodeIfPresent(String.self, forKey: .repeatKind) ?? LunarEvent.yearly
        start = try c.decodeIfPresent(String.self, forKey: .start)
        weekdays = try c.decodeIfPresent([Int].self, forKey: .weekdays) ?? []
        until = try c.decodeIfPresent(String.self, forKey: .until)
        count = try c.decodeIfPresent(Int.self, forKey: .count)
        exceptions = try c.decodeIfPresent([String].self, forKey: .exceptions) ?? []
        allDay = try c.decodeIfPresent(Bool.self, forKey: .allDay) ?? true
        startTime = try c.decodeIfPresent(String.self, forKey: .startTime) ?? "09:00"
        endTime = try c.decodeIfPresent(String.self, forKey: .endTime) ?? "10:00"
        days = try c.decodeIfPresent(Int.self, forKey: .days) ?? 1
        reminders = try c.decodeIfPresent([Int].self, forKey: .reminders) ?? []
        notes = try c.decodeIfPresent(String.self, forKey: .notes)
        location = try c.decodeIfPresent(String.self, forKey: .location)
        color = try c.decodeIfPresent(String.self, forKey: .color) ?? LunarEvent.gold
        ref = try c.decodeIfPresent(String.self, forKey: .ref)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encode(lunarDay, forKey: .lunarDay)
        try c.encode(lunarMonth, forKey: .lunarMonth)
        try c.encode(isLeapMonth, forKey: .isLeapMonth)
        try c.encode(createdAt, forKey: .createdAt)
        if calendar != LunarEvent.lunar { try c.encode(calendar, forKey: .calendar) }
        if repeatKind != LunarEvent.yearly { try c.encode(repeatKind, forKey: .repeatKind) }
        try c.encodeIfPresent(start, forKey: .start)
        if !weekdays.isEmpty { try c.encode(weekdays, forKey: .weekdays) }
        try c.encodeIfPresent(until, forKey: .until)
        try c.encodeIfPresent(count, forKey: .count)
        if !exceptions.isEmpty { try c.encode(exceptions, forKey: .exceptions) }
        if !allDay { try c.encode(allDay, forKey: .allDay) }
        if startTime != "09:00" { try c.encode(startTime, forKey: .startTime) }
        if endTime != "10:00" { try c.encode(endTime, forKey: .endTime) }
        if days != 1 { try c.encode(days, forKey: .days) }
        if !reminders.isEmpty { try c.encode(reminders, forKey: .reminders) }
        try c.encodeIfPresent(notes, forKey: .notes)
        try c.encodeIfPresent(location, forKey: .location)
        if color != LunarEvent.gold { try c.encode(color, forKey: .color) }
        try c.encodeIfPresent(ref, forKey: .ref)
    }
}

/// One note of the old "Ghi chú" card (key `userNotes`, web and Android only).
public struct UserNote: Codable, Hashable, Sendable {
    public let id: String
    public let date: String
    public let content: String
    public let createdAt: Int

    public init(id: String, date: String, content: String, createdAt: Int) {
        self.id = id
        self.date = date
        self.content = content
        self.createdAt = createdAt
    }
}

/// What an upcoming event is; holidays include mồng 1 and rằm. `personal`: one of the user's own events.
public enum EventKind: String, Sendable {
    case holiday, observance, observanceLunar, festival, personal
}

/// One row of the Sự kiện list.
public struct UpcomingEvent: Hashable, Sendable {
    public let solarDate: SolarDate
    public let title: String
    public let lunarDay: Int?
    public let lunarMonth: Int?
    public let lunar: Bool
    public let daysUntil: Int
    public var kind: EventKind = .holiday
    /// Personal events: the event's id, colour token and time label (nil when all day).
    public var eventId: String? = nil
    public var color: String? = nil
    public var time: String? = nil
    /// Built-in days: what a reminder for this day is keyed by: its title, or "Mồng 1" / "Ngày rằm"
    /// for every month's mồng 1 / rằm (shared/design/events.md, "Reminders for built-in days").
    public var ref: String? = nil

    public init(
        solarDate: SolarDate, title: String, lunarDay: Int?, lunarMonth: Int?, lunar: Bool, daysUntil: Int,
        kind: EventKind = .holiday, eventId: String? = nil, color: String? = nil, time: String? = nil, ref: String? = nil
    ) {
        self.solarDate = solarDate
        self.title = title
        self.lunarDay = lunarDay
        self.lunarMonth = lunarMonth
        self.lunar = lunar
        self.daysUntil = daysUntil
        self.kind = kind
        self.eventId = eventId
        self.color = color
        self.time = time
        self.ref = ref
    }
}

/// Built-in event groups of shared/data/presets.json as LunarEvents.
public func presetEvents(_ enabled: Set<String>) -> [LunarEvent] {
    CalendarData.presets.presets.filter { enabled.contains($0.id) }.flatMap { p in
        p.events.map { LunarEvent(id: $0.id, title: $0.title, lunarDay: $0.lunarDay, lunarMonth: $0.lunarMonth, isLeapMonth: false, createdAt: 0) }
    }
}
