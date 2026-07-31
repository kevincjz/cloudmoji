package app.cloudmoji.android.ui.flashcards

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.model.Language
import org.junit.Rule
import org.junit.Test

/**
 * The Compose half of Flash Cards' contract: the child-facing touch-target
 * floor (`CLAUDE.md` rule 1 / `conventions.md`), the TalkBack label on a
 * choice (the emoji's **word**, not its Unicode name — a screen reader
 * announcing "grinning face with smiling eyes" while the cloud asks for
 * "happy" makes the question unanswerable by ear), and the fact that the
 * celebration window really does stop further taps.
 *
 * **Not runnable in this environment** — no emulator/device is available
 * here (`connectedAndroidTest` cannot run; see `conventions.md`). This file
 * only needs to *compile* (`./gradlew :app:compileDebugAndroidTestKotlin`),
 * which was confirmed as part of this task. Run it once a device is
 * available, before trusting it. The pure-`Dp` half of the same contract
 * lives in `app/src/test/.../ui/flashcards/FlashCardMetricsTest.kt` and does
 * execute here.
 */
class FlashCardsChildTargetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aChoiceMeetsTheChildTargetFloorAndCarriesItsWordAsTheLabel() {
        var tapped = false
        composeRule.setContent {
            ChoiceTile(
                emoji = "🐶",
                label = "dog",
                index = 0,
                side = FlashCardMetrics.choiceSide(compact = true),
                glyphSize = FlashCardMetrics.glyphSize(compact = true),
                isBouncing = false,
                isSolved = false,
                isAdvancing = false,
                onTap = { tapped = true },
            )
        }

        composeRule.onNodeWithTag("flash-choice-🐶")
            .assertWidthIsAtLeast(FlashCardMetrics.childMinimum)
            .assertHeightIsAtLeast(FlashCardMetrics.childMinimum)
            .assertHasClickAction()
            .performClick()

        assert(tapped) { "a choice tile's click action must reach onTap" }
        composeRule.onNodeWithContentDescription("dog").assertExists()
    }

    /** iOS `.disabled(isAdvancing)`: while a correct answer is being
     * celebrated, nothing else responds — the one moment in this mini-app
     * where a tap is deliberately ignored, so that the reward is not cut
     * short by the next question arriving early. */
    @Test
    fun aChoiceStopsRespondingWhileTheCelebrationRuns() {
        var tapped = false
        composeRule.setContent {
            ChoiceTile(
                emoji = "🐱",
                label = "cat",
                index = 1,
                side = FlashCardMetrics.choiceSide(compact = false),
                glyphSize = FlashCardMetrics.glyphSize(compact = false),
                isBouncing = false,
                isSolved = false,
                isAdvancing = true,
                onTap = { tapped = true },
            )
        }

        composeRule.onNodeWithTag("flash-choice-🐱").assertIsNotEnabled()
        assert(!tapped) { "a choice must not fire while a celebration is running" }
    }

    @Test
    fun theReplayButtonMeetsTheChildTargetFloorAndSpeaksItsCaption() {
        var replayed = false
        val caption = FlashCardsUiText.replay(Language.English)
        composeRule.setContent {
            ReplayButton(caption = caption, isExpandedPad = false, onTap = { replayed = true })
        }

        composeRule.onNodeWithTag("flash-replay")
            .assertHeightIsAtLeast(64.dp)
            .assertHasClickAction()
            .performClick()

        assert(replayed) { "the replay button's click action must reach onTap" }
        composeRule.onNodeWithText(caption).assertExists()
    }
}
