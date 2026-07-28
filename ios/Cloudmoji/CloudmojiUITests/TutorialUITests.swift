import XCTest

/// The welcome tour, driven through the flag that actually persists it.
///
/// The behaviour worth testing here is not "the sheet renders" — a test that
/// only asserts that passes against a tour that appears on every single launch,
/// which is the failure mode a parent would actually notice. What has to be
/// proved is the *transition*: shown on the first launch, gone on the second,
/// and reachable again from Settings afterwards.
///
/// So the first test below deliberately does **not** pin `cm_seen_tutorial` on
/// its second launch. Every other suite pins it, and must — a first-launch sheet
/// over the app takes all twenty-four of their assertions with it. This is the
/// one suite that lets the real persisted value decide, because that value is
/// the subject.
///
/// Mutations **actually run** against this suite, both of which took it red:
///
/// * delete `.onDisappear { model.settings.seenTutorial = true }` in `ContentView`
///   → `testTheTourAppearsOnFirstLaunchAndNotOnTheNext` fails on the relaunch,
///   with "the tour came back on the second launch". This is the mutation that
///   the first version of the suite survived, twice — once because the assertion
///   raced the sheet's presentation (see ``assertNoTour``) and once because the
///   simulator's container was contaminated (see `launch(_:tourFlag:resettingContainer:)`).
/// * delete `.accessibilityElement(children: .contain)` from `TutorialView`
///   → the same test fails with "there is no Got it button". So that modifier is
///   load-bearing here, unlike on `AboutView`'s `List`, where it was measured to
///   be a no-op. This one wraps a plain `VStack`, so the panel's identifier does
///   propagate down and swallow the children.
///
/// Three more follow directly from the assertions rather than having been run,
/// and are recorded as reasoning, not as measurement: deleting the `.task` that
/// presents the sheet, dropping its `guard !model.settings.seenTutorial`, and
/// deleting the `settings-tutorial-row` link each remove an element that a
/// `waitForExistence` here requires.
final class TutorialUITests: XCTestCase {

    /// Parent chrome, so the 44pt iOS HIG floor. Frames arrive from the
    /// accessibility layer as floats, hence the same twentieth of a point the
    /// other three suites allow.
    private static let parentMinimum: CGFloat = 44
    private static let tolerance: CGFloat = 0.05

    override func setUp() {
        continueAfterFailure = false
    }

    /// Everything except the tour flag, which each test decides for itself.
    ///
    /// Pinned through `NSArgumentDomain` exactly as the other suites do, so this
    /// one cannot inherit a disabled category from whichever suite ran before —
    /// that inheritance is what took sixteen `WordsModeUITests` red at once.
    private static let contentPins = [
        "-cm_lang", "en",
        "-cm_muted", "YES",
        "-cm_enabled_langs", "(en,zh,ms,ja,tl)",
        "-cm_enabled_cats", "(fruits,food,animals,vehicles,nature,objects,people,faces)",
    ]

    /// - Parameter tourFlag: pins `cm_seen_tutorial` through `NSArgumentDomain`.
    ///   Pass `nil` to leave the decision to whatever the app itself persisted —
    ///   which is the whole point of the relaunch below, and the one place in the
    ///   UI suites where the key is deliberately *not* pinned.
    /// - Parameter resettingContainer: wipes the app's persisted settings before
    ///   `SettingsStore` reads them. Without it this suite is not hermetic: the
    ///   flag is only ever written false → true, so a simulator that has run the
    ///   tour once reports "seen" forever and the relaunch assertion passes even
    ///   with the write deleted from `ContentView`. That was measured, not
    ///   assumed — it is why this parameter exists.
    private func launch(
        _ app: XCUIApplication, tourFlag: String?, resettingContainer: Bool = false
    ) {
        app.launchArguments =
            (resettingContainer ? ["-cm_reset_persisted_settings", "YES"] : [])
            + Self.contentPins
            + (tourFlag.map { ["-cm_seen_tutorial", $0] } ?? [])
        app.launch()
    }

    /// Type-agnostic lookup.
    ///
    /// Load-bearing, not tidiness: the tour's rows are combined accessibility
    /// elements and its panel is a container, neither of which is reliably a
    /// button or an `Other` — `app.otherElements["tutorial-panel"]` is the exact
    /// shape of assertion this project keeps finding unable to fail.
    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    /// Waits for the child's screen, so a failure below cannot be "the app never
    /// started" wearing the costume of a tour bug.
    private func waitForWordsMode(_ app: XCUIApplication) {
        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForExistence(timeout: 30),
            "the app never reached Words mode — nothing below this can mean anything"
        )
    }

    // MARK: - The one property this feature exists for

    /// First launch shows it; the launch after dismissing it does not.
    ///
    /// The second launch passes **no** tour argument at all, so the only thing
    /// that can suppress the sheet is the value `SettingsStore` wrote to the app
    /// domain when the first one was dismissed. That is what makes this a test
    /// of persistence rather than of a launch argument being honoured.
    func testTheTourAppearsOnFirstLaunchAndNotOnTheNext() {
        let app = XCUIApplication()
        launch(app, tourFlag: "NO", resettingContainer: true)

        let panel = element("tutorial-panel", in: app)
        XCTAssertTrue(
            panel.waitForExistence(timeout: 30),
            "the tour did not appear on a launch with cm_seen_tutorial off"
        )

        // The five things it has to say are actually on the sheet, not merely in
        // a static array — `TutorialViewTests` can only prove the array.
        for id in ["tap", "modes", "typing-row", "mute", "settings"] {
            XCTAssertTrue(
                element("tutorial-step-\(id)", in: app).exists,
                "tutorial-step-\(id) is missing, or the panel's identifier swallowed the rows"
            )
        }
        // The one fact the tour exists to deliver, read off the live screen.
        let mute = element("tutorial-step-mute", in: app)
        XCTAssertTrue(
            mute.label.contains("silent switch"),
            "the mute step on screen says \"\(mute.label)\" — the silent-switch warning is gone"
        )

        // One tap, on a control that is on screen without scrolling. If the
        // button were below the fold this would be false and a two-year-old
        // would be stuck behind it.
        let done = element("tutorial-done", in: app)
        XCTAssertTrue(done.waitForExistence(timeout: 5), "there is no Got it button")
        XCTAssertTrue(done.isHittable, "the Got it button is off screen — the tour is a wall")
        XCTAssertGreaterThanOrEqual(
            done.frame.height, Self.parentMinimum - Self.tolerance,
            "the Got it button is \(done.frame.height)pt tall, under the 44pt HIG floor"
        )
        done.tap()

        XCTAssertTrue(
            panel.waitForNonExistence(timeout: 10),
            "Got it did not close the tour"
        )
        waitForWordsMode(app)
        XCTAssertTrue(app.buttons["emoji-🍎"].isHittable,
                      "the app is not tappable after the tour closed")

        // The whole point. No tour argument this time: whatever the first launch
        // persisted is the only thing deciding.
        app.terminate()
        launch(app, tourFlag: nil)
        waitForWordsMode(app)
        assertNoTour(
            app,
            "the tour came back on the second launch — dismissing it was not persisted"
        )
    }

    /// A negative wait, not an immediate `exists` check.
    ///
    /// This is the correction that made the whole suite mean something. The first
    /// version asked `element("tutorial-panel").exists` the instant `emoji-🍎`
    /// appeared — and a tile `exists` while a sheet is sitting on top of it, so
    /// the question was being asked before the sheet had had a chance to present.
    /// Deleting the persistence write from `ContentView` left every test green.
    ///
    /// `waitForExistence` returning false after a real timeout is the honest form
    /// of "it never showed up". The hittability check is a second, independent
    /// signal: a modal sheet makes the grid untappable, so it catches a tour that
    /// somehow published no identifier at all.
    private func assertNoTour(
        _ app: XCUIApplication, _ message: String,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        XCTAssertFalse(
            element("tutorial-panel", in: app).waitForExistence(timeout: 5),
            message, file: file, line: line
        )
        XCTAssertTrue(
            app.buttons["emoji-🍎"].isHittable,
            "something is covering the grid — \(message)",
            file: file, line: line
        )
    }

    /// The pin every other suite depends on.
    ///
    /// If `cm_seen_tutorial` ever stops suppressing the sheet, `WordsModeUITests`,
    /// `ParentalGateUITests` and `AboutUITests` all start measuring a tour that is
    /// covering the app — twenty-four tests failing for a reason none of their
    /// messages would name. This says it in one place.
    func testTheTourStaysAwayWhenItHasBeenSeen() {
        let app = XCUIApplication()
        launch(app, tourFlag: "YES", resettingContainer: true)
        waitForWordsMode(app)
        assertNoTour(
            app,
            "cm_seen_tutorial=YES did not suppress the tour — every other UI suite is now measuring a sheet"
        )
        XCTAssertFalse(element("tutorial-done", in: app).exists,
                       "the Got it button is on screen over the child's grid")
    }

    // MARK: - Getting it back

    /// The reason the request was made: a parent who dismissed it too quickly —
    /// or whose child dismissed it for them — must be able to read it again.
    ///
    /// Driven the whole way a parent reaches it: ⚙️ → gate → Settings → the row.
    /// Launched with the tour already seen, so the panel that appears at the end
    /// can only have come from the row that was tapped.
    func testTheSettingsRowReopensTheTour() {
        let app = XCUIApplication()
        launch(app, tourFlag: "YES", resettingContainer: true)
        waitForWordsMode(app)

        let gear = app.buttons["parent-btn"]
        XCTAssertTrue(gear.waitForExistence(timeout: 5), "there is no gear in the header")
        gear.tap()

        let question = app.staticTexts["gate-question"]
        XCTAssertTrue(question.waitForExistence(timeout: 5), "the gate did not open")
        // Read rather than assumed: the challenge rotates on every attempt.
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

        // The row is in the last section, below the fold on a 6.9" screen. A
        // `Form` publishes only the rows it has laid out, so this has to be
        // scrolled to rather than merely waited for.
        let row = element("settings-tutorial-row", in: app)
        for _ in 0..<12 where !row.exists {
            app.swipeUp()
        }
        XCTAssertTrue(
            row.waitForExistence(timeout: 5),
            "there is no How to use Cloudmoji row in Settings — the tour is unreachable once dismissed"
        )
        XCTAssertGreaterThanOrEqual(
            row.frame.height, Self.parentMinimum - Self.tolerance,
            "the row is \(row.frame.height)pt tall, under the 44pt HIG floor"
        )
        row.tap()

        XCTAssertTrue(
            element("tutorial-step-mute", in: app).waitForExistence(timeout: 5),
            "the row did not open the tour"
        )
        // Pushed, not presented as a first run: the dismissal button belongs to
        // the sheet only, and here the navigation bar's back control is the way
        // out. A Got it button in this presentation would be a second, competing
        // exit.
        XCTAssertFalse(
            element("tutorial-done", in: app).exists,
            "the pushed tour has a Got it button as well as a back button"
        )
    }
}
