package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.faultCopy
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * A session that exists on disk and cannot be replayed — [SessionScreen.Unrecoverable] drawn.
 *
 * It is drawn from parts that already exist: [quitOptions] decides the rows, [PlayerSheetRow] is the
 * quit sheet's own row, [DiscardArm] is the quit sheet's own three-second window, and [faultCopy] is
 * the one fault table. **Not one string here is new** — 鍛錬を終えますか, ここまでを記録する,
 * 記録せずに終える / 終える, 本当に消しますか and まだ 記録するものがありません are all `QuitSheet.kt`'s,
 * sourced there to `03-player.md` §A GYM.SESSION.QUIT_SHEET.
 *
 * **It lived in `GymShell.kt` until now, and the duplication that came with it is gone.** It was
 * written there because it was the one player screen nobody had written when the player was wired up,
 * and an unhandled arm in `GymPage` is a blank screen in the middle of a workout — strictly the worst
 * of the three outcomes. The Phase 1 wiring agent disclosed the debt: the page belonged next to the
 * sheet, and the arming window and its two sentences belonged in one place rather than two. Both are
 * now done, with **no change to behaviour and no change to copy** — `DISCARD_ARMED_LABEL`,
 * `DISCARD_ARMED_DESCRIPTION`, `DISCARD_DESCRIPTION` and the 3s window are `QuitSheet.kt`'s, and this
 * page reads them.
 *
 * **つづける is removed, and that is the whole difference from the sheet.** There is nothing to continue
 * — the stored results cannot be landed on the recompiled timeline — so offering the escape would be
 * offering a door onto the same room. [SessionUnrecoverableState] carries `resultsWritten` for exactly
 * the reason the sheet does: at zero there is nothing to record, so ここまでを記録する is **omitted**
 * rather than disabled and the destructive row softens to 終える and asks once.
 *
 * *Rejected* — rendering this as a `PlayerSheet` so it matched the quit sheet pixel for pixel. A sheet
 * is a thing over something, and its scrim would dim an empty screen; worse, with つづける gone the
 * scrim tap and Back would have to be dead, which is a modal the user cannot dismiss. It is a page.
 *
 * *Rejected* — a subtitle when there is work to save. The sheet's is `quitSummaryLine(activeMs, …)` and
 * an unreplayable session has no timeline to measure those against. Saying nothing is honest; a summary
 * computed from the rows we have just declared unreadable would not be.
 */
@Composable
fun SessionUnrecoverablePage(
    state: SessionUnrecoverableState,
    actions: SessionActions,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val options = quitOptions(state.resultsWritten)
    val arm = rememberDiscardArm()

    // Which write failed, so もう一度 retries the one the user asked for. A failed **discard** has no
    // retry the host exposes, and answering it with a finish would be a wrong write dressed as a no-op
    // — `QuitSheet.kt` makes the same distinction for the same reason.
    var retryable by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.routineName,
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "鍛錬を終えますか",
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = c.inkSoft),
        )
        if (options.subtitleIsWarning) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "まだ 記録するものがありません",
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkFaint),
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))

        if (options.canRecord) {
            PlayerSheetRow(
                label = "ここまでを記録する",
                description = "ここまでを記録する",
                color = c.accent,
                enabled = !state.saving,
                onClick = {
                    retryable = true
                    actions.onQuitSaveRecording()
                },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
        }

        PlayerSheetRow(
            label = if (arm.armed) DISCARD_ARMED_LABEL else options.discardLabel,
            description = if (arm.armed) DISCARD_ARMED_DESCRIPTION else DISCARD_DESCRIPTION,
            color = c.inkFaint,
            enabled = !state.saving,
            assertive = arm.armed,
            onClick = {
                arm.press(options.confirmsDiscard) {
                    retryable = false
                    actions.onQuitDiscard()
                }
            },
        )

        state.fault?.let { fault ->
            Spacer(Modifier.height(12.dp))
            if (retryable) {
                FaultStrip(fault, onRecover = actions::onRetryFinish)
            } else {
                Text(
                    text = faultCopy(fault).message,
                    style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.accent),
                )
            }
        }
    }
}
