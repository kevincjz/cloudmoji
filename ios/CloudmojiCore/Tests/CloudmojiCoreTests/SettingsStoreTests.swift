import Foundation
import Testing
@testable import CloudmojiCore

@Suite("SettingsStore")
struct SettingsStoreTests {
    /// Isolated defaults per test, so cases cannot leak into each other.
    func makeDefaults(_ name: String = UUID().uuidString) -> UserDefaults {
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    @Test("fresh install starts in English with everything enabled")
    func defaultsOnFreshInstall() {
        let store = SettingsStore(defaults: makeDefaults())
        #expect(store.language == .en)
        #expect(store.enabledLanguages == Set(Language.allCases))
        #expect(store.enabledCategories == Set(Category.allCases))
        #expect(store.countRange == 2...9)
        #expect(store.muted == false)
    }

    @Test("a stored language survives a reload")
    func persistence() {
        let defaults = makeDefaults()
        SettingsStore(defaults: defaults).language = .ja
        #expect(SettingsStore(defaults: defaults).language == .ja)
    }

    @Test("an unknown stored language recovers to English")
    func recoversFromCorruptLanguage() {
        let defaults = makeDefaults()
        // "es" was never a Cloudmoji language. On the web this exact value
        // reached NUMBER_WORDS[lang][n-1] and crashed Count mode on first tap.
        defaults.set("es", forKey: "cm_lang")
        #expect(SettingsStore(defaults: defaults).language == .en)
    }

    @Test("garbage in the enabled-languages set is filtered out")
    func filtersUnknownLanguages() {
        let defaults = makeDefaults()
        defaults.set(["en", "th", "zh"], forKey: "cm_enabled_langs")
        #expect(SettingsStore(defaults: defaults).enabledLanguages == [.en, .zh])
    }

    @Test("the enabled set is never empty")
    func neverEmpty() {
        let defaults = makeDefaults()
        defaults.set([String](), forKey: "cm_enabled_langs")
        #expect(SettingsStore(defaults: defaults).enabledLanguages == Set(Language.allCases))

        defaults.set([String](), forKey: "cm_enabled_cats")
        #expect(SettingsStore(defaults: defaults).enabledCategories == Set(Category.allCases))
    }

    @Test("the active language is forced back into the enabled set")
    func activeLanguageMustBeEnabled() {
        let defaults = makeDefaults()
        defaults.set("ja", forKey: "cm_lang")
        defaults.set(["en", "zh"], forKey: "cm_enabled_langs")
        // Disabling the active language would leave the picker with no valid
        // selection, so it recovers rather than showing an impossible state.
        #expect(SettingsStore(defaults: defaults).language == .en)
    }

    @Test("setting language to a disabled language recovers rather than persisting it")
    func rejectsSettingLanguageOutsideEnabledSet() {
        let defaults = makeDefaults()
        let store = SettingsStore(defaults: defaults)
        store.enabledLanguages = [.en, .zh]
        // .tl is not enabled -- this must recover, not stick.
        store.language = .tl
        #expect(store.language == .en)
    }

    @Test("the persisted language matches the recovered value, not the rejected one")
    func persistsRecoveredLanguageNotRejectedOne() {
        let defaults = makeDefaults()
        let store = SettingsStore(defaults: defaults)
        store.enabledLanguages = [.en, .zh]
        store.language = .tl
        #expect(defaults.string(forKey: "cm_lang") == "en")
        #expect(defaults.string(forKey: "cm_lang") != "tl")
    }

    @Test("setting language to an enabled language still works normally")
    func acceptsLanguageInsideEnabledSet() {
        let defaults = makeDefaults()
        let store = SettingsStore(defaults: defaults)
        store.enabledLanguages = [.en, .zh]
        store.language = .zh
        #expect(store.language == .zh)
        #expect(defaults.string(forKey: "cm_lang") == "zh")
    }

    @Test("with English disabled, an invalid stored language still recovers into the enabled set")
    func recoversWithinEnabledSetWhenEnglishIsDisabled() {
        let defaults = makeDefaults()
        defaults.set(["ja", "zh"], forKey: "cm_enabled_langs")
        // "es" was never a Cloudmoji language -- same corrupt value as
        // `recoversFromCorruptLanguage` above, but here English itself is
        // disabled. Recovering to `.en` unconditionally (the bug `init` used
        // to have) would land outside enabledLanguages, exactly the
        // inconsistent state this invariant exists to prevent.
        defaults.set("es", forKey: "cm_lang")
        let store = SettingsStore(defaults: defaults)
        #expect(store.enabledLanguages.contains(store.language))
        #expect(store.language == .ja)
    }

    @Test("with English disabled, a valid stored language is kept")
    func keepsValidStoredLanguageWhenEnglishIsDisabled() {
        let defaults = makeDefaults()
        defaults.set(["ja", "zh"], forKey: "cm_enabled_langs")
        defaults.set("zh", forKey: "cm_lang")
        #expect(SettingsStore(defaults: defaults).language == .zh)
    }

    @Test("with English disabled, the persisted language matches the recovered value")
    func persistsRecoveredLanguageWhenEnglishIsDisabled() {
        let defaults = makeDefaults()
        let store = SettingsStore(defaults: defaults)
        store.enabledLanguages = [.ja, .zh]
        // .tl is not enabled, and neither is .en -- recovery must land on
        // the alphabetically-first enabled language (`ja`) and that must be
        // what's written to disk, not a hardcoded "en".
        store.language = .tl
        #expect(store.language == .ja)
        #expect(defaults.string(forKey: "cm_lang") == "ja")
    }

    @Test("recovery from an invalid language assignment always lands inside the enabled set")
    func recoveredLanguageIsAlwaysEnabled() {
        let defaults = makeDefaults()
        let store = SettingsStore(defaults: defaults)
        store.enabledLanguages = [.ja, .zh]
        // If `resolveLanguage` could ever hand back a value outside
        // `enabled`, this assignment would fail the guard on re-entry and
        // recurse without end rather than settling -- checking membership
        // (not just a specific expected value) is what actually exercises
        // that termination guarantee.
        store.language = .en
        #expect(store.enabledLanguages.contains(store.language))
        #expect(defaults.string(forKey: "cm_lang") == store.language.rawValue)
    }

    @Test("disabling the active language settles it into the enabled set and persists that value")
    func disablingActiveLanguageSettlesAndPersists() {
        let defaults = makeDefaults()
        let store = SettingsStore(defaults: defaults)
        store.language = .ja
        // Disabling the language that is currently active forces
        // enabledLanguages's didSet to recover `language` itself. That
        // recovery reassigns `language`, re-entering *its* didSet in turn --
        // if either recovery could land outside the newly enabled set, this
        // line would hang instead of returning.
        store.enabledLanguages = [.en, .zh]
        #expect(store.enabledLanguages.contains(store.language))
        #expect(store.language == .en)
        #expect(defaults.string(forKey: "cm_lang") == "en")
    }

    @Test("an inverted or out-of-bounds count range is clamped")
    func clampsCountRange() {
        let defaults = makeDefaults()
        defaults.set(9, forKey: "cm_count_lower")
        defaults.set(2, forKey: "cm_count_upper")
        #expect(SettingsStore(defaults: defaults).countRange == 2...9)

        defaults.set(0, forKey: "cm_count_lower")
        defaults.set(99, forKey: "cm_count_upper")
        #expect(SettingsStore(defaults: defaults).countRange == 2...10)
    }
}
