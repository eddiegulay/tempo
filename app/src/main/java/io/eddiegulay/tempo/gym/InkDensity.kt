package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.gym.data.bucketOf
import io.eddiegulay.tempo.gym.data.loadScaleOf
import io.eddiegulay.tempo.i18n.Strings
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor

/*
 * The month ink-grid of `GYM.RECORDS.INDEX`: where the 42 cells are, which day a tap landed on, how
 * dark a day's dot is, and what the whole thing says out loud.
 *
 * The grid is **one `Canvas`, not 42 Boxes** (§5.1), which buys sub-pixel control of the today-ring
 * and costs the page every piece of layout the composer would have done for it. All of that
 * arithmetic is here: a `DrawScope` needs a device, so geometry computed inside one is geometry that
 * is never tested, and the calendar grid is exactly the surface where an off-by-one is invisible
 * until February.
 *
 * It is also **one TalkBack node**, which is the deliberate answer to "how does a blind user read a
 * heatmap": you give them the list, not a worse heatmap. The node carries [gridSemantics]' spoken
 * summary and its click opens `GYM.RECORDS.HISTORY`, which is a real list with real nodes.
 */

// ─── The grid ───────────────────────────────────────────────────────────────────────────────────

/**
 * A month laid out as cells — everything both the draw loop and the hit test need, computed once.
 *
 * [month] rides along so [dayAt] can return a real date from a tap without the call site passing the
 * month a second time and the two disagreeing after a page swipe.
 */
data class MonthGrid(
    val month: YearMonth,
    /** Empty cells before the 1st. 0..6, **Sunday-first**. */
    val leadingBlank: Int,
    /** Days in the month: 28, 29, 30 or 31. */
    val length: Int,
    /** 4, 5 or 6 — the grid is never a fixed six rows. */
    val rows: Int,
) {
    /** Total cells drawn, blanks included. */
    val cells: Int get() = rows * 7
}

/**
 * Lays a month out, Sunday-first.
 *
 * **Sunday-first, matching `JapaneseDate.DOW`** (`JapaneseDate.kt:18`), which is what the weekday
 * header row above the canvas renders from. A grid that started on Monday while its own header said
 * 日 月 火 would shift every dot one column and nothing would look broken.
 *
 * Note the deliberate divergence from `weekStartOf`, which is ISO **Monday** and stays that way: the
 * weekly charts are compared against each other and against a seven-day monotony window, so their
 * weeks must be ISO. This grid is a calendar page, and a calendar page starts where the reader's
 * calendars start. Two different jobs, stated here so nobody "fixes" one to match the other.
 *
 * **[rows] is 4, 5 or 6 and is computed, never assumed.** `ceil((leadingBlank + length) / 7)`. A
 * fixed six-row grid leaves a blank strip under most months and makes the page jump when the strip
 * is finally used; February 2026 is 28 days beginning on a Sunday and fits in exactly four rows,
 * which is the case a hardcoded five would get wrong. The caller wraps the canvas in
 * `animateContentSize` so a 5→6 row month does not snap (§5.1).
 */
fun monthCells(month: YearMonth): MonthGrid {
    val length = month.lengthOfMonth()
    // java.time: MONDAY=1 .. SUNDAY=7; mod 7 maps SUNDAY→0, the same conversion `JapaneseDate.dowChar`
    // makes for the header letters this grid sits under.
    val leadingBlank = month.atDay(1).dayOfWeek.value % 7
    return MonthGrid(
        month = month,
        leadingBlank = leadingBlank,
        length = length,
        rows = ceil((leadingBlank + length) / 7.0).toInt(),
    )
}

/**
 * Which day a tap at ([x], [y]) landed on, or null for a blank cell or a miss.
 *
 * §5.1 asks for this "even though the whole grid currently navigates to `HISTORY`" — four lines, and
 * it is what makes the grid testable at all. §4 edge case 2 is equally explicit that resolving a date
 * does **not** mean navigating to a filtered single-day view: the tap opens `HISTORY` anchored at the
 * month. One list, one idiom.
 *
 * A day **after today still resolves**. The grid draws nothing there (an unlived day is not an
 * unfilled one, §4 edge case 3) but a tap is still a tap on the 28th, and swallowing it would make
 * the bottom-right corner of the current month mysteriously dead. What today means is the caller's
 * business, and this function is not given it.
 *
 * Guards a zero or non-finite [cellPx]: a canvas measured before layout would otherwise divide by
 * zero and resolve every tap to the same day.
 */
fun dayAt(x: Float, y: Float, cellPx: Float, grid: MonthGrid): LocalDate? {
    if (!cellPx.isFinite() || cellPx <= 0f) return null
    if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f) return null
    val column = floor(x / cellPx).toInt()
    val row = floor(y / cellPx).toInt()
    if (column !in 0..6 || row !in 0 until grid.rows) return null
    val day = row * 7 + column - grid.leadingBlank + 1
    if (day !in 1..grid.length) return null
    return grid.month.atDay(day)
}

// ─── Ink ────────────────────────────────────────────────────────────────────────────────────────

/**
 * How dark a day's dot is: **0 for a rest day, then 1..4** mapped by the caller to `c.ink` at
 * 0.15 / 0.35 / 0.60 / 0.90 (§5.1).
 *
 * Five values, not four, and the difference is the whole grid: level 0 draws *nothing*, so a rest day
 * and the lightest training day are never the same mark. `bucketOf` — which is Phase 1's, tested, and
 * called here rather than restated — returns 0..3 and collapses "no session" into "a session below
 * the 40th percentile", because its job is bucketing a load and not deciding whether a day exists.
 * This function makes the rest day its own level and shifts the rest up, which is exactly §5.1's
 * `when` block: `0 -> Unit`, then four alphas.
 *
 * **`DECISIONS.md` §Q11 — below eight non-zero days every trained day draws the same mid level.**
 * `02-data.md` §D.3 ends "with fewer than 8 non-zero days, fall back to fixed cutoffs and document
 * it" and names no cutoffs; three invented millisecond thresholds are precisely the kind of number
 * `00-plan.md` §2 forbids, so `loadScaleOf` refused them and kept [LoadScale.usedFallback] instead.
 * §Q11 makes that flag a live contract and this is where it is honoured.
 *
 * **This is not a fallback cutoff. It is declining to rank an insufficient sample.** With two
 * training days on file, percentiles say only that one of them was longer than the other — and the
 * grid would then paint the shorter one at 0.15 and the longer at 0.90, which reads as "a light week
 * and a heavy week" about a user who has trained twice. One mid level says the true thing: these are
 * days you trained, and there is not yet enough history to say which were your bigger ones.
 * Percentile ranking resumes at eight non-zero days, where a 40/70/90 split has four days on each
 * side of it and starts meaning something.
 *
 * **Mid is level 2 (alpha 0.35), the lower of the two middles of 1..4.** Rounding down is the
 * direction that cannot overclaim: an unranked grid must not be able to tell the user that a day was
 * among their heaviest, and 0.35 is legible as ink without reading as emphasis.
 */
fun inkLevel(activeMs: Long, scale: LoadScale): Int {
    if (activeMs <= 0L) return INK_LEVEL_NONE
    if (scale.usedFallback) return INK_LEVEL_UNRANKED
    return bucketOf(activeMs, scale) + 1
}

/** A day with no session. Draws nothing at all — not a faint dot, not a placeholder. */
const val INK_LEVEL_NONE: Int = 0

/** The single level every trained day takes while the sample is too thin to rank (§Q11). */
const val INK_LEVEL_UNRANKED: Int = 2

/** The darkest level, so a caller's `when` can be exhaustive without a magic 4. */
const val INK_LEVEL_MAX: Int = 4

/**
 * The trailing-window scale, from the days the repository already read.
 *
 * A shaping adapter and **nothing more** — `loadScaleOf` is the authority on percentiles, on the
 * nearest-rank rule, and on when the sample is too thin (`02-data.md` §D.3), and this exists only so
 * a page holding a `Map<LocalDate, DayLoad>` does not write its own fold to get a `List<Long>` out of
 * it. Two folds over the same days is how one screen ends up ranking against a different scale than
 * the one beside it.
 *
 * Zero-active days are dropped by `loadScaleOf` itself, which is why nothing is filtered here: the
 * percentiles are over *non-zero* days by definition, because a rest day is not a light session.
 */
fun loadScaleFrom(days: Collection<DayLoad>): LoadScale = loadScaleOf(days.map { it.activeMs })

// ─── What the grid says ─────────────────────────────────────────────────────────────────────────

/*
 * **Both functions below turn on a month *name*** — 六月, the word, not a count of months.
 *
 * They go through [Strings.fmt]'s `monthName`, and emphatically **not** through `months(n)`, which is
 * a count. Japanese spells both 六月, so the two are indistinguishable in the language being migrated
 * away from; the wrong one renders `6 months` where a heading should say `June`, which is the precise
 * shape of bug `.planning/i18n/TREE.md` §3 exists to catch. `HistoryPaging.groupByMonth` draws the
 * same word and takes the same route.
 */

/**
 * How many days of a month **carry ink** — the number both captions are built from.
 *
 * The predicate is `activeMs > 0`, and it is the draw loop's, not a second opinion: the grid asks
 * `inkLevel(days[date]?.activeMs ?: 0L, scale)` and `inkLevel` returns [INK_LEVEL_NONE] for a day with
 * no active milliseconds, so a day counted on `sessions > 0` alone would appear in the caption and in
 * [gridSemantics]' spoken summary while the picture showed nothing. `monthLoad`'s query groups every
 * finished session including one whose `SUM(active_ms)` is zero — a session started and finished
 * instantly — so that day is real and reachable. **A caption and the grid it captions must not be able
 * to disagree**, and the summary node is read by exactly the user who cannot check it against the dots.
 *
 * *Rejected* — the other direction, having the draw loop ink any day the map holds. It would put a dot
 * on the grid for a session with no duration, which is the same claim `inkLevel`'s zero rung exists to
 * refuse. One definition either way; this is the one that keeps both.
 */
private fun trainedDays(days: Map<LocalDate, DayLoad>): Int =
    days.count { (_, load) -> load.activeMs > 0L }

/**
 * The caption under the grid: 十二日 ・ 六月, or この月は 〇日 for a month with nothing in it.
 *
 * Both strings are §6's, and the second is load-bearing: §4 says outright that a month with no
 * sessions in a history that *has* sessions **is not the empty state**. The grid still renders, the
 * pager still works, and the page still says 記録はありません to nobody. A month you did not train is
 * a fact about June; an empty store is a fact about the app, and `00-plan.md` §4.1 rule 1 exists so
 * the two can never share a composable.
 */
fun monthCaption(month: YearMonth, days: Map<LocalDate, DayLoad>, strings: Strings): String {
    val trained = trainedDays(days)
    if (trained == 0) return strings.gymRecords.monthUntrained(strings.fmt.days(0))
    return strings.fmt.days(trained) + strings.fmt.separator + strings.fmt.monthName(month.monthValue)
}

/**
 * The grid's one spoken node: 「六月、十二日 鍛錬しました、いちばん多かったのは 六月十七日」.
 *
 * §4's accessibility section verbatim, and it is a *summary* rather than a reading of the dots
 * because forty-two per-day nodes would be unusable and would still not answer the question the
 * heatmap answers at a glance. Per-day access is `HISTORY`'s job — a real list with real nodes.
 *
 * An empty month falls back to [monthCaption]'s この月は 〇日 rather than announcing 〇日 鍛錬しました,
 * which would be a sentence claiming the user trained on zero days rather than one stating there is
 * nothing here. The いちばん多かったのは fragment is dropped with it: there is no busiest day, and
 * naming one anyway is what a `?: firstDayOfMonth` would do.
 *
 * The busiest day is decided on active milliseconds and ties break **toward the earlier date** — the
 * same rule `isBetter` applies to every record in this feature (you set it the first time you hit
 * it), applied here so the sentence does not change under a re-read.
 */
fun gridSemantics(month: YearMonth, days: Map<LocalDate, DayLoad>, strings: Strings): String {
    val monthLabel = strings.fmt.monthName(month.monthValue)
    val listSeparator = strings.fmt.listSeparator
    val trained = trainedDays(days)
    if (trained == 0) return monthLabel + listSeparator + monthCaption(month, days, strings)

    val busiest = days.entries
        .filter { it.value.activeMs > 0L }
        .sortedWith(compareByDescending<Map.Entry<LocalDate, DayLoad>> { it.value.activeMs }.thenBy { it.key })
        .firstOrNull()
        ?.key

    val head = monthLabel + listSeparator + strings.gymRecords.trainedDays(strings.fmt.days(trained))
    // Unreachable now that [trainedDays] counts on the same `activeMs > 0` the filter above uses — a
    // month with a trained day has a busiest one by construction. Kept because the alternative to an
    // omitted fragment is naming an arbitrary day, and the two predicates must never drift again.
    if (busiest == null) return head
    return head + listSeparator + strings.gymRecords.busiestDay(strings.fmt.monthDay(busiest.atStartOfDay()))
}
