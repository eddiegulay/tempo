package io.eddiegulay.tempo.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import io.eddiegulay.tempo.notification.TempoNotification
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

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    // Nudge the system to reconnect the listener if access is granted but it isn't bound yet
    // (e.g. after a process restart).
    LaunchedEffect(enabled) {
        if (enabled) viewModel.requestNotificationRebind(context)
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp)) {
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

                notifications.isEmpty() -> QuietState()

                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp)) {
                    items(notifications, key = { it.key }) { n ->
                        NotifRow(
                            n = n,
                            onOpen = { viewModel.openNotification(n) },
                            onDismiss = { viewModel.dismissNotification(n.key) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifRow(n: TempoNotification, onOpen: () -> Unit, onDismiss: () -> Unit) {
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
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bgSolid)
                // Present the row as one node: open on activation, with an explicit dismiss action.
                .clearAndSetSemantics {
                    contentDescription = rowDescription
                    onClick(label = "開く") { onOpen(); true }
                    customActions = dismissAction
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 4.dp, vertical = 17.dp),
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
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
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
