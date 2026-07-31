package app.cloudmoji.android.ui.music

import androidx.compose.ui.unit.dp
import app.cloudmoji.android.platform.ToneBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `InstrumentPadMetrics`'s own arithmetic — `side`/`tint`/`columns` are
 * plain `Dp`/list step functions with no Compose runtime or Android device
 * behind them, so — like `CountTileMetricsTest` — this runs on the plain
 * JVM, unlike the rest of `ui/music/`. Mirrors the floor/tint/column-count
 * assertions from iOS `AudioDirectorTests` (`padGridTransposes`,
 * `padsNeverShrinkPastTheFloor`, `everyPadHasATint`).
 */
class InstrumentPadMetricsTest {

    /**
     * **The floor is not a suggestion.** Mirrors iOS's own comment on
     * `InstrumentPadView.side`: a cramped screen overflows its grid by a
     * couple of points rather than shrinking a child-facing pad under
     * [InstrumentPadMetrics.minimumSide].
     *
     * Mutation proof: temporarily changed the floor comparison in `side()`
     * from `fitted > minimumSide` to always return `fitted` (dropping the
     * `max` behaviour). The cramped-screen assertion below failed (about
     * 62dp, under the 72dp floor) before the floor was restored.
     */
    @Test
    fun `pads never shrink below the preferred child size`() {
        val roomy = InstrumentPadMetrics.side(
            availableWidth = 375.dp,
            availableHeight = 700.dp,
            columns = 2,
            rows = 4,
            spacing = 8.dp,
        )
        assertTrue(roomy >= InstrumentPadMetrics.minimumSide)
        assertTrue(roomy <= 375.dp)

        val cramped = InstrumentPadMetrics.side(
            availableWidth = 320.dp,
            availableHeight = 240.dp,
            columns = 2,
            rows = 4,
            spacing = 8.dp,
        )
        assertTrue(
            "a cramped screen gave ${cramped} pads, under the ${InstrumentPadMetrics.minimumSide} floor",
            cramped >= InstrumentPadMetrics.minimumSide,
        )

        // Degenerate input must not divide by zero.
        assertEquals(
            InstrumentPadMetrics.minimumSide,
            InstrumentPadMetrics.side(availableWidth = 375.dp, availableHeight = 700.dp, columns = 0, rows = 0, spacing = 8.dp),
        )
    }

    /** Eight pads, eight colours, and the lookup wraps rather than
     * trapping. */
    @Test
    fun `every pad has a tint`() {
        assertEquals(ToneBuffer.pitches.size, InstrumentPadMetrics.tints.size)
        for (index in ToneBuffer.pitches.indices) {
            InstrumentPadMetrics.tint(index) // must not throw
        }
        // Past the end, which cannot happen from the view but must not trap.
        InstrumentPadMetrics.tint(99)
    }

    /** Sideways is the transpose of upright: the same eight pads fill
     * whichever axis there is more of. */
    @Test
    fun `the pad grid is 2 columns upright and 4 columns compact or expanded-landscape`() {
        assertEquals(2, InstrumentPadMetrics.columns(compact = false))
        assertEquals(4, InstrumentPadMetrics.columns(compact = true))
        assertEquals(2, InstrumentPadMetrics.columns(compact = false, isExpandedPad = true, isLandscape = false))
        assertEquals(4, InstrumentPadMetrics.columns(compact = false, isExpandedPad = true, isLandscape = true))
        // `compact` alone is enough, regardless of the pad/landscape flags.
        assertEquals(4, InstrumentPadMetrics.columns(compact = true, isExpandedPad = false, isLandscape = false))
    }
}
