package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.GymWrite
import io.eddiegulay.tempo.gym.HistoryMonth
import io.eddiegulay.tempo.gym.LifetimeSummary
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.groupByMonth
import io.eddiegulay.tempo.gym.historySubtitle
import io.eddiegulay.tempo.gym.isLastPage
import io.eddiegulay.tempo.gym.mergePage
import io.eddiegulay.tempo.gym.nextCursor
import io.eddiegulay.tempo.gym.sessionRowLines
import io.eddiegulay.tempo.gym.shouldPrefetch
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * GYM.RECORDS.HISTORY — これまで. `04-library-records.md` §4's second page.
 *
 * Everything above the composables is pure and JUnit-tested (`RecordsHistoryScreenTest`), for
 * `00-plan.md` §4.1 rule 10's reason: a decision made inside a composable can only be checked by
 * launching an emulator. The *paging arithmetic* is not here at all — `groupByMonth`, `nextCursor`,
 * `isLastPage`, `shouldPrefetch` and `mergePage` shipped in `gym/HistoryPaging.kt` and are called, never
 * re-derived. What is left for this file is the page's own three questions: which of the four list
 * states is showing, what a row says out loud, and what 記録を削除 asks before it destroys anything.
 *
 * **Three list states that must never be confused** (`00-plan.md` §4.1 rule 1). A store that could not
 * be read renders `FaultPanel` and 記録を読めません; a history with nothing in it renders
 * 記録はありません; a *filtered* history with nothing in it renders この型は まだ やっていません, which
 * is a claim about one routine and not about the store. None of the four shares a composable, and
 * [historyPageState] is the one place that chooses between them — with `rows > 0` first, so a paging
 * failure can never take down what is already on screen (§4's `Error (next page)`).
 *
 * **The paging *flags* are pure too, and that is a repair.** `HistoryLoad` and its transitions
 * (`historyHeadFailed`, `historyRetry`, …) exist because the nine `var`s they replaced wedged the page:
 * a failed window left `inFlight` up forever, `shouldPrefetch` therefore refused every further page,
 * and もう一度 re-keyed an effect that the filtered branch does not run. None of that was visible to a
 * JUnit test until the transitions had names.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure logic — Android-free, JUnit-tested in `gym/ui/RecordsHistoryScreenTest.kt`
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * `history(cursor, limit = 30)`'s page, named so the prefetch threshold has something to be relative to.
 *
 * Thirty is `GymRepository.history`'s own default and `HistoryPaging`'s stated assumption
 * (`PREFETCH_THRESHOLD_ROWS` is documented as "far enough ahead … on a 30-row page"). Restating it here
 * rather than reading the default keeps the *filtered* window — which grows in multiples of it — tied to
 * the same number.
 */
const val HISTORY_PAGE_SIZE: Int = 30

/**
 * Which of §4's four list states the page is in.
 *
 * Ordering is the specification, not an implementation detail:
 *
 * 1. **Rows win.** §4's `Error (next page)` is explicit — *a paging failure must never destroy what is
 *    already on screen* — so a fault arriving after the first page is a footer, never a panel.
 * 2. **A fault beats loading.** A read that failed is not a read that is still going.
 * 3. **Loading beats empty**, which is the whole `Loadable` doctrine: an unread store must never
 *    render an ありません sentence.
 */
enum class HistoryPageState { Loading, Failed, Empty, Ready }

fun historyPageState(headLoaded: Boolean, headFault: TempoFault?, rows: Int): HistoryPageState = when {
    rows > 0 -> HistoryPageState.Ready
    headFault != null -> HistoryPageState.Failed
    !headLoaded -> HistoryPageState.Loading
    else -> HistoryPageState.Empty
}

/**
 * 記録はありません, or この型は まだ やっていません.
 *
 * Both are §4's own words for this page and they are **not** interchangeable. The filtered sentence is a
 * statement about one routine and is safe to make from a `RoutineDetail` the user just came from; the
 * unfiltered one is a statement about the user's whole history and is only ever reached through
 * [historyPageState]'s `Empty`, which a fault and a pending read both outrank.
 *
 * Note that neither is §6's records-index string まだ 記録はありません — that sentence belongs to
 * `GYM.RECORDS.INDEX`, arrives with a 型をえらぶ action beside it, and §4 gives this page its own
 * shorter form. Two pages, two rows of the table, no borrowing.
 */
fun historyEmptyCopy(routineId: String?): String =
    if (routineId == null) "記録はありません" else "この型は まだ やっていません"

/**
 * The footer under the last row: nothing, 読み込み中, もう一度, or 40.dp of air.
 *
 * §4's states, in the order they outrank each other. A page that failed shows its retry even while the
 * list below has more to give, because the user's next scroll would otherwise silently re-issue the
 * request that just failed.
 */
enum class HistoryFooter { None, Loading, Retry, End }

fun historyFooter(endReached: Boolean, inFlight: Boolean, pageFault: TempoFault?): HistoryFooter = when {
    pageFault != null -> HistoryFooter.Retry
    inFlight -> HistoryFooter.Loading
    endReached -> HistoryFooter.End
    else -> HistoryFooter.None
}

/**
 * One entry of the flat list: a plain month header, or a session.
 *
 * **Flat rather than nested `item` + `items` calls**, and the keys are §4's own (`"month:$ym"` and
 * `SessionSummary.key`) so the structure is unchanged — what the flattening buys is that
 * [anchorHeaderIndex] and [rowsThrough] are arithmetic over a list rather than assertions about a
 * `LazyListScope` nobody can call from a JVM test.
 *
 * **Never a `stickyHeader`** (`00-plan.md` §4.1 rule 3). `CalendarScreen.kt:148-158` sets the precedent
 * with plain items and the sticky variant is still experimental foundation API.
 */
sealed interface HistoryItem {

    val key: String

    data class Header(val month: HistoryMonth) : HistoryItem {
        override val key: String get() = month.key
    }

    data class Row(val summary: SessionSummary) : HistoryItem {
        override val key: String get() = summary.key
    }
}

/** The month sections, flattened into the order the list draws them. */
fun historyItems(months: List<HistoryMonth>): List<HistoryItem> = buildList {
    months.forEach { month ->
        add(HistoryItem.Header(month))
        month.sessions.forEach { add(HistoryItem.Row(it)) }
    }
}

/**
 * How many **session rows** lie at or before [flatIndex].
 *
 * `shouldPrefetch` counts rows, deliberately — it "has no business knowing row heights", and month
 * headers are neither rows nor a fixed fraction of them. Handing it a flat index would make a history
 * of twelve one-session months prefetch at twice the intended depth, and one of two thirty-session
 * months at half of it.
 */
fun rowsThrough(items: List<HistoryItem>, flatIndex: Int): Int =
    items.take((flatIndex + 1).coerceIn(0, items.size)).count { it is HistoryItem.Row }

/**
 * Where [anchor]'s header sits in the flat list, or null while it has not been paged in yet.
 *
 * §4 edge case 7: `anchorMonth` from the ink grid is *"honoured by paging until that month is loaded,
 * then scrolling to its header — not by a jump-to-offset query"*. This is the "then" half; the paging
 * half is the caller's loop, which stops on `endReached` so a tap on an empty month cannot page the
 * whole history looking for a header that will never exist.
 */
fun anchorHeaderIndex(items: List<HistoryItem>, anchor: YearMonth?): Int? {
    if (anchor == null) return null
    val index = items.indexOfFirst { it is HistoryItem.Header && it.month.month == anchor }
    return index.takeIf { it >= 0 }
}

/**
 * `八十六回 ・ 二千四百分`, or `「七分間」十四回` — **or nothing at all.**
 *
 * §4 edge case 8 is unambiguous: the totals come from `summary()` and **not** from the loaded pages,
 * which are partial by construction. The filtered form is available — `GymRepository.countForRoutine`
 * is an exact, live `Loadable<Int>` for precisely this routine, and §4 edge case 4 makes the
 * denormalised `routine_name` on the row the sanctioned source for the name.
 *
 * **The unfiltered form is now sourced too.** `GymRepository.summary()` — the lifetime `COUNT(*)` +
 * `SUM(active_ms)` that `DECISIONS.md` §Q22 added for this line and for `GYM.RECORDS.INDEX`'s これまで
 * tile — is the read edge case 8 names, and it is reached through `GymViewModel.lifetimeSummary`, whose
 * subscription *is* the request. Counting the loaded pages instead would print 三十回 to a user with
 * eighty-six sessions and correct itself as they scrolled, which is the one thing edge case 8 was
 * written to forbid.
 *
 * **[routineId], not merely [routineName], decides which form is asked for.** The name is read off the
 * first loaded row, so it is null on a filtered page whose first window has not landed yet — branching
 * on it would print the user's *lifetime* totals as the subtitle of one routine's history for as long
 * as that read took, which is a wrong number rather than a missing one.
 *
 * Null for an unread count or an unread lifetime, for the `Loadable` doctrine's reason: 「七分間」〇回 or
 * 〇回 ・ 〇分 over a store that has not answered is an ありません-as-emptiness claim with a numeral on it.
 */
fun historySubtitleOrNull(
    routineId: String?,
    routineName: String?,
    sessionCount: Int?,
    lifetime: LifetimeSummary?,
): String? {
    if (routineId != null) {
        if (routineName == null || sessionCount == null) return null
        // `totalActiveMs` is unread by the filtered branch of `historySubtitle` — §4 writes the filtered
        // subtitle as 「七分間」十四回 and nothing else — so passing zero states no minutes rather than
        // claiming zero of them.
        return historySubtitle(sessions = sessionCount, totalActiveMs = 0L, routineName = routineName)
    }
    if (lifetime == null) return null
    return historySubtitle(sessions = lifetime.sessions, totalActiveMs = lifetime.totalActiveMs)
}

/**
 * Every field the two fetch loops write, as **one value**, so the transitions between them are pure
 * functions a JUnit test can run.
 *
 * They were nine separate `var`s in the composable, and that is exactly how this page came to wedge:
 * the head flow's failure branch set `pageFault` and forgot `inFlight`, `inFlight` was cleared in only
 * one of the four places that set it, and nothing outside a running emulator could see either fact.
 * Each transition below clears the flag it is finished with, once, in the one place that decides it.
 *
 * [retryToken] is part of the state rather than a counter beside it because it is what もう一度
 * *changes* — see [historyHeadKey].
 */
data class HistoryLoad(
    val loaded: List<SessionSummary> = emptyList(),
    val headLoaded: Boolean = false,
    val headFault: TempoFault? = null,
    val pageFault: TempoFault? = null,
    val endReached: Boolean = false,
    val inFlight: Boolean = false,
    val pagesWanted: Int = 1,
    val retryToken: Int = 0,
)

/**
 * The head subscription's identity: change it and the flow is collected again.
 *
 * `TableChangeNotifier.observing` emits on subscription (`onStart { emit("") }`), so **re-collecting is
 * re-querying** — which makes this the one re-fetch path the routine-filtered branch has, since that
 * branch walks no cursor. [HistoryLoad.retryToken] is therefore part of the key: without it もう一度
 * changed nothing the head effect was keyed on, and a filtered list whose first window failed could
 * never ask for anything again.
 *
 * The window is the whole filtered list (`30 × pagesWanted`, the growing prefix) and a single page when
 * unfiltered, where the cursor pages walk the rest beneath it.
 */
data class HistoryHeadKey(val routineId: String?, val window: Int, val retryToken: Int)

fun historyHeadKey(routineId: String?, pagesWanted: Int, retryToken: Int): HistoryHeadKey =
    HistoryHeadKey(
        routineId = routineId,
        window = if (routineId == null) HISTORY_PAGE_SIZE else HISTORY_PAGE_SIZE * pagesWanted,
        retryToken = retryToken,
    )

/**
 * A head read that failed.
 *
 * **`inFlight` is cleared here, and that is half the fix for the wedge.** A request that failed is a
 * request that is over; leaving the flag up made `shouldPrefetch` answer false forever — it counts an
 * in-flight request as a reason not to start another — so no further page could be asked for on any
 * branch, and the footer sat on 読み込み中 under a もう一度 the user had already tapped.
 *
 * Rows outrank the fault: §4's `Error (next page)` — a re-read that failed while rows are on screen is
 * a footer, never a panel.
 */
fun historyHeadFailed(load: HistoryLoad, fault: TempoFault): HistoryLoad =
    if (load.loaded.isEmpty()) {
        load.copy(inFlight = false, headFault = fault)
    } else {
        load.copy(inFlight = false, pageFault = fault)
    }

/**
 * A head read that landed.
 *
 * A short head is only conclusive while the head *is* the list: once the cursor has walked past it the
 * end is whatever the deepest page said, so `endReached` is carried rather than being un-set by a
 * re-emission of the newest thirty rows.
 */
fun historyHeadReady(
    load: HistoryLoad,
    page: List<SessionSummary>,
    filtered: Boolean,
    window: Int,
): HistoryLoad = load.copy(
    loaded = mergePage(load.loaded, page),
    headLoaded = true,
    headFault = null,
    pageFault = null,
    inFlight = false,
    endReached = when {
        filtered -> isLastPage(page, window)
        load.pagesWanted == 1 -> isLastPage(page, HISTORY_PAGE_SIZE)
        else -> load.endReached
    },
)

/** A cursor page that landed. */
fun historyPageReady(load: HistoryLoad, page: List<SessionSummary>): HistoryLoad = load.copy(
    loaded = mergePage(load.loaded, page),
    inFlight = false,
    endReached = load.endReached || isLastPage(page, HISTORY_PAGE_SIZE),
)

/** A cursor page that failed. `inFlight` down, for [historyHeadFailed]'s reason. */
fun historyPageFailed(load: HistoryLoad, fault: TempoFault): HistoryLoad =
    load.copy(inFlight = false, pageFault = fault)

/**
 * Another page is wanted — the prefetch threshold, or the anchor month's paging loop.
 *
 * `inFlight` goes up **here** rather than inside the fetch, because the fetch is a `LaunchedEffect`
 * keyed on `pagesWanted` and there is a frame between the bump and the effect running in which the
 * threshold would otherwise fire a second time.
 */
fun historyPageWanted(load: HistoryLoad): HistoryLoad =
    load.copy(inFlight = true, pagesWanted = load.pagesWanted + 1, pageFault = null)

/**
 * もう一度, in the footer.
 *
 * Three things, and the page had only the first: the fault is dismissed, the in-flight flag is lowered
 * so the list can want a page again, and [HistoryLoad.retryToken] is bumped — which re-keys
 * [historyHeadKey] and therefore re-collects, and so re-runs, the head query. Bumping a token rather
 * than decrementing `pagesWanted` is deliberate: decrementing would re-fire the cursor effect
 * immediately and turn one failed page into a retry loop.
 */
fun historyRetry(load: HistoryLoad): HistoryLoad =
    load.copy(pageFault = null, inFlight = false, retryToken = load.retryToken + 1)

/**
 * `七分間、六月十七日、六分十四秒、三巡、三百二十回、きつい、自己最高` — one row, one node.
 *
 * §4's accessibility line verbatim, and it is deliberately **not** `sessionRowLines`' visible text. The
 * drawn detail line says 十七日 because the row sits under a 六月 header the eye can see; a TalkBack
 * user arriving at a row mid-list has no header in earshot, so the spoken form carries the full
 * 六月十七日. Two jobs, two strings, and this is the one place the difference is decided.
 *
 * The rounds and reps fragments are re-derived from the summary rather than split back out of
 * `sessionRowLines.detail`, because that line has already dropped the month and re-parsing a rendered
 * string to recover its parts is how the two forms start disagreeing.
 */
fun historyRowSemantics(summary: SessionSummary): String {
    val lines = sessionRowLines(summary)
    return listOfNotNull(
        summary.routineName,
        JapaneseDate.monthDay(summary.localDate.atStartOfDay()),
        lines.duration,
        JapaneseDate.kanjiExtended(summary.roundsCompleted).takeIf { summary.roundsCompleted > 0 }
            ?.plus("巡"),
        JapaneseDate.kanjiExtended(summary.totalReps).takeIf { summary.totalReps > 0 }?.plus("回"),
        lines.rating,
        lines.chip,
    ).joinToString("、")
}

/** `六月、十二回` — the month header as one node (§4: "Month header one node"). */
fun historyMonthSemantics(month: HistoryMonth): String = month.header + "、" + month.countLabel

/**
 * The two items behind a long press, as values rather than as composables.
 *
 * They are a list because they are needed **twice** — once as `DropdownMenuItem`s and once as
 * `customActions` — and `00-plan.md` §4.1 rule 4, the most-repeated accessibility requirement in the
 * plan, is exactly the requirement that the two never disagree. One list is the source of both.
 *
 * **Both items are always offered.** §4 edge case 4: a session whose routine was archived still shows
 * its denormalised `routine_name` and *"この型を見る still works and lands on the archived detail"*. A
 * purged routine cannot be the other case, because `purgeRoutine` is offered only at zero sessions.
 */
enum class HistoryMenuItem(val label: String) {
    Delete("記録を削除"),
    OpenRoutine("この型を見る"),
}

val HISTORY_MENU_ITEMS: List<HistoryMenuItem> = HistoryMenuItem.entries

/**
 * 記録を削除's confirmation, from §6's own row: `この記録を削除しますか` / `元に戻せません。`
 *
 * The two buttons are §6's delete pair — 削除 and やめる — carried over from the routine-delete rows
 * that spell them out. The nouns differ (a 型 there, a 記録 here) and the *verbs* do not: this is the
 * same act on the same kind of dialog, and inventing a second confirm word for it would be a string
 * with no table behind it.
 *
 * There is deliberately no count and no blast radius here. `04` §1 rule 4's "let the user see what is
 * destroyed" is a routine-deletion rule and it is served there by two readings of `countForRoutine`;
 * one session is its own blast radius, and 元に戻せません。 already says the only thing that matters.
 */
data class SessionDeleteCopy(
    val title: String,
    val body: String,
    val confirm: String,
    val dismiss: String,
)

fun sessionDeleteCopy(): SessionDeleteCopy = SessionDeleteCopy(
    title = "この記録を削除しますか",
    body = "元に戻せません。",
    confirm = "削除",
    dismiss = "やめる",
)

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

private const val PAGE_TITLE = "これまで"

/**
 * これまで — every finished session, newest first, month by month.
 *
 * **Keyset paging, never `OFFSET`** (§4 edge cases 1 and 2). The unfiltered list walks the composite
 * `(started_at, id)` cursor through `GymRepository.history`, and `nextCursor` takes the minimum over
 * that composite rather than `last()`, so a merged or re-sorted list can never fetch from the middle.
 *
 * **The filtered list pages by a growing prefix instead, and that is disclosed rather than hidden.**
 * `GymRepository` has no routine-filtered keyset read — `history(cursor, limit)` takes no `routineId`
 * and `attemptsForRoutine(routineId, limit)` takes no cursor — so the routine-filtered page asks for
 * the newest `30 × n` attempts and grows `n`. It shares the total order (`ORDER BY started_at DESC,
 * id DESC`) with the keyset query, so it has the property keyset exists to guarantee: a prefix read is
 * re-anchored at the newest row every time, and a session deleted mid-scroll shifts the tail *up* into
 * the next window rather than past it. **No row can be skipped and none can repeat**, which is edge
 * case 1's whole content. What it costs is a re-read of the rows already held, which on a
 * single-routine history is tens of rows, not thousands. `history(cursor, limit, routineId = null)` is
 * the read the data layer owes to make both branches identical; it is reported, not smuggled in here.
 *
 * **The loaded pages and the scroll position live in this composable, not in `GymViewModel`** — §4 asks
 * for the opposite ("Scroll and loaded pages live in `GymViewModel` keyed by the filter, so a round trip
 * to a session detail does not re-page from the top") and this unit does not own that file. Rotation is
 * safe regardless, because `MainActivity` declares `configChanges` for orientation and the composition
 * is never recreated under it — so edge case 6 holds. A round trip into a session's detail and back does
 * re-page from the top, and that is **reported** as the one behaviour this page owes and does not have.
 * [HistoryLoad] is the shape that move wants: one value keyed by the filter, so hoisting it is a
 * `mutableMapOf<String?, HistoryLoad>` on the view model plus the list state's first-visible index, and
 * nothing else on this page has to change.
 *
 * The `routineId` filter is a nav argument and is **not user-clearable here** (§4's back behaviour): a
 * 絞り込みを外す action would silently change which page you are on.
 */
@Composable
fun RecordsHistoryScreen(
    gym: GymViewModel,
    routineId: String? = null,
    anchorMonth: YearMonth? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val repository = gym.repository
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // One value, keyed on the filter: a `History(routineId)` and a `History(null)` are two lists and
    // must not inherit each other's pages, cursor or end-of-list flag. Every write below goes through
    // one of `HistoryLoad`'s transitions, so no branch can set a flag and forget its partner.
    var load by remember(routineId) { mutableStateOf(HistoryLoad()) }
    var deleteFault by remember(routineId) { mutableStateOf<TempoFault?>(null) }
    // The record the failed delete was for, held so もう一度 on the strip can re-attempt *it* rather
    // than re-running reads that were never the problem.
    var failedDelete by remember(routineId) { mutableStateOf<SessionSummary?>(null) }
    var pendingDelete by remember { mutableStateOf<SessionSummary?>(null) }

    val months = remember(load.loaded) { groupByMonth(load.loaded) }
    val items = remember(months) { historyItems(months) }

    /*
     * The head. A **flow**, not a one-shot: it is what makes a finish elsewhere in the shell, or the
     * delete below, re-emit into this list without the page asking. For the filtered branch the window
     * itself grows, so this one subscription is the whole list; for the unfiltered branch it is the
     * newest page only and the cursor effect walks beneath it.
     *
     * Keyed on [historyHeadKey], whose third field is the retry token — re-collecting re-queries, and
     * for the filtered branch that is the only re-fetch there is.
     */
    val headKey = historyHeadKey(routineId, load.pagesWanted, load.retryToken)
    LaunchedEffect(headKey) {
        val flow = if (routineId == null) {
            repository.history(null, HISTORY_PAGE_SIZE)
        } else {
            repository.attemptsForRoutine(routineId, headKey.window)
        }
        flow.collect { outcome ->
            load = when (outcome) {
                Loadable.Loading -> load
                is Loadable.Failed -> historyHeadFailed(load, outcome.fault)
                is Loadable.Ready ->
                    historyHeadReady(load, outcome.value, filtered = routineId != null, window = headKey.window)
            }
        }
    }

    /*
     * The cursor pages, unfiltered only. Keyed on the retry token as well as the page count so もう一度
     * can re-ask for a page whose number has not changed.
     */
    LaunchedEffect(routineId, load.pagesWanted, load.retryToken) {
        if (routineId != null || load.pagesWanted <= 1) return@LaunchedEffect
        load = load.copy(inFlight = true, pageFault = null)
        val cursor = nextCursor(load.loaded)
        load = when (val outcome = repository.history(cursor, HISTORY_PAGE_SIZE).first { it !is Loadable.Loading }) {
            is Loadable.Ready -> historyPageReady(load, outcome.value)
            is Loadable.Failed -> historyPageFailed(load, outcome.fault)
            Loadable.Loading -> load
        }
    }

    // The scroll's own question, asked of `shouldPrefetch` and nothing else. `derivedStateOf` keeps the
    // page off the recomposition-per-scroll-frame path that reading `layoutInfo` directly would put it on.
    val lastVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(lastVisible, load.loaded.size, load.endReached, load.inFlight, load.pageFault) {
        if (load.pageFault != null) return@LaunchedEffect
        val rows = rowsThrough(items, lastVisible)
        if (shouldPrefetch(rows, load.loaded.size, load.endReached, load.inFlight)) {
            load = historyPageWanted(load)
        }
    }

    /*
     * §4 edge case 7: an `anchorMonth` is honoured by **paging until that month is loaded**, then
     * scrolling to its header — never by a jump-to-offset query. It gives up at the end of the list, so
     * a tap on a month the user did not train in settles wherever the list ended rather than paging
     * forever looking for a header that does not exist.
     */
    var anchorSettled by remember(routineId, anchorMonth) { mutableStateOf(anchorMonth == null) }
    LaunchedEffect(items, anchorSettled, load.endReached, load.inFlight, load.pageFault) {
        if (anchorSettled) return@LaunchedEffect
        val index = anchorHeaderIndex(items, anchorMonth)
        when {
            index != null -> {
                listState.scrollToItem(index)
                anchorSettled = true
            }
            load.endReached || load.pageFault != null -> anchorSettled = true
            !load.inFlight -> load = historyPageWanted(load)
        }
    }

    // Exact and live, and a `Loadable` — so an unread or unreadable count omits the subtitle rather
    // than printing 「七分間」〇回 over a routine with a decade behind it (`countForRoutine`'s own KDoc
    // is the argument, on the identical read the delete dialog branches on).
    val sessionCount: Int? = if (routineId == null) {
        null
    } else {
        val counted by gym.countForRoutine(routineId).collectAsStateWithLifecycle()
        counted.valueOrNull()
    }
    // 八十六回 ・ 二千四百分 (§4's mock, §Q22's read). Subscribing *is* the request — the ViewModel's
    // `stateIn` performs it — so this page owns no effect for it and cannot leave it half-asked.
    val lifetime by gym.lifetimeSummary.collectAsStateWithLifecycle()
    // §4 edge case 4: the routine's name comes off the **denormalised** column on the session row,
    // never a join — so a filtered history still names an archived routine.
    val filteredName = if (routineId == null) null else load.loaded.firstOrNull()?.routineName

    /*
     * The delete, in one place because it has **two** callers — the confirmation's 削除 and the failure
     * strip's もう一度 — and a second copy is how the two would come to disagree about what a failure
     * leaves behind.
     *
     * **Never optimistic**, and the precedent is this feature's own:
     * `GymViewModel.confirmDiscardStaleSession` refuses to remove a session from the screen before the
     * store says it is gone, because a record vanishing on a write that then failed is the worst
     * outcome the page has. `HistoryPaging.mergePage`'s KDoc anticipated an optimistic delete; the
     * conservative form is chosen here for the same reason. The head flow re-emits anyway — this
     * removal is what reaches the deeper cursor pages it does not cover.
     *
     * `refreshLifetimeSummary()` because the subtitle above is a lifetime total and `summary()` is a
     * one-shot read, not a flow: without it 八十六回 stays on screen over eighty-five rows.
     */
    val runDelete: suspend (SessionSummary) -> Unit = { target ->
        when (val write = repository.discardSession(target.sessionId)) {
            is GymWrite.Ok -> {
                load = load.copy(loaded = load.loaded.filterNot { it.sessionId == target.sessionId })
                deleteFault = null
                failedDelete = null
                gym.refreshLifetimeSummary()
            }

            is GymWrite.Failed -> {
                deleteFault = write.fault
                failedDelete = target
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        HistoryHeader(
            subtitle = historySubtitleOrNull(routineId, filteredName, sessionCount, lifetime.valueOrNull()),
            onClose = { if (gym.stack.value.size > 1) gym.onBack() },
        )

        // Above the list, never over it — `LibraryIndexScreen`'s placement, for its reason: whatever
        // failed, the records stay readable behind it.
        deleteFault?.let { fault ->
            FaultStrip(
                fault = fault,
                // もう一度 on a *delete* that was rejected re-attempts the delete. `gym.retry()` here
                // re-ran every read and did nothing whatever about the write, so the strip dismissed
                // itself and the record stayed. §Q6 withholds the action word entirely for the faults
                // that must not be re-attempted (`StoreFull`, `Rejected`), so `FaultStrip` draws no
                // もう一度 for them and this lambda is unreachable in those cases — the fallback is a
                // dismissal, not a silent second write.
                onRecover = {
                    val target = failedDelete
                    deleteFault = null
                    failedDelete = null
                    if (target != null) scope.launch { runDelete(target) }
                },
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }

        val fault = load.headFault
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (historyPageState(load.headLoaded, fault, load.loaded.size)) {
                HistoryPageState.Loading -> HistoryLoading()

                // Never 記録はありません for a history we could not read — the distinction this whole
                // feature routes its reads through `Loadable` to preserve (`DECISIONS.md` §Q6). The
                // elvis is unreachable: `historyPageState` returns `Failed` only for a non-null fault.
                HistoryPageState.Failed -> FaultPanel(
                    fault = fault ?: GymFault.Unknown(null),
                    onRecover = {
                        load = historyRetry(load.copy(headFault = null))
                        gym.retry()
                    },
                )

                // Two claims, two composables (`00-plan.md` §4.1 rule 1). 記録はありません is about the
                // store; この型は まだ やっていません is about one routine. The branch is on `routineId`
                // and not on a string handed down, so the two can never be reached through each other.
                HistoryPageState.Empty ->
                    if (routineId == null) HistoryEmptyAll() else HistoryEmptyFiltered(routineId)

                HistoryPageState.Ready -> HistoryList(
                    items = items,
                    listState = listState,
                    footer = historyFooter(load.endReached, load.inFlight, load.pageFault),
                    onOpen = { gym.go(GymRoute.Record(it.key)) },
                    onMenu = { summary, item ->
                        deleteFault = null
                        when (item) {
                            HistoryMenuItem.Delete -> pendingDelete = summary
                            HistoryMenuItem.OpenRoutine -> gym.go(GymRoute.RoutineDetail(summary.routineId))
                        }
                    },
                    // §4's `Error (next page)`: a tappable もう一度 that actually asks again.
                    // [historyRetry] lowers `inFlight` — which is what let the list want a page at all
                    // again — and bumps the token the head subscription is keyed on.
                    onRetryPage = { load = historyRetry(load) },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        val copy = sessionDeleteCopy()
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = c.bgSolid,
            title = {
                Text(copy.title, style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink))
            },
            text = {
                Text(
                    text = copy.body,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.5.sp,
                        color = c.inkFaint,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { runDelete(target) }
                    },
                ) {
                    Text(copy.confirm, style = TextStyle(fontFamily = Mincho, color = c.accent))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(copy.dismiss, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
                }
            },
        )
    }
}

/** これまで, its subtitle when there is one to make, and とじる. The gym page header idiom. */
@Composable
private fun HistoryHeader(subtitle: String?, onClose: () -> Unit) {
    val c = LocalTempoColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f, fill = true)) {
                Text(
                    text = PAGE_TITLE,
                    modifier = Modifier.semantics { heading() },
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            fontFamily = Mincho,
                            fontSize = 13.sp,
                            letterSpacing = 4.sp,
                            color = c.inkFaint,
                        ),
                    )
                }
            }
            HeaderAction(label = "とじる", description = "とじる", color = c.inkFaint, onClick = onClose)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}

/**
 * The list: plain month headers, session cards, and one footer.
 *
 * `items(key = …)` over the flattened [HistoryItem]s, whose keys are §4's own — structurally identical
 * to `CalendarScreen.kt:148-158`, and **never `stickyHeader`** (`00-plan.md` §4.1 rule 3).
 */
@Composable
private fun HistoryList(
    items: List<HistoryItem>,
    listState: LazyListState,
    footer: HistoryFooter,
    onOpen: (SessionSummary) -> Unit,
    onMenu: (SessionSummary, HistoryMenuItem) -> Unit,
    onRetryPage: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
        // The seated tab bar, shallower than the launcher's floating dock (`00-plan.md` §3.2).
        contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is HistoryItem.Header -> MonthHeader(item.month)
                is HistoryItem.Row -> SessionCard(
                    summary = item.summary,
                    onOpen = { onOpen(item.summary) },
                    onMenu = { onMenu(item.summary, it) },
                )
            }
        }
        item(key = "footer") { HistoryFooterRow(footer, onRetryPage) }
    }
}

/** `六月 … 十二回` — one node, and a plain item. */
@Composable
private fun MonthHeader(month: HistoryMonth) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 6.dp)
            .clearAndSetSemantics {
                heading()
                contentDescription = historyMonthSemantics(month)
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = month.header,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        Text(
            text = month.countLabel,
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
        )
    }
}

/**
 * One finished session, in §4's three-line card, with its menu behind a long press.
 *
 * **The card is one accessibility node and the menu is two actions on it** — `00-plan.md` §4.1 rule 4,
 * following `NotificationsScreen.kt:204`'s note: a long press is invisible to TalkBack, so every item
 * the gesture reveals is also a `customAction`. `clearAndSetSemantics` wipes the `combinedClickable`'s
 * own semantics, which is why the click, the long press *and* its label are re-declared inside it.
 *
 * **Swipe-to-dismiss is deliberately not used** (§4 edge case 5), unlike `NotificationsScreen`:
 * dismissing a notification is local and undoable, deleting a training record is neither.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    summary: SessionSummary,
    onOpen: () -> Unit,
    onMenu: (HistoryMenuItem) -> Unit,
) {
    val c = LocalTempoColors.current
    var menuOpen by remember { mutableStateOf(false) }

    val lines = remember(summary) { sessionRowLines(summary) }
    val description = remember(summary) { historyRowSemantics(summary) }
    val actions = remember(onMenu) {
        HISTORY_MENU_ITEMS.map { item -> CustomAccessibilityAction(label = item.label) { onMenu(item); true } }
    }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.card)
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick { onOpen(); true }
                    onLongClick(label = "メニュー") { menuOpen = true; true }
                    customActions = actions
                },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = lines.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true),
                        style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
                    )
                    Text(
                        text = lines.duration,
                        style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
                    )
                }
                if (lines.detail.isNotBlank()) {
                    Text(
                        text = lines.detail,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = Gothic,
                            fontSize = 13.sp,
                            lineHeight = 19.5.sp,
                            color = c.inkSoft,
                        ),
                    )
                }
                if (lines.rating != null || lines.chip != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The rating fragment is simply absent on an unrated session — §4 edge case 8:
                        // the row ends after the duration rather than carrying a placeholder.
                        Box(Modifier.weight(1f, fill = true)) {
                            lines.rating?.let { rating ->
                                Text(
                                    text = rating,
                                    style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
                                )
                            }
                        }
                        lines.chip?.let { chip ->
                            // 途中まで is `c.inkFaint` and 自己最高 is `c.accent`: a session that ended
                            // early is recorded with dignity, not decorated (`00-plan.md` §4.1 rule 2).
                            Text(
                                text = chip,
                                style = TextStyle(
                                    fontFamily = Mincho,
                                    fontSize = 11.sp,
                                    letterSpacing = 3.sp,
                                    color = if (lines.partial) c.inkFaint else c.accent,
                                ),
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            HISTORY_MENU_ITEMS.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = TextStyle(fontFamily = Mincho, color = c.ink)) },
                    onClick = {
                        menuOpen = false
                        onMenu(item)
                    },
                )
            }
        }
    }
}

/**
 * The paging sentinel — 56.dp of 読み込み中, a tappable もう一度, or 40.dp of air.
 *
 * `liveRegion = Polite` on the one node, so 読み込み中 → new rows is announced **once**, not per row
 * (§4's accessibility). It is a live region and not a countdown, which is why `00-plan.md` §4.1 rule 5
 * does not bite: nothing here ticks.
 *
 * `EndOfList` is a `Spacer` and carries **no** "no more items" text (§4's own note) — a finished list
 * simply stops.
 */
@Composable
private fun HistoryFooterRow(footer: HistoryFooter, onRetry: () -> Unit) {
    val c = LocalTempoColors.current
    when (footer) {
        HistoryFooter.None -> Spacer(Modifier.height(8.dp))
        HistoryFooter.End -> Spacer(Modifier.height(40.dp))
        HistoryFooter.Loading -> Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "読み込み中" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "読み込み中",
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 4.sp, color = c.inkFaint),
            )
        }

        HistoryFooter.Retry -> Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .sizeIn(minHeight = 48.dp)
                .semantics { role = Role.Button; contentDescription = "もう一度" }
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onRetry),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "もう一度",
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 2.sp, color = c.accent),
            )
        }
    }
}

/**
 * 読み込み中 — a store that has not answered yet.
 *
 * Its own composable, sharing nothing with [HistoryEmpty]. They look nearly identical and mean two
 * different things, and `00-plan.md` §4.1 rule 1 is the rule a shared composable would quietly break
 * the first time somebody added a branch to it.
 */
@Composable
private fun HistoryLoading() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "読み込み中",
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * 記録はありません — a store that answered, and answered with nothing.
 *
 * **Its own composable, sharing nothing with [HistoryEmptyFiltered].** They draw the same shape today
 * and they make two different claims: this one is about the user's whole history, that one is about a
 * single routine. `00-plan.md` §4.1 rule 1's mechanism is that states which mean different things do
 * not share a composable — a shared one parameterised by a string is one `if` away from saying either
 * sentence in the other's situation, and the sentence is the entire content of the screen.
 *
 * The literal still comes from [historyEmptyCopy], which is where both are tested.
 */
@Composable
private fun HistoryEmptyAll() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = historyEmptyCopy(routineId = null),
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * この型は まだ やっていません — a claim about **one routine**, safe to make from the filter the caller
 * navigated with. See [HistoryEmptyAll] for why this is a second composable and not a parameter.
 */
@Composable
private fun HistoryEmptyFiltered(routineId: String) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = historyEmptyCopy(routineId),
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}
