package io.eddiegulay.tempo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.calendar.displayTitle
import io.eddiegulay.tempo.calendar.rememberCalendarPermissionState
import io.eddiegulay.tempo.calendar.toEpochMillis
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import java.time.LocalDate

/**
 * Calendar (予定): the next fortnight of events from the device's calendar — the same provider the
 * Google Calendar sync adapter writes into, so this is the very agenda the user's other devices show.
 *
 * Sibling to [NotificationsScreen] by design: same header, same washi cards, same permission gate.
 * The one deliberate divergence is that events cannot be swiped away. Dismissing a notification is
 * local and undoable; cancelling a meeting is remote and everyone on the invite sees it, so deletion
 * lives behind a confirmation inside the composer and nowhere else.
 *
 * Reached only by tapping Home's top-right cluster — Calendar has no dock tab.
 */
@Composable
fun CalendarScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val now by rememberMinuteTime()

    val granted by viewModel.calendarAccess.collectAsStateWithLifecycle()
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val events = agenda.valueOrNull().orEmpty()

    // Access can be revoked in Settings while the launcher is alive, so re-check on every return —
    // the same shape as the notification-listener check on the sibling page.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCalendarAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permission = rememberCalendarPermissionState(
        granted = granted,
        onGrantedChange = viewModel::setCalendarAccess,
    )

    val today = now.toLocalDate()
    val nowMillis = now.toEpochMillis()
    val days = remember(events) { events.groupBy { it.date() }.toSortedMap() }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = s.calendar.title,
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = s.fmt.era(now) + s.fmt.separator + s.fmt.monthDay(now),
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            if (granted) {
                HeaderAction(
                    label = s.calendar.add,
                    description = s.calendar.addEvent,
                    color = c.accent,
                    onClick = { viewModel.composeNewEvent() },
                )
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                !granted -> AccessPrompt(
                    permanentlyDenied = permission.permanentlyDenied,
                    onClick = permission.request,
                )

                // Never `calendar.empty` for a calendar we failed to read — see [Loadable].
                agenda is Loadable.Failed -> FaultPanel(
                    fault = (agenda as Loadable.Failed).fault,
                    onRecover = {
                        val fault = (agenda as Loadable.Failed).fault
                        if (fault == CalendarFault.PermissionLost) permission.request() else viewModel.retryAgenda()
                    },
                )

                agenda is Loadable.Loading -> EmptyState(text = s.calendar.loading)

                events.isEmpty() -> EmptyState(text = s.calendar.empty)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp),
                    // Clear the floating dock so the last card is never trapped beneath it.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    days.forEach { (date, dayEvents) ->
                        item(key = "day:$date") { DayHeader(date, today, dayEvents.size) }
                        items(dayEvents, key = { it.key }) { event ->
                            EventCard(
                                event = event,
                                nowMillis = nowMillis,
                                onClick = { viewModel.composeEvent(event) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A quiet day divider: 今日 / 明日 / 水曜日 ・ 六月十九日, plus a count. One TalkBack node. */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, count: Int) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val label = s.fmt.dayHeading(date, today)
    // The badge beside the label stays arabic in both languages — it is a numeral in a fixed slot,
    // drawn in Gothic, not a sentence. Only the spoken form is a counter, and a counter is `fmt`'s.
    val spoken = label + s.fmt.listSeparator + s.fmt.items(count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Inset to the card's internal padding so the label lines up with the card titles.
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        Text(
            text = count.toString(),
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 1.sp, color = c.inkFaint),
        )
    }
}

/**
 * One event as a washi card, mirroring [NotificationsScreen]'s row geometry so the two pages read as
 * siblings. The calendar's colour occupies the slot where a notification puts its app icon — the
 * page's only colour, and it reads as a seal-dot rather than a legend.
 */
@Composable
private fun EventCard(event: CalendarEvent, nowMillis: Long, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val ongoing = event.isOngoing(nowMillis)

    // Resolved here, at the last possible moment, and never put back on the event: see [displayTitle].
    val title = event.displayTitle(s)

    val timeLabel = when {
        event.allDay -> s.calendar.allDay
        else -> s.fmt.clockAt(event.startDateTime())
    }
    val detail = remember(event, s) {
        val span = if (event.allDay) {
            val lastDay = event.endDateTime().toLocalDate().minusDays(1)
            if (lastDay.isAfter(event.date())) {
                s.calendar.allDayUntil(s.fmt.monthDay(lastDay.atStartOfDay()))
            } else {
                s.calendar.allDay
            }
        } else {
            s.fmt.clockAt(event.startDateTime()) + " – " + s.fmt.clockAt(event.endDateTime())
        }
        listOfNotNull(span, event.location).joinToString(s.fmt.separator)
    }
    // Flagged up front, so a read-only composer is never a surprise when the user taps in.
    val source = remember(event, s) {
        listOfNotNull(
            event.calendarName.takeIf { it.isNotBlank() },
            s.calendar.recurring.takeIf { event.recurring },
        ).joinToString(s.fmt.separator)
    }

    val description = remember(event, ongoing, s) {
        listOfNotNull(title, detail, source, s.calendar.ongoing.takeIf { ongoing })
            .joinToString(s.fmt.listSeparator)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            // Whole card, one shape: the fill and the press wash take the same corner, so a pressed
            // card darkens exactly within its own outline. The role stays in the semantics block
            // because `clearAndSetSemantics` discards anything the click modifier declared.
            .background(c.card, TempoShapes.Card)
            .pressable(TempoShapes.Card, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.padding(top = 6.dp).size(20.dp), contentAlignment = Alignment.TopCenter) {
                Canvas(Modifier.size(6.dp)) {
                    // Sunk into the washi rather than glowing on top of it; an event running right
                    // now takes the vermillion at full strength instead.
                    drawCircle(color = if (ongoing) c.accent else Color(event.color).copy(alpha = 0.75f))
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true),
                        style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
                    )
                    if (ongoing) {
                        Text(
                            text = s.calendar.ongoing,
                            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, color = c.accent),
                        )
                    } else {
                        Text(
                            text = timeLabel,
                            style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
                        )
                    }
                }
                Text(
                    text = detail,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.5.sp, color = c.inkSoft),
                )
                if (source.isNotBlank()) {
                    Text(
                        text = source,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
                    )
                }
            }
        }
    }
}

/**
 * A quiet word in a page header — `calendar.add`, or the composer's save / cancel.
 *
 * A lozenge, because there is nothing here but the word: no fill, no border, so the press wash is the
 * control's only outline and it should read as ink gathering round the word rather than a tile behind
 * it. Disabled, `pressable` emits no interactions at all, so 保存 greyed out also stays silent.
 */
@Composable
fun HeaderAction(
    label: String,
    description: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = color),
        )
    }
}

/**
 * The tap-to-grant gate, shaped exactly like the notification-access prompt.
 *
 * Once the permission is permanently denied the system stops showing its dialog, and tapping can only
 * open Settings. Saying so is the difference between a considered hand-off and an app that appears to
 * throw you out of itself when you press a button.
 */
@Composable
private fun AccessPrompt(permanentlyDenied: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = if (permanentlyDenied) s.calendar.accessDeniedTitle else s.calendar.accessTitle,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, lineHeight = 28.sp, letterSpacing = 4.sp, color = c.inkSoft),
            )
            Text(
                text = if (permanentlyDenied) s.calendar.accessOpenSettings else s.calendar.accessGrant,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 3.sp, color = c.accent),
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}
