package app.cloudmoji.android.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.view.WindowManager

/**
 * Keeps the screen alive, and gives it back. Ported from iOS `ScreenAwake`
 * (`Views/Sleepy/ScreenControl.swift`).
 *
 * A wind-down session is the one place in this app where the device must not
 * go dark on its own: a two-minute breathe with a thirty-second screen
 * timeout goes black halfway through and the child is left looking at
 * nothing. It is also the one place where *leaking* the hold is worst — an
 * app that quietly disabled the screen timeout and never re-enabled it
 * flattens the battery, and nothing on screen would ever say so.
 *
 * So the flag has an owner with a balanced pair of methods, and the writer
 * is injected: a test can prove hold-and-release without touching a real
 * `Window`, which in a unit test does not exist at all.
 *
 * Unlike iOS's `UIApplication.shared.isIdleTimerDisabled` — a process-wide
 * flag — Android's equivalent ([setKeepScreenOn], which
 * `SleepyCloudScreen`'s own wiring passes as this class's [write]) is scoped
 * to one window, and the platform itself stops honouring it the moment that
 * window is no longer the foreground one. That is a real, favourable
 * platform difference: an Android session backgrounded without this class's
 * [release] ever running cannot leak the hold onto whatever the user
 * switches to next, the way an unreleased iOS flag could keep a *different*
 * foreground app's screen from timing out. `SleepyCloudScreen` still calls
 * [release] on every exit anyway, for the same "the writer should never have
 * to remember whether this matters" discipline the rest of this app's
 * audio/focus code already keeps.
 */
class ScreenAwake(private val write: (Boolean) -> Unit = {}) {

    /** Whether this owner is currently holding the screen awake. Idempotent
     * in both directions — [hold] twice writes once, which matters because
     * a fresh composition and a lifecycle callback can both arrive for one
     * entry. */
    var isHeld: Boolean = false
        private set

    fun hold() {
        if (isHeld) return
        isHeld = true
        write(true)
    }

    fun release() {
        if (!isHeld) return
        isHeld = false
        write(false)
    }
}

/**
 * Turns the screen down across a session, and puts it back exactly where it
 * was. Ported from iOS `ScreenDimmer` (`Views/Sleepy/ScreenControl.swift`).
 *
 * The brightness a parent had set is theirs, not this app's, so the
 * original is captured on the first [dim] and restored by [restore] — from
 * leaving the mini-app, from backgrounding, and from reaching the end of
 * the session, because a mini-app that hands back a phone at a third of its
 * usual brightness has broken something the person holding it cannot easily
 * explain.
 *
 * [read] and [write] are injected for the same reason [ScreenAwake]'s
 * writer is: real screen brightness is device-wide, window-scoped state
 * that a test must not move. `SleepyCloudScreen`'s own default wiring reads
 * the system brightness setting and writes an override on the hosting
 * `Activity`'s `Window` — see that file's doc for why those are two
 * different Android APIs, unlike iOS's single `UIScreen.brightness`
 * property.
 */
class ScreenDimmer(
    private val read: () -> Float = { 1f },
    private val write: (Float) -> Unit = {},
) {
    /** What the screen was at before the first [dim]. `null` means nothing
     * has been taken and there is nothing to give back. */
    var original: Float? = null
        private set

    val isDimmed: Boolean get() = original != null

    companion object {
        /** The floor, as a fraction of [original]. Below about a third the
         * screen is unreadably dark on an already-dim phone, and the point
         * is a room getting quieter, not a screen that has failed. */
        const val FLOOR: Double = 0.35

        /** The brightness for a session [progress] (0...1) of the way
         * through, as a fraction of where the screen started. Pure, so the
         * ramp can be checked without a screen. */
        fun level(original: Float, progress: Double): Float {
            val clamped = progress.coerceIn(0.0, 1.0)
            return (original * (1 - (1 - FLOOR) * clamped)).toFloat()
        }
    }

    /** Takes the screen down to where [progress] says it should be,
     * remembering where it started the first time it is called. */
    fun dim(progress: Double) {
        val base = original ?: read()
        original = base
        write(level(base, progress))
    }

    /** Puts it back. Safe to call when nothing was ever taken, and safe to
     * call twice — which it will be, because more than one exit path calls
     * it (see [SleepyCloudScreen]'s own doc, mirroring iOS
     * `yieldTheScreen`'s "three exits" comment). */
    fun restore() {
        val base = original ?: return
        write(base)
        original = null
    }
}

/**
 * Finds the hosting [Activity] from a possibly-wrapped [Context] — the
 * standard Compose idiom for reaching the window a composable is drawn
 * into, since `LocalContext.current` is not guaranteed to be the `Activity`
 * itself once a theme or test harness has wrapped it in a
 * [ContextWrapper]. `null` only in a context this app never actually runs
 * in (a preview, or a future non-Activity host) — [ScreenDimmer]'s real
 * [write] default already treats that as "nothing to do" rather than a
 * crash.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A best-effort read of the device's own brightness setting, normalised to
 * `0f..1f` — [ScreenDimmer]'s real [ScreenDimmer.dim] source, wired at
 * [app.cloudmoji.android.ui.sleepy.SleepyCloudScreen]'s own construction
 * site. Reading `Settings.System.SCREEN_BRIGHTNESS` needs no permission on
 * any API level this app supports, unlike *writing* it (`WRITE_SETTINGS`,
 * a dangerous permission this app does not hold and should never need —
 * see [setWindowBrightness] for the permission-free alternative this app
 * writes through instead). Falls back to full brightness if the setting
 * cannot be read for any reason: a bedtime routine that cannot discover the
 * room's current brightness should start from "not dimmed" rather than
 * fail outright.
 */
fun systemBrightnessFraction(context: Context): Float =
    runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(255).coerceIn(0, 255) / 255f

/**
 * Overrides just this [activity]'s own window brightness —
 * [ScreenDimmer]'s real write target. Scoped to one window, not the
 * device: unlike `Settings.System.SCREEN_BRIGHTNESS` (which needs
 * `WRITE_SETTINGS` to change and would leave every other app dimmed too
 * until something puts it back), a `Window.attributes.screenBrightness`
 * override needs no permission and the platform itself stops honouring it
 * the instant this `Activity` is no longer the foreground window — see
 * [ScreenAwake]'s own doc for why that same shape is a real, favourable
 * platform difference from iOS's single, device-wide `UIScreen.brightness`.
 *
 * Pass [BRIGHTNESS_FOLLOWS_SYSTEM] to drop the override entirely rather
 * than pin the window at some number: that is what `SleepyCloudScreen`
 * writes on the way out, and it is strictly better than iOS's own restore.
 * iOS has no choice but to write the remembered brightness back, because
 * `UIScreen.brightness` is one device-wide value with no "no opinion"
 * state; Android does have one, and using it means a parent who changes the
 * system brightness after a wind-down session still sees this app follow
 * along.
 */
fun setWindowBrightness(activity: Activity, value: Float) {
    val window = activity.window
    window.attributes = window.attributes.apply { screenBrightness = value }
}

/**
 * `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE` — "this window has
 * no opinion about brightness; follow the system." Named rather than spelled
 * `-1f` at the call site, because a magic negative float in a brightness
 * expression reads like a bug.
 */
const val BRIGHTNESS_FOLLOWS_SYSTEM: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

/**
 * [ScreenAwake]'s real write target: `FLAG_KEEP_SCREEN_ON` on this
 * [activity]'s own window — the Android analogue of iOS's
 * `UIApplication.shared.isIdleTimerDisabled`, and deliberately not a
 * `PowerManager.WakeLock`.
 *
 * A wake lock is a process-wide, permission-carrying object that survives
 * the screen it was taken for and has to be released by hand or it runs the
 * battery down silently — precisely the leak [ScreenAwake]'s own doc is
 * about. A window flag cannot leak that way: the platform stops honouring it
 * the moment this window is not the foreground one, and it dies with the
 * `Activity` regardless. `SleepyCloudScreen` still clears it on every exit —
 * the flag being harmless is not a reason to leave it set.
 */
fun setKeepScreenOn(activity: Activity, on: Boolean) {
    if (on) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
