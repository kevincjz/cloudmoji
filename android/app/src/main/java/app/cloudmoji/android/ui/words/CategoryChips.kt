package app.cloudmoji.android.ui.words

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.CategoryTab
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextSecondary

/** Which shape the same list of categories takes. Mirrors iOS
 * `CategorySource.swift`'s `CategoryLayout`. */
enum class CategoryChipLayout {
    /** Portrait: a horizontally scrolling strip, icon + label. */
    Strip,

    /** Landscape: a two-column vertical rail of icons only. */
    Rail,
}

/** Every number both layouts are drawn from. Mirrors iOS `CategorySourceMetrics`. */
object CategoryChipMetrics {
    /** The floor for anything a child taps — a category chip is squarely
     * that, changed far more often than any parent control. */
    val side = 64.dp
    val padSide = 72.dp
    val spacing = 8.dp
    val cornerRadius = 16.dp
    val borderWidth = 1.5.dp
    val stripGlyphSize = 18.sp
    val padStripGlyphSize = 22.sp
    val railGlyphSize = 28.sp
    val labelSize = 14.sp
    val padLabelSize = 16.sp
    val chipHorizontalPadding = 16.dp
    val padChipHorizontalPadding = 20.dp
    val chipGap = 4.dp
    const val pressedScale = 0.9f

    /** The web/iOS dim an inactive rail icon with `grayscale + opacity`;
     * Compose has no cheap per-Text desaturation, so this port keeps only
     * the opacity half of that treatment. */
    const val inactiveOpacity = 0.65f
}

/**
 * One component, two layouts — the same list of category tabs, either a
 * horizontal strip (portrait) or a two-column rail (landscape, hosted inside
 * `SideRail`). Ported from iOS `CategorySource.swift`.
 *
 * A chip **scrolls** the grid to its section; it never filters anything —
 * [selectedId] is reported back by whichever section the grid is actually
 * showing, so the chip and the content can never disagree.
 */
@Composable
fun CategoryChips(
    tabs: List<CategoryTab>,
    selectedId: String,
    labelFor: (CategoryTab) -> String,
    layout: CategoryChipLayout,
    isExpandedPad: Boolean,
    onSelect: (CategoryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (layout) {
        CategoryChipLayout.Strip -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CategoryChipMetrics.spacing),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = modifier.testTag("category-bar"),
        ) {
            items(tabs, key = { it.id }) { tab ->
                CategoryChip(
                    tab = tab,
                    isActive = tab.id == selectedId,
                    label = labelFor(tab),
                    showsLabel = true,
                    isExpandedPad = isExpandedPad,
                    onSelect = onSelect,
                )
            }
        }

        CategoryChipLayout.Rail -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(CategoryChipMetrics.spacing),
            verticalArrangement = Arrangement.spacedBy(CategoryChipMetrics.spacing),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            modifier = modifier.testTag("category-rail"),
        ) {
            items(tabs, key = { it.id }) { tab ->
                CategoryChip(
                    tab = tab,
                    isActive = tab.id == selectedId,
                    label = labelFor(tab),
                    showsLabel = false,
                    isExpandedPad = isExpandedPad,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    tab: CategoryTab,
    isActive: Boolean,
    label: String,
    showsLabel: Boolean,
    isExpandedPad: Boolean,
    onSelect: (CategoryTab) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CategoryChipMetrics.cornerRadius)
    val side = if (isExpandedPad) CategoryChipMetrics.padSide else CategoryChipMetrics.side
    val plate = if (isActive) Teal.copy(alpha = 0.2f) else if (showsLabel) Surface else Color.Transparent
    val outline = if (isActive) Teal.copy(alpha = 0.4f) else if (showsLabel) SurfaceBorder else Color.Transparent
    val glyphAlpha = if (showsLabel || isActive) 1f else CategoryChipMetrics.inactiveOpacity
    val glyphSize = if (showsLabel) {
        if (isExpandedPad) CategoryChipMetrics.padStripGlyphSize else CategoryChipMetrics.stripGlyphSize
    } else {
        CategoryChipMetrics.railGlyphSize
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CategoryChipMetrics.chipGap, Alignment.CenterHorizontally),
        modifier = Modifier
            .then(if (showsLabel) Modifier.heightIn(min = side) else Modifier.size(side))
            .pressScale(interactionSource, CategoryChipMetrics.pressedScale)
            .clip(shape)
            .background(plate, shape)
            .border(CategoryChipMetrics.borderWidth, outline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onSelect(tab) },
            )
            .semantics {
                role = Role.Button
                contentDescription = label
                selected = isActive
            }
            .testTag("cat-${tab.id}")
            .then(
                if (showsLabel) {
                    Modifier.padding(
                        horizontal = if (isExpandedPad) {
                            CategoryChipMetrics.padChipHorizontalPadding
                        } else {
                            CategoryChipMetrics.chipHorizontalPadding
                        },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = tab.icon,
            fontSize = glyphSize,
            modifier = Modifier.graphicsLayer { alpha = glyphAlpha },
        )
        if (showsLabel) {
            Text(
                text = label,
                color = if (isActive) Teal else TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isExpandedPad) CategoryChipMetrics.padLabelSize else CategoryChipMetrics.labelSize,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
