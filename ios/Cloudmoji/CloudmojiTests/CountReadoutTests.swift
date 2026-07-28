import SwiftUI
import Testing
import UIKit
@testable import Cloudmoji

@Suite("CountReadout")
@MainActor
struct CountReadoutTests {

    /// A filled dot is teal (sums to 479); an empty one is 10% white over the
    /// background and sums to about 120. 300 is comfortably between them.
    static let dotThreshold = 300

    /// The dots are the round's progress bar and the only thing on screen that
    /// says how many are left. Six of them, two counted: a fixture of three could
    /// not tell "one too many" from "all of them", which is the mutation that
    /// matters.
    ///
    /// Several scanlines are read and the largest run count taken, because the dot
    /// row is 12pt tall after its 1.2 scale and pinning a single y to it by
    /// arithmetic is how a passing test becomes a fragile one.
    ///
    /// Mutation: change `index < progress` to `index <= progress`. Three dots
    /// light and this fails.
    @Test("exactly as many dots are lit as things have been counted")
    func dotsFollowProgress() async {
        func litDots(progress: Int) async -> Int {
            let bitmap = await Bitmap.of(
                CountReadout(target: 6, progress: progress, numeral: "\(progress)", phrase: "six dogs")
                    .frame(width: 375),
                width: 375, height: 160
            )
            return (0..<20).map { bitmap.runs(y: $0, threshold: Self.dotThreshold).count }.max() ?? 0
        }

        #expect(await litDots(progress: 0) == 0, "nothing counted, yet dots are lit")
        #expect(await litDots(progress: 2) == 2)
        #expect(await litDots(progress: 6) == 6, "a finished round should light every dot")
    }

    /// The exact bug `WordsView.bubbleRow` was built to fix, in the one other place
    /// it can happen. The numeral and phrase only exist once something has been
    /// counted, and if their slot collapses when they are absent, the entire grid
    /// jumps down the screen on the first tap and back up on every shuffle.
    ///
    /// **Measured inside a stack, which is the whole point.** An absent `if`
    /// branch is an `EmptyView`, and a stack drops an `EmptyView` from its layout
    /// *along with any frame on it* — but `ImageRenderer` handed the same view on
    /// its own reports a perfectly correct height. The first version of the
    /// equivalent Words-mode test measured it alone, passed, and passed just as
    /// happily against the broken row.
    ///
    /// 10 + 112 + 10, with the two rules spelled out as literals: 132 means the
    /// slot is there, 20-something means it vanished.
    ///
    /// Mutation: replace the `Color.clear.frame(…).overlay { … }` with a bare
    /// `if progress > 0 { … }`. The stack collapses.
    @Test("the readout holds its height inside a stack before anything is counted")
    func readoutReservesItsHeightWhenEmpty() {
        let stacked = VStack(spacing: 0) {
            Color.red.frame(height: 10)
            CountReadout(target: 4, progress: 0, numeral: "", phrase: "")
            Color.red.frame(height: 10)
        }
        .frame(width: 375)

        #expect(
            Bitmap.rendered(stacked).height == 132,
            "the readout collapsed — the stack is \(Bitmap.rendered(stacked).height)pt, not 10 + 112 + 10"
        )
    }

    /// The other half of the same rule, and it is not redundant: the test above
    /// only ever sees the *empty* readout, so it passes just as happily against a
    /// readout that reserves 112pt and then grows past it once counting starts —
    /// which jumps the grid exactly as badly.
    ///
    /// Mutation, run and confirmed: keep `Color.clear.frame(height:)` but put
    /// `numberBlock` after it as a sibling of the stack rather than an overlay on
    /// it. The empty test above still passes; this one reports 232pt.
    @Test("the readout is exactly as tall once something has been counted")
    func readoutHoldsTheSameHeightWhenFilled() {
        func stackedHeight(progress: Int, numeral: String, phrase: String) -> Int {
            Bitmap.rendered(
                VStack(spacing: 0) {
                    Color.red.frame(height: 10)
                    CountReadout(target: 4, progress: progress, numeral: numeral, phrase: phrase)
                    Color.red.frame(height: 10)
                }
                .frame(width: 375)
            ).height
        }

        #expect(
            stackedHeight(progress: 3, numeral: "3", phrase: "three dogs") == 132,
            "the readout grew to \(stackedHeight(progress: 3, numeral: "3", phrase: "three dogs"))pt once filled"
        )
    }

    /// Sideways there is a third of the height, and the readout has to give most of
    /// it back or the controls go off the bottom of the screen — which is exactly
    /// what happened on the web from the second round onward.
    ///
    /// Mutation: return the same height for both. This fails.
    @Test("the readout is far shorter sideways than upright")
    func readoutHeightsDiffer() {
        #expect(CountReadoutMetrics.height(compact: false) == 112)
        #expect(CountReadoutMetrics.height(compact: true) == 72)
    }
}
