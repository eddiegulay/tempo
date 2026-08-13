package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.Resumability
import io.eddiegulay.tempo.gym.SegmentResultDraft
import io.eddiegulay.tempo.gym.cue.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every row of `03-player.md` §C.1, and the guards that stop the wrong one firing.
 *
 * The table is thirty-four rows because a session player has thirty-four ways to end up somewhere,
 * and the ones that get shipped broken are never the obvious ones — they are "back on the quit sheet
 * discarded the session", "the tick fired while the sheet was open and the clock kept running",
 * "◁◁ on the first station stepped back into 支度". Each of those is one row here.
 *
 * The rows are asserted by [Rule], which carries the table's own number, so a test failure names the
 * line of the spec it broke.
 */
class SessionMachineTest {

    /** 支度 already served, which is where every live rule below actually happens. */
    private val warm = compiledCircuit(rounds = 2).markClosed(0, 5_000, 0)
    // [0 支度 5s][5 運動 30s][35 休息 10s][45 運動 30s][75 巡の間 60s][135 運動 30s][165 休息 10s][175 運動 30s]

    private val cold = compiledCircuit(rounds = 2)

    private val amrapRoutine = snapshot(
        Engine.AMRAP,
        listOf(station(0, "a", reps = 10), station(1, "b", reps = 5)),
        prepareSeconds = 0,
        timeCapSeconds = 60,
    )
    private val amrap = compile(amrapRoutine, ScalingTier.RX, circuitLib)

    private fun ctx(
        timeline: Timeline,
        elapsedMs: Long,
        overlay: Overlay = Overlay.NONE,
        autoAdvance: Boolean = false,
        resultsWritten: Int = 0,
        pausedForMs: Long = 0L,
        pausedBeforeSheet: Boolean = false,
    ) = SessionContext(
        timeline = timeline,
        elapsedMs = elapsedMs,
        nowWallMs = 1_000_000L,
        overlay = overlay,
        autoAdvance = autoAdvance,
        resultsWritten = resultsWritten,
        pausedForMs = pausedForMs,
        pausedBeforeSheet = pausedBeforeSheet,
    )

    /** What the caller feeds the next [step] after applying a transition. */
    private fun SessionContext.then(t: SessionTransition, elapsedMs: Long = this.elapsedMs) = copy(
        timeline = t.timeline,
        elapsedMs = elapsedMs,
        overlay = t.overlay,
        pausedBeforeSheet = t.pausedBeforeSheet,
    )

    private fun SessionTransition.write(): SegmentResultDraft =
        effects.filterIsInstance<SessionEffect.Write>().single().draft

    private fun SessionTransition.wrote(): Boolean = effects.any { it is SessionEffect.Write }

    // ─── 1. Entry ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `row 1 - 始める inserts the session before anything else happens`() {
        val t = step(ctx(cold, 0), SessionEvent.Started)!!

        assertEquals(Rule.START, t.rule)
        assertEquals(Destination.Live(Phase.PREPARE), t.destination)
        // A session that exists in the UI but not in the DB is the one state process death cannot
        // recover from, so the insert is first and navigation waits on it.
        assertEquals(
            listOf(SessionEffect.InsertSession, SessionEffect.AnchorClock),
            t.effects,
        )
    }

    // ─── 2, 3. PREPARE ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `row 2 - prepare running out fires the session cue and writes nothing`() {
        val t = step(ctx(cold, 5_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.PREPARE_ELAPSED, t.rule)
        assertEquals(Destination.Live(Phase.WORK), t.destination)
        assertEquals(listOf(SessionEffect.Fire(Cue.SESSION_START)), t.effects)
        assertFalse(t.wrote()) // 支度 is not a segment result
        assertTrue(t.timeline.segments[0].closed) // but it is consumed, so it fires exactly once
    }

    @Test
    fun `row 3 - skipping prepare seeks the clock to the end of it`() {
        val t = step(ctx(cold, 2_000), SessionEvent.SkipForward)!!

        assertEquals(Rule.PREPARE_SKIPPED, t.rule)
        assertEquals(5_000L, t.seekToMs)
        assertFalse(t.wrote())
        assertTrue(t.effects.contains(SessionEffect.DisarmCues))
    }

    // ─── 4, 5. WORK ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `row 4 - a work segment reaching zero writes itself and hands over to the rest`() {
        val t = step(ctx(warm, 35_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.WORK_EXPIRED, t.rule)
        assertEquals(Destination.Live(Phase.REST), t.destination)
        assertEquals(1, t.write().ordinal)
        assertEquals(30_000L, t.write().actualMs)
        assertFalse(t.write().skipped)
        // The write and its clock checkpoint are one transaction, in that order.
        assertEquals(
            listOf(
                SessionEffect.Write(t.write()),
                SessionEffect.Checkpoint,
                SessionEffect.Fire(Cue.INTERVAL_END),
            ),
            t.effects,
        )
    }

    @Test
    fun `row 4 - a late tick does not lengthen the segment it closes`() {
        // The render loop is 50ms; the timeline must not drift by its jitter once per station.
        val t = step(ctx(warm, 35_050), SessionEvent.Elapsed)!!

        assertEquals(30_000L, t.write().actualMs)
        assertEquals(35_000L, t.timeline.segments[2].startMs)
    }

    @Test
    fun `row 5 - skipping a station records the time actually spent in it`() {
        val t = step(ctx(warm, 20_000), SessionEvent.SkipForward)!!

        assertEquals(Rule.WORK_SKIPPED, t.rule)
        assertTrue(t.write().skipped)
        assertEquals(15_000L, t.write().actualMs)
        assertNull(t.write().actualReps)
        // No seek: closing the segment re-flowed the next one to start exactly now.
        assertNull(t.seekToMs)
        assertEquals(20_000L, t.timeline.segments[2].startMs)
        assertEquals(Destination.Live(Phase.REST), t.destination)
    }

    // ─── 6, 7, 8, 9. REPS ───────────────────────────────────────────────────────────────────────

    @Test
    fun `row 6 - 済 closes at the measured duration, not at the estimate`() {
        val t = step(ctx(amrap, 12_000), SessionEvent.RepsCompleted(actualReps = null))!!

        assertEquals(Rule.REPS_DONE, t.rule)
        assertEquals(12_000L, t.write().actualMs) // the estimate was 20s
        assertTrue(t.effects.contains(SessionEffect.Fire(Cue.REP_DONE)))
        assertEquals(12_000L, t.timeline.segments[1].startMs)
    }

    @Test
    fun `row 7 - the rep wheel's count is recorded as given, over or under the prescription`() {
        // §A REPS edge case 3: more than prescribed is legal and interesting. No clamp, no warning.
        val t = step(ctx(amrap, 30_000), SessionEvent.RepsCompleted(actualReps = 14, adjusted = true))!!

        assertEquals(Rule.REPS_ADJUSTED, t.rule)
        assertEquals(14, t.write().actualReps)
        assertEquals(10, t.write().prescribedReps)
    }

    @Test
    fun `a wheel wound down to zero is a set the user did not do`() {
        // §A REPS edge case 4: `actualReps == 0` via the wheel is skipped = true, rendered とばした.
        val t = step(ctx(amrap, 30_000), SessionEvent.RepsCompleted(actualReps = 0, adjusted = true))!!

        assertTrue(t.write().skipped)
        // Not 0. The schema's CHECK (skipped = 0 OR actual_reps IS NULL) rejects the pair outright,
        // and 済 ・ 〇回 in COMPLETE's 内訳 would claim a set that never happened.
        assertNull(t.write().actualReps)
        assertTrue(t.timeline.segments[0].skipped)
        assertNull(t.timeline.segments[0].actualReps)
    }

    @Test
    fun `a second 済 keyed to the segment it was drawn for closes nothing`() {
        // §A REPS edge case 6. The second tap of a double tap lands on the *next* segment's UI, so
        // an unkeyed close would close a station the user has not started.
        val once = step(ctx(amrap, 12_000), SessionEvent.RepsCompleted(null, ordinal = 0))!!
        val twice = step(
            ctx(once.timeline, 12_000),
            SessionEvent.RepsCompleted(null, ordinal = 0),
        )

        assertNull(twice)
        assertNotNull(step(ctx(once.timeline, 12_000), SessionEvent.RepsCompleted(null, ordinal = 1)))
    }

    @Test
    fun `row 8 - 目安で自動的に進む closes the slide with the prescribed count`() {
        val t = step(ctx(amrap, 20_000, autoAdvance = true), SessionEvent.Elapsed)!!

        assertEquals(Rule.REPS_AUTO_ADVANCED, t.rule)
        assertEquals(10, t.write().actualReps)
    }

    @Test
    fun `row 9 - without it, reaching the estimate advances nothing at all`() {
        val t = step(ctx(amrap, 25_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.REPS_OVERRUN, t.rule)
        assertEquals(Destination.Live(Phase.REPS), t.destination)
        assertFalse(t.wrote())
        assertEquals(amrap.segments, t.timeline.segments)
    }

    @Test
    fun `the auto-advance preference never reaches an emom, whose grid already gates`() {
        val t = step(ctx(emom(rounds = 3), 30_000, autoAdvance = true), SessionEvent.Elapsed)

        assertNull(t)
        assertFalse(autoAdvanceApplies(Engine.EMOM))
        assertFalse(autoAdvanceApplies(Engine.EMOM_ASCENDING))
        assertFalse(autoAdvanceApplies(Engine.INTERVAL_CIRCUIT))
    }

    // ─── 10, 11, 12, 13. Caps and fail-outs ─────────────────────────────────────────────────────

    @Test
    fun `row 10 - an emom minute closing over open work is a recorded miss and the session goes on`() {
        val t = step(ctx(emom(rounds = 3), 60_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.EMOM_MISSED, t.rule)
        assertTrue(t.write().skipped)
        assertTrue(t.effects.contains(SessionEffect.Fire(Cue.EMOM_FAIL)))
        assertFalse(t.effects.any { it is SessionEffect.Finish })
        // Minute two, not the zero-span remainder that was never earned (§B.6 invariant 4).
        assertEquals(Destination.Live(Phase.REPS), t.destination)
        assertEquals(60_000L, t.timeline.segments[2].startMs)
    }

    @Test
    fun `a minute used to its last second leaves no 残り row behind`() {
        // The cell closed at the boundary, so the remainder re-flows to zero span. §B.6 invariant 4
        // keeps it off the screen; writing it anyway puts a 0:00 残り line in COMPLETE's 内訳 for a
        // cell the player never showed.
        val missed = step(ctx(emom(rounds = 3), 60_000), SessionEvent.Elapsed)!!
        assertEquals(Phase.REPS, missed.write().phase)
        assertTrue(missed.timeline.segments[1].vestigial)

        val remainder = step(ctx(missed.timeline, 60_000), SessionEvent.Elapsed)!!

        assertFalse(remainder.wrote())
        // Still consumed on the timeline, or the due-scan re-selects it on every tick forever.
        assertTrue(remainder.timeline.segments[1].closed)
        assertEquals(Destination.Live(Phase.REPS), remainder.destination)
        assertNull(step(ctx(remainder.timeline, 60_000), SessionEvent.Elapsed))
    }

    @Test
    fun `row 11 - the ascending ladder ends when the minute beats you`() {
        val t = step(ctx(emom(rounds = 3, ascending = true), 60_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.EMOM_FAILED_OUT, t.rule)
        assertEquals(Destination.Complete(CompletionReason.EMOM_FAIL_OUT), t.destination)
        // Reaching failure is the protocol succeeding, so it finishes complete rather than partial.
        assertTrue(t.effects.contains(SessionEffect.Finish(complete = true)))
    }

    @Test
    fun `row 12 - the amrap cap ends the session wherever it lands`() {
        val t = step(ctx(amrap, 60_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.AMRAP_CAPPED, t.rule)
        assertEquals(Destination.Complete(CompletionReason.AMRAP_CAP), t.destination)
        assertTrue(t.effects.contains(SessionEffect.Fire(Cue.AMRAP_CAP)))
        assertTrue(t.effects.contains(SessionEffect.Finish(complete = true)))
        assertTrue(t.wrote())
    }

    @Test
    fun `row 12 - the station the cap interrupted is a partial, not a completed set of unknown reps`() {
        val t = step(ctx(amrap, 60_000), SessionEvent.Elapsed)!!

        // The same shape §A QUIT_SHEET edge case 2 gives any mid-set close: partial, no rep count.
        assertTrue(t.write().skipped)
        assertNull(t.write().actualReps)
        assertTrue(t.timeline.segments[t.write().ordinal].skipped)
    }

    @Test
    fun `the last materialised round is not the end of an amrap`() {
        // §A REPS edge case 7: "the user must never see 完了 mid-cap." Closing the tail of the list
        // while the cap still runs must leave a live frame for the caller's needsAnotherRound check.
        val tail = amrap.segments.lastIndex
        val nearlyDone = (0 until tail).fold(amrap) { tl, o -> tl.close(o, 1_000) }

        val t = step(ctx(nearlyDone, tail * 1_000L + 500), SessionEvent.RepsCompleted(actualReps = 5))!!

        assertEquals(Rule.REPS_DONE, t.rule)
        assertTrue(t.destination is Destination.Live)
        assertFalse(t.effects.any { it is SessionEffect.Finish })
        assertFalse(t.effects.contains(SessionEffect.Fire(Cue.SESSION_COMPLETE)))
        assertTrue(t.timeline.needsAnotherRound(tail))
    }

    @Test
    fun `the same close past the cap does finish the session`() {
        val tail = amrap.segments.lastIndex
        val nearlyDone = (0 until tail).fold(amrap) { tl, o -> tl.close(o, 1_000) }

        val t = step(ctx(nearlyDone, 90_000), SessionEvent.RepsCompleted(actualReps = 5))!!

        assertEquals(Rule.LAST_SEGMENT_CLOSED, t.rule)
        assertEquals(Destination.Complete(CompletionReason.LAST_SEGMENT), t.destination)
    }

    @Test
    fun `row 13 - an amrap grows another round without changing phase`() {
        val t = step(
            ctx(amrap, 100_000),
            SessionEvent.RoundsExhausted(amrapRoutine, circuitLib),
        )!!

        assertEquals(Rule.AMRAP_EXTENDED, t.rule)
        assertEquals(5, t.timeline.totalRounds)
        assertFalse(t.wrote())
    }

    @Test
    fun `a timeline that cannot grow refuses the request rather than pretending`() {
        assertNull(step(ctx(warm, 20_000), SessionEvent.RoundsExhausted(circuit(), circuitLib)))
    }

    // ─── 14, 15, 16. REST ───────────────────────────────────────────────────────────────────────

    @Test
    fun `row 14 - a rest running out writes itself like any other segment`() {
        val t = step(ctx(warm.markClosed(1, 30_000, 0), 45_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.REST_EXPIRED, t.rule)
        assertEquals(2, t.write().ordinal)
        assertEquals(Phase.REST, t.write().phase)
        assertEquals(Destination.Live(Phase.WORK), t.destination)
    }

    @Test
    fun `row 15 - とばす on an ordinary rest closes it as skipped`() {
        val t = step(ctx(warm, 40_000), SessionEvent.SkipForward)!!

        assertEquals(Rule.REST_SKIPPED, t.rule)
        assertTrue(t.write().skipped)
        assertEquals(5_000L, t.write().actualMs)
    }

    @Test
    fun `row 16 - ＋二十秒 grows the rest and checkpoints it immediately`() {
        val t = step(ctx(warm, 40_000), SessionEvent.ExtendRest)!!

        assertEquals(Rule.REST_EXTENDED, t.rule)
        assertEquals(20_000L, t.timeline.segments[2].addedMs)
        assertEquals(
            listOf(SessionEffect.Fire(Cue.EXTEND_ACK), SessionEffect.Checkpoint),
            t.effects,
        )
        // A session recovered after process death must remember the added time or the record lies.
        assertTrue(t.effects.contains(SessionEffect.Checkpoint))
    }

    @Test
    fun `a mandated rest refuses both the skip and the extension`() {
        val barbara = compile(
            snapshot(
                Engine.FOR_TIME_WITH_REST,
                listOf(station(0, "a", reps = 20)),
                rounds = 2,
                prepareSeconds = 0,
                restBetweenRounds = 180,
            ),
            ScalingTier.RX,
            circuitLib,
        ).close(0, 40_000)

        assertEquals(Phase.REST, barbara.stateAt(50_000).segment.phase)
        assertNull(step(ctx(barbara, 50_000), SessionEvent.SkipForward))
        assertNull(step(ctx(barbara, 50_000), SessionEvent.ExtendRest))
    }

    @Test
    fun `＋二十秒 that lands after the frontier moved on is dropped, never rewound`() {
        // §A REST edge case 1. The tap is resolved against the segment we are in *now*.
        assertNull(step(ctx(warm, 20_000), SessionEvent.ExtendRest)) // we are in 運動, not 休息
    }

    // ─── 17, 18, 19. PAUSED ─────────────────────────────────────────────────────────────────────

    @Test
    fun `row 17 - pausing stops the clock and disarms every pending cue`() {
        val t = step(ctx(warm, 20_000), SessionEvent.PauseRequested)!!

        assertEquals(Rule.PAUSED, t.rule)
        assertEquals(Destination.Paused, t.destination)
        assertEquals(Overlay.PAUSED, t.overlay)
        // A pending 3-2-1 that fires during a pause is the most jarring bug this feature can ship.
        assertEquals(
            listOf(SessionEffect.PauseClock, SessionEffect.DisarmCues, SessionEffect.Checkpoint),
            t.effects,
        )
    }

    @Test
    fun `row 18 - a short pause resumes straight back into the phase it left`() {
        val t = step(
            ctx(warm, 20_000, overlay = Overlay.PAUSED, pausedForMs = 20_000),
            SessionEvent.ResumeRequested,
        )!!

        assertEquals(Rule.RESUMED, t.rule)
        assertEquals(Destination.Live(Phase.WORK), t.destination)
        assertEquals(Overlay.NONE, t.overlay)
        assertNull(t.prepareForMs)
        assertTrue(t.effects.contains(SessionEffect.ResumeClock))
    }

    @Test
    fun `row 19 - over a minute away and you get a countdown back in`() {
        val t = step(
            ctx(warm, 20_000, overlay = Overlay.PAUSED, pausedForMs = 60_001),
            SessionEvent.ResumeRequested,
        )!!

        assertEquals(Rule.RESUMED_WITH_PREPARE, t.rule)
        assertEquals(3_000L, t.prepareForMs)
        // NOT Live(PREPARE): the timeline has no 支度 cell under the frontier, so stateAt goes on
        // reporting 運動 and a destination naming PREPARE is a phase only the shell could invent.
        assertEquals(Destination.Live(Phase.WORK), t.destination)
    }

    @Test
    fun `row 19 - the three seconds of 支度 are not charged to the station being resumed`() {
        // Twenty seconds into a thirty-second station, five minutes away, then 続ける.
        val resume = step(
            ctx(warm, 20_000, overlay = Overlay.PAUSED, pausedForMs = 300_000),
            SessionEvent.ResumeRequested,
        )!!

        assertEquals(
            listOf(SessionEffect.ResumeClockAfter(3_000), SessionEffect.Checkpoint),
            resume.effects,
        )
        // A bare ResumeClock starts the session clock under a countdown the user is still watching.
        assertFalse(resume.effects.contains(SessionEffect.ResumeClock))

        val closed = step(ctx(warm, 20_000 + clockAdvanceDuringPrepare(resume)), SessionEvent.SkipForward)!!
        // Fifteen seconds of work — the number the record would carry if the user had never walked
        // away. Charging the countdown to the segment inflates it to eighteen.
        assertEquals(15_000L, closed.write().actualMs)
    }

    /** How far session-elapsed moves while the resume countdown runs, per the effect handed back. */
    private fun clockAdvanceDuringPrepare(t: SessionTransition): Long =
        if (t.effects.contains(SessionEffect.ResumeClock)) 3_000L else 0L

    @Test
    fun `exactly sixty seconds is still a short pause`() {
        val t = step(
            ctx(warm, 20_000, overlay = Overlay.PAUSED, pausedForMs = LONG_PAUSE_MS),
            SessionEvent.ResumeRequested,
        )!!

        assertEquals(Rule.RESUMED, t.rule)
    }

    @Test
    fun `skipping while paused stays paused`() {
        // A user who paused to re-plan should be able to re-plan without the clock starting under them.
        val t = step(ctx(warm, 20_000, overlay = Overlay.PAUSED), SessionEvent.SkipForward)!!

        assertEquals(Rule.WORK_SKIPPED, t.rule)
        assertEquals(Overlay.PAUSED, t.overlay)
        // Overlay and destination are two fields and a shell may key on either. A Live destination
        // under a PAUSED overlay is a screen that un-pauses itself on a skip.
        assertEquals(Destination.Paused, t.destination)
    }

    @Test
    fun `stepping back while paused stays paused too`() {
        val restart = step(
            ctx(warm, 20_000, overlay = Overlay.PAUSED),
            SessionEvent.BackTapped(BackAction.RESTART_SEGMENT),
        )!!
        val back = step(
            ctx(warm, 40_000, overlay = Overlay.PAUSED),
            SessionEvent.BackTapped(BackAction.PREVIOUS_SEGMENT),
        )!!

        assertEquals(Destination.Paused, restart.destination)
        assertEquals(Destination.Paused, back.destination)
    }

    // ─── 20, 21, 22, 23. The quit sheet ─────────────────────────────────────────────────────────

    @Test
    fun `row 20 - the clock pauses the instant the sheet opens`() {
        val t = step(ctx(warm, 20_000), SessionEvent.QuitRequested)!!

        assertEquals(Rule.QUIT_REQUESTED, t.rule)
        assertEquals(Overlay.QUIT_SHEET, t.overlay)
        // Otherwise the record includes the time spent deciding.
        assertTrue(t.effects.contains(SessionEffect.PauseClock))
    }

    @Test
    fun `row 20 - the sheet is reachable from a pause as well as from a live phase`() {
        val t = step(ctx(warm, 20_000, overlay = Overlay.PAUSED), SessionEvent.QuitRequested)!!

        assertEquals(Rule.QUIT_REQUESTED, t.rule)
        assertEquals(Overlay.QUIT_SHEET, t.overlay)
        // The single overlay slot has just overwritten the pause, so the sheet carries the fact.
        assertTrue(t.pausedBeforeSheet)
        // The clock is already stopped; pausing it again overwrites the anchor §A PAUSED's data-out
        // depends on.
        assertFalse(t.effects.contains(SessionEffect.PauseClock))
        assertTrue(t.effects.contains(SessionEffect.DisarmCues))
    }

    @Test
    fun `row 21 - back on the sheet is つづける and never a discard`() {
        val t = step(ctx(warm, 20_000, overlay = Overlay.QUIT_SHEET), SessionEvent.QuitDismissed)!!

        assertEquals(Rule.QUIT_DISMISSED, t.rule)
        assertEquals(Destination.Live(Phase.WORK), t.destination)
        assertEquals(listOf(SessionEffect.ResumeClock), t.effects)
    }

    @Test
    fun `row 21 - ┃┃ then ✕ then back leaves the session paused, not running`() {
        // The whole sequence, as the caller runs it: each context is the previous transition applied.
        val live = ctx(warm, 20_000)
        val paused = step(live, SessionEvent.PauseRequested)!!
        val sheet = step(live.then(paused), SessionEvent.QuitRequested)!!
        val back = step(live.then(paused).then(sheet), SessionEvent.QuitDismissed)!!

        assertEquals(Rule.QUIT_DISMISSED, back.rule)
        // §C.1 row 21 returns to the *originating* phase, and here that phase was 休止.
        assertEquals(Destination.Paused, back.destination)
        assertEquals(Overlay.PAUSED, back.overlay)
        // §A PAUSED: "a back press must never restart a timer the user cannot see."
        assertFalse(back.effects.contains(SessionEffect.ResumeClock))
        assertTrue(back.effects.isEmpty())
    }

    @Test
    fun `row 22 - ここまでを記録する closes the segment in progress as a skip with no rep count`() {
        val t = step(
            ctx(warm, 20_000, overlay = Overlay.QUIT_SHEET, resultsWritten = 3),
            SessionEvent.SavePartial,
        )!!

        assertEquals(Rule.SAVED_PARTIAL, t.rule)
        assertEquals(Destination.Complete(CompletionReason.PARTIAL_SAVE), t.destination)
        assertTrue(t.write().skipped)
        // Guessing a rep count from the pacer would fabricate data (§A QUIT_SHEET edge case 2).
        assertNull(t.write().actualReps)
        assertTrue(t.effects.contains(SessionEffect.Finish(complete = false)))
    }

    @Test
    fun `there is nothing to save before the first segment closes`() {
        assertNull(
            step(
                ctx(warm, 20_000, overlay = Overlay.QUIT_SHEET, resultsWritten = 0),
                SessionEvent.SavePartial,
            ),
        )
    }

    @Test
    fun `row 23 - 記録せずに終える deletes the session and goes home`() {
        val t = step(
            ctx(warm, 20_000, overlay = Overlay.QUIT_SHEET, resultsWritten = 3),
            SessionEvent.DiscardConfirmed,
        )!!

        assertEquals(Rule.DISCARDED, t.rule)
        assertEquals(Destination.Home, t.destination)
        assertTrue(t.effects.contains(SessionEffect.Discard))
    }

    @Test
    fun `the clock cannot advance while the sheet is open`() {
        val open = ctx(warm, 35_000, overlay = Overlay.QUIT_SHEET)

        assertNull(step(open, SessionEvent.Elapsed))
        assertNull(step(open, SessionEvent.SkipForward))
        assertNull(step(open, SessionEvent.RepsCompleted(null)))
        assertNull(step(open, SessionEvent.ExtendRest))
        assertNull(step(open, SessionEvent.BackTapped(BackAction.RESTART_SEGMENT)))
    }

    // ─── 24, 25. ◁ ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `row 24 - the first back tap restarts the segment and persists nothing`() {
        val t = step(ctx(warm, 20_000), SessionEvent.BackTapped(BackAction.RESTART_SEGMENT))!!

        assertEquals(Rule.BACK_RESTART, t.rule)
        assertEquals(5_000L, t.seekToMs)
        assertFalse(t.wrote())
        assertEquals(warm.segments, t.timeline.segments)
    }

    @Test
    fun `row 25 - the second deletes the previous result and reopens it`() {
        val t = step(ctx(warm, 40_000), SessionEvent.BackTapped(BackAction.PREVIOUS_SEGMENT))!!

        assertEquals(Rule.BACK_PREVIOUS, t.rule)
        assertEquals(5_000L, t.seekToMs)
        // Without the delete the record counts that station twice.
        assertTrue(t.effects.contains(SessionEffect.DeleteResult(1)))
        assertFalse(t.timeline.segments[1].closed)
        assertEquals(Destination.Live(Phase.WORK), t.destination)
    }

    @Test
    fun `stepping back from the first real segment degrades to a restart, never into 支度`() {
        val t = step(ctx(warm, 20_000), SessionEvent.BackTapped(BackAction.PREVIOUS_SEGMENT))!!

        assertEquals(Rule.BACK_RESTART, t.rule)
        assertEquals(5_000L, t.seekToMs)
        assertFalse(t.effects.any { it is SessionEffect.DeleteResult })
    }

    // ─── 26. The end ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `row 26 - closing the last segment finishes the session complete`() {
        val single = compile(
            snapshot(Engine.INTERVAL_CIRCUIT, listOf(station(0, "a", seconds = 30)), prepareSeconds = 0),
            ScalingTier.RX,
            circuitLib,
        )

        val t = step(ctx(single, 30_000), SessionEvent.Elapsed)!!

        assertEquals(Rule.LAST_SEGMENT_CLOSED, t.rule)
        assertEquals(Destination.Complete(CompletionReason.LAST_SEGMENT), t.destination)
        assertTrue(t.effects.contains(SessionEffect.Fire(Cue.SESSION_COMPLETE)))
        assertTrue(t.effects.contains(SessionEffect.Finish(complete = true)))
        assertTrue(t.wrote())
    }

    @Test
    fun `row 26 - skipping the last segment finishes it too`() {
        val single = compile(
            snapshot(Engine.INTERVAL_CIRCUIT, listOf(station(0, "a", seconds = 30)), prepareSeconds = 0),
            ScalingTier.RX,
            circuitLib,
        )

        val t = step(ctx(single, 4_000), SessionEvent.SkipForward)!!

        assertEquals(Rule.LAST_SEGMENT_CLOSED, t.rule)
        assertTrue(t.write().skipped)
        assertTrue(t.effects.contains(SessionEffect.Finish(complete = true)))
        // §D.7's skip row, and the case CueEngine.enterSegment cannot mask: there is no next segment
        // to arm, so a 3-2-1 left pending for the final station fires over the completion ceremony.
        assertEquals(SessionEffect.DisarmCues, t.effects.first())
        assertTrue(
            t.effects.indexOf(SessionEffect.DisarmCues) <
                t.effects.indexOf(SessionEffect.Fire(Cue.SESSION_COMPLETE)),
        )
    }

    @Test
    fun `every forward skip disarms the cues it was racing`() {
        // §A WORK edge case 8: "▷ during the last 3s — the interval-end cue must be cancelled, not
        // fired twice." ◁ and the 支度 skip both did this; the ordinary path did not.
        val work = step(ctx(warm, 33_000), SessionEvent.SkipForward)!!
        val rest = step(ctx(warm, 40_000), SessionEvent.SkipForward)!!

        assertEquals(SessionEffect.DisarmCues, work.effects.first())
        assertEquals(SessionEffect.DisarmCues, rest.effects.first())
    }

    @Test
    fun `a segment that ends on its own does not disarm mid-session`() {
        // The disarm belongs to the skip, not to the close: an auto-advance hands the next segment
        // to enterSegment, which cancels and re-arms in one move.
        val t = step(ctx(warm, 35_000), SessionEvent.Elapsed)!!

        assertFalse(t.effects.contains(SessionEffect.DisarmCues))
    }

    // ─── 27, 28. Coming back ────────────────────────────────────────────────────────────────────

    @Test
    fun `row 27 - foregrounding back-fills what the clock did while we were away`() {
        val t = step(ctx(cold, 80_000), SessionEvent.Foregrounded)!!

        assertEquals(Rule.RECONCILED, t.rule)
        assertEquals(3, t.effects.filterIsInstance<SessionEffect.Write>().size)
        assertEquals(Destination.Live(Phase.REST), t.destination)
    }

    @Test
    fun `row 27 - a frontier that ran past the end lands on the record, auto-completed`() {
        val t = step(ctx(cold, 10_000_000), SessionEvent.Foregrounded)!!

        assertEquals(Destination.Complete(CompletionReason.RECONCILED_PAST_END), t.destination)
        assertTrue(t.effects.contains(SessionEffect.Finish(complete = true)))
    }

    @Test
    fun `foregrounding with nothing to fix writes nothing`() {
        assertNull(step(ctx(cold, 1_000), SessionEvent.Foregrounded))
    }

    @Test
    fun `a session already reconciled past its end is not finished a second time`() {
        // ON_START fires on every return to the app. The first pass finished the session; the second
        // has nothing to write and must not hand the caller another Finish(complete = true).
        val once = step(ctx(cold, 10_000_000), SessionEvent.Foregrounded)!!

        assertNull(step(ctx(once.timeline, 10_000_000), SessionEvent.Foregrounded))
    }

    @Test
    fun `row 28 - the stall guard pauses and decides nothing`() {
        val t = step(ctx(amrap, 1_900_000), SessionEvent.Stalled)!!

        assertEquals(Rule.STALLED, t.rule)
        assertEquals(Destination.Paused, t.destination)
        // No auto-save. A forgotten phone is the user's call, not the app's.
        assertFalse(t.effects.any { it is SessionEffect.Finish })
        assertFalse(t.wrote())
    }

    // ─── 29–32. The resume prompt ───────────────────────────────────────────────────────────────

    @Test
    fun `row 29 - an open session on cold start raises the prompt`() {
        val t = step(ctx(warm, 0), SessionEvent.ColdStartFoundSession)!!

        assertEquals(Rule.COLD_START_OPEN_SESSION, t.rule)
        assertEquals(Destination.ResumePrompt, t.destination)
    }

    @Test
    fun `row 30 - 続ける re-anchors and starts from a fresh countdown`() {
        val t = step(ctx(warm, 20_000), SessionEvent.PromptResumed(Resumability.RESUMABLE))!!

        assertEquals(Rule.PROMPT_RESUMED, t.rule)
        // §A PREPARE edge case 4: three seconds, over the exercise the session was interrupted
        // *inside* — which is the phase stateAt reports on the replayed timeline, not PREPARE.
        assertEquals(3_000L, t.prepareForMs)
        assertEquals(Destination.Live(Phase.WORK), t.destination)
        assertEquals(
            listOf(SessionEffect.AnchorClock, SessionEffect.ResumeClockAfter(3_000)),
            t.effects,
        )
    }

    @Test
    fun `続ける does not exist after a reboot or once the session has gone stale`() {
        // Not disabled — removed. Any resume across a reboot would fabricate the time the device
        // spent switched off.
        assertNull(step(ctx(warm, 0), SessionEvent.PromptResumed(Resumability.REBOOTED)))
        assertNull(step(ctx(warm, 0), SessionEvent.PromptResumed(Resumability.STALE)))
        assertNull(step(ctx(warm, 0), SessionEvent.PromptResumed(Resumability.NOTHING_TO_SAVE)))
    }

    @Test
    fun `row 31 - 記録する finishes the abandoned session as incomplete`() {
        val t = step(ctx(warm, 0, resultsWritten = 2), SessionEvent.PromptRecorded)!!

        assertEquals(Rule.PROMPT_RECORDED, t.rule)
        assertEquals(Destination.Complete(CompletionReason.PARTIAL_SAVE), t.destination)
        assertTrue(t.effects.contains(SessionEffect.Finish(complete = false)))
    }

    @Test
    fun `記録する is not offered when there is nothing worth saving`() {
        assertNull(step(ctx(warm, 0, resultsWritten = 0), SessionEvent.PromptRecorded))
    }

    @Test
    fun `row 32 - 捨てる deletes it`() {
        val t = step(ctx(warm, 0), SessionEvent.PromptDiscarded)!!

        assertEquals(Rule.PROMPT_DISCARDED, t.rule)
        assertEquals(Destination.Home, t.destination)
        assertTrue(t.effects.contains(SessionEffect.Discard))
    }

    // ─── 33, 34. Leaving the record ─────────────────────────────────────────────────────────────

    @Test
    fun `row 33 - 閉じる pops the whole player stack`() {
        val t = step(ctx(warm, 205_000), SessionEvent.RecordClosed)!!

        assertEquals(Rule.RECORD_CLOSED, t.rule)
        assertEquals(Destination.Home, t.destination)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `row 34 - もう一度 goes back to the routine, not into a second session`() {
        val t = step(ctx(warm, 205_000), SessionEvent.RecordRepeated)!!

        assertEquals(Rule.RECORD_REPEATED, t.rule)
        assertEquals(Destination.RoutineDetail, t.destination)
        assertTrue(t.effects.isEmpty())
    }

    // ─── The table itself ───────────────────────────────────────────────────────────────────────

    @Test
    fun `every row of the table exists exactly once`() {
        assertEquals(34, Rule.entries.size)
        assertEquals((1..34).toList(), Rule.entries.map { it.row }.sorted())
        assertEquals(Rule.entries.size, Rule.entries.map { it.row }.toSet().size)
    }

    @Test
    fun `the gating table matches §C_3 for every engine`() {
        assertEquals(FailPolicy.NONE, failPolicy(Engine.INTERVAL_CIRCUIT))
        assertEquals(FailPolicy.NONE, failPolicy(Engine.AMRAP))
        assertEquals(FailPolicy.NONE, failPolicy(Engine.FOR_TIME))
        assertEquals(FailPolicy.NONE, failPolicy(Engine.FOR_TIME_WITH_REST))
        assertEquals(FailPolicy.MARK_MISSED, failPolicy(Engine.EMOM))
        assertEquals(FailPolicy.END_SESSION, failPolicy(Engine.EMOM_ASCENDING))
        assertEquals(FailPolicy.NONE, failPolicy(Engine.FIXED_SETS))

        assertTrue(autoAdvanceApplies(Engine.AMRAP))
        assertTrue(autoAdvanceApplies(Engine.FOR_TIME))
        assertTrue(autoAdvanceApplies(Engine.FOR_TIME_WITH_REST))
        assertTrue(autoAdvanceApplies(Engine.FIXED_SETS))

        assertTrue(canSkip(RestKind.STATION) && canSkip(RestKind.ROUND))
        assertFalse(canSkip(RestKind.MANDATED) || canSkip(RestKind.EMOM_REMAINDER))
        assertTrue(canExtend(RestKind.STATION) && canExtend(RestKind.ROUND))
        assertFalse(canExtend(RestKind.MANDATED) || canExtend(RestKind.EMOM_REMAINDER))
    }

    @Test
    fun `the auto-advance upgrade only ever touches a manual rep slide`() {
        val work = warm.segments[1]
        val slide = amrap.segments[0]

        assertEquals(Gate.AUTO, effectiveGate(work, Engine.INTERVAL_CIRCUIT, autoAdvance = true))
        assertEquals(Gate.MANUAL, effectiveGate(slide, Engine.AMRAP, autoAdvance = false))
        assertEquals(Gate.AUTO, effectiveGate(slide, Engine.AMRAP, autoAdvance = true))
        assertEquals(
            Gate.AUTO_AT_CAP,
            effectiveGate(emom(rounds = 2).segments[0], Engine.EMOM, autoAdvance = true),
        )
    }

    @Test
    fun `a finished session ignores the controls it no longer has`() {
        val done = compile(
            snapshot(Engine.INTERVAL_CIRCUIT, listOf(station(0, "a", seconds = 30)), prepareSeconds = 0),
            ScalingTier.RX,
            circuitLib,
        ).close(0, 30_000)

        assertNull(step(ctx(done, 30_000), SessionEvent.PauseRequested))
        assertNull(step(ctx(done, 30_000), SessionEvent.SkipForward))
        assertNotNull(step(ctx(done, 30_000), SessionEvent.RecordClosed))
    }
}
