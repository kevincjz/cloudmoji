package app.cloudmoji.android

import android.app.Application
import android.util.Log
import app.cloudmoji.android.data.EmojiCatalogException
import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.SettingsRepository
import app.cloudmoji.android.data.settingsDataStore
import app.cloudmoji.android.model.CountViewModel
import app.cloudmoji.android.model.CountingGrammar
import app.cloudmoji.android.model.CoroutineMascotScheduler
import app.cloudmoji.android.model.CoroutineWordsScheduler
import app.cloudmoji.android.model.MascotMoodMachine
import app.cloudmoji.android.model.Settings
import app.cloudmoji.android.model.WordsViewModel
import app.cloudmoji.android.platform.AndroidAudioFocusSystem
import app.cloudmoji.android.platform.AndroidHapticFeedback
import app.cloudmoji.android.platform.AndroidSpeechEngine
import app.cloudmoji.android.platform.AudioFocusOwner
import app.cloudmoji.android.platform.CoroutineSpeechWatchdogScheduler
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.platform.StubEntitlementStore
import app.cloudmoji.android.platform.VoiceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The process-lifetime home for everything that must survive an Activity
 * recreation — most importantly the one Android throws at every rotation,
 * since `AndroidManifest.xml` declares no `android:configChanges` for
 * `MainActivity`.
 *
 * Before this class existed, `CloudmojiApp()` built the whole platform stack
 * — including a real `TextToSpeech` connection — with `remember {}`, scoped
 * to the Activity's own Composition. A rotation destroys and recreates that
 * Composition from scratch: the JSON catalogue was re-parsed on the main
 * thread on every turn of the phone, the previous [AndroidSpeechEngine]'s
 * `TextToSpeech` was abandoned with no way to release it (see
 * [app.cloudmoji.android.platform.SpeechEngine.shutdown]), and Words' own
 * typed row and mascot mood were silently reset — the opposite of iOS, where
 * `@State` survives rotation for free. This class is the fix: everything
 * below is built at most once per process, in a [CoroutineScope]
 * ([appScope]) that outlives any single Activity instance, so a scheduled
 * bubble/bounce/mascot timer or speech watchdog is never orphaned by a
 * rotation either.
 *
 * **Trade-off accepted deliberately**: [wordsViewModel]/[mascotMoodMachine]
 * now also survive *navigating away from Words and back* (to the launcher
 * and back in), not just rotation — a child's typed row and the mascot's
 * mood persist across a visit to another mini-app rather than resetting
 * fresh, unlike iOS's view-scoped `@State`. That is one consistent retention
 * mechanism instead of two (a rotation-surviving one for the platform stack,
 * plus a separate reset-on-navigate one for Words' own state) — a real
 * `androidx.lifecycle.ViewModel` (cleared on the *destination* leaving the
 * back stack, not on every config change) is the framework-provided way to
 * get both properties at once, but that dependency is not part of this
 * project yet and adding it is out of scope for this fix. See the Task 6
 * fix report for the reasoning in full.
 */
class CloudmojiApplication : Application() {

    /** Outlives any single Activity instance — the whole point of this
     * class. `SupervisorJob` so one scheduled callback throwing never cancels
     * every other pending timer. `Dispatchers.Main.immediate` mirrors what
     * `rememberCoroutineScope()` gave every scheduler before this class
     * existed, preserving the main-thread confinement `SpeechController`/
     * `MascotMoodMachine`/`WordsViewModel` all require. */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val repository: EmojiRepository by lazy {
        try {
            EmojiRepositoryLoader.fromAssets(this)
        } catch (e: EmojiCatalogException) {
            // A missing or malformed bundled resource is a build error, not
            // a runtime path — but the child must never see a crash, so an
            // empty repository is the degraded case rather than a trap.
            // Logged because "the grid is silently empty" would otherwise be
            // a mystery with no trace of why.
            Log.e(TAG, "EmojiData.json could not be loaded; falling back to an empty repository", e)
            EmojiRepository.empty
        }
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }

    val entitlementStore: StubEntitlementStore by lazy { StubEntitlementStore() }

    /** Mirrors [settingsRepository]'s current value without needing a
     * `@Composable` context — [speechController]'s `isMuted` reads this
     * directly. Kept current by a collector started in [onCreate], on
     * [appScope] (main thread), the same role `rememberUpdatedState` played
     * in `CloudmojiApp` before this class existed. */
    private var latestSettings: Settings = Settings.default()

    val speechController: SpeechController by lazy {
        val resolver = VoiceResolver(repository.languages)
        val focusOwner = AudioFocusOwner(AndroidAudioFocusSystem(this))
        val engine = AndroidSpeechEngine(this, focusOwner)
        val watchdog = CoroutineSpeechWatchdogScheduler(appScope)
        SpeechController(resolver, engine, watchdog, isMuted = { latestSettings.muted })
    }

    /** Words mode's own state — see the class doc's trade-off note. */
    val wordsViewModel: WordsViewModel by lazy { WordsViewModel(CoroutineWordsScheduler(appScope)) }

    /** Shared by every screen that reacts to taps and speech — see
     * `MascotMoodMachine`'s own doc. */
    val mascotMoodMachine: MascotMoodMachine by lazy { MascotMoodMachine(CoroutineMascotScheduler(appScope)) }

    /** "three dogs", "三只狗", "いぬ みっつ" — built once per process, like
     * [repository] itself; [Words][wordsViewModel] has no equivalent because
     * Words never counts anything. */
    val countingGrammar: CountingGrammar by lazy { CountingGrammar(repository) }

    /** Count mode's own round/phrase state — see that class's doc for why it
     * is a separate type from [wordsViewModel] rather than a second use of
     * the same shape. Application-scoped for the same rotation-survival
     * reason as [wordsViewModel]; `CloudmojiApp`'s `onOpen` is what resets it
     * to a fresh round on every *fresh* entry from the launcher — see
     * `ui/count/CountScreen.kt`'s own doc for the full reasoning on why that
     * differs from Words' "persists across navigation" trade-off. */
    val countViewModel: CountViewModel by lazy { CountViewModel() }

    /**
     * A **second**, Count-specific [MascotMoodMachine] instance — deliberately
     * not [mascotMoodMachine] again. Sharing one instance would let Count's
     * taps count toward Words' 10/25/50/100 milestone tally (and vice versa),
     * and a finished Count round is not a milestone at all: every round ends
     * in a celebration, unconditionally, on iOS `CountView`'s own timing
     * (1200ms delay, 3500ms hold) rather than Words' (500ms/3000ms). Passing
     * `emptySet()` for milestones means this instance's own tap tally never
     * auto-celebrates on its own; Count calls `celebrateNow()` directly on
     * round completion instead. See [MascotMoodMachine.celebrateNow]/
     * [MascotMoodMachine.reset].
     */
    val countMoodMachine: MascotMoodMachine by lazy {
        MascotMoodMachine(
            scheduler = CoroutineMascotScheduler(appScope),
            milestones = emptySet(),
            celebrationDelayMillis = COUNT_CELEBRATION_DELAY_MS,
            celebrationHoldMillis = COUNT_CELEBRATION_HOLD_MS,
        )
    }

    /** The taps and rewards a child feels — see [HapticFeedback]'s own doc.
     * Words mode does not wire this in yet (Task 6 shipped without it); Count
     * does, per that interface's own "a finished round in Count mode" line. */
    val hapticFeedback: HapticFeedback by lazy { AndroidHapticFeedback(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            settingsRepository.settings.collect { latestSettings = it }
        }
    }

    /**
     * Best-effort only — real devices essentially never call this (it is
     * documented as an emulator/debugging convenience, not something
     * production code can rely on), so it is not *the* release path, but it
     * is free, correct when it does run, and it is what makes
     * [SpeechController.shutdown] reachable from anywhere in this app at
     * all rather than dead code with no call site.
     */
    override fun onTerminate() {
        speechController.shutdown()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "CloudmojiApplication"

        /** iOS `CountView.completionDelay`/`beamingHold` — see [countMoodMachine]. */
        private const val COUNT_CELEBRATION_DELAY_MS = 1200L
        private const val COUNT_CELEBRATION_HOLD_MS = 3500L
    }
}
