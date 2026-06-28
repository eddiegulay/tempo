package io.eddiegulay.tempo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.LocalDateTime
import io.eddiegulay.tempo.ui.theme.TempoColors
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text

/**
 * The home layer: a faint sumi-e ensō ring, the date in vertical Reiwa kanji (top-right), a large
 * mincho clock with its spoken reading (lower-left), and the lone vermillion 静 ("stillness") seal.
 *
 * Positions mirror the prototype's absolute offsets within its 384-wide canvas; on taller screens
 * the extra space falls below the seal, which keeps the airy, unhurried feeling intact.
 */
@Composable
fun HomeScreen(showSeal: Boolean, onEnterFocus: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val now by rememberMinuteTime()
    val haptics = LocalHapticFeedback.current
    Box(modifier.fillMaxSize()) {

        Enso(
            color = c.enso,
            modifier = Modifier
                .padding(start = 6.dp, top = 70.dp)
                .size(252.dp),
        )

        VerticalDate(
            now = now,
            colors = c,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 44.dp, end = 30.dp),
        )

        Column(
            modifier = Modifier
                .padding(start = 34.dp, top = 190.dp)
                // Long-pressing the clock is the deliberate way into Focus mode; a plain tap is inert.
                // pointerInput (not combinedClickable) so the calm clock never flashes a ripple.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnterFocus()
                        },
                    )
                }
                .semantics {
                    onLongClick(label = "集中モード") { onEnterFocus(); true }
                },
        ) {
            Text(
                text = JapaneseDate.time(now),
                style = TextStyle(
                    fontFamily = Mincho,
                    fontWeight = FontWeight.Medium,
                    fontSize = 104.sp,
                    lineHeight = 94.sp,
                    letterSpacing = (-1).sp,
                    color = c.ink,
                ),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = JapaneseDate.reading(now),
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 15.sp,
                    letterSpacing = 3.sp,
                    color = c.inkSoft,
                ),
            )
        }

        if (showSeal) {
            Seal(
                accent = c.accent,
                card = c.card,
                modifier = Modifier.padding(start = 36.dp, top = 368.dp),
            )
        }
    }
}

/** The broken brush ring — an arc with a gap, drawn from the prototype's enso path geometry. */
@Composable
private fun Enso(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val diameter = size.minDimension
        val radius = diameter * (101f / 252f)
        val center = Offset(diameter * (126f / 252f), diameter * (126f / 252f))
        drawArc(
            color = color,
            startAngle = -60f,   // gap sits in the upper-right, matching the source path
            sweepAngle = 312f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = diameter * (8f / 252f), cap = StrokeCap.Round),
        )
    }
}

/** Vertical-rl, upright date: 令和八年 / 六月十七日 / 水曜日, columns flowing right-to-left. */
@Composable
private fun VerticalDate(now: LocalDateTime, colors: TempoColors, modifier: Modifier) {
    // vertical-rl => the first line is rightmost, so we render left-to-right as dow, md, era.
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        VerticalLine(JapaneseDate.dayOfWeek(now), colors.inkFaint)
        VerticalLine(JapaneseDate.monthDay(now), colors.inkSoft)
        VerticalLine(JapaneseDate.era(now), colors.inkSoft)
    }
}

@Composable
private fun VerticalLine(text: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.forEach { ch ->
            Text(
                text = ch.toString(),
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 19.sp,
                    color = color,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/** The single vermillion 静 seal — a slightly rotated outlined square. */
@Composable
private fun Seal(accent: Color, card: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(50.dp)
            .rotate(-4f)
            .border(1.5.dp, accent, RoundedCornerShape(8.dp))
            .background(card, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "静",
            style = TextStyle(
                fontFamily = Mincho,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = accent,
            ),
        )
    }
}
