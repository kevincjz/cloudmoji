package app.cloudmoji.android.ui.parents

import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from iOS `AppModel.canDisableLanguage`/`canDisableCategory` — the
 * rule that greys out the last enabled switch in the Grown-ups panel rather
 * than letting a tap trigger `SettingsRepository`'s "empty means all of
 * them" recovery, which would otherwise snap every language or category back
 * on the instant the last one goes off.
 */
class SettingsControlsTest {

    @Test
    fun `the sole enabled language cannot be disabled`() {
        assertFalse(
            SettingsControls.canDisableLanguage(setOf(Language.Chinese), Language.Chinese),
        )
    }

    @Test
    fun `a language is disableable when at least one other language is enabled`() {
        assertTrue(
            SettingsControls.canDisableLanguage(
                setOf(Language.English, Language.Chinese),
                Language.English,
            ),
        )
    }

    @Test
    fun `a language that is already off is still reported disableable — greying is not a one-way trap`() {
        // Mirrors the iOS UI test's assertion that the four already-off
        // languages remain switchable back on: this predicate only speaks to
        // whether flipping *this* id off would empty the set, and a language
        // that is already off is never the one keeping the set non-empty.
        assertTrue(
            SettingsControls.canDisableLanguage(setOf(Language.Chinese), Language.English),
        )
    }

    @Test
    fun `every language is disableable when all five are enabled`() {
        Language.entries.forEach { language ->
            assertTrue(SettingsControls.canDisableLanguage(Language.entries.toSet(), language))
        }
    }

    @Test
    fun `the sole enabled category cannot be disabled`() {
        assertFalse(
            SettingsControls.canDisableCategory(setOf(Category.Animals), Category.Animals),
        )
    }

    @Test
    fun `a category is disableable when at least one other category is enabled`() {
        assertTrue(
            SettingsControls.canDisableCategory(
                setOf(Category.Fruits, Category.Animals),
                Category.Fruits,
            ),
        )
    }

    @Test
    fun `every category is disableable when all eight are enabled`() {
        Category.entries.forEach { category ->
            assertTrue(SettingsControls.canDisableCategory(Category.entries.toSet(), category))
        }
    }
}
