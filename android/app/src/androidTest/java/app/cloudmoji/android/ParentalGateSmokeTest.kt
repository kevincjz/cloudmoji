package app.cloudmoji.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cloudmoji.android.ui.parents.GateChallenge
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The parent door, driven end to end — mirrors
 * `ios/Cloudmoji/CloudmojiUITests/ParentalGateUITests.swift`'s
 * `testAWrongAnswerDoesNotOpenSettingsAndTheRightOneDoes` /
 * `testCancelClosesTheGateWithoutOpeningSettings`. `GateAttemptTest` proves
 * `GateAttempt.submit` rejects a wrong answer as pure logic; it cannot prove
 * the **screen** consults it — a gate whose Continue button is wired
 * straight to `onPass` would pass every JVM test in this project while
 * opening for anything, which is exactly the gap this instrumented suite
 * closes.
 *
 * **Not run in this environment** — see the Task 8 brief and
 * `conventions.md`: no emulator/device is available, so this only proves it
 * *compiles* (`:app:compileDebugAndroidTestKotlin`), the same status every
 * other `androidTest` suite in this module carries.
 *
 * The first challenge after a fresh app launch is always `GateChallenge.at(0)`
 * — `7 × 8 = 56` — since `CloudmojiApp`'s `gateIndex` starts at zero and
 * nothing in this test opens the gate before it does; hard-coding 56 here
 * (rather than reading the question off screen, as the iOS suite does) is
 * safe for exactly that reason and keeps this test simple.
 */
@RunWith(AndroidJUnit4::class)
class ParentalGateSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingGrownUpsOpensTheGate() {
        composeRule.onNodeWithTag("launcher-parent").performClick()
        composeRule.onNodeWithTag("parental-gate").assertIsDisplayed()
        composeRule.onNodeWithTag("gate-question").assertIsDisplayed()
    }

    @Test
    fun aWrongAnswerDoesNotOpenSettingsAndTheRightOneDoes() {
        composeRule.onNodeWithTag("launcher-parent").performClick()

        composeRule.onNodeWithTag("gate-input").performTextInput("55")
        composeRule.onNodeWithTag("gate-submit").performClick()

        composeRule.onNodeWithTag("gate-error").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-panel").assertDoesNotExist()
        // Still open — a wrong answer must not have dismissed the gate itself.
        composeRule.onNodeWithTag("gate-question").assertIsDisplayed()

        composeRule.onNodeWithTag("gate-input").performTextInput(
            GateChallenge.at(0).answer.toString(),
        )
        composeRule.onNodeWithTag("gate-submit").performClick()

        composeRule.onNodeWithTag("settings-panel").assertIsDisplayed()
    }

    @Test
    fun cancelClosesTheGateWithoutOpeningSettings() {
        composeRule.onNodeWithTag("launcher-parent").performClick()
        composeRule.onNodeWithTag("gate-cancel").performClick()

        composeRule.onNodeWithTag("parental-gate").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-panel").assertDoesNotExist()
        composeRule.onNodeWithTag("launcher").assertIsDisplayed()
    }

    @Test
    fun theGateReachedFromInsideWordsModeAlsoBlocksSettings() {
        composeRule.onNodeWithTag("launcher-tile-words").performClick()
        composeRule.onNodeWithTag("words-screen").assertIsDisplayed()

        composeRule.onNodeWithTag("parent-btn").performClick()
        composeRule.onNodeWithTag("parental-gate").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-panel").assertDoesNotExist()

        composeRule.onNodeWithTag("gate-input").performTextInput(
            GateChallenge.at(0).answer.toString(),
        )
        composeRule.onNodeWithTag("gate-submit").performClick()

        composeRule.onNodeWithTag("settings-panel").assertIsDisplayed()
    }

    @Test
    fun doneReturnsToTheLauncherAndFullCloudmojiHasNoPurchaseButton() {
        composeRule.onNodeWithTag("launcher-parent").performClick()
        composeRule.onNodeWithTag("gate-input").performTextInput(
            GateChallenge.at(0).answer.toString(),
        )
        composeRule.onNodeWithTag("gate-submit").performClick()
        composeRule.onNodeWithTag("settings-panel").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-plan-row").performClick()
        composeRule.onNodeWithTag("full-cloudmoji-panel").assertIsDisplayed()
        // StubEntitlementStore defaults unlocked (see its own doc), so this
        // device only ever exercises the "full-unlocked" branch of
        // FullCloudmojiScreen; the locked, no-purchase-button branch has no
        // JVM- or device-observable trigger until Play Billing exists, and is
        // covered by inspection instead — see the Task 8 report.
        composeRule.onNodeWithTag("full-unlocked").assertIsDisplayed()

        composeRule.onNodeWithTag("full-back").performClick()
        composeRule.onNodeWithTag("settings-done").performClick()
        composeRule.onNodeWithTag("launcher").assertIsDisplayed()
    }
}
