import Foundation
import Photos

/// Copies app-private photographs into Apple Photos after a grown-up asks.
///
/// Cloudmoji only requests add-only access. It never needs to read, browse or
/// edit the parent's library, and the originals stay in `PhotoStore` after a
/// successful export.
enum PhotoLibraryExporter {
    enum ExportError: Error, Equatable {
        case noPhotos
        case accessDenied
        case photoUnavailable
        case saveFailed
    }

    static func save(_ urls: [URL]) async throws {
        guard !urls.isEmpty else { throw ExportError.noPhotos }
        guard urls.allSatisfy({ FileManager.default.fileExists(atPath: $0.path()) }) else {
            throw ExportError.photoUnavailable
        }

        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard canAddPhotos(for: status) else { throw ExportError.accessDenied }

        do {
            try await PHPhotoLibrary.shared().performChanges {
                for url in urls {
                    _ = PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: url)
                }
            }
        } catch {
            throw ExportError.saveFailed
        }
    }

    /// Kept separate from the system prompt so every authorization result can be
    /// tested without changing the simulator's real photo-library permission.
    static func canAddPhotos(for status: PHAuthorizationStatus) -> Bool {
        switch status {
        case .authorized, .limited:
            true
        case .notDetermined, .restricted, .denied:
            false
        @unknown default:
            false
        }
    }
}
