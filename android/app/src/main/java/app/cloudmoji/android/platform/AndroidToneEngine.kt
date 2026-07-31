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

    private fun build() {
        if (built) return
        built = true
        tracks = pitches.mapNotNull { pitch ->
            val samples = ToneBuffer.samples(frequency = pitch, sampleRate = sampleRate.toDouble())
            if (samples.isEmpty()) return@mapNotNull null
            runCatching { buildTrack(samples) }.getOrNull()
        }
    }

    private fun buildTrack(samples: FloatArray): AudioTrack {
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
        return track
    }
}
