package app.cloudmoji.android.ui.launcher

import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.ui.theme.Amber
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.Lavender
import app.cloudmoji.android.ui.theme.Moonlight
import app.cloudmoji.android.ui.theme.Teal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `LauncherTileMetrics`'s own arithmetic and `MiniApp.launcherIconTheme()`'s
 * lookup table are plain `Dp`/`TextUnit`/`Color`-returning pure functions
 * with no Compose runtime or Android device behind them — the same reason
 * [app.cloudmoji.android.ui.count.CountTileMetricsTest] and
 * [app.cloudmoji.android.ui.mascot.CloudMascotStyleTest] run on the plain
 * JVM instead of `androidTest`.
 */
class LauncherTileMetricsTest {

    /**
     * `CLAUDE.md` rule 1: the *cell* — the whole tappable region
     * [LauncherTile] draws, not the visible squircle alone — must clear the
     * 64dp child-target floor in every layout tier the real
     * [app.cloudmoji.android.ui.common.CloudmojiLayout] can hand this
     * screen: phone portrait, compact phone landscape, and expanded pad.
     *
     * Mutation: drop `compact -> 94.dp` to `60.dp`. Fails.
     */
    @Test
    fun `cellHeight never drops below the child target floor, in any layout tier`() {
        for (compact in listOf(false, true)) {
            for (isExpandedPad in listOf(false, true)) {
                val height = LauncherTileMetrics.cellHeight(compact, isExpandedPad)
                assertTrue(
                    "cellHeight(compact=$compact, isExpandedPad=$isExpandedPad) is $height, " +
                        "under the ${LauncherTileMetrics.childMinimum} floor",
                    height >= LauncherTileMetrics.childMinimum,
                )
            }
        }
    }

    /**
     * `CLAUDE.md` rule 2: the grid's own row/column spacing is the gap
     * between adjacent tiles, and must clear the 8dp floor regardless of
     * layout tier.
     */
    @Test
    fun `spacing never drops below the 8dp gap floor`() {
        for (isExpandedPad in listOf(false, true)) {
            val spacing = LauncherTileMetrics.spacing(isExpandedPad)
            assertTrue(
                "spacing(isExpandedPad=$isExpandedPad) is $spacing, under the " +
                    "${LauncherTileMetrics.gapMinimum} floor",
                spacing >= LauncherTileMetrics.gapMinimum,
            )
        }
    }

    /**
     * An expanded pad gets a strictly larger icon, cell, and label than a
     * compact phone in every case — the whole reason [LauncherTileMetrics]
     * takes both flags rather than being one constant.
     *
     * Mutation: swap the `isExpandedPad` and `compact` branches in
     * `iconSide`. Fails.
     */
    @Test
    fun `an expanded pad tile is strictly larger than a compact phone tile`() {
        val compactIcon = LauncherTileMetrics.iconSide(compact = true, isExpandedPad = false)
        val padIcon = LauncherTileMetrics.iconSide(compact = false, isExpandedPad = true)
        assertTrue(padIcon > compactIcon)

        val compactCell = LauncherTileMetrics.cellHeight(compact = true, isExpandedPad = false)
        val padCell = LauncherTileMetrics.cellHeight(compact = false, isExpandedPad = true)
        assertTrue(padCell > compactCell)

        val compactLabel = LauncherTileMetrics.labelSize(compact = true, isExpandedPad = false)
        val padLabel = LauncherTileMetrics.labelSize(compact = false, isExpandedPad = true)
        assertTrue(padLabel.value > compactLabel.value)
    }

    /** Regular phone sits strictly between compact phone and expanded pad —
     * a step function, not a two-value switch. */
    @Test
    fun `regular phone sizing sits strictly between compact phone and expanded pad`() {
        val compact = LauncherTileMetrics.iconSide(compact = true, isExpandedPad = false)
        val regular = LauncherTileMetrics.iconSide(compact = false, isExpandedPad = false)
        val pad = LauncherTileMetrics.iconSide(compact = false, isExpandedPad = true)
        assertTrue(compact < regular)
        assertTrue(regular < pad)
    }

    @Test
    fun `an expanded pad grid is wider than a phone grid`() {
        assertTrue(LauncherTileMetrics.maxGridWidth(isExpandedPad = true) > LauncherTileMetrics.maxGridWidth(isExpandedPad = false))
    }

    /**
     * `docs/design/DESIGN_SYSTEM.md`'s Active States table: "Launcher icon
     * cells: `scale(0.92)`".
     *
     * Mutation: change `pressedScale` to `0.85f` (the emoji-tile value from
     * the same table, a plausible copy-paste slip). Fails.
     */
    @Test
    fun `pressedScale and cornerRadiusFraction match the design system`() {
        assertEquals(0.92f, LauncherTileMetrics.pressedScale, 0.0001f)
        assertEquals(0.25f, LauncherTileMetrics.cornerRadiusFraction, 0.0001f)
    }

    /**
     * Ported from iOS `MiniApp.visualTheme` — the Phase 0 Android tile had
     * Photos' secondary stop drifted to [Amber] (`Coral, Amber`); iOS's own
     * source (`Views/Launcher/MiniApp.swift`) pairs Photos with
     * `Theme.coral`/`Theme.moonlight`, identical to Music's pairing — not a
     * bug on iOS's side, just a color this table happens to reuse.
     *
     * Mutation: revert Photos' secondary stop to `Amber`. Fails.
     */
    @Test
    fun `every mini-app's icon theme matches iOS MiniApp visualTheme exactly`() {
        val expected = mapOf(
            MiniApp.Words to LauncherIconTheme(Teal, Coral),
            MiniApp.Count to LauncherIconTheme(Gold, Amber),
            MiniApp.FlashCards to LauncherIconTheme(Gold, Lavender),
            MiniApp.Music to LauncherIconTheme(Coral, Moonlight),
            MiniApp.Animals to LauncherIconTheme(Teal, Gold),
            MiniApp.Photos to LauncherIconTheme(Coral, Moonlight),
            MiniApp.Sleepy to LauncherIconTheme(Moonlight, Lavender),
        )

        MiniApp.entries.forEach { app ->
            assertEquals("theme for $app", expected.getValue(app), app.launcherIconTheme())
        }
    }
}
