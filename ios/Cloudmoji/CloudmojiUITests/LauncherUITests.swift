import XCTest

/// The launcher, driven the way a two-year-old drives it.
///
/// This suite inherits the promise `CountModeUITests.testTheTabBarSwitchesBetweenTheTwoModes`
/// used to make — that every screen in the app is reachable, and that only one of
/// them is on at a time — and extends it from two modes to seven mini-apps.
///
/// Every loop asserts its collection is **non-empty first**. A suite that
/// silently measures zero tiles is worse than no suite at all: it reports green
/// at the last gate before the app reaches a child's hands, which has already
/// happened three times on this project.
final class LauncherUITests: XCTestCase {

    /// `CLAUDE.md` rule 1's floor for anything a **child** taps — and a launcher
    /// tile and the cloud home button are squarely that.
    private static let childMinimum: CGFloat = 64
    /// `CLAUDE.md` rule 2. Two touching targets are one wide target as far as a
    /// toddler's aim is concerned.
    private static let minimumGap: CGFloat = 8
    /// Frames arrive from the accessibility layer as floats; a twentieth of a
    /// point is rounding, not a violation.
    private static let tolerance: CGFloat = 0.05

    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
    }

    // MARK: - Harness

    /// Everything the settings state has to be pinned to, so this suite is
    /// hermetic against whatever the suite before it left on the simulator.
    /// `-cm_open` is deliberately absent: the launcher is what this suite
    /// measures.
    private static let contentPins = [
        "-cm_use_stub_entitlements", "YES",
        "-cm_lang", "en",
        "-cm_muted", "YES",
        "-cm_enabled_langs", "(en,zh,ms,ja,tl)",
        "-cm_enabled_cats", "(fruits,food,animals,vehicles,nature,objects,people,faces)",
        // Without this a fresh simulator opens the first-launch tour over the
        // app and every assertion below measures a sheet.
        "-cm_seen_tutorial", "YES",
    ]

    /// Launches on the launcher itself.
    private func launch(extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        let entitlementPins = extraArguments.contains("-cm_premium_unlocked")
            ? []
            : ["-cm_premium_unlocked", "YES"]
        app.launchArguments = Self.contentPins + entitlementPins + extraArguments
        app.launch()
        XCTAssertTrue(
            app.buttons["launcher-tile-words"].waitForExistence(timeout: 30),
            "the launcher never appeared — nothing below this can mean anything"
        )
        return app
    }

    private func tiles(_ app: XCUIApplication) -> XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "launcher-tile-"))
    }

    /// Measures every element in a query against the child-facing floor, having
    /// first insisted the query found something.
    @discardableResult
    private func assertMeetsChildMinimum(
        _ query: XCUIElementQuery, named what: String, atLeast expected: Int,
        file: StaticString = #filePath, line: UInt = #line
    ) -> [CGRect] {
        let elements = query.allElementsBoundByIndex
        XCTAssertGreaterThanOrEqual(
            elements.count, expected,
            "found \(elements.count) \(what)s, expected at least \(expected) — "
            + "an empty query measures nothing and passes everything",
            file: file, line: line
        )
        var frames: [CGRect] = []
        for element in elements {
            let frame = element.frame
            frames.append(frame)
            XCTAssertGreaterThanOrEqual(
                frame.width, Self.childMinimum - Self.tolerance,
                "a \(what) is \(frame.width)pt wide", file: file, line: line
            )
            XCTAssertGreaterThanOrEqual(
                frame.height, Self.childMinimum - Self.tolerance,
                "a \(what) is \(frame.height)pt tall", file: file, line: line
            )
        }
        return frames
    }

    // MARK: - What is on the launcher

    /// Seven tiles, all of them big enough, none of them touching.
    ///
    /// Mutation: remove the icon cell's minimum height. The size assertion fails
    /// even though the visible 76pt squircle still looks plausible.
    func testTheLauncherShowsSevenChildSizedTiles() {
        let app = launch()

        let frames = assertMeetsChildMinimum(tiles(app), named: "launcher tile", atLeast: 7)
        XCTAssertEqual(frames.count, 7, "the launcher drew \(frames.count) tiles rather than seven")

        // Rule 2, between neighbours. Two rects sharing neither a row nor a
        // column are diagonal, and the gap that matters between them is the one
        // their neighbours already enforce.
        var comparisons = 0
        for (index, a) in frames.enumerated() {
            for b in frames[(index + 1)...] {
                XCTAssertFalse(a.intersects(b), "two launcher tiles overlap: \(a) and \(b)")
                if a.minY < b.maxY && b.minY < a.maxY {
                    let gap = a.minX < b.minX ? b.minX - a.maxX : a.minX - b.maxX
                    XCTAssertGreaterThanOrEqual(
                        gap, Self.minimumGap - Self.tolerance,
                        "launcher tiles \(a) and \(b) are \(gap)pt apart horizontally"
                    )
                    comparisons += 1
                }
            }
        }
        XCTAssertGreaterThanOrEqual(comparisons, 3, "only \(comparisons) tile pairs shared a row")
        XCTAssertFalse(
            app.buttons["launcher-full-cloudmoji"].exists,
            "the Full discovery door should disappear after Full is unlocked"
        )
    }

    /// Every mini-app has a tile, by the raw value that is also its deep link.
    ///
    /// Mutation: rename any case's raw value without updating `MiniApp`. The tile
    /// this looks for is missing and this fails, naming it.
    func testEveryMiniAppHasATile() {
        let app = launch()
        for raw in ["words", "count", "flashcards", "instrument", "animalsounds", "photos", "sleepy"] {
            XCTAssertTrue(
                app.buttons["launcher-tile-\(raw)"].exists,
                "there is no launcher tile for \(raw)"
            )
        }
    }

    /// The entitlement decides what is on the launcher, and it is the *only*
    /// thing that does.
    ///
    /// Mutation: return `MiniApp.allCases` from `visibleMiniApps`. The locked
    /// launch still shows seven tiles and this fails.
    func testLockingFullLeavesWordsAndCount() {
        let app = launch(extraArguments: ["-cm_premium_unlocked", "NO"])

        let frames = assertMeetsChildMinimum(tiles(app), named: "launcher tile", atLeast: 2)
        XCTAssertEqual(frames.count, 2, "a free launcher drew \(frames.count) tiles rather than two")

        for raw in ["words", "count"] {
            XCTAssertTrue(app.buttons["launcher-tile-\(raw)"].exists, "\(raw) should always be there")
        }
        for raw in ["instrument", "flashcards", "animalsounds", "photos", "sleepy"] {
            XCTAssertFalse(app.buttons["launcher-tile-\(raw)"].exists, "\(raw) is on a free launcher")
        }

        let grownUps = app.buttons["launcher-full-cloudmoji"]
        XCTAssertTrue(grownUps.exists, "the free launcher has no visible route to Full Cloudmoji")
        XCTAssertGreaterThanOrEqual(grownUps.frame.width, Self.childMinimum - Self.tolerance)
        XCTAssertGreaterThanOrEqual(grownUps.frame.height, Self.childMinimum - Self.tolerance)
        XCTAssertEqual(grownUps.label, "For grown-ups")
        for commercialWord in ["buy", "price", "upgrade", "$"] {
            XCTAssertFalse(
                grownUps.label.lowercased().contains(commercialWord),
                "the child-facing doorway contains commercial copy: \(grownUps.label)"
            )
        }
    }

    /// Purchase status belongs in the gated parent panel. Once an app is
    /// available, its child-facing tile is just an app — no "extra" badge and no
    /// commercial language in VoiceOver.
    func testTheChildLauncherHasNoCommercialBadges() {
        let app = launch()
        for raw in ["words", "count", "flashcards", "instrument", "animalsounds", "photos", "sleepy"] {
            let label = app.buttons["launcher-tile-\(raw)"].label
            XCTAssertFalse(
                label.contains("extra"),
                "\(raw)'s child-facing tile is labelled \"\(label)\""
            )
        }
    }

    /// Sound and language are parent choices on the launcher, so the child gets
    /// one labelled, gated door rather than three adjacent utility controls.
    func testTheLauncherHasOneParentDoorInsteadOfLooseSettings() {
        let app = launch()
        XCTAssertTrue(app.buttons["parent-btn"].exists)
        XCTAssertFalse(app.buttons["mute-btn"].exists)
        XCTAssertFalse(app.buttons["lang-picker"].exists)
    }

    /// A muted audio activity must not look broken to the next child who opens
    /// it. The recovery target is deliberately child-sized and disappears once
    /// used, leaving sound on for the rest of the session.
    func testMutedAudioAppsOfferAChildSizedWayBackToSound() {
        let app = XCUIApplication()
        app.launchArguments = Self.contentPins
            + ["-cm_premium_unlocked", "YES", "-cm_open", "sleepy"]
        app.launch()

        let recovery = app.buttons["sound-recovery-btn"]
        XCTAssertTrue(recovery.waitForExistence(timeout: 30))
        XCTAssertGreaterThanOrEqual(recovery.frame.width, Self.childMinimum - Self.tolerance)
        XCTAssertGreaterThanOrEqual(recovery.frame.height, Self.childMinimum - Self.tolerance)

        recovery.tap()
        XCTAssertTrue(recovery.waitForNonExistence(timeout: 2),
                      "the recovery button remained after sound was turned on")
    }

    /// Sleepy Cloud belongs to both halves of the family. Its durations are
    /// child-sized and begin immediately; there is no parental gate between a
    /// sleepy toddler and the breathing session.
    func testSleepyCloudStartsWithoutAParentalGate() {
        let app = XCUIApplication()
        app.launchArguments = Self.contentPins
            + ["-cm_premium_unlocked", "YES", "-cm_open", "sleepy"]
        app.launch()

        let duration = app.buttons["sleepy-duration-2"]
        XCTAssertTrue(duration.waitForExistence(timeout: 30))
        XCTAssertGreaterThanOrEqual(duration.frame.width, Self.childMinimum - Self.tolerance)
        XCTAssertGreaterThanOrEqual(duration.frame.height, Self.childMinimum - Self.tolerance)
        duration.tap()

        XCTAssertTrue(app.staticTexts["sleepy-phase"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["gate-question"].exists)
    }

    // MARK: - Navigation

    /// **The promise the tab bar used to make**, for all seven mini-apps: each
    /// tile opens its own screen, the launcher goes away while it is open, the
    /// cloud is there, and tapping the cloud brings the launcher back.
    ///
    /// Mutation: delete `active = nil` from `RootContent.goHome`. The launcher
    /// never comes back and this fails on the first mini-app.
    func testEachTileOpensItsOwnMiniAppAndTheCloudBringsYouBack() {
        let app = launch()

        // Something on each screen that only that screen draws, so "it opened"
        // is not merely "the launcher went away".
        let landmarks: [(String, XCUIElement)] = [
            ("words", app.buttons["emoji-🍎"]),
            ("count", app.buttons["count-item-0"]),
            ("flashcards", app.buttons["flash-replay"]),
            ("instrument", app.buttons["pad-0"]),
            ("animalsounds", app.buttons["emoji-🐶"]),
            ("photos", app.descendants(matching: .any).matching(identifier: "photos-panel").firstMatch),
            ("sleepy", app.buttons["sleepy-duration-2"]),
        ]

        for (raw, landmark) in landmarks {
            let tile = app.buttons["launcher-tile-\(raw)"]
            XCTAssertTrue(tile.waitForExistence(timeout: 10), "the launcher did not come back before \(raw)")
            tile.tap()

            XCTAssertTrue(
                landmark.waitForExistence(timeout: 10),
                "tapping \(raw) did not bring up its own screen"
            )
            XCTAssertFalse(
                app.buttons["launcher-tile-words"].exists,
                "the launcher is still on screen underneath \(raw)"
            )

            let home = app.buttons["home-btn"]
            XCTAssertTrue(home.waitForExistence(timeout: 5), "\(raw) has no way home")
            home.tap()
            XCTAssertTrue(
                app.buttons["launcher-tile-words"].waitForExistence(timeout: 10),
                "the cloud did not bring the launcher back from \(raw)"
            )
        }
    }

    /// The way out is the same size everywhere, because it is the same control
    /// everywhere.
    ///
    /// Mutation: delete `.frame(width:height:)` from `CloudHomeButton`. The
    /// target collapses to the mascot's own art box and this fails.
    func testTheHomeButtonIsChildSizedInEveryMiniApp() {
        for raw in ["words", "count", "flashcards", "instrument", "animalsounds", "photos", "sleepy"] {
            // Deliberately not `launch()`: that helper waits for a launcher tile,
            // and `-cm_open` means there is no launcher to wait for. Waiting for
            // the wrong landmark is how this test failed the first time it ran.
            let app = XCUIApplication()
            app.launchArguments = Self.contentPins
                + ["-cm_premium_unlocked", "YES", "-cm_open", raw]
            app.launch()

            XCTAssertTrue(
                app.buttons["home-btn"].waitForExistence(timeout: 30),
                "\(raw) never drew a home button — nothing below this can mean anything"
            )
            assertMeetsChildMinimum(
                app.buttons.matching(identifier: "home-btn"),
                named: "home button in \(raw)", atLeast: 1
            )
            app.terminate()
        }
    }

    // MARK: - The gate

    /// The gate covers the launcher, not just the screen behind it. A gate a
    /// child can tap around is not a gate — and the launcher is the one screen
    /// where the gear and the tiles are on screen together.
    ///
    /// Mutation: move the `.overlay` inside the `if let active` branch of
    /// `RootContent.body`. The tile tap goes through and this fails.
    func testTheGateCoversTheLauncherTiles() {
        let app = launch()

        app.buttons["parent-btn"].tap()
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "parental-gate").firstMatch
                .waitForExistence(timeout: 10),
            "the gear did not open the gate"
        )

        // A tile underneath the gate must not answer. `tap()` on a covered
        // element still dispatches at its coordinates, which is exactly the
        // toddler behaviour being guarded against.
        let tile = app.buttons["launcher-tile-words"]
        if tile.isHittable { tile.tap() }

        XCTAssertFalse(
            app.buttons["emoji-🍎"].waitForExistence(timeout: 3),
            "a tap landed on a launcher tile through the gate"
        )
    }

    // MARK: - Sideways

    /// Rotating keeps the stable four-column Home Screen rhythm: still seven,
    /// still child-sized.
    ///
    /// Mutation: return 2 from `LauncherView.columns(compact:)` for both. The
    /// first-row assertion fails.
    func testRotatingKeepsEveryTileOnScreenAndChildSized() {
        let app = launch()
        XCUIDevice.shared.orientation = .landscapeLeft

        XCTAssertTrue(
            app.buttons["launcher-tile-sleepy"].waitForExistence(timeout: 10),
            "the last tile did not survive the rotation"
        )
        let frames = assertMeetsChildMinimum(tiles(app), named: "launcher tile", atLeast: 7)
        XCTAssertEqual(frames.count, 7, "sideways the launcher drew \(frames.count) tiles")

        // Four across: the top row has four tiles sharing a band of y.
        let firstRow = frames.filter { abs($0.minY - (frames.map(\.minY).min() ?? 0)) < 1 }
        XCTAssertEqual(firstRow.count, 4, "the landscape grid is \(firstRow.count) across, not four")
    }
}
