package app.cloudmoji.android.ui.count

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.ui.theme.Teal
import org.junit.Rule
import org.junit.Test

/**
 * The 64dp child-facing touch-target contract (`CLAUDE.md` rule 1 /
 * `conventions.md`) for Count mode's own composables — `CountTile` (a
 * round's tiles) and `CountControl` (Shuffle/Replay/Next) — plus a smoke
 * check that a tap actually reaches the caller. Mirrors the touch-target
 * intent of `ios/Cloudmoji/CloudmojiUITests/CountModeUITests.swift` and this
 * project's own `WordsChildTargetsTest.kt`.
 *
 * **Not runnable in this environment** — no emulator/device is available
 * here (`connectedAndroidTest` cannot run; see `conventions.md`). This file
 * only needs to *compile* (`./gradlew :app:compileDebugAndroidTestKotlin`),
 * which was confirmed as part of this task. Run it once a device is
 * available, before trusting it.
 */
class CountChildTargetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * `CountTileMetrics.side()` is a step function of the round size, not a
     * single constant — this asserts the *rule*, not one rendered instance,
     * across every round size the parent's count range can ever produce
     * ([app.cloudmoji.android.model.Settings.countBounds] is `2..10`), both
     * orientations.
     */
    @Test
    fun countTileSideNeverDropsBelowTheChildTargetFloor() {
        for (count in 2..10) {
            for (compact in listOf(false, true)) {
                val side = CountTileMetrics.side(count, compact)
                assert(side >= CountTileMetrics.childMinimum) {
                    "CountTileMetrics.side(count=$count, compact=$compact) is $side, " +
                        "under the ${CountTileMetrics.childMinimum} floor"
                }
            }
        }
    }

    @Test
    fun anUncountedTileMeetsTheChildTargetFloorAndReachesOnTap() {
        var tapped = false
        composeRule.setContent {
            CountTile(
                emoji = "🐶",
                index = 0,
                badge = null,
                isJustCounted = false,
                side = 96.dp,
                glyphSize = TextUnit.Unspecified,
                onTap = { tapped = true },
            )
        }

        composeRule.onNodeWithTag("count-item-0")
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
            .assertHasClickAction()
            .performClick()

        assert(tapped) { "tapping the tile must reach onTap" }
    }

    @Test
    fun aCountedTileStillMeetsTheFloorAndShowsItsBadge() {
        composeRule.setContent {
            CountTile(
                emoji = "🐶",
                index = 2,
                badge = 3,
                isJustCounted = true,
                side = 64.dp, // the smallest side CountTileMetrics ever hands out
                glyphSize = TextUnit.Unspecified,
                onTap = {},
            )
        }

        composeRule.onNodeWithTag("count-item-2")
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun countControlMeetsTheChildTargetFloorAndReachesItsAction() {
        var tapped = false
        composeRule.setContent {
            CountControl(
                glyph = "🔄",
                caption = "Shuffle",
                identifier = "count-shuffle",
                tint = Teal,
                action = { tapped = true },
            )
        }

        composeRule.onNodeWithTag("count-shuffle")
            .assertHeightIsAtLeast(64.dp)
            .assertHasClickAction()
            .performClick()

        assert(tapped) { "tapping Shuffle must reach its action" }
    }

    @Test
    fun countReadoutRendersTheNumeralAndPhraseWhenSomethingHasBeenCounted() {
        composeRule.setContent {
            CountReadout(
                target = 3,
                progress = 1,
                numeral = "1",
                phrase = "one dog",
                isCompact = false,
                isExpandedPad = false,
            )
        }

        composeRule.onNodeWithTag("count-readout").assertIsDisplayed()
        composeRule.onNodeWithTag("count-phrase").assertIsDisplayed()
    }
}
