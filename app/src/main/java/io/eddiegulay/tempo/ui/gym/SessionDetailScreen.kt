package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.GymWrite
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.SessionDetail
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import kotlinx.coroutines.launch

/*
 * GYM.RECORDS.SESSION_DETAIL — 記録の中身. `04-library-records.md` §4's third page.
 *
 * **This page mounts `ui/gym/RecordSummary.kt` in [RecordMode.Historical]. It does not redraw the
 * record.** `00-plan.md` §2 row 9 made that component one component with two modes precisely so this
 * page could exist without duplicating design §4's layout, and §4's eleven-row difference table is the
 * specification of the lie each page would otherwise tell. Every row of it is either already enforced
 * inside `RecordSummary` — the ensō drawn already closed, the どうでしたか heading dropped once
 * answered, today's streak *discarded* rather than merely not passed, 自己最高 demoted to
 * 当時の自己最高 in `c.inkFaint` — or is one of the five that are genuinely this page's:
 *
 * | # | row | where |
 * |---|---|---|
 * | 1 | data source | **here** — [SessionDetail] off `GymRepository.sessionDetail`, narrowed by [historicalData]. |
 * | 5 | comparison baseline | the **store**'s: `previous` arrives scoped to the same routine *and* tier. |
 * | 8 | 予定に入れる | **absent**, replaced by もう一度. No dead button is drawn. |
 * | 9 | header actions | **here** — とじる at the head, 記録を削除 at the foot. |
 * | 10 | tab bar | the shell's: this route is not `immersive`, so the bar stays. |
 * | 11 | back | the shell's: a straight pop of one entry. |
 *
 * There is no discard prompt on back, because there is nothing undecided: an edited rating persists on
 * the tap that made it.
 */

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Pure logic — Android-free, JUnit-tested in `gym/ui/SessionDetailScreenTest.kt`
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * `SessionDetail` → [RecordSummaryData], which is the whole of §4's "data source" row.
 *
 * Four fields and not one of them is computed here, which is the point:
 *
 * - **`isStillBest` is the store's**, recomputed on read (§4 edge case 2), so beating an old PR
 *   retroactively demotes this record's chip with no migration and nothing to rebuild. The live page
 *   passes `true` unconditionally; this one must not, and that difference is row 6 of the table.
 * - **`streakDays = null`.** Row 7 is enforced twice on purpose — `recordAccolades` discards the value
 *   in `Historical` whatever is passed, and nothing is passed. Showing today's streak on a March record
 *   is a category error and computing March's would mean walking the whole history for one line.
 * - **`failedOut = false`.** `CompletionReason` is the *player's* in-memory answer to why a session
 *   ended and nothing stores it, so a reopened デス・バイ cannot know it ran to failure. It therefore
 *   reads as an ordinary complete session — which it is, since a fail-out is stored `complete = 1` —
 *   and loses only the 十七分で 力尽きた chip and the 到達 hero label. **Reported**, because the fix is a
 *   column, not a page: inventing a reason from the row's shape would be this page guessing at the one
 *   fact it exists to report.
 * - **`routineArchived` is the store's**, and it drives `RecordSummary`'s greyed もう一度 with
 *   この型は削除されています beneath it (`04` §6 :1182).
 */
fun historicalData(detail: SessionDetail): RecordSummaryData = RecordSummaryData(
    summary = detail.summary,
    results = detail.results,
    previous = detail.previous,
    isStillBest = detail.isStillBest,
    streakDays = null,
    failedOut = false,
    routineArchived = detail.routineArchived,
)

/**
 * The breakdown's names, **from the pinned `routine_version`**.
 *
 * §4 edge case 1 is the entire justification for the version pin: *the breakdown renders from
 * `session_result` joined to the pinned `routine_version`, never to the live routine* — a station
 * removed last week still appears in a session performed before it was removed. The rows themselves are
 * already pinned (they are the session's own results), so what the snapshot adds is the *naming*: a
 * station whose exercise has since been retired from the catalogue is still named by the version that
 * prescribed it.
 *
 * Null snapshot returns an empty map, and the caller then falls back to the live catalogue — which is
 * §4 edge case 4's own instruction for a record that predates the pin.
 *
 * **The pinned names are not translated, and that is correct** (§L10). A snapshot is what the session
 * actually prescribed, in the words it prescribed it in; resolving it against today's catalogue would
 * be the live routine wearing the pin's clothes, which is the confusion the pin exists to prevent. The
 * live catalogue is the *fallback*, and only there does the reader's language apply.
 */
fun pinnedExerciseNames(snapshot: RoutineSnapshot?): Map<String, String> =
    snapshot?.stations.orEmpty()
        .mapNotNull { station -> station.exercise?.nameJa?.let { station.exerciseId to it } }
        .toMap()

/**
 * 当時の内容は残っていません, or null.
 *
 * §6 marks this string **"backup-restore case only"** and §4 edge case 4 says what that case is: a
 * session stored before the version pin existed, which no shipped build can create but a restored
 * backup can outlive. It is *not* the ordinary case of a routine that was edited afterwards — the pin
 * is exactly what survives that, and `RoutineBest.structureChanged` / 中身が変わっています is the
 * separate, gentler thing said about it.
 *
 * So the condition is a **missing snapshot**, and nothing else. Rendering this line for any routine
 * whose shape has moved would tell a user their record's contents are gone when they are pinned and
 * intact, on the one page whose purpose is being honest about a record.
 */
fun missingSnapshotCopy(snapshot: RoutineSnapshot?, strings: Strings): String? =
    if (snapshot == null) strings.gymRecords.missingSnapshot else null

/**
 * `令和八年 ・ 六月十七日` — when this record was made.
 *
 * The header's subtitle slot, in the idiom every other gym page header already uses (`型`'s and
 * `GYM.HOME`'s both read `${era} ・ ${monthDay}`), with the **session's own** local date rather than
 * today's. §4's RECORDS.INDEX mock prints exactly this shape (`令和八年 ・ 六月`) under a records title,
 * so the two formatters and the separator are all documented; what is composed here is which date goes
 * into them, and on a historical record that can only be the day it was performed.
 *
 * The date is [io.eddiegulay.tempo.gym.SessionSummary.localDate] — the day the session **started**,
 * bucketed in Kotlin in the device's own zone at write time. §4 edge case 6 states the rule once for
 * the whole feature: a session that crosses midnight belongs to the day it started, in the grid, in the
 * streak, in the history grouping, and therefore here.
 */
fun recordDateLine(detail: SessionDetail, strings: Strings): String {
    val at = detail.summary.localDate.atStartOfDay()
    return strings.fmt.era(at) + strings.fmt.separator + strings.fmt.monthDay(at)
}

/**
 * The two actions at the foot, under もう一度.
 *
 * 型を見る is §6's own `session detail actions` row. 記録を削除 is §6's `history menu` row, reused here
 * for the 削除 that §4's difference table (row 9) puts "at the foot" without naming: the act and the
 * object are identical — deleting this record — and inventing a second word for one button would put an
 * unsourced string on the page. `00-plan.md` §4's page table names the same two actions for this page
 * (もう一度 · 型を見る · 記録を削除).
 */
enum class RecordFooterAction {
    OpenRoutine,
    Delete,
}

/** 型を見る / 記録を削除 — from the table rather than a constructor argument (§L3). */
fun RecordFooterAction.label(strings: Strings): String = when (this) {
    RecordFooterAction.OpenRoutine -> strings.gymRecords.openRoutine
    RecordFooterAction.Delete -> strings.gymRecords.deleteRecord
}

/**
 * Whether the page should get out of the way rather than offer a retry.
 *
 * §4 edge case 9: `GymFault.SessionGone` **pops immediately** — the record was deleted from another
 * shell state while this page held its key, and もう一度 on a row that is gone can only fail again.
 * Every other fault keeps the page and its `FaultPanel`, because 記録を読めません is a store problem and
 * re-asking is exactly the right remedy (`DECISIONS.md` §Q6).
 *
 * **The fault is now reachable** (`DECISIONS.md` §Q23): `GymStore.sessionDetail` throws `SessionMissing`
 * and `Throwable.toGymFault` maps it to `SessionGone`. It used to fall through to `GymFault.Unknown`,
 * which made this whole branch dead code and a deleted session read 記録を読めません under a もう一度
 * that could never succeed. The branch was written to the spec while the mapping was missing precisely
 * so that closing the gap took one arm and no page change; recovering the case by matching on
 * `Unknown`'s *message* would have been worse than the bug.
 *
 * Matched by *value* and not by class, so a future fault carrying a payload cannot silently join it.
 */
fun popsOnFault(fault: TempoFault?): Boolean = fault == GymFault.SessionGone

/**
 * The optimistic rating, boxed so that "un-rated" and "not editing" are different values.
 *
 * Top-level and `internal` rather than private, because the box is only worth having if the read that
 * unwraps it can be tested — see [shownRating], which is the read that was getting it wrong.
 */
internal data class RatingEdit(val rating: Rating?)

/**
 * Which rating the chips show: the edit if there is one, the store's answer otherwise.
 *
 * **The elvis that used to be here (`edit?.rating ?: stored`) collapsed the one case the box exists
 * for.** Re-tapping the selected chip un-rates, and un-rating is a real answer (§4 row 3, edge case 7);
 * `RatingEdit(null)` is that answer in flight. The elvis mapped it straight back to the stored rating,
 * so the chip the user had just cleared stayed lit until the store round-tripped — write, notify, 80ms
 * debounce, re-emit — and if the write failed, the revert reverted to what was already on screen and
 * the user was told nothing at all. An optimistic UI that is not optimistic in one direction is worse
 * than one that is optimistic in neither, because only one of the two answers lies.
 */
internal fun shownRating(edit: RatingEdit?, stored: Rating?): Rating? =
    if (edit != null) edit.rating else stored

/**
 * The edit is over once the store agrees with it.
 *
 * Dropping the box on `GymWrite.Ok` instead — what this page did — is wrong for exactly the same case:
 * the write lands before the read re-emits, so for one frame [shownRating] would fall back to a stored
 * rating that is still the old one, and an un-rate would visibly flash its old chip back on before
 * clearing. The box is held until the two agree and then dropped, so the page stops overlaying a value
 * the store is now the source of.
 */
internal fun settledEdit(edit: RatingEdit?, stored: Rating?): RatingEdit? =
    if (edit != null && edit.rating == stored) null else edit

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * 記録の中身 — one finished session, reopened, honest about how it looks *now*.
 *
 * @param sessionKey [io.eddiegulay.tempo.gym.SessionSummary.key], which is the row id as a string —
 *   `GymRoute.Record` carries the key rather than the id because that is what a list row already holds.
 *   A key that is not a number names no session and is treated as [GymFault.SessionGone]: it is the
 *   same fact, and it is unreachable in practice because every caller builds the route from a summary.
 *
 * **The rating is editable here, and re-tapping un-rates.** That is §4's row 3 and it is the one write
 * this page makes. It is optimistic and **reverted on failure**, with a `FaultStrip` above the rating
 * row — `RecordSummary` already places the strip there for `Historical` — because an optimistic UI that
 * silently keeps a value the store rejected is a lie in the record. Un-rating is a real answer and not
 * a cancel: §4 edge case 7 is explicit that it removes the session's contribution to load and therefore
 * to monotony, which is correct, because an unrated session has no CR10 and must not be assigned one.
 */
@Composable
fun SessionDetailScreen(
    gym: GymViewModel,
    sessionKey: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sessionId = remember(sessionKey) { sessionKey.toLongOrNull() }

    val outcome: Loadable<SessionDetail> = if (sessionId == null) {
        Loadable.Failed(GymFault.SessionGone)
    } else {
        val flow = remember(sessionId) { gym.repository.sessionDetail(sessionId) }
        val state by flow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
        state
    }

    // The optimistic rating, as a *box* rather than a bare `Rating?`: null is a real answer here
    // (re-tapping un-rates), so "no edit in flight" and "edited to unrated" cannot share a value.
    var edit by remember(sessionId) { mutableStateOf<RatingEdit?>(null) }
    var ratingFault by remember(sessionId) { mutableStateOf<GymFault?>(null) }
    var confirmingDelete by remember(sessionId) { mutableStateOf(false) }
    var deleteFault by remember(sessionId) { mutableStateOf<GymFault?>(null) }

    // The box is dropped when the store agrees with it, never on the write returning — see
    // [settledEdit]. Idempotent, so this settles in one pass and cannot loop. Only a `Ready` outcome
    // settles anything: a fault carries no rating and must not be read as "the store says unrated".
    val ready = (outcome as? Loadable.Ready)?.value
    LaunchedEffect(ready?.summary?.rating, ready != null, edit) {
        if (ready != null) edit = settledEdit(edit, ready.summary.rating)
    }

    /*
     * The delete, in one place because it has **two** callers — the confirmation's 削除 and the failure
     * strip's もう一度 — and a second copy is how the two would come to disagree.
     *
     * `discardSession` is the delete. Its name is the quit sheet's word, and its body is exactly what §4
     * edge case 8 asks for: the row goes, its results and PR rows cascade, and `rebuildPersonalRecords`
     * re-derives the bests so *"a best whose session was deleted simply becomes a different session's"*
     * — its own KDoc cites that edge case. There is no orphan case and nothing here caches a best.
     *
     * **Never optimistic, and the pop is the write's.** The page leaves only once the store has
     * confirmed the record is gone; nothing is removed from the screen ahead of that, because a record
     * vanishing on a write that then failed is the worst outcome this page has. [popsOnFault] is the
     * *other* case and not a second exit racing this one: it covers a record deleted from another shell
     * state under a page that did not delete it, which arrives as a read failure rather than as a write
     * result. `refreshLifetimeSummary()` because these totals feed 記録's これまで tile and
     * これまで's subtitle, and `summary()` is a one-shot read rather than a flow.
     */
    val runDelete: suspend (Long) -> Unit = { id ->
        when (val write = gym.repository.discardSession(id)) {
            is GymWrite.Ok -> {
                gym.refreshLifetimeSummary()
                if (gym.stack.value.size > 1) gym.onBack()
            }

            is GymWrite.Failed -> deleteFault = write.fault
        }
    }

    val fault = (outcome as? Loadable.Failed)?.fault
    // §4 edge case 9. Guarded on the stack depth exactly as `LibraryDetailScreen` guards its own
    // RoutineGone pop: the shell's root must never be popped out from under the tab bar.
    LaunchedEffect(fault) {
        if (popsOnFault(fault) && gym.stack.value.size > 1) gym.onBack()
    }

    Column(modifier.fillMaxSize()) {
        RecordHeader(
            subtitle = (outcome as? Loadable.Ready)?.value?.let { recordDateLine(it, s) },
            note = (outcome as? Loadable.Ready)?.value?.let { missingSnapshotCopy(it.snapshot, s) },
            onClose = { if (gym.stack.value.size > 1) gym.onBack() },
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (outcome) {
                // Its own composable, sharing nothing with an empty state — there is no empty state on
                // this page at all, because a record either exists or the read failed, and those are
                // the two things `00-plan.md` §4.1 rule 1 refuses to let share a shape.
                Loadable.Loading -> RecordLoading()

                is Loadable.Failed -> FaultPanel(fault = outcome.fault, onRecover = gym::retry)

                is Loadable.Ready -> {
                    val detail = outcome.value
                    val data = remember(detail) { historicalData(detail) }
                    val pinned = remember(detail.snapshot) { pinnedExerciseNames(detail.snapshot) }

                    RecordSummary(
                        mode = RecordMode.Historical,
                        data = data,
                        // Through the box, never around it: `RatingEdit(null)` is the un-rate the user
                        // just tapped and it must render as unrated. See [shownRating].
                        rating = shownRating(edit, detail.summary.rating),
                        ratingFault = ratingFault,
                        onRate = { picked ->
                            if (sessionId == null) return@RecordSummary
                            edit = RatingEdit(picked)
                            ratingFault = null
                            scope.launch {
                                when (val write = gym.repository.rateSession(sessionId, picked)) {
                                    // Held, not dropped: the store has not re-emitted yet, and dropping
                                    // it here would flash the old chip back on for a frame ([settledEdit]).
                                    is GymWrite.Ok -> Unit
                                    is GymWrite.Failed -> {
                                        // Reverted, and *stated*. §4's data-out note wants both.
                                        edit = null
                                        ratingFault = write.fault
                                    }
                                }
                            }
                        },
                        // §4's exits: もう一度 reaches `GYM.SESSION.PREPARE` **via** `LIBRARY.DETAIL`,
                        // so it lands on the routine's page rather than starting a workout. A
                        // read-only record must not begin one from a tap that said "again"; the start
                        // guard, the tier chips and 始める all live one page along, where they belong.
                        onRepeat = { gym.go(GymRoute.RoutineDetail(detail.summary.routineId)) },
                        // The pinned version first, the live catalogue only as the fallback §4 edge
                        // case 4 prescribes for a record that predates the pin.
                        // The pinned version first — frozen, and never retranslated (§L10) — and the
                        // live catalogue, in the reader's language, only as the fallback §4 edge case 4
                        // prescribes for a record that predates the pin.
                        exerciseName = { id ->
                            id?.let { pinned[it] ?: ExerciseCatalog.byId(it)?.displayName(s) }
                        },
                        footer = {
                            RecordFooter(
                                onAction = { action ->
                                    when (action) {
                                        RecordFooterAction.OpenRoutine ->
                                            gym.go(GymRoute.RoutineDetail(detail.summary.routineId))

                                        RecordFooterAction.Delete -> confirmingDelete = true
                                    }
                                },
                            )
                            deleteFault?.let { failed ->
                                Spacer(Modifier.height(12.dp))
                                FaultStrip(
                                    fault = failed,
                                    // もう一度 on a rejected *write* re-attempts the write. `gym.retry()`
                                    // re-ran every read instead, which had nothing to do with what
                                    // failed: the strip dismissed itself and the record stayed. §Q6
                                    // withholds the action word for the faults that must not be
                                    // re-attempted (`StoreFull`, `Rejected`), so `FaultStrip` draws no
                                    // もう一度 for those and this is never a silent second write.
                                    onRecover = {
                                        deleteFault = null
                                        if (sessionId != null) scope.launch { runDelete(sessionId) }
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmingDelete && sessionId != null) {
        val copy = sessionDeleteCopy(s)
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = c.bgSolid,
            title = {
                Text(copy.title, style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink))
            },
            text = {
                Text(
                    text = copy.body,
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.5.sp,
                        color = c.inkFaint,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        scope.launch { runDelete(sessionId) }
                    },
                ) {
                    Text(copy.confirm, style = TextStyle(fontFamily = Mincho, color = c.accent))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(copy.dismiss, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
                }
            },
        )
    }
}

/**
 * 記録の中身, the day it was made, and とじる.
 *
 * **とじる, not 閉じる.** `03` §A writes the player's terminal word in kanji and §6 lists とじる as the
 * generic close; they are different words on different pages and `CompletePage`'s own KDoc says so.
 * Row 9 of the difference table is exactly this.
 *
 * The header is drawn in every state, including `Loading` and `Failed`, so とじる is reachable from a
 * record that cannot be read. A page whose only way out is the system back gesture is a page a user
 * with a fault on screen can be trapped on.
 */
@Composable
private fun RecordHeader(subtitle: String?, note: String?, onClose: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f, fill = true)) {
                Text(
                    text = s.gymRecords.detailTitle,
                    modifier = Modifier.semantics { heading() },
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            fontFamily = Mincho,
                            fontSize = 13.sp,
                            letterSpacing = 4.sp,
                            color = c.inkFaint,
                        ),
                    )
                }
                // 当時の内容は残っていません sits with the page's own statements about the record rather
                // than beside the 内訳 it qualifies, because `RecordSummary` offers no slot there and
                // this unit does not fork it. **Reported** as the one placement §4 leaves to the page.
                if (note != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = note,
                        style = TextStyle(
                            fontFamily = Gothic,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = c.inkFaint,
                        ),
                    )
                }
            }
            HeaderAction(
                label = s.gymRecords.close,
                description = s.gymRecords.close,
                color = c.inkFaint,
                onClick = onClose,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}

/**
 * 型を見る and 記録を削除, under `RecordSummary`'s もう一度.
 *
 * 削除 draws `c.inkFaint` rather than `c.accent`: the accent is rationed to one word per surface and
 * もう一度 above it has already spent it, which is the same rationing `GYM.LIBRARY.DETAIL` applies to
 * its own foot actions.
 */
@Composable
private fun RecordFooter(onAction: (RecordFooterAction) -> Unit) {
    RecordFooterAction.entries.forEach { action ->
        FooterAction(action = action, onClick = { onAction(action) })
    }
}

/** §2's centred action row: one word, 48.dp of target, no box around it. */
@Composable
private fun FooterAction(action: RecordFooterAction, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val label = LocalStrings.current.let { action.label(it) }
    Box(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = c.inkFaint,
            ),
        )
    }
}

/**
 * 読み込み中 — a record that has not arrived yet.
 *
 * Its own composable and its own state. There is deliberately **no skeleton record**: an ensō drawn at
 * zero sweep over dashes is indistinguishable from a session with nothing in it, which is the
 * `Loadable` doctrine's forbidden confusion in the one place it would be most convincing.
 */
@Composable
private fun RecordLoading() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = LocalStrings.current.gymRecords.loading,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}
