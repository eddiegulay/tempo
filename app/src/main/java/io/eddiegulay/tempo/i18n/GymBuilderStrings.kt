package io.eddiegulay.tempo.i18n

/**
 * The routine builder and the station picker.
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
 * **Three strings that are conspicuously absent, and their absence is the point.** 種目の間の休息,
 * 巡の間の休息 and 巡数 were `private const val`s in `BuilderScreen.kt` used as *control flow* — the page
 * matched `engineRows`' output against them by string equality. They now live once, in
 * [GymSharedStrings], and the dispatch reads `EngineRow.kind`. Re-adding them here would put the same
 * words in two tables and invite the match to come back.
 *
 * Likewise `なし`: the builder's wheels read [GymSharedStrings.restNone] rather than declaring their
 * own, because the row above a wheel is `engineRows`' string and the rows *inside* it are this page's.
 * `DECISIONS.md` §Q10 requires the two to be the same word, and two table entries is exactly how one
 * of them comes to say "None" while the other says "No rest".
 */
interface GymBuilderStrings {

    // ─── The header ─────────────────────────────────────────────────────────────────────────────

    /** 型を作る — the builder opened with no routine behind it. */
    val titleCreate: String

    /** 型を編集 — the builder opened on an existing routine. */
    val titleEdit: String

    /**
     * やめる — the header's left action, **and the discard dialog's confirm button**.
     *
     * One string for both on purpose (§6 :1142): the destructive choice in the dialog reads as
     * finishing the act the header action started, rather than as a new decision.
     */
    val cancel: String

    /** 保存 — the one button on this page that writes. */
    val save: String

    /** 保存中 — the label while the write is in flight, and the honest reason it is inert. */
    val saving: String

    /**
     * 保存、名前と種目が要ります — §3's rule that a disabled word always says why.
     *
     * One string rather than [save] plus a reason: English wants a different join from Japanese's 、
     * and a two-part composition would spell 「Save, needs…」 by luck rather than by choice.
     */
    val saveBlocked: String

    // ─── The fields ─────────────────────────────────────────────────────────────────────────────

    /** 名前 — the label beside the name field, and the field's own `contentDescription`. */
    val fieldName: String

    /** 同じ名前の型があります — §3 edge case 4, which **warns and never blocks**. */
    val duplicateName: String

    /** 方式 — the engine row's label. The engine words themselves are [GymSharedStrings]'. */
    val fieldEngine: String

    /** 選択中 — the `stateDescription` on the chosen engine chip. */
    val selected: String

    /** 種目 — the station list's field label. */
    val fieldStations: String

    /** 種目を加えてください — the empty station list, which is a sentence and not an empty card. */
    val emptyStations: String

    /** ＋ 加える — full-width plus, a space, and the word. Label and description are one string. */
    val addStation: String

    /** これ以上は加えられません — §3 edge case 2, at `STATION_CAP` stations. */
    val stationCapReached: String

    /**
     * 不明な種目 — a station whose exercise the catalogue no longer knows still lists.
     *
     * It is a *name substitute*, drawn where an exercise name would be, so it is copy rather than a
     * sentinel: nothing compares against it and nothing stores it.
     */
    val unknownExercise: String

    /** 読み込み中 — an edit whose routine has not arrived. Never an empty state and never a fault. */
    val loading: String

    // ─── The station rows ───────────────────────────────────────────────────────────────────────

    /** 上へ動かす — a TalkBack custom action, because a drag is invisible to a screen reader. */
    val actionMoveUp: String

    /** 下へ動かす — the same, downward. */
    val actionMoveDown: String

    /** 編集 — opens the station picker on this row. */
    val actionEdit: String

    /** 削除 — removes this row from the draft. */
    val actionDelete: String

    /** 移動中 — the handle's `stateDescription` while a drag is live. */
    val dragging: String

    /**
     * 「腕立て伏せ の並べ替え」 — the drag handle's own description.
     *
     * Note the space before の in the Japanese: it is what shipped and is transcribed rather than
     * tidied.
     */
    fun reorderHandle(name: String): String

    /**
     * 「三番目に移動しました」 — announced on drop and on a TalkBack move.
     *
     * @param position already formatted by `fmt.ordinal`; 三番目 and `3rd` are the formatter's job, not
     *   this table's.
     */
    fun movedTo(position: String): String

    /**
     * 「これまでの六回の記録はそのまま残ります」 — §3 edge case 8, shown when the routine has history
     * **and** the structure is dirty.
     *
     * @param times already formatted by `fmt.times`.
     */
    fun historySafe(times: String): String

    // ─── The discard dialog ─────────────────────────────────────────────────────────────────────

    /** 編集をやめますか — raised only when there is something to lose. */
    val discardTitle: String

    /** 保存していない変更は消えます。 — note the full stop; it is in the Japanese. */
    val discardBody: String

    /** もどる — the escape, and deliberately the *dismiss* button rather than the confirm one. */
    val discardBack: String

    // ─── What changing the engine costs (`gym/BuilderDraft.kt`) ─────────────────────────────────

    /**
     * 段階では一種目だけ使われます — §6 :1140.
     *
     * The extra stations are kept and greyed rather than deleted, so this line is the only thing that
     * says why they stopped counting.
     */
    val noticeSingleStation: String

    /** 毎分では種目の間の休息はありません — §6 :1140. A rest forced to zero looks like one that was set. */
    val noticeNoStationRest: String

    /**
     * この方式では使えません — why a はかり方 chip is inert.
     *
     * Travels with the chip rather than sitting under the row, because §3's accessibility line makes
     * it part of the chip's description: 「秒数、この方式では使えません」.
     */
    val measureUnavailable: String
}

internal object JaGymBuilder : GymBuilderStrings {

    override val titleCreate = "型を作る"
    override val titleEdit = "型を編集"
    override val cancel = "やめる"
    override val save = "保存"
    override val saving = "保存中"
    override val saveBlocked = "保存、名前と種目が要ります"

    override val fieldName = "名前"
    override val duplicateName = "同じ名前の型があります"
    override val fieldEngine = "方式"
    override val selected = "選択中"
    override val fieldStations = "種目"
    override val emptyStations = "種目を加えてください"
    override val addStation = "＋ 加える"
    override val stationCapReached = "これ以上は加えられません"
    override val unknownExercise = "不明な種目"
    override val loading = "読み込み中"

    override val actionMoveUp = "上へ動かす"
    override val actionMoveDown = "下へ動かす"
    override val actionEdit = "編集"
    override val actionDelete = "削除"
    override val dragging = "移動中"

    override fun reorderHandle(name: String) = "$name の並べ替え"
    override fun movedTo(position: String) = position + "に移動しました"
    override fun historySafe(times: String) = "これまでの" + times + "の記録はそのまま残ります"

    override val discardTitle = "編集をやめますか"
    override val discardBody = "保存していない変更は消えます。"
    override val discardBack = "もどる"

    override val noticeSingleStation = "段階では一種目だけ使われます"
    override val noticeNoStationRest = "毎分では種目の間の休息はありません"
    override val measureUnavailable = "この方式では使えません"
}

internal object EnGymBuilder : GymBuilderStrings {

    /** 型 is a *form* — a shape you practise. "Routine" is the word the rest of the feature uses. */
    override val titleCreate = "New routine"
    override val titleEdit = "Edit routine"
    override val cancel = "Cancel"
    override val save = "Save"
    override val saving = "Saving"

    /** §3: a disabled word says *why*, not merely that it is disabled. */
    override val saveBlocked = "Save, needs a name and a station"

    override val fieldName = "Name"

    /** It warns; it does not block. So it states the fact and asks for nothing. */
    override val duplicateName = "Another routine has this name"

    /** 方式 is how the routine is run, not what kind of thing it is. */
    override val fieldEngine = "Format"
    override val selected = "Selected"
    override val fieldStations = "Stations"
    override val emptyStations = "Add a station"

    /** The Japanese uses a full-width ＋; Latin typography does not, so this is an ASCII plus. */
    override val addStation = "+ Add"
    override val stationCapReached = "No more can be added"
    override val unknownExercise = "Unknown exercise"
    override val loading = "Loading"

    override val actionMoveUp = "Move up"
    override val actionMoveDown = "Move down"
    override val actionEdit = "Edit"
    override val actionDelete = "Delete"
    override val dragging = "Moving"

    override fun reorderHandle(name: String) = "Reorder $name"
    override fun movedTo(position: String) = "Moved to $position"

    /**
     * `6 times already recorded, kept as they are`.
     *
     * The count leads, as it does in Japanese, because the count is the reassurance — the sentence
     * exists to tell someone with history that editing does not cost them any of it.
     */
    override fun historySafe(times: String) = "$times already recorded, kept as they are"

    override val discardTitle = "Discard this edit?"
    override val discardBody = "Unsaved changes will be lost."
    override val discardBack = "Back"

    override val noticeSingleStation = "Steps uses only the first station"
    override val noticeNoStationRest = "Every minute has no rest between stations"
    override val measureUnavailable = "Not available in this format"
}
