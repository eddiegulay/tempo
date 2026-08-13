package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * 安全のために — the one place the app says plainly that it is not medical advice.
 *
 * `01-shell.md` §B: "Four paragraphs and a heading, not a feature." Entered from `GYM.SETTINGS` and,
 * once, from `GYM.HOME`'s first-run line. Read and back; the tab bar is hidden at depth 3 by the
 * shell's one rule (`stack.size == 1`), so this page opts into nothing.
 *
 * **The four paragraphs are not here, because no spec contains them.** §B specifies this page's
 * *typography* completely — Mincho 15.sp, `lineHeight` 26.sp, `c.inkSoft`, `padding(horizontal = 28.dp)`,
 * led by 「痛みを感じたらやめる」 as a standalone 18.sp `c.accent` line — and supplies exactly one of its
 * strings: that leading line, which `01-shell.md` :533 and :844 both carry. `00-plan.md` §4.1 forbids
 * inventing copy, and safety prose is the *last* copy in this app that may be improvised: a paragraph
 * of my own about pain, medical clearance or when to stop training would be a claim the product has not
 * made, on the one page whose entire purpose is to be careful. So the page ships with its heading, its
 * sourced line and nothing under them — `GymShell.gymPagePlaceholderTitle`'s own reasoning, applied to
 * a body rather than to a page: **a heading with nothing beneath it claims nothing.** The gap is
 * reported; the four paragraphs drop straight into [SafetyBody] when they are written.
 *
 * **This page writes nothing at all, and that is §B's rule read exactly.** §B GYM.SAFETY's Data line is
 * "none, except `setSafetyNoteAcknowledged()` **when reached from the first-run note**", and
 * `GymHomeScreen`'s `SafetyFootnote` already writes the flag on both of its exits — 開く before it
 * navigates here, and the dismissal — so the acknowledgement travels with the note that was dismissed,
 * which is the mechanism §B specifies. The flag means *the first-run note has been dealt with*, not
 * *this page has been read*.
 *
 * **Rejected: writing it here on arrival whenever the stored flag is false.** It looks like a harmless
 * backstop and is not one — the page has no way to tell how it was entered, so a new user who opens
 * 鍛錬 → 設定 → 安全のために, having never trained, would permanently suppress the 痛みを感じたらやめる
 * line on `GYM.HOME` that they were meant to be shown before their first workout. Conditioning on the
 * stored flag does not save it: it skips the write only when the note was *already* acknowledged, which
 * is the one case where writing would have been harmless. A genuine backstop needs an entry-source
 * signal — `GymRoute.Safety` gaining a `fromFirstRun` flag set only by `SafetyFootnote` — and until §B
 * asks for one, reading the page from 設定 is not a consent and leaves the note standing.
 */
@Composable
fun GymSafetyScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        GymBackHeader(title = "安全のために", subtitle = null, onBack = { if (gym.stack.value.size > 1) gym.onBack() })
        SafetyBody()
    }
}

/**
 * The body: one sourced line, in §B's own type.
 *
 * Its own composable rather than four lines inside the page, so that adding the missing paragraphs is
 * an edit to one function with the geometry already correct — and so this file names, in one place,
 * the thing that is missing.
 */
@Composable
private fun SafetyBody() {
    val c = LocalTempoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(bottom = 32.dp),
    ) {
        Spacer(Modifier.height(26.dp))
        Text(
            text = "痛みを感じたらやめる",
            style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, lineHeight = 28.sp, color = c.accent),
        )
    }
}
