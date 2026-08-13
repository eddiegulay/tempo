package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coming back to a session the clock kept running without us.
 *
 * `elapsedRealtime` includes deep sleep, so a phone left face-down through a doze returns with the
 * frontier several segments ahead and none of them written. The failure this prevents is not a
 * cosmetic one: the *record* would show a session that jumped from station two to station six.
 *
 * The interesting half of the test is what reconcile refuses to do. A self-paced segment has no end
 * until the user says so, and every fabricated one would be indistinguishable from a real one
 * forever after.
 */
class ReconcileTest {

    private val circuit = compiledCircuit(rounds = 2)
    // [0 支度 5s][5 運動 30s][35 休息 10s][45 運動 30s][75 巡の間 60s][135 運動 30s][165 休息 10s][175 運動 30s]

    @Test
    fun `segments the clock walked past are back-filled at their planned length`() {
        val r = circuit.reconcile(fromOrdinal = 0, elapsedMs = 80_000, nowWallMs = 1_000_000)

        // 支度 is walked past but never written: it is not a segment result.
        assertEquals(listOf(1, 2, 3), r.backFilled.map { it.ordinal })
        assertTrue(r.backFilled.all { !it.skipped })
        assertEquals(listOf(30_000L, 10_000L, 30_000L), r.backFilled.map { it.actualMs })
        assertEquals(4, r.landedOrdinal)
        assertFalse(r.autoCompleted)
    }

    @Test
    fun `every back-filled close carries the instant it would have happened, not the instant we noticed`() {
        val r = circuit.reconcile(fromOrdinal = 0, elapsedMs = 80_000, nowWallMs = 1_000_000)

        assertEquals(listOf(35_000L, 45_000L, 75_000L), r.backFilled.map { it.closedAtElapsedMs })
        // Interpolated backwards from now, so four stations do not stack onto one timestamp.
        assertEquals(listOf(955_000L, 965_000L, 995_000L), r.backFilled.map { it.closedAtWallMs })
    }

    @Test
    fun `a back-filled segment records no rep count`() {
        // The segment ran unattended. What the user did in it is unknown, and a prescription is a
        // plan rather than a record — hard rule 3's whole point.
        val r = circuit.reconcile(0, 80_000, 0)

        assertTrue(r.backFilled.all { it.actualReps == null })
    }

    @Test
    fun `walking past the end reports an auto-completed session`() {
        val r = circuit.reconcile(0, 10_000_000, 0)

        assertEquals(7, r.backFilled.size) // everything but 支度
        assertTrue(r.autoCompleted)
        assertEquals(circuit.segments.lastIndex, r.landedOrdinal)
    }

    @Test
    fun `a segment still running is left alone`() {
        val r = circuit.reconcile(0, 40_000, 0)

        assertEquals(listOf(1), r.backFilled.map { it.ordinal })
        assertEquals(2, r.landedOrdinal)
    }

    @Test
    fun `nothing to back-fill is an empty reconciliation rather than a phantom write`() {
        val r = circuit.reconcile(0, 1_000, 0)

        assertTrue(r.backFilled.isEmpty())
        assertFalse(r.autoCompleted)
        assertEquals(circuit.segments, r.timeline.segments)
    }

    @Test
    fun `an open segment stops the walk however long the app was away`() {
        val forTime = compile(
            snapshot(
                Engine.FOR_TIME,
                listOf(station(0, "a", reps = 10), station(1, "b", reps = 10)),
                prepareSeconds = 0,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        val r = forTime.reconcile(0, 3_600_000, 0)

        assertTrue(r.backFilled.isEmpty())
        assertFalse(r.autoCompleted)
        assertEquals(0, r.landedOrdinal)
    }

    @Test
    fun `an emom window that closed over open work is left for the fail policy, not back-filled`() {
        // Closing it here would record it as an ordinary completed cell. It is a miss, and only the
        // state machine knows whether a miss ends the session (§C.3).
        val r = emom(rounds = 3).reconcile(0, 200_000, 0)

        assertTrue(r.backFilled.isEmpty())
        assertEquals(0, r.landedOrdinal)
    }

    @Test
    fun `back-filling an extended rest keeps the added time and the real duration apart`() {
        val extended = circuit.extend(2, REST_EXTENSION_MS)
        val r = extended.reconcile(0, 200_000, 0)

        val rest = r.backFilled.single { it.ordinal == 2 }
        assertEquals(Phase.REST, rest.phase)
        assertEquals(10_000L, rest.plannedMs)
        assertEquals(20_000L, rest.addedMs)
        assertEquals(30_000L, rest.actualMs)
    }

    @Test
    fun `reconciling twice writes nothing the second time`() {
        // ON_START can fire twice for one absence. The back-filled segments are closed on the
        // returned timeline, so the second pass has nothing left to find.
        val first = circuit.reconcile(0, 80_000, 0)
        val second = first.timeline.reconcile(0, 80_000, 0)

        assertTrue(second.backFilled.isEmpty())
    }

    @Test
    fun `reconciling a session that already ran past its end does not auto-complete it again`() {
        val first = circuit.reconcile(0, 10_000_000, 0)
        val second = first.timeline.reconcile(0, 10_000_000, 0)

        assertTrue(second.backFilled.isEmpty())
        // autoCompleted means *this call* closed the final segment. Reporting it whenever the walk
        // merely reaches the end makes it true forever after, and the caller finishes a finished
        // session on every subsequent ON_START.
        assertFalse(second.autoCompleted)
        assertTrue(first.autoCompleted)
    }

    @Test
    fun `a reconciled timeline still reports the same phase stateAt does`() {
        val r = circuit.reconcile(0, 80_000, 0)

        assertEquals(Phase.REST, r.timeline.stateAt(80_000).segment.phase)
        assertEquals(r.landedOrdinal, r.timeline.stateAt(80_000).ordinal)
        assertNull(r.timeline.segments[4].actualMs) // the segment we landed in is untouched
    }
}
