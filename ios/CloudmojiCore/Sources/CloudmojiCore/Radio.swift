import Foundation

/// The wire format the phone and the watch talk over.
///
/// Two payload shapes cross the wrist gap, both encoded as `[String: String]`.
/// The all-String choice is not tidiness — it is the one thing that keeps the
/// feature clear of Swift 6's concurrency wall. `WCSession` hands its delegate a
/// `[String: Any]` on a background queue; a `[String: String]` is `Sendable`, so
/// the delegate can decode at the boundary and hop only the decoded, Sendable
/// value to the main actor. A `[String: Any]` cannot cross that hop.
///
/// Everything here is pure and nonisolated so it can be exercised without a
/// live session on either side.

/// One emoji travelling between the devices.
public struct RadioMessage: Equatable, Sendable {
    /// Which way it is going. The raw values are a wire contract between two
    /// installed app versions — a rename must fail a test, not silently drop a
    /// family's taps — so `RadioTests` pins them.
    public enum Direction: String, Sendable {
        case toWatch
        case toPhone
    }

    public let emoji: String
    public let direction: Direction
    /// The language the *sender* was in, so the receiver speaks it correctly
    /// even before a context sync has landed.
    public let language: Language

    public init(emoji: String, direction: Direction, language: Language) {
        self.emoji = emoji
        self.direction = direction
        self.language = language
    }

    /// The version tag rides on every payload so a future format change can be
    /// recognised rather than mis-parsed. Phase 1 only writes and accepts "1".
    static let version = "1"
    static let kind = "emoji"

    /// A `WCSession.sendMessage` payload.
    public var payload: [String: String] {
        [
            "v": Self.version,
            "kind": Self.kind,
            "emoji": emoji,
            "dir": direction.rawValue,
            "lang": language.rawValue,
        ]
    }

    /// Decodes what a session delegate received. `nil` — never a trap and never
    /// a fabricated default — for the wrong kind, a missing key, a non-String
    /// value, or an unknown enum raw value. A garbled message is silence, which
    /// is the safe direction (`CLAUDE.md` rule 4: no failure states).
    public init?(payload: [String: Any]) {
        guard payload["kind"] as? String == Self.kind,
              let emoji = payload["emoji"] as? String, !emoji.isEmpty,
              let dir = payload["dir"] as? String, let direction = Direction(rawValue: dir),
              let lang = payload["lang"] as? String, let language = Language(rawValue: lang)
        else { return nil }
        self.init(emoji: emoji, direction: direction, language: language)
    }
}

/// The parent's device state the watch mirrors: which language to speak, and
/// whether sound is muted. Pushed phone → watch as the application context — a
/// latest-value-wins channel that iOS persists and delivers even if the watch
/// app was closed, which is what lets the watch open already in sync.
public struct RadioContext: Equatable, Sendable {
    public let language: Language
    public let muted: Bool

    public init(language: Language, muted: Bool) {
        self.language = language
        self.muted = muted
    }

    static let version = "1"

    public var payload: [String: String] {
        [
            "v": Self.version,
            "lang": language.rawValue,
            "muted": muted ? "1" : "0",
        ]
    }

    /// `nil` on a missing/typed-wrong language. `muted` reads "1" → true and
    /// **anything else** → false — the harmless direction, the same reasoning
    /// `SettingsStore.seenTutorial` uses: a stale or malformed value can only
    /// mean "make a sound", never "silently stay muted".
    public init?(payload: [String: Any]) {
        guard let lang = payload["lang"] as? String,
              let language = Language(rawValue: lang)
        else { return nil }
        let muted = (payload["muted"] as? String) == "1"
        self.init(language: language, muted: muted)
    }
}
