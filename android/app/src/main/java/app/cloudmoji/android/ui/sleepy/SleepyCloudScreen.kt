package app.cloudmoji.android.ui.sleepy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.cloudmoji.android.model.BreathPhase
import app.cloudmoji.android.model.BreathState
import app.cloudmoji.android.model.BreathingSession
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.model.SleepySessionState
import app.cloudmoji.android.platform.BRIGHTNESS_FOLLOWS_SYSTEM
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.ScreenAwake
import app.cloudmoji.android.platform.ScreenDimmer
import app.cloudmoji.android.platform.ToneDirector
import app.cloudmoji.android.platform.findActivity
import app.cloudmoji.android.platform.setKeepScreenOn
import app.cloudmoji.android.platform.setWindowBrightness
import app.cloudmoji.android.platform.systemBrightnessFraction
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.MiniAppScaffold
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Lavender
import app.cloudmoji.android.ui.theme.Moonlight
import app.cloudmoji.android.ui.theme.TextSecondary
import app.cloudmoji.android.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Sleepy Cloud 🌙 — the wind-down. Ported from iOS
 * `Views/Sleepy/SleepyCloudView.swift`.
 *
 * A child or grown-up picks two, five or ten minutes; the cloud breathes and
 * the child breathes with it; the room gets darker; at the end the cloud
 * falls asleep and everything stops.
 *
 * **It never speaks.** A voice is the opposite of what a bedtime routine
 * needs. Nothing here touches
 * [app.cloudmoji.android.platform.SpeechController]. Instead, an
 * intentionally quiet synthesized rain/ocean wash begins with the session and
 * stops whenever the session pauses, finishes, or leaves the screen — routed
 * through the *same* [ToneDirector] and [android.media.AudioTrack] graph
 * Music uses (see [ToneDirector]'s own doc), not a parallel audio stack.
 *
 * ### The three lifetimes on this screen
 * 1. **The composition** — bracketed by [MiniAppScaffold] and this function.
 * 2. **A session** ([SleepySessionState.isRunning]) — from a duration tap to
 *    the cloud falling asleep or the child leaving. The picker holds nothing:
 *    no engine, no screen flag, no brightness override.
 * 3. **Foreground** — Android's `ON_RESUME`/`ON_PAUSE`, the analogue of iOS's
 *    `scenePhase == .active`.
 *
 * Everything the screen *takes* — the keep-screen-on flag, the window
 * brightness override, the audio engine and its focus — is taken only while
 * all three hold at once, and given back the instant any one of them stops,
 * by a single `DisposableEffect`'s `onDispose`. That is the Compose shape of
 * iOS's `resume`/`pause`/`yieldTheScreen` trio, and it closes the same bug
 * iOS's own doc records: a session interrupted by a notification and returned
 * to must re-take what it gave back, not run on to its end with the screen
 * timeout switched back on.
 *
 * The one deliberate difference from iOS is what happens to the *clock* while
 * backgrounded. iOS pauses its dim loop; so does this, since the loop is
 * cancelled with the effect — but neither platform pauses the session, because
 * [SleepySessionState] measures forward from a start instant rather than
 * accumulating. Ten minutes is ten minutes of wall clock whether or not a
 * notification interrupted it, which is the behaviour a parent expects from a
 * bedtime timer.
 */
@Composable
fun SleepyCloudScreen(
    session: SleepySessionState,
    language: Language,
    muted: Boolean,
    toneDirector: ToneDirector,
    hapticFeedback: HapticFeedback,
    onHome: () -> Unit,
    onUnmute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // The screen's two owners, built once per entry. Both take injected
    // read/write closures so `ScreenControlTest` can prove hold-and-release
    // on the JVM — see `platform/ScreenControl.kt`.
    val awake = remember(activity) {
        ScreenAwake(write = { on -> activity?.let { setKeepScreenOn(it, on) } })
    }
    val dimmer = remember(activity) {
        // Read once, at entry, rather than on every dim: this is the
        // brightness the parent had set when they opened the screen, and a
        // `ContentResolver` lookup once a second for a value that does not
        // move is work for nothing.
        val base = activity?.let { systemBrightnessFraction(it) } ?: 1f
        ScreenDimmer(
            read = { base },
            write = { value ->
                activity?.let {
                    // At `progress == 0` and again on `restore()`,
                    // `ScreenDimmer` writes exactly `base` back — and the
                    // honest way to say "this window wants the brightness the
                    // system says" on Android is to drop the override, not to
                    // pin the same number. See [BRIGHTNESS_FOLLOWS_SYSTEM].
                    setWindowBrightness(it, if (value >= base) BRIGHTNESS_FOLLOWS_SYSTEM else value)
                }
            },
        )
    }

    val minutes by session.minutes.collectAsState()
    val isAsleep by session.isAsleep.collectAsState()
    val progress by session.progress.collectAsState()

    val isForeground = rememberIsForeground(activity as? LifecycleOwner)
    val isRunning = minutes != null && !isAsleep
    val isActive = isRunning && isForeground

    // **The one place the screen is taken, and the one place it is handed
    // back.** `onDispose` fires on all three of iOS's exits at once: the end
    // of a session and a child leaving both flip `isActive` (the key), and
    // leaving the mini-app disposes the effect outright. Nothing else in this
    // file calls `release`/`restore`/`detach`, so there is no fourth path to
    // get wrong.
    DisposableEffect(isActive) {
        if (isActive) {
            awake.hold()
            toneDirector.attach()
        }
        onDispose {
            toneDirector.stopSleepNoise()
            toneDirector.detach()
            awake.release()
            dimmer.restore()
        }
    }

    // Muting mid-session silences the wash without ending the session or
    // giving up the engine — iOS `SleepyCloudView`'s own
    // `.onChange(of: model.settings.muted)`. Keyed on `muted` as well as
    // `isActive` so an unmute brings it straight back.
    LaunchedEffect(isActive, muted) {
        if (!isActive) return@LaunchedEffect
        if (muted) toneDirector.stopSleepNoise() else toneDirector.playSleepNoise()
    }

    // The dim loop. iOS `runDimLoop`, one iteration at a time: step the
    // progress off the wall clock, take the screen down to match, and stop
    // when the clock runs out — at which point `tick()` flips `isAsleep`,
    // `isActive` goes false, and the effect above hands everything back.
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            val reachedTheEnd = session.tick()
            dimmer.dim(session.progress.value)
            if (reachedTheEnd) break
            delay(SleepyCloudMetrics.TICK_MILLIS)
        }
    }

    val dim = SleepyCloudMetrics.dim(progress)
    // iOS `.animation(.linear(duration: 1.2), value: dim)`: the dim steps
    // once a second and is eased across the gap, so nothing ever visibly
    // jumps.
    val animatedDim by animateFloatAsState(
        targetValue = dim.toFloat(),
        animationSpec = tween(
            durationMillis = SleepyCloudMetrics.DIM_ANIMATION_MILLIS,
            easing = LinearEasing,
        ),
        label = "sleepyDim",
    )

    MiniAppScaffold(
        onHome = onHome,
        homeAccent = Moonlight,
        screenTag = "sleepy-screen",
        showsSoundRecovery = MiniApp.Sleepy.showsSoundRecovery,
        muted = muted,
        onUnmute = onUnmute,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize().testTag("sleepy-panel")) {
            MoonHalo(dim = animatedDim, isExpandedPad = layout.isExpandedPad, isCompactPhone = layout.isCompactPhone)
            Starfield(dim = animatedDim)

            if (minutes == null) {
                DurationPicker(
                    language = language,
                    muted = muted,
                    onPick = { choice ->
                        hapticFeedback.tap()
                        session.begin(choice)
                    },
                )
            } else {
                RunningSession(
                    session = session,
                    language = language,
                    isAsleep = isAsleep,
                    dim = animatedDim,
                    onAgain = {
                        hapticFeedback.tap()
                        session.reset()
                    },
                )
            }

            // The deepening dark. Above the stars and the cloud, below
            // nothing — and explicitly *not* clickable, so it never swallows
            // a tap meant for a duration button, the "again" button, or the
            // cloud home button underneath. A `Box` with no pointer input of
            // its own does not consume touches in Compose; iOS needs
            // `.allowsHitTesting(false)` to get the same property.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = animatedDim * SleepyCloudMetrics.SCRIM_SHARE))
                    .clearAndSetSemantics {},
            )

            if (minutes != null && !isAsleep) {
                ProgressLine(
                    progress = progress.toFloat(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Whether the hosting Activity is currently the resumed, interactive one —
 * the Android analogue of iOS's `scenePhase == .active`, which
 * `SleepyCloudScreen` reads to decide whether a session should be holding
 * the screen and the speaker.
 *
 * Observes the [Activity][android.app.Activity]'s own lifecycle rather than
 * a `LocalLifecycleOwner`: `androidx.activity.ComponentActivity` *is* a
 * [LifecycleOwner], so `androidx.lifecycle` is already a first-class
 * dependency of this app through the Activity it launches, and reaching it
 * this way adds nothing to the build. A `null` owner (a preview, or a future
 * non-Activity host) reports a permanently-foreground screen, which is the
 * safe answer: a preview that never dims is better than one that never
 * breathes.
 */
@Composable
private fun rememberIsForeground(owner: LifecycleOwner?): Boolean {
    var isForeground by remember(owner) {
        mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true)
    }

    DisposableEffect(owner) {
        val lifecycle = owner?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isForeground = true
                Lifecycle.Event.ON_PAUSE -> isForeground = false
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    return isForeground
}

/**
 * The duration picker — a still cloud, the name of the thing, and three
 * plates. Ported from iOS `SleepyCloudView.picker`.
 *
 * The cloud here is drawn at [BreathPhase.Hold] and does not move: the
 * picker is the moment before the wind-down, and a cloud already breathing
 * at a child who has not chosen anything is an invitation to watch rather
 * than to pick.
 */
@Composable
private fun DurationPicker(
    language: Language,
    muted: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val isPad = layout.isExpandedPad
    val spacing = if (isPad) 34.dp else if (layout.isCompactPhone) 12.dp else 26.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (isPad) 34.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
    ) {
        BreathingCloud(
            scale = 0.85,
            phase = BreathPhase.Hold,
            width = SleepyCloudMetrics.cloudWidth(isPad, layout.isCompactPhone),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isPad) 5.dp else 2.dp),
        ) {
            Text(
                text = SleepyUiText.text(SleepyUiText.title, language),
                fontFamily = CloudmojiDisplayFont,
                fontSize = if (isPad) 36.sp else if (layout.isCompactPhone) 20.sp else 26.sp,
                textAlign = TextAlign.Center,
                // iOS paints the title with a moonlight-to-lavender gradient;
                // Compose can do the same with `Brush.linearGradient` on a
                // `TextStyle`, but at this size the two stops read as one
                // colour anyway, so the nearer stop alone is used — the same
                // simplification `ModeHeader` already makes.
                color = Moonlight,
            )
            Text(
                text = SleepyUiText.text(SleepyUiText.subtitle, language),
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isPad) 16.sp else 12.sp,
                textAlign = TextAlign.Center,
                color = TextSecondary,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                if (isPad) SleepyCloudMetrics.padChoiceSpacing else SleepyCloudMetrics.choiceSpacing,
            ),
        ) {
            for (choice in BreathingSession.CHOICES) {
                DurationButton(choice = choice, language = language, onPick = onPick)
            }
        }

        Text(
            text = SleepyUiText.text(SleepyUiText.grownUp, language),
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = if (isPad) 14.sp else 11.sp,
            color = TextTertiary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isPad) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // iOS uses the `waveform` / `speaker.slash.fill` SF Symbols;
            // this app ships no icon set, and an emoji is what every other
            // Android screen here uses in their place.
            Text(text = if (muted) "🔇" else "🔊", fontSize = if (isPad) 16.sp else 12.sp)
            Text(
                text = SleepyUiText.text(SleepyUiText.sound, language),
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isPad) 14.sp else 11.sp,
                color = Moonlight.copy(alpha = 0.68f),
            )
        }
    }
}

/** One duration plate. Ported from iOS `SleepyCloudView.durationButton`. */
@Composable
private fun DurationButton(
    choice: Int,
    language: Language,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val isPad = layout.isExpandedPad
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SleepyCloudMetrics.choiceCornerRadius)
    val label = SleepyUiText.minutesLabel(choice, language)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        modifier = modifier
            .size(
                width = if (isPad) SleepyCloudMetrics.padChoiceWidth else SleepyCloudMetrics.choiceWidth,
                height = if (isPad) SleepyCloudMetrics.padChoiceHeight else SleepyCloudMetrics.choiceHeight,
            )
            .pressScale(interactionSource, SleepyCloudMetrics.PRESSED_SCALE)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Moonlight.copy(alpha = 0.14f), Lavender.copy(alpha = 0.08f)),
                ),
                shape,
            )
            .border(SleepyCloudMetrics.borderWidth, Moonlight.copy(alpha = 0.28f), shape)
            // The whole plate answers a tap, not just the label — without
            // this most of what a toddler aims at is dead, the same trap
            // iOS's own `.contentShape(Rectangle())` closes.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onPick(choice) },
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag("sleepy-duration-$choice"),
    ) {
        Text(text = "🌙", fontSize = if (isPad) 16.sp else 13.sp)
        Text(
            text = label,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = if (isPad) 18.sp else 15.sp,
            maxLines = 1,
            color = Moonlight,
        )
    }
}

/**
 * The running session. Ported from iOS `SleepyCloudView.session`.
 *
 * Its own composable specifically so the breath's frame loop below
 * invalidates *this* subtree and not the whole screen: [breath] is written
 * on every frame, and a read of it at `SleepyCloudScreen`'s own level would
 * recompose the scrim, the starfield and the scaffold sixty times a second
 * for a change only the cloud can see.
 *
 * `withFrameNanos` is the `TimelineView(.animation)` of the iOS original and
 * the `requestAnimationFrame` of the prototype: the breath is computed from
 * the wall clock on the display's own schedule, rather than accumulated by a
 * timer that drifts — which is what would make a ten-minute session end a
 * visible beat late. It also stops for free when the Activity stops
 * producing frames, so a backgrounded session costs nothing.
 */
@Composable
private fun RunningSession(
    session: SleepySessionState,
    language: Language,
    isAsleep: Boolean,
    dim: Float,
    onAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val isPad = layout.isExpandedPad
    val spacing = if (isPad) 38.dp else if (layout.isCompactPhone) 14.dp else 28.dp

    var breath by remember {
        mutableStateOf(BreathState(BreathingSession.REST_SCALE, BreathPhase.Inhale))
    }

    LaunchedEffect(session, isAsleep) {
        if (isAsleep) {
            breath = BreathState(BreathingSession.ASLEEP_SCALE, BreathPhase.Asleep)
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            breath = session.breathState()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
    ) {
        BreathingCloud(
            scale = breath.scale,
            phase = breath.phase,
            dim = dim.toDouble(),
            width = SleepyCloudMetrics.cloudWidth(isPad, layout.isCompactPhone),
        )

        if (isAsleep) {
            Finished(language = language, onAgain = onAgain)
        } else {
            Text(
                text = phaseLabel(breath.phase, language),
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isPad) 19.sp else 15.sp,
                letterSpacing = 1.4.sp,
                color = Moonlight.copy(alpha = (0.5f - dim * 0.4f).coerceIn(0f, 1f)),
                modifier = Modifier
                    // A fixed height, so the cloud does not step up and down
                    // the screen when the hold's blank label arrives.
                    .height(20.dp)
                    .testTag("sleepy-phase"),
            )
        }
    }
}

/**
 * The hold has no label on purpose: a word arriving in the pause between
 * breathing in and breathing out is an instruction to do something, and
 * there is nothing to do. Ported from iOS `SleepyCloudView.label(for:)`.
 */
internal fun phaseLabel(phase: BreathPhase, language: Language): String = when (phase) {
    BreathPhase.Inhale -> SleepyUiText.text(SleepyUiText.breatheIn, language)
    BreathPhase.Exhale -> SleepyUiText.text(SleepyUiText.breatheOut, language)
    BreathPhase.Hold, BreathPhase.Asleep -> ""
}

/** The end of a session. Ported from iOS `SleepyCloudView.finished`. */
@Composable
private fun Finished(language: Language, onAgain: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val againLabel = SleepyUiText.text(SleepyUiText.again, language)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = SleepyUiText.text(SleepyUiText.allDone, language),
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            letterSpacing = 1.sp,
            color = Moonlight.copy(alpha = 0.45f),
            modifier = Modifier.testTag("sleepy-done"),
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = SleepyCloudMetrics.againWidth, height = SleepyCloudMetrics.againHeight)
                .pressScale(interactionSource, SleepyCloudMetrics.PRESSED_SCALE)
                .clip(CircleShape)
                .background(Moonlight.copy(alpha = 0.09f), CircleShape)
                .border(SleepyCloudMetrics.borderWidth, Moonlight.copy(alpha = 0.20f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onAgain,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = againLabel
                }
                .testTag("sleepy-again"),
        ) {
            Text(
                text = againLabel,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Moonlight.copy(alpha = 0.72f),
            )
        }
    }
}

/**
 * A quiet crescent high in the sky, so the picker reads as a full-screen
 * scene while the breathing cloud stays the only moving thing on it. Ported
 * from iOS `SleepyCloudView.moonHalo`.
 *
 * The halo is a radial gradient rather than iOS's blurred circle: a real
 * blur needs `RenderEffect` and API 31, and this app's `minSdk` is 26 — the
 * same constraint
 * [app.cloudmoji.android.ui.mascot.CloudMascot] already documents.
 */
@Composable
private fun MoonHalo(
    dim: Float,
    isExpandedPad: Boolean,
    isCompactPhone: Boolean,
    modifier: Modifier = Modifier,
) {
    val haloRadiusDp = if (isExpandedPad) 118f else 78f
    val glyphSize = if (isExpandedPad) 122.sp else 82.sp
    val xFraction = if (isExpandedPad) 0.82f else if (isCompactPhone) 0.83f else 0.78f
    val yFraction = if (isExpandedPad) 0.15f else if (isCompactPhone) 0.24f else 0.16f
    val skyAlpha = (1f - dim * 0.8f).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centre = Offset(size.width * xFraction, size.height * yFraction)
            val radius = haloRadiusDp * density
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Moonlight.copy(alpha = 0.10f * skyAlpha),
                        Moonlight.copy(alpha = 0f),
                    ),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }
        Text(
            text = "🌙",
            fontSize = glyphSize,
            // `Modifier.alpha`, not a translucent text colour: the moon is an
            // emoji, and a colour-font glyph paints its own colours — the
            // `color` argument would be ignored and the moon would never fade
            // as the room darkens.
            modifier = Modifier
                .align(Alignment.TopStart)
                .offsetByFraction(xFraction, yFraction)
                .alpha(0.34f * skyAlpha),
        )
    }
}

/**
 * Fourteen faint stars. Ported from iOS `SleepyCloudView.starfield`.
 *
 * One `Canvas` and one shared clock rather than fourteen animated
 * composables — see [SleepyCloudMetrics.starTwinkleAlpha] for why, and for
 * how each star keeps its own period and delay regardless.
 */
@Composable
private fun Starfield(dim: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "starfield")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SleepyCloudMetrics.TWINKLE_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "twinkle",
    )
    val skyAlpha = (1f - dim).coerceIn(0f, 1f)

    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics {}) {
        for (index in 0 until SleepyCloudMetrics.STAR_COUNT) {
            drawCircle(
                color = Moonlight,
                radius = SleepyCloudMetrics.STAR_DIAMETER * density / 2f,
                center = Offset(
                    x = size.width * SleepyCloudMetrics.starXFraction(index),
                    y = size.height * SleepyCloudMetrics.starYFraction(index),
                ),
                alpha = SleepyCloudMetrics.starTwinkleAlpha(index, clock) * skyAlpha,
            )
        }
    }
}

/** How far through, along the bottom edge. Ported from iOS
 * `SleepyCloudView.progressLine`. */
@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SleepyCloudMetrics.progressLineHeight)
            .background(Color.White.copy(alpha = 0.04f))
            .clearAndSetSemantics {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(SleepyCloudMetrics.progressLineHeight)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Moonlight.copy(alpha = SleepyCloudMetrics.PROGRESS_LINE_ALPHA),
                            Lavender.copy(alpha = SleepyCloudMetrics.PROGRESS_LINE_ALPHA),
                        ),
                    ),
                )
                .testTag("sleepy-progress"),
        )
    }
}

/**
 * Positions a child at a fraction of its parent's size, the way SwiftUI's
 * `.position(x:y:)` does — Compose has no direct equivalent, and
 * `Modifier.offset` alone takes absolute `Dp`. The child is centred on the
 * point, matching SwiftUI.
 */
private fun Modifier.offsetByFraction(xFraction: Float, yFraction: Float): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                x = (constraints.maxWidth * xFraction - placeable.width / 2f).toInt(),
                y = (constraints.maxHeight * yFraction - placeable.height / 2f).toInt(),
            )
        }
    }
