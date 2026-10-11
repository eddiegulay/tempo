package io.eddiegulay.tempo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two Tempo palettes.
 *
 *  - "paper" — 白練 ivory wash, cooler sumi ink, 朱 accent (the only warm mark), faint grain.
 *  - "sumi" — the same materials on a cool charcoal wash.
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
    bgSolid = Color(0xFFF7F4EE),
    bgStops = listOf(Color(0xFFFEFCF7), Color(0xFFF7F4EE), Color(0xFFEEEAE2)),
    card = PaperInk.copy(alpha = 0.035f),
    // Cooler sumi so the page is ivory, not peach. Soft/faint stay alpha-of-ink so hairlines and
    // the seal keep one pigment. Faint cannot hit 4.5:1 without becoming Soft — 0.54 is ~3.3:1.
    ink = Color(0xFF2F2C28),
    inkSoft = PaperInk.copy(alpha = 0.66f),
    inkFaint = PaperInk.copy(alpha = 0.54f),
    hair = PaperInk.copy(alpha = 0.10f),
    accent = Color(0xFFC24A38),
    enso = PaperInk.copy(alpha = 0.06f),
    grainOpacity = 0.05f,
)

private val SumiInk = Color(0xFFECE7DB)

// Cool charcoal wash (top-centre, like paper). Grain is a Screen blend in dark mode
// (see Background.kt) so fibres catch light rather than muddy the ground.
val SumiColors = TempoColors(
    isDark = true,
    bgSolid = Color(0xFF161513),
    bgStops = listOf(Color(0xFF1C1B19), Color(0xFF161513), Color(0xFF121110)),
    card = SumiInk.copy(alpha = 0.05f),
    ink = Color(0xFFE4DFD4),
    inkSoft = SumiInk.copy(alpha = 0.72f),
    inkFaint = SumiInk.copy(alpha = 0.48f),
    hair = SumiInk.copy(alpha = 0.16f),
    accent = Color(0xFFD45A42),
    enso = SumiInk.copy(alpha = 0.07f),
    grainOpacity = 0.05f,
)

val LocalTempoColors = staticCompositionLocalOf { PaperColors }
