import Foundation
import XCTest
@testable import LunarCore

/// shared/test-vectors/ of the repository: this file is
/// apps/ios/LunarCore/Tests/LunarCoreTests/TestSupport.swift, six folders below the root.
let testVectors: URL = {
    var url = URL(fileURLWithPath: #filePath)
    for _ in 0..<6 { url.deleteLastPathComponent() }
    return url.appendingPathComponent("shared/test-vectors")
}()

func readVectors(_ name: String) throws -> [String: Any] {
    let data = try Data(contentsOf: testVectors.appendingPathComponent(name))
    return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
}

/// A JSON value (from JSONSerialization) decoded as a LunarEvent, as the apps read stored events.
func decodeEvent(_ value: Any) throws -> LunarEvent {
    let data = try JSONSerialization.data(withJSONObject: value)
    return try JSONDecoder().decode(LunarEvent.self, from: data)
}

func decodeEvent(json: String) throws -> LunarEvent {
    try JSONDecoder().decode(LunarEvent.self, from: Data(json.utf8))
}

func encodeEvent(_ e: LunarEvent) throws -> [String: Any] {
    let data = try JSONEncoder().encode(e)
    return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
}

/// "yyyy-MM-dd HH:mm".
func minute(_ s: String) -> LocalDateTime {
    let p = s.split(separator: " ")
    let d = SolarDate.parse(String(p[0]))!
    let t = p[1].split(separator: ":")
    return LocalDateTime(d, Int(t[0])!, Int(t[1])!)
}

func day(_ s: String) -> SolarDate { SolarDate.parse(s)! }

extension LunarEvent {
    /// The Kotlin tests' `LunarEvent(id, title, day, month, leap, createdAt, calendar, repeat)`.
    static func make(
        _ id: String, _ title: String, _ lunarDay: Int, _ lunarMonth: Int, _ leap: Bool = false, _ createdAt: Int = 0,
        calendar: String = LunarEvent.lunar, repeatKind: String = LunarEvent.yearly, start: String? = nil,
        weekdays: [Int] = [], until: String? = nil, count: Int? = nil, exceptions: [String] = [], allDay: Bool = true,
        startTime: String = "09:00", endTime: String = "10:00", days: Int = 1, reminders: [Int] = [],
        notes: String? = nil, location: String? = nil, color: String = LunarEvent.gold, ref: String? = nil
    ) -> LunarEvent {
        LunarEvent(id: id, title: title, lunarDay: lunarDay, lunarMonth: lunarMonth, isLeapMonth: leap, createdAt: createdAt,
                   calendar: calendar, repeatKind: repeatKind, start: start, weekdays: weekdays, until: until, count: count,
                   exceptions: exceptions, allDay: allDay, startTime: startTime, endTime: endTime, days: days,
                   reminders: reminders, notes: notes, location: location, color: color, ref: ref)
    }
}
