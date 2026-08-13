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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.toEpochMillis
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.LocalDateTime
import io.eddiegulay.tempo.ui.theme.TempoColors
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text

/**
 * The home layer: a faint sumi-e ensō ring, the next calendar event in the top-right corner, a large
 * mincho clock with its spoken reading (lower-left), and the lone vermillion 静 ("stillness") seal.
 *
 * The corner is set 縦書き in Japanese and horizontally in English — see [CornerCluster]. Everything
 * else on this screen is a number, a shape or a seal, and is the same in both languages.
 *
 * Positions mirror the prototype's absolute offsets within its 384-wide canvas; on taller screens
 * the extra space falls below the seal, which keeps the airy, unhurried feeling intact.
 */
@Composable
fun HomeScreen(
    showSeal: Boolean,
    events: List<CalendarEvent>,
    onChooseMode: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val now by rememberMinuteTime()
    val haptics = LocalHapticFeedback.current

    // The agenda is already sorted by start time, so the first event that hasn't ended yet is the one
    // that matters — which naturally prefers a meeting you are currently *in* over the one after it.
    val nextEvent = remember(events, now) {
        val nowMillis = now.toEpochMillis()
        events.firstOrNull { it.end > nowMillis }
    }

    Box(modifier.fillMaxSize()) {

        // 202.dp of ring centred in a 252.dp box — the prototype's 101/252 radius and 8/252 stroke,
        // stated in dp now that [Enso] no longer scales them from the box.
        Enso(
            color = c.enso,
            diameter = 202.dp,
            strokeWidth = 8.dp,
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
                // Long-pressing the clock is the deliberate way out of the launcher and into a mode —
                // 集中 or 鍛錬; a plain tap is inert. pointerInput (not combinedClickable) so the calm
                // clock never flashes a ripple.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onChooseMode()
                        },
                    )
                }
                .semantics {
                    onLongClick(label = s.home.chooseMode) { onChooseMode(); true }
                },
        ) {
            Text(
                text = s.fmt.time(now),
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
                text = s.fmt.reading(now),
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

/**
 * The top-right corner: the next calendar event, falling back to today's date.
 *
 * The date is the *floor*, not a peer — stacking both would make six columns and turn a quiet corner
 * into a paragraph. So the corner shows the event when there is one and the date whenever there
 * isn't: no permission, no upcoming event, or the provider query still in flight. It is never empty
 * and it never nags. Either way it is the only way into the Calendar page.
 *
 * **Two layouts, one meaning** (`.planning/i18n/DECISIONS.md` §L9). Japanese is set 縦書き — upright
 * columns flowing right-to-left with a ruled margin at the page edge — and that is left exactly as it
 * shipped. English is set horizontally, because there is no Latin equivalent of vertical-rl: stacking
 * `W/e/d/n/e/s/d/a/y` down nine cells is the ransom note `Tategaki.kt` names and rejects, and
 * quarter-turning the line asks the reader to tilt their head at the one screen they glance at most.
 * The pieces, the states and the spoken description are identical; only the axis turns.
 */
@Composable
private fun CornerCluster(
    now: LocalDateTime,
    event: CalendarEvent?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(if (pressed) 120 else 180, easing = LinearOutSlowInEasing),
        label = "cornerPress",
    )

    // Spoken, not drawn — so it is a sentence in both languages even where the drawn corner is three
    // wordless columns. The joins are `fmt.listSeparator`: 、 in Japanese, a comma in English.
    val description = remember(event, now, s) {
        if (event == null) {
            "${s.fmt.era(now)} ${s.fmt.monthDay(now)} ${s.fmt.dayOfWeek(now)}"
        } else {
            val day = s.fmt.dayToken(event.date(), now.toLocalDate())
            val time = if (event.allDay) s.home.allDay else s.fmt.eventTime(event.startDateTime())
            val sep = s.fmt.listSeparator
            s.home.nextEventPrefix + sep + day + sep + time + sep + event.title
        }
    }

    val frame = modifier
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
            onClick(label = s.home.openSchedule) { onOpen(); true }
        }
        // Grow the hit rect inward, away from the screen edge, without moving the glyphs.
        .padding(start = 14.dp, bottom = 14.dp)

    if (s.lang == Lang.Ja) {
        VerticalCorner(frame, now, event, c, s)
    } else {
        HorizontalCorner(frame, now, event, c, s)
    }
}

/**
 * 縦書き, unchanged: the event or the date as upright columns, with the ruled margin at the screen
 * edge where a vertical rule belongs.
 */
@Composable
private fun VerticalCorner(
    modifier: Modifier,
    now: LocalDateTime,
    event: CalendarEvent?,
    colors: TempoColors,
    s: Strings,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CornerContent(event) { target ->
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
                if (target == null) VerticalDate(now, colors, s) else EventColumns(target, now, colors, s)
            }
        }

        // A ruled margin, as on a printed page. It is the corner's only hint that it can be touched,
        // and it is drawn in both states so nothing appears or disappears when the event resolves.
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(1.dp)
                .height(RULE_LENGTH)
                .background(colors.hair),
        )
    }
}

/**
 * The same corner set horizontally for English: date over era, or day-and-time over the event title,
 * right-aligned under the same ruled margin.
 *
 * The rule turns with the writing. In 縦書き the margin runs down the page edge, which is the edge the
 * first column hugs; set horizontally the block hangs from the top edge instead, so the rule goes
 * above it — same 96.dp of hairline, same "drawn in both states", and it does not shift when a
 * one-line title becomes a two-line one.
 */
@Composable
private fun HorizontalCorner(
    modifier: Modifier,
    now: LocalDateTime,
    event: CalendarEvent?,
    colors: TempoColors,
    s: Strings,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .padding(end = 2.dp)
                .width(RULE_LENGTH)
                .height(1.dp)
                .background(colors.hair),
        )

        CornerContent(event) { target ->
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (target == null) HorizontalDate(now, colors, s) else EventLines(target, now, colors, s)
            }
        }
    }
}

/**
 * The crossfade both corners share.
 *
 * Ambient information arriving, not a page turn — slower than the 260ms screen transition, so it
 * reads like ink soaking into paper and the user only half-notices it.
 */
@Composable
private fun CornerContent(
    event: CalendarEvent?,
    content: @Composable (CalendarEvent?) -> Unit,
) {
    AnimatedContent(
        targetState = event,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(240)) },
        contentKey = { it?.key },
        label = "corner",
    ) { target -> content(target) }
}

/**
 * The next event, read right-to-left as a phrase: 今日 → 十九時三十分 → Standup.
 *
 * The day hugs the screen edge and the title — the one column that can grow long — hangs inward
 * toward the ensō, where there is room for it.
 */
@Composable
private fun EventColumns(event: CalendarEvent, now: LocalDateTime, colors: TempoColors, s: Strings) {
    val time = if (event.allDay) s.home.allDay else s.fmt.eventTime(event.startDateTime())
    val day = s.fmt.dayToken(event.date(), now.toLocalDate())

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isImminent(event, now)) {
            Canvas(Modifier.padding(bottom = 6.dp).size(4.dp)) {
                drawCircle(color = colors.accent)
            }
        }
        // The title is the only full-ink element in the cluster — it is the new information — and at
        // 19sp against a 104sp clock, Home's hierarchy is untouched.
        VerticalColumn(
            text = event.title,
            color = colors.ink,
            weight = FontWeight.Medium,
            overflowColor = colors.inkFaint,
        )
    }
    VerticalColumn(time, colors.inkSoft, size = 17.sp)
    VerticalColumn(day, colors.inkFaint, size = 15.sp)
}

/** The same event as two horizontal lines: `Today 19:30` over `Design review`. */
@Composable
private fun EventLines(event: CalendarEvent, now: LocalDateTime, colors: TempoColors, s: Strings) {
    val time = if (event.allDay) s.home.allDay else s.fmt.eventTime(event.startDateTime())
    val day = s.fmt.dayToken(event.date(), now.toLocalDate())

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isImminent(event, now)) {
            Canvas(Modifier.size(4.dp)) { drawCircle(color = colors.accent) }
        }
        CornerLine("$day $time", colors.inkSoft, 13.sp)
    }
    // Two lines rather than one: an event title is the only string in this corner the app did not
    // write, and clipping someone's meeting to `Design r…` is worse than spending a second line.
    Text(
        text = event.title,
        style = TextStyle(
            fontFamily = Mincho,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = colors.ink,
            textAlign = TextAlign.End,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = CORNER_MAX_WIDTH),
    )
}

/**
 * Within half an hour: the corner earns the second vermillion mark on Home. Static — Tempo does not
 * pulse at you.
 */
private fun isImminent(event: CalendarEvent, now: LocalDateTime): Boolean =
    !event.allDay && event.begin - now.toEpochMillis() in 0..IMMINENT_WINDOW_MS

/** Within this many millis of starting, the next event is flagged with the accent dot. */
private const val IMMINENT_WINDOW_MS = 30 * 60 * 1000L

/** How long the ruled margin is, whichever way it runs. */
private val RULE_LENGTH: Dp = 96.dp

/**
 * The corner's height budget in 縦書き, and its width budget set horizontally. Both are the distance
 * from the corner to the clock, which is what the corner may not reach.
 */
private val CORNER_BUDGET: Dp = 150.dp
private val CORNER_MAX_WIDTH: Dp = 180.dp

/** Vertical-rl, upright date: 令和八年 / 六月十七日 / 水曜日, columns flowing right-to-left. */
@Composable
private fun VerticalDate(now: LocalDateTime, colors: TempoColors, s: Strings) {
    // vertical-rl => the first line is rightmost, so we render left-to-right as dow, md, era.
    VerticalColumn(s.fmt.dayOfWeek(now), colors.inkFaint)
    VerticalColumn(s.fmt.monthDay(now), colors.inkSoft)
    VerticalColumn(s.fmt.era(now), colors.inkSoft)
}

/** Horizontal date: `Wednesday 17 June` over `Reiwa 8`, the era kept rather than replaced (§L9). */
@Composable
private fun HorizontalDate(now: LocalDateTime, colors: TempoColors, s: Strings) {
    CornerLine("${s.fmt.dayOfWeek(now)} ${s.fmt.monthDay(now)}", colors.inkSoft, 15.sp)
    CornerLine(s.fmt.era(now), colors.inkFaint, 13.sp)
}

/**
 * One column of the vertical corner.
 *
 * This is [TategakiText] and not a naive per-`Char` stack: the old `VerticalLine` gave every character
 * its own cell with no upright/rotated distinction, no 縦中横, no cell cap and no height bound, which
 * is exactly what `Tategaki.kt` calls a ransom note. For the Japanese this now draws — 令和八年,
 * 六月十七日, 十九時三十分 — the two agree glyph for glyph, because every one of those characters is
 * upright and the spacing and centring are the same. What Tategaki adds is a floor under the cases
 * that used to have none.
 */
@Composable
private fun VerticalColumn(
    text: String,
    color: Color,
    size: TextUnit = 19.sp,
    weight: FontWeight = FontWeight.Normal,
    overflowColor: Color = color,
) {
    TategakiText(
        text = text,
        style = TextStyle(
            fontFamily = Mincho,
            fontWeight = weight,
            fontSize = size,
            color = color,
        ),
        overflowColor = overflowColor,
        // Bounded so a long title ellipsises rather than running down into the clock.
        modifier = Modifier.heightIn(max = CORNER_BUDGET),
    )
}

/** One line of the horizontal corner: right-aligned, single line, ellipsised at the width budget. */
@Composable
private fun CornerLine(text: String, color: Color, size: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = Mincho,
            fontSize = size,
            color = color,
            textAlign = TextAlign.End,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = CORNER_MAX_WIDTH),
    )
}

/**
 * The single vermillion 静 seal — a slightly rotated outlined square.
 *
 * **静 stays 静 in English**, and it is not in the string table. This is a *hanko*: a seal is a mark
 * rather than a word, the way a monogram or a maker's stamp is, and translating it would be like
 * translating a logo. It is also the only place on Home the accent colour appears, so it is carrying
 * the app's character rather than its copy — "Stillness" in a 50.dp box would carry neither, since
 * the box holds one square glyph and nothing else.
 */
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
