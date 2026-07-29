import Foundation
import Testing
import CloudmojiCore
@testable import Cloudmoji

@Suite("Animal sounds")
@MainActor
struct AnimalSoundCatalogTests {

    private var repository: EmojiRepository {
        (try? EmojiRepository()) ?? .empty
    }

    /// **The one that matters.** A glyph in the catalogue that is not in
    /// `EmojiData.json` under `animals` is a recording nothing can ever play: the
    /// grid is built from the repository, so a tile for it never appears and the
    /// file is dead weight in the bundle.
    ///
    /// Mutation: add `"🐺": "wolf"` to `files`. There is no 🐺 in the catalogue
    /// and this fails, naming it.
    @Test("every mapped glyph is a real animal in the shipped catalogue")
    func everyGlyphIsInTheCatalogue() {
        let animals = Set(repository.emojis.filter { $0.cat == .animals }.map(\.emoji))
        #expect(!animals.isEmpty, "the repository has no animals at all — nothing below can mean anything")

        for glyph in AnimalSoundCatalog.files.keys {
            #expect(animals.contains(glyph),
                    "\(glyph) is mapped to a sound but is not an animal in EmojiData.json")
        }
    }

    /// Resource names have to be usable as bundle resource names and unique: two
    /// glyphs pointing at one file is a copy-paste error that plays a cat for a
    /// dog, and it is silent in every other check.
    @Test("resource names are unique and bundle-safe")
    func resourceNamesAreSane() {
        let names = Array(AnimalSoundCatalog.files.values)
        #expect(!names.isEmpty)
        #expect(Set(names).count == names.count, "two glyphs share a file name in \(names.sorted())")
        for name in names {
            #expect(!name.isEmpty)
            #expect(name.allSatisfy { $0.isASCII && ($0.isLowercase || $0 == "-") },
                    "\"\(name)\" is not a plain lowercase resource name")
        }
    }

    /// Enough animals to be a mini-app rather than a demo. This is about the
    /// *mapping*, not the files — see ``bundledRecordingsAllDecode`` for those.
    @Test("the catalogue names at least a dozen animals")
    func catalogueIsBigEnough() {
        #expect(AnimalSoundCatalog.files.count >= 12,
                "only \(AnimalSoundCatalog.files.count) animals are mapped")
    }

    /// Every recording that actually shipped is one `AVAudioFile` will open.
    ///
    /// A download that arrived truncated, or a conversion that silently produced
    /// a zero-length file, is the failure mode of a hand-assembled asset folder —
    /// and it is invisible until a child taps a dog and hears nothing.
    ///
    /// **Vacuous while the library is empty, and deliberately so.** The mapping
    /// is the intent and the bundle is the truth; the screen falls back to
    /// speaking the animal's name for anything with no file, so an empty folder
    /// is a valid build. What this must never do is pass over a *corrupt* file.
    @Test("every recording that shipped decodes")
    func bundledRecordingsAllDecode() {
        let available = AnimalSoundCatalog.available(in: .main)
        for glyph in available {
            guard let url = AnimalSoundCatalog.url(for: glyph, in: .main) else {
                Issue.record("\(glyph) reported available with no URL")
                continue
            }
            #expect(AnimalSoundCatalog.isDecodable(url),
                    "\(glyph) → \(url.lastPathComponent) will not open as audio")
        }
    }

    /// The grid is never empty, whether or not a single recording has landed.
    ///
    /// This is the assertion that makes the fallback design safe: content comes
    /// from the repository, not from the sound folder, so a build with no audio
    /// in it still shows a child forty-one animals that all say their names.
    ///
    /// Mutation: build the grid from `AnimalSoundCatalog.available()` instead of
    /// `emojis(in: .animals)`. With no files shipped the screen is blank and this
    /// fails.
    @Test("the animal grid is populated from the catalogue, not from the sound folder")
    func gridComesFromTheRepository() {
        let defaults = UserDefaults(suiteName: "animals-\(UUID().uuidString)")!
        let model = AppModel(settings: SettingsStore(defaults: defaults))
        let animals = model.emojis(in: .animals)

        #expect(animals.count >= 20, "only \(animals.count) animals on the grid")
        // Strictly more than the sound library, which is the whole point: the
        // grid does not shrink to what has been recorded.
        #expect(animals.count > AnimalSoundCatalog.files.count)
        #expect(animals.allSatisfy { $0.cat == .animals })
    }

    /// A glyph with no recording is asked about and answered `nil`, rather than
    /// producing a URL that fails to open at play time.
    @Test("an unmapped glyph has no sound and says so")
    func unmappedGlyphHasNoSound() {
        #expect(AnimalSoundCatalog.url(for: "🍎", in: .main) == nil)
        #expect(!AnimalSoundCatalog.available(in: .main).contains("🍎"))
    }
}
