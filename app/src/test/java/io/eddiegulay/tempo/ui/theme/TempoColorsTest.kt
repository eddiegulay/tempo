package io.eddiegulay.tempo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Contrast of the three ink roles on each solid ground. Alpha inks are flattened onto the ground
 * first — that is the colour a reader actually sees.
 */
class TempoColorsTest {

    @Test
    fun `paper ink roles clear their floors on the ivory ground`() {
        assertContrast(PaperColors.ink, PaperColors.bgSolid, 7.0)
        assertContrast(PaperColors.inkSoft, PaperColors.bgSolid, 4.5)
        assertContrast(PaperColors.inkFaint, PaperColors.bgSolid, 3.0)
    }

    @Test
    fun `sumi ink roles clear their floors on the charcoal ground`() {
        assertContrast(SumiColors.ink, SumiColors.bgSolid, 7.0)
        assertContrast(SumiColors.inkSoft, SumiColors.bgSolid, 4.5)
        assertContrast(SumiColors.inkFaint, SumiColors.bgSolid, 3.0)
    }
}

private fun assertContrast(ink: Color, ground: Color, floor: Double) {
    val ratio = contrast(flatten(ink, ground), ground)
    assertTrue("$ratio < $floor", ratio >= floor)
}

private fun flatten(ink: Color, ground: Color): Color {
    val a = ink.alpha
    return Color(
        red = ink.red * a + ground.red * (1f - a),
        green = ink.green * a + ground.green * (1f - a),
        blue = ink.blue * a + ground.blue * (1f - a),
    )
}

private fun contrast(a: Color, b: Color): Double {
    val lighter = maxOf(luminance(a), luminance(b))
    val darker = minOf(luminance(a), luminance(b))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun luminance(color: Color): Double {
    fun channel(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}
