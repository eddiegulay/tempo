package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.SegmentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to the rest of the session when one segment ends somewhere other than where it was
 * planned to.
 *
 * This is the file the EMOM lives or dies in. An elastic timeline *must* shift — a set that took
 * twelve seconds longer moves everything twelve seconds later, because the clock is real — and an
 * anchored one *must not*, because the grid is the protocol. The same `close` call has to do both,
 * and a single test that only ever checks the elastic case would pass against a compiler that had
 * quietly turned every EMOM into a circuit.
 */
class TimelineMutationTest {

    private fun amrap(): Timeline = compile(
        snapshot(
            Engine.AMRAP,
            listOf(station(0, "a", reps = 10), station(1, "b", reps = 5)),
            prepareSeconds = 0,
            timeCapSeconds = 60,
        ),
        ScalingTier.RX,
        circuitLib,
    )

    // ─── Elastic ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `closing an open segment late moves everything after it by the same delta`() {
        val tl = amrap() // a: 0..20s, b: 20..30s, then two more rounds
        val closed = tl.close(0, 35_000, actualReps = 10)

        assertEquals(35_000L, closed.segments[0].endMs)
        assertEquals(35_000L, closed.segments[1].startMs)
        // Every later cell moved by the same fifteen seconds; none of them changed length.
        tl.segments.drop(1).zip(closed.segments.drop(1)) { before, after ->
            assertEquals(before.startMs + 15_000L, after.startMs)
            assertEquals(before.plannedMs, after.plannedMs)
        }
    }

    @Test
    fun `closing early pulls the session forward, which is what a skip does`() {
        val tl = compiledCircuit(rounds = 2)
        val skipped = tl.close(1, 4_000, skipped = true)

        assertEquals(9_000L, skipped.segments[2].startMs) // 5s prepare + 4s of a 30s station
        assertTrue(skipped.segments[1].skipped)
        assertEquals(tl.plannedTotalMs - 26_000L, skipped.plannedTotalMs)
    }

    @Test
    fun `reopen is the exact inverse of close`() {
        // §B.4 asks for this as a property, because ◁◁ un-shifts by the same delta it shifted by, and
        // an inverse that is only approximately an inverse leaks a few milliseconds per correction.
        val tl = amrap()

        assertEquals(tl.segments, tl.close(2, 41_000, actualReps = 7, skipped = true).reopen(2).segments)
        assertEquals(tl.segments, tl.close(0, 1).reopen(0).segments)
        assertEquals(tl.hash(), tl.close(0, 99_000).reopen(0).hash())
    }

    @Test
    fun `reopen clears the recorded reps and the skip flag, not just the duration`() {
        // The stored row is deleted by the caller; a timeline that kept "とばした" on a reopened
        // segment would render a station the user is standing in as one they abandoned.
        val reopened = amrap().close(0, 9_000, actualReps = 12, skipped = true).reopen(0)

        assertFalse(reopened.segments[0].closed)
        assertNull(reopened.segments[0].actualMs)
        assertNull(reopened.segments[0].actualReps)
        assertFalse(reopened.segments[0].skipped)
    }

    @Test
    fun `plus twenty seconds grows the rest and pushes the rest of the session out`() {
        val tl = compiledCircuit(rounds = 2)
        val extended = tl.extend(2, REST_EXTENSION_MS) // the station rest

        assertEquals(20_000L, extended.segments[2].addedMs)
        assertEquals(30_000L, extended.segments[2].effectiveMs) // 10s planned + 20s added
        assertEquals(tl.segments[3].startMs + 20_000L, extended.segments[3].startMs)
        assertEquals(tl.plannedTotalMs + 20_000L, extended.plannedTotalMs)
    }

    @Test
    fun `added time is absorbed by the measured duration rather than counted twice`() {
        // §B.1's literal `(actualMs ?: plannedMs) + addedMs` makes a 15s rest that was extended and
        // then taken in full 55 seconds long, and pushes every later offset out by the same 20s.
        val taken = compiledCircuit(rounds = 2)
            .extend(2, REST_EXTENSION_MS)
            .close(2, 30_000)

        assertEquals(30_000L, taken.segments[2].effectiveMs)
        assertEquals(taken.segments[2].startMs + 30_000L, taken.segments[3].startMs)
    }

    @Test
    fun `extending a segment that has already closed is dropped, never rewound`() {
        // §A REST edge case 1: a ＋二十秒 that lands after the frontier moved applies to nothing.
        val tl = compiledCircuit(rounds = 2).close(2, 10_000)

        assertSame(tl, tl.extend(2, REST_EXTENSION_MS))
    }

    // ─── Anchored ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `closing an emom cell early does not move the grid - it grows the remainder`() {
        val tl = emom(rounds = 3)
        val done = tl.close(0, 20_000, actualReps = 10)

        // The remainder soaked up the forty seconds that were left of minute one...
        assertEquals(20_000L, done.segments[1].startMs)
        assertEquals(40_000L, done.segments[1].plannedMs)
        // ...and minute two still starts at 1:00, exactly as it did before anyone finished early.
        assertEquals(60_000L, done.segments[2].startMs)
        assertEquals(120_000L, done.segments[4].startMs)
        assertEquals(tl.segments.map { it.windowStartMs }, done.segments.map { it.windowStartMs })
    }

    @Test
    fun `an emom cell finished with seconds to spare leaves a proportionally smaller remainder`() {
        val done = emom(rounds = 3).close(0, 55_000)

        assertEquals(5_000L, done.segments[1].plannedMs)
        assertEquals(60_000L, done.segments[2].startMs)
    }

    @Test
    fun `two stations sharing a minute hand the window on without leaving it`() {
        val tl = emom(rounds = 2, stations = 2)
        val done = tl.close(0, 25_000)

        // Station two picks up at 0:25 with the thirty-five seconds that are left...
        assertEquals(25_000L, done.segments[1].startMs)
        assertEquals(35_000L, done.segments[1].plannedMs)
        // ...and minute two is still minute two.
        assertEquals(60_000L, done.segments.first { it.round == 2 }.startMs)
    }

    @Test
    fun `an anchored segment refuses to be extended`() {
        val tl = emom(rounds = 2)

        assertSame(tl, tl.extend(1, REST_EXTENSION_MS))
        assertSame(tl, tl.extend(0, REST_EXTENSION_MS))
    }

    @Test
    fun `the same early finish shifts an elastic timeline and not an anchored one`() {
        val elastic = amrap().close(0, 5_000)
        val grid = emom(rounds = 2).close(0, 5_000)

        assertEquals(5_000L, elastic.segments[1].startMs)
        assertEquals(60_000L, grid.segments[2].startMs)
    }

    // ─── Growth and replay ──────────────────────────────────────────────────────────────────────

    @Test
    fun `an amrap appends a round at the tail without disturbing what is already laid`() {
        val routine = snapshot(
            Engine.AMRAP,
            listOf(station(0, "a", reps = 10), station(1, "b", reps = 5)),
            prepareSeconds = 0,
            timeCapSeconds = 60,
        )
        val tl = compile(routine, ScalingTier.RX, circuitLib)
        val grown = tl.extendOneRound(routine, circuitLib)

        assertEquals(5, grown.totalRounds)
        assertEquals(tl.segments, grown.segments.take(tl.segments.size))
        assertEquals(tl.plannedTotalMs, grown.segments[tl.segments.size].startMs)
        assertEquals(listOf(5, 5), grown.segments.drop(tl.segments.size).map { it.round })
    }

    @Test
    fun `a timeline that is not extensible refuses to grow`() {
        val tl = compiledCircuit()
        assertSame(tl, tl.extendOneRound(circuit(), circuitLib))
    }

    @Test
    fun `needsAnotherRound fires on the last materialised round and not before`() {
        val tl = amrap()
        assertFalse(tl.needsAnotherRound(0)) // round 1 of 4
        assertTrue(tl.needsAnotherRound(tl.segments.lastIndex)) // round 4 of 4
    }

    @Test
    fun `replaying stored results moves the frontier back to where the user left it`() {
        // §E.2 step 2. The replayed timeline is the live one: same shifts, same frontier, same phase.
        val tl = amrap()
        val replayed = tl.replay(
            listOf(
                result(0, actualMs = 31_000, actualReps = 10),
                result(1, actualMs = 12_000, actualReps = 5),
            ),
        )

        assertEquals(2, replayed.frontier)
        assertEquals(43_000L, replayed.segments[2].startMs)
        assertEquals(10, replayed.segments[0].actualReps)
    }

    @Test
    fun `a replayed result for an ordinal the timeline does not have is dropped, not thrown on`() {
        // The real guard against drift is §E.2's compiled_hash check. A crash here would turn a
        // recoverable session into an app that cannot be opened.
        val tl = amrap()
        val replayed = tl.replay(listOf(result(99, actualMs = 1_000)))

        assertEquals(tl.segments, replayed.segments)
    }

    @Test
    fun `replay restores added time on a segment that was never open`() {
        val tl = compiledCircuit(rounds = 2)
        val replayed = tl.replay(listOf(result(2, actualMs = 30_000, addedMs = 20_000, phase = Phase.REST)))

        assertEquals(20_000L, replayed.segments[2].addedMs)
        assertEquals(30_000L, replayed.segments[2].effectiveMs)
    }
}

internal fun result(
    ordinal: Int,
    actualMs: Long,
    actualReps: Int? = null,
    skipped: Boolean = false,
    addedMs: Long = 0L,
    phase: Phase = Phase.REPS,
): SegmentResult = SegmentResult(
    ordinal = ordinal,
    phase = phase,
    roundIndex = 1,
    stationOrder = 0,
    exerciseId = "a",
    measure = null,
    prescribedReps = null,
    prescribedSeconds = null,
    actualReps = actualReps,
    actualMs = actualMs,
    addedMs = addedMs,
    skipped = skipped,
    difficultyCoefficient = 1.0,
    volumeUnits = 0.0,
    closedAtWallMs = 0L,
    closedAtElapsedMs = 0L,
)
