import AVFoundation
import Observation

/// Holds and plays the voice message a parent sent from their watch.
///
/// **The clip lives in memory, for the session, and is never written to our own
/// storage.** WatchConnectivity delivers a file; `WCSessionTransport` reads it
/// into `Data` and deletes the delivered copy, and only that `Data` reaches
/// here. Playing it builds an `AVAudioPlayer` straight from the bytes — no temp
/// file of ours, nothing on disk, gone when the app closes.
///
/// One message at a time: a new one replaces the last, which is what "the latest
/// message is replayable" means.
@MainActor
@Observable
final class VoiceMailbox: NSObject {
    /// Whether a clip is held and can be replayed.
    private(set) var hasMessage = false
    /// Whether it is sounding right now, so the UI can pulse.
    private(set) var isPlaying = false

    private var data: Data?
    private var player: AVAudioPlayer?

    /// Takes a new clip, replacing any held one. Does not play it — the caller
    /// decides that, because muting and Sleepy Cloud gate playback.
    func hold(_ data: Data) {
        stop()
        self.data = data
        hasMessage = true
    }

    /// Plays (or replays) the held clip. The audio session is already `.playback`
    /// and active — `AudioDirector` owns it from launch — so there is nothing to
    /// configure here.
    func play() {
        guard let data else { return }
        stop()
        guard let player = try? AVAudioPlayer(data: data) else { return }
        player.delegate = self
        self.player = player
        player.play()
        isPlaying = true
    }

    func stop() {
        player?.stop()
        player = nil
        isPlaying = false
    }

    /// Drops the held clip entirely — on leaving into Sleepy Cloud, say.
    func clear() {
        stop()
        data = nil
        hasMessage = false
    }
}

extension VoiceMailbox: AVAudioPlayerDelegate {
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.isPlaying = false }
    }
}
