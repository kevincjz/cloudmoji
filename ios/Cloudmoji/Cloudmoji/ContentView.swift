//
//  ContentView.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI
import UIKit
// For `SpeechController.cancelAll` — this file is the one place a mode change is
// turned into "stop talking".
import CloudmojiCore

/// The app's one screen, and the one thing that knows which mini-app is showing.
///
/// `AdaptiveShell` paints the background and makes the single portrait/landscape
/// decision; everything below reads that decision out of the environment, which is
/// why the switch lives in a nested view rather than here.
struct ContentView: View {
    var body: some View {
        AdaptiveShell { RootContent() }
    }
}

private struct RootContent: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.openURL) private var openURL

    /// Which mini-app is open. `nil` is the launcher, which is home.
    @State private var active: MiniApp?

    @State private var isGateShowing = false
    /// Which question comes next. Advanced on every attempt, passed or not, so a
    /// parent who has just answered one is not asked the same one again.
    @State private var gateIndex = 0

    private enum ParentRequest {
        case settings
        case cameraSettings

        var explanation: String {
            switch self {
            case .settings:
                "Settings let you choose Cloudmoji's sound, languages, categories and learning range."
            case .cameraSettings:
                "Camera access was turned off. A grown-up can open iPhone Settings and allow it again."
            }
        }
    }

    @State private var parentRequest: ParentRequest = .settings

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

    /// Preselects a mini-app from `-cm_open <raw value>`, so a UI suite can land
    /// on the screen it means to measure instead of tapping its way there.
    ///
    /// Debug only — compiled out of Release entirely, so there is no path to it
    /// in a shipped binary. Read through `UserDefaults` rather than
    /// `ProcessInfo.arguments` for the same reason
    /// `resetPersistedSettingsIfRequested` is: `NSArgumentDomain` parses
    /// `-key value` pairs, so a bare flag would swallow the argument after it.
    init() {
        #if DEBUG
        if let raw = UserDefaults.standard.string(forKey: "cm_open"),
           let requested = MiniApp(rawValue: raw) {
            _active = State(initialValue: requested)
        }
        #endif
    }

    var body: some View {
        // A `switch`, so the outgoing screen is torn down rather than kept alive
        // off-screen. Words mode's typing row and Count mode's round are
        // therefore **not** preserved across a switch — which is exactly what
        // `App.tsx` does, where the modes are the arms of a ternary and React
        // unmounts whichever one is not showing. Keeping them alive would also
        // leave several mascots animating and several `.task`s running for
        // screens nobody is looking at.
        Group {
            if let active {
                hosted(active)
                    .transition(
                        reduceMotion
                            ? .opacity
                            : .scale(scale: 0.96).combined(with: .opacity)
                    )
            } else {
                LauncherView(
                    apps: model.visibleMiniApps,
                    onOpen: open,
                    onParent: openParentDoor
                )
                .transition(.opacity)
            }
        }
        .animation(
            reduceMotion
                ? .easeOut(duration: 0.16)
                : .spring(response: 0.34, dampingFraction: 0.86),
            value: active
        )
        // An overlay rather than a sheet. Swapping one sheet for another on pass is
        // a documented SwiftUI misfire, and a numeric keyboard inside a detented
        // sheet fights the detent. It sits out here rather than inside the switch
        // so that it also covers the launcher tiles and the home button — a gate a
        // child can tap around is not a gate.
        .overlay {
            if isGateShowing {
                ParentalGate(
                    challenge: GateChallenge.at(gateIndex),
                    action: parentRequest.explanation,
                    onPass: {
                        isGateShowing = false
                        gateIndex += 1
                        completeParentRequest()
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

    // MARK: - Mini-apps

    /// One mini-app, plus the way home.
    ///
    /// The home button is an overlay rather than a row in a stack, so a screen
    /// that fills its space (the emoji grid, a breathing cloud, a camera
    /// preview) does not have to know it is there. `safeAreaPadding` is what
    /// keeps the content out from under it — padding the overlay instead would
    /// let the grid's last row sit beneath the cloud, where every tap aimed at
    /// it would go home.
    private func hosted(_ app: MiniApp) -> some View {
        HostedMiniApp(app: app, onHome: goHome) { screen(app) }
    }

    @ViewBuilder private func screen(_ app: MiniApp) -> some View {
        switch app {
        case .words: WordsView(onParent: openParentDoor)
        case .count: CountView(onParent: openParentDoor)
        case .flashCards: FlashCardsView()
        case .instrument: InstrumentPadView()
        case .animalSounds: AnimalSoundsView()
        case .photos: PhotosView(onCameraPermissionHelp: openCameraDoor)
        case .sleepy: SleepyCloudView()
        }
    }

    private func openParentDoor() {
        // A child may be mid-word; a parent opening this is not a reason to keep
        // talking over the keypad.
        model.speech.cancelAll()
        parentRequest = .settings
        isGateShowing = true
    }

    private func openCameraDoor() {
        model.speech.cancelAll()
        parentRequest = .cameraSettings
        isGateShowing = true
    }

    private func completeParentRequest() {
        switch parentRequest {
        case .settings:
            sheet = .settings
        case .cameraSettings:
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            openURL(url)
        }
    }

    /// Opening a mini-app is on the list of things that must cancel speech. The
    /// outgoing screen's `.onDisappear` also cancels, but the ordering of that
    /// against the incoming screen's first frame is not guaranteed — and the
    /// failure it would produce is the launcher's last word playing over the new
    /// screen's, which is exactly the class of bug `SpeechController` exists for.
    private func open(_ app: MiniApp) {
        model.speech.cancelAll()
        active = app
    }

    /// Home. Cancels everything the mini-app might still be saying, and hands
    /// the audio engine back — a pad tone ringing on the launcher would outlive
    /// the screen that asked for it.
    ///
    /// The mascot needs no equivalent care: `mood` is `@State` on each screen,
    /// so leaving mid-utterance discards the `.speaking` face along with the rest
    /// of that screen and the launcher's cloud starts at `.happy`.
    private func goHome() {
        model.speech.cancelAll()
        model.audio.detach()
        active = nil
    }
}

/// One mini-app, plus the way home.
///
/// Its own view rather than a method on `RootContent`, and that is not tidiness:
/// `RootContent` is the value `AdaptiveShell` is *handed*, so reading
/// `cloudmojiIsCompact` there put an environment dependency on the view that
/// supplies the shell's content. The result was a launcher that never re-laid
/// out on rotation — every landscape assertion in `LauncherUITests` and
/// `WordsModeUITests` went red at once and the app stayed in its portrait
/// arrangement on a sideways phone. Reading it *inside* the hosted subtree,
/// where the value is actually published, is both correct and unremarkable.
private struct HostedMiniApp<Content: View>: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiLayout) private var layout

    let app: MiniApp
    let onHome: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        ZStack(alignment: .bottom) {
            if app.hasThemedBackdrop {
                MiniAppBackdrop(app: app)
            }

            // **A real band, not a safe-area inset.**
            //
            // This was `.safeAreaPadding(.bottom,)`, which only insets where a
            // scroll view's content *ends* — everything in the middle still
            // travelled underneath the cloud. On a phone that meant the way home
            // sat on top of a moving field of emoji, and the one control a child
            // needs to find was the hardest thing on the screen to pick out.
            //
            // A `VStack` with a reserved strip is the blunt fix and the right
            // one: the mini-app is *shorter*, the strip is empty background, and
            // nothing can ever scroll through it. The cost is a band of
            // wallpaper, which is exactly what makes the cloud visible.
            VStack(spacing: 0) {
                content
                    // Photos with an empty gallery is a `ScrollView` around one
                    // short line of text: without this the stack shrank to that
                    // line's width and the cloud came out off-centre.
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                Color.clear
                    .frame(
                        height: HomeButtonMetrics.reservedHeight(
                            expandedPad: layout.isExpandedPad
                        )
                    )
            }

            CloudHomeButton(accent: app.homeAccent, action: onHome)
                .padding(
                    .bottom,
                    HomeButtonMetrics.inset(expandedPad: layout.isExpandedPad)
                )
        }
        .overlay(alignment: .topTrailing) {
            if app.showsSoundRecovery && model.settings.muted {
                SoundRecoveryButton {
                    model.settings.muted = false
                }
                .padding(12)
                .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.18), value: model.settings.muted)
    }
}

/// A child-readable recovery from global mute.
///
/// Audio-driven mini-apps must never look broken because a parent muted the app
/// earlier. Words and Count already expose their own header control; this large
/// speaker appears only where there is otherwise no route back to sound.
private struct SoundRecoveryButton: View {
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            ZStack {
                Circle()
                    .fill(Theme.bgPrimary.opacity(0.94))
                Circle()
                    .fill(Theme.coral.opacity(0.16))
                    .padding(5)
                Image(systemName: "speaker.slash.fill")
                    .font(.system(size: 24, weight: .black))
                    .foregroundStyle(Theme.coral)
            }
            .frame(width: 64, height: 64)
            .overlay(Circle().stroke(Theme.coral.opacity(0.52), lineWidth: 2))
            .shadow(color: Theme.coral.opacity(0.20), radius: 12, y: 5)
            .contentShape(Circle())
        }
        .buttonStyle(PressScale(scale: 0.88))
        .accessibilityLabel("Turn sound on")
        .accessibilityIdentifier("sound-recovery-btn")
    }
}

#Preview {
    ContentView().environment(AppModel())
}
