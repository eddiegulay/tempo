package io.eddiegulay.tempo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.data.BlockadeRepository
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.theme.InkPressIndication
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes

/**
 * The commitment gate for hiding an app. Blocking is irreversible for [BlockadeRepository.BLOCK_DAYS]
 * days and best-effort survives reinstalling (via Android Auto Backup), so we make the user
 * acknowledge the outcome explicitly with a checkbox before confirmation is enabled.
 */
@Composable
fun BlockConfirmDialog(
    appLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    var accepted by remember { mutableStateOf(false) }
    val days = BlockadeRepository.BLOCK_DAYS

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        title = {
            Text(
                text = s.block.confirmHeading(days),
                style = TextStyle(fontFamily = Mincho, fontSize = 22.sp, color = c.ink),
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = s.block.confirmBody(appLabel, days),
                    style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkSoft, letterSpacing = 0.5.sp),
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    // `toggleable` is not wrapped by `pressable` — a checkbox row is a two-state
                    // control and must keep announcing itself as one — so the indication is attached
                    // by hand with the shape the clip uses. Same row corner as everything else that
                    // spans a dialog's width; the acknowledgement is the hinge this whole gate turns
                    // on, and it should feel as considered under a finger as it reads.
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TempoShapes.Row)
                        .toggleable(
                            value = accepted,
                            interactionSource = null,
                            indication = InkPressIndication(TempoShapes.Row),
                            role = Role.Checkbox,
                            onValueChange = { accepted = it },
                        )
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.inkFaint),
                    )
                    Text(
                        text = s.block.confirmAcknowledge,
                        style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.ink),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = accepted) {
                Text(
                    text = s.block.confirmHeading(days),
                    style = TextStyle(fontFamily = Mincho, color = if (accepted) c.accent else c.inkFaint),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.block.confirmCancel, style = TextStyle(fontFamily = Mincho, color = c.inkFaint))
            }
        },
    )
}
