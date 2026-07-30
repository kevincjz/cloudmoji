import Foundation
import Testing
@testable import Cloudmoji
import CloudmojiCore

@MainActor
@Suite("AppModel")
struct AppModelTests {
    /// Isolated defaults per test, so cases cannot leak into each other.
    func makeModel(unlocked: Bool = true) -> AppModel {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        defaults.set(unlocked, forKey: StubEntitlementStore.storageKey)
        return AppModel(
            settings: SettingsStore(defaults: defaults),
            entitlements: StubEntitlementStore(defaults: defaults)
        )
    }

    @Test("exposes all content by default")
    func allContentByDefault() {
        let model = makeModel()
        #expect(model.emojis(in: nil).count == 200)
        #expect(model.availableLanguages.count == 5)
        #expect(model.categories.count == 9)
    }

    // MARK: - The continuous list

    /// The list is every enabled emoji, once, in catalogue order — not a
    /// category at a time.
    ///
    /// The counts are spelled out. Reading them back off `emojis(in: nil)`
    /// would be a derived value asserted against its own definition, which is
    /// this project's most common dead test.
    @Test("the sections hold the whole catalogue, once each, in catalogue order")
    func sectionsCoverEverything() {
        let model = makeModel()
        let sections = model.sections

        #expect(sections.count == 8, "\(sections.count) sections for 8 categories")
        #expect(sections.map(\.id) == ["fruits", "food", "animals", "vehicles",
                                      "nature", "objects", "people", "faces"])
        #expect(sections.flatMap(\.entries).count == 200)
        // No emoji in two sections, and every section actually holds its own.
        let ids = sections.flatMap(\.entries).map(\.id)
        #expect(Set(ids).count == ids.count, "an emoji appears in two sections")
        for section in sections {
            #expect(!section.entries.isEmpty, "\(section.id) is an empty section")
            #expect(
                section.entries.allSatisfy { $0.cat.rawValue == section.id },
                "\(section.id) holds an emoji from another category"
            )
        }
    }

    /// "All" is a view of the list, not a place inside it. A chip for it could
    /// never be the section the child is in, and tapping it would be a control
    /// that does nothing.
    @Test("there is no All section")
    func noAllSection() {
        let model = makeModel()
        #expect(model.categories.contains { $0.id == "all" }, "the tab itself still exists")
        #expect(!model.sections.contains { $0.id == "all" })
    }

    /// Settings narrowing still applies, and it applies *structurally*: the
    /// switched-off category has no section at all rather than an empty one.
    ///
    /// This is what replaced `WordsView`'s fallback handler. Before, a chip
    /// filtered the grid and switching off the category the child was on left a
    /// blank screen; now that category is simply not in the list, and the other
    /// seven are untouched.
    ///
    /// Mutation: build `sections` from `repository.categories` instead of the
    /// filtered `categories` — Fruits keeps its section and this fails.
    /// (Swapping the inner `emojis(in: category)` for an unfiltered filter does
    /// *not* fail, and deliberately so: the two filters are belt and braces, and
    /// the outer one is the load-bearing half.)
    @Test("a category switched off has no section, and the rest are untouched")
    func disablingACategoryRemovesItsSection() {
        let model = makeModel()
        #expect(model.sections.contains { $0.id == "fruits" }, "setup")

        model.settings.enabledCategories.remove(.fruits)

        #expect(!model.sections.contains { $0.id == "fruits" })
        #expect(model.sections.count == 7)
        #expect(model.sections.flatMap(\.entries).allSatisfy { $0.cat != .fruits })
        // A narrowing, not a blanking — the child still has a list to scroll.
        #expect(model.sections.flatMap(\.entries).count > 150)
        #expect(model.sections.contains { $0.id == "animals" })
    }

    /// The extreme: one category left on. Still a list, still scrollable, still
    /// no empty section anywhere.
    @Test("one enabled category leaves exactly one section")
    func oneCategoryLeavesOneSection() {
        let model = makeModel()
        model.settings.enabledCategories = [.faces]
        #expect(model.sections.map(\.id) == ["faces"])
        #expect(!model.sections[0].entries.isEmpty)
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

    @Test("the free plan always renders and speaks English")
    func freePlanUsesEnglishWithoutOverwritingThePreference() throws {
        let model = makeModel(unlocked: false)
        model.settings.language = .ja
        let apple = try #require(model.emojis(in: .fruits).first { $0.emoji == "🍎" })

        #expect(model.effectiveLanguage == .en)
        #expect(model.availableLanguages.map(\.id) == [.en])
        #expect(model.word(for: apple) == "apple")
        #expect(!model.canCycleLanguage)

        model.cycleLanguage()
        #expect(model.settings.language == .ja, "the saved Full preference was overwritten")
    }

    @Test("the access policy is the complete free and Full contract")
    func accessPolicyContract() {
        let free = AppAccessPolicy(hasFullAccess: false)
        #expect(free.canUse(.words))
        #expect(free.canUse(.count))
        #expect(!free.canUse(.instrument))
        #expect(!free.canUse(.flashCards))
        #expect(!free.canUse(.animalSounds))
        #expect(!free.canUse(.photos))
        #expect(!free.canUse(.sleepy))
        #expect(free.effectiveLanguage(preferred: .tl) == .en)
        #expect(!free.canUseWatch)
        #expect(!free.canUseWatchVoiceNotes)

        let full = AppAccessPolicy(hasFullAccess: true)
        #expect(MiniApp.allCases.allSatisfy { full.canUse($0) })
        #expect(full.effectiveLanguage(preferred: .ja) == .ja)
        #expect(full.canUseWatch)
        #expect(full.canUseWatchVoiceNotes)
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

    // MARK: - What Settings edits

    /// Settings is the one screen that must show what is switched **off**, so it
    /// reads the unfiltered lists. Everything else in the app reads the filtered
    /// ones — that is the whole point of filtering in the model.
    ///
    /// Mutation: point `allLanguages` at `availableLanguages`. The second
    /// expectation fails and a parent can never switch a language back on.
    @Test("Settings sees every language and category, including the disabled ones")
    func settingsSeesEverything() {
        let model = makeModel()
        #expect(model.allLanguages.count == 5)
        #expect(model.allCategories.count == 8, "8 real categories; the All tab is not one of them")
        #expect(!model.allCategories.contains { $0.id == "all" })

        model.settings.enabledLanguages = [.en]
        model.settings.enabledCategories = [.fruits]
        #expect(model.allLanguages.count == 5, "disabled languages vanished from Settings")
        #expect(model.allCategories.count == 8, "disabled categories vanished from Settings")
        #expect(model.availableLanguages.count == 1, "the rest of the app should still be narrowed")
        #expect(model.categories.count == 2, "the child's tab strip should be All plus Fruits")
    }

    /// `SettingsStore` silently re-enables everything if the last one is switched
    /// off — a sane invariant, and a baffling thing to watch happen. Settings asks
    /// first and greys the switch instead.
    ///
    /// Mutation: return `true` unconditionally. The two "== false" expectations
    /// fail, and a parent turning off their last language sees all five snap back on.
    @Test("the last enabled language and category cannot be switched off")
    func theLastOneIsProtected() {
        let model = makeModel()
        #expect(model.canDisableLanguage(.en))
        #expect(model.canDisableCategory(.fruits))

        model.settings.enabledLanguages = [.zh]
        #expect(model.canDisableLanguage(.zh) == false)
        #expect(model.canDisableLanguage(.en), "a language that is already off is not the last one on")

        model.settings.enabledCategories = [.animals]
        #expect(model.canDisableCategory(.animals) == false)
        #expect(model.canDisableCategory(.fruits))
    }

    /// What the greying is protecting the parent from, spelled out: the store's
    /// own recovery. Without `canDisable*` in front of it, the switch a parent
    /// taps turns *itself* back on along with four others.
    ///
    /// This is the justification for the two functions above, and it belongs in a
    /// test rather than only in a comment — `SettingsStore`'s behaviour here is
    /// deliberate and must not be "fixed" out from under Settings.
    ///
    /// Mutation: delete the `isEmpty ? Set(Language.allCases)` recovery in
    /// `SettingsStore`. Run and confirmed failing — as a trap rather than an
    /// expectation, on `resolveLanguage`'s own precondition, which is the store
    /// saying the same thing one layer down. Either way the app has no language
    /// and dies, which is precisely what Settings must never be able to cause.
    @Test("emptying the last switch is what the greying prevents")
    func emptyingSnapsEverythingBackOn() {
        let model = makeModel()
        model.settings.enabledLanguages = []
        #expect(model.availableLanguages.count == 5, "the store did not recover an empty language set")
        model.settings.enabledCategories = []
        #expect(model.categories.count == 9, "the store did not recover an empty category set")
    }

    // MARK: - The language toggle

    /// A cycle has to come back. Tapping once per enabled language and landing
    /// where it started is the whole contract, and it is not satisfied by a
    /// button that simply advances — one that ran off the end and stuck on the
    /// last language would pass any "the language changed" assertion.
    ///
    /// The visited list is asserted as a whole rather than just its endpoints:
    /// endpoints alone would also accept a toggle that oscillated between the
    /// first two languages and never reached 日本語.
    ///
    /// Mutation: drop the `% enabled.count` in `nextLanguage(after:in:)`. The
    /// fourth tap traps on an out-of-range index; with a clamp instead of a
    /// modulo it sticks on `.tl` and the sequence assertion fails.
    @Test("cycling visits every enabled language once and wraps to the start")
    func cyclingWrapsAround() {
        let model = makeModel()
        model.settings.language = .en
        let expected = model.availableLanguages.map(\.id)
        #expect(expected.count == 5, "setup: all five languages should be enabled by default")

        var visited: [Language] = []
        for _ in expected.indices {
            model.cycleLanguage()
            visited.append(model.settings.language)
        }
        // Five taps from the first language: the other four in order, then home.
        #expect(visited == Array(expected.dropFirst()) + [expected[0]], "visited \(visited)")
    }

    /// The reason the toggle exists in this shape: a family that switched three
    /// languages off in Settings gets a two-way toggle, not a five-way one.
    ///
    /// Ten taps rather than two, so a bug that only shows up after the wrap has
    /// somewhere to appear. The assertion is on the *set* of everything visited —
    /// asserting `!= .zh` on a single tap would pass even if the third tap landed
    /// on Chinese.
    ///
    /// Mutation: make `cycleLanguage` read `repository.languages` instead of
    /// `availableLanguages` (i.e. drop the Settings filter). Chinese appears on
    /// the second tap. Run and confirmed failing.
    @Test("cycling never reaches a language Settings switched off")
    func cyclingRespectsSettings() {
        let model = makeModel()
        model.settings.enabledLanguages = [.en, .ja]
        model.settings.language = .en

        var visited: Set<Language> = [model.settings.language]
        for _ in 0..<10 {
            model.cycleLanguage()
            visited.insert(model.settings.language)
        }
        #expect(visited == [.en, .ja], "the toggle reached \(visited.sorted { $0.rawValue < $1.rawValue })")
        // And it really does alternate rather than sitting still — a toggle that
        // never moved would also satisfy a subset check.
        model.settings.language = .en
        model.cycleLanguage()
        #expect(model.settings.language == .ja)
    }

    /// One language left on is the case where a cycle has nothing to cycle to.
    /// The button must not be left looking tappable and doing nothing, so the
    /// model says so and the view disables it.
    ///
    /// Mutation: change `canCycleLanguage` to `true`. The second expectation
    /// fails and the header ships a control that answers every tap with silence.
    @Test("with one language enabled the toggle reports that it cannot cycle")
    func oneLanguageCannotCycle() {
        let model = makeModel()
        #expect(model.canCycleLanguage, "five languages are enabled by default")

        model.settings.enabledLanguages = [.ms]
        #expect(model.availableLanguages.map(\.id) == [.ms], "setup: only Malay should be left")
        #expect(!model.canCycleLanguage)
        // And if it is tapped anyway, it stays put rather than falling off the end.
        model.cycleLanguage()
        #expect(model.settings.language == .ms)
    }

    /// The impossible case, made possible: a current language outside the enabled
    /// list. `SettingsStore` re-resolves it, so the model can never be asked this
    /// — but the pure function is where the recovery lives and it is the only
    /// place it can be exercised.
    ///
    /// Mutation: make the `guard`'s else branch `return current`. An off-list
    /// language then never recovers and the toggle is dead for that parent.
    @Test("a language that is not in the enabled list recovers to the first one")
    func offListLanguageRecovers() {
        // Tagalog is a real case, and deliberately not in the list handed in.
        #expect(AppModel.nextLanguage(after: .tl, in: [.en, .zh]) == .en)
        // An empty list has no answer but the one it was given.
        #expect(AppModel.nextLanguage(after: .tl, in: []) == .tl)
    }
}
