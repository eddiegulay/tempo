package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.ProgressionSet
import io.eddiegulay.tempo.gym.ProgressionStep
import io.eddiegulay.tempo.gym.StepShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven protocols, as laid out cells.
 *
 * A compiler bug here is not a rendering bug: the timeline is what gets *recorded*, so a circuit that
 * compiles one rest too many produces a session whose numbers are wrong forever, and an EMOM whose
 * grid can slide is not an EMOM. Every case below is a shape the built-in catalogue will actually
 * ask for, and `03-player.md` §B.6's seven invariants are each pinned by name.
 */
class TimelineCompilerTest {

    // ─── §B.3.1 INTERVAL_CIRCUIT ────────────────────────────────────────────────────────────────

    @Test
    fun `a circuit boxes every station and rests between them but never after the last`() {
        val tl = compiledCircuit(rounds = 1)

        assertEquals(
            listOf(Phase.PREPARE, Phase.WORK, Phase.REST, Phase.WORK),
            tl.segments.map { it.phase },
        )
        assertEquals(listOf(0L, 5_000L, 35_000L, 45_000L), tl.segments.map { it.startMs })
        assertEquals(75_000L, tl.plannedTotalMs)
        // Time-boxed and auto-advancing: nothing in a circuit waits for a tap.
        assertTrue(tl.segments.drop(1).none { it.open })
        assertTrue(tl.segments.all { it.gate == Gate.AUTO })
    }

    @Test
    fun `a second round gets a round rest before it and none after it`() {
        val tl = compiledCircuit(rounds = 2)

        assertEquals(
            listOf(RestKind.STATION, RestKind.ROUND, RestKind.STATION),
            tl.segments.mapNotNull { it.restKind },
        )
        assertEquals(2, tl.totalRounds)
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2), tl.segments.drop(1).map { it.round })
    }

    @Test
    fun `a rep-prescribed station in a circuit still gets a fixed box and keeps its reps advisory`() {
        // §B.3.1. 七分間 asks for as many as you manage in thirty seconds, not for twenty however
        // long they take — so the segment is WORK, closed by the clock, with the count carried along.
        val tl = compile(
            snapshot(Engine.INTERVAL_CIRCUIT, listOf(station(0, "a", reps = 15)), prepareSeconds = 0),
            ScalingTier.RX,
            lib(exercise("a", secondsPerRep = 2.0)),
        )

        assertEquals(Phase.WORK, tl.segments.single().phase)
        assertFalse(tl.segments.single().open)
        assertEquals(30_000L, tl.segments.single().plannedMs) // 15 × 2.0s
        assertEquals(15, tl.segments.single().prescribedReps)
    }

    // ─── §B.3.2 AMRAP ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `an amrap materialises ahead of the cap and stays extensible`() {
        val tl = compile(
            snapshot(
                Engine.AMRAP,
                listOf(station(0, "a", reps = 10), station(1, "b", reps = 5)),
                prepareSeconds = 0,
                timeCapSeconds = 60,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        // Round estimate 30s against a 60s cap: (60/30) + 2 = 4 rounds laid, and every station open.
        assertEquals(4, tl.totalRounds)
        assertEquals(8, tl.segments.size)
        assertTrue(tl.segments.all { it.open && it.gate == Gate.MANUAL })
        assertTrue(tl.extensible)
        assertEquals(60_000L, tl.capMs)
    }

    @Test
    fun `a cap shorter than one round still materialises three`() {
        // The floor exists so a user faster than the estimate cannot reach the end of the compiled
        // list; running out of rounds mid-cap is the one thing an AMRAP screen may never show.
        val tl = compile(
            snapshot(Engine.AMRAP, listOf(station(0, "a", reps = 30)), prepareSeconds = 0, timeCapSeconds = 10),
            ScalingTier.RX,
            circuitLib,
        )

        assertEquals(3, tl.totalRounds)
    }

    // ─── §B.3.3 FOR_TIME ────────────────────────────────────────────────────────────────────────

    @Test
    fun `for time is open work with no rest at all, even when the routine carries one`() {
        // §C.3's row says "none", and it wins over the routine's own rest columns: a for-time score
        // that included a scheduled rest would not be comparable to anyone else's.
        val tl = compile(
            snapshot(
                Engine.FOR_TIME,
                listOf(station(0, "a", reps = 10), station(1, "b", reps = 10)),
                prepareSeconds = 0,
                restBetweenStations = 15,
                restBetweenRounds = 60,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        assertTrue(tl.segments.none { it.phase == Phase.REST })
        assertTrue(tl.segments.all { it.open })
        assertNull(tl.capMs)
    }

    // ─── §B.3.4 FOR_TIME_WITH_REST ──────────────────────────────────────────────────────────────

    @Test
    fun `barbara's between-round rest is mandated and is not skippable`() {
        val tl = compile(
            snapshot(
                Engine.FOR_TIME_WITH_REST,
                listOf(station(0, "a", reps = 20)),
                rounds = 3,
                prepareSeconds = 0,
                restBetweenRounds = 180,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        val rests = tl.segments.filter { it.phase == Phase.REST }
        assertEquals(2, rests.size)
        assertTrue(rests.all { it.restKind == RestKind.MANDATED && it.plannedMs == 180_000L })
        assertTrue(rests.none { canSkip(it.restKind) })
        assertTrue(rests.none { canExtend(it.restKind) })
    }

    // ─── §B.3.5 / §B.3.6 EMOM ───────────────────────────────────────────────────────────────────

    @Test
    fun `an emom lays one anchored cell per station plus a remainder, and re-anchors each minute`() {
        val tl = emom(rounds = 3)

        // Three minutes, the last remainder stripped as trailing rest.
        assertEquals(
            listOf(Phase.REPS, Phase.REST, Phase.REPS, Phase.REST, Phase.REPS),
            tl.segments.map { it.phase },
        )
        assertTrue(tl.segments.all { it.anchored })
        assertEquals(listOf(0L, 60_000L, 60_000L, 120_000L, 120_000L), tl.segments.map { it.startMs })
        assertEquals(
            listOf(60_000L, 60_000L, 120_000L, 120_000L, 180_000L),
            tl.segments.map { it.windowEndMs },
        )
        assertEquals(180_000L, tl.capMs)
    }

    @Test
    fun `every emom work cell is open and gated at the cap, and the remainder is not`() {
        val tl = emom(rounds = 2)

        val work = tl.segments.filter { it.phase == Phase.REPS }
        assertTrue(work.all { it.open && it.gate == Gate.AUTO_AT_CAP })
        val remainder = tl.segments.single { it.phase == Phase.REST }
        assertFalse(remainder.open)
        assertEquals(RestKind.EMOM_REMAINDER, remainder.restKind)
        // Zero-span at compile time: nothing has been earned yet. §B.6 invariant 4.
        assertEquals(0L, remainder.effectiveMs)
    }

    @Test
    fun `the ascending variant adds one rep per minute and the plain one does not`() {
        assertEquals(listOf(10, 10, 10), emom(rounds = 3).segments.filter { it.phase == Phase.REPS }
            .map { it.prescribedReps })
        assertEquals(
            listOf(10, 11, 12),
            emom(rounds = 3, ascending = true).segments.filter { it.phase == Phase.REPS }
                .map { it.prescribedReps },
        )
    }

    @Test
    fun `an emom with no interval refuses to compile rather than laying a zero-width grid`() {
        val bad = snapshot(Engine.EMOM, listOf(station(0, "a", reps = 10)), intervalSeconds = null)
        val thrown = runCatching { compile(bad, ScalingTier.RX, circuitLib) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    // ─── §B.3.7 FIXED_SETS ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a step table becomes one open set per row, in the table's order, all on one movement`() {
        // Recon Ron step 1 → 7 6 5 4 4 (§B.3.7). Five sets, four rests, nothing reordered and
        // nothing dropped — the compiler's half of §B.6 invariant 7.
        val step = reconRonStepOne()
        val tl = compile(
            snapshot(Engine.FIXED_SETS, listOf(station(0, "a", reps = 7)), prepareSeconds = 0),
            ScalingTier.RX,
            circuitLib,
            progressionStep = step,
        )

        assertEquals(
            listOf(7, null, 6, null, 5, null, 4, null, 4),
            tl.segments.map { it.prescribedReps },
        )
        assertTrue(tl.segments.filter { it.phase == Phase.REPS }.all { it.open && it.gate == Gate.MANUAL })
        assertTrue(tl.segments.filter { it.phase == Phase.REST }.all { it.plannedMs == 90_000L })
        assertTrue(tl.segments.all { it.exerciseId == "a" || it.phase == Phase.REST })
    }

    @Test
    fun `without a step table each station is a set`() {
        val tl = compile(
            snapshot(
                Engine.FIXED_SETS,
                listOf(station(0, "a", reps = 10), station(1, "b", reps = 10)),
                prepareSeconds = 0,
                restBetweenStations = 60,
            ),
            ScalingTier.RX,
            circuitLib,
        )

        assertEquals(listOf(Phase.REPS, Phase.REST, Phase.REPS), tl.segments.map { it.phase })
    }

    // ─── §B.6 the invariants ────────────────────────────────────────────────────────────────────

    @Test
    fun `invariant 1 - ordinal equals list index, after compiling and after every mutation`() {
        val tl = compiledCircuit(rounds = 2)
        assertTrue(tl.segments.withIndex().all { (i, s) -> s.ordinal == i })

        val mutated = tl.close(1, 21_000).extend(2, 20_000).reopen(1)
        assertTrue(mutated.segments.withIndex().all { (i, s) -> s.ordinal == i })
    }

    @Test
    fun `invariant 2 - each segment starts where the last one ended, except across a grid boundary`() {
        val elastic = compiledCircuit(rounds = 2)
        elastic.segments.zipWithNext { a, b -> assertEquals(a.endMs, b.startMs) }

        // The EMOM is the exception the invariant names: minute two's cell starts at the boundary,
        // not where the remainder before it happened to end.
        val grid = emom(rounds = 2)
        val head = grid.segments.first { it.round == 2 }
        assertEquals(head.windowStartMs, head.startMs)
    }

    @Test
    fun `invariant 3 - the last segment is work, never a rest left hanging after it`() {
        val withTrailingRoundRest = compiledCircuit(rounds = 3)
        assertEquals(Phase.WORK, withTrailingRoundRest.segments.last().phase)
        assertEquals(Phase.REPS, emom(rounds = 3).segments.last().phase)
        assertEquals(Phase.REPS, compile(
            snapshot(Engine.FIXED_SETS, listOf(station(0, "a", reps = 5)), prepareSeconds = 0),
            ScalingTier.RX,
            circuitLib,
            progressionStep = reconRonStepOne(),
        ).segments.last().phase)
    }

    @Test
    fun `invariant 4 - stateAt never lands on a zero-span emom remainder`() {
        val tl = emom(rounds = 2)
        val remainder = tl.segments.single { it.restKind == RestKind.EMOM_REMAINDER }
        assertTrue(remainder.vestigial)

        // The boundary instant belongs to the minute that is starting, not to the remainder that
        // never happened.
        assertNotEquals(remainder.ordinal, tl.close(0, 60_000).stateAt(60_000).ordinal)
    }

    @Test
    fun `invariant 5 - the same routine compiles to the same timeline and the same hash`() {
        val a = compiledCircuit(rounds = 2)
        val b = compiledCircuit(rounds = 2)

        assertEquals(a.segments, b.segments)
        assertEquals(a.hash(), b.hash())
        // A different shape must not collide, or compiled_hash cannot detect drift on resume.
        assertNotEquals(a.hash(), compiledCircuit(rounds = 3).hash())
    }

    @Test
    fun `invariant 5 - the hash ignores everything the session writes onto the timeline`() {
        // §E.2 compares the hash mid-session, against a timeline whose segments have been closed by
        // replay. A hash that moved when a segment closed would report drift on every recovery.
        val tl = compiledCircuit(rounds = 2)

        assertEquals(tl.hash(), tl.close(1, 21_000, actualReps = 9, skipped = true).hash())
        assertEquals(tl.hash(), tl.extend(2, 20_000).hash())
    }

    @Test
    fun `invariant 6 - a routine with no stations fails at compile, not mid-session`() {
        val thrown = runCatching {
            compile(snapshot(Engine.INTERVAL_CIRCUIT, emptyList()), ScalingTier.RX, circuitLib)
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `prepare emits no segment at all when the setting is zero`() {
        // §A PREPARE edge case 5: a zero-length 支度 divides by zero in the ensō sweep.
        val tl = compiledCircuit(prepareSeconds = 0)

        assertTrue(tl.segments.none { it.phase == Phase.PREPARE })
        assertEquals(0L, tl.prepareMs)
        assertEquals(0, tl.firstRealSegment)
    }

    @Test
    fun `a limit-yourself station gets the documented pacing estimate and stays open`() {
        val tl = compile(
            snapshot(Engine.FOR_TIME, listOf(station(0, "a")), prepareSeconds = 0),
            ScalingTier.RX,
            circuitLib,
        )

        assertEquals(DEFAULT_MAX_EFFORT_ESTIMATE_MS, tl.segments.single().plannedMs)
        assertTrue(tl.segments.single().open)
    }

    @Test
    fun `an exercise the catalogue no longer knows is refused rather than compiled around`() {
        val thrown = runCatching {
            compile(
                snapshot(Engine.FOR_TIME, listOf(station(0, "gone", reps = 5)), prepareSeconds = 0),
                ScalingTier.RX,
                circuitLib,
            )
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }
}

/**
 * Recon Ron's first rung — `03-player.md` §B.3.7 quotes it as `[7,6,5,4,4]`, `02-data.md` §F.2 gives
 * the programme `rest_sec = 90`, and 24 + 2×1 = 26 as §B.6 invariant 7 requires. Nothing here is a
 * number this test made up; the table itself is Track A's to seed and to regression-test.
 */
internal fun reconRonStepOne(): ProgressionStep = ProgressionStep(
    stepIndex = 1,
    labelJa = null,
    shape = StepShape.FIXED,
    totalReps = 26,
    restSeconds = 90,
    noteJa = null,
    sets = listOf(7, 6, 5, 4, 4).mapIndexed { i, reps -> ProgressionSet(i, reps, null) },
)
