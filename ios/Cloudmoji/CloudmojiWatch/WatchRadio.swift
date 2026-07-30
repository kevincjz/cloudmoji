import Foundation
import WatchConnectivity
import CloudmojiCore

/// The watch end of the wrist link.
///
/// The mirror of the phone's `WCSessionTransport`, minus the protocol seam — the
/// watch side is proven on a real device rather than unit-tested, so there is
/// nothing to fake here. The important discipline is the same: `WCSession`
/// delegate callbacks arrive on a background queue, so every payload is decoded
/// **inside** the nonisolated method and only the decoded, `Sendable`
/// `RadioMessage` / `RadioContext` hops to the main actor.
@MainActor
final class WatchRadio: NSObject {
    private static let voiceKind = "voice"
    private static let transferAttemptKey = "attempt"
    private static let maxVoiceTransferAttempts = 2

    /// The child's phone sent an emoji.
    var onMessage: ((RadioMessage) -> Void)?
    /// The phone pushed new language/mute state.
    var onContext: ((RadioContext) -> Void)?
    private var isEnabled = false

    /// Starts the session and applies whatever state iOS already had queued.
    ///
    /// `receivedApplicationContext` is the last context the phone pushed, held by
    /// the system and readable the instant the session activates — which is why
    /// the watch opens already in sync without asking. The `hello` afterwards is
    /// a best-effort nudge for the phone to re-push if the parent has the phone
    /// in hand right now; it is not relied upon.
    func activate() {
        guard WCSession.isSupported() else { return }
        isEnabled = true
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    func deactivate() {
        isEnabled = false
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        for transfer in session.outstandingFileTransfers
        where transfer.file.metadata?["kind"] as? String == Self.voiceKind {
            let url = transfer.file.fileURL
            transfer.cancel()
            discardVoice(at: url)
        }
        if session.delegate === self {
            session.delegate = nil
        }
    }

    func send(_ payload: [String: String]) {
        let session = WCSession.default
        guard isEnabled, session.activationState == .activated else { return }
        // Fire and forget — an unreachable phone is a silent no-op, not an error
        // a toddler's parent should see.
        session.sendMessage(payload, replyHandler: nil, errorHandler: nil)
    }

    /// Sends a recorded clip to the phone.
    ///
    /// `transferFile` rather than `sendMessage`: files ride a queue that survives
    /// the watch app backgrounding and reconnects, which a voice message — a few
    /// seconds of the parent's own voice — needs and a one-tap emoji does not.
    /// The `voice` metadata tells the phone what kind of file arrived.
    func sendVoice(_ url: URL) {
        let session = WCSession.default
        guard isEnabled,
              session.activationState == .activated,
              FileManager.default.isReadableFile(atPath: url.path()) else {
            discardVoice(at: url)
            return
        }
        queueVoice(at: url, attempt: 0)
    }

    private func queueVoice(at url: URL, attempt: Int) {
        WCSession.default.transferFile(
            url,
            metadata: [
                "kind": Self.voiceKind,
                Self.transferAttemptKey: attempt,
            ]
        )
    }

    /// The source file must remain readable for the full asynchronous transfer.
    /// It is discarded only after delivery succeeds or the single retry fails.
    private func finishVoiceTransfer(at url: URL, failed: Bool, attempt: Int) {
        let session = WCSession.default
        if failed,
           attempt + 1 < Self.maxVoiceTransferAttempts,
           session.activationState == .activated,
           FileManager.default.isReadableFile(atPath: url.path()) {
            queueVoice(at: url, attempt: attempt + 1)
            return
        }
        discardVoice(at: url)
    }

    private func discardVoice(at url: URL) {
        try? FileManager.default.removeItem(at: url)
    }

    private func deliverStoredContext() {
        guard isEnabled else { return }
        let stored = WCSession.default.receivedApplicationContext
        if let context = RadioContext(payload: stored) {
            onContext?(context)
        }
    }

    private func sayHello() {
        let session = WCSession.default
        guard isEnabled, session.activationState == .activated else { return }
        session.sendMessage(["kind": "hello"], replyHandler: nil, errorHandler: nil)
    }
}

extension WatchRadio: WCSessionDelegate {
    nonisolated func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        guard activationState == .activated else { return }
        Task { @MainActor in
            guard self.isEnabled else { return }
            self.deliverStoredContext()
            self.sayHello()
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        guard let decoded = RadioMessage(payload: message) else { return }
        Task { @MainActor in
            guard self.isEnabled else { return }
            self.onMessage?(decoded)
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveApplicationContext context: [String: Any]) {
        guard let decoded = RadioContext(payload: context) else { return }
        Task { @MainActor in
            guard self.isEnabled else { return }
            self.onContext?(decoded)
        }
    }

    nonisolated func session(
        _ session: WCSession,
        didFinish fileTransfer: WCSessionFileTransfer,
        error: Error?
    ) {
        // Decode inside the nonisolated callback; only Sendable values cross
        // back to the main actor.
        guard fileTransfer.file.metadata?["kind"] as? String == "voice" else { return }
        let url = fileTransfer.file.fileURL
        let attempt = fileTransfer.file.metadata?["attempt"] as? Int ?? 0
        let failed = error != nil
        Task { @MainActor in
            self.finishVoiceTransfer(at: url, failed: failed, attempt: attempt)
        }
    }
}
