import AVFoundation
import Foundation

/// Everything `AVCaptureSession` needs, confined to one queue.
///
/// `@unchecked Sendable` with a written-down reason: `AVCaptureSession` and
/// `AVCapturePhotoOutput` are not `Sendable`, but every call against them in this
/// file happens on ``CameraController``'s single serial queue — which is exactly
/// the discipline Apple's own capture documentation asks for. The one exception
/// is the preview layer, which reads `session` on the main thread; handing a
/// session to a preview layer is the documented way to do it.
/// `nonisolated` because this module builds with `SWIFT_DEFAULT_ACTOR_ISOLATION
/// = MainActor`: without it the box would be main-actor isolated and the whole
/// point — touching it from the capture queue — would be a concurrency error.
private nonisolated final class CaptureBox: @unchecked Sendable {
    let session = AVCaptureSession()
    let output = AVCapturePhotoOutput()
}

/// Whether there is a camera to use, and whether we may.
enum CameraAvailability: String, Hashable {
    /// No camera at all — every simulator, and an iPad with the camera
    /// restricted by Screen Time.
    case unavailable
    /// There is one, and nobody has been asked yet.
    case needsPermission
    /// A grown-up said no. Photos replaces the camera tile with a useful,
    /// parent-gated recovery action that opens this app's iPhone Settings page.
    case denied
    case ready
}

/// The camera, as much of it as a toddler needs.
///
/// **Video input only, never audio.** The privacy copy says Cloudmoji never asks
/// for the microphone, and that sentence has to stay true — so there is no
/// `AVCaptureDevice.default(for: .audio)` anywhere in this file and there must
/// never be one. A still-photo output needs no audio input, so this costs
/// nothing.
///
/// `stop()` is called from three places — `.onDisappear`, `goHome`, and
/// backgrounding — deliberately redundantly. A green camera indicator that
/// outlives the mini-app it belongs to is a trust catastrophe in a kids app, and
/// the redundancy is cheaper than being right once.
@MainActor
final class CameraController {

    private let box = CaptureBox()
    private let queue = DispatchQueue(label: "app.cloudmoji.camera")

    private(set) var isConfigured = false
    private var pendingCapture: PhotoCaptureDelegate?
    /// When the last shutter fired. A toddler holds the button down and taps it
    /// forty times; without this the disk fills with forty near-identical
    /// photographs of a carpet.
    private var lastCaptureAt: Date?

    static let captureDebounce: TimeInterval = 1.0

    /// The session, for the preview layer. Nothing else should touch it.
    var session: AVCaptureSession { box.session }

    /// Whether the hardware exists. Static because the answer is a property of
    /// the device, and the gallery has to know it before building a controller.
    static var hasCamera: Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
    }

    static var availability: CameraAvailability {
        guard hasCamera else { return .unavailable }
        return switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: .ready
        case .denied, .restricted: .denied
        case .notDetermined: .needsPermission
        @unknown default: .denied
        }
    }

    /// Resolves the current authorization state, asking only when iOS still has
    /// a system prompt available. Calling `requestAccess` after denial cannot
    /// display that prompt again, so the Photos screen routes a grown-up to the
    /// app's iPhone Settings page instead.
    func requestAccess() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            true
        case .notDetermined:
            await AVCaptureDevice.requestAccess(for: .video)
        case .denied, .restricted:
            false
        @unknown default:
            false
        }
    }

    /// Builds the graph and starts it. Safe to call repeatedly.
    func start() {
        let box = self.box
        let needsConfiguring = !isConfigured
        isConfigured = true
        queue.async {
            if needsConfiguring { Self.configure(box) }
            guard !box.session.isRunning else { return }
            box.session.startRunning()
        }
    }

    func stop() {
        let box = self.box
        queue.async {
            guard box.session.isRunning else { return }
            box.session.stopRunning()
        }
    }

    /// Whether a capture may start now. Pure, so the debounce can be tested
    /// without a camera — and separate from ``capture`` so the *caller* can ask
    /// before it lights the flash.
    static func acceptsCapture(now: Date, lastCaptureAt: Date?) -> Bool {
        guard let lastCaptureAt else { return true }
        return now.timeIntervalSince(lastCaptureAt) >= captureDebounce
    }

    /// Takes one photograph. Returns `false` when the shutter was pressed a
    /// moment ago and this press was swallowed — in which case **the completion
    /// is never called**, which is exactly why the answer has to come back.
    ///
    /// It used to return nothing and simply drop the request, and the caller
    /// raised its white flash *before* asking. A toddler doing what toddlers do
    /// to a big white button therefore left the flash up with no completion
    /// coming to take it down: the viewfinder went white and stayed white until
    /// the mini-app was closed. The whole failure lived in the gap between "I
    /// asked" and "I was refused".
    @discardableResult
    func capture(_ completion: @escaping @MainActor (Data?) -> Void) -> Bool {
        let now = Date()
        guard Self.acceptsCapture(now: now, lastCaptureAt: lastCaptureAt) else { return false }
        lastCaptureAt = now

        let delegate = PhotoCaptureDelegate { data in
            Task { @MainActor in
                self.pendingCapture = nil
                completion(data)
            }
        }
        // Held, because `capturePhoto(with:delegate:)` does not retain its
        // delegate and a deallocated one is a photograph that never arrives.
        pendingCapture = delegate

        let box = self.box
        queue.async {
            guard box.session.isRunning else {
                delegate.finish(nil)
                return
            }
            box.output.capturePhoto(with: AVCapturePhotoSettings(), delegate: delegate)
        }
        return true
    }

    /// Runs on ``queue`` only — see ``CaptureBox``.
    private nonisolated static func configure(_ box: CaptureBox) {
        box.session.beginConfiguration()
        box.session.sessionPreset = .photo

        // Video only. See the type's note: adding an audio input here would make
        // the privacy copy false.
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
           let input = try? AVCaptureDeviceInput(device: device),
           box.session.canAddInput(input) {
            box.session.addInput(input)
        }
        if box.session.canAddOutput(box.output) {
            box.session.addOutput(box.output)
        }
        box.session.commitConfiguration()
    }
}

/// One capture's worth of delegate.
///
/// A separate object rather than a conformance on `CameraController`, because
/// `AVCapturePhotoCaptureDelegate` callbacks arrive on a background queue and
/// `CameraController` is main-actor isolated. The only thing that crosses back is
/// `Data`, which is `Sendable`.
private nonisolated final class PhotoCaptureDelegate: NSObject, AVCapturePhotoCaptureDelegate, @unchecked Sendable {
    private let completion: @Sendable (Data?) -> Void
    /// So a failure path and a success path cannot both call back.
    private var hasFinished = false
    private let lock = NSLock()

    init(completion: @escaping @Sendable (Data?) -> Void) {
        self.completion = completion
    }

    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        finish(error == nil ? photo.fileDataRepresentation() : nil)
    }

    func finish(_ data: Data?) {
        lock.lock()
        let alreadyDone = hasFinished
        hasFinished = true
        lock.unlock()
        guard !alreadyDone else { return }
        completion(data)
    }
}
