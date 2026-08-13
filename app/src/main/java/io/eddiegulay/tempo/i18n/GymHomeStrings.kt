package io.eddiegulay.tempo.i18n

/**
 * 鍛錬's home page and its resumable-session copy.
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
 * **Why a dozen of these are functions rather than constants.** `GymHomeCopy.kt` had no string
 * `const val` at all — every line it drew was a `+` around a numeral — and the migration's job was to
 * turn each one into a *whole composed string* the table owns end to end. 「八種目まで進んだ」 puts its
 * verb after the count and "Reached 8 stations" puts it before; 「最高 十七巡」 and "Best 17 rounds"
 * agree only by accident. A call site that concatenated two members would have baked Japanese word
 * order into every language, which is the failure this whole layer exists to prevent. The numeral
 * itself always arrives already formatted, from [Formats] — this table never sees an `Int`.
 */
interface GymHomeStrings {

    // ─── The page ───────────────────────────────────────────────────────────────────────────────

    /** 鍛錬 — the page heading. See [tabTrain] for why the tab's word is a second member. */
    val title: String

    /** 読み込み中 — a feed still arriving, which is never the same picture as one that came back empty. */
    val loading: String

    /** 作る — the header action, and 自分の型's inline empty state. */
    val actionCreate: String

    /**
     * 型を作る — what 作る *opens*, spoken rather than drawn.
     *
     * It names the builder's own page title, so the announcement and the page a user lands on agree.
     */
    val createDescription: String

    /** すべて見る — beside 型's heading, and only when the preview is hiding something. */
    val seeAll: String

    /** 閉じる — the safety footnote's dismiss, and a stuck write fault's. */
    val close: String

    /** やめる — backing out of a dialog. Not the gym's *other* やめる, which abandons a workout. */
    val cancel: String

    // ─── The tab bar ────────────────────────────────────────────────────────────────────────────

    /*
     * 鍛錬 / 型 / 記録 — three words in three `weight(1f)` thirds. They were constructor arguments on
     * `GymTab` and moved here under §L3: an argument is resolved at class-init and cannot follow the
     * language when the user flips the picker. See `GymTab.label`.
     *
     * **These are the app's most width-constrained labels**, which is why [tabTrain] exists beside
     * [title] rather than being the same member: the page heading has a whole line and the tab has a
     * third of one at any font scale.
     */

    val tabTrain: String
    val tabLibrary: String
    val tabRecords: String

    // ─── The three sections ─────────────────────────────────────────────────────────────────────

    val sectionFrequent: String

    /** 型 — the built-in list. The same word as the library's own title, deliberately a second constant. */
    val sectionBuiltIn: String

    val sectionUser: String

    /**
     * 型はまだありません — the one emptiness this page ever admits to, scoped to 自分の型.
     *
     * **It must never stand in for a failed read.** A store that could not be read says 記録を読めません
     * through `FaultPanel`; this sentence is a true statement about a section of a page whose other
     * sections are full.
     */
    val userEmpty: String

    // ─── A card's three lines ───────────────────────────────────────────────────────────────────

    /**
     * 前回 六分五十秒 ・ 三日前 — what last time was, and when.
     *
     * @param value a duration or a round count, already formatted; which one is engine-dependent.
     * @param day already through [Formats.relativeDay].
     *
     * The separator between them is [Formats.separator] and not a character baked in here: the card's
     * TalkBack node re-punctuates this very line into [Formats.listSeparator], and it can only find
     * the separator if the table used the one the formatter owns.
     */
    fun lastResult(value: String, day: String): String

    /**
     * 最高 十七巡 / 最高 三百二十回 — the record, when the metric is a *most*.
     *
     * One member for rounds and reps because the difference is entirely in the counter, which arrives
     * already formatted.
     */
    fun bestMost(value: String): String

    /**
     * 最速 六分十四秒 — a different word from 最高, and the difference is load-bearing.
     *
     * 最高 六分十四秒 would read as the *longest* session, which is the opposite of the record.
     */
    fun bestFastest(duration: String): String

    /** 最高負荷 八十四 — difficulty-weighted volume, deliberately unitless in both languages. */
    fun bestVolume(value: String): String

    /**
     * 型の操作 — the long-press, declared so a gesture is not invisible to a screen reader.
     *
     * The *menu it opens* has no members here: 編集 / 写して作る / 削除 are the same three actions
     * `GYM.LIBRARY` offers on the same gesture, and they are read from `strings.gymLibrary.action*` so
     * that one affordance keeps one set of words. (This page and the library index still announce the
     * *gesture* differently — 型の操作 here, メニュー there — which is a pre-existing split the survey
     * flagged and which no table can settle; it is on the report.)
     */
    val cardLongPress: String

    // ─── The つづき banner ──────────────────────────────────────────────────────────────────────

    /** つづき — the heading over the banner, and the first fragment of its one TalkBack node. */
    val resumeBanner: String

    /** 続ける — removed rather than disabled when there is nothing to resume into. */
    val resumeAction: String

    /**
     * 六分十四秒 経過 — how long the session has been running.
     *
     * Note the space before 経過 in the Japanese; it is transcribed as it ships.
     */
    fun elapsed(value: String): String

    /**
     * 八種目まで進んだ — how far the session got.
     *
     * @param stations already through [Formats.stations]. Never called with zero: before the first
     *   station closed the line is omitted, because 〇種目まで進んだ is a claim about nothing.
     */
    fun progress(stations: String): String

    /*
     * **削除's confirmation has no members here.** The same dialog is raised by three surfaces from the
     * same two rows of `04-library-records.md` §6, and it reads `strings.gymLibrary.delete*` on all
     * three — see `deleteRoutineCopy`. Three tables for one sentence is how the three drift, which is
     * the argument §Q7 makes about numerals and the reason this namespace deliberately stops short.
     */

    // ─── How stale the open session is ──────────────────────────────────────────────────────────

    /*
     * `03-player.md` §A edge case 4's words for an interrupted workout, and **they are buckets that
     * look like measurements**. The Japanese is transcribed as it ships — 一時間前 is the word the spec
     * gives the 10m–2h bucket and 二時間前 the word it gives 2h–4h, whatever the arithmetic — but the
     * English is authored, and authoring "2 hours ago" for a three-and-a-half-hour-old session would
     * state something false rather than something coarse. §A's rule refuses false *precision*; it does
     * not license a false *statement*, which is the same argument `stalenessLabel`'s KDoc makes about
     * where the 二時間前 bucket has to stop. So the English rounds where the Japanese names.
     *
     * Past four hours neither language uses these at all: the label stops measuring and asks
     * [Formats.relativeDay] what day it was.
     */

    /** Under ten minutes. */
    val stalenessJustNow: String

    /** Ten minutes to two hours. */
    val stalenessEarlier: String

    /** Two hours to the four-hour resumability horizon. */
    val stalenessSeveralHours: String

    // ─── The resume prompt ──────────────────────────────────────────────────────────────────────

    /**
     * 途中の 鍛錬があります — there is one, and you can walk back into it.
     *
     * Note the internal space; the Japanese is transcribed exactly.
     */
    val promptTitleOpen: String

    /**
     * 途中の 鍛錬が 残っています — one is *left over*, and the clock has closed the door on it.
     *
     * The title shifts on the clock and never on the option set: a session whose routine was deleted
     * keeps [promptTitleOpen], because the workout is still there even with nothing to resume into.
     */
    val promptTitleStopped: String

    /**
     * 続きからは できません — present exactly when 続ける was removed **by the clock**.
     *
     * A deleted routine also removes 続ける and gets no note: §A writes this sentence for the reboot
     * case alone, and saying more would be inventing an explanation.
     */
    val promptNoResume: String

    /** 記録する — keep what was done as a record. */
    val promptRecord: String

    /** 捨てる — the destructive door, asked twice. */
    val promptDiscard: String

    /**
     * 元に戻せません。 — the second ask.
     *
     * `04-library-records.md` §6's own sentence for a deletion that cannot be taken back. The resume
     * prompt's second confirm has no copy of its own anywhere in the specs; this is the nearest
     * documented line rather than a new one, in both languages.
     */
    val promptDiscardIrreversible: String

    /**
     * そのまま — leave the session exactly as it is.
     *
     * The way out of a modal whose only remaining option deletes a workout. **Auto-discarding is
     * tempting and wrong**: this app never deletes silently.
     */
    val promptLeave: String
}

internal object JaGymHome : GymHomeStrings {

    override val title = "鍛錬"
    override val loading = "読み込み中"
    override val actionCreate = "作る"
    override val createDescription = "型を作る"
    override val seeAll = "すべて見る"
    override val close = "閉じる"
    override val cancel = "やめる"

    override val tabTrain = "鍛錬"
    override val tabLibrary = "型"
    override val tabRecords = "記録"

    override val sectionFrequent = "よく使う"
    override val sectionBuiltIn = "型"
    override val sectionUser = "自分の型"
    override val userEmpty = "型はまだありません"

    override fun lastResult(value: String, day: String): String =
        "前回 " + value + JaFormats.separator + day

    override fun bestMost(value: String): String = "最高 " + value
    override fun bestFastest(duration: String): String = "最速 " + duration
    override fun bestVolume(value: String): String = "最高負荷 " + value

    override val cardLongPress = "型の操作"

    override val resumeBanner = "つづき"
    override val resumeAction = "続ける"
    override fun elapsed(value: String): String = value + " 経過"
    override fun progress(stations: String): String = stations + "まで進んだ"

    override val stalenessJustNow = "さっき"
    override val stalenessEarlier = "一時間前"
    override val stalenessSeveralHours = "二時間前"

    override val promptTitleOpen = "途中の 鍛錬があります"
    override val promptTitleStopped = "途中の 鍛錬が 残っています"
    override val promptNoResume = "続きからは できません"
    override val promptRecord = "記録する"
    override val promptDiscard = "捨てる"
    override val promptDiscardIrreversible = "元に戻せません。"
    override val promptLeave = "そのまま"
}

/**
 * English.
 *
 * Two judgements run through the words below.
 *
 * **The page never exclaims and never explains twice.** 型はまだありません is a flat statement of fact
 * and "No routines yet" is the same statement; "You haven't created any routines yet — tap + to get
 * started!" would be a faithful translation of the words and a wrong translation of the app.
 *
 * **Length is functional.** Three of these words live in `weight(1f)` thirds of a tab bar, two more in
 * a header row beside an accent action, and the card's third line holds up to four fragments on one
 * ellipsised line. Japanese is roughly half the width of English for the same content, so the English
 * is chosen short wherever the slot is measured rather than flowed.
 */
internal object EnGymHome : GymHomeStrings {

    override val title = "Training"
    override val loading = "Loading"

    // 作る is "make". The header has room for one accent word beside 設定 and the empty state repeats
    // it under an arrow, so it stays a single short verb rather than "Create routine".
    override val actionCreate = "New"
    override val createDescription = "Make a routine"

    override val seeAll = "See all"
    override val close = "Close"

    // やめる here backs out of a dialog. The gym's other やめる — abandoning a workout — is a different
    // string in a different namespace and the two must not be merged.
    override val cancel = "Cancel"

    // 鍛錬 as a tab is the verb, not the noun: "Train" is five glyphs against 鍛錬's two, and it is the
    // shortest honest English for the thing the tab leads to. The page's own heading is [title].
    override val tabTrain = "Train"
    override val tabLibrary = "Routines"
    override val tabRecords = "Records"

    override val sectionFrequent = "Frequent"

    // 型 is a routine — a shape of work, kept and re-run. Not a "form", which in a gym means posture.
    override val sectionBuiltIn = "Routines"
    override val sectionUser = "Your routines"
    override val userEmpty = "No routines yet"

    override fun lastResult(value: String, day: String): String =
        "Last " + value + EnFormats.separator + day

    override fun bestMost(value: String): String = "Best $value"
    override fun bestFastest(duration: String): String = "Fastest $duration"
    override fun bestVolume(value: String): String = "Highest load $value"

    override val cardLongPress = "Routine actions"

    // つづき is "the continuation". As a heading over a card whose action already says Continue, the
    // noun has to name the place rather than repeat the verb.
    override val resumeBanner = "Where you left off"
    override val resumeAction = "Continue"

    override fun elapsed(value: String): String = "$value elapsed"
    override fun progress(stations: String): String = "Reached $stations"

    // Buckets, not measurements — see the interface KDoc. The second covers ten minutes to two hours
    // and the third two hours to four, so neither states an hour count it cannot stand behind.
    override val stalenessJustNow = "Just now"
    override val stalenessEarlier = "A little while ago"
    override val stalenessSeveralHours = "A few hours ago"

    override val promptTitleOpen = "There's an unfinished workout"

    // 残っています — it is *left over*. The clock, not the data, is what closed the door.
    override val promptTitleStopped = "An unfinished workout is left"
    override val promptNoResume = "It can't be continued"

    override val promptRecord = "Record it"
    override val promptDiscard = "Discard"
    override val promptDiscardIrreversible = "This can't be undone."

    // そのまま is "as it is" — the session stays untouched and the question comes back next time.
    override val promptLeave = "Leave it"
}
