import XCTest

/// Taps and swipes on Lịch, run on a simulator. Each test starts on a fixed day through the app's
/// link (-ui-open), so the months and lunar dates checked do not depend on today.
final class CalendarUITests: XCTestCase {
    private var app: XCUIApplication!

    private func launch(on day: String) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-ui-reset", "-ui-open", "com.coding4pizza.lunar://open?tab=calendar&d=\(day)"]
        app.launch()
    }

    private var header: XCUIElement { app.buttons["Chọn tháng, năm"] }
    private var selectedDay: XCUIElement { app.staticTexts["selected-day"] }
    private func day(_ iso: String) -> XCUIElement { app.buttons["day-\(iso)"] }

    private func expect(_ element: XCUIElement, _ format: String, _ value: String, file: StaticString = #filePath, line: UInt = #line) {
        let done = XCTNSPredicateExpectation(predicate: NSPredicate(format: format, value), object: element)
        XCTAssertEqual(XCTWaiter().wait(for: [done], timeout: 5), .completed,
                       "\(element) is \(element.label) / \(String(describing: element.value)), expected \(value)", file: file, line: line)
    }

    private func expectMonth(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        expect(header, "value == %@", text, file: file, line: line)
    }

    private func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    /// Arrows and sideways swipes on the grid turn the month; the selection keeps its day number.
    func testArrowsAndSwipesTurnTheMonth() {
        launch(on: "2026-11-28")
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        expectMonth("Tháng 11 năm 2026")
        expect(selectedDay, "label == %@", "Thứ Bảy, 28/11")
        shot("c1-november")
        app.buttons["Tháng sau"].tap()
        expectMonth("Tháng 12 năm 2026")
        expect(selectedDay, "label == %@", "Thứ Hai, 28/12")
        day("2026-12-16").swipeLeft()
        expectMonth("Tháng 1 năm 2027")
        day("2027-01-13").swipeRight()
        expectMonth("Tháng 12 năm 2026")
        // A slow drag by hand, 150 pt to the left.
        let from = day("2026-12-16").coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        from.press(forDuration: 0.1, thenDragTo: from.withOffset(CGVector(dx: -150, dy: 0)), withVelocity: 300, thenHoldForDuration: 0.1)
        expectMonth("Tháng 1 năm 2027")
        app.buttons["Tháng trước"].tap()
        app.buttons["Tháng trước"].tap()
        expectMonth("Tháng 11 năm 2026")
    }

    /// Tapping a day selects it (panel, ring, "day/month" lunar date); a faded day of the next month
    /// shows that month.
    func testTapSelectsADay() {
        launch(on: "2026-11-28")
        XCTAssertTrue(day("2026-11-09").waitForExistence(timeout: 5))
        day("2026-11-09").tap()
        expect(selectedDay, "label == %@", "Thứ Hai, 9/11")
        XCTAssertTrue(day("2026-11-09").isSelected)
        XCTAssertFalse(day("2026-11-28").isSelected)
        // 9/11/2026 is lunar 1/10 (its cell says "1/10"); the panel names the day.
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "1/10 · tháng Mười")).firstMatch.exists)
        shot("c2-first-of-month")
        day("2026-12-03").tap()
        expectMonth("Tháng 12 năm 2026")
        expect(selectedDay, "label == %@", "Thứ Năm, 3/12")
    }

    /// The month/year picker: next year, a month; then the 12-year page, a year, a month.
    func testPickerChoosesMonthAndYear() {
        launch(on: "2026-11-28")
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        header.tap()
        XCTAssertTrue(app.buttons["Tháng 2"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["Tháng 11"].isSelected)
        shot("c3-picker")
        app.buttons["Năm sau"].tap()
        XCTAssertTrue(app.buttons["2027"].waitForExistence(timeout: 3))
        app.buttons["Tháng 2"].tap()
        expectMonth("Tháng 2 năm 2027")
        XCTAssertFalse(app.buttons["Tháng 5"].exists, "the picker stayed open")
        header.tap()
        XCTAssertTrue(app.buttons["2027"].waitForExistence(timeout: 3))
        app.buttons["2027"].tap()
        XCTAssertTrue(app.buttons["2030"].waitForExistence(timeout: 3))
        shot("c4-years")
        app.buttons["2030"].tap()
        XCTAssertTrue(app.buttons["Tháng 5"].waitForExistence(timeout: 3))
        app.buttons["Tháng 5"].tap()
        expectMonth("Tháng 5 năm 2030")
    }

    /// A leap lunar month: 25/7/2025 is lunar 1/6 nhuận, written "1/6ⁿ" in its cell.
    func testLeapMonthDay() {
        launch(on: "2025-07-25")
        XCTAssertTrue(day("2025-07-25").waitForExistence(timeout: 5))
        expect(day("2025-07-25"), "label == %@", "25 tháng 7, âm lịch 1/6 nhuận")
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "tháng Sáu nhuận")).firstMatch.exists)
        shot("c5-leap")
    }

    /// "Hôm nay" brings the grid back to today's month and selects today.
    func testTodayChip() {
        launch(on: "2027-02-17")
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        expectMonth("Tháng 2 năm 2027")
        // The chip in the header's row, not the tab "Hôm nay".
        app.scrollViews.buttons["Hôm nay"].firstMatch.tap()
        let c = Calendar.current.dateComponents([.month, .year], from: Date())
        expectMonth("Tháng \(c.month!) năm \(c.year!)")
        XCTAssertFalse(app.scrollViews.buttons["Hôm nay"].exists, "the chip stays after going back to today")
    }

    /// A vertical drag that starts on the grid scrolls the page (only sideways swipes turn the month).
    func testDragOnTheGridScrolls() throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad, "one column only: on iPad the grid and the panel sit side by side")
        launch(on: "2026-11-28")
        XCTAssertTrue(selectedDay.waitForExistence(timeout: 5))
        let before = selectedDay.frame.minY
        let start = day("2026-11-18").coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: -300)))
        let moved = before - selectedDay.frame.minY
        XCTAssertGreaterThan(moved, 100, "dragging up on the grid did not scroll (moved \(moved))")
        expectMonth("Tháng 11 năm 2026")
        XCTAssertTrue(app.staticTexts["Giờ hoàng đạo"].waitForExistence(timeout: 3))
        shot("c6-scrolled")
    }

    /// WeekFold: scrolled down, the header stays at the top and the month folds to two weeks under
    /// it, the selected day's (23–29/11) and the next; a kept day still takes a tap.
    func testMonthFoldsToTwoWeeksAsThePageScrolls() throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad, "one column only: no WeekFold beside the panel on iPad")
        launch(on: "2026-11-28")
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        let headerTop = header.frame.minY
        let start = app.staticTexts["selected-day"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: start.withOffset(CGVector(dx: 0, dy: -500)), withVelocity: 300, thenHoldForDuration: 0.5)
        sleep(1)
        shot("c7-folded")
        XCTAssertEqual(header.frame.minY, headerTop, accuracy: 1, "the header stays")
        let kept = day("2026-11-28").frame, next = day("2026-11-30").frame
        XCTAssertGreaterThan(kept.minY, header.frame.maxY, "the selected week is under the header")
        XCTAssertEqual(next.minY - kept.minY, kept.height + 2, accuracy: 2, "the next week right under it")
        XCTAssertLessThan(next.maxY, header.frame.maxY + 3 * kept.height + 120, "the month is folded")
        day("2026-11-25").tap()
        XCTAssertTrue(selectedDay.label.contains("25/11"), selectedDay.label)
        start.press(forDuration: 0.1, thenDragTo: start.withOffset(CGVector(dx: 0, dy: 600)), withVelocity: 600, thenHoldForDuration: 0.3)
        sleep(1)
        XCTAssertTrue(app.scrollViews.buttons["Đổi ngày"].isHittable, "scrolled back, the chips are back")
    }
}
