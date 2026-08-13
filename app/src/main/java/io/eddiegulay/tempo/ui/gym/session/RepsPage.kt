package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.ui.ENSO_SWEEP_DEGREES
import io.eddiegulay.tempo.ui.TempoValueWheel
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * 運動・回数 — a self-paced set (`03-player.md` §A GYM.SESSION.REPS).
 *
 * **済 is the gate, and nothing else is.** The segment is open, its `plannedMs` is a *pacing estimate*
 * and is not authoritative, and the estimate running out advances nothing: the ring holds at empty,
 * the 目安 line turns into a count-up in `c.accent`, and the page waits. That is hard product rule 2,
 * and it is why the only thing on this screen that closes a segment is a button under the user's
 * thumb.
 *
 * Nothing about overrun is a failure and the page says so by not changing: no pulse, no red, no
 * shrinking button. §10 forbids loops, and the ring "does not refill and does not pulse".
 *
 * The 済 button sits where 運動's next-up line sits, at the same vertical anchor ([NEXT_UP_SLOT]), so
 * the chrome does not move between a station and a set. It is the largest target in the app —
 * 64.dp × (width − 44.dp) — correctly, because it is pressed with a shaking hand.
 *
 * States: 1 `Pacing` and 2 `Overrun` are [pacerLabel]'s two branches; 3 `MaxEffort` swaps the hero for
 * 限界まで, drops the estimate line and holds the ring full in `c.enso`; 4 `AutoAdvanceArmed` is
 * invisible by design — the only difference is that the state ends itself, and the host owns it;
 * 5 `EmomWindow` replaces the estimate with what is left of the minute; 6 `RepAdjusting` is the wheel
 * below; 7 `Paused` is [PausedPage].
 *
 * Edge cases: 1 (the 30-minute stall), 2 (the hairline's denominator), 3 (`actualReps > prescribed`
 * is legal and unclamped), 4 (a wheel entry of 0 is recorded as とばした — passed straight through,
 * because `SessionMachine` already decides that and re-deriving it here would be a second opinion),
 * 6 (the 300ms debounce **and** the ordinal key) and 7 (AMRAP appending a round) are all the host's or
 * the machine's. **Edge case 5 is this page's**: the wheel is `rememberSaveable`, so a configuration
 * change cannot lose a count the user has already dialled, and the clock is untouched while it is
 * open. **Edge case 8** — 済's light CLICK, distinct from the heavy interval-end waveform — is the cue
 * engine's `REP_DONE`, fired from the transition this button raises.
 */
@Composable
fun RepsPage(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val reps = state.prescribedReps
    val maxEffort = reps == null
    val emomWindow = state.segment.anchored
    val name = state.exercise?.nameJa

    var wheelOpen by rememberSaveable { mutableStateOf(false) }
    var dialled by rememberSaveable { mutableIntStateOf(reps ?: 0) }

    // The wheel belongs to *this* set. A window boundary, a cap or a ◁◁ that moves the frontier while
    // it is open would otherwise leave it recording a count against a segment the user has left.
    LaunchedEffect(state.ordinal) {
        wheelOpen = false
        dialled = reps ?: 0
    }

    Box(modifier.fillMaxSize()) {
        PlayerFrame(
            state = state,
            actions = actions,
            // §A REPS state 3: 限界まで has no estimate to deplete, so the ring is a static full circle
            // in `c.enso` rather than an accent ring that would read as a timer with all the time in
            // the world left.
            ringColor = if (maxEffort) c.enso else c.accent,
            ringSweep = if (maxEffort) ENSO_SWEEP_DEGREES else state.ensoSweepAngle,
            counter = phaseCounterLabel(state),
            controls = liveControls(state, actions),
            counterAccent = startsFinalRound(state.timeline, state.ordinal),
            // One polite utterance on crossing the estimate, then silence (§A REPS, accessibility).
            // The 目安 line itself is never a live region: it changes every second.
            announcement = if (state.overrunning) OVERRUN_ANNOUNCEMENT else null,
            ringContent = {
                Column(
                    modifier = Modifier.clearAndSetSemantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = repHeroDescription(name, reps)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ExerciseHeading(name)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = repHero(reps),
                        softWrap = false,
                        maxLines = 1,
                        style = TextStyle(
                            fontFamily = Mincho,
                            // The reps are the hero here, not the clock — 76.sp against 運動's 88.
                            fontSize = heroSize(76.sp),
                            color = c.ink,
                        ),
                    )
                }
                pacerLabel(reps, state.remainingMs, state.overrunMs, emomWindow)?.let { pacer ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pacer,
                        style = TextStyle(
                            fontFamily = Gothic,
                            fontSize = 13.sp,
                            color = if (state.overrunning) c.accent else c.inkFaint,
                        ),
                    )
                }
            },
            below = {
                RoundIndicator(state)
                Spacer(Modifier.height(18.dp))
                DoneButton(
                    prescribedReps = reps,
                    onDone = { actions.onDone(ordinal = state.ordinal) },
                    onAdjust = { wheelOpen = true },
                )
            },
        )

        RepWheelSheet(
            visible = wheelOpen,
            value = dialled,
            onValue = { dialled = it },
            onDismiss = { wheelOpen = false },
            onRecord = {
                wheelOpen = false
                actions.onDone(actualReps = dialled, adjusted = true, ordinal = state.ordinal)
            },
        )
    }
}

/**
 * 済 — and the long-press that opens the wheel.
 *
 * **`onLongClickLabel` is not optional.** TalkBack surfaces a long-press only when it is labelled
 * (`00-plan.md` §4.1 rule 4, and §A REPS says so in bold), so the gesture is declared twice: once as
 * the label on the click semantics, and once as a `customAction`, which is how a switch-access or
 * keyboard user reaches it at all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoneButton(prescribedReps: Int?, onDone: () -> Unit, onAdjust: () -> Unit) {
    val c = LocalTempoColors.current
    val description = repDoneDescription(prescribedReps)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NEXT_UP_SLOT)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .combinedClickable(
                onClick = onDone,
                onLongClick = onAdjust,
                onLongClickLabel = "回数を変える",
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onLongClick(label = "回数を変える") { onAdjust(); true }
                customActions = listOf(CustomAccessibilityAction("回数を変える") { onAdjust(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "済",
            style = TextStyle(fontFamily = Mincho, fontSize = 20.sp, letterSpacing = 4.sp, color = c.accent),
        )
    }
}

/**
 * The rep-adjust sheet: one column, 0..99, opening on the prescribed count.
 *
 * **The clock keeps running while it is open** (§A REPS), which is the honest thing: the set is still
 * happening, and the duration recorded on 記録する is the measured one either way.
 *
 * Zero is a legal entry and is *not* special-cased here. `SessionMachine` records `actualReps == 0` as
 * `skipped` and the record renders it とばした; deriving that a second time on this screen is exactly
 * the kind of duplicated rule that ends up disagreeing.
 *
 * The wheel is keyed on its own value so the column can be re-seeded when the sheet reopens —
 * `TempoWheelColumn.initialIndex` seeds the list state once and never re-scrolls, by design.
 */
@Composable
private fun RepWheelSheet(
    visible: Boolean,
    value: Int,
    onValue: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRecord: () -> Unit,
) {
    val c = LocalTempoColors.current
    PlayerSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            text = "回数を変える",
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = c.inkSoft),
        )
        Spacer(Modifier.height(14.dp))
        // Arabic mid-spin, deliberately — `DECISIONS.md` §Q4: counts render kanji once they have
        // settled, but a kanji column changing under the finger is unreadable, so the wheel keeps the
        // digits and the recorded count comes back as 二十回 everywhere it is *read*.
        TempoValueWheel(values = 0..99, selected = value, onSelect = onValue)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "記録する",
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onRecord)
                .clearAndSetSemantics {
                    contentDescription = "記録する"
                    role = Role.Button
                }
                .padding(top = 16.dp),
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}
