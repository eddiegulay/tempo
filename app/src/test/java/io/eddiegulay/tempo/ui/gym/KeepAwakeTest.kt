package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.ActiveSession
import io.eddiegulay.tempo.gym.GymRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When 鍛錬 may hold the screen awake — and, far more importantly, when it may not.
 *
 * `FLAG_KEEP_SCREEN_ON` held one screen too long is not a cosmetic defect: it is a phone that never
 * sleeps in a bag, which is the leak class commit `1f49dfc` was written to close. `01-shell.md` §A.7
 * names three surfaces that must be excluded, and the only one of them that a `session != null` check
 * cannot exclude on its own is 型の中身 — because a session stays live across a HOME press by design.
 * That case is the reason this predicate takes the top route, and it is asserted below.
 */
class KeepAwakeTest {

    private val session = ActiveSession(sessionId = 1L, routineId = "r", routineName = "七分間")
    private val player = GymRoute.Session(routineId = "r")

    @Test
    fun `a live session on the player with the preference on keeps the screen awake`() {
        // The one true case. Everything else in this file is a way of not being it.
        assertTrue(keepAwake(session, keepScreenOnPref = true, topRoute = player))
    }

    @Test
    fun `a live session browsing a routine does not keep the screen awake`() {
        // §A.7's GYM.LIBRARY.DETAIL exclusion, and the one that does not fall out of the session being
        // null. The route there: press HOME mid-workout — onLeaveShell resets the stack and keeps the
        // session on purpose, so a fat thumb cannot end a workout — re-enter 鍛錬, tap a routine card.
        // The session is still live and 型の中身 is on top, and the user may read it for an hour.
        assertFalse(keepAwake(session, keepScreenOnPref = true, topRoute = GymRoute.RoutineDetail("r")))
    }

    @Test
    fun `the resume prompt does not keep the screen awake`() {
        // §A.7's third exclusion. GYM.HOME with a live session is the つづき banner: the workout is
        // running but nobody is looking at it, and the screen the user is looking at is a list.
        assertFalse(keepAwake(session, keepScreenOnPref = true, topRoute = GymRoute.Home))
    }

    @Test
    fun `every other route the session can be left on is excluded too`() {
        // The gate is an allow-list of one, so routes nobody has built yet are already excluded. This
        // is the property that makes the fix structural rather than a list to remember to extend.
        val elsewhere = listOf(
            GymRoute.Library,
            GymRoute.Records,
            GymRoute.Settings,
            GymRoute.Safety,
            GymRoute.Builder(null),
            GymRoute.StationPicker(0),
            GymRoute.ExerciseIndex,
            GymRoute.ExerciseDetail("e"),
            GymRoute.Record("k"),
            GymRoute.Bests,
            GymRoute.Charts,
        )
        elsewhere.forEach { route ->
            assertFalse("$route must not hold the screen", keepAwake(session, true, route))
        }
    }

    @Test
    fun `the player without a live session claims nothing`() {
        // 支度 renders before startSession has returned an id, and after a quit it can still be on top
        // for the frame before the pop. No workout, no claim.
        assertFalse(keepAwake(null, keepScreenOnPref = true, topRoute = player))
    }

    @Test
    fun `the preference is a veto and not a suggestion`() {
        // 画面を消さない off means off, mid-workout included.
        assertFalse(keepAwake(session, keepScreenOnPref = false, topRoute = player))
    }
}
