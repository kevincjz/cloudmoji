package app.cloudmoji.android.ui.mascot

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import app.cloudmoji.android.model.MascotMood
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for the review finding on `CloudMascot.kt`:
 * `Modifier.semantics {}` defaults `mergeDescendants = false`, which *adds* a
 * labeled node rather than collapsing descendants — so the sparkle glyphs
 * rendered as separate `Text` composables during excited/beaming would each
 * surface their own unlabeled node to TalkBack alongside the mascot's own
 * "Cloudmoji" label. `Modifier.clearAndSetSemantics` (the fix, and the direct
 * analogue of iOS's `.accessibilityElement(children: .ignore)`) walls the
 * whole subtree off instead.
 *
 * **Not runnable in this environment** — no emulator/device is available
 * here (`connectedAndroidTest` cannot run; see `conventions.md`). This class
 * compiles under `:app:compileDebugAndroidTestKotlin` but has not been
 * executed, and its assertions are therefore unverified beyond that: correct
 * per my understanding of how `clearAndSetSemantics` prunes the semantics
 * tree at traversal time (before iOS's `accessibilityElement`'s Compose
 * analogue existed to sit next to a runnable JVM test), not proven by a
 * fail-then-pass run the way `MascotMoodMachineTest` was. Run this once a
 * device is available, before trusting it.
 */
class CloudMascotSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun beamingMascotExposesOneLabeledNodeNotEachSparkleGlyph() {
        composeRule.setContent {
            CloudMascot(mood = MascotMood.Beaming)
        }

        composeRule.onNodeWithTag("cloud-mascot").assertContentDescriptionEquals("Cloudmoji")

        // Beaming shows 5 sparkle glyphs (2 base + 3 beaming-only), each its
        // own `Text` composable. `clearAndSetSemantics` on the mascot's outer
        // `Box` must keep every one of them out of the reachable semantics
        // tree — none of the glyphs below should be independently
        // discoverable by their own text.
        composeRule.onAllNodesWithText("✨").assertCountEquals(0)
        composeRule.onAllNodesWithText("⭐").assertCountEquals(0)
        composeRule.onAllNodesWithText("🌟").assertCountEquals(0)
    }

    @Test
    fun excitedMascotExposesOneLabeledNodeNotTheSparkleGlyphs() {
        composeRule.setContent {
            CloudMascot(mood = MascotMood.Excited)
        }

        composeRule.onNodeWithTag("cloud-mascot").assertContentDescriptionEquals("Cloudmoji")
        composeRule.onAllNodesWithText("✨").assertCountEquals(0)
    }

    @Test
    fun happyMascotHasNoSparklesToBeginWith() {
        // A mood with zero sparkles (nothing beneath the Box to worry about)
        // still gets the plain content description — the fix must not have
        // broken the base case.
        composeRule.setContent {
            CloudMascot(mood = MascotMood.Happy)
        }

        composeRule.onNodeWithTag("cloud-mascot").assertContentDescriptionEquals("Cloudmoji")
    }
}
