package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure state function, read at every instant that has ever broken a timer.
 *
 * Zero, a segment edge to the millisecond, past the end, and negative — the last of which is not
 * hypothetical: ◁ seeks the clock backwards, and a seek that lands one millisecond before the origin
 * must render 支度 rather than crash a live session.
 *
 * The boundary convention is the important one and it is asserted, not assumed: an instant that is
 * exactly a segment's `startMs` belongs to **that** segment, so the first frame of a station is the
 * station and not the last millisecond of the rest before it.
 */
class StateAtTest {

    private val circuit = compiledCircuit(rounds = 2)
    // [0 支度 5s][5 運動 30s][35 休息 10s][45 運動 30s][75 巡の間 60s][135 運動 30s][165 休息 10s][175 運動 30s]

    @Test
    fun `zero is the first segment, one frame in`() {
        val s = circuit.stateAt(0)

        assertEquals(0, s.ordinal)
        assertEquals(Phase.PREPARE, s.segment.phase)
        assertEquals(0L, s.elapsedInSegmentMs)
        assertEquals(5_000L, s.remainingMs)
        assertEquals(1f, s.ringFraction, 0.0001f)
        assertEquals(0f, s.sessionFraction, 0.0001f)
        assertFalse(s.finished)
    }

    @Test
    fun `a segment edge belongs to the segment that is starting`() {
        assertEquals(Phase.PREPARE, circuit.stateAt(4_999).segment.phase)
        assertEquals(1, circuit.stateAt(5_000).ordinal)
        assertEquals(0L, circuit.stateAt(5_000).elapsedInSegmentMs)
        assertEquals(2, circuit.stateAt(35_000).ordinal)
        assertEquals(1, circuit.stateAt(34_999).ordinal)
    }

    @Test
    fun `the ring depletes across the segment and is clamped at both ends`() {
        assertEquals(1f, circuit.stateAt(5_000).ringFraction, 0.0001f)
        assertEquals(0.5f, circuit.stateAt(20_000).ringFraction, 0.0001f)
        assertEquals(0f, circuit.stateAt(34_999).ringFraction, 0.001f)
    }

    @Test
    fun `past the end pins to the last segment and reports finished once nothing is open`() {
        val s = circuit.stateAt(10_000_000)

        assertEquals(circuit.segments.lastIndex, s.ordinal)
        assertTrue(s.finished)
        assertEquals(0f, s.ringFraction, 0.0001f)
        // The hairline measures segments *behind* the cursor, so the last one reads as its own start
        // rather than as one — 完了 is what says the session ended, not a full bar.
        assertEquals(175_000f / 205_000f, s.sessionFraction, 0.001f)
    }

    @Test
    fun `a negative elapsed lands on the first segment instead of walking off the front`() {
        // ◁ seeks the clock backwards; a rounding error at the origin must not index at -1.
        val s = circuit.stateAt(-1)

        assertEquals(0, s.ordinal)
        assertEquals(0L, s.elapsedInSegmentMs)
        assertEquals(5_000L, s.remainingMs)
    }

    @Test
    fun `state never walks past an open segment, however long the user takes`() {
        // The frontier rule. An open segment absorbs all time beyond its start, so the binary search
        // finding station four while the user is still holding station two would be a lie the whole
        // record then inherits.
        val tl = compile(
            snapshot(
                Engine.FOR_TIME,
                listOf(station(0, "a", reps = 10), station(1, "b", reps = 10)),
                prepareSeconds = 0,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        val s = tl.stateAt(600_000)
        assertEquals(0, s.ordinal)
        assertEquals(600_000L, s.elapsedInSegmentMs)
        assertFalse(s.finished)
    }

    @Test
    fun `overrun is reported as a positive number and the ring holds empty`() {
        // §A REPS state 2: the ring does not refill and does not pulse. Nothing about overrun is a
        // failure, so nothing about it is rendered as one.
        val tl = compile(
            snapshot(Engine.FOR_TIME, listOf(station(0, "a", reps = 10)), prepareSeconds = 0),
            ScalingTier.RX,
            circuitLib,
        )

        val s = tl.stateAt(27_000) // estimate is 20s
        assertEquals(-7_000L, s.remainingMs)
        assertEquals(7_000L, s.overrunMs)
        assertEquals(0f, s.ringFraction, 0.0001f)
    }

    @Test
    fun `a for-time ring tracks stations rather than a duration it does not have`() {
        val tl = compile(
            snapshot(
                Engine.FOR_TIME,
                listOf(station(0, "a", reps = 10), station(1, "b", reps = 10)),
                prepareSeconds = 0,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        assertEquals(1f, tl.stateAt(0).ringFraction, 0.0001f)
        assertEquals(0.5f, tl.close(0, 20_000).stateAt(20_000).ringFraction, 0.0001f)
    }

    @Test
    fun `the hairline denominator follows the current timeline, not the compiled one`() {
        // §A REPS edge case 2: an overrun that grew the session must grow its own denominator, or the
        // hairline reads over one hundred percent.
        val tl = compile(
            snapshot(
                Engine.FOR_TIME,
                listOf(station(0, "a", reps = 10), station(1, "b", reps = 10)),
                prepareSeconds = 0,
            ),
            ScalingTier.RX,
            circuitLib,
        )
        val overran = tl.close(0, 220_000)

        assertTrue(overran.stateAt(220_000).sessionFraction <= 1f)
        assertEquals(220_000f / 240_000f, overran.stateAt(220_000).sessionFraction, 0.001f)
    }

    @Test
    fun `an amrap reports its cap the instant the clock reaches it`() {
        val tl = compile(
            snapshot(
                Engine.AMRAP,
                listOf(station(0, "a", reps = 10)),
                prepareSeconds = 5,
                timeCapSeconds = 60,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        // The cap is measured from the end of 支度: getting ready is not part of the score.
        assertFalse(tl.stateAt(64_999).capExceeded)
        assertTrue(tl.stateAt(65_000).capExceeded)
    }

    @Test
    fun `an emom reports its window closing over work that is still open`() {
        val tl = emom(rounds = 2)

        assertFalse(tl.stateAt(59_999).capExceeded)
        assertTrue(tl.stateAt(60_000).capExceeded)
        // Closed in time, and the boundary is no longer a failure.
        assertFalse(tl.close(0, 30_000).stateAt(60_000).capExceeded)
    }

    @Test
    fun `a circuit never reports a cap it does not have`() {
        assertFalse(circuit.stateAt(10_000_000).capExceeded)
    }
}
