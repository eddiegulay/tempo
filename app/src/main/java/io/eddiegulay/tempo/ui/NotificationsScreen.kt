package io.eddiegulay.tempo.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.notification.NotificationGroup
import io.eddiegulay.tempo.notification.TempoNotification
import io.eddiegulay.tempo.notification.TempoNotificationAction
import io.eddiegulay.tempo.notification.TempoNotificationListener
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * Notifications (通知): a quiet, sparse list of the device's current notifications.
 *
 * Reading them requires notification-listener access, so until that's granted we show a calm
 * tap-to-enable prompt rather than fake content. Access state is re-checked whenever the launcher
 * returns to the foreground (the user may have toggled it in Settings).
 */
@Composable
fun NotificationsScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
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
    // Per-app expand state for the 他X件 collapse; keyed by package, survives recomposition.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

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
                    text = "通知",
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "${JapaneseDate.era(now)} ・ ${JapaneseDate.monthDay(now)}",
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                )
            }
            if (enabled && groups.isNotEmpty()) {
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

                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp)) {
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
                                onOpen = { viewModel.openNotification(n) },
                                onDismiss = { viewModel.dismissNotification(n.key) },
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
            if (pending.isNotEmpty()) {
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
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
    onReply: (Int, String) -> Unit,
) {
    val c = LocalTempoColors.current

    // A single, readable TalkBack announcement for the whole row, plus an explicit "dismiss" action
    // (the swipe gesture below is invisible to accessibility services, so without this a screen-reader
    // user could read a notification but never clear it).
    val rowDescription = remember(n.appLabel, n.title, n.body, n.time) {
        listOf(n.appLabel, n.title, n.body.takeIf { it.isNotBlank() }, n.time)
            .filterNotNull()
            .joinToString("、")
    }
    val dismissAction = remember(onDismiss) {
        listOf(CustomAccessibilityAction(label = "消去") { onDismiss(); true })
    }

    // Swipe either direction to clear; the list removes the row once the service reports it gone.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth(),
        backgroundContent = {},
    ) {
        // Each notification is its own soft card — a faint washi fill rounded at the corners with a
        // small gap to its neighbours — rather than a full-bleed row split by hairlines. The gentle
        // radius and internal breathing room let each one read as a discrete, calm object.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.card),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    // The tappable summary is one TalkBack node: open on activate, dismiss as an
                    // action. Inline actions below stay separately focusable (not in this subtree).
                    .clearAndSetSemantics {
                        contentDescription = rowDescription
                        onClick(label = "開く") { onOpen(); true }
                        customActions = dismissAction
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (n.icon != null) {
                    Image(
                        bitmap = n.icon,
                        contentDescription = n.appLabel,
                        colorFilter = ColorFilter.tint(c.inkSoft),
                        modifier = Modifier.padding(top = 2.dp).size(20.dp),
                    )
                } else {
                    Spacer(Modifier.width(20.dp))
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = n.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = true),
                            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
                        )
                        Text(
                            text = n.time,
                            style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
                        )
                    }
                    if (n.body.isNotBlank()) {
                        Text(
                            text = n.body,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = Gothic, fontSize = 13.sp, lineHeight = 19.5.sp, color = c.inkSoft),
                        )
                    }
                    Text(
                        text = n.appLabel,
                        style = TextStyle(fontFamily = Mincho, fontSize = 11.sp, letterSpacing = 3.sp, color = c.inkFaint),
                    )
                }
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

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button },
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
            .semantics { contentDescription = "返信を入力" },
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 6.dp)) {
                    if (text.isEmpty()) {
                        Text(
                            text = "返信",
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

/** Beyond this many notifications, an app's bucket collapses behind a 他X件 toggle. */
private const val COLLAPSE_THRESHOLD = 4

/** A quiet per-app header: faint tinted icon, mincho label, count. One TalkBack node. */
@Composable
private fun GroupHeader(group: NotificationGroup) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Inset to the card's internal padding so the header icon lines up with the row icons.
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)
            .clearAndSetSemantics { contentDescription = "${group.appLabel}、${group.items.size}件" },
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
            text = group.items.size.toString(),
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 1.sp, color = c.inkFaint),
        )
    }
}

/** The 他X件 / 折りたたむ expander for an over-long app bucket. */
@Composable
private fun CollapseToggle(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    val c = LocalTempoColors.current
    val label = if (expanded) "折りたたむ" else "他 $hiddenCount 件"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { role = Role.Button; contentDescription = label }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.accent),
        )
    }
}

/** The quiet すべて消去 (clear all) control in the header. */
@Composable
private fun ClearAllButton(onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "すべて消去" }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "すべて消去",
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.inkFaint),
        )
    }
}

/** Bottom strip offering to undo the in-flight dismissals before they commit. */
@Composable
private fun UndoStrip(count: Int, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.card)
            .padding(start = 28.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count 件を消去",
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.inkSoft),
        )
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onUndo)
                .semantics { role = Role.Button; contentDescription = "元に戻す" }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "元に戻す",
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = c.accent),
            )
        }
    }
}

@Composable
private fun EnableAccessPrompt(onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "通知へのアクセス",
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 4.sp, color = c.inkSoft),
            )
            Text(
                text = "タップして許可",
                modifier = Modifier.clickable(onClick = onClick),
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 3.sp, color = c.accent),
            )
        }
    }
}

@Composable
private fun QuietState() {
    val c = LocalTempoColors.current
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "通知はありません",
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
        )
    }
}
