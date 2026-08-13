package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.Strings

/*
 * What `GYM.LIBRARY.DETAIL` says about a routine's shape, and it is different for every engine.
 *
 * The whole file exists because of one sentence in `04-library-records.md` §3: "AMRAP / FOR_TIME have
 * no meaningful 巡数". A detail page that renders the same four rows for all seven engines prints
 * 巡数 一巡 for a マーフ and 制限時間 なし for a 七分間 — rows that are not wrong so much as *not about
 * anything*, and a read-only page whose rows are noise teaches the user to stop reading it.
 *
 * The decision is a table rather than a chain of `if (rounds != null)` checks because the interesting
 * cases are the *absences*, and an absence has no field to test. FOR_TIME's rounds column is
 * populated — マーフ is one round — and the row still must not appear.
 */

/**
 * Which row this is, independently of what it says.
 *
 * **The discriminator exists because a caller dispatches on these rows**, and until the labels moved
 * into the string table the only thing it could dispatch on was the Japanese text. `BuilderScreen` is
 * that caller: it turns 種目の間の休息, 巡の間の休息 and 巡数 into wheels and everything else into a
 * read-only line. Matching on [EngineRow.label] worked exactly as long as there was one language, and
 * would have failed **silently** in the other — three settings quietly becoming read-only, with no
 * compile error and no failing test.
 *
 * Nothing in this file needs it. It is here so that dispatching on the words is never necessary again.
 */
enum class EngineRowKind { TIME_CAP, REST_BETWEEN_STATIONS, REST_BETWEEN_ROUNDS, ROUNDS }

/** One read-only `PickerRow`-shaped line: 制限時間 / 二十分. */
data class EngineRow(val kind: EngineRowKind, val label: String, val value: String)

/**
 * The rows a routine's engine earns, in the order `04-library-records.md` §3's シンディ mock draws
 * them: 制限時間 → 種目の間の休息 → 巡の間の休息 → 巡数.
 *
 * | engine | rows | why |
 * |---|---|---|
 * | `INTERVAL_CIRCUIT` | both rests, 巡数 | the shape *is* work/rest/repeat |
 * | `AMRAP` | 制限時間, 種目の間の休息, 巡数 = 時間内で | the mock, verbatim; §6 marks 制限時間 AMRAP-only |
 * | `FOR_TIME` | none | §3 edge case 4: no meaningful 巡数, and マーフ has no prescribed rest |
 * | `FOR_TIME_WITH_REST` | 巡の間の休息, 巡数 | バーバラ's rest is *mandated* and is the whole difference from `FOR_TIME` |
 * | `EMOM` | 巡数 | §3's migration notice says it outright: 毎分では種目の間の休息はありません |
 * | `EMOM_ASCENDING` | none | it runs until failure — there is no round count to state |
 * | `FIXED_SETS` | 巡の間の休息, 巡数 | one station by construction (段階では一種目だけ使われます), so a between-station rest cannot exist |
 *
 * **A zero rest still gets its row**, reading なし. The mock proves it: シンディ prescribes no rest
 * between stations and the page says 種目の間の休息 / なし rather than dropping the line. "No rest" is a
 * prescription; a missing row is a question.
 *
 * **The one thing an EMOM cannot say is its window.** `intervalSeconds` is on the projection and this
 * function is handed it, but no spec table carries a label for it — 毎分 is the *engine*'s name, not a
 * row's — and inventing 間隔 or 一巡の長さ is not permitted. A 毎分 routine with a non-60-second window
 * therefore renders no line about it. Flagged rather than papered over; it needs one string.
 *
 * @param strings taken as a parameter rather than read from a Compose local: this is domain code, and
 *   `04-library-records.md`'s row table is tested on the JVM with no `Context` and no composition.
 */
fun engineRows(
    engine: Engine,
    rounds: Int?,
    timeCapSeconds: Int?,
    restBetweenStations: Int,
    restBetweenRounds: Int,
    strings: Strings,
): List<EngineRow> = buildList {
    if (engine == Engine.AMRAP && timeCapSeconds != null) {
        add(
            EngineRow(
                EngineRowKind.TIME_CAP,
                strings.gymShared.rowTimeCap,
                strings.fmt.duration(timeCapSeconds),
            ),
        )
    }
    if (engine == Engine.INTERVAL_CIRCUIT || engine == Engine.AMRAP) {
        add(
            EngineRow(
                EngineRowKind.REST_BETWEEN_STATIONS,
                strings.gymShared.rowRestBetweenStations,
                restLabel(restBetweenStations, strings),
            ),
        )
    }
    if (engine == Engine.INTERVAL_CIRCUIT ||
        engine == Engine.FOR_TIME_WITH_REST ||
        engine == Engine.FIXED_SETS
    ) {
        add(
            EngineRow(
                EngineRowKind.REST_BETWEEN_ROUNDS,
                strings.gymShared.rowRestBetweenRounds,
                restLabel(restBetweenRounds, strings),
            ),
        )
    }
    when (engine) {
        // 時間内で in the 巡数 slot: the answer to "how many rounds" is genuinely "as many as fit",
        // and §6 gives that exact phrase for exactly this slot.
        Engine.AMRAP ->
            add(EngineRow(EngineRowKind.ROUNDS, strings.gymShared.rowRounds, strings.gymShared.roundsWithinCap))
        Engine.INTERVAL_CIRCUIT, Engine.FOR_TIME_WITH_REST, Engine.EMOM, Engine.FIXED_SETS ->
            if (rounds != null) {
                add(EngineRow(EngineRowKind.ROUNDS, strings.gymShared.rowRounds, strings.fmt.rounds(rounds)))
            }
        Engine.FOR_TIME, Engine.EMOM_ASCENDING -> Unit
    }
}

/** The detail page's own projection, so a call site does not have to unpack five fields in order. */
fun engineRows(snapshot: RoutineSnapshot, strings: Strings): List<EngineRow> = engineRows(
    engine = snapshot.engine,
    rounds = snapshot.rounds,
    timeCapSeconds = snapshot.timeCapSeconds,
    restBetweenStations = snapshot.restBetweenStations,
    restBetweenRounds = snapshot.restBetweenRounds,
    strings = strings,
)

/** The list projection, for the same reason. */
fun engineRows(summary: RoutineSummary, strings: Strings): List<EngineRow> = engineRows(
    engine = summary.engine,
    rounds = summary.rounds,
    timeCapSeconds = summary.timeCapSeconds,
    restBetweenStations = summary.restBetweenStations,
    restBetweenRounds = summary.restBetweenRounds,
    strings = strings,
)

/**
 * A rest, in **the unit the user set it in**: `六十秒`, `九十秒`, `なし` at zero.
 *
 * `DECISIONS.md` §Q10 states the rule once, and it is a rule about *acts*, not about durations: a
 * duration the user **chose** renders as the value they chose — bare seconds via
 * `Formats.seconds` — while a duration the app **measured** renders through `Formats.duration`
 * (一分三十秒), which is `01-focus-settings.md` :826's `secondsLabelJa` contract and stays untouched.
 *
 * Rests are set. The builder wheel (`04-library-records.md` :350) and the settings row
 * (`01-focus-settings.md` :735) both spell sixty seconds 六十秒, so a detail page that echoed the same
 * stored value back as 一分 would be telling the user their routine holds a value they never picked.
 * The 制限時間 row above is left on `Formats.duration` deliberately: §3's mock prints a twenty-minute cap
 * as 二十分, and a cap is read as a span of the clock rather than counted out.
 *
 * なし at zero is the builder's own word for it (§6), not 〇秒 — "no rest" is a prescription.
 */
/**
 * A rest the user **chose**, as the row that reports it prints it.
 *
 * §Q10's chosen-vs-measured split, and §L7's ruling on what happens to it. Japanese keeps the split:
 * a duration you set renders bare (六十秒), one the app measured is spelled out. English has no second
 * orthography to carry that, so both collapse onto `fmt.duration` — which also keeps this row
 * agreeing with the wheel that sets it. Before this, the row said `60s` while the wheel said `1m`.
 */
private fun restLabel(seconds: Int, strings: Strings): String = when {
    seconds <= 0 -> strings.gymShared.restNone
    strings.lang == Lang.Ja -> strings.fmt.seconds(seconds)
    else -> strings.fmt.duration(seconds)
}

/** One 最高 tile: a value over a label, read **label-first** by TalkBack (§3, accessibility). */
data class BestTile(val label: String, val value: String)

/**
 * The 最高 tiles for a routine, which are engine-dependent because what counts as "best" is.
 *
 * The metric assignment is `04-library-records.md` §4's edge case 2, not a guess: FOR_TIME* is scored
 * on the clock (最速), AMRAP on rounds then reps (最高巡数 + 最高反復 — two tiles, the only engine with
 * two), EMOM_ASCENDING on rounds survived, and the fixed-shape engines on weighted volume (最高負荷,
 * deliberately unitless, because duration stations enter it through an approximation).
 *
 * The third tile, やった回数, is **engine-independent and always sourced**: it is `timesDone`, it needs
 * no `personal_record` row to exist, and it is the number the 削除 confirm branches on. It is therefore
 * emitted whenever `timesDone > 0` even if no metric tile resolves.
 *
 * **This function fails open, and `DECISIONS.md` §Q9 makes that binding.** It used to return an empty
 * list whenever no metric tile resolved, which swallowed the count too — so a routine whose stored
 * bests carried a metric this engine does not score showed a user with fifty sessions exactly what a
 * user with zero saw. That is the `Loadable` doctrine failing in the direction `00-plan.md` §2 row 15
 * exists to prevent. The real `NoAttempts` condition is `timesDone <= 0 && bests.isEmpty()`: nothing
 * done and nothing recorded. A row of 〇 is still a scolding (§4's Empty state says so about the
 * records page and it is the same instinct), so *that* case is still the empty list.
 *
 * **`HIGHEST_STEP` gets no tile**, because it is the one `BestMetric` with no documented Japanese label
 * anywhere in the specs and none is to be invented (§Q9). The engine table above cannot produce one
 * either — but only because **nothing seeds `HIGHEST_STEP` in v1**: §Q9 moved リーコン・ロン's
 * `primary_metric` to `MOST_VOLUME` (`02-data.md` §F.5 amended, `04` :281 and :885 both map
 * `FIXED_SETS → 最高負荷`), and it is the seed, not this table, that keeps the column empty. The column
 * survives in the schema and the enum, so a stored best carrying it is possible and is *skipped* —
 * without taking やった回数 down with it. Where the step you have reached belongs is [stepFor], as
 * 第九段 / 十八段のうち.
 */
fun bestTilesFor(
    engine: Engine,
    bests: List<RoutineBest>,
    timesDone: Int,
    strings: Strings,
): List<BestTile> {
    if (timesDone <= 0 && bests.isEmpty()) return emptyList()
    val wanted = when (engine) {
        Engine.FOR_TIME, Engine.FOR_TIME_WITH_REST -> listOf(BestMetric.BEST_TIME)
        Engine.AMRAP -> listOf(BestMetric.MOST_ROUNDS, BestMetric.MOST_REPS)
        Engine.EMOM_ASCENDING -> listOf(BestMetric.MOST_ROUNDS)
        Engine.INTERVAL_CIRCUIT, Engine.EMOM, Engine.FIXED_SETS -> listOf(BestMetric.MOST_VOLUME)
    }
    val tiles = wanted.mapNotNull { metric ->
        val best = bests.firstOrNull { it.metric == metric } ?: return@mapNotNull null
        val label = bestMetricLabel(metric, strings) ?: return@mapNotNull null
        BestTile(label, bestValueLabel(metric, best.value, strings))
    }
    // Fail open: the count stands on its own. A `timesDone` of zero beside stored bests is a
    // disagreement between two reads, and the tile that would announce it says 〇回 — so it is the
    // count that is dropped there, not the records.
    if (timesDone <= 0) return tiles
    return tiles + BestTile(strings.gymShared.timesDone, strings.fmt.times(timesDone))
}

/**
 * The tile label for a metric, or null for the one that has none.
 *
 * Public because `GYM.RECORDS.PR`'s rows carry the same four words under a different layout
 * (`04-library-records.md` §4's 最高巡数 ・ 時間内 meta line), and two tables would drift. §7 assigns
 * `bestValueCopy` to a `Bests.kt` this unit does not own — **that function should call these two
 * rather than restate them.**
 */
fun bestMetricLabel(metric: BestMetric, strings: Strings): String? = when (metric) {
    BestMetric.BEST_TIME -> strings.gymShared.metricBestTime
    BestMetric.MOST_ROUNDS -> strings.gymShared.metricMostRounds
    BestMetric.MOST_REPS -> strings.gymShared.metricMostReps
    BestMetric.MOST_VOLUME -> strings.gymShared.metricMostVolume
    // No documented copy exists for this one, and `DECISIONS.md` §Q9 forbids inventing any:
    // `04-library-records.md` §6 :1131's tile list has five entries and none of them is a step.
    // :1164's いちばん上 is the *movement ladder's* hardest rung, a different surface. See [bestTilesFor].
    BestMetric.HIGHEST_STEP -> null
}

/**
 * A best's value in the unit its metric is measured in.
 *
 * [BestMetric.BEST_TIME] is milliseconds and reads as a duration; rounds and reps are counts;
 * 最高負荷 is a **unitless** number by decision (§4, edge case 7) — it is never suffixed 回, because
 * weighted volume is not a rep count and printing it as one would invite adding two of them together.
 */
fun bestValueLabel(metric: BestMetric, value: Double, strings: Strings): String = when (metric) {
    BestMetric.BEST_TIME -> strings.fmt.durationFromMs(value.toLong())
    BestMetric.MOST_ROUNDS -> strings.fmt.rounds(value.toInt())
    BestMetric.MOST_REPS -> strings.fmt.reps(value.toInt())
    BestMetric.MOST_VOLUME -> strings.fmt.count(Math.round(value).toInt())
    BestMetric.HIGHEST_STEP -> strings.fmt.count(value.toInt())
}

/**
 * The 段階 line and its caption: `第七段 ・ 七 六 五 四 四` under `十八段のうち`.
 *
 * Separate fields because they are separate composables at different sizes (§3's mock), and because
 * the caption is the only part that survives a step whose sets are not numbers.
 */
data class StepCopy(val line: String, val caption: String)

/**
 * Where a `FIXED_SETS` routine currently stands, rendered as **one step, not eighteen**.
 *
 * `04-library-records.md` §3 edge case 5 is explicit: リーコン・ロン's detail page shows its current
 * step and a 十八段のうち caption, never the whole ladder. Eighteen rows of five numbers is a reference
 * table, and the page is trying to answer "what am I doing today".
 *
 * `stepIndex` is **1-based**, matching `02-data.md` §F.2's own numbering and the invariant every row
 * satisfies there (`total == 24 + 2 × step`). A step with no history is 第一段, which is why the null
 * case belongs to the caller — a routine with no `progression_state` yet is not an error, it is a
 * routine you have not started.
 *
 * **The step's own stored label wins, and the counter's word comes from `stepUnit`.** `02-data.md`
 * :261 documents `label_ja` as "'第一日' for day-based programs" and `BuiltInCatalog` already seeds
 * `p_armstrong` with `stepUnit = DAY` and 第一日..第五日. Synthesising 第○段 unconditionally is correct
 * only for as long as リーコン・ロン is the sole routine with a progression — the first routine pointing
 * at Armstrong would render 第三段 / 五段のうち over a seed that says 第三日. Neither 段 nor 日 is
 * invented here: both are the seeds' own copy, one per `step_unit` value, and the fallback title takes
 * the same word as the caption so a day-based step with a null `label_ja` cannot read 第三段 / 五日のうち.
 *
 * A step renders its numeral run **only when every set carries a rep count**. A step mixing numbers
 * with `null`s — a pyramid whose last set is to failure — would otherwise print a shorter run than it
 * prescribes, which misstates the prescription rather than merely abbreviating it. There is no
 * documented per-set copy for "to failure" inside this line; 限界まで is the *measure chip*'s word,
 * borrowed into a numeral run it was not written for. So the title stands alone, as the paragraph
 * above always promised.
 */
fun stepFor(state: ProgressionState?, strings: Strings): StepCopy? {
    if (state == null) return null
    // The seed's own label wins. It is catalogue data — 第一日..第五日 for `p_armstrong` — and is not
    // this table's to restate; the synthesised form below is the fallback for a step that has none.
    val title = state.currentStep?.labelJa
        ?: strings.gymShared.stepTitle(strings.fmt.count(state.currentStepIndex), state.stepUnit)
    val sets = state.currentStep?.sets.orEmpty()
    val reps = sets.mapNotNull { it.reps }
    // `size` rather than `isEmpty`: a step that is *partly* numeric is the case that matters, and
    // dropping its unnumbered sets would print a shorter run than the step prescribes.
    val line = if (sets.isEmpty() || reps.size != sets.size) {
        title
    } else {
        title + strings.fmt.separator + reps.joinToString(" ") { strings.fmt.count(it) }
    }
    return StepCopy(
        line = line,
        caption = strings.gymShared.stepCaption(strings.fmt.count(state.stepCount), state.stepUnit),
    )
}
