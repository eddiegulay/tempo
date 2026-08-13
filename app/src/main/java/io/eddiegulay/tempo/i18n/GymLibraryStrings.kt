package io.eddiegulay.tempo.i18n

/**
 * The routine library: index, detail, filters.
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
 * **What is deliberately *not* here.** The engine, tier, measure and duration-bucket words this page
 * draws are [GymSharedStrings]' — the library renders them and does not own them — and every count,
 * date and separator is [Formats]'. Both are read through the same [Strings] the caller already holds.
 */
interface GymLibraryStrings {

    /**
     * The default name a duplicated routine is **saved** under — not drawn, stored.
     *
     * Three things about the Japanese do not port, which is why this is two functions rather than a
     * suffix constant. ` の写し` is a **postposition**, so English wants the prefix `Copy of X`. The
     * collision counter starts at **two**, because the first copy is already 「の写し」 and 「の写し一」
     * reads as "copy one" of a copy the user cannot find. And the space before の is this copy's
     * habit throughout (まだ やっていません), not a typo.
     *
     * §L10's accepted consequence: a routine named under one language keeps that name after a
     * switch. It is the user's row from the moment it is written, and no toggle may rewrite it.
     */
    fun copyName(base: String): String

    /** The same name once [copyName] has collided; [ordinal] is already formatted and starts at two. */
    fun copyNameNumbered(base: String, ordinal: String): String

    // ─── 型, the index page ─────────────────────────────────────────────────────────────────────

    /**
     * The page's own title.
     *
     * Deliberately a separate member from [sectionBuiltIn] even though Japanese spells both 型: §6
     * lists the word twice, once as "library page title — here a page, not a section" and once as the
     * built-in section's heading. English has to say two different things, which is the case that
     * proves the split was right rather than redundant.
     */
    val title: String

    val sectionFrequent: String
    val sectionBuiltIn: String
    val sectionUser: String

    /** 探す — the header action that opens the search row. It names the *act*, not the state. */
    val searchOpen: String

    /** とじる — the same button once the row is open. */
    val searchClose: String

    /**
     * さがす — the field's placeholder and its `contentDescription`.
     *
     * Hiragana against [searchOpen]'s kanji, which is the page's own distinction between a button you
     * press and a box you type in. English has one word for both and that collapse is accepted.
     */
    val searchPlaceholder: String

    val create: String

    /** 型を作る — [create]'s spoken form, borrowed from the builder's title so both say one thing. */
    val createDescription: String

    /**
     * 選択中 — a filter chip that is on.
     *
     * There is no member for the unselected state and none may be invented: §3 documents one word, and
     * an unselected chip carries no state, which is also what it means.
     */
    val selected: String

    /** メニュー — the card's long-press, which is invisible to TalkBack without it. */
    val menu: String

    /**
     * 型はまだありません — 自分の型 with nothing under it.
     *
     * **Never stands in for a library that could not be read**, which is the trap §6 flags in bold and
     * the reason [noMatch] is a separate member rather than a shared "empty" string.
     */
    val userEmpty: String

    /** 種目を見る — the one exit from 型 into the movement catalogue. */
    val exercises: String

    val loading: String

    /** 該当する型はありません — the *search* found nothing. Not an empty library; see [userEmpty]. */
    val noMatch: String

    /** 絞り込みを外す — offered only when a chip is narrowing the page, because it clears chips only. */
    val clearFilters: String

    val cancel: String

    /**
     * 最高 十七巡 — a card's record line, and the 最高 section heading's own word with a value after it.
     *
     * A function because the value is already formatted by the time it arrives (`bestValueLabel`) and
     * because Japanese prefixes with a space where English needs none of the same shape.
     */
    fun bestValue(value: String): String

    // ─── The long-press menu and the detail page's foot ─────────────────────────────────────────

    /*
     * One set of words for both surfaces. The index offers them as a dropdown and as `customActions`,
     * the detail page as centred rows; §6 gives one table for both, and two key sets would be the
     * drift `DECISIONS.md` §Q7 describes for numerals arriving here instead.
     */

    /** 始める — the menu item *and* the detail page's primary button. */
    val start: String

    val actionDuplicate: String
    val actionFavourite: String
    val actionUnfavourite: String
    val actionEdit: String
    val actionDelete: String

    /** 元に戻す — un-archives. Offered in 削除's place, never beside it. */
    val actionRestore: String

    // ─── 削除's confirmation ────────────────────────────────────────────────────────────────────

    /*
     * The dialog exists in three implementations across `GYM.HOME`, this index and this detail page —
     * a data class, a sealed interface, and a call site that supplied its own やめる. The shapes are
     * still three; **the words are now one set**, and that is what stops them drifting.
     */

    /** 「シンディ」を削除しますか — the question, which does not depend on the count. */
    fun deleteTitle(name: String): String

    /**
     * これまでの六回の記録は残ります。型だけが一覧から消えます。
     *
     * @param count already through [Formats.times]. Never rendered at zero — that is [deleteBodyPurge]'s
     *   branch, and 〇回 in this sentence would promise the survival of nothing.
     */
    fun deleteBodyArchive(count: String): String

    /** やった記録はありません。完全に消えます。 — the irreversible branch, offered only at a genuine zero. */
    val deleteBodyPurge: String

    /** 削除 — the archive branch's button. Same word as [actionDelete] in Japanese, by design. */
    val deleteConfirmArchive: String

    /** 完全に削除 — the purge branch's, and the reason the two bodies exist. */
    val deleteConfirmPurge: String

    // ─── 型の中身, the detail page ──────────────────────────────────────────────────────────────

    /** とじる, on the header. Distinct from [searchClose]: this one leaves the page. */
    val close: String

    /** 削除済み — the chip beside an archived routine's subtitle. */
    val archived: String

    val sectionStructure: String
    val sectionBests: String
    val sectionAttempts: String

    /** すべて見る — into the full history, drawn only when there is history. */
    val seeAll: String

    /**
     * まだ やっていません — a fact about **this routine**, never about the store.
     *
     * Reachable only from a `RoutineDetail` the page already proved it could read. The internal space
     * is this copy's habit and is transcribed as it ships.
     */
    val noAttempts: String

    /** 出典 — a built-in's provenance. Absent, never 出典 —, when the user wrote the routine. */
    val origin: String

    /** 不明な種目 — a station whose exercise the catalogue no longer knows. It still lists. */
    val unknownExercise: String

    /**
     * 決めた時間で何巡できるか — the gloss under 時間内, and the only engine that has one.
     *
     * §6 :1118 attaches it to AMRAP and marks it "on the detail page". Six invented siblings would be
     * six sentences no spec wrote.
     */
    val glossAmrap: String

    /**
     * 「シンディ」を始める — what TalkBack reads on the start button, in **every** face it has.
     *
     * The visible label changes to [preparing] while the insert is in flight; this sentence does not,
     * because substituting the label produced 「シンディ」を支度, which is not a sentence in either
     * language.
     */
    fun startDescription(name: String): String

    /** 支度 — the in-flight face of 始める. A state's word, not an action's. */
    val preparing: String

    /** 種目が見つからないため 始められません — why 始める is inert, under the button. */
    val startBlockedUnknownExercise: String

    /**
     * 種目を加えてください — the builder's sentence for the same fact, borrowed.
     *
     * There is **no documented detail-page string** for a routine with no stations, and the case is
     * unreachable through the seeder and the builder. Borrowing beats inventing; it is on the report.
     */
    val startBlockedNoStations: String
}

internal object JaGymLibrary : GymLibraryStrings {

    override fun copyName(base: String) = "$base の写し"
    override fun copyNameNumbered(base: String, ordinal: String) = "$base の写し" + ordinal


    override val title = "型"
    override val sectionFrequent = "よく使う"
    override val sectionBuiltIn = "型"
    override val sectionUser = "自分の型"

    override val searchOpen = "探す"
    override val searchClose = "とじる"
    override val searchPlaceholder = "さがす"
    override val create = "作る"
    override val createDescription = "型を作る"
    override val selected = "選択中"
    override val menu = "メニュー"
    override val userEmpty = "型はまだありません"
    override val exercises = "種目を見る"
    override val loading = "読み込み中"
    override val noMatch = "該当する型はありません"
    override val clearFilters = "絞り込みを外す"
    override val cancel = "やめる"

    override fun bestValue(value: String): String = "最高 $value"

    override val start = "始める"
    override val actionDuplicate = "写して作る"
    override val actionFavourite = "よく使うに入れる"
    override val actionUnfavourite = "よく使うから外す"
    override val actionEdit = "編集"
    override val actionDelete = "削除"
    override val actionRestore = "元に戻す"

    override fun deleteTitle(name: String): String = "「" + name + "」を削除しますか"

    override fun deleteBodyArchive(count: String): String =
        "これまでの" + count + "の記録は残ります。型だけが一覧から消えます。"

    override val deleteBodyPurge = "やった記録はありません。完全に消えます。"
    override val deleteConfirmArchive = "削除"
    override val deleteConfirmPurge = "完全に削除"

    override val close = "とじる"
    override val archived = "削除済み"
    override val sectionStructure = "組み立て"
    override val sectionBests = "最高"
    override val sectionAttempts = "これまで"
    override val seeAll = "すべて見る"
    override val noAttempts = "まだ やっていません"
    override val origin = "出典"
    override val unknownExercise = "不明な種目"
    override val glossAmrap = "決めた時間で何巡できるか"

    override fun startDescription(name: String): String = "「" + name + "」を始める"

    override val preparing = "支度"
    override val startBlockedUnknownExercise = "種目が見つからないため 始められません"
    override val startBlockedNoStations = "種目を加えてください"
}

/**
 * English.
 *
 * Three judgements run through the words below.
 *
 * **型 is a routine, and its two headings are not the same word.** Japanese titles the page 型 and the
 * built-in section 型; English titles the page for what it lists and the section for where the rows
 * came from, which is what those two 型 actually mean.
 *
 * **The section headings and chips are width-constrained.** They sit at Mincho 12–13.sp with 3.sp
 * tracking in a horizontally-scrolling row, over cards whose name line is `maxLines = 1`. Two words is
 * the ceiling, and that is why 自分の型 is `Yours` rather than `Your routines` — the heading is already
 * under the page's own title.
 *
 * **The delete dialog says what it will do, not how it feels about it.** §1 rule 4 exists so the user
 * can see the blast radius, and the two bodies differ in the fact they state and in nothing else.
 */
internal object EnGymLibrary : GymLibraryStrings {

    // Parenthesised rather than bare: "Copy of Cindy 2" reads as a routine called "Cindy 2".
    override fun copyName(base: String) = "Copy of $base"
    override fun copyNameNumbered(base: String, ordinal: String) = "Copy of $base ($ordinal)"


    override val title = "Routines"
    override val sectionFrequent = "Frequent"
    override val sectionBuiltIn = "Built-in"
    override val sectionUser = "Yours"

    override val searchOpen = "Search"
    override val searchClose = "Close"
    override val searchPlaceholder = "Search"

    /** 作る is *make one*. The button opens the builder, so the shortest true word is the right one. */
    override val create = "New"
    override val createDescription = "Create a routine"
    override val selected = "Selected"
    override val menu = "Menu"

    /**
     * Not a bare "No routines" — the built-ins listed above it are routines, and this line sits under
     * [sectionUser]. It stays parallel with [noMatch], exactly as 型はまだありません and
     * 該当する型はありません are: the pair has to be **near-identical in shape and unmistakable in
     * meaning**, which is what §6 is warning about when it says never to swap one for the other.
     */
    override val userEmpty = "No routines of your own yet"
    override val exercises = "Browse movements"
    override val loading = "Loading"

    /** A statement about the *search*, which is the whole of what keeps it distinct from [userEmpty]. */
    override val noMatch = "No routines match"
    override val clearFilters = "Clear filters"
    override val cancel = "Cancel"

    override fun bestValue(value: String): String = "Best $value"

    override val start = "Start"

    /** 写して作る is *copy it and make one*, i.e. this is how you get an editable built-in. */
    override val actionDuplicate = "Make a copy"

    /** よく使う is the section's name, so both directions have to name it. */
    override val actionFavourite = "Add to frequent"
    override val actionUnfavourite = "Remove from frequent"
    override val actionEdit = "Edit"
    override val actionDelete = "Delete"
    override val actionRestore = "Restore"

    override fun deleteTitle(name: String): String = "Delete “" + name + "”?"

    /**
     * The count arrives as `6 times`, so the sentence has to be built around an adverbial rather than
     * around a noun phrase — "your 6 times" is not English. Saying it as a sentence of its own also
     * keeps the promise (the records stay) in a clause the reader reaches last.
     */
    override fun deleteBodyArchive(count: String): String =
        "You have done this " + count + ". The records stay; only the routine leaves the list."

    override val deleteBodyPurge = "There is nothing recorded. This removes it completely."
    override val deleteConfirmArchive = "Delete"
    override val deleteConfirmPurge = "Delete completely"

    override val close = "Close"

    /** The routine is archived, not gone; 削除済み is what the user's own 削除 left behind. */
    override val archived = "Deleted"
    override val sectionStructure = "Structure"
    override val sectionBests = "Best"

    /** これまで is *up to now*. "History" is the word the records tab already uses for the same list. */
    override val sectionAttempts = "History"
    override val seeAll = "See all"

    /** About this routine, and phrased so it cannot be read as a claim about the store. */
    override val noAttempts = "Not done yet"
    override val origin = "Source"
    override val unknownExercise = "Unknown movement"

    /** 何巡できるか — the question the engine asks the user, kept as a phrase and not a definition. */
    override val glossAmrap = "How many rounds fit in the time"

    override fun startDescription(name: String): String = "Start “" + name + "”"

    /** 支度 is *getting ready*. One word, because it replaces a 4-glyph label on a 64.dp button. */
    override val preparing = "Preparing"

    override val startBlockedUnknownExercise = "A movement is missing, so this cannot start"
    override val startBlockedNoStations = "Add a movement"
}
