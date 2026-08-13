package io.eddiegulay.tempo.ui.gym.session

import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.gym.cue.Cue
import io.eddiegulay.tempo.gym.cue.CueEvent
import io.eddiegulay.tempo.gym.cue.CueSegment
import io.eddiegulay.tempo.gym.session.Destination
import io.eddiegulay.tempo.gym.session.Rule
import io.eddiegulay.tempo.gym.session.SessionEffect
import io.eddiegulay.tempo.gym.session.Timeline
import io.eddiegulay.tempo.i18n.Strings

/*
 * The host's arithmetic — everything it has to be right about that does not need a `Context`.
 *
 * It lives beside the composable rather than inside it for the reason the repo already applies to
 * `groupByApp`, `layoutTategaki` and `keepAwake`: a decision made inside a composable can only be
 * checked by launching an emulator, and every decision below is one whose failure is silent. A cue
 * armed for the wrong segment, a session that never released the screen-on flag, a resumed session
 * whose stored rows landed on the wrong stations — none of them fail a build and none of them look
 * wrong in a screenshot.
 */

/**
 * §A REPS edge case 1: an open segment held this long auto-pauses.
 *
 * Thirty minutes is the spec's own figure and it is chosen against the failure it prevents — a phone
 * put down face-first mid-set producing a nine-hour "session" — not against any belief about how long
 * a set takes. There is no auto-save on the other side of it: §A PAUSED's `Stalled` shows the guard
 * fired and lets the user decide what the time was worth.
 */
const val SESSION_STALL_MS: Long = 30 * 60 * 1000L

/** §A WORK edge case 6: a stutter tap on ▷ must not skip three stations. */
const val SKIP_DEBOUNCE_MS: Long = 250L

/** §A REPS edge case 6: 済's own window, longer because it is pressed with a shaking hand. */
const val DONE_DEBOUNCE_MS: Long = 300L

/** The pure half of §A REPS edge case 1. `openForMs` is time in the segment, not session-elapsed. */
fun isStalled(openForMs: Long): Boolean = openForMs >= SESSION_STALL_MS

/**
 * Whether a tap arrived too soon after the last one to be a second intention.
 *
 * Time is passed in for the same reason [SessionClock] takes it: a 250ms window asserted by sleeping
 * for 250ms is a slow test that fails on a loaded machine.
 */
fun debounced(lastAtMs: Long?, nowMs: Long, windowMs: Long): Boolean =
    lastAtMs != null && nowMs - lastAtMs < windowMs

/**
 * Whether the persisted rows can be replayed onto this freshly compiled timeline — `03-player.md`
 * §E.2's drift check, in the strongest form the shipped store supports.
 *
 * §E.2 specifies `require(tl0.hash() == rec.compiledHash)`. **That comparison is not available**:
 * `GymStore.startSession` writes `compiled_hash = snapshot.structuralHash` — the routine *version's*
 * content address, not [Timeline.hash] — and `ResumableSession` carries no hash field at all, so
 * neither side of the equation reaches this layer. See the unit report; the fix is a store change.
 *
 * What is checked instead is the invariant the replay actually depends on, which the hash was only ever
 * a proxy for: **every stored result must land on a segment that is the same cell it was written from.**
 * `Timeline.replay` is by ordinal, so a timeline that compiled to a different shape would silently
 * close the wrong stations — recording a plank where the user did burpees — and that is precisely the
 * "guessing" §E.2 forbids. Ordinal in range, same phase, same exercise: anything else is drift.
 *
 * Drift is reachable without anyone editing a routine, which is why this is not theatre. The pinned
 * `routine_version` is immutable, but `compile` also reads the exercise catalogue's `secondsPerRep`
 * (a seed bump across an app update moves every estimate) and a `FIXED_SETS` routine's current
 * progression step (which advances when a session finishes). Either can change the cell count between
 * the session starting and the user reopening the app.
 */
fun replayable(timeline: Timeline, results: List<SegmentResult>): Boolean = results.all { result ->
    val segment = timeline.segments.getOrNull(result.ordinal) ?: return@all false
    segment.phase == result.phase && segment.exerciseId == result.exerciseId
}

/**
 * How many rounds are finished, for `checkpoint`'s third argument and 巡 counts.
 *
 * A round counts once **every** effort cell in it is closed; its rests are ignored, because a round
 * whose last station is done is a round the user finished whether or not they took the rest after it.
 * Derived from the timeline rather than counted up as sessions progress, so process death cannot lose
 * it and a ◁◁ that reopens a segment takes it back down again.
 */
fun roundsCompletedOf(timeline: Timeline): Int {
    val efforts = timeline.segments.filter { it.phase == Phase.WORK || it.phase == Phase.REPS }
    if (efforts.isEmpty()) return 0
    return efforts.groupBy { it.round }
        .count { (_, cells) -> cells.all { it.closed } }
}

/**
 * How many stations are **done** — the 八 of 「二十種目中 八」.
 *
 * Skipped cells are excluded deliberately. A station the user pressed ▷ through is recorded and shown
 * as とばした in the breakdown; counting it here would make the quit sheet's summary flatter than the
 * record it is about to write, and §A QUIT_SHEET's line has to be the honest one.
 */
fun stationsCompletedOf(timeline: Timeline): Int = timeline.segments.count {
    (it.phase == Phase.WORK || it.phase == Phase.REPS) && it.closed && !it.skipped
}

/**
 * The cell the cue engine is being armed for — `03-player.md` §D.1's "derived from the timeline, not
 * from ticks".
 *
 * The mapping is here rather than in `gym/cue/` on purpose: [CueSegment]'s own KDoc explains that it
 * is deliberately not `Segment`, so that the cue schedule can be tested without building a routine.
 * This is the one place the two shapes meet, and it is the player's side of the seam.
 *
 * @param nextName resolved by the caller from the catalogue, because §D.2's `SESSION_START` and
 *   `INTERVAL_END` rows both speak the exercise that is *about to start* — the same string from both
 *   sides of a boundary.
 * @param strings the language the movement is **spoken** in. It has to be the engine's own, not a
 *   guess: a name resolved in one language and read by a voice bound to the other is mispronounced
 *   whatever the string table says, which is why `SessionController` passes `cues.strings` and never
 *   a table of its own.
 */
fun cueSegmentFor(
    timeline: Timeline,
    ordinal: Int,
    lib: Map<String, Exercise>,
    strings: Strings,
): CueSegment {
    val segment = timeline.segments[ordinal]
    val capAt = timeline.capMs?.takeIf { timeline.extensible }?.let { cap ->
        timeline.prepareMs + cap - segment.startMs
    }
    return CueSegment(
        ordinal = ordinal,
        phase = segment.phase,
        plannedMs = segment.effectiveMs,
        // An extensible AMRAP's last materialised cell is a compilation detail, never the end of the
        // session — §A REPS edge case 7: "the user must never see 完了 mid-cap". Its ending is the cap,
        // which arrives as its own cue.
        isFinalSegment = ordinal == timeline.segments.lastIndex && !timeline.extensible,
        startsFinalRound = startsFinalRound(timeline, ordinal),
        nextExerciseNameJa = nextExerciseName(timeline, ordinal, lib, strings),
        capAtMs = capAt,
        open = segment.open && !segment.closed,
        anchored = segment.anchored,
    )
}

/**
 * The name §D.2's two dynamic rows speak, or null when there is nothing after this.
 *
 * It walks past rests to the next cell that names a movement, because 「次、休息 十五秒、そのあと
 * プランク」 is one sentence about one exercise and the rest is its adverb.
 */
fun nextExerciseName(
    timeline: Timeline,
    ordinal: Int,
    lib: Map<String, Exercise>,
    strings: Strings,
): String? = nextExercise(timeline, ordinal, lib)?.displayName(strings)

/** As [nextExerciseName], resolved. Null when the catalogue no longer knows the id, or nothing follows. */
fun nextExercise(timeline: Timeline, ordinal: Int, lib: Map<String, Exercise>): Exercise? =
    timeline.segments.asSequence()
        .drop(ordinal + 1)
        .mapNotNull { it.exerciseId }
        .firstOrNull()
        ?.let { lib[it] }

/**
 * The next cell a user will actually see, skipping the ones §B.6 invariant 4 says are never rendered.
 *
 * A minute used to its last second leaves an EMOM remainder with no span at all. It is a real cell and
 * it is consumed by the machine, but showing 「次 ・ 残り 0:00」 would be describing something that does
 * not happen.
 */
fun nextVisibleSegment(timeline: Timeline, ordinal: Int) =
    timeline.segments.drop(ordinal + 1).firstOrNull { !it.vestigial }

/**
 * §A WORK state 4: the first effort cell of the final round.
 *
 * Single-round routines are excluded — 「最後の巡」 on a routine with one lap is not information, and
 * `LAST_ROUND` firing on the first station of every 七分間 would be a cue that means nothing.
 */
fun startsFinalRound(timeline: Timeline, ordinal: Int): Boolean {
    val segment = timeline.segments[ordinal]
    if (timeline.totalRounds <= 1 || segment.round != timeline.totalRounds) return false
    if (segment.phase != Phase.WORK && segment.phase != Phase.REPS) return false
    return timeline.segments.take(ordinal).none {
        it.round == segment.round && (it.phase == Phase.WORK || it.phase == Phase.REPS)
    }
}

/**
 * Which row of §D.7's disarm matrix a transition's `DisarmCues` belongs to.
 *
 * The effect says *that* the cues must go quiet; the matrix says *how much*, and the difference
 * between its rows is the difference between a skip (drop the pending posts, keep the duck for the
 * segment being landed in) and a pause (cancel the motor, drop the duck, stop the voice). Deciding it
 * from the rule and the destination keeps the matrix's five rows five rows, rather than a boolean the
 * caller has to remember to set at four call sites.
 *
 * `ON_STOP` is not derivable from any transition and is dispatched by the lifecycle instead — it is
 * the one row that is not a decision the state machine makes.
 */
fun cueEventFor(rule: Rule, destination: Destination): CueEvent = when {
    destination is Destination.Complete -> CueEvent.COMPLETE
    rule == Rule.PAUSED || rule == Rule.STALLED -> CueEvent.PAUSE
    rule == Rule.QUIT_REQUESTED -> CueEvent.QUIT_SHEET
    // Discarding is the loudest thing that can happen to a running session and it must be silent:
    // it is a quit sheet outcome, so it takes the quit sheet's row rather than a skip's.
    rule == Rule.DISCARDED -> CueEvent.QUIT_SHEET
    else -> CueEvent.SKIP
}

/**
 * The dynamic phrase a fired cue speaks, or null where §D.2's row carries a fixed one.
 *
 * Only two rows are dynamic and both name the movement **about to start**, which is why this is one
 * lookup rather than a table: everything else in [Cue] already holds its own words.
 *
 * It reads from [ordinal] **inclusive**, unlike [nextExerciseName], and the difference is the whole
 * correctness of the phrase. A fired cue is fired *after* the transition that caused it, so the
 * segment `stateAt` reports is already the new one — its own exercise is the one about to start, and
 * only when it is a rest does the answer lie further on.
 */
fun cueSpeechFor(
    cue: Cue,
    timeline: Timeline,
    ordinal: Int,
    lib: Map<String, Exercise>,
    strings: Strings,
): String? = when (cue) {
    Cue.SESSION_START, Cue.INTERVAL_END -> upcomingExerciseName(timeline, ordinal, lib, strings)
    else -> null
}

/** The movement at or after [ordinal] — see [cueSpeechFor] for why "at" is included. */
fun upcomingExerciseName(
    timeline: Timeline,
    ordinal: Int,
    lib: Map<String, Exercise>,
    strings: Strings,
): String? = timeline.segments.asSequence()
    .drop(ordinal)
    .mapNotNull { it.exerciseId }
    .firstOrNull()
    ?.let { lib[it]?.displayName(strings) }

/**
 * Whether a transition's effects end the session — the one obligation `ui/gym/KeepAwake.kt` states it
 * cannot discharge.
 *
 * `keepAwake` excludes 型の中身 and the resume prompt structurally, because neither is
 * `GymRoute.Session`. `GYM.SESSION.COMPLETE` it cannot exclude: 記録 is a *phase* of the player and
 * renders under the same route, so the flag is only dropped when `GymViewModel.onSessionClosed()`
 * nulls the live session. That call is owed at finish, at quit and at discard, and this predicate is
 * how the host knows which transitions those are without matching on rules by hand.
 *
 * A workout that ended with the screen-on flag still held is a phone that never sleeps in a bag —
 * the leak class commit `1f49dfc` was written to close, arrived at from the third direction.
 */
fun closesSession(effects: List<SessionEffect>): Boolean =
    effects.any { it is SessionEffect.Finish || it is SessionEffect.Discard }
