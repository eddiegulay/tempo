package io.eddiegulay.tempo.ui

import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.calendar.displayTitle
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * **A placeholder is what a blank looks like, never what a blank is.**
 *
 * The calendar is the one surface where Tempo's copy leaves Tempo. `（無題）` used to be substituted
 * into `CalendarEvent.title` as the provider row was read, and from there it prefilled the composer,
 * it was the reason 保存 was enabled at all, and it went into the `EventDraft` — so opening an untitled
 * event to move it by an hour *named* it, in Japanese, and the sync adapter carried that name to the
 * user's Google account, to their other devices and to every guest holding a copy of the invite.
 *
 * Translating the string would have written an English placeholder into their calendar instead, which
 * is why this is a behaviour test and not a copy test. These are the assertions that would have caught
 * it, and the ones that stop it coming back the next time somebody finds an empty title untidy.
 */
class UntitledEventTest {

    private fun untitled(title: String = "") = CalendarEvent(
        eventId = 7L,
        title = title,
        begin = 0L,
        end = 1L,
        allDay = false,
        location = null,
        calendarId = 1L,
        calendarName = "work",
        color = 0,
        recurring = false,
        writable = true,
    )

    @Test
    fun `an untitled event carries a blank title, and the placeholder only appears when it is drawn`() {
        val event = untitled()
        assertTrue("the model must hold the provider's blank, not a word for it", event.title.isBlank())
        assertEquals("（無題）", event.displayTitle(StringsJa))
        assertEquals("(No title)", event.displayTitle(StringsEn))
    }

    @Test
    fun `an event with a title of its own is drawn with it, in either language`() {
        val event = untitled(title = "Standup")
        assertEquals("Standup", event.displayTitle(StringsJa))
        assertEquals("Standup", event.displayTitle(StringsEn))
    }

    @Test
    fun `the draft built from an untitled event carries nothing to write`() {
        // The composer prefills from `title` (EventComposeScreen:100) and trims it into the draft
        // (:178). This is that value. If a placeholder is ever put back on the model, this is the
        // assertion that fails, and it fails before the write reaches anyone's calendar.
        val draft = EventDraft(
            title = untitled().title.trim(),
            start = LocalDateTime.of(2026, 6, 19, 9, 30),
            end = LocalDateTime.of(2026, 6, 19, 10, 0),
            allDay = false,
            location = "",
            calendarId = 1L,
        )
        assertTrue(draft.title, draft.title.isEmpty())
        assertNotEquals(StringsJa.calendar.untitled, draft.title)
        assertNotEquals(StringsEn.calendar.untitled, draft.title)
    }

    @Test
    fun `a new event still asks for a name`() {
        // `.planning/calendar-design.md` §3.5, unchanged: an event nobody has named is an accidental
        // press, and the one word is cheap.
        assertFalse(
            canSaveEvent(title = "  ", calendarId = 1L, isNewEvent = true, readOnly = false, writing = false),
        )
        assertTrue(
            canSaveEvent(title = "Standup", calendarId = 1L, isNewEvent = true, readOnly = false, writing = false),
        )
    }

    @Test
    fun `an event that arrived untitled can still be rescheduled`() {
        // The regression the fix must not introduce. Gating an *existing* event on its title is what
        // made the placeholder load-bearing; without the placeholder that same gate would leave a
        // user unable to move a meeting somebody else created without a name.
        assertTrue(
            canSaveEvent(title = "", calendarId = 4L, isNewEvent = false, readOnly = false, writing = false),
        )
    }

    @Test
    fun `nothing is savable without somewhere to save it, or while a write is in flight`() {
        assertFalse(
            "no writable calendar",
            canSaveEvent(title = "Standup", calendarId = -1L, isNewEvent = true, readOnly = false, writing = false),
        )
        assertFalse(
            "a recurring or read-only event is never saved from here",
            canSaveEvent(title = "Standup", calendarId = 1L, isNewEvent = false, readOnly = true, writing = false),
        )
        assertFalse(
            "a second tap while the first is still writing",
            canSaveEvent(title = "Standup", calendarId = 1L, isNewEvent = false, readOnly = false, writing = true),
        )
    }

    @Test
    fun `the Japanese placeholder keeps its full-width parentheses`() {
        // H10. It is U+FF08 / U+FF09, not ASCII, and a copy-paste through anything that "tidies"
        // punctuation changes the string silently. Pinned because §H1 showed this one can travel.
        val untitled = StringsJa.calendar.untitled
        assertEquals('（', untitled.first())
        assertEquals('）', untitled.last())
        assertEquals("（無題）", untitled)
    }
}
