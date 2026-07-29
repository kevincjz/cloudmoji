import AVFoundation
import Observation

/// Records a short voice message on the watch.
///
/// The only microphone in Cloudmoji, and it lives here on purpose: the parent's
/// own wrist, one way, watch → phone. The iPhone app never records. A clip is a
/// temporary file, transferred and then deleted — nothing is kept on the watch.
///
/// Bounded to `maxDuration` so a clip stays small enough to transfer promptly and
/// a parent cannot accidentally leave it running.
@MainActor
@Observable
final class VoiceRecorder {
    enum State: Equatable {
        case idle
        case denied
        case recording(elapsed: TimeInterval)
    }

    private(set) var state: State = .idle

    /// Long enough for "Dinner's ready, come and wash your hands!", short enough
    /// to keep the file tiny and the transfer quick.
    static let maxDuration: TimeInterval = 15

    private var recorder: AVAudioRecorder?
    private var startedAt: Date?
    private var tick: Task<Void, Never>?

    /// Handed the finished clip's URL. The owner transfers it and is then
    /// responsible for cleaning it up.
    var onFinished: ((URL) -> Void)?

    var isRecording: Bool {
        if case .recording = state { return true }
        return false
    }

    /// Asks once, then starts. A refusal parks the recorder in `.denied` — the
    /// UI shows a gentle explanation rather than a dead button.
    func start() {
        guard !isRecording else { return }
        Task {
            let granted = await Self.requestPermission()
            guard granted else { state = .denied; return }
            beginRecording()
        }
    }

    /// Stops and hands the clip over. A no-op if not recording.
    func stop() {
        guard isRecording else { return }
        tick?.cancel()
        tick = nil
        recorder?.stop()
        let url = recorder?.url
        recorder = nil
        startedAt = nil
        state = .idle
        // `.record` grabbed the session; give it back so speech can play again.
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        if let url { onFinished?(url) }
    }

    private func beginRecording() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, options: [.duckOthers])
            try session.setActive(true)
        } catch {
            state = .denied
            return
        }

        let url = FileManager.default.temporaryDirectory
            .appending(path: "cloudmoji-voice.m4a")
        try? FileManager.default.removeItem(at: url)

        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 22_050,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
        ]

        guard let recorder = try? AVAudioRecorder(url: url, settings: settings) else {
            state = .denied
            return
        }
        self.recorder = recorder
        recorder.record(forDuration: Self.maxDuration)
        startedAt = Date()
        state = .recording(elapsed: 0)

        // Drive the elapsed readout, and stop cleanly when the cap is hit.
        tick = Task {
            while !Task.isCancelled, let started = startedAt {
                let elapsed = Date().timeIntervalSince(started)
                if elapsed >= Self.maxDuration { stop(); return }
                state = .recording(elapsed: elapsed)
                try? await Task.sleep(for: .milliseconds(200))
            }
        }
    }

    private static func requestPermission() async -> Bool {
        switch AVAudioApplication.shared.recordPermission {
        case .granted: return true
        case .denied: return false
        case .undetermined:
            return await withCheckedContinuation { continuation in
                AVAudioApplication.requestRecordPermission { granted in
                    continuation.resume(returning: granted)
                }
            }
        @unknown default: return false
        }
    }
}
