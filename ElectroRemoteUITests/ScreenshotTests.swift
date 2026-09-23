import XCTest

/// Прогон по экранам в демо-режиме (`-screenshots`) с сохранением PNG в
/// каталог из переменной окружения SCREENSHOT_DIR (передаётся как
/// TEST_RUNNER_SCREENSHOT_DIR в xcodebuild).
final class ScreenshotTests: XCTestCase {
    private var app: XCUIApplication!
    private var dir: URL!

    override func setUpWithError() throws {
        continueAfterFailure = false
        let path = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] ?? NSTemporaryDirectory() + "screens"
        dir = URL(fileURLWithPath: path)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        app = XCUIApplication()
        app.launchArguments = ["-screenshots"]
        app.launch()
    }

    private func shot(_ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: dir.appendingPathComponent(name + ".png"))
        let a = XCTAttachment(uniformTypeIdentifier: "public.png", name: name + ".png", payload: png, userInfo: nil)
        a.lifetime = .keepAlways
        add(a)
    }

    private func wait(_ e: XCUIElement, _ t: TimeInterval = 15) {
        XCTAssertTrue(e.waitForExistence(timeout: t), "не дождались \(e)")
    }

    func testScreenshots() throws {
        // 1. вход
        let email = app.textFields.firstMatch
        wait(email)
        shot("01-login")
        email.tap(); email.typeText("driver@example.com")
        let pass = app.secureTextFields.firstMatch
        pass.tap(); pass.typeText("password123")
        app.buttons["Войти"].tap()

        // 2. подключение
        let connect = app.buttons["Подключиться"]
        wait(connect, 30)
        shot("02-connect")
        connect.tap()

        // 3. главная
        wait(app.staticTexts["Панель быстрого доступа"], 30)
        // 3D-модель качается с сервера: ждём её, иначе в кадр попадает плоская заглушка
        if !app.otherElements["car3d-ready"].waitForExistence(timeout: 90) {
            print("ВНИМАНИЕ: 3D-модель не загрузилась за 90 с, снимок с заглушкой")
        }
        sleep(2)
        shot("03-home")

        // 4. климат и сиденья
        app.buttons["Климат"].firstMatch.tap()
        wait(app.staticTexts["Функции"])
        sleep(1)
        shot("04-climate")
        app.buttons["Сиденья"].tap()
        wait(app.staticTexts["Водитель"])
        sleep(1)
        shot("05-seats")
        app.buttons["Закрыть"].tap()

        // 5. новости
        wait(app.buttons["Новости"])
        app.buttons["Новости"].tap()
        wait(app.staticTexts["Обновление LeapRemote"])
        sleep(1)
        shot("06-news")
        app.buttons["Назад"].tap()

        // 6. карта
        wait(app.buttons["Карта"])
        app.buttons["Карта"].tap()
        wait(app.staticTexts["Положение автомобиля"])
        sleep(2)
        shot("07-map")

        // 7. сцены
        app.buttons["Главная"].tap()
        wait(app.buttons["Мои сцены"])
        app.buttons["Мои сцены"].tap()
        wait(app.staticTexts["Остудить к выходу"])
        sleep(1)
        shot("08-scenes")
    }
}
