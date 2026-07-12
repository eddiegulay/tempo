package io.eddiegulay.tempo.ui

import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.EventDraft
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

    @Test
    fun `every fault says something to the user`() {
        everyFault.forEach { fault ->
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
