package io.eddiegulay.tempo.ui.gym

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.MovementBest
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.displayCue
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.gym.label
import io.eddiegulay.tempo.gym.matchExercise
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable

/*
 * GYM.LIBRARY.EXERCISE_INDEX — 種目. `04-library-records.md` §3's movement catalogue.
 *
 * **This page has no Loading state, no Empty state and no Error state, and that is the spec's own
 * sentence rather than an omission.** `ExerciseCatalog` is an in-memory map filled once at repository
 * construction (`00-plan.md` §2 row 6), so `all()` is a lookup and not a query: a spinner over it would
 * be a lie about where the data is, and §3 says outright "never Loading, never Empty, never Failed. …
 * Do not add one." Everything the page draws from that map is therefore drawn unconditionally.
 *
 * The **personal bests are the exception, and they are an overlay rather than a gate** — they come off
 * the store, they can fail, and §3's rule for them is one sentence: "A PB-overlay failure simply omits
 * the 最高 fragment — it is an enrichment, never a gate." So `Loadable.Loading` and `Loadable.Failed`
 * both render the card *without* its 最高 fragment, and neither is allowed to blank the row, replace it
 * with 記録を読めません, or turn into 最高 —. That is not the `Loadable` doctrine being bent: the doctrine
 * forbids an unread store rendering as an *emptiness claim about the user's data*, and a card with no
 * best fragment claims nothing at all. `GYM.LIBRARY.EXERCISE_DETAIL`, where 最高 is a section the user
 * came to read, keeps all four states separate.
 *
 * Everything above the composables is pure and JUnit-tested in
 * `test/ui/gym/ExerciseCatalogueCopyTest.kt`, per `00-plan.md` §4.1 rule 10 — a decision made inside a
 * composable can only be checked by launching an emulator.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure copy and shaping — Android-free
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * 走る's id, and the only place a catalogue id appears in the UI.
 *
 * §3's EXERCISE_INDEX edge case 5 names exactly one movement — "走る has no rep semantics: its
 * coefficient renders `—` and the `秒/回` fragment is suppressed" — and **no column of `Exercise`
 * distinguishes it.** It is `LOCOMOTION` and isometric, and so is もも上げ, whose 〇.七 is a real
 * weighting the user's volume is scored with; a rule written over those two flags would hide a number
 * that means something in order to hide one that does not.
 *
 * *Rejected* — deriving it from `isIsometric` alone, which would blank プランク's 一.〇 as well. The
 * coefficient of a hold is exactly as real as a push-up's: it is what `volume_units` multiplies by
 * (`00-plan.md` §2 row 13). Only 走る's is meaningless, because nothing about a run is counted.
 *
 * Reported: closing this properly wants a `countsReps` (or a nullable `difficulty`) on the catalogue
 * row, which lives in `gym/data/` and is another unit's file.
 */
private const val RUN_ID = "run"

/** Whether this movement's reps are a countable thing — §3 edge case 5's predicate, see [RUN_ID]. */
internal fun countsReps(exercise: Exercise): Boolean = exercise.id != RUN_ID

/**
 * 一.〇 / 1.0 / — , through the one coefficient formatter ([Strings.fmt]).
 *
 * `fmt.coefficient(null)` is already `—`, so edge case 5 is expressed by *what is passed*, not by a
 * second branch here. `Numerals.kt`'s own KDoc names 走る as the caller that passes null, and the
 * English implementation keeps that hole exactly as it is: a movement with no rep semantics renders no
 * value, rather than a number that would imply you could count it.
 */
internal fun exerciseCoefficient(exercise: Exercise, strings: Strings): String =
    strings.fmt.coefficient(exercise.difficulty.takeIf { countsReps(exercise) })

/**
 * 二.〇秒/回 — the meta line's pace fragment, or null when there is no pace to state.
 *
 * **Suppressed for every isometric, not only for 走る**, and that is a derivation from the shipped
 * model rather than a widening of edge case 5. `Exercise.secondsPerRep` "means two different things
 * and the schema comment says so": for a rep movement it is a pacer estimate, and for a hold it is
 * redefined as *the seconds that count as one volume unit* (`GymModels.kt`, §F.1, §D.5). Printing
 * プランク's ten as 十.〇秒/回 would be labelling a volume conversion as a pace — a wrong fact, where
 * omitting it is merely a missing one. 走る is isometric, so edge case 5's second half falls out of
 * the same rule.
 */
internal fun paceLabel(exercise: Exercise, strings: Strings): String? =
    if (exercise.isIsometric) null else strings.gymExercise.pace(strings.fmt.coefficient(exercise.secondsPerRep))

/** 最高 三十二回 — §3's own fragment. 最高 is §6's word, the count is [Formats.reps]'. */
internal fun bestRepsLabel(singleSetReps: Int, strings: Strings): String? =
    if (singleSetReps <= 0) null else strings.gymExercise.bestReps(strings.fmt.reps(singleSetReps))

/**
 * 十六の動き — §6's exercise-index subtitle, counted through [Strings.fmt] as that row requires.
 *
 * Counts the **catalogue**, never the matches. The subtitle says what this page is a list of, and a
 * number that shrank as the user typed would be answering a different question from the one the line
 * asks; §3's `SearchActive` state changes the *list's* shape and says nothing about the header.
 */
internal fun exerciseIndexSubtitle(catalogueSize: Int, strings: Strings): String =
    strings.gymExercise.indexSubtitle(strings.fmt.count(catalogueSize))

/**
 * One exercise card's words. A shape, so the composable is a layout and nothing else — the same
 * bargain [RoutineCardCopy] makes on the sibling page.
 *
 * ```
 * ┌────────────────────────────────────────┐
 * │ 腕立て伏せ                       一.〇 │  name · coefficient
 * │ 体は一直線に                           │  cue, omitted when null (edge case 3)
 * │ 押す ・ 二.〇秒/回        最高 三十二回 │  meta · best
 * └────────────────────────────────────────┘
 * ```
 *
 * [description] is the whole card as one TalkBack node and its first three fragments are the *station
 * picker's* documented sentence — 「腕立て伏せ、押す、難度 一.〇」 (§3, STATION_PICKER accessibility).
 * §3 writes no accessibility paragraph for this page, and borrowing the one written for the other
 * surface that lists the same rows verbatim is applying documented copy rather than inventing it —
 * the same move `prescriptionLabel` made for 限界まで. The **cue is deliberately not in it**: it is a
 * sentence of coaching, it is the reason 種目の中身 exists, and reading it on every one of twenty-three
 * rows would bury the three words that tell them apart.
 *
 * @param singleSetReps the movement's best recorded set, or null when the store has not said. Null is
 *   *three* different facts here — not read yet, unreadable, and never performed — and all three
 *   correctly render the same nothing, because §3 makes the fragment an enrichment (see this file's
 *   header) and edge case 4 forbids the alternative outright: "No history → no 最高 fragment, not
 *   最高 —."
 */
internal data class ExerciseCardCopy(
    val name: String,
    val coefficient: String,
    val cue: String?,
    val meta: String,
    val best: String?,
    val description: String,
)

internal fun exerciseCardCopy(exercise: Exercise, singleSetReps: Int?, strings: Strings): ExerciseCardCopy {
    val coefficient = exerciseCoefficient(exercise, strings)
    val best = singleSetReps?.let { bestRepsLabel(it, strings) }
    val name = exercise.displayName(strings)
    return ExerciseCardCopy(
        // The row's own second column, drawn for the first time. `name_en` has been NOT NULL and
        // correctly seeded for all twenty-three movements since schema v1 and was simply never read.
        name = name,
        coefficient = coefficient,
        // §3 edge case 3: no cue, no line — never a blank one, which reads as a missing value. The
        // null survives translation: `displayCue` returns null for the six movements §F.1 gives no cue.
        cue = exercise.displayCue(strings),
        meta = listOfNotNull(exercise.pattern.label(strings), paceLabel(exercise, strings))
            .joinToString(strings.fmt.separator),
        best = best,
        description = listOfNotNull(
            name,
            exercise.pattern.label(strings),
            strings.gymExercise.difficulty(coefficient),
            best,
        ).joinToString(strings.fmt.listSeparator),
    )
}

/**
 * A run of cards under one heading, or — when a query is active — the whole list under none.
 *
 * [pattern] is null for the flat search result, which is §3's `SearchActive` state: "pattern headings
 * collapse to a single flat list; grouping under a query is noise."
 */
internal data class ExerciseSection(val pattern: Pattern?, val exercises: List<Exercise>)

/**
 * The catalogue as this page lists it: filtered, ordered, and grouped unless a query says otherwise.
 *
 * Two orderings, both §3's and neither of them alphabetical:
 *
 * 1. **Within a pattern, by difficulty ascending, ties by name** (edge case 1) — "so a ladder reads
 *    top-to-bottom as it should be climbed". The name breaks ties rather than the id, because the tie
 *    is only ever visible as two names in a column — and for the same reason it is the **displayed**
 *    name, [displayName], not `nameJa`.
 *
 *    That is a deliberate behaviour change and it is visible: 空気椅子 sorts before 踏み台昇降 by
 *    codepoint, and "Step-up" sorts before "Wall sit" by letter, so those two rows swap under an
 *    English UI. Sorting an English list by kanji block is an order no reader of that list can see,
 *    and a tiebreak nobody can read is not an order worth preserving. **Only within-section order
 *    moves**; the sections themselves are [Pattern]'s declaration order and are untouched.
 *
 *    *Note* — `ExerciseCatalogSource.ladder` made the twin fix the other way, tiebreaking on `id`.
 *    That is right there and wrong here: a ladder's order *is* its meaning, so it must not reshuffle
 *    when the language changes; this list's order is a reading aid for the labels beside it.
 * 2. **Pattern order is [Pattern]'s declaration order** (edge case 2) — 押す → 引く → しゃがむ → 股関節 →
 *    体幹 → 移動 → 跳ぶ, which is design §9's fatigue alternation. Sorting it would quietly undo a
 *    decision about the body. The enum's own KDoc says the same thing from the other end, so this
 *    function reads `entries` rather than restating the list.
 *
 * The flat search list keeps that same sequence with the headings removed, rather than re-ranking by
 * match quality: the user is scanning for a name they already know, and a list that reorders itself
 * per keystroke is one they cannot re-find their place in.
 *
 * Empty patterns are dropped. A heading over nothing is furniture announcing an absence, and 移動
 * genuinely empties under most queries.
 */
internal fun exerciseSections(
    catalogue: List<Exercise>,
    query: String,
    strings: Strings,
): List<ExerciseSection> {
    val matched = catalogue
        .filter { matchExercise(it, query) }
        .sortedWith(compareBy({ it.pattern.ordinal }, { it.difficulty }, { it.displayName(strings) }))
    if (matched.isEmpty()) return emptyList()
    // `matchExercise` treats a blank query as matching everything, so "is anything being searched" is
    // asked of the query and not of the result — a query of spaces must not collapse the headings.
    if (query.isNotBlank()) return listOf(ExerciseSection(pattern = null, exercises = matched))
    return Pattern.entries.mapNotNull { pattern ->
        matched.filter { it.pattern == pattern }
            .takeIf { it.isNotEmpty() }
            ?.let { ExerciseSection(pattern, it) }
    }
}

/**
 * The best single set the store attributes to **this exercise specifically**, keyed by id.
 *
 * Fed by `GymRepository.exerciseBests()` — **one row per movement** — and never by `movementBests()`,
 * whose rows are ladder-family roll-ups named for the rung with the most lifetime volume. Matching
 * those on `exerciseId` was the shipped bug: a 膝つき腕立て set of thirty-two printed itself on the
 * 腕立て伏せ card while every other rung of the family showed nothing, which §3 edge case 4 defines as
 * "no history". One wrong number and six false silences, all from a read that succeeded.
 *
 * A zero is dropped rather than mapped: `movementBests`'s row can carry a zero best when the family
 * has volume but no counted set, and 最高 〇回 is a record of nothing. §3 edge case 4's rule for that
 * card is "no 最高 fragment, not 最高 —", so the absent key is the whole answer.
 */
internal fun bestRepsByExercise(bests: List<MovementBest>): Map<String, Int> =
    bests.filter { it.singleSetReps > 0 }.associate { it.exerciseId to it.singleSetReps }

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * 種目 — the movement catalogue as a browsable thing.
 *
 * **Search state is the composable's own**, unlike the sibling 型 page whose query, chips and scroll
 * are hoisted into `GymViewModel`. §3's back behaviour for this page is one clause — "straight pop;
 * search state discarded" — so there is deliberately **no `BackHandler`**: back pops the page and the
 * `remember` goes with it, which is exactly what that clause asks for. The 型 index needs the opposite
 * (its own back closes the search row and clears the query in one press) and hoists its state to
 * survive a tab switch; this page is not a tab root and cannot be switched away from.
 *
 * The bests arrive as a `Loadable` and are unwrapped **here**, at the point where §3 licenses it, and
 * nowhere deeper: [exerciseCardCopy] takes a nullable count so nothing below this line can tell a
 * failed read from a movement never performed, which is precisely the confusion that would matter if
 * this were a gate. It is not — see the file header.
 */
@Composable
fun ExerciseIndexScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    // A map read, not a query. `remember` because the catalogue cannot change for the life of the
    // process, and re-sorting twenty-three rows on every recomposition of a scrolling list is work
    // done for a value that is already known.
    val catalogue = remember { ExerciseCatalog.all() }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // `GymViewModel` exposes no bests — reported — so the read is the repository's own, remembered so
    // a recomposition does not resubscribe. Seeded `Loading`, and Loading is treated as "no fragment
    // yet" rather than as "none", which is the enrichment rule and not a doctrine breach.
    // **`exerciseBests`, never `movementBests`**: one card is one movement, and a family roll-up hung
    // on a card is a number about six other rows (see [bestRepsByExercise]).
    val bestsFlow = remember(gym) { gym.repository.exerciseBests() }
    val bests by bestsFlow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
    val bestReps = remember(bests) { bestRepsByExercise(bests.valueOrNull().orEmpty()) }

    // Keyed on `s` as well: the tiebreak sorts on the displayed name, so a language change reorders
    // the rows within a section and a cached list would be the old order under the new labels.
    val sections = remember(catalogue, query, s) { exerciseSections(catalogue, query, s) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(
                    text = s.gymExercise.indexTitle,
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = exerciseIndexSubtitle(catalogue.size, s),
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            HeaderAction(
                // 探す / とじる, §6's own pair — the word states what the tap does, not what is open.
                // There is no second とじる for the page itself: this page is pushed and back pops it,
                // and two identical words on one header would be two different exits.
                label = if (searchOpen) s.gymExercise.closeSearch else s.gymExercise.openSearch,
                description = if (searchOpen) s.gymExercise.closeSearch else s.gymExercise.openSearch,
                color = c.inkFaint,
                onClick = {
                    if (searchOpen) query = ""
                    searchOpen = !searchOpen
                },
            )
        }

        ExerciseSearchField(open = searchOpen, query = query, onQuery = { query = it })

        Box(Modifier.fillMaxWidth().weight(1f)) {
            // 該当する種目はありません is a claim about the *search*, so it is reachable only from a
            // search. With no query an empty list can only mean the catalogue was never installed —
            // a programming state, not a user one — and §3's "never Empty" says the page does not
            // have words for it; drawing the search's sentence there would blame the user's typing
            // for the repository's construction order.
            if (sections.isEmpty() && query.isNotBlank()) {
                ExerciseNoMatch()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    // The seated tab bar, shallower than the launcher's floating dock (`00-plan.md` §3.2).
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    sections.forEach { section ->
                        // Never `stickyHeader` (`00-plan.md` §4.1 rule 3) — plain items, as the
                        // calendar's day headers already are.
                        section.pattern?.let { pattern ->
                            item(key = "head:${pattern.name}") { PatternHeading(pattern) }
                        }
                        items(
                            items = section.exercises,
                            // Sections partition the catalogue, so an id is unique across the list —
                            // but the flat search branch and the grouped one are different lists, and
                            // prefixing keeps the key stable when a query empties.
                            key = { "exercise:${it.id}" },
                        ) { exercise ->
                            ExerciseCard(
                                copy = exerciseCardCopy(exercise, bestReps[exercise.id], s),
                                onOpen = { gym.go(GymRoute.ExerciseDetail(exercise.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The search field, unfolding in place — `animateContentSize(220ms, LinearOutSlowIn)` is §3's own
 * figure for the sibling page's row and the `PickerRow` idiom from `EventComposeScreen`.
 *
 * There are **no filter chips**. §6 documents tier, engine and duration chips for 型 and none for 種目,
 * and a chip row over 難度 or 押す/引く would be a filter nobody wrote words for.
 */
@Composable
private fun ExerciseSearchField(open: Boolean, query: String, onQuery: (String) -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val focusRequester = remember { FocusRequester() }

    Column(Modifier.fillMaxWidth().animateContentSize(tween(220, easing = LinearOutSlowInEasing))) {
        if (!open) return@Column

        // A search row that appears and then waits to be tapped again is two gestures for one intent.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .focusRequester(focusRequester)
                .semantics { contentDescription = s.gymExercise.searchPlaceholder },
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = 8.dp)) {
                        if (query.isEmpty()) {
                            Text(
                                text = s.gymExercise.searchPlaceholder,
                                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.inkFaint),
                            )
                        }
                        inner()
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
                }
            },
        )
    }
}

/** 押す / 引く / … — §2's section-heading row, one TalkBack node, and never sticky. */
@Composable
private fun PatternHeading(pattern: Pattern) {
    val c = LocalTempoColors.current
    Text(
        text = pattern.label(LocalStrings.current),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)
            .semantics { heading() },
        style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
    )
}

/**
 * One movement, in the library index's card geometry (§3: "Cards match the library index geometry").
 *
 * One accessibility node with the click re-declared inside `clearAndSetSemantics`, because that
 * modifier wipes the `clickable`'s own semantics. There is **no long press and therefore no
 * `customActions`**: §3's actions for this page are "search · tap row" and nothing else, so
 * `00-plan.md` §4.1 rule 4 has nothing to attach to here.
 *
 * The name gets one line and the cue one, so the card keeps a constant height down the list and the
 * coefficient column stays aligned — the same reason the routine card caps its own lines.
 */
@Composable
private fun ExerciseCard(copy: ExerciseCardCopy, onOpen: () -> Unit) {
    val c = LocalTempoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(c.card, TempoShapes.Card)
            .pressable(TempoShapes.Card, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = copy.description
                role = Role.Button
                onClick { onOpen(); true }
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
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
            Text(
                text = copy.coefficient,
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
            )
        }
        copy.cue?.let { cue ->
            Text(
                text = cue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.5.sp, color = c.inkSoft),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = copy.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = true),
                style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )
            copy.best?.let { best ->
                Text(
                    text = best,
                    style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.accent),
                )
            }
        }
    }
}

/**
 * 該当する種目はありません — §6's own sentence for this page, and **not** 型はまだありません's shape.
 *
 * Its own composable, sharing nothing with anything: §6 flags the trap on the sibling page in bold
 * ("**never** 型はまだありません — that means something else"), and a shared "nothing here" composable
 * is how the two sentences end up one branch apart.
 */
@Composable
private fun ExerciseNoMatch() {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = s.gymExercise.noMatch,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}
