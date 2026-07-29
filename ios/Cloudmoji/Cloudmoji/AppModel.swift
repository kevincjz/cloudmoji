import Foundation
import Observation
import CloudmojiCore

/// `objc/runtime.h` also declares a `Category` (`typedef struct objc_category *`),
/// which Foundation drags in, so the bare name is ambiguous everywhere in the app
/// target. Declaring it once here fixes it for the whole module — a declaration in
/// the current module wins over an imported one — so the views can keep writing
/// `Category` rather than qualifying every mention.
typealias Category = CloudmojiCore.Category

/// One category's worth of the continuous emoji list, with the tab that names
/// it. Mirrors the web's `SECTION_CATEGORIES` join in `src/data/emojis.ts`.
///
/// The id is the tab's, so it doubles as the scroll target a chip jumps to.
struct EmojiSection: Identifiable, Equatable {
    let tab: CategoryTab
    let entries: [EmojiEntry]

    var id: String { tab.id }
}

/// Everything the views read. Settings filtering happens here, so a view never
/// branches on a setting — it consumes an already-narrowed list.
///
/// `@MainActor` because SwiftUI reads it and `SpeechController` is main-actor
/// isolated. Stage 3's WatchConnectivity callbacks arrive off-main and will
/// need to hop here; that is the isolation decision Stage 1 left open.
@MainActor
@Observable
final class AppModel {
    let settings: SettingsStore
    let speech: SpeechController
    let grammar: CountingGrammar
    /// Whether the extra mini-apps are on. Held as an existential so StoreKit
    /// can replace the stub without a view changing — see `EntitlementProviding`.
    let entitlements: any EntitlementProviding
    /// The one owner of `AVAudioSession`, and of the tone engine the instrument
    /// pad and the animal sounds play through. Speech is not routed through it —
    /// see `AudioDirector`.
    let audio: AudioDirector

    private let repository: EmojiRepository
    private let allEmojis: [EmojiEntry]
    /// Glyph → category, built once. `Countable` carries no category of its own,
    /// so narrowing the 84 countables to the categories a parent left enabled
    /// means joining on the glyph against the 200-entry emoji catalogue. Doing
    /// that per lookup would be 200 comparisons per tile per round.
    private let categoryByGlyph: [String: Category]
    /// Retained so the voice cache can be dropped on foreground. `SpeechController`
    /// deliberately knows the engine only as a protocol, so it cannot forward this.
    private let engine: SystemSpeechEngine

    init(
        settings: SettingsStore = SettingsStore(),
        entitlements: any EntitlementProviding = StubEntitlementStore(),
        audio: AudioDirector = AudioDirector()
    ) {
        self.settings = settings
        self.entitlements = entitlements
        self.audio = audio
        // A missing or malformed bundled resource is a build error, not a
        // runtime path — but the child must never see a crash, so an empty
        // repository is the degraded case rather than a trap.
        let repo = (try? EmojiRepository()) ?? .empty
        self.repository = repo
        self.allEmojis = repo.emojis
        // `uniquingKeysWith` rather than a plain `Dictionary(uniqueKeysWithValues:)`:
        // the generator's parity check enforces that a glyph appears once across
        // the whole catalogue, and if that ever stopped being true the first entry
        // is as good an answer as the last. It must not trap.
        self.categoryByGlyph = Dictionary(
            repo.emojis.map { ($0.emoji, $0.cat) },
            uniquingKeysWith: { first, _ in first }
        )
        self.grammar = CountingGrammar(repository: repo)
        let engine = SystemSpeechEngine()
        self.engine = engine
        self.speech = SpeechController(
            resolver: VoiceResolver(languages: repo.languages),
            engine: engine
        )
    }

    var availableLanguages: [LanguageMeta] {
        repository.languages.filter { settings.enabledLanguages.contains($0.id) }
    }

    /// Whether the header's language button does anything. False when the parent
    /// has left exactly one language on — the button stays visible, because it is
    /// the only place the current language is written down, but it is disabled
    /// rather than tappable-and-inert.
    var canCycleLanguage: Bool { availableLanguages.count > 1 }

    /// Advances to the next language the parent left enabled, wrapping at the end.
    ///
    /// Replaces a menu picker: a 27-month-old cannot open a menu, read five rows
    /// and hit one, but he can hit one button repeatedly. Cycling only through
    /// `availableLanguages` is the point of the design — a family that switched
    /// three off gets a two-way toggle.
    func cycleLanguage() {
        settings.language = Self.nextLanguage(
            after: settings.language,
            in: availableLanguages.map(\.id)
        )
    }

    /// Pure, so the wrap-around and the not-in-the-list case can be given inputs
    /// the shipped data cannot produce.
    ///
    /// A current language that is not in `enabled` should be impossible —
    /// `SettingsStore` re-resolves it whenever either side changes — but "should
    /// be impossible" is how the web shipped a stale `es` into `NUMBER_WORDS`.
    /// Landing on the first enabled language is the recovery, and it is the same
    /// answer an empty list gives.
    static func nextLanguage(after current: Language, in enabled: [Language]) -> Language {
        guard let index = enabled.firstIndex(of: current) else { return enabled.first ?? current }
        return enabled[(index + 1) % enabled.count]
    }

    var categories: [CategoryTab] {
        repository.categories.filter { tab in
            guard let category = tab.category else { return true } // "all"
            return settings.enabledCategories.contains(category)
        }
    }

    /// Every language, enabled or not. **Settings only** — it is the one screen
    /// that has to show a parent what they have switched off, so it is the one
    /// screen that reads past the filter. Anything else using this is a bug.
    var allLanguages: [LanguageMeta] { repository.languages }

    /// Every real category, enabled or not, without the "All" tab — "All" is a
    /// view of the grid, not something a parent can switch off.
    var allCategories: [CategoryTab] {
        repository.categories.filter { $0.category != nil }
    }

    /// False when this is the only language left on. `SettingsStore` treats an
    /// empty set as "all of them", which is the right invariant and a baffling
    /// thing for a parent to watch happen — five switches snapping back on after
    /// they turned the last one off. Settings greys the switch instead.
    func canDisableLanguage(_ id: Language) -> Bool {
        settings.enabledLanguages != [id]
    }

    func canDisableCategory(_ id: Category) -> Bool {
        settings.enabledCategories != [id]
    }

    /// `nil` means the "all" tab.
    ///
    /// The enabled check comes first and applies even when an explicit category
    /// is named. Skipping it in that branch looks like a free shortcut — the
    /// tab is already gone from `categories` — but a view can still be holding
    /// the old selection for a frame, and the result would be a grid full of
    /// exactly the content a parent just turned off.
    func emojis(in category: Category?) -> [EmojiEntry] {
        allEmojis.filter { entry in
            guard settings.enabledCategories.contains(entry.cat) else { return false }
            guard let category else { return true }
            return entry.cat == category
        }
    }

    /// The emoji list as the child sees it: one continuous run of every enabled
    /// emoji, cut into a section per category, in catalogue order.
    ///
    /// Built from ``categories`` and ``emojis(in:)``, both of which have already
    /// applied the enabled-set filter — so a category a parent switched off has
    /// no section here, no chip, and no tiles, and the view never branches on a
    /// setting to make that true.
    ///
    /// That is also what deletes a failure state rather than guarding it. When a
    /// chip *filtered* the grid, switching off the category the child was on
    /// left an empty screen — `CLAUDE.md` rule 4 — and `WordsView` carried an
    /// `.onChange` handler to snap the selection back to "All". A section that
    /// does not exist is simply absent from a list that still has seven others
    /// in it, so there is nothing to snap back from.
    ///
    /// The "All" tab is deliberately not a section: in a continuous list every
    /// emoji is already on screen, so "all of them" is not a place to scroll to.
    /// An empty section is dropped too — no category ships empty today, but a
    /// header with nothing under it is a small blank screen of its own.
    var sections: [EmojiSection] {
        categories.compactMap { tab in
            guard let category = tab.category else { return nil }
            let entries = emojis(in: category)
            guard !entries.isEmpty else { return nil }
            return EmojiSection(tab: tab, entries: entries)
        }
    }

    /// The countables Count mode may draw from, narrowed to the categories the
    /// parent left enabled.
    ///
    /// Filtering happens here for the same reason it does for emojis: `CountView`
    /// consumes an already-narrowed catalogue and never branches on a setting.
    var countables: [Countable] {
        Self.narrowed(
            repository.countables,
            to: settings.enabledCategories,
            categoryOf: { categoryByGlyph[$0.emoji] }
        )
    }

    /// The narrowing rule, pure so it can be given cases the shipped data does not
    /// contain.
    ///
    /// Two rules, both deliberate. A countable that maps to **no** category — 🌟
    /// is the only one today, because it is in `countables.ts` and not in
    /// `emojis.ts` — is always available: a parent has no switch that could remove
    /// it, so removing it would be removing something they never asked to lose.
    /// And a narrowing that leaves **nothing** degrades to the whole catalogue
    /// rather than to an empty screen, because a blank Count mode is a failure
    /// state and rule 4 says the child never sees one.
    static func narrowed(
        _ countables: [Countable],
        to categories: Set<Category>,
        categoryOf: (Countable) -> Category?
    ) -> [Countable] {
        let kept = countables.filter { item in
            guard let category = categoryOf(item) else { return true }
            return categories.contains(category)
        }
        return kept.isEmpty ? countables : kept
    }

    /// How high this family counts. `SettingsStore` has already clamped it into
    /// `SettingsStore.countBounds` and un-inverted it, so this needs no checking.
    var countRange: ClosedRange<Int> { settings.countRange }

    /// "three dogs", "三只狗", "いぬ みっつ", "tatlong aso" — built by
    /// `CountingGrammar`, which is where the five languages' rules live and are
    /// tested. This exists so the view never has to know the language either.
    func phrase(for item: Countable, count: Int) -> String {
        grammar.phrase(item, count: count, in: settings.language)
    }

    func word(for entry: EmojiEntry) -> String {
        entry.word(settings.language)
    }

    /// What an animal *says* in the chosen language — "woof woof", 汪汪, ワンワン.
    /// `nil` when this glyph has no noise on file. See `src/data/animalSounds.ts`.
    func animalSound(for glyph: String) -> String? {
        repository.animalSound(for: glyph, in: settings.language)
    }

    /// Every animal with a noise, whatever the language.
    var animalSoundGlyphs: Set<String> { repository.animalSoundGlyphs }

    /// Drops the cached voice list, so the next utterance re-reads what iOS has
    /// installed. Called when the app returns to the foreground.
    func invalidateVoiceCache() {
        engine.invalidateVoiceCache()
    }

    /// The current-language word for a glyph the child tapped earlier.
    ///
    /// The typing row stores glyphs, not entries, and has to be re-read when the
    /// language changes. Glyphs are unique across the catalogue — the generator's
    /// parity check enforces it — so the first match is the only match.
    func word(forEmoji glyph: String) -> String? {
        guard let entry = allEmojis.first(where: { $0.emoji == glyph }) else { return nil }
        return word(for: entry)
    }

    func label(for tab: CategoryTab) -> String {
        tab.label(settings.language)
    }
}
