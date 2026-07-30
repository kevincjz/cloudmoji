import SwiftUI

/// The one-screen tour a parent sees once, and can always get back to.
///
/// It exists because the ⚙️ → gate → Settings path is otherwise undiscoverable:
/// nothing on the child's screen suggests Settings is there at all, and the two
/// facts a new parent most needs — that mute lives *in the app* because Cloudmoji
/// plays through the hardware silent switch on purpose, and that there is a
/// grown-ups screen behind an arithmetic question — are both invisible until
/// someone says them.
///
/// Two presentations, one view:
///
/// * **First launch**, as a sheet, with the big Got it button. Not behind the
///   parental gate — a parent installing the app should not have to solve a sum
///   to find out what it does, and the gate still protects Settings itself.
/// * **From Settings**, pushed like `AboutView`, with no button: the navigation
///   bar's back control is the way out, and the row that pushed it is already
///   behind the gate.
///
/// The first-launch presentation is deliberately **not a wall**. The person
/// holding the phone when an app first opens may well be the two-year-old it was
/// bought for, so there is exactly one obvious control, it is enormous, and
/// nothing is lost when a child mashes through it — the Settings row brings the
/// whole thing back.
///
/// English only, like `AboutView` and the FAQ. This is parent chrome; inventing
/// five translations of it with no native-speaker pass would be worse than not
/// translating it.
struct TutorialView: View {

    struct Step: Identifiable {
        /// Explicit rather than slugged from `title`, for the same reason
        /// `AboutView.Entry` does it: the identifier a UI test looks up stays
        /// stable when the wording is edited, and stays plain ASCII.
        let id: String
        /// Shown at the head of the row. A colour emoji in every case, so it
        /// keeps its own colours rather than taking the accent tint.
        let glyph: String
        let title: String
        let detail: String
    }

    /// Six steps, and no more. This is a toddler app, not an enterprise
    /// onboarding flow — a parent who reads the first two lines has already
    /// learnt the whole product, and the rest exist only because they cover
    /// things that cannot be discovered by tapping.
    static let steps: [Step] = [
        Step(
            id: "tap",
            glyph: "👆",
            title: "Tap an emoji, hear the word",
            detail: """
                Start with Words and Count in English. Tap anything in the grid and \
                Cloudmoji says it out loud. A grown-up can find plan details and \
                all parent controls in Grown-ups.
                """
        ),
        Step(
            id: "launcher",
            glyph: "🗣️",
            title: "More worlds with Full Cloudmoji",
            detail: """
                Full Cloudmoji adds Music, Flash Cards, Animals, Photos and Sleepy \
                Cloud, plus four more languages and the Apple Watch experience. \
                Purchase details stay inside the gated Grown-ups screen.
                """
        ),
        Step(
            id: "home",
            glyph: "☁️",
            title: "The cloud brings you home",
            detail: """
                Inside a mini-app there is a big cloud with a small house along the \
                bottom. Tapping it goes home. It is always in the same place, and \
                it is the only way out.
                """
        ),
        Step(
            id: "typing-row",
            glyph: "⌨️",
            title: "The row along the top",
            detail: """
                Everything your child taps in Words mode collects there. Tap any \
                one of them to hear it again, 🔊 replays the lot, ⌫ removes the \
                last one and ✕ clears the row.
                """
        ),
        Step(
            id: "mute",
            glyph: "🔊",
            title: "Mute lives in the app",
            detail: """
                Cloudmoji plays through the phone's silent switch on purpose. Sound \
                is controlled in the grown-ups screen; if it is off, sound-based \
                mini-apps show a large 🔊 button to turn it back on.
                """
        ),
        Step(
            id: "settings",
            glyph: "⚙️",
            title: "Grown-ups settings",
            detail: """
                The locked Grown-ups button on Home opens them, behind a simple sum \
                to keep small fingers out. Inside you can choose sound, categories, \
                how high Count mode goes and which languages are available with your \
                plan. You can also see what the free version includes and what Full \
                Cloudmoji unlocks.
                """
        ),
    ]

    /// Supplied only by the first-launch sheet. `nil` when this is pushed from
    /// Settings, where the navigation bar already has a way back and a second
    /// one would be clutter.
    var onDone: (() -> Void)?

    /// Comfortably past the 44pt HIG floor for parent chrome. This one control
    /// is the exception that gets extra: it is the only thing on a first-run
    /// screen, and it must be unmissable to somebody who is not reading.
    private static let doneHeight: CGFloat = 56
    /// `CLAUDE.md` rule 2, the floor between adjacent targets — kept as the gap
    /// between the scrolling copy and the button so the last line of text is
    /// never something you can catch with the same thumb.
    private static let doneInset: CGFloat = 16

    var body: some View {
        // The button sits **outside** the `ScrollView`, not at the end of its
        // content. Inside, it would be below the fold on a 6.1" phone and the
        // one obvious way out of a first-run screen would be a scroll away —
        // which is precisely the wall this is not allowed to be.
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    heading
                    ForEach(Self.steps) { step in
                        row(step)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 24)
                // Without this the column shrinks to its text and the whole
                // tour centres itself in a narrow band on an iPad.
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if let onDone {
                Button(action: onDone) {
                    Text("Got it")
                        // Text, so it takes the accent tint unless this says
                        // otherwise — the same trap the gate's buttons carry.
                        .font(Theme.body(17, .black))
                        .foregroundStyle(Theme.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: Self.doneHeight)
                        .background(
                            Theme.teal.opacity(0.32),
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Theme.teal.opacity(0.55), lineWidth: 2)
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressScale(scale: 0.88))
                .padding(.horizontal, 20)
                .padding(.top, Self.doneInset)
                .padding(.bottom, 20)
                .accessibilityIdentifier("tutorial-done")
            }
        }
        .background(Theme.background.ignoresSafeArea())
        // Set for the same reason `SettingsView` and `AboutView` set it: without
        // it the navigation bar and the scroll indicators come back in the
        // system light palette on a light device.
        .preferredColorScheme(.dark)
        // A no-op in the first-launch sheet, which has no navigation bar; this
        // is for the pushed-from-Settings presentation.
        .navigationTitle("How to use Cloudmoji")
        .navigationBarTitleDisplayMode(.inline)
        // Load-bearing here, unlike on `AboutView`'s `List`. This is a plain
        // `VStack` around a `ScrollView`, so the identifier below propagates
        // down and overwrites `tutorial-done` and every `tutorial-step-*` —
        // exactly what made the typing row's three controls unreachable in
        // stage 2a. Measured, not assumed: removing this line takes
        // `TutorialUITests` red on the step and button lookups.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("tutorial-panel")
    }

    private var heading: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                CloudMascot(mood: .happy, size: 72)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Welcome to Cloudmoji")
                        .font(Theme.display(24))
                        .foregroundStyle(Theme.teal)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Text("Tap. Listen. Learn!")
                        .font(Theme.body(12, .heavy))
                        .foregroundStyle(Theme.textSecondary)
                }
            }

            Text("Thirty seconds, and you will know everything there is to know.")
                .font(Theme.body(13, .bold))
                .foregroundStyle(Theme.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.bottom, 2)
    }

    private func row(_ step: Step) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text(step.glyph)
                .font(.system(size: 26))
                // A fixed column, so five glyphs of different widths do not
                // leave five differently-indented paragraphs.
                .frame(width: 34, alignment: .leading)

            VStack(alignment: .leading, spacing: 4) {
                Text(step.title)
                    .font(Theme.body(15, .black))
                    .foregroundStyle(Theme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(step.detail)
                    .font(Theme.body(13, .bold))
                    .foregroundStyle(Theme.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        // Combined rather than contained: a step is one thing to read, and
        // VoiceOver stepping through it as glyph-then-title-then-body is worse.
        // The identifier then has an element of its own to land on instead of
        // propagating into the two `Text`s.
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("tutorial-step-\(step.id)")
    }
}

#Preview("Tutorial, first launch") {
    TutorialView(onDone: {})
}

#Preview("Tutorial, from Settings") {
    NavigationStack { TutorialView() }
}
