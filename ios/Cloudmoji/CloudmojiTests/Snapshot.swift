import SwiftUI
import UIKit

/// Photographs a SwiftUI view and lets a test measure the pixels.
///
/// This exists because the cheap ways of testing layout in this project have all
/// been caught passing against code that drew nothing:
///
/// * `ImageRenderer` never runs a layout pass over a lazy container and never
///   fires `.onAppear` / `.task`, so a `LazyVGrid` renders blank and an animated
///   view renders at its initial — usually invisible — state.
/// * SwiftUI does not build its accessibility tree until an assistive technology
///   asks for it, so element queries from a unit test return nothing and every
///   "each control is at least 64pt" assertion is vacuously true over an empty
///   array.
///
/// So ``Bitmap/of(_:width:height:settling:)`` puts the real view in a real key
/// window, lets UIKit lay it out, and draws it — the same route
/// `EmojiGridTests` takes, factored out because the typing row and the word
/// bubble both need it. ``Bitmap/rendered(_:)`` keeps the `ImageRenderer` path
/// for views that are neither lazy nor animated, where it is exact and cheap.
@MainActor
struct Bitmap {
    let width: Int
    let height: Int
    private let pixels: [UInt8]

    // MARK: Capture

    /// Lays `view` out at the top of a `width` × `height` key window over black
    /// and photographs it.
    ///
    /// - Parameter settling: how long to let animations and `.task` run before
    ///   the shutter. Zero for static views.
    /// - Parameter fillsWindow: pass `true` for a view that is *meant* to take
    ///   the whole window. The default pins the view to the top with a `Spacer`
    ///   below it, which is right for a row or a card — but a greedy view and a
    ///   `Spacer` are both fully flexible, so a `GeometryReader` under the
    ///   default would be handed half the window and report half the height it
    ///   will really be given. `AdaptiveShell` decides the whole app's layout
    ///   from that number.
    static func of(
        _ view: some View,
        width: CGFloat,
        height: CGFloat,
        settling: Duration = .zero,
        fillsWindow: Bool = false
    ) async -> Bitmap {
        // Only one capture at a time, across every suite in the target.
        //
        // `drawHierarchy(afterScreenUpdates:)` needs its window to be key, and
        // each capture makes a fresh one key. Swift Testing runs @MainActor tests
        // concurrently and interleaves them at every `await` — and this function
        // awaits, as does any test that sleeps to let an animation settle. So a
        // second capture could take key status away from a first one mid-flight
        // and hand it back a black rectangle, which reads as "the view drew
        // nothing". That was roughly a 1-in-20 failure across the target.
        //
        // A `.serialized` trait would not have fixed this: it orders a suite's
        // own tests, not sibling suites, and the race is between suites.
        await CaptureGate.acquire()
        defer { CaptureGate.release() }

        let host = UIHostingController(
            rootView: VStack(spacing: 0) {
                view
                if !fillsWindow {
                    Spacer(minLength: 0)
                }
            }
        )
        // The host would otherwise inherit the simulator's safe-area insets and
        // push the content 60-odd points down the image, under a scanline aimed
        // by arithmetic.
        host.safeAreaRegions = []
        host.view.backgroundColor = .black

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: width, height: height))
        // Without a scene the window never becomes visible and
        // `drawHierarchy(afterScreenUpdates:)` hands back a black rectangle —
        // which reads as a view that drew nothing.
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
            window.windowScene = scene
        }
        window.rootViewController = host
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
        host.view.layoutIfNeeded()

        if settling > .zero {
            try? await Task.sleep(for: settling)
            host.view.layoutIfNeeded()
        }

        let bounds = CGRect(x: 0, y: 0, width: width, height: height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let image = UIGraphicsImageRenderer(bounds: bounds, format: format).image { _ in
            host.view.drawHierarchy(in: bounds, afterScreenUpdates: true)
        }
        // Give up key status and let the window go, rather than leaving one
        // dangling per capture for the rest of the run.
        window.isHidden = true
        window.rootViewController = nil
        window.windowScene = nil
        return Bitmap(image.cgImage)
    }

    /// `ImageRenderer` over a static, non-lazy view — which also reports the
    /// view's own ideal size, the thing a touch-target test wants to measure.
    /// Never use it on anything animated: `.task` will not have run.
    static func rendered(_ view: some View) -> Bitmap {
        let renderer = ImageRenderer(content: view.background(Color.black))
        renderer.scale = 1
        return Bitmap(renderer.uiImage?.cgImage)
    }

    private init(_ cgImage: CGImage?) {
        guard let cgImage else {
            self.width = 0
            self.height = 0
            self.pixels = []
            return
        }
        let w = cgImage.width
        let h = cgImage.height
        var buffer = [UInt8](repeating: 0, count: w * h * 4)
        buffer.withUnsafeMutableBytes { raw in
            guard let context = CGContext(
                data: raw.baseAddress,
                width: w,
                height: h,
                bitsPerComponent: 8,
                bytesPerRow: w * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return }
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        self.width = w
        self.height = h
        self.pixels = buffer
    }

    // MARK: Reading

    struct RGB: Equatable {
        var r: Int, g: Int, b: Int
        var sum: Int { r + g + b }
    }

    func rgb(x: Int, y: Int) -> RGB {
        guard x >= 0, y >= 0, x < width, y < height else { return RGB(r: 0, g: 0, b: 0) }
        let offset = (y * width + x) * 4
        return RGB(r: Int(pixels[offset]), g: Int(pixels[offset + 1]), b: Int(pixels[offset + 2]))
    }

    /// A run of horizontally adjacent pixels brighter than `threshold` — one
    /// drawn element, as laid out.
    struct Run: Equatable {
        var start: Int
        var end: Int // exclusive
        var width: Int { end - start }
    }

    /// The runs along one scanline, ignoring everything left of `from`.
    ///
    /// `threshold` is how the caller separates the element it is measuring from
    /// whatever is painted behind it — the typing row's own 4%-white plate is
    /// itself above zero, so a threshold of 0 would report the entire row as one
    /// run.
    func runs(y: Int, threshold: Int, from: Int = 0) -> [Run] {
        guard y >= 0, y < height else { return [] }
        var runs: [Run] = []
        var current: Run?
        for x in max(0, from)..<width {
            let lit = rgb(x: x, y: y).sum > threshold
            switch (lit, current) {
            case (true, nil):
                current = Run(start: x, end: x + 1)
            case (true, .some(var run)):
                run.end = x + 1
                current = run
            case (false, .some(let run)):
                runs.append(run)
                current = nil
            case (false, nil):
                break
            }
        }
        if let run = current { runs.append(run) }
        return runs
    }

    func litPixels(threshold: Int) -> Int {
        var count = 0
        for y in 0..<height {
            for x in 0..<width where rgb(x: x, y: y).sum > threshold {
                count += 1
            }
        }
        return count
    }
}

/// A one-at-a-time gate for bitmap captures.
///
/// Everything here is already `@MainActor`, so no lock is needed — but being on
/// the main actor does NOT make a sequence of `await`s atomic, which is the whole
/// problem this solves. Waiters queue on a continuation and are handed the gate
/// in turn.
@MainActor
enum CaptureGate {
    private static var busy = false
    private static var waiting: [CheckedContinuation<Void, Never>] = []

    static func acquire() async {
        // A loop, not an `if`: being resumed means it is this waiter's turn, but
        // a re-check costs nothing and makes the invariant local rather than
        // spread across acquire and release.
        while busy {
            await withCheckedContinuation { waiting.append($0) }
        }
        busy = true
    }

    static func release() {
        busy = false
        guard !waiting.isEmpty else { return }
        waiting.removeFirst().resume()
    }
}
