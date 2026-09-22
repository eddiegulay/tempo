package io.eddiegulay.tempo.notification

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.eddiegulay.tempo.ui.theme.TempoColors

/**
 * The bits of a notification-card PNG that are safe to reason about without a device.
 *
 * The on-screen row paints [TempoColors.card] at 3.5–5% over the washi wash. Capturing that live
 * row would export a nearly invisible sticker — the fill vanishes the moment it leaves Tempo's
 * paper. Export (and the lifted overlay) therefore uses [opaqueCardFill]: the same ink washed
 * onto [TempoColors.bgSolid] and then locked at alpha 1, so the rounded corners stay transparent
 * and the card itself still reads on someone else's wallpaper.
 */
fun TempoColors.opaqueCardFill(): Color = card.compositeOver(bgSolid)

/**
 * A filesystem-safe stem for the clipboard cache file and the Pictures/Tempo display name.
 *
 * App labels are third-party text (LINE, 家族グループ, …). Slashes, spaces and CJK punctuation
 * become hyphens; an empty result falls back to `notification` so MediaStore always gets a name.
 */
fun exportFileStem(appLabel: String, postTime: Long): String {
    val safe = appLabel
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-')
        .take(40)
        .ifBlank { "notification" }
    return "Tempo-$safe-$postTime"
}
