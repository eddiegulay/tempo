package io.eddiegulay.tempo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders one of the design's stroked SVG glyphs (24×24 viewbox) at an arbitrary size.
 *
 * Each glyph is a list of SVG `d` strings; we parse them with Compose's [PathParser] and stroke
 * them with round caps/joins, exactly like the prototype's `stroke-linecap/linejoin="round"`.
 */
@Composable
fun LineIcon(
    paths: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 23.dp,
    strokeWidth: Dp = 1.5.dp,
) {
    val parsed: List<Path> = remember(paths) {
        paths.map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / 24f
        val strokePx = strokeWidth.toPx()
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            val style = Stroke(
                width = strokePx / scale, // keep apparent stroke constant under the scale
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            parsed.forEach { drawPath(it, color, style = style) }
        }
    }
}

/** SVG path data for every icon used across Tempo, copied verbatim from the prototype. */
object TempoIcons {
    // Dock
    val Home = listOf("M4 11.2l8-7 8 7", "M6.2 10v8.5h11.6V10")
    val Search = listOf("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "M16.2 16.2l3.8 3.8")
    val Bell = listOf(
        "M6.2 17h11.6l-1.7-2.2v-3.8a4.1 4.1 0 0 0-8.2 0v3.8L6.2 17z",
        "M10.2 17a1.8 1.8 0 0 0 3.6 0",
    )
    val Sun = listOf(
        "M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9z",
        "M12 2.5v2", "M12 19.5v2", "M4.5 12h-2", "M21.5 12h-2",
        "M6 6L4.6 4.6", "M19.4 19.4L18 18", "M18 6l1.4-1.4", "M4.6 19.4L6 18",
    )
    val Moon = listOf("M20 13.5A8 8 0 1 1 10.5 4a6.2 6.2 0 0 0 9.5 9.5z")

    /**
     * 鍛錬. A dumbbell in three strokes — two weights and the bar between them.
     *
     * Literal rather than allusive, unlike the rest of this set, and deliberately so: the dock is
     * the one place in Tempo a glyph has to be recognised cold, with no label beside it. A more
     * characterful mark (an ensō, a mountain) was rejected twice over — the ensō is already the
     * session timer and would promise something this button does not do, and nothing else reads as
     * "exercise" at 23.dp without a caption.
     */
    val Dumbbell = listOf("M6 8v8", "M18 8v8", "M6 12h12")

    /**
     * Language. A globe: circle, equator, meridian.
     *
     * The same reasoning as [Dumbbell] — the Search header's controls are icon-only, so the mark has
     * to be recognised without a caption. A globe is the near-universal convention for this control
     * and that convention is worth more here than a more characterful mark would be. Rejected: the
     * letter pair 「あA」, which is legible only to someone who already reads one of the two scripts
     * and so fails exactly the user who most needs to find this button.
     */
    val Globe = listOf(
        "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z",
        "M3 12h18",
        "M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18z",
    )

    // Hidden-apps filter
    val Eye = listOf(
        "M2 12s3.5-6.5 10-6.5S22 12 22 12s-3.5 6.5-10 6.5S2 12 2 12z",
        "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    )
    val EyeOff = listOf(
        "M9.9 5.1A9.6 9.6 0 0 1 12 5c6.5 0 10 7 10 7a14.6 14.6 0 0 1-2.2 3",
        "M6.5 7A14.4 14.4 0 0 0 2 12s3.5 7 10 7a9.5 9.5 0 0 0 3.3-.55",
        "M10.5 10.5a2.1 2.1 0 0 0 3 3",
        "M3.5 3.5l17 17",
    )
}
