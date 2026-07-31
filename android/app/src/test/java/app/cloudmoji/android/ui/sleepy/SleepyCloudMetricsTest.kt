package app.cloudmoji.android.ui.sleepy

import androidx.compose.ui.unit.dp
import app.cloudmoji.android.model.BreathPhase
import app.cloudmoji.android.model.BreathingSession
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SleepyCloudMetrics] and [SleepyUiText] — the parts of `SleepyCloudScreen`
 * that are pure arithmetic and pure tables, and therefore the parts that can
 * be silently wrong without anyone noticing on a screenshot. Everything here
 * runs on the JVM; the Compose half of this screen lives in
 * `app/src/androidTest/.../SleepyChildTargetsTest.kt` and cannot execute in
 * this environment (see `conventions.md`).
 */
class SleepyCloudMetricsTest {

    /**
     * The child-facing touch-target contract, `CLAUDE.md` rule 1 — the
     * duration plates are tapped by a child as often as by a grown-up, which
     * is exactly why iOS overrode the prototype's 56pt buttons.
     *
     * Mutation proof: temporarily set `choiceHeight` to `56.dp` (the
     * prototype's own number, i.e. the regression this guards). This test
     * failed before it was restored to 64.
     */
    @Test
    fun `every child-facing target clears the 64dp floor`() {
        val floor = 64.dp

        assertTrue("choiceHeight ${SleepyCloudMetrics.choiceHeight}", SleepyCloudMetrics.choiceHeight >= floor)
        assertTrue("choiceWidth ${SleepyCloudMetrics.choiceWidth}", SleepyCloudMetrics.choiceWidth >= floor)
        assertTrue("padChoiceHeight", SleepyCloudMetrics.padChoiceHeight >= floor)
        assertTrue("padChoiceWidth", SleepyCloudMetrics.padChoiceWidth >= floor)
        assertTrue("againWidth", SleepyCloudMetrics.againWidth >= floor)
        assertTrue("againHeight", SleepyCloudMetrics.againHeight >= floor)

        // `CLAUDE.md` rule 2: the gap between adjacent child targets.
        assertTrue("choiceSpacing", SleepyCloudMetrics.choiceSpacing >= 8.dp)
        assertTrue("padChoiceSpacing", SleepyCloudMetrics.padChoiceSpacing >= 8.dp)
    }

    /**
     * The dim ramp: nothing at the start, [SleepyCloudMetrics.MAXIMUM_DIM] at
     * the end, and never past either bound whatever it is handed.
     *
     * Mutation proof: temporarily removed the `.coerceIn(0.0, 1.0)` from
     * [SleepyCloudMetrics.dim]. The over-run case below returned 1.1 — an
     * alpha past opaque, i.e. a black screen — before it was restored.
     */
    @Test
    fun `the dim ramp runs from nothing to the maximum and stops there`() {
        assertEquals(0.0, SleepyCloudMetrics.dim(0.0), 0.0001)
        assertEquals(SleepyCloudMetrics.MAXIMUM_DIM, SleepyCloudMetrics.dim(1.0), 0.0001)
        assertEquals(SleepyCloudMetrics.MAXIMUM_DIM / 2, SleepyCloudMetrics.dim(0.5), 0.0001)

        // A progress out of range is a bug elsewhere; it must not become an
        // invalid alpha here.
        assertEquals(SleepyCloudMetrics.MAXIMUM_DIM, SleepyCloudMetrics.dim(2.0), 0.0001)
        assertEquals(0.0, SleepyCloudMetrics.dim(-1.0), 0.0001)

        // The scrim carries only half of it — the room gets quieter, not
        // merely black. iOS: `Color.black.opacity(dim * 0.5)`.
        val deepest = SleepyCloudMetrics.dim(1.0) * SleepyCloudMetrics.SCRIM_SHARE
        assertTrue("the scrim reaches $deepest, which is more than half-opaque", deepest <= 0.5)
    }

    /**
     * The sky is the same sky every night: the star positions are the
     * prototype's own arithmetic, not random, so a child does not get a new
     * constellation each time the screen opens.
     *
     * Mutation proof: temporarily changed [SleepyCloudMetrics.starYFraction]'s
     * `% 90` to `% 100` (the plausible typo — the X row uses 100). The
     * "stars stay clear of the bottom" assertion failed before it was
     * restored.
     */
    @Test
    fun `the starfield is deterministic and stays inside the sky`() {
        val first = (0 until SleepyCloudMetrics.STAR_COUNT).map {
            SleepyCloudMetrics.starXFraction(it) to SleepyCloudMetrics.starYFraction(it)
        }
        val second = (0 until SleepyCloudMetrics.STAR_COUNT).map {
            SleepyCloudMetrics.starXFraction(it) to SleepyCloudMetrics.starYFraction(it)
        }
        assertEquals("the sky moved between two reads", first, second)

        for ((x, y) in first) {
            assertTrue("a star sits off the left/right edge at $x", x in 0f..1f)
            assertTrue("a star sits below the sky at $y", y in 0f..0.9f)
        }

        // Not all in one place, either — a "deterministic" starfield of
        // fourteen identical points would pass everything above.
        assertTrue("every star landed on the same spot", first.toSet().size > 1)
    }

    /**
     * Every star breathes between the two documented opacities, and no two
     * neighbours are in step — iOS gives each its own period and delay, and
     * a shared clock that lost them would produce fourteen stars blinking as
     * one, which reads as a flashing screen rather than a night sky.
     *
     * Mutation proof: temporarily dropped the `- delay` term from
     * [SleepyCloudMetrics.starTwinkleAlpha]. Stars 0 and 4 (same `index % 4`,
     * so the same period) then twinkled identically and the "out of step"
     * assertion failed before it was restored.
     */
    @Test
    fun `stars twinkle between the two opacities, out of step with each other`() {
        for (clockStep in 0..20) {
            val clock = clockStep / 20f
            for (index in 0 until SleepyCloudMetrics.STAR_COUNT) {
                val alpha = SleepyCloudMetrics.starTwinkleAlpha(index, clock)
                assertTrue(
                    "star $index at clock $clock had alpha $alpha",
                    alpha >= SleepyCloudMetrics.STAR_DIM_ALPHA - 0.0001f &&
                        alpha <= SleepyCloudMetrics.STAR_BRIGHT_ALPHA + 0.0001f,
                )
            }
        }

        assertNotEquals(
            "stars 0 and 4 share a period and must still be out of step",
            SleepyCloudMetrics.starTwinkleAlpha(0, 0.3f),
            SleepyCloudMetrics.starTwinkleAlpha(4, 0.3f),
        )
    }

    /** Sideways the cloud is drawn small: a landscape phone gives about
     * 400dp of height, and the upright stack overflowed it and clipped the
     * cloud against the top edge. iOS `SleepyCloudView.cloudWidth`. */
    @Test
    fun `the cloud shrinks sideways and grows on a tablet`() {
        val phone = SleepyCloudMetrics.cloudWidth(isExpandedPad = false, isCompactPhone = false)
        val sideways = SleepyCloudMetrics.cloudWidth(isExpandedPad = false, isCompactPhone = true)
        val tablet = SleepyCloudMetrics.cloudWidth(isExpandedPad = true, isCompactPhone = false)

        assertEquals(BreathingCloudMetrics.renderedWidth, phone)
        assertEquals(BreathingCloudMetrics.compactRenderedWidth, sideways)
        assertTrue("sideways is not smaller than upright", sideways < phone)
        assertTrue("a tablet does not get a bigger cloud", tablet > phone)
    }

    /**
     * Every table carries all five languages. A missing row is a child
     * seeing English inside an otherwise Malay app — the exact failure
     * `CLAUDE.md` rule 24 exists to prevent, and one that no test of the
     * *English* path would ever catch.
     *
     * Mutation proof: temporarily deleted the Tagalog row from
     * [SleepyUiText.breatheOut]. This test failed before it was restored.
     */
    @Test
    fun `every chrome string exists in all five languages`() {
        val tables = mapOf(
            "title" to SleepyUiText.title,
            "subtitle" to SleepyUiText.subtitle,
            "grownUp" to SleepyUiText.grownUp,
            "breatheIn" to SleepyUiText.breatheIn,
            "breatheOut" to SleepyUiText.breatheOut,
            "allDone" to SleepyUiText.allDone,
            "again" to SleepyUiText.again,
            "sound" to SleepyUiText.sound,
            "minutes" to SleepyUiText.minutes,
        )

        for ((name, table) in tables) {
            for (language in Language.entries) {
                val value = table[language]
                assertTrue("$name is missing $language", value != null && value.isNotBlank())
            }
        }
    }

    /**
     * The minutes label actually substitutes the number, in every language.
     *
     * Mutation proof: temporarily removed the `%d` from the Japanese row of
     * [SleepyUiText.minutes]. This test failed on the Japanese case before it
     * was restored.
     */
    @Test
    fun `the minutes label carries the number in every language`() {
        for (language in Language.entries) {
            for (choice in BreathingSession.CHOICES) {
                val label = SleepyUiText.minutesLabel(choice, language)
                assertTrue(
                    "$language's label for $choice minutes was \"$label\"",
                    label.contains(choice.toString()),
                )
                assertTrue("$language's label still carries a raw %d", !label.contains("%d"))
            }
        }
    }

    /** A missing row falls back to English rather than crashing in front of
     * a child. iOS `SleepyCloudView.text(_:)`'s own contract. */
    @Test
    fun `a missing row falls back to English rather than blank`() {
        val partial = mapOf(Language.English to "only English here")

        assertEquals("only English here", SleepyUiText.text(partial, Language.Japanese))
        assertEquals("", SleepyUiText.text(emptyMap(), Language.English))
    }

    /**
     * **The hold is silent on purpose.** A word arriving in the pause
     * between breathing in and breathing out is an instruction to do
     * something, and there is nothing to do; the same goes for a cloud that
     * has already fallen asleep.
     *
     * Mutation proof: temporarily made [phaseLabel]'s `Hold` branch return
     * the `breatheIn` string. This test failed before it was restored.
     */
    @Test
    fun `only the two moving phases carry a word`() {
        for (language in Language.entries) {
            assertTrue(phaseLabel(BreathPhase.Inhale, language).isNotBlank())
            assertTrue(phaseLabel(BreathPhase.Exhale, language).isNotBlank())
            assertEquals("the hold must be silent", "", phaseLabel(BreathPhase.Hold, language))
            assertEquals("a sleeping cloud must be silent", "", phaseLabel(BreathPhase.Asleep, language))
        }

        assertEquals("breathe in", phaseLabel(BreathPhase.Inhale, Language.English))
        assertEquals("breathe out", phaseLabel(BreathPhase.Exhale, Language.English))
    }
}
