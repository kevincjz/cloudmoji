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
    /// it does not. Adding five more 44pt controls squeezes the wordmark from 86
    /// columns down to 48 and finally clips the mascot at x=0 — the controls
    /// themselves stay politely on screen throughout, so nothing aimed at the
    /// right-hand edge can catch any of it.
    ///
    /// So each assertion below is aimed at a different, measured failure, and none
    /// of them is satisfied by one control impersonating another:
    ///
    /// * neither edge is clipped, which is the one shape an overflow can take;
    /// * the wordmark still occupies the space between the mascot and the controls
    ///   — this is what actually breaks first, and it breaks silently;
    /// * all three right-hand controls are drawn, as three separate clusters of lit
    ///   columns. This is the one that fails when the picker leaves.
    ///
    /// Mutation: delete `languagePicker` from the body (three clusters become two);
    /// or add four more `muteControl`s (the wordmark collapses to a sliver).
    @Test("the header fits the mascot, the wordmark and all three controls at 375pt")
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
        // controls. Three 44pt-ish controls and their gaps start at roughly x=195
        // in a healthy layout, so the band stops short of that rather than at the
        // 200 it could use when there were only two controls.
        let wordmark = litColumnCount(bitmap, threshold: 150, in: 80..<190)
        #expect(
            wordmark >= Self.wordmarkFloor,
            "the wordmark occupies only \(wordmark) of the 110 columns between the mascot and the controls — it has been squeezed to a sliver"
        )

        // Right of the wordmark there must be three things: the gate button, the
        // mute button and the language picker.
        let controls = columns.filter { $0.start >= 190 }
        #expect(
            controls.count >= 3,
            "only \(controls.count) control cluster(s) right of x=190: \(controls) — the gate button, the mute button and the language picker are not all on screen"
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

    /// The worst case for the width budget: the longer of the two wordmarks, and
    /// every control present. `Cloudculator` is three glyphs longer than
    /// `Cloudmoji` and the ⚙️ is 44pt of new fixed width plus an 8pt gap, so if the
    /// strip survives this it survives everything.
    ///
    /// The assertions are the same three the two-control test above uses, and for
    /// the same measured reason: nothing here is ever clipped, so a test aimed at
    /// the right-hand edge catches nothing — with a control deleted the remaining
    /// ones simply slide right and light the band it was watching. What actually
    /// degrades is the wordmark, silently, and what actually disappears is one of
    /// the three clusters.
    ///
    /// Mutation: delete `parentControl` from the body — three clusters become two,
    /// and Settings is unreachable exactly the way mute was unreachable for the
    /// whole of stage 2a. Run and confirmed failing.
    ///
    /// The brief proposed watching the last 60pt of the strip instead, and
    /// predicted that removing `.minimumScaleFactor` would push the picker off the
    /// edge. Both were tried. Nothing is ever clipped by an extra control — the
    /// others just slide right and light whatever band is being watched — and
    /// removing `.minimumScaleFactor` changes nothing this test can see, because
    /// `lineLimit(1)` truncates the wordmark on its own.
    @Test("the header keeps every control and its wordmark with the gate button added")
    func headerSurvivesTheWorstCase() async {
        let width = 375
        let widest = ModeHeader(
            mood: .happy, title: "Cloudculator", subtitle: "🧮 Let's count!", onParent: {}
        )
        .environment(makeModel())
        .frame(width: CGFloat(width))

        let bitmap = await Bitmap.of(widest, width: CGFloat(width), height: 90)
        let columns = litColumns(bitmap, threshold: 150)
        #expect(!columns.isEmpty, "the header drew nothing at all")
        guard let first = columns.first, let last = columns.last else { return }

        let padding = Int(ModeHeaderMetrics.horizontalPadding)
        #expect(
            first.start >= 8,
            "content starts at x=\(first.start) — the mascot is clipped, so the strip overflowed"
        )
        #expect(
            last.end - 1 <= width - padding,
            "content reaches x=\(last.end - 1), past the \(padding)pt inset — the strip overflowed"
        )

        // Three controls at 44 + 8 + 44 + 8 + ~62, inset 14, leaves the wordmark
        // the band between the 14 + 64 mascot and roughly x=195.
        let wordmark = litColumnCount(bitmap, threshold: 150, in: 80..<190)
        #expect(
            wordmark >= Self.wordmarkFloor,
            "the wordmark occupies only \(wordmark) of the 110 columns between the mascot and the controls — it has been squeezed to a sliver"
        )

        let controls = columns.filter { $0.start >= 190 }
        #expect(
            controls.count >= 3,
            "only \(controls.count) control cluster(s) right of x=190: \(controls) — the gate button, the mute button and the language picker are not all three on screen"
        )
    }

    /// Measured on this simulator, not guessed: "Cloudmoji" draws 86 of the 110
    /// columns in its band and "Cloudculator" 88. Crowding the strip with five
    /// more 44pt controls takes both to 48. 65 sits between the two with room on
    /// either side.
    ///
    /// Deleting `.minimumScaleFactor(0.8)` does **not** move this number and does
    /// not fail either test — `lineLimit(1)` truncates on its own, so the strip
    /// still lays out and the wordmark still fills its band with a shorter string.
    /// That mutation was tried and survived; it is recorded here so the next
    /// person does not re-derive it from the comment that used to claim otherwise.
    private static let wordmarkFloor = 65

    /// The language toggle is parent chrome too, and it is the control that has
    /// already shipped here under the floor once: the menu picker it replaced laid
    /// out at 62 × 34 while the comment beside it said 44.
    ///
    /// Measured off a real render, not off `ModeHeaderMetrics`. The failing
    /// mutation is not "delete the frame" — the width would survive on the text's
    /// own intrinsic size — it is the height.
    ///
    /// Mutation: delete `.frame(width:height:)` from `LanguageToggle`. The button
    /// collapses to the label's own ~19pt box and the height assertion fails. Run
    /// and confirmed failing.
    @Test("the language toggle is at least 44pt on both axes")
    func languageToggleMeetsTheParentChromeMinimum() {
        let toggle = LanguageToggle(
            // The widest of the five labels — if the box holds this it holds them
            // all, and if the box were intrinsic this is where it would burst.
            label: "日本語",
            voiceOverLabel: "Language: Japanese",
            voiceOverValue: "日本語",
            voiceOverHint: "Switches to Tagalog",
            isEnabled: true,
            action: {}
        )
        let bitmap = Bitmap.rendered(toggle)
        #expect(bitmap.width >= 44, "the toggle is \(bitmap.width)pt wide")
        #expect(bitmap.height >= 44, "the toggle is \(bitmap.height)pt tall")
        // And it stays inside the width the strip budgeted for it, or the wordmark
        // pays for the overrun.
        #expect(
            bitmap.width <= Int(ModeHeaderMetrics.languageControlWidth),
            "the toggle is \(bitmap.width)pt wide, past the \(Int(ModeHeaderMetrics.languageControlWidth))pt the header budgeted"
        )
    }

    /// The header has to *read* the current language, not just own a button.
    ///
    /// Asserted on the pixels inside the toggle's own band rather than on a
    /// string, because the string would be `meta.short` compared against
    /// `meta.short` — a value asserted against its own definition. "EN" and "中文"
    /// are two Latin letters against two CJK glyphs; they cannot draw the same
    /// ink.
    ///
    /// The band is the last 62pt inside the 14pt inset, which is where the fixed
    /// `languageControlWidth` puts it. The 12%-white border falls under the 150
    /// threshold, so what is being counted is the label and nothing else.
    ///
    /// Mutation: hardcode `label:` to `"EN"` in `languageToggle`. Both renders
    /// light the same pixels. Run and confirmed failing.
    @Test("the toggle shows the language that is actually selected")
    func toggleShowsTheCurrentLanguage() async {
        let width = 375
        let band = (width - Int(ModeHeaderMetrics.horizontalPadding)
                    - Int(ModeHeaderMetrics.languageControlWidth))..<(width - Int(ModeHeaderMetrics.horizontalPadding))

        func labelInk(_ language: Language) async -> Int {
            let model = makeModel()
            model.settings.language = language
            let bitmap = await Bitmap.of(
                header(model).frame(width: CGFloat(width)), width: CGFloat(width), height: 90
            )
            return litColumnCount(bitmap, threshold: 150, in: band)
        }

        let english = await labelInk(.en)
        let chinese = await labelInk(.zh)
        #expect(english > 0, "nothing was drawn in the toggle's band at all")
        #expect(
            english != chinese,
            "EN and 中文 both lit \(english) columns in the toggle's band — the label is not reading the language"
        )
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
