import SwiftUI
import CloudmojiCore

/// The record screen: one big button that starts and stops, and sends.
///
/// Presented over Pocket Cloud when the parent taps the mic. Tap the circle to
/// start (it turns coral and counts up), tap again to stop and send — or it
/// stops itself at the cap. Deliberately one control, like everything else the
/// watch does, so it works with a glance and a thumb.
struct RecordView: View {
    @Environment(WatchModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var recorder = VoiceRecorder()

    var body: some View {
        VStack(spacing: 10) {
            Text(caption)
                .font(.system(.footnote, design: .rounded).weight(.bold))
                .foregroundStyle(.white.opacity(0.8))
                .multilineTextAlignment(.center)

            Button(action: toggle) {
                ZStack {
                    Circle()
                        .fill(recorder.isRecording ? Color(red: 1, green: 0.42, blue: 0.42) : WatchTheme.teal)
                        .frame(width: 92, height: 92)
                    Image(systemName: recorder.isRecording ? "stop.fill" : "mic.fill")
                        .font(.system(size: 34, weight: .black))
                        .foregroundStyle(.white)
                }
            }
            .buttonStyle(.plain)
            .disabled(recorder.state == .denied)

            if case .recording(let elapsed) = recorder.state {
                Text(elapsedText(elapsed))
                    .font(.system(.caption, design: .rounded).weight(.heavy).monospacedDigit())
                    .foregroundStyle(WatchTheme.teal)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(WatchTheme.background.ignoresSafeArea())
        .onAppear {
            recorder.onFinished = { url in
                model.sendVoice(url)
                dismiss()
            }
        }
        .onChange(of: model.entitlements.isUnlocked) { _, isFull in
            guard !isFull else { return }
            recorder.cancel()
            dismiss()
        }
        // Leaving mid-recording must not send a partial clip or strand the mic.
        .onDisappear { recorder.cancel() }
    }

    private func toggle() {
        guard model.entitlements.isUnlocked else { return }
        if recorder.isRecording {
            recorder.stop()
        } else {
            recorder.start()
        }
    }

    private var caption: String {
        switch recorder.state {
        case .idle: "Hold the watch up and tap to talk to Cloud"
        case .recording: "Tap to send"
        case .denied: "Turn on the microphone for Cloudmoji in the watch's Settings to send your voice."
        }
    }

    private func elapsedText(_ elapsed: TimeInterval) -> String {
        let remaining = max(0, Int(VoiceRecorder.maxDuration - elapsed))
        return "\(Int(elapsed))s · \(remaining)s left"
    }
}
