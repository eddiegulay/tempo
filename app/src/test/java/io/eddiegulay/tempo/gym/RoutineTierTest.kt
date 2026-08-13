package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The three boundaries `04-library-records.md` §3 pins, and the two cases where the heuristic declines
 * to guess at all.
 *
 * The boundaries are tested at values that are **exactly** representable, which is not fussiness: the
 * formula multiplies, and `0.75 × 1.2` is `0.8999999999999999` in a `Double`, so a test written with
 * those numbers would pass or fail on the arithmetic's rounding rather than on the rule. A twenty
 * minute routine doubles its mean difficulty exactly — multiplying a `Double` by two only moves the
 * exponent — so 0.45 lands on 0.9 and 0.7 lands on 1.4 with no error at all, and the `<` in the rule
 * is what is being measured.
 */
class RoutineTierTest {

    /** 1 + 1200/60/20 = 2, so a mean difficulty doubles. */
    private val twentyMinutes = 20 * 60

    @Test
    fun `below nine tenths is 入門`() {
        assertEquals(Tier.BEGINNER, derivedTier(null, listOf(0.44), twentyMinutes))
    }

    @Test
    fun `exactly nine tenths is already 中級`() {
        // `< 0.9 → 入門`: the boundary belongs to the band above it, which is the half of the rule an
        // implementation written with `<=` gets wrong on exactly one score.
        assertEquals(Tier.INTERMEDIATE, derivedTier(null, listOf(0.45), twentyMinutes))
    }

    @Test
    fun `exactly one point four is already 上級`() {
        assertEquals(Tier.ADVANCED, derivedTier(null, listOf(0.7), twentyMinutes))
        assertEquals(Tier.INTERMEDIATE, derivedTier(null, listOf(0.69), twentyMinutes))
    }

    @Test
    fun `duration is half the judgement, not a tiebreak`() {
        // The same movements for four minutes and for forty are not the same routine. 1.0 mean at
        // 4 min scores 1.2 (中級); at 40 min it scores 3.0 (上級).
        assertEquals(Tier.INTERMEDIATE, derivedTier(null, listOf(1.0), 4 * 60))
        assertEquals(Tier.ADVANCED, derivedTier(null, listOf(1.0), 40 * 60))
    }

    @Test
    fun `the mean is over the stations, so one hard movement does not carry a routine`() {
        // 2.5 beside three 0.4s means 0.925 — the routine is mostly easy and reads that way.
        assertEquals(
            Tier.INTERMEDIATE,
            derivedTier(null, listOf(2.5, 0.4, 0.4, 0.4), 10 * 60),
        )
    }

    @Test
    fun `a stored tier always wins`() {
        // 入門 stored on a built-in beats a score that would have said 上級. The seeded tier is
        // somebody's judgement; the formula is a heuristic, and a heuristic does not overrule a fact.
        assertEquals(Tier.BEGINNER, derivedTier(Tier.BEGINNER, listOf(2.5), 40 * 60))
    }

    @Test
    fun `no resolvable difficulty means no tier, not 入門`() {
        // A routine whose exercises the catalogue no longer knows is not an easy routine. `Tier?` is
        // nullable precisely so the subtitle can render 巡回 alone rather than 巡回 ・ 入門.
        assertNull(derivedTier(null, emptyList(), twentyMinutes))
    }

    @Test
    fun `a routine with no estimate still gets a tier from its movements`() {
        // デス・バイ estimates nothing (it runs to failure). The multiplier is then 1 and the mean
        // stands alone, which is a weaker guess but a legitimate one — dropping to null here would
        // hide the tier chip on the routines whose difficulty is least in doubt.
        assertEquals(Tier.ADVANCED, derivedTier(null, listOf(1.8), 0))
    }

    @Test
    fun `the snapshot form reads the resolved stations and the stored estimate`() {
        val snapshot = snapshot(
            difficulties = listOf(0.45, 0.45),
            estimatedDurationSeconds = twentyMinutes,
        )
        assertEquals(Tier.INTERMEDIATE, derivedTier(snapshot, stored = null))
        assertEquals(Tier.BEGINNER, derivedTier(snapshot, stored = Tier.BEGINNER))
    }

    @Test
    fun `an unknown exercise is skipped rather than scored as zero`() {
        // A station the catalogue cannot resolve contributes nothing to the mean. Counting it as 0.0
        // would drag a hard routine down to 入門 because of a *missing* row, which is the library
        // index's edge case 3 (a routine with an unknown station still lists) turning into a lie.
        val snapshot = snapshot(
            difficulties = listOf(0.7, 0.7),
            unknownStations = 2,
            estimatedDurationSeconds = twentyMinutes,
        )
        assertEquals(Tier.ADVANCED, derivedTier(snapshot, stored = null))
    }
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun exercise(difficulty: Double) = Exercise(
    id = "e" + difficulty,
    nameJa = "種目",
    nameEn = "exercise",
    pattern = Pattern.H_PUSH,
    secondsPerRep = 2.0,
    difficulty = difficulty,
    isIsometric = false,
    cue = null,
    ladderId = null,
    catalogVersion = 1,
    archived = false,
)

private fun snapshot(
    difficulties: List<Double>,
    estimatedDurationSeconds: Int,
    unknownStations: Int = 0,
): RoutineSnapshot {
    val known = difficulties.mapIndexed { index, difficulty ->
        RoutineStation(
            position = index,
            exerciseId = "e" + difficulty,
            exercise = exercise(difficulty),
            measure = Measure.REPS,
            prescribedReps = 10,
            prescribedSeconds = null,
            note = null,
        )
    }
    val unknown = List(unknownStations) { index ->
        RoutineStation(
            position = known.size + index,
            exerciseId = "gone",
            exercise = null,
            measure = Measure.REPS,
            prescribedReps = 10,
            prescribedSeconds = null,
            note = null,
        )
    }
    return RoutineSnapshot(
        versionId = 1,
        routineId = "r",
        versionNumber = 1,
        name = "型",
        engine = Engine.INTERVAL_CIRCUIT,
        rounds = 1,
        timeCapSeconds = null,
        intervalSeconds = null,
        restBetweenStations = 0,
        restBetweenRounds = 0,
        prepareSeconds = 5,
        progressionProgramId = null,
        primaryMetric = BestMetric.MOST_VOLUME,
        stationCount = known.size + unknown.size,
        estimatedDurationSeconds = estimatedDurationSeconds,
        estimatedTotalReps = 0,
        structuralHash = 0,
        createdAt = 0,
        stations = known + unknown,
    )
}
