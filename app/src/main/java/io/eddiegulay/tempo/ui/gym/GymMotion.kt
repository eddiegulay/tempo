package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.GymRoute

/**
 * Which of the shell's two easings a fade takes. `SettleIn` is `LinearOutSlowInEasing`, `Snap` is
 * `FastOutLinearInEasing` — the mapping to Compose lives in `GymShell`, so this file stays Android-free.
 *
 * Two values, not a parameterised easing: `01-shell.md` §A.6 ends "Everything is `LinearOutSlowInEasing`.
 * Nothing bounces, overshoots or loops", and the only departure is the outgoing fade, which is faster
 * because leaving is faster than arriving everywhere in this app.
 */
enum class GymEase { SettleIn, Snap }

/** A scale cue on the entering page. Absent — `null` — is itself a decision; see [routeMotion]. */
data class ScaleCue(val initialScale: Float, val durationMillis: Int, val delayMillis: Int)

/**
 * One row of `01-shell.md` §A.6's motion table, as data the `AnimatedContent` spec is built from.
 */
data class RouteMotion(
    val fadeInMillis: Int,
    val fadeInDelayMillis: Int,
    val fadeOutMillis: Int,
    val fadeOutEase: GymEase,
    /** `null` means **no scale**, which is the whole content of the tab-switch row. */
    val scale: ScaleCue?,
)

/**
 * The shell's route transition, chosen from the depths either side of it and the route being entered.
 *
 * **§A.6 is a table with rows, not one spec with a direction flag.** Collapsing it into the single
 * push/pop spec — `forward = target.depth >= initial.depth`, fade 240 + `scaleIn`, always — silently
 * defeats two of its rows and the reasons written beside them:
 *
 * - **tab switch — 200ms cross-fade, no scale**, because "lateral moves get no depth cue, which is
 *   what makes push/pop legible". A tab tap is depth 1 → depth 1, so it satisfies `forward` and, under
 *   the collapsed spec, arrives with the push's own 0.98 settle. Every move then carries a depth cue,
 *   and a cue every move carries is not a cue.
 * - **entering the player — 320ms fade**, the longest arrival in the feature. 支度 is the threshold of
 *   a workout and it is the one page the shell takes its time over; at the generic 240 it enters like
 *   a routine detail.
 *
 * The remaining rows are unchanged: a push settles in from 0.98, a pop settles out from 1.02, and that
 * is the only direction signal in the shell — there is no horizontal slide anywhere, because a slide
 * implies a spatial arrangement these pages do not have.
 *
 * *Rejected* — treating any equal-depth move as lateral. `replaceTop` swaps a ladder rung for its
 * sibling at depth 3 or 4, which is a move *within* a hierarchy and reads correctly with the push cue.
 * Lateral means the tab bar, and the tab bar only ever swaps roots, so the test is both depths being 1.
 *
 * *Rejected* — giving the player row a scale as well. The table says "320ms fade" where the push row
 * says "240ms fade + `scaleIn(0.98f)`"; the omission is the specification. Its outgoing fade keeps the
 * shell's standard 90ms `Snap`, since only the *entering* half of that row is specified and leaving
 * fast is the house rule.
 *
 * The other three rows of the table are not route transitions and live elsewhere in `GymShell`:
 * mode dialog → shell is `Modifier.gymEntrance()`, tab bar hide/show is the bar's `AnimatedVisibility`
 * (which is also the "bar leaves over 160ms" half of the player row), and shell → launcher Home is the
 * outer layer's swap in `TempoApp`.
 *
 * Pure and Android-free so every row is asserted in `GymMotionTest` — a duration inside a
 * `transitionSpec` lambda is otherwise only observable by eye, which is how the table got collapsed
 * in the first place.
 *
 * @param initialDepth `stack.size` before the move.
 * @param targetDepth `stack.size` after it.
 * @param targetRoute the route being entered — `stack.last()` after the move.
 */
fun routeMotion(initialDepth: Int, targetDepth: Int, targetRoute: GymRoute): RouteMotion = when {
    targetRoute is GymRoute.Session ->
        RouteMotion(320, 0, 90, GymEase.Snap, scale = null)

    initialDepth == 1 && targetDepth == 1 ->
        // A true cross-fade: both halves take the same 200ms and the same easing, so nothing dips.
        RouteMotion(200, 0, 200, GymEase.SettleIn, scale = null)

    else -> RouteMotion(
        fadeInMillis = 240,
        fadeInDelayMillis = 30,
        fadeOutMillis = 90,
        fadeOutEase = GymEase.Snap,
        scale = ScaleCue(
            initialScale = if (targetDepth >= initialDepth) 0.98f else 1.02f,
            durationMillis = 280,
            delayMillis = 30,
        ),
    )
}
