import XCTest

/// Personal events and built-in reminders, run on a simulator: add, pick a day, repeat, detail,
/// edit, delete, a built-in day's reminder, kept after a restart. Starts on a fixed day (-ui-open).
final class EventsUITests: XCTestCase {
    private var app: XCUIApplication!

    private func launch(tab: String = "calendar", day: String = "2026-11-28", reset: Bool = true) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = (reset ? ["-ui-reset"] : []) + ["-ui-open", "com.coding4pizza.lunar://open?tab=\(tab)&d=\(day)"]
        app.launch()
    }

    private func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    private func waitGone(_ e: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: e)
        XCTAssertEqual(XCTWaiter().wait(for: [gone], timeout: 5), .completed, "\(e) still shown", file: file, line: line)
    }

    private var titleField: XCUIElement { app.textFields["Tên sự kiện, vd: Giỗ ông nội"] }
    private var addButton: XCUIElement { app.buttons["Thêm sự kiện"].firstMatch }
    private func text(_ s: String) -> XCUIElement { app.staticTexts[s].firstMatch }
    private func containing(_ s: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", s)).firstMatch
    }

    /// Opens the editor from the Calendar's day panel and types a title.
    private func newEvent(_ title: String) {
        XCTAssertTrue(addButton.waitForExistence(timeout: 5))
        addButton.tap()
        XCTAssertTrue(text("Sự kiện mới").waitForExistence(timeout: 3))
        titleField.tap()
        titleField.typeText(title)
    }

    /// A new event on another day: it shows on that day only, and in Sự kiện as "Cá nhân".
    func testAddAnEventOnAnotherDay() {
        launch()
        newEvent("Giỗ ông nội")
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Ngày bắt đầu")).firstMatch.tap()
        XCTAssertTrue(app.buttons["pick-2026-11-30"].waitForExistence(timeout: 3))
        shot("e1-date-picker")
        app.buttons["pick-2026-11-30"].tap()
        XCTAssertTrue(containing("30/11/2026").exists, "the date field shows the day picked")
        app.buttons["Lưu"].tap()
        waitGone(text("Sự kiện mới"))
        XCTAssertTrue(text("Chưa có sự kiện trong ngày này.").waitForExistence(timeout: 3), "28/11 has no event")
        app.buttons["day-2026-11-30"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Giỗ ông nội")).firstMatch.waitForExistence(timeout: 3))
        shot("e2-on-the-day")
        app.buttons["tab-events"].tap()
        XCTAssertTrue(app.buttons["Của bạn (1)"].waitForExistence(timeout: 5))
        // Listed from today's month on (the highlight when it is the next event, else a row "Cá nhân").
        // Sự kiện counts from the real today: this check holds while 30/11/2026 is in its loaded months.
        XCTAssertTrue(containing("Giỗ ông nội").waitForExistence(timeout: 3))
        shot("e3-events-tab")
    }

    /// A yearly event: the lunar rule note; its detail; edit all; delete all.
    func testRepeatingEventDetailEditDelete() {
        launch()
        newEvent("Giỗ ông nội")
        app.buttons["Không lặp"].tap()
        app.buttons["Hằng năm"].tap()
        XCTAssertTrue(containing("Ngày 20/10 âm hằng năm").waitForExistence(timeout: 3), "28/11/2026 is lunar 20/10")
        shot("e4-yearly")
        app.buttons["Lưu"].tap()
        waitGone(text("Sự kiện mới"))

        let row = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Giỗ ông nội")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 3))
        row.tap()
        XCTAssertTrue(app.buttons["Sửa"].waitForExistence(timeout: 3))
        XCTAssertTrue(containing("Thứ Bảy, 28/11/2026 · 20/10 âm").exists)
        shot("e5-detail")
        app.buttons["Sửa"].tap()
        XCTAssertTrue(text("Sửa sự kiện lặp lại").waitForExistence(timeout: 3))
        app.buttons["Tất cả các lần"].tap()
        XCTAssertTrue(text("Sửa sự kiện").waitForExistence(timeout: 3))
        titleField.tap()
        titleField.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 12) + "Giỗ bà nội")
        app.buttons["Lưu"].tap()
        // Back on the detail, with the new title.
        XCTAssertTrue(text("Giỗ bà nội").waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["Xoá"].exists)
        app.buttons["Xoá"].tap()
        XCTAssertTrue(text("Xoá sự kiện lặp lại").waitForExistence(timeout: 3))
        app.buttons["Tất cả các lần"].tap()
        XCTAssertTrue(text("Chưa có sự kiện trong ngày này.").waitForExistence(timeout: 5))
    }

    /// A built-in day (the next event: a mồng 1) gets a reminder, then it is turned off.
    func testBuiltinDayReminder() {
        launch(tab: "events")
        let next = app.buttons["next-event"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        next.tap()
        XCTAssertTrue(text("Nhắc ngày này").waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["Tắt nhắc"].exists, "no reminder yet")
        shot("e6-builtin-reminder")
        app.buttons["Lưu"].tap()
        waitGone(text("Nhắc ngày này"))
        next.tap()
        XCTAssertTrue(app.buttons["Tắt nhắc"].waitForExistence(timeout: 3), "the reminder was kept")
        app.buttons["Tắt nhắc"].tap()
        waitGone(text("Nhắc ngày này"))
        next.tap()
        XCTAssertTrue(text("Nhắc ngày này").waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["Tắt nhắc"].exists, "the reminder was removed")
    }

    /// Events are stored (App Group defaults) and come back after a restart.
    func testEventKeptAfterRestart() {
        launch()
        newEvent("Sinh nhật mẹ")
        app.buttons["Lưu"].tap()
        waitGone(text("Sự kiện mới"))
        app.terminate()
        launch(tab: "events", reset: false)
        XCTAssertTrue(app.buttons["Của bạn (1)"].waitForExistence(timeout: 5))
        app.buttons["Của bạn (1)"].tap()
        XCTAssertTrue(text("Sự kiện của bạn").waitForExistence(timeout: 3))
        XCTAssertTrue(containing("Sinh nhật mẹ").exists)
        shot("e7-your-events")
    }
}
