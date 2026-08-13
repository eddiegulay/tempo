package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.Strings
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * The three charts of `GYM.RECORDS.CHARTS`, as arithmetic.
 *
 * **There is no chart library and there will not be one.** `CONTRIBUTING.md` forbids new
 * dependencies, so `04-library-records.md` §5 draws all three on a raw `Canvas` — and a `Canvas`
 * whose geometry is computed inside its own `DrawScope` is untestable, because a `DrawScope` needs a
 * device. So every position on every chart is decided here, in floats, and the draw scope only issues
 * primitives. §7 states the bargain in one line: *floats in, geometry out — this is what makes a
 * library-free chart testable.*
 *
 * That is why nothing in this file mentions Compose. No `Offset`, no `Size`, no `Dp`, no `Color`,
 * no `DrawScope`. The caller converts its `Dp`s to pixels with `toPx()` — which it must do anyway,
 * since a `Dp` means nothing without a density — and reads [BarRect] / [ChartPoint] into whatever
 * primitives it draws with. Colours never appear here at all: every one comes from
 * `LocalTempoColors` at the call site (`00-plan.md` §4.1 rule 6), and a chart that chose its own ink
 * would break silently the first time somebody opened Sumi.
 *
 * **No chart animates** (§4, edge case 1). These functions return an end state, once. There is no
 * `progress` parameter and adding one would be adding the growing-bars reveal design §10's motion
 * table deliberately has no entry for.
 */

// ─── Ranges ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The three range chips: 十二週 / 二十六週 / 一年.
 *
 * A year is **fifty-two weeks**, not twelve months, because every series behind these charts is
 * bucketed by `local_week_start` (§D.4) and a "month" range would need a second bucketing that
 * disagrees with the first at every year boundary. §4's own edge case 4 calls the year range
 * "一年 = 52 bars", which settles it.
 *
 * The labels are the strings, not derived from [weeks]: 一年 is not 五十二週, and computing the chip
 * text from the count would produce exactly that. They come from the table rather than a constructor
 * argument for §L3's reason — a label fixed at class-init cannot follow a language switch.
 */
enum class ChartRange(val weeks: Int) {
    TWELVE(12),
    TWENTY_SIX(26),
    YEAR(52),
    ;

    companion object {
        /** The range a freshly opened page shows. §4: *back … range resets to 十二週*. */
        val Default: ChartRange = TWELVE
    }
}

/** 十二週 / 二十六週 / 一年 — the chip's word. Never derived from [ChartRange.weeks]. */
fun ChartRange.label(strings: Strings): String = when (this) {
    ChartRange.TWELVE -> strings.gymRecords.rangeTwelveWeeks
    ChartRange.TWENTY_SIX -> strings.gymRecords.rangeTwentySixWeeks
    ChartRange.YEAR -> strings.gymRecords.rangeYear
}

/** How many weekly points a range asks the repository for — `weeklySeries(rangeWeeks(range))`. */
fun rangeWeeks(range: ChartRange): Int = range.weeks

/**
 * How many daily points the 積み上げ chart asks for — `volumeSeries(rangeDays(range))`.
 *
 * Seven times the weeks, so the daily chart covers exactly the span the two weekly charts above it
 * do. A range where the three charts disagreed about what "十二週" means would be worse than no
 * range chips at all.
 */
fun rangeDays(range: ChartRange): Int = range.weeks * 7

/** §4's `ThinHistory` gate: 積み上げ stays suppressed below four weeks of history. */
const val VOLUME_MIN_HISTORY_DAYS: Int = 28

/**
 * The 積み上げ chart's suppression copy, or null when it has earned its place.
 *
 * Design §7.4 is explicit that load surfaces stay suppressed until 28 days, and the reason is that a
 * weighted-volume trend over six days is noise presented as insight. Returned as copy rather than as
 * a boolean because the suppressed state **renders a sentence** — the chart does not silently vanish,
 * which would leave the user wondering whether it broke.
 */
fun chartSuppressionCopy(historyDays: Int, strings: Strings): String? =
    if (historyDays >= VOLUME_MIN_HISTORY_DAYS) {
        null
    } else {
        // The threshold reaches the sentence through the formatter, so 二十八日 and the constant above
        // it cannot drift: there is one 28 on this page and it is [VOLUME_MIN_HISTORY_DAYS].
        strings.gymRecords.volumeSuppressed(strings.fmt.days(VOLUME_MIN_HISTORY_DAYS))
    }

// ─── Geometry ───────────────────────────────────────────────────────────────────────────────────

/**
 * One bar, in pixels, top-left origin — the coordinate system a `DrawScope` already uses.
 *
 * A plain rectangle rather than a Compose `Rect`: this file has no Compose on its classpath, and the
 * call site composes `Offset(left, top)` / `Size(width, height)` in the one line where it draws.
 */
data class BarRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/** One point of a line, in the same coordinate system. */
data class ChartPoint(val x: Float, val y: Float)

/**
 * The bars of 週ごとの回数, centred in equal slots across [width].
 *
 * **The y-axis always starts at zero and there is no axis label to say so** (§4, edge case 3), which
 * makes it a property that has to be true rather than a claim that can be checked: every bar's height
 * is `value / max` of the full plot height, and `max` is `max(values.max(), 1)` so a series of all
 * zeroes draws a flat baseline instead of dividing by zero. A chart that auto-scaled its floor would
 * turn "two sessions" into "a big week" the moment the surrounding weeks were quiet.
 *
 * A zero week returns a **zero-height** rect at the baseline rather than being dropped from the list.
 * The caller draws its 2.dp stub at that x (§5.2), and it needs the position to do so: "you did
 * nothing that week" and "we have no data for that week" must not render identically, which is the
 * `Loadable` doctrine applied to pixels. Dropping the entry would also break the index alignment
 * `values[i]` relies on.
 *
 * [barFraction] is 0.42 of the slot at the call site, then clamped into [minBarPx]..[maxBarPx] so a
 * twelve-week range is not chunky and a fifty-two-week range is still visible. The clamp is finally
 * capped at the slot itself — a minimum wider than the slot would draw bars that overlap, which reads
 * as one solid block rather than as a dense chart, and denseness is what [isDense] is for.
 *
 * Returns empty for an empty series or a zero-area canvas. A `Canvas` measured at zero on its first
 * frame is ordinary, and NaN geometry from `width / 0` is not something a draw scope reports — it
 * simply paints nothing, somewhere, forever.
 */
fun barGeometry(
    values: List<Int>,
    width: Float,
    height: Float,
    barFraction: Float = DEFAULT_BAR_FRACTION,
    minBarPx: Float = 0f,
    maxBarPx: Float = Float.MAX_VALUE,
): List<BarRect> {
    if (values.isEmpty()) return emptyList()
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return emptyList()

    val slot = width / values.size
    val barWidth = (slot * barFraction)
        .coerceAtLeast(minBarPx)
        .coerceAtMost(maxBarPx)
        .coerceAtMost(slot)
    val maxValue = maxOf(values.max(), 1)

    return values.mapIndexed { i, value ->
        val barHeight = height * (value.coerceAtLeast(0).toFloat() / maxValue)
        BarRect(
            left = i * slot + (slot - barWidth) / 2f,
            top = height - barHeight,
            width = barWidth,
            height = barHeight,
        )
    }
}

/** §5.2's own figure, kept beside the function it belongs to rather than at four call sites. */
const val DEFAULT_BAR_FRACTION: Float = 0.42f

/**
 * The polyline of 活動時間 — **split into segments at every gap**.
 *
 * The return type is a list of *segments* and that is the whole point: §5.3 says the path breaks at a
 * gap rather than interpolating across it, because a straight line drawn from March to May through a
 * missing April states a value that was never recorded. On a page whose entire purpose is honest
 * records that is the wrong trade, and it is invisible once drawn — an interpolated line looks
 * exactly like a measured one.
 *
 * A null value is a gap. A **zero is not**: a week with no sessions is a real, measured zero and sits
 * on the baseline. The distinction survives all the way from `zeroFilledWeeks`, which exists to make
 * an untrained week a zero rather than an absence.
 *
 * A one-point series is centred rather than pinned to x = 0, so §4's `SinglePoint` state draws its
 * dot where a reader looks. With two or more points the first sits on the left edge and the last on
 * the right, because the last point carries the `c.accent` dot and an inset would leave it floating.
 *
 * [maxValue] can be supplied so two series share a scale — 積み上げ draws its daily ticks and its
 * seven-day mean over one plot, and letting them scale independently would put the mean line above
 * its own data.
 */
fun linePoints(
    values: List<Double?>,
    width: Float,
    height: Float,
    maxValue: Double? = null,
): List<List<ChartPoint>> {
    if (values.isEmpty()) return emptyList()
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return emptyList()

    val top = maxValue ?: values.filterNotNull().maxOrNull() ?: 0.0
    // As with the bars: a zero ceiling would divide by zero, and the honest picture of an all-zero
    // series is a flat line on the baseline, not an empty plot.
    val ceiling = if (top <= 0.0) 1.0 else top
    val step = if (values.size == 1) 0f else width / (values.size - 1)

    val segments = mutableListOf<List<ChartPoint>>()
    var current = mutableListOf<ChartPoint>()
    values.forEachIndexed { i, value ->
        if (value == null) {
            if (current.isNotEmpty()) segments += current
            current = mutableListOf()
            return@forEachIndexed
        }
        val x = if (values.size == 1) width / 2f else i * step
        val fraction = (value / ceiling).coerceIn(0.0, 1.0)
        current += ChartPoint(x = x, y = height - (height * fraction).toFloat())
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

/**
 * The seven-day trailing mean of 積み上げ, **null until the window is full**.
 *
 * §5.4 says it outright: the mean line starts on day seven. Averaging the first six days over
 * whatever data exists would draw a ramp that is an artefact of the window filling up, and a reader
 * has no way to tell that from a real early trend — the line would appear to rise every time the user
 * opened a new range.
 *
 * *Rejected* — dropping the first six entries instead of nulling them. The list is indexed against
 * the same x-positions as the daily ticks it is drawn over, so a shorter list would silently shift
 * the whole mean line six days left.
 */
fun trailingMean(values: List<Double>, window: Int): List<Double?> {
    require(window >= 1) { "a trailing mean needs a window of at least one day" }
    return values.indices.map { i ->
        if (i + 1 < window) null else values.subList(i + 1 - window, i + 1).average()
    }
}

/**
 * Whether a bar chart has run out of room and must be drawn as a line instead.
 *
 * §4 edge case 4: at 一年 the chart holds fifty-two bars, and below 4.dp of slot width each bar is
 * sub-pixel — the chart degrades into a grey smear that reads as texture rather than as data. The
 * switch is **decided here and not at draw time**, which is the point of the edge case: a renderer
 * that measured itself and changed shape mid-draw could not be tested, and the caption has to change
 * with it (週ごとの回数（折れ線）), which the draw scope cannot do.
 *
 * The threshold is a *slot* width, not a bar width. The bar is a fraction of its slot, so testing the
 * bar would make the answer depend on [DEFAULT_BAR_FRACTION] and change the day someone retunes it.
 */
fun isDense(count: Int, width: Float, minSlotPx: Float): Boolean {
    if (count <= 0 || !width.isFinite() || width <= 0f) return false
    return width / count < minSlotPx
}

// ─── Captions and spoken summaries ──────────────────────────────────────────────────────────────

/** Which of the three charts a caption or summary is for. */
enum class ChartKind {
    WEEKLY_SESSIONS,
    ACTIVE_MINUTES,
    VOLUME,
}

/** 週ごとの回数 / 活動時間 / 積み上げ — a chart's name, from the table (§L3). */
fun ChartKind.heading(strings: Strings): String = when (this) {
    ChartKind.WEEKLY_SESSIONS -> strings.gymRecords.chartWeeklySessions
    ChartKind.ACTIVE_MINUTES -> strings.gymRecords.activeTime
    ChartKind.VOLUME -> strings.gymRecords.chartVolume
}

/**
 * The heading above a chart, which gains a parenthetical when the bars have become a line.
 *
 * §4 edge case 4's exact string. Only the bar chart can change renderer, so [dense] is ignored by the
 * other two rather than silently producing 活動時間（折れ線） for a chart that was always a line.
 */
fun chartHeading(kind: ChartKind, strings: Strings, dense: Boolean = false): String {
    val heading = kind.heading(strings)
    return if (kind == ChartKind.WEEKLY_SESSIONS && dense) {
        strings.gymRecords.chartAsLine(heading)
    } else {
        heading
    }
}

/**
 * **The mean that the current week is not allowed to drag down.**
 *
 * `zeroFilledWeeks` always ends at the week containing today, so the last point of every weekly
 * series is a week in progress — on a Monday it holds one day of data and six days of nothing. §4
 * edge case 2 excludes it from ならして for exactly that reason, and drawing it in `c.accent` is the
 * *only* reason that colour appears on this page: it marks the partial point rather than celebrating
 * it.
 *
 * The same care is why this returns null rather than 0.0 for a single-point series. With one week of
 * history there is no completed week to average, and 〇回 as an average is a claim about a period the
 * user has not finished living. The caller omits the ならして fragment.
 *
 * The *maximum* is deliberately **not** filtered this way — see [chartCaption]. A partial week that
 * is already the busiest is a fact, and いちばん多い週 六回 stays true tomorrow.
 */
fun meanExcludingPartial(values: List<Double>): Double? {
    if (values.size < 2) return null
    return values.dropLast(1).average()
}

/**
 * The line beneath a chart, in `04-library-records.md` §6's three exact forms.
 *
 * | kind | caption |
 * |---|---|
 * | `WEEKLY_SESSIONS` | `いちばん多い週 六回 ・ ならして 三.四回` |
 * | `ACTIVE_MINUTES` | `合計 二千四百分 ・ ならして 二百分/週` |
 * | `VOLUME` | `日ごとの積み上げと 七日平均 ・ 目安` |
 *
 * **目安 is not optional** (§5.4, and §4 edge case 5). Weighted volume takes duration stations in
 * through an approximation — `volumeUnits` converts a hold at the exercise's own seconds-per-unit —
 * so the number is an estimate wearing a total's clothes, and the word is what keeps it honest.
 * 積み上げ's caption is therefore a constant: there is nothing in it to compute, and nothing in it to
 * drop.
 *
 * **合計 counts every week; ならして counts only the finished ones.** They disagree on purpose, and
 * that asymmetry is [meanExcludingPartial]'s whole reason for existing. A total is a fact about what
 * has happened; an average is a claim about a typical week, and this week is not yet one.
 *
 * The decimal goes through [coefficientLabel], which is the app's one kanji-decimal formatter
 * (`一.〇`, `三.四` — kanji digits, ASCII stop). Writing a second one here is exactly the divergence
 * `DECISIONS.md` §Q7 forbids: two formatters agree on every number anyone tests and disagree on the
 * one that ships.
 *
 * @param values the plotted series. Sessions and minutes per week for the first two; ignored for
 *   `VOLUME`, whose caption states its method rather than its numbers.
 */
fun chartCaption(kind: ChartKind, values: List<Double>, strings: Strings): String {
    val copy = strings.gymRecords
    val separator = strings.fmt.separator
    return when (kind) {
        ChartKind.VOLUME -> copy.volumeMethod + separator + copy.estimateNote

        ChartKind.WEEKLY_SESSIONS -> {
            val max = values.maxOrNull() ?: 0.0
            val mean = meanExcludingPartial(values)
            val head = copy.busiestWeek(strings.fmt.count(max.roundToInt()))
            if (mean == null) head else head + separator + copy.weeklyAverage(strings.fmt.coefficient(mean))
        }

        ChartKind.ACTIVE_MINUTES -> {
            val total = values.sum()
            val mean = meanExcludingPartial(values)
            val head = copy.totalMinutes(strings.fmt.minutes(total.roundToInt()))
            if (mean == null) {
                head
            } else {
                head + separator + copy.weeklyAverageMinutes(strings.fmt.minutes(mean.roundToInt()))
            }
        }
    }
}

/**
 * The one spoken node a chart carries — `role = Role.Image`, **and no click**.
 *
 * §4's accessibility note gives the bar chart's sentence verbatim:
 * `"週ごとの回数、直近十二週、いちばん多い週は 六回、ならして 三.四回、今週は 二回"`. The other two charts
 * have no written sentence, so theirs is the same skeleton — heading, 直近 + the range chip's own
 * label, then the chart's documented [chartCaption] — rather than three new sentences. That is
 * applying documented copy across a shape the spec established, which is the line `DECISIONS.md` §Q6
 * drew for `SessionGone`; **it is still a copy gap and is reported as one.**
 *
 * 今週は appears only on the weekly-sessions chart, because it is the only one whose §4 sentence has
 * it and the only one where the last point is a count you would say out loud.
 *
 * The caption lines under the canvas are `clearAndSetSemantics {}`-cleared by the caller, since their
 * numbers are already in this string — reading the same figures twice is worse than not reading them.
 * And there is no click label anywhere in here: the charts are not tappable (no tooltips, no
 * scrubbing), and an a11y affordance for an action that does not exist is a bug.
 */
fun chartSemantics(kind: ChartKind, range: ChartRange, values: List<Double>, strings: Strings): String {
    val copy = strings.gymRecords
    val listSeparator = strings.fmt.listSeparator
    val head = kind.heading(strings) + listSeparator + copy.overLast(range.label(strings))
    if (values.isEmpty()) return head
    return when (kind) {
        ChartKind.WEEKLY_SESSIONS -> {
            val max = values.maxOrNull() ?: 0.0
            val mean = meanExcludingPartial(values)
            listOfNotNull(
                head,
                copy.busiestWeekSpoken(strings.fmt.count(max.roundToInt())),
                mean?.let { copy.weeklyAverage(strings.fmt.coefficient(it)) },
                copy.thisWeekSpoken(strings.fmt.count(values.last().roundToInt())),
            ).joinToString(listSeparator)
        }
        ChartKind.ACTIVE_MINUTES, ChartKind.VOLUME ->
            head + listSeparator + chartCaption(kind, values, strings)
    }
}

/**
 * The bar chart's two footer labels: the first week's date on the left, 今週 on the right.
 *
 * §4's mock draws them as a `Row(SpaceBetween)` beneath the plot — 三月三十日 / 今週 — and they are
 * text rather than in-canvas labels because text inside a `DrawScope` needs a `TextMeasurer` and
 * `drawText`, which is more machinery than two static strings deserve (§5.1's reasoning about the
 * weekday letters, applied again).
 *
 * The left label is the **week start**, which is an ISO Monday computed in Kotlin at write time
 * (`weekStartOf`), never a formatted SQL date — `strftime` is UTC and would name the wrong Monday
 * for anyone east of Greenwich (`00-plan.md` §2 row 14).
 */
fun chartAxisLabels(weekStarts: List<LocalDate>, strings: Strings): Pair<String, String>? {
    val first = weekStarts.firstOrNull() ?: return null
    return strings.fmt.monthDay(first.atStartOfDay()) to strings.gymRecords.thisWeek
}
