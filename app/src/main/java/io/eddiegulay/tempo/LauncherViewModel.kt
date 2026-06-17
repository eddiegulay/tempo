package io.eddiegulay.tempo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.data.AppRepository
import io.eddiegulay.tempo.data.TempoTheme
import io.eddiegulay.tempo.data.ThemeRepository
import io.eddiegulay.tempo.notification.NotificationRepository
import io.eddiegulay.tempo.notification.TempoNotification
import io.eddiegulay.tempo.ui.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for launcher UI state: active screen, theme, search query, the app
 * inventory, live notifications, and whether Tempo is the default home app.
 *
 * Holding this in a ViewModel (scoped to the Activity) means it survives configuration changes and
 * keeps all navigation/persistence logic out of the composables.
 */
class LauncherViewModel(
    private val themeRepository: ThemeRepository,
    private val appRepository: AppRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    val theme: StateFlow<TempoTheme> = themeRepository.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TempoTheme.Paper)

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val apps: StateFlow<List<AppInfo>> = appRepository.apps
    val notifications: StateFlow<List<TempoNotification>> = notificationRepository.notifications

    private val _isDefaultLauncher = MutableStateFlow(false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    init {
        // Begin live app enumeration up front so Search is ready on first open.
        appRepository.start()
    }

    fun goHome() {
        _screen.value = Screen.Home
        _searchQuery.value = ""
    }

    fun goSearch() {
        _screen.value = Screen.Search
    }

    fun goNotifications() {
        _screen.value = Screen.Notifications
        _searchQuery.value = "" // drop the search query whenever we leave Search
    }

    /** Called from MainActivity.onNewIntent — a HOME press always returns to a clean Home. */
    fun resetToHome() = goHome()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val next = if (theme.value == TempoTheme.Amoled) TempoTheme.Paper else TempoTheme.Amoled
            themeRepository.setTheme(next)
        }
    }

    fun ensureAppsLoaded() = appRepository.start()

    fun launchApp(
        context: Context,
        app: AppInfo,
        sourceBounds: android.graphics.Rect? = null,
        opts: android.os.Bundle? = null,
    ) = appRepository.launch(context, app, sourceBounds, opts)

    fun openAppInfo(context: Context, app: AppInfo) = appRepository.openAppInfo(context, app)

    fun requestUninstall(context: Context, app: AppInfo) = appRepository.requestUninstall(context, app)

    fun peekIcon(app: AppInfo) = appRepository.peekIcon(app)

    suspend fun loadIcon(app: AppInfo) = appRepository.loadIcon(app)

    fun setDefaultLauncher(isDefault: Boolean) {
        _isDefaultLauncher.value = isDefault
    }

    // ----- notifications -----

    fun openNotification(notification: TempoNotification) {
        val intent = notification.contentIntent ?: return
        runCatching { intent.send() }.onSuccess {
            if (notification.autoCancel) notificationRepository.dismiss(notification.key)
        }
    }

    fun dismissNotification(key: String) = notificationRepository.dismiss(key)

    fun requestNotificationRebind(context: Context) = notificationRepository.requestRebind(context)
}

/** Manual factory wiring the repositories from the application context (no DI framework). */
class LauncherViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LauncherViewModel(
            themeRepository = ThemeRepository(appContext),
            appRepository = AppRepository.getInstance(appContext),
            notificationRepository = NotificationRepository(),
        ) as T
    }
}
