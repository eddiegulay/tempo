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

    /**
     * `ON_STOP`. **The one row whose answer depends on whether a foreground service holds the session.**
     *
     * With no service (Phase 1, and any device that refused to start one) backgrounding means the cues
     * stop, because the process is about to be frozen and a held `ToneGenerator` is a battery bug.
     * With the health service up it means nothing at all — see [CueState.serviceHolding].
     */
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
    /**
     * Whether 鍛錬's health foreground service is actually holding this session — `03-player.md` §E.5.
     *
     * §E.5 says the whole of what Phase 3's service changes: *"the only thing that changes in this spec
     * is the disarm matrix (§D.7)"*. This flag is that change, and it is a fact about the **process**,
     * not about the session: it is true only once `startForeground` has returned without throwing
     * (`TrainingHold`), so a device that refused the service — API 34's `health` prerequisite, API 31's
     * background-start rules, a manifest mismatch — falls back to Phase 1's row rather than keeping a
     * `ToneGenerator` open in a process the system is about to freeze.
     *
     * Default false, deliberately. Every other event ignores it, and a caller that has not been told
     * about the service gets the behaviour that was correct before it existed.
     */
    val serviceHolding: Boolean = false,
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
    /**
     * Whether the cue channel is **still live** once this plan has been carried out.
     *
     * Two rows say true and they say it for different reasons, which is why the field is phrased as a
     * state rather than as an instruction: a skip leaves the engine running and the caller arms the
     * segment it landed in, while a service-backed `ON_STOP` leaves the engine running *and leaves the
     * already-posted schedule alone*. False everywhere else means the caller may drop its armed key,
     * because there is nothing left to keep.
     */
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
 * Three rows deviate from a naive reading of the table, all deliberately:
 *
 * 1. **Preferences gate the behavioural rows.** A channel that was never armed has nothing to cancel,
 *    and cancelling it anyway would mean [ON_STOP]-shaped teardown running on every pause. The
 *    exception is [CueEvent.ON_STOP] itself, which does not consult preferences at all — it is
 *    teardown, not a channel decision, and an engine armed a moment before the user switched a channel
 *    off still holds the resources.
 * 2. **[CueEvent.COMPLETE]'s waits collapse for a partial session**, because the cue they are waiting
 *    for does not fire. See [CueState.sessionComplete].
 * 3. **[CueEvent.ON_STOP] has two rows, not one** — `03-player.md` §E.5's single sanctioned Phase 3
 *    change. §D.7's printed row is the no-service one and it is kept verbatim; a session the health
 *    foreground service is holding disarms nothing at all. See [CueState.serviceHolding].
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

    // The only row §E.5 changes when the health service lands, and it changes to *nothing happens*.
    //
    // A backgrounded session was always correct on return — the timeline is derived from
    // `stateAt(elapsedRealtime)` — so the sole thing a foreground process buys is the cue channel. If
    // this row still tore the channel down, the service would ask for ACTIVITY_RECOGNITION, post a
    // notification, cost a Play Console health declaration, and deliver a pocketed workout exactly as
    // silent as Phase 1's. Keeping the channel is not an optimisation; it is the entire deliverable.
    //
    // Nothing is cancelled rather than cancelled-and-re-armed: the pending 3-2-1 for the segment on
    // screen is already posted at the right wall time, and re-computing it across the transition is a
    // cue dropped at exactly the boundary the user cannot see.
    CueEvent.ON_STOP -> if (state.serviceHolding) {
        DisarmPlan(
            cancelHaptics = false,
            cancelHapticsAfterMs = 0L,
            cancelPending = false,
            tones = ToneDisarm.KEEP,
            toneDelayMs = 0L,
            stopSpeech = false,
            rearm = true,
        )
    } else {
        DisarmPlan(
            cancelHaptics = true,
            cancelHapticsAfterMs = 0L,
            cancelPending = true,
            tones = ToneDisarm.RELEASE,
            toneDelayMs = 0L,
            stopSpeech = true,
            rearm = false,
        )
    }

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
