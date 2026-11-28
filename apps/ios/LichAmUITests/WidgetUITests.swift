import XCTest

/// The widgets in SpringBoard's widget gallery, run on a simulator: Lịch Âm lists Tờ lịch hôm nay and
/// Lịch tháng in three sizes each, drawn by the widget extension itself. Adding one to the home screen
/// is not tested: on the iOS 26.5 simulator SpringBoard crashes in -[SBHRippleSimulation clear] when a
/// widget is dropped in (crash reports 2026-11-28, also with Reduce Motion on).
/// SpringBoard's labels are English (the simulator's language).
final class WidgetUITests: XCTestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    private func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    func testGalleryListsBothWidgetsInThreeSizes() throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad, "the gallery pages are laid out for the iPhone")
        continueAfterFailure = false
        // The widgets follow the app's Giao diện: start from the defaults (Theo máy, light simulator),
        // not from what an earlier test left (a dark theme drew the gallery dark).
        let app = XCUIApplication()
        app.launchArguments = ["-ui-reset"]
        app.launch()
        app.terminate()
        XCUIDevice.shared.press(.home)
        sleep(1)
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 2)
        let edit = springboard.buttons["Edit"]
        XCTAssertTrue(edit.waitForExistence(timeout: 5), "edit mode")
        edit.tap()
        springboard.buttons["Add Widget"].tap()
        let search = springboard.searchFields["Search Widgets"]
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        search.tap()
        search.typeText("Lịch")
        let cell = springboard.cells["Lịch Âm"]
        XCTAssertTrue(cell.waitForExistence(timeout: 5), "the app's widgets are listed")
        cell.tap()

        // Pages: Tờ lịch small, medium, large, then Lịch tháng small, medium, large; swiped on the
        // preview's height (the title changes from page 4 on).
        XCTAssertTrue(springboard.staticTexts["Tờ lịch hôm nay"].waitForExistence(timeout: 5))
        shot("w1-sheet-small")
        let from = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.6))
        let to = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.6))
        for page in 2...6 {
            from.press(forDuration: 0.05, thenDragTo: to)
            sleep(1)
            XCTAssertTrue(springboard.staticTexts[page <= 3 ? "Tờ lịch hôm nay" : "Lịch tháng"].exists, "page \(page)")
            shot("w\(page)-" + ["", "", "sheet-medium", "sheet-large", "month-small", "month-medium", "month-large"][page])
        }
        springboard.buttons["Close"].firstMatch.tap()
        XCUIDevice.shared.press(.home)
    }
}
