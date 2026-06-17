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
import io.eddiegulay.tempo.ui.theme.LocalTempoColors

/**
 * The bottom dock: an iOS-style floating pill holding Home / Search / Notifications, the active tab
 * in vermillion accent. The capsule reads as floating over content via a faint card fill and a
 * hairline border. A slim indicator sits below — long-pressing it requests the default-home role.
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
    onRequestDefault: () -> Unit,
    modifier: Modifier = Modifier,
    frosted: Boolean = false,
) {
    val c = LocalTempoColors.current
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
                        onLongClick(label = "Tempoを既定のホームに設定") {
                            onRequestDefault()
                            true
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockButton(TempoIcons.Home, active = current == Screen.Home, contentDescription = "ホーム", onClick = onHome)
            DockButton(TempoIcons.Search, active = current == Screen.Search, contentDescription = "検索", onClick = onSearch)
            DockButton(TempoIcons.Bell, active = current == Screen.Notifications, contentDescription = "通知", onClick = onNotifications)
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
