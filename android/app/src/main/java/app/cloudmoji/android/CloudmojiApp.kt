package app.cloudmoji.android

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.SettingsRepository
import app.cloudmoji.android.model.AppAccessPolicy
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.CountRound
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.model.Settings
import app.cloudmoji.android.model.narrowedCountables
import app.cloudmoji.android.model.narrowedEmojis
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.platform.StubEntitlementStore
import app.cloudmoji.android.platform.findActivity
import app.cloudmoji.android.platform.openAppSettings
import app.cloudmoji.android.ui.animals.AnimalsScreen
import app.cloudmoji.android.ui.animals.narrowedAnimals
import app.cloudmoji.android.ui.common.AdaptiveShell
import app.cloudmoji.android.ui.count.CountScreen
import app.cloudmoji.android.ui.flashcards.FlashCardsScreen
import app.cloudmoji.android.ui.launcher.LauncherScreen
import app.cloudmoji.android.ui.music.MusicScreen
import app.cloudmoji.android.ui.parents.GateAttempt
import app.cloudmoji.android.ui.parents.GrownUpsHost
import app.cloudmoji.android.ui.parents.ParentRequest
import app.cloudmoji.android.ui.parents.ParentalGate
import app.cloudmoji.android.ui.photos.PhotosScreen
import app.cloudmoji.android.ui.sleepy.SleepyCloudScreen
import app.cloudmoji.android.ui.words.WordsScreen
import kotlinx.coroutines.launch

internal const val LauncherRoute = "launcher"
internal const val ParentRoute = "parents"

/**
 * Coerces a `route` value read back from saved-instance state so a restored
 * [ParentRoute] can never land the app straight in the Grown-ups area with
 * no arithmetic in front of it.
 *
 * `rememberSaveable`'s restore path fires identically for a config-change
 * recreation (same process, e.g. a rotation) and for Android killing the
 * process in the background and recreating the Activity from the same saved
 * `Bundle` when the task is reopened — `Activity.onCreate` receives a
 * non-null `savedInstanceState` either way, and Compose has no cheaper way
 * to tell them apart at this call site. iOS has no equivalent bug: its
 * `@State` sheet flag lives only in memory and dies with the process, so a
 * cold launch there always starts at the launcher. Every other restored
 * value passes through unchanged — a restored mini-app route is fine, since
 * none of them carry parent-only controls, settings, or outbound links.
 */
internal fun sanitizeRestoredRoute(raw: String): String =
    if (raw == ParentRoute) LauncherRoute else raw

/** Applies [sanitizeRestoredRoute] at restore time, in place of the implicit
 * `autoSaver()` a bare `rememberSaveable { mutableStateOf(...) }` would use
 * for a `String`. `save` is the identity — nothing about *writing* the
 * current route to the bundle needs to change; only a value coming back
 * *out* of a bundle can ever produce the bypass this guards against. */
private val RouteSaver: Saver<String, String> = Saver(
    save = { it },
    restore = { sanitizeRestoredRoute(it) },
)

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
 *
 * **The parental gate** ([ParentalGate]) is a full-screen overlay drawn
 * *after* [AdaptiveShell] in the same [Box], so it sits over the launcher
 * tiles and whatever mini-app is currently active — the same reason iOS
 * draws it as a body-level `.overlay` on `RootContent` rather than a sheet
 * scoped to one screen: a gate a child can tap around underneath is not a
 * gate. [openParentDoor] is the one function every "Grown-ups"/gear control
 * in the app is wired to (the launcher header, and each mini-app's
 * [app.cloudmoji.android.ui.common.ModeHeader]) — gating it once here gates
 * every route to [ParentRoute] at once, since none of those callers can
 * reach `route = ParentRoute` any other way.
 */
@Composable
fun CloudmojiApp() {
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as CloudmojiApplication }
    // Only for `shouldShowRequestPermissionRationale`, which is an Activity
    // method with no Context equivalent. Same helper `SleepyCloudScreen`
    // already uses to reach its window.
    val activity = remember(context) { context.findActivity() }
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf(LauncherRoute) }

    // The gate's own state. `gateIndex` only ever advances by one, on every
    // close — pass or cancel alike, mirroring iOS `RootContent`'s
    // `gateIndex += 1` fired from both `onPass` and `onCancel` — so the
    // *next* time a parent opens the gate they see the next question in the
    // rotation, never a repeat of the one they just answered or dismissed.
    // Kept as a plain `Int` (rather than a persisted `GateAttempt`) so it can
    // keep using the ordinary default Saver; [closeGate] below still drives
    // the advance through `GateAttempt.next()` rather than a second,
    // duplicated `+ 1`.
    var isGateShowing by rememberSaveable { mutableStateOf(false) }
    var gateIndex by rememberSaveable { mutableStateOf(0) }

    // Why the gate is open — the sentence it shows, and what passing it does.
    // Held as the enum's `name` so it keeps the ordinary default Saver, the
    // same trade-off `gateIndex` above makes; [ParentRequest.fromName] is what
    // makes a restored value from an older build safe.
    var parentRequestName by rememberSaveable { mutableStateOf(ParentRequest.Settings.name) }
    val parentRequest = ParentRequest.fromName(parentRequestName)

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
    // Flash Cards' own pool, narrowed the same way — iOS `AppModel.emojis(in:)`
    // with a `nil` category, which is exactly what `FlashCardsView.nextRound()`
    // asks for.
    val flashCardsPool = remember(application.repository, settings.enabledCategories) {
        narrowedEmojis(application.repository, settings.enabledCategories)
    }
    // Animals' own pool, narrowed to *only* the animals category — unlike
    // `flashCardsPool` above, an empty result here is not backfilled from the
    // rest of the catalogue; it is `AnimalsScreen`'s own "switched off" cue.
    val animalsPool = remember(application.repository, settings.enabledCategories) {
        narrowedAnimals(application.repository, settings.enabledCategories)
    }
    // The launcher tile follows the same setting: `AppAccessPolicy.visibleMiniApps`'s
    // own doc explains why hiding the tile (rather than opening onto an empty
    // grid) is the "no failure states" answer here.
    val animalsEnabled = Category.Animals in settings.enabledCategories
    val onCycleLanguage: () -> Unit = {
        val next = Language.next(after = effectiveLanguage, enabled = availableLanguages.map { it.id })
        scope.launch { application.settingsRepository.setLanguage(next) }
        Unit
    }

    // Opens a mini-app. Count mode's own round is reset to a fresh one on
    // every entry here, and Words' own mood/tap-tally/typed-row are reset
    // right alongside it — every mini-app with process-scoped state resets it
    // on a fresh entry now; see `CloudmojiApplication`'s own doc for why that
    // state is application-scoped in the first place (rotation survival) even
    // though this closure also resets it on navigation. This closure fires
    // only on a genuine navigation (a launcher tile tap); `route` itself
    // survives rotation via `rememberSaveable` above without ever calling
    // back through here, so a rotation mid-round (or mid-visit to Words)
    // never re-triggers this reset.
    val onOpenApp: (MiniApp) -> Unit = { app ->
        when (app) {
            // Words keeps no round of its own, but it does keep the shared
            // mascotMoodMachine's cumulative tap tally and the typed row —
            // both process-scoped, per `CloudmojiApplication`'s own doc — so a
            // fresh entry has to reset both explicitly, the same as every
            // other branch here. Without this, `mascotMoodMachine.tapCount`
            // only ever climbs: once it passes 100, none of the 10/25/50/100
            // milestones can be reached again for the rest of the process, and
            // the typed row from three visits ago would still be sitting there
            // — unlike iOS, where `WordsView`'s `@State` typed row and tap
            // count are reborn at zero on every fresh visit for free.
            MiniApp.Words -> {
                application.speechController.cancelAll()
                application.mascotMoodMachine.reset()
                application.wordsViewModel.clear()
            }

            MiniApp.Count -> {
                application.speechController.cancelAll()
                application.countMoodMachine.reset()
                application.countViewModel.startRound(countables, CountRound.firstTarget(settings.countRange))
            }

            // Cleared rather than started: `FlashCardsScreen`'s own first
            // effect deals the round and speaks it, which is where the
            // language and the "say the word out loud" half of a fresh round
            // live. See `FlashCardsViewModel.reset`.
            MiniApp.FlashCards -> {
                application.speechController.cancelAll()
                application.flashCardsMoodMachine.reset()
                application.flashCardsViewModel.reset()
            }

            // `AnimalsScreen` keeps no round or typed row of its own to
            // clear — only its mood, so a milestone-free mascot never opens
            // mid-hold from whatever the last visit left it doing.
            MiniApp.Animals -> {
                application.speechController.cancelAll()
                application.animalsMoodMachine.reset()
            }

            // Sleepy Cloud opens at the picker, always — see
            // `CloudmojiApplication.sleepySessionState`'s own doc for why a
            // fresh entry must not resume the last visit's session. Speech
            // is cancelled for a stronger reason here than anywhere else:
            // this mini-app never speaks at all, and a word still finishing
            // from Words mode over a wind-down scene is the opposite of what
            // the screen is for.
            MiniApp.Sleepy -> {
                application.speechController.cancelAll()
                application.sleepySessionState.reset()
            }

            // Photos keeps no round, mood or session of its own to clear — it
            // re-reads the folder and the permission on its own first frame —
            // but a word still finishing from Words mode over a silent
            // viewfinder is the same wrong-screen carry-over every branch
            // above cancels. iOS gets this for free: its `open(_:)` cancels
            // speech for every mini-app unconditionally.
            MiniApp.Photos -> application.speechController.cancelAll()

            else -> Unit
        }
        route = app.route
    }

    // Android's own camera dialog, shown only *after* a grown-up has answered
    // the gate — see [ParentRequest.CameraPermission]. Registered here rather
    // than in `PhotosScreen` because this is the composition root, and because
    // the answer has to outlive the screen that asked: it is stored in
    // `application.cameraPermission`, which `PhotosScreen` observes.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Read *after* the result, which is the only time this answer means
        // "a further prompt is still possible" — see
        // `permanentlyDeniedAfterRequest`. Defaults to "can ask again" when
        // there is no Activity to ask, which errs toward showing the child a
        // camera tile rather than a locked one.
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: true
        application.cameraPermission.onRequestResult(granted, canAskAgain)
    }

    // The one way any "Grown-ups"/gear control reaches the gate — see this
    // function's own doc. A child mid-word tapping the gear is not a reason
    // to keep talking over the arithmetic question.
    val openParentDoor: () -> Unit = {
        application.speechController.cancelAll()
        parentRequestName = ParentRequest.Settings.name
        isGateShowing = true
    }

    // Photos' two camera doors — iOS `openCameraPermissionDoor` /
    // `openCameraSettingsDoor`. Both go through the same single gate as
    // everything else; only the sentence on it and what a pass does differ.
    val openCameraPermissionDoor: () -> Unit = {
        application.speechController.cancelAll()
        parentRequestName = ParentRequest.CameraPermission.name
        isGateShowing = true
    }

    val openCameraSettingsDoor: () -> Unit = {
        application.speechController.cancelAll()
        parentRequestName = ParentRequest.CameraSettings.name
        isGateShowing = true
    }

    // Closes the gate and advances the rotation — the one production call
    // site for `GateAttempt.next()`, which `GateAttemptTest` already proves
    // resets `entry`/`wasWrong` and advances the index by exactly one.
    // Wrapping `gateIndex` in a throwaway `GateAttempt` here is cheaper than
    // this file re-deriving its own "+ 1" formula a third time — `onPass`,
    // `onCancel`, and the `BackHandler` below all shared that duplicated
    // literal before this.
    fun closeGate() {
        isGateShowing = false
        gateIndex = GateAttempt(index = gateIndex).next().index
    }

    // **The one place a passed gate turns into an action**, and therefore the
    // one place `route = ParentRoute` is ever written — see
    // `sanitizeRestoredRoute`'s own doc for why that single write site is a
    // hard invariant rather than a preference. Ported from iOS
    // `completeParentRequest`. Exhaustive over the enum on purpose: a fourth
    // door added later should break this file loudly rather than silently do
    // nothing.
    fun completeParentRequest() {
        when (parentRequest) {
            ParentRequest.Settings -> route = ParentRoute
            ParentRequest.CameraPermission -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            ParentRequest.CameraSettings -> openAppSettings(context)
        }
    }

    BackHandler(enabled = route != LauncherRoute) {
        route = LauncherRoute
    }
    // Composed after the [BackHandler] above, so it is the more-recently
    // registered callback and fires first while the gate is showing — even
    // when `route` is already a mini-app, back closes the gate rather than
    // leaving for the launcher underneath it.
    BackHandler(enabled = isGateShowing) { closeGate() }

    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveShell(
            // While the gate is up, the screen underneath (the launcher, or
            // whatever mini-app the gear was tapped from) is removed from the
            // accessibility tree entirely, mirroring iOS's
            // `.accessibilityElement(children: .contain)` on the gate itself.
            // Without this, TalkBack could still swipe past the scrim — which
            // blocks touch but not accessibility focus — and reach launcher
            // tiles or a mini-app's controls while the gate is meant to be
            // the only thing on screen.
            modifier = if (isGateShowing) Modifier.clearAndSetSemantics {} else Modifier,
        ) {
            when {
                route == LauncherRoute -> LauncherScreen(
                    apps = accessPolicy.visibleMiniApps(animalsEnabled = animalsEnabled),
                    language = effectiveLanguage,
                    onOpen = onOpenApp,
                    onParent = openParentDoor,
                )

                route == ParentRoute -> GrownUpsHost(
                    settings = settings,
                    accessPolicy = accessPolicy,
                    allLanguages = application.repository.languages,
                    availableLanguages = availableLanguages,
                    categories = application.repository.categories.filter { it.category != null },
                    effectiveLanguage = effectiveLanguage,
                    isUnlocked = isUnlocked,
                    photoStore = application.photoStore,
                    cameraPermission = application.cameraPermission,
                    onSetMuted = { muted -> scope.launch { application.settingsRepository.setMuted(muted) } },
                    onSetEnabledLanguages = { languages ->
                        scope.launch { application.settingsRepository.setEnabledLanguages(languages) }
                    },
                    onSetLanguage = { language -> scope.launch { application.settingsRepository.setLanguage(language) } },
                    onSetEnabledCategories = { categories ->
                        scope.launch { application.settingsRepository.setEnabledCategories(categories) }
                    },
                    onSetCountRange = { range -> scope.launch { application.settingsRepository.setCountRange(range) } },
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
                                onParent = openParentDoor,
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
                                onParent = openParentDoor,
                            )

                            MiniApp.FlashCards -> FlashCardsScreen(
                                pool = flashCardsPool,
                                language = effectiveLanguage,
                                muted = settings.muted,
                                speechController = application.speechController,
                                hapticFeedback = application.hapticFeedback,
                                moodMachine = application.flashCardsMoodMachine,
                                viewModel = application.flashCardsViewModel,
                                onHome = { route = LauncherRoute },
                                onUnmute = { scope.launch { application.settingsRepository.setMuted(false) } },
                            )

                            MiniApp.Music -> MusicScreen(
                                muted = settings.muted,
                                toneDirector = application.toneDirector,
                                hapticFeedback = application.hapticFeedback,
                                onHome = { route = LauncherRoute },
                                onUnmute = { scope.launch { application.settingsRepository.setMuted(false) } },
                            )

                            MiniApp.Animals -> AnimalsScreen(
                                pool = animalsPool,
                                repository = application.repository,
                                language = effectiveLanguage,
                                muted = settings.muted,
                                speechController = application.speechController,
                                hapticFeedback = application.hapticFeedback,
                                moodMachine = application.animalsMoodMachine,
                                onHome = { route = LauncherRoute },
                                onUnmute = { scope.launch { application.settingsRepository.setMuted(false) } },
                            )

                            MiniApp.Sleepy -> SleepyCloudScreen(
                                session = application.sleepySessionState,
                                language = effectiveLanguage,
                                muted = settings.muted,
                                toneDirector = application.toneDirector,
                                hapticFeedback = application.hapticFeedback,
                                onHome = { route = LauncherRoute },
                                onUnmute = { scope.launch { application.settingsRepository.setMuted(false) } },
                            )

                            // Photos keeps no round, mood or audio of its own
                            // to reset on entry — hence no branch in
                            // `onOpenApp` above — but it does re-read the
                            // folder and the camera permission every time it
                            // appears, which is its own first effect.
                            MiniApp.Photos -> PhotosScreen(
                                store = application.photoStore,
                                cameraPermission = application.cameraPermission,
                                language = effectiveLanguage,
                                hapticFeedback = application.hapticFeedback,
                                onCameraPermissionRequest = openCameraPermissionDoor,
                                onCameraPermissionHelp = openCameraSettingsDoor,
                                onHome = { route = LauncherRoute },
                            )
                        }
                    } else {
                        LauncherScreen(
                            apps = accessPolicy.visibleMiniApps(animalsEnabled = animalsEnabled),
                            language = effectiveLanguage,
                            onOpen = onOpenApp,
                            onParent = openParentDoor,
                        )
                    }
                }
            }
        }

        if (isGateShowing) {
            ParentalGate(
                challengeIndex = gateIndex,
                action = parentRequest.explanation,
                onPass = {
                    closeGate()
                    completeParentRequest()
                },
                onCancel = ::closeGate,
            )
        }
    }
}
