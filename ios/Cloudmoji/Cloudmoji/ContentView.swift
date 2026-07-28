//
//  ContentView.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI
// For `SpeechController.cancelAll` — this file is the one place a mode change is
// turned into "stop talking".
import CloudmojiCore

/// The app's one screen, and the one thing that knows which mode is showing.
///
/// `AdaptiveShell` paints the background and makes the single portrait/landscape
/// decision; everything below reads that decision out of the environment, which is
/// why the mode switch lives in a nested view rather than here.
struct ContentView: View {
    var body: some View {
        AdaptiveShell { RootContent() }
    }
}

private struct RootContent: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact

    @State private var mode: AppMode = .words

    @State private var isGateShowing = false
    /// Which question comes next. Advanced on every attempt, passed or not, so a
    /// parent who has just answered one is not asked the same one again.
    @State private var gateIndex = 0

    /// One presenter for both sheets rather than two `.sheet` modifiers.
    ///
    /// Two `.sheet` modifiers on the *same* view is a long-standing SwiftUI
    /// misfire — only the last one presents — and this view needs both the
    /// first-launch tour and the parent panel. An `item:` presenter with one
    /// case each is the shape that cannot hit it.
    private enum RootSheet: String, Identifiable {
        case tutorial, settings
        var id: String { rawValue }
    }

    @State private var sheet: RootSheet?

    var body: some View {
        VStack(spacing: 0) {
            // A `switch`, so the outgoing screen is torn down rather than kept
            // alive off-screen. Words mode's typing row and Count mode's round are
            // therefore **not** preserved across a switch — which is exactly what
            // `App.tsx` does, where the two modes are the two arms of a ternary and
            // React unmounts whichever one is not showing. Keeping both alive would
            // also leave two mascots animating and two `.task`s running for a
            // screen nobody is looking at.
            switch mode {
            case .words:
                WordsView(mode: mode, onSelectMode: select, onParent: openParentDoor)
            case .count:
                CountView(mode: mode, onSelectMode: select, onParent: openParentDoor)
            }

            // Sideways the tabs are in the rail instead: a landscape phone gives
            // about 390pt of height and a 64pt bar would take a sixth of it.
            if !isCompact {
                ModeTabBar(mode: mode, layout: .bar, onSelect: select)
            }
        }
        // An overlay rather than a sheet. Swapping one sheet for another on pass is
        // a documented SwiftUI misfire, and a numeric keyboard inside a detented
        // sheet fights the detent. It sits out here rather than inside either mode
        // so that it also covers the tab bar — a gate a child can tap around is not
        // a gate.
        .overlay {
            if isGateShowing {
                ParentalGate(
                    challenge: GateChallenge.at(gateIndex),
                    action: "Settings let you choose which languages and categories Cloudmoji shows, and how high Count mode goes.",
                    onPass: {
                        isGateShowing = false
                        gateIndex += 1
                        sheet = .settings
                    },
                    onCancel: {
                        isGateShowing = false
                        gateIndex += 1
                    }
                )
                .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: isGateShowing)
        .sheet(item: $sheet) { which in
            switch which {
            case .tutorial:
                TutorialView(onDone: { sheet = nil })
                // The **only** place the flag is written, and deliberately not
                // inside `onDone` as well. A sheet closes two ways — the Got it
                // button and a downward swipe — and only this one covers both;
                // a parent who flicks the tour away would otherwise get it back
                // on every launch forever.
                //
                // Writing it in both places was the first version, and it made
                // `TutorialUITests` unable to fail on this line: the test taps
                // Got it, so deleting this modifier changed nothing it could
                // see. One write site means the test that taps the button is
                // also the test that proves `.onDisappear` fires at all.
                .onDisappear { model.settings.seenTutorial = true }
            case .settings:
                NavigationStack {
                    SettingsView()
                }
                // Re-injected rather than inherited. A sheet is presented from a
                // detached hierarchy, and an observable put in with `.environment`
                // at the app level is not reliably visible inside one — the
                // failure is a crash on first appearance, not a compile error.
                .environment(model)
            }
        }
        // First launch only, and never behind the gate: a parent installing this
        // should not have to answer an arithmetic question to find out what they
        // just installed. Settings itself stays gated.
        .task {
            guard !model.settings.seenTutorial else { return }
            sheet = .tutorial
        }
    }

    private func openParentDoor() {
        // A child may be mid-word; a parent opening this is not a reason to keep
        // talking over the keypad.
        model.speech.cancelAll()
        isGateShowing = true
    }

    private func select(_ next: AppMode) {
        guard next != mode else { return }
        // Mode change is on the list of things that must cancel speech. The
        // outgoing screen's `.onDisappear` also cancels, but the ordering of that
        // against the incoming screen's first frame is not guaranteed — and the
        // failure it would produce is the old mode's word playing over the new
        // mode's, which is exactly the class of bug `SpeechController` exists for.
        //
        // The mascot needs no equivalent care: `mood` is `@State` on each screen,
        // so a switch taken mid-utterance discards the `.speaking` face along with
        // the rest of that screen and the incoming cloud starts at `.happy`.
        model.speech.cancelAll()
        mode = next
    }
}

#Preview {
    ContentView().environment(AppModel())
}
