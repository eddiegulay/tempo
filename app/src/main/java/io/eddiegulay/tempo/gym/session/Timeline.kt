package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.RoutineStation
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.i18n.Strings

/*
 * The compiled session and the pure function that reads it — `03-player.md` §B.
 *
 * The whole player rests on one bargain: **a precompiled `List<Segment>` plus a pure `stateAt`
 * derived from a monotonic elapsed time.** The 50ms render tick never advances anything; it re-reads
 * `stateAt(clock.elapsedMs())` and hands the result to Compose. Rotation, backgrounding, deep sleep,
 * process death and "seek to station four" then all fall out of one code path instead of four, and
 * `03-player.md` §E.2's cold-start reconstruction is literally the same call as a live frame.
 *
 * *Rejected* — a ticking state machine that mutates `currentSegment` on a timer, which is how this
 * component is usually written. It has to invent an answer for every way time can skip: doze, a
 * backgrounded hour, a resumed session, a rewind. Each answer is a separate branch and each branch is
 * a separate bug, and none of them are testable without a device.
 *
 * There are no Android imports in this file, and there must not be. `SystemClock.elapsedRealtime()`
 * enters at exactly one place — the ViewModel's tick — and everything below this line is arithmetic
 * a JUnit test can pin.
 */

// ─── Vocabulary ─────────────────────────────────────────────────────────────────────────────────

/**
 * What a rest is *for*, which is the only thing that decides whether it can be skipped or grown.
 *
 * [MANDATED] and [EMOM_REMAINDER] are the two the player refuses to skip, for opposite reasons.
 * Barbara's three minutes are part of the prescription and skipping them makes the resulting time
 * incomparable to anyone else's Barbara; an EMOM remainder is not a rest the user was given at all,
 * it is the part of the minute they earned by finishing early, and the grid — not the user — decides
 * when it ends.
 *
 * *Rejected* — a bare `mandatory: Boolean` on [Segment]. The two cases render differently (決められた休息
 * shows its disabled controls, 残り hides them outright, `03-player.md` §A REST states 4 and 5), so a
 * boolean would immediately need a second boolean beside it.
 */
enum class RestKind { STATION, ROUND, MANDATED, EMOM_REMAINDER }

/**
 * How a segment ends.
 *
 * [AUTO] ends itself at `plannedMs`. [MANUAL] ends only on 済 — the estimate is a pacer and is never
 * authoritative. [AUTO_AT_CAP] is open like MANUAL, but a hard boundary fires the engine's fail
 * policy: an EMOM minute closing over unfinished work is a miss, not an advance.
 *
 * The gate compiled onto a segment is the *routine's* gate. The 目安で自動的に進む preference upgrades
 * MANUAL to AUTO at run time via [effectiveGate] and deliberately never reaches [compile], because a
 * preference inside the compiler would break §B.6 invariant 5 — the same routine would compile to two
 * different timelines and `session.compiled_hash` would stop meaning anything.
 */
enum class Gate { AUTO, MANUAL, AUTO_AT_CAP }

/**
 * やさしい / 基本 / きつい — which scaling of a routine is being performed.
 *
 * Carried through [compile] and onto the session row, but it rewrites **nothing**: `02-data.md` §A
 * has no per-tier override table, and `04-library-records.md` §1 makes the scaled tiers *separate
 * stored routines* joined by `scaled_from_routine_id`. §B.2's sketch calls an `applyTier` over a
 * `routine.tierOverrides` map that no schema in this feature has. Picking a tier therefore picks a
 * different routine id upstream, and by the time a snapshot reaches the compiler the scaling has
 * already happened.
 *
 * Not to be confused with `Tier` (入門/中級/上級), which is a difficulty band printed on a card.
 *
 * **The label left the constructor** (`.planning/i18n/DECISIONS.md` §L3, which applies the same ruling
 * to all thirteen label-carrying enums): a constructor argument is fixed at class-init and cannot be
 * re-resolved when the user flips the language, so a stored word would be the one thing on screen that
 * did not change. What reaches `session.tier` is `name` — `EASY`/`RX`/`HARD`, ASCII — and that is
 * unchanged, so nothing here is a stored value and there is no migration. See [ScalingTier.label].
 */
enum class ScalingTier {
    EASY,
    RX,
    HARD,
}

/** やさしい / 基本 / きつい, resolved at draw time. */
fun ScalingTier.label(strings: Strings): String = when (this) {
    ScalingTier.EASY -> strings.gymSession.scalingTierEasy
    ScalingTier.RX -> strings.gymSession.scalingTierRx
    ScalingTier.HARD -> strings.gymSession.scalingTierHard
}

/**
 * What a station asks for, in the form the compiler needs.
 *
 * The store's `Measure` + two nullable columns say the same thing, but say it in a shape where
 * "REPS with a null count" is representable. Mapping into this sealed type at the compiler's front
 * door means every engine below can pattern-match without a `!!`.
 */
sealed interface Prescription {
    data class Reps(val count: Int) : Prescription
    data class Duration(val seconds: Int) : Prescription
    data object MaxEffort : Prescription
}

/**
 * The station's prescription, degrading a schema-impossible row to [Prescription.MaxEffort].
 *
 * The CHECK constraint promises a rep count on a REPS station, so a null here is a corrupt or
 * hand-edited row. The honest degradation is a self-paced segment the user closes with 済 — **not** a
 * zero-length one, which divides by zero in the ensō sweep and is the exact failure `03-player.md`
 * §A PREPARE edge case 5 makes the compiler avoid.
 */
fun RoutineStation.prescription(): Prescription = when (measure) {
    Measure.REPS -> prescribedReps?.let { Prescription.Reps(it) } ?: Prescription.MaxEffort
    Measure.DURATION -> prescribedSeconds?.let { Prescription.Duration(it) } ?: Prescription.MaxEffort
    Measure.MAX_EFFORT -> Prescription.MaxEffort
}

// ─── The compiled cell ──────────────────────────────────────────────────────────────────────────

/**
 * One cell of the compiled session.
 *
 * `open` — self-paced; ends only when the user taps 済 (or a cap fires). [plannedMs] is a pacing
 * estimate and is **not** authoritative; an open segment absorbs all elapsed time past its start
 * until it is closed, which is why [Timeline.stateAt] may never search past the frontier.
 *
 * `anchored` — belongs to a fixed grid (an EMOM minute). Anchored cells never let the grid move:
 * closing one early grows the remainder inside the same window instead of shifting the whole session
 * earlier. Getting this distinction wrong is precisely what makes an EMOM wrong — the protocol is the
 * grid, and a grid that slides with the user's pace is a circuit wearing an EMOM's name.
 *
 * Three fields diverge from §B.1's declaration, all deliberately.
 *
 * 1. **`anchored: Boolean` became [windowStartMs]/[windowEndMs].** §B.4 requires a closed anchored
 *    cell to hand its remaining time to the following remainder-rest, and a boolean cannot say *where
 *    the window ends*. The boolean survives as the derived [anchored] so every §B call site reads
 *    unchanged.
 * 2. **[actualReps] and [skipped] were added.** `Timeline.close` is specified to take both, and a
 *    type that accepts data it silently discards is a trap. `03-player.md` §E.2 replays a resumed
 *    session through `close`, and the resumed player has to render とばした for a skipped station
 *    without going back to the database for it.
 * 3. **[effectiveMs] is `actualMs ?: (plannedMs + addedMs)`, not §B.1's `(actualMs ?: plannedMs) +
 *    addedMs`.** The spec's expression double-counts ＋二十秒: a rest extended by twenty seconds and
 *    then run to expiry has a *measured* duration that already contains them, so adding `addedMs` on
 *    top makes a 15s rest 55s long and pushes every downstream offset out by the same amount. Added
 *    time extends the **plan**; once the real duration is known it supersedes the plan entirely.
 */
data class Segment(
    val ordinal: Int,
    val phase: Phase,
    val startMs: Long,
    val plannedMs: Long,
    val open: Boolean = false,
    val gate: Gate = Gate.AUTO,
    val stationIndex: Int? = null,
    val exerciseId: String? = null,
    val round: Int = 1,
    val prescribedReps: Int? = null,
    val restKind: RestKind? = null,
    /** Start of the grid window this cell belongs to; null for an elastic cell. */
    val windowStartMs: Long? = null,
    /** End of that window — the boundary that fires the fail policy. Null for an elastic cell. */
    val windowEndMs: Long? = null,
    val closed: Boolean = false,
    val actualMs: Long? = null,
    val addedMs: Long = 0,
    val actualReps: Int? = null,
    val skipped: Boolean = false,
) {
    val anchored: Boolean get() = windowEndMs != null

    val effectiveMs: Long get() = actualMs ?: (plannedMs + addedMs)

    val endMs: Long get() = startMs + effectiveMs

    /** A cell with no span is never rendered and [Timeline.stateAt] never lands on it (start == end). */
    val vestigial: Boolean get() = effectiveMs <= 0L
}

/**
 * A whole session, compiled once and then only ever re-flowed.
 *
 * [segments] is authoritative for structure; the clock is authoritative for time; nothing else exists.
 * There is no `currentPhase` field here and there must never be one — `03-player.md` §E.2's whole
 * argument is that the phase is *derived*, so process death cannot resurrect a second opinion about
 * where the session is.
 */
data class Timeline(
    val routineId: String,
    val engine: Engine,
    val segments: List<Segment>,
    val totalRounds: Int,
    val stationsPerRound: Int,
    /** AMRAP cap, or an EMOM grid's total length. Null when the engine has no clock ceiling. */
    val capMs: Long? = null,
    /** AMRAP: rounds are materialised ahead and appended on demand. */
    val extensible: Boolean = false,
    val prepareMs: Long = 5_000,
) {
    init {
        // §B.6 invariant 6. An engine that emitted nothing is a compiler bug, and the only place it
        // can be caught before a live session is here.
        require(segments.isNotEmpty()) { "timeline for $routineId has no segments" }
    }

    /** The first open segment not yet closed; everything after it is provisional. */
    val frontier: Int get() = segments.indexOfFirst { it.open && !it.closed }

    val plannedTotalMs: Long get() = segments.lastOrNull()?.endMs ?: 0L

    /**
     * The first segment ◁ may step back to. 支度 is not one: stepping back into it would restart a
     * countdown the user has already sat through, and `03-player.md` §C.4 forbids it outright.
     */
    val firstRealSegment: Int get() = if (segments.first().phase == Phase.PREPARE) 1 else 0

    /**
     * A content address of the compiled **shape**, deliberately blind to everything a running session
     * does to it: `closed`, `actualMs`, `addedMs`, `actualReps`, `skipped`, and — less obviously —
     * `startMs`, plus an anchored cell's `plannedMs`. Those last two move on every re-flow, and
     * `session.compiled_hash` is compared *mid-session* on resume (§E.2), so a hash that changed when
     * a segment closed would report drift on every recovery. Offsets are derivable from the durations
     * anyway; nothing is lost by leaving them out.
     *
     * Hand-rolled rather than `hashCode()`: `String.hashCode` is contractually stable across JVMs and
     * releases, but a data class's generated hash is not something to store in a database.
     */
    fun hash(): Int {
        var h = routineId.hashCode()
        h = 31 * h + engine.name.hashCode()
        h = 31 * h + totalRounds
        h = 31 * h + stationsPerRound
        h = 31 * h + (capMs ?: 0L).hashCode()
        h = 31 * h + prepareMs.hashCode()
        for (s in segments) {
            h = 31 * h + s.ordinal
            h = 31 * h + s.phase.name.hashCode()
            // An anchored cell's planned span is its share of a window and is rewritten whenever the
            // cell before it closes; the window itself is the invariant, and it is hashed below.
            h = 31 * h + if (s.anchored) 0 else s.plannedMs.hashCode()
            h = 31 * h + s.gate.name.hashCode()
            h = 31 * h + if (s.open) 1 else 0
            h = 31 * h + (s.stationIndex ?: -1)
            h = 31 * h + (s.exerciseId?.hashCode() ?: 0)
            h = 31 * h + s.round
            h = 31 * h + (s.prescribedReps ?: -1)
            h = 31 * h + (s.restKind?.name?.hashCode() ?: 0)
            h = 31 * h + (s.windowStartMs ?: -1L).hashCode()
            h = 31 * h + (s.windowEndMs ?: -1L).hashCode()
        }
        return h
    }
}

// ─── Mutation: close, extend, reopen ────────────────────────────────────────────────────────────

/**
 * Re-flows every segment after [index] against a rewritten one at [index].
 *
 * Two rules, and the second is the entire EMOM correctness story.
 *
 * *Elastic* cells shift by the delta — a set that took twelve seconds longer moves the rest of the
 * session twelve seconds later, which is honest because the clock is real and the user is still doing
 * the work.
 *
 * *Anchored* cells belong to a grid. The **head** of each window (its first cell) is pinned to
 * [Segment.windowStartMs] and never moves no matter what happened before it; the cells behind it slide
 * within the window and re-span to whatever is left of it. That is how a station finished at 0:20
 * hands the next station forty seconds and the remainder-rest whatever survives, while 1:00 stays 1:00.
 *
 * *Rejected* — recomputing every `startMs` from scratch on each frame instead of on each mutation.
 * It is the same arithmetic, sixty times a second, over a list that changed twice a minute.
 */
private fun Timeline.reflowFrom(index: Int, replacement: Segment): Timeline {
    val out = ArrayList<Segment>(segments.size)
    for (i in 0 until index) out += segments[i]
    out += replacement
    var cursor = replacement.endMs
    for (i in index + 1 until segments.size) {
        val s = segments[i]
        val head = s.anchored && s.windowStartMs != null &&
            (i == 0 || segments[i - 1].windowStartMs != s.windowStartMs)
        val start = if (head) s.windowStartMs!! else cursor
        // An anchored cell that has not closed yet spans whatever is left of its window: that is what
        // makes the remainder-rest grow when the work before it finishes early, with no special case
        // for "the last station of the minute".
        val planned = if (s.anchored && !s.closed) {
            (s.windowEndMs!! - start).coerceAtLeast(0L)
        } else {
            s.plannedMs
        }
        val moved = s.copy(startMs = start, plannedMs = planned)
        out += moved
        cursor = moved.endMs
    }
    return copy(segments = out)
}

/**
 * Closes the open segment at [ordinal] with its true duration and re-flows everything after it.
 *
 * [actualMs] is the measured elapsed time in the segment, never the estimate — that difference is the
 * whole reason a rep slide exists (`03-player.md` §A REPS, data out).
 *
 * Also used to close a *fixed* segment early, which is what ▷ does: a skipped station shortens to the
 * time actually spent in it and everything downstream moves up. `markClosed` is the same operation
 * named for the replay path, which reaches it from a stored row rather than from a tap.
 */
fun Timeline.close(
    ordinal: Int,
    actualMs: Long,
    actualReps: Int? = null,
    skipped: Boolean = false,
): Timeline {
    val s = segments[ordinal]
    return reflowFrom(
        ordinal,
        s.copy(
            closed = true,
            actualMs = actualMs.coerceAtLeast(0L),
            actualReps = actualReps,
            skipped = skipped,
        ),
    )
}

/**
 * Closes a segment from a **persisted** result — `03-player.md` §E.2's replay, which reaches segments
 * that were never open (an auto-advanced interval) and must restore the added time with them.
 */
fun Timeline.markClosed(ordinal: Int, actualMs: Long, addedMs: Long): Timeline {
    val s = segments[ordinal]
    return reflowFrom(
        ordinal,
        s.copy(closed = true, actualMs = actualMs.coerceAtLeast(0L), addedMs = addedMs),
    )
}

/**
 * ＋二十秒, or any planned-duration mutation of a segment not yet closed.
 *
 * **Anchored segments refuse**, and refuse silently by returning the timeline unchanged: growing a
 * cell inside a fixed grid would either overrun the minute or steal from the next one. The caller
 * asks [canExtend] first and renders the control disabled — `03-player.md` §A REST edge case 3 is
 * explicit that a control which vanishes teaches the user it was never there.
 *
 * A closed segment also refuses. §A REST edge case 1: when the frontier has already moved on, the tap
 * is dropped with a negative CLICK — never retroactively rewound into a segment that is now history.
 */
fun Timeline.extend(ordinal: Int, deltaMs: Long): Timeline {
    val s = segments[ordinal]
    if (s.anchored || s.closed || deltaMs == 0L) return this
    return reflowFrom(ordinal, s.copy(addedMs = (s.addedMs + deltaMs).coerceAtLeast(0L)))
}

/**
 * The exact inverse of [close], for ◁◁.
 *
 * Restores the segment to its compiled state and lets the re-flow un-shift everything behind it by the
 * same delta, so `close(o, x)` then `reopen(o)` is the identity. Stepping back also **deletes** the
 * stored result (`03-player.md` §C.4) — this call only undoes the geometry; the row is the caller's.
 */
fun Timeline.reopen(ordinal: Int): Timeline {
    val s = segments[ordinal]
    return reflowFrom(
        ordinal,
        s.copy(closed = false, actualMs = null, actualReps = null, skipped = false),
    )
}

/**
 * Replays persisted results onto a freshly compiled timeline — `03-player.md` §E.2 step 2, the half of
 * cold-start recovery that moves the frontier back to where the user left it.
 *
 * Out-of-range ordinals are dropped rather than thrown on. The real guard against a drifted timeline
 * is the `compiled_hash` comparison §E.2 does *before* calling this; a crash on the cold-start path
 * would turn a recoverable session into an uninstallable app.
 */
fun Timeline.replay(results: List<SegmentResult>): Timeline =
    results.asSequence()
        .filter { it.ordinal in segments.indices }
        .sortedBy { it.ordinal }
        .fold(this) { tl, r ->
            if (tl.segments[r.ordinal].open) {
                tl.close(r.ordinal, r.actualMs, r.actualReps, r.skipped)
            } else {
                tl.markClosed(r.ordinal, r.actualMs, r.addedMs)
            }
        }

// ─── stateAt ────────────────────────────────────────────────────────────────────────────────────

/**
 * Everything one frame of the player needs, and nothing it has to ask a second question for.
 *
 * [remainingMs] goes negative on an open segment past its estimate; [overrunMs] is the same fact as a
 * positive number because the 目安 line prints it. Both exist because overrun is **not** a failure and
 * the UI must be able to say so without an `abs()` at the call site.
 */
data class PlayerState(
    val ordinal: Int,
    val segment: Segment,
    val elapsedInSegmentMs: Long,
    val remainingMs: Long,
    val overrunMs: Long,
    /** 1f full → 0f empty, clamped. Drives the ensō sweep. */
    val ringFraction: Float,
    /** The session hairline. */
    val sessionFraction: Float,
    val finished: Boolean,
    /** An AMRAP cap reached, or an EMOM window that closed with work still open. */
    val capExceeded: Boolean,
)

/**
 * The current state is a pure function of elapsed milliseconds. Ticks render; they never advance
 * state.
 *
 * The one non-linearity: an OPEN segment not yet closed absorbs all time beyond its start, so
 * downstream offsets are provisional until it closes and the search must never walk past the frontier.
 * A binary search that ignored the frontier would happily report station five while the user is still
 * holding station four's plank.
 */
fun Timeline.stateAt(elapsedMs: Long): PlayerState {
    val f = frontier
    var lo = 0
    var hi = segments.lastIndex
    var found = 0
    while (lo <= hi) { // binary search on startMs
        val mid = (lo + hi) ushr 1
        if (segments[mid].startMs <= elapsedMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    // The search keeps the LAST index whose start has passed, which is what makes §B.6 invariant 4
    // hold for free: a zero-span EMOM remainder shares its start with the cell after it, and the cell
    // after it wins. There is no special case for it here, and there must not be one.
    val idx = if (f >= 0) minOf(found, f) else found
    val seg = segments[idx]

    val inSeg = (elapsedMs - seg.startMs).coerceAtLeast(0)
    val planned = seg.effectiveMs
    val remaining = planned - inSeg
    val overrun = if (seg.open && !seg.closed && remaining < 0) -remaining else 0L

    val ring = when {
        seg.open && overrun > 0 -> 0f // held empty, never refilled and never pulsed
        planned <= 0 -> 0f
        // FOR_TIME counts up, so there is no duration to deplete against: the ring tracks stations.
        engine == Engine.FOR_TIME -> 1f - sessionProgress(idx)
        else -> (remaining.toFloat() / planned).coerceIn(0f, 1f)
    }

    val finished = f < 0 && idx == segments.lastIndex && remaining <= 0
    val capExceeded = when (engine) {
        Engine.AMRAP -> capMs != null && elapsedMs >= prepareMs + capMs
        Engine.EMOM, Engine.EMOM_ASCENDING ->
            seg.anchored && seg.open && !seg.closed && remaining <= 0
        else -> false
    }
    return PlayerState(idx, seg, inSeg, remaining, overrun, ring, sessionProgress(idx), finished, capExceeded)
}

/**
 * Hairline denominator: closed segments count their real duration, the rest their estimate.
 *
 * Recomputed from the **current** timeline every call, never from the compiled one. An overrun rep
 * slide grows the denominator as it grows the numerator, which is what keeps the hairline under 100%
 * (`03-player.md` §A REPS edge case 2).
 */
private fun Timeline.sessionProgress(idx: Int): Float {
    val total = segments.sumOf { it.effectiveMs }.coerceAtLeast(1)
    return (segments.take(idx).sumOf { it.effectiveMs }.toFloat() / total).coerceIn(0f, 1f)
}
