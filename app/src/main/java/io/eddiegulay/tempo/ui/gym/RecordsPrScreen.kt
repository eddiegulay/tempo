package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.gym.BestRowCopy
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.MovementBest
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.gym.bestMetricFor
import io.eddiegulay.tempo.gym.bestMetricLabel
import io.eddiegulay.tempo.gym.bestValueCopy
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/*
 * GYM.RECORDS.PR — 最高. `04-library-records.md` §4's third records page.
 *
 * Every best the user holds, split by **the two things that can improve**: a routine, and a movement.
 * That split is the page, and it is why there are two tabs rather than one list — a 最高負荷 on 七分間
 * and a 三十二回 on 腕立て伏せ are not comparable, not sortable together, and not reached by the same
 * kind of work.
 *
 * **Data out — none.** §4 says so in one line and it has a consequence the code has to honour: a
 * routine row navigates with `gym.go(GymRoute.RoutineDetail(...))` and **not** with
 * `GymViewModel.openRoutine`, which bumps `touchRoutine`'s recency counter. That bump is `GYM.HOME`'s
 * and the library index's — tapping a card there *is* reaching for the routine — but reading your own
 * record for シンディ must not quietly promote it into よく使う. A read-only page that writes is a page
 * whose spec line is false.
 *
 * Everything above the composables is pure and JUnit-tested in `test/ui/gym/RecordsPrCopyTest.kt`
 * (`ORCHESTRATION.md`'s hard constraint 6 — pure logic carries JUnit tests; `00-plan.md` §4.1 has seven
 * rules and none of them is that one), and the structural rules that no return value can express — the
 * missing write, the absent `stickyHeader`, the four unshared states — are in
 * `test/ui/gym/RecordsPrAndChartsStructureTest.kt`.
 *
 * **Nothing here formats a best.** `bestValueCopy` already does, calling `EngineRows`'
 * `bestMetricLabel` / `bestValueLabel` so the four words 最速 / 最高巡数 / 最高反復 / 最高負荷 live in one
 * table (`DECISIONS.md` §Q7's rule, applied). This file decides *which row* out of a routine's stored
 * records is the one to print, and what a movement's three lines say — nothing else.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure copy and shaping — Android-free
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** §6: `PR tabs | 型ごと / 動きごと`. The declaration order is the display order, 型ごと first. */
enum class PrTab(val label: String) {
    Routines("型ごと"),
    Movements("動きごと"),
    ;

    companion object {
        /** §4: *back — straight pop; **the tab selection resets to 型ごと***. */
        val Default: PrTab = Routines
    }
}

/**
 * One 型ごと row: the record it prints, and the three ids the three taps need.
 *
 * The [RoutineBest] rides along rather than being flattened into the copy because the row has three
 * destinations and [BestRowCopy] carries none of them — it is words, deliberately, so that the same
 * words can be tested without a navigation graph.
 */
internal data class PrRoutineRow(
    val routineId: String,
    val sessionId: Long,
    val achievedAt: Long,
    val copy: BestRowCopy,
)

/**
 * A routine's stored records reduced to **one row per routine**, most recent first.
 *
 * `personal_record` holds a row per metric, so an AMRAP has two — `MOST_ROUNDS` and the `MOST_REPS`
 * that rides along with it (`metricsFor`) — and printing both would give シンディ two rows on a page
 * whose whole shape is "one line per thing you are good at". §4 edge case 2 says which one survives:
 * **the metric is engine-determined**, which is [bestMetricFor]'s question.
 *
 * *Rejected* — `bests.filter { it.metric == bestMetricFor(it.engine) }`, the obvious one-liner. It
 * deletes a routine from the page outright when its engine was changed after the record was set: the
 * stored row then carries a metric the current engine does not score, matches nothing, and シンディ
 * silently vanishes from 最高 rather than keeping the record she set. §4 edge case 4's whole stance is
 * that an edited routine **keeps its old best** and the honest amount to say about it is
 * 中身が変わっています — not to hide it. So the engine's metric is a *preference*, and a routine with no
 * matching row falls back to its most recent record instead.
 *
 * A record whose metric has no Japanese label is dropped, and that is `HIGHEST_STEP` and nothing else
 * (`DECISIONS.md` §Q9: no label exists and none is to be invented). The fallback picks the most recent
 * **labellable** record for exactly that reason — falling back to an unprintable one would drop the
 * routine after all the trouble taken above.
 *
 * **Sorted by `achievedAt` descending, explicitly.** §4's `Ready` state asks for it — *a bests page
 * should show momentum, not an alphabet* — and `GymStore.readBests` already orders that way, but the
 * grouping above chooses a row per routine that need not be the group's newest, so the order has to be
 * re-established over what was chosen rather than inherited from what arrived.
 */
internal fun prRoutineRows(bests: List<RoutineBest>): List<PrRoutineRow> =
    bests.groupBy { it.routineId }
        .values
        .mapNotNull { records ->
            val labellable = records.filter { bestMetricLabel(it.metric) != null }
            val preferred = labellable.firstOrNull { it.metric == bestMetricFor(it.engine) }
            val chosen = preferred ?: labellable.maxByOrNull { it.achievedAt } ?: return@mapNotNull null
            val copy = bestValueCopy(chosen) ?: return@mapNotNull null
            PrRoutineRow(
                routineId = chosen.routineId,
                sessionId = chosen.sessionId,
                achievedAt = chosen.achievedAt,
                copy = copy,
            )
        }
        .sortedByDescending { it.achievedAt }

/**
 * One 動きごと row, in the three lines §4's mock draws.
 *
 * ```
 * 腕立て伏せ                            三十二回     ← name / value
 * 一度に ・ のべ 四百回                              ← meta
 * 六月十日                    いちばん上 足上げ腕立て ← date / hardest rung
 * ```
 */
internal data class PrMovementRow(
    val exerciseId: String,
    val name: String,
    /** 三十二回 — the best **single set**, which is the number this row is about. */
    val value: String,
    /** 一度に ・ のべ 四百回 — what the value is, and the lifetime total beside it. */
    val meta: String,
    /** 六月十日, or null when the store has no date for the family. Never `—` on a bare line. */
    val date: String?,
    /** いちばん上 足上げ腕立て, or null when the row's own movement *is* the hardest rung reached. */
    val hardest: String?,
    /** The whole row as one node. */
    val semantics: String,
)

/**
 * `04-library-records.md` §6: *PR: hardest reached | `いちばん上` | `いちばん上 足上げ腕立て`*.
 *
 * **This surface's word, and only this surface's.** `DECISIONS.md` §Q9 is explicit that いちばん上 is
 * the movement ladder's hardest rung and "is not a routine tile and must not be repurposed as one" —
 * it was the string リーコン・ロン's `HIGHEST_STEP` tile was nearly built out of. A routine's step
 * belongs to `stepFor`, as 第九段 / 十八段のうち.
 */
private const val HARDEST_REACHED = "いちばん上"

/**
 * A movement's family record as a row, or **null when nothing was counted**.
 *
 * §4 edge case 5 and [MovementBest.singleSetReps]' own KDoc: 一度に counts only sets with a recorded
 * `actual_reps`. A row whose best single set is zero is a family the store has volume for but no
 * counted repetition in, and printing 〇回 as a personal best would be a scolding dressed as a record.
 * `GymStore.readMovementBests` builds its families from exactly the sets that were counted, so this is
 * a guard against a shape the query cannot currently produce rather than a live path — kept because the
 * alternative failure is silent and on the page.
 *
 * **The whole ladder rolls up into one row** (§4 edge case 6). Nothing here does that rolling: the
 * store keys its families on `ladder_id ?: id` and names each one for the rung the user actually
 * trains, so seven near-identical push-up rows never reach this function. What this function adds is
 * the いちばん上 fragment, which is null precisely when the hardest rung reached *is* the row's own
 * movement — `hardestReachedExerciseName` is defined as "the harder one, if there is one".
 *
 * The date is **omitted** rather than rendered `—` when the store has none. A tile has a slot that must
 * keep its shape and fills it with `—` (`ExerciseDetailScreen.movementTiles`); this is a line of text,
 * and a bare dash at the foot of a card reads as a rendering fault.
 */
internal fun prMovementRow(best: MovementBest): PrMovementRow? {
    if (best.singleSetReps <= 0) return null
    val value = JapaneseDate.kanjiExtended(best.singleSetReps) + "回"
    val meta = "一度に ・ のべ " + JapaneseDate.kanjiExtended(best.lifetimeReps) + "回"
    val date = best.lastLocalDate?.let { JapaneseDate.monthDay(it.atStartOfDay()) }
    val hardest = best.hardestReachedExerciseName?.let { "$HARDEST_REACHED $it" }
    return PrMovementRow(
        exerciseId = best.exerciseId,
        name = best.exerciseName,
        value = value,
        meta = meta,
        date = date,
        hardest = hardest,
        // The same shape §4 fixes for the routine row — 「シンディ、最高巡数 十七巡、時間内、六月十七日、
        // 六回」 — read down the card in the order it is drawn, with the label before its value so each
        // fragment is a sentence rather than an orphan number. §4 writes no sentence for this row, so
        // its skeleton is borrowed rather than a second one invented (`DECISIONS.md` §Q6's line).
        semantics = listOfNotNull(
            best.exerciseName,
            "一度に " + value,
            "のべ " + JapaneseDate.kanjiExtended(best.lifetimeReps) + "回",
            date,
            hardest,
        ).joinToString("、"),
    )
}

/**
 * The 動きごと list, newest outing first.
 *
 * `GymStore.readMovementBests` already sorts by `lastPerformedAt` descending and this re-states it for
 * the reason [prRoutineRows] does: §4's `Ready` state is a promise about the page, and a page that is
 * only sorted because a query happened to be is a page that unsorts itself the day the query changes.
 *
 * `lastPerformedAt` is the nearest thing this row has to §4's `achieved_at` — a movement best has no
 * achievement instant of its own, only the family's most recent outing — and rows with neither sort
 * last rather than to the top, because a null is an absence and not a recency.
 */
internal fun prMovementRows(bests: List<MovementBest>): List<PrMovementRow> =
    bests.sortedByDescending { it.lastPerformedAt ?: Long.MIN_VALUE }.mapNotNull(::prMovementRow)

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

private const val PAGE_TITLE = "最高"

/** §6: `generic close | とじる`, already the word `EventComposeScreen` and これまで use. */
private const val CLOSE = "とじる"

/** §6: `records empty | まだ 記録はありません`. Reachable from `Ready` and from nowhere else. */
private const val EMPTY = "まだ 記録はありません"

/** §6: `PR: empty explanation | 回数を数えた種目だけ ここに出ます | why 動きごと can be empty`. */
private const val MOVEMENTS_EMPTY_EXPLANATION = "回数を数えた種目だけ ここに出ます"

/** §6: `PR: see all exercises | すべての種目` — this page's exit into `GYM.LIBRARY.EXERCISE_INDEX`. */
private const val ALL_EXERCISES = "すべての種目"

/** §6: `archived chip | 削除済み`. The row stays tappable; §4's `ArchivedRoutineBest`. */
private const val ARCHIVED = "削除済み"

private const val LOADING = "読み込み中"

/** §4's accessibility note, verbatim: the two nested fragments are the parent's `customActions`. */
private const val ACTION_SESSION = "この記録を見る"
private const val ACTION_HISTORY = "これまでを見る"

/**
 * §2 `list bottom padding | 88.dp with the tab bar, **40.dp without**` — and this page is without.
 *
 * `04` §4's per-page annotation reads `Tab bar — visible`, and it loses to the shell's one rule:
 * `tabBarVisible(stack) = stack.size == 1` (`GymNav.kt`). 最高 is only ever reached pushed on top of
 * `GymRoute.Records`, so the bar is never on screen while this page is, and the deeper value leaves
 * 48.dp of dead air under the last card. The other half of the same spec row is the number.
 */
private val LIST_BOTTOM = 40.dp

/**
 * 最高 — every best you hold.
 *
 * **Two reads, two `Loadable`s, and they never merge.** §4: *`Error` **per tab independently** (a
 * movement-bests failure must not hide routine bests)*. Only one tab is on screen at a time, so this
 * falls out of asking each tab about its own read — but it is why the two flows are collected side by
 * side here rather than combined into one state, which would make either failure both tabs' failure.
 *
 * The tab lives in a plain `remember` on purpose. §4's back behaviour is *the tab selection resets to
 * 型ごと*, and a `rememberSaveable` would restore 動きごと on the next visit, which is the state the
 * spec asks the page not to keep. The cost is that a rotation also returns to 型ごと; that is the same
 * rule applied to a different departure, and it is one tap.
 *
 * The reads are the repository's own — `GymViewModel` exposes no bests flows (reported) — remembered
 * on [gym] so a recomposition does not resubscribe.
 */
@Composable
fun RecordsPrScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    var tab by remember { mutableStateOf(PrTab.Default) }

    val routineFlow = remember(gym) { gym.repository.routineBests() }
    val routineBests by routineFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
    val movementFlow = remember(gym) { gym.repository.movementBests() }
    val movementBests by movementFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = PAGE_TITLE,
                modifier = Modifier.semantics { heading() },
                style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
            )
            HeaderAction(label = CLOSE, description = CLOSE, color = c.inkFaint, onClick = { gym.onBack() })
        }

        PrTabs(selected = tab, onSelect = { tab = it })

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                PrTab.Routines -> when (val state = routineBests) {
                    // Never まだ 記録はありません for records we could not read — `00-plan.md` §4.1 rule
                    // 1, and the one distinction `DECISIONS.md` §Q6 pins 記録を読めません apart for.
                    Loadable.Loading -> PrLoading()
                    is Loadable.Failed -> FaultPanel(fault = state.fault, onRecover = gym::retry)
                    is Loadable.Ready -> {
                        val rows = remember(state.value) { prRoutineRows(state.value) }
                        if (rows.isEmpty()) {
                            PrRoutinesEmpty()
                        } else {
                            PrRoutineList(
                                rows = rows,
                                onRoutine = { gym.go(GymRoute.RoutineDetail(it)) },
                                onSession = { gym.go(GymRoute.Record(it.toString())) },
                                onHistory = { gym.go(GymRoute.History(routineId = it)) },
                            )
                        }
                    }
                }

                PrTab.Movements -> when (val state = movementBests) {
                    Loadable.Loading -> PrLoading()
                    is Loadable.Failed -> FaultPanel(fault = state.fault, onRecover = gym::retry)
                    is Loadable.Ready -> {
                        val rows = remember(state.value) { prMovementRows(state.value) }
                        if (rows.isEmpty()) {
                            PrMovementsEmpty(onAllExercises = { gym.go(GymRoute.ExerciseIndex) })
                        } else {
                            PrMovementList(
                                rows = rows,
                                onMovement = { gym.go(GymRoute.ExerciseDetail(it)) },
                                onAllExercises = { gym.go(GymRoute.ExerciseIndex) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 型ごと / 動きごと — a segmented pair, not a Material `TabRow`.
 *
 * §4's mock draws a word with a 1.dp `c.accent` rule under it and nothing else: no indicator slide, no
 * ripple, no divider across the width. `Modifier.selectable(role = Role.Tab)` is what makes TalkBack
 * announce the pair as tabs *and* say which is current, which a `clickable` with a hand-written
 * `stateDescription` would only half do.
 *
 * The rule is drawn under the selected tab only, and it is the page's one piece of vermillion.
 */
@Composable
private fun PrTabs(selected: PrTab, onSelect: (PrTab) -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PrTab.entries.forEach { entry ->
            val isSelected = entry == selected
            Column(
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(entry) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = entry.label,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 14.sp,
                        letterSpacing = 2.sp,
                        color = if (isSelected) c.ink else c.inkFaint,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                // Decoration: the tab's selected state is already spoken by `Role.Tab`.
                Box(
                    Modifier
                        .width(if (isSelected) 44.dp else 0.dp)
                        .height(1.dp)
                        .background(if (isSelected) c.accent else c.hair)
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}

/**
 * The 型ごと list.
 *
 * **Never `stickyHeader`, and in fact no header at all** — the list is one flat run of cards sorted by
 * recency, so there is no group to head (`00-plan.md` §4.1 rule 3 is satisfied by there being nothing
 * to make sticky, and the rule is pinned in the structure test anyway).
 */
@Composable
private fun PrRoutineList(
    rows: List<PrRoutineRow>,
    onRoutine: (String) -> Unit,
    onSession: (Long) -> Unit,
    onHistory: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp),
        contentPadding = PaddingValues(bottom = LIST_BOTTOM),
    ) {
        items(rows, key = { "routine:${it.routineId}" }) { row ->
            PrRoutineCard(
                row = row,
                onOpen = { onRoutine(row.routineId) },
                onSession = { onSession(row.sessionId) },
                onHistory = { onHistory(row.routineId) },
            )
        }
    }
}

/**
 * One 型ごと card — **one TalkBack node with two custom actions**, which is §4's accessibility line
 * read literally.
 *
 * The date and the count are tappable and are *inside* a card that is itself tappable. A nested
 * tappable row is unusable with TalkBack — the reader lands on the row, then on two fragments whose
 * relationship to it is unstated — so §4 fixes the answer: the fragments carry `clearAndSetSemantics`
 * on themselves and vanish from the tree, and the parent exposes them as `customActions`
 * (「この記録を見る」, 「これまでを見る」). Sighted users get three targets; TalkBack gets one node and a
 * menu, and both reach the same three places.
 *
 * **The value is `c.accent`.** §2's token table lists a `PR value` in `c.ink`, but that row sits beside
 * a `PR label` in Gothic 11.sp ls 1.sp, which is the *tile* pair `GYM.LIBRARY.EXERCISE_DETAIL` already
 * draws (`movementTiles`) — this page's meta line is Mincho 11.sp ls 3.sp, a different element. §4's
 * own mock annotates this number `value Mincho 22.sp c.accent`, and the page-specific mock is the more
 * specific instruction. Recorded because the two tables genuinely disagree if you read §2's row as
 * being about this page.
 */
@Composable
private fun PrRoutineCard(
    row: PrRoutineRow,
    onOpen: () -> Unit,
    onSession: () -> Unit,
    onHistory: () -> Unit,
) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.card)
                .clickable(onClick = onOpen)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = row.copy.semantics
                    customActions = listOf(
                        CustomAccessibilityAction(ACTION_SESSION) { onSession(); true },
                        CustomAccessibilityAction(ACTION_HISTORY) { onHistory(); true },
                    )
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.copy.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true),
                    style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.copy.value,
                    style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.accent),
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.copy.meta,
                    style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
                )
                // §4's `ArchivedRoutineBest`: the chip appears and the row stays tappable, because
                // `04` §1 rule 3 keeps an archived routine's detail page reachable from a record.
                if (row.copy.archived) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = ARCHIVED,
                        style = TextStyle(
                            fontFamily = Mincho,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            color = c.inkFaint,
                        ),
                    )
                }
            }

            // 中身が変わっています — §4 edge case 4's honest amount to say. It states the fact and
            // declines the judgement: the record stands, and whether a thirteenth station invalidates a
            // twelve-station circuit's PR is not a policy this app can get right.
            row.copy.note?.let { note ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note,
                    style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrFragment(
                    text = row.copy.date,
                    style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
                    onClick = onSession,
                )
                PrFragment(
                    text = row.copy.count,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                    onClick = onHistory,
                )
            }
        }
    }
}

/**
 * A tappable fragment inside a tappable card: a 48.dp target that is **invisible to TalkBack**.
 *
 * `clearAndSetSemantics {}` with an empty block is the point — the fragment's words are already in the
 * parent's one node and its action is already in the parent's `customActions`, so leaving it in the
 * tree would announce the date twice and offer an activation whose relationship to the row is unsaid.
 */
@Composable
private fun PrFragment(text: String, style: TextStyle, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = style)
    }
}

/** The 動きごと list, with すべての種目 beneath it. */
@Composable
private fun PrMovementList(
    rows: List<PrMovementRow>,
    onMovement: (String) -> Unit,
    onAllExercises: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp),
        contentPadding = PaddingValues(bottom = LIST_BOTTOM),
    ) {
        items(rows, key = { "movement:${it.exerciseId}" }) { row ->
            PrMovementCard(row = row, onOpen = { onMovement(row.exerciseId) })
        }
        item(key = "action:exercises") {
            PrCenteredAction(label = ALL_EXERCISES, onClick = onAllExercises)
        }
    }
}

/**
 * One 動きごと card — one node, **no nested taps**.
 *
 * §4's mock annotates 「both fragments tappable」 on the *routine* row's third line and on nothing else,
 * and the movement row's third line ends in いちばん上 足上げ腕立て, which is a fact rather than a
 * destination. So the whole card is one target into `EXERCISE_DETAIL` and there are no custom actions:
 * an a11y affordance for an action that does not exist is a bug, and so is the tap target for one.
 * (`MovementBest` does carry the session its best set was made in; wiring the date to it would be a
 * fourth destination the spec does not draw. Reported rather than added.)
 */
@Composable
private fun PrMovementCard(row: PrMovementRow, onOpen: () -> Unit) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.card)
                .clickable(onClick = onOpen)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = row.semantics
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true),
                    style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.value,
                    style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.accent),
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = row.meta,
                style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )

            if (row.date != null || row.hardest != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.date.orEmpty(),
                        style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
                    )
                    row.hardest?.let { hardest ->
                        Text(
                            text = hardest,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 読み込み中 — a read that has not answered yet.
 *
 * Its own composable, sharing nothing with [PrRoutinesEmpty] or [PrMovementsEmpty]. The three look
 * nearly identical and mean three different things, and a shared one is what quietly breaks the first
 * time someone adds a branch to it (`00-plan.md` §4.1 rule 1; `LibraryIndexScreen` split the same three
 * for the same reason).
 */
@Composable
private fun PrLoading() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = LOADING,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/** まだ 記録はありません — 型ごと has nothing in it, and the store said so. */
@Composable
private fun PrRoutinesEmpty() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = EMPTY,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * まだ 記録はありません **plus** 回数を数えた種目だけ ここに出ます.
 *
 * §4's `Empty` state gives 動きごと the second line and says why: *a user who only ever runs duration
 * stations will otherwise think it is broken*. That sentence is an **explanation of an empty tab**, and
 * it is a third thing again — not the emptiness, and certainly not a failure. A user with a hundred
 * plank sessions has an empty 動きごと and nothing has gone wrong.
 *
 * すべての種目 is kept here as well as under a populated list. §4 names `EXERCISE_INDEX` as an exit of
 * this page, and an empty tab whose only content is a sentence about why it is empty is a dead end
 * otherwise.
 */
@Composable
private fun PrMovementsEmpty(onAllExercises: () -> Unit) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = EMPTY,
                style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
            )
            Text(
                text = MOVEMENTS_EMPTY_EXPLANATION,
                style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
            )
            PrCenteredAction(label = ALL_EXERCISES, onClick = onAllExercises)
        }
    }
}

/** §2's centred action row: one accent word, 48.dp of target, no box around it. */
@Composable
private fun PrCenteredAction(label: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}
