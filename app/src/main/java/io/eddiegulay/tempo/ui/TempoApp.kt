package io.eddiegulay.tempo.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.TempoTheme
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.PaperColors
import io.eddiegulay.tempo.ui.theme.SumiColors

/** The navigable layers of the launcher. Filter is the hidden-apps page, reached from Search. */
enum class Screen { Home, Search, Notifications, Filter }

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
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val pendingBlock by viewModel.pendingBlock.collectAsStateWithLifecycle()
    val lockedTap by viewModel.lockedTap.collectAsStateWithLifecycle()

    val isDark = theme == TempoTheme.Sumi
    val colors = if (isDark) SumiColors else PaperColors

    // All-files access can be toggled in Settings while we're away; re-check on resume and re-merge
    // the durable blockade ledger so a freshly-granted permission immediately backs up existing blocks.
    val context = LocalContext.current
    var storageGranted by remember { mutableStateOf(viewModel.hasStorageAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageGranted = viewModel.hasStorageAccess()
        viewModel.reconcileBlockade()
    }

    // Android 10 grants shared-storage access through a runtime permission rather than a Settings
    // screen, so it returns a result inline. Android 11+ goes through the All-files-access settings
    // page instead and is picked up by the ON_RESUME re-check above.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        storageGranted = granted
        if (granted) viewModel.reconcileBlockade()
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

    // Back never leaves the launcher. Filter is a sub-page of Search, so it returns there; any other
    // sub-screen returns home; on home it's a no-op.
    BackHandler(enabled = screen == Screen.Filter) { viewModel.goSearch() }
    BackHandler(enabled = screen != Screen.Home && screen != Screen.Filter) { viewModel.goHome() }
    BackHandler(enabled = screen == Screen.Home) { /* stay on home */ }

    CompositionLocalProvider(LocalTempoColors provides colors) {
        Box(Modifier.fillMaxSize().tempoBackground(colors)) {
            // Seeded synchronously from DataStore, so this is already correct on the first frame —
            // returning users land straight on Home with no flash of the onboarding gate.
            if (!onboardingComplete) {
                OnboardingScreen(
                    isDefaultLauncher = isDefaultLauncher,
                    onRequestDefault = onRequestDefault,
                    onComplete = viewModel::completeOnboarding,
                    modifier = Modifier.systemBarsPadding(),
                )
            } else {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
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
                                Screen.Home -> HomeScreen(showSeal = showSeal)
                                Screen.Search -> SearchScreen(
                                    viewModel = viewModel,
                                    isDark = isDark,
                                    onToggleTheme = viewModel::toggleTheme,
                                    onOpenFilter = viewModel::goFilter,
                                )
                                Screen.Notifications -> NotificationsScreen(viewModel = viewModel)
                                Screen.Filter -> FilterScreen(viewModel = viewModel)
                            }
                        }
                    }
                    Dock(
                        current = screen,
                        isDefaultLauncher = isDefaultLauncher,
                        onHome = viewModel::goHome,
                        onSearch = viewModel::goSearch,
                        onNotifications = viewModel::goNotifications,
                        onRequestDefault = onRequestDefault,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        // Over a sub-screen the dock becomes frosted "wet paper"; over Home it stays a
                        // faint pill on the wallpaper.
                        frosted = screen != Screen.Home,
                    )
                }
            }

            // Commitment gate for hiding an app — overlays whichever screen requested the block.
            pendingBlock?.let { app ->
                BlockConfirmDialog(
                    appLabel = app.label,
                    storageGranted = storageGranted,
                    onGrantStorage = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val direct = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                            runCatching { context.startActivity(direct) }
                                .onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        } else {
                            // Android 10: no All-files access; the legacy storage runtime permission
                            // backs the uninstall-proof mirror.
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
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
