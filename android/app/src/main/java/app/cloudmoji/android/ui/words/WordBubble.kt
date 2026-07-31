package app.cloudmoji.android.ui.words

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Every number the bubble is drawn and timed from. Mirrors iOS
 * `WordBubble.swift`'s `WordBubbleMetrics` — the durations are the
 * `wordFloat` keyframe percentages resolved against [lifetimeMs], kept as the
 * source so the fade-out always finishes exactly as the owner removes the
 * bubble rather than being cut into a jump cut.
 */
object WordBubbleMetrics {
    /** `wordFloat 2.2s`, and `WordsViewModel.BUBBLE_HOLD_MS` — the same
     * number on both sides of the show/hide boundary. */
    const val lifetimeMs = 2200
    val arriveAtMs = (0.15 * lifetimeMs).toInt() // 330
    val settleAtMs = (0.25 * lifetimeMs).toInt() // 550
    val fadeFromMs = (0.78 * lifetimeMs).toInt() // 1716

    val fontSize = 18.sp
    val emojiSize = 22.sp
    val cornerRadius = 18.dp
    val horizontalPadding = 18.dp
    val verticalPadding = 5.dp
    val spacing = 6.dp
    val borderWidth = 1.dp
}

/**
 * The floating label showing the word being spoken. Rises, holds and fades
 * over [WordBubbleMetrics.lifetimeMs] — the owner ([app.cloudmoji.android.model.WordsViewModel])
 * is expected to drop it after the same span. Ported from iOS `WordBubble.swift`.
 *
 * [id] is the typed item's own identity, not its content: a repeat of the
 * exact same word must replay the float from the bottom rather than sit
 * there, which content alone cannot distinguish (the same emoji tapped twice
 * in a row is the same `(emoji, word)` pair).
 *
 * Decoration, never a control — it carries no `clickable`, so a tap aimed at
 * whatever is underneath (the emoji grid in landscape, where this floats as
 * an overlay) passes straight through, the same as iOS's `allowsHitTesting(false)`.
 */
@Composable
fun WordBubble(emoji: String, word: String, id: Long, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.7f) }
    val offsetY = remember { Animatable(12f) }

    LaunchedEffect(id) {
        alpha.snapTo(0f)
        scale.snapTo(0.7f)
        offsetY.snapTo(12f)

        // 0 -> arriveAt: opacity 0 -> 1, scale 0.7 -> 1.06 (the overshoot), offsetY 12 -> 0.
        launch { alpha.animateTo(1f, tween(WordBubbleMetrics.arriveAtMs, easing = LinearOutSlowInEasing)) }
        launch { scale.animateTo(1.06f, tween(WordBubbleMetrics.arriveAtMs)) }
        offsetY.animateTo(0f, tween(WordBubbleMetrics.arriveAtMs))

        // arriveAt -> settleAt: scale 1.06 -> 1 (settled, held from here).
        scale.animateTo(1f, tween(WordBubbleMetrics.settleAtMs - WordBubbleMetrics.arriveAtMs))

        // Held until fadeFrom.
        delay((WordBubbleMetrics.fadeFromMs - WordBubbleMetrics.settleAtMs).toLong())

        // fadeFrom -> lifetime: opacity 1 -> 0, offsetY 0 -> -10, scale 1 -> 0.95.
        launch { alpha.animateTo(0f, tween(WordBubbleMetrics.lifetimeMs - WordBubbleMetrics.fadeFromMs)) }
        launch { scale.animateTo(0.95f, tween(WordBubbleMetrics.lifetimeMs - WordBubbleMetrics.fadeFromMs)) }
        offsetY.animateTo(-10f, tween(WordBubbleMetrics.lifetimeMs - WordBubbleMetrics.fadeFromMs))
    }

    val shape = RoundedCornerShape(WordBubbleMetrics.cornerRadius)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WordBubbleMetrics.spacing),
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
                translationY = offsetY.value.dp.toPx()
            }
            .clip(shape)
            .background(
                // Coral into teal — the app's two brand colours meeting. A
                // neutral material here would read as a system alert rather
                // than as the app answering the child.
                Brush.linearGradient(listOf(Coral.copy(alpha = 0.2f), Teal.copy(alpha = 0.2f))),
            )
            .border(WordBubbleMetrics.borderWidth, Color.White.copy(alpha = 0.1f), shape)
            .padding(horizontal = WordBubbleMetrics.horizontalPadding, vertical = WordBubbleMetrics.verticalPadding)
            // It is a report on what just happened, not something to
            // navigate by — the child is already being told out loud.
            .clearAndSetSemantics {}
            .testTag("word-bubble"),
    ) {
        Text(text = emoji, fontSize = WordBubbleMetrics.emojiSize)
        Text(
            text = word,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = WordBubbleMetrics.fontSize,
            letterSpacing = 0.5.sp,
        )
    }
}
