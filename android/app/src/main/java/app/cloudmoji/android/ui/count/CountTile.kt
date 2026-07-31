package app.cloudmoji.android.ui.count

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TealDeep
import app.cloudmoji.android.ui.theme.TextPrimary

/**
 * Every number Count mode's grid is drawn from. Mirrors iOS `CountTile.swift`'s
 * `CountTileMetrics`, dp for pt.
 *
 * The sizes are step functions of the round size because a round of nine
 * 96dp tiles does not fit on a phone — and the steps stop at [childMinimum],
 * because shrinking a child's target below the floor to make a layout fit is
 * the one trade this project does not make.
 */
object CountTileMetrics {
    /** The floor. Named rather than inlined so the tests can say what they
     * are enforcing — a count tile is child-facing, and 72dp is *preferred*,
     * not required. */
    val childMinimum: Dp = 64.dp

    fun side(count: Int, compact: Boolean): Dp = when {
        compact -> if (count <= 5) 72.dp else 64.dp
        count <= 3 -> 96.dp
        count <= 6 -> 82.dp
        else -> 72.dp
    }

    fun glyphSize(count: Int, compact: Boolean): TextUnit = when {
        compact -> if (count <= 5) 44.sp else 36.sp
        count <= 3 -> 64.sp
        count <= 6 -> 54.sp
        else -> 46.sp
    }

    /** Three across upright, five sideways — and never more columns than
     * there are tiles, so a round of two centres under the readout instead
     * of hugging the left of a three-column track. */
    fun columns(count: Int, compact: Boolean): Int = minOf(count, if (compact) 5 else 3)

    /** Roomier for the small rounds, which have the space for it. Never
     * under the 8dp floor between two things a child taps. */
    fun gridSpacing(count: Int, compact: Boolean): Dp = when {
        compact -> 10.dp
        count <= 4 -> 16.dp
        else -> 12.dp
    }

    /** `max-width: 360` upright, `520` sideways — stops a round of two
     * spreading across a tablet. */
    fun maxGridWidth(compact: Boolean): Dp = if (compact) 520.dp else 360.dp

    val cornerRadius: Dp = 22.dp

    /** Heavier than a plate hairline because on a counted tile this border
     * is the state. */
    val borderWidth: Dp = 2.5.dp

    /** The order badge: a 34dp disc hung off the top-end corner. */
    val badgeSide: Dp = 34.dp

    /** How far it hangs outside the tile, on both axes. The grid pads by
     * this much so a top-row badge is not clipped. */
    val badgeOverhang: Dp = 10.dp
    val badgeGlyphSize: TextUnit = 19.sp
    val badgeBorderWidth: Dp = 2.5.dp

    /** Uncounted tiles sit back so the counted ones come forward. */
    const val uncountedOpacity: Float = 0.75f
    const val uncountedScale: Float = 0.95f

    /** Design system Active States: this is a tile, so `scale(0.85)` — the
     * same as an emoji tile, not the 0.88 of a control button. */
    const val pressedScale: Float = 0.85f

    /** `bounceEmoji 0.35s ease`, fired on the tile just counted. */
    const val bounceScale: Float = 1.15f

    /** `transition: all 0.25s ease` between the counted and uncounted looks
     * — also stands in for the bounce's own duration, a reasonable single
     * animation in place of iOS's two independently-keyed ones. */
    const val stateDurationMs: Int = 250
}

/**
 * One of the N identical things on screen. Ported from iOS `CountTile.swift`.
 *
 * It knows nothing about the round: it is handed a badge number or `null`,
 * and a size. That is what lets it be measured on its own, and it is why the
 * round's rules live in `CountRound`, where they can be tested.
 */
@Composable
fun CountTile(
    emoji: String,
    index: Int,
    badge: Int?,
    isJustCounted: Boolean,
    side: Dp,
    glyphSize: TextUnit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isCounted = badge != null
    val shape = RoundedCornerShape(CountTileMetrics.cornerRadius)

    val scale by animateFloatAsState(
        targetValue = when {
            isJustCounted -> CountTileMetrics.bounceScale
            isCounted -> 1f
            else -> CountTileMetrics.uncountedScale
        },
        animationSpec = tween(durationMillis = CountTileMetrics.stateDurationMs),
        label = "countTileScale",
    )

    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = modifier
            .size(side)
            // Counted/bouncing tiles paint over their neighbours: the badge
            // hangs outside the tile and a bounced tile overlaps the row
            // after it, so without this a grid painting in catalogue order
            // draws the child's own tap underneath what comes after it.
            .zIndex(if (isCounted || isJustCounted) 1f else 0f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(side)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .pressScale(interactionSource, CountTileMetrics.pressedScale)
                .clip(shape)
                .background(if (isCounted) Teal.copy(alpha = 0.15f) else Surface, shape)
                .border(
                    CountTileMetrics.borderWidth,
                    if (isCounted) Teal.copy(alpha = 0.6f) else SurfaceBorder,
                    shape,
                )
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onTap)
                .alpha(if (isCounted) 1f else CountTileMetrics.uncountedOpacity)
                .semantics {
                    role = Role.Button
                    contentDescription = emoji
                    if (badge != null) stateDescription = "Counted $badge"
                }
                .testTag("count-item-$index"),
        ) {
            Text(text = emoji, fontSize = glyphSize)
        }

        if (badge != null) {
            CountBadge(number = badge)
        }
    }
}

@Composable
private fun CountBadge(number: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(CountTileMetrics.badgeSide)
            .offset(x = CountTileMetrics.badgeOverhang, y = -CountTileMetrics.badgeOverhang)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Teal, TealDeep)), CircleShape)
            .border(CountTileMetrics.badgeBorderWidth, TextPrimary.copy(alpha = 0.3f), CircleShape),
    ) {
        Text(
            text = number.toString(),
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = CountTileMetrics.badgeGlyphSize,
        )
    }
}
