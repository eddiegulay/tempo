package io.eddiegulay.tempo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.data.AppRepository
import io.eddiegulay.tempo.data.BlockadeRepository
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
    private val blockadeRepository: BlockadeRepository,
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

    /** The full inventory, including blocked apps — used by the hidden-apps filter page. */
    val apps: StateFlow<List<AppInfo>> = appRepository.apps

    /** Blocked packages mapped to their unlock time (epoch millis). Presence == hidden. */
    val blockade: StateFlow<Map<String, Long>> = blockadeRepository.blockade

    /** The inventory minus blocked apps — what Search shows. */
    val visibleApps: StateFlow<List<AppInfo>> =
        combine(appRepository.apps, blockade) { apps, blocked ->
            apps.filterNot { it.packageName in blocked }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The app awaiting block confirmation, driving the commitment dialog; null when none. */
    private val _pendingBlock = MutableStateFlow<AppInfo?>(null)
    val pendingBlock: StateFlow<AppInfo?> = _pendingBlock.asStateFlow()

    /** A still-locked app the user tried to un-hide early; drives the countdown dialog. Null when none. */
    private val _lockedTap = MutableStateFlow<AppInfo?>(null)
    val lockedTap: StateFlow<AppInfo?> = _lockedTap.asStateFlow()

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

    // ----- focus mode (landscape flip clock / pomodoro) -----

    /** True while the "enter focus mode?" confirmation dialog is showing. */
    private val _pendingFocus = MutableStateFlow(false)
    val pendingFocus: StateFlow<Boolean> = _pendingFocus.asStateFlow()

    /** Long-pressing the Home clock asks to enter focus mode; surfaces the confirmation dialog. */
    fun requestFocus() {
        _pendingFocus.value = true
    }

    fun cancelFocus() {
        _pendingFocus.value = false
    }

    /** Confirm the pending request and step into the full-screen focus page. */
    fun confirmFocus() {
        _pendingFocus.value = false
        goFocus()
    }

    private fun goFocus() {
        _screen.value = Screen.Focus
    }

    // ----- app blockade (10-day hide) -----

    /** Ask to block an app; surfaces the commitment confirmation dialog. */
    fun requestBlock(app: AppInfo) {
        _pendingBlock.value = app
    }

    fun cancelBlock() {
        _pendingBlock.value = null
    }

    /** Confirm the pending block: hide the app for [BlockadeRepository.BLOCK_DAYS] days. */
    fun confirmBlock() {
        val app = _pendingBlock.value ?: return
        _pendingBlock.value = null
        viewModelScope.launch { blockadeRepository.block(app.packageName) }
    }

    /** Attempt to un-hide a package; silently ignored while its block is still active. */
    fun unblockApp(packageName: String) {
        viewModelScope.launch { blockadeRepository.unblock(packageName) }
    }

    /** Show the "still locked" countdown dialog for an app whose block hasn't elapsed. */
    fun showLocked(app: AppInfo) {
        _lockedTap.value = app
    }

    fun dismissLocked() {
        _lockedTap.value = null
    }

    /** Unlock time (epoch millis) for a package, or null if it isn't blocked. */
    fun unlockAt(packageName: String): Long? = blockade.value[packageName]

    fun blockadeNow(): Long = blockadeRepository.now()

    fun canUnblock(packageName: String): Boolean = blockadeRepository.canUnblock(packageName)

    /** Called from MainActivity.onNewIntent — a HOME press always returns to a clean Home. */
    fun resetToHome() {
        // A HOME press yields a genuinely clean Home: dismiss any transient blockade dialogs too.
        // Leaving Focus this way unmounts FocusScreen, which restores orientation and system bars.
        _pendingBlock.value = null
        _lockedTap.value = null
        _pendingFocus.value = false
        goHome()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val next = if (theme.value == TempoTheme.Sumi) TempoTheme.Paper else TempoTheme.Sumi
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
            blockadeRepository = BlockadeRepository.getInstance(appContext),
        ) as T
    }
}
