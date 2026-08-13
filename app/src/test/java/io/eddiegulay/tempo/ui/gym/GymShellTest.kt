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
        // Phase 2's six, all wired in the edit that wrote them.
        assertNull(gymPagePlaceholderTitle(GymRoute.Builder()))
        assertNull(gymPagePlaceholderTitle(GymRoute.Builder("r_cindy")))
        assertNull(gymPagePlaceholderTitle(GymRoute.StationPicker(null)))
        assertNull(gymPagePlaceholderTitle(GymRoute.StationPicker(2)))
        assertNull(gymPagePlaceholderTitle(GymRoute.ExerciseIndex))
        assertNull(gymPagePlaceholderTitle(GymRoute.ExerciseDetail("e_pullup")))
        // 設定 and 安全のために are pinned here rather than only by the structural test below because
        // `GYM.HOME`'s first-run line navigates straight to `Safety` and acknowledges the note on the
        // way: while that route was a placeholder, the only way to clear a permanent footnote was to
        // walk into an empty page.
        assertNull(gymPagePlaceholderTitle(GymRoute.Settings))
        assertNull(gymPagePlaceholderTitle(GymRoute.Safety))
        // Phase 3's five, and the fifth, sixth, seventh and eighth repetition of the same regression:
        // `RecordsIndexScreen`, `RecordsHistoryScreen`, `SessionDetailScreen`, `RecordsPrScreen` and
        // `RecordsChartsScreen` were all written, all compiling, and had zero call sites, while 記録,
        // 最高, 移り変わり, これまで and 記録の中身 each drew a heading and a hairline.
        assertNull(gymPagePlaceholderTitle(GymRoute.Records))
        assertNull(gymPagePlaceholderTitle(GymRoute.Record("s_1")))
        assertNull(gymPagePlaceholderTitle(GymRoute.History()))
        assertNull(gymPagePlaceholderTitle(GymRoute.History("r_cindy", YearMonth.of(2026, 8))))
        assertNull(gymPagePlaceholderTitle(GymRoute.Bests))
        assertNull(gymPagePlaceholderTitle(GymRoute.Charts))
    }

    @Test
    fun `no route is a placeholder any more`() {
        // Phase 3 emptied the list, and this is the statement of that rather than a gap left by
        // deleting the old assertions. Every route in 鍛錬 now reaches a page it can draw.
        //
        // [gymPagePlaceholderTitle] is deliberately **kept** in that state: an exhaustive `when` with
        // no `else` is what makes Phase 4's first new route a compile error rather than a page nobody
        // can reach. When one is added and its page is not written yet, it earns a title from its own
        // spec here, and this test is where the count changes.
        assertEquals(emptyList<String>(), routes.values.mapNotNull(::gymPagePlaceholderTitle))
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
    fun `the health service is mounted on the live session and nowhere higher`() {
        // `TrainingServiceMount` is not a page: it draws nothing, and what it *is* is a lifetime. It
        // starts the foreground service on entering the composition and stops it on leaving, so the
        // arm it sits on is the whole specification of when the notification exists. On the `Live` arm
        // the three endings the service owes — finish, quit and discard — are one fact, because every
        // one of them takes the screen off `Live`.
        //
        // Mounted at the shell root instead, it would satisfy the orphan check below and still be
        // wrong in the way that matters: a workout notification standing over 記録, over the library,
        // and over every screen until the user left 鍛錬 altogether.
        val page = declarationBody(shell, "private fun GymPage(")
        val root = declarationBody(shell, "fun GymShell(")

        assertTrue("GymShell must not hold the service above the player", !root.contains("TrainingServiceMount"))
        val live = page.indexOf("is SessionScreen.Live ->")
        val complete = page.indexOf("is SessionScreen.Complete ->")
        val mount = page.indexOf("TrainingServiceMount(")
        assertTrue("the player must mount the service: $page", mount > 0)
        assertTrue("...on the Live arm, after it and before Complete", mount in (live + 1) until complete)

        // The consent is the other half and belongs one arm later. `TRAINING_CONSENT_DELAY_MS` carries
        // the argument: the two moments this must not be asked at — launch, and in front of a workout —
        // leave exactly one, and it is 記録. The historical `GYM.RECORDS.SESSION_DETAIL` draws the same
        // `RecordSummary` and must never ask; it gets that by simply not being this arm.
        val consent = page.indexOf("TrainingConsentMount(")
        assertTrue("the record screen must ask for the service's permissions: $page", consent > complete)
    }

    @Test
    fun `予定に入れる is on the record screen, and only the live one`() {
        // `03-player.md` §A COMPLETE places it there. `04-library-records.md` :809 keeps it off the
        // historical page for a reason no code can infer: booking "the next one" is something you do
        // having just finished, not while reading a record from March.
        val complete = sources.getValue("CompletePage.kt")
        val detail = sources.getValue("SessionDetailScreen.kt")

        assertTrue("CompletePage must draw ScheduleNextAction: it is 予定に入れる", complete.contains("ScheduleNextAction("))
        assertTrue("SESSION_DETAIL must not", !detail.contains("ScheduleNextAction("))
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
