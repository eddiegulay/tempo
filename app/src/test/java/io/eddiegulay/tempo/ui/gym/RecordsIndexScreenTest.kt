package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.DayLoad
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.Streak
import io.eddiegulay.tempo.gym.WeekPoint
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * `GYM.RECORDS.INDEX`'s decisions, as values.
 *
 * Everything here is the half of the page a JVM can see: which body state a set of reads produces,
 * which words a tile and a 最近 row carry, and where the pager's arrows stop. The composable half — the
 * canvas, the semantics nodes, the fault strip's action — is pinned by
 * [RecordsIndexScreenStructureTest], which reads the source, because this module's unit-test classpath
 * is `junit` and nothing else (`00-plan.md` §2 forbids adding Robolectric or `compose-ui-test`).
 *
 * The grid's own arithmetic is **not** tested here. `monthCells`, `dayAt`, `inkLevel`, `gridSemantics`
 * and `monthCaption` are `InkDensity.kt`'s and are covered by `InkDensityTest`; re-asserting them from
 * the page would be a second opinion about the same function, which is how two tests come to disagree.
 */
class RecordsIndexScreenTest {

    // ─── which body is on screen ────────────────────────────────────────────────────────────────

    private val ready = Loadable.Ready(mapOf(LocalDate.of(2026, 6, 17) to DayLoad(1_200_000L, 1, 2)))
    private val noDays = Loadable.Ready(emptyMap<LocalDate, DayLoad>())
    private val streakReady = Loadable.Ready(Streak(4, 1, null))

    @Test
    fun `an unreadable store is never the empty state`() {
        // The whole reason this feature routes reads through `Loadable`. `history` reports
        // StoreCorrupt on a quarantined file; if that ever collapsed into an empty list the page would
        // tell a user with a year of training that they have never trained.
        val body = recordsBodyState(
            recent = Loadable.Failed(GymFault.StoreCorrupt),
            currentMonth = noDays,
            displayedMonth = noDays,
            streak = streakReady,
        )

        assertEquals(RecordsBody.Failed(GymFault.StoreCorrupt), body)
    }

    @Test
    fun `a fault is not masked by a sibling read that has not answered`() {
        // Failed is checked before Loading. The other order shows 読み込み中 forever on a store where
        // one read fails and another never completes.
        val body = recordsBodyState(
            recent = Loadable.Loading,
            currentMonth = Loadable.Failed(GymFault.StoreFull),
            displayedMonth = Loadable.Loading,
            streak = Loadable.Loading,
        )

        assertEquals(RecordsBody.Failed(GymFault.StoreFull), body)
    }

    @Test
    fun `a failed month is a page fault even when every other read succeeded`() {
        val body = recordsBodyState(
            recent = Loadable.Ready(listOf(session())),
            currentMonth = ready,
            displayedMonth = Loadable.Failed(GymFault.StoreCorrupt),
            streak = streakReady,
        )

        assertEquals(RecordsBody.Failed(GymFault.StoreCorrupt), body)
    }

    @Test
    fun `nothing is claimed until the reads answer`() {
        val body = recordsBodyState(Loadable.Loading, noDays, noDays, streakReady)

        assertEquals(RecordsBody.Loading, body)
    }

    @Test
    fun `paging to a month that has not loaded does not blank the page`() {
        // The displayed month is checked for faults and not for loading: an arrow press must not take
        // the streak and the tiles off screen. The grid slot carries its own 読み込み中.
        val body = recordsBodyState(
            recent = Loadable.Ready(listOf(session())),
            currentMonth = ready,
            displayedMonth = Loadable.Loading,
            streak = streakReady,
        )

        assertEquals(RecordsBody.Ready, body)
    }

    @Test
    fun `zero sessions ever is the empty state`() {
        val body = recordsBodyState(Loadable.Ready(emptyList()), noDays, noDays, streakReady)

        assertEquals(RecordsBody.Empty, body)
    }

    @Test
    fun `a month with no sessions inside a history that has some is not empty`() {
        // §4: "Ready, empty month (history exists) — the grid renders with no dots and the caption
        // reads この月は 〇日. **This is not the empty state**; the pager must remain usable."
        val body = recordsBodyState(
            recent = Loadable.Ready(listOf(session())),
            currentMonth = noDays,
            displayedMonth = noDays,
            streak = streakReady,
        )

        assertEquals(RecordsBody.Ready, body)
    }

    // ─── the tiles ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tiles are labelled 今月 活動時間 これまで`() {
        val tiles = recordsTiles(
            monthSessions = 12,
            monthActiveMs = 240 * 60_000L,
            lifetimeSessions = 86,
            strings = StringsJa,
        )

        assertEquals(listOf("今月", "活動時間", "これまで"), tiles.map { it.label })
        assertEquals(listOf("十二回", "二百四十分", "八十六回"), tiles.map { it.value })
    }

    @Test
    fun `a tile reads label-first`() {
        val tiles = recordsTiles(12, 240 * 60_000L, 86, StringsJa)

        assertEquals("今月、十二回", recordsTileSemantics(tiles.first(), StringsJa))
    }

    @Test
    fun `an unavailable lifetime total omits its tile rather than printing a wrong one`() {
        // `summary()` exists now (`DECISIONS.md` §Q22) and null means what a `Loadable` means: the read
        // has not answered, or it failed. An absent tile says nothing; 〇回 over a quarantined store
        // would be a claim, on the page whose whole subject is honest records.
        val tiles = recordsTiles(12, 240 * 60_000L, lifetimeSessions = null, strings = StringsJa)

        assertEquals(listOf("今月", "活動時間"), tiles.map { it.label })
    }

    @Test
    fun `minutes truncate`() {
        // 59 seconds is not a minute, exactly as `historySubtitle` counts them.
        val tiles = recordsTiles(1, 119_000L, null, StringsJa)

        assertEquals("一分", tiles[1].value)
    }

    @Test
    fun `a month with nothing in it still counts, in zeroes`() {
        // The tiles are only ever drawn over a history that exists, so 〇回 here is a fact about June
        // and not a wall of zeroes over a first-run page — that page is `RecordsBody.Empty`.
        val tiles = recordsTiles(0, 0L, null, StringsJa)

        assertEquals(listOf("〇回", "〇分"), tiles.map { it.value })
    }

    // ─── 最近 rows ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a recent row leads with the month, unlike a history row`() {
        val copy = recentRowCopy(session(), StringsJa)

        assertEquals("六月十七日", copy.date)
        assertEquals("七分間", copy.name)
        assertEquals("六分十四秒", copy.duration)
    }

    @Test
    fun `an unrated session simply ends after the duration`() {
        // §4 edge case 8, and the reason `rating` is nullable rather than defaulted to ちょうど.
        val copy = recentRowCopy(session(rating = null), StringsJa)

        assertNull(copy.rating)
        assertEquals("六月十七日、七分間、六分十四秒", copy.semantics)
    }

    @Test
    fun `a partial session says so here too`() {
        // `00-plan.md` §4.1 rule 2: the same treatment wherever it appears. Without the chip this row
        // would present an abandoned circuit exactly as it presents a finished one.
        val copy = recentRowCopy(session(complete = false, stationsCompleted = 2, stationsPlanned = 3), StringsJa)

        assertEquals("途中まで ・ 三種目中 二", copy.partial)
        assertTrue(copy.semantics.endsWith("途中まで ・ 三種目中 二"))
    }

    @Test
    fun `a finished session carries no partial chip`() {
        assertNull(recentRowCopy(session(), StringsJa).partial)
    }

    // ─── the pager ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the pager stops at the first session's month`() {
        val june = YearMonth.of(2026, 6)

        assertFalse(canPageBack(june, earliest = june))
        assertTrue(canPageBack(june, earliest = YearMonth.of(2026, 5)))
    }

    @Test
    fun `an unknown earliest month leaves the arrow usable`() {
        // `populatedMonths()` has not answered, or failed. Disabling on an unread bound hides months
        // that exist; paging into an empty one is harmless and says この月は 〇日.
        assertTrue(canPageBack(YearMonth.of(2026, 6), earliest = null))
    }

    @Test
    fun `the pager never walks into an unlived month`() {
        val june = YearMonth.of(2026, 6)

        assertFalse(canPageForward(june, current = june))
        assertTrue(canPageForward(YearMonth.of(2026, 5), current = june))
    }

    @Test
    fun `the pager labels its month and the subtitle carries the year`() {
        val june = YearMonth.of(2026, 6)

        assertEquals("六月", monthPagerLabel(june, current = june, strings = StringsJa))
        assertEquals("令和八年 ・ 六月", recordsSubtitle(LocalDateTime.of(2026, 6, 17, 9, 0), StringsJa))
    }

    @Test
    fun `a month outside the current year says which year it is`() {
        // The defect this closes: the bare 六月 was justified by "the year lives in the page subtitle",
        // and the subtitle is `recordsSubtitle(now)` — the *clock*, not the displayed month. Paging
        // back thirteen months therefore drew 六月 under 令和八年 ・ 六月 with another year's ink in the
        // grid between them: two different Junes rendered identically.
        assertEquals(
            "令和七年 ・ 六月",
            monthPagerLabel(YearMonth.of(2025, 6), current = YearMonth.of(2026, 6), strings = StringsJa),
        )
    }

    @Test
    fun `the long form is the subtitle's own string and not a second formatter`() {
        // `DECISIONS.md` §Q7's rule: one formatter is authoritative and the other delegates, so the
        // pager and the subtitle cannot spell a year two ways.
        assertEquals(
            recordsSubtitle(LocalDateTime.of(2025, 6, 1, 0, 0), StringsJa),
            monthPagerLabel(YearMonth.of(2025, 6), current = YearMonth.of(2026, 6), strings = StringsJa),
        )
    }

    @Test
    fun `every month of the displayed year is named, never counted`() {
        // The mock is unchanged for the user who never leaves this year, which is the common case and
        // the one §4 drew: 「‹ 六月 ›」, with the year two lines above it in the subtitle.
        //
        // The label is `fmt.monthName`, and the reason it is asserted in both languages is that
        // `fmt.months` — a *count* of months — spells 六月 identically in Japanese. Only an English
        // build can tell 「June」 from 「6 months」, so only an English assertion can catch the swap.
        val current = YearMonth.of(2026, 6)
        (1..12).forEach { m ->
            assertEquals(
                StringsJa.fmt.monthName(m),
                monthPagerLabel(YearMonth.of(2026, m), current = current, strings = StringsJa),
            )
            assertEquals(
                StringsEn.fmt.monthName(m),
                monthPagerLabel(YearMonth.of(2026, m), current = current, strings = StringsEn),
            )
        }
        assertEquals("June", monthPagerLabel(YearMonth.of(2026, 6), current, StringsEn))
        assertEquals("Reiwa 7 · June", monthPagerLabel(YearMonth.of(2025, 6), current, StringsEn))
    }

    // ─── the weekday header ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the weekday letters are Sunday-first and are each their own day's initial`() {
        // The header used to be seven Japanese literals on this page, pinned against
        // `JapaneseDate.dayOfWeek` by stripping 「曜日」 off its output — a test reaching into the
        // spelling of a formatter it does not own, and an assertion that could only ever be about
        // Japanese. The letters are `fmt.weekdayInitials()` now, so the property is restated per
        // language: **seven entries, Sunday first, each one the opening of that day's own name.**
        //
        // Sunday-first is what `monthCells` computes `leadingBlank` from (`dayOfWeek.value % 7`), in
        // both languages: a header that started on Monday would shift every dot one column and nothing
        // would look broken. `EnFormats` keeps the Japanese grid's week start deliberately (H-21).
        val sunday = LocalDate.of(2026, 6, 7)
        listOf<Strings>(StringsJa, StringsEn).forEach { strings ->
            val initials = strings.fmt.weekdayInitials()
            assertEquals(7, initials.size)
            (0..6).forEach { offset ->
                val day = sunday.plusDays(offset.toLong()).atStartOfDay()
                assertTrue(
                    "${initials[offset]} must open ${strings.fmt.dayOfWeek(day)}",
                    strings.fmt.dayOfWeek(day).startsWith(initials[offset]),
                )
            }
        }

        // The Japanese seven, transcribed, so the migration stayed behaviour-neutral for the language
        // that shipped them. English's S M T W T F S collides on two pairs and that is accepted: the
        // cell is 20.dp, which is one CJK glyph or about three Latin characters.
        assertEquals(listOf("日", "月", "火", "水", "木", "金", "土"), StringsJa.fmt.weekdayInitials())
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), StringsEn.fmt.weekdayInitials())
    }

    // ─── the sparkline ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the sparkline plots sessions per week, zeroes included`() {
        // A zero week is a measured zero and keeps its slot — `barGeometry` returns a zero-height rect
        // there and the canvas draws its 2.dp stub. Dropping it would close the gap up and make a
        // fortnight off read as a fortnight on.
        val weeks = listOf(
            WeekPoint(LocalDate.of(2026, 6, 1), sessions = 3, activeMinutes = 60, completeSessions = 3),
            WeekPoint(LocalDate.of(2026, 6, 8), sessions = 0, activeMinutes = 0, completeSessions = 0),
        )

        assertEquals(listOf(3, 0), sparklineSessions(weeks))
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────────────────────

    private fun session(
        rating: Rating? = Rating.HARD,
        complete: Boolean = true,
        stationsPlanned: Int = 12,
        stationsCompleted: Int = 12,
    ) = SessionSummary(
        sessionId = 1,
        routineId = "r_seven",
        routineVersionId = 1,
        routineName = "七分間",
        engine = Engine.INTERVAL_CIRCUIT,
        tier = null,
        startedAt = 0L,
        finishedAt = 1L,
        localDate = LocalDate.of(2026, 6, 17),
        activeMs = 374_000L,
        wallMs = 374_000L,
        complete = complete,
        reachedTimeCap = false,
        roundsPlanned = 1,
        roundsCompleted = 1,
        stationsPlanned = stationsPlanned,
        stationsCompleted = stationsCompleted,
        totalReps = 320,
        volumeUnits = 0.0,
        rating = rating,
        personalBest = false,
    )
}
