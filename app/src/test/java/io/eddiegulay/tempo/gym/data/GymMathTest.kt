package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.PersistedClock
import io.eddiegulay.tempo.gym.Resumability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The arithmetic the store freezes onto rows, and therefore the arithmetic that can never be corrected
 * after the fact.
 *
 * `volume_units`, `local_date`, `est_duration_sec` and `structural_hash` are all written once and read
 * for the life of the install. A mistake in any of them is not a bug that gets fixed in the next
 * release — it is a permanent misstatement about a day the user already lived, which is why these are
 * plain functions over plain values and why they are tested at the boundaries rather than in a screen.
 */
class GymMathTest {

    private val plank = exercise("plank", isometric = true, secondsPerRep = 10.0, difficulty = 1.0)
    private val pushup = exercise("pushup", isometric = false, secondsPerRep = 2.0, difficulty = 1.0)
    private val pullup = exercise("pullup", isometric = false, secondsPerRep = 3.0, difficulty = 2.0)

    // ── Weighted volume (§D.5) ──────────────────────────────────────────────────────────────────

    @Test
    fun `a thirty-second plank is three units, not zero`() {
        // Design §7.4's bare `Σ reps × coefficient` scores every hold as zero, because actual_reps is
        // NULL for a duration station — which makes 七分間, one third holds, read as half a workout.
        assertEquals(3.0, volumeUnits(plank, actualReps = null, actualDurationMs = 30_000), 1e-9)
    }

    @Test
    fun `rep work is reps times coefficient, exactly as specified`() {
        assertEquals(20.0, volumeUnits(pushup, actualReps = 20, actualDurationMs = 40_000), 1e-9)
        assertEquals(20.0, volumeUnits(pullup, actualReps = 10, actualDurationMs = 30_000), 1e-9)
    }

    @Test
    fun `a rep station with no recorded count scores nothing`() {
        // An unverified station is not a zero-effort station, but it is not a number either, and the
        // one thing it must never do is inherit its duration as though it were a hold.
        assertEquals(0.0, volumeUnits(pushup, actualReps = null, actualDurationMs = 60_000), 1e-9)
    }

    // ── Calendar bucketing (`00-plan.md` §2 row 14) ─────────────────────────────────────────────

    @Test
    fun `a session near midnight lands on the day it was performed`() {
        // This is the bug the whole stored-column decision exists to prevent. SQLite's date() is UTC,
        // so bucketing in SQL moves a session across the date line in both directions: an early
        // morning in Tokyo falls back to yesterday, a late evening in New York runs on to tomorrow.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val morning = ZonedDateTime.of(2026, 6, 17, 7, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 6, 17), localDateOf(morning, tokyo))
        assertEquals(LocalDate.of(2026, 6, 16), localDateOf(morning, ZoneId.of("UTC")))

        val newYork = ZoneId.of("America/New_York")
        val evening = ZonedDateTime.of(2026, 6, 17, 23, 30, 0, 0, newYork).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 6, 17), localDateOf(evening, newYork))
        assertEquals(LocalDate.of(2026, 6, 18), localDateOf(evening, ZoneId.of("UTC")))
    }

    @Test
    fun `a week always starts on the ISO Monday`() {
        // Never strftime('%W'), which is both UTC and non-ISO. A locale's own first-day-of-week would
        // make one user's weekly chart incomparable with another's.
        assertEquals(LocalDate.of(2026, 6, 15), weekStartOf(LocalDate.of(2026, 6, 21))) // Sunday
        assertEquals(LocalDate.of(2026, 6, 15), weekStartOf(LocalDate.of(2026, 6, 15))) // Monday
    }

    @Test
    fun `a stored date round-trips and a corrupt one is null rather than a default`() {
        val date = LocalDate.of(2026, 8, 13)
        assertEquals(10, storedDate(date).length) // the schema's CHECK asserts this width
        assertEquals(date, parseStoredDate(storedDate(date)))
        assertEquals(null, parseStoredDate("2026-13-99"))
        assertEquals(null, parseStoredDate(null))
    }

    // ── Version derivation (§A.3) ───────────────────────────────────────────────────────────────

    @Test
    fun `七分間's arithmetic is 475 seconds`() {
        // 12 × 30 + 11 × 10 + 5, and the prepare counts.
        val stations = List(12) { StationShape("plank", Measure.DURATION, null, 30, null) }
        assertEquals(
            475,
            estimatedDurationSeconds(stations, { 10.0 }, 1, null, 10, 60, 5),
        )
    }

    @Test
    fun `a trailing round rest is not counted`() {
        // タバタ: 8 × 20 + 7 × 10 + 5 = 235. Design §9's "4:00" counts the rest after the last round.
        val stations = listOf(StationShape("burpee", Measure.DURATION, null, 20, null))
        assertEquals(235, estimatedDurationSeconds(stations, { 4.0 }, 8, null, 0, 10, 5))
    }

    @Test
    fun `a max-effort station contributes no estimate at all`() {
        // An open-ended segment closed by 済 has no length, and guessing one would put a fabricated
        // number on a card.
        val stations = listOf(StationShape("pullup", Measure.MAX_EFFORT, null, null, null))
        assertEquals(5, estimatedDurationSeconds(stations, { 3.0 }, 1, null, 90, 0, 5))
    }

    @Test
    fun `a capped engine is its cap plus the prepare`() {
        val stations = listOf(StationShape("squat", Measure.REPS, 15, null, null))
        assertEquals(1205, estimatedDurationSeconds(stations, { 1.8 }, null, 1200, 0, 0, 5))
    }

    @Test
    fun `estimated reps count prescriptions and nothing else`() {
        // A hold is not "three reps" here — volumeUnits is what weighs holds, and this column is the
        // plain count of repetitions the routine asks for.
        val stations = listOf(
            StationShape("pushup", Measure.REPS, 10, null, null),
            StationShape("plank", Measure.DURATION, null, 30, null),
            StationShape("pullup", Measure.MAX_EFFORT, null, null, null),
        )
        assertEquals(30, estimatedTotalReps(stations, rounds = 3))
    }

    @Test
    fun `the structural hash is stable, and a rename is a new version`() {
        val stations = listOf(StationShape("pushup", Measure.REPS, 10, null, null))
        val a = structuralHashOf("シンディ", Engine.AMRAP, null, 1200, null, 0, 0, 5, null, stations)
        val b = structuralHashOf("シンディ", Engine.AMRAP, null, 1200, null, 0, 0, 5, null, stations)
        // A rename must produce a new version: `routine_version.name` is versioned precisely so that
        // renaming a routine does not rewrite what past sessions say they did.
        val renamed = structuralHashOf("シンディ改", Engine.AMRAP, null, 1200, null, 0, 0, 5, null, stations)
        assertEquals(a, b)
        assertNotEquals(a, renamed)
    }

    @Test
    fun `reordering stations changes the hash`() {
        // Order is the intervention in an alternating circuit, so two versions that differ only in it
        // are genuinely different routines.
        val one = StationShape("pushup", Measure.REPS, 10, null, null)
        val two = StationShape("squat", Measure.REPS, 15, null, null)
        assertNotEquals(
            structuralHashOf("x", Engine.AMRAP, null, 600, null, 0, 0, 5, null, listOf(one, two)),
            structuralHashOf("x", Engine.AMRAP, null, 600, null, 0, 0, 5, null, listOf(two, one)),
        )
    }

    // ── Personal records (§D.2) ─────────────────────────────────────────────────────────────────

    @Test
    fun `only best time is won by a smaller number`() {
        assertTrue(isBetter(BestMetric.BEST_TIME, 100.0, 0.0, best = 120.0 to 0.0))
        assertFalse(isBetter(BestMetric.BEST_TIME, 130.0, 0.0, best = 120.0 to 0.0))
        assertTrue(isBetter(BestMetric.MOST_ROUNDS, 18.0, 0.0, best = 17.0 to 0.0))
        assertFalse(isBetter(BestMetric.MOST_ROUNDS, 16.0, 0.0, best = 17.0 to 0.0))
    }

    @Test
    fun `a tie never displaces the incumbent`() {
        // You set the record the first time you hit it — ties break toward the earlier session.
        assertFalse(isBetter(BestMetric.BEST_TIME, 120.0, 0.0, best = 120.0 to 0.0))
        assertFalse(isBetter(BestMetric.MOST_ROUNDS, 17.0, 5.0, best = 17.0 to 5.0))
        // …unless the finer answer separates them, which is what the tiebreak is for.
        assertTrue(isBetter(BestMetric.MOST_ROUNDS, 17.0, 6.0, best = 17.0 to 5.0))
    }

    @Test
    fun `the first ever attempt is always a record`() {
        assertTrue(isBetter(BestMetric.MOST_VOLUME, 0.5, 0.0, best = null))
    }

    @Test
    fun `a round-counting engine also keeps its rep record`() {
        // `04` §3 edge case 6 puts 最高巡数 and 最高反復 side by side on an AMRAP, and a tile with no
        // row behind it renders nothing.
        assertEquals(
            listOf(BestMetric.MOST_ROUNDS, BestMetric.MOST_REPS),
            metricsFor(Engine.AMRAP, BestMetric.MOST_ROUNDS),
        )
        assertEquals(
            listOf(BestMetric.MOST_VOLUME),
            metricsFor(Engine.INTERVAL_CIRCUIT, BestMetric.MOST_VOLUME),
        )
    }

    @Test
    fun `a partial session takes no record, in any engine`() {
        // §D.2's opening sentence, `00-plan.md` §4.1 rule 2 and `04` §4 edge case 1 all say the same
        // thing: a PR requires complete = 1. The two that read as self-gating are the ones that were
        // wrong — MOST_VOLUME is the seeded metric for both 七分間 and タバタ, so quitting 七分間 after
        // four of twelve stations took 最高負荷 on the common path, and MOST_REPS did the same.
        BestMetric.entries.forEach { metric ->
            assertFalse(
                metric.name,
                prEligible(metric, complete = false, reachedTimeCap = false),
            )
            assertFalse(
                metric.name,
                prEligible(metric, complete = false, reachedTimeCap = true),
            )
        }
    }

    @Test
    fun `a completed session is eligible for everything except an uncapped round count`() {
        assertTrue(prEligible(BestMetric.BEST_TIME, complete = true, reachedTimeCap = false))
        assertTrue(prEligible(BestMetric.MOST_REPS, complete = true, reachedTimeCap = false))
        assertTrue(prEligible(BestMetric.MOST_VOLUME, complete = true, reachedTimeCap = false))
        assertTrue(prEligible(BestMetric.HIGHEST_STEP, complete = true, reachedTimeCap = false))
        // An AMRAP that stopped at minute twelve of a twenty-minute cap is not a rounds record, and
        // running the cap out is a stronger condition than finishing (§D.2).
        assertFalse(prEligible(BestMetric.MOST_ROUNDS, complete = true, reachedTimeCap = false))
        assertTrue(prEligible(BestMetric.MOST_ROUNDS, complete = true, reachedTimeCap = true))
    }

    // ── Resumability (§E.3) ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a reboot removes 続ける rather than disabling it`() {
        // elapsedRealtime resets to ~0 on reboot, so a stored elapsed origin from a previous boot
        // makes duration run backwards. Resuming would credit time the device spent switched off.
        val clock = clock(bootAnchor = 1_000_000L, startedAtElapsed = 60_000L)
        assertEquals(
            Resumability.REBOOTED,
            resumabilityOf(clock, lastWriteWallMs = 1_100_000L, hasResults = true,
                nowWall = 5_000_000L, nowElapsed = 30_000L),
        )
    }

    @Test
    fun `elapsed time going backwards within a boot is also a reboot`() {
        // The second, independent check: it catches the pathological case where a clock adjustment
        // happens to make the two boot anchors agree across a reboot.
        val clock = clock(bootAnchor = 1_000_000L, startedAtElapsed = 600_000L)
        assertEquals(
            Resumability.REBOOTED,
            resumabilityOf(clock, lastWriteWallMs = 1_600_000L, hasResults = true,
                nowWall = 1_100_000L, nowElapsed = 100_000L),
        )
    }

    @Test
    fun `four hours untouched is stale`() {
        val clock = clock(bootAnchor = 1_000_000L, startedAtElapsed = 60_000L)
        val nowElapsed = 4L * 60 * 60 * 1000 + 120_000L
        assertEquals(
            Resumability.STALE,
            resumabilityOf(clock, lastWriteWallMs = 1_060_000L, hasResults = true,
                nowWall = 1_000_000L + nowElapsed, nowElapsed = nowElapsed),
        )
    }

    @Test
    fun `a session with nothing recorded has nothing to save`() {
        // NOTHING_TO_SAVE removes 記録する and leaves 捨てる and a dismiss. Discarding it automatically
        // is tempting and wrong: the user should learn that this app never deletes silently.
        val clock = clock(bootAnchor = 1_000_000L, startedAtElapsed = 60_000L)
        assertEquals(
            Resumability.NOTHING_TO_SAVE,
            resumabilityOf(clock, lastWriteWallMs = 1_060_000L, hasResults = false,
                nowWall = 1_120_000L, nowElapsed = 120_000L),
        )
        assertEquals(
            Resumability.RESUMABLE,
            resumabilityOf(clock, lastWriteWallMs = 1_060_000L, hasResults = true,
                nowWall = 1_120_000L, nowElapsed = 120_000L),
        )
    }

    @Test
    fun `recovered time never credits the gap before a reboot`() {
        // Computed purely from stored values, with no reference to the current clock: the gap between
        // the last write and the reboot is unknowable, and 記録する uses this conservative figure.
        val clock = clock(bootAnchor = 1_000_000L, startedAtElapsed = 60_000L)
            .copy(pausedAccumulatedMs = 10_000L)
        assertEquals(230_000L, recoveredActiveMs(clock, lastWriteElapsedMs = 300_000L))
    }

    @Test
    fun `recovered time is never negative`() {
        val clock = clock(bootAnchor = 1_000_000L, startedAtElapsed = 600_000L)
        assertEquals(0L, recoveredActiveMs(clock, lastWriteElapsedMs = 60_000L))
    }

    private fun clock(bootAnchor: Long, startedAtElapsed: Long) = PersistedClock(
        startedAtElapsedMs = startedAtElapsed,
        pausedAccumulatedMs = 0L,
        pausedAtElapsedMs = null,
        pausedAtWallMs = null,
        bootAnchorMs = bootAnchor,
        anchorWallMs = bootAnchor + startedAtElapsed,
    )

    private fun exercise(
        id: String,
        isometric: Boolean,
        secondsPerRep: Double,
        difficulty: Double,
    ) = Exercise(
        id = id,
        nameJa = id,
        nameEn = id,
        pattern = Pattern.CORE,
        secondsPerRep = secondsPerRep,
        difficulty = difficulty,
        isIsometric = isometric,
        cue = null,
        ladderId = null,
        catalogVersion = 1,
        archived = false,
    )
}
