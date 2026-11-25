import Foundation

/// One occurrence of a personal event, expanded for the calendar export.
public struct Occurrence: Hashable, Sendable {
    public let solar: SolarDate
    /// Stable per occurrence: the event id plus the year (and month) it belongs to.
    public let uid: String
    /// Lunar events: the lunar month, leap flag and lunar year the occurrence stands for.
    public var lunarMonth: Int? = nil
    public var lunarLeap: Bool = false
    public var lunarYear: Int? = nil
    /// The day was moved to the month's last day (lunar 30 → 29, solar 31 → 30, 29/2 → 28/2).
    public var adjustedTo: Int? = nil
}

/// An event covering a day: the event and the start day of its occurrence.
public struct DayEvent: Hashable, Sendable {
    public let event: LunarEvent
    public let start: SolarDate
}

/// A reminder of one occurrence: fires `minutes` before the reminder base, at `at` (local time).
public struct ReminderFire: Hashable, Sendable {
    public let event: LunarEvent
    public let start: SolarDate
    public let minutes: Int
    public let at: LocalDateTime
}

/// One occurrence skipped during export (a leap month that does not exist that year, …).
public struct SkippedYear: Hashable, Sendable {
    public let title: String
    public let lunarYear: Int
    public let reason: String
}

/// The user's own events: the rules of shared/design/events.md, ported from Android's
/// PersonalEvents.kt (shared/test-vectors/events.json holds dates every app must reproduce).
/// Days are handled as Julian day numbers.
public enum PersonalEvents {
    /// One note of the old "Ghi chú" card as a one-off all-day event on its day (events.md, "Old notes").
    public static func noteToEvent(_ n: UserNote) -> LunarEvent {
        let text = n.content.trimmingCharacters(in: .whitespacesAndNewlines)
        let first = text.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline).first
            .map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
        let title: String
        if first.isEmpty {
            title = "Ghi chú"
        } else if first.count > 60 {
            title = String(first.prefix(59)).replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression) + "…"
        } else {
            title = first
        }
        let d = SolarDate.parse(n.date)
        return LunarEvent(
            id: n.id, title: title, lunarDay: d?.day ?? 1, lunarMonth: d?.month ?? 1, isLeapMonth: false, createdAt: n.createdAt,
            calendar: LunarEvent.solar, repeatKind: LunarEvent.none, start: n.date,
            notes: text == title ? nil : text
        )
    }

    private static let firstJd = LunarCalendar.firstDay.jd
    private static let lastJd = LunarCalendar.lastDay.jd

    // MARK: fields with their defaults

    public static func startOf(_ e: LunarEvent) -> SolarDate? { e.start.flatMap(SolarDate.parse) }
    public static func untilOf(_ e: LunarEvent) -> SolarDate? { e.until.flatMap(SolarDate.parse) }

    /// Weekly: the chosen weekdays (0 = Monday), else the weekday of the start day.
    public static func weekdaysOf(_ e: LunarEvent) -> [Int] {
        e.weekdays.isEmpty ? (startOf(e).map { [$0.weekdayIndex] } ?? []) : e.weekdays
    }

    // MARK: occurrences

    /// A lunar month of lunar year [year]: first day (Julian day number) and length (29 or 30).
    private struct Month { let firstJd: Int; let days: Int }

    private static func monthsWithLength(_ year: Int) -> [Month] {
        guard let starts = LunarCalendar.monthsOf(year) else { return [] }
        let nextJd = LunarCalendar.monthsOf(year + 1)?.first?.jd
        var out: [Month] = []
        for (i, m) in starts.enumerated() {
            guard let end = i + 1 < starts.count ? starts[i + 1].jd : nextJd else { continue }
            out.append(Month(firstJd: m.jd, days: end - m.jd))
        }
        return out
    }

    /// The rule's candidate start days in [from, to], in order (start, until, count, exceptions not
    /// applied). Lazy: a count only reads as far as it needs.
    private static func candidates(_ e: LunarEvent, _ from: Int, _ to: Int) -> AnySequence<Int> {
        let lo = max(from, firstJd)
        let hi = min(to, lastJd)
        guard lo <= hi else { return AnySequence([]) }
        let inRange = { (d: Int) in d >= lo && d <= hi }
        switch e.repeatKind {
        case LunarEvent.none:
            guard let s = startOf(e)?.jd, inRange(s) else { return AnySequence([]) }
            return AnySequence([s])
        case LunarEvent.daily, LunarEvent.weekly:
            let daily = e.repeatKind == LunarEvent.daily
            let wd = Set(weekdaysOf(e))
            return AnySequence((lo...hi).lazy.filter { daily || wd.contains($0 % 7) })
        default:
            let ylo = SolarDate(jd: lo).year
            let yhi = SolarDate(jd: hi).year
            if e.isSolar {
                let months = e.isMonthly ? Array(1...12) : [e.lunarMonth]
                return AnySequence((ylo...yhi).lazy.flatMap { y in
                    months.map { m in SolarDate(y, m, min(e.lunarDay, daysInMonth(y, m))).jd }
                }.filter(inRange))
            }
            // Lunar: lunar year ylo - 1 may still have months in solar year ylo.
            return AnySequence(((ylo - 1)...yhi).lazy.flatMap { ly -> [Int] in
                if e.isMonthly {
                    return monthsWithLength(ly).map { $0.firstJd + min(e.lunarDay, $0.days) - 1 }
                }
                if case let .ok(solar, _) = Conversion.lunarToSolar(e.lunarDay, e.lunarMonth, ly, e.isLeapMonth) {
                    return [solar.jd]
                }
                return []
            }.filter(inRange))
        }
    }

    private static func startsBetween(_ e: LunarEvent, _ from: Int, _ to: Int) -> [Int] {
        let start = startOf(e)?.jd
        let until = untilOf(e)?.jd
        var lo = from
        var hi = to
        if let start { lo = max(lo, start) }
        if let until { hi = min(hi, until) }
        guard lo <= hi else { return [] }
        var list: [Int]
        if let start, let count = e.count, count > 0 {
            // The first `count` candidates from the start.
            list = candidates(e, start, until ?? lastJd).prefix(count).filter { $0 >= lo && $0 <= hi }
        } else {
            list = Array(candidates(e, lo, hi))
        }
        if e.exceptions.isEmpty { return list }
        let skip = Set(e.exceptions)
        list.removeAll { skip.contains(SolarDate(jd: $0).iso) }
        return list
    }

    /// Start days of the occurrences of [e] that start in [from] … [to], in order: the candidates
    /// bounded by start and until, the first `count` from start, without exceptions.
    public static func occurrenceStarts(_ e: LunarEvent, _ from: SolarDate, _ to: SolarDate) -> [SolarDate] {
        startsBetween(e, from.jd, to.jd).map { SolarDate(jd: $0) }
    }

    /// Start days of the occurrences covering some day of [from] … [to] (an occurrence covers `days` days).
    public static func occurrencesCovering(_ e: LunarEvent, _ from: SolarDate, _ to: SolarDate) -> [SolarDate] {
        startsBetween(e, from.jd - (e.span - 1), to.jd).map { SolarDate(jd: $0) }
    }

    /// The events covering [day]: all-day ones first (list order), then timed ones by start time.
    public static func eventsOnDay(_ events: [LunarEvent], _ day: SolarDate) -> [DayEvent] {
        guard !events.isEmpty, day.isInRange else { return [] }
        var out: [DayEvent] = []
        for e in events { for s in occurrencesCovering(e, day, day) { out.append(DayEvent(event: e, start: s)) } }
        return stableSorted(out) { ($0.event.allDay ? "" : $0.event.startTime) < ($1.event.allDay ? "" : $1.event.startTime) }
    }

    /// The events covering [date] (eventsOnDay without the start days).
    public static func on(_ events: [LunarEvent], _ date: SolarDate) -> [LunarEvent] { eventsOnDay(events, date).map(\.event) }

    /// Whether some occurrence of [e] covers [date].
    public static func occursOn(_ e: LunarEvent, _ date: SolarDate) -> Bool { !occurrencesCovering(e, date, date).isEmpty }

    /// The next occurrence of [e] starting on or after [day], or nil (scans up to 2099).
    public static func nextOccurrence(_ e: LunarEvent, _ day: SolarDate) -> SolarDate? {
        var y = day.year
        while y <= LunarCalendar.lastDay.year {
            let from = max(day, SolarDate(y, 1, 1))
            if let s = occurrenceStarts(e, from, SolarDate(y + 4, 12, 31)).first { return s }
            y += 5
        }
        return nil
    }

    /// The events starting on [d] as Sự kiện list entries (first on their day).
    public static func upcoming(_ events: [LunarEvent], _ d: SolarDate, _ daysUntil: Int) -> [UpcomingEvent] {
        let own = eventsOnDay(events, d).filter { $0.start == d }
        if own.isEmpty { return [] }
        let l = LunarCalendar.solarToLunar(d)
        return own.map { de in
            let e = de.event
            let time = e.allDay ? nil : timeLabel(e)
            return e.isLunarRule
                ? UpcomingEvent(solarDate: d, title: e.title, lunarDay: l.day, lunarMonth: l.month, lunar: true, daysUntil: daysUntil,
                                kind: .personal, eventId: e.id, color: e.color, time: time)
                : UpcomingEvent(solarDate: d, title: e.title, lunarDay: nil, lunarMonth: nil, lunar: false, daysUntil: daysUntil,
                                kind: .personal, eventId: e.id, color: e.color, time: time)
        }
    }

    // MARK: words

    public static let weekdayShort = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]
    public static let weekdayNames = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"]
    private static func fmt(_ d: SolarDate) -> String { "\(d.day)/\(d.month)/\(d.year)" }

    /// "Cả ngày", "09:00 – 10:00".
    public static func timeLabel(_ e: LunarEvent) -> String { e.allDay ? "Cả ngày" : "\(e.startTime) – \(e.endTime)" }

    /// The repeat in words: "Hằng năm vào 10/3 âm · đến 31/12/2030", "Hằng tuần vào T2, T4 · 10 lần", "Không lặp · 25/11/2026".
    public static func describe(_ e: LunarEvent) -> String {
        let lunar = e.calendar == LunarEvent.lunar
        var rule: String
        switch e.repeatKind {
        case LunarEvent.none: return startOf(e).map { "Không lặp · \(fmt($0))" } ?? "Không lặp"
        case LunarEvent.daily: rule = "Hằng ngày"
        case LunarEvent.weekly: rule = "Hằng tuần vào " + weekdaysOf(e).sorted().map { weekdayShort[$0] }.joined(separator: ", ")
        case LunarEvent.monthly: rule = "Hằng tháng vào ngày \(e.lunarDay)\(lunar ? " âm" : "")"
        default: rule = "Hằng năm vào \(e.lunarDay)/\(e.lunarMonth)\(lunar ? (e.isLeapMonth ? " nhuận âm" : " âm") : "")"
        }
        if let until = untilOf(e) {
            rule += " · đến \(fmt(until))"
        } else if let count = e.count, e.start != nil {
            rule += " · \(count) lần"
        }
        return rule
    }

    /// Reminder options: minutes and label; all-day ones are counted back from 08:00 of the day.
    public static let remindersTimed: [(minutes: Int, label: String)] = [
        (0, "Đúng giờ"), (5, "5 phút trước"), (10, "10 phút trước"), (15, "15 phút trước"), (30, "30 phút trước"),
        (60, "1 giờ trước"), (120, "2 giờ trước"), (1440, "1 ngày trước"), (2880, "2 ngày trước"), (10080, "1 tuần trước"),
    ]
    public static let remindersAllDay: [(minutes: Int, label: String)] = [
        (0, "Vào ngày, lúc 8:00"), (1440, "1 ngày trước, lúc 8:00"), (2880, "2 ngày trước, lúc 8:00"), (10080, "1 tuần trước, lúc 8:00"),
    ]

    public static func reminderOptions(_ allDay: Bool) -> [(minutes: Int, label: String)] { allDay ? remindersAllDay : remindersTimed }

    public static func reminderLabel(_ allDay: Bool, _ minutes: Int) -> String {
        reminderOptions(allDay).first { $0.minutes == minutes }?.label ?? "\(minutes) phút trước"
    }

    // MARK: reminders

    /// The moment [minutes] before the reminder base of the occurrence starting on [start].
    public static func fireTime(_ e: LunarEvent, _ start: SolarDate, _ minutes: Int) -> LocalDateTime {
        let base = e.allDay ? (hour: 8, minute: 0) : (parseTime(e.startTime) ?? (hour: 9, minute: 0))
        return LocalDateTime(start, base.hour, base.minute).plusMinutes(-minutes)
    }

    /// Reminders of [events] firing in [from, to), in time order.
    public static func remindersBetween(_ events: [LunarEvent], _ from: LocalDateTime, _ to: LocalDateTime) -> [ReminderFire] {
        var out: [ReminderFire] = []
        for e in events {
            guard let longest = e.reminders.max() else { continue }
            // Occurrences whose reminders can fall in the window: starts up to `longest` minutes after it.
            let lo = from.date.jd - 1
            let hi = to.plusMinutes(longest).date.jd + 1
            for s in startsBetween(e, lo, hi) {
                let start = SolarDate(jd: s)
                for m in e.reminders {
                    let at = fireTime(e, start, m)
                    if at >= from && at < to { out.append(ReminderFire(event: e, start: start, minutes: m, at: at)) }
                }
            }
        }
        return stableSorted(out) { $0.at < $1.at }
    }

    /// The notification text of an occurrence: "Hôm nay · 09:00 – 10:00", "Ngày mai · Cả ngày",
    /// "Thứ Năm 26/11 · Cả ngày", plus " · 10/10 âm" for lunar repeats.
    public static func reminderText(_ e: LunarEvent, _ start: SolarDate, _ today: SolarDate) -> String {
        let day: String
        switch start.jd - today.jd {
        case 0: day = "Hôm nay"
        case 1: day = "Ngày mai"
        default: day = "\(weekdayNames[start.weekdayIndex]) \(start.day)/\(start.month)"
        }
        var lunar = ""
        if e.isLunarRule && start.isInRange {
            let l = LunarCalendar.solarToLunar(start)
            lunar = " · \(l.day)/\(l.month) âm"
        }
        return "\(day) · \(timeLabel(e))\(lunar)"
    }

    // MARK: export

    /// Whether the export writes [e] as one VEVENT with an RRULE (solar rules RFC 5545 can say exactly).
    public static func exportsAsRule(_ e: LunarEvent) -> Bool {
        switch e.repeatKind {
        case LunarEvent.none, LunarEvent.daily, LunarEvent.weekly: return e.start != nil
        default:
            if !e.isSolar { return false }
            if e.isMonthly { return e.lunarDay <= 28 }
            return !(e.lunarMonth == 2 && e.lunarDay == 29)
        }
    }

    /// The first candidate start day on or after [from] (exceptions not applied: COUNT and EXDATE count them).
    public static func firstCandidate(_ e: LunarEvent, _ from: SolarDate) -> SolarDate? {
        let lo = startOf(e)?.jd ?? from.jd
        return candidates(e, lo, untilOf(e)?.jd ?? lastJd).first { _ in true }.map { SolarDate(jd: $0) }
    }

    /// Every occurrence of [e] whose start falls in [startYear] … [startYear] + [yearCount] - 1, with
    /// the lunar month and year it stands for; lunar yearly leap events also return the lunar years
    /// of the range that lack the leap month (skipped).
    public static func occurrences(_ e: LunarEvent, _ startYear: Int, _ yearCount: Int) -> (list: [Occurrence], skipped: [SkippedYear]) {
        let endYear = startYear + yearCount - 1
        let starts = occurrenceStarts(e, SolarDate(startYear, 1, 1), SolarDate(endYear, 12, 31))
        let list = starts.map { s -> Occurrence in
            if e.isLunarRule {
                let l = LunarCalendar.solarToLunar(s)
                let uid = e.isMonthly ? "\(e.id)-\(l.year)-\(l.month)\(l.leap ? "n" : "")" : "\(e.id)-\(l.year)"
                return Occurrence(solar: s, uid: uid, lunarMonth: l.month, lunarLeap: l.leap, lunarYear: l.year,
                                  adjustedTo: l.day != e.lunarDay ? l.day : nil)
            }
            switch e.repeatKind {
            case LunarEvent.monthly:
                return Occurrence(solar: s, uid: "\(e.id)-\(s.year)-\(s.month)", adjustedTo: s.day != e.lunarDay ? s.day : nil)
            case LunarEvent.yearly:
                return Occurrence(solar: s, uid: "\(e.id)-\(s.year)", adjustedTo: s.day != e.lunarDay ? s.day : nil)
            default:
                return Occurrence(solar: s, uid: "\(e.id)-\(s)")
            }
        }
        var skipped: [SkippedYear] = []
        if e.calendar == LunarEvent.lunar && e.repeatKind == LunarEvent.yearly && e.isLeapMonth {
            for ly in stride(from: startYear, through: endYear, by: 1) {
                if case let .fail(reason) = Conversion.lunarToSolar(e.lunarDay, e.lunarMonth, ly, true) {
                    skipped.append(SkippedYear(title: e.title, lunarYear: ly, reason: reason))
                }
            }
        }
        return (list, skipped)
    }
}

/// Sorts keeping the order of equal elements (Swift's `sorted` is not guaranteed stable; the
/// Kotlin port relies on `sortedBy` being stable).
func stableSorted<T>(_ items: [T], by less: (T, T) -> Bool) -> [T] {
    items.enumerated().sorted { a, b in
        less(a.element, b.element) || (!less(b.element, a.element) && a.offset < b.offset)
    }.map(\.element)
}

/// Reminders for built-in days (shared/design/events.md, "Reminders for built-in days"): one
/// event per built-in day ref, stored under `builtinReminders`, with the personal events' repeat,
/// end and reminder fields. It is never listed; the day's row shows a bell instead.
public enum BuiltinReminders {
    /// Refs of every month's mồng 1 / rằm, whose reminder may repeat monthly (`UpcomingEvent.ref`).
    public static let mong1 = "Mồng 1"
    public static let ram = "Ngày rằm"

    /// The repeats offered for a built-in day, the default first: every month's mồng 1 / rằm also monthly.
    public static func repeats(_ ref: String) -> [String] {
        ref == mong1 || ref == ram
            ? [LunarEvent.monthly, LunarEvent.yearly, LunarEvent.none]
            : [LunarEvent.yearly, LunarEvent.none]
    }

    /// The reminder kept for a built-in day, if any.
    public static func of(_ reminders: [LunarEvent], _ ref: String) -> LunarEvent? { reminders.first { $0.ref == ref } }

    /// Whether a built-in day's occurrence on [day] has a reminder.
    public static func reminds(_ reminders: [LunarEvent], _ ref: String?, _ day: SolarDate) -> Bool {
        guard let ref, let r = of(reminders, ref) else { return false }
        return !r.reminders.isEmpty && !PersonalEvents.occurrenceStarts(r, day, day).isEmpty
    }
}
