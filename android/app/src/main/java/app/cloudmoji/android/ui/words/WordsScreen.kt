package app.cloudmoji.android.ui.words

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.CategoryTab
import app.cloudmoji.android.model.CoroutineMascotScheduler
import app.cloudmoji.android.model.CoroutineWordsScheduler
import app.cloudmoji.android.model.EmojiEntry
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta
import app.cloudmoji.android.model.MascotMoodMachine
import app.cloudmoji.android.model.SectionJump
import app.cloudmoji.android.model.TypedEmoji
import app.cloudmoji.android.model.WordsViewModel
import app.cloudmoji.android.model.buildSections
import app.cloudmoji.android.platform.SpeechController
import app.cloudmoji.android.platform.SpeechItem
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.MiniAppScaffold
import app.cloudmoji.android.ui.common.ModeHeader
import app.cloudmoji.android.ui.common.SideRail

/**
 * Words mode — the whole app, as far as a two-year-old is concerned. Ported
 * from iOS `WordsView.swift` / web `WordsMode.tsx`. One tap does four things
 * at once: the word is spoken, it floats up in a bubble, the tile bounces,
 * and the emoji joins the typing row.
 *
 * Every piece of policy-filtered state ([enabledCategories], [language],
 * [muted], [availableLanguages]) arrives already resolved — this screen makes
 * no accessibility or settings decision of its own, per the Task 6 brief.
 * [repository] and [speechController] are constructed once at the app shell
 * and shared with every future mini-app; [WordsViewModel] and the mascot's
 * [MascotMoodMachine] are owned here instead — screen-scoped, so leaving
 * Words mid-utterance discards the speaking face (and the typed row) along
 * with the rest of this screen, mirroring iOS's `@State`-scoped `WordsView`.
 */
@Composable
fun WordsScreen(
    repository: EmojiRepository,
    enabledCategories: Set<Category>,
    language: Language,
    muted: Boolean,
    availableLanguages: List<LanguageMeta>,
    speechController: SpeechController,
    onSetMuted: (Boolean) -> Unit,
    onCycleLanguage: () -> Unit,
    onHome: () -> Unit,
    onParent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val scope = rememberCoroutineScope()

    val mascotMoodMachine = remember { MascotMoodMachine(CoroutineMascotScheduler(scope)) }
    val wordsViewModel = remember { WordsViewModel(CoroutineWordsScheduler(scope)) }

    val mood by mascotMoodMachine.mood.collectAsState()
    val typed by wordsViewModel.typed.collectAsState()
    val bubble by wordsViewModel.bubble.collectAsState()
    val bouncingId by wordsViewModel.bouncingId.collectAsState()

    val sections = remember(repository, enabledCategories) { buildSections(repository, enabledCategories) }
    val tabLabel: (CategoryTab) -> String = { it.label(language) }
    val tabs = remember(sections) { sections.map { it.tab } }

    var jump by remember { mutableStateOf<SectionJump?>(null) }
    var activeSectionId by remember { mutableStateOf("") }

    // Bridges the speech engine's own state into the mascot's mood machine —
    // exactly the "two-line StateFlow collector" `MascotMoodMachine`'s own
    // doc anticipates. Screen-scoped like the machine itself: it stops the
    // instant Words is torn down.
    LaunchedEffect(speechController, mascotMoodMachine) {
        speechController.isSpeaking.collect { speaking ->
            if (speaking) mascotMoodMachine.onSpeechStarted() else mascotMoodMachine.onSpeechFinished()
        }
    }

    // A language or mute change silences whatever is already queued —
    // mirrors iOS `WordsView`'s `silence()`, fired from its own `onChange`.
    // Without this the phone finishes the previous language's word after a
    // switch, or keeps talking after mute.
    LaunchedEffect(language, muted) {
        speechController.cancelAll()
    }

    // Keeps every already-typed emoji's word current after a language
    // change — otherwise a repeat tap, or a replay, speaks the emoji in its
    // old language's phonetics through the new voice.
    LaunchedEffect(language) {
        wordsViewModel.onLanguageChanged { emoji -> repository.entry(emoji)?.word(language) }
    }

    DisposableEffect(speechController) {
        onDispose { speechController.cancelAll() }
    }

    fun speakTapped(word: String) {
        mascotMoodMachine.onTap()
        speechController.speak(word, language)
    }

    val onTapEmoji: (EmojiEntry) -> Unit = { entry ->
        val word = entry.word(language)
        wordsViewModel.tapEmoji(entry, word)
        speakTapped(word)
    }

    val onTapTyped: (TypedEmoji) -> Unit = { item ->
        wordsViewModel.showBubble(item)
        speakTapped(item.word)
    }

    val onSelectCategory: (CategoryTab) -> Unit = { tab ->
        val label = tabLabel(tab)
        jump = wordsViewModel.tapCategory(tab, label)
        speakTapped(label)
    }

    val onReplay: () -> Unit = {
        if (typed.isNotEmpty() && !muted) {
            speechController.speakSequence(
                typed.map { item -> SpeechItem(text = item.word) { wordsViewModel.showBubble(item) } },
                language,
            )
        }
    }

    val onDelete: () -> Unit = {
        speechController.cancelAll()
        wordsViewModel.deleteLast()
    }

    val onClear: () -> Unit = {
        speechController.cancelAll()
        wordsViewModel.clear()
    }

    MiniAppScaffold(onHome = onHome, screenTag = "words-screen", modifier = modifier) {
        if (layout.isCompactPhone) {
            Row(modifier = Modifier.fillMaxSize()) {
                SideRail {
                    CategoryChips(
                        tabs = tabs,
                        selectedId = activeSectionId,
                        labelFor = tabLabel,
                        layout = CategoryChipLayout.Rail,
                        isExpandedPad = layout.isExpandedPad,
                        onSelect = onSelectCategory,
                    )
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    ModeHeader(
                        mood = mood,
                        title = "Cloudmoji",
                        subtitle = "Tap. Listen. Learn!",
                        isCompact = true,
                        isExpandedPad = layout.isExpandedPad,
                        muted = muted,
                        language = language,
                        availableLanguages = availableLanguages,
                        onParent = onParent,
                        onToggleMute = { onSetMuted(!muted) },
                        onCycleLanguage = onCycleLanguage,
                    )
                    TypingRow(
                        typed = typed,
                        muted = muted,
                        language = language,
                        onReplay = onReplay,
                        onDelete = onDelete,
                        onClear = onClear,
                        onTapTyped = onTapTyped,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        EmojiGrid(
                            sections = sections,
                            bouncingId = bouncingId,
                            wordFor = { it.word(language) },
                            labelFor = { section -> tabLabel(section.tab) },
                            jumpTo = jump,
                            isExpandedPad = layout.isExpandedPad,
                            onActiveSection = { activeSectionId = it },
                            onTap = onTapEmoji,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Sideways there is no height to spend on a reserved
                        // row, so the bubble floats over the grid instead of
                        // pushing it down.
                        bubble?.let {
                            Box(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                            ) {
                                WordBubble(emoji = it.emoji, word = it.word, id = it.id)
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ModeHeader(
                    mood = mood,
                    title = "Cloudmoji",
                    subtitle = "Tap. Listen. Learn!",
                    isCompact = false,
                    isExpandedPad = layout.isExpandedPad,
                    muted = muted,
                    language = language,
                    availableLanguages = availableLanguages,
                    onParent = onParent,
                    onToggleMute = { onSetMuted(!muted) },
                    onCycleLanguage = onCycleLanguage,
                )
                TypingRow(
                    typed = typed,
                    muted = muted,
                    language = language,
                    onReplay = onReplay,
                    onDelete = onDelete,
                    onClear = onClear,
                    onTapTyped = onTapTyped,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                // Reserves the bubble's own row so the category strip and
                // grid below it never jump on every tap — an absent
                // conditional branch drops its frame along with itself.
                Box(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    bubble?.let { WordBubble(emoji = it.emoji, word = it.word, id = it.id) }
                }
                CategoryChips(
                    tabs = tabs,
                    selectedId = activeSectionId,
                    labelFor = tabLabel,
                    layout = CategoryChipLayout.Strip,
                    isExpandedPad = layout.isExpandedPad,
                    onSelect = onSelectCategory,
                )
                EmojiGrid(
                    sections = sections,
                    bouncingId = bouncingId,
                    wordFor = { it.word(language) },
                    labelFor = { section -> tabLabel(section.tab) },
                    jumpTo = jump,
                    isExpandedPad = layout.isExpandedPad,
                    onActiveSection = { activeSectionId = it },
                    onTap = onTapEmoji,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}
