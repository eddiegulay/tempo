package io.eddiegulay.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The height of one row, and of the wheel: three rows, so exactly one neighbour shows either side. */
internal const val WHEEL_ROW = 44
internal const val WHEEL_HEIGHT = WHEEL_ROW * 3

/**
 * How much of the wheel a column takes.
 *
 * Two cases rather than a bare `Dp?`, because "share what is left" and "be this wide" are different
 * intentions and a null would have to be read as one of them by convention. A date needs the room its
 * longest label asks for; an hour is two digits and should not drift about as the day column changes.
 */
sealed interface WheelWidth {

    /** Shares the remaining width with the other [Fill] columns. */
    data object Fill : WheelWidth

    data class Fixed(val width: Dp) : WheelWidth
}

/**
 * One column of a [TempoWheel]: the words it shows, where it opens, and how wide it sits.
 *
 * [initialIndex] is *initial*, deliberately. Once the column is on screen its own scroll position is
 * the truth, and re-driving it from state would fight the user's finger mid-fling — the value they
 * are dialling changes on every settle, which would scroll the wheel they are holding. Callers that
 * need to move the wheel programmatically should re-key it instead.
 */
data class TempoWheelColumn(
    val items: List<String>,
    val initialIndex: Int,
    val width: WheelWidth = WheelWidth.Fill,
    val onSelect: (Int) -> Unit,
)

/**
 * The 栞 (shiori, "bookmark") wheel — the only place in Tempo where a value is dialled.
 *
 * Snapping columns under a pair of hairline rules, like a paper bookmark laid across the page.
 * Material's `DatePicker` / `TimePicker` are not an option here: they arrive with Material's type
 * scale, its tonal surfaces, its clock dial and its dialog chrome, none of which exist anywhere else
 * in this app. Nor is there a third-party picker to reach for — `CONTRIBUTING.md` forbids the
 * dependency, and this is ninety lines.
 *
 * Generic in its columns rather than in a value type: a wheel shows *words*, and every caller already
 * knows how to turn its own values into words (dates into 六月十九日, seconds into 十五秒). Pushing a
 * `List<T>` and a formatter down here would buy nothing and force every call site to name a type.
 */
@Composable
fun TempoWheel(
    columns: List<TempoWheelColumn>,
    modifier: Modifier = Modifier,
    spacing: Dp = 24.dp,
) {
    val c = LocalTempoColors.current

    Box(modifier.fillMaxWidth().height(WHEEL_HEIGHT.dp).padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            columns.forEach { column ->
                val sizing = when (val width = column.width) {
                    WheelWidth.Fill -> Modifier.weight(1f)
                    is WheelWidth.Fixed -> Modifier.width(width.width)
                }
                Box(sizing) {
                    WheelColumn(
                        items = column.items,
                        initialIndex = column.initialIndex,
                        onSelect = column.onSelect,
                    )
                }
            }
        }

        // The bookmark itself: one pair of rules laid across *all* the columns, not bracketing each
        // one separately — a 栞 is a single strip of paper, and per-column rules would break at every
        // column gap. Drawn over the wheels, and transparent to touch so the columns still scroll.
        Column(
            Modifier.fillMaxSize().padding(vertical = WHEEL_ROW.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
        }
    }
}

/**
 * A single column of numbers — a rest length, a round count, a rep total.
 *
 * [values] is a list rather than a range because the useful ones are not arithmetic: rests jump
 * 10 · 15 · 20 · 30 · 45 · 60, and an existing routine's odd 37 has to stay reachable. [label] is
 * where kanji happens; this component knows nothing about numerals beyond `toString`.
 *
 * A value not in [values] opens on the first entry rather than being silently coerced to the nearest
 * one — a wheel that quietly changes the stored value the moment it is *looked at* is the same class
 * of bug as the mounting emission that [WheelColumn] guards against.
 */
@Composable
fun TempoValueWheel(
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { it.toString() },
) {
    TempoWheel(
        columns = listOf(
            TempoWheelColumn(
                items = values.map(label),
                initialIndex = values.indexOf(selected).coerceAtLeast(0),
                onSelect = { onSelect(values[it]) },
            ),
        ),
        modifier = modifier,
    )
}

/** [TempoValueWheel] over a plain range or step — `0..99`, `10..120 step 5`. */
@Composable
fun TempoValueWheel(
    values: IntProgression,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { it.toString() },
) {
    TempoValueWheel(
        values = remember(values) { values.toList() },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        label = label,
    )
}

/**
 * The date/time wheel the event composer dials: a day column, and — unless the event is 終日 — an hour
 * and a minute beside it.
 *
 * Minutes step by five. A launcher does not need 12:37, and halving the column removes a whole class
 * of fiddly scrolling — but an existing event's odd minute is kept in the list rather than silently
 * rounded away.
 */
@Composable
fun TempoDateTimeWheel(current: LocalDateTime, allDay: Boolean, onChange: (LocalDateTime) -> Unit) {
    val today = remember { LocalDate.now() }

    // Span from whichever is earlier — today, or the date of an event being edited — so an existing
    // date is always reachable on the wheel.
    val firstDay = remember(current) { minOf(today, current.toLocalDate()) }
    val days = remember(firstDay) {
        val count = ChronoUnit.DAYS.between(firstDay, today.plusDays(60)).toInt() + 1
        List(count) { firstDay.plusDays(it.toLong()) }
    }
    val minutes = remember(current) {
        // Keep 09:37 as 09:37 unless the user actually spins the column.
        ((0..55 step 5) + current.minute).distinct().sorted()
    }

    val dayIndex = remember(current, days) {
        days.indexOf(current.toLocalDate()).coerceAtLeast(0)
    }

    TempoWheel(
        columns = buildList {
            add(
                TempoWheelColumn(
                    items = days.map { JapaneseDate.dayToken(it, today) },
                    initialIndex = dayIndex,
                    onSelect = { i ->
                        val d = days[i]
                        onChange(current.withYear(d.year).withDayOfYear(d.dayOfYear))
                    },
                ),
            )
            if (!allDay) {
                add(
                    TempoWheelColumn(
                        items = (0..23).map { "%02d".format(it) },
                        initialIndex = current.hour,
                        width = WheelWidth.Fixed(58.dp),
                        onSelect = { onChange(current.withHour(it)) },
                    ),
                )
                add(
                    TempoWheelColumn(
                        items = minutes.map { "%02d".format(it) },
                        initialIndex = minutes.indexOf(current.minute).coerceAtLeast(0),
                        width = WheelWidth.Fixed(58.dp),
                        onSelect = { onChange(current.withMinute(minutes[it])) },
                    ),
                )
            }
        },
    )
}

@Composable
private fun WheelColumn(items: List<String>, initialIndex: Int, onSelect: (Int) -> Unit) {
    val c = LocalTempoColors.current
    val haptics = LocalHapticFeedback.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Whichever row sits nearest the viewport centre is the selection. Reading it from layout (rather
    // than tracking scroll offsets) keeps it correct through flings, snaps and rotation.
    val centred by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - centre) }
                ?.index
        }
    }

    // Skip the first emission: mounting the wheel must not itself count as the user choosing a value.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(centred) {
        val index = centred ?: return@LaunchedEffect
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(index)
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        // A row of padding above and below, so the selected row rests in the centre band.
        contentPadding = PaddingValues(vertical = WHEEL_ROW.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.size) { i ->
            val selected = i == centred
            Box(
                Modifier.fillMaxWidth().height(WHEEL_ROW.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = items[i],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = if (selected) 22.sp else 16.sp,
                        color = if (selected) c.ink else c.inkFaint,
                    ),
                )
            }
        }
    }
}
