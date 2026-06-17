package io.eddiegulay.tempo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two Tempo palettes, lifted directly from the prototype's `base` object.
 *
 *  - "paper" — washi-cream radial wash, sumi-ink text, vermillion accent, faint paper grain.
 *  - "amoled" — true-black, warm off-white ink, brighter vermillion, no grain.
 *
 * Colours are exposed through [LocalTempoColors] so every screen reads the active theme without
 * threading it through every call site.
 */
@Immutable
data class TempoColors(
    val isDark: Boolean,
    /** Solid fill for AMOLED and a safe fallback / window background. */
    val bgSolid: Color,
    /** Three stops of the paper radial gradient (top-centre origin). */
    val bgStops: List<Color>,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val hair: Color,
    val accent: Color,
    val enso: Color,
    /** Grain overlay opacity; 0 disables the noise pass (AMOLED). */
    val grainOpacity: Float,
)

private val PaperInk = Color(0xFF2B2B2B)

val PaperColors = TempoColors(
    isDark = false,
    bgSolid = Color(0xFFF2EEE4),
    bgStops = listOf(Color(0xFFF8F5EF), Color(0xFFF2EEE4), Color(0xFFEBE6DB)),
    card = PaperInk.copy(alpha = 0.035f),
    ink = PaperInk,
    inkSoft = PaperInk.copy(alpha = 0.56f),
    inkFaint = PaperInk.copy(alpha = 0.30f),
    hair = PaperInk.copy(alpha = 0.10f),
    accent = Color(0xFFB5503A),
    enso = PaperInk.copy(alpha = 0.06f),
    grainOpacity = 0.06f,
)

private val AmoledInk = Color(0xFFE8E4DA)

val AmoledColors = TempoColors(
    isDark = true,
    bgSolid = Color(0xFF000000),
    bgStops = listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
    card = AmoledInk.copy(alpha = 0.05f),
    ink = AmoledInk,
    inkSoft = AmoledInk.copy(alpha = 0.52f),
    inkFaint = AmoledInk.copy(alpha = 0.30f),
    hair = AmoledInk.copy(alpha = 0.10f),
    accent = Color(0xFFCC6149),
    enso = AmoledInk.copy(alpha = 0.05f),
    grainOpacity = 0f,
)

val LocalTempoColors = staticCompositionLocalOf { PaperColors }
