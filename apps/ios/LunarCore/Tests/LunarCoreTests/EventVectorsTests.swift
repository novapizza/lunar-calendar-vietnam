import XCTest
@testable import LunarCore

/// shared/test-vectors/events.json: personal event dates every app must reproduce
/// (shared/design/events.md; the web app and Android's core reproduce them).
final class EventVectorsTests: XCTestCase {
    private func root() throws -> [String: Any] { try readVectors("events.json") }

    func testStarts() throws {
        let root = try root()
        let range = try XCTUnwrap(root["range"] as? [String: Any])
        let from = day(range["from"] as! String)
        let to = day(range["to"] as! String)
        let cases = try XCTUnwrap(root["cases"] as? [[String: Any]])
        XCTAssertGreaterThanOrEqual(cases.count, 12, "expected the full case list")
        var bad: [String] = []
        for c in cases {
            let expected = c["starts"] as! [String]
            let actual = PersonalEvents.occurrenceStarts(try decodeEvent(c["event"]!), from, to).map(\.iso)
            if expected != actual { bad.append("\(c["name"]!):\n  expected \(expected)\n  actual   \(actual)") }
        }
        XCTAssertEqual(bad.count, 0, "\(bad.count) of \(cases.count) cases differ:\n\(bad.joined(separator: "\n"))")
    }

    func testCovering() throws {
        let c = try XCTUnwrap(try root()["covering"] as? [String: Any])
        let d = day(c["day"] as! String)
        let e = try decodeEvent(c["event"]!)
        let expected = c["starts"] as! [String]
        XCTAssertEqual(PersonalEvents.occurrencesCovering(e, d, d).map(\.iso), expected)
        XCTAssertEqual(PersonalEvents.eventsOnDay([e], d).map(\.start.iso), expected)
    }

    func testReminders() throws {
        let r = try XCTUnwrap(try root()["reminders"] as? [String: Any])
        let events = try (r["events"] as! [Any]).map { try decodeEvent($0) }
        let from = minute(r["from"] as! String)
        let to = minute(r["to"] as! String)
        let expected = (r["fires"] as! [[Any]]).map { a in [a[0] as! String, a[1] as! String, "\(a[2])", a[3] as! String] }
        let actual = PersonalEvents.remindersBetween(events, from, to).map { [$0.event.id, $0.start.iso, "\($0.minutes)", $0.at.description] }
        XCTAssertEqual(actual, expected)
    }
}
