package io.eddiegulay.tempo.notification

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
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

    override fun onListenerConnected() {
        activeInstance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        activeInstance = null
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
        val items = active
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

        return TempoNotification(
            key = sbn.key,
            title = title.ifBlank { appLabel(sbn.packageName) },
            body = body,
            time = formatTime(sbn.postTime),
            appLabel = appLabel(sbn.packageName),
            icon = icon,
            postTime = sbn.postTime,
            contentIntent = notification.contentIntent,
            autoCancel = (notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun formatTime(postTime: Long): String {
        val zone = ZoneId.systemDefault()
        val posted = Instant.ofEpochMilli(postTime).atZone(zone)
        val today = LocalDate.now(zone)
        return when (posted.toLocalDate()) {
            today -> "%02d:%02d".format(posted.hour, posted.minute)
            today.minusDays(1) -> "昨日"
            else -> "%d/%d".format(posted.monthValue, posted.dayOfMonth)
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
