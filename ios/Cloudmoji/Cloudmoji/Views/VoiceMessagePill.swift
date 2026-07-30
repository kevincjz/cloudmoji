import SwiftUI

/// The "a grown-up left you a message" widget shown below the launcher icons.
///
/// It persists — unlike the emoji echo, which flashes and fades — because the
/// whole point of keeping the clip in memory is that a child can come back and
/// tap to hear Mama again. Its broad card shape follows the launcher’s iOS Home
/// Screen metaphor without covering the Cloudmoji brand header.
struct VoiceMessagePill: View {
    @Environment(\.cloudmojiIsCompact) private var isCompact
    @Environment(\.cloudmojiLayout) private var layout

    let isPlaying: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: layout.isExpandedPad ? 16 : 13) {
                ZStack {
                    Circle()
                        .fill(Theme.teal.opacity(0.18))
                    Image(systemName: isPlaying ? "speaker.wave.2.fill" : "play.fill")
                        .font(.system(
                            size: layout.isExpandedPad ? 22 : 18,
                            weight: .black
                        ))
                        .foregroundStyle(Theme.teal)
                        .symbolEffect(.variableColor, isActive: isPlaying)
                }
                .frame(
                    width: layout.isExpandedPad ? 54 : 46,
                    height: layout.isExpandedPad ? 54 : 46
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text("Hear the grown-up")
                        .font(Theme.body(layout.isExpandedPad ? 17 : 15, .black))
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(1)
                    if !isCompact {
                        Text(isPlaying ? "Playing your voice message" : "Tap to replay the voice message")
                            .font(Theme.body(layout.isExpandedPad ? 12 : 10, .heavy))
                            .foregroundStyle(Theme.textTertiary)
                            .lineLimit(1)
                    }
                }

                Spacer(minLength: 8)

                Image(systemName: "waveform")
                    .font(.system(
                        size: layout.isExpandedPad ? 25 : 21,
                        weight: .bold
                    ))
                    .foregroundStyle(Theme.coral.opacity(isPlaying ? 1 : 0.72))
                    .symbolEffect(.variableColor.iterative, isActive: isPlaying)
            }
            .padding(.horizontal, layout.isExpandedPad ? 20 : 16)
            .padding(.vertical, layout.isExpandedPad ? 14 : 12)
            .frame(maxWidth: .infinity, minHeight: layout.isExpandedPad ? 82 : 70)
            .background(
                Theme.headerPlate,
                in: RoundedRectangle(
                    cornerRadius: layout.isExpandedPad ? 26 : 22,
                    style: .continuous
                )
            )
            .overlay(
                RoundedRectangle(
                    cornerRadius: layout.isExpandedPad ? 26 : 22,
                    style: .continuous
                )
                    .stroke(Theme.teal.opacity(0.42), lineWidth: 1)
            )
            .shadow(color: Theme.teal.opacity(0.22), radius: 14, y: 5)
            .scaleEffect(isPlaying ? 1.015 : 1)
            .animation(.spring(duration: 0.3), value: isPlaying)
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(PressScale(scale: 0.92))
        .accessibilityLabel("Play the grown-up's message")
        .accessibilityIdentifier("voice-message-pill")
    }
}

#Preview("Voice pill") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 30) {
            VoiceMessagePill(isPlaying: false) {}
            VoiceMessagePill(isPlaying: true) {}
        }
    }
}
