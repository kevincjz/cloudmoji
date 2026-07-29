import SwiftUI
import CloudmojiCore

/// Every number a launcher tile is drawn from.
///
/// Recorded in `docs/design/DESIGN_SYSTEM.md` alongside the emoji tile and the
/// count tile, for the same reason those are: the touch-target rule in
/// `CLAUDE.md` is a rule rather than a literal, so it has to be assertable.
enum LauncherTileMetrics {
    /// The visible squircle is Home-Screen sized, while the whole icon-and-label
    /// cell remains the much larger child-facing target.
    static let iconSide: CGFloat = 76
    static let compactIconSide: CGFloat = 68
    static let cellHeight: CGFloat = 112
    static let compactCellHeight: CGFloat = 94

    /// `CLAUDE.md` rule 2, the floor between adjacent child-facing targets.
    /// Doubles as the grid's row and column spacing.
    static let spacing: CGFloat = 10

    static let cornerRadius: CGFloat = 22

    static let labelSize: CGFloat = 13
    static let compactLabelSize: CGFloat = 12

    /// Design system Active States: tabs `scale(0.9)`. A launcher tile is the
    /// tab bar's successor, so it takes the tab's number rather than inventing
    /// an fifth one.
    static let pressedScale: CGFloat = 0.92

}

/// One mini-app on the launcher.
///
/// Its own type, like `EmojiTile` and `CountTile`, so the child-facing 64pt
/// floor can be measured off a real render rather than read back out of the
/// constant that set it.
struct LauncherTile: View {
    let app: MiniApp
    /// Already resolved to the family's language by the caller — the tile does
    /// not read settings, the same way no other tile in this app does.
    let label: String
    var isCompact: Bool = false
    let onTap: () -> Void

    private var iconSide: CGFloat {
        isCompact ? LauncherTileMetrics.compactIconSide : LauncherTileMetrics.iconSide
    }

    private var cellHeight: CGFloat {
        isCompact ? LauncherTileMetrics.compactCellHeight : LauncherTileMetrics.cellHeight
    }

    var body: some View {
        Button {
            // Before anything else, so the buzz lands with the finger rather
            // than after the incoming screen has decided what to draw.
            Haptics.tap()
            onTap()
        } label: {
            VStack(spacing: isCompact ? 4 : 7) {
                LauncherAppIcon(app: app, side: iconSide)

                Text(label)
                    .font(Theme.body(isCompact
                                     ? LauncherTileMetrics.compactLabelSize
                                     : LauncherTileMetrics.labelSize, .black))
                    // Text, so it takes the button's accent tint — the system
                    // blue — unless this says otherwise. The glyph above is a
                    // colour emoji and is unaffected.
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .shadow(color: Theme.bgPrimary.opacity(0.9), radius: 2, y: 1)
            }
            .frame(maxWidth: .infinity, minHeight: cellHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: LauncherTileMetrics.pressedScale))
        .accessibilityLabel(label)
        .accessibilityIdentifier("launcher-tile-\(app.rawValue)")
    }
}

/// A layered, in-app app icon.
///
/// It follows the visual grammar of an iOS Home Screen icon — one simple idea,
/// a dimensional background and a consistent squircle — without pretending to
/// be a system-owned icon or embedding tiny interface screenshots.
struct LauncherAppIcon: View {
    let app: MiniApp
    let side: CGFloat

    private var shape: RoundedRectangle {
        RoundedRectangle(
            cornerRadius: side * 0.25,
            style: .continuous
        )
    }

    var body: some View {
        ZStack {
            shape
                .fill(
                    LinearGradient(
                        colors: [
                            app.visualTheme.accent,
                            app.visualTheme.secondary,
                            Theme.bgMid.opacity(0.88),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            Circle()
                .fill(Color.white.opacity(0.18))
                .frame(width: side * 0.76)
                .blur(radius: side * 0.12)
                .offset(x: -side * 0.25, y: -side * 0.28)

            artwork
                .frame(width: side, height: side)

            shape
                .strokeBorder(Color.white.opacity(0.24), lineWidth: 1)
        }
        .frame(width: side, height: side)
        .clipShape(shape)
        .shadow(color: app.visualTheme.accent.opacity(0.28), radius: 10, y: 6)
        .accessibilityHidden(true)
    }

    @ViewBuilder private var artwork: some View {
        switch app {
        case .words:
            ZStack {
                Image(systemName: "quote.bubble.fill")
                    .font(.system(size: side * 0.43, weight: .black))
                    .foregroundStyle(Color.white)
                Circle()
                    .fill(Theme.coral)
                    .frame(width: side * 0.18)
                    .overlay(
                        Image(systemName: "waveform")
                            .font(.system(size: side * 0.09, weight: .black))
                            .foregroundStyle(Color.white)
                    )
                    .offset(x: side * 0.24, y: side * 0.23)
            }

        case .count:
            ZStack {
                RoundedRectangle(cornerRadius: side * 0.16, style: .continuous)
                    .fill(Color.white.opacity(0.92))
                    .frame(width: side * 0.58, height: side * 0.58)
                    .rotationEffect(.degrees(-7))
                Text("123")
                    .font(Theme.body(side * 0.24, .black))
                    .foregroundStyle(Theme.bgMid)
            }

        case .flashCards:
            ZStack {
                card(color: Color.white.opacity(0.34))
                    .rotationEffect(.degrees(-13))
                    .offset(x: -side * 0.08, y: side * 0.02)
                card(color: Color.white.opacity(0.94))
                    .rotationEffect(.degrees(7))
                    .offset(x: side * 0.06)
                Image(systemName: "bolt.fill")
                    .font(.system(size: side * 0.34, weight: .black))
                    .foregroundStyle(Theme.coral)
                    .rotationEffect(.degrees(7))
            }

        case .instrument:
            ZStack {
                HStack(spacing: side * 0.035) {
                    ForEach(0..<4, id: \.self) { index in
                        RoundedRectangle(cornerRadius: side * 0.035)
                            .fill(index.isMultiple(of: 2) ? Color.white : Theme.gold)
                    }
                }
                .frame(width: side * 0.64, height: side * 0.44)
                .offset(y: side * 0.08)
                Image(systemName: "music.note")
                    .font(.system(size: side * 0.28, weight: .black))
                    .foregroundStyle(Color.white)
                    .offset(x: side * 0.18, y: -side * 0.22)
            }

        case .animalSounds:
            ZStack {
                Image(systemName: "pawprint.fill")
                    .font(.system(size: side * 0.46, weight: .black))
                    .foregroundStyle(Color.white)
                Image(systemName: "waveform")
                    .font(.system(size: side * 0.19, weight: .black))
                    .foregroundStyle(Theme.gold)
                    .offset(x: side * 0.25, y: -side * 0.24)
            }

        case .photos:
            ZStack {
                Image(systemName: "camera.fill")
                    .font(.system(size: side * 0.45, weight: .black))
                    .foregroundStyle(Color.white)
                Circle()
                    .fill(Theme.bgMid)
                    .frame(width: side * 0.21)
                Circle()
                    .stroke(Theme.moonlight, lineWidth: side * 0.035)
                    .frame(width: side * 0.12)
            }

        case .sleepy:
            ZStack {
                Image(systemName: "moon.fill")
                    .font(.system(size: side * 0.47, weight: .black))
                    .foregroundStyle(Color.white)
                    .offset(x: -side * 0.08, y: -side * 0.08)
                Image(systemName: "cloud.fill")
                    .font(.system(size: side * 0.31, weight: .black))
                    .foregroundStyle(Theme.moonlight)
                    .offset(x: side * 0.16, y: side * 0.20)
            }
        }
    }

    private func card(color: Color) -> some View {
        RoundedRectangle(cornerRadius: side * 0.09, style: .continuous)
            .fill(color)
            .frame(width: side * 0.48, height: side * 0.58)
    }
}

#Preview("Home Screen icons") {
    ZStack {
        Theme.background.ignoresSafeArea()
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4),
            spacing: 10
        ) {
            ForEach(MiniApp.allCases) { app in
                LauncherTile(
                    app: app,
                    label: app.label(.en),
                    isCompact: false
                ) {}
            }
        }
        .padding()
    }
}
