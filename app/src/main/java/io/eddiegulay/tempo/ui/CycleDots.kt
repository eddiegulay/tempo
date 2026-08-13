package io.eddiegulay.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row of dots counting how far through a repeating cycle you are: Pomodoro blocks before the long
 * break, rounds of a circuit.
 *
 * It stays a dumb row of two colours on purpose. A progress bar would say "you are 60% of the way
 * through" — which is a lie for a cycle you can pause, skip, or reset mid-way — and an arc would
 * compete with the ensō the session player already draws above it. Discrete dots say only what is
 * true: this many done, this many not.
 *
 * The defaults are Focus's values (9.dp, 12.dp gap), so the Pomodoro face renders pixel-identically
 * to when this lived private inside `FocusScreen`. Callers that want the smaller session-player
 * geometry pass `dotSize = 6.dp` and a `pendingColor` of `c.hair`.
 *
 * **Deliberately not handled here: the overflow fallback.** Past nine dots the row stops being
 * countable at a glance and the fix is to render words instead ("三巡目 / 十二巡"). That text is the
 * caller's domain — only the caller knows the counter word — so this composable refuses to guess,
 * and [cycleDotsOverflow] is the shared predicate it refuses on. Silently shrinking the dots to fit
 * was rejected: it makes a long cycle look like a short one.
 *
 * @param total dots to draw. A non-positive total draws nothing rather than throwing: a decoration
 *   must never crash a live session reconciling a stale checkpoint.
 * @param filled dots already earned; clamped, because a caller that counts past [total] (a resumed
 *   session reconciling a stale checkpoint) should not crash a decoration.
 * @param label announced as one node. Individual dots are stripped of semantics, since "dot, dot,
 *   dot" is noise; TalkBack should hear "三巡目、五巡中" once or hear nothing at all.
 */
@Composable
fun CycleDots(
    total: Int,
    filled: Int,
    filledColor: Color,
    pendingColor: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 9.dp,
    gap: Dp = 12.dp,
    label: String? = null,
) {
    if (total <= 0) return
    val done = cycleDotsFilled(total, filled)
    Row(
        modifier = if (label == null) modifier else modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(dotSize)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < done) filledColor else pendingColor)
                    .clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Whether a cycle of [total] rounds is too long to draw as dots, and must be spoken as text
 * ("三巡目 / 十二巡") instead. The rule is `03-player.md` :172's, verbatim: past nine, use words.
 *
 * **Countability, not pixel width, is the operative constraint.** A width predicate was tried and
 * rejected: at the player's geometry it admits seventeen dots, so a twelve-round routine would draw
 * a row the spec says must be words. Ten dots that technically fit are still ten dots nobody counts
 * at a glance — the eye gives up long before the row does. The 210.dp figure in the spec prose is
 * loose corroboration rather than the rule, and arithmetically loose at that, since
 * `Arrangement.spacedBy` yields `n − 1` gaps rather than `n`.
 *
 * Public because the session player needs the same answer [CycleDots] does, and a threshold
 * hard-coded at two call sites is the kind of disagreement nobody notices until they disagree.
 */
fun cycleDotsOverflow(total: Int): Boolean = total > 9

/**
 * How many of [total] dots to fill, given a [filled] count that may be out of range.
 *
 * Extracted from the composable so the clamp is reachable from a JVM test, in the repo's usual
 * bargain — `groupByApp` and `layoutTategaki` are pure for the same reason. Asserting
 * `filled.coerceIn(0, total)` in a test would only assert the standard library; asserting *this*
 * asserts what the composable actually draws.
 *
 * `coerceIn` alone is not enough. It requires `min <= max` and throws otherwise, so a negative
 * [total] turns a decoration into a crash — which is exactly backwards, since the callers most
 * likely to pass a strange count are the ones recovering from a stale checkpoint and least able to
 * afford losing the screen.
 */
fun cycleDotsFilled(total: Int, filled: Int): Int =
    if (total <= 0) 0 else filled.coerceIn(0, total)
