import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

/// Count mode's timing and SwiftUI state cannot be driven from a unit test, so
/// this covers the two things that can: the rules the screen enforces, and whether
/// it draws anything at all. The behaviour lives in `CountModeUITests`.
@Suite("CountView")
@MainActor
struct CountViewTests {

    /// Isolated defaults per test, optionally with the round size pinned.
    func makeModel(countRange: ClosedRange<Int>? = nil) -> AppModel {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        if let countRange {
            defaults.set(countRange.lowerBound, forKey: "cm_count_lower")
            defaults.set(countRange.upperBound, forKey: "cm_count_upper")
        }
        return AppModel(settings: SettingsStore(defaults: defaults))
    }

    func screen(_ model: AppModel) -> some View {
        AdaptiveShell { CountView() }.environment(model)
    }

    // MARK: - Rules

    /// The completion phrase is the same phrase with an exclamation on it — the
    /// round's "and that's three dogs!". Dropping it is silent: the round still
    /// completes, the mascot still beams, and the fanfare says the same flat thing
    /// the last tap said.
    ///
    /// Mutation: return `phrase` unchanged. Both expectations fail.
    @Test("the completion phrase is the running phrase, exclaimed")
    func completionPhraseIsExclaimed() {
        #expect(CountView.completionPhrase("three dogs") == "three dogs!")
        #expect(CountView.completionPhrase("いぬ みっつ") == "いぬ みっつ!")
    }

    /// Nothing counted, nothing shown. A readout that draws "0" before the first
    /// tap tells the child the answer is zero, which is both wrong and the opposite
    /// of an invitation.
    ///
    /// Mutation: return `String(progress)` unconditionally. The first fails.
    @Test("the numeral is blank until something has been counted")
    func numeralIsBlankAtZero() {
        #expect(CountView.numeral(for: 0) == "")
        #expect(CountView.numeral(for: 1) == "1")
        #expect(CountView.numeral(for: 9) == "9")
    }

    /// All fifteen strings, as literals.
    ///
    /// Every row is spelled out rather than only the completeness of the tables
    /// being checked: a table that fell back to English for the two languages added
    /// last is exactly what the web's `Count to` indicator does, and it is why that
    /// indicator is not being ported. The `!= nil` sweep at the end is what catches
    /// a *sixth* language being added without its copy.
    ///
    /// Mutation: change or delete any row. Its own expectation fails rather than
    /// silently resolving to English.
    @Test("the mode's chrome exists in all five languages")
    func chromeIsTranslated() {
        #expect(CountView.uiText.subtitle[.en] == "Let's count!")
        #expect(CountView.uiText.subtitle[.zh] == "数一数!")
        #expect(CountView.uiText.subtitle[.ms] == "Jom kira!")
        #expect(CountView.uiText.subtitle[.ja] == "かぞえよう!")
        #expect(CountView.uiText.subtitle[.tl] == "Magbilang tayo!")

        #expect(CountView.uiText.shuffle[.en] == "Shuffle")
        #expect(CountView.uiText.shuffle[.zh] == "换一换")
        #expect(CountView.uiText.shuffle[.ms] == "Tukar")
        // `つぎ` and `つぎへ!` below are nearly the same word. That is what
        // `src/components/CountMode.tsx` says; it is not a transcription slip.
        #expect(CountView.uiText.shuffle[.ja] == "つぎ")
        #expect(CountView.uiText.shuffle[.tl] == "Palitan")

        #expect(CountView.uiText.next[.en] == "Next!")
        #expect(CountView.uiText.next[.zh] == "下一个!")
        #expect(CountView.uiText.next[.ms] == "Seterusnya!")
        #expect(CountView.uiText.next[.ja] == "つぎへ!")
        #expect(CountView.uiText.next[.tl] == "Susunod!")

        for language in Language.allCases {
            #expect(CountView.uiText.subtitle[language] != nil, "no subtitle for \(language)")
            #expect(CountView.uiText.shuffle[language] != nil, "no shuffle caption for \(language)")
            #expect(CountView.uiText.next[language] != nil, "no next caption for \(language)")
        }
    }

    // MARK: - Touch targets

    /// `CLAUDE.md` rule 1: Shuffle and Next are child-facing, so 64pt is the floor.
    ///
    /// Measured off a real render rather than read back out of
    /// ``CountControl/minHeight``, which is a constant agreeing with itself and
    /// would follow that constant down to 44. The captions are the longest each
    /// table has, because a caption that wrapped would grow the button rather than
    /// shrink it — the failure is a button that is *too short*, and only the frame
    /// can cause that.
    ///
    /// Mutation: `.frame(minHeight: Self.minHeight)` → 44, or delete it. The button
    /// collapses to the height of its 18pt glyph and fails.
    @Test("Shuffle and Next clear the 64pt child-facing minimum")
    func controlsClearTheChildMinimum() {
        for caption in ["Shuffle", "Seterusnya!", "つぎ", "换一换"] {
            let control = CountControl(
                glyph: "🔄", caption: caption, identifier: "count-shuffle",
                tint: Theme.amber, action: {}
            )
            let height = Bitmap.rendered(control).height
            #expect(height >= 64, "\"\(caption)\" renders \(height)pt tall, under the 64pt floor")
        }
    }

    // MARK: - It actually draws

    /// Lit pixels between `lo` and `hi`, exclusive of `hi`.
    ///
    /// Banded rather than whole-screen, which is the difference between measuring
    /// the thing under test and measuring the header. See
    /// ``portraitScreenDrawsItsRound()``.
    static func band(_ bitmap: Bitmap, _ lo: Int, _ hi: Int, threshold: Int) -> Int {
        (lo..<hi).reduce(0) { total, y in
            total + bitmap.runs(y: y, threshold: threshold).reduce(0) { $0 + $1.width }
        }
    }

    /// The window this suite photographs the upright screen in, and the strip of
    /// it the tiles occupy.
    ///
    /// The header ends at 80pt (a 64pt mascot with 10 above and 6 below) and the
    /// readout takes the next 112; the controls own the bottom 84. 200...860 is
    /// comfortably inside what is left, and **nothing but the grid can reach it** —
    /// which is the entire point of banding, see below.
    static let portraitSize = (width: 440, height: 956)
    static let gridBand = (top: 200, bottom: 860)

    /// If this draws nothing then nothing else here means anything and Count mode
    /// is a black rectangle on a real phone.
    ///
    /// **Measured in a band, and at two thresholds, because the obvious version of
    /// this test cannot fail.** Counting bright pixels over the whole window — the
    /// shape `WordsViewTests` uses, where it works — does not work here: Count
    /// mode's chrome alone (the white mascot, 🔊, 🧮, the teal wordmark, the amber
    /// Shuffle caption) scores about 3,600 at threshold 400 before a single tile is
    /// drawn, and the *round's* own contribution at that threshold ranges from 237
    /// to 4,407 depending on which countable was drawn — 🌑 and 🍇 are dark, 🌟 and
    /// ⭐ are not. No whole-window floor can separate those two populations, and a
    /// floor of 3,000 sits below the chrome, so it passes over an empty grid.
    /// Confirmed by running it against exactly that mutation.
    ///
    /// So: the strip only the grid can reach, at two thresholds.
    ///
    /// * **160** is above the background gradient (about 114) and above a tile's
    ///   4%-white plate (about 140), but below its 12%-white border (about 190) —
    ///   so it counts the tiles themselves whatever is drawn inside them. Measured
    ///   5,480–8,607 across twelve random rounds.
    /// * **250** is above everything a tile is made of, so it counts only the
    ///   emoji. Measured 2,190–6,614 across the same twelve.
    ///
    /// Both floors sit far under the observed minimum and both collapse to zero
    /// when the grid is removed.
    ///
    /// Mutation: replace `grid` with `EmptyView`. Both expectations report 0.
    @Test("the upright screen draws its round")
    func portraitScreenDrawsItsRound() async {
        let bitmap = await Bitmap.of(
            screen(makeModel()),
            width: CGFloat(Self.portraitSize.width), height: CGFloat(Self.portraitSize.height),
            settling: .milliseconds(400), fillsWindow: true
        )
        let plates = Self.band(bitmap, Self.gridBand.top, Self.gridBand.bottom, threshold: 160)
        let glyphs = Self.band(bitmap, Self.gridBand.top, Self.gridBand.bottom, threshold: 250)

        #expect(plates > 3000, "only \(plates) tile pixels — the grid did not draw")
        #expect(glyphs > 800, "only \(glyphs) bright pixels inside the tiles — the tiles are empty")
    }

    /// The bug this exists for is documented in `CountMode.tsx`: the counting area
    /// took its content height as a floor, and from the second round onward it
    /// pushed Shuffle and Next off the bottom of the screen. The web fixed it with
    /// `min-height: 0` and a scroll; the `ScrollView` here is the equivalent.
    ///
    /// **375 × 480 with a round of ten is not a device — it is the smallest window
    /// in which the round genuinely does not fit, and that is the only geometry
    /// where this property has any teeth.** The column wants 616pt there (80 header
    /// + 112 readout + 340 of grid + 84 of controls). On every real phone and in
    /// the landscape window the brief proposed, a round of nine fits with room to
    /// spare, so the same assertions pass just as happily over a counting area with
    /// no give in it at all — which is how a test that cannot fail gets written.
    ///
    /// Both ends are asserted, because the two ways of getting this wrong lose
    /// different ones:
    ///
    /// * a counting area that stops being the flexible piece un-pins the controls
    ///   from the bottom, and the **bottom** band empties;
    /// * a counting area that insists on its content height overflows the window,
    ///   and SwiftUI centres the overflow — so the **top** band loses the mascot
    ///   while the bottom band fills with tiles, which look exactly like a button
    ///   to any brightness test.
    ///
    /// Measured: 547 in the bottom band and 2,451 in the top, both stable to ±1
    /// across rounds, because neither band contains a countable.
    ///
    /// Mutation: drop the `ScrollView` and the `maxHeight: .infinity` from
    /// `countingArea`. Both expectations fail.
    @Test("the largest round in the shortest window leaves the controls on screen")
    func controlsSurviveTheLargestRound() async {
        let width = 375
        let height = 480
        let bitmap = await Bitmap.of(
            screen(makeModel(countRange: 10...10)),
            width: CGFloat(width), height: CGFloat(height),
            settling: .milliseconds(400), fillsWindow: true
        )
        // The bottom 60pt, where the controls live, less the last 10 — several
        // rows, because pinning one y to a button by arithmetic is how a passing
        // test becomes brittle.
        let controls = Self.band(bitmap, height - 60, height - 10, threshold: 400)
        // The top 60pt, which is the mascot and the wordmark.
        let chrome = Self.band(bitmap, 0, 60, threshold: 400)

        #expect(controls > 200, "only \(controls) lit pixels in the bottom 60pt — Shuffle is off the screen")
        #expect(chrome > 1500, "only \(chrome) lit pixels in the top 60pt — the column overflowed the window")
    }
}
