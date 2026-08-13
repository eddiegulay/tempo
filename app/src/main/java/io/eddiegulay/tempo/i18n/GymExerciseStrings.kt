package io.eddiegulay.tempo.i18n

/**
 * The exercise index, the exercise detail page and the builder's station picker.
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
 * **The picker files under this namespace rather than the builder's**, following the survey: it edits
 * one station of the builder's draft and never touches the database, but every word on it is about a
 * *movement* — the same rows, the same 難度, the same 該当する種目はありません — and splitting them
 * across two tables would have put one sentence in two places.
 *
 * **Why several members are functions.** A member that interpolates is a whole composed string and not
 * a fragment to be glued at the call site: 「難度 一.〇」 is a prefix and so is "Difficulty 1.0", but
 * 「二.〇秒/回」 is a suffix and "2.0s/rep" is a suffix of a different shape. The numeral itself always
 * arrives already formatted, from [Formats] — this table never sees an `Int`. Where the composition is
 * a genuine *list* (a row's TalkBack sentence, a chip and its reason) the call site joins with
 * [Formats.listSeparator] instead, which is what the em-dash-free 、 → `, ` swap is for.
 */
interface GymExerciseStrings {

    // ─── Shared by all three pages ──────────────────────────────────────────────────────────────

    /**
     * 難度 一.〇 — a movement's difficulty coefficient, labelled.
     *
     * One member for four call sites (the index card's description, the detail subtitle, a ladder
     * rung's sentence, the picker row's sentence) because §3 writes the fragment once and the station
     * picker's documented sentence is the one the other surfaces borrow.
     *
     * @param coefficient already through [Formats.coefficient], which renders 走る's absent value as
     *   [Formats.noValue]. 「難度 —」 is a real and intended output; see [io.eddiegulay.tempo.ui.gym]'s
     *   `RUN_ID` for why that hole is not filled.
     */
    fun difficulty(coefficient: String): String

    /** さがす — the search field's placeholder and its own content description. */
    val searchPlaceholder: String

    /**
     * 該当する種目はありません — a search that matched nothing, on the index and on the picker.
     *
     * **Never 型はまだありません's shape.** That sentence means "you have no routines yet"; this one is
     * a statement about the query and is reachable only with something typed. Both pages flag the trap
     * in bold, and one shared "nothing here" string is how the two come to be one branch apart.
     */
    val noMatch: String

    // ─── GYM.LIBRARY.EXERCISE_INDEX ─────────────────────────────────────────────────────────────

    /** 種目 — the page title. The catalogue of movements, not a routine's stations. */
    val indexTitle: String

    /**
     * 十六の動き — the subtitle, counting the **catalogue** and never the matches.
     *
     * @param count already through [Formats.count]. The singular is unreachable: this counts the whole
     *   seeded catalogue, which is twenty-three rows.
     */
    fun indexSubtitle(count: String): String

    /**
     * 二.〇秒/回 — the meta line's pace fragment.
     *
     * @param coefficient seconds per rep, through [Formats.coefficient]. Only ever called for a
     *   movement that counts reps; a hold's `secondsPerRep` is a volume conversion and the caller
     *   suppresses the whole fragment rather than mislabelling it.
     */
    fun pace(coefficient: String): String

    /**
     * 最高 三十二回 — the card's personal-best fragment.
     *
     * @param reps already through [Formats.reps]. Never called with a zero: §3 edge case 4 is "no
     *   history → no 最高 fragment, **not** 最高 —", so the absence is expressed by not calling this.
     */
    fun bestReps(reps: String): String

    /** 探す — the header action that opens the search row. The word states what the tap does. */
    val openSearch: String

    /** とじる — the same header action once the row is open. Closes the search, not the page. */
    val closeSearch: String

    // ─── GYM.LIBRARY.EXERCISE_DETAIL ────────────────────────────────────────────────────────────

    /** とじる — this page's own exit, in the header. Distinct from [closeSearch]: it pops the page. */
    val close: String

    /** 最高 — the section of personal bests. */
    val sectionBests: String

    /** 段階 — the progression ladder. Same word as `gymShared.engineFixedSets`, same rungs. */
    val sectionLadder: String

    /** 使われている型 — which routines contain this movement. */
    val sectionUsedBy: String

    /** 一度に — the best single set. §3 edge case 4: only sets whose `actual_reps` was recorded. */
    val tileSingleSet: String

    /** のべ回数 — every rep of this movement ever recorded. */
    val tileLifetime: String

    /** 最後 — the day it was last performed. */
    val tileLast: String

    /** 読み込み中 — the two store-backed blocks only. The catalogue never loads; it is a map. */
    val loading: String

    /** まだ やっていません — this movement has no recorded set. Reachable from `Ready` and nowhere else. */
    val noHistory: String

    /** どの型にも入っていません — no routine uses this movement. Also `Ready`-only, for the same reason. */
    val noRoutines: String

    /** いま — the accent word beside the rung the user is standing on. The block's only accent. */
    val ladderCurrent: String

    /**
     * いまここ — a ladder rung's TalkBack sentence ends on this when it is the current one.
     *
     * @see rungNotReached — there are exactly two forms, because §3 writes exactly two.
     */
    val rungCurrent: String

    /**
     * まだ — every other rung, **including one already climbed past**.
     *
     * The word answers "am I standing here". A third word for "climbed but not current" would be copy
     * no table carries, which is why a reached-but-lower rung reads まだ like the rest.
     */
    val rungNotReached: String

    // ─── GYM.LIBRARY.STATION_PICKER ─────────────────────────────────────────────────────────────

    /** 種目をえらぶ — the picker's title. */
    val pickerTitle: String

    /** 保存 — writes the station into the draft. The routine's own save is the builder's. */
    val save: String

    /**
     * 種目をえらんでください — why 保存 is inert, joined to it by [Formats.listSeparator].
     *
     * §3's rule that a disabled word says why. Joined rather than baked into one sentence so the
     * comma is the locale's and the two halves stay separately readable.
     */
    val savePickFirst: String

    /** やめる — leave without applying. No discard prompt: one station is too small a unit for one. */
    val cancel: String

    /**
     * はかり方 — the label over the 回数 / 秒数 / 限界まで chips.
     *
     * The chips' own words are `gymShared.measure*` and the sentence explaining a greyed one is
     * `gymBuilder.measureUnavailable`, which arrives on `MeasureOption.reason` — this namespace holds
     * only the row's label, so one refusal sentence has one owner.
     */
    val measureLabel: String

    /**
     * 目安 四十秒 — how long this station is expected to take.
     *
     * **Labelled every single time it appears** (§3, picker edge case 5): it advances nothing, and the
     * label is the only thing that says so. Absent entirely for 限界まで, where there is nothing to
     * estimate and a number would be the app deciding what "to your limit" means.
     *
     * @param duration already through [Formats.duration] — a duration the app *computed*, which is the
     *   half of §Q10 that spells itself out in Japanese.
     */
    fun paceEstimate(duration: String): String

    /** できるところまで — what stands where the wheel would be, on a 限界まで station. */
    val openEnded: String

    /** 選択中 — a chosen row or chip, as `stateDescription`. Never drawn. */
    val selected: String

    /** 削除 — take this station out of the routine. Edit mode only. */
    val remove: String
}

internal object JaGymExercise : GymExerciseStrings {

    override fun difficulty(coefficient: String): String = "難度 " + coefficient
    override val searchPlaceholder = "さがす"
    override val noMatch = "該当する種目はありません"

    override val indexTitle = "種目"
    override fun indexSubtitle(count: String): String = count + "の動き"
    override fun pace(coefficient: String): String = coefficient + "秒/回"
    override fun bestReps(reps: String): String = "最高 " + reps
    override val openSearch = "探す"
    override val closeSearch = "とじる"

    override val close = "とじる"
    override val sectionBests = "最高"
    override val sectionLadder = "段階"
    override val sectionUsedBy = "使われている型"
    override val tileSingleSet = "一度に"
    override val tileLifetime = "のべ回数"
    override val tileLast = "最後"
    override val loading = "読み込み中"
    override val noHistory = "まだ やっていません"
    override val noRoutines = "どの型にも入っていません"
    override val ladderCurrent = "いま"
    override val rungCurrent = "いまここ"
    override val rungNotReached = "まだ"

    override val pickerTitle = "種目をえらぶ"
    override val save = "保存"
    override val savePickFirst = "種目をえらんでください"
    override val cancel = "やめる"
    override val measureLabel = "はかり方"
    override fun paceEstimate(duration: String): String = "目安 " + duration
    override val openEnded = "できるところまで"
    override val selected = "選択中"
    override val remove = "削除"
}

/**
 * English.
 *
 * Three judgements run through the words below and are worth stating once.
 *
 * **The catalogue's own names are not here.** 腕立て伏せ / "Push-up" comes off the `exercise` row's
 * second column through `Exercise.displayName`, and the cue through `Exercise.displayCue`. This table
 * holds the chrome around them and nothing the seeds already carry.
 *
 * **Length is functional.** The meta line is one ellipsised line beside a best fragment, the three
 * 最高 tiles are thirds of the screen at 11.sp, and the はかり方 label shares a `SpaceBetween` row with
 * three chips. That is why the pace fragment is `2.0s/rep` and not `2.0 seconds per repetition`.
 *
 * **The register stays flat.** 「まだ やっていません」 is a statement, not an encouragement; "Not done
 * yet" keeps that and "You haven't tried this one yet!" would be a different app.
 */
internal object EnGymExercise : GymExerciseStrings {

    override fun difficulty(coefficient: String): String = "Difficulty $coefficient"
    override val searchPlaceholder = "Search"
    override val noMatch = "No matching exercise"

    /** 種目 is the catalogue's name for its rows. The subtitle counts 動き, and keeps its own word. */
    override val indexTitle = "Exercises"
    override fun indexSubtitle(count: String): String = "$count movements"

    /** Seconds per repetition, in the two characters a meta line can spare. */
    override fun pace(coefficient: String): String = "${coefficient}s/rep"

    override fun bestReps(reps: String): String = "Best $reps"

    /** The verb, matching 探す — the word says what the tap does, not what is open. */
    override val openSearch = "Search"
    override val closeSearch = "Close"

    override val close = "Close"
    override val sectionBests = "Best"

    /** 段階 is a ladder of rungs, and `gymShared.engineFixedSets` already calls each one a step. */
    override val sectionLadder = "Steps"

    /** 型 is a routine. The count beside this heading names them, so the heading need not. */
    override val sectionUsedBy = "Used in"

    override val tileSingleSet = "In one set"

    /** のべ is cumulative-over-all-time, which "Total" alone would leave open to reading as today's. */
    override val tileLifetime = "Lifetime"
    override val tileLast = "Last"

    override val loading = "Loading"

    /** A statement about the record, not about the user. 「まだ」 is "not yet" and stays that. */
    override val noHistory = "Not done yet"
    override val noRoutines = "Not in any routine"

    override val ladderCurrent = "Now"
    override val rungCurrent = "you are here"
    override val rungNotReached = "not yet"

    override val pickerTitle = "Choose an exercise"
    override val save = "Save"
    override val savePickFirst = "choose an exercise first"
    override val cancel = "Cancel"

    /** はかり方 is *how this is measured*, and the chips below it are the answer. */
    override val measureLabel = "Measure"

    /** 目安 is a rough guide. "About" is the one word that says estimate without saying target. */
    override fun paceEstimate(duration: String): String = "About $duration"

    /** できるところまで — as far as you can, which is a prescription and not encouragement. */
    override val openEnded = "As far as you can"

    override val selected = "Selected"

    /** The station leaves the routine; nothing is deleted from the catalogue or the database. */
    override val remove = "Remove"
}
