package io.eddiegulay.tempo.ui.gym.session

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.calendar.findActivity
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.EffectiveGymPreferences
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymTab
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.GymWrite
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.TrainingHold
import io.eddiegulay.tempo.gym.cue.Cue
import io.eddiegulay.tempo.gym.cue.CueEngine
import io.eddiegulay.tempo.gym.cue.CueEvent
import io.eddiegulay.tempo.gym.cue.CueState
import io.eddiegulay.tempo.gym.session.BackTapResolver
import io.eddiegulay.tempo.gym.session.CompletionReason
import io.eddiegulay.tempo.gym.session.Destination
import io.eddiegulay.tempo.gym.session.LONG_PAUSE_MS
import io.eddiegulay.tempo.gym.session.Overlay
import io.eddiegulay.tempo.gym.session.Rule
import io.eddiegulay.tempo.gym.session.ScalingTier
import io.eddiegulay.tempo.gym.session.SessionContext
import io.eddiegulay.tempo.gym.session.SessionEffect
import io.eddiegulay.tempo.gym.session.SessionEvent
import io.eddiegulay.tempo.gym.session.SessionTransition
import io.eddiegulay.tempo.gym.session.Timeline
import io.eddiegulay.tempo.gym.session.compile
import io.eddiegulay.tempo.gym.session.needsAnotherRound
import io.eddiegulay.tempo.gym.session.replay
import io.eddiegulay.tempo.gym.session.stateAt
import io.eddiegulay.tempo.gym.session.step
import io.eddiegulay.tempo.ui.tempoBackground
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The render tick — `03-player.md` §B.5.
 *
 * Fifty milliseconds so a one-second numeral flip is never more than 50ms late, and so the cue
 * schedule has sub-frame resolution to be checked against. It is a *read* cadence and nothing else:
 * the tick re-reads `stateAt(clock.elapsedMs())` and never advances anything, so a tick that arrives
 * late renders late and loses no time at all.
 */
private const val TICK_MS: Long = 50L

/**
 * Whether the 50ms read loop runs — `03-player.md` §B.5's gate, and the load-bearing half of §E.5's
 * Phase 3 change.
 *
 * A free function rather than a `when` inside the composable for the same reason `disarmPlan` is one:
 * this is the difference between a workout that cues from a pocket and one that does not, it cannot be
 * seen in a screenshot, it cannot fail a build, and it needs no device to decide. `SessionHostTest`
 * walks all eight rows.
 *
 * @param ticking the machine's answer: false while paused, while the quit sheet is open, and once the
 *   session is finished. Nothing below can turn this on — a paused session does not tick because a
 *   notification is up.
 * @param visible whether the window is on screen, from the host's `ON_START` / `ON_STOP` observer.
 * @param serviceHeld whether a foreground service is actually holding the process (`TrainingHold`).
 *   **This term is why the service exists.** [SessionController.onTick] is what advances a segment and
 *   what arms the next cue, so a loop stopped at `ON_STOP` means a pocketed session neither cues nor
 *   advances — and, because the notification is a projection of the published frame, it also freezes
 *   the notification on the phase the phone was pocketed in. Without this term the service posts a
 *   permanently wrong notification and buys nothing else.
 *
 * With no service the two terms collapse to `ticking && visible`, which is exactly what
 * `repeatOnLifecycle(STARTED)` used to impose from the outside — a backgrounded process with nothing
 * keeping it alive is about to be frozen, and twenty wake-ups a second inside it is pure cost.
 */
internal fun sessionShouldTick(ticking: Boolean, visible: Boolean, serviceHeld: Boolean): Boolean =
    ticking && (visible || serviceHeld)

/**
 * The session host: the object that makes the player run.
 *
 * It owns exactly four things — the compiled [Timeline], the [SessionClock], the [CueEngine], and the
 * position in the state machine — and it owns them **for the lifetime of the Session route**, not of
 * the gym. It is deliberately not part of `GymViewModel`: `01-shell.md` §A makes the ViewModel the
 * shell's state (routes, feeds, the live-session *fact*), and a timeline living there would outlive
 * the screen that draws it and give the shell an opinion about how a workout runs.
 *
 * Everything it does is one loop:
 *
 * 1. the tick reads `stateAt(clock.elapsedMs(now))` and hands the frame to Compose;
 * 2. a user event or a passed boundary is dispatched into `step(ctx, event)` — the pure §C.1 table;
 * 3. the returned [SessionTransition]'s effects are applied, in order, against `GymRepository`.
 *
 * **State is never mutated by a tick.** If a change to this file starts advancing a segment on a
 * timer, it is wrong — re-read §B.5. Every way time can skip (a doze, a backgrounded hour, a resume,
 * a rewind) is the same code path here precisely because the tick has no memory.
 *
 * *Rejected* — a second `ViewModel` scoped to the route. It would need its own factory, its own
 * `SavedStateHandle` and a `ViewModelStoreOwner` this shell does not have (there is no NavHost —
 * `GymRoute.kt` explains why), and it would buy nothing: process death is survived by the **database**
 * (§E.1), not by retained objects, and the recovery path has to work on a cold start anyway.
 *
 * @param nowElapsed the monotonic clock. Injected so the whole controller can be driven by a test
 *   without a device; production passes `SystemClock.elapsedRealtime`, which is the only clock this
 *   feature keeps time with (§B.5).
 */
@Stable
class SessionController(
    private val gym: GymViewModel,
    private val route: GymRoute.Session,
    private val cues: CueEngine,
    private val scope: CoroutineScope,
    private val lib: Map<String, Exercise>,
    private val nowElapsed: () -> Long = SystemClock::elapsedRealtime,
    private val nowWall: () -> Long = System::currentTimeMillis,
) : SessionActions {

    /** What the page draws. The only observable this class exposes, rebuilt whole on every change. */
    var screen: SessionScreen by mutableStateOf(SessionScreen.Loading)
        private set

    /**
     * Whether the session **wants** the clock read: false while paused, while the quit sheet is open,
     * and once the session is finished.
     *
     * This is the machine's half of the question. Whether the loop actually runs is [shouldTick], which
     * adds the process's half.
     */
    var ticking: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the window is on screen. `ON_START` / `ON_STOP`, kept here rather than in the host so
     * that the one predicate below can be read in one place.
     */
    private var visible: Boolean by mutableStateOf(true)

    /**
     * Whether the health foreground service is holding this session — `gym/TrainingHold`, pushed in by
     * the host through [onServiceHold] rather than read directly, so this class keeps no Android
     * dependency it did not already have and a test can drive it.
     */
    private var serviceHeld: Boolean by mutableStateOf(false)

    /**
     * Whether the 50ms loop should actually be running, and **the whole reason the service exists**.
     *
     * Before Phase 3 this was `ticking` gated by `repeatOnLifecycle(STARTED)` in the host, which is
     * correct while there is no foreground service and catastrophic once there is one: cue arming and
     * segment advance are both driven by [onTick], so a stopped loop means nothing re-arms, nothing
     * advances, and — because the notification is a projection of the published frame — the
     * notification freezes on whichever phase the phone was pocketed in. A twenty-minute session would
     * have shown 運動 ・ 腕立て伏せ with a permanent 休止 button for its whole length.
     *
     * So the gate is: the machine wants a tick, **and** either someone can see it or something is
     * keeping the process alive on purpose. With no service the second term is false and this is
     * exactly the old behaviour — a loop that spins doing nothing twenty times a second is still twenty
     * wake-ups a second, and a backgrounded process without a foreground service is about to be frozen
     * anyway.
     */
    val shouldTick: Boolean get() = sessionShouldTick(ticking, visible, serviceHeld)

    /** The host's report from `TrainingHold`. Idempotent; called on every change and on none besides. */
    fun onServiceHold(held: Boolean) {
        serviceHeld = held
    }

    private var loaded = false
    private var lastPrefs: EffectiveGymPreferences? = null
    private var sessionId = -1L
    private var routineName = ""
    private var stationsPlanned = 0
    private var snapshot: RoutineSnapshot? = null
    private var timeline: Timeline? = null
    private var clock: SessionClock? = null

    private var overlay = Overlay.NONE
    private var pausedBeforeSheet = false
    private var autoAdvance = false
    private var stalled = false
    private var resultsWritten = 0

    private var resumePrepareTotalMs = 0L
    private var completionReason = CompletionReason.LAST_SEGMENT
    private var completion: SessionCompleteState? = null
    private var pendingFinishComplete: Boolean? = null

    private var loadFault: GymFault? = null
    private var liveFault: GymFault? = null
    private var quitFault: GymFault? = null
    private var quitSaving = false
    private var unrecoverable = false

    private var armedKey: Pair<Int, Long>? = null
    private var lastSkipAt: Long? = null
    private var lastDoneAt: Long? = null
    private val backTaps = BackTapResolver()

    /**
     * Serialises the store writes of one session.
     *
     * `kotlinx.coroutines.Mutex` is fair, and the launches that enter it are dispatched in order, so
     * the transitions' writes reach SQLite in the order the machine emitted them. That ordering is
     * §E.1's, and it is the difference between a record that is one segment short and one that claims
     * a segment the user never did.
     */
    private val writes = Mutex()

    // ── Loading ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Reconstructs the session — `03-player.md` §E.2, and it is the same call for a fresh start.
     *
     * **There is no separate start path, and that is the simplification the whole file rests on.**
     * `GYM.LIBRARY.DETAIL`'s 始める has already compiled the routine, inserted the row and stamped
     * `started_at_elapsed` before it navigated (§A's start path, `GymViewModel.startSession`), so a
     * session opened a moment ago reads back from the store as "a few milliseconds into 支度". One
     * reconstruction, exercised on every single launch of the player, rather than a recovery path that
     * only ever runs after a crash and is therefore only ever tested after one.
     *
     * Four refusals, each with its own screen and none of them sharing a composable
     * (`00-plan.md` §4.1 rule 1):
     *
     * - the store could not be read → [SessionScreen.Failed], through the existing `faultCopy`;
     * - there is no open session → `SessionGone`, which is the truth: something closed it;
     * - the pinned routine version is unreadable → `RoutineGone`;
     * - the stored results cannot be replayed onto the recompiled timeline → [SessionScreen.Unrecoverable],
     *   which is **not** a fault: nothing failed, and the user still owns the two honest outcomes.
     *
     * The recompile is from the **pinned `routine_version`**, never the live routine, so a routine
     * edited since the session started cannot retro-change the record (§E.2 step 1).
     */
    suspend fun load(prefs: EffectiveGymPreferences) {
        if (loaded) return
        loaded = true
        lastPrefs = prefs
        autoAdvance = prefs.stored.autoAdvanceReps
        cues.arm(prefs)

        val repository = gym.repository
        val row = when (val open = repository.resumableSession()) {
            is Loadable.Ready -> open.value
            is Loadable.Failed -> return failLoad(open.fault as? GymFault ?: GymFault.Unknown(null))
            Loadable.Loading -> return failLoad(GymFault.Unknown(null))
        } ?: return failLoad(GymFault.SessionGone)

        sessionId = row.sessionId
        routineName = row.routineName
        stationsPlanned = row.stationsPlanned
        resultsWritten = row.results.size

        val version = when (val v = repository.routineVersion(row.routineVersionId)) {
            is Loadable.Ready -> v.value
            is Loadable.Failed -> return failLoad(v.fault as? GymFault ?: GymFault.RoutineGone)
            Loadable.Loading -> return failLoad(GymFault.RoutineGone)
        }
        snapshot = version

        // A FIXED_SETS routine performs one rung of a ladder, and which rung is a fact about the
        // *programme*, not about the routine version. It is read here rather than pinned because
        // `GymRepository` exposes no read for `session.progression_step_id` — see the unit report; the
        // consequence is that a progression which advanced between the session starting and this call
        // shows up as unreplayable rather than as a silently different session.
        val progressionStep = version.progressionProgramId?.let { programId ->
            repository.progression(programId).first { it !is Loadable.Loading }.valueOrNull()?.currentStep
        }
        val tier = ScalingTier.entries.firstOrNull { it.name == row.tier } ?: ScalingTier.RX
        val compiled = runCatching { compile(version, tier, lib, progressionStep) }
            .getOrElse { return failLoad(GymFault.Rejected) }

        if (!replayable(compiled, row.results)) {
            unrecoverable = true
            publish()
            return
        }

        timeline = compiled.replay(row.results)
        clock = SessionClock.from(row.clock)

        if (route.resume) {
            // §E.3's own refusal, re-asserted at the moment of use: the store computed `resumability`
            // from the boot anchor *and* the monotonicity double-check, and the machine returns null
            // for anything but RESUMABLE. Resuming across a reboot would fabricate the time the device
            // spent switched off, so there is nothing to fall back to — the two honest outcomes are.
            val resumed = context()?.let { step(it, SessionEvent.PromptResumed(row.resumability)) }
            if (resumed == null) {
                unrecoverable = true
                publish()
                return
            }
            applyTransition(resumed)
            // The clock ran while the app was away — deep sleep included — so the segments it walked
            // past are owed their rows before anything else happens (§A WORK edge case 3).
            dispatch(SessionEvent.Foregrounded)
        }
        publish()
    }

    private fun failLoad(fault: GymFault) {
        loadFault = fault
        publish()
    }

    // ── The tick ────────────────────────────────────────────────────────────────────────────────

    /**
     * One read of the clock. **It advances nothing.**
     *
     * Three things can be true at a boundary and they are asked in priority order: an AMRAP that has
     * run out of materialised rounds must grow before anything can report 完了 mid-cap (§A REPS edge
     * case 7); an open segment held for half an hour is a forgotten phone; and otherwise the machine
     * is asked whether a boundary has passed.
     *
     * [SessionEvent.Elapsed] is dispatched **once** per tick, never in a loop until it returns null —
     * `Rule.REPS_OVERRUN` is a deliberate self-loop and a loop would not terminate. Several boundaries
     * passing at once is what [SessionEvent.Foregrounded] is for.
     */
    fun onTick() {
        val tl = timeline ?: return
        val ck = clock ?: return
        if (overlay == Overlay.NONE) {
            val state = tl.stateAt(ck.elapsedMs(nowElapsed()))
            val segment = state.segment
            when {
                tl.needsAnotherRound(state.ordinal) ->
                    snapshot?.let { dispatch(SessionEvent.RoundsExhausted(it, lib)) }

                segment.open && !segment.closed && isStalled(state.elapsedInSegmentMs) ->
                    dispatch(SessionEvent.Stalled)

                else -> dispatch(SessionEvent.Elapsed)
            }
        }
        publish()
    }

    /** `ON_START`: reconcile what the clock walked past, then let [publish] re-arm the cues. */
    fun onForegrounded() {
        visible = true
        if (timeline == null) return
        // Dropping the key forces [publish] to recompute the segment's schedule, which is right when
        // the channel was torn down while we were away. When the service held the session nothing was
        // torn down, and recomputing would cancel a 3-2-1 that is already posted at the correct wall
        // time and re-post it a few milliseconds late — a cue degraded by tidying that buys nothing.
        if (!serviceHeld) armedKey = null
        dispatch(SessionEvent.Foregrounded)
        publish()
    }

    /**
     * `ON_STOP` — and, since Phase 3, the one place `03-player.md` §E.5's promise is kept.
     *
     * §E.5: *"When the health service lands in Phase 3, the only thing that changes in this spec is the
     * disarm matrix (§D.7)."* This is that call site. With no service the row is Phase 1's, unchanged:
     * the cues go quiet, the session does not, because the clock is monotonic and the timeline is
     * derived from it. With the service up the row disarms nothing, so the 3-2-1 and the 「休息」 fire
     * from a pocket, which is the only thing the notification, the permission and the Play Console
     * health declaration were ever bought for.
     *
     * The armed key survives with the channel for [disarmPlan]'s reason: the schedule that is posted is
     * the right one, and a session that keeps cueing must not re-arm the segment it never left.
     */
    fun onBackgrounded() {
        visible = false
        cues.handle(
            CueEvent.ON_STOP,
            CueState(phaseNow(), sessionComplete = true, serviceHolding = serviceHeld),
        )
        if (!serviceHeld) armedKey = null
    }

    fun release() = cues.release()

    /**
     * A preference changed while the session was running.
     *
     * 目安で自動的に進む is applied here, at run time, and deliberately never inside `compile` — a
     * preference in the compiler would make one routine compile two ways and `compiled_hash` would
     * report drift the first time a switch moved mid-session (`effectiveGate`'s own argument).
     */
    fun onPreferences(prefs: EffectiveGymPreferences) {
        lastPrefs = prefs
        autoAdvance = prefs.stored.autoAdvanceReps
        cues.arm(prefs)
    }

    /** Rebuilds the frame without touching the session — the paused screen's one-shot 60s refresh. */
    fun refresh() = publish()

    // ── SessionActions ──────────────────────────────────────────────────────────────────────────

    override fun onSkipForward() {
        val now = nowElapsed()
        if (debounced(lastSkipAt, now, SKIP_DEBOUNCE_MS)) return
        lastSkipAt = now
        // A ◁ armed on the station being left must not step back out of the one replacing it.
        backTaps.disarm()
        dispatch(SessionEvent.SkipForward)
    }

    override fun onBackTap() = dispatch(SessionEvent.BackTapped(backTaps.resolve(nowElapsed())))

    override fun onPause() = dispatch(SessionEvent.PauseRequested)

    override fun onResume() = dispatch(SessionEvent.ResumeRequested)

    override fun onDone(actualReps: Int?, adjusted: Boolean, ordinal: Int?) {
        val now = nowElapsed()
        if (debounced(lastDoneAt, now, DONE_DEBOUNCE_MS)) return
        lastDoneAt = now
        dispatch(SessionEvent.RepsCompleted(actualReps, adjusted, ordinal))
    }

    override fun onExtendRest() = dispatch(SessionEvent.ExtendRest)

    override fun onQuit() = dispatch(SessionEvent.QuitRequested)

    override fun onQuitSaveRecording() {
        if (quitSaving) return
        // A finish that already ran and failed is **retried**, not re-taken. Row 22 closed the segment
        // in progress and wrote it before `finishSession` failed, so re-dispatching it would close
        // whatever the frontier moved on to and add a second, zero-length result to the record. This
        // is the same write もう一度 offers one row below (`QuitSheet.SaveFailedLine`), reached from the
        // row that is now live again because [holdQuitSheetOpen] put the machine back in the sheet.
        if (quitFault != null && pendingFinishComplete != null) return onRetryFinish()
        // An unrecoverable session has no timeline to close a partial against, so there is no
        // transition to run: the rows on disk are already the whole of what happened, and finishing
        // incomplete is exactly what 記録する means for them.
        if (unrecoverable) return finishDirectly(complete = false)
        dispatch(SessionEvent.SavePartial)
    }

    override fun onQuitDiscard() {
        if (quitSaving) return
        if (unrecoverable) return discardDirectly()
        dispatch(SessionEvent.DiscardConfirmed)
    }

    override fun onQuitDismissed() {
        if (quitSaving) return
        quitFault = null
        dispatch(SessionEvent.QuitDismissed)
    }

    override fun onRate(rating: Rating?) {
        val current = completion ?: return
        // Optimistic, per §A COMPLETE edge case 3: the tap lands now and a failure says so, rather
        // than the row reverting under a user who has already looked away.
        completion = current.copy(rating = rating, ratingFault = null)
        publish()
        scope.launch {
            writes.withLock {
                val write = gym.repository.rateSession(sessionId, rating)
                if (write is GymWrite.Failed) {
                    completion = completion?.copy(ratingFault = write.fault)
                    publish()
                }
            }
        }
    }

    override fun onCloseRecord() = dispatch(SessionEvent.RecordClosed)

    override fun onRepeat() = dispatch(SessionEvent.RecordRepeated)

    override fun onRetryFinish() {
        val complete = pendingFinishComplete ?: return
        if (quitSaving) return
        liveFault = null
        quitFault = null
        quitSaving = true
        publish()
        runWrites(listOf(SessionEffect.Finish(complete)))
    }

    override fun onRetryLoad() {
        val prefs = lastPrefs ?: return
        if (loadFault == null) return
        loadFault = null
        loaded = false
        publish()
        scope.launch { load(prefs) }
    }

    override fun onDismissFault() {
        liveFault = null
        quitFault = null
        publish()
    }

    /**
     * Back, for every state the player can be in — the player's single back path (§A).
     *
     * Back on the sheet is つづける and **never** a discard; back on 記録 pops the whole player stack;
     * back anywhere live opens the sheet, even in 支度 where nothing has happened yet, because one
     * back path for the whole player is the point.
     */
    fun onBack() {
        when {
            completion != null -> onCloseRecord()
            // A load that failed has no session to quit and no sheet to open: `onQuit()` dispatches,
            // `context()` is null with no timeline, and the dispatch returns before it decides
            // anything — so Back did nothing at all. On an immersive, chromeless screen whose fault
            // may carry no もう一度 at all (`DECISIONS.md` §Q6 gives `RoutineGone`, `SessionGone`,
            // `StoreFull` and `Rejected` a message and no action), that is a dead end with the system
            // bars hidden. It leaves the same way [unrecoverable] does, and decides as little: the row
            // is untouched and the resume prompt asks again on the next entry.
            loadFault != null -> leaveToHome()
            // Nothing here can be continued, so Back is not a discard and not a save — it is the
            // dismiss the resume prompt already has, and it **decides nothing**: the row survives and
            // the prompt asks again on the next entry (`00-plan.md` §2 row 8).
            unrecoverable -> leaveToHome()
            overlay == Overlay.QUIT_SHEET -> onQuitDismissed()
            else -> onQuit()
        }
    }

    // ── The machine ─────────────────────────────────────────────────────────────────────────────

    private fun context(): SessionContext? {
        val tl = timeline ?: return null
        val ck = clock ?: return null
        val now = nowElapsed()
        return SessionContext(
            timeline = tl,
            elapsedMs = ck.elapsedMs(now),
            nowWallMs = nowWall(),
            overlay = overlay,
            autoAdvance = autoAdvance,
            resultsWritten = resultsWritten,
            pausedForMs = ck.pausedForMs(now),
            pausedBeforeSheet = pausedBeforeSheet,
        )
    }

    private fun dispatch(event: SessionEvent) {
        val ctx = context() ?: return
        val transition = step(ctx, event) ?: return
        applyTransition(transition)
        publish()
    }

    /**
     * One row of §C.1, carried out.
     *
     * Clock and cue effects are applied **synchronously**, because the very next frame renders from
     * them; store effects are queued in order behind [writes]. That split is the reason a pause is
     * instant and a checkpoint is not, and it is safe in the one direction that matters: the clock the
     * checkpoint persists is read at the moment the write runs, so a slow disk cannot record a stale
     * anchor.
     */
    private fun applyTransition(transition: SessionTransition) {
        val now = nowElapsed()
        val wall = nowWall()
        val wasQuitSheet = overlay == Overlay.QUIT_SHEET

        timeline = transition.timeline
        overlay = transition.overlay
        // Rows 22 and 23 both hand back `Overlay.NONE`, and [step] only stamps `pausedBeforeSheet` on
        // a transition that *leaves* the sheet open — so taking the transition's value here would
        // forget, at the moment of the write, whether the sheet was opened over a pause. The sheet is
        // still on screen until the write answers, and if it fails it stays; the fact it remembers is
        // still true, so it is kept rather than reset. See [holdQuitSheetOpen].
        if (!(wasQuitSheet && closesSession(transition.effects))) {
            pausedBeforeSheet = transition.pausedBeforeSheet
        }
        when (transition.rule) {
            Rule.STALLED -> stalled = true
            Rule.RESUMED, Rule.RESUMED_WITH_PREPARE -> stalled = false
            else -> Unit
        }
        transition.prepareForMs?.let { resumePrepareTotalMs = it }
        transition.seekToMs?.let { target -> clock = clock?.seekTo(target, now) }
        (transition.destination as? Destination.Complete)?.let { completionReason = it.reason }

        val stored = mutableListOf<SessionEffect>()
        for (effect in transition.effects) {
            when (effect) {
                is SessionEffect.PauseClock -> clock = clock?.pause(now, wall)
                is SessionEffect.ResumeClock -> clock = clock?.resume(now)
                is SessionEffect.ResumeClockAfter ->
                    clock = clock?.resumeAfter(now, wall, effect.delayMs)
                is SessionEffect.AnchorClock -> clock = clock?.reanchor(now, wall)
                is SessionEffect.DisarmCues -> disarm(transition)
                is SessionEffect.Fire -> fire(effect.cue)
                // The row exists before the player does: navigation was blocked on the INSERT
                // (§A's start path), which is why this state cannot be reached with no session.
                is SessionEffect.InsertSession -> Unit
                is SessionEffect.DeleteResult,
                is SessionEffect.Write,
                is SessionEffect.Checkpoint,
                is SessionEffect.Finish,
                is SessionEffect.Discard,
                -> stored += effect
            }
        }

        // "Never navigate away on a failed save" (§A QUIT_SHEET) — so the sheet is held open by the
        // host's own flag rather than by the machine's overlay, which has already moved on.
        if (wasQuitSheet && closesSession(transition.effects)) quitSaving = true

        if (transition.destination is Destination.Home &&
            transition.effects.none { it is SessionEffect.Discard }
        ) {
            leaveToHome()
        }
        if (transition.destination is Destination.RoutineDetail) {
            leaveToHome()
            gym.go(GymRoute.RoutineDetail(gym.tierRoutineId(route.routineId)))
        }

        if (stored.isNotEmpty()) runWrites(stored)
    }

    /**
     * §E.1's persistence schedule, in the order the table gives it.
     *
     * **The one honest caveat, stated here because the recovery code must not pretend otherwise:**
     * §E.1 pairs every `session_result` with a clock checkpoint *in one transaction*, and the shipped
     * `GymRepository` exposes them as two suspend calls and therefore two transactions. What survives
     * the split is what recovery actually reads — `recordSegment` updates `last_write_elapsed` /
     * `last_write_wall` **inside its own transaction**, and those two columns are the whole of
     * `recoveredActiveMs`. The checkpoint adds only anchors that do not move on a segment close
     * (`started_at_elapsed`, `paused_*`) and `rounds_completed`, which [roundsCompletedOf] re-derives
     * from the replayed timeline. So a crash between the two loses nothing that is not recomputed.
     *
     * The invariant itself is unchanged and non-negotiable: **the database is authoritative up to the
     * last transition, nothing between transitions is recoverable, and no code here may guess at the
     * gap.**
     */
    private fun runWrites(effects: List<SessionEffect>) {
        val id = sessionId
        val reason = completionReason
        scope.launch {
            writes.withLock {
                for (effect in effects) {
                    when (effect) {
                        is SessionEffect.Write -> {
                            // Counted optimistically: the quit sheet's shape and §C.1 row 22's guard
                            // both read it, and a tap arriving between the write and its answer must
                            // not see one fewer result than the store already holds.
                            resultsWritten += 1
                            val write = gym.repository.recordSegment(id, effect.draft)
                            if (write is GymWrite.Failed) {
                                resultsWritten -= 1
                                liveFault = write.fault
                            }
                        }

                        // §C.4: "stepping back **deletes** the previous segment's result before
                        // re-seeking, or the record double-counts it." `recordSegment` being
                        // idempotent under `idx_result_session` covers only the case where the user
                        // redoes the segment; stepping back and then quitting — or skipping the
                        // segment instead of redoing it — leaves an attempt in the record that the
                        // user withdrew. Deleting a row that is not there is `Ok`, so the count only
                        // moves on a delete that could have removed something.
                        is SessionEffect.DeleteResult -> {
                            val write = gym.repository.deleteResult(id, effect.ordinal)
                            if (write is GymWrite.Failed) {
                                liveFault = write.fault
                            } else if (resultsWritten > 0) {
                                resultsWritten -= 1
                            }
                        }

                        is SessionEffect.Checkpoint -> {
                            val ck = clock ?: continue
                            val tl = timeline ?: continue
                            gym.repository.checkpoint(id, ck.persisted(), roundsCompletedOf(tl))
                        }

                        is SessionEffect.Finish -> {
                            pendingFinishComplete = effect.complete
                            when (val write = gym.repository.finishSession(id, effect.complete)) {
                                is GymWrite.Ok -> {
                                    // §E.4's obligation, discharged: `keepAwake` cannot exclude 記録
                                    // because it renders under `GymRoute.Session`, so the flag drops
                                    // only when the live session is nulled — here, at the finish.
                                    gym.onSessionClosed()
                                    gym.refreshResumable()
                                    completion = SessionCompleteState(
                                        outcome = write.value,
                                        reason = reason,
                                        rating = write.value.summary.rating,
                                        ratingFault = null,
                                    )
                                    quitSaving = false
                                    quitFault = null
                                }

                                is GymWrite.Failed -> {
                                    liveFault = write.fault
                                    holdQuitSheetOpen(write.fault)
                                }
                            }
                        }

                        is SessionEffect.Discard -> {
                            when (val write = gym.repository.discardSession(id)) {
                                is GymWrite.Ok -> {
                                    // The other half of §E.4's obligation: a discarded session is a
                                    // session that stopped, and the screen may sleep again.
                                    gym.onSessionClosed()
                                    gym.refreshResumable()
                                    quitSaving = false
                                    leaveToHome()
                                }

                                is GymWrite.Failed -> holdQuitSheetOpen(write.fault)
                            }
                        }

                        else -> Unit
                    }
                }
                publish()
            }
        }
    }

    /**
     * A quit-sheet write failed, so the sheet the user is looking at is still the truth — including
     * to the machine.
     *
     * **つづける is the one row of the sheet that must never be conditional (§A QUIT_SHEET), and it was
     * dead after every failed write.** Rows 22 and 23 hand back `Overlay.NONE` *before* the write is
     * attempted, because from the machine's point of view the session is over; §A's "never navigate
     * away on a failed save" then keeps the sheet on screen through [quitFault] alone. The two halves
     * disagreed: the sheet was drawn, and every row on it — つづける on row 21, 記録する on row 22,
     * 記録せずに終える on row 23 — opens with the same guard, `if (ctx.overlay != Overlay.QUIT_SHEET)
     * return null`, so all three did nothing at all. The escape from a failure dialog silently doing
     * nothing is worse than the failure.
     *
     * So the machine is put back where the picture says it is. [pausedBeforeSheet] is untouched — it
     * was preserved across the closing transition for this moment (see [applyTransition]) — so row 21
     * still returns a sheet opened from ┃┃ to the pause and never restarts a timer the user cannot
     * see.
     *
     * *Rejected* — relaxing row 21's guard so `QuitDismissed` works from `Overlay.NONE`. That guard is
     * what makes a stray back press on a live segment a no-op instead of a phantom resume; the state
     * was wrong here, not the guard.
     */
    private fun holdQuitSheetOpen(fault: GymFault) {
        overlay = Overlay.QUIT_SHEET
        quitSaving = false
        quitFault = fault
    }

    /** 記録する / 捨てる on a session that cannot be replayed — no transition, the same two writes. */
    private fun finishDirectly(complete: Boolean) {
        quitSaving = true
        quitFault = null
        publish()
        runWrites(listOf(SessionEffect.Finish(complete)))
    }

    private fun discardDirectly() {
        quitSaving = true
        quitFault = null
        publish()
        runWrites(listOf(SessionEffect.Discard))
    }

    /**
     * The third call site of §E.4's obligation, and the navigation that goes with it.
     *
     * `selectTab(Train)` rather than a pop: §A COMPLETE pops the **entire** player stack to
     * `GYM.HOME`, so back can never re-enter a finished session, and a tab select is exactly that
     * rebase (`GymNav.selectTab`).
     */
    private fun leaveToHome() {
        gym.onSessionClosed()
        gym.selectTab(GymTab.Train)
    }

    // ── Cues ────────────────────────────────────────────────────────────────────────────────────

    private fun disarm(transition: SessionTransition) {
        val event = cueEventFor(transition.rule, transition.destination)
        val complete = transition.effects
            .filterIsInstance<SessionEffect.Finish>()
            .firstOrNull()?.complete ?: true
        val phase = if (transition.destination is Destination.Complete) Phase.COMPLETE else phaseNow()
        cues.handle(event, CueState(phase, sessionComplete = complete))
        // A skip re-arms on the segment it lands in; everything else has stopped, and either way the
        // key has to go or `publish` will believe the new segment is already armed.
        armedKey = null
    }

    private fun fire(cue: Cue) {
        val tl = timeline ?: return
        val ck = clock ?: return
        val ordinal = tl.stateAt(ck.elapsedMs(nowElapsed())).ordinal
        cues.fire(cue, cueSpeechFor(cue, tl, ordinal, lib))
    }

    /**
     * Arms the cue engine for the segment on screen — §D.1's "compute the schedule once on entering".
     *
     * Keyed on `(ordinal, effectiveMs)` rather than on the ordinal alone, so that ＋二十秒 re-arms:
     * twenty seconds moves the 3-2-1, and §D.1 is explicit that the schedule is *recomputed* after
     * `extend()` rather than a pending post being patched up.
     */
    private fun syncCues(tl: Timeline, ordinal: Int, elapsedInSegmentMs: Long) {
        val key = ordinal to tl.segments[ordinal].effectiveMs
        if (armedKey == key) return
        armedKey = key
        cues.enterSegment(cueSegmentFor(tl, ordinal, lib), elapsedInSegmentMs)
    }

    private fun phaseNow(): Phase {
        val tl = timeline ?: return Phase.PREPARE
        val ck = clock ?: return Phase.PREPARE
        return tl.stateAt(ck.elapsedMs(nowElapsed())).segment.phase
    }

    // ── The frame ───────────────────────────────────────────────────────────────────────────────

    private fun publish() {
        val complete = completion
        val tl = timeline
        val ck = clock
        screen = when {
            complete != null -> SessionScreen.Complete(complete)
            loadFault != null -> SessionScreen.Failed(loadFault!!)
            unrecoverable -> SessionScreen.Unrecoverable(
                SessionUnrecoverableState(
                    routineId = route.routineId,
                    routineName = routineName,
                    resultsWritten = resultsWritten,
                    saving = quitSaving,
                    fault = quitFault,
                ),
            )

            tl == null || ck == null -> SessionScreen.Loading
            else -> SessionScreen.Live(liveState(tl, ck))
        }
        ticking = (screen as? SessionScreen.Live)?.state
            ?.let { it.overlay == SessionOverlayUi.None && !it.finished && !quitSaving } == true
    }

    private fun liveState(tl: Timeline, ck: SessionClock): SessionUiState {
        val now = nowElapsed()
        val elapsed = ck.elapsedMs(now)
        val pendingResume = ck.resumePendingMs(now)
        val prepare = if (pendingResume > 0L) {
            PrepareUi(pendingResume, resumePrepareTotalMs, resumed = true)
        } else {
            compiledPrepare(tl, elapsed)
        }
        val state = sessionUiState(
            routineId = route.routineId,
            routineName = routineName,
            stationsPlanned = stationsPlanned,
            timeline = tl,
            elapsedMs = elapsed,
            lib = lib,
            overlay = overlayUi(ck, now),
            prepare = prepare,
            autoAdvance = autoAdvance,
            resultsWritten = resultsWritten,
            fault = liveFault,
        )
        // Armed from the published frame rather than from the transition, so that a resume, a seek and
        // a foreground regain all land the schedule at the right offset with no separate call sites.
        if (state.overlay == SessionOverlayUi.None && pendingResume == 0L && !state.finished) {
            syncCues(tl, state.ordinal, state.elapsedInSegmentMs)
        }
        return state
    }

    private fun overlayUi(ck: SessionClock, now: Long): SessionOverlayUi = when {
        quitSaving || quitFault != null -> SessionOverlayUi.Quit(quitSaving, quitFault)
        overlay == Overlay.QUIT_SHEET -> SessionOverlayUi.Quit(saving = false, fault = null)
        overlay == Overlay.PAUSED -> {
            val pausedFor = ck.pausedForMs(now)
            SessionOverlayUi.Paused(
                pausedForMs = pausedFor,
                resumeNeedsPrepare = pausedFor > LONG_PAUSE_MS,
                stalled = stalled,
            )
        }

        else -> SessionOverlayUi.None
    }
}

/**
 * The session player's window: portrait-locked, immersive, and delegating every pixel to [page].
 *
 * **The phone is on the floor and you are looking down at it from a plank.** That is the whole reason
 * for the orientation lock, and it is why this is `SCREEN_ORIENTATION_PORTRAIT` where `FocusScreen`
 * takes landscape — the rest of that effect is reused verbatim, including the
 * `OnWindowFocusChangeListener` re-hide, because `hide()` is silently reverted whenever the window
 * loses and regains focus and a status bar creeping back over a timer is exactly what immersive was
 * asked for.
 *
 * There is deliberately **no page composable here**. The host is a controller; the six player screens
 * and 記録 are drawn by whoever owns them, and the only thing this scaffold guarantees them is the
 * paper background every page in the feature renders on, the window state, and one [SessionScreen] per
 * frame.
 */
@Composable
fun SessionHost(
    gym: GymViewModel,
    route: GymRoute.Session,
    modifier: Modifier = Modifier,
    page: @Composable (SessionScreen, SessionActions) -> Unit,
) {
    val colors = LocalTempoColors.current
    val context = LocalContext.current
    val prefs by gym.prefs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    ImmersivePortrait()

    // `DECISIONS.md` §Q2: speech is off by default and auto-enabled for the session when touch
    // exploration is on — read here, at session start, and never written back to the preference.
    val touchExploration = remember(context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        manager?.isTouchExplorationEnabled == true
    }
    val effectivePrefs = EffectiveGymPreferences(prefs, touchExploration)
    val latestPrefs = rememberUpdatedState(effectivePrefs)

    val controller = remember(gym, route) {
        var engine: CueEngine? = null
        // The engine cannot report a voice until something asks it to bind, so the answer arrives
        // late — and when it does, the channels have to be re-armed or a TalkBack user gets silence.
        // Read through `latestPrefs` because that answer can arrive minutes after this ran.
        val created = CueEngine(context) { engine?.arm(latestPrefs.value) }
        engine = created
        SessionController(
            gym = gym,
            route = route,
            cues = created,
            scope = scope,
            lib = ExerciseCatalog.all().associateBy { it.id },
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    LaunchedEffect(controller) { controller.load(effectivePrefs) }

    // A switch flipped in 設定 mid-session must reach this session: the channels re-arm and
    // 目安で自動的に進む takes effect on the next segment, which is what `effectiveGate` is for.
    LaunchedEffect(controller, effectivePrefs) { controller.onPreferences(effectivePrefs) }

    // Whether the health foreground service actually got started for this session — `TrainingHold`
    // reports the platform's answer, not the mount's intention, and it is the only input that makes
    // §E.5's disarm change and the loop gate below correct on a device that refused the service.
    //
    // `collectAsState`, deliberately not `collectAsStateWithLifecycle`: the lifecycle-aware collector
    // stops at `ON_STOP`, which is the exact moment both readers need this value.
    val serviceHeld by TrainingHold.held.collectAsState()
    LaunchedEffect(controller, serviceHeld) { controller.onServiceHold(serviceHeld) }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // §E.5's two rows, and still the only two — what the service changed is what
                // `onBackgrounded` does with the second one, not that there is a third.
                Lifecycle.Event.ON_START -> controller.onForegrounded()
                Lifecycle.Event.ON_STOP -> controller.onBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /*
     * The render loop — §B.5's `while (isActive) { read; delay(50) }`, gated by one predicate.
     *
     * *Rejected* — `withFrameNanos`. It stops with the frame clock when the window is not visible,
     * which is half of what is wanted, but it also runs at the display's refresh rate: two to three
     * times the reads for no more information, on the one screen in the app that is expected to stay
     * lit for twenty minutes. `delay` is also the honest shape here, because the loop's cadence is a
     * *sampling rate* and not an animation — the ring is a value redraw, never an `Animatable`.
     *
     * `delay` schedules on a timebase that stops in deep sleep, and that is harmless precisely because
     * nothing accumulates: every pass re-reads `elapsedRealtime`, so a loop that was asleep for four
     * minutes comes back and renders the truth on its first pass.
     *
     * *Rejected* — keeping `repeatOnLifecycle(STARTED)` around this, which is what Phase 1 had. It is a
     * second, invisible gate saying the opposite of [SessionController.shouldTick]'s `serviceHeld`
     * term: a service-backed session would still have had its loop stopped at `ON_STOP`, so nothing
     * would advance a segment or arm a cue from a pocket and the whole service would deliver silence.
     * Visibility is now one term of one predicate, fed by the same `ON_START`/`ON_STOP` observer above,
     * and `collectLatest` over it stops the loop while paused, while the quit sheet is open, once the
     * session is finished, and while a backgrounded session has nothing holding the process open.
     */
    LaunchedEffect(controller) {
        snapshotFlow { controller.shouldTick }.collectLatest { run ->
            if (!run) return@collectLatest
            while (controller.shouldTick) {
                controller.onTick()
                delay(TICK_MS)
            }
        }
    }

    // 休止 has one thing that changes without a tick: 続ける gains 三秒の支度から once the pause passes a
    // minute. One post rather than a loop — the alternative is twenty wake-ups a second to observe a
    // threshold that is crossed exactly once.
    val paused = (controller.screen as? SessionScreen.Live)?.state?.overlay as? SessionOverlayUi.Paused
    LaunchedEffect(paused?.resumeNeedsPrepare, paused != null) {
        val pausedFor = paused?.takeIf { !it.resumeNeedsPrepare }?.pausedForMs ?: return@LaunchedEffect
        delay(LONG_PAUSE_MS - pausedFor + 1)
        controller.refresh()
    }

    BackHandler(enabled = true) { controller.onBack() }

    Box(modifier.fillMaxSize().tempoBackground(colors)) {
        page(controller.screen, controller)
    }
}

/**
 * `FocusScreen.kt:86-113`, reused verbatim with one substitution: portrait instead of landscape.
 *
 * Everything else is Focus's, deliberately down to the comment about `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
 * — a deliberate swipe peeks the bars and they auto-hide — and to restoring `originalOrientation` on
 * dispose rather than forcing `UNSPECIFIED`, so that a device the user had locked in one orientation
 * before entering the gym goes back to it.
 */
@Composable
private fun ImmersivePortrait() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        fun hideBars() = controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        hideBars()

        // hide() is silently reverted whenever the window loses then regains focus — the shade, a
        // toast, a permission prompt, returning from another app. Re-hide on every focus regain.
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) hideBars()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
