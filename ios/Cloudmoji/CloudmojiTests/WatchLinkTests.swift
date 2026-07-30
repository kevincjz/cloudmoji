import Foundation
import Testing
import CloudmojiCore
@testable import Cloudmoji

@Suite("WatchLink")
@MainActor
struct WatchLinkTests {

    /// A fake in place of `WCSession`, the same seam `FakeEngine` is for
    /// `AudioDirector`. Records what was sent and lets a test play the watch's
    /// side by calling `deliver`.
    final class FakeTransport: RadioTransporting {
        var isSupported: Bool
        var onMessage: ((RadioMessage) -> Void)?
        var onVoice: ((Data) -> Void)?
        var onHello: (() -> Void)?
        var activated = false
        var deactivated = false
        var sent: [[String: String]] = []
        var contexts: [[String: String]] = []

        init(isSupported: Bool = true) { self.isSupported = isSupported }

        func activate() { activated = true }
        func deactivate() {
            activated = false
            deactivated = true
        }
        func send(_ payload: [String: String]) { sent.append(payload) }
        func updateContext(_ payload: [String: String]) { contexts.append(payload) }

        /// Simulate an emoji arriving from the watch.
        func deliver(_ message: RadioMessage) { onMessage?(message) }
        /// Simulate a voice clip arriving from the watch.
        func deliverVoice(_ data: Data) { onVoice?(data) }
    }

    private func make(
        language: Language = .en,
        muted: Bool = false,
        supported: Bool = true,
        unlocked: Bool = true
    ) -> (WatchLink, FakeTransport, SettingsStore) {
        let defaults = UserDefaults(suiteName: "watchlink-\(UUID().uuidString)")!
        defaults.set(unlocked, forKey: StubEntitlementStore.storageKey)
        let settings = SettingsStore(defaults: defaults)
        settings.language = language
        settings.muted = muted
        let transport = FakeTransport(isSupported: supported)
        let entitlements = StubEntitlementStore(defaults: defaults)
        return (
            WatchLink(
                settings: settings,
                entitlements: entitlements,
                transport: transport
            ),
            transport,
            settings
        )
    }

    /// Bringing the link up tells the watch the current state, so it opens in
    /// sync.
    ///
    /// Mutation: delete `pushContext()` from `activate`. No context is sent and
    /// this fails.
    @Test("activating pushes the current context")
    func activatingPushesContext() {
        let (link, transport, _) = make(language: .ja, muted: true)
        link.activate()
        #expect(transport.activated)
        let context = RadioContext(payload: transport.contexts.last ?? [:])
        #expect(context == RadioContext(language: .ja, muted: true))
    }

    /// On a device WatchConnectivity does not support — an iPad — the whole
    /// feature is a silent no-op, never a crash.
    ///
    /// Mutation: drop the `guard transport.isSupported` from `activate` and
    /// `childTapped`. Something gets recorded and this fails.
    @Test("an unsupported transport is never activated or sent to")
    func unsupportedTransportIsInert() {
        let (link, transport, _) = make(supported: false)
        link.activate()
        link.childTapped("🍎")
        #expect(!transport.activated)
        #expect(transport.sent.isEmpty)
        #expect(transport.contexts.isEmpty)
    }

    @Test("a free installation cannot activate, send or receive Watch content")
    func lockedEntitlementIsInert() {
        let (link, transport, _) = make(language: .ja, unlocked: false)

        link.activate()
        link.childTapped("🍎")
        transport.deliver(RadioMessage(emoji: "🐶", direction: .toPhone, language: .ja))
        transport.deliverVoice(Data([1, 2, 3]))

        #expect(!transport.activated)
        #expect(transport.sent.isEmpty)
        #expect(transport.contexts.isEmpty)
        #expect(link.incoming == nil)
        #expect(link.incomingVoice == nil)
    }

    @Test("deactivation clears received content and stops the transport")
    func deactivationClearsContent() {
        let (link, transport, _) = make()
        transport.deliver(RadioMessage(emoji: "🐶", direction: .toPhone, language: .en))
        transport.deliverVoice(Data([1]))

        link.deactivate()

        #expect(transport.deactivated)
        #expect(link.incoming == nil)
        #expect(link.incomingVoice == nil)
    }

    /// A child's tap becomes a `.toWatch` message in the language the parent set.
    ///
    /// Mutation: delete the `transport.send` line in `childTapped`. Nothing is
    /// sent and this fails.
    @Test("a child's tap becomes a toWatch message in the current language")
    func childTapSendsMessage() {
        let (link, transport, _) = make(language: .zh)
        link.childTapped("🍎")
        let message = RadioMessage(payload: transport.sent.last ?? [:])
        #expect(message == RadioMessage(emoji: "🍎", direction: .toWatch, language: .zh))
    }

    /// Every tap also refreshes the watch's state — cheap, and it keeps a watch
    /// that missed a Settings change up to date.
    ///
    /// Mutation: delete `pushContext()` from `childTapped`. No context follows
    /// the send and this fails.
    @Test("each tap refreshes the context")
    func tapRefreshesContext() {
        let (link, transport, _) = make()
        link.childTapped("🍎")
        #expect(!transport.contexts.isEmpty)
    }

    /// An incoming emoji is published, and the same glyph arriving twice
    /// publishes twice — the token bump is what lets the view re-flash a repeat.
    ///
    /// Mutation: remove the `token += 1` in `receive`. The second delivery has
    /// the same id and this fails.
    @Test("an incoming message publishes, and a repeat re-publishes")
    func incomingPublishesEachTime() {
        let (link, transport, _) = make()
        transport.deliver(RadioMessage(emoji: "🐶", direction: .toPhone, language: .en))
        let first = link.incoming
        #expect(first?.message.emoji == "🐶")

        transport.deliver(RadioMessage(emoji: "🐶", direction: .toPhone, language: .en))
        #expect(link.incoming?.id != first?.id, "a repeated emoji must re-publish")
    }

    /// Our own outgoing echo coming back in is dropped — a `.toWatch` message on
    /// the incoming path would loop.
    ///
    /// Mutation: delete the `direction == .toPhone` guard in `receive`. The
    /// `.toWatch` delivery publishes and this fails.
    @Test("a toWatch message arriving back is dropped")
    func toWatchEchoIsDropped() {
        let (link, transport, _) = make()
        transport.deliver(RadioMessage(emoji: "🍎", direction: .toWatch, language: .en))
        #expect(link.incoming == nil)
    }

    /// **Sleepy Cloud suppresses everything.** A bedtime screen must not light up
    /// or make a sound because a parent's thumb brushed the watch.
    ///
    /// Mutation: delete the `active == .sleepy` branch. It falls through to a
    /// bubble and this fails.
    @Test("presentation: Sleepy Cloud suppresses the echo entirely")
    func sleepySuppresses() {
        #expect(WatchLink.presentation(active: .sleepy, muted: false) == .suppressed)
        #expect(WatchLink.presentation(active: .sleepy, muted: true) == .suppressed)
    }

    /// A muted phone shows the emoji — the picture is the point — but says
    /// nothing, on every screen except Sleepy Cloud.
    ///
    /// Mutation: collapse the muted branch to `.bubbleAndSpeech`. This fails.
    @Test("presentation: mute keeps the bubble and drops the speech")
    func mutePresentsBubbleOnly() {
        for active in [MiniApp?.none, .some(.words), .some(.count), .some(.photos)] {
            #expect(WatchLink.presentation(active: active, muted: true) == .bubbleOnly)
        }
    }

    /// The ordinary case: bubble and speech.
    @Test("presentation: the everyday case shows and speaks")
    func ordinaryPresentsBoth() {
        #expect(WatchLink.presentation(active: nil, muted: false) == .bubbleAndSpeech)
        #expect(WatchLink.presentation(active: .words, muted: false) == .bubbleAndSpeech)
    }

    /// A voice clip is published for the view to play, and a fresh clip
    /// re-publishes even if the bytes happen to match.
    ///
    /// Mutation: remove the `voiceToken += 1` in `receiveVoice`. The second clip
    /// keeps the first id and this fails.
    @Test("an incoming voice clip publishes, and a repeat re-publishes")
    func incomingVoicePublishes() {
        let (link, transport, _) = make()
        let clip = Data([1, 2, 3, 4])
        transport.deliverVoice(clip)
        let first = link.incomingVoice
        #expect(first?.data == clip)

        transport.deliverVoice(clip)
        #expect(link.incomingVoice?.id != first?.id, "a second clip must re-publish")
    }

    /// An empty transfer is not a message.
    ///
    /// Mutation: drop the `!data.isEmpty` guard. The empty clip publishes and
    /// this fails.
    @Test("an empty voice clip is ignored")
    func emptyVoiceIgnored() {
        let (link, transport, _) = make()
        transport.deliverVoice(Data())
        #expect(link.incomingVoice == nil)
    }

    /// The pushed context carries the live mute flag, so flipping mute on the
    /// phone reaches the watch.
    @Test("the pushed context reflects the current mute state")
    func contextCarriesMute() {
        let (link, transport, settings) = make(muted: false)
        link.pushContext()
        #expect(RadioContext(payload: transport.contexts.last ?? [:])?.muted == false)
        settings.muted = true
        link.pushContext()
        #expect(RadioContext(payload: transport.contexts.last ?? [:])?.muted == true)
    }
}
