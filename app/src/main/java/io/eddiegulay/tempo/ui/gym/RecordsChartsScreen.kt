package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.ChartKind
import io.eddiegulay.tempo.gym.ChartPoint
import io.eddiegulay.tempo.gym.ChartRange
import io.eddiegulay.tempo.gym.DEFAULT_BAR_FRACTION
import io.eddiegulay.tempo.gym.DailyLoad
import io.eddiegulay.tempo.gym.DayVolume
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.TrainingLoad
import io.eddiegulay.tempo.gym.WeekPoint
import io.eddiegulay.tempo.gym.barGeometry
import io.eddiegulay.tempo.gym.chartAxisLabels
import io.eddiegulay.tempo.gym.chartCaption
import io.eddiegulay.tempo.gym.chartHeading
import io.eddiegulay.tempo.gym.chartSemantics
import io.eddiegulay.tempo.gym.chartSuppressionCopy
import io.eddiegulay.tempo.gym.data.zeroFilledLoad
import io.eddiegulay.tempo.gym.isDense
import io.eddiegulay.tempo.gym.linePoints
import io.eddiegulay.tempo.gym.rangeDays
import io.eddiegulay.tempo.gym.rangeWeeks
import io.eddiegulay.tempo.gym.trailingMean
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.rememberMinuteTime
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.LocalDate

/*
 * GYM.RECORDS.CHARTS — 移り変わり. `04-library-records.md` §4's last records page.
 *
 * Three quiet trends — how often, how long, how much — **drawn in ink, with no library**. There is no
 * chart dependency and there will not be one (`CONTRIBUTING.md`), so every position on every chart is
 * decided by `gym/ChartGeometry.kt` in plain floats and this file only issues primitives. If you find
 * yourself doing arithmetic inside a `DrawScope`, it belongs there: a `DrawScope` needs a device, so
 * geometry computed inside one is geometry that is never tested.
 *
 * **The charts are not tappable.** No tooltips, no scrubbing, no selection, no click label, no
 * `Role.Button`. §4 states it outright and it is a deliberate restraint rather than an unbuilt
 * feature: the page answers "how am I trending", which is a question about the shape, and a chart you
 * can interrogate point-by-point is a chart that has to carry a value readout, an axis, and a
 * selection state — three elements this page does not have and does not want. An a11y affordance for
 * an action that does not exist is a bug, so the canvases carry `Role.Image` and a spoken summary and
 * no `onClick` at all.
 *
 * **No chart animates** (§4 edge case 1). Charts draw their end state on the first frame. There is no
 * `progress` parameter anywhere and a growing-bars reveal is exactly the loop design §10's motion table
 * has no entry for. §4's parenthetical also describes a 180ms cross-fade of the whole `Canvas` on a
 * range change; that is **deliberately not implemented** — the three series are re-subscribed when the
 * range flips, so both halves of such a fade would be showing the same in-flight read and the animation
 * would be between two identical frames. Reported rather than faked.
 *
 * **Three distinct states, and they never share a composable** (`00-plan.md` §4.1 rule 1). This page
 * has *four*: a read in flight (読み込み中), a user who has never trained (まだ 記録はありません), a
 * store that will not answer (記録を読めません, through `FaultStrip`), and — only on 積み上げ — a chart
 * that has not earned its place yet (二十八日ぶん たまると 出ます). The last one is the newest trap: a
 * suppressed chart is not empty and is not broken, it is *early*, and design §7.4 is explicit that load
 * surfaces stay suppressed until twenty-eight days because a weighted-volume trend over six days is
 * noise presented as insight.
 *
 * Pure logic is in `gym/ChartGeometry.kt` (the geometry unit's) and, for the two decisions this page
 * owns, in the section below — tested in `test/ui/gym/RecordsChartsCopyTest.kt`.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure shaping — Android-free
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Whether 積み上げ may be drawn at all, and what to say when it may not.
 *
 * Four cases and no fifth, because the gate has its own `Loadable` behind it: the twenty-eight-day
 * figure comes from `TrainingLoad.historyDays`, which is a store read like any other and can be in
 * flight or fail. Collapsing an unread gate into "suppressed" would put 二十八日ぶん たまると 出ます in
 * front of a user with two years of history for as long as the query took, which is a claim about their
 * training made out of a pending read.
 */
internal sealed interface VolumeGate {

    /** The gate itself has not answered. Draws 読み込み中, never the suppression sentence. */
    data object Loading : VolumeGate

    /** The gate could not be read. Draws the store's own sentence, never 二十八日ぶん. */
    data class Failed(val fault: TempoFault) : VolumeGate

    /** Under twenty-eight days. Carries §6's exact copy so the caller cannot paraphrase it. */
    data class Suppressed(val message: String) : VolumeGate

    /** Enough history. The series' own `Loadable` decides what happens next. */
    data object Open : VolumeGate
}

/**
 * §4's `ThinHistory` gate — **whether enough history exists, never whether the last month was busy**
 * (`DECISIONS.md` §Q24).
 *
 * `TrainingLoad` is `Ready(null)` whenever `GymStore` finds no daily load in its trailing twenty-eight
 * days, and in that branch it never computes `historyDays` at all. Reading the missing value as zero —
 * which is what this function used to do — told a user with years of training who came back after a
 * month away 二十八日ぶん たまると 出ます. That sentence is **false** for them, and it hides data they
 * have: `volumeSeries` queries eighty-three days back for the default range, so the sessions they took
 * five weeks ago are already in hand and would have drawn.
 *
 * So the null case is decided by [everTrained] instead, and the inference is exact rather than a
 * heuristic: a finished session exists (`Ready(true)`) *and* none of it falls in the trailing
 * twenty-eight days, therefore the first session is at least twenty-eight days old, therefore the gate
 * is earned. `Ready(false)` is a user who has genuinely never trained and the sentence is true for
 * them. An unread feed is [VolumeGate.Loading] and an unreadable one is [VolumeGate.Failed]: the gate
 * has four states precisely so an unknown answer is never rendered as a claim about the user.
 *
 * **This is the page failing safe, not the fix.** The fact belongs in the store — `historyDays` should
 * be computed from `MIN(local_date)` whether or not the recent window is empty, and `GymStore` already
 * reads that row two lines above the branch that discards it. That edit is in `gym/data/`, outside this
 * unit, and is reported. Until it lands this function errs towards *drawing* the chart, which is the
 * safe direction: a chart of real sessions can only be true, where the suppression sentence can be
 * false.
 *
 * The message is [chartSuppressionCopy]'s, not a literal: one sentence, one owner.
 */
internal fun volumeGate(load: Loadable<TrainingLoad?>, everTrained: Loadable<Boolean>): VolumeGate =
    when (load) {
        Loadable.Loading -> VolumeGate.Loading
        is Loadable.Failed -> VolumeGate.Failed(load.fault)
        is Loadable.Ready -> when (val days = load.value?.historyDays) {
            null -> when (everTrained) {
                Loadable.Loading -> VolumeGate.Loading
                is Loadable.Failed -> VolumeGate.Failed(everTrained.fault)
                is Loadable.Ready ->
                    if (everTrained.value) VolumeGate.Open else gateFor(historyDays = 0)
            }
            else -> gateFor(days)
        }
    }

/** [chartSuppressionCopy]'s answer as a gate, so the threshold is asked for in exactly one place. */
private fun gateFor(historyDays: Int): VolumeGate =
    chartSuppressionCopy(historyDays)?.let { VolumeGate.Suppressed(it) } ?: VolumeGate.Open

/**
 * The daily volume series as a **contiguous spine of [days] values**, ending on [end].
 *
 * `volumeSeries` is a `GROUP BY local_date`, so a rest day produces no row at all. Drawing that list
 * directly would space the ticks by *session*, not by day — a fortnight off would close up into a
 * dense run, and `trailingMean(daily, 7)` would average seven training days rather than seven days,
 * which is not a seven-day mean of anything.
 *
 * **The fill is `zeroFilledLoad`'s**, not a second date walk. That function is `GymMath`'s one date
 * spine and the brief for this phase is explicit that a divergent second copy is the failure mode to
 * avoid; the `DailyLoad` it produces carries `sessions` and `unrated` fields that are never read here,
 * which is the small price of having exactly one loop in the app that turns a sparse map of dates into
 * a dense list.
 *
 * [end] is the device's local date rather than the repository's monotonic-guarded one, which is **not**
 * exposed (reported). The consequence is bounded on purpose: the spine is extended to cover the last
 * day that actually carries data, so a wound-back clock can never drop a recorded day off the chart —
 * it can only lengthen the empty tail by a day, and this chart has no day labels and no tap targets for
 * that to be visible in. The streak, where a fabricated day would fabricate a number, uses the guarded
 * clock and is a different surface (§4, `RECORDS.INDEX` edge case 5).
 */
internal fun volumeSpine(series: List<DayVolume>, days: Int, end: LocalDate): List<Double> {
    if (days <= 0) return emptyList()
    val last = series.maxOfOrNull { it.date }
    val to = if (last != null && last.isAfter(end)) last else end
    val from = to.minusDays((days - 1).toLong())
    val byDate = series
        .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        .associate { it.date to DailyLoad(it.date, it.volumeUnits, sessions = 0, unrated = 0) }
    return zeroFilledLoad(from, to, byDate).map { it.load }
}

/** The sessions-per-week series, in the ints [barGeometry] takes. */
internal fun weeklySessionValues(points: List<WeekPoint>): List<Int> = points.map { it.sessions }

/** The active-minutes series. Zero-filled weeks are **real zeros**, never gaps — see [linePoints]. */
internal fun weeklyMinuteValues(points: List<WeekPoint>): List<Double> =
    points.map { it.activeMinutes.toDouble() }

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** §6: `records / history / charts / PR titles | … / 移り変わり / …` — utsurikawari, how things shift. */
private const val PAGE_TITLE = "移り変わり"

private const val CLOSE = "とじる"

/** §6: `records empty | まだ 記録はありません`. Reachable only from a resolved "never trained". */
private const val EMPTY = "まだ 記録はありません"

private const val LOADING = "読み込み中"

/** §4's exit into `GYM.RECORDS.HISTORY`, and §6's `see all`-shaped centred action. */
private const val SEE_HISTORY = "これまでを見る"

/** §2: `chart canvas | 96.dp tall, width − 44.dp`. The 44 is this page's 22.dp of padding, twice. */
private val CHART_HEIGHT = 96.dp

/**
 * §2 `list bottom padding | 88.dp with the tab bar, **40.dp without**` — and this page is without.
 *
 * `04` §4's per-page annotation reads `Tab bar — visible`, and it loses to the shell's one rule:
 * `tabBarVisible(stack) = stack.size == 1` (`GymNav.kt`). 移り変わり is only ever reached pushed on top
 * of `GymRoute.Records`, so the bar is never on screen while this page is, and 88.dp of it is 48.dp of
 * dead air under the last thing the user can read. The other half of the same spec row is the number.
 */
private val LIST_BOTTOM = 40.dp

/** §4 edge case 4: below 4.dp of **slot** width the bars go sub-pixel and the chart becomes a line. */
private val DENSE_MIN_SLOT = 4.dp

/** §2: `chart bar | bar 42% of slot, clamped [3.dp, 10.dp]`. */
private val BAR_MIN = 3.dp
private val BAR_MAX = 10.dp

/** §5.2: a zero week draws a 2.dp stub so "nothing that week" and "no data" do not render alike. */
private val ZERO_STUB = 2.dp

/** §2: `chart line | stroke 1.5.dp`; `chart baseline | 1.dp`; §5.4's daily ticks are 1.dp. */
private val LINE_STROKE = 1.5.dp
private val HAIRLINE = 1.dp

/** §5.3: the single-point dot is 2.dp `c.inkSoft`; the last point's accent dot is 3.dp. */
private val POINT_DOT = 2.dp
private val LAST_DOT = 3.dp

/** §5.4: `trailingMean(daily, 7)` — nulls for the first six days, so no ramp-in artefact. */
private const val MEAN_WINDOW_DAYS = 7

/**
 * 移り変わり — three trends, one range, no taps.
 *
 * @param gym the shell's one instance. The three series are read straight off
 *   [GymViewModel.repository]: no bests, series or load flows are exposed on the ViewModel (reported),
 *   and each is remembered on the range so flipping 十二週 → 一年 re-subscribes exactly once.
 *
 * **`everTrained` is how this page knows the difference between empty and quiet.** Every series here is
 * windowed, so "no sessions in the last twelve weeks" is not "no sessions" — rendering
 * まだ 記録はありません from an all-zero window would tell a user with three years of history that they
 * have never trained. `GymHomeFeed.everTrained` is a `SELECT 1 … LIMIT 1` over every finished session,
 * and it is the only honest source for that state. Its own `Loadable` is respected: an unread or
 * unreadable feed renders the charts, never the empty page. The cost is one extra subscription to the
 * home join for as long as this page is open, which is one query.
 *
 * The same read now also settles the 積み上げ gate in the one case `trainingLoad` cannot answer
 * (`DECISIONS.md` §Q24 — see [volumeGate]). `GymRepository.summary()` was *rejected* for both jobs even
 * though §Q22 has since added it: it is a `COUNT(*)` and a `SUM(...)` over the whole `session` table
 * where `everTrained` is a `LIMIT 1`, and neither question here wants the totals.
 */
@Composable
fun RecordsChartsScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    var range by remember { mutableStateOf(ChartRange.Default) }

    // The minute clock is read through a `derivedStateOf` rather than with `by`, so this page
    // recomposes at **midnight** rather than every sixty seconds: the only thing it wants from the
    // clock is the day the volume spine ends on, and redrawing three canvases once a minute to learn
    // that the date has not changed is work for nothing.
    val minute = rememberMinuteTime()
    val today by remember(minute) { derivedStateOf { minute.value.toLocalDate() } }

    val weeklyFlow = remember(gym, range) { gym.repository.weeklySeries(rangeWeeks(range)) }
    val weekly by weeklyFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
    val volumeFlow = remember(gym, range) { gym.repository.volumeSeries(rangeDays(range)) }
    val volume by volumeFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
    val loadFlow = remember(gym) { gym.repository.trainingLoad() }
    val trainingLoad by loadFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
    val feed by gym.homeFeed.collectAsStateWithLifecycle()

    // One read, two questions. `neverTrained` is the page's empty state; `everTrained` is the 積み上げ
    // gate's tie-breaker for the case `trainingLoad` cannot answer (§Q24) — and it keeps its own
    // `Loadable`, because "the feed has not answered" and "the user has never trained" are the two
    // states this page exists to keep apart.
    val neverTrained = (feed as? Loadable.Ready)?.value?.everTrained == false
    val everTrained: Loadable<Boolean> = when (val f = feed) {
        Loadable.Loading -> Loadable.Loading
        is Loadable.Failed -> Loadable.Failed(f.fault)
        is Loadable.Ready -> Loadable.Ready(f.value.everTrained)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = PAGE_TITLE,
                modifier = Modifier.semantics { heading() },
                style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
            )
            HeaderAction(label = CLOSE, description = CLOSE, color = c.inkFaint, onClick = { gym.onBack() })
        }

        RangeChips(selected = range, onSelect = { range = it })

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                neverTrained -> ChartsEmpty()
                // §4's `Loading`: **the whole body, never skeleton charts** — an empty chart and a
                // loading chart look identical, which is the doctrine's forbidden case drawn in pixels.
                weekly is Loadable.Loading -> ChartsLoading()
                else -> ChartsBody(
                    range = range,
                    weekly = weekly,
                    volume = volume,
                    gate = volumeGate(trainingLoad, everTrained),
                    today = today,
                    onRetry = gym::retry,
                    onHistory = { gym.go(GymRoute.History()) },
                )
            }
        }
    }
}

/**
 * 十二週 / 二十六週 / 一年 — the labels are [ChartRange]'s own, never derived from its week count.
 *
 * 一年 is fifty-two weeks and is not spelled 五十二週; computing the chip text from the number would
 * produce exactly that. The chips reuse the library index's `FilterChip` geometry — 48.dp of target,
 * accent when chosen, no pill and no fill — because a second chip idiom on the same feature would read
 * as a different kind of control.
 */
@Composable
private fun RangeChips(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChartRange.entries.forEach { entry ->
            val isSelected = entry == selected
            Box(
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clickable { onSelect(entry) }
                    .semantics {
                        role = Role.Button
                        // §3's accessibility line for a chosen chip, verbatim. There is no documented
                        // word for the unselected state and none is invented — an unselected chip
                        // carries no state, which is also what it means.
                        if (isSelected) stateDescription = "選択中"
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        color = if (isSelected) c.accent else c.inkFaint,
                    ),
                )
            }
        }
    }
}

/**
 * The three charts and これまでを見る, in one scroll.
 *
 * The `Loadable`s arrive per read, so each chart resolves its own — §4 asks for `Error` **per chart**,
 * and the two weekly charts share one query, so a `weeklySeries` failure legitimately marks both. What
 * it must never do is take 積み上げ down with it, or the page with it.
 */
@Composable
private fun ChartsBody(
    range: ChartRange,
    weekly: Loadable<List<WeekPoint>>,
    volume: Loadable<List<DayVolume>>,
    gate: VolumeGate,
    today: LocalDate,
    onRetry: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        WeeklySessionsChart(range = range, weekly = weekly, onRetry = onRetry)
        ActiveMinutesChart(range = range, weekly = weekly, onRetry = onRetry)
        VolumeChart(range = range, volume = volume, gate = gate, today = today, onRetry = onRetry)

        Spacer(Modifier.height(10.dp))
        ChartsCenteredAction(label = SEE_HISTORY, onClick = onHistory)
        Spacer(Modifier.height(LIST_BOTTOM))
    }
}

/**
 * 週ごとの回数 — bars, or a line when the range has run out of room.
 *
 * §4 edge case 4: at 一年 the chart holds fifty-two bars and below 4.dp of slot width each one is
 * sub-pixel, so the chart degrades into a grey smear that reads as texture rather than as data. The
 * switch is decided **here, outside the draw scope**, by [isDense] over the width `BoxWithConstraints`
 * measured — which is also why it can change the heading to 週ごとの回数（折れ線）, something a
 * `DrawScope` could not do.
 */
@Composable
private fun WeeklySessionsChart(
    range: ChartRange,
    weekly: Loadable<List<WeekPoint>>,
    onRetry: () -> Unit,
) {
    when (weekly) {
        Loadable.Loading -> {
            ChartHeading(chartHeading(ChartKind.WEEKLY_SESSIONS))
            ChartLoading()
        }
        is Loadable.Failed -> {
            ChartHeading(chartHeading(ChartKind.WEEKLY_SESSIONS))
            FaultStrip(fault = weekly.fault, onRecover = onRetry, modifier = Modifier.padding(vertical = 6.dp))
        }
        is Loadable.Ready -> {
            val points = weekly.value
            val counts = remember(points) { weeklySessionValues(points) }
            val asDoubles = remember(counts) { counts.map { it.toDouble() } }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val dense = denseFor(counts.size, maxWidth)
                Column(Modifier.fillMaxWidth()) {
                    ChartHeading(chartHeading(ChartKind.WEEKLY_SESSIONS, dense))
                    val summary = chartSemantics(ChartKind.WEEKLY_SESSIONS, range, asDoubles)
                    if (dense) {
                        LineCanvas(values = asDoubles, semantics = summary)
                    } else {
                        BarCanvas(values = counts, semantics = summary)
                    }
                    chartAxisLabels(points.map { it.weekStart })?.let { (first, last) ->
                        ChartAxisRow(first, last)
                    }
                    ChartCaption(chartCaption(ChartKind.WEEKLY_SESSIONS, asDoubles))
                }
            }
        }
    }
}

/** 活動時間 — one line, straight segments, no smoothing and no area fill (§5.3). */
@Composable
private fun ActiveMinutesChart(
    range: ChartRange,
    weekly: Loadable<List<WeekPoint>>,
    onRetry: () -> Unit,
) {
    ChartHeading(chartHeading(ChartKind.ACTIVE_MINUTES))
    when (weekly) {
        Loadable.Loading -> ChartLoading()
        is Loadable.Failed ->
            FaultStrip(fault = weekly.fault, onRecover = onRetry, modifier = Modifier.padding(vertical = 6.dp))
        is Loadable.Ready -> {
            val minutes = remember(weekly.value) { weeklyMinuteValues(weekly.value) }
            LineCanvas(
                values = minutes,
                semantics = chartSemantics(ChartKind.ACTIVE_MINUTES, range, minutes),
            )
            ChartCaption(chartCaption(ChartKind.ACTIVE_MINUTES, minutes))
        }
    }
}

/**
 * 積み上げ — daily ticks under a seven-day mean, and the one chart with a gate in front of it.
 *
 * Four states, four bodies, no sharing: the gate's own read may be in flight or failed, the gate may be
 * shut (二十八日ぶん たまると 出ます — *early*, not empty and not broken), and only past it does the
 * series' own `Loadable` get a say.
 *
 * Both passes share **one scale**, which is why [linePoints] takes an explicit ceiling: letting the
 * mean scale to its own maximum would draw it above the data it is the mean of.
 */
@Composable
private fun VolumeChart(
    range: ChartRange,
    volume: Loadable<List<DayVolume>>,
    gate: VolumeGate,
    today: LocalDate,
    onRetry: () -> Unit,
) {
    ChartHeading(chartHeading(ChartKind.VOLUME))
    when (gate) {
        VolumeGate.Loading -> ChartLoading()
        is VolumeGate.Failed ->
            FaultStrip(fault = gate.fault, onRecover = onRetry, modifier = Modifier.padding(vertical = 6.dp))
        is VolumeGate.Suppressed -> ChartSuppressed(gate.message)
        VolumeGate.Open -> when (volume) {
            Loadable.Loading -> ChartLoading()
            is Loadable.Failed ->
                FaultStrip(fault = volume.fault, onRecover = onRetry, modifier = Modifier.padding(vertical = 6.dp))
            is Loadable.Ready -> {
                val days = rangeDays(range)
                val spine = remember(volume.value, days, today) { volumeSpine(volume.value, days, today) }
                val mean = remember(spine) { trailingMean(spine, MEAN_WINDOW_DAYS) }
                // The one number both passes are drawn against, decided **here**: a `DrawScope` needs a
                // device, so arithmetic performed inside one is arithmetic no JVM test can reach (this
                // file's header, and `ChartGeometry.kt`'s). The canvas is handed the ceiling and issues
                // primitives, exactly as it is handed the spine and the mean.
                val ceiling = remember(spine) { spine.maxOrNull() }
                VolumeCanvas(
                    daily = spine,
                    mean = mean,
                    ceiling = ceiling,
                    semantics = chartSemantics(ChartKind.VOLUME, range, spine),
                )
                ChartCaption(chartCaption(ChartKind.VOLUME, spine))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The canvases — primitives only, every colour from LocalTempoColors
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * §5.2's bars, verbatim in behaviour.
 *
 * `drawRect`, not `drawRoundRect`: a rounded bar is a Material affectation and a flat ink bar is the
 * app's register. No axis, no gridlines, no in-canvas labels — text inside a `DrawScope` needs a
 * `TextMeasurer` and `drawText`, and the two labels beneath are `Text`s for that reason.
 *
 * **A zero week draws a 2.dp stub at the baseline, not nothing.** [barGeometry] returns a zero-height
 * rect at the right x precisely so this can happen: "you did nothing that week" and "we have no data
 * for that week" must not render identically, which is the `Loadable` doctrine applied to pixels.
 *
 * The last bar is `c.accent` because it is the **partial** current week and for no other reason — the
 * same rationing of vermillion as everywhere, marking the point rather than celebrating it. It is also
 * excluded from ならして by [chartCaption], so the colour and the arithmetic agree.
 */
@Composable
private fun BarCanvas(values: List<Int>, semantics: String) {
    val c = LocalTempoColors.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            // Role.Image and no click: the chart is not tappable (§4).
            .semantics { role = Role.Image; contentDescription = semantics },
    ) {
        val stub = ZERO_STUB.toPx()
        val rects = barGeometry(
            values = values,
            width = size.width,
            height = size.height,
            barFraction = DEFAULT_BAR_FRACTION,
            minBarPx = BAR_MIN.toPx(),
            maxBarPx = BAR_MAX.toPx(),
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
        drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), HAIRLINE.toPx())
    }
}

/**
 * §5.3's line: straight `lineTo`, round caps and joins, **breaks at gaps**, no fill.
 *
 * A spline through weekly totals invents values that were never recorded; on a page whose purpose is
 * honest records that is the wrong trade, and round joins already soften the polyline into brushwork.
 * Gaps `moveTo` rather than interpolate, which is why [linePoints] returns a list of segments.
 *
 * Only the final point carries a dot, in `c.accent`. A one-point series therefore renders as that dot
 * and no path, which is §4's `SinglePoint` state — never a horizontal line drawn from one measurement.
 */
@Composable
private fun LineCanvas(values: List<Double>, semantics: String) {
    val c = LocalTempoColors.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { role = Role.Image; contentDescription = semantics },
    ) {
        val segments = linePoints(values, size.width, size.height)
        drawSegments(segments, c.inkSoft, LINE_STROKE.toPx(), POINT_DOT.toPx())
        segments.lastOrNull()?.lastOrNull()?.let {
            drawCircle(c.accent, LAST_DOT.toPx(), Offset(it.x, it.y))
        }
        drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), HAIRLINE.toPx())
    }
}

/**
 * §5.4's two passes on one plot: raw daily volume as 1.dp `c.hair` ticks, then the seven-day mean drawn
 * exactly like §5.3.
 *
 * The ticks are **texture, not data you read off** — they are what makes the mean legible as a mean —
 * and they are positioned by [linePoints] rather than by a second geometry function, so a tick and the
 * mean point above it are guaranteed to share an x. Both passes are given the same [ceiling], and it
 * arrives as a parameter rather than being taken off `daily` here: letting the mean scale to its own
 * maximum would draw it above the data it is the mean of, and deciding the shared scale inside the
 * `DrawScope` would put this page's one piece of arithmetic in the one place no test can reach it.
 *
 * `trailingMean` returns nulls for the first six days, so the mean line starts on day seven: no ramp-in
 * artefact, and no fabricated early trend that would appear to rise every time a range was opened.
 */
@Composable
private fun VolumeCanvas(
    daily: List<Double>,
    mean: List<Double?>,
    ceiling: Double?,
    semantics: String,
) {
    val c = LocalTempoColors.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { role = Role.Image; contentDescription = semantics },
    ) {
        val tickTops = linePoints(daily, size.width, size.height, ceiling)
        val hair = HAIRLINE.toPx()
        tickTops.forEach { segment ->
            segment.forEach { point ->
                drawLine(c.hair, Offset(point.x, size.height), Offset(point.x, point.y), hair)
            }
        }

        val meanSegments = linePoints(mean, size.width, size.height, ceiling)
        drawSegments(meanSegments, c.inkSoft, LINE_STROKE.toPx(), POINT_DOT.toPx())
        meanSegments.lastOrNull()?.lastOrNull()?.let {
            drawCircle(c.accent, LAST_DOT.toPx(), Offset(it.x, it.y))
        }
        drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), hair)
    }
}

/**
 * The shared stroke pass of §5.3 and §5.4 — **a drawing helper, not a decision**.
 *
 * It holds no branch about what the data means: the caller has already chosen the colour, the series
 * and the ceiling. What it owns is the one-point case, which must be a dot rather than a
 * `Path` with a single `moveTo` (which draws nothing at all).
 */
private fun DrawScope.drawSegments(
    segments: List<List<ChartPoint>>,
    color: Color,
    strokeWidth: Float,
    dotRadius: Float,
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    segments.forEach { segment ->
        when {
            segment.size == 1 -> drawCircle(color, dotRadius, Offset(segment.first().x, segment.first().y))
            segment.size > 1 -> drawPath(
                path = Path().apply {
                    moveTo(segment.first().x, segment.first().y)
                    segment.drop(1).forEach { lineTo(it.x, it.y) }
                },
                color = color,
                style = stroke,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Chrome
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Whether the bars have run out of room, decided in [isDense] over measured pixels — not at draw time. */
@Composable
private fun denseFor(count: Int, available: Dp): Boolean {
    val density = LocalDensity.current
    return remember(count, available, density) {
        with(density) { isDense(count, available.toPx(), DENSE_MIN_SLOT.toPx()) }
    }
}

/** A chart's name: §2's section-heading token, `heading()` so TalkBack can jump between the three. */
@Composable
private fun ChartHeading(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).semantics { heading() },
        style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
    )
}

/**
 * The caption beneath a chart, **cleared for TalkBack**.
 *
 * §4's accessibility note: the caption's numbers are already in the canvas node's spoken summary, and
 * reading the same figures twice is worse than not reading them.
 */
@Composable
private fun ChartCaption(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {},
        style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
    )
}

/** 三月三十日 / 今週 — §5.2's `Row(SpaceBetween)` beneath the bars, cleared for the same reason. */
@Composable
private fun ChartAxisRow(first: String, last: String) {
    val c = LocalTempoColors.current
    val style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = first, style = style)
        Text(text = last, style = style)
    }
}

/**
 * 読み込み中 for one chart — a read in flight, and never mistakable for a chart with nothing in it.
 *
 * Sized to the plot it stands in so the page does not jump when the data lands, and kept apart from
 * [ChartSuppressed] and [ChartsEmpty] even though all three are a line of faint Mincho: they mean
 * *not yet*, *not enough* and *never*, and a shared composable is what merges them the first time
 * someone adds a branch (`00-plan.md` §4.1 rule 1).
 */
@Composable
private fun ChartLoading() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
        Text(
            text = LOADING,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * 二十八日ぶん たまると 出ます — the 積み上げ gate.
 *
 * **Not an empty state and not a failure.** The chart is suppressed because a weighted-volume trend
 * over six days is noise presented as insight (design §7.4), and the sentence says so in the one form
 * §6 gives it: something is coming, and it is worth waiting for.
 */
@Composable
private fun ChartSuppressed(message: String) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/** 読み込み中 over the whole body — §4's `Loading`, which is explicitly **not** skeleton charts. */
@Composable
private fun ChartsLoading() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = LOADING,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/** まだ 記録はありません — nothing has ever been trained. Reachable only from a resolved `everTrained`. */
@Composable
private fun ChartsEmpty() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = EMPTY,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/** §2's centred action row: one accent word, 48.dp of target, no box around it. */
@Composable
private fun ChartsCenteredAction(label: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}
