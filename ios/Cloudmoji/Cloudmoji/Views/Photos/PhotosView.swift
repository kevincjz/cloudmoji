import SwiftUI
import CloudmojiCore

enum PhotoGalleryMetrics {
    /// The preferred child target. A thumbnail is tapped to make it big, which
    /// is the one gesture in this mini-app.
    static let thumbnailSide: CGFloat = 92
    static let spacing: CGFloat = 12
    static let cornerRadius: CGFloat = 12
    /// The camera tile is bigger than a thumbnail on purpose: it is the thing
    /// the child came here to do, and the photographs are what happened last
    /// time.
    static let cameraSide: CGFloat = 148
    static let pressedScale: CGFloat = 0.85
}

/// Photos 📷 — the child's own pictures, and the way to take another.
///
/// **There is no delete on this screen.** A gallery a two-year-old can empty is a
/// gallery a two-year-old will empty, and the photographs are the point. Deleting
/// lives in `ManagePhotosView`, behind the parental gate, where a grown-up who
/// means it can find it.
///
/// A denied camera never becomes a dead or missing feature: the normal camera
/// tile becomes a parent-gated recovery card that can open iPhone Settings.
struct PhotosView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact

    @State private var store = PhotoStore()
    @State private var photos: [URL] = []
    @State private var enlarged: URL?
    @State private var isCameraShowing = false
    @State private var availability = CameraController.availability
    @Environment(\.scenePhase) private var scenePhase

    /// A denied system permission can only be changed in iPhone Settings. The
    /// root supplies a parental gate before opening that external screen.
    var onCameraPermissionHelp: () -> Void = {}

    enum CameraEntryAction: Equatable {
        case openCamera
        case requestPermission
        case askParent
        case unavailable
    }

    static func cameraEntryAction(for availability: CameraAvailability) -> CameraEntryAction {
        switch availability {
        case .ready: .openCamera
        case .needsPermission: .requestPermission
        case .denied: .askParent
        case .unavailable: .unavailable
        }
    }

    /// Chrome, in the five languages. Copy, not content.
    struct UIText {
        let empty: [Language: String]
        let takeOne: [Language: String]
        /// Parent-facing, and English only — like every other explanation in this
        /// app, it is for the grown-up who is wondering why a control is missing.
        let noCamera: String
        let cameraDenied: String
        let askGrownUp: [Language: String]
    }

    static let uiText = UIText(
        empty: [
            .en: "No pictures yet", .zh: "还没有照片", .ms: "Belum ada gambar",
            .ja: "まだ しゃしんが ないよ", .tl: "Wala pang litrato",
        ],
        takeOne: [
            .en: "Take a picture", .zh: "拍一张", .ms: "Ambil gambar",
            .ja: "しゃしんを とる", .tl: "Kumuha ng litrato",
        ],
        // Every Simulator lands here, which is why this line exists: without it
        // Photos on a Simulator is a blank screen with a cloud in the corner and
        // looks exactly like a mini-app that was never built.
        noCamera: "This device has no camera, so there is nothing to photograph with. On an iPhone or iPad the camera button appears here.",
        cameraDenied: "Camera access is off.",
        askGrownUp: [
            .en: "Ask a grown-up", .zh: "请大人帮忙", .ms: "Minta orang dewasa",
            .ja: "おとなに きいてね", .tl: "Magtanong sa matanda",
        ]
    )

    private func text(_ table: [Language: String]) -> String {
        table[model.settings.language] ?? table[.en] ?? ""
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 18) {
                cameraTile

                if photos.isEmpty {
                    emptyScrapbook
                    cameraNote
                } else {
                    grid
                    cameraNote
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, isCompact ? 6 : 14)
        }
        .onAppear {
            photos = store.photos
            refreshAvailability()
        }
        // Coming back from the Settings app is the one way `.denied` becomes
        // `.ready` without this view being rebuilt, so the camera tile has to
        // appear on return rather than on the next cold launch.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refreshAvailability() }
        }
        .fullScreenCover(isPresented: $isCameraShowing) {
            CameraView(
                caption: text(Self.uiText.takeOne),
                onCapture: { data in save(data) },
                onDone: { isCameraShowing = false }
            )
        }
        .fullScreenCover(item: enlargedItem) { item in
            EnlargedPhoto(url: item.url) { enlarged = nil }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("photos-panel")
    }

    // MARK: - Pieces

    /// Why the camera button is not there on hardware with no usable camera.
    ///
    /// The button itself is **absent** rather than disabled when there is no
    /// camera — a control that answers a tap with nothing is the failure state
    /// `CLAUDE.md` rule 4 forbids. Denial is handled separately by the recovery
    /// card above the gallery.
    @ViewBuilder private var cameraNote: some View {
        if availability == .unavailable {
            Text(Self.uiText.noCamera)
                .font(Theme.body(12, .bold))
                // This is the only explanation a parent gets for an absent
                // camera control, so it must remain readable over the warm
                // scrapbook glow.
                .foregroundStyle(Theme.textTertiary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 24)
                .padding(.top, 4)
                .accessibilityIdentifier("photos-camera-note")
        }
    }

    @ViewBuilder private var cameraTile: some View {
        if availability == .denied {
            cameraPermissionTile
        } else if availability != .unavailable {
            let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
            Button {
                Haptics.tap()
                openCamera()
            } label: {
                ZStack {
                    shape.fill(
                        LinearGradient(
                            colors: [Color.white.opacity(0.18), Theme.coral.opacity(0.18)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                    RoundedRectangle(cornerRadius: 17, style: .continuous)
                        .fill(Theme.bgPrimary.opacity(0.66))
                        .frame(maxWidth: .infinity)
                        .padding(12)

                    HStack(spacing: 18) {
                        ZStack {
                            Circle()
                                .fill(Theme.bgPrimary)
                                .frame(width: 76, height: 76)
                                .overlay(Circle().stroke(Theme.moonlight.opacity(0.44), lineWidth: 3))
                            Circle()
                                .fill(
                                    RadialGradient(
                                        colors: [Theme.moonlight.opacity(0.68), Theme.bgMid],
                                        center: .topLeading,
                                        startRadius: 0,
                                        endRadius: 34
                                    )
                                )
                                .frame(width: 50, height: 50)
                            Circle()
                                .fill(Color.white.opacity(0.72))
                                .frame(width: 10, height: 10)
                                .offset(x: -12, y: -12)
                        }

                        VStack(alignment: .leading, spacing: 5) {
                            Image(systemName: "camera.fill")
                                .font(.system(size: 22, weight: .black))
                                .foregroundStyle(Theme.coral)
                            Text(text(Self.uiText.takeOne))
                                .font(Theme.body(15, .black))
                                .foregroundStyle(Theme.textPrimary)
                                .lineLimit(2)
                                .minimumScaleFactor(0.72)
                        }
                    }
                    .padding(.horizontal, 24)
                }
                .frame(maxWidth: .infinity)
                .frame(height: PhotoGalleryMetrics.cameraSide)
                .clipShape(shape)
                .overlay(shape.stroke(Color.white.opacity(0.22), lineWidth: 2))
                .shadow(color: Theme.coral.opacity(0.18), radius: 18, y: 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(PressScale(scale: PhotoGalleryMetrics.pressedScale))
            .accessibilityLabel(text(Self.uiText.takeOne))
            .accessibilityIdentifier("photos-camera-btn")
        }
    }

    /// A denied permission is recoverable, so it stays visible as a useful
    /// action rather than disappearing. The action itself is gated by the root
    /// before iPhone Settings opens.
    private var cameraPermissionTile: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        return Button {
            Haptics.tap()
            onCameraPermissionHelp()
        } label: {
            HStack(spacing: 16) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 30, weight: .black))
                    .foregroundStyle(Theme.coral)
                    .frame(width: 68, height: 68)
                    .background(Theme.coral.opacity(0.14), in: Circle())

                VStack(alignment: .leading, spacing: 5) {
                    Text(text(Self.uiText.askGrownUp))
                        .font(Theme.body(16, .black))
                        .foregroundStyle(Theme.textPrimary)
                    Text(Self.uiText.cameraDenied)
                        .font(Theme.body(12, .bold))
                        .foregroundStyle(Theme.textTertiary)
                }

                Spacer(minLength: 0)

                Image(systemName: "lock.fill")
                    .font(.system(size: 16, weight: .black))
                    .foregroundStyle(Theme.gold)
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
            .frame(height: PhotoGalleryMetrics.cameraSide)
            .background(Theme.bgPrimary.opacity(0.72), in: shape)
            .overlay(shape.stroke(Theme.coral.opacity(0.36), lineWidth: 2))
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: PhotoGalleryMetrics.pressedScale))
        .accessibilityLabel("\(text(Self.uiText.askGrownUp)). \(Self.uiText.cameraDenied)")
        .accessibilityIdentifier("photos-camera-permission-btn")
    }

    /// Even the Simulator, where there is no camera, gets a composed empty
    /// scrapbook rather than a line of copy floating in a dark room.
    private var emptyScrapbook: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.white.opacity(0.08))
                .frame(width: 132, height: 110)
                .rotationEffect(.degrees(-7))
                .offset(x: -18, y: 2)
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.white.opacity(0.12))
                .frame(width: 132, height: 110)
                .rotationEffect(.degrees(7))
                .offset(x: 18, y: 2)

            VStack(spacing: 9) {
                Image(systemName: "photo.on.rectangle.angled")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(Theme.moonlight.opacity(0.78))
                Text(text(Self.uiText.empty))
                    .font(Theme.body(14, .heavy))
                    .foregroundStyle(Theme.textTertiary)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .frame(width: 150, height: 116)
            .background(
                Theme.bgPrimary.opacity(0.82),
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Theme.coral.opacity(0.24), lineWidth: 2)
            )
        }
        .frame(height: 140)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("photos-empty")
    }

    private var grid: some View {
        LazyVGrid(
            columns: [GridItem(
                .adaptive(minimum: PhotoGalleryMetrics.thumbnailSide),
                spacing: PhotoGalleryMetrics.spacing
            )],
            spacing: PhotoGalleryMetrics.spacing
        ) {
            ForEach(Array(photos.enumerated()), id: \.element) { index, url in
                thumbnail(url, index: index)
            }
        }
    }

    private func thumbnail(_ url: URL, index: Int) -> some View {
        let shape = RoundedRectangle(cornerRadius: PhotoGalleryMetrics.cornerRadius, style: .continuous)
        return Button {
            Haptics.tap()
            enlarged = url
        } label: {
            VStack(spacing: 6) {
                photoImage(url, maximumPoints: PhotoGalleryMetrics.thumbnailSide)
                    .frame(
                        minWidth: PhotoGalleryMetrics.thumbnailSide,
                        maxWidth: .infinity,
                        minHeight: PhotoGalleryMetrics.thumbnailSide,
                        maxHeight: PhotoGalleryMetrics.thumbnailSide
                    )
                    .clipShape(shape)

                Image(systemName: index.isMultiple(of: 3) ? "heart.fill" : "sparkles")
                    .font(.system(size: 11, weight: .black))
                    .foregroundStyle(index.isMultiple(of: 3) ? Theme.coral : Theme.gold)
                    .frame(height: 14)
            }
                .padding(7)
                .background(Color.white.opacity(0.90), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .stroke(Color.white.opacity(0.66), lineWidth: 1)
                )
                .rotationEffect(.degrees([-2.5, 1.8, -1.0, 2.2][index % 4]))
                .shadow(color: Theme.bgPrimary.opacity(0.34), radius: 9, y: 6)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: PhotoGalleryMetrics.pressedScale))
        .accessibilityLabel("Photo")
        .accessibilityIdentifier("photo-\(url.lastPathComponent)")
    }

    // MARK: - Behaviour

    private func refreshAvailability() {
        availability = CameraController.availability
    }

    private func openCamera() {
        switch Self.cameraEntryAction(for: availability) {
        case .openCamera:
            isCameraShowing = true
        case .requestPermission:
            Task {
                let granted = await CameraController().requestAccess()
                availability = CameraController.availability
                if granted { isCameraShowing = true }
            }
        case .askParent:
            onCameraPermissionHelp()
        case .unavailable:
            break
        }
    }

    /// `Data?` because a capture can come back empty — a shutter pressed while
    /// the session was restarting, or a system-level failure. The child still
    /// got the flash and the buzz; there is simply nothing new in the gallery.
    private func save(_ data: Data?) {
        guard let data else { return }
        guard (try? store.save(data)) != nil else { return }
        withAnimation(.spring(response: 0.42, dampingFraction: 0.78)) {
            photos = store.photos
        }
    }

    /// `fullScreenCover(item:)` wants something `Identifiable`, and a bare `URL`
    /// is not.
    private struct EnlargedItem: Identifiable {
        let url: URL
        var id: String { url.lastPathComponent }
    }

    private var enlargedItem: Binding<EnlargedItem?> {
        Binding(
            get: { enlarged.map(EnlargedItem.init) },
            set: { enlarged = $0?.url }
        )
    }
}

/// One photograph, decoded no larger than it is going to be drawn.
///
/// `UIImage(contentsOfFile:)` was the first version and is wrong for a grid: it
/// decodes the **full** 12-megapixel JPEG — about 48MB of bitmap — on the main
/// actor, for a 72pt square. One is imperceptible; a gallery a child has been
/// filling for a month scrolls like treacle and then gets jettisoned for memory.
///
/// `CGImageSourceCreateThumbnailAtIndex` decodes straight to the size asked for,
/// so the cost is bounded by the *thumbnail*, not by the photograph. Results are
/// cached by URL and pixel size, because a `LazyVGrid` re-asks for the same
/// image every time a row comes back on screen.
enum PhotoThumbnails {
    /// Small: a few hundred KB per entry at thumbnail size, and a gallery is
    /// realistically dozens of photos rather than thousands.
    private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 240
        return cache
    }()

    /// `maximumPixels` is the longest edge in **pixels**, so callers multiply
    /// their point size by the screen scale.
    static func image(at url: URL, maximumPixels: Int) -> UIImage? {
        let key = "\(url.lastPathComponent)|\(maximumPixels)" as NSString
        if let hit = cache.object(forKey: key) { return hit }

        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  // Without this a JPEG shot in portrait comes back on its side.
                  kCGImageSourceCreateThumbnailWithTransform: true,
                  kCGImageSourceShouldCacheImmediately: true,
                  kCGImageSourceThumbnailMaxPixelSize: maximumPixels,
              ] as CFDictionary)
        else { return nil }

        let image = UIImage(cgImage: cg)
        cache.setObject(image, forKey: key)
        return image
    }

    /// Dropped when the gallery is emptied, so deleted photographs do not live
    /// on in memory behind a screen that says they are gone.
    static func forget() { cache.removeAllObjects() }
}

/// One photograph, at the size it will be drawn.
@ViewBuilder
func photoImage(_ url: URL, maximumPoints: CGFloat) -> some View {
    // `.main.scale` rather than the environment's `displayScale`: this is a free
    // function used from three call sites, and a 2× decode on a 3× screen is a
    // visibly soft thumbnail.
    let pixels = Int(maximumPoints * UIScreen.main.scale)
    if let image = PhotoThumbnails.image(at: url, maximumPixels: max(pixels, 1)) {
        Image(uiImage: image)
            .resizable()
            .scaledToFill()
    } else {
        // A file that will not decode is a file that should not be on screen,
        // but an empty plate is a better answer than a crash.
        Theme.surface
    }
}

/// A photograph, big. Tap anywhere to go back — there is no close button,
/// because there is no small target a toddler has to find.
private struct EnlargedPhoto: View {
    let url: URL
    let onClose: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            // Full-screen, so the decode budget is the screen rather than a
            // thumbnail — but still bounded, and still off the full 12MP path.
            photoImage(url, maximumPoints: 1400)
                .scaledToFit()
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClose)
        .accessibilityIdentifier("photo-full")
    }
}

#Preview("Photos") {
    AdaptiveShell { PhotosView() }
        .environment(AppModel())
}
