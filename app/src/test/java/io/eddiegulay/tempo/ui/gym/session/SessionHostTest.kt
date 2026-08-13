package io.eddiegulay.tempo.ui.gym.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What `03-player.md` §E.5's one sanctioned Phase 3 change actually amounts to in the player.
 *
 * §E.5: *"When the `health` service lands in Phase 3, **the only thing that changes in this spec is the
 * disarm matrix (§D.7)**."* The disarm matrix itself is `CueDisarmTest`'s. This file pins the two
 * things in `SessionHost.kt` that decide whether that change reaches a user at all — the loop gate and
 * the `ON_STOP` call site — because Phase 3 shipped a service that started, posted a notification, and
 * changed nothing whatsoever about a pocketed workout.
 *
 * The gate is a pure function so this half needs no device. The call sites cannot be, so they are read
 * from source, the same technique `GymShellTest` uses for reachability and for the same reason: the
 * failure is invisible to the compiler and invisible in a screenshot.
 */
class SessionHostTest {

    // ── The loop gate ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a visible session ticks and a paused one does not, service or no service`() {
        // The machine's answer is never overridden. A paused session with a notification up is still
        // paused; the notification is the thing that says so.
        assertTrue(sessionShouldTick(ticking = true, visible = true, serviceHeld = false))
        assertTrue(sessionShouldTick(ticking = true, visible = true, serviceHeld = true))
        assertFalse(sessionShouldTick(ticking = false, visible = true, serviceHeld = false))
        assertFalse(sessionShouldTick(ticking = false, visible = true, serviceHeld = true))
    }

    @Test
    fun `a pocketed session keeps ticking when the service is holding it`() {
        // This single row is the entire deliverable of the foreground service. `onTick` is what
        // advances a segment and what arms the next cue, so a loop stopped here means the 3-2-1 never
        // fires, 「休息」 never fires, and — because the notification is a projection of the published
        // frame — the notification itself freezes on 運動 ・ 腕立て伏せ with a 休止 button, for twenty
        // minutes, on a service whose own KDoc lists "it never ticks" as a virtue.
        assertTrue(sessionShouldTick(ticking = true, visible = false, serviceHeld = true))
    }

    @Test
    fun `a pocketed session with no service stops, exactly as Phase 1 did`() {
        // Not a degraded path — the correct one. A backgrounded process with nothing holding it open
        // is about to be frozen, and twenty wake-ups a second inside it is pure cost.
        assertFalse(sessionShouldTick(ticking = true, visible = false, serviceHeld = false))
        assertFalse(sessionShouldTick(ticking = false, visible = false, serviceHeld = false))
        assertFalse(sessionShouldTick(ticking = false, visible = false, serviceHeld = true))
    }

    // ── The call sites ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the loop is gated by the predicate and by nothing else`() {
        // `repeatOnLifecycle(STARTED)` is a second, invisible gate saying the opposite of the
        // `serviceHeld` term: with it in place a service-backed session still had its loop stopped at
        // ON_STOP, so fixing the disarm matrix alone would have yielded silence anyway — nothing
        // advances a segment and nothing re-arms a schedule. Two gates, one of which cannot be seen
        // from the other, is how this shipped broken the first time.
        assertFalse(
            "the tick loop must not be gated on window visibility a second time",
            host.contains("repeatOnLifecycle(Lifecycle.State"),
        )
        assertFalse(
            "...and the import goes with it, so the idiom cannot creep back unnoticed",
            host.contains("import androidx.lifecycle.repeatOnLifecycle"),
        )
        assertTrue(
            "the loop must run off the predicate: $host",
            host.contains("snapshotFlow { controller.shouldTick }"),
        )
    }

    @Test
    fun `backgrounding tells the disarm matrix whether the service is holding the session`() {
        // §D.7's ON_STOP row has two answers now and this is the only place that chooses between
        // them. A `CueState` built without the flag silently takes the default — Phase 1's teardown —
        // and the service goes back to buying nothing, with nothing failing.
        val body = declarationBody("fun onBackgrounded()")
        assertTrue("ON_STOP is still the event: $body", body.contains("CueEvent.ON_STOP"))
        assertTrue("the flag must reach CueState: $body", body.contains("serviceHolding = serviceHeld"))
    }

    @Test
    fun `the armed schedule survives a transition the service did not interrupt`() {
        // Nulling the key forces `publish` to recompute the segment's schedule on the way back. That
        // is right when the channel was torn down and wrong when it was not: the pending 3-2-1 is
        // already posted at the correct wall time, and cancelling it to re-post it a few milliseconds
        // later degrades the one cue the service was bought to deliver.
        for (header in listOf("fun onBackgrounded()", "fun onForegrounded()")) {
            val body = declarationBody(header)
            assertTrue("$header must not drop the key unconditionally: $body", body.contains("if (!serviceHeld) armedKey = null"))
        }
    }

    @Test
    fun `the hold is read from the platform's answer, not from the mount's intention`() {
        // `TrainingHold` is set inside `startForegroundOrStop`'s try, after the call that can refuse.
        // Three refusals are possible and on a device that takes any of them the process is NOT
        // protected — a cue channel left armed there is §D.4's leaked ToneGenerator in a process the
        // system is about to freeze, which is worse than the silence it was trying to fix.
        val service = read("gym/TrainingService.kt")
        val start = service.indexOf("ServiceCompat.startForeground(")
        val set = service.indexOf("TrainingHold.set(true)")
        assertTrue("the hold must be claimed: $service", set > 0)
        assertTrue("...only after startForeground returned", set > start)
        assertTrue("...and inside the try that catches every refusal", set < service.indexOf("catch (_: Exception)"))

        // Cleared on every way out, including the one the framework takes on its own with
        // stopWithTask="true" — see `TrainingManifestTest`.
        assertTrue("stopNow must clear it", declarationBody("private fun stopNow()", service).contains("TrainingHold.set(false)"))
        assertTrue("onDestroy must clear it", declarationBody("override fun onDestroy()", service).contains("TrainingHold.set(false)"))

        // Read lifecycle-unaware, because ON_STOP is the moment both readers need the value and
        // `collectAsStateWithLifecycle` has stopped collecting by then.
        assertTrue("the host must read it: $host", host.contains("TrainingHold.held.collectAsState()"))
    }

    // ── Source access ───────────────────────────────────────────────────────────────────────────

    private val host: String by lazy { read("ui/gym/session/SessionHost.kt") }

    private fun declarationBody(header: String, source: String = host): String {
        val at = source.indexOf(header)
        check(at >= 0) { "not found: $header" }
        val open = source.indexOf('{', at)
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

    @Test
    fun `the source reader is looking at a real file`() {
        // A silent typo in a path would turn every assertion above into a passing test over an empty
        // string. `read` throws instead, and this states the expectation once.
        assertEquals(true, host.contains("class SessionController"))
    }
}
