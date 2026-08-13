package io.eddiegulay.tempo.i18n

/**
 * Records: index, history, charts, personal records, session detail.
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
 * **Why so many members are functions taking a `String`.** Every numeral, duration and date arrives
 * *already formatted* from [Formats] — this table never sees an `Int`. What it owns is the sentence
 * around the number, and that sentence is not a prefix and a suffix that a call site could glue
 * together: 「二十種目中 八」 puts the total first and "8 of 20 stations" puts it last, 「ならして 三.四回」
 * circumfixes its counter and "averaging 3.4" has none. A call site that concatenated two members
 * would have baked Japanese word order into English.
 *
 * **What is deliberately absent.** There is no member for a *slower* session (`RecordCopy`'s documented
 * gap — inventing 遅い is forbidden), none for the unselected range chip, and none for
 * `BestMetric.HIGHEST_STEP`. Each of those is a null the specs chose; a string here would be the
 * invention the null exists to refuse.
 *
 * Three sentences this namespace could have owned and does not, because they already have an owner:
 * `もう一度` on the history footer is a genuine retry and takes [FaultStrings.retry];
 * 「この型は削除されています」 under a greyed もう一度 is [GymFaultStrings.routineGone], the same sentence
 * about the same fact; and 記録を読めません reaches the quarantine panel through `faultCopy`.
 */
interface GymRecordsStrings {

    // ─── Words shared across the five pages ─────────────────────────────────────────────────────

    /** 記録 — `GYM.RECORDS.INDEX`'s title. */
    val pageTitle: String

    /** 読み込み中 — a read that has not answered. Seven unshared composables draw it; one word. */
    val loading: String

    /**
     * とじる — the generic close.
     *
     * **Deliberately not `CompletePage`'s 閉じる** (§H8). The player's terminal word is written in
     * kanji and this one in hiragana, on purpose, and both are "Close" in English — the distinction
     * lives in the Japanese table and nowhere else. Merging the *keys* would delete it.
     */
    val close: String

    /** 活動時間 — the hero's label, the 記録 tile, and the middle chart's heading. One fact, one word. */
    val activeTime: String

    /** 最高 — the bests page, and the row on 記録 that opens it. */
    val bests: String

    /** これまでを見る — the way into `GYM.RECORDS.HISTORY` from the charts and bests pages. */
    val seeHistory: String

    // ─── Empty states ───────────────────────────────────────────────────────────────────────────

    /**
     * まだ 記録はありません — the **long** form, on 記録, 移り変わり and 最高.
     *
     * Kept apart from [historyEmpty] because §H8 keeps them apart: two lengths, three pages, sourced
     * separately, and the index's carries a 型をえらぶ action beside it.
     */
    val recordsEmpty: String

    /** 記録はありません — `GYM.RECORDS.HISTORY`'s own shorter form. */
    val historyEmpty: String

    /**
     * この型は まだ やっていません — a claim about **one routine**.
     *
     * Never reachable through [historyEmpty] and never the other way round: one is a statement about
     * the store, the other about a routine the user just navigated from.
     */
    val historyEmptyFiltered: String

    /** 型をえらぶ — the empty index's one action. */
    val chooseRoutine: String

    // ─── `GYM.RECORDS.INDEX` ────────────────────────────────────────────────────────────────────

    /** 今月 — always the *current* month, never the month the pager is showing. */
    val tileMonth: String

    /** これまで — the lifetime tile. */
    val tileLifetime: String

    /** 週ごと — the sparkline's section heading. */
    val weeklyHeading: String

    /** 最近 — the three-row section heading. */
    val recentHeading: String

    /** 詳しく — the header action, and the sparkline's own click label. They do the same thing. */
    val actionDetail: String

    /** すべて見る — 最近's way into the whole list. */
    val actionSeeAll: String

    /** 記録の一覧をひらく — the 42-day canvas's click label, which is the whole grid's affordance. */
    val gridA11y: String

    // ─── The ink grid's captions (`InkDensity`) ─────────────────────────────────────────────────

    /**
     * この月は 〇日 — a month with nothing in it.
     *
     * §4 is explicit that this **is not the empty state**: a month you did not train is a fact about
     * June, and an empty store is a fact about the app.
     *
     * @param days already through [Formats.days], and zero on purpose.
     */
    fun monthUntrained(days: String): String

    /**
     * 十二日 鍛錬しました — how much of a month carries ink.
     *
     * @param days already through [Formats.days].
     */
    fun trainedDays(days: String): String

    /**
     * いちばん多かったのは 六月十七日 — the grid's spoken summary names its busiest day.
     *
     * @param date already through [Formats.monthDay].
     */
    fun busiestDay(date: String): String

    // ─── `GYM.RECORDS.HISTORY` ──────────────────────────────────────────────────────────────────

    /** これまで — the history page's title. Same word as [tileLifetime], different role. */
    val historyTitle: String

    /**
     * 「七分間」十四回 — the subtitle when the list is filtered to one routine.
     *
     * The minutes are dropped deliberately (§4): opened from a routine's page, the number that matters
     * is how many times you have done it.
     *
     * @param routineName **user or catalogue data**, arriving in whatever language it was stored in.
     *   §L10: `session.routine_name` is denormalised at write time and is never retranslated.
     * @param times already through [Formats.times].
     */
    fun historyForRoutine(routineName: String, times: String): String

    /** メニュー — the long press a screen reader cannot see, named so it can be reached. */
    val menuA11y: String

    /** 記録を削除 — the history menu, and the session detail's foot. One act, one word. */
    val deleteRecord: String

    /** この型を見る — from a history row. Distinct from [openRoutine] (§H8). */
    val openThisRoutine: String

    /** 型を見る — from the session detail's foot. */
    val openRoutine: String

    /** この記録を削除しますか — the confirmation's title. */
    val deleteConfirmTitle: String

    /**
     * 元に戻せません。 — the only trailing ideographic full stop in this scope, and it is transcribed.
     *
     * There is no count and no blast radius beside it: one session is its own blast radius.
     */
    val deleteConfirmBody: String

    /** 削除 — the destructive button. */
    val deleteConfirm: String

    /**
     * やめる — the way out of the delete dialog.
     *
     * The quit sheet's escape is つづける and must **not** be merged with this (§H8): やめる means
     * abandon, which is the right word for cancelling a deletion and the wrong one for leaving a
     * workout.
     */
    val deleteDismiss: String

    // ─── `GYM.RECORDS.CHARTS` ───────────────────────────────────────────────────────────────────

    /** 移り変わり — how things shift. */
    val chartsTitle: String

    /**
     * 選択中 — the chosen range chip's `stateDescription`.
     *
     * **There is no word for the unselected state and none is to be invented** (§3): an unselected chip
     * carries no state, which is also what it means.
     */
    val chipSelected: String

    // ─── `GYM.RECORDS.PR` ───────────────────────────────────────────────────────────────────────

    /** 型ごと — declaration order is display order. */
    val tabRoutines: String

    /** 動きごと. */
    val tabMovements: String

    /**
     * いちばん上 — the hardest rung of a movement ladder reached.
     *
     * **This surface's word and only this surface's** (§Q9). It is not a routine tile and must not be
     * repurposed as one; a routine's step is `stepFor`'s 第九段.
     */
    val hardestReached: String

    /** 一度に — the label alone, for the visible meta line. */
    val singleSetLabel: String

    /**
     * 一度に 三十二回 — the spoken form, which splits the meta line differently from the visible one.
     *
     * @param value already through [Formats.reps].
     */
    fun singleSet(value: String): String

    /**
     * のべ 四百回 — the family's lifetime total.
     *
     * @param reps already through [Formats.reps].
     */
    fun lifetimeReps(reps: String): String

    /** 回数を数えた種目だけ ここに出ます — why 動きごと can be empty without anything being wrong. */
    val movementsEmptyWhy: String

    /** すべての種目 — this page's exit into the exercise index. */
    val allExercises: String

    /** この記録を見る — a `CustomAccessibilityAction` on a bests card. */
    val actionSession: String

    // ─── `GYM.RECORDS.SESSION_DETAIL` ───────────────────────────────────────────────────────────

    /** 記録の中身 — one finished session, reopened. */
    val detailTitle: String

    /**
     * 当時の内容は残っていません — **the backup-restore case only**.
     *
     * Not the ordinary case of a routine edited afterwards; the version pin is exactly what survives
     * that, and [structureChanged] is the separate, gentler thing said about it.
     */
    val missingSnapshot: String

    // ─── The record itself (`RecordSummary`, `RecordCopy`) ──────────────────────────────────────

    /**
     * 到達 — the hero label on an `EMOM_ASCENDING` fail-out.
     *
     * The number means *how far up the ladder*, not *time spent*, so the label changes with it.
     */
    val heroLabelReached: String

    /**
     * 十七分で 力尽きた — the chip a fail-out carries instead of 途中まで.
     *
     * @param duration already through [Formats.durationFromMs].
     */
    fun failedOut(duration: String): String

    /**
     * 途中まで — a session that ended early, said with dignity and never as a score.
     *
     * Drawn bare when `stations_planned` is unusable; [partialStations] is appended otherwise.
     */
    val partial: String

    /**
     * 二十種目中 八 — how far a partial session got.
     *
     * Japanese puts the **total first** and English puts it last, which is the whole reason this is a
     * function rather than two members joined at the call site.
     *
     * @param completed already through [Formats.count] — bare, because the unit is on [stations].
     * @param stations the frozen `stations_planned`, already through [Formats.stations].
     */
    fun partialStations(completed: String, stations: String): String

    /** 自己最高 — the record stands. */
    val prCurrent: String

    /** 当時の自己最高 — it was a record when it was set, and has since been beaten. */
    val prFormer: String

    /**
     * 前回より 二十二秒 速い — design §12's one comparison sentence.
     *
     * There is deliberately **no slower, level or round-based twin**. See `RecordCopy.comparisonCopy`:
     * the gap is documented and inventing 遅い is forbidden.
     *
     * @param duration already through [Formats.duration].
     */
    fun fasterThanLast(duration: String): String

    /** はじめての記録 — a fact, not a compliment, which is why it survives accolade suppression. */
    val firstEver: String

    /**
     * 四日 連続 — the streak, in days.
     *
     * Never `〇日 連続`: zero is not a small streak and the caller returns null for it.
     *
     * @param days already through [Formats.days].
     */
    fun streakDays(days: String): String

    /**
     * 連続は とぎれています — the streak has ended.
     *
     * A fact with no verdict in it, and a sentence you can read on a rest day without flinching. The
     * record screen deliberately does **not** use it (see `RecordSummary.streakLine`).
     */
    val streakBroken: String

    /**
     * ゆるし 一回 使いました — the allowance that was spent.
     *
     * **It never states how many days remain**, and [io.eddiegulay.tempo.gym.Streak] carries no
     * remainder to print: a number you can watch going down is a number you will spend.
     *
     * @param times already through [Formats.times].
     */
    fun forgivenessUsed(times: String): String

    /** 同じ調子が続いています — gated on a threshold *and* a fortnight of history. */
    val monotonyNudge: String

    /** 種目 — the first tile's label. The same word its value is suffixed with, in both languages. */
    val tileLabelStations: String

    /** 巡. */
    val tileLabelRounds: String

    /** 回. */
    val tileLabelReps: String

    /** どうでしたか — the rating heading, dropped on a historical record that has an answer. */
    val ratingPrompt: String

    /** 未評価 — the radio group's state before it has one. */
    val ratingUnset: String

    /**
     * ちょうど を選択 — the group's `stateDescription` once answered.
     *
     * @param rating from `Rating.label`, which is `gymShared`'s.
     */
    fun ratingSelected(rating: String): String

    /**
     * きついとして記録する — a chip's `onClick` label.
     *
     * 「として」 is a case particle and English needs a verb in front instead, which is why this is a
     * function and not a suffix concatenated at the call site.
     */
    fun ratingOption(rating: String): String

    /** 内訳 — the breakdown's heading. */
    val breakdownHeading: String

    /**
     * 不明な種目 — the catalogue no longer knows the id the session recorded.
     *
     * A data fact, stated. A blank in a list of names reads as a rendering bug.
     */
    val unknownExercise: String

    /** 済 — a station that was done. */
    val statusDone: String

    /** とばした — a station that was skipped. The row draws faint as a whole. */
    val statusSkipped: String

    /**
     * 十八回 / 二十回 — actual against prescribed, when they disagree.
     *
     * Both numerals arrive bare, from [Formats.count], and each language attaches its own counter:
     * Japanese suffixes both (十八回 / 二十回) and English carries one at the end (18 / 20 reps). A call
     * site handed two [Formats.reps] strings could only have produced "18 reps / 20 reps".
     */
    fun repsShortfall(actual: String, prescribed: String): String

    /**
     * 十七日 — the day alone, on a history row that sits under a month header.
     *
     * **Not a date formatter.** [Formats] has `monthDay` and `days`; this is neither — it is the
     * bare day-of-month with Japanese's 日 and nothing at all in English, because the month is already
     * on screen one row up. A `fmt.dayOfMonth` would be the right home for it and does not exist;
     * that is reported rather than built here.
     *
     * @param day already through [Formats.count].
     */
    fun dayOfMonth(day: String): String

    /**
     * もう一度 — back to the routine.
     *
     * **Not a retry** ([FaultStrings.retry]), and the two are different words in English. This one
     * means *do this workout again*; the history footer's もう一度 means *ask the store again*.
     */
    val repeat: String

    /** 中身が変わっています — the routine has been edited since the record was set. */
    val structureChanged: String

    /** 削除済み — the routine was deleted. The row stays tappable and says so. */
    val archived: String

    // ─── The charts (`ChartGeometry`) ───────────────────────────────────────────────────────────

    /*
     * The three range labels are strings and are **not** derived from their week counts: 一年 is not
     * 五十二週, and computing the chip text from the number would produce exactly that.
     */

    /** 十二週. */
    val rangeTwelveWeeks: String

    /** 二十六週. */
    val rangeTwentySixWeeks: String

    /** 一年 — fifty-two weeks, spelled as a year. */
    val rangeYear: String

    /** 週ごとの回数. */
    val chartWeeklySessions: String

    /** 積み上げ — weighted volume, accumulating. */
    val chartVolume: String

    /**
     * 週ごとの回数（折れ線） — the heading when the bars have gone sub-pixel and become a line.
     *
     * Only the bar chart can change renderer, so the other two never reach this.
     */
    fun chartAsLine(heading: String): String

    /**
     * 二十八日ぶん たまると 出ます — 積み上げ has not earned its place yet.
     *
     * Not empty and not broken: *early*. Design §7.4 suppresses load surfaces below four weeks because
     * a weighted-volume trend over six days is noise presented as insight.
     *
     * @param days already through [Formats.days] — the threshold, so the sentence and the constant
     *   cannot drift.
     */
    fun volumeSuppressed(days: String): String

    /**
     * いちばん多い週 六回 — the drawn caption's first clause.
     *
     * @param count already through [Formats.count]; the counter belongs to the sentence, because
     *   Japanese repeats it on the average beside this and English does not.
     */
    fun busiestWeek(count: String): String

    /** いちばん多い週は 六回 — the **spoken** form. §4 writes both and they differ by one particle. */
    fun busiestWeekSpoken(count: String): String

    /**
     * ならして 三.四回 — the mean, over finished weeks only.
     *
     * @param value already through [Formats.coefficient]. A decimal, so it cannot go through
     *   [Formats.reps].
     */
    fun weeklyAverage(value: String): String

    /** 今週は 二回 — the spoken summary's last clause, on the sessions chart alone. */
    fun thisWeekSpoken(count: String): String

    /**
     * 合計 二千四百分 — the minutes chart's total, which counts every week.
     *
     * @param minutes already through [Formats.minutes].
     */
    fun totalMinutes(minutes: String): String

    /** ならして 二百分/週 — the same chart's mean, which counts only the finished ones. */
    fun weeklyAverageMinutes(minutes: String): String

    /** 日ごとの積み上げと 七日平均 — 積み上げ's caption states its method rather than its numbers. */
    val volumeMethod: String

    /**
     * 目安 — and it is **not optional** (§5.4).
     *
     * Weighted volume takes duration stations in through an approximation, so the number is an
     * estimate wearing a total's clothes and this word is what keeps it honest.
     */
    val estimateNote: String

    /**
     * 直近十二週 — the spoken summary's range clause.
     *
     * @param range one of the three range labels above.
     */
    fun overLast(range: String): String

    /** 今週 — the bar chart's right-hand axis label. */
    val thisWeek: String
}

internal object JaGymRecords : GymRecordsStrings {

    override val pageTitle = "記録"
    override val loading = "読み込み中"
    override val close = "とじる"
    override val activeTime = "活動時間"
    override val bests = "最高"
    override val seeHistory = "これまでを見る"

    override val recordsEmpty = "まだ 記録はありません"
    override val historyEmpty = "記録はありません"
    override val historyEmptyFiltered = "この型は まだ やっていません"
    override val chooseRoutine = "型をえらぶ"

    override val tileMonth = "今月"
    override val tileLifetime = "これまで"
    override val weeklyHeading = "週ごと"
    override val recentHeading = "最近"
    override val actionDetail = "詳しく"
    override val actionSeeAll = "すべて見る"
    override val gridA11y = "記録の一覧をひらく"

    override fun monthUntrained(days: String) = "この月は $days"
    override fun trainedDays(days: String) = "$days 鍛錬しました"
    override fun busiestDay(date: String) = "いちばん多かったのは $date"

    override val historyTitle = "これまで"
    override fun historyForRoutine(routineName: String, times: String) = "「$routineName」$times"
    override val menuA11y = "メニュー"
    override val deleteRecord = "記録を削除"
    override val openThisRoutine = "この型を見る"
    override val openRoutine = "型を見る"
    override val deleteConfirmTitle = "この記録を削除しますか"
    override val deleteConfirmBody = "元に戻せません。"
    override val deleteConfirm = "削除"
    override val deleteDismiss = "やめる"

    override val chartsTitle = "移り変わり"
    override val chipSelected = "選択中"

    override val tabRoutines = "型ごと"
    override val tabMovements = "動きごと"
    override val hardestReached = "いちばん上"
    override val singleSetLabel = "一度に"
    override fun singleSet(value: String) = "一度に $value"
    override fun lifetimeReps(reps: String) = "のべ $reps"
    override val movementsEmptyWhy = "回数を数えた種目だけ ここに出ます"
    override val allExercises = "すべての種目"
    override val actionSession = "この記録を見る"

    override val detailTitle = "記録の中身"
    override val missingSnapshot = "当時の内容は残っていません"

    override val heroLabelReached = "到達"
    override fun failedOut(duration: String) = "${duration}で 力尽きた"
    override val partial = "途中まで"
    override fun partialStations(completed: String, stations: String) = "${stations}中 $completed"
    override val prCurrent = "自己最高"
    override val prFormer = "当時の自己最高"
    override fun fasterThanLast(duration: String) = "前回より $duration 速い"
    override val firstEver = "はじめての記録"
    override fun streakDays(days: String) = "$days 連続"
    override val streakBroken = "連続は とぎれています"
    override fun forgivenessUsed(times: String) = "ゆるし $times 使いました"
    override val monotonyNudge = "同じ調子が続いています"

    override val tileLabelStations = "種目"
    override val tileLabelRounds = "巡"
    override val tileLabelReps = "回"

    override val ratingPrompt = "どうでしたか"
    override val ratingUnset = "未評価"
    override fun ratingSelected(rating: String) = "$rating を選択"
    override fun ratingOption(rating: String) = "${rating}として記録する"

    override val breakdownHeading = "内訳"
    override val unknownExercise = "不明な種目"
    override val statusDone = "済"
    override val statusSkipped = "とばした"
    override fun repsShortfall(actual: String, prescribed: String) = "${actual}回 / ${prescribed}回"
    override fun dayOfMonth(day: String) = "${day}日"

    override val repeat = "もう一度"
    override val structureChanged = "中身が変わっています"
    override val archived = "削除済み"

    override val rangeTwelveWeeks = "十二週"
    override val rangeTwentySixWeeks = "二十六週"
    override val rangeYear = "一年"
    override val chartWeeklySessions = "週ごとの回数"
    override val chartVolume = "積み上げ"
    override fun chartAsLine(heading: String) = "$heading（折れ線）"
    override fun volumeSuppressed(days: String) = "${days}ぶん たまると 出ます"

    override fun busiestWeek(count: String) = "いちばん多い週 ${count}回"
    override fun busiestWeekSpoken(count: String) = "いちばん多い週は ${count}回"
    override fun weeklyAverage(value: String) = "ならして ${value}回"
    override fun thisWeekSpoken(count: String) = "今週は ${count}回"
    override fun totalMinutes(minutes: String) = "合計 $minutes"
    override fun weeklyAverageMinutes(minutes: String) = "ならして $minutes/週"
    override val volumeMethod = "日ごとの積み上げと 七日平均"
    override val estimateNote = "目安"
    override fun overLast(range: String) = "直近$range"
    override val thisWeek = "今週"
}

/**
 * English.
 *
 * Three judgements run through the words below.
 *
 * **The record does not congratulate.** 途中まで is *up to partway*, not *incomplete*; 力尽きた is
 * *strength ran out*, not *failed*. The English keeps that register — a session you stopped is
 * recorded, never graded — which is `00-plan.md` §4.1 rule 2 expressed in vocabulary rather than in
 * layout.
 *
 * **Length is functional.** These strings sit in 11–13.sp captions, in chips 28.dp tall, in a tab
 * label with a rule measured to it, and in seven-glyph tile labels. Japanese is roughly half the width
 * of English for the same content, so the compact form is chosen wherever the meaning survives it.
 *
 * **Two words that look mergeable stay apart**, because the Japanese keeps them apart on purpose
 * (§H8): [close] against `CompletePage`'s 閉じる, [recordsEmpty] against [historyEmpty], and
 * [openRoutine] against [openThisRoutine]. English cannot carry the first of those three distinctions
 * at all; it is preserved in the Japanese table rather than deleted from both.
 */
internal object EnGymRecords : GymRecordsStrings {

    override val pageTitle = "Records"
    override val loading = "Loading"
    override val close = "Close"
    override val activeTime = "Active time"
    override val bests = "Bests"
    override val seeHistory = "See all time"

    override val recordsEmpty = "No records yet"
    override val historyEmpty = "No records"
    override val historyEmptyFiltered = "You have not done this routine yet"
    override val chooseRoutine = "Choose a routine"

    override val tileMonth = "This month"
    override val tileLifetime = "All time"
    override val weeklyHeading = "By week"
    override val recentHeading = "Recent"

    /** 詳しく is *in more detail*, which is what the charts page is: the same facts, unsummarised. */
    override val actionDetail = "In detail"
    override val actionSeeAll = "See all"
    override val gridA11y = "Open the list of records"

    override fun monthUntrained(days: String) = "$days this month"
    override fun trainedDays(days: String) = "Trained $days"
    override fun busiestDay(date: String) = "Busiest day $date"

    override val historyTitle = "All time"
    override fun historyForRoutine(routineName: String, times: String) = "$routineName · $times"
    override val menuA11y = "Menu"
    override val deleteRecord = "Delete record"
    override val openThisRoutine = "See this routine"
    override val openRoutine = "See the routine"
    override val deleteConfirmTitle = "Delete this record?"
    override val deleteConfirmBody = "This cannot be undone."
    override val deleteConfirm = "Delete"
    override val deleteDismiss = "Cancel"

    /** 移り変わり is how things shift over time. "Trends" is the one-word English for that. */
    override val chartsTitle = "Trends"
    override val chipSelected = "Selected"

    override val tabRoutines = "By routine"
    override val tabMovements = "By movement"

    /** いちばん上 is the top rung *reached*, not the top rung that exists. */
    override val hardestReached = "Highest reached"
    override val singleSetLabel = "In one set"
    override fun singleSet(value: String) = "$value in one set"
    override fun lifetimeReps(reps: String) = "$reps in total"
    override val movementsEmptyWhy = "Only movements with counted reps appear here"
    override val allExercises = "All movements"
    override val actionSession = "See this record"

    override val detailTitle = "Record details"
    override val missingSnapshot = "The contents of that day are no longer stored"

    /** 到達 is *how far you got* before the minute beat you, which is what the number now measures. */
    override val heroLabelReached = "Reached"

    /** 力尽きた is strength running out, not failure. */
    override fun failedOut(duration: String) = "Gave out at $duration"

    /** 途中まで is *up to partway*. Recorded, not graded. */
    override val partial = "Stopped early"
    override fun partialStations(completed: String, stations: String) = "$completed of $stations"
    override val prCurrent = "Personal best"

    /** 当時の — it was the best *then*. The chip is faint for the same reason. */
    override val prFormer = "Best at the time"
    override fun fasterThanLast(duration: String) = "$duration faster than last time"
    override val firstEver = "Your first record"
    override fun streakDays(days: String) = "$days in a row"

    /** とぎれています states a fact and passes no judgement on it; "ended" is the same restraint. */
    override val streakBroken = "The streak has ended"
    override fun forgivenessUsed(times: String) = "Grace used $times"

    /** 同じ調子 is *the same tune*: not too much training, the same training. */
    override val monotonyNudge = "Your sessions have been much the same"

    override val tileLabelStations = "Stations"
    override val tileLabelRounds = "Rounds"
    override val tileLabelReps = "Reps"

    override val ratingPrompt = "How was it?"
    override val ratingUnset = "Not rated"
    override fun ratingSelected(rating: String) = "$rating selected"
    override fun ratingOption(rating: String) = "Record as $rating"

    override val breakdownHeading = "Breakdown"
    override val unknownExercise = "Unknown movement"
    override val statusDone = "Done"
    override val statusSkipped = "Skipped"
    override fun repsShortfall(actual: String, prescribed: String) = "$actual / $prescribed reps"

    /** The month is already on the header above the row, so the day stands alone. */
    override fun dayOfMonth(day: String) = day

    /** 「back to the routine」, which is not the fault layer's "Try again". */
    override val repeat = "Do it again"
    override val structureChanged = "The routine has changed since"
    override val archived = "Deleted"

    override val rangeTwelveWeeks = "12 weeks"
    override val rangeTwentySixWeeks = "26 weeks"
    override val rangeYear = "1 year"
    override val chartWeeklySessions = "Sessions per week"

    /** 積み上げ is what piles up. Not "volume", which is the metric's name and nobody's word for it. */
    override val chartVolume = "Build-up"
    override fun chartAsLine(heading: String) = "$heading (line)"
    override fun volumeSuppressed(days: String) = "Appears once $days have built up"

    override fun busiestWeek(count: String) = "Busiest week $count"
    override fun busiestWeekSpoken(count: String) = "Busiest week $count"
    override fun weeklyAverage(value: String) = "averaging $value"
    override fun thisWeekSpoken(count: String) = "this week $count"
    override fun totalMinutes(minutes: String) = "$minutes total"
    override fun weeklyAverageMinutes(minutes: String) = "averaging $minutes a week"
    override val volumeMethod = "Daily build-up and a 7-day mean"

    /** 目安 is *a rough guide*, and the caption it tails is already three fragments long. */
    override val estimateNote = "approx."
    override fun overLast(range: String) = "last $range"
    override val thisWeek = "This week"
}
