package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}

