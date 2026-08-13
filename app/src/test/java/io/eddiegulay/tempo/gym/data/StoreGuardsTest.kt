package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.data.GymFault
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two guards that stand between a failed database and a lie on screen.
 *
 * Both are about the same thing: `02-data.md` §7.1's rule that an unreadable store must never render as
 * 記録はありません, and §C.4's that a routine with history cannot be silently deleted. Neither can be
 * exercised through `GymStore` in a JVM test — `SQLiteDatabase` is not there — so what is tested here is
 * the part that was actually broken: the signal that carries a corruption from the platform callback to
 * the read path, and the classification of the purge refusal.
 */
class StoreGuardsTest {

    @After
    fun reset() {
        HistoryLoss.watch { }
        HistoryLoss.clear()
    }

    @Test
    fun `a corruption discovered after construction is visible to the read path immediately`() {
        HistoryLoss.restore(raised = false)
        assertFalse(HistoryLoss.isLost)

        // What `ExerciseCorruptionHandler` does, minus the file moves. Before this signal existed the
        // handler held nothing but a Context: it quarantined the file and wrote DataStore while the
        // store's own `historyLost` field stayed false, so `writableDatabase` reopened, the seeder
        // repopulated, and a destroyed year read as Ready(emptyList()) for the rest of the process.
        HistoryLoss.raise()

        assertTrue(HistoryLoss.isLost)
    }

    @Test
    fun `raising wakes the collectors, synchronously, on the thread that discovered it`() {
        var wakeups = 0
        HistoryLoss.watch { wakeups++ }
        HistoryLoss.restore(raised = false)

        HistoryLoss.raise()

        // Setting the flag is only half of it. Flows already collecting have their last emission —
        // Ready(emptyList()) — and nothing re-runs them until a table changes, so without this the
        // history stays rendered as empty until something unrelated happens to write.
        assertEquals(1, wakeups)
        assertTrue(HistoryLoss.isLost)
    }

    @Test
    fun `a listener that throws cannot leave the flag down`() {
        HistoryLoss.watch { error("notifier exploded") }
        HistoryLoss.restore(raised = false)

        HistoryLoss.raise()

        assertTrue(HistoryLoss.isLost)
    }

    @Test
    fun `restoring the durable flag at process start does not wake anybody`() {
        var wakeups = 0
        HistoryLoss.watch { wakeups++ }

        HistoryLoss.restore(raised = true)

        // Nothing is collecting at construction, and `observeHistory`'s onStart reports the loss on
        // its own. A notify here would only fan out to no one.
        assertTrue(HistoryLoss.isLost)
        assertEquals(0, wakeups)
    }

    @Test
    fun `only an acknowledgement lowers the flag`() {
        HistoryLoss.restore(raised = true)

        // Finishing a session used to clear it, which was wrong twice: one session does not restore a
        // year, and a corruption raised mid-session would have been un-flagged by that same session's
        // finish. §E.6 keeps it raised until the user acknowledges it — `clear` is reachable from
        // `GymStore.acknowledgeHistoryLoss` and nowhere else.
        assertTrue(HistoryLoss.isLost)
        HistoryLoss.clear()
        assertFalse(HistoryLoss.isLost)
    }

    @Test
    fun `a purge refused for having history reads as 保存できませんでした, not as an unknown failure`() {
        // The local guard throws before either delete rather than letting a deferred foreign key fail
        // at COMMIT — deterministic rollback, and the same remedy-shaped fault either way: Rejected
        // carries 保存できませんでした with no もう一度, because retrying refuses it again (§Q6).
        assertEquals(GymFault.Rejected, RoutineHasHistory("r_seven_minute").toGymFault())
    }
}
