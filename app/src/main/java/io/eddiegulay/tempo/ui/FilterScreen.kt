package io.eddiegulay.tempo.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.data.BlockadeRepository
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import kotlin.math.ceil

/**
 * The hidden-apps page (非表示アプリ): the full inventory with a per-app block toggle. Hiding an app is
 * a 10-day commitment — tapping a visible app raises the confirmation dialog; a blocked app shows its
 * remaining days and refuses to un-hide until the block elapses, after which a tap restores it.
 */
@Composable
fun FilterScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val blockade by viewModel.blockade.collectAsStateWithLifecycle()
    // Re-read the guarded clock whenever the blockade changes; day-level countdown needs no finer tick.
    val now = remember(blockade) { viewModel.blockadeNow() }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp)) {
            // Absent in English by design: the kana line is the *reading* of the title below it, and
            // a language with no second script for the same word has nothing to put here. See
            // `FilterStrings.kana`.
            s.filter.kana?.let { kana ->
                Text(
                    text = kana,
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 6.sp, color = c.inkFaint),
                )
            }
            Box(Modifier.padding(top = 12.dp)) {
                Text(
                    text = s.filter.title,
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, color = c.ink),
                )
            }
            Text(
                // The day count is read from the constant the dialogs read, not typed in again. It
                // used to be a literal `10` here, which is a drift waiting for someone to change
                // BLOCK_DAYS and not grep for the number.
                text = s.filter.subtitle(BlockadeRepository.BLOCK_DAYS),
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
    app: AppInfo,
    unlockAt: Long?,
    now: Long,
    onRequestBlock: () -> Unit,
    onUnblock: () -> Unit,
    onLocked: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

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
        unlockable -> s.filter.rowUnlockable
        else -> s.filter.rowRemaining(remainingLabel(remaining, s))
    }
    val dim = isBlocked && !unlockable

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The same row shape as Search's app list, because it is the same inventory seen through a
            // different lens — the two pages must feel like one under a finger.
            .pressable(TempoShapes.Row, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.alpha(if (dim) 0.4f else 1f)) {
            AppGlyph(app = app, color = c.inkSoft, size = 26.dp)
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

/**
 * Coarse remaining-time label: days while >= 1 day, else hours, floored at one hour.
 *
 * **The hours branch is not migrated, and that is an open escalation rather than an oversight.**
 * [io.eddiegulay.tempo.i18n.Formats] carries counters for 回 巡 種目 秒 分 日 月 件 番目 — there is no
 * hours counter, and the migration brief is explicit that a namespace must not grow a second one
 * privately. Nothing else substitutes: `fmt.duration` is right in English (`23h`) and wrong in
 * Japanese, where `durationKanji` has no hours at all and renders 23 hours as 千三百八十分, and
 * `fmt.minutes` says the same thing in the same wrong unit. Rewriting the branch to report days would
 * change what Japanese users see today, which this migration is not allowed to do.
 *
 * `Formats.hours(n)` exists for exactly this, and is the only correct way to say a number of hours in
 * either language. The sub-day branch is reached on the last day of a ten-day block.
 */
private fun remainingLabel(millis: Long, strings: Strings): String {
    val hours = millis / (60L * 60L * 1000L)
    return if (hours >= 24) strings.fmt.days(ceil(hours / 24.0).toInt()) else strings.fmt.hours(maxOf(hours, 1L).toInt())
}
