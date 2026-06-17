package io.eddiegulay.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * The bottom dock: Home / Search / Notifications, the active tab in vermillion accent, plus an
 * always-faint sun/moon theme toggle. A hairline tops the bar and a slim indicator pill sits below,
 * matching the prototype's dock chrome.
 */
@Composable
fun Dock(
    current: Screen,
    isDark: Boolean,
    isDefaultLauncher: Boolean,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onToggleTheme: () -> Unit,
    onRequestDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, start = 30.dp, end = 30.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockButton(TempoIcons.Home, active = current == Screen.Home, contentDescription = "ホーム", onClick = onHome)
            DockButton(TempoIcons.Search, active = current == Screen.Search, contentDescription = "検索", onClick = onSearch)
            DockButton(TempoIcons.Bell, active = current == Screen.Notifications, contentDescription = "通知", onClick = onNotifications)
            // Theme toggle stays faint regardless of screen, like the source.
            DockButton(
                paths = if (isDark) TempoIcons.Sun else TempoIcons.Moon,
                active = false,
                contentDescription = if (isDark) "ライトテーマに切り替え" else "ダークテーマに切り替え",
                onClick = onToggleTheme,
            )
        }

        // Home-indicator pill. When Tempo isn't the default home app it tints to the accent as a
        // subtle "hold me" cue; long-pressing it requests the default-home role.
        Box(
            modifier = Modifier
                .padding(top = 14.dp, bottom = 9.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 118.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isDefaultLauncher) c.inkFaint else c.accent)
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
                },
        )
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
