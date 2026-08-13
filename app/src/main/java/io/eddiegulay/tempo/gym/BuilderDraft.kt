package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.gym.data.StationShape
import io.eddiegulay.tempo.gym.data.structuralHashOf
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.stringsFor

/*
 * Everything `GYM.LIBRARY.BUILDER` and `GYM.LIBRARY.STATION_PICKER` decide, decided without a
 * composable — so the two pages can be thin over it and every rule below is answerable by a JVM test.
 *
 * Three of these carry more weight than their size suggests, and the file is arranged around them.
 *
 * 1. **[reorderShift] and [moveItem] are the drag-reorder library.** `CONTRIBUTING.md` forbids new
 *    dependencies, so `reorderable` is out and there will be no replacement for it: the page fixes its
 *    row height at 56.dp (`04-library-records.md` §3, edge case 1), translates each row by whole
 *    row-heights while a drag is live, and commits the list on drop. [reorderShift] answers the first
 *    half and [moveItem] the second, and the illusion holds only while they agree — which is a
 *    property a test can check exhaustively and an eye cannot.
 *
 * 2. **[isDirty] is the only input to the discard prompt.** `01-shell.md` §198 registers
 *    `BackHandler(enabled = isDirty)`, so a false negative is not a missing dialog — it is a back press
 *    that **silently discards the user's work**. It is therefore a comparison of *values*, never a
 *    "was anything touched" flag: a reorder dragged back to where it started and a name typed back to
 *    the original both read clean, because prompting on those trains the user to dismiss the prompt
 *    unread, after which the next one — the real one — is dismissed too.
 *
 * 3. **[structuralHash] is what makes an edit a new shape**, and it can be wrong in both directions.
 *    Too sensitive and every keystroke in the name field forks a routine version; too blunt and an
 *    April edit is not recorded as a version at all, so a March session — which pins its version id
 *    forever — is re-read against April's routine. That second failure is the one `00-plan.md` §2
 *    row 10 exists to close, and it is silent.
 *
 * Everything else here is a range or a table, and every number in one is `04-library-records.md` §3's.
 */

// ─── Reordering ─────────────────────────────────────────────────────────────────────────────────

/**
 * The list a drop produces: [from] lifted out and reinserted at [to].
 *
 * Out of range in either index returns the list **unchanged** rather than throwing. A drag can outlive
 * the row it started on — a rotation cancels it (§3, edge case 9), a fling produces an index past the
 * end before the caller clamps — and a station list that loses a row to an
 * `IndexOutOfBoundsException` is the reorder gesture deleting work. There is no correct partial answer
 * to "insert this somewhere that does not exist"; the list as it was is the only honest one.
 *
 * Generic because the builder reorders `StationDraft`s and the test reorders strings, and a list
 * operation that knows what it is holding is one more thing to mock.
 */
fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
    if (from == to) return list
    if (from !in list.indices || to !in list.indices) return list
    return list.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * How far the row at [index] must slide, in whole row-heights, while a drag from [from] to [to] is in
 * flight: `-1` up, `+1` down, `0` for everything the drag has not passed.
 *
 * The dragged row itself answers `0` **on purpose** — it is following the finger through
 * `translationY = dragOffsetPx`, and adding a layout shift to that would double its travel.
 *
 * The rule is one sentence in each direction: dragging down, every row from `from + 1` through `to`
 * moves up one to make room behind; dragging up, every row from `to` through `from − 1` moves down
 * one. It is deliberately **size-blind** — the signature `04-library-records.md` §3 edge case 1 names
 * is `reorderShift(i, from, to)`, and the caller is iterating the list it already has, so a `size`
 * parameter would be a fourth argument that only ever restates the loop bound.
 *
 * A negative index — of the row, or of either end of the drag — shifts nothing. Nothing is known about
 * a row that does not exist, and the page computes [to] from a pixel offset that goes negative on a
 * fast upward fling before it is clamped.
 */
fun reorderShift(index: Int, from: Int, to: Int): Int = when {
    index < 0 || from < 0 || to < 0 -> 0
    index == from -> 0
    from < to -> if (index in (from + 1)..to) -1 else 0
    from > to -> if (index in to until from) 1 else 0
    else -> 0
}

// ─── What counts as a change ────────────────────────────────────────────────────────────────────

/**
 * The content address of a draft's **shape** — everything a `routine_version` stores except its name.
 *
 * There are two structural hashes in this feature and they answer different questions. `gym/data`'s
 * `structuralHashOf` is the *storage* content address: it includes the name, because
 * `routine_version.name` is versioned so that renaming a routine does not rewrite what past sessions
 * say they did, and because it is what makes reseeding idempotent (`02-data.md` §B.3). This one is the
 * *builder's* question — "is this a different shape from the one I opened" — and `04-library-records.md`
 * §3 answers it name-blind, in the formula it writes out for the back handler:
 * `structuralHash(draft) != structuralHash(original) || draft.name != original.name`. The `||` is the
 * proof: a name-sensitive hash would make the second half of that line dead code.
 *
 * Keeping the name out is what lets the two surfaces that read this be right. [isDirty] adds the name
 * back itself, and §3's edge case 8 — 「これまでの六回の記録はそのまま残ります」, shown "when
 * `countForRoutine > 0` **and the structure is dirty**" — must *not* appear when the user has only
 * corrected a typo in the title.
 *
 * **It delegates rather than restating the canonical form**, with the name pinned to a constant. Two
 * independent field lists over the same record is how one of them ends up missing `prepareSeconds` for
 * a release: the store's hash and this one now cover exactly the same fields, by construction, and
 * both are order-sensitive because both build their string station by station.
 */
fun structuralHash(draft: RoutineDraft): Int = structuralHashOf(
    name = NAME_EXCLUDED,
    engine = draft.engine,
    rounds = draft.rounds,
    timeCapSeconds = draft.timeCapSeconds,
    intervalSeconds = draft.intervalSeconds,
    restBetweenStations = draft.restBetweenStations,
    restBetweenRounds = draft.restBetweenRounds,
    prepareSeconds = draft.prepareSeconds,
    // A user routine never attaches to a progression program — `FIXED_SETS` is the only engine that
    // can and `saveRoutine` writes null for every draft, so there is nothing here to vary.
    progressionProgramId = null,
    stations = draft.stations.map { StationShape(it.exerciseId, it.measure, it.reps, it.seconds, it.note) },
)

/** Stands in the name's slot so the shape hash cannot see it. Any constant would do; this one reads. */
private const val NAME_EXCLUDED = ""

/**
 * Whether the discard prompt is owed — `04-library-records.md` §3's formula, plus its sentence about
 * a routine that does not exist yet.
 *
 * [original] is null for 作る, where §3 says the draft is dirty "as soon as the name is non-blank or a
 * station exists". That is narrower than comparing against a pristine draft, and deliberately so: a
 * user who spun the 休息 wheel and pressed back has nothing to save — `canSave` is false with no name
 * and no stations — so the prompt would be offering to discard a routine that could never have
 * existed. A dialog with no stakes is how a dialog with stakes gets dismissed.
 *
 * Everything else is a value comparison, and it covers every field of [RoutineDraft] except
 * `routineId`. Identity is not content: `saveRoutine` may return an id different from the one it was
 * given — editing a built-in is a copy-on-write into a new user routine (`02-data.md` §A.0.3) — and
 * that is not an edit the user made.
 *
 * **Returning from `GYM.LIBRARY.STATION_PICKER` is not a special case and must not become one.** The
 * draft lives in `GymViewModel`, so it is the same object on the way back; if the picker changed a
 * station, the draft is genuinely dirty and the prompt is genuinely owed on the *next* back press,
 * which is §3's "forward-and-back within one edit" read correctly.
 */
fun isDirty(draft: RoutineDraft, original: RoutineDraft?): Boolean {
    if (original == null) return draft.name.isNotBlank() || draft.stations.isNotEmpty()
    return structuralHash(draft) != structuralHash(original) || draft.name != original.name
}

// ─── Saving ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The station list is capped at 24 (`04-library-records.md` §3, edge case 2): past that the estimate
 * line and the timeline compiler both stop being useful, and ＋ 加える greys out with
 * `これ以上は加えられません`.
 *
 * It is also what makes the page's plain `Column` inside a `verticalScroll` legitimate instead of a
 * `LazyColumn` — bounded content, hand-rolled drag, no viewport arithmetic.
 */
const val STATION_CAP = 24

/** Whether ＋ 加える is live. The copy for the refusal is the page's; the number is here. */
fun canAddStation(draft: RoutineDraft): Boolean = draft.stations.size < STATION_CAP

/**
 * Whether 保存 is live: `name.isNotBlank() && stations.isNotEmpty() && !saving && routineLoaded`,
 * `04-library-records.md` §3 verbatim.
 *
 * **Warnings are not in it, and no reading of design §6 puts them there** — "The builder warns, it does
 * not block." Neither is [STATION_CAP]: the cap is enforced where a station is *added*, so a draft can
 * only exceed it by having been loaded that way, and refusing to save a routine the user already owns
 * would strand it in the builder with no way out but 捨てる.
 *
 * @param saving true while the write is in flight — the 保存中 state. Double-tapping 保存 is otherwise
 *   two versions.
 * @param routineLoaded false while an edit is still reading its routine. Saving then would write a
 *   half-empty draft over a real routine, which is the one bug in this page that cannot be undone.
 */
fun canSave(draft: RoutineDraft, saving: Boolean = false, routineLoaded: Boolean = true): Boolean =
    draft.name.isNotBlank() && draft.stations.isNotEmpty() && !saving && routineLoaded

// ─── Changing the engine ────────────────────────────────────────────────────────────────────────

/**
 * A draft rewritten for a new engine, and the lines the page must show about what that cost.
 *
 * The notices are a list because §6 :1140 says "one per lossy field", and they are resolved strings
 * rather than an enum because both of them are §6's own copy and there are exactly two of them.
 */
data class DraftMigration(val draft: RoutineDraft, val notices: List<String>)

/** 巡回→時間内's default cap: 二十分 (`04-library-records.md` §3, edge case 5). */
const val DEFAULT_TIME_CAP_SECONDS = 20 * 60

/** 毎分 *is* the window. Sixty seconds is the engine's own name, not a number chosen for it. */
const val DEFAULT_INTERVAL_SECONDS = 60

/**
 * Changing the engine, honestly.
 *
 * `04-library-records.md` §3's edge case 5 is the whole specification of this function, and its last
 * sentence is the point: **"Nothing is silently dropped without a visible line saying so."** The three
 * documented migrations are implemented exactly as written —
 *
 * - **→時間内 (`AMRAP`)** replaces 巡数 with 制限時間 at [DEFAULT_TIME_CAP_SECONDS], keeping a cap the
 *   user had already set.
 * - **→段階 (`FIXED_SETS`)** clears the rests and the round count and uses one station, keeping the
 *   extras (the page greys them) and saying `段階では一種目だけ使われます`.
 * - **→毎分 (`EMOM`, `EMOM_ASCENDING`)** forces the between-station rest to zero and says
 *   `毎分では種目の間の休息はありません`.
 *
 * — and the rest of the fields follow the one rule those three imply: **a value no row can state is
 * not kept.** A cap outside `AMRAP`, an interval outside the minute engines and a round count on an
 * engine that has none (`engineRows` renders no 巡数 for `AMRAP`, `FOR_TIME` or `EMOM_ASCENDING`) would
 * otherwise ride along invisibly and reappear if the user switched back — a value the page never
 * showed, silently deciding a timeline.
 *
 * **Why 段階 clears rests that its own detail rows would show.** A `FIXED_SETS` routine takes its sets
 * *and their rests* from a progression program — `progression_step.rest_sec` per step (`02-data.md`
 * §F.2) — so a rest dialled on the builder's wheel is shadowed by the ladder rather than honoured.
 * §3's "clears rests and rounds" is not an inconsistency with the detail page's 巡の間の休息 row; it is
 * the difference between what a program prescribes and what a builder can offer.
 *
 * **Two notices exist and no third is invented.** Dropping a cap or an interval has no documented
 * string, and none is written here: `00-plan.md` forbids inventing copy, and those two losses are the
 * only ones the page shows on its own — the 制限時間 row is *there* and then is *gone*, in place,
 * under the finger that changed the engine. The station notice and the rest notice are needed because
 * neither loss is visible: the extra stations stay on screen looking usable, and a rest forced to zero
 * looks like a rest the user set to zero.
 *
 * Notices fire **only when something is actually lost** — a one-station draft moving to 段階 and a
 * rest-free draft moving to 毎分 say nothing. A notice that always appears is chrome, and chrome is
 * what teaches the user not to read the notices that mean something.
 *
 * Migrating to the engine the draft already has returns it untouched, so an idempotent re-selection
 * (the picker unfolds in place and a tap on the current chip is the obvious way to close it) cannot
 * reset a cap the user has since edited.
 *
 * @param strings taken as a parameter rather than read from a Compose local: this is domain code, and
 *   every rule above is tested on the JVM with no `Context` and no composition.
 */
fun migrateDraft(draft: RoutineDraft, newEngine: Engine, strings: Strings): DraftMigration {
    if (newEngine == draft.engine) return DraftMigration(draft, emptyList())

    val minutely = newEngine == Engine.EMOM || newEngine == Engine.EMOM_ASCENDING
    val stepped = newEngine == Engine.FIXED_SETS
    val notices = buildList {
        if (stepped && draft.stations.size > 1) add(strings.gymBuilder.noticeSingleStation)
        if (minutely && draft.restBetweenStations > 0) add(strings.gymBuilder.noticeNoStationRest)
    }

    val migrated = draft.copy(
        engine = newEngine,
        rounds = when (newEngine) {
            // 時間内 answers 巡数 with 時間内で, 毎分増 runs until failure, and 段階's sets come from
            // its program. None of the three has a round count to carry.
            Engine.AMRAP, Engine.EMOM_ASCENDING, Engine.FIXED_SETS -> null
            // One is the floor of §3's 巡数 wheel (1..20) and the honest reading of "not set yet".
            Engine.INTERVAL_CIRCUIT, Engine.FOR_TIME, Engine.FOR_TIME_WITH_REST, Engine.EMOM ->
                draft.rounds ?: 1
        },
        timeCapSeconds = if (newEngine == Engine.AMRAP) {
            draft.timeCapSeconds ?: DEFAULT_TIME_CAP_SECONDS
        } else {
            null
        },
        intervalSeconds = if (minutely) draft.intervalSeconds ?: DEFAULT_INTERVAL_SECONDS else null,
        restBetweenStations = if (minutely || stepped) 0 else draft.restBetweenStations,
        restBetweenRounds = if (stepped) 0 else draft.restBetweenRounds,
    )
    return DraftMigration(migrated, notices)
}

// ─── The station picker ─────────────────────────────────────────────────────────────────────────

/**
 * One はかり方 chip: which measure, whether it can be chosen here, and — when it cannot — why.
 *
 * The reason travels with the chip because §3's accessibility line requires it to:
 * `"秒数、この方式では使えません"` is the chip's whole content description. A disabled control that
 * cannot explain itself is `04-library-records.md`'s recurring complaint about greyed words.
 */
data class MeasureOption(val measure: Measure, val enabled: Boolean, val reason: String?)

/**
 * The three はかり方 chips for an engine, in 回数 / 秒数 / 限界まで order (§6's measures row, and the
 * picker mock).
 *
 * Two engines refuse a measure, and `04-library-records.md` §3's picker edge cases say why:
 *
 * - **限界まで is unavailable on 毎分 and 毎分増** (edge case 2) — those engines are *defined* by a
 *   fixed rep count inside a fixed window, so an open-ended set has no window to end in.
 * - **秒数 is unavailable on 完走** (edge case 3) — the score is total reps against the clock, so a
 *   station that ends on a timer is not part of the thing being measured.
 *
 * Both render greyed **with the reason**, rather than disappearing. That is the opposite of the detail
 * page's rule for 編集 on a built-in (absent entirely, not greyed) and the difference is real: an
 * action that will never be available is chrome, but a chip that is available under a *different
 * engine the user can select on the previous page* is information about this engine.
 *
 * **完走 keeps 限界まで**, and that is not an oversight: マーフ's two one-mile runs are `MAX_EFFORT`
 * stations with a `一マイル` note, because a `DURATION` with a null second count is refused by the
 * schema's CHECK (`DECISIONS.md` §Q15). Disabling it here would make a shipped built-in unbuildable in
 * the builder.
 *
 * *Rejected* — extending 秒数's refusal to `FOR_TIME_WITH_REST` on the grounds that バーバラ is scored
 * the same way. §3 names `FOR_TIME` and only `FOR_TIME_WITH_REST` is a rest-carrying circuit shape;
 * widening a refusal is not a transcription, and the cost of being wrong is a measure the user cannot
 * pick with no way to find out why.
 */
fun allowedMeasures(engine: Engine, strings: Strings): List<MeasureOption> = Measure.entries.map { measure ->
    val enabled = measureAllowed(engine, measure)
    MeasureOption(measure, enabled, if (enabled) null else strings.gymBuilder.measureUnavailable)
}

/**
 * The predicate on its own, without the sentence that explains a refusal.
 *
 * Split out because two callers want different halves. The picker draws chips and needs the reason;
 * `seededMeasure` only needs to know whether a stored measure is still legal, and threading a string
 * table through a question that has no words in it would make a page's copy a parameter of its
 * *correctness* — which is how a pure predicate acquires a `Strings` it never reads.
 */
fun measureAllowed(engine: Engine, measure: Measure): Boolean = when (measure) {
    Measure.REPS -> true
    Measure.DURATION -> engine != Engine.FOR_TIME
    Measure.MAX_EFFORT -> engine != Engine.EMOM && engine != Engine.EMOM_ASCENDING
}

/** What a station asks for, as the picker holds it before it becomes a [StationDraft]. */
data class Prescription(val reps: Int?, val seconds: Int?)

/** 二十回 (`04-library-records.md` §3, picker edge case 1). */
const val DEFAULT_REPS = 20

/** 三十秒 (same line). */
const val DEFAULT_SECONDS = 30

/**
 * The value a はかり方 chip lands on: the last one this measure held, or its default.
 *
 * `04-library-records.md` §3's picker edge case 1 is one sentence and both halves are load-bearing:
 * "Switching 回数 → 秒数 does **not** carry the number across (20 reps ≠ 20 seconds). Each type
 * remembers its own last value within the session; defaults 二十回 / 三十秒."
 *
 * So the memory is **per measure**, which is why there are two remembered parameters rather than one
 * value being reinterpreted. A single carried number is the bug the sentence was written against: a
 * twenty-rep push-up station switched to 秒数 becomes a twenty-second one, which is a different
 * exercise prescription that looks like it was chosen.
 *
 * `MAX_EFFORT` carries neither, and the schema's CHECK agrees — 限界まで with a rep count is a
 * contradiction the store refuses.
 *
 * "Within the session" is the caller's scope to keep: this function is handed what was remembered and
 * has no opinion about how long that lasts. Nothing here is persisted; the picker writes to the draft
 * and the draft writes on 保存 only.
 */
fun defaultPrescription(
    measure: Measure,
    lastReps: Int? = null,
    lastSeconds: Int? = null,
): Prescription = when (measure) {
    Measure.REPS -> Prescription(reps = lastReps ?: DEFAULT_REPS, seconds = null)
    Measure.DURATION -> Prescription(reps = null, seconds = lastSeconds ?: DEFAULT_SECONDS)
    Measure.MAX_EFFORT -> Prescription(reps = null, seconds = null)
}

// ─── The wheels ─────────────────────────────────────────────────────────────────────────────────

/**
 * One row of a `TempoValueWheel`: the value it sets and the words it shows.
 *
 * A pair rather than a bare `List<Int>` plus a formatter because the wheel needs both and the two must
 * not be derived independently — `TempoValueWheel(values, selected, onSelect, label)` seeds its
 * position with `values.indexOf(selected)`, which is `−1` for a value the list does not contain, and
 * the wheel then opens on its first row instead. A default that is not on its own wheel silently
 * becomes 一回.
 */
data class WheelOption(val value: Int, val label: String)

/** Which rest is being set. The two wheels have different ranges and different steps (§3, edge 11). */
enum class RestSlot { BETWEEN_STATIONS, BETWEEN_ROUNDS }

/**
 * A rest wheel: `0,5,…,120` between stations, `0,15,…,300` between rounds, `なし` at zero.
 *
 * The ranges and their steps are `04-library-records.md` §3's edge case 11 verbatim. The steps differ
 * because the two rests are set for different reasons — a between-station rest is a pacing decision
 * measured in a few seconds, and a between-round rest is a recovery decision measured in half-minutes;
 * one shared wheel would need 61 rows to serve both.
 *
 * **The labels are bare seconds**, and `DECISIONS.md` §Q10 is what makes that binding: a duration the
 * user *chose* renders as the value they chose. Sixty is 六十秒 here, on the settings row, and on
 * `GYM.LIBRARY.DETAIL` — `durationKanji` would render it 一分 and the user would read back a value
 * they never picked.
 *
 * **Kanji, not arabic.** §6's numerals note excepts "the wheel mid-spin", but §3's builder mock draws
 * this very wheel as 十秒 / 十五秒 / 二十秒 and §Q10 requires the wheel and the row that echoes it to
 * agree — which they cannot if one is 60 and the other 六十秒. The mock and §Q10 outrank a general
 * note; a mid-spin rendering that differs from the resting one is a change of *presentation* the page
 * can still make, and it is the page's to make.
 *
 * **The values do not vary by language and must not.** `DECISIONS.md` §Q21 is a rule about the *value*
 * set — a wheel opened on a value it cannot represent silently rewrites it — so only the labels are
 * resolved here. `mergedWheelOptions` compares on `value`, and a range that differed by language would
 * make the merge itself language-dependent.
 */
fun restOptions(slot: RestSlot, strings: Strings): List<WheelOption> = when (slot) {
    RestSlot.BETWEEN_STATIONS -> STATION_REST_OPTIONS.getValue(strings.lang)
    RestSlot.BETWEEN_ROUNDS -> ROUND_REST_OPTIONS.getValue(strings.lang)
}

/** 回数's wheel: `1..100`, counted in 回 (§3, edge case 11). */
fun repOptions(strings: Strings): List<WheelOption> = REP_OPTIONS.getValue(strings.lang)

/**
 * 秒数's wheel: `5,10,…,300` (§3, edge case 11).
 *
 * It starts at five rather than at zero, and has no `なし`: a zero-second station is not a station, and
 * an open-ended one is 限界まで — which is a *measure*, chosen on the chips above the wheel, not a
 * value on it. Same chosen-duration rule as [restOptions] (`DECISIONS.md` §Q10).
 */
fun secondOptions(strings: Strings): List<WheelOption> = SECOND_OPTIONS.getValue(strings.lang)

/**
 * 巡数's wheel: `1..20`, counted in 巡 (§3, edge case 11).
 *
 * Not in `04-library-records.md` §7's function list, which names the other three — an omission rather
 * than a decision, since §3 specifies the range beside them and the builder draws the row in its mock.
 * Zero rounds is not a routine, so the wheel starts at one.
 */
fun roundOptions(strings: Strings): List<WheelOption> = ROUND_OPTIONS.getValue(strings.lang)

/**
 * The four wheel labels, in one block because `DECISIONS.md` §Q10 is one rule.
 *
 * **These used to be eight functions.** `BuilderScreen` carried a second copy of all four so that
 * `mergedWheelOptions` could label the one row these lists do not contain, and both KDocs recorded the
 * duplication as deliberate — the two files belonged to two units, so §Q10 was enforced by twin tests
 * rather than by the compiler. One unit now owns both, and the note both copies carried said what to
 * do about it: **unify onto §Q10's sentence, never onto `durationKanji`.** That is what this is.
 *
 * A count renders with its counter, and a duration the user **chose** renders as the value they chose —
 * bare seconds in Japanese, `なし` at zero (§6 :1141's own word; 〇秒 would be a prescription of
 * nothing). The zero word is `gymShared.restNone` rather than this namespace's own, because the
 * row *above* a rest wheel is `engineRows`' string and the rows inside it are these — two entries
 * saying "None" and "No rest" is precisely how that row comes to contradict itself.
 */
fun restWheelValueLabel(seconds: Int, strings: Strings): String =
    if (seconds <= 0) strings.gymShared.restNone else setSecondsLabel(seconds, strings)

/** 三十秒 — 秒数's wheel has no zero (a zero-second station is not a station), so no なし branch. */
fun secondWheelValueLabel(seconds: Int, strings: Strings): String = setSecondsLabel(seconds, strings)

/** 二百回 — 回数's counter. */
fun repWheelValueLabel(reps: Int, strings: Strings): String = strings.fmt.reps(reps)

/** 三十巡 — 巡数's counter. */
fun roundWheelValueLabel(rounds: Int, strings: Strings): String = strings.fmt.rounds(rounds)

/**
 * A number of seconds the user **set**, as against one the app measured.
 *
 * **In English the distinction is deleted rather than translated** (`.planning/i18n/DECISIONS.md` §L7).
 * §Q10 is carried entirely by orthography — 六十秒 against 一分三十秒, two ways of spelling the same
 * sixty — and English has one orthography, so there is nowhere for the second form to go. The English
 * arm therefore renders through the same `fmt.duration` a measured value uses: sixty is `1m` on this
 * wheel exactly as it is on a results line. Keeping `fmt.seconds` would preserve the *shape* of a rule
 * whose content is gone, and would print `300s` at the bottom of a rest wheel.
 *
 * This is `GymSettingsCopy.settingsSecondsLabel`'s ruling, applied to the wheel that page's own rows
 * are built from — the two must agree, because that page dials this list.
 */
fun setSecondsLabel(seconds: Int, strings: Strings): String =
    if (strings.lang == Lang.Ja) strings.fmt.seconds(seconds) else strings.fmt.duration(seconds)

/*
 * Built once per language, not once per frame. The builder recomposes on every keystroke of the name
 * field, and rebuilding a hundred labels per frame to hand a wheel a list it already has is the kind
 * of waste that shows up as a dropped frame on a drag rather than as a number anybody measures.
 *
 * Both languages are built at class-init and the lookup is a map read, matching `prepareOptions`. The
 * alternative — caching the last one — rebuilds on every flip of the toggle and is more code for less.
 */
private val STATION_REST_OPTIONS = wheelPerLanguage(0..120, 5, ::restWheelValueLabel)
private val ROUND_REST_OPTIONS = wheelPerLanguage(0..300, 15, ::restWheelValueLabel)
private val REP_OPTIONS = wheelPerLanguage(1..100, 1, ::repWheelValueLabel)
private val SECOND_OPTIONS = wheelPerLanguage(5..300, 5, ::secondWheelValueLabel)
private val ROUND_OPTIONS = wheelPerLanguage(1..20, 1, ::roundWheelValueLabel)

private fun wheelPerLanguage(
    range: IntRange,
    step: Int,
    label: (Int, Strings) -> String,
): Map<Lang, List<WheelOption>> = Lang.entries.associateWith { lang ->
    val strings = stringsFor(lang)
    (range.first..range.last step step).map { WheelOption(it, label(it, strings)) }
}
