package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.AdvanceRule
import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.SetVariant
import io.eddiegulay.tempo.gym.StepShape
import io.eddiegulay.tempo.gym.StepUnit
import io.eddiegulay.tempo.gym.Tier
import kotlin.math.ceil
import kotlin.math.floor

/*
 * The shipped catalogue, as reviewable Kotlin.
 *
 * Built-in routines live in the **database**, seeded from here — design §7.3's "Kotlin constants" is
 * superseded (`00-plan.md` §2 row 5). A `session` needs a foreign key to what it performed, and a
 * session pointing at a `val` has no referential integrity: nothing would stop a later app version
 * editing that constant and silently re-interpreting a March session. The home list also joins
 * routines against session aggregates and personal records in one query, which against a Kotlin list
 * is an N+1 or a Kotlin-side join over the whole session table. This file survives as the **seed
 * source of truth** — the thing a reviewer reads in a diff — and the seeder writes it into the tables.
 *
 * **Never edit a shipped progression table in place.** `session.progression_step_id` points at a step,
 * so rewriting that step's numbers retroactively changes what a past session prescribed. If a
 * transcription is ever corrected, add a new program id (`p_recon_ron_v2`) and leave the old rows
 * alone (§A.5).
 */

/** One row of `exercise`. */
internal data class ExerciseSeed(
    val id: String,
    val nameJa: String,
    val nameEn: String,
    val pattern: Pattern,
    val secondsPerRep: Double,
    val difficulty: Double,
    val isIsometric: Boolean,
    val cue: String?,
    val ladderId: String? = null,
    val catalogVersion: Int = 1,
)

/** One row of `routine_station`, before a version id exists to hang it on. */
internal data class StationSeed(
    val exerciseId: String,
    val measure: Measure,
    val reps: Int? = null,
    val seconds: Int? = null,
    val note: String? = null,
)

/** One built-in routine and its head version, as one object because the seeder writes them together. */
internal data class RoutineSeed(
    val id: String,
    val name: String,
    val engine: Engine,
    val tier: Tier?,
    val origin: String?,
    val sortOrder: Int,
    val primaryMetric: BestMetric,
    val stations: List<StationSeed>,
    val rounds: Int? = null,
    val timeCapSeconds: Int? = null,
    val intervalSeconds: Int? = null,
    val restBetweenStations: Int = 0,
    val restBetweenRounds: Int = 0,
    val prepareSeconds: Int = 5,
    val progressionProgramId: String? = null,
    val scaledFromRoutineId: String? = null,
    val catalogVersion: Int = 1,
) {
    fun shapes(): List<StationShape> = stations.map {
        StationShape(it.exerciseId, it.measure, it.reps, it.seconds, it.note)
    }
}

internal data class SetSeed(val setIndex: Int, val reps: Int?, val variant: SetVariant? = null)

internal data class StepSeed(
    val stepIndex: Int,
    val labelJa: String?,
    val shape: StepShape,
    val totalReps: Int?,
    val restSeconds: Int,
    val noteJa: String?,
    val sets: List<SetSeed>,
)

/**
 * What a database at a given catalog version is **missing** — a seed upgrade, as a value.
 *
 * The filtering used to be three inline `.filter { it.catalogVersion > from }` calls inside [Seeder],
 * where the one property this feature's phasing rests on could not be asserted without an
 * `SQLiteDatabase` — and there is none on the JVM classpath. Lifting it here makes it a plain unit test:
 * `planFrom(1)` names the six routines Phase 2 adds and nothing else, and `planFrom(2)` is empty, which
 * is what idempotence means one level above the content hash.
 *
 * *Rejected* — asserting the same thing by reading `Seeder.kt` as text. `GymManifestTest` reads XML that
 * way and is right to, because a manifest has no Kotlin form; a filter does.
 */
internal data class SeedPlan(
    val exercises: List<ExerciseSeed>,
    val programs: List<ProgramSeed>,
    val routines: List<RoutineSeed>,
) {
    val isEmpty: Boolean get() = exercises.isEmpty() && programs.isEmpty() && routines.isEmpty()
}

internal data class ProgramSeed(
    val id: String,
    val nameJa: String,
    val stepUnit: StepUnit,
    val stepCount: Int,
    val advanceRule: AdvanceRule,
    val advanceParam: Int?,
    val cycleDays: Int?,
    val origin: String,
    val noteJa: String?,
    val steps: List<StepSeed>,
    val catalogVersion: Int = 1,
)

internal object SeedCatalog {

    /**
     * The **content** counter, and it is not the schema counter.
     *
     * v2 is Phase 2's six built-ins — シンディ, シンディ（やさしい）, チェルシー, バーバラ, マーフ,
     * デス・バイ. **Not one of them needs a column**, so not one of them needs a migration:
     * [SCHEMA_VERSION] is still 1, `MIGRATIONS` still holds the single `V1CreateSchema`, and this bump
     * is `onOpen`'s business rather than `onUpgrade`'s (§B.2, `00-plan.md` §2 row 17). That is not a
     * convenience — an app update whose schema is unchanged **never calls `onUpgrade` at all**, so a
     * seed that lived there would silently fail to arrive for every existing install.
     *
     * The bump is an **append**: [planFrom] hands the seeder only the rows stamped newer than the
     * database's own generation, so an upgrade cannot revisit 七分間, cannot re-derive its estimate, and
     * cannot un-archive a built-in the user removed. `SeedUpgradeTest` pins all of it.
     *
     * Pavel's ladder (§F.4) is still unsourced and still lands whenever a transcription does — as v3,
     * by the same property, which is what §F.4 was promised.
     */
    const val VERSION: Int = 2

    /**
     * The rows a database at [fromCatalogVersion] has not seen.
     *
     * Strictly `>`: a generation the database already installed is never re-emitted, which is what makes
     * "upgrade" mean *add* rather than *reconcile*. A reconciling seeder would have to decide what to do
     * about a built-in the user archived, and every answer to that question is the app overruling them.
     */
    fun planFrom(fromCatalogVersion: Int): SeedPlan = SeedPlan(
        exercises = exercises.filter { it.catalogVersion > fromCatalogVersion },
        programs = programs.filter { it.catalogVersion > fromCatalogVersion },
        routines = routines.filter { it.catalogVersion > fromCatalogVersion },
    )

    // ── §F.1 exercise ────────────────────────────────────────────────────────────────────────────
    //
    // [added] rows — `crunch` and `pushup_rotation` — are not in design §12 and are required: the ACSM
    // seven-minute circuit that §9 forbids reordering contains both, so without them the shipped
    // circuit is not the published one (`00-plan.md` §2 row 20).
    //
    // On isometric `secondsPerRep = 10.0`: for a hold the column is repurposed as "the seconds that
    // count as one volume unit", which is the whole fix for design §7.4's formula scoring a plank as
    // zero. Ten is the only place the number is used for a hold, since a hold's pacer is its
    // prescribed duration (§F.1, §D.5).
    val exercises: List<ExerciseSeed> = listOf(
        ExerciseSeed("pushup", "腕立て伏せ", "Push-up", Pattern.H_PUSH, 2.0, 1.0, false, "体は一直線に", LADDER_PUSH),
        ExerciseSeed("knee_pushup", "膝つき腕立て", "Knee push-up", Pattern.H_PUSH, 1.8, 0.5, false, "腰を落とさない", LADDER_PUSH),
        ExerciseSeed("pushup_rotation", "回旋腕立て伏せ", "Push-up with rotation", Pattern.H_PUSH, 3.0, 1.2, false, "上げた手を目で追う"),
        ExerciseSeed("pullup", "懸垂", "Pull-up", Pattern.V_PULL, 3.0, 2.0, false, "肩を下げてから引く"),
        ExerciseSeed("ring_row", "斜め懸垂", "Ring row", Pattern.V_PULL, 2.2, 0.8, false, "体は板のまま"),
        ExerciseSeed("squat", "スクワット", "Air squat", Pattern.SQUAT, 1.8, 1.0, false, "膝は爪先の向きに"),
        ExerciseSeed("wall_sit", "空気椅子", "Wall sit", Pattern.SQUAT, 10.0, 0.8, true, "膝は九十度"),
        ExerciseSeed("lunge", "ランジ", "Lunge", Pattern.SQUAT, 2.2, 1.0, false, "前膝を爪先より前に出さない"),
        ExerciseSeed("step_up", "踏み台昇降", "Step-up", Pattern.SQUAT, 2.0, 0.8, false, "足の裏全体で乗る"),
        ExerciseSeed("situp", "腹筋", "Sit-up", Pattern.CORE, 1.7, 1.0, false, "反動を使わない"),
        ExerciseSeed("crunch", "クランチ", "Crunch", Pattern.CORE, 1.5, 0.8, false, "腰は床につけたまま"),
        ExerciseSeed("plank", "プランク", "Plank", Pattern.CORE, 10.0, 1.0, true, "肘は肩の真下に"),
        ExerciseSeed("side_plank", "横プランク", "Side plank", Pattern.CORE, 10.0, 1.1, true, "腰を落とさない"),
        ExerciseSeed("dip", "ディップス", "Triceps dip", Pattern.H_PUSH, 2.5, 1.5, false, "肘は後ろへ"),
        ExerciseSeed("burpee", "バーピー", "Burpee", Pattern.PLYO, 4.0, 1.6, false, "着地は柔らかく"),
        ExerciseSeed("jumping_jack", "ジャンピングジャック", "Jumping jacks", Pattern.PLYO, 0.8, 0.5, false, "肩の力を抜く"),
        ExerciseSeed("high_knees", "もも上げ", "High knees", Pattern.LOCOMOTION, 10.0, 0.7, true, "腿は腰の高さまで"),
        // §F.1 gives 走る a cue of "—", which is the table's way of writing "none", not a cue.
        ExerciseSeed("run", "走る", "Run", Pattern.LOCOMOTION, 10.0, 1.0, true, null),

        // The five ladder rungs §F.1 names for GYM.LIBRARY.EXERCISE_DETAIL. Together with 腕立て伏せ and
        // 膝つき腕立て they are the seven rows `04` §4 edge case 6 says must roll up into one.
        //
        // `seconds_per_rep = 2.0` and the English names are **§F.1's own**, ratified and written into
        // that table (DECISIONS.md §Q12): the 2.0 is 腕立て伏せ's measured value carried across its
        // ladder — marked **[fam]** there — because one measured value applied to a movement family is
        // a derivation, and strictly better than five separately invented estimates. `name_en` is
        // NOT NULL because TalkBack reads it, and a direct translation of a documented Japanese name
        // invents no fact. Difficulties are §F.1's and unchanged. Cues are left null rather than
        // written, because the table gives none.
        ExerciseSeed("wall_pushup", "壁腕立て", "Wall push-up", Pattern.H_PUSH, 2.0, 0.2, false, null, LADDER_PUSH),
        ExerciseSeed("incline_pushup", "斜め腕立て", "Incline push-up", Pattern.H_PUSH, 2.0, 0.4, false, null, LADDER_PUSH),
        ExerciseSeed("feet_elevated_pushup", "足上げ腕立て", "Feet-elevated push-up", Pattern.H_PUSH, 2.0, 1.3, false, null, LADDER_PUSH),
        ExerciseSeed("archer_pushup", "アーチャー腕立て", "Archer push-up", Pattern.H_PUSH, 2.0, 1.6, false, null, LADDER_PUSH),
        ExerciseSeed("one_arm_pushup", "片手腕立て", "One-arm push-up", Pattern.H_PUSH, 2.0, 2.5, false, null, LADDER_PUSH),
    )

    // ── §F.2 リーコン・ロン ───────────────────────────────────────────────────────────────────────
    //
    // **Provenance.** LtCol Stanley J. Pasieka Jr., USMC (Ret.), "Over the Top on 'Dead Hang'
    // Pull-Ups", *Marine Corps Gazette*, December 1981.
    //
    // **Verification (DECISIONS.md §Q3, 2026-08-13).** `00-plan.md` §2 row 2 required this
    // transcription to be checked against the source before seeding, because a second, plausible
    // table generated during planning disagreed in the middle rows. The check has been done. The
    // fuller 31-step 1st Recon pull-up progression chart was obtained independently of the planning
    // session, and these eighteen rows are exactly **steps 11–28** of it — matching set-for-set, not
    // merely in their totals. Where the scraped 31-step copy's step 15 sums to 35 against a printed
    // total of 34, the row below (step 5, `10 7 6 6 5`) is the internally consistent one. The rejected
    // planning-generated table is confirmed wrong: it lost the rotational increment pattern the source
    // chart carries continuously across all 31 steps.
    //
    // Two invariants hold on every row and are asserted by `SeedInvariantsTest`: the five sets sum to
    // the stated total, and the total is exactly `24 + 2 × stepIndex`.
    private val reconRonSteps: List<List<Int>> = listOf(
        listOf(7, 6, 5, 4, 4),      // 26
        listOf(8, 6, 5, 5, 4),      // 28
        listOf(8, 7, 5, 5, 5),      // 30
        listOf(9, 7, 6, 5, 5),      // 32
        listOf(10, 7, 6, 6, 5),     // 34
        listOf(10, 8, 6, 6, 6),     // 36
        listOf(11, 8, 7, 6, 6),     // 38
        listOf(12, 8, 7, 7, 6),     // 40
        listOf(12, 9, 7, 7, 7),     // 42
        listOf(13, 9, 8, 7, 7),     // 44
        listOf(14, 9, 8, 8, 7),     // 46
        listOf(14, 10, 8, 8, 8),    // 48
        listOf(15, 10, 9, 8, 8),    // 50
        listOf(16, 10, 9, 9, 8),    // 52
        listOf(16, 11, 9, 9, 9),    // 54
        listOf(17, 11, 10, 9, 9),   // 56
        listOf(18, 11, 10, 10, 9),  // 58
        listOf(18, 12, 10, 10, 10), // 60
    )

    private val reconRon = ProgramSeed(
        id = "p_recon_ron",
        nameJa = "リーコン・ロン",
        stepUnit = StepUnit.STEP,
        stepCount = 18,
        advanceRule = AdvanceRule.WEEKS_ELAPSED,
        advanceParam = 2,
        cycleDays = null,
        origin = "Pasieka, \"Over the Top on Dead Hang Pull-Ups\", Marine Corps Gazette, 1981-12",
        // The "one chosen day each week at one third volume" rule is a session-level modifier, not a
        // table row — the player multiplies each set by a third when the user marks the day light. If
        // it ever needs enforcing, `light_day_divisor` belongs on `progression_program` in schema v2.
        noteJa = "週に一日は三分の一の回数で",
        steps = reconRonSteps.mapIndexed { index, sets ->
            StepSeed(
                stepIndex = index + 1,
                labelJa = null,
                shape = StepShape.FIXED,
                totalReps = sets.sum(),
                restSeconds = 90,
                noteJa = null,
                sets = sets.mapIndexed { i, reps -> SetSeed(i + 1, reps) },
            )
        },
    )

    // ── §F.3 アームストロング ────────────────────────────────────────────────────────────────────
    //
    // Armstrong's days are **rules, not numbers**, and four of the five cannot be written as a rep
    // table at all. That is precisely why `progression_step.shape` exists: a schema that only stored
    // reps could represent one fifth of this programme. Days 1, 2 and 4 carry zero set rows and
    // compile entirely from their shape; only day 3 has rows, and they carry a grip rather than a
    // count, resolved at run time from the user's day-1 maximum.
    //
    // Day 2's rest is "10s × previous reps" and the column is a plain integer, so it stores the
    // multiplier and the shape says how to read it — a PYRAMID step's `rest_sec` is seconds *per rep
    // just performed*. Day 5's rest is written "—" in §F.3 because day 5 is day 4 repeated, so it
    // takes day 4's sixty rather than the column default.
    private val armstrong = ProgramSeed(
        id = "p_armstrong",
        nameJa = "アームストロング",
        stepUnit = StepUnit.DAY,
        stepCount = 5,
        advanceRule = AdvanceRule.SESSIONS_COMPLETED,
        advanceParam = 1,
        cycleDays = 7,
        origin = "Maj. Charles Lewis Armstrong, USMC pull-up program",
        noteJa = null,
        steps = listOf(
            StepSeed(1, "第一日", StepShape.MAX_EFFORT, null, 90, "全力五組", emptyList()),
            StepSeed(2, "第二日", StepShape.PYRAMID, null, 10, "段を上げて限界まで", emptyList()),
            StepSeed(
                3, "第三日", StepShape.GRIP_ROTATION, null, 60, "順手・狭手・逆手",
                listOf(
                    SetSeed(1, null, SetVariant.OVERHAND),
                    SetSeed(2, null, SetVariant.OVERHAND),
                    SetSeed(3, null, SetVariant.OVERHAND),
                    SetSeed(4, null, SetVariant.CLOSE),
                    SetSeed(5, null, SetVariant.CLOSE),
                    SetSeed(6, null, SetVariant.CLOSE),
                    SetSeed(7, null, SetVariant.REVERSE),
                    SetSeed(8, null, SetVariant.REVERSE),
                    SetSeed(9, null, SetVariant.REVERSE),
                ),
            ),
            StepSeed(4, "第四日", StepShape.MAX_EFFORT, null, 60, "一番きつい日", emptyList()),
            StepSeed(5, "第五日", StepShape.FIXED, null, 60, "第四日をもう一度", emptyList()),
        ),
    )

    // ── §F.4 ファイター懸垂 — the program row, and deliberately no steps ─────────────────────────
    //
    // The thirty daily ladders are structurally certain and numerically not: the programme is
    // 5-on/2-off, ladder-based and scaled to the trainee's current maximum, but the exact day-by-day
    // rung table varies between reproductions. Design §9 sets the standard — *"There is no documented
    // RECONDO push-up sequence with real numbers and this spec will not invent one"* — and the same
    // restraint applies here. Zero `progression_step` rows, and no routine references the program, so
    // nothing in the UI can reach an empty one.
    //
    // §F.4 gives no `advance_rule`, and the CHECK demands one of four. `MANUAL` is the honest reading
    // of a programme whose advancement we have not sourced: nothing advances it but the user.
    private val fighter = ProgramSeed(
        id = "p_fighter",
        nameJa = "ファイター懸垂",
        stepUnit = StepUnit.DAY,
        stepCount = 30,
        advanceRule = AdvanceRule.MANUAL,
        advanceParam = null,
        cycleDays = 7,
        origin = "Tsatsouline, Fighter Pull-Up Program",
        noteJa = null,
        steps = emptyList(),
    )

    val programs: List<ProgramSeed> = listOf(reconRon, armstrong, fighter)

    /** §F.5's interval for both minute-grid routines, named once so the grid and its bound agree. */
    private const val MINUTE_SEC = 60

    /**
     * How many minutes of デス・バイ's ascending grid to lay down — and it is a **materialisation
     * bound, not a prescription**.
     *
     * §F.5 gives デス・バイ neither `rounds` nor `time_cap_sec`, because the protocol has neither: `03`
     * §C.3 states that `EMOM_ASCENDING`'s fail-out *is* the terminating condition. The schema disagrees
     * — `CHECK (rounds IS NOT NULL OR time_cap_sec IS NOT NULL)`, on the premise that something must end
     * a session — and so does the compiler, which lays `1..rounds` minutes and has no
     * `extendOneRound` for this engine (only an AMRAP is `extensible`). One of the two columns has to
     * carry a number. **This is the one figure in Phase 2 that no source supplies; it is disclosed
     * rather than slipped in, exactly as §Q12's `sec/rep` were, and it is a §Q-shaped question for the
     * orchestrator to ratify or overrule.**
     *
     * It is derived rather than chosen. Minute *m* prescribes *m* burpees inside a fixed window, so at
     * the catalogue's own pace for `burpee` — §F.1's measured 4.0 s/rep, the same number
     * `estimateMs` paces the segment with — the last minute whose prescription still fits its own window
     * is `floor(60 / 4.0) = 15`. Past it the prescription cannot be completed by anyone at catalogue
     * pace, so the grid is laid to precisely where the protocol's own terminating condition becomes
     * arithmetic. Nothing is claimed about the athlete.
     *
     * *Rejected* — Stew Smith's ten-minute Death by Push-ups (design §9). It is a real, sourced number
     * and it belongs to a **different workout**: ten push-ups EMOM inside a ten-minute plank, neither
     * ascending nor burpees. Borrowing its ten would be inventing with a citation attached.
     */
    private fun ascendingMinuteBound(intervalSeconds: Int, secondsPerRep: Double): Int =
        floor(intervalSeconds / secondsPerRep).toInt().coerceAtLeast(1)

    private val deathByMinutes: Int = ascendingMinuteBound(
        intervalSeconds = MINUTE_SEC,
        secondsPerRep = exercises.first { it.id == "burpee" }.secondsPerRep,
    )

    // ── §F.5 built-in routines ───────────────────────────────────────────────────────────────────
    val routines: List<RoutineSeed> = listOf(

        // 七分間. Twelve stations at DURATION 30 **in the published order** — design §9 forbids
        // reordering, because the sequence alternates total-body → lower → upper → core so opposing
        // groups recover while others work.
        //
        // est_duration_sec computes to 12×30 + 11×10 + 5 = 475 (≈ 約八分). `00-plan.md` §2 row 18 says
        // to render that figure: "7-minute workout" is ACSM's own branding rounding, not our
        // arithmetic, and quietly dropping the prepare to reach 420 would be arranging the sums to
        // agree with the marketing.
        //
        // §9's "1–3 circuits" is served by `rounds_planned` at launch, not by three seeded routines.
        RoutineSeed(
            id = "r_seven_minute",
            name = "七分間",
            engine = Engine.INTERVAL_CIRCUIT,
            tier = Tier.BEGINNER,
            origin = "Klika & Jordan, ACSM's Health & Fitness Journal, 2013-05",
            sortOrder = 0,
            primaryMetric = BestMetric.MOST_VOLUME,
            rounds = 1,
            restBetweenStations = 10,
            restBetweenRounds = 60,
            prepareSeconds = 5,
            stations = listOf(
                StationSeed("jumping_jack", Measure.DURATION, seconds = 30),
                StationSeed("wall_sit", Measure.DURATION, seconds = 30),
                StationSeed("pushup", Measure.DURATION, seconds = 30),
                StationSeed("crunch", Measure.DURATION, seconds = 30),
                StationSeed("step_up", Measure.DURATION, seconds = 30),
                StationSeed("squat", Measure.DURATION, seconds = 30),
                StationSeed("dip", Measure.DURATION, seconds = 30),
                StationSeed("plank", Measure.DURATION, seconds = 30),
                StationSeed("high_knees", Measure.DURATION, seconds = 30),
                StationSeed("lunge", Measure.DURATION, seconds = 30),
                StationSeed("pushup_rotation", Measure.DURATION, seconds = 30),
                StationSeed("side_plank", Measure.DURATION, seconds = 30),
            ),
        ),

        // タバタ. est_duration_sec computes to 8×20 + 7×10 + 5 = 235 — design §9's "4:00" counts the
        // rest after the final round and ours does not, which is the honest number. The exercise choice
        // is ours (the 1996 protocol was a cycle ergometer), which is what the station note says; there
        // is no note column on a version, and the station is the only place the routine has to say it.
        RoutineSeed(
            id = "r_tabata",
            name = "タバタ",
            engine = Engine.INTERVAL_CIRCUIT,
            tier = Tier.INTERMEDIATE,
            origin = "Tabata et al., Med Sci Sports Exerc, 1996",
            sortOrder = 1,
            primaryMetric = BestMetric.MOST_VOLUME,
            rounds = 8,
            restBetweenStations = 0,
            restBetweenRounds = 10,
            stations = listOf(
                StationSeed("burpee", Measure.DURATION, seconds = 20, note = "種目は自由に"),
            ),
        ),

        // リーコン・ロン. One station, MAX_EFFORT — the reps come from the step table, which is the
        // whole point of FIXED_SETS. §F.5 gives `rest_between_stations = 90` and a single station, so
        // that rest can never fire; the rest that does is between sets, and it is `rest_between_rounds`.
        // Both carry 90 — §F.5's number for one and §F.2's `rest_sec = 90` for the other, the same
        // ninety seconds from the same programme, placed where the schema makes it operative.
        //
        // §F.5 gives no `origin` for the routine, so it carries its programme's.
        //
        // **`primary_metric = MOST_VOLUME`, not `HIGHEST_STEP`** (DECISIONS.md §Q9; §F.5 amended).
        // `04` :281 and :885 both map FIXED_SETS → 最高負荷, and `04` §6 :1131's best-tile list —
        // 最高巡数 / 最高反復 / 最速 / 最高負荷 / やった回数 — has no tile for HIGHEST_STEP at all. Seeded
        // as HIGHEST_STEP the only personal_record row this routine could produce was one
        // `bestTilesFor` had no label for and dropped, so its 最高 block rendered **empty forever**:
        // fifty sessions looked exactly like zero. The step reached is not lost — `stepFor` renders it
        // in the progression block as 第九段 / 十八段のうち, which is where a ladder belongs.
        // HIGHEST_STEP stays in the CHECK constraint and in BestMetric; nothing seeds it in v1.
        RoutineSeed(
            id = "r_recon_ron",
            name = "リーコン・ロン",
            engine = Engine.FIXED_SETS,
            tier = Tier.ADVANCED,
            origin = "Pasieka, \"Over the Top on Dead Hang Pull-Ups\", Marine Corps Gazette, 1981-12",
            sortOrder = 2,
            primaryMetric = BestMetric.MOST_VOLUME,
            rounds = 5,
            restBetweenStations = 90,
            restBetweenRounds = 90,
            progressionProgramId = "p_recon_ron",
            stations = listOf(
                StationSeed("pullup", Measure.MAX_EFFORT),
            ),
        ),

        // ── Phase 2, catalog_version = 2 ─────────────────────────────────────────────────────────
        //
        // **Every row below is seeded exactly as §F.5 writes it.** `DECISIONS.md` §Q15 records that the
        // whole table was checked against primary sources independently of the planning session — these
        // are the numbers most often misremembered, which is why the check happened at all — so this is
        // not a place to improve on the figures. Sources are §Q15's: CrossFit's own benchmark-workout
        // article for the Girls, crossfit.com/murph, and BoxRox's list for チェルシー and バーバラ.
        //
        // These six need **no migration**. They are content, `catalogVersion = 2` is the content
        // counter, and `SCHEMA_VERSION` does not move (§B.2). What makes that safe is that every one of
        // them is expressible in schema v1: the engines were all in the `routine_version` CHECK from the
        // start, `interval_sec` and `time_cap_sec` were already columns, and the one shape that did not
        // fit — マーフ's runs — bends to the model rather than the model to it, below.

        // シンディ. **5/10/15, not 10/15/15** — design §9's correction, and §Q15 confirms it: the
        // 10/15/15 version has no source and is a garbled recollection of a workout first posted on
        // CrossFit.com on 2004-12-29.
        //
        // `rounds = null` is the AMRAP's whole point. The rounds are the *score*, not the plan, so the
        // column stays empty and the cap is what bounds the session — which is also how the CHECK
        // `(rounds IS NOT NULL OR time_cap_sec IS NOT NULL)` is satisfied. The compiler materialises a
        // few rounds and appends more as the frontier nears the cap, and reports what it laid.
        RoutineSeed(
            id = "r_cindy",
            name = "シンディ",
            engine = Engine.AMRAP,
            tier = Tier.INTERMEDIATE,
            origin = "CrossFit.com, 2004-12-29",
            sortOrder = 3,
            primaryMetric = BestMetric.MOST_ROUNDS,
            rounds = null,
            timeCapSeconds = 1200,
            catalogVersion = 2,
            stations = listOf(
                StationSeed("pullup", Measure.REPS, reps = 5),
                StationSeed("pushup", Measure.REPS, reps = 10),
                StationSeed("squat", Measure.REPS, reps = 15),
            ),
        ),

        // シンディ（やさしい） — 3 ring rows / 6 knee push-ups / 9 squats in twelve minutes.
        //
        // Design §9: *"Every preset needs a scaled tier. Cindy's official beginner scaling is a
        // 12-minute AMRAP of 3 ring rows, 6 assisted push-ups, 9 air squats — ship that as 「やさしい」
        // rather than letting a beginner bounce off the Rx version."* It is a separate routine row
        // rather than a tier override on シンディ because `04` §3 says scaled tiers are separate rows,
        // and because `scaled_from_routine_id` exists precisely to carry the relationship without
        // making one routine's history the other's.
        //
        // **`tier = 入門` is a derivation, disclosed.** §F.5's Phase 2 table carries no tier column at
        // all; §9's table gives the other five (see each below) and describes this one only as the
        // *beginner* scaling. 入門 is the enum's word for beginner and the routine is nothing else, so
        // the mapping states no new fact — but it is not a transcription either, and it is called out
        // here so a reviewer sees it rather than finds it.
        //
        // The row must be seeded **after** シンディ: `scaled_from_routine_id` is a foreign key at it.
        // `defer_foreign_keys` would tolerate the other order inside the transaction; relying on that
        // for an ordering the list can simply have is how a seed becomes launch-order-sensitive.
        RoutineSeed(
            id = "r_cindy_scaled",
            name = "シンディ（やさしい）",
            engine = Engine.AMRAP,
            tier = Tier.BEGINNER,
            origin = "CrossFit official scaling",
            sortOrder = 4,
            primaryMetric = BestMetric.MOST_ROUNDS,
            rounds = null,
            timeCapSeconds = 720,
            scaledFromRoutineId = "r_cindy",
            catalogVersion = 2,
            stations = listOf(
                StationSeed("ring_row", Measure.REPS, reps = 3),
                StationSeed("knee_pushup", Measure.REPS, reps = 6),
                StationSeed("squat", Measure.REPS, reps = 9),
            ),
        ),

        // チェルシー — シンディ's identical 5/10/15, on a thirty-minute anchored minute grid.
        //
        // Same three stations, and that is the workout: the difference between シンディ and チェルシー is
        // entirely in the engine, which is `00-plan.md` §1's claim about the engine model being load
        // bearing, demonstrated. An EMOM's rest is the remainder of the minute, so
        // `rest_between_stations` and `rest_between_rounds` are both zero — a grid that also carried a
        // rest column would drift off its own anchor.
        RoutineSeed(
            id = "r_chelsea",
            name = "チェルシー",
            engine = Engine.EMOM,
            tier = Tier.ADVANCED,
            origin = "CrossFit benchmark",
            sortOrder = 5,
            primaryMetric = BestMetric.MOST_ROUNDS,
            rounds = 30,
            intervalSeconds = MINUTE_SEC,
            catalogVersion = 2,
            stations = listOf(
                StationSeed("pullup", Measure.REPS, reps = 5),
                StationSeed("pushup", Measure.REPS, reps = 10),
                StationSeed("squat", Measure.REPS, reps = 15),
            ),
        ),

        // バーバラ — five rounds of 20/30/40/50, with **exactly three minutes** between them.
        //
        // The rest is the reason this engine exists. `03` §B.3: skipping it makes the resulting time
        // incomparable to anyone else's バーバラ, so it compiles as `RestKind.MANDATED` and the player
        // refuses ＋二十秒 and とばす on it, visibly. Seeding it as `rest_between_rounds` rather than as
        // four rest stations is what puts it there: `FOR_TIME_WITH_REST` is the only engine that reads
        // that column as mandated, and a rest expressed as a station would be skippable like any other.
        RoutineSeed(
            id = "r_barbara",
            name = "バーバラ",
            engine = Engine.FOR_TIME_WITH_REST,
            tier = Tier.ADVANCED,
            origin = "CrossFit benchmark",
            sortOrder = 6,
            primaryMetric = BestMetric.BEST_TIME,
            rounds = 5,
            restBetweenRounds = 180,
            catalogVersion = 2,
            stations = listOf(
                StationSeed("pullup", Measure.REPS, reps = 20),
                StationSeed("pushup", Measure.REPS, reps = 30),
                StationSeed("situp", Measure.REPS, reps = 40),
                StationSeed("squat", Measure.REPS, reps = 50),
            ),
        ),

        // マーフ — a mile, then 100 / 200 / 300, then a mile.
        //
        // **The run legs are `MAX_EFFORT` carrying a note, and this is the decision to read before
        // touching them** (§F.5, `DECISIONS.md` §Q15). The obvious encoding — `DURATION` with a NULL
        // `prescribed_sec` — is refused by `routine_station`'s coherence CHECK, and rightly: a duration
        // station with no duration is exactly the malformed row that CHECK exists to reject three
        // screens before the timeline compiler meets it. `MAX_EFFORT` needs no schema change at all,
        // because the player already treats a `MAX_EFFORT` `LOCOMOTION` station as an open segment
        // closed by 済 — which is what a run is: you finish it and you say so.
        //
        // The distance therefore stays **a note** rather than becoming a column. A `distance_m` would
        // have to be understood by the compiler, the estimate, the volume formula, the record screen and
        // the builder's picker, all to serve two stations of one routine (§Q15).
        //
        // **The weight vest is not modelled.** The 20lb/14lb vest is optional in the source and there is
        // no load column; inventing one to hold an optional would be the same mistake as the distance,
        // without even the excuse of being prescribed.
        //
        // `rounds = 1` is the CHECK's price for a for-time routine with no cap — design §7.2 gives
        // `FOR_TIME` "no cap", so the other column has to be the non-null one. It states nothing beyond
        // the shape §F.5 already writes: run / 100 / 200 / 300 / run, performed once.
        RoutineSeed(
            id = "r_murph",
            name = "マーフ",
            engine = Engine.FOR_TIME,
            tier = Tier.ADVANCED,
            origin = "CrossFit, in memory of Lt. Michael Murphy",
            sortOrder = 7,
            primaryMetric = BestMetric.BEST_TIME,
            rounds = 1,
            catalogVersion = 2,
            stations = listOf(
                StationSeed("run", Measure.MAX_EFFORT, note = "一マイル"),
                StationSeed("pullup", Measure.REPS, reps = 100),
                StationSeed("pushup", Measure.REPS, reps = 200),
                StationSeed("squat", Measure.REPS, reps = 300),
                StationSeed("run", Measure.MAX_EFFORT, note = "一マイル"),
            ),
        ),

        // デス・バイ — one more rep every minute, until you cannot.
        //
        // **It is a format, not a canonical workout** (`DECISIONS.md` §Q15). §F.5 picks `burpee` and
        // that choice is ours in exactly the way タバタ's is — the 1996 protocol was a cycle ergometer,
        // and the 種目は自由に on that routine's station says so. This one says it the same way, in the
        // same words, on its own station: the model has no note column on a version, and the station is
        // the only place a routine has to speak.
        //
        // No tier. Design §9's table gives every other preset one and writes "scales itself" here, which
        // is a fact about the format rather than a band; `routine.tier` is nullable for precisely such a
        // row, and picking 中級 to fill the column would be inventing the one thing §9 declined to say.
        //
        // The first minute is one rep and the compiler adds `m − 1` — so `prescribed_reps = 1` is the
        // whole of "+1 rep per minute", and it also satisfies the CHECK that a REPS station prescribe a
        // positive count. See [ascendingMinuteBound] for why `rounds` carries a number at all.
        RoutineSeed(
            id = "r_death_by",
            name = "デス・バイ",
            engine = Engine.EMOM_ASCENDING,
            tier = null,
            origin = "Stew Smith / CrossFit tradition",
            sortOrder = 8,
            primaryMetric = BestMetric.MOST_ROUNDS,
            rounds = deathByMinutes,
            intervalSeconds = MINUTE_SEC,
            catalogVersion = 2,
            stations = listOf(
                StationSeed("burpee", Measure.REPS, reps = 1, note = "種目は自由に"),
            ),
        ),
    )
}

/**
 * The seconds a seed will freeze into `routine_version.est_duration_sec`.
 *
 * [estimatedDurationSeconds] with **one substitution**, and the substitution is the house rule rather
 * than a new one. `RoutineEstimate.kt` states it for the builder's live line: for `EMOM` /
 * `EMOM_ASCENDING` *"a round is one interval window whether or not the work fills it, so the duration is
 * `interval × rounds`"*. `GymMath`'s estimator has no interval parameter — it was written for the two
 * engines Phase 1 shipped — so left alone it sums the *work* and チェルシー's thirty anchored minutes
 * come out at 1865s (約三十一分) against the grid's own 1805 (約三十分). Feeding the grid in through the
 * cap parameter is not a trick: that function already documents *"a time-capped engine is its cap plus
 * the prepare, because the cap **is** the duration"*, and for an anchored grid the grid is the cap —
 * literally, it is what `Builder.emom` assigns to `capMs`.
 *
 * **No v1 value moves.** 七分間, タバタ and リーコン・ロン carry neither an interval nor a cap, so the
 * substitution is inert for them and their columns stay 475 / 235 / 5. That matters more than it looks:
 * a fresh install seeds every generation while an upgrade seeds only the new one, so an estimator that
 * treated v1 rows differently would produce two populations whose 型 cards disagree.
 *
 * *Rejected* — teaching [estimatedDurationSeconds] about intervals. It is shared with the builder's save
 * path and with Phase 1's pinned totals, and this is a seed-time derivation of a denormalised column,
 * not a change to what an estimate means.
 */
internal fun RoutineSeed.estimatedSeconds(secondsPerRep: (String) -> Double?): Int =
    estimatedDurationSeconds(
        stations = shapes(),
        secondsPerRep = secondsPerRep,
        rounds = rounds,
        timeCapSeconds = timeCapSeconds ?: gridSeconds,
        restBetweenStations = restBetweenStations,
        restBetweenRounds = restBetweenRounds,
        prepareSeconds = prepareSeconds,
    )

/**
 * The reps a seed will freeze into `routine_version.est_total_reps`.
 *
 * [estimatedTotalReps] multiplies a round's prescribed reps by the round count, which is right for every
 * engine that has one — and an AMRAP does not. Left alone, シンディ would store **thirty**: one round's
 * worth, rendered as 三十回まで on a page whose まで means *upper bound over twenty minutes*.
 * `04` §3's own mock says 「六百回まで」, and `RoutineEstimate.kt` gives the arithmetic that reaches it:
 * the cap divided by a round's estimate, rounded **up** because the fragment is a ceiling.
 *
 * Reached by calling the same two functions rather than by restating their arithmetic — one round of the
 * duration estimator gives the divisor — because a second copy of a formula is the divergence §Q7 was
 * written about. Cindy: `⌈1200 / 62⌉ = 20` rounds × 30 reps = 600.
 *
 * Inert for every engine that carries `rounds`, so again no v1 column moves.
 */
internal fun RoutineSeed.estimatedReps(secondsPerRep: (String) -> Double?): Int =
    estimatedTotalReps(shapes(), rounds ?: projectedRounds(secondsPerRep))

/** How many rounds a cap has room for, at the catalogue's pace. Null when there is no cap to divide. */
private fun RoutineSeed.projectedRounds(secondsPerRep: (String) -> Double?): Int? {
    val cap = timeCapSeconds ?: return null
    val roundSeconds = estimatedDurationSeconds(
        stations = shapes(),
        secondsPerRep = secondsPerRep,
        rounds = 1,
        timeCapSeconds = null,
        restBetweenStations = restBetweenStations,
        restBetweenRounds = 0,
        prepareSeconds = 0,
    )
    return if (roundSeconds <= 0) null else ceil(cap.toDouble() / roundSeconds).toInt()
}

/**
 * An anchored minute grid's length. Non-null only for the two EMOM engines, because they are the only
 * two that carry an interval — nothing else in the catalogue has one to be confused by.
 */
private val RoutineSeed.gridSeconds: Int?
    get() = intervalSeconds?.let { interval -> rounds?.let { interval * it } }

/** The push-up family — the seven rows `04` §4 edge case 6 rolls into one 動きごと row. */
internal const val LADDER_PUSH = "push"
