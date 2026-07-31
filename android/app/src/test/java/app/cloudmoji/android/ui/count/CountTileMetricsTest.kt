package app.cloudmoji.android.ui.count

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `CountTileMetrics`'s own arithmetic — `side`/`glyphSize`/`gridSpacing`/
 * `maxGridWidth` are plain `Dp`/`TextUnit` step functions with no Compose
 * runtime or Android device behind them, so unlike everything else in
 * `ui/count/`, this class runs on the plain JVM. Moved here from
 * `CountChildTargetsTest` (androidTest, unrunnable in this environment) so
 * the floor invariant actually executes.
 */
class CountTileMetricsTest {

    /**
     * `CountTileMetrics.side()` is a step function of the round size, not a
     * single constant — this asserts the *rule*, not one instance, across
     * every round size the parent's count range can ever produce
     * ([app.cloudmoji.android.model.Settings.countBounds] is `2..10`), both
     * orientations, and both `isExpandedPad` settings — a tablet's 1.30×
     * scale-up can only ever grow the tile, but it is asserted here rather
     * than assumed.
     *
     * Mutation: change `expandedPadSideScale` to something under `1f`, or
     * drop the 96dp/82dp/72dp/64dp base steps below 64dp. Either fails.
     */
    @Test
    fun `side never drops below the child target floor, in any orientation or pad mode`() {
        for (count in 2..10) {
            for (compact in listOf(false, true)) {
                for (isExpandedPad in listOf(false, true)) {
                    val side = CountTileMetrics.side(count, compact, isExpandedPad)
                    assertTrue(
                        "CountTileMetrics.side(count=$count, compact=$compact, isExpandedPad=$isExpandedPad) " +
                            "is $side, under the ${CountTileMetrics.childMinimum} floor",
                        side >= CountTileMetrics.childMinimum,
                    )
                }
            }
        }
    }

    /**
     * The expanded-pad scale is a genuine *increase*, not a no-op — this is
     * the property `CountView.swift`'s own `layout.isExpandedPad ? 1.30 : 1`
     * exists for: a tablet gets tablet-sized tiles, not phone-sized ones
     * adrift in more empty space.
     *
     * Mutation: hardcode `expandedPadSideScale` to `1f`. Every equality below
     * still holds only by coincidence of rounding, and the "is strictly
     * larger" assertion catches it directly.
     */
    @Test
    fun `the expanded-pad side is exactly 1_30x the base side, and strictly larger`() {
        for (count in 2..10) {
            for (compact in listOf(false, true)) {
                val base = CountTileMetrics.side(count, compact, isExpandedPad = false)
                val expanded = CountTileMetrics.side(count, compact, isExpandedPad = true)
                assertEquals(base.value * 1.30f, expanded.value, 0.01f)
                assertTrue(
                    "expanded side ($expanded) must be strictly larger than base ($base)",
                    expanded > base,
                )
            }
        }
    }

    @Test
    fun `the expanded-pad glyph size is exactly 1_30x the base glyph size`() {
        for (count in 2..10) {
            for (compact in listOf(false, true)) {
                val base = CountTileMetrics.glyphSize(count, compact, isExpandedPad = false)
                val expanded = CountTileMetrics.glyphSize(count, compact, isExpandedPad = true)
                assertEquals(base.value * 1.30f, expanded.value, 0.01f)
            }
        }
    }

    /**
     * Mutation: hardcode `expandedPadSpacingScale` to `1f`. This fails, and
     * so would using `expandedPadSideScale` (1.30) here instead of the
     * correct, smaller 1.18 iOS uses for spacing specifically.
     */
    @Test
    fun `the expanded-pad grid spacing is exactly 1_18x the base spacing, not the side scale`() {
        for (count in 2..10) {
            for (compact in listOf(false, true)) {
                val base = CountTileMetrics.gridSpacing(count, compact, isExpandedPad = false)
                val expanded = CountTileMetrics.gridSpacing(count, compact, isExpandedPad = true)
                assertEquals(base.value * 1.18f, expanded.value, 0.01f)
            }
        }
    }

    @Test
    fun `the expanded-pad max grid width is exactly 1_30x the base, in both orientations`() {
        for (compact in listOf(false, true)) {
            val base = CountTileMetrics.maxGridWidth(compact, isExpandedPad = false)
            val expanded = CountTileMetrics.maxGridWidth(compact, isExpandedPad = true)
            assertEquals(base.value * 1.30f, expanded.value, 0.01f)
        }
    }

    /**
     * `columns` is a column *count*, not a size — iOS's own `grid` never
     * scales it by `isExpandedPad`, only `side`/`glyph`/`spacing`. Pinned
     * here so a future edit does not "helpfully" start scaling it too.
     */
    @Test
    fun `columns has no isExpandedPad parameter to scale`() {
        // Compile-time assertion, really: `columns(count, compact)` takes
        // exactly two arguments. If this line stops compiling because a
        // third parameter was added, that is the regression this test exists
        // to catch — the two runtime assertions below are secondary.
        assertEquals(3, CountTileMetrics.columns(count = 9, compact = false))
        assertEquals(5, CountTileMetrics.columns(count = 9, compact = true))
    }

    @Test
    fun `scale constants match the product spec ported from iOS CountView`() {
        assertEquals(1.30f, CountTileMetrics.expandedPadSideScale, 0.001f)
        assertEquals(1.18f, CountTileMetrics.expandedPadSpacingScale, 0.001f)
    }
}
