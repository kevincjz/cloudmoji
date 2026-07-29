import SwiftUI

/// The little "a grown-up left you a message" control, sitting at the top of the
/// screen for as long as a voice clip is held.
///
/// It persists — unlike the emoji echo, which flashes and fades — because the
/// whole point of keeping the clip in memory is that a child can come back and
/// tap to hear Mama again. It pulses while the audio is sounding.
struct VoiceMessagePill: View {
    let isPlaying: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: isPlaying ? "speaker.wave.2.fill" : "play.circle.fill")
                    .font(.system(size: 18, weight: .black))
                    .foregroundStyle(Theme.teal)
                    .symbolEffect(.variableColor, isActive: isPlaying)
                Text("Hear the grown-up")
                    .font(Theme.body(14, .black))
                    .foregroundStyle(Theme.textPrimary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Theme.bgPrimary.opacity(0.9),
                in: Capsule(style: .continuous)
            )
            .overlay(Capsule(style: .continuous).stroke(Theme.teal.opacity(0.5), lineWidth: 2))
            .shadow(color: Theme.teal.opacity(0.22), radius: 14, y: 5)
            .scaleEffect(isPlaying ? 1.04 : 1)
            .animation(.spring(duration: 0.3), value: isPlaying)
            .contentShape(Capsule())
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
