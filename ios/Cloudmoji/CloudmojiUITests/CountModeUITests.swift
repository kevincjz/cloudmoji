import XCTest

/// Count mode and the mode tab bar, driven the way a two-year-old drives them.
///
/// Two things had no permanent coverage anywhere in the project before this file:
///
/// 1. **The tab bar's 64pt guarantee.** It was measured once by hand, by a throwaway
///    test that was deleted the same day. The web shipped this control at 42.5pt on
///    notched phones — `box-sizing: border-box` folded the home-indicator inset into
///    `min-height: 64px` instead of adding it below — and `ModeTabBarMetrics` carries
///    a comment warning against exactly that. A comment is not a test.
/// 2. **Count mode's behaviour.** `CountRoundTests` covers the arithmetic of a round;
///    nothing drove the real screen. The state machine is the part of this app that
///    fails silently: a round that counts the same dog twice still looks and sounds
///    perfect, and teaches "two dogs" from one dog.
///
/// These are behaviour tests first and geometry tests second. Every loop asserts its
/// collection is **non-empty first** — a suite that silently measures zero tiles is
/// worse than no suite at all, because it reports green at the last gate before the
/// app reaches a child's hands.
///
/// Mute, the parental gate and About are deliberately **not** re-tested here:
/// `WordsModeUITests.testTheMuteButtonIsReachableAndSilencesTheApp`,
/// `ParentalGateUITests` and `AboutUITests` already own them, and a second copy costs
/// a launch each without covering a line the first copy misses.
final class CountModeUITests: XCTestCase {

    /// The floor for anything a **child** taps: count tiles, Shuffle, Next, and both
    /// mode tabs. Parent-only chrome (the gear, mute, the language picker) follows the
    /// 44pt HIG minimum instead and is checked in `WordsModeUITests`.
    private static let childMinimum: CGFloat = 64
    /// `CLAUDE.md` rule 2. Two touching targets are one wide target as far as a
    /// toddler's aim is concerned.
    private static let minimumGap: CGFloat = 8
    /// Frames arrive from the accessibility layer as floats; a twentieth of a point is
    /// rounding, not a violation. Anything larger is a real shortfall and must fail.
    private static let tolerance: CGFloat = 0.05

    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        // The one rotating test would otherwise hand its orientation to whatever runs
        // next, and the landscape layout has no bottom tab bar at all.
        XCUIDevice.shared.orientation = .portrait
    }

    // MARK: - Harness

    /// Launches in a known state.
    ///
    /// Everything this suite depends on goes through `NSArgumentDomain`, which outranks
    /// anything `SettingsStore` previously wrote to the app domain. Without it these
    /// tests inherit whatever the last run left on the simulator — which is not
    /// hypothetical: `ParentalGateUITests` switches a category off through the real
    /// Settings panel, it persists, and that took all sixteen `WordsModeUITests` red at
    /// once until they pinned the same keys.
    ///
    /// The count range is pinned on **every** launch, default included, for that exact
    /// reason: Settings has two sliders that write it, and a stray drag in another
    /// suite would otherwise change how many tiles this one measures. Pinning both ends
    /// to the same number is also what lets a test finish a round in two taps instead
    /// of nine.
    private func launch(countRange: ClosedRange<Int> = 2...9) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-cm_lang", "en",
            "-cm_muted", "NO",
            "-cm_enabled_langs", "(en,zh,ms,ja,tl)",
            "-cm_enabled_cats", "(fruits,food,animals,vehicles,nature,objects,people,faces)",
            // Without this a fresh simulator opens the first-launch tour over the app
            // and every assertion below measures a sheet instead of the screen.
            "-cm_seen_tutorial", "YES",
            "-cm_count_lower", "\(countRange.lowerBound)",
            "-cm_count_upper", "\(countRange.upperBound)",
        ]
        app.launch()
        XCTAssertTrue(
            app.buttons["tab-count"].waitForExistence(timeout: 30),
            "the tab bar never appeared — nothing below this can mean anything"
        )
        return app
    }

    private func openCountMode(_ app: XCUIApplication) {
        app.buttons["tab-count"].tap()
        XCTAssertTrue(
            app.buttons["count-item-0"].waitForExistence(timeout: 10),
            "tapping the Count tab did not bring up a round"
        )
    }

    private func countTiles(_ app: XCUIApplication) -> XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "count-item-"))
    }

    /// The running count, as digits. `CountReadout` draws nothing at all before the
    /// first tap, so this element's *absence* is meaningful and is asserted as such.
    private func readout(_ app: XCUIApplication) -> XCUIElement {
        app.staticTexts["count-readout"]
    }

    /// The badge number a tile is showing, or `""` when it has not been counted.
    ///
    /// `CountTile` publishes it as the accessibility value; an uncounted tile is given
    /// the empty string, which the tree may hand back as either `""` or `nil`.
    private func badge(_ app: XCUIApplication, _ index: Int) -> String {
        (app.buttons["count-item-\(index)"].value as? String) ?? ""
    }

    private func waitForReadout(
        _ app: XCUIApplication, toBe expected: String, _ message: String,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", expected),
            object: readout(app)
        )
        XCTAssertEqual(
            XCTWaiter().wait(for: [expectation], timeout: 6), .completed,
            "\(message) — expected \"\(expected)\", saw \"\(readout(app).label)\"",
            file: file, line: line
        )
    }

    private func waitForCount(
        _ query: XCUIElementQuery, toBe expected: Int, _ message: String,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "count == %d", expected), object: query
        )
        XCTAssertEqual(
            XCTWaiter().wait(for: [expectation], timeout: 6), .completed,
            "\(message) — expected \(expected), saw \(query.count)",
            file: file, line: line
        )
    }

    /// Waits out the animations a tap starts, so what is measured afterwards is the
    /// resting layout. Not padding: a tile mid-bounce measures 15% larger and a badge
    /// mid-pop smaller, so a size assertion taken too early lies in a different
    /// direction on every run. The longest here is the tile bounce at 0.4s.
    private func settleAnimations() {
        Thread.sleep(forTimeInterval: 1.0)
    }

    /// Asserts a group of child-facing targets is present and every one of them clears
    /// 64pt on **both** axes.
    ///
    /// The count check is first and load-bearing: a loop over an empty query passes
    /// trivially, which is the exact failure mode this file exists to rule out. Height
    /// alone would pass a 200×20 sliver, so both axes, or it is not a target.
    @discardableResult
    private func assertMeetsChildMinimum(
        _ query: XCUIElementQuery, named group: String, atLeast expectedCount: Int,
        file: StaticString = #filePath, line: UInt = #line
    ) -> [CGRect] {
        let frames = query.allElementsBoundByIndex.map { $0.frame }
        XCTAssertGreaterThanOrEqual(
            frames.count, expectedCount,
            "found \(frames.count) \(group)s — too few for this to be a real check",
            file: file, line: line
        )
        for frame in frames {
            XCTAssertGreaterThanOrEqual(
                frame.width, Self.childMinimum - Self.tolerance,
                "a \(group) is \(frame.width)pt wide — a child taps it, so 64pt is the floor",
                file: file, line: line
            )
            XCTAssertGreaterThanOrEqual(
                frame.height, Self.childMinimum - Self.tolerance,
                "a \(group) is \(frame.height)pt tall — a child taps it, so 64pt is the floor",
                file: file, line: line
            )
        }
        return frames
    }

    // MARK: - Switching modes

    /// Mutation: delete `mode = next` from `ContentView.select`.
    func testTheTabBarSwitchesBetweenTheTwoModes() {
        let app = launch()
        XCTAssertTrue(app.buttons["emoji-🍎"].waitForExistence(timeout: 10), "Words mode did not open first")

        openCountMode(app)
        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForNonExistence(timeout: 5),
            "the Words grid is still on screen in Count mode"
        )

        app.buttons["tab-words"].tap()
        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForExistence(timeout: 5),
            "the Words grid did not come back"
        )
        XCTAssertTrue(
            app.buttons["count-item-0"].waitForNonExistence(timeout: 5),
            "the round is still on screen in Words mode"
        )
    }

    // MARK: - Counting

    /// The core loop, and the proof that the app says "one" then "two" rather than the
    /// same number twice — the readout is the number being spoken.
    ///
    /// The tiles are counted out of order on purpose: a badge is a tile's position in
    /// the counting order, never its position in the grid, which is the whole point of
    /// counting things that are not in a line.
    ///
    /// Mutation: delete `round = current` from `CountView.tap` — the readout never
    /// changes. Or change `CountRound.badge(for:)` to return the index — the badges
    /// come out in grid order and the last block fails.
    func testCountingRunsTheTotalUpAndBadgesTheOrderCounted() {
        let app = launch(countRange: 4...4)
        openCountMode(app)

        waitForCount(countTiles(app), toBe: 4, "a round pinned to four drew the wrong number of tiles")
        XCTAssertFalse(
            readout(app).exists,
            "the readout is showing a number before anything has been counted"
        )

        app.buttons["count-item-2"].tap()
        waitForReadout(app, toBe: "1", "the first tap should read one")
        app.buttons["count-item-0"].tap()
        waitForReadout(app, toBe: "2", "the second tap should read two")
        app.buttons["count-item-3"].tap()
        waitForReadout(app, toBe: "3", "the third tap should read three")

        settleAnimations()
        XCTAssertEqual(badge(app, 2), "1", "the tile counted first is not badged 1")
        XCTAssertEqual(badge(app, 0), "2", "the tile counted second is not badged 2")
        XCTAssertEqual(badge(app, 3), "3", "the tile counted third is not badged 3")
        XCTAssertEqual(badge(app, 1), "", "a tile nobody counted is wearing a badge")
    }

    /// The one way this mode can actively teach the wrong thing: counting the same dog
    /// twice and calling it two dogs.
    ///
    /// The last two lines are the vacuity guard. Without them, deleting the tap handler
    /// outright — an app that counts *nothing* — would pass this test.
    ///
    /// Mutation: delete `guard !counted.contains(index) else { return false }` from
    /// `CountRound.tap`. The readout reaches 3 off one tile and this fails.
    func testATileCannotBeCountedTwice() {
        let app = launch(countRange: 4...4)
        openCountMode(app)

        let first = app.buttons["count-item-0"]
        first.tap()
        waitForReadout(app, toBe: "1", "setup")
        first.tap()
        first.tap()

        // Given a second to change, and it must not have.
        settleAnimations()
        XCTAssertEqual(readout(app).label, "1", "the same tile was counted more than once")
        XCTAssertEqual(badge(app, 0), "1", "the badge changed on a refused tap")

        // …and the round is still live, so the two assertions above cannot be passing
        // because nothing counts at all.
        app.buttons["count-item-1"].tap()
        waitForReadout(app, toBe: "2", "a refused tap left the round unable to count anything else")
    }

    /// Finishing a round has to be reachable, and it has to be rewarded. `CloudMascot`
    /// shipped `accessibilityHidden`, so no UI test could observe a mood and deleting a
    /// celebration outright left the whole suite green; the identifier it publishes now
    /// is the only way a test can see the reward at all.
    ///
    /// Pinned to a round of two so the celebration is a second away rather than eight,
    /// and so Next's appearance is provable in three taps.
    ///
    /// Mutation: delete `setMood(.beaming)` from `CountView.celebrate`, or drop the
    /// `if round?.isComplete == true` guard around the Next button.
    func testFinishingARoundRevealsNextAndBeamsTheMascot() {
        let app = launch(countRange: 2...2)
        openCountMode(app)
        waitForCount(countTiles(app), toBe: 2, "a round pinned to two drew the wrong number of tiles")

        XCTAssertTrue(app.buttons["count-shuffle"].exists, "Shuffle should always be available")
        XCTAssertFalse(app.buttons["count-next"].exists, "Next is offered before the round is finished")
        XCTAssertEqual(
            app.descendants(matching: .any).matching(identifier: "mascot-beaming").count, 0,
            "the mascot is already celebrating before the round has started"
        )

        app.buttons["count-item-0"].tap()
        waitForReadout(app, toBe: "1", "setup")
        XCTAssertFalse(app.buttons["count-next"].exists, "Next appeared halfway through the round")

        app.buttons["count-item-1"].tap()
        waitForReadout(app, toBe: "2", "the round did not reach its target")

        let beaming = app.descendants(matching: .any).matching(identifier: "mascot-beaming")
        let appeared = XCTNSPredicateExpectation(predicate: NSPredicate(format: "count > 0"), object: beaming)
        XCTAssertEqual(
            XCTWaiter().wait(for: [appeared], timeout: 8), .completed,
            "the mascot never celebrated a completed round"
        )
        XCTAssertTrue(
            app.buttons["count-next"].waitForExistence(timeout: 5),
            "Next never appeared after the round was finished"
        )
        // Next only exists here, so this is the only place its size can be measured.
        assertMeetsChildMinimum(
            app.buttons.matching(identifier: "count-next"), named: "Next button", atLeast: 1
        )
    }

    /// Shuffle throws the round away and starts a fresh one — the readout, the badges
    /// and the child's place in the count all go.
    ///
    /// Mutation: delete the `startRound(target:)` call from `CountView.shuffle`. The
    /// old round stays on screen with its badges and this fails. (Run: it also takes
    /// `testMashingCountModeNeverProducesAFailureState` red, which is the only mutation
    /// found that does.)
    func testShuffleStartsAFreshRound() {
        let app = launch(countRange: 4...4)
        openCountMode(app)

        app.buttons["count-item-0"].tap()
        app.buttons["count-item-1"].tap()
        waitForReadout(app, toBe: "2", "setup")

        app.buttons["count-shuffle"].tap()

        waitForCount(
            app.staticTexts.matching(identifier: "count-readout"), toBe: 0,
            "shuffling left the previous round's number on screen"
        )
        settleAnimations()
        for index in 0..<4 {
            XCTAssertEqual(badge(app, index), "", "a badge survived the shuffle on tile \(index)")
        }
        // And the new round is usable, which is what "fresh" has to mean.
        waitForCount(countTiles(app), toBe: 4, "the shuffled round drew the wrong number of tiles")
        app.buttons["count-item-0"].tap()
        waitForReadout(app, toBe: "1", "the shuffled round does not accept taps")
    }

    /// Next is the reward for finishing, and it must hand back a different round rather
    /// than the same one again. Pinned to 2...3 so the round walks 3 → 2 and the change
    /// is visible as a change in the number of tiles.
    ///
    /// Mutation: replace `CountRound.nextTarget`'s body with `return target`, or delete
    /// the `startRound(target:)` call from `CountView.nextRound`.
    func testNextHandsBackADifferentRound() {
        let app = launch(countRange: 2...3)
        openCountMode(app)
        waitForCount(countTiles(app), toBe: 3, "a range of 2...3 should open on three")

        for index in 0..<3 { app.buttons["count-item-\(index)"].tap() }
        XCTAssertTrue(
            app.buttons["count-next"].waitForExistence(timeout: 8),
            "Next never appeared after the round was finished"
        )

        app.buttons["count-next"].tap()

        waitForCount(countTiles(app), toBe: 2, "Next did not move the round on")
        waitForCount(
            app.staticTexts.matching(identifier: "count-readout"), toBe: 0,
            "Next left the finished round's number on screen"
        )
        XCTAssertEqual(badge(app, 0), "", "a badge survived Next")
        app.buttons["count-item-0"].tap()
        waitForReadout(app, toBe: "1", "the round after Next does not accept taps")
    }

    // MARK: - Touch targets

    /// `CLAUDE.md` rules 1 and 2, on the controls Count mode adds.
    ///
    /// Measured on a fresh round of nine — the largest, therefore the smallest tiles —
    /// with nothing tapped: a counted tile is drawn at 1.0 and the tile just counted at
    /// 1.15, so an untouched round is the conservative case on both axes.
    ///
    /// **Upright only, and that is a finding, not an omission.** `CountTile` draws an
    /// uncounted tile at `uncountedScale` 0.95, and `scaleEffect` moves the hit region
    /// as well as the pixels — so what a child actually taps is 0.95 × `side`. Upright
    /// that is 72 × 0.95 = 68.4 and clears the floor. Sideways, a round of six or more
    /// takes `side` 64 and lands at **60.8pt**, measured on iPhone 17 Pro Max. Asserting
    /// it here would ship this suite red, so it is reported rather than enforced; the
    /// fix is `CountTileMetrics`'s, not this file's.
    ///
    /// Mutation: change `CountTileMetrics.side`'s last line from 72 to 60.
    func testEveryChildFacingControlInCountModeClearsSixtyFourPoints() {
        let app = launch(countRange: 9...9)
        openCountMode(app)
        waitForCount(countTiles(app), toBe: 9, "a round of nine drew the wrong number of tiles")
        settleAnimations()

        let tiles = assertMeetsChildMinimum(countTiles(app), named: "count tile", atLeast: 9)
        assertMeetsChildMinimum(
            app.buttons.matching(identifier: "count-shuffle"), named: "Shuffle button", atLeast: 1
        )
        assertMeetsChildMinimum(
            app.buttons.matching(NSPredicate(format: "identifier IN %@", ["tab-words", "tab-count"])),
            named: "mode tab", atLeast: 2
        )

        // Rule 2, among the tiles. Only neighbours are compared: two rects sharing
        // neither a row nor a column are diagonal, and the gap that matters between
        // them is the one their neighbours already enforce.
        var comparisons = 0
        for (index, a) in tiles.enumerated() {
            for b in tiles[(index + 1)...] {
                XCTAssertFalse(a.intersects(b), "two count tiles overlap: \(a) and \(b)")
                if a.minY < b.maxY && b.minY < a.maxY {
                    let gap = a.minX < b.minX ? b.minX - a.maxX : a.minX - b.maxX
                    XCTAssertGreaterThanOrEqual(
                        gap, Self.minimumGap - Self.tolerance,
                        "count tiles \(a) and \(b) are \(gap)pt apart horizontally"
                    )
                    comparisons += 1
                }
                if a.minX < b.maxX && b.minX < a.maxX {
                    let gap = a.minY < b.minY ? b.minY - a.maxY : a.minY - b.maxY
                    XCTAssertGreaterThanOrEqual(
                        gap, Self.minimumGap - Self.tolerance,
                        "count tiles \(a) and \(b) are \(gap)pt apart vertically"
                    )
                    comparisons += 1
                }
            }
        }
        XCTAssertGreaterThanOrEqual(comparisons, 12, "only \(comparisons) tile pairs were adjacent enough to check")
    }

    /// **The web's own 42.5pt regression, in the one place it can recur.**
    ///
    /// `box-sizing: border-box` folded the home-indicator inset into the bar's
    /// `min-height: 64px` instead of adding it below, and every notched phone got a
    /// 42.5pt target. `ModeTabBar` guards against it by putting `.ignoresSafeArea` on
    /// the plate behind the tabs and never on the row itself — which is a comment until
    /// something measures it.
    ///
    /// Two independent things are asserted, because neither implies the other: the tabs
    /// are 64pt on both axes, **and** they stop short of the physical bottom edge. Both
    /// halves were mutation-checked, and each is caught by a different edit:
    ///
    /// - Delete `.frame(minWidth:minHeight:)` from `ModeTabBar.tab` and the target
    ///   collapses to **41pt** — the web's 42.5pt regression, near enough exactly.
    ///   Caught by the size assertion; the clearance assertion still passes.
    /// - In `AdaptiveShell`, move `.ignoresSafeArea()` off the background and onto the
    ///   `GeometryReader` and the tabs slide down to **0pt** of clearance, over the home
    ///   indicator, while staying 64pt. Caught by the clearance assertion only.
    ///
    /// Moving `.ignoresSafeArea(edges: .bottom)` from the tab bar's plate onto the row
    /// itself — the mutation this test was originally specified against — turns out to
    /// change **nothing** measurable: `AdaptiveShell` sizes its content to the safe area
    /// through a `GeometryReader` frame, so a descendant cannot expand past it. Recorded
    /// because a mutation that does not bite is worth knowing about; it is not evidence
    /// the guard is redundant, as the two edits above demonstrate.
    func testTheTabBarKeepsItsFullHeightAboveTheHomeIndicator() {
        let app = launch()

        let tabs = app.buttons.matching(NSPredicate(format: "identifier IN %@", ["tab-words", "tab-count"]))
        let frames = assertMeetsChildMinimum(tabs, named: "mode tab", atLeast: 2)

        // Rule 2 between the two of them, and no overlap — a negative-width layout
        // would satisfy the height assertion while producing one.
        XCTAssertFalse(frames[0].intersects(frames[1]), "the two tabs overlap")
        let gap = frames[0].minX < frames[1].minX
            ? frames[1].minX - frames[0].maxX
            : frames[0].minX - frames[1].maxX
        XCTAssertGreaterThanOrEqual(gap, Self.minimumGap - Self.tolerance, "the tabs are \(gap)pt apart")

        // The home indicator. Its inset is not readable from the test process, so the
        // device is asked whether it has one: only a phone with a tall status bar — a
        // notch or an island — has a home indicator at the bottom, and every such
        // device reserves at least 20pt for it. The status bar belongs to SpringBoard,
        // not to the app under test, so `app.statusBars` is always empty.
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let statusBar = springboard.statusBars.firstMatch
        guard statusBar.exists, statusBar.frame.height >= 30 else {
            XCTFail(
                "this suite must run on a device with a home indicator, or the "
                + "regression it guards cannot appear — saw a \(statusBar.frame.height)pt status bar"
            )
            return
        }

        let window = app.windows.firstMatch.frame
        XCTAssertGreaterThan(window.height, 0, "no window to measure the screen's bottom edge from")
        for frame in frames {
            let below = window.maxY - frame.maxY
            XCTAssertGreaterThanOrEqual(
                below, 20,
                "a mode tab ends \(below)pt above the bottom of the screen — the "
                + "home-indicator inset must be ADDED below the 64pt target, not eaten by it"
            )
        }
    }

    /// Sideways the bar is gone and the tabs are in the rail, which is a different
    /// layout under the same 64pt obligation. Both must never be on screen at once.
    ///
    /// Found without naming an element type: a SwiftUI container surfaces as an `Other`
    /// or as a plain group depending on how many branches sit under it, so a
    /// type-specific query is right in exactly one of the two states — which is how the
    /// first draft of `WordsModeUITests` "proved" the typing row did not exist.
    func testLandscapeMovesTheTabsIntoTheRail() {
        let app = launch()
        let bar = app.descendants(matching: .any).matching(identifier: "tab-bar").firstMatch
        let rail = app.descendants(matching: .any).matching(identifier: "tab-rail").firstMatch

        XCTAssertTrue(bar.exists, "no bottom tab bar upright")
        XCTAssertFalse(rail.exists, "the rail's tabs are already on screen upright")

        XCUIDevice.shared.orientation = .landscapeLeft

        XCTAssertTrue(
            rail.waitForExistence(timeout: 10),
            "rotating did not move the tabs into the rail"
        )
        XCTAssertFalse(bar.exists, "both tab layouts are on screen at once")
        assertMeetsChildMinimum(
            app.buttons.matching(NSPredicate(format: "identifier IN %@", ["tab-words", "tab-count"])),
            named: "rail tab", atLeast: 2
        )
    }

    // MARK: - No failure states

    /// `CLAUDE.md` rule 4. Mashing is not misuse, it is how the app is used — and
    /// Shuffle mid-round, mid-celebration and mid-utterance is the combination
    /// `CountMode.tsx` documents as the pair of bugs it had to fix.
    func testMashingCountModeNeverProducesAFailureState() {
        let app = launch(countRange: 5...5)
        openCountMode(app)

        for _ in 0..<2 {
            for index in 0..<5 {
                app.buttons["count-item-\(index)"].tap()
            }
            app.buttons["count-shuffle"].tap()
        }

        XCTAssertEqual(app.alerts.count, 0, "an alert appeared — this app has no failure states")
        XCTAssertEqual(app.state, .runningForeground, "the app did not survive being mashed")
        waitForCount(countTiles(app), toBe: 5, "the round stopped drawing its tiles")
        app.buttons["count-item-0"].tap()
        waitForReadout(app, toBe: "1", "the round stopped accepting taps")
    }
}
