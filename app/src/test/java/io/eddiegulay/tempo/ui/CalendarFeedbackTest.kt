package io.eddiegulay.tempo.ui

import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.data.GymFault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The contract this feature rests on: nothing fails silently, and nothing fails without a way out.
 *
 * These are the assertions that would have caught the original bugs — a provider error rendering as
 * "no events", and a failed save walking the user back to an agenda that didn't contain their event.
 */
class CalendarFeedbackTest {

    private val everyFault = listOf(
        CalendarFault.PermissionLost,
        CalendarFault.NoWritableCalendar,
        CalendarFault.EventGone,
        CalendarFault.Rejected,
        CalendarFault.NoCalendarApp,
        CalendarFault.Unknown("boom"),
    )

    // 鍛錬 rides the same strip, the same panel and the same copy function. Listed exhaustively here
    // on purpose: `faultCopy` dispatches by family, so a case added to GymFault without copy would
    // compile — this list is what fails instead.
    private val everyGymFault = listOf(
        GymFault.StoreCorrupt,
        GymFault.StoreUnavailable("locked"),
        GymFault.StoreFull,
        GymFault.StoreReset,
        GymFault.RoutineGone,
        GymFault.SessionGone,
        GymFault.Rejected,
        GymFault.Unknown("boom"),
    )

    @Test
    fun `every fault says something to the user`() {
        everyFault.forEach { fault ->
            assertTrue("$fault has no message", faultCopy(fault).message.isNotBlank())
        }
        everyGymFault.forEach { fault ->
            assertTrue("$fault has no message", faultCopy(fault).message.isNotBlank())
        }
    }

    @Test
    fun `every fault the user can act on offers a way out`() {
        // NoCalendarApp is the one dead end, and only because there is genuinely nothing to press —
        // no calendar app exists to open. It still gets a message, so the user knows to go install one.
        val recoverable = everyFault - CalendarFault.NoCalendarApp
        recoverable.forEach { fault ->
            assertNotNull("$fault leaves the user stuck", faultCopy(fault).action)
        }
        assertNull(faultCopy(CalendarFault.NoCalendarApp).action)
    }

    @Test
    fun `a lost permission asks for permission rather than offering a pointless retry`() {
        assertEquals("許可する", faultCopy(CalendarFault.PermissionLost).action)
    }

    @Test
    fun `nowhere to write sends the user to add an account, which is the only real fix`() {
        assertEquals("アカウントを追加", faultCopy(CalendarFault.NoWritableCalendar).action)
    }

    @Test
    fun `an unknown fault is still shown, and is still retryable`() {
        val copy = faultCopy(CalendarFault.Unknown(cause = null))
        assertTrue(copy.message.isNotBlank())
        assertEquals("もう一度", copy.action)
    }

    @Test
    fun `every gym fault says exactly the words the spec decided on`() {
        // DECISIONS.md §Q6, verbatim. Each entry there is sourced to a string table; a case that drifts
        // off this table is a case shipping copy nobody signed off on.
        assertEquals("記録を読めません", faultCopy(GymFault.StoreCorrupt).message)
        assertEquals("記録を読めません", faultCopy(GymFault.StoreUnavailable("locked")).message)
        assertEquals("記録を読めません", faultCopy(GymFault.StoreReset).message)
        assertEquals("記録を読めません", faultCopy(GymFault.Unknown("boom")).message)
        assertEquals("空き容量が足りません", faultCopy(GymFault.StoreFull).message)
        assertEquals("この型は削除されています", faultCopy(GymFault.RoutineGone).message)
        assertEquals("この記録は削除されています", faultCopy(GymFault.SessionGone).message)
        assertEquals("保存できませんでした", faultCopy(GymFault.Rejected).message)
    }

    @Test
    fun `every gym fault offers exactly the action the spec decided on`() {
        assertEquals("もう一度", faultCopy(GymFault.StoreCorrupt).action)
        assertEquals("もう一度", faultCopy(GymFault.StoreUnavailable("locked")).action)
        assertEquals("もう一度", faultCopy(GymFault.StoreReset).action)
        assertEquals("もう一度", faultCopy(GymFault.Unknown("boom")).action)
        assertNull(faultCopy(GymFault.StoreFull).action)
        assertNull(faultCopy(GymFault.RoutineGone).action)
        assertNull(faultCopy(GymFault.SessionGone).action)
        assertNull(faultCopy(GymFault.Rejected).action)
    }

    @Test
    fun `a full disk is named as a full disk, and offers no retry that cannot work`() {
        val copy = faultCopy(GymFault.StoreFull)
        assertEquals("空き容量が足りません", copy.message)
        assertNull("retrying cannot free space", copy.action)
    }

    @Test
    fun `a vanished routine or session leaves nothing to press, because the page is leaving`() {
        assertNull(faultCopy(GymFault.RoutineGone).action)
        assertNull(faultCopy(GymFault.SessionGone).action)
    }

    @Test
    fun `a refused row leaves nothing to press, because the same draft will be refused again`() {
        assertNull(faultCopy(GymFault.Rejected).action)
    }

    @Test
    fun `a store that might come back is retryable`() {
        // The store cases whose remedy is "try again": a lock that clears, a quarantine that has
        // already happened, a downgrade that gets reinstalled, and the unforeseen. All four are the
        // 記録を読めません cases, and 記録を読めません is precisely the sentence a retry can undo.
        listOf(
            GymFault.StoreUnavailable("locked"),
            GymFault.StoreCorrupt,
            GymFault.StoreReset,
            GymFault.Unknown(null),
        ).forEach { fault ->
            assertEquals("$fault leaves the user stuck", "記録を読めません", faultCopy(fault).message)
            assertEquals("$fault leaves the user stuck", "もう一度", faultCopy(fault).action)
        }
    }

    @Test
    fun `an unreadable gym store never renders as an empty one`() {
        // The whole reason GymFault exists. StoreCorrupt means the history is unreadable, and the copy
        // must not be — nor be confusable with — 記録はありません. 記録を読めません carries no ありません
        // at all, which is what makes it unmistakable at a glance.
        val copy = faultCopy(GymFault.StoreCorrupt)
        assertEquals("記録を読めません", copy.message)
        assertTrue(copy.message, !copy.message.contains("ありません"))
    }

    @Test
    fun `the confirmation restates a timed event as a span`() {
        val summary = draftSummary(
            EventDraft(
                title = "Standup",
                start = LocalDateTime.of(2026, 6, 19, 9, 30),
                end = LocalDateTime.of(2026, 6, 19, 10, 0),
                allDay = false,
                location = "",
                calendarId = 1L,
            ),
        )
        assertEquals("Standup", summary.title)
        assertTrue(summary.when_, summary.when_.contains("09:30 – 10:00"))
    }

    @Test
    fun `the confirmation restates an all-day event without inventing a time`() {
        val summary = draftSummary(
            EventDraft(
                title = "休み",
                start = LocalDateTime.of(2026, 6, 19, 0, 0),
                end = LocalDateTime.of(2026, 6, 19, 0, 0),
                allDay = true,
                location = "",
                calendarId = 1L,
            ),
        )
        assertTrue(summary.when_, summary.when_.endsWith("終日"))
    }

    @Test
    fun `an untitled draft is confirmed as untitled rather than as an empty quote`() {
        val summary = draftSummary(
            EventDraft(
                title = "   ",
                start = LocalDateTime.of(2026, 6, 19, 9, 30),
                end = LocalDateTime.of(2026, 6, 19, 10, 0),
                allDay = false,
                location = "",
                calendarId = 1L,
            ),
        )
        assertEquals("（無題）", summary.title)
    }
}
