import SwiftUI
import CloudmojiCore

/// Music 🎹 — eight pads, one note each.
///
/// The simplest possible instrument, and deliberately so: no scales to choose, no
/// octave control, no recording. Eight coloured squares that make a sound when
/// touched, arranged so a two-year-old can hit them with the flat of his hand.
/// The notes are a C-major pentatonic run, which is what makes every combination
/// sound intentional — see `ToneBuffer.pitches`.
///
/// Silent in one respect only: it does not speak. There is no word to say about a
/// note, and the mute button governs speech and sounds alike, so a muted phone
/// answers a pad with the haptic and the colour and nothing else.
struct InstrumentPadView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact
    @Environment(\.cloudmojiLayout) private var layout

    /// Two across upright, four across sideways — the transpose of each other,
    /// so the same eight pads fill whichever axis there is more of.
    static func columns(compact: Bool) -> Int { compact ? 4 : 2 }

    static func columns(compact: Bool, expandedPad: Bool, landscape: Bool) -> Int {
        compact || (expandedPad && landscape) ? 4 : 2
    }

    var body: some View {
        GeometryReader { proxy in
            let columns = Self.columns(
                compact: isCompact,
                expandedPad: layout.isExpandedPad,
                landscape: layout.isLandscape
            )
            let rows = (ToneBuffer.pitches.count + columns - 1) / columns
            let side = padSide(in: proxy.size, columns: columns, rows: rows)

            VStack(spacing: InstrumentPadMetrics.spacing) {
                ForEach(0..<rows, id: \.self) { row in
                    HStack(spacing: InstrumentPadMetrics.spacing) {
                        ForEach(0..<columns, id: \.self) { column in
                            let index = row * columns + column
                            if index < ToneBuffer.pitches.count {
                                InstrumentPad(index: index, side: side) { strike(index) }
                            }
                        }
                    }
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .padding(InstrumentPadMetrics.spacing)
        .onAppear { model.audio.attach(.instrument) }
        // Belt and braces with `goHome`, which also detaches: `.onDisappear` is
        // the one that fires when the app is torn down some other way, and a
        // running engine outliving the screen is what makes a phone hum.
        .onDisappear { model.audio.detach() }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("instrument-panel")
    }

    /// The largest square that fits the grid, never smaller than the 72pt
    /// preferred child target.
    ///
    /// The floor is not a suggestion. On the shortest screen the arithmetic
    /// wants about 68pt, and letting it have that would put a child-facing
    /// control under the size `CLAUDE.md` rule 1 sets — so the pads overflow
    /// their box by a couple of points instead, which nobody can see.
    static func side(available: CGSize, columns: Int, rows: Int, spacing: CGFloat) -> CGFloat {
        guard columns > 0, rows > 0 else { return InstrumentPadMetrics.minimumSide }
        let width = (available.width - spacing * CGFloat(columns - 1)) / CGFloat(columns)
        let height = (available.height - spacing * CGFloat(rows - 1)) / CGFloat(rows)
        return max(InstrumentPadMetrics.minimumSide, min(width, height))
    }

    private func padSide(in size: CGSize, columns: Int, rows: Int) -> CGFloat {
        let fitted = Self.side(
            available: size,
            columns: columns,
            rows: rows,
            spacing: InstrumentPadMetrics.spacing
        )
        return layout.isExpandedPad
            ? min(fitted, InstrumentPadMetrics.maximumPadSide)
            : fitted
    }

    private func strike(_ index: Int) {
        // Muting silences the phone; it does not mean "stop responding to me".
        // The haptic and the colour still answer — `Haptics` documents the same
        // rule, and the pad fires its own buzz before this is reached.
        guard !model.settings.muted else { return }
        model.audio.playTone(index)
    }
}

#Preview("Instrument pad") {
    AdaptiveShell { InstrumentPadView() }
        .environment(AppModel())
}
