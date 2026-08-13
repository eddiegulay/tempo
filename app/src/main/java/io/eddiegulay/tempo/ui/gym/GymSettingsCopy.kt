package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.SpeechAvailability
import io.eddiegulay.tempo.gym.Units
import io.eddiegulay.tempo.gym.WheelOption
import io.eddiegulay.tempo.gym.clampPrepareSeconds

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
 */
enum class SettingSection(val heading: String) {
    Cues("合図"),
    Progress("進行"),
    RestDefaults("休息の初期値"),
    Display("表示"),
}

/**
 * Every switchable or dialable row, with the word §B's mock puts on it.
 *
 * Declaration order is display order, which is also the focus order §B's accessibility paragraph asks
 * for ("focus order is visual order").
 */
enum class SettingRow(val label: String, val section: SettingSection) {
    Haptics("振動", SettingSection.Cues),
    Tones("音", SettingSection.Cues),
    Speech("音声", SettingSection.Cues),
    AutoAdvanceReps("目安で自動的に進む", SettingSection.Progress),
    PrepareSeconds("支度の長さ", SettingSection.Progress),
    StationRest("種目の間", SettingSection.RestDefaults),
    RoundRest("巡の間", SettingSection.RestDefaults),
    KeepScreenOn("画面を消さない", SettingSection.Display),
    Units("単位", SettingSection.Display),
}

/**
 * Whether a row can be touched, and the line under it that explains itself.
 *
 * [subtitle] is null for the rows §B's mock leaves bare — it draws the sub-line "only when the row
 * needs explaining", and a permanent explanatory line under every switch is the chrome that teaches a
 * user to stop reading the ones that mean something.
 */
data class RowState(val enabled: Boolean, val subtitle: String?)

// §B's mock and its states table. Every string below appears in a spec table; none is composed here.
private const val SUB_SPEECH = "種目の名前を読み上げる"
private const val SUB_NO_JAPANESE_VOICE = "日本語の音声が入っていません"
private const val SUB_NO_TTS_ENGINE = "読み上げ機能がありません"
private const val SUB_AUTO_ADVANCE = "回数の種目でも時間が来たら次へ"
private const val SUB_KEEP_SCREEN_ON = "運動中だけ"
private const val SUB_SILENT_MODE = "マナーモードでも鳴ります"

/** §B's `SessionLive` state: the rest defaults and 支度 do not retro-apply to a compiled timeline. */
private const val SUB_NOT_THIS_SESSION = "いまの鍛錬には反映されません"

/** 「これから作る型に使われます」 — a footnote under the 休息の初期値 card, not a row inside it. */
const val REST_DEFAULTS_FOOTNOTE = "これから作る型に使われます"

/** §B's `Error` state, and `DECISIONS.md` §Q6's `Rejected` copy. One line, under the card that failed. */
const val WRITE_FAILED_LINE = "保存できませんでした"

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
    prefs: GymPreferences,
    speech: SpeechAvailability?,
    sessionLive: Boolean,
    tonesJustEnabled: Boolean = false,
    touchExplorationEnabled: Boolean = false,
): Map<SettingRow, RowState> = buildMap {
    put(SettingRow.Haptics, RowState(enabled = true, subtitle = null))
    put(
        SettingRow.Tones,
        RowState(enabled = true, subtitle = if (tonesJustEnabled) SUB_SILENT_MODE else null),
    )
    put(SettingRow.Speech, speechRowState(prefs, speech, touchExplorationEnabled))
    put(SettingRow.AutoAdvanceReps, RowState(enabled = true, subtitle = SUB_AUTO_ADVANCE))
    put(SettingRow.PrepareSeconds, RowState(enabled = true, subtitle = sessionNotice(sessionLive)))
    put(SettingRow.StationRest, RowState(enabled = true, subtitle = sessionNotice(sessionLive)))
    put(SettingRow.RoundRest, RowState(enabled = true, subtitle = sessionNotice(sessionLive)))
    put(SettingRow.KeepScreenOn, RowState(enabled = true, subtitle = SUB_KEEP_SCREEN_ON))
    put(SettingRow.Units, RowState(enabled = true, subtitle = null))
}

private fun sessionNotice(sessionLive: Boolean): String? = if (sessionLive) SUB_NOT_THIS_SESSION else null

/**
 * 音声, which is the only row with four different things to say.
 *
 * **A missing voice disables the row but never clears the stored flag** (§B edge case 4): reinstalling
 * a voice must restore speech without the user having to re-find the switch, so the preference survives
 * the hardware being unable to honour it. `armCues` disarms the channel meanwhile and the player falls
 * back to tones silently.
 *
 * **The TalkBack-override sub-line is `SUB_SPEECH`, and that is a documented gap, not a choice.**
 * `DECISIONS.md` §Q2 requires "a subtitle explaining the override when touch exploration is on" and
 * **no spec table contains that sentence** — not §B's mock, not §B's states table, not `03-player.md`
 * §D.6. `00-plan.md` §4.1 forbids inventing copy, so the row keeps the one sub-line the mock does give
 * it and the gap is reported rather than filled with prose of my own. When the string lands, it belongs
 * on the [touchExplorationEnabled] branch below and nowhere else.
 */
private fun speechRowState(
    prefs: GymPreferences,
    speech: SpeechAvailability?,
    touchExplorationEnabled: Boolean,
): RowState = when (speech) {
    SpeechAvailability.NoJapaneseVoice -> RowState(enabled = false, subtitle = SUB_NO_JAPANESE_VOICE)
    SpeechAvailability.NoEngine -> RowState(enabled = false, subtitle = SUB_NO_TTS_ENGINE)
    // Available, or not yet probed. Both are live rows; see the KDoc on `settingsRowStates`.
    SpeechAvailability.Available, null -> RowState(
        enabled = true,
        // Shown while the row needs explaining: before it has been switched on at all, and while
        // TalkBack is speaking for it (`DECISIONS.md` §Q2 — the string gap above).
        subtitle = if (!prefs.speech || touchExplorationEnabled) SUB_SPEECH else null,
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
fun toggleWord(on: Boolean): String = if (on) "入" else "切"

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
 * A duration the user **chose**, in bare seconds — `DECISIONS.md` §Q10, and なし at zero.
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
 * **Named for this page rather than `chosenSecondsLabel`**, which the player already owns: that one
 * takes *milliseconds* off a running clock and has no zero case, because a segment of zero length is
 * not a thing the player draws. Here zero is a real, selectable row and it reads なし — §6 :1141's own
 * word for no rest, where 〇秒 would be a prescription of nothing. Two functions one autocomplete apart
 * that differ in their units *and* at their boundary should not share a name.
 */
fun settingsSecondsLabel(seconds: Int): String =
    if (seconds <= 0) "なし" else JapaneseDate.kanjiExtended(seconds) + "秒"

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
 * Built once. This page recomposes on every toggle and rebuilding sixteen kanji strings per frame is
 * waste that shows up as a dropped frame on a spin rather than as a number anybody measures.
 */
fun prepareOptions(): List<WheelOption> = PREPARE_OPTIONS

private val PREPARE_OPTIONS: List<WheelOption> =
    wheelSteps(clampPrepareSeconds(0)..clampPrepareSeconds(Int.MAX_VALUE), 1)
        .map { WheelOption(it, settingsSecondsLabel(it)) }

// ─── What TalkBack hears ────────────────────────────────────────────────────────────────────────

/**
 * 「支度の長さ、五秒」 — §B's accessibility line for a value row, verbatim.
 *
 * One function so the description and the two visible words cannot drift apart, and so a row that
 * gains a sub-line gains it in the announcement too: §B folds the explanation into the description for
 * the disabled 音声 case (`"音声、切、日本語の音声が入っていません"`), and the same shape reads correctly
 * for every other row that carries one.
 */
fun settingsRowDescription(label: String, value: String, subtitle: String? = null): String =
    listOfNotNull(label, value, subtitle).joinToString("、")
