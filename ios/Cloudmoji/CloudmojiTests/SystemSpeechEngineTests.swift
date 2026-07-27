import Testing
import AVFoundation
@testable import Cloudmoji
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
}
