package io.eddiegulay.tempo.ui.gym.session

import io.eddiegulay.tempo.gym.PersistedClock

/**
 * The session clock, as arithmetic over `SystemClock.elapsedRealtime()` readings the caller supplies.
 *
 * `elapsedRealtime` is the only clock this feature keeps time with, because it is monotonic, it
 * **includes deep sleep**, and it cannot be moved by winding the wall clock — which is what makes a
 * for-time record unfakeable (`03-player.md` §B.5, §E.4). `Handler.postDelayed`'s `uptimeMillis`
 * timebase stops during deep sleep, so a session backgrounded on a bedside table would silently
 * under-count; that is the bug this type exists to make unrepresentable.
 *
 * **Nothing here reads a clock.** Every method takes `nowElapsedMs`, so the whole of the pause /
 * resume / seek / reboot story is a JUnit test rather than a stopwatch and a device — the same bargain
 * `Timeline.stateAt` makes one layer down. The host is the single place `SystemClock` is touched.
 *
 * *Rejected* — accumulating elapsed time on the 50ms tick. It is the obvious implementation and it is
 * wrong in exactly the cases that matter: a dropped frame loses a tick, a doze loses thousands, and
 * neither is recoverable because there is nothing to compare against. Elapsed time is
 * `now − origin − pausedAccumulated`, evaluated fresh, always.
 *
 * @param resumesAtElapsedMs a resume that has been *scheduled* but has not happened yet — the three
 *   seconds of 支度 back into a session (§C.1 rows 19 and 30). The clock stays frozen until this
 *   instant and then continues from exactly where it stopped, so the countdown is charged to nobody.
 *   A plain resume would charge those three seconds to the station the user has not restarted yet.
 *
 *   It is deliberately **not** persisted: [PersistedClock] has no column for it, and the honest
 *   recovery from a process death during the countdown is the pause it is still inside. The user then
 *   answers the resume prompt again, which is the same question they were three seconds from answering.
 */
data class SessionClock(
    val startedAtElapsedMs: Long,
    val pausedAccumulatedMs: Long = 0L,
    val pausedAtElapsedMs: Long? = null,
    val pausedAtWallMs: Long? = null,
    val resumesAtElapsedMs: Long? = null,
    val bootAnchorMs: Long,
    val anchorWallMs: Long,
) {

    val paused: Boolean get() = pausedAtElapsedMs != null

    /**
     * Session-elapsed milliseconds — the one number the whole player is derived from.
     *
     * One expression covers running, paused, and paused-with-a-scheduled-resume, and that is worth
     * stating because the three-case version of it is where this normally goes wrong. While paused,
     * the accumulated pause grows exactly as fast as `now`, so the difference is constant and the
     * clock is frozen; once [resumesAtElapsedMs] passes, the accumulation stops there and the same
     * subtraction starts moving again from the value it froze at.
     */
    fun elapsedMs(nowElapsedMs: Long): Long {
        val pausedAt = pausedAtElapsedMs ?: return (nowElapsedMs - startedAtElapsedMs - pausedAccumulatedMs)
            .coerceAtLeast(0L)
        val frozenUntil = minOf(nowElapsedMs, resumesAtElapsedMs ?: nowElapsedMs)
        val accumulated = pausedAccumulatedMs + (frozenUntil - pausedAt).coerceAtLeast(0L)
        return (nowElapsedMs - startedAtElapsedMs - accumulated).coerceAtLeast(0L)
    }

    /** How long the current pause has lasted, in real time. Null when the clock is running. */
    fun pausedForMs(nowElapsedMs: Long): Long =
        pausedAtElapsedMs?.let { (nowElapsedMs - it).coerceAtLeast(0L) } ?: 0L

    /** Whether a scheduled resume is still in front of us — the 支度 countdown is on screen. */
    fun resumePendingMs(nowElapsedMs: Long): Long =
        resumesAtElapsedMs?.let { (it - nowElapsedMs).coerceAtLeast(0L) } ?: 0L

    /**
     * ┃┃. Idempotent: pausing an already-paused clock keeps the original anchor.
     *
     * That is not defensive coding — §C.1 row 20 is reachable *from* PAUSED, and a second anchor there
     * would overwrite the instant §A PAUSED's data-out depends on and credit the deciding time as
     * training.
     */
    fun pause(nowElapsedMs: Long, nowWallMs: Long): SessionClock =
        if (paused) this else copy(pausedAtElapsedMs = nowElapsedMs, pausedAtWallMs = nowWallMs)

    /** 続ける, now. Collapses the pause into [pausedAccumulatedMs] and clears both anchors. */
    fun resume(nowElapsedMs: Long): SessionClock {
        val pausedAt = pausedAtElapsedMs ?: return this
        val frozenUntil = minOf(nowElapsedMs, resumesAtElapsedMs ?: nowElapsedMs)
        return copy(
            pausedAccumulatedMs = pausedAccumulatedMs + (frozenUntil - pausedAt).coerceAtLeast(0L),
            pausedAtElapsedMs = null,
            pausedAtWallMs = null,
            resumesAtElapsedMs = null,
        )
    }

    /**
     * 続ける in [delayMs] — the countdown back in.
     *
     * Pauses first when the clock is running, because that is the state a resume-after has to hold:
     * §C.1 row 30 reaches this on a session that was killed while running, where there is no pause to
     * extend and the three seconds would otherwise be charged to the interrupted station.
     */
    fun resumeAfter(nowElapsedMs: Long, nowWallMs: Long, delayMs: Long): SessionClock =
        pause(nowElapsedMs, nowWallMs).copy(resumesAtElapsedMs = nowElapsedMs + delayMs)

    /**
     * Moves the clock so that `elapsedMs(now)` reads [targetElapsedMs] — ◁'s restart, ▷'s jump over
     * 支度.
     *
     * The origin moves; the pause does not. §A PAUSED is explicit that **skip-while-paused stays
     * paused**, so a seek issued from 休止 leaves the clock frozen at its new position rather than
     * starting it under a user who deliberately stopped it.
     */
    fun seekTo(targetElapsedMs: Long, nowElapsedMs: Long): SessionClock {
        val accumulated = pausedAtElapsedMs?.let { pausedAt ->
            val frozenUntil = minOf(nowElapsedMs, resumesAtElapsedMs ?: nowElapsedMs)
            pausedAccumulatedMs + (frozenUntil - pausedAt).coerceAtLeast(0L)
        } ?: pausedAccumulatedMs
        return copy(startedAtElapsedMs = nowElapsedMs - targetElapsedMs - accumulated)
    }

    /**
     * Re-anchors a clock read back from disk into this boot — `03-player.md` §E.2 step 3.
     *
     * **The origin is kept, deliberately.** §E.3: within a boot `startedAtElapsedMs` is still valid,
     * so nothing needs rewriting, and a session killed while *running* therefore returns with the
     * wall-clock gap counted as active time. That is not an oversight — it is why the resume prompt
     * exists at all: 続ける accepts the gap, and 記録する takes the conservative last-transition figure
     * instead. Only the two witness values are refreshed, so the next reboot check compares against
     * this boot.
     *
     * The clock is also **frozen at [nowElapsedMs] if it was running**, because every caller of this
     * is about to put three seconds of 支度 in front of the user (§C.1 row 30), and those three
     * seconds belong to nobody.
     */
    fun reanchor(nowElapsedMs: Long, nowWallMs: Long): SessionClock = copy(
        bootAnchorMs = nowWallMs - nowElapsedMs,
        anchorWallMs = nowWallMs,
    ).pause(nowElapsedMs, nowWallMs)

    /** The row the store persists. [resumesAtElapsedMs] has no column and is dropped — see the KDoc. */
    fun persisted(): PersistedClock = PersistedClock(
        startedAtElapsedMs = startedAtElapsedMs,
        pausedAccumulatedMs = pausedAccumulatedMs,
        pausedAtElapsedMs = pausedAtElapsedMs,
        pausedAtWallMs = pausedAtWallMs,
        bootAnchorMs = bootAnchorMs,
        anchorWallMs = anchorWallMs,
    )

    companion object {

        /**
         * The clock as the session row last recorded it.
         *
         * Used for **both** a fresh start and a resume, which is the simplification that removes an
         * entire class of bug: `startSession` already stamped `started_at_elapsed` at the INSERT, so a
         * session opened a moment ago reads back as "a few milliseconds into 支度" with no separate
         * start path to keep in step. There is no second place a session's origin can come from.
         */
        fun from(persisted: PersistedClock): SessionClock = SessionClock(
            startedAtElapsedMs = persisted.startedAtElapsedMs,
            pausedAccumulatedMs = persisted.pausedAccumulatedMs,
            pausedAtElapsedMs = persisted.pausedAtElapsedMs,
            pausedAtWallMs = persisted.pausedAtWallMs,
            bootAnchorMs = persisted.bootAnchorMs,
            anchorWallMs = persisted.anchorWallMs,
        )
    }
}
