import Foundation

/// Where a child's photographs live until a grown-up explicitly saves a copy.
///
/// Three decisions here are the whole of "photos stay on this device", and each
/// one is load-bearing:
///
/// * **Application Support, not the photo library.** A toddler's shutter presses
///   stay in the app rather than appearing automatically in the parent's camera
///   roll or iCloud Photos. Only `PhotoLibraryExporter`, reached through the
///   parental gate, copies a photo out when a grown-up asks.
/// * **Excluded from backup.** Without this the folder rides along in the
///   device's iCloud backup, which is a copy of the child's photographs on
///   Apple's servers — true, defensible, and not what the About screen says.
/// * **Complete file protection.** The file is unreadable while the phone is
///   locked, which is the state a phone handed to a toddler spends most of its
///   life in.
///
/// The directory is injectable so tests get a temporary one. A test that wrote
/// into the real Application Support folder would leak into every run after it.
@MainActor
final class PhotoStore {

    enum StoreError: Error {
        case directoryUnavailable
        /// The file was written but could not be marked as excluded from iCloud
        /// backup. The photo has already been deleted by the time this is
        /// thrown — see ``save(_:)``.
        case couldNotExcludeFromBackup
    }

    let directory: URL
    private let now: () -> Date

    init(directory: URL? = nil, now: @escaping () -> Date = Date.init) {
        self.directory = directory ?? Self.defaultDirectory
        self.now = now
    }

    static var defaultDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            // `.applicationSupportDirectory` is always present in an app
            // container. The fallback exists so this is not a force-unwrap in
            // code that runs while a child is holding the phone.
            ?? URL.temporaryDirectory
        return base.appending(path: "Photos", directoryHint: .isDirectory)
    }

    /// Newest first, which is the order a child expects: the thing he just
    /// photographed is the first thing he sees.
    ///
    /// Sorted on the file *name* rather than a creation-date resource value,
    /// because the name carries a millisecond timestamp put there for exactly
    /// this purpose — and because file-system timestamps have a coarser
    /// resolution than two shutter presses a second apart.
    var photos: [URL] {
        let contents = (try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: nil,
            options: [.skipsHiddenFiles]
        )) ?? []
        return contents
            .filter { $0.pathExtension == Self.fileExtension }
            .sorted { $0.lastPathComponent > $1.lastPathComponent }
    }

    static let fileExtension = "jpg"

    /// How a photograph is written. Named rather than spelled inline at the one
    /// call site, because the Simulator does not implement data protection at all
    /// — its container sits on the Mac's filesystem, which has no such attribute
    /// — so `attributesOfItem` reports nothing there and an on-disk assertion is
    /// vacuous in the only environment the tests run in. Asserting on the
    /// constant is shallow, but it does catch the edit that matters: somebody
    /// dropping `.completeFileProtection` while tidying up.
    static let writeOptions: Data.WritingOptions = [.atomic, .completeFileProtection]

    /// Writes one photograph and returns where it went.
    ///
    /// Throws rather than failing quietly: the caller shows the child a shutter
    /// animation either way, but a parent looking at an empty gallery deserves
    /// the failure to have been real somewhere.
    @discardableResult
    func save(_ data: Data) throws -> URL {
        try ensureDirectory()
        let stamp = Int(now().timeIntervalSince1970 * 1000)
        let url = directory.appending(
            path: "\(stamp)-\(UUID().uuidString).\(Self.fileExtension)"
        )
        try data.write(to: url, options: Self.writeOptions)

        // **No photograph without the exclusion.**
        //
        // This was `try?`, which is the wrong shape for this particular line:
        // "photos are excluded from iCloud backup" is an absolute promise made
        // to a parent in `AboutView`, and a best-effort implementation of an
        // absolute promise is a promise that is sometimes false — silently, and
        // in the direction that uploads a child's photographs. If the mark will
        // not stick, the file goes away again and the caller is told. A missing
        // photo is a disappointment; a photo in someone's iCloud backup that the
        // About screen says cannot exist is a broken commitment.
        do {
            try Self.excludeFromBackup(url)
        } catch {
            delete(url)
            throw StoreError.couldNotExcludeFromBackup
        }
        return url
    }

    func delete(_ url: URL) {
        try? FileManager.default.removeItem(at: url)
    }

    func deleteAll() {
        for url in photos { delete(url) }
    }

    var count: Int { photos.count }

    private func ensureDirectory() throws {
        var isDirectory: ObjCBool = false
        if FileManager.default.fileExists(atPath: directory.path(), isDirectory: &isDirectory) {
            guard isDirectory.boolValue else { throw StoreError.directoryUnavailable }
            return
        }
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete]
        )
        // The folder as well as each file: a folder that is backed up carries
        // anything written into it later, including by a future version of this
        // app that forgets to mark its own writes. Not `try?` — if the folder
        // cannot be marked, no photo should be written into it, and throwing
        // here is what stops the first one.
        try Self.excludeFromBackup(directory)
    }

    /// Static and separate so the test that checks the flag is checking the same
    /// code path `save` uses, rather than a second spelling of it.
    static func excludeFromBackup(_ url: URL) throws {
        var target = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try target.setResourceValues(values)
    }

    /// Reads the flag back off disk. Used by the tests, and by nothing else —
    /// the app sets it and trusts it.
    static func isExcludedFromBackup(_ url: URL) -> Bool {
        (try? url.resourceValues(forKeys: [.isExcludedFromBackupKey]))?.isExcludedFromBackup ?? false
    }
}
