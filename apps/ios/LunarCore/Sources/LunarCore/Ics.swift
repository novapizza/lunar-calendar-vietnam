import Foundation

public struct IcsResult: Sendable {
    public let text: String
    public let totalEvents: Int
    public let skipped: [SkippedYear]
    public var skippedCount: Int { skipped.count }
}

/// RFC 5545 export of personal events (shared/design/events.md, "Export"): one VEVENT with an RRULE
/// for none, daily, weekly, solar monthly with day ≤ 28 and solar yearly except 29/2
/// (`PersonalEvents.exportsAsRule`); one VEVENT per occurrence whose start falls inside
/// startYear … startYear + yearCount - 1 for the rest (lunar repeats, solar day > 28), as the web
/// app's icsService and Android's Ics.kt. Timed events use floating local times; reminders become
/// VALARM DISPLAY.
public enum Ics {
    private static func escape(_ s: String) -> String {
        s.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: ";", with: "\\;")
            .replacingOccurrences(of: ",", with: "\\,")
            .replacingOccurrences(of: "\n", with: "\\n")
    }

    /// Folds a line at 75 UTF-8 bytes; continuation lines start with one space.
    public static func fold(_ line: String) -> String {
        var parts: [String] = []
        var current = String.UnicodeScalarView()
        var bytes = 0
        var max = 75
        for scalar in line.unicodeScalars {
            let len = String(scalar).utf8.count
            if bytes + len > max {
                parts.append(String(current))
                current = String.UnicodeScalarView()
                bytes = 0
                max = 74
            }
            current.append(scalar)
            bytes += len
        }
        parts.append(String(current))
        return parts.joined(separator: "\r\n ")
    }

    private static func ymd(_ d: SolarDate) -> String { String(format: "%04d%02d%02d", d.year, d.month, d.day) }
    private static func local(_ t: LocalDateTime) -> String { ymd(t.date) + String(format: "T%02d%02d00", t.hour, t.minute) }
    private static func time(_ s: String) -> (hour: Int, minute: Int) { parseTime(s) ?? (9, 0) }

    public static func utcStamp(_ now: Date = Date()) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let c = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: now)
        return String(format: "%04d%02d%02dT%02d%02d%02dZ", c.year!, c.month!, c.day!, c.hour!, c.minute!, c.second!)
    }

    public static func build(_ events: [LunarEvent], _ startYear: Int, _ yearCount: Int, dtstamp: String = utcStamp()) -> IcsResult {
        var vevents: [String] = []
        var skipped: [SkippedYear] = []
        for event in events {
            if PersonalEvents.exportsAsRule(event) {
                if let v = ruleEvent(event, startYear, dtstamp) { vevents.append(v) }
                continue
            }
            let (list, skip) = PersonalEvents.occurrences(event, startYear, yearCount)
            skipped += skip
            vevents += list.map { vevent(event, $0, dtstamp) }
        }
        let header = [
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Lịch Âm Việt Nam//VI", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
            fold("X-WR-CALNAME:Sự kiện âm lịch Việt Nam"),
        ].joined(separator: "\r\n")
        let text = "\(header)\r\n\(vevents.joined(separator: "\r\n"))\r\nEND:VCALENDAR"
        return IcsResult(text: text, totalEvents: vevents.count, skipped: skipped)
    }

    /// How many VEVENTs `build` writes (the export button's count).
    public static func count(_ events: [LunarEvent], _ startYear: Int, _ yearCount: Int) -> Int {
        events.reduce(0) { sum, e in
            if PersonalEvents.exportsAsRule(e) {
                return sum + (PersonalEvents.firstCandidate(e, SolarDate(startYear, 1, 1)) != nil ? 1 : 0)
            }
            return sum + PersonalEvents.occurrences(e, startYear, yearCount).list.count
        }
    }

    /// DTSTART / DTEND of the occurrence starting on [start]: dates when all day, else floating local times.
    private static func span(_ e: LunarEvent, _ start: SolarDate) -> [String] {
        let last = start.plusDays(e.span - 1)
        if e.allDay { return ["DTSTART;VALUE=DATE:\(ymd(start))", "DTEND;VALUE=DATE:\(ymd(last.plusDays(1)))"] }
        let s = time(e.startTime)
        let t = time(e.endTime)
        let from = LocalDateTime(start, s.hour, s.minute)
        var to = LocalDateTime(last, t.hour, t.minute)
        if to <= from { to = from.plusMinutes(60) }
        return ["DTSTART:\(local(from))", "DTEND:\(local(to))"]
    }

    /// "-PT15M", "PT8H", "-PT16H", "-P1D": an RFC 5545 duration of [minutes] (negative = before).
    public static func duration(_ minutes: Int) -> String {
        let sign = minutes < 0 ? "-" : ""
        var m = abs(minutes)
        if m == 0 { return "PT0M" }
        if m % 1440 == 0 { return "\(sign)P\(m / 1440)D" }
        let h = m / 60
        m %= 60
        return sign + "PT" + (h > 0 ? "\(h)H" : "") + (m > 0 ? "\(m)M" : "")
    }

    /// VALARM DISPLAY per reminder, relative to DTSTART (all day: DTSTART is midnight, the base 08:00).
    private static func alarms(_ e: LunarEvent) -> [String] {
        Array(Set(e.reminders)).sorted().flatMap { m in
            ["BEGIN:VALARM", "ACTION:DISPLAY", fold("DESCRIPTION:\(escape(e.title))"),
             "TRIGGER:\(duration(e.allDay ? 8 * 60 - m : -m))", "END:VALARM"]
        }
    }

    private static func extras(_ e: LunarEvent) -> [String] {
        guard let loc = e.location?.trimmingCharacters(in: .whitespacesAndNewlines), !loc.isEmpty else { return [] }
        return [fold("LOCATION:\(escape(loc))")]
    }

    private static let byDay = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]

    private static func trimmedNotes(_ e: LunarEvent) -> String? {
        guard let n = e.notes?.trimmingCharacters(in: .whitespacesAndNewlines), !n.isEmpty else { return nil }
        return n
    }

    /// One VEVENT with an RRULE (`PersonalEvents.exportsAsRule`); nil when the rule has no occurrence.
    private static func ruleEvent(_ e: LunarEvent, _ startYear: Int, _ dtstamp: String) -> String? {
        // The first candidate: COUNT counts from it and EXDATE removes the exceptions after.
        guard let first = PersonalEvents.firstCandidate(e, SolarDate(startYear, 1, 1)) else { return nil }
        var rule: String?
        switch e.repeatKind {
        case LunarEvent.none: rule = nil
        case LunarEvent.daily: rule = "FREQ=DAILY"
        case LunarEvent.weekly: rule = "FREQ=WEEKLY;BYDAY=" + PersonalEvents.weekdaysOf(e).sorted().map { byDay[$0] }.joined(separator: ",")
        case LunarEvent.monthly: rule = "FREQ=MONTHLY;BYMONTHDAY=\(e.lunarDay)"
        default: rule = "FREQ=YEARLY;BYMONTH=\(e.lunarMonth);BYMONTHDAY=\(e.lunarDay)"
        }
        if let r = rule {
            if let until = PersonalEvents.untilOf(e) {
                // UNTIL takes the type of DTSTART: a date, or the end of that day in floating local time.
                rule = r + ";UNTIL=" + (e.allDay ? ymd(until) : "\(ymd(until))T235959")
            } else if let count = e.count, e.start != nil {
                rule = "\(r);COUNT=\(count)"
            }
        }
        let exdates: [String] = rule == nil ? [] : e.exceptions.compactMap(SolarDate.parse).sorted().map { d in
            if e.allDay { return "EXDATE;VALUE=DATE:\(ymd(d))" }
            let t = time(e.startTime)
            return "EXDATE:\(local(LocalDateTime(d, t.hour, t.minute)))"
        }
        let description = (trimmedNotes(e).map { "\($0)\n" } ?? "") + "Tạo bởi Lịch Âm Việt Nam"
        var lines = ["BEGIN:VEVENT", fold("UID:\(e.id)@lunar-vn"), "DTSTAMP:\(dtstamp)"] + span(e, first)
        if let rule { lines.append("RRULE:\(rule)") }
        lines += exdates
        lines += [fold("SUMMARY:\(escape(e.title))"), fold("DESCRIPTION:\(escape(description))")]
        lines += extras(e) + alarms(e) + ["END:VEVENT"]
        return lines.joined(separator: "\r\n")
    }

    /// One occurrence of an expanded event (lunar repeats, solar day > 28).
    private static func vevent(_ e: LunarEvent, _ o: Occurrence, _ dtstamp: String) -> String {
        let summary: String
        let about: String
        var note = ""
        if let lunarMonth = o.lunarMonth {
            let leapTag = o.lunarLeap ? " Nhuận" : ""
            summary = "\(e.title) (Âm \(e.lunarDay)/\(lunarMonth)\(leapTag))"
            let year = o.lunarYear.map(String.init) ?? ""
            about = e.isMonthly
                ? "Sự kiện âm lịch ngày \(e.lunarDay) hằng tháng, tháng \(lunarMonth)\(leapTag) (năm âm \(year))"
                : "Sự kiện âm lịch ngày \(e.lunarDay) tháng \(e.lunarMonth)\(leapTag) (năm âm \(year))"
            if o.adjustedTo != nil { note = "\nTháng \(lunarMonth) năm nay chỉ có 29 ngày nên sự kiện được đặt vào ngày 29." }
        } else {
            summary = e.title
            about = e.isMonthly
                ? "Sự kiện dương lịch ngày \(e.lunarDay) hằng tháng"
                : "Sự kiện dương lịch ngày \(e.lunarDay) tháng \(e.lunarMonth) hằng năm"
            if let adjusted = o.adjustedTo { note = "\nTháng \(o.solar.month) chỉ có \(adjusted) ngày nên sự kiện được đặt vào ngày \(adjusted)." }
        }
        let notes = trimmedNotes(e).map { "\n\($0)" } ?? ""
        let description = escape("\(about)\(note)\(notes)\nTạo bởi Lịch Âm Việt Nam")
        let lines = ["BEGIN:VEVENT", fold("UID:\(o.uid)@lunar-vn"), "DTSTAMP:\(dtstamp)"] + span(e, o.solar)
            + [fold("SUMMARY:\(escape(summary))"), fold("DESCRIPTION:\(description)")] + extras(e) + alarms(e) + ["END:VEVENT"]
        return lines.joined(separator: "\r\n")
    }

    /// File name the web app uses.
    public static func fileName(_ startYear: Int, _ yearCount: Int) -> String {
        "lich-am-su-kien-\(startYear)-\(startYear + yearCount - 1).ics"
    }
}
