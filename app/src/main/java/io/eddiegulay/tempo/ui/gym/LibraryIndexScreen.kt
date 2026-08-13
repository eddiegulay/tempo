package io.eddiegulay.tempo.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.gym.label
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.gym.DurationBucket
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.LibraryIndexRoutines
import io.eddiegulay.tempo.gym.RoutineEstimate
import io.eddiegulay.tempo.gym.RoutineFilter
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.gym.Tier
import io.eddiegulay.tempo.gym.bestMetricLabel
import io.eddiegulay.tempo.gym.bestValueLabel
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.gym.estimateLabel
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.rememberMinuteTime
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.combinedPressable
import io.eddiegulay.tempo.ui.theme.pressable

/*
 * GYM.LIBRARY.INDEX — 型. `04-library-records.md` §3's first page.
 *
 * Everything above the composables is pure and testable (`LibraryIndexScreenTest`), for the reason
 * `00-plan.md` §4.1 rule 10 gives: a decision made inside a composable can only be checked by
 * launching an emulator. The searching, filtering, ranking and kana folding are *not* here at all —
 * they are `LibraryFilters.kt` and `GymPageState.libraryIndexRoutines`, already shipped and already
 * tested, and this page calls them through `GymViewModel.libraryIndex`. What is left for this file is
 * the copy: which words a card carries, which items a long-press menu offers, and what 削除 asks.
 */

// ─── The card's three lines ─────────────────────────────────────────────────────────────────────

/**
 * One routine card's words. A shape, so the composable is a layout and nothing else.
 *
 * Three lines and a trailing count, exactly as §3's mock draws them:
 *
 * ```
 * ┌────────────────────────────────────────┐
 * │ シンディ                        中級   │  name · tier
 * │ 三種目 ・ 二十分 ・ 時間内              │  detail
 * │ 最高 十七巡                    六回    │  best-or-engine · count
 * └────────────────────────────────────────┘
 * ```
 *
 * **[engine] and [best] are mutually exclusive, and that is the mock's own rule.** The engine label
 * appears exactly once per card: on the meta line, unless a record has taken that line, in which case
 * it moves to the end of [detail]. That is why 七分間's mock reads `… ・ 約七分` / `巡回` while
 * シンディ's reads `… ・ 時間内` / `最高 十七巡` — two cards, one rule, and no card that either
 * repeats the engine or drops it.
 *
 * [description] is the whole card as one TalkBack node, and it is deliberately *not* assembled from
 * whichever line a fragment happened to land on: §3's accessibility paragraph fixes the order as
 * name → tier → structure → estimate → engine → count "so the first two words disambiguate", and a
 * description that reordered itself when a PR appeared would make two readings of the same card.
 */
data class RoutineCardCopy(
    val name: String,
    val tier: String?,
    val detail: String,
    val engine: String?,
    val best: String?,
    val count: String?,
    val description: String,
)

/**
 * A [RoutineSummary] as a card reads it.
 *
 * **Every number comes off the stored projection** — §3 edge case 9: "the card estimate comes from the
 * stored `est_duration_sec` projection, never recomputed per frame". [estimateLabel] is handed that
 * stored figure rather than a re-derived one, which is also why it is passed zero reps: the mock's
 * detail line prints a duration and no rep count, and 三百回 belongs to the builder's live line and the
 * detail page's summary.
 *
 * Three deliberate absences, each of which is a projection that cannot answer rather than a decision:
 *
 * 1. **No 「三十秒 / 十秒」 fragment.** §3's mock shows one for 七分間, and it is composed by
 *    `GymStore.toCard` from a per-version `MIN/MAX(prescribed_sec)` aggregate that `RoutineSummary`
 *    does not carry — the index reads list projections precisely so it does not inflate station lists
 *    (§3's Data in). The index card therefore prints a subset of `GYM.HOME`'s card for the same
 *    routine, never a different claim. Closing it needs a column on the projection, not a query here.
 * 2. **No 「種目 一件が不明」** (§3 edge case 3). Same cause: whether a station's exercise id still
 *    resolves is a fact about the version's rows. The routine still lists, which is the half of that
 *    edge case this page owes; 始める is disabled with its reason on the detail page, which is where
 *    the sentence says it belongs.
 * 3. **No 目安 suffix.** [RoutineEstimate.approximate] is a property of the arithmetic and the stored
 *    seconds do not carry it, so the card cannot claim the estimate was exact *or* approximate — it
 *    prints the number and says nothing about it. Inventing `approximate = true` would put 目安 on
 *    七分間, whose estimate is exact arithmetic (`00-plan.md` §2 row 18).
 *
 * The 制限時間 fragment is AMRAP-only, following `EngineRows.engineRows` rather than "any routine with
 * a cap": §6 marks 制限時間 AMRAP-only and the two surfaces must not disagree about which routines have
 * a clock. A cap renders through `fmt.duration` (二十分 / `20m`) and not as bare seconds, which is
 * the same split `DECISIONS.md` §Q10 draws — a cap is a span of the clock you watch, a rest is a value
 * you set.
 *
 * A best with no documented label — `HIGHEST_STEP`, the one `BestMetric` §Q9 forbids inventing a word
 * for — is treated as no best at all, so the card falls back to its engine label rather than printing
 * a bare number under a missing heading.
 *
 * The name is [displayName]'s, not the row's: a seeded routine is stored once and read in whichever
 * language is on, and a user's own routine falls through to what they typed (`CatalogDisplay.kt`).
 */
fun routineCardCopy(summary: RoutineSummary, strings: Strings): RoutineCardCopy {
    val name = summary.displayName(strings)
    val structure = strings.fmt.stations(summary.stationCount)
    val cap = summary.timeCapSeconds
        ?.takeIf { summary.engine == Engine.AMRAP }
        ?.let { strings.fmt.duration(it) }
    // The cap *is* the routine's duration, so an 約二十分 beside 二十分 would state one fact twice.
    val estimate = if (cap != null) {
        null
    } else {
        estimateLabel(
            summary.engine,
            RoutineEstimate(summary.estimatedDurationSeconds, totalReps = 0, approximate = false),
            strings,
        ).takeIf { it.isNotBlank() }
    }
    val best = summary.best
        ?.takeIf { bestMetricLabel(it.metric, strings) != null }
        ?.let { strings.gymLibrary.bestValue(bestValueLabel(it.metric, it.value, strings)) }
    val count = summary.timesDone.takeIf { it > 0 }?.let { strings.fmt.times(it) }
    val engineLabel = summary.engine.label(strings)

    val detail = listOfNotNull(
        structure,
        cap,
        estimate,
        // Displaced from the meta line only when a record is standing on it.
        engineLabel.takeIf { best != null },
    ).joinToString(strings.fmt.separator)

    return RoutineCardCopy(
        name = name,
        tier = summary.tier?.label(strings),
        detail = detail,
        engine = if (best == null) engineLabel else null,
        best = best,
        count = count,
        description = listOfNotNull(
            name,
            summary.tier?.label(strings),
            structure,
            cap,
            estimate,
            engineLabel,
            best,
            count,
        ).joinToString(strings.fmt.listSeparator),
    )
}

/**
 * A section heading as TalkBack reads it: 「よく使う、二件」 (§3, accessibility).
 *
 * An empty section announces its name alone. 自分の型 is the only heading that survives having nothing
 * under it, and 「自分の型、〇件」 is a scolding where 型はまだありません — the line directly beneath it —
 * is a statement; the count adds nothing a screen-reader user is about to hear anyway.
 */
fun sectionSemantics(label: String, count: Int, strings: Strings): String =
    if (count <= 0) label else label + strings.fmt.listSeparator + strings.fmt.items(count)

// ─── The long-press menu ────────────────────────────────────────────────────────────────────────

/**
 * The four items of §3's `MenuOpen` state, as values rather than as composables.
 *
 * They are a list because they are needed **twice**: once as `DropdownMenuItem`s and once as
 * `customActions`, and `00-plan.md` §4.1 rule 4 — the most-repeated accessibility requirement in the
 * plan — is exactly the requirement that those two never disagree. A screen-reader user must never
 * need the gesture, and the way to guarantee that is to make one list the source of both.
 *
 * Every label is `04-library-records.md` §6's: 始める, 写して作る, よく使うに入れる / よく使うから外す,
 * 削除 — and every one of them is now [label]'s rather than a constructor argument. A label fixed at
 * class-init cannot be re-resolved when the user flips the language (`DECISIONS.md` §L3), and this
 * enum is a `object`-lifetime singleton: it would have held whichever language was selected the first
 * time the class loaded, for the life of the process.
 *
 * The words are shared with the detail page's foot, which offers the same actions as centred rows.
 */
enum class RoutineMenuItem {
    Start,
    Duplicate,
    Favourite,
    Unfavourite,
    Delete,
}

/** §6's word for each item, resolved per read. */
fun RoutineMenuItem.label(strings: Strings): String = when (this) {
    RoutineMenuItem.Start -> strings.gymLibrary.start
    RoutineMenuItem.Duplicate -> strings.gymLibrary.actionDuplicate
    RoutineMenuItem.Favourite -> strings.gymLibrary.actionFavourite
    RoutineMenuItem.Unfavourite -> strings.gymLibrary.actionUnfavourite
    RoutineMenuItem.Delete -> strings.gymLibrary.actionDelete
}

/**
 * The menu for one routine.
 *
 * **A built-in has no 削除.** §1 rule 5: built-ins are never archivable or editable, and an item that
 * can only ever refuse is chrome rather than information. 写して作る stays, because copying a built-in
 * is precisely how you get a routine you *can* edit — §1 rule 5's own mechanism.
 *
 * 始める is offered without checking whether the routine can actually start. `startBlock`'s two
 * blocking reasons are properties of the *snapshot* (no stations, an unresolved exercise) and the
 * index reads projections that carry neither; `GymViewModel.startSession` re-asserts the predicate at
 * the write, which is where §3 edge case 3 puts the refusal — "at the detail page, not silently broken
 * here". A block therefore surfaces as a fault strip, never as a workout that starts wrong.
 *
 * **It surfaces with the wrong words, and that is disclosed rather than papered over.**
 * `GymViewModel.proceedWithStart` collapses every `startBlock != None` into `GymFault.Rejected`, which
 * `DECISIONS.md` §Q6 binds to 保存できませんでした — so a user who long-presses a routine with an
 * unresolved station and taps 始める is told a *save* failed when a *start* was refused. §6 :1129
 * carries the right sentence, 種目が見つからないため 始められません, and nothing reaches it from here.
 * `GYM.LIBRARY.DETAIL` never hits this because it disables 始める with that sentence; the index is the
 * only surface that exposes the path. The fix is a fault that carries the reason, in `GymViewModel` —
 * this file cannot make it, and two alternatives inside this file were **rejected**: dropping 始める
 * from the menu contradicts §3's exits table (`GYM.SESSION.PREPARE` (long-press → 始める)), and
 * gating it on `summary.stationCount` would catch only `NoStations` while leaving `UnknownExercise`
 * — the actual case of edge case 3 — reporting the same wrong sentence.
 */
fun routineMenuItems(summary: RoutineSummary): List<RoutineMenuItem> = buildList {
    add(RoutineMenuItem.Start)
    add(RoutineMenuItem.Duplicate)
    add(if (summary.favourite) RoutineMenuItem.Unfavourite else RoutineMenuItem.Favourite)
    if (!summary.builtIn) add(RoutineMenuItem.Delete)
}

// ─── The page ───────────────────────────────────────────────────────────────────────────────────

/*
 * 型's three sections and the page's own title are `strings.gymLibrary.title` /
 * `.sectionFrequent` / `.sectionBuiltIn` / `.sectionUser`.
 *
 * The title and the built-in heading are deliberately **two members** even though Japanese spells both
 * 型: §6 lists the word twice, once as "library page title — here a page, not a section" and once as
 * the built-in section's heading. English is where that split stops being theoretical.
 */

/**
 * The engine chips, in §3's own order: 巡回 段階 毎分 完走 時間内.
 *
 * Five of the seven engines, because five is the list the spec's chip row draws. The two it omits —
 * 毎分増 and 完走 ・ 休息あり — have labels in §6 and no routine in Phase 1 (`00-plan.md` §6 seeds
 * 七分間 / タバタ / リーコン・ロン and compiles two engines), so a chip for either would be a filter
 * that can only ever empty the page. They join this list when Phase 2 seeds デス・バイ and バーバラ.
 */
private val ENGINE_CHIPS = listOf(
    Engine.INTERVAL_CIRCUIT,
    Engine.FIXED_SETS,
    Engine.EMOM,
    Engine.FOR_TIME,
    Engine.AMRAP,
)

/**
 * 型 — browse, search and filter every routine you can start.
 *
 * **Three empty states that must never be confused, which is the whole of `00-plan.md` §4.1 rule 1.**
 * A store that could not be read renders `FaultPanel` and the word 記録を読めません; a search that
 * matched nothing renders 該当する型はありません with the filter row still open so it can be widened; a
 * library with no user routines renders 型はまだありません *inside 自分の型*, beneath built-ins that
 * are still listed. §6 flags the trap outright — 該当する型はありません, "**never** 型はまだありません
 * — that means something else" — and none of the three shares a composable here.
 *
 * The page holds no filter state of its own. Query, chips and scroll live in [GymViewModel] because
 * §3 edge case 8 requires them to survive a rotation and a tab switch, and `LazyListState` is passed
 * in rather than remembered for the same reason. What this composable owns is one thing: which of the
 * routines it is handed is under the user's thumb, and that is the long-press menu.
 *
 * Back is wired to [GymViewModel.closeSearch] and nothing else. The shell's own handler is registered
 * above this page and is already the fallback, so an `enabled = false` handler here would be pure
 * ceremony (`GymShell`'s note says so explicitly) — with the search row open, one press closes it *and
 * clears the query*, which is §3's back behaviour: one back press, not two.
 */
@Composable
fun LibraryIndexScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val now by rememberMinuteTime()

    val index by gym.libraryIndex.collectAsStateWithLifecycle()
    val filter by gym.routineFilter.collectAsStateWithLifecycle()
    val searchOpen by gym.searchOpen.collectAsStateWithLifecycle()
    val listState by gym.libraryListState.collectAsStateWithLifecycle()
    val writeFault by gym.writeFault.collectAsStateWithLifecycle()

    // 作る while the store is unread would produce a save that cannot land (§3 edge case 6). Ready is
    // the only state that permits it — Loading included, because a library still arriving cannot yet
    // tell a duplicate name from a new one.
    val ready = index is Loadable.Ready

    var pendingDelete by remember { mutableStateOf<RoutineSummary?>(null) }

    BackHandler(enabled = searchOpen) { gym.closeSearch() }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = s.gymLibrary.title,
                    modifier = Modifier.semantics { heading() },
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = s.fmt.era(now) + s.fmt.separator + s.fmt.monthDay(now),
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(
                    // 探す / とじる, §6's own pair. The word states what the tap does, not what is open.
                    label = if (searchOpen) s.gymLibrary.searchClose else s.gymLibrary.searchOpen,
                    description = if (searchOpen) s.gymLibrary.searchClose else s.gymLibrary.searchOpen,
                    color = c.inkFaint,
                    onClick = { if (searchOpen) gym.closeSearch() else gym.openSearch() },
                )
                HeaderAction(
                    label = s.gymLibrary.create,
                    // §6's builder title, so the button and the page it opens say the same word.
                    description = s.gymLibrary.createDescription,
                    color = if (ready) c.accent else c.inkFaint,
                    enabled = ready,
                    onClick = { gym.go(GymRoute.Builder()) },
                )
            }
        }

        SearchAndFilters(
            open = searchOpen,
            filter = filter,
            onQuery = gym::setQuery,
            onTier = gym::toggleTier,
            onEngine = gym::toggleEngine,
            onDuration = gym::setDurationBucket,
        )

        // Above the list, never over it: the routines stay readable behind whatever failed, which is
        // the same placement the composer uses. Three of the eight faults carry no action word
        // (`DECISIONS.md` §Q6 — 空き容量が足りません, この型は削除されています, 保存できませんでした), so the
        // strip has nothing to press; those clear on the next action below, or on leaving the gym.
        writeFault?.let { fault ->
            FaultStrip(
                fault = fault,
                onRecover = {
                    gym.retry()
                    gym.dismissWriteFault()
                },
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val state = index) {
                // Never 型はまだありません for a library we could not read — the distinction this
                // whole feature routes its reads through `Loadable` to preserve.
                is Loadable.Failed -> FaultPanel(fault = state.fault, onRecover = gym::retry)

                Loadable.Loading -> LoadingState()

                is Loadable.Ready ->
                    // A search that matched nothing is not an empty library, and says so in its own
                    // words. Only reachable with something typed or chipped: with no filter at all a
                    // zero match means the library itself is empty, which falls through to the list
                    // and renders 型はまだありません under 自分の型 (§3's `Empty`).
                    if (state.value.matched == 0 && !filter.isEmpty) {
                        NoMatchState(
                            chipped = filter.tiers.isNotEmpty() ||
                                filter.engines.isNotEmpty() ||
                                filter.duration != null,
                            onClear = gym::clearRoutineFilters,
                        )
                    } else {
                        LibraryList(
                            routines = state.value,
                            filtering = !filter.isEmpty,
                            listState = listState,
                            onOpen = gym::openRoutine,
                            onMenu = { summary, item ->
                                // The last failure is answered by the next attempt, not left on the
                                // page under a strip the user cannot dismiss.
                                gym.dismissWriteFault()
                                when (item) {
                                    RoutineMenuItem.Start -> gym.startSession(summary.routineId)
                                    RoutineMenuItem.Duplicate -> gym.duplicateRoutine(summary.routineId)
                                    RoutineMenuItem.Favourite -> gym.setFavourite(summary.routineId, true)
                                    RoutineMenuItem.Unfavourite -> gym.setFavourite(summary.routineId, false)
                                    RoutineMenuItem.Delete -> pendingDelete = summary
                                }
                            },
                            onExercises = { gym.go(GymRoute.ExerciseIndex) },
                        )
                    }
            }
        }
    }

    pendingDelete?.let { target ->
        DeleteRoutineDialog(
            gym = gym,
            target = target,
            onDismiss = { pendingDelete = null },
        )
    }

    // 始める's **start guard** (`03-player.md` §A.1), which this page raises and — until this mount —
    // never drew. `GymViewModel.startSession` answers an open session by publishing a
    // `ResumePromptState` and returning *while still holding the start lock*: "the lock stays held on
    // purpose: 始める reads 支度 until the prompt is answered or dismissed". With no host on this page
    // the prompt existed only in the ViewModel, so a long-press → 始める over an open session rendered
    // nothing at all and left `_startInFlight` non-null forever — every later 始める anywhere in the
    // gym short-circuited, and `GYM.LIBRARY.DETAIL` drew a 支度 that could not resolve. The only
    // release is `dismissResumePrompt`, which is a button inside the prompt.
    //
    // **Unconditional, not gated on a pending start.** [ResumePromptHost] already returns on a null
    // `gym.resumePrompt`, so the ordinary path costs one flow read; gating it on this page's own
    // notion of "I asked" was rejected because the prompt outlives the composition that raised it
    // (process death restores the row, `onHomeShown` can raise it while this page is on top) and a
    // page-local gate would reintroduce exactly the undrawable state above. This is the same
    // unconditional mount `GYM.HOME` and `GYM.LIBRARY.DETAIL` make.
    //
    // The three mounts overlap for the shell's ~280ms `AnimatedContent` cross-fade, which the review
    // records as a separate MINOR against `GymShell`; hoisting a single host above the transition is
    // the fix, and it must land in all three files at once or the prompt doubles. Not done here.
    ResumePromptHost(gym)
}

/**
 * The search field and the three chip rows, unfolding in place.
 *
 * `animateContentSize(220ms, LinearOutSlowIn)` is §3's own figure and the `PickerRow` idiom from
 * `EventComposeScreen` — the row grows the page rather than covering it, so the list underneath keeps
 * its position and the user can see what their typing is narrowing.
 *
 * The field takes focus when it opens. A search row that appears and then waits to be tapped again is
 * two gestures for one intent, and the keyboard is the reason the row exists.
 */
@Composable
private fun SearchAndFilters(
    open: Boolean,
    filter: RoutineFilter,
    onQuery: (String) -> Unit,
    onTier: (Tier) -> Unit,
    onEngine: (Engine) -> Unit,
    onDuration: (DurationBucket?) -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val focusRequester = remember { FocusRequester() }

    Column(Modifier.fillMaxWidth().animateContentSize(tween(220, easing = LinearOutSlowInEasing))) {
        if (!open) return@Column

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        BasicTextField(
            value = filter.query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .focusRequester(focusRequester)
                .semantics { contentDescription = s.gymLibrary.searchPlaceholder },
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = 8.dp)) {
                        if (filter.query.isEmpty()) {
                            Text(
                                text = s.gymLibrary.searchPlaceholder,
                                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.inkFaint),
                            )
                        }
                        inner()
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tier.entries.forEach { tier ->
                FilterChip(tier.label(s), tier in filter.tiers) { onTier(tier) }
            }
            ChipDivider()
            ENGINE_CHIPS.forEach { engine ->
                FilterChip(engine.label(s), engine in filter.engines) { onEngine(engine) }
            }
            ChipDivider()
            DurationBucket.entries.forEach { bucket ->
                FilterChip(bucket.label(s), bucket == filter.duration) { onDuration(bucket) }
            }
        }
    }
}

/** A hairline between chip groups — the mock's │. Decoration, so it is invisible to TalkBack. */
@Composable
private fun ChipDivider() {
    val c = LocalTempoColors.current
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(16.dp)
            .background(c.hair)
            .clearAndSetSemantics {},
    )
}

/**
 * One filter chip. Selected is vermillion, unselected is faint — **no fill and no border**: the page
 * already carries cards, and a second boxed idiom on the same screen would make the chips read as
 * content.
 *
 * The header of this function used to end that list with "no ripple pill" while the body was a bare
 * `clickable`, which has never been indication-free: it took whatever `LocalIndication` provided —
 * Material's grey rectangle before [InkPress], the ink wash since. The doc and the code have disagreed
 * since the chip was written, and the disagreement is resolved **towards a press**, not away from one.
 *
 * What that line was defending is the chip's *resting* appearance, and that is untouched: a chip at
 * rest still draws nothing but its word. What it was not entitled to promise is that a tap does
 * nothing visible. Every other chip family in 鍛錬 — the builder's engine row, the picker's はかり方,
 * the detail page's tiers — now takes [TempoShapes.Word], and a filter that changes the list below it
 * is the last control on the page that should leave a thumb guessing whether it landed. `Word` is also
 * the shape that keeps the promise honest: a lozenge of wash that appears under the finger and dries,
 * never a pill drawn around the word.
 *
 * *Rejected* — `indication = null`, keeping the sentence as written. It is the reading the old comment
 * supports, and it would make these the only chips in the feature that are silent, on the one screen
 * whose chips filter what is underneath them. `GymTabBar` is silent on purpose because the dock is
 * chrome; a filter is content.
 *
 * `stateDescription = "選択中"` is §3's accessibility line verbatim. There is no documented word for
 * the unselected state and none is invented — an unselected chip simply carries no state, which is
 * also what it means.
 */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            // 体 is one glyph wide. The target grows into the gutter either side; the word does not
            // move, because the `Box` centres it in whatever width the floor buys.
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick)
            .semantics {
                if (selected) stateDescription = s.gymLibrary.selected
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = if (selected) c.accent else c.inkFaint,
            ),
        )
    }
}

/**
 * The three sections, and 種目を見る under them.
 *
 * **Keys are section-scoped.** A routine appears in both よく使う and its own section by design — that
 * is what §3's mock draws — so a bare routine id would be two items with one key and Compose would
 * throw. `01-shell.md` §B edge case 6 fixes the form: `"builtin:$id"` / `"user:$id"`.
 *
 * **Never `stickyHeader`.** `00-plan.md` §4.1 rule 3: section headings are plain items, as the
 * calendar's day headers already are, and the sticky variant is still experimental foundation API.
 *
 * A section with no matches is omitted rather than drawn empty — with one exception. 自分の型 keeps its
 * heading and says 型はまだありません **only when nothing is being filtered**, because that sentence is
 * a claim about the library and a filter is the user hiding things on purpose. Under an active filter
 * the honest report is the 該当する型はありません the caller already renders when nothing at all matched.
 */
@Composable
private fun LibraryList(
    routines: LibraryIndexRoutines,
    filtering: Boolean,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    onMenu: (RoutineSummary, RoutineMenuItem) -> Unit,
    onExercises: () -> Unit,
) {
    val s = LocalStrings.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 22.dp, vertical = 6.dp),
        // The seated tab bar, which is shallower than the launcher's floating dock (`00-plan.md` §3.2).
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        routineSection(s.gymLibrary.sectionFrequent, "frequent", routines.frequent, onOpen, onMenu)
        routineSection(s.gymLibrary.sectionBuiltIn, "builtin", routines.builtIn, onOpen, onMenu)

        if (routines.user.isNotEmpty() || !filtering) {
            routineSection(s.gymLibrary.sectionUser, "user", routines.user, onOpen, onMenu, keepEmpty = true)
            if (routines.user.isEmpty()) {
                item(key = "empty:user") { InlineEmpty(s.gymLibrary.userEmpty) }
            }
        }

        item(key = "action:exercises") {
            CenteredAction(label = s.gymLibrary.exercises, onClick = onExercises)
        }
    }
}

/**
 * @param keepEmpty draw the heading over nothing. True only for 自分の型, which owns the
 *   型はまだありません line beneath it; an empty よく使う or 型 heading would be furniture announcing
 *   an absence the user cannot act on.
 */
private fun LazyListScope.routineSection(
    heading: String,
    keyPrefix: String,
    routines: List<RoutineSummary>,
    onOpen: (String) -> Unit,
    onMenu: (RoutineSummary, RoutineMenuItem) -> Unit,
    keepEmpty: Boolean = false,
) {
    if (routines.isEmpty() && !keepEmpty) return
    item(key = "head:$keyPrefix") { SectionHeading(heading, routines.size) }
    items(routines, key = { "$keyPrefix:${it.routineId}" }) { summary ->
        RoutineCardRow(
            summary = summary,
            onOpen = { onOpen(summary.routineId) },
            onMenu = { onMenu(summary, it) },
        )
    }
}

/**
 * 「よく使う」 — the calendar's day-header geometry, one TalkBack node, no sticky behaviour.
 *
 * **The count is spoken and never drawn.** §3's mock prints the bare word (as does `01-shell.md`'s
 * HOME mock, and `GymHomeScreen`'s implementation of it), and §2's token table has one row for
 * `section heading` — Mincho 12.sp ls 3.sp `c.inkFaint` — and no row for a glyph beside it. A visible
 * 「よく使う 二」 in a second, unsourced type style (Gothic 11.sp ls 1.sp) shipped here and was
 * **rejected**: it is a number no spec asks for, in a style no table supplies, disagreeing with the
 * sibling page about what a heading is. §3's accessibility line asks only that the *description* read
 * 「よく使う、二件」, which [sectionSemantics] delivers on its own — the one place the specs do print a
 * section count — `GYM.LIBRARY.EXERCISE_DETAIL`'s 使われている型 四件 — is a different page,
 * right-aligned, and carries 件.
 *
 * `heading()` rides *inside* `clearAndSetSemantics` rather than in a second `semantics` block, because
 * `clearAndSetSemantics` discards the children's semantics and everything this node announces has to
 * be declared in the one place. `01-shell.md` :615 and :813 both state the rule ("**Section labels**
 * are `semantics { heading() }`") and these three labels are the page's entire navigational structure:
 * without it TalkBack's heading navigation cannot jump 型 → 自分の型 and a screen-reader user has to
 * swipe through every card in between. The page title already carries it, so its absence here was also
 * this file disagreeing with itself.
 */
@Composable
private fun SectionHeading(label: String, count: Int) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)
            .clearAndSetSemantics {
                heading()
                contentDescription = sectionSemantics(label, count, s)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
    }
}

/**
 * One routine, as a washi card with a menu behind a long press.
 *
 * **The card is one accessibility node and the menu is four actions on it** — `00-plan.md` §4.1 rule 4,
 * following `NotificationsScreen.kt:204`'s note: a long press is invisible to TalkBack, so every item
 * the gesture reveals is also a `customAction`. `clearAndSetSemantics` wipes the `combinedClickable`'s
 * own semantics, which is why the click, the long press *and* its label are re-declared inside it —
 * the modifier is the affordance, the semantics block is the announcement, and they are separate.
 *
 * The name is allowed one line and the detail line two, because a card that grows to fit a long
 * routine name pushes the count out of alignment down the whole list; §3 sizes the card at ~86.dp.
 */
@Composable
private fun RoutineCardRow(
    summary: RoutineSummary,
    onOpen: () -> Unit,
    onMenu: (RoutineMenuItem) -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }

    val copy = remember(summary, s) { routineCardCopy(summary, s) }
    val items = remember(summary.builtIn, summary.favourite) { routineMenuItems(summary) }
    val actions = remember(items, onMenu, s) {
        items.map { item -> CustomAccessibilityAction(label = item.label(s)) { onMenu(item); true } }
    }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .background(c.card, TempoShapes.Card)
                .combinedPressable(
                    shape = TempoShapes.Card,
                    onLongClickLabel = s.gymLibrary.menu,
                    onLongClick = { menuOpen = true },
                    onClick = onOpen,
                )
                .clearAndSetSemantics {
                    contentDescription = copy.description
                    role = Role.Button
                    onClick { onOpen(); true }
                    onLongClick(label = s.gymLibrary.menu) { menuOpen = true; true }
                    customActions = actions
                },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = copy.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true),
                        style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
                    )
                    copy.tier?.let { tier ->
                        Text(
                            text = tier,
                            style = TextStyle(
                                fontFamily = Mincho,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp,
                                // 上級 sits one step forward, so the hardest band is the one the eye
                                // finds first (§2's tier badge row).
                                color = if (summary.tier == Tier.ADVANCED) c.inkSoft else c.inkFaint,
                            ),
                        )
                    }
                }
                if (copy.detail.isNotBlank()) {
                    Text(
                        text = copy.detail,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.5.sp, color = c.inkSoft),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f, fill = true)) {
                        copy.best?.let { best ->
                            Text(
                                text = best,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.accent),
                            )
                        }
                        copy.engine?.let { engine ->
                            Text(
                                text = engine,
                                style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
                            )
                        }
                    }
                    copy.count?.let { count ->
                        Text(
                            text = count,
                            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                        )
                    }
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label(s), style = TextStyle(fontFamily = Mincho, color = c.ink)) },
                    onClick = {
                        menuOpen = false
                        onMenu(item)
                    },
                )
            }
        }
    }
}

/**
 * 削除's confirmation.
 *
 * **The count is read twice and both readings must agree before anything is destroyed.**
 * `countForRoutine` is a flow that starts at `Loading` — it was seeded at zero until the read was
 * widened to a `Loadable`, and a dialog opened in the first frames of that query then read "no
 * history" and offered 完全に削除 over a routine with fifty sessions behind it. Unknown is now
 * expressible, and is read as zero here only for the maximum, never for the branch: the projection's
 * own `timesDone` is the second reading, and
 * the purge branch requires both to be zero — §1 rule 4's whole purpose is that the user can see the
 * blast radius, and a race that hides it defeats the rule far more thoroughly than wrong copy would.
 *
 * The archive branch is the ordinary one: sessions stay readable and keep their PRs (§1 rule 2), which
 * is exactly what the body says out loud.
 *
 * The words themselves are **not this page's**. `GYM.HOME` and `GYM.LIBRARY.DETAIL` raise the same
 * confirm from the same two rows of §6, and three implementations of one string table is the
 * divergence bug `DECISIONS.md` §Q7 spells out for numerals. It now reads [deleteRoutineConfirm] —
 * `GYM.LIBRARY.DETAIL`'s own branch, which is the same branch this page used to reach through
 * `deleteRoutineCopy` — so the two library surfaces share one implementation and one key set.
 * `GymHomeCopy.deleteRoutineCopy` is still a third copy of the branch; pointing it at the same keys is
 * a one-line change in a file this unit does not own, and it is on the report.
 */
@Composable
private fun DeleteRoutineDialog(
    gym: GymViewModel,
    target: RoutineSummary,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    // `countForRoutine` is now a `Loadable` (`GymRepository.countForRoutine`), so an unread or failed
    // count no longer masquerades as zero. Unwrapped here only to keep this page compiling unchanged;
    // the projection's own `timesDone` is still the second reading and still the one that gates the
    // purge branch, so an unknown store count can only ever make this dialog *more* conservative.
    val stored by gym.countForRoutine(target.routineId).collectAsStateWithLifecycle()
    val sessions = maxOf(stored.valueOrNull() ?: 0, target.timesDone)
    val copy = deleteRoutineConfirm(target.displayName(s), sessions, s)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(text = copy.title, style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink))
        },
        text = {
            Text(
                text = copy.body,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.5.sp,
                    color = c.inkFaint,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    if (sessions > 0) {
                        gym.archiveRoutine(target.routineId)
                    } else {
                        gym.purgeRoutine(target.routineId)
                    }
                },
            ) {
                Text(copy.confirm, style = TextStyle(fontFamily = Mincho, color = c.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(copy.cancel, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}

/**
 * 読み込み中 — a store that has not answered yet.
 *
 * Its own composable, sharing nothing with [NoMatchState] or [InlineEmpty]. They look nearly
 * identical and mean three different things, and `00-plan.md` §4.1 rule 1 is the rule that a shared
 * composable would quietly break the first time someone added a branch to it.
 */
@Composable
private fun LoadingState() {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = s.gymLibrary.loading,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * 該当する型はありません — the search found nothing.
 *
 * Not an empty library and never worded as one. 絞り込みを外す appears only when a *chip* is narrowing
 * the page: it clears chips, not the query (`GymViewModel.clearRoutineFilters`), so offering it to a
 * user who only typed would be a button that changes nothing they can see.
 */
@Composable
private fun NoMatchState(chipped: Boolean, onClear: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = s.gymLibrary.noMatch,
                style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
            )
            if (chipped) {
                CenteredAction(label = s.gymLibrary.clearFilters, onClick = onClear)
            }
        }
    }
}

/** 型はまだありません, inside 自分の型 — a section with nothing in it, not a page with nothing on it. */
@Composable
private fun InlineEmpty(text: String) {
    val c = LocalTempoColors.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/** §2's centred action row: one accent word, 48.dp of target, no box around it. */
@Composable
private fun CenteredAction(label: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}
