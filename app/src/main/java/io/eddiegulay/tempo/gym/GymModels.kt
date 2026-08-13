package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.Strings
import java.time.LocalDate

/*
 * 鍛錬's data-layer vocabulary: every type the repository signatures in `02-data.md` §C mention, and
 * nothing that draws.
 *
 * Two rules run through the whole file and explain most of what looks redundant in it.
 *
 * 1. **Where §A froze a value onto a row, the model carries it too.** `session.routine_name`,
 *    `session.stations_planned`, `session_result.difficulty_coef` and `session_result.volume_units`
 *    are all denormalised copies of something that lives elsewhere, and every one of them is there
 *    because the elsewhere is allowed to change. A routine deleted after the fact still has to name
 *    itself in a つづき banner (§A.6, `00-plan.md` §2 row 10); a coefficient retuned in v1.4 must not
 *    move a chart the user has already read (§A.0.5, `00-plan.md` §2 row 13). Reading the current
 *    library row instead would be a smaller model that tells a different story every release.
 *
 * 2. **Nullable in the schema is nullable here.** A prescription is reps *or* seconds *or* neither
 *    and the database has a CHECK that says so; collapsing that into a non-null `value: Int` with a
 *    unit tag would move the failure from the write to the timeline compiler, three screens later.
 *
 * *Rejected* — a Compose `@Immutable` annotation on these classes, which `01-shell.md` §B uses in its
 * sketch. `calendar/CalendarModels.kt` is the house reference and is Compose-free, and the data layer
 * having no opinion about a UI toolkit is worth more than the skipping it would buy on a handful of
 * list-carrying aggregates. If a recomposition problem ever shows up, the fix belongs at the point of
 * use, where someone can measure it.
 *
 * *Rejected* — one file per model. The set is only meaningful together: a `SessionSummary` with no
 * `Engine` cannot be read, and a `RoutineBest` with no `BestMetric` cannot be printed. Splitting it
 * would produce twenty files whose imports all point at each other.
 */

// ─── Enumerations that are also storage tokens ──────────────────────────────────────────────────

/**
 * How a routine is scored and paced — the `routine_version.engine` column.
 *
 * The constant names are **exactly** the strings the schema's CHECK constraint permits, so the store
 * layer reads and writes them with `name` and `valueOf` and no mapping table. A mapping table is the
 * thing that drifts: it compiles perfectly while spelling one case wrong.
 *
 * All seven exist from schema v1 even though Phase 1 only compiles two (`00-plan.md` §6). The schema
 * is not phased, only the UI is, and an engine missing from the enum would make a Phase-2 seed row
 * unreadable by a Phase-1 build that has already stored it.
 *
 * The **display word** is not here, for the reason [Tier]'s KDoc gives at length: a label passed as a
 * constructor argument is fixed at class-init and cannot be re-resolved when the user changes
 * language. See [Engine.label].
 */
enum class Engine {
    INTERVAL_CIRCUIT,
    AMRAP,
    FOR_TIME,
    FOR_TIME_WITH_REST,
    EMOM,
    EMOM_ASCENDING,
    FIXED_SETS,
    ;

    companion object {
        /** Null rather than a throw: an unreadable row is a fault to report, not a crash. */
        fun fromStorage(raw: String?): Engine? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * The word a user reads for this engine.
 *
 * [Engine.FOR_TIME_WITH_REST] is a **composition**, not a literal: 完走 ・ 休息あり is `FOR_TIME`'s own
 * word, the list separator, and a qualifier. The separator comes from [Strings.fmt] because it is
 * ` ・ ` in Japanese and ` · ` in English — a label with the middle dot baked in would have carried CJK
 * punctuation into the Latin table, and would also have let the two spellings of 完走 drift apart.
 */
fun Engine.label(strings: Strings): String = when (this) {
    Engine.INTERVAL_CIRCUIT -> strings.gymShared.engineIntervalCircuit
    Engine.AMRAP -> strings.gymShared.engineAmrap
    Engine.FOR_TIME -> strings.gymShared.engineForTime
    Engine.FOR_TIME_WITH_REST ->
        strings.gymShared.engineForTime + strings.fmt.separator + strings.gymShared.engineWithRest
    Engine.EMOM -> strings.gymShared.engineEmom
    Engine.EMOM_ASCENDING -> strings.gymShared.engineEmomAscending
    Engine.FIXED_SETS -> strings.gymShared.engineFixedSets
}

/**
 * The movement pattern a station loads — `exercise.pattern`.
 *
 * Declaration order is the display order and it is not alphabetical: 押す → 引く → しゃがむ → 股関節 →
 * 体幹 → 移動 → 跳ぶ, which is the ACSM alternation design §9 forbids reordering
 * (`04-library-records.md` §3, EXERCISE_INDEX edge case 2). Sorting this list would quietly undo a
 * decision made about fatigue.
 *
 * There is deliberately no `H_PULL`. §7.1 lists none, and the one thing `pattern` is consumed for —
 * the builder's adjacent-station warning — *wants* a ring row and a pull-up to collide, because they
 * load the same lats (§A.1).
 */
enum class Pattern {
    H_PUSH,
    V_PULL,
    SQUAT,
    HINGE,
    CORE,
    LOCOMOTION,
    PLYO,
    ;

    companion object {
        fun fromStorage(raw: String?): Pattern? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * The word a user reads for this pattern — the station picker's section headings, and the exercise
 * pages' meta line.
 *
 * The `when` is exhaustive rather than a map lookup so that adding an eighth pattern is a compile
 * error here as well as in the table. A map with a `?: ""` default is how a heading ships blank.
 */
fun Pattern.label(strings: Strings): String = when (this) {
    Pattern.H_PUSH -> strings.gymShared.patternHorizontalPush
    Pattern.V_PULL -> strings.gymShared.patternVerticalPull
    Pattern.SQUAT -> strings.gymShared.patternSquat
    Pattern.HINGE -> strings.gymShared.patternHinge
    Pattern.CORE -> strings.gymShared.patternCore
    Pattern.LOCOMOTION -> strings.gymShared.patternLocomotion
    Pattern.PLYO -> strings.gymShared.patternPlyo
}

/**
 * What a station asks for — `routine_station.prescription_kind`, and the builder's はかり方 chips.
 *
 * The two names are the same thing seen from either end, and the spec uses both: the schema calls it
 * a prescription kind, the picker calls it a measure. One type, named for the user-facing word,
 * because that is the one that appears in a string table.
 *
 * [MAX_EFFORT] carries neither a rep count nor a duration and the database enforces that. It is how
 * マーフ's run legs are represented — a `DURATION` with a NULL seconds value is refused by the CHECK,
 * so an open-ended segment closed by 済 is the honest encoding (§F.5).
 */
enum class Measure {
    REPS,
    DURATION,
    MAX_EFFORT,
    ;

    companion object {
        fun fromStorage(raw: String?): Measure? = entries.firstOrNull { it.name == raw }
    }
}

/** The はかり方 chip's word. */
fun Measure.label(strings: Strings): String = when (this) {
    Measure.REPS -> strings.gymShared.measureReps
    Measure.DURATION -> strings.gymShared.measureDuration
    Measure.MAX_EFFORT -> strings.gymShared.measureMaxEffort
}

/**
 * A routine's difficulty band — `routine.tier`, nullable because a user routine has none until
 * `derivedTier` guesses one.
 *
 * The stored value is a **Japanese-spelled token**, because that is what the schema's CHECK constraint
 * spells (`tier IN ('入門','中級','上級')`). It stays that way in every language, exactly as
 * `ThemeRepository` still stores `"amoled"` for a mode now called Sumi: what is on disk means what it
 * has always meant, and nothing on disk has to move.
 *
 * That is not merely convenient. SQLite 3.28 (minSdk 29) cannot alter a CHECK constraint without
 * rebuilding the table, and `routine` carries mutual foreign keys — so translating this token would
 * have cost a table rebuild *and* orphaned every existing row's tier. Keeping it opaque costs
 * nothing (`.planning/i18n/DECISIONS.md` §L3).
 *
 * The **display word** is not here. It used to be, as a constructor argument, which meant it was
 * fixed at class-init and could not be re-resolved when the user changed language. It now lives in
 * the string table; see `Tier.label(Strings)`.
 *
 * Not to be confused with the player's scaling tier (やさしい / 基本 / きつい). Those are *separate
 * stored routines* joined by `scaled_from_routine_id` (`04-library-records.md` §1), not values of
 * this enum. Nor with `session.tier`, a different column holding ASCII enum names.
 */
enum class Tier(val storageValue: String) {
    BEGINNER("入門"),
    INTERMEDIATE("中級"),
    ADVANCED("上級"),
    ;

    companion object {
        fun fromStorage(raw: String?): Tier? = entries.firstOrNull { it.storageValue == raw }
    }
}

/**
 * The word a user reads for this tier.
 *
 * An extension rather than a property so the enum carries no language at all — a label on the enum
 * is resolved once, at class-init, and would silently keep the language the app started in.
 */
fun Tier.label(strings: Strings): String = when (this) {
    Tier.BEGINNER -> strings.gym.tierBeginner
    Tier.INTERMEDIATE -> strings.gym.tierIntermediate
    Tier.ADVANCED -> strings.gym.tierAdvanced
}

/**
 * Which record a routine's card shows — `routine_version.primary_metric` and `personal_record.metric`.
 *
 * **Deliberately carries no label.** Four of the five have documented copy (最速 / 最高巡数 / 最高反復 /
 * 最高負荷, `04-library-records.md` §6) and `HIGHEST_STEP` has none anywhere in the specs. Giving four
 * of them a label and inventing the fifth would put an unsourced Japanese string in the data layer,
 * which is the one thing no part of this feature is allowed to do. The wording is decided by the pure
 * `bestTilesFor` / `bestValueCopy`, which are engine-aware and can say so.
 */
enum class BestMetric {
    BEST_TIME,
    MOST_ROUNDS,
    MOST_REPS,
    MOST_VOLUME,
    HIGHEST_STEP,
    ;

    companion object {
        fun fromStorage(raw: String?): BestMetric? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * A segment's kind — `session_result.phase`, and the player's state machine
 * (`03-player.md`, shared vocabulary).
 *
 * Declared here rather than beside the state machine because [SegmentResultDraft] is a repository
 * argument and needs it to compile. The player imports this one; a second `Phase` would let a
 * timeline and a stored row disagree about what a row means, silently.
 *
 * [PREPARE] and [COMPLETE] never reach `session_result` — the schema stores only WORK/REPS/REST — but
 * they are phases of the same machine and splitting the enum in two would make every transition a
 * conversion.
 */
enum class Phase {
    PREPARE,
    WORK,
    REPS,
    REST,
    COMPLETE,
    ;

    companion object {
        fun fromStorage(raw: String?): Phase? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * どうでしたか — the one thing the app asks the user, and the only source of a CR10 value.
 *
 * The mapping to CR10 is **frozen onto the session row** at write time (`session.rating_cr10`,
 * §A.0.5). If a later version decides きつい is a 8 rather than a 9, every historical chart must stay
 * where it is; this enum is the current truth for new work only.
 *
 * The rating is skippable, and therefore nullable everywhere it appears. An unrated session has no
 * load and must not be assigned one — `COALESCE(rating_cr10, 7)` would be fabricating the user's
 * perceived exertion, which is precisely why the month grid is drawn from active minutes instead
 * (§D.3).
 */
enum class Rating(val cr10: Int) {
    EASY(4),
    JUST_RIGHT(7),
    HARD(9),
    ;

    companion object {
        fun fromStorage(raw: String?): Rating? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * どうでしたか's three answers.
 *
 * The label left the constructor; [Rating.cr10] did **not**, and must not. It is a frozen numeric
 * mapping written onto `session.rating_cr10` at record time — a number, not a word, and nothing about
 * language may move it.
 *
 * Note きつい is also `ScalingTier.HARD`'s word and they are unrelated concepts. Two keys, deliberately.
 */
fun Rating.label(strings: Strings): String = when (this) {
    Rating.EASY -> strings.gymShared.ratingEasy
    Rating.JUST_RIGHT -> strings.gymShared.ratingJustRight
    Rating.HARD -> strings.gymShared.ratingHard
}

// ─── The catalogue ──────────────────────────────────────────────────────────────────────────────

/**
 * One movement — a row of `exercise`, held in memory for the life of the process.
 *
 * [difficulty] is the **current** coefficient and nothing historical may be computed from it: every
 * `session_result` froze its own copy, which is what makes retuning a coefficient a safe, ordinary
 * seed update rather than a rewrite of the past (§A.0.5, `00-plan.md` §2 row 13).
 *
 * [secondsPerRep] means two different things and the schema comment says so. For a rep movement it is
 * a pacer estimate, used by the builder's 目安 line and by nothing that scores. For an isometric it is
 * redefined as *the seconds that count as one volume unit* — ten, so a thirty-second plank scores
 * three (§F.1). That redefinition is the fix for design §7.4's formula, which scores a plank as zero
 * because `actual_reps` is NULL for a hold, and therefore makes 七分間 — one third holds — read as
 * half a workout (§D.5).
 *
 * [ladderId] groups the progression family the exercise belongs to ('push'), which is what
 * `ExerciseCatalog.ladder` and 動きごと's いちばん上 roll-up are keyed on (§F.1,
 * `04-library-records.md` §4). **It is named in §F.1 but absent from §A.1's DDL** — see the report
 * for Track A; the column has to exist for either of those to work.
 */
data class Exercise(
    val id: String,
    val nameJa: String,
    val nameEn: String,
    val pattern: Pattern,
    val secondsPerRep: Double,
    val difficulty: Double,
    val isIsometric: Boolean,
    val cue: String?,
    val ladderId: String?,
    val catalogVersion: Int,
    val archived: Boolean,
)

// ─── Routines ───────────────────────────────────────────────────────────────────────────────────

/**
 * One station of one routine version, with its exercise already resolved.
 *
 * [exercise] is nullable and [exerciseId] is not, which is the entire point of carrying both. A
 * routine referencing an id the catalogue no longer knows still has to *list* — the library index
 * renders it, the detail page says 種目 一件が不明 and disables 始める there rather than failing
 * silently at compile time (`04-library-records.md` §3). Resolving to null and keeping the id is what
 * lets the page be specific about what is missing.
 */
data class RoutineStation(
    val position: Int,
    val exerciseId: String,
    val exercise: Exercise?,
    val measure: Measure,
    val prescribedReps: Int?,
    val prescribedSeconds: Int?,
    val note: String?,
)

/**
 * A routine as a scrolling list needs it — identity plus its head version's headline, plus how the
 * user has done at it. The projection behind `02-data.md` §D.7's single join.
 *
 * The denormalised [stationCount] / [estimatedDurationSeconds] / [estimatedTotalReps] come off the
 * version row rather than being computed from the stations, and that is **not** premature
 * optimisation: the card renders 「十二種目 ・ 三十秒 / 十秒 ・ 約七分」 for every row on screen, and
 * deriving it would be an N+1 over `routine_station`. It is safe precisely because a
 * `routine_version` is immutable — there is no staleness window to manage (§A.3).
 *
 * [lastActiveMs] arrives from a bare column beside a `MAX(started_at)`, which SQLite defines and
 * standard SQL does not (§D.7). It is the 「前回より 二十二秒 速い」 baseline for free; the *query* is
 * where that has to be commented, or the next reader replaces it with a correlated subquery.
 *
 * [archivedAt] is always null in `GymRepository.routines`, which excludes archived rows. It is on the
 * projection anyway so the archived-routine paths reuse the same shape rather than growing a
 * near-identical twin.
 */
data class RoutineSummary(
    val routineId: String,
    val versionId: Long,
    val name: String,
    val engine: Engine,
    val builtIn: Boolean,
    val tier: Tier?,
    val origin: String?,
    val favourite: Boolean,
    val sortOrder: Int,
    val rounds: Int?,
    val timeCapSeconds: Int?,
    val intervalSeconds: Int?,
    val restBetweenStations: Int,
    val restBetweenRounds: Int,
    val stationCount: Int,
    val estimatedDurationSeconds: Int,
    val estimatedTotalReps: Int,
    val primaryMetric: BestMetric,
    val timesDone: Int,
    val lastStartedAt: Long?,
    val lastActiveMs: Long?,
    val best: RoutineBest?,
    val archivedAt: Long?,
) {
    val archived: Boolean get() = archivedAt != null
}

/**
 * An immutable `routine_version` with its stations — the exact shape something was, or is about to be,
 * performed as.
 *
 * This is the anti-March/April type. A session pins `routine_version_id` forever, so re-reading a
 * March session renders March's routine even though April rewrote it (§A.0.4). Versions are never
 * updated and never deleted, so nothing here needs a refresh path.
 *
 * [structuralHash] is a content address, and it is what makes reseeding idempotent: an `onOpen` that
 * runs the seeder over a database a crashed upgrade already half-seeded must not pile up identical
 * versions (§B.3).
 */
data class RoutineSnapshot(
    val versionId: Long,
    val routineId: String,
    val versionNumber: Int,
    val name: String,
    val engine: Engine,
    val rounds: Int?,
    val timeCapSeconds: Int?,
    val intervalSeconds: Int?,
    val restBetweenStations: Int,
    val restBetweenRounds: Int,
    val prepareSeconds: Int,
    val progressionProgramId: String?,
    val primaryMetric: BestMetric,
    val stationCount: Int,
    val estimatedDurationSeconds: Int,
    val estimatedTotalReps: Int,
    val structuralHash: Int,
    val createdAt: Long,
    val stations: List<RoutineStation>,
) {
    /** 始める is disabled on this, with a reason, rather than compiling a routine with a hole in it. */
    val hasUnknownExercise: Boolean get() = stations.any { it.exercise == null }
}

/**
 * One routine at its head version, everything `GYM.LIBRARY.DETAIL` renders in one read.
 *
 * [bests] is a list rather than a single value because the 最高 tiles are engine-dependent: an AMRAP
 * shows 最高巡数 *and* 最高反復 side by side (`04-library-records.md` §3, edge case 6). [timesDone] is
 * carried separately from `bests.size` because it is the third tile and it is also the thing 削除's
 * copy branches on — count zero means the routine is purged outright, count above zero means it is
 * archived and the records survive.
 *
 * [archivedAt] rather than a boolean: the detail page's 削除済み state needs to exist, and the sort
 * order of a restore queue would need the instant. A boolean throws that away for nothing.
 */
data class RoutineDetail(
    val routineId: String,
    val builtIn: Boolean,
    val tier: Tier?,
    val origin: String?,
    val favourite: Boolean,
    val derivedFromRoutineId: String?,
    val scaledFromRoutineId: String?,
    val createdAt: Long,
    val archivedAt: Long?,
    val snapshot: RoutineSnapshot,
    val bests: List<RoutineBest>,
    val recentAttempts: List<SessionSummary>,
    val timesDone: Int,
) {
    val archived: Boolean get() = archivedAt != null
}

/**
 * One station as the builder holds it, before anything has been written.
 *
 * Separate from [RoutineStation] because a draft has no position (the list index is the position) and
 * no resolved [Exercise] — the picker hands back an id, and resolution is the catalogue's job at
 * render time. Merging the two would give the builder a field it must keep consistent for no reason.
 */
data class StationDraft(
    val exerciseId: String,
    val measure: Measure,
    val reps: Int?,
    val seconds: Int?,
    val note: String? = null,
)

/**
 * What `GYM.LIBRARY.BUILDER` accumulates and `saveRoutine` consumes. The builder writes exactly once,
 * on an explicit 保存 (`04-library-records.md` §3).
 *
 * [routineId] null means a new routine; non-null means an edit, which inserts a **new version** and
 * repoints the head rather than updating anything. Editing a built-in is a copy-on-write into a new
 * user routine and `saveRoutine` therefore returns an id that may not be the one passed in — the
 * caller has to adopt it, because the built-in is genuinely unchanged (§A.0.3, §C.4).
 */
data class RoutineDraft(
    val routineId: String?,
    val name: String,
    val engine: Engine,
    val stations: List<StationDraft>,
    val rounds: Int?,
    val timeCapSeconds: Int?,
    val intervalSeconds: Int?,
    val restBetweenStations: Int,
    val restBetweenRounds: Int,
    val prepareSeconds: Int,
)

// ─── Progressions ───────────────────────────────────────────────────────────────────────────────

/** Whether a program's rows are steps you earn (Recon Ron) or days you rotate through (Armstrong). */
enum class StepUnit { STEP, DAY }

/** How a program decides you have finished a step — `progression_program.advance_rule`. */
enum class AdvanceRule { SESSIONS_COMPLETED, WEEKS_ELAPSED, ALL_SETS_MADE, MANUAL }

/**
 * The shape of one step, and the reason `progression_step` is not a flat rep table.
 *
 * Four of Armstrong's five days cannot be written as numbers — day 1 is five sets to failure, day 2 is
 * a pyramid to failure, day 4 is max sets at a chosen count, day 5 repeats day 4 (§F.3). A schema that
 * only stored reps could represent one fifth of the programme.
 */
enum class StepShape { FIXED, MAX_EFFORT, PYRAMID, LADDER, GRIP_ROTATION }

/** Grip for Armstrong's day 3, the only step that carries per-set variants — 順手・狭手・逆手 (§F.3). */
enum class SetVariant { OVERHAND, CLOSE, REVERSE }

/** One prescribed set within a step. [reps] is null when the step's [StepShape] resolves at run time. */
data class ProgressionSet(
    val setIndex: Int,
    val reps: Int?,
    val variant: SetVariant?,
)

/**
 * One rung of a progression.
 *
 * [totalReps] is the *published* total, stored so the regression test can be a plain SQL assertion
 * rather than a hand-maintained Kotlin fixture — every Recon Ron row must both sum to it and satisfy
 * `24 + 2 × stepIndex` (§A.5). It is not used for anything at run time and must not become so.
 */
data class ProgressionStep(
    val stepIndex: Int,
    val labelJa: String?,
    val shape: StepShape,
    val totalReps: Int?,
    val restSeconds: Int,
    val noteJa: String?,
    val sets: List<ProgressionSet>,
)

/**
 * Where the user stands in a progression, joined to the program that defines it.
 *
 * Carries [currentStep] resolved rather than just [currentStepIndex] because `GYM.LIBRARY.DETAIL`
 * renders the current step's sets inline — 「第七段 ・ 七 六 五 四 四」 under a 「十八段のうち」 caption —
 * and re-reading the step table per frame for one line would be a query behind a label
 * (`04-library-records.md` §3, edge case 5). [stepCount] rides along for the same caption.
 *
 * A program whose numbers are ever corrected gets a **new id**, never an edit: `progression_step`
 * rows are replaced wholesale on reseed, and rewriting them would retroactively change what a past
 * session prescribed (§A.5).
 */
data class ProgressionState(
    val programId: String,
    val nameJa: String,
    val stepUnit: StepUnit,
    val stepCount: Int,
    val advanceRule: AdvanceRule,
    val advanceParam: Int?,
    val cycleDays: Int?,
    val origin: String,
    val noteJa: String?,
    val currentStepIndex: Int,
    val currentStep: ProgressionStep?,
    val sessionsAtStep: Int,
    val stepEnteredAt: Long,
    val lastSessionId: Long?,
    val cycleDay: Int,
)

// ─── The live session ───────────────────────────────────────────────────────────────────────────

/**
 * Every clock anchor a session needs to survive process death, persisted on the session row.
 *
 * The session clock is [startedAtElapsedMs] — `SystemClock.elapsedRealtime()` — because it is
 * monotonic, includes deep sleep, and is untouched by the system clock. That is what makes a for-time
 * PR unfakeable by winding the wall clock (§E.4).
 *
 * [bootAnchorMs] is the price of using it. `elapsedRealtime` resets to ~0 on reboot, so a stored
 * elapsed origin from a previous boot makes duration run *backwards*. `currentTimeMillis −
 * elapsedRealtime` is approximately the instant the device booted and is stable within a boot to a
 * few milliseconds, so comparing it is how a reboot is detected — and a rebooted session loses 続ける
 * entirely, because resuming would fabricate time (`03-player.md` §E.3).
 *
 * Verbatim from `03-player.md` §A. Track C's `resumability` and `reanchor` read exactly these fields.
 */
data class PersistedClock(
    val startedAtElapsedMs: Long,
    val pausedAccumulatedMs: Long,
    val pausedAtElapsedMs: Long?,
    val pausedAtWallMs: Long?,
    val bootAnchorMs: Long,
    val anchorWallMs: Long,
)

/**
 * One completed segment, as the player hands it to the store on a phase transition.
 *
 * Written **on the transition out**, not during, and roughly two to four times a minute — which is
 * trivially cheap and buys full process-death recovery. It is idempotent under
 * `idx_result_session (session_id, ordinal)`, so a retry after a crash cannot double-count
 * (§A.7, `03-player.md` §A).
 *
 * Verbatim from `03-player.md` §A. Note it carries no volume: `volume_units` is computed at write
 * time by the repository, from the exercise's *current* coefficient, and frozen on the row (§D.5).
 * The player must not compute it, or two code paths will drift.
 */
data class SegmentResultDraft(
    val ordinal: Int,
    val phase: Phase,
    val stationIndex: Int?,
    val exerciseId: String?,
    val round: Int,
    val prescribedReps: Int?,
    val actualReps: Int?,
    val plannedMs: Long,
    val actualMs: Long,
    val addedMs: Long,
    val skipped: Boolean,
    val closedAtWallMs: Long,
    val closedAtElapsedMs: Long,
)

/**
 * One `session_result` row read back — for the breakdown, and for replaying a resumed session's
 * timeline to where it left off (`03-player.md` §E.2).
 *
 * [difficultyCoefficient] and [volumeUnits] are the frozen copies, not lookups. They are what let a
 * coefficient be retuned in a later release without a single historical chart moving (§A.0.5).
 *
 * Field names match the replay call sites in `03-player.md` §E.2 (`actualMs`, `addedMs`) rather than
 * the column names, because the timeline is where they are consumed and a rename at that boundary
 * would be one more thing to get right at 3am.
 */
data class SegmentResult(
    val ordinal: Int,
    val phase: Phase,
    val roundIndex: Int,
    val stationOrder: Int,
    val exerciseId: String?,
    val measure: Measure?,
    val prescribedReps: Int?,
    val prescribedSeconds: Int?,
    val actualReps: Int?,
    val actualMs: Long,
    val addedMs: Long,
    val skipped: Boolean,
    val difficultyCoefficient: Double,
    val volumeUnits: Double,
    val closedAtWallMs: Long,
    val closedAtElapsedMs: Long,
)

/**
 * Whether an unfinished session can be picked back up, and what the prompt is therefore allowed to
 * offer (`03-player.md` §E.3).
 *
 * [REBOOTED] and [STALE] both **remove** 続ける rather than disabling it — an option that will never
 * be available is chrome, not information, and a resume across a reboot would credit time the device
 * spent switched off. [NOTHING_TO_SAVE] removes 記録する instead, leaving 捨てる and a dismiss: auto
 * discarding is tempting and wrong, because the user should learn that this app never deletes
 * silently.
 */
enum class Resumability { RESUMABLE, REBOOTED, STALE, NOTHING_TO_SAVE }

/**
 * The one session that may be open, read back after a quit, a crash or process death.
 *
 * At most one can exist, and that is a **schema** guarantee rather than a convention:
 * `idx_session_live` is a unique partial index on `finished_at WHERE finished_at IS NULL`, which makes
 * "two live sessions" unrepresentable. Without it, a crash mid-session followed by a fresh start
 * leaves two unfinished rows and the resume banner picks one arbitrarily (§A.6).
 *
 * [routineName] is the denormalised copy from the session row, and [routineExists] says whether the
 * routine is still there. Together they are how a つづき banner renders for a session whose routine was
 * deleted — the one case the version FK cannot serve (`00-plan.md` §2 row 10). Never join for this
 * name.
 *
 * [tier] is the raw column value rather than an enum: `startSession` takes it as a `String?` in §C.2,
 * and the scaled tiers it records are separate routines rather than values of [Tier].
 */
data class ResumableSession(
    val sessionId: Long,
    val routineId: String,
    val routineVersionId: Long,
    val routineName: String,
    val routineExists: Boolean,
    val tier: String?,
    val engine: Engine,
    val startedAtWallMs: Long,
    val lastWriteWallMs: Long,
    val stationsPlanned: Int,
    val roundsPlanned: Int?,
    val clock: PersistedClock,
    val results: List<SegmentResult>,
    val resumability: Resumability,
)

/**
 * Everything `GYM.SESSION.COMPLETE` renders, returned by the finish transaction so the record screen
 * never queries after a finish.
 *
 * [previous] is read **inside** the same transaction, and that is not an optimisation. Read it after
 * the commit and the just-finished session is its own baseline, and the screen renders
 * 「前回より 0秒 速い」 (§E.5).
 *
 * [newBests] is whatever this session took, decided against the cached `personal_record` rows within
 * the transaction. A partial session is a first-class outcome — fully recorded, honest numbers — and
 * is excluded from PR eligibility only (§C.2, §D.2), so this list is simply empty for one.
 *
 * [streakDays] is here because `03-player.md`'s COMPLETE spec calls a `streakDays()` that §C never
 * declares. Carrying it on the outcome honours §C.2's promise that the record screen issues no
 * follow-up query, without adding a repository method the data spec does not have. Null means the
 * streak is unknown, and the line is then **omitted** — never drawn as `—`.
 */
data class SessionOutcome(
    val summary: SessionSummary,
    val results: List<SegmentResult>,
    val snapshot: RoutineSnapshot?,
    val previous: SessionSummary?,
    val newBests: List<RoutineBest>,
    val progression: ProgressionState?,
    val streakDays: Int?,
)

// ─── History and records ────────────────────────────────────────────────────────────────────────

/**
 * Everything that has ever been finished, as two numbers — `GymRepository.summary()`'s answer.
 *
 * It exists because two shipped UI elements are unfeedable without it and both were left incomplete
 * rather than faked: `GYM.RECORDS.INDEX`'s third tile これまで, and `GYM.RECORDS.HISTORY`'s unfiltered
 * subtitle 八十六回 ・ 二千四百分 (`04-library-records.md` §4's mock and §6's tile table; `DECISIONS.md`
 * §Q22 is the ruling that added the read).
 *
 * **Lifetime, not a window, and that is the whole point.** *Rejected* — deriving it from anything
 * already on the interface, which is what §Q22 examined and refused three times over: `routines`'
 * `timesDone` misses archived routines, `routineBests` misses routines only ever done partially, and
 * `weeklySeries(52)` is a year. A number that is *nearly* the lifetime total is worse than no tile,
 * because nobody looking at it can tell that it is wrong.
 *
 * [totalActiveMs] is summed over `active_ms`, so it excludes pauses exactly as [SessionSummary] does —
 * the subtitle's 分 and every hero time on the page are then the same measurement, and 活動時間 means
 * one thing in this feature.
 *
 * Counts **finished** sessions only. An open row is not yet a record of anything, and a live session
 * must not make the tile tick.
 */
data class LifetimeSummary(val sessions: Int, val totalActiveMs: Long)

/**
 * One finished session as a list row.
 *
 * [routineName] and [stationsPlanned] are the frozen copies from the session row. The name survives
 * the routine's deletion (§A.6); the denominator survives the *shape* changing, because the 「二十種目
 * 中 八」 chip has to say the twenty that was on screen, and open rep segments and skips can move a
 * derived one (§A.6).
 *
 * [activeMs] excludes pauses and is always what the hero time shows. [wallMs] includes them and is
 * kept for honesty. **They are not interchangeable** — if wall-clock is ever displayed it needs a
 * different label (`04-library-records.md` §4, edge case 6).
 *
 * [personalBest] is whether this session held a record *when asked*, recomputed on read rather than
 * stored, so beating an old PR demotes the old row's chip with no migration and no orphan
 * (`04-library-records.md` §4, edge case 2).
 */
data class SessionSummary(
    val sessionId: Long,
    val routineId: String,
    val routineVersionId: Long,
    val routineName: String,
    val engine: Engine,
    val tier: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val localDate: LocalDate,
    val activeMs: Long,
    val wallMs: Long,
    val complete: Boolean,
    val reachedTimeCap: Boolean,
    val roundsPlanned: Int?,
    val roundsCompleted: Int,
    val stationsPlanned: Int,
    val stationsCompleted: Int,
    val totalReps: Int,
    val volumeUnits: Double,
    val rating: Rating?,
    val personalBest: Boolean,
) {
    /**
     * Stable list key, following `CalendarEvent.key`. The row id is durable here in a way an
     * Instances id is not, so it needs no composite.
     */
    val key: String get() = sessionId.toString()
}

/**
 * One finished session reopened, read-only.
 *
 * [snapshot] is the **pinned** version, and the breakdown renders against it rather than the live
 * routine: a station removed last week still appears in a session performed before it was removed.
 * That is the entire justification for the version pin. It is nullable for the one case that outlives
 * a schema — a restored backup predating the pin — where the page says 当時の内容は残っていません and
 * fabricates nothing (`04-library-records.md` §4, edge cases 1 and 4).
 *
 * [previous] is the session immediately before *this one* by `started_at`, scoped to the same routine
 * and tier — **not** the most recent overall. Reopening a March record must not compare it to
 * yesterday, and comparing an Rx run to a やさしい run is meaningless (§4, edge case 3).
 *
 * [isStillBest] separates 自己最高 from 当時の自己最高. A record since beaten must not keep claiming the
 * crown, and demoting it on read rather than on write means there is nothing to migrate.
 */
data class SessionDetail(
    val summary: SessionSummary,
    val results: List<SegmentResult>,
    val snapshot: RoutineSnapshot?,
    val previous: SessionSummary?,
    val best: RoutineBest?,
    val isStillBest: Boolean,
    val routineArchived: Boolean,
)

/**
 * The keyset cursor for `GYM.RECORDS.HISTORY`, `(started_at, id)` descending.
 *
 * Both columns, always. `OFFSET` skips or duplicates rows when a session is deleted mid-scroll, and a
 * single-column cursor silently drops one of a `started_at` tie — which a restored backup can
 * produce (`04-library-records.md` §4, edge cases 1 and 2).
 *
 * Named `HistoryCursor` rather than §C.3's bare `Cursor`, which would collide with
 * `android.database.Cursor` in exactly the layer that also holds real ones.
 */
data class HistoryCursor(
    val startedAt: Long,
    val sessionId: Long,
)

/**
 * One day of the month ink-grid.
 *
 * [activeMs], not Foster load. §5.1 said session load, but the rating is skippable, so load is NULL
 * for any unrated session and `COALESCE(rating_cr10, 7)` would be inventing the user's exertion to
 * colour a dot. Active minutes are always known and never invented; load stays where a real rating
 * exists, in the monotony maths (§D.3).
 *
 * [bucket] is 0..3 and is assigned against a [LoadScale] computed **once** over a trailing window,
 * not recomputed per query. A per-query `NTILE` would make March's dots visibly change when April
 * turns out to be heavy — the grid rewriting its own history.
 *
 * Days absent from a month's map are rest days and render blank. There is no zero-bucket row for them,
 * because a day with no session is not a day with a small session.
 */
data class DayLoad(
    val activeMs: Long,
    val sessions: Int,
    val bucket: Int,
)

/**
 * The three thresholds that turn active minutes into ink levels — the 40th, 70th and 90th percentiles
 * of the user's own non-zero days over a trailing window.
 *
 * Relative, not absolute, because absolute cutoffs leave a twenty-minute-session user permanently at
 * level one and a marathoner permanently at level four (`04-library-records.md` §4, edge case 1).
 *
 * [usedFallback] says the sample was too thin for percentiles to mean anything and fixed cutoffs were
 * used instead. Exposed rather than hidden so a caller can decide whether to explain itself, and so a
 * test can tell the two regimes apart.
 */
data class LoadScale(
    val p40Ms: Long,
    val p70Ms: Long,
    val p90Ms: Long,
    val nonZeroDays: Int,
    val usedFallback: Boolean,
)

/**
 * 連続 — consecutive days the plan was honoured, walked backwards from today (§D.1).
 *
 * [endedOn] is the day the walk stopped, or null when it ran to the lookback limit without breaking.
 * Carrying the day rather than a bare boolean means the copy can be specific later without a second
 * query.
 *
 * [forgivenThisMonth] is reported but the remaining budget deliberately is **not** — the records page
 * says ゆるし 一回 使いました and never how many are left, because a countdown invites gaming it
 * (`04-library-records.md` §6).
 *
 * `days == 0` renders 連続は とぎれています, never 〇日 連続. Zero is not a small streak.
 */
data class Streak(
    val days: Int,
    val forgivenThisMonth: Int,
    val endedOn: LocalDate?,
)

/**
 * One routine's record — a `personal_record` row, joined to enough of the routine to print it.
 *
 * A PR requires `complete = 1` in every engine. A quit AMRAP is not a round count, a quit for-time is
 * not a time, and a PR system that rewards quitting early is worse than none (§D.2).
 *
 * [tiebreak] carries an AMRAP's partial reps in the final round, because most-rounds alone cannot
 * separate two identical scores and the finer answer is already recorded. Ties on both break toward
 * the **earlier** session — you set the record the first time you hit it.
 *
 * [structureChanged] is the honest amount to say when a routine was edited after the record was set.
 * Deciding whether a twelve-station circuit's PR is invalidated by a thirteenth is a policy this app
 * cannot get right, so it states the fact and declines the judgement (`04-library-records.md` §4,
 * edge case 4).
 */
data class RoutineBest(
    val routineId: String,
    val routineName: String,
    val engine: Engine,
    val metric: BestMetric,
    /** Milliseconds for [BestMetric.BEST_TIME]; a count or a unitless volume otherwise. */
    val value: Double,
    val tiebreak: Double,
    val sessionId: Long,
    val achievedAt: Long,
    val localDate: LocalDate,
    val timesDone: Int,
    val routineArchived: Boolean,
    val structureChanged: Boolean,
)

/**
 * One movement's record, rolled up across its whole ladder.
 *
 * Keyed by [ladderId] rather than by exercise, because seven near-identical push-up rows would be
 * unreadable: 腕立て伏せ's row reports [hardestReachedExerciseName] as いちばん上 instead
 * (`04-library-records.md` §4, edge case 6).
 *
 * [singleSetReps] counts **only sets where `actual_reps` was recorded**. A prescribed-but-unverified
 * twenty is not a record, it is a plan — this is the difference between a record and a wish, and it is
 * also why 動きごと can be legitimately empty for a user who only ever trains duration stations
 * (`04-library-records.md` §3, edge case 4).
 */
data class MovementBest(
    val ladderId: String?,
    val exerciseId: String,
    val exerciseName: String,
    val singleSetReps: Int,
    val lifetimeReps: Int,
    val sessionId: Long?,
    val lastPerformedAt: Long?,
    val lastLocalDate: LocalDate?,
    val hardestReachedExerciseId: String?,
    val hardestReachedExerciseName: String?,
)

/**
 * One week of the two weekly charts, which share a scan because splitting them would double it (§D.4).
 *
 * [weekStart] is the stored ISO-Monday `local_week_start`, computed in Kotlin at write time. Never
 * `strftime('%W')`, which is UTC *and* uses non-ISO week numbering — bucketing in SQL puts a 23:30
 * session in UTC+9 on the wrong day and is the most common bug in fitness-history code (§0).
 *
 * Weeks with no sessions do not appear in the query result and are zero-filled in Kotlin. A recursive
 * CTE date spine would do it in SQL and be two lines longer and untestable off a device.
 */
data class WeekPoint(
    val weekStart: LocalDate,
    val sessions: Int,
    val activeMinutes: Int,
    val completeSessions: Int,
)

/**
 * One day of the 積み上げ series — difficulty-weighted volume, summed from the frozen
 * `session_result.volume_units`.
 *
 * Labelled 目安 wherever it is drawn, because duration stations enter it through an approximation
 * (`04-library-records.md` §4, edge case 5). It is deliberately unitless and is never presented as a
 * rep count.
 */
data class DayVolume(
    val date: LocalDate,
    val volumeUnits: Double,
)

/**
 * One day's Foster load — session RPE × active minutes, summed.
 *
 * [unrated] is the field that keeps this honest. A day holding an unrated session has a partly
 * unknown load, and [monotony] over a window containing one returns null rather than a number
 * standing in for a question the user declined to answer (§D.6).
 */
data class DailyLoad(
    val date: LocalDate,
    val load: Double,
    val sessions: Int,
    val unrated: Int,
)

/**
 * Foster load, its seven-day monotony, and the ratio that is computed and never drawn.
 *
 * Monotony has no SQL alternative on this platform, and the reason is worth keeping written down:
 * SQLite has no `stdev()`, Android's public `SQLiteDatabase` cannot register a custom function, the
 * algebraic workaround needs `sqrt()` which arrived in 3.35 behind a compile flag, and the
 * calculation needs a seven-row date spine with rest days as explicit zeros that `GROUP BY` cannot
 * produce. Kotlin, over a widened query (§D.6).
 *
 * [monotony7d] is null when any day in the window holds an unrated session, and when the standard
 * deviation is zero — seven identical days are undefined, and the honest answer is no answer. Above
 * 2.0 the records page surfaces 同じ調子が続いています, and only once [historyDays] reaches 14.
 *
 * [acwr] is a **governor, never a display**. §7.4 is unambiguous: no injury-risk percentage, no ratio
 * gauge. It exists to cap a ramp at the boring, defensible ten percent per week, and it is suppressed
 * entirely below 28 days of history.
 */
data class TrainingLoad(
    val recent: List<DailyLoad>,
    val monotony7d: Double?,
    val acwr: Double?,
    val historyDays: Int,
)

// ─── The home feed ──────────────────────────────────────────────────────────────────────────────

/**
 * A routine's last outing, for the 「前回 六分五十秒 ・ 三日前」 line.
 *
 * Carries [engine] because what "last time" *means* is engine-dependent — a circuit reports a
 * duration, an AMRAP reports rounds — and `lastResultLine(result, now)` takes nothing else to decide
 * with (`01-shell.md` §B).
 */
data class LastResult(
    val sessionId: Long,
    val startedAt: Long,
    val activeMs: Long,
    val roundsCompleted: Int,
    val complete: Boolean,
    val engine: Engine,
)

/**
 * One card on `GYM.HOME`, verbatim from `01-shell.md` §B.
 *
 * [summary] is a rendered string rather than the parts, because the card shows
 * 「十二種目 ・ 三十秒 / 十秒 ・ 約七分」 and composing that per frame from a projection is work the
 * feed already did once.
 *
 * A [timesDone] of zero hides its line rather than rendering 〇回, and a null [lastResult] or [best]
 * hides theirs. Card heights must therefore not be fixed (`01-shell.md` §B, edge cases 3 and 4).
 */
data class RoutineCard(
    val routineId: String,
    val name: String,
    /**
     * The pieces of the card's one-line shape, **not the sentence**.
     *
     * It used to be a rendered string, composed in `GymStore` because that is where the pieces are.
     * That cannot survive a language switch: the feed re-emits on a *table* change, so every card
     * would keep the sentence it was built with until something else wrote to the database. Copy
     * belongs where it can be re-resolved — the card composable calls `routineCardSummary`.
     */
    val stationCount: Int,
    val workSeconds: Int?,
    val restBetweenStations: Int,
    val estimatedDurationSeconds: Int,
    val timesDone: Int,
    val lastResult: LastResult?,
    val best: RoutineBest?,
    val builtIn: Boolean,
    val origin: String?,
)

/**
 * Everything `GYM.HOME` renders, in one read — verbatim from `01-shell.md` §B.
 *
 * One query, not five. An N+1 over routines for the last-result and PR lines would issue a dozen
 * SQLite reads per frame; a single joined read produces one `Loadable` and one recomposition (§D.7).
 *
 * [everTrained] drives the first-run shape rather than `recent.isEmpty()`, because those are different
 * facts: a user who has trained but only ever run one routine has a legitimately short よく使う list
 * and should not be shown a first-run page.
 */
data class GymHomeFeed(
    val recent: List<RoutineCard>,
    val builtIn: List<RoutineCard>,
    val builtInTotal: Int,
    val userRoutines: List<RoutineCard>,
    val everTrained: Boolean,
)
