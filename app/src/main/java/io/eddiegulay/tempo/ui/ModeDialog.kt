package io.eddiegulay.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.LauncherMode
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * The one door out of the launcher: 集中 (the landscape clock) or 鍛錬 (the gym).
 *
 * It stays a gate rather than becoming a menu you can browse. Both modes take the whole window and
 * change what the hardware does — orientation and system bars for 集中, a live timer for 鍛錬 — so a
 * stray long-press must not land you inside either one.
 *
 * Two rows, not two buttons: a confirm/dismiss pair can only ask about one destination, and adding a
 * second confirm button would have made the dialog argue with itself about which is the default.
 * Neither is. The rows carry a one-line gloss because 集中 and 鍛錬 name feelings, not screens, and the
 * user meets both here for the first time. **Rejected:** a dock tab or a Home glyph for 鍛錬 — the gym
 * is a mode, and modes are entered deliberately or they are not modes.
 */
@Composable
fun ModeDialog(
    onChoose: (LauncherMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        text = {
            Column {
                ModeRow(
                    title = "集中",
                    subtitle = "時計だけの画面",
                    onClick = { onChoose(LauncherMode.Focus) },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.hair),
                )
                ModeRow(
                    title = "鍛錬",
                    subtitle = "体を動かす",
                    onClick = { onChoose(LauncherMode.Gym) },
                )
            }
        },
        // Leaving is the only button, so it sits where a confirm would: bottom-right, and quiet.
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "やめる",
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, color = c.inkFaint),
                )
            }
        },
    )
}

/**
 * One mode, its gloss, and the arrow that says this row leads somewhere.
 *
 * The arrow is stripped from the semantics tree: read aloud it is a direction, not information, and
 * the row already announces itself as a button.
 */
@Composable
private fun ModeRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = LocalTempoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // clickable merges the pair into one node: a title and its gloss announced separately read
            // as two orphan fragments. The label is the mode's own name, so the action is spoken as
            // "double tap to 集中" rather than "activate".
            .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, color = c.ink),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
            )
        }
        Text(
            text = "→",
            style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkFaint),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
