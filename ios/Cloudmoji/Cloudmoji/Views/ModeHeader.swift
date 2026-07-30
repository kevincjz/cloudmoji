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
    static let padControlSide: CGFloat = 54

    /// Minimum gap between adjacent targets, `CLAUDE.md` rule 2.
    static let spacing: CGFloat = 8

    /// `border-radius: 14` on the web's header buttons.
    static let controlCornerRadius: CGFloat = 14
    /// `2px solid` — heavier than a tile's hairline, because these sit on the
    /// background rather than on a plate.
    static let controlBorderWidth: CGFloat = 2
    static let controlGlyphSize: CGFloat = 18
    static let padControlGlyphSize: CGFloat = 22

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
    static let padTitleSize: CGFloat = 29
    static let padSubtitleSize: CGFloat = 13

    static let mascotSize: CGFloat = 64
    static let compactMascotSize: CGFloat = 42
    static let padMascotSize: CGFloat = 86

    /// `font-size: 14; font-weight: 900` on the web's `LangToggle` button.
    static let languageLabelSize: CGFloat = 14
    static let padLanguageLabelSize: CGFloat = 17

    /// Fixed, not intrinsic, and both halves of that are deliberate.
    ///
    /// 62 is what the menu picker it replaced laid out at, so the width budget
    /// `ModeHeaderTests` measures — mascot, wordmark, three controls, 375pt — is
    /// unchanged. And a fixed width means the strip does not reflow when the
    /// label changes: cycling EN → 中文 → BM → 日本語 → TL through an intrinsically
    /// sized button would shuffle the whole header sideways under the child's
    /// finger on every tap.
    ///
    /// It fits the longest label with room to spare: 日本語 is three CJK glyphs at
    /// 14pt, so about 42pt inside a 58pt interior.
    static let languageControlWidth: CGFloat = 62
    static let padLanguageControlWidth: CGFloat = 78

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
    @Environment(\.cloudmojiLayout) private var layout

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
        HStack(spacing: layout.isExpandedPad ? 12 : ModeHeaderMetrics.spacing) {
            CloudMascot(
                mood: mood,
                size: layout.isExpandedPad
                    ? ModeHeaderMetrics.padMascotSize
                    : (isCompact
                       ? ModeHeaderMetrics.compactMascotSize
                       : ModeHeaderMetrics.mascotSize)
            )

            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(
                        Theme.display(
                            layout.isExpandedPad
                                ? ModeHeaderMetrics.padTitleSize
                                : (isCompact
                                   ? ModeHeaderMetrics.compactTitleSize
                                   : ModeHeaderMetrics.titleSize)
                        )
                    )
                    .foregroundStyle(Theme.teal)
                    // The wordmark is the only elastic thing in a strip whose
                    // other four items are fixed. Without these it wins the
                    // negotiation on a 375pt screen and pushes the picker off.
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                if !isCompact {
                    Text(subtitle)
                        .font(
                            Theme.body(
                                layout.isExpandedPad
                                    ? ModeHeaderMetrics.padSubtitleSize
                                    : ModeHeaderMetrics.subtitleSize,
                                .heavy
                            )
                        )
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)

            parentControl
            muteControl
            languageToggle
        }
        .padding(
            .horizontal,
            layout.isExpandedPad
                ? 24
                : (isCompact
                   ? ModeHeaderMetrics.compactHorizontalPadding
                   : ModeHeaderMetrics.horizontalPadding)
        )
        .padding(
            .top,
            layout.isExpandedPad
                ? 18
                : (isCompact
                   ? ModeHeaderMetrics.compactTopPadding
                   : ModeHeaderMetrics.topPadding)
        )
        .padding(
            .bottom,
            layout.isExpandedPad
                ? 10
                : (isCompact
                   ? ModeHeaderMetrics.compactBottomPadding
                   : ModeHeaderMetrics.bottomPadding)
        )
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

    /// One tap, one language. This was a `.menu` `Picker` until a 27-month-old
    /// met it: opening a menu, reading five rows and landing on one is three
    /// separate skills he does not have, and the language stopped changing at
    /// all. A button that advances to the next enabled language is one tap, one
    /// action, one visible result — `CLAUDE.md` rule 3, applied to the one piece
    /// of parent chrome the child had learned to reach for.
    ///
    /// Keeps the identifier `lang-picker` even though it is no longer a picker:
    /// `WordsModeUITests` measures it by name, and renaming it would silently
    /// retire that test rather than fail it.
    private var languageToggle: some View {
        let languages = model.availableLanguages
        let current = languages.first { $0.id == model.effectiveLanguage }
        let next = Self.nextMeta(in: languages, after: model.effectiveLanguage)
        let canCycle = model.canCycleLanguage

        return LanguageToggle(
            // The fallback cannot happen — `SettingsStore` re-resolves the active
            // language whenever either side of the invariant moves — but a blank
            // button would be a worse way to find that out than a wrong one.
            label: current?.short ?? model.effectiveLanguage.rawValue.uppercased(),
            // English, and spelled out: VoiceOver here is for the parent. The
            // short label alone would have it read "BM" and "TL" as letters.
            voiceOverLabel: "Language: \(current?.name ?? model.effectiveLanguage.rawValue)",
            voiceOverValue: current?.short ?? "",
            voiceOverHint: canCycle
                ? "Switches to \(next?.name ?? "the next language")"
                : "The only language switched on in Settings",
            isEnabled: canCycle,
            action: { model.cycleLanguage() }
        )
    }

    /// The language this control would move to next. Pure and static so the
    /// wrap-around can be tested without a view, and shared with `AppModel` so the
    /// hint can never promise a different language from the one the tap delivers.
    static func nextMeta(in languages: [LanguageMeta], after current: Language) -> LanguageMeta? {
        let next = AppModel.nextLanguage(after: current, in: languages.map(\.id))
        return languages.first { $0.id == next }
    }
}

/// The language button: the current language's own short name, tapped to advance
/// to the next one the parent left enabled.
///
/// Its own type for the same reason `ModeHeaderControl` is — so the 44pt parent
/// chrome floor can be measured off a real render rather than read back out of
/// the constant that set it.
struct LanguageToggle: View {
    @Environment(\.cloudmojiLayout) private var layout

    let label: String
    let voiceOverLabel: String
    let voiceOverValue: String
    let voiceOverHint: String
    /// False when this is the only language the parent left on. The button stays
    /// on screen — it is the only place the current language is written down —
    /// but it is visibly disabled rather than tappable and inert. A control that
    /// answers every tap with nothing is exactly the failure state rule 4 forbids,
    /// and the child would keep tapping it.
    let isEnabled: Bool
    let action: () -> Void

    private var shape: RoundedRectangle {
        RoundedRectangle(
            cornerRadius: layout.isExpandedPad ? 18 : ModeHeaderMetrics.controlCornerRadius,
            style: .continuous
        )
    }

    var body: some View {
        Button(action: action) {
            Text(label)
                // A text label inside a Button takes the button's accent tint,
                // which is the system blue unless it is said otherwise. The two
                // controls beside this one get away with saying nothing only
                // because 🔊 and ⚙️ are colour emoji.
                .foregroundStyle(Theme.textPrimary)
                .font(
                    Theme.body(
                        layout.isExpandedPad
                            ? ModeHeaderMetrics.padLanguageLabelSize
                            : ModeHeaderMetrics.languageLabelSize,
                        .black
                    )
                )
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(
                    width: layout.isExpandedPad
                        ? ModeHeaderMetrics.padLanguageControlWidth
                        : ModeHeaderMetrics.languageControlWidth,
                    height: layout.isExpandedPad
                        ? ModeHeaderMetrics.padControlSide
                        : ModeHeaderMetrics.controlSide
                )
                .background(Theme.surface, in: shape)
                .overlay(
                    shape.stroke(
                        Theme.surfaceBorderStrong,
                        lineWidth: ModeHeaderMetrics.controlBorderWidth
                    )
                )
                // The frame alone does NOT grow the hit area: without this only
                // the glyphs themselves are tappable. The menu picker this
                // replaced shipped that way — 62 x 34, beside a comment claiming
                // 44 — and nothing failed.
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: ModeHeaderMetrics.pressedScale))
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.5)
        .accessibilityLabel(voiceOverLabel)
        .accessibilityValue(voiceOverValue)
        .accessibilityHint(voiceOverHint)
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
    @Environment(\.cloudmojiLayout) private var layout

    let glyph: String
    /// English: VoiceOver here is for the parent, not the child.
    let label: String
    let identifier: String
    /// The wash when the control is on. Off is the neutral surface.
    let tint: Color
    let isOn: Bool
    let action: () -> Void

    private var shape: RoundedRectangle {
        RoundedRectangle(
            cornerRadius: layout.isExpandedPad ? 18 : ModeHeaderMetrics.controlCornerRadius,
            style: .continuous
        )
    }

    var body: some View {
        Button(action: action) {
            Text(glyph)
                // 🔊 and 🔇 are colour emoji and are unaffected, but any text
                // symbol put in here would take the button's tint — which is the
                // system accent blue unless it is said otherwise.
                .foregroundStyle(Theme.textPrimary)
                .font(
                    .system(
                        size: layout.isExpandedPad
                            ? ModeHeaderMetrics.padControlGlyphSize
                            : ModeHeaderMetrics.controlGlyphSize
                    )
                )
                .frame(
                    width: layout.isExpandedPad
                        ? ModeHeaderMetrics.padControlSide
                        : ModeHeaderMetrics.controlSide,
                    height: layout.isExpandedPad
                        ? ModeHeaderMetrics.padControlSide
                        : ModeHeaderMetrics.controlSide
                )
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
