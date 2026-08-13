package io.eddiegulay.tempo.i18n

/**
 * The live player: prepare, work, reps, rest, paused, complete, quit.
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
 * ## Why so much of this table is functions
 *
 * `GymSettingsStrings` can say "nothing here takes a parameter, and that is the point": its page
 * branches by *selecting* a constant. The player cannot. Only three of `PlayerCopy.kt`'s outputs were
 * ever a whole literal; the other forty-four were assembled at run time from fifty-seven fragments with
 * `+`, templates, `buildString` and `joinToString`, and eight of them put their words in an order
 * English does not use — 「四種目中 三」 counts the total first, 「五秒後に プランク」 binds the
 * postposition to the number and trails the name.
 *
 * So the unit of translation here is the **whole message**, parameterised. A fragment table would have
 * been the same concatenation with an extra indirection, and would have shipped "of 4 stations 3".
 * Numbers never arrive as text a caller built: they arrive as `Int`, or as a string [Formats] already
 * shaped, so that pluralisation and numeral rules stay in one place.
 *
 * ## The two numeral rules die here, and this is where the corpse is (§L7)
 *
 * The player is where `DECISIONS.md` §Q4 and §Q10 are actually implemented, and where English deletes
 * them rather than translating them:
 *
 * - §Q4 — *ticking is arabic, stopped is kanji.* On PAUSED the same instant is drawn `0:23` and spoken
 *   `残り 二十三秒`, deliberately. English speaks `23s left`, which is the drawn value again. The
 *   distinction is gone, not carried.
 * - §Q10 — *a duration you chose renders bare (`六十秒`), one the app measured is spelled out
 *   (`六分十四秒`).* English renders both through `fmt.duration`.
 *
 * Both collapses live in [Formats] and in `chosenSecondsLabel`, not in this file; every member below
 * that takes a number simply asks [Formats] for it. `ui/gym/GymSettingsCopy.kt`'s `settingsSecondsLabel`
 * made the same collapse first and this follows it.
 *
 * ## What is deliberately **not** here
 *
 * - `限界まで` — it is `Measure.MAX_EFFORT`'s word and lives on `gymShared.measureMaxEffort`. The REPS
 *   hero draws the same prescription the builder set, and two keys would be two words for one fact.
 *   See the note on the hero cap in `RepsPage.kt`.
 * - Exercise names and form cues — `Exercise.displayName` / `displayCue`, resolved from the row.
 * - Anything the four *null-returning* functions decline to say. Nine of `PlayerCopy.kt`'s functions
 *   return `String?` and the page then draws an empty fixed slot; there is no member here for those
 *   holes, because a member is exactly how one gets filled by accident.
 */
interface GymSessionStrings {

    // ─── Chrome shared by every live page ───────────────────────────────────────────────────────

    /** The ✕'s spoken name. The glyph itself is not copy. */
    val quitGlyphAction: String

    /**
     * ◁ — and the only way a TalkBack user learns it is a double-tap.
     *
     * The gesture is *in* the sentence because there is nowhere else to put it: the glyph is a single
     * character and the control has no other affordance.
     */
    val controlsBack: String

    /** ◁ inside 支度, where it is dead — used as both the description and the disabled reason. */
    val controlsBackPrepare: String

    /** ┃┃. */
    val controlsPause: String

    /** ▷, and the word the mandated-rest refusal quotes back. */
    val controlsForward: String

    // ─── The counter line ───────────────────────────────────────────────────────────────────────

    /** Replaces the round clause on the final round — 「一巡目」 of one lap is chrome, not information. */
    val counterLastRound: String

    /** 「三巡目」. Dropped entirely on a single-round routine. */
    fun counterRound(round: Int): String

    /**
     * 「四種目中 三」 — and the **total comes first** in Japanese.
     *
     * This is the plainest case for parameterised messages in the whole table: the fragments are the
     * same two numbers in both languages and the order is not, so a concatenation at the call site
     * could only have been right in one of them.
     */
    fun counterStation(index: Int, total: Int): String

    /** 「三巡目 / 十二巡」 — the words that replace the dots past nine rounds (§Q8). */
    fun roundsOverflow(round: Int, total: Int): String

    /** The dot row's one merged accessibility node. */
    fun cycleDots(round: Int, total: Int): String

    /** The session hairline's description. Japanese spells the unit `パーセント`; English has a sign. */
    fun progress(percent: Int): String

    // ─── The next-up line, drawn and spoken ─────────────────────────────────────────────────────

    /*
     * Two families, never one. The visible line joins with ` ・ ` and points with `→`; the spoken line
     * joins with `、` and spells `→` out as そのあと, because an arrow reads as nothing. They are
     * phrased for different senses and are separate strings in both languages.
     */

    val nextComplete: String

    /** @param thenName the movement after the rest, or null when the catalogue no longer knows it. */
    fun nextRest(seconds: String, thenName: String?): String

    fun nextExercise(name: String): String

    val nextCompleteSpoken: String
    fun nextRestSpoken(seconds: String, thenName: String?): String
    fun nextExerciseSpoken(name: String): String

    // ─── 支度 ───────────────────────────────────────────────────────────────────────────────────

    /**
     * The ring's word, and the whole announcement when no movement is known.
     *
     * English is 「Get ready」 — the same noun `gymSettings.rowPrepareSeconds` ("Get-ready time") and
     * `gymCue.phasePrepare` use, because all three name one segment and a page that called it something
     * else would be describing a different thing.
     */
    val prepareTitle: String

    /** 「支度、五秒後に ジャンピングジャック」. English reverses it: the name leads and the number trails. */
    fun prepareAnnounce(seconds: Int, name: String): String

    // ─── 運動 ───────────────────────────────────────────────────────────────────────────────────

    /** 「残り 三十秒」 / 「残り 十秒」, and nothing else — the node speaks at most twice per segment. */
    fun countdownRemaining(seconds: Int): String

    // ─── 運動・回数 ─────────────────────────────────────────────────────────────────────────────

    /** 済 — the gate's label, and the description a 限界まで set gets instead of a sentence. */
    val repsDone: String

    /** 「済、二十回として記録」. @param reps already counted by [Formats.reps]. */
    fun repDone(reps: String): String

    /**
     * 回数を変える — the long-press, declared three times on one control and identically each time.
     *
     * One key rather than two: `onLongClickLabel`, the semantics `onLongClick` label and the
     * `CustomAccessibilityAction` must be the same words or a switch-access user is offered a different
     * action from the one TalkBack described, and the wheel's own heading is the same act again.
     */
    val repsAdjust: String

    /** The wheel's confirm. */
    val repsRecord: String

    /** One polite announcement on crossing the pacing estimate, then silence. */
    val overrunAnnouncement: String

    /*
     * The pacer line, all three arabic because all three tick (§Q4, and the one half of it English
     * keeps by accident — a clock is a clock in both languages).
     */

    /** 「残り 0:22」 — what is left of an EMOM minute, which is a boundary rather than an estimate. */
    fun pacerRemaining(clock: String): String

    /** 「＋0:07」 — past the estimate, counting up. */
    fun pacerOverrun(clock: String): String

    /** 「目安 0:38」 — a pace, never authoritative. */
    fun pacerEstimate(clock: String): String

    // ─── 休息 ───────────────────────────────────────────────────────────────────────────────────

    /*
     * The ring's four words. Each is doing work: 巡の間 replaces the hidden dots during a round rest,
     * 決められた休息 is the reason the controls beside it are dead, 残り says the grid is anchored and
     * this is the leftover rather than a rest anybody granted.
     *
     * The slot is 15.sp at 6.sp letter-spacing inside a 220.dp ring, sharing a `Row` with the ＋0:20
     * suffix, so the English is held to two words.
     */

    val restStation: String
    val restRound: String
    val restMandated: String
    val restEmomRemainder: String

    /** つぎ, under the countdown. Hiragana in Japanese: the softest form of the softest screen. */
    val restNext: String

    /** 「＋二十秒」, the inline control's visible label. */
    fun restExtendLabel(seconds: Int): String

    /** 「とばす ▷」. The glyph is part of the label; the spoken form is [controlsForward]. */
    val restSkipLabel: String

    /** 「二十秒 追加」 — what ＋二十秒 *does*. */
    fun restExtendAction(seconds: Int): String

    /** 「四十秒 追加済み」 — what it has already done, so a user is not left counting taps. */
    fun restAddedState(seconds: Int): String

    /** 「＋0:20」 — the accent suffix beside the ring label. Arabic: it is part of a running clock. */
    fun restExtendedSuffix(clock: String): String

    /**
     * 「、決められた休息のため使えません」 — one reason clause, appended to whichever control was refused.
     *
     * A single member rather than one sentence per control, because the spec writes the reason **once**
     * and disables both in the same breath. Two members is how the two controls end up explaining
     * themselves differently.
     */
    fun restDisabledReason(action: String): String

    /** 「休息 十五秒、次は プランク…」's second clause. The rest of the sentence joins with [Formats.listSeparator]. */
    fun restAnnounceNext(name: String): String

    // ─── 休止 ───────────────────────────────────────────────────────────────────────────────────

    val pausedTitle: String

    /** The 30-minute guard fired. Nothing is saved on the strength of it, and this does not imply it was. */
    val pausedStalled: String

    /**
     * 続ける — and note it is **not** the quit sheet's つづける.
     *
     * Two Japanese spellings of one verb for two different acts: this restarts a stopped clock, and
     * [quitContinue] declines to end a session that never stopped. English keeps them apart too.
     */
    val pausedResume: String

    /** The second line inside the same button, and the reason it is there rather than a surprise. */
    fun pausedResumeNote(seconds: Int): String

    /** Both lines as one node. */
    fun pausedResumeLong(seconds: Int): String

    /** 「六分十四秒 経過」 — session **active** time, so 支度 is already excluded. */
    fun pausedElapsed(duration: String): String

    /** 「八種目 ・ 二巡 済」. @param rounds null before a round has closed; the 済 still binds to what is left. */
    fun pausedAccrued(stations: String, rounds: String?): String

    /** 「休止中、六分十四秒 経過、八種目 済」 — one utterance on entering 休止. */
    fun pausedAnnounce(duration: String, stations: String): String

    /**
     * 「残り 二十三秒、休止中」 — the frozen countdown.
     *
     * §L7's sharpest case. The same instant is **drawn** `0:23` and **spoken** in kanji, on purpose,
     * because it has stopped moving. English has one numeral system, so the spoken form is the drawn
     * form and §Q4 is deleted here rather than translated.
     */
    fun pausedNumeral(seconds: Int): String

    // ─── 記録 ───────────────────────────────────────────────────────────────────────────────────

    /**
     * 閉じる — **deliberately not** `SessionDetailScreen`'s とじる.
     *
     * The kanji form is the live player's way out of its own stack; the hiragana form closes a record
     * from March. Documented in both files' KDoc, and English collapsing them into one "Close" is fine:
     * they are on different pages and never on screen together.
     */
    val completeClose: String

    // ─── 鍛錬を終えますか ───────────────────────────────────────────────────────────────────────

    val quitTitle: String

    /** The subtitle when nothing has been recorded — a statement, not a warning about losing anything. */
    val quitNothingToSave: String

    /** The accent row. */
    val quitSave: String

    /** 「六分十四秒 ・ 二十種目中 八」 — the same total-first inversion [counterStation] has. */
    fun quitSummary(duration: String, done: Int, planned: Int): String

    /** The destructive row when there is work to lose. */
    val quitDiscard: String

    /** Its consequence, spoken. A separate string from [quitDiscard] and allowed to say more. */
    val quitDiscardDescription: String

    /** The same row at zero results: nothing is being destroyed, so it does not say so. */
    val quitDiscardNothing: String

    /** 本当に消しますか — the armed label, for three seconds after the first tap. */
    val quitArmed: String

    /** Assertive on purpose: a destructive confirmation is the one place interrupting the user is right. */
    val quitArmedDescription: String

    /**
     * つづける — the escape, and **never** the word that means "abandon".
     *
     * `QuitSheet.kt`'s KDoc forbids "fixing" this to match the calendar composer's やめる, and the
     * English must not be fixed into "Cancel" either: a row labelled Cancel inside a dialog titled
     * "End this workout?" is ambiguous about which thing it cancels. It says what it does.
     */
    val quitContinue: String

    /** 記録できませんでした. It does not apologise. */
    val quitSaveFailed: String

    /** もう一度 — re-runs the finish, not the transition. */
    val quitRetry: String

    // ─── ScalingTier ────────────────────────────────────────────────────────────────────────────

    /*
     * やさしい / 基本 / きつい — which scaling of a routine is being performed. `ScalingTier.name`
     * (`EASY`/`RX`/`HARD`) is what reaches `session.tier`, so nothing here is stored (§L3).
     *
     * Not `Tier` (入門/中級/上級), which is a difficulty band on a card, and not `Rating.HARD`, whose
     * word is also きつい. Three unrelated concepts, one of which Japanese happens to spell twice.
     */

    val scalingTierEasy: String
    val scalingTierRx: String
    val scalingTierHard: String
}

internal object JaGymSession : GymSessionStrings {

    override val quitGlyphAction = "鍛錬を終える"
    override val controlsBack = "前へ、二回押すと一つ戻る"
    override val controlsBackPrepare = "戻る"
    override val controlsPause = "休止"
    override val controlsForward = "とばす"

    override val counterLastRound = "最後の巡"
    override fun counterRound(round: Int) = JaFormats.count(round) + "巡目"
    override fun counterStation(index: Int, total: Int) =
        JaFormats.stations(total) + "中 " + JaFormats.count(index)

    override fun roundsOverflow(round: Int, total: Int) =
        counterRound(round) + " / " + JaFormats.rounds(total)

    override fun cycleDots(round: Int, total: Int) =
        counterRound(round) + JaFormats.listSeparator + JaFormats.rounds(total) + "中"

    override fun progress(percent: Int) = "全体 " + JaFormats.count(percent) + "パーセント"

    override val nextComplete = "次" + JaFormats.separator + "完了"
    override fun nextRest(seconds: String, thenName: String?): String {
        val rest = "次" + JaFormats.separator + "休息 " + seconds
        return if (thenName == null) rest else "$rest → $thenName"
    }

    override fun nextExercise(name: String) = "次" + JaFormats.separator + name

    override val nextCompleteSpoken = "次" + JaFormats.listSeparator + "完了"
    override fun nextRestSpoken(seconds: String, thenName: String?): String {
        val rest = "次" + JaFormats.listSeparator + "休息 " + seconds
        return if (thenName == null) rest else rest + JaFormats.listSeparator + "そのあと " + thenName
    }

    override fun nextExerciseSpoken(name: String) = "次" + JaFormats.listSeparator + name

    override val prepareTitle = "支度"
    override fun prepareAnnounce(seconds: Int, name: String) =
        prepareTitle + JaFormats.listSeparator + JaFormats.seconds(seconds) + "後に " + name

    override fun countdownRemaining(seconds: Int) = "残り " + JaFormats.seconds(seconds)

    override val repsDone = "済"
    override fun repDone(reps: String) = repsDone + JaFormats.listSeparator + reps + "として記録"
    override val repsAdjust = "回数を変える"
    override val repsRecord = "記録する"
    override val overrunAnnouncement = "目安を過ぎました"

    override fun pacerRemaining(clock: String) = "残り $clock"
    override fun pacerOverrun(clock: String) = "＋$clock"
    override fun pacerEstimate(clock: String) = "目安 $clock"

    override val restStation = "休息"
    override val restRound = "巡の間"
    override val restMandated = "決められた休息"
    override val restEmomRemainder = "残り"
    override val restNext = "つぎ"

    override fun restExtendLabel(seconds: Int) = "＋" + JaFormats.seconds(seconds)
    override val restSkipLabel = "とばす ▷"
    override fun restExtendAction(seconds: Int) = JaFormats.seconds(seconds) + " 追加"
    override fun restAddedState(seconds: Int) = JaFormats.seconds(seconds) + " 追加済み"
    override fun restExtendedSuffix(clock: String) = "＋$clock"

    override fun restDisabledReason(action: String) =
        action + JaFormats.listSeparator + restMandated + "のため使えません"

    override fun restAnnounceNext(name: String) = JaFormats.listSeparator + "次は " + name

    override val pausedTitle = "休止"
    override val pausedStalled = "長い間 動きがありません"
    override val pausedResume = "続ける"
    override fun pausedResumeNote(seconds: Int) = JaFormats.seconds(seconds) + "の支度から"
    override fun pausedResumeLong(seconds: Int) =
        pausedResume + JaFormats.listSeparator + pausedResumeNote(seconds)

    override fun pausedElapsed(duration: String) = "$duration 経過"
    override fun pausedAccrued(stations: String, rounds: String?): String {
        val body = if (rounds == null) stations else stations + JaFormats.separator + rounds
        return "$body 済"
    }

    override fun pausedAnnounce(duration: String, stations: String) =
        pausedTitle + "中" + JaFormats.listSeparator +
            pausedElapsed(duration) + JaFormats.listSeparator + "$stations 済"

    override fun pausedNumeral(seconds: Int) =
        countdownRemaining(seconds) + JaFormats.listSeparator + pausedTitle + "中"

    override val completeClose = "閉じる"

    override val quitTitle = "鍛錬を終えますか"
    override val quitNothingToSave = "まだ 記録するものがありません"
    override val quitSave = "ここまでを記録する"

    override fun quitSummary(duration: String, done: Int, planned: Int) =
        duration + JaFormats.separator + counterStation(done, planned)

    override val quitDiscard = "記録せずに終える"
    override val quitDiscardDescription =
        quitDiscard + JaFormats.listSeparator + "これまでの記録は消えます"
    override val quitDiscardNothing = "終える"
    override val quitArmed = "本当に消しますか"
    override val quitArmedDescription = quitArmed + JaFormats.listSeparator + "もう一度 押すと消えます"
    override val quitContinue = "つづける"
    override val quitSaveFailed = "記録できませんでした"
    override val quitRetry = "もう一度"

    override val scalingTierEasy = "やさしい"
    override val scalingTierRx = "基本"
    override val scalingTierHard = "きつい"
}

/**
 * English.
 *
 * **Short because the slots are short**, not because English prose should be. Fifteen of these land in
 * a fixed box or under `maxLines = 1`: the ring labels are 15.sp at 6.sp letter-spacing inside a 220.dp
 * circle, the two inline rest controls sit either side of a rigid 40.dp gap, and the resume button
 * stacks two lines into 64.dp. Where a longer phrase was the better sentence and the shorter one still
 * true, the shorter one is here.
 *
 * Words shared with pages another unit owns are **taken**, never restated: 「Get ready」 is
 * `gymCue.phasePrepare` and `gymSettings.rowPrepareSeconds`' noun, and 限界まで is
 * `gymShared.measureMaxEffort` and is not in this file at all.
 */
internal object EnGymSession : GymSessionStrings {

    override val quitGlyphAction = "End the workout"

    // The double-tap is in the sentence because the glyph cannot carry it. "Back" alone would leave a
    // TalkBack user unable to discover the only gesture on the bar.
    override val controlsBack = "Previous — press twice to step back one"
    override val controlsBackPrepare = "Back"
    override val controlsPause = "Pause"
    override val controlsForward = "Skip"

    override val counterLastRound = "Last round"
    override fun counterRound(round: Int) = "Round ${EnFormats.count(round)}"

    // 「四種目中 三」 inverted. This is the one the survey called out and it is simply "m of n".
    override fun counterStation(index: Int, total: Int) =
        "${EnFormats.count(index)} of ${EnFormats.stations(total)}"

    override fun roundsOverflow(round: Int, total: Int) =
        "${counterRound(round)} / ${EnFormats.count(total)}"

    // 「三巡目、五巡中」 is two clauses in Japanese and one phrase in English; the ideographic comma has
    // no job here, and TalkBack reading a pause into the middle of "round 3 of 5" is a wrong pause.
    override fun cycleDots(round: Int, total: Int) =
        "${counterRound(round)} of ${EnFormats.count(total)}"

    override fun progress(percent: Int) = "$percent% overall"

    override val nextComplete = "Next" + EnFormats.separator + "Done"
    override fun nextRest(seconds: String, thenName: String?): String {
        val rest = "Next" + EnFormats.separator + "Rest $seconds"
        return if (thenName == null) rest else "$rest → $thenName"
    }

    override fun nextExercise(name: String) = "Next" + EnFormats.separator + name

    override val nextCompleteSpoken = "Next, done"
    override fun nextRestSpoken(seconds: String, thenName: String?): String {
        val rest = "Next, rest $seconds"
        return if (thenName == null) rest else "$rest, then $thenName"
    }

    override fun nextExerciseSpoken(name: String) = "Next, $name"

    override val prepareTitle = "Get ready"

    // 「支度、五秒後に プランク」 reversed: 後に is a postposition binding to the number, and English
    // puts the movement first and the countdown after it.
    override fun prepareAnnounce(seconds: Int, name: String) =
        "$prepareTitle, $name in ${EnFormats.duration(seconds)}"

    override fun countdownRemaining(seconds: Int) = "${EnFormats.duration(seconds)} left"

    override val repsDone = "Done"
    override fun repDone(reps: String) = "$repsDone, recorded as $reps"
    override val repsAdjust = "Change the count"
    override val repsRecord = "Record"

    // 目安を過ぎました states a fact about the clock, not about the user. So does this.
    override val overrunAnnouncement = "Past the estimate"

    override fun pacerRemaining(clock: String) = "$clock left"
    override fun pacerOverrun(clock: String) = "+$clock"

    // 目安 is the pacing estimate. Abbreviated because it shares a 13.sp line with the clock, directly
    // under a 76.sp hero, and is read mid-set.
    override fun pacerEstimate(clock: String) = "Est. $clock"

    override val restStation = "Rest"
    override val restRound = "Round rest"

    // 決められた休息 is a rest the protocol fixed and the player refuses to skip. "Fixed" is the half
    // that matters and the half that fits beside +0:20 in the ring.
    override val restMandated = "Fixed rest"

    // 残り above an anchored EMOM countdown: what is left of the minute, not a rest anybody granted.
    // "Left" is shorter and wrong in the other slot this word appears in — `restAnnouncement` puts the
    // ring label in front of a duration, and "Remaining 30s" is a sentence where "Left 30s" is not.
    override val restEmomRemainder = "Remaining"
    override val restNext = "Next"

    override fun restExtendLabel(seconds: Int) = "+${EnFormats.duration(seconds)}"
    override val restSkipLabel = "Skip ▷"
    override fun restExtendAction(seconds: Int) = "Add ${EnFormats.duration(seconds)}"
    override fun restAddedState(seconds: Int) = "${EnFormats.duration(seconds)} added"
    override fun restExtendedSuffix(clock: String) = "+$clock"

    override fun restDisabledReason(action: String) = "$action — not available on a fixed rest"

    override fun restAnnounceNext(name: String) = ", next: $name"

    override val pausedTitle = "Paused"
    override val pausedStalled = "Nothing has moved for a long time"
    override val pausedResume = "Resume"
    override fun pausedResumeNote(seconds: Int) =
        "After a ${EnFormats.duration(seconds)} get-ready"

    override fun pausedResumeLong(seconds: Int) =
        "$pausedResume, after a ${EnFormats.duration(seconds)} get-ready"

    override fun pausedElapsed(duration: String) = "$duration elapsed"
    override fun pausedAccrued(stations: String, rounds: String?): String {
        val body = if (rounds == null) stations else stations + EnFormats.separator + rounds
        return "$body done"
    }

    override fun pausedAnnounce(duration: String, stations: String) =
        pausedTitle + EnFormats.listSeparator + pausedElapsed(duration) +
            EnFormats.listSeparator + "$stations done"

    // §L7: kanji spoken over an arabic numeral is a distinction English cannot carry, so this is the
    // drawn value plus the state.
    override fun pausedNumeral(seconds: Int) =
        countdownRemaining(seconds) + EnFormats.listSeparator + "paused"

    override val completeClose = "Close"

    override val quitTitle = "End this workout?"
    override val quitNothingToSave = "Nothing to record yet"
    override val quitSave = "Record what you have done"

    override fun quitSummary(duration: String, done: Int, planned: Int) =
        duration + EnFormats.separator + counterStation(done, planned)

    override val quitDiscard = "End without recording"
    override val quitDiscardDescription = "$quitDiscard — everything so far is deleted"
    override val quitDiscardNothing = "End"
    override val quitArmed = "Delete it for real?"
    override val quitArmedDescription = "$quitArmed Press again to delete."

    // Not "Cancel". Inside a sheet titled "End this workout?" a Cancel row is ambiguous about which
    // thing it cancels, and this row is the escape — it says what it does. See the interface note.
    override val quitContinue = "Keep going"

    override val quitSaveFailed = "Couldn't record"
    override val quitRetry = "Try again"

    override val scalingTierEasy = "Easy"

    // 基本 is the unscaled routine — CrossFit's "Rx", which is jargon. The word Tempo uses elsewhere
    // for the unmodified thing is the plain one.
    override val scalingTierRx = "Standard"
    override val scalingTierHard = "Hard"
}
