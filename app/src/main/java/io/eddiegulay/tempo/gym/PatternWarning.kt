package io.eddiegulay.tempo.gym

/*
 * 腕立て伏せ と ディップス は続けて置かない方がよい — the builder's one piece of coaching.
 *
 * Design §6 gives both the sentence and the reason: two adjacent stations sharing a movement pattern
 * mean the second one starts on an already-fatigued muscle and a locally depleted phosphocreatine
 * pool, so you get less total work for the same cost. The whole point of a circuit is that the rest
 * for muscle A is the work interval for muscle B.
 *
 * And then, one line later: **"But it is the user's routine, and a warning is where that ends."**
 * Nothing in this file returns anything `canSave` reads. That is not an oversight to be tidied up.
 *
 * `04-library-records.md` §3's edge case 3 adds the pair design §6 misses, and it is the one everybody
 * misses: **when the routine repeats, the last station is adjacent to the first.** A three-station
 * circuit of 押す ・ しゃがむ ・ 押す reads clean straight down the page and puts two pushes back to
 * back at every round boundary — which is most of the routine, not an edge of it.
 */

/**
 * Two stations that will be performed back to back on the same movement pattern.
 *
 * The indices are in **loop order**, so [firstIndex] is the station that finishes and [secondIndex]
 * the one that starts tired — and for the round-wrap pair that means `(last, 0)`, not `(0, last)`.
 * §3's layout insets the warning line under the *second* of the pair, which is what makes the
 * distinction load-bearing rather than cosmetic: the line belongs to the row it is a warning about.
 */
data class PatternClash(val firstIndex: Int, val secondIndex: Int, val pattern: Pattern)

/**
 * Every adjacent pair of stations sharing a pattern, wrap included.
 *
 * @param patterns one entry per station, in list order. **Null means the catalogue could not resolve
 *   that station's exercise**, and a null never clashes: a warning about a movement the app cannot
 *   name is a warning the user cannot act on, and the missing exercise is already reported as 不明な種目
 *   where it can be acted on.
 * @param wraps whether the station list is performed more than once — §3 edge case 3's `rounds > 1`.
 *   A boolean rather than the round count because the *engine* decides it as often as the number does;
 *   [routineWraps] is the derivation and the draft overload below is what the builder actually calls.
 *
 * A **one-station routine never clashes with itself**, however many rounds it runs. タバタ is eight
 * rounds of one movement and 段階 is one movement by construction, so the alternative fires on two of
 * the three shipped built-ins to tell the user something they chose on purpose.
 *
 * **A pair of indices is warned about once.** In a two-station circuit that repeats, the pair `(0,1)`
 * and the wrap `(1,0)` are the same two rows: two lines reading 腕立て伏せ と ディップス and
 * ディップス と 腕立て伏せ under one another describe one problem as two. Longer lists have no such
 * collision — `(n−1, 0)` is a pair no straight-line adjacency produces — so the de-duplication only
 * ever fires where it is needed.
 */
fun adjacentPatternClashes(patterns: List<Pattern?>, wraps: Boolean): List<PatternClash> {
    if (patterns.size < 2) return emptyList()
    val clashes = mutableListOf<PatternClash>()
    for (index in 0 until patterns.size - 1) {
        val pattern = patterns[index] ?: continue
        if (patterns[index + 1] == pattern) clashes += PatternClash(index, index + 1, pattern)
    }
    if (wraps) {
        val last = patterns.size - 1
        val pattern = patterns[last]
        // `size > 2` is the de-duplication: at exactly two stations the wrap is the pair already
        // reported, seen from the other side.
        if (pattern != null && patterns[0] == pattern && patterns.size > 2) {
            clashes += PatternClash(last, 0, pattern)
        }
    }
    return clashes
}

/**
 * The builder's form: patterns resolved through the catalogue, and [wraps] taken from the draft's own
 * engine and round count.
 *
 * @param pattern the catalogue lookup, passed rather than reached for — the same bargain
 *   `estimateRoutine` makes, and for the same reason: this recomputes on every reorder.
 */
fun adjacentPatternClashes(draft: RoutineDraft, pattern: (String) -> Pattern?): List<PatternClash> =
    adjacentPatternClashes(
        patterns = draft.stations.map { pattern(it.exerciseId) },
        wraps = routineWraps(draft.engine, draft.rounds),
    )

/**
 * Whether the station list comes back around to its first station.
 *
 * §3 edge case 3 states it as `rounds > 1`, and for the engines that carry a round count that is
 * exactly what this is. Two engines have no count to test and repeat anyway:
 *
 * - **`AMRAP`** — `rounds` is null because the answer is 時間内で, *as many as fit*. It is the most
 *   repeated shape the app has, and reading its null as one round would silence the warning on
 *   precisely the engine where the wrap pair is performed most often. シンディ runs its three stations
 *   fifteen-odd times in twenty minutes.
 * - **`EMOM_ASCENDING`** — デス・バイ runs until you fail, so it too has no count and certainly repeats.
 *
 * Everything else with a null count is a draft mid-edit whose 巡数 has not been set, and one round is
 * the honest floor: warning about a repeat before the user has said the routine repeats would be the
 * app inventing the premise of its own advice.
 *
 * The fatigue argument is `04-library-records.md` §3's own — "the fatigue argument applies
 * identically" — and it does not distinguish between a round count of five and a time cap that fits
 * five rounds. **This function is a derivation, not a transcription**, and it is the only one in the
 * file: it decides which routines wrap, never what is said about them.
 */
fun routineWraps(engine: Engine, rounds: Int?): Boolean = when (engine) {
    Engine.AMRAP, Engine.EMOM_ASCENDING -> true
    Engine.INTERVAL_CIRCUIT,
    Engine.FOR_TIME,
    Engine.FOR_TIME_WITH_REST,
    Engine.EMOM,
    Engine.FIXED_SETS,
    -> (rounds ?: 1) > 1
}

/**
 * Design §6's sentence, verbatim: `腕立て伏せ と ディップス は続けて置かない方がよい`.
 *
 * Names rather than a [PatternClash], because the clash carries indices and the copy needs the two
 * exercises' Japanese names — which the caller has already resolved to draw the rows. The pattern's
 * own label (押す) is deliberately **not** in the sentence: §6 names the movements, and a line reading
 * 「押す が続いています」 would be true, general, and about nothing the user can move.
 */
fun clashCopy(first: String, second: String): String = "$first と $second は続けて置かない方がよい"
