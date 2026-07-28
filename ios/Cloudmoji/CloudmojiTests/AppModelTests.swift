import Foundation
import Testing
@testable import Cloudmoji
import CloudmojiCore

@MainActor
@Suite("AppModel")
struct AppModelTests {
    /// Isolated defaults per test, so cases cannot leak into each other.
    func makeModel() -> AppModel {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return AppModel(settings: SettingsStore(defaults: defaults))
    }

    @Test("exposes all content by default")
    func allContentByDefault() {
        let model = makeModel()
        #expect(model.emojis(in: nil).count == 200)
        #expect(model.availableLanguages.count == 5)
        #expect(model.categories.count == 9)
    }

    @Test("disabling a category narrows both the grid and the tabs")
    func disablingACategory() {
        let model = makeModel()
        let before = model.emojis(in: nil).count
        model.settings.enabledCategories.remove(.fruits)
        #expect(model.emojis(in: nil).count < before)
        #expect(!model.categories.contains { $0.id == "fruits" })
        // Views must never have to filter for themselves.
        #expect(model.emojis(in: nil).allSatisfy { $0.cat != .fruits })
    }

    /// The tab disappears, but a view can still be holding the old selection for
    /// a frame. Asking for a disabled category must return nothing rather than
    /// its contents — otherwise the obvious shortcut (skip the enabled check
    /// whenever an explicit category is given) shows a parent exactly the
    /// content they just turned off, and every other test here still passes.
    @Test("a disabled category is empty even when asked for by name")
    func disabledCategoryAskedByName() {
        let model = makeModel()
        // Spelled out rather than inferred, so this also pins the module-wide
        // alias in AppModel.swift: `objc/runtime.h` declares a `Category` too,
        // and without the alias no app-target file can name ours at all. It is
        // module-qualified here only because this file imports both modules, so
        // the bare name has three candidates rather than one.
        let fruits: Cloudmoji.Category = .fruits
        #expect(!model.emojis(in: fruits).isEmpty)
        model.settings.enabledCategories.remove(fruits)
        #expect(model.emojis(in: fruits).isEmpty)
    }

    @Test("disabling a language narrows the picker")
    func disablingALanguage() {
        let model = makeModel()
        model.settings.enabledLanguages = [.en, .zh]
        #expect(model.availableLanguages.map(\.id) == [.en, .zh])
    }

    @Test("the word follows the selected language")
    func wordFollowsLanguage() throws {
        let model = makeModel()
        let apple = try #require(model.emojis(in: .fruits).first { $0.emoji == "🍎" })
        model.settings.language = .en
        #expect(model.word(for: apple) == "apple")
        model.settings.language = .ja
        #expect(model.word(for: apple) == "りんご")
    }

    @Test("the tab label follows the selected language")
    func labelFollowsLanguage() throws {
        let model = makeModel()
        let fruits = try #require(model.categories.first { $0.id == "fruits" })
        model.settings.language = .en
        #expect(model.label(for: fruits) == "Fruits")
        model.settings.language = .ja
        #expect(model.label(for: fruits) == "くだもの")
    }

    @Test("filtering by category returns only that category")
    func filterByCategory() {
        let model = makeModel()
        let fruits = model.emojis(in: .fruits)
        #expect(!fruits.isEmpty)
        #expect(fruits.allSatisfy { $0.cat == .fruits })
    }

    // MARK: - Count content

    /// The literal 84 and the literal 57. Reading either back out of the
    /// repository would agree with a model that published nothing at all.
    ///
    /// Mutation: delete the `enabledCategories.contains` line in `countables`.
    /// Both numbers come back 84.
    @Test("countables narrow when a category is switched off")
    func countablesFollowEnabledCategories() {
        let model = makeModel()
        #expect(model.countables.count == 84)

        model.settings.enabledCategories.remove(.animals)
        // 27 of the 84 countables are animals, counted from the generated JSON.
        #expect(model.countables.count == 57)
        #expect(!model.countables.contains { $0.emoji == "🐶" }, "a dog survived the filter")
        #expect(model.countables.contains { $0.emoji == "🍎" }, "fruit was filtered out too")
    }

    /// 🌟 is the one countable with no entry in the emoji catalogue, so it belongs
    /// to no category and a parent has no switch that could remove it. Narrowing
    /// to `faces` — which has no countables at all — is the sharpest way to say so.
    ///
    /// Mutation: change the `categoryOf` miss from `true` to `false` in
    /// `narrowed`. The star vanishes and `faces` alone leaves nothing to count.
    @Test("a countable in no category survives every narrowing")
    func uncategorisedCountablesAlwaysSurvive() {
        let model = makeModel()
        model.settings.enabledCategories = [.faces]
        #expect(model.countables.map(\.emoji) == ["🌟"])
    }

    /// Pure, so it can be given the case the shipped data does not contain: a
    /// narrowing that leaves nothing. Five items across two categories plus one
    /// uncategorised — the smallest fixture in which "kept the right ones",
    /// "dropped the right ones" and "kept the uncategorised one" are three
    /// distinguishable outcomes.
    ///
    /// Mutation: delete the `kept.isEmpty ? countables : kept` fallback. The last
    /// expectation returns an empty array, and Count mode has a blank screen.
    @Test("a narrowing that leaves nothing falls back to everything")
    func narrowingNeverReturnsNothing() {
        func item(_ emoji: String) -> Countable {
            Countable(emoji: emoji, en: emoji, zh: emoji, ms: emoji, ja: emoji, tl: emoji)
        }
        let all = [item("a"), item("b"), item("c"), item("d"), item("star")]
        // Module-qualified for the same reason `disabledCategoryAskedByName`
        // above qualifies its `fruits`: this file imports both modules, so the
        // bare name has more than one candidate here.
        let categories: [String: Cloudmoji.Category] = [
            "a": .animals, "b": .animals, "c": .fruits, "d": .fruits,
        ]
        let categoryOf: (Countable) -> Cloudmoji.Category? = { categories[$0.emoji] }

        #expect(
            AppModel.narrowed(all, to: [.animals], categoryOf: categoryOf).map(\.emoji)
                == ["a", "b", "star"]
        )
        #expect(
            AppModel.narrowed(all, to: [.animals, .fruits], categoryOf: categoryOf).count == 5
        )
        // Nothing in this fixture is a vehicle, so only the uncategorised star
        // survives — which is exactly why the fallback below has to be reached
        // through a fixture that has no uncategorised item at all.
        let noStar = Array(all.prefix(4))
        #expect(
            AppModel.narrowed(noStar, to: [.vehicles], categoryOf: categoryOf).count == 4,
            "an empty narrowing must degrade to the whole catalogue, not to nothing"
        )
    }

    /// The grammar is `CloudmojiCore`'s and is tested there; what is tested here
    /// is that the model hands it the *selected* language. Literals for all five,
    /// because the four non-English rules are structurally different and a model
    /// that always passed `.en` would satisfy any single-language assertion.
    ///
    /// Mutation: hardcode `.en` in `phrase(for:count:)`. Four of the five fail.
    @Test("the count phrase follows the selected language")
    func phraseFollowsLanguage() throws {
        let model = makeModel()
        let dog = try #require(model.countables.first { $0.emoji == "🐶" })

        model.settings.language = .en
        #expect(model.phrase(for: dog, count: 3) == "three dogs")
        model.settings.language = .zh
        #expect(model.phrase(for: dog, count: 3) == "三只狗")
        model.settings.language = .ms
        #expect(model.phrase(for: dog, count: 3) == "tiga ekor anjing")
        model.settings.language = .ja
        #expect(model.phrase(for: dog, count: 3) == "いぬ みっつ", "noun first, counter last")
        model.settings.language = .tl
        #expect(model.phrase(for: dog, count: 3) == "tatlong aso")
        #expect(model.phrase(for: dog, count: 4) == "apat na aso", "consonant-final numeral takes a separate na")
    }

    /// Mutation: return `SettingsStore.countBounds` instead of `settings.countRange`.
    /// The second expectation reads 2...10 and Count mode ignores the parent.
    @Test("the count range is the parent's, not the hard bounds")
    func countRangeFollowsSettings() {
        let model = makeModel()
        #expect(model.countRange == 2...9)
        model.settings.countRange = 3...5
        #expect(model.countRange == 3...5)
    }
}
