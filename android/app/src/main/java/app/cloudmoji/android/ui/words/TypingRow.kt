package app.cloudmoji.android.ui.words

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.TypedEmoji
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.Amber
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary

/** Every number the typing row is drawn from. Mirrors iOS `TypingRow.swift`'s
 * `TypingRowMetrics`. */
object TypingRowMetrics {
    /** The floor for anything a *child* taps. Not the 72dp preferred for the
     * grid — the row scrolls rather than shrinking these to fit. */
    val typedSide = 64.dp
    val controlSide = 64.dp
    val spacing = 8.dp
    val typedGlyphSize = 32.sp
    val replayGlyphSize = 24.sp
    val deleteGlyphSize = 22.sp
    val clearGlyphSize = 20.sp
    val controlCornerRadius = 12.dp
    val containerCornerRadius = 20.dp
    val horizontalPadding = 10.dp
    val verticalPadding = 7.dp
    val borderWidth = 1.dp
    const val controlPressedScale = 0.88f
    const val typedPressedScale = 0.9f
    val placeholderSize = 13.sp
    val minHeight = typedSide + verticalPadding * 2
}

/** Chrome, not content — lives beside the markup, like iOS/web, not in the
 * generated catalogue: there is nothing in `EmojiData.json` to generate it
 * from. Keep the five rows in sync with `TypingRow.swift`/`TypingRow.tsx` by
 * hand when either changes. */
private val placeholders = mapOf(
    Language.English to "Tap emojis below! 👇",
    Language.Chinese to "点击下面的表情 👇",
    Language.Malay to "Ketik emoji di bawah! 👇",
    Language.Japanese to "したの えもじを タップしてね 👇",
    Language.Tagalog to "Pindutin ang emoji sa ibaba! 👇",
)

/**
 * The strip of emojis the child has tapped, with replay, delete and clear.
 * Ported from iOS `TypingRow.swift`.
 *
 * The row **scrolls** instead of shrinking: when the typed emojis no longer
 * fit, the targets stay 64dp and slide out of view — a 40dp emoji a toddler
 * cannot hit is worse than one they have to scroll to. It scrolls to the
 * **newest** emoji on every tap, so what the child just typed is the thing
 * they can see.
 */
@Composable
fun TypingRow(
    typed: List<TypedEmoji>,
    muted: Boolean,
    language: Language,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onTapTyped: (TypedEmoji) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Keyed on the newest item's own identity, not `typed.size`: past the
    // 50-cap (`WordsViewModel.MAX_TYPED`) the size is pinned, so a
    // size-keyed effect would never restart and the emoji a child just
    // mashed past the cap would land off-screen — contradicting the "scrolls
    // to the newest emoji on every tap" promise below.
    LaunchedEffect(typed.lastOrNull()?.id) {
        if (typed.isNotEmpty()) listState.animateScrollToItem(typed.size - 1)
    }

    val shape = RoundedCornerShape(TypingRowMetrics.containerCornerRadius)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TypingRowMetrics.spacing),
        modifier = modifier
            .heightIn(min = TypingRowMetrics.minHeight)
            .clip(shape)
            .background(Surface, shape)
            .border(TypingRowMetrics.borderWidth, SurfaceBorder, shape)
            .padding(horizontal = TypingRowMetrics.horizontalPadding, vertical = TypingRowMetrics.verticalPadding)
            .testTag("typing-row"),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (typed.isEmpty()) {
                Text(
                    text = placeholders[language] ?: placeholders.getValue(Language.English),
                    color = TextSecondary,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = TypingRowMetrics.placeholderSize,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(TypingRowMetrics.spacing),
                ) {
                    items(typed, key = { it.id }) { item ->
                        TypedEmojiButton(item = item, onTap = { onTapTyped(item) })
                    }
                }
            }
        }

        if (typed.isNotEmpty()) {
            // Nothing to replay when the app is muted, and a button that
            // does nothing is a failure state — the one thing this app must
            // not have.
            if (!muted) {
                TypingRowControl(
                    glyph = "🔊",
                    label = "Replay",
                    identifier = "replay-btn",
                    tint = Teal,
                    glyphSize = TypingRowMetrics.replayGlyphSize,
                    action = onReplay,
                )
            }
            TypingRowControl(
                glyph = "⌫",
                label = "Delete last",
                identifier = "delete-btn",
                tint = Amber,
                glyphSize = TypingRowMetrics.deleteGlyphSize,
                action = onDelete,
            )
            TypingRowControl(
                glyph = "✕",
                label = "Clear all",
                identifier = "clear-btn",
                tint = Coral,
                glyphSize = TypingRowMetrics.clearGlyphSize,
                action = onClear,
            )
        }
    }
}

/** One typed emoji. Tapping it speaks the word again.
 *
 * The `testTag` is per-item (`item.id`, the same identity `LazyRow`'s own
 * `key` uses) rather than a single shared `"typed-emoji"` string: the same
 * glyph can be typed more than once, and a shared tag would make
 * `onNodeWithTag` match an arbitrary one of several on-screen nodes instead
 * of the specific one a test means to act on. */
@Composable
private fun TypedEmojiButton(item: TypedEmoji, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TypingRowMetrics.typedSide)
            .pressScale(interactionSource, TypingRowMetrics.typedPressedScale)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onTap)
            .semantics {
                role = Role.Button
                contentDescription = item.word
            }
            .testTag("typed-emoji-${item.id}"),
    ) {
        Text(text = item.emoji, fontSize = TypingRowMetrics.typedGlyphSize)
    }
}

/** Replay, delete or clear. Child-facing, so 64dp — not the 44dp parent-chrome floor. */
@Composable
private fun TypingRowControl(
    glyph: String,
    label: String,
    identifier: String,
    tint: androidx.compose.ui.graphics.Color,
    glyphSize: TextUnit,
    action: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(TypingRowMetrics.controlCornerRadius)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TypingRowMetrics.controlSide)
            .pressScale(interactionSource, TypingRowMetrics.controlPressedScale)
            .clip(shape)
            .background(tint.copy(alpha = 0.2f), shape)
            .border(TypingRowMetrics.borderWidth, tint.copy(alpha = 0.3f), shape)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = action)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag(identifier),
    ) {
        Text(text = glyph, color = TextPrimary, fontSize = glyphSize)
    }
}
