package io.eddiegulay.tempo.ui.gym

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.LeaveReason
import io.eddiegulay.tempo.gym.TrainingCommands
import io.eddiegulay.tempo.gym.TrainingControl
import io.eddiegulay.tempo.gym.currentTab
import io.eddiegulay.tempo.gym.tabBarVisible
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.gym.session.CompletePage
import io.eddiegulay.tempo.ui.gym.session.LivePlayer
import io.eddiegulay.tempo.ui.gym.session.SessionActions
import io.eddiegulay.tempo.ui.gym.session.SessionHost
import io.eddiegulay.tempo.ui.gym.session.SessionScreen
import io.eddiegulay.tempo.ui.gym.session.SessionUnrecoverablePage
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * One route plus how deep it sits, which is the pair the route animation needs.
 *
 * `AnimatedContent` can only compare its target states, and two routes alone cannot tell a push from
 * a pop — 型の中身 → 型 is a pop, 型 → 型の中身 is a push, and the top route is a different value in
 * both directions with no ordering between them. Carrying the depth makes the direction a comparison
 * (`01-shell.md` §A.6).
 */
@Immutable
private data class GymFrame(val route: GymRoute, val depth: Int)

/**
 * 鍛錬's shell: the window, the back stack, the tab bar and the keep-awake flag.
 *
 * Renders in `TempoApp`'s **outer** layer beside Focus, never inside the dock layer — the gym takes
 * the whole window and the launcher's floating dock has no business over it.
 *
 * **The `BackHandler` is unconditionally enabled, and that is not defensive coding.** Three facts
 * make it load-bearing: `MainActivity` is `launchMode="singleTask"` and the task root, it is a
 * `category.HOME` activity, and `android:enableOnBackInvokedCallback="true"` is set application-wide.
 * Together they mean any moment where no handler is enabled hands the gesture to the system, which
 * runs a predictive-back preview of leaving the task and finishes the root activity — from a home app
 * that is a black screen or a bounce into whatever was there before. So the decision lives **inside
 * the lambda**, never in the `enabled` flag; `enabled = stack.size > 1` would be exactly one frame of
 * that hole at the gym's own root, which is the most-visited screen in the feature.
 *
 * *Rejected* — `PredictiveBackHandler`. It opts this surface into the system's "you are leaving"
 * peek, and a home app must never render one: there is nothing behind it to peek at. The shell's own
 * enter motion supplies the spatial feedback instead.
 *
 * **Nesting.** Compose registers back callbacks in composition order and the most recently added
 * enabled one wins. This handler is registered *above* the `AnimatedContent` that composes the page,
 * so any handler a page adds is later and takes priority — that is the mechanism by which the session
 * player's Back opens the quit sheet, and a dirty builder's Back opens the discard prompt. The only
 * rule pages must follow: never register `BackHandler(enabled = false)` expecting to reach the shell
 * "faster". It already is the fallback, and a disabled handler is free.
 *
 * @param onExit run after the gym has reset its own state; the launcher returns to Home.
 */
@Composable
fun GymShell(gym: GymViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val stack by gym.stack.collectAsStateWithLifecycle()
    val session by gym.activeSession.collectAsStateWithLifecycle()
    val prefs by gym.prefs.collectAsStateWithLifecycle()

    BackHandler(enabled = true) {
        if (!gym.onBack()) {
            // Both doors out run the same reset, and this is the Back one. Ordering matters: the gym
            // clears its own transient state *before* the launcher goes Home, so re-entry lands on a
            // clean 鍛錬 rather than on whatever was open when it was left (§A.5, §A.9).
            gym.onLeaveShell(LeaveReason.Back)
            onExit()
        }
    }

    KeepScreenOn(enabled = keepAwake(session, prefs.keepScreenOn, stack.last()))

    val slide = with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(modifier.fillMaxSize().gymEntrance()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState = GymFrame(stack.last(), stack.size),
                    transitionSpec = {
                        // Every row of §A.6's table, decided in one pure place so the durations are
                        // assertable; this end only turns the answer into Compose specs.
                        val m = routeMotion(initialState.depth, targetState.depth, targetState.route)
                        val enter = fadeIn(
                            tween(m.fadeInMillis, delayMillis = m.fadeInDelayMillis, easing = LinearOutSlowInEasing),
                        ).let { fade ->
                            val cue = m.scale
                            if (cue == null) fade else fade + scaleIn(
                                initialScale = cue.initialScale,
                                animationSpec = tween(
                                    cue.durationMillis,
                                    delayMillis = cue.delayMillis,
                                    easing = LinearOutSlowInEasing,
                                ),
                            )
                        }
                        val exitEase = when (m.fadeOutEase) {
                            GymEase.SettleIn -> LinearOutSlowInEasing
                            GymEase.Snap -> FastOutLinearInEasing
                        }
                        enter togetherWith fadeOut(tween(m.fadeOutMillis, easing = exitEase))
                    },
                    label = "gym-route",
                ) { frame ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            // An immersive route takes the whole window and manages its own insets;
                            // everything else gets the status bar cleared here, and the navigation bar
                            // from the tab bar, so the bar's fill can bleed under the gesture area
                            // while its labels stay clear (§A.7).
                            .then(if (frame.route.immersive) Modifier else Modifier.statusBarsPadding()),
                    ) {
                        GymPage(frame.route, gym)
                    }
                }
            }

            AnimatedVisibility(
                visible = tabBarVisible(stack),
                enter = fadeIn(tween(160, easing = LinearOutSlowInEasing)) +
                    slideInVertically(tween(160, easing = LinearOutSlowInEasing)) { slide },
                exit = fadeOut(tween(160, easing = FastOutLinearInEasing)) +
                    slideOutVertically(tween(160, easing = FastOutLinearInEasing)) { slide },
            ) {
                GymTabBar(current = currentTab(stack), onSelect = gym::selectTab)
            }
        }
    }
}

/**
 * `FLAG_KEEP_SCREEN_ON` for as long as a workout is running — released on **`ON_PAUSE`**, not on
 * disposal.
 *
 * The gym owns this rather than `TempoApp`, overruling design §8.2: whether the screen should stay
 * awake depends on the 画面を消さない preference *and* on whether a session is live, and `TempoApp`
 * can read neither without constructing `GymViewModel` on every launcher frame — which destroys the
 * lazy-open guarantee the whole feature is built on (`00-plan.md` §2 row 1).
 *
 * **Two gates, and they close different holes.** [keepAwake] decides *whether the claim is true at
 * all* — a live session, the preference, and the player actually on top, which is what excludes
 * 型の中身 and the resume prompt structurally (§A.7). This effect decides *when a true claim stops
 * being true*, which is the moment the window stops being visible. Neither substitutes for the other:
 * without the route gate the flag survives a HOME press and a tap into 型の中身 with the session still
 * live, and without `ON_PAUSE` it survives the window itself.
 *
 * `GYM.SESSION.COMPLETE` is the one §A.7 exclusion no gate here can make, because 記録 is a phase of
 * the player and renders under `GymRoute.Session`. It stays an obligation on the player:
 * `onSessionClosed()` at finish, quit or discard nulls the session and drops the flag. See [keepAwake].
 *
 * Releasing on `ON_PAUSE` is the correct fix for the leak class commit `1f49dfc` addressed. That bug
 * was a *disposal-scoped* release: pressing HOME while Tempo is not the default launcher backgrounds
 * the window without unmounting anything, so `onDispose` never runs and the flag outlives the screen
 * it belonged to. `ON_PAUSE` is the moment the window stops being visible, which is exactly the
 * moment the claim stops being true.
 *
 * `FLAG_KEEP_SCREEN_ON` only — never a `SCREEN_BRIGHT_WAKE_LOCK`, which survives the window and needs
 * a permission this app does not hold. `MainActivity` declares `configChanges` for orientation,
 * screenSize, density and uiMode, so the Activity is not recreated by a rotation and this does not
 * thrash.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(enabled, lifecycleOwner) {
        val window = (view.context as? Activity)?.window
        fun apply(on: Boolean) = if (on) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        apply(enabled)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> apply(false)
                Lifecycle.Event.ON_RESUME -> apply(enabled)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            apply(false)
        }
    }
}

/**
 * The shell's arrival: 280ms fade and a settle from 0.98, after a 40ms beat.
 *
 * One step gentler than the launcher's own 0.97 page transition, deliberately — a bigger move should
 * feel calmer, not sharper (`01-shell.md` §A.6). It runs here rather than as an `AnimatedContent`
 * around `TempoApp`'s outer layers because wrapping those would also animate Focus, which enters
 * instantly today and is not this unit's to move.
 *
 * There is no matching exit. The outer layer swaps instantly, exactly as Focus's does, so leaving is
 * the launcher entering on its own spec — which is the direction the spec's table asks for anyway
 * ("leaving is faster than arriving, as everywhere in this app").
 *
 * With `ANIMATOR_DURATION_SCALE = 0` the `Animatable` completes on its first frame, because Compose
 * reads the system scale through the recomposer's `MotionDurationScale`. The shell is then correct
 * immediately, which is also how a UI test will see it.
 */
@Composable
private fun Modifier.gymEntrance(): Modifier {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(280, delayMillis = 40, easing = LinearOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = entrance.value
        val settle = 0.98f + 0.02f * entrance.value
        scaleX = settle
        scaleY = settle
    }
}

/**
 * Which routes have no page yet, and what heading stands in for one — `null` for the routes that do.
 *
 * A pure function rather than a `when` buried in [GymPage] for one reason: **"is this route wired to a
 * real page?" is the question this phase kept getting wrong**, and it was unanswerable without running
 * Compose. Three finished pages shipped with zero call sites because the placeholder branch was still
 * catching their routes, and nothing in the build or the tests could see it. Here it is a value a JVM
 * test asserts on (`GymShellTest`), so the next page to land cannot quietly fail to be reachable.
 *
 * The stand-ins are honestly placeholders: the page's own title in the header idiom and **nothing
 * under it**. Not an empty state, not 読み込み中, not a hand-written "coming soon" — the first two
 * would be claims about the user's data the shell is in no position to make, and the third would be
 * prose no spec table contains. A heading with nothing beneath it claims nothing.
 *
 * **Phase 3 emptied the list.** Every route in [GymRoute] now reaches a real page: the five record
 * routes — 記録, 記録の中身, これまで, 最高, 移り変わり — were the last five stand-ins, and they moved out
 * in the same edit that added their arms to [GymPage], which is the discipline this function exists to
 * enforce. `GymShellTest` fails either half on its own.
 *
 * **It is kept, returning `null` everywhere, rather than deleted.** An empty classifier looks like dead
 * code and is the opposite: it is the thing that makes Phase 4's first new route a *compile error*
 * instead of a page nobody can reach. Five pages in this feature have shipped fully written and
 * unreachable, every one of them because a route fell into a branch that answered before [GymPage]
 * ever looked at it. The cost of the empty `when` is one line per route; the cost of removing it is
 * paid the next time somebody adds one.
 *
 * Exhaustive over [GymRoute] with no `else`, deliberately: a route added in a later phase must be
 * classified here or the build stops, rather than defaulting into whichever branch was cheapest. A
 * new route that belongs in this list gets its own arm back, with the title its own spec gives it —
 * never an empty state, never 読み込み中, never hand-written prose. A heading with nothing beneath it
 * claims nothing.
 */
internal fun gymPagePlaceholderTitle(route: GymRoute): String? = when (route) {
    GymRoute.Home,
    GymRoute.Library,
    is GymRoute.RoutineDetail,
    is GymRoute.Session,
    is GymRoute.Builder,
    is GymRoute.StationPicker,
    GymRoute.ExerciseIndex,
    is GymRoute.ExerciseDetail,
    GymRoute.Settings,
    GymRoute.Safety,
    GymRoute.Records,
    is GymRoute.Record,
    is GymRoute.History,
    GymRoute.Bests,
    GymRoute.Charts,
    -> null
}

/**
 * Whatever page the top route names.
 *
 * [gymPagePlaceholderTitle] decides *whether* there is a page; this only says which composable it is.
 * The placeholder is taken first and returns, so a route can never both draw its page and fall through
 * to a heading — the fall-through is exactly how `GymHomeScreen`, `LibraryIndexScreen` and
 * `LibraryDetailScreen` came to be written, compiling and unreachable, and then how the **entire
 * session player** did: `SessionHost`, `LivePlayer`, `PreparePage` and `CompletePage` all shipped with
 * zero call sites while `is GymRoute.Session` still resolved to the 支度 placeholder, so 始める landed
 * on a heading and a hairline. `GymShellTest` now walks the tree for orphaned pages so a fourth one
 * cannot be forgotten.
 *
 * Every `SessionScreen` gets an arm, and none of them is a `->` into nothing: an unhandled case is a
 * blank screen in the middle of a workout, which is the one failure this player must not have.
 * `SessionUnrecoverablePage` was written *in this file* for exactly that reason — it was the one player
 * screen nobody had — and it now lives in `ui/gym/session/` beside the quit sheet whose rows, window
 * and words it shares. Nothing about it changed in the move.
 *
 * [gym] is passed down rather than resolved per page with `viewModel()`: the shell already holds the
 * one instance keyed to `MainActivity`'s store, and a page reaching for its own would be a second
 * lookup with a second chance to disagree about which gym it is talking to.
 *
 * **Two of the things composed here are not pages.** `TrainingServiceMount` and `TrainingConsentMount`
 * draw nothing at all; they are lifetimes. Their placement inside the player's `when` rather than at
 * the shell root is load-bearing and is argued at each arm — a mount is a claim about *how long*
 * something lives, so where it is mounted is the whole of what it says.
 */
@Composable
private fun GymPage(route: GymRoute, gym: GymViewModel) {
    val placeholder = gymPagePlaceholderTitle(route)
    if (placeholder != null) {
        GymPagePlaceholder(placeholder)
        return
    }
    when (route) {
        GymRoute.Home -> GymHomeScreen(gym)
        GymRoute.Library -> LibraryIndexScreen(gym)
        is GymRoute.RoutineDetail -> LibraryDetailScreen(gym, route.routineId)

        // Phase 2's six. Each argument is the route's own — the builder needs to know whether it is
        // 作る or 編集 (`editingId`), and the picker whether it is editing station three or adding one
        // (`index`, null for 加える) — so the route is destructured here rather than handed down whole:
        // a page that read the stack would be a page that had to agree with the shell about which frame
        // it was drawing.
        is GymRoute.Builder -> BuilderScreen(gym, route.editingId)
        is GymRoute.StationPicker -> StationPickerScreen(gym, route.index)
        GymRoute.ExerciseIndex -> ExerciseIndexScreen(gym)
        is GymRoute.ExerciseDetail -> ExerciseDetailScreen(gym, route.exerciseId)
        GymRoute.Settings -> GymSettingsScreen(gym)
        GymRoute.Safety -> GymSafetyScreen(gym)

        // Phase 3's five. `Record` and `History` are destructured for the same reason Phase 2's were:
        // 記録の中身 is *which* session, and これまで is optionally one routine's attempts anchored at one
        // month, and a page that read the stack for those would have to agree with the shell about
        // which frame it was drawing. `Records`, `Bests` and `Charts` carry nothing — they are the tab
        // and its two depth-2 pages.
        GymRoute.Records -> RecordsIndexScreen(gym)
        is GymRoute.Record -> SessionDetailScreen(gym, route.sessionKey)
        is GymRoute.History -> RecordsHistoryScreen(gym, route.routineId, route.anchorMonth)
        GymRoute.Bests -> RecordsPrScreen(gym)
        GymRoute.Charts -> RecordsChartsScreen(gym)

        is GymRoute.Session -> SessionHost(gym, route) { screen, actions ->
            when (screen) {
                // §A's prompt rule, applied to the player itself: **no flash and no spinner**. The
                // reconstruction is a single indexed read and is over in a frame or two, so a 読み込み中
                // here would be a word the user sees only when the phone is struggling — and it would
                // be the first thing 始める showed them. Nothing is drawn but the paper `SessionHost`
                // already laid down.
                SessionScreen.Loading -> Unit

                // The health service is mounted **here, on the `Live` arm**, and the placement is the
                // whole design (`03-player.md` §E.5). `TrainingServiceMount` starts the service when it
                // enters the composition and stops it when it leaves, so scoping it to this arm makes
                // the three endings the service owes — finish, quit and discard — one fact rather than
                // three call sites: every one of them takes the screen off `Live`.
                //
                // *Rejected* — mounting it at the shell root, or in `SessionHost`'s scaffold. Both
                // outlive the session: the root would hold a workout notification over 記録 and over the
                // whole of 型 afterwards, and the scaffold survives the transition to `Complete`. A
                // foreground notification that is still up after the user finished is worse than none.
                is SessionScreen.Live -> {
                    TrainingServiceMount(trainingNotice(screen.state))
                    TrainingCommandRelay(actions)
                    LivePlayer(screen.state, actions)
                }

                // 記録 is the one moment `TrainingConsent` names as decent to ask on — see
                // `TRAINING_CONSENT_DELAY_MS`. It is mounted beside the page rather than inside it
                // because it draws nothing and belongs to the *service*, not to the record: the
                // historical `GYM.RECORDS.SESSION_DETAIL` renders the same `RecordSummary` and must
                // never ask, and it gets that for free by simply not being this arm.
                is SessionScreen.Complete -> {
                    TrainingConsentMount()
                    CompletePage(screen.state, actions)
                }

                is SessionScreen.Unrecoverable -> SessionUnrecoverablePage(screen.state, actions)
                // The read failed, so this is the `Loadable` doctrine's third case and it renders
                // through the one place a fault becomes words (`DECISIONS.md` §Q6). もう一度 re-reads
                // and rebuilds; the retry is idempotent because reconstruction writes nothing.
                is SessionScreen.Failed -> FaultPanel(screen.fault, onRecover = actions::onRetryLoad)
            }
        }
    }
}

/**
 * The notification's ┃┃ and 続ける, turned back into [SessionActions] calls.
 *
 * Without this the two buttons on the foreground notification are drawn, tappable, and inert:
 * `TrainingService` publishes into [TrainingCommands] from a fresh `onStartCommand` and has no way to
 * reach the player, because the two ends genuinely never meet — one is a framework callback, the other
 * a composable in an Activity that may not even be resumed.
 *
 * Collected on the **`Live`** arm only, and keyed on [actions], so the subscription exists exactly as
 * long as there is a session to command. [TrainingCommands] has no replay for the matching reason: a
 * 続ける tapped on a lock screen at 07:00 must not fire into a session started at 19:00, and an emit
 * with no subscriber is dropped rather than queued.
 *
 * Every call is safe at any moment — `SessionActions`' contract is that the machine returns null for
 * an event the current phase does not accept and that null is a no-op, never an error — so this needs
 * no view of the state to decide whether the button it is relaying was applicable.
 */
@Composable
private fun TrainingCommandRelay(actions: SessionActions) {
    LaunchedEffect(actions) {
        TrainingCommands.events.collect { control ->
            when (control) {
                TrainingControl.PAUSE -> actions.onPause()
                TrainingControl.RESUME -> actions.onResume()
            }
        }
    }
}

/** The page header idiom of `CalendarScreen`, with the body its owner has yet to write. */
@Composable
private fun GymPagePlaceholder(title: String) {
    val c = LocalTempoColors.current
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
            )
        }
        // The full-width hairline every gym page carries under its header.
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}
