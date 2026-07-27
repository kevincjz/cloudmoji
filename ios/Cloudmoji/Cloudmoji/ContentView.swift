//
//  ContentView.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI

/// The app's one screen.
///
/// Stage 2a ships Words mode only, so there is no tab bar and no routing to do:
/// `AdaptiveShell` paints the background and makes the single
/// portrait/landscape decision, and ``WordsView`` is what sits in it. Count mode
/// joins it here in Stage 2b.
///
/// This replaced the Task 1 scaffold that drew the four mascot moods and a
/// content summary — that proof now lives in `AppModelTests` and
/// `CloudMascotTests`, which is where it belongs.
struct ContentView: View {
    var body: some View {
        AdaptiveShell { WordsView() }
    }
}

#Preview {
    ContentView()
        .environment(AppModel())
}
