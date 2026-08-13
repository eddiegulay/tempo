package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.gym.EffectiveGymPreferences
import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.SpeechAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cue engine as a **sequence**, which is the only way its two worst failures are visible.
 *
 * `CueScheduleTest` pins the arithmetic — which cues, at which offsets, sharing which windows — and it
 * passed throughout the entire life of a bug that held the user's audio focus for twenty minutes. It
 * had to: the window's numbers were right. What was wrong was the *order of operations across a
 * transition*, and an assertion about a `FocusSpan` cannot see an order.
 *
 * So these tests run a fake clock and count what the speaker was actually asked for. The two they
 * exist for:
 *
 * 1. **A window opened for segment N must still close after segment N+1 has cancelled N's cues.**
 *    Every merged window in §D.4 closes *after* its segment ends, so the close is always in flight at
 *    the moment of the transition — and a close posted on the segment's own token is deleted by it.
 * 2. **Wanting speech must be enough to probe the engine.** Arming on *availability* is a deadlock:
 *    availability only resolves inside the probe.
 */
class CueEngineTest {

    // ── fakes ───────────────────────────────────────────────────────────────────────────────────

    /**
     * A [CuePoster] on a clock the test moves by hand.
     *
     * Insertion order breaks ties so two posts due on the same millisecond run in the order they were
     * made, which is what `Handler` does and what the merged-window arithmetic assumes.
     */
    private class FakeCuePoster : CuePoster {

        private class Post(val token: Any, val atMs: Long, val seq: Long, val action: () -> Unit)

        private val queue = mutableListOf<Post>()
        private var seq = 0L

        var nowMs = 0L
            private set

        override fun post(token: Any, delayMs: Long, action: () -> Unit) {
            queue += Post(token, nowMs + delayMs, seq++, action)
        }

        override fun cancel(token: Any) {
            queue.removeAll { it.token === token }
        }

        override fun cancelAll() {
            queue.clear()
        }

        /** Runs everything due up to [targetMs], including anything those actions post on the way. */
        fun advanceTo(targetMs: Long) {
            while (true) {
                val next = queue.filter { it.atMs <= targetMs }
                    .minWithOrNull(compareBy({ it.atMs }, { it.seq })) ?: break
                queue.remove(next)
                nowMs = next.atMs
                next.action()
            }
            nowMs = targetMs
        }
    }

    /**
     * Counts windows the way [GymTones] does — including the part that matters, that the delayed close
     * lives on the sink's **own** token and no transition can cancel it.
     */
    private class FakeTones(private val poster: FakeCuePoster) : ToneSink {

        private val windowToken = Any()

        /** The refcount. Anything other than zero between cues is a duck the user is still under. */
        var openWindows = 0
            private set

        var opens = 0
            private set

        val played = mutableListOf<TonePattern>()

        override fun openWindow() {
            opens++
            openWindows++
        }

        override fun closeWindow(afterMs: Long) {
            if (afterMs <= 0L) dropWindow() else poster.post(windowToken, afterMs) { dropWindow() }
        }

        override fun play(pattern: TonePattern?) {
            pattern?.let { played += it }
        }

        override fun cancelPendingTones() = Unit

        override fun stopAll() {
            poster.cancel(windowToken)
            openWindows = 0
        }

        override fun release() = stopAll()

        private fun dropWindow() {
            if (openWindows > 0) openWindows--
        }
    }

    private class FakeSpeech : SpeechSink {

        override var availability: SpeechAvailability = SpeechAvailability.NoEngine
            private set

        override var onAvailabilityChanged: (SpeechAvailability) -> Unit = {}

        var prepareCount = 0
            private set

        val spokenIds = mutableListOf<String>()

        override fun prepare() {
            prepareCount++
        }

        override fun speak(text: String, utteranceId: String) {
            spokenIds += utteranceId
        }

        override fun stop() = Unit

        override fun shutdown() = Unit

        /** What the TTS engine's init callback does, once it has an answer. */
        fun engineAnswers(answer: SpeechAvailability) {
            availability = answer
            onAvailabilityChanged(answer)
        }
    }

    private class FakeHaptics : HapticSink {
        override val available: Boolean = true
        val played = mutableListOf<Cue>()
        var cancels = 0
            private set

        override fun play(cue: Cue) {
            played += cue
        }

        override fun cancel() {
            cancels++
        }
    }

    private class Harness {
        val clock = FakeCuePoster()
        val tones = FakeTones(clock)
        val speech = FakeSpeech()
        val haptics = FakeHaptics()
        val availabilityAnswers = mutableListOf<SpeechAvailability>()
        val engine = CueEngine(
            haptics = haptics,
            tones = tones,
            speech = speech,
            poster = clock,
            onSpeechAvailabilityChanged = { availabilityAnswers += it },
        )
    }

    private fun harness(
        haptics: Boolean = false,
        tones: Boolean = true,
        speech: Boolean = false,
    ): Harness = Harness().also {
        it.engine.arm(CueSettings(haptics = haptics, tones = tones, speech = speech), wantsSpeech = speech)
    }

    private fun segment(ordinal: Int, plannedMs: Long, phase: Phase = Phase.WORK) =
        CueSegment(ordinal = ordinal, phase = phase, plannedMs = plannedMs)

    // ── the focus window across a transition ────────────────────────────────────────────────────

    @Test
    fun `a window opened for one segment still closes after the next segment cancelled its cues`() {
        // §D.4: focus is "never held for the whole session, or the user's music stops for twenty
        // minutes". The merged window is open T-3150 .. T+450, so at the transition its close is still
        // 450ms away — and the transition's first act is to cancel the leaving segment's key set. When
        // the close rode that key set it was deleted here, and the count never came back to zero.
        val h = harness()
        h.engine.enterSegment(segment(ordinal = 0, plannedMs = 30_000))
        h.clock.advanceTo(30_000)
        assertEquals(1, h.tones.openWindows) // the 3-2-1's window, still open at the boundary

        h.engine.enterSegment(segment(ordinal = 1, plannedMs = 30_000, phase = Phase.REST))
        h.clock.advanceTo(31_000)

        assertEquals(0, h.tones.openWindows)
    }

    @Test
    fun `four segments back to back leave the focus count exactly where they found it`() {
        // The shape of the leak: monotonically non-decreasing. One stranded window is a duck the user
        // never gets out of; four is the same duck, and neither shows up in any arithmetic assertion.
        val h = harness()
        repeat(4) { i ->
            h.engine.enterSegment(segment(ordinal = i, plannedMs = 30_000))
            h.clock.advanceTo(30_000L * (i + 1))
        }
        h.clock.advanceTo(30_000L * 4 + 1_000)

        // Two per 30s WORK segment: 半分's own −150/+350, and the merged 3-2-1 + interval end.
        assertEquals(8, h.tones.opens)
        assertEquals(0, h.tones.openWindows)
    }

    @Test
    fun `a skip out of a segment mid-window still lifts the duck`() {
        // §D.7's skip row keeps the window deliberately — the segment being landed in wants it — but
        // "keep" means the open one is handed on, not that its close is thrown away.
        val h = harness()
        h.engine.enterSegment(segment(ordinal = 0, plannedMs = 30_000))
        h.clock.advanceTo(27_500)
        assertEquals(1, h.tones.openWindows)

        h.engine.handle(CueEvent.SKIP, CueState(phase = Phase.WORK))
        h.engine.enterSegment(segment(ordinal = 1, plannedMs = 30_000, phase = Phase.REST))
        h.clock.advanceTo(31_000)

        assertEquals(0, h.tones.openWindows)
    }

    @Test
    fun `a pause zeroes the count outright rather than closing one window`() {
        // §D.7's closeWindow(0) cell. A pause that decremented would leave the user's music ducked
        // until a segment that will never resume closed the rest.
        val h = harness()
        h.engine.enterSegment(segment(ordinal = 0, plannedMs = 30_000))
        h.clock.advanceTo(27_500)

        h.engine.handle(CueEvent.PAUSE, CueState(phase = Phase.WORK))

        assertEquals(0, h.tones.openWindows)
    }

    @Test
    fun `landing past a window's close asks for no duck at all`() {
        // §E.5's resume: the frontier moved while backgrounded. Requesting focus for a window whose
        // sound has already been missed is a duck with nothing under it.
        val h = harness()
        h.engine.enterSegment(segment(ordinal = 0, plannedMs = 30_000), elapsedInSegmentMs = 29_000)
        h.clock.advanceTo(60_000)

        assertEquals(1, h.tones.opens) // the tail of the merged window, and only that
        assertEquals(0, h.tones.openWindows)
    }

    // ── arming the speech channel ───────────────────────────────────────────────────────────────

    @Test
    fun `wanting speech probes the engine even though nothing yet says the engine works`() {
        // The deadlock this replaces: armCues sets speech only when availability == Available;
        // availability only resolves inside prepare(); prepare() was only called when speech was
        // already true. Nothing could make the first move, so §D.6's whole channel was dead on
        // arrival — 半分 / 最後の巡 / 時間切れ / 終わり, and DECISIONS.md §Q2's TalkBack auto-enable
        // with it. We probe on INTENT.
        val h = Harness()
        h.engine.arm(
            CueSettings(haptics = false, tones = true, speech = false), // availability is still NoEngine
            wantsSpeech = true,
        )

        assertEquals(1, h.speech.prepareCount)
    }

    @Test
    fun `not wanting speech never binds the engine`() {
        // A TextToSpeech binds to another process; a launcher whose user never turns speech on must
        // never hold that binding.
        val h = Harness()
        h.engine.arm(CueSettings(haptics = true, tones = true, speech = false), wantsSpeech = false)

        assertEquals(0, h.speech.prepareCount)
    }

    @Test
    fun `the engine's answer reaches the owner, which is what makes a second arm possible`() {
        val h = Harness()
        val prefs = EffectiveGymPreferences(
            stored = GymPreferences(haptics = false, tones = true, speech = true),
            touchExplorationEnabled = false,
        )

        h.engine.arm(prefs)
        assertEquals(1, h.speech.prepareCount)
        assertFalse("nothing may be spoken before the engine has answered", h.engine.settings.speech)

        h.speech.engineAnswers(SpeechAvailability.Available)
        assertEquals(listOf(SpeechAvailability.Available), h.availabilityAnswers)

        h.engine.arm(prefs) // what the owner does on that callback
        assertTrue(h.engine.settings.speech)
    }

    @Test
    fun `a TalkBack session arms speech without the stored preference being on`() {
        // DECISIONS.md §Q2: enabled for this session only, and the engine reads the in-force value.
        val h = Harness()
        val prefs = EffectiveGymPreferences(
            stored = GymPreferences(speech = false),
            touchExplorationEnabled = true,
        )

        h.engine.arm(prefs)
        assertEquals(1, h.speech.prepareCount)

        h.speech.engineAnswers(SpeechAvailability.Available)
        h.engine.arm(prefs)

        assertTrue(h.engine.settings.speech)
    }

    @Test
    fun `a device with no Japanese voice arms everything except speech, silently`() {
        // §D.6: fall back to tones and never prompt for a voice download mid-workout.
        val h = Harness()
        val prefs = EffectiveGymPreferences(
            stored = GymPreferences(haptics = true, tones = true, speech = true),
            touchExplorationEnabled = false,
        )
        h.engine.arm(prefs)
        h.speech.engineAnswers(SpeechAvailability.NoJapaneseVoice)
        h.engine.arm(prefs)

        assertTrue(h.engine.settings.tones)
        assertFalse(h.engine.settings.speech)
    }

    // ── utterance identity ──────────────────────────────────────────────────────────────────────

    @Test
    fun `re-entering a segment does not reuse the utterance id it spoke the first time`() {
        // A ◁ single-tap restart and a ＋二十秒 re-schedule both re-enter the same ordinal. An id that
        // repeats verbatim asks the speech channel for a second focus window under a key it already
        // holds, and only one terminal callback can ever give it back.
        val h = harness(tones = true, speech = true)
        val seg = CueSegment(ordinal = 4, phase = Phase.WORK, plannedMs = 30_000, startsFinalRound = true)

        h.engine.enterSegment(seg)
        h.clock.advanceTo(0)
        h.engine.enterSegment(seg)
        h.clock.advanceTo(1)

        assertEquals(2, h.speech.spokenIds.size)
        assertNotEquals(h.speech.spokenIds[0], h.speech.spokenIds[1])
    }

    @Test
    fun `a silent engine schedules nothing at all`() {
        val h = Harness() // never armed: CueSettings.Silent
        h.engine.enterSegment(segment(ordinal = 0, plannedMs = 30_000))
        h.clock.advanceTo(60_000)

        assertEquals(0, h.tones.opens)
        assertEquals(0, h.haptics.played.size)
    }
}
