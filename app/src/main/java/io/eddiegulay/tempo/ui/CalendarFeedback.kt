package io.eddiegulay.tempo.ui

import io.eddiegulay.tempo.calendar.displayTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.calendar.PendingWrite
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import java.time.LocalDateTime

/**
 * What to say when something goes wrong, and what the user can do about it.
 *
 * Every fault has words, and every fault the user can actually fix has an action. [action] is null
 * only where there is genuinely nothing to press — and even then we still say what happened, because
 * an explained dead end is not a dead end: the user knows to go elsewhere.
 */
data class FaultCopy(val message: String, val action: String?)

/**
 * The one place a fault becomes words, for every feature that raises one.
 *
 * Dispatch is by family and then by case, rather than one flat table, so each family stays
 * exhaustively matched by the compiler — [TempoFault] cannot be `sealed` (see its docs) and a flat
 * `when` would need an `else` that quietly swallowed a newly added calendar or gym case. The `else`
 * here can only be reached by a *third* family nobody has written yet, and even that one gets words.
 *
 * [strings] is a required parameter rather than a global read: this is a pure function, the copy tests
 * that pin it run on plain JUnit with no Compose and no `Context`, and that is deliberate (§L4).
 * Composables pass `LocalStrings.current`.
 */
fun faultCopy(fault: TempoFault, strings: Strings): FaultCopy = when (fault) {
    is CalendarFault -> calendarFaultCopy(fault, strings)
    is GymFault -> gymFaultCopy(fault, strings)
    // Unreachable today, and not a placeholder for copy that exists: no written fault reaches this
    // branch. It exists so that "every fault says something" survives a family we have not thought of
    // yet — see [TempoFault]'s doctrine, which permits a third family to be vague but never silent.
    else -> FaultCopy(strings.fault.unknownFamily, strings.fault.retry)
}

/**
 * Whether a fault leaves the user with nothing to press.
 *
 * **Language-independent by construction.** [faultCopy] decides *whether* there is an action from the
 * fault alone — the `when` arms that pass `null` pass it in every language — so the answer cannot
 * differ between tables, and `CalendarFeedbackTest` pins that it does not. Callers that only want the
 * *shape* of the strip therefore need no [Strings] of their own, which keeps a predicate about layout
 * from acquiring a dependency on copy.
 */
fun faultHasAction(fault: TempoFault): Boolean = faultCopy(fault, StringsJa).action != null

private fun calendarFaultCopy(fault: CalendarFault, strings: Strings): FaultCopy {
    val s = strings.fault
    return when (fault) {
        CalendarFault.PermissionLost ->
            FaultCopy(s.calendar.permissionLost, s.calendar.permissionLostAction)

        CalendarFault.NoWritableCalendar ->
            FaultCopy(s.calendar.noWritableCalendar, s.calendar.noWritableCalendarAction)

        CalendarFault.EventGone ->
            FaultCopy(s.calendar.eventGone, s.calendar.eventGoneAction)

        CalendarFault.Rejected ->
            FaultCopy(s.calendar.rejected, s.retry)

        CalendarFault.NoCalendarApp ->
            FaultCopy(s.calendar.noCalendarApp, null)

        is CalendarFault.Unknown ->
            FaultCopy(s.calendar.unknown, s.retry)
    }
}

/**
 * 鍛錬's faults, in the same shape and the same chrome as the calendar's.
 *
 * **The unreadable-store sentence is the load-bearing one here, and the reason is what it cannot be
 * mistaken for.** A store we could not read and a store with nothing in it are one pixel apart on
 * screen and worlds apart in meaning: 記録はありません tells the user their training history does not
 * exist, and a user who believes that stops looking for it. 記録を読めません carries no ありません at
 * all, so it cannot be read as emptiness even at a glance — it says the 記録 is there and *we* are the
 * ones who failed. English does the same job with different machinery: "Cannot read your records"
 * carries no *no*, *none*, *empty* or *nothing*. That distinction is the whole point of routing gym
 * reads through `Loadable`/[GymFault] rather than an empty list, and `CalendarFeedbackTest` pins it in
 * both languages rather than pinning one language's spelling of it.
 *
 * Every case's message and action is fixed by `.planning/exercise/DECISIONS.md` §Q6, which sourced
 * each one to the string tables (`04-library-records.md` §6, `01-shell.md`, `03-player.md`). Nothing
 * on this page is an implementer's guess, and nothing falls through to a generic.
 */
private fun gymFaultCopy(fault: GymFault, strings: Strings): FaultCopy {
    val s = strings.fault
    return when (fault) {
        // The store is there; we could not read it. Retrying is the remedy for all four — a lock that
        // clears, a quarantine that has already happened, a downgrade that gets reinstalled, the
        // unforeseen.
        GymFault.StoreCorrupt,
        GymFault.StoreReset,
        is GymFault.StoreUnavailable,
        is GymFault.Unknown,
        -> FaultCopy(s.gym.storeUnreadable, s.retry)

        // No action: retrying cannot free disk, and offering a button that cannot work is worse than none.
        GymFault.StoreFull ->
            FaultCopy(s.gym.storeFull, null)

        // Both pop the page they occur on rather than sit on it, so there is nothing left here to press.
        GymFault.RoutineGone ->
            FaultCopy(s.gym.routineGone, null)

        GymFault.SessionGone ->
            FaultCopy(s.gym.sessionGone, null)

        // A CHECK constraint refused the row: the same draft will be refused again, so no もう一度.
        GymFault.Rejected ->
            FaultCopy(s.gym.rejected, null)
    }
}

/**
 * A fault, stated in place with its way out — never a toast, which would slide away before the user
 * had finished reading it and leave them with a screen that simply looks broken.
 *
 * Vermillion on washi: the same accent the app uses for anything that wants a decision. It sits above
 * the fields it concerns, and the composer stays exactly as the user left it behind it.
 */
/**
 * @param action overrides the action **word** only — never the message, and never whether there is an
 *   action at all. It exists for the one case where the same fault has two destinations: a calendar
 *   permission that can still be asked for says 許可する, and one the system will no longer prompt for
 *   can only be reached through Settings, which `CalendarScreen.AccessPrompt` already answers with
 *   設定を開く. A tap that throws the user out of the app has to say so first. Both words are the
 *   string table's; nothing here may be assembled.
 */
@Composable
fun FaultStrip(
    fault: TempoFault,
    onRecover: () -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null,
) {
    val c = LocalTempoColors.current
    val copy = faultCopy(fault, LocalStrings.current)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TempoShapes.Row)
            .background(c.card)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .semantics { contentDescription = copy.message },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copy.message,
            modifier = Modifier.weight(1f, fill = true).padding(end = 12.dp),
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.sp, color = c.inkSoft),
        )
        // 44dp before, which is nobody's accessible minimum — it was the one control in the app under
        // the floor, and it is the control a user reaches for when something has already gone wrong.
        (action ?: copy.action)?.let { label ->
            Text(
                text = label,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onRecover)
                    .semantics { contentDescription = label }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.accent),
            )
        }
    }
}

/**
 * A whole page that failed. Distinct from an empty one on purpose: "予定はありません" is a claim about
 * the user's day, and we may only make it when we know it to be true.
 */
@Composable
fun FaultPanel(fault: TempoFault, onRecover: () -> Unit) {
    val c = LocalTempoColors.current
    val copy = faultCopy(fault, LocalStrings.current)

    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = copy.message,
                style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 2.sp, color = c.inkSoft),
            )
            copy.action?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .pressable(TempoShapes.Word, role = Role.Button, onClick = onRecover)
                        .semantics { contentDescription = label }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 3.sp, color = c.accent),
                )
            }
        }
    }
}

/**
 * The confirmation gate for every change Tempo makes to the calendar.
 *
 * A calendar is not private state: what is written here lands on the user's laptop, and — if the
 * event has guests — in their inboxes. A launcher is a place of quick taps and pocket-presses, so
 * each change restates itself in full and waits to be told yes.
 */
@Composable
fun EventConfirmDialog(pending: PendingWrite, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    // One `when`, not three. The heading, the button and the consequence are a set that has to agree,
    // and three parallel `when`s over the same subject is three chances for them to stop agreeing.
    val copy = when (pending) {
        is PendingWrite.Create -> s.dialog.eventCreate
        is PendingWrite.Update -> s.dialog.eventUpdate
        is PendingWrite.Delete -> s.dialog.eventDelete
    }
    val summary = when (pending) {
        is PendingWrite.Create -> draftSummary(pending.draft, s)
        is PendingWrite.Update -> draftSummary(pending.draft, s)
        is PendingWrite.Delete -> eventSummary(pending.event, s)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = copy.heading,
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            Column {
                Text(
                    text = summary.title,
                    style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, color = c.ink),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary.when_,
                    style = TextStyle(fontFamily = Gothic, fontSize = 14.sp, color = c.inkSoft),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = copy.consequence,
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp, color = c.inkFaint),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(copy.confirm, style = TextStyle(fontFamily = Mincho, color = c.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.dialog.dismiss, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}

/** A change restated in the two lines that matter: what it is, and when it is. */
internal data class WriteSummary(val title: String, val when_: String)

/**
 * The confirmation's two lines for a draft.
 *
 * The blank title is resolved to the placeholder **here**, at the point of display, and nowhere near
 * the point of write — that inversion is the whole of the `（無題）` fix: the placeholder used to be
 * substituted on the way *in*, which made editing an untitled event save it into `Events.TITLE` and
 * sync a Japanese placeholder to Google and to every guest.
 */
internal fun draftSummary(draft: EventDraft, strings: Strings): WriteSummary = WriteSummary(
    title = draft.title.trim().ifBlank { strings.calendar.untitled },
    when_ = span(draft.start, draft.end, draft.allDay, strings),
)

internal fun eventSummary(event: CalendarEvent, strings: Strings): WriteSummary = WriteSummary(
    title = event.displayTitle(strings),
    when_ = span(event.startDateTime(), event.endDateTime(), event.allDay, strings),
)

private fun span(start: LocalDateTime, end: LocalDateTime, allDay: Boolean, strings: Strings): String {
    val date = strings.fmt.monthDay(start)
    val sep = strings.fmt.separator
    return if (allDay) {
        date + sep + strings.calendar.allDay
    } else {
        date + sep + strings.fmt.clockAt(start) + " – " + strings.fmt.clockAt(end)
    }
}
