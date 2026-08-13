package io.eddiegulay.tempo.gym.session.ui

import io.eddiegulay.tempo.ui.gym.session.DISCARD_ARMED_DESCRIPTION
import io.eddiegulay.tempo.ui.gym.session.DISCARD_ARMED_LABEL
import io.eddiegulay.tempo.ui.gym.session.DISCARD_DESCRIPTION
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
 * identical. The window and the words are now `QuitSheet.kt`'s, and this pins the decision the two call
 * sites still make for themselves.
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
        assertFalse(quitOptions(resultsWritten = 0).confirmsDiscard)

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
        assertEquals("本当に消しますか", DISCARD_ARMED_LABEL)
        assertEquals("本当に消しますか、もう一度 押すと消えます", DISCARD_ARMED_DESCRIPTION)
        assertEquals("記録せずに終える、これまでの記録は消えます", DISCARD_DESCRIPTION)
    }
}
