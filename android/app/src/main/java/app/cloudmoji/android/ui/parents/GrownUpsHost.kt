package app.cloudmoji.android.ui.parents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.cloudmoji.android.model.AppAccessPolicy
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.CategoryTab
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta
import app.cloudmoji.android.model.Settings

private const val DestinationPanel = "panel"
private const val DestinationAbout = "about"
private const val DestinationFullCloudmoji = "full"
private const val DestinationTutorial = "tutorial"

/**
 * The whole Grown-ups area behind the gate: the panel itself, plus the three
 * screens it can push ([AboutScreen], [FullCloudmojiScreen],
 * [TutorialScreen]). A raw-string internal destination, mirroring the same
 * pattern `CloudmojiApp.kt` already uses for its own top-level `route` — this
 * app has no navigation library, and a second one would be inconsistent with
 * the first for no benefit.
 *
 * [onHome] is *only* reachable from [DestinationPanel] (the "Done" button) —
 * every sub-screen's own back control returns to the panel first, via the
 * [BackHandler] below and each screen's own `onBack`. That is also what the
 * hardware/gesture back button does: composed after `CloudmojiApp.kt`'s own
 * route-level `BackHandler`, this one is the more-recently-registered
 * callback and therefore fires first while any sub-screen is open, popping
 * to the panel; only a second press (with `destination == panel`, this
 * handler disabled) reaches the outer one and leaves Grown-ups entirely.
 */
@Composable
fun GrownUpsHost(
    settings: Settings,
    accessPolicy: AppAccessPolicy,
    allLanguages: List<LanguageMeta>,
    availableLanguages: List<LanguageMeta>,
    categories: List<CategoryTab>,
    effectiveLanguage: Language,
    isUnlocked: Boolean,
    onSetMuted: (Boolean) -> Unit,
    onSetEnabledLanguages: (Set<Language>) -> Unit,
    onSetLanguage: (Language) -> Unit,
    onSetEnabledCategories: (Set<Category>) -> Unit,
    onSetCountRange: (IntRange) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(DestinationPanel) }

    BackHandler(enabled = destination != DestinationPanel) {
        destination = DestinationPanel
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            DestinationAbout -> AboutScreen(onBack = { destination = DestinationPanel })
            DestinationFullCloudmoji -> FullCloudmojiScreen(
                isUnlocked = isUnlocked,
                onBack = { destination = DestinationPanel },
            )
            DestinationTutorial -> TutorialScreen(onBack = { destination = DestinationPanel })
            else -> GrownUpsScreen(
                settings = settings,
                accessPolicy = accessPolicy,
                allLanguages = allLanguages,
                availableLanguages = availableLanguages,
                categories = categories,
                effectiveLanguage = effectiveLanguage,
                isUnlocked = isUnlocked,
                onSetMuted = onSetMuted,
                onSetEnabledLanguages = onSetEnabledLanguages,
                onSetLanguage = onSetLanguage,
                onSetEnabledCategories = onSetEnabledCategories,
                onSetCountRange = onSetCountRange,
                onOpenFullCloudmoji = { destination = DestinationFullCloudmoji },
                onOpenAbout = { destination = DestinationAbout },
                onOpenTutorial = { destination = DestinationTutorial },
                onDone = onHome,
            )
        }
    }
}
