package app.cloudmoji.android.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Binds [ToneEngineDriving] to `android.media.AudioTrack`.
 *
 * One `AudioTrack` per pad ([pitches].size total), each built once in
 * `MODE_STATIC` with its waveform written up front. This is the Android
 * analogue of iOS `ToneEngine`'s one-player-node-per-pad design
 * (`AudioDirector.swift`): separate tracks are what let two fingers on two
 * pads sound together as a chord — a single shared track would queue the
 * second tone behind the first — and pre-loading each track's buffer once
 * is what keeps the *first* tap after [start] from paying a synthesis cost,
 * per the Task 10 brief's low-latency requirement.
 *
 * **Restarting a still-playing pad**: `AudioTrack.setPlaybackHeadPosition(0)`
 * rewinds a `MODE_STATIC` track to its first sample, and is only legal while
 * the track is stopped or paused — so [playTone] always calls `stop()`
 * first, unconditionally, before rewinding and playing again. The result is
 * the same "interrupt rather than queue" behaviour iOS's own
 * `ToneEngine.playTone` doc calls out: a repeated pad is a fresh strike, not
 * a wait in line.
 *
 * **Built once, kept forever** — [build] is guarded by [built] the same way
 * iOS `ToneEngine.build()` is guarded by `isBuilt`: most sessions never open
 * Music, so building eight tracks happens lazily, on the first [start]
 * rather than at construction. Unlike iOS's `AVAudioEngine`, though, there is
 * no shared audio graph to start/stop as a unit — each `AudioTrack` is
 * independent and stays usable once built, so [stop] here only halts
 * whatever is currently sounding; it does not tear the tracks down, and
 * [isRunning] reports whether they were ever built, not whether the platform
 * silently killed them out from under this class the way an interrupted
 * `AVAudioEngine` can. A real Android focus interruption is instead handled
 * proactively — see [AudioFocusLossAction] — rather than discovered lazily
 * through [isRunning] the way iOS's `restartIfStalled` discovers one.
 *
 * Not host-testable — it binds to the real `AudioTrack`, which needs a
 * device's audio stack — which is exactly why [ToneBuffer]'s synthesis math
 * and [ToneDirector]'s arbitration are both kept in separate, pure-JVM
 * classes that are.
 */
class AndroidToneEngine(
    private val pitches: List<Double> = ToneBuffer.pitches,
    private val sampleRate: Int = ToneBuffer.sampleRate.toInt(),
) : ToneEngineDriving {

    private var tracks: List<AudioTrack> = emptyList()

    /** The bedtime ambience — a ninth track, separate from [tracks] so
     * [playTone]'s index lookup never accidentally reaches it. Built
     * alongside the eight pads in [build], mirroring iOS's real `ToneEngine`
     * class, which attaches its own `sleepPlayer` in the very same `build()`
     * that attaches the eight pad players — one graph, built once, whichever
     * of Music or Sleepy Cloud opens it first. `null` only when
     * [SleepNoiseBuffer.samples] degenerates or the platform refuses to
     * build the track — the same "silent, not a crash" fallback [tracks]
     * itself already tolerates per-pad via [mapNotNull]. */
    private var sleepTrack: AudioTrack? = null
    private var built = false

    override val isRunning: Boolean
        get() = built

    override fun start() {
        build()
    }

    override fun stop() {
        for (track in tracks) {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
        }
        stopSleepNoise()
    }

    override fun playTone(index: Int) {
        if (!built) return
        val track = tracks.getOrNull(index) ?: return
        // Silent on failure, and that is the product decision: a phone that
        // cannot restart a track should show a child a working instrument
        // that happens to be quiet on that one pad, not a crash.
        runCatching {
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        }
    }

    /**
     * Starts the ambience from the beginning. A no-op while it is already
     * playing — mirrors iOS `ToneEngine.playSleepNoise()`'s own
     * `guard !sleepPlayer.isPlaying else { return }`, which is what keeps a
     * mute-then-unmute inside one session from restarting the loop
     * audibly from silence instead of continuing it.
     *
     * Unlike [playTone] (always a fresh strike, `.interrupts`-style — see
     * that method's own doc), a call while already playing here is
     * deliberately *not* a restart: the ambience is a continuous bed, not a
     * struck note, and restarting it on every redundant call (Sleepy Cloud's
     * own [ToneDirector.playSleepNoise] can be called more than once for one
     * unmute) would introduce an audible seam nobody asked for.
     */
    override fun playSleepNoise() {
        if (!built) return
        val track = sleepTrack ?: return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) return
        // `setPlaybackHeadPosition` requires the track to already be
        // stopped/paused, hence stopping first — the same ordering
        // [playTone] uses, even though this track is only ever reached here
        // already stopped (the `isPlaying` guard above), for the same
        // defensive reason: a track this class did not itself stop (a
        // platform quirk) must not fail `setPlaybackHeadPosition` outright.
        runCatching {
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        }
    }

    override fun stopSleepNoise() {
        val track = sleepTrack ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        }
    }

    private fun build() {
        if (built) return
        built = true
        tracks = pitches.mapNotNull { pitch ->
            val samples = ToneBuffer.samples(frequency = pitch, sampleRate = sampleRate.toDouble())
            if (samples.isEmpty()) return@mapNotNull null
            runCatching { buildTrack(samples, looping = false) }.getOrNull()
        }
        val sleepSamples = SleepNoiseBuffer.samples(sampleRate = sampleRate.toDouble())
        sleepTrack = if (sleepSamples.isEmpty()) {
            null
        } else {
            runCatching { buildTrack(sleepSamples, looping = true) }.getOrNull()?.also {
                it.setVolume(SLEEP_VOLUME)
            }
        }
    }

    /** [looping] uses `AudioTrack.setLoopPoints(0, frameCount, LOOP_FOREVER)`
     * to repeat the whole buffer indefinitely — legal on a `MODE_STATIC`
     * track once its data has been written, which is why this call comes
     * after [AudioTrack.write] rather than before. [SleepNoiseBuffer]'s own
     * edge fade is what keeps the wrap from clicking. */
    private fun buildTrack(samples: FloatArray, looping: Boolean): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (looping) track.setLoopPoints(0, samples.size, LOOP_FOREVER)
        return track
    }

    companion object {
        /** A gentle, constant bedtime level. Soft enough to sit under a
         * quiet room without droning — the noise is a bed, not the event.
         * Mirrors iOS `ToneEngine.sleepVolume`. */
        private const val SLEEP_VOLUME: Float = 0.5f

        /** `AudioTrack.setLoopPoints`'s own sentinel for "forever". */
        private const val LOOP_FOREVER: Int = -1
    }
}
