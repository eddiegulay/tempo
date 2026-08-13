package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.gym.data.loadScaleOf
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The month ink-grid: its layout, its hit test, and how dark a day is allowed to be.
 *
 * The grid is one `Canvas`, so nothing the composer would normally do for it happens — the cells, the
 * row count and the tap resolution are all arithmetic, and arithmetic inside a `DrawScope` is
 * arithmetic that never gets tested. February is the reason to care: a hardcoded five-row grid is
 * right eleven months of the year.
 *
 * The other half of this file is `DECISIONS.md` §Q11, which is a decision about *restraint*. Below
 * eight non-zero days the grid stops ranking and draws every trained day the same. That is not a
 * fallback threshold — it is declining to rank a sample of two, because ranking two days paints one
 * of them at 0.15 and the other at 0.90 and tells a user who has trained twice that they had a light
 * week and a heavy one.
 */
class InkDensityTest {

    // ─── layout (§5.1) ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a month that starts on Sunday and holds twenty-eight days fits in four rows`() {
        // February 2026 begins on a Sunday. A fixed six-row grid leaves a blank strip under most
        // months and makes the page jump the month it is finally used; ceil() is four lines.
        val grid = monthCells(YearMonth.of(2026, 2))
        assertEquals(0, grid.leadingBlank)
        assertEquals(28, grid.length)
        assertEquals(4, grid.rows)
    }

    @Test
    fun `an ordinary month takes five rows and a long one takes six`() {
        val june = monthCells(YearMonth.of(2026, 6))   // Monday 1st, 30 days → 31 cells
        assertEquals(1, june.leadingBlank)
        assertEquals(5, june.rows)

        val august = monthCells(YearMonth.of(2026, 8)) // Saturday 1st, 31 days → 37 cells
        assertEquals(6, august.leadingBlank)
        assertEquals(6, august.rows)
        assertEquals(42, august.cells)
    }

    @Test
    fun `leap February gains its twenty-ninth day and the row it needs`() {
        val leap = monthCells(YearMonth.of(2024, 2))   // Thursday 1st, 29 days → 33 cells
        assertEquals(29, leap.length)
        assertEquals(4, leap.leadingBlank)
        assertEquals(5, leap.rows)

        // The same month in a common year is one day and, here, one row shorter.
        assertEquals(28, monthCells(YearMonth.of(2023, 2)).length)
    }

    @Test
    fun `the week starts on Sunday, matching the header letters above the canvas`() {
        // JapaneseDate.DOW is 日 月 火 水 木 金 土 (`JapaneseDate.kt:18`). A grid that started on
        // Monday under that header would shift every dot one column and look perfectly fine.
        // Note the deliberate divergence from weekStartOf, which is ISO Monday and stays that way —
        // the weekly charts are compared against a seven-day monotony window and must be ISO.
        assertEquals(0, monthCells(YearMonth.of(2026, 2)).leadingBlank)  // Sunday → no blanks
        assertEquals(6, monthCells(YearMonth.of(2026, 8)).leadingBlank)  // Saturday → six
    }

    // ─── the hit test ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a tap resolves to the day under it`() {
        val grid = monthCells(YearMonth.of(2026, 6))   // one leading blank
        assertEquals(LocalDate.of(2026, 6, 1), dayAt(x = 30f, y = 10f, cellPx = 20f, grid = grid))
        assertEquals(LocalDate.of(2026, 6, 7), dayAt(x = 10f, y = 30f, cellPx = 20f, grid = grid))
        assertEquals(LocalDate.of(2026, 6, 30), dayAt(x = 50f, y = 90f, cellPx = 20f, grid = grid))
    }

    @Test
    fun `a blank cell and a miss resolve to nothing`() {
        val grid = monthCells(YearMonth.of(2026, 6))
        assertNull(dayAt(x = 10f, y = 10f, cellPx = 20f, grid = grid))    // before the 1st
        assertNull(dayAt(x = 70f, y = 90f, cellPx = 20f, grid = grid))    // after the 30th
        assertNull(dayAt(x = 150f, y = 10f, cellPx = 20f, grid = grid))   // past the seventh column
        assertNull(dayAt(x = 30f, y = 500f, cellPx = 20f, grid = grid))   // below the last row
        assertNull(dayAt(x = -1f, y = 10f, cellPx = 20f, grid = grid))
    }

    @Test
    fun `a canvas measured before layout resolves nothing rather than everything`() {
        // Without the guard, x / 0 sends every tap to the same day and the grid appears to work.
        val grid = monthCells(YearMonth.of(2026, 6))
        assertNull(dayAt(x = 30f, y = 10f, cellPx = 0f, grid = grid))
        assertNull(dayAt(x = 30f, y = 10f, cellPx = Float.NaN, grid = grid))
    }

    @Test
    fun `a day after today still resolves, because a tap on it is still a tap`() {
        // The grid draws nothing there — an unlived day is not an unfilled one (§4 edge case 3) — but
        // swallowing the tap would make the corner of the current month mysteriously dead. What today
        // means is the caller's business, and this function is not given it.
        val grid = monthCells(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 29), dayAt(x = 30f, y = 90f, cellPx = 20f, grid = grid))
    }

    // ─── ink levels (§5.1, `DECISIONS.md` §Q11) ─────────────────────────────────────────────────

    private val ranked = loadScaleOf(listOf(60L, 70L, 80L, 90L, 100L, 110L, 120L, 130L).map { it * 60_000L })

    @Test
    fun `a rest day is its own level and draws nothing`() {
        // Five values, not four. bucketOf returns 0..3 and collapses "no session" into "below the
        // 40th percentile", because its job is bucketing a load rather than deciding a day exists.
        assertEquals(INK_LEVEL_NONE, inkLevel(0L, ranked))
        assertEquals(INK_LEVEL_NONE, inkLevel(-1L, ranked))
    }

    @Test
    fun `with eight days on file the grid ranks them across all four levels`() {
        assertEquals(8, ranked.nonZeroDays)
        assertEquals(1, inkLevel(60L * 60_000L, ranked))    // below p40
        assertEquals(2, inkLevel(90L * 60_000L, ranked))    // p40
        assertEquals(3, inkLevel(110L * 60_000L, ranked))   // p70
        assertEquals(4, inkLevel(130L * 60_000L, ranked))   // p90
    }

    @Test
    fun `below eight non-zero days every trained day is one mid level`() {
        // §Q11, and it is the whole reason `usedFallback` was kept rather than three invented
        // millisecond cutoffs. Ranking two days says only that one was longer than the other, and the
        // grid would then report a light week and a heavy week about a user who has trained twice.
        val thin = loadScaleOf(listOf(10L * 60_000L, 90L * 60_000L))
        assertEquals(true, thin.usedFallback)
        assertEquals(INK_LEVEL_UNRANKED, inkLevel(10L * 60_000L, thin))
        assertEquals(INK_LEVEL_UNRANKED, inkLevel(90L * 60_000L, thin))
        // Even an absurd day does not get the darkest ink. There is nothing to be darkest against.
        assertEquals(INK_LEVEL_UNRANKED, inkLevel(600L * 60_000L, thin))
        // A rest day is still a rest day: this suspends ranking, not the grid.
        assertEquals(INK_LEVEL_NONE, inkLevel(0L, thin))
    }

    @Test
    fun `percentile ranking resumes at exactly eight`() {
        val seven = loadScaleOf((1..7).map { it * 10L * 60_000L })
        val eight = loadScaleOf((1..8).map { it * 10L * 60_000L })
        assertEquals(true, seven.usedFallback)
        assertEquals(false, eight.usedFallback)
        assertEquals(INK_LEVEL_UNRANKED, inkLevel(70L * 60_000L, seven))
        assertEquals(INK_LEVEL_MAX, inkLevel(80L * 60_000L, eight))
    }

    @Test
    fun `an empty history ranks nothing rather than painting everything`() {
        val none = loadScaleOf(emptyList())
        assertEquals(true, none.usedFallback)
        assertEquals(INK_LEVEL_NONE, inkLevel(0L, none))
        assertEquals(INK_LEVEL_UNRANKED, inkLevel(40L * 60_000L, none))
    }

    @Test
    fun `loadScaleFrom is a shape, not a second fold`() {
        // Two folds over the same days is how one screen ends up ranking against a different scale
        // than the one beside it. loadScaleOf is the authority; this only unpacks the map.
        val days = (1..8).map { day(activeMs = it * 10L * 60_000L) }
        assertEquals(loadScaleOf(days.map { it.activeMs }), loadScaleFrom(days))
    }

    // ─── what the grid says ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the caption counts the days you trained`() {
        assertEquals("三日 ・ 六月", monthCaption(YearMonth.of(2026, 6), trainedMonth(), StringsJa))
    }

    @Test
    fun `a month with nothing in it is not the empty state`() {
        // §4 says so outright: the grid still renders, the pager still works, and the page says
        // 記録はありません to nobody. A month you did not train is a fact about June; an empty store is
        // a fact about the app, and the two must never share a composable.
        assertEquals("この月は 〇日", monthCaption(YearMonth.of(2026, 6), emptyMap(), StringsJa))
        assertEquals("六月、この月は 〇日", gridSemantics(YearMonth.of(2026, 6), emptyMap(), StringsJa))
    }

    @Test
    fun `the grid speaks one summary rather than forty-two dots`() {
        // §4's accessibility sentence verbatim. Per-day access is HISTORY's job — a real list with
        // real nodes. This is the deliberate answer to "how does a blind user read a heatmap".
        assertEquals(
            "六月、三日 鍛錬しました、いちばん多かったのは 六月十七日",
            gridSemantics(YearMonth.of(2026, 6), trainedMonth(), StringsJa),
        )
    }

    @Test
    fun `a session with no duration is counted exactly as the grid inks it`() {
        // The caption and the picture must agree. `monthLoad`'s query groups every finished session,
        // including one whose SUM(active_ms) is zero — a session started and finished instantly — so
        // the map really can hold a day with `sessions = 1, activeMs = 0`. The draw loop asks
        // `inkLevel(activeMs, scale)`, which returns INK_LEVEL_NONE for it and paints nothing; a
        // caption counting `sessions > 0` therefore read 十二日 over eleven visible dots, and
        // `gridSemantics` said the same to the one user who cannot check it against the picture.
        val zeroDuration = mapOf(
            LocalDate.of(2026, 6, 4) to day(20L * 60_000L),
            LocalDate.of(2026, 6, 5) to day(activeMs = 0L, sessions = 1),
        )

        assertEquals(INK_LEVEL_NONE, inkLevel(0L, loadScaleOf(listOf(20L * 60_000L))))
        assertEquals("一日 ・ 六月", monthCaption(YearMonth.of(2026, 6), zeroDuration, StringsJa))
        assertEquals(
            "六月、一日 鍛錬しました、いちばん多かったのは 六月四日",
            gridSemantics(YearMonth.of(2026, 6), zeroDuration, StringsJa),
        )
    }

    @Test
    fun `a month whose only session had no duration is not a month you trained`() {
        // The other end of the same predicate: nothing is inked, so the caption must not claim a day.
        val onlyZero = mapOf(LocalDate.of(2026, 6, 5) to day(activeMs = 0L, sessions = 1))

        assertEquals("この月は 〇日", monthCaption(YearMonth.of(2026, 6), onlyZero, StringsJa))
        assertEquals("六月、この月は 〇日", gridSemantics(YearMonth.of(2026, 6), onlyZero, StringsJa))
    }

    @Test
    fun `the caption names the month and never counts months`() {
        // `fmt.monthName`, not `fmt.months`. Japanese spells both 六月 so the mistake is invisible
        // there; in English the wrong one renders "6 months" under a calendar of June.
        assertEquals("3 days · June", monthCaption(YearMonth.of(2026, 6), trainedMonth(), StringsEn))
        assertEquals(
            "June, Trained 3 days, Busiest day 17 June",
            gridSemantics(YearMonth.of(2026, 6), trainedMonth(), StringsEn),
        )
        assertEquals("0 days this month", monthCaption(YearMonth.of(2026, 6), emptyMap(), StringsEn))
    }

    @Test
    fun `a tie for the busiest day goes to the earlier one`() {
        // The same rule isBetter applies to every record in this feature — you set it the first time
        // you hit it — so the sentence does not change under a re-read.
        val tied = mapOf(
            LocalDate.of(2026, 6, 4) to day(20L * 60_000L),
            LocalDate.of(2026, 6, 9) to day(20L * 60_000L),
        )
        assertEquals(
            "六月、二日 鍛錬しました、いちばん多かったのは 六月四日",
            gridSemantics(YearMonth.of(2026, 6), tied, StringsJa),
        )
    }
}

private fun day(activeMs: Long, sessions: Int = 1) =
    DayLoad(activeMs = activeMs, sessions = sessions, bucket = 0)

private fun trainedMonth(): Map<LocalDate, DayLoad> = mapOf(
    LocalDate.of(2026, 6, 4) to day(20L * 60_000L),
    LocalDate.of(2026, 6, 9) to day(30L * 60_000L),
    LocalDate.of(2026, 6, 17) to day(45L * 60_000L),
)
