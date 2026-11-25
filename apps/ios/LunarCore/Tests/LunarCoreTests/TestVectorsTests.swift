import XCTest
@testable import LunarCore

/// The Swift port must reproduce shared/test-vectors exactly, like the web app and Android's core
/// (both at 0 mismatches on 2026-11-21).
final class TestVectorsTests: XCTestCase {
    private func rows(_ file: String) throws -> [[Any]] {
        try XCTUnwrap(readVectors(file)["rows"] as? [[Any]])
    }

    func testLunarDays() throws {
        let rows = try rows("lunar-days.json")
        XCTAssertGreaterThan(rows.count, 10_000, "expected the full vector set")
        var mismatches: [String] = []
        for r in rows {
            let date = day(r[0] as! String)
            let info = DayInfo.of(date)
            let expected = [
                r[0] as! String, "\(r[1])", "\(r[2])", "\(r[3])", "\(r[4])",
                r[5] as! String, r[6] as! String, r[7] as! String, r[8] as! String, r[9] as! String, r[10] as! String,
            ]
            let actual = [
                date.iso, "\(info.lunar.day)", "\(info.lunar.month)", "\(info.lunar.year)", info.lunar.leap ? "1" : "0",
                info.canChiDay, info.canChiMonth, info.canChiYear, info.truc, info.rating.rawValue,
                info.gioHoangDao.map { String($0.chiIndex) }.joined(separator: " "),
            ]
            if expected != actual { mismatches.append("expected \(expected)\n   actual \(actual)") }
        }
        XCTAssertEqual(mismatches.count, 0, "\(mismatches.count) of \(rows.count) rows differ, first:\n\(mismatches.prefix(5).joined(separator: "\n"))")
    }

    func testLunarToSolarRoundTrip() throws {
        // Every vector row converted back must land on the same solar date.
        let bad = try rows("lunar-days.json").filter { r in
            let back = LunarCalendar.lunarToSolar(r[1] as! Int, r[2] as! Int, r[3] as! Int, (r[4] as! Int) == 1)
            return back?.iso != (r[0] as! String)
        }
        XCTAssertEqual(bad.count, 0, "first: \(bad.prefix(3))")
    }

    func testMenhYears() throws {
        let menh = try readVectors("menh.json")
        let years = try XCTUnwrap((menh["years"] as? [String: Any])?["rows"] as? [[Any]])
        XCTAssertEqual(years.count, 200)
        let bad = years.filter { r in
            let m = MenhCalc.ofLunarYear(r[0] as! Int)
            return [m.canChi, m.napAm, m.element.rawValue] != [r[1] as! String, r[2] as! String, r[3] as! String]
        }
        XCTAssertEqual(bad.count, 0, "first: \(bad.prefix(3))")
    }

    func testMenhBirthDates() throws {
        let menh = try readVectors("menh.json")
        let rows = try XCTUnwrap((menh["birthDates"] as? [String: Any])?["rows"] as? [[Any]])
        XCTAssertGreaterThan(rows.count, 390)
        let bad = rows.filter { r in MenhCalc.ofBirthDate(day(r[0] as! String))?.lunarYear != (r[1] as! Int) }
        XCTAssertEqual(bad.count, 0, "first: \(bad.prefix(3))")
    }

    func testKnownDates() {
        // Spot checks that read like the app: Tết 2026 and the day before it.
        XCTAssertEqual(LunarCalendar.solarToLunar(2026, 2, 17), LunarDate(day: 1, month: 1, year: 2026, leap: false))
        XCTAssertEqual(MenhCalc.ofBirthDate(SolarDate(1990, 1, 26))?.canChi, "Kỷ Tỵ")
        XCTAssertEqual(MenhCalc.ofBirthDate(SolarDate(1990, 1, 27))?.element, .tho)
        XCTAssertEqual(MenhCalc.paletteFor(MenhCalc.ofLunarYear(1990), .tuongSinh), .hoa)
    }
}
