import AVFoundation

/// Binds `AVSpeechSynthesizer` to the queue protocol `CloudmojiCore` defines.
///
/// Lives in the package rather than the app target because both the iOS app and
/// the watchOS companion drive it — it imports only AVFoundation, which is
/// watchOS-clean, so nothing here is platform-specific.
///
/// The synthesiser's delegate methods are not main-actor isolated, so they hop
/// back explicitly. Only one utterance is ever in flight — `SpeechController`
/// always stops before speaking — so a single pending callback is sufficient.
///
/// `@MainActor` is spelled out because the package has no
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor` build setting the way the app
/// target does; here the annotation is load-bearing, not decorative.
@MainActor
public final class SystemSpeechEngine: NSObject, SpeechEngine {
    private let synthesizer = AVSpeechSynthesizer()
    private var pendingFinish: (() -> Void)?
    private var cachedVoices: [AVSpeechSynthesisVoice]?

    /// Test seam: how many times the system voice list was actually enumerated.
    public private(set) var voiceLookupCount = 0

    public override init() {
        super.init()
        synthesizer.delegate = self
    }

    public func voices() -> [any VoiceDescribing] {
        if let cachedVoices { return cachedVoices }
        voiceLookupCount += 1
        let fresh = AVSpeechSynthesisVoice.speechVoices()
        cachedVoices = fresh
        return fresh
    }

    /// Call when the app returns to the foreground — a parent may have installed
    /// a voice in Settings while the app was backgrounded.
    public func invalidateVoiceCache() {
        cachedVoices = nil
    }

    /// `SpeechController.rate` converted into AVFoundation's scale.
    ///
    /// Named so a test can assert the one property that matters and that the
    /// raw constant violated: this must come out **slower** than normal speech.
    public static let utteranceRate = AVSpeechUtteranceDefaultSpeechRate * SpeechController.rate

    public func speak(_ utterance: SpeechUtterance) {
        pendingFinish = utterance.onFinish
        let u = AVSpeechUtterance(string: utterance.text)
        // `SpeechController.rate` is a fraction of normal speed, on the Web
        // Speech API's scale where 1.0 is normal. `AVSpeechUtterance.rate` runs
        // 0...1 with `AVSpeechUtteranceDefaultSpeechRate` (0.5) as normal, so
        // assigning 0.85 straight across asks for near-maximum speed — which is
        // what shipped, and it sounded exactly as fast as it was. Nothing caught
        // it: the tests asserted the constant equalled itself, and both suites
        // are silent.
        u.rate = Self.utteranceRate
        u.pitchMultiplier = SpeechController.pitch
        u.voice = utterance.voice as? AVSpeechSynthesisVoice
            ?? AVSpeechSynthesisVoice(language: utterance.languageTag)
        // Setting a voice overrides this, but it is the fallback when no voice
        // resolved and the engine picks for itself.
        synthesizer.speak(u)
    }

    public func stop() {
        // Drop the callback before stopping: a delegate call can still arrive
        // for the utterance being cancelled, and it must not resume the queue.
        pendingFinish = nil
        synthesizer.stopSpeaking(at: .immediate)
    }

    /// Invoked by the delegate, and directly by tests.
    public func simulateFinish() {
        let callback = pendingFinish
        pendingFinish = nil
        callback?()
    }
}

extension SystemSpeechEngine: AVSpeechSynthesizerDelegate {
    public nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        Task { @MainActor in self.simulateFinish() }
    }

    public nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didCancel utterance: AVSpeechUtterance
    ) {
        // Cancellation already cleared the callback in `stop()`; nothing to do.
    }
}
