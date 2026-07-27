import Foundation

/// Builds the spoken phrase for "N of this thing", per language.
///
/// The rules differ structurally, not just lexically:
/// zh and ms bake the classifier into the noun, ja fuses the counter into the
/// number and puts the noun first, tl attaches a linker to the numeral.
public struct CountingGrammar: Sendable {
    private let repository: EmojiRepository

    public init(repository: EmojiRepository) {
        self.repository = repository
    }

    public func phrase(_ item: Countable, count: Int, in language: Language) -> String {
        guard let number = repository.numberWord(language, count: count) else {
            // No number word for this count — speak the bare noun rather than
            // fabricate a counter.
            return item.noun(language)
        }

        switch language {
        case .en:
            return "\(number) \(englishPlural(item, count: count))"
        case .zh:
            // The measure word is already part of the noun (只狗), and Chinese
            // takes no space between numeral and classifier.
            return "\(number)\(item.zh)"
        case .ms:
            // Likewise the penjodoh bilangan (ekor anjing), space-separated.
            return "\(number) \(item.ms)"
        case .ja:
            // Noun first, counter last: "りんご みっつ". The number-の-noun order
            // is grammatical but bookish, and the ～つ counter is already fused
            // into the number word, so the noun never changes form.
            return "\(item.ja) \(number)"
        case .tl:
            // The linker attaches to the NUMERAL, not the noun, and the noun is
            // never pluralised after a numeral.
            return "\(Self.tagalogLinked(number)) \(item.tl)"
        }
    }

    // MARK: - English

    func englishPlural(_ item: Countable, count: Int) -> String {
        guard count > 1 else { return item.en }
        if let irregular = item.enPlural { return irregular }
        return Self.regularPlural(item.en)
    }

    static func regularPlural(_ noun: String) -> String {
        if noun == "fish" { return "fish" }
        if noun.hasSuffix("y"), let beforeY = noun.dropLast().last,
           !"aeiou".contains(beforeY) {
            return noun.dropLast() + "ies"
        }
        for suffix in ["s", "sh", "ch", "x", "z"] where noun.hasSuffix(suffix) {
            return noun + "es"
        }
        return noun + "s"
    }

    // MARK: - Tagalog

    /// Attaches the Tagalog linker to a numeral.
    /// Vowel-final takes -ng (tatlo → tatlong); n-final takes -g;
    /// any other consonant takes a separate "na" (apat → apat na).
    static func tagalogLinked(_ number: String) -> String {
        guard let last = number.lowercased().last else { return number }
        if "aeiou".contains(last) { return number + "ng" }
        if last == "n" { return number + "g" }
        return number + " na"
    }
}
