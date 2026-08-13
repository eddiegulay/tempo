package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.SpeechAvailability
import io.eddiegulay.tempo.gym.Units
import io.eddiegulay.tempo.gym.WheelOption
import io.eddiegulay.tempo.gym.clampPrepareSeconds
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.stringsFor

/*
 * Everything `GYM.SETTINGS` decides, decided without a composable — `01-shell.md` §B's "Pure logic to
 * unit test" list, minus the three entries that already shipped elsewhere and must not be restated
 * here:
 *
 * - `clampPrepareSeconds` / `clampStationRest` / `clampRoundRest` are `gym/GymPreferencesRepository.kt`'s
 *   and are called, not copied. A bound spelled at two call sites is the disagreement nobody notices
 *   until the wheel offers a value the store then clamps away, so the row shows something that was
 *   never written.
 * - `effectiveCues` is `gym/cue/CueSettings.kt`'s `armCues`. §B lists both, and `CueSettings`' own KDoc
 *   already argues they are one question asked twice; this page reads the *stored* flags and the
 *   availability, and never asks the hardware anything.
 * - `secondsLabelJa` is **deliberately not defined here**. `DECISIONS.md` §Q10 assigns that name to the
 *   formatter for a duration the app *measured* (`durationKanji`, 一分三十秒). Every duration on this
 *   page is one the user *chose*, so it renders bare seconds — see [settingsSecondsLabel].
 *
 * The reason any of this is a file rather than a `when` inside the page is `00-plan.md` §4.1: a
 * decision made inside a composable can only be checked by launching an emulator, and the decisions
 * below include which row is disabled and why — which is exactly the class of thing that ships wrong.
 *
 * **Every word this page draws now comes from `strings.gymSettings`, and every function that needs one
 * takes the table as a parameter** (`.planning/i18n/DECISIONS.md` §L4). Not a global and not a
 * composition local: this file is covered by plain JUnit with no Compose and no `Context`, and passing
 * the table is what keeps it there. The shape below is the one the migration is aiming at everywhere —
 * the `when` expressions *select* a member of the table, they never build a sentence out of one.
 */

// ─── The rows ───────────────────────────────────────────────────────────────────────────────────

/**
 * The four cards of §B's mock, in the order it stacks them.
 *
 * A section is the unit a failed write reports against, not merely a heading: §B's `Error` state puts
 * 保存できませんでした "under the card", so the page has to know which card a row belongs to. That is why
 * this is an enum on [SettingRow] rather than four hand-grouped lists in the composable.
 *
 * 安全のために has no section — it is a lone 64.dp card below the four, and it writes nothing.
 *
 * **The heading is not a constructor argument** (§L3's rule for all thirteen label-carrying enums): an
 * argument is fixed at class-init and cannot be re-resolved when the language changes. See [heading].
 */
enum class SettingSection {
    Cues,
    Progress,
    RestDefaults,
    Display,
}

/** The word §B's mock puts over this card. */
fun SettingSection.heading(strings: Strings): String = when (this) {
    SettingSection.Cues -> strings.gymSettings.sectionCues
    SettingSection.Progress -> strings.gymSettings.sectionProgress
    SettingSection.RestDefaults -> strings.gymSettings.sectionRestDefaults
    SettingSection.Display -> strings.gymSettings.sectionDisplay
}

/**
 * Every switchable or dialable row.
 *
 * Declaration order is display order, which is also the focus order §B's accessibility paragraph asks
 * for ("focus order is visual order"). [section] stays a constructor argument because it is structure
 * rather than copy; the word is [label].
 */
enum class SettingRow(val section: SettingSection) {
    Haptics(SettingSection.Cues),
    Tones(SettingSection.Cues),
    Speech(SettingSection.Cues),
    AutoAdvanceReps(SettingSection.Progress),
    PrepareSeconds(SettingSection.Progress),
    StationRest(SettingSection.RestDefaults),
    RoundRest(SettingSection.RestDefaults),
    KeepScreenOn(SettingSection.Display),
    Units(SettingSection.Display),
}

/** The word §B's mock puts on this row. */
fun SettingRow.label(strings: Strings): String = when (this) {
    SettingRow.Haptics -> strings.gymSettings.rowHaptics
    SettingRow.Tones -> strings.gymSettings.rowTones
    SettingRow.Speech -> strings.gymSettings.rowSpeech
    SettingRow.AutoAdvanceReps -> strings.gymSettings.rowAutoAdvanceReps
    SettingRow.PrepareSeconds -> strings.gymSettings.rowPrepareSeconds
    SettingRow.StationRest -> strings.gymSettings.rowStationRest
    SettingRow.RoundRest -> strings.gymSettings.rowRoundRest
    SettingRow.KeepScreenOn -> strings.gymSettings.rowKeepScreenOn
    SettingRow.Units -> strings.gymSettings.rowUnits
}

/**
 * Whether a row can be touched, and the line under it that explains itself.
 *
 * [subtitle] is null for the rows §B's mock leaves bare — it draws the sub-line "only when the row
 * needs explaining", and a permanent explanatory line under every switch is the chrome that teaches a
 * user to stop reading the ones that mean something.
 */
data class RowState(val enabled: Boolean, val subtitle: String?)

/**
 * Every row's enablement and sub-line, from the three facts that decide them.
 *
 * **[speech] is nullable and that is the `Loadable` doctrine in miniature.** A TTS engine answers
 * asynchronously, so "not probed yet" is a third state alongside Available and unavailable — and
 * collapsing it into `NoEngine` would open this page with 音声 greyed and 読み上げ機能がありません under
 * it on every device, correcting itself a beat later. An unknown answer is not a negative answer: the
 * row stays live and says only what §B's mock says. See `rememberSpeechAvailability`.
 *
 * **The 音声 row reflects the *stored* flag, never the effective one** (`DECISIONS.md` §Q2), which is
 * why [touchExplorationEnabled] changes only the sub-line and never [RowState.enabled] — nor, at the
 * call site, the 入/切 word. A toggle showing the effective value would appear to switch itself on when
 * TalkBack came up, and switching it off again would do nothing the user could see.
 *
 * @param strings the active table. A parameter rather than a lookup, so this stays a JVM test (§L4).
 * @param sessionLive `GymViewModel.activeSession != null`. §B's `SessionLive` state footnotes 支度 and
 *   the two rests; 合図 and 表示 deliberately take effect mid-session, which is the main reason this
 *   page is reachable at all.
 * @param tonesJustEnabled the 音 row was switched **on** during this visit — §B edge case 3's "say so
 *   in the sub-line on first enable only". Scoped to the visit rather than to the lifetime of the
 *   install, because a lifetime flag would need a tenth preference key and this unit owns no store.
 *   Disclosed rather than slipped in: the line then reappears for a user who toggles 音 off and on
 *   again, which is a repetition of something true rather than a claim of something false.
 * @param touchExplorationEnabled `gym.cue.isTouchExplorationEnabled`, read for the sub-line only.
 */
fun settingsRowStates(
    strings: Strings,
    prefs: GymPreferences,
    speech: SpeechAvailability?,
    sessionLive: Boolean,
    tonesJustEnabled: Boolean = false,
    touchExplorationEnabled: Boolean = false,
): Map<SettingRow, RowState> = buildMap {
    val copy = strings.gymSettings
    put(SettingRow.Haptics, RowState(enabled = true, subtitle = null))
    put(
        SettingRow.Tones,
        RowState(enabled = true, subtitle = if (tonesJustEnabled) copy.subSilentMode else null),
    )
    put(SettingRow.Speech, speechRowState(strings, prefs, speech, touchExplorationEnabled))
    put(SettingRow.AutoAdvanceReps, RowState(enabled = true, subtitle = copy.subAutoAdvance))
    put(SettingRow.PrepareSeconds, RowState(enabled = true, subtitle = sessionNotice(strings, sessionLive)))
    put(SettingRow.StationRest, RowState(enabled = true, subtitle = sessionNotice(strings, sessionLive)))
    put(SettingRow.RoundRest, RowState(enabled = true, subtitle = sessionNotice(strings, sessionLive)))
    put(SettingRow.KeepScreenOn, RowState(enabled = true, subtitle = copy.subKeepScreenOn))
    put(SettingRow.Units, RowState(enabled = true, subtitle = null))
}

private fun sessionNotice(strings: Strings, sessionLive: Boolean): String? =
    if (sessionLive) strings.gymSettings.subNotThisSession else null

/**
 * 音声, which is the only row with four different things to say.
 *
 * **A missing voice disables the row but never clears the stored flag** (§B edge case 4): reinstalling
 * a voice must restore speech without the user having to re-find the switch, so the preference survives
 * the hardware being unable to honour it. `armCues` disarms the channel meanwhile and the player falls
 * back to tones silently.
 *
 * **The TalkBack-override sub-line is `subSpeech`, and that is a documented gap, not a choice.**
 * `DECISIONS.md` §Q2 requires "a subtitle explaining the override when touch exploration is on" and
 * **no spec table contains that sentence** — not §B's mock, not §B's states table, not `03-player.md`
 * §D.6. `00-plan.md` §4.1 forbids inventing copy, so the row keeps the one sub-line the mock does give
 * it and the gap is reported rather than filled with prose of my own. Translating the page does not
 * close it: an English sentence in that position would be an invention in a second language rather
 * than a translation of anything. When the string lands, it belongs on the [touchExplorationEnabled]
 * branch below and nowhere else.
 *
 * **`NoVoiceForLanguage`'s sub-line no longer names a language.** The probe asks for a voice in whatever
 * language the app is speaking, so the English says only that no voice is installed; the Japanese is
 * transcribed as it ships and still names 日本語, which is what a Japanese UI probing for Japanese
 * correctly reports. See `GymSettingsStrings.subNoVoice`.
 */
private fun speechRowState(
    strings: Strings,
    prefs: GymPreferences,
    speech: SpeechAvailability?,
    touchExplorationEnabled: Boolean,
): RowState = when (speech) {
    SpeechAvailability.NoVoiceForLanguage ->
        RowState(enabled = false, subtitle = strings.gymSettings.subNoVoice)
    SpeechAvailability.NoEngine ->
        RowState(enabled = false, subtitle = strings.gymSettings.subNoTtsEngine)
    // Available, or not yet probed. Both are live rows; see the KDoc on `settingsRowStates`.
    SpeechAvailability.Available, null -> RowState(
        enabled = true,
        // Shown while the row needs explaining: before it has been switched on at all, and while
        // TalkBack is speaking for it (`DECISIONS.md` §Q2 — the string gap above).
        subtitle = if (!prefs.speech || touchExplorationEnabled) strings.gymSettings.subSpeech else null,
    )
}

// ─── The toggle, which is a word ────────────────────────────────────────────────────────────────

/**
 * 入 / 切 — §B's toggle control.
 *
 * The project has no `Switch` idiom (its only Material control anywhere is one `Checkbox`), and
 * `FocusScreen` already expresses binary state as a word. This is both the visible label **and** the
 * `stateDescription`, from one function, because §B's accessibility line requires that "the visible
 * word and the state description must always agree" — two literals is how they stop agreeing.
 */
fun toggleWord(strings: Strings, on: Boolean): String =
    if (on) strings.gymSettings.toggleOn else strings.gymSettings.toggleOff

/** 単位 cycles on tap: two options do not deserve a picker (§B's value-rows note). */
fun nextUnits(current: Units): Units =
    if (current == Units.Metric) Units.Imperial else Units.Metric

// ─── The wheels ─────────────────────────────────────────────────────────────────────────────────

/**
 * `0,1,…,15` and friends — §B's own signature, kept because a wheel's rows are a list and a bound is a
 * range, and the conversion is the one place an off-by-one hides.
 *
 * A non-positive [step] yields an empty list rather than looping forever. There is no correct wheel for
 * "step by zero", and a page that renders no rows is recoverable where a hung composition is not.
 */
fun wheelSteps(range: IntRange, step: Int): List<Int> =
    if (step <= 0) emptyList() else (range.first..range.last step step).toList()

/**
 * A duration the user **chose** — bare seconds in Japanese, and なし at zero.
 *
 * §Q10's rule stated once: a duration the user chose renders as the value they chose, and a duration
 * the app measured renders through `durationKanji`. Sixty is 六十秒 on this page, on the builder's
 * wheel and on `GYM.LIBRARY.DETAIL`; rendering it 一分 anywhere would echo back a value nobody picked.
 *
 * This is the fourth spelling of that one-line rule — `BuilderDraft.restWheelLabel`,
 * `EngineRows.restLabel` and `ui.gym.session.chosenSecondsLabel` are the others — and, like them, it is
 * a twin rather than a shared function because the four files belong to four units. §Q10 is the
 * contract that binds them and each pins 六十秒 in its own test, so a divergence fails a build rather
 * than reaching a page. **If they are ever unified, unify them onto §Q10's sentence and never onto
 * `durationKanji`.**
 *
 * **In English the twins collapse, and that is a deletion rather than a translation** (§L7). §Q10's
 * distinction is carried by *orthography* — two ways of spelling the same sixty — and English has only
 * one, so there is nowhere for it to go. The English arm therefore renders through the same
 * `fmt.duration` a measured value uses: sixty is `1m` here exactly as it is on a results line, and the
 * two halves of §Q10 say the same thing. Keeping `fmt.seconds` for this arm would have preserved the
 * *shape* of a rule whose content is gone, and would have printed `300s` on a rest wheel.
 *
 * The Japanese arm is untouched, which is the point of branching on [Strings.lang] rather than picking
 * one rule for both: §Q10 remains binding for `Ja` and `GymSettingsCopyTest` still pins 六十秒.
 *
 * **Named for this page rather than `chosenSecondsLabel`**, which the player already owns: that one
 * takes *milliseconds* off a running clock and has no zero case, because a segment of zero length is
 * not a thing the player draws. Here zero is a real, selectable row and it reads なし — §6 :1141's own
 * word for no rest, where 〇秒 would be a prescription of nothing. Two functions one autocomplete apart
 * that differ in their units *and* at their boundary should not share a name.
 */
fun settingsSecondsLabel(strings: Strings, seconds: Int): String = when {
    seconds <= 0 -> strings.gymSettings.secondsNone
    strings.lang == Lang.Ja -> strings.fmt.seconds(seconds)
    else -> strings.fmt.duration(seconds)
}

/**
 * 支度の長さ's wheel: every second from なし to 十五秒.
 *
 * The bound is [clampPrepareSeconds]'s, read from it rather than restated, so the wheel can never offer
 * a value the store would clamp away. **Zero is a real row and is not "off"**: the timeline compiler
 * emits *no* 支度 segment for it rather than a zero-length one, which would divide by zero in the ensō
 * sweep (§B edge case 1).
 *
 * Step one rather than five, unlike the rest wheels: the range is sixteen rows at its widest, and 支度
 * is the one duration a user tunes against their own pocket rather than against a recovery interval.
 * The rest wheels' steps are `04-library-records.md` §3's and are reused verbatim through
 * `restOptions` — this page builds no parallel list for them.
 *
 * **Built once per language, not once per frame.** This page recomposes on every toggle and rebuilding
 * sixteen labels per frame is waste that shows up as a dropped frame on a spin rather than as a number
 * anybody measures. There are two languages and the lists are immutable, so both are built at class-init
 * and the switch is a map lookup — the alternative, caching the last one, would rebuild on every flip
 * of the language and is more code for less.
 */
fun prepareOptions(strings: Strings): List<WheelOption> = PREPARE_OPTIONS.getValue(strings.lang)

private val PREPARE_OPTIONS: Map<Lang, List<WheelOption>> = Lang.entries.associateWith { lang ->
    val strings = stringsFor(lang)
    wheelSteps(clampPrepareSeconds(0)..clampPrepareSeconds(Int.MAX_VALUE), 1)
        .map { WheelOption(it, settingsSecondsLabel(strings, it)) }
}

// ─── What TalkBack hears ────────────────────────────────────────────────────────────────────────

/**
 * 「支度の長さ、五秒」 — §B's accessibility line for a value row, verbatim.
 *
 * One function so the description and the two visible words cannot drift apart, and so a row that
 * gains a sub-line gains it in the announcement too: §B folds the explanation into the description for
 * the disabled 音声 case (`"音声、切、日本語の音声が入っていません"`), and the same shape reads correctly
 * for every other row that carries one.
 *
 * The separator is `fmt.listSeparator` rather than a literal 「、」: an ideographic comma is punctuation
 * in one language and a mojibake-looking glyph in the other, and it is the formatter's to choose.
 */
fun settingsRowDescription(
    strings: Strings,
    label: String,
    value: String,
    subtitle: String? = null,
): String = listOfNotNull(label, value, subtitle).joinToString(strings.fmt.listSeparator)
