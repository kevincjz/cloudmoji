import Observation
import SwiftUI
import WatchConnectivity
import CloudmojiCore

/// What `WatchLink` needs a session to do.
///
/// A seam, exactly like `ToneEngineDriving` is for `AudioDirector`: the real
/// implementation is `WCSessionTransport`, and `WatchLinkTests` swaps in a fake
/// so the send/receive rules can be checked without a live watch. Everything
/// crossing this boundary is `Sendable` — `[String: String]` payloads and the
/// decoded `RadioMessage` — which is what keeps the WCSession delegate's
/// off-main callbacks clear of Swift 6's isolation wall.
@MainActor
protocol RadioTransporting: AnyObject {
    /// False on a device with no watch pairing capability — an iPad. Guarding on
    /// it means the whole feature is a no-op there rather than a crash.
    var isSupported: Bool { get }
    var onMessage: ((RadioMessage) -> Void)? { get set }
    /// A voice clip arrived, already read into memory at the delegate boundary.
    var onVoice: ((Data) -> Void)? { get set }
    /// The watch asked us to re-send state (its "hello" on launch).
    var onHello: (() -> Void)? { get set }
    func activate()
    func send(_ payload: [String: String])
    func updateContext(_ payload: [String: String])
}

/// The phone end of the wrist link, owned by `AppModel`.
///
/// Outgoing: a child's tap in Words mode becomes a `.toWatch` message so the
/// parent's wrist buzzes with what Cloud is playing with. Incoming: an emoji the
/// parent sends from the watch is published on `incoming` for `RootContent` to
/// flash and speak.
@MainActor
@Observable
final class WatchLink {
    /// The most recent emoji the watch sent. Carries a token so that the parent
    /// sending the same glyph twice re-fires the view's `.onChange`.
    struct Echo: Equatable, Identifiable {
        let id: Int
        let message: RadioMessage
    }
    private(set) var incoming: Echo?

    /// A voice message the watch sent, published for `RootContent` to play.
    /// Carries the bytes and a token so a fresh clip re-fires `.onChange` even if
    /// the audio happens to be identical.
    struct VoiceDrop: Equatable, Identifiable {
        let id: Int
        let data: Data
    }
    private(set) var incomingVoice: VoiceDrop?

    /// How an incoming watch emoji should be presented. Pure and static so the
    /// bedtime and mute rules are unit-tested without any SwiftUI.
    enum EchoPresentation {
        /// Flash the bubble and say the word.
        case bubbleAndSpeech
        /// Flash the bubble but stay silent (muted).
        case bubbleOnly
        /// Nothing at all (Sleepy Cloud — a bedtime screen must not light up).
        case suppressed
    }

    /// Sleepy Cloud outranks everything: it is silent and dim by design, and a
    /// parent's stray tap must not break that. Otherwise a muted phone still
    /// shows the emoji — the visual is the point — but says nothing.
    static func presentation(active: MiniApp?, muted: Bool) -> EchoPresentation {
        if active == .sleepy { return .suppressed }
        return muted ? .bubbleOnly : .bubbleAndSpeech
    }

    private let transport: any RadioTransporting
    private let settings: SettingsStore
    private var token = 0
    private var voiceToken = 0

    init(settings: SettingsStore, transport: (any RadioTransporting)? = nil) {
        self.settings = settings
        self.transport = transport ?? WCSessionTransport()
        self.transport.onMessage = { [weak self] message in self?.receive(message) }
        self.transport.onVoice = { [weak self] data in self?.receiveVoice(data) }
        self.transport.onHello = { [weak self] in self?.pushContext() }
    }

    /// Brings the connection up and tells the watch our current state. A no-op
    /// where WatchConnectivity is unsupported (iPad).
    func activate() {
        guard transport.isSupported else { return }
        transport.activate()
        pushContext()
    }

    /// A child tapped `glyph` in Words mode — mirror it to the wrist, and refresh
    /// the watch's state while we are at it.
    func childTapped(_ glyph: String) {
        guard transport.isSupported else { return }
        transport.send(
            RadioMessage(emoji: glyph, direction: .toWatch, language: settings.language).payload
        )
        pushContext()
    }

    /// Push the parent's language and mute to the watch as the application
    /// context — the channel that survives the watch app being closed.
    func pushContext() {
        guard transport.isSupported else { return }
        transport.updateContext(
            RadioContext(language: settings.language, muted: settings.muted).payload
        )
    }

    private func receive(_ message: RadioMessage) {
        // Drop our own echo: a `.toWatch` message coming back would loop.
        guard message.direction == .toPhone else { return }
        token += 1
        incoming = Echo(id: token, message: message)
    }

    private func receiveVoice(_ data: Data) {
        guard !data.isEmpty else { return }
        voiceToken += 1
        incomingVoice = VoiceDrop(id: voiceToken, data: data)
    }
}

/// The real `WCSession` behind `WatchLink`.
///
/// The delegate methods land on a background queue, so each payload is decoded
/// **inside** the nonisolated method and only the resulting `Sendable` value
/// hops to the main actor — the same discipline `SystemSpeechEngine`'s delegate
/// uses. There is deliberately no `replyHandler` anywhere: its closure is not
/// `Sendable` and would not survive the hop; the application context (persisted,
/// delivered even when the watch app is closed) makes replies unnecessary.
@MainActor
final class WCSessionTransport: NSObject, RadioTransporting {
    var onMessage: ((RadioMessage) -> Void)?
    var onVoice: ((Data) -> Void)?
    var onHello: (() -> Void)?

    /// The latest context, held until the session is activated.
    ///
    /// `activate()` is asynchronous — `updateApplicationContext` called in the
    /// same breath after it fails with `sessionNotActivated`, and the *first*
    /// context (the one that puts the watch in sync on launch) is exactly the one
    /// pushed then. So the newest payload is buffered and flushed from
    /// `activationDidCompleteWith`. Latest-wins is already the channel's
    /// semantics, so holding only the most recent loses nothing.
    private var pendingContext: [String: String]?

    var isSupported: Bool { WCSession.isSupported() }

    private var isActivated: Bool {
        WCSession.default.activationState == .activated
    }

    func activate() {
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    func send(_ payload: [String: String]) {
        guard isActivated else { return }
        // Fire and forget: an unreachable or watchless phone is a silent no-op,
        // never an error surfaced to a child (`CLAUDE.md` rule 4).
        WCSession.default.sendMessage(payload, replyHandler: nil, errorHandler: nil)
    }

    func updateContext(_ payload: [String: String]) {
        guard isActivated else {
            pendingContext = payload
            return
        }
        try? WCSession.default.updateApplicationContext(payload)
    }

    /// Flush a context that arrived before the session was ready.
    fileprivate func flushPendingContext() {
        guard isActivated, let pending = pendingContext else { return }
        pendingContext = nil
        try? WCSession.default.updateApplicationContext(pending)
    }
}

extension WCSessionTransport: WCSessionDelegate {
    nonisolated func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        guard activationState == .activated else { return }
        // Send whatever context was queued before the session was ready — the
        // launch push that puts the watch in sync.
        Task { @MainActor in self.flushPendingContext() }
    }

    // A phone can pair with more than one watch over its life; when the active
    // one changes, the session deactivates and must be re-activated to follow.
    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}
    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        // The watch's launch "hello" is not an emoji — treat it as "re-send state".
        if message["kind"] as? String == "hello" {
            Task { @MainActor in self.onHello?() }
            return
        }
        guard let decoded = RadioMessage(payload: message) else { return }
        Task { @MainActor in self.onMessage?(decoded) }
    }

    /// A voice clip arrived.
    ///
    /// `file.fileURL` is only valid for the duration of this call, so the bytes
    /// are read **here**, on the delegate's queue, and only the `Sendable` `Data`
    /// crosses to the main actor. The delivered file is deleted right after — the
    /// clip lives in memory from this point on, never in our storage.
    nonisolated func session(_ session: WCSession, didReceive file: WCSessionFile) {
        guard file.metadata?["kind"] as? String == "voice",
              let data = try? Data(contentsOf: file.fileURL) else { return }
        try? FileManager.default.removeItem(at: file.fileURL)
        Task { @MainActor in self.onVoice?(data) }
    }
}
