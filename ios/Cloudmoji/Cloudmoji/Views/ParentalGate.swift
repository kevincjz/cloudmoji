import SwiftUI

/// One arithmetic question.
///
/// Ported from `src/components/ParentalGate.tsx`, including the eight pairs and the
/// rotation. Rotating rather than randomising is deliberate: the gate does not need
/// unpredictability — a two-year-old cannot do arithmetic at all — and a fixed
/// sequence is testable.
struct GateChallenge: Equatable {
    let a: Int
    let b: Int

    var answer: Int { a * b }

    static let all: [GateChallenge] = [
        GateChallenge(a: 7, b: 8),
        GateChallenge(a: 9, b: 6),
        GateChallenge(a: 12, b: 7),
        GateChallenge(a: 6, b: 11),
        GateChallenge(a: 8, b: 9),
        GateChallenge(a: 11, b: 8),
        GateChallenge(a: 7, b: 12),
        GateChallenge(a: 9, b: 7),
    ]

    /// Wraps, so a parent opening Settings a ninth time gets the first question
    /// again rather than an index out of range.
    static func at(_ index: Int) -> GateChallenge {
        all[abs(index) % all.count]
    }

    /// The whole gate, in one line.
    ///
    /// Trimming is not leniency: a numeric keypad and an autocorrect pass both
    /// produce trailing whitespace, and a parent who typed the right answer has
    /// answered the question. Anything that is not a whole number is not an answer
    /// — which is already stricter than the web, where `Number("")` is 0.
    func accepts(_ entry: String) -> Bool {
        guard let value = Int(entry.trimmingCharacters(in: .whitespaces)) else { return false }
        return value == answer
    }
}

/// A real gate, not a gesture.
///
/// Deliberately boring: no timer, no penalty, no lock-out. A parent who misreads
/// just tries again. Ported from `src/components/ParentalGate.tsx`.
///
/// Drawn as an overlay rather than a sheet, for two reasons that both bit: a sheet
/// swapped for another sheet on pass is a documented SwiftUI misfire, and a numeric
/// keyboard inside a detented sheet fights the detent.
struct ParentalGate: View {
    let challenge: GateChallenge
    /// What the parent is about to do, so they know why they were asked.
    let action: String
    let onPass: () -> Void
    let onCancel: () -> Void

    @State private var entry = ""
    @State private var wasWrong = false
    @FocusState private var isFocused: Bool

    private static let cardWidth: CGFloat = 340
    /// Parent-only chrome, so the 44pt HIG minimum. The web draws 52 and 48; both
    /// clear it and both are kept.
    private static let fieldHeight: CGFloat = 52
    private static let buttonHeight: CGFloat = 48

    var body: some View {
        ZStack {
            Color.black.opacity(0.82)
                .ignoresSafeArea()
                // Tapping outside is Cancel. A toddler who reached this screen
                // needs a way out that is not the right answer.
                .onTapGesture(perform: onCancel)

            card
                .frame(maxWidth: Self.cardWidth)
                .padding(20)
        }
        // Without this the identifier below propagates down and overwrites
        // `gate-input`, `gate-submit`, `gate-cancel` and `gate-question` — the
        // same defect that made all three of the typing row's controls
        // unreachable for the whole of stage 2a. It is invisible to every unit
        // test, because SwiftUI builds no accessibility tree outside XCUITest.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("parental-gate")
        .task { isFocused = true }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Grown-ups only")
                .font(Theme.body(16, .black))
                .foregroundStyle(Theme.textPrimary)
                .padding(.bottom, 6)

            Text(action)
                .font(Theme.body(13, .bold))
                .foregroundStyle(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 16)

            Text("What is \(challenge.a) × \(challenge.b)?")
                .font(Theme.body(22, .black))
                .foregroundStyle(Theme.textPrimary)
                .padding(.bottom, 10)
                .accessibilityIdentifier("gate-question")

            TextField("", text: $entry)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .font(Theme.body(20, .black))
                // A `TextField`'s text takes the accent colour on this background
                // unless it is told otherwise, which reads as system blue.
                .foregroundStyle(Theme.textPrimary)
                .focused($isFocused)
                .frame(height: Self.fieldHeight)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(wasWrong ? Theme.coral.opacity(0.6) : Theme.surfaceBorderStrong,
                                lineWidth: 2)
                )
                .onChange(of: entry) { _, new in
                    // A hardware or paste path can still deliver letters.
                    let digits = new.filter(\.isNumber)
                    if digits != new { entry = digits }
                    // Only a **non-empty** edit clears the error, and that is
                    // load-bearing rather than a nicety. React fires `onChange`
                    // for user input only; SwiftUI fires it for any assignment,
                    // including `submit`'s own `entry = ""` — so clearing
                    // unconditionally here sets `wasWrong` back to false in the
                    // same update that set it to true, and the "Not quite" line
                    // never appears at all. Leaving it up while the field is
                    // empty is also the better behaviour: the message stands
                    // until the parent starts a new answer.
                    if !new.isEmpty { wasWrong = false }
                }
                .accessibilityIdentifier("gate-input")
                .padding(.bottom, wasWrong ? 6 : 14)

            if wasWrong {
                Text("Not quite — have another go.")
                    .font(Theme.body(12, .heavy))
                    .foregroundStyle(Theme.coral)
                    .padding(.bottom, 10)
                    .accessibilityIdentifier("gate-error")
            }

            HStack(spacing: 8) {
                gateButton("Cancel", identifier: "gate-cancel",
                           tint: Theme.textTertiary, filled: false, action: onCancel)
                gateButton("Continue", identifier: "gate-submit",
                           tint: Theme.teal, filled: true, action: submit)
            }
        }
        .padding(24)
        .background(
            LinearGradient(colors: [Theme.bgMid, Theme.bgEdge],
                           startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Theme.surfaceBorderStrong, lineWidth: 1.5)
        )
        // The web's `onClick={(e) => e.stopPropagation()}`. Without it a tap on
        // the card's own padding — the 24pt margin, or the gap between the
        // question and the field — falls through to the dimmer behind it and
        // cancels the gate a parent is halfway through answering. A child view
        // with its own gesture (the buttons, the field) still wins.
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onTapGesture { }
    }

    private func gateButton(
        _ title: String, identifier: String, tint: Color, filled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(Theme.body(14, .black))
                // Text, so it takes the accent tint unless this says otherwise.
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity)
                .frame(height: Self.buttonHeight)
                .background(filled ? tint.opacity(0.2) : Color.clear,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(filled ? tint.opacity(0.4) : Theme.surfaceBorderStrong, lineWidth: 2)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: 0.88))
        .accessibilityIdentifier(identifier)
    }

    private func submit() {
        if challenge.accepts(entry) {
            onPass()
        } else {
            wasWrong = true
            entry = ""
        }
    }
}

#Preview("Gate") {
    ZStack {
        Theme.background.ignoresSafeArea()
        ParentalGate(
            challenge: GateChallenge.at(0),
            action: "Settings let you choose which languages and categories Cloudmoji shows.",
            onPass: {}, onCancel: {}
        )
    }
}
