import AVFoundation
import Foundation

/// Which animal in the catalogue has a recording, and what it is called on disk.
///
/// The mapping is glyph → resource base name; the files live in
/// `Resources/AnimalSounds` as CC0 recordings converted with
/// `afconvert -f caff -d ima4`, each one listed in `LICENSES.txt` beside them.
///
/// **The mapping is the intent; the bundle is the truth.** A resource named here
/// with no file beside it is not an error and must never be one — the sound
/// library is sourced and ear-approved separately from the code, and a build that
/// refused to run until every file had landed would block a screen that is
/// perfectly usable without them. ``url(for:in:)`` is the only way to ask, and it
/// answers `nil`.
enum AnimalSoundCatalog {

    /// The extension the conversion produces. One place, so a swap to `.m4a`
    /// later is one edit rather than fifteen.
    static let fileExtension = "caf"

    /// Every glyph here is in `EmojiData.json` under `animals` —
    /// `AnimalSoundCatalogTests` holds that line, because a glyph that is not in
    /// the catalogue is a recording nothing can ever play.
    static let files: [String: String] = [
        "🐶": "dog",
        "🐱": "cat",
        "🐮": "cow",
        "🐷": "pig",
        "🐔": "chicken",
        "🐴": "horse",
        "🐑": "sheep",
        "🦁": "lion",
        "🐘": "elephant",
        "🐸": "frog",
        "🦆": "duck",
        "🐝": "bee",
        "🦉": "owl",
        "🐦": "bird",
        "🐵": "monkey",
        "🐧": "penguin",
    ]

    /// The recording for a glyph, if one actually shipped.
    static func url(for glyph: String, in bundle: Bundle = .main) -> URL? {
        guard let name = files[glyph] else { return nil }
        return bundle.url(forResource: name, withExtension: fileExtension)
    }

    /// The glyphs that have a playable file in this build. Empty is a valid
    /// answer and the screen still works — see ``AnimalSoundsView``.
    static func available(in bundle: Bundle = .main) -> Set<String> {
        Set(files.keys.filter { url(for: $0, in: bundle) != nil })
    }

    /// Whether a file on disk is something `AVAudioFile` will actually open.
    ///
    /// Used by the tests rather than by the app: a download that arrived
    /// truncated, or a conversion that silently produced a zero-length file, is
    /// the failure mode of a hand-assembled asset folder, and it is invisible
    /// until a child taps a dog and hears nothing.
    static func isDecodable(_ url: URL) -> Bool {
        guard let file = try? AVAudioFile(forReading: url) else { return false }
        return file.length > 0
    }
}
