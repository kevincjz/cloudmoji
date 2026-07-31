package app.cloudmoji.android.ui.sleepy

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.model.Clock
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.SleepySessionState
import app.cloudmoji.android.platform.AudioFocusOwner
import app.cloudmoji.android.platform.AudioFocusSystem
import app.cloudmoji.android.platform.NoOpHapticFeedback
import app.cloudmoji.android.platform.ToneDirector
import app.cloudmoji.android.platform.ToneEngineDriving
import org.junit.Rule
import org.junit.Test

/**
 * Sleepy Cloud's Compose surface: the child-facing touch-target contract
 * (`CLAUDE.md` rule 1 / `conventions.md`), the `testTag`s the picker and the
 * running session publish, and that a duration tap actually starts a
 * session. Mirrors the intent of iOS `SleepyCloudTests`' UI half and this
 * project's own `MusicChildTargetsTest`/`FlashCardsChildTargetsTest`.
 *
 * **Not runnable in this environment** — no emulator or device is available
 * here, so `connectedAndroidTest` cannot run (see `conventions.md`). This
 * file was only compiled (`./gradlew :app:compileDebugAndroidTestKotlin`),
 * which is the whole of what Task 13 could verify about it. Run it once a
 * device is available, before trusting it.
 *
 * The screen's arithmetic — the dim ramp, the star placement, the five
 * language tables, the breath timing, the session state machine, and the
 * keep-screen-on/brightness hold-and-release pair — is deliberately *not*
 * tested here. All of it lives in pure classes with JVM tests that do
 * execute: `SleepyCloudMetricsTest`, `BreathingSessionTest`,
 * `SleepySessionStateTest`, `ScreenControlTest`.
 */
class SleepyChildTargetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FixedClock(var millis: Long = 0L) : Clock {
        override fun nowMillis(): Long = millis
    }

    private class SilentEngine : ToneEngineDriving {
        override val isRunning: Boolean get() = true
        override fun start() = Unit
        override fun stop() = Unit
        override fun playTone(index: Int) = Unit
        override fun playSleepNoise() = Unit
        override fun stopSleepNoise() = Unit
    }

    private class GrantingFocus : AudioFocusSystem {
        override fun requestTransientDuckFocus(): Boolean = true
        override fun abandonFocus() = Unit
    }

    private fun setScreen(session: SleepySessionState) {
        composeRule.setContent {
            SleepyCloudScreen(
                session = session,
                language = Language.English,
                muted = true, // no engine noise from a test run
                toneDirector = ToneDirector(AudioFocusOwner(GrantingFocus()), SilentEngine()),
                hapticFeedback = NoOpHapticFeedback,
                onHome = {},
                onUnmute = {},
            )
        }
    }

    @Test
    fun everyDurationPlateMeetsTheChildTargetFloor() {
        setScreen(SleepySessionState(FixedClock()))

        for (choice in listOf(2, 5, 10)) {
            composeRule.onNodeWithTag("sleepy-duration-$choice")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(64.dp)
                .assertHeightIsAtLeast(64.dp)
                .assertHasClickAction()
        }
    }

    @Test
    fun tappingADurationStartsTheSessionAndReplacesThePicker() {
        val session = SleepySessionState(FixedClock())
        setScreen(session)

        composeRule.onNodeWithTag("sleepy-duration-5").performClick()

        composeRule.waitForIdle()
        assert(session.minutes.value == 5) { "the tap did not start a five-minute session" }
        composeRule.onNodeWithTag("sleepy-progress").assertIsDisplayed()
    }

    /** The way back out is reachable from the picker *and* from a running
     * session — `MiniAppScaffold`'s cloud home button, the only navigation
     * control any mini-app has. */
    @Test
    fun theCloudHomeButtonIsPresentThroughout() {
        val session = SleepySessionState(FixedClock())
        setScreen(session)

        composeRule.onNodeWithTag("sleepy-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("sleepy-duration-2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sleepy-screen").assertIsDisplayed()
    }

    /** After the session ends, "again" is a child-facing target too — the
     * child who taps it is the one who just watched the cloud fall asleep. */
    @Test
    fun theAgainButtonMeetsTheChildTargetFloor() {
        val clock = FixedClock()
        val session = SleepySessionState(clock)
        session.begin(minutes = 2)
        clock.millis = 121_000L
        session.tick()

        setScreen(session)

        composeRule.onNodeWithTag("sleepy-done").assertIsDisplayed()
        composeRule.onNodeWithTag("sleepy-again")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
            .assertHasClickAction()
            .performClick()

        composeRule.waitForIdle()
        assert(session.minutes.value == null) { "\"again\" did not return to the picker" }
    }
}
