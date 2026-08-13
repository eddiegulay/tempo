package io.eddiegulay.tempo.ui.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Facts about `GYM.RECORDS.PR` and `GYM.RECORDS.CHARTS`'s **composable bodies** — the half no return
 * value can answer.
 *
 * Six of `04-library-records.md` §4's rules for these two pages are structural, and every one is a rule
 * about something that must *not* be there: a read-only page must not write, a chart must not be
 * tappable, no chart may animate, no draw call may name its own colour, no month or section header may
 * be sticky, and no sentence the string tables own may be restated at a second site.
 *
 * **Why the source text and not the composition.** This module's unit-test classpath is `junit` and
 * nothing else (`app/build.gradle.kts`): no Robolectric, no `compose-ui-test`, and `00-plan.md` §2
 * forbids adding either. `LibraryIndexScreenStructureTest` made the same trade for the same reason and
 * the helpers at the foot are its. Every assertion is about *presence of a call inside a named
 * declaration*, never formatting, so reindenting or renaming a local leaves them green.
 */
class RecordsPrAndChartsStructureTest {

    private val pr: String by lazy { readSource("ui/gym/RecordsPrScreen.kt") }
    private val charts: String by lazy { readSource("ui/gym/RecordsChartsScreen.kt") }

    // ─── Data out — none ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the PR page never writes, so a record you read does not promote its routine`() {
        // §4: **Data out — none.** `GymViewModel.openRoutine` bumps `touchRoutine`'s recency counter,
        // which is `GYM.HOME`'s and the library index's happy-path write — tapping a card *there* is
        // reaching for the routine. Reading your own record for シンディ must not quietly promote her
        // into よく使う. A read-only page that writes is a page whose spec line is false.
        val body = declarationBody(pr, "fun RecordsPrScreen(")

        assertTrue("a routine row pushes the detail directly", body.contains("gym.go(GymRoute.RoutineDetail("))
        assertTrue("…and never through the recency bump", !body.contains("openRoutine"))
        assertTrue(!body.contains("touchRoutine"))
        assertTrue(!body.contains("setFavourite"))
        assertTrue(!body.contains("startSession"))
    }

    @Test
    fun `the charts page never writes either`() {
        val body = declarationBody(charts, "fun RecordsChartsScreen(")

        assertTrue(!body.contains("openRoutine"))
        assertTrue(!body.contains("touchRoutine"))
        assertTrue(!body.contains("startSession"))
        assertTrue("これまでを見る is the page's one exit", body.contains("GymRoute.History()"))
    }

    // ─── Charts are not tappable ────────────────────────────────────────────────────────────────

    @Test
    fun `no canvas carries a click, a click label or a button role`() {
        // §4, stated outright: **Charts are not tappable** — no tooltips, no scrubbing, no selection.
        // "Charts are not clickable, so they carry no `Role.Button` and no click label: an a11y
        // affordance for an action that does not exist is a bug."
        listOf("fun BarCanvas(", "fun LineCanvas(", "fun VolumeCanvas(").forEach { header ->
            val body = declarationBody(charts, header)
            assertTrue("$header must not be clickable", !body.contains("clickable"))
            assertTrue("$header must not carry a click label", !body.contains("onClick"))
            assertTrue("$header must not claim to be a button", !body.contains("Role.Button"))
            assertTrue("$header is an image with a spoken summary", body.contains("Role.Image"))
            assertTrue("$header must say what it shows", body.contains("contentDescription = semantics"))
        }
    }

    @Test
    fun `no chart animates`() {
        // §4 edge case 1: charts draw their end state on the first frame. Design §10's motion table has
        // no entry for charts and a growing-bars reveal is exactly the loop the doctrine forbids.
        listOf("animateContentSize", "Crossfade", "AnimatedVisibility", "AnimatedContent", "animateFloat")
            .forEach { forbidden ->
                assertTrue("charts must not $forbidden", !charts.contains("$forbidden("))
            }
    }

    // ─── Every colour from LocalTempoColors ─────────────────────────────────────────────────────

    @Test
    fun `not one draw call names its own colour`() {
        // `00-plan.md` §4.1 rule 6 and §4 edge case 8. A hardcoded colour in a chart is invisible until
        // someone opens Sumi, which is the one theme nobody screenshots.
        assertTrue("no literal colours", !charts.contains("Color(0x"))
        assertTrue("no named Compose colours", !Regex("""Color\.[A-Z]""").containsMatchIn(charts))
        assertTrue("the canvases read the theme", charts.contains("LocalTempoColors.current"))

        // Every colour argument that reaches a primitive is a `c.` token or the parameter the caller
        // already resolved from one.
        val drawn = Regex("""draw(Rect|Line|Circle|Path)\(""").findAll(charts).count()
        assertTrue("the three canvases issue primitives", drawn >= 6)
    }

    // ─── The Loadable doctrine ──────────────────────────────────────────────────────────────────

    @Test
    fun `loading, empty and failed never share a composable on either page`() {
        // `00-plan.md` §4.1 rule 1. The three look nearly identical — a line of faint Mincho — and mean
        // three different things, which is exactly why a shared one breaks the first time somebody adds
        // a branch to it.
        listOf("fun PrLoading(", "fun PrRoutinesEmpty(", "fun PrMovementsEmpty(").forEach { header ->
            assertTrue("$header must exist", pr.contains(header))
        }
        listOf("fun ChartsLoading(", "fun ChartsEmpty(", "fun ChartLoading(", "fun ChartSuppressed(")
            .forEach { header ->
                assertTrue("$header must exist", charts.contains(header))
            }
    }

    @Test
    fun `an unreadable store raises a fault rather than an empty page`() {
        // The failure this feature's review has caught four times: 記録はありません rendered over a store
        // that could not be read. `DECISIONS.md` §Q6 keeps 記録を読めません unmistakable for it, and both
        // pages must route their `Failed` branch there.
        val prBody = declarationBody(pr, "fun RecordsPrScreen(")
        assertTrue(prBody.contains("is Loadable.Failed -> FaultPanel("))
        assertEquals(
            "both tabs raise the panel independently — a movement failure must not hide routine bests",
            2,
            Regex(Regex.escape("is Loadable.Failed -> FaultPanel(")).findAll(prBody).count(),
        )
        assertTrue(charts.contains("FaultStrip("))
    }

    @Test
    fun `the empty sentence exists once per page and is never a loading branch`() {
        // One constant, one site — counted as a *literal*, since the sentence is discussed in prose
        // wherever it is explained. A second literal is how a state's wording drifts from the state it
        // was written for.
        val literal = Regex.escape("\"まだ 記録はありません\"")
        assertEquals(1, Regex(literal).findAll(pr).count())
        assertEquals(1, Regex(literal).findAll(charts).count())
        assertTrue(pr.contains("Loadable.Loading -> PrLoading()"))
    }

    @Test
    fun `the suppression sentence is the geometry unit's and is never restated here`() {
        // 二十八日ぶん たまると 出ます belongs to `chartSuppressionCopy`, which owns the 28 alongside it.
        // Naming it in a `Text` here would be a second place for the gate's wording — and a second
        // place for its threshold to disagree.
        assertTrue(charts.contains("chartSuppressionCopy("))
        assertTrue(
            "the sentence may be quoted in prose, never drawn from a literal",
            !charts.contains("text = \"二十八日"),
        )
    }

    @Test
    fun `目安 reaches the page through chartCaption and is not spelled out here`() {
        // §4 edge case 5 and §5.4: weighted volume is labelled 目安 wherever it appears, because
        // duration stations enter it through an approximation. The word is `chartCaption`'s constant.
        assertTrue(charts.contains("chartCaption(ChartKind.VOLUME"))
        assertTrue(charts.contains("chartCaption(ChartKind.WEEKLY_SESSIONS"))
        assertTrue(charts.contains("chartCaption(ChartKind.ACTIVE_MINUTES"))
        assertTrue("目安 is not this file's string", !charts.contains("\"目安"))
    }

    // ─── Arithmetic never happens inside a DrawScope ────────────────────────────────────────────

    @Test
    fun `the volume chart's shared ceiling is decided outside the draw scope`() {
        // This file's header and `ChartGeometry.kt`'s both say it: a `DrawScope` needs a device, so a
        // number computed inside one is a number no JVM test can reach. `VolumeCanvas` used to take
        // `daily.maxOrNull()` at draw time — the one piece of arithmetic on this page, in the one place
        // it could not be checked, while `barGeometry` and `linePoints` own all the rest.
        val canvas = declarationBody(charts, "fun VolumeCanvas(")
        assertTrue("the canvas must not decide the scale", !canvas.contains("maxOrNull()"))
        assertTrue("it is handed one instead", canvas.contains("linePoints(daily, size.width, size.height, ceiling)"))
        assertTrue("and both passes share it", canvas.contains("linePoints(mean, size.width, size.height, ceiling)"))

        val chart = declarationBody(charts, "fun VolumeChart(")
        assertTrue("the ceiling is decided beside the spine", chart.contains("remember(spine) { spine.maxOrNull() }"))
        assertTrue("and passed in", chart.contains("ceiling = ceiling"))
    }

    // ─── The bottom padding belongs to the shell's rule, not the page's annotation ──────────────

    @Test
    fun `neither pushed page reserves room for a tab bar that is never on screen`() {
        // §2: `list bottom padding | 88.dp with the tab bar, 40.dp without`. `tabBarVisible(stack) =
        // stack.size == 1` (`GymNav.kt`), and both pages are only reachable pushed on top of
        // GymRoute.Records — so the bar is never up while they are, and the deeper value is 48.dp of
        // dead air under the last card. `04` §4's per-page `Tab bar — visible` line loses to the shell.
        listOf(pr, charts).forEach { source ->
            assertTrue("the depth-2 value is declared", source.contains("private val LIST_BOTTOM = 40.dp"))
            assertTrue("no list reserves the tab bar's depth", !source.contains("PaddingValues(bottom = 88.dp)"))
            assertTrue("nor does a spacer", !source.contains("Spacer(Modifier.height(88.dp))"))
        }
        assertEquals(
            "both of the PR page's lists take it",
            2,
            Regex(Regex.escape("contentPadding = PaddingValues(bottom = LIST_BOTTOM)")).findAll(pr).count(),
        )
        assertTrue(charts.contains("Spacer(Modifier.height(LIST_BOTTOM))"))
    }

    // ─── The header's citations point at things that exist ──────────────────────────────────────

    @Test
    fun `the PR page's header cites files and rules that exist`() {
        // A citation that does not resolve sends the next agent hunting for a rule to obey and, worse,
        // teaches them a rule number that means something else. `00-plan.md` §4.1 has seven rules and
        // none is "pure logic carries JUnit tests" — that is ORCHESTRATION.md's hard constraint 6 — and
        // the structure test for these two pages is RecordsPrAndChartsStructureTest.kt.
        val header = pr.substring(0, pr.indexOf("// ────"))

        assertTrue("the structure test must be named correctly", header.contains("RecordsPrAndChartsStructureTest.kt"))
        assertTrue("…and the file that does not exist must not be", !header.contains("RecordsScreenStructureTest.kt"))
        assertTrue("the pure-logic requirement is ORCHESTRATION's", !header.contains("§4.1 rule 10"))
    }

    // ─── Never stickyHeader ─────────────────────────────────────────────────────────────────────

    @Test
    fun `neither page makes a header sticky`() {
        // `00-plan.md` §4.1 rule 3: the calendar page set the precedent and `stickyHeader` is still
        // experimental foundation API. The *call* is what is forbidden — both files name the rule in
        // prose, which is the point of writing it down.
        val call = Regex("""stickyHeader\s*[({]""")
        assertTrue(!call.containsMatchIn(pr))
        assertTrue(!call.containsMatchIn(charts))
    }

    // ─── Nested taps ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the PR row is one node whose two fragments are custom actions`() {
        // §4's accessibility note: "The two tappable fragments are nested nodes with
        // `clearAndSetSemantics` on themselves and are exposed as `customActions` on the parent
        // (「この記録を見る」, 「これまでを見る」) — a nested-tappable row is otherwise unusable with
        // TalkBack."
        val card = declarationBody(pr, "fun PrRoutineCard(")
        assertTrue(card.contains("customActions = listOf("))
        assertTrue(card.contains("CustomAccessibilityAction(ACTION_SESSION)"))
        assertTrue(card.contains("CustomAccessibilityAction(ACTION_HISTORY)"))

        val fragment = declarationBody(pr, "fun PrFragment(")
        assertTrue("a fragment contributes nothing to the tree", fragment.contains("clearAndSetSemantics {}"))
        assertTrue("…and is still a real target for a finger", fragment.contains("minHeight = 48.dp"))
    }

    @Test
    fun `the movement row has no nested tap, because its second fragment is a fact`() {
        // §4's mock annotates 「both fragments tappable」 on the routine row and nowhere else; the
        // movement row's bottom line ends in いちばん上 足上げ腕立て, which is a fact, not a destination.
        val card = declarationBody(pr, "fun PrMovementCard(")
        assertTrue(!card.contains("customActions"))
        assertTrue(!card.contains("PrFragment("))
    }

    // ─── Helpers — `LibraryIndexScreenStructureTest`'s ──────────────────────────────────────────

    /** The declaration's body, brace-matched from its header. */
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
