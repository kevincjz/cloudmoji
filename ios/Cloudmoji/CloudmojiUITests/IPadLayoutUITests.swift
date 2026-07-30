import XCTest
import UIKit

@MainActor
final class IPadLayoutUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        guard UIDevice.current.userInterfaceIdiom == .pad else {
            throw XCTSkip("iPad-only layout coverage")
        }
        app = XCUIApplication()
        app.launchArguments = [
            "-cm_reset_persisted_settings", "YES",
            "-cm_seen_tutorial", "YES",
            "-cm_use_stub_entitlements", "YES",
            "-cm_premium_unlocked", "YES",
            "-cm_lang", "en",
            "-cm_muted", "NO",
        ]
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
        app = nil
        super.tearDown()
    }

    func testPortraitLauncherUsesPadSizedHomeScreenCells() {
        XCUIDevice.shared.orientation = .portrait
        app.launch()

        let words = app.buttons["launcher-tile-words"]
        let count = app.buttons["launcher-tile-count"]
        let sleepy = app.buttons["launcher-tile-sleepy"]
        XCTAssertTrue(words.waitForExistence(timeout: 4))
        XCTAssertTrue(sleepy.exists)

        XCTAssertGreaterThanOrEqual(words.frame.height, 145)
        XCTAssertGreaterThanOrEqual(words.frame.width, 110)
        XCTAssertGreaterThan(count.frame.minX - words.frame.minX, 105)
        XCTAssertGreaterThanOrEqual(count.frame.minX - words.frame.maxX, 18)
        XCTAssertGreaterThan(sleepy.frame.minY - words.frame.minY, 150)
        XCTAssertTrue(app.buttons["parent-btn"].isHittable)

        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "iPad portrait launcher"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testLandscapeLauncherUsesTheAvailableCanvasWithoutOverlap() {
        XCUIDevice.shared.orientation = .portrait
        app.launch()

        let words = app.buttons["launcher-tile-words"]
        let count = app.buttons["launcher-tile-count"]
        let sleepy = app.buttons["launcher-tile-sleepy"]
        XCTAssertTrue(words.waitForExistence(timeout: 4))
        XCTAssertTrue(sleepy.exists)

        XCUIDevice.shared.orientation = .landscapeLeft
        let settledLandscape = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                words.frame.width >= 190 && words.frame.height >= 200
            },
            object: nil
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [settledLandscape], timeout: 5),
            .completed,
            "launcher never settled into its expanded landscape composition"
        )

        XCTAssertGreaterThanOrEqual(words.frame.width, 190)
        XCTAssertGreaterThanOrEqual(words.frame.height, 200)
        XCTAssertGreaterThanOrEqual(count.frame.minX - words.frame.maxX, 28)
        XCTAssertGreaterThan(sleepy.frame.minY - words.frame.minY, 220)
        XCTAssertTrue(app.buttons["parent-btn"].isHittable)

        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "iPad landscape launcher"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testLandscapeInstrumentTransposesToFourColumns() {
        XCUIDevice.shared.orientation = .landscapeLeft
        app.launchArguments += ["-cm_open", "instrument"]
        app.launch()

        let first = app.buttons["pad-0"]
        let fourth = app.buttons["pad-3"]
        let fifth = app.buttons["pad-4"]
        XCTAssertTrue(first.waitForExistence(timeout: 4))
        XCTAssertEqual(first.frame.midY, fourth.frame.midY, accuracy: 4)
        XCTAssertGreaterThan(fifth.frame.midY, first.frame.midY)
        XCTAssertGreaterThanOrEqual(first.frame.width, 180)
        XCTAssertGreaterThanOrEqual(app.buttons["home-btn"].frame.width, 100)

        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "iPad landscape instrument"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
