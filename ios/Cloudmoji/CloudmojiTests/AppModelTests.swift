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
}
