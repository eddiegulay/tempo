package io.eddiegulay.tempo.ui.gym.session

import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.session.Timeline
import io.eddiegulay.tempo.gym.session.canExtend
import io.eddiegulay.tempo.gym.session.canSkip
import io.eddiegulay.tempo.gym.session.stateAt
import io.eddiegulay.tempo.ui.ensoSweep

/**
 * One frame, built from the compiled timeline and one elapsed reading — and from nothing else.
 *
 * This is the whole of what the 50ms tick does. It **re-reads** state; it never advances it
 * (`03-player.md` §B.5), which is why every field below is a function of `(timeline, elapsedMs)` and
 * why rotation, a doze, a seek and a cold-start recovery all produce the same frame as a live tick
 * would.
 *
 * *Rejected* — memoising the derived fields across ticks. They are a handful of list scans over forty
 * cells; a cache would be a second copy of the session's state, and the reason this player is
 * tractable at all is that there is exactly one.
 *
 * @param prepare non-null for the 3s countdown back in, which has no segment of its own (§C.1 row 19,
 *   and see [PrepareUi]). When it is set, the ring depletes over **it** rather than over the segment
 *   the session is about to resume into — the user is watching a countdown, not a plank.
 */
@Suppress("LongParameterList")
fun sessionUiState(
    routineId: String,
    routineName: String,
    stationsPlanned: Int,
    timeline: Timeline,
    elapsedMs: Long,
    lib: Map<String, Exercise>,
    overlay: SessionOverlayUi,
    prepare: PrepareUi?,
    autoAdvance: Boolean,
    resultsWritten: Int,
    fault: GymFault?,
): SessionUiState {
    val state = timeline.stateAt(elapsedMs)
    val segment = state.segment
    val next = nextVisibleSegment(timeline, state.ordinal)

    // The resumed countdown owns the ring while it is on screen; the segment underneath is frozen and
    // its own fraction would read as a station already half gone.
    val ring = if (prepare != null && prepare.resumed && prepare.totalMs > 0L) {
        prepare.remainingMs.toFloat() / prepare.totalMs
    } else {
        state.ringFraction
    }

    return SessionUiState(
        routineId = routineId,
        routineName = routineName,
        engine = timeline.engine,
        timeline = timeline,
        ordinal = state.ordinal,
        segment = segment,
        phase = segment.phase,
        elapsedMs = elapsedMs,
        // 支度 is timeline offset [0, prepareMs) and is excluded from active time (§A PREPARE).
        activeMs = (elapsedMs - timeline.prepareMs).coerceAtLeast(0L),
        elapsedInSegmentMs = state.elapsedInSegmentMs,
        remainingMs = state.remainingMs,
        overrunMs = state.overrunMs,
        ringFraction = ring,
        ensoSweepAngle = ensoSweep(ring),
        sessionFraction = state.sessionFraction,
        round = segment.round,
        totalRounds = timeline.totalRounds,
        roundsCompleted = roundsCompletedOf(timeline),
        stationIndex = segment.stationIndex,
        stationsPerRound = timeline.stationsPerRound,
        stationsCompleted = stationsCompletedOf(timeline),
        stationsPlanned = stationsPlanned,
        exercise = segment.exerciseId?.let { lib[it] },
        prescribedReps = segment.prescribedReps,
        restKind = segment.restKind,
        addedMs = segment.addedMs,
        next = next,
        nextExercise = nextExercise(timeline, state.ordinal, lib),
        isLastRound = timeline.totalRounds > 1 && segment.round == timeline.totalRounds,
        // The structural skip is always allowed over work; a rest decides for itself, and the two it
        // refuses must render **visibly disabled** rather than silently inert (§A REST states 4, 5).
        canSkipForward = segment.phase != Phase.REST || canSkip(segment.restKind),
        canExtendRest = segment.phase == Phase.REST && canExtend(segment.restKind),
        // ◁◁ never steps back into 支度 — re-running a countdown the user already sat through is not
        // what they asked for (§C.4).
        canStepBack = state.ordinal > timeline.firstRealSegment,
        autoAdvance = autoAdvance,
        finished = state.finished,
        prepare = prepare,
        overlay = overlay,
        resultsWritten = resultsWritten,
        fault = fault,
    )
}

/**
 * 支度, when the timeline's own first cell is one — the other half of [PrepareUi].
 *
 * Returns null the instant the countdown is over, so the page swaps to WORK on the same frame the
 * clock does rather than a tick later. A `prepareSeconds == 0` routine emits no PREPARE segment at all
 * (§A PREPARE edge case 5), so this is null from the first frame and nothing special-cases zero.
 */
fun compiledPrepare(timeline: Timeline, elapsedMs: Long): PrepareUi? {
    val first = timeline.segments.firstOrNull() ?: return null
    if (first.phase != Phase.PREPARE) return null
    val remaining = first.effectiveMs - elapsedMs
    if (remaining <= 0L) return null
    return PrepareUi(remainingMs = remaining, totalMs = first.effectiveMs, resumed = false)
}
