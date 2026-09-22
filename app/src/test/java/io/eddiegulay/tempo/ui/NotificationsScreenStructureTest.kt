package io.eddiegulay.tempo.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The share overlay is a lift + scrim, not a Material menu. These pins are the ones a future
 * edit would silently drop: the 2-second hold, the swipe freeze, the house haptic, and the
 * refusal of Dialog / DropdownMenu chrome.
 *
 * JVM-only — same idiom as `LibraryIndexScreenStructureTest`.
 */
class NotificationsScreenStructureTest {

    private val screen by lazy { repoFile("src/main/java/io/eddiegulay/tempo/ui/NotificationsScreen.kt").readText() }
    private val share by lazy { repoFile("src/main/java/io/eddiegulay/tempo/ui/NotificationShare.kt").readText() }
    private val app by lazy { repoFile("src/main/java/io/eddiegulay/tempo/ui/TempoApp.kt").readText() }

    @Test
    fun `the hold is two seconds and not the platform long-press`() {
        assertTrue(share.contains("SHARE_HOLD_MS = 2_000L"))
        assertTrue(declarationBody(screen, "private fun NotifRow(").contains("ShareHoldEffect"))
        assertTrue(
            "must not use combinedPressable for the 2s hold",
            !declarationBody(screen, "private fun NotifRow(").contains("combinedPressable"),
        )
    }

    @Test
    fun `swipe is frozen while a card is lifted`() {
        val body = declarationBody(screen, "private fun NotifRow(")
        assertTrue(body.contains("enableDismissFromStartToEnd = dismissEnabled"))
        assertTrue(body.contains("enableDismissFromEndToStart = dismissEnabled"))
    }

    @Test
    fun `commit haptic is LongPress at the call site`() {
        val body = declarationBody(screen, "private fun NotifRow(")
        assertTrue(body.contains("HapticFeedbackType.LongPress"))
    }

    @Test
    fun `the overlay is a lift not a Material surface`() {
        assertTrue(share.contains("fun NotificationShareOverlay("))
        assertTrue(share.contains("BackHandler"))
        assertTrue("no AlertDialog", !share.contains("AlertDialog"))
        assertTrue("no DropdownMenu", !share.contains("DropdownMenu"))
        assertTrue("no ModalBottomSheet", !share.contains("ModalBottomSheet"))
        assertTrue("hosted above the dock", app.contains("NotificationShareOverlay("))
        assertTrue("not nested in the list", !screen.contains("NotificationShareOverlay("))
    }

    @Test
    fun `TalkBack names share without requiring the two-second hold`() {
        val body = declarationBody(screen, "private fun NotifRow(")
        assertTrue(body.contains("onLongClick(label = s.notifications.share)"))
        assertTrue(
            "share is not also a custom action",
            !body.contains("CustomAccessibilityAction(label = s.notifications.share)"),
        )
    }

    @Test
    fun `the lift measures the face not the action chips`() {
        val body = declarationBody(screen, "private fun NotifRow(")
        assertTrue(body.contains("if (lifted) onShareBoundsChange(rect)"))
        assertTrue(body.contains("onGloballyPositioned"))
    }

    private fun declarationBody(source: String, header: String): String {
        val start = source.indexOf(header)
        require(start >= 0) { "declaration not found: $header" }
        val open = source.indexOf('{', start)
        require(open >= 0) { "no body for: $header" }
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, i)
                }
            }
        }
        throw IllegalStateException("unbalanced braces reading: $header")
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, relative).takeIf { it.isFile }?.let { return it }
            File(dir, "app/$relative").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        throw AssertionError("$relative not found from ${System.getProperty("user.dir")}")
    }
}
