package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.session.Destination
import io.eddiegulay.tempo.gym.session.Overlay
import io.eddiegulay.tempo.gym.session.SessionContext
import io.eddiegulay.tempo.gym.session.SessionEvent
import io.eddiegulay.tempo.gym.session.SessionTransition
import io.eddiegulay.tempo.gym.session.compiledCircuit
import io.eddiegulay.tempo.gym.session.step
import io.eddiegulay.tempo.ui.gym.session.closesSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the player's pages and its host actually *call* — the half of the player no pure function sees.
 *
 * `SessionController` is untestable on this classpath for the reason `GymApiContractTest` states: it
 * holds a `GymViewModel`, which needs a main dispatcher, and `CONTRIBUTING.md` forbids adding
 * Robolectric or `kotlinx-coroutines-test` to reach one. Every defect below was therefore invisible to
 * a suite of pure functions that all passed:
 *
 * - `countdownAnnouncement` was correct for its arguments and told a lie on screen, because the page
 *   never gave it the one fact that makes the answer true;
 * - `SessionMachine` row 21 was correct and unreachable, because the host published a state the sheet
 *   on screen contradicted;
 * - `SessionEffect.DeleteResult` was emitted correctly and applied as a no-op.
 *
 * So this file pins the *call*: it reads the source the way `GymShellTest` does — crudely, by name,
 * and honest about it — and where a claim can be run instead of read, it is run against the real
 * machine ([`つづける is reachable again after a failed quit write`]).
 */
class PlayerWiringTest {

    // ─── the announcement's second argument ─────────────────────────────────────────────────────

    @Test
    fun `WorkPage tells the announcement how long the segment is, not only what is left`() {
        // タバタ is twenty-second intervals, eight of them, and every one opened with 「残り 三十秒」 in
        // TalkBack: at t=0 the remainder is 20_000, which fell into the >10s arm. Passing the segment's
        // own length is the whole fix, and the page is where it lives — a page that passed
        // `state.remainingMs` twice would satisfy `PlayerCopyTest` and go on lying.
        val call = Regex("countdownAnnouncement\\(([^)]*)\\)").find(source("ui/gym/session/WorkPage.kt"))
        assertNotNull("WorkPage must announce the countdown at all", call)

        val args = call!!.groupValues[1].split(",").map { it.trim() }
        assertEquals("countdownAnnouncement takes the remainder and the plan: $args", 2, args.size)
        assertTrue(
            "the second argument must be the segment's own length, not the remainder again: $args",
            args[1].contains("effectiveMs"),
        )
    }

    // ─── the quit sheet the machine forgot it was in ────────────────────────────────────────────

    @Test
    fun `a failed quit write puts the machine back in the sheet that is still on screen`() {
        // §A QUIT_SHEET calls つづける "the one row that must never be conditional", and it was dead
        // after every failed write: rows 22 and 23 hand back `Overlay.NONE` before the write is even
        // attempted, while §A's "never navigate away on a failed save" keeps the sheet drawn through
        // the host's own flags. `QuitDismissed` then hit row 21's guard and returned null.
        for (effect in listOf("is SessionEffect.Finish ->", "is SessionEffect.Discard ->")) {
            val arm = body(host, effect)
            val failed = arm.substringAfter("is GymWrite.Failed ->", "")
            assertTrue("$effect must handle a failed write", failed.isNotBlank())
            assertTrue(
                "$effect's failure must restore Overlay.QUIT_SHEET: $failed",
                resolves(failed, "overlay = Overlay.QUIT_SHEET"),
            )
        }
    }

    @Test
    fun `つづける is reachable again after a failed quit write`() {
        // The same claim, run rather than read: the sequence a user performs, with the write failing,
        // and then the row that must always work.
        val live = context(elapsedMs = 20_000L, resultsWritten = 3)
        val sheet = live.applied(step(live, SessionEvent.QuitRequested)!!)
        val saved = sheet.applied(step(sheet, SessionEvent.SavePartial)!!)

        // What the machine publishes on its own — it has already left the sheet the user can see.
        assertEquals(Overlay.NONE, saved.overlay)
        assertNull("this null is the bug: the escape from a failure dialog did nothing", step(saved, SessionEvent.QuitDismissed))

        // The overlay is read out of `SessionHost.kt` rather than named here, so this is a claim about
        // the shipped host and not about a state a test invented for it.
        val held = saved.copy(overlay = restoredOverlay)
        val back = step(held, SessionEvent.QuitDismissed)
        assertNotNull("つづける must never be conditional", back)
        // Row 21's own assertions live in `SessionMachineTest`; what matters here is that the row is
        // reachable at all, and that it puts the user back in the session rather than out of it.
        assertTrue("つづける returns to the session: ${back!!.destination}", back.destination is Destination.Live)
        assertEquals(Overlay.NONE, back.overlay)
    }

    @Test
    fun `a sheet opened over a pause still returns to the pause after a failed quit write`() {
        // §C.1 row 21 returns to the *originating* phase, and §A PAUSED forbids restarting a timer the
        // user cannot see — so the fact the sheet was opened from ┃┃ has to survive the closing
        // transition, which is the one place [step] does not stamp it.
        val live = context(elapsedMs = 20_000L, resultsWritten = 3)
        val paused = live.applied(step(live, SessionEvent.PauseRequested)!!)
        val sheet = paused.applied(step(paused, SessionEvent.QuitRequested)!!)
        assertTrue(sheet.pausedBeforeSheet)

        // [step] stamps `pausedBeforeSheet` only on a transition that *leaves* the sheet open, so the
        // host has to decline to take the closing transition's value. That declining is what this
        // reads: the assignment must sit under a `closesSession` guard.
        val apply = body(host, "private fun applyTransition(")
        val assignment = apply.indexOf("pausedBeforeSheet = transition.pausedBeforeSheet")
        assertTrue("applyTransition must carry pausedBeforeSheet at all", assignment > 0)
        assertTrue(
            "the closing transition must not reset pausedBeforeSheet while the sheet is still drawn",
            apply.substring(0, assignment).takeLast(200).contains("closesSession"),
        )

        val saved = sheet.applied(step(sheet, SessionEvent.SavePartial)!!)
        assertTrue("the sheet is still drawn, so what it remembers is still true", saved.pausedBeforeSheet)

        val back = step(saved.copy(overlay = restoredOverlay), SessionEvent.QuitDismissed)!!
        assertEquals(Destination.Paused, back.destination)
        assertEquals(Overlay.PAUSED, back.overlay)
    }

    // ─── the result ◁◁ stepped over ─────────────────────────────────────────────────────────────

    @Test
    fun `stepping back deletes the result it stepped over`() {
        // §C.4: "stepping back **deletes** the previous segment's result before re-seeking, or the
        // record double-counts it." The effect was emitted by row 25 and dropped by the host, so a
        // skip-back double-tap left both attempts in the record.
        val applied = body(host, "private fun applyTransition(")
        assertTrue(
            "DeleteResult must reach the write queue, not a no-op arm",
            !applied.contains("is SessionEffect.DeleteResult -> Unit"),
        )

        val writes = body(host, "private fun runWrites(")
        val arm = writes.substringAfter("is SessionEffect.DeleteResult ->", "")
        assertTrue("runWrites must handle DeleteResult", arm.isNotBlank())
        assertTrue(
            "the stepped-over row is deleted through the repository: $arm",
            arm.contains("repository.deleteResult("),
        )
    }

    // ─── the one screen Back could not leave ────────────────────────────────────────────────────

    @Test
    fun `Back leaves a session that failed to load`() {
        // The player is immersive and chromeless, and `DECISIONS.md` §Q6 gives `RoutineGone`,
        // `SessionGone`, `StoreFull` and `Rejected` a message and **no** もう一度 — so with no arm here
        // the only screen left was a dead end: `onQuit()` dispatches, `context()` is null with no
        // timeline, and the dispatch returns before deciding anything.
        val back = body(host, "fun onBack(")
        val arm = back.substringAfter("loadFault != null ->", "")
        assertTrue("Back must have an arm for a failed load", arm.isNotBlank())
        assertTrue("a failed load leaves the player: $arm", arm.trimStart().startsWith("leaveToHome()"))
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────────────────────

    private fun context(
        elapsedMs: Long,
        resultsWritten: Int = 0,
    ) = SessionContext(
        timeline = compiledCircuit(),
        elapsedMs = elapsedMs,
        nowWallMs = 1_000_000L,
        overlay = Overlay.NONE,
        autoAdvance = false,
        resultsWritten = resultsWritten,
        pausedForMs = 0L,
        pausedBeforeSheet = false,
    )

    /**
     * The context `SessionController.applyTransition` leaves behind — including its one asymmetry:
     * a transition that closes the session *from the sheet* does not reset `pausedBeforeSheet`,
     * because the sheet is still on screen until the write answers and may stay.
     */
    private fun SessionContext.applied(t: SessionTransition) = copy(
        timeline = t.timeline,
        overlay = t.overlay,
        pausedBeforeSheet = if (overlay == Overlay.QUIT_SHEET && closesSession(t.effects)) {
            pausedBeforeSheet
        } else {
            t.pausedBeforeSheet
        },
    )

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    private val host: String by lazy { source("ui/gym/session/SessionHost.kt") }

    /**
     * The overlay the host restores when a quit-sheet write fails, read out of the failure branch
     * itself — one hop through a helper, like [resolves].
     *
     * Read rather than named so that [`つづける is reachable again after a failed quit write`] is a
     * claim about the shipped host: if the branch stops restoring anything, the machine below it is
     * handed `Overlay.NONE` and row 21 returns null again, which is exactly the bug.
     */
    private val restoredOverlay: Overlay by lazy {
        val failed = body(host, "is SessionEffect.Finish ->").substringAfter("is GymWrite.Failed ->", "")
        val hops = listOf(failed) + Regex("\\b([a-z]\\w*)\\(").findAll(failed)
            .map { "private fun ${it.groupValues[1]}(" }
            .filter { host.contains(it) }
            .map { body(host, it) }
        val name = hops.firstNotNullOfOrNull { Regex("overlay = Overlay\\.(\\w+)").find(it) }
            ?.groupValues?.get(1)
        requireNotNull(name) { "a failed quit write restores no overlay at all: $failed" }
        Overlay.valueOf(name)
    }

    /**
     * True if [fragment] states [claim] itself, or calls a declaration in [host] that does.
     *
     * One hop, deliberately: a fix hidden two calls deep is a fix nobody reading the failure branch
     * can see either.
     */
    private fun resolves(fragment: String, claim: String): Boolean {
        if (fragment.contains(claim)) return true
        return Regex("\\b([a-z]\\w*)\\(").findAll(fragment).any { call ->
            val header = "private fun ${call.groupValues[1]}("
            host.contains(header) && body(host, header).contains(claim)
        }
    }

    /**
     * The brace-matched body that follows [header] — `GymShellTest`'s reader, and sound for the same
     * reason: every brace in these files is balanced, and an unbalanced one throws here rather than
     * silently truncating.
     */
    private fun body(source: String, header: String): String {
        val start = source.indexOf(header)
        require(start >= 0) { "not found: $header" }
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

    private fun source(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                val candidate = File(dir, prefix + relative)
                if (candidate.exists()) return candidate.readText()
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}
