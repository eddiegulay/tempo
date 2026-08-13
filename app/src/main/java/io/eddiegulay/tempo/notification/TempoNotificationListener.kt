package io.eddiegulay.tempo.notification

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import io.eddiegulay.tempo.data.BlockadeRepository
import io.eddiegulay.tempo.data.ThemeRepository
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.i18n.stringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Surfaces real device notifications to the Notifications screen.
 *
 * Android binds this once the user grants notification access. On connect / post / removal / ranking
 * update we re-snapshot the active, clearable, non-summary notifications — ordered by the system
 * ranking then post-time — into [NotificationStore]. A reference to the bound instance is kept so the
 * UI can dismiss a row (`cancelNotification`).
 */
class TempoNotificationListener : NotificationListenerService() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val blockade by lazy { BlockadeRepository.getInstance(applicationContext) }
    private val settings by lazy { ThemeRepository(applicationContext) }

    /**
     * The copy table this service formats timestamps with.
     *
     * A bound service has no composition, so it cannot read `LocalStrings`; it resolves its own and
     * keeps it current. Seeded on connect and replaced from [ThemeRepository.language], because
     * `TempoNotification.time` is **baked in at snapshot time** — a language switch is only visible
     * once the snapshot is retaken, which is why the collector re-runs [refresh].
     *
     * Read and written on the service's main thread only (the callbacks and `Main.immediate` are the
     * same thread), so this needs no synchronisation.
     */
    private var strings: Strings = StringsJa

    override fun onListenerConnected() {
        activeInstance = this
        // A disconnected scope is cancelled for good, so start a fresh one on (re)connect.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // Seed synchronously so the very first snapshot is already in the right language — the same
        // single file read the first frame does, for the same reason. `drop(1)` below then skips the
        // emission we just read.
        strings = stringsFor(runCatching { settings.loadInitialSettings().lang }.getOrDefault(Lang.Ja))
        scope.launch {
            settings.language.drop(1).collect { lang ->
                strings = stringsFor(lang)
                refresh()
            }
        }
        // Re-suppress when the blocked set changes (e.g. an app is blocked while it has a live
        // notification). `drop(1)` skips the seeded value — onListenerConnected already refreshes.
        scope.launch { blockade.blockade.drop(1).collect { refresh() } }
        refresh()
    }

    override fun onListenerDisconnected() {
        activeInstance = null
        scope.cancel()
        NotificationStore.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val rankingMap = runCatching { currentRanking }.getOrNull()
        val ranking = Ranking()
        val rankOf: (String) -> Int = { key ->
            if (rankingMap != null && rankingMap.getRanking(key, ranking)) ranking.rank else Int.MAX_VALUE
        }

        val active = runCatching { activeNotifications }.getOrNull() ?: emptyArray()

        // Blocked apps are suppressed system-wide: clear any of their notifications from the shade
        // (best effort — only clearable ones can be cancelled) and never surface them in Tempo.
        val blocked = runCatching { blockade.blockade.value.keys }.getOrDefault(emptySet())
        if (blocked.isNotEmpty()) {
            active.filter { it.packageName in blocked && it.isClearable }
                .forEach { runCatching { cancelNotification(it.key) } }
        }

        val items = active
            .filterNot { it.packageName in blocked }
            .filter { it.isClearable }
            // Collapse groups: drop the summary, keep the individual children.
            .filterNot { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 }
            .mapNotNull { toModel(it) }
            .sortedWith(compareBy({ rankOf(it.key) }, { -it.postTime }))

        NotificationStore.set(items)
    }

    private fun toModel(sbn: StatusBarNotification): TempoNotification? {
        val notification = sbn.notification
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return null

        val icon = runCatching {
            notification.smallIcon?.loadDrawable(this)?.toBitmap()?.asImageBitmap()
        }.getOrNull()

        // Keep only invokable actions, in order; position here == index into notification.actions[],
        // which is how we fire / reply later (we never retain the action PendingIntent in the model).
        val actions = notification.actions.orEmpty().mapNotNull { action ->
            val label = action.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TempoNotificationAction(title = label, isReply = !action.remoteInputs.isNullOrEmpty())
        }

        return TempoNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title.ifBlank { appLabel(sbn.packageName) },
            body = body,
            time = formatTime(sbn.postTime),
            appLabel = appLabel(sbn.packageName),
            icon = icon,
            postTime = sbn.postTime,
            contentIntent = notification.contentIntent,
            autoCancel = (notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            actions = actions,
        )
    }

    /**
     * Fire a plain (non-reply) inline action by its index in the live notification's action list.
     * We resolve the action fresh from [activeNotifications] rather than holding a stale handle.
     */
    fun sendAction(key: String, actionIndex: Int) {
        val action = findAction(key, actionIndex) ?: return
        runCatching { action.actionIntent?.send() }
    }

    /** Submit a RemoteInput reply for the action at [actionIndex] of notification [key]. */
    fun reply(key: String, actionIndex: Int, text: CharSequence) {
        val action = findAction(key, actionIndex) ?: return
        val remoteInputs = action.remoteInputs ?: return
        val pendingIntent = action.actionIntent ?: return
        val intent = Intent()
        val results = Bundle().apply {
            remoteInputs.forEach { putCharSequence(it.resultKey, text) }
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, results)
        runCatching { pendingIntent.send(this, 0, intent) }
    }

    private fun findAction(key: String, actionIndex: Int): Notification.Action? {
        val sbn = runCatching { activeNotifications }.getOrNull()?.firstOrNull { it.key == key } ?: return null
        return sbn.notification.actions?.getOrNull(actionIndex)
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * The timestamp on a notification card: clock today, a relative word yesterday, a date beyond.
     *
     * **This used to be the app's third relative-day vocabulary**, and the only one that was not
     * `JapaneseDate`'s. It said 昨日 where `GymHomeCopy` says きのう, and it rendered older dates as
     * `"%d/%d"` — arabic, slash-separated, no year, and day/month-ambiguous outside Japan and the US —
     * where `JapaneseDate.monthDay` says 六月十七日. Two renderings of the same idea, disagreeing about
     * both script and separator, in one app.
     *
     * It now goes through `fmt`, which changes three outputs and is meant to:
     *
     * | posted | before | ja now | en now |
     * |---|---|---|---|
     * | today | `09:05` | `09:05` | `09:05` |
     * | yesterday | `昨日` | `きのう` | `Yesterday` |
     * | older | `6/17` | `六月十七日` | `17 June` |
     *
     * The hiragana is `fmt.relativeDay`'s backward-looking vocabulary, which is the correct one here:
     * a posted notification is in the past, and 昨日 was the odd one out (§H3, §L7).
     */
    private fun formatTime(postTime: Long): String {
        val zone = ZoneId.systemDefault()
        val posted = Instant.ofEpochMilli(postTime).atZone(zone)
        val today = LocalDate.now(zone)
        val date = posted.toLocalDate()
        return when {
            date == today -> strings.fmt.clockAt(posted.toLocalDateTime())
            date == today.minusDays(1) -> strings.fmt.relativeDay(date, today)
            else -> strings.fmt.monthDay(posted.toLocalDateTime())
        }
    }

    companion object {
        /** The currently-bound listener, used by the UI to dismiss notifications. */
        @Volatile
        var activeInstance: TempoNotificationListener? = null
            private set

        /** True when the user has granted Tempo notification-listener access. */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        /** Intent target to open the system "Notification access" settings screen. */
        val settingsAction: String = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    }
}
