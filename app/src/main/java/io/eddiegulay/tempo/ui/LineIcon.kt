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
}
