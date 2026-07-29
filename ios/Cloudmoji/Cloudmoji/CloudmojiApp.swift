//
//  CloudmojiApp.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI
// For `EntitlementProviding.startObserving` — a no-op in the stub, and the hook
// StoreKit's `Transaction.updates` observer will land on.
import CloudmojiCore

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
        let model = AppModel()
        _model = State(initialValue: model)

        BundledFonts.register()
        // Warm the Taptic Engine now rather than on the child's first tap: an
        // unprepared generator has to spin the hardware up, and that delay is
        // enough to break the link between finger and buzz.
        Haptics.prepare()
        Self.didRegisterFontsAtLaunch = BundledFonts.allFacesAreAvailable

        // The four session lines that used to be here now live in
        // `AudioDirector`, which is the app's one owner of `AVAudioSession`.
        // They moved whole: nothing about the category or the options changed.
        model.audio.activateSession()
        model.entitlements.startObserving()
        // Bring up the wrist link and tell a paired watch the current language
        // and mute. No-op if there is no watch.
        model.radio.activate()
    }

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView().environment(model)
        }
        .onChange(of: scenePhase) { _, phase in
            // Session re-activation on foreground, and stopping the tone engine
            // on background, are both `AudioDirector`'s to decide — see the
            // rationale on `handleScenePhase`.
            model.audio.handleScenePhase(phase)

            guard phase == .active else { return }

            // Voices are cached on first use, and iOS installs new ones while
            // the app is backgrounded — a parent going to Settings to fetch the
            // Japanese voice, coming back, and finding nothing changed was the
            // motivating case. `invalidateVoiceCache` shipped implemented,
            // tested, and called by nothing at all.
            model.invalidateVoiceCache()

            // A parent may have changed language or mute on another device, or
            // paired a watch, while we were away — re-push the context so the
            // wrist is never stale.
            model.radio.pushContext()
        }
    }
}
