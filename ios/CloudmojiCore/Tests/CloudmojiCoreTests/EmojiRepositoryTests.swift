import Testing
@testable import CloudmojiCore

@Suite("EmojiRepository")
struct EmojiRepositoryTests {
    let repo = try! EmojiRepository()

    @Test("loads the full content set")
    func counts() {
        #expect(repo.emojis.count == 200)
        #expect(repo.countables.count == 84)
        #expect(repo.languages.count == 5)
        #expect(repo.categories.count == 9)
    }

    @Test("every emoji has a non-empty word in all five languages")
    func allLanguagesPopulated() {
        for entry in repo.emojis {
            for language in Language.allCases {
                #expect(!entry.word(language).isEmpty, "\(entry.emoji) missing \(language)")
            }
        }
    }

    @Test("tooth keeps its deliberate katakana spelling")
    func toothIsKatakana() throws {
        let tooth = try #require(repo.emojis.first { $0.emoji == "🦷" })
        // Hiragana は is parsed as the topic particle and voiced "wa".
        #expect(tooth.word(.ja) == "ハ")
    }

    @Test("number words run one through ten in every language")
    func numberWords() {
        for language in Language.allCases {
            #expect(repo.numberWord(language, count: 1) != nil)
            #expect(repo.numberWord(language, count: 10) != nil)
            #expect(repo.numberWord(language, count: 11) == nil)
            #expect(repo.numberWord(language, count: 0) == nil)
        }
    }

    @Test("category labels are translated in every language")
    func categoryLabels() {
        for tab in repo.categories {
            for language in Language.allCases {
                #expect(!tab.label(language).isEmpty, "\(tab.id) missing \(language)")
            }
        }
    }

    /// The degraded case the app falls back to when the bundled resource cannot
    /// be loaded. It must be genuinely inert: every accessor still answers, so
    /// the child sees an empty grid instead of a crash.
    @Test("the empty repository answers rather than traps")
    func emptyRepositoryIsInert() {
        let empty = EmojiRepository.empty
        #expect(empty.emojis.isEmpty)
        #expect(empty.countables.isEmpty)
        #expect(empty.languages.isEmpty)
        #expect(empty.categories.isEmpty)
        for language in Language.allCases {
            #expect(empty.numberWord(language, count: 3) == nil)
            #expect(empty.meta(for: language) == nil)
        }
    }
}

@Suite("Animal sounds")
struct AnimalSoundDataTests {

    private var repository: EmojiRepository {
        (try? EmojiRepository()) ?? .empty
    }

    /// Every glyph with a noise is a real animal in the catalogue. One that is
    /// not can never be tapped — the grid is built by intersecting the two — so
    /// it is a silent content bug.
    ///
    /// Mutation: add a `"🦄"` row to `src/data/animalSounds.ts` and regenerate.
    /// This fails and names it.
    @Test("every animal with a noise is an animal in the catalogue")
    func soundsMapToRealAnimals() {
        let repo = repository
        let animals = Set(repo.emojis.filter { $0.cat == .animals }.map(\.emoji))
        #expect(!animals.isEmpty, "no animals at all — nothing below can mean anything")

        let glyphs = repo.animalSoundGlyphs
        #expect(glyphs.count >= 15, "only \(glyphs.count) animals have a noise")
        for glyph in glyphs {
            #expect(animals.contains(glyph), "\(glyph) has a noise but is not an animal")
        }
    }

    /// **Five languages or none.** A missing row is not papered over at runtime —
    /// `animalSound(for:in:)` deliberately does not fall back to English, because
    /// an English voice saying "woof" on a Chinese screen is worse than silence —
    /// so a gap here is a gap a child would actually meet.
    ///
    /// Mutation: delete the `ja` entry for any animal and regenerate. This fails
    /// and names the animal and the language.
    @Test("every animal noise exists in all five languages")
    func soundsCoverEveryLanguage() {
        let repo = repository
        for glyph in repo.animalSoundGlyphs {
            for language in Language.allCases {
                let sound = repo.animalSound(for: glyph, in: language)
                #expect(sound?.isEmpty == false,
                        "\(glyph) has no \(language.rawValue) noise")
            }
        }
    }

    /// The noise is not the name. "dog" and "woof woof" are different strings in
    /// every language, and a table that had quietly become a second copy of the
    /// word list would make the whole mini-app pointless while looking fine.
    ///
    /// Mutation: set 🐶's `en` noise to "dog". This fails.
    @Test("an animal's noise is never just its name")
    func noiseIsNotTheName() {
        let repo = repository
        let byGlyph = Dictionary(
            repo.emojis.map { ($0.emoji, $0) }, uniquingKeysWith: { first, _ in first }
        )
        for glyph in repo.animalSoundGlyphs {
            guard let entry = byGlyph[glyph] else { continue }
            for language in Language.allCases {
                let noise = repo.animalSound(for: glyph, in: language)
                #expect(noise != entry.word(language),
                        "\(glyph)'s \(language.rawValue) noise is just its name")
            }
        }
    }

    /// An animal with no entry answers `nil` rather than handing back something
    /// from another language or another animal.
    @Test("an animal with no noise says so")
    func missingGlyphHasNoSound() {
        #expect(repository.animalSound(for: "🍎", in: .en) == nil)
        #expect(!repository.animalSoundGlyphs.contains("🍎"))
    }
}
