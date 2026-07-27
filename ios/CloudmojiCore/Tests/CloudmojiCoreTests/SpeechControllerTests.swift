import Testing
@testable import CloudmojiCore

/// Records what would have been spoken, and lets a test decide when an
/// utterance "finishes" — so queue behaviour is deterministic.
@MainActor
private final class FakeEngine: SpeechEngine {
    var spoken: [(text: String, lang: String)] = []
    var stopCount = 0
    var onFinish: (() -> Void)?

    func voices() -> [any VoiceDescribing] {
        [FakeVoice(lang: "en-US", name: "Samantha")]
    }

    func speak(_ utterance: SpeechUtterance) {
        spoken.append((utterance.text, utterance.languageTag))
        onFinish = utterance.onFinish
    }

    func stop() {
        stopCount += 1
        onFinish = nil
    }

    /// Simulates the engine finishing the current utterance.
    func finishCurrent() {
        let callback = onFinish
        onFinish = nil
        callback?()
    }

    private struct FakeVoice: VoiceDescribing {
        let lang: String
        let name: String
    }
}

@MainActor
@Suite("SpeechController")
struct SpeechControllerTests {
    // `private` because it returns `FakeEngine`, a file-scoped `private` type —
    // the same access-level trap `VoiceResolverTests.swift` notes for `appleish`.
    private func makeController() -> (SpeechController, FakeEngine) {
        let engine = FakeEngine()
        let resolver = VoiceResolver(languages: try! EmojiRepository().languages)
        return (SpeechController(resolver: resolver, engine: engine), engine)
    }

    @Test("speaking cancels whatever came before")
    func speakCancelsPrevious() {
        let (controller, engine) = makeController()
        controller.speak("apple", in: .en)
        controller.speak("banana", in: .en)
        #expect(engine.spoken.map(\.text) == ["apple", "banana"])
        #expect(engine.stopCount == 2)
    }

    @Test("a sequence advances only when the engine reports finished")
    func sequenceChainsOnFinish() {
        let (controller, engine) = makeController()
        controller.speakSequence(
            [SpeechItem(text: "one"), SpeechItem(text: "two"), SpeechItem(text: "three")],
            in: .en
        )
        #expect(engine.spoken.map(\.text) == ["one"])
        engine.finishCurrent()
        #expect(engine.spoken.map(\.text) == ["one", "two"])
        engine.finishCurrent()
        #expect(engine.spoken.map(\.text) == ["one", "two", "three"])
    }

    @Test("cancelAll stops a sequence already in flight")
    func cancelStopsSequence() {
        let (controller, engine) = makeController()
        controller.speakSequence(
            [SpeechItem(text: "one"), SpeechItem(text: "two"), SpeechItem(text: "three")],
            in: .en
        )
        controller.cancelAll()
        engine.finishCurrent() // a late callback from the cancelled utterance
        #expect(engine.spoken.map(\.text) == ["one"], "nothing may speak after cancelAll")
    }

    @Test("onSpeak fires for each item as it starts")
    func onSpeakCallbacks() {
        let (controller, engine) = makeController()
        var seen: [String] = []
        controller.speakSequence(
            [
                SpeechItem(text: "one") { seen.append("one") },
                SpeechItem(text: "two") { seen.append("two") },
            ],
            in: .en
        )
        #expect(seen == ["one"])
        engine.finishCurrent()
        #expect(seen == ["one", "two"])
    }

    @Test("an empty sequence speaks nothing")
    func emptySequence() {
        let (controller, engine) = makeController()
        controller.speakSequence([], in: .en)
        #expect(engine.spoken.isEmpty)
    }

    @Test("rate and pitch match the web app")
    func rateAndPitch() {
        #expect(SpeechController.rate == 0.85)
        #expect(SpeechController.pitch == 1.1)
    }
}
