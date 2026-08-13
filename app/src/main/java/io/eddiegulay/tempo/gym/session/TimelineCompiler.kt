package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.ProgressionStep
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.RoutineStation
import kotlin.math.roundToLong

/*
 * `03-player.md` §B.2 and §B.3 — the seven engines.
 *
 * Phase 1 ships two of them in the UI (`00-plan.md` §6), and all seven are compiled here anyway. The
 * schema is not phased, only the UI is: a Phase-2 seed row is readable by a Phase-1 build, and an
 * engine the compiler cannot lay out is an engine that crashes the first time someone restores a
 * backup. They are also, together, about two hundred lines — the expensive part of an engine is its
 * screen, not its arithmetic.
 *
 * *Rejected* — one compiler per engine behind an interface. Six of the seven are the same three
 * moves (a station, an inter-station rest, an inter-round rest) with different gates, and the one
 * genuinely different case, the EMOM grid, is different in a way an interface cannot express: it
 * re-anchors the cursor rather than advancing it.
 */

/**
 * What an open-ended station is worth on the pacing line when nothing prescribes it —
 * `03-player.md` §B.2.
 *
 * A pacer only. It never gates, never scores, and never appears in a record: 限界まで closes when the
 * user taps 済 and the recorded duration is the measured one.
 */
const val DEFAULT_MAX_EFFORT_ESTIMATE_MS = 45_000L

/**
 * Lays a routine out as the list of cells the player will walk.
 *
 * Deterministic in the strongest sense — same snapshot, same tier, same library ⇒ byte-identical
 * timeline (§B.6 invariant 5). That is what `session.compiled_hash` asserts on resume, so **nothing
 * that can vary between two runs may enter this function**: no preferences (the 目安で自動的に進む
 * upgrade is applied at run time by [effectiveGate]), no clock, no random, no catalogue lookups
 * beyond [lib].
 *
 * Failing here is the point of calling it early. `03-player.md` §A's start path compiles *before* the
 * session INSERT precisely so a routine with no stations, or with a station whose exercise the
 * catalogue no longer knows, fails on the detail page rather than three segments into a workout.
 *
 * @param tier recorded, never applied — see [ScalingTier].
 * @param progressionStep the rung a `FIXED_SETS` routine is standing on (`02-data.md` §F.2's Recon
 *   Ron table, one row of it). Null compiles the routine's own stations as the sets, which is what a
 *   user-authored fixed-sets routine has.
 */
fun compile(
    routine: RoutineSnapshot,
    tier: ScalingTier,
    lib: Map<String, Exercise>,
    progressionStep: ProgressionStep? = null,
): Timeline {
    require(routine.stations.isNotEmpty()) { "routine ${routine.routineId} has no stations" }
    val b = Builder(routine, routine.stations.sortedBy { it.position }, lib, progressionStep)
    // `00-plan.md` §2 / §A PREPARE edge case 5: zero seconds emits NO segment rather than an empty
    // one, because a zero-length segment divides by zero in the ensō sweep.
    if (routine.prepareSeconds > 0) b.prepare()
    when (routine.engine) {
        Engine.INTERVAL_CIRCUIT -> b.intervalCircuit()
        Engine.AMRAP -> b.amrap()
        Engine.FOR_TIME -> b.forTime()
        Engine.FOR_TIME_WITH_REST -> b.forTimeWithRest()
        Engine.EMOM -> b.emom(ascending = false)
        Engine.EMOM_ASCENDING -> b.emom(ascending = true)
        Engine.FIXED_SETS -> b.fixedSets()
    }
    b.stripTrailingRest()
    return b.build()
}

/**
 * AMRAP ran out of materialised rounds. Appends one more at the tail cursor.
 *
 * The user must never see 完了 while the cap is still running (`03-player.md` §A REPS edge case 7),
 * and materialising every round a fifteen-minute cap could theoretically hold would compile hundreds
 * of cells for a session that will use nine. Compile a few, append as the frontier approaches.
 */
fun Timeline.extendOneRound(routine: RoutineSnapshot, lib: Map<String, Exercise>): Timeline {
    if (!extensible) return this
    val stations = routine.stations.sortedBy { it.position }
    val round = totalRounds + 1
    val added = mutableListOf<Segment>()
    var ordinal = segments.size
    var cursor = plannedTotalMs
    if (routine.restBetweenRounds > 0) {
        added += Segment(
            ordinal = ordinal++,
            phase = Phase.REST,
            startMs = cursor,
            plannedMs = routine.restBetweenRounds * 1000L,
            restKind = RestKind.ROUND,
            round = round - 1,
        )
        cursor += routine.restBetweenRounds * 1000L
    }
    stations.forEachIndexed { i, st ->
        val estimate = estimateMs(st, lib)
        added += Segment(
            ordinal = ordinal++,
            phase = Phase.REPS,
            startMs = cursor,
            plannedMs = estimate,
            open = true,
            gate = Gate.MANUAL,
            stationIndex = i,
            exerciseId = st.exerciseId,
            round = round,
            prescribedReps = st.prescribedReps,
        )
        cursor += estimate
        if (i != stations.lastIndex && routine.restBetweenStations > 0) {
            added += Segment(
                ordinal = ordinal++,
                phase = Phase.REST,
                startMs = cursor,
                plannedMs = routine.restBetweenStations * 1000L,
                restKind = RestKind.STATION,
                round = round,
            )
            cursor += routine.restBetweenStations * 1000L
        }
    }
    return copy(segments = segments + added, totalRounds = round)
}

/**
 * `seconds × 1000` for a duration, `count × secondsPerRep × 1000` for reps, and
 * [DEFAULT_MAX_EFFORT_ESTIMATE_MS] for 限界まで — **pacer only, never authoritative** (§B.2).
 */
internal fun estimateMs(station: RoutineStation, lib: Map<String, Exercise>): Long =
    when (val p = station.prescription()) {
        is Prescription.Duration -> p.seconds * 1000L
        is Prescription.Reps -> (p.count * exerciseFor(station, lib).secondsPerRep * 1000.0).roundToLong()
        Prescription.MaxEffort -> DEFAULT_MAX_EFFORT_ESTIMATE_MS
    }

/**
 * The library is the authority and the snapshot's own resolved copy is the fallback, because a
 * cold-start reconstruction (§E.2) compiles from a pinned version whose exercise may since have been
 * retired from the catalogue.
 *
 * Throwing when both are empty is deliberate: `RoutineSnapshot.hasUnknownExercise` exists so the
 * detail page can disable 始める with a reason, and compiling a routine with a hole in it would move
 * that failure into the middle of a live session.
 */
private fun exerciseFor(station: RoutineStation, lib: Map<String, Exercise>): Exercise =
    lib[station.exerciseId] ?: station.exercise
        ?: throw IllegalArgumentException("unknown exercise ${station.exerciseId}")

private class Builder(
    private val routine: RoutineSnapshot,
    private val stations: List<RoutineStation>,
    private val lib: Map<String, Exercise>,
    private val step: ProgressionStep?,
) {
    private val out = mutableListOf<Segment>()
    private var cursor = 0L
    private var capMs: Long? = null
    private var extensible = false

    /**
     * How many rounds were actually laid, when that is not what the routine asked for. An AMRAP's
     * `rounds` column is meaningless — the cap decides — so [amrap] records what it materialised and
     * `Timeline.totalRounds` reports that, which is what 巡 dots and [needsAnotherRound] count.
     */
    private var laidRounds: Int? = null

    private val rounds: Int get() = (routine.rounds ?: 1).coerceAtLeast(1)
    private val stationRestMs: Long get() = routine.restBetweenStations * 1000L
    private val roundRestMs: Long get() = routine.restBetweenRounds * 1000L

    /** Appends at the cursor and advances it. The one exception is [emom], which re-anchors. */
    private fun add(
        phase: Phase,
        ms: Long,
        open: Boolean = false,
        gate: Gate = Gate.AUTO,
        stationIndex: Int? = null,
        exerciseId: String? = null,
        round: Int = 1,
        reps: Int? = null,
        restKind: RestKind? = null,
        windowStartMs: Long? = null,
        windowEndMs: Long? = null,
    ) {
        out += Segment(
            ordinal = out.size,
            phase = phase,
            startMs = cursor,
            plannedMs = ms,
            open = open,
            gate = gate,
            stationIndex = stationIndex,
            exerciseId = exerciseId,
            round = round,
            prescribedReps = reps,
            restKind = restKind,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
        )
        cursor += ms
    }

    fun prepare() = add(Phase.PREPARE, routine.prepareSeconds * 1000L)

    /**
     * **1. `INTERVAL_CIRCUIT`** — everything time-boxed and auto-advancing.
     *
     * A rep-prescribed station inside a circuit still gets a fixed work box and its reps stay
     * advisory, which is the protocol: 七分間 asks for as many as you manage in thirty seconds, not
     * for twenty of them however long they take.
     */
    fun intervalCircuit() {
        for (r in 1..rounds) {
            stations.forEachIndexed { i, st ->
                add(
                    Phase.WORK, estimateMs(st, lib), gate = Gate.AUTO,
                    stationIndex = i, exerciseId = st.exerciseId, round = r, reps = st.prescribedReps,
                )
                if (i != stations.lastIndex && stationRestMs > 0) {
                    add(Phase.REST, stationRestMs, restKind = RestKind.STATION, round = r)
                }
            }
            if (r != rounds && roundRestMs > 0) {
                add(Phase.REST, roundRestMs, restKind = RestKind.ROUND, round = r)
            }
        }
    }

    /**
     * **2. `AMRAP`** — cap set, every station open, rounds materialised ahead and appended on demand.
     *
     * `(cap / roundEstimate) + 2`, minimum 3. The `+2` and the floor are slack for a user who is
     * faster than the estimate — running out of compiled rounds mid-cap is the one thing the AMRAP
     * screen may never show.
     */
    fun amrap() {
        val cap = (routine.timeCapSeconds ?: 0) * 1000L
        capMs = cap
        extensible = true
        val roundEstimate = (
            stations.sumOf { estimateMs(it, lib) } +
                stationRestMs * (stations.size - 1).coerceAtLeast(0)
            ).coerceAtLeast(1L)
        val materialised = ((cap / roundEstimate).toInt() + 2).coerceAtLeast(3)
        laidRounds = materialised
        for (r in 1..materialised) {
            openRound(r)
            if (r != materialised && roundRestMs > 0) {
                add(Phase.REST, roundRestMs, restKind = RestKind.ROUND, round = r)
            }
        }
    }

    /**
     * **3. `FOR_TIME`** — every station open, one pass, and **no rest segments at all** (§C.3).
     *
     * The player counts up and the ensō tracks station progress rather than time, because there is no
     * time to deplete against. A multi-round for-time compiles as one continuous chain, which is what
     * "no rest" means when there is more than one lap.
     */
    fun forTime() {
        for (r in 1..rounds) {
            stations.forEachIndexed { i, st ->
                add(
                    Phase.REPS, estimateMs(st, lib), open = true, gate = Gate.MANUAL,
                    stationIndex = i, exerciseId = st.exerciseId, round = r, reps = st.prescribedReps,
                )
            }
        }
    }

    /**
     * **4. `FOR_TIME_WITH_REST`** — Barbara. Rounds of open work separated by a **mandated** fixed
     * rest.
     *
     * [RestKind.MANDATED] is not a UI hint; it is the record's comparability. Skipping the three
     * minutes makes the resulting time incomparable to anyone else's Barbara, so the player refuses
     * the skip — visibly, per `03-player.md` §A REST state 4.
     */
    fun forTimeWithRest() {
        for (r in 1..rounds) {
            openRound(r)
            if (r != rounds && roundRestMs > 0) {
                add(Phase.REST, roundRestMs, restKind = RestKind.MANDATED, round = r)
            }
        }
    }

    /**
     * **5/6. `EMOM` / `EMOM_ASCENDING`** — the minute grid is anchored.
     *
     * Every cell of minute *m* carries that minute's window, and the cursor is **re-anchored** to the
     * boundary at the end of each minute rather than advanced by what was laid inside it. That is the
     * difference between an EMOM and a circuit: finishing early buys you rest, not an earlier start.
     *
     * §B.3's sketch gives each cell `plannedMs = window − (cursor − minuteStart)` — the whole
     * remaining window — and that is kept verbatim, including its consequence: in a multi-station
     * minute every cell after the first compiles to zero span, sitting on the boundary. It is not a
     * state the user can reach, because [Timeline.stateAt] pins at the frontier and the frontier is
     * the first station; the moment that station closes, the re-flow hands the next one whatever is
     * left of the minute. Advancing the cursor by an estimate instead would compile a prettier
     * timeline that the first `close` immediately contradicted.
     */
    fun emom(ascending: Boolean) {
        val window = (routine.intervalSeconds ?: 0) * 1000L
        require(window > 0) { "routine ${routine.routineId} is an EMOM with no interval" }
        capMs = window * rounds
        for (m in 1..rounds) {
            val minuteStart = cursor
            val minuteEnd = minuteStart + window
            stations.forEachIndexed { i, st ->
                val reps = (st.prescription() as? Prescription.Reps)?.count
                    ?.let { if (ascending) it + (m - 1) else it }
                add(
                    Phase.REPS, (minuteEnd - cursor).coerceAtLeast(0L),
                    open = true, gate = Gate.AUTO_AT_CAP,
                    stationIndex = i, exerciseId = st.exerciseId, round = m, reps = reps,
                    windowStartMs = minuteStart, windowEndMs = minuteEnd,
                )
            }
            // The remainder cell. Its span is rewritten by close(); zero-duration cells are never shown.
            add(
                Phase.REST, (minuteEnd - cursor).coerceAtLeast(0L),
                restKind = RestKind.EMOM_REMAINDER, round = m,
                windowStartMs = minuteStart, windowEndMs = minuteEnd,
            )
            cursor = minuteEnd // re-anchor: the grid is authoritative
        }
    }

    /**
     * **7. `FIXED_SETS`** — a step table: N open sets of prescribed reps with a fixed elastic rest.
     *
     * With a [step], the sets come from `progression_step` + `progression_set` (Recon Ron step 1 →
     * `[7,6,5,4,4]`) and every set is the routine's **first** station: a progression prescribes sets
     * of one movement, and pairing set three with station three would silently reinterpret the
     * programme. Without one, each station is a set, which is what a user-authored fixed-sets routine
     * means.
     *
     * The inter-set rest comes from the step when there is one — `progression_step.rest_sec` is part
     * of the prescription, and Recon Ron's rest is not the routine's rest.
     */
    fun fixedSets() {
        val sets = step?.sets.orEmpty()
        if (sets.isNotEmpty()) {
            laidRounds = 1 // the step *is* the session; the routine's round count does not apply
            val st = stations.first()
            val restMs = step!!.restSeconds * 1000L
            sets.forEachIndexed { i, set ->
                val ms = set.reps
                    ?.let { (it * exerciseFor(st, lib).secondsPerRep * 1000.0).roundToLong() }
                    ?: DEFAULT_MAX_EFFORT_ESTIMATE_MS
                add(
                    Phase.REPS, ms, open = true, gate = Gate.MANUAL,
                    stationIndex = 0, exerciseId = st.exerciseId, round = 1, reps = set.reps,
                )
                if (i != sets.lastIndex && restMs > 0) {
                    add(Phase.REST, restMs, restKind = RestKind.STATION, round = 1)
                }
            }
            return
        }
        for (r in 1..rounds) {
            openRound(r)
            if (r != rounds && roundRestMs > 0) {
                add(Phase.REST, roundRestMs, restKind = RestKind.ROUND, round = r)
            }
        }
    }

    /** One lap of open, self-paced stations with elastic rests between them. */
    private fun openRound(round: Int) {
        stations.forEachIndexed { i, st ->
            add(
                Phase.REPS, estimateMs(st, lib), open = true, gate = Gate.MANUAL,
                stationIndex = i, exerciseId = st.exerciseId, round = round, reps = st.prescribedReps,
            )
            if (i != stations.lastIndex && stationRestMs > 0) {
                add(Phase.REST, stationRestMs, restKind = RestKind.STATION, round = round)
            }
        }
    }

    /**
     * Drops trailing REST segments: dead time between the last effort and 完了 (§B.2).
     *
     * Also drops an EMOM's final remainder, which is the same dead time wearing a grid's clothes —
     * the protocol's last minute is over when its last rep is.
     */
    fun stripTrailingRest() {
        while (out.isNotEmpty() && out.last().phase == Phase.REST) out.removeAt(out.lastIndex)
    }

    fun build(): Timeline {
        // §B.6 invariant 6, stated as a throw rather than an empty timeline the player would render
        // as an instantly-complete session.
        require(out.any { it.phase != Phase.PREPARE }) {
            "engine ${routine.engine} produced no work for routine ${routine.routineId}"
        }
        return Timeline(
            routineId = routine.routineId,
            engine = routine.engine,
            segments = out.toList(),
            totalRounds = laidRounds ?: rounds,
            stationsPerRound = if (routine.engine == Engine.FIXED_SETS && !step?.sets.isNullOrEmpty()) {
                step!!.sets.size
            } else {
                stations.size
            },
            capMs = capMs,
            extensible = extensible,
            prepareMs = routine.prepareSeconds * 1000L,
        )
    }
}
