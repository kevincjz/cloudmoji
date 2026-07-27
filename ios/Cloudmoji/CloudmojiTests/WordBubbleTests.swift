import SwiftUI
import Testing
@testable import Cloudmoji

/// The word bubble is the app's only written confirmation that the tap was
/// heard, and it is on screen for 2.2 seconds. Two things about it are easy to
/// break invisibly: it can stop showing the word it was given, and it can stop
/// appearing at all — a bubble whose entrance animation never runs sits at
/// opacity 0 for its whole life and every "it builds" check still passes.
///
/// The visual assertions target ``WordBubbleLabel``, which has no animation on
/// it, so `ImageRenderer` is exact. The animated ``WordBubble`` is checked
/// separately, in a real window, for actually becoming visible.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("WordBubble")
@MainActor
struct WordBubbleTests {

    // MARK: Content

    /// The bubble draws the word it was handed. Deleting the `Text(word)` leaves
    /// a bubble that still draws, still has the right colours, and says nothing.
    @Test("the bubble draws the word it is given")
    func bubbleDrawsItsWord() {
        let apple = Bitmap.rendered(WordBubbleLabel(emoji: "🍎", word: "apple"))
        let banana = Bitmap.rendered(WordBubbleLabel(emoji: "🍎", word: "banana"))
        #expect(apple.width > 0, "the bubble rendered nothing at all")
        // Different words, different bubble — the longer word is the wider one.
        #expect(banana.width > apple.width, "the word is not sizing the bubble")
    }

    /// And the emoji. Same word, different glyph: if the emoji is dropped the
    /// two renders are identical.
    @Test("the bubble draws the emoji it is given")
    func bubbleDrawsItsEmoji() {
        let apple = Bitmap.rendered(WordBubbleLabel(emoji: "🍎", word: "fruit"))
        let dog = Bitmap.rendered(WordBubbleLabel(emoji: "🐶", word: "fruit"))
        #expect(apple.width > 0)
        #expect(apple.width == dog.width, "two emoji should occupy the same width")
        // 🍎 is red, 🐶 is brown; somewhere in the glyph the pixels must differ.
        let differs = (0..<apple.height).contains { y in
            (0..<apple.width).contains { x in apple.rgb(x: x, y: y) != dog.rgb(x: x, y: y) }
        }
        #expect(differs, "the two bubbles are pixel-identical — the emoji is not drawn")
    }

    // MARK: Colour

    /// `linear-gradient(135deg, rgba(255,107,107,0.2), rgba(78,205,196,0.2))` —
    /// coral into teal, the app's two brand colours meeting.
    ///
    /// This is the assertion that fails against a neutral `.ultraThinMaterial`
    /// capsule, which looks perfectly reasonable in a screenshot and is a system
    /// alert rather than the app answering the child. Sampled in the horizontal
    /// padding either side of the text, at the vertical midpoint where the
    /// rounded rect is at full width.
    @Test("the bubble runs coral on the left into teal on the right")
    func bubbleIsCoralIntoTeal() {
        let bitmap = Bitmap.rendered(WordBubbleLabel(emoji: "🍎", word: "apple"))
        let y = bitmap.height / 2
        let left = bitmap.rgb(x: 6, y: y)
        let right = bitmap.rgb(x: bitmap.width - 7, y: y)

        #expect(left.r > left.b, "the left of the bubble is not coral: \(left)")
        #expect(left.r > left.g, "the left of the bubble is not coral: \(left)")
        #expect(right.g > right.r, "the right of the bubble is not teal: \(right)")
        #expect(right.b > right.r, "the right of the bubble is not teal: \(right)")
    }

    // MARK: The wordFloat curve

    /// `@keyframes wordFloat`, as the four states it interpolates between. A
    /// bubble whose `entering` opacity is already 1, or whose `leaving` opacity
    /// is still 1, never appears to arrive or leave — and draws identically to a
    /// correct one in any single-frame snapshot.
    @Test("the phases rise from nothing, overshoot, settle, and fade upward")
    func phasesMatchTheWordFloatKeyframes() {
        #expect(WordBubblePhase.entering.opacity == 0)
        #expect(WordBubblePhase.arrived.opacity == 1)
        #expect(WordBubblePhase.settled.opacity == 1)
        #expect(WordBubblePhase.leaving.opacity == 0)

        // Rises into place from below, drifts up and away on the way out.
        #expect(WordBubblePhase.entering.offsetY > 0)
        #expect(WordBubblePhase.arrived.offsetY == 0)
        #expect(WordBubblePhase.leaving.offsetY < 0)

        // Small, overshoot, settle. Without the overshoot it slides rather than
        // pops, which is the whole character of the animation.
        #expect(WordBubblePhase.entering.scale < 1)
        #expect(WordBubblePhase.arrived.scale > 1)
        #expect(WordBubblePhase.settled.scale == 1)
        #expect(WordBubblePhase.leaving.scale < 1)
    }

    /// The four legs must add up to the lifetime. They are derived from it, so
    /// this fails the moment one is replaced by a hand-written literal — which
    /// is how a bubble ends up still fading after its owner has removed it.
    @Test("the animation legs fill exactly the 2.2s the web gives the bubble")
    func legsFillTheLifetime() {
        // 2200ms in `src/components/WordsMode.tsx`, `wordFloat 2.2s` in
        // `src/index.css`. The owner's timeout and this must stay equal.
        #expect(WordBubbleMetrics.lifetime == 2.2)

        let total = WordBubbleMetrics.enterDuration
            + WordBubbleMetrics.settleDuration
            + WordBubbleMetrics.holdDuration
            + WordBubbleMetrics.exitDuration
        #expect(abs(total - WordBubbleMetrics.lifetime) < 0.0001, "the legs total \(total)s")

        for leg in [
            WordBubbleMetrics.enterDuration, WordBubbleMetrics.settleDuration,
            WordBubbleMetrics.holdDuration, WordBubbleMetrics.exitDuration,
        ] {
            #expect(leg > 0, "a leg of the animation has no duration")
        }
    }

    // MARK: It actually appears

    /// The one thing none of the above proves. ``WordBubble`` starts at opacity
    /// 0 and only becomes visible once its `.task` has run — and `ImageRenderer`
    /// never runs `.task`, so a bubble with the entrance animation deleted
    /// renders blank and every pixel comparison between two blank images passes.
    ///
    /// So: a real window, a real wait past the 0.55s the entrance takes, and a
    /// count of lit pixels. Deleting the `.task` leaves this at zero.
    @Test("the bubble is invisible at first and visible once it has animated in")
    func bubbleAnimatesItselfIntoView() async {
        let bubble = WordBubble(emoji: "🍎", word: "apple")

        let atRest = await Bitmap.of(bubble, width: 300, height: 120)
        let arrived = await Bitmap.of(bubble, width: 300, height: 120, settling: .milliseconds(900))

        #expect(arrived.litPixels(threshold: 30) > 500, "the bubble never became visible")
        #expect(
            arrived.litPixels(threshold: 30) > atRest.litPixels(threshold: 30),
            "the bubble is not animating in — it looks the same before and after"
        )
    }

    /// And it takes itself away again, so the fade-out is not left to whoever
    /// removes it. Past `fadeFrom` (1.716s) plus the exit, nothing is left.
    @Test("the bubble has faded out by the end of its lifetime")
    func bubbleFadesItselfOut() async {
        let bubble = WordBubble(emoji: "🍎", word: "apple")
        let visible = await Bitmap.of(bubble, width: 300, height: 120, settling: .milliseconds(900))
        let gone = await Bitmap.of(bubble, width: 300, height: 120, settling: .milliseconds(2600))

        #expect(gone.litPixels(threshold: 30) < visible.litPixels(threshold: 30) / 4,
                "the bubble is still on screen after its 2.2s is up")
    }
}
