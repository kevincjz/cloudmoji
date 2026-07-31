package app.cloudmoji.android.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The `:active` press transform every tappable element in the app is
 * required to have — "one tap = one action = one reward", `CLAUDE.md` rule 3.
 * Ported from iOS `PressScale.swift`'s `ButtonStyle`.
 *
 * The caller owns [interactionSource] and feeds the same instance to
 * `Modifier.clickable(interactionSource = ..., indication = null)`, so this
 * modifier only ever reacts to presses that button itself recognised — it
 * *is* the indication, replacing the platform ripple the design has no room
 * for on a 64–84dp child-facing target.
 *
 * [scale] has no default: every call site should have looked its value up in
 * `docs/design/DESIGN_SYSTEM.md`'s Active States table (emoji tiles 0.85,
 * category/rail chips 0.9, control buttons 0.88, typed emojis 0.9, the home
 * button 0.9), and a default invites a fifth number nobody chose.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    scale: Float,
    durationMillis: Int = 100,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val animatedScale by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        // CSS `ease` is cubic-bezier(0.25, 0.1, 0.25, 1); `tween`'s default
        // easing is close enough for a 100ms micro-interaction nobody times
        // with a stopwatch.
        animationSpec = tween(durationMillis = durationMillis),
        label = "pressScale",
    )
    Modifier.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}
