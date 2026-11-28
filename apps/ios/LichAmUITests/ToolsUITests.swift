import XCTest

/// Tiện ích, run on a simulator: Đổi ngày (solar ↔ lunar, then open the day), Tìm ngày tốt, Xuất lịch
/// (saving the .ics through the system's sheet), the birth date (tuổi and mệnh), Thông tin, Giao diện.
final class ToolsUITests: XCTestCase {
    private var app: XCUIApplication!

    private func launch(tab: String, day: String = "2026-11-28") {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-ui-reset", "-ui-open", "com.coding4pizza.lunar://open?tab=\(tab)&d=\(day)"]
        app.launch()
    }

    private func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    private func containing(_ s: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", s)).firstMatch
    }

    private func waitGone(_ e: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: e)
        XCTAssertEqual(XCTWaiter().wait(for: [gone], timeout: 5), .completed, "\(e) still shown", file: file, line: line)
    }

    /// From the Calendar's chip: 28/11/2026 is lunar 20/10; the lunar grid's next month, 15/11 âm =
    /// 23/12/2026; "Xem ngày" opens it in Lịch.
    func testQuickDateConvertsAndOpensTheDay() {
        launch(tab: "calendar")
        app.scrollViews.buttons["Đổi ngày"].tap()
        XCTAssertTrue(app.staticTexts["Đổi ngày âm – dương"].waitForExistence(timeout: 3))
        XCTAssertTrue(containing("Dương lịch 28/11/2026").exists)
        let lunarCard = containing("Âm lịch 20/10/2026")
        XCTAssertTrue(lunarCard.exists, "28/11/2026 is lunar 20/10/2026")
        lunarCard.tap()
        XCTAssertTrue(app.staticTexts["Chọn ngày âm · số nhỏ là ngày dương"].waitForExistence(timeout: 3))
        app.buttons["nav-next"].tap()
        XCTAssertTrue(app.staticTexts["Tháng 11, 2026"].waitForExistence(timeout: 3))
        app.buttons["q-2026-12-23"].tap()
        XCTAssertTrue(containing("Âm lịch 15/11/2026").waitForExistence(timeout: 3))
        XCTAssertTrue(containing("Dương lịch 23/12/2026").exists)
        shot("t2-quick-date")
        app.buttons["Xem ngày 23/12/2026"].tap()
        waitGone(app.staticTexts["Đổi ngày âm – dương"])
        XCTAssertEqual(app.buttons["Chọn tháng, năm"].value as? String, "Tháng 12 năm 2026")
        XCTAssertTrue(app.staticTexts["selected-day"].label.contains("23/12"))
    }

    /// Tìm ngày tốt from Tiện ích: a kind of work lists good days; one opens in Lịch.
    func testAuspiciousListsDaysAndOpensOne() {
        launch(tab: "tools")
        app.buttons["Tìm ngày tốt"].tap()
        XCTAssertTrue(app.staticTexts["Chọn loại việc ở trên để xem các ngày tốt."].waitForExistence(timeout: 3))
        app.buttons["Cưới Hỏi"].tap()
        // Counted from the real today: two months always hold days good for a wedding (Trực rules).
        XCTAssertTrue(containing("ngày phù hợp").waitForExistence(timeout: 3))
        shot("t3-auspicious")
        let first = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Trực")).firstMatch
        XCTAssertTrue(first.exists)
        first.tap()
        waitGone(containing("ngày phù hợp"))
        XCTAssertTrue(app.buttons["tab-calendar"].isSelected, "Lịch is shown")
    }

    /// Xuất lịch: ticking Tết counts its occurrences; "Tải file .ics" opens the system's save sheet,
    /// and saving it says how many events were written.
    func testExportSavesAnIcsFile() {
        launch(tab: "calendar")
        app.scrollViews.buttons["Xuất lịch"].tap()
        XCTAssertTrue(app.buttons["Chọn sự kiện để xuất"].waitForExistence(timeout: 3))
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Tết Nguyên Đán")).firstMatch.tap()
        let save = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Tải file .ics")).firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 3))
        shot("t4-export")
        save.tap()
        // The system's sheet (Files): its Save / Move button, in English or Vietnamese.
        let confirm = app.buttons.matching(NSPredicate(format: "label IN %@", ["Save", "Move", "Lưu", "Di chuyển"])).firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 10), "the save sheet")
        shot("t5-save-sheet")
        confirm.tap()
        // Saving again over an earlier run's file asks to replace it.
        let replace = app.buttons.matching(NSPredicate(format: "label IN %@", ["Replace", "Thay thế"])).firstMatch
        if replace.waitForExistence(timeout: 3) { replace.tap() }
        XCTAssertTrue(containing("Đã tạo").waitForExistence(timeout: 10), "the file was written")
        shot("t6-exported")
    }

    /// Ngày sinh 15/6/1990: tuổi Canh Ngọ, mệnh Thổ; then cleared.
    func testBirthDateSetsTuoiAndMenh() {
        launch(tab: "tools")
        app.buttons["Chọn ngày sinh"].tap()
        XCTAssertTrue(app.staticTexts["Chọn năm sinh (dương lịch)"].waitForExistence(timeout: 3))
        app.buttons["1990"].tap()
        app.buttons["Tháng 6"].tap()
        XCTAssertTrue(app.buttons["b-1990-06-15"].waitForExistence(timeout: 3))
        shot("t7-birth-date")
        app.buttons["b-1990-06-15"].tap()
        let menh = app.staticTexts["menh"]
        XCTAssertTrue(menh.waitForExistence(timeout: 3))
        XCTAssertTrue(menh.label.contains("Canh Ngọ") && menh.label.contains("Thổ"), menh.label)
        XCTAssertEqual(app.buttons["Đổi ngày sinh"].value as? String, "15/6/1990")
        shot("t8-menh")
        app.buttons["Đổi ngày sinh"].tap()
        app.buttons["Xoá ngày sinh"].tap()
        XCTAssertTrue(app.buttons["Chọn ngày sinh"].waitForExistence(timeout: 3))
        XCTAssertFalse(menh.exists)
    }

    /// Thông tin ứng dụng opens and closes; Giao diện marks the theme chosen.
    func testAboutAndTheme() {
        launch(tab: "tools")
        app.buttons["Thông tin ứng dụng"].tap()
        XCTAssertTrue(app.buttons["Đã hiểu"].waitForExistence(timeout: 3))
        app.buttons["Đã hiểu"].tap()
        waitGone(app.buttons["Đã hiểu"])
        let light = app.buttons["theme-light"], system = app.buttons["theme-system"]
        light.tap()
        XCTAssertTrue(light.isSelected)
        system.tap()
        XCTAssertTrue(system.isSelected)
        XCTAssertFalse(light.isSelected)
    }
}
