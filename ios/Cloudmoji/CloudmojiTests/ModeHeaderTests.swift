import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

/// The header is parent chrome sharing one strip with a 64pt mascot on a 375pt
/// screen, which is the tightest width budget in the app. Two things can go wrong
/// and neither shows up on the simulator this machine runs: the controls can fall
/// under the 44pt HIG floor, and the whole strip can overflow an iPhone SE.
@Suite("ModeHeader")
@MainActor
struct ModeHeaderTests {

    func makeModel() -> AppModel {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return AppModel(settings: SettingsStore(defaults: defaults))
    }

    func header(_ model: AppModel) -> some View {
        ModeHeader(mood: .happy, title: "Cloudmoji", subtitle: "Tap. Listen. Learn!")
            .environment(model)
    }

    /// 44 and not 64: the mute button is parent-only chrome, and forcing the
    /// child-facing floor on it swallows the header on a 375pt screen. 44 is the
    /// iOS HIG minimum and it is a floor, not a suggestion.
    ///
    /// Measured off a real render rather than read off `ModeHeaderMetrics`, which
    /// is why this control is its own type. A constant asserted against itself
    /// follows any value, including 20.
    ///
    /// Mutation: delete `.frame(width:height:)` in `ModeHeaderControl`. The glyph's
    /// own box is about 22pt and this fails on both axes.
    @Test("a header control is at least 44pt on both axes")
    func headerControlMeetsTheParentChromeMinimum() {
        let control = ModeHeaderControl(
            glyph: "🔊", label: "Mute", identifier: "mute-btn",
            tint: Theme.teal, isOn: false, action: {}
        )
        let bitmap = Bitmap.rendered(control)
        #expect(bitmap.width >= 44, "the control is \(bitmap.width)pt wide")
        #expect(bitmap.height >= 44, "the control is \(bitmap.height)pt tall")
    }

    /// The iPhone SE is 375pt wide and it is the device this header has to survive.
    /// The budget: 14pt inset, a 64pt mascot, 8pt, the wordmark, then every control
    /// and its 8pt gaps, then 14pt.
    ///
    /// Asserting an ideal width of 375 or less would be the obvious form and is
    /// wrong twice over: a wordmark with `minimumScaleFactor` reports its
    /// *unscaled* ideal, so that test fails on a header that lays out perfectly —
    /// and a strip that does not fit never reports anything at all. It degrades.
    ///
    /// The degradation was measured rather than assumed, because both guesses were
    /// wrong. The first draft of this test asserted "something is drawn in the last
    /// 60pt" and passed with the language picker **deleted outright**: with the
    /// picker gone the mute button simply slides right into that band and lights
    /// it. And the brief predicts the picker gets pushed off the right-hand edge;
    /// it does not. Adding four more 44pt controls squeezes the wordmark from 93
    /// columns down to 7 — `lineLimit(1)` truncates "Cloudmoji" to a sliver — and
    /// every control stays politely on screen. Nothing is ever clipped, so nothing
    /// aimed at the edges can catch it.
    ///
    /// So each assertion below is aimed at a different, measured failure, and none
    /// of them is satisfied by one control impersonating another:
    ///
    /// * neither edge is clipped, which is the one shape an overflow can take;
    /// * the wordmark still occupies the space between the mascot and the controls
    ///   — this is what actually breaks first, and it breaks silently;
    /// * both right-hand controls are drawn, as two separate clusters of lit
    ///   columns. This is the one that fails when the picker leaves.
    ///
    /// Mutation: delete `languagePicker` from the body (two clusters become one);
    /// or add four more `muteControl`s (the wordmark collapses to 7 columns).
    @Test("the header fits the mascot, the wordmark and both controls at 375pt")
    func headerKeepsThePickerOnANarrowPhone() async {
        let width = 375
        let bitmap = await Bitmap.of(
            header(makeModel()).frame(width: CGFloat(width)), width: CGFloat(width), height: 90
        )
        let columns = litColumns(bitmap, threshold: 150)
        #expect(!columns.isEmpty, "the header drew nothing at all")
        guard let first = columns.first, let last = columns.last else { return }

        // `Bitmap.of` draws over black and the header paints no background of its
        // own, so a lit column is a column something was drawn in.
        let padding = Int(ModeHeaderMetrics.horizontalPadding)
        #expect(
            first.start >= 8,
            "content starts at x=\(first.start) — the mascot is clipped, so the strip overflowed"
        )
        #expect(
            last.end - 1 <= width - padding,
            "content reaches x=\(last.end - 1), past the \(padding)pt inset — the strip overflowed"
        )

        // The wordmark's own band: right of the 14 + 64 mascot, left of the
        // controls, which start at 247 in a healthy layout. It draws 93 of these
        // 120 columns when it fits and 7 when it has been squeezed to a sliver.
        let wordmark = litColumnCount(bitmap, threshold: 150, in: 80..<200)
        #expect(
            wordmark >= 60,
            "the wordmark occupies only \(wordmark) of the 120 columns between the mascot and the controls — it has been squeezed to a sliver"
        )

        // Right of the wordmark there must be two things, not one.
        let controls = columns.filter { $0.start >= 200 }
        #expect(
            controls.count >= 2,
            "only \(controls.count) control cluster(s) right of x=200: \(controls) — the mute button and the language picker are not both on screen"
        )
    }

    /// Columns that anything was drawn in, merged into clusters.
    ///
    /// The merge distance is measured, not guessed. Inside one control the widest
    /// dark gap is 7 columns — between the picker's "EN" and its chevron. Between
    /// the mute button and the picker it is 33, because the 8pt spacing sits
    /// between two 44pt boxes whose glyphs do not reach their own edges. 12 is
    /// comfortably clear of the first and nowhere near the second, so each control
    /// comes out as exactly one cluster.
    private static let clusterGap = 12

    /// How many columns in `range` anything was drawn in.
    private func litColumnCount(_ bitmap: Bitmap, threshold: Int, in range: Range<Int>) -> Int {
        occupiedColumns(bitmap, threshold: threshold)[range].filter { $0 }.count
    }

    private func occupiedColumns(_ bitmap: Bitmap, threshold: Int) -> [Bool] {
        var occupied = [Bool](repeating: false, count: bitmap.width)
        for y in 0..<bitmap.height {
            for run in bitmap.runs(y: y, threshold: threshold) {
                for x in run.start..<min(run.end, bitmap.width) { occupied[x] = true }
            }
        }
        return occupied
    }

    private func litColumns(_ bitmap: Bitmap, threshold: Int) -> [Bitmap.Run] {
        let occupied = occupiedColumns(bitmap, threshold: threshold)
        var clusters: [Bitmap.Run] = []
        var current: Bitmap.Run?
        var darkRun = 0
        for x in 0..<bitmap.width {
            if occupied[x] {
                darkRun = 0
                if var run = current {
                    run.end = x + 1
                    current = run
                } else {
                    current = Bitmap.Run(start: x, end: x + 1)
                }
            } else if current != nil {
                darkRun += 1
                if darkRun >= Self.clusterGap {
                    clusters.append(current!)
                    current = nil
                }
            }
        }
        if let run = current { clusters.append(run) }
        return clusters
    }

    /// Mutation: return a constant from `muteGlyph`. One of the two fails, and the
    /// parent has no way to tell whether the app is silenced.
    @Test("the mute control says which state it is in")
    func muteGlyphFollowsTheState() {
        #expect(ModeHeader.muteGlyph(muted: false) == "🔊")
        #expect(ModeHeader.muteGlyph(muted: true) == "🔇")
    }

    /// A contract between this task, which sets `mascot-<mood>`, and the UI tests
    /// that query it. Renaming a case silently breaks a test in another target
    /// that this one cannot see.
    ///
    /// Mutation: change any case's raw value. `CountModeUITests` stops being able
    /// to observe the celebration and goes green forever.
    @Test("mascot moods carry the identifiers the UI tests query")
    func mascotMoodRawValues() {
        #expect(MascotMood.happy.rawValue == "happy")
        #expect(MascotMood.excited.rawValue == "excited")
        #expect(MascotMood.speaking.rawValue == "speaking")
        #expect(MascotMood.beaming.rawValue == "beaming")
        #expect(MascotMood.allCases.count == 4)
    }
}
