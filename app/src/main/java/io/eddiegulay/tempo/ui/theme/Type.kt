package io.eddiegulay.tempo.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.eddiegulay.tempo.R

/**
 * Typography roles from the design.
 *
 *  - [Mincho] — Shippori Mincho, the brushy serif used for the clock, date, app names and 静 seal.
 *  - [Gothic] — Zen Kaku Gothic New, the light sans used for body text (notification copy, romaji).
 *
 * The OFL `.ttf` files are bundled under `res/font`, so the design renders with its intended type
 * rather than the platform Noto CJK fallback. Each family carries the two weights the UI actually
 * uses; an unspecified `fontWeight` resolves to Regular (W400).
 */
val Mincho: FontFamily = FontFamily(
    Font(R.font.shippori_mincho_regular, FontWeight.Normal),
    Font(R.font.shippori_mincho_medium, FontWeight.Medium),
)

val Gothic: FontFamily = FontFamily(
    Font(R.font.zen_kaku_gothic_new_light, FontWeight.Light),
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
)
