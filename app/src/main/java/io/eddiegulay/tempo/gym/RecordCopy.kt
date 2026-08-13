package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.data.JapaneseDate

/*
 * The words a finished session gets, in the two places it appears: `GYM.SESSION.COMPLETE` (live) and
 * `GYM.RECORDS.SESSION_DETAIL` / `GYM.RECORDS.HISTORY` (historical). One set of functions, because
 * `00-plan.md` §2 row 9 makes those the same component under a `RecordMode`, and two copy tables
 * behind one component is how the historical view starts quietly lying about the live one.
 *
 * **`ensoSweep` is not here.** `04-library-records.md` §7 lists it in this file; it shipped in Phase 0
 * as `io.eddiegulay.tempo.ui.ensoSweep` (P4) and callers import it from there. A second one would be a
 * second clamp and a second NaN rule for the same ring — see `DECISIONS.md` §Q7 for the identical
 * ruling on `kanjiExtended`.
 *
 * Two rules run through every function below.
 *
 * 1. **A partial session is a real session** (`00-plan.md` §4.1 rule 2). It gets a 途中まで chip, no PR
 *    chip, no comparison, and the honest numbers — the same treatment in the history list, the record
 *    screen and the detail page, because a user who sees it read three ways learns that the app is
 *    grading them. Dignified, never punitive.
 * 2. **Every string here is from a spec table.** Where a case has no documented copy the function
 *    returns null and the surface omits the line, rather than inventing a sentence. The one case that
 *    costs us something real is named at [comparisonCopy].
 */

/** Nothing to report, in a slot that must still have a shape. Never `〇`, never blank. */
private const val NO_VALUE = "—"

/**
 * A session shorter than this sets nothing and beats nothing (`03-player.md` §A, COMPLETE edge case 6).
 *
 * Thirty seconds is the spec's number, and the reason it is a *copy* rule rather than only a PR rule
 * is that a twelve-second session which "beats" a real one would print 前回より 五分 速い — arithmetic
 * that is correct and a sentence that is false. Suppression happens here so no caller can forget it.
 */
const val MINIMUM_MEANINGFUL_SESSION_MS = 30_000L

/**
 * 前回より 二十二秒 速い — this session against the one immediately before it.
 *
 * Null means **the line is omitted**, and it is null far more often than the design mock suggests.
 * Four suppressions, each with a reason:
 *
 * - **No previous session.** The caller renders 03's `はじめての記録` for its own `NoComparison` state;
 *   that is a state, not a comparison, so it is not this function's string to return.
 * - **A partial session.** `03-player.md` §A's `Partial` state suppresses the comparison outright: a
 *   session you stopped early is faster than one you finished, and saying so is a taunt.
 * - **Under [MINIMUM_MEANINGFUL_SESSION_MS].**
 * - **Slower, or level, or an engine whose clock is fixed.** — and this one is a gap, not a decision.
 *
 * **The gap, stated so it is not mistaken for a choice:** design §12 documents exactly one comparison
 * sentence, 「前回より 二十二秒 速い」. There is no documented copy for a slower session, an identical
 * one, or an AMRAP/EMOM (where active time is the *cap* and is the same every outing, so a time
 * comparison is meaningless and rounds are what moved). Inventing 遅い, 同じ or 二巡 多い is forbidden
 * (`00-plan.md` §4.1 rule 7's string discipline), so those sessions get no line at all. That is
 * flattery by omission and it is the least-bad option available without new copy — the hero time and
 * the tiles above it still show the honest numbers, so nothing is hidden, only unremarked. **If one
 * string is ever added to this feature, make it the slower case.**
 */
fun comparisonCopy(current: SessionSummary, previous: SessionSummary?): String? {
    if (previous == null) return null
    if (!current.complete) return null
    if (current.activeMs < MINIMUM_MEANINGFUL_SESSION_MS) return null
    // Three engines where a shorter clock is not a better session, so 速い would be false or
    // backwards (`04-library-records.md` §4, edge case 2 names what each is actually scored on):
    // AMRAP and EMOM run to a fixed cap and are scored in rounds, and EMOM_ASCENDING is scored on how
    // long you *survived* — for a デス・バイ, a shorter session is a worse one.
    if (current.engine == Engine.AMRAP ||
        current.engine == Engine.EMOM ||
        current.engine == Engine.EMOM_ASCENDING
    ) {
        return null
    }
    val fasterByMs = previous.activeMs - current.activeMs
    if (fasterByMs <= 0L) return null
    // Truncating, like every other duration: 21.9s faster is 二十一秒, because the second it is short
    // of twenty-two has not elapsed.
    val seconds = (fasterByMs / 1000L).toInt()
    if (seconds <= 0) return null
    return "前回より " + durationKanji(seconds) + " 速い"
}

/**
 * Which 自己最高 chip a session carries, if any.
 *
 * Two chips rather than one boolean, because `04-library-records.md` §4's difference table is explicit
 * that a record since beaten **must not keep claiming the crown**: the live screen says 自己最高 in
 * `c.accent`, and the same session reopened after it has been beaten says 当時の自己最高 in
 * `c.inkFaint`. Demoting on read rather than on write is what makes that free of migrations
 * (§4, edge case 2) — nothing stored ever has to change.
 *
 * The colour stays with the caller. This returns which chip, never how it looks; `LocalTempoColors`
 * is a composition-local and this file has no Android on its classpath.
 *
 * @param isStillBest recomputed on read. The live screen passes `true` — a record set thirty seconds
 *   ago is by definition still standing.
 */
enum class PrChip(val label: String) {
    /** `c.accent` — the record stands. */
    CURRENT("自己最高"),

    /** `c.inkFaint` — it was a record when it was set, and has since been beaten. */
    FORMER("当時の自己最高"),
}

/**
 * The chip a session's header shows, or null for the ordinary case of a session that set nothing.
 *
 * A partial session never gets one, in any engine (`04-library-records.md` §4, edge case 1: a PR
 * requires `complete = 1`). Neither does one under [MINIMUM_MEANINGFUL_SESSION_MS].
 */
fun prChip(summary: SessionSummary, isStillBest: Boolean): PrChip? {
    if (!summary.complete) return null
    if (!summary.personalBest) return null
    if (summary.activeMs < MINIMUM_MEANINGFUL_SESSION_MS) return null
    return if (isStillBest) PrChip.CURRENT else PrChip.FORMER
}

/**
 * 途中まで ・ 二十種目中 八 — the chip a session that ended early carries everywhere it appears.
 *
 * The denominator is the **frozen** `session.stations_planned`, not a count of the rows that exist.
 * Open rep segments and skips can move a derived denominator, and the chip has to say the twenty that
 * was on screen (§A.6). It is a session field for exactly this reason, so nothing here recomputes it.
 *
 * Null for a complete session. A `stations_planned` of zero — an engine with no station plan, or a
 * damaged row — degrades to the bare 途中まで rather than printing 〇種目中 〇, which reads as a score.
 */
fun partialChipCopy(summary: SessionSummary): String? {
    if (summary.complete) return null
    if (summary.stationsPlanned <= 0) return "途中まで"
    return "途中まで ・ " + JapaneseDate.kanjiExtended(summary.stationsPlanned) + "種目中 " +
        JapaneseDate.kanjiExtended(summary.stationsCompleted.coerceAtLeast(0))
}

/**
 * One line of the 内訳, in the four shapes design §4 draws plus とばした.
 *
 * The parts are returned separately rather than as one string because the row is a `Row` of columns
 * that align down the list, and because a skipped row is `c.inkFaint` *as a whole* — a caller handed
 * one string would have to re-parse it to know that.
 */
data class BreakdownRow(
    /** 腕立て伏せ, or 不明な種目 when the catalogue no longer knows the id the session recorded. */
    val name: String,
    /** `0:41` — arabic, so a column of them can be scanned. [NO_VALUE] on a skipped station. */
    val duration: String,
    /** 済 or とばした. */
    val status: String,
    /** 二十回, or 十八回 / 二十回 when actual and prescribed disagree, or null for a hold. */
    val reps: String?,
    /** The whole row draws `c.inkFaint`. */
    val skipped: Boolean,
)

/**
 * One `session_result` as a breakdown row, or **null for a row the 内訳 does not list**.
 *
 * Rests, 支度 and 完了 return null: design §4's breakdown is a list of what you *did*, and interleaving
 * 休息 0:10 between every station would double the list's length to say nothing. The durations are
 * still in the hero — `active_ms` includes rest — so nothing is being hidden.
 *
 * The four shapes, from `04-library-records.md` §4 edge case 5:
 * - a hold shows 済 with no rep count;
 * - a rep station where actual == prescribed shows one number;
 * - where they differ shows both, 十八回 / 二十回;
 * - a skipped station shows `—` and とばした.
 *
 * A fifth shape the spec does not enumerate: a rep station whose `actual_reps` was never recorded (the
 * player closed it on the estimate rather than on 済, or a MAX_EFFORT nobody counted). It prints the
 * prescription alone, because the row still has to say what the station asked for — but note that
 * `movementBests` deliberately refuses to let that number set a record (§4, edge case 5). **A
 * breakdown states what was prescribed; a record states what was counted. They are allowed to differ,
 * and this is the seam.**
 *
 * @param exerciseName resolved from the catalogue by the caller. Null becomes 不明な種目
 *   (`04-library-records.md` §6) rather than a blank, because a nameless row in a list of names looks
 *   like a rendering bug and this is a data fact.
 */
fun breakdownRow(result: SegmentResult, exerciseName: String?): BreakdownRow? {
    if (result.phase != Phase.WORK && result.phase != Phase.REPS) return null
    val name = exerciseName ?: "不明な種目"
    if (result.skipped) {
        return BreakdownRow(name = name, duration = NO_VALUE, status = "とばした", reps = null, skipped = true)
    }
    val actual = result.actualReps
    val prescribed = result.prescribedReps
    val reps = when {
        actual != null && prescribed != null && actual != prescribed ->
            JapaneseDate.kanjiExtended(actual) + "回 / " + JapaneseDate.kanjiExtended(prescribed) + "回"
        actual != null -> JapaneseDate.kanjiExtended(actual) + "回"
        prescribed != null -> JapaneseDate.kanjiExtended(prescribed) + "回"
        else -> null
    }
    return BreakdownRow(
        name = name,
        duration = clockDuration(result.actualMs),
        status = "済",
        reps = reps,
        skipped = false,
    )
}

/**
 * The hero number: 六分十四秒, **always active time**.
 *
 * `active_ms` excludes pauses and `wall_ms` does not, and they are not interchangeable — a session
 * paused for a phone call would otherwise report the phone call as training. If wall-clock is ever
 * shown it needs a different label; do not quietly swap the argument (`04-library-records.md` §4,
 * edge case 6).
 */
fun heroTime(activeMs: Long): String = durationKanjiFromMs(activeMs)

/**
 * The four text slots of a history row, in the geometry `04-library-records.md` §4 draws.
 *
 * ```
 * 七分間                              六分十四秒     ← name / duration
 * 十七日 ・ 三巡 ・ 三百二十回                        ← detail
 * きつい                            自己最高        ← rating / chip
 * ```
 */
data class SessionRowLines(
    val name: String,
    val duration: String,
    val detail: String,
    /** 楽 / ちょうど / きつい, or null — an unrated session simply ends after the duration (§4, edge case 8). */
    val rating: String?,
    /** 自己最高 or 途中まで ・ 三種目中 二. Never both: a partial session cannot hold a record. */
    val chip: String?,
    /** The chip is the partial one, so it draws `c.inkFaint` rather than `c.accent`. */
    val partial: Boolean,
)

/**
 * One finished session as a history card.
 *
 * The detail line says **十七日, not 六月十七日**, because the list is grouped under a plain 六月 header
 * (§4's mock) and repeating the month on every row of a month is noise. The screen-reader string is
 * the opposite — `"七分間、六月十七日、…"` — because a TalkBack user arriving at a row mid-list has no
 * header in earshot. Two different jobs; the caller owns the semantics string.
 *
 * Zero rounds or zero reps **omit their fragment** rather than printing 〇巡 or 〇回. A pure-time
 * circuit has no rep count, and zero there is an inapplicability, not a result (`03-player.md` §A,
 * COMPLETE edge case 4). A row can therefore legitimately carry a detail line of just the day.
 */
fun sessionRowLines(summary: SessionSummary): SessionRowLines {
    val parts = buildList {
        add(JapaneseDate.kanji(summary.localDate.dayOfMonth) + "日")
        if (summary.roundsCompleted > 0) add(JapaneseDate.kanjiExtended(summary.roundsCompleted) + "巡")
        if (summary.totalReps > 0) add(JapaneseDate.kanjiExtended(summary.totalReps) + "回")
    }
    val partial = partialChipCopy(summary)
    return SessionRowLines(
        name = summary.routineName,
        duration = heroTime(summary.activeMs),
        detail = parts.joinToString(" ・ "),
        rating = summary.rating?.label,
        // The list has no `isStillBest` — it would be a per-row query — so a row shows the current
        // 自己最高 only. 当時の自己最高 is the detail page's distinction, where the best has been read.
        chip = partial ?: prChip(summary, isStillBest = true)?.label,
        partial = partial != null,
    )
}

/**
 * The `GYM.RECORDS.HISTORY` subtitle: 八十六回 ・ 二千四百分, or 「七分間」十四回 when filtered.
 *
 * The totals come from `summary()` and **not** from the loaded pages, which are partial by
 * construction — a keyset-paged list that has loaded thirty of eighty-six rows would otherwise print
 * 三十回 and correct itself as the user scrolls (§4, edge case 8).
 *
 * The filtered form drops the minutes deliberately: `04-library-records.md` §4 writes it as
 * 「七分間」十四回 and nothing else, and the number that matters when you have opened one routine's
 * history from that routine's page is how many times you have done it.
 *
 * Minutes truncate. 二千四百分 is the whole minutes trained, not the nearest.
 */
fun historySubtitle(sessions: Int, totalActiveMs: Long, routineName: String? = null): String {
    if (routineName != null) {
        return "「" + routineName + "」" + JapaneseDate.kanjiExtended(sessions) + "回"
    }
    val minutes = (if (totalActiveMs <= 0L) 0L else totalActiveMs / 60_000L).toInt()
    return JapaneseDate.kanjiExtended(sessions) + "回 ・ " + JapaneseDate.kanjiExtended(minutes) + "分"
}
