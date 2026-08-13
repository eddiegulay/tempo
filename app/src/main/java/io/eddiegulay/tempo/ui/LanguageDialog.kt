package io.eddiegulay.tempo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * Choose the UI language.
 *
 * **A picker, not a toggle**, and that is the whole design (`.planning/i18n/DECISIONS.md` §L6). The
 * theme control next to it in the Search header is a blind toggle, which is fine there: the result is
 * visible instantly and any mistake is undone by eye. Language is not reversible by eye. A user who
 * flips blindly into a script they cannot read has also lost the ability to find the control that
 * would put it back — so both destinations are named, in advance, and the choice is explicit.
 *
 * **The two rows are not translated, and must never be.** 日本語 is written in Japanese and English in
 * English, because the row exists to be read by the person who wants *that* language. A row saying
 * "Japanese" under an English UI tells a Japanese speaker nothing; it is addressed to the one user
 * who does not need it. This is the one place in the app where leaving a string untranslated is the
 * correct answer rather than an unfinished one.
 *
 * The current language carries the accent and a mark; the other row stays quiet. Nothing here
 * confirms — choosing *is* the action, and the dialog closes behind it.
 */
@Composable
fun LanguageDialog(
    current: Lang,
    onChoose: (Lang) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgSolid,
        text = {
            Column {
                LanguageRow(
                    // Endonyms, deliberately. See the KDoc above.
                    name = "日本語",
                    selected = current == Lang.Ja,
                    onClick = { onChoose(Lang.Ja) },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.hair),
                )
                LanguageRow(
                    name = "English",
                    selected = current == Lang.En,
                    onClick = { onChoose(Lang.En) },
                )
            }
        },
        // Same shape as ModeDialog: leaving is the only button, so it sits where a confirm would.
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = s.dialog.dismiss,
                    style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, color = c.inkFaint),
                )
            }
        },
    )
}

/**
 * One language and whether it is the active one.
 *
 * The check mark is stripped from the semantics tree because the row already announces its selected
 * state through [Role.RadioButton] — read aloud, a `✓` after "English, selected" is the same fact
 * twice. `onClickLabel` is the language's own name for the same reason [ModeDialog] uses the mode's:
 * "double tap to English" says what happens, where "activate" does not.
 */
@Composable
private fun LanguageRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalTempoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = name, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 17.sp,
                color = if (selected) c.accent else c.ink,
            ),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "✓",
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.accent),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
