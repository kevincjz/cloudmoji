import SwiftUI
import Testing
import UIKit
@testable import Cloudmoji

/// Reports the environment value as a width, so a test can read the layout
/// decision off the pixels rather than off the constant that made it.
///
/// Two obvious alternatives were rejected. Asserting
/// `EnvironmentValues().cloudmojiIsCompact == false` tests the key's default and
/// nothing about the shell. And a probe that writes to a `@State` from `body`
/// tests that SwiftUI evaluated a body, not that the value reached a child.
private struct CompactProbe: View {
    static let compactWidth: CGFloat = 100
    static let roomyWidth: CGFloat = 220

    @Environment(\.cloudmojiIsCompact) private var isCompact

    var body: some View {
        Rectangle()
            .fill(.white)
            .frame(width: isCompact ? Self.compactWidth : Self.roomyWidth, height: 20)
    }
}

/// `AdaptiveShell` makes exactly one decision, and every screen in the app is
/// arranged from it. Getting it wrong does not crash or fail to build — it hands
/// a phone in landscape the portrait layout, which is the state the web app
/// shipped in for weeks.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("AdaptiveShell")
@MainActor
struct AdaptiveShellTests {

    /// White on the dark gradient. The background's brightest stop sums to about
    /// 114; white sums to 765.
    static let whiteThreshold = 400

    /// Whether the shell told its content the screen is compact, measured off
    /// the width of the bar the content then drew.
    ///
    /// Reads once.
    ///
    /// This used to read twice, because captures across suites raced for the key
    /// window and roughly one full-target run in twenty came back as a width
    /// belonging to neither layout. That race is fixed at the source now —
    /// `Bitmap.of` serialises through `CaptureGate` and `EmojiGridTests` no
    /// longer builds windows of its own — so a retry here would only hide a
    /// regression of that fix.
    func isCompact(
        width: CGFloat,
        height: CGFloat,
        isPad: Bool = false
    ) async throws -> Bool {
        let answer = await readProbe(width: width, height: height, isPad: isPad)
        return try #require(answer, "the probe drew neither layout at \(width)×\(height)")
    }

    /// `nil` when the render is not legible as either layout.
    private func readProbe(width: CGFloat, height: CGFloat, isPad: Bool) async -> Bool? {
        let bitmap = await Bitmap.of(
            AdaptiveShell(isPad: isPad) { CompactProbe() },
            width: width, height: height, fillsWindow: true
        )
        let runs = bitmap.runs(y: Int(height) / 2, threshold: Self.whiteThreshold)
        guard runs.count == 1, let bar = runs.first else { return nil }
        if abs(CGFloat(bar.width) - CompactProbe.compactWidth) <= 4 { return true }
        if abs(CGFloat(bar.width) - CompactProbe.roomyWidth) <= 4 { return false }
        return nil
    }

    /// The two orientations of every phone this ships to, and both iPad
    /// orientations. The iPad landscape row is the one the naive
    /// "width > height" rule gets wrong: it has 768pt of height and should keep
    /// the roomy layout.
    @Test("a phone held sideways is compact; a phone upright and an iPad are not")
    func realDevicesGetTheRightLayout() async throws {
        #expect(try await isCompact(width: 956, height: 440))   // iPhone 17 Pro Max landscape
        #expect(try await isCompact(width: 667, height: 375))   // iPhone SE landscape
        #expect(try await !isCompact(width: 440, height: 956))  // iPhone 17 Pro Max portrait
        #expect(try await !isCompact(width: 375, height: 667))  // iPhone SE portrait
        #expect(try await !isCompact(width: 1024, height: 768, isPad: true)) // iPad landscape
        #expect(try await !isCompact(width: 768, height: 1024, isPad: true)) // iPad portrait
    }

    /// Both halves of the condition are load-bearing, and each is invisible to
    /// the other's cases. A short *portrait* window — a Split View slice on an
    /// iPad, or a phone with the keyboard up — is short but not wide, and must
    /// not be handed the landscape rail.
    @Test("a short but narrow window is not compact")
    func heightAloneIsNotEnough() async throws {
        #expect(try await !isCompact(width: 300, height: 400))
    }

    /// The 560pt threshold, asserted as a literal on both sides of the line.
    /// Reading `AdaptiveShell.compactHeight` back here instead would pass
    /// against any number, including one that made every device compact.
    @Test("the threshold is 560pt, inclusive")
    func thresholdIsWhereItSays() async throws {
        #expect(try await isCompact(width: 900, height: 560))
        #expect(try await !isCompact(width: 900, height: 561))
    }

    /// Nothing is compact until the shell says so — this is what pins the
    /// decision to `AdaptiveShell` rather than to the environment key's default,
    /// which would otherwise satisfy half the assertions above on its own.
    @Test("content outside the shell defaults to the roomy layout")
    func defaultIsNotCompact() async throws {
        let bitmap = await Bitmap.of(CompactProbe(), width: 900, height: 400)
        let runs = bitmap.runs(y: 10, threshold: Self.whiteThreshold)
        let bar = try #require(runs.first)
        #expect(abs(CGFloat(bar.width) - CompactProbe.roomyWidth) <= 2,
                "an unwrapped probe drew a \(bar.width)pt bar")
    }

    /// The shell owns the background, and it has to reach the corners — the
    /// window under it is black, so a missing background is a black screen with
    /// the app floating on it. `ignoresSafeArea` cannot be measured here (the
    /// snapshot host has no insets), which is what the simulator pass is for.
    @Test("the shell paints the app background behind its content")
    func shellPaintsTheBackground() async {
        let bitmap = await Bitmap.of(
            AdaptiveShell { Color.clear }, width: 400, height: 800, fillsWindow: true
        )
        let top = bitmap.rgb(x: 4, y: 4)
        let bottom = bitmap.rgb(x: 4, y: 795)
        #expect(top.sum > 0, "the top of the shell is pure black — no background drew")
        #expect(bottom.sum > 0, "the bottom of the shell is pure black — no background drew")
        // The gradient runs #0F0E2A → #1A1145 → #0D2137: blue-dominant at both
        // ends, and never the flat fill a single colour would give.
        #expect(top.b > top.r)
        #expect(bottom.b > bottom.r)
        #expect(top != bottom, "the background is flat — the gradient stops collapsed")
    }
}
