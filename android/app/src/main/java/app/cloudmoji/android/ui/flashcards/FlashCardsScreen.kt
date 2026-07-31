package app.cloudmoji.android.ui.flashcards

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.cloudmoji.android.model.EmojiEntry
import app.cloudmoji.android.model.FlashCardsViewModel
import app.cloudmoji.android.model.FlashRound
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.model.MascotMoodMachine
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.MiniAppScaffold
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.mascot.CloudMascot
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.Lavender
import app.cloudmoji.android.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Every number Flash Cards is drawn from. Mirrors iOS
 * `FlashCardsView.swift`'s `FlashCardMetrics`, pt for dp.
 */
object FlashCardMetrics {
    /** The child-facing floor `CLAUDE.md` rule 1 sets. Nothing here is
     * anywhere near it — named so the tests can say what they enforce. */
    val childMinimum: Dp = 64.dp

    /**
     * Far past the 72dp preferred size. There are only ever three of these on
     * screen and the child is choosing between them from across a room's
     * worth of attention span — bigger is unambiguously better here.
     */
    fun choiceSide(compact: Boolean, isExpandedPad: Boolean = false): Dp = when {
        isExpandedPad -> 148.dp
        compact -> 96.dp
        else -> 110.dp
    }

    fun glyphSize(compact: Boolean, isExpandedPad: Boolean = false): TextUnit = when {
        isExpandedPad -> 78.sp
        compact -> 46.sp
        else -> 56.sp
    }

    /**
     * [choiceSide] narrowed to what the screen actually has, never below
     * [childMinimum]. **Not in iOS**, and needed only here: iOS's narrowest
     * phone is 375pt, where three 110pt tiles and their gaps (354pt) fit, but
     * Android's common 360dp — and 320dp on older small phones — do not, and
     * a fixed 110dp there would push the outer tiles off the screen edge
     * rather than merely past their padding.
     *
     * Shrinking stops dead at [childMinimum]: a target under the floor is the
     * one trade this project does not make (`CLAUDE.md` rule 1), so a screen
     * too narrow even for that overflows instead — the same call
     * `InstrumentPadMetrics.side` makes for Music's pads.
     *
     * A degenerate [count] (zero or negative — there are always at least two
     * choices, but this must not divide by zero if that ever changed) returns
     * [preferred] rather than trapping.
     */
    fun fittedChoiceSide(availableWidth: Dp, preferred: Dp, count: Int, spacing: Dp = this.spacing): Dp {
        if (count <= 0) return preferred
        val fitted = (availableWidth - spacing * (count - 1)) / count
        return when {
            fitted >= preferred -> preferred
            fitted <= childMinimum -> childMinimum
            else -> fitted
        }
    }

    /** The glyph keeps its share of the plate when [fittedChoiceSide] shrinks
     * one, or a narrow phone gets a full-size emoji in a smaller tile with no
     * margin left around it. */
    fun fittedGlyphSize(preferredSide: Dp, fittedSide: Dp, preferredGlyph: TextUnit): TextUnit =
        if (fittedSide >= preferredSide || preferredSide.value <= 0f) {
            preferredGlyph
        } else {
            preferredGlyph * (fittedSide.value / preferredSide.value)
        }

    /** `CLAUDE.md` rule 2 (8dp floor), with room to spare. */
    val spacing: Dp = 12.dp

    val cornerRadius: Dp = 22.dp
    val borderWidth: Dp = 2.dp

    /** Design system Active States: emoji tiles `scale(0.85)`. */
    const val pressedScale: Float = 0.85f

    /** iOS `EmojiTileMetrics.bounceScale`/`bounceDuration`, which
     * `FlashCardsView` reuses for a tapped choice rather than inventing its
     * own — a choice tile *is* an emoji tile as far as the child is
     * concerned. */
    const val bounceScale: Float = 1.3f

    /** The correct tile's own swell while the celebration runs. Smaller than
     * [bounceScale] on purpose: it is held for 1400ms rather than flashed. */
    const val solvedScale: Float = 1.08f

    /** How far the *other* two tiles fade while a correct answer is being
     * celebrated. Not a disabled look — they come straight back with the next
     * question. */
    const val dimmedAlpha: Float = 0.34f

    /** The replay button, which a child taps to hear the word again. */
    val replaySide: Dp = 64.dp
    val padReplaySide: Dp = 76.dp

    /** Three tiles, three tilts — a hand-dealt look rather than a row of
     * identical plates. iOS `FlashCardsView.choiceTile`'s
     * `[-3.0, 2.0, -1.5][index % 3]`. */
    private val tilts: List<Float> = listOf(-3f, 2f, -1.5f)

    fun tilt(index: Int): Float = tilts[index.mod(tilts.size)]

    /** iOS `FlashCardsView.choiceTint`: gold, lavender, coral, repeating, so
     * a child can aim at "the yellow one". */
    private val tints: List<Color> = listOf(Gold, Lavender, Coral)

    fun tint(index: Int): Color = tints[index.mod(tints.size)]
}

/** Chrome, not content — the mode's own copy, in five languages. Mirrors iOS
 * `FlashCardsView.uiText`. There is no word list in this file and there must
 * never be one; `src/data/` is the single source. */
internal object FlashCardsUiText {
    private val promptTable = mapOf(
        Language.English to "Which one is it?",
        Language.Chinese to "是哪一个?",
        Language.Malay to "Yang mana satu?",
        Language.Japanese to "どれかな?",
        Language.Tagalog to "Alin ito?",
    )
    private val replayTable = mapOf(
        Language.English to "Say it again",
        Language.Chinese to "再说一次",
        Language.Malay to "Sebut lagi",
        Language.Japanese to "もういちど",
        Language.Tagalog to "Ulitin",
    )

    private fun lookup(table: Map<Language, String>, language: Language): String =
        table[language] ?: table.getValue(Language.English)

    fun prompt(language: Language) = lookup(promptTable, language)

    fun replay(language: Language) = lookup(replayTable, language)
}

/**
 * Flash Cards ⚡ — Cloudmoji says a word, the child finds it. Ported from iOS
 * `Views/FlashCards/FlashCardsView.swift`.
 *
 * The first screen in this app that asks a question, which makes it the first
 * one that could have a wrong answer — and `CLAUDE.md` rule 4 says it must
 * not. So a non-matching tap is not wrong: the emoji the child actually
 * touched says **its own** name, bounces, and the question is repeated. He
 * named a thing, out loud, in the language his family chose. That is the same
 * reward Words mode gives, arrived at by a detour. Nothing reddens, nothing
 * shakes, nothing is taken away, and the other choices stay live throughout —
 * [FlashCardsViewModel.tap]'s own doc traces each of those back to the iOS
 * line it comes from.
 *
 * [pool] arrives already narrowed to the categories the parent left enabled
 * (`narrowedEmojis`, resolved in `CloudmojiApp`), per the project rule that a
 * screen never decides what it is allowed to show.
 *
 * No [app.cloudmoji.android.ui.common.ModeHeader] here, matching iOS
 * `FlashCardsView` exactly: no language control and no mute toggle of its own,
 * just the question and [MiniAppScaffold]'s cloud home button. Since there is
 * no mute control on this screen, muting from elsewhere would otherwise leave
 * a mode whose entire premise is a spoken word silently broken — so
 * [MiniApp.FlashCards]'s `showsSoundRecovery` flag, [muted] and [onUnmute] are
 * handed to the scaffold, which draws the same recovery button Music already
 * uses.
 */
@Composable
fun FlashCardsScreen(
    pool: List<EmojiEntry>,
    language: Language,
    muted: Boolean,
    speechController: SpeechController,
    hapticFeedback: HapticFeedback,
    moodMachine: MascotMoodMachine,
    viewModel: FlashCardsViewModel,
    onHome: () -> Unit,
    onUnmute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current

    val mood by moodMachine.mood.collectAsState()
    val round by viewModel.round.collectAsState()
    val bounce by viewModel.bounce.collectAsState()
    val solvedId by viewModel.solvedId.collectAsState()
    val isAdvancing by viewModel.isAdvancing.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()

    // Read fresh on every call rather than captured once — `language` and
    // `pool` change out from under a long-lived callback (one armed 1400ms
    // earlier), so a stale closure over either would speak, or draw from,
    // the wrong thing.
    val languageState = rememberUpdatedState(language)
    val poolState = rememberUpdatedState(pool)

    // Bridges the speech engine's own state into the mascot's mood machine —
    // the same "two-line StateFlow collector" `MascotMoodMachine`'s own doc
    // anticipates, and the pattern `WordsScreen`/`CountScreen` already use.
    LaunchedEffect(speechController, moodMachine) {
        speechController.isSpeaking.collect { speaking ->
            if (speaking) moodMachine.onSpeechStarted() else moodMachine.onSpeechFinished()
        }
    }

    /**
     * Says a word with the excited face on, which is what iOS's
     * `speak(_:thenReturnToHappy: true)` means: star eyes for ~600ms
     * (`CLAUDE.md` rule 8), then whatever the speech engine is actually
     * doing. [MascotMoodMachine.onTap] is the only route to that pair, so it
     * is called here even for the two paths that are not literally a child's
     * tap (the first question of a round, and a re-ask after a language or
     * mute change) — its tap tally is inert on this screen's machine, which
     * is built with no milestones (see `CloudmojiApplication`).
     */
    fun speakExcited(word: String) {
        moodMachine.onTap()
        speechController.speak(word, languageState.value)
    }

    /** Says the word the round is asking for — iOS `FlashCardsView.ask()`. */
    fun ask() {
        val target = viewModel.round.value?.target ?: return
        speakExcited(target.word(languageState.value))
    }

    /** iOS `FlashCardsView.silence()`: everything in flight is dropped and
     * the cloud goes back to resting. [MascotMoodMachine.reset] lowers a
     * beaming face directly rather than through `arbitrate`, which is the
     * point — a celebration for a round that is being thrown away must not be
     * protected by the rule that keeps a *live* one from being interrupted. */
    fun silence() {
        speechController.cancelAll()
        moodMachine.reset()
        viewModel.clearPendingTap()
    }

    /** iOS `FlashCardsView.nextRound()`. */
    fun nextRound() {
        silence()
        viewModel.startRound(poolState.value, languageState.value)
        ask()
    }

    // The first question. Mirrors iOS's `.task { if round == nil { nextRound() } }`
    // — including the part where it does *not* re-ask on a rotation, which
    // rebuilds this composable from scratch but leaves the process-scoped
    // `viewModel`'s round in place. A genuine fresh entry from the launcher
    // is what clears it: `CloudmojiApp`'s `onOpen` calls `viewModel.reset()`,
    // the Android stand-in for iOS's view-scoped `@State` dying on a mode
    // switch (the same trade-off `CountScreen`'s own doc sets out).
    LaunchedEffect(Unit) {
        if (viewModel.round.value == null) nextRound()
    }

    // A language change re-asks the same question in the new language rather
    // than throwing the round away: the emojis on screen have not changed,
    // and pulling them out from under a child mid-choice would be the
    // failure. Guarded on a remembered previous value so it does not fire on
    // the first composition — the round above has already spoken — nor after
    // a rotation, which resets that `remember` to null. Mirrors iOS
    // `.onChange(of: model.effectiveLanguage)`.
    var previousLanguage by remember { mutableStateOf<Language?>(null) }
    LaunchedEffect(language) {
        val prior = previousLanguage
        previousLanguage = language
        if (prior != null && prior != language) {
            silence()
            ask()
        }
    }

    // Muting silences; unmuting re-asks, so the child is not left looking at
    // a question he never heard. Mirrors iOS `.onChange(of: model.settings.muted)`
    // exactly, including the asymmetry: the unmute path deliberately does not
    // `silence()` first.
    var previousMuted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(muted) {
        val prior = previousMuted
        previousMuted = muted
        if (prior != null && prior != muted) {
            if (muted) silence() else ask()
        }
    }

    // A parent narrowing the categories mid-session invalidates the round on
    // screen: it may be asking for something they just switched off. Mirrors
    // iOS `.onChange(of: model.settings.enabledCategories) { nextRound() }`,
    // one step further down the same derivation (`pool` is what
    // `enabledCategories` produces).
    var previousPool by remember { mutableStateOf<List<EmojiEntry>?>(null) }
    LaunchedEffect(pool) {
        val prior = previousPool
        previousPool = pool
        if (prior != null && prior != pool) nextRound()
    }

    // The tapped tile's bounce, and the 1400ms hand-off every tap arms. Both
    // are keyed on a value carrying a per-tap token, so a second tap on the
    // *same* tile restarts the timer instead of inheriting what is left of
    // the first one's — iOS cancels and re-arms both tasks unconditionally.
    LaunchedEffect(bounce) {
        val current = bounce ?: return@LaunchedEffect
        delay(FlashCardsViewModel.BOUNCE_HOLD_MS)
        viewModel.clearBounce(current.token)
    }

    LaunchedEffect(pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        delay(FlashCardsViewModel.ADVANCE_DELAY_MS)
        when (action.kind) {
            // iOS lowers the mascot's own flag here as well; `nextRound`'s
            // `silence()` is what does that on this side.
            FlashCardsViewModel.PendingAction.Kind.Advance -> nextRound()
            // The question comes back, because it was never withdrawn.
            FlashCardsViewModel.PendingAction.Kind.Repeat -> ask()
        }
        viewModel.clearPendingAction(action.token)
    }

    // iOS `.onDisappear`. The pending hand-off is deliberately *not* cleared:
    // it lives on the process-scoped view model so that a rotation landing
    // mid-celebration re-arms it above and still advances, instead of
    // stranding a solved tile with nothing left to move it on.
    DisposableEffect(speechController, moodMachine) {
        onDispose {
            speechController.cancelAll()
            moodMachine.reset()
        }
    }

    fun onTapChoice(entry: EmojiEntry) {
        // Before anything else, so the buzz lands with the finger — and
        // unconditionally, ahead of the accept/refuse check, matching iOS
        // `FlashCardsView.tap`'s own ordering.
        hapticFeedback.tap()
        when (viewModel.tap(entry)) {
            null -> Unit

            is FlashCardsViewModel.TapOutcome.Correct -> {
                hapticFeedback.reward()
                // Straight to beaming, with no excited face first: this
                // screen's machine is built with a zero-length first leg, so
                // `celebrateNow` is the exact equivalent of iOS's
                // `setMood(.beaming)` here. The word that follows is spoken
                // *without* `speakExcited` for the same reason — iOS passes
                // `thenReturnToHappy: false` — and `MascotMood.arbitrate`
                // refuses to let the speech finishing lower the celebration
                // (`CLAUDE.md` rule 11).
                moodMachine.celebrateNow()
                speechController.speak(entry.word(languageState.value), languageState.value)
            }

            is FlashCardsViewModel.TapOutcome.Other ->
                // Not an error — the thing he touched says its own name.
                speakExcited(entry.word(languageState.value))
        }
    }

    val choiceSide = FlashCardMetrics.choiceSide(layout.isCompactPhone, layout.isExpandedPad)
    val glyphSize = FlashCardMetrics.glyphSize(layout.isCompactPhone, layout.isExpandedPad)
    val sideBySide = layout.isCompactPhone || (layout.isExpandedPad && layout.isLandscape)

    MiniAppScaffold(
        onHome = onHome,
        homeAccent = Gold,
        screenTag = "flashcards-screen",
        showsSoundRecovery = MiniApp.FlashCards.showsSoundRecovery,
        muted = muted,
        onUnmute = onUnmute,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().testTag("flash-panel"),
        ) {
            val prompt: @Composable () -> Unit = {
                PromptCard(
                    mood = mood,
                    prompt = FlashCardsUiText.prompt(language),
                    word = round?.target?.word(language).orEmpty(),
                    isCompact = layout.isCompactPhone,
                    isExpandedPad = layout.isExpandedPad,
                    isLandscape = layout.isLandscape,
                )
            }
            val choices: @Composable () -> Unit = {
                // Measured rather than assumed: in the side-by-side layout
                // this sits after the prompt card in a `Row`, so the
                // constraint it reads is already the remainder, and in the
                // stacked one it is the padded screen width.
                BoxWithConstraints {
                    val count = round?.choices?.size ?: FlashRound.DEFAULT_CHOICE_COUNT
                    val side = FlashCardMetrics.fittedChoiceSide(maxWidth, choiceSide, count)
                    val glyph = FlashCardMetrics.fittedGlyphSize(choiceSide, side, glyphSize)
                    Row(horizontalArrangement = Arrangement.spacedBy(FlashCardMetrics.spacing)) {
                        round?.choices?.forEachIndexed { index, entry ->
                            ChoiceTile(
                                emoji = entry.emoji,
                                label = entry.word(language),
                                index = index,
                                side = side,
                                glyphSize = glyph,
                                isBouncing = bounce?.id == entry.id,
                                isSolved = solvedId == entry.id,
                                isAdvancing = isAdvancing,
                                onTap = { onTapChoice(entry) },
                            )
                        }
                    }
                }
            }
            val replay: @Composable () -> Unit = {
                ReplayButton(
                    caption = FlashCardsUiText.replay(language),
                    isExpandedPad = layout.isExpandedPad,
                    onTap = {
                        hapticFeedback.tap()
                        ask()
                    },
                )
            }

            if (sideBySide) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (layout.isExpandedPad) 48.dp else 18.dp),
                    modifier = Modifier.padding(horizontal = if (layout.isExpandedPad) 44.dp else 18.dp),
                ) {
                    prompt()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (layout.isExpandedPad) 24.dp else 12.dp),
                    ) {
                        choices()
                        replay()
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (layout.isExpandedPad) 34.dp else 22.dp),
                    modifier = Modifier.padding(horizontal = if (layout.isExpandedPad) 36.dp else 12.dp),
                ) {
                    prompt()
                    choices()
                    replay()
                }
            }
        }
    }
}

/**
 * The spoken prompt, as a physical stack of cards rather than a header
 * followed by loose labels — a new round should feel like a fresh card being
 * dealt. Ported from iOS `FlashCardsView.promptCard`, including the two
 * tilted plates behind it, which splay further apart while the mascot is
 * beaming.
 */
@Composable
private fun PromptCard(
    mood: MascotMood,
    prompt: String,
    word: String,
    isCompact: Boolean,
    isExpandedPad: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBeaming = mood == MascotMood.Beaming
    val backTilt by animateFloatAsState(
        targetValue = if (isBeaming) 10f else 6f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
        label = "promptCardTilt",
    )

    val shape = RoundedCornerShape(30.dp)
    val frontShape = RoundedCornerShape(28.dp)

    val width: Dp? = when {
        isExpandedPad && isLandscape -> 360.dp
        isCompact -> 236.dp
        else -> null
    }
    val height = if (isExpandedPad) 326.dp else 224.dp
    val maxWidth = if (isExpandedPad) 430.dp else 310.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .widthIn(max = maxWidth)
            .height(height),
    ) {
        // The two plates behind. Purely decorative, so they stay out of the
        // accessibility tree by having no semantics of their own.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = -7.dp.toPx()
                    translationY = 3.dp.toPx()
                    rotationZ = -backTilt
                }
                .clip(shape)
                .background(Lavender.copy(alpha = 0.13f), shape)
                .border(2.dp, Lavender.copy(alpha = 0.22f), shape),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = 7.dp.toPx()
                    translationY = 3.dp.toPx()
                    rotationZ = backTilt
                }
                .clip(shape)
                .background(Coral.copy(alpha = 0.14f), shape)
                .border(2.dp, Coral.copy(alpha = 0.24f), shape),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                if (isExpandedPad) 14.dp else if (isCompact) 6.dp else 10.dp,
                Alignment.CenterVertically,
            ),
            modifier = Modifier
                .matchParentSize()
                .clip(frontShape)
                .background(BackgroundPrimary.copy(alpha = 0.88f), frontShape)
                .border(2.dp, Gold.copy(alpha = 0.34f), frontShape)
                .padding(horizontal = 14.dp, vertical = if (isExpandedPad) 22.dp else 12.dp),
        ) {
            CloudMascot(
                mood = mood,
                size = if (isExpandedPad) 112.dp else if (isCompact) 58.dp else 78.dp,
            )
            Text(
                text = prompt,
                color = TextTertiary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isExpandedPad) 17.sp else 13.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = word,
                // Gold into coral, the same sunrise the launcher's own title
                // uses. iOS applies it as a `LinearGradient` foreground style;
                // Compose's `TextStyle.brush` is the same thing.
                style = TextStyle(brush = Brush.linearGradient(listOf(Gold, Coral))),
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (isExpandedPad) 42.sp else if (isCompact) 24.sp else 31.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("flash-word"),
            )
        }
    }
}

/**
 * One of the three tiles the child chooses between. Ported from iOS
 * `FlashCardsView.choiceTile`.
 *
 * `internal`, not `private`, for the same reason `CountTile`/`CountControl`
 * are: it lets a Compose test measure the touch-target floor and read the
 * TalkBack label without standing up the whole screen's dependency graph.
 *
 * [label] is the emoji's word in the active language, and it is the
 * accessibility label — TalkBack announcing "grinning face with smiling eyes"
 * for 😄 while the cloud is asking for "happy" would make the question
 * unanswerable by ear.
 */
@Composable
internal fun ChoiceTile(
    emoji: String,
    label: String,
    index: Int,
    side: Dp,
    glyphSize: TextUnit,
    isBouncing: Boolean,
    isSolved: Boolean,
    isAdvancing: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = FlashCardMetrics.tint(index)
    val shape = RoundedCornerShape(FlashCardMetrics.cornerRadius)

    val scale by animateFloatAsState(
        targetValue = when {
            isSolved -> FlashCardMetrics.solvedScale
            isBouncing -> FlashCardMetrics.bounceScale
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "flashChoiceScale",
    )
    val tileAlpha by animateFloatAsState(
        targetValue = if (isAdvancing && !isSolved) FlashCardMetrics.dimmedAlpha else 1f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
        label = "flashChoiceAlpha",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(side)
            // A tile at 1.3× overlaps its neighbours and the row paints in
            // order, so without this the tile the child just touched is
            // partly covered by the ones after it.
            .zIndex(if (isBouncing || isSolved) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = FlashCardMetrics.tilt(index)
                this.alpha = tileAlpha
            }
            .pressScale(interactionSource, FlashCardMetrics.pressedScale)
            .clip(shape)
            .background(tint.copy(alpha = 0.20f), shape)
            .border(FlashCardMetrics.borderWidth, tint.copy(alpha = 0.50f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isAdvancing,
                role = Role.Button,
                onClick = onTap,
            )
            .semantics {
                role = Role.Button
                contentDescription = label
                if (isAdvancing) disabled()
            }
            .testTag("flash-choice-$emoji"),
    ) {
        Text(text = emoji, fontSize = glyphSize)
    }
}

/**
 * "Say it again". Child-facing — this is the control a two-year-old reaches
 * for when he did not catch the word — so it takes the 64dp floor, not the
 * 44dp parent-chrome one. Ported from iOS `FlashCardsView.replayButton`.
 */
@Composable
internal fun ReplayButton(
    caption: String,
    isExpandedPad: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = CircleShape

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isExpandedPad) 9.dp else 6.dp),
        modifier = modifier
            .heightIn(
                min = if (isExpandedPad) FlashCardMetrics.padReplaySide else FlashCardMetrics.replaySide,
            )
            .pressScale(interactionSource, 0.88f)
            .clip(shape)
            .background(Gold.copy(alpha = 0.13f), shape)
            .border(2.dp, Gold.copy(alpha = 0.34f), shape)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onTap)
            .semantics {
                role = Role.Button
                contentDescription = caption
            }
            .testTag("flash-replay")
            .padding(horizontal = if (isExpandedPad) 30.dp else 22.dp),
    ) {
        Text(text = "🔊", fontSize = if (isExpandedPad) 22.sp else 17.sp)
        Text(
            text = caption,
            color = Gold,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = if (isExpandedPad) 18.sp else 14.sp,
            maxLines = 1,
        )
    }
}
