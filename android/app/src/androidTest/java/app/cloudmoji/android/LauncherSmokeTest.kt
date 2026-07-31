package app.cloudmoji.android

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun lockedLauncherShowsFreeAppsAndNoPremiumApps() {
        composeRule.onNodeWithTag("launcher").assertExists()
        composeRule.onNodeWithTag("launcher-tile-words").assertExists()
        composeRule.onNodeWithTag("launcher-tile-count").assertExists()
        composeRule.onNodeWithTag("launcher-tile-photos").assertDoesNotExist()
    }

    @Test
    fun cloudHomeReturnsFromWordsToLauncher() {
        composeRule.onNodeWithTag("launcher-tile-words").performClick()
        composeRule.onNodeWithTag("mini-app-words").assertExists()
        composeRule.onNodeWithTag("cloud-home").performClick()
        composeRule.onNodeWithTag("launcher").assertExists()
    }
}

