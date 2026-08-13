package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.SegmentResultDraft

/**
 * What the player owes the store after the clock ran without it — `03-player.md` §A WORK edge cases
 * 3 and 4, and §E.2.
 *
 * [backFilled] is in ordinal order and must be written in that order, in one transaction with the
 * checkpoint. [autoCompleted] means **this call** closed the final segment: the session is over, and
 * `GYM.SESSION.COMPLETE` is entered without the user ever seeing its last segment.
 *
 * "This call" is load-bearing. Reporting it whenever the walk merely *reaches* the end makes it true
 * forever after on a session that already ran past its end, and `ON_START` fires again on every
 * return to the app — so a second pass with nothing to write would hand the caller a second
 * `Finish(complete = true)` and re-finish a finished session.
 */
data class Reconciliation(
    val timeline: Timeline,
    val backFilled: List<SegmentResultDraft>,
    val landedOrdinal: Int,
    val autoCompleted: Boolean,
)

/**
 * Back-fills every segment the clock walked past while the app was backgrounded.
 *
 * This is the single most important correctness path in the player. `elapsedRealtime` includes deep
 * sleep — which is why it is the session clock — so a session left on the floor through a four-minute
 * doze comes back with the frontier several segments ahead and **none of those segments written**.
 * [Timeline.stateAt] already reports the right phase; without this, the record would show a session
 * that jumped from station two to station six.
 *
 * Three rules, and the second is the one that keeps the record honest.
 *
 * 1. A passed auto-gated segment is written with `actualMs = plannedMs`, `skipped = false`, and its
 *    `closedAtElapsedMs` **interpolated** to the instant it would have ended. Writing them all with
 *    "now" would stack four stations onto one timestamp.
 * 2. **An open segment stops the walk.** A self-paced set has no end until the user says so, and
 *    inventing one — from the pacer, from the estimate, from anything — fabricates data. An EMOM cell
 *    is the same shape: it is open, its window has closed, and the fail policy is the state machine's
 *    to fire, not this function's to guess at. This is exactly the frontier rule [Timeline.stateAt]
 *    already obeys, and the two must agree or the phase on screen would not match the rows on disk.
 * 3. 支度 is walked past but never written. It is not a segment result (§A PREPARE, data out).
 *
 * @param fromOrdinal where the player believed it was — normally `stateAt(...).ordinal` before the
 *   gap, or 0 after a cold start replay.
 * @param nowWallMs the wall instant *at* [elapsedMs], from which every back-filled wall timestamp is
 *   interpolated backwards. Passed in so this stays pure.
 */
fun Timeline.reconcile(fromOrdinal: Int, elapsedMs: Long, nowWallMs: Long): Reconciliation {
    var tl = this
    val drafts = mutableListOf<SegmentResultDraft>()
    var ordinal = fromOrdinal.coerceAtLeast(0)
    var closedTheLast = false
    while (ordinal <= tl.segments.lastIndex) {
        val s = tl.segments[ordinal]
        if (s.open && !s.closed) break
        if (!s.closed) {
            if (s.endMs > elapsedMs) break
            val closedAtElapsed = s.endMs
            if (ordinal == tl.segments.lastIndex) closedTheLast = true
            tl = tl.markClosed(ordinal, s.effectiveMs, s.addedMs)
            if (s.phase != Phase.PREPARE) {
                drafts += SegmentResultDraft(
                    ordinal = s.ordinal,
                    phase = s.phase,
                    stationIndex = s.stationIndex,
                    exerciseId = s.exerciseId,
                    round = s.round,
                    prescribedReps = s.prescribedReps,
                    // Never a rep count. The segment ran its planned length unattended; what the user
                    // actually did in it is unknown, and a prescription is a plan, not a record.
                    actualReps = null,
                    plannedMs = s.plannedMs,
                    actualMs = s.effectiveMs,
                    addedMs = s.addedMs,
                    skipped = false,
                    closedAtWallMs = nowWallMs - (elapsedMs - closedAtElapsed),
                    closedAtElapsedMs = closedAtElapsed,
                )
            }
        }
        ordinal++
    }
    // Past the end *and* it was this walk that took it there. A session whose last segment was
    // already closed before the call is simply a finished session being looked at twice.
    val autoCompleted = ordinal > tl.segments.lastIndex && closedTheLast
    return Reconciliation(
        timeline = tl,
        backFilled = drafts.toList(),
        landedOrdinal = tl.stateAt(elapsedMs).ordinal,
        autoCompleted = autoCompleted,
    )
}
