package io.eddiegulay.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.theme.LocalTempoColors

/**
 * The bottom dock: an iOS-style floating pill holding Home / Search / Notifications / 鍛錬, the
 * active tab in vermillion accent. The capsule reads as floating over content via a faint card fill
 * and a hairline border. A slim indicator sits below — long-pressing it requests the default-home role.
 *
 * **鍛錬 is a fourth button rather than a long-press on ホーム.** Two reasons, and the second is the
 * deciding one. A long-press is undiscoverable for a whole mode of the app — the clock's long-press
 * was reasonable for 集中 because 集中 hides everything anyway, but a workout log you are meant to
 * return to daily needs a place you can point at. And the pill *already* long-presses: the Row above
 * claims that gesture for the default-home role, so a long-press on a child button would sit inside
 * a parent that wants the same gesture, and which of the two won would depend on hit-test order.
 *
 * Four is still under the bar this dock sets for itself. A fifth would not be.
 *
 * The theme toggle no longer lives here; it moved to the Search screen's top-right.
 */
@Composable
fun Dock(
    current: Screen,
    isDefaultLauncher: Boolean,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onGym: () -> Unit,
    onRequestDefault: () -> Unit,
    modifier: Modifier = Modifier,
    frosted: Boolean = false,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val pillShape = RoundedCornerShape(percent = 50)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Floating capsule. Over Home it's a faint card pill on the wallpaper; over a sub-screen it
        // turns into a heavy translucent "wet wrinkled paper" panel so the list reads through it.
        // The 0.5dp border carries a hint of the accent. Long-pressing the pill requests the
        // default-home role (the old indicator bar carried this; it now lives on the capsule itself).
        Row(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .clip(pillShape)
                .then(if (frosted) Modifier.wetPaper(c) else Modifier.background(c.card))
                .border(0.5.dp, c.accent.copy(alpha = if (frosted) 0.35f else 0.25f), pillShape)
                .pointerInput(isDefaultLauncher) {
                    detectTapGestures(
                        onLongPress = { if (!isDefaultLauncher) onRequestDefault() },
                    )
                }
                .semantics {
                    if (!isDefaultLauncher) {
                        onLongClick(label = s.app.dockSetDefault) {
                            onRequestDefault()
                            true
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockButton(TempoIcons.Home, active = current == Screen.Home, contentDescription = s.app.dockHome, onClick = onHome)
            DockButton(TempoIcons.Search, active = current == Screen.Search, contentDescription = s.app.dockSearch, onClick = onSearch)
            DockButton(TempoIcons.Bell, active = current == Screen.Notifications, contentDescription = s.app.dockNotifications, onClick = onNotifications)
            DockButton(TempoIcons.Dumbbell, active = current == Screen.Gym, contentDescription = s.app.dockGym, onClick = onGym)
        }
    }
}

@Composable
private fun DockButton(
    paths: List<String>,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val c = LocalTempoColors.current
    val tint = if (active) c.accent else c.inkFaint
    Box(
        modifier = Modifier
            // 48dp meets the minimum accessible touch target while the glyph stays 23dp.
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(paths = paths, color = tint, size = 23.dp)
    }
}
