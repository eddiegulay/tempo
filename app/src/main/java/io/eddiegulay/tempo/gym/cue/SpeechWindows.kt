package io.eddiegulay.tempo.gym.cue

/**
 * The bookkeeping behind "speech owns its own audio-focus window" (`03-player.md` §D.6), extracted
 * from [GymSpeech] because it is the part that can be wrong and the part that needs no engine.
 *
 * One utterance id, one window. That sentence is the whole invariant, and it used to be broken by a
 * single line: [GymSpeech.speak] added the id to a `Set` and then opened a window unconditionally.
 * Scheduled cues use `"${cue.name}@s${ordinal}"`, which repeats **verbatim** whenever a segment is
 * re-entered — a `◁` single-tap restart, a ＋二十秒 re-schedule — so the second `speak` opened a second
 * window that only one `onDone` could ever close. A stranded reference count is a duck that never
 * lifts: the user's music stays quiet until the session ends.
 *
 * So the *set membership* is authoritative, not the call. [open] returns whether a window was actually
 * taken, [close] whether one was actually given back, and the caller does exactly what it is told.
 *
 * *Rejected* — making the ids unique per `speak` instead. It works, and it hides the same bug one layer
 * down: with unique ids the count is only balanced as long as every id gets a terminal callback, and
 * `QUEUE_FLUSH` plus a binder-thread listener is precisely where that assumption fails. A counter that
 * cannot go wrong beats ids that must not collide. (The engine makes its ids unique anyway — belt and
 * braces, and it costs one integer.)
 */
class SpeechWindows(
    private val onOpen: () -> Unit,
    private val onClose: () -> Unit,
) {

    private val pending = mutableSetOf<String>()

    /** How many windows this counter believes it holds. Exposed so a test can state the invariant. */
    val heldCount: Int get() = pending.size

    /**
     * Takes a window for [utteranceId] unless one is already held for it.
     *
     * @return true when a window was opened, so the caller can abandon it again on a failed `speak`.
     */
    fun open(utteranceId: String): Boolean {
        if (!pending.add(utteranceId)) return false
        onOpen()
        return true
    }

    /** Gives back [utteranceId]'s window. A second terminal callback for the same id is a no-op. */
    fun close(utteranceId: String) {
        if (pending.remove(utteranceId)) onClose()
    }

    /**
     * Gives back every window at once — `tts.stop()`, where the terminal callbacks for the flushed
     * utterances may or may not arrive and waiting to find out is how the duck gets stuck.
     */
    fun drain() {
        if (pending.isEmpty()) return
        val count = pending.size
        pending.clear()
        repeat(count) { onClose() }
    }
}
