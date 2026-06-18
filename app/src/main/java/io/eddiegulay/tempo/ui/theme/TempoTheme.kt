package io.eddiegulay.tempo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two Tempo palettes, lifted directly from the prototype's `base` object.
 *
 *  - "paper" — washi-cream radial wash, sumi-ink text, vermillion accent, faint paper grain.
 *  - "sumi" — dark washi: a warm near-black charcoal wash, warm off-white ink at high
 *    contrast, a slightly brighter vermillion, and a faint light-catching paper tooth.
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

private val SumiInk = Color(0xFFECE7DB)

// Dark washi. Warm charcoal radial wash (top-centre origin, like paper) instead of true black,
// with ink alphas raised so secondary text — dates, romaji, labels — stays legible on dark
// (the old AMOLED ran inkFaint at 0.30 over #000, ~2.4:1, which failed). The grain is drawn with
// a Screen blend in dark mode (see Background.kt) so paper fibres catch light rather than darken.
val SumiColors = TempoColors(
    isDark = true,
    bgSolid = Color(0xFF1A1814),
    bgStops = listOf(Color(0xFF211F1B), Color(0xFF1A1814), Color(0xFF141210)),
    card = SumiInk.copy(alpha = 0.05f),
    ink = SumiInk,
    inkSoft = SumiInk.copy(alpha = 0.72f),
    inkFaint = SumiInk.copy(alpha = 0.48f),
    hair = SumiInk.copy(alpha = 0.16f),
    accent = Color(0xFFD2664B),
    enso = SumiInk.copy(alpha = 0.07f),
    grainOpacity = 0.05f,
)

val LocalTempoColors = staticCompositionLocalOf { PaperColors }
