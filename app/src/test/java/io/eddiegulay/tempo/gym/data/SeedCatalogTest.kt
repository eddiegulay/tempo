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

    private fun routine(id: String): RoutineSeed = SeedCatalog.routines.first { it.id == id }

    private val reconRon = SeedCatalog.programs.first { it.id == "p_recon_ron" }
    private val sevenMinute = routine("r_seven_minute")
    private val tabata = routine("r_tabata")
    private val cindy = routine("r_cindy")
    private val chelsea = routine("r_chelsea")

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

    // ── Phase 2's six (§F.5, verified per DECISIONS.md §Q15) ────────────────────────────────────

    @Test
    fun `シンディ is five, ten, fifteen — not ten, fifteen, fifteen`() {
        // Design §9's correction and §Q15's first confirmed row. The 10/15/15 version has no source and
        // is a garbled recollection; this is the workout posted on CrossFit.com on 2004-12-29. It is the
        // single most misremembered number in the catalogue, which is why it gets its own assertion.
        assertEquals(
            listOf("pullup" to 5, "pushup" to 10, "squat" to 15),
            cindy.stations.map { it.exerciseId to it.reps },
        )
        assertTrue(cindy.stations.all { it.measure == Measure.REPS })
        assertEquals(1200, cindy.timeCapSeconds)
        // An AMRAP's rounds are the score, not the plan. A number here would be a target, and the whole
        // point of the engine is that there is not one.
        assertNull(cindy.rounds)
    }

    @Test
    fun `the scaling is three, six, nine in twelve minutes, and points at what it scales`() {
        // Design §9: Cindy's official beginner scaling, shipped so a beginner does not bounce off the Rx
        // version. `scaled_from_routine_id` is what makes it a scaling rather than an unrelated routine
        // that happens to look similar.
        val scaled = routine("r_cindy_scaled")
        assertEquals(
            listOf("ring_row" to 3, "knee_pushup" to 6, "squat" to 9),
            scaled.stations.map { it.exerciseId to it.reps },
        )
        assertEquals(720, scaled.timeCapSeconds)
        assertEquals("r_cindy", scaled.scaledFromRoutineId)
    }

    @Test
    fun `a scaling is seeded after the routine it scales`() {
        // `scaled_from_routine_id` is a foreign key at `routine`. `defer_foreign_keys` would tolerate
        // the other order inside the transaction, but relying on it for an ordering the list can simply
        // have is how a seed becomes sensitive to something invisible.
        SeedCatalog.routines.forEachIndexed { index, seed ->
            val parent = seed.scaledFromRoutineId ?: return@forEachIndexed
            val parentIndex = SeedCatalog.routines.indexOfFirst { it.id == parent }
            assertTrue(seed.id, parentIndex in 0 until index)
        }
    }

    @Test
    fun `チェルシー is シンディ's stations on a thirty minute grid`() {
        // The difference between the two routines is **entirely** the engine, which is `00-plan.md`
        // §1's claim about the engine model being load-bearing, in one assertion. Same 5/10/15.
        assertEquals(
            cindy.stations.map { it.exerciseId to it.reps },
            chelsea.stations.map { it.exerciseId to it.reps },
        )
        assertEquals(Engine.EMOM, chelsea.engine)
        assertEquals(30, chelsea.rounds)
        assertEquals(60, chelsea.intervalSeconds)
    }

    @Test
    fun `every minute grid carries an interval, because the compiler refuses one without`() {
        // `Builder.emom` opens with `require(window > 0)`, so an EMOM seeded with a null interval is a
        // crash on 始める rather than a bad estimate.
        SeedCatalog.routines
            .filter { it.engine == Engine.EMOM || it.engine == Engine.EMOM_ASCENDING }
            .forEach {
                assertTrue(it.id, (it.intervalSeconds ?: 0) > 0)
                assertTrue(it.id, (it.rounds ?: 0) > 0)
            }
    }

    @Test
    fun `バーバラ is five rounds of 20-30-40-50 with exactly three minutes between them`() {
        // The rest is the workout, not a courtesy: `03` §B.3 compiles it as RestKind.MANDATED because
        // skipping it makes the resulting time incomparable to anyone else's バーバラ. Seeding it as
        // `rest_between_rounds` is what puts it there — a rest expressed as a station would be
        // skippable like any other.
        val barbara = routine("r_barbara")
        assertEquals(
            listOf("pullup" to 20, "pushup" to 30, "situp" to 40, "squat" to 50),
            barbara.stations.map { it.exerciseId to it.reps },
        )
        assertEquals(5, barbara.rounds)
        assertEquals(180, barbara.restBetweenRounds)
        assertEquals(BestMetric.BEST_TIME, barbara.primaryMetric)
    }

    @Test
    fun `マーフ's runs are 限界まで carrying 一マイル, never a duration with no seconds`() {
        // §F.5 and DECISIONS.md §Q15. `DURATION` with a NULL `prescribed_sec` is refused by
        // routine_station's coherence CHECK — correctly, since a duration station with no duration is
        // the malformed row that CHECK exists to catch. MAX_EFFORT needs no schema change at all: the
        // player already closes a MAX_EFFORT LOCOMOTION station on 済, which is what a run is.
        val murph = routine("r_murph")
        val runs = murph.stations.filter { it.exerciseId == "run" }
        assertEquals(2, runs.size)
        runs.forEach {
            assertEquals(Measure.MAX_EFFORT, it.measure)
            assertEquals("一マイル", it.note)
            assertNull(it.seconds)
            assertNull(it.reps)
        }
        assertEquals(
            listOf("run", "pullup", "pushup", "squat", "run"),
            murph.stations.map { it.exerciseId },
        )
        assertEquals(
            listOf(100, 200, 300),
            murph.stations.filter { it.measure == Measure.REPS }.map { it.reps },
        )
    }

    @Test
    fun `デス・バイ starts at one rep and says the movement is ours`() {
        // The compiler adds `m − 1` to the prescription in minute m, so one rep **is** "+1 rep per
        // minute" — and it satisfies the CHECK that a REPS station prescribe a positive count.
        //
        // §Q15: デス・バイ is a *format*, not a canonical workout, so the burpee is our choice exactly as
        // タバタ's exercise is. It says so in タバタ's own words, on the only surface a routine has for
        // saying anything — a version carries no note column.
        val deathBy = routine("r_death_by")
        assertEquals(Engine.EMOM_ASCENDING, deathBy.engine)
        val station = deathBy.stations.single()
        assertEquals("burpee", station.exerciseId)
        assertEquals(1, station.reps)
        assertEquals("種目は自由に", station.note)
        assertEquals(tabata.stations.single().note, station.note)
        // No tier. Design §9's table writes "scales itself" where the other five carry a band, and
        // filling the column with 中級 would invent the one thing that table declined to say.
        assertNull(deathBy.tier)
    }

    @Test
    fun `デス・バイ's grid is laid to where the prescription stops fitting the minute`() {
        // The one Phase 2 figure no source supplies (see `ascendingMinuteBound`). It is derived, not
        // chosen: minute m asks for m burpees, §F.1 measures a burpee at 4.0s, so 60 / 4.0 = 15 is the
        // last minute completable at catalogue pace. It bounds the *materialisation* — `03` §C.3 says
        // the fail-out is the protocol's terminating condition — and the compiler needs a bound because
        // only an AMRAP is `extensible`.
        assertEquals(15, routine("r_death_by").rounds)
        assertEquals(60.0, 15 * SeedCatalog.exercises.first { it.id == "burpee" }.secondsPerRep, 0.0)
    }

    @Test
    fun `Phase 2's routines are stamped as Phase 2, and Phase 1's are not restamped`() {
        // The stamp is the whole upgrade mechanism: `planFrom` filters on it, so a routine stamped 1
        // never reaches an existing database again and a routine stamped 2 reaches it exactly once.
        val phaseTwo = setOf(
            "r_cindy", "r_cindy_scaled", "r_chelsea", "r_barbara", "r_murph", "r_death_by",
        )
        SeedCatalog.routines.forEach {
            assertEquals(it.id, if (it.id in phaseTwo) 2 else 1, it.catalogVersion)
        }
        assertEquals(
            phaseTwo,
            SeedCatalog.routines.filter { it.catalogVersion == 2 }.map { it.id }.toSet(),
        )
    }

    @Test
    fun `every station satisfies the prescription CHECK it will be written under`() {
        // routine_station's coherence CHECK, as Kotlin. Getting it wrong is a seed that refuses to
        // insert on a user's first launch — the failure is total and it happens on the launcher.
        SeedCatalog.routines.flatMap { r -> r.stations.map { r.id to it } }.forEach { (id, s) ->
            val where = "$id/${s.exerciseId}"
            when (s.measure) {
                Measure.REPS -> {
                    assertTrue(where, (s.reps ?: 0) > 0)
                    assertNull(where, s.seconds)
                }

                Measure.DURATION -> {
                    assertTrue(where, (s.seconds ?: 0) > 0)
                    assertNull(where, s.reps)
                }

                Measure.MAX_EFFORT -> {
                    assertNull(where, s.reps)
                    assertNull(where, s.seconds)
                }
            }
        }
    }

    @Test
    fun `an AMRAP's cap is its duration, and its reps are a ceiling over the whole cap`() {
        // 5 + 1200. And the reps: `04` §3's mock says 「六百回まで」 for シンディ, which is ⌈1200 / 62⌉ = 20
        // rounds of thirty. Storing one round's thirty — which is what a plain rounds × reps gives for a
        // routine with no round count — would put 三十回まで on a twenty-minute page.
        assertEquals(1205, cindy.estimatedSeconds())
        assertEquals(600, cindy.estimatedReps())
    }

    @Test
    fun `a minute grid's duration is its grid, not the work inside it`() {
        // `RoutineEstimate.kt`'s rule: a round is one interval window whether or not the work fills it.
        // 5 + 60 × 30 = 1805 (約三十分). Summing the work instead gives 1865 — 約三十一分 for a workout
        // whose entire premise is that it takes exactly thirty minutes.
        assertEquals(1805, chelsea.estimatedSeconds())
        assertEquals(905, routine("r_death_by").estimatedSeconds())
    }

    @Test
    fun `a Phase 2 bump moves no Phase 1 estimate`() {
        // A fresh install seeds every generation; an upgrade seeds only the new one. Any change to how
        // an older routine's frozen columns are derived would therefore produce two populations whose
        // 型 cards disagree, with nothing on screen to explain it.
        assertEquals(475, sevenMinute.estimatedSeconds())
        assertEquals(235, tabata.estimatedSeconds())
        // リーコン・ロン's four inter-set rests and its prepare, and nothing else: a MAX_EFFORT station
        // contributes zero seconds because there is nothing to estimate, which is `GymMath`'s doctrine
        // for the stored column and the reason マーフ's miles cost nothing either.
        assertEquals(365, routine("r_recon_ron").estimatedSeconds())
        assertEquals(0, sevenMinute.estimatedReps())
        assertEquals(0, tabata.estimatedReps())
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

    /**
     * The catalogue's own pace table, standing in for the `exercise` rows the seeder reads.
     *
     * The two are the same numbers by construction — the rows are seeded from this list — so a test
     * that asserts a stored estimate can use it without pretending to have a database.
     */
    private val pace: (String) -> Double? =
        { id -> SeedCatalog.exercises.firstOrNull { it.id == id }?.secondsPerRep }

    private fun RoutineSeed.estimatedSeconds(): Int = estimatedSeconds(pace)

    private fun RoutineSeed.estimatedReps(): Int = estimatedReps(pace)
}
