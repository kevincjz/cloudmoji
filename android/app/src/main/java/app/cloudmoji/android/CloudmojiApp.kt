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
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.model.Settings
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.platform.StubEntitlementStore
import app.cloudmoji.android.ui.MiniAppPlaceholder
import app.cloudmoji.android.ui.ParentPlaceholder
import app.cloudmoji.android.ui.common.AdaptiveShell
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

    BackHandler(enabled = route != LauncherRoute) {
        route = LauncherRoute
    }

    AdaptiveShell {
        when {
            route == LauncherRoute -> LauncherScreen(
                apps = accessPolicy.visibleMiniApps(),
                language = effectiveLanguage,
                onOpen = { route = it.route },
                onParent = { route = ParentRoute },
            )

            route == ParentRoute -> ParentPlaceholder(
                onHome = { route = LauncherRoute },
            )

            else -> {
                val app = MiniApp.fromRoute(route)
                if (app != null && accessPolicy.canUse(app)) {
                    if (app == MiniApp.Words) {
                        WordsScreen(
                            repository = application.repository,
                            enabledCategories = settings.enabledCategories,
                            language = effectiveLanguage,
                            muted = settings.muted,
                            availableLanguages = availableLanguages,
                            speechController = application.speechController,
                            mascotMoodMachine = application.mascotMoodMachine,
                            wordsViewModel = application.wordsViewModel,
                            onSetMuted = { muted -> scope.launch { application.settingsRepository.setMuted(muted) } },
                            onCycleLanguage = {
                                val next = Language.next(
                                    after = effectiveLanguage,
                                    enabled = availableLanguages.map { it.id },
                                )
                                scope.launch { application.settingsRepository.setLanguage(next) }
                            },
                            onHome = { route = LauncherRoute },
                            onParent = { route = ParentRoute },
                        )
                    } else {
                        MiniAppPlaceholder(
                            app = app,
                            language = effectiveLanguage,
                            onHome = { route = LauncherRoute },
                        )
                    }
                } else {
                    LauncherScreen(
                        apps = accessPolicy.visibleMiniApps(),
                        language = effectiveLanguage,
                        onOpen = { route = it.route },
                        onParent = { route = ParentRoute },
                    )
                }
            }
        }
    }
}
