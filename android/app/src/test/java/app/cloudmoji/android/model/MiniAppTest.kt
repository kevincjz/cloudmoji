package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppTest {
    @Test
    fun `routes are unique and reversible`() {
        assertEquals(7, MiniApp.entries.map(MiniApp::route).toSet().size)
        MiniApp.entries.forEach { app ->
            assertEquals(app, MiniApp.fromRoute(app.route))
        }
    }

    @Test
    fun `every app has a label in every language`() {
        MiniApp.entries.forEach { app ->
            Language.entries.forEach { language ->
                assertNotNull(app.label(language).takeIf(String::isNotBlank))
            }
        }
    }

    /**
     * `showsSoundRecovery` — mirrors iOS `MiniApp.showsSoundRecovery`
     * (`Views/Launcher/MiniApp.swift:191-196`) exactly: `true` for every
     * mini-app with no header mute control of its own ([FlashCards],
     * [Music], [Animals], [Sleepy]); `false` for [Words]/[Count] (both
     * already have a header mute button) and [Photos] (not audio-driven).
     * The regression this guards: a mismapped `true` would show the
     * recovery button on a screen that never needs it (e.g. `words-screen`,
     * which is quietly fine); a mismapped `false` recreates the exact
     * dead-end the flag exists to close — a mini-app muted elsewhere, with
     * no way back to sound short of the gated Grown-ups panel.
     *
     * Mutation proof: see the Task 10 fix report for the fail-then-pass run
     * against this test (temporarily flipped `Music`'s branch).
     */
    @Test
    fun `showsSoundRecovery matches iOS MiniApp exactly, case for case`() {
        val expectedTrue = setOf(MiniApp.FlashCards, MiniApp.Music, MiniApp.Animals, MiniApp.Sleepy)
        val expectedFalse = setOf(MiniApp.Words, MiniApp.Count, MiniApp.Photos)

        assertEquals(
            "expectedTrue/expectedFalse must partition every MiniApp — update this test if a case is added",
            MiniApp.entries.toSet(),
            expectedTrue + expectedFalse,
        )
        expectedTrue.forEach { app -> assertTrue("$app should show sound recovery", app.showsSoundRecovery) }
        expectedFalse.forEach { app -> assertFalse("$app should not show sound recovery", app.showsSoundRecovery) }
    }
}

