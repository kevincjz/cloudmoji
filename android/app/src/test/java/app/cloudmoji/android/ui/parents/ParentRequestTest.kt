package app.cloudmoji.android.ui.parents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate now has three doorways rather than one, and the sentence a parent
 * reads while answering the arithmetic has to match the one they are about to
 * walk through. Ports iOS `RootContent.ParentRequest`'s `explanation`.
 */
class ParentRequestTest {

    /** A restored value from a `Bundle` written by an older build falls back
     * to the least surprising door rather than throwing on a parent mid-gate.
     *
     * Mutation: `entries.first { it.name == name }` without the fallback. The
     * unknown case throws instead of failing softly. */
    @Test
    fun anUnknownSavedNameFallsBackToSettings() {
        assertEquals(ParentRequest.Settings, ParentRequest.fromName("no-such-request"))
        assertEquals(ParentRequest.Settings, ParentRequest.fromName(""))
    }

    @Test
    fun everyRequestRoundTripsThroughItsSavedName() {
        for (request in ParentRequest.entries) {
            assertEquals(request, ParentRequest.fromName(request.name))
        }
    }

    /** Three doors, three different sentences: a gate that explained the
     * settings panel while a parent was actually allowing camera access would
     * be asking for consent to the wrong thing. */
    @Test
    fun eachDoorExplainsItself() {
        val explanations = ParentRequest.entries.map { it.explanation }

        assertEquals(explanations.size, explanations.toSet().size)
        assertTrue(explanations.none { it.isBlank() })
    }

    @Test
    fun theCameraDoorsNameTheCamera() {
        assertTrue(ParentRequest.CameraPermission.explanation.contains("camera", ignoreCase = true))
        assertTrue(ParentRequest.CameraSettings.explanation.contains("camera", ignoreCase = true))
        assertTrue(ParentRequest.Settings.explanation.contains("Settings"))
    }
}
