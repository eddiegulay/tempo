package io.eddiegulay.tempo.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.eddiegulay.tempo.notification.TempoNotificationListener
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * First-launch gate. Before Tempo reaches Home it names every access it relies on and why, then lets
 * the user grant each (or defer it). Both grants are special-access toggles handed off to system
 * Settings, so we can't show the standard runtime dialog — instead we open the right Settings screen
 * and re-read the grant when the launcher returns to the foreground.
 *
 * Nothing here is dangerous-permission gated and no data leaves the device; the copy says as much.
 * The walkthrough is "gated" only in that 始める stays inert until each access is either granted or
 * explicitly deferred — the user always sees the full picture first.
 *
 * @param isDefaultLauncher live HOME-role status (refreshed by MainActivity on every resume).
 * @param onRequestDefault  asks the system to make Tempo the default home app.
 * @param onComplete        persists "onboarding done" and reveals Home.
 */
@Composable
fun OnboardingScreen(
    isDefaultLauncher: Boolean,
    onRequestDefault: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val context = LocalContext.current

    // Notification access can be toggled in Settings while we're away; re-read it on each resume.
    var notifEnabled by remember { mutableStateOf(TempoNotificationListener.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifEnabled = TempoNotificationListener.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // "Defer" is local intent — it lets the gate clear without granting, but never persists.
    var launcherDeferred by remember { mutableStateOf(false) }
    var notifDeferred by remember { mutableStateOf(false) }

    val launcherSettled = isDefaultLauncher || launcherDeferred
    val notifSettled = notifEnabled || notifDeferred
    val canBegin = launcherSettled && notifSettled

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            text = "ようこそ",
            style = TextStyle(fontFamily = Mincho, fontSize = 30.sp, letterSpacing = 6.sp, color = c.ink),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "はじめる前に、Tempo が使う権限をお知らせします。" +
                "いずれも端末の中だけで使われ、外部へ送信されることはありません。",
            style = TextStyle(
                fontFamily = Gothic,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.5.sp,
                color = c.inkSoft,
            ),
        )

        Spacer(Modifier.height(40.dp))

        AccessItem(
            title = "既定のホーム",
            rationale = "ホームボタンを押したときに Tempo が開くようにします。" +
                "ランチャーとしての基本的な動作に必要です。",
            granted = isDefaultLauncher,
            deferred = launcherDeferred,
            onGrant = onRequestDefault,
            onDefer = { launcherDeferred = true },
        )

        Spacer(Modifier.height(28.dp))

        AccessItem(
            title = "通知へのアクセス",
            rationale = "受信した通知を読み取り、通知画面（通知）に静かに表示します。" +
                "内容が端末の外に出ることはありません。",
            granted = notifEnabled,
            deferred = notifDeferred,
            onGrant = {
                context.startActivity(
                    Intent(TempoNotificationListener.settingsAction)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onDefer = { notifDeferred = true },
        )

        Spacer(Modifier.height(48.dp))

        BeginButton(enabled = canBegin, onClick = onComplete)

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * One access: kanji label, a status dot, the "why", and either grant/defer actions or a settled
 * state line. Deferred access can still be granted — the 許可 control stays available.
 */
@Composable
private fun AccessItem(
    title: String,
    rationale: String,
    granted: Boolean,
    deferred: Boolean,
    onGrant: () -> Unit,
    onDefer: () -> Unit,
) {
    val c = LocalTempoColors.current

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            StatusDot(active = granted)
            Text(
                text = title,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 3.sp, color = c.ink),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = rationale,
            style = TextStyle(
                fontFamily = Gothic,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.5.sp,
                color = c.inkSoft,
            ),
        )
        Spacer(Modifier.height(14.dp))
        when {
            granted -> StateLine(text = "許可済み", color = c.accent)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                TextAction(label = "許可", color = c.accent, onClick = onGrant)
                if (!deferred) {
                    TextAction(label = "後で", color = c.inkFaint, onClick = onDefer)
                } else {
                    StateLine(text = "後で設定", color = c.inkFaint)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    val c = LocalTempoColors.current
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (active) c.accent else c.inkFaint.copy(alpha = 0.4f)),
    )
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 2.sp, color = color),
        )
    }
}

@Composable
private fun StateLine(text: String, color: Color) {
    Box(Modifier.sizeIn(minHeight = 48.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = color),
        )
    }
}

@Composable
private fun BeginButton(enabled: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val color = if (enabled) c.accent else c.inkFaint
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.07f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "始める" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "始める",
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 6.sp, color = color),
        )
    }
}
