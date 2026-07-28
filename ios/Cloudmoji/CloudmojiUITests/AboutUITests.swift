import XCTest

/// About, driven the whole way a parent reaches it: ⚙️ → gate → Settings → About.
///
/// Everything here needs a real accessibility tree and can only be asked in this
/// target. `AboutViewTests` proves the copy is the iOS copy; it cannot prove a
/// single word of it is reachable, because SwiftUI builds no accessibility tree
/// outside XCUITest and every element query in a unit test comes back empty.
///
/// The mutation that was actually run: deleting the per-row
/// `.accessibilityIdentifier` in `AboutView.section(_:_:)`. Both tests below fail.
/// The disclosure assertions cover the other two shapes structurally — copy
/// rendered unconditionally instead of inside a `DisclosureGroup` trips the
/// `XCTAssertFalse` before the tap, and a group that will not open or will not
/// close trips one of the two waits after it.
///
/// What it does **not** catch, recorded so nobody re-derives it: removing
/// `AboutView`'s `.accessibilityElement(children: .contain)`. Both tests here stay
/// green without it. A `List`'s rows are table cells that publish their own
/// elements, so the panel's identifier does not swallow them the way a `VStack`'s
/// would — the modifier is kept as insurance against a future refactor, not
/// because this suite is guarding it.
final class AboutUITests: XCTestCase {

    /// Parent chrome, so the 44pt iOS HIG floor rather than the app's 64pt child
    /// minimum. Frames arrive from the accessibility layer as floats, hence the
    /// same twentieth of a point the other two suites allow.
    private static let parentMinimum: CGFloat = 44
    private static let tolerance: CGFloat = 0.05

    override func setUp() {
        continueAfterFailure = false
    }

    /// Launches with a known settings state.
    ///
    /// Pinned through `NSArgumentDomain` exactly as `ParentalGateUITests` does.
    /// This suite writes nothing back — it only reads About — but the pin is what
    /// stops it *inheriting* a disabled category left behind by the suite that
    /// runs before it, which is how sixteen `WordsModeUITests` went red at once.
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-cm_lang", "en",
            "-cm_muted", "YES",
            "-cm_enabled_langs", "(en,zh,ms,ja,tl)",
            "-cm_enabled_cats", "(fruits,food,animals,vehicles,nature,objects,people,faces)",
            // Pinned for the same reason the four above are, and it is the most
            // dangerous of the five: without it a fresh simulator opens the
            // first-launch tour over the app and every assertion in this suite
            // measures a sheet instead of the screen it meant to. `TutorialUITests`
            // is the one suite that leaves it off.
            "-cm_seen_tutorial", "YES",
        ]
        app.launch()
        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForExistence(timeout: 30),
            "the app never reached Words mode — nothing below this can mean anything"
        )
        return app
    }

    /// Type-agnostic lookup.
    ///
    /// Load-bearing, not tidiness: a `List` is a table and a `DisclosureGroup` row
    /// is neither a button nor an `Other`, so `app.otherElements["about-privacy"]`
    /// is false whether About is open or shut — one of the exact shapes of dead
    /// test this project keeps finding.
    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    /// Swipes until `identifier` is laid out, then returns it.
    ///
    /// Not a convenience. A `List` publishes only the rows it has actually laid
    /// out, so a row below the fold does not exist yet and `waitForExistence`
    /// alone waits out its timeout and reports "missing" for a row that is simply
    /// further down. Both screens here are taller than a 6.9" phone.
    @discardableResult
    private func scrollTo(
        _ identifier: String, in app: XCUIApplication,
        file: StaticString = #filePath, line: UInt = #line
    ) -> XCUIElement {
        let target = element(identifier, in: app)
        for _ in 0..<12 where !target.exists {
            app.swipeUp()
        }
        XCTAssertTrue(
            target.waitForExistence(timeout: 5),
            "\(identifier) is not on screen after scrolling to the bottom",
            file: file, line: line
        )
        return target
    }

    /// Opens ⚙️, answers the question read off the screen, and lands in Settings.
    private func openSettings(_ app: XCUIApplication) {
        let gear = app.buttons["parent-btn"]
        XCTAssertTrue(gear.waitForExistence(timeout: 5), "there is no gear in the header")
        gear.tap()

        let question = app.staticTexts["gate-question"]
        XCTAssertTrue(question.waitForExistence(timeout: 5), "the gate did not open")
        // Read rather than assumed: the challenge rotates on every attempt, so a
        // hard-coded 7 × 8 would be wrong for every run after the first.
        let numbers = question.label
            .components(separatedBy: CharacterSet.decimalDigits.inverted)
            .compactMap(Int.init)
        XCTAssertEqual(numbers.count, 2, "could not read a question out of \"\(question.label)\"")
        guard numbers.count == 2 else { return }

        let field = app.textFields["gate-input"]
        XCTAssertTrue(field.waitForExistence(timeout: 5), "the gate has no input field")
        field.tap()
        field.typeText(String(numbers[0] * numbers[1]))
        app.buttons["gate-submit"].tap()

        XCTAssertTrue(
            element("settings-lang-en", in: app).waitForExistence(timeout: 5),
            "Settings did not open"
        )
        // About is the last section of five, below the fold on a 6.9" screen.
        scrollTo("settings-about-row", in: app)
    }

    /// The whole route, and the rows once it arrives.
    ///
    /// One test rather than four, deliberately: every assertion below needs the
    /// same forty-odd seconds of gate-and-navigate, and this simulator drops runs
    /// when a suite grows.
    func testAboutIsReachableFromSettingsAndItsRowsKeepTheirIdentifiers() {
        let app = launch()
        openSettings(app)

        let row = element("settings-about-row", in: app)
        XCTAssertGreaterThanOrEqual(
            row.frame.height, Self.parentMinimum - Self.tolerance,
            "the About row is \(row.frame.height)pt tall, under the 44pt HIG floor"
        )
        row.tap()

        // Each row's own identifier. Walked top to bottom because `scrollTo` only
        // ever swipes forward.
        for id in ["about-how-to-use", "about-guided-access", "about-languages",
                   "about-privacy", "about-terms", "about-v1-0-ios", "about-web-history"] {
            let entry = scrollTo(id, in: app)
            // Every disclosure row is a parent target and must clear 44pt.
            XCTAssertGreaterThanOrEqual(
                entry.frame.height, Self.parentMinimum - Self.tolerance,
                "the \(id) row is \(entry.frame.height)pt tall, under the 44pt HIG floor"
            )

            guard id == "about-privacy" else { continue }
            // Shut, the answer is nowhere; open, it is readable; shut again, it is
            // gone. Asserting all three is what stops this passing against a
            // `DisclosureGroup` that renders its body unconditionally *and*
            // against one that never opens at all — and closing it again keeps the
            // rows below from being pushed a screenful down for the next loop.
            let disclosed = app.staticTexts.containing(
                NSPredicate(format: "label CONTAINS %@", "Data Not Collected")
            ).firstMatch
            XCTAssertFalse(disclosed.exists, "the privacy answer is showing before the row was opened")

            entry.tap()
            XCTAssertTrue(
                disclosed.waitForExistence(timeout: 5),
                "tapping Privacy Policy did not reveal its answer"
            )
            entry.tap()
            XCTAssertTrue(
                disclosed.waitForNonExistence(timeout: 5),
                "the Privacy Policy row would not close again"
            )
        }
    }

    /// The single riskiest item in a Kids Category review is an outbound link, and
    /// the deliberate difference from the web is that there is not one. Nothing on
    /// this screen may offer to leave the app.
    ///
    /// Read off the live screen rather than off `AboutView.faq`, so it also covers
    /// a link added in the view rather than in the copy — a `Link`, a Ko-fi button
    /// or a `UIApplication.shared.open` would all publish a label here.
    func testNothingOnTheAboutScreenLeavesTheApp() {
        let app = launch()
        openSettings(app)
        element("settings-about-row", in: app).tap()
        XCTAssertTrue(
            element("about-how-to-use", in: app).waitForExistence(timeout: 5),
            "About did not open"
        )

        // Swept down the whole panel rather than sampled at the top: a `List`
        // publishes only the rows it has laid out, so a Ko-fi button in the last
        // section would be invisible to a single dump of the first screenful.
        //
        // Static texts, buttons and links rather than `descendants(matching:
        // .any)`: five dumps of the whole tree took three and a half minutes on
        // this simulator, and every way out of the app is one of these three —
        // copy is a `staticText`, a Ko-fi button is a `button`, and a SwiftUI
        // `Link` is a `link`.
        var labels: [String] = []
        for _ in 0..<5 {
            for query in [app.staticTexts, app.buttons, app.links] {
                labels += query.allElementsBoundByIndex.map { "\($0.label) \($0.value ?? "")" }
            }
            app.swipeUp()
        }
        XCTAssertGreaterThan(labels.count, 10, "only \(labels.count) elements — About is not really on screen")
        XCTAssertTrue(
            labels.contains { $0.contains("Version history") },
            "the sweep never reached the bottom of the panel, so it proves nothing about what is down there"
        )
        for banned in ["ko-fi", "Ko-fi", "http", "Buy us a coffee", "Open in Safari"] {
            for label in labels where label.contains(banned) {
                XCTFail("\"\(banned)\" is on the About screen, in \"\(label)\"")
            }
        }
    }
}
