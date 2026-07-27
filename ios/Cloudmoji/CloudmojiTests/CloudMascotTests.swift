import SwiftUI
import Testing
@testable import Cloudmoji

/// What the mascot *looks* like is a judgement for the eye — no assertion here
/// substitutes for opening the preview. What can be pinned is the wiring:
/// which face belongs to which mood, and the timings ported out of
/// `src/index.css`, both of which can rot without anything failing to build.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("CloudMascot")
@MainActor
struct CloudMascotTests {

    // MARK: Mood → face

    /// The strongest cheap check on a four-way switch: a copy-paste slip that
    /// gives two moods the same face is invisible in code review and obvious to
    /// a child, who stops getting feedback that anything changed.
    @Test("no two moods wear the same face")
    func facesAreDistinct() {
        let faces = MascotMood.allCases.map { mood -> MascotStyle.Eyes in MascotStyle(mood).eyes }
        #expect(Set(faces).count == MascotMood.allCases.count)

        let mouths = MascotMood.allCases.map { mood -> MascotStyle.Mouth in MascotStyle(mood).mouth }
        #expect(Set(mouths).count == MascotMood.allCases.count)
    }

    /// Tables live in the body rather than in `@Test(arguments:)` because the
    /// app target builds main-actor-by-default and the macro evaluates its
    /// argument expression outside that isolation.
    @Test("each mood maps to the face the web mascot draws")
    func faceForMood() {
        let expected: [(MascotMood, MascotStyle.Eyes, MascotStyle.Mouth)] = [
            (.happy, .arc, .smile),
            (.excited, .star, .grin),
            (.speaking, .dot, .openRound),
            (.beaming, .squint, .wideGrin),
        ]
        #expect(expected.count == MascotMood.allCases.count)

        for (mood, eyes, mouth) in expected {
            let style = MascotStyle(mood)
            #expect(style.eyes == eyes, "\(mood) eyes")
            #expect(style.mouth == mouth, "\(mood) mouth")
        }
    }

    // MARK: Mood → motion

    /// Happy and excited deliberately share the idle drift: on the web they are
    /// the same `animation-name`, so flicking between them on every tap must not
    /// restart the float and jolt the cloud.
    @Test("each mood runs the keyframe the stylesheet gives it")
    func motionForMood() {
        let expected: [(MascotMood, MascotMotion)] = [
            (.happy, .float),
            (.excited, .float),
            (.speaking, .bounce),
            (.beaming, .beam),
        ]
        for (mood, motion) in expected {
            #expect(MascotStyle(mood).motion == motion, "\(mood)")
        }
    }

    // MARK: Timing

    /// CSS `animation-duration` covers the whole 0% → 100% round trip;
    /// SwiftUI's `repeatForever(autoreverses: true)` treats its duration as one
    /// leg. Forgetting the halving runs the mascot at half speed, which still
    /// looks like a working animation.
    @Test("keyframe durations survive the CSS-to-SwiftUI halving")
    func durations() {
        let expected: [(MascotMotion, Double, Double)] = [
            (.float, 3.0, 1.5),
            (.bounce, 0.4, 0.2),
            (.beam, 0.6, 0.3),
        ]
        #expect(expected.count == MascotMotion.allCases.count)

        for (motion, css, half) in expected {
            #expect(motion.cssDuration == css, "\(motion) css duration")
            #expect(abs(motion.halfCycle - half) < 0.0001, "\(motion) half cycle")
        }
    }

    /// Speaking is the fastest cycle and idle the slowest — that ordering is
    /// what makes speech read as "the cloud is talking" rather than drifting.
    @Test("the speaking bounce is the quickest cycle and the idle drift the slowest")
    func relativePace() {
        #expect(MascotMotion.bounce.cssDuration < MascotMotion.beam.cssDuration)
        #expect(MascotMotion.beam.cssDuration < MascotMotion.float.cssDuration)
    }

    /// The web keyframes are absolute pixels against a 64px mascot. A lift that
    /// ignored `size` would be invisible on the 120pt splash and enormous on the
    /// 42pt landscape header.
    @Test("the lift scales with the mascot")
    func liftScales() {
        #expect(MascotMotion.float.lift(for: 64) == 4)
        #expect(MascotMotion.float.lift(for: 128) == 8)
        #expect(MascotMotion.float.lift(for: 32) == 2)
        #expect(MascotMotion.beam.lift(for: 64) == 6)
        #expect(MascotMotion.bounce.lift(for: 64) == 3)
    }

    /// Beaming is the only mood that is already scaled up at rest — that is what
    /// makes the milestone read as a step change rather than a faster wobble.
    @Test("only beaming sits larger than life at rest")
    func restScales() {
        #expect(MascotMotion.float.restScale == 1)
        #expect(MascotMotion.bounce.restScale == 1)
        #expect(MascotMotion.beam.restScale > 1)
    }

    @Test("every motion peaks no smaller than it rests")
    func peaksAreNotShrinks() {
        for motion in MascotMotion.allCases {
            #expect(motion.peakScale >= motion.restScale, "\(motion) shrinks at its peak")
        }
    }

    // MARK: Celebration

    /// `beaming` is the 10/25/50/100-tap milestone. Everything that marks it out
    /// — the glow, the extra stars, the gold cast — belongs to it alone; leaking
    /// any of it into an ordinary tap devalues the milestone.
    @Test("the celebration extras belong to beaming alone")
    func celebrationIsExclusiveToBeaming() {
        for mood in MascotMood.allCases where mood != .beaming {
            #expect(MascotStyle(mood).showsGlow == false, "\(mood) should not glow")
        }
        #expect(MascotStyle(.beaming).showsGlow)
    }

    @Test("sparkles appear only when the cloud is excited or beaming, and beaming gets more")
    func sparkleCounts() {
        #expect(MascotStyle(.happy).sparkles.isEmpty)
        #expect(MascotStyle(.speaking).sparkles.isEmpty)
        #expect(MascotStyle(.excited).sparkles.count == 2)
        #expect(MascotStyle(.beaming).sparkles.count == 5)
    }

    /// `ForEach` needs stable, unique ids; duplicates make SwiftUI drop rows, so
    /// stars would silently go missing from the milestone.
    @Test("sparkle ids are unique")
    func sparkleIDsAreUnique() {
        let sparkles = MascotStyle(.beaming).sparkles
        #expect(Set(sparkles.map(\.id)).count == sparkles.count)
    }

    /// Staggered so they twinkle out of step; identical timings read as one
    /// blinking block.
    @Test("sparkles are staggered")
    func sparklesAreStaggered() {
        let sparkles = MascotStyle(.beaming).sparkles
        #expect(Set(sparkles.map(\.cssDuration)).count > 1)
        #expect(Set(sparkles.map(\.delay)).count > 1)
    }

    /// Teal under the cloud normally, gold while beaming — the cast on the
    /// background is half of what sells the milestone.
    @Test("the cast shadow turns gold only while beaming")
    func shadowColour() {
        #expect(MascotStyle(.beaming).glowShadow == Theme.gold.opacity(0.5))
        for mood in MascotMood.allCases where mood != .beaming {
            #expect(MascotStyle(mood).glowShadow == Theme.teal.opacity(0.35), "\(mood)")
        }
    }

    // MARK: Blush

    @Test("the beaming blush is both fuller and rosier")
    func blushGrowsWhenBeaming() {
        let resting = MascotStyle(.happy)
        let beaming = MascotStyle(.beaming)
        #expect(beaming.blushRadii.width > resting.blushRadii.width)
        #expect(beaming.blushRadii.height > resting.blushRadii.height)
        #expect(beaming.blushOpacity > resting.blushOpacity)
        #expect(beaming.blushColor == Theme.blushBeaming)
        #expect(resting.blushColor == Theme.blush)
    }

    /// Cheeks are always visible — the cloud is never a blank face.
    @Test("the blush is always drawn")
    func blushIsAlwaysPresent() {
        for mood in MascotMood.allCases {
            let style = MascotStyle(mood)
            #expect(style.blushOpacity > 0, "\(mood)")
            #expect(style.blushRadii.width > 0 && style.blushRadii.height > 0, "\(mood)")
        }
    }
}
