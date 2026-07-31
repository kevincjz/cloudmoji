package app.cloudmoji.android.ui.music

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * The child-facing touch-target contract (`CLAUDE.md` rule 1 /
 * `conventions.md`) for `InstrumentPad` — Music's own floor is 72dp, not the
 * general 64dp minimum, see `InstrumentPadMetrics.minimumSide` — plus a
 * smoke check that the TalkBack `onClick` semantics action reaches
 * [InstrumentPad]'s `onStrike` the same way a real double-tap would. Mirrors
 * the touch-target intent of `ios/Cloudmoji/CloudmojiTests/AudioTests.swift`'s
 * `padsNeverShrinkPastTheFloor` and this project's own `CountChildTargetsTest`.
 *
 * **Not runnable in this environment** — no emulator/device is available
 * here (`connectedAndroidTest` cannot run; see `conventions.md`). This file
 * only needs to *compile* (`./gradlew :app:compileDebugAndroidTestKotlin`),
 * which was confirmed as part of this task. Run it once a device is
 * available, before trusting it.
 */
class MusicChildTargetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    // `InstrumentPadMetrics.side()`'s own floor rule is pure `Dp` arithmetic
    // — no Compose runtime needed — so it lives in the JVM test source set
    // instead of here: `app/src/test/java/.../ui/music/InstrumentPadMetricsTest.kt`.
    // It actually executes in this environment, unlike everything below.

    @Test
    fun aPadMeetsTheChildTargetFloorAndReachesOnStrikeViaAccessibilityClick() {
        var struck = false
        composeRule.setContent {
            InstrumentPad(index = 0, side = InstrumentPadMetrics.minimumSide, onStrike = { struck = true })
        }

        composeRule.onNodeWithTag("pad-0")
            .assertWidthIsAtLeast(72.dp)
            .assertHeightIsAtLeast(72.dp)
            .assertHasClickAction()
            .performClick()

        assert(struck) { "the onClick semantics action must reach onStrike" }
    }

    @Test
    fun aLargerExpandedPadStillMeetsTheFloor() {
        composeRule.setContent {
            InstrumentPad(index = 3, side = InstrumentPadMetrics.maximumPadSide, onStrike = {})
        }

        composeRule.onNodeWithTag("pad-3")
            .assertWidthIsAtLeast(72.dp)
            .assertHeightIsAtLeast(72.dp)
    }
}
