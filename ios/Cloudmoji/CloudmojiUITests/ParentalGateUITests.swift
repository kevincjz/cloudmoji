import XCTest

/// The parent door, driven end to end.
///
/// Everything here needs a real accessibility tree and can only be asked in this
/// target. `ParentalGateTests` proves `GateChallenge.accepts` rejects a wrong
/// answer; it cannot prove the **view** consults it, and a gate whose Continue
/// button is wired straight to `onPass` passes every unit test in the project
/// while opening for anything. Likewise `AppModelTests` proves the model narrows
/// its lists; only a running app can show that the child's screen followed.
final class ParentalGateUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    /// Launches with a known settings state.
    ///
    /// The defaults go through `NSArgumentDomain`, which outranks anything
    /// `SettingsStore` persisted on a previous run — otherwise a test that
    /// switches Tagalog off leaves it off for every test after it, and the ones
    /// that follow measure a state they did not set.
    private func launch(extraArguments: [String] = []) -> XCUIApplication {
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
        ] + extraArguments
        app.launch()
        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForExistence(timeout: 30),
            "the app never reached Words mode — nothing below this can mean anything"
        )
        return app
    }

    /// Opens the gate and reads the question off the screen, because the rotation
    /// advances on every attempt and hard-coding 7 × 8 would make every test after
    /// the first one wrong.
    @discardableResult
    private func openGate(_ app: XCUIApplication) -> (a: Int, b: Int) {
        let gear = app.buttons["parent-btn"]
        XCTAssertTrue(gear.waitForExistence(timeout: 5), "there is no gear in the header")
        // Parent chrome, so the 44pt HIG floor rather than the 64pt child one.
        // The tolerance is the same twentieth of a point `WordsModeUITests` uses:
        // frames arrive from the accessibility layer as floats and this control
        // measures 43.99999999999997.
        XCTAssertGreaterThanOrEqual(gear.frame.height, 44 - 0.05, "the gear is \(gear.frame.height)pt tall")
        XCTAssertGreaterThanOrEqual(gear.frame.width, 44 - 0.05, "the gear is \(gear.frame.width)pt wide")
        gear.tap()

        let question = app.staticTexts["gate-question"]
        XCTAssertTrue(question.waitForExistence(timeout: 5), "the gate did not open")
        let numbers = question.label
            .components(separatedBy: CharacterSet.decimalDigits.inverted)
            .compactMap(Int.init)
        XCTAssertEqual(numbers.count, 2, "could not read a question out of \"\(question.label)\"")
        guard numbers.count == 2 else { return (0, 0) }
        return (numbers[0], numbers[1])
    }

    /// Type-agnostic lookup.
    ///
    /// Load-bearing, not tidiness. A `Form` is a table, not an `Other`, so
    /// `app.otherElements["settings-panel"].exists` is false whether Settings is
    /// open or shut — and every `XCTAssertFalse` written that way would pass
    /// forever, including the one that is supposed to catch a gate letting a
    /// wrong answer through. That is one of the exact shapes of dead test this
    /// project keeps finding.
    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    /// Flips one Settings switch and proves it flipped.
    ///
    /// `element.tap()` taps the centre of the element's frame, and a `Toggle` in a
    /// `Form` publishes **one** accessibility element spanning the whole row — so
    /// the centre is over the label, where a tap does nothing at all. Every
    /// narrowing test below silently did nothing and blamed the model until this
    /// was measured. The switch itself sits at the trailing edge.
    ///
    /// The value assertion is the load-bearing part: without it a tap that misses
    /// again would be reported as "the narrowing is broken", which is a lie about
    /// a different file.
    private func flip(
        _ identifier: String, in app: XCUIApplication, to expected: String,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        let toggle = element(identifier, in: app)
        XCTAssertTrue(toggle.waitForExistence(timeout: 5), "\(identifier) is not on screen",
                      file: file, line: line)
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        XCTAssertEqual(
            element(identifier, in: app).value as? String, expected,
            "\(identifier) did not change state — the tap missed the switch",
            file: file, line: line
        )
    }

    private func type(_ text: String, into app: XCUIApplication) {
        let field = app.textFields["gate-input"]
        XCTAssertTrue(field.waitForExistence(timeout: 5), "the gate has no input field")
        field.tap()
        field.typeText(text)
    }

    // MARK: - The gate

    /// The one property the gate exists for. A wrong answer must leave the parent
    /// exactly where they were, and the right one must let them through — proved
    /// in the same test, so a gate that is simply broken shut cannot pass it
    /// either.
    ///
    /// The answer is computed from the question on screen rather than assumed, so
    /// this survives the rotation and would survive re-ordering the table.
    func testAWrongAnswerDoesNotOpenSettingsAndTheRightOneDoes() {
        let app = launch()
        let (a, b) = openGate(app)

        type(String(a * b + 1), into: app)
        app.buttons["gate-submit"].tap()

        XCTAssertTrue(
            app.staticTexts["gate-error"].waitForExistence(timeout: 3),
            "a wrong answer produced no correction — the gate did not evaluate it at all"
        )
        XCTAssertFalse(
            element("settings-panel", in: app).exists,
            "a wrong answer opened Settings — the gate is decorative"
        )
        XCTAssertTrue(app.staticTexts["gate-question"].exists, "the gate closed on a wrong answer")

        // And the door does open, so the assertion above is not passing merely
        // because the whole thing is stuck.
        type(String(a * b), into: app)
        app.buttons["gate-submit"].tap()

        XCTAssertTrue(
            element("settings-panel", in: app).waitForExistence(timeout: 5),
            "the right answer did not open Settings"
        )
    }

    /// A gate whose answer is printed next to the question is not a gate. Nothing
    /// on this screen may contain the product — not a placeholder, not a hint, not
    /// a leftover debug label.
    func testTheAnswerIsNowhereOnTheGateScreen() {
        let app = launch()
        let (a, b) = openGate(app)
        let answer = String(a * b)

        let labels = app.descendants(matching: .any).allElementsBoundByIndex
            .map { "\($0.label) \($0.value ?? "")" }
        XCTAssertGreaterThan(labels.count, 3, "only \(labels.count) elements — the gate is not really on screen")
        for label in labels where label.contains(answer) {
            XCTFail("the answer \(answer) is readable on the gate screen, in \"\(label)\"")
        }
    }

    /// Cancel is a toddler's way out. It must close the gate without opening
    /// anything.
    func testCancelClosesTheGateWithoutOpeningSettings() {
        let app = launch()
        openGate(app)

        app.buttons["gate-cancel"].tap()

        XCTAssertTrue(
            app.staticTexts["gate-question"].waitForNonExistence(timeout: 5),
            "Cancel left the gate on screen"
        )
        XCTAssertFalse(element("settings-panel", in: app).exists, "Cancel opened Settings")
        XCTAssertTrue(app.buttons["emoji-🍎"].isHittable, "the app did not come back after Cancel")
    }

    // MARK: - What Settings actually changes

    /// Settings is the only screen that shows a parent what they switched **off**,
    /// and the child's picker is the only one that must not.
    ///
    /// Switching three languages off in the real panel and then counting the
    /// child's picker is the whole feature in one test: the toggle writes through
    /// `SettingsStore`, `AppModel.availableLanguages` narrows, and `ModeHeader`
    /// consumes the narrowed list without branching on anything.
    func testSwitchingLanguagesOffNarrowsTheChildsPicker() {
        let app = launch()
        let (a, b) = openGate(app)
        type(String(a * b), into: app)
        app.buttons["gate-submit"].tap()
        XCTAssertTrue(
            element("settings-lang-ms", in: app).waitForExistence(timeout: 5),
            "Settings did not open, or its rows lost their identifiers to the panel's"
        )

        // All five are listed even though the test is about to disable three —
        // Settings reads past the filter, and this is the assertion that says so.
        for language in ["en", "zh", "ms", "ja", "tl"] {
            XCTAssertTrue(
                element("settings-lang-\(language)", in: app).exists,
                "settings-lang-\(language) is missing — Settings is showing the filtered list"
            )
        }

        for language in ["ms", "ja", "tl"] {
            flip("settings-lang-\(language)", in: app, to: "0")
        }
        app.buttons["settings-done"].tap()

        let picker = app.descendants(matching: .any).matching(identifier: "lang-picker").firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 5), "the header picker did not come back")
        picker.tap()

        // The menu's options are the only place the narrowing is visible. English
        // and 中文 stay; the three that were switched off must be gone.
        for name in ["BM", "日本語", "TL"] {
            XCTAssertFalse(
                app.buttons[name].waitForExistence(timeout: 1),
                "\(name) is still offered to the child after being switched off in Settings"
            )
        }
        XCTAssertTrue(app.buttons["中文"].exists, "中文 was left on but is not in the picker")
        XCTAssertTrue(app.buttons["EN"].exists, "EN was left on but is not in the picker")
    }

    /// The same proof on the other axis, and the one a parent actually notices:
    /// a category switched off takes its emojis out of the child's grid.
    func testSwitchingACategoryOffEmptiesItFromTheGrid() {
        let app = launch()
        XCTAssertTrue(app.buttons["cat-fruits"].exists, "setup: Fruits should be on the strip")

        let (a, b) = openGate(app)
        type(String(a * b), into: app)
        app.buttons["gate-submit"].tap()
        flip("settings-cat-fruits", in: app, to: "0")
        app.buttons["settings-done"].tap()

        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForNonExistence(timeout: 5),
            "the apple is still in the grid after Fruits was switched off"
        )
        XCTAssertFalse(app.buttons["cat-fruits"].exists, "the Fruits chip survived being switched off")
        // The rest of the app is untouched — a narrowing, not a blanking.
        XCTAssertTrue(app.buttons["emoji-🐶"].exists, "the dog went with the fruit")
        XCTAssertTrue(app.buttons["cat-animals"].exists, "the Animals chip went with the fruit")
    }

    /// `SettingsStore` silently re-enables everything when the last switch goes
    /// off. Settings must not let a parent reach that: the last one on is greyed
    /// out, and tapping it changes nothing.
    ///
    /// Pre-seeded to a single enabled language rather than tapping four toggles,
    /// so the assertion is about the greying and not about the route to it.
    func testTheLastLanguageCannotBeSwitchedOff() {
        let app = launch(extraArguments: ["-cm_enabled_langs", "(zh)", "-cm_lang", "zh"])
        let (a, b) = openGate(app)
        type(String(a * b), into: app)
        app.buttons["gate-submit"].tap()

        let last = element("settings-lang-zh", in: app)
        XCTAssertTrue(last.waitForExistence(timeout: 5), "Settings did not open")
        XCTAssertFalse(last.isEnabled, "the only language left on is still switchable off")

        // A disabled switch may still be tapped; nothing may come of it. Aimed at
        // the switch itself, not the row's centre, so this is a real attempt.
        last.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        XCTAssertEqual(
            element("settings-lang-zh", in: app).value as? String, "1",
            "the last language switched itself off"
        )
        // And the four that are already off are still switchable back on, which is
        // the thing the greying must not take away.
        XCTAssertTrue(
            element("settings-lang-en", in: app).isEnabled,
            "a language that is already off was greyed out too — there is no way back"
        )
    }

    /// Switching off the category the child is CURRENTLY on must not strand them
    /// on a blank grid.
    ///
    /// The existing test above starts on "All", where the bug is invisible. Here
    /// the child is on Animals — the likeliest real sequence, since a parent
    /// narrowing the app is usually reacting to what is on screen. Before the
    /// fix the grid went empty, no chip was highlighted, and every tap did
    /// nothing: a failure state, which `CLAUDE.md` rule 4 forbids.
    ///
    /// Mutation: delete the `.onChange(of: model.categories.map(\.id))` handler
    /// in `WordsView`.
    func testDisablingTheCategoryTheChildIsOnFallsBackToAll() {
        let app = launch()
        app.buttons["cat-animals"].tap()
        XCTAssertTrue(
            app.buttons["emoji-🐶"].waitForExistence(timeout: 5),
            "setup: the dog should be on screen under Animals"
        )

        let (a, b) = openGate(app)
        type(String(a * b), into: app)
        app.buttons["gate-submit"].tap()
        flip("settings-cat-animals", in: app, to: "0")
        app.buttons["settings-done"].tap()

        // The child is left somewhere usable, not on an empty screen.
        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForExistence(timeout: 5),
            "the grid is empty — the child is stranded on a category that no longer exists"
        )
        XCTAssertFalse(
            app.buttons["emoji-🐶"].exists,
            "the dog is still showing after Animals was switched off"
        )
    }
}
