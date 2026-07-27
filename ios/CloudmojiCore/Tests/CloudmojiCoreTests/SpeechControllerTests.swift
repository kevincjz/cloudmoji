import Testing
@testable import CloudmojiCore

/// Records what would have been spoken, and lets a test decide when an
/// utterance "finishes" — so queue behaviour is deterministic.
@MainActor
private final class FakeEngine: SpeechEngine {
    var spoken: [(text: String, lang: String)] = []
    var stopCount = 0
    private var onFinish: (() -> Void)?
    /// The finish callback belonging to whatever was in flight the moment
    /// `stop()` was last called. A real `AVSpeechSynthesizerDelegate` call
    /// can still land after `stopSpeaking` returns — `finishLate()` models
    /// that race instead of pretending `stop()` makes a pending callback
    /// vanish.
    private var lateFinish: (() -> Void)?

    func voices() -> [any VoiceDescribing] {
        [FakeVoice(lang: "en-US", name: "Samantha")]
    }

    func speak(_ utterance: SpeechUtterance) {
        spoken.append((utterance.text, utterance.languageTag))
        onFinish = utterance.onFinish
    }

    func stop() {
        stopCount += 1
        lateFinish = onFinish
        onFinish = nil
    }

    /// Simulates the engine finishing the utterance that is currently
    /// speaking — i.e. no `stop()` has intervened since the matching
    /// `speak()`.
    func finishCurrent() {
        let callback = onFinish
        onFinish = nil
        callback?()
    }

    /// Simulates a delegate callback arriving for the utterance that was in
    /// flight at the moment `stop()` was last called — after the fact, and
    /// regardless of whatever has been spoken since.
    func finishLate() {
        let callback = lateFinish
        lateFinish = nil
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

    @Test("a late finish callback from a cancelled utterance cannot resurrect the sequence")
    func cancelStopsSequence() {
        let (controller, engine) = makeController()
        var seen: [String] = []
        controller.speakSequence(
            [
                SpeechItem(text: "one"),
                SpeechItem(text: "two") { seen.append("two") },
                SpeechItem(text: "three"),
            ],
            in: .en
        )
        controller.cancelAll()
        engine.finishLate() // a late callback from the utterance in flight when cancelAll() ran
        #expect(engine.spoken.map(\.text) == ["one"], "nothing may speak after cancelAll")
        #expect(seen.isEmpty, "onSpeak must not fire for an item in a chain that was already cancelled")
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

    @Test("an onSpeak that reentrantly cancels stops its own item from reaching the engine")
    func onSpeakReentrantCancelDoesNotEmit() {
        let (controller, engine) = makeController()
        controller.speakSequence(
            [
                SpeechItem(text: "one"),
                SpeechItem(text: "TWO") {
                    // A milestone celebration or similar reentrantly speaking
                    // over the sequence mid-item.
                    controller.speak("great job", in: .en)
                },
            ],
            in: .en
        )
        #expect(engine.spoken.map(\.text) == ["one"])
        engine.finishCurrent() // advances to "TWO", whose onSpeak cancels it
        #expect(
            engine.spoken.map(\.text) == ["one", "great job"],
            "the superseded item must not be forwarded to the engine after its own onSpeak cancelled it"
        )
    }

    @Test("an empty sequence speaks nothing")
    func emptySequence() {
        let (controller, engine) = makeController()
        controller.speakSequence([], in: .en)
        #expect(engine.spoken.isEmpty)
    }

    @Test("an empty speakSequence still cancels whatever was already playing")
    func emptySequenceCancelsInFlight() {
        let (controller, engine) = makeController()
        controller.speakSequence(
            [SpeechItem(text: "one"), SpeechItem(text: "two"), SpeechItem(text: "three")],
            in: .en
        )
        controller.speakSequence([], in: .en) // e.g. clearing the typing row
        engine.finishCurrent() // the old sequence's pending callback, if still wired up
        #expect(
            engine.spoken.map(\.text) == ["one"],
            "replacing a round with an empty one must stop the old one"
        )
        #expect(engine.stopCount == 2, "an empty request is itself a cancellation")
    }

    @Test("an empty speak still cancels whatever was already playing")
    func emptySpeakCancelsInFlight() {
        let (controller, engine) = makeController()
        controller.speak("apple", in: .en)
        controller.speak("", in: .en) // e.g. an unmapped or blank word
        #expect(engine.spoken.map(\.text) == ["apple"])
        #expect(engine.stopCount == 2, "an empty request is itself a cancellation")
    }

    @Test("speak interrupts an in-flight sequence")
    func speakInterruptsSequence() {
        let (controller, engine) = makeController()
        controller.speakSequence([SpeechItem(text: "one"), SpeechItem(text: "two")], in: .en)
        controller.speak("apple", in: .en)
        engine.finishLate() // late callback from the sequence's first item, now stale
        #expect(
            engine.spoken.map(\.text) == ["one", "apple"],
            "the sequence must not resume once speak() has taken over"
        )
    }

    @Test("a sequence interrupts an in-flight single speak")
    func sequenceInterruptsSpeak() {
        let (controller, engine) = makeController()
        controller.speak("apple", in: .en)
        controller.speakSequence([SpeechItem(text: "one"), SpeechItem(text: "two")], in: .en)
        #expect(engine.spoken.map(\.text) == ["apple", "one"])
        engine.finishCurrent()
        #expect(engine.spoken.map(\.text) == ["apple", "one", "two"])
    }

    @Test("rate and pitch match the web app")
    func rateAndPitch() {
        #expect(SpeechController.rate == 0.85)
        #expect(SpeechController.pitch == 1.1)
    }
}
