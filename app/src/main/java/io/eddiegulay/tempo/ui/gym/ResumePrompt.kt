package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.ResumePromptState
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable

/**
 * つづき — the question a session left open asks, as a **modal and never a route**.
 *
 * `00-plan.md` §2 row 8 settled this against the player's own spec, and the reason is the whole
 * behaviour of the thing: **dismissing it decides nothing.** The session row is untouched, the つづき
 * banner on `GYM.HOME` stays exactly as it was, and the prompt re-asks itself on the next entry to the
 * gym. A route would have made the dismissal a navigation, and a navigation looks like an answer.
 *
 * It is drawn by whichever page is under it — `GYM.HOME` on a cold start, `GYM.LIBRARY.DETAIL` when it
 * fires as 始める's start guard (`03-player.md` §A) — which is why the presentation half takes a state
 * and four callbacks rather than a ViewModel. [ResumePromptHost] is the wiring both of those pages
 * share, so neither has to know that 記録する is `finishSession(complete = false)`.
 */
@Composable
fun ResumePromptHost(gym: GymViewModel) {
    // Claim the modal before reading it. See [ResumePromptMount]: several pages mount this host and
    // `GymShell`'s `AnimatedContent` keeps two of them in composition for the whole cross-fade, so
    // "am I the one drawing it?" is a question no page can answer about itself.
    val token = remember { Any() }
    DisposableEffect(token) {
        ResumePromptMount.mount(token)
        onDispose { ResumePromptMount.unmount(token) }
    }
    if (!ResumePromptMount.draws(token)) return

    val state by gym.resumePrompt.collectAsStateWithLifecycle()
    val prompt = state ?: return
    ResumePrompt(
        state = prompt,
        onResume = gym::resumeStaleSession,
        onRecord = gym::recordStaleSession,
        onRequestDiscard = gym::requestDiscardStaleSession,
        onConfirmDiscard = gym::confirmDiscardStaleSession,
        onCancelDiscard = gym::cancelDiscardStaleSession,
        onDismiss = gym::dismissResumePrompt,
    )
}

/**
 * Which of the two writes もう一度 must re-run. There are only two, and neither is guessable from
 * [ResumePromptState].
 */
enum class ResumeRetry { Record, Discard }

/**
 * The prompt's retry intent, kept **outside composition** because the fault it belongs to is.
 *
 * `ResumePromptState.fault` lives in the ViewModel and outlives every recomposition, every page swap
 * and every cross-fade; the `remember`ed lambda that used to answer もう一度 did not. Any path that
 * re-entered this composable with a fault already in state — `GymShell` swapping frames, HOME
 * recomposing after the host page changed, the mount claim moving from one page to another — left the
 * strip's 記録を読めません ・ もう一度 rendering over a button that did nothing.
 *
 * **Deriving the retry from state instead was considered and rejected as unsafe.** The two failures
 * are indistinguishable: `confirmDiscardStaleSession` clears `confirmingDiscard` on failure, so after
 * a failed 捨てる the state is `confirmingDiscard = false, fault != null` — exactly a failed 記録する.
 * `if (confirmingDiscard) discard else record` therefore answers a failed *delete* by **saving** the
 * session, which is a wrong write and not the no-op it looks like (`recordStaleSession`'s only guard
 * is `canRecord`, which is still true).
 *
 * Keyed on the session id so an intent cannot outlive the workout it was about. Process death clears
 * this and the ViewModel together, which is why [forSession] returning null is unreachable rather than
 * merely unlikely: the only two callers that can set a fault both record their intent first.
 */
internal object ResumePromptRetry {
    private var intent: Pair<Long, ResumeRetry>? = null

    fun record(sessionId: Long, action: ResumeRetry) {
        intent = sessionId to action
    }

    fun forSession(sessionId: Long): ResumeRetry? = intent?.takeIf { it.first == sessionId }?.second

    fun clear() {
        intent = null
    }
}

/**
 * Which mounted [ResumePromptHost] actually draws — the first one still in composition, and only it.
 *
 * `GYM.HOME`, `GYM.LIBRARY.DETAIL` and `GYM.LIBRARY.INDEX` all raise this modal and all mount the
 * host, and `GymShell` renders routes through an `AnimatedContent` whose ~280ms cross-fade keeps the
 * outgoing and incoming pages composed together. A pop from the detail page back to HOME therefore
 * stacked two identical `AlertDialog`s — two scrims, one announcement read twice.
 *
 * **The reviewed fix was to hoist a single host into `GymShell` and delete the page mounts, and it was
 * rejected here for a reason the review names elsewhere in the same document:** the library index is
 * currently missing its mount, and a page that raises the prompt without mounting a host strands
 * `startInFlight` forever. A rule that says "exactly one page may mount this" is a rule every new page
 * can break silently. Making the host itself idempotent fixes the duplication *and* makes the missing
 * third mount safe to add, with no coordination between pages and no shell edit.
 *
 * First-in wins rather than last-in, so the dialog does not jump between compositions mid-transition:
 * the outgoing page keeps drawing until it disposes, and the incoming host takes over in the same
 * frame that releases it. There is one frame at the very start where nothing draws — `DisposableEffect`
 * runs after composition — which is the same "no flash before the query resolves" the prompt's
 * `Loading` state already trades for.
 */
internal object ResumePromptMount {
    private var mounted by mutableStateOf<List<Any>>(emptyList())

    fun mount(token: Any) {
        if (mounted.none { it === token }) mounted = mounted + token
    }

    fun unmount(token: Any) {
        mounted = mounted.filterNot { it === token }
    }

    fun draws(token: Any): Boolean = mounted.firstOrNull() === token

    /** Test-only: the object is process-scoped, so a leaked token would leak into the next test. */
    fun reset() {
        mounted = emptyList()
    }
}

/**
 * The prompt itself: three doors, in the order least to most destructive.
 *
 * `AlertDialog` with `containerColor = c.bgSolid`, matching `BlockConfirmDialog` exactly as
 * `01-shell.md` §B asks — the app has one dialog idiom and this is not the place to invent a second.
 * The three options live in the dialog's *text* slot rather than in its button slots because they are
 * three peers reading top to bottom (`03-player.md` §A's mock), and a Material button row would
 * reorder them by convention and put the destructive one first on some locales.
 *
 * **Which options exist is decided by [ResumePromptState.options] and never here.** 続ける is *removed*
 * for a rebooted, stale or routine-less session and 記録する for one with nothing to save; a disabled
 * row that can never become enabled is chrome, not information.
 *
 * **The Writing state disables the rows and leaves the dialog standing** (§B's `Writing` state). A
 * failed write then lands in [ResumePromptState.fault] on a prompt that never closed, so nothing is
 * lost and もう一度 can retry the very action that failed — which is what [ResumePromptRetry] holds,
 * *outside* this composition, because the fault it belongs to is outside it too. Optimistically
 * closing here is §B edge case 8's worst outcome: a resumable workout vanishing because a write failed.
 */
@Composable
fun ResumePrompt(
    state: ResumePromptState,
    onResume: () -> Unit,
    onRecord: () -> Unit,
    onRequestDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onCancelDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    // Coarse by design (`03-player.md` §A edge case 4), so one reading at the moment the prompt appears
    // is the honest one — re-reading the clock per frame would make 一時間前 flip to 二時間前 under a
    // user who is deciding, which is a fact about our clock and not about their workout.
    val nowWallMs = remember(state.session.sessionId) { System.currentTimeMillis() }
    val copy = remember(state.session.sessionId, nowWallMs, s) {
        resumePromptCopy(state.session, nowWallMs, s)
    }

    AlertDialog(
        onDismissRequest = {
            when {
                // A write is in flight: the dialog stays put until it lands, in either direction.
                state.writing -> Unit
                // Back out of the second ask, not out of the question. 捨てる asked twice and this is
                // the answer "no" to the second one.
                state.confirmingDiscard -> onCancelDiscard()
                else -> onDismiss()
            }
        },
        containerColor = c.bgSolid,
        title = {
            Text(
                text = copy.title,
                style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = c.inkSoft),
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (copy.detail.isNotBlank()) {
                    Text(
                        text = copy.detail,
                        style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.5.sp, color = c.inkFaint),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = copy.staleness,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                )
                copy.note?.let { note ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note,
                        style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                    )
                }

                state.fault?.let { fault ->
                    Spacer(Modifier.height(14.dp))
                    FaultStrip(
                        fault = fault,
                        onRecover = {
                            when (ResumePromptRetry.forSession(state.session.sessionId)) {
                                ResumeRetry.Record -> onRecord()
                                // The second ask has to be re-opened before it can be re-answered:
                                // `confirmDiscardStaleSession` clears `confirmingDiscard` when it
                                // fails and then refuses any call that arrives without it. Both are
                                // ViewModel calls reading the current state, so the pair runs as one
                                // retry of the delete the user already confirmed.
                                ResumeRetry.Discard -> {
                                    onRequestDiscard()
                                    onConfirmDiscard()
                                }
                                // Unreachable: a fault on this prompt can only have come from one of
                                // the two writes above, and both record their intent before running.
                                null -> Unit
                            }
                        },
                    )
                }

                if (state.confirmingDiscard) {
                    Spacer(Modifier.height(18.dp))
                    // The second ask. `04-library-records.md` §6's own sentence for a deletion that
                    // cannot be taken back — see this unit's report: the resume prompt's second confirm
                    // has no copy of its own anywhere in the specs, and this is the nearest documented
                    // line rather than a new one.
                    Text(
                        text = s.gymHome.promptDiscardIrreversible,
                        style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, lineHeight = 22.sp, color = c.inkFaint),
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
                    if (state.options.canResume) {
                        PromptOption(
                            label = s.gymHome.resumeAction,
                            color = c.accent,
                            enabled = !state.writing,
                            onClick = onResume,
                        )
                    }
                    if (state.options.canRecord) {
                        PromptOption(
                            label = s.gymHome.promptRecord,
                            color = c.inkSoft,
                            enabled = !state.writing,
                            onClick = {
                                ResumePromptRetry.record(state.session.sessionId, ResumeRetry.Record)
                                onRecord()
                            },
                        )
                    }
                    if (state.options.canDiscard) {
                        PromptOption(
                            label = s.gymHome.promptDiscard,
                            color = c.inkFaint,
                            enabled = !state.writing,
                            onClick = onRequestDiscard,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.confirmingDiscard) {
                TextButton(
                    onClick = {
                        ResumePromptRetry.record(state.session.sessionId, ResumeRetry.Discard)
                        onConfirmDiscard()
                    },
                    enabled = !state.writing,
                ) {
                    Text(
                        text = s.gymHome.promptDiscard,
                        style = TextStyle(
                            fontFamily = Mincho,
                            color = if (state.writing) c.inkFaint else c.accent,
                        ),
                    )
                }
            }
        },
        dismissButton = {
            when {
                state.confirmingDiscard -> TextButton(onClick = onCancelDiscard, enabled = !state.writing) {
                    Text(s.gymHome.cancel, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
                }
                // A session with nothing worth saving has lost both 続ける and 記録する, so 捨てる is the
                // only row left — and a modal whose one visible option deletes a workout needs its way
                // out written down rather than left to a tap outside. `03-player.md` §A names it:
                // 捨てる plus a そのまま dismiss. **Auto-discarding is tempting and wrong** — the user
                // should learn that this app never deletes silently.
                !state.options.canRecord -> TextButton(onClick = onDismiss, enabled = !state.writing) {
                    Text(s.gymHome.promptLeave, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
                }
                else -> Unit
            }
        },
    )
}

/**
 * One door of the prompt: a word on the left, an arrow on the right, the whole 56.dp row tappable.
 *
 * The arrow is the same 「→」 the settings and safety rows use — it says "this leads somewhere" and is
 * the reason the row needs no button chrome to read as one. Disabled rows keep their word and lose
 * their colour, and carry `disabled()` so TalkBack says so rather than announcing an option that will
 * not respond.
 */
@Composable
private fun PromptOption(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val shown = if (enabled) color else c.inkFaint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            // The same shape the quit sheet's rows take, because the note above says these two are
            // meant to read as one object.
            .pressable(TempoShapes.Row, enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
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
