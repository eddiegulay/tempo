package io.eddiegulay.tempo.gym

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GymViewModel`'s `init` block must come after every property it touches.
 *
 * This exists because of a real crash on entering 鍛錬, and it reads the source rather than running
 * the class because there is no other way to see it here: constructing a ViewModel needs a main
 * dispatcher, and `CONTRIBUTING.md` rules out the dependency that would supply one. `GymShellTest`
 * makes the same trade for the same reason.
 *
 * **The bug.** Kotlin runs property initialisers and `init` blocks in *declaration* order. An `init`
 * sitting above `private val _resumable = MutableStateFlow(...)` sees that field as null. The block
 * launched a coroutine, the launch returned immediately, and the continuation resumed a moment later
 * onto `_resumable.value = …` — `NullPointerException`, on the main thread, every time anyone opened
 * the gym.
 *
 * The coroutine did not cause the bug so much as hide it. A direct assignment would have thrown
 * during construction, at the line that was wrong. Deferring the touch by one dispatch moved the
 * crash away from its cause and past every compiler check, which is exactly the shape of failure a
 * structural test is worth writing for.
 */
class GymViewModelInitOrderTest {

    private val source: String by lazy {
        val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
            .first { File(it, "app/src/main/java/io/eddiegulay/tempo/gym/GymViewModel.kt").exists() }
        File(root, "app/src/main/java/io/eddiegulay/tempo/gym/GymViewModel.kt").readText()
    }

    @Test
    fun `init runs after every state property it could touch`() {
        val init = Regex("""^\s{4}init\s*\{""", RegexOption.MULTILINE).find(source)
        assertTrue("GymViewModel has no init block — if it was removed, delete this test.", init != null)

        // Every backing field the init block (or anything it calls) could plausibly assign. Matching
        // on the declaration keyword rather than a hand-kept list, so a new one is covered for free.
        val declarations = Regex("""^\s{4}private val (_\w+)\s*[:=]""", RegexOption.MULTILINE)
            .findAll(source)
            .map { it.groupValues[1] to it.range.first }
            .toList()

        assertTrue("Expected several private backing fields; found ${declarations.size}.", declarations.size > 5)

        val initAt = init!!.range.first
        val late = declarations.filter { (_, at) -> at > initAt }.map { it.first }

        assertTrue(
            "These fields are declared AFTER init, so init sees them as null: $late. " +
                "Move init below them — it belongs at the bottom of the class body.",
            late.isEmpty(),
        )
    }

    @Test
    fun `the fields the init block names are real`() {
        // Guards the test above against quietly passing because init stopped mentioning anything:
        // if these names change, the ordering assertion still holds but stops meaning what it says.
        val initBlock = source.substringAfter("    init {").substringBefore("\n    }")
        assertTrue("init no longer refreshes the resumable row.", "refreshResumable()" in initBlock)
        assertTrue("init no longer collects preferences into _prefs.", "_prefs" in initBlock)
        assertTrue("_resumable is assigned by refreshResumable.", "_resumable.value" in source)
    }
}
