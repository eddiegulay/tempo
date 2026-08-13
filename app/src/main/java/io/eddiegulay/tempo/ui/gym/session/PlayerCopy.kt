package io.eddiegulay.tempo.ui.gym.session

import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.session.REST_EXTENSION_MS
import io.eddiegulay.tempo.gym.session.RESUME_PREPARE_MS
import io.eddiegulay.tempo.gym.session.RestKind
import io.eddiegulay.tempo.gym.session.Segment
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.Strings
import kotlin.math.roundToInt

/*
 * Every word the five live player pages and the quit sheet put on screen — `03-player.md` §A.
 *
 * It is one file, Android-free and JUnit-tested, for the reason `00-plan.md` §4.1 rule 7 makes copy a
 * page's own business and `04-library-records.md` §7 still gathers the string tables: **the words are
 * shared across pages and the layouts are not.** 「三巡目 ・ 四種目中 三」 sits on WORK, REPS and REST at
 * the same pixel, and a page that formatted its own would be the page that disagreed about 最後の巡.
 *
 * **Not one Japanese literal is left here.** Every word comes from `strings.gymSession`, every number
 * from `strings.fmt`, every movement name from `Exercise.displayName`. What stayed is the *dispatch* —
 * which of the table's messages a given `RestKind`, `Phase`, `Segment` shape or numeric window selects,
 * and which of them selects **nothing**. That split is the whole design: a `when` that selects a
 * message translates by replacing the table, and a `when` that builds a sentence does not.
 *
 * Three rules run through the file.
 *
 * 1. **The table arrives as `strings: Strings`, the first parameter, and is never read from a global**
 *    (`.planning/i18n/DECISIONS.md` §L4). `PlayerCopyTest` runs on plain JUnit with no Compose and no
 *    `Context`, and passing the table is what keeps it there. [prepareNumeral] is the one function
 *    without it, and that absence is a claim — see its KDoc.
 * 2. **Nine of these return null, and the null is the specification.** Where §A gives no words, the
 *    page draws an empty fixed slot rather than a sentence this file invented — see [repDoneDescription]
 *    and [nextUpLabel] for the two places that bit. An English string in one of those positions
 *    re-introduces exactly the bug the null prevents.
 * 3. **`DECISIONS.md` §Q4 and §Q10 are still applied without exception in Japanese, and are deleted in
 *    English** (§L7). A *ticking* value is arabic — the countdown, the pacer, the ＋0:07 overrun —
 *    because a kanji column changing under a shaking hand is unreadable; anything that has stopped
 *    moving is kanji, and §Q10 splits that half again between a duration the user **chose**
 *    ([chosenSecondsLabel]) and one the app **measured** (`fmt.durationFromMs`). English has one
 *    orthography and therefore carries neither distinction. Both rules now live in [Strings.fmt].
 */

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
 * `fmt.clock` rather than a second `%d:%02d` here: the breakdown's 0:41 and this numeral have to be the
 * same form, and two format strings are two forms the day one of them grows a zero-padded minute. It is
 * **identical in both languages** — a clock is the one numeral English and Japanese already agree on —
 * which is why this is the half of §Q4 that survives translation by accident rather than by design.
 */
fun formatCountdown(strings: Strings, remainingMs: Long): String =
    strings.fmt.clock(ceilSeconds(remainingMs) * 1000L)

/**
 * 支度's numeral: a **bare integer**, not `0:03` (§A PREPARE's mock says so explicitly).
 *
 * Five seconds is not a duration you read off a clock face, it is a count you say out loud, and the
 * colon form would make the last five seconds of every session look like the last five seconds of a
 * plank.
 *
 * Takes no [Strings] and must not: this is a *ticking* value, so §Q4 keeps it arabic in Japanese too,
 * and `fmt.count` — which spells kanji — would be the wrong formatter in both languages.
 */
fun prepareNumeral(remainingMs: Long): String = ceilSeconds(remainingMs).toString()

/**
 * A duration the user **set** — `DECISIONS.md` §Q10, and §L7's ruling on what happens to it.
 *
 * Japanese renders bare kanji seconds: 六十秒, never 一分. That is §Q10's "chosen" half and it is
 * unchanged. **English deletes the rule**, because the distinction was carried by orthography and
 * English has one — so a chosen duration reads the same way a measured one does.
 *
 * The shape is `ui/gym/GymSettingsCopy.kt`'s `settingsSecondsLabel`, which made this collapse first.
 * The two are deliberately not one function: that one takes seconds off a wheel and has a zero row
 * reading なし, this one takes milliseconds off a running clock and has no zero case, because a segment
 * of zero length is not something the player draws.
 */
fun chosenSecondsLabel(strings: Strings, ms: Long): String {
    val seconds = ceilSeconds(ms).toInt()
    return if (strings.lang == Lang.Ja) strings.fmt.seconds(seconds) else strings.fmt.duration(seconds)
}

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
 *
 * The two clauses being independently droppable is why the join stays here and the *phrasing* moved:
 * one message string cannot express "either half may be absent", and `GymSessionStrings.counterStation`
 * cannot express `四種目中 三` in English word order. Each side does the part it can.
 */
fun counterLabel(
    strings: Strings,
    round: Int,
    totalRounds: Int,
    station: Int?,
    stationsPerRound: Int,
    lastRound: Boolean,
): String? {
    val s = strings.gymSession
    val rounds = when {
        totalRounds <= 1 -> null
        lastRound -> s.counterLastRound
        else -> s.counterRound(round)
    }
    val stations = if (station != null && stationsPerRound > 0) {
        s.counterStation(station + 1, stationsPerRound)
    } else {
        null
    }
    return listOfNotNull(rounds, stations).takeIf { it.isNotEmpty() }
        ?.joinToString(strings.fmt.separator)
}

/**
 * 「三巡目 / 十二巡」 — the words that replace the dots past nine rounds (`03-player.md` :172,
 * `DECISIONS.md` §Q8).
 *
 * The predicate is `cycleDotsOverflow`'s and lives with the component; the counter word is copy and
 * lives here, which is the split `CycleDots` documents at length.
 */
fun roundsOverflowLabel(strings: Strings, round: Int, totalRounds: Int): String =
    strings.gymSession.roundsOverflow(round, totalRounds)

/** The dots' one merged node — §A WORK, accessibility. */
fun cycleDotsLabel(strings: Strings, round: Int, totalRounds: Int): String =
    strings.gymSession.cycleDots(round, totalRounds)

/**
 * 「全体 四十パーセント」 — the session hairline's description, which is the only thing that reads it.
 *
 * Rounded to a whole percent because that is the form §A WORK's accessibility note writes, and because
 * a hairline announced to a tenth of a percent would be a number nobody can act on.
 */
fun progressLabel(strings: Strings, fraction: Float): String {
    val percent = if (fraction.isNaN()) 0 else (fraction * 100f).roundToInt().coerceIn(0, 100)
    return strings.gymSession.progress(percent)
}

/**
 * 「次 ・ 休息 十五秒 → プランク」, 「次 ・ プランク」 (§A WORK state 7), 「次 ・ 完了」 (state 6).
 *
 * Null is a fourth case the spec does not name and cannot be given a word: a next segment whose
 * exercise the catalogue no longer knows. 「次 ・ 完了」 would be a lie (there is more session), and
 * inventing 「次 ・ 種目」 would be inventing copy, so the slot is left blank and the chrome does not
 * move — the same fixed-slot bargain §A REST edge case 5 strikes for a missing form cue.
 */
fun nextUpLabel(strings: Strings, next: Segment?, nextExerciseName: String?): String? {
    val s = strings.gymSession
    return when {
        next == null -> s.nextComplete
        next.phase == Phase.REST ->
            s.nextRest(chosenSecondsLabel(strings, next.effectiveMs), nextExerciseName)

        nextExerciseName != null -> s.nextExercise(nextExerciseName)
        else -> null
    }
}

/**
 * The same line for TalkBack, with 「そのあと」 spelled out — 「→」 reads as nothing (§A WORK,
 * accessibility).
 */
fun nextUpDescription(strings: Strings, next: Segment?, nextExerciseName: String?): String? {
    val s = strings.gymSession
    return when {
        next == null -> s.nextCompleteSpoken
        next.phase == Phase.REST ->
            s.nextRestSpoken(chosenSecondsLabel(strings, next.effectiveMs), nextExerciseName)

        nextExerciseName != null -> s.nextExerciseSpoken(nextExerciseName)
        else -> null
    }
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
fun countdownAnnouncement(strings: Strings, remainingMs: Long, plannedMs: Long): String? = when {
    remainingMs in 10_001L..30_000L && plannedMs > 30_000L ->
        strings.gymSession.countdownRemaining(30)

    remainingMs in 1L..10_000L && plannedMs > 10_000L ->
        strings.gymSession.countdownRemaining(10)

    else -> null
}

/**
 * What a station asks for, under its name: 「三十秒」, 「二十回」, 「限界まで」.
 *
 * A rep count wins over a duration even inside a circuit, because 七分間's rep stations *are* rep
 * stations — the fixed box is the protocol's pacing, and the number the user works to is the count.
 * An open segment with no count is 限界まで (§A REPS state 3), and its estimate is never printed here:
 * `DEFAULT_MAX_EFFORT_ESTIMATE_MS` is a pacer and would read as a prescription.
 *
 * 限界まで is `Measure.MAX_EFFORT`'s own word and is read from `gymShared` rather than restated: this
 * line shows the prescription the builder set, and two keys for one prescription is how the two pages
 * end up calling it different things.
 */
fun prescriptionLabel(strings: Strings, segment: Segment?): String? {
    if (segment == null) return null
    val reps = segment.prescribedReps
    return when {
        reps != null -> strings.fmt.reps(reps)
        segment.open -> strings.gymShared.measureMaxEffort
        segment.plannedMs > 0L -> chosenSecondsLabel(strings, segment.plannedMs)
        else -> null
    }
}

/** REPS' hero: 「二十回」 in kanji, or 「限界まで」 (§A REPS states 1 and 3). */
fun repHero(strings: Strings, prescribedReps: Int?): String =
    prescribedReps?.let { strings.fmt.reps(it) } ?: strings.gymShared.measureMaxEffort

/**
 * 「済、二十回として記録」 — §A REPS, accessibility.
 *
 * A 限界まで set gets the bare 「済」. 「限界までとして記録」 is not in any table and would be a sentence
 * this file made up about the one prescription that has no number.
 */
fun repDoneDescription(strings: Strings, prescribedReps: Int?): String =
    prescribedReps?.let { strings.gymSession.repDone(strings.fmt.reps(it)) }
        ?: strings.gymSession.repsDone

/**
 * The line under the rep hero: 「目安 0:38」 pacing, 「＋0:07」 past it, 「残り 0:22」 inside an EMOM
 * minute (§A REPS states 1, 2, 5).
 *
 * Overrun counts **up** and therefore truncates, where the countdown rounds up: seven and a half
 * seconds over is seven seconds you have actually spent, and rounding it up would claim a second that
 * has not happened. Null on 限界まで, whose state has no estimate line at all.
 */
fun pacerLabel(
    strings: Strings,
    prescribedReps: Int?,
    remainingMs: Long,
    overrunMs: Long,
    emomWindow: Boolean,
): String? {
    val s = strings.gymSession
    return when {
        emomWindow -> s.pacerRemaining(formatCountdown(strings, remainingMs))
        prescribedReps == null -> null
        overrunMs > 0L -> s.pacerOverrun(strings.fmt.clock(overrunMs))
        else -> s.pacerEstimate(formatCountdown(strings, remainingMs))
    }
}

/**
 * The ring's word on a rest — 休息 / 巡の間 / 決められた休息 / 残り (§A REST states 1, 3, 4, 5).
 *
 * Each one is doing work. 巡の間 replaces the hidden dots during a round rest; 決められた休息 is the
 * reason the controls beside it are dead; 残り says the grid is anchored and this is what is left of
 * the minute rather than a rest anybody granted.
 */
fun restLabel(strings: Strings, kind: RestKind?): String = when (kind) {
    RestKind.ROUND -> strings.gymSession.restRound
    RestKind.MANDATED -> strings.gymSession.restMandated
    RestKind.EMOM_REMAINDER -> strings.gymSession.restEmomRemainder
    RestKind.STATION, null -> strings.gymSession.restStation
}

/** How long ＋二十秒 adds, in the unit the copy needs it. `SessionMachine` owns the number. */
private val EXTENSION_SECONDS: Int = (REST_EXTENSION_MS / 1000L).toInt()

/** 「＋二十秒」 — the inline control's visible label, and the amount it really adds. */
fun extendRestLabel(strings: Strings): String =
    strings.gymSession.restExtendLabel(EXTENSION_SECONDS)

/** 「とばす ▷」 — §A REST's inline "I'm ready, go", beside the bar's structural skip. */
fun skipRestLabel(strings: Strings): String = strings.gymSession.restSkipLabel

/** 「＋0:20」, the accent suffix that makes added time visible in the record's mental model (state 2). */
fun extendedSuffix(strings: Strings, addedMs: Long): String? =
    if (addedMs <= 0L) null else strings.gymSession.restExtendedSuffix(strings.fmt.clock(addedMs))

/** 「四十秒 追加済み」 — the ＋二十秒 control's `stateDescription` once it has been tapped. */
fun addedStateDescription(strings: Strings, addedMs: Long): String? =
    if (addedMs <= 0L) null else strings.gymSession.restAddedState((addedMs / 1000L).toInt())

/** 「二十秒 追加」 — what ＋二十秒 *does*, for TalkBack (§A REST, accessibility). */
fun extendRestDescription(strings: Strings): String =
    strings.gymSession.restExtendAction(EXTENSION_SECONDS)

/**
 * 「とばす、決められた休息のため使えません」 — a disabled control states its reason (§A REST, accessibility).
 *
 * Only MANDATED has one written, and only MANDATED renders disabled: an EMOM remainder **hides** its
 * controls instead (state 5), so there is nothing there to describe.
 *
 * Composed from the control's own name and the one reason clause, rather than transcribed whole. That
 * is not a liberty: the sentence *is* とばす plus the clause, the clause is shared with
 * [extendDisabledDescription] below, and one clause is what keeps the two controls from explaining
 * themselves differently.
 */
fun skipDisabledDescription(strings: Strings, kind: RestKind?): String? =
    if (kind == RestKind.MANDATED) {
        strings.gymSession.restDisabledReason(strings.gymSession.controlsForward)
    } else {
        null
    }

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
fun extendDisabledDescription(strings: Strings, kind: RestKind?): String? =
    if (kind == RestKind.MANDATED) {
        strings.gymSession.restDisabledReason(extendRestDescription(strings))
    } else {
        null
    }

/** 「六分十四秒 経過」 — session **active** time, so 支度 is already excluded (§A PAUSED). */
fun elapsedLine(strings: Strings, activeMs: Long): String =
    strings.gymSession.pausedElapsed(strings.fmt.durationFromMs(activeMs))

/** 「八種目 ・ 二巡 済」. Null before anything has accrued, which is `PausedDuringPrepare`'s hidden line. */
fun accruedLine(strings: Strings, stationsCompleted: Int, roundsCompleted: Int): String? {
    if (stationsCompleted <= 0 && roundsCompleted <= 0) return null
    return strings.gymSession.pausedAccrued(
        stations = strings.fmt.stations(stationsCompleted),
        rounds = if (roundsCompleted <= 0) null else strings.fmt.rounds(roundsCompleted),
    )
}

/** 「休止中、六分十四秒 経過、八種目 済」 — announced once on entering 休止. */
fun pausedAnnouncement(strings: Strings, activeMs: Long, stationsCompleted: Int): String =
    strings.gymSession.pausedAnnounce(
        duration = strings.fmt.durationFromMs(activeMs),
        stations = strings.fmt.stations(stationsCompleted),
    )

/**
 * 「残り 二十三秒、休止中」 — the frozen numeral, which is **not** a live region.
 *
 * §Q4's sharpest instance and §L7's clearest casualty. The same instant is *drawn* `0:23` by
 * [formatCountdown] and *spoken* in kanji here, because it has stopped moving — one value, two numeral
 * systems, on purpose. English has one, so the spoken form is the drawn form and the rule is gone
 * rather than translated.
 */
fun pausedNumeralDescription(strings: Strings, remainingMs: Long): String =
    strings.gymSession.pausedNumeral(ceilSeconds(remainingMs).toInt())

/** 「支度、五秒後に ジャンピングジャック」 — one announcement on entry, and only one. */
fun prepareAnnouncement(strings: Strings, remainingMs: Long, exerciseName: String?): String =
    if (exerciseName == null) {
        strings.gymSession.prepareTitle
    } else {
        strings.gymSession.prepareAnnounce(ceilSeconds(remainingMs).toInt(), exerciseName)
    }

/**
 * 「休息 十五秒、次は プランク、三十秒」 with the form cue appended — §A REST, accessibility.
 *
 * The cue is appended rather than dropped because it is one clause and it is the useful part: it is
 * the only moment in the session when the user is not yet moving and can still act on it.
 */
fun restAnnouncement(
    strings: Strings,
    kind: RestKind?,
    restMs: Long,
    nextExerciseName: String?,
    prescription: String?,
    cue: String?,
): String = buildString {
    append(restLabel(strings, kind))
    append(" ")
    append(chosenSecondsLabel(strings, restMs))
    if (nextExerciseName != null) {
        append(strings.gymSession.restAnnounceNext(nextExerciseName))
        if (prescription != null) {
            append(strings.fmt.listSeparator)
            append(prescription)
        }
    }
    if (cue != null) {
        append(strings.fmt.listSeparator)
        append(cue)
    }
}

/** 「腕立て伏せ、二十回」 — the REPS hero as one node (§A REPS, accessibility). */
fun repHeroDescription(strings: Strings, exerciseName: String?, prescribedReps: Int?): String =
    listOfNotNull(exerciseName, repHero(strings, prescribedReps))
        .joinToString(strings.fmt.listSeparator)

/** 「六分十四秒 ・ 二十種目中 八」 — the quit sheet's subtitle (§A QUIT_SHEET). */
fun quitSummaryLine(
    strings: Strings,
    activeMs: Long,
    stationsCompleted: Int,
    stationsPlanned: Int,
): String = strings.gymSession.quitSummary(
    duration = strings.fmt.durationFromMs(activeMs),
    done = stationsCompleted,
    planned = stationsPlanned,
)

/** 「三秒の支度から」 — the resume button's second line. `SessionMachine` owns the three seconds. */
fun resumePrepareNote(strings: Strings): String =
    strings.gymSession.pausedResumeNote(RESUME_PREPARE_SECONDS)

/** Both of the resume button's lines as one node. */
fun resumeLongDescription(strings: Strings): String =
    strings.gymSession.pausedResumeLong(RESUME_PREPARE_SECONDS)

private val RESUME_PREPARE_SECONDS: Int = (RESUME_PREPARE_MS / 1000L).toInt()

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

fun quitOptions(strings: Strings, resultsWritten: Int): QuitSheetOptions =
    if (resultsWritten >= 1) {
        QuitSheetOptions(
            canRecord = true,
            discardLabel = strings.gymSession.quitDiscard,
            confirmsDiscard = true,
            subtitleIsWarning = false,
        )
    } else {
        QuitSheetOptions(
            canRecord = false,
            discardLabel = strings.gymSession.quitDiscardNothing,
            confirmsDiscard = false,
            subtitleIsWarning = true,
        )
    }
