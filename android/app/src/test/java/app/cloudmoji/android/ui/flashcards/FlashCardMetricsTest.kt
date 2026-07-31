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

    // MARK: - The side-by-side remainder

    /**
     * **The regression this rule was nearly useless without.** In the
     * side-by-side (landscape) layout the choices share a `Row` with the
     * prompt card, and Compose measures a non-weighted child against the
     * row's *own* incoming max width — so asking Compose "how much room do
     * the choices have?" from inside that `Row` answers with nearly the whole
     * row, not the remainder. `choicesAvailableWidth` is the arithmetic that
     * replaces the measurement.
     *
     * The assertion that bites is the comparison: the same total width must
     * yield strictly less room side-by-side than stacked, by exactly the
     * prompt card plus its gap.
     *
     * Mutation proof: temporarily made `choicesAvailableWidth` ignore
     * `promptWidth` (the shape the broken measurement produced). Both
     * assertions below failed, then passed once restored.
     */
    @Test
    fun `the side-by-side layout subtracts the prompt card from the choices' width`() {
        val total = 640.dp
        val padding = FlashCardMetrics.horizontalPadding(sideBySide = true, isExpandedPad = false)
        val gap = FlashCardMetrics.sideBySideSpacing(isExpandedPad = false)
        val promptWidth = requireNotNull(
            FlashCardMetrics.promptCardWidth(compact = true, isExpandedPad = false, isLandscape = true),
        ) { "the side-by-side layout must have a fixed prompt width to subtract" }

        val beside = FlashCardMetrics.choicesAvailableWidth(total, padding, promptWidth, gap)
        val stacked = FlashCardMetrics.choicesAvailableWidth(total, padding, promptWidth = null, rowSpacing = gap)

        assertTrue("the prompt card was not subtracted at all", beside < stacked)
        assertEquals(stacked - promptWidth - gap, beside)
    }

    /**
     * The same arithmetic, carried through to the size a tile actually gets,
     * on a 560dp landscape phone — small enough that the remainder after a
     * 236dp prompt card cannot hold three preferred tiles, but large enough
     * that shrinking them fixes it. This is precisely the case the broken
     * measurement hid: the tiles stayed at 96dp and ran under the card.
     *
     * Mutation proof: same mutation as the test above (ignore `promptWidth`).
     * The `fitted < preferred` and the fits-in-the-remainder assertions both
     * failed, then passed once restored.
     */
    @Test
    fun `a narrow landscape phone shrinks its choices to fit beside the prompt card`() {
        val preferred = FlashCardMetrics.choiceSide(compact = true)
        val padding = FlashCardMetrics.horizontalPadding(sideBySide = true, isExpandedPad = false)
        val gap = FlashCardMetrics.sideBySideSpacing(isExpandedPad = false)
        val promptWidth = requireNotNull(
            FlashCardMetrics.promptCardWidth(compact = true, isExpandedPad = false, isLandscape = true),
        )

        val available = FlashCardMetrics.choicesAvailableWidth(560.dp, padding, promptWidth, gap)
        val fitted = FlashCardMetrics.fittedChoiceSide(available, preferred, count = 3)

        assertTrue("$available should not hold three ${preferred}s", fitted < preferred)
        assertTrue("$fitted is under the child floor", fitted >= FlashCardMetrics.childMinimum)
        assertTrue(
            "$fitted x3 overflows $available",
            fitted * 3 + FlashCardMetrics.spacing * 2 <= available,
        )
    }

    /**
     * …and past that, the floor wins and the row is *allowed* to overflow.
     * A 480dp landscape phone leaves 190dp beside the card, which three tiles
     * cannot share without going under 64dp — so they stay at 64dp and spill,
     * exactly as `fittedChoiceSide`'s own doc says. Asserted rather than left
     * implicit, because "it overflows here" is a deliberate choice
     * (`CLAUDE.md` rule 1 outranks fitting) and not a bug someone should
     * later "fix" by shrinking the tile.
     */
    @Test
    fun `past the floor the choices overflow rather than shrink further`() {
        val preferred = FlashCardMetrics.choiceSide(compact = true)
        val padding = FlashCardMetrics.horizontalPadding(sideBySide = true, isExpandedPad = false)
        val gap = FlashCardMetrics.sideBySideSpacing(isExpandedPad = false)

        val available = FlashCardMetrics.choicesAvailableWidth(480.dp, padding, promptWidth = 236.dp, rowSpacing = gap)
        val fitted = FlashCardMetrics.fittedChoiceSide(available, preferred, count = 3)

        assertEquals(FlashCardMetrics.childMinimum, fitted)
        assertTrue(
            "the floor case is expected to overflow; if it fits, this fixture is no longer the floor case",
            fitted * 3 + FlashCardMetrics.spacing * 2 > available,
        )
    }

    /** A roomy tablet in landscape still has space for the preferred size
     * after the 360dp prompt card — the subtraction must not over-shrink. */
    @Test
    fun `an expanded tablet in landscape keeps the preferred size after the subtraction`() {
        val preferred = FlashCardMetrics.choiceSide(compact = false, isExpandedPad = true)
        val padding = FlashCardMetrics.horizontalPadding(sideBySide = true, isExpandedPad = true)
        val gap = FlashCardMetrics.sideBySideSpacing(isExpandedPad = true)
        val promptWidth = requireNotNull(
            FlashCardMetrics.promptCardWidth(compact = false, isExpandedPad = true, isLandscape = true),
        )

        val available = FlashCardMetrics.choicesAvailableWidth(1366.dp, padding, promptWidth, gap)
        assertEquals(preferred, FlashCardMetrics.fittedChoiceSide(available, preferred, count = 3))
    }

    /**
     * The layout branch and the width subtraction must agree about which
     * layout is which: every side-by-side case has a fixed prompt width to
     * subtract, and every stacked one has none.
     *
     * Mutation proof: temporarily made `promptCardWidth` return `null` for the
     * compact case. This test failed on the compact-landscape row, then
     * passed once restored.
     */
    @Test
    fun `every side-by-side layout has a prompt width to subtract, and no stacked one does`() {
        // compact, isExpandedPad, isLandscape
        val cases = listOf(
            Triple(true, false, true), // phone landscape
            Triple(false, true, true), // tablet landscape
            Triple(false, false, false), // phone upright
            Triple(false, true, false), // tablet upright
            Triple(false, false, true), // tall phone landscape (not "compact")
        )
        for ((compact, isExpandedPad, isLandscape) in cases) {
            val sideBySide = FlashCardMetrics.isSideBySide(compact, isExpandedPad, isLandscape)
            val promptWidth = FlashCardMetrics.promptCardWidth(compact, isExpandedPad, isLandscape)
            assertEquals(
                "compact=$compact pad=$isExpandedPad landscape=$isLandscape",
                sideBySide,
                promptWidth != null,
            )
        }
    }

    /** A width too small for anything hands the fitting rule zero rather than
     * a negative number, and the floor still holds. */
    @Test
    fun `an impossibly narrow window still floors at the child minimum`() {
        val available = FlashCardMetrics.choicesAvailableWidth(
            totalWidth = 200.dp,
            horizontalPadding = FlashCardMetrics.horizontalPadding(sideBySide = true, isExpandedPad = false),
            promptWidth = 236.dp,
            rowSpacing = FlashCardMetrics.sideBySideSpacing(isExpandedPad = false),
        )
        assertEquals(0.dp, available)
        assertEquals(
            FlashCardMetrics.childMinimum,
            FlashCardMetrics.fittedChoiceSide(available, FlashCardMetrics.choiceSide(compact = true), count = 3),
        )
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
