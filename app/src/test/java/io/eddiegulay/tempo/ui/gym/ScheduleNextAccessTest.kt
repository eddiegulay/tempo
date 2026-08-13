package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.faultCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What 予定に入れる says while it is asking for calendar access, and where its 許す goes.
 *
 * Both claims are about a `@Composable`'s fault chain, which no JVM test can render, so the structural
 * halves are read from source — the same technique `GymShellTest` uses, for the same reason: a message
 * shown at the wrong moment fails nothing, compiles cleanly, and is only ever seen by a user.
 *
 * They are pinned at all because the strip is the one place this control speaks, and both defects were
 * about it saying something untrue: a "lost" message for access that was never held, and 許可する on a
 * tap that could only open Settings.
 *
 * **Restated against keys rather than against literals.** The words moved into [StringsJa] and
 * [StringsEn], so `source.contains("\"設定を開く\"")` now asserts something that is *false by
 * construction* in every migrated file, and its sibling `assertFalse` — 許可する is not in this file —
 * became **vacuously true**: the copy left, so the absence is guaranteed and the assertion guards
 * nothing forever. Both are replaced by the two facts that are actually load-bearing:
 *
 * 1. the word each file reaches for is the *right key*, and that key still holds the right words; and
 * 2. this file supplies **exactly one** strip action, the Settings fork — everything else falls
 *    through to `faultCopy`, which is what "許可する belongs to faultCopy" was trying to say.
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
        assertTrue(
            "the fork must be read, not merely relied on: $source",
            source.contains("permission.permanentlyDenied"),
        )

        // The word is `CalendarScreen.AccessPrompt`'s, and both files now reach it by the same key
        // rather than by re-typing it. Pinning the key's *value* here keeps this a test about what the
        // user is told, not merely about which member was referenced.
        assertEquals("設定を開く", StringsJa.calendar.accessOpenSettings)
        assertEquals("Open Settings", StringsEn.calendar.accessOpenSettings)
        assertTrue(
            "設定を開く is read from the table, not restated: $source",
            source.contains("s.calendar.accessOpenSettings"),
        )
        assertTrue(
            "設定を開く is the word for CalendarScreen's permanently-denied fork",
            read("ui/CalendarScreen.kt").contains("calendar.accessOpenSettings"),
        )
    }

    @Test
    fun `the grant word stays faultCopy's, and this file overrides one action and no more`() {
        // The claim the old `assertFalse(source.contains("許可する"))` was making, made in a way that
        // can still fail. 許可する is `faultCopy(PermissionLost)`'s word for a permission the system
        // will still prompt for; this file's only business with it is *replacing* it in the one case
        // where no prompt is coming.
        assertEquals("許可する", StringsJa.fault.calendar.permissionLostAction)
        assertEquals(
            "the strip's default action for a lost permission is faultCopy's, in both languages",
            listOf(StringsJa.fault.calendar.permissionLostAction, StringsEn.fault.calendar.permissionLostAction),
            listOf(
                faultCopy(CalendarFault.PermissionLost, StringsJa).action,
                faultCopy(CalendarFault.PermissionLost, StringsEn).action,
            ),
        )

        // One override, and it is the Settings one. A second `action =` on this panel would be this
        // file re-authoring a fault's words — by key or by literal, and the key is the way it would
        // happen now that the literals are gone.
        assertEquals(
            "ScheduleNextAction may supply exactly one FaultStrip action: $source",
            1,
            Regex("""action\s*=""").findAll(source).count(),
        )
        assertTrue(
            "and it is the permanently-denied fork, conditional on the fork",
            source.contains("action = s.calendar.accessOpenSettings.takeIf {"),
        )
        assertFalse(
            "the grant word is faultCopy's to choose, not this file's",
            source.contains("permissionLostAction"),
        )
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
