package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.gym.Phase

/**
 * Everything the cue schedule needs to know about one compiled segment — and nothing else.
 *
 * This is deliberately **not** `Timeline.Segment`. The cue engine consumes six scalars and two names;
 * depending on the compiler's type would couple the one component that must be re-armed on every
 * transition to the one type that changes shape whenever an engine is added, and would make
 * [cueSchedule] untestable without building a whole routine to exercise a 3-2-1. The player maps its
 * `Segment` onto this in one place, at the call site.
 *
 * `03-player.md` §D.1's rule is the reason a schedule exists at all: cues are **derived from the
 * timeline, not from ticks**. A tick-driven cue engine fires late by up to a frame every time and
 * fires twice whenever two ticks straddle the same millisecond.
 *
 * @param plannedMs the segment's *effective* length, `addedMs` already included. ＋二十秒 moves the
 *   3-2-1, so the caller re-derives a segment and re-schedules rather than patching a pending post.
 * @param isFinalSegment suppresses the terminal cue: the last segment's ending is the session's
 *   ending, and [Cue.SESSION_COMPLETE] is fired on entering COMPLETE rather than scheduled here — a
 *   partial finish must not inherit a cue that was posted before the user decided to stop.
 * @param nextExerciseNameJa what [Cue.SESSION_START] and [Cue.INTERVAL_END] speak. Both rows of §D.2
 *   name the exercise that is *about to start*, which is the same string from both sides of the
 *   boundary.
 * @param capAtMs offset within this segment at which the AMRAP cap falls, or null. Absolute in
 *   session terms it is `prepareMs + capMs`; the caller subtracts the segment start so that
 *   everything in this file is one segment's own clock.
 * @param open mirrors `Timeline.Segment.open` — self-paced, ending on 済 rather than on a clock. §B.1
 *   says it outright: for an open segment "`plannedMs` is a pacing estimate and is **NOT
 *   authoritative**". Everything a clock-driven cue asserts is false here, which is why [cueSchedule]
 *   suppresses the countdown and the terminal cue.
 * @param anchored mirrors `Timeline.Segment.anchored` — the segment belongs to a fixed grid, so its
 *   end *is* a clock even when it is open. An anchored open segment is REPS state 5, the EMOM minute
 *   window, where §A says plainly that "the boundary decides"; it keeps every clock-driven cue.
 */
data class CueSegment(
    val ordinal: Int,
    val phase: Phase,
    val plannedMs: Long,
    val isFinalSegment: Boolean = false,
    val startsFinalRound: Boolean = false,
    val nextExerciseNameJa: String? = null,
    val capAtMs: Long? = null,
    val open: Boolean = false,
    val anchored: Boolean = false,
)

/**
 * A span of audio focus, in the segment's own milliseconds.
 *
 * Focus is a span rather than a per-cue flag because §D.4's whole argument is about spans: four
 * request/abandon cycles inside three seconds thrashes the ducking ramp and sounds like a stutter, so
 * the 3-2-1 and the interval end must share **one** window. [mergeFocusWindows] is what makes that
 * fall out of arithmetic instead of a special case.
 */
data class FocusSpan(val openAtMs: Long, val closeAtMs: Long) {
    val durationMs: Long get() = closeAtMs - openAtMs
}

/** One cue, placed on the segment's clock. [speech] is already resolved — fixed phrase or name. */
data class ScheduledCue(
    val cue: Cue,
    val atMs: Long,
    val speech: String? = null,
    val focus: FocusSpan? = null,
)

/**
 * How far ahead of a sound the duck is asked for, and how long the tail is held after it.
 *
 * 150ms is §D.2's own lead on every cue it can see coming; the cues it cannot (a fail-out, a cap, a
 * finish) open at zero, because a window that opens before an event we only learn about at the event
 * is not a thing that can exist.
 */
private const val FOCUS_LEAD_MS = 150L

/** §D.2's 3-2-1 trigger, and the threshold below which it is suppressed rather than fired late. */
const val COUNT_TICK_LEAD_MS = 3_000L
const val COUNT_TICK_MIN_SEGMENT_MS = 3_500L

/** §D.2: 半分 fires only on a WORK segment long enough for a midpoint to mean anything. */
const val HALFWAY_MIN_SEGMENT_MS = 20_000L

/**
 * The audio-focus window §D.2 gives this cue, placed around [atMs] — or null where the row gives none.
 *
 * The three null rows ([Cue.REP_DONE], [Cue.EXTEND_ACK], [Cue.SKIP_BACK_ARMED]) make no sound at all.
 * [Cue.LAST_ROUND] is null for a different reason and it matters: its row reads *"speech only"*, so
 * its window is opened and closed by the utterance itself (§D.6's `UtteranceProgressListener` at
 * `onDone`), not by a duration this file would have had to invent.
 */
fun focusSpanFor(cue: Cue, atMs: Long): FocusSpan? = when (cue) {
    Cue.SESSION_START -> FocusSpan(atMs - FOCUS_LEAD_MS, atMs + 450)
    Cue.COUNT_TICK -> FocusSpan(atMs - FOCUS_LEAD_MS, atMs + 3_450)
    Cue.INTERVAL_END -> FocusSpan(atMs - FOCUS_LEAD_MS, atMs + 450)
    Cue.HALFWAY -> FocusSpan(atMs - FOCUS_LEAD_MS, atMs + 350)
    Cue.EMOM_FAIL -> FocusSpan(atMs, atMs + 550)
    Cue.AMRAP_CAP -> FocusSpan(atMs, atMs + 750)
    Cue.SESSION_COMPLETE -> FocusSpan(atMs, atMs + 900)
    Cue.LAST_ROUND, Cue.REP_DONE, Cue.EXTEND_ACK, Cue.SKIP_BACK_ARMED -> null
}

/**
 * Every cue this segment will fire, on the segment's own clock, in trigger order.
 *
 * Pure, and pure on purpose: this is the half of the cue engine that can be wrong silently. A 3-2-1
 * that fires on a two-second station, a 半分 on a rest, or an interval end on the final segment are
 * all mistakes a device test would show as "sounded a bit odd" and a table test shows as a failure.
 *
 * The schedule ignores the user's channel preferences entirely. That is not an oversight — what the
 * timeline *contains* and what the user has *switched on* are different questions, and folding them
 * together would mean re-deriving the schedule when a preference changes mid-session. The engine asks
 * [CueSettings] per channel at the moment of firing; [focusWindows] asks it once, because focus is a
 * property of the speaker and not of the cue.
 *
 * Terminal-cue rule, which is the one place three §D.2 rows overlap:
 * - 支度 ends in [Cue.SESSION_START] — §A edge case 3, *the session-start cue fires at the end of
 *   prepare*, so PREPARE never also fires an interval end;
 * - the final segment ends in nothing, because COMPLETE owns that moment;
 * - a self-paced segment ends in nothing either, for the reason below;
 * - everything else ends in [Cue.INTERVAL_END].
 *
 * **A self-paced segment has no clock to cue against, and this is the rule that says so.** §B.1: for an
 * `open` segment `plannedMs` "is a pacing estimate and is **NOT** authoritative", and §A REPS state 2
 * spells out what happens when it runs out with the default settings — *"enter `Overrun`; nothing
 * advances"*. Before this rule the estimate hitting zero fired a full 3-2-1 and then the interval end's
 * 400ms full-amplitude buzz, its `TONE_PROP_BEEP2` and the next exercise's name, **mid-set**, for a
 * transition that was not happening. §A is equally plain that "nothing about overrun is a failure";
 * counting the user down to it is the loudest possible way to say otherwise.
 *
 * The exception is an **anchored** open segment — REPS state 5, the EMOM minute window, where the
 * estimate is replaced by the minute remaining, "overrun is impossible; the boundary decides". There
 * the clock is real, so the countdown and the boundary cue are real too.
 */
fun cueSchedule(segment: CueSegment): List<ScheduledCue> {
    val out = mutableListOf<ScheduledCue>()
    val planned = segment.plannedMs

    // Self-paced: ends on 済, not on a clock. An anchored open cell is on a grid and so is exempt.
    val selfPaced = segment.open && !segment.anchored

    if (segment.startsFinalRound) out += scheduled(Cue.LAST_ROUND, 0L)

    if (segment.phase == Phase.WORK && planned >= HALFWAY_MIN_SEGMENT_MS) {
        out += scheduled(Cue.HALFWAY, planned / 2)
    }

    // §A, WORK edge case 1: a 2s station suppresses the 3-2-1 entirely rather than firing it late.
    if (!selfPaced && planned >= COUNT_TICK_MIN_SEGMENT_MS) {
        out += scheduled(Cue.COUNT_TICK, planned - COUNT_TICK_LEAD_MS)
    }

    // The cap survives on a self-paced segment: it is a hard boundary the user agreed to, not an
    // estimate, and §A REPS names the AMRAP cap as one of the two things that can end an open segment.
    segment.capAtMs?.let { cap -> if (cap in 0..planned) out += scheduled(Cue.AMRAP_CAP, cap) }

    val terminal = when {
        segment.isFinalSegment -> null
        segment.phase == Phase.PREPARE -> Cue.SESSION_START
        selfPaced -> null
        else -> Cue.INTERVAL_END
    }
    if (terminal != null) out += scheduled(terminal, planned, segment.nextExerciseNameJa)

    return out.sortedBy { it.atMs }
}

private fun scheduled(cue: Cue, atMs: Long, dynamicSpeech: String? = null) =
    ScheduledCue(cue = cue, atMs = atMs, speech = cue.speech ?: dynamicSpeech, focus = focusSpanFor(cue, atMs))

/**
 * The focus windows a schedule actually needs, overlapping ones merged into one request.
 *
 * This is where §D.4's "the 3-2-1 and the interval-end share one focus window (open T−3150, close
 * T+450)" comes from — not from a rule that names those two cues, but from the arithmetic: their spans
 * touch, so they merge. Adding a cue between them changes nothing, and removing the 3-2-1 (a short
 * segment) leaves the interval end with its own −150/+450, exactly as §D.2's row says it should.
 *
 * *Rejected* — hard-coding the pair. It would have been three lines and it would have been wrong the
 * first time a routine had a 3.4-second station, where the 3-2-1 is suppressed and the interval end
 * must fall back to its own window.
 *
 * Windows are dropped entirely when tones are off: speech carries its own window, opened and dropped
 * by the utterance listener, and requesting a duck for silence is how a launcher earns a reputation
 * for interrupting music.
 */
fun focusWindows(schedule: List<ScheduledCue>, tonesEnabled: Boolean): List<FocusSpan> {
    if (!tonesEnabled) return emptyList()
    val spans = schedule.mapNotNull { it.focus }.sortedBy { it.openAtMs }
    return mergeFocusWindows(spans)
}

/** Merges touching or overlapping spans. Input need not be sorted; output is, and is disjoint. */
fun mergeFocusWindows(spans: List<FocusSpan>): List<FocusSpan> {
    if (spans.isEmpty()) return emptyList()
    val sorted = spans.sortedBy { it.openAtMs }
    val out = mutableListOf<FocusSpan>()
    var current = sorted.first()
    for (next in sorted.drop(1)) {
        current = if (next.openAtMs <= current.closeAtMs) {
            FocusSpan(current.openAtMs, maxOf(current.closeAtMs, next.closeAtMs))
        } else {
            out += current
            next
        }
    }
    out += current
    return out
}
