package app.cloudmoji.android.ui.parents

import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language

/**
 * Pure gating rules for the Grown-ups panel's own switches — mirrors iOS
 * `AppModel.canDisableLanguage`/`canDisableCategory`.
 *
 * `SettingsRepository` treats an empty enabled set as "all of them"
 * ([app.cloudmoji.android.model.Settings.cleanedLanguages] /
 * `cleanedCategories`), which is the right invariant but a baffling thing for
 * a parent to watch happen — every switch snapping back on the instant they
 * turn the last one off. The panel greys the last switch out instead of
 * letting a tap trigger that snap-back, using these two pure checks so the
 * rule lives in one place a JVM test can reach directly, with no Compose
 * `Switch` in the way.
 */
object SettingsControls {

    /** `false` only when [id] is the one language left in [enabled] — flipping
     * it off would leave the set empty, which [enabled] can never mean "no
     * restriction" for in practice, only "every language". */
    fun canDisableLanguage(enabled: Set<Language>, id: Language): Boolean =
        enabled != setOf(id)

    /** The category analogue of [canDisableLanguage]. */
    fun canDisableCategory(enabled: Set<Category>, id: Category): Boolean =
        enabled != setOf(id)
}
