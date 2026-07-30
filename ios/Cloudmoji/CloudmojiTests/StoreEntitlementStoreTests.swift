import StoreKit
import StoreKitTest
import XCTest
import CloudmojiCore

/// End-to-end StoreKit 2 coverage using Xcode's local store.
///
/// These tests exercise the same `Product`, verified `Transaction`, restore,
/// and revocation APIs as production. The launch-argument stub remains useful
/// for deterministic UI states, but it cannot substitute for this suite.
@MainActor
final class StoreEntitlementStoreTests: XCTestCase {
    private func makeSession() throws -> SKTestSession {
        let session = try SKTestSession(configurationFileNamed: "Cloudmoji")
        session.resetToDefaultState()
        session.clearTransactions()
        session.disableDialogs = true
        return session
    }

    /// One serial lifecycle because StoreKit Test supports one active local
    /// environment per process. Keeping catalog, purchase, revocation and
    /// restore together prevents XCTest's parallel runner from making several
    /// `SKTestSession`s compete for that environment.
    func testPurchaseRestoreAndRefundLifecycle() async throws {
        let os = ProcessInfo.processInfo.operatingSystemVersion
        if os.majorVersion == 26, os.minorVersion == 5 {
            throw XCTSkip(
                "iOS 26.5 rejects SKTestSession mutations with "
                + "SKInternalErrorDomain Code 3. Run this lifecycle on an "
                + "iOS 18–26.4 or 26.6+ simulator."
            )
        }

        let session = try makeSession()
        let store = StoreEntitlementStore()
        store.startObserving()

        await store.refresh()
        await store.reloadProduct()

        XCTAssertEqual(store.accessState, .locked)
        XCTAssertEqual(store.productState, .available)
        XCTAssertEqual(store.priceText, "$9.99")

        let outcome = await store.purchase()

        XCTAssertEqual(outcome, .unlocked)
        XCTAssertEqual(store.accessState, .unlocked)

        var purchasedID: UInt64?
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result,
                  transaction.productID == StoreEntitlementStore.productID else {
                continue
            }
            purchasedID = transaction.id
        }
        try session.refundTransaction(identifier: UInt(try XCTUnwrap(purchasedID)))

        // A refund occurs outside the app. Production must relock from
        // `Transaction.updates` while the app remains open; calling `refresh()`
        // here would only prove the foreground/relaunch fallback.
        let clock = ContinuousClock()
        let refundDeadline = clock.now + .seconds(5)
        while store.accessState != .locked, clock.now < refundDeadline {
            try await Task.sleep(for: .milliseconds(50))
        }
        XCTAssertEqual(store.accessState, .locked)

        _ = try await session.buyProduct(
            identifier: StoreEntitlementStore.productID
        )
        let restoredStore = StoreEntitlementStore()
        restoredStore.startObserving()
        let restoreOutcome = await restoredStore.restore()

        XCTAssertEqual(restoreOutcome, .unlocked)
        XCTAssertTrue(restoredStore.isUnlocked)
    }
}
