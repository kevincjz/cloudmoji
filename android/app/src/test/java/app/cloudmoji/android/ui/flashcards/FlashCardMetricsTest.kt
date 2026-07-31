package app.cloudmoji.android.ui.flashcards

import androidx.compose.ui.unit.dp
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `FlashCardMetrics`'s own arithmetic and `FlashCardsUiText`'s tables — plain
 * `Dp`/list/map lookups with no Compose runtime behind them, so, like
 * `CountTileMetricsTest`/`InstrumentPadMetricsTest`, this runs on the plain
 * JVM. The Compose half of the touch-target contract lives in
 * `app/src/androidTest/.../FlashCardsChildTargetsTest.kt`, which cannot be
 * executed in this environment.
 */
class FlashCardMetricsTest {

    /**
     * `CLAUDE.md` rule 1 for every layout a choice tile is drawn in. Flash
     * Cards is far past the floor by design — there are only ever three tiles
     * and the child is choosing between them from across the room — so this
     * asserts the floor *and* that the smallest of the three is still
     * comfortably above the 72dp preferred size.
     *
     * Mutation proof: temporarily changed the `compact` branch of
     * `choiceSide` from 96dp to 48dp. This test failed on both the
     * `childMinimum` and the 72dp assertion, then passed again once restored.
     */
    @Test
    fun `a choice tile clears the child touch-target floor in every layout`() {
        val sizes = listOf(
            FlashCardMetrics.choiceSide(compact = false, isExpandedPad = false),
            FlashCardMetrics.choiceSide(compact = true, isExpandedPad = false),
            FlashCardMetrics.choiceSide(compact = false, isExpandedPad = true),
            FlashCardMetrics.choiceSide(compact = true, isExpandedPad = true),
        )
        for (size in sizes) {
            assertTrue(
                "$size is under the ${FlashCardMetrics.childMinimum} floor",
                size >= FlashCardMetrics.childMinimum,
            )
            assertTrue("$size is under the 72dp preferred child size", size.value >= 72f)
        }
    }

    /**
     * The Android-only fitting rule, and the reason it exists: three 110dp
     * tiles plus their gaps come to 354dp, which fits iOS's narrowest phone
     * (375pt) but not a 360dp Android one, and certainly not a 320dp one. A
     * fixed size there would push the outer tiles off the screen edge.
     *
     * Mutation proof: temporarily made `fittedChoiceSide` return `preferred`
     * unconditionally. The 360dp and 320dp assertions failed (354dp of tiles
     * in 336dp of space), then passed once restored.
     */
    @Test
    fun `choices are fitted to a narrow phone instead of running off it`() {
        val preferred = FlashCardMetrics.choiceSide(compact = false)
        val spacing = FlashCardMetrics.spacing

        // A 360dp phone, less the stacked layout's 12dp side padding.
        val narrow = FlashCardMetrics.fittedChoiceSide(336.dp, preferred, count = 3)
        assertTrue("$narrow overflows 336dp", narrow * 3 + spacing * 2 <= 336.dp)
        assertTrue("$narrow is under the child floor", narrow >= FlashCardMetrics.childMinimum)

        // A 320dp phone, same padding.
        val tiny = FlashCardMetrics.fittedChoiceSide(296.dp, preferred, count = 3)
        assertTrue("$tiny is under the child floor", tiny >= FlashCardMetrics.childMinimum)
        assertTrue(tiny <= narrow)
    }

    /**
     * …but the floor is not negotiable. A screen too narrow even for three
     * 64dp tiles overflows rather than shrinking one under `CLAUDE.md`
     * rule 1 — the same call `InstrumentPadMetrics.side` makes.
     *
     * Mutation proof: temporarily removed the `fitted <= childMinimum`
     * branch. This test failed at about 25dp per tile, then passed once
     * restored.
     */
    @Test
    fun `fitting never shrinks a choice under the child floor`() {
        val cramped = FlashCardMetrics.fittedChoiceSide(
            availableWidth = 100.dp,
            preferred = FlashCardMetrics.choiceSide(compact = false),
            count = 3,
        )
        assertEquals(FlashCardMetrics.childMinimum, cramped)
    }

    /** A roomy screen gets the full preferred size and the full glyph — the
     * fitting rule must be inert whenever there is space. */
    @Test
    fun `a roomy screen gets the preferred size untouched`() {
        val preferred = FlashCardMetrics.choiceSide(compact = false)
        val glyph = FlashCardMetrics.glyphSize(compact = false)

        val fitted = FlashCardMetrics.fittedChoiceSide(1000.dp, preferred, count = 3)
        assertEquals(preferred, fitted)
        assertEquals(glyph, FlashCardMetrics.fittedGlyphSize(preferred, fitted, glyph))

        // …and the glyph shrinks in step when the plate does.
        val shrunk = FlashCardMetrics.fittedChoiceSide(296.dp, preferred, count = 3)
        assertTrue(FlashCardMetrics.fittedGlyphSize(preferred, shrunk, glyph).value < glyph.value)
    }

    /** The replay button is child-facing — the control a two-year-old reaches
     * for when he did not catch the word — so it takes the 64dp floor, not
     * the 44dp parent-chrome one. */
    @Test
    fun `the replay button clears the child touch-target floor`() {
        assertTrue(FlashCardMetrics.replaySide >= FlashCardMetrics.childMinimum)
        assertTrue(FlashCardMetrics.padReplaySide >= FlashCardMetrics.replaySide)
    }

    /** `CLAUDE.md` rule 2: at least 8dp between two things a child taps. */
    @Test
    fun `choices are spaced at least the required gap apart`() {
        assertTrue(FlashCardMetrics.spacing.value >= 8f)
    }

    /**
     * A tablet gets tablet-sized tiles, and a phone in landscape gets smaller
     * ones than a phone upright — iOS `FlashCardMetrics`'s three literals
     * (110/96/148), asserted as an ordering so a future re-tune cannot
     * silently collapse the three cases into one.
     */
    @Test
    fun `an expanded tablet gets the largest tiles and a compact phone the smallest`() {
        val regular = FlashCardMetrics.choiceSide(compact = false)
        val compact = FlashCardMetrics.choiceSide(compact = true)
        val pad = FlashCardMetrics.choiceSide(compact = false, isExpandedPad = true)

        assertTrue(compact < regular)
        assertTrue(regular < pad)
        // The glyph moves with the plate, or a tablet gets a phone-sized
        // emoji adrift in a large tile.
        assertTrue(
            FlashCardMetrics.glyphSize(compact = true).value <
                FlashCardMetrics.glyphSize(compact = false).value,
        )
        assertTrue(
            FlashCardMetrics.glyphSize(compact = false).value <
                FlashCardMetrics.glyphSize(compact = false, isExpandedPad = true).value,
        )
    }

    /**
     * Three tiles, three distinct colours and three distinct tilts, wrapping
     * after that — a child aims at "the yellow one", and three identical
     * plates in a row read as a form rather than a hand of cards.
     *
     * Mutation proof: temporarily made `tint` return `tints[0]` always. The
     * distinct-colour assertion failed, then passed once restored.
     */
    @Test
    fun `the three choices get three colours and three tilts, then wrap`() {
        val tints = (0 until 3).map { FlashCardMetrics.tint(it) }
        assertEquals("three tiles must not share a colour", 3, tints.toSet().size)
        assertEquals("a fourth tile wraps to the first colour", tints[0], FlashCardMetrics.tint(3))

        val tilts = (0 until 3).map { FlashCardMetrics.tilt(it) }
        assertEquals("three tiles must not share a tilt", 3, tilts.toSet().size)
        assertEquals(tilts[0], FlashCardMetrics.tilt(3))
        // A negative index cannot arise from a `forEachIndexed`, but `mod`
        // (not `%`) is what keeps it from throwing if it ever did.
        assertEquals(tilts[2], FlashCardMetrics.tilt(-1))
    }

    /**
     * Chrome in all five languages, with no gaps — a missing row would fall
     * back to English and quietly hand a Malay-speaking family an English
     * prompt.
     *
     * Mutation proof: temporarily deleted the Tagalog prompt row. The
     * "distinct" assertion failed (Tagalog fell back to the English string),
     * then passed once restored.
     */
    @Test
    fun `the prompt and replay captions are translated into all five languages`() {
        val prompts = Language.entries.associateWith { FlashCardsUiText.prompt(it) }
        val replays = Language.entries.associateWith { FlashCardsUiText.replay(it) }

        for (language in Language.entries) {
            assertTrue("$language has no prompt", prompts.getValue(language).isNotEmpty())
            assertTrue("$language has no replay caption", replays.getValue(language).isNotEmpty())
        }
        assertEquals("two languages share a prompt", 5, prompts.values.toSet().size)
        assertEquals("two languages share a replay caption", 5, replays.values.toSet().size)
        assertNotEquals(
            FlashCardsUiText.prompt(Language.English),
            FlashCardsUiText.prompt(Language.Japanese),
        )
    }
}
