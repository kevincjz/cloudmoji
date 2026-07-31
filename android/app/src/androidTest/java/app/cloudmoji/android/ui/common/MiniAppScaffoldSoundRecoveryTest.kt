package app.cloudmoji.android.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * The sound-recovery overlay [MiniAppScaffold] ports from iOS
 * `HostedMiniApp`'s `if app.showsSoundRecovery && model.settings.muted { SoundRecoveryButton { ... } }`
 * — the fix for the load-bearing gap a Task 10 review found (Music, and
 * every future `showsSoundRecovery` mini-app, had no way back to sound once
 * muted from another screen; see the Task 10 report's fix addendum).
 *
 * **Not runnable in this environment** — no emulator/device is available
 * here (`connectedAndroidTest` cannot run; see `conventions.md`). This file
 * only needs to *compile* (`./gradlew :app:compileDebugAndroidTestKotlin`),
 * which was confirmed as part of this fix. Run it once a device is
 * available, before trusting it.
 */
class MiniAppScaffoldSoundRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theRecoveryButtonAppearsOnlyWhenBothShowsSoundRecoveryAndMutedAreTrue() {
        composeRule.setContent {
            MiniAppScaffold(
                onHome = {},
                showsSoundRecovery = true,
                muted = true,
                onUnmute = {},
            ) {
                Box {}
            }
        }

        composeRule.onNodeWithTag("sound-recovery-btn")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun tappingTheRecoveryButtonReachesOnUnmute() {
        var unmuted = false
        composeRule.setContent {
            MiniAppScaffold(
                onHome = {},
                showsSoundRecovery = true,
                muted = true,
                onUnmute = { unmuted = true },
            ) {
                Box {}
            }
        }

        composeRule.onNodeWithTag("sound-recovery-btn").performClick()

        assert(unmuted) { "tapping the recovery button must reach onUnmute" }
    }

    @Test
    fun theButtonIsAbsentWhenTheScreenHasItsOwnMuteControl() {
        // Words/Count's own call sites never set `showsSoundRecovery`, so the
        // default (`false`) applies regardless of `muted` — mirrors iOS
        // leaving `.words`/`.count`/`.photos` out of `showsSoundRecovery`.
        composeRule.setContent {
            MiniAppScaffold(onHome = {}, muted = true) {
                Box {}
            }
        }

        composeRule.onNodeWithTag("sound-recovery-btn").assertDoesNotExist()
    }

    @Test
    fun theButtonIsAbsentWhenUnmuted() {
        composeRule.setContent {
            MiniAppScaffold(onHome = {}, showsSoundRecovery = true, muted = false) {
                Box {}
            }
        }

        composeRule.onNodeWithTag("sound-recovery-btn").assertDoesNotExist()
    }
}
