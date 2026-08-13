package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Pattern

/**
 * The in-memory half of the exercise catalogue — the map [io.eddiegulay.tempo.gym.ExerciseCatalog]
 * reads from.
 *
 * The exercises live in the database, because `routine_station` and `session_result` hold foreign keys
 * into them and a Kotlin `val` has no referential integrity (§A.0.1). They live **also** here, because
 * the builder's station picker filters on every keystroke and the timeline compiler resolves an id per
 * segment, and neither can afford a suspension point to read a lookup table (`00-plan.md` §2 row 6).
 * Eighteen-odd rows in memory costs nothing, and the consequence is worth stating: the picker and the
 * exercise index have **no Loading, Empty or Error state**, and a spinner over an in-memory list would
 * be a lie about where the data is.
 *
 * It is installed once, at repository construction, and never written again — which is what makes the
 * synchronous reads safe without a lock. The `@Volatile` is for the publication of that single write,
 * not for contention.
 *
 * *Rejected* — making this the object the UI calls. `ExerciseCatalog` is the declared contract and
 * lives in the feature package with the models; this is the store's private backing for it. Two public
 * names for one lookup table would guarantee that half the call sites use the one that is empty.
 */
internal object ExerciseCatalogSource {

    @Volatile
    private var byId: Map<String, Exercise> = emptyMap()

    @Volatile
    private var live: List<Exercise> = emptyList()

    fun install(exercises: List<Exercise>) {
        byId = exercises.associateBy { it.id }
        // Retired movements stay in the table for their foreign keys and are excluded from every
        // list the user sees. They remain resolvable by id, because a session that performed one
        // still has to name it.
        live = exercises.filterNot { it.archived }
    }

    fun all(): List<Exercise> = live

    fun byId(id: String): Exercise? = byId[id]

    /**
     * The progression family, easiest → hardest by difficulty so the ladder reads top-to-bottom as it
     * should be climbed, ties by name.
     *
     * Empty — not a list of one — for a movement that is its own ladder, which is what lets
     * `EXERCISE_DETAIL` omit the 段階 block entirely rather than drawing a ladder with a single rung.
     */
    fun ladder(exerciseId: String): List<Exercise> {
        val ladderId = byId[exerciseId]?.ladderId ?: return emptyList()
        return live.filter { it.ladderId == ladderId }
            .sortedWith(compareBy({ it.difficulty }, { it.nameJa }))
    }

    /**
     * Grouped for the picker's headings, in [Pattern]'s declaration order — 押す → 引く → しゃがむ →
     * 股関節 → 体幹 → 移動 → 跳ぶ. That order is design §9's alternation rather than the alphabet, so
     * the map is built by walking the enum and the result preserves insertion order.
     */
    fun byPattern(): Map<Pattern, List<Exercise>> = buildMap {
        Pattern.entries.forEach { pattern ->
            val members = live.filter { it.pattern == pattern }
            if (members.isNotEmpty()) put(pattern, members.sortedBy { it.difficulty })
        }
    }
}
