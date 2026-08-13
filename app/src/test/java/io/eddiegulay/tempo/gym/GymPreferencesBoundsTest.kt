package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three bounds `GYM.SETTINGS` writes and the player reads.
 *
 * They are pinned here because they are enforced in two directions and both are load-bearing. On the
 * way in a wheel could offer a value the store then clamps away, leaving a row showing something that
 * was never persisted — the one thing `01-shell.md` §B says a settings row must never do. On the way
 * out a value written by an older build or restored from a backup reaches the timeline compiler
 * unchecked, and an out-of-range 支度 is not a cosmetic bug: it is a workout that never starts.
 */
class GymPreferencesBoundsTest {

    @Test
    fun `支度 is capped at fifteen seconds and floored at zero`() {
        assertEquals(15, clampPrepareSeconds(60))
        assertEquals(0, clampPrepareSeconds(-1))
    }

    @Test
    fun `a zero-second 支度 is legal and survives the clamp`() {
        // Zero is a real choice, not "off". The compiler must then emit NO 支度 segment rather than a
        // zero-length one, because a zero-duration segment divides by zero in the ensō sweep.
        assertEquals(0, clampPrepareSeconds(0))
    }

    @Test
    fun `an in-range value passes through untouched`() {
        assertEquals(5, clampPrepareSeconds(5)) // The default.
        assertEquals(15, clampStationRest(15))
        assertEquals(60, clampRoundRest(60))
    }

    @Test
    fun `the between-stations rest tops out at five minutes`() {
        assertEquals(300, clampStationRest(301))
        assertEquals(0, clampStationRest(-30))
    }

    @Test
    fun `the between-rounds rest tops out at ten minutes`() {
        // Twice the station bound, because a rest between rounds legitimately is: シンディ rests
        // between stations in seconds and between rounds in minutes.
        assertEquals(600, clampRoundRest(9999))
        assertEquals(0, clampRoundRest(-1))
    }
}
