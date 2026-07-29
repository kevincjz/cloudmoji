import Foundation
import Observation

/// What a purchase or restore attempt came back with.
///
/// `.pending` is Ask-to-Buy: a child on a Family Sharing account taps unlock, a
/// parent gets the approval prompt on their own phone, and nothing happens here
/// for minutes or days. It is a real outcome, not an error, and Settings says
/// "waiting for approval" rather than "something went wrong".
public enum PurchaseOutcome: Sendable, Hashable {
    case unlocked
    case pending
    case cancelled
    case failed
}

/// The one thing the app asks about the extra mini-apps: are they on?
///
/// A protocol rather than a concrete type so that StoreKit can land later
/// without a single view changing. `StubEntitlementStore` below is the whole
/// implementation today; `StoreEntitlementStore` will be the second one, and the
/// launcher, the tiles and Settings will not know which one they are holding.
///
/// `AnyObject` because the store outlives any view that reads it, and
/// `Observable` because SwiftUI has to redraw the launcher when it flips.
@MainActor
public protocol EntitlementProviding: AnyObject, Observable {
    /// Whether the premium mini-apps are available.
    var isUnlocked: Bool { get }
    /// Localised price, or `nil` when there is nothing to price yet. The stub
    /// always returns `nil`, which is what keeps Settings from advertising a
    /// number no App Store product has agreed to.
    var priceText: String? { get }
    func purchase() async -> PurchaseOutcome
    func restore() async -> PurchaseOutcome
    /// Starts watching for entitlement changes made elsewhere — another device,
    /// an Ask-to-Buy approval, a refund. A no-op in the stub.
    func startObserving()
}

/// The pre-StoreKit entitlement: a single persisted flag, and no money anywhere.
///
/// **It defaults to unlocked.** There is no product in App Store Connect yet, so
/// a default of `false` would hide three finished mini-apps behind a button that
/// cannot do anything — which is the failure state `CLAUDE.md` rule 4 forbids,
/// aimed at the parent instead of the child. When `StoreEntitlementStore`
/// arrives the truth moves to `Transaction.currentEntitlements` and this key
/// demotes to a fast-boot cache.
///
/// Reads in `init`, writes in `didSet` — `SettingsStore`'s exact pattern, and
/// for the same two reasons: `-cm_premium_unlocked NO` through
/// `NSArgumentDomain` pins it for a UI test, and
/// `-cm_reset_persisted_settings YES` wipes it with everything else.
@MainActor
@Observable
public final class StubEntitlementStore: EntitlementProviding {

    /// Named here rather than spelled at each call site: the privacy copy in
    /// `AboutView` enumerates the keys this app writes, and a key that is only a
    /// string literal is one nobody can find when that list has to be checked.
    public static let storageKey = "cm_premium_unlocked"

    @ObservationIgnored private let defaults: UserDefaults

    public var isUnlocked: Bool {
        didSet { defaults.set(isUnlocked, forKey: Self.storageKey) }
    }

    /// Nothing to price until there is a product. See the type's note.
    public var priceText: String? { nil }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.isUnlocked = Self.readUnlocked(from: defaults)
    }

    /// Absent means unlocked; present means whatever it says.
    ///
    /// Read through `bool(forKey:)` rather than `object(forKey:) as? Bool`,
    /// because `NSArgumentDomain` stores `-cm_premium_unlocked NO` as the
    /// *string* "NO" — which casts to `nil` and would quietly hand every UI test
    /// the default instead of the value it asked for.
    ///
    /// Static and pure so both halves can be tested without a live store.
    public static func readUnlocked(from defaults: UserDefaults) -> Bool {
        guard defaults.object(forKey: storageKey) != nil else { return true }
        return defaults.bool(forKey: storageKey)
    }

    public func purchase() async -> PurchaseOutcome {
        isUnlocked = true
        return .unlocked
    }

    public func restore() async -> PurchaseOutcome {
        isUnlocked = true
        return .unlocked
    }

    public func startObserving() {}
}
