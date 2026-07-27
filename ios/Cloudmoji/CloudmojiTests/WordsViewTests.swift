import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

/// Words mode is the whole app, and almost none of it is reachable from a unit
/// test: the state is private, the taps come from a finger, and SwiftUI builds
/// no accessibility tree for an element query outside XCUITest. So this suite
/// covers the two things that *are* reachable and that no screenshot proves —
/// the rules the screen enforces, and whether it drew anything at all.
///
/// The rules are deliberately `static` and pure on ``WordsView`` rather than
/// inlined in `tap`/`speak`. That is the only shape in which the 50-emoji cap
/// and the beaming-priority rule can be tested at all, and both are silent when
/// wrong: an unbounded row still scrolls, and a stolen beaming face still
/// smiles.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("WordsView")
@MainActor
struct WordsViewTests {

    // MARK: Fixtures

    static let repository = (try? EmojiRepository()) ?? .empty

    /// Isolated defaults, so one test's language switch cannot reach another.
    func makeModel() -> AppModel {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return AppModel(settings: SettingsStore(defaults: defaults))
    }

    func screen(_ model: AppModel) -> some View {
        AdaptiveShell { WordsView() }.environment(model)
    }

    // MARK: - The 50-emoji cap

    /// `TypingRow.maxTyped` was published by Task 7 and enforced by nobody. A
    /// 27-month-old mashing tiles is exactly the input that finds an unbounded
    /// array, and the PRD sets the limit at 50 with the *oldest* dropped first.
    ///
    /// Both halves matter and only one of them is obvious. A count-only
    /// assertion passes against `prefix`, which keeps the first fifty emojis of
    /// the session forever and never shows the child another one — the row would
    /// look frozen, which is the single worst thing it can do.
    ///
    /// 50 and 60 are spelled out rather than read back from
    /// `TypingRow.maxTyped`: a test that quotes the constant at itself follows it
    /// to any value, including zero.
    @Test("appending 60 emojis leaves the newest 50, oldest first out")
    func typingRowIsCappedAtFiftyKeepingTheNewest() {
        var typed: [TypedEmoji] = []
        for index in 1...60 {
            typed = WordsView.capped(typed, appending: TypedEmoji(emoji: "🍎", word: "word \(index)"))
        }

        #expect(typed.count == 50)
        #expect(typed.first?.word == "word 11", "kept the wrong end — first is \(typed.first?.word ?? "nothing")")
        #expect(typed.last?.word == "word 60", "the emoji just tapped is not in the row")
        // Order is not incidental: the row is read left to right and replayed in
        // the same order, so a cap that reversed or shuffled would be a
        // different bug with the same count.
        #expect(typed.map(\.word) == (11...60).map { "word \($0)" })
    }

    /// Below the cap nothing is dropped, and nothing is reordered. Without this
    /// an implementation that always trimmed to a fixed 50 — or returned only
    /// the new item — would satisfy the test above.
    @Test("a row below the cap keeps every emoji, in order")
    func belowTheCapNothingIsDropped() {
        var typed: [TypedEmoji] = []
        for index in 1...5 {
            typed = WordsView.capped(typed, appending: TypedEmoji(emoji: "🍎", word: "word \(index)"))
        }
        #expect(typed.map(\.word) == ["word 1", "word 2", "word 3", "word 4", "word 5"])
    }

    // MARK: - Beaming priority

    /// `CLAUDE.md` rule 11. The milestone tap is itself a tap, and the word it
    /// speaks finishes a beat later — so without this the beaming face is pulled
    /// off by the very events that earned it, and three seconds of celebration
    /// become a flicker nobody sees.
    @Test("beaming outranks every other mood")
    func beamingIsNotInterrupted() {
        for requested: MascotMood in [.happy, .excited, .speaking] {
            #expect(
                WordsView.arbitrate(current: .beaming, requested: requested) == .beaming,
                "\(requested) was allowed to interrupt the celebration"
            )
        }
    }

    /// The other direction, which is what stops the rule being implemented as
    /// "never change the mood". Every non-beaming state must still follow the
    /// taps and the speech.
    @Test("every other mood gives way, and beaming can always be entered")
    func nonBeamingMoodsAreReplaced() {
        for current: MascotMood in [.happy, .excited, .speaking] {
            for requested: MascotMood in MascotMood.allCases {
                #expect(WordsView.arbitrate(current: current, requested: requested) == requested)
            }
        }
        // The celebration has to be able to start from anywhere, including from
        // the excited face the milestone tap just set.
        #expect(WordsView.arbitrate(current: .beaming, requested: .beaming) == .beaming)
    }

    /// `CLAUDE.md` rule 10, as literals. Reading the set back out of
    /// ``WordsView/milestones`` would pass against an empty set — a mascot that
    /// never celebrates, and an app that never rewards persistence.
    @Test("milestones fire at 10, 25, 50 and 100 taps and nowhere else")
    func milestonesAreWhereTheSpecSaysTheyAre() {
        #expect(WordsView.milestones == [10, 25, 50, 100])
        for count in [1, 9, 11, 24, 26, 49, 51, 99, 101] {
            #expect(!WordsView.milestones.contains(count), "\(count) taps should not celebrate")
        }
    }

    // MARK: - It actually draws

    /// The confirmed failure mode of every visual task in this plan is a test
    /// that passes over a blank image. Emoji are the brightest thing on screen
    /// by a wide margin — the background gradient's brightest stop sums to about
    /// 114 — so a threshold of 400 counts emoji pixels and nothing else.
    static let emojiThreshold = 400

    /// The whole screen, assembled, in a portrait window. If this draws nothing
    /// then nothing else in this suite means anything and the app is a black
    /// rectangle on a real phone.
    @Test("the portrait screen draws its grid")
    func portraitScreenDrawsSomething() async {
        let bitmap = await Bitmap.of(
            screen(makeModel()), width: 440, height: 956,
            settling: .milliseconds(400), fillsWindow: true
        )
        // Measured: 23,412 with the grid, 2,860 with it emptied — the mascot,
        // the typing row and the category strip are lit either way. 5,000 sits
        // in the gap, so this fails on a screen that drew everything *except*
        // the grid, which is the only interesting way for it to be wrong.
        #expect(
            bitmap.litPixels(threshold: Self.emojiThreshold) > 5000,
            "only \(bitmap.litPixels(threshold: Self.emojiThreshold)) bright pixels — the grid did not draw"
        )
    }

    /// Sideways, the categories move into the rail on the left and the grid
    /// starts to the right of it. The rail lays its own darker plate, so the
    /// left edge of a landscape screen is measurably darker than the right —
    /// and on a portrait screen the same two columns are the same background.
    ///
    /// Sampled at x = 2 and x = width - 3, which are inside the rail's 4pt inset
    /// and outside the grid's 10pt padding respectively: no glyph reaches
    /// either, so this measures the plate and not what is drawn on it.
    @Test("sideways, the rail lays a darker plate down the left edge")
    func landscapeDrawsTheRail() async {
        let width: CGFloat = 956
        let height: CGFloat = 440

        let landscape = await Bitmap.of(
            screen(makeModel()), width: width, height: height,
            settling: .milliseconds(400), fillsWindow: true
        )
        #expect(
            landscape.litPixels(threshold: Self.emojiThreshold) > 5000,
            "the landscape screen drew no emoji at all"
        )

        let rows = [Int(height) / 2, Int(height) * 3 / 4]
        for y in rows {
            let left = landscape.rgb(x: 2, y: y).sum
            let right = landscape.rgb(x: Int(width) - 3, y: y).sum
            #expect(left < right, "at y=\(y) the left edge (\(left)) is not darker than the right (\(right)) — no rail")
        }

        // The same measurement upright, where there is no rail to find. This is
        // what stops the assertion above from passing on the background gradient
        // alone, or on any left-to-right shading in the window.
        let portrait = await Bitmap.of(
            screen(makeModel()), width: height, height: width,
            settling: .milliseconds(400), fillsWindow: true
        )
        for y in [Int(width) / 2, Int(width) * 3 / 4] {
            let left = portrait.rgb(x: 2, y: y).sum
            let right = portrait.rgb(x: Int(height) - 3, y: y).sum
            #expect(left == right, "upright, the edges differ (\(left) vs \(right)) — something rail-shaped drew")
        }
    }

    /// The bubble's row has to occupy its height when it is *empty*, which is
    /// most of the time. It did not, and the category strip and the whole grid
    /// jumped 44pt down the screen on every tap and back up 2.2 seconds later —
    /// found by tapping the app on the simulator, not by any assertion here.
    ///
    /// **Measured inside a stack, which is the whole point.** An absent `if let`
    /// branch is an `EmptyView`, and a stack drops an `EmptyView` from its
    /// layout along with any frame on it — but `ImageRenderer` handed the same
    /// view on its own reports a perfectly correct 44pt. The first version of
    /// this test measured it on its own, passed, and passed just as happily
    /// against the broken row.
    ///
    /// The two 10pt rules top and bottom are what make the arithmetic readable:
    /// 64 means the row is there, 20 means it vanished.
    @Test("the bubble's row holds its height inside a stack when it is empty")
    func emptyBubbleRowStillReservesItsHeight() {
        let stacked = VStack(spacing: 0) {
            Color.red.frame(height: 10)
            WordsView().bubbleRow
            Color.red.frame(height: 10)
        }
        .frame(width: 200)

        // 44 and 10 spelled out rather than read back from
        // `WordsView.bubbleRowHeight`: the failure guarded against is the row
        // measuring zero, and a test that quotes the constant at itself would
        // agree that zero was correct.
        #expect(
            Bitmap.rendered(stacked).height == 64,
            "the row collapsed — the stack is \(Bitmap.rendered(stacked).height)pt tall, not 10 + 44 + 10"
        )
    }

    // MARK: - The tile's VoiceOver label

    /// `EmojiTile` hardcoded `entry.en`, so VoiceOver announced "apple" while
    /// the child heard 苹果. The screen now passes the word it is about to
    /// speak, and the default keeps every older call site compiling.
    ///
    /// Checked on the value the view is built from rather than through an
    /// element query: SwiftUI does not publish an accessibility tree to a unit
    /// test, so a query-based version of this would run over an empty array and
    /// pass no matter what the tile said.
    @Test("a tile announces the word in the chosen language, and English by default")
    func tileLabelFollowsTheLanguage() throws {
        let apple = try #require(Self.repository.emojis.first { $0.emoji == "🍎" })
        #expect(EmojiTile(entry: apple) {}.word == nil)
        #expect(apple.word(.ja) != apple.en, "the fixture cannot tell the two languages apart")
        #expect(EmojiTile(entry: apple, word: apple.word(.ja)) {}.word == "りんご")
    }

    /// The grid is what the screen actually builds, and it is where a dropped
    /// `word` would strand the label in English. Asserted on the value the grid
    /// is constructed with — a traversal of `UIHostingController.view` for the
    /// rendered label was tried first and returns an empty array, exactly as the
    /// suite's header describes.
    @Test("the grid carries a word for its tiles, and defaults to none")
    func gridCarriesTheWord() throws {
        let apple = try #require(Self.repository.emojis.first { $0.emoji == "🍎" })
        #expect(EmojiGrid(entries: [apple], onTap: { _ in }).word == nil)
        let localised = EmojiGrid(entries: [apple], word: { $0.word(.zh) }, onTap: { _ in })
        #expect(localised.word?(apple) == "苹果")
    }
}
