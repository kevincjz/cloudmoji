import Foundation
import CloudmojiCore

/// Which mini-apps the launcher may draw.
///
/// An extension in the app target rather than a property on `AppModel` proper,
/// because `MiniApp` is a view-layer type and must not leak into `CloudmojiCore`
/// — the package is shared with a future watch target that has no launcher.
extension AppModel {
    /// The free four when the extras are locked, all seven when they are not.
    ///
    /// One filter, in one place, is what keeps every premium mini-app from
    /// carrying its own entitlement branch: `FlashCardsView` never asks whether
    /// it is allowed to be on screen, because a locked launcher never opens it.
    var visibleMiniApps: [MiniApp] {
        let unlocked = entitlements.isUnlocked
        let hasAnimals = settings.enabledCategories.contains(.animals)
        return MiniApp.allCases.filter { app in
            guard !app.isPremium || unlocked else { return false }
            // Animal Sounds *is* the animals category. A parent who switched
            // animals off in Settings has said they do not want them, and the
            // mini-app has nothing else to show — it used to render a blank
            // grid, which is the failure state `CLAUDE.md` rule 4 forbids.
            // Hiding the tile is both the honest reading of the setting and the
            // only answer that leaves nothing empty on screen.
            guard app != .animalSounds || hasAnimals else { return false }
            return true
        }
    }
}
