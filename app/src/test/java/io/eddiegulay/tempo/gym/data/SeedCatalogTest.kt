package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.StepShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped catalogue is data, and data of this kind fails silently.
 *
 * A transposed digit in Recon Ron's table produces a programme that still runs, still renders and is
 * simply not the one LtCol Pasieka published — and nobody notices, because there is nothing on screen
 * to compare it against. `02-data.md` §A.5 puts `total_reps` on the row for exactly this reason: the
 * regression is a plain assertion rather than a hand-maintained fixture. The same applies to the ACSM
 * circuit, whose *order* is the intervention (design §9 forbids reordering it), and to the two derived
 * durations, which `00-plan.md` §2 rows 18 and 19 say to render as computed rather than as branded.
 */
class SeedCatalogTest {

    private val reconRon = SeedCatalog.programs.first { it.id == "p_recon_ron" }
    private val sevenMinute = SeedCatalog.routines.first { it.id == "r_seven_minute" }
    private val tabata = SeedCatalog.routines.first { it.id == "r_tabata" }

    // ── Recon Ron (§F.2, verified per DECISIONS.md §Q3) ─────────────────────────────────────────

    @Test
    fun `every Recon Ron step sums to its published total`() {
        // §A.5's assertion, as Kotlin: the five sets must add up to the number the source prints.
        reconRon.steps.forEach { step ->
            assertEquals(
                "step ${step.stepIndex}",
                step.totalReps,
                step.sets.sumOf { it.reps ?: 0 },
            )
        }
    }

    @Test
    fun `every Recon Ron total is 24 plus twice its step`() {
        // The second invariant, and the one that catches a whole row pasted into the wrong place —
        // a swap of two rows still sums correctly but breaks this immediately.
        reconRon.steps.forEach { step ->
            assertEquals("step ${step.stepIndex}", 24 + 2 * step.stepIndex, step.totalReps)
        }
    }

    @Test
    fun `Recon Ron is eighteen steps of five sets, ending at sixty`() {
        assertEquals(18, reconRon.stepCount)
        assertEquals(18, reconRon.steps.size)
        assertTrue(reconRon.steps.all { it.sets.size == 5 })
        assertEquals(26, reconRon.steps.first().totalReps)
        assertEquals(60, reconRon.steps.last().totalReps)
        // Every set carries ninety seconds; the routine's own rests are a separate matter (§F.5).
        assertTrue(reconRon.steps.all { it.restSeconds == 90 })
    }

    @Test
    fun `Recon Ron's sets never rise as the step advances`() {
        // The rejected planning-generated table lost the source chart's rotational increment. Within a
        // step the sets descend; across steps no set position ever goes backwards.
        reconRon.steps.forEach { step ->
            val reps = step.sets.map { it.reps ?: 0 }
            assertEquals("step ${step.stepIndex} descends", reps.sortedDescending(), reps)
        }
        reconRon.steps.zipWithNext { a, b ->
            a.sets.indices.forEach { i ->
                assertTrue(
                    "set ${i + 1} from step ${a.stepIndex} to ${b.stepIndex}",
                    (b.sets[i].reps ?: 0) >= (a.sets[i].reps ?: 0),
                )
            }
        }
    }

    // ── Armstrong and Pavel (§F.3, §F.4) ────────────────────────────────────────────────────────

    @Test
    fun `four of Armstrong's five days carry no rep table at all`() {
        // This is the schema earning its keep: a design that modelled progressions as a flat rep table
        // could represent one fifth of this programme.
        val armstrong = SeedCatalog.programs.first { it.id == "p_armstrong" }
        assertEquals(5, armstrong.steps.size)
        assertEquals(4, armstrong.steps.count { it.sets.isEmpty() })
        val gripDay = armstrong.steps.first { it.shape == StepShape.GRIP_ROTATION }
        assertEquals(9, gripDay.sets.size)
        // Three of each grip, and no counts — they resolve at run time from the day-1 maximum.
        assertEquals(3, gripDay.sets.groupBy { it.variant }.size)
        assertTrue(gripDay.sets.all { it.reps == null })
    }

    @Test
    fun `Pavel's programme is declared and deliberately empty`() {
        // The thirty daily ladders are structurally certain and numerically not, and design §9's
        // refusal to invent a RECONDO sequence applies identically. A programme row with no steps is
        // the honest encoding; no routine references it, so nothing can reach it.
        val fighter = SeedCatalog.programs.first { it.id == "p_fighter" }
        assertEquals(30, fighter.stepCount)
        assertTrue(fighter.steps.isEmpty())
        assertTrue(SeedCatalog.routines.none { it.progressionProgramId == "p_fighter" })
    }

    // ── The routines (§F.5) ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the ACSM circuit is twelve stations in the published order`() {
        // Design §9 forbids reordering: the sequence alternates total-body, lower, upper and core so
        // opposing groups recover while others work. Sorting this list would undo a decision made
        // about fatigue.
        assertEquals(
            listOf(
                "jumping_jack", "wall_sit", "pushup", "crunch", "step_up", "squat",
                "dip", "plank", "high_knees", "lunge", "pushup_rotation", "side_plank",
            ),
            sevenMinute.stations.map { it.exerciseId },
        )
        assertTrue(sevenMinute.stations.all { it.measure == Measure.DURATION && it.seconds == 30 })
    }

    @Test
    fun `the two exercises added for the ACSM circuit exist`() {
        // Without クランチ and 回旋腕立て伏せ the shipped circuit is not the published one
        // (`00-plan.md` §2 row 20).
        assertNotNull(SeedCatalog.exercises.firstOrNull { it.id == "crunch" })
        assertNotNull(SeedCatalog.exercises.firstOrNull { it.id == "pushup_rotation" })
    }

    @Test
    fun `七分間 computes to 475 seconds, not to seven minutes`() {
        // 12 × 30 + 11 × 10 + 5. "7-minute workout" is ACSM's own branding rounding, and
        // `00-plan.md` §2 row 18 says to render the arithmetic instead.
        assertEquals(475, sevenMinute.estimatedSeconds())
    }

    @Test
    fun `タバタ computes to 235 seconds, because a trailing rest is not work`() {
        // 8 × 20 + 7 × 10 + 5. Design §9's "4:00" counts the rest after the final round; ours does not.
        assertEquals(235, tabata.estimatedSeconds())
    }

    @Test
    fun `every seeded station names a movement the catalogue holds`() {
        // A missing id is a foreign-key failure on first launch — the seeder cannot start the app.
        val ids = SeedCatalog.exercises.map { it.id }.toSet()
        SeedCatalog.routines.forEach { routine ->
            routine.stations.forEach { station ->
                assertTrue("${routine.id}/${station.exerciseId}", station.exerciseId in ids)
            }
        }
    }

    @Test
    fun `only the FIXED_SETS routine carries a progression, and it carries one`() {
        // The schema's CHECK is `(engine = 'FIXED_SETS') = (progression_program_id IS NOT NULL)`, so
        // getting this wrong is a seed that refuses to insert on a user's first launch.
        SeedCatalog.routines.forEach { routine ->
            assertEquals(
                routine.id,
                routine.engine == Engine.FIXED_SETS,
                routine.progressionProgramId != null,
            )
        }
    }

    @Test
    fun `every routine is bounded by rounds or by a cap`() {
        // `CHECK (rounds IS NOT NULL OR time_cap_sec IS NOT NULL)` — something must end the session.
        assertTrue(SeedCatalog.routines.all { it.rounds != null || it.timeCapSeconds != null })
    }

    @Test
    fun `リーコン・ロン is scored by weighted volume, not by the step it reached`() {
        // DECISIONS.md §Q9, and §F.5 is amended to match. `04` :281 and :885 both map FIXED_SETS to
        // 最高負荷, and §6 :1131's tile list carries no tile for HIGHEST_STEP at all — so seeded as
        // HIGHEST_STEP the only personal_record row this routine could ever produce was one
        // `bestTilesFor` had no label for and dropped, and its 最高 block rendered empty forever. A
        // user with fifty sessions saw exactly what a user with zero saw.
        assertEquals(
            BestMetric.MOST_VOLUME,
            SeedCatalog.routines.first { it.id == "r_recon_ron" }.primaryMetric,
        )
    }

    @Test
    fun `nothing seeds HIGHEST_STEP in v1`() {
        // The column and the enum case both stay — the column was never the problem — but no Japanese
        // label exists for the metric, so a seed that used it would be a tile that cannot render.
        // The step reached is shown by `stepFor`, in the progression block, where a ladder belongs.
        assertTrue(SeedCatalog.routines.none { it.primaryMetric == BestMetric.HIGHEST_STEP })
    }

    // ── The catalogue (§F.1) ────────────────────────────────────────────────────────────────────

    @Test
    fun `the five ladder rungs carry 腕立て伏せ's own two seconds per rep`() {
        // §F.1's **[fam]** rows, ratified in DECISIONS.md §Q12: one measured value applied across a
        // movement family, rather than five separately invented estimates. The value is now in the
        // spec's table, so this pins the seed to it.
        listOf(
            "wall_pushup",
            "incline_pushup",
            "feet_elevated_pushup",
            "archer_pushup",
            "one_arm_pushup",
        ).forEach { id ->
            assertEquals(id, 2.0, SeedCatalog.exercises.first { it.id == id }.secondsPerRep, 0.0)
        }
    }

    @Test
    fun `the push-up ladder is the seven rungs the bests page rolls into one row`() {
        // `04` §4 edge case 6 counts seven near-identical push-up rows. 回旋腕立て伏せ is a different
        // movement rather than a rung, so it is deliberately not in the family.
        val push = SeedCatalog.exercises.filter { it.ladderId == LADDER_PUSH }
        assertEquals(7, push.size)
        assertTrue(push.none { it.id == "pushup_rotation" })
        // Ordered by difficulty, the ladder must be strictly climbable — two rungs at one difficulty
        // would make いちばん上 ambiguous.
        val difficulties = push.map { it.difficulty }.sorted()
        assertEquals(difficulties.distinct(), difficulties)
    }

    @Test
    fun `an isometric counts ten seconds as one unit`() {
        // For a hold, seconds_per_rep is repurposed as "the seconds that count as one volume unit",
        // which is the whole fix for design §7.4 scoring a plank as zero (§D.5).
        SeedCatalog.exercises.filter { it.isIsometric }.forEach {
            assertEquals(it.id, 10.0, it.secondsPerRep, 0.0)
        }
    }

    @Test
    fun `every exercise satisfies the CHECK constraints it will be written under`() {
        SeedCatalog.exercises.forEach {
            assertTrue(it.id, it.secondsPerRep > 0)
            assertTrue(it.id, it.difficulty > 0)
            assertTrue(it.id, it.nameJa.isNotBlank() && it.nameEn.isNotBlank())
        }
        assertEquals(
            "ids are unique",
            SeedCatalog.exercises.size,
            SeedCatalog.exercises.map { it.id }.distinct().size,
        )
    }

    @Test
    fun `走る carries no cue rather than an em dash`() {
        // §F.1 writes "—" in that cell, which is the table's way of saying "none" — storing the dash
        // itself would put a punctuation mark on the rest slide.
        assertNull(SeedCatalog.exercises.first { it.id == "run" }.cue)
    }

    private fun RoutineSeed.estimatedSeconds(): Int = estimatedDurationSeconds(
        stations = shapes(),
        secondsPerRep = { id -> SeedCatalog.exercises.firstOrNull { it.id == id }?.secondsPerRep },
        rounds = rounds,
        timeCapSeconds = timeCapSeconds,
        restBetweenStations = restBetweenStations,
        restBetweenRounds = restBetweenRounds,
        prepareSeconds = prepareSeconds,
    )
}
