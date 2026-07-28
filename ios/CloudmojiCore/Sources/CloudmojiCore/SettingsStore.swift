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
        static let seenTutorial = "cm_seen_tutorial"
    }

    /// Count mode never goes below two (one is not counting) or above ten
    /// (Japanese has no ～つ form past とお).
    public static let countBounds = 2...10

    public var language: Language {
        didSet {
            guard enabledLanguages.contains(language) else {
                // Recover rather than silently persist an inconsistent state --
                // the same principle enabledLanguages's own didSet applies in
                // the opposite direction, and for the same reason: both
                // funnel through `resolveLanguage`, the single place that
                // rule lives (see its doc comment), so the two paths can
                // never disagree.
                //
                // Under @Observable this stored property is rewritten into a
                // computed one whose didSet body lives in a real setter, so
                // assigning to `language` here *does* re-trigger this
                // didSet -- it does not no-op. This outer call returns right
                // after, before ever reaching `defaults.set` below, so it is
                // the nested (re-triggered) call that actually performs the
                // persist. That nesting is exactly two levels deep, not
                // open-ended recursion: `resolveLanguage`'s result is always
                // a member of `enabled`, so the guard above passes on
                // re-entry and the nested call falls through to the persist
                // instead of recovering again.
                language = Self.resolveLanguage(preferring: language, enabled: enabledLanguages)
                return
            }
            defaults.set(language.rawValue, forKey: Key.language)
        }
    }

    public var enabledLanguages: Set<Language> {
        didSet {
            let cleaned = enabledLanguages.isEmpty ? Set(Language.allCases) : enabledLanguages
            if cleaned != enabledLanguages { enabledLanguages = cleaned; return }
            defaults.set(cleaned.map(\.rawValue).sorted(), forKey: Key.enabledLanguages)
            if !cleaned.contains(language) { language = Self.resolveLanguage(preferring: language, enabled: cleaned) }
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
            // Unlike language/enabledLanguages/enabledCategories above, this
            // used to persist whatever it was handed -- an out-of-bounds
            // range survived the session instead of self-healing. Route
            // through the same `clampedRange` helper `readRange` uses, so
            // init and the runtime setter can't disagree about what a valid
            // range is.
            let cleaned = Self.clampedRange(lower: countRange.lowerBound, upper: countRange.upperBound)
            if cleaned != countRange { countRange = cleaned; return }
            defaults.set(countRange.lowerBound, forKey: Key.countLower)
            defaults.set(countRange.upperBound, forKey: Key.countUpper)
        }
    }

    public var muted: Bool {
        didSet { defaults.set(muted, forKey: Key.muted) }
    }

    /// Whether the welcome tour has been dismissed at least once.
    ///
    /// The only key here whose *default* is the interesting value: false on a
    /// fresh install is what makes the tour appear, so this must never be
    /// written at launch — only when a grown-up (or a toddler) has actually
    /// dismissed it. Reopening the tour from Settings deliberately does not
    /// touch it; that route is a lookup, not a first run.
    ///
    /// No validation clause, unlike the four above, and that is not an
    /// oversight: `UserDefaults.bool(forKey:)` maps anything that is not a
    /// truthy value to false, so a hand-edited or stale value can only ever
    /// mean "show the tour again" — the harmless direction. `muted` is stored
    /// the same way for the same reason.
    public var seenTutorial: Bool {
        didSet { defaults.set(seenTutorial, forKey: Key.seenTutorial) }
    }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults

        let languages = Self.readSet(defaults, Key.enabledLanguages, Language.init(rawValue:))
        let cleanedLanguages = languages.isEmpty ? Set(Language.allCases) : languages

        let categories = Self.readSet(defaults, Key.enabledCategories, Category.init(rawValue:))
        let cleanedCategories = categories.isEmpty ? Set(Category.allCases) : categories

        // didSet does not run during init, so the "active language must be
        // enabled" rule is applied explicitly here as well, via the same
        // `resolveLanguage` helper `language`'s didSet uses -- that is the
        // single place the rule lives, so the two paths can't disagree.
        // This is computed with locals (rather than reading
        // self.enabledLanguages) because, under @Observable, self cannot be
        // used until every stored property has an initial value.
        let stored = defaults.string(forKey: Key.language).flatMap(Language.init(rawValue:))
        let resolvedLanguage = Self.resolveLanguage(preferring: stored, enabled: cleanedLanguages)

        self.enabledLanguages = cleanedLanguages
        self.enabledCategories = cleanedCategories
        self.language = resolvedLanguage
        self.countRange = Self.readRange(defaults)
        self.muted = defaults.bool(forKey: Key.muted)
        self.seenTutorial = defaults.bool(forKey: Key.seenTutorial)
    }

    /// The single place the "active language must be enabled" invariant is
    /// decided. Both `init` (reading a possibly-stale stored value) and
    /// `language`'s didSet (reacting to a runtime assignment that turned out
    /// to be disabled) call this rather than each encoding their own
    /// recovery -- that duplication is exactly what let them drift apart:
    /// `init` used to recover to `.en` unconditionally, which is wrong when
    /// a parent has disabled English.
    ///
    /// Resolution order: `candidate` if it's enabled, else `.en` if *it's*
    /// enabled, else the alphabetically-first enabled language. The last
    /// tier only matters once English has been disabled -- there's always
    /// some enabled language to fall back to, so this never needs to
    /// express "no valid answer".
    ///
    /// `enabled` must be non-empty. Both call sites already guarantee this
    /// -- `init` passes the cleaned set (empty is replaced with
    /// `Language.allCases` before it ever reaches here) and the didSet
    /// passes `enabledLanguages`, which its own didSet keeps non-empty the
    /// same way. That's asserted below via `precondition` rather than
    /// trusted implicitly, so a future call site that breaks the guarantee
    /// fails loudly at the point of the mistake instead of via a bare `!`
    /// somewhere downstream.
    private static func resolveLanguage(preferring candidate: Language?, enabled: Set<Language>) -> Language {
        precondition(!enabled.isEmpty, "resolveLanguage requires a non-empty enabled set; callers must guarantee this")
        if let candidate, enabled.contains(candidate) { return candidate }
        if enabled.contains(.en) { return .en }
        // `enabled` is non-empty per the precondition above, so `min` here
        // always finds a value -- the `?? .en` is unreachable, not a real
        // fallback, and exists only so this stays force-unwrap-free.
        return enabled.min { $0.rawValue < $1.rawValue } ?? .en
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

    /// The single place the "count range is within countBounds, and not
    /// inverted" invariant is decided. Both `readRange` (reading two
    /// possibly-stale, possibly-inverted raw Ints out of UserDefaults) and
    /// `countRange`'s didSet (reacting to a runtime assignment that may sit
    /// outside countBounds) call this rather than each encoding their own
    /// clamp -- the same shared-helper shape `resolveLanguage` uses above.
    ///
    /// Each bound is clamped independently into countBounds, then the pair
    /// falls back to the default 2...9 if that leaves them inverted. A
    /// single-value range (lower == upper) is valid -- a parent choosing
    /// "exactly 3" is a real, intentional setting, not an error.
    private static func clampedRange(lower: Int, upper: Int) -> ClosedRange<Int> {
        let clampedLower = max(countBounds.lowerBound, lower)
        let clampedUpper = min(countBounds.upperBound, upper)
        guard clampedLower <= clampedUpper else { return 2...9 }
        return clampedLower...clampedUpper
    }

    private static func readRange(_ defaults: UserDefaults) -> ClosedRange<Int> {
        guard defaults.object(forKey: Key.countLower) != nil,
              defaults.object(forKey: Key.countUpper) != nil else { return 2...9 }
        return clampedRange(
            lower: defaults.integer(forKey: Key.countLower),
            upper: defaults.integer(forKey: Key.countUpper)
        )
    }
}
