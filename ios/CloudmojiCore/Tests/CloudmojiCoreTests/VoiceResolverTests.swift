import Testing
@testable import CloudmojiCore

// `private` keeps this file-scoped so another test file in this target can
// declare its own `FakeVoice` without a redeclaration collision. `appleish`
// below is marked `private` too: a member's access level must not be wider
// than its inferred type's, and a plain `internal` (the struct's default)
// property of type `[FakeVoice]` would be exactly that.
private struct FakeVoice: VoiceDescribing, Equatable {
    let lang: String
    let name: String
}

@Suite("VoiceResolver")
struct VoiceResolverTests {
    let resolver = VoiceResolver(languages: try! EmojiRepository().languages)

    /// A plausible notched-iPhone voice set: no Filipino, which is the norm.
    private let appleish = [
        FakeVoice(lang: "en-US", name: "Samantha"),
        FakeVoice(lang: "en-GB", name: "Daniel"),
        FakeVoice(lang: "zh-CN", name: "Tingting"),
        FakeVoice(lang: "ja-JP", name: "Kyoko"),
        FakeVoice(lang: "ms-MY", name: "Amira"),
        FakeVoice(lang: "id-ID", name: "Damayanti"),
        FakeVoice(lang: "es-ES", name: "Monica"),
    ]

    @Test("with no Filipino voice, Tagalog falls to Malay and never to English")
    func tagalogFallsToMalay() throws {
        let picked = try #require(resolver.pick(from: appleish, for: .tl))
        #expect(picked.lang == "ms-MY")
        #expect(!picked.lang.hasPrefix("en"))
    }

    @Test("a real Filipino voice wins over the fallback")
    func filipinoWins() throws {
        let voices = appleish + [FakeVoice(lang: "fil-PH", name: "Rosa")]
        #expect(try #require(resolver.pick(from: voices, for: .tl)).name == "Rosa")
    }

    @Test("tl-PH tagging is accepted as well as fil-PH")
    func tlTagAccepted() throws {
        let voices = appleish + [FakeVoice(lang: "tl-PH", name: "Angelo")]
        #expect(try #require(resolver.pick(from: voices, for: .tl)).name == "Angelo")
    }

    @Test("Malay falls back to Indonesian")
    func malayFallsToIndonesian() throws {
        let voices = appleish.filter { !$0.lang.hasPrefix("ms") }
        #expect(try #require(resolver.pick(from: voices, for: .ms)).lang == "id-ID")
        // and Tagalog then lands on Indonesian too, still not English
        #expect(try #require(resolver.pick(from: voices, for: .tl)).lang == "id-ID")
    }

    @Test("the other four languages are unaffected")
    func othersUnaffected() throws {
        #expect(try #require(resolver.pick(from: appleish, for: .en)).name == "Samantha")
        #expect(try #require(resolver.pick(from: appleish, for: .zh)).name == "Tingting")
        #expect(try #require(resolver.pick(from: appleish, for: .ja)).name == "Kyoko")
        #expect(try #require(resolver.pick(from: appleish, for: .ms)).name == "Amira")
    }

    @Test("an English-only device resolves to nothing rather than mislabelling")
    func englishOnlyDevice() {
        let voices = [FakeVoice(lang: "en-US", name: "Samantha")]
        #expect(resolver.pick(from: voices, for: .tl) == nil)
        #expect(resolver.pick(from: voices, for: .ja) == nil)
        #expect(resolver.pick(from: voices, for: .zh) == nil)
        #expect(resolver.pick(from: voices, for: .en)?.name == "Samantha")
    }

    @Test("an exact language match beats a looser one in the same tier")
    func exactMatchPreferred() throws {
        let voices = [
            FakeVoice(lang: "en-GB", name: "Daniel"),
            FakeVoice(lang: "en-US", name: "Alex"),
        ]
        #expect(try #require(resolver.pick(from: voices, for: .en)).lang == "en-US")
    }

    @Test("a voice tagged tlh (Klingon) does not steal the tl (Tagalog) tier")
    func klingonDoesNotMatchTagalogPrefix() throws {
        // "tlh" is Klingon's real IANA-registered language subtag. A bare
        // `hasPrefix("tl")` would match it — "tlh" starts with "tl" — and wrongly
        // seat it in Tagalog's tier before Malay/Indonesian ever get a look in.
        // The chain must require an exact tag or a "tl-"-prefixed subtag, so a
        // same-lettered but unrelated language never steals the tier.
        let voices = appleish + [FakeVoice(lang: "tlh", name: "Worf")]
        let picked = try #require(resolver.pick(from: voices, for: .tl))
        #expect(picked.lang == "ms-MY")
    }
}
