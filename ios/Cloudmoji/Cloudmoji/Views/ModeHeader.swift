import SwiftUI
import CloudmojiCore

/// Every number the header is drawn from.
///
/// Values come from the shipped web header in `src/components/WordsMode.tsx` and
/// `src/components/CountMode.tsx`, with two deliberate departures, both forced by
/// rules the web does not follow:
///
/// * the web's About and mute buttons are 40pt tall, under the 44pt iOS HIG
///   minimum for parent chrome, so both grow to 44 here;
/// * the web puts 6px between them, under the 8pt floor `CLAUDE.md` sets between
///   adjacent targets, so the gap grows to 8.
///
/// Those two changes cost 12pt of width on a 375pt screen, which is why the About
/// affordance is a glyph here rather than the web's `About` text button.
enum ModeHeaderMetrics {
    /// Parent-only chrome, so the 44pt HIG minimum rather than the child-facing
    /// 64. Forcing 64 here swallows the header on a 375pt screen — the mascot,
    /// the wordmark and three controls do not fit.
    static let controlSide: CGFloat = 44

    /// Minimum gap between adjacent targets, `CLAUDE.md` rule 2.
    static let spacing: CGFloat = 8

    /// `border-radius: 14` on the web's header buttons.
    static let controlCornerRadius: CGFloat = 14
    /// `2px solid` — heavier than a tile's hairline, because these sit on the
    /// background rather than on a plate.
    static let controlBorderWidth: CGFloat = 2
    static let controlGlyphSize: CGFloat = 18

    /// `padding: 10px 14px 6px` upright, `4px 12px 2px` sideways.
    static let horizontalPadding: CGFloat = 14
    static let compactHorizontalPadding: CGFloat = 12
    static let topPadding: CGFloat = 10
    static let compactTopPadding: CGFloat = 4
    static let bottomPadding: CGFloat = 6
    static let compactBottomPadding: CGFloat = 2

    /// Design system type scale: logo 21px, tagline 10px / 800.
    static let titleSize: CGFloat = 21
    static let compactTitleSize: CGFloat = 17
    static let subtitleSize: CGFloat = 10

    static let mascotSize: CGFloat = 64
    static let compactMascotSize: CGFloat = 42

    /// Design system Active States: control buttons `scale(0.88)`.
    static let pressedScale: CGFloat = 0.88
}

/// The strip at the top of every screen: the mascot, the wordmark, and the
/// parent's controls.
///
/// One component, used by both modes. The web has four copies of this — portrait
/// and landscape in each of `WordsMode.tsx` and `CountMode.tsx` — which is the
/// same shape that let three edits land on a dead copy of the category list.
///
/// The mascot's mood is passed in rather than owned, because the mood belongs to
/// whichever screen is driving the speech.
struct ModeHeader: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact

    let mood: MascotMood
    /// "Cloudmoji" in Words mode, "Cloudculator" in Count mode. Chrome, not
    /// content — these live alongside the markup on the web too, so there is
    /// nothing in `src/data/` to generate them from.
    let title: String
    /// Shown upright only; sideways there is no height for it.
    let subtitle: String

    /// Opens the parental gate. Defaulted to a no-op so the previews and
    /// `ModeHeaderTests` can build the header on its own — the real one is wired in
    /// `ContentView`, which owns the gate.
    var onParent: () -> Void = {}

    /// Which speaker the mute button shows. Static and pure because it is the one
    /// part of this control that can be silently wrong: a button that always draws
    /// 🔊 still toggles, and the parent has no way to tell whether it worked.
    static func muteGlyph(muted: Bool) -> String {
        muted ? "🔇" : "🔊"
    }

    var body: some View {
        HStack(spacing: ModeHeaderMetrics.spacing) {
            CloudMascot(
                mood: mood,
                size: isCompact
                    ? ModeHeaderMetrics.compactMascotSize
                    : ModeHeaderMetrics.mascotSize
            )

            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(Theme.display(isCompact
                                        ? ModeHeaderMetrics.compactTitleSize
                                        : ModeHeaderMetrics.titleSize))
                    .foregroundStyle(Theme.teal)
                    // The wordmark is the only elastic thing in a strip whose
                    // other four items are fixed. Without these it wins the
                    // negotiation on a 375pt screen and pushes the picker off.
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                if !isCompact {
                    Text(subtitle)
                        .font(Theme.body(ModeHeaderMetrics.subtitleSize, .heavy))
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)

            parentControl
            muteControl
            languagePicker
        }
        .padding(.horizontal, isCompact
                 ? ModeHeaderMetrics.compactHorizontalPadding
                 : ModeHeaderMetrics.horizontalPadding)
        .padding(.top, isCompact
                 ? ModeHeaderMetrics.compactTopPadding
                 : ModeHeaderMetrics.topPadding)
        .padding(.bottom, isCompact
                 ? ModeHeaderMetrics.compactBottomPadding
                 : ModeHeaderMetrics.bottomPadding)
    }

    /// The one door to the parent's half of the app. Everything behind it is
    /// gated: the web's ungated `About` button and its five-tap gesture on the
    /// wordmark are both replaced by this, because a gesture is something a parent
    /// has to be told about and the only place to tell them is behind it — and
    /// because Kids Category review looks for a gate in front of anything that is
    /// not the game.
    private var parentControl: some View {
        ModeHeaderControl(
            // A colour emoji, so it keeps its own colours rather than taking
            // the button's accent tint the way a text symbol would.
            glyph: "⚙️",
            label: "Grown-ups only",
            identifier: "parent-btn",
            tint: Theme.teal,
            isOn: false,
            action: onParent
        )
    }

    /// The control that was 90% built and unreachable: `SettingsStore.muted`
    /// persisted, `TypingRow` hid replay when it was set, `WordsView.speak` had a
    /// muted branch with its own mood recovery, and nothing anywhere set it. The
    /// app spoke through the hardware silent switch — `.playback` is deliberate —
    /// so a parent's only recourse was the volume buttons.
    private var muteControl: some View {
        ModeHeaderControl(
            glyph: Self.muteGlyph(muted: model.settings.muted),
            label: model.settings.muted ? "Unmute" : "Mute",
            identifier: "mute-btn",
            tint: Theme.coral,
            isOn: model.settings.muted
        ) {
            model.settings.muted.toggle()
        }
    }

    /// Unchanged from Stage 2a, including its identifier — `WordsModeUITests`
    /// measures it by name.
    private var languagePicker: some View {
        @Bindable var settings = model.settings
        return Picker("Language", selection: $settings.language) {
            ForEach(model.availableLanguages) { meta in
                Text(meta.short).tag(meta.id)
            }
        }
        .pickerStyle(.menu)
        // A menu picker draws its current value as tinted text, which is the
        // system accent blue unless it is said otherwise.
        .tint(Theme.textPrimary)
        .frame(minWidth: ModeHeaderMetrics.controlSide,
               minHeight: ModeHeaderMetrics.controlSide)
        // The frame alone does NOT grow a menu picker's hit area — it lays out at
        // 62 x 34 and only the text is tappable, which the UI tests measured.
        .contentShape(Rectangle())
        .accessibilityIdentifier("lang-picker")
    }
}

/// One parent-chrome button. 44pt, the iOS HIG minimum — **not** the 64pt
/// child-facing floor, which governs the grid, the chips and the tab bar.
///
/// Its own type so the 44pt rule can be measured off a real render rather than
/// read back out of a constant, the same reason `TypedEmojiButton` is its own
/// type.
struct ModeHeaderControl: View {
    let glyph: String
    /// English: VoiceOver here is for the parent, not the child.
    let label: String
    let identifier: String
    /// The wash when the control is on. Off is the neutral surface.
    let tint: Color
    let isOn: Bool
    let action: () -> Void

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: ModeHeaderMetrics.controlCornerRadius, style: .continuous)
    }

    var body: some View {
        Button(action: action) {
            Text(glyph)
                // 🔊 and 🔇 are colour emoji and are unaffected, but any text
                // symbol put in here would take the button's tint — which is the
                // system accent blue unless it is said otherwise.
                .foregroundStyle(Theme.textPrimary)
                .font(.system(size: ModeHeaderMetrics.controlGlyphSize))
                .frame(width: ModeHeaderMetrics.controlSide,
                       height: ModeHeaderMetrics.controlSide)
                .background(isOn ? tint.opacity(0.2) : Theme.surface, in: shape)
                .overlay(
                    shape.stroke(
                        isOn ? tint.opacity(0.3) : Theme.surfaceBorderStrong,
                        lineWidth: ModeHeaderMetrics.controlBorderWidth
                    )
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: ModeHeaderMetrics.pressedScale))
        .accessibilityLabel(label)
        .accessibilityIdentifier(identifier)
    }
}

#Preview("Header, upright and sideways") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 24) {
            ModeHeader(mood: .happy, title: "Cloudmoji", subtitle: "Tap. Listen. Learn!")
            ModeHeader(mood: .beaming, title: "Cloudculator", subtitle: "🧮 Let's count!")
                .environment(\.cloudmojiIsCompact, true)
        }
        .frame(width: 375)
    }
    .environment(AppModel())
}
