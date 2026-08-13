package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.ui.defaultStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * 予定に入れる's arithmetic — `gym/ScheduleNext.kt`.
 *
 * The two properties worth defending are both about **not inventing**: the opening time is the app's
 * own next-clean-half-hour and not a second rule, and the block's length is the session's own measured
 * duration and not a guessed default.
 *
 * The opening-time cases live here rather than in a calendar test on purpose. They arrived with
 * 予定に入れる, over a `scheduleStartDefault` that was a verbatim copy of `EventComposeScreen`'s
 * `defaultStart`; `DECISIONS.md` §Q7 ruled the copy out, so the cases now exercise the one surviving
 * implementation and stay where the reason they exist is written down.
 */
class ScheduleNextTest {

    // ── the opening time (EventComposeScreen.defaultStart) ────────────────────────────────────────

    @Test
    fun `before half past, the draft opens on this hour's half hour`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 13, 14, 30),
            defaultStart(LocalDateTime.of(2026, 8, 13, 14, 7)),
        )
    }

    @Test
    fun `at or after half past, the draft opens on the next hour`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 13, 15, 0),
            defaultStart(LocalDateTime.of(2026, 8, 13, 14, 30)),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 13, 15, 0),
            defaultStart(LocalDateTime.of(2026, 8, 13, 14, 59)),
        )
    }

    @Test
    fun `the opening time rolls the date over at the end of the day`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 14, 0, 0),
            defaultStart(LocalDateTime.of(2026, 8, 13, 23, 41)),
        )
    }

    @Test
    fun `seconds are dropped, because nobody schedules anything at fourteen oh seven and nine`() {
        val start = defaultStart(LocalDateTime.of(2026, 8, 13, 14, 7, 9, 123))
        assertEquals(0, start.second)
        assertEquals(0, start.nano)
    }

    @Test
    fun `the opening time is never in the past`() {
        // The property the two branches exist to hold, stated once over a whole day of minutes.
        val day = LocalDateTime.of(2026, 8, 13, 0, 0)
        (0 until 24 * 60).forEach { minute ->
            val now = day.plusMinutes(minute.toLong())
            assertTrue("$now", defaultStart(now).isAfter(now))
        }
    }

    // ── scheduleEnd ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the block is as long as the session that just ended, rounded up to the minute`() {
        val start = LocalDateTime.of(2026, 8, 13, 19, 30)
        // 六分十四秒 — 七分間's own hero time in `03-player.md` §A COMPLETE's mock.
        assertEquals(LocalDateTime.of(2026, 8, 13, 19, 37), scheduleEnd(start, 6 * 60_000L + 14_000L))
    }

    @Test
    fun `a whole number of minutes is not rounded up a further minute`() {
        val start = LocalDateTime.of(2026, 8, 13, 19, 30)
        assertEquals(LocalDateTime.of(2026, 8, 13, 19, 37), scheduleEnd(start, 7 * 60_000L))
    }

    @Test
    fun `one millisecond of training still books one minute, never a zero-length event`() {
        val start = LocalDateTime.of(2026, 8, 13, 19, 30)
        assertEquals(LocalDateTime.of(2026, 8, 13, 19, 31), scheduleEnd(start, 1L))
    }

    @Test
    fun `a session with no measured time falls through to the calendar's own one-hour nudge`() {
        // `atLeastOneHourAfter` is the calendar package's answer to "the end is not after the start",
        // and reusing it is what stops a number being invented for a case that cannot really happen.
        val start = LocalDateTime.of(2026, 8, 13, 19, 30)
        assertEquals(LocalDateTime.of(2026, 8, 13, 20, 30), scheduleEnd(start, 0L))
        assertEquals(LocalDateTime.of(2026, 8, 13, 20, 30), scheduleEnd(start, -5L))
    }

    // ── scheduleDraft ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the draft carries the routine's name and nothing the app had to guess`() {
        val start = LocalDateTime.of(2026, 8, 13, 19, 30)
        val draft = scheduleDraft("七分間", 6 * 60_000L + 14_000L, start, calendarId = 4L)

        assertEquals("七分間", draft.title)
        assertEquals(start, draft.start)
        assertEquals(LocalDateTime.of(2026, 8, 13, 19, 37), draft.end)
        assertEquals(4L, draft.calendarId)
        // A workout is an appointment with a clock, and a launcher does not know where anyone trains.
        assertEquals(false, draft.allDay)
        assertEquals("", draft.location)
    }

    @Test
    fun `the label is the spec's word and is not assembled from fragments`() {
        assertEquals("予定に入れる", SCHEDULE_ACTION_LABEL)
    }

    // ── §Q7: one implementation, and the other delegates ──────────────────────────────────────────

    @Test
    fun `exactly one file in the app decides when a new event opens`() {
        // The cases above pass against a second copy just as happily as against the first — that is
        // the whole nature of the bug §Q7 was raised over, two implementations agreeing until the day
        // one of them is edited. So the property is asserted about the tree, not about a value.
        //
        // `withMinute(30)` is the rule's own fingerprint: the half-hour is where the two branches
        // meet, and no other arithmetic in this app snaps a time to it.
        val owners = kotlinSources()
            .filter { it.readText().contains("withMinute(30)") }
            .map { it.name }
            .toList()
            .sorted()

        assertEquals(listOf("EventComposeScreen.kt"), owners)
    }

    @Test
    fun `the gym's hand-off calls the composer's rule rather than restating it`() {
        val action = kotlinSources().first { it.name == "ScheduleNextAction.kt" }.readText()
        assertTrue("予定に入れる must open on the app's own opening time", action.contains("defaultStart()"))
    }

    /** Every Kotlin file under the app's main source set, resolved without a working-directory guess. */
    private fun kotlinSources(): Sequence<File> {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo", "src/main/java/io/eddiegulay/tempo")) {
                val root = File(dir, prefix)
                if (root.isDirectory) return root.walkTopDown().filter { it.extension == "kt" }
            }
            dir = dir.parentFile
        }
        throw AssertionError("main sources not found from ${File("").absolutePath}")
    }
}
