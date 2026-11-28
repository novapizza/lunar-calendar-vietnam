import XCTest

/// Taps and swipes on Hôm nay, run on a simulator: xcodebuild test -scheme LichAm. Each test keeps
/// screenshots in the result bundle (xcrun xcresulttool export attachments) as evidence.
final class TodayUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-ui-reset"]
        app.launch()
    }

    /// "27/11/2026" for today + [offset] days, the end of the sheet number's label.
    private func dayLabel(_ offset: Int) -> String {
        let d = Calendar.current.date(byAdding: .day, value: offset, to: Date())!
        let c = Calendar.current.dateComponents([.day, .month, .year], from: d)
        return "\(c.day!)/\(c.month!)/\(c.year!)"
    }

    private var sheetDay: XCUIElement { app.staticTexts.matching(identifier: "sheet-day").firstMatch }

    private func waitForDay(_ offset: Int, file: StaticString = #filePath, line: UInt = #line) {
        let shows = NSPredicate(format: "label ENDSWITH %@", dayLabel(offset))
        let found = XCTNSPredicateExpectation(predicate: shows, object: sheetDay)
        XCTAssertEqual(XCTWaiter().wait(for: [found], timeout: 5), .completed,
                       "sheet shows \(sheetDay.label), expected \(dayLabel(offset))", file: file, line: line)
    }

    private func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    func testArrowsAndBackToToday() {
        XCTAssertTrue(sheetDay.waitForExistence(timeout: 5))
        waitForDay(0)
        shot("1-today")
        app.buttons["Ngày sau"].firstMatch.tap()
        waitForDay(1)
        app.buttons["Ngày sau"].firstMatch.tap()
        waitForDay(2)
        shot("2-two-days-on")
        app.buttons["Ngày trước"].firstMatch.tap()
        waitForDay(1)
        app.buttons["Về hôm nay"].tap()
        waitForDay(0)
        XCTAssertFalse(app.buttons["Về hôm nay"].exists)
    }

    func testSwipeCurlsThePage() {
        XCTAssertTrue(sheetDay.waitForExistence(timeout: 5))
        sheetDay.swipeLeft()
        waitForDay(1)
        shot("3-swiped-left")
        sheetDay.swipeRight()
        waitForDay(0)
    }

    /// Drags the page up from [y] (a fraction of the screen's height) and returns how far the sheet moved.
    private func scrollUp(from y: CGFloat) -> CGFloat {
        let before = sheetDay.frame.minY
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: y))
        start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: y - 0.4)))
        return before - sheetDay.frame.minY
    }

    /// The cards scroll into view (exists alone is not enough: XCUITest finds views off screen).
    func testCardsScrollIntoView() throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad, "one column only: on iPad the cards sit beside the sheet (TabletUITests)")
        XCTAssertTrue(sheetDay.waitForExistence(timeout: 5))
        let moved = scrollUp(from: 0.82)
        XCTAssertGreaterThan(moved, 100, "dragging on the cards did not scroll")
        let hours = app.staticTexts["Giờ hoàng đạo"]
        XCTAssertTrue(hours.waitForExistence(timeout: 3))
        XCTAssertTrue(app.windows.firstMatch.frame.contains(hours.frame), "Giờ hoàng đạo not on screen")
        XCTAssertTrue(app.staticTexts["VIỆC NÊN LÀM"].isHittable)
        // Scrolled content stays below the status bar: the sheet's top edge is cut there.
        let statusBottom = app.statusBars.firstMatch.exists ? app.statusBars.firstMatch.frame.maxY : 0
        XCTAssertFalse(sheetDay.isHittable && sheetDay.frame.minY < statusBottom, "content drawn behind the status bar")
        shot("4-cards")
        app.buttons["Nhập ngày sinh"].tap()
        XCTAssertTrue(app.staticTexts["Tiện ích"].waitForExistence(timeout: 3))
    }

    /// A vertical drag that starts on the sheet scrolls the page too (only sideways drags curl it).
    func testDragOnTheSheetScrolls() throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad, "one column only: on iPad the sheet stays put (TabletUITests)")
        XCTAssertTrue(sheetDay.waitForExistence(timeout: 5))
        let moved = scrollUp(from: 0.5)
        shot("7-drag-on-sheet")
        XCTAssertGreaterThan(moved, 100, "dragging up on the sheet did not scroll (moved \(moved))")
        waitForDay(0)
    }

    /// The binding's theme button cycles Theo máy → Sáng → Tối, and the choice survives a restart.
    func testThemeCyclesAndIsKept() {
        XCTAssertTrue(app.buttons["Giao diện: theo hệ thống"].waitForExistence(timeout: 5))
        app.buttons["Giao diện: theo hệ thống"].tap()
        XCTAssertTrue(app.buttons["Giao diện: sáng"].waitForExistence(timeout: 3))
        app.buttons["Giao diện: sáng"].tap()
        XCTAssertTrue(app.buttons["Giao diện: tối"].waitForExistence(timeout: 3))
        shot("5-dark")
        app.terminate()
        app.launchArguments = []
        app.launch()
        XCTAssertTrue(app.buttons["Giao diện: tối"].waitForExistence(timeout: 5), "theme not kept after a restart")
    }

    /// com.coding4pizza.lunar://open?tab=calendar&d=… opens the Calendar on that month.
    func testLinkOpensTheCalendar() {
        XCTAssertTrue(sheetDay.waitForExistence(timeout: 5))
        app.open(URL(string: "com.coding4pizza.lunar://open?tab=calendar&d=2027-02-17")!)
        let header = app.buttons["Chọn tháng, năm"]
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        XCTAssertEqual(header.value as? String, "Tháng 2 năm 2027")
        shot("6-link-calendar")
    }
}
