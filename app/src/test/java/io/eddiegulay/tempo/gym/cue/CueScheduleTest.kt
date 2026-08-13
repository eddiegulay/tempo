package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which cues a segment fires, when, and how many times the speaker is asked to duck for them.
 *
 * Every failure this pins is inaudible-until-it-is-annoying: a 3-2-1 on a two-second station, a 半分
 * announced during a rest, an interval end fired on the last segment so the ceremony arrives second,
 * or four separate ducks inside three seconds, which sounds like the music stuttering rather than like
 * a cue. None of them fails a build and none of them shows in a screenshot.
 */
class CueScheduleTest {

    /** The schedule resolves fixed phrases against a table now; Japanese is what these tests pin. */
    private fun cueSchedule(segment: CueSegment) = cueSchedule(segment, StringsJa)

    private fun work(
        plannedMs: Long,
        phase: Phase = Phase.WORK,
        isFinalSegment: Boolean = false,
        startsFinalRound: Boolean = false,
        nextExerciseNameJa: String? = null,
        capAtMs: Long? = null,
        open: Boolean = false,
        anchored: Boolean = false,
    ) = CueSegment(
        ordinal = 4,
        phase = phase,
        plannedMs = plannedMs,
        isFinalSegment = isFinalSegment,
        startsFinalRound = startsFinalRound,
        nextExerciseNameJa = nextExerciseNameJa,
        capAtMs = capAtMs,
        open = open,
        anchored = anchored,
    )

    @Test
    fun `a thirty-second station counts down, marks its half, and ends`() {
        val schedule = cueSchedule(work(30_000))
        assertEquals(
            listOf(Cue.HALFWAY to 15_000L, Cue.COUNT_TICK to 27_000L, Cue.INTERVAL_END to 30_000L),
            schedule.map { it.cue to it.atMs },
        )
    }

    @Test
    fun `a two-second station suppresses the three-two-one rather than firing it late`() {
        // 03-player.md §A, WORK edge case 1, stated as a rule: if (plannedMs < 3500) skip COUNT_TICK.
        // Firing it at T-3000 on a 2000ms segment would put the whole ladder before the segment began.
        val schedule = cueSchedule(work(2_000))
        assertTrue(schedule.none { it.cue == Cue.COUNT_TICK })
        assertEquals(listOf(Cue.INTERVAL_END), schedule.map { it.cue })
    }

    @Test
    fun `three and a half seconds is where the count-down starts being possible`() {
        assertTrue(cueSchedule(work(3_499)).none { it.cue == Cue.COUNT_TICK })
        assertTrue(cueSchedule(work(3_500)).any { it.cue == Cue.COUNT_TICK })
    }

    @Test
    fun `halfway needs twenty seconds to mean anything`() {
        assertTrue(cueSchedule(work(19_999)).none { it.cue == Cue.HALFWAY })
        assertTrue(cueSchedule(work(20_000)).any { it.cue == Cue.HALFWAY })
    }

    @Test
    fun `halfway is a work cue and never fires on a rest`() {
        // §D.2 restricts it to WORK. A 「半分」 through a sixty-second rest announces the half of a
        // recovery, which is not a thing anyone is pacing.
        val rest = cueSchedule(work(60_000, phase = Phase.REST))
        assertTrue(rest.none { it.cue == Cue.HALFWAY })
    }

    @Test
    fun `prepare ends in the session-start cue, never in an interval end`() {
        // §A edge case 3: the session-start cue fires at the END of prepare. Both rows trigger at
        // plannedMs, so a segment that emitted both would fire two cues on the same millisecond.
        val schedule = cueSchedule(work(5_000, phase = Phase.PREPARE, nextExerciseNameJa = "腕立て伏せ"))
        assertEquals(listOf(Cue.COUNT_TICK, Cue.SESSION_START), schedule.map { it.cue })
        assertEquals("腕立て伏せ", schedule.last().speech)
    }

    @Test
    fun `the final segment ends in nothing, because COMPLETE owns that moment`() {
        // A partial finish must not inherit a terminal cue posted before the user decided to stop.
        val schedule = cueSchedule(work(30_000, isFinalSegment = true))
        assertTrue(schedule.none { it.cue == Cue.INTERVAL_END })
        assertTrue(schedule.none { it.cue == Cue.SESSION_COMPLETE })
    }

    @Test
    fun `the final round announces itself the instant it starts`() {
        val schedule = cueSchedule(work(30_000, startsFinalRound = true))
        assertEquals(Cue.LAST_ROUND, schedule.first().cue)
        assertEquals(0L, schedule.first().atMs)
        assertEquals("最後の巡", schedule.first().speech)
    }

    @Test
    fun `an interval end speaks the exercise that is about to start`() {
        val schedule = cueSchedule(work(30_000, nextExerciseNameJa = "プランク"))
        assertEquals("プランク", schedule.single { it.cue == Cue.INTERVAL_END }.speech)
    }

    @Test
    fun `a cue with no name to say says nothing rather than an empty phrase`() {
        assertNull(cueSchedule(work(30_000)).single { it.cue == Cue.INTERVAL_END }.speech)
    }

    @Test
    fun `a self-paced rep segment is neither counted down nor ended by the clock`() {
        // §B.1: on an open segment "plannedMs is a pacing estimate and is NOT authoritative", and §A
        // REPS state 2 says that at estimate → 0 with autoAdvanceReps off — the default — "nothing
        // advances". Firing here gave the user a full 3-2-1 and then a 400ms full-amplitude buzz, a
        // TONE_PROP_BEEP2 and the next exercise's name, MID-SET, for a transition that is not
        // happening. §A also says "nothing about overrun is a failure"; this is the loudest possible
        // way to say otherwise.
        val schedule = cueSchedule(work(38_000, phase = Phase.REPS, open = true, nextExerciseNameJa = "懸垂"))

        assertTrue(schedule.none { it.cue == Cue.COUNT_TICK })
        assertTrue(schedule.none { it.cue == Cue.INTERVAL_END })
    }

    @Test
    fun `an EMOM minute window keeps both, because there the boundary decides`() {
        // §A REPS state 5: the estimate is replaced by the minute remaining and "overrun is impossible;
        // the boundary decides". An anchored open cell is on a real clock, so its cues are real too —
        // which is why the rule is `open && !anchored` rather than `open`.
        val schedule = cueSchedule(work(60_000, phase = Phase.REPS, open = true, anchored = true))

        assertTrue(schedule.any { it.cue == Cue.COUNT_TICK })
        assertTrue(schedule.any { it.cue == Cue.INTERVAL_END })
    }

    @Test
    fun `a self-paced segment still announces its final round and still honours a cap`() {
        // Neither is a clock claim. LAST_ROUND fires at 0 on information that is already true, and the
        // AMRAP cap is a hard boundary the user agreed to — §A REPS names it as one of the two things
        // that can end an open segment.
        val schedule = cueSchedule(
            work(38_000, phase = Phase.REPS, open = true, startsFinalRound = true, capAtMs = 12_000),
        )

        assertEquals(listOf(Cue.LAST_ROUND, Cue.AMRAP_CAP), schedule.map { it.cue })
    }

    @Test
    fun `an AMRAP cap inside the segment is scheduled and one past its end is not`() {
        assertTrue(cueSchedule(work(30_000, capAtMs = 12_000)).any { it.cue == Cue.AMRAP_CAP })
        assertTrue(cueSchedule(work(30_000, capAtMs = 40_000)).none { it.cue == Cue.AMRAP_CAP })
    }

    @Test
    fun `the count-down and the interval end share one duck`() {
        // §D.4's exact figures: open T-3150, close T+450. They are not hard-coded anywhere — the two
        // spans touch, so they merge, which is what keeps the next case correct too.
        val schedule = cueSchedule(work(30_000))
        val windows = focusWindows(schedule, tonesEnabled = true)
        assertTrue(windows.contains(FocusSpan(openAtMs = 26_850, closeAtMs = 30_450)))
        assertEquals(2, windows.size) // the other is 半分's
    }

    @Test
    fun `a suppressed count-down leaves the interval end its own window`() {
        // The case a hard-coded pair would have got wrong: below 3500ms there is no 3-2-1 to share
        // with, and §D.2 says the interval end then falls back to -150 / +450.
        val schedule = cueSchedule(work(3_400))
        assertEquals(
            listOf(FocusSpan(openAtMs = 3_250, closeAtMs = 3_850)),
            focusWindows(schedule, tonesEnabled = true),
        )
    }

    @Test
    fun `tones switched off asks for no duck at all`() {
        // Requesting focus for silence is how a launcher earns a reputation for interrupting music.
        assertEquals(emptyList<FocusSpan>(), focusWindows(cueSchedule(work(30_000)), tonesEnabled = false))
    }

    @Test
    fun `the last-round cue owns no numeric window, because its row says speech only`() {
        // §D.2 gives it no duration, and the utterance's own onDone is the honest end of the window.
        assertNull(focusSpanFor(Cue.LAST_ROUND, atMs = 0L))
        assertNull(focusSpanFor(Cue.REP_DONE, atMs = 0L))
        assertNull(focusSpanFor(Cue.EXTEND_ACK, atMs = 0L))
        assertNull(focusSpanFor(Cue.SKIP_BACK_ARMED, atMs = 0L))
    }

    @Test
    fun `windows that do not touch stay separate`() {
        val merged = mergeFocusWindows(
            listOf(FocusSpan(0, 100), FocusSpan(500, 600)),
        )
        assertEquals(listOf(FocusSpan(0, 100), FocusSpan(500, 600)), merged)
    }

    @Test
    fun `windows that merely touch still merge into one request`() {
        // Abandoning and re-requesting on the same millisecond thrashes the ducking ramp for nothing.
        assertEquals(
            listOf(FocusSpan(0, 600)),
            mergeFocusWindows(listOf(FocusSpan(0, 500), FocusSpan(500, 600))),
        )
    }

    @Test
    fun `merging does not assume its input arrived in order`() {
        assertEquals(
            listOf(FocusSpan(0, 900)),
            mergeFocusWindows(listOf(FocusSpan(400, 900), FocusSpan(0, 500))),
        )
    }

    @Test
    fun `a window swallowed by a longer one does not shorten it`() {
        assertEquals(
            listOf(FocusSpan(0, 1_000)),
            mergeFocusWindows(listOf(FocusSpan(0, 1_000), FocusSpan(200, 300))),
        )
    }

    @Test
    fun `an empty schedule asks for nothing`() {
        assertEquals(emptyList<FocusSpan>(), mergeFocusWindows(emptyList()))
    }

    @Test
    fun `the table spells the three-two-one as one waveform, not three posts`() {
        // Honest about what this is: a transcription check on §D.2's row, not a check that the engine
        // fires it once. Six entries = off/on × 3, spanning 2060ms from T-3000. The rule it protects is
        // §D.3's — three postDelayed callbacks accumulate scheduler jitter against a tone sequence that
        // does not — and the table is what stops the row being re-typed as three Singles.
        val haptic = Cue.COUNT_TICK.haptic as HapticPattern.Waveform
        assertEquals(listOf(0L, 60L, 940L, 60L, 940L, 60L), haptic.timingsMs)
        assertEquals(listOf(0, 120, 0, 120, 0, 120), haptic.amplitudes)
    }

    @Test
    fun `the table gives the count-down no speech column at all`() {
        // Again a transcription check, and deliberately so: §D.6's "never TTS for the 3-2-1" is
        // enforced by the ROW being null rather than by a branch in the engine, so there is nothing to
        // get wrong at runtime and nothing to test at runtime either. Engine latency is variable and
        // speech would drift audibly against a haptic that does not.
        assertFalse("§D.2 gives the 3-2-1 no speech column", Cue.COUNT_TICK.speaks)
        assertNull(Cue.COUNT_TICK.speech(StringsJa))
        assertNull(Cue.COUNT_TICK.speech(StringsEn))
    }

    @Test
    fun `every row that says it speaks has a phrase in both languages, and no row that says it does not`() {
        // `speaks` and `speech(strings)` are two statements of one fact, and they are made in two
        // places: the enum's constructor argument and a `when` over every case. A row added with
        // `speaks = true` and forgotten in the `when` would be silent for a reason nothing reports —
        // the engine simply passes null to `play` and moves on.
        Cue.entries.forEach { cue ->
            assertEquals(cue.name, cue.speaks, cue.speech(StringsJa) != null)
            assertEquals(cue.name, cue.speaks, cue.speech(StringsEn) != null)
        }
    }

    @Test
    fun `the two 終わり stay two strings, because English does not spell them the same`() {
        // The survey kept two keys for one Japanese word on purpose: the AMRAP cap is a clock landing
        // on an effort still in progress, and the session end is the whole thing being over. A merge is
        // easy to make and impossible to undo once every call site reads the same key.
        assertEquals(Cue.AMRAP_CAP.speech(StringsJa), Cue.SESSION_COMPLETE.speech(StringsJa))
        assertNotEquals(Cue.AMRAP_CAP.speech(StringsEn), Cue.SESSION_COMPLETE.speech(StringsEn))
    }

    @Test
    fun `the schedule speaks the language it is given, not the one the process started in`() {
        val ja = cueSchedule(work(30_000, startsFinalRound = true), StringsJa)
        val en = cueSchedule(work(30_000, startsFinalRound = true), StringsEn)

        assertEquals("最後の巡", ja.first { it.cue == Cue.LAST_ROUND }.speech)
        assertEquals("Last round", en.first { it.cue == Cue.LAST_ROUND }.speech)
        // Only the words move. A cue table that changed shape with the language would be a second
        // specification.
        assertEquals(ja.map { it.cue to it.atMs }, en.map { it.cue to it.atMs })
    }

    @Test
    fun `an exercise name passes through whatever the language is, because it is not ours to translate`() {
        // The catalogue owns nameJa/nameEn and a user-authored routine's name is their data. Whatever
        // arrives here is spoken verbatim in both tables.
        val en = cueSchedule(work(30_000, nextExerciseNameJa = "腕立て伏せ"), StringsEn)
        assertEquals("腕立て伏せ", en.single { it.cue == Cue.INTERVAL_END }.speech)
    }
}
