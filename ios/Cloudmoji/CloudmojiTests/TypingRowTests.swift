import SwiftUI
import Testing
import CloudmojiCore
@testable import Cloudmoji

/// The typing row's job is to keep four things true, none of which the compiler
/// checks and only one of which a screenshot would make obvious:
///
/// 1. Everything in it is at least 64pt, because a child taps all of it.
/// 2. There is at least 8pt between any two of those targets.
/// 3. It **scrolls** rather than shrinking when the emojis stop fitting.
/// 4. The controls are not there when there is nothing to replay or delete.
///
/// So these lay the real row out in a real window and measure the drawn control
/// buttons off the pixels. A version of this suite that read `TypingRowMetrics`
/// back out would have passed against a row whose controls were being squeezed
/// to 40pt by a full strip of emojis — which is the actual failure this is here
/// to catch.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("TypingRow")
@MainActor
struct TypingRowTests {

    // MARK: Fixtures

    static let words = [
        ("🍎", "apple"), ("🐶", "dog"), ("🚗", "car"), ("🌈", "rainbow"),
        ("🍌", "banana"), ("🐱", "cat"), ("⭐️", "star"), ("🎈", "balloon"),
    ]

    func typed(_ count: Int) -> [TypedEmoji] {
        (0..<count).map { index in
            let (emoji, word) = Self.words[index % Self.words.count]
            return TypedEmoji(emoji: emoji, word: word)
        }
    }

    func row(
        _ typed: [TypedEmoji],
        muted: Bool = false,
        language: Language = .en
    ) -> some View {
        TypingRow(
            typed: typed,
            muted: muted,
            language: language,
            onReplay: {},
            onDelete: {},
            onClear: {},
            onTapTyped: { _ in }
        )
    }

    // MARK: Measurement

    /// The control fills are their tint at 20% over the row's own 4%-white
    /// plate: teal sums to about 120, amber 123, coral 117, while the bare plate
    /// sums to 30 and its 6%-white border to 45. 70 sits in the empty middle, so
    /// a run here is a control button and nothing else.
    static let controlThreshold = 70

    /// The vertical centre of the row's content, where the control buttons'
    /// left and right edges are straight and unantialiased.
    static let scanline = Int(TypingRowMetrics.verticalPadding + TypingRowMetrics.controlSide / 2)

    /// Everything right of here, at 430pt wide with a single typed emoji, is
    /// controls: the strip ends at 204 and the leftmost control starts at 212,
    /// while the one emoji in it never reaches past 74. Fixed rather than
    /// derived from the metrics, so shrinking a control cannot also shrink the
    /// window the test is looking through.
    static let controlRegionStart = 180
    static let wideWidth: CGFloat = 430

    /// The 1pt border is stroked *centred* on the button's edge, so it spills
    /// half a point either side and a 64pt control measures 66. Charged against
    /// the measurement, never against the rule: a control genuinely shrunk to
    /// 63pt measures 65 and still fails.
    ///
    /// The rightmost control is the exception — its outer spill lands on the
    /// row's last pixel column at half coverage and drops under the threshold,
    /// so it measures 65. Widths are therefore taken off every control but the
    /// last, and the last is held to its *position* instead.
    static let antialiasFringe = 2

    /// The rule, applied to the three control runs however they were captured.
    func expectControlsObeyTheRule(_ runs: [Bitmap.Run], within width: CGFloat) {
        #expect(runs.count == 3, "found \(runs.count) controls, not 3")

        for control in runs.dropLast() {
            let drawn = control.width - Self.antialiasFringe
            #expect(drawn >= 64, "a control is only \(drawn)pt wide")
        }

        // Measured centre to centre, then the button's own width taken back
        // off. Both edges of both buttons carry the same spill, so it cancels
        // and this is exact — 72pt of pitch minus 64pt of button is 8pt of gap.
        for (left, right) in zip(runs, runs.dropFirst()) {
            let pitch = CGFloat(right.start - left.start)
            let gap = pitch - (CGFloat(left.width) - CGFloat(Self.antialiasFringe))
            // 8, spelled out rather than read back from `TypingRowMetrics`: a
            // test that quotes the constant at itself follows it anywhere, and
            // shrinking `spacing` to 4 would leave it green.
            #expect(gap >= 8, "only \(gap)pt between two controls")
        }

        if let last = runs.last {
            #expect(
                CGFloat(last.end) <= width - TypingRowMetrics.horizontalPadding
                    + CGFloat(Self.antialiasFringe),
                "the controls reach \(last.end) on a \(Int(width))pt screen"
            )
        }
    }

    func controls(
        _ typed: [TypedEmoji],
        muted: Bool = false,
        language: Language = .en,
        width: CGFloat = TypingRowTests.wideWidth,
        from: Int = TypingRowTests.controlRegionStart
    ) async -> [Bitmap.Run] {
        let bitmap = await Bitmap.of(
            row(typed, muted: muted, language: language),
            width: width,
            height: 200
        )
        return bitmap.runs(y: Self.scanline, threshold: Self.controlThreshold, from: from)
    }

    // MARK: The touch-target rule, measured off a real render

    /// The non-negotiable rule from `CLAUDE.md`, on the surface the child taps
    /// to hear a word again. Measured off the rendered button, so it fails if
    /// the frame is dropped and the 32pt glyph is left to size the target —
    /// which still builds, still draws an apple, and is roughly 38pt.
    @Test("a typed emoji renders at least the 64pt minimum a child-facing target may be")
    func typedEmojiIsBigEnoughForAToddler() {
        let bitmap = Bitmap.rendered(
            TypedEmojiButton(item: TypedEmoji(emoji: "🍎", word: "apple")) {}
        )
        #expect(bitmap.width >= 64, "typed emoji is \(bitmap.width)pt wide")
        #expect(bitmap.height >= 64, "typed emoji is \(bitmap.height)pt tall")
    }

    /// The same, for the replay/delete/clear buttons. These are the ones the
    /// design system's own touch-target table still lists at 34 × 34 — a stale
    /// row the shipped web already ignores, and the one number in this project
    /// most likely to be copied back in by someone reading that table.
    @Test("a control button renders at least the 64pt minimum a child-facing target may be")
    func controlIsBigEnoughForAToddler() {
        let bitmap = Bitmap.rendered(
            TypingRowControl(
                glyph: "🔊", label: "Replay", identifier: "replay-btn",
                tint: Theme.teal, glyphSize: TypingRowMetrics.replayGlyphSize, action: {}
            )
        )
        #expect(bitmap.width >= 64, "control is \(bitmap.width)pt wide")
        #expect(bitmap.height >= 64, "control is \(bitmap.height)pt tall")
    }

    /// A guardrail on the constants themselves, stating the *rule* (≥ 64, ≥ 8)
    /// rather than the values, so it survives a redesign and still fails a
    /// shrink. This is the assertion `tests/review-fixes.spec.ts` makes on the
    /// web.
    @Test("the metrics obey the toddler touch-target rule")
    func metricsObeyTheRule() {
        #expect(TypingRowMetrics.typedSide >= 64)
        #expect(TypingRowMetrics.controlSide >= 64)
        #expect(TypingRowMetrics.spacing >= 8)
        // A container shorter than its contents clips the target it holds.
        #expect(TypingRowMetrics.minHeight >= TypingRowMetrics.controlSide)
    }

    /// The PRD cap, and `MAX_TYPED` in `src/components/WordsMode.tsx`. The row
    /// does not enforce it — whoever owns the array does — so this is the number
    /// they enforce it against.
    @Test("the row publishes the PRD's 50-emoji cap")
    func maxTypedMatchesThePRD() {
        #expect(TypingRow.maxTyped == 50)
    }

    // MARK: Layout, measured off the drawn row

    /// The cheapest guard against a suite that measures an empty image: if this
    /// finds no controls, nothing below this line means anything.
    @Test("the row draws three controls once something has been typed")
    func rowDrawsItsControls() async {
        let runs = await controls(typed(1))
        #expect(runs.count == 3, "found \(runs.count) controls, not 3")
    }

    /// The rule again, on buttons that have been through the row's own
    /// arithmetic rather than rendered alone with nothing competing for width.
    @Test("every control drawn in the row is at least 64pt wide, 8pt apart, and on screen")
    func controlsObeyTheTouchTargetRuleInSitu() async {
        expectControlsObeyTheRule(await controls(typed(1)), within: Self.wideWidth)
    }

    /// The row scrolls; it does not shrink. Fill it past what fits on the
    /// narrowest phone we support and the controls must still be full size and
    /// still on screen — the emojis are what slide out of view.
    ///
    /// This is the test that a metrics-reading suite cannot write: nothing about
    /// `TypingRowMetrics` changes when a greedy strip pushes the controls off
    /// the edge of a 375pt screen.
    @Test("a full row scrolls rather than squeezing the controls")
    func rowScrollsRatherThanShrinkingItsTargets() async {
        let width: CGFloat = 375
        let bitmap = await Bitmap.of(row(typed(TypingRow.maxTyped)), width: width, height: 200)
        // The strip's clipped emoji glyphs light up too, so measure the whole
        // scanline and take the three rightmost runs — the controls are pinned
        // to the trailing edge, with the row's 8pt gap keeping them separate.
        let all = bitmap.runs(y: Self.scanline, threshold: Self.controlThreshold)
        #expect(all.count > 3, "the strip drew nothing — only \(all.count) runs on the scanline")
        expectControlsObeyTheRule(Array(all.suffix(3)), within: width)
    }

    /// A row longer than the screen must show the *newest* emoji, not the
    /// oldest. Get this wrong and the strip freezes on the first thing the child
    /// ever tapped: the app still speaks, still bounces, and appears to have
    /// stopped listening — the single worst failure this row can have, and one
    /// that no touch-target or control assertion notices.
    ///
    /// Read off colour rather than shape: the oldest emoji is a red square and
    /// the newest a blue one, with white squares between, so "which end of the
    /// strip is on screen" becomes a question about hue.
    @Test("a row longer than the screen shows its newest emoji, not its oldest")
    func stripScrollsToTheNewestEmoji() async {
        var glyphs = Array(repeating: "⬜️", count: 20)
        glyphs[0] = "🟥"
        glyphs[glyphs.count - 1] = "🟦"
        let items = glyphs.map { TypedEmoji(emoji: $0, word: "square") }

        let width: CGFloat = 375
        // Settled, because the scroll is done from `.task` — which is exactly
        // why `ImageRenderer` could not be used for this.
        let bitmap = await Bitmap.of(
            row(items), width: width, height: 200, settling: .milliseconds(300)
        )

        // The strip, left of where the controls begin on a 375pt screen.
        var sawNewest = false
        var sawOldest = false
        for x in Int(TypingRowMetrics.horizontalPadding)..<150 {
            let pixel = bitmap.rgb(x: x, y: Self.scanline)
            if pixel.b > pixel.r + 40 { sawNewest = true }
            if pixel.r > pixel.b + 40 { sawOldest = true }
        }
        #expect(sawNewest, "the newest emoji is off screen — the strip is not scrolled to the end")
        #expect(!sawOldest, "the oldest emoji is still on screen")
    }

    // MARK: Which controls appear

    /// A replay button with nothing to play is a control that does nothing when
    /// tapped, and "no failure states" is rule 4. The web hides it when muted.
    @Test("muting removes the replay button and leaves the other two")
    func mutingHidesReplay() async {
        #expect(await controls(typed(1), muted: false).count == 3)
        #expect(await controls(typed(1), muted: true).count == 2)
    }

    /// Nothing typed, nothing to replay, delete or clear. Sampled at the centre
    /// of where the trailing control sits rather than by counting runs, so the
    /// placeholder text — which is also lit — cannot be mistaken for a button.
    @Test("an empty row draws no controls at all")
    func emptyRowHasNoControls() async {
        let x = Int(Self.wideWidth - TypingRowMetrics.horizontalPadding
                    - TypingRowMetrics.controlSide / 2)

        let empty = await Bitmap.of(row([]), width: Self.wideWidth, height: 200)
        #expect(
            empty.rgb(x: x, y: Self.scanline).sum <= Self.controlThreshold,
            "something is drawn where the clear button would be"
        )

        let populated = await Bitmap.of(row(typed(1)), width: Self.wideWidth, height: 200)
        #expect(
            populated.rgb(x: x, y: Self.scanline).sum > Self.controlThreshold,
            "the clear button is not where the empty-row check looked for it"
        )
    }

    // MARK: Placeholder

    /// The row must say something before the first tap, and say it in the
    /// language the family chose. Compared as pixels rather than as strings: a
    /// row that looks up the right string and then draws the English one — or
    /// draws nothing, because the text wrapped out of a 64pt-tall row — passes
    /// every dictionary assertion.
    @Test("the empty row draws a placeholder, in the chosen language")
    func placeholderIsDrawnAndLocalised() async {
        let english = await Bitmap.of(row([], language: .en), width: Self.wideWidth, height: 200)
        let japanese = await Bitmap.of(row([], language: .ja), width: Self.wideWidth, height: 200)

        // The plate alone is a uniform 30; text on it is ~180. A blank row would
        // report zero here.
        #expect(english.litPixels(threshold: 120) > 200, "the placeholder was not drawn")
        #expect(japanese.litPixels(threshold: 120) > 200, "the Japanese placeholder was not drawn")
        #expect(
            english.litPixels(threshold: 120) != japanese.litPixels(threshold: 120),
            "the same pixels in English and Japanese — the language is being ignored"
        )
    }

    /// Every language has its own placeholder; none silently falls back to
    /// English. Cheap, and the only thing that catches a missing row when a
    /// sixth language is added.
    @Test("every language has its own placeholder")
    func everyLanguageHasAPlaceholder() {
        let all = Language.allCases.map(TypingRow.placeholder)
        #expect(Set(all).count == Language.allCases.count)
        for placeholder in all {
            #expect(!placeholder.isEmpty)
        }
    }

    // MARK: Press feedback

    /// The design system requires an `:active` transform on every tappable
    /// element — a press that does nothing reads as the app being broken.
    /// Only the constants can be checked from here; that the style is actually
    /// attached, and that it is not `.buttonStyle(.plain)`, was confirmed by
    /// pressing the buttons in the simulator.
    @Test("both press scales shrink, and stay recognisably the same control")
    func pressFeedbackShrinks() {
        for scale in [TypingRowMetrics.typedPressedScale, TypingRowMetrics.controlPressedScale] {
            #expect(scale < 1)
            #expect(scale > 0.5)
        }
    }
}
