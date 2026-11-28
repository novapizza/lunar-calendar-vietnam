import XCTest

/// The wide layout (iPad, as Android's tablet layout at 600 dp and up), in portrait and landscape:
/// the rail on the left instead of the bottom bar, and Hôm nay in two panes (the sheet fitted to the
/// pane, the day's cards beside it). Skipped on a phone.
final class TabletUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad, "iPad only")
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        app.launchArguments = ["-ui-reset", "-ui-open", "com.coding4pizza.lunar://open?tab=today&d=2026-11-28"]
        app.launch()
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
    }

    private func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    private func tab(_ name: String) -> XCUIElement { app.buttons["tab-\(name)"] }
    private var sheetDay: XCUIElement { app.staticTexts.matching(identifier: "sheet-day").firstMatch }
    private var gio: XCUIElement { app.staticTexts["Giờ hoàng đạo"] }

    /// The rail: the three tabs stacked down the left edge.
    private func checkRail(file: StaticString = #filePath, line: UInt = #line) {
        let today = tab("today").frame, calendar = tab("calendar").frame, events = tab("events").frame
        let window = app.windows.firstMatch.frame
        print("PROBE rail window=\(window) today=\(today) calendar=\(calendar) events=\(events)")
        XCTAssertLessThan(today.maxX, window.minX + 100, "tabs on the left edge", file: file, line: line)
        XCTAssertGreaterThan(calendar.minY, today.maxY - 1, "tabs stacked", file: file, line: line)
        XCTAssertGreaterThan(events.minY, calendar.maxY - 1, "tabs stacked", file: file, line: line)
        XCTAssertLessThan(events.maxY, window.midY, "tabs at the top, not a bottom bar", file: file, line: line)
    }

    /// Hôm nay: the sheet and the cards side by side, all of the sheet on screen.
    private func checkTwoPanes(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(sheetDay.waitForExistence(timeout: 5), file: file, line: line)
        XCTAssertTrue(gio.waitForExistence(timeout: 5), file: file, line: line)
        let sheet = sheetDay.frame, card = gio.frame, window = app.windows.firstMatch.frame
        let chi = app.staticTexts["Bính Ngọ"].firstMatch.frame
        print("PROBE panes sheet-day=\(sheet) gio=\(card) chi=\(chi)")
        XCTAssertGreaterThan(card.minX, sheet.maxX, "cards to the right of the sheet", file: file, line: line)
        XCTAssertTrue(sheetDay.label.hasSuffix("28/11/2026"), sheetDay.label, file: file, line: line)
        XCTAssertLessThan(chi.maxY, window.maxY, "the sheet's can chi is on screen", file: file, line: line)
    }

    func testRailAndTwoPanesInBothOrientations() {
        XCTAssertTrue(tab("today").waitForExistence(timeout: 5))
        checkRail()
        checkTwoPanes()
        shot("t1-portrait-today")

        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(2)
        checkRail()
        checkTwoPanes()
        shot("t2-landscape-today")
        // The day's cards scroll beside the sheet, which stays put.
        let sheetTop = sheetDay.frame.minY
        gio.swipeUp()
        sleep(1)
        XCTAssertEqual(sheetDay.frame.minY, sheetTop, accuracy: 1, "the sheet stays while the cards scroll")

        tab("calendar").tap()
        XCTAssertTrue(app.staticTexts["selected-day"].waitForExistence(timeout: 5))
        let grid = app.buttons["day-2026-11-28"].frame, panel = app.staticTexts["selected-day"].frame
        print("PROBE calendar day=\(grid) panel=\(panel)")
        XCTAssertGreaterThan(panel.minX, grid.maxX, "the day panel beside the grid")
        shot("t3-landscape-calendar")
        tab("events").tap()
        XCTAssertTrue(app.buttons["next-event"].waitForExistence(timeout: 5))
        shot("t4-landscape-events")
        app.buttons["Tiện ích và cài đặt"].tap()
        XCTAssertTrue(app.buttons["theme-dark"].waitForExistence(timeout: 5))
        XCTAssertGreaterThan(app.buttons["theme-dark"].frame.minX, app.buttons["Tìm ngày tốt"].frame.maxX, "settings in the second column")
        shot("t5-landscape-tools")
        // Rotating back keeps the tab.
        XCUIDevice.shared.orientation = .portrait
        sleep(2)
        XCTAssertTrue(app.buttons["theme-dark"].isHittable)
        checkRail()
    }
}
