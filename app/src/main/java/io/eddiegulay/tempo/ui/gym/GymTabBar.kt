package io.eddiegulay.tempo.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.gym.GymTab
import io.eddiegulay.tempo.gym.label
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * 鍛錬 / 型 / 記録, seated across the bottom of the gym shell.
 *
 * **Every choice here is an inversion of the launcher's dock, and that is the whole design.** The
 * dock is a floating `RoundedCornerShape(percent = 50)` pill of three line glyphs on `c.card` with a
 * 0.5.dp accent border, frosted with `wetPaper` over sub-screens. This bar is seated and full-width
 * rather than floating, words rather than glyphs, carries a top hairline, and is never frosted. One
 * glance has to tell the user which of the two modes they are in, and a bar that merely *differed*
 * would read as the same chrome drawn slightly wrong (`00-plan.md` §3.2, `01-shell.md` §A.4).
 *
 * *Rejected* — three new `TempoIcons` line-paths. There is no existing glyph for training, forms or
 * records, and drawing three is more invention than this needs; 鍛錬 / 型 / 記録 are the feature's own
 * vocabulary and reading them is the point.
 *
 * *Rejected* — a floating pill with a different fill. A pill says "chrome hovering over a page". A
 * seated bar with a hairline says "this window has a different frame", which is the true statement.
 *
 * The background sits on the outer column and the labels take `navigationBarsPadding()` inside it, so
 * the fill bleeds under the gesture bar while nothing readable does (`01-shell.md` §A.7).
 *
 * @param current the tab owning the stack's **root**, from `currentTab`. Null is drawable — no tab
 *   lit — but is not a state the shell can reach, since the stack's first entry is always a root.
 */
@Composable
fun GymTabBar(current: GymTab?, onSelect: (GymTab) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    Column(modifier.fillMaxWidth().background(c.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
        Row(
            // `selectableGroup()` is what makes the three tabs a *collection* rather than three
            // unrelated selectable nodes. Compose turns it into the node's `CollectionInfo`, which is
            // where TalkBack gets the 3の1 fragment of §B GYM.HOME's specified announcement
            // "鍛錬、タブ、3の1、選択中" — without it a blind user hears the tab and its state but not
            // how many tabs exist or where they are among them. Material3's own `TabRow` does exactly
            // this on its container; it draws no chrome and adds no dependency.
            modifier = Modifier.fillMaxWidth().selectableGroup().navigationBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GymTab.entries.forEach { tab ->
                GymTabItem(
                    tab = tab,
                    selected = tab == current,
                    onSelect = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One tab: a 4.dp dot 7.dp above its word, both in `c.accent` when active.
 *
 * The dot's space is held by a transparent placeholder when inactive, so selecting a tab moves
 * nothing but colour — a label that jumps 11.dp on every tap is the kind of motion that makes a
 * seated bar feel like a toy.
 *
 * `Role.Tab` plus `selected` is this node's entire accessibility contract, and the container's
 * `selectableGroup()` supplies the rest: TalkBack composes "鍛錬、タブ、3の1、選択中" (§B GYM.HOME) from
 * the word, the collection position and the state, with no hand-written `contentDescription` — one
 * would only be a second copy of the label to keep in sync. The item is the click target at its full
 * 64.dp height.
 */
@Composable
private fun GymTabItem(
    tab: GymTab,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(c.accent))
        } else {
            // Reserves the dot's height without drawing it; stripped of semantics because "dot" is
            // noise on a node that already announces itself as a selected tab.
            Spacer(Modifier.size(4.dp).clearAndSetSemantics { })
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = tab.label(s),
            // **Bounded, which it was not.** Three words share the bar in `weight(1f)` thirds, and
            // 鍛錬 / 型 / 記録 are two, one and two glyphs — so nothing could overflow and nothing
            // needed to say what happens if it did. Train / Routines / Records are five to eight
            // characters, and at 200% font scale the tracking scales with them; unbounded, the middle
            // tab would paint over its neighbours rather than clip.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 13.sp,
                // 3.sp of tracking is right for two CJK glyphs and wrong for eight Latin ones: it is
                // ~8% of the Japanese word's width and ~25% of the English one's.
                letterSpacing = if (s.lang == Lang.Ja) 3.sp else 1.sp,
                color = if (selected) c.accent else c.inkFaint,
            ),
        )
    }
}
