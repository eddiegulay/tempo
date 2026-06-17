package io.eddiegulay.tempo.notification

import android.app.PendingIntent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification row, distilled from a posted StatusBarNotification for the design's layout. */
@Immutable
data class TempoNotification(
    val key: String,
    /** Source package — the grouping key for the per-app layout. */
    val packageName: String,
    val title: String,
    val body: String,
    val time: String,
    val appLabel: String,
    /** Monochrome small icon, tinted to ink-soft at draw time. May be null if unavailable. */
    val icon: ImageBitmap?,
    /** Post time (epoch millis) — secondary sort key after ranking. */
    val postTime: Long,
    /** Fired when the row is tapped; null if the notification carries no content intent. */
    val contentIntent: PendingIntent?,
    /** Whether the notification should be cleared after its content intent is sent. */
    val autoCancel: Boolean,
    /**
     * Inline actions in their original order. Position is the index into the live
     * `Notification.actions[]`, which is how [TempoNotificationListener] re-resolves and fires them
     * (we never hold the action's PendingIntent in the snapshot — see the listener).
     */
    val actions: List<TempoNotificationAction> = emptyList(),
)

/** A single inline notification action; [isReply] means it carries a RemoteInput text field. */
@Immutable
data class TempoNotificationAction(
    val title: String,
    val isReply: Boolean,
)

/**
 * In-memory bridge between [TempoNotificationListener] (a system-bound service) and the Compose UI.
 *
 * The service can't be observed directly, so it pushes the current set here and the Notifications
 * screen collects it. A simple object singleton is enough — there's exactly one listener.
 */
object NotificationStore {
    private val _notifications = MutableStateFlow<List<TempoNotification>>(emptyList())
    val notifications: StateFlow<List<TempoNotification>> = _notifications.asStateFlow()

    fun set(items: List<TempoNotification>) {
        _notifications.value = items
    }

    fun clear() {
        _notifications.value = emptyList()
    }
}
