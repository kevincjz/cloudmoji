package app.cloudmoji.android.ui.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.ToneBuffer
import app.cloudmoji.android.platform.ToneDirector
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.MiniAppScaffold
import app.cloudmoji.android.ui.theme.Amber
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.Lavender
import app.cloudmoji.android.ui.theme.Moonlight
import app.cloudmoji.android.ui.theme.Teal

/**
 * Every number Music's pad grid is drawn from. Mirrors iOS
 * `InstrumentPadView.swift`'s `InstrumentPadMetrics`, pt for dp.
 */
object InstrumentPadMetrics {
    /**
     * The preferred child-facing size — and, unlike most of this app's other
     * child targets, also the hard floor. iOS's own `InstrumentPadView.side`
     * never lets a pad shrink below this, choosing to overflow its grid by a
     * couple of points on the very shortest screen instead of shrinking a
     * pad under it. A pad is struck rather than aimed at, often with a whole
     * hand, so it gets the stricter of the two numbers `CLAUDE.md` rule 1
     * allows (64dp minimum, 72dp preferred).
     */
    val minimumSide: Dp = 72.dp

    /** Full-screen tablets have room for enormous pads, but past this point
     * the notes stop reading as a set and start reading as separate panels. */
    val maximumPadSide: Dp = 260.dp

    /** `CLAUDE.md` rule 2, the floor between adjacent child-facing targets. */
    val spacing: Dp = 8.dp

    val cornerRadius: Dp = 20.dp
    val borderWidth: Dp = 2.dp

    /** Design system Active States: emoji tiles `scale(0.85)`. A pad is the
     * most tile-like thing on this screen and takes the same number. */
    const val pressedScale: Float = 0.85f

    /** The eight colours, one per pad, so a child can aim at "the red one".
     * Brand hues first, then the two Sleepy Cloud tints, then round again —
     * deliberately not eight invented colours. Mirrors iOS
     * `InstrumentPadMetrics.tints` exactly. */
    val tints: List<Color> = listOf(Coral, Teal, Gold, Amber, Moonlight, Lavender, Coral, Teal)

    fun tint(index: Int): Color = if (tints.isEmpty()) Teal else tints[index % tints.size]

    /** Four across compact or expanded-landscape, two across otherwise — the
     * transpose of each other, so the same eight pads fill whichever axis
     * there is more of. Mirrors iOS `InstrumentPadView.columns(compact:expandedPad:landscape:)`. */
    fun columns(compact: Boolean, isExpandedPad: Boolean = false, isLandscape: Boolean = false): Int =
        if (compact || (isExpandedPad && isLandscape)) 4 else 2

    /**
     * The largest square that fits the grid, never smaller than
     * [minimumSide]. Mirrors iOS
     * `InstrumentPadView.side(available:columns:rows:spacing:)`.
     *
     * Degenerate `columns`/`rows` (zero or negative — cannot happen from a
     * real layout, since there are always eight pitches, but must not divide
     * by zero if it ever did) returns [minimumSide] rather than trapping.
     */
    fun side(availableWidth: Dp, availableHeight: Dp, columns: Int, rows: Int, spacing: Dp = this.spacing): Dp {
        if (columns <= 0 || rows <= 0) return minimumSide
        val width = (availableWidth - spacing * (columns - 1)) / columns
        val height = (availableHeight - spacing * (rows - 1)) / rows
        val fitted = if (width < height) width else height
        return if (fitted > minimumSide) fitted else minimumSide
    }
}

/**
 * Music 🎹 — eight pads, one note each. Ported from iOS
 * `InstrumentPadView.swift`.
 *
 * The simplest possible instrument, and deliberately so: no scales to
 * choose, no octave control, no recording. Eight coloured squares that make
 * a sound when touched, sized so a two-year-old can hit them with the flat
 * of his hand. The notes are a C-major pentatonic run — see
 * [ToneBuffer.pitches] — which is what makes every combination sound
 * intentional.
 *
 * Silent in one respect only, same as iOS: it never speaks. There is no word
 * to say about a note. [muted] silences the tone; it does not mean "stop
 * responding to me" — the haptic and the colour still answer a tap on a
 * muted phone, the rule [HapticFeedback]'s own doc states.
 *
 * No [app.cloudmoji.android.ui.common.ModeHeader] here, matching iOS
 * `InstrumentPadView` exactly: no mascot, no language control, no mute
 * toggle of its own — just the pads and [MiniAppScaffold]'s cloud home
 * button. Since this screen has no mute control of its own, muting the
 * phone from another screen would otherwise be a silent dead end here — no
 * failure states, `CLAUDE.md` rule 4 — so [MiniAppScaffold] is handed
 * [MiniApp.Music]'s `showsSoundRecovery` flag, [muted], and [onUnmute],
 * which together draw the same recovery button iOS's `HostedMiniApp` shows
 * for `.instrument` (`MiniApp.swift`'s `showsSoundRecovery`,
 * `ContentView.swift`'s `SoundRecoveryButton`). (iOS also paints a themed
 * backdrop behind the pads; that part is not ported — out of this task's
 * scope, see the Task 10 report and its fix addendum.)
 */
@Composable
fun MusicScreen(
    muted: Boolean,
    toneDirector: ToneDirector,
    hapticFeedback: HapticFeedback,
    onHome: () -> Unit,
    onUnmute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current

    // Belt and braces with `goHome`, the same reasoning iOS's own
    // `.onAppear`/`.onDisappear` doc gives: `onDispose` is the one that
    // fires when the app is torn down some other way, and a running engine
    // outliving the screen is what makes a phone hum.
    DisposableEffect(toneDirector) {
        toneDirector.attach()
        onDispose { toneDirector.detach() }
    }

    fun strike(index: Int) {
        hapticFeedback.tap()
        // Muting silences the phone; it does not mean "stop responding to
        // me". The haptic and the colour still answer — the pad itself has
        // already fired its own visual feedback by the time this runs.
        if (muted) return
        toneDirector.playTone(index)
    }

    MiniAppScaffold(
        onHome = onHome,
        homeAccent = Coral,
        screenTag = "music-screen",
        showsSoundRecovery = MiniApp.Music.showsSoundRecovery,
        muted = muted,
        onUnmute = onUnmute,
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(InstrumentPadMetrics.spacing),
            contentAlignment = Alignment.Center,
        ) {
            val columns = InstrumentPadMetrics.columns(
                compact = layout.isCompactPhone,
                isExpandedPad = layout.isExpandedPad,
                isLandscape = layout.isLandscape,
            )
            val pitchCount = ToneBuffer.pitches.size
            val rows = (pitchCount + columns - 1) / columns
            val fitted = InstrumentPadMetrics.side(
                availableWidth = maxWidth,
                availableHeight = maxHeight,
                columns = columns,
                rows = rows,
            )
            val side = if (layout.isExpandedPad && fitted > InstrumentPadMetrics.maximumPadSide) {
                InstrumentPadMetrics.maximumPadSide
            } else {
                fitted
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(InstrumentPadMetrics.spacing),
                modifier = Modifier.testTag("instrument-panel"),
            ) {
                for (row in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(InstrumentPadMetrics.spacing)) {
                        for (column in 0 until columns) {
                            val index = row * columns + column
                            if (index < pitchCount) {
                                InstrumentPad(index = index, side = side, onStrike = { strike(index) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One key. Ported from iOS `InstrumentPad`.
 *
 * **It sounds on touch-down, not touch-up** — a custom [pointerInput]
 * gesture rather than `Modifier.clickable`, matching iOS's choice of
 * `DragGesture(minimumDistance: 0)` over `Button` exactly, and for the same
 * reason: an instrument that waits for the finger to lift does not read as
 * an instrument to a toddler, it reads as broken, and he stops trying. A
 * finger dragged off the pad after landing still counts, the same as a real
 * keyboard — the gesture below latches on the first `down` and does not fire
 * again until the pointer is fully released, mirroring iOS's `hasStruck`
 * latch (which exists so a held finger cannot machine-gun the note).
 *
 * TalkBack does not get the raw touch gesture above: [onStrike] is also
 * wired through the `onClick` semantics action, so a double-tap activates
 * the pad the same way any other button in this app does, without a second,
 * competing gesture detector layered on top of the custom one.
 *
 * The [pointerInput] gesture is keyed on `Unit`, not [onStrike]: [onStrike]
 * is a fresh lambda every time the caller recomposes (it closes over
 * `muted`, which can change while the screen is open), and keying on it
 * would restart the gesture coroutine on every such recomposition —
 * cancelling a finger that is mid-press and either losing the strike or
 * stranding [isDown]. [rememberUpdatedState] is the fix Compose itself
 * recommends for exactly this shape: the coroutine is installed once and
 * always reads the *latest* [onStrike] at the moment a finger actually
 * lands, without needing to restart.
 */
@Composable
fun InstrumentPad(
    index: Int,
    side: Dp,
    onStrike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDown by remember { mutableStateOf(false) }
    val currentOnStrike by rememberUpdatedState(onStrike)
    val tint = InstrumentPadMetrics.tint(index)
    val shape = RoundedCornerShape(InstrumentPadMetrics.cornerRadius)

    val scale by animateFloatAsState(
        targetValue = if (isDown) InstrumentPadMetrics.pressedScale else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "instrumentPadScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(side)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = if (isDown) 0.96f else 0.72f),
                        tint.copy(alpha = if (isDown) 0.62f else 0.34f),
                        BackgroundPrimary.copy(alpha = 0.92f),
                    ),
                ),
                shape,
            )
            .border(
                InstrumentPadMetrics.borderWidth,
                Color.White.copy(alpha = if (isDown) 0.50f else 0.20f),
                shape,
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isDown = true
                    currentOnStrike()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    isDown = false
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "Note ${index + 1}"
                onClick(label = null) { currentOnStrike(); true }
            }
            .testTag("pad-$index"),
    ) {
        Text(
            text = "${index + 1}",
            color = Color.White.copy(alpha = 0.88f),
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
        )
    }
}
