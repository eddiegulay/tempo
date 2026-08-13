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
 * `GYM.RECORDS.HISTORY`'s paging, and the one bug it exists to make impossible.
 *
 * This page pages *and* deletes. 長押し → 記録を削除 is one of two actions it offers, so a row
 * disappearing between two fetches is the normal case rather than a race. Under `OFFSET` that is
 * silent data loss: page two is "rows 30..59 of the current list", and a delete moves every row after
 * it one place up, so the row that had been at index 30 is never asked for again. The user can only
 * recover it by leaving the screen, and nothing anywhere reports that it happened.
 *
 * The keyset cursor cannot do that, because it names a row rather than a position. That claim is what
 * the middle section of this file tests — with a fake store, a real delete between two fetches, and
 * an `OFFSET` implementation sitting beside it losing the row so the difference is on the record
 * rather than in a comment.
 */
class HistoryPagingTest {

    // ─── grouping ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `rows group into months in the order they arrive`() {
        val groups = groupByMonth(
            listOf(
                row(id = 3, date = LocalDate.of(2026, 6, 17)),
                row(id = 2, date = LocalDate.of(2026, 6, 2)),
                row(id = 1, date = LocalDate.of(2026, 5, 30)),
            ),
            StringsJa,
        )
        assertEquals(listOf("六月", "五月"), groups.map { it.header })
        assertEquals(listOf("二回", "一回"), groups.map { it.countLabel })
        assertEquals(listOf(3L, 2L), groups[0].sessions.map { it.sessionId })
        assertEquals("month:2026-06", groups[0].key)
    }

    @Test
    fun `deleting the last session in a month removes that month's header`() {
        // §4 edge case 3. The grouping is derived from the whole list on every emission and never
        // patched incrementally — the emission nobody remembers to patch is the one that leaves an
        // empty 五月 heading above 四月's rows.
        val loaded = listOf(row(id = 2, date = LocalDate.of(2026, 6, 2)), row(id = 1, date = LocalDate.of(2026, 5, 30)))
        val afterDelete = loaded.filterNot { it.sessionId == 1L }
        assertEquals(listOf("六月"), groupByMonth(afterDelete, StringsJa).map { it.header })
        assertTrue(groupByMonth(emptyList(), StringsJa).isEmpty())
    }

    @Test
    fun `a session begun before midnight groups under the day it started`() {
        // §4 edge case 6, stated once for the whole feature: the grid, the streak and this grouping
        // all use local_date, which is bucketed in Kotlin in the device's own zone at write time.
        // Grouping on started_at instead would move a 23:40 session on the 31st into the next month.
        val newYearsEve = row(
            id = 9,
            date = LocalDate.of(2025, 12, 31),
            // 2026-01-01T01:00Z — already January in UTC. The local date is what decides.
            startedAt = 1_767_229_200_000L,
        )
        assertEquals(listOf("十二月"), groupByMonth(listOf(newYearsEve), StringsJa).map { it.header })
    }

    @Test
    fun `a month header is a month's name and not a count of months`() {
        // `fmt.monthName` against `fmt.months`. Both are 六月 in Japanese, so only an English build can
        // catch the substitution — and it renders "6 months" over June's rows when it happens.
        val groups = groupByMonth(listOf(row(id = 1, date = LocalDate.of(2026, 6, 17))), StringsEn)
        assertEquals(listOf("June"), groups.map { it.header })
        assertEquals(listOf("1 time"), groups.map { it.countLabel })
    }

    // ─── the cursor ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the cursor is the oldest row held, whatever order the list is in`() {
        val loaded = listOf(row(id = 5, startedAt = 500), row(id = 9, startedAt = 100), row(id = 7, startedAt = 300))
        assertEquals(HistoryCursor(startedAt = 100, sessionId = 9), nextCursor(loaded))
        assertNull(nextCursor(emptyList()))
    }

    @Test
    fun `a tie on started_at breaks by id, so neither of the pair is dropped`() {
        // §4 edge case 2. A restored backup can stamp two sessions the same millisecond, and a
        // single-column cursor loses whichever one the page boundary fell between — permanently.
        val tied = listOf(row(id = 8, startedAt = 400), row(id = 4, startedAt = 400))
        assertEquals(HistoryCursor(startedAt = 400, sessionId = 4), nextCursor(tied))

        val store = FakeHistory(listOf(row(id = 8, startedAt = 400), row(id = 4, startedAt = 400), row(id = 1, startedAt = 100)))
        val first = store.page(cursor = null, limit = 1)
        val second = store.page(cursor = nextCursor(first), limit = 2)
        assertEquals(listOf(8L), first.map { it.sessionId })
        assertEquals(listOf(4L, 1L), second.map { it.sessionId })
    }

    // ─── the whole point ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a row deleted between two fetches costs nothing, and nothing repeats`() {
        val store = FakeHistory((1..9).map { row(id = it.toLong(), startedAt = it * 100L) })
        val firstPage = store.page(cursor = null, limit = 3)          // ids 9, 8, 7
        var loaded = mergePage(emptyList(), firstPage)

        // The user long-presses a row that has not been fetched yet and deletes it.
        store.delete(5L)

        val secondPage = store.page(cursor = nextCursor(loaded), limit = 3)
        loaded = mergePage(loaded, secondPage)

        assertEquals(listOf(9L, 8L, 7L, 6L, 4L, 3L), loaded.map { it.sessionId })
        assertEquals(loaded.map { it.sessionId }.distinct().size, loaded.size)
    }

    @Test
    fun `deleting a row that was already on screen does not shift the next page`() {
        val store = FakeHistory((1..9).map { row(id = it.toLong(), startedAt = it * 100L) })
        val firstPage = store.page(cursor = null, limit = 3)          // 9, 8, 7
        val cursor = nextCursor(firstPage)                            // (700, 7)

        store.delete(8L)
        // The page removes the row the user deleted where they tapped; nothing here does it for them,
        // so an undelivered page can never make a visible row vanish.
        val loaded = mergePage(firstPage.filterNot { it.sessionId == 8L }, store.page(cursor, limit = 3))
        assertEquals(listOf(9L, 7L, 6L, 5L, 4L), loaded.map { it.sessionId })
    }

    @Test
    fun `an offset page would have skipped the row a keyset page keeps`() {
        // The failure being prevented, run rather than described. Same store, same delete as the test
        // above — the only difference is how page two is addressed.
        val store = FakeHistory((1..9).map { row(id = it.toLong(), startedAt = it * 100L) })
        val firstPage = store.offsetPage(offset = 0, limit = 3)       // ids 9, 8, 7
        store.delete(8L)
        val secondPage = store.offsetPage(offset = 3, limit = 3)

        // With id 8 gone every later row moved up one, so index 3 is now id 5 — and **id 6 is never
        // returned by either page**. The keyset run above fetched it as a matter of course.
        assertEquals(listOf(5L, 4L, 3L), secondPage.map { it.sessionId })
        assertFalse(6L in (firstPage + secondPage).map { it.sessionId })
    }

    // ─── merging ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a refetched page folds in without duplicating, keeping the fresher copy`() {
        // A retry after §4's Error (next page), or a re-emission after a delete, can hand back rows
        // already held. The incoming copy wins because personalBest is recomputed on read and demotes
        // an old chip once the record is beaten.
        val held = listOf(row(id = 3, startedAt = 300, personalBest = true), row(id = 2, startedAt = 200))
        val refetched = listOf(row(id = 3, startedAt = 300, personalBest = false), row(id = 1, startedAt = 100))
        val merged = mergePage(held, refetched)
        assertEquals(listOf(3L, 2L, 1L), merged.map { it.sessionId })
        assertFalse(merged.first().personalBest)
    }

    @Test
    fun `merging restores the total order the cursor assumes`() {
        val merged = mergePage(listOf(row(id = 1, startedAt = 100)), listOf(row(id = 9, startedAt = 900)))
        assertEquals(listOf(9L, 1L), merged.map { it.sessionId })
        assertEquals(HistoryCursor(100, 1), nextCursor(merged))
    }

    @Test
    fun `an empty page leaves the list untouched`() {
        val held = listOf(row(id = 1))
        assertEquals(held, mergePage(held, emptyList()))
    }

    // ─── prefetch and end of list ───────────────────────────────────────────────────────────────

    @Test
    fun `a short page is the end of the list and a full one is not`() {
        assertTrue(isLastPage(List(12) { row(id = it.toLong()) }, limit = 30))
        assertFalse(isLastPage(List(30) { row(id = it.toLong()) }, limit = 30))
        assertTrue(isLastPage(emptyList(), limit = 30))
    }

    @Test
    fun `prefetch fires near the end, once`() {
        assertTrue(shouldPrefetch(lastVisibleIndex = 24, loadedCount = 30, endReached = false, inFlight = false))
        assertFalse(shouldPrefetch(lastVisibleIndex = 10, loadedCount = 30, endReached = false, inFlight = false))
        // Past the end there is nothing to ask for; a list that keeps asking issues a query per frame.
        assertFalse(shouldPrefetch(lastVisibleIndex = 29, loadedCount = 30, endReached = true, inFlight = false))
        // The same page requested twice arrives twice, and mergePage would fold the duplicate away
        // silently — the bug would show up only as a doubled query count.
        assertFalse(shouldPrefetch(lastVisibleIndex = 29, loadedCount = 30, endReached = false, inFlight = true))
        // The first page is fetched by the page's own load, never by a scroll.
        assertFalse(shouldPrefetch(lastVisibleIndex = 0, loadedCount = 0, endReached = false, inFlight = false))
    }
}

// ─── a store that pages ─────────────────────────────────────────────────────────────────────────

/**
 * The smallest thing that can be paged: rows in `(started_at, id)` descending, addressable either
 * way, and deletable in between.
 */
private class FakeHistory(rows: List<SessionSummary>) {
    private val rows = rows.sortedWith(
        compareByDescending<SessionSummary> { it.startedAt }.thenByDescending { it.sessionId },
    ).toMutableList()

    fun delete(id: Long) {
        rows.removeAll { it.sessionId == id }
    }

    /** `WHERE (started_at, id) < (?, ?) ORDER BY started_at DESC, id DESC LIMIT ?` */
    fun page(cursor: HistoryCursor?, limit: Int): List<SessionSummary> =
        rows.filter {
            cursor == null ||
                it.startedAt < cursor.startedAt ||
                (it.startedAt == cursor.startedAt && it.sessionId < cursor.sessionId)
        }.take(limit)

    /** What §4 edge case 1 forbids, kept only so a test can watch it lose a row. */
    fun offsetPage(offset: Int, limit: Int): List<SessionSummary> = rows.drop(offset).take(limit)
}

private fun row(
    id: Long = 1L,
    date: LocalDate = LocalDate.of(2026, 6, 17),
    startedAt: Long = id * 100L,
    personalBest: Boolean = false,
) = SessionSummary(
    sessionId = id,
    routineId = "seven",
    routineVersionId = 1L,
    routineName = "七分間",
    engine = Engine.INTERVAL_CIRCUIT,
    tier = null,
    startedAt = startedAt,
    finishedAt = startedAt + 374_000L,
    localDate = date,
    activeMs = 374_000L,
    wallMs = 374_000L,
    complete = true,
    reachedTimeCap = false,
    roundsPlanned = 1,
    roundsCompleted = 3,
    stationsPlanned = 12,
    stationsCompleted = 12,
    totalReps = 320,
    volumeUnits = 0.0,
    rating = null,
    personalBest = personalBest,
)
