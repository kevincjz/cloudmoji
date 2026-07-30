import Foundation
import Photos
import Testing
import UIKit
@testable import Cloudmoji

@Suite("PhotoStore")
@MainActor
struct PhotoStoreTests {

    /// A temporary directory per test. A test that wrote into the real
    /// Application Support folder would leak into every run after it — and into
    /// the simulator a UI suite launches next.
    private func makeStore(clock: @escaping () -> Date = Date.init) -> PhotoStore {
        let directory = URL.temporaryDirectory
            .appending(path: "photo-store-tests-\(UUID().uuidString)", directoryHint: .isDirectory)
        return PhotoStore(directory: directory, now: clock)
    }

    /// Real JPEG bytes rather than `Data("x".utf8)`: `save` writes whatever it is
    /// handed, but the gallery reads it back through `UIImage`, and a fixture
    /// that is not an image would let a decode bug through.
    private func jpeg(_ shade: CGFloat) -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8))
        let image = renderer.image { context in
            UIColor(white: shade, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
        return image.jpegData(compressionQuality: 0.8) ?? Data()
    }

    /// Newest first — the thing a child just photographed is the first thing he
    /// sees.
    ///
    /// The clock is stepped explicitly rather than slept through: file-system
    /// timestamps have a coarser resolution than two shutter presses a second
    /// apart, which is exactly why the name carries a millisecond stamp.
    ///
    /// Mutation: change the sort in `photos` to `<`. This fails.
    @Test("photos come back newest first")
    func photosAreNewestFirst() throws {
        var tick = Date(timeIntervalSince1970: 1_000)
        let store = makeStore { tick }

        let first = try store.save(jpeg(0.2))
        tick = tick.addingTimeInterval(5)
        let second = try store.save(jpeg(0.5))
        tick = tick.addingTimeInterval(5)
        let third = try store.save(jpeg(0.8))

        #expect(store.photos == [third, second, first])
        #expect(store.count == 3)
    }

    /// Mutation: make `delete` a no-op. Both assertions fail.
    @Test("deleting removes one, and delete-all empties the folder")
    func deletingWorks() throws {
        var tick = Date(timeIntervalSince1970: 2_000)
        let store = makeStore { tick }
        for _ in 0..<3 {
            _ = try store.save(jpeg(0.4))
            tick = tick.addingTimeInterval(1)
        }
        #expect(store.count == 3)

        let target = store.photos[1]
        store.delete(target)
        #expect(store.count == 2)
        #expect(!store.photos.contains(target))

        store.deleteAll()
        #expect(store.photos.isEmpty)
        // Twice, because the confirmation dialog can be answered on an already
        // empty folder.
        store.deleteAll()
        #expect(store.photos.isEmpty)
    }

    /// **The load-bearing half of "photos never leave the device."** Without the
    /// exclusion the folder rides along in the device's iCloud backup, which is a
    /// copy of a child's photographs on Apple's servers — and the About screen
    /// says otherwise.
    ///
    /// Mutation: delete the `try? Self.excludeFromBackup(url)` line in `save`.
    /// This fails.
    @Test("a saved photo is excluded from iCloud backup")
    func savedPhotosAreExcludedFromBackup() throws {
        let store = makeStore()
        let url = try store.save(jpeg(0.6))
        #expect(PhotoStore.isExcludedFromBackup(url),
                "\(url.lastPathComponent) would be uploaded with the device's backup")
        // The folder too: anything written into it later inherits the exclusion,
        // including by a future version of this app that forgets to mark its own
        // writes.
        #expect(PhotoStore.isExcludedFromBackup(store.directory))
    }

    /// Unreadable while the phone is locked, which is the state a phone handed to
    /// a toddler spends most of its life in.
    ///
    /// Mutation: drop `.completeFileProtection` from `PhotoStore.writeOptions`.
    /// This fails.
    ///
    /// **Asserted on the constant, and honestly labelled as shallow.** The
    /// Simulator does not implement data protection — its container lives on the
    /// Mac's filesystem, which has no such attribute — so `attributesOfItem`
    /// reports nothing and an on-disk check would be vacuous in the only
    /// environment these tests run in. That was measured, not assumed: the
    /// on-disk version of this test was written first and came back `nil`.
    /// The on-disk half is kept below as a conditional, so that the day this
    /// suite runs on a device it starts asserting for real.
    @Test("a saved photo asks for complete file protection")
    func savedPhotosAreProtected() throws {
        #expect(PhotoStore.writeOptions.contains(.completeFileProtection),
                "photos are written without file protection")
        #expect(PhotoStore.writeOptions.contains(.atomic),
                "a non-atomic write can leave a half-photo in a child's gallery")

        let store = makeStore()
        let url = try store.save(jpeg(0.6))
        let attributes = try FileManager.default.attributesOfItem(atPath: url.path())
        if let protection = attributes[.protectionKey] as? FileProtectionType {
            #expect(protection == .complete, "the file was written with protection \(protection)")
        }
    }

    /// What was written is what comes back. A store that saved zero bytes would
    /// pass every assertion above and show a child an empty grey square.
    @Test("the bytes written are the bytes read back")
    func savedPhotosRoundTrip() throws {
        let store = makeStore()
        let data = jpeg(0.3)
        let url = try store.save(data)

        #expect(try Data(contentsOf: url) == data)
        #expect(UIImage(contentsOfFile: url.path()) != nil, "the saved file does not decode as an image")
    }

    /// Only photographs. A stray file in the folder — a `.DS_Store`, a partial
    /// write — must not become a grey square in a child's gallery.
    @Test("non-photo files in the folder are ignored")
    func strayFilesAreIgnored() throws {
        let store = makeStore()
        _ = try store.save(jpeg(0.5))
        try Data("junk".utf8).write(to: store.directory.appending(path: "notes.txt"))

        #expect(store.count == 1)
        #expect(store.photos.allSatisfy { $0.pathExtension == PhotoStore.fileExtension })
    }

    /// The folder is created on first save rather than at launch — most sessions
    /// never open Photos at all.
    @Test("the folder is created on demand")
    func directoryIsCreatedOnDemand() throws {
        let store = makeStore()
        #expect(!FileManager.default.fileExists(atPath: store.directory.path()))
        #expect(store.photos.isEmpty, "reading an absent folder must not throw or crash")

        _ = try store.save(jpeg(0.5))
        #expect(FileManager.default.fileExists(atPath: store.directory.path()))
    }
}

@Suite("Photo library export")
@MainActor
struct PhotoLibraryExporterTests {

    @Test("only statuses that allow adding photos proceed")
    func authorizationStatusesAreHandled() {
        #expect(PhotoLibraryExporter.canAddPhotos(for: .authorized))
        #expect(PhotoLibraryExporter.canAddPhotos(for: .limited))
        #expect(!PhotoLibraryExporter.canAddPhotos(for: .notDetermined))
        #expect(!PhotoLibraryExporter.canAddPhotos(for: .restricted))
        #expect(!PhotoLibraryExporter.canAddPhotos(for: .denied))
    }

    @Test("the app explains why it adds to the photo library")
    func addOnlyUsageDescriptionIsPresent() {
        let description = Bundle.main.object(
            forInfoDictionaryKey: "NSPhotoLibraryAddUsageDescription"
        ) as? String
        #expect(!(description ?? "").isEmpty)
        #expect(description?.localizedCaseInsensitiveContains("save") == true)
    }
}

@Suite("Camera lifecycle")
@MainActor
struct CameraLifecycleTests {

    @Test("every camera authorization state has an explicit recovery action")
    func permissionStatesNeverBecomeADeadCameraTile() {
        #expect(PhotosView.cameraEntryAction(for: .ready) == .openCamera)
        #expect(PhotosView.cameraEntryAction(for: .needsPermission) == .requestPermission)
        #expect(PhotosView.cameraEntryAction(for: .denied) == .askParent)
        #expect(PhotosView.cameraEntryAction(for: .unavailable) == .unavailable)
    }

    /// **The white-out.** A debounced capture never calls its completion, so a
    /// caller that raised a flash before asking had nothing coming to lower it —
    /// the viewfinder went white and stayed white. The debounce answering
    /// truthfully is what lets `CameraView` ask first and light the flash second.
    ///
    /// Mutation: return `true` unconditionally from `acceptsCapture`. The
    /// within-the-window case fails.
    @Test("a second shutter press inside the debounce window is refused")
    func debounceRefusesRapidPresses() {
        let first = Date(timeIntervalSince1970: 10_000)

        #expect(CameraController.acceptsCapture(now: first, lastCaptureAt: nil),
                "the very first press must always be accepted")
        #expect(!CameraController.acceptsCapture(
            now: first.addingTimeInterval(0.2), lastCaptureAt: first
        ), "a press 200ms later was accepted — a toddler drums far faster than that")
        #expect(!CameraController.acceptsCapture(
            now: first.addingTimeInterval(CameraController.captureDebounce - 0.01),
            lastCaptureAt: first
        ))
        #expect(CameraController.acceptsCapture(
            now: first.addingTimeInterval(CameraController.captureDebounce),
            lastCaptureAt: first
        ), "the window never reopens — the shutter would work exactly once")
    }

    /// The debounce is about a held finger, not about making the camera feel
    /// slow. Anything much past a second and a parent taking two pictures of a
    /// moving child loses the second one.
    @Test("the debounce window is about a second")
    func debounceIsShort() {
        #expect(CameraController.captureDebounce >= 0.5)
        #expect(CameraController.captureDebounce <= 1.5)
    }
}
