package io.eddiegulay.tempo.ui.gym

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.eddiegulay.tempo.gym.DEFAULT_REPS
import io.eddiegulay.tempo.gym.DEFAULT_SECONDS
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.MeasureOption
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.RoutineDraft
import io.eddiegulay.tempo.gym.StationDraft
import io.eddiegulay.tempo.gym.WheelOption
import io.eddiegulay.tempo.gym.adjacentPatternClashes
import io.eddiegulay.tempo.gym.allowedMeasures
import io.eddiegulay.tempo.gym.canAddStation
import io.eddiegulay.tempo.gym.clashCopy
import io.eddiegulay.tempo.gym.coefficientLabel
import io.eddiegulay.tempo.gym.defaultPrescription
import io.eddiegulay.tempo.gym.durationKanji
import io.eddiegulay.tempo.gym.matchExercise
import io.eddiegulay.tempo.gym.paceEstimateSec
import io.eddiegulay.tempo.gym.repOptions
import io.eddiegulay.tempo.gym.secondOptions
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.TempoValueWheel
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import kotlinx.coroutines.delay

/*
 * GYM.LIBRARY.STATION_PICKER — 種目をえらぶ (`04-library-records.md` §3).
 *
 * The page exists only to edit one station of `GYM.LIBRARY.BUILDER`'s draft, and everything about it
 * follows from that:
 *
 * - **It never writes to the database.** §3's Data out is one line — "returns a `Station` to the
 *   draft" — and this page honours it by calling [BuilderDraftHolder.applyStation] and nothing else.
 *   The single write in the whole authoring flow is 保存 on the builder.
 * - **There is no Loading state, no Empty state and no Error state for the list.** The catalogue is an
 *   in-memory map (`00-plan.md` §2 row 6, and `ExerciseCatalog`'s own KDoc says so "loudly, because it
 *   reads as an omission otherwise"). A spinner over a lookup table would be a lie about where the data
 *   is. `NoMatch` is a *search* result and is a different composable saying a different thing.
 * - **Back is やめる and raises no prompt.** §3's back behaviour states the asymmetry with the builder
 *   outright, "so a reviewer does not fix it": one station is a small enough unit that a second dialog
 *   is friction, and the builder's own prompt still guards the routine as a whole. No `BackHandler` is
 *   registered here at all — the shell's pop is exactly the behaviour wanted.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The words, and what the list shows
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** 「保存、種目をえらんでください」 — §3's rule that a disabled word says why (accessibility). */
internal fun pickerSaveSemantics(canSave: Boolean): String =
    if (canSave) "保存" else "保存、種目をえらんでください"

/** 「腕立て伏せ、押す、難度 一.〇」 — §3's exercise row description, verbatim. */
internal fun exerciseSemantics(exercise: Exercise): String =
    exercise.nameJa + "、" + exercise.pattern.label + "、難度 " + coefficientLabel(exercise.difficulty)

/**
 * 「秒数、この方式では使えません」 — a disabled chip carries its reason in its description (§3).
 *
 * An enabled chip is its own label and nothing more; there is no documented word for "available", and
 * a chip that announced one would be describing the absence of a problem.
 */
internal fun measureSemantics(option: MeasureOption): String =
    listOfNotNull(option.measure.label, option.reason).joinToString("、")

/**
 * 目安 — what this station is expected to take, and **labelled 目安 every single time it appears**
 * (§3, picker edge case 5). It never advances anything, and the label is what says so.
 *
 * Null for `MAX_EFFORT`: `paceEstimateSec` returns null because there is nothing to estimate, and §3's
 * `MaxEffortSelected` state removes the line rather than printing a guess beside できるところまで.
 *
 * The duration is rendered through [durationKanji] — 四十秒, 一分三十秒 — because this is a duration the
 * **app computed**, which is the half of `DECISIONS.md` §Q10 that `durationKanji` owns. The wheel above
 * it is the other half and stays on bare seconds. §3's mock draws this line as 「目安 〇:四十」, a
 * kanji-digit clock form that no formatter in this app produces and that §6's numerals note does not
 * describe; the two documented forms are `durationKanji` and the arabic `clockDuration` of the
 * breakdown rows, and an estimate is read rather than watched. Disclosed rather than transcribed.
 */
internal fun paceLine(seconds: Int?): String? = seconds?.let { "目安 " + durationKanji(it) }

/**
 * The catalogue as the picker draws it: grouped by pattern, in [Pattern]'s declaration order, with the
 * search applied.
 *
 * The order is design §9's alternation (押す → 引く → しゃがむ → 股関節 → 体幹 → 移動 → 跳ぶ) and
 * `ExerciseCatalog.byPattern` already preserves it — sorting here would quietly undo a decision made
 * about fatigue. A group the search emptied is dropped, so a heading never stands over nothing.
 *
 * The query is **trimmed before folding** (§3, picker edge case 6): leading space is what a Japanese
 * IME leaves behind between conversions, and a search that matched nothing because of it would look
 * like the catalogue was missing a movement.
 */
internal fun pickerGroups(
    byPattern: Map<Pattern, List<Exercise>>,
    query: String,
): List<Pair<Pattern, List<Exercise>>> {
    val needle = query.trim()
    return byPattern.entries
        .map { (pattern, exercises) -> pattern to exercises.filter { matchExercise(it, needle) } }
        .filter { it.second.isNotEmpty() }
}

/**
 * The clash this choice would create, shown **here, inline, before the user commits** (§3, picker edge
 * case 4) — cheaper than discovering it back in the builder.
 *
 * It is asked of the *prospective* draft: the station list as it would be with this exercise at this
 * position, so `adjacentPatternClashes` answers the same question it will answer on the builder a
 * moment later, wrap pair included. Only a clash involving this position is reported — the routine's
 * other clashes are the builder's lines and are already on that page.
 *
 * @param index null for ＋ 加える, where the prospective position is the end of the list.
 */
internal fun prospectiveClash(
    draft: RoutineDraft,
    index: Int?,
    exerciseId: String,
    pattern: (String) -> Pattern?,
    name: (String) -> String,
): String? {
    // Only the exercise id matters to a pattern clash, so the measure and its value are placeholders
    // and deliberately not the prescription being edited: a warning about fatigue does not depend on
    // how many reps were dialled.
    val placeholder = StationDraft(exerciseId, Measure.REPS, DEFAULT_REPS, null)
    val stations = when {
        index == null -> draft.stations + placeholder
        index in draft.stations.indices -> draft.stations.toMutableList().also { it[index] = placeholder }
        else -> return null
    }
    val position = index ?: stations.lastIndex
    val clash = adjacentPatternClashes(draft.copy(stations = stations), pattern)
        .firstOrNull { it.firstIndex == position || it.secondIndex == position }
        ?: return null
    return clashCopy(
        name(stations[clash.firstIndex].exerciseId),
        name(stations[clash.secondIndex].exerciseId),
    )
}

/**
 * The station this page would hand back. `MAX_EFFORT` carries neither number, and the schema agrees.
 *
 * **[note] is threaded through rather than defaulted away.** `applyStation` *replaces* the station, and
 * there is no note field on this page, so a station built without one destroys whatever the routine
 * carried the moment 保存 is pressed — with nothing changed. That is not hypothetical: `BuiltInCatalog`
 * gives マーフ's two run legs `note = "一マイル"` and says outright that "there is no note column on a
 * version, and the station is the only place the routine has to say it", and タバタ / デス・バイ carry
 * 種目は自由に. Opening one of those stations and pressing 保存 would delete the only record of the
 * distance, and — because `structuralHash` includes the note — would also make the draft dirty for an
 * edit the user never made, raising the discard prompt on the way back out.
 *
 * The caller carries it **only while the exercise is unchanged** ([carriedNote]): a note about 一マイル
 * is a fact about running a mile and does not survive swapping the movement for a burpee.
 */
internal fun stationOf(
    exerciseId: String,
    measure: Measure,
    reps: Int,
    seconds: Int,
    note: String? = null,
): StationDraft =
    when (measure) {
        Measure.REPS -> StationDraft(exerciseId, measure, reps, null, note)
        Measure.DURATION -> StationDraft(exerciseId, measure, null, seconds, note)
        Measure.MAX_EFFORT -> StationDraft(exerciseId, measure, null, null, note)
    }

/**
 * The note [stationOf] should carry: the one the station already had, and only while it is still about
 * the same movement.
 *
 * Null for ＋ 加える — a new station has no note and this page cannot author one — and null the moment
 * the exercise changes, which is the whole of the rule stated once so both the save and its test read
 * the same sentence.
 */
internal fun carriedNote(existing: StationDraft?, exerciseId: String): String? =
    existing?.note?.takeIf { existing.exerciseId == exerciseId }

/**
 * The はかり方 this page must open on, corrected against the engine that will have to run it.
 *
 * §3 states the refusal once and it applies to **both** ways a measure gets chosen. `onSelect` already
 * corrected a chip the engine refuses; an `EditMode` open did not, and `migrateDraft` does not rewrite
 * station measures — so a routine whose engine was changed to 完走 with a `DURATION` station on it
 * opened the picker showing a chip that was greyed *and* 選択中 at once, the wheel for a measure this
 * engine will not accept, and a live 保存 that re-committed it.
 *
 * One helper, called from the seed and from the selection, so the two cannot drift apart.
 */
internal fun seededMeasure(engine: Engine, requested: Measure?): Measure {
    val allowed = allowedMeasures(engine)
    return allowed.firstOrNull { it.measure == requested && it.enabled }?.measure
        ?: allowed.first { it.enabled }.measure
}

/** できるところまで — §6's word for the wheel that is not there, and the whole `MaxEffortSelected` state. */
private const val OPEN_ENDED = "できるところまで"

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * 種目をえらぶ — one exercise, one prescription, one save.
 *
 * @param index the station being edited, or null for ＋ 加える. It is the whole difference between §3's
 *   `EditMode` (opens selected, 削除 present, 保存 live immediately) and `NothingSelected`.
 */
@Composable
fun StationPickerScreen(gym: GymViewModel, index: Int?, modifier: Modifier = Modifier) {
    val holder: BuilderDraftHolder = viewModel()
    val state by holder.state.collectAsStateWithLifecycle()
    val session = state

    // The builder is directly below on the stack and seeded the draft before it could push this page,
    // so a null session means the process was rebuilt around a stack this page cannot serve. Leaving is
    // the only honest answer: there is no draft to edit and nothing to say about one.
    LaunchedEffect(session == null) {
        if (session == null && gym.stack.value.size > 1) gym.onBack()
    }

    // The picker is the builder's other half, so it keeps the same clearing rule: a HOME press out of
    // the shell from *this* page must drop the draft too (`01-shell.md` §A.5).
    DisposableEffect(Unit) {
        onDispose { if (!builderDraftSurvives(gym.stack.value)) holder.clear() }
    }

    if (session == null) {
        Box(modifier.fillMaxSize())
        return
    }

    val draft = session.draft
    val existing = index?.let { draft.stations.getOrNull(it) }

    var selectedId by remember { mutableStateOf(existing?.exerciseId) }
    // Corrected at the open, not only on select: see [seededMeasure].
    var measure by remember { mutableStateOf(seededMeasure(draft.engine, existing?.measure)) }
    var reps by remember { mutableStateOf(existing?.reps ?: session.lastReps ?: DEFAULT_REPS) }
    var seconds by remember { mutableStateOf(existing?.seconds ?: session.lastSeconds ?: DEFAULT_SECONDS) }
    var query by remember { mutableStateOf("") }

    val chosen = selectedId
    // ＋ 加える past the cap cannot land, and the builder greys its own row for the same reason; this is
    // the second gate rather than the first, and it keeps 保存 from writing a station into a full list.
    val canSave = chosen != null && (index != null || canAddStation(draft))

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        PickerHeader(
            canSave = canSave,
            onCancel = { if (gym.stack.value.size > 1) gym.onBack() },
            onSave = {
                val id = chosen ?: return@PickerHeader
                holder.applyStation(
                    index = index,
                    // The note the station already carried, kept while it is still the same movement —
                    // `applyStation` replaces the station, so anything not handed back here is deleted.
                    station = stationOf(id, measure, reps, seconds, note = carriedNote(existing, id)),
                    // Per measure, never carried across: twenty reps is not twenty seconds
                    // (§3, picker edge case 1). Only the measure just used updates its memory.
                    lastReps = reps.takeIf { measure == Measure.REPS },
                    lastSeconds = seconds.takeIf { measure == Measure.DURATION },
                )
                if (gym.stack.value.size > 1) gym.onBack()
            },
        )

        PickerBody(
            draft = draft,
            index = index,
            engine = draft.engine,
            query = query,
            onQuery = { query = it },
            selectedId = chosen,
            measure = measure,
            reps = reps,
            seconds = seconds,
            onSelect = { exercise ->
                selectedId = exercise.id
                // Landing on a measure this engine refuses would be a selection the user cannot save;
                // the first allowed chip is the honest opening, and 回数 is it for six of seven engines.
                measure = seededMeasure(draft.engine, measure)
            },
            onMeasure = { chosenMeasure ->
                measure = chosenMeasure
                val prescription = defaultPrescription(chosenMeasure, reps, seconds)
                prescription.reps?.let { reps = it }
                prescription.seconds?.let { seconds = it }
            },
            onReps = { reps = it },
            onSeconds = { seconds = it },
            onRemove = index?.let { at ->
                {
                    holder.removeStation(at)
                    if (gym.stack.value.size > 1) gym.onBack()
                }
            },
        )
    }
}

/** 種目をえらぶ, with やめる and the 保存 that writes to the draft. */
@Composable
private fun PickerHeader(canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "種目をえらぶ",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(label = "やめる", description = "やめる", color = c.inkFaint, onClick = onCancel)
            HeaderAction(
                label = "保存",
                description = pickerSaveSemantics(canSave),
                color = if (canSave) c.accent else c.inkFaint,
                enabled = canSave,
                onClick = onSave,
            )
        }
    }
}

/**
 * The search, the catalogue, and — once something is chosen — the prescription.
 *
 * `imePadding()` plus a scroll to the block on selection (§3, picker edge case 7): the wheel must never
 * be born under the keyboard. The block is the last thing on the page, so scrolling to the end of the
 * content is what brings it fully into view, and the wait is the unfold's own 220ms — animating to a
 * `maxValue` measured mid-unfold lands short by however much the block had left to grow.
 */
@Composable
private fun PickerBody(
    draft: RoutineDraft,
    index: Int?,
    engine: Engine,
    query: String,
    onQuery: (String) -> Unit,
    selectedId: String?,
    measure: Measure,
    reps: Int,
    seconds: Int,
    onSelect: (Exercise) -> Unit,
    onMeasure: (Measure) -> Unit,
    onReps: (Int) -> Unit,
    onSeconds: (Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val scroll = rememberScrollState()
    val groups = remember(query) { pickerGroups(ExerciseCatalog.byPattern(), query) }
    val selected = selectedId?.let { ExerciseCatalog.byId(it) }

    LaunchedEffect(selectedId) {
        if (selectedId == null) return@LaunchedEffect
        delay(UNFOLD_MILLIS)
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 26.dp)
            .imePadding(),
    ) {
        SearchField(query = query, onQuery = onQuery)

        if (groups.isEmpty()) {
            // A search that found nothing. **Not** an empty catalogue and never worded as one — there
            // is no such state here, which is why this composable is reached only through a query.
            NoMatchState()
        } else {
            groups.forEach { (pattern, exercises) ->
                PatternHeading(pattern.label)
                exercises.forEach { exercise ->
                    ExerciseRow(
                        exercise = exercise,
                        selected = exercise.id == selectedId,
                        onClick = { onSelect(exercise) },
                    )
                }
            }
        }

        // §3's `Selected` state, unfolding in place. Absent entirely while nothing is chosen: an empty
        // prescription block would be a set of controls for a station that does not exist.
        Column(Modifier.fillMaxWidth().animateContentSize(tween(220, easing = LinearOutSlowInEasing))) {
            if (selected != null) {
                PrescriptionBlock(
                    draft = draft,
                    index = index,
                    engine = engine,
                    exercise = selected,
                    measure = measure,
                    reps = reps,
                    seconds = seconds,
                    onMeasure = onMeasure,
                    onReps = onReps,
                    onSeconds = onSeconds,
                    onRemove = onRemove,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The unfold's own duration — §3's `animateContentSize` 220ms, waited out before the scroll. */
private const val UNFOLD_MILLIS = 220L

/** さがす — the library index's field, in the one other place §6 gives it. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = LocalTempoColors.current
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.ink),
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "さがす" },
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 10.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "さがす",
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

/** 押す / 引く / しゃがむ … — §6's pattern words, as a heading TalkBack can jump between. */
@Composable
private fun PatternHeading(label: String) {
    val c = LocalTempoColors.current
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 4.dp)
            .semantics { heading() },
        style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
    )
}

/**
 * One movement: a dot when it is the chosen one, its name, and its coefficient.
 *
 * `Role.RadioButton` with `stateDescription = "選択中"` is §3's accessibility line verbatim — this is a
 * single choice among many, and announcing it as a button would lose that. The dot is drawn rather than
 * spoken; `clearAndSetSemantics` makes the row one node so the coefficient is read as part of the
 * movement instead of as a stray number after it.
 */
@Composable
private fun ExerciseRow(exercise: Exercise, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val description = exerciseSemantics(exercise)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                role = Role.RadioButton
                contentDescription = description
                if (selected) stateDescription = "選択中"
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(5.dp).clip(CircleShape).background(c.accent))
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = exercise.nameJa,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true).padding(end = 12.dp),
            style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.ink),
        )
        Text(
            text = coefficientLabel(exercise.difficulty),
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
        )
    }
}

/**
 * What the chosen movement will ask for: はかり方, the wheel, the 目安, and 削除 on an edit.
 *
 * The block announces itself on unfold through a `liveRegion = Polite` on its heading (§3,
 * accessibility) — it appears below the reading position, so a screen-reader user who has just chosen a
 * movement would otherwise have no way to learn that a set of controls arrived.
 *
 * **A disabled chip stays on screen with its reason** (§3, picker edge cases 2 and 3). That is the
 * opposite of the detail page's rule for 編集 on a built-in, which disappears, and the difference is
 * real: a measure that is available under a *different engine the user can change on the previous page*
 * is information about this engine, not chrome.
 */
@Composable
private fun PrescriptionBlock(
    draft: RoutineDraft,
    index: Int?,
    engine: Engine,
    exercise: Exercise,
    measure: Measure,
    reps: Int,
    seconds: Int,
    onMeasure: (Measure) -> Unit,
    onReps: (Int) -> Unit,
    onSeconds: (Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val c = LocalTempoColors.current
    val measures = remember(engine) { allowedMeasures(engine) }
    val station = remember(exercise.id, measure, reps, seconds) {
        stationOf(exercise.id, measure, reps, seconds)
    }
    val pace = remember(station, exercise) { paceLine(paceEstimateSec(station, exercise)) }
    val clash = remember(draft, index, exercise.id) {
        prospectiveClash(
            draft = draft,
            index = index,
            exerciseId = exercise.id,
            pattern = { ExerciseCatalog.byId(it)?.pattern },
            name = { ExerciseCatalog.byId(it)?.nameJa ?: UNKNOWN_EXERCISE },
        )
    }

    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    Text(
        text = exercise.nameJa,
        modifier = Modifier
            .padding(top = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = TextStyle(fontFamily = Mincho, fontSize = 20.sp, color = c.ink),
    )
    exercise.cue?.let { cue ->
        Text(
            text = cue,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "はかり方",
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            measures.forEach { option ->
                MeasureChip(option = option, selected = option.measure == measure) {
                    onMeasure(option.measure)
                }
            }
        }
    }
    // The reason, once, under the chips that carry it. Each disabled chip already announces its own
    // (「秒数、この方式では使えません」); repeating the sentence per chip would draw it twice on an
    // engine that refuses two measures, and §6 gives exactly one string for the refusal.
    if (measures.any { !it.enabled }) {
        Text(
            text = "この方式では使えません",
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
        )
    }

    if (measure == Measure.MAX_EFFORT) {
        // §3's `MaxEffortSelected`: the wheel is replaced and the 目安 line disappears — there is
        // nothing to estimate, and a number here would be the app deciding what "to your limit" means.
        Text(
            text = OPEN_ENDED,
            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            style = TextStyle(fontFamily = Mincho, fontSize = 20.sp, color = c.ink),
        )
    } else {
        val options = if (measure == Measure.REPS) repOptions() else secondOptions()
        ValueWheel(
            options = options,
            selected = if (measure == Measure.REPS) reps else seconds,
            label = measure.label,
            // 回数's wheel is 1..100 and マーフ has stations at 200 and 300, so the value being edited
            // is not always on its own wheel — [mergedWheelOptions] puts it there (`DECISIONS.md` §Q21).
            labelFor = if (measure == Measure.REPS) ::repWheelValueLabel else ::secondWheelValueLabel,
            onSelect = if (measure == Measure.REPS) onReps else onSeconds,
        )
        pace?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkFaint),
            )
        }
    }

    clash?.let {
        Text(
            text = it,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
        )
    }

    // 削除 — edit mode only. There is nothing to delete when the station does not exist yet, and an
    // action that can only refuse is chrome (the same rule that keeps 削除 off a built-in's menu).
    onRemove?.let { remove ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable(onClick = remove)
                .semantics { role = Role.Button; contentDescription = "削除" }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "削除",
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
            )
        }
    }
}

/** One はかり方 chip: 回数 / 秒数 / 限界まで, greyed and inert where the engine refuses it. */
@Composable
private fun MeasureChip(option: MeasureOption, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val description = measureSemantics(option)
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = option.enabled, onClick = onClick)
            .clearAndSetSemantics {
                role = Role.RadioButton
                contentDescription = description
                if (selected) stateDescription = "選択中"
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.measure.label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = if (selected && option.enabled) c.accent else c.inkFaint,
            ),
        )
    }
}

/**
 * The value wheel, with the labels its own options carry — 二十回, 三十秒.
 *
 * Bare seconds, not `durationKanji`: this is the value the user is *choosing* (`DECISIONS.md` §Q10),
 * and the builder's row and the detail page both echo it back in the same words. One accessibility
 * node, for the reason §3 gives — TalkBack must not read all hundred rows.
 *
 * **The list is [mergedWheelOptions]', never [options] raw** (`DECISIONS.md` §Q21): 回数's range is a
 * sensible authoring vocabulary and マーフ's 200- and 300-rep stations are deliberately outside it, so
 * the value being edited has to be merged in or `TempoValueWheel` seeds row 0 and unfolding the row
 * rewrites it.
 */
@Composable
private fun ValueWheel(
    options: List<WheelOption>,
    selected: Int,
    label: String,
    labelFor: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    val merged = remember(options, selected) { mergedWheelOptions(options, selected, labelFor) }
    val values = remember(merged) { merged.map { it.value } }
    val labels = remember(merged) { merged.associate { it.value to it.label } }
    val current = labels[selected] ?: selected.toString()

    Box(
        Modifier.clearAndSetSemantics {
            contentDescription = label
            stateDescription = current
        },
    ) {
        TempoValueWheel(
            values = values,
            selected = selected,
            onSelect = onSelect,
            label = { labels[it] ?: it.toString() },
        )
    }
}

/**
 * 該当する種目はありません — §6's own sentence for a search that matched nothing.
 *
 * Its own composable and reachable only with something typed. There is no empty state for the
 * catalogue and no loading state either, so this is the only "nothing here" this page can say — and it
 * is a statement about the query, not about the app.
 */
@Composable
private fun NoMatchState() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "該当する種目はありません",
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}
