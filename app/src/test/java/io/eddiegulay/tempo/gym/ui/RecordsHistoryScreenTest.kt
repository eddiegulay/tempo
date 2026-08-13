package io.eddiegulay.tempo.gym.ui

import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.LifetimeSummary
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.groupByMonth
import io.eddiegulay.tempo.gym.shouldPrefetch
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.gym.HISTORY_MENU_ITEMS
import io.eddiegulay.tempo.ui.gym.HistoryFooter
import io.eddiegulay.tempo.ui.gym.HistoryItem
import io.eddiegulay.tempo.ui.gym.HistoryLoad
import io.eddiegulay.tempo.ui.gym.HistoryMenuItem
import io.eddiegulay.tempo.ui.gym.HistoryPageState
import io.eddiegulay.tempo.ui.gym.anchorHeaderIndex
import io.eddiegulay.tempo.ui.gym.historyEmptyCopy
import io.eddiegulay.tempo.ui.gym.historyFooter
import io.eddiegulay.tempo.ui.gym.historyHeadFailed
import io.eddiegulay.tempo.ui.gym.historyHeadKey
import io.eddiegulay.tempo.ui.gym.historyHeadReady
import io.eddiegulay.tempo.ui.gym.historyItems
import io.eddiegulay.tempo.ui.gym.historyMonthSemantics
import io.eddiegulay.tempo.ui.gym.historyPageFailed
import io.eddiegulay.tempo.ui.gym.historyPageState
import io.eddiegulay.tempo.ui.gym.historyPageWanted
import io.eddiegulay.tempo.ui.gym.historyRetry
import io.eddiegulay.tempo.ui.gym.historyRowSemantics
import io.eddiegulay.tempo.ui.gym.historySubtitleOrNull
import io.eddiegulay.tempo.ui.gym.label
import io.eddiegulay.tempo.ui.gym.rowsThrough
import io.eddiegulay.tempo.ui.gym.sessionDeleteCopy
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GYM.RECORDS.HISTORY`'s own decisions, as assertions.
 *
 * The paging *arithmetic* is not on trial here — `HistoryPagingTest` already pins `groupByMonth`,
 * `nextCursor`, `shouldPrefetch` and `mergePage`, and this page calls them rather than restating them.
 * What is pinned below is what the page adds on top: which of four states is showing, which sentence an
 * empty list gets, what a row says out loud, and that the two long-press items and the delete dialog
 * carry §6's words and not near-misses of them.
 *
 * The doctrine on trial is `00-plan.md` §4.1 rule 1 — **loading ≠ empty ≠ failed** — and it is tested
 * as an *ordering*, because that is the shape the bug takes: every regression of it has been a branch
 * that reached the ありません sentence one condition too early.
 */
class RecordsHistoryScreenTest {

    // ─── the four list states ───────────────────────────────────────────────────────────────────

    @Test
    fun `an unread history is loading, never empty`() {
        // The whole doctrine in one line: nothing has answered, so nothing may be claimed.
        assertEquals(
            HistoryPageState.Loading,
            historyPageState(headLoaded = false, headFault = null, rows = 0),
        )
    }

    @Test
    fun `an unreadable history is failed, never empty`() {
        // `DECISIONS.md` §Q6: 記録を読めません must never be reachable as 記録はありません.
        assertEquals(
            HistoryPageState.Failed,
            historyPageState(headLoaded = false, headFault = GymFault.StoreCorrupt, rows = 0),
        )
    }

    @Test
    fun `a history that answered with nothing is empty`() {
        assertEquals(
            HistoryPageState.Empty,
            historyPageState(headLoaded = true, headFault = null, rows = 0),
        )
    }

    @Test
    fun `a paging failure never destroys what is already on screen`() {
        // §4's `Error (next page)`, stated as a precedence: rows outrank a fault, so a failure that
        // arrives after the first page is a footer and never a panel.
        assertEquals(
            HistoryPageState.Ready,
            historyPageState(headLoaded = true, headFault = GymFault.StoreUnavailable(null), rows = 12),
        )
    }

    // ─── the empty sentences ────────────────────────────────────────────────────────────────────

    @Test
    fun `an unfiltered empty history says 記録はありません`() {
        assertEquals("記録はありません", historyEmptyCopy(routineId = null, strings = StringsJa))
    }

    @Test
    fun `a filtered empty history says この型は まだ やっていません`() {
        // A claim about one routine, not about the store. §4's `Empty (filtered)`.
        assertEquals("この型は まだ やっていません", historyEmptyCopy(routineId = "r_seven", strings = StringsJa))
    }

    @Test
    fun `neither empty sentence can be mistaken for the unreadable one`() {
        // 記録を読めません is `DECISIONS.md` §Q6's, and the property that keeps the promise is that it
        // contains no ありません-as-emptiness. Pinned from this side too.
        listOf(historyEmptyCopy(null, StringsJa), historyEmptyCopy("r_seven", StringsJa)).forEach { sentence ->
            assertNotEquals("記録を読めません", sentence)
            assertFalse(sentence.contains("読めません"))
        }
    }

    // ─── the footer ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the footer is 読み込み中 while a page is in flight`() {
        assertEquals(
            HistoryFooter.Loading,
            historyFooter(endReached = false, inFlight = true, pageFault = null),
        )
    }

    @Test
    fun `a failed page outranks everything and offers もう一度`() {
        // Otherwise the user's next scroll silently re-issues the request that just failed.
        assertEquals(
            HistoryFooter.Retry,
            historyFooter(endReached = false, inFlight = true, pageFault = GymFault.StoreUnavailable(null)),
        )
        assertEquals(
            HistoryFooter.Retry,
            historyFooter(endReached = true, inFlight = false, pageFault = GymFault.StoreFull),
        )
    }

    @Test
    fun `the end of the list is air, not a sentence`() {
        // §4: the footer becomes a 40.dp Spacer and there is **no** "no more items" text.
        assertEquals(
            HistoryFooter.End,
            historyFooter(endReached = true, inFlight = false, pageFault = null),
        )
        assertEquals(
            HistoryFooter.None,
            historyFooter(endReached = false, inFlight = false, pageFault = null),
        )
    }

    // ─── the flat list ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `months flatten to a header then its rows, with §4's keys`() {
        val june = session(sessionId = 2L, date = LocalDate.of(2026, 6, 17))
        val may = session(sessionId = 1L, date = LocalDate.of(2026, 5, 3))
        val items = historyItems(groupByMonth(listOf(june, may), StringsJa))

        assertEquals(
            listOf("month:2026-06", "2", "month:2026-05", "1"),
            items.map { it.key },
        )
        assertTrue(items[0] is HistoryItem.Header)
        assertTrue(items[1] is HistoryItem.Row)
    }

    @Test
    fun `deleting the last session in a month removes that month's header`() {
        // §4 edge case 3, honoured by deriving the grouping from the whole list on every emission
        // rather than patching it. The page's delete is a `filterNot` on the loaded list, so this is
        // the whole mechanism.
        val june = session(sessionId = 2L, date = LocalDate.of(2026, 6, 17))
        val may = session(sessionId = 1L, date = LocalDate.of(2026, 5, 3))
        val after = listOf(june, may).filterNot { it.sessionId == 1L }

        assertEquals(listOf("month:2026-06", "2"), historyItems(groupByMonth(after, StringsJa)).map { it.key })
    }

    @Test
    fun `rowsThrough counts sessions, never headers`() {
        // `shouldPrefetch` counts rows and has no business knowing row heights. A flat index would make
        // twelve one-session months prefetch at twice the intended depth.
        val items = historyItems(
            groupByMonth(
                listOf(
                    session(sessionId = 3L, date = LocalDate.of(2026, 6, 17)),
                    session(sessionId = 2L, date = LocalDate.of(2026, 5, 9)),
                    session(sessionId = 1L, date = LocalDate.of(2026, 4, 2)),
                ),
                StringsJa,
            ),
        )
        // header, row, header, row, header, row
        assertEquals(0, rowsThrough(items, 0))
        assertEquals(1, rowsThrough(items, 1))
        assertEquals(1, rowsThrough(items, 2))
        assertEquals(2, rowsThrough(items, 3))
        // The footer sits one past the end and must not throw.
        assertEquals(3, rowsThrough(items, items.size + 4))
    }

    // ─── the anchor month ───────────────────────────────────────────────────────────────────────

    @Test
    fun `an anchor month resolves to its header once it has been paged in`() {
        val items = historyItems(
            groupByMonth(
                listOf(
                    session(sessionId = 2L, date = LocalDate.of(2026, 6, 17)),
                    session(sessionId = 1L, date = LocalDate.of(2026, 5, 3)),
                ),
                StringsJa,
            ),
        )
        assertEquals(2, anchorHeaderIndex(items, YearMonth.of(2026, 5)))
    }

    @Test
    fun `an anchor month that is not loaded yet resolves to nothing`() {
        // Null is what keeps the caller paging (§4 edge case 7) rather than scrolling to a guess.
        val items = historyItems(groupByMonth(listOf(session(date = LocalDate.of(2026, 6, 17))), StringsJa))
        assertNull(anchorHeaderIndex(items, YearMonth.of(2026, 3)))
        assertNull(anchorHeaderIndex(items, null))
    }

    // ─── the subtitle ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a filtered history names its routine and counts it`() {
        assertEquals(
            "「七分間」十四回",
            historySubtitleOrNull(
                routineId = "r_seven",
                routineName = "七分間",
                sessionCount = 14,
                lifetime = null,
                strings = StringsJa,
            ),
        )
    }

    @Test
    fun `an unfiltered history prints the lifetime totals §4's mock asks for`() {
        // §4's own mock, second line. The totals come from `summary()` (§4 edge case 8, `DECISIONS.md`
        // §Q22) and never from the loaded pages — which are partial, so counting them would print 三十回
        // to a user with eighty-six sessions and correct itself as they scrolled.
        assertEquals(
            "八十六回 ・ 二千四百分",
            historySubtitleOrNull(
                routineId = null,
                routineName = null,
                sessionCount = null,
                lifetime = LifetimeSummary(sessions = 86, totalActiveMs = 2_400L * 60_000L),
                strings = StringsJa,
            ),
        )
    }

    @Test
    fun `an unread lifetime omits the subtitle rather than printing 〇回 ・ 〇分`() {
        // `summary()` is a `Loadable` for exactly this reason: 〇回 over a store that has not answered —
        // or over a quarantined one — is a claim about the user's life that nobody can sanity-check.
        assertNull(
            historySubtitleOrNull(
                routineId = null,
                routineName = null,
                sessionCount = null,
                lifetime = null,
                strings = StringsJa,
            ),
        )
    }

    @Test
    fun `a filtered page never borrows the lifetime totals while its name is unread`() {
        // The name comes off the first loaded row, so it is null until the first window lands. Branching
        // on the name rather than on the filter would print 八十六回 ・ 二千四百分 as the subtitle of one
        // routine's history for as long as that read took — a wrong number, not a missing one.
        assertNull(
            historySubtitleOrNull(
                routineId = "r_seven",
                routineName = null,
                sessionCount = 14,
                lifetime = LifetimeSummary(sessions = 86, totalActiveMs = 2_400L * 60_000L),
                strings = StringsJa,
            ),
        )
    }

    @Test
    fun `an unread count omits the subtitle rather than printing 〇回`() {
        assertNull(
            historySubtitleOrNull(
                routineId = "r_seven",
                routineName = "七分間",
                sessionCount = null,
                lifetime = null,
                strings = StringsJa,
            ),
        )
    }

    // ─── the wedge: a failed window must not stop the list forever ──────────────────────────────

    @Test
    fun `a failed window lets go of the in-flight flag`() {
        // The bug in one line: the head flow's failure branch set the fault and left `inFlight` up, and
        // `shouldPrefetch` counts an in-flight request as a reason not to start another. One failed
        // window therefore stopped the list from ever asking for anything again — on the filtered
        // branch permanently, because that branch has no other fetch at all.
        val asking = HistoryLoad(loaded = rows(30), inFlight = true)
        assertTrue("the fixture must be able to prefetch before the failure", canPrefetch(asking.copy(inFlight = false)))

        val failed = historyHeadFailed(asking, GymFault.StoreUnavailable(null))

        assertFalse("a request that failed is a request that is over", failed.inFlight)
        assertTrue("and the list must be able to want another page", canPrefetch(failed))
        // Rows outrank the fault: §4's `Error (next page)` — a footer, never a panel.
        assertEquals(GymFault.StoreUnavailable(null), failed.pageFault)
        assertNull(failed.headFault)
        assertEquals(HistoryFooter.Retry, historyFooter(failed.endReached, failed.inFlight, failed.pageFault))
    }

    @Test
    fun `a first window that failed is a panel, not a footer`() {
        val failed = historyHeadFailed(HistoryLoad(inFlight = true), GymFault.StoreCorrupt)
        assertEquals(GymFault.StoreCorrupt, failed.headFault)
        assertNull(failed.pageFault)
        assertFalse(failed.inFlight)
        assertEquals(
            HistoryPageState.Failed,
            historyPageState(failed.headLoaded, failed.headFault, failed.loaded.size),
        )
    }

    @Test
    fun `a failed page followed by もう一度 fetches again on the filtered branch`() {
        // The second half of the wedge. The retry token used to key the cursor-page effect only, and
        // that effect returns immediately for a filtered list — so もう一度 fired no query whatsoever
        // and the footer merely flipped back to 読み込み中 under a stuck `inFlight`.
        val failed = historyHeadFailed(HistoryLoad(loaded = rows(30), inFlight = true), GymFault.StoreUnavailable(null))
        val before = historyHeadKey("r_seven", failed.pagesWanted, failed.retryToken)

        val retried = historyRetry(failed)

        assertNull("もう一度 dismisses the fault", retried.pageFault)
        assertFalse("and lowers the flag that was blocking every further page", retried.inFlight)
        assertNotEquals(
            "and re-keys the head subscription, which is the filtered list's only re-fetch",
            before,
            historyHeadKey("r_seven", retried.pagesWanted, retried.retryToken),
        )
        // The rows already on screen survive it (§4's `Error (next page)`).
        assertEquals(30, retried.loaded.size)
    }

    @Test
    fun `もう一度 re-asks the unfiltered head too, where the cursor effect declines to run`() {
        // `pagesWanted == 1` means no cursor page exists yet, so the cursor effect returns immediately
        // and the head subscription is the only thing that can be asked again.
        val failed = historyHeadFailed(HistoryLoad(loaded = rows(30), inFlight = true), GymFault.StoreUnavailable(null))
        val retried = historyRetry(failed)
        assertEquals(1, retried.pagesWanted)
        assertNotEquals(
            historyHeadKey(null, failed.pagesWanted, failed.retryToken),
            historyHeadKey(null, retried.pagesWanted, retried.retryToken),
        )
    }

    @Test
    fun `a failed cursor page also lets go of the flag`() {
        val failed =
            historyPageFailed(HistoryLoad(loaded = rows(60), inFlight = true, pagesWanted = 2), GymFault.StoreFull)
        assertFalse(failed.inFlight)
        assertEquals(GymFault.StoreFull, failed.pageFault)
    }

    @Test
    fun `wanting a page raises the flag before the fetch runs`() {
        // There is a frame between the bump and the `LaunchedEffect` it keys, and the scroll threshold
        // would otherwise fire a second time inside it — two requests for one page.
        val wanted = historyPageWanted(HistoryLoad(loaded = rows(30)))
        assertTrue(wanted.inFlight)
        assertEquals(2, wanted.pagesWanted)
        assertFalse(canPrefetch(wanted))
    }

    @Test
    fun `a landed window clears both faults and the flag together`() {
        val recovering = HistoryLoad(loaded = rows(30), pageFault = GymFault.StoreUnavailable(null), inFlight = true)
        val ready = historyHeadReady(recovering, rows(30), filtered = false, window = 30)
        assertNull(ready.pageFault)
        assertNull(ready.headFault)
        assertFalse(ready.inFlight)
        assertTrue(ready.headLoaded)
    }

    @Test
    fun `a re-emitted head does not un-end a list the cursor has walked to the bottom of`() {
        // A short head is conclusive only while the head *is* the list.
        val deep = HistoryLoad(loaded = rows(45), endReached = true, pagesWanted = 2)
        assertTrue(historyHeadReady(deep, rows(30), filtered = false, window = 30).endReached)
    }

    // ─── two claims, two composables ────────────────────────────────────────────────────────────

    @Test
    fun `the two empty states do not share a composable`() {
        // `00-plan.md` §4.1 rule 1's mechanism, not merely its outcome: states that mean different
        // things do not share a shape. 記録はありません is a claim about the store and
        // この型は まだ やっていません is a claim about one routine, and one composable taking the
        // sentence as a parameter is a single `if` away from saying either in the other's situation.
        // Read from source for `LifetimeSummaryTest`'s reason: no Robolectric on this classpath.
        val source = readSource("ui/gym/RecordsHistoryScreen.kt")
        assertTrue("HistoryEmptyAll must exist", source.contains("private fun HistoryEmptyAll()"))
        assertTrue("HistoryEmptyFiltered must exist", source.contains("private fun HistoryEmptyFiltered("))
        assertFalse("and the shared one must be gone", source.contains("private fun HistoryEmpty("))
        assertTrue(
            "the Empty arm must branch on the filter rather than pass a sentence down",
            source.contains("if (routineId == null) HistoryEmptyAll() else HistoryEmptyFiltered(routineId)"),
        )
    }

    // ─── the delete strip retries the delete ────────────────────────────────────────────────────

    @Test
    fun `もう一度 on a failed delete re-attempts the delete, not the reads`() {
        // `gym.retry()` re-runs every observing *query* and does nothing about a `discardSession` that
        // was rejected: the strip dismissed itself and the record stayed. The failed target is held so
        // the strip can ask again, and the lifetime totals are re-read after a delete that landed —
        // `summary()` is one-shot, so otherwise 八十六回 sits above eighty-five rows.
        val source = readSource("ui/gym/RecordsHistoryScreen.kt")
        assertTrue("the failed target must be held", source.contains("var failedDelete by remember"))
        assertTrue("and the strip must re-run the delete", source.contains("scope.launch { runDelete(target) }"))
        assertTrue("a landed delete re-reads the lifetime totals", source.contains("gym.refreshLifetimeSummary()"))
    }

    // ─── what a row says out loud ───────────────────────────────────────────────────────────────

    @Test
    fun `a row speaks the full month-day, though it draws only the day`() {
        // §4's accessibility line. The drawn detail says 十七日 under a 六月 header the eye can see; a
        // TalkBack user arriving mid-list has no header in earshot.
        val spoken = historyRowSemantics(
            session(
                rating = Rating.HARD,
                roundsCompleted = 3,
                totalReps = 320,
                personalBest = true,
            ),
            StringsJa,
        )
        assertEquals("七分間、六月十七日、六分十四秒、三巡、三百二十回、きつい、自己最高", spoken)
    }

    @Test
    fun `an unrated row simply ends after its numbers`() {
        // §4 edge case 8: the rating fragment is absent, not blank and not a placeholder.
        val spoken = historyRowSemantics(session(rating = null, roundsCompleted = 3, totalReps = 320), StringsJa)
        assertEquals("七分間、六月十七日、六分十四秒、三巡、三百二十回", spoken)
    }

    @Test
    fun `a partial row speaks its chip and never a record`() {
        // `00-plan.md` §4.1 rule 2 — a partial session is a real session: honest numbers, the 途中まで
        // chip, and no 自己最高 in any engine (§4 edge case 1).
        val spoken = historyRowSemantics(
            session(complete = false, stationsCompleted = 2, stationsPlanned = 3, personalBest = true),
            StringsJa,
        )
        assertTrue(spoken.endsWith("途中まで ・ 三種目中 二"))
        assertFalse(spoken.contains("自己最高"))
    }

    @Test
    fun `a zero rounds or zero reps fragment is omitted, never spoken as 〇`() {
        val spoken = historyRowSemantics(session(roundsCompleted = 0, totalReps = 0), StringsJa)
        assertEquals("七分間、六月十七日、六分十四秒", spoken)
    }

    @Test
    fun `a month header is one node carrying its count`() {
        val month = groupByMonth(
            listOf(
                session(sessionId = 2L, date = LocalDate.of(2026, 6, 17)),
                session(sessionId = 1L, date = LocalDate.of(2026, 6, 3)),
            ),
            StringsJa,
        ).single()
        assertEquals("六月、二回", historyMonthSemantics(month, StringsJa))
    }

    // ─── the long press and the confirm ─────────────────────────────────────────────────────────

    @Test
    fun `the long-press menu is §6's two items, and both are always offered`() {
        // §4 edge case 4: a session whose routine was archived still shows its denormalised name and
        // この型を見る still works, landing on the archived detail. There is no conditional item.
        assertEquals(listOf("記録を削除", "この型を見る"), HISTORY_MENU_ITEMS.map { it.label(StringsJa) })
        assertEquals(
            listOf("Delete record", "See this routine"),
            HISTORY_MENU_ITEMS.map { it.label(StringsEn) },
        )
        assertEquals(HistoryMenuItem.entries.size, HISTORY_MENU_ITEMS.size)
    }

    @Test
    fun `the delete confirm is §6's own two sentences`() {
        val copy = sessionDeleteCopy(StringsJa)
        assertEquals("この記録を削除しますか", copy.title)
        assertEquals("元に戻せません。", copy.body)
        assertEquals("削除", copy.confirm)
        assertEquals("やめる", copy.dismiss)
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────────────

    /** Scrolled to the last loaded row: the one question the prefetch effect asks. */
    private fun canPrefetch(load: HistoryLoad): Boolean =
        shouldPrefetch(load.loaded.size - 1, load.loaded.size, load.endReached, load.inFlight)

    private fun rows(count: Int): List<SessionSummary> =
        (count downTo 1).map { session(sessionId = it.toLong()) }

    /** As `LifetimeSummaryTest`'s: a composable's shape is invisible to reflection and to JUnit. */
    private fun readSource(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                val candidate = File(dir, prefix + relative)
                if (candidate.exists()) return candidate.readText()
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}

private fun session(
    sessionId: Long = 1L,
    activeMs: Long = 374_000L,
    complete: Boolean = true,
    date: LocalDate = LocalDate.of(2026, 6, 17),
    personalBest: Boolean = false,
    rating: Rating? = null,
    roundsCompleted: Int = 0,
    stationsCompleted: Int = 12,
    stationsPlanned: Int = 12,
    totalReps: Int = 0,
) = SessionSummary(
    sessionId = sessionId,
    routineId = "r_seven",
    routineVersionId = 1L,
    routineName = "七分間",
    engine = Engine.INTERVAL_CIRCUIT,
    tier = null,
    startedAt = sessionId,
    finishedAt = sessionId + activeMs,
    localDate = date,
    activeMs = activeMs,
    wallMs = activeMs,
    complete = complete,
    reachedTimeCap = false,
    roundsPlanned = 1,
    roundsCompleted = roundsCompleted,
    stationsPlanned = stationsPlanned,
    stationsCompleted = stationsCompleted,
    totalReps = totalReps,
    volumeUnits = 0.0,
    rating = rating,
    personalBest = personalBest,
)
