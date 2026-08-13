package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.gym.Phase

/**
 * The five moments `03-player.md` §D.7 says the cue engine must go quiet, and the only five.
 *
 * They are named for what happened, not for what to do about it — [PAUSE] and [QUIT_SHEET] take
 * identical action today and are still two constants, because they are two different promises. The
 * quit sheet's silence is part of "the clock pauses the instant the sheet opens": a sheet that lets
 * cues keep firing while you decide is a record that includes the deciding.
 */
enum class CueEvent {

    /** ┃┃ from any live phase, and the 30-minute stall guard. */
    PAUSE,

    /** ✕ or back opened 鍛錬を終えますか. */
    QUIT_SHEET,

    /** ▷ or ◁ moved the frontier. The only row that re-arms rather than shutting down. */
    SKIP,

    /** `ON_STOP`. Phase 1 has no foreground service, so backgrounding means the cues stop. */
    ON_STOP,

    /** Entering 記録. */
    COMPLETE,
}

/** What §D.7's tone column asks for. Three verbs, because a closed window and a released generator are not the same thing. */
enum class ToneDisarm {

    /** Leave the window alone — a skip does not interrupt a duck the next segment will want anyway. */
    KEEP,

    /** Abandon focus. The `ToneGenerator` survives, because the session is not over. */
    CLOSE_WINDOW,

    /**
     * Abandon focus **and** release the generator. §D.4: a leaked `ToneGenerator` holds an
     * `AudioTrack` and is the standard way this component becomes a battery bug.
     */
    RELEASE,
}

/**
 * The state the disarm decision reads besides the preferences — phase, plus the one fact about the
 * session that changes what COMPLETE means.
 *
 * [sessionComplete] is here rather than on the event because a partial finish and a full finish arrive
 * through the same door. §D.2: a complete session gets [Cue.SESSION_COMPLETE]; **a partial gets
 * `REP_DONE` alone**. So the completion ceremony's 600ms of buzz and 900ms of tone exist in one case
 * and not the other, and waiting for them in the other holds the vibrator open over silence.
 */
data class CueState(
    val phase: Phase,
    val sessionComplete: Boolean = true,
)

/**
 * The four actions of one §D.7 row, resolved against what is actually armed.
 *
 * Delays are carried as data rather than applied by the caller's own `postDelayed` because the two
 * non-zero ones ([cancelHapticsAfterMs], [toneDelayMs]) are the completion cue's own length. Reading
 * them off the cue table is what stops them drifting apart from the cue they are waiting for.
 */
data class DisarmPlan(
    val cancelHaptics: Boolean,
    val cancelHapticsAfterMs: Long,
    val cancelPending: Boolean,
    val tones: ToneDisarm,
    val toneDelayMs: Long,
    val stopSpeech: Boolean,
    val rearm: Boolean,
)

/** How long the completion haptic runs, read off its own row so the wait cannot drift from the buzz. */
private val COMPLETION_HAPTIC_MS: Long =
    (Cue.SESSION_COMPLETE.haptic as HapticPattern.OneShot).durationMs

/** How long the completion window stays open, read off §D.2's own focus column for the same reason. */
private val COMPLETION_WINDOW_MS: Long =
    focusSpanFor(Cue.SESSION_COMPLETE, 0L)?.closeAtMs ?: 0L

/** PREPARE / WORK / REPS / REST. COMPLETE is a destination, not a phase you can skip inside. */
private val Phase.live: Boolean get() = this != Phase.COMPLETE

/**
 * `03-player.md` §D.7, as a pure function.
 *
 * The matrix is five rows of four columns and every cell is a way to leave a motor running, a duck
 * held over a user's podcast, or a 「半分」 arriving forty seconds after the half. None of that is
 * visible in a screenshot and none of it fails a build, which is exactly why it is arithmetic here
 * with a test per cell rather than five `when` branches inside a `ViewModel` that needs a device.
 *
 * Two rows deviate from a naive reading of the table, both deliberately:
 *
 * 1. **Preferences gate the behavioural rows.** A channel that was never armed has nothing to cancel,
 *    and cancelling it anyway would mean [ON_STOP]-shaped teardown running on every pause. The
 *    exception is [CueEvent.ON_STOP] itself, which does not consult preferences at all — it is
 *    teardown, not a channel decision, and an engine armed a moment before the user switched a channel
 *    off still holds the resources.
 * 2. **[CueEvent.COMPLETE]'s waits collapse for a partial session**, because the cue they are waiting
 *    for does not fire. See [CueState.sessionComplete].
 *
 * *Rejected* — one `disarm()` method on the engine with the matrix inlined. It compiles, it is
 * shorter, and it is unreviewable: the reason §D.7 is a table is that the cells disagree in ways that
 * only line up when you can see them side by side.
 */
fun disarmPlan(event: CueEvent, cues: CueSettings, state: CueState): DisarmPlan = when (event) {

    CueEvent.PAUSE, CueEvent.QUIT_SHEET -> DisarmPlan(
        cancelHaptics = cues.haptics,
        cancelHapticsAfterMs = 0L,
        cancelPending = true,
        tones = if (cues.tones) ToneDisarm.CLOSE_WINDOW else ToneDisarm.KEEP,
        toneDelayMs = 0L,
        stopSpeech = cues.speech,
        rearm = false,
    )

    // The frontier moved but the session did not stop: the vibrator is left alone (a skip is often
    // itself acknowledged by a haptic), pending posts die with the segment they belonged to, and the
    // duck is kept because the segment we are landing in will want it.
    CueEvent.SKIP -> DisarmPlan(
        cancelHaptics = false,
        cancelHapticsAfterMs = 0L,
        cancelPending = true,
        tones = ToneDisarm.KEEP,
        toneDelayMs = 0L,
        stopSpeech = cues.speech,
        rearm = state.phase.live,
    )

    CueEvent.ON_STOP -> DisarmPlan(
        cancelHaptics = true,
        cancelHapticsAfterMs = 0L,
        cancelPending = true,
        tones = ToneDisarm.RELEASE,
        toneDelayMs = 0L,
        stopSpeech = true,
        rearm = false,
    )

    CueEvent.COMPLETE -> {
        val ceremony = state.sessionComplete
        DisarmPlan(
            cancelHaptics = cues.haptics,
            cancelHapticsAfterMs = if (cues.haptics && ceremony) COMPLETION_HAPTIC_MS else 0L,
            cancelPending = true,
            tones = if (cues.tones) ToneDisarm.CLOSE_WINDOW else ToneDisarm.KEEP,
            toneDelayMs = if (cues.tones && ceremony) COMPLETION_WINDOW_MS else 0L,
            // Speech is NOT stopped: 「終わり」 is the last thing the session says and cutting it off
            // to tidy up would be the one moment of ceremony the feature has, truncated.
            stopSpeech = false,
            rearm = false,
        )
    }
}
