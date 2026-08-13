package io.eddiegulay.tempo.ui.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Facts about `LibraryIndexScreen`'s **composable bodies** — the half of the page no value can answer.
 *
 * Three of the four defects this class pins are structural: a composable that was never mounted, a
 * semantics property that was never declared, and a `Text` that should never have been drawn. None of
 * them is reachable from a pure function, and none of them changes any value [routineCardCopy],
 * [sectionSemantics] or [routineMenuItems] returns — which is precisely why all three shipped past a
 * green [LibraryIndexScreenTest].
 *
 * **Why the source text and not the composition.** This module's unit-test classpath is `junit` and
 * nothing else (`app/build.gradle.kts`): no Robolectric, no `compose-ui-test`, and `00-plan.md` §2
 * forbids adding either. `createComposeRule` lives in `androidTest` and needs an emulator, which is the
 * same "only an emulator can check this" trap `00-plan.md` §4.1 rule 10 tells this feature to design
 * around. So the check is made where the fact actually lives. Reading the file is crude and it is
 * honest about being crude: it asserts *presence of a call in a named declaration*, never formatting,
 * so reindenting, renaming a local or reordering the body all leave it green.
 *
 * **Rejected:** asserting over the whole file rather than one declaration — `ResumePromptHost` appears
 * in `GymHomeScreen` and `LibraryDetailScreen` too, and a file-wide `contains` would have passed on
 * a mount sitting inside `DeleteRoutineDialog` where the prompt could never be seen. [declarationBody]
 * brace-matches so each assertion is scoped to the composable that owes it.
 */
class LibraryIndexScreenStructureTest {

    private val source: String by lazy { readSource("ui/gym/LibraryIndexScreen.kt") }

    @Test
    fun `the page draws the prompt it raises`() {
        // 始める → `startSession` → an open session → `_resumePrompt` published and the start lock held
        // deliberately (`03-player.md` §A.1). With no host on this page the prompt was raised into a
        // ViewModel nothing rendered: the tap did nothing, and `_startInFlight` stayed non-null for the
        // rest of the process, short-circuiting every later 始める in the gym. It is unreachable by
        // any other means — `dismissResumePrompt` is a button inside the prompt.
        val body = declarationBody("fun LibraryIndexScreen(")

        assertTrue(
            "LibraryIndexScreen must mount ResumePromptHost(gym); 始める cannot resolve without it",
            body.contains("ResumePromptHost(gym)"),
        )
    }

    @Test
    fun `the prompt is mounted unconditionally, not behind this page's own state`() {
        // `ResumePromptHost` early-returns on a null prompt, so the ordinary path costs one flow read.
        // A page-local gate ("only if I asked") reintroduces the undrawable state, because the prompt
        // outlives the composition that raised it — process death, or `onHomeShown` raising it while
        // this page is on top. The mount is therefore a statement of the body, not of a branch: it is
        // the last thing in the composable and sits outside `pendingDelete?.let { … }`.
        val body = declarationBody("fun LibraryIndexScreen(")
        val mount = body.lastIndexOf("ResumePromptHost(gym)")
        val tail = body.substring(mount + "ResumePromptHost(gym)".length)

        assertTrue("ResumePromptHost(gym) must be the composable's last statement", tail.isBlank())
        assertEquals(
            "ResumePromptHost(gym) must be mounted once and unconditionally",
            1,
            Regex("ResumePromptHost\\(").findAll(body).count(),
        )
    }

    @Test
    fun `section labels are headings`() {
        // `01-shell.md` :615 and :813 state the rule and `GymHomeScreen` implements it. The three
        // labels are the page's whole navigational structure; without `heading()` TalkBack cannot jump
        // 型 → 自分の型 and the user swipes through every card between them. It must be *inside*
        // `clearAndSetSemantics`, which discards anything declared elsewhere on the node.
        val semantics = declarationBody("private fun SectionHeading(")
            .substringAfter("clearAndSetSemantics")
            .substringBefore('}')

        assertTrue("SectionHeading must declare heading() inside clearAndSetSemantics", semantics.contains("heading()"))
        assertTrue(semantics.contains("sectionSemantics(label, count)"))
    }

    @Test
    fun `a section heading is one word, and the count is spoken only`() {
        // §3's mock and `01-shell.md`'s HOME mock both print the bare label; §2's token table has one
        // row for `section heading` and none for a glyph beside it. The 「よく使う 二」 that shipped
        // drew an unsourced number in an unsourced style (Gothic 11.sp ls 1.sp) and disagreed with the
        // sibling page. §3 asks for the count in the *description*, which `sectionSemantics` supplies.
        val body = declarationBody("private fun SectionHeading(")

        assertEquals("SectionHeading draws the label and nothing else", 1, Regex("\\bText\\(").findAll(body).count())
        assertTrue("no visible numeral beside a section label", !body.contains("kanjiExtended"))
        assertTrue("no second, unsourced type style on the heading row", !body.contains("Gothic"))
    }

    @Test
    fun `the KDoc discloses that a blocked start reports the wrong sentence`() {
        // The refusal path is real and its copy is wrong: `proceedWithStart` collapses every
        // `startBlock` into `GymFault.Rejected` → 保存できませんでした, while §6 :1129 carries
        // 種目が見つからないため 始められません for exactly this case. The fault mapping is
        // `GymViewModel`'s to fix; what this file owes is to say so instead of claiming, as it did,
        // that a block "surfaces as a fault strip with words".
        val kdoc = kdocFor("fun routineMenuItems(")

        assertTrue("the wrong sentence must be named", kdoc.contains("保存できませんでした"))
        assertTrue("the right sentence must be named", kdoc.contains("種目が見つからないため 始められません"))
        assertTrue(
            "the KDoc must not still claim the refusal is correctly worded",
            !kdoc.contains("surfaces as a fault strip with words"),
        )
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /**
     * The declaration's body, brace-matched from its header.
     *
     * Naive counting is sound for this file: every brace in it is balanced, including the one string
     * template (`"${'$'}{JapaneseDate.era(now)} …"`), and Kotlin has no unbalanced-brace char literal.
     * If that ever stops holding, this throws rather than silently truncating.
     */
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

    /** The `/** … */` block immediately above [header]. */
    private fun kdocFor(header: String): String {
        val start = source.indexOf(header)
        require(start >= 0) { "declaration not found: $header" }
        val open = source.lastIndexOf("/**", start)
        require(open >= 0) { "no KDoc above: $header" }
        return source.substring(open, source.indexOf("*/", open))
    }

    /**
     * Resolve a main-source file without depending on the test's working directory, which Gradle sets
     * to the module dir but IDE runners do not always agree about.
     */
    private fun readSource(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/io/eddiegulay/tempo/$relative")
            if (candidate.isFile) return candidate.readText()
            val inModule = File(dir, "src/main/java/io/eddiegulay/tempo/$relative")
            if (inModule.isFile) return inModule.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}
