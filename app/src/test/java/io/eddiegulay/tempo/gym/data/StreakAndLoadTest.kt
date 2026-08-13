package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.DailyLoad
import io.eddiegulay.tempo.gym.WeekPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The two folds `02-data.md` §D deliberately keeps out of SQL, at the boundaries where they are wrong
 * in ways nobody would notice.
 *
 * The streak is stateful across the scan — a forgiveness budget that refreshes on a month boundary,
 * over a plan that is date-versioned — and it is the number a user will check every day, so an
 * off-by-one is a promise broken daily. Monotony has no SQL form at all on this platform (no
 * `stdev()`, no `sqrt()`, no custom functions, and a seven-row spine `GROUP BY` cannot produce), and
 * its whole value is knowing when to return **no answer** rather than a plausible one.
 */
class StreakAndLoadTest {

    private val today = LocalDate.of(2026, 6, 17) // a Wednesday

    // ── The streak fold (§D.1, §A.8) ────────────────────────────────────────────────────────────

    @Test
    fun `with no plan, a rest day after a training day extends the streak`() {
        // §A.8's fallback in one sentence: honoured if you trained, or if you trained the day before.
        // One earned rest day per training day, which is design §5.2's promise with no plan model.
        val trained = setOf(today, today.minusDays(2), today.minusDays(4))
        val streak = currentStreak(today, trained, plans = emptyList())
        assertEquals(5, streak.days)
        assertEquals(0, streak.forgivenThisMonth)
    }

    @Test
    fun `with no plan, two untrained days in a row end it`() {
        // The other half of the same rule, and the reason the fallback needs no hidden state.
        val trained = setOf(today, today.minusDays(3))
        val streak = currentStreak(today, trained, plans = emptyList())
        // Yesterday was neither trained nor the day after a training day, so it is the miss.
        assertEquals(1, streak.days)
        assertEquals(today.minusDays(1), streak.endedOn)
    }

    @Test
    fun `an untrained today is not yet a miss`() {
        // Today never breaks a streak: the day is not over.
        val trained = setOf(today.minusDays(1), today.minusDays(2))
        val streak = currentStreak(today, trained, plans = emptyList())
        // Two training days survive, and today counts as the earned rest day after yesterday's
        // session rather than as a miss. It is the day *before* the first training day that ends it.
        assertEquals(3, streak.days)
        assertEquals(today.minusDays(3), streak.endedOn)
    }

    @Test
    fun `a user who has never trained has no streak, not a four-hundred-day one`() {
        // The §D.1 snippet as literally written answers 400 here, because it writes "honoured" into
        // the variable that means "planned". Zero renders 連続は とぎれています, and zero is not a
        // small streak.
        assertEquals(0, currentStreak(today, trained = emptySet(), plans = emptyList()).days)
    }

    @Test
    fun `an unplanned weekday extends the streak without being trained`() {
        // Under an explicit plan, a day nothing was planned for is honoured by definition.
        val plan = PlanWindow(today.minusMonths(6), daysMask = MON_WED_FRI, forgivenessPerMonth = 0)
        val trained = setOf(today, today.minusDays(2)) // Wednesday and Monday
        val streak = currentStreak(today, trained, plans = listOf(plan))
        // Wed, Tue (unplanned), Mon — and then Sunday, Saturday are unplanned too, so the walk runs on
        // until the first missed Friday.
        assertEquals(5, streak.days)
        assertEquals(today.minusDays(5), streak.endedOn) // the Friday before
    }

    @Test
    fun `forgiveness absorbs a missed planned day and is reported`() {
        val plan = PlanWindow(today.minusMonths(6), daysMask = MON_WED_FRI, forgivenessPerMonth = 2)
        val trained = setOf(today, today.minusDays(2)) // Wednesday and Monday; the Friday is missed
        val streak = currentStreak(today, trained, plans = listOf(plan))
        // The two missed Fridays and Wednesdays before them are absorbed, and the third miss ends it.
        assertEquals(2, streak.forgivenThisMonth)
        assertEquals(9, streak.days)
        assertEquals(today.minusDays(9), streak.endedOn)
    }

    @Test
    fun `the budget is exhausted, not accumulated`() {
        val plan = PlanWindow(today.minusMonths(6), daysMask = EVERY_DAY, forgivenessPerMonth = 2)
        val trained = setOf(today)
        val streak = currentStreak(today, trained, plans = listOf(plan))
        // Today, then two forgiven days, then the third miss ends it.
        assertEquals(3, streak.days)
        assertEquals(2, streak.forgivenThisMonth)
        assertEquals(today.minusDays(3), streak.endedOn)
    }

    @Test
    fun `crossing into an earlier month refreshes the budget`() {
        // "Two per month" is what a user plainly means, and it is exactly what makes this fold
        // stateful across the scan — and therefore a recursive CTE in SQL.
        val firstOfMonth = LocalDate.of(2026, 6, 1)
        val plan = PlanWindow(LocalDate.of(2025, 1, 1), daysMask = EVERY_DAY, forgivenessPerMonth = 2)
        val streak = currentStreak(firstOfMonth, trained = setOf(firstOfMonth), plans = listOf(plan))
        // 1 June trained, then May's own budget absorbs two more days before the streak ends.
        assertEquals(3, streak.days)
    }

    @Test
    fun `the later plan window is the one in force`() {
        // Date-versioned, so changing the plan cannot retroactively rewrite last month's streak.
        val plans = listOf(
            PlanWindow(LocalDate.of(2025, 1, 1), MON_WED_FRI, 0),
            PlanWindow(LocalDate.of(2026, 6, 1), EVERY_DAY, 2),
        )
        assertEquals(EVERY_DAY, plans.inForce(today)?.daysMask)
        assertEquals(MON_WED_FRI, plans.inForce(LocalDate.of(2026, 5, 30))?.daysMask)
        assertNull(plans.inForce(LocalDate.of(2024, 1, 1)))
    }

    // ── Monotony and ACWR (§D.6) ────────────────────────────────────────────────────────────────

    @Test
    fun `a week holding an unrated session has no monotony`() {
        // The rating is skippable, so the window's load is partly unknown and any number would be a
        // guess. `COALESCE(rating_cr10, 7)` would be inventing the user's perceived exertion.
        val window = List(7) { day(it, load = 100.0) }.toMutableList()
        window[3] = window[3].copy(unrated = 1)
        assertNull(monotony(window))
    }

    @Test
    fun `seven identical days have no monotony either`() {
        // Zero standard deviation is undefined, and the honest answer is no answer rather than
        // infinity dressed up as a ratio.
        assertNull(monotony(List(7) { day(it, load = 100.0) }))
    }

    @Test
    fun `rest days count as zeros, and the zeros are the point`() {
        // They are what makes an easy day easy. Population SD, matching Foster's formulation.
        val window = List(7) { if (it < 3) day(it, 100.0) else day(it, 0.0) }
        val m = monotony(window)
        assertNotNull(m)
        // mean 300/7, sd = sqrt(mean((x-mean)^2)) over 3 hundreds and 4 zeros.
        assertEquals(0.866, m!!, 0.001)
    }

    @Test
    fun `monotony refuses a window that is not seven days`() {
        runCatching { monotony(List(6) { day(it, 100.0) }) }
            .onSuccess { throw AssertionError("a six-day window is not a seven-day window") }
    }

    @Test
    fun `ACWR is suppressed below twenty-eight days`() {
        // It is a governor and never a display (§7.4), and a ratio computed from a fortnight is noise
        // wearing a number's clothes.
        assertNull(acwr(List(14) { day(it, 100.0) }))
        assertNotNull(acwr(List(28) { day(it, 100.0) }))
    }

    @Test
    fun `ACWR is one when the recent week matches the month`() {
        assertEquals(1.0, acwr(List(28) { day(it, 50.0) })!!, 1e-9)
    }

    // ── Zero-filling (§D.4, §D.6) ───────────────────────────────────────────────────────────────

    @Test
    fun `a fortnight off does not silently close up`() {
        // Weeks with no sessions do not appear in the query result. Drawn unfilled, twelve weeks with
        // a gap would read as twelve consecutive weeks of training.
        val filled = zeroFilledWeeks(today, weeks = 4, byWeek = emptyMap())
        assertEquals(4, filled.size)
        assertTrue(filled.all { it.sessions == 0 })
        assertEquals(weekStartOf(today), filled.last().weekStart)
    }

    @Test
    fun `zero-filled weeks keep the weeks that do exist`() {
        val monday = weekStartOf(today)
        val point = WeekPoint(monday, sessions = 3, activeMinutes = 60, completeSessions = 2)
        val filled = zeroFilledWeeks(today, weeks = 3, byWeek = mapOf(monday to point))
        assertEquals(listOf(0, 0, 3), filled.map { it.sessions })
    }

    @Test
    fun `a rest day appears in the load spine as an explicit zero`() {
        val from = today.minusDays(6)
        val spine = zeroFilledLoad(from, today, byDate = emptyMap())
        assertEquals(7, spine.size)
        assertTrue(spine.all { it.load == 0.0 && it.sessions == 0 })
    }

    // ── Ink levels (§D.3) ───────────────────────────────────────────────────────────────────────

    @Test
    fun `thresholds rank the user against their own days`() {
        // Relative, not absolute: fixed cutoffs leave a twenty-minute-session user permanently at
        // level one and a marathoner permanently at level four.
        val scale = loadScaleOf((1..10).map { it * 60_000L })
        assertEquals(4 * 60_000L, scale.p40Ms)
        assertEquals(7 * 60_000L, scale.p70Ms)
        assertEquals(9 * 60_000L, scale.p90Ms)
        assertEquals(10, scale.nonZeroDays)
        assertTrue(!scale.usedFallback)
    }

    @Test
    fun `a thin sample says so rather than pretending to a percentile`() {
        val scale = loadScaleOf(listOf(60_000L, 120_000L))
        assertTrue(scale.usedFallback)
        assertEquals(2, scale.nonZeroDays)
    }

    @Test
    fun `a day with no session is never given an ink level`() {
        // Days absent from a month's map are rest days and render blank: a day with no session is not
        // a day with a small one.
        val scale = loadScaleOf((1..10).map { it * 60_000L })
        assertEquals(0, bucketOf(0L, scale))
        assertEquals(0, bucketOf(60_000L, scale))
        assertEquals(1, bucketOf(5 * 60_000L, scale))
        assertEquals(2, bucketOf(8 * 60_000L, scale))
        assertEquals(3, bucketOf(20 * 60_000L, scale))
    }

    @Test
    fun `an empty window paints nothing rather than painting everything darkest`() {
        // With no trailing history every threshold is zero, and an unguarded comparison would put
        // every day at the darkest ink.
        assertEquals(0, bucketOf(600_000L, loadScaleOf(emptyList())))
    }

    private fun day(index: Int, load: Double) =
        DailyLoad(date = today.minusDays((6 - index).toLong()), load = load, sessions = 1, unrated = 0)

    private companion object {
        /** bit 0 = Monday … bit 6 = Sunday (§A.8). */
        const val MON_WED_FRI = 0b0010101
        const val EVERY_DAY = 0b1111111
    }
}
