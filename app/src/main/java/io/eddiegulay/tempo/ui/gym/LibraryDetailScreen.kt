package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.label
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.gym.BestTile
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymTab
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.ProgressionState
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.gym.RoutineDetail
import io.eddiegulay.tempo.gym.RoutineEstimate
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.RoutineStation
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.StartBlock
import io.eddiegulay.tempo.gym.Tier
import io.eddiegulay.tempo.gym.bestMetricLabel
import io.eddiegulay.tempo.gym.bestTilesFor
import io.eddiegulay.tempo.gym.engineRows
import io.eddiegulay.tempo.gym.estimateLabel
import io.eddiegulay.tempo.gym.heroTime
import io.eddiegulay.tempo.gym.partialChipCopy
import io.eddiegulay.tempo.gym.prChip
import io.eddiegulay.tempo.gym.startBlock
import io.eddiegulay.tempo.gym.stepFor
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.LocalDate

/*
 * 型の中身 — `GYM.LIBRARY.DETAIL`, and the start path.
 *
 * This page absorbed `GYM.SESSION.PREFLIGHT` (`00-plan.md` §2 row 7). PREFLIGHT contributed exactly
 * two mechanisms and both live in 始める: the **start guard** (a live session raises the resume prompt
 * instead of starting) and **insert-before-navigate** (the session row exists in SQLite before the
 * player mounts, which is the one ordering process death can survive). Neither is implemented here —
 * both are `GymViewModel.startSession`'s, which is where they can be tested — and this file's job is
 * to make them visible: a 支度 label while the insert is in flight, a fault that stays on this page
 * when the insert fails, and the prompt when the guard fires.
 *
 * **Everything this page computes is a pure function above the composables** (`00-plan.md` §4.1
 * rule 10). The rule earns its keep here more than anywhere else in the feature: 削除's copy branches
 * on a session count, the estimate line branches on the engine, the 最高 tiles branch on the engine
 * *and* on whether a record was set this month, and a detail page is the surface a user reads rather
 * than taps — a wrong word is invisible in a screenshot and obvious in use.
 *
 * *Rejected* — a `LazyColumn`. §3 specifies `Column(verticalScroll)` and the content is bounded by
 * construction (≤ ~25 station rows, ≤ 5 attempts, ≤ 4 read-only rows), so laziness would buy nothing
 * and cost the whole-page scroll position on every state change.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure copy and shaping — Android-free, JUnit-tested in `ui/gym/LibraryDetailCopyTest.kt`
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** A value slot with nothing to put in it. `—`, as everywhere else in 鍛錬 (`gym/Numerals.kt`). */
private const val NO_VALUE = "—"

/**
 * 「シンディ」を削除しますか — the confirm, whose words change with what is at stake, **and which has
 * no destructive branch at all until the stake is actually known.**
 *
 * **Named apart from the identical function in `GymHomeCopy.kt` and `LibraryIndexScreen.kt` on
 * purpose.** All three surfaces offer 削除 and all three landed the same §1 branch independently; the
 * two of them collide with each other at the time of writing, and adding a third `deleteRoutineCopy`
 * to the package would have made a two-way redeclaration a three-way one. The right end state is one
 * copy of this function that all three call — it is Android-free and belongs beside `RecordCopy.kt` —
 * and that is a move across files no page unit owns. Flagged in this unit's report.
 *
 * `04-library-records.md` §1 rules 2 and 4 are the whole of the Ready branch, and it is **not**
 * cosmetic: with sessions behind it 削除 is an `archiveRoutine` and the records survive, with none it
 * is a `purgeRoutine` and the row is gone. The copy has to say which, because the user cannot see the
 * blast radius of a button whose label is the same in both cases — which is exactly why §1 rule 4
 * says 完全に削除 is *offered* only at zero rather than being the always-available option.
 *
 * **Why the parameter is a [Loadable] and not an `Int`.** It used to be an `Int` seeded with zero, so
 * "not read yet" and "read failed" both rendered 「やった記録はありません。完全に消えます。」 under a
 * 完全に削除 button — an ありません-as-emptiness claim about the user's records, on the one irreversible
 * dialog in the whole feature (`00-plan.md` §4.1 rule 1, `DECISIONS.md` §Q6). An `Int?` would have
 * fixed the seeding and still lost *why* it is unknown, and the two unknowns want different pixels:
 * [DetailDeleteState.Waiting] is a question that is about to be answerable, [Unreadable] is one that
 * is not, and only the second has something to say and a もう一度 to offer.
 *
 * **Neither unknown offers a destructive button.** Not even the archive wording, which is the tempting
 * conservative default: 削除 with an unread count would still claim 「これまでの〇回の記録は残ります」,
 * and there is no count to put in it. A dialog that asks nothing until it can say what it is asking is
 * the only honest shape.
 *
 * [DetailDeleteState.Confirm.purge] rather than the caller re-deriving the branch from the count: the
 * words and the write must come from **one** read, or a count that resolves between the sentence and
 * the tap turns 「記録は残ります」 into a purge.
 *
 * Every string here is §3's delete-confirm table verbatim, with the count rendered through
 * `kanjiExtended` (§6's Numerals note: counts are kanji) and 読み込み中 the app's own Loading word
 * (`01-shell.md` §B, `04` §3 — the same word this page's body slot already draws).
 */
internal sealed interface DetailDeleteState {

    /** 「シンディ」を削除しますか — identical in all three states; the question does not depend on the count. */
    val title: String

    /** やめる. Present in every state, because leaving must always be the available answer. */
    val cancel: String

    /** The count is known: [purge] is true only for a genuine `Ready(0)`. */
    data class Confirm(
        override val title: String,
        val body: String,
        val confirm: String,
        override val cancel: String,
        val purge: Boolean,
    ) : DetailDeleteState

    /** The count is still being read. Says so, and offers nothing to press but やめる. */
    data class Waiting(
        override val title: String,
        val body: String,
        override val cancel: String,
    ) : DetailDeleteState

    /** The count cannot be read. The fault says 記録を読めません ・ もう一度; nothing here deletes. */
    data class Unreadable(
        override val title: String,
        val fault: TempoFault,
        override val cancel: String,
    ) : DetailDeleteState
}

internal fun detailDeleteCopy(name: String, sessions: Loadable<Int>): DetailDeleteState {
    val title = "「" + name + "」を削除しますか"
    return when (sessions) {
        is Loadable.Ready ->
            if (sessions.value > 0) {
                DetailDeleteState.Confirm(
                    title = title,
                    body = "これまでの" + JapaneseDate.kanjiExtended(sessions.value) +
                        "回の記録は残ります。型だけが一覧から消えます。",
                    confirm = "削除",
                    cancel = "やめる",
                    purge = false,
                )
            } else {
                DetailDeleteState.Confirm(
                    title = title,
                    body = "やった記録はありません。完全に消えます。",
                    confirm = "完全に削除",
                    cancel = "やめる",
                    purge = true,
                )
            }

        Loadable.Loading -> DetailDeleteState.Waiting(title = title, body = "読み込み中", cancel = "やめる")

        is Loadable.Failed -> DetailDeleteState.Unreadable(
            title = title,
            fault = sessions.fault,
            cancel = "やめる",
        )
    }
}

/**
 * What one station asks for: 五回 / 三十秒 / 限界まで.
 *
 * Seconds render **bare**, not through `durationKanji` — `DECISIONS.md` §Q10's rule is about acts
 * rather than durations, and a prescription is a value somebody chose. A 90-second hold that the
 * builder wheel set as 九十秒 must not read back as 一分三十秒 on the page that describes it.
 *
 * `限界まで` is the picker's own word for [Measure.MAX_EFFORT] (§6's measures row). It is the only
 * string on this page taken from a table written for another surface; there is no documented
 * detail-page rendering of a max-effort prescription, and the alternative — leaving the slot blank —
 * would read as a missing value rather than as an open-ended set.
 *
 * A prescription whose value is null against a measure that requires one is a store bug the CHECK
 * constraint should have refused. It renders [NO_VALUE] rather than 〇回, which would be a claim.
 */
internal fun prescriptionLabel(station: RoutineStation): String = when (station.measure) {
    Measure.REPS -> station.prescribedReps?.let { JapaneseDate.kanjiExtended(it) + "回" } ?: NO_VALUE
    Measure.DURATION -> station.prescribedSeconds?.let { JapaneseDate.kanjiExtended(it) + "秒" } ?: NO_VALUE
    Measure.MAX_EFFORT -> "限界まで"
}

/**
 * 一番目、懸垂、五回 — a station row as one TalkBack node (§3, accessibility).
 *
 * Ordinal first, because a circuit is an order and a screen-reader user arriving mid-list has no
 * column to read it from. The trailing `›` is decorative and never enters this string.
 */
internal fun stationRowSemantics(position: Int, name: String, prescription: String): String =
    JapaneseDate.kanjiExtended(position) + "番目、" + name + "、" + prescription

/**
 * Why 始める cannot be pressed, or null when the reason is already on screen.
 *
 * [StartBlock.Archived] returns null on purpose: the 削除済み chip beside the title has already said
 * it, and repeating it under a disabled button would be the page telling the user twice.
 *
 * [StartBlock.NoStations] has **no documented detail-page string**. 種目を加えてください is §6's
 * builder copy for the same fact, borrowed here rather than inventing a sentence; the case is
 * unreachable through the seeder and the builder anyway (see [StartBlock]'s own KDoc), and a
 * disabled button with no explanation is worse than one explained in the builder's words. Flagged in
 * this unit's report.
 */
internal fun startBlockCopy(block: StartBlock): String? = when (block) {
    StartBlock.None -> null
    StartBlock.Archived -> null
    StartBlock.UnknownExercise -> "種目が見つからないため 始められません"
    StartBlock.NoStations -> "種目を加えてください"
}

/**
 * What TalkBack reads on 始める — 「シンディ」を始める, **in every face the button has**.
 *
 * §6 :1121 documents exactly one description for this button and the verb in it is 始める. The visible
 * label is allowed to change (`Starting` swaps it for 支度, §3's states list); the *description* is not,
 * because substituting the label into it produced 「シンディ」を支度 — a sentence no table carries and
 * not grammatical Japanese, since 支度 is a noun the mock uses as a標識, not a verb one does to a 型.
 *
 * The reason, when there is one, is **appended** rather than swapped in, for the same reason the
 * builder writes 「保存、名前と種目が要ります」: an inert word explains nothing, and a screen-reader user
 * cannot see the `c.inkFaint` line underneath. [StartBlock.Archived] appends nothing on purpose — the
 * 削除済み chip is inside the header's own merged node and would be the page saying it twice.
 *
 * 支度 is appended for the in-flight face because it is the state's own word (`03-player.md` §A) and
 * because `disabled()` alone would announce a button that is unavailable for no stated reason. The
 * separator is 、 as everywhere else on this page.
 */
internal fun startButtonDescription(name: String, starting: Boolean, block: StartBlock): String =
    listOfNotNull(
        "「" + name + "」を始める",
        if (starting) "支度" else null,
        startBlockCopy(block),
    ).joinToString("、")

/**
 * One row of これまで: 六月十七日 ・ 十七巡 ・ 二十分 ・ 自己最高, as four aligned slots.
 *
 * **Not `sessionRowLines`**, which is the history list's shape: that one prints 十七日 because its
 * rows sit under a 六月 header, and this list has none — a five-row preview spanning two months would
 * otherwise print two 十七日 and mean two different days.
 *
 * [score] is what the outing is measured in and is omitted rather than zeroed: a pure-time circuit
 * completes no rounds it counts and 〇巡 would read as a bad session rather than an inapplicable
 * number (the same call `sessionRowLines` and `estimateLabel` make).
 *
 * [chip] is 途中まで **or** 自己最高 and never both — a partial session cannot hold a record
 * (`00-plan.md` §4.1 rule 2, `04-library-records.md` §4 edge case 1). The list has no per-row
 * `isStillBest`, so a standing record is all it can honestly claim; 当時の自己最高 is the session
 * detail page's distinction, where the current best has actually been read.
 */
internal data class AttemptLine(
    val date: String,
    val score: String?,
    val duration: String,
    val chip: String?,
    val partial: Boolean,
)

internal fun attemptLine(summary: SessionSummary): AttemptLine {
    val partial = partialChipCopy(summary)
    return AttemptLine(
        date = JapaneseDate.monthDay(summary.localDate.atStartOfDay()),
        score = when {
            summary.roundsCompleted > 0 -> JapaneseDate.kanjiExtended(summary.roundsCompleted) + "巡"
            summary.totalReps > 0 -> JapaneseDate.kanjiExtended(summary.totalReps) + "回"
            else -> null
        },
        duration = heroTime(summary.activeMs),
        chip = partial ?: prChip(summary, isStillBest = true)?.label,
        partial = partial != null,
    )
}

/**
 * The estimate line's inputs, taken from the **stored** projection and never recomputed.
 *
 * §3 edge case 9 says it outright for the index card and the reasoning is identical here: a
 * `routine_version` is immutable, so its `est_duration_sec` cannot go stale, and re-deriving it per
 * frame would walk the station list to reproduce a number the writer already agreed on.
 *
 * What the projection does *not* carry is [RoutineEstimate.approximate], which is a fact about how
 * the number was reached rather than about the routine — so it is re-derived here, cheaply, from the
 * two things that make an estimate a guess: a 限界まで station (there is nothing to estimate) and an
 * exercise the catalogue no longer resolves (there is no pace, and `RoutineEstimate`'s doctrine
 * forbids inventing one). Either flips the 目安 suffix.
 */
internal fun snapshotEstimate(snapshot: RoutineSnapshot): RoutineEstimate = RoutineEstimate(
    durationSeconds = snapshot.estimatedDurationSeconds,
    totalReps = snapshot.estimatedTotalReps,
    approximate = snapshot.stations.any { it.measure == Measure.MAX_EFFORT || it.exercise == null },
)

/**
 * Whether the record behind a 最高 tile was set **this calendar month** — §3 edge case 7's `c.accent`.
 *
 * Matched by label rather than by re-deriving which metric the engine scores on: [bestMetricLabel] is
 * injective over the metrics that have one, and duplicating `bestTilesFor`'s engine table here is how
 * the two would eventually disagree about an engine nobody re-read.
 *
 * The やった回数 tile has no date and therefore never accents. That is correct rather than a gap: it
 * is a lifetime count, and a lifetime count is not something that happened this month.
 */
internal fun bestTileIsThisMonth(tile: BestTile, bests: List<RoutineBest>, today: LocalDate): Boolean =
    bests.any { best ->
        bestMetricLabel(best.metric) == tile.label &&
            best.localDate.year == today.year &&
            best.localDate.month == today.month
    }

/** 時間内 ・ 中級 — the header subtitle. A user routine has no tier until `derivedTier` guesses one. */
internal fun detailSubtitle(engine: Engine, tier: Tier?, strings: Strings): String =
    listOfNotNull(engine.label, tier?.label(strings)).joinToString(" ・ ")

/**
 * 決めた時間で何巡できるか — the one engine whose name needs explaining, on the one page that explains it.
 *
 * §6 :1118 attaches this gloss to `AMRAP` and marks it "on the detail page", so this is the page that
 * owes it. It is not a general engine-description slot: no other engine has a documented gloss, and
 * writing one per engine would be seven invented sentences under a table that supplied one.
 *
 * Exhaustive with no `else` so a future engine has to be classified rather than silently glossless.
 */
internal fun detailEngineGloss(engine: Engine): String? = when (engine) {
    Engine.AMRAP -> "決めた時間で何巡できるか"
    Engine.INTERVAL_CIRCUIT,
    Engine.FIXED_SETS,
    Engine.EMOM,
    Engine.EMOM_ASCENDING,
    Engine.FOR_TIME,
    Engine.FOR_TIME_WITH_REST,
    -> null
}

/**
 * The name in the header, including during the Loading window §3 asks to cover ("title from the nav
 * arg to avoid a flash").
 *
 * The nav arg is an id, not a name, so the flash can only be covered by a name somebody else already
 * read — the library list. It is passed in as a [Loadable] and read through `valueOrNull`, which is
 * the honest degradation: an unread library contributes nothing and the header simply has no title
 * yet. **A placeholder would be this page inventing the one fact it exists to report.**
 *
 * Two things this deliberately does not do. It does not fall back to the id — a routine called
 * `r_cindy` is worse than no title. And it does not cover the *archived* case: `repository.routines`
 * is `WHERE archived_at IS NULL`, so a routine entered from a session's 型を見る has no name here until
 * `routineDetail` lands. Closing that needs a last-known-name written by `openRoutine` (a `GymViewModel`
 * field, not this page's), and the alternative — reading the archived list too — would make the header
 * of a live routine depend on a second query that usually returns nothing.
 */
internal fun detailHeaderTitle(
    detail: Loadable<RoutineDetail>,
    library: Loadable<List<RoutineSummary>>,
    routineId: String,
): String? = detail.valueOrNull()?.snapshot?.name
    ?: library.valueOrNull()?.firstOrNull { it.routineId == routineId }?.name

/**
 * 第七段 ・ 七 六 五 四 四 — the ladder block, or the fault that stopped it, or silence.
 *
 * Three outcomes and not two, which is the whole point of the function existing. The block used to be
 * `stepFor(progression.valueOrNull())?.let { … }`, and that made `Loading` and `Failed` render the
 * identical zero pixels: リーコン・ロン with an unreadable `progression_state` looked exactly like a
 * routine that has no ladder at all, while 第七段 ・ 十八段のうち is that routine's central content
 * (§3 edge case 5). Loading may be silent — the read is one query away from answering. Failed may not:
 * `GYM.LIBRARY.DETAIL`'s own `AttemptsError` state and `EXERCISE_DETAIL`'s `PbFailed` both require a
 * visible 記録を読めません ・ もう一度 for exactly this shape of partial failure, and an omission is a
 * claim that there is nothing there.
 *
 * [ProgressionBlock.Silent] also absorbs the two structural cases — a non-`FIXED_SETS` engine, and a
 * `FIXED_SETS` routine with no `progression_program_id` — so the composable has one `when` and no
 * nested `if`s. `stepFor` returning null keeps its own meaning (a state with nothing renderable in it)
 * and stays silent, as its KDoc promises.
 */
internal sealed interface ProgressionBlock {

    /** Nothing to draw, and nothing wrong: no ladder, or the read has not answered yet. */
    data object Silent : ProgressionBlock

    data class Step(val line: String, val caption: String) : ProgressionBlock

    /** The ladder could not be read. Never silence — that would read as "this routine has no steps". */
    data class Unreadable(val fault: TempoFault) : ProgressionBlock
}

internal fun progressionBlock(
    engine: Engine,
    programId: String?,
    progression: Loadable<ProgressionState>,
): ProgressionBlock = when {
    engine != Engine.FIXED_SETS || programId == null -> ProgressionBlock.Silent
    progression is Loadable.Failed -> ProgressionBlock.Unreadable(progression.fault)
    progression is Loadable.Ready -> stepFor(progression.value)
        ?.let { ProgressionBlock.Step(line = it.line, caption = it.caption) }
        ?: ProgressionBlock.Silent

    else -> ProgressionBlock.Silent
}

/**
 * The centred actions at the foot, in §3's mock order, each with whether it can be pressed.
 *
 * **Archived greys, built-in omits — and the difference is the spec's, not a shortcut.** §1 rule 3 and
 * §3's `Archived` state both say 始める / 編集 / よく使う are *disabled*, while §3's `BuiltIn` state says
 * 編集 and 削除 are *absent entirely*. The reason the two differ is worth the sentence: an action that
 * will never be available on a built-in is chrome, but an action that is available again the moment the
 * user taps 元に戻す is information — dropping it hides the fact that this routine could be edited.
 *
 * 写して作る is the one action that stays live on an archived routine (§1 rule 3), which is also why it
 * is the row that has to carry the name the page already holds — see the call site.
 *
 * Returned as data rather than drawn inline so the archived and built-in shapes are assertable without
 * Compose; this is the case the review found the file getting wrong with no comment either way.
 */
internal enum class DetailActionKind { Duplicate, Favourite, Edit, Delete, Restore }

internal data class DetailAction(val label: String, val kind: DetailActionKind, val enabled: Boolean)

internal fun detailActions(builtIn: Boolean, archived: Boolean, favourite: Boolean): List<DetailAction> =
    buildList {
        add(DetailAction("写して作る", DetailActionKind.Duplicate, enabled = true))
        add(DetailAction(detailFavouriteLabel(favourite), DetailActionKind.Favourite, enabled = !archived))
        if (!builtIn) {
            add(DetailAction("編集", DetailActionKind.Edit, enabled = !archived))
            if (archived) {
                add(DetailAction("元に戻す", DetailActionKind.Restore, enabled = true))
            } else {
                add(DetailAction("削除", DetailActionKind.Delete, enabled = true))
            }
        }
    }

/**
 * Whether *this* detail page is the one that should draw the resume prompt.
 *
 * The prompt is a modal over whichever page is beneath it (`00-plan.md` §2 row 8), and both `GYM.HOME`
 * and this page mount [ResumePromptHost]. `GymShell` swaps routes through an `AnimatedContent`, so for
 * the ~280ms of a pop both pages are composed at once and both hosts read the same non-null state —
 * two `AlertDialog`s, two scrims, the question announced twice.
 *
 * The top of the stack is the page the user is actually looking at, so it is the one that owns the
 * modal. This is deliberately **not** a filter on "did this page raise it": a prompt raised over HOME
 * and navigated away from unanswered is still unanswered, and this page must ask it again when it
 * arrives rather than leave a question the user has to go back to find.
 *
 * The right end state is a single host hoisted into `GymShell`, above the `AnimatedContent`, and both
 * page mounts deleted — that is a three-file change no page unit owns, and it is flagged in this unit's
 * report. This guard is correct on its own and stays correct after that move (the page below the shell's
 * host would simply never mount one).
 */
internal fun detailOwnsResumePrompt(stack: List<GymRoute>, routineId: String): Boolean =
    (stack.lastOrNull() as? GymRoute.RoutineDetail)?.routineId == routineId

/** よく使うに入れる / よく使うから外す (§6). The label states what the tap will do, not the state. */
internal fun detailFavouriteLabel(favourite: Boolean): String =
    if (favourite) "よく使うから外す" else "よく使うに入れる"

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * 型の中身 — everything about one routine on one page, including starting it.
 *
 * @param routineId the **nav argument**, which is the routine the user tapped. What is rendered may
 *   be a different id: a scaled tier is a *separate stored routine* (`04-library-records.md` §1
 *   rule 6), so choosing やさしい changes the id this page reads rather than applying a multiplier.
 *   The nav arg stays the key the choice is remembered under, so re-entering restores it.
 */
@Composable
fun LibraryDetailScreen(gym: GymViewModel, routineId: String, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val tierChoice by gym.selectedTier.collectAsStateWithLifecycle()
    val renderedId = tierChoice[routineId] ?: routineId

    val detail by gym.routineDetail(renderedId).collectAsStateWithLifecycle()
    val tiers by gym.scaledTiers(routineId).collectAsStateWithLifecycle()
    // Collected, not `.value`-read: a `StateFlow`'s `.value` inside composition is not a snapshot read,
    // so a library list that resolved one frame later would never repaint the header it was there to
    // fill (§3's "title from the nav arg to avoid a flash").
    val library by gym.routines.collectAsStateWithLifecycle()
    // A `Loadable`, and it stays one all the way into `detailDeleteCopy`: "unread" and "never
    // performed" are different answers, and only one of them may offer 完全に削除.
    val sessions by gym.countForRoutine(renderedId).collectAsStateWithLifecycle()
    val stack by gym.stack.collectAsStateWithLifecycle()
    val startInFlight by gym.startInFlight.collectAsStateWithLifecycle()
    val writeFault by gym.writeFault.collectAsStateWithLifecycle()

    var confirmingDelete by remember(renderedId) { mutableStateOf(false) }

    // §3 edge case 1: the routine was purged by another surface while this page was open. The flow is
    // the source of truth and the page never caches a stale copy — so it leaves, with no dialog. A
    // もう一度 here could not succeed (`DECISIONS.md` §Q6 gives RoutineGone no action), which is the
    // `CalendarFault.EventGone → cancelCompose()` precedent.
    val gone = (detail as? Loadable.Failed)?.fault == GymFault.RoutineGone
    LaunchedEffect(gone) {
        if (gone && gym.stack.value.size > 1) gym.onBack()
    }

    Column(modifier.fillMaxSize()) {
        DetailHeader(
            title = detailHeaderTitle(detail, library, renderedId),
            subtitle = detail.valueOrNull()?.let { detailSubtitle(it.snapshot.engine, it.tier, s) },
            gloss = detail.valueOrNull()?.let { detailEngineGloss(it.snapshot.engine) },
            archived = detail.valueOrNull()?.archived == true,
            onClose = { if (gym.stack.value.size > 1) gym.onBack() },
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            val ready = detail
            when {
                // Loading is not empty is not failed (`00-plan.md` §4.1 rule 1). Three branches, three
                // composables, and まだ やっていません is reachable from none of them.
                ready is Loadable.Failed -> FaultPanel(fault = ready.fault, onRecover = gym::retry)
                ready is Loadable.Loading -> DetailNotice("読み込み中")
                ready is Loadable.Ready -> DetailBody(
                    gym = gym,
                    baseRoutineId = routineId,
                    detail = ready.value,
                    tiers = tiers,
                    starting = startInFlight != null,
                    writeFault = writeFault,
                    onDelete = { confirmingDelete = true },
                )
            }
        }
    }

    // The name is read from the same flow the page draws from, so a routine that disappeared while the
    // confirm was open takes its own dialog with it — there is nothing left to ask about.
    val deleteName = detail.valueOrNull()?.snapshot?.name
    if (confirmingDelete && deleteName != null) {
        val state = detailDeleteCopy(deleteName, sessions)
        DetailDeleteDialog(
            state = state,
            // `purge` comes from the state that chose the words, never from a second look at the count:
            // a count resolving between the sentence and the tap would otherwise turn a promise that
            // 「記録は残ります」 into the irreversible branch.
            onConfirm = { purge ->
                confirmingDelete = false
                // `thenSelect` is the caller's: the deleted routine's own page is on top of the stack
                // here and has to be popped (`04` §2), while HOME's long-press row stays put.
                if (purge) {
                    gym.purgeRoutine(renderedId, GymTab.Library)
                } else {
                    gym.archiveRoutine(renderedId, GymTab.Library)
                }
            },
            onRetry = gym::retry,
            onDismiss = { confirmingDelete = false },
        )
    }

    // 始める's **start guard** (`03-player.md` §A.1): an open session raises つづき *instead of*
    // starting, and the start proceeds by itself once 記録する or 捨てる has resolved the old one. It is
    // not a refusal and there is nothing for this page to do about it beyond drawing the question —
    // [ResumePromptHost] is the shared wiring `GYM.HOME` and this page both mount, so neither knows
    // that 記録する is `finishSession(complete = false)`.
    //
    // Mounted only while this page is the top of the stack, and not filtered on `pendingStart`: a
    // prompt raised over HOME and navigated away from unanswered is still unanswered, and hiding it
    // here would leave a question the user has to go back to find. What the guard removes is the
    // *doubled* dialog — `GymShell`'s `AnimatedContent` keeps the outgoing and incoming pages composed
    // together, so an unguarded mount here and HOME's stack two scrims over the transition.
    // [detailOwnsResumePrompt] carries the rest of the argument.
    if (detailOwnsResumePrompt(stack, routineId)) ResumePromptHost(gym)
}

/**
 * The routine's name over its engine and tier, with とじる pinned to the top.
 *
 * `Alignment.Top` and `weight(1f)` are `CalendarScreen.kt:102`'s: a two-line user routine name must
 * push the action nowhere (§3 edge case 8). Title, subtitle and gloss are one node, because a name and
 * its engine read as two orphan fragments when announced apart.
 *
 * **§3 edge case 9 — 「この記録のときとは 中身が変わっています」 — is deliberately not here, and this is
 * the only place that says so.** The line belongs under this header when the page was entered from a
 * session whose pinned `routine_version` is not the head. Two things are missing and neither is this
 * file's: `GymRoute.RoutineDetail` carries a `routineId` and no `routineVersionId`, so the page cannot
 * know which version the caller was looking at; and `snapshotDiffers` (§3's pure-logic list, via
 * `structuralHash`) is unwritten. Rendering the line whenever *any* newer version exists was rejected —
 * a rename bumps no `structural_hash` and would make the page claim the workout changed when it did
 * not, and §3's own sentence is that the page "never re-renders the past from the present".
 */
@Composable
private fun DetailHeader(
    title: String?,
    subtitle: String?,
    gloss: String?,
    archived: Boolean,
    onClose: () -> Unit,
) {
    val c = LocalTempoColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier
                    .weight(1f, fill = true)
                    .semantics(mergeDescendants = true) { heading() },
            ) {
                // Null only while the very first read is in flight for a routine the library list has
                // not seen either. An empty header is the honest shape: a placeholder name would be
                // this page inventing the one fact it exists to report.
                if (title != null) {
                    Text(
                        text = title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                    )
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = subtitle,
                            style = TextStyle(
                                fontFamily = Mincho,
                                fontSize = 13.sp,
                                letterSpacing = 4.sp,
                                color = c.inkFaint,
                            ),
                        )
                        if (archived) {
                            Text(
                                text = "削除済み",
                                style = TextStyle(
                                    fontFamily = Mincho,
                                    fontSize = 13.sp,
                                    letterSpacing = 4.sp,
                                    color = c.inkSoft,
                                ),
                            )
                        }
                    }
                }
                // 決めた時間で何巡できるか — §6 :1118 marks this gloss "on the detail page", and 時間内
                // is the one engine label that does not explain itself. Under the subtitle rather than
                // beside it: it is a sentence, not a chip.
                if (gloss != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = gloss,
                        style = TextStyle(
                            fontFamily = Gothic,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = c.inkFaint,
                        ),
                    )
                }
            }
            HeaderAction(label = "とじる", description = "とじる", color = c.inkFaint, onClick = onClose)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}

/**
 * The scrolling body, in the order §3's mock draws it.
 *
 * The order is not arbitrary and is worth one sentence: the button the user came for is first, what
 * they are about to do is next, how they have done at it is under that, and the four actions that
 * change the routine are last — the page is read top to bottom exactly once and edited rarely.
 */
@Composable
private fun DetailBody(
    gym: GymViewModel,
    baseRoutineId: String,
    detail: RoutineDetail,
    tiers: List<RoutineSummary>,
    starting: Boolean,
    writeFault: GymFault?,
    onDelete: () -> Unit,
) {
    val c = LocalTempoColors.current
    val block = startBlock(detail)
    val snapshot = detail.snapshot

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
    ) {
        // A failed write states itself where it happened — a start that never landed, a favourite that
        // did not stick — through the existing chrome (`00-plan.md` §4.1, `DECISIONS.md` §Q6). It sits
        // above 始める because that is the write it most often belongs to.
        if (writeFault != null) {
            Spacer(Modifier.height(14.dp))
            FaultStrip(
                fault = writeFault,
                onRecover = { gym.dismissWriteFault(); gym.retry() },
            )
        }

        Spacer(Modifier.height(18.dp))
        StartButton(
            name = snapshot.name,
            starting = starting,
            block = block,
            onStart = { gym.startSession(detail.routineId) },
        )
        startBlockCopy(block)?.let { reason ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = reason,
                style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
            )
        }

        // §3 edge case 2: an archived scaling is not offered. A single chip is not a choice, so the row
        // appears only when there is something to choose between.
        val choices = tiers.filterNot { it.archived }
        if (choices.size > 1) {
            Spacer(Modifier.height(16.dp))
            TierChips(
                choices = choices,
                selectedId = detail.routineId,
                onSelect = { gym.selectTier(baseRoutineId, it) },
            )
        }

        SectionHeading("組み立て")
        snapshot.stations.forEachIndexed { index, station ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
            StationRow(
                position = index + 1,
                station = station,
                onClick = station.exercise?.let { { gym.go(GymRoute.ExerciseDetail(it.id)) } },
            )
        }

        engineRows(snapshot).forEach { row -> ReadOnlyRow(label = row.label, value = row.value) }

        // §3 edge case 5: a ladder renders the rung you are on, never all eighteen. The step comes from
        // `progression_state`; an unread one never claims 第一段, because "which step am I on" is a fact
        // about the user and not a default — but a *failed* read says so out loud rather than drawing
        // the same nothing a laddlerless routine draws. [progressionBlock] holds that argument.
        val programId = snapshot.progressionProgramId
        val progression = if (snapshot.engine == Engine.FIXED_SETS && programId != null) {
            remember(programId) { gym.repository.progression(programId) }
                .collectAsStateWithLifecycle(initialValue = Loadable.Loading)
                .value
        } else {
            Loadable.Loading
        }
        when (val ladder = progressionBlock(snapshot.engine, programId, progression)) {
            ProgressionBlock.Silent -> Unit

            is ProgressionBlock.Step -> {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = ladder.line,
                    style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ladder.caption,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 1.sp, color = c.inkFaint),
                )
            }

            is ProgressionBlock.Unreadable -> {
                Spacer(Modifier.height(14.dp))
                FaultStrip(fault = ladder.fault, onRecover = gym::retry)
            }
        }

        val estimate = estimateLabel(snapshot.engine, snapshotEstimate(snapshot))
        if (estimate.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = estimate,
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.inkSoft),
            )
        }

        // §3 edge case 3: no origin, no block. Never `出典 —`, which would say the provenance is
        // missing rather than that the user wrote this one themselves.
        detail.origin?.let { origin ->
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "出典",
                    style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
                )
                Text(
                    text = origin,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 1.sp, color = c.inkFaint),
                )
            }
        }

        val tiles = bestTilesFor(snapshot.engine, detail.bests, detail.timesDone)
        if (tiles.isNotEmpty()) {
            SectionHeading("最高")
            BestTiles(tiles = tiles, bests = detail.bests)
        }

        AttemptsSection(
            attempts = detail.recentAttempts,
            onSeeAll = { gym.go(GymRoute.History(routineId = detail.routineId)) },
            onAttempt = { gym.go(GymRoute.Record(it.key)) },
        )

        // Which rows exist and which are greyed is [detailActions]', not this composable's: §3's
        // `BuiltIn` state omits 編集/削除 entirely while its `Archived` state *disables* 編集/よく使う,
        // and the file used to drop both, which is the built-in rule applied to a state that does not
        // share its reason. §1 rule 5's 編集-becomes-写して作る is the *index*'s long-press behaviour,
        // where the menu has no room to explain itself; here 写して作る is already its own row.
        Spacer(Modifier.height(18.dp))
        detailActions(builtIn = detail.builtIn, archived = detail.archived, favourite = detail.favourite)
            .forEach { action ->
                CentredAction(label = action.label, enabled = action.enabled) {
                    when (action.kind) {
                        // The name is passed because an archived routine is not in `repository.routines`
                        // and 写して作る is the action §1 rule 3 keeps enabled there — without it the tap
                        // answered 「この型は削除されています」 and copied nothing.
                        DetailActionKind.Duplicate ->
                            gym.duplicateRoutine(detail.routineId, detail.snapshot.name)

                        DetailActionKind.Favourite ->
                            gym.setFavourite(detail.routineId, !detail.favourite)

                        DetailActionKind.Edit -> gym.go(GymRoute.Builder(detail.routineId))
                        DetailActionKind.Delete -> onDelete()
                        DetailActionKind.Restore -> gym.restoreRoutine(detail.routineId)
                    }
                }
            }

        Spacer(Modifier.height(96.dp))
    }
}

/**
 * 始める — the one primary button in the library, and the only place a session is born.
 *
 * Three faces, and the disabled one is deliberately not a fourth colour: `c.inkFaint` on the same
 * card is what every disabled control in this app looks like. The in-flight face is **支度**, from
 * `03-player.md` §A — an insert is sub-millisecond and a spinner would flash, so the label changes
 * and the button stops taking taps. Re-entrancy is `startInFlight`'s and `idx_session_live`'s; this
 * end merely stops asking.
 *
 * The description is `「シンディ」を始める` (§6), never the bare word: by the time a user reaches this
 * button by touch exploration the title has scrolled off, and 始める alone does not say what. It is
 * [startButtonDescription]'s in every face — the *visible* label changes, the sentence does not — and
 * a disabled face carries `disabled()`, as `PromptOption` does, so TalkBack stops offering it.
 */
@Composable
private fun StartButton(name: String, starting: Boolean, block: StartBlock, onStart: () -> Unit) {
    val c = LocalTempoColors.current
    val enabled = !starting && block == StartBlock.None
    val label = if (starting) "支度" else "始める"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .clickable(enabled = enabled, onClick = onStart)
            .semantics {
                role = Role.Button
                contentDescription = startButtonDescription(name, starting, block)
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 20.sp,
                letterSpacing = 4.sp,
                color = if (enabled) c.accent else c.inkFaint,
            ),
        )
    }
}

/**
 * やさしい / 基本 — the scalings of this routine, as radio buttons.
 *
 * The label is each routine's **own name**, not a tier word. `04-library-records.md` §1 rule 6 makes
 * a scaling a separate stored routine, so what distinguishes them is what the seed or the user called
 * them; mapping a name back onto a `ScalingTier` would need a column no schema has, and inventing one
 * per chip would put an unsourced word under the user's thumb.
 *
 * `Role.RadioButton` and a `selectableGroup`, per §3's accessibility note — one selected item out of
 * several is exactly what a radio group announces, and it carries "選択中" without a hand-written
 * `stateDescription`.
 */
@Composable
private fun TierChips(choices: List<RoutineSummary>, selectedId: String, onSelect: (String) -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { choice ->
            val selected = choice.routineId == selectedId
            Box(
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice.routineId) },
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = choice.name,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        color = if (selected) c.accent else c.inkFaint,
                    ),
                )
            }
        }
    }
}

/**
 * One station: 一 ・ 懸垂 ・ 五回 ・ ›.
 *
 * A routine that references an exercise the catalogue no longer knows **still lists**, as 不明な種目
 * (§6), and its row does not lead anywhere — there is no page for a movement nobody has. 始める is
 * disabled above with its own reason, which is where a user can act on it.
 *
 * The long list this page is warned about (`previewRows`, §3) is already collapsed by the data: a
 * snapshot's stations are the *prescription*, one row each, and the compiled timeline — マーフ's
 * hundred pull-ups as a hundred segments — never reaches this page.
 */
@Composable
private fun StationRow(position: Int, station: RoutineStation, onClick: (() -> Unit)?) {
    val c = LocalTempoColors.current
    val name = station.exercise?.nameJa ?: "不明な種目"
    val prescription = prescriptionLabel(station)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .clearAndSetSemantics {
                contentDescription = stationRowSemantics(position, name, prescription)
                if (onClick != null) role = Role.Button
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = JapaneseDate.kanjiExtended(position),
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
        )
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
            style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.ink),
        )
        Text(
            text = prescription,
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
        )
        if (onClick != null) {
            Text(
                text = "›",
                modifier = Modifier.clearAndSetSemantics { },
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkFaint),
            )
        }
    }
}

/**
 * 制限時間 / 二十分 — a `PickerRow` that cannot be picked.
 *
 * Shaped exactly like `EventComposeScreen`'s row so the two read as the same kind of statement, minus
 * the tap: this page is read-only and a row that looks tappable and is not is worse than one that
 * looks like text. Which rows exist at all is `engineRows`', not this composable's — §3 edge case 4.
 */
@Composable
private fun ReadOnlyRow(label: String, value: String) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clearAndSetSemantics { contentDescription = "$label、$value" }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        Text(
            text = value,
            style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.ink),
        )
    }
}

/**
 * 最高 — up to three tiles, read **label first**.
 *
 * "十七巡、最高巡数" reads as a fragment; "最高巡数、十七巡" is a sentence, which is §3's accessibility
 * note and why the semantics string is not the visual order.
 *
 * A record set this month draws `c.accent` (§3 edge case 7). Nothing about the tile is otherwise
 * conditional — `bestTilesFor` **fails open** and emits やった回数 whenever the count is positive even
 * when no metric tile resolves (`DECISIONS.md` §Q9), and no guard here may re-close it.
 */
@Composable
private fun BestTiles(tiles: List<BestTile>, bests: List<RoutineBest>) {
    val c = LocalTempoColors.current
    val today = remember { LocalDate.now() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { tile ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = tile.label + "、" + tile.value }
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tile.value,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 22.sp,
                        color = if (bestTileIsThisMonth(tile, bests, today)) c.accent else c.ink,
                    ),
                )
                Text(
                    text = tile.label,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 1.sp, color = c.inkFaint),
                )
            }
        }
    }
}

/**
 * これまで — the last five outings, or まだ やっていません.
 *
 * The empty case is a *state of this routine*, not of the store: `recentAttempts` arrives inside the
 * same `RoutineDetail` the page already proved it could read, so an empty list here can only mean
 * "never done". That is why §3's `NoAttempts` may say まだ やっていません where an unreadable store
 * never may (`00-plan.md` §4.1 rule 1).
 *
 * §3 also names an `AttemptsError` state — the routine renders and only this block reports a fault.
 * It is unreachable against the shipped data shape and is **deferred**: attempts are a field of
 * `RoutineDetail`, so a history read failing takes the whole page's `Loadable` with it. Reinstating
 * the state needs `attemptsForRoutine` read separately, which is a repository call this page could
 * make but a state nobody could currently produce.
 */
@Composable
private fun AttemptsSection(
    attempts: List<SessionSummary>,
    onSeeAll: () -> Unit,
    onAttempt: (SessionSummary) -> Unit,
) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "これまで",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        if (attempts.isNotEmpty()) {
            HeaderAction(label = "すべて見る", description = "すべて見る", color = c.accent, onClick = onSeeAll)
        }
    }
    if (attempts.isEmpty()) {
        Text(
            text = "まだ やっていません",
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    } else {
        attempts.forEach { attempt -> AttemptRow(attempt = attempt, onClick = { onAttempt(attempt) }) }
    }
}

/** 六月十七日 ・ 十七巡 ・ 二十分 ・ 自己最高 — one outing, one node, one tap into its record. */
@Composable
private fun AttemptRow(attempt: SessionSummary, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val line = remember(attempt) { attemptLine(attempt) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = listOfNotNull(line.date, line.score, line.duration, line.chip)
                    .joinToString("、")
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = line.date,
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
        )
        if (line.score != null) {
            Text(
                text = line.score,
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
            )
        }
        Text(
            text = line.duration,
            modifier = Modifier.weight(1f, fill = true),
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
        )
        if (line.chip != null) {
            // 途中まで is `c.inkFaint` and 自己最高 is `c.accent`: a session that ended early is
            // recorded with dignity, not decorated (`00-plan.md` §4.1 rule 2).
            Text(
                text = line.chip,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    color = if (line.partial) c.inkFaint else c.accent,
                ),
            )
        }
    }
}

/** A section label: 組み立て, 最高. `heading()` so TalkBack can jump between them. */
@Composable
private fun SectionHeading(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).semantics { heading() },
        style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
    )
}

/**
 * One of the centred actions at the foot: 写して作る / よく使う… / 編集 / 削除 / 元に戻す.
 *
 * [enabled] draws `c.inkFaint` and carries `disabled()`, which is `PromptOption`'s idiom and every
 * disabled control's colour in this app — an archived routine's 編集 and よく使う are *greyed*, not
 * dropped (§1 rule 3, §3's `Archived` state), and a greyed row that TalkBack still announces as an
 * available button is the half-fix that idiom exists to prevent.
 */
@Composable
private fun CentredAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = if (enabled) c.accent else c.inkFaint,
            ),
        )
    }
}

/** 読み込み中 in the body slot, with the header above it already drawn. */
@Composable
private fun DetailNotice(text: String) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * The 削除 confirm, whose words — and whose *existence of a confirm button at all* — [detailDeleteCopy]
 * chose.
 *
 * **The destructive button is never the initially focused one** (§3, accessibility): やめる takes
 * focus on appearance. A confirm dialog whose first focus stop destroys something is a dialog that
 * can be dismissed destructively by a user who has learned that the first button is "go away".
 *
 * **Two of the three states have no confirm button at all.** Not a disabled one: a greyed 削除 invites
 * the user to wait for it to light up, and in the [DetailDeleteState.Unreadable] case it never will
 * until the store is readable again — which is what the strip's もう一度 is for. `AlertDialog` requires
 * the slot, so it is filled with nothing, which is the same shape `FaultPanel` uses when a fault has no
 * action (`DECISIONS.md` §Q6).
 */
@Composable
private fun DetailDeleteDialog(
    state: DetailDeleteState,
    onConfirm: (purge: Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = state.title,
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            when (state) {
                is DetailDeleteState.Confirm -> DialogBody(state.body)
                is DetailDeleteState.Waiting -> DialogBody(state.body)
                is DetailDeleteState.Unreadable -> FaultStrip(fault = state.fault, onRecover = onRetry)
            }
        },
        confirmButton = {
            if (state is DetailDeleteState.Confirm) {
                TextButton(onClick = { onConfirm(state.purge) }) {
                    Text(state.confirm, style = TextStyle(fontFamily = Mincho, color = c.accent))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus)) {
                Text(state.cancel, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}

/** The confirm's sentence, in the one style both the branch copy and 読み込み中 are drawn in. */
@Composable
private fun DialogBody(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        style = TextStyle(
            fontFamily = Mincho,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.5.sp,
            color = c.inkFaint,
        ),
    )
}
