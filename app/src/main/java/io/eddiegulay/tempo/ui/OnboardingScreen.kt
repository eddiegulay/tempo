package io.eddiegulay.tempo.ui

import android.content.Intent
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.notification.TempoNotificationListener
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable

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
    lang: Lang,
    onChooseLanguage: (Lang) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
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

        // The language row sits ABOVE the greeting, which is the only position that works. Everything
        // below it — the greeting, the promise that nothing leaves the device, both permission
        // rationales — is prose the user has to be able to read *before* granting anything. A control
        // placed after them would be an apology; placed here it is a prerequisite.
        LanguageChoice(current = lang, onChoose = onChooseLanguage)

        Spacer(Modifier.height(28.dp))

        Text(
            text = s.onboarding.welcome,
            style = TextStyle(fontFamily = Mincho, fontSize = 30.sp, letterSpacing = 6.sp, color = c.ink),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = s.onboarding.preamble,
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
            title = s.onboarding.defaultHomeTitle,
            rationale = s.onboarding.defaultHomeRationale,
            granted = isDefaultLauncher,
            deferred = launcherDeferred,
            onGrant = onRequestDefault,
            onDefer = { launcherDeferred = true },
        )

        Spacer(Modifier.height(28.dp))

        AccessItem(
            title = s.onboarding.notificationAccessTitle,
            rationale = s.onboarding.notificationAccessRationale,
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
    val s = LocalStrings.current

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
            granted -> StateLine(text = s.onboarding.granted, color = c.accent)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                TextAction(label = s.onboarding.grant, color = c.accent, onClick = onGrant)
                if (!deferred) {
                    TextAction(label = s.onboarding.later, color = c.inkFaint, onClick = onDefer)
                } else {
                    StateLine(text = s.onboarding.laterSet, color = c.inkFaint)
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

/**
 * 許可 / あとで. A lozenge for the same reason the header actions are: a bare word with no fill of its
 * own, where a rectangle would be the only hard edge on a page made of prose and hairlines. `minWidth`
 * grows the target rightward into the 28dp gap so あとで is 48dp wide without moving a pixel of type.
 */
@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
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
    val s = LocalStrings.current
    val color = if (enabled) c.accent else c.inkFaint
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            // The one filled control in the app, and already a capsule — so the press takes
            // `CircleShape` too and the wash lands exactly on the fill rather than in a box around it.
            .background(color.copy(alpha = 0.07f), CircleShape)
            .pressable(CircleShape, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = s.onboarding.begin },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = s.onboarding.begin,
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 6.sp, color = color),
        )
    }
}

/**
 * The language choice, offered before anything is explained.
 *
 * **This is a consent control, not a convenience.** The two paragraphs below it say that Tempo will
 * read every notification and become the home app, and that nothing leaves the device. A user who
 * cannot read those paragraphs and taps 許可 has not consented to anything; they have pressed a
 * button. Offering the choice here is what makes the rest of the screen mean what it says.
 *
 * Two words rather than [LanguageDialog], because a modal that opens over the first screen a user
 * ever sees — before any greeting — reads as an error. Inline, both options are visible and the
 * screen redraws under the tap.
 *
 * Neither word is translated, for the reason [LanguageDialog] gives at length: an endonym is
 * addressed to the person who wants that language.
 */
@Composable
private fun LanguageChoice(current: Lang, onChoose: (Lang) -> Unit) {
    val c = LocalTempoColors.current

    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        LanguageWord(word = "日本語", selected = current == Lang.Ja, onClick = { onChoose(Lang.Ja) })
        Box(Modifier.size(width = 1.dp, height = 12.dp).background(c.hair))
        LanguageWord(word = "English", selected = current == Lang.En, onClick = { onChoose(Lang.En) })
    }
}

/** One endonym. Selected carries the accent; the other stays faint and is still a 48.dp target. */
@Composable
private fun LanguageWord(word: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = word },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = word,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = if (selected) c.accent else c.inkFaint,
            ),
        )
    }
}
