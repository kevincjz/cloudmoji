package app.cloudmoji.android.ui.mascot

import app.cloudmoji.android.model.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests/CloudMascotTests.swift`: what the
 * mascot *looks* like is a judgement for the eye (no assertion here
 * substitutes for the `@Preview`), but which face, motion, and timing belong
 * to which mood is a lookup table that can rot silently — a copy-paste slip
 * that gives two moods the same face is invisible in review and obvious to a
 * child who stops getting feedback that anything changed.
 *
 * [MascotStyle] and [MascotMotion] hold no Compose/Android import (see
 * `CloudMascot.kt`'s "Pure style model" section), so this whole class runs on
 * the plain JVM — no Robolectric, no device.
 */
class CloudMascotStyleTest {

    // MARK: - Mood -> face

    @Test
    fun `no two moods wear the same face`() {
        val eyes = MascotMood.entries.map { MascotStyle.forMood(it).eyes }
        assertEquals(MascotMood.entries.size, eyes.toSet().size)

        val mouths = MascotMood.entries.map { MascotStyle.forMood(it).mouth }
        assertEquals(MascotMood.entries.size, mouths.toSet().size)
    }

    @Test
    fun `each mood maps to the face the web mascot draws`() {
        val expected = listOf(
            Triple(MascotMood.Happy, MascotEyeShape.Arc, MascotMouthShape.Smile),
            Triple(MascotMood.Excited, MascotEyeShape.Star, MascotMouthShape.Grin),
            Triple(MascotMood.Speaking, MascotEyeShape.Dot, MascotMouthShape.OpenRound),
            Triple(MascotMood.Beaming, MascotEyeShape.Squint, MascotMouthShape.WideGrin),
        )
        assertEquals(MascotMood.entries.size, expected.size)

        for ((mood, eyes, mouth) in expected) {
            val style = MascotStyle.forMood(mood)
            assertEquals("$mood eyes", eyes, style.eyes)
            assertEquals("$mood mouth", mouth, style.mouth)
        }
    }

    // MARK: - Mood -> motion

    @Test
    fun `each mood runs the keyframe the stylesheet gives it`() {
        val expected = listOf(
            MascotMood.Happy to MascotMotion.Float,
            MascotMood.Excited to MascotMotion.Float,
            MascotMood.Speaking to MascotMotion.Bounce,
            MascotMood.Beaming to MascotMotion.Beam,
        )
        for ((mood, motion) in expected) {
            assertEquals("$mood", motion, MascotStyle.forMood(mood).motion)
        }
    }

    // MARK: - Timing

    @Test
    fun `keyframe durations survive the CSS-to-Compose halving`() {
        val expected = listOf(
            Triple(MascotMotion.Float, 3000, 1500),
            Triple(MascotMotion.Bounce, 400, 200),
            Triple(MascotMotion.Beam, 600, 300),
        )
        assertEquals(MascotMotion.entries.size, expected.size)

        for ((motion, css, half) in expected) {
            assertEquals("$motion css duration", css, motion.cssDurationMs)
            assertEquals("$motion half cycle", half, motion.halfCycleMs)
        }
    }

    @Test
    fun `the speaking bounce is the quickest cycle and the idle drift the slowest`() {
        assertTrue(MascotMotion.Bounce.cssDurationMs < MascotMotion.Beam.cssDurationMs)
        assertTrue(MascotMotion.Beam.cssDurationMs < MascotMotion.Float.cssDurationMs)
    }

    @Test
    fun `the lift scales with the mascot size`() {
        assertEquals(4f, MascotMotion.Float.lift(64f), 0.0001f)
        assertEquals(8f, MascotMotion.Float.lift(128f), 0.0001f)
        assertEquals(2f, MascotMotion.Float.lift(32f), 0.0001f)
        assertEquals(6f, MascotMotion.Beam.lift(64f), 0.0001f)
        assertEquals(3f, MascotMotion.Bounce.lift(64f), 0.0001f)
    }

    @Test
    fun `only beaming sits larger than life at rest`() {
        assertEquals(1f, MascotMotion.Float.restScale, 0f)
        assertEquals(1f, MascotMotion.Bounce.restScale, 0f)
        assertTrue(MascotMotion.Beam.restScale > 1f)
    }

    @Test
    fun `every motion peaks no smaller than it rests`() {
        for (motion in MascotMotion.entries) {
            assertTrue("$motion shrinks at its peak", motion.peakScale >= motion.restScale)
        }
    }

    // MARK: - Celebration

    @Test
    fun `the celebration extras belong to beaming alone`() {
        for (mood in MascotMood.entries.filter { it != MascotMood.Beaming }) {
            assertFalse("$mood should not glow", MascotStyle.forMood(mood).showsGlow)
        }
        assertTrue(MascotStyle.forMood(MascotMood.Beaming).showsGlow)
    }

    @Test
    fun `sparkles appear only when excited or beaming, and beaming gets more`() {
        assertTrue(MascotStyle.forMood(MascotMood.Happy).sparkles.isEmpty())
        assertTrue(MascotStyle.forMood(MascotMood.Speaking).sparkles.isEmpty())
        assertEquals(2, MascotStyle.forMood(MascotMood.Excited).sparkles.size)
        assertEquals(5, MascotStyle.forMood(MascotMood.Beaming).sparkles.size)
    }

    @Test
    fun `sparkle ids are unique`() {
        val sparkles = MascotStyle.forMood(MascotMood.Beaming).sparkles
        assertEquals(sparkles.size, sparkles.map { it.id }.toSet().size)
    }

    @Test
    fun `sparkles are staggered`() {
        val sparkles = MascotStyle.forMood(MascotMood.Beaming).sparkles
        assertTrue(sparkles.map { it.cssDurationMs }.toSet().size > 1)
        assertTrue(sparkles.map { it.delayMs }.toSet().size > 1)
    }

    // MARK: - Blush

    @Test
    fun `the beaming blush is both fuller and rosier`() {
        val resting = MascotStyle.forMood(MascotMood.Happy)
        val beaming = MascotStyle.forMood(MascotMood.Beaming)
        assertTrue(beaming.blushRadiusX > resting.blushRadiusX)
        assertTrue(beaming.blushRadiusY > resting.blushRadiusY)
        assertTrue(beaming.blushOpacity > resting.blushOpacity)
        assertTrue(beaming.isBlushBeaming)
        assertFalse(resting.isBlushBeaming)
    }

    @Test
    fun `the blush is always drawn`() {
        for (mood in MascotMood.entries) {
            val style = MascotStyle.forMood(mood)
            assertTrue("$mood", style.blushOpacity > 0f)
            assertTrue("$mood", style.blushRadiusX > 0f && style.blushRadiusY > 0f)
        }
    }
}
