package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.ui.faultCopy
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import kotlinx.coroutines.delay

/** How long 記録せずに終える stays armed after the first tap — §A QUIT_SHEET's second ask. */
private const val DISCARD_ARM_MS = 3_000L

/**
 * 本当に消しますか — the armed row's word, and its announcement.
 *
 * `internal` and here rather than at each call site because **two screens ask this question**: this
 * sheet, and [SessionUnrecoverablePage], which is the same two outcomes with つづける removed. Both are
 * §A QUIT_SHEET's copy; a second literal is how one of them ends up asking a slightly different
 * question than the one the user answered a moment ago on the other screen.
 */
internal const val DISCARD_ARMED_LABEL = "本当に消しますか"

/** Assertive on purpose: a destructive confirmation is the one place interrupting the user is right. */
internal const val DISCARD_ARMED_DESCRIPTION = "本当に消しますか、もう一度 押すと消えます"

/** 「これまでの記録は消えます」 is the whole reason this row is not the accent one. */
internal const val DISCARD_DESCRIPTION = "記録せずに終える、これまでの記録は消えます"

/**
 * The three-second window that makes 記録せずに終える ask twice.
 *
 * Hoisted out of both screens that draw the row, because a shared *window* is the part that must not
 * drift: a user who armed the row on the quit sheet and a user who armed it on the unrecoverable page
 * have the same three seconds to change their mind, and two `delay` literals is how that stops being
 * true. The **policy** stays at the call site — [press] takes `confirms`, because whether a second ask
 * is owed at all is `quitOptions(resultsWritten).confirmsDiscard`, a property of what there is to lose.
 *
 * A plain `remember`, deliberately, and this is §A QUIT_SHEET edge case 4: a rotation resets the
 * window. A rotate is not a tap, and the safe direction to fail is disarmed.
 */
@Stable
internal class DiscardArm {

    var armed by mutableStateOf(false)
        private set

    /**
     * The row was pressed. Arms on the first press when [confirms], and runs [onConfirmed] otherwise —
     * which covers both the second press and the case with nothing to lose, where §A deletes at once
     * because a mis-tap costs nothing.
     */
    fun press(confirms: Boolean, onConfirmed: () -> Unit) {
        if (armed || !confirms) {
            armed = false
            onConfirmed()
        } else {
            armed = true
        }
    }

    internal fun disarm() {
        armed = false
    }
}

/** [DiscardArm], with the window that disarms it. */
@Composable
internal fun rememberDiscardArm(): DiscardArm {
    val arm = remember { DiscardArm() }
    LaunchedEffect(arm.armed) {
        if (!arm.armed) return@LaunchedEffect
        delay(DISCARD_ARM_MS)
        arm.disarm()
    }
    return arm
}

private const val INTENT_NONE = 0
private const val INTENT_RECORD = 1
private const val INTENT_DISCARD = 2

/**
 * 鍛錬を終えますか — three honest outcomes for a session in progress, never two
 * (`03-player.md` §A GYM.SESSION.QUIT_SHEET).
 *
 * **The escape is つづける and it is never labelled やめる.** Tempo already uses やめる to mean "abandon"
 * in the calendar composer; reusing it here to mean "don't abandon" is a genuine bug in the language,
 * and the spec says in as many words: do not "fix" this to match the composer. つづける is also the one
 * row that appears in every shape of this sheet, because it is the way out of the question.
 *
 * **This is the sheet a user is most likely to mis-tap**, and every decision here follows from that:
 * the clock is already stopped before it opens (a sheet that let the timer run would record the
 * deciding), the scrim and Back are both つづける and never a discard, TalkBack focus lands on the
 * **title** rather than on a destructive row, and 記録せずに終える asks twice whenever there is work to
 * lose.
 *
 * The three states:
 *
 * - `Standard` — all three rows. 記録せずに終える arms for three seconds and needs a second tap.
 * - `NothingToSave` — zero results: ここまでを記録する is **omitted** rather than disabled (there is
 *   nothing to record), the subtitle says so, and the destructive row softens to 終える and asks once,
 *   because a mis-tap now costs nothing ([quitOptions]).
 * - `Saving` / `SaveFailed` — the rows go non-interactive and **the sheet stays open**. Never navigate
 *   away on a failed save: the session is still there, and so is the decision.
 *
 * Edge cases: 1 (killed with the sheet open — the row survives with `pausedAtElapsed` set and the
 * resume prompt asks on the next cold start) and 2 (a partial over an open set gets
 * `actualReps = null`, never a count guessed from the pacer) are the machine's; 5 (a partial with zero
 * rounds is legal) is the record's. **Edge cases 3 and 4 are this sheet's**: the scrim is つづける, and
 * a rotation resets the arming window — which is why the arm is a plain `remember` rather than a
 * `rememberSaveable`. A rotate is not a tap, and the safe direction to fail is disarmed.
 */
@Composable
fun QuitSheet(
    state: SessionUiState,
    quit: SessionOverlayUi.Quit,
    actions: SessionActions,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val options = quitOptions(state.resultsWritten)
    val titleFocus = remember { FocusRequester() }

    val arm = rememberDiscardArm()
    // Which write is in flight, so a failure can be retried with the same one. Kept in saveable state
    // for the reason `ResumePromptRetry` was hoisted out of composition: the fault outlives the frame
    // that raised it, and answering a failed **delete** by saving would be a wrong write rather than
    // the no-op it looks like.
    var intent by rememberSaveable { mutableIntStateOf(INTENT_NONE) }

    LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }

    // The sheet is composed only while the overlay is up, so it would otherwise arrive already
    // visible and skip its own entrance. One frame of `false` is what gives it the 240ms slide.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    PlayerSheet(
        visible = shown,
        onDismiss = actions::onQuitDismissed,
        modifier = modifier,
        dismissable = !quit.saving,
    ) {
        Text(
            text = "鍛錬を終えますか",
            modifier = Modifier.focusRequester(titleFocus).focusable(),
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = c.inkSoft),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (options.subtitleIsWarning) {
                "まだ 記録するものがありません"
            } else {
                quitSummaryLine(state.activeMs, state.stationsCompleted, state.stationsPlanned)
            },
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkFaint),
        )
        Spacer(Modifier.height(12.dp))
        SheetDivider()

        if (options.canRecord) {
            PlayerSheetRow(
                label = "ここまでを記録する",
                description = "ここまでを記録する",
                color = c.accent,
                enabled = !quit.saving,
                onClick = {
                    intent = INTENT_RECORD
                    actions.onQuitSaveRecording()
                },
            )
            SheetDivider()
        }

        PlayerSheetRow(
            label = if (arm.armed) DISCARD_ARMED_LABEL else options.discardLabel,
            description = if (arm.armed) DISCARD_ARMED_DESCRIPTION else DISCARD_DESCRIPTION,
            color = c.inkFaint,
            enabled = !quit.saving,
            assertive = arm.armed,
            // Under one completed segment there is nothing to lose, so `confirmsDiscard` is false and
            // [DiscardArm.press] deletes immediately rather than arming.
            onClick = {
                arm.press(options.confirmsDiscard) {
                    intent = INTENT_DISCARD
                    actions.onQuitDiscard()
                }
            },
        )
        SheetDivider()

        PlayerSheetRow(
            label = "つづける",
            description = "つづける",
            color = c.inkSoft,
            enabled = !quit.saving,
            onClick = actions::onQuitDismissed,
        )

        quit.fault?.let { fault ->
            Spacer(Modifier.height(12.dp))
            if (intent == INTENT_RECORD) {
                // §A QUIT_SHEET's `SaveFailed`, verbatim — and もう一度 re-runs the finish itself
                // rather than the transition, which has already moved the machine past the sheet.
                SaveFailedLine(onRetry = actions::onRetryFinish, enabled = !quit.saving)
            } else {
                // A failed **discard** has no retry to offer: the machine's row 23 fires once and the
                // host exposes no equivalent of `onRetryFinish` for it (reported as a gap). Stating
                // the fault with no button is honest; a もう一度 that silently did nothing is not.
                Text(
                    text = faultCopy(fault).message,
                    style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.accent),
                )
            }
        }
    }
}

/** 「記録できませんでした ・ もう一度」 — one line, and the sheet does not move. */
@Composable
private fun SaveFailedLine(onRetry: () -> Unit, enabled: Boolean) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "記録できませんでした",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
        )
        Text(
            text = " ・ ",
            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkFaint),
        )
        Text(
            text = "もう一度",
            modifier = Modifier
                .sizeIn(minHeight = 44.dp)
                .clickable(enabled = enabled, onClick = onRetry)
                .clearAndSetSemantics {
                    contentDescription = "もう一度"
                    role = Role.Button
                    if (!enabled) disabled()
                }
                .padding(vertical = 12.dp),
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}

/**
 * One door of the sheet: a word on the left, an arrow on the right, the whole 56.dp row tappable.
 *
 * The same shape the resume prompt's rows use, deliberately — these are the two places in 鍛錬 where a
 * modal asks a question with three answers, and they should read as the same object.
 */
@Composable
internal fun PlayerSheetRow(
    label: String,
    description: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    assertive: Boolean = false,
) {
    val c = LocalTempoColors.current
    val shown = if (enabled) color else c.inkFaint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (assertive) liveRegion = LiveRegionMode.Assertive
                if (!enabled) disabled()
            }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = shown),
        )
        Text(
            text = "→",
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, color = shown),
        )
    }
}

@Composable
private fun SheetDivider() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
}
