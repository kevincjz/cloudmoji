package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Words mode's own state — the typing row, the word bubble's lifecycle, and
 * the bouncing tile — independent of Compose. Ported from the behavioural
 * contract `WordsView.swift`/`WordsMode.tsx` encode for `typed`/`bubble`/
 * `bouncingID`.
 *
 * [FakeWordsScheduler] deliberately does **not** suppress a cancelled
 * entry's action, mirroring `MascotMoodMachineTest`'s `FakeMascotScheduler`
 * and `SpeechControllerTest`'s `FakeSpeechWatchdogScheduler`: the guard
 * against a stale, superseded timer resuming has to be [WordsViewModel]'s
 * own generation counters, not the scheduler's cooperation, so several tests
 * below fire an old handle on purpose to prove that guard actually holds.
 */
class WordsViewModelTest {

    private class FakeWordsScheduler : WordsScheduler {
        private class Entry(val action: () -> Unit) : WordsScheduleHandle {
            override fun cancel() = Unit // see the class doc.
        }

        private val scheduled = mutableListOf<Entry>()

        override fun schedule(delayMillis: Long, action: () -> Unit): WordsScheduleHandle {
            val entry = Entry(action)
            scheduled += entry
            return entry
        }

        val scheduledCount: Int get() = scheduled.size

        /** Fires the most recently scheduled callback. */
        fun fireLatest() = scheduled.last().action()

        /** Fires the callback scheduled at [index], in scheduling order —
         * for firing an old, already-superseded one on purpose. */
        fun fire(index: Int) = scheduled[index].action()
    }

    private data class Fixture(val viewModel: WordsViewModel, val scheduler: FakeWordsScheduler)

    private fun makeViewModel(): Fixture {
        val scheduler = FakeWordsScheduler()
        return Fixture(WordsViewModel(scheduler), scheduler)
    }

    private fun entry(emoji: String, category: Category = Category.Fruits): EmojiEntry =
        EmojiEntry(emoji = emoji, category = category, en = "en-$emoji", zh = "zh-$emoji", ms = "ms-$emoji", ja = "ja-$emoji", tl = "tl-$emoji")

    private fun tab(id: String, icon: String = "🍇"): CategoryTab =
        CategoryTab(id = id, icon = icon, labels = mapOf("en" to id))

    // MARK: - Typing row: append + cap

    @Test
    fun `starts with an empty row, no bubble, and nothing bouncing`() {
        val (viewModel, _) = makeViewModel()
        assertTrue(viewModel.typed.value.isEmpty())
        assertNull(viewModel.bubble.value)
        assertNull(viewModel.bouncingId.value)
    }

    @Test
    fun `tapping an emoji appends it to the row with the given word`() {
        val (viewModel, _) = makeViewModel()
        val item = viewModel.tapEmoji(entry("🍎"), "apple")

        assertEquals("apple", item.word)
        assertEquals("🍎", item.emoji)
        assertEquals(listOf(item), viewModel.typed.value)
    }

    @Test
    fun `the same emoji tapped twice produces two distinct row entries`() {
        val (viewModel, _) = makeViewModel()
        val first = viewModel.tapEmoji(entry("🍎"), "apple")
        val second = viewModel.tapEmoji(entry("🍎"), "apple")

        assertTrue("repeat taps must not collide on identity", first.id != second.id)
        assertEquals(listOf(first, second), viewModel.typed.value)
    }

    @Test
    fun `the row is capped at 50, oldest dropped first`() {
        val (viewModel, _) = makeViewModel()
        repeat(WordsViewModel.MAX_TYPED + 5) { index ->
            viewModel.tapEmoji(entry("e$index"), "word$index")
        }

        val typed = viewModel.typed.value
        assertEquals(WordsViewModel.MAX_TYPED, typed.size)
        // The first 5 taps (e0..e4) must have been dropped; e5 is the oldest survivor.
        assertEquals("e5", typed.first().emoji)
        assertEquals("e${WordsViewModel.MAX_TYPED + 4}", typed.last().emoji)
    }

    // MARK: - Typing row: delete / clear

    @Test
    fun `deleting the last emoji removes only the last`() {
        val (viewModel, _) = makeViewModel()
        val first = viewModel.tapEmoji(entry("🍎"), "apple")
        viewModel.tapEmoji(entry("🐶"), "dog")

        viewModel.deleteLast()

        assertEquals(listOf(first), viewModel.typed.value)
    }

    @Test
    fun `deleting from an empty row is a no-op`() {
        val (viewModel, _) = makeViewModel()
        viewModel.deleteLast()
        assertTrue(viewModel.typed.value.isEmpty())
    }

    @Test
    fun `clearing empties the row and drops the bubble`() {
        val (viewModel, _) = makeViewModel()
        viewModel.tapEmoji(entry("🍎"), "apple")

        viewModel.clear()

        assertTrue(viewModel.typed.value.isEmpty())
        assertNull(viewModel.bubble.value)
    }

    @Test
    fun `a bubble hold that was already in flight when clear ran cannot resurrect it`() {
        val (viewModel, scheduler) = makeViewModel()
        viewModel.tapEmoji(entry("🍎"), "apple") // arms bubble hold #0
        viewModel.clear()
        viewModel.tapEmoji(entry("🐶"), "dog") // arms bubble hold #1, a fresh bubble

        // The stale hold from the tap before `clear()` must not clear the
        // *new* bubble that clear()'s own generation bump, followed by a
        // fresh tap, put up afterward.
        scheduler.fire(0)

        assertEquals("dog", viewModel.bubble.value?.word)
    }

    // MARK: - Bubble lifecycle

    @Test
    fun `the bubble hold fires after BUBBLE_HOLD_MS and clears the bubble`() {
        // `showBubble` directly, not `tapEmoji`, so the only timer in play is
        // the bubble's own — `tapEmoji` also arms a bounce hold, and this
        // test has no opinion about that one.
        val (viewModel, scheduler) = makeViewModel()
        viewModel.showBubble(TypedEmoji(id = 0, emoji = "🍎", word = "apple"))
        assertEquals("apple", viewModel.bubble.value?.word)

        scheduler.fireLatest()

        assertNull(viewModel.bubble.value)
    }

    @Test
    fun `a second bubble before the first one's hold fires re-arms it instead of stacking`() {
        val (viewModel, scheduler) = makeViewModel()
        viewModel.showBubble(TypedEmoji(id = 0, emoji = "🍎", word = "apple"))
        viewModel.showBubble(TypedEmoji(id = 1, emoji = "🐶", word = "dog"))

        assertEquals(2, scheduler.scheduledCount)
        assertEquals("dog", viewModel.bubble.value?.word)

        // The stale first hold (superseded by the second bubble) must be a
        // no-op even though the fake scheduler does not suppress it.
        scheduler.fire(0)
        assertEquals(
            "a superseded bubble hold must not clear the newer bubble",
            "dog",
            viewModel.bubble.value?.word,
        )

        scheduler.fireLatest() // the second bubble's own hold
        assertNull(viewModel.bubble.value)
    }

    @Test
    fun `re-tapping an already-typed emoji shows its bubble again without changing the row`() {
        val (viewModel, _) = makeViewModel()
        val item = viewModel.tapEmoji(entry("🍎"), "apple")
        val rowBefore = viewModel.typed.value

        viewModel.showBubble(item)

        assertEquals(rowBefore, viewModel.typed.value)
        assertEquals(item, viewModel.bubble.value)
    }

    // MARK: - Bounce lifecycle

    @Test
    fun `tapping an emoji bounces its tile by entry id`() {
        val (viewModel, _) = makeViewModel()
        val apple = entry("🍎")
        viewModel.tapEmoji(apple, "apple")

        assertEquals(apple.id, viewModel.bouncingId.value)
    }

    @Test
    fun `the bounce hold fires after BOUNCE_HOLD_MS and clears it`() {
        val (viewModel, scheduler) = makeViewModel()
        viewModel.tapEmoji(entry("🍎"), "apple")

        // Two timers armed per tap (bubble hold, bounce hold); the bounce
        // hold is scheduled second by `tapEmoji`.
        scheduler.fireLatest()

        assertNull(viewModel.bouncingId.value)
    }

    @Test
    fun `a superseded bounce hold does not clear the tile that is bouncing now`() {
        val (viewModel, scheduler) = makeViewModel()
        val apple = entry("🍎")
        val dog = entry("🐶")
        viewModel.tapEmoji(apple, "apple") // bubble hold #0, bounce hold #1
        viewModel.tapEmoji(dog, "dog") // bubble hold #2, bounce hold #3

        assertEquals(dog.id, viewModel.bouncingId.value)

        // Apple's stale bounce hold (index 1) must not clear dog's bounce.
        scheduler.fire(1)
        assertEquals(
            "a superseded bounce hold must not clear the tile bouncing now",
            dog.id,
            viewModel.bouncingId.value,
        )

        scheduler.fireLatest() // dog's own bounce hold
        assertNull(viewModel.bouncingId.value)
    }

    // MARK: - Category chip taps

    @Test
    fun `tapping a category chip shows its icon and label as the bubble, without touching the row`() {
        val (viewModel, _) = makeViewModel()
        val jump = viewModel.tapCategory(tab("animals", icon = "🐾"), "Animals")

        assertEquals("animals", jump.id)
        assertEquals("🐾", viewModel.bubble.value?.emoji)
        assertEquals("Animals", viewModel.bubble.value?.word)
        assertTrue("a category tap must never append to the typing row", viewModel.typed.value.isEmpty())
    }

    @Test
    fun `every category tap advances the jump token, even for the same tab twice in a row`() {
        val (viewModel, _) = makeViewModel()
        val animalsTab = tab("animals")

        val first = viewModel.tapCategory(animalsTab, "Animals")
        val second = viewModel.tapCategory(animalsTab, "Animals")

        assertEquals("animals", first.id)
        assertEquals("animals", second.id)
        assertTrue(
            "tapping the same chip twice must still produce a fresh token, or the second tap would not re-scroll",
            second.token != first.token,
        )
    }

    // MARK: - Language change

    @Test
    fun `a language change refreshes every typed word using the lookup, keeping identity and order`() {
        val (viewModel, _) = makeViewModel()
        val apple = viewModel.tapEmoji(entry("🍎"), "apple")
        val dog = viewModel.tapEmoji(entry("🐶"), "dog")

        viewModel.onLanguageChanged { emoji ->
            when (emoji) {
                "🍎" -> "苹果"
                "🐶" -> "狗"
                else -> null
            }
        }

        val refreshed = viewModel.typed.value
        assertEquals(listOf(apple.id, dog.id), refreshed.map { it.id })
        assertEquals(listOf("苹果", "狗"), refreshed.map { it.word })
    }

    @Test
    fun `a language change that cannot resolve a word keeps the previous one rather than dropping the row entry`() {
        val (viewModel, _) = makeViewModel()
        viewModel.tapEmoji(entry("🎈"), "balloon")

        viewModel.onLanguageChanged { null }

        assertEquals(1, viewModel.typed.value.size)
        assertEquals("balloon", viewModel.typed.value.single().word)
    }

    // MARK: - Product spec

    @Test
    fun `timings and cap match the product spec`() {
        assertEquals(50, WordsViewModel.MAX_TYPED)
        assertEquals(2200L, WordsViewModel.BUBBLE_HOLD_MS)
        assertEquals(400L, WordsViewModel.BOUNCE_HOLD_MS)
    }
}
