import SwiftUI

/// Where the cloud is in one breath.
///
/// Its own type rather than a `MascotMood` case, and that is a rule not a
/// preference: `MascotMood`'s raw values are the `mascot-<mood>` identifiers the
/// UI suites read the celebration out of, and `MascotMood.arbitrate` is product
/// law about which face outranks which. A sleeping face is neither — it is one
/// screen's animation state, and putting it in the shared enum would make every
/// exhaustive `switch` on mood in the app grow a case it can never see.
enum BreathPhase: String, Hashable {
    case inhale, hold, exhale
    /// The session is over. The cloud stops moving and the loop stops running.
    case asleep
}

/// The breathing itself: timings, easing, and where the scale is at time *t*.
///
/// Pure and static, because it is the part of this screen that can be silently
/// wrong. How the cloud *looks* asleep is a judgement for the eye; whether a
/// four-second inhale actually takes four seconds is arithmetic, and arithmetic
/// that drifted by a beat would still look plausible on a screenshot.
///
/// Timings are `reference/breathing-cloud.jsx` verbatim: 4s in, 2s hold, 6s out.
enum BreathingSession {
    static let inhale: TimeInterval = 4
    static let hold: TimeInterval = 2
    static let exhale: TimeInterval = 6
    static var cycle: TimeInterval { inhale + hold + exhale }

    /// `scale(0.75)` at the bottom of an exhale, `scale(1.1)` at the top of an
    /// inhale, and a little smaller than either once asleep.
    static let restScale: Double = 0.75
    static let peakScale: Double = 1.10
    static let asleepScale: Double = 0.72

    /// The durations a grown-up may pick, in minutes.
    static let choices: [Int] = [2, 5, 10]

    /// Where the cloud is at `t` seconds into a session of `duration` seconds.
    ///
    /// `duration <= 0` means "no session picked yet", and returns the resting
    /// pose rather than declaring the session instantly over — the picker draws
    /// a still cloud through this same function.
    static func state(at t: TimeInterval, duration: TimeInterval) -> (scale: Double, phase: BreathPhase) {
        guard duration > 0 else { return (restScale, .inhale) }
        guard t < duration else { return (asleepScale, .asleep) }

        // Negative time cannot happen — the caller measures forward from a start
        // date — but a clock that stepped backwards must not produce a scale of
        // NaN in front of a child who is trying to fall asleep.
        let p = max(0, t).truncatingRemainder(dividingBy: cycle)

        if p < inhale {
            return (restScale + eased(p / inhale) * (peakScale - restScale), .inhale)
        }
        if p < inhale + hold {
            return (peakScale, .hold)
        }
        let k = (p - inhale - hold) / exhale
        return (peakScale - eased(k) * (peakScale - restScale), .exhale)
    }

    /// The prototype's `0.5 - 0.5 * cos(pi * k)` — an ease-in-out over 0...1.
    /// Linear breathing reads as a machine; this is what makes it read as a
    /// chest.
    private static func eased(_ k: Double) -> Double {
        0.5 - 0.5 * cos(.pi * k)
    }

    /// How far through the session, 0...1. Drives both the dimming and the
    /// progress line.
    static func progress(at t: TimeInterval, duration: TimeInterval) -> Double {
        guard duration > 0 else { return 0 }
        return min(max(t / duration, 0), 1)
    }
}

/// The sleeping cloud.
///
/// Its own view rather than a fifth `MascotMood`, for the reason in
/// ``BreathPhase``. The body geometry is the same 120 × 78 transcription
/// `CloudMascot` uses — same circles, same rounded base, same underside shadow —
/// so the two clouds are recognisably the same character; only the face and the
/// glow differ.
struct BreathingCloud: View {
    let scale: Double
    let phase: BreathPhase
    /// 0 at the start of a session, 0.55 at the end. Everything that glows fades
    /// against it, so the screen gets quieter as the child does.
    var dim: Double = 0

    /// The web viewBox. Every coordinate below is in these units.
    private static let artWidth: CGFloat = 120
    private static let artHeight: CGFloat = 78

    /// Drawn at 200 × 130 in the prototype, which is this viewBox at 1.667×.
    static let renderedWidth: CGFloat = 200

    /// Sideways. A landscape phone gives about 400pt of height, and 130 of it
    /// for the cloud plus a title, three buttons and a caption overflowed —
    /// the cloud came out clipped against the top edge of the screen.
    static let compactRenderedWidth: CGFloat = 132

    /// How wide this cloud is actually drawn. Injected rather than read from the
    /// environment so the previews and the tests can size it directly.
    var width: CGFloat = BreathingCloud.renderedWidth

    private var isAsleep: Bool { phase == .asleep }
    /// Eyes close on the hold as well as in sleep — the pause at the top of a
    /// breath is where a drowsy face settles.
    private var eyesClosed: Bool { isAsleep || phase == .hold }

    var body: some View {
        art
            .frame(width: Self.artWidth, height: Self.artHeight)
            .scaleEffect(width / Self.artWidth)
            .frame(width: width, height: width * 0.65)
            // The breath itself. Applied outside the art's own scale so the two
            // do not fight: this one is driven every frame by `TimelineView`,
            // which is why it carries no `.animation` of its own.
            .scaleEffect(scale)
            // Flatten first, or the six overlapping cloud shapes each cast a
            // shadow onto their neighbours and the silhouette turns glassy —
            // the same trap `CloudMascot` documents.
            .compositingGroup()
            .shadow(color: Theme.moonlight.opacity(0.28 * (1 - dim)), radius: 14 * scale, y: 8)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Cloudmoji")
            // Deliberately **not** `mascot-<mood>`: a second element answering to
            // that identifier would make every celebration lookup in
            // `WordsModeUITests` ambiguous.
            .accessibilityIdentifier("sleepy-cloud-\(phase.rawValue)")
    }

    private var art: some View {
        ZStack {
            halo
            cloudBody
            cloudHighlights
            undersideShadow
            blush
            eyes
            mouth
            if isAsleep { zzz }
        }
    }

    private var halo: some View {
        Ellipse()
            .fill(
                RadialGradient(
                    stops: [
                        .init(color: Theme.moonlight.opacity(0.5), location: 0),
                        .init(color: Theme.moonlight.opacity(0), location: 1),
                    ],
                    center: .center, startRadius: 0, endRadius: 54
                )
            )
            .frame(width: 108, height: 72)
            .position(x: 60, y: 46)
            .opacity(0.35 * (1 - dim))
    }

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

    private var blush: some View {
        ZStack {
            Ellipse().frame(width: 16, height: 9).position(x: 34, y: 58)
            Ellipse().frame(width: 16, height: 9).position(x: 86, y: 58)
        }
        .foregroundStyle(Theme.blush)
        .opacity(0.42)
    }

    @ViewBuilder private var eyes: some View {
        if eyesClosed {
            closedEyes
        } else {
            ZStack {
                Ellipse().frame(width: 4.8, height: 5.6).position(x: 46, y: 51)
                Ellipse().frame(width: 4.8, height: 5.6).position(x: 74, y: 51)
            }
            .foregroundStyle(Theme.eyes)
        }
    }

    /// `M40 52 Q46 47 52 52` and its mirror, as a path so it does not depend on
    /// a font shipping the arc glyph.
    private var closedEyes: some View {
        var path = Path()
        for cx in [CGFloat(46), CGFloat(74)] {
            path.move(to: CGPoint(x: cx - 6, y: 52))
            path.addQuadCurve(to: CGPoint(x: cx + 6, y: 52), control: CGPoint(x: cx, y: 42))
        }
        return path
            .stroke(Theme.eyes, style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
            .frame(width: Self.artWidth, height: Self.artHeight)
    }

    @ViewBuilder private var mouth: some View {
        switch phase {
        case .asleep:
            Ellipse()
                .fill(Theme.blushBeaming.opacity(0.75))
                .frame(width: 8, height: 6)
                .position(x: 60, y: 62)
        case .inhale:
            // A small round mouth, the way a person taking a breath in has one.
            Ellipse()
                .fill(Theme.blushBeaming.opacity(0.7))
                .frame(width: 9, height: 8)
                .position(x: 60, y: 62)
        case .hold, .exhale:
            quad(from: CGPoint(x: 55, y: 61), to: CGPoint(x: 65, y: 61), control: CGPoint(x: 60, y: 69))
                .stroke(Theme.eyes.opacity(0.75), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                .frame(width: Self.artWidth, height: Self.artHeight)
        }
    }

    private var zzz: some View {
        ZStack {
            Text("z")
                .font(Theme.body(12, .black))
                .foregroundStyle(Theme.moonlight.opacity(0.8))
                .position(x: 101, y: 14)
            Text("z")
                .font(Theme.body(9, .black))
                .foregroundStyle(Theme.moonlight.opacity(0.6))
                .position(x: 109, y: 5)
        }
    }

    private func circle(cx: CGFloat, cy: CGFloat, r: CGFloat) -> some View {
        Circle().frame(width: r * 2, height: r * 2).position(x: cx, y: cy)
    }

    private func quad(from: CGPoint, to: CGPoint, control: CGPoint) -> Path {
        var path = Path()
        path.move(to: from)
        path.addQuadCurve(to: to, control: control)
        return path
    }
}

#Preview("Breathing, all four phases") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack {
            BreathingCloud(scale: 1.1, phase: .inhale)
            BreathingCloud(scale: 1.1, phase: .hold)
            BreathingCloud(scale: 0.75, phase: .exhale)
            BreathingCloud(scale: 0.72, phase: .asleep, dim: 0.55)
        }
    }
}
