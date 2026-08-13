package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.gym.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every cell of `03-player.md` §D.7's disarm matrix.
 *
 * Five events × four channels, and each cell is a way to leave something running that the user cannot
 * see: a 2060ms count-down waveform buzzing in a paused hand, a duck held over a podcast until the app
 * is killed, a 「半分」 arriving forty seconds after the half. The matrix is a table in the spec because
 * its rows disagree in ways that only line up side by side — a skip keeps the window a pause drops, a
 * completion waits where a pause does not, and `ON_STOP` ignores preferences that every other row
 * obeys. Testing it cell by cell is the only way those disagreements stay deliberate.
 */
class CueDisarmTest {

    private val allOn = CueSettings(haptics = true, tones = true, speech = true)
    private val allOff = CueSettings.Silent
    private val live = CueState(Phase.WORK)

    // ── Row: pause ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `pause silences all four channels at once`() {
        val plan = disarmPlan(CueEvent.PAUSE, allOn, live)
        assertTrue(plan.cancelHaptics)
        assertEquals(0L, plan.cancelHapticsAfterMs)
        assertTrue(plan.cancelPending)
        assertEquals(ToneDisarm.CLOSE_WINDOW, plan.tones)
        assertEquals(0L, plan.toneDelayMs)
        assertTrue(plan.stopSpeech)
        assertFalse(plan.rearm)
    }

    @Test
    fun `pause with every channel off has nothing to cancel`() {
        // A channel that was never armed must not be torn down anyway; doing so would run ON_STOP-
        // shaped teardown on every pause of a session that makes no sound at all.
        val plan = disarmPlan(CueEvent.PAUSE, allOff, live)
        assertFalse(plan.cancelHaptics)
        assertEquals(ToneDisarm.KEEP, plan.tones)
        assertFalse(plan.stopSpeech)
        assertTrue(plan.cancelPending) // posts exist regardless of which channel would have played
    }

    // ── Row: quit sheet ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `opening the quit sheet disarms exactly as a pause does`() {
        // Two constants, one behaviour: the sheet's silence is part of "the clock pauses the instant
        // the sheet opens". A sheet that keeps cueing while you decide produces a record of deciding.
        assertEquals(
            disarmPlan(CueEvent.PAUSE, allOn, live),
            disarmPlan(CueEvent.QUIT_SHEET, allOn, live),
        )
    }

    // ── Row: skip forward / back ────────────────────────────────────────────────────────────────

    @Test
    fun `a skip drops the pending cues but leaves the motor and the duck alone`() {
        val plan = disarmPlan(CueEvent.SKIP, allOn, live)
        assertFalse(plan.cancelHaptics) // the skip itself is often acknowledged by a haptic
        assertTrue(plan.cancelPending)
        assertEquals(ToneDisarm.KEEP, plan.tones) // the segment we land in will want the window
        assertTrue(plan.stopSpeech)
        assertTrue(plan.rearm)
    }

    @Test
    fun `skipping into COMPLETE does not re-arm anything`() {
        // ▷ on the last segment lands on 記録, which schedules nothing — re-arming there would post
        // cues for a segment that no longer exists.
        val plan = disarmPlan(CueEvent.SKIP, allOn, CueState(Phase.COMPLETE))
        assertTrue(plan.cancelPending)
        assertFalse(plan.rearm)
    }

    @Test
    fun `a skip with speech off has no utterance to interrupt`() {
        assertFalse(disarmPlan(CueEvent.SKIP, allOn.copy(speech = false), live).stopSpeech)
    }

    // ── Row: ON_STOP ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `backgrounding releases the generator rather than merely closing the window`() {
        val plan = disarmPlan(CueEvent.ON_STOP, allOn, live)
        assertTrue(plan.cancelHaptics)
        assertTrue(plan.cancelPending)
        assertEquals(ToneDisarm.RELEASE, plan.tones)
        assertTrue(plan.stopSpeech)
        assertFalse(plan.rearm)
    }

    @Test
    fun `backgrounding ignores the preferences entirely`() {
        // The one row that does not consult them, and deliberately: it is teardown, not a channel
        // decision. An engine armed a moment before the user switched a channel off still holds a
        // ToneGenerator, which holds an AudioTrack, which is how this component becomes a battery bug.
        val plan = disarmPlan(CueEvent.ON_STOP, allOff, live)
        assertTrue(plan.cancelHaptics)
        assertEquals(ToneDisarm.RELEASE, plan.tones)
        assertTrue(plan.stopSpeech)
    }

    // ── Row: COMPLETE ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a completed session gets its ceremony before anything is cancelled`() {
        val plan = disarmPlan(CueEvent.COMPLETE, allOn, CueState(Phase.COMPLETE, sessionComplete = true))
        assertTrue(plan.cancelHaptics)
        assertEquals(600L, plan.cancelHapticsAfterMs) // the completion cue's own one-shot length
        assertTrue(plan.cancelPending)
        assertEquals(ToneDisarm.CLOSE_WINDOW, plan.tones)
        assertEquals(900L, plan.toneDelayMs) // §D.2's own +900ms focus tail
        assertFalse(plan.rearm)
    }

    @Test
    fun `the last word of a session is allowed to finish`() {
        // Every other row stops speech. This one does not: 「終わり」 is the one moment of ceremony the
        // feature has, and truncating it to tidy up is worse than the tidying is worth.
        assertFalse(disarmPlan(CueEvent.COMPLETE, allOn, CueState(Phase.COMPLETE)).stopSpeech)
    }

    @Test
    fun `a partial finish waits for nothing, because no ceremony fires`() {
        // §D.2: a partial session gets REP_DONE alone. Waiting 600ms for a buzz that never happens
        // holds the vibrator open over silence, and 900ms of duck over the same.
        val plan = disarmPlan(CueEvent.COMPLETE, allOn, CueState(Phase.COMPLETE, sessionComplete = false))
        assertTrue(plan.cancelHaptics)
        assertEquals(0L, plan.cancelHapticsAfterMs)
        assertEquals(0L, plan.toneDelayMs)
    }

    @Test
    fun `a completion with haptics off does not wait for a buzz it never queued`() {
        val plan = disarmPlan(CueEvent.COMPLETE, allOn.copy(haptics = false), CueState(Phase.COMPLETE))
        assertFalse(plan.cancelHaptics)
        assertEquals(0L, plan.cancelHapticsAfterMs)
    }

    @Test
    fun `a completion with tones off keeps a window it never opened`() {
        val plan = disarmPlan(CueEvent.COMPLETE, allOn.copy(tones = false), CueState(Phase.COMPLETE))
        assertEquals(ToneDisarm.KEEP, plan.tones)
        assertEquals(0L, plan.toneDelayMs)
    }

    // ── The whole matrix ────────────────────────────────────────────────────────────────────────

    @Test
    fun `every event cancels its pending posts, and only a skip re-arms`() {
        // The one column with no exception, and the one with exactly one. Stated together because a
        // future row that forgot either would leave cues firing for a segment nobody is in.
        for (event in CueEvent.entries) {
            assertTrue(event.name, disarmPlan(event, allOn, live).cancelPending)
            assertEquals(event.name, event == CueEvent.SKIP, disarmPlan(event, allOn, live).rearm)
        }
    }
}
