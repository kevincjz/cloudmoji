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
                WordsView(mode: mode, onSelectMode: select)
            case .count:
                CountView(mode: mode, onSelectMode: select)
            }

            // Sideways the tabs are in the rail instead: a landscape phone gives
            // about 390pt of height and a 64pt bar would take a sixth of it.
            if !isCompact {
                ModeTabBar(mode: mode, layout: .bar, onSelect: select)
            }
        }
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
