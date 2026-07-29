import SwiftUI

/// The emoji a parent sends from their Apple Watch, shown to the child.
///
/// A centred plate rather than the floating `WordBubble`: this is not something
/// the child tapped, it is a hello from across the room, so it wants to read as
/// an arrival — the cloud's teal ring, the emoji, and the word. `RootContent`
/// presents it as a hit-testing-disabled overlay and clears it after
/// `WordBubbleMetrics.lifetime`.
struct WatchEchoBubble: View {
    let emoji: String
    /// The current-language word, or `nil` when the glyph is not in the
    /// catalogue — then the emoji stands alone rather than a blank line showing.
    let word: String?

    var body: some View {
        VStack(spacing: 10) {
            Text(emoji)
                .font(.system(size: 88))
            if let word, !word.isEmpty {
                Text(word)
                    .font(Theme.body(22, .black))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 26)
        .background(
            Theme.bgPrimary.opacity(0.9),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(Theme.teal.opacity(0.5), lineWidth: 2)
        )
        .shadow(color: Theme.teal.opacity(0.25), radius: 24, y: 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(word ?? emoji)
        .accessibilityIdentifier("watch-echo-bubble")
    }
}

#Preview("Watch echo") {
    ZStack {
        Theme.background.ignoresSafeArea()
        WatchEchoBubble(emoji: "🐶", word: "dog")
    }
}
