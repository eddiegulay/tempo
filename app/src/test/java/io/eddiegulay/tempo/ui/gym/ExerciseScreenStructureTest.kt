package io.eddiegulay.tempo.ui.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Facts about the two exercise pages' **composable bodies** — the half no value can answer.
 *
 * Four of `04-library-records.md` §3's rules for these pages are structural, and every one of them is
 * a rule about something that must *not* be drawn or must be drawn *differently*, which no return
 * value can express: the catalogue must never get a spinner, a ladder tap must replace rather than
 * push, an unknown id must pop rather than render, and a section heading must never be sticky.
 *
 * **Why the source text and not the composition.** This module's unit-test classpath is `junit` and
 * nothing else (`app/build.gradle.kts`): no Robolectric, no `compose-ui-test`, and `00-plan.md` §2
 * forbids adding either. `LibraryIndexScreenStructureTest` made the same trade for the same reason and
 * the helpers below are its. The assertions are about *presence of a call in a named declaration*,
 * never formatting, so reindenting or renaming a local leaves them green.
 */
class ExerciseScreenStructureTest {

    private val index: String by lazy { readSource("ui/gym/ExerciseIndexScreen.kt") }
    private val detail: String by lazy { readSource("ui/gym/ExerciseDetailScreen.kt") }

    @Test
    fun `a ladder tap replaces the top of the stack and never pushes`() {
        // `00-plan.md` §3's page tree states it inline — "(ladder taps REPLACE, never push — one back
        // always exits)" — and §3's back behaviour gives the reason: walking a seven-rung ladder with
        // `go` builds a seven-deep stack and turns Back into a maze. `GymNav.replaceTop` exists for
        // this one rule and for nothing else.
        val body = declarationBody(detail, "fun ExerciseDetailScreen(")

        assertTrue(
            "a rung must navigate with replaceTop, not go",
            body.contains("gym.replaceTop(GymRoute.ExerciseDetail("),
        )
        assertTrue(
            "no rung may push another ExerciseDetail",
            !body.contains("gym.go(GymRoute.ExerciseDetail("),
        )
    }

    @Test
    fun `the index pushes into the detail, because that step is a real one`() {
        // The replace rule is the *ladder's*. Arriving from the catalogue list is an ordinary push, or
        // Back from the first exercise a user opened would leave the gym's 型 tab instead of the list.
        val body = declarationBody(index, "fun ExerciseIndexScreen(")

        assertTrue(body.contains("gym.go(GymRoute.ExerciseDetail("))
        assertTrue("the index has no replaceTop to make", !body.contains("replaceTop"))
    }

    @Test
    fun `an unknown id pops instead of rendering an error page`() {
        // §3's `UnknownId`: "**immediate pop, no error page** — there is no user-reachable path that
        // produces this; it is a programming error and should fail fast rather than render a ghost."
        // A `FaultPanel` would be wrong twice over: nothing failed, and もう一度 could only fail again.
        val body = declarationBody(detail, "fun ExerciseDetailScreen(")

        assertTrue("an unknown id must leave the page", body.contains("gym.onBack()"))
        assertTrue("…and must draw nothing", body.contains("if (exercise == null) return"))
        assertTrue("…and must not raise a fault panel for it", !body.contains("FaultPanel"))
    }

    @Test
    fun `neither page puts a spinner over the catalogue`() {
        // `00-plan.md` §2 row 6 and §3 both say it, and §3 says it twice: the catalogue is an in-memory
        // map, so these pages have no Loading state for it and "a spinner over an in-memory list would
        // be a lie about where the data is. Do not add one." 読み込み中 is legal on this page **only**
        // for the two store-backed blocks — 最高 and 使われている型 — which is why it may appear in
        // `ExerciseDetailScreen` and must not appear anywhere in the index.
        assertTrue(
            "the catalogue index has no loading state to draw",
            !index.contains("読み込み中"),
        )
        assertTrue(
            "the catalogue index has no fault state to draw either",
            !index.contains("FaultPanel") && !index.contains("FaultStrip"),
        )
        // Both 読み込み中 sites in the detail page are inside a `when` over a store `Loadable`, never
        // over the catalogue: the catalogue is read with `remember`, not collected.
        assertTrue(
            "the catalogue is a map read, not a flow",
            detail.contains("remember(exerciseId) { ExerciseCatalog.byId(exerciseId) }"),
        )
        assertEquals(
            "読み込み中 belongs to the two store-backed blocks and nothing else",
            2,
            drawn(detail, "読み込み中"),
        )
    }

    @Test
    fun `the store-backed blocks keep loading, empty and failed apart`() {
        // `00-plan.md` §4.1 rule 1. まだ やっていません and どの型にも入っていません are claims about the
        // user's history; each must be reachable from `Loadable.Ready` and from nothing else, and the
        // failure branch must reach the fault chrome instead.
        val bestsAndUsedBy = declarationBody(detail, "fun ExerciseDetailScreen(") +
            declarationBody(detail, "private fun UsedBySection(")

        for (branch in listOf("Loadable.Loading ->", "is Loadable.Failed ->", "is Loadable.Ready ->")) {
            assertEquals(
                "each Loadable must be answered in all three states: $branch",
                2,
                Regex(Regex.escape(branch)).findAll(bestsAndUsedBy).count(),
            )
        }
        assertEquals("まだ やっていません is drawn once", 1, drawn(detail, "まだ やっていません"))
        assertEquals("どの型にも入っていません is drawn once", 1, drawn(detail, "どの型にも入っていません"))
        assertEquals("both failures reach the one fault chrome", 2, Regex("FaultStrip\\(").findAll(detail).count())
    }

    @Test
    fun `no section heading is sticky`() {
        // `00-plan.md` §4.1 rule 3: month and section headers are plain items, and `stickyHeader` is
        // still experimental foundation API. The pattern headings are the only headers in a LazyColumn
        // in this unit and they are the exact shape that invites it.
        // The call, not the word: both files name the rule in a comment, which is the point of the
        // rule being written down.
        assertTrue(!index.contains("stickyHeader("))
        assertTrue(!detail.contains("stickyHeader("))
    }

    @Test
    fun `the ladder is composables, not a Canvas`() {
        // §3 edge case 7 states it and gives the reason: the ladder "needs text, taps and semantics per
        // rung", none of which survives a draw call. The spine is a Box the rungs are laid over.
        assertTrue("no Canvas on this page", !detail.contains("Canvas("))
        assertTrue(
            "the spine is a Box behind the rows",
            declarationBody(detail, "private fun LadderBlock(").contains("background(c.hair)"),
        )
        assertTrue(
            "each rung is a button with its own sentence",
            declarationBody(detail, "private fun LadderRungRow(").contains("rungSemantics(rung)"),
        )
    }

    @Test
    fun `the spine and the dots are decoration`() {
        // §3, accessibility: "The spine and dots are decorative; the parent `Row` uses
        // `clearAndSetSemantics`." Without it TalkBack stops twice between every pair of rung names on
        // nodes that say nothing.
        assertTrue(declarationBody(detail, "private fun LadderBlock(").contains("clearAndSetSemantics {}"))
        assertTrue(declarationBody(detail, "private fun LadderRungRow(").contains("clearAndSetSemantics {"))
    }

    @Test
    fun `every colour comes from LocalTempoColors`() {
        // `00-plan.md` §4.1 rule 6: not one hardcoded value in any draw call, or Sumi breaks silently.
        for (source in listOf(index, detail)) {
            assertTrue("no literal colour", !Regex("Color\\(0x").containsMatchIn(source))
            assertTrue("no named Material colour", !Regex("Color\\.[A-Z]").containsMatchIn(source))
        }
    }

    @Test
    fun `both pages read the per-exercise bests, never the ladder roll-up`() {
        // `movementBests()` emits one row per ladder family, named for the rung with the most lifetime
        // volume, with both counts summed across the family (`MovementBest`'s KDoc). Hung on a page or
        // a card headed by ONE rung it says two false things at once: 腕立て伏せ read
        // まだ やっていません for a user who trains it, and 膝つき腕立て read a total no rung earned —
        // an emptiness claim and a number, both assembled from a read that SUCCEEDED
        // (`00-plan.md` §4.1 rule 1). `exerciseBests()` is the per-movement read those numbers were
        // always computed from before the `groupBy` discarded them.
        for ((name, source) in listOf("index" to index, "detail" to detail)) {
            assertTrue("$name must read exerciseBests()", source.contains("gym.repository.exerciseBests()"))
            assertTrue(
                "$name must not hang a ladder-family roll-up on one rung",
                !source.contains("gym.repository.movementBests()"),
            )
        }
    }

    @Test
    fun `the per-exercise read exists on the repository and stays a Loadable`() {
        // The pages' fix is a repository read, and its shape is the fix: a bare List could not tell
        // 「not read yet」 from 「never performed」, which is the distinction 最高's four-state block on
        // EXERCISE_DETAIL is built out of. Reflection because `GymStore` is an SQLiteOpenHelper and
        // cannot run on this classpath — `GymApiContractTest`'s argument, for the same reason.
        val read = Class.forName("io.eddiegulay.tempo.gym.GymRepository").getMethod("exerciseBests")

        assertTrue(
            "exerciseBests must be a Loadable of MovementBest: ${read.genericReturnType.typeName}",
            read.genericReturnType.typeName.contains("Loadable") &&
                read.genericReturnType.typeName.contains("MovementBest"),
        )
    }

    @Test
    fun `もう一度 restarts the reads that failed, not only the flows`() {
        // The per-version `routineVersion` reads live inside `produceState`, and `gym.retry()` cannot
        // reach them: it re-runs the observing flows, and `GymRepository.routines` is a StateFlow over
        // a `Loadable.Ready` data class, so a successful re-query is structurally EQUAL to the held
        // value and is conflated — no emission, no key change, the producer never restarts and the
        // button is dead in exactly the case it is drawn for. A counter is a key that cannot compare
        // equal to itself.
        val page = declarationBody(detail, "fun ExerciseDetailScreen(")

        assertTrue("the page owns a retry counter", page.contains("mutableIntStateOf(0)"))
        assertTrue("…reset when the movement changes", page.contains("remember(exerciseId) { mutableIntStateOf(0) }"))
        assertTrue("…bumped by もう一度, alongside the flow retry", page.contains("gym.retry(); attempt++"))
        assertTrue("…and passed to the producer", page.contains("rememberRoutinesUsing(gym, exerciseId, attempt)"))
        assertTrue(
            "the producer must key on it, or the counter changes nothing",
            declarationBody(detail, "private fun rememberRoutinesUsing(")
                .contains("produceState<Loadable<List<RoutineSummary>>>(Loadable.Loading, library, exerciseId, attempt)"),
        )
    }

    @Test
    fun `browsing from a movement to a type writes nothing`() {
        // §3 states this page's contract as "**Data out** — none". `GymViewModel.openRoutine` writes
        // `last_touched_*` inside `write(TABLE_ROUTINE)` before navigating, which notifies
        // TABLE_ROUTINE and forces every routine-observing flow to re-query — a write and a full
        // library re-read for merely looking, and a write attempt from a read-only page on a
        // quarantined store. `LibraryDetailScreen`'s station taps already navigate plainly.
        val page = declarationBody(detail, "fun ExerciseDetailScreen(")

        // The call, not the word — the page names the rejected route in a comment, which is the point
        // of writing the rule down.
        assertTrue("no recency write from a read-only page", !page.contains("gym::openRoutine"))
        assertTrue("…by either spelling", !page.contains("gym.openRoutine("))
        assertTrue("a chip navigates and nothing else", page.contains("gym.go(GymRoute.RoutineDetail("))
    }

    @Test
    fun `the KDoc discloses that the current rung is looser than edge case 1 defines it`() {
        // §3 edge case 1 wants the hardest rung performed in the last 90 days with a set of ≥ 3 reps.
        // `movementBests()` carries neither a window nor a rep floor and there is no repository read
        // that does, so the いま marker is one rung too generous. What this file owes is to say so.
        val kdoc = kdocFor(detail, "internal fun currentRung(")

        assertTrue("the 90-day window must be named as missing", kdoc.contains("90-day"))
        assertTrue("the rep floor must be named as missing", kdoc.contains("3-rep"))
        assertTrue("the gap must be reported, not quietly closed", kdoc.contains("Reported"))
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /**
     * How many times [sentence] is *drawn*, rather than merely written.
     *
     * These pages name their own copy in KDoc — deliberately, because the argument for a sentence is
     * the sentence — so a bare `contains` count would rise every time the reasoning was documented.
     * A drawn string is one handed to [ExerciseNotice], which is the only composable that renders a
     * section-sized line on either page.
     */
    private fun drawn(source: String, sentence: String): Int =
        Regex(Regex.escape("ExerciseNotice(\"$sentence\")")).findAll(source).count()

    /** The declaration's body, brace-matched from its header — `LibraryIndexScreenStructureTest`'s. */
    private fun declarationBody(source: String, header: String): String {
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
    private fun kdocFor(source: String, header: String): String {
        val start = source.indexOf(header)
        require(start >= 0) { "declaration not found: $header" }
        val open = source.lastIndexOf("/**", start)
        require(open >= 0) { "no KDoc above: $header" }
        return source.substring(open, source.indexOf("*/", open))
    }

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
