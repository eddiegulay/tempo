package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.GymRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ◁◁ deletes the row it stepped over — the write that did not exist.
 *
 * `03-player.md` §C.1 row 25 and §C.4 both require it, `SessionMachine` emits
 * `SessionEffect.DeleteResult(ordinal)` for it, and `SessionController` applied that effect as
 * `-> Unit` with a comment saying so, because [GymRepository] had no method to call. The user-visible
 * consequence is a record with **both** attempts at a station the user stepped back and redid, or an
 * abandoned attempt left behind by a step-back followed by a quit.
 *
 * `GymStore` is a `SQLiteOpenHelper` and this module's unit-test classpath is `junit` and nothing else
 * (`00-plan.md` §2 forbids adding Robolectric), so what is asserted is what was actually missing — the
 * signature on the interface, and the shape of its implementation. Both are read where they live:
 * reflection for the contract, the source text for the transaction, following `GymApiContractTest` and
 * `LibraryIndexScreenStructureTest` respectively.
 */
class DeleteResultTest {

    @Test
    fun `the repository can delete one result, addressed by ordinal`() {
        val delete = GymRepository::class.java.methods.singleOrNull { it.name == "deleteResult" }
        assertTrue("GymRepository must declare deleteResult", delete != null)
        // (sessionId, ordinal) plus the suspend `Continuation`. The ordinal is the identity of a result
        // — it is what `replay` lands stored rows on — and nothing above the data layer holds row ids.
        assertEquals(3, delete!!.parameterCount)
        assertEquals(java.lang.Long.TYPE, delete.parameterTypes[0])
        assertEquals(Integer.TYPE, delete.parameterTypes[1])
    }

    @Test
    fun `the delete runs in the store's one write path, and announces the same tables as recordSegment`() {
        // `write(...)` is the mutex, the transaction, the fault classification and the post-commit
        // notify, all four. A delete outside it would race the `recordSegment` the next 済 issues for
        // the same `(session_id, ordinal)`, and a delete that announced fewer tables would leave a
        // history surface rendering a result the store no longer holds.
        val body = declarationBody("override suspend fun deleteResult(")

        assertTrue(
            "deleteResult must go through write(TABLE_SESSION, TABLE_SESSION_RESULT): $body",
            body.contains("TABLE_SESSION_RESULT"),
        )
        assertTrue("the row is addressed by session and ordinal", body.contains("session_id = ? AND ordinal = ?"))
        assertTrue("it deletes rather than blanking the row", body.contains("delete(TABLE_SESSION_RESULT"))
    }

    @Test
    fun `the write helper's signature is the one deleteResult uses`() {
        // The pin that makes the test above mean what it says: if `write` ever stops being the
        // mutex-and-transaction path, this fails rather than the assertion quietly checking nothing.
        assertTrue(
            source.contains("private suspend fun <T> write(vararg tables: String"),
        )
        assertTrue(declarationBody("private suspend fun <T> write(").contains("writeLock.withLock"))
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    private val source: String by lazy { readSource("gym/data/GymStore.kt") }

    /** The declaration's body, brace-matched from its header. Throws rather than truncating. */
    private fun declarationBody(header: String): String {
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

    private fun readSource(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                val candidate = File(dir, prefix + relative)
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}
