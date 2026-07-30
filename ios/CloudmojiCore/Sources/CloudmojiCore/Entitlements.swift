import Foundation
import Observation
import StoreKit

public enum PurchaseOutcome: Sendable, Hashable {
    case unlocked
    case pending
    case cancelled
    case notFound
    case failed
}

public enum EntitlementAccessState: Sendable, Hashable {
    case checking
    case locked
    case unlocked
}

public enum EntitlementProductState: Sendable, Hashable {
    case loading
    case available
    case unavailable
}

public enum EntitlementOperationState: Sendable, Hashable {
    case idle
    case purchasing
    case pending
    case restoring

    public var isBusy: Bool {
        self == .purchasing || self == .restoring
    }
}

public enum EntitlementNotice: Sendable, Hashable {
    case purchaseFailed
    case restoreFailed
    case restoreNotFound
}

/// The one commerce seam shared by iPhone, iPad, and Apple Watch.
///
/// Views ask this object for presentation state, while access itself is always
/// the single `isUnlocked` answer. The production implementation below derives
/// that answer only from verified StoreKit transactions. The stub is retained
/// for previews, unit tests, and explicit Debug UI-test launches.
@MainActor
public protocol EntitlementProviding: AnyObject, Observable {
    var accessState: EntitlementAccessState { get }
    var isUnlocked: Bool { get }
    var productState: EntitlementProductState { get }
    var operationState: EntitlementOperationState { get }
    var notice: EntitlementNotice? { get }
    var priceText: String? { get }

    func purchase() async -> PurchaseOutcome
    func restore() async -> PurchaseOutcome
    func refresh() async
    func reloadProduct() async
    func startObserving()
}

/// StoreKit 2 source of truth for Full Cloudmoji.
///
/// No UserDefaults Boolean grants access here. `Transaction.currentEntitlements`
/// is maintained by StoreKit on the device and remains available for an offline
/// relaunch after a verified purchase.
@MainActor
@Observable
public final class StoreEntitlementStore: EntitlementProviding {
    public static let productID = "app.cloudmoji.unlock.full"

    public private(set) var accessState: EntitlementAccessState = .checking
    public private(set) var productState: EntitlementProductState = .loading
    public private(set) var operationState: EntitlementOperationState = .idle
    public private(set) var notice: EntitlementNotice?

    public var isUnlocked: Bool { accessState == .unlocked }
    public var priceText: String? { product?.displayPrice }

    @ObservationIgnored private var product: Product?
    @ObservationIgnored private var updatesTask: Task<Void, Never>?
    @ObservationIgnored private var startupTask: Task<Void, Never>?
    /// A revocation update can arrive a fraction before
    /// `Transaction.currentEntitlements` drops the old transaction. Remembering
    /// the verified ID prevents that stale snapshot from immediately granting
    /// access again. A later verified granting update for the same ID removes it.
    @ObservationIgnored private var revokedTransactionIDs: Set<UInt64> = []

    public init() {}

    deinit {
        updatesTask?.cancel()
        startupTask?.cancel()
    }

    public func startObserving() {
        guard updatesTask == nil else { return }

        // Start this before the initial scan so a purchase or revocation cannot
        // fall into the gap between launch and current-entitlement reconciliation.
        updatesTask = Task { [weak self] in
            for await update in Transaction.updates {
                guard !Task.isCancelled else { return }
                await self?.consume(update)
            }
        }

        startupTask = Task { [weak self] in
            guard let self else { return }
            await self.refresh()
            await self.reloadProduct()
        }
    }

    public func refresh() async {
        await finishRelevantUnfinishedTransactions()

        var hasFull = false
        for await result in Transaction.currentEntitlements {
            guard let transaction = verified(result),
                  transaction.productID == Self.productID else { continue }
            if grantsAccess(transaction) {
                hasFull = true
            }
        }

        accessState = hasFull ? .unlocked : .locked
        if hasFull {
            operationState = .idle
            notice = nil
        }
    }

    public func reloadProduct() async {
        productState = .loading
        do {
            let products = try await Product.products(for: [Self.productID])
            product = products.first { $0.id == Self.productID }
            productState = product == nil ? .unavailable : .available
        } catch {
            product = nil
            productState = .unavailable
        }
    }

    public func purchase() async -> PurchaseOutcome {
        notice = nil
        guard let product else {
            productState = .unavailable
            return .failed
        }

        operationState = .purchasing
        do {
            switch try await product.purchase() {
            case .success(let result):
                guard let transaction = verified(result),
                      transaction.productID == Self.productID,
                      transactionGrantsAccess(transaction) else {
                    operationState = .idle
                    notice = .purchaseFailed
                    return .failed
                }
                revokedTransactionIDs.remove(transaction.id)
                accessState = .unlocked
                operationState = .idle
                notice = nil
                await transaction.finish()
                return .unlocked

            case .pending:
                operationState = .pending
                return .pending

            case .userCancelled:
                operationState = .idle
                return .cancelled

            @unknown default:
                operationState = .idle
                notice = .purchaseFailed
                return .failed
            }
        } catch {
            operationState = .idle
            notice = .purchaseFailed
            return .failed
        }
    }

    public func restore() async -> PurchaseOutcome {
        notice = nil
        operationState = .restoring

        do {
            try await AppStore.sync()
            await refresh()
            operationState = .idle
            if isUnlocked {
                return .unlocked
            }
            notice = .restoreNotFound
            return .notFound
        } catch {
            operationState = .idle
            notice = .restoreFailed
            return .failed
        }
    }

    private func finishRelevantUnfinishedTransactions() async {
        for await result in Transaction.unfinished {
            guard let transaction = verified(result),
                  transaction.productID == Self.productID else { continue }

            if transactionGrantsAccess(transaction) {
                guard !revokedTransactionIDs.contains(transaction.id) else {
                    await transaction.finish()
                    continue
                }
                accessState = .unlocked
            } else {
                revokedTransactionIDs.insert(transaction.id)
            }
            await transaction.finish()
        }
    }

    private func consume(_ result: VerificationResult<Transaction>) async {
        guard let transaction = verified(result),
              transaction.productID == Self.productID else { return }

        if transactionGrantsAccess(transaction) {
            // This result came from the live update stream, rather than a
            // possibly lagging entitlement scan, so it is authoritative for
            // undoing a previous revocation of the same transaction.
            revokedTransactionIDs.remove(transaction.id)
            accessState = .unlocked
            operationState = .idle
            notice = nil
        } else {
            // Record the verified revocation before scanning. StoreKit may emit
            // this update just before `currentEntitlements` stops returning its
            // earlier snapshot; the ID guard prevents that stale item from
            // re-unlocking Full.
            revokedTransactionIDs.insert(transaction.id)
            accessState = .locked
            operationState = .idle
            notice = nil
            await transaction.finish()

            // Another current transaction (for example a later repurchase) may
            // still grant the product, so reconcile the complete set after the
            // immediate relock.
            await refresh()
            return
        }
        await transaction.finish()
    }

    private func grantsAccess(_ transaction: Transaction) -> Bool {
        transactionGrantsAccess(transaction)
            && !revokedTransactionIDs.contains(transaction.id)
    }

    private func transactionGrantsAccess(_ transaction: Transaction) -> Bool {
        transaction.revocationDate == nil && !transaction.isUpgraded
    }

    private func verified(
        _ result: VerificationResult<Transaction>
    ) -> Transaction? {
        switch result {
        case .verified(let transaction):
            transaction
        case .unverified:
            nil
        }
    }
}

/// Deterministic entitlement for previews and tests.
///
/// It defaults to unlocked so app-target unit tests and previews continue to
/// exercise all seven completed mini-apps. The shipping app creates this store
/// only when the explicit Debug `cm_use_stub_entitlements` switch is present.
@MainActor
@Observable
public final class StubEntitlementStore: EntitlementProviding {
    public static let storageKey = "cm_premium_unlocked"
    public static let priceKey = "cm_stub_price"
    public static let productUnavailableKey = "cm_stub_product_unavailable"
    public static let purchaseOutcomeKey = "cm_stub_purchase_outcome"
    public static let restoreOutcomeKey = "cm_stub_restore_outcome"

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let fallbackPrice: String?

    public var isUnlocked: Bool {
        didSet { defaults.set(isUnlocked, forKey: Self.storageKey) }
    }

    public var accessState: EntitlementAccessState {
        isUnlocked ? .unlocked : .locked
    }

    public private(set) var productState: EntitlementProductState
    public private(set) var operationState: EntitlementOperationState = .idle
    public private(set) var notice: EntitlementNotice?

    public var priceText: String? {
        guard productState == .available else { return nil }
        return defaults.string(forKey: Self.priceKey) ?? fallbackPrice
    }

    public init(
        defaults: UserDefaults = .standard,
        priceText: String? = "$9.99"
    ) {
        self.defaults = defaults
        self.fallbackPrice = priceText
        self.isUnlocked = Self.readUnlocked(from: defaults)
        self.productState = defaults.bool(forKey: Self.productUnavailableKey)
            ? .unavailable
            : (priceText == nil ? .unavailable : .available)
    }

    public static func readUnlocked(from defaults: UserDefaults) -> Bool {
        guard defaults.object(forKey: storageKey) != nil else { return true }
        return defaults.bool(forKey: storageKey)
    }

    public func purchase() async -> PurchaseOutcome {
        notice = nil
        operationState = .purchasing
        let outcome = stubOutcome(
            defaults.string(forKey: Self.purchaseOutcomeKey),
            default: .unlocked
        )
        apply(outcome, restoring: false)
        return outcome
    }

    public func restore() async -> PurchaseOutcome {
        notice = nil
        operationState = .restoring
        let fallback: PurchaseOutcome = isUnlocked ? .unlocked : .notFound
        let outcome = stubOutcome(
            defaults.string(forKey: Self.restoreOutcomeKey),
            default: fallback
        )
        apply(outcome, restoring: true)
        return outcome
    }

    public func refresh() async {}

    public func reloadProduct() async {
        productState = defaults.bool(forKey: Self.productUnavailableKey)
            ? .unavailable
            : (priceText == nil && fallbackPrice == nil ? .unavailable : .available)
    }

    public func startObserving() {}

    private func apply(_ outcome: PurchaseOutcome, restoring: Bool) {
        switch outcome {
        case .unlocked:
            isUnlocked = true
            operationState = .idle
        case .pending:
            operationState = .pending
        case .cancelled:
            operationState = .idle
        case .notFound:
            operationState = .idle
            notice = .restoreNotFound
        case .failed:
            operationState = .idle
            notice = restoring ? .restoreFailed : .purchaseFailed
        }
    }

    private func stubOutcome(
        _ raw: String?,
        default fallback: PurchaseOutcome
    ) -> PurchaseOutcome {
        switch raw?.lowercased() {
        case "unlocked", "success": .unlocked
        case "pending": .pending
        case "cancelled", "canceled": .cancelled
        case "notfound", "not-found": .notFound
        case "failed", "failure": .failed
        default: fallback
        }
    }
}
