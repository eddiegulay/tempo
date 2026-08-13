package io.eddiegulay.tempo.ui.gym

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What 予定に入れる says while it is asking for calendar access, and where its 許す goes.
 *
 * Both claims are about a `@Composable`'s fault chain, which no JVM test can render, so both are read
 * from source — the same technique `GymShellTest` uses, for the same reason: a message shown at the
 * wrong moment fails nothing, compiles cleanly, and is only ever seen by a user.
 *
 * They are pinned at all because the strip is the one place this control speaks, and both defects were
 * about it saying something untrue: a "lost" message for access that was never held, and 許可する on a
 * tap that could only open Settings.
 */
class ScheduleNextAccessTest {

    private val source: String by lazy { read("ui/gym/ScheduleNextAction.kt") }

    @Test
    fun `the access message waits for an answer instead of appearing under the dialog asking for it`() {
        // `permission.request()` is fired from the same tap that expands this panel, so a chain that
        // reads `PermissionLost.takeIf { !granted }` renders 「カレンダーへのアクセスが必要です」
        // *underneath the system dialog* — a sentence about access being lost, shown to someone who is
        // at that moment being asked for it for the first time. It is the one term
        // `EventComposeScreen`'s otherwise-identical chain deliberately does not have.
        assertFalse(
            "the strip must not claim lost access before the ask comes back: $source",
            source.contains("PermissionLost.takeIf { !granted }"),
        )
        assertTrue(
            "the ask's answer is what unlocks the message",
            source.contains("PermissionLost.takeIf { answered && !granted }"),
        )
    }

    @Test
    fun `a tap that can only open Settings says so`() {
        // `CalendarPermissionState.permanentlyDenied` exists for exactly this and its own KDoc says
        // why: *"a tap that silently throws the user into Settings with no warning reads as a bug —
        // the prompt has to change its words and say where it is taking them"*. The mechanism was
        // being used here (request() already routes to Settings) while the words were not.
        assertTrue("the fork must be read, not merely relied on: $source", source.contains("permission.permanentlyDenied"))
        // Both words are the string table's: 許可する is `faultCopy(PermissionLost)`'s, 設定を開く is
        // `CalendarScreen.AccessPrompt`'s. Neither is assembled here.
        assertTrue("設定を開く is AccessPrompt's own word", source.contains("\"設定を開く\""))
        assertTrue(
            "設定を開く is the word for CalendarScreen's permanently-denied fork",
            read("ui/CalendarScreen.kt").contains("\"設定を開く\""),
        )
        assertFalse("許可する belongs to faultCopy, not to this file", source.contains("\"許可する\""))
    }

    /** Walks up from the working directory, so the test does not care where it is run from. */
    private fun read(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                File(dir, prefix + relative).takeIf { it.isFile }?.let { return it.readText() }
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relative from ${File("").absolutePath}")
    }
}
