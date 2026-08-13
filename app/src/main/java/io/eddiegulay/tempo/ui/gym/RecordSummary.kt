package io.eddiegulay.tempo.ui.gym

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.BreakdownRow
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.MINIMUM_MEANINGFUL_SESSION_MS
import io.eddiegulay.tempo.gym.PrChip
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.breakdownRow
import io.eddiegulay.tempo.gym.comparisonCopy
import io.eddiegulay.tempo.gym.durationKanjiFromMs
import io.eddiegulay.tempo.gym.heroTime
import io.eddiegulay.tempo.gym.partialChipCopy
import io.eddiegulay.tempo.gym.prChip
import io.eddiegulay.tempo.ui.Enso
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.ensoSweep
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import kotlinx.coroutines.delay

/*
 * 記録 — one finished session, drawn once and read twice.
 *
 * `00-plan.md` §2 row 9 is binding: **one component under a [RecordMode]**, mounted live by
 * `GYM.SESSION.COMPLETE` and historically by `GYM.RECORDS.SESSION_DETAIL` (Phase 3). Design §5 asked
 * for "one component, two entry points"; the mode is what makes that true without either page lying,
 * and `04-library-records.md` §4's eleven-row difference table is the specification of the lie each
 * one would otherwise tell. Every row of it is either implemented here or belongs to the page:
 *
 * | # | row | where |
 * |---|---|---|
 * | 1 | data source | the **page**'s: `SessionOutcome` live, `SessionDetail` historical, both narrowed here. |
 * | 2 | ensō closure | here — [playsEnsoClosure]. Live animates once; Historical is already closed. |
 * | 3 | rating | here — [ratingPromptVisible] drops どうでしたか on an answered historical session. |
 * | 4 | rating unset | here — an unrated historical session still gets the prompt. Later beats never. |
 * | 5 | comparison baseline | the **store**'s: `previous` arrives scoped to the same routine and tier. |
 * | 6 | PR chip | here — [RecordSummaryData.isStillBest] picks 自己最高 or 当時の自己最高 via `prChip`. |
 * | 7 | streak | here — [recordAccolades] **discards** `streakDays` in Historical rather than asking the caller to pass null. |
 * | 8 | 予定に入れる | neither — it is Phase 3 (`00-plan.md` §6) and **no dead button is drawn**. |
 * | 9 | header actions | the **page**'s. 閉じる and とじる are different words on different pages. |
 * | 10 | tab bar | the shell's. |
 * | 11 | back | the page's. |
 * | 12 | partial sessions | here, and **identical in both modes** — the one row deliberately the same. |
 *
 * Rule 2 of `00-plan.md` §4.1 governs the whole file: **a partial session is a real session.** It gets
 * the 途中まで chip, no PR chip, no ensō ceremony, and the honest numbers, in both modes and in the
 * same pixels. Nothing here scolds, and nothing here decorates what was not earned.
 *
 * *Rejected* — two composables sharing private helpers. That is what "one component" was written to
 * prevent: the shared half drifts within a release, and the drift is invisible because no user sees
 * both screens in the same minute. The mode is an argument precisely so the differences are a table
 * one can read, rather than a diff one has to find.
 *
 * *Rejected* — taking `SessionOutcome` directly. It carries `newBests`, `progression` and a snapshot
 * that the historical page reads differently, and half of it would be unused on either side.
 * [RecordSummaryData] is the intersection, and it is small enough to construct in a test.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure logic — Android-free, JUnit-tested in `gym/ui/RecordSummaryTest.kt`
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** A value slot with nothing in it. `—`, as everywhere else in 鍛錬 (`gym/Numerals.kt`). */
private const val NO_VALUE = "—"

/**
 * A full circle. **Not [io.eddiegulay.tempo.ui.ENSO_SWEEP_DEGREES]**, which is 312° — the gap is what
 * makes the mark an ensō, and closing it is the entire ceremony (design §4). This is therefore the one
 * sweep in the app that deliberately does not come from `ensoSweep`, whose job is to clamp a *fraction
 * of a phase* into the open ring.
 */
const val ENSO_CLOSED_DEGREES = 360f

/** 900ms, `LinearOutSlowInEasing`, drawn once, then still. No confetti (design §4, `03` §A). */
private const val CLOSURE_MS = 900

/** The accolade block's fade, once it is allowed to appear (`03` §A COMPLETE, "Ordering is load-bearing"). */
private const val ACCOLADE_FADE_MS = 240

/** Which of the two pages is mounting [RecordSummary]. See the table at the head of this file. */
enum class RecordMode {
    /** `GYM.SESSION.COMPLETE` — the session ended seconds ago and the ensō has not closed yet. */
    Live,

    /** `GYM.RECORDS.SESSION_DETAIL` — a record reopened, honest about how it looks *now*. */
    Historical,
}

/**
 * Everything the record screen draws, narrowed to what both entry points can supply.
 *
 * @param previous the session immediately before this one **for the same routine and tier**, which is
 *   the store's own scoping (§4 row 5). Null is the first-ever session and renders はじめての記録.
 * @param isStillBest recomputed on read. Live passes `true` unconditionally: a record set thirty
 *   seconds ago is by definition still standing (`RecordCopy.prChip`).
 * @param streakDays `SessionOutcome.streakDays`, and **null means unknown, not zero** — the line is
 *   omitted rather than drawn as `—` (§A COMPLETE edge case 7). Historical discards it either way.
 * @param failedOut an `EMOM_ASCENDING` that ran to failure. Treated as *complete* — reaching failure
 *   is the protocol succeeding — so it keeps the ceremony and only its chip and hero label change.
 * @param routineArchived the routine has since been deleted. もう一度 is disabled and says why
 *   (`04` §6 :1182). Always false live: you were just in it.
 */
@Immutable
data class RecordSummaryData(
    val summary: SessionSummary,
    val results: List<SegmentResult>,
    val previous: SessionSummary?,
    val isStillBest: Boolean,
    val streakDays: Int?,
    val failedOut: Boolean = false,
    val routineArchived: Boolean = false,
)

/** 二十種目 / 種目 — one of the three numbers under the ring. Value first, label under the rule. */
@Immutable
data class RecordTile(val value: String, val label: String)

/**
 * The flattery, gathered so it can be withheld as a unit.
 *
 * It is one type rather than three call sites because the whole block appears at once, 400ms after the
 * rating is answered or six seconds in, and because [firstEver] occupies the comparison's slot and
 * must not double up with it.
 */
@Immutable
data class RecordAccolades(
    /** 前回より 二十二秒 速い, or null — `RecordCopy.comparisonCopy` is null far more often than not. */
    val comparison: String?,
    /** はじめての記録 — `03` §A's `NoComparison` state. Never together with [comparison]. */
    val firstEver: Boolean,
    /** 四日 連続, or null. Live only. */
    val streak: String?,
    /** 自己最高 / 当時の自己最高, or null. */
    val chip: PrChip?,
) {
    /** Nothing to say. The block is not merely empty — it is never inserted, so nothing is announced. */
    val isEmpty: Boolean get() = comparison == null && !firstEver && streak == null && chip == null
}

/**
 * 活動時間, or 到達 for a fail-out.
 *
 * Both are `03` §A COMPLETE's own words (the mock's hero label, and the `FailedOut` state's "hero label
 * becomes 到達"). The label changes because the number means something different: a デス・バイ's clock
 * is not time you spent, it is how far up the ladder you got before the minute beat you.
 */
fun recordHeroLabel(failedOut: Boolean): String = if (failedOut) "到達" else "活動時間"

/**
 * 十七分で 力尽きた — the chip an `EMOM_ASCENDING` fail-out carries instead of 途中まで.
 *
 * The duration goes through `durationKanjiFromMs` like every other measured duration in the app
 * (`DECISIONS.md` §Q10: measured durations are 一分三十秒-shaped, chosen ones are bare seconds).
 */
fun failedOutChip(activeMs: Long): String = durationKanjiFromMs(activeMs) + "で 力尽きた"

/**
 * The one chip under the ring: 途中まで ・ 二十種目中 八, or 十七分で 力尽きた, or nothing.
 *
 * A fail-out is stored `complete = true`, so `partialChipCopy` returns null for it and the two can
 * never collide. The precedence is stated anyway, because the day an engine writes a fail-out as
 * incomplete, the honest chip is the one that says *why* it ended.
 */
fun recordHeaderChip(data: RecordSummaryData): String? = when {
    data.failedOut -> failedOutChip(data.summary.activeMs)
    else -> partialChipCopy(data.summary)
}

/**
 * Whether this session may be flattered at all — `03` §A COMPLETE's `shouldSuppressAccolades`.
 *
 * Two reasons, both already enforced inside `comparisonCopy` and `prChip`. It exists as its own
 * predicate because the *block* has to know: an accolade section that appears, animates and contains
 * nothing is worse than one that never appears, and a `liveRegion` that announces an empty node is
 * worse still.
 *
 * - **Partial.** A session you stopped early is faster than one you finished, and saying so is a taunt.
 * - **Under thirty seconds** (`MINIMUM_MEANINGFUL_SESSION_MS`, §A COMPLETE edge case 6). A twelve-second
 *   "session" that beats a real one is noise.
 */
fun shouldSuppressAccolades(summary: SessionSummary): Boolean =
    !summary.complete || summary.activeMs < MINIMUM_MEANINGFUL_SESSION_MS

/**
 * 四日 連続, or null.
 *
 * Null covers both unknowns and they are deliberately the same pixel: `streakDays == null` is
 * §A edge case 7's "if it returns null the line is **omitted**, not rendered as `—`", and zero is
 * `04` §6's "never `〇日 連続`". The records page has a word for a broken streak (連続は とぎれています);
 * this screen does not use it, because the user has just this second trained — a session that ends with
 * a zero-day streak is an arithmetic disagreement, not a fact worth stating over a finished workout.
 */
fun streakLine(days: Int?): String? {
    if (days == null || days <= 0) return null
    return JapaneseDate.kanjiExtended(days) + "日 連続"
}

/**
 * The three tiles: 種目 / 巡 / 回.
 *
 * **Completed, not planned.** A partial session's chip already says 二十種目中 八; a tile reading 二十種目
 * beside it would be the same screen quoting two different numbers for the same thing, and the honest
 * one is what you did.
 *
 * **Zero renders `—`.** §A COMPLETE edge case 4 states it for reps — a pure-time circuit has no rep
 * count and "zero is not a result here, it is an inapplicability" — and the same holds for the other
 * two: `RecordCopy.sessionRowLines` already *omits* a zero 巡 or 回 fragment for exactly that reason.
 * A fixed three-column row cannot omit a column without unaligning the labels, so the slot takes the
 * app's own empty value instead. 〇回 in 20.sp reads as a score.
 */
fun recordTiles(summary: SessionSummary): List<RecordTile> = listOf(
    RecordTile(tileValue(summary.stationsCompleted, "種目"), "種目"),
    RecordTile(tileValue(summary.roundsCompleted, "巡"), "巡"),
    RecordTile(tileValue(summary.totalReps, "回"), "回"),
)

private fun tileValue(count: Int, unit: String): String =
    if (count <= 0) NO_VALUE else JapaneseDate.kanjiExtended(count) + unit

/**
 * The comparison, the streak and the PR chip, after both the mode and the session have had their say.
 *
 * The streak is dropped in [RecordMode.Historical] **here** rather than by asking the caller to pass
 * null (§4 row 7). Showing today's streak on a March record is a category error, and computing March's
 * would mean walking the whole history for one line; making the component enforce it means the Phase 3
 * page cannot get it wrong by handing over the number it happens to have.
 */
fun recordAccolades(mode: RecordMode, data: RecordSummaryData): RecordAccolades {
    val suppressed = shouldSuppressAccolades(data.summary)
    return RecordAccolades(
        comparison = comparisonCopy(data.summary, data.previous),
        // A first session is a fact, not a compliment, so it survives the suppression that silences
        // the comparison — but not a partial one: 「はじめての記録」 over a session the user walked out
        // of claims a milestone they did not reach.
        firstEver = data.previous == null && data.summary.complete,
        streak = if (mode == RecordMode.Live) streakLine(data.streakDays) else null,
        chip = if (suppressed) null else prChip(data.summary, data.isStillBest),
    )
}

/**
 * Whether the ring closes under animation — Live, and only for a session that finished.
 *
 * A partial session's ring holds at [recordSweepAngle] and does not close (`03` §A `Partial`, design
 * §4). A historical record is drawn already closed and never animates, in any state.
 */
fun playsEnsoClosure(mode: RecordMode, summary: SessionSummary): Boolean =
    mode == RecordMode.Live && summary.complete

/**
 * Where the ring ends up: a closed circle for a finished session, its final partial sweep otherwise.
 *
 * The partial sweep is stations, not time: a session that ran long and stopped early has no meaningful
 * fraction of a clock, and `stations_planned` is the frozen denominator the 途中まで chip already
 * counts against (`RecordCopy.partialChipCopy`), so the ring and the chip agree by construction.
 *
 * A missing or damaged denominator draws **no ink** rather than a full ring, on the same argument
 * `ensoSweep` makes about NaN: an unknown fraction must not render as a completed one.
 */
fun recordSweepAngle(summary: SessionSummary): Float {
    if (summary.complete) return ENSO_CLOSED_DEGREES
    if (summary.stationsPlanned <= 0) return 0f
    return ensoSweep(summary.stationsCompleted.toFloat() / summary.stationsPlanned)
}

/**
 * The 内訳, in `session_result` order, with the rows the breakdown does not list already dropped.
 *
 * `RecordCopy.breakdownRow` owns every word and every one of the five shapes; this is the plural, and
 * it is here rather than there only because `RecordCopy.kt` belongs to another unit. **Do not add a
 * second formatter** — if this ever needs a shape `breakdownRow` lacks, the fix is in `breakdownRow`.
 *
 * @param exerciseName the caller's resolver. The live page reads the in-memory catalogue; Phase 3's
 *   historical page may prefer the **pinned** `routine_version`, which is what §4 edge case 1 requires
 *   for stations that have since been removed from the live routine. Injecting it is what lets one
 *   component serve both without knowing which.
 */
fun breakdownRows(
    results: List<SegmentResult>,
    exerciseName: (String?) -> String?,
): List<BreakdownRow> = results.mapNotNull { breakdownRow(it, exerciseName(it.exerciseId)) }

/**
 * Whether どうでしたか is drawn above the three buttons.
 *
 * §4 row 3: the historical page shows what was answered and lets it be changed, but drops the question
 * once it has an answer — a record you are reading is not being asked anything. Live always asks, even
 * after a tap, because §A's `Rated` state is explicit that **the block does not collapse**: the user
 * may change their mind, and a heading that disappears on first tap would make the second tap look
 * like it was not offered.
 */
fun ratingPromptVisible(mode: RecordMode, rating: Rating?): Boolean =
    mode == RecordMode.Live || rating == null

/** `未評価` / `ちょうど を選択` — the rating group's `stateDescription` (`03` §A COMPLETE accessibility). */
fun ratingGroupState(rating: Rating?): String =
    if (rating == null) "未評価" else rating.label + " を選択"

/** `きついとして記録する` — the `onClick` label a rating chip carries (`04` §4 accessibility). */
fun ratingOptionLabel(rating: Rating): String = rating.label + "として記録する"

/**
 * How long the accolades wait — §A COMPLETE's "Ordering is load-bearing", in one number.
 *
 * Design §4 wants the rating asked *before* the streak and the PR chip can bias it, and also wants
 * them above it in the reading order. The resolution is time rather than layout: the block fades in
 * **400ms after the first rating tap or after 6 seconds, whichever is first**, and the final scroll
 * order stays exactly as drawn.
 *
 * The six seconds is not an animation and is therefore untouched by `ANIMATOR_DURATION_SCALE`
 * (§A COMPLETE edge case 1) — only the fade is, and Compose scales that itself through the
 * recomposer's `MotionDurationScale`, exactly as `GymShell.gymEntrance` documents.
 */
fun accoladeDelayMs(rated: Boolean): Long = if (rated) 400L else 6_000L

/** `七分間、活動時間 六分十四秒、途中まで ・ 二十種目中 八` — the header as one node, chip included. */
fun recordHeroSemantics(data: RecordSummaryData): String = listOfNotNull(
    data.summary.routineName,
    recordHeroLabel(data.failedOut) + " " + heroTime(data.summary.activeMs),
    recordHeaderChip(data),
).joinToString("、")

/**
 * `二十種目、五巡、三百二十回` — the three tiles as **one** node.
 *
 * Three separate nodes read as three orphan numbers (§A COMPLETE accessibility). The labels are left
 * out on purpose: every value already carries its own unit, so 「種目 二十種目」 would say it twice.
 */
fun recordTilesSemantics(tiles: List<RecordTile>): String =
    tiles.joinToString("、") { it.value }

/** `前回より 二十二秒 速い、四日 連続、自己最高` — the delayed block as one polite announcement. */
fun accoladeSemantics(accolades: RecordAccolades): String? {
    val parts = listOfNotNull(
        accolades.comparison ?: "はじめての記録".takeIf { accolades.firstEver },
        accolades.streak,
        accolades.chip?.label,
    )
    return parts.joinToString("、").takeIf { it.isNotEmpty() }
}

/** `バーピー、とばした` / `腕立て伏せ、0:41、済、二十回` — one breakdown row, one node. */
fun breakdownSemantics(row: BreakdownRow): String = if (row.skipped) {
    row.name + "、" + row.status
} else {
    listOfNotNull(row.name, row.duration, row.status, row.reps).joinToString("、")
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The component
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * One finished session, drawn in design §4's order, under [mode]'s rules.
 *
 * The caller owns the page header (閉じる vs とじる) and anything below もう一度 ([footer]); this owns
 * everything between the ensō and the 内訳, which is the part that must not differ.
 *
 * @param rating the current answer, held by the caller because the write is optimistic on both pages.
 * @param ratingFault a `rateSession` that did not land. **Never silently reverted here** — §A COMPLETE
 *   is explicit that the failure is stated rather than the value withdrawn.
 * @param onRate re-tapping the selected rating passes null; un-rating is a real answer (§4 row 3, and
 *   `SessionActions.onRate`).
 * @param exerciseName see [breakdownRows].
 * @param footer 型を見る / 記録を削除 for the historical page. **予定に入れる is Phase 3 and no slot is
 *   reserved for it** — a greyed button for an action that does not exist teaches the user it is broken.
 */
@Composable
fun RecordSummary(
    mode: RecordMode,
    data: RecordSummaryData,
    rating: Rating?,
    ratingFault: TempoFault?,
    onRate: (Rating?) -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    exerciseName: (String?) -> String? = { id -> id?.let { ExerciseCatalog.byId(it)?.nameJa } },
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val scroll = rememberScrollState()
    val accolades = remember(mode, data) { recordAccolades(mode, data) }
    val tiles = remember(data.summary) { recordTiles(data.summary) }
    // Keyed on the rows only: the resolver is a property of the *page* and never changes within one,
    // while a default lambda argument is a fresh instance on every recomposition and would rebuild the
    // whole breakdown twenty times a second for nothing.
    val rows = remember(data.results) { breakdownRows(data.results, exerciseName) }

    /*
     * The rating gate. Historical has nothing to gate — its accolades are as old as its numbers and
     * cannot bias an answer given months ago — so the block is simply there.
     */
    var revealed by remember(mode) { mutableStateOf(mode == RecordMode.Historical) }
    LaunchedEffect(mode, rating != null) {
        if (mode != RecordMode.Live) return@LaunchedEffect
        delay(accoladeDelayMs(rating != null))
        revealed = true
    }

    // Viewport and rating geometry, for the one scroll §A COMPLETE asks for: the page opens with the
    // rating row at the vertical centre, so the question is what the user is looking at.
    var viewportTop by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0) }
    var ratingCentre by remember { mutableStateOf<Int?>(null) }
    var centred by remember(mode) { mutableStateOf(mode != RecordMode.Live) }

    LaunchedEffect(ratingCentre, viewportHeight) {
        val centre = ratingCentre ?: return@LaunchedEffect
        if (centred || viewportHeight <= 0) return@LaunchedEffect
        centred = true
        // Clamped by ScrollState itself: on a screen tall enough to hold the whole record this is a
        // no-op, which is the correct behaviour rather than a special case.
        scroll.scrollTo((centre - viewportHeight / 2).coerceAtLeast(0))
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                viewportTop = it.positionInRoot().y
                viewportHeight = it.size.height
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            RecordHero(mode = mode, data = data)

            Spacer(Modifier.height(22.dp))
            RecordTiles(tiles)

            // Unconditional, and *above* the block it speaks for. See [AccoladeAnnouncer]: the live
            // region has to be a node that already existed on the previous accessibility pass, which
            // the `AnimatedVisibility` content by construction is not.
            AccoladeAnnouncer(revealed = revealed, accolades = accolades)

            // One `AnimatedVisibility`, exactly as §A COMPLETE costs it out. `ExitTransition.None`
            // because it never goes away again: the block is revealed once and the page is then still.
            AnimatedVisibility(
                visible = revealed && !accolades.isEmpty,
                enter = fadeIn(tween(ACCOLADE_FADE_MS, easing = LinearOutSlowInEasing)),
                exit = ExitTransition.None,
            ) {
                Accolades(accolades)
            }

            if (ratingFault != null && mode == RecordMode.Historical) {
                // §4's data-out note puts the strip *above* the rating row on the historical page,
                // where the optimistic value is reverted and the user is looking at a row that just
                // changed back. Live keeps it under the buttons, where §A COMPLETE puts it.
                Spacer(Modifier.height(16.dp))
                FaultStrip(fault = ratingFault, onRecover = { onRate(rating) })
            }

            RatingBlock(
                mode = mode,
                rating = rating,
                onRate = onRate,
                modifier = Modifier.onGloballyPositioned { coords ->
                    if (centred) return@onGloballyPositioned
                    val topInViewport = coords.positionInRoot().y - viewportTop
                    ratingCentre = (topInViewport + scroll.value + coords.size.height / 2f).toInt()
                },
            )

            if (ratingFault != null && mode == RecordMode.Live) {
                Spacer(Modifier.height(12.dp))
                FaultStrip(fault = ratingFault, onRecover = { onRate(rating) })
            }

            SectionLabel("内訳")
            rows.forEach { row -> BreakdownRowLine(row) }

            Spacer(Modifier.height(24.dp))
            RepeatAction(archived = data.routineArchived, onRepeat = onRepeat)
            footer()
            Spacer(Modifier.height(64.dp))
        }
    }
}

/**
 * The ensō, the hero, and the chip — one node, in one 220.dp box.
 *
 * The ring's closure is gated on **`ON_RESUME`, not on composition** (§A COMPLETE edge case 2): a
 * session that auto-completed while the screen was off has already fired its completion cue, and the
 * 900ms closure has to be there to be watched. `repeatOnLifecycle` is how that is expressed; the guard
 * on `value` is what stops it replaying on every later foreground, which would be ceremony on demand.
 *
 * `ANIMATOR_DURATION_SCALE == 0` needs no code (§A COMPLETE edge case 1): `Animatable.animateTo`
 * resolves through the recomposer's `MotionDurationScale`, so the arc lands closed on its first frame.
 */
@Composable
private fun RecordHero(mode: RecordMode, data: RecordSummaryData) {
    val c = LocalTempoColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val target = remember(data.summary) { recordSweepAngle(data.summary) }
    val plays = remember(mode, data.summary) { playsEnsoClosure(mode, data.summary) }
    val sweep = remember(target, plays) { Animatable(if (plays) 0f else target) }

    if (plays) {
        LaunchedEffect(sweep, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (sweep.value < target) {
                    sweep.animateTo(target, tween(CLOSURE_MS, easing = LinearOutSlowInEasing))
                }
            }
        }
    }

    val chip = recordHeaderChip(data)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = recordHeroSemantics(data)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative: the hero text carries all of the meaning (§A COMPLETE accessibility).
            Enso(
                color = c.accent,
                sweepAngle = sweep.value,
                modifier = Modifier.size(220.dp).clearAndSetSemantics {},
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = data.summary.routineName,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 15.sp,
                        letterSpacing = 6.sp,
                        color = c.inkFaint,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                HeroTime(heroTime(data.summary.activeMs))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = recordHeroLabel(data.failedOut),
                    style = TextStyle(
                        fontFamily = Gothic,
                        fontSize = 12.sp,
                        letterSpacing = 3.sp,
                        color = c.inkFaint,
                    ),
                )
            }
        }
        if (chip != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .height(28.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chip,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 12.sp,
                        letterSpacing = 3.sp,
                        color = c.inkFaint,
                    ),
                )
            }
        }
    }
}

/**
 * 六分十四秒 at 64.sp, and never wrapped.
 *
 * Six kanji at 64.sp is wider than a phone, so the size is capped the way `03` §A WORK edge case 7 caps
 * the countdown — with one deliberate change to its arithmetic. WORK divides the available width by
 * **4.2**, which encodes the four-character shape of `0:23`; a hero time is five or six kanji and that
 * divisor would still overflow. Dividing by **the string's own length** instead invents no number at
 * all and is the more accurate form anyway, because a CJK glyph advances almost exactly one em.
 *
 * `fontScale` is in the denominator for the same reason: `X.sp` occupies `X × fontScale` dp, so a cap
 * expressed in sp that ignored it would fail at exactly the accessibility setting it exists for.
 */
@Composable
private fun HeroTime(text: String) {
    val c = LocalTempoColors.current
    val density = LocalDensity.current
    BoxWithConstraints {
        val glyphs = text.length.coerceAtLeast(1)
        val cap = (maxWidth.value / (glyphs * density.fontScale)).sp
        val size: TextUnit = if (cap < 64.sp) cap else 64.sp
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFamily = Mincho, fontSize = size, color = c.ink),
        )
    }
}

/** 二十種目 ・ 五巡 ・ 三百二十回, over a 1.dp rule, read as one node. */
@Composable
private fun RecordTiles(tiles: List<RecordTile>) {
    val c = LocalTempoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = recordTilesSemantics(tiles) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.forEach { tile ->
                Text(
                    text = tile.value,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = Gothic, fontSize = 20.sp, color = c.ink),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.forEach { tile ->
                Text(
                    text = tile.label,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                )
            }
        }
    }
}

/**
 * The one node that says the accolades out loud — 1.dp tall, and **present from the first frame**.
 *
 * §A COMPLETE's accessibility requirement is that the delayed block "is announced when it appears
 * rather than silently inserted below the reading position". Putting `liveRegion = Polite` on the block
 * itself does not do that, and the reason is in Compose's own delegate rather than in our code:
 * `AndroidComposeViewAccessibilityDelegateCompat.sendSemanticsPropertyChangeEvents` (ui 1.7.6) opens
 * every iteration with `val oldNode = previousSemanticsNodes[id] ?: return@forEachKey`, so a semantics
 * node that **did not exist on the previous pass emits no event at all**. A live region that is born
 * already speaking is silent for ever; only a live region whose properties *change* is announced.
 *
 * So the node is here, outside the `AnimatedVisibility`, from the first composition, and what changes
 * at the reveal is its `contentDescription` — `""` before, [accoladeSemantics] after. That is a
 * `ContentDescription` diff on a node the delegate already knows, which is the one branch of
 * `sendSemanticsPropertyChangeEvents` that ships the new text inside the event.
 *
 * **1.dp, not `size(0.dp)`.** A genuinely zero-size node is pruned before it can ever be diffed:
 * `SemanticsUtils.android.kt`'s `findAllSemanticNodesRecursive` does `region.set(left, top, right,
 * bottom)` from `touchBoundsInRoot` and keeps the node only `if (region.op(unaccountedSpace, INTERSECT))`
 * — an empty `Region` intersects to false, and this node has no click to expand it to a minimum touch
 * target. One dp is the smallest box that survives that test at every density, and it is 1.dp of
 * whitespace above a block that is already 20.dp clear of the tiles.
 *
 * *Rejected* — clearing the description again a frame after the announcement, so the text is never
 * duplicated between this node and [Accolades]. The announcement travels **inside** the event, so
 * clearing would be safe, but it makes the announcement depend on a recomposition landing after the
 * one that armed it, and it fires a second, empty event. The cost it avoids is TalkBack reading the
 * accolade line twice to a user swiping through the page; the cost it adds is a timing-dependent
 * announcement, which is the failure this whole node exists to fix.
 *
 * *Rejected* — `invisibleToUser()` on this node to stop that double read. It sets
 * `info.isVisibleToUser = false` and `isImportantForAccessibility = false`, and a screen reader that
 * drops events from nodes it has been told are invisible would put us back at silence.
 */
@Composable
private fun AccoladeAnnouncer(revealed: Boolean, accolades: RecordAccolades) {
    val announcement =
        if (revealed && !accolades.isEmpty) accoladeSemantics(accolades).orEmpty() else ""
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
    )
}

/**
 * The comparison, the streak and the PR chip, drawn together when they appear.
 *
 * `clearAndSetSemantics` makes the whole block one node so it reads as one sentence rather than three
 * orphans — but **no `liveRegion` here**. This node does not exist until the reveal, and a node that
 * appears cannot announce (see [AccoladeAnnouncer] for the delegate line that decides it). Its
 * description is what TalkBack reads when the user *navigates* to the block; the announcement when the
 * block *arrives* belongs to the announcer above it.
 */
@Composable
private fun Accolades(accolades: RecordAccolades) {
    val c = LocalTempoColors.current
    val description = accoladeSemantics(accolades)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .clearAndSetSemantics {
                if (description != null) contentDescription = description
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val comparison = accolades.comparison
        if (comparison != null) {
            Text(
                text = comparison,
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
            )
        } else if (accolades.firstEver) {
            Text(
                text = "はじめての記録",
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.inkSoft),
            )
        }
        accolades.streak?.let { streak ->
            Text(
                text = streak,
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.inkSoft),
            )
        }
        accolades.chip?.let { chip ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .height(28.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chip.label,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 12.sp,
                        letterSpacing = 3.sp,
                        // 当時の自己最高 is faint: it was a record when it was set and has since been
                        // beaten, and a chip that keeps the accent keeps claiming the crown (§4 row 6).
                        color = if (chip == PrChip.CURRENT) c.accent else c.inkFaint,
                    ),
                )
            }
        }
    }
}

/**
 * どうでしたか — three buttons, one tap, skippable.
 *
 * A `selectableGroup` of `Role.RadioButton`, which is what "one of three, exactly one selected"
 * announces without a hand-written state string per chip; the group's own `stateDescription` carries
 * 未評価 for the unanswered case, which a radio group cannot express on its own.
 *
 * Re-tapping the selected answer passes null. That is the historical page's editability
 * (§4 row 3) and it costs the live page nothing to have: a mis-tapped rating that cannot be taken back
 * would be the one irreversible thing on a screen with no confirmations.
 */
@Composable
private fun RatingBlock(
    mode: RecordMode,
    rating: Rating?,
    onRate: (Rating?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    Column(modifier.fillMaxWidth().padding(top = 22.dp)) {
        if (ratingPromptVisible(mode, rating)) {
            Text(
                text = "どうでしたか",
                modifier = Modifier.semantics { heading() },
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { stateDescription = ratingGroupState(rating) },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Rating.entries.forEach { option ->
                val selected = option == rating
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .then(
                            if (selected) {
                                Modifier.border(1.dp, c.accent, RoundedCornerShape(16.dp))
                            } else {
                                Modifier
                            },
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onRate(if (selected) null else option) },
                        )
                        .semantics { onClick(label = ratingOptionLabel(option), action = null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = TextStyle(
                            fontFamily = Mincho,
                            fontSize = 15.sp,
                            letterSpacing = 2.sp,
                            color = if (selected) c.accent else c.inkSoft,
                        ),
                    )
                }
            }
        }
    }
}

/** 内訳 — and どうでしたか's token, which is the only sectional one this screen's spec defines. */
@Composable
private fun SectionLabel(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.padding(top = 26.dp, bottom = 8.dp).semantics { heading() },
        style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 3.sp, color = c.inkFaint),
    )
}

/**
 * `腕立て伏せ  0:41  済  二十回` — one station, one node.
 *
 * A skipped row is `c.inkFaint` **as a whole**, which is why `BreakdownRow` returns its parts rather
 * than a sentence: the colour is a property of the row, not of any one column.
 */
@Composable
private fun BreakdownRowLine(row: BreakdownRow) {
    val c = LocalTempoColors.current
    val colour = if (row.skipped) c.inkFaint else c.inkSoft
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 36.dp)
            .clearAndSetSemantics { contentDescription = breakdownSemantics(row) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.name,
            modifier = Modifier.weight(1f, fill = true),
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = colour),
        )
        Text(
            text = row.duration,
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = colour),
        )
        Text(
            text = row.status,
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = colour),
        )
        val reps = row.reps
        if (reps != null) {
            Text(
                text = reps,
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = colour),
            )
        }
    }
}

/**
 * もう一度 — back to the routine, which is the only thing this screen offers to do next in Phase 1.
 *
 * Disabled with its reason when the routine has been deleted (`04` §6 :1182): the action is *greyed
 * rather than dropped*, which is this app's idiom for a control that exists and cannot be used, and it
 * carries `disabled()` so TalkBack stops offering it.
 */
@Composable
private fun RepeatAction(archived: Boolean, onRepeat: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = !archived, onClick = onRepeat)
            .semantics {
                role = Role.Button
                contentDescription = "もう一度"
                if (archived) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "もう一度",
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 15.sp,
                letterSpacing = 2.sp,
                color = if (archived) c.inkFaint else c.accent,
            ),
        )
    }
    if (archived) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "この型は削除されています",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
        )
    }
}
