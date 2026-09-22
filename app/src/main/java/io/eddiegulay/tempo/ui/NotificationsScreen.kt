package io.eddiegulay.tempo.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.notification.NotificationGroup
import io.eddiegulay.tempo.notification.TempoNotification
import io.eddiegulay.tempo.notification.TempoNotificationAction
import io.eddiegulay.tempo.notification.TempoNotificationListener
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable

/**
 * Notifications (通知): a quiet, sparse list of the device's current notifications.
 *
 * Reading them requires notification-listener access, so until that's granted we show a calm
 * tap-to-enable prompt rather than fake content. Access state is re-checked whenever the launcher
 * returns to the foreground (the user may have toggled it in Settings).
 */
@Composable
fun NotificationsScreen(
    viewModel: LauncherViewModel,
    sharingKey: String? = null,
    onShare: (TempoNotification, Rect) -> Unit = { _, _ -> },
    onShareBoundsChange: (Rect) -> Unit = {},
    onCloseShare: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val now by rememberMinuteTime()

    var enabled by remember { mutableStateOf(TempoNotificationListener.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = TempoNotificationListener.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val groups by viewModel.grouped.collectAsStateWithLifecycle()
    val pending by viewModel.pendingDismiss.collectAsStateWithLifecycle()
    // Per-app expand state for the "N more" collapse; keyed by package, survives recomposition.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(groups, sharingKey) {
        val key = sharingKey ?: return@LaunchedEffect
        val stillThere = groups.any { group -> group.items.any { it.key == key } }
        if (!stillThere) onCloseShare()
    }

    // Nudge the system to reconnect the listener if access is granted but it isn't bound yet
    // (e.g. after a process restart).
    LaunchedEffect(enabled) {
        if (enabled) viewModel.requestNotificationRebind(context)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = s.notifications.title,
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = s.fmt.era(now) + s.fmt.separator + s.fmt.monthDay(now),
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            if (enabled && groups.isNotEmpty() && sharingKey == null) {
                ClearAllButton(onClick = { viewModel.dismissAllVisible() })
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                !enabled -> EnableAccessPrompt(
                    onClick = {
                        context.startActivity(
                            Intent(TempoNotificationListener.settingsAction)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )

                groups.isEmpty() -> QuietState()

                // The bottom inset clears the floating dock pill, exactly as the Calendar, Search and
                // Filter lists do. Without it the last notification — and the swipe that clears it —
                // sat underneath the capsule and could not be reached.
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 6.dp)
                        .then(if (sharingKey != null) Modifier.clearAndSetSemantics { } else Modifier),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    groups.forEach { group ->
                        val collapsible = group.items.size > COLLAPSE_THRESHOLD
                        val isExpanded = expanded[group.packageName] == true
                        val visible = if (collapsible && !isExpanded) {
                            group.items.take(COLLAPSE_THRESHOLD)
                        } else {
                            group.items
                        }
                        val hidden = group.items.size - visible.size

                        item(key = "header:${group.packageName}") { GroupHeader(group) }
                        items(visible, key = { it.key }) { n ->
                            NotifRow(
                                n = n,
                                lifted = sharingKey == n.key,
                                dismissEnabled = sharingKey == null,
                                onOpen = { viewModel.openNotification(context, n) },
                                onDismiss = { viewModel.dismissNotification(n.key) },
                                onShare = { bounds -> onShare(n, bounds) },
                                onShareBoundsChange = onShareBoundsChange,
                                onAction = { idx -> viewModel.sendNotificationAction(n.key, idx) },
                                onReply = { idx, text -> viewModel.replyToNotification(n.key, idx, text) },
                            )
                        }
                        if (collapsible) {
                            item(key = "more:${group.packageName}") {
                                CollapseToggle(
                                    expanded = isExpanded,
                                    hiddenCount = hidden,
                                    onToggle = { expanded[group.packageName] = !isExpanded },
                                )
                            }
                        }
                    }
                }
            }

            // Transient undo affordance — auto-fades when the window commits (pending clears).
            if (pending.isNotEmpty() && sharingKey == null) {
                UndoStrip(
                    count = pending.size,
                    onUndo = { viewModel.undoDismiss() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifRow(
    n: TempoNotification,
    lifted: Boolean,
    dismissEnabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (Rect) -> Unit,
    onShareBoundsChange: (Rect) -> Unit,
    onAction: (Int) -> Unit,
    onReply: (Int, String) -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    // A single, readable TalkBack announcement for the whole row, plus explicit actions for
    // dismiss / share. The swipe and the 900ms hold are both invisible to accessibility
    // services, so without these a screen-reader user could read a notification but never
    // clear it or export it. TalkBack's long-click opens the overlay immediately — the hold
    // wait is a finger-only accident brake.
    val rowDescription = remember(n.appLabel, n.title, n.body, n.time, s) {
        listOf(n.appLabel, n.title, n.body.takeIf { it.isNotBlank() }, n.time)
            .filterNotNull()
            .joinToString(s.fmt.listSeparator)
    }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    var suppressOpen by remember { mutableStateOf(false) }
    val sharePress = remember { MutableInteractionSource() }
    LaunchedEffect(lifted) {
        if (!lifted) suppressOpen = false
    }
    ShareHoldEffect(enabled = dismissEnabled, interactionSource = sharePress) {
        suppressOpen = true
        onShare(cardBounds)
    }
    val rowActions = remember(onDismiss, s) {
        listOf(CustomAccessibilityAction(label = s.notifications.rowDismiss) { onDismiss(); true })
    }

    // Swipe either direction to clear; the list removes the row once the service reports it gone.
    // Frozen while any card is lifted so a leftover drag cannot dismiss the thing being shared.
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.6f },
        confirmValueChange = { value ->
            if (dismissEnabled && value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        },
    )
    LaunchedEffect(dismissEnabled) {
        if (!dismissEnabled) dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = dismissEnabled,
        enableDismissFromEndToStart = dismissEnabled,
        backgroundContent = {},
    ) {
        // Each notification is its own soft card — a faint washi fill rounded at the corners with a
        // small gap to its neighbours — rather than a full-bleed row split by hairlines. The gentle
        // radius and internal breathing room let each one read as a discrete, calm object.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(TempoShapes.Card)
                .background(c.card),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        cardBounds = rect
                        if (lifted) onShareBoundsChange(rect)
                    }
                    .graphicsLayer { alpha = if (lifted) 0f else 1f }
                    // The summary is the pressable part of the card, not the whole card: the action
                    // chips below open nothing. So the wash takes the card's own corners at the top
                    // and squares off at the bottom whenever there are actions under it, meeting them
                    // flush instead of curving away from a card that visibly continues.
                    .pressable(
                        shape = if (n.actions.isEmpty()) TempoShapes.Card else SUMMARY_OVER_ACTIONS,
                        interactionSource = sharePress,
                        onClick = {
                            if (suppressOpen) {
                                suppressOpen = false
                            } else {
                                onOpen()
                            }
                        },
                    )
                    // The tappable summary is one TalkBack node: open on activate, share on
                    // long-click, dismiss as an action. Inline actions below stay separately
                    // focusable (not in this subtree). Role and label stay in this block rather
                    // than moving to `pressable`: `clearAndSetSemantics` replaces whatever the
                    // click modifier declared, so a role passed there would be discarded.
                    // When lifted the list is hidden from TalkBack (the overlay words remain);
                    // an empty semantics node here would still be activatable as "Open".
                    .then(
                        if (lifted) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier.clearAndSetSemantics {
                                contentDescription = rowDescription
                                onClick(label = s.notifications.rowOpen) { onOpen(); true }
                                onLongClick(label = s.notifications.share) {
                                    onShare(cardBounds)
                                    true
                                }
                                customActions = rowActions
                            }
                        },
                    ),
            ) {
                NotificationCardFace(
                    n = n,
                    fill = Color.Transparent,
                )
            }
            if (n.actions.isNotEmpty()) {
                ActionsRow(actions = n.actions, onAction = onAction, onReply = onReply)
            }
        }
    }
}

/**
 * Inline notification actions. Plain actions fire on tap; a reply action toggles an inline field
 * whose submit routes back through the service's RemoteInput plumbing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsRow(
    actions: List<TempoNotificationAction>,
    onAction: (Int) -> Unit,
    onReply: (Int, String) -> Unit,
) {
    var replyingIndex by remember { mutableStateOf<Int?>(null) }

    // Indent to align beneath the title: card padding (18) + icon (20) + row spacing (16).
    Column(Modifier.fillMaxWidth().padding(start = 54.dp, end = 18.dp, bottom = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            actions.forEachIndexed { index, action ->
                ActionChip(
                    label = action.title,
                    onClick = {
                        if (action.isReply) {
                            replyingIndex = if (replyingIndex == index) null else index
                        } else {
                            onAction(index)
                        }
                    },
                )
            }
        }
        replyingIndex?.let { index ->
            ReplyField(
                onSend = { text ->
                    onReply(index, text)
                    replyingIndex = null
                },
            )
        }
    }
}

/**
 * One inline action.
 *
 * A lozenge rather than the row shape: this is a bare accent word with no fill of its own, so the
 * press wash is the only outline it ever draws, and a rounded rectangle around 返信 would look like a
 * button that forgot its border. `minWidth` grows the target rightward into the gap between chips —
 * a two-character label is 30dp wide, which is not a touch target — while `CenterStart` keeps the word
 * itself exactly where it was.
 */
@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 1.sp, color = c.accent),
        )
    }
}

@Composable
private fun ReplyField(onSend: (String) -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(fontFamily = Gothic, fontSize = 15.sp, color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text.trim()) }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
            .focusRequester(focusRequester)
            .semantics { contentDescription = s.notifications.replyDescription },
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 6.dp)) {
                    if (text.isEmpty()) {
                        Text(
                            text = s.notifications.replyPlaceholder,
                            style = TextStyle(fontFamily = Gothic, fontSize = 15.sp, color = c.inkFaint),
                        )
                    }
                    inner()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
            }
        },
    )
}

/** Beyond this many notifications, an app's bucket collapses behind an "N more" toggle. */
private const val COLLAPSE_THRESHOLD = 4

/**
 * The card corner with its bottom squared off: the press shape of a notification summary that has
 * action chips beneath it inside the same card.
 *
 * Derived from [TempoShapes.Card] rather than restated, so the two halves of a card can never round
 * by different amounts, and hoisted to a file constant so the branch in [NotifRow] allocates nothing
 * per frame.
 */
private val SUMMARY_OVER_ACTIONS: RoundedCornerShape =
    TempoShapes.Card.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))

/** A quiet per-app header: faint tinted icon, mincho label, count. One TalkBack node. */
@Composable
private fun GroupHeader(group: NotificationGroup) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Inset to the card's internal padding so the header icon lines up with the row icons.
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)
            .clearAndSetSemantics {
                contentDescription = s.notifications.groupHeader(group.appLabel, group.items.size)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val icon = group.items.first().icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(c.inkFaint),
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = group.appLabel,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        Text(
            // Left as arabic in both languages, deliberately. `fmt.count` would spell this 三 in
            // Japanese per §Q4 (a stopped value is kanji), but this is a badge beside the app name,
            // not a value in a sentence, and it draws identically in both languages today. Changing
            // what a Japanese user sees here is a design decision about badges, not a translation.
            text = group.items.size.toString(),
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 1.sp, color = c.inkFaint),
        )
    }
}

/**
 * The "N more" / "show less" expander for an over-long app bucket.
 *
 * It spans the list's width and sits between two washi cards, so it takes the card corner rather than
 * a lozenge — pressed, it reads as the gap between the cards darkening, which is what it acts on. It
 * was 40dp tall before the `sizeIn`, which is under the floor for a control this easy to mis-tap.
 */
@Composable
private fun CollapseToggle(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val label = if (expanded) s.notifications.collapse else s.notifications.more(hiddenCount)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .pressable(TempoShapes.Card, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = label }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}

/** The quiet clear-all control in the header. A word, so a lozenge — see [ActionChip]. */
@Composable
private fun ClearAllButton(onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = s.notifications.clearAll }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = s.notifications.clearAll,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.inkFaint),
        )
    }
}

/**
 * Bottom strip offering to undo the in-flight dismissals before they commit.
 *
 * A floating washi card, not a full-bleed bar. It used to be an edge-to-edge rectangle pinned to the
 * bottom of the list — square corners, hard edges, and sitting *underneath* the floating dock, which
 * put the one control with a deadline where a finger could not reach it. Inset and rounded, it is the
 * same object as the notification cards it hovers over, and the bottom margin clears the capsule.
 */
@Composable
private fun UndoStrip(count: Int, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, bottom = 84.dp)
            .clip(TempoShapes.Card)
            .background(c.card)
            .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = s.notifications.undoCount(count),
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.inkSoft),
        )
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .pressable(TempoShapes.Word, role = Role.Button, onClick = onUndo)
                .semantics { contentDescription = s.notifications.undo }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = s.notifications.undo,
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
            )
        }
    }
}

@Composable
private fun EnableAccessPrompt(onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = s.notifications.accessTitle,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 4.sp, color = c.inkSoft),
            )
            // Was a bare `clickable` on a 22dp-tall line with no role: a tap target under half the
            // floor, and a screen reader could read the sentence but not learn it was a button. Same
            // treatment as the calendar's identical gate.
            Text(
                text = s.notifications.accessAction,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 3.sp, color = c.accent),
            )
        }
    }
}

@Composable
private fun QuietState() {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = s.notifications.empty,
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}
