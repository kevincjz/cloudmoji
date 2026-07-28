import UIKit

/// The taps a child feels.
///
/// Sound is the point of this app, but it is also the part a parent switches
/// off — in a waiting room, beside a sleeping sibling, on a bus. Haptics are
/// what is left when the volume is down: the confirmation that the tile a
/// toddler aimed at is the tile that answered. So this is deliberately NOT tied
/// to the mute setting; muting silences the phone, it does not mean "stop
/// responding to me".
///
/// Two textures, matching the two things the mascot already distinguishes:
/// a firm knock for "you tapped a thing", and the system's success pattern for
/// "you finished a thing". A child who cannot yet read a progress dot can still
/// feel the difference.
@MainActor
enum Haptics {
    /// Held rather than created per tap. A generator that has not been prepared
    /// spins up its hardware on first use, and the resulting delay is long
    /// enough to break the link between the finger landing and the buzz — which
    /// is the entire value of the thing at this age.
    private static let tapGenerator = UIImpactFeedbackGenerator(style: .heavy)
    private static let rewardGenerator = UINotificationFeedbackGenerator()

    /// Call when a screen appears, and after each event, so the next one is warm.
    /// Cheap, and the Taptic Engine powers back down on its own.
    static func prepare() {
        tapGenerator.prepare()
        rewardGenerator.prepare()
    }

    /// One emoji, one count tile — the ordinary tap.
    static func tap() {
        // `.heavy` at full intensity — the strongest of the standard impacts.
        //
        // Tuned by hand on a real phone, not by theory. The first build used
        // `.light`, reasoning that a toddler mashing tiles would turn anything
        // stronger into a continuous rumble; the phone said "I don't feel any
        // haptics". `.medium` at full intensity still read as light. Each step
        // was a guess corrected by someone holding the device, which is the only
        // way this can be judged — the Simulator has no Taptic Engine at all.
        //
        // This is the ceiling for `UIImpactFeedbackGenerator`. If it is still not
        // enough, the next step is CoreHaptics with a custom transient, which can
        // hold a sharper attack for longer than a single system impact.
        tapGenerator.impactOccurred(intensity: 1.0)
        tapGenerator.prepare()
    }

    /// A milestone in Words mode, a finished round in Count mode. The same
    /// moments the mascot beams for, so the two rewards land together.
    static func reward() {
        rewardGenerator.notificationOccurred(.success)
        rewardGenerator.prepare()
    }
}
