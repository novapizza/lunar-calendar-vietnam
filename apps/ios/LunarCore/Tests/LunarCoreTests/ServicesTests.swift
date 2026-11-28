import XCTest
@testable import LunarCore

/// Ported from Android's ServicesTest.kt, same cases and expected values.
final class ServicesTests: XCTestCase {
    func testMonthGridStartsMondayEndsSunday() {
        let g = monthGrid(2026, 11)
        XCTAssertEqual(g.first, SolarDate(2026, 10, 26))
        XCTAssertEqual(g.last, SolarDate(2026, 12, 6))
        XCTAssertEqual(g.count % 7, 0)
    }

    func testSubtitleAndWeek() {
        XCTAssertEqual(lunarMonthSubtitle(2026, 11), "Tháng 9 – 10 âm lịch · Bính Ngọ")
        XCTAssertEqual(SolarDate(2026, 11, 21).weekOfYear, 47)
    }

    func testUpcomingStartsWithNamedOrLunarDays() {
        let ev = Upcoming.events(SolarDate(2026, 11, 21))
        XCTAssertEqual(ev.count, 12)
        // 21/11/2026 is 13/10 âm; the next lunar day of note is rằm tháng 10 (23/11), which has
        // its own name, Tết Hạ nguyên, in place of "Ngày rằm tháng 10".
        XCTAssertEqual(ev.first?.title, "Tết Hạ nguyên")
        XCTAssertEqual(ev.first?.solarDate, SolarDate(2026, 11, 23))
    }

    func testHolidaysObservancesAndFestivals() {
        // 27/7 is a day off only from 2027; before that it is a commemorative day.
        let jul2026 = DayInfo.of(SolarDate(2026, 7, 27))
        XCTAssertNil(jul2026.holiday)
        XCTAssertEqual(jul2026.observance, "Ngày Thương binh – Liệt sĩ")
        let jul2027 = DayInfo.of(SolarDate(2027, 7, 27))
        XCTAssertEqual(jul2027.holiday, "Ngày Thương binh – Liệt sĩ")
        XCTAssertNil(jul2027.observance)
        // 24/11 is a day off from 2026 (Nghị quyết 28/2026/QH16).
        XCTAssertEqual(DayInfo.of(SolarDate(2026, 11, 24)).holiday, "Ngày Văn hóa Việt Nam")
        XCTAssertNil(DayInfo.of(SolarDate(2025, 11, 24)).holiday)
        XCTAssertEqual(DayInfo.of(SolarDate(2026, 5, 19)).observance, "Ngày sinh Chủ tịch Hồ Chí Minh")
        // 18/11/2026 is 10/10 âm: the Ông Hoàng Mười festival, listed as a festival.
        let nov18 = DayInfo.of(SolarDate(2026, 11, 18))
        XCTAssertEqual(nov18.lunar.day, 10)
        XCTAssertEqual(nov18.lunar.month, 10)
        XCTAssertEqual(nov18.observance, "Lễ hội đền Ông Hoàng Mười")
        let ev = Upcoming.month(SolarDate(2026, 11, 1), 0).filter { $0.solarDate == SolarDate(2026, 11, 18) }
        XCTAssertTrue(ev.contains { $0.kind == .festival && $0.title == "Lễ hội đền Ông Hoàng Mười" }, "\(ev)")
    }

    func testConversionFallsBackToDay29() {
        // Every lunar month has 29 or 30 days; day 30 must always resolve (exactly or adjusted).
        for y in 2020...2030 {
            for m in 1...12 {
                guard case .ok = Conversion.lunarToSolar(30, m, y, false) else { return XCTFail("\(m)/\(y)") }
            }
        }
        XCTAssertEqual(Conversion.lunarToSolar(1, 1, 2026, true), .fail("leap_month_not_found"))
    }

    func testIcsFoldsAndCounts() {
        let e = LunarEvent.make("e1", "Giỗ ông nội, có dấu phẩy; và chấm phẩy", 10, 3)
        let r = Ics.build([e], 2026, 3, dtstamp: "20261121T000000Z")
        XCTAssertEqual(r.totalEvents, 3)
        XCTAssertTrue(r.text.hasPrefix("BEGIN:VCALENDAR\r\n"))
        XCTAssertTrue(r.text.contains("SUMMARY:Giỗ ông nội\\, có dấu phẩy\\; và chấm phẩy (Âm 10/3)"))
        for line in r.text.components(separatedBy: "\r\n") {
            XCTAssertLessThanOrEqual(line.utf8.count, 75, line)
        }
    }

    private func ev(_ day: Int, _ month: Int = 1, calendar: String = LunarEvent.lunar, repeatKind: String = LunarEvent.yearly, leap: Bool = false) -> LunarEvent {
        LunarEvent.make("x", "t", day, month, leap, 0, calendar: calendar, repeatKind: repeatKind)
    }

    /// Days of 2024-2028 an event falls on, scanned one by one.
    private func scan(_ e: LunarEvent) -> [SolarDate] {
        var out: [SolarDate] = []
        var d = SolarDate(2024, 1, 1)
        while d.year <= 2028 {
            if PersonalEvents.occursOn(e, d) { out.append(d) }
            d = d.plusDays(1)
        }
        return out
    }

    func testPersonalEventsMatchTheirOccurrences() {
        // Same cases and counts as the web app's personalEvents.ts (measured in the browser 2026-11-23).
        let cases: [(e: LunarEvent, count: Int, adjusted: Int, first: SolarDate)] = [
            (ev(10, 3), 5, 0, SolarDate(2024, 4, 18)),
            (ev(30, repeatKind: LunarEvent.monthly), 62, 30, SolarDate(2024, 1, 10)),
            (ev(15, repeatKind: LunarEvent.monthly), 62, 0, SolarDate(2024, 1, 25)),
            (ev(15, 6, leap: true), 1, 0, SolarDate(2025, 8, 8)),
            (ev(29, 2, calendar: LunarEvent.solar), 5, 3, SolarDate(2024, 2, 29)),
            (ev(31, calendar: LunarEvent.solar, repeatKind: LunarEvent.monthly), 60, 25, SolarDate(2024, 1, 31)),
        ]
        for c in cases {
            let days = scan(c.e)
            let list = PersonalEvents.occurrences(c.e, 2024, 5).list
            XCTAssertEqual(list.map(\.solar), days, "\(c.e)")
            XCTAssertEqual(days.count, c.count, "\(c.e)")
            XCTAssertEqual(list.filter { $0.adjustedTo != nil }.count, c.adjusted, "\(c.e)")
            XCTAssertEqual(days.first, c.first, "\(c.e)")
        }
        XCTAssertEqual(PersonalEvents.occurrences(ev(15, 6, leap: true), 2024, 5).skipped.map(\.lunarYear), [2024, 2026, 2027, 2028])
    }

    func testPersonalEventsKeepTheOldStoredShape() throws {
        let old = try decodeEvent(json: #"{"id":"a","title":"Giỗ","lunarDay":10,"lunarMonth":3,"isLeapMonth":false,"createdAt":1}"#)
        XCTAssertEqual(old.calendar, LunarEvent.lunar)
        XCTAssertEqual(old.repeatKind, LunarEvent.yearly)
        // Lunar yearly events are saved without the new fields, as before (and as the other apps).
        let encoded = try encodeEvent(old)
        XCTAssertEqual(Set(encoded.keys), ["id", "title", "lunarDay", "lunarMonth", "isLeapMonth", "createdAt"])
        XCTAssertEqual(try decodeEvent(encoded), old)
        XCTAssertTrue(old.allDay)
        XCTAssertEqual(old.reminders, [])
        var solar = old
        solar.calendar = LunarEvent.solar
        solar.repeatKind = LunarEvent.monthly
        XCTAssertEqual(try decodeEvent(try encodeEvent(solar)), solar)
        XCTAssertEqual(try encodeEvent(solar)["repeat"] as? String, "monthly")
        var full = solar
        full.repeatKind = LunarEvent.weekly
        full.start = "2026-11-23"; full.weekdays = [0, 3]; full.count = 4; full.exceptions = ["2026-11-26"]
        full.allDay = false; full.startTime = "07:30"; full.days = 2; full.reminders = [0, 30]
        full.notes = "n"; full.location = "l"; full.color = "clash"
        XCTAssertEqual(try decodeEvent(try encodeEvent(full)), full)
        // The web may write nulls for absent values: they read as the defaults.
        let fromWeb = try decodeEvent(json: #"{"id":"a","title":"t","lunarDay":1,"lunarMonth":1,"isLeapMonth":false,"createdAt":1,"allDay":null,"start":null,"reminders":null,"color":"brand","extra":1}"#)
        XCTAssertTrue(fromWeb.allDay)
        XCTAssertEqual(fromWeb.color, "brand")
    }

    func testPersonalEventsInListAndExport() {
        let today = SolarDate(2026, 11, 23)
        let mine = [LunarEvent.make("m", "Sinh nhật mẹ", 25, 11, calendar: LunarEvent.solar)]
        let nov = Upcoming.month(today, 0, personal: mine).filter { $0.kind == .personal }
        XCTAssertEqual(nov.map(\.solarDate), [SolarDate(2026, 11, 25)])
        XCTAssertEqual(nov.map(\.daysUntil), [2])
        // Solar yearly: one VEVENT with an RRULE; lunar monthly: one per occurrence (24 in 2 years).
        let both = mine + [ev(10, repeatKind: LunarEvent.monthly)]
        let r = Ics.build(both, 2026, 2, dtstamp: "20261121T000000Z")
        XCTAssertEqual(r.totalEvents, 1 + 24)
        XCTAssertEqual(Ics.count(both, 2026, 2), r.totalEvents)
        XCTAssertTrue(r.text.contains("UID:m@lunar-vn\r\nDTSTAMP:20261121T000000Z\r\nDTSTART;VALUE=DATE:20261125\r\nDTEND;VALUE=DATE:20261126\r\nRRULE:FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=25\r\n"), r.text)
        XCTAssertTrue(r.text.contains("SUMMARY:Sinh nhật mẹ\r\n"))
        XCTAssertTrue(r.text.contains("SUMMARY:t (Âm 10/12)"))
    }

    func testIcsOldLunarYearlyUnchanged() {
        // The export of an old stored event, byte for byte as the other apps write it.
        let e = LunarEvent.make("g", "Giỗ", 10, 3)
        let r = Ics.build([e], 2026, 1, dtstamp: "20261121T000000Z")
        let expected = [
            "BEGIN:VEVENT", "UID:g-2026@lunar-vn", "DTSTAMP:20261121T000000Z", "DTSTART;VALUE=DATE:20260426", "DTEND;VALUE=DATE:20260427",
            "SUMMARY:Giỗ (Âm 10/3)", "DESCRIPTION:Sự kiện âm lịch ngày 10 tháng 3 (năm âm 2026)\\nTạo\r\n  bởi Lịch Âm Việt Nam", "END:VEVENT",
        ].joined(separator: "\r\n")
        XCTAssertTrue(r.text.contains(expected), r.text)
        XCTAssertEqual(r.totalEvents, 1)
    }

    func testIcsRulesTimesAndAlarms() {
        let weekly = LunarEvent.make(
            "w", "Họp", 23, 11, calendar: LunarEvent.solar, repeatKind: LunarEvent.weekly, start: "2026-11-23", weekdays: [3, 0],
            until: "2026-12-31", exceptions: ["2026-12-03"], allDay: false, startTime: "09:30", endTime: "10:15",
            reminders: [15, 1440], notes: "Phòng 2", location: "Hà Nội"
        )
        let allDay = LunarEvent.make("a", "Đi chơi", 24, 12, calendar: LunarEvent.solar, repeatKind: LunarEvent.daily, start: "2026-12-24",
                                     count: 3, days: 2, reminders: [0, 1440])
        let lunar = LunarEvent.make("l", "Rằm", 15, 1, calendar: LunarEvent.lunar, repeatKind: LunarEvent.monthly, allDay: false,
                                    startTime: "18:00", endTime: "19:00")
        let text = Ics.build([weekly, allDay, lunar], 2026, 1, dtstamp: "20261121T000000Z").text
        for s in [
            "DTSTART:20261123T093000\r\nDTEND:20261123T101500\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261231T235959\r\nEXDATE:20261203T093000",
            "DESCRIPTION:Phòng 2\\nTạo bởi Lịch Âm Việt Nam\r\nLOCATION:Hà Nội\r\nBEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:Họp\r\nTRIGGER:-PT15M\r\nEND:VALARM",
            "TRIGGER:-P1D",
            "DTSTART;VALUE=DATE:20261224\r\nDTEND;VALUE=DATE:20261226\r\nRRULE:FREQ=DAILY;COUNT=3",
            "TRIGGER:PT8H\r\n", "TRIGGER:-PT16H\r\n",
            "UID:l-2026-1@lunar-vn\r\nDTSTAMP:20261121T000000Z\r\nDTSTART:20260303T180000\r\nDTEND:20260303T190000",
        ] {
            XCTAssertTrue(text.contains(s), s)
        }
    }

    func testPersonalEventsWordsAndReminders() {
        let e = LunarEvent.make("w", "Họp", 23, 11, calendar: LunarEvent.solar, repeatKind: LunarEvent.weekly, start: "2026-11-23", weekdays: [3, 0], count: 10)
        XCTAssertEqual(PersonalEvents.describe(e), "Hằng tuần vào T2, T5 · 10 lần")
        XCTAssertEqual(PersonalEvents.describe(LunarEvent.make("g", "Giỗ", 10, 3)), "Hằng năm vào 10/3 âm")
        XCTAssertEqual(PersonalEvents.describe(LunarEvent.make("g", "Giỗ", 1, 3, repeatKind: LunarEvent.monthly, until: "2030-12-31")),
                       "Hằng tháng vào ngày 1 âm · đến 31/12/2030")
        XCTAssertEqual(PersonalEvents.describe(LunarEvent.make("n", "t", 24, 12, calendar: LunarEvent.solar, repeatKind: LunarEvent.none, start: "2026-12-24")),
                       "Không lặp · 24/12/2026")
        let today = SolarDate(2026, 11, 25)
        // 25/11/2026 is 17/10 âm (23/11 is rằm tháng 10).
        XCTAssertEqual(PersonalEvents.reminderText(LunarEvent.make("g", "Giỗ", 17, 10), today, today), "Hôm nay · Cả ngày · 17/10 âm")
        var timed = e
        timed.allDay = false
        XCTAssertEqual(PersonalEvents.reminderText(timed, SolarDate(2026, 11, 26), today), "Ngày mai · 09:00 – 10:00")
        XCTAssertEqual(PersonalEvents.reminderText(e, SolarDate(2026, 11, 27), today), "Thứ Sáu 27/11 · Cả ngày")
        // Day lists: all-day first, then timed by start time.
        let a = LunarEvent.make("a", "A", 25, 11, calendar: LunarEvent.solar, repeatKind: LunarEvent.none, start: "2026-11-25", allDay: false, startTime: "14:00")
        let b = LunarEvent.make("b", "B", 25, 11, calendar: LunarEvent.solar, repeatKind: LunarEvent.none, start: "2026-11-25", allDay: false, startTime: "08:00")
        let c = LunarEvent.make("c", "C", 24, 11, calendar: LunarEvent.solar, repeatKind: LunarEvent.none, start: "2026-11-24", days: 2)
        XCTAssertEqual(PersonalEvents.on([a, b, c], today).map(\.title), ["C", "B", "A"])
    }

    func testAuspiciousFindsMatchingDays() {
        let days = Auspicious.find("WEDDING", from: SolarDate(2026, 11, 21))
        XCTAssertFalse(days.isEmpty)
        for d in days { XCTAssertTrue(DayInfo.of(d.date).activities.contains("wedding")) }
    }

    func testOldNotesBecomeOneOffEvents() {
        // Same results as the web app's noteToEvent (checked in the browser 2026-11-23).
        let short = PersonalEvents.noteToEvent(UserNote(id: "n1", date: "2026-11-24", content: "Mua hoa quả cúng rằm", createdAt: 1))
        XCTAssertEqual(short.title, "Mua hoa quả cúng rằm")
        XCTAssertNil(short.notes)
        XCTAssertEqual(short.repeatKind, LunarEvent.none)
        XCTAssertEqual(short.start, "2026-11-24")
        XCTAssertEqual(PersonalEvents.occurrenceStarts(short, SolarDate(2026, 1, 1), SolarDate(2027, 12, 31)), [SolarDate(2026, 11, 24)])
        let long = PersonalEvents.noteToEvent(UserNote(id: "n2", date: "2026-11-27", content: "Gọi điện cho bác Hai\nhỏi chuyện đám cưới tháng sau, nhớ mang quà", createdAt: 2))
        XCTAssertEqual(long.title, "Gọi điện cho bác Hai")
        XCTAssertEqual(long.notes, "Gọi điện cho bác Hai\nhỏi chuyện đám cưới tháng sau, nhớ mang quà")
        let cut = PersonalEvents.noteToEvent(UserNote(id: "n3", date: "2026-11-28", content: String(repeating: "x", count: 80), createdAt: 3))
        XCTAssertEqual(cut.title, String(repeating: "x", count: 59) + "…")
    }
}
