package io.eddiegulay.tempo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.data.AppRepository
import io.eddiegulay.tempo.data.TempoTheme
import io.eddiegulay.tempo.data.ThemeRepository
import io.eddiegulay.tempo.notification.NotificationGroup
import io.eddiegulay.tempo.notification.NotificationRepository
import io.eddiegulay.tempo.notification.TempoNotification
import io.eddiegulay.tempo.notification.groupByApp
import io.eddiegulay.tempo.ui.Screen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a swiped/cleared notification stays recoverable before it is really cancelled. */
private const val UNDO_WINDOW_MS = 4_000L

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

    // Read once, synchronously, at construction so the first frame already reflects stored choices
    // (no theme flash, no blank Home for returning users). The flows below stay the live source of
    // truth and update the UI whenever the values change later.
    private val initialSettings = themeRepository.loadInitialSettings()

    val theme: StateFlow<TempoTheme> = themeRepository.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.theme)

    /**
     * First-launch gate: true once the user has worked through the onboarding walkthrough. Seeded
     * from the synchronous read so a returning user lands straight on Home with no flash of the gate.
     */
    val onboardingComplete: StateFlow<Boolean> = themeRepository.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.onboardingComplete)

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** The full inventory, including hidden apps — used by the hidden-apps filter page. */
    val apps: StateFlow<List<AppInfo>> = appRepository.apps

    /** Packages the user has hidden from the launcher. */
    val hiddenApps: StateFlow<Set<String>> = themeRepository.hiddenApps
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.hiddenApps)

    /** The inventory minus hidden apps — what Search shows. */
    val visibleApps: StateFlow<List<AppInfo>> =
        combine(appRepository.apps, hiddenApps) { apps, hidden ->
            apps.filterNot { it.packageName in hidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notifications: StateFlow<List<TempoNotification>> = notificationRepository.notifications

    /** Keys swiped/cleared but not yet committed — hidden from the UI during the undo window. */
    private val _pendingDismiss = MutableStateFlow<Set<String>>(emptySet())
    val pendingDismiss: StateFlow<Set<String>> = _pendingDismiss.asStateFlow()
    private val dismissJobs = mutableMapOf<String, Job>()

    /** The notification list bucketed per app, with pending dismissals removed. */
    val grouped: StateFlow<List<NotificationGroup>> =
        combine(notifications, _pendingDismiss) { list, pending ->
            groupByApp(list.filterNot { it.key in pending })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Open the hidden-apps filter page (launched from the Search header). */
    fun goFilter() {
        _screen.value = Screen.Filter
    }

    /** Hide or unhide a package from the launcher. */
    fun setAppHidden(packageName: String, hidden: Boolean) {
        viewModelScope.launch { themeRepository.setHidden(packageName, hidden) }
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

    /** Mark the first-launch walkthrough finished; the gate gives way to Home from here on. */
    fun completeOnboarding() {
        viewModelScope.launch { themeRepository.setOnboardingComplete() }
    }

    // ----- notifications -----

    fun openNotification(notification: TempoNotification) {
        val intent = notification.contentIntent ?: return
        runCatching { intent.send() }.onSuccess {
            if (notification.autoCancel) notificationRepository.dismiss(notification.key)
        }
    }

    /**
     * Deferred dismissal: hide the row immediately, but only really cancel it after the undo window.
     * A notification can't be re-posted once cancelled, so undo works by *delaying* the real cancel
     * rather than restoring it.
     */
    fun dismissNotification(key: String) {
        if (key in _pendingDismiss.value) return
        _pendingDismiss.update { it + key }
        dismissJobs[key]?.cancel()
        dismissJobs[key] = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            notificationRepository.dismiss(key)
            dismissJobs.remove(key)
            _pendingDismiss.update { it - key }
        }
    }

    /** Clear everything currently visible, as one undoable batch. */
    fun dismissAllVisible() {
        notifications.value.map { it.key }
            .filterNot { it in _pendingDismiss.value }
            .forEach { dismissNotification(it) }
    }

    /** Restore all rows still inside the undo window (cancels their pending real-dismissals). */
    fun undoDismiss() {
        dismissJobs.values.forEach { it.cancel() }
        dismissJobs.clear()
        _pendingDismiss.value = emptySet()
    }

    fun sendNotificationAction(key: String, actionIndex: Int) =
        notificationRepository.sendAction(key, actionIndex)

    fun replyToNotification(key: String, actionIndex: Int, text: String) =
        notificationRepository.reply(key, actionIndex, text)

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
