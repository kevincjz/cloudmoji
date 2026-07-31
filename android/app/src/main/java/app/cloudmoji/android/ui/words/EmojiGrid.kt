package app.cloudmoji.android.ui.words

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.cloudmoji.android.model.EmojiEntry
import app.cloudmoji.android.model.EmojiSection
import app.cloudmoji.android.model.SectionJump
import app.cloudmoji.android.model.sectionAtItemIndex
import app.cloudmoji.android.model.sectionHeaderIndex
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont

/** The list's own spacing and section headers. Mirrors iOS `EmojiGridMetrics`. */
object EmojiGridMetrics {
    val horizontalPadding = 10.dp
    val padHorizontalPadding = 22.dp
    val topPadding = 2.dp

    /** Clears the reserved home-button band so the last row is never
     * half-hidden behind it — see `MiniAppScaffold`. */
    val bottomPadding = 24.dp

    val headerHeight = 34.dp
    val padHeaderHeight = 44.dp
    val headerGlyphSize = 18.sp
    val padHeaderGlyphSize = 22.sp
    val headerLabelSize = 14.sp
    val padHeaderLabelSize = 17.sp
    val headerGap = 6.dp
    val headerRuleHeight = 1.dp
}

/** Every number the tile is drawn from. Mirrors iOS `EmojiTileMetrics`. */
object EmojiTileMetrics {
    /** 64dp is the floor for anything a *child* taps; 72dp is the preferred
     * size, and this is the surface a toddler taps more than any other. Do
     * not shrink this to make a layout fit — the grid adds a column instead
     * (`GridCells.Adaptive`). */
    val side = 72.dp
    val padSide = 92.dp
    val spacing = 8.dp
    val padSpacing = 10.dp
    val cornerRadius = 18.dp
    val glyphSize = 40.sp
    val padGlyphSize = 50.sp
    val borderWidth = 1.dp
    const val pressedScale = 0.85f

    /** The peak of the bounce fired when a word is spoken. */
    const val bounceScale = 1.3f
}

/**
 * **One long scrollable list of every emoji**, cut into a section per
 * category. Ported from iOS `EmojiGrid.swift`.
 *
 * Columns stay adaptive at [EmojiTileMetrics.side]/[EmojiTileMetrics.padSide]
 * minimum, the direct equivalent of the web's
 * `repeat(auto-fill, minmax(72px, 1fr))`: the same list reflows from a
 * 375dp phone (4 columns) to a wide tablet (12+) with no breakpoint, and a
 * column can never end up narrower than the tile it holds.
 *
 * [jumpTo] scrolls the list to a section — see [sectionHeaderIndex]. Which
 * section is currently showing is reported back through [onActiveSection],
 * driven by [LazyGridState.firstVisibleItemIndex] via [sectionAtItemIndex] —
 * Compose already tracks that without the geometry probing iOS's
 * `ScrollView` needed.
 */
@Composable
fun EmojiGrid(
    sections: List<EmojiSection>,
    bouncingId: String?,
    wordFor: (EmojiEntry) -> String,
    labelFor: (EmojiSection) -> String,
    jumpTo: SectionJump?,
    isExpandedPad: Boolean,
    onActiveSection: (String) -> Unit,
    onTap: (EmojiEntry) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val tileSide = if (isExpandedPad) EmojiTileMetrics.padSide else EmojiTileMetrics.side
    val tileSpacing = if (isExpandedPad) EmojiTileMetrics.padSpacing else EmojiTileMetrics.spacing

    LaunchedEffect(jumpTo, sections) {
        val jump = jumpTo ?: return@LaunchedEffect
        val index = sectionHeaderIndex(sections, jump.id) ?: return@LaunchedEffect
        gridState.animateScrollToItem(index)
    }

    val activeSection by remember(sections) {
        derivedStateOf { sectionAtItemIndex(sections, gridState.firstVisibleItemIndex) }
    }
    LaunchedEffect(activeSection) {
        activeSection?.let(onActiveSection)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = tileSide),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(tileSpacing),
        verticalArrangement = Arrangement.spacedBy(tileSpacing),
        contentPadding = PaddingValues(
            start = if (isExpandedPad) EmojiGridMetrics.padHorizontalPadding else EmojiGridMetrics.horizontalPadding,
            end = if (isExpandedPad) EmojiGridMetrics.padHorizontalPadding else EmojiGridMetrics.horizontalPadding,
            top = EmojiGridMetrics.topPadding,
            bottom = EmojiGridMetrics.bottomPadding,
        ),
        modifier = modifier.testTag("emoji-grid"),
    ) {
        sections.forEach { section ->
            item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(section = section, label = labelFor(section), isExpandedPad = isExpandedPad)
            }
            items(section.entries, key = { it.id }) { entry ->
                EmojiTile(
                    entry = entry,
                    isBouncing = bouncingId == entry.id,
                    word = wordFor(entry),
                    isExpandedPad = isExpandedPad,
                    onTap = { onTap(entry) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(section: EmojiSection, label: String, isExpandedPad: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EmojiGridMetrics.headerGap),
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isExpandedPad) EmojiGridMetrics.padHeaderHeight else EmojiGridMetrics.headerHeight)
            // One element, not two: TalkBack announcing "🍎", "Fruits" as
            // separate stops in the middle of a 200-tile list is noise.
            .semantics(mergeDescendants = true) { contentDescription = "${section.tab.icon} $label" }
            .testTag("section-${section.id}"),
    ) {
        Text(
            text = section.tab.icon,
            fontSize = if (isExpandedPad) EmojiGridMetrics.padHeaderGlyphSize else EmojiGridMetrics.headerGlyphSize,
        )
        Text(
            text = label,
            color = Teal,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (isExpandedPad) EmojiGridMetrics.padHeaderLabelSize else EmojiGridMetrics.headerLabelSize,
            maxLines = 1,
        )
        // Runs the name out to the edge, so a section reads as a band across
        // the list rather than as a word floating above some emojis.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(EmojiGridMetrics.headerRuleHeight)
                .background(SurfaceBorder),
        )
    }
}

/**
 * One emoji. 72dp is this project's preferred child-facing target; the rule
 * is 64dp minimum, and this is the surface a toddler taps most. Ported from
 * iOS `EmojiTile.swift`.
 */
@Composable
fun EmojiTile(
    entry: EmojiEntry,
    isBouncing: Boolean,
    word: String,
    isExpandedPad: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val side = if (isExpandedPad) EmojiTileMetrics.padSide else EmojiTileMetrics.side
    val shape = RoundedCornerShape(if (isExpandedPad) 22.dp else EmojiTileMetrics.cornerRadius)

    val bounceScale by animateFloatAsState(
        targetValue = if (isBouncing) EmojiTileMetrics.bounceScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "emojiBounce",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = side)
            .zIndex(if (isBouncing) 1f else 0f)
            .graphicsLayer {
                scaleX = bounceScale
                scaleY = bounceScale
            }
            .pressScale(interactionSource, EmojiTileMetrics.pressedScale)
            .clip(shape)
            .background(Surface, shape)
            .border(EmojiTileMetrics.borderWidth, SurfaceBorder, shape)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onTap)
            .semantics {
                role = Role.Button
                contentDescription = word
            }
            .testTag("emoji-${entry.emoji}"),
    ) {
        Text(text = entry.emoji, fontSize = if (isExpandedPad) EmojiTileMetrics.padGlyphSize else EmojiTileMetrics.glyphSize)
    }
}
