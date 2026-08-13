package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.Strings

/*
 * What the health foreground service's notification says — `00-plan.md` §6 Phase 3, `03-player.md` §E.5.
 *
 * §E.5 states Phase 1's position and what changes when this lands: *"the only thing that changes in
 * this spec is the disarm matrix (§D.7) — the state machine, the timeline, and the persistence
 * schedule are already service-agnostic."* This file is the other half of that sentence: the notice is
 * a **projection of the state the player already publishes**, so nothing new is persisted, nothing new
 * ticks, and the service can be started, updated and stopped from one value.
 *
 * **Every word is a word the player already says.** `03-player.md` §A's five phase headings supply
 * 支度 / 運動 / 運動・回数 / 休息 / 記録; §A PAUSED supplies 休止; §A WORK's accessibility table gives the
 * two controls their names outright — `┃┃ = "休止"`, and `PausedPage` answers with 続ける. Nothing on
 * the notification is copy invented for a notification.
 *
 * **Why so little of it.** Two candidate rows were dropped rather than written:
 *
 * - **とばす.** It is a real control on screen (`▷`), and from a pocket it is a station silently lost
 *   with no ensō to watch it go. A skip is a decision that writes a `SegmentResult`; the notification
 *   is not where decisions of that class belong.
 * - **鍛錬を終える.** Ending a session is `03` §A QUIT_SHEET's job, and that sheet exists precisely
 *   because 記録せずに終える must be asked twice. A notification action cannot ask twice, and a
 *   one-tap discard on a lock screen is the one irreversible thing in the whole feature.
 *
 * That leaves the pair that are pure clock control, are individually reversible, and are exactly what
 * a user reaching into a pocket mid-plank wants.
 */

/** The one control the notification carries, which of the two it is decided by [noticeControl]. */
enum class TrainingControl {
    /** ┃┃ — `03-player.md` §A WORK, accessibility. */
    PAUSE,

    /** 続ける — §A PAUSED. May route through the 3s 支度; the machine decides, not the notification. */
    RESUME,
}

/**
 * Everything the notification draws, and **nothing that ticks**.
 *
 * The brief on this component is that it *"must not tick every second — a per-second notification
 * update is a battery and jank problem"*, and the enforcement is structural rather than a rate limit:
 * this type has no remaining-time field, no elapsed field and no fraction, so a caller projecting a
 * `SessionUiState` sixty times a minute produces sixty **equal** values and the mount's
 * `LaunchedEffect(notice)` restarts on none of them. The notification is rewritten when, and only
 * when, one of these four facts changes — which is a phase transition, a pause, or a new station.
 *
 * **The guarantee is only as good as the test that watches the shape of this type, so the test watches
 * the shape.** `TrainingNoticeTest`'s `the notice carries no field that can tick` asserts over
 * `declaredFields`, because the obvious assertion — two projections of one segment are equal — is true
 * of every data class and would stay green after a fifth parameter with a default value was added.
 *
 * *Rejected* — `setUsesChronometer(true)`, which would show live elapsed time at zero update cost
 * because SystemUI renders it. It is genuinely free, and it is still wrong here: the number it counts
 * is wall time since the notification was posted, which is **not** `activeMs` — it keeps running
 * through 休止 and through every pause the user takes, so the one number on the notification would
 * disagree with the one number on the record screen. A wrong free number is worse than no number.
 */
data class TrainingNotice(
    val routineName: String,
    val phase: Phase,
    val paused: Boolean,
    /** The movement under way, when the phase has one. Null on 支度 and on every rest. */
    val exerciseName: String?,
)

/**
 * 支度 / 運動 / 運動・回数 / 休息 / 記録 — `03-player.md` §A's own page headings, and 休止 over any of them.
 *
 * The pause wins over the phase for the same reason `SessionOverlayUi.Paused` is an overlay rather
 * than a sixth phase: 休止 *renders the phase underneath it*, and a notification has one line, so the
 * line has to carry the fact that changed. A user glancing at a lock screen needs to know the clock
 * has stopped far more than they need to know which kind of segment it stopped in.
 *
 * [Phase.COMPLETE] has a label because the enum has the case, not because it is ever posted: the
 * service is stopped on the transition into 記録 (there is nothing left to keep the process alive
 * for), so this arm exists to be exhaustive rather than to be seen.
 *
 * **The pause is an `if` and the phase is a `when`, and that shape is the point.** Written as one flat
 * `when { paused -> …; phase == … }` chain it needed a trailing `else`, and a bare `else` over an enum
 * is precisely what forfeits exhaustiveness: a sixth `Phase` added in Phase 4 would have rendered
 * silently as 記録 on a live notification instead of failing to compile here.
 */
fun noticePhaseLabel(phase: Phase, paused: Boolean, strings: Strings): String =
    if (paused) strings.gymCue.phasePaused else when (phase) {
        Phase.PREPARE -> strings.gymCue.phasePrepare
        Phase.WORK -> strings.gymCue.phaseWork
        Phase.REPS -> strings.gymCue.phaseReps
        Phase.REST -> strings.gymCue.phaseRest
        Phase.COMPLETE -> strings.gymCue.phaseComplete
    }

/**
 * The title: the routine, and only the routine.
 *
 * It is the same word the ensō carries on every player screen, so a notification and the screen behind
 * it name the session identically. `routine_name` is `NOT NULL` and denormalised onto the session row
 * (`00-plan.md` §2 row 10), so there is no empty case to defend against here.
 */
fun noticeTitle(notice: TrainingNotice): String = notice.routineName

/**
 * `運動 ・ 腕立て伏せ`, or just `休息`, or just `休止`.
 *
 * The join is the feature's own (「七分間 ・ 六分十四秒 ・ 八種目」, `03` §A's resume prompt) and comes
 * from `fmt.separator`, not from a literal — the middle dot is CJK punctuation, and Latin typography
 * spells the same join `·`. The exercise clause is dropped exactly when the player itself has no
 * movement to name — 支度 and every rest, where `SessionUiState.exercise` is null. A paused session
 * drops it too: 休止 is the whole of what is true, and 「休止 ・ 腕立て伏せ」 would read as a push-up in
 * progress.
 *
 * The exercise name itself is **not** translated here. It arrives already resolved from the catalogue
 * and is `catalog.*`'s to choose between `nameJa` and `nameEn`; a user-authored routine's is their own
 * data either way.
 */
fun noticeText(notice: TrainingNotice, strings: Strings): String {
    val phase = noticePhaseLabel(notice.phase, notice.paused, strings)
    val exercise = notice.exerciseName?.takeIf { !notice.paused && it.isNotBlank() }
    return if (exercise == null) phase else phase + strings.fmt.separator + exercise
}

/** 休止 while running, 続ける while paused. Exactly one action, and it is always the reversible one. */
fun noticeControl(notice: TrainingNotice): TrainingControl =
    if (notice.paused) TrainingControl.RESUME else TrainingControl.PAUSE

/** The button's word — `03` §A WORK's `┃┃ = "休止"` and §A PAUSED's 続ける, unchanged. */
fun noticeControlLabel(control: TrainingControl, strings: Strings): String = when (control) {
    TrainingControl.PAUSE -> strings.gymCue.controlPause
    TrainingControl.RESUME -> strings.gymCue.controlResume
}

/**
 * `七分間、運動 ・ 腕立て伏せ` — the notification as one sentence, for the reader that speaks it.
 *
 * TalkBack reads a notification's title and text as one utterance anyway; stating the join here means
 * the order is asserted rather than left to the platform, and it is the same `fmt.listSeparator` — 、
 * in Japanese, a comma in English — that the record screen's merged nodes use
 * (`RecordSummary.recordHeroSemantics`).
 */
fun noticeSemantics(notice: TrainingNotice, strings: Strings): String =
    noticeTitle(notice) + strings.fmt.listSeparator + noticeText(notice, strings)
