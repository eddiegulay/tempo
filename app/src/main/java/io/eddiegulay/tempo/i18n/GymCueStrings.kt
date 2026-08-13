package io.eddiegulay.tempo.i18n

/**
 * Spoken cues and the foreground-service notification.
 *
 * **This file is owned by one migration unit.** Interface plus both implementations live together so
 * that adding a string is a single-file change and two people migrating different pages never touch
 * the same file. Add members here; never to `Strings.kt`, which only lists the namespaces.
 *
 * Rules for filling it, from `.planning/i18n/DECISIONS.md`:
 *
 * - A key names *what the string means*, never what it says.
 * - Japanese values are **transcribed, not authored** — the app ships them today and this move must be
 *   behaviour-neutral for Japanese.
 * - Anything built from a number or a date belongs on [Formats], not here.
 * - Do not fill a deliberate hole: several functions return null rather than invent a sentence the
 *   specs never wrote, and an English string in that position re-introduces the bug the null prevents.
 *
 * ## Two surfaces, and neither of them is a screen
 *
 * **Spoken phrasing is a separate translation surface from drawn text.** The five phrases below are
 * heard, once, over a workout, usually from a phone on the floor — so they are written for the ear and
 * are deliberately *not* the same words as the labels that mean the same thing. `03-player.md` §D.6's
 * whole channel is two words at a time, and English gets two words at a time.
 *
 * **The notification is rendered by SystemUI**, outside this app's theme, font and layout. Nothing here
 * can be measured, ellipsised or laid out by us, so every value is short enough to survive a lock
 * screen. They duplicate words the player also draws; that duplication is intentional — the player's
 * copy belongs to `gymSession` and the two are allowed to diverge, because a phase heading under a
 * 104sp clock and a phase word in a notification line are not the same slot.
 */
interface GymCueStrings {

    // ─── The spoken channel — `03-player.md` §D.2's speech column ───────────────────────────────

    /** [Cue.HALFWAY] — fired at the midpoint of a WORK segment of 20s or more. */
    val speakHalfway: String

    /** [Cue.LAST_ROUND] — the speech-only row; its focus window *is* this utterance. */
    val speakLastRound: String

    /** [Cue.EMOM_FAIL] — the EMOM minute closed over work that was not finished. */
    val speakTimeUp: String

    /**
     * [Cue.AMRAP_CAP] — the agreed cap fell and the AMRAP is over.
     *
     * Japanese says 終わり here and 終わり again for [speakSessionEnd]. **The two keys stay two keys.**
     * A merge is easy to make and hard to undo, and in English they already diverge: the cap is a clock
     * stopping mid-effort, the other is the session being finished.
     */
    val speakCapReached: String

    /** [Cue.SESSION_COMPLETE] — complete sessions only. §D.7 lets this one finish rather than cutting it off. */
    val speakSessionEnd: String

    // ─── The foreground-service notification ────────────────────────────────────────────────────

    /**
     * The notification channel's name, which the user reads in **Android's own settings**.
     *
     * Set at channel creation. Android caches it against the channel id, so it follows a language
     * change only when the channel is created again — which `TrainingService.ensureChannel` does on
     * every post, i.e. at the start of the next session and not before.
     */
    val channelName: String

    /** 支度 / 運動 / 運動・回数 / 休息 / 記録, and 休止 over any of them. One line, so one fact. */
    val phasePrepare: String
    val phaseWork: String
    val phaseReps: String
    val phaseRest: String

    /** Never posted — the service stops on the transition into it. Exists so the `when` stays exhaustive. */
    val phaseComplete: String
    val phasePaused: String

    /** The one action button. Always the reversible one, and always the opposite of the clock. */
    val controlPause: String
    val controlResume: String
}

internal object JaGymCue : GymCueStrings {

    override val speakHalfway = "半分"
    override val speakLastRound = "最後の巡"
    override val speakTimeUp = "時間切れ"
    override val speakCapReached = "終わり"
    override val speakSessionEnd = "終わり"

    override val channelName = "鍛錬"

    override val phasePrepare = "支度"
    override val phaseWork = "運動"

    /** Note the **absent** spaces around ・ — unlike every other join in the feature. Transcribed as-is. */
    override val phaseReps = "運動・回数"
    override val phaseRest = "休息"
    override val phaseComplete = "記録"
    override val phasePaused = "休止"

    override val controlPause = "休止"
    override val controlResume = "続ける"
}

internal object EnGymCue : GymCueStrings {

    // Written for the ear. A cue is heard once, from a phone on the floor, over breathing — so these
    // are the words a coach says out loud, not the words a button carries. "Halfway" rather than
    // "Half"; "Last round" rather than "Final".
    override val speakHalfway = "Halfway"
    override val speakLastRound = "Last round"
    override val speakTimeUp = "Time's up"

    // The two 終わり, diverged. The cap is a clock landing on an effort still in progress; the session
    // end is the whole thing being over. Japanese spells both the same and English does not have to.
    override val speakCapReached = "Done"
    override val speakSessionEnd = "Finished"

    // The same word as the launcher's mode dialog and the shell's first tab, because it names the same
    // thing in the same place a user goes looking for it.
    override val channelName = "Training"

    override val phasePrepare = "Get ready"
    override val phaseWork = "Work"

    // 運動・回数 distinguishes a rep station from a timed one; "Reps" carries that on its own and does
    // not drag a CJK middle dot onto a lock screen.
    override val phaseReps = "Reps"
    override val phaseRest = "Rest"
    override val phaseComplete = "Record"
    override val phasePaused = "Paused"

    override val controlPause = "Pause"
    override val controlResume = "Resume"
}
