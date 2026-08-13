package io.eddiegulay.tempo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that turns time left into ring left. The ensō is the session player's clock face, so a
 * sweep outside 0..312 is not a cosmetic slip — it is a ring that lies about how much of the interval
 * remains, or draws over its own gap.
 */
class EnsoTest {

    @Test
    fun `a full phase draws the whole ring, gap included`() {
        assertEquals(ENSO_SWEEP_DEGREES, ensoSweep(1f), 0.001f)
    }

    @Test
    fun `a spent phase draws nothing`() {
        assertEquals(0f, ensoSweep(0f), 0.001f)
    }

    @Test
    fun `the sweep is linear in the fraction remaining`() {
        assertEquals(156f, ensoSweep(0.5f), 0.001f)
        assertEquals(78f, ensoSweep(0.25f), 0.001f)
    }

    @Test
    fun `overrun clamps to empty rather than sweeping backwards`() {
        // Past the estimate the ring holds at empty; it must not run negative and unwind anticlockwise.
        assertEquals(0f, ensoSweep(-0.4f), 0.001f)
    }

    @Test
    fun `a clock read early clamps to full rather than overdrawing the gap`() {
        assertEquals(ENSO_SWEEP_DEGREES, ensoSweep(1.3f), 0.001f)
    }

    @Test
    fun `a zero-length phase reads as spent, not as a blank canvas`() {
        // 0ms remaining over a 0ms segment is NaN, which drawArc renders as nothing at all.
        // Written as Float.NaN rather than 0f / 0f so the compiler does not warn on a literal
        // division by zero — the value under test is the NaN, not the way it was produced.
        assertEquals(0f, ensoSweep(Float.NaN), 0.001f)
    }
}
