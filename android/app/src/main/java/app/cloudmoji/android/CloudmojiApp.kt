package app.cloudmoji.android

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.SettingsRepository
import app.cloudmoji.android.model.AppAccessPolicy
import app.cloudmoji.android.model.CountRound
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.model.Settings
import app.cloudmoji.android.model.narrowedCountables
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.platform.StubEntitlementStore
import app.cloudmoji.android.ui.MiniAppPlaceholder
import app.cloudmoji.android.ui.ParentPlaceholder
import app.cloudmoji.android.ui.common.AdaptiveShell
import app.cloudmoji.android.ui.count.CountScreen
import app.cloudmoji.android.ui.launcher.LauncherScreen
import app.cloudmoji.android.ui.words.WordsScreen
import kotlinx.coroutines.launch

private const val LauncherRoute = "launcher"
private const val ParentRoute = "parents"

/**
 * The app's one composition root: the launcher/mini-app/parent-door route
 * switch.
 *
 * The real platform stack — [EmojiRepository], [SettingsRepository],
 * [StubEntitlementStore], the [SpeechController] stack, and Words' own
 * `WordsViewModel`/`MascotMoodMachine` — is built exactly once *for the
 * whole process*, not by this composable: see [CloudmojiApplication], which
 * a rotation-triggered Activity recreation does not tear down the way a
 * `remember {}` here would. This function's own job is thinner than it used
 * to be as a result — read the already-built stack off
 * `LocalContext.current.applicationContext`, resolve the two per-composition
 * derived values ([effectiveLanguage]/`availableLanguages`) through
 * [AppAccessPolicy], and hand already-filtered state down: a screen never
 * decides what it is allowed to show or hear.
 */
@Composable
fun CloudmojiApp() {
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as CloudmojiApplication }
    var route by rememberSaveable { mutableStateOf(LauncherRoute) }

    val settings by application.settingsRepository.settings.collectAsState(initial = Settings.default())
    val isUnlocked by application.entitlementStore.isUnlocked.collectAsState()
    val accessPolicy = AppAccessPolicy(hasFullAccess = isUnlocked)

    val scope = rememberCoroutineScope()

    val effectiveLanguage = accessPolicy.effectiveLanguage(settings.language)
    val availableLanguages = application.repository.languages.filter {
        it.id in accessPolicy.allowedLanguages(settings.enabledLanguages)
    }
    // Count mode's own catalogue, narrowed to the categories the parent left
    // enabled — the Count analogue of `WordsScreen`'s `buildSections`. Passed
    // down already-filtered, per the Task 6 brief's "a screen never decides
    // what it is allowed to show" rule.
    val countables = remember(application.repository, settings.enabledCategories) {
        narrowedCountables(application.repository, settings.enabledCategories)
    }
    val onCycleLanguage: () -> Unit = {
        val next = Language.next(after = effectiveLanguage, enabled = availableLanguages.map { it.id })
        scope.launch { application.settingsRepository.setLanguage(next) }
        Unit
    }

    // Opens a mini-app. Count mode's own round is reset to a fresh one on
    // every entry here — see `ui/count/CountScreen.kt`'s own doc for why
    // that differs from Words' "persists across a visit to the launcher and
    // back" trade-off. This closure fires only on a genuine navigation
    // (a launcher tile tap); `route` itself survives rotation via
    // `rememberSaveable` above without ever calling back through here, so a
    // rotation mid-round never re-triggers this reset.
    val onOpenApp: (MiniApp) -> Unit = { app ->
        if (app == MiniApp.Count) {
            application.speechController.cancelAll()
            application.countMoodMachine.reset()
            application.countViewModel.startRound(countables, CountRound.firstTarget(settings.countRange))
        }
        route = app.route
    }

    BackHandler(enabled = route != LauncherRoute) {
        route = LauncherRoute
    }

    AdaptiveShell {
        when {
            route == LauncherRoute -> LauncherScreen(
                apps = accessPolicy.visibleMiniApps(),
                language = effectiveLanguage,
                onOpen = onOpenApp,
                onParent = { route = ParentRoute },
            )

            route == ParentRoute -> ParentPlaceholder(
                onHome = { route = LauncherRoute },
            )

            else -> {
                val app = MiniApp.fromRoute(route)
                if (app != null && accessPolicy.canUse(app)) {
                    when (app) {
                        MiniApp.Words -> WordsScreen(
                            repository = application.repository,
                            enabledCategories = settings.enabledCategories,
                            language = effectiveLanguage,
                            muted = settings.muted,
                            availableLanguages = availableLanguages,
                            speechController = application.speechController,
                            mascotMoodMachine = application.mascotMoodMachine,
                            wordsViewModel = application.wordsViewModel,
                            onSetMuted = { muted -> scope.launch { application.settingsRepository.setMuted(muted) } },
                            onCycleLanguage = onCycleLanguage,
                            onHome = { route = LauncherRoute },
                            onParent = { route = ParentRoute },
                        )

                        MiniApp.Count -> CountScreen(
                            countables = countables,
                            countRange = settings.countRange,
                            language = effectiveLanguage,
                            muted = settings.muted,
                            availableLanguages = availableLanguages,
                            grammar = application.countingGrammar,
                            speechController = application.speechController,
                            hapticFeedback = application.hapticFeedback,
                            moodMachine = application.countMoodMachine,
                            viewModel = application.countViewModel,
                            onSetMuted = { muted -> scope.launch { application.settingsRepository.setMuted(muted) } },
                            onCycleLanguage = onCycleLanguage,
                            onHome = { route = LauncherRoute },
                            onParent = { route = ParentRoute },
                        )

                        else -> MiniAppPlaceholder(
                            app = app,
                            language = effectiveLanguage,
                            onHome = { route = LauncherRoute },
                        )
                    }
                } else {
                    LauncherScreen(
                        apps = accessPolicy.visibleMiniApps(),
                        language = effectiveLanguage,
                        onOpen = onOpenApp,
                        onParent = { route = ParentRoute },
                    )
                }
            }
        }
    }
}
