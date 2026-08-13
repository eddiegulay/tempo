package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * 支度 — five seconds to put the phone down and get into position (`03-player.md` §A GYM.SESSION.PREPARE).
 *
 * It draws the player's chrome with one thing missing and one thing added: ◁ is dead, because there is
 * nothing behind a countdown, and the ring is `c.inkFaint` rather than the accent, because nothing is
 * being spent yet.
 *
 * **The numeral is a bare integer**, not `0:03`. Five seconds is a count you say out loud, and the
 * clock form would make the threshold of a session look like the last five seconds of a plank
 * ([prepareNumeral] carries the argument). It stays **arabic** — design §14.3 and `DECISIONS.md` §Q4
 * settle that deliberately, and it is the same reasoning that keeps the flip clock arabic.
 *
 * Both countdowns render here: the session's own 支度 (a compiled segment) and the three seconds back
 * in from a long pause (§C.1 row 19, which has no segment at all). [SessionUiState.prepare] is the
 * only thing that tells them apart, and the difference the user sees is which movement the hero names
 * — §A PREPARE edge case 4 asks the resumed one for the exercise the session was interrupted *inside*,
 * which falls out for free from `state.exercise` being the interrupted segment's.
 *
 * Edge cases, in the spec's numbering:
 *
 * 1. `ANIMATOR_DURATION_SCALE == 0` — handled in [LivePlayer], which zeroes the phase cross-fade. The
 *    ring is unaffected by design: it is a value redraw driven by `stateAt`, never an `Animatable`.
 * 2. Backgrounding mid-countdown needs no code. `elapsedRealtime` keeps running and `stateAt` lands
 *    wherever it lands — the point of the whole timing architecture.
 * 3. The session-start cue fires at the **end** of 支度 and is the cue engine's, not this page's.
 * 4. Handled above: the resumed countdown's hero is the interrupted exercise.
 * 5. `prepareSeconds == 0` emits no segment, so this page is never composed for it (the compiler's
 *    guard, `TimelineCompiler.compile`).
 */
@Composable
fun PreparePage(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val remaining = state.prepare?.remainingMs ?: state.remainingMs
    val total = state.prepare?.totalMs ?: state.segment.effectiveMs
    val exercise = state.exercise ?: state.nextExercise
    val prescription = prescriptionLabel(upcomingSegment(state))

    // Frozen on the countdown's *length*, not on what is left of it: read live it would say 五秒後に,
    // 四秒後に, 三秒後に — one announcement per second, which is the failure `00-plan.md` §4.1 rule 5
    // exists to prevent, and it would talk over the very cue it is describing.
    val announcement = remember(total, exercise?.nameJa) {
        prepareAnnouncement(total, exercise?.nameJa)
    }

    PlayerFrame(
        state = state,
        actions = actions,
        ringColor = c.inkFaint,
        ringSweep = state.ensoSweepAngle,
        counter = null,
        controls = liveControls(state, actions),
        modifier = modifier,
        announcement = announcement,
        ringContent = {
            Text(
                text = "支度",
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 6.sp, color = c.inkFaint),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = prepareNumeral(remaining),
                softWrap = false,
                maxLines = 1,
                // Not a live region: announcing 5, 4, 3, 2, 1 collides with the tone cues and is worse
                // than silence (§A PREPARE, accessibility). The container above says it once.
                modifier = Modifier.clearAndSetSemantics { },
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = heroSize(88.sp),
                    fontFeatureSettings = "tnum",
                    color = c.ink,
                ),
            )
        },
        below = {
            UpcomingBlock(
                name = exercise?.nameJa,
                prescription = prescription,
                cue = exercise?.cue,
                modifier = Modifier.clearAndSetSemantics {
                    // One node, and it is the same sentence the entry announcement made: three
                    // separate nodes read as an orphaned name, an orphaned number and an orphaned
                    // instruction.
                    contentDescription = listOfNotNull(exercise?.nameJa, prescription, exercise?.cue)
                        .joinToString("、")
                },
            )
        },
    )
}

/**
 * The segment whose prescription 支度 is describing.
 *
 * The session's own countdown is a segment with no exercise, so the answer is the one after it; the
 * resumed countdown sits on top of a real station, so the answer is that station. One expression
 * covers both, and neither case needs [SessionUiState.prepare] to be consulted.
 */
private fun upcomingSegment(state: SessionUiState) =
    if (state.segment.exerciseId != null) state.segment else state.next
