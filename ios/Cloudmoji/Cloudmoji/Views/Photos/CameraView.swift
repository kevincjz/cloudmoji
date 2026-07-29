import AVFoundation
import SwiftUI

/// The viewfinder and one enormous button.
///
/// Presented full screen from the gallery rather than routed through the
/// launcher, so it keeps its own way out: a child who opened the camera by
/// accident gets the same cloud he gets everywhere else, and it does the same
/// thing.
struct CameraView: View {
    @Environment(\.scenePhase) private var scenePhase

    let caption: String
    let onCapture: (Data?) -> Void
    let onDone: () -> Void

    @State private var camera = CameraController()
    /// The white flash that says a picture was taken. The only feedback a
    /// two-year-old will read, since the gallery is behind him at that moment.
    @State private var isFlashing = false

    /// Bigger than the 72pt preferred size and bigger than the 64pt floor: it is
    /// the only control on the screen and it is pressed with a thumb while both
    /// hands are holding a phone at arm's length.
    static let shutterSide: CGFloat = 88

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            CameraPreview(session: camera.session)
                .ignoresSafeArea()

            if isFlashing {
                Color.white.ignoresSafeArea().transition(.opacity)
            }

            VStack {
                Text(caption)
                    .font(Theme.body(14, .black))
                    .foregroundStyle(Theme.textPrimary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.35), in: Capsule())
                    .padding(.top, 12)

                Spacer()

                HStack(alignment: .bottom) {
                    CloudHomeButton(action: leave)
                    Spacer()
                    shutter
                    Spacer()
                    // A spacer the width of the home button, so the shutter is
                    // centred on the screen rather than centred on what is left
                    // of it. An off-centre shutter is a shutter a child misses.
                    Color.clear.frame(width: HomeButtonMetrics.side, height: 1)
                }
                .padding(.horizontal, HomeButtonMetrics.inset)
                .padding(.bottom, HomeButtonMetrics.inset)
            }
        }
        .animation(.easeOut(duration: 0.18), value: isFlashing)
        .onAppear { camera.start() }
        // Three exits, deliberately redundant. A green camera indicator that
        // outlives the screen is a trust catastrophe in a kids app.
        .onDisappear { camera.stop() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { camera.start() } else { camera.stop() }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("camera-panel")
    }

    private var shutter: some View {
        Button(action: capture) {
            Circle()
                .fill(Color.white)
                .frame(width: Self.shutterSide, height: Self.shutterSide)
                .overlay(
                    Circle()
                        .stroke(Color.white.opacity(0.5), lineWidth: 4)
                        .padding(-8)
                )
                .contentShape(Circle())
        }
        .buttonStyle(PressScale(scale: 0.9))
        .accessibilityLabel("Take a picture")
        .accessibilityIdentifier("camera-shutter")
    }

    private func capture() {
        // **Ask first, then light the flash.** The order is the whole fix.
        //
        // The flash used to go up before the request, and a debounced request
        // never calls back — so a toddler drumming on the shutter left the
        // viewfinder white with nothing coming to take it down again. Raising it
        // only for a capture that was actually accepted means every flash has a
        // completion that ends it.
        guard camera.capture({ data in
            isFlashing = false
            onCapture(data)
        }) else { return }

        // The reward pattern rather than the tap knock: a photograph is a thing
        // finished, which is the distinction `Haptics` draws.
        Haptics.reward()
        isFlashing = true
    }

    private func leave() {
        camera.stop()
        onDone()
    }
}

/// `AVCaptureVideoPreviewLayer`, which has no SwiftUI equivalent.
///
/// The layer is resized in `layoutSubviews` rather than in `updateUIView`: a
/// `CALayer` inside a `UIView` does not inherit autoresizing, so without this the
/// preview keeps whatever size the view had on its first layout pass and a
/// rotation leaves a letterboxed picture in the corner.
private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        let previewLayer = AVCaptureVideoPreviewLayer()

        override init(frame: CGRect) {
            super.init(frame: frame)
            layer.addSublayer(previewLayer)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError("not used from a nib") }

        override func layoutSubviews() {
            super.layoutSubviews()
            previewLayer.frame = bounds
        }
    }
}
