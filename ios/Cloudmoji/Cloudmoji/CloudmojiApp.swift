//
//  CloudmojiApp.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI
import AVFoundation

@main
struct CloudmojiApp: App {
    /// Proof that launch registered the bundled fonts, so a test can assert it
    /// without registering them itself and passing regardless.
    ///
    /// `BundledFonts` shipped with its registration wired up and nothing calling
    /// it: `Font.custom` falls back to the system face in silence, so the
    /// wordmark rendered in SF Rounded and looked merely a little plain. Its own
    /// tests call `register()` before asserting, so they passed throughout.
    private(set) static var didRegisterFontsAtLaunch = false

    /// The one instance every screen reads. Owned here rather than created per
    /// view, because `SpeechController` holds the synthesiser and a second one
    /// would talk over the first.
    @State private var model = AppModel()

    init() {
        BundledFonts.register()
        Self.didRegisterFontsAtLaunch = BundledFonts.logoFontIsAvailable

        // `.playback` so Cloudmoji speaks even with the ringer switch off — what
        // a parent expects when handing the phone over. A deliberate override of
        // a system setting, recorded as such in the design spec. `.duckOthers`
        // so a podcast or a nursery-rhyme playlist dips rather than stops.
        try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView().environment(model)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            // The session is activated once in `init` and an interruption — a
            // phone call, Siri, a route change — deactivates it. Nothing brought
            // it back, so the app returned from a call silent, with no mute
            // control to make that legible and force-quit as the only recovery.
            // Audio is the whole product here, so re-activating on every
            // foreground is the cheap side of the trade.
            try? AVAudioSession.sharedInstance().setActive(true)

            // Voices are cached on first use, and iOS installs new ones while
            // the app is backgrounded — a parent going to Settings to fetch the
            // Japanese voice, coming back, and finding nothing changed was the
            // motivating case. `invalidateVoiceCache` shipped implemented,
            // tested, and called by nothing at all.
            model.invalidateVoiceCache()
        }
    }
}
