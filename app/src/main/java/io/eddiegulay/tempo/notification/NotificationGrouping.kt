package io.eddiegulay.tempo.notification

import androidx.compose.runtime.Immutable

/** A per-app bucket of notifications, in the order they should be rendered. */
@Immutable
data class NotificationGroup(
    val packageName: String,
    val appLabel: String,
    val items: List<TempoNotification>,
)

/**
 * Buckets a flat, already-ranked notification list by source app.
 *
 * The input is the service snapshot, which is sorted by system ranking then post-time. We preserve
 * that order in two ways: items keep their incoming order *within* a bucket, and buckets are emitted
 * in first-seen order — so the app whose best-ranked notification came first stays on top. No
 * re-ranking is needed beyond what the service already did.
 *
 * Pure function (no Android types touched) so it is unit-testable on the JVM.
 */
fun groupByApp(notifications: List<TempoNotification>): List<NotificationGroup> {
    val buckets = LinkedHashMap<String, MutableList<TempoNotification>>()
    for (n in notifications) {
        buckets.getOrPut(n.packageName) { mutableListOf() }.add(n)
    }
    return buckets.map { (pkg, items) ->
        NotificationGroup(packageName = pkg, appLabel = items.first().appLabel, items = items)
    }
}
