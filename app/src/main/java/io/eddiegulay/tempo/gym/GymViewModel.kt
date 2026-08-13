package io.eddiegulay.tempo.gym

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.session.ScalingTier
import io.eddiegulay.tempo.gym.session.compile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A workout that is happening.
 *
 * Held in memory by [GymViewModel] and **not** derived from the back stack, which is the single most
 * important property in this file — see [GymViewModel.activeSession].
 *
 * Deliberately thin. This is the shell's view of a live session: enough to know one exists, to keep
 * the screen awake for it, and to route 続ける back into it. The clock, the compiled timeline, the
 * closed segments and the open one are the player's (`03-player.md` §B/§C), and it is the player that
 * owns adding them here — a shell that understood a timeline would be a shell with an opinion about
 * how a workout runs.
 *
 * @param sessionId the row `startSession` returned. Present because the session exists in SQLite
 *   before it exists on screen: process death between the two is recoverable, the other order is not.
 * @param routineName denormalised from the session row rather than joined, so a つづき banner still
 *   has a name when the routine behind it has been deleted (`00-plan.md` §2 row 10).
 */
data class ActiveSession(
    val sessionId: Long,
    val routineId: String,
    val routineName: String,
)

/**
 * Why the shell is being left. Both reasons reset exactly the same state today; the parameter exists
 * so the two call sites read as two events rather than one, and so that a future divergence has
 * somewhere to live other than a boolean bolted on afterwards.
 */
enum class LeaveReason { Back, HomePress }

/**
 * A 始める that is waiting for the resume prompt to be answered.
 *
 * `03-player.md` §A's start guard is *not* a refusal: an open session raises the prompt **instead of**
 * starting, and "once the user picks 記録する or 捨てる, the start proceeds automatically." That promise
 * needs the tap's three arguments to survive the modal, which is what this carries.
 *
 * Dropped, deliberately, when the user picks 続ける or dismisses — both of those are the user choosing
 * *not* to start the new routine, and resuming into one workout while silently queuing another is the
 * one outcome nobody asked for.
 */
data class PendingStart(
    val routineId: String,
    val tier: ScalingTier,
    val roundsPlanned: Int?,
)

/**
 * つづき, as modal state rather than as a place.
 *
 * `00-plan.md` §2 row 8 is the whole design: it is a question, not a page, so it has no [GymRoute] and
 * **dismissing it decides nothing** — [GymViewModel.dismissResumePrompt] leaves the session row, the
 * つづき banner and [GymViewModel.resumable] exactly as they were. A route would have made the
 * dismissal a navigation, and a navigation looks like an answer.
 *
 * @param session the open row, read once. [ResumableSession.resumability] on it was computed by the
 *   store from `03-player.md` §E.3's rule and is what [options] is derived from.
 * @param options which of the three doors exist at all — see [resumeOptions].
 * @param pendingStart non-null when the prompt was raised as `GYM.LIBRARY.DETAIL`'s start guard
 *   rather than over `GYM.HOME`.
 * @param writing a commit or a discard is in flight. `01-shell.md` §B's `Writing` state: the buttons
 *   disable, **the dialog does not dismiss**, and a failure lands in [fault] with the row untouched.
 * @param confirmingDiscard 捨てる asks twice (`01-shell.md` §B's actions table), because it is the one
 *   button here that destroys a workout.
 * @param fault the last failed write, shown inline. Never a reason to close: §B edge case 8 — a
 *   resumable workout vanishing because a write failed is the worst possible failure on this page.
 */
data class ResumePromptState(
    val session: ResumableSession,
    val options: ResumeOptions,
    val pendingStart: PendingStart? = null,
    val writing: Boolean = false,
    val confirmingDiscard: Boolean = false,
    val fault: GymFault? = null,
)

/**
 * `recentUsage(60)` — the window よく使う ranks over, from `04-library-records.md` §3 edge case 5.
 *
 * Named rather than inlined because the same sixty days decides both the library index's よく使う
 * section and, through the store, `GYM.HOME`'s 「よく使う」 feed; two literals would drift apart the
 * first time one of them was tuned.
 */
private const val RECENT_USAGE_DAYS = 60

/**
 * 鍛錬's state, separate from `LauncherViewModel` and constructed only when someone actually trains.
 *
 * `LauncherViewModel` is 495 lines and already holds theme, onboarding, screen, search, the app
 * inventory, the blockade, notifications with their undo window, and the calendar pipeline. Three
 * reasons harder than line count kept the gym out of it:
 *
 * 1. **Cold-start cost.** This is a HOME app. `MainActivity.onCreate` runs on every HOME press from a
 *    cold process and the first frame *is* the user's home screen. Opening `SQLiteOpenHelper` — which
 *    runs `onCreate`/`onUpgrade` on the first `getReadableDatabase` — belongs behind a lazy door that
 *    a user who never touches 鍛錬 never opens.
 * 2. **Failure isolation.** A corrupt gym database must degrade to a fault panel inside the shell. As
 *    a constructor parameter of `LauncherViewModel` a throw in its `init` takes Home, Search and
 *    Notifications down with it.
 * 3. **Lifetime honesty.** Launcher state is "what is on screen". Gym state includes a live workout
 *    that must outlive both.
 *
 * The repositories are exposed rather than proxied. Thirty delegating methods would add a second
 * place for every signature to drift and would put this ViewModel in the way of pages that legitimately
 * hold their own state; `01-shell.md`'s pages are specified against the repository's own signatures.
 */
class GymViewModel(
    val repository: GymRepository,
    val preferences: GymPreferencesRepository,
) : ViewModel() {

    private val _stack = MutableStateFlow<List<GymRoute>>(listOf(GymRoute.Home))
    val stack: StateFlow<List<GymRoute>> = _stack.asStateFlow()

    /**
     * The live workout. Deliberately **not** derived from [stack]: a session's existence is a fact
     * about the user's body, not about what is on screen. Leaving the player, leaving the shell and a
     * HOME press must all leave this untouched — see [onLeaveShell].
     *
     * Deriving it from the stack is the tempting version and it loses a workout to a fat thumb: HOME
     * pressed mid-circuit would end the session, and the user would come back to nothing having
     * pressed nothing that means "stop".
     */
    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    /**
     * The stored preferences, seeded synchronously so the first frame is already right.
     *
     * Named as the shell reads it (`prefs.keepScreenOn`). This is the *stored* truth; the cue engine
     * wants [EffectiveGymPreferences] instead, which is a different question with a different answer
     * under TalkBack (`DECISIONS.md` §Q2).
     */
    private val _prefs = MutableStateFlow(preferences.loadInitial())
    val prefs: StateFlow<GymPreferences> = _prefs.asStateFlow()

    init {
        viewModelScope.launch { preferences.preferences.collect { _prefs.value = it } }
        // Cold start over GYM.HOME (`03-player.md` §A). Constructing this ViewModel *is* entering the
        // gym, and the repository singleton behind it has already opened the database, so this costs
        // the lazy-open guarantee nothing — while a `resumable` left at Loading forever would make
        // every reader of it wrong for a user who walks straight into 型.
        refreshResumable()
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // GYM.HOME — the feed and つづき
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Everything `GYM.HOME` renders, in one read (`01-shell.md` §B).
     *
     * `WhileSubscribed(5_000)` is the whole reason this is not collected in the composable: a rotation
     * and a tab bounce both fall inside the grace window, so neither re-queries, and the seeded value
     * is [Loadable.Loading] rather than an empty feed — **an unread store must never render
     * 記録はありません** (`00-plan.md` §4.1 rule 1). It stays a `Loadable` all the way to the page for
     * the same reason; unwrapping it to a nullable here is precisely how loading becomes
     * indistinguishable from empty.
     *
     * Read at the **first-run** preview width. The first-run shape shows six built-ins and the
     * ordinary one four (`01-shell.md` §B's `FirstRun` state), and re-subscribing the whole feed when
     * `everTrained` flips would re-run the join to *narrow* a list already in memory. [homeBuiltIn]
     * takes the four; `builtInTotal` is unaffected either way, so すべて見る stays honest.
     */
    val homeFeed: StateFlow<Loadable<GymHomeFeed>> =
        repository.homeFeed(HOME_RECENT_LIMIT, HOME_BUILTIN_PREVIEW_FIRST_RUN)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /**
     * The one session that may be open, as the store last reported it.
     *
     * A `MutableStateFlow` fed by a one-shot `suspend` read rather than a flow, because that is what
     * `02-data.md` §C.2 provides and what it means: `idx_session_live` guarantees at most one
     * candidate, and the row is read once at cold start and once on entering `GYM.HOME`
     * ([onHomeShown]). [Loadable.Loading] here is load-bearing — the prompt does not appear until the
     * query resolves, which is what stops it flashing over a page that is still drawing.
     */
    private val _resumable = MutableStateFlow<Loadable<ResumableSession?>>(Loadable.Loading)
    val resumable: StateFlow<Loadable<ResumableSession?>> = _resumable.asStateFlow()

    /**
     * Which つづき treatment `GYM.HOME` owes, decided by [resumeAffordance] rather than by the page.
     *
     * A `Loading` or `Failed` read maps to [ResumeAffordance.None]: there is no banner to draw yet,
     * and a banner is not an empty state, so this collapse tells the user nothing untrue. The raw
     * [resumable] stays exposed for a page that wants to say more.
     */
    val homeResume: StateFlow<ResumeAffordance> =
        combine(activeSession, _resumable) { live, stale -> resumeAffordance(live, stale.valueOrNull()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumeAffordance.None)

    private val _resumePrompt = MutableStateFlow<ResumePromptState?>(null)
    val resumePrompt: StateFlow<ResumePromptState?> = _resumePrompt.asStateFlow()

    /**
     * The session the prompt has already been raised for, so it is asked **once on first sight**
     * (`01-shell.md` §B's `StaleSession` state) rather than on every recomposition of HOME.
     *
     * Reset by [clearTransientState], which is what makes §B edge case 9 true: HOME pressed with the
     * prompt open dismisses it, leaves the row untouched, and re-asks on the next entry.
     */
    private var promptedSessionId: Long? = null

    /** Re-reads the open-session row. Cheap, and the only way [resumable] ever changes. */
    fun refreshResumable() {
        viewModelScope.launch { _resumable.value = repository.resumableSession() }
    }

    /**
     * `GYM.HOME` has appeared: re-read the row, and raise the prompt if one is owed.
     *
     * Three guards, each from a different line of §B:
     * - a **live** session suppresses it entirely (edge case 1 — the live banner wins, and the stale
     *   row is asked about once that session ends; never two banners),
     * - the same session is never asked about twice ([promptedSessionId]),
     * - a prompt already on screen is not replaced under the user's thumb.
     */
    fun onHomeShown() {
        viewModelScope.launch {
            val outcome = repository.resumableSession()
            _resumable.value = outcome
            val row = outcome.valueOrNull() ?: return@launch
            if (_activeSession.value != null) return@launch
            if (promptedSessionId == row.sessionId || _resumePrompt.value != null) return@launch
            promptedSessionId = row.sessionId
            _resumePrompt.value = ResumePromptState(session = row, options = resumeOptions(row))
        }
    }

    /**
     * 続ける on a **live** session's banner. Pure navigation: nothing is written, because the workout
     * the user is being taken back to never stopped (`01-shell.md` §B's actions table).
     */
    fun resumeLiveSession() {
        val live = _activeSession.value ?: return
        go(GymRoute.Session(live.routineId, resume = true))
    }

    /**
     * 続ける on the prompt: rehydrate [ActiveSession] from the row and open the player.
     *
     * Any [PendingStart] riding on the prompt is dropped — see [PendingStart]. Guarded on
     * [ResumeOptions.canResume] so that a call from a page that drew the button anyway (or a stale
     * TalkBack custom action) cannot resume across a reboot, which would fabricate duration.
     */
    fun resumeStaleSession() {
        val state = _resumePrompt.value ?: return
        if (state.writing || !state.options.canResume) return
        val row = state.session
        closePrompt()
        _startInFlight.value = null
        onSessionOpened(ActiveSession(row.sessionId, row.routineId, row.routineName))
        go(GymRoute.Session(row.routineId, resume = true))
    }

    /**
     * 記録する: finish the open session as a **partial**, honestly.
     *
     * `complete = false` is not a failure path — `00-plan.md` §4.1 rule 2: a partial session is a real
     * session, fully recorded, excluded from PR eligibility only. On success the page it opens is the
     * record, unless the prompt was a start guard, in which case the start it was blocking proceeds
     * instead (`03-player.md` §A).
     */
    fun recordStaleSession() {
        val state = _resumePrompt.value ?: return
        if (state.writing || !state.options.canRecord) return
        _resumePrompt.value = state.copy(writing = true, fault = null)
        viewModelScope.launch {
            when (val write = repository.finishSession(state.session.sessionId, complete = false)) {
                is GymWrite.Ok -> {
                    onSessionClosed()
                    closePrompt()
                    _resumable.value = repository.resumableSession()
                    val pending = state.pendingStart
                    if (pending == null) {
                        go(GymRoute.Record(write.value.summary.key))
                    } else {
                        proceedWithStart(pending)
                    }
                }
                // The row survives, the banner survives, the prompt stays open with the reason on it.
                is GymWrite.Failed -> _resumePrompt.value =
                    _resumePrompt.value?.copy(writing = false, fault = write.fault)
            }
        }
    }

    /** 捨てる, first tap. Asks again rather than deleting — §B's actions table: "after a second confirm". */
    fun requestDiscardStaleSession() {
        val state = _resumePrompt.value ?: return
        if (state.writing || !state.options.canDiscard) return
        _resumePrompt.value = state.copy(confirmingDiscard = true, fault = null)
    }

    fun cancelDiscardStaleSession() {
        _resumePrompt.value = _resumePrompt.value?.copy(confirmingDiscard = false)
    }

    /**
     * 捨てる, confirmed. Deletes the row and stays put.
     *
     * **Never optimistic.** §B edge case 8: the banner is removed by the store reporting the row gone,
     * not by the tap, because a resumable workout vanishing on a write that then failed is the worst
     * outcome this page has.
     */
    fun confirmDiscardStaleSession() {
        val state = _resumePrompt.value ?: return
        if (state.writing || !state.confirmingDiscard) return
        _resumePrompt.value = state.copy(writing = true, fault = null)
        viewModelScope.launch {
            when (val write = repository.discardSession(state.session.sessionId)) {
                is GymWrite.Ok -> {
                    onSessionClosed()
                    closePrompt()
                    _resumable.value = repository.resumableSession()
                    state.pendingStart?.let { proceedWithStart(it) }
                }
                is GymWrite.Failed -> _resumePrompt.value =
                    _resumePrompt.value?.copy(writing = false, confirmingDiscard = false, fault = write.fault)
            }
        }
    }

    /**
     * Dismiss — and **decide nothing** (`00-plan.md` §2 row 8).
     *
     * The session row is untouched, [resumable] is untouched, and the つづき banner therefore stays
     * exactly as it was. The only casualties are the two things that were questions themselves: the
     * pending start, and 始める's in-flight lock, which would otherwise leave the detail page showing
     * a 支度 that will never resolve.
     */
    fun dismissResumePrompt() {
        if (_resumePrompt.value?.writing == true) return
        _resumePrompt.value = null
        _startInFlight.value = null
    }

    private fun closePrompt() {
        _resumePrompt.value = null
        promptedSessionId = null
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // GYM.LIBRARY.INDEX
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /** The unfiltered library, straight from the store, for a caller that wants the whole list. */
    val routines: StateFlow<Loadable<List<RoutineSummary>>> get() = repository.routines

    val usageCounts: StateFlow<Map<String, Int>> = repository.usageCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val recentUsage: StateFlow<Map<String, Int>> = repository.recentUsage(RECENT_USAGE_DAYS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _routineFilter = MutableStateFlow(RoutineFilter())

    /**
     * The index's query and chips. View-model-only and **never persisted** — a filter that survives an
     * app restart is a filter the user will one day blame the library for
     * (`04-library-records.md` §3).
     */
    val routineFilter: StateFlow<RoutineFilter> = _routineFilter.asStateFlow()

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    private val _libraryListState = MutableStateFlow(LazyListState())

    /**
     * The index's scroll position, hoisted out of the composable exactly as §3 edge case 8 requires:
     * "rotation preserves query, filters and scroll — `rememberLazyListState` hoisted into
     * `GymViewModel`, not `remember`."
     *
     * A `LazyListState` in a ViewModel is a Compose type outside a composition, which is worth one
     * sentence of justification. It is constructible off the composition, holds only a scroll offset
     * and its animation scaffolding, and `MainActivity` declares `configChanges` for orientation, so
     * it is never recreated under it. *Rejected* — storing the index and offset as two `Int`s and
     * having each page re-attach them through a `snapshotFlow`: it is the same state with a
     * hand-written synchroniser in front of it, and the synchroniser is where the off-by-one lives.
     *
     * A `StateFlow` rather than a bare `val` so that leaving the shell can genuinely discard it —
     * a `LazyListState` cannot be scrolled back to zero without suspending, and replacing the object
     * is the honest reset.
     */
    val libraryListState: StateFlow<LazyListState> = _libraryListState.asStateFlow()

    /**
     * Station names per **version** id, for search.
     *
     * §3 edge case 2 requires 懸垂 to surface シンディ, and a [RoutineSummary] is a projection that
     * deliberately carries no stations (one query, no N+1). The names therefore have to come from
     * somewhere, and the only read that has them is `routineVersion(versionId)` — a `suspend` read of
     * an **immutable** row, which is what makes this cache safe to hold forever: a version is never
     * updated and never deleted, so there is no staleness window to manage.
     *
     * *Rejected* — a new repository read returning every routine's station names. It would need a
     * method on `GymRepository` that `GymStore` does not implement, and this unit does not own that
     * file; it would also re-scan `routine_station` on every keystroke's upstream emission for data
     * that cannot change.
     */
    private val stationNames = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    /**
     * The index's three sections, searched, filtered and ranked by [libraryIndexRoutines].
     *
     * Still a `Loadable`, and that is the point: `Failed` reaches the page as `Failed` and renders a
     * `FaultPanel`, never a fall-through to an empty list (`04-library-records.md` §3's `Error`
     * state). The `Loading` seed is what `NoMatch` is told apart from.
     */
    val libraryIndex: StateFlow<Loadable<LibraryIndexRoutines>> = combine(
        repository.routines.onEach(::cacheStationNames),
        _routineFilter,
        stationNames,
        recentUsage,
        usageCounts,
    ) { loadable, filter, names, recent, counts ->
        loadable.mapValue { list ->
            libraryIndexRoutines(list, filter, recent, counts) { names[it.versionId].orEmpty() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun setQuery(query: String) = _routineFilter.update { it.copy(query = query) }

    fun toggleTier(tier: Tier) = _routineFilter.update {
        it.copy(tiers = if (tier in it.tiers) it.tiers - tier else it.tiers + tier)
    }

    fun toggleEngine(engine: Engine) = _routineFilter.update {
        it.copy(engines = if (engine in it.engines) it.engines - engine else it.engines + engine)
    }

    /** Single-select: tapping the selected chip clears it, so there is no third tap to find. */
    fun setDurationBucket(bucket: DurationBucket?) = _routineFilter.update {
        it.copy(duration = if (it.duration == bucket) null else bucket)
    }

    /** 絞り込みを外す — chips only. The query is the user's typing and is not swept away with them. */
    fun clearRoutineFilters() = _routineFilter.update {
        it.copy(tiers = emptySet(), engines = emptySet(), duration = null)
    }

    fun openSearch() {
        _searchOpen.value = true
    }

    /**
     * Back with the search row open closes it **and clears the query — one back press, not two**
     * (`04-library-records.md` §3's back behaviour).
     *
     * @return true when it consumed the press, so the page's `BackHandler` knows whether to fall
     *   through to the shell's rebase.
     */
    fun closeSearch(): Boolean {
        if (!_searchOpen.value) return false
        _searchOpen.value = false
        _routineFilter.update { it.copy(query = "") }
        return true
    }

    /**
     * Fills [stationNames] for any version it has not seen. Immutable rows, so this runs once per
     * version for the life of the process and never re-runs for a name that changed.
     */
    private fun cacheStationNames(loadable: Loadable<List<RoutineSummary>>) {
        val summaries = loadable.valueOrNull() ?: return
        val missing = summaries.map { it.versionId }.filterNot { it in stationNames.value }.distinct()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val resolved = missing.mapNotNull { versionId ->
                val snapshot = repository.routineVersion(versionId).valueOrNull() ?: return@mapNotNull null
                // The id is kept when the catalogue no longer knows it: searching for a station that
                // is missing should find nothing, not throw away the rest of the routine's names.
                versionId to snapshot.stations.mapNotNull { it.exercise?.nameJa }
            }
            if (resolved.isNotEmpty()) stationNames.update { it + resolved }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // GYM.LIBRARY.DETAIL
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * One `stateIn`-ed flow per routine, cached, so that returning to a detail page — or rotating on
     * one — does not re-run the join behind it. Keyed by routine id and read only from the main
     * thread, which is where a composable asks for it.
     *
     * Cleared on leaving the shell ([clearTransientState]), which bounds the map to the routines
     * visited in one visit to 鍛錬.
     */
    private val detailFlows = mutableMapOf<String, StateFlow<Loadable<RoutineDetail>>>()
    private val tierFlows = mutableMapOf<String, StateFlow<List<RoutineSummary>>>()
    private val countFlows = mutableMapOf<String, StateFlow<Loadable<Int>>>()

    fun routineDetail(routineId: String): StateFlow<Loadable<RoutineDetail>> =
        detailFlows.getOrPut(routineId) {
            repository.routineDetail(routineId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
        }

    /**
     * The routine and its scaled siblings — `やさしい` and friends are **separate stored routines**
     * sharing a `scaled_from_routine_id`, not a runtime scaling (`04-library-records.md` §1 rule 6),
     * which is why choosing a chip changes the id the page renders rather than a multiplier.
     *
     * Not a `Loadable`: `02-data.md` §C.1 gives it as a bare `Flow<List<…>>` that degrades to empty,
     * and an absent chip row is the routine having no scalings rather than a claim about the store.
     */
    fun scaledTiers(routineId: String): StateFlow<List<RoutineSummary>> =
        tierFlows.getOrPut(routineId) {
            repository.scaledTiers(routineId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    /**
     * What 削除's copy branches on: sessions exist ⇒ archive, none ⇒ purge (§1 rules 2 and 4) — and
     * **`Loading` / `Failed` ⇒ neither**.
     *
     * Seeded with [Loadable.Loading], not with zero, and that single character is the whole fix. A zero
     * seed made the first frames of the query say 「やった記録はありません。完全に消えます。」 under a
     * 完全に削除 button, which is an ありません-as-emptiness claim about the user's records on the one
     * irreversible dialog in the feature (`00-plan.md` §4.1 rule 1, `DECISIONS.md` §Q6). See
     * [GymRepository.countForRoutine] for the read's half of the argument.
     */
    fun countForRoutine(routineId: String): StateFlow<Loadable<Int>> =
        countFlows.getOrPut(routineId) {
            repository.countForRoutine(routineId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
        }

    private val _selectedTier = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * Base routine id → the scaled routine id currently being rendered for it. Absent means the base
     * itself, so the map starts empty and never needs seeding.
     *
     * In memory, not in the store: `04-library-records.md` §3 asks only that "the chosen `ScalingTier`
     * is remembered per routine, so re-entering restores it", and the `setLastTier(routineId, tier)`
     * that `03-player.md` §A's start path calls **does not exist on `GymRepository`** — see this
     * unit's report. A visit-scoped memory delivers the sentence; a persisted one would need a column.
     */
    val selectedTier: StateFlow<Map<String, String>> = _selectedTier.asStateFlow()

    fun selectTier(baseRoutineId: String, routineId: String) =
        _selectedTier.update { it + (baseRoutineId to routineId) }

    /** The routine id the detail page should render for [baseRoutineId] — its own, unless a chip won. */
    fun tierRoutineId(baseRoutineId: String): String =
        _selectedTier.value[baseRoutineId] ?: baseRoutineId

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Writes, and the fault they surface through
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private val _writeFault = MutableStateFlow<GymFault?>(null)

    /**
     * The last failed write on a library page, for a `FaultStrip` rendered through the existing
     * `faultCopy` — no new fault chrome (`00-plan.md` §4.1, `DECISIONS.md` §Q6).
     *
     * One field for every action on the page rather than one per action: only one write is ever in
     * flight from a tap, and a per-action fault would be three-quarters null on every frame.
     */
    val writeFault: StateFlow<GymFault?> = _writeFault.asStateFlow()

    fun dismissWriteFault() {
        _writeFault.value = null
    }

    /** Tapping a routine card: the recency bump, then the page. The only happy-path write on HOME. */
    fun openRoutine(routineId: String) {
        viewModelScope.launch { repository.touchRoutine(routineId) }
        go(GymRoute.RoutineDetail(routineId))
    }

    fun setFavourite(routineId: String, favourite: Boolean) =
        write { repository.setFavourite(routineId, favourite) }

    /**
     * 写して作る. The name is [uniqueName]'s — 「七分間 の写し」, then 「の写し二」 — chosen against the
     * library the store last reported, so a copy never arrives pre-collided (§3 edge case 7).
     *
     * On success the builder opens on the **new** id, because the original is genuinely unchanged.
     *
     * **[name] exists because an archived routine is not in [GymRepository.routines].** That list is
     * `readSummaries(archivedOnly = false)` — `WHERE r.archived_at IS NULL` — so resolving the source
     * name from it alone answered 写して作る on an archived routine with 「この型は削除されています」
     * while copying nothing. `04-library-records.md` §1 rule 3 keeps 写して作る **enabled** on exactly
     * that page (reachable as specced from a session's 型を見る), so that was the live path, not a
     * corner. A caller holding the snapshot passes the name it already has; a caller that does not
     * falls back to the list, and then to [GymRepository.routineDetail], which reads archived rows too.
     *
     * [uniqueName] still computes against the visible library. Deliberate: a copy is created unarchived
     * and has to be distinguishable from the routines the user can currently see, not from ones they
     * deleted. An unread library is still [GymFault.RoutineGone] — with no list there is no set to be
     * unique against, and guessing a name that then collides is worse than saying so.
     */
    fun duplicateRoutine(routineId: String, name: String? = null) {
        val library = repository.routines.value.valueOrNull()
        if (library == null) {
            _writeFault.value = GymFault.RoutineGone
            return
        }
        val listed = library.firstOrNull { it.routineId == routineId }?.name
        val taken = library.mapTo(mutableSetOf()) { it.name }
        viewModelScope.launch {
            val sourceName = name
                ?: listed
                ?: repository.routineDetail(routineId)
                    .first { it !is Loadable.Loading }
                    .valueOrNull()?.snapshot?.name
            if (sourceName == null) {
                // The routine is genuinely gone, not merely archived: nothing anywhere can name it, and
                // inventing a name would be worse than saying so.
                _writeFault.value = GymFault.RoutineGone
                return@launch
            }
            when (val outcome = repository.duplicateRoutine(routineId, uniqueName(sourceName, taken))) {
                is GymWrite.Ok -> go(GymRoute.Builder(outcome.value))
                is GymWrite.Failed -> _writeFault.value = outcome.fault
            }
        }
    }

    /**
     * 削除 with sessions behind it: a soft archive, never a delete. The records stay readable and keep
     * their PRs (§1 rule 2).
     *
     * **Where it lands afterwards is the caller's, because the two callers owe different things.**
     * `GYM.LIBRARY.DETAIL` passes [GymTab.Library]: the deleted routine's own page is on top of the
     * stack and has to be popped (`04-library-records.md` §2). `GYM.HOME`'s long-press row takes the
     * default and **stays put** — `01-shell.md` §B's actions table gives that row a Navigates value for
     * 編集 only, and the feed re-emits without the routine, so the card disappears in place. Hard-coding
     * `GymTab.Library` here teleported a HOME user to the 型 tab for deleting a card they were looking
     * at, which is a rebase of `[Home]` to `[Library]` that nothing asked for.
     */
    fun archiveRoutine(routineId: String, thenSelect: GymTab? = null) = write(thenSelect) {
        repository.archiveRoutine(routineId)
    }

    /** 完全に削除 — offered only on a `Ready(0)` count, so nothing readable is being destroyed. [thenSelect] as [archiveRoutine]. */
    fun purgeRoutine(routineId: String, thenSelect: GymTab? = null) = write(thenSelect) {
        repository.purgeRoutine(routineId)
    }

    /** 元に戻す, from an archived routine's detail page. Stays put: the page is now a live routine. */
    fun restoreRoutine(routineId: String) = write { repository.restoreRoutine(routineId) }

    private fun write(thenSelect: GymTab? = null, body: suspend () -> GymWrite<*>) {
        viewModelScope.launch {
            when (val outcome = body()) {
                is GymWrite.Ok -> thenSelect?.let(::selectTab)
                is GymWrite.Failed -> _writeFault.value = outcome.fault
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // The start path — 03-player.md §A
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private val _startInFlight = MutableStateFlow<String?>(null)

    /**
     * The routine whose 始める is mid-flight, or null.
     *
     * Both the re-entrancy guard §A asks for and the page's `Starting` state: 始める becomes a
     * non-interactive 支度. A double tap cannot create two sessions — and `idx_session_live` would
     * refuse the second insert anyway, which is the guarantee this is merely being polite in front of.
     */
    val startInFlight: StateFlow<String?> = _startInFlight.asStateFlow()

    /**
     * 始める: guard, compile, **insert, then navigate** (`03-player.md` §A).
     *
     * The order is the entire mechanism, and `00-plan.md` §2 row 7 moved it here when `PREFLIGHT` was
     * deleted as a route:
     *
     * 1. **Start guard.** An open session — anyone's, any routine — raises the resume prompt *instead
     *    of* starting. It is not a refusal: the start is remembered as a [PendingStart] and proceeds
     *    once 記録する or 捨てる resolves the old one.
     * 2. **Compile first.** A routine with no stations, or with a station the catalogue no longer
     *    knows, fails here — on a page with a back button — rather than three segments into a workout.
     * 3. **Insert before navigating.** A session that exists on screen but not in the database is the
     *    one state process death cannot recover from, so navigation is blocked on the row landing. A
     *    `GymWrite.Failed` surfaces through [writeFault] and **does not navigate**.
     *
     * @param tier recorded on the session row, never applied — picking a scaling picks a *different
     *   routine id* upstream (`04-library-records.md` §1 rule 6), so by the time a snapshot reaches
     *   the compiler the scaling has already happened. The base routine is 基本 by definition, which
     *   is why that is the default and not a guess.
     */
    fun startSession(
        routineId: String,
        tier: ScalingTier = ScalingTier.RX,
        roundsPlanned: Int? = null,
    ) {
        if (_startInFlight.value != null) return
        _startInFlight.value = routineId
        _writeFault.value = null
        val request = PendingStart(routineId, tier, roundsPlanned)
        viewModelScope.launch {
            val open = repository.resumableSession()
            _resumable.value = open
            val row = open.valueOrNull()
            if (row != null) {
                promptedSessionId = row.sessionId
                _resumePrompt.value =
                    ResumePromptState(session = row, options = resumeOptions(row), pendingStart = request)
                // The lock stays held on purpose: 始める reads 支度 until the prompt is answered or
                // dismissed, and dismissing releases it (see dismissResumePrompt).
                return@launch
            }
            // A read that failed cannot promise there is no live session. Starting anyway is safe:
            // idx_session_live refuses a second insert, and that refusal arrives as a fault below.
            proceedWithStart(request)
        }
    }

    private suspend fun proceedWithStart(request: PendingStart) {
        _startInFlight.value = request.routineId
        val detail = repository.routineDetail(request.routineId).first { it !is Loadable.Loading }
        val ready = when (detail) {
            is Loadable.Ready -> detail.value
            is Loadable.Failed -> return failStart(detail.fault as? GymFault ?: GymFault.RoutineGone)
            Loadable.Loading -> return failStart(GymFault.Unknown(null))
        }
        // The same predicate the page disables 始める with, re-asserted at the write: the flow behind
        // the page is the source of truth and may have moved since the frame that drew the button.
        if (startBlock(ready) != StartBlock.None) return failStart(GymFault.Rejected)

        val step = ready.snapshot.progressionProgramId?.let { programId ->
            repository.progression(programId).first { it !is Loadable.Loading }.valueOrNull()?.currentStep
        }
        val library = ExerciseCatalog.all().associateBy { it.id }
        if (runCatching { compile(ready.snapshot, request.tier, library, step) }.isFailure) {
            return failStart(GymFault.Rejected)
        }

        when (val write = repository.startSession(request.routineId, request.tier.name, request.roundsPlanned)) {
            is GymWrite.Ok -> {
                onSessionOpened(
                    ActiveSession(
                        sessionId = write.value,
                        routineId = request.routineId,
                        routineName = ready.snapshot.name,
                    ),
                )
                _resumable.value = repository.resumableSession()
                promptedSessionId = write.value
                _startInFlight.value = null
                go(GymRoute.Session(request.routineId, resume = false))
            }
            is GymWrite.Failed -> failStart(write.fault)
        }
    }

    private fun failStart(fault: GymFault) {
        _writeFault.value = fault
        _startInFlight.value = null
    }

    /**
     * もう一度 behind a `FaultPanel`: re-runs every failed read, exactly as the calendar's does, and
     * re-asks the one read that is not a flow.
     */
    fun retry() {
        repository.retry()
        refreshResumable()
    }

    /**
     * The user has read 記録を読めません on a quarantined database and is putting it down.
     *
     * Beside [retry] because it is the other half of that strip and the opposite act: [retry] re-asks
     * the store, this accepts the answer. **Nothing in Phase 1 calls it** — the affordance belongs to
     * `GYM.RECORDS.INDEX`'s fault strip (Phase 3), which is the surface that reads `StoreCorrupt`. It
     * is declared now because the pair (interface + `override`) had to land together and a method the
     * ViewModel cannot reach is a method Phase 3 would have to re-plumb.
     */
    fun acknowledgeHistoryLoss() {
        viewModelScope.launch { repository.acknowledgeHistoryLoss() }
    }

    fun go(route: GymRoute) {
        _stack.update { push(it, route) }
    }

    /** A ladder rung swapping the page it was tapped from — `00-plan.md` §3, and see [replaceTop]. */
    fun replaceTop(route: GymRoute) {
        _stack.update { replaceTop(it, route) }
    }

    fun selectTab(tab: GymTab) {
        _stack.update { selectTab(it, tab) }
    }

    /** @return true if the gym consumed Back; false means the caller must exit the shell. */
    fun onBack(): Boolean = when (val outcome = back(_stack.value)) {
        is BackOutcome.Pop -> { _stack.value = outcome.stack; true }
        is BackOutcome.Rebase -> { _stack.value = outcome.stack; true }
        BackOutcome.ExitShell -> false
    }

    /**
     * The player has opened a session. Called after `startSession` returned an id — never before, so
     * the in-memory session and the row can never disagree about whether one exists.
     */
    fun onSessionOpened(session: ActiveSession) {
        _activeSession.value = session
    }

    /**
     * The player has finished, quit, or discarded. **The only way this becomes null.** Neither Back,
     * nor a HOME press, nor the shell unmounting may call it: each of those is the user going
     * somewhere else, and none of them is the user saying they have stopped training.
     *
     * It is also what excludes 記録 (the post-session page), 型の中身 and the resume prompt from
     * keep-screen-on: by the time any of those is on screen the workout is over or has not started,
     * and `GymShell` needs no route list to know it (`01-shell.md` §A.7).
     */
    fun onSessionClosed() {
        _activeSession.value = null
    }

    /**
     * Called on every exit from the shell — Back at the root, or a HOME press.
     *
     * Two consequences worth stating outright, because both look like bugs from the outside:
     *
     * - A HOME press mid-session must **not** commit the session as partial. Only 記録する in the quit
     *   sheet does that.
     * - On re-entry, `GYM.HOME`'s つづき banner is fed by `activeSession ?: interruptedRow` — the
     *   in-memory session takes precedence, so HOME-and-return resumes exactly, with the monotonic
     *   clock having kept running while the shell was away.
     *
     * This is a state reset, not a teardown. There is deliberately no `clear()` that nulls the
     * repositories: the ViewModel outlives every exit and is recreated only with the Activity.
     */
    fun onLeaveShell(reason: LeaveReason) {
        _stack.value = listOf(GymRoute.Home)
        clearTransientState()
        // _activeSession is untouched. Always. So is the open-session row in SQLite — it is the
        // process-death safety net, and deleting it on a HOME press is how you lose a workout to a fat
        // thumb — and so is any in-flight write, which completes on viewModelScope because cancelling
        // a half-written session is worse than finishing it.
    }

    /**
     * Everything that is a question rather than an answer, dropped on the way out.
     *
     * `01-shell.md` §A.5 fixes the list: sheets, prompts, the builder's unsaved draft, the long-press
     * menu, the station picker, the fault banner. Each is transient by definition — a stale-session
     * prompt re-asks itself from the same database row, a fault re-derives from the next query, and an
     * unsaved draft was abandoned by the press that got here.
     *
     * The alternative — each page observing the shell's exit and clearing itself — is how one of them
     * ends up not doing it, and the symptom is a modal that reappears over a screen the user has
     * already left.
     *
     * The query, the chips and the scroll go with them: `04-library-records.md` §3 keeps them "in
     * `GymViewModel`, retained across tab switches, **discarded on gym exit**". [promptedSessionId] is
     * reset so the stale prompt re-asks on the next entry, which is `01-shell.md` §B edge case 9.
     *
     * The one field that must never appear here is [_activeSession]. Neither may [_resumable]: the
     * open row is a fact about the store, not a question on screen, and clearing it would make the
     * next つづき banner flash in from Loading for no reason.
     */
    private fun clearTransientState() {
        _resumePrompt.value = null
        promptedSessionId = null
        _startInFlight.value = null
        _writeFault.value = null
        _routineFilter.value = RoutineFilter()
        _searchOpen.value = false
        _libraryListState.value = LazyListState()
        _selectedTier.value = emptyMap()
        detailFlows.clear()
        tierFlows.clear()
        countFlows.clear()
    }
}

/**
 * `Loadable`'s functor, private because widening it would mean editing `calendar/CalendarOutcome.kt`.
 *
 * It exists so that a page-shaping step never has to unwrap: `Failed` passes through as `Failed` and
 * `Loading` as `Loading`, which is the mechanical half of `00-plan.md` §4.1 rule 1. The moment this is
 * replaced by `valueOrNull()` in a pipeline, loading becomes indistinguishable from empty.
 */
private inline fun <T, R> Loadable<T>.mapValue(transform: (T) -> R): Loadable<R> = when (this) {
    is Loadable.Ready -> Loadable.Ready(transform(value))
    is Loadable.Failed -> this
    Loadable.Loading -> Loadable.Loading
}

/**
 * Manual factory, mirroring `LauncherViewModelFactory`. No DI framework, as everywhere in this repo.
 *
 * Cheap to construct and idempotent in [create], which it has to be: the Activity's `by viewModels`
 * delegate and Compose's `viewModel(factory = …)` each build one, and they must agree on a single
 * instance. They do, because both resolve against `MainActivity`'s `ViewModelStore` under the same
 * default key, and because the repositories behind this are singletons in the existing
 * `BlockadeRepository` / `AppRepository` style.
 */
class GymViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GymViewModel(
            repository = GymRepository.getInstance(appContext),
            preferences = GymPreferencesStore(appContext),
        ) as T
    }
}
