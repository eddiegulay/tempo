package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.DailyLoad
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.LoadScale
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.PersistedClock
import io.eddiegulay.tempo.gym.Resumability
import io.eddiegulay.tempo.gym.Streak
import io.eddiegulay.tempo.gym.WeekPoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Everything in 鍛錬's store that can be decided without a database.
 *
 * The split is not tidiness. `02-data.md` §D says outright which halves of the hard queries belong
 * here and why: SQLite 3.28 has no `stdev()`, no `sqrt()`, and Android's public `SQLiteDatabase`
 * cannot register a custom function, so monotony has no SQL form at all; the streak fold carries a
 * forgiveness budget that resets on a month boundary and a plan that is date-versioned, which in SQL
 * is a recursive CTE carrying running state — writable, unreadable, and testable only against a real
 * device. Every function below is instead a plain function over plain values, which is the same
 * bargain the repo already makes for `groupByApp` and `layoutTategaki`, and it is what makes §D
 * verifiable at all.
 *
 * The other reason this file exists is `00-plan.md` §2 row 14: **calendar bucketing happens here, in
 * Kotlin, at write time.** SQLite's `date()` and `strftime()` are UTC, so a 23:30 session in UTC+9
 * bucketed in SQL lands on the previous day, and `strftime('%W')` is not ISO week numbering. That is
 * the single most common bug in fitness-history code and the reason `session.local_date` and
 * `session.local_week_start` are stored columns rather than expressions.
 */

// ─── Calendar bucketing ─────────────────────────────────────────────────────────────────────────

/**
 * The local day an instant belongs to, as the `YYYY-MM-DD` string stored in `session.local_date`.
 *
 * Stored as text rather than an epoch day because ISO-8601 sorts lexicographically, which is what lets
 * `idx_session_date` serve a month range scan as an exact index range (§A.6). An integer epoch day
 * would sort just as well and would be unreadable in a `sqlite3` dump of a bug report.
 */
fun localDateOf(millis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/**
 * The ISO Monday of the week a date falls in — `session.local_week_start`.
 *
 * `DayOfWeek.MONDAY` explicitly rather than a locale's first-day-of-week: the two weekly charts are
 * compared against each other and against a 7-day monotony window, and a week that starts on Sunday
 * for one user and Monday for another makes those numbers incomparable across a bug report.
 */
fun weekStartOf(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

/** The stored form of a date column. Fixed width 10, which the schema's CHECK constraints assert. */
fun storedDate(date: LocalDate): String = date.toString()

/** Parses a stored date column, or null for a value that is not one — a corrupt row, never a default. */
fun parseStoredDate(raw: String?): LocalDate? =
    raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

// ─── Weighted volume (§D.5) ─────────────────────────────────────────────────────────────────────

/**
 * One station's contribution to weighted volume.
 *
 * Design §7.4 states `Σ reps × coefficient`, and that formula **scores a sixty-second plank as zero**:
 * `actual_reps` is NULL for a duration station, so 七分間 — one third holds — reads as half a workout.
 * The fix is here rather than in the query: isometric holds convert at the exercise's own
 * [Exercise.secondsPerRep], which for a hold is redefined as *the seconds that count as one unit*
 * (ten), so a thirty-second plank scores three units × its coefficient.
 *
 * The result is frozen onto `session_result.volume_units` at write time. That is the whole point — a
 * coefficient retuned in a later release must not move a chart the user has already read (§A.0.5), and
 * a `SUM(volume_units)` is also a flat scan where a join back to the live `exercise` table would not
 * be (§D.5).
 *
 * *Rejected* — letting the player compute this and pass it down. Two code paths over one formula drift,
 * and the player has no business knowing what a coefficient is.
 */
fun volumeUnits(exercise: Exercise, actualReps: Int?, actualDurationMs: Long): Double =
    if (exercise.isIsometric) {
        (actualDurationMs / 1000.0) / exercise.secondsPerRep * exercise.difficulty
    } else {
        (actualReps ?: 0) * exercise.difficulty
    }

// ─── The streak fold (§D.1) ─────────────────────────────────────────────────────────────────────

/**
 * One row of `training_plan`, in force from [effectiveFrom] until a later row supersedes it.
 *
 * Date-versioned for the same reason `routine_version` is: changing your plan must not retroactively
 * rewrite last month's streak (§A.8). Per `DECISIONS.md` §Q1 no row is ever written in Phases 1–3, so
 * in practice this list is empty and the fallback below is what runs — but the table exists in schema
 * v1 so adding the picker later needs no migration.
 */
data class PlanWindow(
    val effectiveFrom: LocalDate,
    val daysMask: Int,
    val forgivenessPerMonth: Int,
)

/** The plan in force on [date] — the latest window that had already started. Null when none has. */
fun List<PlanWindow>.inForce(date: LocalDate): PlanWindow? =
    filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }

/**
 * 連続: consecutive days *the plan was honoured*, walking back from [today].
 *
 * A day is honoured when it was trained, or when nothing was planned for it. A missed planned day is
 * absorbed by that calendar month's forgiveness budget; the streak ends when the month's budget is
 * exhausted. Crossing into an earlier month refreshes the budget, which is what "two per month" plainly
 * means.
 *
 * With no plan on file the fallback is: honoured if trained, or if the day before was. One earned rest
 * day per training day — design §5.2's promise, with no plan model and no hidden state (§A.8).
 *
 * Today itself never breaks a streak: an untrained today is "not yet", not "missed".
 *
 * [today] must come from the repository's monotonic-guarded clock, never a raw `LocalDate.now()`. A
 * system clock wound backwards must not be able to fabricate a streak (§E.4).
 */
fun currentStreak(
    today: LocalDate,
    trained: Set<LocalDate>,
    plans: List<PlanWindow>,
    maxLookbackDays: Int = MAX_STREAK_LOOKBACK_DAYS,
): Streak {
    var day = today
    var length = 0
    var monthKey = YearMonth.from(today)
    var budget = plans.inForce(today)?.forgivenessPerMonth ?: 0
    var forgiven = 0

    while (ChronoUnit.DAYS.between(day, today) < maxLookbackDays) {
        if (YearMonth.from(day) != monthKey) {
            monthKey = YearMonth.from(day)
            budget = plans.inForce(day)?.forgivenessPerMonth ?: 0
            forgiven = 0
        }
        val plan = plans.inForce(day)
        // §D.1's snippet writes the fallback as `day in trained || day.minusDays(1) in trained` and
        // that is inverted: `planned` is the thing that *breaks* a streak when untrained, so writing
        // "honoured" into it makes an untrained day after a training day end the streak and an
        // untrained day in the middle of nowhere extend it — a user with one session ever would read
        // 四百日 連続. The rule §A.8 states in prose is implemented instead, unchanged in every
        // observable respect: honoured if trained, or if the day before was; a day that is neither is
        // the miss that ends it, which is exactly "the streak ends at the second consecutive
        // untrained day".
        val planned = plan?.let { it.daysMask and (1 shl day.dayOfWeek.ordinal) != 0 }
            ?: !(day in trained || day.minusDays(1) in trained)
        val didTrain = day in trained

        when {
            didTrain -> length++
            !planned -> length++
            day == today -> Unit
            forgiven < budget -> {
                forgiven++
                length++
            }
            else -> return Streak(length, forgivenThisMonth = forgiven, endedOn = day)
        }
        day = day.minusDays(1)
    }
    return Streak(length, forgiven, endedOn = null)
}

/** A longer streak than this is not worth the scan it would cost (§D.1). */
const val MAX_STREAK_LOOKBACK_DAYS: Int = 400

// ─── Load, monotony, ACWR (§D.6) ────────────────────────────────────────────────────────────────

/**
 * Foster monotony: mean/sd of daily load over seven days, rest days counted as zero — the zeros are
 * the point, since they are what makes an easy day easy.
 *
 * Population SD, matching Foster's original formulation. Returns null when any day in the window holds
 * an unrated session — the window's load is then partly unknown and a number would be a guess — and
 * when SD is zero, where seven identical days are undefined and the honest answer is no answer.
 */
fun monotony(window: List<DailyLoad>): Double? {
    require(window.size == MONOTONY_WINDOW_DAYS) { "monotony needs exactly $MONOTONY_WINDOW_DAYS days" }
    if (window.any { it.unrated > 0 }) return null
    val loads = window.map { it.load }
    val mean = loads.average()
    val sd = sqrt(loads.sumOf { (it - mean).pow(2) } / loads.size)
    return if (sd < 1e-9) null else mean / sd
}

/**
 * Acute:chronic workload ratio — a seven-day mean over a twenty-eight-day mean, both over zero-filled
 * spines.
 *
 * **Computed and never displayed as risk** (§7.4). It exists to govern a ramp at the boring, defensible
 * ten-percent-per-week cap in Phase 4, and it is suppressed entirely below twenty-eight days of history,
 * which is what the null return means. An injury-risk percentage from a launcher would be a medical
 * claim this app is in no position to make.
 */
fun acwr(spine: List<DailyLoad>): Double? {
    if (spine.size < ACWR_CHRONIC_DAYS) return null
    val chronic = spine.takeLast(ACWR_CHRONIC_DAYS)
    val acute = spine.takeLast(MONOTONY_WINDOW_DAYS)
    val chronicMean = chronic.sumOf { it.load } / ACWR_CHRONIC_DAYS
    if (chronicMean < 1e-9) return null
    return (acute.sumOf { it.load } / MONOTONY_WINDOW_DAYS) / chronicMean
}

const val MONOTONY_WINDOW_DAYS: Int = 7
const val ACWR_CHRONIC_DAYS: Int = 28

/**
 * Fills the gap days a `GROUP BY` cannot produce.
 *
 * `GROUP BY local_date` returns no row for a rest day, and a rest day is exactly the thing monotony
 * needs to see as a zero. Kotlin rather than a recursive-CTE date spine: the spine is two lines here
 * and untestable off a device there (§D.6).
 */
fun zeroFilledLoad(from: LocalDate, to: LocalDate, byDate: Map<LocalDate, DailyLoad>): List<DailyLoad> =
    generateSequence(from) { if (it < to) it.plusDays(1) else null }
        .map { byDate[it] ?: DailyLoad(date = it, load = 0.0, sessions = 0, unrated = 0) }
        .toList()

/**
 * The same zero-fill for the weekly charts, which need a contiguous array of exactly [weeks] points
 * ending at the week containing [today]. Weeks with no sessions do not appear in the query result and
 * must still be drawn, or the bars silently close up and a fortnight off looks like a fortnight on
 * (§D.4).
 */
fun zeroFilledWeeks(today: LocalDate, weeks: Int, byWeek: Map<LocalDate, WeekPoint>): List<WeekPoint> {
    val lastMonday = weekStartOf(today)
    return (weeks - 1 downTo 0).map { back ->
        val monday = lastMonday.minusWeeks(back.toLong())
        byWeek[monday] ?: WeekPoint(monday, sessions = 0, activeMinutes = 0, completeSessions = 0)
    }
}

// ─── Ink levels (§D.3) ──────────────────────────────────────────────────────────────────────────

/**
 * The three thresholds that turn a day's active minutes into an ink level, taken over the user's own
 * non-zero days in a trailing window.
 *
 * Relative rather than absolute, because absolute cutoffs leave a twenty-minute-session user
 * permanently at level one and a marathoner permanently at level four. Computed **once** and passed
 * into the grid query rather than recomputed per query with `NTILE`: window functions do exist at
 * 3.28, but a per-query percentile makes March's dots visibly change when April turns out to be heavy,
 * and a grid that rewrites its own history is worse than a coarse one.
 *
 * [LoadScale.usedFallback] is true below [MIN_DAYS_FOR_PERCENTILES], where the sample is too thin for
 * a percentile to mean anything. **§D.3 says to fall back to fixed cutoffs and no spec table anywhere
 * gives their values**, so rather than invent three numbers this keeps ranking the user against their
 * own days and flags the thin sample for the caller to explain. See the Track A report.
 */
fun loadScaleOf(nonZeroDayMillis: List<Long>): LoadScale {
    val sorted = nonZeroDayMillis.filter { it > 0 }.sorted()
    if (sorted.isEmpty()) {
        return LoadScale(p40Ms = 0, p70Ms = 0, p90Ms = 0, nonZeroDays = 0, usedFallback = true)
    }
    return LoadScale(
        p40Ms = percentile(sorted, 0.40),
        p70Ms = percentile(sorted, 0.70),
        p90Ms = percentile(sorted, 0.90),
        nonZeroDays = sorted.size,
        usedFallback = sorted.size < MIN_DAYS_FOR_PERCENTILES,
    )
}

/** Nearest-rank, so every threshold is a value the user actually recorded rather than an interpolation. */
private fun percentile(sorted: List<Long>, fraction: Double): Long {
    val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

/** Ink levels 0..3, mapped by the caller to `c.ink` at 0.15 / 0.35 / 0.6 / 0.9 (§D.3). */
fun bucketOf(dayMs: Long, scale: LoadScale): Int = when {
    dayMs <= 0L -> 0
    // With no trailing history every threshold is zero, and an unguarded comparison would paint every
    // day at the darkest ink — the grid claiming to rank days it has nothing to rank against.
    scale.nonZeroDays == 0 -> 0
    dayMs >= scale.p90Ms -> 3
    dayMs >= scale.p70Ms -> 2
    dayMs >= scale.p40Ms -> 1
    else -> 0
}

const val MIN_DAYS_FOR_PERCENTILES: Int = 8

// ─── Resumability (§E.3, mirrored) ──────────────────────────────────────────────────────────────

/** NTP nudges and rounding, not a reboot (`03-player.md` §E.3). */
private const val BOOT_TOLERANCE_MS = 5_000L

/** Past this, a session is not something the user is still in the middle of. */
private const val STALE_AFTER_MS = 4 * 60 * 60 * 1000L

/**
 * Whether the one open session can be picked back up.
 *
 * `02-data.md` §C.2 says this is computed by the player's pure `resumability()`, and it is — but the
 * player's signature takes a whole `ResumableSession`, which is the very object the repository is in
 * the middle of building and which already carries the answer as a field. Reading it from there would
 * be circular, so the same rule is applied over the raw stored values at the one point where the row
 * becomes a model. `03-player.md` §E.3 is the authority; if that rule ever changes, it changes here.
 *
 * **Both checks are needed.** `elapsedRealtime()` resets to ~0 on reboot, so a stored elapsed origin
 * from a previous boot makes duration run backwards; the boot anchor catches that even when the wall
 * clock also moved, and the monotonicity check catches the pathological case where a clock adjustment
 * happens to make the two anchors agree across a reboot.
 */
fun resumabilityOf(
    clock: PersistedClock,
    lastWriteWallMs: Long,
    hasResults: Boolean,
    nowWall: Long,
    nowElapsed: Long,
): Resumability {
    val sameBoot = abs((nowWall - nowElapsed) - clock.bootAnchorMs) < BOOT_TOLERANCE_MS
    val monotonic = nowElapsed >= clock.startedAtElapsedMs
    val ageMs = nowWall - lastWriteWallMs
    return when {
        !sameBoot || !monotonic -> Resumability.REBOOTED
        ageMs >= STALE_AFTER_MS -> Resumability.STALE
        !hasResults -> Resumability.NOTHING_TO_SAVE
        else -> Resumability.RESUMABLE
    }
}

/**
 * The active time a session is worth when the current `elapsedRealtime()` cannot be trusted — a reboot,
 * or a stale row.
 *
 * Derived purely from stored values, with **no reference to the current clock at all**: the gap between
 * the last write and the reboot is unknowable and is never credited (`03-player.md` §E.3). It is also
 * what `finishSession` uses in every case, because §C.2's signature carries no clock — which is exactly
 * the conservative last-transition figure 記録する is specified to use.
 */
fun recoveredActiveMs(clock: PersistedClock, lastWriteElapsedMs: Long): Long =
    (lastWriteElapsedMs - clock.startedAtElapsedMs - clock.pausedAccumulatedMs).coerceAtLeast(0L)

// ─── Version derivation ─────────────────────────────────────────────────────────────────────────

/**
 * One station as a version is written from — the shape both the seeder and `saveRoutine` reduce to
 * before any of the derived columns can be computed.
 */
data class StationShape(
    val exerciseId: String,
    val measure: Measure,
    val reps: Int?,
    val seconds: Int?,
    val note: String?,
)

/**
 * The content address of a routine version — `routine_version.structural_hash`.
 *
 * It is what makes reseeding idempotent, and that is not a micro-optimisation: `onOpen` runs the seeder
 * on every launch whose stored catalog version is behind, including a database a crashed upgrade
 * already half-seeded. Without a content check, every such launch inserts another identical version and
 * they pile up forever (§B.3).
 *
 * Hashed from a **canonical string** rather than `listOf(…).hashCode()`, because this value is compared
 * against one written by a previous process: `String.hashCode` is specified by the language and stable
 * across processes and releases, while the hash of a data class is neither.
 *
 * The name is included deliberately. A rename must produce a new version — `routine_version.name` is
 * versioned precisely so that renaming a routine does not rewrite what past sessions say they did.
 */
fun structuralHashOf(
    name: String,
    engine: Engine,
    rounds: Int?,
    timeCapSeconds: Int?,
    intervalSeconds: Int?,
    restBetweenStations: Int,
    restBetweenRounds: Int,
    prepareSeconds: Int,
    progressionProgramId: String?,
    stations: List<StationShape>,
): Int = buildString {
    append(name).append(SEP)
    append(engine.name).append(SEP)
    append(rounds).append(SEP).append(timeCapSeconds).append(SEP).append(intervalSeconds).append(SEP)
    append(restBetweenStations).append(SEP).append(restBetweenRounds).append(SEP)
    append(prepareSeconds).append(SEP).append(progressionProgramId).append(SEP)
    stations.forEach { s ->
        append(s.exerciseId).append(SEP).append(s.measure.name).append(SEP)
        append(s.reps).append(SEP).append(s.seconds).append(SEP).append(s.note).append(SEP)
    }
}.hashCode()

/** Field separator for the canonical form above; never appears in an id or an engine name. */
private const val SEP = "\u0001"

/**
 * The denormalised 「約七分」 on every library card — `routine_version.est_duration_sec`.
 *
 * Denormalising it is safe because a version is immutable, so there is no staleness window; it is
 * *necessary* because the card renders for every row of a scrolling list and deriving it would be an
 * N+1 over `routine_station` (§A.3).
 *
 * Three things the arithmetic deliberately does:
 *
 *  - **Prepare counts.** 七分間 computes to `12×30 + 11×10 + 5 = 475`s, and `00-plan.md` §2 row 18 says
 *    to render that (≈ 約八分) rather than ACSM's branding. Dropping the prepare to reach a rounder
 *    number would be arranging the arithmetic to agree with the marketing.
 *  - **A trailing rest is not counted.** タバタ computes to `8×20 + 7×10 + 5 = 235`s where design §9
 *    says 4:00; §9 counts the rest after the last round and we do not, which is the honest number.
 *  - **`MAX_EFFORT` contributes zero.** An open-ended segment closed by 済 has no length to estimate,
 *    and guessing one would put a fabricated number on a card. Callers that care can ask whether any
 *    station is [Measure.MAX_EFFORT].
 *
 * A time-capped engine is its cap plus the prepare, because the cap *is* the duration.
 */
fun estimatedDurationSeconds(
    stations: List<StationShape>,
    secondsPerRep: (String) -> Double?,
    rounds: Int?,
    timeCapSeconds: Int?,
    restBetweenStations: Int,
    restBetweenRounds: Int,
    prepareSeconds: Int,
): Int {
    if (timeCapSeconds != null) return prepareSeconds + timeCapSeconds
    val effectiveRounds = (rounds ?: 1).coerceAtLeast(1)
    val work = stations.sumOf { s ->
        when (s.measure) {
            Measure.DURATION -> s.seconds ?: 0
            Measure.REPS -> {
                val perRep = secondsPerRep(s.exerciseId) ?: 0.0
                ceil((s.reps ?: 0) * perRep).toInt()
            }
            Measure.MAX_EFFORT -> 0
        }
    }
    val stationRests = (stations.size - 1).coerceAtLeast(0) * restBetweenStations
    val roundRests = (effectiveRounds - 1).coerceAtLeast(0) * restBetweenRounds
    return prepareSeconds + effectiveRounds * (work + stationRests) + roundRests
}

/**
 * `routine_version.est_total_reps` — prescribed reps only, across every round.
 *
 * Duration and max-effort stations contribute nothing on purpose. Converting a thirty-second plank into
 * "three reps" here would be the §7.4 mistake in reverse: [volumeUnits] exists to weigh holds, and this
 * column is the plain count of repetitions the routine asks for.
 */
fun estimatedTotalReps(stations: List<StationShape>, rounds: Int?): Int {
    val effectiveRounds = (rounds ?: 1).coerceAtLeast(1)
    return effectiveRounds * stations.sumOf { if (it.measure == Measure.REPS) it.reps ?: 0 else 0 }
}

// ─── Personal records (§D.2) ────────────────────────────────────────────────────────────────────

/**
 * Which records a finished session is allowed to take.
 *
 * The version's `primary_metric` is what the card shows and is always evaluated. `MOST_REPS` rides
 * along for the round-counting engines because `04-library-records.md` §3 edge case 6 puts 最高巡数 and
 * 最高反復 side by side on an AMRAP's detail page, and a tile with no row behind it renders nothing.
 *
 * Evaluating all five for every routine was rejected: `HIGHEST_STEP` is meaningless without a
 * progression, `BEST_TIME` is meaningless for an AMRAP that always runs its cap, and a `personal_record`
 * table full of records nobody can interpret is a table that will eventually be displayed.
 */
fun metricsFor(engine: Engine, primary: BestMetric): List<BestMetric> {
    val roundCounting = engine == Engine.AMRAP ||
        engine == Engine.EMOM ||
        engine == Engine.EMOM_ASCENDING
    return if (roundCounting && primary != BestMetric.MOST_REPS) {
        listOf(primary, BestMetric.MOST_REPS)
    } else {
        listOf(primary)
    }
}

/**
 * Whether a finished session may take [metric] at all — asked **before** any value is computed.
 *
 * `02-data.md` §D.2 opens with the rule and it is not per-metric: *a PR requires `complete = 1`*. A quit
 * for-time is not a time, a quit AMRAP is not a round count, and a 七分間 abandoned after four of twelve
 * stations is not a 最高負荷 — it is a session with less work in it than usual that happens to be
 * scored by a metric which only ever counts upward. `00-plan.md` §4.1 rule 2 says the record screen for
 * such a session carries a 途中まで chip and **no** PR chip, and `04-library-records.md` §4 edge case 1
 * says it in one line: *partial sessions never set a best, in any engine.*
 *
 * The trap this exists to close is that [BestMetric.MOST_REPS] and [BestMetric.MOST_VOLUME] read as
 * self-gating — reps done are reps done — so §D.2's own SQL for them omits the `complete = 1` clause
 * that its opening sentence states once for all five. `MOST_VOLUME` is the seeded metric for both
 * 七分間 and タバタ, so "quitting early takes the record" was the common path, not an edge case.
 *
 * [reachedTimeCap] narrows [BestMetric.MOST_ROUNDS] further, and is *not* implied by [complete]: an
 * AMRAP that stopped at minute twelve of a twenty-minute cap ran out of will, not out of clock.
 */
fun prEligible(metric: BestMetric, complete: Boolean, reachedTimeCap: Boolean): Boolean {
    if (!complete) return false
    return when (metric) {
        BestMetric.MOST_ROUNDS -> reachedTimeCap
        BestMetric.BEST_TIME,
        BestMetric.MOST_REPS,
        BestMetric.MOST_VOLUME,
        BestMetric.HIGHEST_STEP,
        -> true
    }
}

/**
 * Whether a candidate beats the record standing at [best].
 *
 * [BestMetric.BEST_TIME] is the only metric where smaller wins, and forgetting that is the bug that
 * makes a personal-best chip appear after the user's slowest ever attempt. A tie is **not** a win: ties
 * break toward the earlier session, because you set the record the first time you hit it (§D.2).
 */
fun isBetter(metric: BestMetric, value: Double, tiebreak: Double, best: Pair<Double, Double>?): Boolean {
    if (best == null) return true
    val (bestValue, bestTiebreak) = best
    return if (metric == BestMetric.BEST_TIME) {
        value < bestValue
    } else {
        value > bestValue || (value == bestValue && tiebreak > bestTiebreak)
    }
}
