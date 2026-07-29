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
    /// The child's phone sent an emoji.
    var onMessage: ((RadioMessage) -> Void)?
    /// The phone pushed new language/mute state.
    var onContext: ((RadioContext) -> Void)?

    /// Starts the session and applies whatever state iOS already had queued.
    ///
    /// `receivedApplicationContext` is the last context the phone pushed, held by
    /// the system and readable the instant the session activates — which is why
    /// the watch opens already in sync without asking. The `hello` afterwards is
    /// a best-effort nudge for the phone to re-push if the parent has the phone
    /// in hand right now; it is not relied upon.
    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    func send(_ payload: [String: String]) {
        let session = WCSession.default
        guard session.activationState == .activated else { return }
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
        guard session.activationState == .activated else { return }
        session.transferFile(url, metadata: ["kind": "voice"])
    }

    private func deliverStoredContext() {
        let stored = WCSession.default.receivedApplicationContext
        if let context = RadioContext(payload: stored) {
            onContext?(context)
        }
    }

    private func sayHello() {
        let session = WCSession.default
        guard session.activationState == .activated else { return }
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
            self.deliverStoredContext()
            self.sayHello()
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        guard let decoded = RadioMessage(payload: message) else { return }
        Task { @MainActor in self.onMessage?(decoded) }
    }

    nonisolated func session(_ session: WCSession, didReceiveApplicationContext context: [String: Any]) {
        guard let decoded = RadioContext(payload: context) else { return }
        Task { @MainActor in self.onContext?(decoded) }
    }
}
