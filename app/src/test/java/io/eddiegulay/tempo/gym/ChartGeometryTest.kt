package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three charts, without a device and without a chart library.
 *
 * There is no chart library in this app and there will not be one, so every position on every chart
 * is computed by a function in `ChartGeometry.kt` and drawn by a `DrawScope` that makes no decisions.
 * That is the only arrangement under which a chart can be tested at all — a `DrawScope` needs a
 * device, and geometry computed inside one is geometry nobody ever checks.
 *
 * Three properties are on trial here and none of them is about pixels. **The y-axis starts at zero**
 * and there is no axis label to say so, which makes it a promise rather than a claim. **A gap is not
 * a zero**: an untrained week sits on the baseline and a week with no data breaks the line, because a
 * straight segment drawn across a hole states a value that was never recorded. And **the current week
 * never drags the average down** — it is a week in progress, so it is drawn in `c.accent` and left
 * out of ならして.
 */
class ChartGeometryTest {

    // ─── bars (§5.2) ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bars are centred in equal slots and scale from a zero baseline`() {
        // Twelve weeks across 480px: 40px slots, 0.42 of which is 16.8 — clamped down to the 10px
        // maximum so a short range does not read as a bar chart of doors.
        val rects = barGeometry(
            values = listOf(2, 4, 0, 6, 3, 1, 5, 2, 4, 3, 2, 6),
            width = 480f,
            height = 96f,
            minBarPx = 3f,
            maxBarPx = 10f,
        )
        assertEquals(12, rects.size)
        assertEquals(10f, rects[0].width, 0.001f)
        assertEquals(15f, rects[0].left, 0.001f)   // (40 − 10) / 2
        assertEquals(55f, rects[1].left, 0.001f)
        // The tallest week fills the plot and touches y = 0; every other bar is its fraction of that.
        assertEquals(0f, rects[3].top, 0.001f)
        assertEquals(96f, rects[3].height, 0.001f)
        assertEquals(64f, rects[1].height, 0.001f) // 4 of 6
        assertEquals(32f, rects[1].top, 0.001f)
    }

    @Test
    fun `a zero week keeps its slot with no height, because the caller draws its stub there`() {
        // "You did nothing that week" and "we have no data for that week" must not render
        // identically — the Loadable doctrine applied to pixels. The caller draws a 2.dp stub at this
        // x, and it needs the x.
        val rects = barGeometry(listOf(2, 0, 3), width = 300f, height = 96f)
        assertEquals(3, rects.size)
        assertEquals(0f, rects[1].height, 0.001f)
        assertEquals(96f, rects[1].top, 0.001f)
        assertEquals(rects[1].left, 100f + (100f - rects[1].width) / 2f, 0.001f)
    }

    @Test
    fun `an all-zero series draws a flat baseline rather than dividing by zero`() {
        // maxValue = max(values.max(), 1). Without the floor this is a NaN rect, which a draw scope
        // does not report — it simply paints nothing, somewhere, forever.
        val rects = barGeometry(listOf(0, 0, 0), width = 300f, height = 96f)
        assertTrue(rects.all { it.height == 0f && it.top == 96f })
    }

    @Test
    fun `a dense range clamps up to the minimum bar, and never past its own slot`() {
        // 52 bars across 260px is a 5px slot: 0.42 of it is sub-pixel, so the 3px floor applies.
        val roomy = barGeometry(List(52) { 3 }, width = 260f, height = 96f, minBarPx = 3f, maxBarPx = 10f)
        assertEquals(3f, roomy[0].width, 0.001f)

        // …but a floor wider than the slot would overlap its neighbours and read as one solid block.
        val cramped = barGeometry(List(52) { 3 }, width = 104f, height = 96f, minBarPx = 3f, maxBarPx = 10f)
        assertEquals(2f, cramped[0].width, 0.001f)
    }

    @Test
    fun `an unmeasured canvas yields no geometry at all`() {
        assertTrue(barGeometry(listOf(1, 2), width = 0f, height = 96f).isEmpty())
        assertTrue(barGeometry(listOf(1, 2), width = 300f, height = 0f).isEmpty())
        assertTrue(barGeometry(emptyList(), width = 300f, height = 96f).isEmpty())
        assertTrue(linePoints(listOf(1.0, 2.0), width = Float.NaN, height = 96f).isEmpty())
    }

    // ─── the line (§5.3) ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the line spans the full width and inverts the y axis`() {
        val segments = linePoints(listOf(0.0, 100.0, 50.0), width = 200f, height = 80f)
        assertEquals(1, segments.size)
        assertEquals(listOf(0f, 100f, 200f), segments[0].map { it.x })
        assertEquals(listOf(80f, 0f, 40f), segments[0].map { it.y })
    }

    @Test
    fun `a gap breaks the path and a zero does not`() {
        // §5.3: the path breaks at a gap, never interpolates across it. A zero week is a measured
        // zero and belongs on the baseline — that distinction survives all the way from
        // zeroFilledWeeks, which exists to make an untrained week a zero rather than an absence.
        val withZero = linePoints(listOf(4.0, 0.0, 4.0), width = 200f, height = 80f)
        assertEquals(1, withZero.size)
        assertEquals(80f, withZero[0][1].y, 0.001f)

        val withGap = linePoints(listOf(4.0, null, 4.0), width = 200f, height = 80f)
        assertEquals(2, withGap.size)
        assertEquals(listOf(0f), withGap[0].map { it.x })
        assertEquals(listOf(200f), withGap[1].map { it.x })
    }

    @Test
    fun `a single point is centred, so the SinglePoint state draws where a reader looks`() {
        // §4's SinglePoint state: one dot and no path. Never a horizontal line from one point.
        val segments = linePoints(listOf(7.0), width = 200f, height = 80f)
        assertEquals(1, segments.size)
        assertEquals(1, segments[0].size)
        assertEquals(100f, segments[0][0].x, 0.001f)
    }

    @Test
    fun `a shared ceiling lets two series be drawn over one plot`() {
        // 積み上げ draws daily ticks and a seven-day mean over the same plot; scaling them
        // independently would put the mean line above its own data.
        val mean = linePoints(listOf(5.0, 5.0), width = 100f, height = 100f, maxValue = 10.0)
        assertEquals(50f, mean[0][0].y, 0.001f)
    }

    // ─── trailing mean (§5.4) ───────────────────────────────────────────────────────────────────

    @Test
    fun `the seven-day mean starts on day seven, with no ramp-in`() {
        // Averaging the first six days over a partly-filled window draws a rise that is an artefact
        // of the window filling up, and a reader cannot tell that from a real early trend.
        val daily = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        val mean = trailingMean(daily, 7)
        assertEquals(8, mean.size)
        assertEquals(List(6) { null }, mean.take(6))
        assertEquals(4.0, mean[6]!!, 1e-9)   // (1+2+3+4+5+6+7) / 7
        assertEquals(5.0, mean[7]!!, 1e-9)   // (2+…+8) / 7
    }

    @Test
    fun `the mean keeps its index, so it lines up with the ticks it is drawn over`() {
        // Dropping the first six entries instead of nulling them would shift the whole line six days
        // to the left, silently.
        assertEquals(30, trailingMean(List(30) { 1.0 }, 7).size)
    }

    // ─── density, ranges, suppression ───────────────────────────────────────────────────────────

    @Test
    fun `a year of bars is dense and twelve weeks is not`() {
        // §4 edge case 4, and the switch is decided here rather than at draw time — the heading has to
        // change with it, which a DrawScope cannot do.
        assertTrue(isDense(count = 52, width = 400f, minSlotPx = 12f))
        assertFalse(isDense(count = 12, width = 400f, minSlotPx = 12f))
        assertFalse(isDense(count = 0, width = 400f, minSlotPx = 12f))
    }

    @Test
    fun `a dense bar chart says so in its heading`() {
        assertEquals("週ごとの回数（折れ線）", chartHeading(ChartKind.WEEKLY_SESSIONS, StringsJa, dense = true))
        assertEquals("週ごとの回数", chartHeading(ChartKind.WEEKLY_SESSIONS, StringsJa, dense = false))
        // Only the bars can change renderer. 活動時間 was always a line and must not gain the note.
        assertEquals("活動時間", chartHeading(ChartKind.ACTIVE_MINUTES, StringsJa, dense = true))
    }

    @Test
    fun `a year is fifty-two weeks, and the daily chart covers the same span`() {
        assertEquals(12, rangeWeeks(ChartRange.TWELVE))
        assertEquals(26, rangeWeeks(ChartRange.TWENTY_SIX))
        assertEquals(52, rangeWeeks(ChartRange.YEAR))
        assertEquals(84, rangeDays(ChartRange.TWELVE))
        assertEquals(364, rangeDays(ChartRange.YEAR))
        assertEquals(ChartRange.TWELVE, ChartRange.Default)
    }

    @Test
    fun `a range chip is labelled, never computed from its week count`() {
        // 一年 is fifty-two weeks and is not spelled 五十二週; deriving the chip text from [weeks]
        // would produce exactly that, in either language.
        assertEquals(
            listOf("十二週", "二十六週", "一年"),
            ChartRange.entries.map { it.label(StringsJa) },
        )
        assertEquals(
            listOf("12 weeks", "26 weeks", "1 year"),
            ChartRange.entries.map { it.label(StringsEn) },
        )
    }

    @Test
    fun `the load chart stays suppressed below twenty-eight days and says why`() {
        // Design §7.4: a weighted-volume trend over six days is noise presented as insight. The
        // suppressed chart renders a sentence rather than vanishing, so nobody wonders if it broke.
        assertEquals("二十八日ぶん たまると 出ます", chartSuppressionCopy(27, StringsJa))
        assertNull(chartSuppressionCopy(28, StringsJa))
    }

    // ─── captions (§6) ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the bar caption reports the busiest week and the average of the finished ones`() {
        // §6's string, on numbers that produce it: the first five weeks average 3.4, the sixth is
        // this week and is excluded from ならして but not from いちばん多い週.
        val weeks = listOf(6.0, 4.0, 3.0, 2.0, 2.0, 2.0)
        assertEquals("いちばん多い週 六回 ・ ならして 三.四回", chartCaption(ChartKind.WEEKLY_SESSIONS, weeks, StringsJa))
    }

    @Test
    fun `a Monday must not drag the average down`() {
        // §4 edge case 2, stated as arithmetic. Four full weeks of four sessions and a current week
        // holding one: ならして is four, not 3.4.
        val weeks = listOf(4.0, 4.0, 4.0, 4.0, 1.0)
        assertEquals(4.0, meanExcludingPartial(weeks)!!, 1e-9)
        assertEquals("いちばん多い週 四回 ・ ならして 四.〇回", chartCaption(ChartKind.WEEKLY_SESSIONS, weeks, StringsJa))
    }

    @Test
    fun `one week of history has no average to state`() {
        // 〇回 as an average is a claim about a period the user has not finished living. The fragment
        // is omitted and the total still stands.
        assertNull(meanExcludingPartial(listOf(3.0)))
        assertEquals("いちばん多い週 三回", chartCaption(ChartKind.WEEKLY_SESSIONS, listOf(3.0), StringsJa))
    }

    @Test
    fun `the minutes caption totals every week and averages the finished ones`() {
        assertEquals(
            "合計 二千四百分 ・ ならして 二百分/週",
            chartCaption(ChartKind.ACTIVE_MINUTES, List(12) { 200.0 }, StringsJa),
        )
    }

    @Test
    fun `the load caption states its method and always carries 目安`() {
        // §5.4: 目安 is not optional. Duration stations enter weighted volume through an
        // approximation, so the number is an estimate wearing a total's clothes.
        assertEquals(
            "日ごとの積み上げと 七日平均 ・ 目安",
            chartCaption(ChartKind.VOLUME, listOf(120.0, 80.0), StringsJa),
        )
        assertTrue(chartCaption(ChartKind.VOLUME, emptyList(), StringsJa).endsWith("目安"))
    }

    // ─── spoken summaries ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the bar chart speaks §4's sentence verbatim`() {
        assertEquals(
            "週ごとの回数、直近十二週、いちばん多い週は 六回、ならして 三.四回、今週は 二回",
            chartSemantics(ChartKind.WEEKLY_SESSIONS, ChartRange.TWELVE, listOf(6.0, 4.0, 3.0, 2.0, 2.0, 2.0), StringsJa),
        )
    }

    @Test
    fun `the other two charts reuse the same skeleton rather than inventing sentences`() {
        assertEquals(
            "活動時間、直近二十六週、合計 二千四百分 ・ ならして 二百分/週",
            chartSemantics(ChartKind.ACTIVE_MINUTES, ChartRange.TWENTY_SIX, List(12) { 200.0 }, StringsJa),
        )
        assertEquals(
            "積み上げ、直近一年、日ごとの積み上げと 七日平均 ・ 目安",
            chartSemantics(ChartKind.VOLUME, ChartRange.YEAR, listOf(1.0), StringsJa),
        )
    }

    @Test
    fun `an empty series says what chart it is and stops`() {
        // The Empty state has its own composable; this is a defence, not a surface. It must not read
        // as いちばん多い週 〇回, which would be a claim about a user with no history.
        assertEquals("週ごとの回数、直近十二週", chartSemantics(ChartKind.WEEKLY_SESSIONS, ChartRange.TWELVE, emptyList(), StringsJa))
    }

    @Test
    fun `the bar chart's footer names the first week and the last`() {
        val labels = chartAxisLabels(listOf(LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 6)), StringsJa)
        assertEquals("三月三十日" to "今週", labels)
        assertNull(chartAxisLabels(emptyList(), StringsJa))
    }

    // ─── English ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the captions are sentences in English rather than transliterated fragments`() {
        // ならして circumfixes its counter (ならして 三.四回) and English has none, which is why the
        // average is a whole member on the table rather than a prefix glued to a formatted number.
        assertEquals(
            "Busiest week 6 · averaging 3.4",
            chartCaption(ChartKind.WEEKLY_SESSIONS, listOf(6.0, 4.0, 3.0, 2.0, 2.0, 2.0), StringsEn),
        )
        assertEquals(
            "2400m total · averaging 200m a week",
            chartCaption(ChartKind.ACTIVE_MINUTES, List(12) { 200.0 }, StringsEn),
        )
        assertEquals(
            "Daily build-up and a 7-day mean · approx.",
            chartCaption(ChartKind.VOLUME, listOf(120.0, 80.0), StringsEn),
        )
    }

    @Test
    fun `the suppression sentence carries the threshold, not a spelled-out number`() {
        assertEquals("Appears once 28 days have built up", chartSuppressionCopy(27, StringsEn))
        assertNull(chartSuppressionCopy(28, StringsEn))
    }
}
