import XCTest

/// Words mode, driven the way a two-year-old drives it.
///
/// These are the only tests in the project that see a real accessibility tree.
/// SwiftUI builds none outside XCUITest, so in `CloudmojiTests` every element
/// query returns zero elements and every "for each control, assert 64pt" loop is
/// vacuously true over an empty array; `ImageRenderer` does not lay out lazy
/// containers either, so the grid renders blank and a pixel comparison passes on
/// nothing. The touch-target rule in `CLAUDE.md` — the single most physical
/// property of a toddler app — can therefore only be checked here.
///
/// Which is why every loop below asserts its collection is **non-empty first**.
/// A suite that silently measures zero tiles is worse than no suite at all: it
/// reports green at the last gate before the app reaches a child's hands.
final class WordsModeUITests: XCTestCase {

    // MARK: - The rules being enforced
    //
    // `CLAUDE.md`, Toddler UX 1 and 2. Named rather than inlined so a failure
    // message can say which rule broke.

    /// Minimum for anything a **child** taps. Parent-only chrome (the language
    /// picker) is governed by the 44pt HIG minimum instead and is deliberately
    /// excluded from every group below.
    private static let childMinimum: CGFloat = 64
    /// The preferred size, and what an emoji tile must actually be.
    private static let tilePreferred: CGFloat = 72
    /// Minimum gap between two adjacent child-facing targets.
    private static let minimumGap: CGFloat = 8
    /// Frames arrive from the accessibility layer as floats. A twentieth of a
    /// point is rounding, not a violation — anything larger is a real shortfall
    /// and must fail.
    private static let tolerance: CGFloat = 0.05

    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        // One rotating test would otherwise hand its orientation to whatever
        // runs next, and the landscape layout has no category strip at all.
        XCUIDevice.shared.orientation = .portrait
    }

    // MARK: - Harness

    /// Launches the app in a known state.
    ///
    /// Every setting this suite depends on goes through `NSArgumentDomain`, which
    /// outranks anything `SettingsStore` previously wrote to the app domain.
    /// Without them these tests inherit whatever `UserDefaults` the last run left
    /// on the simulator: a leftover `cm_lang` of `ja` makes every word assertion
    /// below fail, and a leftover `cm_muted` of `YES` removes `replay-btn` from
    /// the row entirely, because a button that cannot do anything is a failure
    /// state.
    ///
    /// The two content keys were added when `ParentalGateUITests` arrived and
    /// every one of the sixteen tests here went red at once: that suite switches
    /// Fruits off through the real Settings panel, `SettingsStore` persists it,
    /// and the next launch without an override has no 🍎 in the grid — so the
    /// `launch()` precondition failed and nothing below it ran. Pinning the
    /// content the same way the language was already pinned makes this suite
    /// hermetic against any test, or any hand-fiddling, that came before it.
    private func launch(enabledLanguages: String = "(en,zh,ms,ja,tl)") -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-cm_lang", "en",
            "-cm_muted", "NO",
            "-cm_enabled_langs", enabledLanguages,
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
            "the emoji grid never appeared — nothing below this can mean anything"
        )
        return app
    }

    /// Every emoji tile the grid has laid out. Identifiers are glyph-based, so
    /// this is the same set in all five languages.
    private func tiles(_ app: XCUIApplication) -> XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "emoji-"))
    }

    private func chips(_ app: XCUIApplication) -> XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "cat-"))
    }

    /// The typing row, found without naming its element type.
    ///
    /// It is an `Other` container while it holds emojis and a plain `ScrollView`
    /// while it holds only the placeholder — SwiftUI only synthesises the
    /// container once there is more than one branch under it. A type-specific
    /// query is therefore right in exactly one of the two states, which is how
    /// the first draft of this file "proved" the row did not exist.
    private func typingRow(_ app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "typing-row").firstMatch
    }

    /// Scoped to the row on purpose: it proves the emojis are *in* the row, not
    /// merely somewhere on screen.
    private func typedEmojis(_ app: XCUIApplication) -> XCUIElementQuery {
        typingRow(app).buttons.matching(identifier: "typed-emoji")
    }

    private func rowControls(_ app: XCUIApplication) -> XCUIElementQuery {
        app.buttons.matching(
            NSPredicate(format: "identifier IN %@", ["replay-btn", "delete-btn", "clear-btn"])
        )
    }

    /// Waits out every animation a tap starts, so what is measured afterwards is
    /// the resting layout.
    ///
    /// This is not padding. A tile mid-bounce measures 84pt and a typed emoji
    /// mid-pop-in measures 55pt — both were observed in the accessibility tree —
    /// so a size assertion taken too early lies in a different direction on every
    /// run. The wait covers the longest of the three: bounce 0.4s, pop-in 0.3s,
    /// word bubble 2.2s.
    private func settleAnimations() {
        Thread.sleep(forTimeInterval: 2.6)
    }

    private func waitForCount(
        _ query: XCUIElementQuery,
        toBe expected: Int,
        _ message: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "count == %d", expected),
            object: query
        )
        let result = XCTWaiter().wait(for: [expectation], timeout: 5)
        XCTAssertEqual(
            result, .completed,
            "\(message) — expected \(expected), saw \(query.count)",
            file: file, line: line
        )
    }

    /// Asserts a group of child-facing targets is present and every one of them
    /// clears 64pt on **both** axes.
    ///
    /// Height alone would pass a 200×20 sliver. Both axes, or it is not a target.
    private func assertMeetsChildMinimum(
        _ query: XCUIElementQuery,
        named group: String,
        atLeast expectedCount: Int,
        file: StaticString = #filePath,
        line: UInt = #line
    ) -> [CGRect] {
        let frames = query.allElementsBoundByIndex.map { $0.frame }
        // First, and load-bearing: an empty group makes every assertion below it
        // vacuous, which is the exact failure mode this file exists to rule out.
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

    // MARK: - One tap, one reward

    func testTappingAnEmojiShowsItsWord() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()

        let bubble = app.staticTexts.matching(identifier: "word-bubble")
        let appeared = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "count > 0"), object: bubble
        )
        XCTAssertEqual(
            XCTWaiter().wait(for: [appeared], timeout: 5), .completed,
            "no word bubble after tapping the apple"
        )

        let labels = bubble.allElementsBoundByIndex.map { $0.label }
        XCTAssertTrue(labels.contains("apple"), "the bubble said \(labels), not 'apple'")
        XCTAssertTrue(labels.contains("🍎"), "the bubble showed \(labels), not the emoji tapped")
    }

    func testTappedEmojisJoinTheTypingRowInOrder() {
        let app = launch()
        XCTAssertTrue(typingRow(app).exists, "the typing row is not in the accessibility tree at all")

        app.buttons["emoji-🍎"].tap()
        app.buttons["emoji-🍌"].tap()

        let typed = typedEmojis(app)
        waitForCount(typed, toBe: 2, "two taps should leave two emojis in the row")
        // The label is the spoken word, so this also proves the row and the
        // speaker are being handed the same string.
        XCTAssertEqual(
            typed.allElementsBoundByIndex.map { $0.label }, ["apple", "banana"],
            "the row is out of order — the newest emoji must arrive last"
        )
    }

    func testDeleteRemovesOnlyTheLastEmoji() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        app.buttons["emoji-🍌"].tap()
        waitForCount(typedEmojis(app), toBe: 2, "setup")

        app.buttons["delete-btn"].tap()

        waitForCount(typedEmojis(app), toBe: 1, "delete should remove exactly one emoji")
        XCTAssertEqual(
            typedEmojis(app).element.label, "apple",
            "delete took the wrong end of the row"
        )
    }

    func testClearEmptiesTheTypingRow() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        app.buttons["emoji-🍌"].tap()
        waitForCount(typedEmojis(app), toBe: 2, "setup")

        app.buttons["clear-btn"].tap()

        waitForCount(typedEmojis(app), toBe: 0, "clear should empty the row")
        // The row itself stays — it is where the placeholder lives, and a row
        // that vanished would move everything below it up the screen.
        XCTAssertTrue(typingRow(app).exists, "the row disappeared with its contents")
        XCTAssertTrue(
            app.staticTexts["Tap emojis below! 👇"].waitForExistence(timeout: 2),
            "the invitation to tap did not come back"
        )
    }

    /// Regression guard for a defect this suite found on its first run.
    ///
    /// `TypingRow` sets `replay-btn`, `delete-btn` and `clear-btn` in source, but
    /// the row's own `.accessibilityIdentifier("typing-row")` propagated down and
    /// overwrote all three: the tree contained three buttons called "typing-row"
    /// and none of the documented identifiers existed. Invisible to every unit
    /// test, because there is no tree to look at outside XCUITest.
    func testTypingRowControlsKeepTheirOwnIdentifiers() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()

        for identifier in ["replay-btn", "delete-btn", "clear-btn"] {
            XCTAssertTrue(
                app.buttons[identifier].waitForExistence(timeout: 3),
                "\(identifier) is set in TypingRow.swift but absent from the accessibility tree"
            )
        }
    }

    // MARK: - Touch targets

    /// The rule the whole app is built around, measured on a real layout.
    func testEveryChildFacingControlMeetsTheSixtyFourPointRule() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        app.buttons["emoji-🍌"].tap()
        settleAnimations()

        // Counts are floors, not exact numbers: the grid is lazy and realises
        // however many tiles the device is tall enough for. They exist so that a
        // group which quietly stopped rendering cannot pass by being empty.
        let tileFrames = assertMeetsChildMinimum(tiles(app), named: "emoji tile", atLeast: 20)
        let chipFrames = assertMeetsChildMinimum(chips(app), named: "category chip", atLeast: 9)
        let typedFrames = assertMeetsChildMinimum(typedEmojis(app), named: "typed emoji", atLeast: 2)
        let controlFrames = assertMeetsChildMinimum(rowControls(app), named: "row control", atLeast: 3)

        let total = tileFrames.count + chipFrames.count + typedFrames.count + controlFrames.count
        XCTAssertGreaterThan(total, 30, "only \(total) child-facing controls were measured")
    }

    /// 64pt is the floor; the tile a toddler aims at more than anything else is
    /// specified at 72. Measured on a launch with no taps, so nothing is mid-bounce.
    func testEmojiTilesAreTheSeventyTwoPointPreferredSize() {
        let app = launch()
        let frames = tiles(app).allElementsBoundByIndex.map { $0.frame }
        XCTAssertGreaterThan(frames.count, 20, "only \(frames.count) tiles — not a real check")

        for frame in frames {
            XCTAssertGreaterThanOrEqual(
                frame.height, Self.tilePreferred - Self.tolerance,
                "an emoji tile is \(frame.height)pt tall — the preferred size is 72"
            )
            XCTAssertGreaterThanOrEqual(
                frame.width, Self.tilePreferred - Self.tolerance,
                "an emoji tile is \(frame.width)pt wide — the preferred size is 72"
            )
        }
    }

    /// `CLAUDE.md` rule 2. Two 72pt targets touching each other are one 144pt
    /// target as far as a toddler's aim is concerned.
    func testAdjacentChildFacingTargetsAreAtLeastEightPointsApart() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        settleAnimations()

        let frames = tiles(app).allElementsBoundByIndex.map { $0.frame }
            + chips(app).allElementsBoundByIndex.map { $0.frame }
            + typedEmojis(app).allElementsBoundByIndex.map { $0.frame }
            + rowControls(app).allElementsBoundByIndex.map { $0.frame }
        XCTAssertGreaterThan(frames.count, 30, "only \(frames.count) targets — not a real check")

        var comparisons = 0
        for (index, a) in frames.enumerated() {
            for b in frames[(index + 1)...] {
                XCTAssertFalse(
                    a.intersects(b),
                    "two child-facing targets overlap: \(a) and \(b)"
                )
                // Only neighbours are compared: two rects that share no row and
                // no column are diagonal, and the gap that matters between them
                // is the one their neighbours already enforce.
                if a.minY < b.maxY && b.minY < a.maxY {
                    let gap = a.minX < b.minX ? b.minX - a.maxX : a.minX - b.maxX
                    XCTAssertGreaterThanOrEqual(
                        gap, Self.minimumGap - Self.tolerance,
                        "targets \(a) and \(b) are \(gap)pt apart horizontally"
                    )
                    comparisons += 1
                }
                if a.minX < b.maxX && b.minX < a.maxX {
                    let gap = a.minY < b.minY ? b.minY - a.maxY : a.minY - b.maxY
                    XCTAssertGreaterThanOrEqual(
                        gap, Self.minimumGap - Self.tolerance,
                        "targets \(a) and \(b) are \(gap)pt apart vertically"
                    )
                    comparisons += 1
                }
            }
        }
        XCTAssertGreaterThan(comparisons, 50, "only \(comparisons) pairs were adjacent enough to check")
    }

    // MARK: - Categories

    func testCategorySelectionFiltersTheGrid() {
        let app = launch()
        XCTAssertTrue(app.buttons["emoji-🍎"].exists, "the apple should be on screen under 'All'")

        app.buttons["cat-animals"].tap()

        XCTAssertTrue(
            app.buttons["emoji-🍎"].waitForNonExistence(timeout: 5),
            "fruit is still in the grid after choosing Animals"
        )
        XCTAssertTrue(app.buttons["emoji-🐶"].exists, "the dog is missing from Animals")
        XCTAssertTrue(
            app.buttons["emoji-🐶"].isHittable,
            "the dog is in the tree but not reachable — the grid did not scroll back to the top"
        )
    }

    /// Landscape is a real way a toddler holds a phone, and it swaps the whole
    /// category strip for a side rail — a different code path with the same 64pt
    /// obligation.
    func testLandscapeSwapsTheStripForTheRailAndKeepsItsTargets() {
        let app = launch()
        XCTAssertTrue(app.scrollViews["category-bar"].exists, "no category strip in portrait")

        XCUIDevice.shared.orientation = .landscapeLeft

        XCTAssertTrue(
            app.scrollViews["category-rail"].waitForExistence(timeout: 5),
            "rotating did not bring up the side rail"
        )
        XCTAssertFalse(
            app.scrollViews["category-bar"].exists,
            "both category layouts are on screen at once"
        )
        _ = assertMeetsChildMinimum(chips(app), named: "rail chip", atLeast: 9)
        _ = assertMeetsChildMinimum(tiles(app), named: "landscape emoji tile", atLeast: 10)
    }

    // MARK: - No failure states

    /// `CLAUDE.md` rule 4. Mashing is not misuse, it is how the app is used.
    func testRapidTappingNeverProducesAFailureState() {
        let app = launch()
        let glyphs = ["🍎", "🍌", "🍊", "🍇", "🍓", "🍉", "🍒", "🥝", "🍑", "🍋", "🍍", "🥭"]
        for glyph in glyphs {
            app.buttons["emoji-\(glyph)"].tap()
        }

        waitForCount(
            typedEmojis(app), toBe: glyphs.count,
            "twelve taps should leave twelve emojis — none dropped, none doubled"
        )
        XCTAssertEqual(app.alerts.count, 0, "an alert appeared — this app has no failure states")
        XCTAssertEqual(app.state, .runningForeground, "the app did not survive twelve fast taps")
        XCTAssertTrue(
            app.buttons["emoji-🍎"].isHittable,
            "the grid stopped accepting taps"
        )
        // And the thirteenth tap still works, which is the actual promise.
        app.buttons["emoji-🍎"].tap()
        waitForCount(typedEmojis(app), toBe: glyphs.count + 1, "the row stopped accepting emojis")
    }

    /// Replay re-speaks the row after the bubble has gone.
    ///
    /// Deliberately a ONE-emoji row. The first version of this test typed two and
    /// asserted the bubble said "apple", on the reasoning that a replay restarts
    /// at the first word — and it failed, reporting "banana". That reasoning was
    /// right about the code and wrong about the test: `speakSequence` advances as
    /// each word finishes, and on a simulator that is faster than XCUITest can
    /// sample the accessibility tree, so the first word's bubble exists for
    /// milliseconds. Sampling mid-sequence is a race whichever word you expect.
    ///
    /// What this does still prove is the part that had no coverage at all: replay
    /// produces a bubble again after the original expired, with no dependency on
    /// installed voices — `speakSequence` calls each item's `onSpeak`
    /// synchronously before handing anything to the engine, which is exactly what
    /// that seam exists for. Ordering within a sequence is covered by
    /// `SpeechControllerTests` at the unit level, where time is controllable.
    func testReplaySpeaksTheRowAgain() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        waitForCount(typedEmojis(app), toBe: 1, "setup")
        // Let the tap's own bubble expire, so a bubble afterwards can only have
        // come from the replay.
        settleAnimations()
        XCTAssertEqual(
            app.staticTexts.matching(identifier: "word-bubble").count, 0,
            "the first bubble never went away, so this test cannot prove anything"
        )

        app.buttons["replay-btn"].tap()

        let bubble = app.staticTexts.matching(identifier: "word-bubble")
        let appeared = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "count > 0"), object: bubble
        )
        XCTAssertEqual(
            XCTWaiter().wait(for: [appeared], timeout: 5), .completed,
            "replay produced no word bubble"
        )
        let labels = bubble.allElementsBoundByIndex.map { $0.label }
        XCTAssertTrue(labels.contains("apple"), "replay showed \(labels), not the row's word")
    }

    /// The language toggle is parent-only chrome, so it follows the 44pt iOS HIG
    /// minimum rather than the 64pt child-facing rule.
    ///
    /// Worth an explicit test because the trap here is silent, and it has already
    /// been sprung once: `.frame(minHeight:)` grows a control's layout box without
    /// growing what is tappable, so the menu picker this replaced measured 62 × 34
    /// while a comment beside it claimed 44. Nothing failed; it was found by
    /// measuring by hand. The replacement carries `.contentShape(Rectangle())` for
    /// exactly that reason.
    func testLanguagePickerMeetsTheParentChromeMinimum() {
        let app = launch()
        let picker = app.descendants(matching: .any)
            .matching(identifier: "lang-picker").firstMatch
        XCTAssertTrue(
            picker.waitForExistence(timeout: 5),
            "the language picker was not in the tree at all"
        )

        let frame = picker.frame
        XCTAssertGreaterThanOrEqual(
            frame.height, 44,
            "the picker is \(frame.height)pt tall, under the 44pt HIG minimum"
        )
        XCTAssertGreaterThanOrEqual(
            frame.width, 44,
            "the picker is \(frame.width)pt wide, under the 44pt HIG minimum"
        )
    }

    /// The toggle, tapped the way the child taps it.
    ///
    /// This is the only place the whole path can be seen: the button exists, it
    /// is hittable, tapping it reaches `AppModel.cycleLanguage`, the change is
    /// persisted, and the header redraws. `AppModelTests` proves the cycle is a
    /// cycle; it cannot prove anything is wired to it, and a `Button` whose action
    /// closure was dropped would leave that suite entirely green.
    ///
    /// Five taps and back to English, not one tap and "it changed": a control
    /// that advanced and stuck on the last language would satisfy the weaker
    /// assertion. The intermediate labels are collected too, so a toggle
    /// oscillating between the first two cannot pass by ending in the right place.
    func testTheLanguageToggleCyclesThroughEveryEnabledLanguage() {
        let app = launch()
        let toggle = app.descendants(matching: .any)
            .matching(identifier: "lang-picker").firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5), "the language toggle was not in the tree")

        let start = toggle.label
        XCTAssertEqual(start, "Language: English", "the app did not launch pinned to English")

        var seen: [String] = []
        for _ in 1...5 {
            seen.append(tapAndSettle(toggle, from: seen.last ?? start))
        }

        XCTAssertEqual(
            seen,
            [
                "Language: Chinese",
                "Language: Malay",
                "Language: Japanese",
                "Language: Tagalog",
                "Language: English",
            ],
            "starting at \(start) the toggle visited \(seen)"
        )
    }

    /// Taps `element` and returns its label once it has moved off `previous`.
    ///
    /// The wait is generous and its result is deliberately **not** asserted: this
    /// machine has run the suite at 27 seconds a test under load, and a tap that
    /// has not landed within eight seconds should be reported by whatever the
    /// caller is actually measuring — the visited sequence — rather than by a
    /// timeout that says nothing about which language went missing.
    private func tapAndSettle(_ element: XCUIElement, from previous: String) -> String {
        element.tap()
        let changed = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label != %@", previous), object: element
        )
        _ = XCTWaiter().wait(for: [changed], timeout: 8)
        return element.label
    }

    /// The design case: a family switches three languages off and gets a two-way
    /// toggle. `AppModel.availableLanguages` already filters, but nothing proved
    /// the header consumed the filtered list rather than the catalogue.
    ///
    /// Six taps over a two-language cycle, so a third language has three
    /// wrap-arounds to appear in. The assertion is on the set of everything seen —
    /// checking only the final label would pass on a five-way cycle that happened
    /// to land back on English.
    func testTheLanguageToggleOnlyOffersTheLanguagesSettingsLeftOn() {
        let app = launch(enabledLanguages: "(en,ja)")
        let toggle = app.descendants(matching: .any)
            .matching(identifier: "lang-picker").firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5), "the language toggle was not in the tree")

        var visited: [String] = [toggle.label]
        for _ in 1...6 {
            visited.append(tapAndSettle(toggle, from: visited.last!))
        }

        // Asserted as a set of exactly two: membership is the claim ("never a
        // third language"), and the count of two is what stops a toggle that
        // simply never moved from satisfying it.
        XCTAssertEqual(
            Set(visited), ["Language: English", "Language: Japanese"],
            "with only English and Japanese switched on the toggle visited \(visited)"
        )
    }

    /// The mute control, end to end.
    ///
    /// Everything behind it shipped in Stage 2a and nothing reached it:
    /// `SettingsStore.muted` persisted, `TypingRow` hid replay for it,
    /// `WordsView.speak` had a muted branch for it, and no control anywhere set
    /// it. `TypingRowTests.mutingHidesReplay` passed the whole time, because a
    /// unit test hands the row the flag itself — the thing that was missing was
    /// a way for a *parent* to set it, and only a real tree can show that.
    ///
    /// So the assertion is not "muted is true" but the observable consequence:
    /// `replay-btn` leaves the row and comes back. That is the same wiring the
    /// speaker is on.
    func testTheMuteButtonIsReachableAndSilencesTheApp() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        XCTAssertTrue(
            app.buttons["replay-btn"].waitForExistence(timeout: 3),
            "setup: replay should be in the row while the app is unmuted"
        )

        let mute = app.buttons["mute-btn"]
        XCTAssertTrue(mute.waitForExistence(timeout: 3), "there is no mute button in the tree")
        // Parent chrome, so the 44pt HIG minimum — not the 64pt child floor.
        XCTAssertGreaterThanOrEqual(mute.frame.height, 44 - Self.tolerance,
                                    "the mute button is \(mute.frame.height)pt tall")
        XCTAssertGreaterThanOrEqual(mute.frame.width, 44 - Self.tolerance,
                                    "the mute button is \(mute.frame.width)pt wide")

        mute.tap()
        XCTAssertTrue(
            app.buttons["replay-btn"].waitForNonExistence(timeout: 5),
            "tapping mute changed nothing — the control is still not wired to the setting"
        )
        // The label is the parent's only readout of which state they are in.
        XCTAssertEqual(app.buttons["mute-btn"].label, "Unmute",
                       "the muted button still offers to mute")

        app.buttons["mute-btn"].tap()
        XCTAssertTrue(
            app.buttons["replay-btn"].waitForExistence(timeout: 5),
            "unmuting did not bring the sound — or the row — back"
        )
    }

    /// The mascot was `.accessibilityHidden(true)`, so no test in any target could
    /// see it and deleting the milestone celebration outright left the whole suite
    /// green. This is the check that the identifier actually reaches the tree —
    /// setting one on a hidden element compiles and does nothing.
    func testTheMascotPublishesItsMood() {
        let app = launch()
        let mascot = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "mascot-"))
        XCTAssertGreaterThan(
            mascot.count, 0,
            "no mascot-<mood> element in the tree — the mood is unobservable again"
        )
        XCTAssertTrue(
            app.descendants(matching: .any)["mascot-happy"].exists,
            "the resting mascot should publish mascot-happy"
        )
    }

    /// Nothing the app draws may overlap the Dynamic Island or status bar, in
    /// either orientation.
    ///
    /// Asserts frames do not INTERSECT, rather than comparing edges on one axis.
    /// Two earlier versions of this test were wrong in instructive ways: the
    /// first compared the bubble to the mascot, which would pass even if the
    /// whole header were buried under the island since both move together; the
    /// second compared `minY` against the status bar's `maxY`, which is
    /// meaningless in landscape, where the bar is a rotated strip down the
    /// leading edge and its `maxY` is the bottom of the screen. Intersection is
    /// the question actually being asked, and it holds in any orientation.
    func testNothingIsDrawnUnderTheIsland() {
        for orientation in [UIDeviceOrientation.portrait, .landscapeLeft] {
            XCUIDevice.shared.orientation = orientation
            let app = launch()

            // The status bar belongs to SpringBoard, not the app under test —
            // `app.statusBars` is always empty.
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let statusBar = springboard.statusBars.firstMatch
            guard statusBar.exists, statusBar.frame.height > 0 else {
                XCTFail("\(orientation): no status bar to measure the unsafe region from")
                return
            }
            let unsafe = statusBar.frame

            let mascot = app.descendants(matching: .any)
                .matching(NSPredicate(format: "identifier BEGINSWITH %@", "mascot-")).firstMatch
            XCTAssertTrue(mascot.waitForExistence(timeout: 5), "\(orientation): no mascot")
            XCTAssertFalse(
                mascot.frame.intersects(unsafe),
                "\(orientation): the header overlaps the island — mascot \(mascot.frame) vs \(unsafe)"
            )

            app.buttons["emoji-🍎"].tap()

            let bubble = app.staticTexts.matching(identifier: "word-bubble")
            let appeared = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "count > 0"), object: bubble
            )
            XCTAssertEqual(
                XCTWaiter().wait(for: [appeared], timeout: 5), .completed,
                "\(orientation): no word bubble appeared"
            )
            for element in bubble.allElementsBoundByIndex {
                XCTAssertFalse(
                    element.frame.intersects(unsafe),
                    "\(orientation): the word bubble \"\(element.label)\" at \(element.frame) "
                    + "overlaps the island region \(unsafe)"
                )
            }
            app.terminate()
        }
        XCUIDevice.shared.orientation = .portrait
    }

    /// Ten taps must actually make the cloud beam.
    ///
    /// This was the stage 2a review's sharpest finding and it was still true a
    /// stage later: `WordsViewTests` asserts the milestone SET is [10,25,50,100]
    /// and asserts `arbitrate` in both directions, but nothing tested the call
    /// site — delete `if Self.milestones.contains(tapCount) { celebrate() }` and
    /// every one of 178 unit and 38 UI tests stayed green while the mascot never
    /// celebrated again. The tap counter exists solely to produce this moment.
    ///
    /// Possible only since the mascot began publishing `mascot-<mood>`; the
    /// technique is borrowed from `CountModeUITests`.
    ///
    /// Mutation: delete the milestone check, or `tapCount += 1`, in `WordsView`.
    func testTenTapsMakesTheCloudBeam() {
        let app = launch()
        let beaming = app.descendants(matching: .any).matching(identifier: "mascot-beaming")
        XCTAssertEqual(beaming.count, 0, "the mascot was already beaming before any taps")

        for _ in 0..<10 { app.buttons["emoji-🍎"].tap() }

        let appeared = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "count > 0"), object: beaming
        )
        XCTAssertEqual(
            XCTWaiter().wait(for: [appeared], timeout: 8), .completed,
            "ten taps did not produce a celebration"
        )
    }

    /// The typing row stops at fifty and keeps the NEWEST.
    ///
    /// `WordsView.capped` is well tested as a pure function — suffix-versus-prefix
    /// is proven — but nothing proved `tap()` calls it. Change that line to a
    /// plain `typed.append(...)` and the suite stayed green, leaving an unbounded
    /// array in the hands of a child whose entire interaction model is mashing.
    ///
    /// Mutation: replace `Self.capped(typed, appending:)` with `typed.append(_:)`.
    func testTheTypingRowStopsAtFifty() {
        let app = launch()
        let apple = app.buttons["emoji-🍎"]
        // Past the cap, so the drop path runs rather than merely being reached.
        for _ in 0..<55 { apple.tap() }
        settleAnimations()

        let typed = typedEmojis(app)
        XCTAssertEqual(
            typed.count, 50,
            "the row holds \(typed.count) emojis; the cap is 50 and nothing is enforcing it"
        )
        XCTAssertEqual(app.state, .runningForeground, "the app did not survive 55 taps")
    }
}
