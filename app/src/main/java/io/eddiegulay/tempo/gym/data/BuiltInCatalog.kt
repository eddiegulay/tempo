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
     * Phase 2 ships シンディ, チェルシー, バーバラ, マーフ and デス・バイ, and Pavel's ladder lands
     * whenever a sourced transcription does. Neither needs a column, so neither needs a migration —
     * they arrive as `VERSION = 2` and `onOpen` reseeds. That separation was designed for exactly this
     * (`00-plan.md` §2 row 17, §B.2).
     */
    const val VERSION: Int = 1

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
    )
}

/** The push-up family — the seven rows `04` §4 edge case 6 rolls into one 動きごと row. */
internal const val LADDER_PUSH = "push"
