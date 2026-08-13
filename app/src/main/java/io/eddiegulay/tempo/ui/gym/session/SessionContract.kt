package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.SessionOutcome
import io.eddiegulay.tempo.gym.session.CompletionReason
import io.eddiegulay.tempo.gym.session.RestKind
import io.eddiegulay.tempo.gym.session.Segment
import io.eddiegulay.tempo.gym.session.Timeline

/*
 * The contract between the session host and the pages that draw it — `03-player.md` §A.
 *
 * One state type and one action type, and the split is the whole point: **the host owns every fact
 * that has to survive process death, and the pages own every word on screen.** `00-plan.md` §4.1
 * rule 7 puts all UI copy in the page that shows it, so nothing in this file is a Japanese string, and
 * `03-player.md` §B's doctrine puts the phase in `stateAt` rather than in a field, so nothing in this
 * file is a phase a page may set.
 *
 * *Rejected* — one state class per page (`WorkUiState`, `RepsUiState`, `RestUiState`). It reads
 * tidier and it breaks §3.1's single most-defended property: WORK, REPS and REST are **the same
 * chrome** with three bodies, drawn at pixel-stable positions so that nothing jumps at a transition.
 * Three state types would be three layouts within a week, because nothing would hold them together.
 *
 * *Rejected* — handing the pages a `PlayerState` and the `Timeline` and letting them derive the rest.
 * They need the derivations anyway ([SessionUiState.next], [SessionUiState.canExtendRest],
 * [SessionUiState.roundsCompleted]), and derived twice on two pages is derived differently on two
 * pages. Both are still here, uncollapsed, because the pages' own pure label functions
 * (`nextUpLabel`, `counterLabel`, `restHero`) are specified against them.
 */

/**
 * 支度, whichever of the two it is.
 *
 * The compiled countdown at the head of a session and the three seconds back in from a long pause
 * (§C.1 rows 19 and 30) are the same screen, so they are one value here — and `resumed` is the only
 * thing that differs, because §A PREPARE edge case 4 makes the resumed one's hero "the exercise the
 * session was interrupted *inside*, not the first one".
 *
 * The resumed countdown deliberately does **not** exist as a segment. Inserting one mid-timeline
 * would renumber every ordinal after it, and the ordinal is the identity every persisted
 * `session_result` row carries — see `SessionTransition.prepareForMs`, which rejects that fix at
 * length.
 */
@Immutable
data class PrepareUi(
    val remainingMs: Long,
    val totalMs: Long,
    /** True for the 3s countdown back into a session already under way; false for the session's own 支度. */
    val resumed: Boolean,
)

/**
 * What sits *over* the phase, never instead of it.
 *
 * 休止 and 鍛錬を終えますか both render the phase underneath them — the frozen ensō, the frozen numeral
 * — so they are an overlay on one state rather than two more phases. That is also why
 * [io.eddiegulay.tempo.gym.session.Overlay] has exactly these three values in the state machine: this
 * type is its UI-facing twin, carrying the extra facts a screen needs and the machine does not.
 */
@Immutable
sealed interface SessionOverlayUi {

    data object None : SessionOverlayUi

    /**
     * @param pausedForMs how long the pause has lasted, for §A PAUSED's `PausedLong`.
     * @param resumeNeedsPrepare the same fact as `pausedForMs > 60s`, resolved once by the host so the
     *   subtitle and the actual behaviour cannot disagree — the machine decides the 3s countdown from
     *   its own copy of `pausedForMs`, and a page recomputing the threshold would be a second opinion.
     * @param stalled the 30-minute guard fired rather than the user tapping ┃┃ (§A PAUSED `Stalled`).
     *   **No auto-save rides on it**: the user decides what a forgotten phone was worth.
     */
    data class Paused(
        val pausedForMs: Long,
        val resumeNeedsPrepare: Boolean,
        val stalled: Boolean,
    ) : SessionOverlayUi

    /**
     * 鍛錬を終えますか.
     *
     * @param saving a finish or a discard is in flight; §A QUIT_SHEET's `Saving` makes the rows
     *   non-interactive and **does not dismiss the sheet**.
     * @param fault the last failed outcome. §A is explicit: *never navigate away on a failed save*, so
     *   this is rendered on the open sheet and the session stays exactly where it was.
     */
    data class Quit(val saving: Boolean, val fault: GymFault?) : SessionOverlayUi
}

/**
 * One frame of the live player.
 *
 * Every field is derived from the compiled [timeline] and one monotonic elapsed reading, so the whole
 * thing is reproducible from `(timeline, elapsedMs)` — which is what makes rotation, backgrounding,
 * deep sleep and cold-start recovery the same code path (§B.5, §E.2). Nothing here is accumulated
 * across frames, and a page must not accumulate either.
 *
 * @param elapsedMs session-elapsed, pauses already subtracted. The number `stateAt` was read at.
 * @param activeMs [elapsedMs] minus 支度, which is what 「六分十四秒 経過」 counts — prepare is timeline
 *   offset `[0, prepareMs)` and is excluded from active time (§A PREPARE, data out).
 * @param remainingMs negative on an open segment past its estimate; [overrunMs] is the same fact as a
 *   positive number, because the 目安 line prints it and **overrun is not a failure**.
 * @param ringFraction 1f full → 0f empty, already clamped. [ensoSweepAngle] is the same value through
 *   `ensoSweep`, so no page multiplies by 312 itself.
 * @param next the next segment that will actually be shown — a zero-span EMOM remainder is skipped,
 *   because §B.6 invariant 4 says such a cell is never rendered. Null on the final segment, which is
 *   §A WORK state 6's `NoNextUp`.
 * @param nextExercise the movement the user is heading *for*: the next segment's own exercise when it
 *   has one, and otherwise the one after the rest. That is what 休息's hero and WORK's 次 line both
 *   name, so it is resolved once here.
 * @param canStepBack whether ◁◁ has anywhere to go; false on the first real segment, which §C.4
 *   degrades to a restart rather than stepping back into 支度.
 * @param resultsWritten how many `session_result` rows exist. The quit sheet's shape branches on it
 *   (`NothingToSave`), and so does whether 記録せずに終える asks twice.
 * @param fault a write that failed *without* stopping the session — the finish that could not land.
 *   It is on the live state rather than a navigation because §A QUIT_SHEET's `SaveFailed` rule
 *   generalises: a failed save never moves the user.
 */
@Immutable
data class SessionUiState(
    val routineId: String,
    val routineName: String,
    val engine: Engine,
    val timeline: Timeline,
    val ordinal: Int,
    val segment: Segment,
    val phase: Phase,
    val elapsedMs: Long,
    val activeMs: Long,
    val elapsedInSegmentMs: Long,
    val remainingMs: Long,
    val overrunMs: Long,
    val ringFraction: Float,
    val ensoSweepAngle: Float,
    val sessionFraction: Float,
    val round: Int,
    val totalRounds: Int,
    val roundsCompleted: Int,
    val stationIndex: Int?,
    val stationsPerRound: Int,
    val stationsCompleted: Int,
    val stationsPlanned: Int,
    val exercise: Exercise?,
    val prescribedReps: Int?,
    val restKind: RestKind?,
    val addedMs: Long,
    val next: Segment?,
    val nextExercise: Exercise?,
    val isLastRound: Boolean,
    val canSkipForward: Boolean,
    val canExtendRest: Boolean,
    val canStepBack: Boolean,
    val autoAdvance: Boolean,
    val finished: Boolean,
    val prepare: PrepareUi?,
    val overlay: SessionOverlayUi,
    val resultsWritten: Int,
    val fault: GymFault?,
) {
    /** Self-paced and still open: the 済 gate is what ends it, and nothing else may (hard rule 2). */
    val awaitingDone: Boolean get() = segment.open && !segment.closed && phase == Phase.REPS

    /** Past the estimate on an open segment. The ring holds empty; the 目安 line counts up. */
    val overrunning: Boolean get() = overrunMs > 0L
}

/**
 * 記録, once the session is closed and the store has answered.
 *
 * [outcome] is `finishSession`'s own return value, which `02-data.md` §C.2 fills **inside** the finish
 * transaction precisely so this screen issues no follow-up query — read the previous session after the
 * commit and the just-finished one is its own baseline, and the page renders 「前回より 0秒 速い」.
 *
 * [rating] is held here rather than read back from [outcome] because it is edited on this screen and
 * the write is fire-and-forget (§A COMPLETE edge case 3): the tap must land visually now, and
 * [ratingFault] is how a failed write says so without silently reverting.
 */
@Immutable
data class SessionCompleteState(
    val outcome: SessionOutcome,
    val reason: CompletionReason,
    val rating: Rating?,
    val ratingFault: GymFault?,
)

/**
 * A session that exists on disk and cannot be continued — §E.2's "not resumable rather than guessing".
 *
 * It is a state of its own rather than a fault, because nothing failed: the store read perfectly and
 * the stored rows are intact. What is impossible is *replaying* them onto a freshly compiled timeline,
 * and the honest response is the quit sheet's own two outcomes with つづける removed — every word of
 * which is already in `03-player.md` §A's table.
 *
 * @param resultsWritten as [SessionUiState.resultsWritten]; zero removes ここまでを記録する exactly as
 *   `NothingToSave` does on the sheet.
 */
@Immutable
data class SessionUnrecoverableState(
    val routineId: String,
    val routineName: String,
    val resultsWritten: Int,
    val saving: Boolean,
    val fault: GymFault?,
)

/**
 * What the host is showing, as four cases that share no composable.
 *
 * The `Loadable` doctrine, applied to a session (`00-plan.md` §4.1 rule 1): a session still being
 * reconstructed, a session running, a session finished and a session that could not be read are four
 * different pictures. There is deliberately **no Empty** — a session with nothing in it is
 * [Unrecoverable] with `resultsWritten = 0`, which offers the user a decision rather than a blank page.
 */
@Immutable
sealed interface SessionScreen {

    /** The store has not answered yet. §A's prompt rule applies here too: no flash, and no spinner. */
    data object Loading : SessionScreen

    data class Live(val state: SessionUiState) : SessionScreen

    data class Complete(val state: SessionCompleteState) : SessionScreen

    data class Unrecoverable(val state: SessionUnrecoverableState) : SessionScreen

    /** The read itself failed. Rendered through the existing `faultCopy`/`FaultPanel`, never as empty. */
    data class Failed(val fault: GymFault) : SessionScreen
}

/**
 * Every event a player page can raise — `03-player.md` §A's five actions tables, deduplicated.
 *
 * An interface rather than a bag of lambdas so a page holds **one** stable reference: a data class of
 * twelve lambdas is a new instance on every recomposition and defeats skipping on every button that
 * takes one.
 *
 * All of them are safe to call at any time. The state machine returns null for an event that does not
 * apply in the current overlay or phase, and §C is explicit that null is a **no-op, never an error** —
 * a ▷ on a mandated rest and a 済 that arrived after the segment closed are both ordinary.
 */
@Stable
interface SessionActions {

    /** ▷ — 支度 jumps to the first station; anything else closes as `skipped` (§C.1 rows 3, 5, 15). */
    fun onSkipForward()

    /**
     * ◁ — first tap restarts this segment, a second within 2s steps back one and **deletes its
     * result** (§C.4). The window is the host's [io.eddiegulay.tempo.gym.session.BackTapResolver]; a
     * page must not arm its own or the two will disagree about what "second tap" means.
     */
    fun onBackTap()

    /** ┃┃ */
    fun onPause()

    /** 続ける — via a 3s 支度 when the pause ran over a minute (§C.1 row 19). */
    fun onResume()

    /**
     * 済, and the rep wheel's 記録する.
     *
     * @param actualReps null keeps the prescribed count; **0 is recorded as とばした**, because a set
     *   wound down to zero is one the user did not do (§A REPS edge case 4).
     * @param adjusted the count came from the wheel rather than from the button, which is the only
     *   difference between §C.1 rows 6 and 7.
     * @param ordinal the segment the button was drawn for. Pass it: a 済 double-tap's second press
     *   lands on the *next* segment's UI, and a debounce alone cannot tell that apart from a genuine
     *   fast close (§A REPS edge case 6).
     */
    fun onDone(actualReps: Int? = null, adjusted: Boolean = false, ordinal: Int? = null)

    /** ＋二十秒. Refused silently on a mandated rest or an EMOM remainder — ask [SessionUiState.canExtendRest]. */
    fun onExtendRest()

    /** ✕ or Back from a live phase. The clock stops the instant the sheet opens. */
    fun onQuit()

    /** ここまでを記録する — closes the open segment as a partial and finishes `complete = false`. */
    fun onQuitSaveRecording()

    /**
     * 記録せずに終える, **already confirmed**. The host does not arm the 3s window; the sheet does,
     * because §A QUIT_SHEET makes the two-step conditional on `resultsWritten >= 1` and the arming is
     * a property of the row the user is looking at.
     */
    fun onQuitDiscard()

    /** つづける, Back, or the scrim — all one thing, and none of them a discard. */
    fun onQuitDismissed()

    /** どうでしたか. Re-tapping the selected rating un-rates, so null is a real argument. */
    fun onRate(rating: Rating?)

    /** 閉じる — pops the whole player stack to `GYM.HOME`. */
    fun onCloseRecord()

    /** もう一度 — back to `GYM.LIBRARY.DETAIL` for the same routine. */
    fun onRepeat()

    /** もう一度 on a finish that failed. The session's rows are all written; only the close did not land. */
    fun onRetryFinish()

    /**
     * もう一度 behind [SessionScreen.Failed] — re-reads the session and rebuilds the timeline.
     *
     * The whole of the reconstruction is idempotent (it writes nothing), so a store that was busy or
     * locked when the player opened is recovered by asking again rather than by leaving the user on a
     * dead button under 記録を読めません.
     */
    fun onRetryLoad()

    /** Clears [SessionUiState.fault] once the user has read it. Decides nothing else. */
    fun onDismissFault()
}
