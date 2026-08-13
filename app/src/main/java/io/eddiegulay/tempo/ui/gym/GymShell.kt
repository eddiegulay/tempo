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
import io.eddiegulay.tempo.gym.currentTab
import io.eddiegulay.tempo.gym.tabBarVisible
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
                        GymPage(frame.route)
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
 * Whatever page the top route names.
 *
 * Every branch is a placeholder today and each is honestly one: the page's own title in the header
 * idiom and **nothing under it**. Not an empty state, not 読み込み中, not a hand-written "coming soon"
 * — the first two would be claims about the user's data that this unit is in no position to make, and
 * the third would be prose no spec table contains. A heading with nothing beneath it claims nothing.
 *
 * The titles themselves are each page spec's own, so replacing a branch with the real page keeps the
 * word the user already saw: 型の中身 `04` §3, 種目をえらぶ `04` §3, これまで / 最高 / 移り変わり `04`
 * §4, 支度 `03` §A, 設定 / 安全のために `01` §B, and the three tab words from `GymTab`.
 */
@Composable
private fun GymPage(route: GymRoute) {
    val title = when (route) {
        GymRoute.Home -> "鍛錬"
        GymRoute.Library -> "型"
        GymRoute.Records -> "記録"
        is GymRoute.RoutineDetail -> "型の中身"
        is GymRoute.Builder -> if (route.editingId == null) "型を作る" else "型を編集"
        is GymRoute.StationPicker -> "種目をえらぶ"
        GymRoute.ExerciseIndex -> "種目"
        is GymRoute.ExerciseDetail -> "種目の中身"
        is GymRoute.Session -> "支度"
        is GymRoute.Record -> "記録の中身"
        is GymRoute.History -> "これまで"
        GymRoute.Bests -> "最高"
        GymRoute.Charts -> "移り変わり"
        GymRoute.Settings -> "設定"
        GymRoute.Safety -> "安全のために"
    }
    GymPagePlaceholder(title)
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
