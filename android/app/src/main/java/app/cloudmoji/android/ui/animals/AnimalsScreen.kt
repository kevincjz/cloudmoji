package app.cloudmoji.android.ui.animals

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.EmojiEntry
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.model.MascotMoodMachine
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.ui.common.CloudmojiLayout
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.MiniAppScaffold
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.mascot.CloudMascot
import app.cloudmoji.android.ui.theme.Amber
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.Lavender
import app.cloudmoji.android.ui.theme.Moonlight
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Every number Animals' habitat-card grid is drawn from. Mirrors iOS
 * `AnimalSoundsView.swift`'s `AnimalCardMetrics`/`columns(compact:expandedPad:landscape:)`,
 * pt for dp — the same "port the iOS constant literally" convention
 * `InstrumentPadMetrics`/`FlashCardMetrics` already use.
 */
object AnimalGridMetrics {
    val spacing: Dp = 10.dp
    val height: Dp = 138.dp
    val compactHeight: Dp = 106.dp
    val padHeight: Dp = 162.dp
    val cornerRadius: Dp = 26.dp

    /** iOS `AnimalCardMetrics.tints`: teal, gold, coral, moonlight, lavender,
     * amber, repeating — a child aims at "the blue one", never at a fixed
     * per-animal colour. */
    val tints: List<Color> = listOf(Teal, Gold, Coral, Moonlight, Lavender, Amber)

    fun tint(index: Int): Color = if (tints.isEmpty()) Teal else tints[index.mod(tints.size)]

    /** iOS `AnimalSoundsView.columns(compact:expandedPad:landscape:)`, ported
     * literally: an expanded tablet gets 3 upright / 4 sideways; a compact
     * (landscape) phone always gets 4; everything else gets 2. */
    fun columns(compact: Boolean, expandedPad: Boolean, landscape: Boolean): Int {
        if (expandedPad) return if (landscape) 4 else 3
        return if (compact) 4 else 2
    }
}

/** Chrome, not content — mirrors iOS `AnimalSoundsView`'s hard-coded English
 * "switched off" copy exactly (unlocalized there too, like `CloudmojiApp`'s
 * own `GateExplanation`): this is the same defensive, parent-facing message
 * for a state a child never causes and rarely sees — the animals category
 * disabled while somehow still routed here. There is no word list in this
 * file and there must never be one; `src/data/` is the single source for
 * everything a *child* reads or hears. */
internal object AnimalsUiText {
    const val UNAVAILABLE =
        "Animals are switched off in the grown-ups screen, so there is nothing to play here. " +
            "Turn the Animals category back on to use this."
}

/**
 * The Animals category's own pool, narrowed to whether a parent has left it
 * enabled — the one-category analogue of `narrowedEmojis`/`narrowedCountables`.
 * Mirrors iOS `AppModel.emojis(in: .animals)`: `entry.category == .animals`
 * *and* `enabledCategories.contains(.animals)`.
 *
 * Unlike `narrowedEmojis` (which falls the whole catalogue back in when a
 * narrowing leaves nothing), an empty result here is not papered over: a
 * parent who switched Animals off gets an empty pool, on purpose, so
 * [AnimalsScreen] can show the "switched off" message rather than the whole
 * category flooding back in behind their back.
 */
fun narrowedAnimals(repository: EmojiRepository, enabledCategories: Set<Category>): List<EmojiEntry> =
    if (Category.Animals in enabledCategories) repository.entries(Category.Animals) else emptyList()

/**
 * Which of [pool] actually has something to say. Ported from iOS
 * `AnimalSoundsView.grid(from:withRecordings:)` — minus the bundled-recording
 * half of that function's own union, since Android never ships one (see
 * [AnimalsScreen]'s own doc): [glyphsWithSound] is always
 * `EmojiRepository.animalSoundGlyphs` here, the same set iOS unions a
 * (permanently empty, on this build) `AnimalSoundCatalog.available()` into.
 *
 * One fallback, covering two causes: `matched` is empty both when
 * [glyphsWithSound] itself is empty (a broken/empty sound table — filtering
 * against an empty set can never match anything) and when it is merely
 * mismatched with [pool] (a mis-typed glyph, or a sound entry for an animal
 * since removed from the catalogue) — so a single `ifEmpty` covers both of
 * iOS's guard branches without needing to test [glyphsWithSound] up front. In
 * either case the whole [pool] comes back rather than a blank grid.
 *
 * Pure so the fallback is host-testable without a repository or a screen.
 */
fun animalGrid(pool: List<EmojiEntry>, glyphsWithSound: Set<String>): List<EmojiEntry> {
    val matched = pool.filter { it.emoji in glyphsWithSound }
    return matched.ifEmpty { pool }
}

/** A tap's bounce, keyed on a per-tap [token] so a second tap on the same
 * tile is a genuinely distinct value — the same reason
 * `FlashCardsViewModel.BounceState` carries one — which is what makes
 * `LaunchedEffect(bounce)` restart its hold timer instead of silently
 * inheriting whatever was left of the first tap's. */
private data class BounceState(val id: String, val token: Int)

/** The word hand-off a noise-then-name tap arms — iOS `AnimalSoundsView`'s
 * `wordTask`. Also token-keyed, for the same reason as [BounceState]. */
private data class WordCue(val entry: EmojiEntry, val token: Int)

/** How long a tapped card's swell holds — iOS `AnimalSoundsView.bounceHold`. */
private const val BOUNCE_HOLD_MS = 400L

/** How long the noise gets before Cloudmoji names the animal — iOS
 * `AnimalSoundsView.wordDelay`: long enough to read as its own beat, short
 * enough that the two land as one answer to one tap. A fixed timer, not a
 * completion callback — `speechController.speak` cancels-then-starts, so the
 * word simply interrupts the noise if it is still going when this fires,
 * exactly like iOS's `say(_:)` calling into the same cancel-first controller. */
private const val WORD_DELAY_MS = 1100L

/**
 * Animals 🔊 — tap a creature, hear its noise, then hear its name. Ported
 * from iOS `Views/AnimalSounds/AnimalSoundsView.swift`.
 *
 * **The noise is spoken, not recorded**, unconditionally on this platform:
 * iOS still carries `AnimalSoundCatalog`, a glyph → bundled-`.caf` mapping
 * that wins when a real recording has landed (none has, as of this port —
 * see that file's own doc), with the spoken noise as its fallback. Android
 * ships no audio-file catalogue at all — there is no `assets/AnimalSounds/`
 * — so [animalGrid] only ever sees the *sound-table* half of iOS's
 * `withSounds = model.animalSoundGlyphs.union(AnimalSoundCatalog.available())`
 * union, which is exactly what iOS's own build resolves to today anyway. If
 * a recording catalogue is ever added on this platform, it plugs in the same
 * way iOS's does: another glyph set unioned into
 * `EmojiRepository.animalSoundGlyphs` before it reaches [animalGrid], with no
 * change to this screen's tap order.
 *
 * [pool] arrives already narrowed by [narrowedAnimals] — a screen never
 * decides what it is allowed to show. [repository] is consulted only for
 * content lookups ([EmojiRepository.animalSoundGlyphs]/
 * [EmojiRepository.animalSound]), exactly as iOS's `AppModel.animalSound(for:)`
 * reads straight through to its own repository with no policy attached —
 * the same shape [app.cloudmoji.android.ui.words.WordsScreen] already takes
 * `repository` for.
 *
 * No [app.cloudmoji.android.ui.common.ModeHeader] here, matching iOS exactly:
 * no language control and no mute toggle of its own, just this screen's own
 * small mascot header and [MiniAppScaffold]'s cloud home button. Since there
 * is no mute control on this screen, [MiniApp.Animals]'s `showsSoundRecovery`
 * flag is handed to the scaffold along with [muted]/[onUnmute], the same
 * recovery button Music/FlashCards already use.
 */
@Composable
fun AnimalsScreen(
    pool: List<EmojiEntry>,
    repository: EmojiRepository,
    language: Language,
    muted: Boolean,
    speechController: SpeechController,
    hapticFeedback: HapticFeedback,
    moodMachine: MascotMoodMachine,
    onHome: () -> Unit,
    onUnmute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val mood by moodMachine.mood.collectAsState()

    val animals = remember(pool, repository) { animalGrid(pool, repository.animalSoundGlyphs) }
    val isUnavailable = animals.isEmpty()

    var bounce by remember { mutableStateOf<BounceState?>(null) }
    var pendingWord by remember { mutableStateOf<WordCue?>(null) }
    var tapCounter by remember { mutableIntStateOf(0) }

    // Bridges the speech engine's own state into the mascot's mood machine —
    // the same two-line `StateFlow` collector every other mini-app wires.
    LaunchedEffect(speechController, moodMachine) {
        speechController.isSpeaking.collect { speaking ->
            if (speaking) moodMachine.onSpeechStarted() else moodMachine.onSpeechFinished()
        }
    }

    fun silence() {
        pendingWord = null
        speechController.cancelAll()
        moodMachine.reset()
    }

    // A language or mute change drops whatever is queued — iOS
    // `.onChange(of: model.settings.muted)` / `.onChange(of: model.effectiveLanguage)`,
    // both wired straight to `silence()`.
    LaunchedEffect(language, muted) { silence() }

    // iOS `.onDisappear`.
    DisposableEffect(speechController, moodMachine) {
        onDispose { silence() }
    }

    LaunchedEffect(bounce) {
        if (bounce == null) return@LaunchedEffect
        delay(BOUNCE_HOLD_MS)
        bounce = null
    }

    // The noise-then-name hand-off. A language/mute change (or leaving the
    // screen) already clears `pendingWord` via `silence()` above, which is
    // what keeps this from ever firing with a stale language — the same
    // guarantee iOS gets from `wordTask?.cancel()` inside its own `silence()`.
    LaunchedEffect(pendingWord) {
        val cue = pendingWord ?: return@LaunchedEffect
        delay(WORD_DELAY_MS)
        speechController.speak(cue.entry.word(language), language)
        pendingWord = null
    }

    fun tap(entry: EmojiEntry) {
        // Before anything else, so the buzz lands with the finger — iOS
        // `AnimalSoundsView.tap`'s own ordering.
        hapticFeedback.tap()
        tapCounter += 1
        bounce = BounceState(entry.id, tapCounter)
        // iOS `silenceAudio()`: drop whatever the previous tap queued before
        // deciding what this one says.
        pendingWord = null
        speechController.cancelAll()

        // `MascotMoodMachine.onTap()` folds iOS's two branches into one call:
        // it requests the excited face and arms the ~600ms hold regardless of
        // `muted`, and — since nothing below ever calls `speak` while muted,
        // so `isSpeaking` never flips true — the hold's own timer resolves to
        // Happy on its own once it closes, exactly matching iOS's explicit
        // `guard !muted else { …afterDelay(excitedHold) { setMood(.happy) } }`
        // branch without needing a second code path here.
        moodMachine.onTap()
        if (muted) return

        // The noise first, then the name — the order a parent uses. Empty
        // string is "absent" too: `EmojiRepository.animalSound` already folds
        // that into `null` (Task 1's contract), so this `if` is the entire
        // fallback tier Android needs — see this file's own doc for why iOS's
        // third, bundled-recording tier has no counterpart here.
        val noise = repository.animalSound(entry.emoji, language)
        if (noise != null) {
            speechController.speak(noise, language)
            pendingWord = WordCue(entry, tapCounter)
        } else {
            speechController.speak(entry.word(language), language)
        }
    }

    MiniAppScaffold(
        onHome = onHome,
        homeAccent = Teal,
        screenTag = "animals-screen",
        showsSoundRecovery = MiniApp.Animals.showsSoundRecovery,
        muted = muted,
        onUnmute = onUnmute,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimalStageHeader(mood = mood, layout = layout)

            if (isUnavailable) {
                Text(
                    text = AnimalsUiText.UNAVAILABLE,
                    color = TextSecondary,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                        .testTag("animals-unavailable"),
                )
            } else {
                val columns = AnimalGridMetrics.columns(
                    compact = layout.isCompactPhone,
                    expandedPad = layout.isExpandedPad,
                    landscape = layout.isLandscape,
                )
                val cardHeight = when {
                    layout.isExpandedPad -> AnimalGridMetrics.padHeight
                    layout.isCompactPhone -> AnimalGridMetrics.compactHeight
                    else -> AnimalGridMetrics.height
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(AnimalGridMetrics.spacing),
                    verticalArrangement = Arrangement.spacedBy(AnimalGridMetrics.spacing),
                    contentPadding = PaddingValues(
                        horizontal = if (layout.isExpandedPad) 26.dp else 14.dp,
                        vertical = 14.dp,
                    ),
                    modifier = Modifier.fillMaxSize().testTag("animals-grid"),
                ) {
                    itemsIndexed(animals, key = { _, entry -> entry.id }) { index, entry ->
                        AnimalCard(
                            emoji = entry.emoji,
                            word = entry.word(language),
                            tint = AnimalGridMetrics.tint(index),
                            isPlaying = bounce?.id == entry.id,
                            height = cardHeight,
                            isCompact = layout.isCompactPhone,
                            isExpandedPad = layout.isExpandedPad,
                            onTap = { tap(entry) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cloudmoji becomes part of a little sound stage rather than a repeated page
 * header — iOS `AnimalSoundsView.animalStageHeader`. `pressScale`'s host,
 * `ModeHeader`, uses Material icon-style glyphs nowhere in this codebase; a
 * plain paw/speaker emoji is this project's established stand-in for iOS's
 * SF Symbols (`FlashCardsScreen.ReplayButton`'s "🔊" is the same trade-off).
 */
@Composable
private fun AnimalStageHeader(mood: MascotMood, layout: CloudmojiLayout) {
    val isExpandedPad = layout.isExpandedPad
    val isCompact = layout.isCompactPhone
    val shape = RoundedCornerShape(percent = 50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isExpandedPad) 22.dp else 14.dp),
        modifier = Modifier
            .padding(horizontal = if (isExpandedPad) 34.dp else 24.dp)
            .padding(top = if (isExpandedPad) 18.dp else if (isCompact) 2.dp else 8.dp)
            .heightIn(min = if (isExpandedPad) 94.dp else if (isCompact) 50.dp else 68.dp)
            .clip(shape)
            .background(BackgroundPrimary.copy(alpha = 0.46f), shape)
            .border(1.dp, Teal.copy(alpha = 0.20f), shape)
            .padding(horizontal = 18.dp),
    ) {
        Text(text = "🐾", fontSize = if (isExpandedPad) 26.sp else if (isCompact) 18.sp else 20.sp)
        CloudMascot(mood = mood, size = if (isExpandedPad) 82.dp else if (isCompact) 44.dp else 58.dp)
        Text(text = "🔊", fontSize = if (isExpandedPad) 26.sp else if (isCompact) 18.sp else 20.sp)
    }
}

/**
 * A habitat card, not an `EmojiTile` — iOS `AnimalSoundCard`. The larger
 * portrait glyph, name and speaker mark make the same content read as a
 * creature about to perform rather than a second copy of Words filtered to
 * animals.
 */
@Composable
private fun AnimalCard(
    emoji: String,
    word: String,
    tint: Color,
    isPlaying: Boolean,
    height: Dp,
    isCompact: Boolean,
    isExpandedPad: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(AnimalGridMetrics.cornerRadius)

    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "animalCardScale",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isExpandedPad) 18.dp else if (isCompact) 8.dp else 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(interactionSource, 0.88f)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = if (isPlaying) 0.42f else 0.24f),
                        BackgroundPrimary.copy(alpha = 0.88f),
                    ),
                ),
                shape,
            )
            .border(2.dp, tint.copy(alpha = if (isPlaying) 0.58f else 0.30f), shape)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onTap)
            .semantics {
                role = Role.Button
                contentDescription = word
            }
            .testTag("emoji-$emoji")
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = emoji,
            fontSize = if (isExpandedPad) 60.sp else if (isCompact) 38.sp else 50.sp,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = word,
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isExpandedPad) 18.sp else if (isCompact) 12.sp else 15.sp,
                maxLines = 1,
            )
            Text(
                text = "🔊",
                fontSize = if (isExpandedPad) 20.sp else if (isCompact) 14.sp else 17.sp,
                modifier = Modifier.graphicsLayer { alpha = if (isPlaying) 1f else 0.60f },
            )
        }
    }
}
