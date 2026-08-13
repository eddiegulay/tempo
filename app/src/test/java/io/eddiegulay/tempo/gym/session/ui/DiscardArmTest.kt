package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.gym.session.DiscardArm
import io.eddiegulay.tempo.ui.gym.session.quitOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 記録せずに終える's second ask, now that both screens that draw it share one.
 *
 * `QuitSheet` and `SessionUnrecoverablePage` had a copy each — the same three-second window, the same
 * two sentences, the same `when` — which is exactly the shape a divergence hides in: the two screens
 * ask the user to destroy the same session, and nothing but a reader's memory kept their questions
 * identical. The window is `QuitSheet.kt`'s and the words are `strings.gymSession`'s, and this pins the
 * decision the two call sites still make for themselves.
 *
 * The **timer** is deliberately not tested here. `rememberDiscardArm` is a composable and the delay is
 * a `LaunchedEffect`; what a JVM test can check is the state machine underneath, which is the part that
 * decides whether a tap destroys a workout.
 */
class DiscardArmTest {

    @Test
    fun `with work to lose the first press arms and the second discards`() {
        val arm = DiscardArm()
        var discarded = 0

        arm.press(confirms = true) { discarded++ }
        assertTrue(arm.armed)
        assertEquals(0, discarded)

        arm.press(confirms = true) { discarded++ }
        assertFalse(arm.armed)
        assertEquals(1, discarded)
    }

    @Test
    fun `with nothing to lose the first press discards`() {
        // §A QUIT_SHEET's `NothingToSave`: at zero results a mis-tap costs nothing, so the row softens
        // to 終える and asks once. `quitOptions` is what decides that, and it stays at the call site.
        val arm = DiscardArm()
        var discarded = 0
        assertFalse(quitOptions(StringsJa, resultsWritten = 0).confirmsDiscard)

        arm.press(confirms = false) { discarded++ }
        assertFalse(arm.armed)
        assertEquals(1, discarded)
    }

    @Test
    fun `disarming is not discarding`() {
        // The window closing must never be mistaken for the second tap — that would be a three-second
        // timer deleting a workout.
        val arm = DiscardArm()
        var discarded = 0
        arm.press(confirms = true) { discarded++ }
        arm.disarm()
        assertFalse(arm.armed)
        assertEquals(0, discarded)
    }

    @Test
    fun `both screens ask the same question in the same words`() {
        // The two screens no longer *can* diverge — they read three keys rather than six literals —
        // so what is left to pin is the words themselves, and that the announcement is the label plus
        // its consequence rather than a differently-worded second sentence.
        val ja = StringsJa.gymSession
        assertEquals("本当に消しますか", ja.quitArmed)
        assertEquals("本当に消しますか、もう一度 押すと消えます", ja.quitArmedDescription)
        assertEquals("記録せずに終える、これまでの記録は消えます", ja.quitDiscardDescription)
    }

    @Test
    fun `the armed announcement and the discard consequence extend their own row's label`() {
        // Language-independent, and the property that actually matters: a user who has just read
        // 記録せずに終える must hear a sentence that starts with it, or the announcement is describing
        // some other row. It held in Japanese by transcription and now holds in English by
        // construction.
        for (s in listOf(StringsJa.gymSession, StringsEn.gymSession)) {
            assertTrue(s.quitArmedDescription.startsWith(s.quitArmed))
            assertTrue(s.quitDiscardDescription.startsWith(s.quitDiscard))
        }
    }
}
