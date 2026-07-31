package app.cloudmoji.android.ui.words

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.CategoryTab
import app.cloudmoji.android.model.EmojiEntry
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.TypedEmoji
import app.cloudmoji.android.model.WordsViewModel
import org.junit.Rule
import org.junit.Test

/**
 * The 64dp child-facing touch-target contract (`CLAUDE.md` rule 1 /
 * `conventions.md`) for Words mode's own composables, plus the grid/chip/
 * typing semantics the Task 6 brief asks for. Mirrors the touch-target intent
 * of `ios/Cloudmoji/CloudmojiUITests/WordsModeUITests.swift`.
 *
 * **Not runnable in this environment** — no emulator/device is available
 * here (`connectedAndroidTest` cannot run; see `conventions.md`). Unlike the
 * first version of this file (Task 6), this class — and `LauncherSmokeTest.kt`
 * — now **compile** (`./gradlew :app:compileDebugAndroidTestKotlin`
 * verified green as part of this fix): deleting `LauncherSmokeTest.kt`'s two
 * bogus `assertExists`/`assertDoesNotExist` top-level imports, found while
 * fixing the review's third finding, turned out to be the whole pre-existing
 * Task 9 blocker. Compiling is not running, though — none of these
 * assertions have been executed against a real semantics tree. Run this
 * once a device is available, before trusting it.
 */
class WordsChildTargetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val apple = EmojiEntry(emoji = "🍎", category = Category.Fruits, en = "apple", zh = "苹果", ms = "epal", ja = "りんご", tl = "mansanas")
    private val fruitsTab = CategoryTab(id = "fruits", icon = "🍎", labels = mapOf("en" to "Fruits"))

    @Test
    fun emojiTileMeetsTheChildTargetFloorAndReportsTheSpokenWord() {
        var tapped = false
        composeRule.setContent {
            EmojiTile(entry = apple, isBouncing = false, word = "apple", isExpandedPad = false, onTap = { tapped = true })
        }

        composeRule.onNodeWithTag("emoji-🍎")
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
            .assertHasClickAction()
            .performClick()

        assert(tapped) { "tapping the tile must reach onTap" }
    }

    @Test
    fun categoryChipInTheStripMeetsTheChildTargetFloor() {
        var selected: CategoryTab? = null
        composeRule.setContent {
            CategoryChips(
                tabs = listOf(fruitsTab),
                selectedId = "fruits",
                labelFor = { it.label(Language.English) },
                layout = CategoryChipLayout.Strip,
                isExpandedPad = false,
                onSelect = { selected = it },
            )
        }

        composeRule.onNodeWithTag("cat-fruits")
            .assertHeightIsAtLeast(64.dp)
            .assertHasClickAction()
            .performClick()

        assert(selected == fruitsTab) { "the tapped chip must be reported back, not a filtered selection" }
    }

    @Test
    fun typingRowControlsAndTypedEmojisMeetTheChildTargetFloor() {
        val typed = listOf(TypedEmoji(id = 0, emoji = "🍎", word = "apple"))
        composeRule.setContent {
            TypingRow(
                typed = typed,
                muted = false,
                language = Language.English,
                onReplay = {},
                onDelete = {},
                onClear = {},
                onTapTyped = {},
            )
        }

        composeRule.onNodeWithTag("typed-emoji-0").assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithTag("replay-btn").assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithTag("delete-btn").assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithTag("clear-btn").assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun theRowScrollsToTheNewestEmojiEvenPastThe50Cap() {
        // Regression coverage for the review finding on `TypingRow.kt`: the
        // auto-scroll effect used to key on `typed.size`, which
        // `WordsViewModel.tapEmoji`'s `takeLast(MAX_TYPED)` pins at 50 once
        // the cap is reached — past that point the effect never restarted,
        // and the emoji a child just tapped landed off-screen. It must now
        // key on the newest item's own identity instead.
        //
        // A narrow fixed width forces the strip to actually scroll rather
        // than fitting every glyph on screen at once, which would make this
        // assertion trivially true regardless of the fix.
        val typed = (0 until WordsViewModel.MAX_TYPED + 3).map { id ->
            TypedEmoji(id = id.toLong(), emoji = "🍎", word = "apple $id")
        }
        composeRule.setContent {
            TypingRow(
                typed = typed,
                muted = true, // no replay/delete/clear competing for the narrow width
                language = Language.English,
                onReplay = {},
                onDelete = {},
                onClear = {},
                onTapTyped = {},
                modifier = Modifier.width(240.dp),
            )
        }

        val newestId = typed.last().id
        composeRule.onNodeWithTag("typed-emoji-$newestId").assertIsDisplayed()
    }

    @Test
    fun aMutedRowHidesReplayRatherThanShowingADeadButton() {
        val typed = listOf(TypedEmoji(id = 0, emoji = "🍎", word = "apple"))
        composeRule.setContent {
            TypingRow(
                typed = typed,
                muted = true,
                language = Language.English,
                onReplay = {},
                onDelete = {},
                onClear = {},
                onTapTyped = {},
            )
        }

        // No failure states, CLAUDE.md rule 4: a muted replay button that
        // does nothing must not exist at all.
        composeRule.onNodeWithTag("replay-btn").assertDoesNotExist()
        composeRule.onNodeWithTag("delete-btn").assertHasClickAction()
    }

    @Test
    fun anEmptyRowShowsTheLocalizedPlaceholderInsteadOfControls() {
        composeRule.setContent {
            TypingRow(
                typed = emptyList(),
                muted = false,
                language = Language.English,
                onReplay = {},
                onDelete = {},
                onClear = {},
                onTapTyped = {},
            )
        }

        composeRule.onNodeWithText("Tap emojis below! 👇").assertExists()
        composeRule.onNodeWithTag("clear-btn").assertDoesNotExist()
    }
}
