package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.Strings
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * What a routine will cost, before you do it: 約 十八分 ・ 三百回.
 *
 * The number is arithmetic over design §3.5's per-rep pace table, and the table is the reason this is
 * a *pure* function rather than a repository read. §3.5 is unusually clear that the pace exists "only
 * for the pacer and the routine's about-七分 estimate. It never advances anything." A pace that lives
 * next to the timeline compiler eventually gets used by it.
 *
 * **Every pace comes from the exercise row, and every exercise row is seeded from §3.5's table** —
 * push-up 2.0s, pull-up 3.0s, squat 1.8s, sit-up 1.7s, dip 2.5s, burpee 4.0s. This file never
 * substitutes a default for a movement the table omits: an unknown exercise contributes no seconds and
 * flips [RoutineEstimate.approximate], so the line says 目安 rather than quietly inventing 2.0s for a
 * movement nobody timed. §3's own rule for the one case that *does* have a number — MAX_EFFORT, at
 * [MAX_EFFORT_FALLBACK_SECONDS] — ends with "never present a guess as arithmetic", and that is the
 * whole doctrine of this file.
 */

/**
 * The seconds a `限界まで` station contributes to a duration estimate when there is no history to take
 * a median from — `04-library-records.md` §3, BUILDER edge case 7, verbatim ("its *median historical*
 * seconds, or 45s with no history").
 *
 * The median half of that rule is real and is implemented: [estimateRoutine]'s `maxEffortSeconds`
 * lookup. This constant is what it falls back to, which is every call that does not pass one.
 *
 * A max-effort station contributes **zero reps**, always. It has no prescribed count, and putting the
 * median historical one into 三百回 would make the reps half-measured and wholly unlabelled.
 */
const val MAX_EFFORT_FALLBACK_SECONDS = 45

/**
 * What a routine is expected to cost.
 *
 * [approximate] is not a confidence score, it is a *string* decision: true means the estimate line
 * appends 目安 because something in it was not arithmetic — a max-effort station, or an exercise the
 * catalogue could not resolve. It is deliberately coarse. A percentage error bar on a workout estimate
 * would be the most precise thing on a page whose whole register is approximation.
 */
data class RoutineEstimate(
    val durationSeconds: Int,
    val totalReps: Int,
    val approximate: Boolean,
)

/**
 * One station's contribution to the clock, in seconds — or **null when it cannot be estimated**.
 *
 * Null has one meaning per measure, and they are different meanings:
 * - `MAX_EFFORT` — there is nothing to estimate. `04-library-records.md` §3's picker replaces the
 *   wheel with できるところまで and *removes the 目安 line entirely*, and this null is what that state
 *   reads. The routine estimate substitutes [MAX_EFFORT_FALLBACK_SECONDS] itself, because a duration
 *   still has to add up; a single station's own 目安 must not.
 * - `REPS` with an unresolved [exercise] — there is no pace, and the table forbids inventing one.
 *
 * `DURATION` needs no exercise at all: the prescription *is* the seconds.
 *
 * Rounds rather than truncates, because 5 pull-ups at 3.0s is exactly 15 and 15 sit-ups at 1.7s is
 * 25.5 — truncating would make every odd station one second optimistic, and twelve of them a station
 * short.
 */
fun paceEstimateSec(station: StationDraft, exercise: Exercise?): Int? = when (station.measure) {
    Measure.DURATION -> station.seconds
    Measure.MAX_EFFORT -> null
    Measure.REPS -> {
        val reps = station.reps
        val perRep = exercise?.secondsPerRep
        if (reps == null || perRep == null) null else max(0, (reps * perRep).roundToInt())
    }
}

/**
 * The whole routine: how long, how many reps, and whether either was a guess.
 *
 * **The arithmetic is pinned by a documented total.** `00-plan.md` §2 row 18 states 七分間 as
 * `12 × 30 + 11 × 10 + 5 prepare = 475`, and the eleven is the load-bearing part: a round has
 * `n − 1` station rests, not `n`. A trailing rest after the last station of a round would add ten
 * seconds here, break the one number the plan writes out in full, and — worse — put a rest between the
 * last station and the finish line in the compiler that reads this shape.
 *
 * Per engine:
 * - `AMRAP` — the duration **is** the cap, and the reps are a ceiling. 「六百回まで」 is an upper bound
 *   (`04-library-records.md` §3's mock says まで), so a 20-minute cap over a 62-second round rounds
 *   *up* to twenty rounds rather than down to nineteen. 支度 is deliberately excluded: the cap is what
 *   the clock will show, and adding five seconds to it would make the page disagree with the timer.
 * - `EMOM` / `EMOM_ASCENDING` — a round is one interval window whether or not the work fills it, so
 *   the duration is `interval × rounds`. デス・バイ has no round count at all (it runs to failure), so
 *   it estimates nothing and [estimateLabel] renders nothing.
 * - everything else — prepare, then per round the stations and their `n − 1` rests, plus
 *   `rounds − 1` round rests.
 *
 * @param exercise the catalogue lookup, passed rather than reached for. `ExerciseCatalog` is a
 *   synchronous in-memory map (`00-plan.md` §2 row 6) and this could call it directly — but then this
 *   file would need the catalogue loaded to be testable, and the builder's live estimate is the one
 *   thing on that page that recomputes on every keystroke.
 * @param maxEffortSeconds the **median historical** seconds a `限界まで` station on this exercise has
 *   actually run, keyed by exercise id — the first half of `04-library-records.md` §3 BUILDER edge case
 *   7, which reads "it contributes 0 reps and its *median historical* seconds (or 45s with no history)".
 *   A lookup rather than a value because a routine can hold several max-effort stations on different
 *   movements, and a 懸垂 hold and a 腕立て伏せ hold are not the same guess. Returning null — or a
 *   non-positive median, which is a store bug rather than a fast set — falls back to
 *   [MAX_EFFORT_FALLBACK_SECONDS].
 *
 *   **Nothing supplies it in Phase 1.** The median is a query over `session_station` durations that
 *   `GymRepository` does not expose yet, so `GYM.LIBRARY.BUILDER`'s live estimate and the detail page's
 *   summary both default it and **every max-effort station currently estimates at 45s**. That is a
 *   named gap with a seam, not a silent one: the surface that owes the query is the repository, the
 *   parameter it feeds is here, and until it lands the 目安 suffix — which this station flips
 *   regardless of which branch it took — is the whole of what the page claims.
 */
fun estimateRoutine(
    draft: RoutineDraft,
    exercise: (String) -> Exercise?,
    maxEffortSeconds: (String) -> Int? = { null },
): RoutineEstimate {
    var approximate = false
    var workSeconds = 0
    var repsPerRound = 0
    for (station in draft.stations) {
        val seconds = paceEstimateSec(station, exercise(station.exerciseId))
        when {
            seconds != null -> workSeconds += seconds
            station.measure == Measure.MAX_EFFORT -> {
                // The median is a measurement of this user's own sets, and 45s is a documented stand-in
                // for having none. Either way the total is not arithmetic, so 目安 goes on regardless:
                // edge case 7 appends it to the rule, not to the fallback branch of the rule.
                workSeconds += maxEffortSeconds(station.exerciseId)?.takeIf { it > 0 }
                    ?: MAX_EFFORT_FALLBACK_SECONDS
                approximate = true
            }
            // An unresolvable exercise, or a prescription with no value. It contributes no time —
            // inventing a pace is the one thing §3.5 forbids — and the 目安 suffix says the total is
            // short by something rather than pretending it is not.
            else -> approximate = true
        }
        if (station.measure == Measure.REPS) repsPerRound += station.reps ?: 0
    }
    val stations = draft.stations.size
    val stationRests = if (stations > 1) draft.restBetweenStations * (stations - 1) else 0
    val roundSeconds = workSeconds + stationRests

    return when (draft.engine) {
        Engine.AMRAP -> {
            val cap = draft.timeCapSeconds ?: 0
            val rounds = if (roundSeconds > 0) ceil(cap.toDouble() / roundSeconds).toInt() else 0
            RoutineEstimate(cap, repsPerRound * rounds, approximate)
        }

        Engine.EMOM, Engine.EMOM_ASCENDING -> {
            val rounds = draft.rounds
            val interval = draft.intervalSeconds
            if (rounds == null || interval == null) {
                // Nothing to say, and 約 〇分 would be saying something. [estimateLabel] renders "".
                RoutineEstimate(0, 0, approximate)
            } else {
                RoutineEstimate(draft.prepareSeconds + interval * rounds, repsPerRound * rounds, approximate)
            }
        }

        else -> {
            val rounds = max(1, draft.rounds ?: 1)
            val roundRests = draft.restBetweenRounds * (rounds - 1)
            RoutineEstimate(
                durationSeconds = draft.prepareSeconds + roundSeconds * rounds + roundRests,
                totalReps = repsPerRound * rounds,
                approximate = approximate,
            )
        }
    }
}

/**
 * The builder's live line and the detail page's summary: `約 十八分 ・ 三百回`.
 *
 * Three variations, all documented:
 * - AMRAP's reps are a cap, so the fragment is 「六百回まで」 (`04-library-records.md` §3, edge case 6:
 *   "The estimate on an AMRAP is a cap, not a duration").
 * - Anything approximate appends ` 目安` (edge case 7).
 * - **Zero reps omits the reps fragment**, rather than printing 〇回. A pure-time circuit — 七分間 is
 *   one third holds — has no rep count, and zero there is an inapplicability, not a result
 *   (`03-player.md` §A, COMPLETE edge case 4, which makes the same call for the tile).
 *
 * Minutes round to the nearest, then floor at one. 475 seconds reads 約 八分, which is `00-plan.md`
 * §2 row 18's own arithmetic — the routine is *named* 七分間 and the app says eight, deliberately,
 * because "7-minute workout" is ACSM's branding rounding and 約 is not a licence to repeat it. A
 * routine under thirty seconds would round to zero, and 約 〇分 is not a duration, so it floors at
 * 約 一分.
 *
 * An estimate of nothing at all — デス・バイ, which runs until you fail — returns the **empty string**,
 * and the caller omits the line. There is no documented copy for "unbounded", and 約 〇分 would be a
 * claim.
 */
fun estimateLabel(engine: Engine, estimate: RoutineEstimate, strings: Strings): String {
    val minutes = if (estimate.durationSeconds > 0) {
        max(1, (estimate.durationSeconds / 60.0).roundToInt())
    } else {
        0
    }
    if (minutes == 0 && estimate.totalReps == 0) return ""
    val parts = buildList {
        if (minutes > 0) add(strings.gymShared.estimateDuration(strings.fmt.minutes(minutes)))
        if (estimate.totalReps > 0) {
            val reps = strings.fmt.reps(estimate.totalReps)
            add(if (engine == Engine.AMRAP) strings.gymShared.estimateRepsCap(reps) else reps)
        }
    }
    val line = parts.joinToString(strings.fmt.separator)
    return if (estimate.approximate) strings.gymShared.estimateApproximate(line) else line
}
