package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.gym.PersistedClock
import io.eddiegulay.tempo.ui.gym.session.SessionClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session clock, which is the one piece of this player that cannot be checked by looking at it.
 *
 * Every failure here is a workout that recorded the wrong duration, and every one of them is silent:
 * a pause that kept counting, a resume that charged its own countdown to the user's plank, a seek that
 * quietly started a clock the user had deliberately stopped. None of them throw and none of them look
 * wrong on screen for the first few seconds.
 *
 * The clock takes `now` as an argument precisely so these are assertions rather than a stopwatch.
 */
class SessionClockTest {

    /** Started at elapsedRealtime 1000, wall 500_000 — arbitrary, and never round, on purpose. */
    private val running = SessionClock(
        startedAtElapsedMs = 1_000L,
        bootAnchorMs = 499_000L,
        anchorWallMs = 500_000L,
    )

    @Test
    fun `elapsed is now minus the origin`() {
        assertEquals(0L, running.elapsedMs(1_000L))
        assertEquals(30_000L, running.elapsedMs(31_000L))
    }

    @Test
    fun `elapsed never runs backwards past zero`() {
        // A reading from before the origin is only reachable across a reboot, where §E.3 refuses the
        // resume outright — but a clock that answered -4_000 would draw an ensō with a negative sweep.
        assertEquals(0L, running.elapsedMs(0L))
    }

    @Test
    fun `a paused clock is frozen however long the pause lasts`() {
        val paused = running.pause(nowElapsedMs = 21_000L, nowWallMs = 520_000L)
        assertEquals(20_000L, paused.elapsedMs(21_000L))
        assertEquals(20_000L, paused.elapsedMs(600_000L))
        assertTrue(paused.paused)
    }

    @Test
    fun `pausing twice keeps the first anchor`() {
        // §C.1 row 20 is reachable from PAUSED: ┃┃ then ✕ must not re-anchor, or the seconds spent
        // deciding whether to quit are credited as training.
        val paused = running.pause(21_000L, 520_000L)
        val again = paused.pause(400_000L, 899_000L)
        assertEquals(paused.pausedAtElapsedMs, again.pausedAtElapsedMs)
        assertEquals(20_000L, again.elapsedMs(400_000L))
    }

    @Test
    fun `resuming continues from where the pause froze it`() {
        val resumed = running.pause(21_000L, 520_000L).resume(nowElapsedMs = 500_000L)
        assertFalse(resumed.paused)
        assertEquals(20_000L, resumed.elapsedMs(500_000L))
        assertEquals(25_000L, resumed.elapsedMs(505_000L))
    }

    @Test
    fun `a scheduled resume holds the clock for exactly the countdown`() {
        // §C.1 rows 19 and 30: the three seconds of 支度 back in run on the wall and are charged to
        // nobody. A plain resume here would record three seconds of a station the user has not
        // restarted yet.
        val held = running.pause(21_000L, 520_000L).resumeAfter(100_000L, 600_000L, delayMs = 3_000L)
        assertEquals(20_000L, held.elapsedMs(100_000L))
        assertEquals(20_000L, held.elapsedMs(102_999L))
        assertEquals(20_000L, held.elapsedMs(103_000L))
        assertEquals(20_500L, held.elapsedMs(103_500L))
    }

    @Test
    fun `a scheduled resume on a running clock pauses it first`() {
        // Row 30 reaches this on a session killed while running: there is no pause to extend, and
        // without one the countdown would be charged to the interrupted station.
        val held = running.resumeAfter(31_000L, 530_000L, delayMs = 3_000L)
        assertEquals(30_000L, held.elapsedMs(31_000L))
        assertEquals(30_000L, held.elapsedMs(33_500L))
        assertEquals(31_000L, held.elapsedMs(35_000L))
    }

    @Test
    fun `the countdown reports how much of itself is left`() {
        val held = running.resumeAfter(31_000L, 530_000L, delayMs = 3_000L)
        assertEquals(3_000L, held.resumePendingMs(31_000L))
        assertEquals(1_000L, held.resumePendingMs(33_000L))
        assertEquals(0L, held.resumePendingMs(40_000L))
        assertEquals(0L, running.resumePendingMs(40_000L))
    }

    @Test
    fun `seeking moves the clock to the asked-for elapsed`() {
        // ◁ restarting a segment, and ▷ jumping the 支度 countdown, are both this.
        val seeked = running.seekTo(targetElapsedMs = 5_000L, nowElapsedMs = 31_000L)
        assertEquals(5_000L, seeked.elapsedMs(31_000L))
        assertEquals(6_000L, seeked.elapsedMs(32_000L))
    }

    @Test
    fun `seeking while paused stays paused`() {
        // §A PAUSED, in one line: "skip-while-paused stays paused." A user who paused to re-plan
        // must be able to re-plan without the clock starting under them.
        val seeked = running.pause(31_000L, 530_000L).seekTo(5_000L, 40_000L)
        assertTrue(seeked.paused)
        assertEquals(5_000L, seeked.elapsedMs(40_000L))
        assertEquals(5_000L, seeked.elapsedMs(90_000L))
    }

    @Test
    fun `re-anchoring keeps the origin and freezes a running clock`() {
        // §E.3: within a boot `startedAtElapsedMs` is still valid, so a resumed session returns with
        // the wall-clock gap counted — deliberately, and the resume prompt is where the user accepts
        // it. Only the two witnesses move, so the *next* reboot check compares against this boot.
        val reanchored = running.reanchor(nowElapsedMs = 200_000L, nowWallMs = 699_000L)
        assertEquals(1_000L, reanchored.startedAtElapsedMs)
        assertEquals(499_000L, reanchored.bootAnchorMs)
        assertEquals(699_000L, reanchored.anchorWallMs)
        assertTrue(reanchored.paused)
        assertEquals(199_000L, reanchored.elapsedMs(200_000L))
        assertEquals(199_000L, reanchored.elapsedMs(260_000L))
    }

    @Test
    fun `re-anchoring a clock that was already paused does not move the pause`() {
        val reanchored = running.pause(21_000L, 520_000L).reanchor(200_000L, 699_000L)
        assertEquals(20_000L, reanchored.elapsedMs(200_000L))
    }

    @Test
    fun `a session row round-trips through the clock unchanged`() {
        // The store is authoritative for everything up to the last transition (§E.1), so what it hands
        // back has to be what a running clock would have written.
        val persisted = PersistedClock(
            startedAtElapsedMs = 1_000L,
            pausedAccumulatedMs = 4_000L,
            pausedAtElapsedMs = 90_000L,
            pausedAtWallMs = 590_000L,
            bootAnchorMs = 499_000L,
            anchorWallMs = 560_000L,
        )
        assertEquals(persisted, SessionClock.from(persisted).persisted())
    }

    @Test
    fun `the pending countdown is not persisted`() {
        // There is no column for it, and the honest recovery from a death inside those three seconds
        // is the pause they are still inside — the user answers the prompt again.
        val held = running.resumeAfter(31_000L, 530_000L, delayMs = 3_000L)
        val recovered = SessionClock.from(held.persisted())
        assertEquals(null, recovered.resumesAtElapsedMs)
        assertTrue(recovered.paused)
        assertEquals(30_000L, recovered.elapsedMs(999_000L))
    }

    @Test
    fun `pause duration is real time, not session time`() {
        // §A PAUSED's `PausedLong` and §C.1 row 19 both read this, and both mean "how long has the
        // user been away", which session-elapsed cannot answer because it is frozen.
        val paused = running.pause(21_000L, 520_000L)
        assertEquals(0L, paused.pausedForMs(21_000L))
        assertEquals(61_000L, paused.pausedForMs(82_000L))
        assertEquals(0L, running.pausedForMs(82_000L))
    }
}
