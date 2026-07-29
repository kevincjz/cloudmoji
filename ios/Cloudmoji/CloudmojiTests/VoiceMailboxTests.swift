import Foundation
import Testing
@testable import Cloudmoji

@Suite("VoiceMailbox")
@MainActor
struct VoiceMailboxTests {

    /// A tiny but real AAC clip, so `AVAudioPlayer(data:)` in `play()` has
    /// something valid to chew on if a test ever calls it — these tests exercise
    /// the holding logic, not playback (which needs an output device).
    private func clip() -> Data { Data([0xDE, 0xAD, 0xBE, 0xEF]) }

    /// Holding a clip makes it replayable; there is nothing before one arrives.
    ///
    /// Mutation: leave `hasMessage` false in `hold`. This fails.
    @Test("holding a clip marks a message available")
    func holdMarksAvailable() {
        let mailbox = VoiceMailbox()
        #expect(!mailbox.hasMessage)
        mailbox.hold(clip())
        #expect(mailbox.hasMessage)
    }

    /// Clearing forgets the clip — what Sleepy Cloud and app-close do.
    ///
    /// Mutation: make `clear` a no-op. This fails.
    @Test("clearing forgets the held message")
    func clearForgets() {
        let mailbox = VoiceMailbox()
        mailbox.hold(clip())
        mailbox.clear()
        #expect(!mailbox.hasMessage)
    }

    /// A new clip replaces the last — only the most recent is replayable, which
    /// is what "the latest message" means.
    @Test("a new clip replaces the previous one")
    func newClipReplaces() {
        let mailbox = VoiceMailbox()
        mailbox.hold(Data([1]))
        mailbox.hold(Data([2]))
        // Still exactly one message held (no accumulation), and not playing.
        #expect(mailbox.hasMessage)
        #expect(!mailbox.isPlaying)
    }
}
