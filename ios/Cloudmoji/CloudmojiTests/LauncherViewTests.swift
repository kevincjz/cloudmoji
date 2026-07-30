import Foundation
import SwiftUI
import Testing
import CloudmojiCore
@testable import Cloudmoji

@Suite("Launcher")
@MainActor
struct LauncherViewTests {

    private func makeModel(unlocked: Bool) -> AppModel {
        let defaults = UserDefaults(suiteName: "launcher-tests-\(UUID().uuidString)")!
        defaults.set(unlocked, forKey: StubEntitlementStore.storageKey)
        return AppModel(
            settings: SettingsStore(defaults: defaults),
            entitlements: StubEntitlementStore(defaults: defaults)
        )
    }

    // MARK: - Arrangement

    /// The launcher now uses the Home Screen's stable four-column rhythm in
    /// both orientations. The `LazyVGrid` owns order and leaves a partial final
    /// row left-aligned instead of re-centering every icon when one disappears.
    @Test("the Home Screen rhythm is four columns in both layouts")
    func columnCounts() {
        #expect(LauncherView.columns(compact: false) == 4)
        #expect(LauncherView.columns(compact: true) == 4)
    }

    /// The visible squircle became smaller, but the full icon-and-label cell is
    /// still the child's target. Measuring the rendered view protects the
    /// distinction between those two sizes.
    @Test("a Home Screen icon cell remains child-sized")
    func iconCellMeetsTheTouchTargetFloor() {
        let bitmap = Bitmap.rendered(
            LauncherTile(app: .words, label: "Words", onTap: {})
                .frame(width: 80)
        )
        #expect(bitmap.width >= 64, "the icon cell is \(bitmap.width)pt wide")
        #expect(bitmap.height >= 64, "the icon cell is \(bitmap.height)pt tall")
    }

    /// The free launcher's parent doorway occupies the same generous grid cell
    /// as a mini-app even though it is not itself child content.
    @Test("the Full discovery doorway remains child-sized")
    func fullDiscoveryDoorMeetsTheTouchTargetFloor() {
        let bitmap = Bitmap.rendered(
            LauncherFullCloudmojiDoor(onTap: {})
                .frame(width: 80)
        )
        #expect(bitmap.width >= 64, "the grown-up door is \(bitmap.width)pt wide")
        #expect(bitmap.height >= 64, "the grown-up door is \(bitmap.height)pt tall")
    }

    /// The replay affordance is a launcher widget now, not a small overlay over
    /// the brand. It keeps the child-facing touch-target floor while spanning
    /// the available launcher column.
    @Test("the voice-message widget is broad and child-sized")
    func voiceMessageWidgetIsBroadAndChildSized() {
        let bitmap = Bitmap.rendered(
            VoiceMessagePill(isPlaying: false, onTap: {})
                .frame(width: 350)
        )
        #expect(bitmap.width == 350, "the widget collapsed to \(bitmap.width)pt wide")
        #expect(bitmap.height >= 64, "the widget is only \(bitmap.height)pt tall")
    }

    // MARK: - What is on the launcher

    /// The entitlement decides, in one place, and every premium mini-app relies
    /// on it rather than carrying its own branch.
    ///
    /// Mutation: return `MiniApp.allCases` unconditionally from
    /// `visibleMiniApps`. The locked case fails.
    @Test("the extras are on the launcher only when they are unlocked")
    func visibilityFollowsTheEntitlement() {
        let locked = makeModel(unlocked: false)
        #expect(locked.visibleMiniApps.count == 2)
        #expect(locked.visibleMiniApps.allSatisfy { !$0.requiresFull })
        #expect(!locked.visibleMiniApps.contains(.flashCards))
        #expect(!locked.visibleMiniApps.contains(.instrument))
        #expect(!locked.visibleMiniApps.contains(.sleepy))

        let unlocked = makeModel(unlocked: true)
        #expect(unlocked.visibleMiniApps.count == 7)
        #expect(unlocked.visibleMiniApps == MiniApp.allCases)
    }

    /// The deterministic stub defaults to unlocked so previews and legacy unit
    /// tests continue to exercise the complete product. Production never uses
    /// this flag as an access grant.
    ///
    /// Mutation: drop the `object(forKey:) != nil` guard in `readUnlocked` so a
    /// missing key falls through to `bool(forKey:)`, which is false. The first
    /// expectation fails.
    @Test("a fresh install is unlocked, and a pinned NO is respected")
    func stubDefaultsToUnlocked() {
        let fresh = UserDefaults(suiteName: "entitlement-fresh-\(UUID().uuidString)")!
        #expect(StubEntitlementStore.readUnlocked(from: fresh))

        // `NSArgumentDomain` stores `-cm_premium_unlocked NO` as the *string*
        // "NO", which is why this is read through `bool(forKey:)` rather than an
        // `as? Bool` cast.
        let pinned = UserDefaults(suiteName: "entitlement-pinned-\(UUID().uuidString)")!
        pinned.set("NO", forKey: StubEntitlementStore.storageKey)
        #expect(!StubEntitlementStore.readUnlocked(from: pinned))
    }

    /// Buying persists, so the next launch does not ask again.
    ///
    /// Mutation: delete the `didSet` on `isUnlocked`. This fails.
    @Test("unlocking is written down")
    func purchasePersists() async {
        let defaults = UserDefaults(suiteName: "entitlement-buy-\(UUID().uuidString)")!
        defaults.set(false, forKey: StubEntitlementStore.storageKey)
        let store = StubEntitlementStore(defaults: defaults)
        #expect(!store.isUnlocked)

        #expect(await store.purchase() == .unlocked)
        #expect(store.isUnlocked)
        #expect(StubEntitlementStore.readUnlocked(from: defaults))
    }

    // MARK: - The tiles themselves

    /// The raw values are three contracts at once: the `launcher-tile-<raw>`
    /// identifiers `LauncherUITests` looks up, the `-cm_open <raw>` deep link
    /// every UI suite launches through, and Words and Count keeping the values
    /// `AppMode` gave them.
    ///
    /// Mutation: rename `flashCards`' raw value. This fails, and so does the UI
    /// suite that opens it — which is the point of writing them down twice.
    @Test("the raw values are the identifiers and the deep-link names")
    func rawValuesAreTheContract() {
        #expect(MiniApp.words.rawValue == "words")
        #expect(MiniApp.count.rawValue == "count")
        #expect(MiniApp.flashCards.rawValue == "flashcards")
        #expect(MiniApp.animalSounds.rawValue == "animalsounds")

        let raws = MiniApp.allCases.map(\.rawValue)
        #expect(Set(raws).count == raws.count, "duplicate raw values in \(raws)")
        for raw in raws {
            #expect(raw.allSatisfy { $0.isASCII && $0.isLowercase },
                    "\"\(raw)\" is not a plain lowercase identifier")
            // Every one of them has to round-trip, or the deep link silently
            // lands on the launcher and a whole UI suite measures the wrong
            // screen while passing.
            #expect(MiniApp(rawValue: raw) != nil)
        }
    }

    /// Exactly five Full mini-apps, matching the paywall, Settings and About copy.
    ///
    /// Mutation: make Music or Sleepy Cloud free. This fails.
    @Test("five mini-apps require Full and two are free")
    func premiumSetIsTheDocumentedOne() {
        #expect(
            Set(MiniApp.allCases.filter(\.requiresFull))
                == Set([.instrument, .flashCards, .animalSounds, .photos, .sleepy])
        )
        #expect(MiniApp.allCases.filter { !$0.requiresFull } == [.words, .count])
    }

    /// Every tile has a caption in every language, and none of them falls back
    /// to a raw value — which is what a missing row would look like on screen.
    ///
    /// Mutation: delete the `.zh` entry for any mini-app. This fails and names it.
    @Test("every tile is captioned in all five languages")
    func labelsCoverEveryLanguage() {
        for app in MiniApp.allCases {
            for language in Language.allCases {
                let label = app.label(language)
                #expect(!label.isEmpty)
                #expect(label != app.rawValue,
                        "\(app.rawValue) has no \(language.rawValue) caption and fell back to its raw value")
            }
        }
    }
}

@Suite("Mini-app availability")
@MainActor
struct MiniAppAvailabilityTests {

    private func makeModel(categories: Set<CloudmojiCore.Category>) -> AppModel {
        let defaults = UserDefaults(suiteName: "availability-\(UUID().uuidString)")!
        let settings = SettingsStore(defaults: defaults)
        settings.enabledCategories = categories
        return AppModel(settings: settings, entitlements: StubEntitlementStore(defaults: defaults))
    }

    /// **Animal Sounds *is* the animals category.** A parent who switched animals
    /// off has said they do not want them, and this mini-app has nothing else to
    /// show — it used to draw an empty grid, which is the failure state
    /// `CLAUDE.md` rule 4 forbids, reached through a setting a parent can flip in
    /// four taps.
    ///
    /// Mutation: drop the `app != .animalSounds || hasAnimals` clause from
    /// `visibleMiniApps`. The first expectation fails.
    @Test("the Animals tile goes away when the Animals category is switched off")
    func animalsTileFollowsTheCategory() {
        let without = makeModel(categories: [.fruits, .food, .vehicles])
        #expect(!without.visibleMiniApps.contains(.animalSounds),
                "the Animals mini-app is on the launcher with no animals to put in it")
        // Everything else is untouched — this is one tile, not a general rule.
        #expect(without.visibleMiniApps.contains(.words))
        #expect(without.visibleMiniApps.contains(.count))
        #expect(without.visibleMiniApps.count == 6)

        let with = makeModel(categories: [.animals, .fruits])
        #expect(with.visibleMiniApps.contains(.animalSounds))
        #expect(with.visibleMiniApps.count == 7)
    }

    /// The grid narrows to the sound library once there is one, and never to
    /// nothing.
    ///
    /// Mutation: drop the `matched.isEmpty ? animals : matched` fallback. The
    /// mismatched-library case comes back empty and fails.
    @Test("the animal grid follows the recordings, and is never empty")
    func gridFollowsTheRecordings() {
        let dog = EmojiEntry(emoji: "🐶", cat: .animals, en: "dog", zh: "狗", ms: "anjing", ja: "いぬ", tl: "aso")
        let cat = EmojiEntry(emoji: "🐱", cat: .animals, en: "cat", zh: "猫", ms: "kucing", ja: "ねこ", tl: "pusa")
        let bee = EmojiEntry(emoji: "🐝", cat: .animals, en: "bee", zh: "蜜蜂", ms: "lebah", ja: "はち", tl: "bubuyog")
        let pool = [dog, cat, bee]

        // No library yet: everything, so no tile is ever dead.
        #expect(AnimalSoundsView.grid(from: pool, withRecordings: []) == pool)

        // A library: exactly what it covers.
        #expect(AnimalSoundsView.grid(from: pool, withRecordings: ["🐶", "🐝"]) == [dog, bee])

        // A library that covers nothing in the pool must not blank the screen.
        #expect(AnimalSoundsView.grid(from: pool, withRecordings: ["🦄"]) == pool)
    }
}
