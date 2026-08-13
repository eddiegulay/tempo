package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.Resumability
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.cue.CueEvent
import io.eddiegulay.tempo.gym.session.CompletionReason
import io.eddiegulay.tempo.gym.session.Destination
import io.eddiegulay.tempo.gym.session.Overlay
import io.eddiegulay.tempo.gym.session.Rule
import io.eddiegulay.tempo.gym.session.ScalingTier
import io.eddiegulay.tempo.gym.session.SessionContext
import io.eddiegulay.tempo.gym.session.SessionEvent
import io.eddiegulay.tempo.gym.session.circuitLib
import io.eddiegulay.tempo.gym.session.close
import io.eddiegulay.tempo.gym.session.compile
import io.eddiegulay.tempo.gym.session.compiledCircuit
import io.eddiegulay.tempo.gym.session.markClosed
import io.eddiegulay.tempo.gym.session.snapshot
import io.eddiegulay.tempo.gym.session.station
import io.eddiegulay.tempo.gym.session.step
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.gym.session.DONE_DEBOUNCE_MS
import io.eddiegulay.tempo.ui.gym.session.closesSession
import io.eddiegulay.tempo.ui.gym.session.cueEventFor
import io.eddiegulay.tempo.ui.gym.session.cueSegmentFor
import io.eddiegulay.tempo.ui.gym.session.debounced
import io.eddiegulay.tempo.ui.gym.session.isStalled
import io.eddiegulay.tempo.ui.gym.session.nextExerciseName
import io.eddiegulay.tempo.ui.gym.session.replayable
import io.eddiegulay.tempo.ui.gym.session.roundsCompletedOf
import io.eddiegulay.tempo.ui.gym.session.stationsCompletedOf
import io.eddiegulay.tempo.ui.gym.session.upcomingExerciseName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The host's arithmetic — the half of the session player that fails silently.
 *
 * Two of these are load-bearing beyond their size. [`the screen-on flag is released…`] is the one
 * obligation `ui/gym/KeepAwake.kt` says it cannot discharge structurally, and a session that ends
 * without discharging it is a phone that never sleeps. [`stored rows that no longer describe…`] is
 * §E.2's drift check, where getting it wrong records a plank the user never did.
 */
class SessionReplayTest {

    // [0 支度 5s][1 運動 a 30s][2 休息 10s][3 運動 b 30s]
    private val circuit = compiledCircuit(rounds = 1)

    private fun result(
        ordinal: Int,
        phase: Phase,
        exerciseId: String?,
        actualMs: Long = 30_000L,
    ) = SegmentResult(
        ordinal = ordinal,
        phase = phase,
        roundIndex = 1,
        stationOrder = 0,
        exerciseId = exerciseId,
        measure = Measure.DURATION,
        prescribedReps = null,
        prescribedSeconds = 30,
        actualReps = null,
        actualMs = actualMs,
        addedMs = 0L,
        skipped = false,
        difficultyCoefficient = 1.0,
        volumeUnits = 0.0,
        closedAtWallMs = 0L,
        closedAtElapsedMs = 0L,
    )

    // ── §E.2's drift check ──────────────────────────────────────────────────────────────────────

    @Test
    fun `stored rows that describe the recompiled timeline replay`() {
        val stored = listOf(
            result(1, Phase.WORK, "a"),
            result(2, Phase.REST, null, actualMs = 10_000L),
        )
        assertTrue(replayable(circuit, stored))
    }

    @Test
    fun `stored rows that no longer describe the recompiled timeline are refused`() {
        // The catalogue's secondsPerRep moved in an app update, or a progression advanced, and the
        // routine now compiles to a different set of cells. Replay is by ordinal, so continuing would
        // close the wrong stations — §E.2's "not resumable rather than guessing".
        assertFalse(replayable(circuit, listOf(result(1, Phase.REST, null))))
        assertFalse(replayable(circuit, listOf(result(1, Phase.WORK, "b"))))
        assertFalse(replayable(circuit, listOf(result(99, Phase.WORK, "a"))))
    }

    @Test
    fun `a session with no results always replays`() {
        // Nothing has been claimed yet, so there is nothing that can be claimed wrongly.
        assertTrue(replayable(circuit, emptyList()))
    }

    @Test
    fun `a resume across a reboot is refused by the machine, not by the host`() {
        // §E.3's two checks live in the store's `resumabilityOf`; this is the assertion that the host
        // cannot talk its way past them. REBOOTED and STALE do not disable 続ける, they remove it,
        // because resuming would fabricate the time the device spent switched off.
        val ctx = SessionContext(timeline = circuit, elapsedMs = 6_000L, nowWallMs = 1L)
        assertNull(step(ctx, SessionEvent.PromptResumed(Resumability.REBOOTED)))
        assertNull(step(ctx, SessionEvent.PromptResumed(Resumability.STALE)))
        assertNotNull(step(ctx, SessionEvent.PromptResumed(Resumability.RESUMABLE)))
    }

    // ── §E.4's obligation ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the screen-on flag is released at finish, at quit and at discard`() {
        // `keepAwake` excludes 型の中身 and the resume prompt structurally, and says plainly that
        // GYM.SESSION.COMPLETE is the one it cannot: 記録 renders under GymRoute.Session. So the flag
        // drops only when the host calls onSessionClosed(), and it knows to from these effects.
        val warm = compiledCircuit(rounds = 1).markClosed(0, 5_000L, 0)

        // The last segment closing — 運動 b ends at 75s, with everything before it already written.
        val walked = warm.markClosed(1, 30_000L, 0L).markClosed(2, 10_000L, 0L)
        val finish = step(SessionContext(walked, elapsedMs = 75_000L, nowWallMs = 1L), SessionEvent.Elapsed)
        assertEquals(Rule.LAST_SEGMENT_CLOSED, finish?.rule)
        assertTrue(closesSession(finish!!.effects))

        // ここまでを記録する.
        val partial = step(
            SessionContext(warm, 40_000L, 1L, overlay = Overlay.QUIT_SHEET, resultsWritten = 1),
            SessionEvent.SavePartial,
        )
        assertTrue(closesSession(partial!!.effects))

        // 記録せずに終える ×2.
        val discarded = step(
            SessionContext(warm, 40_000L, 1L, overlay = Overlay.QUIT_SHEET, resultsWritten = 1),
            SessionEvent.DiscardConfirmed,
        )
        assertTrue(closesSession(discarded!!.effects))
    }

    @Test
    fun `an ordinary transition does not release the screen-on flag`() {
        // The other half of the obligation: dropping the flag mid-circuit would put the screen out
        // between two stations, which is the failure the flag exists to prevent.
        val warm = compiledCircuit(rounds = 1).markClosed(0, 5_000L, 0)
        val expired = step(SessionContext(warm, 35_000L, 1L), SessionEvent.Elapsed)
        assertEquals(Rule.WORK_EXPIRED, expired?.rule)
        assertFalse(closesSession(expired!!.effects))
        val paused = step(SessionContext(warm, 20_000L, 1L), SessionEvent.PauseRequested)
        assertFalse(closesSession(paused!!.effects))
    }

    // ── §D.7's rows ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `each disarm reason maps to its own row of the matrix`() {
        // Naming the row is the whole decision: a skip keeps the duck for the segment being landed in,
        // a pause drops it, and COMPLETE deliberately waits for the ceremony it is disarming after.
        assertEquals(CueEvent.PAUSE, cueEventFor(Rule.PAUSED, Destination.Paused))
        assertEquals(CueEvent.PAUSE, cueEventFor(Rule.STALLED, Destination.Paused))
        assertEquals(CueEvent.QUIT_SHEET, cueEventFor(Rule.QUIT_REQUESTED, Destination.QuitSheet))
        assertEquals(CueEvent.QUIT_SHEET, cueEventFor(Rule.DISCARDED, Destination.Home))
        assertEquals(CueEvent.SKIP, cueEventFor(Rule.WORK_SKIPPED, Destination.Live(Phase.REST)))
        assertEquals(CueEvent.SKIP, cueEventFor(Rule.BACK_PREVIOUS, Destination.Live(Phase.WORK)))
    }

    @Test
    fun `a transition into the record takes the COMPLETE row whatever rule got it there`() {
        val complete = Destination.Complete(CompletionReason.LAST_SEGMENT)
        assertEquals(CueEvent.COMPLETE, cueEventFor(Rule.LAST_SEGMENT_CLOSED, complete))
        assertEquals(CueEvent.COMPLETE, cueEventFor(Rule.SAVED_PARTIAL, complete))
        assertEquals(CueEvent.COMPLETE, cueEventFor(Rule.EMOM_FAILED_OUT, complete))
    }

    // ── Arming a segment ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the last cell of a circuit is final and owns no interval-end cue`() {
        assertTrue(cueSegmentFor(circuit, 3, circuitLib, StringsJa).isFinalSegment)
        assertFalse(cueSegmentFor(circuit, 1, circuitLib, StringsJa).isFinalSegment)
    }

    @Test
    fun `the last materialised cell of an AMRAP is never final`() {
        // §A REPS edge case 7: the user must never see 完了 mid-cap. An AMRAP's tail is a compilation
        // detail; its ending is the cap, which arrives as its own cue.
        val amrap = compile(
            snapshot(
                engine = Engine.AMRAP,
                stations = listOf(station(0, "a", reps = 10)),
                prepareSeconds = 0,
                timeCapSeconds = 60,
            ),
            ScalingTier.RX,
            circuitLib,
        )
        assertFalse(cueSegmentFor(amrap, amrap.segments.lastIndex, circuitLib, StringsJa).isFinalSegment)
        assertNotNull(cueSegmentFor(amrap, 0, circuitLib, StringsJa).capAtMs)
    }

    @Test
    fun `the final round announces itself once, and only when there is more than one`() {
        // 「最後の巡」 on a single-lap routine is not information; on the second station of the last
        // round it is late.
        val twoRounds = compiledCircuit(rounds = 2)
        val firstOfLast = twoRounds.segments.indexOfFirst { it.round == 2 && it.phase == Phase.WORK }
        assertTrue(cueSegmentFor(twoRounds, firstOfLast, circuitLib, StringsJa).startsFinalRound)
        val secondOfLast = twoRounds.segments.indexOfLast { it.round == 2 && it.phase == Phase.WORK }
        assertFalse(cueSegmentFor(twoRounds, secondOfLast, circuitLib, StringsJa).startsFinalRound)
        assertFalse(cueSegmentFor(circuit, 1, circuitLib, StringsJa).startsFinalRound)
    }

    @Test
    fun `a rest is armed with the movement that follows it, not with nothing`() {
        // §D.2's two dynamic rows both name the exercise about to start, and 「次、休息 十五秒、
        // そのあと プランク」 is one sentence about one movement.
        assertEquals("b", nextExerciseName(circuit, 2, circuitLib, StringsJa))
        assertEquals("b", upcomingExerciseName(circuit, 2, circuitLib, StringsJa))
        // From a station, "upcoming" is that station itself — a fired INTERVAL_END is fired *after*
        // the transition, so the segment on screen is already the new one.
        assertEquals("a", upcomingExerciseName(circuit, 1, circuitLib, StringsJa))
        assertEquals("b", nextExerciseName(circuit, 1, circuitLib, StringsJa))
        assertNull(nextExerciseName(circuit, 3, circuitLib, StringsJa))
    }

    // ── Counting ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a round counts once every effort in it is closed`() {
        val two = compiledCircuit(rounds = 2)
        assertEquals(0, roundsCompletedOf(two))
        val firstEffortsDone = two.segments
            .filter { it.round == 1 && (it.phase == Phase.WORK || it.phase == Phase.REPS) }
            .fold(two) { tl, s -> tl.markClosed(s.ordinal, s.plannedMs, 0L) }
        // The round's rest is deliberately not required: a round whose last station is done is a round
        // the user finished, whether or not they took the rest after it.
        assertEquals(1, roundsCompletedOf(firstEffortsDone))
    }

    @Test
    fun `a skipped station is recorded but is not a station done`() {
        // §A QUIT_SHEET's 「二十種目中 八」 has to be the honest number, and とばした is in the record.
        val done = circuit.markClosed(1, 30_000L, 0L)
        assertEquals(1, stationsCompletedOf(done))
        val skipped = done.close(ordinal = 3, actualMs = 4_000L, actualReps = null, skipped = true)
        assertEquals(1, stationsCompletedOf(skipped))
    }

    // ── The two guards ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `an open segment held for half an hour is a forgotten phone`() {
        assertFalse(isStalled(29 * 60 * 1000L))
        assertTrue(isStalled(30 * 60 * 1000L))
    }

    @Test
    fun `a second tap inside the window is the same tap`() {
        assertTrue(debounced(lastAtMs = 1_000L, nowMs = 1_100L, windowMs = DONE_DEBOUNCE_MS))
        assertFalse(debounced(lastAtMs = 1_000L, nowMs = 1_400L, windowMs = DONE_DEBOUNCE_MS))
        assertFalse(debounced(lastAtMs = null, nowMs = 1_000L, windowMs = DONE_DEBOUNCE_MS))
    }
}
