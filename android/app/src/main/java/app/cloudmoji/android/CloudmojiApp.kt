package app.cloudmoji.android

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import app.cloudmoji.android.data.EmojiCatalogException
import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.SettingsRepository
import app.cloudmoji.android.data.settingsDataStore
import app.cloudmoji.android.model.AppAccessPolicy
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.model.Settings
import app.cloudmoji.android.platform.AndroidAudioFocusSystem
import app.cloudmoji.android.platform.AndroidSpeechEngine
import app.cloudmoji.android.platform.AudioFocusOwner
import app.cloudmoji.android.platform.CoroutineSpeechWatchdogScheduler
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.platform.StubEntitlementStore
import app.cloudmoji.android.platform.VoiceResolver
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
 * switch, and the **only** place the real platform stack is constructed.
 *
 * Everything here is built exactly once — simple manual DI, no framework —
 * and passed down as already-filtered state, per the Task 6 brief: a screen
 * never decides what it is allowed to show or hear, it consumes what
 * [AppAccessPolicy] and [SettingsRepository] have already resolved.
 * [EmojiRepository], [SettingsRepository], [StubEntitlementStore] and the
 * [SpeechController] stack are shared across every future mini-app (Task 7+)
 * exactly as Words uses them here; a screen-local machine like Words' own
 * mascot mood is instead owned by that screen — see `WordsScreen`'s doc.
 */
@Composable
fun CloudmojiApp() {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(LauncherRoute) }

    val repository = remember(context) {
        try {
            EmojiRepositoryLoader.fromAssets(context)
        } catch (e: EmojiCatalogException) {
            // A missing or malformed bundled resource is a build error, not
            // a runtime path — but the child must never see a crash, so an
            // empty repository is the degraded case rather than a trap.
            EmojiRepository.empty
        }
    }

    val settingsRepository = remember(context) { SettingsRepository(context.settingsDataStore) }
    val settings by settingsRepository.settings.collectAsState(initial = Settings.default())

    val entitlementStore = remember { StubEntitlementStore() }
    val isUnlocked by entitlementStore.isUnlocked.collectAsState()
    val accessPolicy = AppAccessPolicy(hasFullAccess = isUnlocked)

    val scope = rememberCoroutineScope()
    val currentSettings = rememberUpdatedState(settings)

    val speechController = remember(repository, context) {
        val resolver = VoiceResolver(repository.languages)
        val focusOwner = AudioFocusOwner(AndroidAudioFocusSystem(context))
        val engine = AndroidSpeechEngine(context, focusOwner)
        val watchdog = CoroutineSpeechWatchdogScheduler(scope)
        SpeechController(resolver, engine, watchdog, isMuted = { currentSettings.value.muted })
    }

    val effectiveLanguage = accessPolicy.effectiveLanguage(settings.language)
    val availableLanguages = repository.languages.filter {
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
                            repository = repository,
                            enabledCategories = settings.enabledCategories,
                            language = effectiveLanguage,
                            muted = settings.muted,
                            availableLanguages = availableLanguages,
                            speechController = speechController,
                            onSetMuted = { muted -> scope.launch { settingsRepository.setMuted(muted) } },
                            onCycleLanguage = {
                                val next = Language.next(
                                    after = effectiveLanguage,
                                    enabled = availableLanguages.map { it.id },
                                )
                                scope.launch { settingsRepository.setLanguage(next) }
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
