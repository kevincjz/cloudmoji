import SwiftUI
import Testing
@testable import Cloudmoji

@Suite("iPad layout")
@MainActor
struct IPadLayoutTests {
    private let portrait = CloudmojiLayout(
        size: CGSize(width: 744, height: 1_133),
        isPad: true
    )
    private let landscape = CloudmojiLayout(
        size: CGSize(width: 1_133, height: 744),
        isPad: true
    )

    @Test("full-screen iPads use the expanded composition in either orientation")
    func fullScreenPadLayouts() {
        let landscapeSafeArea = CloudmojiLayout(
            size: CGSize(width: 1_133, height: 680),
            isPad: true
        )

        #expect(portrait.isExpandedPad)
        #expect(!portrait.isLandscape)
        #expect(landscape.isExpandedPad)
        #expect(landscape.isLandscape)
        #expect(landscapeSafeArea.isExpandedPad)
        #expect(!portrait.isCompactPhone)
        #expect(!landscape.isCompactPhone)
    }

    @Test("narrow iPad windows fall back to the phone-sized composition")
    func splitViewFallback() {
        let splitView = CloudmojiLayout(
            size: CGSize(width: 507, height: 1_133),
            isPad: true
        )
        #expect(splitView.isPad)
        #expect(!splitView.isExpandedPad)
        #expect(!splitView.isCompactPhone)
    }

    @Test("the same short canvas is compact only when it is a phone")
    func idiomKeepsPadOutOfCompactPhoneChrome() {
        let size = CGSize(width: 900, height: 540)
        #expect(CloudmojiLayout(size: size, isPad: false).isCompactPhone)
        #expect(!CloudmojiLayout(size: size, isPad: true).isCompactPhone)
    }

    @Test("iPad launcher and home controls are deliberately larger")
    func largerNavigationTargets() {
        #expect(LauncherTileMetrics.padIconSide > LauncherTileMetrics.iconSide)
        #expect(LauncherTileMetrics.padCellHeight > LauncherTileMetrics.cellHeight)
        #expect(LauncherTileMetrics.padCellWidth >= LauncherTileMetrics.padIconSide)
        #expect(HomeButtonMetrics.padSide > HomeButtonMetrics.side)
        #expect(HomeButtonMetrics.padReservedHeight > HomeButtonMetrics.reservedHeight)

        let bitmap = Bitmap.rendered(
            LauncherTile(
                app: .words,
                label: "Words",
                isExpandedPad: true,
                onTap: {}
            )
            .frame(width: 170)
            .environment(\.cloudmojiLayout, portrait)
        )
        #expect(bitmap.height >= Int(LauncherTileMetrics.padCellHeight))
    }

    @Test("four iPad launcher icons fit with a visible gap")
    func launcherGridSpacing() {
        let columns = LauncherView.columns(compact: false)
        let occupiedWidth =
            CGFloat(columns) * LauncherTileMetrics.padCellWidth
            + CGFloat(columns - 1) * LauncherTileMetrics.padSpacing
        let portraitGridWidth = portrait.size.width - 52
        let visibleIconGap =
            LauncherTileMetrics.padCellWidth
            - LauncherTileMetrics.padIconSide
            + LauncherTileMetrics.padSpacing

        #expect(occupiedWidth <= portraitGridWidth)
        #expect(visibleIconGap >= 32)
    }

    @Test("landscape launcher grows to use the wider iPad canvas")
    func landscapeLauncherScale() {
        let columns = LauncherView.columns(compact: false)
        let occupiedWidth =
            CGFloat(columns) * LauncherTileMetrics.padLandscapeCellWidth
            + CGFloat(columns - 1) * LauncherTileMetrics.padLandscapeSpacing
        let visibleIconGap =
            LauncherTileMetrics.padLandscapeCellWidth
            - LauncherTileMetrics.padLandscapeIconSide
            + LauncherTileMetrics.padLandscapeSpacing

        #expect(LauncherTileMetrics.padLandscapeIconSide > LauncherTileMetrics.padIconSide)
        #expect(LauncherTileMetrics.padLandscapeLabelSize > LauncherTileMetrics.padLabelSize)
        #expect(occupiedWidth <= landscape.size.width - 64)
        #expect(occupiedWidth >= landscape.size.width * 0.75)
        #expect(visibleIconGap >= 48)
    }

    @Test("activity grids use the iPad axes and density")
    func activityGridDensity() {
        #expect(
            InstrumentPadView.columns(
                compact: false,
                expandedPad: true,
                landscape: false
            ) == 2
        )
        #expect(
            InstrumentPadView.columns(
                compact: false,
                expandedPad: true,
                landscape: true
            ) == 4
        )

        #expect(
            AnimalSoundsView.columns(
                compact: false,
                expandedPad: true,
                landscape: false
            ) == 3
        )
        #expect(
            AnimalSoundsView.columns(
                compact: false,
                expandedPad: true,
                landscape: true
            ) == 4
        )
    }

    @Test("large iPad surfaces stay bounded")
    func boundedSurfaces() {
        #expect(InstrumentPadMetrics.maximumPadSide <= 260)
        #expect(PhotoGalleryMetrics.padCameraMaxWidth < 800)
        #expect(PhotoGalleryMetrics.padContentMaxWidth <= 1_100)
        #expect(FlashCardMetrics.padChoiceSide >= 140)
    }
}
