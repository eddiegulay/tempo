package io.eddiegulay.tempo.gym

/**
 * What Back means for a given stack, as a value the caller has to handle.
 *
 * [ExitShell] is the reason this is a sealed type rather than a `List<GymRoute>?`. A null return
 * would let a caller treat "nothing to pop" as "nothing to do", and in a HOME activity that is the
 * one mistake with no recovery: a Back nobody consumes is handed to the system, which finishes the
 * task root and leaves the user staring at whatever was behind the launcher. Making the exit an
 * explicit case forces the caller to say what leaving looks like.
 *
 * [Rebase] and [Pop] carry the same payload and are still two cases, because they are two different
 * facts about the journey — one moved within the gym, the other reset it to the gym's own front door
 * — and the shell's route animation reads the depth change to decide which direction it is going.
 */
sealed interface BackOutcome {
    data class Pop(val stack: List<GymRoute>) : BackOutcome
    data class Rebase(val stack: List<GymRoute>) : BackOutcome
    data object ExitShell : BackOutcome
}

/**
 * Back, for any stack the gym can be in.
 *
 * Three cases and no fourth. Depth pops. A side tab's root falls back to the 鍛錬 root before it will
 * consider leaving, so Back is never a surprise ejection from 型 or 記録 — leaving always happens from
 * the same place, and it always takes a visible step through it first. Only the 鍛錬 root exits.
 *
 * *Rejected* — letting a side tab exit directly. It halves the presses and makes the launcher appear
 * from underneath 記録, which is a place the user never entered the gym from. Two presses is the cheap
 * half of that trade.
 */
fun back(stack: List<GymRoute>): BackOutcome = when {
    stack.size > 1 -> BackOutcome.Pop(stack.dropLast(1))
    stack.single() != GymRoute.Home -> BackOutcome.Rebase(listOf(GymRoute.Home))
    else -> BackOutcome.ExitShell
}

/**
 * Pushes a route, unless it is already the top and says it does not stack.
 *
 * The guard compares the whole route, not its type: 型の中身 for another routine is a different page
 * and must stack, while the same one twice is a double-tap on a card mid-animation and must not.
 * [GymRoute.Builder] is the single opt-out, and its own KDoc says why.
 */
fun push(stack: List<GymRoute>, route: GymRoute): List<GymRoute> =
    if (route.singleTop && stack.lastOrNull() == route) stack else stack + route

/**
 * Swaps the top route for another at the same depth.
 *
 * Exists for one rule stated in `00-plan.md` §3's page tree: a ladder rung tapped on 種目の中身
 * **replaces**, never pushes, so that walking 膝つき腕立て → 腕立て → 片手腕立て still costs exactly one
 * Back to leave. Pushing there builds a stack the user did not knowingly create out of what feels
 * like changing the subject of one page.
 *
 * A replace at depth one is refused rather than performed. The stack's first entry is the tab root
 * every other rule here reads — rebase, the bar's selection, the exit — and honouring the request
 * would quietly break all three to satisfy a call that only page-level code can make by mistake.
 */
fun replaceTop(stack: List<GymRoute>, route: GymRoute): List<GymRoute> =
    if (stack.size <= 1) stack else stack.dropLast(1) + route

/** A tab tap always returns to that tab's root, dropping whatever was stacked above it. */
fun selectTab(stack: List<GymRoute>, tab: GymTab): List<GymRoute> = listOf(rootOf(tab))

fun rootOf(tab: GymTab): GymRoute = when (tab) {
    GymTab.Train -> GymRoute.Home
    GymTab.Library -> GymRoute.Library
    GymTab.Records -> GymRoute.Records
}

/**
 * One rule, no per-page opt-outs to forget.
 *
 * *Rejected* — a `hidesTabBar` flag on [GymRoute] beside `immersive`. It reads more expressive and
 * gives every future page a chance to forget it; the page that forgets is then the one page whose bar
 * appears where it should not, and nobody finds it until a user does. Depth is already the honest
 * signal: the bar belongs to the three roots and to nothing else.
 */
fun tabBarVisible(stack: List<GymRoute>): Boolean = stack.size == 1

/**
 * Which tab owns the stack — read from its **root**, not its top.
 *
 * Sub-pages carry no tab, so a stack three deep in a routine opened from 鍛錬 still reports 鍛錬. That
 * is what makes the bar's selection correct at the moment it animates back in.
 */
fun currentTab(stack: List<GymRoute>): GymTab? = stack.first().tab
