package io.eddiegulay.tempo.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [groupByApp] — pure list bucketing, no Android runtime needed (icon / intents are
 * null). Verifies the ordering contract: buckets in first-seen (best-ranked) order, items preserved
 * within a bucket.
 */
class NotificationGroupingTest {

    private fun notif(key: String, pkg: String, label: String = pkg): TempoNotification =
        TempoNotification(
            key = key,
            packageName = pkg,
            title = key,
            body = "",
            time = "",
            appLabel = label,
            icon = null,
            postTime = 0L,
            contentIntent = null,
            autoCancel = false,
        )

    @Test
    fun empty_input_yields_no_groups() {
        assertTrue(groupByApp(emptyList()).isEmpty())
    }

    @Test
    fun buckets_by_package_in_first_seen_order() {
        val input = listOf(
            notif("a1", "pkg.a"),
            notif("b1", "pkg.b"),
            notif("a2", "pkg.a"),
            notif("c1", "pkg.c"),
            notif("b2", "pkg.b"),
        )

        val groups = groupByApp(input)

        assertEquals(listOf("pkg.a", "pkg.b", "pkg.c"), groups.map { it.packageName })
    }

    @Test
    fun preserves_item_order_within_a_bucket() {
        val input = listOf(
            notif("a1", "pkg.a"),
            notif("b1", "pkg.b"),
            notif("a2", "pkg.a"),
            notif("a3", "pkg.a"),
        )

        val groupA = groupByApp(input).first { it.packageName == "pkg.a" }

        assertEquals(listOf("a1", "a2", "a3"), groupA.items.map { it.key })
    }

    @Test
    fun app_label_taken_from_first_item_in_bucket() {
        val input = listOf(
            notif("a1", "pkg.a", label = "Gmail"),
            notif("a2", "pkg.a", label = "ignored"),
        )

        assertEquals("Gmail", groupByApp(input).single().appLabel)
    }

    @Test
    fun single_app_collapses_to_one_group_with_all_items() {
        val input = (1..5).map { notif("m$it", "pkg.chat") }

        val groups = groupByApp(input)

        assertEquals(1, groups.size)
        assertEquals(5, groups.single().items.size)
    }
}
