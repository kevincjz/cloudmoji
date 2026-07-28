import SwiftUI
import Testing
import UIKit
@testable import Cloudmoji

@Suite("TutorialView")
@MainActor
struct TutorialViewTests {

    private var everyStep: [TutorialView.Step] { TutorialView.steps }

    private func step(_ id: String) -> TutorialView.Step? {
        everyStep.first { $0.id == id }
    }

    // MARK: - What the tour has to say

    /// The single most useful thing a new parent will not otherwise discover.
    ///
    /// `CloudmojiApp.init` sets `AVAudioSession` to `.playback`, which is a
    /// deliberate override of the hardware silent switch — so a parent who flicks
    /// the switch and hears Cloudmoji carry on has no way to know that is by
    /// design or that the in-app 🔊 is the real control. If this sentence is ever
    /// edited out, the tour has lost the one fact it exists to deliver.
    ///
    /// Mutation: delete the "silent switch" sentence from the mute step, or drop
    /// the step. Both fail here and name what went missing.
    @Test("the tour says the silent switch will not quieten the app")
    func explainsThatMuteIsInAppOnly() {
        guard let mute = step("mute") else {
            Issue.record("there is no mute step in the tour at all")
            return
        }
        let text = mute.title + "\n" + mute.detail
        #expect(text.contains("silent switch"),
                "the mute step never mentions the silent switch it is overriding")
        #expect(text.contains("🔊"),
                "the mute step never names the button a parent has to press")
    }

    /// Where Settings is, and what is behind it — the reason this whole feature
    /// was asked for. The ⚙️ glyph has to be printed, not described: a parent
    /// scanning the header is matching a shape, not reading the word "gear".
    @Test("the tour points at the gear and says what is behind it")
    func explainsWhereSettingsLives() {
        guard let settings = step("settings") else {
            Issue.record("there is no settings step in the tour at all")
            return
        }
        let text = settings.title + "\n" + settings.detail
        #expect(text.contains("⚙️"), "the settings step never shows the glyph to look for")
        // The three things `SettingsView` actually offers, so this fails if the
        // tour ever promises a panel that is not the one that ships.
        #expect(text.contains("languages"))
        #expect(text.contains("categories"))
        #expect(text.contains("Count mode"))
        // A gate a parent is not warned about reads as the app being broken.
        #expect(text.range(of: "sum", options: .caseInsensitive) != nil,
                "the settings step never warns that a question is coming")
    }

    /// The three things that are on screen but unlabelled. `TypingRow` draws
    /// exactly these three glyphs (`replay-btn` 🔊, `delete-btn` ⌫,
    /// `clear-btn` ✕), and a tour that names different ones is worse than none.
    @Test("the typing-row step names the three controls the row actually draws")
    func explainsTheTypingRow() {
        guard let row = step("typing-row") else {
            Issue.record("there is no typing-row step in the tour at all")
            return
        }
        for glyph in ["🔊", "⌫", "✕"] {
            #expect(row.detail.contains(glyph),
                    "the typing-row step does not mention \(glyph), which the row draws")
        }
    }

    /// Both modes, by the names the tab bar prints. `AppMode.label` is `Words`
    /// and `Count`; a tour calling them anything else sends a parent looking for
    /// a control that is not there.
    @Test("both modes are named exactly as the tab bar labels them")
    func namesBothModes() {
        let text = everyStep.map { $0.title + "\n" + $0.detail }.joined(separator: "\n")
        for mode in AppMode.allCases {
            #expect(text.contains(mode.label),
                    "the tour never mentions the \(mode.label) tab")
        }
    }

    /// Kids Category review's single riskiest item is a way out of the app, and
    /// this screen is shown before any gate — so the bar is at least as high as
    /// `AboutView`'s. Nothing here may read as a link.
    @Test("nothing in the tour offers to leave the app")
    func noOutboundLinks() {
        for entry in everyStep {
            let text = entry.title + "\n" + entry.detail
            for banned in ["http", "www.", "ko-fi", "App Store", "Safari"] {
                #expect(
                    text.range(of: banned, options: .caseInsensitive) == nil,
                    "\"\(entry.title)\" mentions \(banned) — the tour must not point out of the app"
                )
            }
        }
    }

    /// Identifiers are what `TutorialUITests` looks rows up by, and a duplicate
    /// would silently make one of them unreachable — `firstMatch` would keep
    /// returning the other.
    @Test("every step has a distinct, non-empty identifier and some copy")
    func stepsAreWellFormed() {
        #expect(everyStep.count >= 5, "the tour has shrunk below the five things it must cover")
        #expect(Set(everyStep.map(\.id)).count == everyStep.count, "two steps share an id")
        for entry in everyStep {
            #expect(!entry.id.isEmpty)
            #expect(!entry.glyph.isEmpty, "\"\(entry.title)\" has no glyph")
            #expect(!entry.title.isEmpty)
            #expect(entry.detail.count > 20, "\"\(entry.title)\" has no real copy under it")
        }
    }

    /// A tour is not an enterprise onboarding flow. The brief's whole framing is
    /// "genuinely short", and copy grows one well-meaning sentence at a time —
    /// this is the tripwire that makes that growth a decision rather than a
    /// drift.
    @Test("the tour stays short enough that a parent will read it")
    func staysShort() {
        let words = everyStep
            .map { $0.title + " " + $0.detail }
            .joined(separator: " ")
            .split(whereSeparator: \.isWhitespace)
            .count
        #expect(words < 260, "the tour is \(words) words — it has become documentation")
    }

    // MARK: - Layout

    /// The Got it button must be on screen without scrolling.
    ///
    /// This is the property that makes the tour dismissible by a two-year-old,
    /// and the obvious implementation loses it: putting the button at the end of
    /// the `ScrollView`'s content puts it below the fold on every phone, so the
    /// only way out of a first-run screen is a scroll a child will not perform.
    ///
    /// Measured as a run rather than a pixel count, because a whole-window count
    /// is the shape this project keeps catching: the 72pt mascot and five
    /// paragraphs of white text swamp any threshold the button could clear.
    /// Instead the same view is rendered twice — with the button and without,
    /// which is the real Settings-pushed presentation — and only the button
    /// produces a single lit run most of the window wide down at the bottom
    /// edge. Text cannot: words are separated by unlit spaces.
    ///
    /// Mutation: move the button inside the `ScrollView`. The first expectation
    /// fails, because the bottom band is then copy.
    ///
    /// Run at two sizes, both of which the copy overflows. The landscape one is
    /// not decoration: a phone can be sideways when an app is first opened, and
    /// `AdaptiveShell`'s compact layout gives about 390pt of height — less than
    /// this tour is tall, so that is the case where a button at the end of the
    /// scrolling content disappears most completely.
    @Test(
        "the Got it button is pinned on screen, not below the fold",
        arguments: [CGSize(width: 393, height: 500), CGSize(width: 852, height: 393)]
    )
    func doneButtonIsPinned(size: CGSize) async {
        let width = size.width
        // Both fixtures are deliberately shorter than the copy, which runs to
        // roughly 700pt. That is what makes a button at the end of the scrolling
        // content genuinely below the fold. At a full 852pt-tall window it is
        // not: the content happens to fit, the mutation draws a button in the
        // same place as the real code, and this test passed against it — which
        // is exactly what happened when it was first written.
        let height = size.height
        // 48pt up from the bottom: inside the 56pt button, above its 20pt bottom
        // padding, and clear of the button's rounded corners.
        let scanline = Int(height) - 48
        // Comfortably above the background gradient's ~101 and below the button
        // fill's ~176.
        let threshold = 135
        // The button spans the window less 20pt of padding either side. Two
        // thirds of that is far wider than any word of 13pt copy, and far
        // narrower than the button, so it separates the two cases without
        // asserting the button's own arithmetic back at itself.
        let wide = Int(width - 40) * 2 / 3

        let withButton = await Bitmap.of(
            TutorialView(onDone: {}), width: width, height: height, fillsWindow: true
        )
        let withoutButton = await Bitmap.of(
            TutorialView(), width: width, height: height, fillsWindow: true
        )

        #expect(
            withButton.runs(y: scanline, threshold: threshold).contains { $0.width >= wide },
            "nothing \(wide)pt wide is drawn at the bottom of the tour — Got it is not pinned"
        )
        #expect(
            !withoutButton.runs(y: scanline, threshold: threshold).contains { $0.width >= wide },
            "the pushed tour draws a button-shaped band too, so the check above is not the button"
        )
    }

    /// The row of copy has to reach the top of the sheet.
    ///
    /// Cheap, and it is the assertion that stops every copy test above from
    /// being true of a view that renders nothing at all: `Bitmap` hands back a
    /// black rectangle when the hierarchy fails to draw, and each of the string
    /// tests would pass against that unchanged.
    @Test("the tour actually draws")
    func itDraws() async {
        let bitmap = await Bitmap.of(
            TutorialView(onDone: {}), width: 393, height: 852, fillsWindow: true
        )
        // The heading sits in the top 120pt. White text on the gradient.
        let litInHeader = (0..<160).reduce(0) { total, y in
            total + bitmap.runs(y: y, threshold: 400).reduce(0) { $0 + $1.width }
        }
        #expect(litInHeader > 200, "only \(litInHeader) bright pixels in the tour's heading band")
    }
}
