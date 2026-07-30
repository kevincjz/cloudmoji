import SwiftUI
import CloudmojiCore

/// The only purchase screen in Cloudmoji.
///
/// It is pushed from `SettingsView`, which itself is behind the parental gate.
/// No child-facing screen links here or contains commercial copy.
struct FullCloudmojiView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                hero
                freeBaseline
                benefits
                purchaseArea
            }
            .frame(maxWidth: 620)
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .navigationTitle("Full Cloudmoji")
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("full-cloudmoji-panel")
    }

    private var hero: some View {
        VStack(spacing: 8) {
            Text("☁️")
                .font(.system(size: 54))
                .accessibilityHidden(true)
            Text("Upgrade to Full Cloudmoji")
                .font(Theme.display(28))
                .foregroundStyle(Theme.textPrimary)
                .multilineTextAlignment(.center)
            Text("Full Cloudmoji is the paid version.")
                .font(Theme.body(16, .black))
                .foregroundStyle(Theme.textPrimary)
                .multilineTextAlignment(.center)
            Text("One purchase. No subscription.")
                .font(Theme.body(16, .bold))
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var freeBaseline: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Your free version includes")
                .font(Theme.body(15, .black))
                .foregroundStyle(Theme.textPrimary)
            Text("Words and Count in English.")
                .font(Theme.body(15, .bold))
                .foregroundStyle(Theme.textSecondary)
            Text("You can keep using the free version for as long as you like.")
                .font(Theme.body(13, .bold))
                .foregroundStyle(Theme.textTertiary)
                .padding(.top, 2)
        }
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .top) { Divider().overlay(Theme.textSecondary.opacity(0.25)) }
        .overlay(alignment: .bottom) { Divider().overlay(Theme.textSecondary.opacity(0.25)) }
        .accessibilityIdentifier("full-free-includes")
    }

    private var benefits: some View {
        VStack(alignment: .leading, spacing: 17) {
            Text("Full Cloudmoji adds")
                .font(Theme.body(17, .black))
                .foregroundStyle(Theme.textPrimary)

            benefit(
                icon: "music.note",
                title: "Five more mini-apps",
                detail: "Music, Flash Cards, Animals, Photos and Sleepy Cloud"
            )
            benefit(
                icon: "globe.asia.australia.fill",
                title: "Four more languages",
                detail: "Mandarin Chinese, Bahasa Melayu, Japanese and Tagalog"
            )
            benefit(
                icon: "applewatch",
                title: "Apple Watch",
                detail: "Share emoji moments and send a short voice note from your watch."
            )
        }
        .accessibilityIdentifier("full-benefits")
    }

    private func benefit(icon: String, title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: 13) {
            Image(systemName: icon)
                .font(.system(size: 21, weight: .bold))
                .foregroundStyle(Theme.teal)
                .frame(width: 28, height: 28)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(Theme.body(15, .black))
                    .foregroundStyle(Theme.textPrimary)
                Text(detail)
                    .font(Theme.body(14, .bold))
                    .foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .accessibilityElement(children: .combine)
        }
    }

    @ViewBuilder private var purchaseArea: some View {
        if model.entitlements.isUnlocked {
            Label("Full Cloudmoji is unlocked on this device.", systemImage: "checkmark.seal.fill")
                .font(Theme.body(16, .black))
                .foregroundStyle(Theme.teal)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(18)
                .background(
                    Theme.teal.opacity(0.14),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
                .accessibilityIdentifier("full-unlocked")
        } else {
            VStack(spacing: 12) {
                purchaseState

                Button {
                    Task { _ = await model.entitlements.restore() }
                } label: {
                    restoreLabel
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 50)
                }
                .buttonStyle(.bordered)
                .tint(Theme.teal)
                .disabled(interactionsDisabled)
                .accessibilityIdentifier("full-restore")

                if let noticeText {
                    Text(noticeText)
                        .font(Theme.body(13, .bold))
                        .foregroundStyle(Theme.coral)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("full-notice")
                }

                Text("Purchases are handled by Apple. No Cloudmoji account is needed.")
                    .font(Theme.body(12, .bold))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder private var purchaseState: some View {
        switch model.entitlements.productState {
        case .loading:
            Button("Checking price…") {}
                .buttonStyle(.borderedProminent)
                .tint(Theme.teal)
                .frame(maxWidth: .infinity)
                .disabled(true)
                .accessibilityIdentifier("full-purchase")

        case .unavailable:
            VStack(spacing: 10) {
                Text("The Full Cloudmoji price could not be loaded. Check your connection and try again.")
                    .font(Theme.body(14, .bold))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)

                Button("Retry") {
                    Task { await model.entitlements.reloadProduct() }
                }
                .buttonStyle(.borderedProminent)
                .tint(Theme.teal)
                .disabled(interactionsDisabled)
                .accessibilityIdentifier("full-retry")
            }

        case .available:
            Button {
                Task { _ = await model.entitlements.purchase() }
            } label: {
                purchaseLabel
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 50)
            }
            .buttonStyle(.borderedProminent)
            .tint(Theme.teal)
            .disabled(interactionsDisabled)
            .accessibilityIdentifier("full-purchase")
        }
    }

    @ViewBuilder private var purchaseLabel: some View {
        switch model.entitlements.operationState {
        case .purchasing:
            Label("Completing purchase…", systemImage: "hourglass")
        case .pending:
            VStack(spacing: 3) {
                Text("Waiting for approval…")
                Text("Your purchase will unlock automatically when approved.")
                    .font(Theme.body(11, .bold))
            }
            .multilineTextAlignment(.center)
        case .restoring:
            Text("Checking your Apple Account…")
        case .idle:
            Text(unlockLabel)
        }
    }

    @ViewBuilder private var restoreLabel: some View {
        if model.entitlements.operationState == .restoring {
            Label("Checking your Apple Account…", systemImage: "hourglass")
        } else {
            Label("Restore Purchase", systemImage: "arrow.clockwise")
        }
    }

    private var interactionsDisabled: Bool {
        model.entitlements.operationState != .idle
    }

    private var unlockLabel: String {
        guard let price = model.entitlements.priceText else {
            return "Unlock Full Cloudmoji"
        }
        return "Unlock Full Cloudmoji — \(price)"
    }

    private var noticeText: String? {
        switch model.entitlements.notice {
        case .purchaseFailed:
            "Cloudmoji could not complete the purchase. Your free version is still available. Please try again."
        case .restoreFailed:
            "Cloudmoji could not check your Apple Account. Please try again."
        case .restoreNotFound:
            "No Full Cloudmoji purchase was found for this Apple Account."
        case nil:
            nil
        }
    }
}

#Preview {
    NavigationStack { FullCloudmojiView() }
        .environment(AppModel())
}
