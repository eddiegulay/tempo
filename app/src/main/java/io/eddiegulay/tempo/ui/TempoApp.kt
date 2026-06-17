package io.eddiegulay.tempo.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.TempoTheme
import io.eddiegulay.tempo.ui.theme.AmoledColors
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.PaperColors

/** The three navigable layers of the launcher. */
enum class Screen { Home, Search, Notifications }

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

    val isDark = theme == TempoTheme.Amoled
    val colors = if (isDark) AmoledColors else PaperColors

    // Keep the system bar icons legible against whichever theme is active.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    // Back never leaves the launcher: from a sub-screen it returns home; on home it's a no-op.
    BackHandler(enabled = screen != Screen.Home) { viewModel.goHome() }
    BackHandler(enabled = screen == Screen.Home) { /* stay on home */ }

    CompositionLocalProvider(LocalTempoColors provides colors) {
        Box(Modifier.fillMaxSize().tempoBackground(colors)) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(showSeal = showSeal)
                        Screen.Search -> SearchScreen(viewModel = viewModel)
                        Screen.Notifications -> NotificationsScreen(viewModel = viewModel)
                    }
                }
                Dock(
                    current = screen,
                    isDark = isDark,
                    isDefaultLauncher = isDefaultLauncher,
                    onHome = viewModel::goHome,
                    onSearch = viewModel::goSearch,
                    onNotifications = viewModel::goNotifications,
                    onToggleTheme = viewModel::toggleTheme,
                    onRequestDefault = onRequestDefault,
                )
            }
        }
    }
}
