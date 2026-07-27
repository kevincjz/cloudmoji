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
