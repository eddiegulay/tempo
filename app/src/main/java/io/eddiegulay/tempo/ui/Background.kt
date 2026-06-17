package io.eddiegulay.tempo.ui

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.eddiegulay.tempo.ui.theme.TempoColors
import java.util.Random

/**
 * Paints the Tempo backdrop: a top-anchored radial washi gradient (paper) or solid black (AMOLED),
 * with a faint multiplied paper-grain tile on top in paper mode.
 *
 * The prototype used an SVG `feTurbulence` filter; we approximate it with a small cached noise
 * bitmap tiled across the surface and composited with [BlendMode.Multiply] at the theme's grain
 * opacity, which gives the same subtle tooth without per-frame cost.
 */
fun Modifier.tempoBackground(colors: TempoColors): Modifier = drawWithCache {
    val grain: ImageBitmap? = if (colors.grainOpacity > 0f) noiseTile() else null

    val gradient = if (colors.isDark) {
        null
    } else {
        Brush.radialGradient(
            0.00f to colors.bgStops[0],
            0.58f to colors.bgStops[1],
            1.00f to colors.bgStops[2],
            center = Offset(size.width / 2f, 0f),
            radius = size.height * 1.15f,
        )
    }

    onDrawBehind {
        if (gradient != null) drawRect(gradient) else drawRect(colors.bgSolid)

        if (grain != null) {
            val tw = grain.width.toFloat()
            val th = grain.height.toFloat()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawImage(
                        image = grain,
                        topLeft = Offset(x, y),
                        alpha = colors.grainOpacity,
                        blendMode = BlendMode.Multiply,
                    )
                    x += tw
                }
                y += th
            }
        }
    }
}

/**
 * Frosted "wet wrinkled paper" fill for the floating dock when it overlays a sub-screen. The capsule
 * turns into a heavy-but-translucent paper panel: the list behind reads through, muted and diffused;
 * a multiplied grain pass scores it with creases; and a soft top-down sheen gives the wet highlight.
 *
 * Meant to be applied after a `clip(shape)` so the texture is masked to the capsule. Reuses the same
 * cached [noiseTile] as [tempoBackground] for a consistent paper tooth.
 */
fun Modifier.wetPaper(colors: TempoColors): Modifier = drawWithCache {
    val grain = noiseTile()
    // Heavy translucency: enough body to read as paper, sheer enough to see the content behind.
    val base = (if (colors.isDark) colors.bgSolid else colors.bgStops[1]).copy(alpha = 0.60f)
    // Wet sheen — a faint light wash fading from the top edge.
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (colors.isDark) 0.05f else 0.14f),
        1f to Color.Transparent,
    )

    onDrawBehind {
        drawRect(base)
        drawRect(sheen)

        // Wrinkle creases: the grain tile multiplied a touch heavier than the backdrop's.
        val tw = grain.width.toFloat()
        val th = grain.height.toFloat()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawImage(
                    image = grain,
                    topLeft = Offset(x, y),
                    alpha = if (colors.isDark) 0.08f else 0.14f,
                    blendMode = BlendMode.Multiply,
                )
                x += tw
            }
            y += th
        }
    }
}

/** Deterministic grayscale noise tile, generated once per surface size and cached by drawWithCache. */
private fun noiseTile(dim: Int = 140, seed: Long = 7L): ImageBitmap {
    val rnd = Random(seed)
    val pixels = IntArray(dim * dim) {
        val v = rnd.nextInt(256)
        android.graphics.Color.argb(255, v, v, v)
    }
    val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, dim, 0, 0, dim, dim)
    return bmp.asImageBitmap()
}
