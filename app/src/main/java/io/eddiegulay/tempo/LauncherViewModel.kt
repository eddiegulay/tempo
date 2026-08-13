package io.eddiegulay.tempo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.CalendarInfo
import io.eddiegulay.tempo.calendar.CalendarRepository
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.calendar.PendingWrite
import io.eddiegulay.tempo.calendar.WriteOutcome
import io.eddiegulay.tempo.calendar.hasCalendarAccess
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.data.AppRepository
import io.eddiegulay.tempo.data.BlockadeRepository
import io.eddiegulay.tempo.data.TempoTheme
import io.eddiegulay.tempo.data.ThemeRepository
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.notification.NotificationGroup
import io.eddiegulay.tempo.notification.NotificationRepository
import io.eddiegulay.tempo.notification.TempoNotification
import io.eddiegulay.tempo.notification.groupByApp
import io.eddiegulay.tempo.ui.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a swiped/cleared notification stays recoverable before it is really cancelled. */
private const val UNDO_WINDOW_MS = 4_000L

/**
 * What the Home clock's long-press can lead to.
 *
 * A named choice rather than a boolean or the destination [Screen] itself: the dialog reports what
 * the user *meant*, and the ViewModel decides where that lands. Keeping the mapping in one `when`
 * means a third mode cannot be half-added — and it keeps the dialog from being able to navigate the
 * launcher to any screen it likes.
 */
enum class LauncherMode { Focus, Gym }

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
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    // Read once, synchronously, at construction so the first frame already reflects stored choices
    // (no theme flash, no blank Home for returning users). The flows below stay the live source of
    // truth and update the UI whenever the values change later.
    private val initialSettings = themeRepository.loadInitialSettings()

    val theme: StateFlow<TempoTheme> = themeRepository.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.theme)

    /**
     * The UI language, seeded from the same synchronous read as [theme] and for the same reason: it
     * must be correct on the **first frame**. A language that arrives one frame late is a visible
     * flash of the wrong language on every cold start, which is worse than a theme flash — a user
     * sees words they cannot read and assumes the setting did not save.
     *
     * `Eagerly`, deliberately, matching [theme]: `WhileSubscribed` would drop the value whenever the
     * last collector goes away and re-emit the seed on return.
     */
    val lang: StateFlow<Lang> = themeRepository.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.lang)

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

    /**
     * Open 鍛錬 from the dock.
     *
     * This exists because the gym earned a dock button. [confirmMode] still reaches [Screen.Gym] from
     * the clock's mode dialog and both routes are wanted: the dialog is how you *choose* between
     * 集中 and 鍛錬, the dock is how you *return* to a log you keep. 集中 keeps no such button — it
     * hides everything by design, so there is nothing to come back to.
     *
     * The gym's own back stack is untouched here. Re-entering always lands on 鍛錬 because
     * `GymViewModel.onLeaveShell` rebased the stack on the way out, and a live session survives both
     * — leaving the gym is not leaving the workout.
     */
    fun goGym() {
        _screen.value = Screen.Gym
        _searchQuery.value = ""
    }

    /** Open the hidden-apps filter page (launched from the Search header). */
    fun goFilter() {
        _screen.value = Screen.Filter
    }

    // ----- modes (landscape flip clock / pomodoro, and the gym) -----

    /** True while the mode chooser is showing. */
    private val _pendingMode = MutableStateFlow(false)
    val pendingMode: StateFlow<Boolean> = _pendingMode.asStateFlow()

    /** Long-pressing the Home clock asks which mode to enter; surfaces the chooser. */
    fun requestMode() {
        _pendingMode.value = true
    }

    fun cancelMode() {
        _pendingMode.value = false
    }

    /**
     * Commit to a mode and hand the whole window to it.
     *
     * A mode is not a screen the dock can wander back from: each takes the window, and the gym keeps
     * running state of its own. So the chooser closes first and the screen changes second, and there
     * There is deliberately no `goFocus()`: [Screen.Focus] is reachable only through a mode the user
     * picked out loud, because 集中 hides the launcher and should never be one stray tap away.
     * [Screen.Gym] used to share that rule and no longer does — it has a dock button and [goGym],
     * since a training log is somewhere you return to rather than somewhere you commit to.
     */
    fun confirmMode(mode: LauncherMode) {
        _pendingMode.value = false
        _screen.value = when (mode) {
            LauncherMode.Focus -> Screen.Focus
            LauncherMode.Gym -> Screen.Gym
        }
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
        _pendingMode.value = false
        _composing.value = null
        _pendingWrite.value = null
        _calendarFault.value = null
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

    /**
     * Choose the UI language. Writes to DataStore only; [lang] updates when the store echoes it back,
     * so there is one source of truth and no window where the flow and the disk disagree. Exactly
     * how [toggleTheme] behaves.
     */
    fun setLanguage(next: Lang) {
        viewModelScope.launch { themeRepository.setLanguage(next) }
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

    // ----- calendar -----

    /** Whether READ_CALENDAR is currently held. Re-checked on every resume; revocable in Settings. */
    private val _calendarAccess = MutableStateFlow(false)
    val calendarAccess: StateFlow<Boolean> = _calendarAccess.asStateFlow()

    /**
     * The next fortnight of events, live. Collection only starts once access is granted — a query
     * without the permission throws — and stops when nothing is watching, which unregisters the
     * provider observer while the user is off in another app.
     *
     * Carries its own loading and failure states: see [Loadable] for why an unreadable calendar must
     * never be allowed to render as an empty one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val agenda: StateFlow<Loadable<List<CalendarEvent>>> = _calendarAccess
        .flatMapLatest { granted ->
            if (granted) calendarRepository.events() else flowOf(Loadable.Ready(emptyList()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /**
     * The agenda as Home wants it: just the events, or none. Home is the one place that may degrade
     * quietly — it has a perfectly good date to fall back to, and a launcher's home screen is not the
     * place to argue about a provider error.
     */
    val calendarEvents: StateFlow<List<CalendarEvent>> = agenda
        .map { it.valueOrNull().orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Calendars that can host a new event, best default first. */
    private val _writableCalendars = MutableStateFlow<Loadable<List<CalendarInfo>>>(Loadable.Loading)
    val writableCalendars: StateFlow<Loadable<List<CalendarInfo>>> = _writableCalendars.asStateFlow()

    /** The event the composer is editing; null while composing a new one. */
    private val _composing = MutableStateFlow<CalendarEvent?>(null)
    val composing: StateFlow<CalendarEvent?> = _composing.asStateFlow()

    /** The change awaiting the user's yes. Drives the confirmation dialog; null when none. */
    private val _pendingWrite = MutableStateFlow<PendingWrite?>(null)
    val pendingWrite: StateFlow<PendingWrite?> = _pendingWrite.asStateFlow()

    /** True while a confirmed write is in flight, so the composer can't be double-submitted. */
    private val _writing = MutableStateFlow(false)
    val writing: StateFlow<Boolean> = _writing.asStateFlow()

    /** The last thing that went wrong, shown on the composer until acknowledged or resolved. */
    private val _calendarFault = MutableStateFlow<CalendarFault?>(null)
    val calendarFault: StateFlow<CalendarFault?> = _calendarFault.asStateFlow()

    fun refreshCalendarAccess(context: Context) = setCalendarAccess(hasCalendarAccess(context))

    fun setCalendarAccess(granted: Boolean) {
        if (_calendarAccess.value == granted) return
        _calendarAccess.value = granted
        if (granted) {
            // Whatever the user was blocked by, they've just fixed it.
            if (_calendarFault.value == CalendarFault.PermissionLost) _calendarFault.value = null
            loadCalendars()
        } else {
            _writableCalendars.value = Loadable.Ready(emptyList())
        }
    }

    /** Re-run the agenda query after a failure — the "もう一度" on the Calendar page. */
    fun retryAgenda() = calendarRepository.retry()

    /** Re-run the calendar list after a failure — the "もう一度" in the composer. */
    fun loadCalendars() {
        viewModelScope.launch {
            _writableCalendars.value = Loadable.Loading
            _writableCalendars.value = calendarRepository.writableCalendars()
        }
    }

    fun dismissCalendarFault() {
        _calendarFault.value = null
    }

    /** Home's top-right cluster is the only way in — Calendar has no dock tab. */
    fun goCalendar() {
        _screen.value = Screen.Calendar
    }

    fun composeNewEvent() {
        _composing.value = null
        _calendarFault.value = null
        // The list may have been empty because the query failed last time, or because an account was
        // added since. Either way, ask again before showing the user a composer they can't save from.
        if (_writableCalendars.value.valueOrNull().isNullOrEmpty()) loadCalendars()
        _screen.value = Screen.EventCompose
    }

    fun composeEvent(event: CalendarEvent) {
        _composing.value = event
        _calendarFault.value = null
        _screen.value = Screen.EventCompose
    }

    fun cancelCompose() {
        _composing.value = null
        _pendingWrite.value = null
        _calendarFault.value = null
        goCalendar()
    }

    // ----- mutations: proposed, confirmed, then written -----

    /**
     * Propose a save. Nothing is written yet — this only raises the confirmation, because the event
     * is going to land on every device the user owns and, if there are guests, in their inboxes too.
     */
    fun requestSave(draft: EventDraft) {
        val editing = _composing.value
        _pendingWrite.value =
            if (editing == null) PendingWrite.Create(draft) else PendingWrite.Update(editing, draft)
    }

    /** Propose a delete. */
    fun requestDelete() {
        val editing = _composing.value ?: return
        _pendingWrite.value = PendingWrite.Delete(editing)
    }

    fun cancelWrite() {
        _pendingWrite.value = null
    }

    /**
     * Commit the proposed change.
     *
     * On success we leave for the agenda; there is no optimistic update, because the provider notifies
     * its own change and the flow re-queries — so the list can only ever show what the calendar really
     * contains. On failure we stay exactly where we are: the composer keeps every word the user typed,
     * and the fault is shown above the fields with a way out. Navigating away on a failed write would
     * destroy their draft and tell them nothing.
     */
    fun confirmWrite() {
        val pending = _pendingWrite.value ?: return
        if (_writing.value) return
        _pendingWrite.value = null
        _calendarFault.value = null
        _writing.value = true

        viewModelScope.launch {
            val outcome = when (pending) {
                is PendingWrite.Create -> calendarRepository.insert(pending.draft)
                is PendingWrite.Update -> calendarRepository.update(pending.event.eventId, pending.draft)
                is PendingWrite.Delete -> calendarRepository.delete(pending.event.eventId)
            }
            _writing.value = false

            when (outcome) {
                is WriteOutcome.Ok -> {
                    _composing.value = null
                    goCalendar()
                }
                is WriteOutcome.Failed -> {
                    _calendarFault.value = outcome.fault
                    // A write that failed for want of permission means access was revoked underneath
                    // us; drop the flag so the page gates itself and the prompt reappears.
                    if (outcome.fault == CalendarFault.PermissionLost) _calendarAccess.value = false
                }
            }
        }
    }

    /**
     * Hands off to the real calendar app. This is how repeating events get edited at all: writing the
     * provider's exception rows for a single occurrence of a series is subtle, and getting it wrong
     * silently rewrites a meeting for everyone on the invite — so Tempo doesn't try.
     *
     * A device with no calendar app at all would otherwise make this button do nothing when tapped,
     * which is the one thing the escape hatch must never do.
     */
    fun openInCalendarApp(context: Context, event: CalendarEvent) {
        if (!calendarRepository.openInCalendarApp(context, event)) {
            _calendarFault.value = CalendarFault.NoCalendarApp
        }
    }
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
            calendarRepository = CalendarRepository(appContext),
        ) as T
    }
}
