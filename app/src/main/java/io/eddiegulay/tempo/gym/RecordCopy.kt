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

// ─── The streak block (`GYM.RECORDS.INDEX`) ─────────────────────────────────────────────────────

/**
 * The three lines under the month grid, and the **one node** that reads them.
 *
 * §4's accessibility note is why they are one type rather than three functions: *the streak +
 * forgiveness + monotony lines are one node — three announcements for one thought is worse than
 * one.* Splitting them would leave the join to a call site, and a call site that joins three
 * optional strings gets the separators wrong on the day one of them is absent.
 */
data class StreakCopy(
    /** 四日 連続, or 連続は とぎれています. Never 〇日 連続 — zero is not a small streak. */
    val line: String,
    /** The line is the broken one, so it draws `c.inkFaint` rather than `c.inkSoft`. */
    val broken: Boolean,
    /** ゆるし 一回 使いました, or null. Present only when the allowance was actually spent. */
    val forgiveness: String?,
    /** 同じ調子が続いています, or null. Gated on both a threshold and a history length. */
    val monotony: String?,
    /** All of the above, joined — the single spoken string. */
    val semantics: String,
)

/**
 * Foster monotony above which a week reads as monotonous. §4: `monotony7d > 2.0`.
 *
 * Strictly greater, matching the spec's own operator: 2.0 exactly is the boundary of the range, and a
 * nudge that fires *at* the threshold fires for a user the threshold was drawn to exclude.
 */
const val MONOTONY_NUDGE_ABOVE: Double = 2.0

/**
 * Days of history below which the monotony nudge stays silent. §4: `historyDays >= 14`.
 *
 * Design §7.4's stance on premature metrics, applied: a monotony figure over four days is arithmetic
 * about a sample, not a fact about training, and 同じ調子が続いています said to somebody in their first
 * week is the app inventing a problem to have an opinion about.
 */
const val MONOTONY_MIN_HISTORY_DAYS: Int = 14

/**
 * 四日 連続 ・ ゆるし 一回 使いました ・ 同じ調子が続いています — as much of that as is true.
 *
 * **The forgiveness line never states how many days remain**, and `04-library-records.md` §6's table
 * says so beside the string itself. It is the difference between an allowance and a budget: a number
 * you can see going down is a number you will spend, and the whole point of the forgiveness rule is
 * that the user should not be managing it at all. [Streak] carries `forgivenThisMonth` and
 * deliberately carries no remainder, so this function could not print one if it wanted to — the type
 * is where the rule is enforced and this is where it is honoured.
 *
 * **Zero days is 連続は とぎれています, never 〇日 連続** (§4's `StreakZero` state, and [Streak]'s own
 * KDoc). 〇日 連続 is a scolding dressed as a statistic; とぎれています is a fact with no verdict in it,
 * and it is a sentence you can read on a rest day without flinching.
 *
 * @param monotony7d from `TrainingLoad`. Null is the ordinary case — it is null whenever any day in
 *   the window holds an unrated session, or when the SD is zero, and both mean *no answer* rather
 *   than *a low number*. Only a value above [MONOTONY_NUDGE_ABOVE] with at least
 *   [MONOTONY_MIN_HISTORY_DAYS] of history says anything.
 */
fun streakCopy(streak: Streak, monotony7d: Double? = null, historyDays: Int = 0): StreakCopy {
    val broken = streak.days <= 0
    val line = if (broken) {
        "連続は とぎれています"
    } else {
        JapaneseDate.kanjiExtended(streak.days) + "日 連続"
    }
    val forgiveness = if (streak.forgivenThisMonth > 0) {
        "ゆるし " + JapaneseDate.kanjiExtended(streak.forgivenThisMonth) + "回 使いました"
    } else {
        null
    }
    val monotony = if (
        monotony7d != null &&
        monotony7d > MONOTONY_NUDGE_ABOVE &&
        historyDays >= MONOTONY_MIN_HISTORY_DAYS
    ) {
        "同じ調子が続いています"
    } else {
        null
    }
    return StreakCopy(
        line = line,
        broken = broken,
        forgiveness = forgiveness,
        monotony = monotony,
        semantics = listOfNotNull(line, forgiveness, monotony).joinToString("、"),
    )
}

// ─── `GYM.RECORDS.PR` rows ──────────────────────────────────────────────────────────────────────

/**
 * The metric a routine is scored on, decided by its engine.
 *
 * `04-library-records.md` §4 edge case 2's table, exactly: FOR_TIME* is scored on the clock, AMRAP on
 * rounds (with reps as the finer answer), EMOM_ASCENDING on rounds survived, and the fixed-shape
 * engines on weighted volume.
 *
 * **This is the primary metric only** — the single number a routine's record *is*. It is not the set
 * of tiles a detail page shows, which is `bestTilesFor`'s question and is two for an AMRAP (最高巡数
 * beside 最高反復) and one for everything else. Nor is it `metricsFor`, which answers a third
 * question: which records a finished session is allowed to *take*, where `MOST_REPS` rides along on
 * the round-counting engines so an AMRAP's second tile has a row behind it.
 *
 * Three functions over one engine table is one more than anybody wants, and the risk is that they
 * drift into disagreeing — a routine scored one way, its tile labelled another. They are not merged
 * because they genuinely answer different questions, and `EMOM` proves it: it scores on volume, shows
 * one tile, and is allowed to take a `MOST_REPS` record it never displays. What holds them together
 * instead is a test — `RecordCopyTest` asserts for **every** engine that [bestMetricFor] names the
 * metric `bestTilesFor` puts first, so a divergence fails the build rather than shipping.
 *
 * *Rejected* — reading `routine_version.primary_metric` instead. That column is the stored answer and
 * is the right one for a routine that exists; this function is what the PR page uses to *label* a
 * best whose row it is holding, and `DECISIONS.md` §Q9 is the standing proof that a seed can carry a
 * metric the engine does not score (リーコン・ロン shipped as `HIGHEST_STEP` and rendered an empty tile
 * block forever). The engine is the authority on what "best" means; the column is what was written.
 */
fun bestMetricFor(engine: Engine): BestMetric = when (engine) {
    Engine.FOR_TIME, Engine.FOR_TIME_WITH_REST -> BestMetric.BEST_TIME
    Engine.AMRAP, Engine.EMOM_ASCENDING -> BestMetric.MOST_ROUNDS
    Engine.INTERVAL_CIRCUIT, Engine.EMOM, Engine.FIXED_SETS -> BestMetric.MOST_VOLUME
}

/**
 * One 型ごと row of `GYM.RECORDS.PR`, in the four slots §4's mock draws.
 *
 * ```
 * シンディ                              十七巡      ← name / value
 * 最高巡数 ・ 時間内                                 ← meta
 * 六月十七日                            六回        ← date / count, both tappable
 * ```
 */
data class BestRowCopy(
    val name: String,
    /** 十七巡 / 六分十四秒 / 四百二十 — in the unit its metric is measured in. */
    val value: String,
    /** 最高巡数 ・ 時間内 — what was measured, and the engine that decided so. */
    val meta: String,
    /** 六月十七日 — the full month-day, because a PR list spans months. */
    val date: String,
    /** 六回 — how many times the routine has been done. */
    val count: String,
    /** 中身が変わっています, or null. `c.inkFaint`. */
    val note: String?,
    /** The routine was deleted: the row shows a 削除済み chip and stays tappable. */
    val archived: Boolean,
    /** The whole row as one node, with the two tappable fragments exposed as `customActions`. */
    val semantics: String,
)

/**
 * A routine's record as a row, or **null for a record that cannot be labelled**.
 *
 * Null is the `HIGHEST_STEP` case and nothing else. `DECISIONS.md` §Q9 forbids inventing a Japanese
 * label for it — §6's tile list has five entries and none is a step — so a stored row carrying it is
 * *omitted* rather than printed with a blank heading or an invented one. Nothing seeds it in v1, so
 * this is a defence against a restored backup rather than a live path, and where the step you have
 * reached belongs is `stepFor`, as 第九段 / 十八段のうち.
 *
 * The label and the value both come from `EngineRows`' [bestMetricLabel] and [bestValueLabel] rather
 * than being restated here — that file's own KDoc asks for exactly that, and the four words 最速 /
 * 最高巡数 / 最高反復 / 最高負荷 appearing in two tables is how a tile and a row start disagreeing about
 * the same record.
 *
 * **The metric comes off the record, not off the engine.** [bestMetricFor] says what an engine scores
 * on; this row is printing a `personal_record` that already knows which metric it is, and a routine
 * whose engine was changed after the record was set still holds the record it set. That is the same
 * restraint as [structureChanged][RoutineBest.structureChanged]: state the fact, decline the
 * judgement.
 *
 * **A scaled tier needs no decoration.** §4 edge case 3 says scaled tiers are separate rows "labelled
 * シンディ ・ やさしい", and they already are — they are separate stored routines and the seed names
 * one 「シンディ（やさしい）」. Appending a tier here would render 「シンディ（やさしい） ・ やさしい」.
 *
 * The date is 六月十七日 and not the history row's bare 十七日: this list is not grouped by month, so
 * the month is not repeated noise here — it is the only thing saying *when*.
 */
fun bestValueCopy(best: RoutineBest): BestRowCopy? {
    val metricLabel = bestMetricLabel(best.metric) ?: return null
    val value = bestValueLabel(best.metric, best.value)
    val meta = metricLabel + " ・ " + best.engine.label
    val date = JapaneseDate.monthDay(best.localDate.atStartOfDay())
    val count = JapaneseDate.kanjiExtended(best.timesDone) + "回"
    val note = if (best.structureChanged) "中身が変わっています" else null
    return BestRowCopy(
        name = best.routineName,
        value = value,
        meta = meta,
        date = date,
        count = count,
        note = note,
        archived = best.routineArchived,
        semantics = listOfNotNull(
            best.routineName,
            metricLabel + " " + value,
            best.engine.label,
            date,
            count,
            note,
            // Announced last, and announced at all: a row that is still tappable while its routine is
            // gone has to say so, or the 型を見る it offers lands somewhere the user cannot explain.
            if (best.routineArchived) "削除済み" else null,
        ).joinToString("、"),
    )
}
