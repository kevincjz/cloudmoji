import Foundation
import Observation

/// Parent-facing settings, persisted and validated.
///
/// Every value is validated on read. Without that, a stale or hand-edited value
/// flows straight through — on the web a leftover language of "es" reached
/// NUMBER_WORDS[lang][n-1] and crashed Count mode on the first tap.
@Observable
public final class SettingsStore {
    private let defaults: UserDefaults

    private enum Key {
        static let language = "cm_lang"
        static let enabledLanguages = "cm_enabled_langs"
        static let enabledCategories = "cm_enabled_cats"
        static let countLower = "cm_count_lower"
        static let countUpper = "cm_count_upper"
        static let muted = "cm_muted"
    }

    /// Count mode never goes below two (one is not counting) or above ten
    /// (Japanese has no ～つ form past とお).
    public static let countBounds = 2...10

    public var language: Language {
        didSet { defaults.set(language.rawValue, forKey: Key.language) }
    }

    public var enabledLanguages: Set<Language> {
        didSet {
            let cleaned = enabledLanguages.isEmpty ? Set(Language.allCases) : enabledLanguages
            if cleaned != enabledLanguages { enabledLanguages = cleaned; return }
            defaults.set(cleaned.map(\.rawValue).sorted(), forKey: Key.enabledLanguages)
            if !cleaned.contains(language) { language = cleaned.sorted { $0.rawValue < $1.rawValue }.first! }
        }
    }

    public var enabledCategories: Set<Category> {
        didSet {
            let cleaned = enabledCategories.isEmpty ? Set(Category.allCases) : enabledCategories
            if cleaned != enabledCategories { enabledCategories = cleaned; return }
            defaults.set(cleaned.map(\.rawValue).sorted(), forKey: Key.enabledCategories)
        }
    }

    public var countRange: ClosedRange<Int> {
        didSet {
            defaults.set(countRange.lowerBound, forKey: Key.countLower)
            defaults.set(countRange.upperBound, forKey: Key.countUpper)
        }
    }

    public var muted: Bool {
        didSet { defaults.set(muted, forKey: Key.muted) }
    }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults

        let languages = Self.readSet(defaults, Key.enabledLanguages, Language.init(rawValue:))
        let cleanedLanguages = languages.isEmpty ? Set(Language.allCases) : languages

        let categories = Self.readSet(defaults, Key.enabledCategories, Category.init(rawValue:))
        let cleanedCategories = categories.isEmpty ? Set(Category.allCases) : categories

        // didSet does not run during init, so the "active language must be
        // enabled" rule is applied explicitly here as well. This is computed
        // with locals (rather than reading self.enabledLanguages) because,
        // under @Observable, self cannot be used until every stored property
        // has an initial value.
        let stored = defaults.string(forKey: Key.language).flatMap(Language.init(rawValue:))
        let resolvedLanguage: Language
        if let stored, cleanedLanguages.contains(stored) {
            resolvedLanguage = stored
        } else {
            resolvedLanguage = .en
        }

        self.enabledLanguages = cleanedLanguages
        self.enabledCategories = cleanedCategories
        self.language = resolvedLanguage
        self.countRange = Self.readRange(defaults)
        self.muted = defaults.bool(forKey: Key.muted)
    }

    private static func readSet<T: Hashable>(
        _ defaults: UserDefaults,
        _ key: String,
        _ make: (String) -> T?
    ) -> Set<T> {
        guard defaults.object(forKey: key) != nil else { return [] }
        let raw = defaults.stringArray(forKey: key) ?? []
        return Set(raw.compactMap(make))
    }

    private static func readRange(_ defaults: UserDefaults) -> ClosedRange<Int> {
        guard defaults.object(forKey: Key.countLower) != nil,
              defaults.object(forKey: Key.countUpper) != nil else { return 2...9 }
        let lower = max(countBounds.lowerBound, defaults.integer(forKey: Key.countLower))
        let upper = min(countBounds.upperBound, defaults.integer(forKey: Key.countUpper))
        guard lower < upper else { return 2...9 }
        return lower...upper
    }
}
