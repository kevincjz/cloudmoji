import SwiftUI
import CloudmojiCore

/// Home. A wallpaper, one compact parent widget, and app icons.
///
/// The first launcher used two 150pt translucent plates. They were generous
/// targets, but the plate was the visual object and the result read as a
/// dashboard. Here the *cell* remains generous while the visible object becomes
/// the familiar iOS app-icon squircle with its caption beneath it.
struct LauncherView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact

    let apps: [MiniApp]
    let onOpen: (MiniApp) -> Void
    var onParent: () -> Void = {}

    /// A stable four-column rhythm is the Home Screen metaphor. It also keeps
    /// every icon in the same place when the seventh item disappears with the
    /// entitlement; a partial row stays left-aligned rather than re-centering.
    static func columns(compact _: Bool) -> Int { 4 }

    var body: some View {
        ZStack {
            LauncherWallpaper()

            VStack(spacing: 0) {
                LauncherHeaderWidget(onParent: onParent)
                    .padding(.horizontal, isCompact ? 12 : 14)
                    .padding(.top, isCompact ? 4 : 10)

                ScrollView(showsIndicators: false) {
                    LazyVGrid(
                        columns: Array(
                            repeating: GridItem(
                                .flexible(minimum: 64, maximum: 102),
                                spacing: LauncherTileMetrics.spacing
                            ),
                            count: Self.columns(compact: isCompact)
                        ),
                        spacing: LauncherTileMetrics.spacing
                    ) {
                        ForEach(apps) { app in
                            LauncherTile(
                                app: app,
                                label: app.label(model.settings.language),
                                isCompact: isCompact,
                                onTap: { onOpen(app) }
                            )
                        }
                    }
                    .frame(maxWidth: 460)
                    .padding(.horizontal, 12)
                    .padding(.top, isCompact ? 6 : 18)
                    .padding(.bottom, 24)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("launcher")
    }
}

/// The only glass surface on the launcher: a small widget for the brand and the
/// grown-up door. App icons sit directly on the wallpaper, as they do on
/// the iPhone Home Screen.
private struct LauncherHeaderWidget: View {
    @Environment(\.cloudmojiIsCompact) private var isCompact

    let onParent: () -> Void

    var body: some View {
        HStack(spacing: isCompact ? 7 : 9) {
            CloudMascot(mood: .happy, size: isCompact ? 42 : 50)

            VStack(alignment: .leading, spacing: 0) {
                Text("Cloudmoji")
                    .font(Theme.display(isCompact ? 17 : 20))
                    .foregroundStyle(Theme.teal)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                if !isCompact {
                    Text("Tap. Listen. Learn!")
                        .font(Theme.body(9, .heavy))
                        .foregroundStyle(Theme.textTertiary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 2)

            LauncherParentControl(isCompact: isCompact, action: onParent)
        }
        .padding(.horizontal, isCompact ? 10 : 12)
        .padding(.vertical, isCompact ? 6 : 9)
        .background(Theme.headerPlate, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .stroke(Theme.surfaceBorderStrong, lineWidth: 1)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .stroke(Color.white.opacity(0.18), lineWidth: 1)
        )
        .shadow(color: Theme.bgPrimary.opacity(0.38), radius: 18, y: 8)
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
    }
}

/// The child's Home screen has one piece of parent chrome, not three adjacent
/// controls a toddler can accidentally use. Sound and language now live in the
/// gated parent panel; this is the single, labelled door to them.
private struct LauncherParentControl: View {
    let isCompact: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 13, weight: .black))
                Text(isCompact ? "Parents" : "Grown-ups")
                    .font(Theme.body(isCompact ? 11 : 12, .black))
                    .lineLimit(1)
            }
            .foregroundStyle(Color.white)
            .padding(.horizontal, isCompact ? 10 : 12)
            .frame(minHeight: ModeHeaderMetrics.controlSide)
            .background(
                Theme.teal.opacity(0.16),
                in: RoundedRectangle(cornerRadius: 15, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .stroke(Theme.teal.opacity(0.34), lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: ModeHeaderMetrics.pressedScale))
        .accessibilityLabel("Grown-ups only")
        .accessibilityIdentifier("parent-btn")
    }
}

/// A wallpaper rather than a collection of card backgrounds. The three glows
/// are the established Cloudmoji coral / teal / gold ambience; the oversized
/// cloud silhouettes keep the launcher playful without competing with icons.
private struct LauncherWallpaper: View {
    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Theme.background

                Circle()
                    .fill(Theme.coral.opacity(0.16))
                    .frame(width: min(proxy.size.width * 0.90, 430))
                    .blur(radius: 60)
                    .offset(x: -proxy.size.width * 0.46, y: -proxy.size.height * 0.30)

                Circle()
                    .fill(Theme.teal.opacity(0.14))
                    .frame(width: min(proxy.size.width * 0.96, 470))
                    .blur(radius: 68)
                    .offset(x: proxy.size.width * 0.48, y: proxy.size.height * 0.02)

                Circle()
                    .fill(Theme.gold.opacity(0.10))
                    .frame(width: min(proxy.size.width * 0.82, 390))
                    .blur(radius: 62)
                    .offset(x: -proxy.size.width * 0.24, y: proxy.size.height * 0.48)

                Image(systemName: "cloud.fill")
                    .font(.system(size: min(proxy.size.width * 0.58, 250), weight: .black))
                    .foregroundStyle(Color.white.opacity(0.035))
                    .rotationEffect(.degrees(-8))
                    .offset(x: proxy.size.width * 0.34, y: proxy.size.height * 0.34)

                Image(systemName: "cloud.fill")
                    .font(.system(size: min(proxy.size.width * 0.42, 190), weight: .black))
                    .foregroundStyle(Color.white.opacity(0.025))
                    .rotationEffect(.degrees(10))
                    .offset(x: -proxy.size.width * 0.43, y: proxy.size.height * 0.08)
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview("Launcher") {
    AdaptiveShell {
        LauncherView(apps: MiniApp.allCases, onOpen: { _ in })
    }
    .environment(AppModel())
}
