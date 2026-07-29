import Testing
import CloudmojiCore

@Suite("Radio")
struct RadioTests {

    /// A message survives the round trip unchanged, in both directions and every
    /// language — the whole point of the codec.
    ///
    /// Mutation: write `direction.rawValue` as a constant in `payload`. The
    /// toPhone case decodes as toWatch and this fails.
    @Test("an emoji message round-trips in both directions and all five languages")
    func messageRoundTrips() {
        for direction in [RadioMessage.Direction.toWatch, .toPhone] {
            for language in Language.allCases {
                let original = RadioMessage(emoji: "🍎", direction: direction, language: language)
                let decoded = RadioMessage(payload: original.payload)
                #expect(decoded == original,
                        "\(direction.rawValue)/\(language.rawValue) did not survive the round trip")
            }
        }
    }

    /// The context round-trips, including both mute states.
    ///
    /// Mutation: write `"muted"` as `muted ? "0" : "1"`. Both cases invert and
    /// this fails.
    @Test("a context round-trips, muted and unmuted")
    func contextRoundTrips() {
        for muted in [true, false] {
            for language in Language.allCases {
                let original = RadioContext(language: language, muted: muted)
                #expect(RadioContext(payload: original.payload) == original)
            }
        }
    }

    /// A garbled message decodes to nil rather than trapping or inventing an
    /// emoji — silence is the safe failure.
    ///
    /// Mutation: drop the `emoji` guard in `init?(payload:)`. The missing-emoji
    /// case decodes non-nil and this fails.
    @Test("a malformed message payload is rejected, never guessed")
    func messageRejectsGarbage() {
        // Missing emoji.
        #expect(RadioMessage(payload: ["kind": "emoji", "dir": "toWatch", "lang": "en"]) == nil)
        // Empty emoji.
        #expect(RadioMessage(payload: ["kind": "emoji", "emoji": "", "dir": "toWatch", "lang": "en"]) == nil)
        // A context payload fed to the message decoder (wrong kind / no kind).
        #expect(RadioMessage(payload: ["v": "1", "lang": "en", "muted": "0"]) == nil)
        // Non-String value (what an Int-coded field would arrive as).
        #expect(RadioMessage(payload: ["kind": "emoji", "emoji": "🍎", "dir": "toWatch", "lang": 3]) == nil)
        // Unknown language and unknown direction.
        #expect(RadioMessage(payload: ["kind": "emoji", "emoji": "🍎", "dir": "toWatch", "lang": "es"]) == nil)
        #expect(RadioMessage(payload: ["kind": "emoji", "emoji": "🍎", "dir": "sideways", "lang": "en"]) == nil)
        // Nothing at all.
        #expect(RadioMessage(payload: [:]) == nil)
    }

    /// A context with no usable language is rejected; a missing mute flag is the
    /// harmless "not muted".
    ///
    /// Mutation: default a missing language to `.en` instead of returning nil.
    /// The unknown-language case decodes non-nil and this fails.
    @Test("a context needs a real language, and a missing mute means sound on")
    func contextRejectsGarbageButForgivesMute() {
        #expect(RadioContext(payload: [:]) == nil)
        #expect(RadioContext(payload: ["muted": "1"]) == nil, "no language, no context")
        #expect(RadioContext(payload: ["lang": "es", "muted": "1"]) == nil, "unknown language")

        // A missing or unrecognised mute flag can only mean 'make a sound'.
        #expect(RadioContext(payload: ["lang": "en"])?.muted == false)
        #expect(RadioContext(payload: ["lang": "en", "muted": "yes"])?.muted == false)
        #expect(RadioContext(payload: ["lang": "en", "muted": "1"])?.muted == true)
    }

    /// The enum raw values are a cross-version wire contract, not an
    /// implementation detail. A rename would let a watch on the old build and a
    /// phone on the new one silently disagree about which way an emoji is going.
    ///
    /// Mutation: rename a case's raw value. This fails at the literal.
    @Test("the direction raw values are the pinned wire contract")
    func directionRawValuesArePinned() {
        #expect(RadioMessage.Direction.toWatch.rawValue == "toWatch")
        #expect(RadioMessage.Direction.toPhone.rawValue == "toPhone")
        // And the payload actually carries them under the agreed keys.
        let payload = RadioMessage(emoji: "🐶", direction: .toPhone, language: .zh).payload
        #expect(payload["kind"] == "emoji")
        #expect(payload["dir"] == "toPhone")
        #expect(payload["emoji"] == "🐶")
        #expect(payload["lang"] == "zh")
    }
}
