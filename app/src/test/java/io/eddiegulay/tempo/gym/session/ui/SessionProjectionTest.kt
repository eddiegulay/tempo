package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.session.ScalingTier
import io.eddiegulay.tempo.gym.session.Timeline
import io.eddiegulay.tempo.gym.session.circuitLib
import io.eddiegulay.tempo.gym.session.compile
import io.eddiegulay.tempo.gym.session.compiledCircuit
import io.eddiegulay.tempo.gym.session.markClosed
import io.eddiegulay.tempo.gym.session.snapshot
import io.eddiegulay.tempo.gym.session.station
import io.eddiegulay.tempo.ui.ENSO_SWEEP_DEGREES
import io.eddiegulay.tempo.ui.gym.session.PrepareUi
import io.eddiegulay.tempo.ui.gym.session.SessionOverlayUi
import io.eddiegulay.tempo.ui.gym.session.SessionUiState
import io.eddiegulay.tempo.ui.gym.session.compiledPrepare
import io.eddiegulay.tempo.ui.gym.session.sessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One frame of the player, as the two page agents will read it.
 *
 * This is the contract test: every field here is one a page will render without asking a second
 * question, and the ones that are asserted are the ones where deriving it twice on two pages would
 * derive it differently — active time that must exclude 支度, a rest that must refuse ＋二十秒 visibly,
 * a ◁◁ that must not reach back into a countdown the user already sat through.
 */
class SessionProjectionTest {

    // [0 支度 5s][1 運動 a 30s][2 休息 10s][3 運動 b 30s]
    private val circuit = compiledCircuit(rounds = 1)

    private fun frame(
        timeline: Timeline = circuit,
        elapsedMs: Long,
        overlay: SessionOverlayUi = SessionOverlayUi.None,
        prepare: PrepareUi? = null,
    ): SessionUiState = sessionUiState(
        routineId = "r1",
        routineName = "七分間",
        stationsPlanned = 2,
        timeline = timeline,
        elapsedMs = elapsedMs,
        lib = circuitLib,
        overlay = overlay,
        prepare = prepare ?: compiledPrepare(timeline, elapsedMs),
        autoAdvance = false,
        resultsWritten = 0,
        fault = null,
    )

    @Test
    fun `active time excludes the countdown`() {
        // 支度 is timeline offset [0, prepareMs) and is not a segment result, so 「六分十四秒 経過」
        // must not include it (§A PREPARE, data out).
        assertEquals(0L, frame(elapsedMs = 3_000L).activeMs)
        assertEquals(0L, frame(elapsedMs = 5_000L).activeMs)
        assertEquals(10_000L, frame(elapsedMs = 15_000L).activeMs)
    }

    @Test
    fun `the ring depletes over the segment and closes on nothing left`() {
        assertEquals(ENSO_SWEEP_DEGREES, frame(elapsedMs = 5_000L).ensoSweepAngle, 0.01f)
        assertEquals(ENSO_SWEEP_DEGREES / 2f, frame(elapsedMs = 20_000L).ensoSweepAngle, 0.5f)
        assertEquals(0f, frame(elapsedMs = 34_999L).ensoSweepAngle, 1f)
    }

    @Test
    fun `the countdown back in owns the ring while it is on screen`() {
        // The segment underneath is frozen half-way through, and its own fraction would draw a ring
        // that says the plank is half gone while the user is looking at a three-second countdown.
        val state = frame(
            elapsedMs = 20_000L,
            prepare = PrepareUi(remainingMs = 1_500L, totalMs = 3_000L, resumed = true),
        )
        assertEquals(ENSO_SWEEP_DEGREES / 2f, state.ensoSweepAngle, 0.01f)
        assertEquals(Phase.WORK, state.phase)
    }

    @Test
    fun `the compiled countdown ends exactly when the first station starts`() {
        assertEquals(5_000L, compiledPrepare(circuit, 0L)?.remainingMs)
        assertEquals(1L, compiledPrepare(circuit, 4_999L)?.remainingMs)
        assertNull(compiledPrepare(circuit, 5_000L))
    }

    @Test
    fun `a routine with no 支度 has no countdown at all`() {
        // §A PREPARE edge case 5: `prepareSeconds == 0` emits **no** segment rather than a
        // zero-length one, because a zero-duration segment divides by zero in the ensō sweep.
        val none = compiledCircuit(rounds = 1, prepareSeconds = 0)
        assertNull(compiledPrepare(none, 0L))
        assertEquals(Phase.WORK, frame(timeline = none, elapsedMs = 0L).phase)
    }

    @Test
    fun `a station rest can be grown and skipped`() {
        val rest = frame(elapsedMs = 40_000L)
        assertEquals(Phase.REST, rest.phase)
        assertTrue(rest.canExtendRest)
        assertTrue(rest.canSkipForward)
    }

    @Test
    fun `a mandated rest refuses both, and says so rather than going inert`() {
        // §A REST state 4 — Barbara's three minutes are part of the prescription, and a control that
        // vanishes teaches the user it was never there.
        val barbara = compile(
            snapshot(
                engine = Engine.FOR_TIME_WITH_REST,
                stations = listOf(station(0, "a", reps = 20)),
                rounds = 2,
                prepareSeconds = 0,
                restBetweenRounds = 180,
            ),
            ScalingTier.RX,
            circuitLib,
        )
        val restOrdinal = barbara.segments.indexOfFirst { it.phase == Phase.REST }
        val closed = barbara.markClosed(0, 40_000L, 0L)
        val state = frame(timeline = closed, elapsedMs = closed.segments[restOrdinal].startMs + 1_000L)
        assertEquals(Phase.REST, state.phase)
        assertFalse(state.canExtendRest)
        assertFalse(state.canSkipForward)
    }

    @Test
    fun `work never offers ＋二十秒`() {
        val work = frame(elapsedMs = 10_000L)
        assertEquals(Phase.WORK, work.phase)
        assertFalse(work.canExtendRest)
        assertTrue(work.canSkipForward)
    }

    @Test
    fun `the first real segment cannot step back into the countdown`() {
        // §C.4: ◁◁ on the first station degrades to a restart. Re-running a countdown the user has
        // already sat through is not what they asked for.
        assertFalse(frame(elapsedMs = 10_000L).canStepBack)
        assertTrue(frame(elapsedMs = 40_000L).canStepBack)
    }

    @Test
    fun `the next line names the rest and the movement after it`() {
        val work = frame(elapsedMs = 10_000L)
        assertEquals(Phase.REST, work.next?.phase)
        assertEquals("b", work.nextExercise?.nameJa)
        // §A WORK state 6's `NoNextUp`: the final segment has nothing after it, and the page says 完了.
        val last = frame(elapsedMs = 50_000L)
        assertNull(last.next)
        assertNull(last.nextExercise)
    }

    @Test
    fun `an overrunning rep slide holds the ring empty and counts up`() {
        // §A REPS state 2: the ring does not refill and does not pulse, and **nothing about overrun is
        // a failure** — the 目安 line simply starts counting the other way.
        val forTime = compile(
            snapshot(
                engine = Engine.FOR_TIME,
                stations = listOf(station(0, "a", reps = 10)),
                prepareSeconds = 0,
            ),
            ScalingTier.RX,
            circuitLib,
        )
        val state = frame(timeline = forTime, elapsedMs = 60_000L)
        assertTrue(state.awaitingDone)
        assertTrue(state.overrunning)
        assertEquals(0f, state.ensoSweepAngle, 0.01f)
        assertFalse(state.finished)
    }

    @Test
    fun `the overlay is carried, not derived`() {
        // 休止 and 鍛錬を終えますか render the phase underneath them; the host decides which is on top,
        // and the frame reports both so nothing has to guess what it is drawing over.
        val paused = frame(
            elapsedMs = 20_000L,
            overlay = SessionOverlayUi.Paused(90_000L, resumeNeedsPrepare = true, stalled = false),
        )
        assertEquals(Phase.WORK, paused.phase)
        assertNotNull(paused.overlay as? SessionOverlayUi.Paused)
    }
}
