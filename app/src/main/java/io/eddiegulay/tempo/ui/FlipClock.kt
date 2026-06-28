package io.eddiegulay.tempo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * A split-flap style clock: each digit sits in a rounded "card" with a faint divider across its
 * waist, and flips with a vertical fold when its value changes. A `:` renders as a pair of dots
 * between cards rather than a card of its own.
 *
 * The display is purely a function of [text] (e.g. "12:34" or "12:34:56" or a "25:00" countdown),
 * so the same component serves the live clock and the Pomodoro timer. Only the changed digits
 * animate — steady digits stay put because [AnimatedContent] is keyed per character position.
 */
@Composable
fun FlipClock(
    text: String,
    inkColor: Color,
    cardColor: Color,
    dividerColor: Color,
    digitSize: TextUnit,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEach { ch ->
            if (ch == ':') {
                Colon(color = inkColor, cardHeight = cardHeight)
            } else {
                FlipDigit(
                    digit = ch,
                    inkColor = inkColor,
                    cardColor = cardColor,
                    dividerColor = dividerColor,
                    digitSize = digitSize,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                )
            }
        }
    }
}

@Composable
private fun FlipDigit(
    digit: Char,
    inkColor: Color,
    cardColor: Color,
    dividerColor: Color,
    digitSize: TextUnit,
    cardWidth: Dp,
    cardHeight: Dp,
) {
    Box(
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            // The split-flap seam: a single hairline across the card's waist.
            .drawBehind {
                val y = size.height / 2f
                drawLine(
                    color = dividerColor,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                // New leaf drops in from the top; the old one folds away downward — a flip-board flap.
                (slideInVertically(tween(260)) { -it } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(260)) { it } + fadeOut(tween(160)))
            },
            label = "flip-digit",
        ) { value ->
            Text(
                text = value.toString(),
                style = TextStyle(
                    fontFamily = Mincho,
                    fontWeight = FontWeight.Medium,
                    fontSize = digitSize,
                    color = inkColor,
                ),
            )
        }
    }
}

/** The two stacked dots between hour/minute/second groups. */
@Composable
private fun Colon(color: Color, cardHeight: Dp) {
    val dot = (cardHeight.value * 0.08f).coerceAtLeast(7f).dp
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.height(cardHeight),
        verticalArrangement = Arrangement.spacedBy(cardHeight * 0.16f, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(dot).clip(RoundedCornerShape(50)).background(color))
        Box(Modifier.size(dot).clip(RoundedCornerShape(50)).background(color))
    }
}
