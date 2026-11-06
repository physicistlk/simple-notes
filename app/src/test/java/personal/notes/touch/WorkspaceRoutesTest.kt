package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.nav.Screen

/**
 * Route-shape guard for the two-pane workspace: the NavHost declares
 * `navArgument("leftId")` / `navArgument("rightId")`, so the pattern and the
 * builder must agree or the split silently opens with blank ids.
 */
class WorkspaceRoutesTest {

    @Test
    fun splitRouteCarriesBothIds() {
        assertEquals("split/abc/def", Screen.Split.createRoute("abc", "def"))
    }

    @Test
    fun splitPatternDeclaresBothArguments() {
        assertEquals("split/{leftId}/{rightId}", Screen.Split.route)
        assertTrue(Screen.Split.route.contains("{leftId}"))
        assertTrue(Screen.Split.route.contains("{rightId}"))
    }

    @Test
    fun noteIdsSurviveAsFullUuids() {
        val left = "550e8400-e29b-41d4-a716-446655440000"
        val right = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        assertEquals("split/$left/$right", Screen.Split.createRoute(left, right))
    }
}
