import XCTest
@testable import LunarCore

/// Ported from Android's BuiltinRemindersTest.kt and SmallDateTest.kt.
final class BuiltinRemindersTests: XCTestCase {
    private let today = SolarDate(2026, 11, 24)

    func testBuiltInDaysCarryTheirRef() {
        let dec = Dictionary(Upcoming.month(today, 1).map { ($0.title, $0.ref) }, uniquingKeysWith: { a, _ in a })
        XCTAssertEqual(dec["Mồng 1 tháng 11"], BuiltinReminders.mong1)
        XCTAssertEqual(dec["Ngày rằm tháng 11"], BuiltinReminders.ram)
        let jan = Dictionary(Upcoming.month(today, 2).map { ($0.title, $0.ref) }, uniquingKeysWith: { a, _ in a })
        XCTAssertEqual(jan["Tết Dương Lịch"], "Tết Dương Lịch")
    }

    func testRepeatsOffered() {
        XCTAssertEqual(BuiltinReminders.repeats(BuiltinReminders.mong1), [LunarEvent.monthly, LunarEvent.yearly, LunarEvent.none])
        XCTAssertEqual(BuiltinReminders.repeats("Tết Dương Lịch"), [LunarEvent.yearly, LunarEvent.none])
    }

    /// The web's form saves this for "Mồng 1 tháng 11" (9/12/2026) as it opens; same fires.
    func testMonthlyMong1FiresAtEightOnEveryFirst() {
        let r = LunarEvent.make("r", BuiltinReminders.mong1, 1, 11, calendar: LunarEvent.lunar, repeatKind: LunarEvent.monthly,
                                start: "2026-12-09", reminders: [0], ref: BuiltinReminders.mong1)
        let fires = PersonalEvents.remindersBetween([r], LocalDateTime(2026, 12, 1, 0, 0), LocalDateTime(2027, 3, 1, 0, 0))
        XCTAssertEqual(fires.map(\.at), [LocalDateTime(2026, 12, 9, 8, 0), LocalDateTime(2027, 1, 8, 8, 0), LocalDateTime(2027, 2, 6, 8, 0)])
        XCTAssertTrue(BuiltinReminders.reminds([r], BuiltinReminders.mong1, SolarDate(2027, 1, 8)))
        XCTAssertFalse(BuiltinReminders.reminds([r], BuiltinReminders.mong1, SolarDate(2026, 11, 9)))
        // iOS registers the next ones at once: in order, across the growing windows, up to the limit.
        let next = PersonalEvents.nextReminders([r], from: LocalDateTime(2026, 12, 9, 8, 0), limit: 14)
        XCTAssertEqual(next.count, 14)
        XCTAssertEqual(next.first?.at, LocalDateTime(2026, 12, 9, 8, 0), "a reminder at [from] counts")
        XCTAssertEqual(next.map(\.at), next.map(\.at).sorted())
        XCTAssertEqual(Set(next.map(\.at)).count, 14, "no reminder twice where the windows meet")
        XCTAssertEqual(PersonalEvents.nextReminders([r], from: LocalDateTime(2026, 12, 9, 8, 1), limit: 1).first?.at,
                       LocalDateTime(2027, 1, 8, 8, 0))
        XCTAssertTrue(PersonalEvents.nextReminders([r], from: LocalDateTime(2026, 12, 9, 8, 0), limit: 0).isEmpty)
        XCTAssertFalse(BuiltinReminders.reminds([r], BuiltinReminders.ram, SolarDate(2026, 12, 23)))
    }
}

final class SmallDateTests: XCTestCase {
    func testLunarDayShowsItsMonthOnTheFirstAndWhenSelected() {
        let l = LunarDate(day: 16, month: 10, year: 2026, leap: false)
        XCTAssertEqual(SmallDate.lunar(l, selected: false).text, "16")
        XCTAssertEqual(SmallDate.lunar(l, selected: true).text, "16/10")
        XCTAssertEqual(SmallDate.lunar(LunarDate(day: 1, month: 10, year: 2026, leap: false), selected: false).text, "1/10")
    }

    func testLeapMonthIsMarkedOnlyWithTheMonth() {
        // 2025 has a leap 6th lunar month: 2025-08-08 is 15/6 leap (shared/test-vectors/events.json).
        let l = LunarCalendar.solarToLunar(SolarDate(2025, 8, 8))
        XCTAssertEqual(l, LunarDate(day: 15, month: 6, year: 2025, leap: true))
        XCTAssertEqual(SmallDate.lunar(l, selected: false), SmallDate(15))
        XCTAssertEqual(SmallDate.lunar(l, selected: true), SmallDate(15, 6, leap: true))
        XCTAssertEqual(SmallDate.lunar(l, selected: true).text, "15/6n")
    }

    func testSolarDayShowsItsMonthOnTheFirstAndWhenSelected() {
        XCTAssertEqual(SmallDate.solar(SolarDate(2026, 11, 24), selected: false).text, "24")
        XCTAssertEqual(SmallDate.solar(SolarDate(2026, 11, 24), selected: true).text, "24/11")
        XCTAssertEqual(SmallDate.solar(SolarDate(2026, 12, 1), selected: false).text, "1/12")
    }

    func testDatesWithoutCalendar() {
        // Julian-day helpers against java.time's answers (the Android port's source).
        XCTAssertEqual(SolarDate(2026, 11, 23).weekdayIndex, 0) // Monday
        XCTAssertEqual(SolarDate(2026, 11, 29).weekdayIndex, 6) // Sunday
        XCTAssertEqual(SolarDate(2026, 1, 31).plusMonths(1), SolarDate(2026, 2, 28))
        XCTAssertEqual(SolarDate(2026, 11, 1).plusMonths(-11), SolarDate(2025, 12, 1))
        XCTAssertEqual(SolarDate(2024, 12, 31).plusDays(1), SolarDate(2025, 1, 1))
        XCTAssertEqual(LocalDateTime(2026, 12, 9, 8, 0).plusMinutes(-1440 - 30), LocalDateTime(2026, 12, 8, 7, 30))
        XCTAssertNil(SolarDate.parse("2026-1-05"))
        XCTAssertEqual(SolarDate.parse("2026-01-05"), SolarDate(2026, 1, 5))
    }
}
