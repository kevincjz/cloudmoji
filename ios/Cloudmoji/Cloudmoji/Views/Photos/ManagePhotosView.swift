import SwiftUI
import UIKit

/// The grown-up's half of Photos: save copies to Apple Photos or delete them.
///
/// Pushed from `SettingsView`, so it is already behind the parental gate — which
/// is the whole reason it is a separate screen from the child's gallery. The
/// gallery has no delete affordance at all, because a two-year-old with a delete
/// button is a two-year-old with an empty gallery.
///
/// Parent-facing throughout, so the 44pt iOS HIG floor rather than the app's
/// usual 64. Nothing here is for the child.
struct ManagePhotosView: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var cameraAvailability = CameraController.availability
    @State private var store = PhotoStore()
    @State private var photos: [URL] = []
    @State private var isConfirmingDeleteAll = false
    @State private var savingURLs: Set<URL> = []
    @State private var saveAlert: SaveAlert?

    private static let rowHeight: CGFloat = 44
    private static let thumbnailSide: CGFloat = 56

    private struct SaveAlert: Identifiable {
        enum Kind {
            case saved
            case accessDenied
            case failed
        }

        let id = UUID()
        let kind: Kind
        let count: Int
    }

    var body: some View {
        List {
            Section {
                Text(photos.isEmpty
                     ? "No photos on this device."
                     : "\(photos.count) photo\(photos.count == 1 ? "" : "s") on this device.")
                    .font(Theme.body(14, .bold))
                    .foregroundStyle(Theme.textPrimary)
                    .frame(minHeight: Self.rowHeight, alignment: .leading)
                    .accessibilityIdentifier("manage-photos-count")
            } footer: {
                Text("Photos your child takes stay inside Cloudmoji unless you choose to save a copy to Photos. Cloudmoji originals are excluded from iCloud backup, and deleting the app deletes them.")
            }

            // **The one link out of the app, and it is behind the gate.**
            //
            // iOS asks for camera permission once. After a refusal there is no
            // way back from inside the app — the mini-app simply has no camera
            // button and no explanation a parent can act on. This is that way
            // back, and it lives here rather than on the child's screen for two
            // reasons: a two-year-old must never be able to leave the app, and
            // Kids Category review requires any link out to sit behind a
            // parental gate. Settings → grown-ups question → here is that gate.
            if cameraAvailability == .denied {
                Section {
                    Button {
                        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                        openURL(url)
                    } label: {
                        Label("Allow camera access", systemImage: "camera.badge.ellipsis")
                            .font(Theme.body(15, .bold))
                            .frame(minHeight: Self.rowHeight)
                    }
                    .accessibilityIdentifier("manage-photos-camera-settings")
                } header: {
                    Text("Camera")
                } footer: {
                    Text("Cloudmoji does not have permission to use the camera. Photos shows a grown-up recovery card, and this opens iPhone Settings where you can switch access on.")
                }
            }

            if !photos.isEmpty {
                Section {
                    Button {
                        saveToPhotoLibrary(photos)
                    } label: {
                        Label {
                            Text(savingURLs.isEmpty
                                 ? "Save all \(photos.count) to Photos"
                                 : "Saving to Photos…")
                                .font(Theme.body(15, .bold))
                        } icon: {
                            if savingURLs.isEmpty {
                                Image(systemName: "square.and.arrow.down")
                            } else {
                                ProgressView()
                                    .controlSize(.small)
                            }
                        }
                        .frame(minHeight: Self.rowHeight)
                    }
                    .disabled(!savingURLs.isEmpty)
                    .accessibilityIdentifier("manage-photos-save-all")
                } header: {
                    Text("Photo Library")
                } footer: {
                    Text("This adds copies to Apple Photos. The originals remain inside Cloudmoji until you delete them.")
                }

                Section("Photos") {
                    ForEach(photos, id: \.self) { url in
                        HStack(spacing: 12) {
                            photoImage(url, maximumPoints: Self.thumbnailSide)
                                .frame(width: Self.thumbnailSide, height: Self.thumbnailSide)
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            Spacer()
                            Button {
                                saveToPhotoLibrary([url])
                            } label: {
                                if savingURLs.contains(url) {
                                    ProgressView()
                                        .controlSize(.small)
                                        .frame(minWidth: Self.rowHeight, minHeight: Self.rowHeight)
                                        .accessibilityLabel("Saving")
                                } else {
                                    Text("Save")
                                        .font(Theme.body(14, .bold))
                                        .frame(minHeight: Self.rowHeight)
                                }
                            }
                            .buttonStyle(.borderless)
                            .disabled(!savingURLs.isEmpty)
                            .accessibilityIdentifier("manage-photo-save")

                            Button(role: .destructive) {
                                store.delete(url)
                                photos = store.photos
                                PhotoThumbnails.forget()
                            } label: {
                                Text("Delete")
                                    .font(Theme.body(14, .bold))
                                    .frame(minHeight: Self.rowHeight)
                            }
                            .buttonStyle(.borderless)
                            .disabled(!savingURLs.isEmpty)
                            .accessibilityIdentifier("manage-photo-delete")
                        }
                        .frame(minHeight: Self.rowHeight)
                    }
                }

                Section {
                    Button(role: .destructive) {
                        isConfirmingDeleteAll = true
                    } label: {
                        Text("Delete all photos")
                            .font(Theme.body(15, .bold))
                            .frame(minHeight: Self.rowHeight)
                    }
                    .accessibilityIdentifier("manage-photos-delete-all")
                    .disabled(!savingURLs.isEmpty)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Theme.background.ignoresSafeArea())
        // A `List` otherwise renders in the system light palette on a light
        // device and the whole panel comes out white — the same reason
        // `SettingsView` and `AboutView` set it.
        .preferredColorScheme(.dark)
        .navigationTitle("Photos")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            photos = store.photos
            cameraAvailability = CameraController.availability
        }
        // Returning from the Settings app is how this changes, so the row has to
        // go away again on the way back rather than on the next launch.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { cameraAvailability = CameraController.availability }
        }
        // A confirmation, because this is the one irreversible thing a parent can
        // do in this app and the photographs are somebody's afternoon.
        .confirmationDialog(
            "Delete all photos?",
            isPresented: $isConfirmingDeleteAll,
            titleVisibility: .visible
        ) {
            Button("Delete all", role: .destructive) {
                store.deleteAll()
                photos = store.photos
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This cannot be undone.")
        }
        .alert(item: $saveAlert) { alert in
            switch alert.kind {
            case .saved:
                let noun = alert.count == 1 ? "photo is" : "photos are"
                return Alert(
                    title: Text("Saved to Photos"),
                    message: Text("\(alert.count) \(noun) now in your photo library."),
                    dismissButton: .default(Text("Done"))
                )
            case .accessDenied:
                return Alert(
                    title: Text("Allow Photos Access"),
                    message: Text("Cloudmoji needs permission to add these photos to your library. You can allow access in iPhone Settings."),
                    primaryButton: .default(Text("Open Settings")) {
                        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                        openURL(url)
                    },
                    secondaryButton: .cancel()
                )
            case .failed:
                return Alert(
                    title: Text("Couldn’t Save Photos"),
                    message: Text("No photos were removed from Cloudmoji. Please try again."),
                    dismissButton: .default(Text("OK"))
                )
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("manage-photos-panel")
    }

    private func saveToPhotoLibrary(_ urls: [URL]) {
        guard savingURLs.isEmpty, !urls.isEmpty else { return }
        savingURLs = Set(urls)

        Task {
            do {
                try await PhotoLibraryExporter.save(urls)
                saveAlert = SaveAlert(kind: .saved, count: urls.count)
            } catch PhotoLibraryExporter.ExportError.accessDenied {
                saveAlert = SaveAlert(kind: .accessDenied, count: urls.count)
            } catch {
                saveAlert = SaveAlert(kind: .failed, count: urls.count)
            }
            savingURLs.removeAll()
        }
    }
}

#Preview("Manage photos") {
    NavigationStack { ManagePhotosView() }
}
