package io.eddiegulay.tempo.ui.gym

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pieces of the resume prompt that live **outside** its composition, and the two defects that
 * put them there.
 *
 * Both are about the same mismatch: the modal's state belongs to the ViewModel and outlives every
 * recomposition, while the composable that draws it does not — it is remounted by `GymShell`'s
 * `AnimatedContent`, by a page swap, and by whichever page happens to be beneath the dialog. Anything
 * the prompt remembered with `remember` was therefore lost at exactly the moments it mattered, and
 * anything a page assumed about being the only host was wrong for ~280ms on every transition.
 *
 * Neither can be asserted from a composable on this classpath — Compose UI needs Robolectric and the
 * gym's ViewModel needs a main dispatcher, both forbidden here — which is the same reason the rest of
 * this page's decisions are pure functions. These two are objects rather than functions only because
 * they must remember something across compositions.
 */
class ResumePromptTest {

    @After
    fun tearDown() {
        ResumePromptMount.reset()
        ResumePromptRetry.clear()
    }

    // ─── Which host draws ───────────────────────────────────────────────────────────────────────

    @Test
    fun `only the first mounted host draws, so a cross-fade cannot stack two dialogs`() {
        // GYM.HOME, GYM.LIBRARY.DETAIL and GYM.LIBRARY.INDEX all mount this host, and the shell's
        // 280ms cross-fade keeps two pages composed at once — which used to mean two AlertDialogs,
        // two scrims and one announcement read twice.
        val detail = Any()
        val home = Any()

        ResumePromptMount.mount(detail)
        assertTrue(ResumePromptMount.draws(detail))

        ResumePromptMount.mount(home)
        assertTrue("the page already drawing keeps the dialog", ResumePromptMount.draws(detail))
        assertFalse("the arriving page must not draw a second one", ResumePromptMount.draws(home))
    }

    @Test
    fun `the dialog moves to the surviving host when the one drawing it leaves`() {
        val detail = Any()
        val home = Any()
        ResumePromptMount.mount(detail)
        ResumePromptMount.mount(home)

        ResumePromptMount.unmount(detail)

        assertTrue(ResumePromptMount.draws(home))
        assertFalse(ResumePromptMount.draws(detail))
    }

    @Test
    fun `an unmounted host draws nothing, and mounting is idempotent`() {
        val home = Any()
        assertFalse(ResumePromptMount.draws(home))

        ResumePromptMount.mount(home)
        ResumePromptMount.mount(home)
        ResumePromptMount.unmount(home)

        // One unmount for one mount: a host that registered twice would otherwise survive its own
        // disposal and block every later page from ever drawing the prompt again.
        assertFalse(ResumePromptMount.draws(home))
    }

    // ─── What もう一度 re-runs ──────────────────────────────────────────────────────────────────

    @Test
    fun `the retry outlives the composition that recorded it`() {
        // The defect: `lastAction` was `remember`ed, but `ResumePromptState.fault` is not — so any
        // path that re-entered the prompt's composition with a fault already in state left もう一度
        // rendering over a button that did nothing.
        ResumePromptRetry.record(sessionId = 4L, action = ResumeRetry.Record)
        assertEquals(ResumeRetry.Record, ResumePromptRetry.forSession(4L))

        ResumePromptRetry.record(sessionId = 4L, action = ResumeRetry.Discard)
        assertEquals(ResumeRetry.Discard, ResumePromptRetry.forSession(4L))
    }

    @Test
    fun `a retry never crosses from one session to another`() {
        // A failed 捨てる on session 4 must not become a 捨てる of session 5 when the next workout is
        // interrupted. Deriving the intent from state was rejected for the same reason in the other
        // direction: the ViewModel clears `confirmingDiscard` on failure, so a failed delete and a
        // failed save are the *same* state, and guessing answers a failed delete by saving.
        ResumePromptRetry.record(sessionId = 4L, action = ResumeRetry.Discard)
        assertNull(ResumePromptRetry.forSession(5L))
    }
}
