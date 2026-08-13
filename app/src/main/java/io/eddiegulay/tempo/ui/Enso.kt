package io.eddiegulay.tempo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/** The ring's full arc. A closed circle would be a circle; the missing 48° is what makes it an ensō. */
const val ENSO_SWEEP_DEGREES = 312f

/**
 * Where the brush lifts. Deliberately *not* a parameter: the gap sits in the upper right in the
 * source path, it is the only thing that tells the ring which way is up, and it is also the origin a
 * depleting ring drains from. A caller free to rotate it could quietly make two ensō on two screens
 * read as two different marks.
 */
private const val ENSO_START_ANGLE = -60f

/**
 * The broken brush ring — one arc with a gap, drawn from the prototype's ensō path.
 *
 * Home wants a still, faint mark; the gym's session player wants the same mark as its clock face,
 * depleting from the gap as the interval runs out. Both are this one arc, so it lives here rather
 * than being copied and drifting: two ensō that disagree by a degree would be visible the moment a
 * user walks from Home into a session.
 *
 * Geometry is explicit rather than derived. The original private version scaled radius and stroke
 * from `size.minDimension` by the prototype's 252-wide ratios, which is fine for one hand-placed mark
 * and wrong for a timer: the player's ring is a fixed 220.dp / 3.dp regardless of what box the phase
 * layout hands it, and a ratio would have made the stroke thicken on a tablet. [diameter] left
 * unspecified fits the ring to the box's own size, which is what the player wants and what makes its
 * call site read as the spec does.
 *
 * @param sweepAngle how much of the ring to draw, 0f..[ENSO_SWEEP_DEGREES]. See [ensoSweep].
 * @param diameter the ring's diameter *of path*, not of ink; unspecified fits the ink to the box
 *   instead. The ring is always centred in the box, so a diameter smaller than the box is how Home
 *   insets its mark without moving it.
 *
 * The two paths differ deliberately. A stroke is centred on its path, so a path of diameter *d* lays
 * down *d* + [strokeWidth] of ink. When the caller states a [diameter] it is stating the path and has
 * already chosen its own inset — Home's 202.dp inside a 252.dp box has 25.dp of slack either side —
 * so the value is used as given. When [diameter] is unspecified the caller is instead sizing a *box*
 * and expecting the mark to fit it, so the radius is inset by half the stroke: without that, a 220.dp
 * box holds 220.dp + strokeWidth of ink, the ring straddles the box edge by half a stroke on every
 * side, and since `Canvas` draws behind without clipping nothing looks cut — it just sits 1.5.dp
 * larger than the box that positions it, which is a real error once it is centred against other
 * fixed-position content.
 */
@Composable
fun Enso(
    color: Color,
    modifier: Modifier = Modifier,
    sweepAngle: Float = ENSO_SWEEP_DEGREES,
    strokeWidth: Dp = 3.dp,
    diameter: Dp = Dp.Unspecified,
) {
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val radius = if (diameter.isSpecified) {
            diameter.toPx() / 2f
        } else {
            // Half a stroke of ink hangs outside the path on every side; take it back so the mark
            // fits the box the caller sized. Clamped so a box thinner than the stroke draws nothing
            // rather than an inside-out arc.
            (size.minDimension / 2f - stroke / 2f).coerceAtLeast(0f)
        }
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = color,
            startAngle = ENSO_START_ANGLE,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * How much ring is left when [fractionRemaining] of a phase is left.
 *
 * A separate function, and not an expression inlined at the draw call, because the player feeds it
 * arithmetic it does not fully control: a segment whose planned duration is zero divides by zero, and
 * a clock read across a resume can land just outside 0..1. `NaN` reaches `drawArc` as a silently
 * blank canvas — the one failure that looks like the ring was never there. Resolving it to an empty
 * ring instead states the honest thing: a phase with no duration has no time left in it.
 */
fun ensoSweep(fractionRemaining: Float): Float {
    if (fractionRemaining.isNaN()) return 0f
    return ENSO_SWEEP_DEGREES * fractionRemaining.coerceIn(0f, 1f)
}
