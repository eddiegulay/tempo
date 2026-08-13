package io.eddiegulay.tempo.gym.cue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One utterance id, one focus window — the invariant `GymSpeech` used to state and not enforce.
 *
 * The failure is the same class as the tone channel's stranded window and it is just as inaudible
 * until it is unbearable: an unbalanced count is a duck that never lifts, so the user's music stays
 * quiet for the rest of the session and they blame the music app.
 */
class SpeechWindowsTest {

    private var opened = 0
    private var closed = 0
    private val windows = SpeechWindows(onOpen = { opened++ }, onClose = { closed++ })

    @Test
    fun `an utterance takes one window and gives it back`() {
        windows.open("HALFWAY@s4")
        assertEquals(1, opened)
        windows.close("HALFWAY@s4")
        assertEquals(1, closed)
        assertEquals(0, windows.heldCount)
    }

    @Test
    fun `the same id spoken twice takes one window, not two`() {
        // A ◁ single-tap restart and a ＋二十秒 re-schedule both re-enter a segment, and a scheduled
        // cue's id is "${cue.name}@s${ordinal}" — the same string, verbatim. Opening unconditionally
        // took a second window that only one terminal callback could ever return.
        assertTrue(windows.open("INTERVAL_END@s4"))
        assertFalse(windows.open("INTERVAL_END@s4"))

        assertEquals(1, opened)

        windows.close("INTERVAL_END@s4")
        assertEquals(1, closed)
        assertEquals(0, windows.heldCount)
    }

    @Test
    fun `a terminal callback that arrives twice returns one window, not two`() {
        // onDone and onStop can both land for a flushed utterance. A count that goes negative lifts a
        // duck someone else is still inside.
        windows.open("LAST_ROUND@s2")
        windows.close("LAST_ROUND@s2")
        windows.close("LAST_ROUND@s2")

        assertEquals(1, closed)
    }

    @Test
    fun `a terminal callback for an id nobody opened is ignored`() {
        windows.close("EMOM_FAIL@s9")

        assertEquals(0, closed)
    }

    @Test
    fun `draining gives back exactly what is held`() {
        // tts.stop(): the flushed utterances' callbacks may or may not arrive, and waiting to find out
        // is how the duck gets stuck.
        windows.open("HALFWAY@s1")
        windows.open("LAST_ROUND@s2")
        windows.drain()

        assertEquals(2, closed)
        assertEquals(0, windows.heldCount)

        windows.drain()
        assertEquals(2, closed)
    }
}
