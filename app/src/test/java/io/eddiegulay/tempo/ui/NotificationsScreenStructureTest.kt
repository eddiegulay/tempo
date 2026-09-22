package io.eddiegulay.tempo.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The share overlay is a lift + scrim, not a Material menu. These pins are the ones a future
 * edit would silently drop: the 900ms hold, the swipe freeze, the touch-class haptic, and the
 * refusal of Dialog / DropdownMenu chrome.
 *
 * JVM-only — same idiom as `LibraryIndexScreenStructureTest`.
 */
class NotificationsScreenStructureTest {

    private val screen by lazy { repoFile("src/main/java/io/eddiegulay/tempo/ui/NotificationsScreen.kt").readText() }
    private val share by lazy { repoFile("src/main/java/io/eddiegulay/tempo/ui/NotificationShare.kt").readText() }
    private val haptics by lazy { repoFile("src/main/java/io/eddiegulay/tempo/ui/ShareHaptics.kt").readText() }
    private val listener by lazy {
        repoFile("src/main/java/io/eddiegulay/tempo/notification/TempoNotificationListener.kt").readText()
    }
    private val app by lazy { repoFile("src/main/java/io/eddiegulay/tempo/ui/TempoApp.kt").readText() }

    @Test
    fun `the hold is 900ms and not the platform long-press`() {
        assertTrue(share.contains("SHARE_HOLD_MS = 900L"))
        assertTrue(!share.contains("2_000L") && !share.contains("2000L"))
        assertTrue(declarationBody(screen, "private fun NotifRow(").contains("ShareHoldEffect"))
        assertTrue(
            "must not use combinedPressable for the hold",
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
    fun `pop and close haptics are the 30 percent waveform not LongPress`() {
        val row = declarationBody(screen, "private fun NotifRow(")
        assertTrue("hold site must not fire LongPress", !row.contains("HapticFeedbackType.LongPress"))
        assertTrue("hold site must not use LocalHapticFeedback", !row.contains("LocalHapticFeedback"))
        assertTrue(haptics.contains("USAGE_TOUCH"))
        assertTrue(haptics.contains("USAGE_NOTIFICATION"))
        assertTrue(!haptics.contains("USAGE_ALARM"))
        assertTrue(!haptics.contains("GymHaptics"))
        assertTrue(!haptics.contains("HapticFeedbackType"))
        assertTrue(haptics.contains("0, 28, 72, 28"))
        assertTrue(haptics.contains("SHARE_HAPTIC_AMPLITUDE = 77"))
        assertTrue(haptics.contains("SHARE_CLOSE_MS = 36L"))
        assertTrue(share.contains("shareHaptics.pop()"))
        assertTrue(share.contains("shareHaptics.close()"))
        assertTrue(!screen.contains("GymHaptics") && !share.contains("GymHaptics"))
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
    fun `TalkBack names share without requiring the hold`() {
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

    @Test
    fun `list ellipsises title and body share does not`() {
        val face = declarationBody(share, "fun NotificationCardFace(")
        val header = declarationBody(share, "private fun FaceHeader(")
        assertTrue(header.contains("maxLines = if (expanded) Int.MAX_VALUE else 1"))
        val media = declarationBody(share, "private fun FaceMedia(")
        assertTrue(media.contains("maxLines = if (expanded) Int.MAX_VALUE else 3"))
        assertTrue(share.contains("ListPictureMax = 120.dp"))
        assertTrue(share.contains("SharePictureMax = 240.dp"))
        assertTrue(share.contains("TempoShapes.Glyph"))
        assertTrue(media.contains("shareThread") || face.contains("shareThread"))
        assertTrue(media.contains("verticalScroll").not())
        assertTrue(face.contains("verticalScroll"))
    }

    @Test
    fun `the PNG twin cannot sit on save and copy`() {
        assertTrue(share.contains("(-cardW * 2f)"))
        assertTrue(share.contains("captureArmed"))
        val overlay = declarationBody(share, "fun NotificationShareOverlay(")
        val twin = overlay.indexOf("(-cardW * 2f)")
        val options = overlay.indexOf("s.notifications.saveImage")
        assertTrue("twin is composed before the option words", twin in 0 until options)
    }

    @Test
    fun `the listener reads picture extras and MessagingStyle`() {
        assertTrue(listener.contains("EXTRA_PICTURE"))
        assertTrue(listener.contains("largeIcon") || listener.contains("getLargeIcon"))
        assertTrue(listener.contains("MessagingStyle") || listener.contains("EXTRA_MESSAGES"))
        assertTrue(listener.contains("dataUri"))
        assertTrue(listener.contains("runCatching"))
        assertTrue(listener.contains("Dispatchers.Default"))
        assertTrue(listener.contains("SHARE_PICTURE_MAX_PX"))
        assertTrue(listener.contains("picture == null && messages.isEmpty()"))
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
