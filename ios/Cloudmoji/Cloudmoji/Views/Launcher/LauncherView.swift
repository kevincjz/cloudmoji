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
    @Environment(\.cloudmojiLayout) private var layout

    let apps: [MiniApp]
    let onOpen: (MiniApp) -> Void
    var onParent: () -> Void = {}

    /// A stable four-column rhythm is the Home Screen metaphor. It also keeps
    /// every icon in the same place when the seventh item disappears with the
    /// entitlement; a partial row stays left-aligned rather than re-centering.
    static func columns(compact _: Bool) -> Int { 4 }

    static func gridItems(isExpandedPad: Bool, isLandscape: Bool) -> [GridItem] {
        if isExpandedPad {
            let cellWidth = isLandscape
                ? LauncherTileMetrics.padLandscapeCellWidth
                : LauncherTileMetrics.padCellWidth
            let spacing = isLandscape
                ? LauncherTileMetrics.padLandscapeSpacing
                : LauncherTileMetrics.padSpacing
            return Array(
                repeating: GridItem(
                    .fixed(cellWidth),
                    spacing: spacing
                ),
                count: columns(compact: false)
            )
        }

        return Array(
            repeating: GridItem(
                .flexible(minimum: 64, maximum: 102),
                spacing: LauncherTileMetrics.spacing
            ),
            count: columns(compact: false)
        )
    }

    var body: some View {
        let isLandscapePad = layout.isExpandedPad && layout.isLandscape

        ZStack {
            LauncherWallpaper()

            VStack(spacing: 0) {
                LauncherHeaderWidget(onParent: onParent)
                    .padding(.horizontal, layout.isExpandedPad ? 34 : (isCompact ? 12 : 14))
                    .padding(.top, layout.isExpandedPad ? 24 : (isCompact ? 4 : 10))

                ScrollView(showsIndicators: false) {
                    LazyVGrid(
                        columns: Self.gridItems(
                            isExpandedPad: layout.isExpandedPad,
                            isLandscape: layout.isLandscape
                        ),
                        spacing: isLandscapePad
                            ? LauncherTileMetrics.padLandscapeSpacing
                            : (layout.isExpandedPad
                                ? LauncherTileMetrics.padSpacing
                                : LauncherTileMetrics.spacing)
                    ) {
                        ForEach(apps) { app in
                            LauncherTile(
                                app: app,
                                label: app.label(model.settings.language),
                                isCompact: isCompact,
                                isExpandedPad: layout.isExpandedPad,
                                isLandscapePad: isLandscapePad,
                                onTap: { onOpen(app) }
                            )
                        }
                    }
                    .frame(maxWidth: isLandscapePad ? 1_030 : (layout.isExpandedPad ? 780 : 460))
                    .padding(.horizontal, isLandscapePad ? 24 : (layout.isExpandedPad ? 26 : 12))
                    .padding(.top, isLandscapePad ? 20 : (layout.isExpandedPad ? 42 : (isCompact ? 6 : 18)))
                    .padding(.bottom, isLandscapePad ? 24 : (layout.isExpandedPad ? 46 : 24))
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
    @Environment(\.cloudmojiLayout) private var layout

    let onParent: () -> Void

    private var isLandscapePad: Bool {
        layout.isExpandedPad && layout.isLandscape
    }

    var body: some View {
        HStack(spacing: isLandscapePad ? 16 : (layout.isExpandedPad ? 13 : (isCompact ? 7 : 9))) {
            CloudMascot(
                mood: .happy,
                size: isLandscapePad ? 74 : (layout.isExpandedPad ? 66 : (isCompact ? 42 : 50))
            )

            VStack(alignment: .leading, spacing: 0) {
                Text("Cloudmoji")
                    .font(Theme.display(isLandscapePad ? 31 : (layout.isExpandedPad ? 27 : (isCompact ? 17 : 20))))
                    .foregroundStyle(Theme.teal)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                if !isCompact {
                    Text("Tap. Listen. Learn!")
                        .font(Theme.body(isLandscapePad ? 14 : (layout.isExpandedPad ? 12 : 9), .heavy))
                        .foregroundStyle(Theme.textTertiary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 2)

            LauncherParentControl(
                isCompact: isCompact,
                isExpandedPad: layout.isExpandedPad,
                isLandscapePad: isLandscapePad,
                action: onParent
            )
        }
        .padding(.horizontal, isLandscapePad ? 24 : (layout.isExpandedPad ? 20 : (isCompact ? 10 : 12)))
        .padding(.vertical, isLandscapePad ? 14 : (layout.isExpandedPad ? 13 : (isCompact ? 6 : 9)))
        .background(
            Theme.headerPlate,
            in: RoundedRectangle(
                cornerRadius: layout.isExpandedPad ? 32 : 26,
                style: .continuous
            )
        )
        .overlay(
            RoundedRectangle(
                cornerRadius: layout.isExpandedPad ? 32 : 26,
                style: .continuous
            )
                .stroke(Theme.surfaceBorderStrong, lineWidth: 1)
        )
        .overlay(
            RoundedRectangle(
                cornerRadius: layout.isExpandedPad ? 32 : 26,
                style: .continuous
            )
                .stroke(Color.white.opacity(0.18), lineWidth: 1)
        )
        .shadow(color: Theme.bgPrimary.opacity(0.38), radius: 18, y: 8)
        .frame(maxWidth: isLandscapePad ? 940 : (layout.isExpandedPad ? 720 : 560))
        .frame(maxWidth: .infinity)
    }
}

/// The child's Home screen has one piece of parent chrome, not three adjacent
/// controls a toddler can accidentally use. Sound and language now live in the
/// gated parent panel; this is the single, labelled door to them.
private struct LauncherParentControl: View {
    let isCompact: Bool
    let isExpandedPad: Bool
    let isLandscapePad: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: isExpandedPad ? 8 : 6) {
                Image(systemName: "lock.fill")
                    .font(.system(size: isLandscapePad ? 18 : (isExpandedPad ? 16 : 13), weight: .black))
                Text(isCompact ? "Parents" : "Grown-ups")
                    .font(Theme.body(isLandscapePad ? 17 : (isExpandedPad ? 15 : (isCompact ? 11 : 12)), .black))
                    .lineLimit(1)
            }
            .foregroundStyle(Color.white)
            .padding(.horizontal, isLandscapePad ? 20 : (isExpandedPad ? 18 : (isCompact ? 10 : 12)))
            .frame(minHeight: isLandscapePad ? 60 : (isExpandedPad ? 54 : ModeHeaderMetrics.controlSide))
            .background(
                Theme.teal.opacity(0.16),
                in: RoundedRectangle(
                    cornerRadius: isExpandedPad ? 19 : 15,
                    style: .continuous
                )
            )
            .overlay(
                RoundedRectangle(
                    cornerRadius: isExpandedPad ? 19 : 15,
                    style: .continuous
                )
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
                    .frame(width: min(proxy.size.width * 0.90, 700))
                    .blur(radius: min(proxy.size.width * 0.11, 92))
                    .offset(x: -proxy.size.width * 0.46, y: -proxy.size.height * 0.30)

                Circle()
                    .fill(Theme.teal.opacity(0.14))
                    .frame(width: min(proxy.size.width * 0.96, 760))
                    .blur(radius: min(proxy.size.width * 0.12, 104))
                    .offset(x: proxy.size.width * 0.48, y: proxy.size.height * 0.02)

                Circle()
                    .fill(Theme.gold.opacity(0.10))
                    .frame(width: min(proxy.size.width * 0.82, 640))
                    .blur(radius: min(proxy.size.width * 0.10, 88))
                    .offset(x: -proxy.size.width * 0.24, y: proxy.size.height * 0.48)

                Image(systemName: "cloud.fill")
                    .font(.system(size: min(proxy.size.width * 0.58, 430), weight: .black))
                    .foregroundStyle(Color.white.opacity(0.035))
                    .rotationEffect(.degrees(-8))
                    .offset(x: proxy.size.width * 0.34, y: proxy.size.height * 0.34)

                Image(systemName: "cloud.fill")
                    .font(.system(size: min(proxy.size.width * 0.42, 320), weight: .black))
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
