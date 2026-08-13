package io.eddiegulay.tempo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Shown when the user tries to un-hide an app whose 10-day block hasn't elapsed. A live countdown to
 * the unlock moment drives home that the commitment can't be cut short.
 *
 * @param nowProvider the guarded clock (never runs backwards) so the timer can't be cheated by
 * winding the system clock back.
 */
@Composable
fun BlockedInfoDialog(
    appLabel: String,
    unlockAt: Long,
    nowProvider: () -> Long,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    var now by remember { mutableLongStateOf(nowProvider()) }
    LaunchedEffectTick { now = nowProvider() }
    val remaining = (unlockAt - now).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = s.block.blockedHeading,
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = s.block.blockedBody(appLabel),
                    style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkSoft, letterSpacing = 0.5.sp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = formatCountdown(remaining, s),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Mincho, fontSize = 34.sp, color = c.accent, letterSpacing = 2.sp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = s.block.blockedFootnote,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint, letterSpacing = 2.sp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.block.blockedDismiss, style = TextStyle(fontFamily = Mincho, color = c.accent))
            }
        },
    )
}

/** Re-evaluate [onTick] once per second while the dialog is shown. */
@Composable
private fun LaunchedEffectTick(onTick: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            onTick()
        }
    }
}

/**
 * "六日 23:59:58" / "6 days 23:59:58" while days remain, else the bare clock.
 *
 * The day count goes through `fmt.days`, so Japanese now spells it in kanji where it used to be
 * arabic. That follows §Q4 — a value the user *reads* is spelled out, a value they *watch* is not —
 * and the day field is the half of this line that does not tick: it changes once a day, while the
 * clock beside it stays arabic and keeps moving. Worth a second look from design, because it is the
 * one place in the app where the two conventions sit on the same line.
 *
 * `Locale.ROOT` on the clock for the reason §L8 gives: an unqualified `format` follows the device
 * locale and renders Arabic-Indic digits on an ar-* phone.
 */
private fun formatCountdown(millis: Long, strings: Strings): String {
    val totalSeconds = millis / 1000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val clock = "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    return if (days > 0) strings.fmt.days(days.toInt()) + " " + clock else clock
}
