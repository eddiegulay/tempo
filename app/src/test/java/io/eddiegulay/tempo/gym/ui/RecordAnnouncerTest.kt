package io.eddiegulay.tempo.gym.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accolade block is **announced**, not merely described — `03` §A COMPLETE's accessibility line.
 *
 * > The delayed block is `liveRegion = Polite` so it is announced when it appears rather than silently
 * > inserted below the reading position.
 *
 * The obvious reading of that sentence — put `liveRegion` on the block — cannot work, and the reason is
 * in Compose rather than in us. On the classpath this module builds against (compose ui 1.7.6, BOM
 * 2025.01.00), `AndroidComposeViewAccessibilityDelegateCompat.sendSemanticsPropertyChangeEvents` opens
 * each iteration with:
 *
 * ```kotlin
 * val oldNode = previousSemanticsNodes[id] ?: return@forEachKey
 * ```
 *
 * A semantics node that did not exist on the previous accessibility pass is skipped outright, so a live
 * region born inside an `AnimatedVisibility` reveal emits **no event at all**. The requirement is met
 * only by a node that is already there and whose `contentDescription` *changes* when the block appears.
 *
 * These are source-structural assertions because the module has no Compose test runtime — only
 * `testImplementation(libs.junit)` — and because the property under test is *where a node is declared*,
 * which is exactly what source structure records. They are the same instrument
 * `LibraryIndexScreenStructureTest` and `GymShellTest` use for the same reason.
 *
 * Every one of them fails against the pre-fix file, where `liveRegion = LiveRegionMode.Polite` sat on
 * the `Accolades` column and nothing carried it outside the reveal.
 */
class RecordAnnouncerTest {

    // ─── the live region is not the thing that appears ──────────────────────────────────────────

    @Test
    fun `the accolade column carries no live region`() {
        // It keeps its `clearAndSetSemantics` description — that is what TalkBack reads when the user
        // navigates *to* the block. What it must not claim is the ability to announce its own arrival.
        val accolades = bodyOf("Accolades")
        assertFalse(
            "Accolades still declares a liveRegion; a node that appears cannot announce",
            accolades.contains("liveRegion"),
        )
        assertTrue(
            "Accolades must still describe itself as one node for navigation",
            accolades.contains("contentDescription = description"),
        )
    }

    @Test
    fun `exactly one composable declares the polite live region`() {
        val declaring = declarations().filter { (_, body) ->
            body.contains("liveRegion = LiveRegionMode.Polite")
        }.map { it.first }.toList()
        assertEquals(listOf("AccoladeAnnouncer"), declaring)
    }

    // ─── and the node that does exists from the first frame ─────────────────────────────────────

    @Test
    fun `the announcer is composed unconditionally, above the reveal`() {
        val summary = bodyOf("RecordSummary")
        val announcer = summary.indexOf("AccoladeAnnouncer(")
        val reveal = summary.indexOf("AnimatedVisibility(")
        assertTrue("RecordSummary never composes AccoladeAnnouncer", announcer >= 0)
        assertTrue("the announcer must be declared before the block it speaks for", announcer < reveal)

        // The load-bearing assertion: same brace depth as `RecordTiles(tiles)`, which is unconditional.
        // Wrapping the announcer in `if (revealed)` — the mistake that would quietly restore the bug,
        // because the node would once again be born at reveal time — puts it one level deeper.
        assertEquals(
            "the announcer must sit at the Column's top level, not inside a condition",
            depthAt(summary, summary.indexOf("RecordTiles(tiles)")),
            depthAt(summary, announcer),
        )
    }

    @Test
    fun `the announcer says nothing until the block is revealed`() {
        val body = bodyOf("AccoladeAnnouncer")
        // Empty first, the accolade sentence after. The *change* is the announcement; a node that is
        // already speaking on its first pass is diffed against nothing and stays silent for ever.
        val flat = body.replace(Regex("\\s+"), " ")
        assertTrue(
            "the announcer must be empty before the reveal and the accolade sentence after it",
            flat.contains(
                "if (revealed && !accolades.isEmpty) accoladeSemantics(accolades, s).orEmpty() else \"\"",
            ),
        )
        assertTrue(
            "the announcer must set contentDescription, which is the property that changes",
            body.contains("contentDescription = announcement"),
        )
    }

    @Test
    fun `the announcer has a box big enough to survive semantics pruning`() {
        // `SemanticsUtils.android.kt`'s `findAllSemanticNodesRecursive` keeps a node only
        // `if (region.op(unaccountedSpace, Region.Op.INTERSECT))`, built from `touchBoundsInRoot`. An
        // empty Region intersects to false, and this node has no click to expand it to a minimum touch
        // target — so `Modifier.size(0.dp)` would be dropped from `currentSemanticsNodes` entirely and
        // could never be diffed. A zero-size live region is the same bug wearing a different hat.
        val body = bodyOf("AccoladeAnnouncer")
        assertTrue("the announcer needs a real height", body.contains(".height(1.dp)"))
        assertFalse("a zero-size node is pruned before it can announce", body.contains("0.dp"))
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    private val source: String by lazy { sourceFile("ui/gym/RecordSummary.kt").readText() }

    private fun declarations(): Sequence<Pair<String, String>> =
        Regex("(?m)^(?:private |internal |)fun ([A-Z]\\w*)\\(").findAll(source).map { match ->
            match.groupValues[1] to bodyOf(match.groupValues[1])
        }

    /** Brace depth of [index] relative to the start of [body]. */
    private fun depthAt(body: String, index: Int): Int {
        require(index >= 0) { "offset not found" }
        var depth = 0
        for (i in 0 until index) {
            when (body[i]) {
                '{' -> depth++
                '}' -> depth--
            }
        }
        return depth
    }

    /**
     * The named top-level function's body, brace-matched.
     *
     * The parameter list is stepped over with its own paren matcher rather than by taking the first
     * `{` after the header: `RecordSummary`'s `footer` parameter defaults to `{}`, and a brace matcher
     * started there returns an empty body and every assertion below passes vacuously.
     */
    private fun bodyOf(name: String): String {
        val header = Regex("(?m)^(?:private |internal |)fun $name\\(").find(source)
            ?: throw AssertionError("no top-level declaration named $name in RecordSummary.kt")
        var parens = 0
        var afterParams = -1
        for (i in header.range.last until source.length) {
            when (source[i]) {
                '(' -> parens++
                ')' -> {
                    parens--
                    if (parens == 0) {
                        afterParams = i
                        break
                    }
                }
            }
        }
        require(afterParams >= 0) { "unbalanced parameter list: $name" }
        val open = source.indexOf('{', afterParams)
        require(open >= 0) { "no body for: $name" }
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
        throw IllegalStateException("unbalanced braces reading: $name")
    }

    /**
     * Resolve a main-source path without depending on the test's working directory, which Gradle sets
     * to the module dir but IDE runners do not always agree about.
     */
    private fun sourceFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                val candidate = File(dir, prefix + relative)
                if (candidate.exists()) return candidate
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}
