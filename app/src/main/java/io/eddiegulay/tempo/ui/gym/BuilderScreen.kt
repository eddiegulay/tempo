package io.eddiegulay.tempo.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.GymRepository
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.GymWrite
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.RestSlot
import io.eddiegulay.tempo.gym.RoutineDraft
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.gym.StationDraft
import io.eddiegulay.tempo.gym.WheelOption
import io.eddiegulay.tempo.gym.adjacentPatternClashes
import io.eddiegulay.tempo.gym.canAddStation
import io.eddiegulay.tempo.gym.canSave
import io.eddiegulay.tempo.gym.clashCopy
import io.eddiegulay.tempo.gym.engineRows
import io.eddiegulay.tempo.gym.estimateLabel
import io.eddiegulay.tempo.gym.estimateRoutine
import io.eddiegulay.tempo.gym.isDirty
import io.eddiegulay.tempo.gym.migrateDraft
import io.eddiegulay.tempo.gym.moveItem
import io.eddiegulay.tempo.gym.reorderShift
import io.eddiegulay.tempo.gym.restOptions
import io.eddiegulay.tempo.gym.roundOptions
import io.eddiegulay.tempo.gym.structuralHash
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.TempoValueWheel
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * GYM.LIBRARY.BUILDER — 型を作る / 型を編集 (`04-library-records.md` §3).
 *
 * The page is a shell over `gym/BuilderDraft.kt`, `gym/RoutineEstimate.kt` and `gym/PatternWarning.kt`,
 * all of which shipped with the Phase 2 groundwork and are already tested. Nothing here re-derives a
 * decision those files make — most importantly **not dirtiness**: `isDirty` is a value comparison that
 * already knows a drag returned to its original order is clean, and a second opinion computed in a
 * composable is how a back press comes to silently discard a routine.
 *
 * Three things this file does own, and they are the three the spec is emphatic about.
 *
 * 1. **One write, on 保存.** [BuilderDraftHolder.save] is the only call to `saveRoutine` in the page,
 *    it is guarded by `canSave` and by its own `saving` flag, and nothing else in the file touches the
 *    repository. An autosaving builder would insert a `routine_version` per keystroke and every past
 *    session — which pins its version id forever — would start disagreeing with the routine it came
 *    from (`00-plan.md` §2 row 10).
 * 2. **The returned id is adopted.** `saveRoutine` silently copies a built-in into a new user routine
 *    and returns *that* id (`02-data.md` §A.0.3), so the landing page is [builderLanding]'s, computed
 *    from the id that came back rather than the one the page was opened with. Continuing to edit the
 *    original is how the user's next save lands on a routine they believe they replaced.
 * 3. **The discard prompt is owed only when dirty.** `BackHandler(enabled = isDirty(...))`; a clean
 *    builder registers a disabled handler and falls through to the shell's ordinary pop
 *    (`01-shell.md` §A.3, and `GymShell`'s own note that a disabled handler is free).
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Where the draft lives
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The builder's whole editing session — the draft, what it was when it opened, and the two facts
 * `canSave` needs.
 *
 * [original] is null for 作る and is what makes `isDirty`'s two clauses different questions: a new
 * routine is dirty as soon as it has a name or a station, an edit is dirty when its *values* differ.
 *
 * [lastReps] / [lastSeconds] are the station picker's per-measure memory (`04-library-records.md` §3,
 * picker edge case 1: "each type remembers its own last value **within the session**"). They live here
 * rather than in the picker because the picker is opened and closed once per station and the sentence
 * spans the whole edit.
 *
 * [savedRoutineId] is set by [BuilderDraftHolder.save] and read exactly once by the page's landing
 * effect. It is a field rather than a callback because the write outlives the composition that started
 * it — see [BuilderDraftHolder].
 */
internal data class BuilderDraftState(
    val editingId: String?,
    val draft: RoutineDraft,
    val original: RoutineDraft?,
    val loaded: Boolean,
    val saving: Boolean = false,
    val notices: List<String> = emptyList(),
    val fault: GymFault? = null,
    val savedRoutineId: String? = null,
    val lastReps: Int? = null,
    val lastSeconds: Int? = null,
)

/**
 * The draft, held where it can survive the station picker.
 *
 * **`04-library-records.md` §3 puts this in `GymViewModel` and it is not there.** That file belongs to
 * another unit and this one may not add to it, so the draft lives in a `ViewModel` of its own, resolved
 * with `viewModel()` against `MainActivity`'s store by both pages — which gives it exactly the lifetime
 * the spec asks for: it survives the picker round trip, it survives a configuration change, and it is
 * one instance because both call sites resolve the same default key in the same store. **Reported for
 * the agent who owns `GymViewModel`**: this belongs beside `_routineFilter` and its clear belongs in
 * `clearTransientState`, which already names "the builder's unsaved draft" (`01-shell.md` §A.5).
 *
 * Until then the clearing rule is [builderDraftSurvives], applied in both pages' `onDispose`: the draft
 * is kept while a builder or a picker is still somewhere on the stack, and dropped the moment neither
 * is — which covers 保存, やめる, a discarded back press *and* a HOME press out of the shell, in one
 * predicate rather than four call sites that each have to remember.
 *
 * *Rejected* — a top-level `object` holding the same `MutableStateFlow`. It is the same state with a
 * process lifetime instead of an Activity one, it cannot host the save, and it would keep an abandoned
 * draft alive across an Activity recreation the user experienced as leaving the app.
 *
 * **The save runs on [viewModelScope], deliberately.** A `rememberCoroutineScope` is cancelled when the
 * page leaves composition, and the page leaves composition on the very navigation the save triggers; a
 * back press landing between the tap and the transaction would cancel a write the user asked for. The
 * outcome therefore lands in this state, and the page reacts to it, rather than the coroutine reaching
 * back into a composition that may be gone.
 */
internal class BuilderDraftHolder : ViewModel() {

    private val _state = MutableStateFlow<BuilderDraftState?>(null)
    val state: StateFlow<BuilderDraftState?> = _state.asStateFlow()

    /**
     * Start — or keep — the session for [editingId].
     *
     * Idempotent for the same target, which is what makes returning from the picker a no-op: the draft
     * is the same object, so a station the picker changed is still changed and `isDirty` still says so
     * on the next back press ("a forward-and-back within one edit", §3's back behaviour).
     *
     * A *different* target resets, which is the honest answer for the one stack the shell allows to
     * hold two builders (`GymRoute.Builder.singleTop = false`). The consequence is disclosed rather
     * than papered over: with two builders stacked, popping back to the lower one finds the upper
     * one's draft gone and its own reset. One draft is what `01-shell.md` §A.5 models and what
     * `GymViewModel` would hold; a stack of drafts is a different design and needs a spec.
     */
    fun open(editingId: String?, prefs: GymPreferences) {
        val current = _state.value
        if (current != null && current.editingId == editingId) return
        _state.value = BuilderDraftState(
            editingId = editingId,
            draft = newDraft(prefs),
            original = null,
            // 作る has nothing to read, so it is loaded the moment it opens; 編集 waits for [adopt].
            loaded = editingId == null,
        )
    }

    /**
     * The routine has arrived: seed the draft from its head version.
     *
     * Runs **once**, guarded on `loaded`. Re-seeding on a later emission of the same flow would throw
     * away everything typed since — `routineDetail` re-emits on any table change, including the
     * recency bump the user's own tap wrote on the way in.
     */
    fun adopt(editingId: String?, snapshot: RoutineSnapshot, routineId: String) {
        _state.update { current ->
            if (current == null || current.editingId != editingId || current.loaded) return@update current
            val draft = draftOf(snapshot, routineId)
            current.copy(draft = draft, original = draft, loaded = true)
        }
    }

    /** Any edit to the draft. Clears nothing else — a fault stays until the next 保存 answers it. */
    fun edit(transform: (RoutineDraft) -> RoutineDraft) {
        _state.update { current -> current?.copy(draft = transform(current.draft)) }
    }

    /**
     * 方式: the migration and the lines that say what it cost, both from the pure `migrateDraft`.
     *
     * The notices are *replaced*, not accumulated. They describe the change the user just made; keeping
     * the previous engine's notice under the new engine's row would be a sentence about a shape that is
     * no longer on screen (`DECISIONS.md` §Q17 — there are exactly two notices and neither is chrome).
     */
    fun selectEngine(engine: Engine) {
        _state.update { current ->
            if (current == null) return@update current
            val migration = migrateDraft(current.draft, engine)
            current.copy(draft = migration.draft, notices = migration.notices)
        }
    }

    /** The station picker's only write. Draft only — never the database. */
    fun applyStation(index: Int?, station: StationDraft, lastReps: Int?, lastSeconds: Int?) {
        _state.update { current ->
            if (current == null) return@update current
            val stations = current.draft.stations
            val next = when {
                index == null -> if (canAddStation(current.draft)) stations + station else stations
                index in stations.indices -> stations.toMutableList().also { it[index] = station }
                else -> stations
            }
            current.copy(
                draft = current.draft.copy(stations = next),
                lastReps = lastReps ?: current.lastReps,
                lastSeconds = lastSeconds ?: current.lastSeconds,
            )
        }
    }

    fun removeStation(index: Int) {
        _state.update { current ->
            if (current == null || index !in current.draft.stations.indices) return@update current
            current.copy(draft = current.draft.copy(stations = current.draft.stations.filterIndexed { i, _ -> i != index }))
        }
    }

    fun moveStation(from: Int, to: Int) {
        _state.update { current ->
            if (current == null) return@update current
            current.copy(draft = current.draft.copy(stations = moveItem(current.draft.stations, from, to)))
        }
    }

    /**
     * 保存 — the single write, and the only one this page makes.
     *
     * Guarded twice: `canSave` re-asserts the predicate the header drew the button from (the flow
     * behind it may have moved since that frame), and `saving` makes a double tap one version rather
     * than two. A failure keeps the draft exactly as it is and reports through the page's `FaultStrip`;
     * `StoreFull` carries no retry, because retrying cannot help (`DECISIONS.md` §Q6).
     */
    fun save(repository: GymRepository) {
        val current = _state.value ?: return
        if (!canSave(current.draft, current.saving, current.loaded)) return
        _state.value = current.copy(saving = true, fault = null)
        viewModelScope.launch {
            when (val outcome = repository.saveRoutine(current.draft)) {
                // The id may not be the one that went in: editing a built-in is a copy-on-write.
                is GymWrite.Ok -> _state.update { it?.copy(saving = false, savedRoutineId = outcome.value) }
                is GymWrite.Failed -> _state.update { it?.copy(saving = false, fault = outcome.fault) }
            }
        }
    }

    fun dismissFault() {
        _state.update { it?.copy(fault = null) }
    }

    fun clear() {
        _state.value = null
    }
}

/**
 * Whether the draft is still someone's — true while a builder or a picker is anywhere on the stack.
 *
 * The picker is included because it *is* the builder's other half: pushing it disposes the builder's
 * composition, and a rule that looked only at the top route would drop the draft on the way to the very
 * page that edits it.
 */
internal fun builderDraftSurvives(stack: List<GymRoute>): Boolean =
    stack.any { it is GymRoute.Builder || it is GymRoute.StationPicker }

/**
 * A stored routine as the builder holds it.
 *
 * Everything the version froze, and nothing it did not: `progressionProgramId` is dropped because a
 * user routine cannot attach to a program and `structuralHash` already assumes null for every draft.
 */
internal fun draftOf(snapshot: RoutineSnapshot, routineId: String): RoutineDraft = RoutineDraft(
    routineId = routineId,
    name = snapshot.name,
    engine = snapshot.engine,
    stations = snapshot.stations.map {
        StationDraft(it.exerciseId, it.measure, it.prescribedReps, it.prescribedSeconds, it.note)
    },
    rounds = snapshot.rounds,
    timeCapSeconds = snapshot.timeCapSeconds,
    intervalSeconds = snapshot.intervalSeconds,
    restBetweenStations = snapshot.restBetweenStations,
    restBetweenRounds = snapshot.restBetweenRounds,
    prepareSeconds = snapshot.prepareSeconds,
)

/**
 * A new routine, seeded from the user's own defaults.
 *
 * `defaultStationRest` / `defaultRoundRest` / `prepareSeconds` are `GYM.SETTINGS`' rows and their
 * contract is exactly this: "a **default** for routines authored from now on, never an override"
 * (`GymPreferencesRepository.setDefaultStationRest`). Reading them here is what makes that sentence
 * true; hard-coding fifteen and sixty would leave the settings page promising something nothing does.
 *
 * 巡回 is the opening engine because §3's mock draws 方式 / 巡回 on a new routine, and one round is the
 * floor of the 巡数 wheel — the same floor `migrateDraft` reads a null as.
 */
internal fun newDraft(prefs: GymPreferences): RoutineDraft = RoutineDraft(
    routineId = null,
    name = "",
    engine = Engine.INTERVAL_CIRCUIT,
    stations = emptyList(),
    rounds = 1,
    timeCapSeconds = null,
    intervalSeconds = null,
    restBetweenStations = prefs.defaultStationRest,
    restBetweenRounds = prefs.defaultRoundRest,
    prepareSeconds = prefs.prepareSeconds,
)

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The words, and the arithmetic
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** 型を作る / 型を編集 — §6's two builder titles, chosen by whether there is a routine behind it. */
internal fun builderTitle(editingId: String?): String = if (editingId == null) "型を作る" else "型を編集"

/** 不明な種目 (§6 :1129) — a station whose exercise the catalogue no longer knows still lists. */
internal const val UNKNOWN_EXERCISE = "不明な種目"

/**
 * What a station row shows on its right: 二十回 / 三十秒 / 限界まで.
 *
 * Kanji counts, bare seconds — the same rule as the wheel that sets them (`DECISIONS.md` §Q10). A
 * `MAX_EFFORT` station has no value at all, so it says what it *is*: [Measure.MAX_EFFORT]'s own label,
 * which is §6's 限界まで. できるところまで is the picker's wheel replacement and belongs to that page —
 * it answers "how many", where this row answers "what kind".
 *
 * A prescription with no number is a row the store could not have written (the schema's CHECK refuses
 * it), so it renders as nothing rather than 〇回 — the empty string is the honest report of a value
 * that is missing, and 〇回 would be a prescription.
 */
internal fun stationValueLabel(station: StationDraft): String = when (station.measure) {
    Measure.REPS -> station.reps?.let { JapaneseDate.kanjiExtended(it) + "回" }.orEmpty()
    Measure.DURATION -> station.seconds?.let { JapaneseDate.kanjiExtended(it) + "秒" }.orEmpty()
    Measure.MAX_EFFORT -> Measure.MAX_EFFORT.label
}

/** 「一番目、腕立て伏せ、二十回」 — §3's accessibility line for a station row, verbatim. */
internal fun stationSemantics(position: Int, name: String, value: String): String =
    listOf(JapaneseDate.kanji(position + 1) + "番目", name, value)
        .filter { it.isNotBlank() }
        .joinToString("、")

/** 「腕立て伏せ の並べ替え」 — the handle's own description (§3, accessibility). */
internal fun handleSemantics(name: String): String = "$name の並べ替え"

/** 「三番目に移動しました」 — announced on drop, and on a TalkBack move action (§3, accessibility). */
internal fun moveAnnouncement(position: Int): String = JapaneseDate.kanji(position + 1) + "番目に移動しました"

/**
 * 「三番目に移動しました、腕立て伏せ と ディップス は続けて置かない方がよい」 — the move, and the clash it
 * created, in the one node that can actually speak.
 *
 * §3 asks that a clash "announces itself when the user reorders into one". A `liveRegion` node cannot
 * do that: [WarningLine] is composed only when there *is* a clash, so on every reorder the node is
 * **born** carrying its text — and [MoveAnnouncer]'s own note states the governing rule, that Compose's
 * accessibility delegate emits nothing for a semantics node that did not exist on the previous pass.
 * The move announcer is alive from the first frame and is already speaking about this exact gesture, so
 * the clash rides with it.
 *
 * Joined with 、 — [stationSemantics]' own separator, so nothing new is worded. Null warning means the
 * move alone, which is what a reorder into clean order should say.
 */
internal fun moveAnnouncementWith(position: Int, warning: String?): String =
    listOfNotNull(moveAnnouncement(position), warning).joinToString("、")

/**
 * 「これまでの六回の記録はそのまま残ります」 — §3 edge case 8, shown when the routine has history **and**
 * the structure is dirty.
 *
 * Both halves are conditions, and the second is why [structuralHash] is name-blind: correcting a typo
 * in the title writes a new version of nothing and must not raise a line about records surviving. Null
 * when the line is not owed, so the caller has nothing to decide.
 */
internal fun historySafeLine(sessions: Int, structureDirty: Boolean): String? =
    if (sessions > 0 && structureDirty) {
        "これまでの" + JapaneseDate.kanjiExtended(sessions) + "回の記録はそのまま残ります"
    } else {
        null
    }

/**
 * Whether another routine already carries this name — §3 edge case 4, which **warns and never blocks**.
 *
 * The routine being edited is excluded by id, so renaming 朝の五分 to 朝の五分 is not a collision with
 * itself. Blank never collides: an unnamed draft cannot save at all, and 保存 already says so.
 */
internal fun duplicateName(name: String, routineId: String?, library: List<RoutineSummary>): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return false
    return library.any { it.name == trimmed && it.routineId != routineId }
}

/** 「保存、名前と種目が要ります」 — §3's rule that a disabled word always says why. */
internal fun saveSemantics(canSave: Boolean): String = if (canSave) "保存" else "保存、名前と種目が要ります"

/**
 * What the 保存 action *announces*, which is not always what [saveSemantics] alone would say.
 *
 * `canSave` carries the `saving` flag, so it is false for the whole of §3's `Saving` state — and a
 * screen reader hearing 「保存、名前と種目が要ります」 while the write is in flight is told a reason that
 * is untrue: the name and the stations are both present, which is *why* the write is running. §3's rule
 * is that a disabled word says why, not that it says something. 保存中 is the label already on screen
 * and is the honest reason; no new copy.
 */
internal fun saveDescription(saving: Boolean, canSave: Boolean): String =
    if (saving) "保存中" else saveSemantics(canSave)

/**
 * Whether an edit's read failure owes the **full-page** `FaultPanel`.
 *
 * Only while the routine has never arrived. A routine that could not be read is not a routine with no
 * stations, so an unloaded edit must never render a builder — but once it *has* loaded, the user is
 * editing, and `routineDetail` re-emits on any table change: a later transient failure would replace a
 * draft full of unsaved work with a panel whose only action is もう一度, and whose only other exit is
 * Back into the discard prompt. §3's `Error` state puts the fault "`FaultStrip` **above** the fields,
 * never over them", which is what the loaded case does instead.
 */
internal fun builderFaultPanelOwed(readFailed: Boolean, routineLoaded: Boolean): Boolean =
    readFailed && !routineLoaded

/**
 * The index a drag from [from] would drop at, in whole row-heights.
 *
 * Rounds rather than truncates, so a row dragged more than half a row's height has moved — truncating
 * makes the list feel like it resists the first half of every gesture. Clamped to the list, because the
 * finger can leave it in either direction and [moveItem] answers an out-of-range drop by doing nothing
 * at all, which would read as the gesture being ignored.
 */
internal fun dropIndex(from: Int, offsetPx: Float, rowPx: Float, size: Int): Int {
    if (size <= 0 || rowPx <= 0f) return from
    return (from + (offsetPx / rowPx).roundToInt()).coerceIn(0, size - 1)
}

/**
 * How far to scroll the page while a drag is held near an edge: §3 edge case 1's "auto-scroll when the
 * finger is within 48.dp of either viewport edge, at 6.dp per frame".
 *
 * Pure, and in pixels, because it is the one piece of the drag that a test can check without a
 * viewport: the caller measures the two edges and multiplies nothing.
 */
internal fun autoScrollStep(
    rowCentreY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edgePx: Float,
    stepPx: Float,
): Float = when {
    rowCentreY < viewportTop + edgePx -> -stepPx
    rowCentreY > viewportBottom - edgePx -> stepPx
    else -> 0f
}

/**
 * Where 保存 lands, given what the builder was pushed on top of and the id that came back.
 *
 * §3's exits: `DETAIL(savedId)` when entered from a detail, `INDEX` when entered from the index. The
 * pop does most of it — the page underneath is the caller — so this answers only the one case the pop
 * gets wrong: **the id changed under it**. Editing a built-in copies (`02-data.md` §A.0.3) and
 * 写して作る pushes the builder over the *original's* detail, so popping alone would land the user on
 * the routine they did not just edit, with their work apparently missing.
 *
 * Null means the pop was enough. A detail already showing the saved id is left alone rather than
 * replaced with itself, which would restart its read for no reason.
 */
internal fun builderLanding(below: GymRoute?, savedRoutineId: String): GymRoute? =
    if (below is GymRoute.RoutineDetail && below.routineId != savedRoutineId) {
        GymRoute.RoutineDetail(savedRoutineId)
    } else {
        null
    }

/**
 * The clash lines, keyed by the row they belong under.
 *
 * `adjacentPatternClashes` returns the pair in **loop order**, and §3's layout insets the line under
 * the *second* of the pair — the station that starts tired. For the round-wrap pair that second index
 * is `0`, so the line about a routine that comes back around to its first movement sits under the first
 * row, which is exactly where the user will next look when they reorder.
 *
 * A map rather than a list because the station list draws by index and must not search a list per row.
 *
 * Both lookups are passed rather than reached for — the same bargain `adjacentPatternClashes` and
 * `estimateRoutine` strike, and for the same two reasons: this recomputes on every reorder, and a
 * function that reads `ExerciseCatalog` directly needs the catalogue *installed* to be testable, which
 * on the JVM it never is.
 */
internal fun stationWarnings(
    draft: RoutineDraft,
    pattern: (String) -> Pattern?,
    name: (String) -> String,
): Map<Int, String> = adjacentPatternClashes(draft, pattern)
    .associate { clash ->
        clash.secondIndex to clashCopy(
            name(draft.stations[clash.firstIndex].exerciseId),
            name(draft.stations[clash.secondIndex].exerciseId),
        )
    }

/**
 * The wheel's rows, with the value it was **opened on** guaranteed to be one of them.
 *
 * `DECISIONS.md` §Q21, and it is data loss until it is applied: `TempoValueWheel` resolves an absent
 * value with `values.indexOf(selected).coerceAtLeast(0)`, i.e. it silently seeds **row 0**. Three of the
 * four wheels cannot represent values shipped built-ins already hold — 巡数 is `1..20` and チェルシー is
 * 30 rounds, 巡の間の休息 steps by 15 and タバタ rests 10, 回数 is `1..100` and マーフ has 200- and
 * 300-rep stations — so opening 編集 or 写して作る on one of those and merely unfolding the row rewrote
 * the value, with no warning and no undo.
 *
 * **Merged, not widened.** §Q21 rules the ranges "a sensible authoring vocabulary" that the built-ins
 * are deliberately outside; widening every range to cover every seedable number would give every user a
 * three-hundred-row 回数 wheel to pay for three stations in the catalogue. The house precedent is
 * `TempoDateTimeWheel`, which merges `current.minute` into its minute column so "an existing event's odd
 * minute is kept in the list rather than silently rounded away".
 *
 * Sorted, because the wheel is a *scale* and a value appended after 二十巡 would read as 一巡 二巡 …
 * 二十巡 三十巡 only by luck. [labelFor] is the caller's, so each wheel keeps its own §Q10 form — なし at
 * zero for a rest, 秒 / 回 / 巡 otherwise — rather than this function guessing a unit from a number.
 */
internal fun mergedWheelOptions(
    options: List<WheelOption>,
    selected: Int,
    labelFor: (Int) -> String,
): List<WheelOption> =
    if (options.any { it.value == selected }) {
        options
    } else {
        (options + WheelOption(selected, labelFor(selected))).sortedBy { it.value }
    }

/**
 * The four wheel labels, in one block because `DECISIONS.md` §Q10 is one rule.
 *
 * A duration the user **chose** renders as the value they chose — bare seconds, `なし` at zero (§6
 * :1141's own word; 〇秒 would be a prescription of nothing) — and a count renders in kanji with its
 * counter. These are the forms `restOptions`, `secondOptions`, `repOptions` and `roundOptions` already
 * build their own rows with; they exist here only so [mergedWheelOptions] can label the one row those
 * lists do not contain, and they are duplicated rather than shared because `gym/BuilderDraft.kt` keeps
 * its label private and belongs to another unit. If they are ever unified, unify onto §Q10's sentence
 * and not onto `durationKanji`.
 */
internal fun restWheelValueLabel(seconds: Int): String =
    if (seconds <= 0) "なし" else JapaneseDate.kanjiExtended(seconds) + "秒"

/** 三十秒 — 秒数's wheel has no zero (a zero-second station is not a station), so no なし branch. */
internal fun secondWheelValueLabel(seconds: Int): String = JapaneseDate.kanjiExtended(seconds) + "秒"

/** 二百回 — 回数's counter. */
internal fun repWheelValueLabel(reps: Int): String = JapaneseDate.kanjiExtended(reps) + "回"

/** 三十巡 — 巡数's counter. */
internal fun roundWheelValueLabel(rounds: Int): String = JapaneseDate.kanjiExtended(rounds) + "巡"

/**
 * The engines the 方式 row offers, in §3's mock order — 巡回 段階 毎分 毎分増 完走 時間内 — **plus
 * 完走 ・ 休息あり**.
 *
 * The seventh is a disclosed divergence, not an oversight. §3's chip row draws six; §6 :1117 gives
 * `FOR_TIME_WITH_REST` a documented label, `migrateDraft` and `allowedMeasures` both handle it, and
 * Phase 2 seeds バーバラ with it. Omitting it would make a shipped built-in unbuildable in the builder,
 * which is the precise argument `allowedMeasures`' own KDoc makes for keeping 限界まで on 完走. It sits
 * next to 完走 so the two forms of the same idea read together.
 *
 * *Rejected* — `Engine.entries`. Its declaration order is the storage order and puts 時間内 second; the
 * mock's order is the reading order the page was drawn in.
 */
internal val ENGINE_CHOICES = listOf(
    Engine.INTERVAL_CIRCUIT,
    Engine.FIXED_SETS,
    Engine.EMOM,
    Engine.EMOM_ASCENDING,
    Engine.FOR_TIME,
    Engine.FOR_TIME_WITH_REST,
    Engine.AMRAP,
)

/** §3 edge case 1: the row height is fixed so the drag maths needs no measurement pass. */
private val STATION_ROW_HEIGHT = 56.dp

/** Which `PickerRow` is unfolded. Exactly one, ever — §3's `WheelOpen` state. */
private enum class BuilderRow { Engine, StationRest, RoundRest, Rounds }

/** §3's own row labels, and the labels `engineRows` already spells for the detail page. */
private const val ROW_STATION_REST = "種目の間の休息"
private const val ROW_ROUND_REST = "巡の間の休息"
private const val ROW_ROUNDS = "巡数"

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * 型を作る / 型を編集.
 *
 * **Three states that must not share a composable** (`00-plan.md` §4.1 rule 1). An edit whose routine
 * has not arrived yet renders 読み込み中; an edit whose read *failed* renders `FaultPanel` and never a
 * builder — an empty draft over a real routine is the one bug on this page that cannot be undone, and
 * `canSave`'s `routineLoaded` parameter exists for exactly it; a builder that is ready renders the
 * fields. 作る has no read at all and is ready from its first frame.
 *
 * @param editingId the routine being edited, or null for 作る. It is the **route's** argument and never
 *   the draft's identity: `saveRoutine` may return a different id and [builderLanding] adopts it.
 */
@Composable
fun BuilderScreen(gym: GymViewModel, editingId: String?, modifier: Modifier = Modifier) {
    val holder: BuilderDraftHolder = viewModel()
    val prefs by gym.prefs.collectAsStateWithLifecycle()

    // Seeded synchronously, before the state is read, so 作る never flashes a 読み込み中 it does not
    // owe. `open` is idempotent for the same target, which is what makes the picker round trip free.
    remember(editingId) { holder.open(editingId, prefs) }
    val state by holder.state.collectAsStateWithLifecycle()

    // Only 編集 reads. The condition is the route's argument and cannot change while this page is on
    // screen, so the conditional call is stable across every recomposition of it.
    val detail = editingId?.let { gym.routineDetail(it).collectAsStateWithLifecycle().value }
    val library by gym.routines.collectAsStateWithLifecycle()
    // Only an edit has a history to be safe about, and only an edit has an id to count against.
    val sessions = editingId?.let { gym.countForRoutine(it).collectAsStateWithLifecycle().value }

    LaunchedEffect(detail, editingId) {
        val ready = (detail as? Loadable.Ready)?.value ?: return@LaunchedEffect
        holder.adopt(editingId, ready.snapshot, ready.routineId)
    }

    // The draft belongs to the edit, not to this composition: pushing the picker disposes this page and
    // must not drop it. Everything else — 保存, やめる, a confirmed discard, a HOME press — leaves no
    // builder and no picker on the stack, and takes the draft with it.
    DisposableEffect(Unit) {
        onDispose { if (!builderDraftSurvives(gym.stack.value)) holder.clear() }
    }

    // 保存 landed. The pop is the ordinary exit; [builderLanding] fixes the one case it gets wrong.
    LaunchedEffect(state?.savedRoutineId) {
        val saved = state?.savedRoutineId ?: return@LaunchedEffect
        if (gym.stack.value.size > 1) gym.onBack()
        builderLanding(gym.stack.value.lastOrNull(), saved)?.let(gym::replaceTop)
    }

    var discarding by remember { mutableStateOf(false) }
    val ready = state
    val dirty = ready != null && isDirty(ready.draft, ready.original)

    // The prompt is owed only when there is something to lose. A clean builder registers a disabled
    // handler and the shell's own — registered above this page — pops (`GymShell`'s nesting note).
    BackHandler(enabled = dirty) { discarding = true }

    val leave = {
        if (dirty) discarding = true else if (gym.stack.value.size > 1) gym.onBack()
    }

    // `GymRoute.Builder` is immersive, so the shell hands this page the whole window and its own insets
    // (`GymShell`). A form needs its header clear of the status bar in a way the player's ✕ does not,
    // so the page takes `statusBarsPadding` itself rather than borrowing the player's 12.dp offset.
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        BuilderHeader(
            title = builderTitle(editingId),
            saving = ready?.saving == true,
            canSave = ready != null && canSave(ready.draft, ready.saving, ready.loaded),
            onCancel = leave,
            onSave = { holder.save(gym.repository) },
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                // A routine that could not be read is not a routine with no stations. Never a builder —
                // but only until it *has* been read: see [builderFaultPanelOwed]. A later failure of the
                // same flow reaches the strip below instead, above the fields the user is editing.
                builderFaultPanelOwed(detail is Loadable.Failed, ready?.loaded == true) ->
                    FaultPanel(fault = (detail as Loadable.Failed).fault, onRecover = gym::retry)

                ready == null || !ready.loaded -> BuilderLoadingState()
                else -> BuilderBody(
                    state = ready,
                    // A read that failed *after* the routine arrived: reported over the draft rather
                    // than instead of it (§3's `Error` state), with the same もう一度 the panel offers.
                    readFault = (detail as? Loadable.Failed)?.fault,
                    onRetryRead = gym::retry,
                    // An unread library is an empty one **for the collision warning only**, and that
                    // can only ever make the page say less: 同じ名前の型があります warns and never
                    // blocks (§3 edge case 4), so a missing warning costs a duplicate name the user is
                    // allowed to have anyway. Nothing else on this page reads the list, and nothing
                    // here branches on it — which is the difference from 削除's dialog, where an
                    // unknown count decides whether records are destroyed.
                    library = library.valueOrNull().orEmpty(),
                    // An unread count is read as zero **for the line only**, which can only ever make
                    // the page say less: [historySafeLine] is null at zero, and nothing here branches
                    // on it the way 削除's dialog does.
                    sessions = sessions?.valueOrNull() ?: 0,
                    autoFocusName = editingId == null,
                    holder = holder,
                    onPickStation = { index -> gym.go(GymRoute.StationPicker(index)) },
                )
            }
        }
    }

    if (discarding) {
        DiscardDialog(
            onConfirm = {
                discarding = false
                if (gym.stack.value.size > 1) gym.onBack()
            },
            onDismiss = { discarding = false },
        )
    }
}

/**
 * The header: the page's title, やめる, and the one button that writes.
 *
 * 保存中 while the write is in flight, with both actions inert — §3's `Saving` state, which leaves the
 * body untouched so the user can still see what is being saved.
 */
@Composable
private fun BuilderHeader(
    title: String,
    saving: Boolean,
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(
                label = "やめる",
                description = "やめる",
                color = c.inkFaint,
                enabled = !saving,
                onClick = onCancel,
            )
            HeaderAction(
                label = if (saving) "保存中" else "保存",
                // Not `saveSemantics(canSave)` alone: `canSave` is false for the whole `Saving` state,
                // and 「名前と種目が要ります」 while the write is in flight is a reason that is untrue.
                description = saveDescription(saving, canSave),
                color = if (canSave) c.accent else c.inkFaint,
                enabled = canSave,
                onClick = onSave,
            )
        }
    }
}

/**
 * Everything below the header: name, engine, stations, the engine's own rows, and the live estimate.
 *
 * `verticalScroll` + `imePadding()` is `EventComposeScreen`'s body verbatim, and the ime padding is
 * mandatory (§3 edge case 10): the name field is at the top, the wheels are at the bottom, and the
 * keyboard must not shove the estimate line off the screen.
 *
 * The station list is a plain `Column`, never a `LazyColumn` — §3 edge case 1. The list is capped at 24
 * (`STATION_CAP`), so there is no viewport arithmetic to get wrong, and the hand-rolled drag can
 * translate rows by whole row-heights because every row is exactly [STATION_ROW_HEIGHT].
 */
@Composable
private fun BuilderBody(
    state: BuilderDraftState,
    readFault: TempoFault?,
    onRetryRead: () -> Unit,
    library: List<RoutineSummary>,
    sessions: Int,
    autoFocusName: Boolean,
    holder: BuilderDraftHolder,
    onPickStation: (Int?) -> Unit,
) {
    val c = LocalTempoColors.current
    val draft = state.draft
    val scroll = rememberScrollState()

    var expanded by remember { mutableStateOf<BuilderRow?>(null) }
    fun toggle(row: BuilderRow) {
        expanded = if (expanded == row) null else row
    }

    val name: (String) -> String = { id -> ExerciseCatalog.byId(id)?.nameJa ?: UNKNOWN_EXERCISE }
    // Named argument, not a trailing lambda: the last parameter of `estimateRoutine` is
    // `maxEffortSeconds`, so a trailing lambda would silently pass the catalogue as the median-history
    // lookup and leave every station unresolved.
    val estimate = remember(draft) { estimateRoutine(draft, exercise = { ExerciseCatalog.byId(it) }) }
    val estimateLine = remember(draft.engine, estimate) { estimateLabel(draft.engine, estimate) }
    val pattern: (String) -> Pattern? = { id -> ExerciseCatalog.byId(id)?.pattern }
    val warnings = remember(draft) { stationWarnings(draft, pattern = pattern, name = name) }
    // The clash map the routine *would* have after a move, asked before the move is announced. The
    // announcer speaks once per gesture and it has to speak about the order the user just made, not the
    // one they left — see [moveAnnouncementWith].
    val warningsAfterMove: (Int, Int) -> Map<Int, String> = { from, to ->
        stationWarnings(
            draft.copy(stations = moveItem(draft.stations, from, to)),
            pattern = pattern,
            name = name,
        )
    }
    val rows = remember(draft) {
        engineRows(
            engine = draft.engine,
            rounds = draft.rounds,
            timeCapSeconds = draft.timeCapSeconds,
            restBetweenStations = draft.restBetweenStations,
            restBetweenRounds = draft.restBetweenRounds,
        )
    }
    val collides = remember(draft.name, draft.routineId, library) {
        duplicateName(draft.name, draft.routineId, library)
    }
    val historyLine = remember(sessions, draft, state.original) {
        historySafeLine(sessions, state.original != null && structuralHash(draft) != structuralHash(state.original))
    }

    var viewportTop by remember { mutableStateOf(0f) }
    var viewportBottom by remember { mutableStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                viewportTop = bounds.top
                viewportBottom = bounds.bottom
            }
            .verticalScroll(scroll)
            .padding(horizontal = 26.dp)
            .imePadding(),
    ) {
        // Above the fields, never over them (§3's `Error` state): the draft stays readable behind
        // whatever failed. Three faults carry no action word at all, so the strip is then a sentence.
        state.fault?.let { fault ->
            FaultStrip(
                fault = fault,
                onRecover = holder::dismissFault,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        // The *read* failing after the routine arrived. Same strip, same place, and もう一度 re-runs the
        // flow — never the panel, which would take the draft off screen (see [builderFaultPanelOwed]).
        readFault?.let { fault ->
            FaultStrip(fault = fault, onRecover = onRetryRead, modifier = Modifier.padding(bottom = 16.dp))
        }

        // §3 edge case 8 puts this line **under 保存**, and says why outright: "The user should know
        // editing is safe *before* they press it." 保存 is pinned in the header, so the only place that
        // is true for a routine with a few stations is the top of the body — at the bottom the line sits
        // below the fold and the user reaches the button having never seen it.
        historyLine?.let { NoticeLine(it) }

        NameField(value = draft.name, autoFocus = autoFocusName) { typed ->
            holder.edit { it.copy(name = typed) }
        }
        if (collides) {
            // Warns, never blocks: two routines called 朝の五分 are the user's business (§3 edge 4).
            NoticeLine("同じ名前の型があります")
        }

        EnginePickerRow(
            engine = draft.engine,
            expanded = expanded == BuilderRow.Engine,
            onToggle = { toggle(BuilderRow.Engine) },
            onSelect = holder::selectEngine,
        )
        state.notices.forEach { NoticeLine(it) }

        FieldLabel("種目")
        StationList(
            draft = draft,
            warnings = warnings,
            warningsAfterMove = warningsAfterMove,
            name = name,
            scroll = scroll,
            viewportTop = viewportTop,
            viewportBottom = viewportBottom,
            onEdit = onPickStation,
            onRemove = holder::removeStation,
            onMove = holder::moveStation,
        )
        AddStationRow(enabled = canAddStation(draft)) { onPickStation(null) }

        rows.forEach { row ->
            when (row.label) {
                ROW_STATION_REST -> WheelRow(
                    label = row.label,
                    value = row.value,
                    options = restOptions(RestSlot.BETWEEN_STATIONS),
                    selected = draft.restBetweenStations,
                    labelFor = ::restWheelValueLabel,
                    expanded = expanded == BuilderRow.StationRest,
                    onToggle = { toggle(BuilderRow.StationRest) },
                    onSelect = { seconds -> holder.edit { it.copy(restBetweenStations = seconds) } },
                )

                // The round rest steps by 15 and タバタ rests 10, so this wheel in particular is opened
                // on a value it does not contain — [mergedWheelOptions] is what keeps it (`§Q21`).
                ROW_ROUND_REST -> WheelRow(
                    label = row.label,
                    value = row.value,
                    options = restOptions(RestSlot.BETWEEN_ROUNDS),
                    selected = draft.restBetweenRounds,
                    labelFor = ::restWheelValueLabel,
                    expanded = expanded == BuilderRow.RoundRest,
                    onToggle = { toggle(BuilderRow.RoundRest) },
                    onSelect = { seconds -> holder.edit { it.copy(restBetweenRounds = seconds) } },
                )

                // 巡数 is a wheel only where it is a number. 時間内 answers it with 時間内で — "as many
                // as fit" is not a value on a wheel — and reads out as the row `engineRows` wrote.
                ROW_ROUNDS -> if (draft.engine == Engine.AMRAP) {
                    ReadOnlyRow(row.label, row.value)
                } else {
                    WheelRow(
                        label = row.label,
                        value = row.value,
                        options = roundOptions(),
                        selected = draft.rounds ?: 1,
                        labelFor = ::roundWheelValueLabel,
                        expanded = expanded == BuilderRow.Rounds,
                        onToggle = { toggle(BuilderRow.Rounds) },
                        onSelect = { rounds -> holder.edit { it.copy(rounds = rounds) } },
                    )
                }

                // 制限時間, and nothing else reaches here. **There is no wheel for it**: §3 edge case 11
                // lists the ranges for the station rest, the round rest, 巡数, 回数 and 秒数 and gives
                // none for a time cap, and inventing one would be inventing numbers. Every AMRAP
                // authored here therefore carries `migrateDraft`'s documented 二十分 default and the row
                // states it. Reported — it needs a range in §3 before it can be dialled, exactly as
                // 毎分's window needed a name it never got (`DECISIONS.md` §Q16).
                else -> ReadOnlyRow(row.label, row.value)
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
        // Live, and never animated: a number that slides while you dial the wheel above it is harder to
        // read than one that simply changes. Empty for a routine that has nothing to estimate — デス・
        // バイ runs until you fail — in which case the line is omitted rather than drawn as 約 〇分.
        if (estimateLine.isNotBlank()) {
            Text(
                text = estimateLine,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.inkSoft),
            )
        } else {
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 名前 — `TitleField`'s type and rule, with the label the builder's mock puts in front of it. */
@Composable
private fun NameField(value: String, autoFocus: Boolean, onChange: (String) -> Unit) {
    val c = LocalTempoColors.current
    val focusRequester = remember { FocusRequester() }
    // Straight into typing on a new routine; on an edit the user came to change something specific,
    // and a keyboard in their face is presumptuous (§3's two `Ready` states, and the composer's
    // stated reason, which this borrows verbatim).
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "名前",
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mincho, fontSize = 20.sp, color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "名前" },
            decorationBox = { inner ->
                Column {
                    // No placeholder word. §6 carries none for this field, and the composer's 題名 is a
                    // different field on a different page; the label to the left already names it.
                    Box(Modifier.padding(vertical = 14.dp)) { inner() }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
                }
            },
        )
    }
}

/**
 * 方式 — the current engine, unfolding into the chip row in place.
 *
 * `animateContentSize(220ms, LinearOutSlowIn)` is §3's figure and the `PickerRow` idiom: the row grows
 * the page rather than covering it, so the stations underneath keep their position and the user can see
 * what changing the engine did to them.
 */
@Composable
private fun EnginePickerRow(
    engine: Engine,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (Engine) -> Unit,
) {
    val c = LocalTempoColors.current
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220, easing = LinearOutSlowInEasing))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable(onClick = onToggle)
                .semantics { role = Role.Button; contentDescription = "方式、${engine.label}" }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "方式",
                style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )
            Text(
                text = engine.label,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.ink),
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ENGINE_CHOICES.forEach { choice ->
                    val selected = choice == engine
                    Box(
                        modifier = Modifier
                            .sizeIn(minHeight = 48.dp)
                            .clickable { onSelect(choice) }
                            .semantics {
                                role = Role.RadioButton
                                contentDescription = choice.label
                                if (selected) stateDescription = "選択中"
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = choice.label,
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
    }
}

/**
 * The stations, in order, with the drag that reorders them.
 *
 * **The drag is the whole of §3 edge case 1 and there is no library behind it.** The gesture is
 * `detectDragGesturesAfterLongPress` **on the handle only** — a whole-row drag would fight the page's
 * own vertical scroll and would steal the tap that opens the picker. While a drag is live the lifted
 * row follows the finger through `translationY` and every other row is offset by `reorderShift`, which
 * is pure, exhaustively tested against `moveItem` over every (from, to) pair, and deliberately
 * size-blind. On drop the list is committed with `moveItem`.
 *
 * **Warning lines are hidden while dragging, and that is load-bearing rather than tidy.** They are
 * inset between rows, so with one on screen the rows are no longer a uniform [STATION_ROW_HEIGHT] apart
 * and every translation would be wrong by the height of a sentence. The alternative is measuring the
 * list, which is exactly what a fixed row height exists to avoid. A warning about an order that is
 * currently in the user's hand is stale anyway; they return, recomputed, the moment the row lands.
 *
 * **Rotation cancels the drag** (§3 edge case 9) without any code here: the gesture's own
 * `onDragCancel` restores the pre-drag order because nothing has been committed yet — the draft is only
 * written on drop. A half-completed reorder is worse than none.
 */
@Composable
private fun StationList(
    draft: RoutineDraft,
    warnings: Map<Int, String>,
    warningsAfterMove: (Int, Int) -> Map<Int, String>,
    name: (String) -> String,
    scroll: ScrollState,
    viewportTop: Float,
    viewportBottom: Float,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val c = LocalTempoColors.current
    val stations = draft.stations
    if (stations.isEmpty()) {
        // §3's `EmptyStations`: the list box is **absent**, not an empty card, and ＋ 加える sits
        // directly under the heading with the one sentence that says what to do.
        NoticeLine("種目を加えてください")
        return
    }

    val density = LocalDensity.current
    val rowPx = with(density) { STATION_ROW_HEIGHT.toPx() }
    val edgePx = with(density) { 48.dp.toPx() }
    val stepPx = with(density) { 6.dp.toPx() }

    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var listTop by remember { mutableStateOf(0f) }
    var announcement by remember { mutableStateOf("") }

    val from = dragFrom
    val to = if (from == null) null else dropIndex(from, dragOffset, rowPx, stations.size)

    // Auto-scroll: while the lifted row is within 48.dp of a viewport edge the page scrolls under it at
    // 6.dp a frame, and the same amount is added to the drag offset so the row stays under the finger.
    LaunchedEffect(from) {
        if (from == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val centre = listTop + from * rowPx + dragOffset + rowPx / 2f
            val step = autoScrollStep(centre, viewportTop, viewportBottom, edgePx, stepPx)
            if (step != 0f) dragOffset += scroll.scrollBy(step)
        }
    }

    MoveAnnouncer(announcement)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .onGloballyPositioned { listTop = it.boundsInWindow().top }
            .animateContentSize(tween(160, easing = LinearOutSlowInEasing)),
    ) {
        stations.forEachIndexed { index, station ->
            val dragging = index == from
            StationRow(
                position = index,
                label = name(station.exerciseId),
                value = stationValueLabel(station),
                // 段階では一種目だけ使われます — the extras are kept and greyed, and the notice above is
                // the only thing that says why (`migrateDraft`, §3 edge case 5).
                dimmed = draft.engine == Engine.FIXED_SETS && index > 0,
                dragging = dragging,
                canMoveUp = index > 0,
                canMoveDown = index < stations.size - 1,
                offsetPx = when {
                    from == null || to == null -> 0f
                    dragging -> dragOffset
                    else -> reorderShift(index, from, to) * rowPx
                },
                onEdit = { onEdit(index) },
                onRemove = { onRemove(index) },
                // The clash rides on the move's own announcement rather than on the warning line, which
                // is born speaking and therefore silent — [moveAnnouncementWith] carries the argument.
                onMoveUp = {
                    val target = index - 1
                    val clash = warningsAfterMove(index, target)[target]
                    onMove(index, target)
                    announcement = moveAnnouncementWith(target, clash)
                },
                onMoveDown = {
                    val target = index + 1
                    val clash = warningsAfterMove(index, target)[target]
                    onMove(index, target)
                    announcement = moveAnnouncementWith(target, clash)
                },
                onDragStart = {
                    dragFrom = index
                    dragOffset = 0f
                },
                onDrag = { delta -> dragOffset += delta },
                // Read from the drag's own state rather than from the `from`/`to` this composition
                // computed: a drop is answered by where the finger actually is, never by where the
                // last recomposition thought it was.
                onDrop = {
                    val start = dragFrom
                    if (start != null) {
                        val target = dropIndex(start, dragOffset, rowPx, stations.size)
                        if (target != start) {
                            val clash = warningsAfterMove(start, target)[target]
                            onMove(start, target)
                            announcement = moveAnnouncementWith(target, clash)
                        }
                    }
                    dragFrom = null
                    dragOffset = 0f
                },
                onDragCancel = {
                    dragFrom = null
                    dragOffset = 0f
                },
            )
            // Under the **second** of the pair, which for the round-wrap pair is the first row — the
            // station that starts tired is the one the line is about (`PatternWarning.PatternClash`).
            if (from == null) warnings[index]?.let { WarningLine(it) }
        }
    }
}

/**
 * One station: its name, its prescription, and a handle.
 *
 * **Fixed at [STATION_ROW_HEIGHT], and nothing in it may wrap.** The drag translates rows by whole row
 * heights, so a row that grew to fit a long exercise name would put every other row's offset out by the
 * difference — which is why the name is one line and ellipsised rather than allowed two.
 *
 * **The four custom actions are the most important accessibility item on this page** (§3, and
 * `00-plan.md` §4.1 rule 4). A drag is invisible to TalkBack, so 上へ動かす / 下へ動かす / 編集 / 削除
 * are declared on the row itself; a screen-reader user must never need the gesture. The two move
 * actions are omitted at the ends rather than offered and refused — an action that can only fail is
 * chrome, and the announcement it would make is the one thing a user cannot check by looking.
 */
@Composable
private fun StationRow(
    position: Int,
    label: String,
    value: String,
    dimmed: Boolean,
    dragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    offsetPx: Float,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDrop: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val c = LocalTempoColors.current
    val description = stationSemantics(position, label, value)
    // **The gesture block outlives the lambdas it was given.** `pointerInput` restarts only when its
    // key changes, so the `detectDragGesturesAfterLongPress` running during a drag is the one composed
    // *before* the drag started — and the callbacks it captured are that composition's. `onDrop` reads
    // the live drag state, so a captured copy commits the move the list had before the finger moved:
    // the reorder silently does nothing. `rememberUpdatedState` is the fix, and it is why the four
    // callbacks are read through it rather than called directly.
    val startDrag by rememberUpdatedState(onDragStart)
    val dragBy by rememberUpdatedState(onDrag)
    val dropAt by rememberUpdatedState(onDrop)
    val cancelDrag by rememberUpdatedState(onDragCancel)
    val actions = buildList {
        if (canMoveUp) add(CustomAccessibilityAction("上へ動かす") { onMoveUp(); true })
        if (canMoveDown) add(CustomAccessibilityAction("下へ動かす") { onMoveDown(); true })
        add(CustomAccessibilityAction("編集") { onEdit(); true })
        add(CustomAccessibilityAction("削除") { onRemove(); true })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(STATION_ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = offsetPx }
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick { onEdit(); true }
                    customActions = actions
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = true).padding(end = 12.dp),
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 16.sp,
                    color = if (dimmed) c.inkFaint else c.ink,
                ),
            )
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 16.sp,
                    color = if (dimmed) c.inkFaint else c.inkSoft,
                ),
            )
        }

        // 24.dp of glyph in a 48.dp target, and the gesture lives here and nowhere else.
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(position) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { startDrag() },
                        onDrag = { change, amount ->
                            change.consume()
                            dragBy(amount.y)
                        },
                        onDragEnd = { dropAt() },
                        onDragCancel = { cancelDrag() },
                    )
                }
                .semantics {
                    contentDescription = handleSemantics(label)
                    if (dragging) stateDescription = "移動中"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⋮⋮",
                style = TextStyle(
                    fontFamily = Gothic,
                    fontSize = 16.sp,
                    color = if (dragging) c.inkSoft else c.inkFaint,
                ),
            )
        }
    }
}

/**
 * The node that says 「三番目に移動しました」 out loud.
 *
 * Present from the first frame and 1.dp tall, which is `RecordSummary.AccoladeAnnouncer`'s hard-won
 * shape and its reasoning applies unchanged: Compose's accessibility delegate emits nothing for a
 * semantics node that did not exist on the previous pass, so a live region that is *born* speaking is
 * silent for ever, and a genuinely zero-sized node is pruned before it can be diffed. What changes here
 * is the description, which is the one branch that ships the new text inside the event.
 */
@Composable
private fun MoveAnnouncer(announcement: String) {
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

/** ＋ 加える, and the one sentence that explains the day it stops working (§3 edge case 2, at 24). */
@Composable
private fun AddStationRow(enabled: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { role = Role.Button; contentDescription = "＋ 加える" }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = "＋ 加える",
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    color = if (enabled) c.accent else c.inkFaint,
                ),
            )
        }
        if (!enabled) NoticeLine("これ以上は加えられません")
    }
}

/**
 * A `PickerRow` over a `TempoValueWheel`: the label, the value it currently holds, and the wheel it
 * unfolds into.
 *
 * The value is `engineRows`' own string, so the builder and `GYM.LIBRARY.DETAIL` cannot disagree about
 * what the same routine says — 六十秒 on both, なし at zero on both. That is `DECISIONS.md` §Q10's whole
 * point, and the reason the wheel's labels come from `restOptions`/`roundOptions` rather than from
 * `durationKanji`.
 *
 * **The wheel is one accessibility node.** §3's accessibility line requires the wrapping box to carry
 * the description and the state and the per-item texts to be cleared, so TalkBack does not read all
 * twenty-five rows. `clearAndSetSemantics` is what does that, and it is honest about the cost: the
 * column is not adjustable under a screen reader. The trade is the spec's own and is noted rather than
 * quietly reversed — a fix needs a documented adjust action, which §3 does not carry.
 *
 * **The list is [mergedWheelOptions]', never [options] raw** (`DECISIONS.md` §Q21). 巡数 is `1..20` and
 * チェルシー is 30 rounds; the round rest steps by 15 and タバタ rests 10. Without the merge the row above
 * read 三十巡 while the 栞 sat on 一巡 — a contradiction on the first frame, and one notch of scroll
 * wrote row 0 over the value with no undo.
 */
@Composable
private fun WheelRow(
    label: String,
    value: String,
    options: List<WheelOption>,
    selected: Int,
    labelFor: (Int) -> String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val c = LocalTempoColors.current
    val merged = remember(options, selected) { mergedWheelOptions(options, selected, labelFor) }
    val values = remember(merged) { merged.map { it.value } }
    val labels = remember(merged) { merged.associate { it.value to it.label } }

    Column(Modifier.fillMaxWidth().animateContentSize(tween(220, easing = LinearOutSlowInEasing))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable(onClick = onToggle)
                .semantics { role = Role.Button; contentDescription = "$label、$value" }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )
            Text(text = value, style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.ink))
        }
        if (expanded) {
            Box(
                Modifier.clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = value
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
    }
}

/**
 * A row the builder shows and cannot set: 制限時間, and 巡数's 時間内で.
 *
 * Not greyed and not hidden. It is a true statement about the routine — the AMRAP *does* run for twenty
 * minutes — and hiding it would leave the estimate line below claiming a duration with nothing on the
 * page to account for it.
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
        Text(text = value, style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = c.inkSoft))
    }
}

/** 種目 — §2's builder field label. */
@Composable
private fun FieldLabel(label: String) {
    val c = LocalTempoColors.current
    Text(
        text = label,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
    )
}

/**
 * A line the builder says about the draft: a migration notice, a name collision, the station cap, or
 * 「これまでの六回の記録はそのまま残ります」.
 *
 * §2's warning-line style, and **no live region**. These do not appear under the user's finger the way
 * a clash does — each one is the direct result of the tap that is still under it — and a page that
 * announces every consequence of every tap is a page a screen-reader user cannot get through.
 */
@Composable
private fun NoticeLine(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
    )
}

/**
 * 「腕立て伏せ と ディップス は続けて置かない方がよい」, inset under the second of the pair — **the visual
 * line only**.
 *
 * §3 asks that a clash announce itself when the user reorders into one, and this node cannot do it: it
 * is composed only when the map has an entry, so on every reorder it is *born* carrying its text, and
 * [MoveAnnouncer]'s KDoc states the rule verbatim — Compose emits nothing for a semantics node that did
 * not exist on the previous pass, so a live region born speaking is silent for ever. Carrying
 * `liveRegion = Polite` here would be a claim the node cannot keep, which is worse than not carrying it:
 * the next agent reads it as delivered and stops looking.
 *
 * The announcement is delivered instead through [moveAnnouncementWith], on the always-alive
 * [MoveAnnouncer] — the one node that is already speaking about the gesture that created the clash.
 */
@Composable
private fun WarningLine(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
        style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
    )
}

/**
 * 読み込み中 — the routine behind an edit has not arrived.
 *
 * Its own composable, sharing nothing with the fault panel or with any empty state. Loading is not
 * empty is not failed, and the three looking alike is precisely how they come to share a branch.
 */
@Composable
private fun BuilderLoadingState() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "読み込み中",
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * 編集をやめますか — raised only when [isDirty] says there is something to lose.
 *
 * **もどる is the escape and it is the dismiss button**, deliberately (§6 :1142). The confirm word is
 * やめる, which is the same word as the header action that can raise this dialog, so the destructive
 * choice reads as finishing the act the user started rather than as a new one.
 */
@Composable
private fun DiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalTempoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(text = "編集をやめますか", style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink))
        },
        text = {
            Text(
                text = "保存していない変更は消えます。",
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
            TextButton(onClick = onConfirm) {
                Text("やめる", style = TextStyle(fontFamily = Mincho, color = c.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("もどる", style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}
