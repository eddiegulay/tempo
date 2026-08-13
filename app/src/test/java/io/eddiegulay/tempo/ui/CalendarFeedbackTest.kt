package io.eddiegulay.tempo.ui

import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
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
 *
 * **Every structural assertion now runs against both tables.** Error copy is where an untranslated
 * string hurts most: the user meets it when something is already wrong and is least able to work
 * around it. A rule that held only in Japanese would be a rule the English build does not have.
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

    private val tables = listOf(StringsJa, StringsEn)

    /**
     * The faults that mean **"we could not read your data"**, across both features.
     *
     * These are the ones the doctrine below is about. A fault like `NoCalendarApp` is also a
     * statement of absence, but it is a true one about the *device*, not a claim about the user's
     * events or history — which is the thing that must never be guessed at.
     */
    private val readFailures = listOf<TempoFault>(
        GymFault.StoreCorrupt,
        GymFault.StoreUnavailable("locked"),
        GymFault.StoreReset,
        GymFault.Unknown("boom"),
        CalendarFault.PermissionLost,
        CalendarFault.Unknown("boom"),
    )

    @Test
    fun `every fault says something to the user, in every language`() {
        tables.forEach { strings ->
            (everyFault + everyGymFault).forEach { fault ->
                assertTrue(
                    "$fault has no message in ${strings.lang}",
                    faultCopy(fault, strings).message.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun `every fault the user can act on offers a way out, in every language`() {
        // NoCalendarApp is the one dead end, and only because there is genuinely nothing to press —
        // no calendar app exists to open. It still gets a message, so the user knows to go install one.
        val recoverable = everyFault - CalendarFault.NoCalendarApp
        tables.forEach { strings ->
            recoverable.forEach { fault ->
                assertNotNull("$fault leaves the user stuck in ${strings.lang}", faultCopy(fault, strings).action)
            }
            assertNull(faultCopy(CalendarFault.NoCalendarApp, strings).action)
        }
    }

    @Test
    fun `whether a fault has a way out is a property of the fault, not of the language`() {
        // What `faultHasAction` — and through it `writeFaultStuck`, and through that the dismiss glyph
        // on GYM.HOME's write strip — rests on. The `when` arms that pass null pass it in every
        // language, so a table that disagreed would mean a strip that is dismissable in one language
        // and pinned to the feed forever in the other.
        (everyFault + everyGymFault).forEach { fault ->
            val byLanguage = tables.map { faultCopy(fault, it).action != null }.toSet()
            assertEquals("$fault offers an action in one language and not the other", 1, byLanguage.size)
            assertEquals("faultHasAction disagrees with faultCopy for $fault", byLanguage.single(), faultHasAction(fault))
        }
    }

    @Test
    fun `a lost permission asks for permission rather than offering a pointless retry`() {
        assertEquals("許可する", faultCopy(CalendarFault.PermissionLost, StringsJa).action)
        assertEquals(StringsEn.fault.calendar.permissionLostAction, faultCopy(CalendarFault.PermissionLost, StringsEn).action)
        // The point of the case: the word is not the retry word, in either language.
        tables.forEach { strings ->
            assertTrue(
                "a permission fault must not offer a retry in ${strings.lang}",
                faultCopy(CalendarFault.PermissionLost, strings).action != strings.fault.retry,
            )
        }
    }

    @Test
    fun `nowhere to write sends the user to add an account, which is the only real fix`() {
        assertEquals("アカウントを追加", faultCopy(CalendarFault.NoWritableCalendar, StringsJa).action)
        assertEquals(
            StringsEn.fault.calendar.noWritableCalendarAction,
            faultCopy(CalendarFault.NoWritableCalendar, StringsEn).action,
        )
        tables.forEach { strings ->
            assertTrue(
                "adding an account is the fix, not a retry, in ${strings.lang}",
                faultCopy(CalendarFault.NoWritableCalendar, strings).action != strings.fault.retry,
            )
        }
    }

    @Test
    fun `an unknown fault is still shown, and is still retryable`() {
        tables.forEach { strings ->
            val copy = faultCopy(CalendarFault.Unknown(cause = null), strings)
            assertTrue(copy.message.isNotBlank())
            assertEquals(strings.fault.retry, copy.action)
        }
        assertEquals("もう一度", faultCopy(CalendarFault.Unknown(cause = null), StringsJa).action)
    }

    @Test
    fun `every gym fault says exactly the words the spec decided on`() {
        // DECISIONS.md §Q6, verbatim. Each entry there is sourced to a string table; a case that drifts
        // off this table is a case shipping copy nobody signed off on. Kept as literals rather than
        // read back out of `StringsJa`: this is the assertion that catches a transcription slip in the
        // move into the table, which a table-against-table comparison cannot see.
        assertEquals("記録を読めません", faultCopy(GymFault.StoreCorrupt, StringsJa).message)
        assertEquals("記録を読めません", faultCopy(GymFault.StoreUnavailable("locked"), StringsJa).message)
        assertEquals("記録を読めません", faultCopy(GymFault.StoreReset, StringsJa).message)
        assertEquals("記録を読めません", faultCopy(GymFault.Unknown("boom"), StringsJa).message)
        assertEquals("空き容量が足りません", faultCopy(GymFault.StoreFull, StringsJa).message)
        assertEquals("この型は削除されています", faultCopy(GymFault.RoutineGone, StringsJa).message)
        assertEquals("この記録は削除されています", faultCopy(GymFault.SessionGone, StringsJa).message)
        assertEquals("保存できませんでした", faultCopy(GymFault.Rejected, StringsJa).message)
    }

    @Test
    fun `every gym fault offers exactly the action the spec decided on`() {
        tables.forEach { strings ->
            assertEquals(strings.fault.retry, faultCopy(GymFault.StoreCorrupt, strings).action)
            assertEquals(strings.fault.retry, faultCopy(GymFault.StoreUnavailable("locked"), strings).action)
            assertEquals(strings.fault.retry, faultCopy(GymFault.StoreReset, strings).action)
            assertEquals(strings.fault.retry, faultCopy(GymFault.Unknown("boom"), strings).action)
            assertNull(faultCopy(GymFault.StoreFull, strings).action)
            assertNull(faultCopy(GymFault.RoutineGone, strings).action)
            assertNull(faultCopy(GymFault.SessionGone, strings).action)
            assertNull(faultCopy(GymFault.Rejected, strings).action)
        }
        assertEquals("もう一度", faultCopy(GymFault.StoreCorrupt, StringsJa).action)
    }

    @Test
    fun `a full disk is named as a full disk, and offers no retry that cannot work`() {
        assertEquals("空き容量が足りません", faultCopy(GymFault.StoreFull, StringsJa).message)
        tables.forEach { strings ->
            assertNull("retrying cannot free space", faultCopy(GymFault.StoreFull, strings).action)
        }
    }

    @Test
    fun `a vanished routine or session leaves nothing to press, because the page is leaving`() {
        tables.forEach { strings ->
            assertNull(faultCopy(GymFault.RoutineGone, strings).action)
            assertNull(faultCopy(GymFault.SessionGone, strings).action)
        }
    }

    @Test
    fun `a refused row leaves nothing to press, because the same draft will be refused again`() {
        tables.forEach { strings ->
            assertNull(faultCopy(GymFault.Rejected, strings).action)
        }
    }

    @Test
    fun `a store that might come back is retryable`() {
        // The store cases whose remedy is "try again": a lock that clears, a quarantine that has
        // already happened, a downgrade that gets reinstalled, and the unforeseen. All four are the
        // unreadable-store cases, and that is precisely the sentence a retry can undo.
        val retryable = listOf(
            GymFault.StoreUnavailable("locked"),
            GymFault.StoreCorrupt,
            GymFault.StoreReset,
            GymFault.Unknown(null),
        )
        tables.forEach { strings ->
            retryable.forEach { fault ->
                assertEquals("$fault drifted off the shared sentence", strings.fault.gym.storeUnreadable, faultCopy(fault, strings).message)
                assertEquals("$fault leaves the user stuck", strings.fault.retry, faultCopy(fault, strings).action)
            }
        }
    }

    // ─── loading ≠ empty ≠ failed ───────────────────────────────────────────────────────────────

    /*
     * The doctrine, and the one rule in this file that is worth more than the sum of the assertions
     * above it.
     *
     * The original assertion read `!copy.message.contains("ありません")` — true, load-bearing, and
     * untranslatable: English has no ありません, so a literal translation of the assertion would check
     * for a word that cannot appear and would pass on any English string at all, including "No
     * records found". Translating the *assertion* would have deleted the guarantee while looking like
     * it kept it.
     *
     * What the assertion protects is a property, not a spelling: **a message that means "we could not
     * read it" must make no claim about whether there is anything to read.** Every language has a way
     * of claiming there is nothing; each language's way is listed below, and the rule is that a
     * read-failure message contains none of them.
     */

    /**
     * How each language says "there is none" — the vocabulary a fault message may not borrow.
     *
     * English matches on **word boundaries**, and that is not a detail: "Cannot" contains the letters
     * of "no", so a substring test would fail the very sentence it exists to protect and teach the
     * next author to weaken the rule.
     */
    private val emptinessClaims: Map<Strings, List<Regex>> = mapOf(
        StringsJa to listOf(Regex("ありません"), Regex("ございません"), Regex("なし")),
        StringsEn to listOf(
            Regex("""\bno\b""", RegexOption.IGNORE_CASE),
            Regex("""\bnone\b""", RegexOption.IGNORE_CASE),
            Regex("""\bnothing\b""", RegexOption.IGNORE_CASE),
            Regex("""\bempty\b""", RegexOption.IGNORE_CASE),
        ),
    )

    @Test
    fun `an unread store or calendar is never phrased as an empty one, in any language`() {
        // The whole reason GymFault and Loadable exist. A read that failed and a read that found
        // nothing are the same picture on screen and opposite claims about the user's data; told
        // there is nothing, a user believes their training history is gone and stops looking for it.
        // This codebase has caught that conflation five separate times.
        tables.forEach { strings ->
            val claims = emptinessClaims.getValue(strings)
            readFailures.forEach { fault ->
                val message = faultCopy(fault, strings).message
                val borrowed = claims.firstOrNull { it.containsMatchIn(message) }
                assertNull(
                    "${strings.lang}: \"$message\" for $fault reads as emptiness — it matched " +
                        "$borrowed, and a user who reads it as \"there is none\" stops looking for " +
                        "data that is still there",
                    borrowed,
                )
            }
        }
    }

    @Test
    fun `the emptiness vocabulary can actually fire`() {
        // The anti-vacuity half, and it is not optional: the assertion above is an *absence*, and an
        // absence passes trivially against a vocabulary that matches nothing. These are the real
        // empty-state sentences the app draws elsewhere — 記録はありません on RECORDS, and the English
        // shapes an empty state takes. If the guard cannot catch these, it is guarding nothing.
        val japaneseEmptyStates = listOf("記録はありません", "まだ 記録はありません", "予定はありません")
        val englishEmptyStates = listOf("No records yet", "Nothing recorded", "Your history is empty", "None yet")

        japaneseEmptyStates.forEach { sentence ->
            assertTrue(
                "the Japanese guard would not catch \"$sentence\"",
                emptinessClaims.getValue(StringsJa).any { it.containsMatchIn(sentence) },
            )
        }
        englishEmptyStates.forEach { sentence ->
            assertTrue(
                "the English guard would not catch \"$sentence\"",
                emptinessClaims.getValue(StringsEn).any { it.containsMatchIn(sentence) },
            )
        }
    }

    @Test
    fun `the English fault table is in English`() {
        // `I18nGateTest`'s anti-vacuity check scans `StringsEn.kt`, and every `En<Namespace>` now
        // lives in its own namespace file instead — so nothing there covers `EnFault`. Until that
        // gate widens, this is what stops an untranslated fault string shipping to an English user,
        // which is the failure the whole migration exists to prevent, arriving in the one place the
        // user can least afford it.
        val japanese = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]")
        (everyFault + everyGymFault).forEach { fault ->
            val copy = faultCopy(fault, StringsEn)
            assertTrue("$fault still speaks Japanese: \"${copy.message}\"", !japanese.containsMatchIn(copy.message))
            copy.action?.let {
                assertTrue("$fault's action still speaks Japanese: \"$it\"", !japanese.containsMatchIn(it))
            }
        }
    }

    @Test
    fun `the two tables say different things`() {
        // Guards the wiring in `StringsJa`/`StringsEn`: a namespace pointed at the wrong implementation
        // is not a compile error, and every assertion above would still pass.
        (everyFault + everyGymFault).forEach { fault ->
            assertEquals(
                "$fault reads identically in both languages — check the `fault` wiring in StringsEn",
                2,
                tables.map { faultCopy(fault, it).message }.toSet().size,
            )
        }
    }

    // ─── the write confirmation ─────────────────────────────────────────────────────────────────

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
            StringsJa,
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
            StringsJa,
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
            StringsJa,
        )
        assertEquals("（無題）", summary.title)
    }
}
