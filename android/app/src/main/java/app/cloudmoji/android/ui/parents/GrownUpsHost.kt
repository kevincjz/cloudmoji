package app.cloudmoji.android.ui.parents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.cloudmoji.android.model.AppAccessPolicy
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.CategoryTab
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta
import app.cloudmoji.android.model.Settings

internal const val DestinationPanel = "panel"
internal const val DestinationAbout = "about"
internal const val DestinationFullCloudmoji = "full"
internal const val DestinationTutorial = "tutorial"

/**
 * Coerces a `destination` value read back from saved-instance state to
 * [DestinationPanel] unless it was already there — belt-and-braces alongside
 * `CloudmojiApp.kt`'s `sanitizeRestoredRoute`, which is the primary defence
 * (a restored [app.cloudmoji.android.ParentRoute] is itself coerced back to
 * the launcher, so this composable would not normally even be reached with
 * restored state). It stays a second, independent check rather than relying
 * on that alone: `rememberSaveable`'s `SaveableStateRegistry` can still hand
 * this composable a value saved under this exact slot the first time it is
 * *ever* composed after a restore — even if that first composition happens
 * later, after a legitimate fresh gate pass — and a fresh entry into the
 * Grown-ups area should always land on the panel, never skip straight to
 * [AboutScreen]'s outbound mail/browser intents.
 */
internal fun sanitizeRestoredDestination(raw: String): String =
    if (raw == DestinationPanel) raw else DestinationPanel

/** Applies [sanitizeRestoredDestination] at restore time — see
 * `CloudmojiApp.kt`'s `RouteSaver` for why `save` stays the identity. */
private val DestinationSaver: Saver<String, String> = Saver(
    save = { it },
    restore = { sanitizeRestoredDestination(it) },
)

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
    var destination by rememberSaveable(stateSaver = DestinationSaver) { mutableStateOf(DestinationPanel) }

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
