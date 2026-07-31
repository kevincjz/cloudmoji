package app.cloudmoji.android.ui.sleepy

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.model.Language
import java.util.Locale

/**
 * Every number Sleepy Cloud is drawn from, and the pure arithmetic behind
 * the scene. Mirrors iOS `SleepyCloudView.swift`'s own static properties,
 * pt for dp.
 *
 * Its own file rather than a companion inside `SleepyCloudScreen.kt` for one
 * reason: everything here is pure `Dp`/`Double`/`Float` arithmetic with no
 * Compose runtime behind it, so `SleepyCloudMetricsTest` can execute it on
 * the JVM — and in this environment (no emulator, see `conventions.md`) a
 * number that only a Compose test could reach is a number nothing checks.
 */
object SleepyCloudMetrics {
    /**
     * The prototype's 56pt buttons lose to `CLAUDE.md` rule 1: either a
     * child or a grown-up may pick the time, so every option has to answer a
     * toddler tap. iOS `SleepyCloudView.choiceHeight`/`choiceWidth` — 64 x 96
     * — already clears the 64dp floor in both axes, and the wide plate means
     * the *aimed-at* dimension is the 96 one.
     */
    val choiceHeight: Dp = 64.dp
    val choiceWidth: Dp = 96.dp

    /** iOS `padChoiceHeight`/`padChoiceWidth`. */
    val padChoiceHeight: Dp = 78.dp
    val padChoiceWidth: Dp = 126.dp

    /** `CLAUDE.md` rule 2's floor between adjacent child targets, and iOS's
     * own `HStack(spacing:)` for the row of three (10 upright, 16 on an
     * expanded pad) — both already past it. */
    val choiceSpacing: Dp = 10.dp
    val padChoiceSpacing: Dp = 16.dp

    val choiceCornerRadius: Dp = 20.dp
    val borderWidth: Dp = 2.dp

    /** Design system Active States. iOS `PressScale(scale: 0.94)`. */
    const val PRESSED_SCALE: Float = 0.94f

    /** The "again" button after a finished session: iOS gives it
     * `minWidth: 96, minHeight: HomeButtonMetrics.side` — a child-facing
     * target well past the floor, since the child who taps it is the one who
     * just watched the cloud fall asleep. */
    val againWidth: Dp = 96.dp
    val againHeight: Dp = 84.dp

    /** How dark the overlay gets by the end. The prototype's
     * `progress * 0.55`, via iOS `SleepyCloudView.maximumDim`. */
    const val MAXIMUM_DIM: Double = 0.55

    /** The scrim carries only half the dim; the other half is spent fading
     * the moon, the stars and the cloud's own glow, so the screen gets
     * quieter rather than merely darker. iOS: `Color.black.opacity(dim * 0.5)`. */
    const val SCRIM_SHARE: Float = 0.5f

    /** iOS `.animation(.linear(duration: 1.2), value: dim)` — the dim is
     * stepped once a second and eased across the gap, so nothing on screen
     * ever steps visibly. */
    const val DIM_ANIMATION_MILLIS: Int = 1200

    /** iOS `runDimLoop`'s own `Task.sleep(for: .seconds(1))`. The breathing
     * is redrawn every frame; the dimming is not, and re-writing a window's
     * brightness sixty times a second is a real cost for a change no eye can
     * see. */
    const val TICK_MILLIS: Long = 1_000L

    /** Fourteen faint stars, placed by the prototype's own arithmetic so the
     * sky is the same sky every night. Deliberately not random: a layout
     * that moved every time the screen opened would be one more thing
     * changing at bedtime. */
    const val STAR_COUNT: Int = 14
    const val STAR_DIAMETER: Float = 3f

    /** iOS `StarTwinkle`: opacity 0.15 -> 0.5. */
    const val STAR_DIM_ALPHA: Float = 0.15f
    const val STAR_BRIGHT_ALPHA: Float = 0.5f

    /** One sweep of the shared twinkle clock. iOS gives each star its own
     * `period: 3 + Double(i % 4)` and `delay: Double(i) * 0.4`; those are
     * folded into [starTwinkleAlpha] below so the whole sky runs off one
     * animation rather than fourteen. */
    const val TWINKLE_CYCLE_MILLIS: Int = 12_000

    /** The progress line along the bottom. iOS: 2pt tall, 0.35 opacity. */
    val progressLineHeight: Dp = 2.dp
    const val PROGRESS_LINE_ALPHA: Float = 0.35f

    /** Where star [index] sits, as a fraction of the width. iOS
     * `proxy.size.width * CGFloat((i * 37) % 100) / 100`. */
    fun starXFraction(index: Int): Float = ((index * 37) % 100) / 100f

    /** Where star [index] sits, as a fraction of the height. iOS
     * `proxy.size.height * CGFloat((i * 23) % 90) / 100` — note the 90, not
     * 100: the stars stay clear of the bottom edge, where the cloud and the
     * home button are. */
    fun starYFraction(index: Int): Float = ((index * 23) % 90) / 100f

    /**
     * Star [index]'s opacity at [clock] (0...1, one sweep of
     * [TWINKLE_CYCLE_MILLIS]).
     *
     * iOS animates each star independently with its own
     * `.easeInOut(duration: period / 2).repeatForever(autoreverses: true)`
     * and a per-star delay. Compose could do the same with fourteen
     * `InfiniteTransition` children, but each of those is a separate
     * observable `State` read during composition; folding the same two
     * numbers into one cosine off a single shared clock means the sky costs
     * one animation and is read in the *draw* phase instead — fourteen stars
     * are not worth fourteen recomposition scopes running at frame rate.
     * The visible result is the same: every star breathes between
     * [STAR_DIM_ALPHA] and [STAR_BRIGHT_ALPHA] on its own period, out of
     * step with its neighbours.
     */
    fun starTwinkleAlpha(index: Int, clock: Float): Float {
        val period = 3f + (index % 4)
        val delay = index * 0.4f
        val seconds = clock * (TWINKLE_CYCLE_MILLIS / 1000f)
        // cos over the star's own period, remapped from -1...1 to 0...1.
        val wave = 0.5f - 0.5f * kotlin.math.cos(
            (2f * Math.PI.toFloat() * (seconds - delay)) / period,
        )
        return STAR_DIM_ALPHA + wave * (STAR_BRIGHT_ALPHA - STAR_DIM_ALPHA)
    }

    /** The overlay's darkness for a session [progress] of the way through.
     * iOS `SleepyCloudView.dim`. Clamped, because a progress out of range is
     * a bug elsewhere and must not produce an invalid alpha here. */
    fun dim(progress: Double): Double = progress.coerceIn(0.0, 1.0) * MAXIMUM_DIM

    /** How wide the cloud is drawn. iOS `SleepyCloudView.cloudWidth`:
     * sideways it is drawn small, because a landscape phone gives about
     * 400dp of height and the upright stack — cloud, title, caption, three
     * 64dp buttons, footer — overflowed it and clipped the cloud against the
     * top edge. */
    fun cloudWidth(isExpandedPad: Boolean, isCompactPhone: Boolean): Dp = when {
        isExpandedPad -> 300.dp
        isCompactPhone -> BreathingCloudMetrics.compactRenderedWidth
        else -> BreathingCloudMetrics.renderedWidth
    }
}

/**
 * Sleepy Cloud's chrome, in the five languages. Copy, not content —
 * `src/data/` stays the single source of truth for anything a child is
 * *taught*, and none of this is; the precedent and the reasoning are iOS
 * `SleepyCloudView.uiText`'s own, which in turn follows `CountView.uiText`.
 *
 * Ported string for string from iOS.
 */
object SleepyUiText {
    val title: Map<Language, String> = mapOf(
        Language.English to "Sleepy Cloud",
        Language.Chinese to "瞌睡云",
        Language.Malay to "Awan Mengantuk",
        Language.Japanese to "ねむいくも",
        Language.Tagalog to "Inaantok na Ulap",
    )

    val subtitle: Map<Language, String> = mapOf(
        Language.English to "Breathe along with the cloud",
        Language.Chinese to "跟着云朵呼吸",
        Language.Malay to "Bernafas bersama awan",
        Language.Japanese to "くもと いっしょに いきをしよう",
        Language.Tagalog to "Huminga kasabay ng ulap",
    )

    val grownUp: Map<Language, String> = mapOf(
        Language.English to "Pick a sleepy time",
        Language.Chinese to "选择睡眠时间",
        Language.Malay to "Pilih masa tidur",
        Language.Japanese to "ねむる じかんを えらぼう",
        Language.Tagalog to "Pumili ng oras ng tulog",
    )

    val breatheIn: Map<Language, String> = mapOf(
        Language.English to "breathe in",
        Language.Chinese to "吸气",
        Language.Malay to "tarik nafas",
        Language.Japanese to "すって",
        Language.Tagalog to "huminga",
    )

    val breatheOut: Map<Language, String> = mapOf(
        Language.English to "breathe out",
        Language.Chinese to "呼气",
        Language.Malay to "hembus nafas",
        Language.Japanese to "はいて",
        Language.Tagalog to "hingahan",
    )

    val allDone: Map<Language, String> = mapOf(
        Language.English to "all done",
        Language.Chinese to "结束了",
        Language.Malay to "sudah selesai",
        Language.Japanese to "おしまい",
        Language.Tagalog to "tapos na",
    )

    val again: Map<Language, String> = mapOf(
        Language.English to "again",
        Language.Chinese to "再来",
        Language.Malay to "sekali lagi",
        Language.Japanese to "もういちど",
        Language.Tagalog to "ulit",
    )

    val sound: Map<Language, String> = mapOf(
        Language.English to "soft sleep sounds",
        Language.Chinese to "轻柔助眠声",
        Language.Malay to "bunyi tidur lembut",
        Language.Japanese to "やさしい ねむりの おと",
        Language.Tagalog to "banayad na tunog sa pagtulog",
    )

    /** `%d` is the number of minutes. */
    val minutes: Map<Language, String> = mapOf(
        Language.English to "%d min",
        Language.Chinese to "%d 分钟",
        Language.Malay to "%d min",
        Language.Japanese to "%d ふん",
        Language.Tagalog to "%d min",
    )

    /** A missing row is a content bug, not a reason for a child to see a
     * crash — iOS `SleepyCloudView.text(_:)`'s own fallback, exactly. */
    fun text(table: Map<Language, String>, language: Language): String =
        table[language] ?: table[Language.English] ?: ""

    /**
     * [minutes] with the number filled in. Kept here rather than at the call
     * site so the one `%d` substitution in this screen has one home — and so
     * a template that lost its placeholder shows up in a JVM test rather
     * than on a child's screen.
     *
     * [Locale.ROOT] rather than the device default, deliberately: the
     * language on screen is the one the *parent chose in this app*, not the
     * one the phone is set to, and a device set to a locale with its own
     * digit forms would otherwise print "٢ min" next to English chrome.
     * iOS's `String(format:)` is locale-independent for `%d` and needs no
     * equivalent argument.
     */
    fun minutesLabel(choice: Int, language: Language): String =
        String.format(Locale.ROOT, text(minutes, language), choice)
}
