import XCTest
import CloudmojiCore

/// Deterministic coverage for every paywall result the UI must explain.
///
/// `StoreEntitlementStoreTests` remains the verified-transaction lifecycle.
/// These tests cover presentation states without depending on a simulator
/// StoreKit daemon, which is especially important while iOS 26.5 cannot mutate
/// `SKTestSession` from command-line test runs.
@MainActor
final class StubEntitlementStoreTests: XCTestCase {
    private var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: "StubEntitlementStoreTests")
        defaults.removePersistentDomain(forName: "StubEntitlementStoreTests")
        defaults.set(false, forKey: StubEntitlementStore.storageKey)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: "StubEntitlementStoreTests")
        defaults = nil
        super.tearDown()
    }

    func testSuccessfulPurchaseUnlocksAndExposesLocalizedPrice() async {
        let store = StubEntitlementStore(defaults: defaults, priceText: "$9.99")

        XCTAssertEqual(store.accessState, .locked)
        XCTAssertEqual(store.productState, .available)
        XCTAssertEqual(store.priceText, "$9.99")

        let outcome = await store.purchase()
        XCTAssertEqual(outcome, .unlocked)
        XCTAssertEqual(store.accessState, .unlocked)
        XCTAssertNil(store.notice)
    }

    func testPendingPurchaseDoesNotGrantAccess() async {
        defaults.set("pending", forKey: StubEntitlementStore.purchaseOutcomeKey)
        let store = StubEntitlementStore(defaults: defaults)

        let outcome = await store.purchase()
        XCTAssertEqual(outcome, .pending)
        XCTAssertFalse(store.isUnlocked)
        XCTAssertEqual(store.operationState, .pending)
    }

    func testPurchaseFailureLeavesFreeVersionAvailable() async {
        defaults.set("failed", forKey: StubEntitlementStore.purchaseOutcomeKey)
        let store = StubEntitlementStore(defaults: defaults)

        let outcome = await store.purchase()
        XCTAssertEqual(outcome, .failed)
        XCTAssertFalse(store.isUnlocked)
        XCTAssertEqual(store.notice, .purchaseFailed)
    }

    func testRestoreNotFoundDoesNotGrantAccess() async {
        defaults.set("notfound", forKey: StubEntitlementStore.restoreOutcomeKey)
        let store = StubEntitlementStore(defaults: defaults)

        let outcome = await store.restore()
        XCTAssertEqual(outcome, .notFound)
        XCTAssertFalse(store.isUnlocked)
        XCTAssertEqual(store.notice, .restoreNotFound)
    }

    func testRestoreSuccessUnlocks() async {
        defaults.set("unlocked", forKey: StubEntitlementStore.restoreOutcomeKey)
        let store = StubEntitlementStore(defaults: defaults)

        let outcome = await store.restore()
        XCTAssertEqual(outcome, .unlocked)
        XCTAssertTrue(store.isUnlocked)
        XCTAssertNil(store.notice)
    }

    func testUnavailableProductHasNoMadeUpPrice() {
        defaults.set(true, forKey: StubEntitlementStore.productUnavailableKey)
        let store = StubEntitlementStore(defaults: defaults)

        XCTAssertEqual(store.productState, .unavailable)
        XCTAssertNil(store.priceText)
    }
}
