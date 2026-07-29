import AVFoundation
import SwiftUI

/// Which mini-app is currently holding the audio engine.
///
/// One at a time, by construction. Two screens both starting an `AVAudioEngine`
/// is how a phone ends up with a pad tone playing under a dog bark from a screen
/// nobody is looking at.
enum AudioClient: String, Hashable, Sendable {
    case instrument
    case animalSounds
    case sleepy
}

/// What the director needs an engine to do. A protocol so the lifecycle — attach,
/// detach, background, foreground — can be tested without the real audio stack,
/// which needs hardware, takes tens of milliseconds to start, and leaks between
/// tests because `AVAudioSession` is a process-wide singleton.
@MainActor
protocol ToneEngineDriving: AnyObject {
    var isRunning: Bool { get }
    /// Starts, building the node graph on first use. Silent on failure: audio
    /// that will not start is a quiet app, never a crash.
    func start()
    func stop()
    /// Plays pad `index` from the pre-rendered set. Out-of-range does nothing.
    func playTone(_ index: Int)
    /// Plays a file already on disk or in the bundle.
    func playSound(_ url: URL)
    /// Starts and stops the quiet looping ambience used by Sleepy Cloud.
    func playSleepNoise()
    func stopSleepNoise()
}

/// The one thing in this app that touches `AVAudioSession`.
///
/// It exists because the session used to be three lines in `CloudmojiApp.init`
/// and one more in the scene-phase handler, which was fine while speech was the
/// only sound. It is not fine with a synthesiser and a sound library in the mix:
/// activation, deactivation, interruption recovery and engine lifetime are four
/// rules that have to agree with each other, and four rules in four files do not.
///
/// Speech is deliberately **not** routed through here. `SpeechController` owns
/// its own `AVSpeechSynthesizer`, which mixes with the engine at the session
/// level; giving it a second owner would mean two things deciding when the
/// session is active.
@MainActor
final class AudioDirector {

    private let engine: any ToneEngineDriving
    /// Injected so a test can watch the session being activated without
    /// activating the process-wide real one.
    private let setSessionActive: @MainActor (Bool) -> Void

    /// Which mini-app has the engine, or `nil` when nobody does — in which case
    /// the engine is stopped and costs nothing.
    private(set) var client: AudioClient?

    init(
        engine: (any ToneEngineDriving)? = nil,
        setSessionActive: @escaping @MainActor (Bool) -> Void = AudioDirector.setSystemSessionActive
    ) {
        self.engine = engine ?? ToneEngine()
        self.setSessionActive = setSessionActive
    }

    /// `.playback` so Cloudmoji speaks even with the ringer switch off — what a
    /// parent expects when handing the phone over. A deliberate override of a
    /// system setting, recorded as such in the design spec. `.duckOthers` so a
    /// podcast or a nursery-rhyme playlist dips rather than stops.
    ///
    /// These four lines used to live in `CloudmojiApp.init`. They moved here
    /// whole; nothing about them changed.
    static func setSystemSessionActive(_ active: Bool) {
        let session = AVAudioSession.sharedInstance()
        if active {
            try? session.setCategory(.playback, options: [.duckOthers])
        }
        try? session.setActive(active)
    }

    /// Called once at launch.
    func activateSession() {
        setSessionActive(true)
    }

    /// Foreground and background, in one place.
    ///
    /// The session is activated at launch and an interruption — a phone call,
    /// Siri, a route change — deactivates it. Nothing used to bring it back, so
    /// the app returned from a call silent with force-quit as the only recovery.
    /// Re-activating on every foreground is the cheap side of that trade, and the
    /// engine comes back with it, but **only if somebody is holding it**: a
    /// launcher in the foreground has no reason to be running an audio graph.
    func handleScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .active:
            setSessionActive(true)
            if client != nil { engine.start() }
        case .background:
            engine.stop()
        default:
            break
        }
    }

    /// A mini-app takes the engine. Idempotent — `.onAppear` and a foreground
    /// can both arrive for one entry.
    func attach(_ next: AudioClient) {
        client = next
        engine.start()
    }

    /// …and gives it back. Called from the mini-app's `.onDisappear` *and* from
    /// `goHome`, deliberately redundantly: a tone still ringing over the launcher
    /// is the kind of thing a parent notices and cannot explain.
    func detach() {
        guard client != nil else { return }
        client = nil
        engine.stop()
    }

    func playTone(_ pad: Int) {
        restartIfStalled()
        engine.playTone(pad)
    }

    func playSound(_ url: URL) {
        restartIfStalled()
        engine.playSound(url)
    }

    func playSleepNoise() {
        guard client == .sleepy else { return }
        restartIfStalled()
        engine.playSleepNoise()
    }

    func stopSleepNoise() {
        guard client == .sleepy else { return }
        engine.stopSleepNoise()
    }

    /// Recovery, checked at the point of use rather than driven by
    /// `AVAudioSession.interruptionNotification`.
    ///
    /// An interruption stops the engine, and the app is usually still in the
    /// foreground when the call ends — so no scene-phase change arrives and the
    /// pads go silent for the rest of the session. Watching the notification is
    /// the textbook fix; checking `isRunning` on the next tap is the same fix
    /// with none of the machinery, and it also covers the cases the notification
    /// misses (a media-services reset, a route change that killed the graph).
    /// The cost is a few milliseconds on the first tap after an interruption and
    /// nothing at all on every other tap.
    private func restartIfStalled() {
        guard client != nil, !engine.isRunning else { return }
        engine.start()
    }
}

/// The real thing: eight pre-rendered tones, one player node each.
///
/// One node per pad rather than one shared node is what gives chords away for
/// free — two fingers land on two views, two `scheduleBuffer` calls go to two
/// players, and they sum in the mixer. A single node would queue the second tone
/// behind the first.
///
/// `AVAudioSourceNode` was the other candidate and was rejected: its render block
/// runs on a real-time thread, and under Swift 6's main-actor-by-default it is a
/// priority-inversion trap that only shows up as an audio glitch on a loaded
/// device. `AVAudioUnitSampler` was rejected too — it wants a soundfont, which is
/// an asset to ship and license for no gain over eight sine-adjacent tones.
@MainActor
final class ToneEngine: ToneEngineDriving {
    private let engine = AVAudioEngine()
    private var players: [AVAudioPlayerNode] = []
    private var buffers: [AVAudioPCMBuffer] = []
    /// A player for bundled files, separate from the pads so a long animal
    /// sound is not cut off by a pad tone sharing its node.
    private let filePlayer = AVAudioPlayerNode()
    /// A dedicated looping player so the bedtime ambience never competes with
    /// a one-shot animal recording for scheduling.
    private let sleepPlayer = AVAudioPlayerNode()
    private var sleepBuffer: AVAudioPCMBuffer?
    private var isBuilt = false

    var isRunning: Bool { engine.isRunning }

    func start() {
        build()
        guard !engine.isRunning else { return }
        // Silent on failure, and that is the product decision: a phone that
        // cannot start an audio graph should show a child a working instrument
        // that happens to be quiet, not an error he cannot read.
        try? engine.start()
    }

    func stop() {
        guard engine.isRunning else { return }
        for player in players where player.isPlaying { player.stop() }
        if filePlayer.isPlaying { filePlayer.stop() }
        if sleepPlayer.isPlaying { sleepPlayer.stop() }
        engine.stop()
    }

    func playTone(_ index: Int) {
        guard engine.isRunning, players.indices.contains(index), buffers.indices.contains(index) else { return }
        let player = players[index]
        // Interrupt rather than queue. A child tapping the same pad twice wants
        // two strikes, not the second one waiting 1.2 seconds for the first to
        // finish — `.interrupts` is what makes a repeated pad feel like an
        // instrument instead of a metronome.
        player.scheduleBuffer(buffers[index], at: nil, options: [.interrupts])
        if !player.isPlaying { player.play() }
    }

    func playSound(_ url: URL) {
        guard engine.isRunning, let file = try? AVAudioFile(forReading: url) else { return }
        filePlayer.stop()
        filePlayer.scheduleFile(file, at: nil)
        filePlayer.play()
    }

    func playSleepNoise() {
        guard engine.isRunning, let sleepBuffer else { return }
        guard !sleepPlayer.isPlaying else { return }
        sleepPlayer.scheduleBuffer(sleepBuffer, at: nil, options: [.loops])
        sleepPlayer.play()
    }

    func stopSleepNoise() {
        if sleepPlayer.isPlaying { sleepPlayer.stop() }
    }

    /// A gentle, constant bedtime level. Soft enough to sit under a quiet room
    /// without droning — the noise is a bed, not the event. One number, tuned by
    /// ear: raise it if the ambience is too faint to mask the house, lower it if
    /// it competes with a child settling.
    static let sleepVolume: Float = 0.5

    /// Built once, on the first `start()`. Not in `init`, because a director is
    /// constructed at launch and most sessions never open a mini-app that makes
    /// a sound — attaching nine nodes to a graph nobody uses is work for nothing.
    private func build() {
        guard !isBuilt else { return }
        isBuilt = true

        let mixer = engine.mainMixerNode
        // Reading the mixer's format is also what forces the engine to realise
        // its output node, which it will not do lazily on the simulator.
        let format = AVAudioFormat(
            standardFormatWithSampleRate: mixer.outputFormat(forBus: 0).sampleRate,
            channels: 2
        ) ?? mixer.outputFormat(forBus: 0)

        for pitch in ToneBuffer.pitches {
            guard let buffer = ToneBuffer.make(frequency: pitch, format: format) else { continue }
            let player = AVAudioPlayerNode()
            engine.attach(player)
            engine.connect(player, to: mixer, format: format)
            players.append(player)
            buffers.append(buffer)
        }

        engine.attach(filePlayer)
        engine.connect(filePlayer, to: mixer, format: nil)

        engine.attach(sleepPlayer)
        engine.connect(sleepPlayer, to: mixer, format: format)
        sleepPlayer.volume = Self.sleepVolume
        sleepBuffer = SleepNoiseBuffer.make(format: format)
        engine.prepare()
    }
}
