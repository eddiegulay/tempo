package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.Strings
import java.time.YearMonth

/*
 * `GYM.RECORDS.HISTORY`'s list, as arithmetic over a composite cursor.
 *
 * **Keyset, never `OFFSET`** (`04-library-records.md` §4, edge case 1). The distinction is not a
 * performance preference and it is not academic on this page: 長押し → 記録を削除 is one of the two
 * actions the page offers, so a row *will* disappear between two page fetches. `LIMIT 30 OFFSET 30`
 * asked after a delete returns rows 30..59 of a list that just became one shorter — the row that had
 * been at index 30 has moved to 29 and is **never fetched**. Delete two and the user loses two rows
 * they can only recover by leaving the screen. The reverse, an insert, silently repeats one. A cursor
 * addressed by a row's own key cannot do either: it says "everything strictly older than this
 * session", and a row leaving the list changes nothing about what that means.
 *
 * **Both columns of the cursor, always** (§4, edge case 2). `started_at` alone is not unique — a
 * restored backup can carry two sessions stamped the same millisecond — and a single-column
 * `WHERE started_at < ?` drops whichever of the pair the page boundary fell between, permanently.
 * `(started_at, id)` is total, so the ordering has no ties left to lose.
 *
 * Everything here is a plain function over plain lists, which is what lets the delete-mid-scroll case
 * be *tested* rather than argued about. `HistoryPagingTest` runs a real page-delete-page sequence
 * against a fake store and asserts no row is lost and none repeats.
 */

// ─── Grouping ───────────────────────────────────────────────────────────────────────────────────

/**
 * One month's worth of the list: a plain header item, then its rows.
 *
 * **Not a `stickyHeader`** (`00-plan.md` §4.1 rule 3). `CalendarScreen.kt:148-158` sets the precedent
 * with plain items and `stickyHeader` is still experimental foundation API; this type exists so the
 * page can lay `item(key = …)` + `items(sessions, key = …)` exactly as the calendar does.
 */
data class HistoryMonth(
    val month: YearMonth,
    /** 六月 — the month alone, because the rows beneath it already say 十七日 (`sessionRowLines`). */
    val header: String,
    /** 十二回 — how many of *this month's loaded rows* are on screen. See [groupByMonth]. */
    val countLabel: String,
    val sessions: List<SessionSummary>,
) {
    /** §4's own key format, so a recomposition cannot reuse one month's header for another. */
    val key: String get() = "month:$month"
}

/**
 * The loaded rows, grouped into month sections — **derived from the whole list on every emission**.
 *
 * §4 edge case 3 is why: *deleting the last session in a month must remove that month's header too*.
 * A grouping patched incrementally as pages arrive has to be told about the delete as well, and the
 * one it is never told about is the one that leaves an empty 五月 heading above 四月's rows. Regrouping
 * a few hundred rows costs nothing next to being wrong once.
 *
 * Grouping is by [SessionSummary.localDate] and **not** by `started_at`. They differ for a session
 * begun at 23:40 on the 31st, and §4 edge case 6 states the rule once for the whole feature: a session
 * that crosses midnight belongs to the day it started, in the grid, in the streak and in this
 * grouping. `local_date` is that day, bucketed in Kotlin at write time in the device's own zone
 * (`00-plan.md` §2 row 14), so using it here is what keeps the three surfaces agreeing.
 *
 * **[HistoryMonth.countLabel] counts loaded rows, not the month's true total**, and it is the only
 * count available — the repository has no per-month tally, and inventing one would be a query per
 * header. A month still being paged in therefore reads low and corrects itself as the user scrolls.
 * That is a visible, self-correcting understatement rather than a wrong claim that persists; the
 * *subtitle*'s totals, which do have to be exact, come from `summary()` instead and never from these
 * pages (§4, edge case 8 — see `historySubtitle`). **Reported as the one number on this page that a
 * repository read would improve.**
 *
 * Input order is preserved as-is. The store returns `(started_at, id) DESC` and [mergePage] keeps it
 * that way; re-sorting here would hide a paging bug rather than surface it.
 *
 * **[HistoryMonth.header] is a month *name*** — 六月, the word — so it goes through `fmt.monthName`
 * and never through `fmt.months(n)`, which is a *count* of months. Japanese spells both 六月, so the
 * wrong one is invisible in the language being migrated away from and renders `6 months` over a
 * month's rows in the one being migrated to. `InkDensity.kt` carries the same note over the same word.
 */
fun groupByMonth(sessions: List<SessionSummary>, strings: Strings): List<HistoryMonth> =
    sessions
        .groupByTo(LinkedHashMap()) { YearMonth.from(it.localDate) }
        .map { (month, rows) ->
            HistoryMonth(
                month = month,
                header = strings.fmt.monthName(month.monthValue),
                countLabel = strings.fmt.times(rows.size),
                sessions = rows,
            )
        }

// ─── The cursor ─────────────────────────────────────────────────────────────────────────────────

/**
 * The cursor the next page is asked for: the **oldest** row currently held.
 *
 * Computed as a minimum over the composite key rather than read off `last()`, and the difference
 * matters exactly once — after [mergePage] has folded an overlapping refetch in, or after a caller
 * has re-sorted for its own reasons. A cursor taken from a list whose order had drifted would ask for
 * "everything older than a row in the middle" and skip the rest, which is the `OFFSET` failure
 * arriving by a different door.
 *
 * Null for an empty list: there is no page-two of nothing. The caller's first fetch passes null too,
 * which is the same statement — start from the newest.
 */
fun nextCursor(loaded: List<SessionSummary>): HistoryCursor? {
    val oldest = loaded.minWithOrNull(compareBy({ it.startedAt }, { it.sessionId })) ?: return null
    return HistoryCursor(startedAt = oldest.startedAt, sessionId = oldest.sessionId)
}

/**
 * Whether a page reached the end of the history — *fewer rows came back than were asked for*.
 *
 * The only honest end-of-list signal a keyset query gives, and the reason §4's `EndOfList` state
 * turns the footer into a 40.dp `Spacer` with **no** "no more items" text: the list simply stops, the
 * way a finished list does.
 *
 * A short page is conclusive; a full page is not. A full final page means one more fetch that returns
 * nothing, which is one wasted query and never a missing row — the safe direction.
 */
fun isLastPage(page: List<SessionSummary>, limit: Int): Boolean = page.size < limit

/**
 * Whether scrolling has come close enough to the end to ask for the next page.
 *
 * Three guards, and each one closes a real failure:
 *
 * - **[endReached]** — past the end there is nothing to ask for, and a list that keeps asking issues
 *   one query per scroll frame forever.
 * - **[inFlight]** — the same page requested twice arrives twice, and [mergePage] would fold the
 *   duplicate away silently, so the bug would show up only as a doubled query count.
 * - **an empty list** — the first page is fetched by the page's own load, not by a scroll.
 *
 * [threshold] is a row count rather than a pixel distance because this function has no business
 * knowing row heights, and rows on this page are not all the same height (an unrated session has no
 * rating line — §4 edge case 8).
 */
fun shouldPrefetch(
    lastVisibleIndex: Int,
    loadedCount: Int,
    endReached: Boolean,
    inFlight: Boolean,
    threshold: Int = PREFETCH_THRESHOLD_ROWS,
): Boolean {
    if (endReached || inFlight || loadedCount <= 0) return false
    return lastVisibleIndex >= loadedCount - threshold
}

/** Far enough ahead that the next page lands before the sentinel is reached, on a 30-row page. */
const val PREFETCH_THRESHOLD_ROWS: Int = 8

/**
 * Folds a freshly fetched page into what is already on screen.
 *
 * Two things happen, in this order, and both are consequences of rows being deletable mid-scroll:
 *
 * 1. **De-duplication by `sessionId`.** A keyset page cannot repeat a row *within* one fetch, but a
 *    page fetched twice — a retry after §4's `Error (next page)`, or a re-emission of the underlying
 *    flow after a delete — can hand back rows already held. Keeping the incoming copy rather than the
 *    held one is deliberate: it is the fresher read, and the field most likely to have changed is
 *    `personalBest`, which is recomputed on read and demotes an old chip when a record is beaten
 *    (§4, edge case 2).
 * 2. **Restoring the total order.** The result is sorted by `(started_at, id)` descending, which is
 *    the order the cursor arithmetic assumes and the order the month grouping reads. Appending alone
 *    would be enough on the happy path; it is not enough after a refetch overlaps, and a list that is
 *    *nearly* sorted is precisely the shape that makes [nextCursor] fetch from the middle.
 *
 * A row deleted from the store simply stops arriving. Nothing here removes it from [existing] —
 * removal is the page's own optimistic delete, applied where the user tapped, so an undelivered page
 * cannot make a row the user is looking at vanish.
 */
fun mergePage(existing: List<SessionSummary>, page: List<SessionSummary>): List<SessionSummary> {
    if (page.isEmpty()) return existing
    val byId = LinkedHashMap<Long, SessionSummary>(existing.size + page.size)
    existing.forEach { byId[it.sessionId] = it }
    page.forEach { byId[it.sessionId] = it }
    return byId.values.sortedWith(
        compareByDescending<SessionSummary> { it.startedAt }.thenByDescending { it.sessionId },
    )
}
