import AVFoundation
import Foundation
import SwiftUI
import Testing
@testable import Cloudmoji

@Suite("ToneBuffer")
@MainActor
struct ToneBufferTests {

    private let sampleRate: Double = 44_100

    /// Clipping is the one way this can be loudly wrong, and eight pads can
    /// sound at once — so the headroom is the point, not just the ceiling.
    ///
    /// Mutation: set `ToneBuffer.peak` to 1.0. The headroom assertion fails, and
    /// on a device two simultaneous pads distort.
    @Test("the waveform peaks inside the headroom and never clips")
    func peakStaysInsideTheHeadroom() {
        let samples = ToneBuffer.samples(frequency: 440, sampleRate: sampleRate)
        #expect(!samples.isEmpty)

        let peak = samples.map(abs).max() ?? 0
        #expect(peak > 0, "the buffer is silent")
        #expect(peak <= 1.0, "the waveform clips at \(peak)")
        #expect(peak <= ToneBuffer.peak + 0.001,
                "peak \(peak) exceeds the \(ToneBuffer.peak) headroom — two pads at once would clip")
    }

    /// A DC offset is inaudible on its own and thumps when eight buffers sum.
    /// A symmetric triangle has a mean of zero; an asymmetric one does not.
    ///
    /// Mutation: change the waveform to `1 - 2 * phase` (a sawtooth from +1 to
    /// −1 with no symmetry about the cycle). The mean drifts and this fails.
    @Test("the waveform carries no DC offset")
    func meanIsZero() {
        let samples = ToneBuffer.samples(frequency: 440, sampleRate: sampleRate)
        let mean = samples.reduce(Float(0), +) / Float(samples.count)
        #expect(abs(mean) < 0.001, "mean sample value is \(mean)")
    }

    /// A note decays. Without it a pad is a drone, and eight of them mashed
    /// together never stop.
    ///
    /// Mutation: return 1 from `envelope(at:)` after the attack. Every step of
    /// the ladder below is equal and this fails.
    @Test("the envelope attacks quickly and then decays without stopping")
    func envelopeDecaysMonotonically() {
        #expect(ToneBuffer.envelope(at: 0) == 0, "the note starts from a click")
        #expect(ToneBuffer.envelope(at: ToneBuffer.attack) > 0.99, "the attack never reaches full")

        var previous = ToneBuffer.envelope(at: ToneBuffer.attack)
        for step in stride(from: ToneBuffer.attack + 0.05, through: ToneBuffer.duration, by: 0.05) {
            let value = ToneBuffer.envelope(at: step)
            #expect(value < previous, "the envelope did not fall between \(step - 0.05) and \(step)")
            previous = value
        }
        #expect(previous < 0.05, "the note is still at \(previous) of full when it ends")
    }

    /// The pitch is actually the pitch. A triangle crosses zero twice a cycle,
    /// so A4 over 1.2 seconds gives about 1056 crossings — and a buffer built at
    /// the wrong rate would still look and decay exactly right.
    ///
    /// Mutation: use `frequency / 2` in `cyclesPerSample`. The count halves and
    /// this fails.
    @Test("A4 really is 440Hz")
    func frequencyIsWhatItClaims() {
        // Measured over the first tenth of a second, where the envelope has not
        // yet pushed the tail into the noise of float comparison.
        let window = Int(sampleRate * 0.1)
        let samples = Array(ToneBuffer.samples(frequency: 440, sampleRate: sampleRate).prefix(window))

        var crossings = 0
        for i in 1..<samples.count where (samples[i - 1] < 0) != (samples[i] < 0) {
            crossings += 1
        }
        // 440Hz × 0.1s × 2 crossings per cycle = 88.
        #expect(abs(crossings - 88) <= 2, "saw \(crossings) zero crossings, expected about 88")
    }

    /// Every pad has a pitch, and they climb. A pentatonic run with a repeat or
    /// an inversion in it is a keyboard where two keys do the same thing.
    @Test("the eight pitches are a rising pentatonic run")
    func pitchesRise() {
        #expect(ToneBuffer.pitches.count == 8)
        for (a, b) in zip(ToneBuffer.pitches, ToneBuffer.pitches.dropFirst()) {
            #expect(b > a, "\(b) does not come after \(a)")
        }
        #expect(abs(ToneBuffer.pitches[4] - 440) < 0.01, "the fifth pad is not A4")
    }

    /// Nonsense in, silence out — never a NaN or a trap in front of a child.
    @Test("a zero or negative frequency produces nothing rather than crashing")
    func degenerateInputIsSafe() {
        #expect(ToneBuffer.samples(frequency: 0, sampleRate: sampleRate).isEmpty)
        #expect(ToneBuffer.samples(frequency: -1, sampleRate: sampleRate).isEmpty)
        #expect(ToneBuffer.samples(frequency: 440, sampleRate: 0).isEmpty)
        #expect(ToneBuffer.samples(frequency: 440, sampleRate: sampleRate, duration: 0).isEmpty)
    }
}

@Suite("SleepNoiseBuffer")
@MainActor
struct SleepNoiseBufferTests {

    @Test("the bedtime ambience is quiet, centred and fades at the loop seam")
    func ambienceIsSafeToLoop() {
        let samples = SleepNoiseBuffer.samples(sampleRate: 4_000, duration: 2)
        #expect(!samples.isEmpty)

        let peak = samples.map(abs).max() ?? 0
        #expect(peak > 0.005, "the sleep buffer is silent")
        #expect(peak <= SleepNoiseBuffer.peak + 0.001, "the sleep buffer clips its own headroom")

        let mean = samples.reduce(Float(0), +) / Float(samples.count)
        #expect(abs(mean) < 0.01, "the sleep buffer carries a DC offset of \(mean)")

        #expect(abs(samples.first ?? 1) < 0.001, "the loop starts with a click")
        #expect(abs(samples.last ?? 1) < 0.001, "the loop ends with a click")
    }

    @Test("invalid sleep-buffer inputs degrade to silence")
    func degenerateInputIsSafe() {
        #expect(SleepNoiseBuffer.samples(sampleRate: 0).isEmpty)
        #expect(SleepNoiseBuffer.samples(duration: 0).isEmpty)
    }
}

/// A stand-in for the real `AVAudioEngine`, which needs hardware, takes tens of
/// milliseconds to start, and leaks between tests through the process-wide
/// `AVAudioSession`.
@MainActor
private final class FakeEngine: ToneEngineDriving {
    var isRunning = false
    var starts = 0
    var stops = 0
    var tones: [Int] = []
    var sounds: [URL] = []
    var sleepStarts = 0
    var sleepStops = 0

    func start() { starts += 1; isRunning = true }
    func stop() { stops += 1; isRunning = false }
    func playTone(_ index: Int) { tones.append(index) }
    func playSound(_ url: URL) { sounds.append(url) }
    func playSleepNoise() { sleepStarts += 1 }
    func stopSleepNoise() { sleepStops += 1 }
}

@Suite("AudioDirector")
@MainActor
struct AudioDirectorTests {

    private func make() -> (AudioDirector, FakeEngine, Box) {
        let engine = FakeEngine()
        let box = Box()
        let director = AudioDirector(engine: engine) { box.sessionWrites.append($0) }
        return (director, engine, box)
    }

    /// A reference type, so the closure's captures survive into the assertions.
    private final class Box {
        var sessionWrites: [Bool] = []
    }

    /// Attach starts, detach stops, and detaching twice does not stop twice —
    /// `goHome` and `.onDisappear` both call it, deliberately.
    ///
    /// Mutation: delete the `guard client != nil` from `detach()`. The second
    /// detach stops a stopped engine and this fails.
    @Test("attach starts the engine and detach gives it back, once")
    func attachDetachIsBalanced() {
        let (director, engine, _) = make()
        #expect(director.client == nil)

        director.attach(.instrument)
        #expect(director.client == .instrument)
        #expect(engine.starts == 1)

        director.detach()
        director.detach()
        #expect(director.client == nil)
        #expect(engine.stops == 1, "the engine was stopped \(engine.stops) times")
    }

    /// Backgrounding stops the engine; foregrounding brings the session back and
    /// the engine **only if somebody is holding it**. A launcher in the
    /// foreground has no reason to be running an audio graph.
    ///
    /// Mutation: drop the `if client != nil` from the `.active` branch. The
    /// nobody-attached case starts the engine and this fails.
    @Test("scene phases stop the engine and restart it only for a live client")
    func scenePhaseDrivesTheEngine() {
        let (director, engine, box) = make()

        // Nobody attached: the session comes back, the engine does not.
        director.handleScenePhase(.active)
        #expect(box.sessionWrites == [true])
        #expect(engine.starts == 0)

        director.attach(.animalSounds)
        #expect(engine.starts == 1)

        director.handleScenePhase(.background)
        #expect(engine.isRunning == false)
        #expect(engine.stops == 1)

        director.handleScenePhase(.active)
        #expect(engine.starts == 2, "coming back to the foreground did not restart the engine")
        #expect(box.sessionWrites == [true, true])
    }

    /// The interruption recovery, checked at the point of use rather than driven
    /// by a notification: a call ends, the app was never backgrounded, and the
    /// next tap has to work.
    ///
    /// Mutation: delete `restartIfStalled()` from `playTone`. The engine stays
    /// stopped and this fails — which on a device is silent pads for the rest of
    /// the session.
    @Test("a tap after an interruption restarts a stalled engine")
    func playRestartsAStalledEngine() {
        let (director, engine, _) = make()
        director.attach(.instrument)

        // What an interruption looks like from here: the engine stopped and
        // nothing told us.
        engine.isRunning = false

        director.playTone(3)
        #expect(engine.starts == 2, "the stalled engine was not restarted")
        #expect(engine.tones == [3])

        // A running engine is not restarted on every tap.
        director.playTone(4)
        #expect(engine.starts == 2)
        #expect(engine.tones == [3, 4])
    }

    /// Nothing plays when nobody is attached — a stray `playTone` from a screen
    /// that has already gone must not spin the engine back up.
    @Test("a detached director does not restart the engine to play")
    func detachedDirectorDoesNotRestart() {
        let (director, engine, _) = make()
        director.playTone(0)
        #expect(engine.starts == 0)
    }

    @Test("Sleepy Cloud starts and stops its dedicated ambience")
    func sleepyAmbienceIsDirected() {
        let (director, engine, _) = make()
        director.attach(.sleepy)

        director.playSleepNoise()
        #expect(director.client == .sleepy)
        #expect(engine.sleepStarts == 1)

        director.stopSleepNoise()
        #expect(engine.sleepStops == 1)
    }

    /// Sideways is the transpose of upright: the same eight pads fill whichever
    /// axis there is more of.
    @Test("the pad grid is 2×4 upright and 4×2 sideways")
    func padGridTransposes() {
        #expect(InstrumentPadView.columns(compact: false) == 2)
        #expect(InstrumentPadView.columns(compact: true) == 4)
    }

    /// **The floor is not a suggestion.** On the shortest screen the arithmetic
    /// wants about 68pt, and a child-facing control under 64 breaks
    /// `CLAUDE.md` rule 1 — so the pads overflow their box by a couple of points
    /// instead, which nobody can see.
    ///
    /// Mutation: drop the `max(InstrumentPadMetrics.minimumSide, …)`. The
    /// cramped case returns about 46pt and this fails.
    @Test("pads never shrink below the preferred child size")
    func padsNeverShrinkPastTheFloor() {
        let roomy = InstrumentPadView.side(
            available: CGSize(width: 375, height: 700), columns: 2, rows: 4, spacing: 8
        )
        #expect(roomy >= InstrumentPadMetrics.minimumSide)
        #expect(roomy <= 375)

        let cramped = InstrumentPadView.side(
            available: CGSize(width: 320, height: 240), columns: 2, rows: 4, spacing: 8
        )
        #expect(cramped >= InstrumentPadMetrics.minimumSide,
                "a cramped screen gave \(cramped)pt pads, under the \(InstrumentPadMetrics.minimumSide)pt floor")

        // Degenerate input must not divide by zero.
        #expect(InstrumentPadView.side(
            available: CGSize(width: 375, height: 700), columns: 0, rows: 0, spacing: 8
        ) == InstrumentPadMetrics.minimumSide)
    }

    /// Eight pads, eight colours, and the lookup wraps rather than trapping.
    @Test("every pad has a tint")
    func everyPadHasATint() {
        #expect(InstrumentPadMetrics.tints.count == ToneBuffer.pitches.count)
        for index in 0..<ToneBuffer.pitches.count {
            _ = InstrumentPadMetrics.tint(index)
        }
        // Past the end, which cannot happen from the view but must not trap.
        _ = InstrumentPadMetrics.tint(99)
    }
}
