package io.eddiegulay.tempo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.toEpochMillis
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.LocalDateTime
import io.eddiegulay.tempo.ui.theme.TempoColors
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text

/**
 * The home layer: a faint sumi-e ensō ring, the next calendar event in vertical kanji (top-right), a
 * large mincho clock with its spoken reading (lower-left), and the lone vermillion 静 ("stillness")
 * seal.
 *
 * Positions mirror the prototype's absolute offsets within its 384-wide canvas; on taller screens
 * the extra space falls below the seal, which keeps the airy, unhurried feeling intact.
 */
@Composable
fun HomeScreen(
    showSeal: Boolean,
    events: List<CalendarEvent>,
    onEnterFocus: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val now by rememberMinuteTime()
    val haptics = LocalHapticFeedback.current

    // The agenda is already sorted by start time, so the first event that hasn't ended yet is the one
    // that matters — which naturally prefers a meeting you are currently *in* over the one after it.
    val nextEvent = remember(events, now) {
        val nowMillis = now.toEpochMillis()
        events.firstOrNull { it.end > nowMillis }
    }

    Box(modifier.fillMaxSize()) {

        Enso(
            color = c.enso,
            modifier = Modifier
                .padding(start = 6.dp, top = 70.dp)
                .size(252.dp),
        )

        CornerCluster(
            now = now,
            event = nextEvent,
            onOpen = onOpenCalendar,
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

/**
 * The top-right corner: the next calendar event in vertical kanji, falling back to today's date.
 *
 * The date is the *floor*, not a peer — stacking both would make six columns and turn a quiet corner
 * into a paragraph. So the corner shows the event when there is one and the date whenever there
 * isn't: no permission, no upcoming event, or the provider query still in flight. It is never empty
 * and it never nags. Either way it is the only way into the Calendar page.
 */
@Composable
private fun CornerCluster(
    now: LocalDateTime,
    event: CalendarEvent?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(if (pressed) 120 else 180, easing = LinearOutSlowInEasing),
        label = "cornerPress",
    )

    val description = remember(event, now) {
        if (event == null) {
            "${JapaneseDate.era(now)} ${JapaneseDate.monthDay(now)} ${JapaneseDate.dayOfWeek(now)}"
        } else {
            val day = JapaneseDate.dayToken(event.date(), now.toLocalDate())
            val time = if (event.allDay) "終日" else JapaneseDate.eventTime(event.startDateTime())
            "次の予定、$day、$time、${event.title}"
        }
    }

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            // No ripple: a Material indication in this corner would be the loudest thing on Home.
            .pointerInput(onOpen) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onOpen() },
                )
            }
            // One node, not a column of single-character nodes — a screen reader walking the glyphs
            // one at a time is unusable.
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = "予定") { onOpen(); true }
            }
            // Grow the hit rect inward, away from the screen edge, without moving the glyphs.
            .padding(start = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Ambient information arriving, not a page turn — slower than the 260ms screen transition, so
        // it reads like ink soaking into paper and the user only half-notices it.
        AnimatedContent(
            targetState = event,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(240)) },
            contentKey = { it?.key },
            label = "corner",
        ) { target ->
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
                if (target == null) VerticalDate(now, c) else EventColumns(target, now, c)
            }
        }

        // A ruled margin, as on a printed page. It is the corner's only hint that it can be touched,
        // and it is drawn in both states so nothing appears or disappears when the event resolves.
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(1.dp)
                .height(96.dp)
                .background(c.hair),
        )
    }
}

/**
 * The next event, read right-to-left as a phrase: 今日 → 十九時三十分 → Standup.
 *
 * The day hugs the screen edge and the title — the one column that can grow long — hangs inward
 * toward the ensō, where there is room for it.
 */
@Composable
private fun EventColumns(event: CalendarEvent, now: LocalDateTime, colors: TempoColors) {
    val time = if (event.allDay) "終日" else JapaneseDate.eventTime(event.startDateTime())
    val day = JapaneseDate.dayToken(event.date(), now.toLocalDate())
    // Within half an hour: the corner earns the second vermillion mark on Home. Static — Tempo does
    // not pulse at you.
    val imminent = !event.allDay &&
        event.begin - now.toEpochMillis() in 0..IMMINENT_WINDOW_MS

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (imminent) {
            Canvas(Modifier.padding(bottom = 6.dp).size(4.dp)) {
                drawCircle(color = colors.accent)
            }
        }
        // The title is the only full-ink element in the cluster — it is the new information — and at
        // 19sp against a 104sp clock, Home's hierarchy is untouched.
        TategakiText(
            text = event.title,
            style = TextStyle(
                fontFamily = Mincho,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
                color = colors.ink,
            ),
            overflowColor = colors.inkFaint,
            // Bounded so a long Latin title ellipsises rather than running down into the clock.
            modifier = Modifier.heightIn(max = 150.dp),
        )
    }
    VerticalLine(time, colors.inkSoft, size = 17.sp)
    VerticalLine(day, colors.inkFaint, size = 15.sp)
}

/** Within this many millis of starting, the next event is flagged with the accent dot. */
private const val IMMINENT_WINDOW_MS = 30 * 60 * 1000L

/** Vertical-rl, upright date: 令和八年 / 六月十七日 / 水曜日, columns flowing right-to-left. */
@Composable
private fun VerticalDate(now: LocalDateTime, colors: TempoColors) {
    // vertical-rl => the first line is rightmost, so we render left-to-right as dow, md, era.
    VerticalLine(JapaneseDate.dayOfWeek(now), colors.inkFaint)
    VerticalLine(JapaneseDate.monthDay(now), colors.inkSoft)
    VerticalLine(JapaneseDate.era(now), colors.inkSoft)
}

@Composable
private fun VerticalLine(text: String, color: Color, size: TextUnit = 19.sp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.forEach { ch ->
            Text(
                text = ch.toString(),
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = size,
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
