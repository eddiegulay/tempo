package io.eddiegulay.tempo.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.CalendarInfo
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.calendar.openAccountSettings
import io.eddiegulay.tempo.calendar.rememberCalendarPermissionState
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The event composer — add (予定を加える), edit (予定を編集), or view (予定).
 *
 * A screen rather than a bottom sheet: it rides the app's existing screen transition for
 * free, and a Material `ModalBottomSheet` would drag in a scrim, a drag handle, and Material's own
 * elevation and motion — four pieces of foreign chrome on a page whose whole point is that it looks
 * like paper.
 *
 * It captures a thing you just agreed to, in ten seconds: title, when, where, which calendar. No
 * guests, no reminders, no recurrence. Anything richer belongs in the real calendar app, and
 * カレンダーで開く is the pressure valve that gets you there.
 */
@Composable
fun EventComposeScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val context = LocalContext.current

    val editing by viewModel.composing.collectAsStateWithLifecycle()
    val calendarsState by viewModel.writableCalendars.collectAsStateWithLifecycle()
    val granted by viewModel.calendarAccess.collectAsStateWithLifecycle()
    val writing by viewModel.writing.collectAsStateWithLifecycle()
    val pendingWrite by viewModel.pendingWrite.collectAsStateWithLifecycle()
    val fault by viewModel.calendarFault.collectAsStateWithLifecycle()

    val calendars = calendarsState.valueOrNull().orEmpty()

    val permission = rememberCalendarPermissionState(
        granted = granted,
        onGrantedChange = viewModel::setCalendarAccess,
    )

    // A recurring occurrence, or an event on a calendar we may not write to (a holiday feed, a
    // colleague's shared calendar), is shown but not edited. See [EventCompose] docs on recurrence.
    val readOnly = editing != null && !editing!!.editable

    var title by remember(editing) { mutableStateOf(editing?.title.orEmpty()) }
    var location by remember(editing) { mutableStateOf(editing?.location.orEmpty()) }
    var allDay by remember(editing) { mutableStateOf(editing?.allDay ?: false) }
    var start by remember(editing) { mutableStateOf(editing?.startDateTime() ?: defaultStart()) }
    var end by remember(editing) {
        mutableStateOf(editing?.endDateTime() ?: defaultStart().plusHours(1))
    }
    var calendarId by remember(editing, calendars) {
        mutableStateOf(editing?.calendarId ?: calendars.firstOrNull()?.id ?: -1L)
    }
    var open by remember(editing) { mutableStateOf(Picker.None) }

    // A new event with nowhere to go. Not a failure — the device simply has no writable calendar —
    // but it makes 保存 impossible, so it is stated up front rather than left as a dead grey word.
    val nothingToWriteTo = editing == null && calendarsState is Loadable.Ready && calendars.isEmpty()

    // The strip shows a real fault first; failing that, the structural reasons the user can't save.
    // Typed as the wider [TempoFault] only because [Loadable.Failed] now is; everything this composer
    // can actually put in it is a [CalendarFault].
    val shown: TempoFault? = fault
        ?: (calendarsState as? Loadable.Failed)?.fault
        ?: CalendarFault.NoWritableCalendar.takeIf { nothingToWriteTo }

    val canSave = title.isNotBlank() && calendarId >= 0 && !readOnly && !writing
    val heading = when {
        readOnly -> "予定"
        editing != null -> "予定を編集"
        else -> "予定を加える"
    }

    // Every fault carries a way out; this is where each one leads.
    val recover: () -> Unit = {
        when (shown) {
            CalendarFault.PermissionLost -> permission.request()
            CalendarFault.NoWritableCalendar -> openAccountSettings(context)
            // The event was deleted elsewhere; there is nothing left here to edit.
            CalendarFault.EventGone -> viewModel.cancelCompose()
            // "もう一度" — clear the strip and let them press 保存 again with the draft still intact.
            else -> viewModel.dismissCalendarFault()
        }
    }

    // The gate. No change reaches the provider without passing through it.
    pendingWrite?.let { pending ->
        EventConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmWrite,
            onDismiss = viewModel::cancelWrite,
        )
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = heading,
                style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(
                    label = if (readOnly) "とじる" else "やめる",
                    description = if (readOnly) "とじる" else "やめる",
                    color = c.inkFaint,
                    onClick = viewModel::cancelCompose,
                )
                if (!readOnly) {
                    HeaderAction(
                        label = if (writing) "保存中" else "保存",
                        description = "保存",
                        color = if (canSave) c.accent else c.inkFaint,
                        enabled = canSave,
                        // Proposes the change; the dialog commits it. See [EventConfirmDialog].
                        onClick = {
                            viewModel.requestSave(
                                EventDraft(
                                    title = title.trim(),
                                    start = start,
                                    end = end,
                                    allDay = allDay,
                                    location = location.trim(),
                                    calendarId = calendarId,
                                ),
                            )
                        },
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
        ) {
            // Above the fields, never over them: the draft stays visible and untouched behind it.
            shown?.let { fault ->
                FaultStrip(
                    fault = fault,
                    onRecover = recover,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            TitleField(
                value = title,
                readOnly = readOnly,
                // Straight into typing on a new event; on an edit the user came to change something
                // specific, and a keyboard in their face is presumptuous.
                autoFocus = editing == null,
                onChange = { title = it },
            )

            if (readOnly && editing?.recurring == true) {
                Text(
                    text = "繰り返しの予定",
                    modifier = Modifier.padding(top = 10.dp),
                    style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 2.sp, color = c.inkFaint),
                )
            }

            Spacer(Modifier.height(10.dp))

            ToggleRow(
                label = "終日",
                on = allDay,
                enabled = !readOnly,
                onToggle = {
                    allDay = !allDay
                    open = Picker.None
                },
            )
            Rule()

            PickerRow(
                label = "開始",
                value = formatValue(start, allDay),
                enabled = !readOnly,
                expanded = open == Picker.Start,
                onClick = { open = if (open == Picker.Start) Picker.None else Picker.Start },
                onChange = { picked ->
                    // Drag the end along so the duration the user already chose survives a change of
                    // start — until it would land before the start, which nothing should allow.
                    val span = Duration.between(start, end).coerceAtLeast(Duration.ofHours(1))
                    start = picked
                    if (!end.isAfter(picked)) end = picked.plus(span)
                },
                current = start,
                allDay = allDay,
            )
            Rule()

            PickerRow(
                label = "終了",
                value = formatValue(end, allDay),
                enabled = !readOnly,
                expanded = open == Picker.End,
                onClick = { open = if (open == Picker.End) Picker.None else Picker.End },
                onChange = { picked -> end = if (picked.isAfter(start)) picked else start.plusHours(1) },
                current = end,
                allDay = allDay,
            )
            Rule()

            LocationField(value = location, readOnly = readOnly, onChange = { location = it })

            // Only worth asking when there is a real choice to make.
            if (!readOnly && calendars.size > 1) {
                Rule()
                CalendarChips(
                    calendars = calendars,
                    selectedId = calendarId,
                    onSelect = { calendarId = it },
                )
            }

            Spacer(Modifier.height(32.dp))

            when {
                readOnly -> editing?.let { event ->
                    CenteredAction(label = "カレンダーで開く", color = c.accent) {
                        viewModel.openInCalendarApp(context, event)
                    }
                }
                // Proposes; [EventConfirmDialog] commits. Deleting is the one change here that cannot
                // be undone, and it withdraws the meeting from everyone invited to it.
                editing != null -> CenteredAction(
                    label = "削除",
                    color = c.accent,
                    enabled = !writing,
                    onClick = viewModel::requestDelete,
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

private enum class Picker { None, Start, End }

/**
 * New events land on the next clean half-hour — nobody schedules anything at 14:07.
 *
 * `internal`, and **the app's only answer to "when does a new event open?"** — `DECISIONS.md` §Q7's
 * ruling, that one implementation is authoritative and the other delegates. `鍛錬`'s 予定に入れる
 * (`ui/gym/ScheduleNextAction.kt`) opened on a verbatim second copy of these six lines; two copies that
 * agree today are the divergence bug §Q7 was raised over, so the copy is gone and this is what it calls.
 *
 * @param now injected so the rule can be pinned by a JVM test at a fixed minute, which a copy that read
 *   the wall clock internally could not be. Production passes nothing.
 */
internal fun defaultStart(now: LocalDateTime = LocalDateTime.now()): LocalDateTime =
    now.truncatedTo(ChronoUnit.MINUTES).let { at ->
        val minute = at.minute
        when {
            minute < 30 -> at.withMinute(30)
            else -> at.plusHours(1).withMinute(0)
        }
    }

private fun formatValue(at: LocalDateTime, allDay: Boolean): String {
    val date = JapaneseDate.monthDay(at)
    return if (allDay) date else "$date ・ ${JapaneseDate.clock(at)}"
}

@Composable
private fun Rule() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
}

@Composable
private fun TitleField(value: String, readOnly: Boolean, autoFocus: Boolean, onChange: (String) -> Unit) {
    val c = LocalTempoColors.current
    val style = TextStyle(fontFamily = Mincho, fontSize = 20.sp, color = if (readOnly) c.inkSoft else c.ink)

    if (readOnly) {
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Text(text = value, style = style)
        }
        Rule()
        return
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "題名" },
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 14.dp)) {
                    if (value.isEmpty()) {
                        Text(text = "題名", style = style.copy(color = c.inkFaint))
                    }
                    inner()
                }
                Rule()
            }
        },
    )
}

@Composable
private fun LocationField(value: String, readOnly: Boolean, onChange: (String) -> Unit) {
    val c = LocalTempoColors.current
    val style = TextStyle(fontFamily = Gothic, fontSize = 15.sp, color = if (readOnly) c.inkSoft else c.ink)

    if (readOnly) {
        if (value.isBlank()) return
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Text(text = value, style = style)
        }
        return
    }

    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "場所" },
        decorationBox = { inner ->
            Box(Modifier.padding(vertical = 14.dp)) {
                if (value.isEmpty()) {
                    Text(text = "場所", style = style.copy(color = c.inkFaint))
                }
                inner()
            }
        },
    )
}

/**
 * 終日 as a two-state word rather than a Material `Switch` — which is the single most recognisably
 * Material widget there is, and would look like it wandered in from another app.
 */
@Composable
private fun ToggleRow(label: String, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onToggle)
            .semantics {
                role = Role.Switch
                contentDescription = label
                stateDescription = if (on) "する" else "しない"
            }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkSoft),
        )
        Text(
            text = if (on) "する" else "しない",
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 14.sp,
                color = if (on) c.accent else c.inkFaint,
            ),
        )
    }
}

/** A date/time field whose wheel unfolds beneath it in place — never a dialog, never an overlay. */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    enabled: Boolean,
    expanded: Boolean,
    current: LocalDateTime,
    allDay: Boolean,
    onClick: () -> Unit,
    onChange: (LocalDateTime) -> Unit,
) {
    val c = LocalTempoColors.current
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220, easing = LinearOutSlowInEasing))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { role = Role.Button; contentDescription = "$label、$value" }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 18.sp,
                    color = if (enabled) c.ink else c.inkSoft,
                ),
            )
        }
        if (expanded && enabled) {
            TempoDateTimeWheel(current = current, allDay = allDay, onChange = onChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarChips(calendars: List<CalendarInfo>, selectedId: Long, onSelect: (Long) -> Unit) {
    val c = LocalTempoColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(
            text = "カレンダー",
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            calendars.forEach { calendar ->
                val selected = calendar.id == selectedId
                Row(
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .clickable { onSelect(calendar.id) }
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = calendar.displayName
                            stateDescription = if (selected) "選択中" else ""
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Canvas(Modifier.size(6.dp)) {
                        drawCircle(color = Color(calendar.color).copy(alpha = if (selected) 1f else 0.5f))
                    }
                    Text(
                        text = calendar.displayName,
                        style = TextStyle(
                            fontFamily = Mincho,
                            fontSize = 14.sp,
                            color = if (selected) c.ink else c.inkFaint,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Delete used to confirm itself inline, in two taps. It now goes through [EventConfirmDialog] like
 * every other change to the calendar: one idiom for "are you sure", so a user who has learnt what
 * Tempo asks before it writes has learnt it everywhere.
 */
@Composable
private fun CenteredAction(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = color),
        )
    }
}
