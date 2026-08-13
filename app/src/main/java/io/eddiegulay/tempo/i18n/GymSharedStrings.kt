package io.eddiegulay.tempo.i18n

import io.eddiegulay.tempo.gym.StepUnit

/**
 * 鍛錬 vocabulary shared across its pages: tiers, engines, patterns, measures.
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
 * **Why so many of these are functions.** A member that interpolates is a whole composed string, not a
 * fragment to be glued at the call site: 「第七段」 is a circumfix around the numeral and "Step 7" is a
 * prefix, 「六百回まで」 puts its bound after the count and "up to 600 reps" puts it before. A call site
 * that concatenated two members would have baked Japanese word order into every language. The numeral
 * itself always arrives already formatted, from [Formats] — this table never sees an `Int`.
 */
interface GymSharedStrings {

    // ─── Engine (`Engine.label`) ────────────────────────────────────────────────────────────────

    /** 巡回 — work, rest, repeat. */
    val engineIntervalCircuit: String

    /** 時間内 — as many rounds as fit in a fixed cap. */
    val engineAmrap: String

    /** 完走 — finish the prescribed work, on the clock. */
    val engineForTime: String

    /**
     * 休息あり — the second half of `FOR_TIME_WITH_REST`'s label.
     *
     * Its label reads 完走 ・ 休息あり, and the separator in the middle is [Formats.separator] rather
     * than a character baked into this string: it is ` ・ ` in Japanese and ` · ` in English, and one
     * literal here would have shipped a CJK middle dot into the Latin table.
     */
    val engineWithRest: String

    /** 毎分 — one round per interval window. */
    val engineEmom: String

    /** 毎分増 — 毎分, with the count rising each window, until it cannot be made. */
    val engineEmomAscending: String

    /** 段階 — a progression's numbered rungs. */
    val engineFixedSets: String

    // ─── Pattern (`Pattern.label`) ──────────────────────────────────────────────────────────────

    /*
     * Declaration order is display order and it is the ACSM alternation `GymModels` refuses to sort.
     * The words are plain verbs in Japanese and stay plain nouns in English; the movement vocabulary a
     * training app uses is not the anatomical one.
     */

    val patternHorizontalPush: String
    val patternVerticalPull: String
    val patternSquat: String
    val patternHinge: String
    val patternCore: String
    val patternLocomotion: String
    val patternPlyo: String

    // ─── Measure (`Measure.label`) ──────────────────────────────────────────────────────────────

    /** 回数 — the builder's はかり方 chip for a counted prescription. */
    val measureReps: String

    /** 秒数 — a held or timed prescription. Seconds, because the wheel that sets it is in seconds. */
    val measureDuration: String

    /** 限界まで — no count and no clock; the segment closes when the user says so. */
    val measureMaxEffort: String

    // ─── Rating (`Rating.label`) ────────────────────────────────────────────────────────────────

    /*
     * どうでしたか's three answers. The CR10 number beside them in the enum is frozen onto the session
     * row and is not copy; only these words move.
     */

    val ratingEasy: String
    val ratingJustRight: String
    val ratingHard: String

    // ─── Duration filter chips (`DurationBucket.label`) ─────────────────────────────────────────

    /**
     * 〜五分 / 五〜十五分 / 十五分〜 — the library index's single-select duration chips.
     *
     * **Three strings, not one symbol moved around.** Japanese carries the whole range on a wave dash
     * used leading, medial and trailing; English needs a different word in each position, and a
     * shared 「〜」 member with the endpoint interpolated would have been the Japanese typography
     * translated rather than the meaning.
     *
     * The endpoints belong to the lower bucket, which the words have to keep saying: exactly five
     * minutes is [durationUnderFive], exactly fifteen is [durationFiveToFifteen].
     */
    val durationUnderFive: String
    val durationFiveToFifteen: String
    val durationOverFifteen: String

    // ─── The detail page's read-only rows (`engineRows`) ────────────────────────────────────────

    /** 制限時間 — AMRAP only. */
    val rowTimeCap: String

    /** 種目の間の休息. */
    val rowRestBetweenStations: String

    /** 巡の間の休息. */
    val rowRestBetweenRounds: String

    /** 巡数 — the row label. */
    val rowRounds: String

    /** 時間内で — AMRAP's answer in the 巡数 slot. "How many rounds" is genuinely "as many as fit". */
    val roundsWithinCap: String

    /** なし — a prescribed zero rest, which is a prescription and never 〇秒. */
    val restNone: String

    // ─── 最高 tiles and PR rows (`bestMetricLabel`, `bestTilesFor`) ─────────────────────────────

    /** やった回数 — the third tile, engine-independent. */
    val timesDone: String

    /** 最速. */
    val metricBestTime: String

    /** 最高巡数. */
    val metricMostRounds: String

    /** 最高反復. */
    val metricMostReps: String

    /**
     * 最高負荷 — deliberately unitless in both languages.
     *
     * Difficulty-weighted volume is not a rep count and is never suffixed as one; `bestValueLabel`
     * prints its number bare for exactly that reason, and this word must not imply otherwise.
     */
    val metricMostVolume: String

    /*
     * `BestMetric.HIGHEST_STEP` has **no member here**, and that is the point: §Q9 forbids inventing a
     * label for it, `bestMetricLabel` returns null, and a string in this table would be the invention.
     */

    // ─── 段階 progression (`stepFor`) ───────────────────────────────────────────────────────────

    /**
     * 第七段 / 第三日 — a step's title when the seed carries no `label_ja` of its own.
     *
     * @param index the step number, already through [Formats.count].
     * @param unit which word the programme counts in. Both words are the seeds' own — `p_armstrong`
     *   ships 第一日..第五日 — so the [StepUnit.DAY] form is a transcription, not a translation choice.
     */
    fun stepTitle(index: String, unit: StepUnit): String

    /**
     * 十八段のうち / 五日のうち — the caption under [stepTitle].
     *
     * Takes the same [unit] as the title so a day-based programme cannot read 第三日 over 五段のうち.
     */
    fun stepCaption(count: String, unit: StepUnit): String

    // ─── The estimate line (`estimateLabel`) ────────────────────────────────────────────────────

    /**
     * 約 十八分 — how long a routine is expected to take.
     *
     * @param duration already through [Formats.minutes]. Floored at one minute by the caller: 約 〇分
     *   is not a duration.
     */
    fun estimateDuration(duration: String): String

    /**
     * 六百回まで — an AMRAP's reps are an **upper bound**, not a total.
     *
     * Japanese puts まで after the count and English puts "up to" before it, which is the whole reason
     * this is a function.
     */
    fun estimateRepsCap(reps: String): String

    /**
     * 「約 十八分 ・ 三百回 目安」 — the same line, marked as a guess.
     *
     * Appended whenever anything in the arithmetic was not arithmetic: a max-effort station, or an
     * exercise the catalogue could not resolve. Coarse on purpose (`RoutineEstimate.approximate` is a
     * string decision, not a confidence score).
     */
    fun estimateApproximate(line: String): String

    // ─── The builder's one piece of coaching (`clashCopy`) ──────────────────────────────────────

    /**
     * 腕立て伏せ と ディップス は続けて置かない方がよい — design §6's sentence, verbatim.
     *
     * Two exercise **names**, which are user or catalogue data and arrive already resolved. The
     * pattern's own word (押す) is deliberately absent: §6 names the movements, because those are what
     * the user can move.
     */
    fun patternClash(first: String, second: String): String

    // ─── The calendar hand-off (`ScheduleNext`) ─────────────────────────────────────────────────

    /** 予定に入れる — the one word `GYM.SESSION.COMPLETE`'s calendar hand-off puts on screen. */
    val scheduleNext: String
}

internal object JaGymShared : GymSharedStrings {

    override val engineIntervalCircuit = "巡回"
    override val engineAmrap = "時間内"
    override val engineForTime = "完走"
    override val engineWithRest = "休息あり"
    override val engineEmom = "毎分"
    override val engineEmomAscending = "毎分増"
    override val engineFixedSets = "段階"

    override val patternHorizontalPush = "押す"
    override val patternVerticalPull = "引く"
    override val patternSquat = "しゃがむ"
    override val patternHinge = "股関節"
    override val patternCore = "体幹"
    override val patternLocomotion = "移動"
    override val patternPlyo = "跳ぶ"

    override val measureReps = "回数"
    override val measureDuration = "秒数"
    override val measureMaxEffort = "限界まで"

    override val ratingEasy = "楽"
    override val ratingJustRight = "ちょうど"
    override val ratingHard = "きつい"

    override val durationUnderFive = "〜五分"
    override val durationFiveToFifteen = "五〜十五分"
    override val durationOverFifteen = "十五分〜"

    override val rowTimeCap = "制限時間"
    override val rowRestBetweenStations = "種目の間の休息"
    override val rowRestBetweenRounds = "巡の間の休息"
    override val rowRounds = "巡数"
    override val roundsWithinCap = "時間内で"
    override val restNone = "なし"

    override val timesDone = "やった回数"
    override val metricBestTime = "最速"
    override val metricMostRounds = "最高巡数"
    override val metricMostReps = "最高反復"
    override val metricMostVolume = "最高負荷"

    override fun stepTitle(index: String, unit: StepUnit): String = "第" + index + stepUnitWord(unit)

    override fun stepCaption(count: String, unit: StepUnit): String =
        count + stepUnitWord(unit) + "のうち"

    private fun stepUnitWord(unit: StepUnit): String = when (unit) {
        StepUnit.STEP -> "段"
        StepUnit.DAY -> "日"
    }

    override fun estimateDuration(duration: String): String = "約 $duration"
    override fun estimateRepsCap(reps: String): String = reps + "まで"
    override fun estimateApproximate(line: String): String = "$line 目安"

    override fun patternClash(first: String, second: String): String =
        "$first と $second は続けて置かない方がよい"

    override val scheduleNext = "予定に入れる"
}

/**
 * English.
 *
 * Two judgements run through the words below and are worth stating once.
 *
 * **The engine names stay plain.** 巡回 / 時間内 / 完走 / 毎分 are ordinary Japanese, not the sport's
 * jargon — the app deliberately does not say AMRAP or EMOM in Japanese, and saying them in English
 * would translate the register wrong even where it translates the meaning right.
 *
 * **Length is functional.** These words sit in filter chips, in a `SpaceBetween` row label, and in a
 * subtitle joined by a separator; the Japanese is one to four glyphs and the English has to stay near
 * that. That is why the duration chips read `5–15 min` rather than `Between 5 and 15 minutes`.
 */
internal object EnGymShared : GymSharedStrings {

    override val engineIntervalCircuit = "Circuit"

    /** 時間内 is *within the time* — a fixed box you fill, which is what "Timed" says in one word. */
    override val engineAmrap = "Timed"

    /** 完走 is *run it to the end*, not "for time": the clock is how it is scored, not what it is. */
    override val engineForTime = "Finish"
    override val engineWithRest = "with rest"

    override val engineEmom = "Every minute"

    /** 毎分増 — the count climbs each window until it cannot be made. デス・バイ is the shape. */
    override val engineEmomAscending = "Every minute, rising"

    /** 段階 is a ladder of rungs, and `stepFor` already calls each one a step. */
    override val engineFixedSets = "Steps"

    override val patternHorizontalPush = "Push"
    override val patternVerticalPull = "Pull"
    override val patternSquat = "Squat"
    override val patternHinge = "Hinge"
    override val patternCore = "Core"

    /** 移動 is going somewhere. "Locomotion" is the enum's name and nobody's word for it. */
    override val patternLocomotion = "Travel"
    override val patternPlyo = "Jump"

    override val measureReps = "Reps"
    override val measureDuration = "Seconds"
    // "All out", not "To failure". Two reasons, and the first is a measurement. The player's hero
    // slot caps at pageWidth / 4.2 — four CJK ems plus slack — and **clips without an ellipsis**:
    // 限界まで is 4.00 em, "To failure" ≈3.97, "All out" ≈2.86. A 5% margin on the one screen read
    // mid-set fails first at large font scales and on narrow devices, silently, as "To failur".
    // Second: 限界まで is plain Japanese, and this app refuses the sport's jargon throughout — 巡回
    // rather than "AMRAP", 完走 rather than "for time". "To failure" is training-science register;
    // "all out" is ordinary speech for the same thing.
    override val measureMaxEffort = "All out"

    override val ratingEasy = "Easy"
    override val ratingJustRight = "Just right"
    override val ratingHard = "Hard"

    /** En dash, matching the wave dash's job in 五〜十五分: a span, not a subtraction. */
    override val durationUnderFive = "Under 5 min"
    override val durationFiveToFifteen = "5–15 min"
    override val durationOverFifteen = "15 min+"

    override val rowTimeCap = "Time cap"
    override val rowRestBetweenStations = "Rest between stations"
    override val rowRestBetweenRounds = "Rest between rounds"
    override val rowRounds = "Rounds"

    /** 時間内で answers "how many rounds" with "as many as fit", which is the honest answer. */
    override val roundsWithinCap = "As many as fit"
    override val restNone = "None"

    override val timesDone = "Times done"
    override val metricBestTime = "Fastest"
    override val metricMostRounds = "Most rounds"
    override val metricMostReps = "Most reps"

    /** Unitless, like the Japanese: it is a weighted total and never a count of anything. */
    override val metricMostVolume = "Highest load"

    override fun stepTitle(index: String, unit: StepUnit): String = when (unit) {
        StepUnit.STEP -> "Step $index"
        StepUnit.DAY -> "Day $index"
    }

    override fun stepCaption(count: String, unit: StepUnit): String = when (unit) {
        StepUnit.STEP -> "of $count steps"
        StepUnit.DAY -> "of $count days"
    }

    override fun estimateDuration(duration: String): String = "About $duration"
    override fun estimateRepsCap(reps: String): String = "up to $reps"

    /** 目安 is "a rough guide". Abbreviated because the line it tails is already three fragments. */
    override fun estimateApproximate(line: String): String = "$line approx."

    override fun patternClash(first: String, second: String): String =
        "Better not to place $first and $second back to back"

    override val scheduleNext = "Add to calendar"
}
