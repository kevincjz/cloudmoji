import Foundation

/// One shape for every delayed effect on every screen, so none of them can forget
/// the cancellation check.
///
/// Fire-and-forget looks equivalent and is not: a stale 400ms timer clears the
/// bounce the child is currently looking at, and a stale 600ms timer opens the
/// mascot's mouth for a word that is no longer being said. Every caller holds the
/// returned task so the *next* event can cancel it.
///
/// Lifted out of `WordsView`, where it was private, because `CountView` needs the
/// identical shape and a second copy is how two timers drift apart.
@MainActor
func afterDelay(
    _ delay: Duration,
    _ work: @escaping @MainActor () -> Void
) -> Task<Void, Never> {
    Task {
        try? await Task.sleep(for: delay)
        guard !Task.isCancelled else { return }
        work()
    }
}
