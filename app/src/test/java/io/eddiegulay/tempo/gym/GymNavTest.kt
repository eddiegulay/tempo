package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 鍛錬's whole navigation model, which is four pure functions over a list precisely so it can be
 * asserted here rather than driven on a device.
 *
 * The stakes are higher than they look for a HOME app. `MainActivity` is `singleTask`, the task root,
 * and a `category.HOME` activity with `enableOnBackInvokedCallback`, so a Back the app fails to
 * account for is not a wrong page — it is the system finishing the task, which from a launcher is a
 * black screen. Every case below is a stack the shell's always-enabled `BackHandler` must have a real
 * answer for, and the two structural invariants — the stack is never empty, and its first entry is
 * always a tab root — are what let the shell assume `stack.last()` and `stack.first().tab` exist
 * without a null check on the frame that draws the screen.
 */
class GymNavTest {

    private val Home = GymRoute.Home
    private val Library = GymRoute.Library
    private val Records = GymRoute.Records

    // ─── back ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `back at the gym root leaves the shell rather than popping into nothing`() {
        // The one stack where the gym has no answer of its own. It must say so explicitly, because the
        // caller has to run onLeaveShell + goHome; a silently-consumed Back here is a Back that does
        // nothing, which reads as a frozen app.
        assertEquals(BackOutcome.ExitShell, back(listOf(Home)))
    }

    @Test
    fun `back at a side tab root rebases to the train tab before it will consider leaving`() {
        // A user two taps into 記録 pressing Back expects to still be in the gym. Ejecting them to the
        // launcher from a side tab is the surprise this rule exists to remove — 鍛錬 is the way out,
        // and it takes two Backs to leave from anywhere that is not it.
        assertEquals(BackOutcome.Rebase(listOf(Home)), back(listOf(Library)))
        assertEquals(BackOutcome.Rebase(listOf(Home)), back(listOf(Records)))
    }

    @Test
    fun `a rebase is one press from leaving, and the second press leaves`() {
        val rebased = (back(listOf(Records)) as BackOutcome.Rebase).stack
        assertEquals(BackOutcome.ExitShell, back(rebased))
    }

    @Test
    fun `back from any depth pops exactly one route`() {
        val stack = listOf(Library, GymRoute.RoutineDetail("cindy"), GymRoute.ExerciseDetail("pullup"))
        assertEquals(
            BackOutcome.Pop(listOf(Library, GymRoute.RoutineDetail("cindy"))),
            back(stack),
        )
    }

    @Test
    fun `a deep stack pops toward its own root, never to the train root`() {
        // Rebase is a *root* rule. Popping a 型 sub-page must land back in 型, or Back would teleport
        // sideways out of the tab the user is working in.
        var stack = listOf(Library, GymRoute.RoutineDetail("murph"))
        stack = (back(stack) as BackOutcome.Pop).stack
        assertEquals(listOf(Library), stack)
        assertEquals(BackOutcome.Rebase(listOf(Home)), back(stack))
    }

    @Test
    fun `the stack is never empty, whatever back does to it`() {
        // Pressing Back until the shell gives up must never leave a frame where stack.last() throws.
        var stack = listOf(Library, GymRoute.RoutineDetail("tabata"), GymRoute.Builder("tabata"))
        repeat(6) {
            when (val outcome = back(stack)) {
                is BackOutcome.Pop -> stack = outcome.stack
                is BackOutcome.Rebase -> stack = outcome.stack
                BackOutcome.ExitShell -> Unit
            }
            assertTrue("back emptied the stack", stack.isNotEmpty())
        }
    }

    @Test
    fun `every stack back can produce still starts at a tab root`() {
        // The tab bar reads stack.first().tab and renders no selection for null. A pop that left a
        // non-root at the bottom would draw a bar with nothing lit and no way to fix it.
        var stack = listOf(Records, GymRoute.History(), GymRoute.Record("41"))
        repeat(4) {
            when (val outcome = back(stack)) {
                is BackOutcome.Pop -> stack = outcome.stack
                is BackOutcome.Rebase -> stack = outcome.stack
                BackOutcome.ExitShell -> Unit
            }
            assertNotNull("stack root lost its tab", stack.first().tab)
        }
    }

    // ─── push ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a singleTop route tapped twice does not stack`() {
        // Two taps on 設定 while the first is still animating in must not cost two Backs to undo.
        val once = push(listOf(Home), GymRoute.Settings)
        assertEquals(once, push(once, GymRoute.Settings))
    }

    @Test
    fun `singleTop compares the whole route, not its kind`() {
        // 型の中身 for a different routine is a different page and must stack; the same one must not.
        val stack = push(listOf(Library), GymRoute.RoutineDetail("cindy"))
        assertEquals(stack, push(stack, GymRoute.RoutineDetail("cindy")))
        assertEquals(3, push(stack, GymRoute.RoutineDetail("murph")).size)
    }

    @Test
    fun `the builder may stack twice because 写して作る opens one from another`() {
        // GymRoute.Builder is the single singleTop = false route. Copying a routine from inside the
        // builder pushes a second builder, and collapsing that to one would silently discard the draft
        // the user was already editing.
        val first = push(listOf(Library), GymRoute.Builder(null))
        val second = push(first, GymRoute.Builder(null))
        assertEquals(listOf(Library, GymRoute.Builder(null), GymRoute.Builder(null)), second)
    }

    @Test
    fun `push only dedupes against the top, not against the whole stack`() {
        // 種目の中身 → a routine using it → the same 種目 again is a real journey, and the second visit
        // is a page the user can Back out of into the routine they came from.
        val stack = listOf(Library, GymRoute.ExerciseDetail("pushup"), GymRoute.RoutineDetail("seven"))
        assertEquals(4, push(stack, GymRoute.ExerciseDetail("pushup")).size)
    }

    @Test
    fun `push never empties or re-roots the stack`() {
        val pushed = push(listOf(Home), GymRoute.Session("seven"))
        assertTrue(pushed.isNotEmpty())
        assertEquals(GymTab.Train, pushed.first().tab)
    }

    // ─── selectTab ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a tab tap drops whatever was stacked above it`() {
        val deep = listOf(Home, GymRoute.RoutineDetail("cindy"), GymRoute.Builder("cindy"))
        assertEquals(listOf(Records), selectTab(deep, GymTab.Records))
    }

    @Test
    fun `tapping the tab you are already in returns to its root`() {
        // The standard tab idiom, and the reason 記録 → a session → 記録 is one tap back to the index
        // rather than a Back the user has to find.
        val deep = listOf(Records, GymRoute.Record("41"))
        assertEquals(listOf(Records), selectTab(deep, GymTab.Records))
    }

    @Test
    fun `every tab root is a single-entry stack that back can still leave`() {
        GymTab.entries.forEach { tab ->
            val stack = selectTab(listOf(Home), tab)
            assertEquals(1, stack.size)
            assertEquals(tab, stack.first().tab)
        }
    }

    @Test
    fun `rootOf and the tab on the route agree in both directions`() {
        // Two independent statements of the same mapping — the enum's `tab` override and rootOf's
        // `when`. A tab whose root does not claim it renders a bar with no selection.
        GymTab.entries.forEach { tab -> assertEquals(tab, rootOf(tab).tab) }
    }

    // ─── tabBarVisible / currentTab ─────────────────────────────────────────────────────────────

    @Test
    fun `the tab bar is visible exactly at depth one`() {
        assertTrue(tabBarVisible(listOf(Home)))
        assertTrue(tabBarVisible(listOf(Library)))
        assertFalse(tabBarVisible(listOf(Home, GymRoute.Settings)))
    }

    @Test
    fun `depth is the only rule — a page cannot opt out and cannot opt in`() {
        // 設定 is not immersive and still hides the bar; the immersive builder hides it for the same
        // reason and not a second one. Per-page opt-outs are the thing this rule exists to prevent,
        // because the one page that forgets to declare it is the bug nobody finds.
        assertFalse(tabBarVisible(listOf(Home, GymRoute.Settings)))
        assertFalse(tabBarVisible(listOf(Library, GymRoute.Builder(null))))
        assertFalse(tabBarVisible(listOf(Home, GymRoute.Session("seven"))))
    }

    @Test
    fun `currentTab reads the root of the stack, not its top`() {
        // The bar keeps 鍛錬 lit while you are three deep in a routine opened from 鍛錬 — which is what
        // makes the rebase rule legible when the bar comes back.
        assertEquals(GymTab.Train, currentTab(listOf(Home, GymRoute.RoutineDetail("cindy"))))
        assertEquals(GymTab.Library, currentTab(listOf(Library, GymRoute.RoutineDetail("cindy"))))
    }

    // ─── replaceTop ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a ladder rung replaces the page it was tapped from`() {
        // 00-plan §3's page tree: "ladder taps REPLACE, never push — one back always exits". Walking
        // 膝つき腕立て → 腕立て → 片手腕立て must not cost three Backs to leave 種目の中身.
        val stack = listOf(Library, GymRoute.ExerciseIndex, GymRoute.ExerciseDetail("pushup_knee"))
        val replaced = replaceTop(stack, GymRoute.ExerciseDetail("pushup"))
        assertEquals(listOf(Library, GymRoute.ExerciseIndex, GymRoute.ExerciseDetail("pushup")), replaced)
        assertEquals(stack.size, replaced.size)
    }

    @Test
    fun `replacing a lone tab root is refused rather than re-rooting the stack`() {
        // The invariant the shell leans on hardest. A replace at depth one would put a non-root at the
        // bottom of the stack, and every rule below — rebase, the tab bar's selection, back's exit —
        // reads that entry.
        assertEquals(listOf(Home), replaceTop(listOf(Home), GymRoute.ExerciseDetail("pullup")))
    }
}
