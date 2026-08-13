package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.ui.CycleDots
import io.eddiegulay.tempo.ui.cycleDotsOverflow
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * 運動 — a time-boxed interval (`03-player.md` §A GYM.SESSION.WORK).
 *
 * **The ensō is the timer.** Not a progress bar, not a coloured background: a 220.dp ring in
 * `c.accent` that depletes from the gap and closes as the interval runs out, with the countdown
 * inside it. The one horizontal element on the page is the 1.dp session hairline, which measures the
 * *session* and is deliberately too faint to compete.
 *
 * There is nothing to press here. The interval ends itself at zero (`gate == AUTO`), and every
 * control on screen is a correction: ▷ skips, ◁ restarts, ◁◁ steps back, ┃┃ pauses. That is the
 * difference between this page and 運動・回数, where 済 is the only way forward.
 *
 * States, in the spec's numbering: 1 `Running`; 2 `LastThree` is **cue only** — the numeral does not
 * turn red, because the design has one accent and the ring already owns it; 3 `Halfway` is a cue with
 * no visual; 4 `LastRound` is the accent counter below; 5 `Paused` is [PausedPage]; 6 `NoNextUp` and
 * 7 `ZeroRest` are [nextUpLabel]'s two other branches; 8 `Skipped` is a write, not a picture.
 *
 * Edge cases 1–6 and 8 are the timeline's, the cue engine's and the host's — a segment shorter than
 * 3.5s suppresses its own 3-2-1, a doze is reconciled on foreground, ▷ is debounced at 250ms and
 * cancels the pending interval-end cue. **Edge case 7 is this page's**: the 88.sp numeral is capped
 * against the page width and never wraps (see `LocalHeroCap`).
 */
@Composable
fun WorkPage(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val name = state.exercise?.nameJa
    val nextLabel = nextUpLabel(state.next, state.nextExercise?.nameJa)
    val nextDescription = nextUpDescription(state.next, state.nextExercise?.nameJa)

    PlayerFrame(
        state = state,
        actions = actions,
        ringColor = c.accent,
        ringSweep = state.ensoSweepAngle,
        counter = phaseCounterLabel(state),
        controls = liveControls(state, actions),
        modifier = modifier,
        counterAccent = startsFinalRound(state.timeline, state.ordinal),
        // The countdown itself is **not** a live region; this node speaks at 30s and 10s and then
        // stops (§A WORK, accessibility). Anything finer fights the tones. `effectiveMs` — the
        // segment's own length, ＋二十秒 included — is passed because a threshold this interval never
        // had must not be announced at all: タバタ's twenty seconds are never 「残り 三十秒」.
        announcement = countdownAnnouncement(state.remainingMs, state.segment.effectiveMs),
        ringContent = {
            ExerciseHeading(name)
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatCountdown(state.remainingMs),
                softWrap = false,
                maxLines = 1,
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
            RoundIndicator(state)
            Spacer(Modifier.height(18.dp))
            // One line, never a card. The next-up slot is the same vertical anchor 済 occupies on
            // 運動・回数, which is what keeps the chrome pixel-stable between the two (§A REPS).
            Box(Modifier.fillMaxWidth().height(NEXT_UP_SLOT), contentAlignment = Alignment.Center) {
                Text(
                    text = nextLabel.orEmpty(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics {
                        if (nextDescription != null) contentDescription = nextDescription
                    },
                    style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, color = c.inkFaint),
                )
            }
        },
    )
}

/**
 * The slot below the ring, sized once so 運動 and 運動・回数 agree.
 *
 * §A REPS: "the next-up line is **replaced** by the 済 button at the same vertical anchor, so the
 * chrome stays pixel-stable between WORK and REPS — design §3.1's whole argument". 64.dp is 済's own
 * height, and the 13.sp line simply centres in it.
 */
internal val NEXT_UP_SLOT = 64.dp

/**
 * The exercise name, as one announced heading — §A WORK, accessibility.
 *
 * It changes exactly once per segment, which is what makes `liveRegion` safe on it where it is
 * forbidden on the numeral: the fact being announced is *which movement*, and that fact changes at a
 * transition and never between.
 */
@Composable
internal fun ExerciseHeading(name: String?, fontSize: TextUnit = 24.sp) {
    val c = LocalTempoColors.current
    Text(
        text = name.orEmpty(),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
        style = TextStyle(
            fontFamily = Mincho,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            letterSpacing = 2.sp,
            color = c.ink,
        ),
    )
}

/**
 * 「三巡目 ・ 四種目中 三」, built for whichever phase is asking.
 *
 * A rest carries no station index — the compiler emits none — so the number comes from the station
 * the rest is counting down *to*, which is the same station its hero already names. Blanking the
 * clause instead would make the top line disappear and come back at every transition, on a screen
 * whose whole design argument is that nothing moves.
 */
@Composable
internal fun phaseCounterLabel(state: SessionUiState): String? = counterLabel(
    round = state.round,
    totalRounds = state.totalRounds,
    station = state.stationIndex ?: state.next?.stationIndex,
    stationsPerRound = state.stationsPerRound,
    lastRound = startsFinalRound(state.timeline, state.ordinal),
)

/**
 * The round dots — 6.dp with `c.hair` pending, which is the player's geometry and not Focus's
 * (prerequisite P3 keeps Focus's 9.dp defaults so Focus is pixel-unchanged).
 *
 * Past nine rounds they become words (`DECISIONS.md` §Q8), and on a single-round routine they are
 * omitted entirely: one dot is not a count, and 七分間 is twelve stations in one lap.
 */
@Composable
internal fun RoundIndicator(state: SessionUiState, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    Box(modifier.fillMaxWidth().height(ROUND_INDICATOR_SLOT), contentAlignment = Alignment.Center) {
        when {
            state.totalRounds <= 1 -> Unit
            cycleDotsOverflow(state.totalRounds) -> Text(
                text = roundsOverflowLabel(state.round, state.totalRounds),
                style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
            )

            else -> CycleDots(
                total = state.totalRounds,
                filled = state.roundsCompleted,
                filledColor = c.accent,
                pendingColor = c.hair,
                dotSize = 6.dp,
                gap = 12.dp,
                label = cycleDotsLabel(state.round, state.totalRounds),
            )
        }
    }
}

/** Fixed, so a round rest hiding its dots does not lift the block below (§A REST state 3). */
internal val ROUND_INDICATOR_SLOT = 18.dp
