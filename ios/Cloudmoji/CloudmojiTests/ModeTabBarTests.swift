import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

@Suite("ModeTabBar")
@MainActor
struct ModeTabBarTests {

    /// A contract with `CountModeUITests`, which queries `tab-words` and
    /// `tab-count`. Renaming a case silently breaks a test in another target that
    /// this one cannot see.
    ///
    /// Mutation: reorder the cases, or change a raw value. The UI tests stop
    /// finding the control they switch modes with and pass forever.
    @Test("the two modes carry the identifiers the UI tests query")
    func modeRawValues() {
        #expect(AppMode.allCases.map(\.rawValue) == ["words", "count"])
        #expect(AppMode.words.label == "Words")
        #expect(AppMode.count.label == "Count")
    }

    /// The web's bug, in the one place it can recur. The bar shipped at 42.5pt
    /// because the home-indicator inset was folded *into* its 64pt rather than
    /// added below it, and a 42pt target is under the floor for the control a
    /// toddler uses to change what the whole app is doing.
    ///
    /// This measures the bar's own ideal height, which excludes the safe area
    /// entirely — so it is the "is the bar itself 64pt" half of the rule. The
    /// "and the inset did not eat it" half needs a real device frame and lives in
    /// `CountModeUITests`.
    ///
    /// **Mutation: delete `.frame(minWidth:minHeight:)` from `tab(_:showsLabel:)`.**
    /// The bar collapses to 50pt — a 26pt glyph, 3pt of gap and a 12pt caption —
    /// and this fails.
    ///
    /// The first draft of the view also carried a `.frame(minHeight:)` on the row
    /// itself, and this test could not fail: with two lines each independently
    /// worth 64pt, deleting either left the other holding the bar open, and the
    /// one that survived was the row — which is not the thing a toddler taps. The
    /// row frame is gone, so the per-tab frame is now the single line under test,
    /// and it is the right one: it is also what makes the *accessibility* frame
    /// 64pt, which is what `CountModeUITests` measures on a real tree.
    @Test("the bar is at least 64pt tall before any safe area is added")
    func barClearsTheChildMinimum() {
        let bitmap = Bitmap.rendered(
            ModeTabBar(mode: .words, layout: .bar, onSelect: { _ in }).frame(width: 375)
        )
        #expect(bitmap.height >= 64, "the tab bar is \(bitmap.height)pt tall — 64 is the floor")
    }

    /// Sideways the tabs move into the rail, and the rule does not change with the
    /// layout.
    ///
    /// The height floor is **80**, not 64, and the difference is the whole point:
    /// what is measured is the rail *section*, which pads 8pt above and below its
    /// tabs. A `>= 64` floor there would be cleared by a 48pt tab — and by the
    /// glyph-sized 32pt one the mutation below actually produces, if the icon were
    /// a shade taller. 80 is the section with a full-size tab in it. Spelled as a
    /// literal rather than `side + 2 * spacing`, which would be the implementation
    /// asserted against its own definition.
    ///
    /// Mutation: delete `.frame(minWidth:minHeight:)` from `tab(_:showsLabel:)`.
    /// The section renders 48 × 76 and both expectations fail. The width is the
    /// half worth having here: the rail is the layout where a tab has no caption
    /// to widen it, so 64pt across is the frame's doing and nothing else's.
    @Test("a rail tab is at least 64pt square")
    func railTabsClearTheChildMinimum() {
        let bitmap = Bitmap.rendered(ModeTabBar(mode: .words, layout: .rail, onSelect: { _ in }))
        #expect(
            bitmap.height >= 80,
            "the rail tabs are \(bitmap.height)pt tall — 64 of target plus 8pt of breathing room each side"
        )
        #expect(
            bitmap.width >= 64 * 2 + 8,
            "the rail tabs are \(bitmap.width)pt wide — two 64pt targets and an 8pt gap"
        )
    }

    /// Which tab is active is the only thing on this control that carries meaning,
    /// and it is silently droppable: a bar that highlights neither, or both, still
    /// switches modes perfectly.
    ///
    /// Rendered twice with the selection flipped and the halves compared, rather
    /// than thresholded once — 🗣️ and 🧮 are not equally bright, so a single
    /// render cannot tell "Words is highlighted" from "the speaking-head emoji has
    /// more pixels in it".
    ///
    /// Mutation: hardcode `isActive` to `true`. Both halves light in both renders
    /// and both expectations fail.
    @Test("exactly the selected tab is highlighted")
    func selectionIsVisible() async {
        func halves(_ mode: AppMode) async -> (left: Int, right: Int) {
            let width = 375
            let bitmap = await Bitmap.of(
                ModeTabBar(mode: mode, layout: .bar, onSelect: { _ in })
                    .frame(width: CGFloat(width)),
                width: CGFloat(width), height: 90
            )
            var left = 0
            var right = 0
            for y in 0..<80 {
                for run in bitmap.runs(y: y, threshold: 300) {
                    if run.start < width / 2 { left += run.width } else { right += run.width }
                }
            }
            return (left, right)
        }

        let wordsActive = await halves(.words)
        let countActive = await halves(.count)

        #expect(
            wordsActive.left > countActive.left,
            "selecting Words did not brighten the Words tab"
        )
        #expect(
            countActive.right > wordsActive.right,
            "selecting Count did not brighten the Count tab"
        )
    }

    /// The rail is the *only* place the tabs may appear sideways. A bottom bar in
    /// landscape takes 64 of the ~390pt a phone has there — a sixth of the screen,
    /// out of the axis that is already starving — and it is invisible in every
    /// portrait screenshot, which is how it would ship.
    ///
    /// Measured on the colour of the bottom-right pixel, which is the tab bar's
    /// own plate and nothing else. `Theme.bgPrimary` at 95% sums to about 72;
    /// the background gradient's bottom stop (`bgEdge`) sums to 101. Sampled at
    /// the right edge because sideways the bottom-*left* pixel is the rail plate,
    /// which is also darker than the gradient — comparing there would pass on the
    /// rail and prove nothing about the bar.
    ///
    /// Both orientations are measured in the same run, so the assertion is a
    /// difference between two real renders rather than a threshold picked to sit
    /// between two remembered numbers.
    ///
    /// Mutation: drop the `if !isCompact` guard in `RootContent`. The landscape
    /// window grows a bar too, its bottom-right pixel drops to the plate colour,
    /// and the second expectation fails.
    @Test("the bar is on screen upright and gone sideways")
    func theBarIsPortraitOnly() async {
        func bottomRight(width: CGFloat, height: CGFloat) async -> Int {
            let suite = UUID().uuidString
            let defaults = UserDefaults(suiteName: suite)!
            defaults.removePersistentDomain(forName: suite)
            let model = AppModel(settings: SettingsStore(defaults: defaults))
            let bitmap = await Bitmap.of(
                ContentView().environment(model),
                width: width, height: height,
                settling: .milliseconds(400), fillsWindow: true
            )
            return bitmap.rgb(x: Int(width) - 3, y: Int(height) - 2).sum
        }

        let upright = await bottomRight(width: 440, height: 956)
        let sideways = await bottomRight(width: 956, height: 440)

        #expect(upright < 90, "the bottom of the upright screen (\(upright)) is not the tab bar's plate")
        #expect(
            sideways > upright,
            "sideways the bottom edge (\(sideways)) is as dark as the upright tab bar (\(upright)) — the bar is still eating the scarce axis"
        )
    }
}
