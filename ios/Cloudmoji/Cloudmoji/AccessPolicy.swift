import CloudmojiCore

/// Pure product policy for the one Full Cloudmoji entitlement.
///
/// Keeping these answers together prevents a new route from inventing its own
/// interpretation of "Full" and makes the exact launch contract unit-testable.
struct AppAccessPolicy: Equatable {
    let hasFullAccess: Bool

    func canUse(_ app: MiniApp) -> Bool {
        hasFullAccess || !app.requiresFull
    }

    func effectiveLanguage(preferred: Language) -> Language {
        hasFullAccess ? preferred : .en
    }

    var canUseWatch: Bool { hasFullAccess }

    /// Explicit even though the whole Watch experience currently requires Full.
    /// If Watch scope changes later, voice recording stays paid until this policy
    /// is deliberately changed.
    var canUseWatchVoiceNotes: Bool { hasFullAccess }
}
