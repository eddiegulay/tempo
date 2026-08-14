package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes

/**
 * 休止 — the session frozen, honestly (`03-player.md` §A GYM.SESSION.PAUSED).
 *
 * **The ensō freezes at its current sweep and there is no animation**: the freeze *is* the
 * confirmation. That is also why [LivePlayer] swaps to this page without a cross-fade — a fade would
 * animate the one moment the spec says must not be animated.
 *
 * The countdown numeral **keeps its size and its position** and only drops from `c.ink` to
 * `c.inkSoft`. It is the one token that changes on pause. Moving or shrinking it would be exactly the
 * layout jump §3.1 forbids, and it would also make the pause feel like a different screen rather than
 * the same session held still.
 *
 * **Back does not resume.** The player's single back path opens the quit sheet from here as from
 * anywhere else (the host owns it) — "a back press must never restart a timer the user cannot see".
 *
 * **Skip-while-paused stays paused**, so ◁ and ▷ are live here: a user who paused to re-plan should be
 * able to re-plan without the clock starting under them.
 *
 * States: `Paused`; `PausedLong` (over a minute — 続ける gains 三秒の支度から and the resume routes
 * through a 3s 支度, which is [SessionOverlayUi.Paused.resumeNeedsPrepare], resolved once by the host
 * so the subtitle and the behaviour cannot disagree); `Stalled` (the 30-minute guard fired —
 * 長い間 動きがありません, and **no auto-save**: the user decides what a forgotten phone was worth);
 * `PausedDuringPrepare` (◁ and the elapsed lines hidden, because nothing has elapsed).
 *
 * Edge cases 1–5 are all the clock's, the store's or the cue engine's — an open REPS segment stops
 * accruing, an EMOM grid shifts whole, `pausedAtElapsed` is on disk before this page draws, a reboot
 * is caught by the boot anchor, and the cues are disarmed by the transition that got here.
 */
@Composable
fun PausedPage(
    state: SessionUiState,
    paused: SessionOverlayUi.Paused,
    actions: SessionActions,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val inPrepare = state.prepare != null || state.phase == Phase.PREPARE
    val resumeFocus = remember { FocusRequester() }

    // Frozen at entry. The clock is stopped, so these do not move — but reading them through
    // `remember` says so, and it is what keeps the single announcement single. Keyed on the language
    // so a switch while paused re-says it rather than leaving the previous one on the node.
    val announcement = remember(s.lang) {
        pausedAnnouncement(s, state.activeMs, state.stationsCompleted)
    }

    LaunchedEffect(Unit) {
        // 続ける is the default TalkBack focus target (§A PAUSED, accessibility): the first thing a
        // user hears on a screen they did not navigate to should be the way back out of it.
        runCatching { resumeFocus.requestFocus() }
    }

    PlayerFrame(
        state = state,
        actions = actions,
        ringColor = frozenRingColor(state),
        ringSweep = state.ensoSweepAngle,
        counter = phaseCounterLabel(state),
        controls = pausedControls(state, actions, inPrepare),
        modifier = modifier,
        announcement = announcement,
        ringContent = {
            Text(
                text = s.gymSession.pausedTitle,
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 6.sp, color = c.inkFaint),
            )
            Spacer(Modifier.height(4.dp))
            // 「残り 二十三秒、休止中」 spoken against `0:23` drawn — §Q4's sharpest case in Japanese,
            // and one string in English (§L7).
            val spoken = pausedNumeralDescription(s, state.remainingMs)
            HeroText(
                text = formatCountdown(s, state.remainingMs),
                base = 88.sp,
                // The one token that changes on pause.
                color = c.inkSoft,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            )
            state.exercise?.displayName(s)?.let { name ->
                Text(
                    text = name,
                    maxLines = 1,
                    // Catalogue content on one line: ellipsised rather than cut mid-glyph, the same
                    // treatment `ExerciseHeading` takes and for the same reason.
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.inkFaint),
                )
            }
        },
        below = {
            // §A PAUSED `PausedDuringPrepare`: nothing has elapsed, so nothing claims to have.
            if (!inPrepare) {
                Text(
                    text = elapsedLine(s, state.activeMs),
                    style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkSoft),
                )
                accruedLine(s, state.stationsCompleted, state.roundsCompleted)?.let { line ->
                    Text(
                        text = line,
                        style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            if (paused.stalled) {
                // The guard fired rather than the user tapping ┃┃. Said plainly, and nothing is saved
                // on the strength of it.
                Text(
                    text = s.gymSession.pausedStalled,
                    style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
                )
                Spacer(Modifier.height(8.dp))
            }

            ResumeButton(
                needsPrepare = paused.resumeNeedsPrepare,
                focusRequester = resumeFocus,
                onResume = actions::onResume,
            )
        },
    )
}

/**
 * 続ける, with the honest footnote when the pause ran long.
 *
 * 三秒の支度から is not decoration: after a minute away the session resumes through a 3s countdown
 * (§C.1 row 19), and a button that started the clock instantly after saying nothing would be the
 * surprise this line exists to remove.
 */
@Composable
private fun ResumeButton(
    needsPrepare: Boolean,
    focusRequester: FocusRequester,
    onResume: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val description = if (needsPrepare) resumeLongDescription(s) else s.gymSession.pausedResume
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NEXT_UP_SLOT)
            .background(c.card, TempoShapes.Card)
            .focusRequester(focusRequester)
            .playerPressable(TempoShapes.Card, onClick = onResume)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = s.gymSession.pausedResume,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Mincho, fontSize = 20.sp, letterSpacing = 4.sp, color = c.accent),
            )
            if (needsPrepare) {
                Text(
                    text = resumePrepareNote(s),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                )
            }
        }
    }
}

/**
 * ◁ ▶ ▷ — the bar with its middle glyph swapped, same 64.dp target.
 *
 * ◁ is hidden rather than dead during a paused 支度, for the same reason the elapsed lines are: there
 * is nothing behind it and nothing to say about that.
 */
@Composable
private fun pausedControls(
    state: SessionUiState,
    actions: SessionActions,
    inPrepare: Boolean,
): BarControls {
    val live = liveControls(state, actions)
    return live.copy(
        back = live.back.copy(state = if (inPrepare) ControlState.Hidden else live.back.state),
        middle = BarControl(
            glyph = "▶",
            description = LocalStrings.current.gymSession.pausedResume,
            onClick = actions::onResume,
        ),
    )
}

/**
 * The ring keeps the colour of the phase it froze in — §A PAUSED: "ring frozen, colour unchanged".
 *
 * The one place the colour is not simply the phase's is 限界まで, which was already `c.enso` before
 * the pause; asking the same question the live page asks keeps them identical.
 */
@Composable
private fun frozenRingColor(state: SessionUiState): Color {
    val c = LocalTempoColors.current
    return when {
        state.prepare != null || state.phase == Phase.PREPARE -> c.inkFaint
        state.phase == Phase.REST -> c.enso
        state.phase == Phase.REPS && state.prescribedReps == null -> c.enso
        else -> c.accent
    }
}
