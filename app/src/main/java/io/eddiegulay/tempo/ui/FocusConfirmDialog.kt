package io.eddiegulay.tempo.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * The deliberate gate into Focus mode. Entering rotates the launcher to a full-screen landscape
 * clock and hides the system bars, so we ask first rather than letting a stray long-press throw the
 * user into an immersive surface. Styled to match [BlockConfirmDialog].
 */
@Composable
fun FocusConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = "集中モード",
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            Text(
                text = "横向きの全画面時計に切り替わります。タップで秒を、長押しでポモドーロを切り替えられます。",
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkSoft, letterSpacing = 0.5.sp),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("続ける", style = TextStyle(fontFamily = Mincho, color = c.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("戻る", style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}
