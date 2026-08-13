package io.eddiegulay.tempo.ui.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Facts about `RecordsIndexScreen`'s **composable bodies** — the half of the page no value can answer.
 *
 * Four of the claims this page makes are structural and every one of them has already shipped broken
 * somewhere in this feature: a Canvas with no semantics (a blank rectangle to a screen-reader user), a
 * hardcoded colour in a draw call (invisible until somebody opens Sumi), an empty state and a fault
 * sharing a composable, and an affordance that exists in the repository and is wired to nothing. None
 * of them changes a value [recordsTiles], [recentRowCopy] or [recordsBodyState] returns, which is
 * exactly why [RecordsIndexScreenTest] cannot see them.
 *
 * **Why the source text and not the composition.** This module's unit-test classpath is `junit` and
 * nothing else (`app/build.gradle.kts`): no Robolectric, no `compose-ui-test`, and `00-plan.md` §2
 * forbids adding either. `createComposeRule` needs an emulator, which is the same "only an emulator can
 * check this" trap `00-plan.md` §4.1 tells this feature to design around. So the check is made where
 * the fact lives. It asserts presence of a call inside a **named declaration**, never formatting, so
 * reindenting, renaming a local or reordering a body all leave it green. Copied from
 * `LibraryIndexScreenStructureTest`, which set the idiom.
 */
class RecordsIndexScreenStructureTest {

    private val source: String by lazy { readSource("ui/gym/RecordsIndexScreen.kt") }

    @Test
    fun `the ink grid is a real node, not a picture`() {
        // §4: "**The grid is one `Canvas` and therefore one TalkBack node**, carrying a spoken
        // *summary*". A Canvas with no semantics is a blank rectangle to TalkBack — the whole heatmap
        // becomes unreadable, and the page's answer to "how does a blind user read a heatmap" (give
        // them the list) depends on this node being the thing that opens the list.
        val body = declarationBody("private fun InkGrid(")

        assertTrue("the grid must carry gridSemantics' summary", body.contains("gridSemantics(month, days)"))
        assertTrue("the summary must be the node's description", body.contains("contentDescription = summary"))
        assertTrue("the node is a button", body.contains("role = Role.Button"))
        assertTrue(
            "§4's own click label must be on the node",
            body.contains("onClickLabel = \"記録の一覧をひらく\""),
        )
    }

    @Test
    fun `the grid asks inkLevel, never bucketOf`() {
        // `DECISIONS.md` §Q11. `bucketOf` collapses "rest day" and "below the 40th percentile" into one
        // value and knows nothing about `usedFallback`, so a grid drawn from it paints a rest day at
        // 0.15 and ranks two training days against each other as though it had a sample. `inkLevel`
        // owns both rules; this draw loop is not allowed an opinion about which days were heavy.
        val body = declarationBody("private fun InkGrid(")

        assertTrue(body.contains("inkLevel("))
        assertTrue("the level table is `inkAlpha`'s, not a second `when` here", body.contains("inkAlpha(level)"))
        assertTrue("bucketOf must not be re-derived on the page", !body.contains("bucketOf"))
        assertTrue("nor may the page re-fold the scale", !body.contains("loadScaleOf"))
    }

    @Test
    fun `every colour in a draw call comes from LocalTempoColors`() {
        // `00-plan.md` §4.1 rule 6. A hardcoded colour in a Canvas is invisible in the light theme and
        // breaks silently the first time somebody opens Sumi, which no test that reads values can see.
        listOf("private fun InkGrid(", "private fun Sparkline(").forEach { header ->
            val body = declarationBody(header)
            assertTrue("$header must read LocalTempoColors", body.contains("LocalTempoColors.current"))
            assertTrue("$header must not construct a Color", !body.contains("Color("))
            assertTrue("$header must not name a Material colour", !body.contains("Color."))
        }
    }

    @Test
    fun `an unlived day draws nothing and today keeps its ring`() {
        // §4 edge case 3: "Days *after* today draw nothing at all — no faint placeholder. An unlived
        // day is not an unfilled one." The ring is drawn *before* the `continue`, so the last week of
        // the current month still shows where today is.
        val body = declarationBody("private fun InkGrid(")
        val ring = body.indexOf("drawCircle(c.hair")
        val skip = body.indexOf("if (date.isAfter(today)) continue")

        assertTrue("the today ring must be drawn", ring >= 0)
        assertTrue("future days must be skipped", skip >= 0)
        assertTrue("the ring is drawn before the skip, or today's own ring disappears", ring < skip)
    }

    @Test
    fun `the corruption strip is dismissed, never retried`() {
        // §E.6: the flag stays raised until the user acknowledges it. `retry()` re-runs the reads and a
        // quarantined file fails them all again, so もう一度 could only ever redraw the same sentence —
        // which is why this one fault answers `DECISIONS.md` §Q6's もう一度 with a dismissal instead.
        // `acknowledgeHistoryLoss` had existed since Phase 1 with no call site anywhere in the app.
        val body = declarationBody("private fun HistoryLossPanel(")

        assertTrue("とじる is the action", body.contains("とじる"))
        assertTrue("もう一度 must not appear on the quarantine panel", !body.contains("もう一度"))
        assertTrue(
            "the message must come from faultCopy, not a second literal",
            body.contains("faultCopy(GymFault.StoreCorrupt)"),
        )

        val page = declarationBody("fun RecordsIndexScreen(")
        assertTrue(
            "the page must wire acknowledgeHistoryLoss — nothing else in the app calls it",
            page.contains("gym::acknowledgeHistoryLoss"),
        )
        assertTrue(
            "only StoreCorrupt is acknowledgeable; every other fault keeps FaultPanel and もう一度",
            page.contains("body.fault == GymFault.StoreCorrupt") && page.contains("FaultPanel("),
        )
    }

    @Test
    fun `loading, empty and failed share no composable`() {
        // `00-plan.md` §4.1 rule 1, and the defect review has caught four times in this feature. The
        // three look nearly identical on screen and mean entirely different things; one shared
        // composable is all it takes for the next branch added to it to make an unreadable store say
        // まだ 記録はありません.
        assertTrue(
            "まだ 記録はありません belongs to RecordsEmpty alone",
            declarationBody("private fun RecordsEmpty(").contains("まだ 記録はありません"),
        )
        assertEquals(
            "まだ 記録はありません must be drawn from exactly one place in the file",
            1,
            Regex("\"まだ 記録はありません\"").findAll(source).count(),
        )
        assertTrue(
            "the loading state says only 読み込み中",
            declarationBody("private fun RecordsLoading(").contains("読み込み中") &&
                !declarationBody("private fun RecordsLoading(").contains("ありません"),
        )
        assertTrue(
            "the quarantine panel must not contain an ありません-as-emptiness sentence",
            !declarationBody("private fun HistoryLossPanel(").contains("ありません"),
        )
    }

    @Test
    fun `an unloaded month is not drawn as an empty one`() {
        // §4's Loading state: "**Not a skeleton grid**: a skeleton grid is indistinguishable from a
        // month with no sessions, which is exactly the `Loadable` doctrine's forbidden confusion." So
        // the grid slot holds a word while the month is loading, and `InkGrid` is only ever reached
        // from a `Ready`.
        val body = declarationBody("private fun RecordsBodyContent(")
        val loading = body.indexOf("Loadable.Loading ->")
        val ready = body.indexOf("is Loadable.Ready -> InkGrid(")

        assertTrue("the loading arm must come before the grid", loading in 0 until ready)
        assertTrue(
            "the loading arm draws a word, not a grid",
            body.substring(loading, ready).contains("読み込み中") &&
                !body.substring(loading, ready).contains("InkGrid("),
        )
    }

    @Test
    fun `no month header is a sticky header`() {
        // `00-plan.md` §4.1 rule 3. This page has no lazy list at all — it is a `Column` with a
        // `verticalScroll`, which is what makes the rule trivially true here; the month-grouped list is
        // `GYM.RECORDS.HISTORY`'s. Pinned so a later "make 最近 lazy" edit has to face the rule.
        // The call form, not the word: the page's own KDoc names the rule it is obeying, and a test
        // that forbade the name would forbid explaining it.
        assertTrue("no stickyHeader call anywhere on this page", !source.contains("stickyHeader("))
        assertTrue("and no lazy list to put one in", !source.contains("LazyColumn("))
    }

    @Test
    fun `no ticking value is a live region`() {
        // `00-plan.md` §4.1 rule 5. The page holds a minute clock (`rememberMinuteTime`) for the today
        // ring; announcing on every tick would steal the TTS engine for a dot that moved by a day.
        assertTrue(!source.contains("liveRegion"))
    }

    @Test
    fun `the これまで tile is fed by §Q22's read, and stays a Loadable to get there`() {
        // §4's mock and §6's tile table both specify three tiles; the page shipped two, with
        // `lifetimeSessions = null` hardcoded, because `GymRepository` had no lifetime read.
        // `DECISIONS.md` §Q22 added `summary()` and `GymViewModel.lifetimeSummary` exposes it —
        // subscribing performs the read, so there is no effect for this page to forget.
        val page = declarationBody("fun RecordsIndexScreen(")
        assertTrue("the page must subscribe to the lifetime read", page.contains("gym.lifetimeSummary"))
        assertTrue("…and hand it down whole", page.contains("lifetime = lifetime"))

        val body = declarationBody("private fun RecordsBodyContent(")
        assertTrue(
            "the tile takes the read's value, never a hardcoded null",
            body.contains("lifetimeSessions = lifetime.valueOrNull()?.sessions"),
        )
        assertTrue("nothing may pin the tile shut again", !body.contains("lifetimeSessions = null"))
        assertTrue(
            "and it must arrive as a Loadable, not an Int the caller already unwrapped",
            source.contains("lifetime: Loadable<LifetimeSummary>"),
        )
    }

    @Test
    fun `the 週ごと block tells a wait apart from a failure`() {
        // §4 sanctions omission for **failure**: "Chart preview failed — the 週ごと block is omitted."
        // The block used to be `weekly?.takeIf { it.isNotEmpty() }?.let`, which drew Loading, Failed and
        // Ready(empty) as the same picture — nothing — so the section popped into a scrolling column
        // when the read landed and shifted 最近 down under the user's finger. The grid slot one screen
        // up already had its own 読み込み中; this is the same fix on the same page.
        val body = declarationBody("private fun RecordsBodyContent(")

        assertTrue("the block branches on the Loadable", body.contains("when (weekly)"))
        assertTrue("a read in flight draws its own wait", body.contains("SparklineLoading()"))
        assertTrue("a failed preview is still omitted", body.contains("is Loadable.Failed -> Unit"))
        assertTrue(
            "the wait must not share the sparkline's composable",
            source.contains("private fun SparklineLoading()"),
        )
        assertTrue(
            "and it is the sparkline's own height, or the column moves anyway",
            declarationBody("private fun SparklineLoading()").contains("height(SPARKLINE_HEIGHT)"),
        )
    }

    @Test
    fun `the pager label is asked with the month the page is showing`() {
        // Two different Junes: `monthPagerLabel` returned a bare 六月 on the grounds that the year was
        // in the subtitle, and the subtitle is `recordsSubtitle(now)` — the clock, not the displayed
        // month. The label now takes the current month to compare against, and all three call sites
        // (the two arrows and the label between them) must pass it or the bug is half-fixed.
        val pager = declarationBody("private fun MonthPager(")

        assertEquals(
            "every label in the pager is year-aware",
            3,
            Regex(Regex.escape("monthPagerLabel(")).findAll(pager).count(),
        )
        assertEquals(
            "…and each is given the current month",
            3,
            Regex(Regex.escape("currentMonth)")).findAll(pager).count(),
        )
        assertTrue(
            "the page hands MonthPager the clock's month",
            declarationBody("private fun RecordsBodyContent(").contains("currentMonth = currentMonth"),
        )
    }

    @Test
    fun `the quarantine panel discloses the fault's second source`() {
        // `StoreCorrupt` arrives two ways — the quarantine flag, and any query throwing
        // `SQLiteDatabaseCorruptException` mapped by `DbSupport` with no flag ever raised. The page
        // selects this panel on the fault *value*, so in the second case とじる lowers a flag that is
        // already down while §Q6's もう一度 is withheld. `GymRepository` exposes no quarantine state to
        // branch on, so the divergence is disclosed here rather than hidden until someone hits it.
        val kdoc = kdocFor("private fun HistoryLossPanel(")

        assertTrue("the panel must say the fault has two sources", kdoc.contains("two sources"))
        assertTrue("the second one must be named", kdoc.contains("SQLiteDatabaseCorruptException"))
        assertTrue("as must the repository read that would settle it", kdoc.contains("historyLost"))
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /**
     * The declaration's body, brace-matched from its header. Throws rather than silently truncating if
     * the braces ever stop balancing.
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

    /** Resolve a main-source file without depending on the test's working directory. */
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
