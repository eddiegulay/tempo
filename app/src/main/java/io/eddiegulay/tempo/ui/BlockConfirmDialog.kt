package io.eddiegulay.tempo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.data.BlockadeRepository
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * The commitment gate for hiding an app. Blocking is irreversible for [BlockadeRepository.BLOCK_DAYS]
 * days and survives reinstalling, so we make the user acknowledge the outcome explicitly.
 *
 * When All-files access hasn't been granted the dialog first routes the user to grant it — the block
 * can't be made uninstall-proof without it, so confirmation stays disabled until then.
 */
@Composable
fun BlockConfirmDialog(
    appLabel: String,
    storageGranted: Boolean,
    onGrantStorage: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    var accepted by remember { mutableStateOf(false) }
    val days = BlockadeRepository.BLOCK_DAYS

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = "${days}日間ふうじる",
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "「$appLabel」を非表示にすると、${days}日間は元に戻せません。" +
                        "アプリを削除して入れ直しても、期間が終わるまで解除されません。",
                    style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkSoft, letterSpacing = 0.5.sp),
                )

                if (!storageGranted) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "確実にするため、まず「すべてのファイル」へのアクセスを許可してください。",
                        style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.accent, letterSpacing = 1.sp),
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.toggleable(
                            value = accepted,
                            role = Role.Checkbox,
                            onValueChange = { accepted = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(
                            checked = accepted,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.inkFaint),
                        )
                        Text(
                            text = "理解しました",
                            style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.ink),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!storageGranted) {
                TextButton(onClick = onGrantStorage) {
                    Text("アクセスを許可", style = TextStyle(fontFamily = Mincho, color = c.accent))
                }
            } else {
                TextButton(onClick = onConfirm, enabled = accepted) {
                    Text(
                        text = "${days}日間ふうじる",
                        style = TextStyle(fontFamily = Mincho, color = if (accepted) c.accent else c.inkFaint),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("やめる", style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}
