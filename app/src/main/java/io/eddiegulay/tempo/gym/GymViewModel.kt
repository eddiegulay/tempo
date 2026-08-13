package io.eddiegulay.tempo.gym

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * It is empty today because every one of those fields belongs to a page this unit does not build,
     * and it exists anyway as the single place they must be reset from. The alternative — each page
     * observing the shell's exit and clearing itself — is how one of them ends up not doing it, and
     * the symptom is a modal that reappears over a screen the user has already left.
     *
     * The one field that must never appear here is [_activeSession].
     */
    private fun clearTransientState() = Unit
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
