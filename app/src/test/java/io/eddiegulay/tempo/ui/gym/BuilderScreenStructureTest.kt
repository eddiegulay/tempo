package io.eddiegulay.tempo.ui.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Facts about the authoring pages' **composable bodies** — the half no value can answer.
 *
 * Four of the five claims below are the ones `04-library-records.md` §3 is emphatic about, and every
 * one of them is structural: how many times the page writes, which flag arms the discard prompt, which
 * actions a station row exposes, and whether the picker can reach the database at all. None changes any
 * value [builderTitle], [stationValueLabel] or [prospectiveClash] returns, which is exactly why they
 * would ship past a green [BuilderScreenTest].
 *
 * **Why the source text and not the composition.** This module's unit-test classpath is `junit` and
 * nothing else (`app/build.gradle.kts`): no Robolectric, no `compose-ui-test`, and `00-plan.md` §2
 * forbids adding either. `LibraryIndexScreenStructureTest` set the precedent and this borrows its
 * brace-matching reader. It asserts *presence of a call in a named declaration*, never formatting, so
 * reindenting or renaming a local leaves it green.
 */
class BuilderScreenStructureTest {

    private val builder: String by lazy { readSource("ui/gym/BuilderScreen.kt") }
    private val picker: String by lazy { readSource("ui/gym/StationPickerScreen.kt") }

    @Test
    fun `the builder writes exactly once, and only from the tap that says so`() {
        // An autosaving builder inserts a `routine_version` per keystroke, and every past session —
        // which pins its version id forever — starts disagreeing with the routine it came from
        // (`00-plan.md` §2 row 10). One call site, inside the one method 保存 reaches.
        assertEquals(
            "saveRoutine must be called exactly once in the whole page",
            1,
            Regex("\\.saveRoutine\\(").findAll(builder).count(),
        )
        assertTrue(
            "the single call belongs to BuilderDraftHolder.save",
            declarationBody(builder, "fun save(repository: GymRepository)").contains("repository.saveRoutine("),
        )
    }

    @Test
    fun `the save is guarded by canSave and by its own in-flight flag`() {
        // `canSave` re-asserts the predicate the header drew the button from — the flow behind it may
        // have moved since that frame — and it carries `saving`, so a double tap is one version.
        val save = declarationBody(builder, "fun save(repository: GymRepository)")

        assertTrue(save.contains("canSave(current.draft, current.saving, current.loaded)"))
        assertTrue("a failed write must leave the draft untouched", save.contains("is GymWrite.Failed"))
    }

    @Test
    fun `the discard prompt is armed by isDirty and nothing else`() {
        // `01-shell.md` §198 registers `BackHandler(enabled = isDirty)`. A false negative here is not a
        // missing dialog — it is a back press that silently discards the user's work — and a second,
        // page-local notion of dirtiness is how the two come to disagree.
        val body = declarationBody(builder, "fun BuilderScreen(")

        assertTrue("dirtiness must come from the pure isDirty", body.contains("isDirty(ready.draft, ready.original)"))
        assertTrue("the handler is enabled by it", body.contains("BackHandler(enabled = dirty)"))
        // Once, in the page's own body. A second derivation is how the prompt and the pop come to
        // disagree about whether there is anything to lose.
        assertEquals("dirtiness is derived once", 1, Regex("isDirty\\(").findAll(body).count())
    }

    @Test
    fun `a station row exposes every move as a custom action`() {
        // §3: "**This is the single most important a11y item on the page**". A drag is invisible to
        // TalkBack, so the four items are declared on the row itself (`00-plan.md` §4.1 rule 4).
        val row = declarationBody(builder, "private fun StationRow(")

        assertTrue(row.contains("customActions = actions"))
        for (label in listOf("上へ動かす", "下へ動かす", "編集", "削除")) {
            assertTrue("the row owes a $label action", row.contains("CustomAccessibilityAction(\"$label\")"))
        }
    }

    @Test
    fun `the three states of an edit never share a composable`() {
        // Loading is not empty is not failed (`00-plan.md` §4.1 rule 1). An unread routine must never
        // render a builder: saving then writes a half-empty draft over a real routine, which is the one
        // bug on this page that cannot be undone.
        val body = declarationBody(builder, "fun BuilderScreen(")

        assertTrue(body.contains("FaultPanel("))
        assertTrue(body.contains("BuilderLoadingState()"))
        assertTrue(body.contains("BuilderBody("))
    }

    @Test
    fun `the picker never touches the database`() {
        // §3's Data out is one line: "returns a `Station` to the draft". The whole authoring flow has
        // exactly one write and it is the builder's 保存.
        assertTrue("the picker must not call the repository", !picker.contains("repository"))
        assertTrue("the picker must not save a routine", !picker.contains("saveRoutine"))
        assertTrue("the picker writes to the draft", picker.contains("holder.applyStation("))
    }

    @Test
    fun `the clash is announced by the live node, not by the line that is born speaking`() {
        // §3 asks a warning line to announce itself when the user reorders into one. `WarningLine` is
        // composed only when the map has an entry, so on every reorder it is *born* carrying its text
        // — and `MoveAnnouncer`'s own KDoc states the rule verbatim. A `liveRegion` on it is a claim
        // the node cannot keep, which is worse than none: the next agent reads it as delivered.
        val warning = declarationBody(builder, "private fun WarningLine(")
        val announcer = declarationBody(builder, "private fun MoveAnnouncer(")
        val list = declarationBody(builder, "private fun StationList(")

        assertTrue("WarningLine must not claim to announce", !warning.contains("liveRegion"))
        assertTrue("the announcer is the one that speaks", announcer.contains("liveRegion = LiveRegionMode.Polite"))
        // Every reorder path — the drop and both TalkBack actions — routes through it.
        assertEquals(
            "all three move paths announce through moveAnnouncementWith",
            3,
            Regex("moveAnnouncementWith\\(").findAll(list).count(),
        )
        assertTrue(
            "the clash is read off the order the move produced",
            list.contains("warningsAfterMove("),
        )
    }

    @Test
    fun `every wheel merges the value it was opened on`() {
        // `DECISIONS.md` §Q21: `TempoValueWheel` seeds row 0 for an absent value, so a wheel that
        // cannot show what it is editing rewrites it on unfold. Both wheel hosts take the merge.
        assertTrue(
            "the builder's rows merge",
            declarationBody(builder, "private fun WheelRow(").contains("mergedWheelOptions(options, selected, labelFor)"),
        )
        assertTrue(
            "the picker's wheel merges",
            declarationBody(picker, "private fun ValueWheel(").contains("mergedWheelOptions(options, selected, labelFor)"),
        )
    }

    @Test
    fun `the history line is on screen wherever 保存 is`() {
        // §3 edge case 8 puts it "under 保存" and gives the reason outright: "The user should know
        // editing is safe *before* they press it." 保存 is pinned in the header, so the line has to be
        // at the top of the body — below the rest and 巡数 rows it sits under the fold.
        val body = declarationBody(builder, "private fun BuilderBody(")

        assertTrue(body.contains("historyLine?.let"))
        assertTrue(
            "the line must render above the fields, not after the wheels",
            body.indexOf("historyLine?.let") < body.indexOf("NameField("),
        )
    }

    @Test
    fun `a station saved by the picker carries the note it arrived with`() {
        // `applyStation` replaces the station, so a note not handed back here is destroyed — マーフ's
        // 一マイル is the only place the routine records the distance.
        val body = declarationBody(picker, "fun StationPickerScreen(")

        assertTrue(body.contains("note = carriedNote(existing, id)"))
        assertTrue("the measure is corrected at the open too", body.contains("seededMeasure(draft.engine, existing?.measure)"))
    }

    @Test
    fun `the picker raises no discard prompt`() {
        // §3's back behaviour states the asymmetry outright "so a reviewer does not fix it": one
        // station is too small a unit for a second dialog, and the builder's prompt still guards the
        // routine as a whole.
        // The page's own body, because the file's preamble names the handler it deliberately omits.
        val body = declarationBody(picker, "fun StationPickerScreen(")

        assertTrue("back is やめる and nothing else", !body.contains("BackHandler"))
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /**
     * The declaration's body, brace-matched from its header — `LibraryIndexScreenStructureTest`'s, and
     * sound for the same reason: every brace in these files is balanced, and an unbalanced one throws
     * here rather than silently truncating.
     */
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

    /**
     * Resolve a main-source file without depending on the test's working directory, which Gradle sets
     * to the module dir but IDE runners do not always agree about.
     */
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
