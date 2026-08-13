package io.eddiegulay.tempo.ui.gym

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.GymRoute
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.RestSlot
import io.eddiegulay.tempo.gym.SpeechAvailability
import io.eddiegulay.tempo.gym.WheelOption
import io.eddiegulay.tempo.gym.cue.isTouchExplorationEnabled
import io.eddiegulay.tempo.gym.label
import io.eddiegulay.tempo.gym.restOptions
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.TempoValueWheel
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import java.util.Locale

/*
 * GYM.SETTINGS — 設定. `01-shell.md` §B's second page.
 *
 * Everything this file decides about *words* is in `GymSettingsCopy.kt` and is JVM-tested. What is
 * left here is the one mechanism the page exists to get right, and it is worth stating before the
 * first composable:
 *
 * **Every row writes on the tap that made it, and a failed write reverts the row.** There is no 保存
 * button, no draft and no confirmation, so there is no dirty state and the page registers no
 * `BackHandler` of its own (§B's back behaviour). The price of that is that an optimistic row which
 * keeps a value the store refused is a *lie about the user's settings* — the settings-page form of the
 * `Loadable` doctrine — so [SettingsWriter] reverts to the stored value and says 保存できませんでした.
 * See its KDoc for why the overlay is a whole `GymPreferences` rather than one field.
 */

/**
 * 設定 — everything that changes how a workout behaves, so no preference has to be found mid-session.
 *
 * **There is no Loading state and that is a fact about the store, not an omission.** `GymPreferences`
 * has total defaults and `GymPreferencesStore.loadInitial()` seeds `GymViewModel.prefs` synchronously
 * before the first frame, so this page opens already correct; a spinner here would appear only to be
 * replaced by values that were already known (§B's states table). There is no Empty state either — a
 * settings page has none — and the Error state is not a page state at all but a line under one card.
 *
 * Reads `gym.prefs` (the **stored** truth) and writes through `gym.preferences`. It deliberately does
 * not read `EffectiveGymPreferences`: `DECISIONS.md` §Q2 makes 音声 default off and auto-enabled under
 * TalkBack *without writing the preference*, and a toggle showing the effective value would appear to
 * switch itself on. The override is explained beside the row instead — see `settingsRowStates`.
 */
@Composable
fun GymSettingsScreen(gym: GymViewModel, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val stored by gym.prefs.collectAsStateWithLifecycle()
    val session by gym.activeSession.collectAsStateWithLifecycle()

    val speech = rememberSpeechAvailability()
    val touchExploration = rememberTouchExploration()

    val writer = rememberSettingsWriter(gym, stored)
    val prefs = writer.shown

    // Which value row has its wheel unfolded, if any. One at a time: two open wheels on one card is
    // two ways to read the same 休息の初期値 heading.
    var openWheel by remember { mutableStateOf<SettingRow?>(null) }
    // §B edge case 3, scoped to this visit — see `settingsRowStates`.
    var tonesJustEnabled by remember { mutableStateOf(false) }

    // Back with a wheel open is consumed by the wheel, which has already committed every settle it saw
    // (§B's back behaviour: "a wheel is direct manipulation, and one that discards on Back is a trap").
    // Nothing else on this page registers a handler; the shell's is the fallback and already correct.
    BackHandler(enabled = openWheel != null) { openWheel = null }

    val rows = settingsRowStates(
        strings = s,
        prefs = prefs,
        speech = speech,
        sessionLive = session != null,
        tonesJustEnabled = tonesJustEnabled,
        touchExplorationEnabled = touchExploration,
    )

    Column(modifier.fillMaxSize()) {
        GymBackHeader(
            title = s.gymSettings.title,
            subtitle = s.gymSettings.subtitle,
            onBack = { if (gym.stack.value.size > 1) gym.onBack() },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Shallower than a tab-bar page's 88.dp: the bar is hidden at depth 2 (§B's tab bar).
                .padding(bottom = 32.dp),
        ) {
            SettingsCard(SettingSection.Cues, writer.failedSection) {
                ToggleRow(SettingRow.Haptics, prefs.haptics, rows) {
                    writer.write(SettingSection.Cues, prefs.copy(haptics = it)) { gym.preferences.setHaptics(it) }
                }
                RowDivider()
                ToggleRow(SettingRow.Tones, prefs.tones, rows) {
                    tonesJustEnabled = it
                    writer.write(
                        section = SettingSection.Cues,
                        next = prefs.copy(tones = it),
                        onFailure = { tonesJustEnabled = false },
                    ) { gym.preferences.setTones(it) }
                }
                RowDivider()
                ToggleRow(SettingRow.Speech, prefs.speech, rows) {
                    writer.write(SettingSection.Cues, prefs.copy(speech = it)) { gym.preferences.setSpeech(it) }
                }
            }

            SettingsCard(SettingSection.Progress, writer.failedSection) {
                ToggleRow(SettingRow.AutoAdvanceReps, prefs.autoAdvanceReps, rows) {
                    writer.write(SettingSection.Progress, prefs.copy(autoAdvanceReps = it)) {
                        gym.preferences.setAutoAdvanceReps(it)
                    }
                }
                RowDivider()
                WheelRow(
                    row = SettingRow.PrepareSeconds,
                    value = prefs.prepareSeconds,
                    options = prepareOptions(s),
                    states = rows,
                    open = openWheel == SettingRow.PrepareSeconds,
                    onOpen = { openWheel = if (openWheel == SettingRow.PrepareSeconds) null else SettingRow.PrepareSeconds },
                    onSelect = { seconds ->
                        writer.write(SettingSection.Progress, prefs.copy(prepareSeconds = seconds)) {
                            gym.preferences.setPrepareSeconds(seconds)
                        }
                    },
                )
            }

            SettingsCard(SettingSection.RestDefaults, writer.failedSection) {
                WheelRow(
                    row = SettingRow.StationRest,
                    value = prefs.defaultStationRest,
                    // §Q10 and the builder's own wheel, reused rather than restated: the value the user
                    // dials here is the value a new routine's 種目の間 row opens on, and two lists is
                    // how one of them ends up offering a step the other cannot show.
                    options = restOptions(RestSlot.BETWEEN_STATIONS, s),
                    states = rows,
                    open = openWheel == SettingRow.StationRest,
                    onOpen = { openWheel = if (openWheel == SettingRow.StationRest) null else SettingRow.StationRest },
                    onSelect = { seconds ->
                        writer.write(SettingSection.RestDefaults, prefs.copy(defaultStationRest = seconds)) {
                            gym.preferences.setDefaultStationRest(seconds)
                        }
                    },
                )
                RowDivider()
                WheelRow(
                    row = SettingRow.RoundRest,
                    value = prefs.defaultRoundRest,
                    options = restOptions(RestSlot.BETWEEN_ROUNDS, s),
                    states = rows,
                    open = openWheel == SettingRow.RoundRest,
                    onOpen = { openWheel = if (openWheel == SettingRow.RoundRest) null else SettingRow.RoundRest },
                    onSelect = { seconds ->
                        writer.write(SettingSection.RestDefaults, prefs.copy(defaultRoundRest = seconds)) {
                            gym.preferences.setDefaultRoundRest(seconds)
                        }
                    },
                )
            }

            // 「これから作る型に使われます」 — §B edge case 6's whole purpose. "Default rest" is the most
            // commonly misread setting in every logging app, and changing it must never rewrite an
            // existing routine's stored rests. A footnote, not a card row.
            Footnote(s.gymSettings.restDefaultsFootnote)

            SettingsCard(SettingSection.Display, writer.failedSection) {
                ToggleRow(SettingRow.KeepScreenOn, prefs.keepScreenOn, rows) {
                    writer.write(SettingSection.Display, prefs.copy(keepScreenOn = it)) {
                        gym.preferences.setKeepScreenOn(it)
                    }
                }
                RowDivider()
                // 単位 cycles on tap: two options do not deserve a sheet (§B's value-rows note).
                ValueRow(
                    row = SettingRow.Units,
                    value = prefs.units.label(s),
                    states = rows,
                    onClick = {
                        val next = nextUnits(prefs.units)
                        writer.write(SettingSection.Display, prefs.copy(units = next)) {
                            gym.preferences.setUnits(next)
                        }
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            SafetyRow(onClick = { gym.go(GymRoute.Safety) })
        }
    }
}

// ─── The header ─────────────────────────────────────────────────────────────────────────────────

/**
 * 「← 設定 / 鍛錬のふるまい」 — §B's header, over the hairline every gym page carries.
 *
 * **← rather than とじる**, which is `GYM.LIBRARY.DETAIL`'s word: §B's mock draws the glyph and sizes
 * it (48.dp, Mincho 20.sp, `c.inkSoft`), so the two pages differ because their specs differ. The
 * glyph's *spoken* label is the one string here no table supplies — a bare "←" would be announced as
 * punctuation or not at all — so it borrows とじる, which is this feature's own word for the header
 * action that pops a page. Reported as a gap rather than invented as prose.
 *
 * Shared with `GYM.SAFETY`, which §B gives the same idiom and no subtitle. `internal` rather than
 * copied: the two pages are one spec section apart and a second ← that drifted by two dp — or that
 * announced a different word — would be a difference nobody chose.
 */
@Composable
internal fun GymBackHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 22.dp, top = 24.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .pressable(TempoShapes.Glyph, role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = s.gymSettings.backAction },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "←",
                    style = TextStyle(fontFamily = Mincho, fontSize = 20.sp, color = c.inkSoft),
                )
            }
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(
                    text = title,
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 3.sp, color = c.ink),
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = subtitle,
                        style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 4.sp, color = c.inkFaint),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}

// ─── The cards ──────────────────────────────────────────────────────────────────────────────────

/**
 * One washi card under its section heading, with §B's `Error` line beneath it when a write in this
 * section failed.
 *
 * The line is per section rather than per page because §B places it "under the card" — a single line at
 * the foot of the page would leave the user hunting for which row went back, and the row that reverted
 * is inside this card.
 */
@Composable
private fun SettingsCard(
    section: SettingSection,
    failedSection: SettingSection?,
    content: @Composable () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Text(
            text = section.heading(s),
            modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 6.dp).semantics { heading() },
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(TempoShapes.Card)
                .background(c.card)
                .animateContentSize(tween(220, easing = LinearOutSlowInEasing)),
        ) {
            content()
        }
        if (failedSection == section) {
            Text(
                text = s.gymSettings.writeFailed,
                // Polite, and this is the one live region on the page. §B's rule bans them for *ticking*
                // values, because a per-second announcement is unusable and steals the TTS engine the
                // cues need; a row that just reverted under the user's thumb is the opposite case — it
                // is a silent change to something they asked for, and `QuitSheet`'s 記録できませんでした
                // sets the same precedent for the same reason.
                modifier = Modifier
                    .padding(start = 18.dp, top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.accent),
            )
        }
    }
}

/** The mock's 0.5.dp rule, inset 18.dp. Decoration, so TalkBack never stops on it. */
@Composable
private fun RowDivider() {
    val c = LocalTempoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(0.5.dp)
            .background(c.hair)
            .clearAndSetSemantics {},
    )
}

/**
 * 「これから作る型に使われます」 — Gothic 11.sp `c.inkFaint`, outside the card it explains.
 *
 * Its own focus stop, and that is the one place this page reads §B's accessibility line loosely.
 * "Footnotes merge into the preceding card's semantics" is honoured for the *row* sub-lines — each one
 * is announced with the row it belongs to and never on its own — but merging a card-level line into the
 * card would mean merging the card, and a 休息の初期値 card that is one node is a card whose two wheels
 * cannot be reached. A sentence a screen-reader user swipes past is a far smaller cost than two rows
 * they cannot open.
 */
@Composable
private fun Footnote(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        modifier = Modifier.padding(start = 40.dp, end = 22.dp, top = 8.dp),
        style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, lineHeight = 17.sp, color = c.inkFaint),
    )
}

// ─── The rows ───────────────────────────────────────────────────────────────────────────────────

/**
 * A toggle, which in this app is a **word**: 入 in `c.accent`, 切 in `c.inkFaint`, the whole 56.dp row
 * tappable and a 120ms colour cross-fade with no thumb to slide (§B's toggle control).
 *
 * **`clearAndSetSemantics`, because §B fixes the announced *order* and a merge cannot deliver it.** §B
 * pins the disabled row verbatim as 「音声、切、日本語の音声が入っていません」 — label, then state, then
 * reason. `semantics(mergeDescendants = true)` composes the node's name from its children in *visual*
 * order (音声, then the sub-line) and appends the state description after both, so the row announced
 * 「音声、日本語の音声が入っていません、切」: the same three pieces in the wrong order, with the reason
 * arriving before the state it is a reason for. [settingsRowDescription] was written for exactly this
 * shape and §B's string is what `GymSettingsCopyTest` pins it against; the row now uses it, as `ValueRow`
 * already did.
 *
 * `stateDescription` is kept alongside the description, deliberately: §B's accessibility line requires
 * the visible word and the state description to always agree, and a `Role.Switch` whose state is only a
 * clause inside its name is a switch that reads as off-state-less to anything that queries the node
 * rather than speaking it. Both come from [toggleWord], so they cannot drift.
 *
 * The visible 入/切 text keeps its own `clearAndSetSemantics {}`: under a cleared parent it is
 * unreachable anyway, and leaving it is one edit away from a duplicate if the parent's clear is ever
 * loosened.
 *
 * A disabled row is inert **and says why**: `disabled()` plus the reason, which is now inside the
 * announcement rather than beside it. A greyed control that cannot explain itself is the complaint
 * these specs repeat most.
 */
@Composable
private fun ToggleRow(
    row: SettingRow,
    on: Boolean,
    states: Map<SettingRow, RowState>,
    onToggle: (Boolean) -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val state = states[row] ?: RowState(enabled = true, subtitle = null)
    val word = toggleWord(s, on)
    val wordColour by animateColorAsState(
        targetValue = when {
            !state.enabled -> c.inkFaint
            on -> c.accent
            else -> c.inkFaint
        },
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "toggle-word",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            // Left as a click rather than converted to `toggleable`: the row's whole announcement is
            // hand-built below (`Role.Switch` plus a `stateDescription` that must be the same word the
            // row draws), and `toggleable` would supply a second, differently-worded state on top.
            .pressable(TempoShapes.Row, enabled = state.enabled) { onToggle(!on) }
            .clearAndSetSemantics {
                role = Role.Switch
                toggleableState = if (on) ToggleableState.On else ToggleableState.Off
                stateDescription = word
                contentDescription = settingsRowDescription(s, row.label(s), word, state.subtitle)
                if (!state.enabled) disabled()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label(s),
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 16.sp,
                    color = if (state.enabled) c.ink else c.inkFaint,
                ),
            )
            Text(
                text = word,
                modifier = Modifier.clearAndSetSemantics {},
                style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 2.sp, color = wordColour),
            )
        }
        state.subtitle?.let { RowSubtitle(it) }
    }
}

/** A row that shows a value and does something on tap — 単位, which cycles rather than opening a wheel. */
@Composable
private fun ValueRow(
    row: SettingRow,
    value: String,
    states: Map<SettingRow, RowState>,
    onClick: () -> Unit,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val state = states[row] ?: RowState(enabled = true, subtitle = null)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .pressable(TempoShapes.Row, enabled = state.enabled, onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = settingsRowDescription(s, row.label(s), value, state.subtitle)
                if (!state.enabled) disabled()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label(s),
                style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = TextStyle(fontFamily = Mincho, fontSize = 15.sp, color = c.inkSoft),
                )
                Text(
                    text = "→",
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, color = c.inkFaint),
                )
            }
        }
        state.subtitle?.let { RowSubtitle(it) }
    }
}

/**
 * A value row whose wheel unfolds beneath it, in place.
 *
 * **Never a dialog and never an overlay** — that is `EventComposeScreen`'s `PickerRow`, the app's only
 * existing wheel idiom, and it is what §B's "opens a `TempoValueWheel` sheet" means here: the card grows
 * and the rows underneath keep their place, so the value being dialled stays beside its own label. A
 * modal sheet would put a scrim over the two rest rows that are read *against each other*.
 *
 * **Each settle writes.** §B edge case 9 — "a wheel opened and dismissed without moving writes nothing"
 * — is honoured twice over: `TempoWheel`'s `WheelColumn` skips its first emission ("mounting is not
 * choosing"), and [onSelect] fires only when the settled value differs from the one on the row.
 *
 * **A stored value the wheel does not carry is still shown truthfully.** The rest wheels step by 5 and
 * 15, so a 37-second rest restored from an older build or a backup has no row of its own: the label
 * falls back to [settingsSecondsLabel] and reads 三十七秒, while the wheel opens on its first entry —
 * `TempoValueWheel`'s own documented behaviour, and the right one, because silently coercing the value
 * to the nearest row would change a setting the user only *looked* at. Nothing is written until they
 * spin.
 */
@Composable
private fun WheelRow(
    row: SettingRow,
    value: Int,
    options: List<WheelOption>,
    states: Map<SettingRow, RowState>,
    open: Boolean,
    onOpen: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val s = LocalStrings.current
    val state = states[row] ?: RowState(enabled = true, subtitle = null)
    val label = remember(options, value, s) {
        options.firstOrNull { it.value == value }?.label ?: settingsSecondsLabel(s, value)
    }

    Column(Modifier.fillMaxWidth()) {
        ValueRow(row = row, value = label, states = states, onClick = onOpen)
        if (open && state.enabled) {
            TempoValueWheel(
                values = remember(options) { options.map { it.value } },
                selected = value,
                onSelect = { picked -> if (picked != value) onSelect(picked) },
                // The wheel is a control of its own row: announcing the row's word on open is what §B's
                // "the wheel announces its title on open" asks for, and the settled value is re-read
                // from the row above, which has already changed.
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .semantics { contentDescription = row.label(s) },
                label = { picked -> options.firstOrNull { it.value == picked }?.label ?: settingsSecondsLabel(s, picked) },
            )
        }
    }
}

/** The sub-line: Gothic 12.sp `c.inkFaint`, drawn only when the row needs explaining. */
@Composable
private fun RowSubtitle(text: String) {
    val c = LocalTempoColors.current
    Text(
        text = text,
        style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, lineHeight = 18.sp, color = c.inkFaint),
    )
}

/** 安全のために — 64.dp, its own card, and the only row here that navigates rather than writing. */
@Composable
private fun SafetyRow(onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .background(c.card, TempoShapes.Card)
            .sizeIn(minHeight = 64.dp)
            .pressable(TempoShapes.Card, onClick = onClick)
            .clearAndSetSemantics { role = Role.Button; contentDescription = s.gymSettings.safetyTitle }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = s.gymSettings.safetyTitle,
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
        )
        Text(
            text = "→",
            style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, color = c.inkFaint),
        )
    }
}

// ─── Writing, and reverting ─────────────────────────────────────────────────────────────────────

/**
 * The optimistic overlay and the revert, which is the one piece of state this page must not get wrong.
 *
 * §B's `Writing` state is optimistic on purpose — a single-key DataStore edit is sub-millisecond and a
 * spinner would flash — and its `Error` state is the other half of that bargain: **the row reverts to
 * its stored value and says 保存できませんでした. A row is never left showing a value that was not
 * persisted.** An optimistic toggle that silently keeps a refused value is a settings page lying about
 * settings, which the user cannot detect and cannot correct.
 *
 * **[pending] is a whole `GymPreferences`, not one field.** Two reasons, and the second is the one that
 * decided it. A per-field map needs a key type and a cast per row, so the compiler stops checking that
 * 支度 holds an `Int`; and the reversion rule is *"show what is stored"*, which a whole-snapshot overlay
 * expresses by simply being dropped. The cost is disclosed: a failure reverts every row whose write has
 * not yet come back through the flow, not only the failed one. That errs toward the stored truth, which
 * is the only direction this page is allowed to err in.
 *
 * The overlay clears itself when the store catches up — `stored == pending` — rather than on the write
 * returning, because returning is not the same as being observed: dropping it a frame early would flash
 * the old value back before `GymPreferencesStore.preferences` emitted the new one.
 *
 * **[submit] is `GymViewModel.writePreference`, and that is not a detail.** The suspend body must run on
 * a scope that outlives this page: a `rememberCoroutineScope` is cancelled the instant the page leaves
 * composition, so a tap followed by Back, by 安全のために or by a HOME press would cancel the
 * `DataStore.edit` it started — leaving the store on the old value with nobody left on screen to revert
 * the row or say 保存できませんでした, which is the exact failure §B's `Error` state exists to prevent.
 * `BuilderScreen`'s save states the same rule for the same reason. The overlay and [failedSection] stay
 * here because they are what the *page* draws; only the write itself has to leave.
 *
 * @param submit takes the write and the revert, in that order, and runs the write off composition.
 *   `writePreference` re-throws `CancellationException` rather than reporting it, so the shell being
 *   torn down mid-write never raises 保存できませんでした on a page that no longer exists.
 */
private class SettingsWriter(
    private val stored: () -> GymPreferences,
    private val submit: (suspend () -> Unit, () -> Unit) -> Unit,
) {
    var pending by mutableStateOf<GymPreferences?>(null)
        private set

    /** Which card owes §B's 保存できませんでした line, or null. */
    var failedSection by mutableStateOf<SettingSection?>(null)
        private set

    /** What the rows draw: the optimistic value if one is in flight, otherwise the stored truth. */
    val shown: GymPreferences get() = pending ?: stored()

    /** Drops the overlay once the store has actually emitted the value it was standing in for. */
    fun reconcile() {
        if (pending == stored()) pending = null
    }

    fun write(
        section: SettingSection,
        next: GymPreferences,
        onFailure: () -> Unit = {},
        body: suspend () -> Unit,
    ) {
        pending = next
        failedSection = null
        // The revert is handed to the ViewModel with the body rather than run here, because the body
        // no longer runs here: `writePreference` owns the catch (any throw but a cancellation) and
        // calls this back on the main dispatcher, so touching this page's state from it is safe.
        submit(body) {
            pending = null
            failedSection = section
            onFailure()
        }
    }
}

/**
 * One [SettingsWriter] for the life of the page, reading the latest stored preferences through a
 * getter.
 *
 * The getter is the point: the writer holds the in-flight overlay, so keying `remember` on `stored`
 * would build a fresh one — with `pending` null — on the very emission the overlay exists to bridge,
 * and the row would flash the old value back mid-write.
 *
 * **There is deliberately no `rememberCoroutineScope` here.** Every write goes to
 * [GymViewModel.writePreference] and runs on `viewModelScope`; see [SettingsWriter]'s KDoc for the
 * failure that rule exists to prevent. A composition scope on this page would be a scope that dies
 * between the tap and the transaction.
 */
@Composable
private fun rememberSettingsWriter(gym: GymViewModel, stored: GymPreferences): SettingsWriter {
    val latest = rememberUpdatedState(stored)
    val writer = remember(gym) { SettingsWriter({ latest.value }, gym::writePreference) }
    LaunchedEffect(stored) { writer.reconcile() }
    return writer
}

// ─── Probing the platform ───────────────────────────────────────────────────────────────────────

/**
 * Whether a voice exists for the language the app is speaking — §B's `speechAvailability()`, as a
 * composable so it can be re-probed.
 *
 * **Null means "not answered yet" and is not a negative answer.** That distinction is the whole reason
 * this is not `GymSpeech.availability`: that field starts at `NoEngine` and its `onAvailabilityChanged`
 * fires only when the value *changes*, so a device that genuinely has no engine never signals, and a
 * device that has one shows 読み上げ機能がありません for the first beat of every visit. Loading is not
 * empty, on this page as on every other (`00-plan.md` §4.1 rule 1). `TextToSpeech`'s init listener
 * always fires, which is what makes the tri-state expressible here and not there.
 *
 * **Re-probed on `ON_RESUME`** — §B edge case 4: a voice uninstalled while the user was in the system's
 * TTS settings must be noticed on the way back, and one reinstalled must restore the row. The stored
 * preference is never touched either way, so speech returns without the user re-finding the switch.
 *
 * The engine is shut down on `ON_PAUSE`. A `TextToSpeech` left un-shut-down keeps a binding to another
 * process alive indefinitely, and this one exists only to answer a question.
 */
@Composable
private fun rememberSpeechAvailability(): SpeechAvailability? {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val lang = LocalStrings.current.lang
    var availability by remember { mutableStateOf<SpeechAvailability?>(null) }

    // Keyed on the language too: switching it makes the previous answer stale, and a page that kept it
    // would grey 音声 for a voice the device has, or offer one it does not.
    DisposableEffect(owner, context, lang) {
        val main = Handler(Looper.getMainLooper())
        var engine: TextToSpeech? = null

        fun release() {
            runCatching { engine?.shutdown() }
            engine = null
        }

        fun probe() {
            if (engine != null) return
            availability = null
            var created: TextToSpeech? = null
            created = TextToSpeech(context) { status ->
                // The callback can arrive on a binder thread, and `created` is assigned on this one.
                // Hopping to the main thread orders both, exactly as `GymSpeech` does.
                main.post { availability = probeResult(status, created, lang) }
            }
            engine = created
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> probe()
                Lifecycle.Event.ON_PAUSE -> release()
                else -> Unit
            }
        }
        // `addObserver` replays the events needed to bring a new observer up to the current state, so a
        // page composed while already resumed probes here rather than waiting for the next resume.
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            release()
        }
    }
    return availability
}

/**
 * The three answers `TextToSpeech` can give, mapped as `GymSpeech.onEngineReady` maps them — including
 * the `runCatching`, because `setLanguage` throws on some engines rather than returning a code, and an
 * engine that throws is one that cannot speak.
 *
 * **It asks about [lang], not about Japanese.** The row this gates says a voice is missing, and until
 * the app spoke two languages the two questions were the same one. They are not any more: probing for
 * Japanese under an English UI would grey 音声 on a device with a perfectly good English voice, and the
 * sub-line would report an absence nobody asked about.
 *
 * **This is not the only probe.** `gym/cue/GymSpeech` runs its own for the player, and the two must
 * answer the same question or this page advertises a capability the cue engine does not have. Both go
 * through `ttsLocale(lang)` for exactly that reason — note it resolves to a bare language rather than
 * a country-qualified tag, so an en-GB voice counts as an English voice.
 */
private fun probeResult(status: Int, engine: TextToSpeech?, lang: Lang): SpeechAvailability {
    if (status != TextToSpeech.SUCCESS || engine == null) return SpeechAvailability.NoEngine
    val result = runCatching { engine.setLanguage(Locale.forLanguageTag(lang.tag)) }
        .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
    return when (result) {
        TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> SpeechAvailability.NoVoiceForLanguage
        else -> SpeechAvailability.Available
    }
}

/**
 * Is TalkBack driving the screen? Read for the 音声 sub-line only (`DECISIONS.md` §Q2).
 *
 * Re-read on `ON_RESUME` rather than observed, matching `gym.cue.isTouchExplorationEnabled`'s own
 * contract: the player reads it once at session start, and a settings page that disagreed with the
 * player about whether speech is in force would be worse than one that is a resume behind.
 */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(false) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = isTouchExplorationEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}
