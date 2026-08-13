package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.data.JapaneseDate

/*
 * Finding a routine in a library you wrote yourself: search, filter, rank, and name.
 *
 * The whole file is one problem wearing four hats. Japanese has three scripts for the same word,
 * two widths for one of them, and a keyboard that will hand you whichever one the user's IME was in
 * when they started typing — so `スクワット` has to be reachable from すくわっと, ｽｸﾜｯﾄ, and すく, and
 * `matchRoutine` cannot be `name.contains(query)`. Everything else here is ordinary list work; the
 * folding is the part that will be subtly wrong if it is wrong, which is why it has its own test class
 * (`04-library-records.md` §7).
 */

// ─── Kana folding ───────────────────────────────────────────────────────────────────────────────

/**
 * Half-width katakana `｡` (U+FF61) through `ﾝ` (U+FF9D), in codepoint order, and their full-width
 * equivalents at the same indices. The two voicing marks that follow them (U+FF9E, U+FF9F) are
 * combining and are handled in the loop, not here.
 */
private const val HALF_WIDTH_KANA =
    "｡｢｣､･ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ"
private const val FULL_WIDTH_KANA =
    "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン"

/** Bases that take a dakuten, and what they become. Katakana and hiragana share the +1 pattern. */
private const val DAKUTEN_BASE = "カキクケコサシスセソタチツテトハヒフヘホウかきくけこさしすせそたちつてとはひふへほう"
private const val DAKUTEN_VOICED = "ガギグゲゴザジズゼゾダヂヅデドバビブベボヴがぎぐげござじずぜぞだぢづでどばびぶべぼゔ"
private const val HANDAKUTEN_BASE = "ハヒフヘホはひふへほ"
private const val HANDAKUTEN_VOICED = "パピプペポぱぴぷぺぽ"

private const val HALF_WIDTH_DAKUTEN = 'ﾞ'
private const val HALF_WIDTH_HANDAKUTEN = 'ﾟ'
private const val COMBINING_DAKUTEN = '゙'
private const val COMBINING_HANDAKUTEN = '゚'
private const val PROLONGED_SOUND_MARK = 'ー'
private const val IDEOGRAPHIC_SPACE = '　'

/**
 * The one normal form every search on this page compares in: trimmed, half-width kana widened,
 * katakana lowered to hiragana, prolonged sound marks dropped, full-width latin narrowed, lowercased.
 *
 * It exists because a Japanese keyboard will hand you any of four spellings of the same word and the
 * user does not experience them as different words. `04-library-records.md` §3's edge case 1 is the
 * requirement: スクワット must be found by typing すくわっと *and* by すく.
 *
 * What it does, and the two things it deliberately does **not**:
 *
 * 1. **Half-width katakana are composed, not stripped.** `ｶﾞ` is two codepoints and `ガ` is one; a
 *    naive width conversion produces `カ` followed by a stray voicing mark, and then ｶﾞｯﾂ folds to
 *    かつつ while ガッツ folds to がっつ — the same word, two normal forms, one of which silently never
 *    matches. Combining U+3099/U+309A are composed the same way, because a string that has been
 *    through NFD (a paste from another app, a backup restore) carries those instead.
 * 2. **Voicing is preserved.** が does not fold to か. It is tempting — it would let a fumbled IME
 *    still find things — and it is wrong: ハンド and バンド are different words, and a search that
 *    conflates them makes the *result list* the thing the user has to disambiguate.
 * 3. **The prolonged sound mark is dropped**, so バーピー and ばぴ and ﾊﾞｰﾋﾟｰ all reach ばぴ. *Rejected*
 *    — expanding ー to the preceding vowel (ばあぴい), which would additionally match a user who types
 *    the long vowel out. It needs a 46-row reading table, it has to special-case ー after ん and after
 *    a small kana, and every row of that table is an opportunity to be wrong about a word nobody on
 *    this project will notice. Dropping is symmetric and needs no table.
 * 4. **Small kana are not folded to large.** ャ stays ャ. Folding them would make きょう and きよう the
 *    same string, which are different words; the small-kana slip is a much rarer typo than the ones
 *    above and is not worth that.
 *
 * Latin is narrowed and lowercased in the same pass because `origin` is Latin (CrossFit, 2004-12-29)
 * and §3's edge case 2 requires ASCII search to be case-insensitive.
 */
fun foldKana(raw: String): String {
    val widened = StringBuilder(raw.length)
    for (ch in raw.trim()) {
        when {
            // An orphan — nothing before it, or something unvoiceable — is kept, in its combining
            // form whichever form it arrived in. Dropping it would make a query of one stray mark
            // fold to the empty string, which matches every routine in the library; keeping the
            // half-width one *as* half-width would give the same orphan two normal forms.
            ch == HALF_WIDTH_DAKUTEN || ch == COMBINING_DAKUTEN ->
                if (!compose(widened, DAKUTEN_BASE, DAKUTEN_VOICED)) widened.append(COMBINING_DAKUTEN)

            ch == HALF_WIDTH_HANDAKUTEN || ch == COMBINING_HANDAKUTEN ->
                if (!compose(widened, HANDAKUTEN_BASE, HANDAKUTEN_VOICED)) widened.append(COMBINING_HANDAKUTEN)

            else -> {
                val half = HALF_WIDTH_KANA.indexOf(ch)
                widened.append(
                    when {
                        half >= 0 -> FULL_WIDTH_KANA[half]
                        // Full-width latin and digits sit exactly 0xFEE0 above their ASCII twins.
                        ch in '！'..'～' -> ch - 0xFEE0
                        ch == IDEOGRAPHIC_SPACE -> ' '
                        else -> ch
                    },
                )
            }
        }
    }

    val folded = StringBuilder(widened.length)
    for (ch in widened) {
        when {
            ch == PROLONGED_SOUND_MARK -> Unit
            // Katakana ァ(U+30A1)..ヶ(U+30F6) sit exactly 0x60 above their hiragana twins — small
            // kana and ヴ included — as do the iteration marks ヽヾ. The gap between them, U+30F7..
            // U+30FA (ヷヸヹヺ), has no hiragana form at all and falls through unchanged rather than
            // becoming four unassigned codepoints.
            ch in 'ァ'..'ヶ' || ch in 'ヽ'..'ヾ' -> folded.append(ch - 0x60)
            else -> folded.append(ch)
        }
    }
    return folded.toString().lowercase()
}

/** Voices the last character written, if it can be voiced. False means the mark was orphaned. */
private fun compose(out: StringBuilder, bases: String, voiced: String): Boolean {
    if (out.isEmpty()) return false
    val at = bases.indexOf(out[out.length - 1])
    if (at < 0) return false
    out[out.length - 1] = voiced[at]
    return true
}

// ─── Matching ───────────────────────────────────────────────────────────────────────────────────

/**
 * Whether a routine answers a query, over its name, its station names and its `origin`.
 *
 * All three, because `04-library-records.md` §3's edge case 2 wants 懸垂 to surface シンディ — a
 * routine whose name contains no kanji at all. The station names have to be passed in: the library
 * index renders [RoutineSummary] projections that deliberately do not inflate their station lists
 * (one query, no N+1), so the caller resolves them through `ExerciseCatalog.byId` and hands them over.
 *
 * A blank query matches everything, so the filter row can open without emptying the list.
 *
 * **Romaji is not supported**, and the spec's instruction about that is worth repeating here: there is
 * no romaji index, so say nothing about it and do not pretend. `sukuwatto` will not find スクワット.
 */
fun matchRoutine(
    name: String,
    origin: String?,
    stationNames: List<String>,
    query: String,
): Boolean {
    val needle = foldKana(query)
    if (needle.isEmpty()) return true
    if (foldKana(name).contains(needle)) return true
    if (origin != null && foldKana(origin).contains(needle)) return true
    return stationNames.any { foldKana(it).contains(needle) }
}

/** The [RoutineSummary] form, so a call site does not unpack three fields in order. */
fun matchRoutine(summary: RoutineSummary, stationNames: List<String>, query: String): Boolean =
    matchRoutine(summary.name, summary.origin, stationNames, query)

/**
 * Whether an exercise answers a query, over its Japanese and English names.
 *
 * English is searched because the catalogue carries it and because the loanword movements are the ones
 * a user is most likely to reach for in latin — a `push` typed into the station picker finding
 * 腕立て伏せ costs nothing and surprises nobody. The form cue is **not** searched: it is a sentence
 * ("肘は体の近くに"), and a search that matches prose returns half the catalogue for a common particle.
 */
fun matchExercise(exercise: Exercise, query: String): Boolean {
    val needle = foldKana(query)
    if (needle.isEmpty()) return true
    return foldKana(exercise.nameJa).contains(needle) || foldKana(exercise.nameEn).contains(needle)
}

// ─── Filtering ──────────────────────────────────────────────────────────────────────────────────

/**
 * The 〜五分 / 五〜十五分 / 十五分〜 chips — single-select, per `04-library-records.md` §6.
 *
 * The labels share their endpoints and a routine must land in exactly one bucket, so **an endpoint
 * belongs to the lower bucket**: exactly five minutes is 〜五分, exactly fifteen is 五〜十五分. The
 * alternative reads worse — a five-minute routine excluded from the chip that says 五分 is the kind of
 * thing a user finds by accident and never trusts again.
 */
enum class DurationBucket(val label: String, val upperBoundSeconds: Int) {
    UNDER_FIVE("〜五分", 5 * 60),
    FIVE_TO_FIFTEEN("五〜十五分", 15 * 60),
    OVER_FIFTEEN("十五分〜", Int.MAX_VALUE),
}

/** Which chip a routine's stored estimate falls under. Never null: every duration is somewhere. */
fun durationBucket(estimatedDurationSeconds: Int): DurationBucket =
    DurationBucket.entries.first { estimatedDurationSeconds <= it.upperBoundSeconds }

/**
 * The library index's filter state, which lives in `GymViewModel` and is **never persisted**
 * (`04-library-records.md` §3). A filter that survives an app restart is a filter the user will one
 * day blame the library for.
 *
 * Tier and engine are sets (multi-select chip rows); duration is a single chip. An empty set means no
 * constraint rather than no matches — the difference between "I have not chosen" and "I chose
 * nothing", which is the same distinction the whole `Loadable` doctrine is about.
 */
data class RoutineFilter(
    val query: String = "",
    val tiers: Set<Tier> = emptySet(),
    val engines: Set<Engine> = emptySet(),
    val duration: DurationBucket? = null,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && tiers.isEmpty() && engines.isEmpty() && duration == null
}

/**
 * Search and filters applied together, in list order.
 *
 * Order is preserved rather than re-sorted: the index's sections are already ordered by
 * `sort_order`, and a filter that also reorders makes the user re-find things they had located.
 *
 * Archived routines are dropped here as well as in the query. `GymRepository.routines` already
 * excludes them (§1 rule 2), so this is belt and braces — but the one call site that will ever pass an
 * archived row is the one that got it from a session, and a deleted routine appearing in a filter
 * result is exactly the bug §1 was written to prevent.
 *
 * @param stationNames resolved per routine by the caller; see [matchRoutine]. Defaulted to empty so a
 *   surface that only filters (no search box) does not have to fabricate one.
 */
fun applyFilters(
    routines: List<RoutineSummary>,
    filter: RoutineFilter,
    stationNames: (RoutineSummary) -> List<String> = { emptyList() },
): List<RoutineSummary> = routines.filter { routine ->
    !routine.archived &&
        (filter.tiers.isEmpty() || routine.tier in filter.tiers) &&
        (filter.engines.isEmpty() || routine.engine in filter.engines) &&
        (filter.duration == null || durationBucket(routine.estimatedDurationSeconds) == filter.duration) &&
        matchRoutine(routine, stationNames(routine), filter.query)
}

// ─── よく使う ────────────────────────────────────────────────────────────────────────────────────

/** よく使う shows at most four cards (`04-library-records.md` §3, edge case 5). */
const val FREQUENT_LIMIT = 4

/**
 * Below two cards the section is **omitted entirely** — a one-item "frequently used" is noise
 * (§3, `Ready` state). The rule lives with the caller because it is about whether to draw a heading,
 * but the number lives here so both halves cannot disagree.
 */
const val FREQUENT_MINIMUM = 2

/**
 * よく使う, ranked: recent use first, ties by lifetime count, then by name.
 *
 * `04-library-records.md` §3 edge case 5 gives the whole rule in one sentence — "`recentUsage(60)`
 * desc, ties by `usageCounts()`, then name; unioned with manual favourites; capped at 4. A manually
 * favourited routine always appears even at zero usage" — and the shape of that sentence is the point:
 * **favourite is a union member and a cap-survival rule, not a sort key.**
 *
 * *Rejected* — sorting favourites to the top, which was the previous implementation. It satisfies
 * "always appears", but it also reorders the section in the ordinary case where nothing is being
 * dropped: one zero-use favourite beside three heavily-used routines is `[b, c, d, fav]` by the spec's
 * keys and came out `[fav, b, c, d]`. The guarantee only binds when the list is being **truncated**, so
 * that is the only place it is applied — favourites claim slots first, the remainder is filled from the
 * rest in the same order, and the survivors are re-emitted in **ranked** order. The user's override
 * decides *who stays*; it does not restate who is used most.
 *
 * The final tie-break is the name, not the id. Ids are stable and would produce an order the user
 * cannot see the logic of; a name is at least an order, and this is the third tie-break of three, so
 * it settles cases where two routines were used the same number of times and never otherwise.
 *
 * Sorting is stable in Kotlin, so equal rows keep their incoming `sort_order`.
 */
fun rankFrequent(
    routines: List<RoutineSummary>,
    recentUsage: Map<String, Int>,
    usageCounts: Map<String, Int>,
    limit: Int = FREQUENT_LIMIT,
): List<RoutineSummary> {
    val ranked = routines
        .filter { !it.archived && (it.favourite || (recentUsage[it.routineId] ?: 0) > 0) }
        .sortedWith(
            compareByDescending<RoutineSummary> { recentUsage[it.routineId] ?: 0 }
                .thenByDescending { usageCounts[it.routineId] ?: 0 }
                .thenBy { it.name },
        )
    if (ranked.size <= limit) return ranked
    // Ids rather than rows: two routines with the same name and usage are equal to `contains` on a
    // data class, and dropping the wrong one of them is a bug nobody would reproduce twice.
    val kept = (ranked.filter { it.favourite } + ranked.filterNot { it.favourite })
        .take(limit)
        .mapTo(mutableSetOf()) { it.routineId }
    return ranked.filter { it.routineId in kept }
}

// ─── Naming a copy ──────────────────────────────────────────────────────────────────────────────

/**
 * The name a 写して作る gets: `七分間 の写し`, then `七分間 の写し二`, `七分間 の写し三`.
 *
 * The suffix and its collision sequence are `04-library-records.md` §1 and §3 edge case 7 verbatim,
 * spacing included — §1 writes the pre-filled name as 「七分間 の写し」 with the space, which is this
 * copy's habit everywhere (まだ やっていません, この記録のときとは 中身が変わっています) and not a typo.
 *
 * The counter starts at 二, not 一: the first copy is 「の写し」, so 「の写し一」 would be an off-by-one
 * the user reads as "copy one" of a copy they cannot find.
 *
 * **Duplicate names are legal** (§3 edge case 4 — two routines called 朝の五分 are the user's business
 * and the builder only warns). This function is not enforcing uniqueness on the library; it is
 * choosing a *default* that does not arrive pre-collided.
 */
fun uniqueName(base: String, existing: Set<String>): String {
    val first = "$base の写し"
    if (first !in existing) return first
    // Bounded by the number of names that could collide, so a caller cannot spin here.
    for (n in 2..existing.size + 2) {
        val candidate = first + JapaneseDate.kanjiExtended(n)
        if (candidate !in existing) return candidate
    }
    return first
}
