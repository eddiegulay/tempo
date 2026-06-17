package io.eddiegulay.tempo.notification

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository over [NotificationStore] and the bound [TempoNotificationListener].
 *
 * Reading goes through the store's flow; mutating actions (dismiss) and rebinding are routed to the
 * live service instance, which is the only object allowed to call `cancelNotification`.
 */
class NotificationRepository {

    val notifications: StateFlow<List<TempoNotification>> = NotificationStore.notifications

    /** Clears a notification from the shade (and Tempo) by key. */
    fun dismiss(key: String) {
        TempoNotificationListener.activeInstance?.cancelNotification(key)
    }

    /**
     * Asks the system to rebind the listener. Useful after a process restart where access is still
     * granted but the service hasn't reconnected yet.
     */
    fun requestRebind(context: Context) {
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, TempoNotificationListener::class.java),
            )
        }
    }
}
