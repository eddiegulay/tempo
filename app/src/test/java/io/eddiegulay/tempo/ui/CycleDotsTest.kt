package io.eddiegulay.tempo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line past which a row of dots stops being a count, so the caller knows when to say "十二巡" in
 * words instead — and the degenerate totals a decoration must survive.
 *
 * The stakes are not cosmetic on either half. A predicate that answers "dots" for a twelve-round
 * routine renders a face the spec forbids, and a total the composable cannot survive turns a
 * decoration into a crash in the middle of a live session.
 */
class CycleDotsTest {

    @Test
    fun `nine dots still count and ten become words`() {
        // 03-player.md :172's rule exactly. Nine is the last countable row, not the first uncountable.
        assertFalse(cycleDotsOverflow(9))
        assertTrue(cycleDotsOverflow(10))
    }

    @Test
    fun `a twelve-round routine renders as text, not as dots that happen to fit`() {
        // The rejected width predicate admitted seventeen dots at the player's 6.dp geometry, so this
        // case answered "dots" and drew the face the spec says must be words.
        assertTrue(cycleDotsOverflow(12))
    }

    @Test
    fun `a short cycle is never an overflow`() {
        assertFalse(cycleDotsOverflow(1))
        assertFalse(cycleDotsOverflow(4)) // Focus's Pomodoro face.
    }

    @Test
    fun `an empty cycle is not an overflow`() {
        // A routine with zero rounds is a data bug, not a layout one — it must not report overflow
        // and send the caller into a "〇巡" text fallback. The composable draws nothing for it.
        assertFalse(cycleDotsOverflow(0))
    }

    @Test
    fun `a negative total is not an overflow either`() {
        assertFalse(cycleDotsOverflow(-3))
    }

    @Test
    fun `a negative total fills nothing instead of throwing`() {
        // A bare coerceIn(0, total) throws here, because it requires min <= max. A decoration must
        // not take down the session it was decorating.
        assertEquals(0, cycleDotsFilled(total = -3, filled = 0))
        assertEquals(0, cycleDotsFilled(total = 0, filled = 4))
    }

    @Test
    fun `filled past total fills the row rather than overdrawing it`() {
        // A resumed session counting past the cycle shows a full row, not a sixth dot in a row of five.
        assertEquals(5, cycleDotsFilled(total = 5, filled = 7))
    }

    @Test
    fun `a negative filled count earns nothing`() {
        assertEquals(0, cycleDotsFilled(total = 5, filled = -2))
    }

    @Test
    fun `an in-range count passes through untouched`() {
        assertEquals(3, cycleDotsFilled(total = 5, filled = 3))
    }
}
