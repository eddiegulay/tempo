package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Four signatures that are the fix, and would otherwise be un-testable on this classpath.
 *
 * Every other gym test asserts on a pure function's answer. These four defects had no answer to assert
 * on: each was a *shape* — a type that could not express "unknown", a destination hard-coded where it
 * had to be a parameter, a name that could only be resolved from a list the routine had left, a method
 * the store implemented and the interface hid. `GymStore` is `SQLiteOpenHelper` and `GymViewModel`
 * needs a main dispatcher, so neither runs here (`CONTRIBUTING.md` forbids adding Robolectric or
 * `kotlinx-coroutines-test` to reach them), and the shape itself is what regressed. So the shape is
 * what is pinned.
 *
 * Reflection rather than a fake implementation of [GymRepository]: the interface carries thirty-odd
 * members and a stub of it would be a second place for every signature to drift — the exact failure
 * mode its KDoc already argues against.
 */
class GymApiContractTest {

    @Test
    fun `countForRoutine can say it does not know`() {
        // A bare Flow<Int> made "not read yet" and "read failed" both arrive as zero, and
        // GYM.LIBRARY.DETAIL's 削除 rendered 「やった記録はありません。完全に消えます。」 from that zero —
        // an ありません-as-emptiness claim about the user's records on the one irreversible dialog in
        // the feature (00-plan §4.1 rule 1, DECISIONS §Q6).
        val read = GymRepository::class.java.getMethod("countForRoutine", String::class.java)
        assertTrue(
            "countForRoutine must be a Loadable, not a defaulted Int: ${read.genericReturnType.typeName}",
            read.genericReturnType.typeName.contains("Loadable"),
        )

        val exposed = GymViewModel::class.java.getMethod("countForRoutine", String::class.java)
        assertTrue(
            "the ViewModel must not unwrap it either: ${exposed.genericReturnType.typeName}",
            exposed.genericReturnType.typeName.contains("Loadable"),
        )
    }

    @Test
    fun `deleting a routine takes its destination from the caller`() {
        // Hard-coded `thenSelect = GymTab.Library` rebased [Home] to [Library], so deleting a card from
        // GYM.HOME's long-press menu teleported the user to the 型 tab. 01-shell §B's actions table
        // gives that row a Navigates value for 編集 only; the tab jump is 04 §2's, for the detail page.
        for (name in listOf("archiveRoutine", "purgeRoutine")) {
            val write = GymViewModel::class.java.getMethod(name, String::class.java, GymTab::class.java)
            assertEquals(2, write.parameterCount)
        }
    }

    @Test
    fun `duplicateRoutine accepts the name its caller already holds`() {
        // An archived routine is not in `GymRepository.routines` (archived_at IS NULL), so resolving the
        // source name from that list alone answered 写して作る with 「この型は削除されています」 while
        // copying nothing — on the one action 04 §1 rule 3 keeps enabled on an archived routine.
        val duplicate =
            GymViewModel::class.java.getMethod("duplicateRoutine", String::class.java, String::class.java)
        assertEquals(2, duplicate.parameterCount)
    }

    @Test
    fun `acknowledgeHistoryLoss is reachable through the interface`() {
        // GymStore is `internal` and reached only through GymRepository, so while this lived on the
        // store alone the quarantined-database fault had no exit at all (§E.6).
        assertTrue(
            GymRepository::class.java.methods.any { it.name == "acknowledgeHistoryLoss" },
        )
        assertTrue(
            GymViewModel::class.java.methods.any { it.name == "acknowledgeHistoryLoss" },
        )
    }
}
