package app.cloudmoji.android.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.ModeHeaderMetrics
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.mascot.CloudMascot
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.HeaderPlate
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary

/**
 * Home. A wallpaper gradient, one gated Grown-ups control, and the app icon
 * grid — the iPhone-style four-column Home Screen metaphor iOS's
 * `LauncherView.swift` establishes. [apps] arrives already filtered by the
 * central [app.cloudmoji.android.model.AppAccessPolicy] — a locked
 * entitlement simply never puts a Full mini-app in this list, so this screen
 * makes no accessibility decision of its own; it draws exactly what it is
 * handed, in that order (partial rows stay left-aligned, matching iOS).
 *
 * Sizing comes from [LocalCloudmojiLayout] (the same composition local every
 * other screen reads, published by the `AdaptiveShell` this composable is
 * always hosted inside) rather than a local `BoxWithConstraints` breakpoint,
 * so a phone-vs-tablet call is made once per composition, the same way, on
 * every screen in the app.
 *
 * Motion is limited to each tile's own press-scale
 * ([LauncherTileMetrics.pressedScale], via [app.cloudmoji.android.ui.common.pressScale]).
 * iOS's launcher has no per-tile appear animation to port — `LauncherView.swift`
 * and `LauncherTile.swift` apply no `.transition`/`.animation` to the grid or
 * its items; the only transition on that screen belongs to the voice-message
 * pill (out of scope: Apple Watch has no Android companion yet) and the
 * launcher/mini-app cross-fade owned by `ContentView.swift` one layer up
 * (this module's equivalent is `CloudmojiApp.kt`'s route switch, also out of
 * this screen's scope). A staggered appear here would be new motion, not a
 * port.
 */
@Composable
fun LauncherScreen(
    apps: List<MiniApp>,
    language: Language,
    onOpen: (MiniApp) -> Unit,
    onParent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val compact = layout.isCompactPhone
    val isExpandedPad = layout.isExpandedPad

    val iconSide = LauncherTileMetrics.iconSide(compact, isExpandedPad)
    val cellHeight = LauncherTileMetrics.cellHeight(compact, isExpandedPad)
    val spacing = LauncherTileMetrics.spacing(isExpandedPad)
    val labelSize = LauncherTileMetrics.labelSize(compact, isExpandedPad)
    val maxGridWidth = LauncherTileMetrics.maxGridWidth(isExpandedPad)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("launcher"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isExpandedPad) 32.dp else if (compact) 12.dp else 14.dp,
                    vertical = if (isExpandedPad) 20.dp else if (compact) 6.dp else 12.dp,
                ),
        ) {
            LauncherHeader(onParent = onParent, compact = compact, isExpandedPad = isExpandedPad)
            Spacer(Modifier.height(if (isExpandedPad) 34.dp else if (compact) 10.dp else 18.dp))

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = maxGridWidth),
                ) {
                    items(apps, key = { it.route }) { app ->
                        LauncherTile(
                            app = app,
                            label = app.label(language),
                            iconSide = iconSide,
                            cellHeight = cellHeight,
                            labelSize = labelSize,
                            onClick = { onOpen(app) },
                        )
                    }
                }
            }
        }
    }
}

/** The only glass surface on the launcher: brand mark plus the one
 * grown-up doorway. Ported from iOS `LauncherHeaderWidget`. */
@Composable
private fun LauncherHeader(
    onParent: () -> Unit,
    compact: Boolean,
    isExpandedPad: Boolean,
) {
    val shape = RoundedCornerShape(if (isExpandedPad) 32.dp else 26.dp)
    val mascotSize = if (isExpandedPad) 66.dp else if (compact) 42.dp else 50.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape)
            .background(HeaderPlate, shape)
            .border(1.dp, SurfaceBorder, shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(
                horizontal = if (isExpandedPad) 20.dp else if (compact) 10.dp else 12.dp,
                vertical = if (isExpandedPad) 13.dp else if (compact) 6.dp else 9.dp,
            ),
    ) {
        CloudMascot(mood = MascotMood.Happy, size = mascotSize)
        Spacer(Modifier.width(if (isExpandedPad) 13.dp else if (compact) 7.dp else 9.dp))

        Column {
            Text(
                text = "Cloudmoji",
                color = Teal,
                fontFamily = CloudmojiDisplayFont,
                fontSize = if (isExpandedPad) 27.sp else if (compact) 17.sp else 20.sp,
                maxLines = 1,
            )
            if (!compact) {
                Text(
                    text = "Tap. Listen. Learn!",
                    color = TextSecondary,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isExpandedPad) 12.sp else 9.sp,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        LauncherParentControl(compact = compact, isExpandedPad = isExpandedPad, onClick = onParent)
    }
}

/** Every route to Settings in this app funnels through the gate behind
 * [onClick] — see `CloudmojiApp.kt`'s `openParentDoor`. This is the only
 * parent-facing element on the launcher itself. */
@Composable
private fun LauncherParentControl(
    compact: Boolean,
    isExpandedPad: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (isExpandedPad) 19.dp else 15.dp)
    val height = if (isExpandedPad) 54.dp else ModeHeaderMetrics.controlSide

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(height)
            .pressScale(interactionSource, ModeHeaderMetrics.pressedScale)
            .clip(shape)
            .background(Teal.copy(alpha = 0.16f), shape)
            .border(1.dp, Teal.copy(alpha = 0.34f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = "For grown-ups"
            }
            .testTag("launcher-parent")
            .padding(horizontal = if (isExpandedPad) 18.dp else if (compact) 10.dp else 12.dp),
    ) {
        Text(
            text = "🔒  " + if (compact) "Parents" else "Grown-ups",
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = if (isExpandedPad) 15.sp else if (compact) 11.sp else 12.sp,
        )
    }
}
