import Testing
import AVFoundation
import CloudmojiCore

@MainActor
@Suite("SystemSpeechEngine")
struct SystemSpeechEngineTests {
    @Test("installed voices are enumerated once and cached")
    func voicesAreCached() {
        let engine = SystemSpeechEngine()
        let first = engine.voices()
        let second = engine.voices()
        // Enumerating installed voices is not free, and this sits on the
        // tap-to-speech path with a sub-200ms budget.
        #expect(engine.voiceLookupCount == 1)
        #expect(first.count == second.count)
    }

    @Test("invalidating the cache forces a fresh lookup")
    func invalidateForcesLookup() {
        let engine = SystemSpeechEngine()
        _ = engine.voices()
        engine.invalidateVoiceCache()
        _ = engine.voices()
        #expect(engine.voiceLookupCount == 2)
    }

    @Test("stop drops the pending finish callback")
    func stopDropsCallback() {
        let engine = SystemSpeechEngine()
        var finished = false
        engine.speak(
            SpeechUtterance(text: "hello", languageTag: "en-US", voice: nil) { finished = true }
        )
        engine.stop()
        // A late delegate callback after stop must not resume a cancelled queue.
        engine.simulateFinish()
        #expect(finished == false)
    }

    @Test("finishing invokes the callback exactly once")
    func finishInvokesOnce() {
        let engine = SystemSpeechEngine()
        var count = 0
        engine.speak(
            SpeechUtterance(text: "hello", languageTag: "en-US", voice: nil) { count += 1 }
        )
        engine.simulateFinish()
        engine.simulateFinish()
        #expect(count == 1)
    }

    /// The rate handed to AVFoundation must be slower than normal speech.
    ///
    /// This shipped wrong. `SpeechController.rate` is 0.85 on the Web Speech
    /// API's scale, where 1.0 is normal — but `AVSpeechUtterance.rate` treats
    /// 0.5 as normal, so assigning it directly asked for near-maximum speed and
    /// the app gabbled at a toddler. Kevin heard it on the device; no test did.
    ///
    /// Asserting `utteranceRate == 0.425` would restate the arithmetic. This
    /// asserts the *intent*, so reverting to the raw constant fails here.
    @Test("speech is slower than normal, not faster")
    func rateIsSlowerThanNormal() {
        #expect(
            SystemSpeechEngine.utteranceRate < AVSpeechUtteranceDefaultSpeechRate,
            "a word aimed at a 2-year-old must be slower than natural speech"
        )
        // Not so slow it drawls — the web sits 15% under normal.
        #expect(SystemSpeechEngine.utteranceRate > AVSpeechUtteranceDefaultSpeechRate * 0.7)
        #expect(SystemSpeechEngine.utteranceRate >= AVSpeechUtteranceMinimumSpeechRate)
        #expect(SystemSpeechEngine.utteranceRate <= AVSpeechUtteranceMaximumSpeechRate)
    }
}
