package io.eddiegulay.tempo.ui.gym.session

import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.clockDuration
import io.eddiegulay.tempo.gym.durationKanjiFromMs
import io.eddiegulay.tempo.gym.session.RestKind
import io.eddiegulay.tempo.gym.session.Segment
import kotlin.math.roundToInt

/*
 * Every word the five live player pages and the quit sheet put on screen — `03-player.md` §A.
 *
 * It is one file, Android-free and JUnit-tested, for the reason `00-plan.md` §4.1 rule 7 makes copy a
 * page's own business and `04-library-records.md` §7 still gathers the string tables: **the words are
 * shared across pages and the layouts are not.** 「三巡目 ・ 四種目中 三」 sits on WORK, REPS and REST at
 * the same pixel, and a page that formatted its own would be the page that disagreed about 最後の巡.
 *
 * Two rules run through the whole file.
 *
 * 1. **Every string here is transcribed from a spec table.** Where the spec gives a shape
 *    (「次 ・ 休息 十五秒 → プランク」) the function composes the same shape from its own documented
 *    fragments; where it gives nothing, the function returns **null** and the page renders the slot
 *    empty. Nothing is invented — see [repDoneDescription] and [nextUpLabel] for the two places that
 *    bit.
 * 2. **`DECISIONS.md` §Q4's numeral split, applied without exception.** A *ticking* value is arabic —
 *    the countdown, the pacer, the ＋0:07 overrun — because a kanji column changing under a shaking
 *    hand is unreadable. Everything that has stopped moving is kanji. `DECISIONS.md` §Q10 then splits
 *    the kanji half again: a duration the user **chose** (a rest length, a station's 三十秒) renders as
 *    bare seconds through [chosenSecondsLabel]; a duration the app **measured** (活動時間) renders
 *    through `durationKanjiFromMs`. Setting and reading are different acts.
 */

/** Announced once on crossing the pacing estimate, then silent — §A REPS, accessibility. */
const val OVERRUN_ANNOUNCEMENT: String = "目安を過ぎました"

/** §A REST, accessibility. The button says ＋二十秒; TalkBack says what it does. */
const val EXTEND_REST_LABEL: String = "＋二十秒"

/** §A REST's inline "I'm ready, go" — the semantic skip, beside the bar's structural one. */
const val SKIP_REST_LABEL: String = "とばす ▷"

/**
 * Seconds, always rounded **up**.
 *
 * A countdown that truncates shows 0:29 for the first second of a thirty-second station and never
 * shows 0:30 at all, then sits on 0:00 for a whole second before the boundary. Rounding up puts the
 * numeral where the user's own counting is: the digit changes when a second is *gone*, and 0:00 is
 * the instant the segment ends rather than a second of dead air before it.
 */
private fun ceilSeconds(ms: Long): Long = if (ms <= 0L) 0L else (ms + 999L) / 1000L

/**
 * The player's countdown — `0:23`, arabic, `03-player.md` shared vocabulary and `DECISIONS.md` §Q4.
 *
 * Shaped by `Numerals.clockDuration` rather than by a second `%d:%02d` here: the breakdown's 0:41 and
 * this numeral have to be the same form, and two format strings are two forms the day one of them
 * grows a zero-padded minute.
 */
fun formatCountdown(remainingMs: Long): String = clockDuration(ceilSeconds(remainingMs) * 1000L)

/**
 * 支度's numeral: a **bare integer**, not `0:03` (§A PREPARE's mock says so explicitly).
 *
 * Five seconds is not a duration you read off a clock face, it is a count you say out loud, and the
 * colon form would make the last five seconds of every session look like the last five seconds of a
 * plank.
 */
fun prepareNumeral(remainingMs: Long): String = ceilSeconds(remainingMs).toString()

/** A duration the user **set**, in bare kanji seconds — `DECISIONS.md` §Q10. 六十秒, never 一分. */
fun chosenSecondsLabel(ms: Long): String =
    JapaneseDate.kanjiExtended(ceilSeconds(ms).toInt()) + "秒"

/**
 * 「三巡目 ・ 四種目中 三」, and 「最後の巡 ・ 四種目中 三」 on the final round's first effort (§A WORK
 * state 4).
 *
 * Both clauses are optional and the line is stable across a phase change, which is the whole point of
 * building it here rather than per page:
 *
 * - the 巡 clause is **dropped on a single-round routine** — 七分間 is twelve stations in one lap, and
 *   「一巡目」 on a routine with one lap is chrome, exactly as `LAST_ROUND` is suppressed for it
 *   ([startsFinalRound]);
 * - the 種目 clause needs a station number, and a rest has none ([Segment.stationIndex] is null on
 *   every rest the compiler emits). The caller passes the **coming** station's index there, because
 *   that is the station the rest's hero already names; blanking the clause instead would make the one
 *   line above the ensō flicker at every transition, which is the layout jump §3.1 forbids.
 *
 * Returns null when neither clause applies, and the page then draws nothing rather than a stray ・.
 */
fun counterLabel(
    round: Int,
    totalRounds: Int,
    station: Int?,
    stationsPerRound: Int,
    lastRound: Boolean,
): String? {
    val rounds = when {
        totalRounds <= 1 -> null
        lastRound -> "最後の巡"
        else -> JapaneseDate.kanjiExtended(round) + "巡目"
    }
    val stations = if (station != null && stationsPerRound > 0) {
        JapaneseDate.kanjiExtended(stationsPerRound) + "種目中 " + JapaneseDate.kanjiExtended(station + 1)
    } else {
        null
    }
    return listOfNotNull(rounds, stations).takeIf { it.isNotEmpty() }?.joinToString(" ・ ")
}

/**
 * 「三巡目 / 十二巡」 — the words that replace the dots past nine rounds (`03-player.md` :172,
 * `DECISIONS.md` §Q8).
 *
 * The predicate is `cycleDotsOverflow`'s and lives with the component; the counter word is copy and
 * lives here, which is the split `CycleDots` documents at length.
 */
fun roundsOverflowLabel(round: Int, totalRounds: Int): String =
    JapaneseDate.kanjiExtended(round) + "巡目 / " + JapaneseDate.kanjiExtended(totalRounds) + "巡"

/** The dots' one merged node — §A WORK, accessibility. */
fun cycleDotsLabel(round: Int, totalRounds: Int): String =
    JapaneseDate.kanjiExtended(round) + "巡目、" + JapaneseDate.kanjiExtended(totalRounds) + "巡中"

/**
 * 「全体 四十パーセント」 — the session hairline's description, which is the only thing that reads it.
 *
 * Rounded to a whole percent because that is the form §A WORK's accessibility note writes, and because
 * a hairline announced to a tenth of a percent would be a number nobody can act on.
 */
fun progressLabel(fraction: Float): String {
    val percent = if (fraction.isNaN()) 0 else (fraction * 100f).roundToInt().coerceIn(0, 100)
    return "全体 " + JapaneseDate.kanjiExtended(percent) + "パーセント"
}

/**
 * 「次 ・ 休息 十五秒 → プランク」, 「次 ・ プランク」 (§A WORK state 7), 「次 ・ 完了」 (state 6).
 *
 * Null is a fourth case the spec does not name and cannot be given a word: a next segment whose
 * exercise the catalogue no longer knows. 「次 ・ 完了」 would be a lie (there is more session), and
 * inventing 「次 ・ 種目」 would be inventing copy, so the slot is left blank and the chrome does not
 * move — the same fixed-slot bargain §A REST edge case 5 strikes for a missing form cue.
 */
fun nextUpLabel(next: Segment?, nextExerciseName: String?): String? = when {
    next == null -> "次 ・ 完了"
    next.phase == Phase.REST -> {
        val rest = "次 ・ 休息 " + chosenSecondsLabel(next.effectiveMs)
        if (nextExerciseName == null) rest else "$rest → $nextExerciseName"
    }

    nextExerciseName != null -> "次 ・ $nextExerciseName"
    else -> null
}

/**
 * The same line for TalkBack, with 「そのあと」 spelled out — 「→」 reads as nothing (§A WORK,
 * accessibility).
 */
fun nextUpDescription(next: Segment?, nextExerciseName: String?): String? = when {
    next == null -> "次、完了"
    next.phase == Phase.REST -> {
        val rest = "次、休息 " + chosenSecondsLabel(next.effectiveMs)
        if (nextExerciseName == null) rest else "$rest、そのあと $nextExerciseName"
    }

    nextExerciseName != null -> "次、$nextExerciseName"
    else -> null
}

/**
 * 「残り 三十秒」 / 「残り 十秒」, and **nothing else** — §A WORK, accessibility.
 *
 * The countdown is not a live region; this is the invisible node beside it, and it changes value at
 * most twice per segment so TalkBack speaks at most twice. Anything finer fights the tones and never
 * finishes a phrase before the next one starts.
 *
 * **A threshold the segment never had is a false statement, not an early one**, which is why
 * [plannedMs] is a parameter and not an optimisation. Branching on what is *left* alone announced
 * 「残り 三十秒」 the instant any interval shorter than thirty seconds opened — and タバタ, a seeded
 * built-in, is twenty-second intervals eight times over, so a TalkBack user was told thirty seconds
 * remained at the top of every one of them. §A WORK says "announces at 30s, 10s, and 0 only"; a
 * twenty-second interval passes through neither 30s nor its own opening frame at 30s, so it announces
 * once, at ten.
 *
 * The comparison is strict — a segment planned at exactly thirty seconds does not announce 三十秒 at
 * its own start, because "thirty seconds remain" is the whole of what it has and telling the user so
 * before they have moved is the same false-because-vacuous statement one frame earlier.
 *
 * The spec's third announcement — 「次、休息 十五秒、プランク」 at zero — is deliberately **not** emitted
 * here. At zero the segment closes and the arriving phase announces itself on the same frame (a rest
 * speaks [restAnnouncement], a station speaks its own name), which is the same fact at the same
 * instant; emitting both would queue two utterances for one boundary and the second would be spoken
 * over the interval-end tone.
 *
 * @param plannedMs the segment's own length — `Segment.effectiveMs`, so ＋二十秒 counts.
 */
fun countdownAnnouncement(remainingMs: Long, plannedMs: Long): String? = when {
    remainingMs in 10_001L..30_000L && plannedMs > 30_000L ->
        "残り " + JapaneseDate.kanjiExtended(30) + "秒"

    remainingMs in 1L..10_000L && plannedMs > 10_000L ->
        "残り " + JapaneseDate.kanjiExtended(10) + "秒"

    else -> null
}

/**
 * What a station asks for, under its name: 「三十秒」, 「二十回」, 「限界まで」.
 *
 * A rep count wins over a duration even inside a circuit, because 七分間's rep stations *are* rep
 * stations — the fixed box is the protocol's pacing, and the number the user works to is the count.
 * An open segment with no count is 限界まで (§A REPS state 3), and its estimate is never printed here:
 * `DEFAULT_MAX_EFFORT_ESTIMATE_MS` is a pacer and would read as a prescription.
 */
fun prescriptionLabel(segment: Segment?): String? {
    if (segment == null) return null
    val reps = segment.prescribedReps
    return when {
        reps != null -> JapaneseDate.kanjiExtended(reps) + "回"
        segment.open -> "限界まで"
        segment.plannedMs > 0L -> chosenSecondsLabel(segment.plannedMs)
        else -> null
    }
}

/** REPS' hero: 「二十回」 in kanji, or 「限界まで」 (§A REPS states 1 and 3). */
fun repHero(prescribedReps: Int?): String =
    prescribedReps?.let { JapaneseDate.kanjiExtended(it) + "回" } ?: "限界まで"

/**
 * 「済、二十回として記録」 — §A REPS, accessibility.
 *
 * A 限界まで set gets the bare 「済」. 「限界までとして記録」 is not in any table and would be a sentence
 * this file made up about the one prescription that has no number.
 */
fun repDoneDescription(prescribedReps: Int?): String =
    prescribedReps?.let { "済、" + JapaneseDate.kanjiExtended(it) + "回として記録" } ?: "済"

/**
 * The line under the rep hero: 「目安 0:38」 pacing, 「＋0:07」 past it, 「残り 0:22」 inside an EMOM
 * minute (§A REPS states 1, 2, 5).
 *
 * Overrun counts **up** and therefore truncates, where the countdown rounds up: seven and a half
 * seconds over is seven seconds you have actually spent, and rounding it up would claim a second that
 * has not happened. Null on 限界まで, whose state has no estimate line at all.
 */
fun pacerLabel(
    prescribedReps: Int?,
    remainingMs: Long,
    overrunMs: Long,
    emomWindow: Boolean,
): String? = when {
    emomWindow -> "残り " + formatCountdown(remainingMs)
    prescribedReps == null -> null
    overrunMs > 0L -> "＋" + clockDuration(overrunMs)
    else -> "目安 " + formatCountdown(remainingMs)
}

/**
 * The ring's word on a rest — 休息 / 巡の間 / 決められた休息 / 残り (§A REST states 1, 3, 4, 5).
 *
 * Each one is doing work. 巡の間 replaces the hidden dots during a round rest; 決められた休息 is the
 * reason the controls beside it are dead; 残り says the grid is anchored and this is what is left of
 * the minute rather than a rest anybody granted.
 */
fun restLabel(kind: RestKind?): String = when (kind) {
    RestKind.ROUND -> "巡の間"
    RestKind.MANDATED -> "決められた休息"
    RestKind.EMOM_REMAINDER -> "残り"
    RestKind.STATION, null -> "休息"
}

/** 「＋0:20」, the accent suffix that makes added time visible in the record's mental model (state 2). */
fun extendedSuffix(addedMs: Long): String? =
    if (addedMs <= 0L) null else "＋" + clockDuration(addedMs)

/** 「四十秒 追加済み」 — the ＋二十秒 control's `stateDescription` once it has been tapped. */
fun addedStateDescription(addedMs: Long): String? =
    if (addedMs <= 0L) null else JapaneseDate.kanjiExtended((addedMs / 1000L).toInt()) + "秒 追加済み"

/** 「二十秒 追加」 — what ＋二十秒 *does*, for TalkBack (§A REST, accessibility). */
fun extendRestDescription(): String = JapaneseDate.kanjiExtended(20) + "秒 追加"

/**
 * 「とばす、決められた休息のため使えません」 — a disabled control states its reason (§A REST, accessibility).
 *
 * Only MANDATED has one written, and only MANDATED renders disabled: an EMOM remainder **hides** its
 * controls instead (state 5), so there is nothing there to describe.
 */
fun skipDisabledDescription(kind: RestKind?): String? =
    if (kind == RestKind.MANDATED) "とばす、決められた休息のため使えません" else null

/**
 * The same sentence for the other control §A REST state 4 disables — 「二十秒 追加、決められた休息のため
 * 使えません」.
 *
 * The spec writes the reason clause once, against とばす, and disables **both** controls in the same
 * breath. Reusing that clause under the control's own documented name is applying documented copy in
 * the way `DECISIONS.md` §Q6 authorises for `SessionGone`: the grammar is unchanged and both halves
 * are the string table's own. Inventing a second, differently worded reason for the neighbouring
 * button would be the actual divergence.
 */
fun extendDisabledDescription(kind: RestKind?): String? =
    if (kind == RestKind.MANDATED) extendRestDescription() + "、決められた休息のため使えません" else null

/** 「六分十四秒 経過」 — session **active** time, so 支度 is already excluded (§A PAUSED). */
fun elapsedLine(activeMs: Long): String = durationKanjiFromMs(activeMs) + " 経過"

/** 「八種目 ・ 二巡 済」. Null before anything has accrued, which is `PausedDuringPrepare`'s hidden line. */
fun accruedLine(stationsCompleted: Int, roundsCompleted: Int): String? {
    if (stationsCompleted <= 0 && roundsCompleted <= 0) return null
    val stations = JapaneseDate.kanjiExtended(stationsCompleted) + "種目"
    if (roundsCompleted <= 0) return "$stations 済"
    return stations + " ・ " + JapaneseDate.kanjiExtended(roundsCompleted) + "巡 済"
}

/** 「休止中、六分十四秒 経過、八種目 済」 — announced once on entering 休止. */
fun pausedAnnouncement(activeMs: Long, stationsCompleted: Int): String =
    "休止中、" + durationKanjiFromMs(activeMs) + " 経過、" +
        JapaneseDate.kanjiExtended(stationsCompleted) + "種目 済"

/** 「残り 二十三秒、休止中」 — the frozen numeral, which is **not** a live region. */
fun pausedNumeralDescription(remainingMs: Long): String =
    "残り " + JapaneseDate.kanjiExtended(ceilSeconds(remainingMs).toInt()) + "秒、休止中"

/** 「支度、五秒後に ジャンピングジャック」 — one announcement on entry, and only one. */
fun prepareAnnouncement(remainingMs: Long, exerciseName: String?): String {
    val head = "支度、" + JapaneseDate.kanjiExtended(ceilSeconds(remainingMs).toInt()) + "秒後に"
    return if (exerciseName == null) "支度" else "$head $exerciseName"
}

/**
 * 「休息 十五秒、次は プランク、三十秒」 with the form cue appended — §A REST, accessibility.
 *
 * The cue is appended rather than dropped because it is one clause and it is the useful part: it is
 * the only moment in the session when the user is not yet moving and can still act on it.
 */
fun restAnnouncement(
    kind: RestKind?,
    restMs: Long,
    nextExerciseName: String?,
    prescription: String?,
    cue: String?,
): String = buildString {
    append(restLabel(kind))
    append(" ")
    append(chosenSecondsLabel(restMs))
    if (nextExerciseName != null) {
        append("、次は ")
        append(nextExerciseName)
        if (prescription != null) {
            append("、")
            append(prescription)
        }
    }
    if (cue != null) {
        append("、")
        append(cue)
    }
}

/** 「腕立て伏せ、二十回」 — the REPS hero as one node (§A REPS, accessibility). */
fun repHeroDescription(exerciseName: String?, prescribedReps: Int?): String =
    listOfNotNull(exerciseName, repHero(prescribedReps)).joinToString("、")

/** 「六分十四秒 ・ 二十種目中 八」 — the quit sheet's subtitle (§A QUIT_SHEET). */
fun quitSummaryLine(activeMs: Long, stationsCompleted: Int, stationsPlanned: Int): String =
    durationKanjiFromMs(activeMs) + " ・ " +
        JapaneseDate.kanjiExtended(stationsPlanned) + "種目中 " +
        JapaneseDate.kanjiExtended(stationsCompleted)

/**
 * Which rows the quit sheet shows, and how hard it asks — §A QUIT_SHEET's `Standard` and
 * `NothingToSave`.
 *
 * Three outcomes collapse to two when nothing has been recorded: there is nothing to save, so
 * ここまでを記録する is **omitted** rather than disabled, and the destructive row softens to 終える —
 * it is not destroying anything. The second ask goes with it: 「a single mis-tap must not destroy 14
 * minutes of work」 is an argument about work that exists.
 *
 * つづける is in **every** shape. It is the escape, it is never labelled やめる, and it is the one row
 * that must never be conditional.
 */
data class QuitSheetOptions(
    val canRecord: Boolean,
    val discardLabel: String,
    val confirmsDiscard: Boolean,
    val subtitleIsWarning: Boolean,
)

fun quitOptions(resultsWritten: Int): QuitSheetOptions =
    if (resultsWritten >= 1) {
        QuitSheetOptions(
            canRecord = true,
            discardLabel = "記録せずに終える",
            confirmsDiscard = true,
            subtitleIsWarning = false,
        )
    } else {
        QuitSheetOptions(
            canRecord = false,
            discardLabel = "終える",
            confirmsDiscard = false,
            subtitleIsWarning = true,
        )
    }
