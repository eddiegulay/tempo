package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.GymRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/**
 * Which routes reach a page, as a fact the JVM can check.
 *
 * Phase 1 shipped `GymHomeScreen`, `LibraryIndexScreen` and `LibraryDetailScreen` fully written, fully
 * compiling and with **zero call sites** — `GymPage` was still resolving their routes to the title-only
 * placeholder, so no user could reach any of them and nothing in the build could tell. That is the one
 * class of bug a Compose-free test suite can normally not see, which is why [gymPagePlaceholderTitle]
 * exists as a pure function rather than as a `when` inside the composable: reachability becomes a
 * value, and this pins it.
 *
 * The placeholder half is pinned too. A route with no page yet must still be an *honest* placeholder —
 * its own spec's title and nothing under it — so a later phase cannot quietly retitle a page it has
 * not written.
 */
class GymShellTest {

    @Test
    fun `the three written pages are wired, not placeholders`() {
        assertNull(gymPagePlaceholderTitle(GymRoute.Home))
        assertNull(gymPagePlaceholderTitle(GymRoute.Library))
        assertNull(gymPagePlaceholderTitle(GymRoute.RoutineDetail("r_cindy")))
    }

    @Test
    fun `every unwritten route still stands under its own spec's title`() {
        assertEquals("記録", gymPagePlaceholderTitle(GymRoute.Records))
        assertEquals("型を作る", gymPagePlaceholderTitle(GymRoute.Builder()))
        assertEquals("型を編集", gymPagePlaceholderTitle(GymRoute.Builder("r_cindy")))
        assertEquals("種目をえらぶ", gymPagePlaceholderTitle(GymRoute.StationPicker(null)))
        assertEquals("種目", gymPagePlaceholderTitle(GymRoute.ExerciseIndex))
        assertEquals("種目の中身", gymPagePlaceholderTitle(GymRoute.ExerciseDetail("e_pullup")))
        assertEquals("支度", gymPagePlaceholderTitle(GymRoute.Session("r_cindy")))
        assertEquals("記録の中身", gymPagePlaceholderTitle(GymRoute.Record("s_1")))
        assertEquals("これまで", gymPagePlaceholderTitle(GymRoute.History(anchorMonth = YearMonth.of(2026, 8))))
        assertEquals("最高", gymPagePlaceholderTitle(GymRoute.Bests))
        assertEquals("移り変わり", gymPagePlaceholderTitle(GymRoute.Charts))
        assertEquals("設定", gymPagePlaceholderTitle(GymRoute.Settings))
        assertEquals("安全のために", gymPagePlaceholderTitle(GymRoute.Safety))
    }
}
