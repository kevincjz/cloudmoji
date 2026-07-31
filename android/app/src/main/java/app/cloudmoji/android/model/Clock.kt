package app.cloudmoji.android.model

/**
 * The wall clock, as a seam. [SleepySessionState] measures a session's
 * elapsed time by re-reading "now" against a fixed start instant on every
 * call — the same choice iOS `SleepyCloudView` makes with `Date()` rather
 * than accumulating a running total — so a test has to be able to move time
 * forward without a real `Thread.sleep`, and production has to read the real
 * clock. `model/` never imports `platform/` (the established dependency
 * direction in this codebase — see `CloudmojiApplication`'s own wiring), so
 * this lives here rather than beside the Activity/Window-facing classes in
 * `platform/ScreenControl.kt` that also serve [SleepySessionState]'s screen.
 */
interface Clock {
    fun nowMillis(): Long
}

/** The real clock. The one production implementation. */
object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
