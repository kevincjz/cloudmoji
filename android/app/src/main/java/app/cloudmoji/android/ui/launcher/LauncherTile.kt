package app.cloudmoji.android.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.Amber
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.Lavender
import app.cloudmoji.android.ui.theme.Moonlight
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary

/**
 * Every number a launcher tile is drawn from. Mirrors iOS
 * `LauncherTile.swift`'s `LauncherTileMetrics`, dp for pt.
 *
 * Sized as step functions of `(compact, isExpandedPad)` — the same shape as
 * `app.cloudmoji.android.ui.count.CountTileMetrics` — rather than a single
 * constant, so [LauncherTileMetricsTest] can assert the child-target floor
 * across every combination the real [app.cloudmoji.android.ui.common.CloudmojiLayout]
 * can ever hand this screen, not just the one shape a device happens to be
 * running.
 */
object LauncherTileMetrics {
    /** `CLAUDE.md` rule 1's floor. The *cell* — icon plus caption, the whole
     * tappable region [LauncherTile] draws — is what has to clear this, not
     * the visible squircle alone: iOS's own comment on `LauncherTile.swift`
     * says the same thing ("the visible squircle is Home-Screen sized, while
     * the whole icon-and-label cell remains the much larger child-facing
     * target"). */
    val childMinimum: Dp = 64.dp

    /** `CLAUDE.md` rule 2's floor between adjacent child-facing targets. */
    val gapMinimum: Dp = 8.dp

    fun iconSide(compact: Boolean, isExpandedPad: Boolean): Dp = when {
        isExpandedPad -> 118.dp
        compact -> 68.dp
        else -> 76.dp
    }

    fun cellHeight(compact: Boolean, isExpandedPad: Boolean): Dp = when {
        isExpandedPad -> 164.dp
        compact -> 94.dp
        else -> 112.dp
    }

    fun spacing(isExpandedPad: Boolean): Dp = if (isExpandedPad) 20.dp else 10.dp

    fun labelSize(compact: Boolean, isExpandedPad: Boolean): TextUnit = when {
        isExpandedPad -> 17.sp
        compact -> 12.sp
        else -> 13.sp
    }

    /** Caps the grid's own width and centers it, standing in for iOS's fixed
     * iPad cell width (140pt) — both exist for the same reason: stop icons
     * spreading edge-to-edge on a wide screen. */
    fun maxGridWidth(isExpandedPad: Boolean): Dp = if (isExpandedPad) 780.dp else 460.dp

    /** `docs/design/DESIGN_SYSTEM.md`'s "22px — 76pt launcher icon (iOS);
     * computed as 25% of the icon side" — the fraction is the actual rule
     * iOS's `LauncherAppIcon.shape` applies (`side * 0.25`); its sibling
     * `cornerRadius: CGFloat = 22` constant is dead code on the iOS side
     * (never referenced by any shape) and is deliberately not ported. */
    const val cornerRadiusFraction: Float = 0.25f

    /** Design system Active States: "Launcher icon cells: `scale(0.92)`". */
    const val pressedScale: Float = 0.92f
}

/** The visual identity of one mini-app's layered launcher icon — accent and
 * secondary stops of the icon's gradient. Mirrors iOS `MiniAppVisualTheme`,
 * narrowed to the two colors [LauncherAppIcon] actually draws with (the third
 * stop is always the app's own background, not a per-app color). Kept public
 * (like `ui.mascot.MascotStyle`) so [LauncherTileMetricsTest] can assert the
 * mapping directly rather than only through a rendered tree. */
data class LauncherIconTheme(val accent: Color, val secondary: Color)

/** Ported from iOS `MiniApp.visualTheme`, case for case. Photos' secondary
 * stop is [Moonlight] there — the Phase 0 Android tile had drifted to
 * [Amber], which this corrects. */
fun MiniApp.launcherIconTheme(): LauncherIconTheme = when (this) {
    MiniApp.Words -> LauncherIconTheme(Teal, Coral)
    MiniApp.Count -> LauncherIconTheme(Gold, Amber)
    MiniApp.FlashCards -> LauncherIconTheme(Gold, Lavender)
    MiniApp.Music -> LauncherIconTheme(Coral, Moonlight)
    MiniApp.Animals -> LauncherIconTheme(Teal, Gold)
    MiniApp.Photos -> LauncherIconTheme(Coral, Moonlight)
    MiniApp.Sleepy -> LauncherIconTheme(Moonlight, Lavender)
}

/**
 * One mini-app on the launcher: a layered squircle icon above a one-line
 * caption. Ported from iOS `LauncherTile.swift`.
 *
 * [label] arrives already resolved to the family's language — this tile does
 * not read settings, the same way no other tile in the app does.
 */
@Composable
fun LauncherTile(
    app: MiniApp,
    label: String,
    iconSide: Dp,
    cellHeight: Dp,
    labelSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(cellHeight)
            .pressScale(interactionSource, LauncherTileMetrics.pressedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag("launcher-tile-${app.route}"),
    ) {
        LauncherAppIcon(app = app, side = iconSide)

        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = labelSize,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A layered, in-app app icon: an accent-to-secondary gradient squircle, a
 * soft highlight in the top-left corner standing in for iOS's blurred white
 * circle (`Modifier.blur` needs `RenderEffect`, API 31+; this module's
 * `minSdk` is 26 — the same trade-off `ui/mascot/CloudMascot.kt` already
 * documents for its own ambient shadow), the mini-app's emoji glyph, and a
 * hairline border. Ported from iOS `LauncherAppIcon`.
 */
@Composable
private fun LauncherAppIcon(app: MiniApp, side: Dp) {
    val theme = remember(app) { app.launcherIconTheme() }
    val shape = remember(side) { RoundedCornerShape(side * LauncherTileMetrics.cornerRadiusFraction) }
    val density = LocalDensity.current
    val sidePx = with(density) { side.toPx() }
    val highlight = remember(sidePx) {
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0f)),
            center = Offset(sidePx * 0.26f, sidePx * 0.24f),
            radius = sidePx * 0.55f,
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(theme.accent, theme.secondary, BackgroundMid.copy(alpha = 0.88f))))
            .background(highlight)
            .border(1.dp, Color.White.copy(alpha = 0.24f), shape),
    ) {
        Text(text = app.icon, fontSize = (side.value * 0.46f).sp, textAlign = TextAlign.Center)
    }
}
