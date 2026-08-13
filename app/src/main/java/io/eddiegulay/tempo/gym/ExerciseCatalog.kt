package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.gym.data.ExerciseCatalogSource

/**
 * The eighteen-odd movements, synchronously.
 *
 * The exercises live in the database — `routine_station` and `session_result` hold foreign keys into
 * them and a Kotlin `val` has no referential integrity (§A.0.1) — **and** in a map loaded once at
 * repository construction. Both, not either: the data layer needs the FKs, and the builder's station
 * picker needs to filter on every keystroke without a `Loading` state (`00-plan.md` §2 row 6).
 *
 * The consequence is worth stating loudly, because it reads as an omission otherwise:
 * **`GYM.LIBRARY.STATION_PICKER` and `GYM.LIBRARY.EXERCISE_INDEX` have no Loading state, no Empty
 * state and no Error state.** Eighteen rows in memory costs nothing, the catalogue is seeded before
 * the first read, and a spinner over an in-memory list would be a lie about where the data is. Do not
 * add one (`04-library-records.md` §3).
 *
 * *Rejected* — exposing this as a `Flow<List<Exercise>>` for consistency with everything else in the
 * store. It would be a flow that emits exactly once per process, and it would force every call site —
 * a per-keystroke filter, a station row resolving its name mid-scroll, the timeline compiler resolving
 * `byId` for every segment — into a coroutine to read a constant.
 *
 * *Rejected* — an injected instance rather than an object. The catalogue is genuinely global and
 * immutable after load, and threading it through the compiler, four screens and a picker would be
 * ceremony around a lookup table. The cost is that it must be installed before use, which the
 * repository's own construction guarantees.
 *
 * **This is the declared contract; [ExerciseCatalogSource] is the store's private backing for it.**
 * Every body below is a straight delegation, deliberately: the map is written exactly once, by
 * `GymStore`'s constructor, from the `exercise` table it has just opened, and nothing else may write
 * it. Two public names for one lookup table would guarantee that half the call sites read the one that
 * is still empty, which is why the source object is `internal` and this one is not.
 *
 * The delegation also means **an uninstalled catalogue reads as empty, not as a crash** — `all()` is an
 * empty list and `byId` is null until the repository has been constructed. That is the right failure:
 * a station whose id does not resolve renders 不明な種目 (§C.1), and a picker with no rows is a picker
 * with no rows. *Rejected* — throwing on an uninstalled read, which would turn "the gym was opened
 * before the store finished its first blocking open" into a crash in a launcher.
 */
object ExerciseCatalog {

    /** Every non-archived movement. Retired ones stay in the table for their FKs and are excluded here. */
    fun all(): List<Exercise> = ExerciseCatalogSource.all()

    /** Null for an id the catalogue no longer knows; the caller says 不明な種目 rather than crashing. */
    fun byId(id: String): Exercise? = ExerciseCatalogSource.byId(id)

    /**
     * The progression family this movement belongs to, ordered **easiest → hardest** by difficulty so
     * the ladder reads top-to-bottom as it should be climbed, ties by name
     * (`04-library-records.md` §3).
     *
     * Empty for a movement that is its own ladder — プランク, 走る — which is what `NoLadder` renders
     * from, omitting the 段階 block entirely rather than drawing a ladder of one.
     */
    fun ladder(exerciseId: String): List<Exercise> = ExerciseCatalogSource.ladder(exerciseId)

    /**
     * Grouped for the picker's headings, in [Pattern]'s declaration order — 押す → 引く → しゃがむ →
     * 股関節 → 体幹 → 移動 → 跳ぶ. That order is design §9's alternation, not alphabetical and not by
     * count, so the returned map must preserve insertion order.
     */
    fun byPattern(): Map<Pattern, List<Exercise>> = ExerciseCatalogSource.byPattern()
}
