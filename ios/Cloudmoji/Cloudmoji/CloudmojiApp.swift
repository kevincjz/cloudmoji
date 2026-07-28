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
    ///
    /// Assigned in `init` rather than given a default value, and that ordering is
    /// load-bearing: a default value is evaluated *before* the initialiser's body
    /// runs, so `SettingsStore` would have already read `UserDefaults` before
    /// ``resetPersistedSettingsIfRequested()`` below could clear it.
    @State private var model: AppModel

    /// Wipes this app's persisted settings when launched with
    /// `-cm_reset_persisted_settings YES`. Debug builds only — it is compiled out
    /// of Release entirely, so there is no path to it in a shipped binary.
    ///
    /// This exists because a UI test cannot otherwise be hermetic about a value
    /// the app only ever writes in one direction. `cm_seen_tutorial` goes false →
    /// true when the tour is dismissed and never back, so once any run has
    /// dismissed it, the simulator's container says "seen" forever. A later run
    /// asserting "the tour does not come back on the second launch" then passes
    /// whether or not the app still writes the flag at all — which is exactly
    /// what happened: deleting the write from `ContentView` left `TutorialUITests`
    /// green. `NSArgumentDomain` cannot fix it, because pinning the key on the
    /// second launch is precisely what that assertion must not do.
    ///
    /// Read through `UserDefaults` rather than `ProcessInfo.arguments` on purpose:
    /// `NSArgumentDomain` parses `-key value` pairs, so a bare flag would swallow
    /// whichever argument came after it.
    #if DEBUG
    private static func resetPersistedSettingsIfRequested() {
        guard UserDefaults.standard.bool(forKey: "cm_reset_persisted_settings"),
              let domain = Bundle.main.bundleIdentifier else { return }
        // Only the app domain. Anything the launch passed in arrives through
        // `NSArgumentDomain`, which this does not touch — so a test can reset and
        // pin in the same launch.
        UserDefaults.standard.removePersistentDomain(forName: domain)
    }
    #endif

    init() {
        #if DEBUG
        Self.resetPersistedSettingsIfRequested()
        #endif
        _model = State(initialValue: AppModel())

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
