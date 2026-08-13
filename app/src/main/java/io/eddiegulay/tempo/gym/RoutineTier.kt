package io.eddiegulay.tempo.gym

/*
 * 入門 / 中級 / 上級 for a routine nobody graded.
 *
 * The three built-ins carry a `tier` because somebody decided it; a routine the user wrote carries
 * null, and the library index still has a chip to fill and a filter to answer. `04-library-records.md`
 * §3 gives the arithmetic in one line and calls it a heuristic, which is the important word: this file
 * *guesses*, it does not measure, and every decision below is about keeping the guess cheap to
 * override and honest about being one.
 */

/** `< 0.9 → 入門` — the boundary belongs to the band above, as `<` says. */
private const val INTERMEDIATE_AT = 0.9

/** `< 1.4 → 中級`, else 上級. */
private const val ADVANCED_AT = 1.4

/** The score doubles at twenty minutes: `score = meanDifficulty × (1 + estimateMinutes / 20)`. */
private const val MINUTES_PER_DOUBLING = 20.0

/**
 * The tier a routine reads as, or **null when there is nothing to go on**.
 *
 * `04-library-records.md` §3 verbatim: `score = meanDifficulty × (1 + estimateMinutes / 20f)`,
 * `< 0.9 → 入門`, `< 1.4 → 中級`, else `上級`, and *a stored tier always wins*. That last clause is why
 * [stored] is the first parameter rather than something the caller remembers to check: a built-in's
 * seeded 入門 is a judgement somebody made about the routine, and this formula is an average of
 * coefficients. The formula loses, every time, and the only way to guarantee that is to make it
 * impossible to call the guess without offering the fact.
 *
 * **Duration is half the judgement, not a tiebreak.** Twenty minutes of the same movements doubles the
 * score. That is the whole reason it is a product rather than a sum of two ranks: シンディ's three
 * bodyweight movements are individually easy and twenty minutes of them is not a 入門 routine, and no
 * threshold on difficulty alone can say so.
 *
 * **A null is not 入門.** A routine whose stations the catalogue can no longer resolve, or one with no
 * stations at all mid-edit, has no mean to take — and 入門 would be a claim about a routine the app
 * cannot read. `Tier?` is nullable through the whole model for exactly this (`GymModels.kt`), and the
 * detail subtitle renders 巡回 alone rather than 巡回 ・ 入門.
 *
 * *Rejected* — counting an unresolved station as difficulty 0.0 so the mean is always defined. It
 * drags a hard routine into 入門 because of a *missing* row, which turns the library index's "a
 * routine with an unknown station still lists" into a routine that lists a lie about itself.
 *
 * *Rejected* — a fourth band, or interpolating the chip's colour by score. Three tiers are the whole
 * of §6's vocabulary and a fourth would need a Japanese word that does not exist in any spec table.
 *
 * @param stored `routine.tier`, non-null on the built-ins and on copies of them.
 * @param difficulties the resolved `Exercise.difficulty` of each station the catalogue knows. Passed
 *   rather than looked up: the catalogue is synchronous (`00-plan.md` §2 row 6) and this could reach
 *   for it, but then the function would need it loaded to be testable, and the index resolves station
 *   rows once for search anyway.
 * @param estimatedDurationSeconds the **stored** `est_duration_sec`, never a per-frame recomputation
 *   (§3, edge case 9). Zero — デス・バイ, which runs until failure — leaves the multiplier at 1 and
 *   scores the movements alone rather than declining to answer; the tier chip is least in doubt on
 *   exactly those routines.
 */
fun derivedTier(stored: Tier?, difficulties: List<Double>, estimatedDurationSeconds: Int): Tier? {
    if (stored != null) return stored
    if (difficulties.isEmpty()) return null
    val mean = difficulties.sum() / difficulties.size
    val minutes = if (estimatedDurationSeconds > 0) estimatedDurationSeconds / 60.0 else 0.0
    val score = mean * (1.0 + minutes / MINUTES_PER_DOUBLING)
    return when {
        score < INTERMEDIATE_AT -> Tier.BEGINNER
        score < ADVANCED_AT -> Tier.INTERMEDIATE
        else -> Tier.ADVANCED
    }
}

/**
 * The detail page's form: a snapshot already carries its stations *resolved* and its estimate stored,
 * so neither the catalogue nor the estimator is needed here.
 *
 * [stored] stays a separate parameter because a [RoutineSnapshot] is a `routine_version` and the tier
 * lives on the `routine` row above it — versions are immutable and a tier is not versioned.
 */
fun derivedTier(snapshot: RoutineSnapshot, stored: Tier?): Tier? = derivedTier(
    stored = stored,
    difficulties = snapshot.stations.mapNotNull { it.exercise?.difficulty },
    estimatedDurationSeconds = snapshot.estimatedDurationSeconds,
)
