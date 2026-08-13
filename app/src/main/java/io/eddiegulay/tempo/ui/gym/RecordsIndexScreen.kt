package io.eddiegulay.tempo.ui.gym

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.ChartKind
import io.eddiegulay.tempo.gym.ChartRange
import io.eddiegulay.tempo.gym.DayLoad
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymTab
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.INK_LEVEL_NONE
import io.eddiegulay.tempo.gym.LifetimeSummary
import io.eddiegulay.tempo.gym.LoadScale
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.Streak
import io.eddiegulay.tempo.gym.WeekPoint
import io.eddiegulay.tempo.gym.barGeometry
import io.eddiegulay.tempo.gym.chartSemantics
import io.eddiegulay.tempo.gym.gridSemantics
import io.eddiegulay.tempo.gym.heroTime
import io.eddiegulay.tempo.gym.inkLevel
import io.eddiegulay.tempo.gym.label
import io.eddiegulay.tempo.gym.monthCaption
import io.eddiegulay.tempo.gym.monthCells
import io.eddiegulay.tempo.gym.partialChipCopy
import io.eddiegulay.tempo.gym.streakCopy
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.faultCopy
import io.eddiegulay.tempo.ui.rememberMinuteTime
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/*
 * GYM.RECORDS.INDEX — 記録. `04-library-records.md` §4's first page, and §5.1's ink grid.
 *
 * **This page renders history, which makes it the page where `00-plan.md` §4.1 rule 1 costs the most.**
 * A store that could not be read must never draw まだ 記録はありません: the sentence is a claim about the
 * user's training, and a user who believes it stops looking for what is still on disk. So every state
 * below is a separate composable and none of them shares a word with another — [RecordsLoading],
 * [RecordsEmpty], [HistoryLossPanel] and `FaultPanel` are four different pictures of four different
 * facts, and [recordsBodyState] is the one place that decides between them, as a value a JVM test can
 * assert on.
 *
 * The arithmetic is *not* here. `InkDensity.kt` lays the month out and decides how dark a day is,
 * `ChartGeometry.kt` positions the sparkline's bars, `RecordCopy.kt` writes the streak block, and
 * `GymMath.kt` is underneath all three. This file owns what is left: which words a tile carries, which
 * reads gate which state, and where the ink goes on the canvas.
 */

// ─── The three tiles ────────────────────────────────────────────────────────────────────────────

/**
 * How a tile is spoken: **label first** (「今月、十二回」), because §4's accessibility line says so and
 * the visual order is the other way round — a number heard before its label is a number the listener
 * has to hold in mind until they find out what it counts.
 *
 * A function rather than a field on the tile, because the tile type is `RecordSummary.kt`'s
 * [RecordTile] and is shared: 記録 and the post-session summary draw the same value-over-label idiom
 * and there is no reason for two types of it. What differs is only the sentence, and that is this.
 */
fun recordsTileSemantics(tile: RecordTile, strings: Strings): String =
    tile.label + strings.fmt.listSeparator + tile.value

/**
 * The tile row: 今月 · 活動時間 · これまで — as many of the three as can be answered.
 *
 * **今月 and 活動時間 are the *current* month, never the month the pager is showing.** Paging the grid
 * back to March must not relabel March's totals 今月, and the word is not this file's to change. Both
 * come from one `monthLoad(currentMonth)` read, so they can never disagree with each other.
 *
 * **これまで comes from `GymRepository.summary()`** — `DECISIONS.md` §Q22, which added the read this
 * page was short of. Three ways of faking it had been rejected and stay rejected: summing
 * `RoutineSummary.timesDone` over `routines` misses every archived routine's sessions, summing
 * `routineBests` misses every routine that has only ever been done partially, and `weeklySeries(52)`
 * counts a year rather than a lifetime. Each is *plausibly* 八十六回 and each is wrong for a user the
 * number matters to.
 *
 * **[lifetimeSessions] is null for a read that has not answered *or* failed, and the tile is then
 * absent.** An absent tile makes no claim; that is what separates it from every other case rule 1
 * governs. 〇回 over a quarantined store would be a claim — the same lie as 記録はありません over an
 * unread one, in miniature — and the two-tile row is a shape this page already draws. *Rejected* — a
 * third tile reading 読み込み中 while the read is in flight: the value slot is a 20.sp numeral a third
 * of the row wide, and a four-glyph word set in it reads as a value rather than as a wait. The read is
 * one `COUNT(*)`, so the tile appears with the rest of the page rather than after it.
 *
 * Minutes truncate, matching `historySubtitle` — 二百四十分 is the whole minutes trained.
 */
fun recordsTiles(
    monthSessions: Int,
    monthActiveMs: Long,
    lifetimeSessions: Int?,
    strings: Strings,
): List<RecordTile> {
    val copy = strings.gymRecords
    val minutes = (if (monthActiveMs <= 0L) 0L else monthActiveMs / 60_000L).toInt()
    return buildList {
        add(RecordTile(strings.fmt.times(monthSessions), copy.tileMonth))
        add(RecordTile(strings.fmt.minutes(minutes), copy.activeTime))
        lifetimeSessions?.let { add(RecordTile(strings.fmt.times(it), copy.tileLifetime)) }
    }
}

// ─── 最近 ───────────────────────────────────────────────────────────────────────────────────────

/**
 * One 最近 row: 六月十七日 ・ 七分間 ・ 六分十四秒 ・ きつい, in §4's own column order.
 *
 * The date leads here and trails on `GYM.RECORDS.HISTORY`, because that list is grouped under a 六月
 * header and this one is three rows under 最近 with no header at all — a bare 十七日 on this page would
 * not say which month. Hence a row copy of its own rather than `sessionRowLines`, which is shaped for
 * the grouped list and would put the month back in the wrong place.
 */
data class RecentRowCopy(
    val date: String,
    val name: String,
    val duration: String,
    /** 楽 / ちょうど / きつい, or null — an unrated row simply ends after the duration (§4 edge case 8). */
    val rating: String?,
    /** 途中まで ・ 三種目中 二, or null. Draws `c.inkFaint`, never beside a 自己最高 chip. */
    val partial: String?,
    val semantics: String,
)

/**
 * A finished session as a 最近 row.
 *
 * **The partial chip rides along even though §4's mock draws four fragments and not five.**
 * `00-plan.md` §4.1 rule 2 is unconditional — *a partial session is a real session, same treatment
 * wherever it appears* — and a row that printed 六月十七日 ・ 七分間 ・ 二分三十秒 with nothing else
 * would present an abandoned circuit as a finished one on the page that summarises the month. The
 * string is `partialChipCopy`'s, not a shorter one written for the width; the name takes the row's
 * flexible width and ellipsises so the chip always fits.
 *
 * There is deliberately **no 自己最高 chip**. `sessionRowLines` can print one because a history row is
 * two lines tall; this row is one, and §4's mock ends it at the rating. A record that stands is one tap
 * away in 最高 and in the session itself.
 */
fun recentRowCopy(summary: SessionSummary, strings: Strings): RecentRowCopy {
    val date = strings.fmt.monthDay(summary.localDate.atStartOfDay())
    val duration = heroTime(summary.activeMs, strings)
    val partial = partialChipCopy(summary, strings)
    val rating = summary.rating?.label(strings)
    return RecentRowCopy(
        date = date,
        // The routine's name is the session row's denormalised copy, frozen at write time and never
        // retranslated (§L10). A session performed in Japanese keeps its Japanese name for ever.
        name = summary.routineName,
        duration = duration,
        rating = rating,
        partial = partial,
        semantics = listOfNotNull(date, summary.routineName, duration, rating, partial)
            .joinToString(strings.fmt.listSeparator),
    )
}

// ─── The month pager ────────────────────────────────────────────────────────────────────────────

/**
 * ‹ 六月 › — the pager's own label, **which grows a year the moment it leaves [current]'s**.
 *
 * The bare 六月 is §4's mock and it is right on entry, where the page subtitle 令和八年 ・ 六月 is two
 * lines above it and says the same year. It stops being right one press past January: paging back
 * thirteen months drew 六月 under a subtitle reading 令和八年 ・ 六月 — two different Junes rendered
 * identically, with another year's ink in the grid between them — because the subtitle is
 * [recordsSubtitle] of the *clock* and the grid is of the *displayed* month. Once the two disagree the
 * year belongs to the thing the user is moving, so the label carries it.
 *
 * **No third formatter.** The long form is [recordsSubtitle]'s own string, asked for with the displayed
 * month's first day rather than re-assembled from `JapaneseDate.era` and `kanji` here — `DECISIONS.md`
 * §Q7's rule, and the reason the pager and the subtitle can never spell a year differently.
 *
 * [current] is required rather than defaulted for the same reason the bug existed: a label that can be
 * asked for without a year to compare against is a label that will be.
 */
fun monthPagerLabel(month: YearMonth, current: YearMonth, strings: Strings): String =
    if (month.year == current.year) {
        // A month **name** — `fmt.monthName`, never `fmt.months`, which counts them. Japanese spells
        // both 六月 and English spells the wrong one `6 months`.
        strings.fmt.monthName(month.monthValue)
    } else {
        recordsSubtitle(month.atDay(1).atStartOfDay(), strings)
    }

/**
 * Whether ‹ may be pressed (§4 edge case 4: disabled at the first session's month).
 *
 * [earliest] null means the earliest month is **unknown** — `populatedMonths()` has not answered, or
 * failed — and the arrow stays *enabled*. Disabling on an unread bound would hide months that exist
 * behind an arrow the user cannot press, which is the same lie as an empty state over an unread store;
 * paging into a month with nothing in it is harmless and self-correcting, because that month says
 * この月は 〇日 and the ‹ that got there still works in reverse.
 */
fun canPageBack(month: YearMonth, earliest: YearMonth?): Boolean =
    earliest == null || month.isAfter(earliest)

/** Whether › may be pressed. Never past the current month: an unlived month is not a page. */
fun canPageForward(month: YearMonth, current: YearMonth): Boolean = month.isBefore(current)

/** 令和八年 ・ 六月 — §4's mock. The month, not the day: this page is about months. */
fun recordsSubtitle(now: LocalDateTime, strings: Strings): String =
    strings.fmt.era(now) + strings.fmt.separator + strings.fmt.monthName(now.monthValue)

// ─── Which of the four bodies is on screen ──────────────────────────────────────────────────────

/**
 * The page body's state — **four cases that never share a composable**, which is the whole of
 * `00-plan.md` §4.1 rule 1 on the page where it matters most.
 */
sealed interface RecordsBody {

    /** No read has answered yet. 読み込み中 for the whole body — **never a skeleton grid** (§4). */
    data object Loading : RecordsBody

    /** A read failed. `FaultPanel`, or [HistoryLossPanel] for the quarantine. */
    data class Failed(val fault: TempoFault) : RecordsBody

    /** Zero sessions ever. まだ 記録はありません + 型をえらぶ — no grid, no zero tiles. */
    data object Empty : RecordsBody

    data object Ready : RecordsBody
}

/**
 * Which body [RecordsIndexScreen] draws, from the reads that gate it.
 *
 * **Failed is checked before Loading**, so a fault is never masked by a sibling read that has not
 * finished: on a quarantined store every history read fails, and the one that answers first is the one
 * with the truth in it.
 *
 * **Emptiness is decided by [recent] and by nothing else.** `history(null, 3)` is unfiltered and
 * ordered newest-first, so `Ready(emptyList())` is exactly "no finished session exists" — and it is an
 * `observeHistory` read, which reports `StoreCorrupt` rather than an honest-looking zero when the file
 * has been quarantined. `populatedMonths()` was **rejected** for this job for precisely that reason: it
 * goes through the plain read path, so a destroyed history returns `Ready(emptyList())` and this page
 * would have drawn まだ 記録はありません over a year of training. It is used for the pager bound only,
 * where being wrong costs an arrow rather than a lie.
 *
 * **[displayedMonth] is checked for faults but not for loading.** Paging to March re-subscribes that
 * one read, and collapsing the whole page to 読み込み中 on every arrow press would make the pager flash
 * the streak and the tiles away. The grid slot carries its own 読み込み中 instead, which is the only
 * part of the page that genuinely does not know its answer yet.
 */
fun recordsBodyState(
    recent: Loadable<List<SessionSummary>>,
    currentMonth: Loadable<Map<LocalDate, DayLoad>>,
    displayedMonth: Loadable<Map<LocalDate, DayLoad>>,
    streak: Loadable<*>,
): RecordsBody {
    listOf(recent, currentMonth, displayedMonth, streak).forEach { read ->
        if (read is Loadable.Failed) return RecordsBody.Failed(read.fault)
    }
    if (recent is Loadable.Loading || currentMonth is Loadable.Loading || streak is Loadable.Loading) {
        return RecordsBody.Loading
    }
    val rows = (recent as? Loadable.Ready)?.value ?: return RecordsBody.Loading
    return if (rows.isEmpty()) RecordsBody.Empty else RecordsBody.Ready
}

/** The sparkline plots sessions per week — §4's 週ごと preview of `GYM.RECORDS.CHARTS`' first chart. */
fun sparklineSessions(weeks: List<WeekPoint>): List<Int> = weeks.map { it.sessions }

// ─── The page ───────────────────────────────────────────────────────────────────────────────────

/** §5.1's geometry: 20.dp cells, 4.dp dots, a 7.dp today ring at 1.dp. */
private val GRID_CELL = 20.dp

/** §4's 週ごと preview height — one number, so the wait and the chart cannot be different sizes. */
private val SPARKLINE_HEIGHT = 56.dp

/** §4's Data in: the ink levels rank a day against the trailing ninety. */
private const val LOAD_SCALE_DAYS = 90

/** §4's Data in: three 最近 rows, and the sparkline's twelve weeks. */
private const val RECENT_ROWS = 3
private const val SPARKLINE_WEEKS = 12

/**
 * 記録 — the month in ink, the streak that survives a rest day, and the three numbers worth knowing.
 *
 * **Month paging resets to the current month on re-entry** (§4's back behaviour), which is why the
 * displayed month is `remember`ed in the composable rather than hoisted into `GymViewModel`: the page
 * leaving the composition *is* the reset, and a records page that opens on March because you looked at
 * March last week is disorienting. It is the one piece of state this page owns.
 *
 * **`today` is the display clock, and the streak's is not.** §4 edge case 5 requires the streak to be
 * walked from the store's monotonic-guarded high-water mark, and it is — `GymStore.streak()` computes
 * it there, from `today()`, and hands down a finished [Streak]. What this page needs a date for is the
 * today-ring and the "an unlived day draws nothing" rule, and `GymRepository` exposes no guarded clock
 * to ask (reported). A wound-back clock can therefore move the ring by a day; it cannot fabricate a
 * streak, a personal best, or a session's `local_date`, which are the three things the guard exists for.
 *
 * The page is a plain scrolling `Column`, not a `LazyColumn`: it draws one grid, three lines, three
 * tiles, one canvas and at most three rows, and every one of them is on screen at once. `00-plan.md`
 * §4.1 rule 3's ban on `stickyHeader` is satisfied by there being no lazy list to sticky anything to —
 * the month-grouped list this page links to is `GYM.RECORDS.HISTORY`'s, and the headers are plain items
 * there.
 */
@Composable
fun RecordsIndexScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val now by rememberMinuteTime()
    val today = now.toLocalDate()
    val currentMonth = YearMonth.from(today)

    var month by remember { mutableStateOf(currentMonth) }
    // A clock that crosses midnight into a new month while the page is open must not strand the pager
    // one month behind its own › bound.
    LaunchedEffect(currentMonth) { if (month.isAfter(currentMonth)) month = currentMonth }

    val recentFlow = remember(gym) { gym.repository.history(null, RECENT_ROWS) }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)

    val streakFlow = remember(gym) { gym.repository.streak() }
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)

    val loadFlow = remember(gym) { gym.repository.trainingLoad() }
    val trainingLoad by loadFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)

    val scaleFlow = remember(gym) { gym.repository.loadScale(LOAD_SCALE_DAYS) }
    val scale by scaleFlow.collectAsStateWithLifecycle(initialValue = EMPTY_SCALE)

    // Two month reads, and they are the same query only while the pager sits on the current month. The
    // tiles are labelled 今月 and must stay the current month's while the grid walks backwards.
    val monthFlow = remember(gym, month) { gym.repository.monthLoad(month) }
    val monthLoad by monthFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
    val currentFlow = remember(gym, currentMonth) { gym.repository.monthLoad(currentMonth) }
    val currentLoad by currentFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)

    val weeklyFlow = remember(gym) { gym.repository.weeklySeries(SPARKLINE_WEEKS) }
    val weekly by weeklyFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)

    // §Q22's lifetime read, and subscribing to it *is* the read — there is no effect to forget and no
    // way for the tile to sit unanswered because a page did not ask. It stays a `Loadable` all the way
    // to [recordsTiles], which is the only thing standing between a quarantined store and a 〇回 tile.
    val lifetime by gym.lifetimeSummary.collectAsStateWithLifecycle()

    // The ‹ bound. One shot: months only ever appear, and a month appearing mid-visit widens the range
    // by one press the user has not yet made.
    var earliest by remember { mutableStateOf<YearMonth?>(null) }
    LaunchedEffect(gym) { earliest = gym.repository.populatedMonths().valueOrNull()?.minOrNull() }

    val body = recordsBodyState(recent, currentLoad, monthLoad, streak)

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = s.gymRecords.pageTitle,
                    modifier = Modifier.semantics { heading() },
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = recordsSubtitle(now, s),
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            // 詳しく is offered only over a page that has records behind it: a charts page reached from
            // an empty or unreadable store is a page of three empty axes.
            if (body is RecordsBody.Ready) {
                HeaderAction(
                    label = s.gymRecords.actionDetail,
                    description = s.gymRecords.actionDetail,
                    color = c.accent,
                    onClick = { gym.go(GymRoute.Charts) },
                )
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (body) {
                RecordsBody.Loading -> RecordsLoading()

                is RecordsBody.Failed ->
                    // §E.6: the quarantine flag stays raised until the user acknowledges it, and
                    // acknowledging is not retrying. Every other fault takes the ordinary panel.
                    if (body.fault == GymFault.StoreCorrupt) {
                        HistoryLossPanel(onDismiss = gym::acknowledgeHistoryLoss)
                    } else {
                        FaultPanel(fault = body.fault, onRecover = gym::retry)
                    }

                RecordsBody.Empty -> RecordsEmpty(onChoose = { gym.selectTab(GymTab.Library) })

                RecordsBody.Ready -> RecordsBodyContent(
                    month = month,
                    currentMonth = currentMonth,
                    today = today,
                    earliest = earliest,
                    monthLoad = monthLoad,
                    currentLoad = currentLoad.valueOrNull().orEmpty(),
                    scale = scale,
                    streak = streak.valueOrNull(),
                    monotony7d = trainingLoad.valueOrNull()?.monotony7d,
                    historyDays = trainingLoad.valueOrNull()?.historyDays ?: 0,
                    lifetime = lifetime,
                    weekly = weekly,
                    recent = recent.valueOrNull().orEmpty(),
                    onMonth = { month = it },
                    onGrid = { gym.go(GymRoute.History(anchorMonth = month)) },
                    onCharts = { gym.go(GymRoute.Charts) },
                    onHistory = { gym.go(GymRoute.History()) },
                    onBests = { gym.go(GymRoute.Bests) },
                    onSession = { gym.go(GymRoute.Record(it.key)) },
                )
            }
        }
    }
}

/**
 * A scale nobody has read yet.
 *
 * `loadScale` is the one read on this page that is not a `Loadable` — `02-data.md` §C.1 gives it as a
 * bare flow and `GymStore` degrades it to `loadScaleOf(emptyList())` — so the initial value has to be
 * something, and this is the same value the store itself falls back to. It reports `usedFallback`, so
 * the grid's first frame draws every trained day at one mid level rather than ranking days against
 * thresholds of zero: `DECISIONS.md` §Q11's rule happens to be exactly right for an unread scale too.
 */
private val EMPTY_SCALE = LoadScale(p40Ms = 0, p70Ms = 0, p90Ms = 0, nonZeroDays = 0, usedFallback = true)

@Composable
private fun RecordsBodyContent(
    month: YearMonth,
    currentMonth: YearMonth,
    today: LocalDate,
    earliest: YearMonth?,
    monthLoad: Loadable<Map<LocalDate, DayLoad>>,
    currentLoad: Map<LocalDate, DayLoad>,
    scale: LoadScale,
    streak: Streak?,
    monotony7d: Double?,
    historyDays: Int,
    lifetime: Loadable<LifetimeSummary>,
    weekly: Loadable<List<WeekPoint>>,
    recent: List<SessionSummary>,
    onMonth: (YearMonth) -> Unit,
    onGrid: () -> Unit,
    onCharts: () -> Unit,
    onHistory: () -> Unit,
    onBests: () -> Unit,
    onSession: (SessionSummary) -> Unit,
) {
    val s = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            // The seated tab bar, shallower than the launcher's floating dock (`00-plan.md` §3.2).
            .padding(bottom = 88.dp),
    ) {
        MonthPager(
            month = month,
            currentMonth = currentMonth,
            canBack = canPageBack(month, earliest),
            canForward = canPageForward(month, currentMonth),
            onMonth = onMonth,
        )

        when (val load = monthLoad) {
            // Not a skeleton grid, and not an empty one: a grid of blank cells is a picture of a month
            // with no sessions, which is the confusion §4 names outright. The pager stays usable.
            Loadable.Loading -> Box(
                Modifier.fillMaxWidth().height(GRID_CELL * 6),
                contentAlignment = Alignment.Center,
            ) { RecordsWord(s.gymRecords.loading) }

            // Unreachable — a failed month is a page-level fault (see `recordsBodyState`) — and left
            // exhaustive rather than as an `else`, so a fifth `Loadable` case would stop the build here
            // instead of silently drawing the grid over a read that failed.
            is Loadable.Failed -> Unit

            is Loadable.Ready -> InkGrid(
                month = month,
                today = today,
                days = load.value,
                scale = scale,
                onTap = onGrid,
            )
        }

        streak?.let {
            Spacer(Modifier.height(14.dp))
            StreakBlock(it, monotony7d, historyDays)
        }

        Spacer(Modifier.height(18.dp))
        TileRow(
            recordsTiles(
                monthSessions = currentLoad.values.sumOf { it.sessions },
                monthActiveMs = currentLoad.values.sumOf { it.activeMs },
                // §Q22's read. `valueOrNull` is the whole of the doctrine here: an unread or unreadable
                // lifetime total is a *missing tile*, never a 〇回 one. See [recordsTiles].
                lifetimeSessions = lifetime.valueOrNull()?.sessions,
                strings = s,
            ),
        )

        when (weekly) {
            // The grid slot one screen up does exactly this, and for the same reason: a block that
            // pops into a scrolling column when a read lands shifts 最近 down under the finger already
            // reaching for it. The 56.dp is the sparkline's own height, so nothing moves.
            Loadable.Loading -> {
                Spacer(Modifier.height(24.dp))
                RecordsSectionRow(
                    label = s.gymRecords.weeklyHeading,
                    action = s.gymRecords.actionDetail,
                    onAction = onCharts,
                )
                SparklineLoading()
            }

            // §4, verbatim: "Chart preview failed — the 週ごと block is omitted. A failed sparkline must
            // not take down the page." Omission is sanctioned **for failure only**, which is why
            // loading no longer shares it.
            is Loadable.Failed -> Unit

            is Loadable.Ready -> if (weekly.value.isEmpty()) {
                // Unreachable on a `Ready` body — the body state needs a finished session and every
                // week is zero-filled — and it falls to the omission rather than to the wait, because
                // there is nothing coming.
                Unit
            } else {
                Spacer(Modifier.height(24.dp))
                RecordsSectionRow(
                    label = s.gymRecords.weeklyHeading,
                    action = s.gymRecords.actionDetail,
                    onAction = onCharts,
                )
                Sparkline(weeks = weekly.value, onTap = onCharts)
            }
        }

        Spacer(Modifier.height(24.dp))
        RecordsSectionRow(
            label = s.gymRecords.recentHeading,
            action = s.gymRecords.actionSeeAll,
            onAction = onHistory,
        )
        recent.forEach { session ->
            RecentRow(summary = session, onClick = { onSession(session) })
        }

        Spacer(Modifier.height(10.dp))
        BestsRow(onClick = onBests)
    }
}

/**
 * ‹ 六月 › — §4's pager.
 *
 * **A disabled arrow draws `c.hair` and is not clickable** (§4 edge case 4). `c.hair` rather than
 * `c.inkFaint` is the specified colour and it is the point: `c.inkFaint` is the colour of the *enabled*
 * secondary actions all over this page, so an unpressable arrow in it would read as pressable.
 */
@Composable
private fun MonthPager(
    month: YearMonth,
    currentMonth: YearMonth,
    canBack: Boolean,
    canForward: Boolean,
    onMonth: (YearMonth) -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The arrows carry the year on the same rule as the label between them: 「五月」 spoken over a
        // grid of last year's ink is the same lie, told to the user who cannot see the grid.
        PagerArrow("‹", enabled = canBack, label = monthPagerLabel(month.minusMonths(1), currentMonth, s)) {
            onMonth(month.minusMonths(1))
        }
        Text(
            text = monthPagerLabel(month, currentMonth, s),
            modifier = Modifier.padding(horizontal = 18.dp),
            style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 3.sp, color = c.ink),
        )
        PagerArrow("›", enabled = canForward, label = monthPagerLabel(month.plusMonths(1), currentMonth, s)) {
            onMonth(month.plusMonths(1))
        }
    }
}

/**
 * One arrow.
 *
 * The spoken label is the month it goes to — 五月 — rather than the glyph, which TalkBack would read as
 * a punctuation name.
 *
 * **A disabled arrow announces nothing at all.** It is unpressable and there is no month behind it, so
 * at that point it is decoration, and decoration a screen reader stops on is noise — the same reasoning
 * that removes rather than disables a door in `ResumeOptions`. Sighted users still get the affordance's
 * shape in `c.hair`, which is what tells them the pager has ended.
 */
@Composable
private fun PagerArrow(glyph: String, enabled: Boolean, label: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Glyph, enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                if (enabled) {
                    role = Role.Button
                    contentDescription = label
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 15.sp,
                color = if (enabled) c.inkSoft else c.hair,
            ),
        )
    }
}

/**
 * §5.1's month grid: **one `Canvas`, not 42 Boxes**, and one TalkBack node.
 *
 * 42 composables per month, re-laid-out on every page, for content that is 42 circles — and a `Canvas`
 * gives the today-ring the sub-pixel control a `Modifier.border` on a 20.dp `Box` does not.
 *
 * **The node is a summary, not a reading of the dots** (§4, accessibility). Per-day access is
 * `GYM.RECORDS.HISTORY`'s job, which is a real list with real nodes — *this is the deliberate answer to
 * "how does a blind user read a heatmap": you give them the list, not a worse heatmap.* The click label
 * is §4's own 記録の一覧をひらく, carried by `clickable`'s `onClickLabel` so the touch affordance and the
 * announcement are the same node rather than two that can disagree.
 *
 * **`dayAt` is deliberately not called here**, and §5.1 anticipates that: *"write `dayAt` even though
 * the whole grid currently navigates to HISTORY"*. Every cell of a month resolves to the same
 * `anchorMonth`, so calling it and discarding the result would be ceremony — one list, one idiom
 * (§4 edge case 2). It stays the tested hit-test that makes the layout arithmetic checkable, and the
 * day a per-day destination exists it is already written.
 *
 * **`DECISIONS.md` §Q11 lives in `inkLevel`, not here.** Below eight non-zero days the scale reports
 * `usedFallback` and every trained day comes back at one mid level, because ranking two days against
 * each other and presenting it as density is a claim about a sample that cannot support it. This draw
 * loop asks for a level and paints the alpha it is given; it is not allowed an opinion about which days
 * were heavy.
 *
 * **Alpha, not radius** (§5.1). The four levels differ only in opacity — graduated dot sizes read as a
 * bubble chart, and the whole point is that it is ink density.
 */
@Composable
private fun InkGrid(
    month: YearMonth,
    today: LocalDate,
    days: Map<LocalDate, DayLoad>,
    scale: LoadScale,
    onTap: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val grid = remember(month) { monthCells(month) }
    val summary = remember(month, days, s) { gridSemantics(month, days, s) }
    val caption = remember(month, days, s) { monthCaption(month, days, s) }

    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 日 月 火 … / S M T … — `fmt.weekdayInitials()`, which is Sunday-first in both languages so
        // that it keeps matching `monthCells`' `leadingBlank`. The cell is one CJK glyph wide, so the
        // letters are clamped to one line rather than allowed to wrap the header out of the grid.
        Row(Modifier.clearAndSetSemantics {}) {
            s.fmt.weekdayInitials().forEach { letter ->
                Box(Modifier.width(GRID_CELL), contentAlignment = Alignment.Center) {
                    Text(
                        text = letter,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = TextStyle(
                            fontFamily = Mincho,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = c.inkFaint,
                        ),
                    )
                }
            }
        }

        // A 5→6 row month must not snap (§5.1). The animation is on the box around the canvas, because
        // the canvas's own height is fixed by the row count it was handed.
        Box(Modifier.animateContentSize(tween(180, easing = LinearOutSlowInEasing))) {
            Canvas(
                Modifier
                    .width(GRID_CELL * 7)
                    .height(GRID_CELL * grid.rows)
                    // The month is one block the size of a card, so the wash takes a card's corner
                    // rather than washing a bare rectangle the width of seven cells.
                    .pressable(TempoShapes.Card, onClickLabel = s.gymRecords.gridA11y, onClick = onTap)
                    .semantics { contentDescription = summary; role = Role.Button },
            ) {
                val cellPx = GRID_CELL.toPx()
                val dotRadius = 2.dp.toPx()
                val ringRadius = 7.dp.toPx()
                val ringWidth = 1.dp.toPx()

                for (day in 1..grid.length) {
                    val index = grid.leadingBlank + day - 1
                    val centre = Offset(
                        x = (index % 7) * cellPx + cellPx / 2f,
                        y = (index / 7) * cellPx + cellPx / 2f,
                    )
                    val date = month.atDay(day)

                    if (date == today) {
                        drawCircle(c.hair, ringRadius, centre, style = Stroke(ringWidth))
                    }
                    // An unlived day is not an unfilled one (§4 edge case 3): no dot, no placeholder.
                    if (date.isAfter(today)) continue

                    val level = inkLevel(days[date]?.activeMs ?: 0L, scale)
                    if (level == INK_LEVEL_NONE) continue
                    drawCircle(c.ink.copy(alpha = inkAlpha(level)), dotRadius, centre)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = caption,
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
        )
    }
}

/**
 * §5.1's four opacities, one per ink level.
 *
 * A function rather than a `when` inside the draw loop so the mapping is one line to read against the
 * spec table, and so level 4 is a floor rather than a case that can be forgotten: `inkLevel` promises
 * 1..4 and anything above the table's last row is the darkest ink, never no ink at all.
 */
private fun inkAlpha(level: Int): Float = when (level) {
    1 -> 0.15f
    2 -> 0.35f
    3 -> 0.60f
    else -> 0.90f
}

/**
 * 四日 連続 / ゆるし 一回 使いました / 同じ調子が続いています — **one node** (§4, accessibility).
 *
 * Three announcements for one thought is worse than one, so the block is `clearAndSetSemantics` with
 * `streakCopy`'s joined sentence. The three lines are still three `Text`s, because they are three type
 * styles and one of them is conditional on a threshold the other two know nothing about.
 *
 * A broken streak draws `c.inkFaint` and says 連続は とぎれています — never 〇日 連続, which is a
 * scolding dressed as a statistic. And the forgiveness line never says how many days remain; the type
 * makes that unrepresentable, since [Streak] carries no remainder to print.
 */
@Composable
private fun StreakBlock(
    streak: Streak,
    monotony7d: Double?,
    historyDays: Int,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val copy = remember(streak, monotony7d, historyDays, s) {
        streakCopy(streak, s, monotony7d, historyDays)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = copy.semantics },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = copy.line,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = if (copy.broken) c.inkFaint else c.inkSoft,
            ),
        )
        copy.forgiveness?.let {
            Text(
                text = it,
                style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 2.sp, color = c.inkFaint),
            )
        }
        copy.monotony?.let {
            Text(
                text = it,
                style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
            )
        }
    }
}

/** §4's three tiles, in one washi row with hairline dividers. Each tile is one label-first node. */
@Composable
private fun TileRow(tiles: List<RecordTile>) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TempoShapes.Card)
            .background(c.card)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tiles.forEachIndexed { index, tile ->
            if (index > 0) {
                Box(Modifier.width(1.dp).height(34.dp).background(c.hair).clearAndSetSemantics {})
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = recordsTileSemantics(tile, s) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tile.value,
                    style = TextStyle(fontFamily = Gothic, fontSize = 20.sp, color = c.ink),
                )
                Text(
                    text = tile.label,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                )
            }
        }
    }
}

/**
 * The 週ごと preview — §5.2's bars at the index's 56.dp height.
 *
 * Every rule of §5.2 that applies at this size applies here: `drawRect` and not `drawRoundRect`,
 * because a rounded bar is a Material affectation and a flat ink bar is the app's register; no axis, no
 * gridlines, no in-canvas labels; the current week in `c.accent` because it is partial, not because it
 * is a triumph; and **a zero week draws a 2.dp stub rather than nothing**, so "you did nothing that
 * week" and "we have no data for that week" cannot render identically — the `Loadable` doctrine,
 * applied to pixels.
 *
 * Tappable, unlike the charts page's canvases: §4's exits list the sparkline beside 詳しく as a way into
 * `GYM.RECORDS.CHARTS`. That is navigation, not the scrubbing and tooltips §4 forbids there, and the
 * click label is 詳しく — the same word the action beside it carries, because they do the same thing.
 * The spoken description is `chartSemantics`', so the numbers are read once and by the canvas.
 */
@Composable
private fun Sparkline(weeks: List<WeekPoint>, onTap: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val values = remember(weeks) { sparklineSessions(weeks) }
    val summary = remember(weeks, s) {
        chartSemantics(ChartKind.WEEKLY_SESSIONS, ChartRange.TWELVE, values.map { it.toDouble() }, s)
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(SPARKLINE_HEIGHT)
            .pressable(TempoShapes.Card, onClickLabel = s.gymRecords.actionDetail, onClick = onTap)
            .semantics { contentDescription = summary; role = Role.Button },
    ) {
        val stub = 2.dp.toPx()
        val rects = barGeometry(
            values = values,
            width = size.width,
            height = size.height,
            minBarPx = 3.dp.toPx(),
            maxBarPx = 10.dp.toPx(),
        )
        rects.forEachIndexed { i, r ->
            val zero = values[i] == 0
            drawRect(
                color = when {
                    zero -> c.hair
                    i == rects.lastIndex -> c.accent
                    else -> c.inkSoft.copy(alpha = 0.75f)
                },
                topLeft = if (zero) Offset(r.left, size.height - stub) else Offset(r.left, r.top),
                size = if (zero) Size(r.width, stub) else Size(r.width, r.height),
            )
        }
        drawLine(
            color = c.hair,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/**
 * 読み込み中 where the sparkline will be — **its own composable, shared with nothing**.
 *
 * `00-plan.md` §4.1 rule 1 again, on the block where it is cheapest to get wrong: the previous shape
 * drew loading, failed and an empty series as the same picture, which is *nothing*, and §4 sanctions
 * that picture for failure alone. A read in flight is not a failure, and this box says so while
 * holding the sparkline's exact height so the column beneath it does not move when the read lands.
 */
@Composable
private fun SparklineLoading() {
    Box(
        Modifier.fillMaxWidth().height(SPARKLINE_HEIGHT),
        contentAlignment = Alignment.Center,
    ) { RecordsWord(LocalStrings.current.gymRecords.loading) }
}

/** 週ごと … 詳しく — a section heading with one accent word opposite it. */
@Composable
private fun RecordsSectionRow(label: String, action: String, onAction: () -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .padding(top = 18.dp, bottom = 6.dp)
                .clearAndSetSemantics { heading(); contentDescription = label },
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        HeaderAction(label = action, description = action, color = c.accent, onClick = onAction)
    }
}

/** One 最近 row: the whole row is one node, and tapping it opens that session's record. */
@Composable
private fun RecentRow(summary: SessionSummary, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val copy = remember(summary, s) { recentRowCopy(summary, s) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .pressable(TempoShapes.Row, onClick = onClick)
            .clearAndSetSemantics { role = Role.Button; contentDescription = copy.semantics }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copy.date,
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkFaint),
        )
        Text(
            text = copy.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
        )
        Text(
            text = copy.duration,
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
        )
        copy.rating?.let {
            Text(
                text = it,
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
            )
        }
        copy.partial?.let {
            Text(
                text = it,
                maxLines = 1,
                style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )
        }
    }
}

/** 最高 › — the way into `GYM.RECORDS.PR`. */
@Composable
private fun BestsRow(onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .pressable(TempoShapes.Row, onClick = onClick)
            .clearAndSetSemantics { role = Role.Button; contentDescription = s.gymRecords.bests }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = s.gymRecords.bests,
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
        )
        Text(
            text = "›",
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, color = c.inkFaint),
        )
    }
}

/**
 * 読み込み中 — no read has answered yet.
 *
 * Its own composable, sharing nothing with [RecordsEmpty] or [HistoryLossPanel]. The three look alike
 * and mean entirely different things, and a shared composable is how the next person to add a branch
 * accidentally makes an unreadable store say まだ 記録はありません.
 */
@Composable
private fun RecordsLoading() {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        RecordsWord(LocalStrings.current.gymRecords.loading)
    }
}

/**
 * まだ 記録はありません — **zero sessions ever, and known to be zero**.
 *
 * No empty grid and no zero tiles: §4 says it in four words — *a wall of 〇 is a scolding*. One action,
 * 型をえらぶ, and it rebases to the 型 tab rather than pushing a page, because the library is a place
 * the user already has a way back from.
 */
@Composable
private fun RecordsEmpty(onChoose: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecordsWord(s.gymRecords.recordsEmpty)
            Box(
                Modifier
                    .sizeIn(minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onChoose)
                    .semantics { contentDescription = s.gymRecords.chooseRoutine }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.gymRecords.chooseRoutine,
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
                )
            }
        }
    }
}

/**
 * 記録を読めません, **with とじる and no もう一度** — the quarantined store, acknowledged rather than
 * retried.
 *
 * `GymRepository.acknowledgeHistoryLoss` has existed since Phase 1 wired to nothing, and this is the
 * surface `02-data.md` §E.6 always meant it for: the corruption flag stays raised until the user has
 * *read* it, because one new session does not bring a year back and a fault that a later write could
 * clear is a fault the user might never see. Retrying is the wrong verb for it — `retry()` re-runs the
 * reads and a quarantined file fails them all again, so もう一度 here would be a button whose only
 * possible outcome is the same sentence — which is why §Q6's もう一度 for `StoreCorrupt` is answered
 * with a dismissal instead. とじる is §6's own generic close, already in use in `EventComposeScreen`.
 *
 * After the dismissal the reads re-run and the page renders whatever is actually left, which will
 * usually be the empty state. **That is the honest sequence and it is why the two are separate
 * composables**: the user is told their history could not be read, they acknowledge it, and only then
 * does the app say there is nothing here — never the other way round, and never both at once.
 *
 * If the read fails again — `SQLiteDatabaseCorruptException` from a query rather than the quarantine
 * flag — this panel comes straight back. It looks like the button did nothing; it is instead the store
 * still failing, and there is nothing truthful to draw in its place.
 *
 * **`StoreCorrupt` has two sources and this panel is selected on the fault value, which cannot tell
 * them apart.** One is the quarantine flag (`GymStore`), which is what everything above describes. The
 * other is a query throwing `SQLiteDatabaseCorruptException`, mapped to the same fault by
 * `DbSupport.toGymFault` **without any flag having been raised** — and in that case とじる lowers a flag
 * that is already down (a durable write and a notify, so the reads do re-run) while §Q6's bound action
 * for `StoreCorrupt`, もう一度, is withheld. The user gets a dismissal that behaves like the retry it is
 * not labelled as. Nothing on `GymRepository` exposes the flag, so the page cannot currently branch on
 * the *condition* rather than the symptom; the fix is a `historyLost: Flow<Boolean>` on the repository
 * over the corruption flag `GymStore` already holds, and it is reported. Until then this is disclosed
 * here rather than hidden, because the two cases differ only in which single word is on the button.
 */
@Composable
private fun HistoryLossPanel(onDismiss: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    // The sentence comes from `faultCopy`, not from a literal here: `DECISIONS.md` §Q6 binds it and a
    // second copy of 記録を読めません is exactly the divergence §Q7 forbids. Only the *action* differs,
    // and that difference is this page's whole assignment.
    val message = faultCopy(GymFault.StoreCorrupt, s).message

    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = message,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    letterSpacing = 2.sp,
                    color = c.inkSoft,
                ),
            )
            Box(
                Modifier
                    .sizeIn(minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = s.gymRecords.close }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.gymRecords.close,
                    style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 3.sp, color = c.accent),
                )
            }
        }
    }
}

/** §2's empty-state type, shared by the words that are only ever words. */
@Composable
private fun RecordsWord(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
    )
}
