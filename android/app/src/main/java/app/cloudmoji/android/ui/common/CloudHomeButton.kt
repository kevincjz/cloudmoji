package app.cloudmoji.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.ui.mascot.CloudMascot
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary

/** Every number [CloudHomeButton] is drawn from. Mirrors iOS `CloudHomeButton.swift`'s `HomeButtonMetrics`. */
object HomeButtonMetrics {
    /** A **child** taps this — it is how they get out of a mini-app they did
     * not mean to open — so it takes 84dp, well past the 64dp floor for
     * anything else a child taps, and sits centred rather than tucked in a
     * corner: a corner is where an adult expects Back, and a toddler scans
     * the middle. This is the most important control in the app for a child
     * who has opened the wrong thing, so it gets the most generous target. */
    val side = 84.dp
    val mascotSize = 58.dp

    /** How far the hosting screen keeps its own content clear of the button —
     * the button, its inset, and a little air. [MiniAppScaffold] reserves
     * this band rather than relying on safe-area padding, which only insets
     * where a scroll view's content *ends*: everything in the middle would
     * otherwise travel underneath the cloud. */
    val reservedHeight = 108.dp
    val inset = 12.dp

    /** Design system Active States: tabs `scale(0.9)`. */
    const val pressedScale = 0.9f

    val borderWidth = 2.dp
    val badgeSize = 26.dp
}

/**
 * The way out of every mini-app: a cloud, not a chevron, because the person
 * who most needs it cannot read "Back" and does not know the top-left corner
 * of a screen means anything — but does know the cloud. Ported from iOS
 * `CloudHomeButton.swift`.
 *
 * [accent] tints the little house badge; `null` uses the app's own teal
 * rather than a per-mini-app identity color, since Words has none of its own.
 */
@Composable
fun CloudHomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val badgeTint = accent ?: Teal

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(HomeButtonMetrics.side)
            .pressScale(interactionSource, HomeButtonMetrics.pressedScale)
            // Two layers, and the near-opaque one is load-bearing: this
            // button floats over content that scrolls underneath it (the
            // emoji grid), and a translucent-only background would leave the
            // cloud sitting on top of a half-visible tile.
            .shadow(14.dp, CircleShape)
            .background(BackgroundPrimary.copy(alpha = 0.95f), CircleShape)
            .background((accent ?: TextPrimary).copy(alpha = if (accent == null) 0.04f else 0.14f), CircleShape)
            .border(HomeButtonMetrics.borderWidth, accent?.copy(alpha = 0.44f) ?: SurfaceBorder, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = "Home"
            }
            .testTag("cloud-home"),
    ) {
        // Hidden from the accessibility tree, unlike every other mascot in
        // the app: `CloudMascot` publishes its own "Cloudmoji" node, and a
        // second one nested inside this button's "Home" node would surface
        // as an ambiguous, separately-focusable stop for TalkBack — the same
        // reason iOS marks this instance `.accessibilityHidden(true)`.
        Box(modifier = Modifier.clearAndSetSemantics {}) {
            CloudMascot(mood = MascotMood.Happy, size = HomeButtonMetrics.mascotSize)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .size(HomeButtonMetrics.badgeSize)
                .background(badgeTint, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        ) {
            Text(text = "🏠", fontSize = 13.sp)
        }
    }
}
