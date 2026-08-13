package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.gym.session.RestKind
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * 休息 — recovery, with the next movement already promoted to hero
 * (`03-player.md` §A GYM.SESSION.REST).
 *
 * The chrome is **pixel-identical to 運動**. Only three things change: the ring is `c.enso`, the
 * numeral is 72.sp in `c.inkSoft` (smaller and softer than 運動 — deliberate; a rest is not the
 * event), and the block under the ring names what is coming rather than what is left.
 *
 * **＋二十秒 and とばす are disabled on a mandated rest, and that is not a styling detail.** Barbara's
 * three minutes are part of the prescription: skipping them makes the resulting time incomparable to
 * anyone else's Barbara, so this is the one place the player refuses an action — and it refuses it
 * *visibly*, with the reason attached, because a control that vanishes teaches the user it was never
 * there (§A REST state 4 and edge case 3). An EMOM remainder is the other refusal and takes the
 * opposite treatment: its controls are **hidden**, because the minute is anchored and there was never
 * anything to skip.
 *
 * Two ▷ affordances coexist on purpose. The inline とばす ▷ is the semantic "I'm ready, go" — the
 * common case, thumb-reachable — and the bar's ▷ is the structural skip. Rest is the one phase where
 * skipping forward is the *expected* action rather than a correction, so both stay.
 *
 * Edge cases: 1 (a ＋二十秒 that lands after the frontier moved) and 6 (a rest slept through, back-
 * filled by `reconcile`) are the machine's and the host's; 2 (no cap on ＋二十秒 — autoregulation is
 * the point) needs no code; 4 (a rest under 4s suppresses its own 3-2-1 and still renders at 72.sp) is
 * the cue engine's rule and this page's non-decision. **Edge cases 3 and 5 are this page's**: the
 * disabled controls are rendered greyed rather than removed, and a movement with no form cue leaves
 * the third line blank rather than re-centring the block ([UpcomingBlock]).
 */
@Composable
fun RestPage(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val kind = state.restKind
    val next = state.next
    val exercise = state.nextExercise
    val prescription = prescriptionLabel(next)
    val added = extendedSuffix(state.addedMs)

    // One announcement on entry, and the form cue rides along with it: it is one clause and it is the
    // useful part, because a rest is the only moment the user is not yet moving and can still act on
    // it (§A REST, accessibility).
    val announcement = remember(state.ordinal, exercise?.nameJa) {
        restAnnouncement(
            kind = kind,
            restMs = state.segment.effectiveMs,
            nextExerciseName = exercise?.nameJa,
            prescription = prescription,
            cue = exercise?.cue,
        )
    }

    PlayerFrame(
        state = state,
        actions = actions,
        ringColor = c.enso,
        ringSweep = state.ensoSweepAngle,
        counter = phaseCounterLabel(state),
        controls = liveControls(state, actions),
        modifier = modifier,
        announcement = announcement,
        ringContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = restLabel(kind),
                    style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 6.sp, color = c.inkFaint),
                )
                // The added time is visible in the ring's own label, because it has to be visible in
                // the record's mental model: a rest that ran 0:35 was a rest the user chose to grow.
                added?.let { suffix ->
                    Text(
                        text = suffix,
                        modifier = Modifier.padding(start = 4.dp),
                        style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, color = c.accent),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatCountdown(state.remainingMs),
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = heroSize(72.sp),
                    fontFeatureSettings = "tnum",
                    color = c.inkSoft,
                ),
            )
            Text(
                text = "つぎ",
                style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 4.sp, color = c.inkFaint),
            )
        },
        below = {
            // §A REST state 3: the dots are hidden through a round rest. The round has just ended and
            // the count is mid-transition, so this is the one moment they would lie — 巡の間 in the
            // ring says the same thing without flickering between n and n+1.
            if (kind == RestKind.ROUND) Spacer(Modifier.height(ROUND_INDICATOR_SLOT)) else RoundIndicator(state)
            Spacer(Modifier.height(10.dp))
            UpcomingBlock(
                name = exercise?.nameJa,
                prescription = prescription,
                cue = exercise?.cue,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = listOfNotNull(exercise?.nameJa, prescription, exercise?.cue)
                        .joinToString("、")
                },
            )
            Spacer(Modifier.height(10.dp))
            InlineRestControls(state, actions)
        },
    )
}

/**
 * ＋二十秒 and とばす ▷, side by side under the hero.
 *
 * 48.dp targets on 14.sp text, which means padding rather than sizing — the words stay the size the
 * mock draws them and the touch area grows around them.
 */
@Composable
private fun InlineRestControls(state: SessionUiState, actions: SessionActions) {
    // §A REST state 5: inside an EMOM remainder neither control exists. The slot is kept so the page
    // does not grow and shrink as the grid hands out its leftovers.
    if (state.restKind == RestKind.EMOM_REMAINDER) {
        Spacer(Modifier.height(48.dp))
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineControl(
            label = EXTEND_REST_LABEL,
            description = extendRestDescription(),
            enabled = state.canExtendRest,
            // 「四十秒 追加済み」 — the control reports what it has already done, so a user who has
            // tapped it three times is not left counting taps.
            state = addedStateDescription(state.addedMs),
            disabledReason = extendDisabledDescription(state.restKind),
            onClick = actions::onExtendRest,
        )
        Spacer(Modifier.width(40.dp))
        InlineControl(
            label = SKIP_REST_LABEL,
            description = "とばす",
            enabled = state.canSkipForward,
            state = null,
            disabledReason = skipDisabledDescription(state.restKind),
            onClick = actions::onSkipForward,
        )
    }
}

@Composable
private fun InlineControl(
    label: String,
    description: String,
    enabled: Boolean,
    state: String?,
    disabledReason: String?,
    onClick: () -> Unit,
) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                // A disabled control states **why**, never a silent no-op (§A REST, accessibility).
                contentDescription = if (enabled) description else (disabledReason ?: description)
                role = Role.Button
                if (state != null) stateDescription = state
                if (!enabled) disabled()
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = if (enabled) c.accent else c.inkFaint,
            ),
        )
    }
}
