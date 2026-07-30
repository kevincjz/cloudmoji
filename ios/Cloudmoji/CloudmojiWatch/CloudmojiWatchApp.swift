import SwiftUI
import CloudmojiCore

@main
struct CloudmojiWatchApp: App {
    /// Assigned in `init` so the connection is up before the first view appears
    /// and any queued context from the phone is applied on launch.
    @State private var model: WatchModel

    init() {
        let entitlements = StoreEntitlementStore()
        let model = WatchModel(entitlements: entitlements)
        _model = State(initialValue: model)
        entitlements.startObserving()
    }

    var body: some Scene {
        WindowGroup {
            WatchRootView().environment(model)
        }
    }
}

private struct WatchRootView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        Group {
            if model.entitlements.isUnlocked {
                PocketCloudView()
            } else {
                LockedWatchView()
            }
        }
        .onChange(of: model.entitlements.isUnlocked) { _, isFull in
            model.handleAccessChange(isFull: isFull)
        }
        .task {
            model.handleAccessChange(isFull: model.entitlements.isUnlocked)
        }
    }
}

private struct LockedWatchView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 9) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 25, weight: .black))
                    .foregroundStyle(WatchTheme.teal)

                Text("Full Cloudmoji required")
                    .font(.system(.headline, design: .rounded).weight(.heavy))
                    .multilineTextAlignment(.center)

                Text("Unlock on your iPhone to share emoji moments and send short voice notes.")
                    .font(.system(.footnote, design: .rounded).weight(.semibold))
                    .foregroundStyle(.white.opacity(0.72))
                    .multilineTextAlignment(.center)

                Text("Cloudmoji → Grown-ups → Full Cloudmoji")
                    .font(.system(.caption2, design: .rounded).weight(.bold))
                    .foregroundStyle(WatchTheme.teal)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 12)
        }
        .background(WatchTheme.background.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("watch-full-required")
    }
}
