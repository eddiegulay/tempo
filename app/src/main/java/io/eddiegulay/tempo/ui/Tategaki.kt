package io.eddiegulay.tempo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vertical Japanese text (縦書き, *tategaki*) for Latin-and-kanji strings.
 *
 * The rule is the one a Japanese typesetter uses: ideographs and kana stack **upright**, one glyph
 * per line; a run of Latin is set **rotated a quarter-turn clockwise** so the reader tilts their head
 * right; and a one- or two-digit number sits upright as a single cell (縦中横, *tate-chū-yoko*).
 *
 * The alternatives both fail. Rotating the whole string turns 会議 on its side, which a Japanese
 * reader registers as simply broken, and severs the visual kinship with the upright columns beside
 * it. Stacking every Latin letter — S / t / a / n / d / u / p — is a ransom note: seven cells for a
 * seven-letter word, with no vertical centre of gravity. The hybrid is compact for Latin, correct for
 * CJK, and because a rotated run is a single [Text], it truncates with an ordinary ellipsis.
 */
@Composable
fun TategakiText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    glyphSpacing: Dp = 3.dp,
    maxCells: Int = 8,
    overflowColor: Color = style.color,
) {
    val laid = remember(text, maxCells) { layoutTategaki(text, maxCells) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(glyphSpacing),
    ) {
        laid.cells.forEach { cell ->
            if (cell.rotated) {
                // Measured against the column's *remaining* height, so a long title ellipsises
                // exactly at the height budget its caller allotted.
                Text(
                    text = cell.text,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.quarterTurn(),
                )
            } else {
                Text(
                    text = cell.text,
                    style = style.copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
        if (laid.truncated) {
            Text(text = "…", style = style.copy(color = overflowColor, textAlign = TextAlign.Center))
        }
    }
}

/**
 * Rotates a composable a quarter-turn clockwise *and swaps its reported size*, which
 * [Modifier.rotate] alone does not do — it is a draw-time transform, so the node would still occupy
 * an unrotated box and overflow its column.
 *
 * The child is measured with width and height exchanged (its text runs along what will become the
 * vertical axis), then offset so its centre coincides with the swapped node's centre, which is the
 * point [rotate] turns about.
 */
private fun Modifier.quarterTurn(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = 0,
                maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            ),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = (placeable.height - placeable.width) / 2,
                y = (placeable.width - placeable.height) / 2,
            )
        }
    }
    .rotate(90f)

/** One line of a vertical column: either an upright glyph (or short number) or a rotated Latin run. */
internal data class TategakiCell(val text: String, val rotated: Boolean)

internal data class TategakiLayout(val cells: List<TategakiCell>, val truncated: Boolean)

/**
 * Segments [text] into maximal upright and sideways runs, then emits one cell per upright character
 * and one cell per sideways run. Stops after [maxCells], reporting the overflow so the caller can
 * mark it.
 */
internal fun layoutTategaki(text: String, maxCells: Int = 8): TategakiLayout {
    val source = text.trim()
    if (source.isEmpty()) return TategakiLayout(emptyList(), truncated = false)

    val cells = mutableListOf<TategakiCell>()
    var i = 0
    while (i < source.length) {
        if (cells.size >= maxCells) return TategakiLayout(cells, truncated = true)

        if (isUpright(source[i])) {
            cells += TategakiCell(source[i].toString(), rotated = false)
            i++
            continue
        }

        // A sideways run absorbs Latin letters, digits, ASCII punctuation and the whitespace between
        // them, so "Design review" stays one stroke rather than fracturing at the space.
        var j = i
        while (j < source.length && !isUpright(source[j])) j++
        val run = source.substring(i, j).trim()
        i = j

        if (run.isEmpty()) continue // whitespace sitting between two upright runs
        // 縦中横: a short number reads upright as a unit — "第2会議室", "10 件".
        val upright = run.length <= 2 && run.all { it.isDigit() }
        cells += TategakiCell(run, rotated = !upright)
    }
    return TategakiLayout(cells, truncated = false)
}

/**
 * True for anything that stacks upright: CJK ideographs, kana, and CJK punctuation. Everything below
 * U+2E80 — Latin, digits, ASCII punctuation, and the Greek/Cyrillic that occasionally turns up in a
 * title — is set sideways.
 */
private fun isUpright(ch: Char): Boolean = ch.code >= 0x2E80
