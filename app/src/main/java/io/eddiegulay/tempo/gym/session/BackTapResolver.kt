package io.eddiegulay.tempo.gym.session

/**
 * What a ◁ tap means, which depends entirely on whether it was the second one.
 *
 * `03-player.md` §C.4. [RESTART_SEGMENT] is the common case and the safe one — you fumbled the first
 * rep and want the interval back — while [PREVIOUS_SEGMENT] is destructive: it deletes the previous
 * segment's stored result before re-seeking, or the record counts that station twice.
 */
enum class BackAction { RESTART_SEGMENT, PREVIOUS_SEGMENT }

/**
 * Pure. First tap restarts the current segment; a second within the window steps back one.
 *
 * Verbatim from `03-player.md` §C.4, including the consume-on-use: a third tap starts a fresh cycle
 * rather than walking backwards a segment per tap. Walking is what a user with a shaking hand does by
 * accident, and each step back destroys a result.
 *
 * *Rejected* — a single ◁ that steps straight back, matching a media player. A media player's back
 * is free; this one deletes work. The double tap is the confirmation, and the first tap is a useful
 * action in its own right rather than a speed bump invented to slow the second one down.
 *
 * *Rejected* — resolving this against `System.currentTimeMillis()` inside the class. The caller
 * passes the instant, so the two-second window is testable without sleeping for two seconds.
 *
 * Not thread-safe, and does not need to be: taps arrive on the main thread.
 */
class BackTapResolver(private val windowMs: Long = 2_000) {

    private var lastTapAt: Long? = null

    fun resolve(nowMs: Long): BackAction {
        val prev = lastTapAt
        lastTapAt = nowMs
        return if (prev != null && nowMs - prev <= windowMs) {
            lastTapAt = null // consume, so a third tap restarts the cycle
            BackAction.PREVIOUS_SEGMENT
        } else {
            BackAction.RESTART_SEGMENT
        }
    }

    /**
     * Forgets the armed tap. Called when the segment changes underneath the user: a ◁ armed on the
     * plank must not step back out of the burpee that replaced it two seconds later.
     */
    fun disarm() {
        lastTapAt = null
    }
}
