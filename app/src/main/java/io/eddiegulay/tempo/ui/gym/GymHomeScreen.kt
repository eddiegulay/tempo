package io.eddiegulay.tempo.ui.gym

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.ActiveSession
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymTab
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.ResumableSession
import io.eddiegulay.tempo.gym.ResumeAffordance
import io.eddiegulay.tempo.gym.RoutineCard
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.gym.resumeOptions
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.FaultPanel
import io.eddiegulay.tempo.ui.FaultStrip
import io.eddiegulay.tempo.ui.HeaderAction
import io.eddiegulay.tempo.ui.rememberMinuteTime
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.combinedPressable
import io.eddiegulay.tempo.ui.theme.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 鍛錬 — the gym's front door: resume an interrupted workout, or choose what to train today.
 *
 * The page is one `LazyColumn` of sections under a header that never moves, and the header staying
 * outside the body is the load-bearing part of the layout: 設定 has to remain reachable when the feed
 * cannot be read, so the fault panel is drawn *in the body slot* and never over the whole page
 * (`01-shell.md` §B's `Error` state).
 *
 * **Loading, Empty and Error are three different pictures and share no composable here.** A feed that
 * is still arriving says 読み込み中; one that failed says 記録を読めません through the app's own
 * `FaultPanel`; and 型はまだありません — the only emptiness this page ever admits to — is **scoped to
 * 自分の型**, drawn inside that section, 56.dp tall. The page as a whole is never empty, because the
 * built-in 型 are seeded. That separation is `00-plan.md` §4.1 rule 1 and it is the single most
 * important property of this file.
 *
 * **The open-session row gets the same treatment as the feed.** It is a `Loadable` too, and the page
 * used to unwrap it — so a row it could not read drew exactly the pixels of a store with no
 * interrupted workout in it. `homeResume` collapses `Loading` and `Failed` into `None` for the
 * *affordance*, which is honest (there is no banner to draw), but the page must not answer a failed
 * read with silence: [resumeFault] puts 記録を読めません in the banner's place, with もう一度 wired to
 * `refreshResumable()` rather than to the feed's `retry()`.
 *
 * The つづき banner and the resume prompt are two different objects and only one of them is a decision:
 * the banner is a way back into a workout, the prompt is a question about one. Dismissing the prompt
 * leaves the banner standing, which is exactly why the prompt is a modal (`00-plan.md` §2 row 8).
 */
@Composable
fun GymHomeScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val now by rememberMinuteTime()
    val zone = remember { ZoneId.systemDefault() }
    val today = now.toLocalDate()

    val feed by gym.homeFeed.collectAsStateWithLifecycle()
    val affordance by gym.homeResume.collectAsStateWithLifecycle()
    val resumable by gym.resumable.collectAsStateWithLifecycle()
    val live by gym.activeSession.collectAsStateWithLifecycle()
    val writeFault by gym.writeFault.collectAsStateWithLifecycle()
    val prefs by gym.prefs.collectAsStateWithLifecycle()

    // "GYM.HOME has appeared" — re-read the open-session row and raise the prompt if one is owed. The
    // page leaves composition whenever a route is pushed over it, so this fires on every arrival; the
    // ViewModel is what makes it ask **once per session id** rather than once per arrival.
    LaunchedEffect(Unit) { gym.onHomeShown() }

    // The long-press menu is keyed on a routine id, not on a card: if the feed re-emits without that
    // routine — deleted from another surface, archived by the write that is in flight — the menu simply
    // finds nothing and closes, rather than acting on a stale target (§B edge case 10).
    var menuFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<String?>(null) }

    val ready = (feed as? Loadable.Ready)?.value
    val cards = remember(ready) {
        ready?.let { it.recent + it.builtIn + it.userRoutines }.orEmpty().associateBy { it.routineId }
    }
    val menuCard = menuFor?.let(cards::get)
    val deleteCard = deleteFor?.let(cards::get)
    val sections = remember(ready, s) { ready?.let { homeSections(it, s) }.orEmpty() }

    // The routine the open session belongs to, when the store could say. 削除's two sentences are not
    // interchangeable and `timesDone` counts only *finished* sessions, so this is what keeps the
    // irreversible branch off the one routine whose purge would be refused — see [deleteRoutineCount].
    val openRoutineId = resumable.valueOrNull()?.routineId

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = s.gymHome.title,
                    modifier = Modifier.semantics { heading() },
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = s.fmt.era(now) + s.fmt.separator + s.fmt.monthDay(now),
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Top) {
                // 記録 is not here: it is a tab now, and the accent stays rationed to one word per
                // header exactly as 予定's 加える does (`00-plan.md` §2 row 16).
                // 設定 is `GYM.SETTINGS`' own page title, read from that namespace rather than
                // re-typed here: the word on the button and the heading it opens are one string.
                HeaderAction(
                    label = s.gymSettings.title,
                    description = s.gymSettings.title,
                    color = c.inkSoft,
                    onClick = { gym.go(GymRoute.Settings) },
                )
                HeaderAction(
                    label = s.gymHome.actionCreate,
                    description = s.gymHome.createDescription,
                    color = if (ready != null) c.accent else c.inkFaint,
                    enabled = ready != null,
                    onClick = { gym.go(GymRoute.Builder(null)) },
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                // Never an empty state for a store we could not read — 記録を読めません, and the header
                // above stays live so 設定 is still reachable.
                feed is Loadable.Failed -> FaultPanel(
                    fault = (feed as Loadable.Failed).fault,
                    onRecover = gym::retry,
                )

                ready == null -> HomeMessage(s.gymHome.loading)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp),
                    // 88.dp, not the launcher's 96.dp: the gym's tab bar is seated rather than floating.
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    // A failed *write* — a duplicate, an archive — states itself in place and leaves the
                    // page alone. It is never a reason to blank the feed behind it.
                    writeFault?.let { fault ->
                        item(key = "write-fault") {
                            WriteFaultStrip(
                                fault = fault,
                                onRetry = { gym.dismissWriteFault(); gym.retry() },
                                onDismiss = gym::dismissWriteFault,
                            )
                        }
                    }

                    // A failed *read* of the open-session row. It cannot share the banner's slot with a
                    // silence: `homeResume` collapses Loading and Failed alike to `None`, so without
                    // this the page would draw a clean 鍛錬 over an interrupted workout it could not
                    // see (`00-plan.md` §4.1 rule 1). もう一度 re-runs **this** read — `retry()` reloads
                    // the feed and would leave the row exactly as unread as it was.
                    resumeFault(resumable)?.let { fault ->
                        item(key = "resume-fault") {
                            Box(Modifier.padding(vertical = 5.dp)) {
                                FaultStrip(fault = fault, onRecover = gym::refreshResumable)
                            }
                        }
                    }

                    resumeBanner(
                        affordance = affordance,
                        live = live,
                        row = resumable.valueOrNull(),
                        onResumeLive = gym::resumeLiveSession,
                        onResumeStale = { row ->
                            // The ViewModel's own 続ける is guarded on the modal being open, and this
                            // banner deliberately outlives it — dismissing the prompt decides nothing,
                            // so the banner it left standing must still work. Same two public calls in
                            // the same order, behind the same pure predicate. See this unit's report.
                            gym.onSessionOpened(ActiveSession(row.sessionId, row.routineId, row.routineName))
                            gym.go(GymRoute.Session(row.routineId, resume = true))
                        },
                    )

                    sections.forEach { section ->
                        item(key = "section:${section.keyPrefix}") {
                            SectionHeader(
                                label = section.label,
                                seeAll = section.seeAll,
                                onSeeAll = { gym.selectTab(GymTab.Library) },
                            )
                        }
                        if (section.kind == HomeSectionKind.User && section.cards.isEmpty()) {
                            item(key = "user-empty") {
                                UserRoutinesEmpty(onCreate = { gym.go(GymRoute.Builder(null)) })
                            }
                        }
                        items(section.cards, key = { "${section.keyPrefix}:${it.routineId}" }) { card ->
                            RoutineCardRow(
                                card = card,
                                today = today,
                                zone = zone,
                                onOpen = { gym.openRoutine(card.routineId) },
                                onMenu = { menuFor = card.routineId },
                                onDuplicate = { gym.duplicateRoutine(card.routineId) },
                                onEdit = { gym.go(GymRoute.Builder(card.routineId)) },
                                onDelete = { deleteFor = card.routineId },
                            )
                        }
                    }

                    if (!ready.everTrained && !prefs.safetyNoteAcknowledged) {
                        item(key = "safety") {
                            SafetyFootnote(
                                onOpen = {
                                    scope.launch { gym.preferences.setSafetyNoteAcknowledged() }
                                    gym.go(GymRoute.Safety)
                                },
                                onDismiss = { scope.launch { gym.preferences.setSafetyNoteAcknowledged() } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (menuCard != null) {
        RoutineMenu(
            card = menuCard,
            onDuplicate = { menuFor = null; gym.duplicateRoutine(menuCard.routineId) },
            onEdit = { menuFor = null; gym.go(GymRoute.Builder(menuCard.routineId)) },
            onDelete = { menuFor = null; deleteFor = menuCard.routineId },
            onDismiss = { menuFor = null },
        )
    }

    if (deleteCard != null) {
        // One number decides the sentence **and** the write, so it is computed once and read twice. A
        // dialog whose copy and whose action can disagree is the shape of the defect this replaced.
        val deleteCount = remember(deleteCard, openRoutineId) { deleteRoutineCount(deleteCard, openRoutineId) }
        DeleteRoutineDialog(
            card = deleteCard,
            timesDone = deleteCount,
            onConfirm = {
                deleteFor = null
                // 削除 means two different things and the copy above has already said which: a routine
                // with history is archived so its records stay readable, one without is purged
                // (`04-library-records.md` §1 rules 2 and 4).
                //
                // **No `thenSelect`.** `01-shell.md` §B's actions table gives the long-press row a
                // destination for 編集 only: delete persists and stays put, and the feed re-emitting
                // without the routine is what makes the card disappear. Jumping to 型 is `04` §2's
                // behaviour for the *detail* page, where the deleted routine's own page must be popped.
                if (deleteCount > 0) {
                    gym.archiveRoutine(deleteCard.routineId)
                } else {
                    gym.purgeRoutine(deleteCard.routineId)
                }
            },
            onDismiss = { deleteFor = null },
        )
    }

    ResumePromptHost(gym)
}

/**
 * つづき — one banner, or none. **Never two** (§B edge case 1).
 *
 * A live session and a stale row can both exist: the process died mid-workout and the user started
 * another before coming back. [ResumeAffordance] has already decided which wins — the live one, always
 * — and the stale row is asked about once the live session ends rather than shouting over it.
 *
 * The live banner's elapsed figure is genuinely live: it comes from the same monotonic anchor the
 * session was started on, so a HOME press and a return show the clock having kept running. The stale
 * banner's is "at the time it stopped" and does not tick, because the gap since the last write is
 * unknowable and crediting it would fabricate training (`03-player.md` §A edge case 6).
 *
 * Written as a `LazyListScope` extension rather than a composable so it can contribute its own keyed
 * items — the label and the card — to the page's single list.
 */
private fun LazyListScope.resumeBanner(
    affordance: ResumeAffordance,
    live: ActiveSession?,
    row: ResumableSession?,
    onResumeLive: () -> Unit,
    onResumeStale: (ResumableSession) -> Unit,
) {
    if (affordance == ResumeAffordance.None) return
    if (affordance == ResumeAffordance.LiveBanner) {
        val session = live ?: return
        // The stored row for the live session, when the store has caught up with it. Absent — a read
        // still in flight, one that failed, or a row that belongs to a different session — the banner
        // draws its name and its way back and says nothing else. A missing number is not a zero.
        val liveRow = row?.takeIf { it.sessionId == session.sessionId }
        item(key = "resume") {
            val s = LocalStrings.current
            Column {
                SectionHeader(label = s.gymHome.resumeBanner, seeAll = false, onSeeAll = {})
                val elapsedMs by rememberElapsedRealtime(enabled = liveRow != null)
                ResumeBannerCard(
                    // The session's own stored name, never a resolved one: it is the snapshot the
                    // workout was started under and it is not ours to re-word (§L10).
                    routineName = session.routineName,
                    elapsed = liveRow?.let { s.fmt.durationFromMs(liveElapsedMs(it.clock, elapsedMs)) },
                    progress = liveRow?.let { progressLine(stationsProgressed(it.results), s) },
                    onResume = onResumeLive,
                )
            }
        }
        return
    }
    val stale = row ?: return
    val stopped = recoveredElapsedMs(stale)
    item(key = "resume") {
        val s = LocalStrings.current
        Column {
            SectionHeader(label = s.gymHome.resumeBanner, seeAll = false, onSeeAll = {})
            ResumeBannerCard(
                routineName = stale.routineName,
                elapsed = if (stopped > 0L) s.fmt.durationFromMs(stopped) else null,
                progress = progressLine(stationsProgressed(stale.results), s),
                // 続ける is removed, not disabled, when it cannot honestly be offered — the same
                // predicate the prompt's own doors are cut from.
                onResume = if (resumeOptions(stale).canResume) ({ onResumeStale(stale) }) else null,
            )
        }
    }
}

/**
 * The only bordered card on the page — 0.5.dp of `c.accent` at 35%, the dock's own border treatment,
 * in both themes.
 *
 * One TalkBack node for the whole banner (`"つづき、七分間、六分十四秒 経過、八種目まで進んだ、続ける"`),
 * and the ticking figure announces on focus only. It is deliberately **not** an
 * `accessibilityLiveRegion`: a per-second announcement is unusable and would steal the TTS engine the
 * workout cues need (`00-plan.md` §4.1 rule 5).
 */
@Composable
private fun ResumeBannerCard(
    routineName: String,
    elapsed: String?,
    progress: String?,
    onResume: (() -> Unit)?,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val shape = TempoShapes.Card
    val description = resumeBannerDescription(routineName, elapsed, progress, onResume != null, s)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            // The clip stays rather than being left to `pressable`: this banner is a card whether or
            // not it can be resumed, and a corner that existed only on the tappable branch would be a
            // second thing for the two states to disagree about.
            .clip(shape)
            .background(c.card)
            .border(0.5.dp, c.accent.copy(alpha = 0.35f), shape)
            .then(if (onResume == null) Modifier else Modifier.pressable(shape, onClick = onResume))
            .clearAndSetSemantics {
                contentDescription = description
                if (onResume != null) role = Role.Button
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = routineName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = true),
                style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
            )
            elapsed?.let {
                Text(
                    text = s.gymHome.elapsed(it),
                    style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progress.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = true),
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.5.sp, color = c.inkSoft),
            )
            if (onResume != null) {
                Text(
                    // The arrow is a glyph rather than part of the word — the same 「→」 the prompt's
                    // rows and the safety footnote use — so one member carries 続ける everywhere.
                    text = s.gymHome.resumeAction + " →",
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
                )
            }
        }
    }
}

/** よく使う / 型 / 自分の型 — a heading, and 型's すべて見る beside it. */
@Composable
private fun SectionHeader(label: String, seeAll: Boolean, onSeeAll: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Inset to the cards' internal padding so the label lines up with their titles, exactly as
            // the calendar's day headers do.
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        if (seeAll) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onSeeAll)
                    .semantics { contentDescription = s.gymHome.seeAll },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = s.gymHome.seeAll,
                    style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 2.sp, color = c.accent),
                )
            }
        }
    }
}

/**
 * One routine, in the two or three rows its data earns.
 *
 * **No card height is fixed.** A routine never done has no 前回 and no 最高 and is legitimately two
 * rows tall; at 200% font scale every card grows and nothing clips, because the section labels use
 * padding rather than fixed spacers (§B edge cases 4 and 11).
 *
 * The whole card is one TalkBack node with the long-press declared as `onLongClick(label = "型の操作")`
 * **and** every menu item repeated as a `customActions` entry. A gesture that exists only as a
 * `pointerInput` is invisible to accessibility services, and a menu reachable only by that gesture is a
 * menu a screen-reader user does not have — the note at `NotificationsScreen.kt:204`, applied to the
 * affordance this page hides three actions behind.
 */
@Composable
private fun RoutineCardRow(
    card: RoutineCard,
    today: LocalDate,
    zone: ZoneId,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val lastLine = remember(card, today, zone, s) { lastResultLine(card.lastResult, today, zone, s) }
    val best = remember(card, s) { bestLine(card.best, s) }
    val prThisMonth = remember(card, today) { isPrThisMonth(card.best, today) }
    val timesDone = remember(card, s) { timesDoneLabel(card.timesDone, s) }
    val description = remember(card, lastLine, best, s) { routineCardDescription(card, lastLine, best, s) }

    // 写して作る for anything; 編集 and 削除 only for what the user owns — a built-in is edited by
    // copying it, which is the copy-on-write the whole library is built on. Destructive last, matching
    // the order the resume prompt's options are read in.
    val actions = remember(card.routineId, card.builtIn, s) {
        buildList {
            if (!card.builtIn) add(CustomAccessibilityAction(s.gymLibrary.actionEdit) { onEdit(); true })
            add(CustomAccessibilityAction(s.gymLibrary.actionDuplicate) { onDuplicate(); true })
            if (!card.builtIn) add(CustomAccessibilityAction(s.gymLibrary.actionDelete) { onDelete(); true })
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(c.card, TempoShapes.Card)
            .sizeIn(minHeight = 72.dp)
            .combinedPressable(
                shape = TempoShapes.Card,
                onLongClickLabel = s.gymHome.cardLongPress,
                onLongClick = onMenu,
                onClick = onOpen,
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onLongClick(label = s.gymHome.cardLongPress) { onMenu(); true }
                customActions = actions
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                // A seeded routine reads in the user's language and a user-authored one reads exactly
                // as they typed it — `displayName` falls through to the stored name for any id the
                // catalogue does not know (§L10).
                text = card.displayName(s),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
            )
            Text(
                text = routineCardSummary(card, s),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.5.sp, color = c.inkSoft),
            )
            // The third row exists only when there is something to put in it. `origin` stands where §B's
            // mock draws a tier chip, because a `RoutineCard` carries provenance and not a tier — and
            // provenance is what §B edge case 6 says a built-in shows.
            val trailing = timesDone
            val provenance = card.origin?.takeIf { card.lastResult == null && it.isNotBlank() }
            if (lastLine != null || trailing != null || best != null || provenance != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lastLine ?: provenance.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true),
                        style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                    )
                    trailing?.let {
                        Text(
                            text = it,
                            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
                        )
                    }
                    best?.let {
                        Text(
                            text = it,
                            style = TextStyle(
                                fontFamily = Mincho,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp,
                                // Accent only while the record is this month's, measured against the
                                // day the store itself bucketed (§B edge case 5).
                                color = if (prThisMonth) c.accent else c.inkFaint,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 自分の型's inline empty state — 56.dp, **inside the section**, never a full-page one.
 *
 * The distinction is the page's whole doctrine in miniature: 型はまだありません is a true statement
 * about one section of a page whose other sections are full, and it is the only emptiness 鍛錬 ever
 * claims. It must never stand in for a feed that failed to load.
 */
@Composable
private fun UserRoutinesEmpty(onCreate: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(c.card, TempoShapes.Card)
            .sizeIn(minHeight = 56.dp)
            .pressable(TempoShapes.Card, role = Role.Button, onClick = onCreate)
            .semantics {
                // Two strings joined, never a third one written out: the node has to say the same
                // words the two lines below draw.
                contentDescription =
                    s.gymHome.userEmpty + s.fmt.listSeparator + s.gymHome.createDescription
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = s.gymHome.userEmpty,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
        Text(
            text = s.gymHome.actionCreate + " →",
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}

/**
 * 痛みを感じたらやめる — the first-run footnote, and the only place 安全のために is offered before the
 * user has been to 設定.
 *
 * **Two tap targets, because §B's `FirstRun` row says "dismissible".** The line opens `GYM.SAFETY` and
 * acknowledges the note, which is that page's own `setSafetyNoteAcknowledged()` contract ("except when
 * reached from the first-run note"); 閉じる acknowledges without going anywhere. A single target was
 * the first shape and it was wrong in a way that is easy to miss: the only way to clear a permanent
 * footnote was to walk into a page, so a user who did not want to read the safety page had to visit it
 * anyway — and in Phase 1 `GymShell` still resolves `GymRoute.Safety` to a title-only placeholder, so
 * the note retired itself by showing an empty page. Acknowledgement must be something the user can
 * *choose*, never a side effect of navigation. 閉じる is `03-player.md` :562's own dismissal word,
 * reused rather than invented.
 *
 * **The one-time "not medical advice" note §B also lists is deliberately absent.** No spec supplies
 * its text — `01-shell.md` §B GYM.SAFETY owns that prose and has not written it — and `00-plan.md` §2
 * forbids inventing copy. It arrives when that page does, not before.
 */
@Composable
private fun SafetyFootnote(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 18.dp).height(1.dp).background(c.hair))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // `GYM.SAFETY`'s own line and title, read from that page's namespace. The footnote is
                // a door into that page, so a second copy of its words here is a second thing to keep
                // in step with it.
                text = s.gymSettings.safetyLine,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .sizeIn(minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onOpen)
                    .semantics {
                        contentDescription = s.gymSettings.safetyLine +
                            s.fmt.listSeparator + s.gymSettings.safetyTitle
                    }
                    .padding(vertical = 20.dp),
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
            )
            Text(
                text = s.gymHome.close,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = s.gymHome.close }
                    .padding(vertical = 20.dp),
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, color = c.inkFaint),
            )
        }
    }
}

/**
 * A failed write, with a way out that does not depend on the fault having one.
 *
 * `DECISIONS.md` §Q6 leaves `Rejected`, `RoutineGone`, `SessionGone` and `StoreFull` with **no action
 * word**, and `FaultStrip` draws its tap target only when one exists — so a failed 写して作る on a
 * routine that has left the library used to pin an unremovable strip to the top of 鍛錬 for the rest of
 * the visit. Only leaving the shell cleared it, which is a way out the user has no reason to guess.
 *
 * 閉じる appears **only** for those faults ([writeFaultStuck]). The ones carrying もう一度 already
 * dismiss themselves on the retry, and a second word beside the first would read as a second decision.
 */
@Composable
private fun WriteFaultStrip(fault: GymFault, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaultStrip(fault = fault, onRecover = onRetry, modifier = Modifier.weight(1f, fill = true))
        if (writeFaultStuck(fault)) {
            Text(
                text = s.gymHome.close,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = s.gymHome.close }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.inkFaint),
            )
        }
    }
}

/**
 * The long-press menu, as a dialog rather than a floating popup.
 *
 * A `DropdownMenu` would be the reflex and would be the app's first floating surface that is not the
 * dock; the dialog idiom already exists here (`BlockConfirmDialog`, `EventConfirmDialog`), consumes
 * Back through its own window, and takes focus on appearance without a hand-written focus request.
 * `00-plan.md` §4.1 rule 4's requirement is met on the card itself, where the actions are also
 * `customActions` — this menu is the sighted user's route to them, not the only one.
 */
@Composable
private fun RoutineMenu(
    card: RoutineCard,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = card.displayName(s),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (!card.builtIn) MenuRow(s.gymLibrary.actionEdit, c.ink, onEdit)
                MenuRow(s.gymLibrary.actionDuplicate, c.ink, onDuplicate)
                if (!card.builtIn) MenuRow(s.gymLibrary.actionDelete, c.inkSoft, onDelete)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.gymHome.cancel, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}

@Composable
private fun MenuRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A menu row inside a dialog: full width, no fill of its own, so `Row`'s 14.dp is what
            // separates one destructive answer from the two above it under a finger.
            .sizeIn(minHeight = 56.dp)
            .pressable(TempoShapes.Row, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = color),
        )
    }
}

/**
 * 削除's confirmation, whose copy branches on whether anything readable is about to be destroyed.
 *
 * The branch is not decoration: with sessions behind it the routine is archived and every record it
 * produced stays readable; with none it is purged outright and 完全に削除 says so. Two words for two
 * outcomes, because "削除" alone would mean the reversible one to a user who has read it once before.
 *
 * @param timesDone the count the branch is taken on — [deleteRoutineCount]'s, **not** the card's raw
 *   `timesDone`, which counts only finished sessions and so put the routine holding an interrupted
 *   one on the irreversible branch. The caller passes the same number it then acts on, so the sentence
 *   and the write cannot disagree.
 */
@Composable
private fun DeleteRoutineDialog(
    card: RoutineCard,
    timesDone: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    // The name in the title is the *displayed* one, so the sentence names the routine the user is
    // looking at. Nothing here is written back, so resolving it is safe (§L10's read-side rule).
    val name = card.displayName(s)
    val copy = remember(name, timesDone, s) { deleteRoutineCopy(name, timesDone, s) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = copy.title,
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            Text(
                text = copy.body,
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp, color = c.inkSoft),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(copy.confirm, style = TextStyle(fontFamily = Mincho, color = c.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.gymHome.cancel, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}

/** 読み込み中 — and nothing else. A page that is still arriving must not look like a page with nothing on it. */
@Composable
private fun HomeMessage(text: String) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}

/**
 * `SystemClock.elapsedRealtime()`, once a second, and only while the window is at least STARTED.
 *
 * The monotonic clock rather than the wall clock, because that is the timebase the session was anchored
 * on: it includes deep sleep, it is untouched by the system clock, and it is what makes a for-time
 * record unfakeable (`03-player.md` §E.4). `rememberSecondTime` reads `LocalDateTime.now()` and is the
 * flip clock's; the two must not be swapped.
 *
 * Suspends below STARTED exactly as `MinuteClock` does — a backgrounded launcher does no CPU wakeups —
 * and is mounted only when a live banner is actually drawing.
 */
@Composable
private fun rememberElapsedRealtime(enabled: Boolean): State<Long> {
    val value = remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, enabled) {
        if (!enabled) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value.value = SystemClock.elapsedRealtime()
                delay(1_000L)
            }
        }
    }
    return value
}
