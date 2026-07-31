package app.cloudmoji.android.ui.count

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.Countable
import app.cloudmoji.android.model.CountRound
import app.cloudmoji.android.model.CountViewModel
import app.cloudmoji.android.model.CountingGrammar
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta
import app.cloudmoji.android.model.MascotMoodMachine
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.MiniAppScaffold
import app.cloudmoji.android.ui.common.ModeHeader
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.Amber
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Teal

/** Chrome, not content — the mode's own copy, in five languages. Mirrors iOS
 * `CountView.uiText`. */
private object CountUiText {
    private val subtitleTable = mapOf(
        Language.English to "Let's count!",
        Language.Chinese to "数一数!",
        Language.Malay to "Jom kira!",
        Language.Japanese to "かぞえよう!",
        Language.Tagalog to "Magbilang tayo!",
    )
    private val shuffleTable = mapOf(
        Language.English to "Shuffle",
        Language.Chinese to "换一换",
        Language.Malay to "Tukar",
        // Nearly the same word as `next` below. That is what
        // `src/components/CountMode.tsx` says; it is not a transcription slip.
        Language.Japanese to "つぎ",
        Language.Tagalog to "Palitan",
    )
    private val nextTable = mapOf(
        Language.English to "Next!",
        Language.Chinese to "下一个!",
        Language.Malay to "Seterusnya!",
        Language.Japanese to "つぎへ!",
        Language.Tagalog to "Susunod!",
    )

    /** Not in iOS/web — neither Count reference has a replay control, only a
     * running Shuffle/Next pair. Added for Android per the Task 7 brief's
     * explicit "replay button re-speaks the phrase" requirement, styled and
     * captioned to match the pattern `TypingRow`'s/`FlashCardsView`'s own
     * replay buttons already set. */
    private val replayTable = mapOf(
        Language.English to "Replay",
        Language.Chinese to "重播",
        Language.Malay to "Main semula",
        Language.Japanese to "もう一回",
        Language.Tagalog to "Ulitin",
    )

    private fun lookup(table: Map<Language, String>, language: Language): String =
        table[language] ?: table.getValue(Language.English)

    fun subtitle(language: Language) = lookup(subtitleTable, language)
    fun shuffle(language: Language) = lookup(shuffleTable, language)
    fun next(language: Language) = lookup(nextTable, language)
    fun replay(language: Language) = lookup(replayTable, language)
}

/** Blank until something has been counted. A readout showing "0" before the
 * first tap answers a question the child has not asked, with zero. Mirrors
 * iOS `CountView.numeral(for:)`. */
internal fun countNumeral(progress: Int): String = if (progress == 0) "" else progress.toString()

/**
 * Count mode — "Cloudculator". Ported from `src/components/CountMode.tsx` /
 * iOS `CountView.swift`.
 *
 * N identical things are on screen and the child taps them one at a time;
 * each tap speaks the running count in the chosen language, lights a dot,
 * and stamps the tile with the number it was. Finishing the round beams the
 * mascot and says the whole phrase again. Nothing here can fail: a tile
 * already counted refuses quietly and still presses; a muted phone still
 * shows the number and still beams; a missing voice changes nothing anyone
 * can see.
 *
 * One column, upright and sideways alike — Count mode has no categories, so
 * unlike Words there is nothing for a side rail to hold, and the round gets
 * the width instead.
 *
 * State ownership is a deliberate departure from Words' precedent. [viewModel]
 * and [moodMachine] both live at [app.cloudmoji.android.CloudmojiApplication]
 * scope — like Words' own `WordsViewModel`/`MascotMoodMachine` — so a
 * rotation (which Android tears the whole Activity down for) never loses the
 * round mid-play. But *unlike* Words, whose typed row and mood are left to
 * persist across a visit to another mini-app and back, Count's round is
 * reset to a fresh one on every fresh entry from the launcher — see
 * `CloudmojiApp`'s `onOpen` — because that is what iOS actually does: a mode
 * switch there tears the whole SwiftUI view down structurally, discarding its
 * `@State` (`CountView`'s own comment on its `.task` guard says so
 * explicitly), unlike a rotation, which iOS's `@State` survives for free and
 * this screen's own composition guards against re-rolling too (see the
 * `countables`/`countRange` change-detection below, which only fires on a
 * *genuine* change while mounted, never on the first frame after a rotation
 * rebuilds this composable from scratch).
 */
@Composable
fun CountScreen(
    countables: List<Countable>,
    countRange: IntRange,
    language: Language,
    muted: Boolean,
    availableLanguages: List<LanguageMeta>,
    grammar: CountingGrammar,
    speechController: SpeechController,
    hapticFeedback: HapticFeedback,
    moodMachine: MascotMoodMachine,
    viewModel: CountViewModel,
    onSetMuted: (Boolean) -> Unit,
    onCycleLanguage: () -> Unit,
    onHome: () -> Unit,
    onParent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current

    val mood by moodMachine.mood.collectAsState()
    val round by viewModel.round.collectAsState()
    val lastCounted by viewModel.lastCounted.collectAsState()
    val phrase by viewModel.phrase.collectAsState()

    // Read fresh on every call rather than captured once — `language` and
    // `muted` change out from under a long-lived callback (a completion
    // handler armed 1200ms earlier), so a stale closure over either would
    // speak/build the wrong thing.
    val languageState = rememberUpdatedState(language)
    fun phraseFor(item: Countable, count: Int): String = grammar.phrase(item, count, languageState.value)

    // Bridges the speech engine's own state into the mascot's mood machine —
    // the same "two-line StateFlow collector" `MascotMoodMachine`'s own doc
    // anticipates, and the pattern `WordsScreen` already uses. Screen-scoped,
    // so it stops the instant Count is torn down.
    LaunchedEffect(speechController, moodMachine) {
        speechController.isSpeaking.collect { speaking ->
            if (speaking) moodMachine.onSpeechStarted() else moodMachine.onSpeechFinished()
        }
    }

    // A language or mute change silences whatever is already queued and
    // throws away an in-flight celebration — mirrors iOS `CountView.silence()`,
    // called from both of its own `onChange` handlers.
    LaunchedEffect(language, muted) {
        speechController.cancelAll()
        moodMachine.reset()
    }

    // Keeps the on-screen phrase current after a language change — otherwise
    // it stays in the old language until the next tap, and would be handed
    // to the new language's voice on a replay.
    LaunchedEffect(language) {
        viewModel.refreshPhrase(::phraseFor)
    }

    // A parent narrowing the categories mid-session invalidates the round on
    // screen if it is now counting something switched off — restarted at the
    // same target, mirroring iOS `.onChange(of: enabledCategories)`. Guarded
    // to skip the very first composition (a `null` "prior" value): the fresh
    // round for this entry was already picked by `CloudmojiApp`'s `onOpen`,
    // and re-running this on every screen open — including after a rotation,
    // which rebuilds this composable from scratch — would silently swap the
    // item out from under the child a frame after they arrived.
    var previousCountables by remember { mutableStateOf<List<Countable>?>(null) }
    LaunchedEffect(countables) {
        val prior = previousCountables
        previousCountables = countables
        if (prior != null && prior != countables) {
            speechController.cancelAll()
            moodMachine.reset()
            viewModel.startRound(countables, round?.target ?: CountRound.firstTarget(countRange))
        }
    }

    // Same reasoning, for the count range — mirrors iOS
    // `.onChange(of: model.settings.countRange)`, which always restarts at a
    // fresh `firstTarget`, unlike the categories handler above (which keeps
    // the current target): a range change is a parent deliberately choosing
    // a new span to count within, not a narrowing of the same one.
    var previousRange by remember { mutableStateOf<IntRange?>(null) }
    LaunchedEffect(countRange) {
        val prior = previousRange
        previousRange = countRange
        if (prior != null && prior != countRange) {
            speechController.cancelAll()
            moodMachine.reset()
            viewModel.startRound(countables, CountRound.firstTarget(countRange))
        }
    }

    DisposableEffect(speechController, moodMachine) {
        onDispose {
            speechController.cancelAll()
            moodMachine.reset()
        }
    }

    fun onTapTile(index: Int) {
        // Fired before the accept/refuse check, matching iOS `CountView.tap`'s
        // own ordering: an already-counted tile still presses and still
        // "answers" the tap physically — it just does not speak or advance
        // the round. Only the haptic is unconditional; the mood and speech
        // below are gated on acceptance, same as iOS.
        hapticFeedback.tap()
        val spoken = viewModel.tap(index, ::phraseFor) ?: return
        moodMachine.onTap()
        speechController.speak(spoken, languageState.value)

        val finished = viewModel.round.value
        if (finished != null && finished.isComplete) {
            val closing = viewModel.completionPhrase(::phraseFor) ?: return
            hapticFeedback.reward()
            moodMachine.celebrateNow {
                viewModel.setPhrase(closing)
                speechController.speak(closing, languageState.value)
            }
        }
    }

    fun restart(target: Int) {
        speechController.cancelAll()
        moodMachine.reset()
        viewModel.startRound(countables, target)
    }

    val onShuffle = { restart(round?.target ?: CountRound.firstTarget(countRange)) }
    val onNext = { restart(CountRound.nextTarget(round?.target ?: countRange.first, countRange)) }
    val onReplay = {
        if (phrase.isNotEmpty()) speechController.speak(phrase, languageState.value)
    }

    MiniAppScaffold(onHome = onHome, screenTag = "count-screen", modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            ModeHeader(
                mood = mood,
                title = "Cloudculator",
                subtitle = "🧮 " + CountUiText.subtitle(language),
                isCompact = layout.isCompactPhone,
                isExpandedPad = layout.isExpandedPad,
                muted = muted,
                language = language,
                availableLanguages = availableLanguages,
                onParent = onParent,
                onToggleMute = { onSetMuted(!muted) },
                onCycleLanguage = onCycleLanguage,
            )
            CountReadout(
                target = round?.target ?: 0,
                progress = round?.progress ?: 0,
                numeral = countNumeral(round?.progress ?: 0),
                phrase = phrase,
                isCompact = layout.isCompactPhone,
                isExpandedPad = layout.isExpandedPad,
            )
            CountGrid(
                round = round,
                lastCounted = lastCounted,
                isCompact = layout.isCompactPhone,
                isExpandedPad = layout.isExpandedPad,
                onTap = ::onTapTile,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            CountControls(
                isComplete = round?.isComplete == true,
                canReplay = phrase.isNotEmpty() && !muted,
                language = language,
                isExpandedPad = layout.isExpandedPad,
                onShuffle = onShuffle,
                onReplay = onReplay,
                onNext = onNext,
            )
        }
    }
}

/**
 * The tiles, sized and columned to the round, and scaled for
 * [isExpandedPad] the same way iOS `CountView.grid` does (1.30× tile
 * side/glyph and the grid's own max width, 1.18× spacing — `columns` itself
 * is left unscaled, since it is a count, not a size).
 *
 * `LazyVerticalGrid`, unlike iOS's non-lazy `Grid` — a round is at most ten
 * tiles either way, so laziness buys nothing here, but nothing needs it not
 * to be lazy either: badge paint order (a counted/bouncing tile drawing over
 * its neighbours) is handled by `CountTile`'s own `zIndex`, not by list
 * realisation order.
 */
@Composable
private fun CountGrid(
    round: CountRound?,
    lastCounted: Int?,
    isCompact: Boolean,
    isExpandedPad: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (round == null) return
    val columns = CountTileMetrics.columns(round.target, isCompact)
    val side = CountTileMetrics.side(round.target, isCompact, isExpandedPad)
    val glyph = CountTileMetrics.glyphSize(round.target, isCompact, isExpandedPad)
    val spacing = CountTileMetrics.gridSpacing(round.target, isCompact, isExpandedPad)

    // The outer `Box` takes the full space [modifier] hands in (the weighted
    // remainder of the column) and centres its child on both axes; the grid
    // itself is capped to `maxGridWidth` and, left unconstrained on height,
    // sizes to its own content — small when the round is small, so it
    // centres vertically too, exactly like iOS's `GeometryReader` +
    // `ScrollView(minHeight: proxy.size.height)` trick, and scrolls via its
    // own `LazyVerticalGrid` machinery once a round of ten no longer fits.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(
                top = CountTileMetrics.badgeOverhang,
                end = CountTileMetrics.badgeOverhang,
                bottom = 6.dp,
            ),
            modifier = Modifier
                .widthIn(max = CountTileMetrics.maxGridWidth(isCompact, isExpandedPad))
                .padding(horizontal = if (isCompact) 12.dp else 20.dp)
                .testTag("count-grid"),
        ) {
            items(round.target) { index ->
                CountTile(
                    emoji = round.item.emoji,
                    index = index,
                    badge = round.badge(index),
                    isJustCounted = lastCounted == index,
                    side = side,
                    glyphSize = glyph,
                    onTap = { onTap(index) },
                )
            }
        }
    }
}

@Composable
private fun CountControls(
    isComplete: Boolean,
    canReplay: Boolean,
    language: Language,
    isExpandedPad: Boolean,
    onShuffle: () -> Unit,
    onReplay: () -> Unit,
    onNext: () -> Unit,
) {
    // 12dp between the controls themselves is unscaled — iOS's `controls`
    // HStack spacing is a flat `12` regardless of `isExpandedPad`; only each
    // `CountControl`'s own internal sizing scales, matching `CountView.swift`.
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        CountControl(
            glyph = "🔄",
            caption = CountUiText.shuffle(language),
            identifier = "count-shuffle",
            tint = Amber,
            isExpandedPad = isExpandedPad,
            action = onShuffle,
        )
        if (canReplay) {
            CountControl(
                glyph = "🔊",
                caption = CountUiText.replay(language),
                identifier = "count-replay",
                tint = Teal,
                isExpandedPad = isExpandedPad,
                action = onReplay,
            )
        }
        if (isComplete) {
            CountControl(
                glyph = "✨",
                caption = CountUiText.next(language),
                identifier = "count-next",
                tint = Teal,
                isExpandedPad = isExpandedPad,
                action = onNext,
            )
        }
    }
}

/**
 * Shuffle, Replay and Next. Child-facing, so 64dp — a two-year-old presses
 * these far more often than a parent does — 78dp for [isExpandedPad], along
 * with every other number here, matching iOS `CountControl`'s own
 * `layout.isExpandedPad` branches literally (`CountView.swift`): 9dp/6dp
 * internal spacing, 24sp/18sp glyph, 18sp/14sp caption, 30dp/22dp horizontal
 * padding.
 *
 * `internal`, not `private` — like `CountTile`/`CountReadout` in their own
 * files, this is what lets `CountChildTargetsTest` measure the 64dp/78dp
 * touch-target floors directly instead of standing up the whole screen's
 * dependency graph (`CountViewModel`, `MascotMoodMachine`, `SpeechController`,
 * ...) just to reach it.
 */
@Composable
internal fun CountControl(
    glyph: String,
    caption: String,
    identifier: String,
    tint: androidx.compose.ui.graphics.Color,
    isExpandedPad: Boolean,
    action: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isExpandedPad) 9.dp else 6.dp),
        modifier = Modifier
            .heightIn(min = if (isExpandedPad) 78.dp else 64.dp)
            .pressScale(interactionSource, 0.88f)
            .clip(shape)
            .background(tint.copy(alpha = 0.15f), shape)
            .border(2.dp, tint.copy(alpha = 0.3f), shape)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = action)
            .semantics {
                role = Role.Button
                contentDescription = caption
            }
            .testTag(identifier)
            .padding(horizontal = if (isExpandedPad) 30.dp else 22.dp),
    ) {
        Text(text = glyph, fontSize = if (isExpandedPad) 24.sp else 18.sp)
        Text(
            text = caption,
            color = tint,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = if (isExpandedPad) 18.sp else 14.sp,
            maxLines = 1,
        )
    }
}
