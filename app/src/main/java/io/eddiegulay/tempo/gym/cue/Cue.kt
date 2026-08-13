package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.i18n.Strings

/**
 * One of the two haptics the platform draws itself, rather than one we describe in milliseconds.
 *
 * These exist as our own names instead of `VibrationEffect.EFFECT_CLICK` / `EFFECT_TICK` so that the
 * cue table below stays Android-free and therefore JVM-testable. The mapping lives in one `when` in
 * [GymHaptics]; putting the platform constants here would have dragged `android.os` into a file whose
 * whole value is that a test can read it without a device.
 */
enum class PredefinedHaptic { CLICK, TICK }

/**
 * What the vibrator is asked to draw for one cue.
 *
 * Three shapes, because the platform has three and they are not interchangeable. A one-shot is a
 * single sustained buzz; a waveform is a rhythm the user is meant to *count*; a predefined effect is
 * the system's own tuned tap, which we use for acknowledgements because a hand-rolled 20ms buzz feels
 * like a different app than the rest of the phone.
 *
 * Amplitudes are carried even though `hasAmplitudeControl()` may be false — `createWaveform` silently
 * degrades to on/off at full strength on such a device, which is acceptable. `03-player.md` §D.3 says
 * plainly: **do not branch.** Two code paths that mostly agree is more risk than one path that is
 * occasionally blunter.
 */
sealed interface HapticPattern {

    /** A single buzz. [amplitude] is 1..255; 0 would be silence and is never what a cue wants. */
    data class OneShot(val durationMs: Long, val amplitude: Int) : HapticPattern

    /**
     * Alternating off/on spans, starting with off — the platform's own convention, which is why every
     * pattern in `03-player.md` §D.2 begins with a `0`.
     *
     * The 3-2-1 is **one** of these, fired once at T−3000, not three scheduled callbacks: three posts
     * accumulate scheduler jitter against a tone sequence that does not, and a waveform cannot drift
     * against itself.
     */
    data class Waveform(val timingsMs: List<Long>, val amplitudes: List<Int>) : HapticPattern

    /** The system's own tap. Used where the cue is an acknowledgement of the user's touch. */
    data class Predefined(val effect: PredefinedHaptic) : HapticPattern
}

/**
 * The four `ToneGenerator` tones the cue table uses, named for what they mean rather than for their
 * constant.
 *
 * There is no fifth: the completion ceremony is [TonePattern.Completion], synthesized rather than
 * generated, because `ToneGenerator` has **no pitch control** and design §10's descending three-note
 * is simply not expressible with it (`00-plan.md` §2 row 12).
 */
enum class ToneId { ACK, BEEP, BEEP2, NACK }

/** What the speaker is asked to play for one cue. */
sealed interface TonePattern {

    data class Single(val tone: ToneId, val durationMs: Int) : TonePattern

    /**
     * [count] tones of [durationMs], one every [periodMs].
     *
     * The 3-2-1 is the reason this exists as a shape rather than three scheduled [Single]s: the tones
     * and the haptic waveform must stay locked to each other, and a single repeating spec is what lets
     * both be posted from one place with one origin.
     */
    data class Repeat(val tone: ToneId, val durationMs: Int, val count: Int, val periodMs: Int) : TonePattern

    /** The synthesized descending three-note of §D.5. See [CompletionTone]. */
    data object Completion : TonePattern
}

/**
 * The cue table of `03-player.md` §D.2, as data.
 *
 * It is data and not a `when` in the engine for one reason: the table is the specification, and a
 * table that can be read top-to-bottom next to the spec is the only way a reviewer can check eleven
 * rows × three channels without executing anything. The engine then has no per-cue knowledge at all —
 * it schedules, and it plays whatever the row says.
 *
 * [speaks] says whether §D.2 gives this row a **fixed** phrase; the phrase itself is [Cue.speech],
 * resolved against the language in force. It is not a constructor argument, and that is the whole of
 * `.planning/i18n/DECISIONS.md` §L3: an enum constant is initialised once, at class-init, so a phrase
 * carried there would keep speaking the language the process started in for as long as the process
 * lived. Every fixed phrase is still §D.2 verbatim in Japanese; none is invented.
 *
 * The two dynamic rows ([SESSION_START], [INTERVAL_END]) hold `speaks = false` for a different reason:
 * their phrase is the *exercise's name*, which the catalogue supplies per segment. A template here
 * would be a string this file made up.
 *
 * *Rejected* — giving each row its trigger offset too. The offsets are functions of the segment
 * (`plannedMs − 3000`, `plannedMs / 2`), not constants, so they live in [cueSchedule] where they can
 * be tested against a segment. A row that carried half its trigger would invite the other half to be
 * computed somewhere else.
 */
enum class Cue(
    val haptic: HapticPattern?,
    val tone: TonePattern?,
    val speaks: Boolean,
) {

    /** Fires at the **end** of 支度, not at its start — the session begins when the preparing stops. */
    SESSION_START(
        haptic = HapticPattern.OneShot(durationMs = 400, amplitude = 200),
        tone = TonePattern.Single(ToneId.ACK, durationMs = 300),
        speaks = false, // the exercise about to start; supplied per segment
    ),

    /**
     * The 3-2-1. **Never TTS** — engine latency is variable and speech would drift audibly against a
     * haptic that does not.
     */
    COUNT_TICK(
        haptic = HapticPattern.Waveform(
            timingsMs = listOf(0L, 60L, 940L, 60L, 940L, 60L),
            amplitudes = listOf(0, 120, 0, 120, 0, 120),
        ),
        tone = TonePattern.Repeat(ToneId.BEEP, durationMs = 80, count = 3, periodMs = 1000),
        speaks = false,
    ),

    INTERVAL_END(
        haptic = HapticPattern.OneShot(durationMs = 400, amplitude = 255),
        tone = TonePattern.Single(ToneId.BEEP2, durationMs = 300),
        speaks = false, // the next exercise's name; supplied per segment
    ),

    HALFWAY(
        haptic = HapticPattern.Waveform(
            timingsMs = listOf(0L, 40L, 120L, 40L),
            amplitudes = listOf(0, 90, 0, 90),
        ),
        tone = TonePattern.Single(ToneId.BEEP, durationMs = 60),
        speaks = true,
    ),

    /** No tone: the round boundary is information, not an instruction, and the ring already moved. */
    LAST_ROUND(
        haptic = HapticPattern.Waveform(
            timingsMs = listOf(0L, 100L, 140L, 100L),
            amplitudes = listOf(0, 160, 0, 160),
        ),
        tone = null,
        speaks = true,
    ),

    /**
     * The 済 acknowledgement: a light CLICK, deliberately **not** the heavy interval-end buzz. The
     * user is holding the phone when this fires (`03-player.md` §A, REPS edge case 8).
     */
    REP_DONE(
        haptic = HapticPattern.Predefined(PredefinedHaptic.CLICK),
        tone = null,
        speaks = false,
    ),

    EMOM_FAIL(
        haptic = HapticPattern.Waveform(
            timingsMs = listOf(0L, 200L, 100L, 200L, 100L, 200L),
            amplitudes = listOf(0, 255, 0, 255, 0, 255), // §D.2: "full amp"
        ),
        tone = TonePattern.Single(ToneId.NACK, durationMs = 400),
        speaks = true,
    ),

    AMRAP_CAP(
        haptic = HapticPattern.OneShot(durationMs = 600, amplitude = 255),
        // §D.2 says "TONE_PROP_BEEP2 ×2" without a duration or a spacing. Both are recoverable rather
        // than invented: BEEP2's duration is 300ms in INTERVAL_END, and the row's own focus window
        // (0 → +750ms) is 300 + 300 + the 150ms tail every other window in the table carries. Two
        // back-to-back 300ms tones is the only reading that closes the window it was given.
        tone = TonePattern.Repeat(ToneId.BEEP2, durationMs = 300, count = 2, periodMs = 300),
        speaks = true,
    ),

    /** Complete sessions only. A partial session gets [REP_DONE] alone — dignified, not punitive. */
    SESSION_COMPLETE(
        haptic = HapticPattern.OneShot(durationMs = 600, amplitude = 255),
        tone = TonePattern.Completion,
        speaks = true,
    ),

    EXTEND_ACK(
        haptic = HapticPattern.Predefined(PredefinedHaptic.TICK),
        tone = null,
        speaks = false,
    ),

    SKIP_BACK_ARMED(
        haptic = HapticPattern.Predefined(PredefinedHaptic.TICK),
        tone = null,
        speaks = false,
    ),
    ;

    /** True where §D.2 gives no audio-focus window, because the cue makes no sound of its own. */
    val silent: Boolean get() = tone == null && !speaks
}

/**
 * The fixed phrase §D.2 gives this row, in the language in force — or null where the row has none.
 *
 * An extension taking [Strings] rather than a property on the enum, matching `Tier.label(strings)`:
 * a constructor argument is resolved once at class-init and would keep speaking whatever language the
 * process started in, which for a cue channel means a user who switches language mid-session keeps
 * hearing the old one until they force-stop the app.
 *
 * Null is not a gap to be filled. [Cue.SESSION_START] and [Cue.INTERVAL_END] speak the exercise's
 * name, supplied per segment from the catalogue; [Cue.COUNT_TICK] is **never** spoken by rule; and the
 * three acknowledgement rows make no sound at all.
 */
fun Cue.speech(strings: Strings): String? = when (this) {
    Cue.HALFWAY -> strings.gymCue.speakHalfway
    Cue.LAST_ROUND -> strings.gymCue.speakLastRound
    Cue.EMOM_FAIL -> strings.gymCue.speakTimeUp
    Cue.AMRAP_CAP -> strings.gymCue.speakCapReached
    Cue.SESSION_COMPLETE -> strings.gymCue.speakSessionEnd
    Cue.SESSION_START, Cue.INTERVAL_END, Cue.COUNT_TICK,
    Cue.REP_DONE, Cue.EXTEND_ACK, Cue.SKIP_BACK_ARMED,
    -> null
}
