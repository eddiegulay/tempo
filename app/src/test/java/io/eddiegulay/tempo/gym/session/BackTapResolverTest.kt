package io.eddiegulay.tempo.gym.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two-second window, at the millisecond either side of it.
 *
 * The stakes are asymmetric, which is why the boundary is pinned rather than approximated: a first
 * tap misread as a second **deletes a segment's result**, while a second misread as a first only
 * restarts the interval the user is already in.
 */
class BackTapResolverTest {

    @Test
    fun `the first tap restarts the segment`() {
        assertEquals(BackAction.RESTART_SEGMENT, BackTapResolver().resolve(1_000))
    }

    @Test
    fun `a second tap inside the window steps back`() {
        val r = BackTapResolver()
        r.resolve(1_000)

        assertEquals(BackAction.PREVIOUS_SEGMENT, r.resolve(2_500))
    }

    @Test
    fun `exactly two seconds still counts as the second tap`() {
        // `<=`, per §C.4. The user who taps on the beat is the user this affordance is for.
        val r = BackTapResolver()
        r.resolve(1_000)

        assertEquals(BackAction.PREVIOUS_SEGMENT, r.resolve(3_000))
    }

    @Test
    fun `one millisecond past the window is a fresh first tap`() {
        val r = BackTapResolver()
        r.resolve(1_000)

        assertEquals(BackAction.RESTART_SEGMENT, r.resolve(3_001))
    }

    @Test
    fun `a third rapid tap restarts the cycle instead of walking backwards`() {
        // The consume. Without it, a shaking hand walks back a segment per tap and deletes a result
        // each time — and the user has no idea why the session is unravelling.
        val r = BackTapResolver()

        assertEquals(BackAction.RESTART_SEGMENT, r.resolve(1_000))
        assertEquals(BackAction.PREVIOUS_SEGMENT, r.resolve(1_500))
        assertEquals(BackAction.RESTART_SEGMENT, r.resolve(2_000))
        assertEquals(BackAction.PREVIOUS_SEGMENT, r.resolve(2_500))
    }

    @Test
    fun `disarming forgets the armed tap`() {
        // The segment changed under the user: a ◁ armed on the plank must not step out of the burpee.
        val r = BackTapResolver()
        r.resolve(1_000)
        r.disarm()

        assertEquals(BackAction.RESTART_SEGMENT, r.resolve(1_500))
    }
}
