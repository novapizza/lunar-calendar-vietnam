import XCTest

/// iOS's "Allow notifications?" alert (SpringBoard), shown the first time the app asks: allowed. Once
/// answered it never shows again while the app stays installed, so it may not come.
func allowNotificationsIfAsked() {
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    let allow = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Allow", "Cho phép"])).firstMatch
    if allow.waitForExistence(timeout: 3) { allow.tap() }
}

/// Reminders, run on a simulator: a reminder shows as a banner (the app open), with the event's title
/// and day, and tapping it opens that day in Lịch. The app's -ui-reminder-soon makes the first
/// reminder show 3 s after it is registered; its real time is LunarCore's (unit tests).
final class RemindersUITests: XCTestCase {
    func testReminderBannerOpensItsDay() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-ui-reset", "-ui-reminder-soon", "-ui-open", "com.coding4pizza.lunar://open?tab=calendar&d=2027-01-15"]
        app.launch()

        let addEvent = app.buttons["Thêm sự kiện"].firstMatch
        XCTAssertTrue(addEvent.waitForExistence(timeout: 5))
        addEvent.tap()
        let title = app.textFields["Tên sự kiện, vd: Giỗ ông nội"]
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        title.tap()
        title.typeText("Họp lớp")
        app.buttons["Thêm nhắc"].tap()
        app.buttons["Vào ngày, lúc 8:00"].tap()
        allowNotificationsIfAsked()
        XCTAssertTrue(app.buttons["Bỏ nhắc Vào ngày, lúc 8:00"].waitForExistence(timeout: 3))
        app.buttons["Lưu"].tap()

        // Away from that day, so the tap has somewhere to go back to.
        app.buttons["tab-today"].tap()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let banner = springboard.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Họp lớp")).firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 10), "the reminder shows as a banner")
        XCTAssertTrue(banner.label.contains("Hôm nay · Cả ngày"), "banner: \(banner.label)")
        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        shot.name = "r1-banner"
        shot.lifetime = .keepAlways
        add(shot)
        banner.tap()

        let header = app.buttons["Chọn tháng, năm"]
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        let shown = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "Tháng 1 năm 2027"), object: header)
        XCTAssertEqual(XCTWaiter().wait(for: [shown], timeout: 5), .completed, "the tap opens Lịch on 15/1/2027")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Họp lớp")).firstMatch.exists
                      || app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Họp lớp")).firstMatch.exists,
                      "the day's panel lists the event")
    }

    /// A daily event with a reminder: more than iOS keeps, so the app registers 64 at their real
    /// times (its log "system holds 64" is read after the run); Sự kiện shows the bell.
    func testDailyReminderFillsTheLimit() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-ui-reset", "-ui-open", "com.coding4pizza.lunar://open?tab=calendar&d=2027-01-15"]
        app.launch()
        let addEvent = app.buttons["Thêm sự kiện"].firstMatch
        XCTAssertTrue(addEvent.waitForExistence(timeout: 5))
        addEvent.tap()
        let title = app.textFields["Tên sự kiện, vd: Giỗ ông nội"]
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        title.tap()
        title.typeText("Uống thuốc")
        app.buttons["Không lặp"].tap()
        app.buttons["Hằng ngày"].tap()
        app.buttons["Thêm nhắc"].tap()
        app.buttons["Vào ngày, lúc 8:00"].tap()
        allowNotificationsIfAsked()
        app.buttons["Lưu"].tap()
        app.buttons["tab-events"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["Có nhắc"].firstMatch.waitForExistence(timeout: 5), "the bell on the event")
        // The registration runs in the background: let it finish before the test closes the app.
        sleep(3)
    }
}
