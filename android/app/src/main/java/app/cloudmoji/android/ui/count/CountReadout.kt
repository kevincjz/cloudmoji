package app.cloudmoji.android.ui.count

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary

/** Every number the readout is drawn from. Mirrors iOS `CountReadout.swift`'s
 * `CountReadoutMetrics`, dp for pt. */
object CountReadoutMetrics {
    val dotSide: Dp = 10.dp
    val padDotSide: Dp = 14.dp
    val dotSpacing: Dp = 6.dp
    val padDotSpacing: Dp = 8.dp

    /** A counted dot swells slightly, so progress reads at a glance rather
     * than only by colour — which matters on a phone held at arm's length by
     * a two-year-old. */
    const val dotCountedScale: Float = 1.2f
    val dotBorderWidth: Dp = 1.5.dp
    val dotRowBottomPadding: Dp = 6.dp

    /** The running count, in the display face. 64dp upright is deliberately
     * huge: it is the number being spoken, and it is the only text in the
     * app a pre-reader is meant to look at. */
    val numeralSize: TextUnit = 64.sp
    val compactNumeralSize: TextUnit = 34.sp
    val padNumeralSize: TextUnit = 88.sp

    /** `text-shadow: 0 0 30px rgba(78,205,196,0.5)`. */
    const val numeralGlowRadius: Float = 30f

    /** The spoken phrase, under the numeral: "three dogs", "三只狗",
     * "いぬ みっつ". */
    val phraseSize: TextUnit = 18.sp
    val compactPhraseSize: TextUnit = 13.sp
    val padPhraseSize: TextUnit = 23.sp
    val phraseTopPadding: Dp = 4.dp

    /** The whole block, reserved whether or not anything has been counted.
     * Upright there is height to spend and the numeral is 64dp; sideways
     * there is not, and the controls at the bottom of the screen are what
     * pays for any extra taken here. */
    fun height(compact: Boolean, expandedPad: Boolean): Dp = when {
        expandedPad -> 154.dp
        compact -> 72.dp
        else -> 112.dp
    }

    /** What is left for the numeral and phrase once the dot row has had its
     * share. Not the *scaled* dot size: `graphicsLayer` scale is a drawing
     * transform and does not change the size a row is laid out at, so the
     * dot row occupies [dotSide]/[padDotSide] however swollen a counted dot
     * looks. */
    fun numberBlockHeight(compact: Boolean, expandedPad: Boolean): Dp =
        height(compact, expandedPad) - (if (expandedPad) padDotSide else dotSide) - dotRowBottomPadding
}

/**
 * Progress dots, the running count, and the phrase being spoken. Ported from
 * iOS `CountReadout.swift`.
 *
 * Takes strings rather than a countable and a language: what to say is
 * `CountingGrammar`'s job and choosing the language is the caller's, so by
 * the time it reaches here it is text.
 */
@Composable
fun CountReadout(
    target: Int,
    progress: Int,
    numeral: String,
    phrase: String,
    isCompact: Boolean,
    isExpandedPad: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        Dots(
            target = target,
            progress = progress,
            isExpandedPad = isExpandedPad,
            modifier = Modifier.padding(bottom = CountReadoutMetrics.dotRowBottomPadding),
        )

        // NOT `if (numeral.isNotEmpty()) { ... }` alone inside a plain
        // Column — an absent child contributes no height, and the grid
        // below would jump up the screen until the first tap and back down
        // on every shuffle. The fixed-height `Box` is the real, invisible
        // thing that holds the space; `numberBlock` only decides what draws
        // inside it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CountReadoutMetrics.numberBlockHeight(isCompact, isExpandedPad)),
            contentAlignment = Alignment.Center,
        ) {
            if (numeral.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = numeral,
                        color = TextPrimary,
                        fontFamily = CloudmojiDisplayFont,
                        fontSize = when {
                            isExpandedPad -> CountReadoutMetrics.padNumeralSize
                            isCompact -> CountReadoutMetrics.compactNumeralSize
                            else -> CountReadoutMetrics.numeralSize
                        },
                        style = TextStyle(
                            shadow = Shadow(
                                color = Teal.copy(alpha = 0.5f),
                                blurRadius = CountReadoutMetrics.numeralGlowRadius,
                            ),
                        ),
                        modifier = Modifier.testTag("count-readout"),
                    )
                    Text(
                        text = phrase,
                        color = TextSecondary,
                        fontFamily = CloudmojiBodyFont,
                        fontWeight = FontWeight.Black,
                        fontSize = when {
                            isExpandedPad -> CountReadoutMetrics.padPhraseSize
                            isCompact -> CountReadoutMetrics.compactPhraseSize
                            else -> CountReadoutMetrics.phraseSize
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = CountReadoutMetrics.phraseTopPadding)
                            .testTag("count-phrase"),
                    )
                }
            }
        }
    }
}

@Composable
private fun Dots(target: Int, progress: Int, isExpandedPad: Boolean, modifier: Modifier = Modifier) {
    // The dots report progress; the numeral beside them says the same thing
    // in a form TalkBack can read, so this would only be repetition.
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            if (isExpandedPad) CountReadoutMetrics.padDotSpacing else CountReadoutMetrics.dotSpacing,
        ),
        modifier = modifier.semantics { hideFromAccessibility() },
    ) {
        repeat(maxOf(target, 0)) { index ->
            val isLit = index < progress
            val scale by animateFloatAsState(
                targetValue = if (isLit) CountReadoutMetrics.dotCountedScale else 1f,
                label = "countDotScale",
            )
            Box(
                modifier = Modifier
                    .size(if (isExpandedPad) CountReadoutMetrics.padDotSide else CountReadoutMetrics.dotSide)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(if (isLit) Teal else TextPrimary.copy(alpha = 0.1f), CircleShape)
                    .border(
                        CountReadoutMetrics.dotBorderWidth,
                        if (isLit) Teal.copy(alpha = 0.6f) else TextPrimary.copy(alpha = 0.06f),
                        CircleShape,
                    ),
            )
        }
    }
}
