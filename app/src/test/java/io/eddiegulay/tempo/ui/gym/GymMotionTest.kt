package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.GymRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `01-shell.md` §A.6's motion table, row by row.
 *
 * The table is not decoration and it is not one spec with a direction flag. Two of its rows exist to
 * say what a move must **not** carry: a tab switch gets no scale, because a depth cue every transition
 * carries stops distinguishing push from pop; and the player gets a longer fade than anything else,
 * because 支度 is the threshold of a workout. Both are invisible to any assertion about pixels and
 * both were silently lost the first time this was written as a single `transitionSpec`, which is why
 * the choice is a pure function with a test rather than a lambda read by eye.
 */
class GymMotionTest {

    @Test
    fun `a tab switch cross-fades over 200ms and carries no scale`() {
        // The row: "tab switch | 200ms cross-fade, no scale". Depth 1 to depth 1 — 鍛錬 to 型 — which
        // is exactly the shape that satisfies `forward = target >= initial` and would otherwise arrive
        // wearing the push's 0.98 settle.
        val m = routeMotion(initialDepth = 1, targetDepth = 1, targetRoute = GymRoute.Library)
        assertNull(m.scale)
        assertEquals(200, m.fadeInMillis)
        assertEquals(0, m.fadeInDelayMillis)
    }

    @Test
    fun `a cross-fade fades both halves together, unlike a push`() {
        // "Cross-fade" is a claim about both halves: same duration, same easing, so the two pages
        // exchange without the shell dipping to its own background between them. A push is the other
        // shape on purpose — leaving is faster than arriving.
        val lateral = routeMotion(1, 1, GymRoute.Records)
        assertEquals(lateral.fadeInMillis, lateral.fadeOutMillis)
        assertEquals(GymEase.SettleIn, lateral.fadeOutEase)

        val push = routeMotion(1, 2, GymRoute.RoutineDetail("r"))
        assertEquals(90, push.fadeOutMillis)
        assertEquals(GymEase.Snap, push.fadeOutEase)
    }

    @Test
    fun `a back-rebase between tab roots is lateral too`() {
        // Back from 記録 rebases to 鍛錬 — still depth 1 to depth 1, still a lateral move between roots,
        // and it must read as the same motion as tapping the tab. The user cannot tell the two apart
        // and the shell must not either.
        assertEquals(routeMotion(1, 1, GymRoute.Home), routeMotion(1, 1, GymRoute.Library))
    }

    @Test
    fun `entering the player takes 320ms and does not scale`() {
        // The row: "entering the player | 320ms fade, bar leaves over 160ms". The 160ms half belongs to
        // the tab bar's own AnimatedVisibility; this half is the page, and the table asks for a fade
        // where the push row asks for a fade *and* a scale.
        val m = routeMotion(initialDepth = 1, targetDepth = 2, targetRoute = GymRoute.Session("r"))
        assertEquals(320, m.fadeInMillis)
        assertNull(m.scale)
    }

    @Test
    fun `the player keeps its own fade however deep it is entered from`() {
        // 続ける from GYM.HOME and a start from 型の中身 are different depths into the same threshold.
        // The route decides this row, not the arithmetic.
        assertEquals(
            routeMotion(1, 2, GymRoute.Session("r")),
            routeMotion(2, 3, GymRoute.Session("r", resume = true)),
        )
    }

    @Test
    fun `a push settles in and a pop settles out`() {
        // "the only direction signal; no horizontal slide anywhere". 型 → 型の中身 against its return.
        val push = routeMotion(1, 2, GymRoute.RoutineDetail("r"))
        val pop = routeMotion(2, 1, GymRoute.Library)
        assertEquals(ScaleCue(0.98f, 280, 30), push.scale)
        assertEquals(ScaleCue(1.02f, 280, 30), pop.scale)
        assertEquals(240, push.fadeInMillis)
        assertEquals(240, pop.fadeInMillis)
    }

    @Test
    fun `swapping a rung for its sibling is a push, not a lateral move`() {
        // replaceTop — a ladder rung swapping the page it was tapped from — is equal-depth but deep in
        // a hierarchy, and it is a move *within* that hierarchy. Lateral means the tab bar, which only
        // ever swaps roots; that is why the test is both depths being 1 rather than merely equal.
        val m = routeMotion(initialDepth = 3, targetDepth = 3, targetRoute = GymRoute.ExerciseDetail("e"))
        assertEquals(ScaleCue(0.98f, 280, 30), m.scale)
        assertEquals(240, m.fadeInMillis)
    }
}
