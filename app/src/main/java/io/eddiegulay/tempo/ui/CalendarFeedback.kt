package io.eddiegulay.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.CalendarFault
import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.calendar.PendingWrite
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.time.LocalDateTime

/**
 * What to say when something goes wrong, and what the user can do about it.
 *
 * Every fault has words, and every fault the user can actually fix has an action. [action] is null
 * only where there is genuinely nothing to press — and even then we still say what happened, because
 * an explained dead end is not a dead end: the user knows to go elsewhere.
 */
data class FaultCopy(val message: String, val action: String?)

fun faultCopy(fault: CalendarFault): FaultCopy = when (fault) {
    CalendarFault.PermissionLost ->
        FaultCopy("カレンダーへのアクセスが必要です", "許可する")

    CalendarFault.NoWritableCalendar ->
        FaultCopy("書き込めるカレンダーがありません", "アカウントを追加")

    CalendarFault.EventGone ->
        FaultCopy("この予定はもうありません。ほかの端末で削除されたようです", "予定へ戻る")

    CalendarFault.Rejected ->
        FaultCopy("この予定を保存できませんでした", "もう一度")

    CalendarFault.NoCalendarApp ->
        FaultCopy("カレンダーのアプリが見つかりません", null)

    is CalendarFault.Unknown ->
        FaultCopy("カレンダーにつながりません", "もう一度")
}

/**
 * A fault, stated in place with its way out — never a toast, which would slide away before the user
 * had finished reading it and leave them with a screen that simply looks broken.
 *
 * Vermillion on washi: the same accent the app uses for anything that wants a decision. It sits above
 * the fields it concerns, and the composer stays exactly as the user left it behind it.
 */
@Composable
fun FaultStrip(fault: CalendarFault, onRecover: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val copy = faultCopy(fault)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = copy.message },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copy.message,
            modifier = Modifier.weight(1f, fill = true).padding(end = 12.dp),
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.sp, color = c.inkSoft),
        )
        copy.action?.let { label ->
            Text(
                text = label,
                modifier = Modifier
                    .sizeIn(minHeight = 44.dp)
                    .clickable(onClick = onRecover)
                    .semantics { role = Role.Button; contentDescription = label }
                    .padding(vertical = 12.dp),
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
fun FaultPanel(fault: CalendarFault, onRecover: () -> Unit) {
    val c = LocalTempoColors.current
    val copy = faultCopy(fault)

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
                        .sizeIn(minHeight = 48.dp)
                        .clickable(onClick = onRecover)
                        .semantics { role = Role.Button; contentDescription = label }
                        .padding(vertical = 12.dp),
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

    val heading = when (pending) {
        is PendingWrite.Create -> "予定を加えますか"
        is PendingWrite.Update -> "予定を変えますか"
        is PendingWrite.Delete -> "予定を削除しますか"
    }
    val confirmLabel = when (pending) {
        is PendingWrite.Create -> "加える"
        is PendingWrite.Update -> "変える"
        is PendingWrite.Delete -> "削除する"
    }
    // The consequence, not the mechanism. The user knows they pressed save; what they may not have in
    // mind is that this reaches every device they own, and that a deletion cannot be taken back.
    val consequence = when (pending) {
        is PendingWrite.Create -> "ほかの端末のカレンダーにも表示されます。"
        is PendingWrite.Update -> "変更はほかの端末のカレンダーにも反映されます。"
        is PendingWrite.Delete -> "ほかの端末のカレンダーからも消えます。元に戻せません。"
    }
    val summary = when (pending) {
        is PendingWrite.Create -> draftSummary(pending.draft)
        is PendingWrite.Update -> draftSummary(pending.draft)
        is PendingWrite.Delete -> eventSummary(pending.event)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = heading,
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
                    text = consequence,
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp, color = c.inkFaint),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = TextStyle(fontFamily = Mincho, color = c.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("やめる", style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}

/** A change restated in the two lines that matter: what it is, and when it is. */
internal data class WriteSummary(val title: String, val when_: String)

internal fun draftSummary(draft: EventDraft): WriteSummary = WriteSummary(
    title = draft.title.trim().ifBlank { "（無題）" },
    when_ = span(draft.start, draft.end, draft.allDay),
)

internal fun eventSummary(event: CalendarEvent): WriteSummary = WriteSummary(
    title = event.title,
    when_ = span(event.startDateTime(), event.endDateTime(), event.allDay),
)

private fun span(start: LocalDateTime, end: LocalDateTime, allDay: Boolean): String {
    val date = JapaneseDate.monthDay(start)
    return if (allDay) {
        "$date ・ 終日"
    } else {
        "$date ・ ${JapaneseDate.clock(start)} – ${JapaneseDate.clock(end)}"
    }
}
