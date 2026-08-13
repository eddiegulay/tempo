package io.eddiegulay.tempo.ui.gym

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.CalendarInfo
import io.eddiegulay.tempo.calendar.CalendarRepository
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.calendar.PendingWrite
import io.eddiegulay.tempo.calendar.WriteOutcome
import io.eddiegulay.tempo.calendar.hasCalendarAccess
import io.eddiegulay.tempo.calendar.openAccountSettings
import io.eddiegulay.tempo.calendar.rememberCalendarPermissionState
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.scheduleDraft
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.EventConfirmDialog
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.TempoDateTimeWheel
import io.eddiegulay.tempo.ui.defaultStart
import io.eddiegulay.tempo.ui.draftSummary
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import kotlinx.coroutines.launch

/*
 * 予定に入れる — `GYM.SESSION.COMPLETE`'s calendar hand-off, deferred from Phase 1 by `00-plan.md` §6.
 *
 * `04-library-records.md` §4 row 8 gives it its whole brief in one clause: *"present — scheduling the
 * next belongs to the moment you finish"*, live only, and absent from `GYM.RECORDS.SESSION_DETAIL`.
 *
 * ## It is the existing compose flow, not a second one
 *
 * Every mechanism on this screen is already written and is called, not copied:
 * `rememberCalendarPermissionState` for the grant and for the permanently-denied fork,
 * `CalendarRepository.writableCalendars` for where the event can go, `EventConfirmDialog` for the
 * gate, `CalendarRepository.insert` for the write, `faultCopy`/`FaultStrip` for every way it can fail,
 * and `openAccountSettings` for the one fault Tempo cannot fix from inside itself. No new permission
 * is declared — `READ_CALENDAR` and `WRITE_CALENDAR` have been in the manifest since the 予定 page.
 *
 * **Every change to the calendar still passes the gate.** `PendingWrite` exists because *"a calendar
 * is shared: an event Tempo creates appears on the user's laptop"*, and a record screen is not a
 * reason to make an exception — least of all one where the user's last three taps were a rating.
 *
 * ## What this deliberately is not
 *
 * It is **not a second composer**. `EventComposeScreen`'s field chrome — `TitleField`, `PickerRow`,
 * `ToggleRow`, `CalendarChips`, `CenteredAction` — is all `private` in a file this unit does not own,
 * and restating a hundred and fifty lines of it here to reach parity would be the "second calendar
 * path" this work was explicitly told not to build. So the hand-off is narrowed to the one thing it
 * cannot derive and the user must say — **when** — and the wheel it asks with is `TempoWheel`'s
 * public `TempoDateTimeWheel`, extracted in Phase 0's P2 for exactly this class of reuse.
 *
 * Three fields are therefore dropped rather than duplicated, each with somewhere honest to go:
 *
 * - **題名.** The title is the routine you just finished. `EventConfirmDialog` states it in full
 *   before anything is written, and 予定 renames it afterwards like any other event.
 * - **終日 / 場所.** A workout is an appointment with a clock, and a launcher has no business guessing
 *   where someone trains.
 * - **カレンダー.** `writableCalendars` already sorts the best default to the front — the Google
 *   primary, then any primary, then any Google — which is the same one the composer pre-selects. A
 *   device with several calendars gets the same default it would have got there, silently rather than
 *   as an unmade choice. This is the one narrowing with a real cost, and it is on the report.
 */

/**
 * 予定に入れる, and the small panel it opens.
 *
 * Rendered as a full-width centred action in `RepeatAction`'s geometry, so the two read as one column
 * of choices. **`03-player.md` §A COMPLETE's mock puts them side by side** — `もう一度` and
 * `予定に入れる` on one row — and that layout is not reachable from a `footer` slot that opens below
 * もう一度. Making it so is a change to `RecordSummary`, which this unit mounts rather than rewrites;
 * it is reported.
 *
 * @param routineName the event's title, verbatim. `SessionSummary.routineName`.
 * @param activeMs how long the session just took, which is how long the next one is booked for. See
 *   `gym/ScheduleNext.kt`'s [io.eddiegulay.tempo.gym.scheduleEnd] for why a measurement beats an
 *   estimate here.
 */
@Composable
fun ScheduleNextAction(
    routineName: String,
    activeMs: Long,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val context = LocalContext.current
    // Read from the table rather than a `const` — a compile-time constant is the one kind of label
    // that cannot be re-resolved on a language change at all.
    //
    // **Every other word on this panel is `s.calendar`'s.** This is the composer's flow narrowed to
    // one field, not a second one, and やめる / 保存 / 保存中 / 設定を開く are the words the 予定 page
    // already uses for the same buttons — `EventComposeScreen` and `CalendarScreen.AccessPrompt`. A
    // private set here would be a second calendar vocabulary to keep in step with the first.
    val s = LocalStrings.current
    val scheduleNext = s.gymShared.scheduleNext
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // One repository, constructed for this screen. It holds no cache and no connection — a resolver
    // handle and a retry flow — so this is not a second copy of any state, and reaching into
    // `LauncherViewModel` for the one the 予定 page uses would couple the gym shell to the launcher's
    // view model for a single insert.
    val repository = remember(context) { CalendarRepository(context) }

    var granted by remember { mutableStateOf(hasCalendarAccess(context)) }

    // True once the system dialog has come back with an answer, whatever the answer was. Nothing else
    // can distinguish "we have not asked yet" from "they said no": `granted` is false in both, and
    // `permanentlyDenied` is false in both too, because before the first request
    // `shouldShowRequestPermissionRationale` is also false (`CalendarPermissionState`'s own note).
    var answered by remember { mutableStateOf(false) }
    val permission = rememberCalendarPermissionState(granted = granted) {
        granted = it
        answered = true
    }

    // Access can be revoked in Settings while this screen is alive, and can be granted there too when
    // the request has been permanently denied. Same re-check on resume the calendar page makes.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasCalendarAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var expanded by remember { mutableStateOf(false) }
    // `EventComposeScreen.defaultStart` — the app's one answer to "when does a new event open?",
    // called rather than restated (`DECISIONS.md` §Q7).
    var start by remember { mutableStateOf(defaultStart()) }
    var calendars by remember { mutableStateOf<Loadable<List<CalendarInfo>>>(Loadable.Loading) }
    var pending by remember { mutableStateOf<PendingWrite?>(null) }
    var writing by remember { mutableStateOf(false) }
    var fault by remember { mutableStateOf<TempoFault?>(null) }

    // Non-null once the insert has landed. It is the *state* of this control, not a message: there is
    // no sourced sentence for "the event is in your calendar", and the app's own idiom for a control
    // that has done its job and cannot be used again is `RepeatAction`'s — greyed, `disabled()`, with
    // the fact underneath it. The fact here is the span, in the confirm dialog's own words.
    var scheduled by remember { mutableStateOf<EventDraft?>(null) }

    LaunchedEffect(granted, expanded) {
        if (granted && expanded) calendars = repository.writableCalendars()
    }

    val ready = calendars.valueOrNull()
    val calendarId = ready?.firstOrNull()?.id ?: -1L
    val draft = remember(routineName, activeMs, start, calendarId) {
        scheduleDraft(routineName, activeMs, start, calendarId)
    }

    // Precedence: a write that failed, then a read that failed, then the two structural reasons there
    // is nowhere to write to. Exactly `EventComposeScreen`'s ordering, for exactly its reason — a real
    // failure outranks a standing condition the user may already have read.
    val shown: TempoFault? = fault
        ?: (calendars as? Loadable.Failed)?.fault
        // Gated on [answered], not on `!granted`. The request is fired from the same tap that opens
        // this panel, so `!granted` alone renders 「カレンダーへのアクセスが必要です」 *underneath the
        // system dialog that is asking for it* — a "lost" message for access that was never held. It is
        // the term `EventComposeScreen`'s otherwise-identical chain deliberately does not have.
        ?: CalendarFault.PermissionLost.takeIf { answered && !granted }
        ?: CalendarFault.NoWritableCalendar.takeIf { ready != null && ready.isEmpty() }

    val recover: () -> Unit = {
        when (shown) {
            // `request()` already routes to Settings once the dialog is spent; what needs the fork is
            // the word, so the tap announces where it goes. See the [action] argument below.
            CalendarFault.PermissionLost -> permission.request()
            CalendarFault.NoWritableCalendar -> openAccountSettings(context)
            else -> {
                fault = null
                calendars = Loadable.Loading
                scope.launch { if (granted) calendars = repository.writableCalendars() }
            }
        }
    }

    pending?.let { proposal ->
        EventConfirmDialog(
            pending = proposal,
            onConfirm = {
                pending = null
                writing = true
                scope.launch {
                    when (val outcome = repository.insert(draft)) {
                        WriteOutcome.Ok -> {
                            scheduled = draft
                            expanded = false
                        }

                        is WriteOutcome.Failed -> fault = outcome.fault
                    }
                    writing = false
                }
            },
            onDismiss = { pending = null },
        )
    }

    Column(modifier.fillMaxWidth().animateContentSize(tween(220, easing = LinearOutSlowInEasing))) {
        val done = scheduled
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .pressable(TempoShapes.Row, enabled = done == null) {
                    expanded = !expanded
                    // Ask at the moment the user reaches for the calendar, which is the only moment
                    // the request has a reason the user can see.
                    if (expanded && !granted) permission.request()
                }
                .semantics {
                    role = Role.Button
                    contentDescription = scheduleNext
                    if (done != null) {
                        disabled()
                        stateDescription = draftSummary(done, s).when_
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = scheduleNext,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    // `03-player.md` §A COMPLETE's mock: もう一度 in c.accent, 予定に入れる in c.inkSoft.
                    // The accent is rationed to the action that repeats the workout.
                    color = if (done == null) c.inkSoft else c.inkFaint,
                ),
            )
        }

        if (done != null) {
            Spacer(Modifier.height(8.dp))
            WhenLine(draftSummary(done, s).when_)
            return@Column
        }

        if (!expanded) return@Column

        shown?.let { current ->
            Spacer(Modifier.height(12.dp))
            FaultStrip(
                fault = current,
                onRecover = recover,
                // 許可する promises a dialog. Once the system will no longer show one, the only way
                // back is Settings, and `CalendarScreen.AccessPrompt` already has the word for that —
                // *"a tap that silently throws the user into Settings with no warning reads as a bug"*,
                // which is `CalendarPermissionState.permanentlyDenied`'s own KDoc.
                action = s.calendar.accessOpenSettings.takeIf {
                    current == CalendarFault.PermissionLost && permission.permanentlyDenied
                },
            )
        }

        // No spinner and no 読み込み中 while the calendars resolve, and — the part that matters —
        // **no empty state either.** `00-plan.md` §4.1 rule 1 is about exactly this: a device we have
        // not finished asking and a device with no writable calendar are different facts, and only
        // the second one has a sentence. Until `Ready` arrives this control simply has no panel.
        if (!granted || ready.isNullOrEmpty()) return@Column

        Spacer(Modifier.height(14.dp))
        WhenLine(draftSummary(draft, s).when_)
        TempoDateTimeWheel(current = start, allDay = false, onChange = { start = it })

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(
                label = s.calendar.cancel,
                description = s.calendar.cancel,
                color = c.inkFaint,
                enabled = !writing,
                onClick = { expanded = false },
            )
            HeaderAction(
                label = if (writing) s.calendar.saving else s.calendar.save,
                // The description stays 保存 while the label says 保存中: the button's *name* does not
                // change when its state does.
                description = s.calendar.save,
                color = if (writing) c.inkFaint else c.accent,
                enabled = !writing,
                // Proposes only. `EventConfirmDialog` commits, as it does for every other write.
                onClick = { pending = PendingWrite.Create(draft) },
            )
        }
    }
}

/** `九月十日 ・ 19:30 – 19:37` — the span, in the confirm dialog's own words and its own type. */
@Composable
private fun WhenLine(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(fontFamily = Gothic, fontSize = 14.sp, color = c.inkSoft),
    )
}
