package io.eddiegulay.tempo.ui.gym.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.eddiegulay.tempo.gym.SessionOutcome
import io.eddiegulay.tempo.gym.session.CompletionReason
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.gym.RecordMode
import io.eddiegulay.tempo.ui.gym.RecordSummary
import io.eddiegulay.tempo.ui.gym.RecordSummaryData
import io.eddiegulay.tempo.ui.theme.LocalTempoColors

/*
 * 記録 — `GYM.SESSION.COMPLETE`, the player's terminal screen.
 *
 * Almost nothing is drawn here. Every pixel between the ensō and the 内訳 belongs to
 * `ui/gym/RecordSummary.kt`, which `00-plan.md` §2 row 9 makes the single component behind this page
 * *and* `GYM.RECORDS.SESSION_DETAIL`; what is left is the three things that are genuinely this page's
 * and are wrong on the other one — the word 閉じる, the fact that it pops the **whole** player stack,
 * and the mapping from a `SessionOutcome` onto [RecordSummaryData].
 *
 * **Three things this page deliberately does not do.**
 *
 * 1. **No `BackHandler`.** `SessionHost` owns exactly one for the entire player and routes 記録 → close
 *    (§C.1 row 33). A second one here would win by nesting and would pop one entry instead of the stack.
 * 2. **No 予定に入れる.** It is Phase 3 (`00-plan.md` §6). A disabled button for an action that does not
 *    exist teaches the user the app is broken, so no slot is reserved and no ghost is drawn.
 * 3. **No screen-on release, no `finishSession`, no query.** All three already happened: the session was
 *    closed by whatever transitioned here, `SessionOutcome` was filled *inside* that transaction (§E.5),
 *    and `SessionController` dropped the wake flag on the finish (§A COMPLETE edge case 8).
 *
 * `SessionScreen.Complete` is published **only after `finishSession` returned Ok**, which is why §A's
 * `SummaryFailed` state (記録を読み込めませんでした ・ 記録は保存されています) has no branch here: a finish
 * that did not land keeps the user on the live screen with the quit sheet's own fault, per §A
 * QUIT_SHEET's "never navigate away on a failed save". It is reported rather than implemented, because
 * implementing it would mean drawing a state the host cannot reach.
 */

/**
 * The record screen: a header holding 閉じる, and the shared summary under it.
 *
 * @param state the host's [SessionCompleteState] — the outcome, why the session ended, the rating as
 *   last tapped, and a rating write that failed. The rating lives on the host rather than here because
 *   the write is optimistic and must survive this composable leaving the composition.
 */
@Composable
fun CompletePage(
    state: SessionCompleteState,
    actions: SessionActions,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val data = remember(state.outcome, state.reason) { recordData(state.outcome, state.reason) }

    // No `statusBarsPadding`: `GymRoute.Session` is immersive and the shell hands immersive routes the
    // whole window with their own insets (`GymShell`, and `00-plan.md` §3.1's route model). The bars
    // are hidden, and 12.dp from the top is the same offset the player's own ✕ takes.
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 閉じる, not とじる. `04` §6's generic close word belongs to the historical page; `03` §A
            // writes this one in kanji and it is the only way back into the shell from here.
            HeaderAction(
                label = "閉じる",
                description = "閉じる",
                color = c.inkSoft,
                onClick = actions::onCloseRecord,
            )
        }
        RecordSummary(
            mode = RecordMode.Live,
            data = data,
            rating = state.rating,
            ratingFault = state.ratingFault,
            onRate = actions::onRate,
            onRepeat = actions::onRepeat,
        )
    }
}

/**
 * `SessionOutcome` → [RecordSummaryData], which is the whole of §4's "data source" difference (row 1).
 *
 * `isStillBest = true` unconditionally: a record set thirty seconds ago is still standing, and the
 * 当時の自己最高 demotion is a fact only a later session can create. `personalBest` itself is **not**
 * re-derived from `newBests` — the summary is read inside the finish transaction after the PR rows are
 * written, so it already knows, and a second opinion here could disagree with the history list.
 *
 * `routineArchived = false`: you were inside this routine a moment ago, and the archive path runs
 * through `GYM.LIBRARY.DETAIL`, which cannot be reached while the player owns the window.
 */
private fun recordData(outcome: SessionOutcome, reason: CompletionReason): RecordSummaryData =
    RecordSummaryData(
        summary = outcome.summary,
        results = outcome.results,
        previous = outcome.previous,
        isStillBest = true,
        streakDays = outcome.streakDays,
        // §A COMPLETE's `FailedOut`: an EMOM_ASCENDING that ran out of ladder. It keeps the ceremony
        // and the streak — reaching failure is the protocol succeeding — and changes only its chip and
        // its hero label. Every other reason renders as an ordinary complete or partial session.
        failedOut = reason == CompletionReason.EMOM_FAIL_OUT,
        routineArchived = false,
    )
