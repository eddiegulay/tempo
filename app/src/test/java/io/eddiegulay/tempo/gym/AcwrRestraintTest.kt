package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The acute:chronic workload ratio is **computed and never drawn**, and this is the file that keeps it
 * that way after everyone who agreed to it has stopped reading the plan.
 *
 * `exercise-design.md` §7.4 and `04-library-records.md` §5 edge case 6 both state the rule and both
 * state the reason: the 0.8–1.3 "sweet spot" is widely cited and has substantially failed to
 * replicate, it is mathematically coupled (the acute window is inside the chronic one, so the ratio
 * correlates with itself), and its risk bands were built on arbitrary bucketing. An injury-risk
 * percentage drawn by a **launcher** would be a medical claim this app is in no position to make, and
 * the failure mode is that a user believes it. So ACWR exists as a ramp governor, suppressed entirely
 * below twenty-eight days of history, and at most as one `c.inkFaint` line of copy — never a gauge,
 * never a percentage, never a fourth chart.
 *
 * A prose rule in a planning file is enforced by whoever last read it. This is the same rule as a fact
 * the JVM can check, which is the only form of it that survives this conversation. It is deliberately
 * modelled on `GymShellTest`'s orphan check: derive the claim from the source tree rather than from a
 * list a person maintains, so the check cannot be satisfied by forgetting.
 *
 * **[`the ratio is still computed`] is the anti-vacuity half and is not decoration.** Every other test
 * here asserts an *absence*, and an absence passes trivially once the thing is gone — delete `acwr`
 * from `GymMath` and the restraint tests all go green while the governor Phase 4 needs has evaporated.
 * The presence assertion is what makes the absences mean something.
 *
 * **Comments are stripped before scanning.** The restraint deserves to be written down at the page
 * that declines to draw it — `RecordsChartsScreen`'s own KDoc is the natural place for "no, there is no
 * fourth chart, and here is why" — and a test that fails on the explanation teaches the next author to
 * delete the explanation. The rule is about what the code *reads*, not about what the file *says*. The
 * stripper is textual and will happily eat a `//` inside a string literal; every assertion here is a
 * substring search over the result, so a mangled literal cannot produce a wrong answer.
 */
class AcwrRestraintTest {

    /**
     * The three files that may name the ratio: it is defined in one, carried in one, and gated in one.
     *
     * A Phase 4 governor is a legitimate fourth entry — reading `TrainingLoad.acwr` to cap a ramp at
     * ten percent per week is exactly what §7.4 says the number is *for*. Adding a file here is
     * therefore allowed, and is meant to be a deliberate act with a reviewer attached. What no entry
     * here can ever unlock is a page: [`no page in 鍛錬 reads the ratio`] scans the whole `ui/` tree
     * independently of this list, so a governor that widens this set still cannot reach a screen.
     */
    private val computeSites = setOf("GymMath.kt", "GymModels.kt", "GymStore.kt")

    @Test
    fun `the ratio is still computed`() {
        // Without this, every assertion below is satisfiable by deletion.
        assertTrue(
            "GymMath must define acwr — the restraint is about not drawing it, not about not having it",
            code("gym/data/GymMath.kt").contains("fun acwr("),
        )
        assertTrue(
            "TrainingLoad must carry the ratio, or nothing is being withheld from anything",
            code("gym/GymModels.kt").contains("val acwr"),
        )
        assertTrue(
            "GymStore must populate it",
            code("gym/data/GymStore.kt").contains("acwr ="),
        )
    }

    @Test
    fun `no page in 鍛錬 reads the ratio`() {
        // The whole `ui/` tree, not merely `ui/gym`: 記録 is where the number would plausibly land, but
        // the rule is not "not on the records pages", it is "not on a screen". A launcher widget or a
        // Home cluster showing a workload ratio would be the same medical claim in a quieter place.
        //
        // Substring, not word-boundary, and case-insensitive: `acwr`, `ACWR`, `acwrLabel`, `AcwrGauge`
        // and `formatAcwr` are all the same violation.
        val offenders = uiSources
            .filter { (_, source) -> source.contains("acwr", ignoreCase = true) }
            .keys.sorted()

        assertEquals(
            "a page names the ratio: §7.4 forbids drawing it — no injury-risk percentage, no ratio " +
                "gauge, no fourth chart. If this is a governor rather than a display, it does not " +
                "belong under ui/. Offenders: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no page reaches the ratio positionally`() {
        // The back door, and the reason the identifier check alone is not enough. `TrainingLoad` is a
        // data class, so `val (recent, monotony, ratio, days) = load` hands a page the ratio under a
        // name of its own choosing and never types the four letters the check above greps for.
        // `component3()` is the same move spelled out.
        //
        // The receiver's name is not something a test can rely on, so the rule does not try: **a page
        // that touches training load does not destructure positionally at all.** That is a real
        // constraint and a cheap one — the whole `ui/` tree holds exactly one positional destructuring
        // today (`AppGlyph`'s two-component `entry`), and it is in a file that has never heard of load.
        // Naming the two fields these pages actually want, `monotony7d` and `historyDays`, costs a
        // reader nothing and is what both screens already do.
        // DOT_MATCHES_ALL, because a destructure wrapped across lines is the same back door as one on
        // a single line — and it is the shape ktlint produces the moment the list gets long enough to
        // matter. An earlier `[^)\n]*` here excluded newlines and so missed exactly the case a
        // four-field TrainingLoad would be written in.
        val destructure = Regex("""\bval\s*\([^)]*\)\s*=""", RegexOption.DOT_MATCHES_ALL)
        val offenders = uiSources.filter { (name, source) ->
            source.contains(".component3()") ||
                (source.contains("rainingLoad") && destructure.containsMatchIn(source))
        }.keys.sorted()

        assertEquals(
            "a page destructures training load: component3 is the ratio wearing another name. Read " +
                "the fields you mean by name. Offenders: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the ratio is confined to the files that compute it`() {
        // The enumerated half, and it catches what the `ui/` scan cannot: a pure-logic file in `gym/`
        // that turns the ratio into a string, a chip or a geometry. Nothing in `ui/` would then name
        // ACWR, every test above would pass, and a page would draw it through a helper.
        //
        // `RecordCopy.kt` and `ChartGeometry.kt` are the two this is really about: they are where a
        // number becomes copy and where a number becomes a chart, which are the two ways the ratio
        // gets on screen.
        val named = mainSources
            .filter { (_, source) -> source.contains("acwr", ignoreCase = true) }
            .keys.sorted()

        assertEquals(
            "the set of files naming ACWR changed. Adding a governor here is a deliberate act; " +
                "turning the ratio into copy or geometry is not one this feature permits",
            computeSites.sorted(),
            named,
        )
    }

    @Test
    fun `the twenty-eight day suppression is wired at the store, not only in the maths`() {
        // Two separate gates, both required, and only one of them is unit-tested elsewhere.
        // `GymMath.acwr` returns null on a spine shorter than twenty-eight days (pinned by
        // `StreakAndLoadTest`), but the spine is always twenty-eight days wide by construction — it is
        // zero-filled from a fixed window — so that gate alone never fires for a user with four days
        // of history and twenty-four zeros. The gate that actually suppresses is the store's, on
        // `historyDays`: the span the user has actually accumulated.
        //
        // This is §Q24's distinction in the other direction. There, an empty recent window was being
        // reported as an absence of history; here, a full spine of zeros must not be reported as
        // twenty-eight days of it.
        val store = code("gym/data/GymStore.kt")
        val call = store.indexOf("acwr = ")
        assertTrue("GymStore must populate TrainingLoad.acwr", call > 0)

        val line = store.substring(call, store.indexOf('\n', call).takeIf { it > 0 } ?: store.length)
        assertTrue(
            "the ratio must be suppressed below ACWR_CHRONIC_DAYS of accumulated history, not merely " +
                "below a full spine: $line",
            line.contains("historyDays >= ACWR_CHRONIC_DAYS"),
        )
        assertTrue(
            "the threshold is a named constant, so the maths and the gate cannot drift apart",
            code("gym/data/GymMath.kt").contains("const val ACWR_CHRONIC_DAYS: Int = 28"),
        )
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /** Every `.kt` under `ui/`, comment-free, keyed by file name. */
    private val uiSources: Map<String, String> by lazy { sourcesUnder("ui") }

    /** Every `.kt` in the app's main source set, comment-free, keyed by file name. */
    private val mainSources: Map<String, String> by lazy { sourcesUnder(".") }

    private fun sourcesUnder(relative: String): Map<String, String> =
        sourceFile(relative).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.name to stripComments(it.readText()) }

    private fun code(relative: String): String = stripComments(sourceFile(relative).readText())

    /**
     * Block and line comments out, everything else untouched.
     *
     * Order matters: block first, because a `//` inside a `/* */` is part of the block and removing it
     * line-wise first would leave an unterminated opener behind.
     */
    private fun stripComments(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /**
     * Resolve a main-source path without depending on the working directory, which Gradle sets to the
     * module dir and IDE runners do not always agree about. `GymShellTest`'s resolver, verbatim in
     * spirit — the same problem, and there is no reason for two answers to it.
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
