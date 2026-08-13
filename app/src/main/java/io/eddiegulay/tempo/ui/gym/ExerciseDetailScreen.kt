package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.MovementBest
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/*
 * GYM.LIBRARY.EXERCISE_DETAIL — 種目の中身. `04-library-records.md` §3's last library page.
 *
 * One movement in full: what it is, how to do it, what it is worth, where it sits on its ladder, how
 * far up you have climbed, and what uses it.
 *
 * **Two data sources, two doctrines, and the page must not confuse them** — which is the whole reason
 * this comment is here rather than a line down. The movement itself, its cue, its coefficient and its
 * ladder are `ExerciseCatalog` reads: an in-memory map filled once at repository construction
 * (`00-plan.md` §2 row 6), so there is **no Loading state and no Error state for any of them**, and §3
 * says so outright. 最高 and 使われている型 are store queries: they load, they can fail, and they carry
 * the full `Loadable` treatment with a separate composable per state. §3's own `PbFailed` sentence is
 * the seam — "最高 replaced by a one-line 記録を読めません ・ もう一度; the rest is unaffected, because the
 * catalogue is in-memory and **the page can never be blank**".
 *
 * **A ladder tap replaces, never pushes** (`00-plan.md` §3, `GymNav.replaceTop`). Walking a seven-rung
 * ladder would otherwise build a seven-deep stack and make Back a maze; one press must always leave
 * the exercise, whichever rung the user is standing on.
 *
 * Everything above the composables is pure and JUnit-tested in
 * `test/ui/gym/ExerciseCatalogueCopyTest.kt` (`00-plan.md` §4.1 rule 10).
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure copy and shaping — Android-free
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** A value slot with nothing in it. `—`, as everywhere else in 鍛錬 (`gym/Numerals.kt`). */
private const val NO_VALUE = "—"

/** 押す ・ 難度 一.〇 — §3's header subtitle, through [exerciseCoefficient] so 走る still reads 難度 —. */
internal fun exerciseDetailSubtitle(exercise: Exercise): String =
    exercise.pattern.label + " ・ 難度 " + exerciseCoefficient(exercise)

/**
 * One rung of the 段階 ladder: the movement, whether it has been climbed, and whether it is where the
 * user currently stands.
 *
 * Two booleans rather than an enum because they are not exclusive — the current rung is also a reached
 * one — and §3's edge cases 2 and 3 colour them independently: the *dot* marks the current rung in
 * `c.accent`, the *name* marks reached-so-far in `c.inkSoft` against `c.inkFaint` above it.
 */
internal data class LadderRung(
    val exercise: Exercise,
    val reached: Boolean,
    val current: Boolean,
)

/**
 * The rung marked いま — the hardest rung of this ladder the store has a record for, or null.
 *
 * **Computed from the per-exercise rows, not from a family roll-up.** `exerciseBests()` emits one row
 * per movement and a row exists exactly when that movement has a completed set with `actual_reps`
 * recorded, so "which rungs have been climbed" is a membership test and "how far" is the max
 * `difficulty` among them. That is the same definition `GymStore` applies to fill
 * `MovementBest.hardestReachedExerciseId` on the ladder-rolled-up read — restated here over rows this
 * page already holds, rather than subscribing to a second flow to be told it.
 *
 * **§3 edge case 1's definition is still not fully implementable, and this function is the nearest
 * honest thing, not the definition.** Edge case 1 says: "the hardest rung (max `difficulty`) performed
 * in the **last 90 days** with at least one completed set of ≥ 3 reps. Below that threshold, nothing is
 * marked いま." A row carries the movement's best-ever set and its most recent outing, which is not the
 * same as *a ≥ 3-rep set inside the window* — the 90-day window needs a per-set date the store does not
 * project, and applying the 3-rep floor to a lifetime best while the window is missing would enforce
 * half a two-clause rule and change who is marked for a reason the spec did not give. **Reported**, and
 * until a windowed read lands this page marks いま one rung too generously: a single two-rep attempt at
 * 片手腕立て two years ago will stand as the current rung.
 *
 * *Rejected* — marking nothing at all until that read exists. A ladder with no いま is §3's `NoHistory`
 * state, which is a claim ("you have not started climbing") that would be false for exactly the users
 * who have. Over-marking is visible and self-correcting; a page that silently reports every user as a
 * beginner is neither.
 *
 * *Rejected* — `lastLocalDate` as a 90-day proxy. On a per-exercise row it is that rung's own last
 * outing, which is better than the family's, but it dates the rung's *most recent* set and says nothing
 * about the one that was ≥ 3 reps.
 */
internal fun currentRung(ladder: List<Exercise>, bests: List<MovementBest>): Exercise? {
    val recorded = bests.mapTo(mutableSetOf()) { it.exerciseId }
    return ladder.filter { it.id in recorded }.maxByOrNull { it.difficulty }
}

/**
 * The ladder with each rung told whether it has been climbed.
 *
 * §3 edge case 2: "Rungs *harder* than the current one are `c.inkFaint`; rungs at or below it are
 * `c.inkSoft` — the ladder reads as climbed-so-far without any gamified language." Edge case 3 is the
 * same sentence read the other way: a rung the user has performed but which is *easier* than their
 * current one still counts as reached, "regression to a warm-up variation must not un-climb the
 * ladder", which is why the test is `difficulty <= current` and never "was this specific rung
 * performed".
 *
 * With no current rung nothing is reached — §3's `NoHistory` state, where "no rung marked いま; every
 * dot `c.inkFaint`".
 */
internal fun ladderRungs(ladder: List<Exercise>, current: Exercise?): List<LadderRung> =
    ladder.map { rung ->
        LadderRung(
            exercise = rung,
            reached = current != null && rung.difficulty <= current.difficulty,
            current = current != null && rung.id == current.id,
        )
    }

/**
 * 「腕立て伏せ、難度 一.〇、いまここ」 or 「…、まだ」 — §3's accessibility line, verbatim.
 *
 * Exactly two forms, because §3 writes exactly two. A *reached but not current* rung reads まだ like
 * every other non-current rung: the third word answers "am I standing here", and inventing a third
 * word for "climbed past" would be copy no table carries.
 */
internal fun rungSemantics(rung: LadderRung): String =
    rung.exercise.nameJa + "、難度 " + exerciseCoefficient(rung.exercise) + "、" +
        if (rung.current) "いまここ" else "まだ"

/** One 最高 tile: the value over its label, read label-first by TalkBack. */
internal data class MovementTile(val value: String, val label: String)

/**
 * 一度に / のべ回数 / 最後 — the three tiles of §3's mock, or null when there is nothing to put in them.
 *
 * Null is §3's `NoHistory` state and it renders まだ やっていません. It is **not** the same value as an
 * unread or unreadable store, which the caller keeps apart as `Loadable` all the way to the branch —
 * この page's 最高 is a section the user came to read, so all four states get their own composable
 * (`00-plan.md` §4.1 rule 1). Only the *index*'s one-word overlay is allowed to collapse them.
 *
 * **一度に counts only sets where `actual_reps` was recorded** (§3 edge case 4). `GymStore`'s query
 * enforces it — `WHERE r.actual_reps IS NOT NULL` — and the distinction is the point: a prescribed
 * twenty that nobody confirmed is a plan, not a record. This is the difference between a record and a
 * wish, and no fallback to `prescribed_reps` may ever be added here.
 *
 * **のべ回数 goes through `kanjiExtended`, which is where §3 edge case 5 is honoured**: it caps kanji at
 * 九千九百九十九 and falls back to arabic above, because kanji at that magnitude is unreadable
 * (prerequisite P5). There is deliberately no second cap in this function — `DECISIONS.md` §Q7 makes
 * that formatter the only one.
 *
 * A zero or a missing date renders [NO_VALUE] rather than 〇回 / a fabricated day: the row exists
 * because *something* was recorded, so a zero in one column is a gap in the store's answer and not a
 * fact about the user.
 *
 * @param best **this exercise's own row**, from [exerciseBestFor] over `GymRepository.exerciseBests`.
 *   Handing it a `movementBests()` row would put a ladder-family sum under three labels that name one
 *   movement — 一度に is a set someone did, and a family's best set was not necessarily done here.
 */
internal fun movementTiles(best: MovementBest?): List<MovementTile>? {
    if (best == null) return null
    val singleSet = if (best.singleSetReps > 0) {
        JapaneseDate.kanjiExtended(best.singleSetReps) + "回"
    } else {
        NO_VALUE
    }
    val lifetime = if (best.lifetimeReps > 0) {
        JapaneseDate.kanjiExtended(best.lifetimeReps) + "回"
    } else {
        NO_VALUE
    }
    val last = best.lastLocalDate?.let { JapaneseDate.monthDay(it.atStartOfDay()) } ?: NO_VALUE
    return listOf(
        MovementTile(singleSet, "一度に"),
        MovementTile(lifetime, "のべ回数"),
        MovementTile(last, "最後"),
    )
}

/**
 * The row that speaks for **this exercise**, out of `exerciseBests()`.
 *
 * The tiles ask "what has *this movement* been worth", so the match is on `exerciseId` and the source
 * is the per-exercise read (`GymRepository.exerciseBests`) — never `movementBests()`, whose row is a
 * ladder-family roll-up named for one rung. Matching a family row on `exerciseId` was the shipped bug:
 * with the seeded push-up ladder it rendered まだ やっていません on 腕立て伏せ for a user who trains it —
 * the family's row was named 膝つき腕立て — and, on 膝つき腕立て, a のべ回数 summed across seven rungs.
 * A false emptiness claim and a wrong number, both from a read that SUCCEEDED, which is `00-plan.md`
 * §4.1 rule 1's target exactly.
 *
 * Null still means まだ やっていません, and now it means it truthfully: no row, no recorded set of this
 * movement. Opening 壁腕立て after only ever doing full push-ups is genuinely a movement never
 * performed, and the 段階 block beside it still shows how far the family has climbed.
 */
internal fun exerciseBestFor(exercise: Exercise, bests: List<MovementBest>): MovementBest? =
    bests.firstOrNull { it.exerciseId == exercise.id }

/**
 * 使われている型 — which routines contain this movement.
 *
 * A [RoutineSummary] is a list projection that deliberately carries no stations (§3's one-query rule),
 * so the ids have to arrive separately, per **version**. Versions are immutable, which is what makes
 * that read safe to do once and hold: there is no staleness window to manage — the same argument
 * `GymViewModel.stationNames` makes for search.
 *
 * §3 edge case 6: **archived routines are excluded, built-ins are included.** Both fall out of the
 * source rather than being filtered here — `GymRepository.routines` already excludes archived rows and
 * has never excluded built-ins — so this function does not re-state either rule and cannot disagree
 * with the store about it.
 *
 * @param stationExerciseIds version id → the exercise ids of its stations. A version **missing** from
 *   the map is a version that could not be read, and the caller must not call this until every one is
 *   present: a partial map would silently shorten the list, and a shortened list that reaches zero
 *   renders どの型にも入っていません — an ありません-as-emptiness claim built out of a failed read, which
 *   is exactly the class of bug `00-plan.md` §4.1 rule 1 exists to prevent.
 */
internal fun routinesUsing(
    routines: List<RoutineSummary>,
    stationExerciseIds: Map<Long, List<String>>,
    exerciseId: String,
): List<RoutineSummary> =
    routines.filter { stationExerciseIds[it.versionId]?.contains(exerciseId) == true }

/** 四件 — the count beside 使われている型, right-aligned. Kanji, per §6's Numerals note. */
internal fun usedByCount(routines: Int): String = JapaneseDate.kanjiExtended(routines) + "件"

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

private const val HEADING_BESTS = "最高"
private const val HEADING_LADDER = "段階"
private const val HEADING_USED_BY = "使われている型"

/**
 * The dot column's width. The spine sits at its centre, which is §3's `x = 26 + 9 dp` measured from
 * the screen edge less this page's own 26.dp of horizontal padding.
 */
private val LADDER_GUTTER = 26.dp

/**
 * 種目の中身 — one movement in full.
 *
 * @param exerciseId the nav argument. An id the catalogue does not know is §3's `UnknownId` state:
 *   **immediate pop, no error page**. There is no user-reachable path that produces it — every entry
 *   point passes an id read out of the same catalogue or off a station row — so it is a programming
 *   error, and failing fast is better than rendering a ghost page about a movement that does not
 *   exist. Deliberately *not* a `FaultPanel`: nothing failed, and もう一度 could only fail again.
 */
@Composable
fun ExerciseDetailScreen(gym: GymViewModel, exerciseId: String, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current

    // Map reads, both of them. No `Loadable`, no spinner, no retry — see the file header.
    val exercise = remember(exerciseId) { ExerciseCatalog.byId(exerciseId) }
    val ladder = remember(exerciseId) { ExerciseCatalog.ladder(exerciseId) }

    LaunchedEffect(exercise) {
        if (exercise == null && gym.stack.value.size > 1) gym.onBack()
    }
    if (exercise == null) return

    // `GymViewModel` exposes no bests — reported — so the read is the repository's own, remembered so
    // a recomposition does not resubscribe. **`exerciseBests`, never `movementBests`**: this page is
    // headed by ONE rung, and `movementBests` rolls a whole ladder into a row named for another
    // (`GymRepository.exerciseBests`). One flow answers both blocks — the tiles take this exercise's
    // row, the ladder asks which rungs have a row at all.
    val bestsFlow = remember(gym) { gym.repository.exerciseBests() }
    val bests by bestsFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)

    // The retry counter for 使われている型's もう一度. It is state and not a plain var because the
    // affordance it drives is the one thing on this page whose reads live inside a `produceState`
    // rather than in a flow `gym.retry()` can re-run — see [rememberRoutinesUsing].
    var attempt by remember(exerciseId) { mutableIntStateOf(0) }
    val usedBy = rememberRoutinesUsing(gym, exerciseId, attempt)

    Column(modifier.fillMaxSize()) {
        ExerciseHeader(
            title = exercise.nameJa,
            subtitle = exerciseDetailSubtitle(exercise),
            onClose = { if (gym.stack.value.size > 1) gym.onBack() },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
        ) {
            // §3 edge case 3's sibling on this page: no cue, no line. 走る has none and gets none.
            exercise.cue?.let { cue ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text = cue,
                    style = TextStyle(fontFamily = Gothic, fontSize = 14.sp, lineHeight = 21.sp, color = c.inkSoft),
                )
            }

            ExerciseSectionHeading(HEADING_BESTS)
            // Four states, four composables, no sharing (`00-plan.md` §4.1 rule 1). まだ やっていません
            // is reachable from exactly one of them, and 記録を読めません from exactly one other.
            when (val state = bests) {
                Loadable.Loading -> ExerciseNotice("読み込み中")
                // §3's `PbFailed`: a one-line 記録を読めません ・ もう一度, and the rest of the page
                // untouched. `FaultStrip` *is* that line — one row, the fault's own sentence through
                // `faultCopy`, and もう一度 only for the faults `DECISIONS.md` §Q6 gives one to.
                is Loadable.Failed -> FaultStrip(fault = state.fault, onRecover = gym::retry)
                is Loadable.Ready -> {
                    val tiles = movementTiles(exerciseBestFor(exercise, state.value))
                    if (tiles == null) ExerciseNotice("まだ やっていません") else BestTileRow(tiles)
                }
            }

            // §3's `NoLadder`: a movement that is its own ladder — プランク, 走る — omits 段階 entirely
            // rather than drawing a ladder of one. `ExerciseCatalog.ladder` returns empty for it.
            if (ladder.isNotEmpty()) {
                ExerciseSectionHeading(HEADING_LADDER)
                // An unread or unreadable bests list marks no rung, which is §3's `NoHistory` ladder —
                // every dot `c.inkFaint`, no いま. The ladder itself still draws, because it is a
                // catalogue fact and cannot fail; only the marking is a claim about the user.
                val rungs = remember(ladder, bests) {
                    ladderRungs(ladder, currentRung(ladder, bests.valueOrNull().orEmpty()))
                }
                LadderBlock(
                    rungs = rungs,
                    // The tap **replaces** the top of the stack, so one Back always leaves the
                    // exercise however many rungs were browsed (`00-plan.md` §3, `GymNav.replaceTop`).
                    onRung = { gym.replaceTop(GymRoute.ExerciseDetail(it.id)) },
                )
            }

            UsedBySection(
                routines = usedBy,
                // Both halves, because the two failures live in different places: `gym.retry()`
                // re-runs the library flow, and the counter re-runs the per-version `suspend` reads
                // that only this page performs (see [rememberRoutinesUsing]).
                onRetry = { gym.retry(); attempt++ },
                // **`go`, not `openRoutine`.** §3 gives this page "**Data out** — none", and
                // `openRoutine` writes `last_touched_*` before navigating, which notifies
                // TABLE_ROUTINE and makes every routine-observing flow re-query — a write and a
                // library re-read for merely browsing from a movement to a type, and a write attempt
                // from a read-only page on a quarantined store. `LibraryDetailScreen`'s station taps
                // already navigate plainly for the same reason.
                onRoutine = { gym.go(GymRoute.RoutineDetail(it)) },
            )

            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * Which routines contain this movement, as a `Loadable` that stays one.
 *
 * The library list is a flow and the station ids are a `suspend` read per version, so the two are
 * joined here rather than in the composable's body. Three outcomes and no fourth:
 *
 * - the library itself failed → **[Loadable.Failed]**, unchanged, so the page reports the store's own
 *   sentence rather than どの型にも入っていません;
 * - any single version read failed → **[Loadable.Failed]** as well, because a list built from a
 *   partial answer is a shorter list, and a shorter list that reaches zero is an emptiness claim
 *   assembled out of a failure (`00-plan.md` §4.1 rule 1);
 * - everything read → **[Loadable.Ready]**, and an empty list then genuinely means どの型にも入って
 *   いません.
 *
 * *Rejected* — `mapNotNull`-ing the failed versions away, which is the shape this loop naturally wants
 * and is the bug above.
 *
 * The cost is one indexed read per routine on entry — eleven of them for the shipped catalogue. It is
 * paid once per visit because `produceState` is keyed on the library value and the exercise, and it is
 * the same N+1 `GymViewModel.cacheStationNames` already pays for search; a single repository read that
 * answered "which routines use this exercise" would be better and lives in `gym/data/`. Reported.
 *
 * @param attempt **the key that makes もう一度 do anything at all.** The reads that actually fail here
 *   are the `suspend` per-version ones inside this producer, and `gym.retry()` cannot reach them: it
 *   re-runs the *observing* flows, and `GymRepository.routines` is a `StateFlow` over a `Loadable.Ready`
 *   data class, so a successful re-query is structurally equal to the value already held and is
 *   **conflated** — no emission, no key change, this producer never restarts, and the FaultStrip's
 *   もう一度 is dead in exactly the case it is drawn for. A counter the caller bumps is a key that
 *   cannot compare equal to itself, which is the whole requirement.
 */
@Composable
private fun rememberRoutinesUsing(
    gym: GymViewModel,
    exerciseId: String,
    attempt: Int,
): Loadable<List<RoutineSummary>> {
    val library by gym.routines.collectAsStateWithLifecycle()
    return produceState<Loadable<List<RoutineSummary>>>(Loadable.Loading, library, exerciseId, attempt) {
        val loaded = library
        value = when (loaded) {
            Loadable.Loading -> Loadable.Loading
            is Loadable.Failed -> loaded
            is Loadable.Ready -> {
                val versions = loaded.value.map { it.versionId }.distinct()
                val read = versions.associateWith { gym.repository.routineVersion(it) }
                val fault = read.values.firstNotNullOfOrNull { (it as? Loadable.Failed)?.fault }
                when {
                    fault != null -> Loadable.Failed(fault)
                    read.values.any { it !is Loadable.Ready } -> Loadable.Loading
                    else -> Loadable.Ready(
                        routinesUsing(
                            routines = loaded.value,
                            stationExerciseIds = read.mapValues { (_, snapshot) ->
                                (snapshot as Loadable.Ready).value.stations.map { it.exerciseId }
                            },
                            exerciseId = exerciseId,
                        ),
                    )
                }
            }
        }
    }.value
}

/**
 * The movement's name over its pattern and coefficient, with とじる pinned to the top.
 *
 * `Alignment.Top` and `weight(1f)` are `CalendarScreen.kt:102`'s and `LibraryDetailScreen`'s: a long
 * name must push the action nowhere. Title and subtitle are one node, because 腕立て伏せ and 押す read
 * as two orphan fragments when announced apart.
 */
@Composable
private fun ExerciseHeader(title: String, subtitle: String, onClose: () -> Unit) {
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
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = subtitle,
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            HeaderAction(label = "とじる", description = "とじる", color = c.inkFaint, onClick = onClose)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}

/** A section label: 最高, 段階. `heading()` so TalkBack can jump between them. */
@Composable
private fun ExerciseSectionHeading(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).semantics { heading() },
        style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
    )
}

/**
 * 読み込み中 / まだ やっていません / どの型にも入っていません — a section-sized line of ink.
 *
 * One composable for three sentences is safe **because it holds no branch**: the caller has already
 * chosen the words in a `when` over the `Loadable`, and this draws whatever it is handed. What
 * `00-plan.md` §4.1 rule 1 forbids is a composable that *decides* between loading, empty and failed —
 * `LibraryDetailScreen.DetailNotice` is the same shape for the same reason.
 */
@Composable
private fun ExerciseNotice(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
    )
}

/**
 * 最高 — three tiles, read **label first**.
 *
 * 「三十二回、一度に」 reads as a fragment; 「一度に、三十二回」 is a sentence, which is §3's accessibility
 * note and why the semantics string is not the visual order. Same shape as the routine detail's tiles,
 * minus the this-month accent: these are lifetime facts about a movement, not records against a
 * routine, and there is no 「set this month」 to colour.
 */
@Composable
private fun BestTileRow(tiles: List<MovementTile>) {
    val c = LocalTempoColors.current
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
                    style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
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
 * 段階 — the progression ladder, drawn with **real composables and not a `Canvas`** (§3 edge case 7):
 * it needs text, taps and semantics per rung, none of which survive a draw call.
 *
 * The spine is one `Box(width = 1.dp)` behind the rows rather than a segment per gap. Its length is
 * whatever the `Column` measures to, so a two-rung ladder and an eight-rung one both get a line that
 * ends where the rungs do, with no arithmetic. It runs edge to edge of the block including past the
 * first and last dots, which is what §3's mock draws — the ladder continues in both directions
 * conceptually even where the catalogue stops.
 */
@Composable
private fun LadderBlock(rungs: List<LadderRung>, onRung: (Exercise) -> Unit) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth()) {
        // `matchParentSize` rather than `fillMaxSize`: the spine must take the height the rungs
        // measure to without contributing to it, or the Box would try to size itself from the line
        // that is supposed to be as long as the list.
        Box(Modifier.matchParentSize().padding(start = LADDER_GUTTER / 2)) {
            // Decorative: every word the ladder says is said by a rung.
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(c.hair)
                    .clearAndSetSemantics {},
            )
        }
        Column(Modifier.fillMaxWidth()) {
            rungs.forEach { rung -> LadderRungRow(rung = rung, onClick = { onRung(rung.exercise) }) }
        }
    }
}

/**
 * One rung: dot, name, coefficient, and いま when the user is standing on it.
 *
 * Three colours from two booleans, each from a sentence of §3:
 * - the **dot** is `c.accent` on the current rung, `c.inkSoft` on a reached one, `c.inkFaint` above;
 * - the **name** is `c.ink` and Medium on the current rung and `c.inkSoft` on every other, which is
 *   the mock's own note ("current row `c.ink` Medium, others `c.inkSoft`");
 * - **いま** is `c.accent`, Mincho 11.sp ls 3.sp, and is the only accent word in the block.
 *
 * The whole row is one node — `Role.Button`, [rungSemantics] — because the dot and the spine are
 * decoration and announcing them would put two meaningless stops between every pair of names.
 */
@Composable
private fun LadderRungRow(rung: LadderRung, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = rungSemantics(rung)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(LADDER_GUTTER), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            rung.current -> c.accent
                            rung.reached -> c.inkSoft
                            else -> c.inkFaint
                        },
                    ),
            )
        }
        Text(
            text = rung.exercise.nameJa,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 15.sp,
                fontWeight = if (rung.current) FontWeight.Medium else FontWeight.Normal,
                color = if (rung.current) c.ink else c.inkSoft,
            ),
        )
        Text(
            text = exerciseCoefficient(rung.exercise),
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
        )
        if (rung.current) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "いま",
                style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.accent),
            )
        }
    }
}

/**
 * 使われている型 — the routines this movement appears in, each one a tap into its detail page.
 *
 * Four states and four bodies. どの型にも入っていません is §6's own sentence and is reachable **only**
 * from `Ready` with an empty list: an unread or unreadable library renders 読み込み中 or the store's own
 * 記録を読めません ・ もう一度 instead, because "no type uses this movement" is a claim about the user's
 * library that a failed read cannot support (`00-plan.md` §4.1 rule 1).
 *
 * §3 writes no failure state for this block — only 最高 has one — so the `PbFailed` treatment is
 * applied here too rather than a new one invented: same `FaultStrip`, same words, same もう一度.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsedBySection(
    routines: Loadable<List<RoutineSummary>>,
    onRetry: () -> Unit,
    onRoutine: (String) -> Unit,
) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = HEADING_USED_BY,
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        // The count is drawn only when it is known. A 〇件 over a loading list is a number the page
        // does not have, and this is the one heading in the feature the specs *do* print a count on
        // (§3's mock: 使われている型 四件).
        routines.valueOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
            Text(
                text = usedByCount(list.size),
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
            )
        }
    }

    when (routines) {
        Loadable.Loading -> ExerciseNotice("読み込み中")
        is Loadable.Failed -> FaultStrip(fault = routines.fault, onRecover = onRetry)
        is Loadable.Ready ->
            if (routines.value.isEmpty()) {
                ExerciseNotice("どの型にも入っていません")
            } else {
                FlowRow(Modifier.fillMaxWidth()) {
                    routines.value.forEachIndexed { index, summary ->
                        Box(
                            modifier = Modifier
                                .sizeIn(minHeight = 48.dp)
                                .clickable { onRoutine(summary.routineId) }
                                .clearAndSetSemantics {
                                    role = Role.Button
                                    // The separator is decoration and never spoken: it belongs to the
                                    // visible line, not to the name of a routine.
                                    contentDescription = summary.name
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                // 七分間 ・ シンディ ・ … — the separator rides on the preceding name so
                                // it can never wrap to the head of a line on its own.
                                text = if (index == routines.value.lastIndex) {
                                    summary.name
                                } else {
                                    summary.name + " ・ "
                                },
                                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
                            )
                        }
                    }
                }
            }
    }
}
