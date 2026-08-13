package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.GymRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

/**
 * Which routes reach a page, as a fact the JVM can check.
 *
 * Phase 1 shipped `GymHomeScreen`, `LibraryIndexScreen` and `LibraryDetailScreen` fully written, fully
 * compiling and with **zero call sites** — `GymPage` was still resolving their routes to the title-only
 * placeholder, so no user could reach any of them and nothing in the build could tell. That is the one
 * class of bug a Compose-free test suite can normally not see, which is why [gymPagePlaceholderTitle]
 * exists as a pure function rather than as a `when` inside the composable: reachability becomes a
 * value, and this pins it.
 *
 * **It happened a third time anyway, to the whole session player, and this file is why.** A hand-written
 * `assertEquals("支度", …)` pinned `GymRoute.Session` *as a placeholder* while `SessionHost`, `LivePlayer`,
 * `PreparePage` and `CompletePage` all sat in the tree uncalled: the gate was green precisely because the
 * test enumerated the answer instead of deriving it. So the enumeration is no longer the whole check.
 * [`no page composable is orphaned`] walks the source tree from [GymShell] and fails on any public page
 * composable nothing reaches, and [`a route is a placeholder if and only if GymPage does not draw it`]
 * derives the placeholder set from `GymPage`'s own dispatch rather than from a list a person maintains.
 * Neither can be satisfied by writing a page and forgetting to wire it, which is the actual defect.
 *
 * The placeholder half is still pinned by value, because "an unwritten route stands under its own spec's
 * title and nothing else" is a claim about *copy* that no structure can make.
 */
class GymShellTest {

    /**
     * Every route, with one instance each — checked against [GymRoute]'s own source below, so a route
     * added in a later phase fails here until it is listed and classified.
     */
    private val routes: Map<String, GymRoute> = mapOf(
        "Home" to GymRoute.Home,
        "Library" to GymRoute.Library,
        "Records" to GymRoute.Records,
        "RoutineDetail" to GymRoute.RoutineDetail("r_cindy"),
        "Builder" to GymRoute.Builder(),
        "StationPicker" to GymRoute.StationPicker(null),
        "ExerciseIndex" to GymRoute.ExerciseIndex,
        "ExerciseDetail" to GymRoute.ExerciseDetail("e_pullup"),
        "Session" to GymRoute.Session("r_cindy"),
        "Record" to GymRoute.Record("s_1"),
        "History" to GymRoute.History(anchorMonth = YearMonth.of(2026, 8)),
        "Bests" to GymRoute.Bests,
        "Charts" to GymRoute.Charts,
        "Settings" to GymRoute.Settings,
        "Safety" to GymRoute.Safety,
    )

    @Test
    fun `the written pages are wired, not placeholders`() {
        assertNull(gymPagePlaceholderTitle(GymRoute.Home))
        assertNull(gymPagePlaceholderTitle(GymRoute.Library))
        assertNull(gymPagePlaceholderTitle(GymRoute.RoutineDetail("r_cindy")))
        // The whole player — 支度 through 記録 — is one route, and it was unreachable while this line
        // asserted the placeholder title. 始める landed on a heading and a hairline.
        assertNull(gymPagePlaceholderTitle(GymRoute.Session("r_cindy")))
        assertNull(gymPagePlaceholderTitle(GymRoute.Session("r_cindy", resume = true)))
    }

    @Test
    fun `every unwritten route still stands under its own spec's title`() {
        assertEquals("記録", gymPagePlaceholderTitle(GymRoute.Records))
        assertEquals("型を作る", gymPagePlaceholderTitle(GymRoute.Builder()))
        assertEquals("型を編集", gymPagePlaceholderTitle(GymRoute.Builder("r_cindy")))
        assertEquals("種目をえらぶ", gymPagePlaceholderTitle(GymRoute.StationPicker(null)))
        assertEquals("種目", gymPagePlaceholderTitle(GymRoute.ExerciseIndex))
        assertEquals("種目の中身", gymPagePlaceholderTitle(GymRoute.ExerciseDetail("e_pullup")))
        assertEquals("記録の中身", gymPagePlaceholderTitle(GymRoute.Record("s_1")))
        assertEquals("これまで", gymPagePlaceholderTitle(GymRoute.History(anchorMonth = YearMonth.of(2026, 8))))
        assertEquals("最高", gymPagePlaceholderTitle(GymRoute.Bests))
        assertEquals("移り変わり", gymPagePlaceholderTitle(GymRoute.Charts))
        assertEquals("設定", gymPagePlaceholderTitle(GymRoute.Settings))
        assertEquals("安全のために", gymPagePlaceholderTitle(GymRoute.Safety))
    }

    @Test
    fun `the route table above is complete`() {
        // The two structural tests are only as good as the set they range over, and that set is the one
        // thing here a person still types. Deriving it from `GymRoute.kt` means a new route cannot be
        // added without either wiring it or declaring it a placeholder.
        val declared = Regex("data (?:object|class) (\\w+)")
            .findAll(readSource("gym/GymRoute.kt"))
            .map { it.groupValues[1] }
            .toSortedSet()

        assertEquals(declared, routes.keys.toSortedSet())
    }

    @Test
    fun `a route is a placeholder if and only if GymPage does not draw it`() {
        // The two halves of the bug, as one biconditional: a route `GymPage` dispatches must not be
        // caught by the placeholder branch above it (which returns before the dispatch `when` is ever
        // reached — that is exactly how three finished pages shipped dead), and a route with no page
        // must not claim to have one.
        val drawn = Regex("GymRoute\\.(\\w+)")
            .findAll(declarationBody(shell, "private fun GymPage("))
            .map { it.groupValues[1] }
            .toSet()

        for ((name, route) in routes) {
            val placeholder = gymPagePlaceholderTitle(route)
            if (name in drawn) {
                assertNull("GymPage draws $name, so it must not be a placeholder", placeholder)
            } else {
                assertTrue("$name has no page, so it owes a placeholder title", placeholder != null)
            }
        }
    }

    @Test
    fun `no page composable is orphaned`() {
        // The check that would have caught all three regressions on the day they landed, and the one
        // that does not care what anybody remembers to add to a list: walk the call graph of `ui/gym`
        // outwards from `GymShell` and assert nothing public is left outside it. `SessionHost`,
        // `LivePlayer`, `CompletePage`, `PreparePage`, `WorkPage`, `RepsPage`, `RestPage`, `PausedPage`
        // and `QuitSheet` were **all** unreachable when this was written: the player existed and no
        // route led to it.
        //
        // Crude on purpose, and honest about it: it reads names, not types, so an overload or a
        // same-named helper in two files merges. That direction is safe — it can only ever call
        // something reachable that is not, and never the reverse, because a name absent from every body
        // in the feature is absent for real.
        val bodies = sources.values.flatMap { declarations(it).toList() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, bodies) -> bodies.joinToString("\n") }

        val reached = mutableSetOf("GymShell")
        val queue = ArrayDeque(reached)
        while (queue.isNotEmpty()) {
            val body = bodies[queue.removeFirst()] ?: continue
            for (call in Regex("\\b([A-Z][A-Za-z0-9]*)\\s*\\(").findAll(body)) {
                val name = call.groupValues[1]
                if (name in bodies && reached.add(name)) queue += name
            }
        }

        val orphans = sources.values
            .flatMap { Regex("(?m)^fun ([A-Z]\\w*)\\(").findAll(it).map { m -> m.groupValues[1] }.toList() }
            .filter { it !in reached }

        assertEquals("page composables nothing in 鍛錬 draws: $orphans", emptyList<String>(), orphans)
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    private val shell: String by lazy { readSource("ui/gym/GymShell.kt") }

    /** Every `.kt` under `ui/gym`, including `ui/gym/session`. */
    private val sources: Map<String, String> by lazy {
        sourceDir("ui/gym").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.name to it.readText() }
    }

    /** `name to body` for every top-level function in [source], private ones included. */
    private fun declarations(source: String): Sequence<Pair<String, String>> =
        Regex("(?m)^(?:private |internal |)fun ([A-Z]\\w*)\\(").findAll(source).map { match ->
            match.groupValues[1] to declarationBody(source, match.value)
        }

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

    private fun readSource(relative: String): String = sourceFile(relative).readText()

    private fun sourceDir(relative: String): File = sourceFile(relative)

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
