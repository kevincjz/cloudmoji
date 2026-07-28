import SwiftUI

/// How the cloud is feeling. The child never sets this directly — it follows
/// taps and speech, and `beaming` (a milestone) outranks everything else.
///
/// The raw values are a contract with the UI tests: `CloudMascot` publishes the
/// current mood as the accessibility identifier `mascot-<rawValue>`, which is
/// the only way a UI test can observe that the celebration ever happens.
enum MascotMood: String, CaseIterable, Hashable {
    case happy, excited, speaking, beaming
}

// MARK: - Motion

/// The three `@keyframes` the web mascot cycles between, ported from
/// `src/index.css`. Kept as data rather than inlined in the view so the timings
/// can be checked against the stylesheet without rendering anything.
enum MascotMotion: CaseIterable, Hashable {
    /// `mascotFloat` — idle drift.
    case float
    /// `mascotBounce` — while a word is being spoken.
    case bounce
    /// `mascotBeam` — milestone celebration; already scaled up at rest.
    case beam

    /// The CSS `animation-duration`, which covers a whole 0% → 100% round trip.
    var cssDuration: Double {
        switch self {
        case .float: 3.0
        case .bounce: 0.4
        case .beam: 0.6
        }
    }

    /// What SwiftUI wants. `repeatForever(autoreverses: true)` treats the
    /// duration as one leg of the round trip, so a CSS keyframe that returns to
    /// its start at 100% is half as long here. Passing `cssDuration` straight
    /// through would run the mascot at half speed — a sedated bounce that still
    /// looks plausible in isolation, which is exactly why it is worth a test.
    var halfCycle: Double { cssDuration / 2 }

    /// Peak `translateY`, in points, at the mascot's reference size of 64.
    /// The web keyframes are absolute pixels against a 64px mascot.
    var referenceLift: CGFloat {
        switch self {
        case .float: 4
        case .bounce: 3
        case .beam: 6
        }
    }

    /// `transform: scale()` at 0%.
    var restScale: CGFloat {
        switch self {
        case .float, .bounce: 1.0
        case .beam: 1.08
        }
    }

    /// `transform: scale()` at 50%.
    var peakScale: CGFloat {
        switch self {
        case .float: 1.0
        case .bounce: 1.06
        case .beam: 1.15
        }
    }

    func lift(for size: CGFloat) -> CGFloat { referenceLift * size / 64 }

    var animation: Animation {
        // `ease-in-out` for the idle drift, CSS `ease` for the two energetic
        // ones — `ease` is cubic-bezier(0.25, 0.1, 0.25, 1), which SwiftUI has
        // no named equivalent for.
        let curve: Animation = switch self {
        case .float: .easeInOut(duration: halfCycle)
        case .bounce, .beam: .timingCurve(0.25, 0.1, 0.25, 1, duration: halfCycle)
        }
        return curve.repeatForever(autoreverses: true)
    }
}

// MARK: - Style

/// A twinkle drawn beside the cloud. Coordinates are the SVG text origin
/// (left edge, baseline) in the 120 × 78 viewBox.
struct Sparkle: Identifiable, Hashable {
    let id: Int
    let glyph: String
    let x: CGFloat
    let y: CGFloat
    let fontSize: CGFloat
    /// CSS `animation-duration` for the shared `sparkle` keyframe.
    let cssDuration: Double
    let delay: Double
}

/// Every appearance decision the mood drives, resolved in one place.
///
/// The view then draws exactly what it is told. This is the part of the mascot
/// worth testing: how it *looks* is a judgement for the eye, but which face
/// goes with which mood is a lookup table that can silently rot.
struct MascotStyle: Hashable {
    enum Eyes: Hashable {
        /// A gentle upward arc — the resting face.
        case arc
        /// Round open eyes, paired with the speaking mouth.
        case dot
        /// Five-pointed stars.
        case star
        /// A wider, flatter arc: squinting with delight.
        case squint
    }

    enum Mouth: Hashable {
        /// An outlined curve, no fill.
        case smile
        /// A small filled curve.
        case grin
        /// A filled ellipse — mid-word.
        case openRound
        /// A wide filled curve with an outline.
        case wideGrin
    }

    var eyes: Eyes
    var mouth: Mouth
    var motion: MascotMotion
    var blushRadii: CGSize
    var blushColor: Color
    var blushOpacity: Double
    var showsGlow: Bool
    var sparkles: [Sparkle]
    /// The `drop-shadow` cast beneath the whole character.
    var glowShadow: Color

    init(_ mood: MascotMood) {
        let isBeaming = mood == .beaming

        eyes = switch mood {
        case .happy: .arc
        case .speaking: .dot
        case .excited: .star
        case .beaming: .squint
        }

        mouth = switch mood {
        case .happy: .smile
        case .speaking: .openRound
        case .excited: .grin
        case .beaming: .wideGrin
        }

        motion = switch mood {
        case .happy, .excited: .float
        case .speaking: .bounce
        case .beaming: .beam
        }

        // Rosier and fuller while beaming.
        blushRadii = isBeaming ? CGSize(width: 10, height: 5.5) : CGSize(width: 8, height: 4.5)
        blushColor = isBeaming ? Theme.blushBeaming : Theme.blush
        blushOpacity = isBeaming ? 0.7 : 0.55

        showsGlow = isBeaming
        glowShadow = isBeaming ? Theme.gold.opacity(0.5) : Theme.teal.opacity(0.35)

        sparkles = switch mood {
        case .happy, .speaking: []
        case .excited: Self.baseSparkles
        case .beaming: Self.baseSparkles + Self.beamingSparkles
        }
    }

    private static let baseSparkles: [Sparkle] = [
        Sparkle(id: 0, glyph: "✨", x: 102, y: 24, fontSize: 10, cssDuration: 0.6, delay: 0),
        Sparkle(id: 1, glyph: "✨", x: 12, y: 28, fontSize: 8, cssDuration: 0.8, delay: 0.2),
    ]

    private static let beamingSparkles: [Sparkle] = [
        Sparkle(id: 2, glyph: "⭐", x: 4, y: 50, fontSize: 9, cssDuration: 0.7, delay: 0.1),
        Sparkle(id: 3, glyph: "⭐", x: 110, y: 48, fontSize: 9, cssDuration: 0.9, delay: 0.4),
        Sparkle(id: 4, glyph: "🌟", x: 58, y: 12, fontSize: 11, cssDuration: 0.5, delay: 0.3),
    ]
}

// MARK: - View

/// The cloud character.
///
/// Drawn as vector shapes in the same 120 × 78 coordinate space as the web SVG,
/// then scaled as a whole. Two things fall out of that: the geometry is a
/// literal transcription of `src/components/CloudMascot.tsx`, so a change on
/// either side is a readable diff; and it stays crisp anywhere between the 42pt
/// landscape header and a 120pt splash without a second set of numbers.
struct CloudMascot: View {
    let mood: MascotMood
    var size: CGFloat = 64

    /// The web viewBox. Every coordinate below is in these units.
    private static let artWidth: CGFloat = 120
    private static let artHeight: CGFloat = 78

    private var style: MascotStyle { MascotStyle(mood) }

    var body: some View {
        art
            .frame(width: Self.artWidth, height: Self.artHeight)
            .scaleEffect(size / Self.artWidth)
            // The SVG letterboxes: `preserveAspectRatio` fits the 120 × 78
            // viewBox inside a `size × size * 0.78` box, so the drawing is
            // centred with a sliver of space above and below. Reproduced here
            // so callers can lay the mascot out against the same box.
            .frame(width: size, height: size * 0.78)
            .modifier(Breathing(motion: style.motion, size: size))
            // A CSS `animation-name` swap abandons the old cycle rather than
            // blending into it. Re-keying on the motion family does the same:
            // `Breathing` gets fresh state and starts its new cycle from rest.
            // Note happy and excited share `.float`, so drifting between them
            // does not jolt.
            .id(style.motion)
            // Flatten first. `.shadow` is applied to each drawn primitive, not
            // to the composite, so without this the six overlapping cloud
            // shapes each cast a shadow onto their neighbours — the silhouette
            // turns glassy and seamed instead of reading as one solid cloud.
            // The web gets this free: `filter: drop-shadow()` acts on the
            // rendered element.
            .compositingGroup()
            .shadow(color: style.glowShadow, radius: size * 0.075, x: 0, y: size * 0.06)
            .animation(.easeInOut(duration: 0.3), value: style.glowShadow)
            // NOT `.accessibilityHidden(true)`, which is what shipped and what made
            // every mood unobservable: a hidden element is absent from the tree, so
            // no UI test can assert the mascot ever celebrates. Deleting the
            // celebration outright left the entire Stage 2a suite green.
            //
            // `children: .ignore` collapses the twenty-odd shapes into one element.
            // The label is real rather than empty because this element is now
            // visible to VoiceOver, and an unlabelled one announces as "image" —
            // "Cloudmoji" is both truthful and the better thing for a parent
            // running VoiceOver to hear. The mood rides on the identifier, which
            // VoiceOver never speaks.
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Cloudmoji")
            .accessibilityIdentifier("mascot-\(mood.rawValue)")
    }

    private var art: some View {
        ZStack {
            if style.showsGlow { beamGlow }
            cloudBody
            cloudHighlights
            undersideShadow
            blush
            eyes
            mouth
            sparkles
        }
    }

    // MARK: Cloud

    private var cloudBody: some View {
        ZStack {
            circle(cx: 30, cy: 46, r: 20)
            circle(cx: 52, cy: 36, r: 23)
            circle(cx: 72, cy: 30, r: 26)
            circle(cx: 94, cy: 42, r: 19)
            circle(cx: 42, cy: 44, r: 16)
            RoundedRectangle(cornerRadius: 12)
                .frame(width: 96, height: 24)
                .position(x: 60, y: 60)
        }
        .foregroundStyle(Theme.cloudWhite)
    }

    private var cloudHighlights: some View {
        ZStack {
            circle(cx: 72, cy: 22, r: 12)
            circle(cx: 50, cy: 30, r: 8).opacity(0.7)
        }
        .foregroundStyle(Theme.cloudHighlight)
    }

    private var undersideShadow: some View {
        Ellipse()
            .frame(width: 88, height: 12)
            .position(x: 60, y: 68)
            .foregroundStyle(Theme.cloudShadow)
            .opacity(0.4)
    }

    // MARK: Face

    private var blush: some View {
        let r = style.blushRadii
        return ZStack {
            Ellipse().frame(width: r.width * 2, height: r.height * 2).position(x: 34, y: 58)
            Ellipse().frame(width: r.width * 2, height: r.height * 2).position(x: 86, y: 58)
        }
        .foregroundStyle(style.blushColor)
        .opacity(style.blushOpacity)
    }

    @ViewBuilder private var eyes: some View {
        switch style.eyes {
        case .arc:
            // The `◠` of the web face, redrawn as a path so it does not depend
            // on a font shipping that glyph.
            arcEyes(halfWidth: 3.5, baseline: 52, apex: 49, lineWidth: 1.8)
        case .squint:
            arcEyes(halfWidth: 7, baseline: 51, apex: 48, lineWidth: 2.5)
        case .dot:
            ZStack {
                circle(cx: 46, cy: 50, r: 4.2)
                circle(cx: 74, cy: 50, r: 4.2)
            }
            .foregroundStyle(Theme.eyes)
        case .star:
            starEyes
                .fill(Theme.eyes)
                .frame(width: Self.artWidth, height: Self.artHeight)
        }
    }

    @ViewBuilder private var mouth: some View {
        switch style.mouth {
        case .smile:
            // Outline only — the resting mouth is a line, not a shape.
            quad(from: CGPoint(x: 54, y: 61), to: CGPoint(x: 66, y: 61), control: CGPoint(x: 60, y: 66))
                .stroke(Theme.eyes, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                .frame(width: Self.artWidth, height: Self.artHeight)
        case .grin:
            quad(from: CGPoint(x: 53, y: 60), to: CGPoint(x: 67, y: 60), control: CGPoint(x: 60, y: 69))
                .fill(Theme.mouth)
                .frame(width: Self.artWidth, height: Self.artHeight)
        case .wideGrin:
            // Fill auto-closes the curve, as SVG does; the stroke stays open,
            // so the top of the grin is a clean edge rather than a drawn line.
            let grin = quad(from: CGPoint(x: 46, y: 59), to: CGPoint(x: 74, y: 59), control: CGPoint(x: 60, y: 74))
            ZStack {
                grin.fill(Theme.mouth)
                grin.stroke(Theme.mouthStroke, lineWidth: 0.5)
            }
            .frame(width: Self.artWidth, height: Self.artHeight)
        case .openRound:
            ZStack {
                Ellipse().fill(Theme.mouth)
                Ellipse().stroke(Theme.mouthStroke, lineWidth: 0.5)
            }
            .frame(width: 11, height: 9) // rx 5.5, ry 4.5
            .position(x: 60, y: 62)
        }
    }

    // MARK: Celebration

    private var beamGlow: some View {
        Circle()
            .fill(
                RadialGradient(
                    stops: [
                        .init(color: Theme.gold.opacity(0.6), location: 0),
                        .init(color: Theme.gold.opacity(0), location: 1),
                    ],
                    center: .center,
                    startRadius: 0,
                    endRadius: 52
                )
            )
            .frame(width: 104, height: 104)
            // Twinkle before positioning, so the pulse scales about the glow's
            // own centre instead of the centre of the whole viewBox.
            .modifier(Twinkle(cssDuration: 1.2, delay: 0))
            .position(x: 60, y: 45)
    }

    private var sparkles: some View {
        ForEach(style.sparkles) { sparkle in
            Text(sparkle.glyph)
                .font(.system(size: sparkle.fontSize))
                .modifier(Twinkle(cssDuration: sparkle.cssDuration, delay: sparkle.delay))
                // SVG `<text>` anchors at its left edge on the baseline; a
                // SwiftUI `Text` centres in its frame. These offsets put the
                // glyph roughly where the stylesheet puts it.
                .position(x: sparkle.x + sparkle.fontSize * 0.58,
                          y: sparkle.y - sparkle.fontSize * 0.4)
        }
    }

    // MARK: Drawing helpers

    private func circle(cx: CGFloat, cy: CGFloat, r: CGFloat) -> some View {
        Circle().frame(width: r * 2, height: r * 2).position(x: cx, y: cy)
    }

    /// Both eyes as one upward arc each, mirrored about the face centre.
    private func arcEyes(halfWidth: CGFloat, baseline: CGFloat, apex: CGFloat, lineWidth: CGFloat) -> some View {
        // A quadratic curve only reaches halfway to its control point, so the
        // control sits twice as far above the endpoints as the visible apex.
        let controlY = 2 * apex - baseline
        var path = Path()
        for cx in [CGFloat(46), CGFloat(74)] {
            path.move(to: CGPoint(x: cx - halfWidth, y: baseline))
            path.addQuadCurve(to: CGPoint(x: cx + halfWidth, y: baseline),
                              control: CGPoint(x: cx, y: controlY))
        }
        return path
            .stroke(Theme.eyes, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
            .frame(width: Self.artWidth, height: Self.artHeight)
    }

    private var starEyes: Path {
        var path = Path()
        path.addPath(Self.star(cx: 46, cy: 50, radius: 5.5))
        path.addPath(Self.star(cx: 74, cy: 50, radius: 5.5))
        return path
    }

    private func quad(from: CGPoint, to: CGPoint, control: CGPoint) -> Path {
        var path = Path()
        path.move(to: from)
        path.addQuadCurve(to: to, control: control)
        return path
    }

    /// A regular five-pointed star, drawn rather than typed: the `★` the web
    /// uses is a font glyph, and iOS has no guarantee about its metrics.
    private static func star(cx: CGFloat, cy: CGFloat, radius: CGFloat) -> Path {
        var path = Path()
        let inner = radius * 0.382 // the waist of a regular pentagram
        for i in 0..<10 {
            let r = i.isMultiple(of: 2) ? radius : inner
            let angle = -CGFloat.pi / 2 + CGFloat(i) * .pi / 5
            let point = CGPoint(x: cx + r * cos(angle), y: cy + r * sin(angle))
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}

// MARK: - Animation modifiers

/// The idle / bounce / beam cycle.
private struct Breathing: ViewModifier {
    let motion: MascotMotion
    let size: CGFloat

    @State private var lifted = false

    func body(content: Content) -> some View {
        content
            .scaleEffect(lifted ? motion.peakScale : motion.restScale)
            .offset(y: lifted ? -motion.lift(for: size) : 0)
            .onAppear { withAnimation(motion.animation) { lifted = true } }
    }
}

/// The shared `sparkle` keyframe: opacity 0.2 → 1, scale 0.7 → 1.3.
private struct Twinkle: ViewModifier {
    let cssDuration: Double
    let delay: Double

    @State private var bright = false

    func body(content: Content) -> some View {
        content
            .opacity(bright ? 1.0 : 0.2)
            .scaleEffect(bright ? 1.3 : 0.7)
            .onAppear {
                withAnimation(
                    .timingCurve(0.25, 0.1, 0.25, 1, duration: cssDuration / 2)
                        .repeatForever(autoreverses: true)
                        .delay(delay)
                ) { bright = true }
            }
    }
}

#Preview("Moods") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 40) {
            ForEach(MascotMood.allCases, id: \.self) { mood in
                HStack(spacing: 36) {
                    CloudMascot(mood: mood, size: 42)
                    CloudMascot(mood: mood, size: 64)
                    CloudMascot(mood: mood, size: 110)
                }
            }
        }
        .padding(40)
    }
}
