package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.Resumability
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.SegmentResultDraft
import io.eddiegulay.tempo.gym.cue.Cue

/*
 * `03-player.md` §C — the state machine, as a pure function.
 *
 * §C.1 is a thirty-four row table, and a table that lives only in a ViewModel is a table nobody can
 * test. [step] is the whole of it: context plus event in, one [SessionTransition] out, no clock, no
 * coroutine, no database. The ViewModel's job shrinks to "hold the context, dispatch the event, apply
 * the effects" — and every row of the table becomes a JUnit assertion instead of a manual pass with a
 * stopwatch.
 *
 * *Rejected* — folding the machine into the timeline. The timeline answers "where are we"; the
 * machine answers "what happens next", and those change for different reasons: §B is fixed by the
 * seven protocols, §C by the seven screens.
 *
 * *Rejected* — a `phase` field carried in the context. The phase is derived from
 * [Timeline.stateAt], always, live and after process death alike (§E.2). A cached phase is a second
 * opinion, and the recovery path is where the two would disagree.
 *
 * **Effects are returned, never performed.** Each one names a write the caller owes the store —
 * `03-player.md` §E.1's persistence schedule, in the order it must happen — so a transition can be
 * asserted on without a database and a database write can never be forgotten by a screen.
 */

/** The overlays that sit *over* a derived phase rather than replacing it. */
enum class Overlay { NONE, PAUSED, QUIT_SHEET }

/** Why the session ended, which is what `GYM.SESSION.COMPLETE` branches its chip and hero on. */
enum class CompletionReason {
    LAST_SEGMENT,
    PARTIAL_SAVE,
    EMOM_FAIL_OUT,
    AMRAP_CAP,
    RECONCILED_PAST_END,
}

/** Where the transition leaves the user. */
sealed interface Destination {
    /** Inside the player, on the phase [Timeline.stateAt] derives. */
    data class Live(val phase: Phase) : Destination
    data object Paused : Destination
    data object QuitSheet : Destination
    data class Complete(val reason: CompletionReason) : Destination
    data object Home : Destination
    data object RoutineDetail : Destination
    data object ResumePrompt : Destination
}

/**
 * A write the caller owes the store, or a device the caller owes an instruction.
 *
 * They are ordered and must be applied in order: `03-player.md` §E.1 pairs every result with a clock
 * checkpoint **in one transaction**, and a [Finish] that landed before its [Write] would record a
 * session missing its last segment.
 */
sealed interface SessionEffect {
    data class Write(val draft: SegmentResultDraft) : SessionEffect
    /** ◁◁ steps back over a segment whose result must go, or the record double-counts it. */
    data class DeleteResult(val ordinal: Int) : SessionEffect
    data object Checkpoint : SessionEffect
    data class Finish(val complete: Boolean) : SessionEffect
    data object Discard : SessionEffect
    data object InsertSession : SessionEffect
    data object AnchorClock : SessionEffect
    data object PauseClock : SessionEffect
    data object ResumeClock : SessionEffect

    /**
     * Start the session clock [delayMs] from now rather than now — §C.1 rows 19 and 30's 3s 支度.
     *
     * The countdown back in runs on the **wall**, not on session-elapsed. A plain [ResumeClock] here
     * charges those three seconds to a segment the user has not restarted yet, so a station they were
     * twenty seconds into is recorded as twenty-three seconds of work they did not do.
     */
    data class ResumeClockAfter(val delayMs: Long) : SessionEffect
    data class Fire(val cue: Cue) : SessionEffect
    /** §D.7: cancel pending posts. Every skip and every pause disarms, or a stale 3-2-1 fires alone. */
    data object DisarmCues : SessionEffect
}

/**
 * One row of §C.1, named. The [row] is the table's own number so a test can cite it and a reader can
 * find it.
 */
enum class Rule(val row: Int) {
    START(1),
    PREPARE_ELAPSED(2),
    PREPARE_SKIPPED(3),
    WORK_EXPIRED(4),
    WORK_SKIPPED(5),
    REPS_DONE(6),
    REPS_ADJUSTED(7),
    REPS_AUTO_ADVANCED(8),
    REPS_OVERRUN(9),
    EMOM_MISSED(10),
    EMOM_FAILED_OUT(11),
    AMRAP_CAPPED(12),
    AMRAP_EXTENDED(13),
    REST_EXPIRED(14),
    REST_SKIPPED(15),
    REST_EXTENDED(16),
    PAUSED(17),
    RESUMED(18),
    RESUMED_WITH_PREPARE(19),
    QUIT_REQUESTED(20),
    QUIT_DISMISSED(21),
    SAVED_PARTIAL(22),
    DISCARDED(23),
    BACK_RESTART(24),
    BACK_PREVIOUS(25),
    LAST_SEGMENT_CLOSED(26),
    RECONCILED(27),
    STALLED(28),
    COLD_START_OPEN_SESSION(29),
    PROMPT_RESUMED(30),
    PROMPT_RECORDED(31),
    PROMPT_DISCARDED(32),
    RECORD_CLOSED(33),
    RECORD_REPEATED(34),
}

/** What the user, the clock, or the system did. */
sealed interface SessionEvent {
    /** 始める, after the routine compiled and before anything was written. */
    data object Started : SessionEvent
    /**
     * The 50ms tick re-read [Timeline.stateAt]. It never advances anything itself: this event asks
     * the machine whether a boundary has passed, and gets null when none has.
     *
     * Dispatch it **once per tick**, not in a loop until null. [Rule.REPS_OVERRUN] is a deliberate
     * self-loop — an overrunning rep slide answers "still here" every 50ms forever — so a loop would
     * not terminate. Several boundaries passing at once is what [SessionEvent.Foregrounded] is for.
     */
    data object Elapsed : SessionEvent
    data object SkipForward : SessionEvent
    data class BackTapped(val action: BackAction) : SessionEvent
    /**
     * 済, or the rep wheel's 記録する when [adjusted].
     *
     * [ordinal] is the segment the button was rendered for. Pass it: §A REPS edge case 6 needs a
     * duplicate close to be a no-op, and a 300ms debounce alone cannot do that — the second tap of a
     * double tap lands on the *next* segment's UI, so an unkeyed close would close the station the
     * user has not started yet. Null skips the check, for callers that have no ordinal to hand.
     */
    data class RepsCompleted(
        val actualReps: Int?,
        val adjusted: Boolean = false,
        val ordinal: Int? = null,
    ) : SessionEvent
    data object ExtendRest : SessionEvent
    data object PauseRequested : SessionEvent
    data object ResumeRequested : SessionEvent
    data object QuitRequested : SessionEvent
    /** つづける, back, or the scrim — all the same thing, and none of them a discard. */
    data object QuitDismissed : SessionEvent
    data object SavePartial : SessionEvent
    /** 記録せずに終える, already armed by its second tap. */
    data object DiscardConfirmed : SessionEvent
    /** `ON_START`: the clock ran while the app was away. */
    data object Foregrounded : SessionEvent
    /** The 30-minute guard on an open segment. */
    data object Stalled : SessionEvent
    data class RoundsExhausted(
        val routine: RoutineSnapshot,
        val lib: Map<String, Exercise>,
    ) : SessionEvent
    data object ColdStartFoundSession : SessionEvent
    data class PromptResumed(val resumability: Resumability) : SessionEvent
    data object PromptRecorded : SessionEvent
    data object PromptDiscarded : SessionEvent
    data object RecordClosed : SessionEvent
    data object RecordRepeated : SessionEvent
}

/**
 * Everything [step] needs that is not the timeline.
 *
 * [elapsedMs] is session-elapsed, pauses already subtracted — the same number the render loop feeds
 * [Timeline.stateAt]. [nowWallMs] rides along only so a [SegmentResultDraft] can carry
 * `closedAtWallMs` without this file importing a clock.
 */
data class SessionContext(
    val timeline: Timeline,
    val elapsedMs: Long,
    val nowWallMs: Long = 0L,
    val overlay: Overlay = Overlay.NONE,
    /** 目安で自動的に進む. Default off (`01-shell.md` §B). */
    val autoAdvance: Boolean = false,
    /** How many results are already persisted — the quit sheet's shape depends on it, not on time. */
    val resultsWritten: Int = 0,
    /** How long the session has been paused, for the >60s 3s-prepare rule. */
    val pausedForMs: Long = 0L,
    /**
     * The quit sheet is open **over a pause** — §C.1 row 20's "any live / PAUSED".
     *
     * [Overlay] is one slot, so `QUIT_SHEET` overwrites `PAUSED` and the fact that the clock was
     * already stopped is otherwise lost. Row 21 returns to the *originating* phase, and §A PAUSED is
     * explicit that "a back press must never restart a timer the user cannot see" — without this
     * flag, ┃┃ → ✕ → back resumes the clock the user deliberately stopped.
     *
     * The caller never computes it: [step] stamps it onto every transition that leaves the sheet
     * open, and the caller copies the transition's value back into the next context.
     */
    val pausedBeforeSheet: Boolean = false,
)

/**
 * The result of one row of §C.1.
 *
 * [seekToMs] is non-null only when the *clock* must move — ◁ restarting a segment, ▷ jumping the 支度
 * countdown. Forward skips over a segment do **not** seek: closing a segment at the elapsed instant
 * re-flows the next one to start exactly now, which is the same outcome with one source of truth
 * instead of two.
 */
data class SessionTransition(
    val rule: Rule,
    val destination: Destination,
    val timeline: Timeline,
    val overlay: Overlay,
    val seekToMs: Long? = null,
    val effects: List<SessionEffect> = emptyList(),
    /**
     * Non-null on §C.1 rows 19 and 30: render 支度 for this long **in front of** [destination], which
     * is already the phase the countdown leads back into.
     *
     * *Rejected* — `Destination.Live(Phase.PREPARE)`. The timeline is the sole source of truth for
     * phase (§E.2), and there is no PREPARE segment under the frontier at this point, so
     * [Timeline.stateAt] goes on reporting WORK or REPS and the shell would have to invent the phase
     * the destination named.
     *
     * *Rejected* — prepending a real 3s PREPARE **segment** so `stateAt` derives it like every other
     * phase, which is the structurally prettier fix. `Segment.ordinal` is the identity every
     * persisted `segment_result` row carries: inserting a cell mid-list renumbers every segment after
     * it, so [SessionEffect.DeleteResult] would delete the wrong row, §E.2's replay-by-ordinal would
     * land stored results on the wrong stations, and [Timeline.hash] would change mid-session so
     * `compiled_hash` reports drift on the next recovery. A three-second countdown is not worth
     * making the ordinal mean two things.
     */
    val prepareForMs: Long? = null,
    /** See [SessionContext.pausedBeforeSheet]; stamped by [step], carried by the caller. */
    val pausedBeforeSheet: Boolean = false,
)

/** ＋二十秒 — §C.1 row 16. */
const val REST_EXTENSION_MS = 20_000L

/** `03-player.md` §A PAUSED: over a minute away and you get a countdown back in, not a cold start. */
const val LONG_PAUSE_MS = 60_000L

/**
 * The countdown back into a session already under way — §A PREPARE's "the resume prompt's 続ける
 * (3s, not 5s)" and §A PAUSED's `PausedLong`. Three, never [Timeline.prepareMs]: the user knows where
 * they are, they only need to put the phone down.
 */
const val RESUME_PREPARE_MS = 3_000L

/** §C.3's fail policy for an engine whose window closed over unfinished work. */
enum class FailPolicy { NONE, MARK_MISSED, END_SESSION }

fun failPolicy(engine: Engine): FailPolicy = when (engine) {
    Engine.EMOM -> FailPolicy.MARK_MISSED
    // The protocol's terminating condition, not an error: reaching failure IS the EMOM ladder
    // succeeding, which is why COMPLETE treats a fail-out as a complete session.
    Engine.EMOM_ASCENDING -> FailPolicy.END_SESSION
    else -> FailPolicy.NONE
}

/**
 * §C.3: 目安で自動的に進む upgrades MANUAL to AUTO on REPS segments for the four self-paced engines
 * only.
 *
 * Never for EMOM — the grid already gates, and a preference that closed a cell early would hand the
 * remaining seconds to a rest the protocol says is work. Meaningless for INTERVAL_CIRCUIT, whose
 * segments are AUTO already.
 */
fun autoAdvanceApplies(engine: Engine): Boolean = when (engine) {
    Engine.AMRAP, Engine.FOR_TIME, Engine.FOR_TIME_WITH_REST, Engine.FIXED_SETS -> true
    Engine.INTERVAL_CIRCUIT, Engine.EMOM, Engine.EMOM_ASCENDING -> false
}

/**
 * The gate actually in force, which is the compiled gate plus one run-time preference.
 *
 * The upgrade is applied here rather than in [compile] on purpose: a preference inside the compiler
 * would make the same routine compile two different ways and `session.compiled_hash` would report
 * drift the first time the user changed a switch mid-session.
 */
fun effectiveGate(segment: Segment, engine: Engine, autoAdvance: Boolean): Gate =
    if (segment.gate == Gate.MANUAL && segment.phase == Phase.REPS &&
        autoAdvance && autoAdvanceApplies(engine)
    ) {
        Gate.AUTO
    } else {
        segment.gate
    }

/**
 * Whether ▷ / とばす is offered — `03-player.md` §A REST states 4 and 5.
 *
 * A mandated rest is part of the prescription and a skipped one makes the record incomparable; an
 * EMOM remainder is not the user's to end. Both must be rendered **visibly disabled** rather than
 * silently inert. Null is not a rest at all, and the structural skip over work is always allowed.
 */
fun canSkip(kind: RestKind?): Boolean = when (kind) {
    RestKind.MANDATED, RestKind.EMOM_REMAINDER -> false
    RestKind.STATION, RestKind.ROUND, null -> true
}

/** ＋二十秒, on the same two exclusions and for the same reasons as [canSkip]. */
fun canExtend(kind: RestKind?): Boolean = when (kind) {
    RestKind.MANDATED, RestKind.EMOM_REMAINDER -> false
    RestKind.STATION, RestKind.ROUND -> true
    null -> false // there is nothing to extend on a work segment
}

/**
 * AMRAP: the frontier has reached the last materialised round, so more must be laid before the user
 * can see the end of a session that has not ended (§A REPS edge case 7).
 */
fun Timeline.needsAnotherRound(ordinal: Int): Boolean =
    extensible && segments.getOrNull(ordinal)?.round == totalRounds

/**
 * One row of §C.1, plus the two overlay facts no single row can be trusted to remember.
 *
 * [Overlay] is one slot and [Destination] is another, so every row is free to set them
 * independently — and rows that only meant to advance the *session* (▷ and ◁ while paused, row 13's
 * AMRAP growth) kept returning [Destination.Live] under `Overlay.PAUSED`. A shell keyed on the
 * destination then un-paused on a skip, which §A PAUSED forbids in one line: **"skip-while-paused
 * stays paused."** Normalising here rather than at four call sites is what makes that a property of
 * the machine instead of a thing each row has to get right.
 *
 * The same argument covers [SessionContext.pausedBeforeSheet]: the quit sheet overwrites the pause it
 * was opened from, so the fact is stamped once, on every transition that leaves the sheet open.
 *
 * @return null when no row matches — a guard failed, or the event does not apply in this overlay.
 *   Null is a **no-op**, never an error: a ▷ on a mandated rest and a 済 that arrived after the
 *   segment already closed are both ordinary, and both must leave the session exactly as it was.
 */
fun step(ctx: SessionContext, event: SessionEvent): SessionTransition? {
    val t = transitionFor(ctx, event) ?: return null
    val paused = if (t.overlay == Overlay.PAUSED && t.destination is Destination.Live) {
        // The phase is not lost: PAUSED renders the segment stateAt is already reporting.
        t.copy(destination = Destination.Paused)
    } else {
        t
    }
    return if (paused.overlay == Overlay.QUIT_SHEET) {
        paused.copy(
            pausedBeforeSheet = if (ctx.overlay == Overlay.QUIT_SHEET) {
                ctx.pausedBeforeSheet
            } else {
                ctx.overlay == Overlay.PAUSED
            },
        )
    } else {
        paused
    }
}

private fun transitionFor(ctx: SessionContext, event: SessionEvent): SessionTransition? {
    val tl = ctx.timeline
    val state = tl.stateAt(ctx.elapsedMs)
    val seg = state.segment

    return when (event) {
        // ── 1. Entry ────────────────────────────────────────────────────────────────────────────
        is SessionEvent.Started -> SessionTransition(
            rule = Rule.START,
            destination = Destination.Live(tl.segments.first().phase),
            timeline = tl,
            overlay = Overlay.NONE,
            effects = listOf(SessionEffect.InsertSession, SessionEffect.AnchorClock),
        )

        // ── 2, 4, 8, 9, 10, 11, 12, 14, 26. The clock reached a boundary ────────────────────────
        is SessionEvent.Elapsed -> onElapsed(ctx, state)

        // ── 3, 5, 15. ▷ ─────────────────────────────────────────────────────────────────────────
        is SessionEvent.SkipForward -> onSkipForward(ctx, state)

        // ── 6, 7. 済 ────────────────────────────────────────────────────────────────────────────
        is SessionEvent.RepsCompleted -> {
            if (ctx.overlay != Overlay.NONE) return null
            if (seg.phase != Phase.REPS || !seg.open || seg.closed) return null
            if (event.ordinal != null && event.ordinal != seg.ordinal) return null
            // §A REPS edge case 4: "`actualReps == 0` via the wheel is treated as `skipped = true`,
            // rendered とばした." A set you wound down to zero is one you did not do, and recording
            // it as a completed set with no reps makes COMPLETE's 内訳 read 済 ・ 〇回 and lets
            // volumeUnits score a prescription that was never attempted.
            //
            // The null rep count is structural, not cosmetic: `02-data.md`'s schema carries
            // CHECK (skipped = 0 OR actual_reps IS NULL), so `skipped = 1, actual_reps = 0` is a row
            // the store rejects outright.
            val abandoned = event.actualReps == 0
            val reps = if (abandoned) null else event.actualReps
            val next = tl.close(seg.ordinal, state.elapsedInSegmentMs, reps, skipped = abandoned)
            closingTransition(
                ctx = ctx,
                rule = if (event.adjusted) Rule.REPS_ADJUSTED else Rule.REPS_DONE,
                closed = seg,
                next = next,
                actualMs = state.elapsedInSegmentMs,
                actualReps = reps,
                skipped = abandoned,
                cue = Cue.REP_DONE,
            )
        }

        // ── 13. AMRAP ran out of materialised rounds ────────────────────────────────────────────
        is SessionEvent.RoundsExhausted -> {
            if (!tl.extensible) return null
            val grown = tl.extendOneRound(event.routine, event.lib)
            SessionTransition(
                rule = Rule.AMRAP_EXTENDED,
                destination = Destination.Live(grown.stateAt(ctx.elapsedMs).segment.phase),
                timeline = grown,
                overlay = ctx.overlay,
            )
        }

        // ── 16. ＋二十秒 ────────────────────────────────────────────────────────────────────────
        is SessionEvent.ExtendRest -> {
            if (ctx.overlay != Overlay.NONE) return null
            // §A REST edge case 1: the frontier is re-read here, *after* any advance. A tap that
            // landed on a segment which is no longer a rest is dropped, never rewound into history.
            if (seg.phase != Phase.REST || !canExtend(seg.restKind)) return null
            SessionTransition(
                rule = Rule.REST_EXTENDED,
                destination = Destination.Live(Phase.REST),
                timeline = tl.extend(seg.ordinal, REST_EXTENSION_MS),
                overlay = Overlay.NONE,
                effects = listOf(SessionEffect.Fire(Cue.EXTEND_ACK), SessionEffect.Checkpoint),
            )
        }

        // ── 24, 25. ◁ ───────────────────────────────────────────────────────────────────────────
        is SessionEvent.BackTapped -> onBackTapped(ctx, state, event.action)

        // ── 17. ┃┃ ─────────────────────────────────────────────────────────────────────────────
        is SessionEvent.PauseRequested -> {
            if (ctx.overlay != Overlay.NONE || state.finished) return null
            SessionTransition(
                rule = Rule.PAUSED,
                destination = Destination.Paused,
                timeline = tl,
                overlay = Overlay.PAUSED,
                effects = listOf(
                    SessionEffect.PauseClock,
                    SessionEffect.DisarmCues,
                    SessionEffect.Checkpoint,
                ),
            )
        }

        // ── 18, 19. 続ける ──────────────────────────────────────────────────────────────────────
        is SessionEvent.ResumeRequested -> {
            if (ctx.overlay != Overlay.PAUSED) return null
            val long = ctx.pausedForMs > LONG_PAUSE_MS
            SessionTransition(
                rule = if (long) Rule.RESUMED_WITH_PREPARE else Rule.RESUMED,
                // Both rows land back in the phase the pause interrupted. Row 19 puts a countdown in
                // front of it (see [SessionTransition.prepareForMs]) rather than naming PREPARE as a
                // destination the timeline cannot corroborate.
                destination = Destination.Live(seg.phase),
                timeline = tl,
                overlay = Overlay.NONE,
                prepareForMs = if (long) RESUME_PREPARE_MS else null,
                effects = listOf(
                    if (long) SessionEffect.ResumeClockAfter(RESUME_PREPARE_MS) else SessionEffect.ResumeClock,
                    SessionEffect.Checkpoint,
                ),
            )
        }

        // ── 20. ✕ / back ───────────────────────────────────────────────────────────────────────
        is SessionEvent.QuitRequested -> {
            if (ctx.overlay == Overlay.QUIT_SHEET) return null
            // The clock pauses the instant the sheet opens. A sheet that lets the timer run while the
            // user decides produces a record that includes the deciding.
            //
            // Unless it is already stopped: row 20 is reachable from PAUSED, and a second PauseClock
            // on a paused clock overwrites the pause anchor §A PAUSED's "data out" depends on.
            val alreadyPaused = ctx.overlay == Overlay.PAUSED
            SessionTransition(
                rule = Rule.QUIT_REQUESTED,
                destination = Destination.QuitSheet,
                timeline = tl,
                overlay = Overlay.QUIT_SHEET,
                effects = buildList {
                    if (!alreadyPaused) add(SessionEffect.PauseClock)
                    add(SessionEffect.DisarmCues)
                    add(SessionEffect.Checkpoint)
                },
            )
        }

        // ── 21. つづける / back / scrim ─────────────────────────────────────────────────────────
        is SessionEvent.QuitDismissed -> {
            if (ctx.overlay != Overlay.QUIT_SHEET) return null
            // Row 21 returns to the **originating** phase, and for a sheet opened from ┃┃ that phase
            // is the pause. §A PAUSED: "Back does not resume; a back press must never restart a timer
            // the user cannot see" — so ┃┃ → ✕ → back leaves the session exactly as ┃┃ left it, with
            // no ResumeClock at all.
            if (ctx.pausedBeforeSheet) {
                SessionTransition(
                    rule = Rule.QUIT_DISMISSED,
                    destination = Destination.Paused,
                    timeline = tl,
                    overlay = Overlay.PAUSED,
                )
            } else {
                SessionTransition(
                    rule = Rule.QUIT_DISMISSED,
                    destination = Destination.Live(seg.phase),
                    timeline = tl,
                    overlay = Overlay.NONE,
                    effects = listOf(SessionEffect.ResumeClock),
                )
            }
        }

        // ── 22. ここまでを記録する ──────────────────────────────────────────────────────────────
        is SessionEvent.SavePartial -> {
            if (ctx.overlay != Overlay.QUIT_SHEET || ctx.resultsWritten < 1) return null
            // The segment in progress is closed as skipped with its partial duration. Its rep count
            // stays NULL: guessing one from the pacer would fabricate data.
            val next = tl.close(seg.ordinal, state.elapsedInSegmentMs, actualReps = null, skipped = true)
            SessionTransition(
                rule = Rule.SAVED_PARTIAL,
                destination = Destination.Complete(CompletionReason.PARTIAL_SAVE),
                timeline = next,
                overlay = Overlay.NONE,
                effects = listOf(
                    SessionEffect.Write(
                        draftOf(seg, state.elapsedInSegmentMs, null, true, ctx),
                    ),
                    SessionEffect.Checkpoint,
                    SessionEffect.Finish(complete = false),
                ),
            )
        }

        // ── 23. 記録せずに終える ×2 ────────────────────────────────────────────────────────────
        is SessionEvent.DiscardConfirmed -> {
            if (ctx.overlay != Overlay.QUIT_SHEET) return null
            SessionTransition(
                rule = Rule.DISCARDED,
                destination = Destination.Home,
                timeline = tl,
                overlay = Overlay.NONE,
                effects = listOf(SessionEffect.DisarmCues, SessionEffect.Discard),
            )
        }

        // ── 27. ON_START, the clock ran while we were away ─────────────────────────────────────
        is SessionEvent.Foregrounded -> {
            // From zero, not from where the player thought it was: the segments owed a write are the
            // ones *behind* the landed ordinal, and closed ones cost a comparison each to walk over.
            val r = tl.reconcile(fromOrdinal = 0, elapsedMs = ctx.elapsedMs, nowWallMs = ctx.nowWallMs)
            // Nothing owed is nothing to do. ON_START fires twice for one absence often enough that
            // "the walk reached the end" cannot be the trigger: on a session that already ran past
            // its end, the second pass has no writes and must not hand the caller a second
            // Finish(complete = true) for a session that was finished the first time.
            if (r.backFilled.isEmpty()) return null
            SessionTransition(
                rule = Rule.RECONCILED,
                destination = if (r.autoCompleted) {
                    Destination.Complete(CompletionReason.RECONCILED_PAST_END)
                } else {
                    Destination.Live(r.timeline.stateAt(ctx.elapsedMs).segment.phase)
                },
                timeline = r.timeline,
                overlay = ctx.overlay,
                effects = buildList {
                    r.backFilled.forEach { add(SessionEffect.Write(it)) }
                    add(SessionEffect.Checkpoint)
                    if (r.autoCompleted) add(SessionEffect.Finish(complete = true))
                },
            )
        }

        // ── 28. the 30-minute guard ────────────────────────────────────────────────────────────
        is SessionEvent.Stalled -> {
            if (ctx.overlay != Overlay.NONE) return null
            // No auto-save. The user decides what a forgotten phone was worth.
            SessionTransition(
                rule = Rule.STALLED,
                destination = Destination.Paused,
                timeline = tl,
                overlay = Overlay.PAUSED,
                effects = listOf(
                    SessionEffect.PauseClock,
                    SessionEffect.DisarmCues,
                    SessionEffect.Checkpoint,
                ),
            )
        }

        // ── 29–32. The resume prompt ───────────────────────────────────────────────────────────
        is SessionEvent.ColdStartFoundSession -> SessionTransition(
            rule = Rule.COLD_START_OPEN_SESSION,
            destination = Destination.ResumePrompt,
            timeline = tl,
            overlay = Overlay.NONE,
        )

        is SessionEvent.PromptResumed -> {
            // REBOOTED and STALE do not merely disable 続ける, they remove it: resuming across a
            // reboot would fabricate the time the device spent switched off.
            if (event.resumability != Resumability.RESUMABLE) return null
            SessionTransition(
                rule = Rule.PROMPT_RESUMED,
                // §A PREPARE edge case 4: the countdown's hero is "the exercise the session was
                // interrupted *inside*, not the first one" — which is exactly what stateAt reports on
                // the replayed timeline, so the destination is that phase and the 3s rides in front.
                destination = Destination.Live(seg.phase),
                timeline = tl,
                overlay = Overlay.NONE,
                prepareForMs = RESUME_PREPARE_MS,
                // Anchored, then held: the countdown must not be charged to the segment the session
                // was interrupted inside, for the same reason it must not be on row 19.
                effects = listOf(
                    SessionEffect.AnchorClock,
                    SessionEffect.ResumeClockAfter(RESUME_PREPARE_MS),
                ),
            )
        }

        is SessionEvent.PromptRecorded -> {
            if (ctx.resultsWritten < 1) return null
            SessionTransition(
                rule = Rule.PROMPT_RECORDED,
                destination = Destination.Complete(CompletionReason.PARTIAL_SAVE),
                timeline = tl,
                overlay = Overlay.NONE,
                effects = listOf(SessionEffect.Finish(complete = false)),
            )
        }

        is SessionEvent.PromptDiscarded -> SessionTransition(
            rule = Rule.PROMPT_DISCARDED,
            destination = Destination.Home,
            timeline = tl,
            overlay = Overlay.NONE,
            effects = listOf(SessionEffect.Discard),
        )

        // ── 33, 34. Leaving 記録 ───────────────────────────────────────────────────────────────
        is SessionEvent.RecordClosed -> SessionTransition(
            rule = Rule.RECORD_CLOSED,
            destination = Destination.Home,
            timeline = tl,
            overlay = Overlay.NONE,
        )

        is SessionEvent.RecordRepeated -> SessionTransition(
            rule = Rule.RECORD_REPEATED,
            destination = Destination.RoutineDetail,
            timeline = tl,
            overlay = Overlay.NONE,
        )
    }
}

// ─── The three compound handlers ────────────────────────────────────────────────────────────────

private fun onElapsed(ctx: SessionContext, state: PlayerState): SessionTransition? {
    if (ctx.overlay != Overlay.NONE) return null
    val tl = ctx.timeline
    val seg = state.segment

    // An AMRAP's cap ends the session mid-segment, wherever it is. Reaching the cap is the AMRAP
    // *completing* — the score is the rounds, and the clock was always going to end it.
    //
    // The interrupted station closes as a **partial**, `skipped = true` with no rep count: it is the
    // same shape §A QUIT_SHEET edge case 2 gives a mid-set REPS segment, and it is the only honest
    // one. `skipped = false, actualReps = null` would render a station the user was halfway through
    // as a completed set of unknown reps. §C.1 row 12 says the partial round's reps are "captured via
    // the wheel" (§C.3): when that prompt is built it rewrites **this row and only this row**, to
    // supply `actualReps` — the flag stays true until the user says what they got.
    if (tl.engine == Engine.AMRAP && state.capExceeded) {
        val next = tl.close(seg.ordinal, state.elapsedInSegmentMs, actualReps = null, skipped = true)
        return SessionTransition(
            rule = Rule.AMRAP_CAPPED,
            destination = Destination.Complete(CompletionReason.AMRAP_CAP),
            timeline = next,
            overlay = Overlay.NONE,
            effects = listOf(
                SessionEffect.Write(draftOf(seg, state.elapsedInSegmentMs, null, true, ctx)),
                SessionEffect.Checkpoint,
                SessionEffect.Fire(Cue.AMRAP_CAP),
                SessionEffect.Finish(complete = true),
            ),
        )
    }

    // An EMOM window that closed over unfinished work. The cell is recorded as missed either way;
    // the engine decides whether the session survives it.
    if (state.capExceeded && failPolicy(tl.engine) != FailPolicy.NONE) {
        val actual = state.elapsedInSegmentMs.coerceAtMost(seg.effectiveMs)
        val next = tl.close(seg.ordinal, actual, actualReps = null, skipped = true)
        val ended = failPolicy(tl.engine) == FailPolicy.END_SESSION
        return SessionTransition(
            rule = if (ended) Rule.EMOM_FAILED_OUT else Rule.EMOM_MISSED,
            destination = if (ended) {
                Destination.Complete(CompletionReason.EMOM_FAIL_OUT)
            } else {
                Destination.Live(next.stateAt(ctx.elapsedMs).segment.phase)
            },
            timeline = next,
            overlay = Overlay.NONE,
            effects = buildList {
                add(SessionEffect.Write(draftOf(seg, actual, null, true, ctx)))
                add(SessionEffect.Checkpoint)
                add(SessionEffect.Fire(Cue.EMOM_FAIL))
                // Reaching failure is the protocol succeeding, so the session is complete, not partial.
                if (ended) add(SessionEffect.Finish(complete = true))
            },
        )
    }

    // The segment that is *owed* an ending, which is not always the one `stateAt` is reporting: at the
    // instant a boundary passes, the state function has already moved to the segment that is
    // starting, and the one behind it still has to be closed and written. Taking the first unclosed
    // segment whose end has passed is what makes the tick's answer independent of *when* the tick
    // arrived — 50ms late, or after a doze.
    val due = tl.segments.firstOrNull { !it.closed && it.endMs <= ctx.elapsedMs } ?: return null

    // A minute used to its last second leaves a remainder with no span at all. §B.6 invariant 4 keeps
    // it off the screen and [Segment.vestigial] names it; writing it anyway puts one 0ms
    // EMOM_REMAINDER row per minute into the store, which COMPLETE's 内訳 then renders as a 残り line
    // reading 0:00 — a row describing a cell the player never showed. It is still closed on the
    // returned timeline, or this scan re-selects it on every tick forever; it is simply not written,
    // which is the treatment 支度 already gets for the same reason.
    if (due.vestigial && due.restKind == RestKind.EMOM_REMAINDER) {
        val consumed = tl.markClosed(due.ordinal, due.effectiveMs, due.addedMs)
        return SessionTransition(
            rule = Rule.REST_EXPIRED,
            destination = Destination.Live(consumed.stateAt(ctx.elapsedMs).segment.phase),
            timeline = consumed,
            overlay = Overlay.NONE,
        )
    }

    return when (due.phase) {
        // 支度 is not a segment result. It is timeline offset [0, prepareMs), excluded from activeMs,
        // so it is consumed on the timeline and nothing at all is written.
        Phase.PREPARE -> SessionTransition(
            rule = Rule.PREPARE_ELAPSED,
            destination = Destination.Live(
                tl.stateAt(maxOf(ctx.elapsedMs, due.endMs)).segment.phase,
            ),
            timeline = tl.markClosed(due.ordinal, due.effectiveMs, due.addedMs),
            overlay = Overlay.NONE,
            effects = listOf(SessionEffect.Fire(Cue.SESSION_START)),
        )

        Phase.REPS -> {
            if (effectiveGate(due, tl.engine, ctx.autoAdvance) != Gate.AUTO) {
                // Overrun. The ring holds empty, the 目安 line counts up, and **nothing advances** —
                // this is a row of the table precisely so that "no transition" is a decision rather
                // than a gap.
                return SessionTransition(
                    rule = Rule.REPS_OVERRUN,
                    destination = Destination.Live(Phase.REPS),
                    timeline = tl,
                    overlay = Overlay.NONE,
                )
            }
            val actual = due.effectiveMs
            val next = tl.close(due.ordinal, actual, due.prescribedReps, skipped = false)
            closingTransition(
                ctx, Rule.REPS_AUTO_ADVANCED, due, next,
                actual, due.prescribedReps, false, Cue.INTERVAL_END,
            )
        }

        Phase.WORK, Phase.REST -> {
            // Closed at its planned length, not at the tick's `elapsedInSegment`. A 50ms-late tick
            // must not lengthen the segment, or the timeline drifts by the render loop's jitter once
            // per station.
            val actual = due.effectiveMs
            val next = tl.close(due.ordinal, actual, actualReps = null, skipped = false)
            closingTransition(
                ctx,
                if (due.phase == Phase.WORK) Rule.WORK_EXPIRED else Rule.REST_EXPIRED,
                due, next, actual, null, false, Cue.INTERVAL_END,
            )
        }

        Phase.COMPLETE -> null
    }
}

private fun onSkipForward(ctx: SessionContext, state: PlayerState): SessionTransition? {
    val tl = ctx.timeline
    val seg = state.segment
    if (ctx.overlay == Overlay.QUIT_SHEET) return null
    if (state.finished) return null

    if (seg.phase == Phase.PREPARE) {
        return SessionTransition(
            rule = Rule.PREPARE_SKIPPED,
            destination = Destination.Live(tl.segments[tl.firstRealSegment].phase),
            timeline = tl,
            overlay = ctx.overlay,
            seekToMs = seg.endMs,
            effects = listOf(SessionEffect.DisarmCues),
        )
    }
    // The one place the player refuses a skip. It must read as disabled, not as an unresponsive tap.
    if (seg.phase == Phase.REST && !canSkip(seg.restKind)) return null

    val actual = state.elapsedInSegmentMs
    val next = tl.close(seg.ordinal, actual, actualReps = null, skipped = true)
    return closingTransition(
        ctx = ctx,
        rule = if (seg.phase == Phase.REST) Rule.REST_SKIPPED else Rule.WORK_SKIPPED,
        closed = seg,
        next = next,
        actualMs = actual,
        actualReps = null,
        skipped = true,
        cue = null,
        overlay = ctx.overlay,
        // §D.7's "skip fwd/back" row and §A WORK edge case 8: a ▷ in the last three seconds must
        // **cancel** the interval-end cue, not let it fire for a segment that is now history. The
        // PREPARE branch above and ◁ both disarm; this path is the one that did not.
        disarm = true,
    )
}

private fun onBackTapped(
    ctx: SessionContext,
    state: PlayerState,
    action: BackAction,
): SessionTransition? {
    val tl = ctx.timeline
    val seg = state.segment
    if (ctx.overlay == Overlay.QUIT_SHEET) return null

    // §C.4: stepping back from the first real segment degrades to a restart. Never back into 支度 —
    // re-running a countdown the user has already sat through is not what they asked for.
    val canStepBack = action == BackAction.PREVIOUS_SEGMENT && seg.ordinal > tl.firstRealSegment
    if (!canStepBack) {
        return SessionTransition(
            rule = Rule.BACK_RESTART,
            destination = Destination.Live(seg.phase),
            timeline = tl,
            overlay = ctx.overlay,
            seekToMs = seg.startMs,
            effects = listOf(
                SessionEffect.DisarmCues,
                SessionEffect.Fire(Cue.SKIP_BACK_ARMED),
            ),
        )
    }
    val prev = tl.segments[seg.ordinal - 1]
    val reopened = tl.reopen(prev.ordinal)
    return SessionTransition(
        rule = Rule.BACK_PREVIOUS,
        destination = Destination.Live(prev.phase),
        timeline = reopened,
        overlay = ctx.overlay,
        seekToMs = reopened.segments[prev.ordinal].startMs,
        effects = listOf(
            SessionEffect.DisarmCues,
            SessionEffect.DeleteResult(prev.ordinal),
            SessionEffect.Checkpoint,
        ),
    )
}

/**
 * The tail every close shares: write the result, checkpoint the clock in the same transaction, and
 * decide whether that was the last segment.
 *
 * Row 26 is not a separate trigger. Every one of rows 4, 5, 6, 7, 8, 14 and 15 becomes row 26 when
 * the segment it closed was the last one, and folding that in here is what stops six call sites from
 * each having to remember to finish the session.
 *
 * @param disarm the caller is a skip, which owes §D.7 a cue cancellation before anything else.
 */
private fun closingTransition(
    ctx: SessionContext,
    rule: Rule,
    closed: Segment,
    next: Timeline,
    actualMs: Long,
    actualReps: Int?,
    skipped: Boolean,
    cue: Cue?,
    overlay: Overlay = Overlay.NONE,
    disarm: Boolean = false,
): SessionTransition {
    // "The last materialised segment" is not the end of an AMRAP. Its rounds are laid a few at a time
    // and appended on demand (§B.3), so the tail of the list is a compilation detail and the **cap**
    // is the ending. §A REPS edge case 7: "the user must never see 完了 mid-cap." Suppressing it here
    // rather than trusting the caller to fire RoundsExhausted first makes that a guarantee of the
    // machine instead of a convention; the transition still returns a live frame, which is the frame
    // the caller's [Timeline.needsAnotherRound] check runs in.
    val capStillRunning = next.extensible && next.capMs != null &&
        ctx.elapsedMs < next.prepareMs + next.capMs
    val last = closed.ordinal == next.segments.lastIndex && !capStillRunning
    val draft = draftOf(closed, actualMs, actualReps, skipped, ctx)
    return SessionTransition(
        rule = if (last) Rule.LAST_SEGMENT_CLOSED else rule,
        destination = if (last) {
            Destination.Complete(CompletionReason.LAST_SEGMENT)
        } else {
            Destination.Live(next.stateAt(ctx.elapsedMs).segment.phase)
        },
        timeline = next,
        overlay = overlay,
        effects = buildList {
            // §D.7's COMPLETE row cancels pending posts too, and the last segment is where it
            // matters most: there is no next `enterSegment` to cancel them as a side effect, so a
            // 3-2-1 armed for the final station would fire on top of the completion ceremony.
            if (disarm || last) add(SessionEffect.DisarmCues)
            add(SessionEffect.Write(draft))
            add(SessionEffect.Checkpoint)
            if (last) {
                add(SessionEffect.Fire(Cue.SESSION_COMPLETE))
                add(SessionEffect.Finish(complete = true))
            } else {
                cue?.let { add(SessionEffect.Fire(it)) }
            }
        },
    )
}

/**
 * A closed segment as the store wants it (`03-player.md` §A, WORK data out).
 *
 * `plannedMs` and `addedMs` stay separate from `actualMs` because the record has to be able to say
 * that a fifteen-second rest was grown to thirty-five and then taken in full — three different facts
 * that a single duration cannot carry.
 */
internal fun draftOf(
    seg: Segment,
    actualMs: Long,
    actualReps: Int?,
    skipped: Boolean,
    ctx: SessionContext,
): SegmentResultDraft = SegmentResultDraft(
    ordinal = seg.ordinal,
    phase = seg.phase,
    stationIndex = seg.stationIndex,
    exerciseId = seg.exerciseId,
    round = seg.round,
    prescribedReps = seg.prescribedReps,
    actualReps = actualReps,
    plannedMs = seg.plannedMs,
    actualMs = actualMs,
    addedMs = seg.addedMs,
    skipped = skipped,
    closedAtWallMs = ctx.nowWallMs,
    closedAtElapsedMs = ctx.elapsedMs,
)
