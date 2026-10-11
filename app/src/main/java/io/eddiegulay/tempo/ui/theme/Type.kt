package io.eddiegulay.tempo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.R
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.LocalStrings

/**
 * The two type roles, resolved by language.
 *
 * Japanese: Shippori Mincho B1 (display) and Zen Kaku Gothic New Regular (body).
 * English: Tempo Sans for both roles. Titles use Regular and Medium; body stays Regular.
 * It is a Latin-only cut of Source Sans 3. The family name is not "Source" because that name is reserved.
 * Glyphs outside the cut fall back to the system font. Light is not shipped: nothing asks for it.
 *
 * Call sites keep saying [Mincho] and [Gothic]. [LocalTempoType] picks the faces.
 */
@Immutable
data class TempoType(
    val display: FontFamily,
    val body: FontFamily,
)

private val MinchoJa = FontFamily(
    Font(R.font.shippori_mincho_b1_regular, FontWeight.Normal),
    Font(R.font.shippori_mincho_b1_medium, FontWeight.Medium),
)

private val GothicJa = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
)

private val MinchoEn = FontFamily(
    Font(R.font.tempo_sans_regular, FontWeight.Normal),
    Font(R.font.tempo_sans_medium, FontWeight.Medium),
)

private val GothicEn = FontFamily(
    Font(R.font.tempo_sans_regular, FontWeight.Normal),
)

val JaType = TempoType(MinchoJa, GothicJa)
val EnType = TempoType(MinchoEn, GothicEn)

val LocalTempoType = staticCompositionLocalOf { JaType }

val Mincho: FontFamily
    @Composable get() = LocalTempoType.current.display

val Gothic: FontFamily
    @Composable get() = LocalTempoType.current.body

/** CJK tracking of 3sp and up is too wide once the words are Latin. */
@Composable
fun track(em: Int): TextUnit {
    if (LocalStrings.current.lang != Lang.En || em < 3) return em.sp
    return (em * 0.28f).sp
}
