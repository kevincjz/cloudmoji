import AVFoundation

/// One note, rendered to samples.
///
/// Pure arithmetic, deliberately kept away from `AVAudioEngine`: what a tone
/// sounds like is a judgement for the ear, but whether it clips, whether it
/// decays, whether it carries a DC offset and whether it is actually the pitch it
/// claims to be are all facts that can be checked without a speaker — and all
/// four are silently wrong in a way no screenshot shows.
///
/// A triangle wave rather than a sine: a sine at these frequencies reads as a
/// test tone, and a square is harsh at a toddler's listening distance. Triangle
/// is the compromise a toy xylophone actually makes.
enum ToneBuffer {

    /// Matches the engine's own output format, so no resampling happens between
    /// here and the speaker.
    static let sampleRate: Double = 44_100

    /// Long enough for the tail to be audible under the next tap, short enough
    /// that eight of them mashed at once do not turn into a drone.
    static let duration: Double = 1.2

    /// Headroom. Eight pads can sound together — separate player nodes mean a
    /// toddler with two hands gets chords for free — and a peak of 1.0 each
    /// would clip the moment two of them overlapped.
    static let peak: Float = 0.55

    /// A fast attack, but not instant: a step from silence to full amplitude is
    /// a click, and the click is the loudest thing in the sound.
    static let attack: Double = 0.006

    /// The exponential decay constant. Chosen so the tone is at about 1% of peak
    /// by the end of ``duration`` — audibly finished rather than cut off.
    static let decay: Double = 3.8

    /// The eight pads, a C-major pentatonic run.
    ///
    /// Pentatonic because there is no wrong note in it: `CLAUDE.md` rule 4 says
    /// there are no failure states, and a chromatic or full major layout lets a
    /// child mash two adjacent pads and hear a semitone clash he will read as a
    /// mistake. Any two of these eight sound intentional together.
    static let pitches: [Double] = [
        261.63, // C4
        293.66, // D4
        329.63, // E4
        392.00, // G4
        440.00, // A4
        523.25, // C5
        587.33, // D5
        659.26, // E5
    ]

    /// The waveform, as mono float samples in −1...1.
    ///
    /// Separate from ``make(frequency:format:)`` so the arithmetic can be tested
    /// without constructing an `AVAudioPCMBuffer`, which needs a format, which
    /// needs a running audio stack.
    static func samples(
        frequency: Double,
        sampleRate: Double = sampleRate,
        duration: Double = duration
    ) -> [Float] {
        // A frequency of zero or less is not a note. It cannot arrive from
        // `pitches`, but it must not produce NaNs if it ever did.
        guard frequency > 0, sampleRate > 0, duration > 0 else { return [] }

        let count = Int(sampleRate * duration)
        var out = [Float](repeating: 0, count: count)
        let cyclesPerSample = frequency / sampleRate

        for i in 0..<count {
            let t = Double(i) / sampleRate
            // Phase within one cycle, 0...1.
            let phase = (Double(i) * cyclesPerSample).truncatingRemainder(dividingBy: 1)
            // A symmetric triangle: −1 at the start of a cycle, +1 halfway.
            // Symmetric is what makes the mean zero, which is what keeps a DC
            // offset out of the mix — eight offset buffers played together would
            // otherwise sum into a thump on every chord.
            let triangle = 1 - 4 * abs(phase - 0.5)
            out[i] = Float(triangle) * envelope(at: t) * peak
        }
        return out
    }

    /// Attack then exponential decay, 0...1. Strictly decreasing after the
    /// attack, which is the property that makes this a note rather than a beep.
    static func envelope(at t: Double, duration: Double = duration) -> Float {
        guard t >= 0 else { return 0 }
        if t < attack { return Float(t / attack) }
        return Float(exp(-decay * (t - attack)))
    }

    /// The same waveform, packed for the engine.
    ///
    /// Returns `nil` rather than trapping on a format the allocator refuses:
    /// silence is a bad outcome, and a crash in front of a child is a worse one.
    static func make(frequency: Double, format: AVAudioFormat) -> AVAudioPCMBuffer? {
        let values = samples(frequency: frequency, sampleRate: format.sampleRate)
        guard !values.isEmpty,
              let buffer = AVAudioPCMBuffer(
                pcmFormat: format,
                frameCapacity: AVAudioFrameCount(values.count)
              ),
              let channels = buffer.floatChannelData
        else { return nil }

        buffer.frameLength = AVAudioFrameCount(values.count)
        for channel in 0..<Int(format.channelCount) {
            for (i, value) in values.enumerated() {
                channels[channel][i] = value
            }
        }
        return buffer
    }
}

/// A quiet, deterministic wash for Sleepy Cloud.
///
/// This is deliberately synthesized rather than downloaded: there is no
/// recording to license, no network dependency, and no personalisation or
/// tracking. Two low-pass stages turn deterministic white noise into a soft
/// rain/ocean texture. The ends fade to silence, so the repeating buffer cannot
/// click at its seam.
enum SleepNoiseBuffer {
    static let duration: Double = 10
    static let peak: Float = 0.16
    static let edgeFade: Double = 0.75

    static func samples(
        sampleRate: Double = ToneBuffer.sampleRate,
        duration: Double = duration
    ) -> [Float] {
        guard sampleRate > 0, duration > 0 else { return [] }

        let count = Int(sampleRate * duration)
        guard count > 1 else { return [] }

        var seed: UInt64 = 0xC10D_5EED_2026
        var soft: Double = 0
        var deep: Double = 0
        var texture = [Double](repeating: 0, count: count)

        for index in 0..<count {
            // A fixed generator means the sound is stable between launches and
            // tests. The high bits of this LCG have the useful distribution.
            seed = seed &* 6_364_136_223_846_793_005 &+ 1
            let unit = Double((seed >> 40) & 0xFF_FFFF) / Double(0xFF_FFFF)
            let white = unit * 2 - 1

            soft = soft * 0.94 + white * 0.06
            deep = deep * 0.992 + white * 0.008
            texture[index] = soft * 0.82 + deep * 0.48
        }

        let mean = texture.reduce(0, +) / Double(texture.count)
        return texture.enumerated().map { index, value in
            let t = Double(index) / sampleRate
            // One slow swell per buffer. Because it is periodic, the ambience
            // breathes without an abrupt volume change when the loop restarts.
            let swell = 0.76 + 0.16 * sin((2 * .pi * t / duration) - (.pi / 2))
            let edge = min(1, min(t / edgeFade, (duration - t) / edgeFade))
            let centred = max(-1, min(1, (value - mean) * 2.6))
            return Float(centred * swell * max(0, edge)) * peak
        }
    }

    static func make(format: AVAudioFormat) -> AVAudioPCMBuffer? {
        let values = samples(sampleRate: format.sampleRate)
        guard !values.isEmpty,
              let buffer = AVAudioPCMBuffer(
                  pcmFormat: format,
                  frameCapacity: AVAudioFrameCount(values.count)
              ),
              let channels = buffer.floatChannelData
        else { return nil }

        buffer.frameLength = AVAudioFrameCount(values.count)
        for channel in 0..<Int(format.channelCount) {
            for (index, value) in values.enumerated() {
                channels[channel][index] = value
            }
        }
        return buffer
    }
}
