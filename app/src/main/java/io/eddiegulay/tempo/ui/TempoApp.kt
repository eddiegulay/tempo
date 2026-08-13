package io.eddiegulay.tempo.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.TempoTheme
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.GymViewModelFactory
import io.eddiegulay.tempo.ui.gym.GymShell
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.stringsFor
import io.eddiegulay.tempo.ui.theme.InkPress
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.PaperColors
import io.eddiegulay.tempo.ui.theme.SumiColors

/**
 * The navigable layers of the launcher. Filter is the hidden-apps page, reached from Search; Focus
 * is the full-screen landscape clock/Pomodoro, reached from the Home clock's mode chooser; Calendar
 * is the agenda, reached only by tapping Home's top-right cluster, with EventCompose beneath it.
 *
 * [Gym] is the other mode, and it is one value on purpose: 鍛錬 keeps its own back stack, so where
 * you are *inside* it is not the launcher's business — exactly as [Focus] does not encode "clock vs
 * pomodoro". It buys the gym a whole shell for one enum entry.
 */
enum class Screen { Home, Search, Notifications, Filter, Focus, Calendar, EventCompose, Gym }

/**
 * Which world owns the window, as distinct from which page is on screen.
 *
 * [Focus] and [Gym] each take the whole window and neither belongs under the floating dock, so the
 * layer is chosen before the dock layer is composed at all. Deriving it from [Screen] rather than
 * storing it keeps one source of truth: there is no way for the layer and the screen to disagree.
 */
private enum class Layer { Onboarding, Launcher, Focus, Gym }

/**
 * Root of the Tempo launcher. State (screen, theme, search, default-home status) lives in
 * [LauncherViewModel]; this composable just reflects it and routes user intent back.
 *
 * @param onRequestDefault asks the system to make Tempo the default home app (wired in MainActivity).
 */
@Composable
fun TempoApp(
    viewModel: LauncherViewModel,
    onRequestDefault: () -> Unit,
    showSeal: Boolean = true,
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val lang by viewModel.lang.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val pendingBlock by viewModel.pendingBlock.collectAsStateWithLifecycle()
    val lockedTap by viewModel.lockedTap.collectAsStateWithLifecycle()
    val pendingMode by viewModel.pendingMode.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()

    val isDark = theme == TempoTheme.Sumi
    val colors = if (isDark) SumiColors else PaperColors

    // Home's corner shows the next event, so calendar access has to be known before the Calendar page
    // is ever opened — and it can be revoked in Settings while the launcher is alive.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCalendarAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the system bar icons legible against whichever theme is active.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    // Keep the screen awake only while Focus is on screen. Driven by [screen] rather than from inside
    // FocusScreen so the wake flag is cleared on every other screen regardless of whether FocusScreen
    // got to dispose — e.g. when Tempo isn't the default launcher, pressing HOME backgrounds the app
    // without unmounting Focus, so a disposal-scoped release would leak the wake-lock process-wide.
    DisposableEffect(screen == Screen.Focus) {
        val window = (view.context as? Activity)?.window
        if (screen == Screen.Focus) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Back never leaves the launcher. Filter is a sub-page of Search and EventCompose a sub-page of
    // Calendar, so each returns to its parent; any other sub-screen returns home; on home it's a no-op.
    BackHandler(enabled = screen == Screen.Filter) { viewModel.goSearch() }
    BackHandler(enabled = screen == Screen.EventCompose) { viewModel.cancelCompose() }
    BackHandler(
        enabled = screen != Screen.Home &&
            screen != Screen.Filter &&
            screen != Screen.EventCompose &&
            // The gym handles its own Back at every depth, and must: this generic handler would pop
            // the whole shell on the first press from anywhere inside it.
            screen != Screen.Gym,
    ) { viewModel.goHome() }
    BackHandler(enabled = screen == Screen.Home) { /* stay on home */ }

    // Which world owns the window. Seeded synchronously from DataStore, so this is already correct on
    // the first frame — returning users land straight on Home with no flash of the onboarding gate.
    val layer = when {
        !onboardingComplete -> Layer.Onboarding
        screen == Screen.Focus -> Layer.Focus
        screen == Screen.Gym -> Layer.Gym
        else -> Layer.Launcher
    }

    // Copy and colour are provided together, and both are `staticCompositionLocalOf` — neither tracks
    // reads, so changing either recomposes this whole content lambda rather than invalidating call
    // sites one at a time. That is what makes a language switch land everywhere in one frame,
    // including inside 鍛錬, which composes within this provider and so inherits both for free.
    CompositionLocalProvider(
        LocalTempoColors provides colors,
        LocalStrings provides stringsFor(lang),
        // Tempo sets no Material 3 `ColorScheme` — the palette above *is* the theme — so Material's
        // default indication had no house colour to fall back on and painted every un-styled
        // `clickable` an M3 grey rectangle. Replacing it here fixes the colour for all 79 press sites
        // at once, migrated or not; `Modifier.pressable` then fixes the *bounds* one call site at a
        // time. The instance is a constant because it reads [LocalTempoColors] at draw time, so it
        // serves Paper and Sumi alike and a theme switch invalidates nothing.
        LocalIndication provides InkPress.Default,
    ) {
        Box(Modifier.fillMaxSize().tempoBackground(colors)) {
            when (layer) {
                Layer.Onboarding -> OnboardingScreen(
                    isDefaultLauncher = isDefaultLauncher,
                    onRequestDefault = onRequestDefault,
                    onComplete = viewModel::completeOnboarding,
                    lang = lang,
                    onChooseLanguage = viewModel::setLanguage,
                    modifier = Modifier.systemBarsPadding(),
                )

                // Focus is its own immersive world: full-bleed, no dock, no system-bar insets — the
                // screen itself locks orientation and hides the bars.
                Layer.Focus -> FocusScreen(Modifier.fillMaxSize())

                // 鍛錬 is the other one, and it is resolved *inside* this branch on purpose: a user who
                // never trains never touches GymViewModelFactory, so no gym database is ever opened.
                // LocalViewModelStoreOwner here is MainActivity, so this is the same instance the
                // Activity's own `by viewModels` delegate returns, and it survives the shell
                // unmounting — which is what lets a live workout outlive a HOME press.
                Layer.Gym -> GymShell(
                    gym = viewModel<GymViewModel>(factory = GymViewModelFactory(LocalContext.current)),
                    // The shell has already run onLeaveShell(Back) by the time this fires.
                    onExit = viewModel::goHome,
                    modifier = Modifier.fillMaxSize(),
                )

                Layer.Launcher -> Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    // Content fills the layer; the floating dock overlays it, so reserve room at the
                    // bottom for the pill plus its indicator. Screens cross-fade with a gentle
                    // scale-settle so a page eases in rather than snapping into place.
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            val enter = fadeIn(tween(260, delayMillis = 40, easing = LinearOutSlowInEasing)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(300, delayMillis = 40, easing = LinearOutSlowInEasing))
                            val exit = fadeOut(tween(80, easing = FastOutLinearInEasing))
                            enter togetherWith exit
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "screen",
                    ) { target ->
                        Box(Modifier.fillMaxSize()) {
                            when (target) {
                                Screen.Home -> HomeScreen(
                                    showSeal = showSeal,
                                    events = calendarEvents,
                                    onChooseMode = viewModel::requestMode,
                                    onOpenCalendar = viewModel::goCalendar,
                                )
                                Screen.Search -> SearchScreen(
                                    viewModel = viewModel,
                                    isDark = isDark,
                                    onToggleTheme = viewModel::toggleTheme,
                                    onOpenFilter = viewModel::goFilter,
                                )
                                Screen.Notifications -> NotificationsScreen(viewModel = viewModel)
                                Screen.Filter -> FilterScreen(viewModel = viewModel)
                                Screen.Calendar -> CalendarScreen(viewModel = viewModel)
                                Screen.EventCompose -> EventComposeScreen(viewModel = viewModel)
                                // Focus renders full-screen in the outer branch; never inside the dock layer.
                                Screen.Focus -> Unit
                                // The gym renders full-screen in the outer branch, for the same reason
                                // Focus does: it owns the whole window and the dock has no business
                                // over it.
                                Screen.Gym -> Unit
                            }
                        }
                    }
                    // The composer is a committed task: leaving it half-written should be a deliberate
                    // Back or やめる, not a stray tap on a dock tab.
                    if (screen != Screen.EventCompose) {
                        Dock(
                            current = screen,
                            isDefaultLauncher = isDefaultLauncher,
                            onHome = viewModel::goHome,
                            onSearch = viewModel::goSearch,
                            onNotifications = viewModel::goNotifications,
                            onGym = viewModel::goGym,
                            onRequestDefault = onRequestDefault,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            // Over a sub-screen the dock becomes frosted "wet paper"; over Home it stays
                            // a faint pill on the wallpaper.
                            frosted = screen != Screen.Home,
                        )
                    }
                }
            }

            // Deliberate gate into a mode — overlays Home after a clock long-press.
            if (pendingMode) {
                ModeDialog(
                    onChoose = viewModel::confirmMode,
                    onDismiss = viewModel::cancelMode,
                )
            }

            // Commitment gate for hiding an app — overlays whichever screen requested the block.
            pendingBlock?.let { app ->
                BlockConfirmDialog(
                    appLabel = app.label,
                    onConfirm = viewModel::confirmBlock,
                    onDismiss = viewModel::cancelBlock,
                )
            }

            // Tapping a still-locked app surfaces a live countdown to its unlock moment.
            lockedTap?.let { app ->
                val unlockAt = viewModel.unlockAt(app.packageName)
                if (unlockAt != null) {
                    BlockedInfoDialog(
                        appLabel = app.label,
                        unlockAt = unlockAt,
                        nowProvider = viewModel::blockadeNow,
                        onDismiss = viewModel::dismissLocked,
                    )
                }
            }
        }
    }
}
