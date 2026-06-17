package io.eddiegulay.tempo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import kotlin.math.ceil

/**
 * The hidden-apps page (非表示アプリ): the full inventory with a per-app block toggle. Hiding an app is
 * a 10-day commitment — tapping a visible app raises the confirmation dialog; a blocked app shows its
 * remaining days and refuses to un-hide until the block elapses, after which a tap restores it.
 */
@Composable
fun FilterScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current

    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val blockade by viewModel.blockade.collectAsStateWithLifecycle()
    // Re-read the guarded clock whenever the blockade changes; day-level countdown needs no finer tick.
    val now = remember(blockade) { viewModel.blockadeNow() }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp)) {
            Text(
                text = "ひひょうじ",
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 6.sp, color = c.inkFaint),
            )
            Box(Modifier.padding(top = 12.dp)) {
                Text(
                    text = "非表示アプリ",
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, color = c.ink),
                )
            }
            Text(
                text = "非表示にすると10日間は解除できません",
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            items(apps, key = { it.key }) { app ->
                FilterRow(
                    viewModel = viewModel,
                    app = app,
                    unlockAt = blockade[app.packageName],
                    now = now,
                    onRequestBlock = { viewModel.requestBlock(app) },
                    onUnblock = { viewModel.unblockApp(app.packageName) },
                    onLocked = { viewModel.showLocked(app) },
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    viewModel: LauncherViewModel,
    app: AppInfo,
    unlockAt: Long?,
    now: Long,
    onRequestBlock: () -> Unit,
    onUnblock: () -> Unit,
    onLocked: () -> Unit,
) {
    val c = LocalTempoColors.current

    val icon by produceState<ImageBitmap?>(initialValue = viewModel.peekIcon(app), app.key) {
        if (value == null) value = viewModel.loadIcon(app)
    }

    val isBlocked = unlockAt != null
    val remaining = if (unlockAt != null) (unlockAt - now).coerceAtLeast(0L) else 0L
    val unlockable = isBlocked && remaining == 0L

    // Visible -> request a block; still locked -> show the countdown popup; elapsed -> restore.
    val onClick: () -> Unit = when {
        !isBlocked -> onRequestBlock
        unlockable -> onUnblock
        else -> onLocked
    }
    val subtitle = when {
        !isBlocked -> null
        unlockable -> "解除できます"
        else -> "あと${remainingLabel(remaining)}"
    }
    val dim = isBlocked && !unlockable

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val iconShape = RoundedCornerShape(7.dp)
        Box(Modifier.alpha(if (dim) 0.4f else 1f)) {
            if (icon != null) {
                Image(
                    bitmap = icon!!,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp).clip(iconShape).border(1.dp, c.hair, iconShape),
                )
            } else {
                Box(Modifier.size(26.dp).clip(iconShape).background(c.card).border(1.dp, c.hair, iconShape))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = app.label,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
                modifier = Modifier.alpha(if (dim) 0.4f else 1f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = Gothic,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = if (unlockable) c.inkFaint else c.accent,
                    ),
                )
            }
        }
        LineIcon(
            paths = if (isBlocked) TempoIcons.EyeOff else TempoIcons.Eye,
            color = if (isBlocked && !unlockable) c.accent else c.inkFaint,
            size = 22.dp,
        )
    }
}

/** Coarse remaining-time label: days while >= 1 day, else hours, else "1時間". */
private fun remainingLabel(millis: Long): String {
    val hours = millis / (60L * 60L * 1000L)
    return if (hours >= 24) "${ceil(hours / 24.0).toInt()}日" else "${maxOf(hours, 1L)}時間"
}
